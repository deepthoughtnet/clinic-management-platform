# AI Receptionist Workflow v2

## Purpose
AI Receptionist v2 extends v1 with multi-turn conversational intelligence, structured slot filling, confidence-aware routing, and richer operational visibility.

Safety boundaries remain unchanged:
- no diagnosis
- no prescription advice
- no emergency triage beyond safe escalation guidance

## V2 State Machine
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

State is persisted at `metadata_json.receptionist.state`.

## Intent Model
Supported intents:
- `CLINIC_FAQ`
- `BOOK_APPOINTMENT`
- `RESCHEDULE_APPOINTMENT`
- `CANCEL_APPOINTMENT`
- `LEAD_CAPTURE`
- `BILLING_QUERY`
- `INSURANCE_QUERY`
- `HUMAN_CALLBACK`
- `COMPLAINT`
- `EMERGENCY_OR_MEDICAL_RISK`
- `UNKNOWN`

Each turn stores intent and confidence at:
- `metadata_json.receptionist.intent`
- `metadata_json.receptionist.intentConfidence`

## Multi-turn Memory
Session-scoped conversational memory is stored under `metadata_json.receptionist.memory`:
- `lastIntent`
- `currentWorkflowState`
- `missingFields`
- `lastAiQuestion`
- `userClarification`
- `escalationState`

## Slot Filling
Structured fields are persisted under `metadata_json.receptionist.slots`:
- `name`
- `phone`
- `email`
- `preferredDoctor`
- `specialty`
- `preferredDate`
- `preferredTime`
- `reasonForVisit`
- `callbackPreference`
- `urgencyRiskFlag`

## Confidence and Routing
Confidence behavior:
- high confidence: continue workflow
- medium confidence: clarify missing details
- low confidence: clarifying fallback and escalation option
- risk intent: immediate safe escalation

## Escalation Routing
Escalation metadata is persisted under `metadata_json.receptionist.escalation`:
- `category` (e.g. `RECEPTIONIST`, `BILLING_DESK`, `CLINIC_ADMIN`, `EMERGENCY_GUIDANCE`)
- `reason`
- `priority`
- `status`

Timeline events include extraction and escalation entries for auditability.

## Appointment Workflow v2
For booking intent:
1. collect required fields
2. compute missing fields
3. require explicit confirmation
4. store booking request status in metadata

v2 does not hallucinate availability slots. If precise schedule data is not resolved, it creates a receptionist follow-up path.

## FAQ Strategy
FAQ answers are deterministic-first using tenant clinic profile data:
- location/address
- contact phone
- timing handoff
- service/specialty safe messaging

AI orchestration is used for safe phrasing where deterministic answers are unavailable.

## Structured Extraction
Each turn stores extraction summary in session timeline (`RECEPTIONIST_EXTRACTION`) including:
- intent
- confidence
- slot snapshot
- missing fields

## Test Endpoint
`POST /api/realtime-ai/receptionist/test-message`
- session-scoped multi-turn simulation by `sessionId`
- uses existing orchestration and guardrails path

## Admin Realtime AI
`Administration -> Realtime AI` displays:
- workflow state
- intent + confidence
- slot values and missing fields
- escalation route/priority/status
- suggested/requested slots
- booking request status
- transcript and event timeline

## Future Scope
- telephony/SIP channels
- multilingual receptionist flows
- explicit provider-level structured extraction templates
- deterministic doctor-slot selection with confirmed doctor identity
