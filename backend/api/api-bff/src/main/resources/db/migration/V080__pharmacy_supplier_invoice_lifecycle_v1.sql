ALTER TABLE pharmacy_supplier_invoices
    ADD COLUMN IF NOT EXISTS invoice_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS variance_amount numeric(18,2),
    ADD COLUMN IF NOT EXISTS variance_reason text,
    ADD COLUMN IF NOT EXISTS cancel_reason text,
    ADD COLUMN IF NOT EXISTS attachment_file_name varchar(255),
    ADD COLUMN IF NOT EXISTS attachment_media_type varchar(128),
    ADD COLUMN IF NOT EXISTS attachment_storage_key varchar(512),
    ADD COLUMN IF NOT EXISTS attachment_size_bytes bigint;

UPDATE pharmacy_supplier_invoices
SET invoice_amount = COALESCE(total_amount, 0)
WHERE invoice_amount = 0;

ALTER TABLE pharmacy_supplier_invoices
    DROP CONSTRAINT IF EXISTS uq_pharmacy_supplier_invoices_tenant_invoice;

ALTER TABLE pharmacy_supplier_invoices
    ADD CONSTRAINT uq_pharmacy_supplier_invoices_tenant_supplier_invoice
        UNIQUE (tenant_id, supplier_id, invoice_number);

CREATE INDEX IF NOT EXISTS ix_pharmacy_supplier_invoices_tenant_lifecycle_status
    ON pharmacy_supplier_invoices (tenant_id, status);
