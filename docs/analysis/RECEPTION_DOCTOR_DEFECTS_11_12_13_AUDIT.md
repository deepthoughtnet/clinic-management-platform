# Reception / Doctor defects 11, 12 and 13 — read-only audit

**Date:** 2026-10-02  
**Scope:** evidence gathering only. No application source, migration, configuration, or runtime behavior was changed for this audit.

## Executive summary

| Defect | Finding | Current enforcement | Migration required to address the gap? |
|---|---|---|---|
| 12 — duplicate appointment slot | The UI disables slots that the API reports as full, and the service re-checks capacity, but there is no database uniqueness/lock protecting the read-then-insert sequence. Same-patient duplication is guarded; different patients are allowed up to configured capacity and may be duplicated by a race. | Frontend presentation + `AppointmentService.ensureScheduledSlotAvailable`; no DB appointment-slot guard. | Not necessarily. A safe fix must preserve `maxPatientsPerSlot` and explicit overbooking; a naïve unique `(tenant, doctor, date, time)` constraint is not compatible. |
| 13 — start consultation before fee/override | Check-in to `WAITING` is payment-gated (or payment-bypassed), but the doctor start endpoint/service does not evaluate the consultation fee or an explicit override. Queue/Day Board action predicates are status/permission based. | Backend lifecycle/assignment guard only; no fee guard at consultation start. | No field is needed if the existing check-in bypass is deliberately the authoritative override. A domain decision is required because current override is a receptionist/admin check-in override, not a doctor-start override. |
| 11 — add availability for all doctors | “All Doctors” is an overview scope only. The Add Availability action requires one selected doctor and calls a single-doctor endpoint; no bulk endpoint or shared availability record exists. | Frontend `canMutateSchedule` and `quickCreateAvailability`; backend path is `POST /api/doctors/{doctorUserId}/availability`. | No for the current behavior. A bulk feature would require an explicit product decision and likely a new API contract, not a bug fix in the existing single-doctor model. |

## Owning contexts and call chains

All three defects are in the appointment/scheduling bounded context. The inbound adapter is `backend/api/api-bff`; appointment persistence and scheduling rules are in `backend/domains/appointment-domain`; the administrative UI is `web-admin`.

## Defect 12 — double booking the same appointment slot

### Creation path

1. `web-admin/src/pages/appointments/AppointmentsPage.tsx` loads `getDoctorSlots`, derives `getAppointmentSlotPresentation(...)`, and only submits a normal booking when the selected slot is bookable. The UI rejects a `FULL`, past, leave/holiday/unavailable, or otherwise non-bookable slot. It also supports an explicit ad-hoc/emergency path outside configured availability.
2. `web-admin/src/api/clinicApi.ts` sends the booking to `POST /api/appointments`.
3. `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/AppointmentController.java#create` requires `appointment.manage`, derives `allowOverbooking` from `DoctorAssignmentSecurityService.isClinicAdmin()`, resolves the clinic time zone, and calls `AppointmentService.createScheduled(...)`.
4. `backend/domains/appointment-domain/src/main/java/com/deepthoughtnet/clinic/appointment/service/AppointmentService.java#createScheduled` validates the command, loads slots, validates the date, tenant-patient/doctor ownership, calls `ensureNoDuplicateActiveAppointment`, then `ensureScheduledSlotAvailable`, and finally saves `AppointmentEntity`.

### Current slot generation and occupancy rule

`AppointmentService#listSlots` loads active availability for the requested tenant/doctor/day and appointments for the same tenant, doctor, and date. It groups appointments by **exact `appointmentTime`** after excluding only `CANCELLED` and `NO_SHOW`.

Therefore the current occupancy rule is:

- `BOOKED`, `WAITING`, `IN_CONSULTATION`, and `COMPLETED` appointments occupy capacity;
- `CANCELLED` and `NO_SHOW` release the slot;
- capacity is `DoctorAvailabilityEntity.maxPatientsPerSlot` (minimum one);
- slot status becomes `FULL` when the exact-start-time booking count reaches capacity;
- appointment duration is used to generate the discrete start/end slots, not to detect arbitrary appointment-interval overlap between bookings.

This is a start-slot/capacity model, not a general interval-exclusion model. The report should not assume that an appointment beginning inside another appointment’s duration is invalid unless product rules change.

### Existing duplicate and capacity guards

`AppointmentService#ensureNoDuplicateActiveAppointment` calls:

```text
existsByTenantIdAndDoctorUserIdAndPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(
  tenant, doctor, patient, date, time, [CANCELLED, NO_SHOW]
)
```

This prevents the **same patient + doctor + date + exact time** from having two active appointments. It does not prevent two different patients from occupying the same slot when capacity is one.

`AppointmentService#ensureScheduledSlotAvailable` then rejects `FULL`/unavailable/past slots. When the API caller is a clinic administrator, `allowOverbooking` is true; a full slot may then be accepted only with a nonblank reason. Ad-hoc booking similarly bypasses configured slot capacity after unavailability checks. Those are existing, intentional policy paths and must be preserved when fixing a true duplicate defect.

The reschedule UI is in the same `AppointmentsPage.tsx` (`openReschedule` / `saveReschedule` around the reschedule dialog); it validates that a date/time is supplied and that the selected time is not past before calling the API. Slot/capacity enforcement therefore remains a backend responsibility for rescheduling.

### Reschedule path

`AppointmentController#reschedule` (`PATCH /api/appointments/{id}/reschedule`) applies `appointment.manage`, appointment access, the same clinic timezone and administrator overbooking decision, then calls `AppointmentService#reschedule`.

`reschedule` only accepts `BOOKED` rows, loads the target slot, repeats `ensureNoDuplicateActiveAppointment` and `ensureScheduledSlotAvailable`, then updates the existing row. Consequently:

- the same capacity policy applies to rescheduling;
- the same concurrency gap exists;
- there is no explicit exclusion of the row being rescheduled from the slot list/duplicate check, so a reschedule to its own current exact slot can be rejected as already occupied (an edge case to test before changing behavior).

### Database and concurrency evidence

`AppointmentEntity` has indexes for tenant/patient, tenant/doctor/date, and tenant/date/status, plus a unique constraint only for `(tenant_id, doctor_user_id, appointment_date, token_number)`. There is **no** unique or exclusion constraint on doctor/date/time. `@Version` protects updates to one existing row, not concurrent inserts.

`AppointmentRepository` contains the same-patient existence query and date/doctor listing methods, but no locked slot-capacity query, count query, or pessimistic-lock method. The service is transactional, but two concurrent requests can both:

1. read the same pre-insert slot count;
2. pass `ensureScheduledSlotAvailable`; and
3. insert separate appointments.

This is a real race for capacity-one slots. A blanket database unique key would incorrectly reject configured capacity greater than one and explicit overbooking, so it is not a safe surgical fix without a product decision. A robust implementation would need a serialized capacity decision (for example, a lockable availability/slot row plus a transactionally consistent re-check, with an explicit strategy for ad-hoc and overbooking paths). A simple extra read check improves sequential behavior but does not close the race.

### Current frontend guard

`getAppointmentSlotPresentation` in `web-admin/src/pages/appointments/slotState.ts` uses the server-provided `status`, `bookedCount`, `maxPatientsPerSlot`, and `bookable` fields. `AppointmentsPage#saveAppointment` repeats the full/past/unavailable/bookable checks before calling `submitAppointment`. This is useful UX only; the backend guard remains authoritative. The emergency/ad-hoc branch intentionally permits a time outside generated availability after confirmation.

### Smallest safe fix options (ranked)

1. **Recommended robustness:** preserve the capacity/overbooking policy, add a transactionally serialized slot-capacity check for configured slots and a separate, explicit policy for ad-hoc slots; apply the same service helper to create and reschedule and exclude the row being rescheduled. This likely needs a repository lock/query or lockable scheduling record, but not necessarily a schema migration.
2. **Smallest code-only mitigation:** add an active-booking count/re-check in `AppointmentService` immediately before save and cover create/reschedule. This closes stale sequential checks but leaves the concurrent insert race.
3. **Only if product policy is changed to one appointment per slot:** add a tenant/doctor/date/time/status-aware database constraint/ledger. Do not implement this under the current capacity/overbooking semantics.

### Defect 12 test matrix

- two different patients, capacity one, concurrent create: exactly one succeeds;
- capacity two: two succeed, third is rejected;
- administrator overbooking with reason remains allowed;
- cancelled/no-show release capacity; completed remains occupying under current rule;
- same patient duplicate remains rejected;
- reschedule collision and reschedule-to-own-slot behavior;
- tenant A cannot affect tenant B;
- UI full-slot disabled while direct API remains protected;
- ad-hoc booking retains its explicit policy and does not silently become a normal slot.

## Defect 13 — Start Consultation payment / doctor override

### Frontend paths and current predicates

`web-admin/src/pages/appointments/DayBoardPage.tsx` computes `selectedAppointmentFee` through `consultationFeeSummary(...)` and displays fee status, but the Start/Continue button is rendered when the user is a doctor with `consultation.create` and the appointment status is `WAITING` or `IN_CONSULTATION`. Its `disabled` expression is only `!canStartConsultation`; it does not include fee status, due amount, or a bypass flag.

`web-admin/src/pages/appointments/QueuePage.tsx#resolveDoctorPrimaryAction` exposes “Start Consultation” for `BOOKED`, `CHECKED_IN`, or `WAITING` (and “Continue Consultation” for `IN_CONSULTATION`) based on appointment status. It does not inspect `paymentState`, `feeDueAmount`, or `paymentBypassedAt`. The queue row does compute those fields for payment cards, but the doctor action resolver does not consume them.

`web-admin/src/components/workflow/workflowHelpers.js#getNextWorkflowAction` returns “Collect Fee” for `AWAITING_PAYMENT`, `PAYMENT_PENDING`, `UNPAID`, or positive due amount; otherwise it returns “Start Consultation” for `CHECKED_IN`/`WAITING`. This helper is a presentation hint and is not the backend start guard.

### API and service path

`AppointmentController#startConsultation` (`POST /api/appointments/{appointmentId}/start-consultation`) requires all of:

- `consultation.create`;
- `appointment.manage`;
- role `DOCTOR`;
- `DoctorAssignmentSecurityService.requireConsultationStartAccess` (appointment assigned to the current doctor and status `WAITING`, or existing consultation plus `IN_CONSULTATION`).

It then calls `ConsultationService#startFromAppointment`.

`ConsultationService#startFromAppointment` rejects only `COMPLETED`, `CANCELLED`, and `NO_SHOW`; for a new consultation it calls `AppointmentService.updateStatus(... IN_CONSULTATION ...)` and creates the consultation. It does not call `BillingService.consultationFeeStatus`, `consultationFeeDueAmount`, or `ensureConsultationFeePaid`, and it does not inspect payment-bypass fields.

Thus the backend currently enforces assignment/lifecycle, but not “fee satisfied OR explicit doctor override” at the start transition. The UI and backend are consistent about omitting a fee check.

### Authoritative payment source

`BillingService#consultationFeeStatus` is the authoritative fee assessment used by appointment/queue responses. It resolves the doctor’s configured consultation fee and aggregates bill/payment state. It returns:

- `NOT_CONFIGURED` when no positive consultation fee is configured;
- `PAID` when remaining due is zero;
- `PARTIAL` when some net payment exists but due remains;
- `UNPAID` otherwise.

`BillingService#consultationFeeDueAmount` returns the remaining due amount. The existing check-in controller path calls `BillingService.ensureConsultationFeePaid` when moving an appointment to `WAITING` without a bypass.

### Existing override semantics and auditability

`AppointmentEntity` already stores `payment_bypass_reason`, `payment_bypass_notes`, `payment_bypassed_by`, and `payment_bypassed_at`; `AppointmentRecord`/`AppointmentResponse` expose them. `AppointmentController#updateStatus` accepts an override only while moving to `WAITING`:

- without a reason it calls `billingService.ensureConsultationFeePaid`;
- with a reason it requires `appointment.checkin.payment_bypass`, validates one of `EMERGENCY`, `DOCTOR_APPROVED`, `PATIENT_WILL_PAY_AFTER_CONSULTATION`, `BILLING_COUNTER_UNAVAILABLE`, or `OTHER` (notes required for `OTHER`), and records the actor/time through `AppointmentService.updateStatus`;
- an audit event `appointment.checkin.payment_bypassed` includes reason, actor, timestamp, notes, and due amount.

Role mappings/tests show `appointment.checkin.payment_bypass` is granted to clinic admin/receptionist, not doctor, billing, or auditor. The `DOCTOR_APPROVED` value is a reason selected by the authorized check-in actor; it is not evidence that the doctor themselves authenticated an explicit start override. No separate doctor consultation-start override concept was found.

### Exact gap and safe options

The defect is reproducible in the code path whenever a doctor can reach a start-eligible lifecycle state with a positive unpaid/partial due and no `paymentBypassedAt`: the UI action is enabled and the endpoint starts the consultation. Normal check-in should prevent most such rows, but the start endpoint itself is not defense-in-depth.

Recommended narrow implementation decision (not performed here): define whether the existing, audited check-in bypass is the accepted `explicitDoctorOverride` for consultation start. If yes, enforce in `ConsultationService#startFromAppointment` (or a shared domain guard) as `fee status == PAID/NOT_CONFIGURED` or `paymentBypassedAt != null`, while preserving assignment/lifecycle checks; align Day Board/Queue predicates. If the product truly requires a doctor-authored override, the current data model/permissions do not provide one; adding it would require an approved domain specification, authorization decision, audit semantics, and likely a migration. Do not repurpose `DOCTOR_APPROVED` silently.

### Defect 13 test matrix

- positive fee + `PAID`: UI enabled and API succeeds;
- `NOT_CONFIGURED` (zero/no fee): existing policy decision must be explicit;
- `UNPAID` and `PARTIAL`, no bypass: UI disabled and direct API rejected;
- unpaid with an audited, authorized bypass: behavior follows the decided override policy;
- bypass actor/reason/timestamp/audit retained;
- assigned doctor only; another doctor receives 403;
- `BOOKED`/`CHECKED_IN` lifecycle behavior remains unchanged;
- completed/cancelled/no-show remain blocked; already in consultation remains resumable;
- queue and Day Board show the same result.

## Defect 11 — “Add same availability for all doctors”

### UI state and disabled predicate

`web-admin/src/pages/doctors/DoctorAvailabilityPage.tsx` deliberately uses an “All Doctors” option for overview/filtering. The helper text says: “Search and choose All Doctors for a full overview.”

For mutation, the page computes:

```text
canMutateSchedule = Boolean(isDoctor ? auth.appUserId : selectedDoctorId)
```

The Add Availability button is `disabled={!canMutateSchedule}`. `quickCreateAvailability` repeats the guard and explicitly returns “Select a specific doctor to add availability.” The form may submit multiple selected weekdays, but it chooses one `doctorUserId` and calls `createDoctorAvailability` once per selected day. There is no loop over all doctors.

### Backend path and model

`web-admin/src/api/clinicApi.ts#createDoctorAvailability` calls `POST /api/doctors/{doctorUserId}/availability`.

`backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/appointment/DoctorAvailabilityController#create` requires `appointment.manage`, resolves one effective doctor, maps `DoctorAvailabilityRequest`, and calls `AppointmentService#createAvailability`.

`AppointmentService#createAvailability` validates one doctor, checks tenant membership, rejects an exact active duplicate, rejects an overlapping active range for that same doctor/day, then saves one `DoctorAvailabilityEntity`. `DoctorAvailabilityEntity` has a required `doctorUserId`; there is no all-doctors sentinel or shared availability entity. The request contains day/start/end/break/duration/capacity/active, but no doctor list.

### Overlap and database behavior

The domain performs exact duplicate and interval-overlap checks per doctor. Migration `V148__doctor_availability_active_session_uniqueness.sql` replaces the original constraint with a partial unique index for active rows on `(tenant_id, doctor_user_id, day_of_week, start_time, end_time)`. This prevents duplicate active sessions, while inactive historical rows remain possible. The database does not implement bulk semantics.

If some doctors conflict, the current UI does not reach a partial operation because “All Doctors” cannot submit. When a specific doctor is selected, a multi-day request uses `Promise.allSettled`: successful days are retained and failures are shown; there is no all-doctors partial-success contract.

### Permissions and intentional behavior

The endpoint requires `appointment.manage`. Doctors are restricted by `DoctorAssignmentSecurityService.effectiveDoctorUserId`; non-doctors may select a doctor but must select a specific doctor to mutate. “All Doctors” is therefore a read/overview scope, not a bulk-create command. The disabled button is consistent with the existing one-doctor API, not evidence of an overlap conflict.

### Smallest safe fix options (not implemented)

1. Treat the current UI as intentional and clarify the label/help text: select one doctor to add; All Doctors is overview only. This is the smallest behavior-preserving correction.
2. If bulk creation is a confirmed requirement, add an explicit bulk application/API operation that expands to one record per selected doctor, reports conflicts per doctor/day, and reuses `createAvailability` validation. This is a new feature contract, not a one-line enabled-state fix. No schema change is inherently required because records are already per doctor, but authorization, partial-success semantics, and conflict UX must be specified.

### Defect 11 test matrix

- All Doctors overview keeps Add Availability disabled and does not submit;
- one selected doctor enables the action and creates one row per selected day;
- duplicate/overlap for one doctor is rejected without affecting another doctor;
- inactive duplicate can be recreated according to current partial-index/domain rules;
- doctor cannot mutate another doctor’s schedule;
- clinic admin/receptionist with `appointment.manage` can select and mutate a specific doctor;
- if bulk is later approved: conflict, partial success, tenant isolation, and audit tests are required.

## Recommended implementation order

1. **Defect 13 safety first:** settle the meaning of “explicit doctor override,” then add one shared backend guard and align the two UI predicates. This is the most direct patient-flow/payment safety issue.
2. **Defect 12 integrity second:** confirm whether the reported duplicate violates configured capacity/overbooking policy. If yes, implement serialized capacity enforcement shared by create/reschedule; do not add a blanket unique key.
3. **Defect 11 UX clarification or scoped bulk feature:** first confirm the requirement is truly bulk creation rather than selecting an overview filter. Prefer clarifying the existing single-doctor behavior before adding a new API.

## Migration assessment

- Defect 12: **No migration required for the recommended lock/recheck approach**, but a database-level capacity ledger/constraint would require an approved migration and compatibility/backfill plan. Existing appointment rows must be audited before any constraint.
- Defect 13: **No migration if the existing audited check-in bypass is reused**. A distinct doctor-authored override would require a new persisted field/audit contract and therefore likely a migration.
- Defect 11: **No migration for current single-doctor records**. A bulk API can expand into existing rows; schema changes are not inherently needed.

## Files likely to change in a future implementation (not changed in this audit)

### Defect 12

- `backend/domains/appointment-domain/.../AppointmentService.java`
- `.../AppointmentRepository.java` and/or a lockable scheduling repository
- `backend/api/api-bff/.../AppointmentController.java` only if error/contract mapping changes
- `web-admin/src/pages/appointments/AppointmentsPage.tsx`, `slotState.ts`, and reschedule UI tests
- appointment-domain/API regression tests

### Defect 13

- `backend/domains/consultation-domain/.../ConsultationService.java`
- possibly a shared appointment/billing guard (no new model assumed)
- `backend/api/api-bff/.../AppointmentController.java` only for response/error mapping if needed
- `web-admin/src/pages/appointments/QueuePage.tsx`, `DayBoardPage.tsx`, and workflow helper/tests

### Defect 11

- current clarification only: `web-admin/src/pages/doctors/DoctorAvailabilityPage.tsx`
- if approved bulk feature: a new application command/controller method plus focused API/UI tests; reuse `AppointmentService#createAvailability` validation.

## Risks and edge cases

- Capacity and authorized overbooking make “one row per slot” uniqueness unsafe.
- Reschedule must not count the row being moved as a competing booking.
- Exact start-time occupancy differs from interval overlap; changing this would alter scheduling semantics.
- Cancelled/no-show release slots, while completed currently remains counted.
- Clinic timezone is supplied to create/reschedule and slot generation; any future database guard must use the persisted local date/time consistently.
- Check-in bypass is currently an audited receptionist/admin action, not a doctor-authored approval; conflating the reason string with actor identity would weaken auditability.
- “All Doctors” is used throughout the availability page for read-only aggregation; enabling the button without a bulk contract could create partial/ambiguous schedules.

## Read-only change and validation statement

Only this audit document was added. No Java/TypeScript source, migration, configuration, database row, permission, appointment, billing, consultation, AIVA/STT/VAD, Lab, Engage, or provider code was modified. No live booking, consultation, or availability mutation was performed.
