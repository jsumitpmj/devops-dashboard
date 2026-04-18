-- =============================================================================
-- DevOps Dashboard Platform — PostgreSQL Schema
-- =============================================================================
-- This file creates the full database schema from scratch.
-- For incremental changes, use the migrations/ directory.
-- =============================================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- ENUM TYPES
-- =============================================================================

CREATE TYPE user_status       AS ENUM ('active', 'inactive', 'suspended');
CREATE TYPE deployment_status AS ENUM ('pending', 'in_progress', 'succeeded', 'failed', 'rolled_back', 'cancelled');
CREATE TYPE environment_type  AS ENUM ('development', 'staging', 'production', 'testing');
CREATE TYPE alert_severity    AS ENUM ('info', 'warning', 'error', 'critical');
CREATE TYPE alert_status      AS ENUM ('open', 'triggered', 'acknowledged', 'resolved', 'silenced');

-- =============================================================================
-- 1. ROLES
-- =============================================================================
-- Roles are a fixed lookup table (admin, developer, viewer, deployer, etc.).
-- Users are linked to roles via a join table to support multiple roles per user.

CREATE TABLE roles (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name        VARCHAR(64)  NOT NULL UNIQUE,
  description TEXT,
  permissions JSONB        NOT NULL DEFAULT '[]',   -- e.g. ["deploy:create","project:read"]
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  roles IS 'Authorization roles with JSONB permission sets';
COMMENT ON COLUMN roles.permissions IS 'Array of permission strings, e.g. ["deploy:create","project:read"]';

-- =============================================================================
-- 2. USERS
-- =============================================================================

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

COMMENT ON TABLE users IS 'Platform users with credentials and profile data';

-- =============================================================================
-- 3. USER_ROLES  (join table — many-to-many)
-- =============================================================================

CREATE TABLE user_roles (
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  granted_by UUID          REFERENCES users(id) ON DELETE SET NULL,
  granted_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE user_roles IS 'Maps users to their assigned roles';

-- =============================================================================
-- 4. PROJECTS
-- =============================================================================

CREATE TABLE projects (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name         VARCHAR(255)  NOT NULL,
  slug         VARCHAR(128)  NOT NULL UNIQUE,    -- URL-safe identifier
  description  TEXT,
  repo_url     TEXT,                              -- Git repository URL
  owner_id     UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  metadata     JSONB         NOT NULL DEFAULT '{}',
  is_archived  BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  projects IS 'Top-level project entities owned by users';
COMMENT ON COLUMN projects.slug IS 'URL-safe unique identifier for the project';
COMMENT ON COLUMN projects.is_archived IS 'Soft archive flag — archived projects are hidden from default views';

-- =============================================================================
-- 5. ENVIRONMENTS
-- =============================================================================

CREATE TABLE environments (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  project_id   UUID              NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name         VARCHAR(128)      NOT NULL,        -- e.g. "production-us-east-1"
  env_type     environment_type  NOT NULL DEFAULT 'development',
  url          TEXT,                               -- deployed URL
  config       JSONB             NOT NULL DEFAULT '{}',
  is_locked    BOOLEAN           NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),

  UNIQUE (project_id, name)                       -- one "production" per project
);

COMMENT ON TABLE  environments IS 'Deployment targets within a project (dev, staging, prod)';
COMMENT ON COLUMN environments.is_locked IS 'When true, deployments are blocked (maintenance / freeze)';

-- =============================================================================
-- 6. DEPLOYMENTS
-- =============================================================================

CREATE TABLE deployments (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  project_id      UUID              NOT NULL REFERENCES projects(id)      ON DELETE CASCADE,
  environment_id  UUID              NOT NULL REFERENCES environments(id)  ON DELETE CASCADE,
  triggered_by    UUID              NOT NULL REFERENCES users(id)         ON DELETE RESTRICT,
  status          deployment_status NOT NULL DEFAULT 'pending',
  git_ref         VARCHAR(255),                 -- branch, tag, or commit SHA
  git_sha         VARCHAR(64),                  -- full commit SHA
  image_tag       VARCHAR(255),                 -- Docker image tag
  config          JSONB             NOT NULL DEFAULT '{}',
  duration_ms     INTEGER,                      -- elapsed time in milliseconds
  error_message   TEXT,
  started_at      TIMESTAMPTZ,
  finished_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  deployments IS 'Individual deployment runs to an environment';
COMMENT ON COLUMN deployments.duration_ms IS 'Total deployment wall-clock time in milliseconds';

-- =============================================================================
-- 7. ALERTS
-- =============================================================================

CREATE TABLE alerts (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title           VARCHAR(512)   NOT NULL,
  message         TEXT,
  severity        alert_severity NOT NULL DEFAULT 'info',
  status          alert_status   NOT NULL DEFAULT 'open',
  source          VARCHAR(128),                  -- e.g. "health-check", "deployment-pipeline"
  -- Polymorphic link: an alert can relate to a deployment, an environment, or both
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

COMMENT ON TABLE  alerts IS 'Operational alerts tied to deployments or environments';
COMMENT ON COLUMN alerts.source IS 'Originating subsystem that raised the alert';

-- =============================================================================
-- 8. AUDIT LOGS
-- =============================================================================
-- Append-only ledger. No UPDATE/DELETE in normal operation.

CREATE TABLE audit_logs (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  actor_id     UUID           REFERENCES users(id) ON DELETE SET NULL,
  action       VARCHAR(128)   NOT NULL,
  entity_type  VARCHAR(64)    NOT NULL,           -- e.g. "deployment", "user"
  entity_id    UUID,                              -- PK of the affected row
  diff         JSONB          NOT NULL DEFAULT '{}', -- before/after snapshot
  ip_address   INET,
  user_agent   TEXT,
  created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  audit_logs IS 'Immutable audit trail for all significant platform actions';
COMMENT ON COLUMN audit_logs.diff IS 'JSON object with "before" and "after" snapshots of changed fields';

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Users
CREATE INDEX idx_users_email        ON users (email);
CREATE INDEX idx_users_status       ON users (status);

-- User Roles
CREATE INDEX idx_user_roles_role    ON user_roles (role_id);

-- Projects
CREATE INDEX idx_projects_owner     ON projects (owner_id);
CREATE INDEX idx_projects_archived  ON projects (is_archived) WHERE is_archived = FALSE;

-- Environments
CREATE INDEX idx_environments_project   ON environments (project_id);
CREATE INDEX idx_environments_type      ON environments (env_type);

-- Deployments
CREATE INDEX idx_deployments_project     ON deployments (project_id);
CREATE INDEX idx_deployments_env         ON deployments (environment_id);
CREATE INDEX idx_deployments_triggered   ON deployments (triggered_by);
CREATE INDEX idx_deployments_status      ON deployments (status);
CREATE INDEX idx_deployments_created     ON deployments (created_at DESC);

-- Alerts
CREATE INDEX idx_alerts_severity     ON alerts (severity);
CREATE INDEX idx_alerts_status       ON alerts (status);
CREATE INDEX idx_alerts_deployment   ON alerts (deployment_id)  WHERE deployment_id IS NOT NULL;
CREATE INDEX idx_alerts_environment  ON alerts (environment_id) WHERE environment_id IS NOT NULL;
CREATE INDEX idx_alerts_project      ON alerts (project_id)     WHERE project_id IS NOT NULL;
CREATE INDEX idx_alerts_created      ON alerts (created_at DESC);

-- Audit Logs
CREATE INDEX idx_audit_actor         ON audit_logs (actor_id)    WHERE actor_id IS NOT NULL;
CREATE INDEX idx_audit_action        ON audit_logs (action);
CREATE INDEX idx_audit_entity        ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_created       ON audit_logs (created_at DESC);

-- =============================================================================
-- UPDATED_AT TRIGGER
-- =============================================================================
-- Automatically refreshes updated_at on row modification.

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at        BEFORE UPDATE ON users        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_roles_updated_at        BEFORE UPDATE ON roles        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_projects_updated_at     BEFORE UPDATE ON projects     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_environments_updated_at BEFORE UPDATE ON environments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_deployments_updated_at  BEFORE UPDATE ON deployments  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_alerts_updated_at       BEFORE UPDATE ON alerts       FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
