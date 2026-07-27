# Jeevanam Discover And Care Segregation Decision

## Status

Proposed for review. No application migration has been implemented by this document.

## Current Frontend Inventory

| Application | Path | Stack | Current route roots | Current deployment | Authentication |
|---|---|---|---|---|---|
| Public web / patient portal | `web-public` | Vite, React 19, React Router, MUI | `/`, `/doctors`, `/clinics`, `/specialities`, `/careai`, `/patient/*`, `/aiva/*`, static support pages | `web-public` container, nginx SPA, local port `5175`, UAT port `5176` | Patient OTP session headers for `/patient/*`; anonymous public catalog; no Keycloak browser client |
| Healthcare operations | `web-admin` | Vite, React 19, React Router, MUI | operational clinic routes, `/platform/*`, `/carepilot/*`, commercial platform | `web-admin` container, local port `5174`, UAT port `5175` | Keycloak OIDC client `clinic-web-admin` |
| AIVA microsite | `web-aiva` | Vite, React 19, React Router, MUI | AIVA product/runtime microsite | `web-aiva` container, local port `5176`, UAT port `5177` | No dedicated Keycloak client found |
| Shared package | `frontend/packages/form-validation-kit` | TypeScript package | validation utilities | consumed by `web-public` and `web-admin` | none |

`web-public/nginx/default.conf` proxies `/api/` and `/ws/` to `clinic-management-api`. Docker compose currently defines only one public web service, so separating Discover and Care requires new service entries, host routing, CORS updates, and environment variables.

Target frontend applications are `web-discover`, `web-care`, and `web-admin`. Platform Administration remains inside `web-admin`, deployed as Jeevanam Healthcare, and is exposed as privileged platform mode through platform roles, route authorization, and tenant-context selection. A separate platform-admin frontend product, deployment, or authentication client is out of scope unless future security or deployment isolation requirements justify it.

## Route Ownership Matrix

| Route | Component | Auth | API dependencies | Current nav | Future app | Complexity | Notes |
|---|---|---|---|---|---|---|---|
| `/` | `PublicHomePage` | anonymous, session-aware CTA | `/api/public/search` | Home | Discover | Medium | Move to Discover; remove patient session coupling except booking handoff CTA. |
| `/doctors` | `PublicDoctorsPage` | anonymous, session-aware booking links | `/api/public/doctors` | Doctors | Discover | Medium | Reusable public cards, filters, pagination. |
| `/doctors/:doctorSlug` | `PublicDoctorDetailPage` | anonymous, session-aware booking links | `/api/public/doctors/{slug}` | Doctors | Discover | Medium | Needs SEO-friendly route and booking-intent handoff. |
| `/clinics` | `PublicClinicsPage` | anonymous, session-aware booking links | `/api/public/clinics` | Clinics | Discover | Medium | Reusable but should read public projection long term. |
| `/clinics/:clinicSlug` | `PublicClinicDetailPage` | anonymous, session-aware booking links | `/api/public/clinics/{slug}` | Clinics | Discover | Medium | Current model is clinic-only; future includes hospitals. |
| `/specialities` | `PublicSpecialitiesPage` | anonymous | `/api/public/specialities` | Specialities | Discover | Low | Move directly. |
| `/specialities/:specialitySlug` | `PublicSpecialityDetailPage` | anonymous, session-aware booking links | `/api/public/specialities/{slug}` | Specialities | Discover | Medium | Move with public catalog client. |
| `/careai` | `PublicCareAiPage` | anonymous, can route to patient | public AIVA prompts and patient booking links | AIVA | Shared transition | Medium | Decide whether public care guidance lives in Discover or separate AIVA app; patient-specific AIVA stays Care. |
| `/aiva/*` | `AivaRedirectPage` | anonymous | `VITE_AIVA_APP_URL` | AIVA | Product marketing / obsolete in web-public | Low | Keep redirect during transition, then remove from Care. |
| `/patient/login` | `PatientLoginPage` | OTP request/verify | `/api/patient-portal/auth/otp/request`, `/verify` | Patient Login | Care | High if moved | Strong evidence for keeping current app as Care. |
| `/patient/register` | `PatientRegistrationPage` | registration session | `/api/patient-portal/registration/complete` | patient flow | Care | High if moved | Coupled to OTP registration session. |
| `/patient/dashboard` | `PatientDashboardPage` | `X-Patient-Session`, `X-Tenant-Id` | `/api/patient-portal/dashboard` | Patient Portal | Care | High if moved | Patient clinical read model. |
| `/patient/book-appointment` | `PatientBookAppointmentPage` | patient session | `/api/public/doctors`, `/api/patient-portal/doctors/{id}/slots`, `/api/patient-portal/appointments` | Patient Portal | Care | High | Mixed public discovery and patient booking. Should become booking-intent consumer. |
| `/patient/appointments` | `PatientAppointmentsPage` | patient session | `/api/patient-portal/appointments` | Patient Portal | Care | Medium | Care-owned. |
| `/patient/prescriptions` | `PatientPrescriptionsPage` | patient session | `/api/patient-portal/prescriptions`, prescription PDF | Patient Portal | Care | Medium | Care-owned patient-safe clinical data. |
| `/patient/bills` | `PatientBillsPage` | patient session | `/api/patient-portal/bills`, bill/receipt PDF | Patient Portal | Care | Medium | Care-owned financial read model. |
| `/patient/notifications` | `PatientNotificationsPage` | patient session | `/api/patient-portal/notifications`, read action | Patient Portal | Care | Medium | Care-owned reminders/notifications. |
| `/patient/lab` | `PatientLabPage` | patient session | `/api/patient-portal/lab/orders`, `/reports`, `/latest`, report PDF | Patient Portal | Care | Medium | Care-owned lab read model. |
| `/patient/careai` | `PatientCareAiPage` | patient session | `/api/patient-portal/careai/message`, `/reset`, `/ws/patient-portal/careai` | Patient Portal | Care | High | Deep patient context and voice runtime integration. |
| `/patient/profile` | `PatientProfilePage` | patient session | `/api/patient-portal/me` GET/POST | Patient Portal | Care | Medium | Care-owned patient profile. |
| `/contact` | `StaticSupportPage` | anonymous | none | Support | Discover | Low | Public support. Care should have care-specific support route later. |
| `/help-centre` | `StaticSupportPage` | anonymous | none | Support | Discover | Low | Public help placeholder. |
| `/privacy-policy` | `StaticSupportPage` | anonymous | none | Support | Shared transition | Low | Can be duplicated or hosted centrally. |
| `/terms` | `StaticSupportPage` | anonymous | none | Support | Shared transition | Low | Can be duplicated or hosted centrally. |

## API Dependency Map

Current public API classes are under `api-bff`, with orchestration into domain services:

| API | Current use | Boundary classification | Future recommendation |
|---|---|---|---|
| `/api/public/clinics` | public clinic list | public discovery | Keep compatibility; later back by Discover public projection. |
| `/api/public/clinics/{slug}` | public clinic page | public discovery | Add hospital/organization support and page-version projection. |
| `/api/public/doctors` | public doctor list and Care booking doctor list | public discovery, mixed in Care booking | Discover owns anonymous search; Care consumes opaque booking intent or patient-safe doctor slot API. |
| `/api/public/doctors/{slug}` | public doctor page | public discovery | Move to Discover API boundary. |
| `/api/public/specialities` | public speciality list | public discovery | Move to Discover projection. |
| `/api/public/search` | homepage search | public discovery | Move to Discover search/index boundary. |
| `/api/patient-portal/auth/otp/*` | Care login | patient authenticated bootstrap | Rename eventually to `/api/patient/auth/otp/*` behind adapter. |
| `/api/patient-portal/**` | Care dashboard, appointments, prescriptions, bills, lab, AIVA | patient authenticated | Keep current behavior during web-public to web-care rename; introduce `/api/patient/**` alias later. |
| `/api/platform/commercial/**` | `web-admin` commercial platform | platform/commercial | Not for public apps except public-safe S5 pricing projection. |
| `/api/carepilot/**`, `/api/careai/**` | Healthcare operations | healthcare operational | Must remain out of Discover and Care except patient-safe AIVA endpoints. |

Recommended long-term API namespaces:

| Namespace | Purpose |
|---|---|
| `/api/public/**` | Anonymous published public pages, search, marketing, public-safe pricing. |
| `/api/patient/**` | Patient Care APIs using patient auth/session. |
| `/api/provider-registration/**` | Discover provider onboarding accounts, draft applications, review submission. |
| `/api/healthcare/**` | Clinic/hospital operations currently in `web-admin`. |
| `/api/platform/**` | Platform administration and commercial administration. |

## Authentication Analysis

Current staff/admin authentication uses Keycloak realm `clinic-management` and client `clinic-web-admin`. The realm export does not define `web-public`, `jeevanam-care`, or `jeevanam-discover` clients. Patient portal authentication is custom OTP based: `/api/patient-portal/auth/otp/**` is public, and subsequent patient APIs use `X-Patient-Session` plus `X-Tenant-Id`; `PatientPortalSessionAuthenticationFilter` grants `ROLE_PATIENT` or `ROLE_PATIENT_REGISTRATION`.

Target clients:

| Client | Use |
|---|---|
| `jeevanam-discover` | Anonymous-first app; optional provider onboarding identity only. |
| `jeevanam-care` | Patient OTP flow now; later can federate to patient OIDC without changing Care route ownership. |
| `jeevanam-healthcare` | Clinic operations and Platform Administration privileged mode; current `clinic-web-admin` successor. |

Provider registration accounts must be isolated from Healthcare operational users. A verified provider onboarding account may own a Discover application, but it must not receive clinic tenant roles until Healthcare onboarding/subscription activation is approved.

## Patient Integration Depth

Patient functionality is deeply embedded in `web-public`: local storage session management, OTP request/verify, registration sessions, patient route guards, appointment booking, appointment filters, prescriptions, bills, lab reports, notifications, profile editing, patient AIVA chat, patient AIVA voice WebSocket, and clinic context handoff helpers. Moving that to a new app would require a high-risk transplant across routes, session state, styles, tests, and API clients.

Conclusion: the lowest-risk path is to evolve current `web-public` into `web-care`.

## Discovery Implementation Depth

Discovery exists but is shallower: public home/search, doctor/clinic/speciality list and detail pages, location state, public cards, public catalog client, booking links, and basic metadata. It is currently coupled to patient session state for booking CTAs and to live operational domain data through `PublicCatalogFacade`.

Reusable in `web-discover`:

| Item | Recommendation |
|---|---|
| `PublicDiscoveryPages.tsx` public cards/search/list/detail | Move or split into Discover components. |
| `publicCatalog.ts` | Move, then adapt to public projection endpoints. |
| `publicDiscovery.js`, `publicLocation.tsx`, date/booking display utilities | Extract or move. |
| `GlobalPatientHeader` | Rewrite as Discover header; it currently mixes Patient Login, demo links, and public navigation. |
| Patient session-aware booking links | Replace with booking-intent handoff. |

## Provider Self-Registration Design

Discover owns provider onboarding before Healthcare tenant activation.

Registration entry points:

| Entry | Draft application type |
|---|---|
| List your practice | selectable doctor/clinic/hospital flow |
| Register as Doctor | individual doctor |
| Register a Clinic | clinic organization |
| Register a Hospital | hospital organization |
| Start Free Trial | provider registration plus commercial onboarding lead |
| Book Demo | lead capture, not automatic operational access |

Lifecycle:

`DRAFT -> CONTACT_VERIFIED -> PROFILE_INCOMPLETE -> READY_FOR_SUBMISSION -> PENDING_REVIEW -> CHANGES_REQUESTED -> APPROVED -> PUBLISHED -> SUSPENDED -> ARCHIVED`

Verification dimensions must stay distinct: account contact verification, professional/business verification, public page publication approval, and Healthcare tenant activation.

## Controlled Public Page Builder

Discover should implement governed templates, not arbitrary HTML or plugins. Page types: doctor, clinic, hospital. Supported blocks: hero, overview, doctors, departments/specialities, services, timings, fees, facilities, locations, gallery, insurance/payment options, booking CTA, contact/map, FAQ, and certifications.

Builder requirements: draft and published versions, controlled typography/colors, section enablement, drag/reorder of approved blocks, media upload, mobile preview, SEO title/description, slug management, publication validation, accessibility checks, and review audit. Verification documents and private uploads must never be exposed.

## Database Boundary Recommendation

Preferred long-term ownership is a Discover bounded context with a `discover` schema or equivalent owned tables:

| Table family | Purpose |
|---|---|
| `discover_registration_accounts` | onboarding login/contact identity |
| `discover_provider_applications` | doctor/clinic/hospital application aggregate |
| `discover_application_versions` | immutable submitted versions |
| `discover_provider_profiles` | normalized provider profile |
| `discover_doctor_profiles` | doctor-specific public profile |
| `discover_organization_profiles` | clinic/hospital public organization profile |
| `discover_public_pages` | page aggregate |
| `discover_page_versions` | draft/published page versions |
| `discover_page_blocks` | governed block content |
| `discover_media_assets` | public-safe media copies/references |
| `discover_publication_reviews` | review workflow |
| `discover_publication_history` | audit/publication trail |
| `discover_search_documents` | search index projection |
| `discover_booking_intents` | opaque booking handoff |
| `discover_leads` | demo/registration leads |

Initial shared-service reads can be tolerated temporarily for compatibility, but Discover should not query operational Healthcare tables for every public page request in production scale. Use publication flow:

`Healthcare / onboarding source -> public projection publisher -> Discover schema/search index -> public page/search`

## Care Data Boundary

Care should retain patient-safe read models only: identity, appointments, confirmations, prescriptions, reports, documents, vaccinations, invoices/payments, reminders, profile/dependants, and patient-context AIVA. Clinical truth remains in Healthcare/clinical domains. Care should not own provider marketing pages, public SEO pages, provider registration, public page authoring, or product pricing.

## Booking Handoff

Discover should create a short-lived opaque booking intent after provider/doctor/service/location/slot selection. Care then performs OTP login, resolves the intent, links the patient, and confirms the appointment.

Intent rules:

| Rule | Recommendation |
|---|---|
| Token | opaque, random, business-safe, no PHI in query string |
| Expiry | short TTL, suggested 10-20 minutes |
| Idempotency | confirming the same intent returns existing confirmation or a controlled expired/used state |
| Invalidations | slot unavailable, provider unpublished, tenant disabled, expired intent |
| Attributes | public provider ref, doctor ref, service, location, slot, source campaign, expiry |

## Product Marketing And Pricing

Discover should host Jeevanam Healthcare product overview, features, AI capabilities, public plans/pricing, book demo, and start registration. Pricing must use a public-safe S5 projection only: no unpublished versions, internal rate rules, tenant overrides, commercial audit, or internal IDs.

## Option Comparison

Scores are 1 low / 5 high, where higher is better.

| Criterion | Option A: `web-public -> web-care`, new `web-discover` | Option B: `web-public -> web-discover`, new `web-care` |
|---|---:|---:|
| Patient migration risk | 5 | 1 |
| Discovery migration risk | 3 | 5 |
| Authentication impact | 5 | 2 |
| Route cleanup simplicity | 4 | 2 |
| SEO reset effort | 3 | 4 |
| Provider onboarding fit | 4 | 3 |
| Time to first clinic onboarding | 4 | 2 |
| Test preservation | 5 | 2 |
| Overall | 33 | 21 |

Final recommendation: choose Option A. Rename/refocus current `web-public` as `web-care`, then create `web-discover` for public discovery, provider registration, public page builder, and product marketing.

## Recommended Frontend Structure

| Package/app | Responsibility |
|---|---|
| `web-care` | Current `web-public` patient portal refocused to Care. |
| `web-discover` | New anonymous-first public discovery and provider onboarding app. |
| `web-admin` | Healthcare operations and Platform Administration privileged mode in the same deployed app. |
| `web-aiva` | AIVA microsite/runtime surface if retained. |
| `frontend/packages/ui-kit` | Future shared design primitives only if duplication becomes real. |
| `frontend/packages/healthcare-api-types` | Generated or shared DTO types, not business logic. |
| `frontend/packages/media-client` | Authenticated/public media helpers if both apps need them. |
| `frontend/packages/booking-intent-client` | Discover/Care handoff client. |

Avoid sharing patient route guards, provider page authoring, or operational tenant controls across apps.

## Migration Plan

| Phase | Scope | API/migrations | Tests | Rollback | Completion |
|---|---|---|---|---|---|
| 0 Inventory and tests | Freeze route/API inventory, add source tests guarding current behavior | none | `web-public` tests, API controller tests | no production change | current app behavior documented and guarded |
| 1 Explicit route ownership | Add route classification constants and navigation labels inside current app | none | route ownership tests | revert nav labels/constants | every route marked Care/Discover/Shared |
| 2 Rename/refocus to Care | Rename Docker/package/service aliases, keep compatibility image/name initially | env and compose aliases only | OTP, dashboard, prescriptions, lab, bills, AIVA | keep old `web-public` deployment | Care domain works at `care.deepthoughtnet.com` |
| 3 Create Discover shell | New `web-discover` Vite app, move public pages/client, remove patient session coupling | new service/env/CORS | anonymous navigation/search/detail tests | route `jeevanam.deepthoughtnet.com` back to old public app | public search/detail parity |
| 4 Provider self-registration | Add onboarding account and application lifecycle | additive Discover/domain migrations | draft/resume/contact verification/submit tests | hide entry points | draft and submit work without Healthcare access |
| 5 Page builder | Controlled templates, media, preview, validation | page/version/block tables | page draft/publish validation tests | disable builder, keep profiles | safe preview and submitted page version |
| 6 Review/publication | Reviewer workspace and publication projection | review/history/search projection tables | maker-checker, publish/unpublish tests | unpublish projection | approved pages published only |
| 7 Booking intent | Discover creates intent, Care consumes after OTP | `discover_booking_intents` | handoff/deep link/idempotency tests | fall back to current Care booking route | no PHI in URL, appointment confirmation works |
| 8 Separate projection/schema | Move `/api/public/**` to Discover projection reads | Discover schema/search index | search parity, privacy tests | compatibility adapter to old facade | public pages no longer live-query operations |
| 9 SEO/security rollout | SSR/prerender decision, sitemap, OG, CSP, rate limit, CAPTCHA | config only unless projections need indexes | SEO snapshots, security tests | domain routing rollback | production domains cut over |

## Deployment And Domain Plan

| Domain | Target |
|---|---|
| `jeevanam.deepthoughtnet.com` | `web-discover` |
| `care.deepthoughtnet.com` | `web-care` |
| `healthcare.deepthoughtnet.com` | `web-admin` |

Required deployment work: add Discover and Care compose services or aliases, update nginx/ingress host routing, configure API CORS for the three product domains, add Keycloak redirect URIs for Healthcare and the future provider-onboarding identity, define logout redirects, set CSP/media origins, and ensure `/ws/patient-portal/careai` is available only for Care. Platform Administration continues to route through `healthcare.deepthoughtnet.com` and the `web-admin` application.

## SEO Assessment

Current `web-public` is client-rendered Vite React. That is acceptable for patient Care, but weak for Discover SEO. Before Discover production launch, evaluate SSR/prerendering. Conservative options:

| Option | Fit |
|---|---|
| Static prerender for published provider pages | Good first step if content changes are controlled. |
| SSR framework for Discover only | Best long-term for metadata, sitemap, schema.org, performance. |
| Keep Vite SPA | Acceptable only for non-SEO MVP/internal launch. |

Do not change framework until route extraction and public projection contracts are stable.

## Security And Privacy

Public pages may expose only approved public-safe fields. Verification documents, private registration uploads, unpublished pages, operational schedules beyond public slot summaries, patient data, staff-only data, internal IDs, and commercial internals must remain private. Provider onboarding needs rate limiting, CAPTCHA or equivalent abuse control, terms acceptance, consent capture, audit, review history, upload scanning, and public media copies/projections.

## Risks And Mitigations

| Risk | Mitigation |
|---|---|
| Care regression during app rename | Keep compatibility deployment and route tests until cutover. |
| Discover leaks operational data | Introduce public projection schema and privacy tests before broad launch. |
| Provider account accidentally gets Healthcare access | Separate provider onboarding identity from tenant membership. |
| SEO underperforms on SPA | Decide SSR/prerender after MVP content model stabilizes. |
| Booking handoff exposes sensitive data | Opaque expiring booking intents only. |
| Duplicate source of truth after Healthcare onboarding | Transfer management authority to Healthcare and publish public projections to Discover. |

## Decision

Proceed with Option A after review: evolve current `web-public` into Jeevanam Care and build Jeevanam Discover as a new application. This preserves the deepest existing integration, limits patient workflow risk, and leaves Discover free to use SEO-appropriate architecture and a clean provider onboarding/publication data model.
