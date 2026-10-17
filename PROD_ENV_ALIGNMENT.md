# Jeevanam Production Environment Alignment

This report compares the effective configuration sources without printing
secret values.

## 1. Executive Summary

The production environment already has the important topology and security
differences from local: external browser URLs, internal Docker service URLs,
loopback-only published ports, production Keycloak mode, restricted CORS, and
development OTP disabled.

Production did not explicitly state the scheduler and outbound voice safety
decisions. Those settings were made explicit without enabling unfinished
functionality. The production Piper default was also corrected from an English
voice to the configured Hindi voice because the application defaults to Hindi
and the provider resolver prioritizes `PIPER_DEFAULT_VOICE` for Hindi.

Engage/MSG91 email credentials and provider enablement are not present in the
production env file. They remain disabled/owner-controlled rather than being
invented or enabled here.

## 2. Configuration Sources

LOCAL:

`local/docker-compose.yml + local/.env.full-docker`

PRODUCTION:

`local/docker-compose.prod.yml + local/.env.prod-jeevanam`

Secret values were inspected only for presence/blankness and were never
printed.

## 3. Effective Configuration Summary

The counts below are reviewed findings, not a claim that every Compose name
has identical business meaning. A finding can have more than one applicable
tag (for example, a provider setting can also be security-sensitive).

| Classification | Count | Summary |
|---|---:|---|
| ALIGNED | 15 | Core database, Redis, MinIO networking, portal modes, production URLs, and application defaults. |
| INTENTIONAL_PROD_DIFFERENCE | 10 | Loopback bindings, Keycloak mode, provider chains, production cadence, and deployment topology. |
| LOCAL_DEV_ONLY | 12 | Host ports, voice test mode, local map defaults, local hostname defaults, and mock/test controls. |
| PROD_ONLY_VALID | 7 | Production storage, notification, Redis host, Keycloak admin, and portal settings. |
| ENV_FILE_OWNED | 19 | External URLs, credentials, deployment metadata, and provider credentials supplied through the production env file. |
| MISSING_PROD | 0 confirmed | No confirmed missing setting required after the safe explicit gates were added. Provider credentials remain conditional, not silently assumed. |
| PROVIDER_CONDITIONAL | 18 | Sarvam, Deepgram, ElevenLabs, Piper, Faster Whisper, MSG91, and DotVoice provider settings. |
| SECURITY_DIFFERENCE | 18 | Secrets, CORS, Keycloak, OTP, network exposure, and voice safety gates. |
| OBSOLETE_CANDIDATE | 3 | Legacy appointment-reminder enablement/hours/grace variables have no active consumer. |
| MANUAL_DECISION | 7 | Production Engage email rollout, approved voice providers, scheduler rollout, map defaults, proxy mode, and optional tuning. |
| PRODUCTION_DEFECT | 1 fixed | Piper Hindi selection could resolve the English default voice. |

## 4. Production Changes Made

| Variable | File changed | Reason / consumer | Previous behavior | New behavior | Risk | Restart/rebuild |
|---|---|---|---|---|---|---|
| `CLINIC_CAREPILOT_SCHEDULER_ENABLED` | `.env.prod-jeevanam` | `CarePilotSchedulerService` execution scheduler | Application default was relied upon | Explicit `false` | Prevents accidental execution rollout | API restart/recreate |
| `CLINIC_CAREPILOT_REMINDERS_ENABLED` | `.env.prod-jeevanam` | `CarePilotReminderScheduler` materialization scheduler | Application default was relied upon | Explicit `false` | Prevents accidental reminder materialization | API restart/recreate |
| `CLINIC_NOTIFICATIONS_SCHEDULER_ENABLED` | `.env.prod-jeevanam` | `NotificationReminderScheduler` active follow-up family processing | Compose default `true` | Explicit `true` | No behavior change; makes ownership visible | API restart/recreate |
| `CLINIC_NOTIFICATIONS_SCHEDULER_FIXED_DELAY` | `.env.prod-jeevanam` | `NotificationReminderScheduler` | Production Compose default `PT5M` | Explicit `PT5M` | No behavior change | API restart/recreate |
| `ENGAGE_VOICE_REMINDERS_ENABLED` | `.env.prod-jeevanam` | Global Engage voice execution gate | Application default was relied upon | Explicit `false` | Prevents billable Engage calls | API restart/recreate |
| `DOTVOICE_ENABLED` | `.env.prod-jeevanam` | DotVoice provider readiness/origination | Compose default was relied upon | Explicit `false` | Prevents provider origination | API restart/recreate |
| `DOTVOICE_STREAM_ENABLED` | `.env.prod-jeevanam` | DotVoice media stream transport | Compose default was relied upon | Explicit `false` | Prevents stream connection/call media | API restart/recreate |
| `PIPER_DEFAULT_VOICE` | `.env.prod-jeevanam` | `PiperTextToSpeechProvider` Hindi resolution | `en_US-lessac-medium` was selected first even for Hindi | `hi_IN-rohan-medium` | Aligns default with application `hi-IN`; English remains explicit through `PIPER_ENGLISH_VOICE` | API and Piper restart/recreate |

No secrets, credentials, or local files were copied. No Compose-file change
was necessary: the API already receives the production env file and Compose
already passes the DotVoice settings with safe defaults.

## 5. Intentional Differences Preserved

- PostgreSQL, Redis, MinIO, Whisper, and API are not publicly exposed.
- Production web/API/gateway bindings are loopback-only.
- Keycloak remains `start --import-realm`, never `start-dev`.
- Production browser URLs and JWT issuer/JWK URLs remain external/internal as
  appropriate; localhost values were not copied.
- Production CORS remains limited to deployed Jeevanam origins.
- Production has `VOICE_TEST_ENABLED=false`.
- Production AI mock mode remains disabled.
- Production does not receive local development passwords/default secrets.
- Production voice provider chains remain separate from local certification
  chains and are not enabled by this change.

## 6. Voice Production Configuration

| Area | Effective production behavior | Classification |
|---|---|---|
| Engage voice gate | `ENGAGE_VOICE_REMINDERS_ENABLED=false` | SECURITY_DIFFERENCE |
| DotVoice origination | `DOTVOICE_ENABLED=false` | SECURITY_DIFFERENCE |
| DotVoice media stream | `DOTVOICE_STREAM_ENABLED=false` | SECURITY_DIFFERENCE |
| STT order | `faster-whisper,mock` | PROVIDER_CONDITIONAL; no live Engage voice rollout |
| TTS order | `piper,mock` | PROVIDER_CONDITIONAL |
| Voice LLM order | `gemini,groq` | PROVIDER_CONDITIONAL; mock is excluded in production |
| Faster Whisper URL | `http://faster-whisper:8000` | Correct internal Docker routing |
| Piper URL | `http://piper-tts:8001` | Correct internal Docker routing |
| Voice orchestration URL | `ws://clinic-management-api:8089/...` for patient portal upstream and internal API health routing | Correct internal Docker routing |
| CarePilot primary/fallback | Application defaults to `dotvoice` / `none`, failover disabled; global gate remains off | PROVIDER_CONDITIONAL |

Piper was reviewed in source. For Hindi, the provider first checks
`PIPER_DEFAULT_VOICE`, then fallback, then English. Production now explicitly
selects `hi_IN-rohan-medium`; English remains `en_US-lessac-medium` via the
explicit English voice setting. No live call was made.

## 7. Engage Scheduler Configuration

| Scheduler | Effective production state | Ownership / overlap |
|---|---|---|
| CarePilot reminder materialization | Disabled explicitly (`CLINIC_CAREPILOT_REMINDERS_ENABLED=false`) | Would create due reminder executions; not enabled here. |
| CarePilot execution processor | Disabled explicitly (`CLINIC_CAREPILOT_SCHEDULER_ENABLED=false`) | Would process due CampaignExecution rows; not enabled here. |
| Notification scheduler | Enabled every `PT5M` | Current `NotificationReminderScheduler` queues follow-up reminders only. Its appointment reminder path was removed by prior consolidation. |
| Legacy appointment scheduler | Not active in the inspected implementation | Appointment scheduled reminders are owned by Engage when that rollout is enabled. |
| Potential duplicate scheduler | No current appointment duplicate found | CarePilot remains disabled; notification scheduler does not queue appointment reminders. |

The local effective notification delay is `PT30S` from local Compose, while
production is intentionally `PT5M`. This is a cadence difference, not a
configuration defect.

## 8. AI Configuration

Production and local both enable the primary AI path and select Gemini first.
Production uses `GEMINI,GROQ` and local uses `GEMINI,GROQ,SARVAM`; this is an
intentional provider-approval difference. Production has no AI mock enablement.

The AI retry/timeout values are already represented by application defaults
(three attempts, 60-second request/Gemini timeout, 60-second retry backoff),
so no redundant production overrides were added.

## 9. Portal/Auth/Security Configuration

- Patient and provider portal auth mode is `ACCESS_APPROVAL`.
- Development OTP exposure is false for both portals.
- Session TTL and session secret remain externally supplied; secret values were
  not printed.
- JWT issuer and browser Keycloak URLs are deployed HTTPS URLs.
- Internal JWK and Keycloak admin URLs use the Docker `keycloak` service.
- Keycloak remains strict HTTPS with production startup mode.

## 10. Frontend URL Configuration

| Surface | Status |
|---|---|
| Admin | Correctly configured with deployed API/Keycloak URLs through production env values. |
| Care | Correctly configured with deployed Care/API URLs. |
| Discover | Correctly configured with deployed Discover URL; map/geocoding use application defaults unless explicitly supplied. |
| AIVA | Correctly configured with deployed AIVA and Care AI URLs. |
| Provider | Correctly configured; `VITE_PROVIDER_APP_URL` is externally overridden, so the localhost Compose fallback is not effective. |
| Keycloak | Correctly configured with deployed HTTPS URL. |
| API | Correctly configured with the deployed API URL and internal service URLs. |

No production localhost browser URL defect remains in the effective env file.

## 11. Storage/Database/Redis Configuration

- Spring datasource uses the internal `postgres:5432` service; no host port is
  required.
- Redis uses the internal `redis` service and internal port `6379`; no public
  Redis port is published.
- MinIO uses `http://minio:9000`, production bucket settings, and no published
  MinIO ports.
- PostgreSQL credentials and MinIO credentials remain external/secret and are
  not printed.

## 12. Obsolete Candidates

The following legacy appointment-reminder variables are still present only in
local Compose wiring and have no active source consumer in the inspected
repository:

- `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_ENABLED`
- `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_GRACE_MINUTES`
- `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_HOURS_BEFORE`

They were not added to production and were not deleted from local configuration.
Removal should be handled as a separate cleanup decision.

## 13. Remaining Manual Decisions

1. Whether to enable the CarePilot reminder and execution schedulers in
   production after a controlled Engage email rollout.
2. Whether production Engage email should be enabled and which MSG91/general
   mail credentials should be injected; no provider credentials were invented.
3. Whether DotVoice/Engage voice may ever be enabled after live approval.
4. Populate/approve the separate Sarvam speech credential before enabling
   Sarvam STT/TTS; Deepgram/ElevenLabs remain separate decisions.
5. Whether Discover map/geocoding defaults need explicit production values.
6. Whether `KC_PROXY` is needed by the deployed reverse-proxy/Keycloak setup.
7. Whether production AI/OCR timeout overrides are operationally required.

## 14. Validation Results

- Local Compose validation: passed with `docker compose ... config --quiet`.
- Production Compose validation: passed with `docker compose ... config --quiet`.
- Effective env names and presence were checked without printing secret values.
- Application bindings were checked for scheduler, voice, Piper, AI, portal,
  storage, Redis, JWT, and CORS names.
- Production localhost URL defects: none found in effective production env.
- Secrets exposed: none.
- Security regressions: none.
- Source-code business logic changed: none.

## Final production safety checks

- PostgreSQL public exposure: no.
- Redis public exposure: no.
- MinIO public exposure: no.
- Whisper public exposure: no.
- API exposure: loopback-only published port behind the intended proxy.
- Keycloak production mode: yes.
- Development OTP: disabled.
- AI mock mode: disabled.
- Voice test mode: disabled.
- Hardcoded secrets: none added.
- Browser-facing production localhost URLs: none effective.
- CORS: restricted to deployed origins.
- Patient session secret: external.
- Engage Voice: globally disabled.
- DotVoice: disabled.

# Voice and Email Production Enablement

## DEV Working State

| Capability | Effective DEV state | Actual path |
|---|---|---|
| General email | Enabled, SMTP-configured | `clinic.mail.*` general mail stack |
| Engage email | Enabled, selected provider `msg91-email-smtp` | `MessageOrchestratorService` → `MessagingProviderRegistry` → MSG91 |
| Platform voice test | DotVoice configured and ready | `CommunicationTestService` → DotVoice REST/media-stream path |
| Engage reminder voice | Globally disabled | `ENGAGE_VOICE_REMINDERS_ENABLED=false` |
| CarePilot reminder materialization | Not enabled by the DEV env source | `CLINIC_CAREPILOT_REMINDERS_ENABLED` uses the application default `false` |
| CarePilot execution scheduler | Enabled | `CLINIC_CAREPILOT_SCHEDULER_ENABLED=true` |
| TTS used by reminder playback | Piper is available in the provider chain | Internal `piper-tts` service |
| Interactive STT/LLM | DEV chains include Sarvam/Faster Whisper and Gemini/Groq | Realtime voice runtime; separate from reminder playback |

The DEV source therefore proves provider/test capability, but does not prove
that automatic Engage reminder calls are enabled. The reminder voice hard gate
is false in DEV.

## Production Previous State

Production had all outbound Engage/CarePilot and DotVoice safety gates set to
false. General mail was disabled. The production env file did not contain
MSG91 or DotVoice credentials, sender/phone identifiers, or a production
provider selection.

## Production New State

The safe production state remains disabled until deployment-owned secrets and
provider identifiers are supplied. Existing production topology is retained:
internal Faster Whisper/Piper service URLs, loopback-only infrastructure
ports, production Keycloak, restricted CORS, and external secret injection.

No provider was enabled without a complete credential set. This avoids a
configuration that appears enabled but cannot pass readiness or would cause
the schedulers to repeatedly create failed delivery attempts.

## Variables changed

No new enablement variables were changed in this pass. The previously applied
explicit safety values remain:

| Variable | Effective production state | Reason |
|---|---|---|
| `CLINIC_CAREPILOT_SCHEDULER_ENABLED` | `false` | Do not process Engage executions before provider rollout is complete. |
| `CLINIC_CAREPILOT_REMINDERS_ENABLED` | `false` | Do not materialize automatic reminders before rollout is complete. |
| `ENGAGE_VOICE_REMINDERS_ENABLED` | `false` | Hard safety gate; no outbound reminder calls. |
| `DOTVOICE_ENABLED` | `false` | No DotVoice credentials/from-number/trunk are present in the production source. |
| `DOTVOICE_STREAM_ENABLED` | `false` | No approved production stream/callback deployment evidence is present. |

## Production blockers

The following deployment-owned values are absent from
`.env.prod-jeevanam` and cannot be invented safely:

- MSG91 username/password and approved sender configuration.
- General SMTP username/password and approved sender configuration.
- DotVoice API key, originating number, trunk, and approved production
  callback/stream routing.

The existing Compose file already passes these variables through, so the
blocker is secret/provider provisioning, not application wiring. Once those
values are supplied, the rollout should explicitly set the provider flags and
CarePilot scheduler gates together during a controlled deployment window.

## Duplicate-delivery assessment

No automatic duplicate delivery is enabled in the current production state.
The CarePilot materializer and execution scheduler are disabled. The enabled
notification scheduler handles the remaining follow-up family and does not
own the consolidated appointment reminder path.

## Multi-instance assessment

CarePilot schedulers use the existing distributed scheduler locks, and campaign
execution keeps its existing execution/attempt ledger. Because the production
CarePilot gates are currently disabled, no outbound multi-instance delivery is
currently occurring. Reassess lock/claim behavior as part of the controlled
enablement rollout.

## Security validation

- No credentials, API keys, phone numbers, or tokens were added or printed.
- PostgreSQL, Redis, MinIO, Whisper, and API exposure remain production-safe.
- Keycloak remains in production mode.
- Development OTP and voice test modes remain disabled.
- No localhost provider-facing URL was introduced.

# Provider Structure Completion

## Sarvam LLM trace

Sarvam LLM is an implemented semantic provider, separate from Sarvam STT and
Sarvam TTS:

`SarvamLlmClient` → `SarvamAiProviderAdapter` → `AiProviderRouterImpl`.

The client is created only when `clinic.ai.enabled=true`,
`clinic.ai.sarvam.enabled=true`, and an API key is nonblank. The adapter is
available to AIVA v2's `SARVAM,GEMINI,GROQ` policy and to the general provider
chain when `SARVAM` is present. Clinical tasks intentionally use the separate
`GEMINI,GROQ` policy.

DEV's general chain is `GEMINI,GROQ,SARVAM`; DEV's voice LLM chain is
`gemini,groq,mock`. Sarvam is therefore part of general/AIVA routing, not
the automated reminder TTS path and not the current voice LLM chain.

Production now declares the same general chain and the complete Sarvam LLM
structure. The owner-populated LLM key is present, so
`SARVAM_LLM_ENABLED=true` is effective. Sarvam speech remains disabled until
the separate speech credential is populated.

## Provider structure added

The production env file now contains:

- General SMTP structure: provider, port, STARTTLS, auth, and sender-name
  fields. Existing blank host/username/password/from-address values were not
  changed.
- Engage MSG91 structure: explicit provider ID, disabled enablement, and
  placeholders only for previously absent provider credentials/sender fields.
- DotVoice structure: provider selection, external API base URL, call path,
  timing, and placeholders for absent account/routing values. Existing
  `DOTVOICE_ENABLED=false` and `DOTVOICE_STREAM_ENABLED=false` were
  preserved.
- Sarvam LLM structure: base URL, model, timeout, enabled LLM path, and the
  owner-populated LLM credential.
- Sarvam speech structure: base URL, language, speaker, disabled STT/TTS gates,
  and a placeholder for the separately required speech credential.
- Faster Whisper and Piper timeout/model settings using internal service
  addresses.

Production Compose already passes the Sarvam LLM variables through the API
environment and already passes the email/DotVoice variables. No secret values
were moved or rewritten.

## Secret preservation

Existing secrets modified: **NONE**.

Existing blank production secret keys remain blank. Newly absent secret keys
use the documented `__SET_PRODUCTION_SECRET__` placeholder only. See
`PROD_PROVIDER_VALUES_TO_SET.md`.

## Enablement after manual values are populated

Do not enable any provider while its placeholder or required blank value
remains. After values are populated and readiness is verified:

- General email: set `CLINIC_MAIL_ENABLED=true`.
- Engage MSG91 email: set `CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED=true`
  and `MSG91_EMAIL_ENABLED=true`.
- DotVoice Platform Communication Test: set `DOTVOICE_ENABLED=true`; set
  `DOTVOICE_STREAM_ENABLED=true` only after the approved stream URL and
  gateway/provider routing are verified.
- Sarvam LLM: set `SARVAM_LLM_ENABLED=true`.

`ENGAGE_VOICE_REMINDERS_ENABLED`,
`CLINIC_CAREPILOT_REMINDERS_ENABLED`, and
`CLINIC_CAREPILOT_SCHEDULER_ENABLED` remain false.

## Missing-secret behavior

Email and DotVoice remain disabled, so their placeholders cannot trigger
provider calls. Sarvam STT/TTS remain disabled, so the speech placeholder
cannot be used in a request. The LLM client is active with its owner-provided
credential; Sarvam timeout and transport failures are classified as retryable,
while authentication failures are fatal and are handled by the existing
provider orchestration fallback.

# Sarvam / AIVA Production Configuration

## LLM

- `SARVAM_LLM_ENABLED=true` is now effective because the owner-populated LLM
  credential is present.
- Base URL: `https://api.sarvam.ai`
- Model: `sarvam-105b-conversations`
- Timeout: `60` seconds.
- General chain: `GEMINI,GROQ,SARVAM`.
- AIVA v2 retains its separate `SARVAM,GEMINI,GROQ` policy.
- Voice LLM remains `gemini,groq`; Sarvam was not added to that chain.

`SarvamLlmClient` is conditionally created from the LLM-specific enablement and
credential. It is separate from the speech providers.

## STT and TTS

The production source now declares the shared Sarvam speech configuration:

- `SARVAM_BASE_URL=https://api.sarvam.ai`
- `SARVAM_DEFAULT_LANGUAGE=hi-IN`
- `SARVAM_TTS_SPEAKER=anushka`
- `SARVAM_API_KEY` is a missing-secret placeholder and was not enabled.

Because the shared speech credential is not yet configured, both
`SARVAM_STT_ENABLED` and `SARVAM_TTS_ENABLED` remain `false`. Provider orders
are nevertheless production-safe:

- STT: `sarvam,faster-whisper`
- TTS: `sarvam,piper`

Mock providers were removed from production orders because the mock STT
provider is always ready and returns a synthetic transcript. The mock TTS
provider produces no playable audio. Real providers are therefore the only
production fallback path.

Once the speech credential is populated, set both speech enablement flags to
`true`; Sarvam will be attempted first and Faster Whisper/Piper will remain
fallbacks.

## Language and voice defaults

- AIVA default language: `hi-IN`
- AIVA response language: `hi-IN`
- AIVA default voice: `anushka`
- Sarvam TTS speaker: `anushka`
- Piper default voice remains `hi_IN-rohan-medium`

The Sarvam speaker is passed by `SarvamTextToSpeechProvider`; Piper uses its
own model voice and does not receive `anushka`.

## Debug console

`VITE_AI_DEBUG_CONSOLE=false` is explicitly set for production. DEV enables
this console, but the production decision avoids exposing prompts, model
responses, routing, medical context, and internal diagnostics in a healthcare
browser build.

## Startup and fallback behavior

Sarvam STT/TTS clients are Spring components that initialize their HTTP
clients without making provider calls at startup. Readiness is checked at
invocation. With the speech flags false, the placeholder cannot be used.
Sarvam LLM bean creation is conditional on its enabled flag and populated
credential; the already-configured LLM can therefore be used now, while
speech remains safely deferred.
