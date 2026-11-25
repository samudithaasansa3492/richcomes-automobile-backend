-- Only run in isolated PostgreSQL after operator runtime SQL.
BEGIN;
SET LOCAL ROLE richcomes_runtime;
DO $test$
DECLARE n int; locked_id bigint; new_id uuid;
BEGIN
 SELECT count(*) INTO n FROM garage.roles;
 IF n <> 4 THEN RAISE EXCEPTION 'Seed roles not readable'; END IF;
 SELECT id INTO locked_id FROM garage.roles WHERE code='OWNER' FOR UPDATE;
 IF locked_id IS NULL THEN RAISE EXCEPTION 'OWNER lock unavailable'; END IF;
 BEGIN
   UPDATE garage.roles SET id=id WHERE id=locked_id;
   RAISE EXCEPTION 'Role edit unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   INSERT INTO garage.permissions(code,description) VALUES ('test:forbidden','Forbidden');
   RAISE EXCEPTION 'Permission insert unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   DELETE FROM garage.account_permission_audit;
   RAISE EXCEPTION 'Audit deletion unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   PERFORM count(*) FROM public.flyway_schema_history;
   RAISE EXCEPTION 'History read unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   CREATE TABLE garage.runtime_forbidden(id int);
   RAISE EXCEPTION 'DDL unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   ALTER TABLE garage.employees ADD COLUMN forbidden int;
   RAISE EXCEPTION 'ALTER unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 BEGIN
   TRUNCATE garage.employees CASCADE;
   RAISE EXCEPTION 'TRUNCATE unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
 INSERT INTO garage.employees(employee_code,nic,first_name,last_name,phone,position)
 VALUES ('RUNTIME-CI','RUNTIME-CI-NIC','Runtime','Test','0770000000','OWNER') RETURNING id INTO new_id;
 UPDATE garage.employees SET first_name='Changed' WHERE id=new_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Employee update blocked'; END IF;
 DELETE FROM garage.employees WHERE id=new_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Employee delete blocked'; END IF;
END
$test$;
ROLLBACK;
