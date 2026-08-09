# Jeevanam Doctor Practice Boundary and Coupling Review

## 1. Executive Summary

The current architecture does **not** persist a Healthcare-owned doctor-to-clinic relationship.

Healthcare owns:
- `tenants`
- `clinic_profiles`
- `doctor_profiles`
- appointments, consultations, prescriptions, billing, lab, pharmacy, vaccination

Discover owns:
- the public provider projection tables
- publication/version history
- slug aliases
- ownership and moderation workflows

Platform Provider Integration owns:
- operational public-to-platform link records
- booking capability / availability state
- connection lifecycle and auditability

Care currently routes booking by public identity inputs such as `clinicSlug`, `tenantId`, `clinicId`, and `publicDoctorId`. It resolves those inputs back to tenant-scoped Healthcare state and does not require a persisted Healthcare doctor-clinic foreign key.

The correct boundary is:
- keep Healthcare semantics frozen
- extend Discover/public projection additively if a doctor-practice association is needed
- keep Platform Provider Integration as the operational connection layer
- defer Care changes until a stable public association read model exists

Recommendation: **GO for additive Discover-side projection work only; NO-GO for any Healthcare schema reinterpretation or doctor-clinic FK retrofits at this stage.**

## 2. Healthcare Actual Model

### Tenant

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / active | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `TenantEntity` | `tenants` | `id` | n/a | n/a | n/a | `status`, module flags, `public_listing_enabled` | `code` unique | Identity domain |

Evidence:
- [TenantEntity](/home/iadmin/code/clinic-management-platform/backend/domains/identity-domain/src/main/java/com/deepthoughtnet/clinic/identity/db/TenantEntity.java)

### Clinic

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / active | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `ClinicProfileEntity` | `clinic_profiles` | `id` | `tenant_id` | n/a | n/a | `active`, `public_listing_enabled` | `tenant_id` unique | Clinic domain |

Important facts:
- `tenant_id` is the sole foreign key-like link.
- The unique constraint on `tenant_id` means there is at most one clinic profile per tenant today.
- Clinic is still a first-class entity separate from Tenant.

Evidence:
- [ClinicProfileEntity](/home/iadmin/code/clinic-management-platform/backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/db/ClinicProfileEntity.java)

### Doctor / User

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor/user ref | Status / active | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `DoctorProfileEntity` | `doctor_profiles` | `id` | `tenant_id` | none | `doctor_user_id` | `active`, `public_listing_enabled` | `(tenant_id, doctor_user_id)` unique | Clinic domain |

Important facts:
- There is **no `clinic_id`** on `doctor_profiles`.
- Operational membership is therefore inferred through the tenant scope.
- The profile stores doctor operational details such as specialization, qualification, fees, slug, and photo metadata.

Evidence:
- [DoctorProfileEntity](/home/iadmin/code/clinic-management-platform/backend/domains/clinic-domain/src/main/java/com/deepthoughtnet/clinic/clinic/db/DoctorProfileEntity.java)

### Appointment

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `AppointmentEntity` | `appointments` | `id` | `tenant_id` | none | `doctor_user_id` | `status`, priority, payment bypass fields | token unique per tenant/doctor/date | Appointment domain |

Evidence:
- [AppointmentEntity](/home/iadmin/code/clinic-management-platform/backend/domains/appointment-domain/src/main/java/com/deepthoughtnet/clinic/appointment/db/AppointmentEntity.java)

### Doctor Availability

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `DoctorAvailabilityEntity` | `doctor_availability` | `id` | `tenant_id` | none | `doctor_user_id` | `active` | slot unique per tenant/doctor/day/time window | Appointment domain |

Evidence:
- [DoctorAvailabilityEntity](/home/iadmin/code/clinic-management-platform/backend/domains/appointment-domain/src/main/java/com/deepthoughtnet/clinic/appointment/db/DoctorAvailabilityEntity.java)

### Doctor Unavailability

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `DoctorUnavailabilityEntity` | `doctor_unavailability` | `id` | `tenant_id` | none | `doctor_user_id` | `active` | none beyond indexes | Appointment domain |

Evidence:
- [DoctorUnavailabilityEntity](/home/iadmin/code/clinic-management-platform/backend/domains/appointment-domain/src/main/java/com/deepthoughtnet/clinic/appointment/db/DoctorUnavailabilityEntity.java)

### Consultation

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `ConsultationEntity` | `consultations` | `id` | `tenant_id` | none | `doctor_user_id` | `status`, `completed_at` | `(tenant_id, appointment_id)` unique | Consultation domain |

Evidence:
- [ConsultationEntity](/home/iadmin/code/clinic-management-platform/backend/domains/consultation-domain/src/main/java/com/deepthoughtnet/clinic/consultation/db/ConsultationEntity.java)

### Prescription

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `PrescriptionEntity` | `prescriptions` | `id` | `tenant_id` | none | `doctor_user_id` | `status`, correction/supersession lifecycle | `prescription_number` unique per tenant | Prescription domain |

Evidence:
- [PrescriptionEntity](/home/iadmin/code/clinic-management-platform/backend/domains/prescription-domain/src/main/java/com/deepthoughtnet/clinic/prescription/db/PrescriptionEntity.java)

### Billing

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Uniques | Owner |
|---|---|---:|---|---|---|---|---|---|
| `BillEntity` | `bills` | `id` | `tenant_id` | none | indirect via consultation/appointment | `status`, financial totals, notes | bill number unique per tenant | Billing domain |
| `ReceiptEntity` | `bill_receipts` | `id` | `tenant_id` | none | indirect via bill/payment | receipt lifecycle is simple, immutable record | receipt number unique per tenant | Billing domain |
| `PaymentEntity` | `bill_payments` | `id` | `tenant_id` | none | indirect via bill | payment mode/date/notes | no extra unique key shown | Billing domain |

Evidence:
- [BillEntity](/home/iadmin/code/clinic-management-platform/backend/domains/billing-domain/src/main/java/com/deepthoughtnet/clinic/billing/db/BillEntity.java)
- [ReceiptEntity](/home/iadmin/code/clinic-management-platform/backend/domains/billing-domain/src/main/java/com/deepthoughtnet/clinic/billing/db/ReceiptEntity.java)
- [PaymentEntity](/home/iadmin/code/clinic-management-platform/backend/domains/billing-domain/src/main/java/com/deepthoughtnet/clinic/billing/db/PaymentEntity.java)

### Lab

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Notes | Owner |
|---|---|---:|---|---|---|---|---|---|
| `LabOrderEntity` | `lab_orders` | `id` | `tenant_id` | none | `doctor_user_id`, plus `requestedByInternalDoctorId` and external doctor snapshots | `status`, sample/result/report lifecycle | carries external clinic name snapshot, not a clinic FK | Lab API/BFF module |

Evidence:
- [LabOrderEntity](/home/iadmin/code/clinic-management-platform/backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/lab/db/LabOrderEntity.java)

### Pharmacy

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Notes | Owner |
|---|---|---:|---|---|---|---|---|---|
| `PharmacySaleEntity` | `pharmacy_sales` | `id` | `tenant_id` | `location_id` only | none | `status`, payment totals | no doctor-clinic relationship persisted | Inventory domain |

Evidence:
- [PharmacySaleEntity](/home/iadmin/code/clinic-management-platform/backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/db/PharmacySaleEntity.java)

### Vaccination

| Entity | Table | PK | Tenant ref | Clinic ref | Doctor ref | Status / lifecycle | Notes | Owner |
|---|---|---:|---|---|---|---|---|---|
| `PatientVaccinationEntity` | `patient_vaccinations` | `id` | `tenant_id` | none | `administeredByUserId`, `verifiedByUserId` | `verified_status`, reminder/AEFI workflow | snapshots bill, inventory, external place | Vaccination domain |

Evidence:
- [PatientVaccinationEntity](/home/iadmin/code/clinic-management-platform/backend/domains/vaccination-domain/src/main/java/com/deepthoughtnet/clinic/vaccination/db/PatientVaccinationEntity.java)

### Explicit answers

A. Is Tenant always equivalent to one Clinic today?
- Operationally, one tenant has at most one `clinic_profiles` row today.
- Conceptually, Tenant and Clinic are still distinct entities.

B. Can one tenant contain more than one clinic in current schema/runtime?
- No. The schema enforces one clinic profile per tenant.

C. Is Clinic a first-class entity separate from Tenant?
- Yes.

D. Does DoctorProfile reference tenant_id, clinic_id, user_id, anything else?
- `tenant_id` and `doctor_user_id` only, plus operational profile fields.
- No `clinic_id`.

E. How is a doctor operationally associated with a clinic?
- Through the tenant scope and the fact that the tenant currently has one clinic profile.

F. Is that association explicit or inferred through tenant scope?
- Inferred.

G. Which workflows assume that doctor belongs to the current clinic?
- Appointment creation, availability lookup, consultation creation, prescription generation, billing/receipt generation, lab orders, patient portal booking, public listing sync, public provider facts, Care discovery, and provider connection suggestions.

H. Which code paths would break if doctor-to-clinic semantics changed?
- Any path that resolves doctor by `tenant_id + doctor_user_id` and assumes a single clinic context, especially booking and public projection code.

## 3. Healthcare Coupling Map

| Component | Assumption | Evidence | Risk if changed | Must preserve? |
|---|---|---|---|---|
| `doctor_profiles.tenant_id` | Doctor membership is tenant-scoped | Doctor profile entity and all downstream services query by tenant | High: breaks all tenant-scoped lookups | Yes |
| `clinic_profiles.tenant_id` unique | One clinic per tenant | Unique constraint on `clinic_profiles.tenant_id` | High: changes entire clinic model | Yes |
| `appointments.tenant_id + doctor_user_id` | Appointment belongs to tenant clinic context | Appointment entity and booking flow | High | Yes |
| `doctor_availability.tenant_id + doctor_user_id` | Availability is tenant-scoped | Availability entity and slot search | High | Yes |
| `consultations.tenant_id + doctor_user_id` | Consultation belongs to tenant clinic context | Consultation entity | High | Yes |
| `prescriptions.tenant_id + doctor_user_id` | Prescription belongs to tenant clinic context | Prescription entity | High | Yes |
| `bills.tenant_id` | Billing is tenant-scoped | Bill entity and receipt generation | High | Yes |
| `lab_orders.tenant_id + doctor_user_id` | Lab order belongs to tenant clinic context | Lab order entity | Medium | Yes |
| `patient_vaccinations.tenant_id` | Vaccination is tenant-scoped | Vaccination entity | Medium | Yes |
| Patient portal booking by `clinicSlug` | clinicSlug resolves the tenant | `PatientPortalService.resolveBookingTenantId()` | High | Yes |
| Public doctor listing by tenant | One doctor belongs to current clinic | `HealthcarePublicListingSyncService.syncDoctor()` | High | Yes |

Classification:
- Hard coupling: tenant-scoped doctor ownership and booking/clinical flows
- Soft coupling: public search and display convenience fields
- UI convenience: labels, subtitles, summary text
- Derived/projected: slugs, booking mode, doctor count, practice references

## 4. Discover Public Model

### Durable tables

| Table | Purpose | Durable identity | Derived fields |
|---|---|---|---|
| `discover_public_provider_profiles` | Current public projection per provider | `provider_id` | display fields, booking mode, counts, document ids |
| `discover_public_provider_profile_versions` | Immutable version history | `provider_id`, `version_number` | snapshot JSON |
| `discover_public_provider_profile_slugs` | Slug alias history | `slug`, `provider_id` | active alias flag |
| `discover_public_profile_ownerships` | Ownership evidence / lifecycle | `public_profile_reference` | active/verified state |
| `discover_public_profile_memberships` | Provider account membership for public profile | `public_profile_reference`, `provider_account_id` | role/status |
| `discover_public_profile_submissions` | Moderation submissions | `submission_reference`, `public_profile_reference` | moderation snapshots |
| `discover_public_profile_publications` | Publication lifecycle | `publication_reference`, `public_profile_reference` | current/public state |

### PublicProviderProfile facts

`PublicProviderProfileModels.PublicProviderProfileSnapshot` is the public source structure used to project clinics, doctors, and hospitals. It contains:
- `providerId`
- `providerType`
- `sourceSystem`
- `referenceNumber`
- `displayName`
- `legalName`
- `canonicalSlug`
- `locations`
- `logoDocumentId`
- `coverImageDocumentId`
- `doctorPhotoDocumentId`
- `bookingMode`
- `doctorCount`
- `publishedAt`
- `publicPath`

The Discover projection stores a durable provider record keyed by `provider_id`; everything else is projection data.

### PublicDoctor

A public doctor record does **not** have a persisted clinic FK.

Current public doctor association is derived from:
- source tenant
- source doctor/user identity
- public profile location snapshots
- synthetic practice references generated from location data
- platform link records when present

The public API fields are not all durable:
- durable: `publicDoctorId`, `doctorSlug`, `publicPath`, `doctorDisplayName`, `photoUrl`, `bookingMode`, `bookingReference`
- synthetic/derived: `clinicSlug`, `clinicDisplayName`, `clinics[]` entries, practice-specific `bookingReference`

Exact source in the current API:
- `PublicDoctorSummaryResponse.clinicSlug` is derived in `PublicCatalogFacade.toDoctorSummary()` from the first location label, then area/city/display name fallback.
- `PublicDoctorDetailResponse.clinics[]` is synthesized in `PublicCatalogFacade.toDoctorDetail()` from the first location only.
- `PublicDoctorDetailResponse.bookingReference` is resolved through `ProviderLinkingService` if a platform link exists, otherwise from a synthetic practice reference.

### PublicClinic

The public clinic record is directly projected from the Discover public profile row.

Exact source in the current API:
- `clinicSlug` = canonical slug
- `clinicName` / `clinicDisplayName` = public profile display name
- `bookingMode` = platform booking capability if a link exists; otherwise projected booking mode or call-to-book fallback
- `doctor_count` = `discover_public_provider_profiles.doctor_count`
- `doctors[]` in clinic detail is currently `List.of()` in `PublicCatalogFacade.toClinicDetail()`

### PublicDoctor counts and booking mode

`doctor_count` on clinic records is derived from the Healthcare sync snapshot:
- `HealthcarePublicListingSyncService.syncClinic()` computes `doctors.size()` from active public-listed doctors in the tenant

`bookingMode` is derived in multiple ways:
- Discover/Healthcare projection sets an initial mode
- Platform Provider Integration can override it through an active link’s booking capability
- Public site falls back to call-to-book when a phone number is present

## 5. Existing Sync Pipeline

### Healthcare -> Discover

Clinic:
`ClinicProfileEntity`
→ `HealthcareClinicPublicListingChangedEvent`
→ `HealthcareClinicPublicListingChangedEventListener`
→ `HealthcarePublicListingSyncService.syncTenant()` / `syncClinic()`
→ `ProviderPublicProfileService.upsertLifecycleProfile()`
→ `discover_public_provider_profiles`, `discover_public_provider_profile_versions`, `discover_public_provider_profile_slugs`
→ `PublicCatalogFacade`

Doctor:
`DoctorProfileEntity`
→ `HealthcareDoctorPublicListingChangedEvent`
→ `HealthcareDoctorPublicListingChangedEventListener`
→ `HealthcarePublicListingSyncService.syncTenant()` / `syncDoctor()`
→ `ProviderPublicProfileService.upsertLifecycleProfile()`
→ discover public profile tables
→ `PublicCatalogFacade`

### Startup reconciliation

`HealthcarePublicListingStartupReconciler`:
- reconciles current publication lifecycle state
- iterates active tenants
- calls `HealthcarePublicListingSyncService.syncTenant()`

### Manual reconcile

`HealthcarePublicListingSyncController`:
- `POST /api/platform/discover/public-listings/reconcile?tenantId=...`
- uses the same `HealthcarePublicListingSyncService.syncTenant()` path

### Media sync

Clinic logo:
- `HealthcarePublicListingSyncService.syncClinicLogo()`
- reads `ClinicProfileEntity.logoDocumentId`
- stores the media through `ProviderPublicProfileService.upsertPublishedMedia()`

Doctor photo:
- `HealthcarePublicListingSyncService.syncDoctorPhoto()`
- reads configured doctor photo from Healthcare
- stores the media through `ProviderPublicProfileService.upsertPublishedMedia()`

### IDs carried across the boundary

| ID | Source identity | Public identity | Association candidate | Implementation detail |
|---|---|---|---|---|
| tenant ID | yes | sometimes as source reference | yes | no |
| clinic ID | yes | yes for public clinic provider_id | yes | no |
| doctor user ID | yes | yes for public doctor provider_id | yes | no |
| doctor profile ID | yes | no | no | yes, for sync bookkeeping |
| public profile provider_id | no | yes | yes | no |
| platform link id | no | no | yes | yes |
| booking reference | no | yes | yes | yes |

## 6. Platform Provider Integration Responsibility

### What the layer owns

`PublicClinicPlatformLinkEntity` and `PublicDoctorPracticePlatformLinkEntity` are **operational connection records**:
- link lifecycle
- booking capability
- availability state
- source system and source revision
- tenant reference and platform clinic reference
- audit trail and reconciliation

### What `PublicDoctorPracticePlatformLinkEntity` actually links

It links:
- `public_doctor_reference`
- `public_practice_reference`
- `tenant_reference`
- `platform_clinic_reference`
- `tenant_doctor_user_reference`
- optional `tenant_doctor_profile_reference`

This is not a Healthcare doctor-clinic membership table.

### Why it is not an authoritative replacement for Healthcare membership

- It is a platform link, not the source-of-truth clinic roster.
- It is keyed by public identity plus operational source references.
- It already carries connection status and booking capability, which are operational concerns.
- It can be reused as a booking/connection projection, but not as the canonical Healthcare association.

### Can a new association duplicate it?

Yes, if the new table tries to become another source of truth for doctor membership.

No, if it is introduced as a derived Discover association read model that references public IDs and source references.

## 7. Care Coupling

Care currently assumes a single clinic context when booking a doctor.

Important facts from `PatientPortalService`:
- `doctorSlots()` and `bookAppointment()` accept `clinicSlug`, `tenantId`, and `clinicId`
- `resolveBookingTenantId()` resolves tenant from explicit references or clinic slug
- `verifyPublicBookingClinic()` checks the resolved clinic is active and publicly listed
- `resolveBookingDoctor()` requires the doctor to exist in the resolved tenant
- `debugBookingResolution()` logs `doctorBelongsToClinic=true`, confirming the assumption

This means:
- `clinicSlug` is a public identity input used operationally
- the current booking flow assumes one doctor maps to the selected clinic context
- Care does not currently consume a separate doctor-practice association table

What can stay unchanged later:
- slot lookup
- appointment creation
- patient verification
- consultation core logic

What would need a later read-model change:
- any UI or lookup that needs a doctor to expose more than one practice
- any search result that needs multiple practice choices for the same doctor

## 8. Existing Doctor/Practice Relationship Candidates

Candidate types found in the codebase:
- `PublicDoctorPracticePlatformLinkEntity`
- `PublicClinicPlatformLinkEntity`
- `LocalDiscoverCatalogAdapter.practiceSummaries()`
- `ProviderConnectionsService` public profile / link matching
- `PublicProfileOwnershipEntity`
- `PublicProfileMembershipEntity`

Classification:

| Candidate | Purpose | Public vs operational | Reusable? |
|---|---|---|---|
| `PublicDoctorPracticePlatformLinkEntity` | operational doctor-practice connection | operational | reusable for booking/linking, not as Healthcare source of truth |
| `PublicClinicPlatformLinkEntity` | operational clinic connection | operational | reusable for clinic link state |
| `LocalDiscoverCatalogAdapter.practiceSummaries()` | synthesize practice entries from doctor locations | public identity projection | reusable for discovery |
| `PublicProfileOwnershipEntity` | ownership evidence | public governance | not a clinic membership record |
| `PublicProfileMembershipEntity` | public profile membership | public governance | not a clinic membership record |

The closest existing concept is the platform link table, but it is not the authoritative Healthcare doctor-practice relationship.

## 9. Green Valley Runtime Mapping

### Healthcare rows

Observed live tenant and clinic rows:
- Green Valley tenant: `407dbc68-107d-4f64-83c8-6499e50e5c78`
- Jeevanam tenant: `4b1e2915-6d35-4a00-b9f8-2f0cc3bccb1f`

Observed live doctor rows:
- Green Valley Amit doctor: `ff4d7d2a-401a-4993-9814-afe2863275b6`
- Jeevanam Amit doctor: `23cf0f04-3152-46ef-a0f6-3b243f90bbc5`

Observed live clinic rows:
- Green Valley clinic: `fb6977b3-683b-40a3-95b8-05ffbad1dac0`
- Jeevanam clinic: `4db5fc3b-9c5f-4355-b69b-618446159201`

These are separate identities:
- different tenant ids
- different doctor user ids
- different clinic ids
- different public provider ids
- different slugs

### Discover rows

Green Valley:
- clinic public provider row exists
- doctor public provider row exists
- public booking mode differs from Jeevanam

Jeevanam:
- clinic public provider row exists
- doctor public provider row exists

Observed public provider rows:
- Green Valley clinic public row: `provider_id=fb6977b3-683b-40a3-95b8-05ffbad1dac0`, `canonical_slug=green-valley-family-clinic`, `booking_mode=CALL_TO_BOOK`, `publication_status=PUBLISHED`
- Green Valley doctor public row: `provider_id=ff4d7d2a-401a-4993-9814-afe2863275b6`, `canonical_slug=amit-verma-2`, `booking_mode=CALL_TO_BOOK`, `publication_status=PUBLISHED`
- Jeevanam clinic public row: `provider_id=4db5fc3b-9c5f-4355-b69b-618446159201`, `canonical_slug=jeevanam-family-clinic-local`, `booking_mode=ONLINE_BOOKING`, `publication_status=PUBLISHED`
- Jeevanam doctor public row: `provider_id=23cf0f04-3152-46ef-a0f6-3b243f90bbc5`, `canonical_slug=amit-verma`, `booking_mode=ONLINE_BOOKING`, `publication_status=PUBLISHED`

### Publication lifecycle

Green Valley clinic:
- ownership exists and is verified
- one current publication exists
- version history exists

### Why they remain separate identities

Because the persisted keys are different at every layer:
- Healthcare tenant
- Healthcare clinic profile
- Healthcare doctor profile
- Discover public provider profile
- Discover slug alias

The shared display name `Amit Verma` is only a label collision.

## 10. Proposed Additive Association Feasibility

Proposed table:

```text
DiscoverDoctorPracticeAssociation
id
publicDoctorReference
publicPracticeReference
practiceType
sourceSystem
sourceDoctorReference
sourcePracticeReference
active
publiclyVisible
associationStatus
createdAt
updatedAt
```

### Feasibility assessment

Durable public doctor reference:
- yes

Durable public practice reference:
- partially yes, but it is currently synthetic in some paths and derived from location/public profile state

Stable source doctor reference:
- yes, in Healthcare it is `doctor_user_id`

Stable source practice reference:
- yes for the tenant clinic, but not as a separate multi-clinic Healthcare practice id today

Can one doctor have N rows safely?
- yes, if the association is additive and versioned

Uniqueness needs:
- `(publicDoctorReference, publicPracticeReference, sourceSystem)` or a stricter natural key including source references if the design must preserve history

History needs:
- yes, because practice membership and publication status can change independently

Delete/deactivate semantics:
- prefer soft-deactivate with history retained

### Minimum schema recommendation

If the proposed table is too broad, the smallest safe shape is:
- `id`
- `publicDoctorReference`
- `publicPracticeReference`
- `sourceSystem`
- `sourceDoctorReference`
- `sourcePracticeReference`
- `active`
- `createdAt`
- `updatedAt`

Leave `practiceType`, `publiclyVisible`, and `associationStatus` to follow-up if the first additive projection is accepted.

## 11. Must-Not-Change Boundaries

These Healthcare semantics should remain frozen:
- `doctor_profiles` stays tenant-scoped with no `clinic_id`
- `clinic_profiles` remains one row per tenant
- appointment, consultation, prescription, billing, lab, vaccination stay tenant-scoped
- tenant_id should not be reinterpreted as a hidden clinic id
- public listing sync must continue to source from Healthcare state
- existing patient portal booking behavior must remain functional

## 12. Safe Extension Points

These areas can be extended additively:
- Discover public projection tables
- slug aliasing and publication history
- platform provider links
- public catalog response shaping
- provider connection read models
- patient portal read-side lookup, if it only consumes new read models

## 13. Risk Matrix

| Change | Risk | Impacted modules | Mitigation |
|---|---|---|---|
| Changing Healthcare doctor schema | Very high | clinic, appointment, consultation, prescription, billing, lab, vaccination, discover sync, care | Do not add clinic FK to core Healthcare doctor tables |
| Reinterpreting `tenant_id` | Very high | identity, clinic, appointment, care, discover | Keep tenant scope semantics unchanged |
| Adding Discover association table | Medium | discover, provider integration, public catalog, care read-side | Make it additive and derived |
| Changing sync upsert semantics | Medium-high | api-bff discover sync, discover public profile | Preserve existing provider_id and canonical slug rules |
| Changing public clinic doctor projection | Medium | public catalog, web-discover, care | Keep current fields stable and add new read model fields only |
| Changing Care clinic filtering | High | patient portal, careai, booking | Defer until the new association is proven by regression tests |
| Modifying platform links | Medium | provider integration, public catalog, booking resolution | Treat as operational connection data, not source-of-truth membership |

## 14. Final Recommended Architecture

### Boundary statement

Healthcare owns the clinical and billing source records.

Discover owns public identity projection and publication history.

Platform Provider Integration owns operational connection and booking capability.

Care consumes public identities and operational connection state, but should not own or infer new doctor-clinic persistence.

### Recommendation

- Add the doctor-practice association only as a Discover-side or platform-side additive projection.
- Do not modify Healthcare core schemas to store a doctor-clinic FK.
- Do not change Care booking core semantics yet.
- Use the existing public location model and platform links as the source for any new public association.

## 15. Revised Batch Plan

### Batch 1
- automatic Healthcare doctor -> Discover sync
- clinic admin effective connection projection

Assessment:
- safe only if this remains projection work
- unsafe if it introduces a Healthcare membership schema

### Batch 2
- Discover-side doctor-practice association
- clinic doctor projection/count

Assessment:
- safe additive target

### Batch 3
- association lifecycle / multi-practice

Assessment:
- safe if the new table is treated as a read model with soft deactivation

### Batch 4
- Care consumes the association
- end-to-end booking validation

Assessment:
- do this only after the association is stable and public responses are regression-tested

## 16. Explicit GO / NO-GO Recommendation

### GO
- Additive Discover-side public association work
- Additive platform connection projection work
- Read-model changes that preserve Healthcare schema and semantics

### NO-GO
- Changing Healthcare doctor schema to add clinic membership
- Reinterpreting tenant_id as a clinic relationship
- Replacing the current booking/availability/consultation semantics
- Destructively changing Care booking filters before a stable public association exists

