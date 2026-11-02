
CREATE TABLE IF NOT EXISTS garage.appointments (

    id BIGSERIAL PRIMARY KEY,

    customer_name VARCHAR(150) NOT NULL,

    customer_mobile VARCHAR(20) NOT NULL,

    vehicle_number VARCHAR(30) NOT NULL,

    customer_id BIGINT
        REFERENCES garage.customers(id),

    vehicle_id BIGINT
        REFERENCES garage.vehicles(id),

    requested_date DATE NOT NULL,

    confirmed_date DATE,

    appointment_time TIME,

    service_description TEXT,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    contacted_at TIMESTAMP,

    staff_notes TEXT,

    rejection_reason TEXT,

    not_completed_reason TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_appointment_status
        CHECK (
            status IN (
                'PENDING',
                'APPROVED',
                'REJECTED',
                'DONE',
                'NOT_COMPLETED'
            )
        ),

    CONSTRAINT chk_appointment_confirmed_schedule
        CHECK (
            status NOT IN (
                'APPROVED',
                'DONE',
                'NOT_COMPLETED'
            )
            OR (
                confirmed_date IS NOT NULL
                AND appointment_time IS NOT NULL
            )
        ),

    CONSTRAINT chk_appointment_not_completed_reason
        CHECK (
            status <> 'NOT_COMPLETED'
            OR NULLIF(TRIM(not_completed_reason), '') IS NOT NULL
        ),

    CONSTRAINT chk_appointment_contacted
        CHECK (
            status = 'PENDING'
            OR contacted_at IS NOT NULL
        )
);

ALTER TABLE garage.appointments
    ALTER COLUMN customer_mobile TYPE VARCHAR(10),
    ALTER COLUMN vehicle_number TYPE VARCHAR(8);

ALTER TABLE garage.appointments
    DROP CONSTRAINT IF EXISTS chk_appointment_mobile_format;

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_mobile_format
    CHECK (
        customer_mobile ~ '^07[0-9]{8}$'
    );

ALTER TABLE garage.appointments
    DROP CONSTRAINT IF EXISTS chk_appointment_vehicle_number_format;

ALTER TABLE garage.appointments
    ADD CONSTRAINT chk_appointment_vehicle_number_format
    CHECK (
        vehicle_number ~
        '^([A-Z]{2,3}|[0-9]{2,3})-[0-9]{4}$'
    );

CREATE INDEX IF NOT EXISTS idx_appointments_status_date
ON garage.appointments(status, requested_date);

CREATE INDEX IF NOT EXISTS idx_appointments_customer_id
ON garage.appointments(customer_id);

CREATE INDEX IF NOT EXISTS idx_appointments_vehicle_id
ON garage.appointments(vehicle_id);

ALTER TABLE garage.appointments
ENABLE ROW LEVEL SECURITY;
