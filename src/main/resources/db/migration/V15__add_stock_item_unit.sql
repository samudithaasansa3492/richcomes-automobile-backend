
-- V15: Add stock item measurement unit

ALTER TABLE garage.stock_items
    ADD COLUMN unit VARCHAR(10) NOT NULL DEFAULT 'PCS';

ALTER TABLE garage.stock_items
    ADD CONSTRAINT chk_stock_items_unit
        CHECK (unit IN ('PCS', 'L', 'ML'));
