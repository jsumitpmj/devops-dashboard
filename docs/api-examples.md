# API Examples

Curl commands for testing all endpoints. Replace `$TOKEN` with a valid JWT.

## Auth Service (:4001)

### Register a new user

```bash
curl -X POST http://localhost:4001/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newuser@devops.local",
    "username": "newuser",
    "password": "securepass123",
    "displayName": "New User"
  }'
```

### Login

```bash
curl -X POST http://localhost:4001/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@devops.local",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": "b0000000-...",
    "email": "admin@devops.local",
    "username": "admin",
    "displayName": "Admin User",
    "status": "active",
    "roles": ["admin"]
  }
}
```

### Get current user

```bash
# Save token from login response
TOKEN="eyJhbGciOi..."

curl http://localhost:4001/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## API Service (:4000)

### List projects

```bash
curl http://localhost:4000/api/v1/projects \
  -H "Authorization: Bearer $TOKEN"
```

### Get project by slug

```bash
curl http://localhost:4000/api/v1/projects/slug/platform-api \
  -H "Authorization: Bearer $TOKEN"
```

### Create a project

```bash
curl -X POST http://localhost:4000/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My New Service",
    "slug": "my-new-service",
    "description": "A brand new microservice",
    "repoUrl": "https://github.com/acme/my-new-service"
  }'
```

### List environments for a project

```bash
curl "http://localhost:4000/api/v1/environments?projectId=c0000000-0000-0000-0000-000000000001" \
  -H "Authorization: Bearer $TOKEN"
```

### Create an environment

```bash
curl -X POST http://localhost:4000/api/v1/environments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "c0000000-0000-0000-0000-000000000001",
    "name": "canary",
    "envType": "staging",
    "url": "https://api-canary.acme.internal"
  }'
```

### List deployments (with filters)

```bash
# All deployments
curl http://localhost:4000/api/v1/deployments \
  -H "Authorization: Bearer $TOKEN"

# Filter by status
curl "http://localhost:4000/api/v1/deployments?status=failed" \
  -H "Authorization: Bearer $TOKEN"

# Filter by project
curl "http://localhost:4000/api/v1/deployments?projectId=c0000000-0000-0000-0000-000000000001" \
  -H "Authorization: Bearer $TOKEN"
```

### Deployment stats

```bash
curl http://localhost:4000/api/v1/deployments/stats \
  -H "Authorization: Bearer $TOKEN"
```

### List alerts

```bash
curl http://localhost:4000/api/v1/alerts \
  -H "Authorization: Bearer $TOKEN"

# Filter by status
curl "http://localhost:4000/api/v1/alerts?status=open" \
  -H "Authorization: Bearer $TOKEN"
```

### List audit logs (admin only)

```bash
curl http://localhost:4000/api/v1/audit-logs \
  -H "Authorization: Bearer $TOKEN"
```

---

## Deployment Service (:4002)

### Create a deployment

```bash
curl -X POST http://localhost:4002/api/v1/deployments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "c0000000-0000-0000-0000-000000000001",
    "environmentId": "d0000000-0000-0000-0000-000000000001",
    "gitRef": "main",
    "gitSha": "abc123def456",
    "imageTag": "platform-api:1.3.0"
  }'
```

### Update deployment status

```bash
curl -X PUT http://localhost:4002/api/v1/deployments/{id}/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "succeeded"
  }'
```

---

## Notification Service (:4003)

### Send notification (sync)

```bash
curl -X POST http://localhost:4003/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "deployment.succeeded",
    "title": "Deploy to prod succeeded",
    "message": "v2.1.0 deployed to production",
    "channels": ["email", "slack"],
    "severity": "info",
    "projectId": "c0000000-0000-0000-0000-000000000001"
  }'
```

### Send notification (async, fire-and-forget)

```bash
curl -X POST http://localhost:4003/api/v1/notifications/send-async \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "alert.critical",
    "title": "Database connection pool exhausted",
    "channels": ["email", "slack"],
    "severity": "critical"
  }'
# Returns 202 Accepted immediately
```

### List notification channels

```bash
curl http://localhost:4003/api/v1/notifications/channels
```

---

## Health Checks

```bash
# All services
for port in 4000 4001 4002 4003 4004; do
  echo "--- :$port ---"
  curl -s http://localhost:$port/health | python3 -m json.tool
done

# Worker stats
curl http://localhost:4004/worker/stats
```

---

## Workflow: Full Login → Deploy → Check

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:4001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@devops.local","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: ${TOKEN:0:20}..."

# 2. List projects
curl -s http://localhost:4000/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 3. Create a deployment
DEPLOY_ID=$(curl -s -X POST http://localhost:4002/api/v1/deployments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "c0000000-0000-0000-0000-000000000001",
    "environmentId": "d0000000-0000-0000-0000-000000000001",
    "gitRef": "main",
    "imageTag": "platform-api:1.3.0"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

echo "Created deployment: $DEPLOY_ID"

# 4. Mark as succeeded
curl -X PUT "http://localhost:4002/api/v1/deployments/$DEPLOY_ID/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"succeeded"}'

# 5. Check dashboard stats
curl -s http://localhost:4000/api/v1/deployments/stats \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```
