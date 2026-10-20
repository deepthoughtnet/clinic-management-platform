# IR-03B — Inbound patient-identification branching and interaction audit

Status: Approved

## Ownership and placement

- Owning bounded context: Communication.
- Persistence: `communication-domain`, table `communication_interactions`.
- Inbound adapter/orchestration: `api-bff` communication test / DotVoice inbound flow.
- Migration owner: `api-bff` Flyway migrations, using the repository-wide next version.
- Frontend: Platform Communication Test read-only details.

## Scope

After DID routing and tenant-scoped phone identification, the call receives an explicit
conversation party state: `IDENTIFIED_PATIENT`, `GUEST_CALLER`, or `PATIENT_UNRESOLVED`.
Only `SINGLE_MATCH` carries a patient ID and uses the deterministic name greeting.
`NO_MATCH` and lookup failures use the generic guest greeting. `MULTIPLE_MATCHES` uses a
deterministic clarification prompt and never selects a candidate.

The interaction record is a non-clinical, best-effort durable audit/usage record keyed
uniquely by `providerCallId`. It stores lifecycle, routing, identification outcome,
duration, transcript presence, and provider outcome fields; it does not store transcript
content, credentials, authentication state, or medical details.

## Lifecycle and safety

The record is created on inbound registration and updated for routing/identification,
answer, transcript presence, and stream completion/failure. Duplicate provider events
reuse the unique provider call record. Persistence errors are logged with safe metadata
and do not terminate the provider websocket flow.
