# Level 3: SRE, Security & Production Readiness

This guide is designed for advanced DevOps engineers, Site Reliability Engineers (SREs), and platform architects. It examines the operational maturity of the DevOps Dashboard platform, covering production security, high availability, observability, zero-downtime deployment patterns, and GitOps workflows.

---

## 1. The Twelve-Factor App Evaluation

The [Twelve-Factor App](https://12factor.net/) is the gold standard for building modern, cloud-native SaaS applications. Here is an architectural evaluation of this repository against those principles:

```
    ┌─────────────────────────────────────────────────────────────┐
    │                 12-FACTOR ARCHITECTURE AUDIT                │
    ├─────────────────────────────┬───────────────────────────────┤
    │ Factor                      │ Implementation in this Repo   │
    ├─────────────────────────────┼───────────────────────────────┤
    │ I. One Codebase             │ Multi-module Maven + Next.js  │
    │ II. Explicit Dependencies   │ pom.xml and package-lock.json │
    │ III. Config in Environment  │ Spring env vars & ConfigMaps  │
    │ IV. Backing Services        │ DB treated as attached URL    │
    │ V. Build, Release, Run      │ Multi-stage Docker & Helm     │
    │ VI. Stateless Processes     │ Replicable, no shared session │
    │ VII. Port Binding           │ Embedded Tomcat & Node server │
    │ VIII. Concurrency           │ Scale via HPA / replica count │
    │ IX. Disposability           │ Fast startup, SIGTERM handling│
    │ X. Dev/Prod Parity          │ Docker locally, K8s in prod   │
    │ XI. Logs as Event Streams   │ stdout/stderr JSON streams    │
    │ XII. Admin Processes        │ One-off migration scripts     │
    └─────────────────────────────┴───────────────────────────────┘
```

---

## 2. Production Secrets Architecture

### The Anti-Pattern: Secrets in Git
In [`infra/k8s/02-secrets.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/02-secrets.yaml), values are stored as base64-encoded strings:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: devops-secrets
stringData:
  db-password: "devops"
  jwt-secret: "dev-jwt-secret-not-for-production-use-32chars"
```
> [!WARNING]
> Base64 is **encoding**, not **encryption**. Anyone with read access to the git repository or Kubernetes cluster can decode these strings with `echo "..." | base64 -d`.

### The Production Solution: External Secrets Operator (ESO)
In enterprise environments, secrets reside in a dedicated secret store (e.g., AWS Secrets Manager, HashiCorp Vault, or GCP Secret Manager).

```
   ┌───────────────────────┐
   │ AWS Secrets Manager / │
   │    HashiCorp Vault    │
   └───────────┬───────────┘
               │
               ▼
   ┌───────────────────────┐
   │ External Secrets      │  (Kubernetes Operator)
   │ Operator (ESO)        │
   └───────────┬───────────┘
               │ Syncs & creates
               ▼
   ┌───────────────────────┐
   │ Native K8s Secret:    │  (Created in-memory in cluster)
   │ devops-secrets        │
   └───────────────────────┘
```

#### Production `ExternalSecret` Resource:
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: devops-secrets
  namespace: devops-dashboard
spec:
  refreshInterval: "1h"
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: devops-secrets
  data:
  - secretKey: db-password
    remoteRef:
      key: production/devops-dashboard/database
      property: password
  - secretKey: jwt-secret
    remoteRef:
      key: production/devops-dashboard/auth
      property: jwtSecret
```

---

## 3. Database High Availability & Reliability

### 3.1 StatefulSet vs Managed Cloud Database
In development, running PostgreSQL as a Kubernetes `StatefulSet` is convenient. However, in production:
- **Do NOT run single-replica databases in Kubernetes for production** without an enterprise operator (e.g. [CloudNativePG](https://cloudnative-pg.io/) or Zalando Postgres Operator).
- **Recommended Production Architecture:** Use a managed database service like **AWS Aurora PostgreSQL** or **GCP Cloud SQL**, providing automated multi-AZ failover, automated point-in-time recovery, and read replicas.

To switch this platform to a managed database:
1. In Helm, set `postgres.enabled=false`.
2. Update `config.databaseUrl` to your cloud endpoint:
   `jdbc:postgresql://db-aurora-cluster.xyz.us-east-1.rds.amazonaws.com:5432/devops_dashboard`

### 3.2 Connection Pooling (HikariCP) Tuning
Each Spring Boot service uses HikariCP for connection pooling. Misconfigured connection pools cause database connection exhaustion (`FATAL: remaining connection slots are reserved`).

#### Sizing Formula (PostgreSQL official recommendation):
$$\text{Pool Size} = (\text{CPU Cores} \times 2) + \text{Effective Spindle Count}$$

For a multi-service architecture with 5 microservices running 2 replicas each (10 pods total):
If PostgreSQL `max_connections = 100`:
- Set each pod's `maximumPoolSize = 8`
- Total connections: $10 \text{ pods} \times 8 = 80 \text{ connections}$ (leaving 20 for migrations, admin access, and replicas).

Configure in `application.yml` or environment variables:
```properties
spring.datasource.hikari.maximum-pool-size=8
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=10000
spring.datasource.hikari.leak-detection-threshold=15000
```

### 3.3 Zero-Downtime Schema Migrations (Expand/Contract Pattern)
Never apply breaking database schema changes (like renaming or dropping columns) simultaneously with application deployments.

```
PHASE 1: EXPAND              PHASE 2: PARALLEL RUN        PHASE 3: CONTRACT
Add new column              Dual-write to both           Remove old column
(Keep old column)           Read from new column
```
1. **Expand:** Add the new column/table as nullable in [`infra/db/migrations/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/db/migrations/).
2. **Parallel Run:** Deploy the updated application code that writes to both columns but reads from the new column.
3. **Backfill:** Run a background script to backfill historical data.
4. **Contract:** Remove the old unused column in a subsequent release.

---

## 4. Zero-Downtime Deployment Patterns

### 4.1 Kubernetes Graceful Termination
When a pod is terminated during a rolling update, Kubernetes sends a `SIGTERM` signal. If the application exits immediately, active HTTP requests will fail with connection resets.

Configure graceful shutdown in Spring Boot:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

Add a `preStop` hook to delay pod shutdown while endpoints are removed from kube-proxy:
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sleep", "5"]
```

### 4.2 Canary Releases with Argo Rollouts
Instead of an all-or-nothing rolling update, use Canary deployments to route 10% of user traffic to a new version, analyze metrics, and automatically promote or abort:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: api-rollout
spec:
  replicas: 5
  strategy:
    canary:
      steps:
      - setWeight: 20
      - pause: { duration: 5m }
      - analysis:
          templates:
          - templateName: success-rate
      - setWeight: 50
      - pause: { duration: 10m }
```

---

## 5. Observability: Metrics, Logging, and Tracing

```
                  ┌───────────────────────────────────────────────┐
                  │                 OBSERVABILITY                 │
                  ├───────────────┬───────────────┬───────────────┤
                  │    METRICS    │     LOGS      │    TRACES     │
                  ├───────────────┼───────────────┼───────────────┤
                  │  Prometheus   │ Promtail/Loki │ OpenTelemetry │
                  │  and Grafana  │ or Fluentbit  │  and Jaeger   │
                  └───────────────┴───────────────┴───────────────┘
```

### 5.1 Metrics (The RED Method)
Track three key metrics for every microservice:
1. **Rate:** Number of HTTP requests per second (`http_server_requests_seconds_count`).
2. **Errors:** Number of HTTP 5xx responses (`http_server_requests_seconds_count{status=~"5.."}`).
3. **Duration:** Response latency distribution and p99 percentiles (`http_server_requests_seconds_bucket`).

### 5.2 Structured JSON Logging & Correlation IDs
In microservice architectures, a single user request traverses multiple services (`frontend` → `api` → `deployment` → `worker` → `postgres`).

Without a correlation ID, debugging errors across log streams is nearly impossible:
1. The **API Gateway** generates a unique `X-Request-ID` (UUID) upon receiving an incoming HTTP request.
2. The ID is stored in the Logging MDC (`Mapped Diagnostic Context`).
3. Outgoing REST calls propagate `X-Request-ID` in HTTP headers.
4. Logs are emitted as structured JSON:
```json
{
  "timestamp": "2026-10-18T02:30:00Z",
  "level": "ERROR",
  "service": "deployment-svc",
  "traceId": "9f1c7d2a-4b3e-48a1-b8d1-123456789abc",
  "message": "Database lock acquired by another transaction",
  "environmentId": "d0000000-0000-0000-0000-000000000001"
}
```

---

## 6. Monorepo CI/CD & GitOps Pipeline Architecture

### 6.1 GitHub Actions Workflow Blueprint
In a monorepo containing multiple microservices, a CI pipeline must only rebuild services that changed.

```
Push to branch ──► Path Filter Check
                         ├──► Did frontend/ change? ──► Test & Build Frontend
                         ├──► Did services/api/ change? ──► Test & Build API
                         └──► Did infra/ change? ──► Run Helm Lint & Kubeconform
```

#### GitHub Actions Workflow Example (`.github/workflows/ci.yml`):
```yaml
name: CI / Monorepo Verification

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      frontend: ${{ steps.filter.outputs.frontend }}
      helm: ${{ steps.filter.outputs.helm }}
    steps:
    - uses: actions/checkout@v4
    - uses: dorny/paths-filter@v3
      id: filter
      with:
        filters: |
          backend:
            - 'services/**'
          frontend:
            - 'frontend/**'
          helm:
            - 'infra/helm/**'

  backend-test:
    needs: detect-changes
    if: needs.detect-changes.outputs.backend == 'true'
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '21'
        cache: 'maven'
    - run: cd services && mvn clean test

  helm-lint:
    needs: detect-changes
    if: needs.detect-changes.outputs.helm == 'true'
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: azure/setup-helm@v4
    - run: helm lint infra/helm/devops-dashboard
```

### 6.2 GitOps Continuous Delivery (ArgoCD)
With GitOps:
- Git is the single source of truth for desired infrastructure state.
- **ArgoCD** continuously monitors the Git repository and automatically synchronizes the live Kubernetes cluster state.
- Manual `kubectl apply` commands in production are strictly forbidden.

---

## ⏭️ Ready to Practice?

Put all these concepts into action with real hands-on exercises:
👉 **[Level 4: Hands-On DevOps Challenges](04-hands-on-devops-challenges.md)**
