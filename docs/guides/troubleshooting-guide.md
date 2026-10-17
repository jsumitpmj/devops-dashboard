# DevOps Troubleshooting Guide & Runbook

This runbook provides diagnostic commands and step-by-step remediation procedures for common errors encountered when deploying and running the **DevOps Dashboard Platform**.

---

## 🔍 Systematic Triage Workflow

When an issue occurs, follow this diagnostic order:

```
                      1. Check Pod / Container Status
                                    │
                                    ▼
                      2. Describe Events (kubelet warnings)
                                    │
                                    ▼
                      3. Inspect Logs (stdout/stderr & previous crashes)
                                    │
                                    ▼
                      4. Verify Config & Secrets
                                    │
                                    ▼
                      5. Test Network Connectivity
```

---

## 1. Kubernetes Pod States & Resolutions

### 1.1 `CrashLoopBackOff`

The container started, crashed, and Kubernetes is retrying with exponential backoff.

#### Step 1: Check exit code and termination reason
```bash
kubectl describe pod <pod-name> -n devops-dashboard
```
Look at the `Last State` block:
```
Last State:     Terminated
  Reason:       Error
  Exit Code:    1        <--- Exit code indicates cause
  Started:      Sat, 18 Oct 2026 02:00:00 +0000
  Finished:     Sat, 18 Oct 2026 02:00:15 +0000
```

#### Step 2: Check previous logs before the crash
```bash
kubectl logs <pod-name> -n devops-dashboard --previous
```

| Exit Code | Root Cause | Fix |
|---|---|---|
| **`1`** | Unhandled Java/Node Exception or missing DB connection | Check logs for stack traces; verify PostgreSQL is reachable |
| **`137`** | **OOMKilled** (Out Of Memory, `128 + 9 = SIGKILL`) | The container exceeded memory limits. Increase `resources.limits.memory` in deployment manifest or tune JVM heap |
| **`143`** | Graceful termination (`128 + 15 = SIGTERM`) | Pod killed by Kubernetes due to failed liveness probe or deployment rollout |

---

### 1.2 `ImagePullBackOff` / `ErrImageNeverPull`

Kubernetes cannot pull the requested container image.

#### Common Causes & Fixes:
1. **Local Image Not Loaded (Minikube / Kind):**
   When deploying locally without pushing to Docker Hub, Kubernetes cannot find images tagged `devops-dashboard/<svc>:latest`.
   - **Fix:** Load images into cluster:
     ```bash
     # Minikube
     minikube image load devops-dashboard/api:latest
     # Kind
     kind load docker-image devops-dashboard/api:latest --name devops-dashboard
     ```
2. **`imagePullPolicy: Always`:**
   If the manifest specifies `imagePullPolicy: Always`, Kubernetes will ignore local images and attempt to contact Docker Hub.
   - **Fix:** In manifests, set:
     ```yaml
     imagePullPolicy: IfNotPresent
     ```

---

### 1.3 `OOMKilled` (Out of Memory)

Java applications dynamically determine their heap size based on available system memory unless constrained.

#### Diagnostic:
```bash
kubectl describe pod <pod-name> -n devops-dashboard | grep -i oom
```
If output contains `OOMKilled: true`, the container was terminated by the Linux kernel cgroup.

#### Fix:
1. Set explicit JVM RAM percentages in the deployment's `JAVA_TOOL_OPTIONS`:
   ```yaml
   env:
   - name: JAVA_TOOL_OPTIONS
     value: "-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
   ```
2. Increase container limits in the manifest:
   ```yaml
   resources:
     limits:
       memory: "768Mi"
     requests:
       memory: "384Mi"
   ```

---

### 1.4 `CreateContainerConfigError`

The pod cannot start because a referenced `ConfigMap` or `Secret` is missing.

#### Diagnostic:
```bash
kubectl describe pod <pod-name> -n devops-dashboard
```
Look for warnings like:
`Error: configmap "devops-config" not found` or `secret "devops-secrets" not found`.

#### Fix:
Ensure all base configuration manifests are applied first:
```bash
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl apply -f infra/k8s/01-configmap.yaml
kubectl apply -f infra/k8s/02-secrets.yaml
```

---

## 2. Probe Failures & JVM Cold-Start Issues

### Symptom:
A pod enters `Running`, but restarts every 60 seconds with:
`Liveness probe failed: HTTP probe failed with statuscode: 503` or timeout.

### Root Cause:
Java 21 / Spring Boot 3.4 applications on constrained CPU environments (like Minikube on a laptop) can take 30–60 seconds to compile bytecode and initialize the HikariCP connection pool. If the liveness probe begins firing too early, Kubernetes kills the pod before it finishes booting.

### Fix:
Verify the **`startupProbe`** is present:
```yaml
startupProbe:
  httpGet:
    path: /health
    port: 4000
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 15    # 15 * 5s = 75 seconds grace period
```
The `startupProbe` disables liveness/readiness probes until the application responds successfully for the first time.

---

## 3. Database & Connectivity Issues

### 3.1 `Connection refused` (port 5432)
- **Check Postgres pod status:**
  ```bash
  kubectl get pod -l app.kubernetes.io/name=postgres -n devops-dashboard
  ```
- **Check Postgres logs:**
  ```bash
  kubectl logs postgres-0 -n devops-dashboard
  ```
- Ensure the service name in `DATABASE_URL` matches the PostgreSQL service (`postgres`):
  `jdbc:postgresql://postgres:5432/devops_dashboard`

### 3.2 `FATAL: password authentication failed for user "devops"`
This occurs when the password in `02-secrets.yaml` does not match the password stored on the persistent database disk volume (`pgdata` PVC).
- If you changed the password in secrets *after* the database was already initialized, PostgreSQL does not automatically update existing user credentials.
- **Fix:** Connect to PostgreSQL and update the password:
  ```bash
  kubectl exec -it postgres-0 -n devops-dashboard -- psql -U devops -d devops_dashboard -c "ALTER USER devops WITH PASSWORD 'new-password';"
  ```

### 3.3 `relation "users" does not exist`
The schema initialization script did not execute.
- Ensure the `postgres-init` ConfigMap was created before deploying `10-postgres.yaml`:
  ```bash
  kubectl create configmap postgres-init \
    --from-file=01_init.sql=infra/db/init.sql \
    --from-file=02_seed.sql=scripts/seed.sql \
    -n devops-dashboard
  ```
- Trigger schema initialization manually:
  ```bash
  kubectl exec -i postgres-0 -n devops-dashboard -- psql -U devops -d devops_dashboard < infra/db/init.sql
  kubectl exec -i postgres-0 -n devops-dashboard -- psql -U devops -d devops_dashboard < scripts/seed.sql
  ```

---

## 4. Ingress & HTTP Routing Issues

### 4.1 Ingress Returns `404 Not Found`
- **Verify Ingress Controller is Running:**
  ```bash
  kubectl get pods -n ingress-nginx
  # On Minikube: minikube addons enable ingress
  ```
- **Verify Ingress Paths:**
  Ensure the URL path matches the paths in [`infra/k8s/30-ingress.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/30-ingress.yaml):
  - API: `/api/v1/...`
  - Auth: `/auth/...`
  - Frontend: `/...`

### 4.2 Ingress Returns `503 Service Temporarily Unavailable`
- A 503 from Ingress means the Ingress controller cannot find any **Ready endpoints** behind the Kubernetes Service.
- Check endpoints:
  ```bash
  kubectl get endpoints -n devops-dashboard
  ```
  If `ENDPOINTS` shows `<none>`, check `kubectl get pods -n devops-dashboard` to see which pods are failing readiness probes.

---

## 5. Docker Compose Local Development Issues

### 5.1 Port Already in Use (`bind: address already in use`)
```
Error response from daemon: driver failed programming external connectivity on endpoint devops-postgres: Bind for 0.0.0.0:5432 failed: port is already allocated
```
Another PostgreSQL instance (or another container) is running on your machine:
```bash
# Find what process is using port 5432
sudo lsof -i :5432
# Stop local system PostgreSQL if needed:
sudo systemctl stop postgresql
```

### 5.2 Clean Reset of Docker Compose State
To wipe all containers, networks, and orphaned database volumes for a completely fresh start:
```bash
./scripts/dev.sh --clean
./scripts/dev.sh --rebuild
```
