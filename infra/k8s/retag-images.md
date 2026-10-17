# Note: If built using `docker compose build`, images are already tagged as devops-dashboard/<svc>:latest.
# If built individually or using older Compose defaults, run the retag below:
docker tag devops-dashboard-auth:latest devops-dashboard/auth:latest 2>/dev/null || true
docker tag devops-dashboard-api:latest devops-dashboard/api:latest 2>/dev/null || true
docker tag devops-dashboard-deployment:latest devops-dashboard/deployment:latest 2>/dev/null || true
docker tag devops-dashboard-notification:latest devops-dashboard/notification:latest 2>/dev/null || true
docker tag devops-dashboard-worker:latest devops-dashboard/worker:latest 2>/dev/null || true
docker tag devops-dashboard-frontend:latest devops-dashboard/frontend:latest 2>/dev/null || true
echo "All images tagged as devops-dashboard/<svc>:latest"

for svc in auth api deployment notification worker frontend; do
  echo "==> Loading devops-dashboard/$svc:latest into minikube..."
  minikube image load devops-dashboard/$svc:latest
  echo "    Done: $svc"
done
echo "All images loaded."

kubectl rollout restart deployment -n devops-dashboard
kubectl get pods -n devops-dashboard