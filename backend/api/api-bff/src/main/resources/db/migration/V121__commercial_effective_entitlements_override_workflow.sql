alter table commercial_tenant_entitlement_overrides
    add column if not exists submitted_at timestamp with time zone,
    add column if not exists submitted_by uuid,
    add column if not exists reviewed_at timestamp with time zone,
    add column if not exists reviewed_by uuid,
    add column if not exists review_remarks varchar(2000);

alter table commercial_tenant_entitlement_overrides
    drop constraint if exists ck_commercial_entitlement_overrides_status;

alter table commercial_tenant_entitlement_overrides
    add constraint ck_commercial_entitlement_overrides_status
        check (status in ('DRAFT', 'PENDING_APPROVAL', 'CHANGES_REQUESTED', 'APPROVED', 'ACTIVE', 'SCHEDULED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED'));
