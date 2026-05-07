-- Align schema with JPA optimistic locking fields and current tenant module columns.

alter table if exists inventory_stocks
    add column if not exists version bigint not null default 0;

alter table if exists medicine_catalogue
    add column if not exists version bigint not null default 0;

alter table if exists notification_history
    add column if not exists version bigint not null default 0;

alter table if exists tenants
    add column if not exists module_decisioning boolean not null default false;

alter table if exists tenants
    add column if not exists module_ai_copilot boolean not null default false;

alter table if exists tenants
    add column if not exists module_agent_intake boolean not null default false;
