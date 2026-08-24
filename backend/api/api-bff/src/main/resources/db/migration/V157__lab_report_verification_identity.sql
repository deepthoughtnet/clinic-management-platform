alter table if exists lab_orders
    add column if not exists report_verification_token varchar(64);

update lab_orders
set report_verification_token = replace(gen_random_uuid()::text, '-', '')
where report_verification_token is null
  and status in ('REPORT_READY', 'REPORT_GENERATED', 'DELIVERED');

create unique index if not exists uq_lab_orders_report_verification_token
    on lab_orders (report_verification_token)
    where report_verification_token is not null;
