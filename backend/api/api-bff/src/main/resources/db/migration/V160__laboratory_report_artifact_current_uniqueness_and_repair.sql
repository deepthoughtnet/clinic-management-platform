-- Owner: laboratory-workflow. Repair duplicate CURRENT report artifacts and normalize report metadata.
with current_winners as (
    select distinct on (a.tenant_id, a.lab_order_id)
        a.tenant_id,
        a.lab_order_id,
        a.id as winner_id
    from lab_report_publication_artifacts a
    where upper(coalesce(a.report_status, '')) = 'CURRENT'
    order by a.tenant_id, a.lab_order_id,
             coalesce(a.generated_at, a.published_at) desc,
             a.artifact_number desc,
             a.id desc
)
update lab_report_publication_artifacts a
set report_status = case
        when a.id = w.winner_id then 'CURRENT'
        else 'SUPERSEDED'
    end,
    superseded_by_artifact_id = case
        when a.id = w.winner_id then null
        else w.winner_id
    end,
    superseded_at = case
        when a.id = w.winner_id then null
        else coalesce(a.superseded_at, now())
    end
from current_winners w
where a.tenant_id = w.tenant_id
  and a.lab_order_id = w.lab_order_id;

with current_final_artifacts as (
    select
        a.id as artifact_id,
        a.tenant_id,
        a.lab_order_id
    from lab_report_publication_artifacts a
    join lab_order_items oi
        on oi.tenant_id = a.tenant_id
       and oi.lab_order_id = a.lab_order_id
    left join lab_order_test_lifecycles l
        on l.tenant_id = a.tenant_id
       and l.lab_order_id = a.lab_order_id
       and l.lab_order_item_id = oi.id
    where a.report_status = 'CURRENT'
      and a.report_mode = 'CONSOLIDATED'
      and a.report_type = 'FINAL'
    group by a.id, a.tenant_id, a.lab_order_id
)
update lab_report_publication_artifacts a
set selected_item_ids = (
    select jsonb_agg(oi.id::text order by oi.sort_order, oi.id)::text
    from lab_order_items oi
    join lab_order_test_lifecycles l
        on l.tenant_id = oi.tenant_id
       and l.lab_order_id = oi.lab_order_id
       and l.lab_order_item_id = oi.id
    where oi.tenant_id = a.tenant_id
      and oi.lab_order_id = a.lab_order_id
      and l.state in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED')
)
from current_final_artifacts cfa
where a.id = cfa.artifact_id;

with current_final_artifacts as (
    select
        a.id as artifact_id,
        a.tenant_id,
        a.lab_order_id,
        a.selected_item_ids
    from lab_report_publication_artifacts a
    join lab_order_items oi
        on oi.tenant_id = a.tenant_id
       and oi.lab_order_id = a.lab_order_id
    left join lab_order_test_lifecycles l
        on l.tenant_id = a.tenant_id
       and l.lab_order_id = a.lab_order_id
       and l.lab_order_item_id = oi.id
    where a.report_status = 'CURRENT'
      and a.report_mode = 'CONSOLIDATED'
      and a.report_type = 'FINAL'
    group by a.id, a.tenant_id, a.lab_order_id, a.selected_item_ids
),
parsed_selection as (
    select
        cfa.artifact_id,
        sel.value::uuid as lab_order_item_id,
        sel.ordinality as sort_order
    from current_final_artifacts cfa
    cross join lateral jsonb_array_elements_text(
        case
            when cfa.selected_item_ids is null or btrim(cfa.selected_item_ids) = '' then '[]'::jsonb
            else cfa.selected_item_ids::jsonb
        end
    ) with ordinality as sel(value, ordinality)
),
missing_publication_tests as (
    insert into lab_report_publication_tests (
        id, tenant_id, publication_artifact_id, lab_order_item_id, result_revision_number, included_at
    )
    select
        gen_random_uuid(),
        a.tenant_id,
        a.id,
        p.lab_order_item_id,
        coalesce((
            select max(rr.revision_number)
            from lab_result_revisions rr
            where rr.tenant_id = a.tenant_id
              and rr.lab_order_item_id = p.lab_order_item_id
        ), 1),
        coalesce(a.published_at, a.generated_at, now())
    from lab_report_publication_artifacts a
    join parsed_selection p on p.artifact_id = a.id
    where not exists (
        select 1
        from lab_report_publication_tests pt
        where pt.tenant_id = a.tenant_id
          and pt.publication_artifact_id = a.id
          and pt.lab_order_item_id = p.lab_order_item_id
    )
    returning publication_artifact_id
),
artifact_summary as (
    select
        a.id as artifact_id,
        count(pt.lab_order_item_id) as included_count,
        (
            select count(distinct oi2.id)
            from lab_order_items oi2
            left join lab_order_test_lifecycles l2
                on l2.tenant_id = oi2.tenant_id
               and l2.lab_order_id = oi2.lab_order_id
               and l2.lab_order_item_id = oi2.id
            where oi2.tenant_id = a.tenant_id
              and oi2.lab_order_id = a.lab_order_id
              and l2.state in ('PUBLISHED', 'REPORT_GENERATED', 'DELIVERED')
        ) as total_count,
        (
            select jsonb_agg(x.lab_order_item_id::text order by x.sort_order, x.lab_order_item_id)::text
            from (
                select distinct pt2.lab_order_item_id, oi2.sort_order
                from lab_report_publication_tests pt2
                join lab_order_items oi2
                    on oi2.tenant_id = pt2.tenant_id
                   and oi2.id = pt2.lab_order_item_id
                where pt2.publication_artifact_id = a.id
                  and pt2.tenant_id = a.tenant_id
            ) x
        ) as selected_item_ids
    from lab_report_publication_artifacts a
    join lab_report_publication_tests pt
        on pt.publication_artifact_id = a.id
       and pt.tenant_id = a.tenant_id
    join lab_order_items oi
        on oi.tenant_id = pt.tenant_id
       and oi.id = pt.lab_order_item_id
    group by a.id, a.tenant_id
)
update lab_report_publication_artifacts a
set selected_item_ids = s.selected_item_ids,
    report_mode = case
        when s.included_count <= 1 then 'INDIVIDUAL'
        when s.included_count = s.total_count then 'CONSOLIDATED'
        else 'GROUPED'
    end,
    report_type = case
        when s.included_count = s.total_count then 'FINAL'
        else 'INTERIM'
    end
from artifact_summary s
where a.id = s.artifact_id;

with current_winners as (
    select distinct on (a.tenant_id, a.lab_order_id)
        a.tenant_id,
        a.lab_order_id,
        a.id as winner_id
    from lab_report_publication_artifacts a
    order by a.tenant_id, a.lab_order_id,
             coalesce(a.generated_at, a.published_at) desc,
             a.artifact_number desc,
             a.id desc
)
update lab_report_publication_artifacts a
set report_status = case
        when a.id = w.winner_id then 'CURRENT'
        else 'SUPERSEDED'
    end,
    superseded_by_artifact_id = case
        when a.id = w.winner_id then null
        else w.winner_id
    end,
    superseded_at = case
        when a.id = w.winner_id then null
        else coalesce(a.superseded_at, now())
    end
from current_winners w
where a.tenant_id = w.tenant_id
  and a.lab_order_id = w.lab_order_id;

create unique index if not exists uq_lab_report_publication_artifacts_current
    on lab_report_publication_artifacts (tenant_id, lab_order_id)
    where report_status = 'CURRENT';
