-- V23: CUSTOMER initial setup requires independently recorded proof and
-- customer-chosen credentials. No existing CUSTOMER account is auto-enabled.
ALTER TABLE garage.user_accounts
  ADD COLUMN customer_verified_at TIMESTAMPTZ,
  ADD COLUMN customer_verified_by BIGINT,
  ADD COLUMN customer_verification_ref VARCHAR(64),
  ADD COLUMN customer_verified_email VARCHAR(254),
  ADD COLUMN customer_password_set_at TIMESTAMPTZ,
  ADD CONSTRAINT ck_customer_proof_complete CHECK (
    (customer_verified_at IS NULL AND customer_verified_by IS NULL
      AND customer_verification_ref IS NULL AND customer_verified_email IS NULL)
    OR (identity_kind = 'CUSTOMER' AND customer_verified_at IS NOT NULL
      AND customer_verified_by IS NOT NULL AND customer_verification_ref IS NOT NULL
      AND customer_verified_email IS NOT NULL)
  ),
  ADD CONSTRAINT ck_customer_password_after_proof CHECK (
    customer_password_set_at IS NULL OR
    (identity_kind = 'CUSTOMER' AND customer_verified_at IS NOT NULL)
  );

-- Same 15-minute hashed one-use recovery challenge storage; a new purpose
-- prevents a normal password reset token from being used for onboarding.
ALTER TABLE garage.recovery_challenges
  DROP CONSTRAINT recovery_challenges_purpose_check;
ALTER TABLE garage.recovery_challenges
  ADD CONSTRAINT recovery_challenges_purpose_check
  CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'INITIAL_PASSWORD'));

-- RLS + revoked public/browser grants for recovery and security audit tables
-- remain in force from V20. Never grant CUSTOMER a direct SQL table role.
