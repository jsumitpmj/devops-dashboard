# DevOps Dashboard Platform

A production-ready, multi-service monorepo for managing deployments, environments, alerts, and audit history.

**Backend:** Java 21 + Spring Boot 3.4 · **Frontend:** Next.js 15 + React 19 · **Database:** PostgreSQL 18

---

## Architecture

```
                           ┌──────────────────────┐
                           │   Frontend (Next.js)  │
                           │       :3000            │
                           └────────┬───┬──────────┘
                                    │   │
                    ┌───────────────┘   └───────────────┐
                    ▼                                   ▼
          ┌─────────────────┐                 ┌─────────────────┐
          │   API Gateway   │                 │   Auth Service   │
          │     :4000       │                 │     :4001        │
          └────────┬────────┘                 └─────────────────┘
                   │
       ┌───────────┼───────────┐
       ▼           ▼           ▼
┌────────────┐ ┌────────────┐ ┌────────────┐
│ Deployment │ │Notification│ │   Worker   │
│   :4002    │ │   :4003    │ │   :4004    │
└────────────┘ └─────▲──────┘ └────┬───────┘
                     │              │ (REST)
                     └──────────────┘
                           │
                    ┌──────┴──────┐
                    │ PostgreSQL  │
                    │    :5432    │
                    └─────────────┘
```

| Service | Port | Tech | Description |
|---------|------|------|-------------|
| **Frontend** | 3000 | Next.js 15 | Dashboard UI with login, projects, deployments, alerts, audit logs |
| **API** | 4000 | Spring Boot | Read-heavy gateway — projects, environments, deployments, alerts, audit logs |
| **Auth** | 4001 | Spring Boot | Registration, login, JWT generation, token validation |
| **Deployment** | 4002 | Spring Boot | Deployment lifecycle — create, status transitions, env lock enforcement |
| **Notification** | 4003 | Spring Boot | Email + Slack notification handlers (strategy pattern) |
| **Worker** | 4004 | Spring Boot | Background jobs — stale deployment detection, alert escalation, env sync |
| **PostgreSQL** | 5432 | PostgreSQL 18 | 8 tables with FK relationships, indexes, triggers |

---

## Quick Start

### Prerequisites

| | Windows | Ubuntu / Linux |
|-|---------|----------------|
| **Docker** | [Docker Desktop](https://docs.docker.com/desktop/install/windows-install/) | `sudo apt install docker.io docker-compose-v2` |
| **Git** | [Git for Windows](https://git-scm.com/download/win) | `sudo apt install git` |
| **Java** (optional) | [Temurin JDK 21](https://adoptium.net/) | `sudo apt install openjdk-21-jdk` |
| **Node.js** (optional) | [Node.js 22 LTS](https://nodejs.org/) | `sudo apt install nodejs npm` |
| **Maven** (optional) | [Apache Maven](https://maven.apache.org/) | `sudo apt install maven` |

> Java, Node.js, and Maven are only needed if you want to develop services outside Docker.

### 1. Clone and configure

**Both OS:**
```bash
git clone <repo-url> devops-dashboard
cd devops-dashboard
```

**Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
```

**Ubuntu / Linux:**
```bash
cp .env.example .env
```

### 2. Start everything

**Both OS (Docker Compose is identical):**
```bash
docker compose up --build
```

**Or use the dev helper scripts:**

| Windows (PowerShell) | Ubuntu / Linux | Description |
|---------------------|----------------|-------------|
| `.\scripts\dev.ps1` | `./scripts/dev.sh` | Start all (auto-init DB) |
| `.\scripts\dev.ps1 -Rebuild` | `./scripts/dev.sh --rebuild` | Rebuild + start |
| `.\scripts\dev.ps1 -DbOnly` | `./scripts/dev.sh --db-only` | PostgreSQL only |
| `.\scripts\dev.ps1 -Build` | `./scripts/dev.sh --build` | Maven build + start |
| `.\scripts\dev.ps1 -Down` | `./scripts/dev.sh --down` | Stop containers |
| `.\scripts\dev.ps1 -Clean` | `./scripts/dev.sh --clean` | Stop + remove volumes |
| `.\scripts\dev.ps1 -Logs` | `./scripts/dev.sh --logs` | Tail all logs |
| `.\scripts\dev.ps1 -Status` | `./scripts/dev.sh --status` | Health + DB status |
| `.\scripts\dev.ps1 -InitDb` | `./scripts/dev.sh --init-db` | Init schema + seed |
| `.\scripts\dev.ps1 -Seed` | `./scripts/dev.sh --seed` | Re-run seed data |
| `.\scripts\dev.ps1 -ResetDb` | `./scripts/dev.sh --reset-db` | Drop + recreate DB |

> 📝 On **first start**, the script auto-detects an empty database and runs `init.sql` + `seed.sql` automatically — no manual step needed.

**Ubuntu / Linux also supports Make:**
```bash
make help        # Show all targets
make up          # Start all services
make rebuild     # Rebuild + start
make down        # Stop
make clean       # Stop + remove volumes
make health      # Check all health endpoints
make test        # Run all Java tests
make init-db     # Initialize DB (schema + seed)
make seed        # Re-run seed data only
make reset-db    # Drop all + reinitialize
make db-status   # Show table/row counts
make db-shell    # Open psql shell
```

> ⚠️ On Ubuntu, if `./scripts/dev.sh` gives a permission error, run: `chmod +x scripts/dev.sh`

### 3. Open the dashboard

| URL | What |
|-----|------|
| **http://localhost:3000** | Frontend dashboard |
| http://localhost:4000/health | API health |
| http://localhost:4001/health | Auth health |
| http://localhost:4002/health | Deployment health |
| http://localhost:4003/health | Notification health |
| http://localhost:4004/health | Worker health |

### 4. Login with seed users

| Email | Password | Roles |
|-------|----------|-------|
| `admin@devops.local` | `password123` | admin |
| `alice@devops.local` | `password123` | developer |
| `bob@devops.local` | `password123` | developer, deployer |
| `carol@devops.local` | `password123` | viewer |

### 5. Verify all services

**Windows (PowerShell):**
```powershell
.\scripts\dev.ps1 -Status
```

**Ubuntu / Linux:**
```bash
./scripts/dev.sh --status
# or
make health
```

**Manual check (both OS):**
```bash
curl http://localhost:4000/health
curl http://localhost:4001/health
curl http://localhost:4002/health
curl http://localhost:4003/health
curl http://localhost:4004/health
curl http://localhost:4004/worker/stats
```

---

## Development Workflows

### Backend only (no frontend)

```bash
docker compose up --build postgres api auth deployment notification worker
```

### Frontend only (requires running backend)

```bash
# Start backend services
docker compose up -d postgres api auth deployment notification worker

# Run frontend with hot reload
cd frontend
npm install
npm run dev
```

The frontend dev server proxies `/api/v1/*` → port 4000 and `/auth/*` → port 4001 automatically.

### Individual service (for debugging)

```bash
# Start dependencies
docker compose up -d postgres

# Run a specific service with Maven
cd services
mvn clean install -pl shared -DskipTests
mvn spring-boot:run -pl api
```

### Run tests

**Windows (PowerShell):**
```powershell
cd services
mvn clean test                                    # all services
mvn clean test -pl shared,api -am                 # API only
```

**Ubuntu / Linux:**
```bash
cd services
mvn clean test                                    # all services
# or
make test                                         # from project root
```

### Reset database

**Windows (PowerShell):**
```powershell
.\scripts\dev.ps1 -Clean
.\scripts\dev.ps1 -Rebuild
```

**Ubuntu / Linux:**
```bash
./scripts/dev.sh --clean
./scripts/dev.sh --rebuild
# or
make clean && make rebuild
```

---

## Project Structure

```
├── frontend/                      # Next.js 15 dashboard
│   ├── Dockerfile                 # Multi-stage Node 22 build
│   ├── README.md                  # Frontend dev guide + design system
│   ├── src/
│   │   ├── app/                   # App Router pages
│   │   │   ├── login/             # Login + register
│   │   │   ├── dashboard/         # Overview with stats
│   │   │   ├── projects/          # Project list + detail
│   │   │   ├── deployments/       # Filterable deployment view
│   │   │   ├── alerts/            # Alert monitoring
│   │   │   └── audit-logs/        # Admin-only audit trail
│   │   ├── components/            # AppShell, Sidebar
│   │   └── lib/                   # API client, auth context
│   └── next.config.ts             # API proxy rewrites
│
├── services/                      # Java backend monorepo
│   ├── pom.xml                    # Maven parent POM
│   ├── README.md                  # Module structure + build commands
│   ├── shared/                    # Shared JPA entities, repos, security, DTOs
│   ├── api/                       # REST API gateway (:4000)
│   ├── auth/                      # Auth + JWT (:4001)
│   ├── deployment/                # Deployment lifecycle (:4002)
│   ├── notification/              # Email/Slack handlers (:4003)
│   └── worker/                    # Background polling jobs (:4004)
│
├── infra/
│   ├── db/
│   │   ├── init.sql               # Full schema (8 tables, indexes, triggers)
│   │   └── migrations/            # Incremental SQL migrations
│   └── k8s/                       # Kubernetes manifests (15 files)
│
├── docs/
│   └── api-examples.md            # Curl examples for every endpoint
│
├── scripts/
│   ├── dev.ps1                    # Dev startup (Windows / PowerShell)
│   ├── dev.sh                     # Dev startup (Ubuntu / Linux / macOS)
│   └── seed.sql                   # Dev seed data (users, projects, deployments)
│
├── docker-compose.yml             # Local development orchestration
├── Makefile                       # Build/dev targets (Linux / macOS)
├── .gitattributes                 # Cross-platform line ending rules
├── .env.example                   # Environment variable template
└── README.md
```

---

## API Reference

> 📖 **Full curl examples:** see [`docs/api-examples.md`](docs/api-examples.md) for a complete copy-paste guide with request/response samples.

### Auth Service (:4001)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | Public | Register new user |
| POST | `/auth/login` | Public | Login → JWT |
| GET | `/auth/me` | Bearer | Current user info |

### API Service (:4000)

| Method | Path | Auth | Role | Description |
|--------|------|------|------|-------------|
| GET | `/api/v1/projects` | Bearer | any | List active projects |
| GET | `/api/v1/projects/{id}` | Bearer | any | Get project by ID |
| GET | `/api/v1/projects/slug/{slug}` | Bearer | any | Get project by slug |
| POST | `/api/v1/projects` | Bearer | admin, developer | Create project |
| GET | `/api/v1/environments` | Bearer | any | List environments (?projectId) |
| GET | `/api/v1/environments/{id}` | Bearer | any | Get environment |
| POST | `/api/v1/environments` | Bearer | admin, developer | Create environment |
| GET | `/api/v1/deployments` | Bearer | any | List with filters + pagination |
| GET | `/api/v1/deployments/{id}` | Bearer | any | Get deployment |
| GET | `/api/v1/deployments/stats` | Bearer | any | Pending/total counts |
| GET | `/api/v1/alerts` | Bearer | any | List alerts (filtered) |
| GET | `/api/v1/alerts/{id}` | Bearer | any | Get alert |
| GET | `/api/v1/audit-logs` | Bearer | admin | List audit logs |

### Deployment Service (:4002)

| Method | Path | Auth | Role | Description |
|--------|------|------|------|-------------|
| POST | `/api/v1/deployments` | Bearer | admin, developer, deployer | Create deployment |
| PUT | `/api/v1/deployments/{id}/status` | Bearer | admin, developer, deployer | Update status |
| GET | `/api/v1/deployments` | Bearer | any | List deployments |
| GET | `/api/v1/deployments/{id}` | Bearer | any | Get deployment |
| GET | `/api/v1/deployments/stats` | Bearer | any | Stats |

### Notification Service (:4003)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/notifications/send` | Internal | Sync notification dispatch |
| POST | `/api/v1/notifications/send-async` | Internal | Fire-and-forget dispatch |
| GET | `/api/v1/notifications/channels` | Internal | List registered channels |
| GET | `/api/v1/alerts` | Bearer | Alert listing |

### Worker Service (:4004)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/worker/stats` | Public | Queue depth stats |

---

## Database Schema

8 tables with full FK relationships, managed by `infra/db/init.sql`:

| Table | Description |
|-------|-------------|
| `roles` | Authorization roles with JSONB permissions |
| `users` | Platform users with bcrypt credentials |
| `user_roles` | Many-to-many user↔role mapping |
| `projects` | Top-level project entities |
| `environments` | Deployment targets (dev/staging/prod) per project |
| `deployments` | Individual deployment runs with lifecycle timestamps |
| `alerts` | Operational alerts linked to deployments/environments |
| `audit_logs` | Append-only audit trail |

---

## Environment Variables

See [.env.example](.env.example) for all options. Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USER` | `devops` | PostgreSQL username |
| `DB_PASSWORD` | `devops` | PostgreSQL password |
| `JWT_SECRET` | (dev default) | JWT signing key (min 32 chars) |
| `POLL_INTERVAL_MS` | `30000` | Worker deployment poll interval |
| `WORKER_STALE_TIMEOUT_MINS` | `30` | Minutes before in_progress → failed |
| `NOTIFICATION_EMAIL_ENABLED` | `false` | Enable email channel |
| `NOTIFICATION_SLACK_ENABLED` | `false` | Enable Slack channel |
| `NOTIFICATION_SLACK_WEBHOOK` | — | Slack incoming webhook URL |
| `LOG_LEVEL` | `info` | Global log level |
---

## Kubernetes Deployment

Production-ready manifests are in [`infra/k8s/`](infra/k8s/).

### Manifest Layout

| File | Resources |
|------|-----------|
| `00-namespace.yaml` | Namespace `devops-dashboard` |
| `01-configmap.yaml` | Shared config (DB URL, ports, polling intervals, notification settings) |
| `02-secrets.yaml` | Secret templates (DB password, JWT secret, Slack webhook) |
| `10-postgres.yaml` | PostgreSQL StatefulSet + PVC + init ConfigMap (dev only) |
| `20-auth.yaml` | Auth Deployment (2 replicas) + Service |
| `21-api.yaml` | API Deployment (2 replicas) + Service |
| `22-deployment-svc.yaml` | Deployment Service (2 replicas) + Service |
| `23-notification.yaml` | Notification Deployment (1 replica) + Service |
| `24-worker.yaml` | Worker Deployment (1 replica, Recreate strategy) + Service |
| `25-frontend.yaml` | Frontend Deployment (2 replicas) + Service |
| `30-ingress.yaml` | Ingress with path-based routing + TLS placeholder |
| `40-hpa.yaml` | HPA for api (2-8), auth (2-6), deployment-svc (2-6), frontend (2-6) |
| `50-network-policies.yaml` | Default deny + explicit allow rules |
| `51-pdb.yaml` | PodDisruptionBudgets (minAvailable: 1) |

### Quick Deploy

```bash
# 1. Create namespace + config + secrets
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl apply -f infra/k8s/01-configmap.yaml

# 2. Edit secrets with real base64 values, then apply
#    echo -n 'your-real-password' | base64
kubectl apply -f infra/k8s/02-secrets.yaml

# 3. Create PostgreSQL init ConfigMap from SQL files
kubectl create configmap postgres-init \
  --from-file=01_init.sql=infra/db/init.sql \
  --from-file=02_seed.sql=scripts/seed.sql \
  -n devops-dashboard

# 4. Deploy PostgreSQL (dev only — use managed DB in production)
kubectl apply -f infra/k8s/10-postgres.yaml

# 5. Build and push images (adjust registry as needed)
docker build -f services/auth/Dockerfile -t your-registry/devops-auth:latest .
docker build -f services/api/Dockerfile -t your-registry/devops-api:latest .
docker build -f services/deployment/Dockerfile -t your-registry/devops-deployment:latest .
docker build -f services/notification/Dockerfile -t your-registry/devops-notification:latest .
docker build -f services/worker/Dockerfile -t your-registry/devops-worker:latest .
docker build -f frontend/Dockerfile -t your-registry/devops-frontend:latest .

# 6. Update image references in manifests, then deploy everything
kubectl apply -f infra/k8s/

# 7. Verify
kubectl get pods -n devops-dashboard
kubectl get svc -n devops-dashboard
kubectl get ingress -n devops-dashboard
```

### Health Probes (all services)

| Probe | Path | Delay | Period | Threshold |
|-------|------|-------|--------|-----------|
| Startup | `/health` | 10s | 5s | 15 failures |
| Liveness | `/health` | 45s | 15s | 3 failures |
| Readiness | `/health` | 20s | 10s | 3 failures |

### Autoscaling

| Service | Min | Max | CPU Target | Memory Target |
|---------|-----|-----|------------|---------------|
| api | 2 | 8 | 70% | 80% |
| auth | 2 | 6 | 70% | 80% |
| deployment-svc | 2 | 6 | 70% | 80% |
| frontend | 2 | 6 | 70% | — |
| notification | 1 | — | *(not scaled)* | — |
| worker | 1 | — | *(not scaled — single leader)* | — |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 15, React 19, Recharts |
| Backend | Java 21, Spring Boot 3.4 |
| ORM | Spring Data JPA (Hibernate) |
| Auth | BCrypt + JWT (jjwt) |
| Database | PostgreSQL 18 |
| Build | Maven multi-module (backend), npm (frontend) |
| Containers | Docker multi-stage builds |
| Orchestration | Docker Compose (dev), Kubernetes (prod) |
| Dev Scripts | PowerShell (Windows), Bash + Make (Ubuntu/macOS) |
| Cross-platform | `.gitattributes` line ending rules |

---

## License

Private — Internal use only.
