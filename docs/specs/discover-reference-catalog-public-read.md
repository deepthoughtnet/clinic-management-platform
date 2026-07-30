# Discover Reference Catalog Public Read

## Status

Approved.

## Scope

Jeevanam Discover exposes read-only catalog data for public discovery and provider onboarding.

This specification covers the non-sensitive reference catalog endpoints under:

- `GET /api/discover/reference/**`

These endpoints provide catalog values required by:

- public Discover pages
- provider registration
- provider onboarding
- public provider profile rendering
- doctor, clinic, and hospital search flows

## Ownership

- Owning bounded context: `discover-domain`
- API adapter: `api-bff`
- Frontend consumers: `web-discover`

## Security

- `GET /api/discover/reference/**` is publicly readable.
- Write operations under the same path remain protected.
- No management operation under the reference catalog path is made public.
- Reference catalog reads must not require tenant context, provider onboarding tokens, patient authentication, or Keycloak authentication.

## Compatibility

- Existing protected routes remain protected.
- Existing provider onboarding and provider ownership flows remain unchanged.
- No hardcoded frontend fallback reference data is introduced.

## Validation

- Anonymous requests to each public reference endpoint return HTTP 200.
- Protected nearby routes continue to return HTTP 401 for anonymous requests.
- Public catalog search/profile routes retain their existing behavior.
- Request-context handling skips tenant resolution for the public reference read path.

## File Ownership

- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/config/SecurityConfig.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/config/RequestContextConfig.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/discover/reference/DiscoverReferenceControllerTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/discover/reference/DiscoverReferenceSecurityTest.java`
- `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/platform/RequestContextFilterTest.java`
