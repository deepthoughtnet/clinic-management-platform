alter table commercial_plan_pricing
    alter column setup_fee drop default;

alter table commercial_plan_pricing
    alter column setup_fee drop not null;

alter table commercial_plan_pricing
    alter column tax_percentage drop default;

alter table commercial_plan_pricing
    alter column tax_percentage drop not null;
