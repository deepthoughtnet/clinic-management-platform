# Jeevanam Health / Care / Provider / Discover Integration

## 1. Purpose

This document records the current integration model between:

- Health operations
- Care patient portal
- Provider workspace and publication flow
- Discover public search
- Platform Admin moderation and connection management

It is an audit and integration guide only. It does not change runtime behavior.

Preserved as-is:

- Provider publication remains `Provider -> Platform Admin review -> Discover publish`
- Discover public visibility remains separate from Health operational visibility
- Care patient visibility is not equivalent to Discover public visibility

## 2. Application Responsibilities

### Health

Current Health application: `web-admin`

Primary responsibilities:

- clinic setup and tenant administration
- doctor management
- availability and appointment operations
- patients, billing, prescriptions, laboratory, notifications
- platform admin access to tenants, provider connections, and Discover review workspaces

### Care

Current Care application: `web-care`

Primary responsibilities:

- patient login and session restoration
- patient portal home, appointments, prescriptions, bills, notifications, lab, profile
- patient booking experience
- CareAI / AIVA patient assistant
- patient-facing access request and registration flows

### Provider

Current Provider workspace is implemented in `web-discover` under provider routes.

Primary responsibilities:

- provider onboarding and account access
- provider draft editing
- provider public profile authoring
- provider review submission and resubmission
- provider workspace and public profile preview

### Discover

Current Discover application: `web-discover`

Primary responsibilities:

- public search and detail pages for doctors, clinics, hospitals, and specialities
- public-facing provider profile rendering
- provider portal/login/workspace
- provider onboarding entry point

### Platform Admin

Current Platform Admin UI is hosted in `web-admin`.

Primary responsibilities:

- review provider applications
- approve/reject/request changes for provider publication
- inspect provider connections and ownerships
- unpublish published public profiles
- operate tenant-level platform workflows

## 3. Current Architecture

### Frontend routes

- Care routes live in `web-care/src/App.tsx`
  - `/patient/login`
  - `/patient/request-access`
  - `/patient/register`
  - `/patient/dashboard`
  - `/patient/book-appointment`
  - `/patient/appointments`
  - `/patient/prescriptions`
  - `/patient/bills`
  - `/patient/notifications`
  - `/patient/lab`
  - `/patient/careai`
  - `/patient/profile`
- Discover public and provider routes live in `web-discover/src/App.tsx` and `web-discover/src/routes.ts`
  - `/discover/doctors`
  - `/discover/clinics`
  - `/discover/hospitals`
  - `/discover/specialities`
  - `/provider/*`
  - `/provider/onboarding/:applicationId/:step`
- Health and Platform Admin routes live in `web-admin/src/app/App.tsx`
  - `/patients`
  - `/appointments`
  - `/doctors/...`
  - `/lab`
  - `/platform/tenants`
  - `/platform/provider-access-requests`
  - `/platform/provider-connections/*`
  - `/platform/discover/provider-applications`

### Backend transport and orchestration

- `backend/api/api-bff` exposes the transport layer for all apps.
- It orchestrates Health, Care, Discover, and Platform Admin requests.
- It does not own domain persistence for domain-owned data.

### Backend domain ownership

- `patient-domain` owns patient records and patient access request logic
- `clinic-domain` owns clinic and doctor operational profiles
- `appointment-domain` owns scheduling and availability
- `discover-domain` owns provider onboarding, public profiles, moderation, and public associations
- `identity-domain` owns app-user / Keycloak-backed tenant identity projections

### Key backend entry points

- Care patient portal: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalController.java`
- CareAI service: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiService.java`
- Public catalog: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogController.java`
- Public catalog facade: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogFacade.java`
- Provider onboarding: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderOnboardingController.java`
- Provider landing page: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderLandingPageController.java`
- Provider review: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/ProviderApplicationReviewController.java`
- Public profile review: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/providerconnections/ProviderPublicProfileReviewController.java`
- Provider connections: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/providerconnections/ProviderConnectionsController.java`
- Health operational appointments: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/AppointmentController.java`
- Health availability: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/DoctorAvailabilityController.java`
- Discover presence sync: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/HealthcarePublicListingSyncService.java`
- Discover presence inspection: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/clinic/DiscoverPresenceController.java`

## 4. Current Identity and Authentication Model

### Provider / clinic staff

Current pattern:

- staff are tenant-scoped `app_users`
- doctors are tenant users with membership role `DOCTOR`
- Keycloak is the login identity boundary
- `app_users.keycloak_sub` is the backend projection of the auth identity
- tenant membership is preserved separately from auth identity
- provider onboarding and landing-page editing use an opaque `X-Provider-Onboarding-Token`
- provider workspace routes in Discover use a provider session flow, not the patient portal token

Evidence:

- `backend/domains/identity-domain/src/main/java/com/deepthoughtnet/clinic/identity/db/AppUserEntity.java`
- `backend/domains/identity-domain/src/main/java/com/deepthoughtnet/clinic/identity/service/TenantUserManagementService.java`
- `backend/domains/identity-domain/src/main/java/com/deepthoughtnet/clinic/identity/service/AppUserProvisionerImpl.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderOnboardingController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderLandingPageController.java`

Observed rules:

- `app_users` is tenant-scoped
- `keycloak_sub` is normalized and reused as the auth identity anchor
- username and email uniqueness are enforced in the identity layer
- doctor users are not global identities for a whole organization; they are tenant membership records

### Patients

Current pattern:

- patients are stored in the tenant-scoped `patients` table
- mobile number is the main authentication/contact attribute
- patient number is tenant-scoped
- a patient record is not a global platform identity

Evidence:

- `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/db/PatientEntity.java`
- `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/db/PatientRepository.java`
- `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/service/PatientService.java`

Current auth paths:

- OTP login: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/auth/PatientPortalOtpController.java`
- patient registration: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalRegistrationController.java`
- access-approval login: `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/service/PatientPortalAccessRequestService.java`

Observed patient auth behavior:

- OTP and access-approval login are both supported
- patient sessions are issued as signed portal tokens by `PatientPortalSessionTokenService`
- session payload carries tenantId, patientId when available, phone, display name, and roles
- the portal token is not a Keycloak user session

### Patient identity observations

- patient UUID is independent from mobile number
- patient records are tenant-scoped
- the same mobile can exist in multiple tenants
- there is no single global patient master record in the current code
- deduplication is tenant-scoped and mobile-aware, not globally canonical

## 5. Current Provider and Clinic Model

### Health operational clinic model

`ClinicProfileEntity`

- one clinic profile per tenant
- fields include clinic name, display name, address, contact details, active, publicListingEnabled, and slug

`DoctorProfileEntity`

- one doctor profile per tenant and doctor user
- fields include specialization, qualification, registration number, consultation room, fees, active, publicListingEnabled, and slug
- unique constraint is `(tenant_id, doctor_user_id)`

Evidence:

- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/db/ClinicProfileEntity.java`
- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/db/DoctorProfileEntity.java`
- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/service/ClinicProfileService.java`
- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/service/DoctorProfileService.java`

### Doctor-to-clinic relationship

Current relationship shape:

- in Health, a doctor profile belongs to a tenant
- the tenant is the clinic boundary for operational scheduling and patient records
- in Discover, a doctor can be associated with one or more public practices through public association projections

Evidence:

- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/db/DoctorProfileEntity.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicdoctorpracticeassociation/PublicDoctorPracticeAssociationService.java`

Interpretation:

- Health operationally models doctor membership at tenant scope
- Discover models public doctor-practice associations separately
- there is no dedicated first-class `doctor_clinic_affiliation` entity in Health for public visibility

### Availability and booking ownership

Current sources:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/DoctorAvailabilityController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/AppointmentController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalService.java`

Observed behavior:

- availability is tenant-scoped
- booking and queue access are tenant-scoped in Health operations
- patient booking in Care can resolve either through public booking targets or through a tenant-aware booking reference

### Public slug ownership

Current slugs are owned by the profile projections:

- clinic slug in `ClinicProfileEntity`
- doctor slug in `DoctorProfileEntity`
- public Discover canonical slug in `DiscoverPublicProviderProfileEntity`

The Discover canonical slug is the public-facing identity used by the public catalog.

## 6. Current Public Publication Workflow

This workflow is preserved.

### Provider-side workflow

Provider onboarding and workspace live in `web-discover`:

- `POST /api/provider-registration/providers`
- `GET /api/provider-registration/providers/me`
- `PUT /api/provider-registration/providers/{id}`
- `POST /api/provider-registration/providers/{id}/submit`
- `GET /api/provider-registration/providers/{id}/preview`

Provider landing page editing is handled by:

- `GET /api/provider/landing-page`
- `PUT /api/provider/landing-page`
- `POST /api/provider/landing-page/publish`

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderOnboardingController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderLandingPageController.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/onboarding/ProviderOnboardingService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofiledraft/ProviderPublicProfileDraftService.java`

### Platform Admin review workflow

Platform Admin review actions are exposed through:

- `GET /api/platform/discover/provider-applications`
- `POST /api/platform/discover/provider-applications/{referenceNumber}/start-review`
- `POST /api/platform/discover/provider-applications/{referenceNumber}/request-changes`
- `POST /api/platform/discover/provider-applications/{referenceNumber}/approve`
- `POST /api/platform/discover/provider-applications/{referenceNumber}/publish`

Public profile moderation and unpublish are exposed through:

- `GET /api/platform/provider-connections/public-profile-reviews`
- `POST /api/platform/provider-connections/public-profile-reviews/{submissionReference}/start`
- `POST /api/platform/provider-connections/public-profile-reviews/{submissionReference}/request-changes`
- `POST /api/platform/provider-connections/public-profile-reviews/{submissionReference}/approve`
- `POST /api/platform/provider-connections/public-profile-reviews/{submissionReference}/publish`
- `POST /api/platform/provider-connections/public-profile-reviews/{publicProfileReference}/unpublish`

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/ProviderApplicationReviewController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/ProviderApplicationReviewApiService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/providerconnections/ProviderPublicProfileReviewController.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/onboarding/ProviderOnboardingService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofilemoderation/ProviderPublicProfileModerationService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofile/ProviderPublicProfileService.java`

### Public visibility rules

Public catalog reads only published profiles.

Evidence:

- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofile/ProviderPublicProfileService.java`
  - `findBySlug(...)` returns data only when publication status is `PUBLISHED`
  - `findByProviderId(...)` returns data only when publication status is `PUBLISHED`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogFacade.java`

Public doctor and clinic pages are derived from published profiles and published public associations.

When publication is revoked or unpublish runs:

- the public profile is removed from public discovery
- the moderation/publication projection is marked `UNPUBLISHED`
- the public catalog no longer exposes the record

## 7. Current Patient/Care Model

### Care patient portal

Care routes:

- login, request-access, registration
- dashboard, book appointment, appointments, prescriptions, bills, notifications, lab, AIVA, profile

Evidence:

- `web-care/src/App.tsx`
- `web-care/src/pages/patient/PatientPortalPages.tsx`
- `web-care/src/pages/aiva/AivaPages.tsx`

### Current backend patient portal model

`PatientPortalController` exposes:

- `/api/patient-portal/dashboard`
- `/api/patient-portal/me`
- `/api/patient-portal/appointments`
- `/api/patient-portal/doctors`
- `/api/patient-portal/doctors/{publicDoctorId}/slots`
- `/api/patient-portal/appointments`
- `/api/patient-portal/careai/message`
- `/api/patient-portal/lab/orders`
- `/api/patient-portal/lab/reports`
- `/api/patient-portal/notifications`

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalController.java`

### Care visibility behavior

Current code has two distinct patient-side lookup modes:

1. Public catalog discovery
   - CareAI `findDoctors` and `findClinics` use `PublicCatalogFacade`
   - patient booking UI loads public doctor detail pages from `/api/public/doctors/{slug}`
   - public clinic/doctor booking targets are used when the provider is published

2. Tenant-scoped patient portal data
   - `/api/patient-portal/doctors` returns active doctors from the authenticated patient tenant
   - appointments, lab, prescriptions, bills, and notifications are tenant-scoped
   - slot lookup and booking are tenant-aware and can use booking references when present

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiBusinessLookupService.java`
- `web-care/src/pages/patient/PatientPortalPages.tsx`

### Booking behavior

Current booking rules in backend:

- if a booking reference resolves through the healthcare availability port, booking can use the resolved tenant doctor and target reference
- otherwise booking resolves the tenant from clinic slug, tenantId, or clinicId
- public booking without booking reference requires the clinic to be active and `publicListingEnabled`
- public-bookable doctor lookup requires the doctor to be active and `publicListingEnabled`

Important consequence:

- Care can show active doctors in the tenant portal even when public listing is disabled
- Discover public search still hides unpublished profiles
- a patient does not automatically gain cross-tenant private visibility just by being a Jeevanam user

### Care booking UX note

Authenticated Care booking now distinguishes two public-facing states without changing backend publication rules:

- online-bookable Health doctors continue through the date, slot, and confirmation flow
- public Discover doctors that are only call-to-book render a provider/contact panel in Care instead of a dead-end slot flow

Profile and clinic links for those public call-to-book providers continue to use the existing public discovery routes and public catalog data; no private Health data is exposed and no new publication model was introduced.

### Patient relationship model observed in code

There is no dedicated first-class `PatientClinicRelationship` entity in the current code.

Current equivalents are:

- approved patient access request
- tenant-scoped patient record
- appointment history
- portal session token tied to tenant and patient

## 8. Current AIVA/CareAI Provider Lookup Model

### Tool registry

`PatientPortalCareAiToolRegistry` maps tools to services:

- `FIND_DOCTOR` -> `publicCatalogFacade.listDoctors`
- `FIND_CLINIC` -> `publicCatalogFacade.listClinics`
- `FIND_SLOTS` -> `businessLookupService.findSlots`
- `BOOK_APPOINTMENT` -> `patientPortalService.bookAppointment`
- `FIND_APPOINTMENTS` / `CHECK_APPOINTMENT` -> patient appointments
- `CANCEL_APPOINTMENT` -> `patientPortalService.cancelAppointment`
- `RESCHEDULE_APPOINTMENT` -> `patientPortalService.rescheduleAppointment`

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiToolRegistry.java`

### Lookup behavior

`PatientPortalCareAiBusinessLookupService`:

- prefers `PublicCatalogFacade` for doctor and clinic search
- falls back to tenant patient portal doctors only if the public catalog facade is unavailable
- slot lookup delegates to `PatientPortalService.doctorSlots(...)`
- appointment lookups are tenant-aware and can search linked tenant records for the same mobile

Evidence:

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiBusinessLookupService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiService.java`

### AIVA booking and privacy model

Current AIVA behavior is turn-based and not full-duplex streaming.

Current flow:

- browser capture
- buffered utterance
- STT
- CareAI orchestration
- LLM response
- TTS
- playback

The AIVA service does not merge public discovery and private tenant data into one shared visibility model.

Important note:

- CareAI doctor/clinic search uses public Discover data
- patient portal appointment and slot resolution uses tenant-aware health data
- this is a dual-path model, not a single global search across all tenants

### Security observation

The current implementation keeps slot and booking resolution server-side and tenant-aware.

The remaining risk surface is architectural clarity, not direct frontend-only filtering.

## 9. Visibility Matrix

| Subject / State | Health visibility | Care visibility | Discover visibility | Bookable | Notes |
| --- | --- | --- | --- | --- | --- |
| Active clinic, unpublished | Visible in Health | Visible only through tenant-authorized Care context | Hidden from public search | Possibly, only through tenant/booking-reference path | PublicListingEnabled does not control Health visibility |
| Active doctor, unpublished | Visible in Health | Visible to tenant-authorized Care context | Hidden from public search | Possibly, only through tenant/booking-reference path | CareAI public search will not find it |
| Active clinic, published | Visible in Health | Visible in Care | Visible in Discover | Yes if booking capability is online or call-to-book | Publish controls public discovery, not Health ownership |
| Active doctor, published | Visible in Health | Visible in Care | Visible in Discover | Yes if booking rules allow | Public association projections may expose multiple clinics |
| Inactive clinic or doctor | Visible in Health | Historical patient records may remain visible | Hidden from public search | No new booking | Historical appointments still matter |
| Existing patient of Clinic A | Health data visible only within tenant A | Care can show tenant A context | Discover only shows published providers | Yes for tenant A rules | No automatic access to Clinic B |
| Existing patient of Clinic B | Health data visible only within tenant B | Care can show tenant B context | Discover only shows published providers | Yes for tenant B rules | Separate tenant authorization required |
| Anonymous user | No Health access | No Care access | Public Discover only | Only published public booking flows | Anonymous users cannot see tenant-private records |

## 10. Real-World Use Cases

| # | Scenario | Current behavior | Support status | Gap / risk |
| --- | --- | --- | --- | --- |
| 1 | Existing patient of Clinic A, clinic and doctor private | Care can operate within tenant A if authenticated; private visibility is tenant-scoped | Partial | No explicit `PatientClinicRelationship` entity; booking path still depends on available tenant/booking-reference data |
| 2 | Existing patient of Clinic A, doctor and clinic public | Works in Care and Discover | Implemented | None material |
| 3 | New anonymous person | Sees only published Discover profiles | Implemented | None material |
| 4 | New Care user with OTP but no Health relationship | Auth can succeed only if tenant context exists; no global private provider search | Partial | Edge cases depend on tenant context resolution and auth mode |
| 5 | Patient books a public doctor through Discover | Booking can use public booking reference and appears later in Care appointments | Implemented | None material |
| 6 | Clinic invitation to a private clinic | Access-approval flow exists | Implemented / partial depending environment mode | Requires explicit access request / approval setup |
| 7 | Doctor referral to a private specialist | No dedicated referral authorization model found | Missing | Not yet a first-class relationship type |
| 8 | Doctor works across multiple clinics | Discover public associations support multiple practices | Partial | Health operational profile is still tenant-scoped |
| 9 | Clinic disables public listing | Discover hides it; Care tenant patients can still remain authorized | Implemented / partial | Care booking still needs tenant or booking reference to resolve safely |
| 10 | Provider publication revoked | Discover removes public visibility; existing Care relationships remain tenant-scoped | Implemented | Need careful policy for existing patient continuity, but code preserves history |
| 11 | Doctor inactive | Hidden from active discovery and active portal lists | Implemented | Historical appointments remain visible |
| 12 | Doctor on leave / not bookable | Can remain visible while slots are not bookable | Implemented | Availability and visibility are separate concerns |
| 13 | Pediatric patient with guardian | No explicit guardian model found in this audit | Missing / partial | Requires separate authorization design |
| 14 | Corporate or private clinic | Can be Care-visible and Discover-hidden | Partial | Depends on tenant authorization and booking flow |
| 15 | AIVA: "Book my regular doctor" | Should use patient-authorized tenant context, but today the model is mostly tenant/public-path driven | Partial | No dedicated first-class "my doctor" relationship primitive |
| 16 | AIVA: "Find a dermatologist near me" | Uses public Discover search | Implemented | None material |
| 17 | Multi-tenant privacy | Tenant isolation is enforced server-side | Implemented | Cross-tenant leakage risk remains only if future authorization shortcuts are added |

## 11. Recommended Integration Model

Smallest clean integration model based on current code:

1. Health remains the operational source of truth
   - clinic profiles
   - doctor profiles
   - availability
   - appointments
   - patient records

2. Discover remains the public publication and search surface
   - only published profiles are public
   - provider moderation and publication remain separate from Health operations

3. Care remains the authenticated patient surface
   - it can show tenant-authorized patient context
   - it can use public Discover for acquisition-style search
   - it must not assume public listing implies private care access

4. Provider publication workflow stays unchanged
   - provider onboarding
   - Platform Admin review
   - publication
   - unpublish
   - public catalog projection

5. A patient-private care relationship should be represented explicitly enough for server-side enforcement
   - current code already has patient access requests, tenant-scoped records, and appointment history
   - if a stronger "existing patient" rule is needed later, the smallest evolution is a patient-to-tenant authorization record or a hardened reuse of approved access requests
   - do not overload `publicListingEnabled` to mean patient authorization

## 12. Authentication Recommendation

### Provider / staff

Smallest consistent model:

- Keycloak user
- username/email + password
- role-based access
- tenant membership projection in `app_users` / `tenant_users`
- MFA can be added later without redesigning identity ownership

This is already the effective model in the codebase.

### Patient

Smallest consistent model:

- OTP-first login or access-approval-first login for Care
- mobile number is the authentication attribute
- patient UUID remains the internal record identity
- patient portal session token is derived server-side
- phone number should not become the primary identity key

This is already close to the current implementation.

## 13. Multi-Tenant Security Rules

- Health endpoint access must stay tenant-scoped and permission-gated
- Care patient portal access must remain tied to the authenticated patient session tenant
- Discover public endpoints must only expose published records
- public search must not leak unpublished or tenant-private providers
- CareAI search must not use frontend-only filtering as its security boundary
- slot lookup and booking authorization must be enforced server-side
- the same mobile number can exist in multiple tenants, but cross-tenant visibility must still require explicit authorization or matching context
- private provider exposure must remain tenant/patient-authorized

## 14. AIVA Rules

### "My doctor"

Should resolve from the authenticated patient’s authorized care context:

- current tenant
- existing appointment history
- explicit booking reference
- approved access / care relationship

It should not search the entire public directory first.

### "My clinic"

Should resolve from the authenticated patient’s authorized tenant context.

### Public provider discovery

Should use Discover published data only.

### Appointment booking

Should authorize against:

- an explicit tenant / booking reference, or
- a published public booking target, or
- an approved Care relationship

If the relation is missing, the assistant should ask the patient to pick a clinic/doctor rather than infer cross-tenant access.

## 15. Missing Capabilities / Gaps

| Capability | Status | Notes |
| --- | --- | --- |
| PatientClinicRelationship | Missing | Current code relies on tenant-scoped patient records, access requests, and appointments |
| PatientProviderRelationship | Partial | Appointment history and patient portal access approximate it |
| Provider affiliation model | Partial | Health tenant membership plus Discover public associations exist, but they are not one unified model |
| Patient invite / access approval | Implemented | `PatientPortalAccessRequestService` already supports request, approve, reject, revoke, and authenticate |
| Existing-patient-only booking | Partial | Supported when tenant context is already known; not a separate first-class relationship entity |
| Private Care visibility | Partial | Works through tenant-scoped patient context, not through Discover publication |
| Public Discover visibility | Implemented | Only published profiles are read by public catalog |
| Booking visibility | Partial | Depends on booking reference / tenant / public listing state |
| Provider status lifecycle | Implemented | Onboarding and moderation lifecycles exist |
| Publication status lifecycle | Implemented | Publication, unpublish, and current publication projection exist |
| Guardian / dependent support | Missing | No explicit guardian model found in this audit |
| Referral-gated specialist visibility | Missing | No explicit referral authorization flow found |

## 16. Proposed Implementation Phases

No implementation is performed in this task. Suggested future phases:

1. Formalize patient-to-tenant authorization semantics
   - keep the current patient portal session model
   - add or harden the relationship record used for "existing patient" access

2. Make Care routing explicit
   - separate public discovery search from tenant-authorized patient search
   - keep the two flows server-side distinct

3. Extend AIVA with authorization-aware context selection
   - "my doctor" and "my clinic" should prefer patient-authorized context
   - generic discovery should continue to use public Discover

4. Preserve provider publication flow as-is
   - do not merge it into Health or Care authorization
   - keep Platform Admin moderation and Discover publication separate

## 17. APIs / Classes / Tables Involved

### Frontend

- `web-admin/src/app/App.tsx`
- `web-admin/src/layout/nav.ts`
- `web-care/src/App.tsx`
- `web-care/src/pages/patient/PatientPortalPages.tsx`
- `web-discover/src/App.tsx`
- `web-discover/src/routes.ts`

### API controllers

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/auth/PatientPortalOtpController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/PatientPortalRegistrationController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/PatientPortalCareAiService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogFacade.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/AppointmentController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/DoctorAvailabilityController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/ProviderApplicationReviewController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/providerconnections/ProviderPublicProfileReviewController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/providerconnections/ProviderConnectionsController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderOnboardingController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/ProviderLandingPageController.java`

### Domain services

- `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/service/PatientPortalAccessRequestService.java`
- `backend/domains/patient-domain/src/main/java/com/deepthoughtnet/clinic/patient/service/PatientService.java`
- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/service/ClinicProfileService.java`
- `backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/service/DoctorProfileService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/onboarding/ProviderOnboardingService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofile/ProviderPublicProfileService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicprofilemoderation/ProviderPublicProfileModerationService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publicdoctorpracticeassociation/PublicDoctorPracticeAssociationService.java`
- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/publichospitaldoctorassociation/PublicHospitalDoctorAssociationService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/platform/discover/HealthcarePublicListingSyncService.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/clinic/DiscoverPresenceController.java`

### Tables / projections

- `patients`
- `app_users`
- `clinic_profiles`
- `doctor_profiles`
- `discover_provider_applications`
- `discover_provider_submissions`
- `discover_provider_status_history`
- `discover_provider_documents`
- `discover_provider_locations`
- `discover_provider_services`
- `discover_provider_change_requests`
- `discover_public_provider_profiles`
- `discover_public_provider_profile_versions`
- `discover_public_provider_profile_slugs`
- `discover_public_profile_submissions`
- `discover_public_profile_publications`
- `discover_public_profile_review_findings`
- `discover_public_doctor_practice_associations`
- `discover_public_hospital_doctor_associations`
- `patient_portal_access_requests`

## 18. Decisions to Preserve

- Existing Provider publication workflow stays intact
- Existing Platform Admin approval flow stays intact
- Discover continues to show only publicly approved/published providers
- Health remains the operational and clinical source
- private provider exposure must remain tenant/patient-authorized
- Care may coexist with public Discover, but public listing must not be treated as the only access rule

## 19. Open Decisions

Only unresolved decisions that remain after this audit:

- Whether private Care access should remain implicit via tenant-scoped patient history, or whether it should be formalized as a first-class patient-to-tenant relationship record
- Whether Care should keep using public Discover for doctor/clinic search while also exposing an explicit tenant-authorized "my doctor / my clinic" mode
- Whether referral-gated specialist visibility is a future requirement for the patient portal

## 20. Multi-Clinic Care Context

### Observed current case

The live Care portal session currently resolves to:

- `Demo Clinic`
- one authenticated patient record for the current portal session
- one active patient portal access request for the verified mobile number

The same mobile number also exists in `Jeevanam Automation Lab`, but that tenant does not currently have an approved patient portal access request for the Care session. As a result, the current Care session does not surface that tenant as an authorized clinic context.

### Current single-tenant limitation

The current patient portal session model is intentionally single-context:

- `PatientPortalSessionTokenService` issues one signed token with one `tenantId`
- `PatientPortalSessionPrincipal` carries one `tenantId` and one `patientId`
- `PatientPortalAuthContextExtractor` resolves the active Care session from that single tenant context
- `PatientPortalAccessRequestService` authenticates into one tenant at a time

This means the Care portal currently represents one clinic context per session, not a list of all tenants where the same mobile number may exist.

### Why `Jeevanam Automation Lab` is not shown

The current session does not show `Jeevanam Automation Lab` because:

- the portal session was issued from the approved Demo Clinic access path
- the Automation Lab patient record exists, but it is not tied to the current Care session by an approved access request
- the portal token cannot carry multiple tenant contexts
- the current Care UI renders the active session tenant, not a cross-tenant clinic switcher

The underlying code also contains a same-mobile convenience expansion in `PatientPortalService.resolveAccessiblePatientAccesses(...)`, but that is a downstream data aggregation helper, not an authorization source for switching clinics.

### Safest authorization source for current Care account access

The safest existing authorization record for deciding whether a person may log in to Care is `patient_portal_access_requests`.

Reasoning:

- it is explicit
- it is tenant-scoped
- it already records approved / active / revoked access state
- it can be tied to a specific patient record when the request is activated

Same-mobile matching alone must not be used as authorization.

### Recommended future Care model

If multi-clinic Care is introduced later, the smallest safe model is:

- keep the current authenticated Care session as the primary trust anchor
- expose a patient-visible list of authorized clinic contexts
- source that list from approved patient portal access requests only as the login bootstrap, then derive entitlement from explicit server-verified patient/tenant links and patient-specific Health evidence
- let the patient switch between authorized clinics only
- scope doctors, appointments, prescriptions, labs, and bills to the selected authorized clinic context

### Security rules

- same phone number across tenants is not authorization
- private clinic/doctor visibility must require explicit server-side authorization
- public Discover publication must remain separate from Care authorization
- cross-tenant private records must remain hidden unless the tenant relationship is explicitly approved
- the current same-mobile aggregation helper must not be promoted into an authorization boundary

### Migration note

No database migration is required to document the current limitation.

If a future implementation adds a true multi-clinic authorization projection or persistent clinic-switching state, that change may require new schema or session payload work, but this diagnostic audit does not introduce such a change.

## 21. Multi-Clinic Care Context - Phase 1.5 Implementation

Phase 1.5 extends Care from a single-tenant patient portal into an authorized multi-clinic patient portal without changing Provider, Discover, or AIVA behavior.

Implemented authorization rule:

- The current Care session remains the trust anchor.
- Authorized clinic contexts are derived from approved, active `patient_portal_access_requests`.
- Same-mobile matching may help locate candidate requests, but it does not grant access by itself.
- A patient can switch only to a tenant that appears in the authorized clinic list and still has an active access request.
- Cross-tenant access remains blocked unless there is an explicit server-verified authorization record.

APIs changed or added:

- Added `GET /api/patient-portal/clinics`
- Added `POST /api/patient-portal/clinics/{tenantId}/switch`
- Kept `GET /api/patient-portal/clinic`
- Kept `GET /api/patient-portal/doctors`
- Kept `GET /api/patient-portal/doctors/{publicDoctorId}/slots`

Session and token decision:

- The portal remains session-based and tenant-scoped.
- Switching clinics issues a fresh patient portal token for the selected authorized tenant.
- The token still carries a single `tenantId` and one active `patientId` for that selected clinic context.
- No database migration was required for Phase 1.5.

Care UI changes:

- Dashboard now shows a "My Care Network" clinic switcher for authorized clinics.
- The current clinic remains the active patient context.
- Switching clinics updates the active Care workspace, including clinic, doctors, appointments, prescriptions, labs, and bills.
- Private Care clinics are shown inside Care only; Discover remains the public search path.

Security rules:

- The patient portal still requires authenticated patient session headers.
- Tenant isolation is preserved because private clinic and doctor resolution always uses the selected authorized tenant context.
- Public listing remains the authorization gate for public Discover visibility.
- Provider publication/moderation and public Discover projections are unchanged.
- Private Care authorization never depends on the same mobile number alone.

Tests added or updated:

- Controller test for `GET /api/patient-portal/clinics`
- Controller test for `POST /api/patient-portal/clinics/{tenantId}/switch`
- Service test for authorized clinic listing from active access requests
- Service test for safe tenant switching and new token issuance
- Service test for same-tenant private clinic visibility without `publicListingEnabled`
- Service test for same-tenant private booking

Explicitly unchanged:

- Provider onboarding workflow
- Platform Admin provider review
- Provider publication workflow
- Discover publication projection
- Discover public search rules
- AIVA / CareAI lookup and booking logic
- AIVA / CareAI behavior

## 22. Care Account Access vs Care Network Entitlement

### Purpose

Care needs two separate decisions:

1. Can this person log in to Care at all?
2. Which Health clinics and doctors may this authenticated Care patient access?

The current pilot `ACCESS_APPROVAL` flow answers only the first question. It is the Care account gate, not the full clinic entitlement model.

### Current temporary account-access model

- Patient requests Care access.
- Platform Admin approves the request.
- `patient_portal_access_requests` records the approval state.
- A portal session token is issued for the selected tenant and patient context.

This is the correct temporary pilot model and should remain in place for now.

### Future auth direction

Future Care authentication may move to one of several login modes:

- `ACCESS_APPROVAL`
- OTP
- username/password
- other approved mechanisms

That is separate from clinic entitlement. Login mode chooses whether the patient can enter Care; it does not by itself decide which clinics are safe to expose.

### Recommended separation

- `Care Account Access` = authentication / login gate
- `Care Network Entitlement` = which Health clinics/doctors the authenticated patient may see

The first can still be managed by `patient_portal_access_requests`.
The second should be derived from legitimate Health evidence and explicit server verification.

### Current code reality

The current code base already distinguishes some of this, but it still leans on `patient_portal_access_requests` for multi-clinic listing and switching.

Important current behaviors:

- `PatientPortalAccessRequestService.authenticate(...)` issues the Care session token after an approved request is validated
- `PatientPortalAccessRequestService.listAuthorizedClinics(...)` currently derives My Care Network from active access requests
- `PatientPortalAccessRequestService.switchAuthorizedClinic(...)` requires an active access request for the selected tenant
- `PatientPortalService.resolveAccessiblePatientAccesses(...)` expands by same mobile across tenants for downstream patient-data aggregation, but that is not an authorization boundary

### Evidence sources for Care Network entitlement

The current schema already exposes several tenant-scoped patient signals:

| Evidence source | Existing in code? | Tenant-scoped? | Patient-specific? | Strong enough for entitlement? | Risk / false-positive concern | Recommended weight |
|---|---:|---:|---:|---:|---|---|
| Active patient record | yes | yes | yes | no | Can be created administratively or duplicated; shared mobile can collide | Low |
| Approved patient portal access request | yes | yes | yes | no, login gate only | It proves login approval, not clinical relationship | High for login, low for entitlement |
| Verified mobile mapped to patient record | yes | yes | partially | no | Shared phones, recycled numbers, or family accounts can mislink | Low |
| Future scheduled appointment | yes | yes | yes | partial | A future booking can exist without a mature care relationship | Medium |
| Completed appointment | yes | yes | yes | yes | Strong operational care evidence | High |
| Consultation started / completed | yes | yes | yes | yes | Stronger than appointment alone | High |
| Prescription issued / finalized | yes | yes | yes | yes | Clear evidence of doctor-patient care interaction | High |
| Lab order / report / verification | yes | yes | yes | yes | Strong care evidence, especially when tied to consultation or appointment | High |
| Bill / receipt / payment for the patient | yes | yes | yes | yes, but secondary | Administrative billing can exist without a recent clinical encounter | Medium-high |
| Explicit patient-clinic relationship entity | no | n/a | n/a | n/a | Missing today; would be the cleanest long-term model | Future |

### Recommended minimum safe rule

The smallest practical rule for auto-entitling a clinic into My Care Network is:

- the Care account is authenticated and already approved for login
- the tenant is active
- there is a tenant-scoped patient record for the verified identity
- and there is at least one strong patient-specific Health evidence signal in that tenant, such as:
  - completed appointment
  - completed consultation
  - issued/finalized prescription
  - lab order / report
  - patient bill / receipt tied to the patient

This is safer than using same-mobile matching alone.

Future-friendly refinement:

- future appointment only can be treated as a weaker signal, not a standalone entitlement
- if the tenant has multiple active patient candidates for the same verified mobile and there is no explicit link, mark the tenant as `AMBIGUOUS` and do not auto-entitle it

### Eligibility states

Use conceptual states such as:

- `ELIGIBLE` - show the clinic in My Care Network and allow normal tenant-scoped Care access
- `AMBIGUOUS` - do not expose clinical data automatically; require confirmation or clinic-side linkage
- `NOT_ELIGIBLE` - no access
- `REVOKED` - remove the clinic from My Care Network and deny future switching

## 23. Phase 1.7 - Unified Care Discovery

Phase 1.7 adds a Care-hosted discovery surface for authenticated patients while preserving the existing Care Network entitlement model and the public Discover publication flow.

### What changed

- Care now hosts the normal patient discovery journey instead of sending authenticated users to Discover for routine search and profile viewing.
- The Care discovery surface combines:
  - `My Care Network` authorized clinics and doctors
  - published Discover doctors
  - published Discover clinics
  - published Discover hospitals
  - published specialities
  - published services
- The source of truth for public profiles remains Discover public catalog APIs.
- The source of truth for private patient context remains the Care patient portal APIs.

### Care-hosted routes

Authenticated Care users now navigate to Care routes such as:

- `/patient/doctors`
- `/patient/doctors/:doctorSlug`
- `/patient/clinics`
- `/patient/clinics/:clinicSlug`
- `/patient/hospitals`
- `/patient/hospitals/:hospitalSlug`
- `/patient/specialities`
- `/patient/specialities/:specialitySlug`
- `/patient/services`
- `/patient/services/:specialitySlug`

These routes consume existing public catalog APIs and patient portal APIs. No new public persistence model was introduced in Care.

### Source separation

Unified Care Discovery still keeps the data sources logically separate:

1. `My Care Network`
   - tenant-authorized Care clinics and doctors
   - tenant-scoped patient access and booking context
   - private Health booking path when the doctor is not public-bookable

2. `Public Discovery`
   - published doctors, clinics, hospitals, specialities, and services
   - public profile rendering only from published Discover data
   - call-to-book providers render a contact/profile panel instead of a dead-end slot flow

### Search and profile rules

- Search can combine Care Network and public Discover results inside one Care page.
- Care Network results should be preferred first when they are authorized for the current patient.
- Published Discover results remain visible inside Care when they match the search.
- Dedupe must use explicit linkage keys such as `publicDoctorId`, `clinicSlug`, or an equivalent explicit public linkage.
- Do not dedupe only by name.
- Do not expose unpublished providers from unrelated tenants.

### Booking action rules

Each Care discovery result must resolve to one primary action path:

- Care-authorized + online-bookable -> book through Care / Health
- Published Discover + online-bookable -> existing public booking flow
- Published Discover + call-to-book -> provider/contact panel
- Public profile only -> view profile

Public call-to-book providers should not show the normal slot-confirmation flow.

### Clinic and doctor profile presentation

- Public doctor and clinic profiles are rendered inside Care using published Discover projection data.
- Care should show the actual published clinic/practice name when available.
- If the published payload does not contain a safe clinic name, the UI should use a neutral fallback such as `Clinic details available on profile`.
- Do not show association labels such as `Primary` unless that is genuinely the published clinic name.
- Public phone/contact data must come only from the published projection.

### Discover fallback

- The low-priority fallback link to open Discover may remain as an escape hatch.
- It must not be the primary path for normal authenticated patient search or profile viewing.

### Security boundaries

- Care-hosted discovery does not relax tenant isolation.
- Public profile projection must remain published-only.
- Private tenant data must not be exposed through public search.
- Public search must not grant Care tenant access.
- The current Phase 1.6 clinic-switch authorization remains unchanged.

### Implementation status

- Backend API changes: not required for this phase
- Frontend changes: implemented in `web-care`
- Provider workflow: unchanged
- Discover publication workflow: unchanged
- Health↔Care entitlement: unchanged
- AIVA / CareAI: out of scope for this phase

### Family and shared-mobile risk

Same authenticated phone number is not safe enough by itself because:

- parents and children can share a phone
- spouses can share a phone
- a clinic can maintain two active patient records with the same mobile
- a phone number can be recycled

Therefore:

- mobile number may be used to discover candidate records only
- it must never grant Care network entitlement by itself
- `patientId`, linked access-request state, and patient-specific Health evidence should be used to confirm the correct tenant context

### Doctor visibility recommendation

Once a clinic is in My Care Network:

- show all active doctors in that authorized clinic by default
- keep booking scoped to that selected tenant
- do not require Discover publication for the clinic's internal doctors
- keep referral-only or specialty-restricted rules as a later policy layer, not the base entitlement rule

This is the simplest Phase 1 behavior that matches normal clinic care.

### Revocation precedence

Clinic access should be removed in this order of precedence:

1. explicit Care revocation or clinic-side access removal
2. tenant deactivation or clinic closure
3. patient record deactivation
4. loss of the verifying patient-tenant link
5. ambiguity caused by shared/recycled mobile numbers

When revocation happens, remove only that tenant from My Care Network; keep other authorized tenants intact.

### Platform Admin role

Platform Admin should remain the approver for Care account access only.

Platform Admin should not need to approve every clinic relationship or every doctor relationship if the clinic can be safely derived from server-verified Health evidence.

That is the scalable direction for Care.

### Implementation options

| Option | Description | Safe enough now? | Notes |
|---|---|---:|---|
| A | Approved access requests only | yes for login, no for full auto-entitlement | Current Phase 1.5 behavior |
| B | Approved access requests + verified Health evidence | yes | Recommended next step; no new schema required if existing encounters are enough |
| C | Explicit patient-clinic relationship projection/table | yes, later | Cleanest long-term model if the product needs durable switches, overrides, or clinic invitations |

### Recommendation

- Keep the current temporary account-access flow.
- Treat `patient_portal_access_requests` as the Care login gate.
- Derive Care Network entitlement from approved login context plus explicit Health evidence.
- Do not use same-mobile matching as authorization.
- Do not require a separate Platform Admin approval for every clinic if the patient already has a verified care relationship there.

### Summary decision

- strongest existing Health relationship signals: completed consultation, finalized prescription, lab order/report, and patient bill tied to the patient
- verified mobile alone is safe: no
- Platform Admin should remain per-account only: yes
- Phase 1.5 should be adjusted conceptually: yes, as a bootstrap layer, not as the final entitlement model
- DB migration needed now: not for this audit; a later explicit entitlement projection may need one
