-- Definitions only: never seed employees, accounts, passwords or enabled identities.
-- Existing descriptions and grants are preserved; this is an additive seed.
INSERT INTO garage.roles (code, description) VALUES
    ('OWNER_ADMIN', 'Owner and administrator'),
    ('CASHIER', 'Cashier'),
    ('TECHNICIAN', 'Technician')
ON CONFLICT (code) DO NOTHING;

INSERT INTO garage.permissions (code, description) VALUES
    ('employee:read', 'View employees'),
    ('employee:create', 'Register employees'),
    ('employee:update', 'Update employees'),
    ('employee:deactivate', 'Deactivate employees'),
    ('account:read', 'View user accounts'),
    ('account:create', 'Create disabled user accounts'),
    ('account:manage', 'Manage account activation and role assignments')
ON CONFLICT (code) DO NOTHING;

INSERT INTO garage.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM garage.roles r CROSS JOIN garage.permissions p
WHERE r.code = 'OWNER_ADMIN'
  AND p.code IN ('employee:read', 'employee:create', 'employee:update',
                 'employee:deactivate', 'account:read', 'account:create', 'account:manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- No Employee or Account management grants are added to CASHIER or TECHNICIAN.
-- Their business-module permissions require a separately agreed mapping.
