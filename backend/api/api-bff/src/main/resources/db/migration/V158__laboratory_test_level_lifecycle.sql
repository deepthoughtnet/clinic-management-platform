-- Owner: laboratory-workflow. Additive compatibility model for ordered-test processing.
create table if not exists lab_order_test_lifecycles (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_item_id uuid not null references lab_order_items(id) on delete cascade,
    state varchar(40) not null,
    latest_result_revision integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid,
    version bigint not null default 0,
    constraint uq_lab_order_test_lifecycle_item unique (tenant_id, lab_order_item_id)
);
create index if not exists ix_lab_order_test_lifecycle_order_state
    on lab_order_test_lifecycles (tenant_id, lab_order_id, state);

create table if not exists lab_specimen_test_links (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_sample_id uuid not null references lab_order_samples(id) on delete cascade,
    lab_order_item_id uuid not null references lab_order_items(id) on delete cascade,
    active boolean not null default true,
    sample_accession_number varchar(64),
    sample_barcode_value varchar(128),
    sample_specimen_type varchar(128),
    sample_container_type varchar(128),
    sample_status varchar(32),
    collected_at timestamptz,
    received_at timestamptz,
    linked_at timestamptz not null default now(),
    linked_by uuid,
    unlinked_at timestamptz,
    unlinked_by uuid,
    constraint uq_lab_specimen_test_link unique (tenant_id, lab_order_sample_id, lab_order_item_id)
);
create index if not exists ix_lab_specimen_test_links_item
    on lab_specimen_test_links (tenant_id, lab_order_item_id, active);

create table if not exists lab_result_revisions (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_item_id uuid not null references lab_order_items(id) on delete cascade,
    revision_number integer not null,
    source_result_id uuid references lab_order_results(id) on delete set null,
    result_snapshot text not null,
    comments text,
    submission_state varchar(24) not null,
    entered_at timestamptz not null default now(),
    entered_by uuid,
    constraint uq_lab_result_revision unique (tenant_id, lab_order_item_id, revision_number)
);
create index if not exists ix_lab_result_revisions_order_item
    on lab_result_revisions (tenant_id, lab_order_id, lab_order_item_id, revision_number desc);

create table if not exists lab_test_verifications (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    lab_order_item_id uuid not null references lab_order_items(id) on delete cascade,
    result_revision_number integer,
    decision varchar(32) not null,
    reason varchar(128),
    comments text,
    verified_at timestamptz not null default now(),
    verified_by uuid not null
);
create index if not exists ix_lab_test_verifications_item
    on lab_test_verifications (tenant_id, lab_order_item_id, verified_at desc);

create table if not exists lab_report_publication_artifacts (
    id uuid primary key,
    tenant_id uuid not null,
    lab_order_id uuid not null references lab_orders(id) on delete cascade,
    artifact_number integer not null,
    filename varchar(512) not null,
    storage_reference varchar(1024),
    verification_token varchar(64),
    verification_url varchar(1024),
    delivery_channels text,
    selected_item_ids text,
    published_at timestamptz not null default now(),
    published_by uuid,
    notes text,
    constraint uq_lab_report_publication_artifact unique (tenant_id, lab_order_id, artifact_number)
);
create index if not exists ix_lab_report_publication_artifacts_order
    on lab_report_publication_artifacts (tenant_id, lab_order_id, published_at desc);

create table if not exists lab_report_publication_tests (
    id uuid primary key,
    tenant_id uuid not null,
    publication_artifact_id uuid not null references lab_report_publication_artifacts(id) on delete cascade,
    lab_order_item_id uuid not null references lab_order_items(id) on delete restrict,
    result_revision_number integer not null,
    included_at timestamptz not null default now(),
    constraint uq_lab_report_publication_test unique (tenant_id, publication_artifact_id, lab_order_item_id)
);
create index if not exists ix_lab_report_publication_tests_item
    on lab_report_publication_tests (tenant_id, lab_order_item_id);

insert into lab_order_test_lifecycles (
    id, tenant_id, lab_order_id, lab_order_item_id, state, latest_result_revision, created_at, updated_at
)
select gen_random_uuid(),
       i.tenant_id,
       i.lab_order_id,
       i.id,
       case
           when o.report_published_at is not null then 'PUBLISHED'
           when o.lab_verified_at is not null or o.status in ('REPORT_READY', 'REPORT_GENERATED', 'DELIVERED') then 'VERIFIED'
           when exists (
               select 1
               from lab_order_samples s
               where s.tenant_id = i.tenant_id
                 and s.lab_order_id = i.lab_order_id
                 and (s.lab_order_item_id = i.id or s.lab_order_item_id is null)
                 and s.status in ('RECOLLECTION_REQUIRED', 'REJECTED')
           ) then 'RECOLLECTION_REQUIRED'
           when exists (select 1 from lab_order_results r where r.tenant_id = i.tenant_id and r.lab_order_item_id = i.id) then 'RESULT_ENTERED'
           when exists (
               select 1
               from lab_order_samples s
               where s.tenant_id = i.tenant_id
                 and s.lab_order_id = i.lab_order_id
                 and (s.lab_order_item_id = i.id or s.lab_order_item_id is null)
                 and s.received_at is not null
           ) then 'SAMPLE_RECEIVED'
           when exists (
               select 1
               from lab_order_samples s
               where s.tenant_id = i.tenant_id
                 and s.lab_order_id = i.lab_order_id
                 and (s.lab_order_item_id = i.id or s.lab_order_item_id is null)
           ) then 'SAMPLE_COLLECTED'
           when o.ready_for_collection_at is not null or o.payment_collected_at is not null then 'READY_FOR_COLLECTION'
           else 'ORDERED'
       end,
       case when exists (select 1 from lab_order_results r where r.tenant_id = i.tenant_id and r.lab_order_item_id = i.id) then 1 else 0 end,
       coalesce(i.created_at, now()),
       coalesce(o.updated_at, i.created_at, now())
from lab_order_items i
join lab_orders o on o.id = i.lab_order_id and o.tenant_id = i.tenant_id
on conflict (tenant_id, lab_order_item_id) do nothing;

insert into lab_specimen_test_links (
    id, tenant_id, lab_order_id, lab_order_sample_id, lab_order_item_id, active,
    sample_accession_number, sample_barcode_value, sample_specimen_type, sample_container_type, sample_status,
    collected_at, received_at, linked_at, linked_by
)
select gen_random_uuid(),
       s.tenant_id,
       s.lab_order_id,
       s.id,
       i.id,
       true,
       s.accession_number,
       s.barcode_value,
       s.specimen_type,
       s.container_type,
       s.status,
       s.collected_at,
       s.received_at,
       coalesce(s.collected_at, s.created_at, now()),
       s.collected_by
from lab_order_samples s
join lab_order_items i on i.tenant_id = s.tenant_id and i.lab_order_id = s.lab_order_id
where s.lab_order_item_id is null or s.lab_order_item_id = i.id
on conflict (tenant_id, lab_order_sample_id, lab_order_item_id) do nothing;

insert into lab_result_revisions (
    id, tenant_id, lab_order_id, lab_order_item_id, revision_number, source_result_id,
    result_snapshot, comments, submission_state, entered_at, entered_by
)
select gen_random_uuid(),
       r.tenant_id,
       r.lab_order_id,
       r.lab_order_item_id,
       1,
       null,
       coalesce(
           jsonb_agg(
               jsonb_build_object(
                   'resultId', r.id,
                   'testCode', r.test_code,
                   'testName', r.test_name,
                   'parameterName', r.parameter_name,
                   'componentName', r.component_name,
                   'resultValue', r.result_value,
                   'unit', r.unit,
                   'referenceRange', r.reference_range,
                   'resultFlag', r.result_flag,
                   'criticalResult', r.critical_result
               )
               order by r.sort_order, r.created_at
           )::text,
           '[]'
       ),
       o.result_comments,
       'SUBMITTED',
       min(r.created_at),
       null
from lab_order_results r
join lab_orders o on o.id = r.lab_order_id and o.tenant_id = r.tenant_id
where r.lab_order_item_id is not null
group by r.tenant_id, r.lab_order_id, r.lab_order_item_id, o.result_comments
on conflict (tenant_id, lab_order_item_id, revision_number) do nothing;

insert into lab_test_verifications (
    id, tenant_id, lab_order_id, lab_order_item_id, result_revision_number,
    decision, reason, comments, verified_at, verified_by
)
select gen_random_uuid(),
       i.tenant_id,
       i.lab_order_id,
       i.id,
       nullif(l.latest_result_revision, 0),
       coalesce(o.lab_verification_decision, 'APPROVE'),
       o.lab_verification_reason,
       o.lab_verification_comments,
       o.lab_verified_at,
       o.lab_verified_by
from lab_order_items i
join lab_orders o on o.id = i.lab_order_id and o.tenant_id = i.tenant_id
join lab_order_test_lifecycles l on l.tenant_id = i.tenant_id and l.lab_order_item_id = i.id
where o.lab_verified_at is not null and o.lab_verified_by is not null
  and not exists (
      select 1
      from lab_test_verifications v
      where v.tenant_id = i.tenant_id
        and v.lab_order_item_id = i.id
        and v.decision = coalesce(o.lab_verification_decision, 'APPROVE')
  );

insert into lab_report_publication_artifacts (
    id, tenant_id, lab_order_id, artifact_number, filename, storage_reference,
    verification_token, verification_url, delivery_channels, selected_item_ids,
    published_at, published_by, notes
)
select gen_random_uuid(),
       o.tenant_id,
       o.id,
       1,
       coalesce(o.report_filename, concat(o.order_number, '-lab-report.pdf')),
       o.report_filename,
       o.report_verification_token,
       case
           when o.report_verification_token is null then null
           else '/api/public/lab/reports/' || o.report_verification_token || '/verify'
       end,
       o.report_delivery_channels,
       coalesce(selected.selected_item_ids, '[]'),
       coalesce(o.report_published_at, o.updated_at, now()),
       o.report_published_by_user_id,
       o.report_delivery_notes
from lab_orders o
left join lateral (
    select jsonb_agg(item_id::text order by sort_order, item_id)::text as selected_item_ids
    from (
        select distinct i.id as item_id, i.sort_order
        from lab_order_items i
        where i.tenant_id = o.tenant_id
          and i.lab_order_id = o.id
    ) deduped_items
) selected on true
where o.report_published_at is not null or o.report_filename is not null
on conflict (tenant_id, lab_order_id, artifact_number) do nothing;

insert into lab_report_publication_tests (
    id, tenant_id, publication_artifact_id, lab_order_item_id, result_revision_number, included_at
)
select gen_random_uuid(),
       a.tenant_id,
       a.id,
       i.id,
       coalesce(l.latest_result_revision, 1),
       coalesce(a.published_at, now())
from lab_report_publication_artifacts a
join lab_order_items i on i.tenant_id = a.tenant_id and i.lab_order_id = a.lab_order_id
left join lab_order_test_lifecycles l on l.tenant_id = i.tenant_id and l.lab_order_item_id = i.id
on conflict (tenant_id, publication_artifact_id, lab_order_item_id) do nothing;
