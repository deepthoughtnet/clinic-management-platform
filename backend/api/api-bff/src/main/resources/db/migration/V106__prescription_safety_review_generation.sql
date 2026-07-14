alter table prescription_safety_reviews
    add column if not exists snapshot_generation integer;

with numbered as (
    select
        id,
        row_number() over (
            partition by tenant_id, prescription_id
            order by created_at asc, updated_at asc, id asc
        )::integer as generated_generation
    from prescription_safety_reviews
)
update prescription_safety_reviews review
set snapshot_generation = numbered.generated_generation
from numbered
where review.id = numbered.id
  and review.snapshot_generation is null;

alter table prescription_safety_reviews
    alter column snapshot_generation set default 1;

alter table prescription_safety_reviews
    alter column snapshot_generation set not null;

drop index if exists uq_prescription_safety_reviews_snapshot;

create unique index if not exists uq_prescription_safety_reviews_generation
    on prescription_safety_reviews (tenant_id, prescription_id, snapshot_generation);

create index if not exists ix_prescription_safety_reviews_tenant_snapshot
    on prescription_safety_reviews (tenant_id, prescription_id, prescription_hash, patient_context_hash, rules_version);
