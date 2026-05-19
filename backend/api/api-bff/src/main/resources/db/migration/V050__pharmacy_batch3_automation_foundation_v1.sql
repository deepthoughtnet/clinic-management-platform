CREATE TABLE IF NOT EXISTS inventory_locations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    location_name varchar(256) NOT NULL,
    location_code varchar(64),
    location_type varchar(32) NOT NULL,
    is_default boolean NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    CONSTRAINT uq_inventory_locations_tenant_name UNIQUE (tenant_id, location_name)
);

CREATE INDEX IF NOT EXISTS ix_inventory_locations_tenant_active ON inventory_locations (tenant_id, active);
CREATE INDEX IF NOT EXISTS ix_inventory_locations_tenant_default ON inventory_locations (tenant_id, is_default);

ALTER TABLE inventory_stocks
    ADD COLUMN IF NOT EXISTS location_id uuid,
    ADD COLUMN IF NOT EXISTS supplier_invoice_id uuid;

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS location_id uuid,
    ADD COLUMN IF NOT EXISTS target_location_id uuid;

ALTER TABLE pharmacy_reconciliations
    ADD COLUMN IF NOT EXISTS location_id uuid,
    ADD COLUMN IF NOT EXISTS extraction_provider varchar(64),
    ADD COLUMN IF NOT EXISTS extraction_confidence numeric(5,2),
    ADD COLUMN IF NOT EXISTS extracted_rows_json text,
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS applied_at timestamp(6) with time zone;

CREATE TABLE IF NOT EXISTS pharmacy_purchase_orders (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    po_number varchar(128) NOT NULL,
    order_date date NOT NULL,
    expected_delivery_date date,
    items_json text NOT NULL,
    matching_status varchar(24) NOT NULL,
    variance_summary text,
    approval_note text,
    created_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    CONSTRAINT uq_pharmacy_purchase_orders_tenant_po UNIQUE (tenant_id, po_number)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_purchase_orders_tenant_supplier ON pharmacy_purchase_orders (tenant_id, supplier_id);
CREATE INDEX IF NOT EXISTS ix_pharmacy_purchase_orders_tenant_status ON pharmacy_purchase_orders (tenant_id, matching_status);

CREATE TABLE IF NOT EXISTS pharmacy_supplier_invoices (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    purchase_order_id uuid,
    invoice_number varchar(128) NOT NULL,
    invoice_date date NOT NULL,
    tax_amount numeric(18,2),
    total_amount numeric(18,2),
    items_json text NOT NULL,
    matching_status varchar(24) NOT NULL,
    variance_summary text,
    approval_note text,
    created_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    CONSTRAINT uq_pharmacy_supplier_invoices_tenant_invoice UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_supplier_invoices_tenant_supplier ON pharmacy_supplier_invoices (tenant_id, supplier_id);
CREATE INDEX IF NOT EXISTS ix_pharmacy_supplier_invoices_tenant_status ON pharmacy_supplier_invoices (tenant_id, matching_status);

CREATE TABLE IF NOT EXISTS pharmacy_goods_receipts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    purchase_order_id uuid,
    supplier_invoice_id uuid,
    receipt_number varchar(128) NOT NULL,
    received_at timestamp(6) with time zone NOT NULL,
    location_id uuid NOT NULL,
    items_json text NOT NULL,
    matching_status varchar(24) NOT NULL,
    variance_summary text,
    approval_note text,
    confirmed_by uuid,
    confirmed_at timestamp(6) with time zone,
    created_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    CONSTRAINT uq_pharmacy_goods_receipts_tenant_receipt UNIQUE (tenant_id, receipt_number)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_goods_receipts_tenant_supplier ON pharmacy_goods_receipts (tenant_id, supplier_id);
CREATE INDEX IF NOT EXISTS ix_pharmacy_goods_receipts_tenant_status ON pharmacy_goods_receipts (tenant_id, matching_status);

INSERT INTO inventory_locations (id, tenant_id, location_name, location_code, location_type, is_default, active, created_at, updated_at, version)
SELECT gen_random_uuid(), s.tenant_id, 'Main Pharmacy', 'MAIN_PHARMACY', 'PHARMACY', true, true, now(), now(), 0
FROM (SELECT DISTINCT tenant_id FROM inventory_stocks) s
ON CONFLICT (tenant_id, location_name) DO NOTHING;

UPDATE inventory_stocks s
SET location_id = l.id
FROM inventory_locations l
WHERE s.location_id IS NULL
  AND l.tenant_id = s.tenant_id
  AND l.is_default = true;

UPDATE inventory_transactions t
SET location_id = s.location_id
FROM inventory_stocks s
WHERE t.location_id IS NULL
  AND t.stock_batch_id = s.id;

UPDATE pharmacy_reconciliations r
SET location_id = s.location_id
FROM inventory_stocks s
WHERE r.location_id IS NULL
  AND r.stock_batch_id = s.id;

