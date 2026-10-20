# IR-01: Platform communication channel configuration

## Scope

Persist tenant-scoped communication channels, expose Platform Admin management APIs and UI, and provide a provider-neutral resolver for tenant-aware inbound and outbound voice flows.

## Ownership and placement

- Owning bounded context: communication configuration.
- Persistence: `communication-domain`.
- HTTP adapter: `api-bff` platform controller and DTOs.
- Frontend: the existing Platform Tenant Detail page.
- Runtime compatibility: existing process-level DotVoice configuration remains an explicit fallback; no existing communication-test or Engage dispatch path is replaced.

## Out of scope

Patient identification, AIVA tools, booking, Discover booking, Engage interactive response workflows, and production inbound routing.

## Invariants

- Only `PLATFORM_ADMIN` may mutate channels.
- Active inbound voice DIDs are unique globally after normalization.
- Resolution only considers active, enabled, direction-enabled channels.
- Outbound selection is location, clinic, then tenant scoped.
- Ambiguous same-scope outbound defaults fail safely.
- Provider secrets are never persisted or returned by the API.
