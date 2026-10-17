# Level 1: Docker & Container Fundamentals

This guide is designed for beginners and DevOps students. It walks through how containerization works, why multi-stage builds are critical in production, how Docker Compose coordinates microservices, and how to debug containers when things go wrong.

---

## 1. Containers vs Virtual Machines

To understand why DevOps relies on containers, contrast them with traditional Virtual Machines (VMs):

```
     ┌────────────────────────┐             ┌────────────────────────┐
     │   App A   │   App B    │             │   App A   │   App B    │
     ├───────────┴────────────┤             ├───────────┼────────────┤
     │  Bins / Libs / Runtime │             │ Guest OS  │  Guest OS  │
     ├────────────────────────┤             ├───────────┴────────────┤
     │     Docker Engine      │             │       Hypervisor       │
     ├────────────────────────┤             ├────────────────────────┤
     │        Host OS         │             │        Host OS         │
     ├────────────────────────┤             ├────────────────────────┤
     │   Physical Hardware    │             │   Physical Hardware    │
     └────────────────────────┘             └────────────────────────┘
          CONTAINERS                               VIRTUAL MACHINES
```

- **Virtual Machines** virtualize the hardware. Each VM runs a full guest operating system (kernel, system services, memory allocation), requiring gigabytes of RAM and taking minutes to boot.
- **Containers** virtualize the operating system kernel. All containers share the host Linux kernel, using kernel features called **namespaces** (for process, mount, and network isolation) and **cgroups** (for CPU and memory limits). A container starts in milliseconds and consumes megabytes instead of gigabytes.

---

## 2. Multi-Stage Docker Builds

In a production environment, you should never ship build tools (like compilers, Maven, or the full Node SDK) into production images. Extra tools increase image size (slowing deployment) and expand the security attack surface.

This repository uses **multi-stage builds** across both backend and frontend.

### 2.1 Backend Java Multi-Stage Build

Look at [`services/api/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/services/api/Dockerfile):

```dockerfile
# -----------------------------------------------------------
# STAGE 1: Build Stage (Includes JDK and Maven tools)
# -----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 1. Copy POM files first to leverage Docker layer caching
COPY pom.xml .
COPY shared/pom.xml shared/
COPY api/pom.xml api/
# ... copy other service POMs

# 2. Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# 3. Copy source code and build the application JAR
COPY shared/src shared/src
COPY api/src api/src
RUN mvn clean package -pl shared,api -am -DskipTests

# -----------------------------------------------------------
# STAGE 2: Runtime Stage (Lightweight JRE only)
# -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as an unprivileged non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy ONLY the compiled JAR from the builder stage
COPY --from=builder /build/api/target/api-*.jar app.jar

EXPOSE 4000

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Why this matters:
1. **Size Reduction:** The `jdk` image with Maven and source files is ~800MB. The final runtime `jre-alpine` image is ~180MB (over 75% smaller).
2. **Security:** The runtime container has no compiler, no source code, and no package manager tools an attacker could exploit.
3. **Layer Caching:** By copying `pom.xml` before `src/`, Docker caches the Maven dependencies layer. When you modify a single Java file, `docker build` skips re-downloading dependencies and only compiles your code.

---

### 2.2 Frontend Next.js Multi-Stage Build

Look at [`frontend/Dockerfile`](file:///home/sumit/ai-work/task01/devops-dashboard/frontend/Dockerfile):

```dockerfile
# Stage 1: Dependencies
FROM node:22-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

# Stage 2: Builder
FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN mkdir -p /app/public
RUN npm run build

# Stage 3: Runner
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy Next.js standalone output (only necessary production files)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
```

#### Key Concepts:
- **`npm ci` vs `npm install`:** `npm ci` does a clean, deterministic install directly from `package-lock.json`. It is faster and guarantees identical dependencies across environments.
- **Standalone Output:** Next.js `output: 'standalone'` traces your code and extracts *only* the specific node modules actually needed, ignoring dev dependencies.
- **`public/` Safety:** We ensure `public/` exists so Docker does not fail if an empty directory is referenced.

---

### 2.3 The Role of `.dockerignore`

Whenever Docker runs `docker build`, it transmits all files from the current folder (the **build context**) to the Docker daemon.

Without [`.dockerignore`](file:///home/sumit/ai-work/task01/devops-dashboard/.dockerignore), Docker will copy `.git` history (often hundreds of megabytes), local Java `target/` folders, and local `node_modules/`. This makes builds painfully slow and leaks local state into containers.

Our root `.dockerignore` ensures only essential source code is transferred:

```dockerignore
.git
.github
target/
**/target/
node_modules/
**/node_modules/
.next/
*.log
```

---

## 3. Docker Compose & Multi-Service Orchestration

While `docker run` starts single containers, [`docker-compose.yml`](file:///home/sumit/ai-work/task01/devops-dashboard/docker-compose.yml) orchestrates the entire multi-service platform.

```
                         Host Port 3000          Host Port 4000
                               │                       │
                               ▼                       ▼
                     ┌───────────────────┐   ┌───────────────────┐
                     │     frontend      │   │        api        │
                     └─────────┬─────────┘   └─────────┬─────────┘
                               │                       │
═══════════════════════════════╪═══════════════════════╪═════════════════════
   Docker Bridge Network       │   dashboard-network   │
   (Internal DNS: 127.0.0.11)  ▼                       ▼
                     ┌───────────────────┐   ┌───────────────────┐
                     │       auth        │   │     postgres      │
                     └───────────────────┘   └─────────┬─────────┘
═══════════════════════════════════════════════════════╪═════════════════════
                                                       ▼
                                             ┌───────────────────┐
                                             │ Named Volume:     │
                                             │ pgdata            │
                                             └───────────────────┘
```

### Key Orchestration Concepts in `docker-compose.yml`:

1. **Service Discovery via Internal DNS:**
   - In Docker Compose, containers on the same user-defined network automatically resolve each other by **service name**.
   - The API service connects to Postgres via:
     `DATABASE_URL=jdbc:postgresql://postgres:5432/devops_dashboard`
   - You do **not** use `localhost` or hardcoded IP addresses. Docker's embedded DNS server (`127.0.0.11`) routes `postgres` to the Postgres container's internal IP.

2. **Port Forwarding (`ports:`):**
   - Syntax: `"HOST_PORT:CONTAINER_PORT"`
   - `4000:4000` allows your local machine (browser or curl) to access port 4000 on the host, which is routed into the container.
   - Services talking to each other internally use their internal port on the bridge network regardless of what is exposed to the host.

3. **Data Persistence (`volumes:`):**
   - Containers are ephemeral: if a container is removed, all changes inside its filesystem are lost.
   - To persist database records, we mount a named volume:
     ```yaml
     volumes:
       - pgdata:/var/lib/postgresql/data
     ```
   - Even if the `postgres` container is destroyed and recreated, the database records on `pgdata` remain intact.

4. **Startup Ordering (`depends_on:`):**
   - `depends_on` controls container startup order:
     ```yaml
     depends_on:
       postgres:
         condition: service_healthy
     ```
   - By combining `condition: service_healthy` with a PostgreSQL health check (`pg_isready -U devops`), backend services only start *after* PostgreSQL is fully accepting connections.

---

## 4. Hands-On Container Operations (Cheat Sheet)

Run these commands from the project root while the platform is running:

### 4.1 Viewing Running Containers
```bash
# List running containers
docker compose ps

# Show CPU and memory consumption in real time
docker stats --no-stream
```

### 4.2 Inspecting Logs
```bash
# Follow logs for all services in real time
docker compose logs -f

# Follow logs for a specific service
docker compose logs -f api

# View the last 50 lines of worker logs
docker compose logs --tail=50 worker
```

### 4.3 Opening an Interactive Shell Inside a Container
```bash
# Open a shell in the running API container
docker compose exec api sh

# Check process list inside the container
ps aux

# Check environment variables inside the container
env | grep DB_

# Exit the container
exit
```

### 4.4 Directly Querying PostgreSQL inside the Container
```bash
docker compose exec postgres psql -U devops -d devops_dashboard
```
Inside the `psql` prompt:
```sql
\dt              -- View all 8 tables
SELECT email, username, display_name FROM users;
SELECT name, slug, repo_url FROM projects;
\q               -- Exit psql
```

### 4.5 Inspecting Docker Networks
```bash
# Find the network name
docker network ls | grep devops

# Inspect network details (IP addresses of all connected containers)
docker network inspect devops-dashboard_dashboard-network
```

---

## 5. Beginner Step-by-Step Lab Exercise

### Objective:
Start the entire stack, test health endpoints, inspect logs, and observe data persistence.

1. **Configure Environment:**
   ```bash
   cp .env.example .env
   ```

2. **Start the Stack:**
   ```bash
   ./scripts/dev.sh
   # Or: docker compose up -d --build
   ```

3. **Verify Health Endpoints:**
   ```bash
   curl -s http://localhost:4000/health
   curl -s http://localhost:4001/health
   curl -s http://localhost:4002/health
   curl -s http://localhost:4003/health
   curl -s http://localhost:4004/health
   ```

4. **Verify Database Seed Data:**
   ```bash
   ./scripts/dev.sh --status
   ```

5. **Stop and Restart Without Losing Data:**
   ```bash
   # Stop containers
   docker compose down

   # Start containers back up
   docker compose up -d

   # Verify database still has your records:
   docker compose exec postgres psql -U devops -d devops_dashboard -c "SELECT count(*) FROM users;"
   ```

6. **Clean Up (Full Reset):**
   ```bash
   # Stops containers AND removes the persistent volumes
   docker compose down -v
   ```

---

## ⏭️ Next Step

Now that you understand containers, multi-stage builds, and Docker Compose networking, proceed to:
👉 **[Level 2: Kubernetes & Helm Orchestration](02-intermediate-kubernetes-and-helm.md)**
