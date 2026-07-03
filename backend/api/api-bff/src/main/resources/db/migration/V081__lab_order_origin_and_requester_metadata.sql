alter table if exists lab_orders
    add column if not exists order_origin varchar(32);

update lab_orders
set order_origin = case
    when consultation_id is not null then 'CONSULTATION'
    else 'WALK_IN'
end
where order_origin is null;

alter table if exists lab_orders
    alter column order_origin set not null;

alter table if exists lab_orders
    add column if not exists requested_by_internal_doctor_id uuid;

alter table if exists lab_orders
    add column if not exists external_doctor_name varchar(256);

alter table if exists lab_orders
    add column if not exists external_doctor_mobile varchar(32);

alter table if exists lab_orders
    add column if not exists external_clinic_name varchar(256);

alter table if exists lab_orders
    add column if not exists referral_source varchar(128);

update lab_orders
set requested_by_internal_doctor_id = doctor_user_id
where requested_by_internal_doctor_id is null
  and doctor_user_id is not null;
