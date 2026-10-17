# DevOps Engineer Learning Roadmap & Curriculum

Welcome to the **DevOps Dashboard Platform** learning path! This curriculum is designed for students, junior engineers, and experienced practitioners who want to master real-world, cloud-native DevOps concepts using a complete, multi-service production-style codebase.

---

## 🧭 Curriculum Overview

Unlike toy "Hello World" tutorials, this repository reflects how modern engineering teams build, containerize, orchestrate, secure, and operate distributed systems in production.

```
                 ┌───────────────────────────────────────────────┐
                 │       LEVEL 1: CONTAINER FOUNDATIONS          │
                 │  Docker · Multi-Stage Builds · Compose · DNS  │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │      LEVEL 2: ORCHESTRATION & PACKAGING       │
                 │   Kubernetes · Probes · HPAs · PDBs · Helm    │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                 ┌───────────────────────────────────────────────┐
                 │        LEVEL 3: PRODUCTION SRE & GITOPS       │
                 │ 12-Factor · Secrets · Observability · CI/CD   │
                 └───────────────────────────────────────────────┘
```

---

## 📊 Skill Matrix & Learning Objectives

| Level | Target Audience | Core Concepts | Files / Components in Repo |
|---|---|---|---|
| **Level 1: Beginner** | Students, Junior Developers, Linux Enthusiasts | Containers vs VMs, Multi-stage Dockerfiles, Docker Compose, Port mapping, Bridge networking, Persistent volumes, Container debugging | [`frontend/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/frontend/Dockerfile), [`services/api/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/services/api/Dockerfile), [`docker-compose.yml`](file:///home/sumit/ai-work/task01/devops-dashboard/docker-compose.yml), [`.dockerignore`](file:///home/sumit/ai-work/task01/devops-dashboard/.dockerignore), [`scripts/dev.sh`](file:///home/sumit/ai-work/task01/devops-dashboard/scripts/dev.sh) |
| **Level 2: Intermediate** | Junior DevOps, Cloud Engineers, Backend Devs | Kubernetes Objects (Deployments, StatefulSets, Services, Ingress), Startup/Liveness/Readiness probes, HPA autoscaling, Zero-trust NetworkPolicies, Helm charts, Templating, Staging/Prod values | [`infra/k8s/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/), [`infra/helm/devops-dashboard/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/helm/devops-dashboard/), [`infra/k8s/TESTING.md`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/TESTING.md) |
| **Level 3: Advanced** | Senior DevOps, SREs, Platform Engineers | 12-Factor compliance, Production Secrets (Vault/ESO), Zero-downtime rollouts, Database reliability, Distributed tracing & Prometheus metrics, CI/CD pipeline design, Chaos engineering | [`services/shared/`](file:///home/sumit/ai-work/task01/devops-dashboard/services/shared/), [`infra/db/migrations/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/db/migrations/), [`infra/k8s/50-network-policies.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/50-network-policies.yaml) |

---

## 🗺️ Documentation Directory

Explore the dedicated guides created for each stage of your learning journey:

1. **[01 — Beginner: Docker & Container Fundamentals](01-beginner-docker-and-containers.md)**
   - Explains containerization theory, multi-stage builds for Java and Next.js, Docker Compose networking, and hands-on container inspection.
2. **[02 — Intermediate: Kubernetes & Helm Orchestration](02-intermediate-kubernetes-and-helm.md)**
   - Explains Kubernetes workload design, cluster networking, probes, autoscaling (HPA), PodDisruptionBudgets, NetworkPolicies, and package management with Helm.
3. **[03 — Advanced: SRE, Security & Production Readiness](03-advanced-sre-and-production.md)**
   - Explains zero-downtime deployment patterns, database connection pooling & migration strategies, External Secrets Operator integration, observability (Prometheus/Grafana/OTel), and CI/CD pipelines.
4. **[04 — Hands-On DevOps Challenges](04-hands-on-devops-challenges.md)**
   - 10 progressive, real-world scenario challenges with verification commands and solution walkthroughs.
5. **[Troubleshooting Guide & Runbook](troubleshooting-guide.md)**
   - Practical flowcharts and triage commands for `CrashLoopBackOff`, `ImagePullBackOff`, `OOMKilled`, probe timeouts, database lockups, and network policy blocks.

---

## 🏁 Recommended Progression Path

### Phase 1: Local Docker Mastery (Week 1–2)
- [ ] Understand the differences between single-stage and multi-stage Docker builds.
- [ ] Inspect the layers and image sizes of [`services/api/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/services/api/Dockerfile) and [`frontend/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/frontend/Dockerfile).
- [ ] Launch the entire stack using `docker compose up --build` or `./scripts/dev.sh`.
- [ ] Inspect container networks using `docker network inspect`.
- [ ] Execute commands directly inside running containers (`docker exec -it ...`).
- [ ] Complete **Challenges 1, 2, and 3** in the [Hands-On DevOps Challenges](04-hands-on-devops-challenges.md).

### Phase 2: Kubernetes & Helm Orchestration (Week 3–4)
- [ ] Set up a local single-node cluster using **Minikube** or **Kind**.
- [ ] Understand why PostgreSQL uses a `StatefulSet` while backend services use `Deployments`.
- [ ] Learn how JVM cold-start delays affect `startupProbe` vs `livenessProbe`.
- [ ] Deploy the application to your local cluster using raw manifests in [`infra/k8s/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/).
- [ ] Package and deploy the application using the Helm chart in [`infra/helm/devops-dashboard/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/helm/devops-dashboard/).
- [ ] Experiment with `values-staging.yaml` and `values-production.yaml`.
- [ ] Complete **Challenges 4, 5, 6, and 7** in the [Hands-On DevOps Challenges](04-hands-on-devops-challenges.md).

### Phase 3: Production Readiness & SRE (Week 5–6)
- [ ] Apply least-privilege `NetworkPolicies` and observe traffic isolation.
- [ ] Configure `PodDisruptionBudgets` and simulate a Kubernetes node drain.
- [ ] Stress-test the API service and observe the Horizontal Pod Autoscaler (HPA) scale replicas from 2 to 8.
- [ ] Design an automated CI/CD pipeline using GitHub Actions to lint, test, build, and deploy this monorepo.
- [ ] Complete **Challenges 8, 9, and 10** in the [Hands-On DevOps Challenges](04-hands-on-devops-challenges.md).

---

## 🎯 Verification & Self-Assessment

Before moving to the next level, ask yourself:
1. *Can I explain why the worker service has 1 replica with a `Recreate` strategy instead of a `RollingUpdate`?*
2. *Can I troubleshoot why a newly deployed pod is stuck in `CrashLoopBackOff`?*
3. *Can I explain how internal DNS works between services in Docker Compose versus Kubernetes?*
4. *Can I modify Helm values to deploy a scaled-down staging environment versus an HA production environment?*

If you can answer these questions with confidence and complete the hands-on challenges, you will have gained practical, job-ready DevOps and platform engineering experience!
