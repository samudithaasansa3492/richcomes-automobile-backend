-- No legacy identities are silently converted into developers.
ALTER TABLE garage.user_accounts ALTER COLUMN employee_id DROP NOT NULL;
ALTER TABLE garage.user_accounts
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN identity_kind VARCHAR(16) NOT NULL DEFAULT 'BUSINESS',
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0 CHECK (credential_version >= 0),
    ADD COLUMN recovery_email VARCHAR(254),
    ADD COLUMN recovery_last_sent_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_account_identity_kind CHECK (
        (identity_kind = 'BUSINESS' AND employee_id IS NOT NULL) OR
        (identity_kind = 'TECHNICAL' AND employee_id IS NULL));

CREATE TABLE garage.recovery_challenges (
    id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES garage.user_accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL,
    credential_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_recovery_challenges_account ON garage.recovery_challenges(account_id);
CREATE INDEX idx_recovery_challenges_expiry ON garage.recovery_challenges(expires_at);
CREATE TABLE garage.security_audit_events (
    id UUID PRIMARY KEY,
    actor_id BIGINT,
    target_id BIGINT NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_security_audit_target_time ON garage.security_audit_events(target_id, occurred_at);
ALTER TABLE garage.recovery_challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE garage.security_audit_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE garage.recovery_challenges, garage.security_audit_events FROM PUBLIC, anon, authenticated;
-- New tables use UUID identifiers; no sequences or browser policies are introduced.
