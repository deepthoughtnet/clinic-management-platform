# AI Receptionist Workflow v1

## Purpose
AI Receptionist v1 is a controlled, auditable workflow built on the Realtime AI Voice Gateway and AI Orchestration Platform.

It supports:
- greeting users
- basic intent detection
- clinic FAQ responses
- lead capture
- appointment-intent collection
- safe human escalation
- session transcript and summary persistence

It does not support:
- autonomous medical diagnosis
- prescription advice
- emergency triage beyond escalation guidance

## Workflow States
- `GREETING`
- `IDENTIFY_INTENT`
- `FAQ_RESPONSE`
- `COLLECT_LEAD_DETAILS`
- `APPOINTMENT_BOOKING_INTENT`
- `COLLECT_APPOINTMENT_DETAILS`
- `AVAILABILITY_LOOKUP`
- `BOOKING_CONFIRMATION_REQUIRED`
- `HUMAN_ESCALATION`
- `SESSION_SUMMARY`
- `COMPLETED`

State is persisted in voice session `metadata_json.receptionist.state`.

## Supported Intents
- `CLINIC_FAQ`
- `BOOK_APPOINTMENT`
- `LEAD_CAPTURE`
- `HUMAN_CALLBACK`
- `UNKNOWN`
- `EMERGENCY_OR_MEDICAL_RISK`

Intent and outcome are persisted in voice session metadata for auditability.

## Prompt Keys
Workflow uses AI orchestration prompt registry keys:
- `AI_RECEPTIONIST_GREETING`
- `AI_RECEPTIONIST_INTENT_DETECTION`
- `AI_RECEPTIONIST_FAQ`
- `AI_RECEPTIONIST_APPOINTMENT_COLLECTION`
- `AI_RECEPTIONIST_LEAD_CAPTURE`
- `AI_RECEPTIONIST_SAFE_ESCALATION`
- `AI_RECEPTIONIST_SUMMARY`

## Safety Rules
- no diagnosis responses
- no prescription guidance
- emergency/medical-risk keywords trigger safe escalation response
- repeated unknown intent triggers escalation
- explicit human callback request triggers escalation

## Lead Capture Flow
When minimum lead identity is available (name + phone):
1. Search existing lead by tenant and phone
2. Create or update lead with source `AI_RECEPTIONIST`
3. Add lead activity note with voice session reference
4. Persist `leadCreated` and `leadId` in session metadata

## Appointment Booking Foundation
v1 captures appointment details and marks appointment-request intent.

If details are incomplete or confidence is low, workflow escalates to receptionist follow-up rather than autonomous booking.

## Human Escalation
Escalation is set on session with reason and visible in admin:
- user asks for human callback
- emergency/medical-risk intent
- repeated unknown intents
- staff confirmation required for sensitive/uncertain requests

## Transcript and Summary
- Transcript timeline stored in `voice_transcripts`
- Events timeline stored in `voice_session_events`
- Session summary generated at completion using `AI_RECEPTIONIST_SUMMARY`
- Summary saved under `metadata_json.receptionist.summary`

## Admin Realtime AI
`Administration -> Realtime AI` now shows receptionist workflow metadata:
- state
- intent
- escalation flags/reason
- lead created + lead id
- appointment request flag
- session summary

Test endpoint for controlled validation:
- `POST /api/realtime-ai/receptionist/test-message`

## Future Roadmap
- telephony/SIP channel integration
- multilingual receptionist prompts
- stronger deterministic FAQ catalog
- explicit appointment booking confirmations with scheduling APIs
