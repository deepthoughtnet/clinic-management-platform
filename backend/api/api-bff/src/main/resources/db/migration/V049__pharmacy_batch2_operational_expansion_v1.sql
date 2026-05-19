ALTER TABLE medicine_catalogue
    ADD COLUMN IF NOT EXISTS barcode varchar(128),
    ADD COLUMN IF NOT EXISTS qr_code varchar(128),
    ADD COLUMN IF NOT EXISTS external_code varchar(128);

ALTER TABLE inventory_stocks
    ADD COLUMN IF NOT EXISTS barcode varchar(128),
    ADD COLUMN IF NOT EXISTS qr_code varchar(128),
    ADD COLUMN IF NOT EXISTS external_code varchar(128),
    ADD COLUMN IF NOT EXISTS purchase_reference_number varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_medicine_catalogue_tenant_barcode ON medicine_catalogue (tenant_id, barcode);
CREATE UNIQUE INDEX IF NOT EXISTS uq_medicine_catalogue_tenant_external_code ON medicine_catalogue (tenant_id, external_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_stocks_tenant_barcode ON inventory_stocks (tenant_id, barcode);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_stocks_tenant_external_code ON inventory_stocks (tenant_id, external_code);

CREATE TABLE IF NOT EXISTS pharmacy_suppliers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    supplier_name varchar(256) NOT NULL,
    contact_person varchar(256),
    phone varchar(32),
    email varchar(256),
    gst_number varchar(64),
    address text,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL,
    CONSTRAINT uq_pharmacy_suppliers_tenant_name UNIQUE (tenant_id, supplier_name)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_suppliers_tenant_active ON pharmacy_suppliers (tenant_id, active);
CREATE INDEX IF NOT EXISTS ix_pharmacy_suppliers_tenant_name ON pharmacy_suppliers (tenant_id, supplier_name);

CREATE TABLE IF NOT EXISTS pharmacy_reconciliations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    medicine_id uuid,
    stock_batch_id uuid,
    supplier_id uuid,
    system_quantity integer NOT NULL,
    physical_quantity integer,
    variance_quantity integer,
    reason text,
    status varchar(24) NOT NULL,
    sheet_document_id uuid,
    sheet_filename varchar(256),
    sheet_media_type varchar(128),
    sheet_storage_key varchar(512),
    extraction_status varchar(24),
    created_by uuid,
    adjusted_by uuid,
    confirmed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version integer NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_reconciliations_tenant_status ON pharmacy_reconciliations (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_pharmacy_reconciliations_tenant_created ON pharmacy_reconciliations (tenant_id, created_at);
