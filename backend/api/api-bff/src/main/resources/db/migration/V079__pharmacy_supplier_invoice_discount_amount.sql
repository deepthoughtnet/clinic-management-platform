ALTER TABLE pharmacy_supplier_invoices
    ADD COLUMN IF NOT EXISTS discount_amount numeric(18,2) NOT NULL DEFAULT 0;

UPDATE pharmacy_supplier_invoices
SET discount_amount = 0
WHERE discount_amount IS NULL;

ALTER TABLE pharmacy_supplier_invoices
    ALTER COLUMN discount_amount SET DEFAULT 0,
    ALTER COLUMN discount_amount SET NOT NULL;
