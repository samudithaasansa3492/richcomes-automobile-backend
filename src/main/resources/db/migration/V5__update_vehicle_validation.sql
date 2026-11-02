
ALTER TABLE garage.vehicles
    ALTER COLUMN vehicle_number TYPE VARCHAR(8),
    ALTER COLUMN current_mileage TYPE DOUBLE PRECISION
        USING current_mileage::DOUBLE PRECISION;

ALTER TABLE garage.vehicles
    DROP CONSTRAINT IF EXISTS chk_vehicle_number_format;

ALTER TABLE garage.vehicles
    ADD CONSTRAINT chk_vehicle_number_format
    CHECK (
        vehicle_number ~
        '^([A-Z]{2,3}|[0-9]{2,3})-[0-9]{4}$'
    );

ALTER TABLE garage.vehicles
    DROP CONSTRAINT IF EXISTS chk_vehicle_mileage;

ALTER TABLE garage.vehicles
    ADD CONSTRAINT chk_vehicle_mileage
    CHECK (
        current_mileage IS NULL
        OR (
            current_mileage >= 0
            AND current_mileage < 'Infinity'::float8
        )
    );
