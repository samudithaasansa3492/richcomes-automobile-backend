
-- V25: Link service job parts to store locations.


ALTER TABLE garage.service_job_parts
    ADD COLUMN store_location_id BIGINT;

ALTER TABLE garage.service_job_parts
    ADD CONSTRAINT fk_service_job_parts_store_location
        FOREIGN KEY (store_location_id)
            REFERENCES garage.store_locations(id);

CREATE INDEX idx_service_job_parts_store_location
    ON garage.service_job_parts(store_location_id);
