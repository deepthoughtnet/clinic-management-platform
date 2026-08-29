ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS business_reference varchar(160);
