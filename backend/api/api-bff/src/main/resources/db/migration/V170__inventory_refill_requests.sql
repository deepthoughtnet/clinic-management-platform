CREATE TABLE IF NOT EXISTS pharmacy_refill_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    prescription_id UUID NOT NULL,
    refill_cycle VARCHAR(96) NOT NULL,
    medicine_summary VARCHAR(512),
    due_date DATE,
    status VARCHAR(24) NOT NULL,
    source VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_refill_request_cycle UNIQUE (tenant_id, patient_id, prescription_id, refill_cycle)
);
CREATE INDEX IF NOT EXISTS ix_refill_request_patient ON pharmacy_refill_requests (tenant_id, patient_id, created_at);
