
ALTER TABLE garage.appointments
DROP CONSTRAINT chk_appointment_cancellation;

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_cancellation
        CHECK (
            (
                (
                    status = 'CANCELLED'
                        AND NULLIF(BTRIM(cancellation_reason), '') IS NOT NULL
                        AND cancelled_at IS NOT NULL
                        AND cancelled_by_account_id IS NOT NULL
                        AND cancelled_by_type IS NOT NULL
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
                ) IS TRUE
            );
