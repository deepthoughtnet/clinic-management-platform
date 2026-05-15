create table if not exists voice_sessions (
    id uuid primary key,
    tenant_id uuid not null,
    session_type varchar(64) not null,
    session_status varchar(32) not null,
    patient_id uuid,
    lead_id uuid,
    started_at timestamptz not null,
    ended_at timestamptz,
    escalation_required boolean not null default false,
    escalation_reason text,
    assigned_human_user_id uuid,
    ai_provider varchar(80),
    stt_provider varchar(80),
    tts_provider varchar(80),
    metadata_json text,
    created_at timestamptz not null default now()
);

create index if not exists ix_voice_sessions_tenant_status_started
    on voice_sessions (tenant_id, session_status, started_at);

create table if not exists voice_session_events (
    id uuid primary key,
    session_id uuid not null references voice_sessions(id) on delete cascade,
    event_type varchar(40) not null,
    event_timestamp timestamptz not null,
    sequence_number bigint not null,
    payload_summary text,
    correlation_id varchar(120),
    created_at timestamptz not null default now()
);

create index if not exists ix_voice_session_events_session_seq
    on voice_session_events (session_id, sequence_number);

create table if not exists voice_transcripts (
    id uuid primary key,
    session_id uuid not null references voice_sessions(id) on delete cascade,
    speaker_type varchar(24) not null,
    transcript_text text not null,
    transcript_timestamp timestamptz not null,
    confidence double precision,
    created_at timestamptz not null default now()
);

create index if not exists ix_voice_transcripts_session_time
    on voice_transcripts (session_id, transcript_timestamp);
