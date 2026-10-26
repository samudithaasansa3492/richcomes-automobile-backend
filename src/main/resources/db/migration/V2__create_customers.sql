CREATE SCHEMA IF NOT EXISTS garage;

CREATE TABLE garage.customers (
    id BIGSERIAL PRIMARY KEY,

    customer_type VARCHAR(20) NOT NULL,

    name VARCHAR(150) NOT NULL,

    nic VARCHAR(12) UNIQUE,

    address VARCHAR(255) NOT NULL,

    mobile VARCHAR(20) NOT NULL,

    office_number VARCHAR(20),

    email VARCHAR(120),

    city VARCHAR(100) NOT NULL,

    vat_number VARCHAR(50),

    credit_limit NUMERIC(12, 2),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_customer_type
        CHECK (customer_type IN ('PERSONAL', 'COMPANY')),

    CONSTRAINT chk_customer_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    CONSTRAINT chk_customer_nic_rule
        CHECK (
            (customer_type = 'PERSONAL' AND nic IS NOT NULL)
            OR
            (customer_type = 'COMPANY' AND nic IS NULL)
        ),

    CONSTRAINT chk_credit_limit
        CHECK (credit_limit IS NULL OR credit_limit >= 0)
);