-- Additive, after V21. This migration does not apply changes to live database.
-- OWNER receives explicit module authorities; CASHIER receives none by default.
-- Per-account delegation uses the garage.account_permissions table created in V21.
INSERT INTO garage.permissions (code, description) VALUES
    ('permission:delegate', 'Allow OWNER to delegate selected business actions to individual CASHIER accounts'),
    ('vehicle:read', 'Read vehicle records'),
    ('vehicle:create', 'Register vehicles'),
    ('vehicle:update', 'Update or activate/deactivate vehicles'),
    ('appointment:read', 'Read appointments'),
    ('appointment:create', 'Create appointments'),
    ('appointment:manage', 'Approve reject or complete appointments'),
    ('inventory:read', 'Read inventory suppliers items categories GRNs and stock movements'),
    ('inventory:create', 'Create inventory suppliers items categories or draft GRNs'),
    ('inventory:update', 'Update and activate/deactivate inventory records'),
    ('service:read', 'Read service categories items jobs and job parts'),
    ('service:create', 'Create service categories items jobs and job parts'),
    ('service:update', 'Update service categories items and job status'),
    ('service:delete', 'Remove service categories items or jobs'),
    ('billing:read', 'Read issued invoice records'),
    ('billing:calculate', 'Calculate invoice totals without issuing invoice'),
    ('report:read', 'Read business dashboard and reports'),
    ('report:export', 'Export business reports as PDF'),
    ('history:read', 'Read service history'),
    ('history:create', 'Create service history'),
    ('history:update', 'Update service history'),
    ('history:delete', 'Delete service history'),
    ('reminder:read', 'Read service reminders'),
    ('reminder:create', 'Create or generate service reminders'),
    ('reminder:manage', 'Update send or cancel reminders'),
    ('reminder:delete', 'Delete reminders'),
    ('notification:read', 'Read notifications'),
    ('notification:send', 'Send or retry notifications'),
    ('notification:delete', 'Delete notifications'),
    ('audit:read', 'Read audit trail (OWNER only; not delegatable)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO garage.role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM garage.roles r CROSS JOIN garage.permissions p
WHERE r.code = 'OWNER'
  AND p.code IN ('permission:delegate', 'vehicle:read', 'vehicle:create', 'vehicle:update', 'appointment:read', 'appointment:create', 'appointment:manage', 'inventory:read', 'inventory:create', 'inventory:update', 'service:read', 'service:create', 'service:update', 'service:delete', 'billing:read', 'billing:calculate', 'report:read', 'report:export', 'history:read', 'history:create', 'history:update', 'history:delete', 'reminder:read', 'reminder:create', 'reminder:manage', 'reminder:delete', 'notification:read', 'notification:send', 'notification:delete', 'audit:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Do not grant permission:delegate/audit:read to CASHIER or CUSTOMER.
-- Do not grant any business permission to technical ADMIN.
-- Explicit per-account grants are created/revoked only by the reviewed API.
