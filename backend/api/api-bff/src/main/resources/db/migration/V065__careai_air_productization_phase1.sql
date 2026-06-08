create index if not exists ix_careai_conversations_tenant_status_channel_updated
    on careai_conversations (tenant_id, status, channel, updated_at desc);

create index if not exists ix_careai_receptionist_tasks_tenant_type_handling_status_created
    on careai_receptionist_tasks (tenant_id, task_type, handling_mode, status, created_at desc);
