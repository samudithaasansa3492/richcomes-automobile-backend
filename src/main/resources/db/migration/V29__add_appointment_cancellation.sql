
ALTER TABLE garage.appointments
    ADD COLUMN cancellation_reason VARCHAR(1000),
    ADD COLUMN cancelled_at TIMESTAMP,
    ADD COLUMN cancelled_by_account_id BIGINT,
    ADD COLUMN cancelled_by_type VARCHAR(20);

ALTER TABLE garage.appointments
    ADD CONSTRAINT fk_appointments_cancelled_by_account
        FOREIGN KEY (cancelled_by_account_id)
            REFERENCES garage.user_accounts(id);

ALTER TABLE garage.appointments
DROP CONSTRAINT IF EXISTS chk_appointment_status;

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_status
        CHECK (status IN (
                          'PENDING',
                          'APPROVED',
                          'REJECTED',
                          'DONE',
                          'NOT_COMPLETED',
                          'CANCELLED'
            ));

ALTER TABLE garage.appointments
DROP CONSTRAINT IF EXISTS chk_appointment_contacted;

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_contacted
        CHECK (
            status IN ('PENDING', 'CANCELLED')
                OR contacted_at IS NOT NULL
            );

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_cancellation
        CHECK (
            (
                status = 'CANCELLED'
                    AND NULLIF(BTRIM(cancellation_reason), '') IS NOT NULL
                    AND cancelled_at IS NOT NULL
                    AND cancelled_by_account_id IS NOT NULL
                    AND cancelled_by_type IN ('CUSTOMER', 'STAFF')
                )
                OR
            (
                status <> 'CANCELLED'
                    AND cancellation_reason IS NULL
                    AND cancelled_at IS NULL
                    AND cancelled_by_account_id IS NULL
                    AND cancelled_by_type IS NULL
                )
            );
