# =============================================================================
# DevOps Dashboard — Makefile (Linux / macOS)
# =============================================================================
# Common developer commands. Run `make help` to see all targets.

.PHONY: help up down rebuild clean logs status build test health db-only init-db seed reset-db db-status db-shell frontend-dev frontend-build

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

# ---- Docker Compose ----

up: ## Start all services
	docker compose up -d

down: ## Stop all services
	docker compose down

rebuild: ## Rebuild and start all services
	docker compose up -d --build

clean: ## Stop, remove containers AND volumes (fresh DB)
	docker compose down -v

logs: ## Tail logs for all services
	docker compose logs -f

db-only: ## Start PostgreSQL only
	docker compose up -d postgres

# ---- Build ----

build: ## Build Java services with Maven
	cd services && mvn clean install -DskipTests -B

test: ## Run all Java tests
	cd services && mvn clean test

# ---- Frontend ----

frontend-dev: ## Run frontend in dev mode (hot reload)
	cd frontend && npm install && npm run dev

frontend-build: ## Build frontend for production
	cd frontend && npm install && npm run build

# ---- Health / Status ----

health: ## Check health of all services
	@for port in 4000 4001 4002 4003 4004; do \
		status=$$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$$port/health 2>/dev/null || echo "000"); \
		if [ "$$status" = "200" ]; then \
			printf "  :%s  \033[32m✓ healthy\033[0m\n" $$port; \
		else \
			printf "  :%s  \033[31m✗ unreachable (%s)\033[0m\n" $$port $$status; \
		fi; \
	done
	@fe=$$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ 2>/dev/null || echo "000"); \
	if [ "$$fe" = "200" ]; then \
		printf "  :3000  \033[32m✓ frontend up\033[0m\n"; \
	else \
		printf "  :3000  \033[31m✗ frontend unreachable (%s)\033[0m\n" $$fe; \
	fi

status: ## Show container status + health checks
	docker compose ps
	@echo ""
	@$(MAKE) --no-print-directory health

# ---- Database ----

init-db: ## Initialize DB (schema + seed, idempotent)
	./scripts/dev.sh --init-db

seed: ## Re-run seed data only
	docker exec -i devops-postgres psql -U devops -d devops_dashboard < scripts/seed.sql

reset-db: ## Drop all tables + re-run schema + seed
	./scripts/dev.sh --reset-db

db-status: ## Show table and row counts
	@docker exec devops-postgres psql -U devops -d devops_dashboard -c \
		"SELECT 'tables' AS item, COUNT(*)::text AS count FROM information_schema.tables WHERE table_schema='public' \
		 UNION ALL SELECT 'users', COUNT(*)::text FROM users \
		 UNION ALL SELECT 'projects', COUNT(*)::text FROM projects \
		 UNION ALL SELECT 'environments', COUNT(*)::text FROM environments \
		 UNION ALL SELECT 'deployments', COUNT(*)::text FROM deployments \
		 UNION ALL SELECT 'alerts', COUNT(*)::text FROM alerts \
		 UNION ALL SELECT 'audit_logs', COUNT(*)::text FROM audit_logs \
		 UNION ALL SELECT 'roles', COUNT(*)::text FROM roles;"

db-shell: ## Open psql shell inside the PostgreSQL container
	docker exec -it devops-postgres psql -U devops -d devops_dashboard
