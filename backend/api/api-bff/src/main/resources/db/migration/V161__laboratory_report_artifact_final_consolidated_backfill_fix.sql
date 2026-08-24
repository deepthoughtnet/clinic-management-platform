-- Owner: laboratory-workflow. Complete legacy final report composition after V160.
--
-- V160 is immutable. This forward-only repair separates report content from
-- the test selected by the original publication event.
with current_final_artifacts as (
    select
        a.id as artifact_id,
        a.tenant_id,
        a.lab_order_id
    from lab_report_publication_artifacts a
    where a.report_status = 'CURRENT'
      and not exists (
          select 1
          from lab_order_test_lifecycles l
          where l.tenant_id = a.tenant_id
            and l.lab_order_id = a.lab_order_id
            and l.state not in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED', 'CANCELLED')
      )
      and exists (
          select 1
          from lab_order_test_lifecycles l
          where l.tenant_id = a.tenant_id
            and l.lab_order_id = a.lab_order_id
            and l.state in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED')
      )
),
final_artifact_item_candidates as (
    select
        cfa.artifact_id,
        oi.id as lab_order_item_id,
        oi.sort_order,
        1 as source_rank
    from current_final_artifacts cfa
    join lab_order_items oi
      on oi.tenant_id = cfa.tenant_id
     and oi.lab_order_id = cfa.lab_order_id
    join lab_order_test_lifecycles l
      on l.tenant_id = oi.tenant_id
     and l.lab_order_id = oi.lab_order_id
     and l.lab_order_item_id = oi.id
    where l.state in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED')
    union all
    select
        cfa.artifact_id,
        pt.lab_order_item_id,
        oi.sort_order,
        2 as source_rank
    from current_final_artifacts cfa
    join lab_report_publication_tests pt
      on pt.tenant_id = cfa.tenant_id
     and pt.publication_artifact_id = cfa.artifact_id
    join lab_order_items oi
      on oi.tenant_id = pt.tenant_id
     and oi.id = pt.lab_order_item_id
     and oi.lab_order_id = cfa.lab_order_id
    left join lab_order_test_lifecycles l
      on l.tenant_id = oi.tenant_id
     and l.lab_order_id = oi.lab_order_id
     and l.lab_order_item_id = oi.id
    where l.id is null
       or l.state in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED')
),
final_artifact_items as (
    select distinct on (artifact_id, lab_order_item_id)
        artifact_id,
        lab_order_item_id,
        sort_order
    from final_artifact_item_candidates
    order by artifact_id, lab_order_item_id, source_rank, sort_order, lab_order_item_id
),
missing_publication_tests as (
    insert into lab_report_publication_tests (
        id,
        tenant_id,
        publication_artifact_id,
        lab_order_item_id,
        result_revision_number,
        included_at
    )
    select
        gen_random_uuid(),
        a.tenant_id,
        a.id,
        fai.lab_order_item_id,
        coalesce((
            select max(rr.revision_number)
            from lab_result_revisions rr
            where rr.tenant_id = a.tenant_id
              and rr.lab_order_item_id = fai.lab_order_item_id
        ), 1),
        coalesce(a.published_at, a.generated_at, now())
    from lab_report_publication_artifacts a
    join final_artifact_items fai
      on fai.artifact_id = a.id
    where not exists (
        select 1
        from lab_report_publication_tests pt
        where pt.tenant_id = a.tenant_id
          and pt.publication_artifact_id = a.id
          and pt.lab_order_item_id = fai.lab_order_item_id
    )
    returning publication_artifact_id
)
update lab_report_publication_artifacts a
set selected_item_ids = (
        select jsonb_agg(
            fai.lab_order_item_id::text
            order by fai.sort_order, fai.lab_order_item_id
        )::text
        from final_artifact_items fai
        where fai.artifact_id = a.id
    ),
    report_mode = 'CONSOLIDATED',
    report_type = 'FINAL'
from current_final_artifacts cfa
where a.id = cfa.artifact_id;
