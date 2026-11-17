-- Supabase's public schema is an exposed Data API surface. Flyway
-- migration history must never be readable by browser/public roles.
-- The migration operator must be the table owner (or equivalent DDL role).
REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history
  FROM PUBLIC, anon, authenticated;
ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;

-- No browser SELECT policies: the privileged migration/table owner still
-- maintains and validates Flyway history without FORCE ROW LEVEL SECURITY.
-- Do not expose this metadata via public views or SECURITY DEFINER APIs.
