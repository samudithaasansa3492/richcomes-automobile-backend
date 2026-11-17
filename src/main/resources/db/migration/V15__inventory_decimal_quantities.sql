

-- 1. STOCK ITEMS
-- ==========================================

ALTER TABLE garage.stock_items
    ALTER COLUMN current_quantity
        TYPE NUMERIC(14, 3)
        USING current_quantity::NUMERIC(14, 3),

    ALTER COLUMN reorder_level
        TYPE NUMERIC(14, 3)
        USING reorder_level::NUMERIC(14, 3);


-- ==========================================
-- 2. STOCK TRANSACTIONS
-- ==========================================

ALTER TABLE garage.stock_transactions
    ALTER COLUMN quantity
        TYPE NUMERIC(14, 3)
        USING quantity::NUMERIC(14, 3),

    ALTER COLUMN quantity_before
        TYPE NUMERIC(14, 3)
        USING quantity_before::NUMERIC(14, 3),

    ALTER COLUMN quantity_after
        TYPE NUMERIC(14, 3)
        USING quantity_after::NUMERIC(14, 3);


-- ==========================================
-- 3. GRN ITEMS
-- ==========================================

ALTER TABLE garage.grn_items
    ALTER COLUMN received_quantity
        TYPE NUMERIC(14, 3)
        USING received_quantity::NUMERIC(14, 3);

-- EXISTING CONSTRAINTS

