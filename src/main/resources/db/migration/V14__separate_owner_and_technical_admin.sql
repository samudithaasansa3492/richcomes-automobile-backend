-- Correct the staff model without rewriting V1, V12 or V13 checksums.
-- Legacy OWNER_ADMIN identities become business OWNER identities, never developer ADMINs.
-- Abort rather than guess if an independently created OWNER/ADMIN role already exists.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM garage.roles WHERE code = 'OWNER_ADMIN') THEN
        RAISE EXCEPTION 'Legacy OWNER_ADMIN seed is missing; review role mapping before applying V14';
    END IF;
    IF EXISTS (SELECT 1 FROM garage.roles WHERE code IN ('OWNER', 'ADMIN')) THEN
        RAISE EXCEPTION 'OWNER or ADMIN already exists; review role mapping before applying V14';
    END IF;
    IF EXISTS (
        SELECT 1 FROM garage.user_accounts a
        JOIN garage.employees e ON e.id = a.employee_id
        JOIN garage.user_account_roles ar ON ar.user_account_id = a.id
        JOIN garage.roles r ON r.id = ar.role_id
        WHERE r.code = 'OWNER_ADMIN' AND (e.position = 'TECHNICIAN' OR EXISTS (
            SELECT 1 FROM garage.user_account_roles other JOIN garage.roles t ON t.id = other.role_id
            WHERE other.user_account_id = a.id AND t.code = 'TECHNICIAN'))
    ) THEN
        RAISE EXCEPTION 'Legacy owner linked to technician; review identity before applying V14';
    END IF;
END $$;

UPDATE garage.roles SET code = 'OWNER', description = 'Client owner: business administration'
WHERE code = 'OWNER_ADMIN';
INSERT INTO garage.roles (code, description)
VALUES ('ADMIN', 'Developer: technical support and security; no business permissions');
-- ADMIN intentionally receives no business grants. Technical endpoints are a separate rollout.

UPDATE garage.user_accounts a SET enabled = FALSE, updated_at = NOW()
WHERE EXISTS (SELECT 1 FROM garage.employees e WHERE e.id = a.employee_id AND e.position = 'TECHNICIAN')
   OR EXISTS (SELECT 1 FROM garage.user_account_roles ar JOIN garage.roles r ON r.id = ar.role_id
              WHERE ar.user_account_id = a.id AND r.code = 'TECHNICIAN');
DELETE FROM garage.user_account_roles WHERE role_id IN (SELECT id FROM garage.roles WHERE code = 'TECHNICIAN');
DELETE FROM garage.role_permissions WHERE role_id IN (SELECT id FROM garage.roles WHERE code = 'TECHNICIAN');
DELETE FROM garage.roles WHERE code = 'TECHNICIAN';

ALTER TABLE garage.employees DROP CONSTRAINT employees_position_check;
UPDATE garage.employees SET position = 'OWNER' WHERE position = 'OWNER_ADMIN';
ALTER TABLE garage.employees ADD CONSTRAINT employees_position_check
    CHECK (position IN ('OWNER', 'CASHIER', 'TECHNICIAN'));
-- No developer account, password, employee or additional business grant is created here.
