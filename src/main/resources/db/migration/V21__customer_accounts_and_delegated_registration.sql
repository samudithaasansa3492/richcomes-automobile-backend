-- CUSTOMER identities use existing Spring-managed JWT accounts, never Supabase Auth.
-- Requires the reviewed V1-V20 history alignment. All changes are additive.
ALTER TABLE garage.user_accounts
  ADD COLUMN customer_id BIGINT,
  ADD CONSTRAINT uq_user_accounts_customer UNIQUE (customer_id),
  ADD CONSTRAINT fk_user_accounts_customer FOREIGN KEY (customer_id)
    REFERENCES garage.customers(id);

-- Each account belongs to exactly one identity domain.
ALTER TABLE garage.user_accounts DROP CONSTRAINT ck_account_identity_kind;
ALTER TABLE garage.user_accounts ADD CONSTRAINT ck_account_identity_kind CHECK (
  (identity_kind = 'BUSINESS' AND employee_id IS NOT NULL AND customer_id IS NULL) OR
  (identity_kind = 'CUSTOMER' AND employee_id IS NULL AND customer_id IS NOT NULL) OR
  (identity_kind = 'TECHNICAL' AND employee_id IS NULL AND customer_id IS NULL)
);

-- Direct overrides are opt-in. Only the application enforces the tiny
-- allowlist; the table itself is inaccessible to API/browser roles.
CREATE TABLE garage.account_permissions (
  account_id BIGINT NOT NULL REFERENCES garage.user_accounts(id) ON DELETE CASCADE,
  permission_id BIGINT NOT NULL REFERENCES garage.permissions(id),
  PRIMARY KEY (account_id, permission_id)
);
CREATE INDEX idx_account_permissions_permission ON garage.account_permissions(permission_id);
ALTER TABLE garage.account_permissions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE garage.account_permissions FROM PUBLIC, anon, authenticated;

INSERT INTO garage.roles (code, description)
VALUES ('CUSTOMER', 'Verified garage customer identity; self-service access only')
ON CONFLICT (code) DO NOTHING;

INSERT INTO garage.permissions (code, description) VALUES
 ('customer:self', 'Read own customer profile only'),
 ('customer:register', 'Create a customer record and enroll customer identity'),
 ('customer:read', 'Read garage customer records'),
 ('customer:manage', 'Manage garage customer records'),
 ('customer:delegate_register', 'Delegate customer enrollment to individual cashier accounts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO garage.role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM garage.roles r CROSS JOIN garage.permissions p
WHERE (r.code = 'CUSTOMER' AND p.code = 'customer:self')
   OR (r.code = 'OWNER' AND p.code IN ('customer:register','customer:read',
                                     'customer:manage','customer:delegate_register'))
ON CONFLICT (role_id,permission_id) DO NOTHING;

-- No CASHIER-wide grant: only selected CASHIER accounts receive direct
-- customer:register via explicitly authorized OWNER action.
-- No ADMIN business grants and no browser access to identity tables.
