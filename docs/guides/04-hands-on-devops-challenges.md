# Level 4: Hands-On DevOps Challenges & Lab Exercises

This guide contains **10 progressive, hands-on challenges** designed to build practical muscle memory. Work through these challenges on your own machine using the code, Docker configurations, and Kubernetes manifests in this repository.

---

## 🏆 Challenge Index

| # | Challenge | Difficulty | Core Skill |
|---|---|---|---|
| **1** | [Multi-Stage Build Layer Inspection](#challenge-1-multi-stage-build-layer-inspection) | Beginner | Docker caching, layer optimization |
| **2** | [Live Container Diagnostics & Database Shell](#challenge-2-live-container-diagnostics--database-shell) | Beginner | `docker exec`, process inspection, PostgreSQL |
| **3** | [Environment Variable Injection & Config Overrides](#challenge-3-environment-variable-injection--config-overrides) | Beginner | 12-Factor config, Compose env overrides |
| **4** | [Deploy Platform to a Local Kubernetes Cluster](#challenge-4-deploy-platform-to-a-local-kubernetes-cluster) | Intermediate | Minikube/Kind, manifests, port-forwarding |
| **5** | [Simulate Pod Failure & Observe Self-Healing](#challenge-5-simulate-pod-failure--observe-self-healing) | Intermediate | Kubernetes controller reconciliation, restart policies |
| **6** | [Simulate Traffic & Trigger HPA Autoscaling](#challenge-6-simulate-traffic--trigger-hpa-autoscaling) | Intermediate | Apache Bench / hey, Metrics Server, HPA scaling |
| **7** | [Package & Roll Back with Helm](#challenge-7-package--roll-back-with-helm) | Intermediate | Helm releases, values overrides, `helm rollback` |
| **8** | [Enforce Zero-Trust Security with NetworkPolicies](#challenge-8-enforce-zero-trust-security-with-networkpolicies) | Advanced | Kubernetes CNI, NetworkPolicy default-deny |
| **9** | [Author a Monorepo CI Pipeline in GitHub Actions](#challenge-9-author-a-monorepo-ci-pipeline-in-github-actions) | Advanced | CI path filtering, test matrix, automated builds |
| **10** | [Zero-Downtime Rolling Update Under Load](#challenge-10-zero-downtime-rolling-update-under-load) | Advanced | RollingUpdate, readiness probes, preStop hooks |

---

## Challenge 1: Multi-Stage Build Layer Inspection

### 🎯 Objective:
Inspect the Docker image layers and size difference between a single-stage build and our multi-stage build.

### 📝 Task:
1. Build the API service using `docker build`:
   ```bash
   docker build -f services/api/Dockerfile -t devops-dashboard/api:test .
   ```
2. Inspect the image history and layer sizes:
   ```bash
   docker history devops-dashboard/api:test
   ```
3. Check the total image size:
   ```bash
   docker images | grep "devops-dashboard/api"
   ```
4. **Question:** How much disk space did the builder stage save compared to shipping the JDK and Maven repository into the final image?

<details>
<summary>💡 Solution & Explanation</summary>

The builder stage downloaded hundreds of megabytes of Maven dependencies and JDK tools. By copying only `app.jar` into `eclipse-temurin:21-jre-alpine`, the final image is only ~180MB. Running `docker history` shows that the final layer (`COPY --from=builder ... app.jar`) adds only ~40MB onto the base Alpine JRE.
</details>

---

## Challenge 2: Live Container Diagnostics & Database Shell

### 🎯 Objective:
Investigate a running container using interactive terminal commands.

### 📝 Task:
1. Start the platform in the background:
   ```bash
   ./scripts/dev.sh
   ```
2. Use `docker compose exec` to open an interactive shell inside the running `api` container.
3. Verify that the application is running under an unprivileged user (not `root`).
4. Connect to PostgreSQL directly inside the `postgres` container and list all active database connections:
   ```sql
   SELECT pid, usename, client_addr, state FROM pg_stat_activity;
   ```

<details>
<summary>💡 Solution & Explanation</summary>

```bash
# 1. Exec into API container
docker compose exec api sh

# 2. Check user ID (should show uid=100(appuser))
whoami
id

# 3. Connect to PostgreSQL
docker compose exec postgres psql -U devops -d devops_dashboard -c "SELECT pid, usename, client_addr, state FROM pg_stat_activity;"
```
Running as non-root is a critical container security best practice that prevents container breakout attacks.
</details>

---

## Challenge 3: Environment Variable Injection & Config Overrides

### 🎯 Objective:
Change application configuration without recompiling the Java code.

### 📝 Task:
1. Modify `.env` or set an environment override for the worker service to decrease its polling interval from `30000ms` (30 seconds) to `5000ms` (5 seconds):
   ```bash
   POLL_INTERVAL_MS=5000
   ```
2. Restart the worker service:
   ```bash
   docker compose up -d worker
   ```
3. Watch the worker logs to verify that the polling interval changed:
   ```bash
   docker compose logs -f worker
   ```

<details>
<summary>💡 Solution & Explanation</summary>

In `docker-compose.yml`, the worker service reads `POLL_INTERVAL_MS: ${POLL_INTERVAL_MS:-30000}`. By updating `.env` and recreating the container, Spring Boot automatically binds the environment variable to `@Value("${app.worker.poll-interval-ms}")`.
</details>

---

## Challenge 4: Deploy Platform to a Local Kubernetes Cluster

### 🎯 Objective:
Deploy the entire platform onto a local Minikube or Kind cluster using raw manifests.

### 📝 Task:
1. Start a local cluster:
   ```bash
   minikube start --cpus=4 --memory=8192
   ```
2. Build and load the Docker images into the cluster:
   ```bash
   docker compose build
   for svc in auth api deployment notification worker frontend; do
     minikube image load devops-dashboard/$svc:latest
   done
   ```
3. Create the `postgres-init` ConfigMap from local SQL scripts:
   ```bash
   kubectl create configmap postgres-init \
     --from-file=01_init.sql=infra/db/init.sql \
     --from-file=02_seed.sql=scripts/seed.sql \
     -n devops-dashboard
   ```
4. Apply the manifests in [`infra/k8s/`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/):
   ```bash
   kubectl apply -f infra/k8s/
   ```
5. Verify that all pods reach the `Running` state.

<details>
<summary>💡 Solution & Explanation</summary>

```bash
kubectl get pods -n devops-dashboard -w
# Wait until all pods show 1/1 Running.
# If images fail to pull, verify imagePullPolicy is IfNotPresent in the manifests.
```
</details>

---

## Challenge 5: Simulate Pod Failure & Observe Self-Healing

### 🎯 Objective:
Experience Kubernetes automated self-healing when a container process dies.

### 📝 Task:
1. List the pods in the `devops-dashboard` namespace:
   ```bash
   kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=api
   ```
2. Pick one API pod and forcefully kill its main Java process using `kubectl exec`:
   ```bash
   kubectl exec -it <pod-name> -n devops-dashboard -- kill 1
   ```
3. Immediately observe pod statuses:
   ```bash
   kubectl get pods -n devops-dashboard -w
   ```
4. Note the restart count incrementing and the pod returning to `Running` status within seconds.

<details>
<summary>💡 Solution & Explanation</summary>

When PID 1 terminates, the container exits. The `kubelet` detects that the container exited with non-zero or unexpected status and invokes the pod's `restartPolicy: Always`. The ReplicaSet maintains the desired state of 2 replicas.
</details>

---

## Challenge 6: Simulate Traffic & Trigger HPA Autoscaling

### 🎯 Objective:
Generate simulated HTTP load to watch the Horizontal Pod Autoscaler (HPA) scale pods automatically.

### 📝 Task:
1. Ensure `metrics-server` is enabled:
   ```bash
   minikube addons enable metrics-server
   ```
2. Check initial HPA status:
   ```bash
   kubectl get hpa api-hpa -n devops-dashboard
   ```
3. Port-forward the API service:
   ```bash
   kubectl port-forward svc/api 4000:4000 -n devops-dashboard &
   ```
4. Generate load using `hey`, `ab` (Apache Bench), or a simple bash loop:
   ```bash
   # Generate 10,000 requests with 50 concurrent workers
   ab -n 10000 -c 50 http://localhost:4000/health
   ```
5. Watch the HPA scale up:
   ```bash
   kubectl get hpa api-hpa -n devops-dashboard -w
   ```

<details>
<summary>💡 Solution & Explanation</summary>

When CPU utilization exceeds the `averageUtilization: 70%` threshold defined in [`infra/k8s/40-hpa.yaml`](file:///home/sumit/ai-work/task01/devops-dashboard/infra/k8s/40-hpa.yaml), the HPA controller calculates the target replica count:
$$\text{Desired Replicas} = \lceil \text{Current Replicas} \times \frac{\text{Current Metric}}{\text{Target Metric}} \rceil$$
The deployment scales from 2 up to 8 pods to distribute the load.
</details>

---

## Challenge 7: Package & Roll Back with Helm

### 🎯 Objective:
Deploy a configuration change using Helm, observe the new release, and perform a rollback.

### 📝 Task:
1. Deploy the initial release:
   ```bash
   helm upgrade --install devops-dashboard infra/helm/devops-dashboard \
     -f infra/helm/devops-dashboard/values-staging.yaml \
     -n devops-dashboard --create-namespace
   ```
2. Upgrade the release with an override (e.g. scale frontend to 3 replicas):
   ```bash
   helm upgrade devops-dashboard infra/helm/devops-dashboard \
     -f infra/helm/devops-dashboard/values-staging.yaml \
     --set frontend.replicaCount=3 \
     -n devops-dashboard
   ```
3. View release history:
   ```bash
   helm history devops-dashboard -n devops-dashboard
   ```
4. Roll back to revision 1:
   ```bash
   helm rollback devops-dashboard 1 -n devops-dashboard
   ```
5. Confirm frontend pods scaled back down to the default count.

<details>
<summary>💡 Solution & Explanation</summary>

Helm stores release manifests as Kubernetes Secrets inside the namespace. `helm rollback` restores the exact manifest configuration of Revision 1 without requiring manual git reverts.
</details>

---

## Challenge 8: Enforce Zero-Trust Security with NetworkPolicies

### 🎯 Objective:
Verify that the `NetworkPolicy` correctly blocks unauthorized pod communication.

### 📝 Task:
1. Apply the network policies:
   ```bash
   kubectl apply -f infra/k8s/50-network-policies.yaml
   ```
2. Spawn an ephemeral debug pod in the namespace:
   ```bash
   kubectl run hacker-pod --image=busybox -it --rm -n devops-dashboard -- sh
   ```
3. Attempt to connect directly to PostgreSQL on port 5432:
   ```sh
   nc -zv postgres 5432 -w 3
   ```
4. **Result:** The connection should time out! The `postgres` network policy only allows incoming traffic from pods labeled `app.kubernetes.io/name: api`, `auth`, `deployment-svc`, and `worker`.

<details>
<summary>💡 Solution & Explanation</summary>

Kubernetes NetworkPolicies enforce firewall rules at the Linux kernel level (via iptables or eBPF in the CNI plugin). Because `hacker-pod` does not match the permitted `podSelector` labels, all packets are dropped.
</details>

---

## Challenge 9: Author a Monorepo CI Pipeline in GitHub Actions

### 🎯 Objective:
Create a GitHub Actions workflow that executes Maven tests for Java and ESLint for the Next.js frontend on pull requests.

### 📝 Task:
1. Create `.github/workflows/ci.yml`.
2. Configure steps to install Temurin JDK 21 and run `mvn clean test` in `services/`.
3. Configure steps to install Node.js 22 and run `npm test` or build in `frontend/`.
4. Trigger the workflow locally or on GitHub.

<details>
<summary>💡 Solution & Explanation</summary>

Refer to the pipeline architecture in [`docs/guides/03-advanced-sre-and-production.md`](03-advanced-sre-and-production.md#6-monorepo-cicd--gitops-pipeline-architecture) for a reference implementation using path filtering.
</details>

---

## Challenge 10: Zero-Downtime Rolling Update Under Load

### 🎯 Objective:
Prove that updating a microservice in Kubernetes produces zero HTTP errors.

### 📝 Task:
1. Start a continuous loop sending 1 request per 100ms to the API service:
   ```bash
   while true; do
     curl -s -o /dev/null -w "%{http_code}\n" http://localhost:4000/health
     sleep 0.1
   done
   ```
2. In a separate terminal, trigger a rolling update:
   ```bash
   kubectl rollout restart deployment/api -n devops-dashboard
   ```
3. Watch the rollout progress:
   ```bash
   kubectl rollout status deployment/api -n devops-dashboard
   ```
4. **Verification:** Confirm that the curl loop returns strictly `200` without a single `502 Bad Gateway` or connection reset during the entire restart.

<details>
<summary>💡 Solution & Explanation</summary>

Because the API deployment defines `readinessProbe` and `maxUnavailable: 0` (or `maxUnavailable: 1`), Kubernetes never shuts down an old pod until the replacement pod's readiness probe has succeeded and the endpoints have been updated.
</details>
