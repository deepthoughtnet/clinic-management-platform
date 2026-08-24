-- Owner: laboratory-workflow. Additive versioning metadata for lab report artifacts.
alter table if exists lab_report_publication_artifacts
    add column if not exists report_mode varchar(24),
    add column if not exists report_type varchar(24),
    add column if not exists report_status varchar(24),
    add column if not exists generated_at timestamptz,
    add column if not exists generated_by uuid,
    add column if not exists superseded_by_artifact_id uuid references lab_report_publication_artifacts(id) on delete set null,
    add column if not exists superseded_at timestamptz;

create index if not exists ix_lab_report_publication_artifacts_verification_token
    on lab_report_publication_artifacts (verification_token);

update lab_report_publication_artifacts a
set report_mode = coalesce(a.report_mode, 'CONSOLIDATED'),
    report_type = coalesce(
        a.report_type,
        case
            when o.report_published_at is not null or o.status in ('REPORT_READY', 'REPORT_GENERATED', 'DELIVERED') then 'FINAL'
            else 'INTERIM'
        end
    ),
    report_status = coalesce(a.report_status, 'CURRENT'),
    generated_at = coalesce(a.generated_at, a.published_at),
    generated_by = coalesce(a.generated_by, a.published_by),
    verification_url = coalesce(
        a.verification_url,
        case
            when a.verification_token is null then null
            else '/api/public/lab/reports/' || a.verification_token || '/verify'
        end
    )
from lab_orders o
where o.id = a.lab_order_id
  and o.tenant_id = a.tenant_id;

update lab_report_publication_artifacts
set report_mode = coalesce(report_mode, 'CONSOLIDATED'),
    report_type = coalesce(report_type, 'FINAL'),
    report_status = coalesce(report_status, 'CURRENT'),
    generated_at = coalesce(generated_at, published_at),
    generated_by = coalesce(generated_by, published_by)
where report_mode is null
   or report_type is null
   or report_status is null
   or generated_at is null
   or generated_by is null;
