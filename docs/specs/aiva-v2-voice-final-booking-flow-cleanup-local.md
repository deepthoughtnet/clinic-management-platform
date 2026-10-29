# AIVA V2 Voice Final Booking Flow Cleanup (LOCAL)

## Scope and placement

- Owning context: AIVA V2 Voice booking and availability orchestration.
- API module: `backend/api/api-bff`.
- Persistence owner: existing appointment availability and Voice
  `SessionProjection`; no persistence/schema change.
- Migration owner: none.
- Frontend: none.
- Allowed dependencies: Voice orchestration uses the existing tenant-scoped
  `PatientPortalService` availability boundary, `AppointmentService`, provider
  resolver, and existing bounded language normalizers.
- Planned files: `PatientPortalService`, `AivaV2TransactionalKernel`,
  `AivaV2VoiceConversationAdapter`, `LocaleTemporalNormalizer`, and focused
  API tests.

## Decisions

1. A canonical active tenant doctor is authoritative for CARE_PRIVATE slot
   lookup. A projection booking reference cannot override that identity.
   External target resolution and validation remain in effect when there is no
   matching tenant doctor.
2. An explicit unique provider switch updates `activeVoiceDoctor` and the
   existing draft provider before availability executes. Ambiguous resolution
   does not query slots against the prior provider.
3. Common Hindi/Hinglish spoken weekday aliases are added to the existing
   `LocaleTemporalNormalizer`; next-occurrence behavior remains unchanged.
4. Additional bounded Hindi/Marathi booking affirmations are interpreted only
   while a prepared draft with a canonical slot and pending confirmation exists.
   Booking mutation continues through the existing execution service.

No routing redesign, authorization change, booking redesign, frontend change,
or migration is part of this cleanup.
