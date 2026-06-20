alter table if exists lab_tests
    alter column test_code drop not null;

alter table if exists lab_orders
    add column if not exists doctor_review_decision varchar(32);

alter table if exists lab_orders
    add column if not exists doctor_review_reason varchar(128);
