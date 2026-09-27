# Jeevanam outbound communication integration analysis

**Scope:** read-only repository audit. No production code, migration, schema, or configuration was changed by this analysis.

## Executive conclusion

Engage/CarePilot is already the outbound CRM and execution boundary. It has campaigns and templates, maker-checker campaign lifecycle, a tenant-scoped execution ledger, provider-neutral message dispatch, retry/attempt history, delivery webhook history, scheduler locking, consent-related tenant settings, and operational reminder APIs. Email is already wired through this path.

The minimum path for automated email and voice reminders is therefore an extension of the existing CarePilot execution pipeline, not a new CRM or notification platform:

1. Keep campaign/template/approval/execution/timeline ownership in CarePilot.
2. Add event-to-campaign trigger adapters for the clinical facts that are not currently covered.
3. Add a `VOICE` channel mapping to the existing execution/dispatch lifecycle and adapt the existing `VoiceCallProvider` SPI; do not call a voice vendor from appointment, pharmacy, or laboratory code.
4. Add provider callback/status handling and structured Engage follow-up actions.
5. Add only the smallest settings/consent fields needed for voice and channel-specific policy, after confirming whether the existing JSON policy is sufficient.

The current implementation is strong for appointment/email reminders and lab-report notification foundations. It is not yet sufficient for reliable voice delivery outcomes, medicine exhaustion calculation, prescription-review gating, or all requested clinical trigger events.

## 1. Current-state findings

### Engage/CarePilot CRM

The CarePilot bounded context owns:

| Capability | Evidence | Finding |
|---|---|---|
| Campaigns | `backend/domains/carepilot-domain/.../campaign/db/CampaignEntity.java`, `CampaignType.java` | Campaigns carry tenant, type, trigger, audience, template, active/status and version/config metadata. Existing types include `APPOINTMENT_REMINDER`, `MISSED_APPOINTMENT_FOLLOW_UP`, `FOLLOW_UP_REMINDER`, `REFILL_REMINDER`, `VACCINATION_REMINDER`, `BILLING_REMINDER`, and custom. |
| Maker-checker | `CampaignEntity`, campaign approval services/controllers; migrations `V110__carepilot_campaign_maker_checker.sql` through `V113__...` | Submit/review/approve/activate/reject state is already represented. Reuse it for outbound policy; do not create a second approval workflow. |
| Execution ledger | `carepilot/execution/db/CampaignExecutionEntity.java` | A tenant-scoped row records campaign/template/channel/patient/schedule/source reference/window/status/attempts/provider and delivery status. It is the correct reminder identity and operational state owner. |
| Attempts/retries | `CampaignExecutionService`, `CampaignDeliveryAttemptEntity`, `CarePilotRetryPolicy` | Due rows are claimed and dispatched with retry scheduling and a durable attempt ledger. |
| Delivery timeline | `CampaignDeliveryEventEntity`, `CarePilotAnalyticsService`, `CarePilotRemindersService` | Provider webhook events, redacted payload, provider ID, external/internal status and event timestamps are queryable in Engage. |
| Leads/follow-up | `lead/db`, `lead/activity/LeadActivityService`, `LeadActivityType`, `followup` services/controllers | Human follow-up can remain a lead activity/task/action. Existing activities include scheduled/completed follow-up and appointment-booked history. A dedicated clinical-outcome activity vocabulary may need a small extension. |
| Scheduler | `api-bff/carepilot/CarePilotReminderScheduler.java`, `CarePilotReminderTriggerService.java` | Distributed lock plus fixed-delay tenant scan. The trigger service already queues appointment, missed-appointment, follow-up, refill, vaccination, billing, birthday, lead, and webinar work where configured. |
| Operational APIs | `CarePilotRemindersService`, CarePilot controllers/DTOs | Existing list/detail/timeline surfaces expose executions and delivery status. Extend responses only if new voice outcome fields are needed. |

### Communication and provider infrastructure

- `backend/providers/messaging-spi` supplies `MessageProvider`, `MessageRequest`, `MessageResult`, `MessageChannel`, `MessageDeliveryStatus`, and recipient abstractions.
- `backend/domains/carepilot-domain/.../MessageOrchestratorService.java` resolves a provider by channel and keeps provider failures inside a dispatch exception boundary.
- `backend/providers/messaging-email` already implements email delivery, including configuration/readiness and provider-neutral results.
- `backend/providers/messaging-whatsapp` already contains a WhatsApp provider/client path; it is a later channel enablement, not a new platform.
- `backend/providers/voice-spi` already defines `VoiceCallProvider`, `VoiceCallRequest`, `VoiceCallResult`, `VoiceCallStatus`, and provider call identifiers/transcripts. The SPI is reusable, but it is not currently represented in `MessageChannel`/`ChannelType` or wired into CarePilot dispatch.

### Appointment

`appointment-domain` owns booking, reschedule, cancellation, status transitions, and reminder-due facts. `AppointmentStatus` includes `BOOKED`, `COMPLETED`, `CANCELLED`, and `NO_SHOW`. Existing facts include `AppointmentBookedEvent`, `AppointmentRescheduledEvent`, `AppointmentCancelledEvent`, and `AppointmentReminderDueEvent`; `AppointmentReminderEventService` creates reminder-due events. The CarePilot scheduler separately queries appointment windows and creates idempotent executions. There is no clearly separate appointment-no-show business event in the inspected appointment event package; status changes are available and a no-show trigger adapter may be required.

### Pharmacy and prescriptions

`prescription-domain` stores prescription and medicine lines. `PrescriptionMedicineRecord`/`PrescriptionMedicineEntity` contain medicine name/type, strength, dosage, frequency, duration, timing and instructions. Dispensing is represented by pharmacy/prescription-dispensation tables and services (for example migration `V075__prescription_dispensation_status_v1.sql`). CarePilot currently has a configurable `refillEstimatedDays` fallback in `CarePilotReminderTriggerService`, but the inspected prescription line model does not provide a reliable remaining-quantity/dispense-date consumption calculation by itself. Automatic refill must therefore remain a reminder/request, not a clinical refill decision. Renewal/review reminders need an explicit prescription-safety/review signal; safety review infrastructure exists, but should be consulted rather than bypassed.

### Laboratory

`laboratory-domain` owns orders, ordered-test lifecycle, samples, result revisions, verification and report publication (`LaboratoryWorkflowService` and entities under `laboratory/db`). States include ordered/payment/collection/verification/published/report-ready concepts. `LabReportPublishedEvent` is already published and `notification-domain` has `LabReportReadyNotificationListener` delegating to `AppointmentBookedNotificationService`. This is a reusable event-to-notification foundation; CarePilot integration should consume the published fact rather than query unfinished lab rows.

### Patient and communication data

`PatientEntity` is tenant-scoped and already has mobile and email fields, with clinical fields kept separate. `TenantNotificationSettingsEntity`/`NotificationSettingsRecord` contain tenant channel enablement (`emailEnabled`, `smsEnabled`, `whatsappEnabled`, `inAppEnabled`), reminder toggles, quiet hours/timezone, default/fallback channel, marketing permission, `requirePatientConsent`, daily limits and policy JSON. Patient-level consent/preference fields were not found in the inspected patient aggregate; do not assume a tenant setting alone is sufficient for regulated outbound communications. A channel-consent/preference source must be identified or added before production voice/email at scale.

### AIVA

AIVA V2 is a conversation/orchestration layer. It can collect a patient request and invoke approved application operations, but it must not decide clinical eligibility, refill quantities, report interpretation, or reminder truth. Trigger facts, eligibility, consent, scheduling, idempotency and provider outcomes belong to domain/CarePilot services. AIVA may initiate an approved “contact clinic”, “confirm/reschedule”, or “request refill review” action and render outcomes; it is not the source of clinical decisions.

## 2. Reusable existing components

Use as-is where possible:

1. CarePilot campaigns, templates, maker-checker approval, activation and version/config-hash controls.
2. `CampaignExecutionEntity` as the durable per-reminder/per-channel execution record.
3. `CampaignDeliveryAttemptEntity`, retry policy, processing claims, dead-letter/suppressed/cancelled states.
4. `CampaignDeliveryEventEntity` and CarePilot analytics/reminder timeline for provider lifecycle callbacks.
5. `CarePilotReminderScheduler` distributed lock and tenant scan.
6. `TenantNotificationSettingsService` and existing quiet-hours/timezone/daily-limit settings.
7. `MessageOrchestratorService` and messaging provider registry.
8. Existing email provider and existing WhatsApp provider path for later enablement.
9. Existing `VoiceCallProvider` SPI and provider-neutral `VoiceCallRequest/Result` types.
10. Module business event infrastructure and durable listener jobs.
11. Appointment reminder-due, booked, rescheduled and cancelled events.
12. Lab report-published event and existing notification listener.
13. Patient/clinic contact data and tenant scoping.
14. Engage lead activities/follow-up services for human work, rather than inventing a task CRM.

## 3. Gaps and classification

| Need | Classification | Minimum gap |
|---|---|---|
| Appointment confirmation/reminders by email | Supported with configuration/template coverage | Confirm campaign binding, consent, recipient and tenant policy. |
| Appointment reminders by voice | Partially supported | Add voice channel mapping/adapter, phone/consent policy, provider implementation and callback status handling. |
| Missed appointment follow-up | Partially supported | Trigger exists in CarePilot scheduler, but a durable appointment NO_SHOW event/adapter and channel-specific campaign outcome should be made explicit. |
| Reschedule/cancellation notices | Partially supported | Existing appointment facts exist; add campaign trigger bindings and ensure source occurrence/version is part of idempotency. |
| Medicine exhaustion/refill reminder | Partially supported | Existing `REFILL_REMINDER` and estimated-days setting exist; reliable dispense/quantity/consumption projection and review gate are missing. |
| Prescription renewal/review | Partially supported | Safety/review domain exists, but no verified CarePilot event/eligibility adapter was found. |
| Lab test/sample reminders | Partially supported | Lab lifecycle is rich; event-to-CarePilot campaign adapters for pending collection need confirmation/addition. |
| Lab report ready | Supported foundation | `LabReportPublishedEvent` and notification listener exist; add CarePilot campaign binding if CRM timeline/campaign execution is required. |
| Follow-up consultation reminders | Partially supported | Appointment/follow-up campaign types exist; source event and due-date projection must be explicit. |
| Pharmacy reorder communication | Partially supported | Prescription/dispensation data exists; no safe automatic reorder decision should be inferred from free-text prescription alone. |
| Engage human follow-up | Supported foundation | Lead/activity/follow-up services exist; add structured outcome/activity types only where existing vocabulary cannot represent the result. |
| Voice confirmed/reschedule/refill outcomes | Missing integration | Provider callback/webhook mapping and structured CarePilot action creation are needed. |

## 4. Proposed integration architecture

```text
Appointment / Prescription / Pharmacy / Laboratory facts
                    |
         module business events (durable)
                    |
     CarePilot trigger adapters + eligibility/consent policy
                    |
       Campaign/template/approval selection
                    |
  CampaignExecution ledger (one logical occurrence + channel)
                    |
  existing scheduler/claim/retry + MessageOrchestratorService
             |                         |
       EmailMessageProvider      VoiceCallProvider adapter
             |                         |
       provider result/webhook -> delivery event + Engage action/activity
```

The clinical modules publish facts. CarePilot decides whether an approved communication campaign should be queued. The execution ledger is the idempotency boundary. Providers only deliver and report status. AIVA is an optional patient-facing orchestrator, never a replacement for this path.

## 5. Event and data flow

### Appointment

`AppointmentBookedEvent`/`AppointmentRescheduledEvent`/`AppointmentCancelledEvent` and `AppointmentReminderDueEvent` should be consumed by a CarePilot adapter. The adapter evaluates appointment status, local timezone, quiet hours, channel consent, campaign approval and patient contact data, then creates an execution with:

`sourceType=APPOINTMENT`, `sourceReferenceId=appointmentId`, `reminderWindow` (for example confirmation/H24/H2), and a reference occurrence timestamp/version.

For NO_SHOW, publish/consume a dedicated status fact or a reliable status-transition adapter; do not infer no-show from a polling race.

### Laboratory

`LabReportPublishedEvent` can trigger a report-ready campaign after publication/verification rules have succeeded. Pending-test/sample reminders should use explicit lifecycle transitions and an idempotent `labOrderId + test/item + reminder window` occurrence, not a broad daily query alone.

### Prescription/pharmacy

A prescription/dispensation/refill-review fact should be projected into a due reminder only after quantity, dispense date, duration/frequency and review requirements are known. If the projection cannot prove a refill date, queue a review/request campaign rather than “refill now”.

### Outcomes

Provider result/webhook -> `CampaignDeliveryEventEntity` and execution lifecycle. For voice, map `COMPLETED`, `NO_ANSWER`, `BUSY`, `FAILED`, and callback-confirmed business intents to delivery status plus a structured CarePilot lead activity/task. Business intents such as confirmed, reschedule requested, refill requested and human follow-up required must not mutate appointment/prescription state without the existing authenticated application command and confirmation rules.

## 6. Minimal code changes

1. Add `VOICE` to the provider-neutral channel vocabulary (`MessageChannel` and CarePilot `ChannelType`) only after migration compatibility review for existing enum rows.
2. Add a CarePilot voice dispatch adapter that converts the existing `MessageRequest`/execution context into `VoiceCallRequest`, invokes `VoiceCallProvider`, and maps `VoiceCallResult` to execution/delivery statuses.
3. Extend recipient resolution to require a valid patient phone and explicit channel consent; preserve tenant and campaign IDs in metadata.
4. Add provider webhook/callback handling in the inbound API adapter with signature verification, replay/idempotency checks and redacted event persistence.
5. Add/confirm event listeners for no-show, prescription/dispensation/review, pending lab collection and follow-up consultation facts.
6. Add structured outcome-to-Engage activity/task mapping. Reuse existing lead/follow-up services; do not create a parallel task store.
7. Add voice-specific campaign/template variables and settings only where existing policy JSON/notification settings cannot express them.
8. Add contract tests for event-to-execution idempotency, consent suppression, voice result mapping, webhook replay, tenant isolation and retry behavior.

No schema change is justified merely to support email: email already uses the existing path. A voice channel migration and callback dedupe fields are likely small schema extensions, but the exact migration should be designed only after confirming current enum persistence and webhook uniqueness constraints.

## 7. Suggested schema changes (only if required)

Prefer existing columns first. If implementation proves they are required:

- add `VOICE` enum values compatibly to `channel_type` and `MessageChannel`;
- add a stable occurrence/version component to the execution identity for rescheduled appointments and recurring refill/lab occurrences if current `sourceReferenceId + reminderWindow + channel` is insufficient;
- add a provider-event idempotency key/unique constraint if `CampaignDeliveryEventEntity` currently deduplicates only provider/message fields;
- add structured outcome code/action fields or use an existing activity payload mechanism for `NO_ANSWER`, `CONFIRMED`, `RESCHEDULE_REQUESTED`, `REFILL_REQUESTED`, and `HUMAN_FOLLOW_UP_REQUIRED`;
- add patient channel consent/preference storage only if no existing patient/identity consent source is authoritative.

Do not add a new notification table, CRM, patient entity, or provider identity table.

## 8. Provider abstraction design

Email and WhatsApp remain implementations of the existing `MessageProvider` SPI. Voice should remain behind the existing `VoiceCallProvider` SPI, with a CarePilot adapter rather than embedding Twilio/Exotel/etc. in appointment, pharmacy, laboratory or AIVA code.

The adapter contract should preserve:

- tenant, campaign and execution IDs;
- scheduled time and correlation ID;
- destination phone and locale/script reference;
- provider call ID and provider name;
- normalized status and failure category;
- callback transcript only when explicitly permitted and retention/redaction rules allow it.

Provider credentials and tenant routing belong in existing provider/configuration infrastructure or a tenant integration settings abstraction, never in campaign bodies or clinical tables.

## 9. AIVA role

AIVA may:

- explain that an appointment/lab/refill reminder was sent;
- collect explicit confirmation, reschedule, refill-review or human-assistance requests;
- invoke existing authenticated commands and create an Engage follow-up request;
- render provider outcomes already accepted by the domain.

AIVA must not:

- infer medicine exhaustion from conversational text;
- authorize an automatic refill or renewal;
- interpret a lab result;
- bypass consent, quiet hours, clinic authorization, campaign approval or execution idempotency;
- call communication vendors directly.

## 10. Engage integration points

Use the existing CarePilot interfaces:

- campaign/template CRUD and maker-checker for configuration;
- `CampaignExecutionService` for queue/process/retry/cancel/suppress/reschedule;
- `CampaignDeliveryEventEntity` and analytics for provider callbacks;
- `CarePilotRemindersService` and controllers for operations/timeline;
- lead/follow-up/activity services for human work;
- tenant notification settings for channel flags, consent policy, quiet hours, timezone and rate limits;
- module business-event listeners for clinical facts.

Outcome mapping should be explicit:

| Outcome | Execution/timeline | Engage action |
|---|---|---|
| delivered | delivery status/event + provider ID | none unless campaign policy says otherwise |
| failed | failed/retry/dead-letter with reason | human follow-up only after retry policy/dead-letter |
| no answer/busy | voice external status, bounded retry | follow-up task after configured attempts |
| confirmed | delivery event/business outcome | activity; domain confirmation remains a separate authenticated command |
| reschedule requested | activity/action with appointment reference | create follow-up/reschedule task; do not mutate appointment automatically |
| refill requested | activity/action with prescription reference | route to pharmacist/doctor review; no automatic dispense |
| human follow-up required | activity/task | assign through existing Engage workflow |

## 11. Email integration plan

Email can be enabled now through existing campaigns/templates, tenant settings, recipient resolution and `EmailMessageProvider`. The remaining work is primarily campaign coverage and event bindings for confirmation, no-show, lab-ready, follow-up and refill-review use cases. Add no new email transport or CRM. Ensure every message has a stable source occurrence, consent check, quiet-hour check, tenant template, retry policy and delivery timeline.

## 12. Automated voice-call integration plan

1. Select a provider implementing `VoiceCallProvider` and add a provider module/configuration adapter.
2. Map CarePilot `VOICE` execution to `VoiceCallRequest`.
3. Add phone validity/consent/time-window/maximum-attempt policy.
4. Persist provider call ID in the existing execution and delivery-event ledger.
5. Add signed webhook handling for answered, completed, no-answer, busy and failure statuses.
6. If the voice platform supports keypad/speech intent capture, treat it as an untrusted request that enters the existing authenticated/verified application workflow; do not directly mutate appointments or prescriptions from a webhook.
7. Create Engage activities/tasks for bounded business outcomes and human follow-up.
8. Keep transcripts off by default; if enabled, redact and retain under an explicit policy.

## 13. Medicine-refill reminder integration

The existing `REFILL_REMINDER` campaign and `refillEstimatedDays` setting are useful scaffolding, but they are not a sufficient clinical exhaustion engine. Build a small projection/trigger adapter from prescription and dispensing facts:

`dispensed quantity + dose/frequency + dispense date + duration/stop/review rules -> estimated due window`.

If quantity, frequency, adherence, substitution, or review status is unknown, send a “request refill review/contact pharmacy” message and create an Engage task rather than an automatic refill. Prescription safety-review signals and doctor/pharmacist approval must remain authoritative. Use `prescription/dispensation ID + occurrence/window + channel` for idempotency.

## 14. Risks and edge cases

- **Duplicate sends:** current appointment execution migration `V033__carepilot_appointment_reminder_hardening_v1.sql` adds a unique logical key over tenant, campaign, source reference, reminder window and channel, and `CampaignExecutionService` handles concurrent create races. Extend the occurrence key for reschedules/recurring clinical reminders rather than relying on message text.
- **Provider callbacks:** deduplicate by tenant + execution + provider event/call ID; authenticate signatures and reject replayed timestamps.
- **Retries:** distinguish transient provider failure from no-answer/busy and from consent/configuration suppression. Do not retry permanent invalid destination or opt-out.
- **Tenant isolation:** every campaign, execution, callback and recipient query must carry tenant ID; never trust provider callback tenant data without a signed correlation lookup.
- **Consent:** tenant `requirePatientConsent` and channel flags are not proof of patient-level consent. Establish the authoritative patient consent source before enabling voice/email broadly.
- **Privacy:** do not place diagnoses, lab values, prescription details or full transcripts in SMS/voice logs, execution metadata, analytics payloads or general audit logs.
- **Language:** templates should select an existing patient/session language preference where available; do not ask AIVA/LLM to translate clinical content at send time.
- **Quiet hours/timezone:** use `NotificationSettingsRecord` timezone/quiet-hours controls and clinic policy; voice calls need a separate call-window policy if current settings are insufficient.
- **Clinical safety:** “confirmed”, “refill requested” and “reschedule requested” are communication outcomes, not domain authorization.
- **No-show timing:** status polling and event delivery can race; require one authoritative occurrence and idempotent event handling.
- **Provider outage:** leave execution in retry/dead-letter state and expose an Engage task only according to policy; do not silently drop reminders.
- **Campaign/template approval:** never dispatch an unapproved or inactive campaign from an event listener.

## 15. Step-by-step implementation roadmap

1. **Contract inventory and test harness:** document event payloads, campaign bindings, consent source, channel status mapping, and current idempotency behavior.
2. **Email completion:** bind existing appointment/lab events to approved CarePilot email campaigns; add missing no-show/follow-up/refill-review adapters; verify timeline and suppression.
3. **Clinical trigger projections:** add explicit, durable adapters for no-show, lab collection, lab publication, prescription review and dispensing/refill windows.
4. **Voice channel:** add compatible channel vocabulary, CarePilot adapter, mock provider, tenant settings and consent checks; do not yet connect a real provider.
5. **Voice provider and callbacks:** implement provider adapter, signed webhook, event dedupe and normalized outcomes; test retries/no-answer/busy/failure.
6. **Engage outcomes:** map delivery/business outcomes to existing activities/tasks; add only missing structured types.
7. **Operational controls:** dashboards, dead-letter handling, per-tenant limits, quiet hours, audit redaction and runbooks.
8. **WhatsApp later:** reuse existing WhatsApp provider and execution ledger after consent/template/opt-out and webhook contracts are certified.
9. **UAT and rollout:** tenant-scoped canary, duplicate-send tests, consent/opt-out tests, provider failure tests, clinical safety review, then gradual enablement.

## 16. Do not build because Engage already provides it

- A new CRM, lead table, follow-up table, campaign engine, approval workflow or task/timeline product.
- A second reminder scheduler or independent retry queue.
- A new generic notification-history database for email/voice.
- Direct provider calls from appointment, prescription, pharmacy, laboratory or AIVA modules.
- A new patient/contact entity just for reminders.
- A second idempotency mechanism based on message text.
- A new WhatsApp transport; the existing provider abstraction should be reused.
- Clinical decision logic in AIVA, campaign templates, voice scripts or delivery webhooks.
- A duplicate appointment/lab/prescription status model.

## Recommended decision

**A — Existing Engage model is sufficient; extend appointments/reminders and provider adapters only**, with a small compatibility extension for a voice channel, callback outcome/idempotency handling, and missing clinical trigger facts. Do not redesign provider identity, CRM, scheduler, patient model, or AIVA.

## Audit evidence and limitations

This document is based on source inspection of the paths named above and the repository migrations. No production code was changed and no live provider dispatch was attempted. Before implementation, verify the authoritative patient-consent source, all prescription-dispensation quantities/date semantics, exact provider webhook contracts, and whether a dedicated appointment NO_SHOW event should be introduced rather than derived from status polling.
