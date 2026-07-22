# Platform Module Business Events

This module provides the foundation for typed, durable, module-to-module business events inside the Jeevanam modular monolith.

## Scope

- Business events only
- No provider integrations
- No notification delivery
- No external broker
- No workflow rewrites

## Event lifecycle

1. A business module completes a fact, such as an appointment being booked or a lead being converted.
2. The module creates an immutable event contract.
3. The event and its listener rows are persisted in the same transaction.
4. A dispatcher polls runnable listener rows after commit.
5. Each listener runs independently and idempotently.
6. Success, retry, dead-letter, and recovery state are persisted.

## Event contract

Every event carries:

- `eventId`
- `eventType`
- `eventVersion`
- `occurredAt`
- `tenantId`
- `sourceModule`
- `aggregateType`
- `aggregateId`
- `correlationId`
- `causationId`
- `actorId` when available
- `payload`

Contract rules:

- immutable
- serializable
- versioned
- no JPA entities
- no provider-specific fields
- no notification-channel fields

## Correlation

The platform uses `X-Correlation-ID` as the primary request header.
The legacy `X-Correlation-Id` remains accepted for compatibility.

Correlation metadata is:

- accepted at the HTTP boundary
- validated for safe token shape
- added to response headers
- stored in request context and MDC
- copied into business event metadata

## Persistence

The module stores two tables:

- `module_business_events`
- `module_business_event_listener_jobs`

This separation keeps:

- event metadata durable
- listener execution idempotent
- failure handling independent per listener

## Persistence ownership

The executable application imports `PlatformEventsPersistenceConfiguration` from this module
instead of naming the internal repository/entity packages directly. That keeps the schema and
repository bootstrap owned by the platform-events module while preserving a single public entry
point for the application.

## Listener model

Listeners are typed by event class and are expected to be:

- tenant-aware
- idempotent
- isolated from other listeners
- free of aggregate mutation
- diagnostic first in this batch

The current representative listeners only record processing diagnostics.
They do not send email, SMS, WhatsApp, push, or in-app notifications yet.

Listener rows are created synchronously with event publication so the business fact and its
execution jobs remain transactionally consistent. Dispatch later reclaims the row by flushing the
PROCESSING claim before listener invocation, which keeps multi-instance processing from double
executing the same job.

## Retry and dead-letter

Listener executions move through:

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `RETRY_SCHEDULED`
- `FAILED`
- `DEAD_LETTERED`

Recovery behavior:

- stalled processing rows are reclaimed after a timeout
- retry delays are bounded
- terminal failure is persisted
- one failing listener does not block independent listeners

## Why CarePilot remains separate

CarePilot campaign execution is a delivery plane for campaigns and reminders.
This module is the cross-module business-event foundation.
They solve different problems and remain separate in Batch 1.

## Future notification listener guidance

When notification integration begins:

- subscribe to this module's business events
- keep provider logic out of the business modules
- preserve idempotency and listener isolation
- keep delivery retries and dead-lettering separate from business-event persistence

## Batch 1 representative events

- `AppointmentBooked`
- `LabReportPublished`
- `LeadConverted`

These are intentionally small and representative.
They prove the module boundary without changing existing workflows.
