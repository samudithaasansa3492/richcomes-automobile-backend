-- V31 operator-only deployment security, NOT a Flyway migration.
-- Run as database owner only on explicitly approved, frozen V31 target.
-- No password or LOGIN: operator sets password privately after verification.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
DO $guard$
BEGIN
 IF current_user <> 'postgres'
 OR (SELECT count(*) FROM public.flyway_schema_history WHERE success) <> 31
 OR (SELECT count(*) FROM public.flyway_schema_history) <> 31
 OR (SELECT max(version::int) FROM public.flyway_schema_history WHERE success) <> 31
 OR (SELECT count(*) FROM information_schema.tables WHERE table_schema='garage' AND table_type='BASE TABLE') <> 34
 OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname='richcomes_runtime')
 OR EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='garage')
 OR NOT (SELECT relrowsecurity FROM pg_class WHERE oid='public.flyway_schema_history'::regclass)
 OR has_table_privilege('anon','public.flyway_schema_history','SELECT')
 OR has_table_privilege('authenticated','public.flyway_schema_history','SELECT')
 THEN RAISE EXCEPTION 'V31 runtime preflight failed'; END IF;
END
$guard$;
CREATE ROLE richcomes_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS NOREPLICATION;
DO $connect$ BEGIN EXECUTE format('GRANT CONNECT ON DATABASE %I TO richcomes_runtime',current_database()); END $connect$;
GRANT USAGE ON SCHEMA garage TO richcomes_runtime;
-- No CREATE, TRUNCATE, REFERENCES, TRIGGER, ownership or schema-management rights.
-- All known tables enumerated; newly migrated tables need explicit privilege review.
DO $setup$
DECLARE t text; mode text;
BEGIN
 FOR t,mode IN SELECT * FROM (VALUES
 ('account_permission_audit','audit'),('account_permissions','mutable'),
 ('appointments','mutable'),('audit_logs','audit'),('customers','mutable'),
 ('employees','mutable'),('grn_item_allocations','mutable'),('grn_items','mutable'),
 ('grns','mutable'),('notifications','mutable'),('payments','mutable'),
 ('permissions','read'),('recovery_challenges','mutable'),('role_permissions','read'),
 ('roles','lock'),('sales_invoice_items','mutable'),('sales_invoices','mutable'),
 ('security_audit_events','audit'),('service_categories','mutable'),
 ('service_history','mutable'),('service_items','mutable'),('service_job_items','mutable'),
 ('service_job_parts','mutable'),('service_jobs','mutable'),('service_reminders','mutable'),
 ('stock_categories','mutable'),('stock_items','mutable'),('stock_transactions','mutable'),
 ('store_locations','mutable'),('store_stock_balances','mutable'),('suppliers','mutable'),
 ('user_account_roles','mutable'),('user_accounts','mutable'),('vehicles','mutable')
 ) AS config(name,access_mode)
 LOOP
   EXECUTE format('ALTER TABLE garage.%I ENABLE ROW LEVEL SECURITY',t);
   EXECUTE format('GRANT SELECT ON garage.%I TO richcomes_runtime',t);
   EXECUTE format('CREATE POLICY runtime_read ON garage.%I FOR SELECT TO richcomes_runtime USING (true)',t);
   IF mode IN ('mutable','audit') THEN
     EXECUTE format('GRANT INSERT ON garage.%I TO richcomes_runtime',t);
     EXECUTE format('CREATE POLICY runtime_insert ON garage.%I FOR INSERT TO richcomes_runtime WITH CHECK (true)',t);
   END IF;
   IF mode='mutable' THEN
     EXECUTE format('GRANT UPDATE, DELETE ON garage.%I TO richcomes_runtime',t);
     EXECUTE format('CREATE POLICY runtime_update ON garage.%I FOR UPDATE TO richcomes_runtime USING (true) WITH CHECK (true)',t);
     EXECUTE format('CREATE POLICY runtime_delete ON garage.%I FOR DELETE TO richcomes_runtime USING (true)',t);
   ELSIF mode='lock' THEN
     -- PostgreSQL SELECT FOR UPDATE needs UPDATE privilege and UPDATE USING.
     -- Restrict to id and forbid all actual updated rows through WITH CHECK.
     EXECUTE 'GRANT UPDATE (id) ON garage.roles TO richcomes_runtime';
     EXECUTE 'CREATE POLICY runtime_owner_lock ON garage.roles FOR UPDATE TO richcomes_runtime USING (code = ''OWNER'') WITH CHECK (false)';
   END IF;
 END LOOP;
 -- Sequences only for application insert paths, not static role/permission IDs.
 FOR t IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='garage' AND c.relkind='S'
   AND c.relname NOT IN ('roles_id_seq','permissions_id_seq')
 LOOP
   EXECUTE format('GRANT USAGE ON SEQUENCE garage.%I TO richcomes_runtime',t);
 END LOOP;
END
$setup$;
DO $verify$
BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='richcomes_runtime'
    AND (rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolreplication))
 OR has_schema_privilege('richcomes_runtime','garage','CREATE')
 OR has_schema_privilege('richcomes_runtime','public','CREATE')
 OR has_database_privilege('richcomes_runtime',current_database(),'CREATE')
 OR has_table_privilege('richcomes_runtime','public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE,TRUNCATE')
 OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='garage' AND c.relkind IN ('r','p')
    AND (NOT c.relrowsecurity OR NOT has_table_privilege('richcomes_runtime',c.oid,'SELECT')
    OR has_table_privilege('richcomes_runtime',c.oid,'TRUNCATE,TRIGGER,REFERENCES')
    OR has_table_privilege('anon',c.oid,'SELECT,INSERT,UPDATE,DELETE')
    OR has_table_privilege('authenticated',c.oid,'SELECT,INSERT,UPDATE,DELETE')))
 THEN RAISE EXCEPTION 'Runtime privilege verification failed'; END IF;
END
$verify$;
COMMIT;
