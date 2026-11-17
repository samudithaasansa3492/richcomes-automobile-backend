-- MANUAL OPERATOR SECURITY CHANGE — NOT A FLYWAY VERSIONED MIGRATION.
-- Run ONLY after DBA approval, current verified backup/restore capability,
-- planned maintenance window, and after all application Flyway migrations
-- finish. Running ALTER TABLE on Flyway's own history while Flyway itself
-- holds history locks can block. Never run this concurrently with Flyway.
--
-- Example after approval, using a database-owner/admin psql session:
--   psql -X -v ON_ERROR_STOP=1 <approved-connection> -f ops/security/lock-down-public-flyway-history.sql
-- Do not paste credentials into terminal/chat, source or shell history.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $guard$
DECLARE
  actual_owner text;
BEGIN
  SELECT pg_get_userbyid(c.relowner) INTO actual_owner
    FROM pg_class c WHERE c.oid = to_regclass('public.flyway_schema_history');
  IF actual_owner IS NULL THEN
    RAISE EXCEPTION 'Flyway history table is missing; refusing migration-history hardening';
  END IF;
  IF current_user <> actual_owner THEN
    RAISE EXCEPTION 'Must run as Flyway history table owner; refusing';
  END IF;
END
$guard$;

REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history
  FROM PUBLIC, anon, authenticated;
ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;

DO $verify$
BEGIN
  IF NOT (SELECT relrowsecurity FROM pg_class
          WHERE oid='public.flyway_schema_history'::regclass)
     OR has_table_privilege('anon','public.flyway_schema_history','SELECT')
     OR has_table_privilege('authenticated','public.flyway_schema_history','SELECT') THEN
    RAISE EXCEPTION 'Browser roles can still read migration history; rolling back';
  END IF;
END
$verify$;
COMMIT;

-- Table owner still bypasses non-FORCE RLS for Flyway's metadata operations.
-- Do not add browser SELECT policies. Confirm migration operator access
-- and use Supabase security advisors after this separate approved change.
