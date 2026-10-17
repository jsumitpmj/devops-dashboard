# Kubernetes Manifests — DevOps Dashboard Platform

## Directory Layout

```
infra/k8s/
├── 00-namespace.yaml          # Namespace: devops-dashboard
├── 01-configmap.yaml          # Shared configuration (non-secret)
├── 02-secrets.yaml            # Secret templates (edit before applying!)
├── 10-postgres.yaml           # PostgreSQL StatefulSet (dev only)
├── 20-auth.yaml               # Auth: Deployment + Service
├── 21-api.yaml                # API: Deployment + Service
├── 22-deployment-svc.yaml     # Deployment Service: Deployment + Service
├── 23-notification.yaml       # Notification: Deployment + Service
├── 24-worker.yaml             # Worker: Deployment + Service
├── 25-frontend.yaml           # Frontend: Deployment + Service
├── 30-ingress.yaml            # Ingress: path-based routing
├── 40-hpa.yaml                # HorizontalPodAutoscalers
├── 50-network-policies.yaml   # NetworkPolicies (default deny + allow rules)
└── 51-pdb.yaml                # PodDisruptionBudgets
```

## Prerequisites

- Kubernetes 1.27+
- `kubectl` configured to target your cluster
- An Ingress controller (nginx-ingress recommended)
- `metrics-server` installed (for HPA)
- A container registry for pushing images

## Deploy (Step by Step)

```bash
# 1 — Namespace, config, secrets
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl apply -f infra/k8s/01-configmap.yaml
# IMPORTANT: Edit 02-secrets.yaml with real base64 values first!
kubectl apply -f infra/k8s/02-secrets.yaml

# 2 — Database init scripts (from local SQL files)
kubectl create configmap postgres-init \
  --from-file=01_init.sql=infra/db/init.sql \
  --from-file=02_seed.sql=scripts/seed.sql \
  -n devops-dashboard

# 3 — PostgreSQL (dev only — use managed DB in production)
kubectl apply -f infra/k8s/10-postgres.yaml

# 4 — Wait for DB to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=postgres \
  -n devops-dashboard --timeout=120s

# 5 — Deploy all services
kubectl apply -f infra/k8s/20-auth.yaml
kubectl apply -f infra/k8s/21-api.yaml
kubectl apply -f infra/k8s/22-deployment-svc.yaml
kubectl apply -f infra/k8s/23-notification.yaml
kubectl apply -f infra/k8s/24-worker.yaml
kubectl apply -f infra/k8s/25-frontend.yaml

# 6 — Ingress, autoscaling, network policies, PDBs
kubectl apply -f infra/k8s/30-ingress.yaml
kubectl apply -f infra/k8s/40-hpa.yaml
kubectl apply -f infra/k8s/50-network-policies.yaml
kubectl apply -f infra/k8s/51-pdb.yaml

# Or apply everything at once:
# kubectl apply -f infra/k8s/
```

## Verify

```bash
kubectl get all -n devops-dashboard
kubectl get ingress -n devops-dashboard
kubectl get hpa -n devops-dashboard
kubectl get pdb -n devops-dashboard
```

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Worker: 1 replica + Recreate** | Prevents duplicate job processing; for HA, add leader election |
| **Notification: 1 replica** | Maintains notification ordering; safe to scale with idempotency |
| **Startup probes** | JVM cold-start can take 30-60s; startup probes prevent premature kills |
| **Network policies** | Default-deny + explicit allow matches least-privilege principle |
| **PDBs on multi-replica services** | Ensures zero-downtime during node drains / cluster upgrades |
| **PostgreSQL as StatefulSet** | Dev convenience only — production should use RDS/Cloud SQL |

## Production Checklist

- [ ] Replace secrets with real values (or use external secrets operator)
- [ ] Set image tags to specific versions (not `latest`)
- [ ] Configure TLS in ingress (uncomment `tls` section)
- [ ] Replace dev PostgreSQL with managed database
- [ ] Update `DATABASE_URL` in ConfigMap for managed DB
- [ ] Configure a container registry and update `image:` fields
- [ ] Review resource requests/limits for your workload
- [ ] Enable monitoring (Prometheus + Grafana)

## Related Documentation & Learning Guides

- 🧪 [Local Kubernetes Testing Guide](TESTING.md) — Comprehensive health check verification and interactive psql login.
- 📦 [Loading Images Locally Guide](retag-images.md) — Step-by-step instructions for Minikube, Kind, and k3d.
- ☸️ [Intermediate Kubernetes & Helm Guide](../../docs/guides/02-intermediate-kubernetes-and-helm.md) — Conceptual deep dive into workloads, probes, HPA, and Helm.
- 🛡️ [Advanced SRE & Production Guide](../../docs/guides/03-advanced-sre-and-production.md) — External Secrets, HA database design, and zero-downtime rollouts.
- 🛠️ [DevOps Troubleshooting Guide](../../docs/guides/troubleshooting-guide.md) — Triage runbook for CrashLoopBackOff, OOMKilled, and probe timeouts.

