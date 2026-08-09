# Discover Clinic Booking Entry

## Status

Approved implementation spec for DISC-UAT-CLINIC-001.

## Scope

Complete clinic-level booking entry in Jeevanam Discover without introducing a clinic appointment aggregate.

In scope:

- derive public clinic booking capability from authoritative doctor/practice booking capability
- expose associated clinic doctors as a first-class section on clinic profile pages
- keep clinic listing and clinic detail booking capability consistent
- preserve existing doctor-first Care handoff semantics
- keep clinic-level Care handoff on clinic context only

Out of scope:

- Healthcare persistence changes
- Discover doctor-practice association redesign
- Care booking-state redesign
- new clinic appointment ownership
- slot generation or appointment creation changes

## Ownership

- Public clinic projection and clinic profile rendering: `discover-domain` and `backend/api/api-bff`
- Public clinic browser UI: `web-discover`
- Care handoff contract: existing patient-booking route, unchanged unless an explicit defect is proven

## Capability Rule

A public clinic is considered online-bookable when at least one published, active doctor/practice association for that clinic resolves to `ONLINE_BOOKING`.

The clinic-level CTA must then route patients into Care with clinic context only:

- `clinicSlug=<public clinic slug>`

Doctor-level CTAs from the clinic profile may include both:

- `doctorId=<stable publicDoctorId>`
- `clinicSlug=<public clinic slug>`

## Public Profile Rule

Clinic profile pages must render a first-class "Doctors at this clinic" section from the association-backed `doctors[]` list already projected on clinic detail responses.

The section must not infer membership from legacy singular clinic fields or doctor name matching.

## Validation

- backend tests for clinic capability derivation and association-backed doctor projection
- browser source/regression tests for clinic card and clinic profile copy
- build verification for affected frontend/backend modules
