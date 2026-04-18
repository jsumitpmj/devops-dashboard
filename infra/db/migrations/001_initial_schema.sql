-- =============================================================================
-- Migration 001: Initial Schema
-- =============================================================================
-- Applies the full DevOps Dashboard schema.
-- Idempotent: uses IF NOT EXISTS where supported.
-- =============================================================================

BEGIN;

-- Track applied migrations
CREATE TABLE IF NOT EXISTS schema_migrations (
  version     INTEGER PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  applied_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Guard: skip if already applied
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM schema_migrations WHERE version = 1) THEN
    RAISE NOTICE 'Migration 001 already applied — skipping';
    RETURN;
  END IF;

  -- ===========================================================================
  -- Extensions
  -- ===========================================================================
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  CREATE EXTENSION IF NOT EXISTS "pgcrypto";

  -- ===========================================================================
  -- ENUM Types
  -- ===========================================================================
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_status') THEN
    CREATE TYPE user_status AS ENUM ('active', 'inactive', 'suspended');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'deployment_status') THEN
    CREATE TYPE deployment_status AS ENUM ('pending', 'in_progress', 'succeeded', 'failed', 'rolled_back', 'cancelled');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'environment_type') THEN
    CREATE TYPE environment_type AS ENUM ('development', 'staging', 'production', 'testing');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'alert_severity') THEN
    CREATE TYPE alert_severity AS ENUM ('info', 'warning', 'error', 'critical');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'alert_status') THEN
    CREATE TYPE alert_status AS ENUM ('open', 'acknowledged', 'resolved', 'silenced');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'audit_action') THEN
    CREATE TYPE audit_action AS ENUM (
      'user.created', 'user.updated', 'user.deleted', 'user.login', 'user.logout',
      'project.created', 'project.updated', 'project.deleted',
      'environment.created', 'environment.updated', 'environment.deleted',
      'deployment.created', 'deployment.started', 'deployment.succeeded',
      'deployment.failed', 'deployment.rolled_back', 'deployment.cancelled',
      'alert.created', 'alert.acknowledged', 'alert.resolved',
      'role.assigned', 'role.revoked'
    );
  END IF;

  -- ===========================================================================
  -- Tables
  -- ===========================================================================

  CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(64)  NOT NULL UNIQUE,
    description TEXT,
    permissions JSONB        NOT NULL DEFAULT '[]',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );

  CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    username       VARCHAR(128) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255),
    avatar_url     TEXT,
    status         user_status  NOT NULL DEFAULT 'active',
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );

  CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_by UUID          REFERENCES users(id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
  );

  CREATE TABLE projects (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         VARCHAR(255)  NOT NULL,
    slug         VARCHAR(128)  NOT NULL UNIQUE,
    description  TEXT,
    repo_url     TEXT,
    owner_id     UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    metadata     JSONB         NOT NULL DEFAULT '{}',
    is_archived  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
  );

  CREATE TABLE environments (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id   UUID              NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name         VARCHAR(128)      NOT NULL,
    env_type     environment_type  NOT NULL DEFAULT 'development',
    url          TEXT,
    config       JSONB             NOT NULL DEFAULT '{}',
    is_locked    BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, name)
  );

  CREATE TABLE deployments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID              NOT NULL REFERENCES projects(id)      ON DELETE CASCADE,
    environment_id  UUID              NOT NULL REFERENCES environments(id)  ON DELETE CASCADE,
    triggered_by    UUID              NOT NULL REFERENCES users(id)         ON DELETE RESTRICT,
    status          deployment_status NOT NULL DEFAULT 'pending',
    git_ref         VARCHAR(255),
    git_sha         VARCHAR(64),
    image_tag       VARCHAR(255),
    config          JSONB             NOT NULL DEFAULT '{}',
    duration_ms     INTEGER,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW()
  );

  CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(512)   NOT NULL,
    message         TEXT,
    severity        alert_severity NOT NULL DEFAULT 'info',
    status          alert_status   NOT NULL DEFAULT 'open',
    source          VARCHAR(128),
    deployment_id   UUID           REFERENCES deployments(id)   ON DELETE SET NULL,
    environment_id  UUID           REFERENCES environments(id)  ON DELETE SET NULL,
    project_id      UUID           REFERENCES projects(id)      ON DELETE SET NULL,
    acknowledged_by UUID           REFERENCES users(id)         ON DELETE SET NULL,
    resolved_by     UUID           REFERENCES users(id)         ON DELETE SET NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    metadata        JSONB          NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
  );

  CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id     UUID           REFERENCES users(id) ON DELETE SET NULL,
    action       audit_action   NOT NULL,
    entity_type  VARCHAR(64)    NOT NULL,
    entity_id    UUID,
    diff         JSONB          NOT NULL DEFAULT '{}',
    ip_address   INET,
    user_agent   TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
  );

  -- ===========================================================================
  -- Indexes
  -- ===========================================================================

  CREATE INDEX idx_users_email            ON users (email);
  CREATE INDEX idx_users_status           ON users (status);
  CREATE INDEX idx_user_roles_role        ON user_roles (role_id);
  CREATE INDEX idx_projects_owner         ON projects (owner_id);
  CREATE INDEX idx_projects_archived      ON projects (is_archived) WHERE is_archived = FALSE;
  CREATE INDEX idx_environments_project   ON environments (project_id);
  CREATE INDEX idx_environments_type      ON environments (env_type);
  CREATE INDEX idx_deployments_project    ON deployments (project_id);
  CREATE INDEX idx_deployments_env        ON deployments (environment_id);
  CREATE INDEX idx_deployments_triggered  ON deployments (triggered_by);
  CREATE INDEX idx_deployments_status     ON deployments (status);
  CREATE INDEX idx_deployments_created    ON deployments (created_at DESC);
  CREATE INDEX idx_alerts_severity        ON alerts (severity);
  CREATE INDEX idx_alerts_status          ON alerts (status);
  CREATE INDEX idx_alerts_deployment      ON alerts (deployment_id)  WHERE deployment_id IS NOT NULL;
  CREATE INDEX idx_alerts_environment     ON alerts (environment_id) WHERE environment_id IS NOT NULL;
  CREATE INDEX idx_alerts_project         ON alerts (project_id)     WHERE project_id IS NOT NULL;
  CREATE INDEX idx_alerts_created         ON alerts (created_at DESC);
  CREATE INDEX idx_audit_actor            ON audit_logs (actor_id)   WHERE actor_id IS NOT NULL;
  CREATE INDEX idx_audit_action           ON audit_logs (action);
  CREATE INDEX idx_audit_entity           ON audit_logs (entity_type, entity_id);
  CREATE INDEX idx_audit_created          ON audit_logs (created_at DESC);

  -- ===========================================================================
  -- updated_at trigger function
  -- ===========================================================================

  CREATE OR REPLACE FUNCTION update_updated_at_column()
  RETURNS TRIGGER AS $fn$
  BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
  END;
  $fn$ LANGUAGE plpgsql;

  CREATE TRIGGER trg_users_updated_at        BEFORE UPDATE ON users        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
  CREATE TRIGGER trg_roles_updated_at        BEFORE UPDATE ON roles        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
  CREATE TRIGGER trg_projects_updated_at     BEFORE UPDATE ON projects     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
  CREATE TRIGGER trg_environments_updated_at BEFORE UPDATE ON environments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
  CREATE TRIGGER trg_deployments_updated_at  BEFORE UPDATE ON deployments  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
  CREATE TRIGGER trg_alerts_updated_at       BEFORE UPDATE ON alerts       FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

  -- ===========================================================================
  -- Record migration
  -- ===========================================================================
  INSERT INTO schema_migrations (version, name) VALUES (1, '001_initial_schema');

END;
$$;

COMMIT;
