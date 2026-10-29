# AIVA V2 Voice Slot Selection Continuation — Phase B

## Scope

Restore bounded selection from the canonical slots already displayed in an
active Voice booking draft. This phase does not change availability retrieval,
provider resolution, final confirmation, transaction mutation, OTP, or other
appointment operations.

## Ownership and placement

- Owning domain: appointment booking application flow.
- API module: `backend/api/api-bff` owns Voice adaptation and the existing V2
  conversation/kernel orchestration.
- Persistence: existing `SessionProjection.latestAvailabilityResult` and
  `BookingDraft.selectedSlotReference`; no new store or schema.
- Dependencies: Voice adapter may use the existing displayed-slot resolver and
  canonical `AivaV2BookingTools.selectBookingSlot` path.
- Planned files: `AivaV2VoiceConversationAdapter`,
  `AivaV2DisplayedSlotSelectionResolver`, and the DotVoice inbound-path test.

## Behavior

Only while the active collecting booking draft has a matching current
`AvailabilityResult` may the adapter route a bounded ordinal or time answer to
the existing `SELECT_SLOT` decision path. The resolver recognizes the
requested numeric/English forms only against the currently displayed page.
Selection continues to use the canonical slot reference from that result.
Out-of-context values do not enter booking selection, and invalid choices keep
the existing draft and offered slots for clarification.

## Validation

The DotVoice inbound sequence exercises booking start, date continuation,
canonical availability, and offered-slot selection. Tests assert provider/date
continuity, canonical reference preservation, invalid-selection retention, and
no global interpretation outside a displayed-choice context.
