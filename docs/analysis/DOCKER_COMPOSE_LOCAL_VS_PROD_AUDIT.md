# Docker Compose Local vs Production Audit

Read-only comparison of `local/docker-compose.yml` and
`local/docker-compose.prod.yml` on 2026-10-03. Values from `.env` files were
not read or printed. Passwords, API keys, tokens, and secret-like defaults are
redacted below.

## 1. Executive summary

Both files define the same 13 services; no service exists only in one file.
Production is intentionally more locked down: core ports are not published or
bind only to loopback, Keycloak runs in production mode, the API uses
`.env.prod-jeevanam`, and production build metadata is explicit.

Compose names include explicit `environment:` keys and interpolation
references. Local has 225 names, production 172, with 65 local-only, 12
production-only, and 40 differing expressions/defaults. A local-only name is
not proof of a production defect because the production API also loads
`.env.prod-jeevanam` and Spring defaults may supply optional settings.

The highest-review items are scheduler/Engage flags, voice provider ordering,
voice/STT/TTS tuning, local-only map configuration, and production security
defaults. No Compose, env, or application source was changed.

## 2. Service-level differences

| Service | Meaningful local → production difference |
|---|---|
| `postgres` | Local publishes `${POSTGRES_PORT:-5437}:5432`; production does not publish a host port. Local password has a development fallback (redacted); production requires the variable. |
| `redis` | Local publishes `${REDIS_PORT:-6383}:6379`; production does not publish a host port. Production API explicitly supplies `REDIS_HOST`/`REDIS_PORT`. |
| `minio` | Local publishes API and console ports; production publishes neither. Local root password has a development fallback (redacted); production requires it. |
| `keycloak` | Local uses `start-dev`, localhost hostname defaults, `KC_PROXY`, and a public development port. Production uses `start`, production URL settings, `KC_HTTP_ENABLED`, proxy headers, strict HTTPS, and loopback-only binding. |
| `whisper-stt` | Local publishes `${WHISPER_STT_PORT:-8092}`; production does not. Image, model download command, volume, and healthcheck align. |
| `faster-whisper` | Build, environment, healthcheck, and model volume align. |
| `piper-tts` | Build, environment, healthcheck, and model volume align; effective API voice defaults differ. |
| `realtime-voice-gateway` | Local publishes on all interfaces; production binds to `127.0.0.1`. Build and healthcheck align. |
| `clinic-management-api` | Local has no `env_file`, publishes publicly, and supplies more development/runtime settings. Production loads `.env.prod-jeevanam`, binds loopback, and adds explicit Redis/MinIO, metadata, notification, portal, and production voice endpoint settings. |
| `web-admin` | Production requires `VITE_API_BASE_URL`, uses production URL defaults, and loopback-only binding. |
| `web-care` | Production requires API URL values where local permits empty defaults and binds loopback. |
| `web-discover` | Production omits local map/geocoding build arguments and requires selected public URLs; binding is loopback-only. |
| `web-aiva` | Production requires `VITE_CAREAI_RUNTIME_URL`; local has a localhost fallback; binding is loopback-only. |

Across common services, `volumes`, `depends_on`, `healthcheck`, `restart`, and
`networks` are otherwise aligned.

## 3. Environment variable counts and method

Counts are names in Compose, not values in any `.env` file.

| Measure | Count |
|---|---:|
| Names in `docker-compose.yml` | 225 |
| Names in `docker-compose.prod.yml` | 172 |
| In both | 160 |
| Local Compose only | 65 |
| Production Compose only | 12 |
| Differing Compose expressions/defaults | 40 |

## 4. Variables present in both

The following exhaustive list is present in both files. Service placement is
covered by the service table above.

| Group | Variables |
|---|---|
| AI/AIVA | `AIVA_RESPONSE_COMPOSER_ENABLED`, `AIVA_RESPONSE_COMPOSER_TIMEOUT_SECONDS`, `AIVA_RESPONSE_COMPOSER_VOICE_ONLY`, `AIVA_V2_ENABLED`, `AI_MOCK_ALLOW_RUNTIME`, `AI_MOCK_ENABLED`, `AI_PROVIDER_CHAIN`, `CLINIC_AI_ENABLED`, `CLINIC_AI_PROVIDER`, `CLINIC_GEMINI_ENABLED`, `CLINIC_GROQ_ENABLED`, `GEMINI_API_KEY`, `GEMINI_BASE_URL`, `GEMINI_MODEL`, `GROQ_API_KEY`, `GROQ_BASE_URL`, `GROQ_MODEL` |
| Engage/email/voice | `CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED`, `CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS`, `CLINIC_CAREPILOT_MESSAGING_EMAIL_PROVIDER`, `DOTVOICE_API_KEY`, `DOTVOICE_BASE_URL`, `DOTVOICE_CALLBACK_BASE_URL`, `DOTVOICE_CALLS_PATH`, `DOTVOICE_ENABLED`, `DOTVOICE_FROM_NUMBER`, `DOTVOICE_MEDIA_FRAME_DURATION_MS`, `DOTVOICE_STREAM_ENABLED`, `DOTVOICE_STREAM_TIMEOUT_MS`, `DOTVOICE_STREAM_URL`, `DOTVOICE_TIMEOUT_MS`, `DOTVOICE_TRUNK`, `MSG91_EMAIL_ENABLED`, `MSG91_EMAIL_FROM`, `MSG91_EMAIL_PASSWORD`, `MSG91_EMAIL_USERNAME` |
| Email/general notifications | `CLINIC_MAIL_AUTH`, `CLINIC_MAIL_ENABLED`, `CLINIC_MAIL_FROM_EMAIL`, `CLINIC_MAIL_FROM_NAME`, `CLINIC_MAIL_HOST`, `CLINIC_MAIL_PASSWORD`, `CLINIC_MAIL_PORT`, `CLINIC_MAIL_PROVIDER`, `CLINIC_MAIL_STARTTLS`, `CLINIC_MAIL_USERNAME` |
| Data/security | `CLINIC_CORS_ALLOWED_ORIGINS`, `CLINIC_JWT_ISSUER_URI`, `CLINIC_JWT_JWK_SET_URI`, `CLINIC_KEYCLOAK_ADMIN_PASSWORD`, `CLINIC_KEYCLOAK_SERVER_URL`, `CLINIC_MINIO_ACCESS_KEY`, `CLINIC_MINIO_SECRET_KEY`, `CLINIC_PATIENT_PORTAL_AUTH_MODE`, `CLINIC_PATIENT_PORTAL_SESSION_SECRET`, `CLINIC_PROVIDER_PORTAL_AUTH_MODE`, `CLINIC_PROVIDER_PORTAL_EXPOSE_DEV_OTP`, `CONTAINER_NAME_PREFIX`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_PORT`, `MINIO_ROOT_PASSWORD`, `MINIO_ROOT_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD`, `POSTGRES_USER`, `REDIS_PORT`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_PROFILES_ACTIVE` |
| OCR/STT/TTS | `CLINIC_OCR_ENABLED`, `CLINIC_OCR_PROVIDER`, `FASTER_WHISPER_COMPUTE_TYPE`, `FASTER_WHISPER_DEVICE`, `FASTER_WHISPER_LANGUAGE`, `FASTER_WHISPER_MODEL`, `PIPER_ALLOW_FALLBACK_VOICE`, `PIPER_CLI`, `PIPER_DEFAULT_VOICE`, `PIPER_ENGLISH_VOICE`, `PIPER_HINDI_VOICE`, `PIPER_MODEL`, `PIPER_MODEL_DIR`, `PIPER_MODEL_HOST_DIR`, `PIPER_VOICE`, `PIPER_VOICE_DOWNLOAD_BASE`, `SARVAM_API_KEY`, `SARVAM_LLM_API_KEY`, `SARVAM_LLM_BASE_URL`, `SARVAM_LLM_ENABLED`, `SARVAM_LLM_MODEL`, `TESSERACT_EXECUTABLE_PATH`, `TESSERACT_LANGUAGE` |
| Deployment metadata | `JEEVANAM_BUILD_TIMESTAMP`, `JEEVANAM_DEPLOYMENT_TIMESTAMP`, `JEEVANAM_ENVIRONMENT`, `JEEVANAM_GIT_COMMIT`, `JEEVANAM_RELEASE_TAG` |
| Keycloak | `KC_DB`, `KC_DB_PASSWORD`, `KC_DB_URL`, `KC_DB_USERNAME`, `KC_HEALTH_ENABLED`, `KC_HOSTNAME_STRICT`, `KC_HOSTNAME_STRICT_HTTPS`, `KC_HOSTNAME_URL`, `KC_HTTP_RELATIVE_PATH`, `KC_PROXY_HEADERS` |
| Web apps | `API_PORT`, `VITE_AIVA_APP_URL`, `VITE_AIVA_V2_ENABLED`, `VITE_AIVA_V2_VOICE_ENABLED`, `VITE_ANALYTICS_ENABLED`, `VITE_ANALYTICS_ID`, `VITE_API_BASE_URL`, `VITE_CAREAI_RUNTIME_URL`, `VITE_CARE_APP_URL`, `VITE_CLINIC_LOGIN_URL`, `VITE_DISCOVER_APP_URL`, `VITE_ENV_NAME`, `VITE_HEALTHCARE_APP_URL`, `VITE_KEYCLOAK_CLIENT_ID`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_URL`, `VITE_PATIENT_PORTAL_AUTH_MODE`, `VITE_PROVIDER_APP_URL`, `VITE_PROVIDER_PORTAL_AUTH_MODE`, `VITE_PUBLIC_API_BASE_URL`, `VITE_SUPPORT_URL`, `WEB_ADMIN_PORT`, `WEB_AIVA_PORT`, `WEB_CARE_PORT`, `WEB_DISCOVER_PORT`, `WEB_PUBLIC_PORT` |
| Voice gateway | `REALTIME_VOICE_GATEWAY_PORT`, `VOICE_AIVA_V2_ENABLED`, `VOICE_AI_ORCHESTRATION_URL`, `VOICE_ENABLE_GPU`, `VOICE_GATEWAY_HOST`, `VOICE_GATEWAY_PORT`, `VOICE_MAX_ACTIVE_SESSIONS`, `VOICE_MAX_TRANSCRIPT_BUFFER`, `VOICE_MODEL_CACHE_DIR`, `VOICE_PATIENT_PORTAL_UPSTREAM_WS_URL`, `VOICE_RUNTIME_SUMMARY_ENDPOINT`, `VOICE_SESSION_IDLE_TIMEOUT`, `VOICE_STT_DEVICE`, `VOICE_STT_LANGUAGE`, `VOICE_STT_MODEL`, `VOICE_STT_PROVIDER`, `VOICE_STT_TIMEOUT_SECONDS`, `VOICE_STT_URL`, `VOICE_TTS_AUTO_DOWNLOAD`, `VOICE_TTS_MODEL_DIR`, `VOICE_TTS_PROVIDER`, `VOICE_TTS_PROVIDER_ORDER`, `VOICE_TTS_VOICE`, `VOICE_WORKER_THREADS`, `VOICE_WS_MAX_SESSIONS`, `WHISPER_STT_PORT` |

Secret-like names are listed only by name; no secret values were inspected.

## 5. Variables only in `docker-compose.yml`

Repository usage was searched in application configuration, source, tests,
docs, and other Compose files (excluding `.env` values). “Likely missing
production wiring” means the variable controls a production-relevant runtime
path but is absent from the production Compose environment block; the
production env file may still supply it through `env_file`.

| Variable(s) | Service | Category / evidence | Production relevance | Recommendation |
|---|---|---|---|---|
| `AIVA_DEFAULT_LANGUAGE`, `AIVA_RESPONSE_LANGUAGE`, `AIVA_DEFAULT_VOICE` | API | Spring voice bindings and provider tests | Optional voice behavior | Review before adding; keep local-only unless production voice requires it. |
| `CLINIC_AI_MAX_ATTEMPTS`, `CLINIC_AI_REQUEST_TIMEOUT_SECONDS`, `CLINIC_AI_RETRY_BACKOFF_MS`, `GEMINI_TIMEOUT_SECONDS` | API | Spring AI settings and README/UAT configuration | Runtime resilience | Likely add to production wiring or document env-file ownership. |
| `CLINIC_CAREPILOT_SCHEDULER_ENABLED` | API | Spring CarePilot scheduler binding | High; controls scheduled Engage execution | Likely add explicitly after confirming intended production value. |
| `CLINIC_CAREPILOT_VOICE_PROVIDER`, `CLINIC_CAREPILOT_VOICE_PROVIDER_FAILOVER_ENABLED`, `CLINIC_CAREPILOT_VOICE_PROVIDER_FALLBACK` | API | Spring CarePilot voice provider selection | High when Engage voice is enabled | Review before adding; keep global voice execution disabled until approved. |
| `CLINIC_DISCOVER_VERIFICATION_EXPOSE_DEVELOPMENT_CODE` | API | Spring Discover verification flag; local default true | Security-sensitive | Keep local-only; production application default is false. |
| `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_ENABLED`, `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_GRACE_MINUTES`, `CLINIC_NOTIFICATIONS_APPOINTMENT_REMINDER_HOURS_BEFORE` | API | No non-Compose consumer found | Unclear/possibly obsolete legacy knobs | Obsolete candidates; manual review before adding or deleting. |
| `CLINIC_NOTIFICATIONS_SCHEDULER_ENABLED`, `CLINIC_NOTIFICATIONS_SCHEDULER_FIXED_DELAY` | API | Spring notification scheduler bindings | Scheduled notification behavior | Likely add/document production ownership; do not infer enablement from local. |
| `CLINIC_OCR_MAX_PAGES`, `TESSERACT_DATA_PATH`, `TESSERACT_OCR_ENGINE_MODE`, `TESSERACT_PAGE_SEG_MODE`, `TESSERACT_RENDER_DPI`, `TESSERACT_REQUIRE_DATA_PATH` | API | Spring OCR settings and README/UAT | OCR correctness/limits | Review; production image/default path may be sufficient. |
| `DEEPGRAM_API_KEY`, `DEEPGRAM_BASE_URL`, `DEEPGRAM_MODEL` | API | Spring voice STT bindings | Optional provider configuration | Keep local-only unless Deepgram is selected; inject secrets externally. |
| `ELEVENLABS_API_KEY`, `ELEVENLABS_BASE_URL`, `ELEVENLABS_MODEL`, `ELEVENLABS_VOICE_ID` | API | Spring ElevenLabs TTS bindings | Relevant if ElevenLabs is selected | Review before adding; use production secret injection only. |
| `ENGAGE_VOICE_REMINDERS_ENABLED` | API | Spring Engage global voice gate and audit docs | Safety-critical | Likely add explicitly as `false` to make the hard gate visible. |
| `FASTER_WHISPER_BASE_URL`, `FASTER_WHISPER_CONNECT_TIMEOUT_MS`, `FASTER_WHISPER_READ_TIMEOUT_MS` | API | Spring Faster Whisper bindings and deployment docs | Runtime STT behavior | Likely add/document if production voice/STT uses the service. |
| `KC_PROXY` | Keycloak | Production deployment docs and UAT Compose | Proxy/security behavior | Review/add only if required by the deployed Keycloak/reverse proxy. |
| `KEYCLOAK_HOSTNAME_STRICT`, `KEYCLOAK_HOSTNAME_STRICT_HTTPS`, `KEYCLOAK_HOSTNAME_URL`, `KEYCLOAK_HTTP_RELATIVE_PATH` | Keycloak | Local hostname controls; no other consumer found | Development-only hostname behavior | Keep local-only; production has its own URL/path settings. |
| `MINIO_API_PORT`, `MINIO_CONSOLE_PORT`, `POSTGRES_PORT`, `WHISPER_STT_PORT` | MinIO/Postgres/Whisper | Local/UAT host ports and deployment docs | Host exposure only | Keep local-only; production intentionally does not publish them. |
| `PIPER_FALLBACK_VOICE`, `PIPER_TTS_BASE_URL`, `PIPER_TTS_CONNECT_TIMEOUT_MS`, `PIPER_TTS_READ_TIMEOUT_MS` | API | Spring Piper bindings and tests/docs | Runtime TTS behavior | Review/document production ownership if Piper is used. |
| `SARVAM_BASE_URL`, `SARVAM_DEFAULT_LANGUAGE`, `SARVAM_ENABLED`, `SARVAM_STT_ENABLED`, `SARVAM_TTS_ENABLED`, `SARVAM_TTS_SPEAKER` | API | Spring Sarvam bindings and provider tests | Relevant only if Sarvam is selected | Review; do not enable implicitly. |
| `VITE_GEOCODING_BASE_URL`, `VITE_GEOCODING_PROVIDER`, `VITE_GEOCODING_SEARCH_PATH`, `VITE_MAP_DEFAULT_LATITUDE`, `VITE_MAP_DEFAULT_LONGITUDE`, `VITE_MAP_DEFAULT_ZOOM`, `VITE_MAP_DIRECTIONS_URL_TEMPLATE`, `VITE_MAP_TILE_ATTRIBUTION`, `VITE_MAP_TILE_URL` | `web-discover` | `web-discover/src/config.ts`, Dockerfile, map tests | Discover map UX | Add only if production needs non-default map settings; otherwise retain defaults. |
| `VOICE_LLM_PROVIDER_ORDER`, `VOICE_STT_PROVIDER_ORDER` | API | Spring voice routing and deployment docs | Relevant to voice runtime | Review approved providers before production wiring. |
| `VOICE_TEST_ENABLED` | API | Spring voice test gate and deployment docs | Test/diagnostic surface | Keep local-only; do not enable a test surface in production without approval. |

## 6. Variables only in `docker-compose.prod.yml`

| Variable(s) | Service | Classification and evidence |
|---|---|---|
| `CLINIC_KEYCLOAK_ADMIN_USERNAME` | API | Production-specific valid; used by Spring Keycloak admin configuration. |
| `CLINIC_MINIO_AUTO_CREATE_BUCKET`, `CLINIC_MINIO_BUCKET`, `CLINIC_MINIO_ENDPOINT` | API | Production-specific valid; used by Spring storage configuration. |
| `CLINIC_NOTIFICATIONS_CHANNEL`, `CLINIC_NOTIFICATIONS_DISPATCHER_ENABLED`, `CLINIC_NOTIFICATIONS_ENABLED` | API | Production-specific valid; explicit dispatcher controls. |
| `CLINIC_PATIENT_PORTAL_EXPOSE_DEV_OTP`, `CLINIC_PATIENT_PORTAL_SESSION_TTL` | API | Production-specific valid; explicit portal security/session controls. |
| `KC_HTTP_ENABLED` | Keycloak | Security/deployment-specific; paired with production mode and proxy/HTTPS settings. |
| `KEYCLOAK_URL` | Keycloak | Production-specific valid; used for the production Keycloak hostname. |
| `REDIS_HOST` | API | Production-specific valid; required for internal service-to-service Redis routing. |

No production-only variable was found to be clearly obsolete from repository
usage.

## 7. Compose-level default differences

These are Compose expressions/defaults, not `.env` values. Secret-like values
are redacted. An empty production expression means the production Compose
requires an external value or `env_file`.

| Variable | Local | Production | Impact |
|---|---|---|---|
| `CLINIC_CORS_ALLOWED_ORIGINS` | localhost origins | required external | Production must explicitly provide allowed origins. |
| `CLINIC_JWT_ISSUER_URI`, `CLINIC_JWT_JWK_SET_URI`, `CLINIC_KEYCLOAK_SERVER_URL` | development URLs | required external | Prevents production inheriting localhost. |
| `CLINIC_KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`, `MINIO_ROOT_PASSWORD`, `CLINIC_PATIENT_PORTAL_SESSION_SECRET` | development fallback (redacted) | required external | Correct secret hardening. |
| `CLINIC_MAIL_FROM_NAME` | `DeepThoughtNet Clinic` | `Jeevanam Healthcare` | Branding difference. |
| `CLINIC_MAIL_PROVIDER` | `logging` | `smtp` | Production selects real general mail transport. |
| `CLINIC_MAIL_HOST`, `CLINIC_MAIL_USERNAME`, `CLINIC_MAIL_FROM_EMAIL`, `CLINIC_MAIL_PASSWORD` | empty/redacted fallback | required external | Production general mail requires explicit configuration. |
| `CLINIC_MINIO_ACCESS_KEY`, `CLINIC_MINIO_SECRET_KEY` | development fallback (redacted) | required external | Correct secret hardening. |
| `CONTAINER_NAME_PREFIX` | `clinic` | `jeevanam-prod` | Naming/isolation only. |
| `DOTVOICE_ENABLED`, `DOTVOICE_STREAM_ENABLED` | `true` | `false` | Local certification defaults enabled; production transport disabled by default. |
| `GEMINI_API_KEY`, `GROQ_API_KEY` | empty fallback | required external | Production credentials must be injected. |
| `KEYCLOAK_PORT` | `8182` | `8090` | Host port convention. |
| `PIPER_DEFAULT_VOICE` | local service/API expressions include `supertonic-3-hi` and `en_US-lessac-medium` | `en_US-lessac-medium` | Voice selection differs; review locale/provider intent. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` | local Postgres defaults | required external | Production database target/identity must be explicit. |
| `TESSERACT_EXECUTABLE_PATH` | `tesseract` | `/usr/bin/tesseract` | Container path correction. |
| `VITE_API_BASE_URL`, `VITE_AIVA_APP_URL`, `VITE_PUBLIC_API_BASE_URL` | empty fallback | required external | Production browser builds need deployed URLs. |
| `VITE_CAREAI_RUNTIME_URL`, `VITE_KEYCLOAK_URL` | empty/localhost fallback | required external | Production browser endpoints must be explicit. |
| `VITE_PROVIDER_APP_URL` | `http://localhost:5177` | `http://localhost:5178` | Port convention; production localhost fallback needs review if externally hosted. |
| `VOICE_AI_ORCHESTRATION_URL` | `host.docker.internal` | `clinic-management-api` | Correct container-to-container routing in production. |
| `WEB_ADMIN_PORT`, `WEB_AIVA_PORT`, `WEB_CARE_PORT`, `WEB_DISCOVER_PORT` | 5174/5176/5175/5177 | 5175/5177/5176/5178 | Host port layout; production binds loopback. |
| `REDIS_PORT` | `6383` | `6379` | Local host-mapped port versus internal service port. |

## 8. High-risk configuration groups

| Group | Assessment | Evidence / review point |
|---|---|---|
| AI / Gemini / Groq | Intentional difference plus review | Providers and credentials exist in both; local has more timeout/retry knobs. Production credentials are external. |
| AIVA | Mostly aligned; possible wiring gap | Flags are in both; local-only language/voice variables are runtime settings. |
| Voice gateway | Intentional production hardening; review | Production loopback binds and container URLs are correct. Local-only provider-order/test knobs are not automatically missing. |
| STT / Faster Whisper | Possible production wiring gap | Production runs the services but omits API-side timeout/base URL/order variables from Compose; `.env.prod-jeevanam` may supply them. |
| Piper / TTS | Possible production wiring gap | Core Piper variables are in both; API URL/timeouts/fallback voice are local-only. |
| Care portal | Production-specific valid settings | Production explicitly supplies session TTL and dev-OTP exposure. |
| Provider / Discover | Security difference | Development verification-code exposure is local-only and application default is false. |
| Keycloak / JWT | Intentional security difference | Production requires external issuer/JWK/admin values and uses production mode/HTTPS. |
| CORS | Correctly stricter production wiring | Local has localhost fallback; production requires explicit origins. |
| SMTP / email | Intentional split | General `clinic.mail.*` is explicit in production; Engage MSG91 variables exist in both. |
| Engage messaging | Possible production gap | Scheduler and global voice-gate names are local-only; production intent should be explicit before rollout. |
| Redis | Intentional difference | Production uses internal `redis:6379` and does not publish Redis. |
| MinIO | Intentional difference | Production uses explicit endpoint/bucket/auto-create settings and no host ports. |
| Database | Intentional security difference | Production requires DB URL/user/password and does not publish Postgres. |
| Feature flags | Review required | Local defaults enable diagnostics/providers; production disables DotVoice transport by default. |

## 9. Likely production gaps

These are review candidates, not changes made by this audit:

1. Make the intended production value of `CLINIC_CAREPILOT_SCHEDULER_ENABLED`
   explicit in production Compose or document that `.env.prod-jeevanam` owns it.
2. Keep `ENGAGE_VOICE_REMINDERS_ENABLED=false` explicit in production until
   live Engage Voice execution is approved.
3. Verify production injection for Faster Whisper, Piper, ElevenLabs/Sarvam,
   and provider-order variables before enabling voice runtime.
4. Verify production Discover, CORS, Keycloak, portal session, and database
   values are present in the production env file; Compose intentionally does
   not provide insecure fallbacks for them.
5. Decide whether Discover map/geocoding settings need explicit production
   values or application defaults are sufficient.
6. Resolve the three legacy appointment-reminder variables marked obsolete
   candidates before adding production wiring.

## 10. Intentional production differences

- No public Postgres, Redis, MinIO, Whisper, or API ports.
- Loopback-only web/API/gateway bindings.
- Keycloak `start` instead of `start-dev`.
- Required external secrets and deployment URLs instead of development
  fallbacks.
- Production Keycloak proxy/HTTPS settings.
- Production notification dispatcher and storage settings.
- Production container naming and service-to-service URLs.
- DotVoice transport disabled by default in production.

## 11. Items needing manual decision

- Whether production should explicitly declare the CarePilot scheduler flag.
- Whether Engage Voice must remain globally disabled in the production env file.
- Which voice providers, if any, are approved for production and their secret
  injection path.
- Whether local legacy appointment-reminder variables are obsolete.
- Whether Discover map/geocoding defaults are acceptable for production.
- Whether `VITE_PROVIDER_APP_URL` production's localhost fallback is intentional.
- Whether `KC_PROXY` is required by the deployed Keycloak/reverse-proxy setup.
- Whether production should override OCR and AI timeout/retry values.

## 12. Recommended next-step update plan

1. Review the manual-decision list with deployment owners.
2. Inspect only variable names/requiredness in `.env.prod-jeevanam`; do not
   duplicate secrets into Compose.
3. Add or document production Compose wiring only for approved runtime flags.
4. Keep development-only ports, mock/test toggles, and insecure defaults local.
5. Validate production Compose with `docker compose config` using the
   deployment env file, without printing the rendered configuration or secrets.
6. Re-run this comparison after any approved Compose change.

## Final counts

- `docker-compose.yml` names: **225**
- `docker-compose.prod.yml` names: **172**
- Local-only names: **65**
- Production-only names: **12**
- Default/expression differences: **40**
- Likely production gaps: scheduler/Engage explicitness, voice/STT/TTS wiring,
  and selected optional AI/OCR tuning (subject to env-file ownership).
- Manual review: legacy reminder knobs, provider approvals, map defaults,
  production portal/Keycloak proxy values, and provider URL fallbacks.
- Files modified: **only this audit document**. Neither Compose file, any
  `.env` file, nor application source was modified.
