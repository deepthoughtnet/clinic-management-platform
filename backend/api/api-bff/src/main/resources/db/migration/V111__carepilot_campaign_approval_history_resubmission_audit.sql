alter table carepilot_campaign_approval_history
    add column if not exists resolution_note text,
    add column if not exists previous_campaign_version integer,
    add column if not exists new_campaign_version integer,
    add column if not exists previous_configuration_hash varchar(128),
    add column if not exists new_configuration_hash varchar(128);
