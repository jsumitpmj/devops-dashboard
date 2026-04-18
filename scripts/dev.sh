#!/usr/bin/env bash
# =============================================================================
# DevOps Dashboard — Local Dev Startup (Bash / Linux / macOS)
# =============================================================================
# Usage:
#   ./scripts/dev.sh              # Start all services (auto-init DB on first run)
#   ./scripts/dev.sh --db-only    # Start PostgreSQL only
#   ./scripts/dev.sh --rebuild    # Rebuild and start all containers
#   ./scripts/dev.sh --build      # Maven build first, then docker compose up
#   ./scripts/dev.sh --down       # Stop and remove all containers
#   ./scripts/dev.sh --clean      # Stop, remove containers AND volumes (fresh DB)
#   ./scripts/dev.sh --logs       # Tail logs for all services
#   ./scripts/dev.sh --status     # Show status of all services
#   ./scripts/dev.sh --init-db    # Force re-initialize DB (schema + seed)
#   ./scripts/dev.sh --seed       # Re-run seed data only
#   ./scripts/dev.sh --reset-db   # Drop all tables, re-run schema + seed

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# ---- Configuration ----
DB_CONTAINER="devops-postgres"
DB_USER="${DB_USER:-devops}"
DB_PASSWORD="${DB_PASSWORD:-devops}"
DB_NAME="${DB_NAME:-devops_dashboard}"
INIT_SQL="infra/db/init.sql"
SEED_SQL="scripts/seed.sql"

# ---- Parse arguments ----
ACTION="up"
for arg in "$@"; do
  case "$arg" in
    --db-only)   ACTION="db-only" ;;
    --rebuild)   ACTION="rebuild" ;;
    --build)     ACTION="build" ;;
    --down)      ACTION="down" ;;
    --clean)     ACTION="clean" ;;
    --logs)      ACTION="logs" ;;
    --status)    ACTION="status" ;;
    --init-db)   ACTION="init-db" ;;
    --seed)      ACTION="seed" ;;
    --reset-db)  ACTION="reset-db" ;;
    --help|-h)
      echo "DevOps Dashboard — Dev Script"
      echo ""
      echo "Usage: $0 [OPTION]"
      echo ""
      echo "Startup:"
      echo "  (no args)      Start all services (auto-init DB on first run)"
      echo "  --db-only      Start PostgreSQL only"
      echo "  --rebuild      Rebuild and start all containers"
      echo "  --build        Maven build, then docker compose up"
      echo ""
      echo "Shutdown:"
      echo "  --down          Stop all containers"
      echo "  --clean         Stop + remove volumes (full reset)"
      echo ""
      echo "Database:"
      echo "  --init-db       Run schema + seed data (idempotent)"
      echo "  --seed          Re-run seed data only"
      echo "  --reset-db      Drop all tables, re-run schema + seed"
      echo ""
      echo "Monitoring:"
      echo "  --logs          Tail logs for all services"
      echo "  --status        Show container + health status"
      exit 0
      ;;
    *)
      echo -e "${RED}Unknown argument: $arg${NC}"
      echo "Run '$0 --help' for usage."
      exit 1
      ;;
  esac
done

echo -e "${CYAN}${BOLD}=== DevOps Dashboard Platform ===${NC}"

# =============================================================================
# Helper: wait for PostgreSQL to become healthy
# =============================================================================
wait_for_postgres() {
  echo -e "${YELLOW}Waiting for PostgreSQL...${NC}"
  local retries=0
  while [ $retries -lt 60 ]; do
    local health
    health=$(docker inspect --format='{{.State.Health.Status}}' "$DB_CONTAINER" 2>/dev/null || echo "not_running")
    if [ "$health" = "healthy" ]; then
      echo -e "${GREEN}PostgreSQL is healthy!${NC}"
      return 0
    fi
    sleep 1
    retries=$((retries + 1))
  done
  echo -e "${RED}PostgreSQL did not become healthy within 60s.${NC}"
  return 1
}

# =============================================================================
# Helper: check if database has been initialized (tables exist)
# =============================================================================
db_is_initialized() {
  local table_count
  table_count=$(docker exec "$DB_CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" \
    2>/dev/null | tr -d '[:space:]')
  [ "${table_count:-0}" -gt 0 ]
}

# =============================================================================
# Helper: check if seed data has been loaded (users exist)
# =============================================================================
db_has_seed_data() {
  local user_count
  user_count=$(docker exec "$DB_CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT COUNT(*) FROM users;" \
    2>/dev/null | tr -d '[:space:]')
  [ "${user_count:-0}" -gt 0 ]
}

# =============================================================================
# Helper: run SQL file against the database
# =============================================================================
run_sql_file() {
  local sql_file="$1"
  local label="$2"

  if [ ! -f "$sql_file" ]; then
    echo -e "${RED}SQL file not found: $sql_file${NC}"
    return 1
  fi

  echo -e "${YELLOW}Running ${label}...${NC}"
  docker exec -i "$DB_CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" \
    < "$sql_file" 2>&1 | while IFS= read -r line; do
      # Filter noise, show only errors and key statements
      case "$line" in
        ERROR*|FATAL*) echo -e "  ${RED}$line${NC}" ;;
        CREATE*|INSERT*|ALTER*|DROP*) echo -e "  ${GREEN}$line${NC}" ;;
        NOTICE*) ;; # suppress notices
        *) ;;
      esac
    done
  echo -e "${GREEN}${label} complete.${NC}"
}

# =============================================================================
# Helper: initialize database (schema + seed)
# =============================================================================
init_database() {
  echo -e "${CYAN}--- Database Initialization ---${NC}"

  if db_is_initialized; then
    echo -e "${YELLOW}Schema already exists (tables found).${NC}"
  else
    run_sql_file "$INIT_SQL" "Schema (init.sql)"
  fi

  if db_has_seed_data; then
    echo -e "${YELLOW}Seed data already loaded (users found).${NC}"
  else
    run_sql_file "$SEED_SQL" "Seed data (seed.sql)"
  fi

  echo -e "${GREEN}Database ready.${NC}"
  echo ""
}

# =============================================================================
# Helper: force re-initialize database (drop + recreate)
# =============================================================================
reset_database() {
  echo -e "${CYAN}--- Database Reset ---${NC}"
  echo -e "${YELLOW}Dropping all tables...${NC}"

  docker exec -i "$DB_CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -c "
      DO \$\$
      DECLARE
        r RECORD;
      BEGIN
        -- Drop all tables
        FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
          EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
        END LOOP;
        -- Drop all custom types
        FOR r IN (SELECT typname FROM pg_type WHERE typnamespace = 'public'::regnamespace AND typtype = 'e') LOOP
          EXECUTE 'DROP TYPE IF EXISTS public.' || quote_ident(r.typname) || ' CASCADE';
        END LOOP;
        -- Drop functions
        FOR r IN (SELECT proname FROM pg_proc WHERE pronamespace = 'public'::regnamespace) LOOP
          EXECUTE 'DROP FUNCTION IF EXISTS public.' || quote_ident(r.proname) || ' CASCADE';
        END LOOP;
      END \$\$;
    " 2>/dev/null

  echo -e "${GREEN}All tables dropped.${NC}"

  run_sql_file "$INIT_SQL" "Schema (init.sql)"
  run_sql_file "$SEED_SQL" "Seed data (seed.sql)"

  echo -e "${GREEN}Database fully reset and seeded.${NC}"
  echo ""
}

# =============================================================================
# Helper: ensure postgres is running, start if not
# =============================================================================
ensure_postgres() {
  local running
  running=$(docker inspect --format='{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || echo "false")
  if [ "$running" != "true" ]; then
    echo -e "${YELLOW}PostgreSQL not running. Starting...${NC}"
    docker compose up -d postgres
  fi
  wait_for_postgres
}

# =============================================================================
# Actions
# =============================================================================

case "$ACTION" in
  down)
    echo -e "${YELLOW}Stopping all containers...${NC}"
    docker compose down
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
    ;;

  clean)
    echo -e "${YELLOW}Stopping all containers and removing volumes...${NC}"
    docker compose down -v
    echo -e "${GREEN}Clean shutdown complete. Next startup will reinitialize the database.${NC}"
    exit 0
    ;;

  logs)
    docker compose logs -f
    exit 0
    ;;

  status)
    echo -e "${CYAN}=== Container Status ===${NC}"
    docker compose ps
    echo ""
    echo -e "${CYAN}=== Health Checks ===${NC}"
    for port in 4000 4001 4002 4003 4004; do
      status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/health" 2>/dev/null || echo "000")
      if [ "$status" = "200" ]; then
        echo -e "  :$port  ${GREEN}✓ healthy${NC}"
      else
        echo -e "  :$port  ${RED}✗ unreachable ($status)${NC}"
      fi
    done
    fe_status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:3000/" 2>/dev/null || echo "000")
    if [ "$fe_status" = "200" ]; then
      echo -e "  :3000  ${GREEN}✓ frontend up${NC}"
    else
      echo -e "  :3000  ${RED}✗ frontend unreachable ($fe_status)${NC}"
    fi
    echo ""

    # Show DB status
    echo -e "${CYAN}=== Database ===${NC}"
    if docker inspect "$DB_CONTAINER" &>/dev/null; then
      if db_is_initialized; then
        table_count=$(docker exec "$DB_CONTAINER" \
          psql -U "$DB_USER" -d "$DB_NAME" -t -c \
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" \
          2>/dev/null | tr -d '[:space:]')
        user_count=$(docker exec "$DB_CONTAINER" \
          psql -U "$DB_USER" -d "$DB_NAME" -t -c \
          "SELECT COUNT(*) FROM users;" \
          2>/dev/null | tr -d '[:space:]')
        deploy_count=$(docker exec "$DB_CONTAINER" \
          psql -U "$DB_USER" -d "$DB_NAME" -t -c \
          "SELECT COUNT(*) FROM deployments;" \
          2>/dev/null | tr -d '[:space:]')
        alert_count=$(docker exec "$DB_CONTAINER" \
          psql -U "$DB_USER" -d "$DB_NAME" -t -c \
          "SELECT COUNT(*) FROM alerts;" \
          2>/dev/null | tr -d '[:space:]')
        echo -e "  Tables:      ${GREEN}${table_count}${NC}"
        echo -e "  Users:       ${GREEN}${user_count}${NC}"
        echo -e "  Deployments: ${GREEN}${deploy_count}${NC}"
        echo -e "  Alerts:      ${GREEN}${alert_count}${NC}"
      else
        echo -e "  ${YELLOW}Database not initialized${NC}"
      fi
    else
      echo -e "  ${RED}PostgreSQL container not found${NC}"
    fi
    exit 0
    ;;

  # ---- Database-only commands ----

  init-db)
    ensure_postgres
    init_database
    exit 0
    ;;

  seed)
    ensure_postgres
    echo -e "${CYAN}--- Re-seeding Database ---${NC}"
    run_sql_file "$SEED_SQL" "Seed data (seed.sql)"
    exit 0
    ;;

  reset-db)
    ensure_postgres
    reset_database
    exit 0
    ;;

  # ---- Startup commands ----

  build)
    echo -e "${YELLOW}Building Java services with Maven...${NC}"
    cd "$PROJECT_ROOT/services"
    mvn clean install -DskipTests -B
    if [ $? -ne 0 ]; then
      echo -e "${RED}Maven build failed!${NC}"
      exit 1
    fi
    cd "$PROJECT_ROOT"
    echo -e "${GREEN}Build complete.${NC}"
    echo -e "${YELLOW}Starting all services...${NC}"
    docker compose up -d --build
    ;;

  rebuild)
    echo -e "${YELLOW}Rebuilding containers...${NC}"
    docker compose up -d --build
    ;;

  db-only)
    echo -e "${YELLOW}Starting PostgreSQL 18 only...${NC}"
    docker compose up -d postgres
    ;;

  up)
    echo -e "${YELLOW}Starting all services...${NC}"
    docker compose up -d
    ;;
esac

# ---- Wait for PostgreSQL ----
wait_for_postgres

# ---- Auto-initialize DB on first start ----
echo -e "${CYAN}--- Checking database state ---${NC}"
sleep 2  # Give PG a moment to finish any entrypoint scripts

if ! db_is_initialized; then
  echo -e "${YELLOW}Empty database detected — running initial setup...${NC}"
  run_sql_file "$INIT_SQL" "Schema (init.sql)"
  run_sql_file "$SEED_SQL" "Seed data (seed.sql)"
  echo ""
  echo -e "${GREEN}✓ Database initialized with schema and seed data.${NC}"
elif ! db_has_seed_data; then
  echo -e "${YELLOW}Schema exists but no seed data — loading...${NC}"
  run_sql_file "$SEED_SQL" "Seed data (seed.sql)"
  echo ""
  echo -e "${GREEN}✓ Seed data loaded.${NC}"
else
  echo -e "${GREEN}✓ Database already initialized and seeded.${NC}"
fi

# ---- Print status ----
echo ""
if [ "$ACTION" = "db-only" ]; then
  echo -e "${GREEN}PostgreSQL running on localhost:5432${NC}"
  echo "  User: $DB_USER / Password: $DB_PASSWORD / DB: $DB_NAME"
  echo ""
  echo -e "${CYAN}Seed users:${NC}"
  echo "  admin@devops.local / password123  (admin)"
  echo "  alice@devops.local / password123  (developer)"
  echo "  bob@devops.local   / password123  (developer, deployer)"
  echo "  carol@devops.local / password123  (viewer)"
  echo ""
  echo -e "${CYAN}To start services locally:${NC}"
  echo "  cd services"
  echo "  mvn clean install -DskipTests"
  echo "  mvn spring-boot:run -pl api"
else
  echo -e "${CYAN}=== Services ===${NC}"
  echo "  Frontend:     http://localhost:3000"
  echo "  API:          http://localhost:4000/health"
  echo "  Auth:         http://localhost:4001/health"
  echo "  Deployment:   http://localhost:4002/health"
  echo "  Notification: http://localhost:4003/health"
  echo "  Worker:       http://localhost:4004/health"
  echo ""
  echo -e "${CYAN}Seed users:${NC}"
  echo "  admin@devops.local / password123  (admin)"
  echo "  alice@devops.local / password123  (developer)"
  echo "  bob@devops.local   / password123  (developer, deployer)"
  echo "  carol@devops.local / password123  (viewer)"
fi

echo ""
echo -e "${GREEN}${BOLD}=== Done ===${NC}"
