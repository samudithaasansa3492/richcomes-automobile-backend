
CREATE TABLE garage.vehicles (
    id BIGSERIAL PRIMARY KEY,

    vehicle_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL
        REFERENCES garage.customers(id),

    vehicle_type VARCHAR(50) NOT NULL,
    brand VARCHAR(60),
    model VARCHAR(80),
    colour VARCHAR(40),

    engine_number VARCHAR(80),
    chassis_number VARCHAR(80),
    tag_number VARCHAR(50),

    delivery_date DATE,
    current_mileage INTEGER
        CHECK (current_mileage >= 0),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicles_customer_id
ON garage.vehicles(customer_id);
