alter table if exists prescription_dispense_items
    add column if not exists pending_quantity integer not null default 0;

alter table if exists inventory_transactions
    add column if not exists before_quantity integer,
    add column if not exists after_quantity integer;
