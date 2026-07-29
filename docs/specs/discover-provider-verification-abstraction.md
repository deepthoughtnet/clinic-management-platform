# Discover Provider Verification Abstraction

## Status

Approved for implementation foundation.

## Scope

Implement a reusable verification capability for Jeevanam Discover that supports:

- provider registration email verification
- provider registration phone verification
- passwordless provider login by email OTP
- passwordless provider login by phone OTP
- future step-up verification for sensitive provider actions

The verification engine must use real challenge persistence, hashing, expiry, attempts, resend cooldown, and replay prevention in every environment.

Out of scope:

- a second simplified mock verification workflow
- hard-coded universal OTPs
- public exposure of OTP values in production
- Healthcare tenant user management
- unrelated provider approval, map, branding, or document-review changes

## Ownership

- Owning bounded context: Discover
- Verification engine / challenge persistence: `discover-domain`
- HTTP transport, provider session cookies, and auth boundary: `api-bff`
- Delivery adapters: existing notification/messaging SPI implementations where suitable, with Discover-specific verification adapters only if the SPI cannot represent the needed channel/purpose
- Migration owner: Discover verification/session persistence, executed via Flyway in `api-bff`
- Frontend: `web-discover`

## Core Rules

- The same verification engine must power registration and login.
- Verification challenges are typed by purpose and channel.
- Login verification must resolve a stable Discover provider account before issuing a session.
- Provider onboarding must not generate or hash OTPs directly.
- Controllers must not directly access repositories or storage.
- Local/UAT must use the same persistence and verification lifecycle as production.
- Any development code exposure must be explicitly disabled in production serialization.

## Supported Purposes

- `PROVIDER_REGISTRATION_EMAIL`
- `PROVIDER_REGISTRATION_PHONE`
- `PROVIDER_LOGIN_EMAIL`
- `PROVIDER_LOGIN_PHONE`

The design must be extensible for future purposes such as profile change verification and ownership transfer.

## Supported Channels

- `EMAIL`
- `SMS`

## Persistence

The verification capability owns typed challenge records and any lightweight Discover provider account linkage needed for login ownership.

Expected table families:

- verification challenges
- provider account / ownership linkage
- provider session or session support data when server-side persistence is required

No plaintext verification codes are stored.

## API Surface

Provider registration verification:

- request verification challenge
- verify challenge
- expose readiness / status

Provider login:

- request login challenge
- verify challenge
- establish provider session
- logout
- session / account resolution endpoints as needed by the workspace

## Delivery Strategy

Delivery providers must be configuration-driven.

Production:

- send email codes through configured email delivery
- send phone codes through configured SMS delivery

Local/UAT:

- use the same verification engine and persistence path
- expose the generated code only through an explicitly enabled non-production mechanism

## Frontend Scope

`web-discover` must provide:

- provider registration contact verification UI driven by the shared verification API
- a separate provider login flow for email or phone OTP
- a minimal provider workspace after login

## Validation Expectations

Backend validation must verify:

- purpose/channel matching
- recipient normalization
- expiry
- attempt limits
- resend cooldown
- replay prevention
- ownership binding for login and onboarding
- safe public responses that do not leak account existence

Frontend validation must verify:

- no browser alerts
- no production development-code display
- clear resend and expiry states
- provider login is separate from patient login
- provider onboarding continues to support contact verification and draft submission readiness

## Test Expectations

Required tests for implementation:

- challenge generation, hashing, expiry, attempt, resend, and replay tests
- provider registration verification regression tests
- provider login verification and session tests
- local/UAT development-code gating tests
- production adapter readiness tests
- ownership and authorization tests
- frontend provider login and verification tests
- regression tests for provider onboarding, public profiles, and existing Discover review flows

## File Ownership Map

- `backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/verification/**`
- `backend/domains/discover-domain/src/test/java/com/deepthoughtnet/clinic/discover/verification/**`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/verification/**`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/session/**`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/discover/verification/**`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/discover/session/**`
- `backend/api/api-bff/src/main/resources/db/migration/**`
- `web-discover/src/api/providerAuth.ts`
- `web-discover/src/pages/provider/ProviderLoginPage.tsx`
- `web-discover/src/pages/provider/ProviderWorkspacePage.tsx`
- `web-discover/src/pages/provider/ProviderOnboardingPage.tsx`

