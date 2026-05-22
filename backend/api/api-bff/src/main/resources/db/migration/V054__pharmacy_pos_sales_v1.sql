CREATE TABLE IF NOT EXISTS pharmacy_sales (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    sale_number varchar(64) NOT NULL,
    patient_id uuid,
    customer_name varchar(256),
    customer_mobile varchar(64),
    sale_date_time timestamp(6) with time zone NOT NULL,
    location_id uuid,
    subtotal numeric(18,2) NOT NULL DEFAULT 0,
    discount numeric(18,2) NOT NULL DEFAULT 0,
    tax numeric(18,2) NOT NULL DEFAULT 0,
    total numeric(18,2) NOT NULL DEFAULT 0,
    paid_amount numeric(18,2) NOT NULL DEFAULT 0,
    due_amount numeric(18,2) NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL,
    notes text,
    created_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uq_pharmacy_sales_tenant_sale_number UNIQUE (tenant_id, sale_number)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sales_tenant_created
    ON pharmacy_sales (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sales_tenant_patient
    ON pharmacy_sales (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS pharmacy_sale_items (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    medicine_id uuid NOT NULL,
    stock_batch_id uuid,
    batch_number varchar(128),
    expiry_date date,
    quantity integer NOT NULL,
    returned_quantity integer NOT NULL DEFAULT 0,
    unit_price numeric(18,2) NOT NULL DEFAULT 0,
    discount numeric(18,2) NOT NULL DEFAULT 0,
    tax numeric(18,2) NOT NULL DEFAULT 0,
    line_total numeric(18,2) NOT NULL DEFAULT 0,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_pharmacy_sale_items_sale FOREIGN KEY (sale_id) REFERENCES pharmacy_sales (id)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sale_items_tenant_sale
    ON pharmacy_sale_items (tenant_id, sale_id);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sale_items_tenant_medicine
    ON pharmacy_sale_items (tenant_id, medicine_id);

CREATE TABLE IF NOT EXISTS pharmacy_sale_payments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    payment_date date NOT NULL,
    payment_date_time timestamp(6) with time zone,
    amount numeric(18,2) NOT NULL DEFAULT 0,
    payment_mode varchar(32) NOT NULL,
    reference_number varchar(128),
    receipt_number varchar(64) NOT NULL,
    notes text,
    received_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_pharmacy_sale_payments_sale FOREIGN KEY (sale_id) REFERENCES pharmacy_sales (id),
    CONSTRAINT uq_pharmacy_sale_payments_tenant_receipt UNIQUE (tenant_id, receipt_number)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sale_payments_tenant_sale
    ON pharmacy_sale_payments (tenant_id, sale_id, created_at DESC);

CREATE TABLE IF NOT EXISTS pharmacy_sale_returns (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    sale_item_id uuid NOT NULL,
    return_number varchar(64) NOT NULL,
    medicine_id uuid NOT NULL,
    stock_batch_id uuid,
    quantity integer NOT NULL,
    gross_amount numeric(18,2) NOT NULL DEFAULT 0,
    discount_amount numeric(18,2) NOT NULL DEFAULT 0,
    tax_amount numeric(18,2) NOT NULL DEFAULT 0,
    refund_amount numeric(18,2) NOT NULL DEFAULT 0,
    reusable boolean NOT NULL DEFAULT false,
    reason text NOT NULL,
    refund_mode varchar(32),
    reference_number varchar(128),
    notes text,
    created_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_pharmacy_sale_returns_sale FOREIGN KEY (sale_id) REFERENCES pharmacy_sales (id),
    CONSTRAINT fk_pharmacy_sale_returns_item FOREIGN KEY (sale_item_id) REFERENCES pharmacy_sale_items (id)
);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sale_returns_tenant_sale
    ON pharmacy_sale_returns (tenant_id, sale_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_pharmacy_sale_returns_tenant_number
    ON pharmacy_sale_returns (tenant_id, return_number);
