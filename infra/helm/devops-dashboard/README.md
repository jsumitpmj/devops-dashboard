# DevOps Dashboard — Helm Chart

A production-ready Helm chart for the **DevOps Dashboard** microservices platform.

## Chart Structure

```
infra/helm/devops-dashboard/
├── Chart.yaml                  # Chart metadata
├── values.yaml                 # Default values (all environments)
├── values-staging.yaml         # Staging overrides
├── values-production.yaml      # Production overrides
├── .helmignore
└── templates/
    ├── _helpers.tpl            # Named template helpers
    ├── NOTES.txt               # Post-install notes
    ├── namespace.yaml
    ├── serviceaccount.yaml
    ├── configmap.yaml
    ├── secrets.yaml
    ├── postgres.yaml           # StatefulSet + Service (dev/staging only)
    ├── api.yaml                # API Gateway
    ├── auth.yaml               # Auth Service
    ├── deployment-svc.yaml     # Deployment Service
    ├── notification.yaml       # Notification Service
    ├── worker.yaml             # Worker (single replica, Recreate strategy)
    ├── frontend.yaml           # Next.js Frontend
    ├── ingress.yaml
    ├── hpa.yaml                # Horizontal Pod Autoscalers
    ├── pdb.yaml                # Pod Disruption Budgets
    └── network-policies.yaml
```

## Prerequisites

| Tool | Version |
|------|---------|
| Helm | ≥ 3.10 |
| Kubernetes | ≥ 1.25 |
| metrics-server | Required for HPA |
| nginx-ingress or Traefik | Required for Ingress |

## Quick Start

### 1. Install (local dev / kind / minikube)

```bash
helm upgrade --install devops-dashboard ./infra/helm/devops-dashboard \
  --namespace devops-dashboard \
  --create-namespace
```

### 2. Install (Staging)

```bash
helm upgrade --install devops-dashboard ./infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-staging.yaml \
  --namespace devops-dashboard \
  --create-namespace
```

### 3. Install (Production)

```bash
helm upgrade --install devops-dashboard ./infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-production.yaml \
  --set secrets.jwtSecret="$JWT_SECRET" \
  --set secrets.dbPassword="$DB_PASSWORD" \
  --namespace devops-dashboard \
  --create-namespace
```

## Key Configuration Options

| Key | Default | Description |
|-----|---------|-------------|
| `global.imageRegistry` | `""` | Private registry prefix |
| `global.imagePullSecrets` | `[]` | Pull secrets for private registries |
| `namespace.create` | `true` | Whether to create the namespace |
| `secrets.jwtSecret` | placeholder | JWT signing secret (≥ 32 chars in prod) |
| `secrets.dbPassword` | `"devops"` | App DB password |
| `postgres.enabled` | `true` | Bundle a dev PostgreSQL StatefulSet |
| `config.databaseUrl` | local | JDBC URL for PostgreSQL |
| `ingress.host` | `dashboard.example.com` | External hostname |
| `ingress.tls.enabled` | `false` | Enable TLS on the Ingress |
| `networkPolicies.enabled` | `true` | Deploy NetworkPolicies |

### Enabling / Disabling Services

Each microservice has an `enabled` flag:

```yaml
api:
  enabled: true   # set to false to skip deploying this service
```

### Using a Private Registry

```bash
helm upgrade --install devops-dashboard ./infra/helm/devops-dashboard \
  --set global.imageRegistry=registry.example.com \
  --set global.imagePullSecrets[0]=my-registry-secret
```

### Overriding Image Tags

```bash
helm upgrade --install devops-dashboard ./infra/helm/devops-dashboard \
  --set api.image.tag=v1.2.3 \
  --set auth.image.tag=v1.2.3
```

## Production Checklist

- [ ] Set a strong `secrets.jwtSecret` (≥ 32 chars)
- [ ] Disable bundled Postgres (`postgres.enabled=false`) and use a managed DB
- [ ] Set `config.databaseUrl` to your external DB endpoint
- [ ] Enable TLS (`ingress.tls.enabled=true`) with a valid cert Secret
- [ ] Set `ingress.host` to your domain
- [ ] Use an external secrets manager (ESO, Vault, Sealed Secrets) instead of plain `--set secrets.*`
- [ ] Ensure `metrics-server` is installed for HPA to work

## Dry Run / Lint

```bash
# Lint the chart
helm lint ./infra/helm/devops-dashboard

# Dry-run to see rendered manifests
helm template devops-dashboard ./infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-staging.yaml

# Diff against live cluster (requires helm-diff plugin)
helm diff upgrade devops-dashboard ./infra/helm/devops-dashboard \
  -f infra/helm/devops-dashboard/values-production.yaml \
  -n devops-dashboard
```

## Uninstall

```bash
helm uninstall devops-dashboard -n devops-dashboard

# Optionally delete the namespace (PVCs will also be deleted)
kubectl delete namespace devops-dashboard
```

## Relationship to `infra/k8s/`

The raw manifests in `infra/k8s/` remain as-is — useful for direct `kubectl apply`
or for reference when debugging the chart. The Helm chart is a superset: every
resource in `infra/k8s/` has a corresponding template here, with all hard-coded
values promoted to `values.yaml`.
