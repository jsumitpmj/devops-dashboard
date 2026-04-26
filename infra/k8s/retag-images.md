docker tag devops-dashboard-auth:latest devops-dashboard/auth:latest && \
docker tag devops-dashboard-api:latest devops-dashboard/api:latest && \
docker tag devops-dashboard-deployment:latest devops-dashboard/deployment:latest && \
docker tag devops-dashboard-notification:latest devops-dashboard/notification:latest && \
docker tag devops-dashboard-worker:latest devops-dashboard/worker:latest && \
docker tag devops-dashboard-frontend:latest devops-dashboard/frontend:latest && \
echo "All images re-tagged successfully"

for svc in auth api deployment notification worker frontend; do
  echo "==> Loading devops-dashboard/$svc:latest into minikube..."
  minikube image load devops-dashboard/$svc:latest
  echo "    Done: $svc"
done
echo "All images loaded."

kubectl rollout restart deployment -n devops-dashboard
kubectl get pods -n devops-dashboard