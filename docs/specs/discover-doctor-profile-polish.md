# Discover Doctor Profile Production Polish

## Status
Proposed

## Scope
Frontend-only polish for `web-discover`.

## Goal
Upgrade the doctor directory card and doctor public profile to a production-grade presentation without changing routes, APIs, booking flow, authentication, search, filters, or provider onboarding.

## In scope
- Doctor directory cards show rating and review summary.
- Doctor profile hero shows rating, review count, recommendation rate, and verification badges.
- Doctor profile supports richer booking, availability, reviews, services, working hours, gallery, location, breadcrumb, related doctors, and related specialities.
- Use reusable frontend components and realistic sample data until backend review/rating data exists.
- Preserve clinic and hospital behavior.

## Out of scope
- Backend API changes.
- Routing changes.
- Search/filter changes.
- Booking flow changes.
- Authentication or provider onboarding changes.
- Public URL changes.

## Frontend ownership
- Area: `web-discover`
- Data source: existing public catalog responses plus local doctor-profile sample data
- Dependencies: current React/MUI/router stack and existing Discover styles/components

## Planned files
- `web-discover/src/components/discovery/DoctorProfileExperiences.tsx`
- `web-discover/src/components/discovery/PublicProviderProfile.tsx`
- `web-discover/src/components/directory/DirectoryComponents.tsx`
- `web-discover/src/pages/discovery/PublicDiscoveryPages.tsx`
- `web-discover/src/styles.css`
- `web-discover/test/discover-doctor-profile-polish.test.mjs`
