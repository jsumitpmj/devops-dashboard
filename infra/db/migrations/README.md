# Database Migrations

## Convention

| Item | Rule |
|------|------|
| Filename | `NNN_description.sql` (zero-padded 3-digit version) |
| Idempotent | Every migration checks `schema_migrations` and skips if already applied |
| Transactional | Wrap in `BEGIN; ... COMMIT;` |
| Direction | Forward-only (no down migrations) |

## Applying Migrations

```bash
# Via Docker Compose
docker compose up -d postgres

# Manually
psql "$DATABASE_URL" -f infra/db/migrations/001_initial_schema.sql
psql "$DATABASE_URL" -f infra/db/migrations/002_indexes.sql
```

## Adding a New Migration

1. Create `infra/db/migrations/NNN_description.sql`
2. Wrap in `BEGIN; DO $$ ... $$; COMMIT;`
3. Check `schema_migrations` at the top
4. Insert into `schema_migrations` at the bottom
5. Test: apply twice — second run should no-op
