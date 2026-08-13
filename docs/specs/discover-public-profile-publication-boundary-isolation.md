# Discover Public Profile Publication Boundary Isolation

## Purpose

Provider edits to public profiles must remain isolated from the patient-facing public snapshot until the profile is explicitly approved and published.

## Scope

This spec covers:

- provider draft editing
- editable child associations for public profiles
- public publication snapshot promotion
- public read isolation

## Required Rules

1. Provider draft changes must never mutate the published public snapshot directly.
2. Patient-facing public endpoints must read only from publication-approved projections.
3. Editable child associations must have a draft/workspace projection distinct from the published projection.
4. Approval/publication must promote the approved draft state atomically into the published projection.
5. Public state must remain unchanged while a draft is saved, submitted, under review, or request-changes is pending.
6. The existing public profile identity and canonical slug must remain unchanged during draft editing.

## Hospital Doctor Associations

Hospital doctor associations are provider-editable workspace data and must obey the same publication boundary:

- provider workspace edits the draft association projection
- public hospital pages read only the published association projection
- approval/publish promotes the draft association set into the published association set

## Non-goals

- Do not change Care booking semantics.
- Do not change Healthcare availability or appointment semantics.
- Do not change tenant membership semantics.
- Do not add alternative publication models for other bounded contexts.

