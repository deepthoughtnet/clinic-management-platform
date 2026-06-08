alter table careai_receptionist_tasks
    add column if not exists due_at timestamptz;

alter table careai_receptionist_tasks
    add column if not exists sla_status varchar(32);

alter table careai_receptionist_tasks
    add column if not exists first_response_at timestamptz;

alter table careai_receptionist_tasks
    add column if not exists breached_at timestamptz;

alter table careai_receptionist_tasks
    add column if not exists last_notification_at timestamptz;

alter table careai_receptionist_tasks
    add column if not exists last_staff_message_at timestamptz;

alter table careai_receptionist_tasks
    add column if not exists handling_mode varchar(32) not null default 'AI_HANDLING';

update careai_receptionist_tasks
set sla_status = coalesce(sla_status, 'ON_TIME'),
    due_at = coalesce(due_at, callback_due_at, created_at
        + case
            when priority = 'URGENT' then interval '5 minutes'
            when task_type = 'ESCALATION' then interval '10 minutes'
            when task_type = 'HUMAN_HANDOFF' then interval '15 minutes'
            when task_type = 'CALLBACK_REQUEST' then interval '4 hours'
            else interval '4 hours'
        end);

alter table careai_receptionist_tasks
    alter column sla_status set not null;

create index if not exists ix_careai_receptionist_tasks_tenant_sla_due
    on careai_receptionist_tasks (tenant_id, sla_status, due_at);

create index if not exists ix_careai_receptionist_tasks_tenant_status_due
    on careai_receptionist_tasks (tenant_id, status, due_at);
