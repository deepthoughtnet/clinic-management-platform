alter table appointments
    add column payment_bypass_reason varchar(64),
    add column payment_bypass_notes text,
    add column payment_bypassed_by uuid,
    add column payment_bypassed_at timestamp with time zone;
