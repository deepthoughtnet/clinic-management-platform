# Engage voice reminder communication integration

## Scope

Connect the existing Engage campaign execution lifecycle to the certified voice
communication infrastructure without adding a scheduler, campaign engine,
retry mechanism, or delivery ledger. Production voice execution is disabled by
default with `engage.voice-reminders.enabled=false`.

## Placement

- Owning domain: `carepilot-domain` (campaign execution and delivery lifecycle)
- Inbound/application adapter: `api-bff` (existing communication-test/DotVoice
  infrastructure reused through a domain port)
- Persistence: existing CarePilot campaign execution and delivery-attempt tables
- Frontend: none
- Migration: none; `ChannelType` is persisted as a string and the new channel
  uses the existing execution row/attempt columns

## Design

`CampaignExecutionService` remains the only scheduler-driven execution owner.
For `VOICE` rows it creates a provider-neutral `ReminderCommunicationRequest`
and calls `VoiceReminderSender`. When disabled, it records the existing
`SKIPPED` delivery status with a deterministic `FEATURE_DISABLED` reason and
never calls DotVoice. When enabled, the API adapter delegates to the already
certified reminder voice path. Email/WhatsApp continue through
`MessageOrchestratorService` unchanged.

## Safety

Tenant, patient, campaign, execution, source, and reminder-window context are
passed as structured metadata. No raw prompts or new clinical data are stored.
Existing scheduler idempotency and retry transitions remain authoritative.
AIVA/STT/VAD/voice-input paths are out of scope.
