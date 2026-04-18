-- =============================================================================
-- Migration 002: Additional Performance Indexes
-- =============================================================================
-- Composite and covering indexes for common query patterns.
-- =============================================================================

BEGIN;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM schema_migrations WHERE version = 2) THEN
    RAISE NOTICE 'Migration 002 already applied — skipping';
    RETURN;
  END IF;

  -- Deployments: project + status (dashboard filtering)
  CREATE INDEX IF NOT EXISTS idx_deployments_project_status
    ON deployments (project_id, status);

  -- Deployments: environment + created_at (timeline view per env)
  CREATE INDEX IF NOT EXISTS idx_deployments_env_created
    ON deployments (environment_id, created_at DESC);

  -- Alerts: project + status (project alert dashboard)
  CREATE INDEX IF NOT EXISTS idx_alerts_project_status
    ON alerts (project_id, status) WHERE project_id IS NOT NULL;

  -- Audit logs: entity lookup with timestamp ordering
  CREATE INDEX IF NOT EXISTS idx_audit_entity_created
    ON audit_logs (entity_type, entity_id, created_at DESC);

  -- Audit logs: actor activity timeline
  CREATE INDEX IF NOT EXISTS idx_audit_actor_created
    ON audit_logs (actor_id, created_at DESC) WHERE actor_id IS NOT NULL;

  -- Users: login lookup (email + status for auth service)
  CREATE INDEX IF NOT EXISTS idx_users_email_active
    ON users (email) WHERE status = 'active';

  INSERT INTO schema_migrations (version, name) VALUES (2, '002_indexes');
END;
$$;

COMMIT;
