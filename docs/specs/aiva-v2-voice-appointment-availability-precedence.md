# AIVA V2 Voice — Appointment Availability Precedence

## Scope

This change only clarifies deterministic Voice routing between future availability and patient-owned appointment history. It does not enable any transaction or appointment lookup.

## Ownership and placement

- Owning boundary: AIVA V2 Voice channel adapter in `backend/api/api-bff`.
- Persistence and migrations: none; appointment-domain behavior is unchanged.
- Frontend: none.
- Allowed dependencies: current normalized turn, Voice session context, and existing read-only slot tool selection.

## Routing contract

- A dated future-availability request mentioning an appointment routes to `FIND_SLOTS`, ahead of generic appointment-noun booking/lookup classification.
- Explicit patient ownership or appointment-history wording (for example, “my upcoming appointments” or an appointment described as already booked) remains `FIND_APPOINTMENTS`.
- `FIND_APPOINTMENTS` remains denied in Voice. Booking, cancellation, and rescheduling remain denied.

## Validation

Cover Hindi, Hinglish, and English future-availability examples and patient-owned history examples through the Voice adapter. Verify the effective slot date/doctor input and the blocked lookup behavior.
