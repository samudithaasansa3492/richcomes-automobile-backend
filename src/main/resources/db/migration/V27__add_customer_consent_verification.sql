-- V27: Add customer consent and verification fields

ALTER TABLE garage.customers
    ADD COLUMN IF NOT EXISTS sms_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Add check constraints
ALTER TABLE garage.customers
    ADD CONSTRAINT chk_sms_consent CHECK (sms_consent IN (TRUE, FALSE));

ALTER TABLE garage.customers
    ADD CONSTRAINT chk_mobile_verified CHECK (mobile_verified IN (TRUE, FALSE));

ALTER TABLE garage.customers
    ADD CONSTRAINT chk_email_consent CHECK (email_consent IN (TRUE, FALSE));

ALTER TABLE garage.customers
    ADD CONSTRAINT chk_email_verified CHECK (email_verified IN (TRUE, FALSE));