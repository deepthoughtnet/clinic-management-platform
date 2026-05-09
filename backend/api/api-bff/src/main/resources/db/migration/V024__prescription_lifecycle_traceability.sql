alter table if exists prescriptions
    add column if not exists corrected_at timestamp with time zone;

alter table if exists prescriptions
    add column if not exists superseded_by_prescription_id uuid;

alter table if exists prescriptions
    add column if not exists superseded_at timestamp with time zone;

create index if not exists ix_prescriptions_tenant_parent on prescriptions (tenant_id, parent_prescription_id);
create index if not exists ix_prescriptions_tenant_superseded_by on prescriptions (tenant_id, superseded_by_prescription_id);
