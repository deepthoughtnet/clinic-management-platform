alter table patients add column if not exists long_term_medications text;
alter table patients add column if not exists surgical_history text;

alter table prescriptions add column if not exists version_number integer not null default 1;
alter table prescriptions add column if not exists parent_prescription_id uuid;
alter table prescriptions add column if not exists correction_reason text;
alter table prescriptions add column if not exists flow_type varchar(32);
alter table prescriptions add column if not exists finalized_by_doctor_user_id uuid;

drop index if exists uq_prescriptions_tenant_consultation;
create index if not exists ix_prescriptions_tenant_consultation_version on prescriptions (tenant_id, consultation_id, version_number desc);
