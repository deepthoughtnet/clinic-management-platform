ALTER TABLE inventory_stocks
    DROP CONSTRAINT IF EXISTS uq_inventory_stocks_tenant_barcode;

ALTER TABLE inventory_stocks
    DROP CONSTRAINT IF EXISTS uq_inventory_stocks_tenant_external_code;

DROP INDEX IF EXISTS uq_inventory_stocks_tenant_barcode;
DROP INDEX IF EXISTS uq_inventory_stocks_tenant_external_code;

ALTER TABLE inventory_stocks
    DROP CONSTRAINT IF EXISTS uq_inventory_stocks_tenant_medicine_location_batch;

CREATE TEMP TABLE stock_batch_dedup_map AS
WITH ranked AS (
    SELECT
        id,
        tenant_id,
        medicine_id,
        location_id,
        batch_number,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, medicine_id, location_id, lower(btrim(batch_number))
            ORDER BY created_at ASC, updated_at ASC, id ASC
        ) AS row_number_in_batch,
        FIRST_VALUE(id) OVER (
            PARTITION BY tenant_id, medicine_id, location_id, lower(btrim(batch_number))
            ORDER BY created_at ASC, updated_at ASC, id ASC
        ) AS canonical_id
    FROM inventory_stocks
    WHERE batch_number IS NOT NULL
      AND btrim(batch_number) <> ''
)
SELECT id AS duplicate_id, canonical_id
FROM ranked
WHERE row_number_in_batch > 1;

CREATE TEMP TABLE stock_batch_merge_source AS
SELECT canonical_id, canonical_id AS source_id
FROM (SELECT DISTINCT canonical_id FROM stock_batch_dedup_map) canonical_rows
UNION ALL
SELECT canonical_id, duplicate_id AS source_id
FROM stock_batch_dedup_map;

WITH merged AS (
    SELECT
        merge_source.canonical_id,
        SUM(stock.quantity_received) AS total_quantity_received,
        SUM(stock.quantity_on_hand) AS total_quantity_on_hand,
        BOOL_OR(stock.active) AS any_active,
        MAX(stock.updated_at) AS latest_updated_at
    FROM stock_batch_merge_source merge_source
    JOIN inventory_stocks stock ON stock.id = merge_source.source_id
    GROUP BY merge_source.canonical_id
)
UPDATE inventory_stocks canonical
SET quantity_received = merged.total_quantity_received,
    quantity_on_hand = merged.total_quantity_on_hand,
    barcode = COALESCE(NULLIF(canonical.barcode, ''), (
        SELECT candidate.barcode
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.barcode IS NOT NULL
          AND btrim(candidate.barcode) <> ''
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    qr_code = COALESCE(NULLIF(canonical.qr_code, ''), (
        SELECT candidate.qr_code
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.qr_code IS NOT NULL
          AND btrim(candidate.qr_code) <> ''
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    external_code = COALESCE(NULLIF(canonical.external_code, ''), (
        SELECT candidate.external_code
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.external_code IS NOT NULL
          AND btrim(candidate.external_code) <> ''
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    purchase_reference_number = COALESCE(NULLIF(canonical.purchase_reference_number, ''), (
        SELECT candidate.purchase_reference_number
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.purchase_reference_number IS NOT NULL
          AND btrim(candidate.purchase_reference_number) <> ''
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    expiry_date = COALESCE(canonical.expiry_date, (
        SELECT candidate.expiry_date
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.expiry_date IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    purchase_date = COALESCE(canonical.purchase_date, (
        SELECT candidate.purchase_date
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.purchase_date IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    supplier_name = COALESCE(NULLIF(canonical.supplier_name, ''), (
        SELECT candidate.supplier_name
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.supplier_name IS NOT NULL
          AND btrim(candidate.supplier_name) <> ''
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    low_stock_threshold = COALESCE(canonical.low_stock_threshold, (
        SELECT candidate.low_stock_threshold
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.low_stock_threshold IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    unit_cost = COALESCE(canonical.unit_cost, (
        SELECT candidate.unit_cost
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.unit_cost IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    purchase_price = COALESCE(canonical.purchase_price, (
        SELECT candidate.purchase_price
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.purchase_price IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    selling_price = COALESCE(canonical.selling_price, (
        SELECT candidate.selling_price
        FROM stock_batch_merge_source merge_source
        JOIN inventory_stocks candidate ON candidate.id = merge_source.source_id
        WHERE merge_source.canonical_id = canonical.id
          AND candidate.selling_price IS NOT NULL
        ORDER BY CASE WHEN candidate.id = canonical.id THEN 0 ELSE 1 END,
                 candidate.updated_at DESC,
                 candidate.created_at DESC,
                 candidate.id DESC
        LIMIT 1
    )),
    active = merged.any_active,
    updated_at = GREATEST(canonical.updated_at, merged.latest_updated_at),
    version = canonical.version + 1
FROM merged
WHERE canonical.id = merged.canonical_id;

UPDATE inventory_transactions transaction_row
SET stock_batch_id = merge_map.canonical_id
FROM stock_batch_dedup_map merge_map
WHERE transaction_row.stock_batch_id = merge_map.duplicate_id;

UPDATE pharmacy_reconciliations reconciliation_row
SET stock_batch_id = merge_map.canonical_id
FROM stock_batch_dedup_map merge_map
WHERE reconciliation_row.stock_batch_id = merge_map.duplicate_id;

UPDATE pharmacy_sale_items sale_item
SET stock_batch_id = merge_map.canonical_id
FROM stock_batch_dedup_map merge_map
WHERE sale_item.stock_batch_id = merge_map.duplicate_id;

UPDATE pharmacy_sale_returns sale_return
SET stock_batch_id = merge_map.canonical_id
FROM stock_batch_dedup_map merge_map
WHERE sale_return.stock_batch_id = merge_map.duplicate_id;

DELETE FROM inventory_stocks duplicate_stock
USING stock_batch_dedup_map merge_map
WHERE duplicate_stock.id = merge_map.duplicate_id;

ALTER TABLE inventory_stocks
    ADD CONSTRAINT uq_inventory_stocks_tenant_medicine_location_batch
        UNIQUE (tenant_id, medicine_id, location_id, batch_number);
