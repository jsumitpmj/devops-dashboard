-- =============================================================================
-- Seed Data for Local Development
-- =============================================================================
-- Run after init.sql / migrations.
-- Usage:  psql "$DATABASE_URL" -f scripts/seed.sql
-- =============================================================================

-- Roles
INSERT INTO roles (id, name, description, permissions) VALUES
  ('a0000000-0000-0000-0000-000000000001', 'admin',     'Full platform access',          '["*"]'),
  ('a0000000-0000-0000-0000-000000000002', 'developer', 'Deploy and manage projects',    '["project:read","project:write","deploy:create","deploy:read","env:read"]'),
  ('a0000000-0000-0000-0000-000000000003', 'viewer',    'Read-only access',              '["project:read","deploy:read","env:read"]'),
  ('a0000000-0000-0000-0000-000000000004', 'deployer',  'Deployment-focused role',       '["deploy:create","deploy:read","deploy:rollback","env:read","project:read"]')
ON CONFLICT (name) DO NOTHING;

-- Users (passwords are bcrypt hash of "password123")
INSERT INTO users (id, email, username, password_hash, display_name, status) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'admin@devops.local',   'admin',   '$2b$10$oXoZyq3sCzvYOpoQ4rkLd.M0BCLPTRcZJrHLbHZvJ7DLEX6h1S5p6', 'Admin User',    'active'),
  ('b0000000-0000-0000-0000-000000000002', 'alice@devops.local',   'alice',   '$2b$10$oXoZyq3sCzvYOpoQ4rkLd.M0BCLPTRcZJrHLbHZvJ7DLEX6h1S5p6', 'Alice Chen',    'active'),
  ('b0000000-0000-0000-0000-000000000003', 'bob@devops.local',     'bob',     '$2b$10$oXoZyq3sCzvYOpoQ4rkLd.M0BCLPTRcZJrHLbHZvJ7DLEX6h1S5p6', 'Bob Martinez',  'active'),
  ('b0000000-0000-0000-0000-000000000004', 'carol@devops.local',   'carol',   '$2b$10$oXoZyq3sCzvYOpoQ4rkLd.M0BCLPTRcZJrHLbHZvJ7DLEX6h1S5p6', 'Carol Wang',    'active'),
  ('b0000000-0000-0000-0000-000000000005', 'dave@devops.local',    'dave',    '$2b$10$oXoZyq3sCzvYOpoQ4rkLd.M0BCLPTRcZJrHLbHZvJ7DLEX6h1S5p6', 'Dave Johnson',  'inactive')
ON CONFLICT (email) DO NOTHING;

-- User Roles
INSERT INTO user_roles (user_id, role_id) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001'), -- admin → admin
  ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002'), -- alice → developer
  ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000002'), -- bob → developer
  ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000004'), -- bob → deployer
  ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000003'), -- carol → viewer
  ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000003') -- dave → viewer
ON CONFLICT DO NOTHING;

-- Projects
INSERT INTO projects (id, name, slug, description, repo_url, owner_id) VALUES
  ('c0000000-0000-0000-0000-000000000001', 'Platform API',      'platform-api',      'Core platform REST API',               'https://github.com/acme/platform-api',      'b0000000-0000-0000-0000-000000000002'),
  ('c0000000-0000-0000-0000-000000000002', 'Web Dashboard',     'web-dashboard',     'Customer-facing dashboard frontend',   'https://github.com/acme/web-dashboard',     'b0000000-0000-0000-0000-000000000002'),
  ('c0000000-0000-0000-0000-000000000003', 'Payment Service',   'payment-service',   'Stripe integration microservice',      'https://github.com/acme/payment-service',   'b0000000-0000-0000-0000-000000000003'),
  ('c0000000-0000-0000-0000-000000000004', 'Notification Hub',  'notification-hub',  'Email and push notification service',  'https://github.com/acme/notification-hub',  'b0000000-0000-0000-0000-000000000001')
ON CONFLICT (slug) DO NOTHING;

-- Environments
INSERT INTO environments (id, project_id, name, env_type, url) VALUES
  -- Platform API
  ('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'development',  'development',  'https://api-dev.acme.internal'),
  ('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'staging',      'staging',      'https://api-staging.acme.internal'),
  ('d0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'production',   'production',   'https://api.acme.com'),
  -- Web Dashboard
  ('d0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'development',  'development',  'https://app-dev.acme.internal'),
  ('d0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000002', 'staging',      'staging',      'https://app-staging.acme.internal'),
  ('d0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000002', 'production',   'production',   'https://app.acme.com'),
  -- Payment Service
  ('d0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000003', 'staging',      'staging',      'https://pay-staging.acme.internal'),
  ('d0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000003', 'production',   'production',   'https://pay.acme.com'),
  -- Notification Hub
  ('d0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000004', 'development',  'development',  NULL),
  ('d0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000004', 'production',   'production',   'https://notify.acme.com')
ON CONFLICT (project_id, name) DO NOTHING;

-- Deployments
INSERT INTO deployments (id, project_id, environment_id, triggered_by, status, git_ref, git_sha, image_tag, duration_ms, started_at, finished_at) VALUES
  ('e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', 'succeeded',   'main',       'abc1234def5678', 'platform-api:1.2.0',    45200,  NOW() - INTERVAL '2 hours',   NOW() - INTERVAL '1 hour 59 minutes'),
  ('e0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'succeeded',   'release/1.2','abc1234def5678', 'platform-api:1.2.0',    62000,  NOW() - INTERVAL '1 hour',    NOW() - INTERVAL '59 minutes'),
  ('e0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001', 'in_progress', 'release/1.2','abc1234def5678', 'platform-api:1.2.0',    NULL,   NOW() - INTERVAL '5 minutes', NULL),
  ('e0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000003', 'failed',      'feat/redesign','deadbeef1234', 'web-dashboard:2.0.0-rc1', 30000, NOW() - INTERVAL '3 hours',  NOW() - INTERVAL '2 hours 59 minutes'),
  ('e0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000008', 'b0000000-0000-0000-0000-000000000003', 'succeeded',   'main',       'cafe0000babe',   'payment-service:3.1.0', 55000,  NOW() - INTERVAL '6 hours',   NOW() - INTERVAL '5 hours 59 minutes'),
  ('e0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000010', 'b0000000-0000-0000-0000-000000000001', 'pending',     'main',       'f00dcafe9999',   'notification-hub:1.0.0', NULL,  NULL,                         NULL)
ON CONFLICT DO NOTHING;

-- Alerts
INSERT INTO alerts (id, title, message, severity, status, source, deployment_id, environment_id, project_id) VALUES
  ('f0000000-0000-0000-0000-000000000001', 'Deploy failed: web-dashboard staging',        'Build step failed — exit code 1',       'error',    'open',         'deployment-pipeline', 'e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000002'),
  ('f0000000-0000-0000-0000-000000000002', 'High error rate on payment-service prod',     '5xx rate > 2% for 5 minutes',           'critical', 'acknowledged', 'health-check',        NULL,                                   'd0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000003'),
  ('f0000000-0000-0000-0000-000000000003', 'Certificate expiring: api.acme.com',          'TLS cert expires in 14 days',           'warning',  'open',         'cert-monitor',        NULL,                                   'd0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001'),
  ('f0000000-0000-0000-0000-000000000004', 'Deployment in progress: platform-api prod',   'Rolling update started',                'info',     'open',         'deployment-pipeline', 'e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- Audit Logs
INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, diff) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'user.created',         'user',        'b0000000-0000-0000-0000-000000000002', '{"after": {"email": "alice@devops.local"}}'),
  ('b0000000-0000-0000-0000-000000000001', 'role.assigned',        'user_role',   'b0000000-0000-0000-0000-000000000002', '{"role": "developer"}'),
  ('b0000000-0000-0000-0000-000000000002', 'project.created',      'project',     'c0000000-0000-0000-0000-000000000001', '{"after": {"slug": "platform-api"}}'),
  ('b0000000-0000-0000-0000-000000000002', 'environment.created',  'environment', 'd0000000-0000-0000-0000-000000000001', '{"after": {"name": "development"}}'),
  ('b0000000-0000-0000-0000-000000000002', 'deployment.created',   'deployment',  'e0000000-0000-0000-0000-000000000001', '{"after": {"status": "pending", "git_ref": "main"}}'),
  ('b0000000-0000-0000-0000-000000000002', 'deployment.succeeded', 'deployment',  'e0000000-0000-0000-0000-000000000001', '{"before": {"status": "in_progress"}, "after": {"status": "succeeded"}}'),
  ('b0000000-0000-0000-0000-000000000001', 'deployment.started',   'deployment',  'e0000000-0000-0000-0000-000000000003', '{"before": {"status": "pending"}, "after": {"status": "in_progress"}}'),
  ('b0000000-0000-0000-0000-000000000003', 'deployment.failed',    'deployment',  'e0000000-0000-0000-0000-000000000004', '{"before": {"status": "in_progress"}, "after": {"status": "failed"}}');
