-- V26: Add fine-grained inventory permissions.

-- Permit permission codes with multiple colon-separated segments.
ALTER TABLE garage.permissions
    DROP CONSTRAINT ck_permissions_code;

ALTER TABLE garage.permissions
    ADD CONSTRAINT ck_permissions_code
    CHECK (
        code ~ '^[a-z][a-z0-9_]*(:[a-z][a-z0-9_]*)+$'
    );

-- Add permissions required by SecurityConfig.
INSERT INTO garage.permissions (code, description) VALUES
    ('inventory:grn:confirm', 'Confirm inventory goods received notes'),
    ('inventory:grn:cancel', 'Cancel inventory goods received notes'),
    ('inventory:stock:adjust', 'Adjust inventory stock quantities')
ON CONFLICT (code) DO NOTHING;

-- Grant the new permissions to the business OWNER.
INSERT INTO garage.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM garage.roles r
CROSS JOIN garage.permissions p
WHERE r.code = 'OWNER'
  AND p.code IN (
      'inventory:grn:confirm',
      'inventory:grn:cancel',
      'inventory:stock:adjust'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
