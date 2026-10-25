
-- Richcomes Automobile
-- Migration V1: Employee Management

-- Private application schema
CREATE SCHEMA IF NOT EXISTS garage;

CREATE TABLE garage.employees (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    employee_code VARCHAR(30) NOT NULL UNIQUE,

    nic VARCHAR(20) NOT NULL UNIQUE,

    first_name VARCHAR(80) NOT NULL,

    last_name VARCHAR(80) NOT NULL,

    date_of_birth DATE,

    address TEXT,

    phone VARCHAR(20) NOT NULL,

    secondary_phone VARCHAR(20),

    email VARCHAR(120),

    position VARCHAR(30) NOT NULL
        CHECK (
            position IN (
                'OWNER_ADMIN',
                'CASHIER',
                'TECHNICIAN'
            )
        ),

    payment_rate NUMERIC(12,2)
        NOT NULL DEFAULT 0
        CHECK (payment_rate >= 0),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT NOW(),

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT NOW()
);
