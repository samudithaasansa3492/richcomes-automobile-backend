-- Simulate Supabase's two NOLOGIN browser roles on throwaway PostgreSQL only.
-- These roles must never be given LOGIN or grants to the garage schema.
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
