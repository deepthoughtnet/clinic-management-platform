# Healthcare Doctor Availability Overlap Validation

## Purpose

Prevent overlapping active doctor availability sessions from being persisted in Healthcare, so downstream slot generation cannot produce duplicate appointment slots.

## Scope

- Healthcare doctor availability create/update validation
- Active-session overlap detection on the same tenant, doctor, and day-of-week scope
- Business-friendly validation errors for overlapping availability
- Preservation of inactive historical sessions

## Rule

Two active availability sessions overlap when:

- `existing.start < new.end`
- `existing.end > new.start`

Adjacent sessions that touch at the boundary are allowed.

## Persistence behavior

- Exact active duplicates remain rejected.
- Active overlaps are rejected before persistence.
- Inactive sessions do not participate in overlap conflict detection.

## User experience

When overlap is rejected, the UI should surface a readable message describing the conflicting session window.
