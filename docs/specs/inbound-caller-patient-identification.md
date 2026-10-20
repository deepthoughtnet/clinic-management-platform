# IR-03A: Inbound caller patient identification

## Status

Approved implementation scope for tenant-scoped, read-only patient
identification in the Platform Communication Test inbound voice flow.

## Scope

After IR-02 resolves the inbound DID to a tenant, normalize the caller phone,
look up active patients within that tenant only, and personalize the first
deterministic Hindi greeting when exactly one patient is found. The in-memory
call context remains keyed by provider `call_id`.

Identification is not authentication. This milestone does not add login, OTP,
Patient Portal sessions, appointment workflows, AIVA, patient creation, or
database mutations.

## Ownership and boundaries

- Patient lookup remains owned by `patient-domain` and reuses its existing
  tenant-scoped active-mobile repository query.
- Inbound voice context, identification state, greeting orchestration, and
  Communication Test API mapping remain in `api-bff`.
- The Communication Test UI renders read-only identification details.
- No migration or persistent call/patient context is introduced.

## Identification rules

- Lookup starts only after DID routing is `RESOLVED` and `tenantId` is present.
- Exactly one active tenant-scoped match produces `SINGLE_MATCH` and a
  first/preferred name greeting.
- Zero matches produce `NO_MATCH`; multiple matches produce
  `MULTIPLE_MATCHES` without selecting a patient.
- Normalization or repository failures produce `LOOKUP_FAILED`; the call stays
  operational and uses the generic greeting.
- Caller phone values are masked in logs. Patient identity is never written to
  global request context and no authentication status is added.
