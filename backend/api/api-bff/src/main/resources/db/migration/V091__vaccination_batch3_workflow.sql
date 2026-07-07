alter table if exists patient_vaccinations
    add column if not exists created_by_user_id uuid null,
    add column if not exists updated_by_user_id uuid null,
    add column if not exists updated_at timestamp with time zone null,
    add column if not exists bill_id uuid null,
    add column if not exists bill_line_id uuid null,
    add column if not exists bill_number_snapshot varchar(64) null,
    add column if not exists bill_status_snapshot varchar(32) null,
    add column if not exists inventory_transaction_id uuid null,
    add column if not exists inventory_stock_batch_id uuid null,
    add column if not exists inventory_batch_number_snapshot varchar(128) null,
    add column if not exists inventory_batch_manufacturer_snapshot varchar(256) null,
    add column if not exists inventory_batch_expiry_date date null,
    add column if not exists reminder_notification_id uuid null,
    add column if not exists reminder_queued_at timestamp with time zone null,
    add column if not exists reminder_status varchar(32) null;

update patient_vaccinations
set updated_at = coalesce(updated_at, created_at)
where updated_at is null;

create index if not exists ix_patient_vaccinations_tenant_bill on patient_vaccinations (tenant_id, bill_id);
create index if not exists ix_patient_vaccinations_tenant_inventory_tx on patient_vaccinations (tenant_id, inventory_transaction_id);
create index if not exists ix_patient_vaccinations_tenant_reminder on patient_vaccinations (tenant_id, reminder_notification_id);
