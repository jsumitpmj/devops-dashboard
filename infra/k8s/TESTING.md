# DevOps Dashboard — Local Kubernetes Testing Guide

This guide covers how to verify every service in the cluster is healthy, how to
log in to PostgreSQL directly, and how to confirm the application is working
end-to-end after a fresh `kubectl apply`.

---

## Prerequisites

```bash
# Confirm the cluster is up and all pods are Running
kubectl get pods -n devops-dashboard

# Expected: every pod shows 1/1 Running, 0 restarts (worker may take a moment)
```

---

## 1. PostgreSQL

### 1.1 Pod health

```bash
kubectl get pod postgres-0 -n devops-dashboard
# READY should be 1/1, STATUS Running
```

### 1.2 Interactive psql login

```bash
kubectl exec -it postgres-0 -n devops-dashboard -- \
  psql -U devops -d devops_dashboard
```

Once inside the `psql` prompt:

```sql
-- List databases
\l

-- List tables (after schema init)
\dt

-- Quick row-count sanity check
SELECT COUNT(*) FROM users;         -- or whatever your seed tables are
SELECT COUNT(*) FROM projects;

-- Exit
\q
```

### 1.3 Check PostgreSQL version

```bash
kubectl exec -it postgres-0 -n devops-dashboard -- psql -U devops -d devops_dashboard -c "SELECT version();"
```

### 1.4 Verify init scripts ran

```bash
kubectl exec -it postgres-0 -n devops-dashboard -- \
  psql -U devops -d devops_dashboard -c "\dt"
# Should list all tables created by infra/db/init.sql
```

### 1.5 Logs

```bash
kubectl logs postgres-0 -n devops-dashboard
# Look for: "database system is ready to accept connections"
```

---

## 2. Auth Service (port 4001)

### 2.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=auth
```

### 2.2 Health endpoint via port-forward

```bash
# Terminal 1 — open the tunnel
kubectl port-forward svc/auth 4001:4001 -n devops-dashboard

# Terminal 2 — hit the health endpoint
curl -s http://localhost:4001/actuator/health | jq .
# Expected: {"status":"UP"}
```

### 2.3 Login smoke test

```bash
curl -s -X POST http://localhost:4001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq .
# Expected: JWT token in response body
```

### 2.4 Logs

```bash
kubectl logs -l app.kubernetes.io/name=auth -n devops-dashboard --tail=50
```

---

## 3. API Gateway (port 4000)

### 3.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=api
```

### 3.2 Health endpoint

```bash
kubectl port-forward svc/api 4000:4000 -n devops-dashboard &

curl -s http://localhost:4000/actuator/health | jq .
# Expected: {"status":"UP"}
```

### 3.3 Authenticated API call

```bash
# Replace <TOKEN> with the JWT from the auth login step above
TOKEN="<your-jwt-here>"

curl -s http://localhost:4000/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 3.4 Logs

```bash
kubectl logs -l app.kubernetes.io/name=api -n devops-dashboard --tail=50
```

---

## 4. Deployment Service (port 4002)

### 4.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=deployment-svc
```

### 4.2 Health endpoint

```bash
kubectl port-forward svc/deployment-svc 4002:4002 -n devops-dashboard &

curl -s http://localhost:4002/actuator/health | jq .
```

### 4.3 Logs

```bash
kubectl logs -l app.kubernetes.io/name=deployment-svc -n devops-dashboard --tail=50
```

---

## 5. Notification Service (port 4003)

### 5.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=notification
```

### 5.2 Health endpoint

```bash
kubectl port-forward svc/notification 4003:4003 -n devops-dashboard &

curl -s http://localhost:4003/actuator/health | jq .
```

### 5.3 Logs

```bash
kubectl logs -l app.kubernetes.io/name=notification -n devops-dashboard --tail=50
```

---

## 6. Worker (port 4004)

> The worker runs with a single replica and `Recreate` strategy — this is by
> design to prevent duplicate job processing.

### 6.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=worker
```

### 6.2 Health endpoint

```bash
kubectl port-forward svc/worker 4004:4004 -n devops-dashboard &

curl -s http://localhost:4004/actuator/health | jq .
```

### 6.3 Logs

```bash
kubectl logs -l app.kubernetes.io/name=worker -n devops-dashboard --tail=50
```

---

## 7. Frontend (port 3000)

### 7.1 Pod health

```bash
kubectl get pods -n devops-dashboard -l app.kubernetes.io/name=frontend
```

### 7.2 Open in browser via port-forward

```bash
kubectl port-forward svc/frontend 3000:3000 -n devops-dashboard
# Then open: http://localhost:3000
```

### 7.3 Logs

```bash
kubectl logs -l app.kubernetes.io/name=frontend -n devops-dashboard --tail=50
```

---

## 8. Full Stack — End-to-End via Ingress

> **Linux + Docker driver note:** `minikube tunnel` does not assign a routable
> `EXTERNAL-IP` to the Ingress on Linux with the Docker driver. Use
> `minikube ip` (the Minikube node IP) directly instead — it is reachable from
> the host without any tunnel.

### Step 1 — Enable the Ingress addon (one-time)

```bash
minikube addons enable ingress

# Wait for the controller pod to be ready (~30s)
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/component=controller \
  -n ingress-nginx --timeout=90s
```

### Step 2 — Get the Minikube node IP

```bash
minikube ip
# Example output: 192.168.49.2
```

### Step 3 — Add an /etc/hosts entry

```bash
# Run once — replace 192.168.49.2 with your actual minikube ip output
echo "$(minikube ip)  dashboard.example.com" | sudo tee -a /etc/hosts
```

### Step 4 — Verify the ingress has an address

```bash
kubectl get ingress -n devops-dashboard
# ADDRESS column should show the minikube node IP after a few seconds
```

### Step 5 — Open the application

```bash
xdg-open http://dashboard.example.com   # Linux
# or just navigate to http://dashboard.example.com in your browser
```

### Troubleshooting — Ingress ADDRESS still empty

```bash
# Check the ingress controller pod is running
kubectl get pods -n ingress-nginx

# Check the ingress controller logs
kubectl logs -n ingress-nginx \
  -l app.kubernetes.io/component=controller --tail=30

# Manually verify reachability using the node IP + NodePort directly
MINIKUBE_IP=$(minikube ip)
curl -s -H "Host: dashboard.example.com" http://$MINIKUBE_IP/
```

---

## 9. Cluster-Wide Checks

### All pod statuses at a glance

```bash
kubectl get pods -n devops-dashboard -o wide
```

### All services and their cluster IPs

```bash
kubectl get svc -n devops-dashboard
```

### Describe a failing pod (replace `<pod-name>`)

```bash
kubectl describe pod <pod-name> -n devops-dashboard
```

### Events (ordered, most recent last)

```bash
kubectl get events -n devops-dashboard --sort-by='.lastTimestamp'
```

### Resource usage (requires metrics-server)

```bash
kubectl top pods -n devops-dashboard
```

---

## 10. After Rebuilding a Local Image

When you rebuild a service image, load it into Minikube and restart the
deployment:

```bash
# 1. Re-tag if needed (only if your build tag differs from the manifest tag)
docker tag devops-dashboard-<svc>:latest devops-dashboard/<svc>:latest

# 2. Load into Minikube
minikube image load devops-dashboard/<svc>:latest

# 3. Force a rolling restart
kubectl rollout restart deployment/<svc> -n devops-dashboard

# 4. Watch the rollout
kubectl rollout status deployment/<svc> -n devops-dashboard
```

Replace `<svc>` with one of: `auth`, `api`, `deployment-svc`, `notification`,
`worker`, `frontend`.

---

## 11. Teardown

```bash
# Delete all app resources (keeps the namespace)
kubectl delete -f infra/k8s/ -n devops-dashboard --ignore-not-found

# Or nuke the namespace entirely
kubectl delete namespace devops-dashboard
```

> **Note:** Deleting the namespace also removes the PVC. PostgreSQL data will
> be lost. Re-run the init steps from README.md to start fresh.
