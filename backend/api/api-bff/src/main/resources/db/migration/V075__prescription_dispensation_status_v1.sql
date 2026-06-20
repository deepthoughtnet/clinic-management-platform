alter table if exists prescription_dispensations
    add column if not exists dispensing_status varchar(32) not null default 'NOT_DISPENSED',
    add column if not exists closure_reason varchar(60),
    add column if not exists closure_remarks varchar(250);
