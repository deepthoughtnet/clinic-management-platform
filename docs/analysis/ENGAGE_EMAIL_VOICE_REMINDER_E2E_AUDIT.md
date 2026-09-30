# Engage Email / Voice Reminder End-to-End Audit

**Scope:** source/runtime audit and controlled Engage email provider cleanup completed 2026-09-28. No schema, scheduler, execution-ledger, Voice/DotVoice, AIVA, STT, or VAD behavior was changed.

## 1. Executive summary

Jeevanam currently has two communication execution planes:

1. **CarePilot/Engage campaign executions** (`carepilot_campaign_executions`) materialized by `CarePilotReminderTriggerService` and dispatched by `CampaignExecutionService`.
2. **The older notification-domain/outbox plane** (`NotificationActionService`, notification listeners, `NotificationDispatcher`, and notification history/outbox), which reacts directly to appointment and other domain events.

They are not one interchangeable ledger. A booking can therefore create an immediate notification-domain event while a separate appointment reminder is later materialized as a CarePilot campaign execution.

The most mature automatic Engage scenario is **appointment reminders**: CarePilot scans today/tomorrow appointments every reminder cycle and creates H24/H2 execution rows, subject to tenant settings, an active typed campaign/template, contact checks, quiet hours, provider readiness, and duplicate guards. Follow-up, refill, vaccination, billing, birthday/wellness, webinar, missed-appointment, and lead paths exist in varying degrees. Some are real provider executions; some are event/outbox or operational timeline entries.

Engage Email now explicitly selects the certified `msg91-email-smtp` provider through `MessagingProviderRegistry`. The selector is a provider ID, not the generic transport value `smtp`. The independent `clinic.mail.*` family remains available for notification-domain/non-Engage transactional mail.

VOICE is represented in the execution enum and the previously implemented disabled integration, but it is not consistently selectable/configurable through Engage. The scheduler’s notification preference model has no VOICE preference, its channel readiness resolver deliberately does not make VOICE usable, Campaigns and Reminders frontend channel unions omit VOICE, and `ENGAGE_VOICE_REMINDERS_ENABLED=false` prevents provider invocation even when a VOICE execution row exists.

**Recommended first production E2E scenario:** appointment H24 reminder. It has the clearest trigger, offset, duplicate keys, template values, and existing execution ledger. Keep voice disabled until tenant policy, consent, UI, and operational readiness are explicitly wired.

## 2. Current architecture and ownership

| Concern | Current owner/evidence | Observed behavior |
|---|---|---|
| Campaign/template domain | `backend/domains/carepilot-domain/.../campaign`, `.../template` | Tenant-scoped campaigns, approval lifecycle, template CRUD and channel enum. |
| Execution ledger | `.../execution/db/CampaignExecutionEntity`, `CampaignDeliveryAttemptEntity`, `CampaignDeliveryEventEntity` | `carepilot_campaign_executions`, delivery attempts/events, status and retry state. |
| Execution orchestration | `.../execution/service/CampaignExecutionService` | Claims due rows, renders template, dispatches Message or Voice, records attempt and outcome. |
| Materialization scheduler | `api-bff/.../CarePilotReminderScheduler` + `CarePilotReminderTriggerService` | Scans tenant data and creates due execution rows. |
| Provider dispatch scheduler | `api-bff/.../CarePilotSchedulerService` | Processes queued/retry-scheduled execution rows. |
| Message channel providers | `.../messaging/service/MessageOrchestratorService`, `MessagingProviderRegistry`, `MessageProvider` SPI | Email/SMS/WhatsApp/IN_APP dispatch. |
| Voice integration | `.../messaging/service/VoiceReminderSender`, API adapter `EngageVoiceReminderSender` | Delegates to the certified deterministic DotVoice reminder path; feature-flagged off. |
| Legacy event notifications | `notification-domain` listeners/services and `api-bff/.../NotificationActionService` | Immediate/event-driven notification outbox/history, separate from CarePilot execution ledger. |
| Tenant policy | `.../notificationsettings/TenantNotificationSettingsService`, `tenant_notification_settings` | Channel enablement, default/fallback, reminder-family flags, quiet hours, consent policy. |
| Frontend | `web-admin/src/products/carepilot`, `web-admin/src/pages/admin/notificationSettingsModel.ts` | Campaign/template/reminder/settings views; channel unions are narrower than backend enum. |

## 3. Scheduler inventory

### 3.1 CarePilot materialization scheduler

**Class:** `com.deepthoughtnet.clinic.api.carepilot.CarePilotReminderScheduler`  
**Method:** `queueDueReminders()`  
**Schedule:** `@Scheduled(fixedDelayString="${carepilot.reminders.fixed-delay:PT15M}")`; default 15 minutes.  
**Enablement:** `@ConditionalOnProperty(prefix="carepilot.reminders", name="enabled", havingValue="true")`.  
**Lock:** distributed lock `scheduler:carepilot-reminder`, wait `${platform.locks.scheduler-wait-timeout:PT2S}`.  
**Work:** `CarePilotReminderTriggerService.queueDueReminders()`.

The trigger service iterates every tenant from `PlatformTenantManagementService.list()`, checks the CarePilot tenant feature flag, reads/creates notification settings, then evaluates appointment, missed appointment, follow-up, refill, vaccination, billing, birthday/wellness, webinar, and lead paths. It stops after the configured aggregate batch size.

**Configuration caveat:** the scheduler class uses the `carepilot.reminders.*` namespace, while the inspected `application.yml` section is nested under `clinic.carepilot.reminders`. The effective profile/runtime should be checked before relying on the configured value; the code defaults remain PT15M and disabled unless the class condition resolves true.

### 3.2 CarePilot execution scheduler

**Class:** `CarePilotSchedulerService`  
**Method:** `processDueExecutions()`  
**Schedule:** `${clinic.carepilot.scheduler.fixedDelay:PT2M}`; default 2 minutes.  
**Enablement:** `@ConditionalOnProperty(prefix="clinic.carepilot.scheduler", name="enabled", havingValue="true")`.  
**Lock:** `scheduler:carepilot-campaign-execution`, distributed lock.  
**Query:** `CampaignExecutionRepository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(...)`, statuses `QUEUED` and `RETRY_SCHEDULED`.  
**Batch:** configured constructor batch size, default 25 (repository query is top 100; service limits processing to configured batch).

It calls `CampaignExecutionService.processDueExecutions`, which claims rows as PROCESSING, dispatches, records `CampaignDeliveryAttemptEntity`, and transitions to SUCCEEDED, RETRY_SCHEDULED, FAILED, or DEAD_LETTER.

### 3.3 Legacy notification reminder scheduler

**Class:** `api/notifications/NotificationReminderScheduler`  
**Schedule:** `${clinic.notifications.scheduler.fixedDelay:PT5M}`; default 5 minutes.  
**Enablement:** `NotificationsSchedulerProperties.enabled`.  
**Tenant iteration:** all platform tenants.  
**Work:** queues missed-appointment, follow-up, vaccination, and payment reminders through `NotificationActionService`. Scheduled appointment reminders are intentionally not handled here; CarePilot is the authoritative appointment-reminder owner. It reports `queuedCount`, `skippedDuplicateCount`, and `failedCount` from notification history/outbox operations.

This is not the CarePilot execution scheduler and does not create `CampaignExecutionEntity` rows for its direct queue paths.

### 3.4 Appointment no-show reconciliation

**Class:** `AppointmentNoShowReconciliationScheduler`  
**Schedule:** `${clinic.appointments.no-show.fixed-delay:PT15M}`; default 15 minutes.  
**Lock:** `scheduler:appointment-no-show-reconciliation`.  
**Work:** scans up to `${clinic.appointments.no-show.days-back:7}` days, marks eligible appointments `NO_SHOW`, emits the no-show notification action and staff alert. It is a status reconciliation trigger, not a CarePilot execution scheduler.

Other scheduled classes (`CarePilotAiCallScheduler`, AI reconciliation, clinical AI jobs, CareAI SLA) are outside Engage reminder delivery and are not provider paths for this audit.

## 4. Communication entry points and trigger inventory

| Source | Initiator / path | Timing and persistence | Result classification |
|---|---|---|---|
| Appointment booked | `AppointmentService` publishes `AppointmentBookedEvent`; notification-domain `AppointmentBookedNotificationListener` → `AppointmentBookedNotificationService.queueBooked` | Immediate event/outbox notification; no CarePilot execution row. | **IMPLEMENTED** (legacy notification plane). |
| Appointment rescheduled | `AppointmentService` publishes `AppointmentRescheduledEvent`; notification listener queues reschedule notification | Immediate event/outbox. | **IMPLEMENTED**. CarePilot future reminder rows are scan-derived; no explicit reschedule mutation in `CarePilotReminderTriggerService`. |
| Appointment cancelled | `AppointmentService` publishes `AppointmentCancelledEvent`; notification listener queues cancellation | Immediate event/outbox. | **IMPLEMENTED**. Existing CarePilot appointment execution rows are not visibly cancelled by this path. |
| Appointment H24/H2 reminder | `CarePilotReminderTriggerService.queueAppointmentReminders` | Periodic scan; `appointmentAt - 24h` and `appointmentAt - 2h`; catch-up at now+1m if within late window. Creates CarePilot execution. | **IMPLEMENTED**, subject to settings/campaign/contact/provider. |
| Appointment due legacy reminder | *(removed from `NotificationReminderScheduler`)* | No scheduled appointment reminder scan remains in this scheduler. | **CONSOLIDATED TO ENGAGE**. |
| Missed appointment follow-up | CarePilot trigger scans NO_SHOW; no-show reconciler marks status; legacy patient reminder scan removed | CarePilot default delay PT2H from appointment time, configurable campaign notes. | **IMPLEMENTED** in Engage for patient follow-up; reconciliation/staff alert remain operational paths. |
| Consultation follow-up | CarePilot trigger scans prescriptions with `followUpDate`; legacy action scans completed/draft consultations and publishes `FollowUpDueEvent` | CarePilot offset default 24h before prescription follow-up date; consultation event remains periodic. | **PARTIAL**; prescription and consultation follow-up fields are separate concepts. |
| Refill | CarePilot trigger scans finalized prescriptions and estimates due date from configured days/medicine data | Estimated refill date = finalized date + configured/derived refill days; default configured days 30, offset default 0. | **PARTIALLY IMPLEMENTED**; no confirmed pharmacy dispense/exhaustion event path in this audit. |
| Vaccination | CarePilot scans vaccination due/overdue records; vaccination service also exposes manual queue path | Due/overdue window with default one-day offset; event/outbox path exists. | **IMPLEMENTED/PARTIAL** depending on plane; CarePilot execution requires settings/campaign/contact. |
| Billing/payment | CarePilot scans bills over configured overdue threshold; legacy scheduler publishes PaymentReminderEvent | CarePilot reminderAt now after threshold; default overdue threshold 3 days. | **IMPLEMENTED** in both planes with differing ledgers. |
| Birthday/wellness | CarePilot scans active patient DOB and creates wellness execution | Target date = birthday minus configured days (default 0), send time default 09:00 system zone. | **IMPLEMENTED** as CarePilot path. |
| Webinar | CarePilot scans registrations for confirmation/H24/H1/follow-up | Windows derived from webinar start/end and registration; execution rows. | **IMPLEMENTED** in trigger code; UI is primarily webinar automation/placeholder and needs runtime verification. |
| Lead follow-up | `CarePilotReminderTriggerService` records `LeadActivity` deterministic follow-up marker | No patient provider execution; operational timeline only. | **UI/OPS MODEL ONLY** for patient delivery. |
| Campaign manual run | `CarePilotCampaignTriggerService.trigger` resolves audience/template and creates execution rows; requires manual dispatcher flag | Immediate queued execution, then CarePilot execution scheduler. | **IMPLEMENTED**, but frontend channel union omits VOICE. |
| Scheduled/event-based campaign | Campaign has `MANUAL`, `SCHEDULED`, `EVENT_BASED` trigger types and approval lifecycle; no separate generic campaign scheduler was found in this audit. | Active/approved lifecycle exists; concrete automatic trigger is the typed reminder scanner, not a generic trigger engine. | **PARTIALLY IMPLEMENTED / NOT WIRED generically**. |
| Prescription-ready / lab/report / pharmacy actions | Notification-domain events exist for prescription-ready and related domain notifications; no CarePilot campaign materialization was found for lab report-ready or pharmacy refill delivery. | Event/outbox where listener exists; no evidence of Engage campaign execution for lab result. | **PARTIAL / NOT FOUND** per event. |

Template categories alone are not evidence of a trigger. For example, `FOLLOW_UP`, `VACCINATION`, `WELLNESS`, `BILLING`, `WEBINAR`, and `LEAD` are model/UI categories, while only the trigger paths above create actual CarePilot executions.

## 5. Appointment reminder trace and exact timeline

### CarePilot path

```text
Appointment persisted (T0)
  -> AppointmentService emits booked/rescheduled/cancelled events (immediate legacy notification plane)
  -> CarePilotReminderScheduler every ~15m (when enabled)
  -> scan today and tomorrow appointments
  -> for BOOKED appointment with patient email and active APPOINTMENT_REMINDER campaign/template:
       H24 reminderAt = appointmentAt - 24h
       H2  reminderAt = appointmentAt - 2h
  -> duplicate checks (source appointment + window + channel; recipient/time ±5m)
  -> CampaignExecutionEntity QUEUED
  -> CarePilotSchedulerService every ~2m (when enabled)
  -> MessageOrchestratorService or VoiceReminderSender
```

The trigger uses tenant settings `appointmentRemindersEnabled`, `appointmentReminder24hEnabled`, and `appointmentReminder2hEnabled`. It skips cancelled/no-show/completed appointments. If a due timestamp is late by up to the implemented catch-up boundary, it schedules approximately now+1 minute; otherwise it does not backfill arbitrarily. Reschedules change the appointment’s current date/time, but this audit found no explicit cancellation/reconciliation of already-created CarePilot H24/H2 rows; duplicate keys protect re-materialization, not necessarily stale rows.

### Legacy path

`NotificationReminderScheduler` still runs its non-appointment families every 5m, but no longer calls `AppointmentReminderEventService`, queues scheduled appointment reminders, or queues the legacy patient-facing missed-appointment scan. The former `NotificationActionService.queueAppointmentReminders` and missed-appointment helpers were removed. CarePilot H24/H2 and NO_SHOW follow-up materialization are now the authoritative appointment reminder paths. `AppointmentReminderProperties` remains only for the existing notification-operations configuration view and is not used to dispatch appointment reminders.

## 6. Refill / medication audit

`CarePilotReminderTriggerService.queueRefillReminders` scans prescriptions with a finalized date and non-cancelled status. It uses configured `estimatedRefillDays` (default 30) or the existing prescription medicine data through `resolveRefillDays`, then computes:

`refillDueDate = finalizedDate + refillDays; reminderAt = refillDueDate - reminderOffset`.

The execution template values include medicine name, prescription date, and refill due date. The code does not establish a pharmacy dispensing ledger, remaining quantity, actual dosage/frequency exhaustion calculation, or a provider-confirmed refill event as the source of truth. Therefore the UI/category is present, but medically accurate refill timing is **PARTIAL** and configuration-estimated.

## 7. Follow-up, vaccination, wellness, billing, webinar, and lead matrix

| Family | Template/model | Trigger/scheduler | Provider execution | Status |
|---|---|---|---|---|
| FOLLOW_UP | CarePilot campaign type/template category | Prescription follow-up date scan; legacy consultation event path | Yes for CarePilot when eligible | PARTIAL (two source models). |
| VACCINATION | Campaign type; Care template default | Vaccination due/overdue scan; manual vaccination queue/event path | Yes where CarePilot binding/settings/contact permit | IMPLEMENTED/PARTIAL. |
| WELLNESS | Campaign type/template category | Birthday DOB scan | Yes | IMPLEMENTED. |
| BILLING | Campaign type/template category | Bill status/overdue scan; legacy payment event | Yes in both planes, separate ledgers | IMPLEMENTED/PARTIAL. |
| WEBINAR | Campaign type/template category | Registration/start/end scan | Yes in CarePilot | IMPLEMENTED in backend; UI maturity needs verification. |
| LEAD | Campaign type/template category | Lead nextFollowUpAt scan | LeadActivity marker only, not patient provider send | MODEL/OPS ONLY for delivery. |
| LAB/report-ready | No corresponding CarePilot trigger found in inspected code | Existing lab domain not connected to CarePilot execution in this audit | No evidence | NOT WIRED. |
| Pharmacy reorder | Refill category only | No dispense/reorder communication execution found | No evidence | NOT WIRED. |

## 8. Template resolution

There are two template abstractions:

1. **CarePilot execution template**: `CampaignTemplateEntity` (`carepilot_campaign_templates`) has tenant, name, `ChannelType`, subject, body, active. Campaigns point to a template ID. `CampaignExecutionService` resolves exactly by `execution.templateId`; if absent, it uses a generic fallback subject/body. VOICE uses the same body and ignores subject.
2. **Richer Care template subsystem**: `CareTemplateEntity`/`CareTemplateService` has `TemplateType`, `TemplateChannel` (including VOICE), `TemplateCategory`, subject, body, variables JSON, active/system-template metadata. Its defaults include Appointment, Refill, Vaccination, Webinar, Billing, Lead, and Wellness templates. It is used by the template administration/filtering API, but the CarePilot execution path inspected directly queries `CampaignTemplateRepository`, not `CareTemplateRepository`.

Consequences:

- The UI’s Type/Category options map naturally to the richer Care template model, but CarePilot execution’s campaign binding is a different table/model.
- VOICE can be represented in both enums (`TemplateChannel.VOICE` and `ChannelType.VOICE`), but a rich Care template is not automatically the template selected by a CarePilot execution.
- Multiple active rich templates are filtered by repository criteria; CarePilot campaign execution is deterministic by template ID, not by category/channel search.
- No evidence of a global “choose one active template by Type+Category+Channel” fallback in `CampaignExecutionService`.
- Subject is meaningful for Email; it is ignored by the Voice request builder.

## 9. Variable resolution

`CarePilotTemplateRenderer` and `CampaignExecutionService.buildTemplateValues` populate a source-aware map. Evidence-backed values include:

| Variable | Source | Null/fallback behavior |
|---|---|---|
| `patientName` | Patient first/last name | Rendered from available names; token can remain if not supplied by generic renderer. |
| `clinicName`, `clinicPhone` | Clinic profile | Optional; absent values become empty/unchanged according to renderer path. |
| `doctorName` | Appointment/provider or prescription/vaccination source | Source-dependent; not guaranteed for every campaign. |
| `appointmentDate`, `appointmentTime`, `appointmentDateTime` | Appointment source/reference date | Present only for appointment executions. |
| `followUpDate`, `followUpTime` | Follow-up/prescription source | Source-dependent. |
| `medicineName`, `prescriptionDate`, `refillDueDate` | Prescription service | Refill estimate is configuration/data-derived. |
| `vaccinationDueDate`, vaccine fields | Vaccination service | Vaccination-only. |
| `billDueDate`, bill fields | Billing service | Billing-only; exact amount/number population is source-dependent. |
| `birthdayDate`, `age` | Patient DOB | Birthday-only. |
| `campaignId`, `reschedulePhone` | Campaign/configuration | Optional. |

The UI examples `{{billAmount}}`, `{{webinarLink}}`, and `{{leadName}}` are supported by the richer template defaults/UI model, but this audit did not find equivalent population in the CarePilot execution value builder for every corresponding event. `appointmentId` and patient first-name-specific variables are not consistently exposed as first-class UI variables. Missing tokens are not a safe clinical decision; they render according to the existing renderer (often empty or unchanged token), so template validation should be treated as an operational gap.

## 10. Channel resolution and policy precedence

For CarePilot reminder materialization the observed precedence is:

1. Template channel (or campaign/template inference when no template is bound).
2. `TenantNotificationSettingsService.resolveEffectiveChannel` checks requested channel if enabled and provider-ready.
3. Tenant fallback channel, if usable.
4. Tenant default channel, if usable.
5. No effective channel → execution is not created.

Patient contact presence and the trigger’s `hasEmail`/address checks occur before creation in most typed paths. Quiet hours and policy settings can defer/suppress creation. The inspected settings model does not contain a VOICE preference, and `isChannelUsable` returns false for VOICE, so a requested VOICE channel cannot currently win this resolution.

The legacy notification-domain path has its own channel/additive delivery policy and recipient resolver; it should not be assumed to obey the CarePilot channel precedence.

## 11. Email / MSG91 trace

### Engage route

```text
CarePilot execution due
 -> CampaignExecutionService.toMessageRequest
 -> MessageOrchestratorService.send
 -> MessagingProviderRegistry.resolve(MessageChannel.EMAIL)
 -> registered MessageProvider
 -> SMTP/provider result
 -> CampaignDeliveryAttemptEntity + execution status
```

The Engage path is:

```text
CampaignExecutionService
 -> MessageOrchestratorService
 -> MessagingProviderRegistry.resolve(EMAIL)
 -> msg91-email-smtp
 -> Msg91EmailMessageProvider
 -> MSG91 SMTP
```

The runtime selector is `CLINIC_CAREPILOT_MESSAGING_EMAIL_PROVIDER=msg91-email-smtp`.
The previous value `smtp` did not match any registry provider and therefore
resolved to `carepilot-noop`. The selector is now corrected in the local runtime
environment and wired into the maintained UAT/production Compose templates.

`MSG91_EMAIL_FROM` is authoritative for MSG91 sends. The CarePilot
`from-address` value is kept aligned for operator visibility and legacy
compatibility; it is not used to override the MSG91 adapter.

## 12. Voice / DotVoice trace

Current path when a VOICE execution row exists and the global flag is enabled:

```text
CampaignExecutionService.processSingleExecution
 -> VOICE branch
 -> VoiceReminderSender / EngageVoiceReminderSender
 -> existing ReminderVoiceRenderer + audio normalizer
 -> DotVoice stream-ready assertion
 -> REST origination + stream_start correlation + μ-law playback
 -> normalized result
 -> CampaignDeliveryAttemptEntity / execution status
```

Current controls/gaps:

- `ENGAGE_VOICE_REMINDERS_ENABLED=false` by default and is checked inside execution, so disabled VOICE returns skipped/feature-disabled without a billable call.
- `ChannelType.VOICE` exists in backend and `TemplateChannel.VOICE` exists in the richer template model.
- `CarePilotCampaignTriggerService` can infer a template channel and create an execution in backend, but its address/readiness and settings path is not VOICE-complete.
- `TenantNotificationSettingsService` has no VOICE preference enum, no voice enabled field, and returns VOICE unusable.
- `CarePilotReminderTriggerService.providerReadiness()` checks Email/SMS/WhatsApp only.
- Campaign UI `CHANNEL_TYPES` is `EMAIL, SMS, WHATSAPP, IN_APP, APP_NOTIFICATION`; VOICE is omitted.
- `CarePilotChannelType` frontend union also omits VOICE, so campaign filters/forms cannot consistently express it.
- Reminders UI channel filter offers Email/SMS/WhatsApp only.
- Notification settings UI `CHANNEL_ORDER` is `IN_APP, EMAIL, SMS, WHATSAPP`; policy maps have no VOICE.
- Platform Communication Test health can show DotVoice, but that is provider certification, not tenant Engage enablement.

Therefore no automatic production VOICE reminder is currently reachable through the normal typed reminder materialization path without additional policy/UI work, and the global flag remains the final gate even if rows are manually created.

## 13. Notification Settings and matrix

Backend `TenantNotificationSettingsEntity`/record contains channel booleans for Email/SMS/WhatsApp/In-App, default/fallback `NotificationChannelPreference`, reminder-family switches, quiet hours, timezone, consent and rate/compliance policy. Defaults observed include Email/In-App enabled, SMS/WhatsApp disabled, default Email, fallback SMS, and consent required.

Frontend `notificationSettingsModel.ts` and settings page expose only In-App/Email/SMS/WhatsApp. The notification matrix covers appointment booking/24h/2h/cancellation/reschedule/no-show, billing, prescription/follow-up, and other policy rows with those channels. Voice is absent from both model and matrix.

The required future Voice work is therefore not just a dropdown: a VOICE preference/enablement, provider readiness, consent/quiet-hours treatment, matrix rows, validation, and persistence/API mapping would be needed. No such change is made in this audit.

## 14. Campaign audit

Campaigns use `CampaignType`, `TriggerType` (`MANUAL`, `SCHEDULED`, `EVENT_BASED`), audience, template ID, lifecycle (`DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `ACTIVE`, etc.), and maker-checker metadata. `CampaignService` owns draft/submit/review/approve/activate transitions. `CarePilotCampaignTriggerService` provides manual preview/trigger and creates execution rows for audience members with duplicate checks.

The backend enum accepts VOICE, but frontend campaign channel lists and provider readiness gating omit it. Generic scheduled/event-based campaign execution is not a separate scheduler discovered here; typed CarePilot reminder scanners are the concrete automatic source.

## 15. Reminders page and persistence states

`CarePilotRemindersController`/`CarePilotRemindersService` read and mutate `CampaignExecutionEntity` rows (retry, resend alias, cancel, suppress, reschedule). The page is an operational execution view, not an independent scheduler. It displays execution status/delivery status and filters, but its channel filter is Email/SMS/WhatsApp only.

Observed meanings:

- **UPCOMING/PENDING:** queued execution with future `scheduledAt` (presentation grouping, not a distinct entity state).
- **QUEUED:** due or future execution awaiting dispatcher.
- **PROCESSING:** claimed by dispatcher.
- **RETRYING:** `RETRY_SCHEDULED` with `nextAttemptAt`/scheduled time.
- **SENT/SUCCEEDED:** provider dispatch accepted/success according to normalized result.
- **DELIVERED/READ:** delivery-history/webhook status where provider/domain events update it; not guaranteed for every provider.
- **FAILED/DEAD_LETTER:** terminal failure or retries exhausted.
- **SKIPPED/SUPPRESSED/CANCELLED:** delivery status or execution status preventing dispatch; Voice disabled uses existing skipped semantics in the execution branch.

The separate legacy notification history page/state uses notification outbox/history and should not be conflated with these execution rows.

## 16. Retry, idempotency, and duplication

CarePilot protects materialization with distributed scheduler locks, source/window/channel duplicate queries, recipient/time-window duplicate queries, and database-integrity fallback. Execution dispatch uses attempt rows and configuration-driven retry (`carepilot.retry.max-retries` default 3, initial backoff 60s, max 900s; retryable statuses default `FAILED,PROVIDER_NOT_AVAILABLE,NOT_CONFIGURED`).

Concrete remaining gap: the provider request contract does not show a universal provider idempotency key for email or Voice. If a process crashes after provider submission but before saving the provider result, a retry can submit again because the execution row remains retryable while the provider may already have accepted the first request. Existing row-level dedupe prevents a second materialization row, not necessarily a second external send/call. This is an audit finding only; no redesign is proposed here.

## 17. Provider readiness vs tenant enablement

The effective gates are separate:

1. Global scheduler/dispatcher feature flags.
2. Tenant CarePilot feature flag and reminder-family setting.
3. Template/campaign active/approved binding.
4. Effective channel policy/default/fallback and provider readiness.
5. Patient contact and consent/quiet-hour policy.
6. Execution dispatcher and provider result.

For Voice specifically, `ENGAGE_VOICE_REMINDERS_ENABLED=false` is checked at execution and cannot be bypassed by a tenant setting, campaign, manual execution row, or retry. However, upstream Voice tenant/channel policy is currently missing, so the flag is not the only missing link.

## 18. Email / Voice gap matrix

| Area | Email | Voice | Status | Missing work |
|---|---|---|---|---|
| Provider transport | Existing MessageProvider/SMTP path | Certified DotVoice reminder path | Email COMPLETE; Voice COMPLETE transport | Engage selection/wiring for Voice policy. |
| Provider readiness | CarePilot messaging status | Platform/DotVoice readiness, not tenant policy | Email COMPLETE; Voice PARTIAL | Add tenant-facing Voice readiness mapping. |
| Tenant settings | Email enabled/default/fallback | No Voice preference/flag | Email COMPLETE; Voice MISSING | Extend settings/matrix deliberately. |
| Templates | CarePilot + richer Care templates | Enum support but two-model mismatch | PARTIAL both | Align campaign binding with intended template model. |
| Campaign selection | UI/backend | Backend enum only; UI omits | Email COMPLETE; Voice PARTIAL | Add Voice to validated UI only after policy. |
| Reminder filters | Present | Omitted | Email COMPLETE; Voice MISSING | Add Voice filter. |
| Notification matrix | Present | Omitted | Email COMPLETE; Voice MISSING | Add Voice rows/consent policy. |
| Scheduler integration | Typed scans | Upstream resolver rejects VOICE | Email COMPLETE; Voice MISSING | Permit Voice only under all gates. |
| Appointment trigger | H24/H2 CarePilot + legacy event path | No production Voice path | Email COMPLETE; Voice PARTIAL | First E2E candidate. |
| Refill trigger | Estimated prescription scan | Same limitation | PARTIAL | Pharmacy/dispensing truth not connected. |
| Follow-up trigger | Prescription/consultation variants | Same limitation | PARTIAL | Consolidate source semantics later. |
| Vaccination trigger | Due/overdue scan/event | Same limitation | PARTIAL | Policy/channel mapping. |
| Wellness trigger | Birthday scan | Same limitation | PARTIAL | Voice policy/template. |
| Consent | Tenant policy/recipient checks | No Voice-specific exposure found | Email PARTIAL/COMPLETE; Voice MISSING | Confirm phone/voice consent and quiet hours. |
| Retry | Execution attempts/backoff | Same engine; provider idempotency gap | COMPLETE/PARTIAL | External idempotency after unknown outcome. |
| Idempotency | Row/source-window guards | Same | PARTIAL | Provider request key. |
| Delivery history | Attempts/events/analytics | Normalized Voice result can enter same ledger | COMPLETE/PARTIAL | Ensure webhook/outcome mapping. |
| Analytics/Ops | Channel filters and reports | Backend enum, UI omissions | Email COMPLETE; Voice PARTIAL | Add Voice operational views. |
| Feature flag | Existing scheduler/provider flags | `ENGAGE_VOICE_REMINDERS_ENABLED=false` | Voice SAFE DISABLED | Keep false until certified production controls. |

## 19. Exact trigger timeline summary

| Mechanism | Actual timing/policy found |
|---|---|
| CarePilot appointment H24 | `appointmentAt - 24h`; materialized by ~15m reminder scan; late catch-up to now+1m within implemented grace. |
| CarePilot appointment H2 | `appointmentAt - 2h`; same scan and catch-up behavior. |
| Legacy appointment reminder | Removed; no scheduled appointment reminder scan remains in the legacy scheduler. |
| Legacy two-hour helper | `NotificationActionService` uses 2h target with 15m slack. |
| CarePilot missed appointment | Appointment time + campaign-note delay, default PT2H; eligible through configured horizon. |
| CarePilot follow-up | Follow-up date start-of-day minus configured offset, default 24h. |
| CarePilot refill | Finalized date + estimated refill days (default 30) minus offset (default 0). |
| CarePilot vaccination | Due/overdue date minus configured offset (default 1 day). |
| CarePilot birthday | Birthday minus configured days (default 0), at configured local send time (default 09:00 system zone). |
| CarePilot billing | Immediate/current scan once overdue threshold reached (default 3 days), optional frequency. |
| CarePilot webinar | H24/H1 before start, registration confirmation window, and post-event follow-up around scheduled end. |
| Lead | `nextFollowUpAt` creates a deterministic LeadActivity marker; no provider send. |
| Execution dispatch | Every ~2m due-row scan, subject to scheduler flag/lock/batch. |

## Legacy appointment reminder consolidation

The legacy scheduled appointment-reminder branch has been removed from
`NotificationReminderScheduler`. Its `AppointmentReminderEventService` caller
and the unused `NotificationActionService.queueAppointmentReminders` helper
were removed after reference searches confirmed there were no remaining
production callers. The appointment reminder event/listener types remain as
shared notification-domain infrastructure because they are also consumed by
notification-center and may be published by other module integrations.

Immediate appointment booked, rescheduled, and cancelled lifecycle
notifications remain unchanged on their existing event listeners. CarePilot is
now the authoritative owner for scheduled appointment H24/H2 materialization,
execution, retry, and delivery history. Missed-appointment patient follow-up
is now CarePilot-owned while no-show reconciliation and staff/operational
notifications remain in the notification domain. Consultation-sourced
follow-up, vaccination, billing, and other legacy reminder families remain
pending their own future consolidation phases.

## Missed Appointment / Follow-Up Consolidation

Missed appointments had three concerns: no-show reconciliation, immediate
no-show/staff notifications, and a legacy periodic patient reminder scan. The
scan inferred a missed appointment from an old `BOOKED`/`WAITING` appointment,
while CarePilot materializes patient follow-up from authoritative `NO_SHOW`
status with a campaign-configured delay. The legacy patient scan and its
`NotificationActionService` helpers were removed. Status reconciliation,
immediate no-show notification, and staff operational alerts remain.

Follow-up has two independently stored sources: `PrescriptionRecord.followUpDate`
and `ConsultationRecord.followUpDate`. CarePilot owns prescription follow-up
campaign executions and no longer emits a second `FollowUpDueEvent` for the
same prescription execution. The legacy consultation scan/event remains because
it can represent a consultation follow-up without a matching prescription.
Full follow-up consolidation remains pending a product decision on whether
those clinical fields are one concept or two.

## Vaccination Reminder Consolidation

Vaccination has one automatic legacy path and one CarePilot path. The legacy
`NotificationReminderScheduler` called `NotificationActionService` to scan
`VaccinationService.listDue()` and publish `VaccinationDueEvent`, which created
notification-domain patient delivery rows. CarePilot independently scans
`listDue()` and `listOverdue()`, applies the campaign `reminderOffset`,
`includeOverdue`, and `overdueGraceDays` settings, then creates a
`CampaignExecutionEntity` with source type `VACCINATION` and a deterministic
window/duplicate guard.

The legacy automatic scheduler dispatch was removed. CarePilot is now the
authoritative owner for scheduled due and overdue vaccination patient
reminders. Vaccination clinical records, due/overdue calculation, and the
notification-domain event/listener infrastructure remain intact.

The vaccination page's explicit **Queue reminders** action is a manual
operator workflow through `VaccinationReminderService`. It publishes a
`VaccinationDueEvent` for the selected patient's due records and remains
`KEEP_MANUAL`; it is not an automatic scheduler and was not silently changed
to a campaign execution. This preserves the existing operator-visible
notification history and manual resend semantics. A future batch can decide
whether manual sends should become Engage executions, but that requires an
explicit UI/status compatibility plan.

No distinct vaccination staff-alert path was removed. The existing
notification-center listener remains available for vaccination due events.
The remaining ambiguity is whether manual resend history should eventually
share the CarePilot execution ledger; it is intentionally outside this
consolidation.

## Billing / Payment Reminder Consolidation

Billing has separate transactional and scheduled paths. Bill/invoice delivery,
receipt delivery, payment collection, and payment-state transitions remain
notification/billing operations. They are not scheduled payment reminders and
were preserved.

The duplicate scheduled path was:

`NotificationReminderScheduler` → `NotificationActionService.queuePaymentReminders`
→ scan bills with positive due amounts → `PaymentReminderEvent` →
notification-domain/outbox.

CarePilot already scans the same bill records and creates the authoritative
`CampaignExecutionEntity` for `BILLING_REMINDER`. Its default behavior is:

- eligible statuses: `UNPAID`, `ISSUED`, `PARTIALLY_PAID`;
- positive `dueAmount` required;
- `billDate` is the available schedule reference (there is no separate bill
  due-date field in this path);
- default overdue threshold: 3 days via
  `clinic.carepilot.reminders.billing-overdue-days`;
- optional campaign `overdueDays`, `reminderFrequencyDays`, and
  `targetStatuses` override the defaults;
- frequency `0` means one execution after the threshold;
- paid, cancelled, and refunded bills are excluded;
- execution-window and channel duplicate guards remain in Engage.

The legacy automatic payment-reminder scheduler branch and its action-service
helpers were removed. Payment reminder events, listeners, stale-state checks,
notification history, and notification-center infrastructure remain for
compatibility with other event publishers and existing operational views.

The billing UI exposes manual invoice-email and receipt-email actions, not a
manual scheduled-payment-reminder action. Those immediate transactional
operations remain unchanged. No separate manual payment-reminder migration was
needed.

There is no distinct staff collection-alert scheduler in the removed branch;
existing payment event/notification-center consumers remain intact. The
remaining ambiguity is whether future collection outreach should be modeled as
a separate operational campaign rather than a payment reminder.

## 20. Recommended smallest next implementation batch (not implemented)

1. **Choose appointment H24 Email first.** It already has the most complete trigger, offset, template values, duplicate guards, and execution history.
2. Make Engage’s Email provider selection explicit and configuration-driven so the certified MSG91 adapter is an allowed/selected registry provider without changing Platform Communication Test or legacy provider behavior.
3. Add a single, narrow production Voice policy slice only after Email: tenant Voice enablement/readiness, phone consent/quiet-hour policy, Voice template binding, frontend campaign/reminder/settings visibility, and an execution-level proof that the global flag remains false by default.
4. Reuse `CampaignExecutionService` and the certified Voice sender; do not add a scheduler, ledger, retry framework, or provider copy.
5. Add end-to-end tests around one appointment H24 execution, then a disabled Voice execution proving zero DotVoice calls.

## 21. Explicit “do not build” list

- Do not create a second Engage/CRM or campaign engine.
- Do not create a second reminder scheduler or outbox/ledger.
- Do not duplicate `MessageProvider`, `VoiceCallProvider`, DotVoice media, or TTS implementations.
- Do not route around `CampaignExecutionService`/`MessageOrchestratorService`.
- Do not create a second template model to represent Email and Voice variants.
- Do not bypass tenant settings, consent, quiet hours, maker-checker, or provider readiness.
- Do not connect lab/pharmacy/lead categories merely because the UI exposes them; add only after an authoritative trigger exists.
- Do not enable `ENGAGE_VOICE_REMINDERS_ENABLED` as part of this audit.
- Do not change AIVA, STT, VAD, booking, Care entitlement, Provider/Discover, or certified DotVoice transport.
## Refill reminder and patient action (current implementation)

`CarePilotReminderTriggerService` remains the only refill reminder materializer;
it uses finalized prescription dates and medicine-duration data when present,
otherwise the configured estimated refill duration. No pharmacy dispense ledger
currently exposes refill eligibility to this scheduler, so the result remains an
explicit estimate rather than a clinical exhaustion claim.

Patient-facing refill requests are now recorded by the inventory-owned
`RefillRequestService` as `REQUESTED` intake records. The authenticated Care
portal accepts a prescription number, verifies the logged-in tenant/patient and
finalized prescription, and applies a unique tenant + patient + prescription +
cycle guard. It does not create a pharmacy sale or dispense. The refill campaign
renderer exposes `refillActionUrl` (default `/patient/refills`) without putting
patient or prescription identifiers in the URL. Voice remains globally disabled.
