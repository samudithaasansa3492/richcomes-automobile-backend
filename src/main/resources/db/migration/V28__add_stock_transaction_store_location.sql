

ALTER TABLE garage.stock_transactions
    ADD COLUMN store_location_id BIGINT;

ALTER TABLE garage.stock_transactions
    ADD CONSTRAINT fk_stock_transactions_store_location
        FOREIGN KEY (store_location_id)
            REFERENCES garage.store_locations(id);

CREATE INDEX idx_stock_transactions_store_created
    ON garage.stock_transactions(store_location_id, created_at DESC);

-- Historic manual adjustments: reference_id was the selected store ID.
-- Keep the original reference_type/reference_id unchanged.
UPDATE garage.stock_transactions AS st
SET store_location_id = s.id
FROM garage.store_locations AS s
WHERE st.store_location_id IS NULL
  AND st.reference_type = 'STORE_LOCATION'
  AND st.reference_id = s.id;

-- Historic job-part movements: backfill only while the referenced job part
-- still exists, refers to the same stock item, and has a known source store.
UPDATE garage.stock_transactions AS st
SET store_location_id = jp.store_location_id
FROM garage.service_job_parts AS jp
WHERE st.store_location_id IS NULL
  AND st.reference_type = 'JOB_PART'
  AND st.reference_id = jp.id
  AND st.stock_item_id = jp.part_id
  AND jp.store_location_id IS NOT NULL;

-- Historic GRNs recorded one STOCK_IN transaction per item, even if the
-- item was split among stores. Backfill ONLY if there is exactly one
-- matching allocation and its quantity matches the recorded movement.
UPDATE garage.stock_transactions AS st
SET store_location_id = a.store_location_id
FROM garage.grn_items AS gi
         JOIN garage.grn_item_allocations AS a
              ON a.grn_item_id = gi.id
WHERE st.store_location_id IS NULL
  AND st.reference_type = 'GRN'
  AND st.transaction_type = 'STOCK_IN'
  AND st.reference_id = gi.grn_id
  AND st.stock_item_id = gi.stock_item_id
  AND st.quantity = a.allocated_quantity
  AND 1 = (
    SELECT COUNT(*)
    FROM garage.grn_item_allocations AS a2
    WHERE a2.grn_item_id = gi.id
);
