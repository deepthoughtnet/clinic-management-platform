# AIVA V2 Voice Phase F — Canonical Availability Context

## Placement and dependency plan

- Owning context: Patient Portal AIVA V2 and Voice availability orchestration.
- API module: `backend/api/api-bff`; this change propagates trusted inbound tenant context to existing availability adapters.
- Persistence owner: existing `appointment-domain` `AppointmentService`; no persistence changes.
- Migration owner: none.
- Frontend area: none.
- Allowed dependencies: CommunicationTestService/Voice runtime → V2 adapter/kernel → AIVA booking/read-only tools → existing PatientPortalService or HealthcareAiReadOnlyToolGateway → AppointmentService.
- Planned files: `AivaV2BookingTools`, `AivaV2TransactionalKernel`, `HealthcareAiReadOnlyToolGateway`, the narrow Hindi provider/date-token parser, and focused API tests.

## Decision

The trusted tenant on `SessionProjection` is authoritative for Voice booking availability. The kernel passes that tenant explicitly to booking tools; a provider's optional tenant metadata cannot replace it. A conflicting provider tenant fails closed. Voice availability uses a tenant-explicit `PatientPortalService.doctorSlotsForTenant` boundary; local doctors continue through `AppointmentService.listSlots`, while referenced external targets are resolved and tenant-checked through the existing provider integration port.

This batch is limited to propagation of the existing session tenant into the existing booking availability tool and avoiding the Web-only `doctorAvailability` wrapper from the Voice service path. No routing, provider resolution, bookability, authorization, or session semantics are changed. Direct Voice read-only FIND_SLOTS continues using `HealthcareAiReadOnlyToolGateway` and `AppointmentService.listSlots` under its trusted `HealthcareAiExecutionContext.tenantId`.

## Validation

Test tenant propagation from kernel session to booking tools and through the tenant-scoped `PatientPortalService.doctorSlotsForTenant` boundary, including a real service-boundary call with no request thread-local, and verify null tenant fails before lookup. Run focused API tests, package, and `git diff --check`. Do not rebuild or restart Docker.
