alter table commercial_plan_pricing
    alter column trial_days drop default;

alter table commercial_plan_pricing
    alter column trial_days drop not null;
