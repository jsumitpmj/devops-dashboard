# =============================================================================
# DevOps Dashboard — Local Dev Startup (PowerShell / Windows)
# =============================================================================
# Usage:
#   .\scripts\dev.ps1                # Start all services (auto-init DB)
#   .\scripts\dev.ps1 -DbOnly       # Start PostgreSQL only
#   .\scripts\dev.ps1 -Rebuild      # Rebuild and start all containers
#   .\scripts\dev.ps1 -Build        # Maven build first, then docker compose up
#   .\scripts\dev.ps1 -Down         # Stop and remove all containers
#   .\scripts\dev.ps1 -Clean        # Stop, remove containers AND volumes
#   .\scripts\dev.ps1 -Logs         # Tail logs for all services
#   .\scripts\dev.ps1 -Status       # Show container + health + DB status
#   .\scripts\dev.ps1 -InitDb       # Force re-initialize DB (schema + seed)
#   .\scripts\dev.ps1 -Seed         # Re-run seed data only
#   .\scripts\dev.ps1 -ResetDb      # Drop all tables, re-run schema + seed

param(
    [switch]$DbOnly,
    [switch]$Rebuild,
    [switch]$Build,
    [switch]$Down,
    [switch]$Clean,
    [switch]$Logs,
    [switch]$Status,
    [switch]$InitDb,
    [switch]$Seed,
    [switch]$ResetDb
)

$ErrorActionPreference = "Stop"

# ---- Configuration ----
$DB_CONTAINER = "devops-postgres"
$DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "devops" }
$DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "devops" }
$DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { "devops_dashboard" }
$INIT_SQL = Join-Path $PSScriptRoot "..\infra\db\init.sql"
$SEED_SQL = Join-Path $PSScriptRoot "..\scripts\seed.sql"

# =============================================================================
# Helpers
# =============================================================================

function Wait-ForPostgres {
    Write-Host "Waiting for PostgreSQL..." -ForegroundColor Yellow
    $retries = 0
    while ($retries -lt 60) {
        $health = docker inspect --format='{{.State.Health.Status}}' $DB_CONTAINER 2>$null
        if ($health -eq "healthy") {
            Write-Host "PostgreSQL is healthy!" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 1
        $retries++
    }
    Write-Host "PostgreSQL did not become healthy within 60s." -ForegroundColor Red
    return $false
}

function Test-DbInitialized {
    $count = docker exec $DB_CONTAINER `
        psql -U $DB_USER -d $DB_NAME -t -c `
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>$null
    $count = ($count -replace '\s','')
    return ([int]$count -gt 0)
}

function Test-DbSeeded {
    $count = docker exec $DB_CONTAINER `
        psql -U $DB_USER -d $DB_NAME -t -c `
        "SELECT COUNT(*) FROM users;" 2>$null
    $count = ($count -replace '\s','')
    return ([int]$count -gt 0)
}

function Invoke-SqlFile {
    param([string]$SqlFile, [string]$Label)

    if (-not (Test-Path $SqlFile)) {
        Write-Host "SQL file not found: $SqlFile" -ForegroundColor Red
        return
    }

    Write-Host "Running ${Label}..." -ForegroundColor Yellow
    Get-Content $SqlFile -Raw | docker exec -i $DB_CONTAINER `
        psql -U $DB_USER -d $DB_NAME 2>&1 | ForEach-Object {
        if ($_ -match '^(ERROR|FATAL)') {
            Write-Host "  $_" -ForegroundColor Red
        } elseif ($_ -match '^(CREATE|INSERT|ALTER|DROP)') {
            Write-Host "  $_" -ForegroundColor Green
        }
    }
    Write-Host "${Label} complete." -ForegroundColor Green
}

function Initialize-Database {
    Write-Host "--- Database Initialization ---" -ForegroundColor Cyan

    if (Test-DbInitialized) {
        Write-Host "Schema already exists (tables found)." -ForegroundColor Yellow
    } else {
        Invoke-SqlFile -SqlFile $INIT_SQL -Label "Schema (init.sql)"
    }

    if (Test-DbSeeded) {
        Write-Host "Seed data already loaded (users found)." -ForegroundColor Yellow
    } else {
        Invoke-SqlFile -SqlFile $SEED_SQL -Label "Seed data (seed.sql)"
    }

    Write-Host "Database ready." -ForegroundColor Green
    Write-Host ""
}

function Reset-Database {
    Write-Host "--- Database Reset ---" -ForegroundColor Cyan
    Write-Host "Dropping all tables..." -ForegroundColor Yellow

    $dropSql = @"
DO `$`$
DECLARE r RECORD;
BEGIN
  FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
    EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
  END LOOP;
  FOR r IN (SELECT typname FROM pg_type WHERE typnamespace = 'public'::regnamespace AND typtype = 'e') LOOP
    EXECUTE 'DROP TYPE IF EXISTS public.' || quote_ident(r.typname) || ' CASCADE';
  END LOOP;
  FOR r IN (SELECT proname FROM pg_proc WHERE pronamespace = 'public'::regnamespace) LOOP
    EXECUTE 'DROP FUNCTION IF EXISTS public.' || quote_ident(r.proname) || ' CASCADE';
  END LOOP;
END `$`$;
"@

    $dropSql | docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME 2>$null
    Write-Host "All tables dropped." -ForegroundColor Green

    Invoke-SqlFile -SqlFile $INIT_SQL -Label "Schema (init.sql)"
    Invoke-SqlFile -SqlFile $SEED_SQL -Label "Seed data (seed.sql)"

    Write-Host "Database fully reset and seeded." -ForegroundColor Green
    Write-Host ""
}

function Ensure-Postgres {
    $running = docker inspect --format='{{.State.Running}}' $DB_CONTAINER 2>$null
    if ($running -ne "true") {
        Write-Host "PostgreSQL not running. Starting..." -ForegroundColor Yellow
        docker compose up -d postgres
    }
    Wait-ForPostgres | Out-Null
}

# =============================================================================
# Main
# =============================================================================

Write-Host "=== DevOps Dashboard Platform ===" -ForegroundColor Cyan

# ---- Quick commands ----

if ($Down) {
    Write-Host "Stopping all containers..." -ForegroundColor Yellow
    docker compose down
    Write-Host "All services stopped." -ForegroundColor Green
    exit 0
}

if ($Clean) {
    Write-Host "Stopping all containers and removing volumes..." -ForegroundColor Yellow
    docker compose down -v
    Write-Host "Clean shutdown complete. Next startup will reinitialize the database." -ForegroundColor Green
    exit 0
}

if ($Logs) {
    docker compose logs -f
    exit 0
}

if ($Status) {
    Write-Host "=== Container Status ===" -ForegroundColor Cyan
    docker compose ps
    Write-Host ""
    Write-Host "=== Health Checks ===" -ForegroundColor Cyan
    foreach ($port in @(4000, 4001, 4002, 4003, 4004)) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:$port/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp.StatusCode -eq 200) {
                Write-Host "  :$port  ✓ healthy" -ForegroundColor Green
            }
        } catch {
            Write-Host "  :$port  ✗ unreachable" -ForegroundColor Red
        }
    }
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:3000/" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq 200) {
            Write-Host "  :3000  ✓ frontend up" -ForegroundColor Green
        }
    } catch {
        Write-Host "  :3000  ✗ frontend unreachable" -ForegroundColor Red
    }
    Write-Host ""

    # DB status
    Write-Host "=== Database ===" -ForegroundColor Cyan
    try {
        if (Test-DbInitialized) {
            $tables = (docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>$null).Trim()
            $users = (docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM users;" 2>$null).Trim()
            $deploys = (docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM deployments;" 2>$null).Trim()
            $alerts = (docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM alerts;" 2>$null).Trim()
            Write-Host "  Tables:      $tables" -ForegroundColor Green
            Write-Host "  Users:       $users" -ForegroundColor Green
            Write-Host "  Deployments: $deploys" -ForegroundColor Green
            Write-Host "  Alerts:      $alerts" -ForegroundColor Green
        } else {
            Write-Host "  Database not initialized" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  PostgreSQL container not found" -ForegroundColor Red
    }
    exit 0
}

# ---- Database-only commands ----

if ($InitDb) {
    Ensure-Postgres
    Initialize-Database
    exit 0
}

if ($Seed) {
    Ensure-Postgres
    Write-Host "--- Re-seeding Database ---" -ForegroundColor Cyan
    Invoke-SqlFile -SqlFile $SEED_SQL -Label "Seed data (seed.sql)"
    exit 0
}

if ($ResetDb) {
    Ensure-Postgres
    Reset-Database
    exit 0
}

# ---- Build Java services if requested ----

if ($Build) {
    Write-Host "Building Java services with Maven..." -ForegroundColor Yellow
    Push-Location "$PSScriptRoot\..\services"
    mvn clean install -DskipTests -B
    Pop-Location
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Maven build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Build complete." -ForegroundColor Green
}

# ---- Docker operations ----

if ($Rebuild) {
    Write-Host "Rebuilding containers..." -ForegroundColor Yellow
    docker compose up -d --build
} elseif ($DbOnly) {
    Write-Host "Starting PostgreSQL 18 only..." -ForegroundColor Yellow
    docker compose up -d postgres
} else {
    Write-Host "Starting all services..." -ForegroundColor Yellow
    docker compose up -d
}

# ---- Wait for PostgreSQL ----
$pgOk = Wait-ForPostgres
if (-not $pgOk) { exit 1 }

# ---- Auto-initialize DB on first start ----
Write-Host "--- Checking database state ---" -ForegroundColor Cyan
Start-Sleep -Seconds 2

if (-not (Test-DbInitialized)) {
    Write-Host "Empty database detected — running initial setup..." -ForegroundColor Yellow
    Invoke-SqlFile -SqlFile $INIT_SQL -Label "Schema (init.sql)"
    Invoke-SqlFile -SqlFile $SEED_SQL -Label "Seed data (seed.sql)"
    Write-Host ""
    Write-Host "✓ Database initialized with schema and seed data." -ForegroundColor Green
} elseif (-not (Test-DbSeeded)) {
    Write-Host "Schema exists but no seed data — loading..." -ForegroundColor Yellow
    Invoke-SqlFile -SqlFile $SEED_SQL -Label "Seed data (seed.sql)"
    Write-Host ""
    Write-Host "✓ Seed data loaded." -ForegroundColor Green
} else {
    Write-Host "✓ Database already initialized and seeded." -ForegroundColor Green
}

# ---- Print status ----
Write-Host ""
if ($DbOnly) {
    Write-Host "PostgreSQL running on localhost:5432" -ForegroundColor Green
    Write-Host "  User: $DB_USER / Password: $DB_PASSWORD / DB: $DB_NAME"
    Write-Host ""
    Write-Host "Seed users:" -ForegroundColor Cyan
    Write-Host "  admin@devops.local / password123  (admin)"
    Write-Host "  alice@devops.local / password123  (developer)"
    Write-Host "  bob@devops.local   / password123  (developer, deployer)"
    Write-Host "  carol@devops.local / password123  (viewer)"
    Write-Host ""
    Write-Host "To start services locally:" -ForegroundColor Cyan
    Write-Host "  cd services"
    Write-Host "  mvn clean install -DskipTests"
    Write-Host "  mvn spring-boot:run -pl api"
} else {
    Write-Host "=== Services ===" -ForegroundColor Cyan
    Write-Host "  Frontend:     http://localhost:3000"
    Write-Host "  API:          http://localhost:4000/health"
    Write-Host "  Auth:         http://localhost:4001/health"
    Write-Host "  Deployment:   http://localhost:4002/health"
    Write-Host "  Notification: http://localhost:4003/health"
    Write-Host "  Worker:       http://localhost:4004/health"
    Write-Host ""
    Write-Host "Seed users:" -ForegroundColor Cyan
    Write-Host "  admin@devops.local / password123  (admin)"
    Write-Host "  alice@devops.local / password123  (developer)"
    Write-Host "  bob@devops.local   / password123  (developer, deployer)"
    Write-Host "  carol@devops.local / password123  (viewer)"
}

Write-Host ""
Write-Host "=== Done ===" -ForegroundColor Green
