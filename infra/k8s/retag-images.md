# Loading Images into Local Kubernetes Clusters

When deploying microservices to a local Kubernetes cluster (such as Minikube, Kind, or k3d), the cluster nodes cannot pull images from your local Docker daemon by default. Unless you push images to Docker Hub or an external container registry, you must load your locally built images directly into the cluster's internal container runtime image cache.

---

## 1. Image Tagging Convention

All manifests in `infra/k8s/` and the Helm chart reference images using the following naming standard:
```
devops-dashboard/<service>:latest
```

When building with `docker compose build`, images are automatically tagged with this prefix.

If you built individual services manually with legacy tags (`devops-dashboard-<svc>:latest`), tag them first:

```bash
docker tag devops-dashboard-auth:latest devops-dashboard/auth:latest 2>/dev/null || true
docker tag devops-dashboard-api:latest devops-dashboard/api:latest 2>/dev/null || true
docker tag devops-dashboard-deployment:latest devops-dashboard/deployment:latest 2>/dev/null || true
docker tag devops-dashboard-notification:latest devops-dashboard/notification:latest 2>/dev/null || true
docker tag devops-dashboard-worker:latest devops-dashboard/worker:latest 2>/dev/null || true
docker tag devops-dashboard-frontend:latest devops-dashboard/frontend:latest 2>/dev/null || true
```

---

## 2. Loading Images by Cluster Type

### Option A: Minikube
Run this loop to load all platform images into Minikube:

```bash
for svc in auth api deployment notification worker frontend; do
  echo "==> Loading devops-dashboard/$svc:latest into minikube..."
  minikube image load devops-dashboard/$svc:latest
  echo "    Done: $svc"
done
```

**Verify loaded images:**
```bash
minikube image ls | grep devops-dashboard
```

---

### Option B: Kind (Kubernetes in Docker)
Run this loop to load images into your Kind cluster (assuming cluster name `devops-dashboard`):

```bash
CLUSTER_NAME="devops-dashboard"

for svc in auth api deployment notification worker frontend; do
  echo "==> Loading devops-dashboard/$svc:latest into kind ($CLUSTER_NAME)..."
  kind load docker-image devops-dashboard/$svc:latest --name "$CLUSTER_NAME"
  echo "    Done: $svc"
done
```

---

### Option C: k3d / k3s
Load images into a k3d cluster:

```bash
CLUSTER_NAME="devops-dashboard"

for svc in auth api deployment notification worker frontend; do
  echo "==> Importing devops-dashboard/$svc:latest into k3d ($CLUSTER_NAME)..."
  k3d image import devops-dashboard/$svc:latest -c "$CLUSTER_NAME"
  echo "    Done: $svc"
done
```

---

### Option D: Docker Desktop (Kubernetes enabled)
If using Docker Desktop's built-in single-node Kubernetes cluster:
- **No loading is required!**
- The cluster shares the exact same Docker engine daemon as your host.
- As long as `docker compose build` succeeds, images are immediately available to the cluster.

---

## 3. Important: `imagePullPolicy`

To ensure Kubernetes uses your locally loaded image instead of attempting to fetch it from Docker Hub:
- All deployment manifests must specify:
  ```yaml
  imagePullPolicy: IfNotPresent
  ```
- If set to `Always`, the pod will fail with `ErrImageNeverPull` or `ImagePullBackOff`.

---

## 4. Triggering a Rollout Restart

After loading updated images into your cluster, trigger a rolling restart to force pods to recreate with the new image:

```bash
kubectl rollout restart deployment -n devops-dashboard
kubectl rollout status deployment -n devops-dashboard
kubectl get pods -n devops-dashboard
```
