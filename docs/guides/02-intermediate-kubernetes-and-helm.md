# Level 2: Kubernetes & Helm Orchestration

This guide bridges the gap between local Docker Compose setups and production-grade container orchestration. You will learn how Kubernetes manages distributed microservices, how to write and debug manifests, and how to package applications with Helm.

---

## 1. Why Move from Docker Compose to Kubernetes?

While Docker Compose is ideal for single-host development, it cannot handle production cluster requirements:

| Capability | Docker Compose | Kubernetes |
|---|---|---|
| **Scope** | Single host / single machine | Multi-node distributed cluster |
| **Self-Healing** | Simple container restart | Reschedules failed pods to healthy nodes |
| **Zero-Downtime Rollouts** | Stops old container, starts new | Gradual rolling updates with readiness gating |
| **Autoscaling** | Manual (`docker compose scale`) | Automatic based on CPU/RAM metrics (HPA) |
| **Traffic Routing** | Direct port bindings | Layer 7 Ingress with path & host routing |
| **Security Isolation** | Docker bridge network | Zero-trust NetworkPolicies & RBAC |

---

## 2. Anatomy of the Kubernetes Manifests

All Kubernetes manifests for this platform are located in [`infra/k8s/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/).

```
                        ┌─────────────────────────────────────────┐
                        │        Ingress (30-ingress.yaml)         │
                        └───────┬─────────────────┬───────────────┘
                                │                 │
               Path: /api/v1/*  ▼                 ▼  Path: /
                  ┌────────────────────┐    ┌────────────────────┐
                  │    Service: api    │    │ Service: frontend  │
                  └─────────┬──────────┘    └────────────────────┘
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      ┌─────────────────┐       ┌─────────────────┐
      │ Pod: api-xxxx-1 │       │ Pod: api-xxxx-2 │
      └────────┬────────┘       └────────┬────────┘
               │                         │
               └────────────┬────────────┘
                            ▼
                  ┌────────────────────┐
                  │ Service: postgres  │
                  └─────────┬──────────┘
                            ▼
                  ┌────────────────────┐
                  │   postgres-0       │ (StatefulSet + PVC)
                  └────────────────────┘
```

### 2.1 Namespaces (`00-namespace.yaml`)
Namespaces provide logical multi-tenancy and boundary isolation:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: devops-dashboard
```
All resources in this repo reside inside `devops-dashboard`. This prevents name collisions and allows resource quotas per team.

---

### 2.2 Workloads: Deployments vs StatefulSets

#### Stateless Services: `Deployment` (e.g. [`21-api.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/21-api.yaml))
- Manages identical, disposable replicas.
- Uses `strategy: type: RollingUpdate` to replace pods one by one without downtime.
- If an API pod crashes, Kubernetes destroys it and launches a fresh one.

#### Stateful Services: `StatefulSet` ([`10-postgres.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/10-postgres.yaml))
- Pods have stable, predictable network identities (`postgres-0`).
- Dynamically provisions storage using `volumeClaimTemplates` linked to a `PersistentVolumeClaim` (PVC).
- Deletion or restarts of `postgres-0` reattach the exact same persistent storage volume.

#### Singleton Worker: `Recreate` Strategy ([`24-worker.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/24-worker.yaml))
Notice the worker configuration:
```yaml
spec:
  replicas: 1
  strategy:
    type: Recreate
```
**Why `Recreate` instead of `RollingUpdate`?**
The worker runs background polling jobs (stale deployment detection, alert escalation). If two worker pods ran concurrently during a rolling update, both would poll the database simultaneously and process duplicate jobs. `Recreate` guarantees the old worker terminates before the new one starts.

---

### 2.3 Probes: Startup, Liveness, and Readiness

Java Spring Boot applications require careful probe tuning due to JVM warmup.

Look at the probe configuration in [`21-api.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/21-api.yaml):

```yaml
startupProbe:
  httpGet:
    path: /health
    port: 4000
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 15     # 15 * 5s = 75 seconds maximum startup window

livenessProbe:
  httpGet:
    path: /health
    port: 4000
  initialDelaySeconds: 45
  periodSeconds: 15
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health
    port: 4000
  initialDelaySeconds: 20
  periodSeconds: 10
  failureThreshold: 3
```

#### Why Three Probes?
1. **`startupProbe`:** Protects slow-starting containers. The JVM must initialize Spring beans and test the database connection before accepting traffic. The `startupProbe` disables liveness/readiness probes until it passes. Without this, Kubernetes would kill the pod for failing liveness before it even finished booting!
2. **`livenessProbe`:** Checks if the application is alive. If an unrecoverable deadlock occurs, liveness fails and Kubernetes **restarts the pod**.
3. **`readinessProbe`:** Checks if the application is ready to receive network traffic. If readiness fails (e.g. database connection temporarily drops), Kubernetes removes the pod from the Service endpoint so clients don't receive `502 Bad Gateway` errors.

---

### 2.4 Ingress & Path-Based Routing ([`30-ingress.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/30-ingress.yaml))

Instead of exposing every service on a public IP or NodePort, an **Ingress** acts as a reverse proxy:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: devops-dashboard-ingress
spec:
  rules:
  - host: dashboard.example.com
    http:
      paths:
      - path: /api/v1/
        pathType: Prefix
        backend:
          service:
            name: api
            port:
              number: 4000
      - path: /auth/
        pathType: Prefix
        backend:
          service:
            name: auth
            port:
              number: 4001
      - path: /
        pathType: Prefix
        backend:
          service:
            name: frontend
            port:
              number: 3000
```
Incoming requests to `dashboard.example.com/api/v1/...` route directly to the API service, while root requests route to the Next.js frontend.

---

### 2.5 Zero-Trust Security: NetworkPolicies ([`50-network-policies.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/50-network-policies.yaml))

By default, any pod in a Kubernetes cluster can communicate with any other pod across any namespace.

This repository enforces **zero-trust network isolation**:
1. **Default Deny:** Blocks all ingress and egress traffic by default.
2. **Explicit Allow Rules:**
   - Ingress controller is allowed to contact `frontend`, `api`, and `auth`.
   - `api`, `auth`, `deployment`, and `worker` are allowed egress to `postgres` on port 5432.
   - `worker` is allowed egress to `notification` on port 4003.
   - `frontend` is **blocked** from contacting `postgres` directly.

---

## 3. Package Management with Helm

While raw manifests are clear, managing environments (dev vs staging vs prod) with copy-pasted YAML leads to configuration drift.

**Helm** is the package manager for Kubernetes. The chart for this platform is located in [`infra/helm/devops-dashboard/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/helm/devops-dashboard/).

### 3.1 Helm Chart Structure
```
infra/helm/devops-dashboard/
├── Chart.yaml              # Chart name, description, and version
├── values.yaml             # Default configuration values
├── values-staging.yaml     # Staging overrides (e.g. 1 replica, reduced resources)
├── values-production.yaml  # Production overrides (e.g. multiple replicas, high limits, TLS)
└── templates/              # Dynamic YAML templates with Go templating
    ├── _helpers.tpl        # Common labels, names, and reusable template blocks
    ├── api.yaml            # Rendered API Deployment + Service
    ├── ingress.yaml        # Ingress resource with dynamic host
    └── ...
```

### 3.2 Helm Values Inheritance
```
 values.yaml (Base Defaults)
       ▲
       ├── + values-staging.yaml    (Dev/Staging: lightweight, low resources)
       │
       └── + values-production.yaml (Prod: HA replicas, strict PDBs, TLS)
```

Example override in `values-production.yaml`:
```yaml
api:
  replicaCount: 4
  resources:
    limits:
      cpu: "1000m"
      memory: "1Gi"
    requests:
      cpu: "500m"
      memory: "512Mi"
```

### 3.3 Essential Helm Commands
```bash
# 1. Lint the chart for syntax and formatting errors
helm lint infra/helm/devops-dashboard

# 2. Render templates locally without contacting a cluster (dry-run)
helm template devops-dashboard infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-staging.yaml

# 3. Install or Upgrade a release
helm upgrade --install devops-dashboard infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-staging.yaml \
  --namespace devops-dashboard \
  --create-namespace

# 4. View release history
helm history devops-dashboard -n devops-dashboard

# 5. Rollback to a previous release (e.g. revision 1)
helm rollback devops-dashboard 1 -n devops-dashboard
```

---

## 4. Hands-On Local Kubernetes Lab (Minikube / Kind)

Follow this step-by-step tutorial to deploy this entire platform to a local Kubernetes cluster on your machine.

### Step 1: Start Minikube or Kind
```bash
# Option A: Using Minikube
minikube start --cpus=4 --memory=8192
minikube addons enable ingress
minikube addons enable metrics-server

# Option B: Using Kind
kind create cluster --name devops-dashboard
```

### Step 2: Build & Load Images Locally
To avoid needing an external Docker registry, build the images and load them directly into your local cluster:

```bash
# 1. Build images with the repository tag
docker compose build

# 2. Load images into the cluster
# For Minikube:
minikube image load devops-dashboard/auth:latest
minikube image load devops-dashboard/api:latest
minikube image load devops-dashboard/deployment:latest
minikube image load devops-dashboard/notification:latest
minikube image load devops-dashboard/worker:latest
minikube image load devops-dashboard/frontend:latest

# For Kind:
kind load docker-image devops-dashboard/auth:latest --name devops-dashboard
kind load docker-image devops-dashboard/api:latest --name devops-dashboard
kind load docker-image devops-dashboard/deployment:latest --name devops-dashboard
kind load docker-image devops-dashboard/notification:latest --name devops-dashboard
kind load docker-image devops-dashboard/worker:latest --name devops-dashboard
kind load docker-image devops-dashboard/frontend:latest --name devops-dashboard
```

### Step 3: Deploy with Helm
```bash
helm upgrade --install devops-dashboard infra/helm/devops-dashboard \
  --namespace devops-dashboard \
  --create-namespace
```

### Step 4: Verify Cluster State
```bash
# Watch pods transition to Running
kubectl get pods -n devops-dashboard -w

# Check services
kubectl get svc -n devops-dashboard

# Check autoscalers
kubectl get hpa -n devops-dashboard
```

### Step 5: Test via Port-Forwarding
```bash
# Forward the API service to localhost:4000
kubectl port-forward svc/api 4000:4000 -n devops-dashboard

# In another terminal, verify the health check:
curl -s http://localhost:4000/health | jq .
```

---

## ⏭️ Next Step

Now that you understand Kubernetes workloads, networking, and Helm charts, proceed to:
👉 **[Level 3: SRE, Security & Production Readiness](03-advanced-sre-and-production.md)**
