# CareAI Architecture Review and Reusable Agent Platform Analysis

Date: 2026-06-05

## 1. Executive Summary

### What CareAI is
CareAI is the patient-facing conversational assistant inside the Clinic Management Platform. It helps patients discover care, authenticate with OTP, self-register when needed, and manage appointments through chat and voice.

### Business problem it solves
CareAI reduces friction in front-desk-heavy workflows:
- Fewer receptionist-led appointment calls for routine booking, reschedule, cancel, and status checks.
- Better patient self-service after hours and during peak load.
- Consistent guidance with explicit confirmation and deterministic backend controls.
- A foundation for a broader AI assistant platform that can later support reminders, follow-ups, knowledge retrieval, and cross-industry assistants.

### Who benefits
- Patients: faster self-service, voice and chat options, fewer calls and less waiting.
- Receptionists: lower load from repetitive booking/status tasks.
- Doctors: fewer scheduling interruptions and cleaner patient routing.
- Clinic admins: reusable AI capabilities without bypassing tenant controls.
- Business owners: lower support cost, better patient experience, and a path to a reusable AI platform.

### Current maturity level
CareAI is beyond prototype level. It already supports real patient workflows for booking, rescheduling, cancelling, appointment status, OTP login, self-registration, and voice interaction. It is not yet a full agent platform. It is still a domain-specific assistant built inside the clinic application.

### Strengths
- Shared business engine for patient chat and patient voice.
- Deterministic execution for appointment actions.
- Tenant-aware and patient-aware authorization.
- Provider fallback across Gemini, Groq, and Mock.
- Working voice stack using WebSocket, STT, LLM, and TTS.
- Planner design already moving toward a reusable agent runtime pattern.

### Limitations
- Conversation memory is primarily in-process, not durable platform memory.
- No true callback workflow yet.
- No production-grade RAG layer yet.
- Limited multilingual depth.
- No streaming STT/TTS or barge-in.
- No formal evaluation, cost analytics, or prompt governance maturity at enterprise scale.

### Future opportunities
- Extract CareAI into a reusable DeepThoughtNet Agent Platform.
- Add domain packs for healthcare, wealth, finance automation, knowledge management, customer service, and RouteExpert.
- Add RAG, workflow tooling, evaluations, analytics, cost tracking, and enterprise observability.

## 2. Current CareAI End-to-End Architecture

### High-level architecture

```text
Patient
  -> web-public Patient Portal UI
  -> Chat (REST) or Voice (WebSocket)
  -> Patient Portal API / Patient Voice WebSocket
  -> PatientPortalCareAiService
  -> AI planner/extraction via AiOrchestrationService
  -> Deterministic PatientPortalService / appointment services
  -> STT/TTS via VoiceOrchestratorService for voice
  -> Appointment domain + doctor/slot services
  -> Patient Portal, reception views, doctor calendar, queue
```

### Component responsibilities
- `web-public`: patient UI for OTP login, self-registration, chat, and voice.
- `PatientPortalController`: REST endpoints for dashboard, profile, appointments, CareAI chat, and reset.
- `PatientPortalVoiceWebSocketHandler`: patient voice transport, turn buffering, event emission.
- `PatientPortalVoiceAssistantService`: STT -> CareAI -> TTS pipeline for one voice turn.
- `PatientPortalCareAiService`: central conversation manager and deterministic workflow coordinator.
- `LlmBackedPatientPortalCareAiPlanner`: LLM-assisted planner/extractor that receives current conversation state plus allowed next actions.
- `AiOrchestrationServiceImpl`: prompt rendering, provider routing, fallback, output normalization, audits.
- `PatientPortalService`: deterministic appointment and doctor operations, tenant-safe and patient-safe.
- `VoiceOrchestratorService`: shared STT/TTS/LLM infrastructure used by admin voice test and patient voice.

## 3. Voice Flow End-to-End

### Step-by-step sequence
1. Patient clicks `Talk to CareAI` in `web-public`.
2. Browser creates or reuses a same-origin WebSocket to `/ws/patient-portal/careai?sessionToken=...`.
3. `web-public` nginx proxies `/ws/` to `clinic-management-api:8089`.
4. `PatientPortalVoiceWebSocketAuthInterceptor` validates the signed patient session token and resolves tenant, patient, app user, and roles.
5. `PatientPortalVoiceWebSocketHandler` accepts the socket and returns `session.connected` / `session.started`.
6. Browser microphone starts. VAD monitors RMS/peak levels and detects speech start/end.
7. While the patient speaks, `MediaRecorder` captures audio locally.
8. When silence exceeds threshold, the browser stops the turn, converts the blob to base64, sends `audio.chunk` messages, then `audio.end`.
9. Backend reconstructs the audio turn in `PatientPortalVoiceWebSocketHandler`.
10. `PatientPortalVoiceAssistantService` calls shared STT through `VoiceOrchestratorService.transcribeBufferedAudio(...)`.
11. Transcript is emitted as `transcript.final`.
12. The same transcript is sent to `PatientPortalCareAiService.message(...)`.
13. `PatientPortalCareAiService` loads conversation state, runs deterministic parsing, optionally runs the LLM planner, then decides the next safe response.
14. If the turn is informational, it returns a safe assistant reply.
15. If the turn is booking/reschedule/cancel/status, it may query deterministic backend services for doctors, appointments, or slots.
16. If explicit confirmation is still missing, it asks for confirmation and does not mutate data.
17. If confirmation is present and the deterministic safety gates pass, the backend executes the action.
18. Assistant text is emitted as `assistant.text`.
19. `PatientPortalVoiceAssistantService` calls `VoiceOrchestratorService.synthesizeAssistantText(...)`.
20. Backend emits `assistant.audio.chunk` messages followed by `assistant.audio.end`.
21. Browser assembles the reply audio, plays it, shows `Speaking`, and blocks microphone auto-resume while playback is active.
22. After playback ends, browser resumes listening automatically.

### Important voice characteristics
- Voice and chat share the same business engine.
- Voice does not bypass tenant or patient authorization.
- No appointment mutation occurs from STT/TTS alone; the same deterministic rules apply as chat.
- Admin voice test and patient voice share low-level voice infrastructure but not business authorization or patient workflow logic.

## 4. Chat Flow End-to-End

### Step-by-step sequence
1. Patient enters a text message in `/patient/careai`.
2. Browser sends `POST /api/patient-portal/careai/message`.
3. `PatientPortalSessionAuthenticationFilter` reads `X-Patient-Session`.
4. Spring Security resolves a `PatientPortalSessionPrincipal` with tenant and patient identity.
5. `PatientPortalController.message(...)` forwards the request to `PatientPortalCareAiService`.
6. `PatientPortalCareAiService`:
   - loads current conversation state
   - runs deterministic parsing
   - optionally calls the LLM planner with current state, missing fields, allowed actions, and user message
   - merges safe extraction output
   - drives the deterministic workflow
7. If the turn needs doctor/slot/appointment data, it queries `PatientPortalService`.
8. If explicit confirmation is absent, it asks clarifying questions and stores state.
9. If confirmed and safe, deterministic backend services perform the action.
10. Assistant response plus updated state is returned to the frontend.
11. Frontend appends the assistant reply to the chat thread and updates the visible state card.

## 5. Appointment Booking Flow

### Functional flow
1. Patient expresses booking intent.
2. CareAI identifies:
   - doctor name or specialty if available
   - target date
   - time window or explicit time
3. If multiple doctors match, CareAI offers numbered options.
4. If date is missing, CareAI asks for date.
5. If time window is missing, CareAI asks for time.
6. `PatientPortalService.careAiDoctorOptions(...)` and `careAiSlotOptions(...)` provide tenant-scoped candidates.
7. CareAI offers slots or nearest available alternatives.
8. CareAI asks for explicit confirmation.
9. Only after confirmation does `PatientPortalService.bookAppointment(...)` execute deterministic appointment creation.
10. The created appointment then becomes visible in:
   - Patient Portal appointments
   - reception and admin workflows
   - doctor calendar/queue workflows handled by the appointment domain

### Safety properties
- No free-form LLM execution of appointment creation.
- No booking without explicit confirmation.
- No doctor or slot lookup outside the patient tenant.

## 6. Appointment Reschedule Flow

### Functional flow
1. Patient expresses reschedule intent.
2. CareAI asks which upcoming appointment to reschedule if ambiguous.
3. `PatientPortalService.careAiUpcomingAppointments(...)` returns tenant- and patient-scoped appointment options.
4. Patient selects appointment by number/name/date context.
5. CareAI asks for the new date/time window if not already provided.
6. `PatientPortalService.careAiSlotOptions(...)` returns new slot options.
7. CareAI proposes exact or nearest slots.
8. CareAI asks for explicit confirmation.
9. `PatientPortalService.rescheduleAppointment(...)` performs the deterministic mutation only after confirmation.

## 7. Appointment Cancellation Flow

### Functional flow
1. Patient expresses cancel intent.
2. CareAI retrieves upcoming appointments for the patient only.
3. Patient selects appointment.
4. CareAI asks for explicit cancellation confirmation.
5. `PatientPortalService.cancelAppointment(...)` changes the appointment status deterministically.
6. The cancellation is then visible in patient and admin/reception views through the normal appointment domain.

## 8. Appointment Status Flow

### Functional flow
1. Patient asks for next appointment or status.
2. CareAI retrieves patient-scoped upcoming appointment options.
3. If one clear next appointment exists, it answers with doctor, date, time, and clinic context.
4. If there are multiple and the request is ambiguous, CareAI asks which one.
5. No confirmation is required because this is read-only.

## 9. AI Orchestration Layer

### What orchestration means here
The orchestration layer is the broker between application workflows and LLM providers. It does not own business execution. It normalizes prompts, chooses providers, applies guardrails, parses model output, records audits, and returns structured results for deterministic application code to use.

### Current components
- `AiOrchestrationServiceImpl`
- `AiProviderRouterImpl`
- Provider adapters:
  - `GeminiAiProviderAdapter`
  - `GroqAiProviderAdapter`
  - `MockAiProvider`
- Prompt registry/catalog:
  - `AiPromptTemplateCatalog`
  - prompt definition/version tables and AI Ops foundations
- Audit/usage/invocation logging services
- Guardrail foundations

### Provider selection
- Configured provider chain: `GEMINI -> GROQ -> MOCK`
- Router filters providers by:
  - configured order
  - bean availability
  - `supports(taskType)`
- Patient CareAI planner uses `AiTaskType.GENERIC_EXTRACTION`
- Admin voice test general responses use `AiTaskType.GENERIC_COPILOT`

### Prompt execution
1. Resolve template code.
2. Merge input variables and evidence.
3. Apply system and user prompt framing.
4. Run guardrail checks.
5. Try providers in order until success.
6. Normalize output.
7. Record invocation/audit details.

### Tool execution
Today, the orchestration layer is not a full generic agent runtime. It does not safely execute arbitrary tools on behalf of the model. Instead:
- LLMs are used for planning, extraction, classification, and summarization.
- deterministic services execute real business actions.

### How business actions are protected
- CareAI can suggest intent and missing fields.
- deterministic services perform all doctor lookup, slot lookup, booking, reschedule, and cancel actions.
- confirmation is enforced outside the LLM.

### Fallback behavior
Current practical chain:
1. Gemini
2. Groq
3. Mock
4. deterministic parser logic still exists inside CareAI for date/time/name/confirmation extraction and acts as a safe fallback when planner output is unusable.

### Strengths
- Clear separation of planning vs execution.
- Provider fallback already exists.
- Prompt registry and AI Ops foundations already exist.
- Invocation logging and cost/usage foundations already exist.

### Weaknesses
- Not yet a complete generic tool runtime.
- RAG integration is not yet first-class.
- Prompt/version governance is still foundational, not fully operationalized.
- LLM planner state is not yet backed by durable memory.

## 10. Deterministic Safety Layer

### Why LLM should not directly book appointments
LLMs are probabilistic. They can misread dates, invent doctor matches, or infer confirmation that was never given. In a healthcare context, irreversible actions must not rely on model output alone.

### How deterministic execution works
- `PatientPortalCareAiService` manages the conversation state.
- `PatientPortalService` performs actual domain operations.
- The LLM can suggest:
  - intent
  - doctor name
  - specialty
  - date
  - time window
  - confirmation hint
  - topic switch
- It cannot execute the mutation itself.

### Confirmation safety
Booking, reschedule, and cancel require explicit confirmation. The patient flow stores `confirmationPending` state and only executes after a clear confirm path.

### Patient safety
- No diagnosis or prescription changes are performed by CareAI booking flows.
- Emergency/handoff wording exists for urgent cases.
- Deterministic backend remains the source of truth for patient, doctor, slot, and appointment data.

### Hallucination reduction
- Limited structured extraction prompts.
- Provider fallback.
- deterministic parser fallback.
- deterministic data queries.
- no free-form action execution.

## 11. Security Architecture

### Patient OTP login
- Patient begins with tenant/clinic code plus mobile number.
- OTP challenge is tenant-scoped.
- Existing patients receive a patient session.
- New patients receive a restricted registration session.

### Patient session model
- `PatientPortalSessionTokenService` issues signed HMAC session tokens.
- Patient token carries tenantId, patientId, subject, displayName, and `PATIENT` role.
- Registration token carries tenantId, phone, and `PATIENT_REGISTRATION` role.

### JWT and staff/admin
- Admin and staff use the standard JWT/Keycloak path.
- Patient portal uses its own signed session token and filter.

### Tenant context
- Tenant comes from the verified patient session or JWT claims, not from untrusted frontend state.
- `PatientPortalAuthContextExtractor` resolves tenant safely and rejects mismatches.

### Appointment authorization
- `@PreAuthorize` requires `PATIENT` role for patient portal endpoints.
- patient voice WebSocket interceptor requires a valid patient session token.
- deterministic service methods check appointment ownership and patient access.

### Data isolation
- doctor lookup is tenant-scoped
- appointment lookup is patient-scoped
- booking/reschedule/cancel target only the current patient
- no frontend `patientId` is trusted

### Trust boundaries
- Frontend can only submit text, audio, or request parameters.
- Backend derives identity, tenant, and patient context.
- LLM is outside the trust boundary for mutations.

## 12. Current Business Capabilities

Already working in the current architecture:
- Public doctor/clinic discovery
- Patient OTP login
- Patient self-registration after OTP verification
- Patient portal profile and appointments
- Patient CareAI chat
- Patient CareAI voice
- Book appointment
- Reschedule appointment
- Cancel appointment
- Appointment status
- Conversation state management
- Doctor selection by number/name/partial name
- Date/time parsing with deterministic and LLM-assisted extraction
- Provider fallback
- Emergency/handoff foundation

## 13. Current Gaps

Main gaps:
- No real callback/handoff workflow ticketing
- No durable cross-session memory store
- No reminder management workflow
- No refill workflow
- Limited multilingual coverage
- No enterprise RAG layer
- No citation-backed grounded answers
- No LiveKit/WebRTC
- No streaming STT/TTS or barge-in
- No evaluation framework for assistant quality
- No mature prompt registry operations model
- No full AI monitoring and guardrail analytics
- Limited cost tracking exposure at product level

## 14. Reusable Agent Platform Analysis

### Core recommendation
CareAI should evolve into a generic `DeepThoughtNet Agent Platform` where healthcare becomes one domain pack, not the platform itself.

### Recommended platform components
- Agent Runtime
  - session handling
  - turn handling
  - modality coordination
- Planner
  - context-aware next-step planning
  - structured extraction
- Conversation Manager
  - active state
  - slot filling
  - confirmation
  - interruption/topic switch handling
- Memory
  - short-term turn memory
  - session memory
  - durable long-term memory when needed
- Tool Registry
  - declarative tools
  - risk classes
  - required approvals
- Workflow Engine
  - deterministic multi-step business workflows
- RAG Engine
  - retrieval, ranking, citations
- Prompt Registry
  - versioning, activation, rollback
- Model Gateway
  - routing, fallback, policy
- Evaluation Framework
  - scenario evaluation, regression suites
- Audit and Observability
  - traces, costs, token usage, business outcomes
- Voice Layer
  - STT/TTS adapters, streaming capability later
- Chat Layer
  - REST/web/mobile integration
- Security Layer
  - tenant resolution
  - subject resolution
  - policy enforcement

### How CareAI maps into that platform
CareAI already contains early versions of:
- planner
- conversation manager
- deterministic workflow engine
- voice layer
- model gateway/orchestration

It should later be extracted as:
- a healthcare domain pack
- a patient scheduling tool pack
- a healthcare safety and compliance policy pack

## 15. Domain Pack Concept

### Healthcare domain pack
- CareAI
- patient booking assistant
- doctor discovery assistant
- prescription/refill/reminder assistant later

### Wealth management domain pack
- portfolio assistant
- relationship manager copilot
- investment research assistant
- retirement planning assistant
- compliance assistant

### Finance automation domain pack
- invoice assistant
- approval assistant
- vendor assistant
- reconciliation assistant

### Knowledge management domain pack
- enterprise knowledge assistant
- policy assistant
- training assistant
- document assistant

### RouteExpert domain pack
- parent assistant
- driver assistant
- operations assistant

### Pattern across all packs
- same platform runtime
- different tool registries
- different prompt sets
- different policy packs
- different data connectors

## 16. RAG Architecture

### Recommended reusable RAG flow
1. Document upload
2. normalization and OCR if needed
3. chunking
4. embedding
5. vector store persistence
6. metadata and access-control indexing
7. retrieval based on user intent and permissions
8. context builder
9. LLM answer generation
10. citation generation
11. audit/logging

### Reuse across domains
- Healthcare: SOPs, clinic policies, discharge instructions, public-safe educational content
- Wealth: research notes, model portfolios, product sheets, compliance memos
- Knowledge management: HR policies, training docs, process manuals
- Finance: invoices, contracts, vendor terms, policies

### Key architectural rule
RAG must respect tenant and document-level authorization before retrieval, not after answer generation.

## 17. Wealth Management Architecture Example

### Example query
`How is my portfolio performing?`

### Target architecture
1. Client asks via chat or voice.
2. Runtime resolves identity, account scope, and permissions.
3. Planner identifies a portfolio performance query.
4. Deterministic services retrieve:
   - portfolio holdings
   - benchmark performance
   - recent transactions
   - risk profile
5. RAG layer retrieves:
   - market research
   - internal product notes
   - compliance disclaimers
6. Context builder assembles:
   - quantitative facts
   - relevant research snippets
   - compliance policy context
7. LLM generates a plain-language answer.
8. Deterministic compliance validator checks required disclaimers and forbidden advice patterns.
9. Voice or chat response is rendered.

### Why this maps well from CareAI
- patient identity -> client/account identity
- appointment tools -> portfolio and order tools
- clinic safety layer -> compliance and suitability layer

## 18. Knowledge Management Architecture Example

### Example query
`What is the leave policy?`

### Target architecture
1. Employee asks via chat or voice.
2. Runtime resolves employee identity and access scope.
3. Planner identifies a policy lookup query.
4. RAG retrieves policy documents and current version metadata.
5. Context builder selects the best passages.
6. LLM generates a concise answer.
7. System returns citations to the actual leave policy documents.
8. If policy is ambiguous, assistant asks clarifying questions, such as country, grade, or contract type.

### Why this maps well from CareAI
- same chat/voice shell
- same orchestration and fallback concepts
- same need for deterministic access control
- different toolset and no appointment workflow

## 19. Professional Diagram Assets

The following diagram assets are created alongside this report:
- `docs/architecture/careai-overall.drawio`
- `docs/architecture/careai-overall.svg`
- `docs/architecture/careai-voice-flow.drawio`
- `docs/architecture/careai-voice-flow.svg`
- `docs/architecture/careai-chat-flow.drawio`
- `docs/architecture/careai-chat-flow.svg`
- `docs/architecture/careai-booking-flow.drawio`
- `docs/architecture/careai-booking-flow.svg`
- `docs/architecture/careai-orchestrator.drawio`
- `docs/architecture/careai-orchestrator.svg`
- `docs/architecture/careai-security.drawio`
- `docs/architecture/careai-security.svg`
- `docs/architecture/deepthoughtnet-agent-platform.drawio`
- `docs/architecture/deepthoughtnet-agent-platform.svg`
- `docs/architecture/wealthai-architecture.drawio`
- `docs/architecture/wealthai-architecture.svg`
- `docs/architecture/knowledgeai-rag.drawio`
- `docs/architecture/knowledgeai-rag.svg`

These are presentation-oriented assets, not code-generation artifacts.

## 20. Management Presentation

The presentation outline is provided in:
- `docs/presentation/careai-management-presentation.md`

It contains a 17-slide management package with:
- title
- purpose
- key points
- suggested diagram
- speaker notes
- business benefits
- technical highlights
- roadmap framing

## 21. Extraction Feasibility Analysis

### Can CareAI be extracted?
Yes. The current implementation is already logically separable, but not fully operationally separated yet.

### What should move into a standalone platform
- AI orchestration layer
- provider router and model gateway
- prompt registry
- planner runtime
- voice STT/TTS orchestration
- conversation manager framework
- tool/workflow registry
- AI audits, invocation logs, and evaluation foundations
- future RAG engine

### What should remain in the Clinic platform
- patient, doctor, clinic, and appointment domain models
- deterministic appointment services
- clinic-specific authorization rules
- clinic-specific admin and patient UIs
- clinic-specific compliance and workflow logic

### Required API model
Recommended boundary:
- Clinic platform exposes deterministic domain APIs and events.
- Agent platform calls those APIs through approved tool adapters.
- Agent platform never writes directly to clinic tables.

### Recommended deployment model
Short term:
- modular monolith with extractable modules

Medium term:
- separate AI Platform service used by clinic-management-api

Long term:
- multi-tenant DeepThoughtNet Agent Platform with domain packs and product-specific adapters

### Final recommendation
Do not jump directly to a broad microservice split. The best path is:
1. keep the current deterministic clinic domain in place
2. formalize planner/runtime/tool interfaces
3. externalize orchestration, prompts, audits, and voice services
4. then extract a reusable platform behind stable APIs

## Appendix: Evidence Sources Reviewed

Primary code areas reviewed for this report:
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/careai/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/patientportal/voice/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/voice/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/realtime/websocket/*`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/config/*`
- `backend/domains/ai-domain/*`
- `web-public/src/api/patientPortal.ts`
- `web-public/src/pages/patient/PatientPortalPages.tsx`
- `web-admin/src/pages/ai/VoiceTestPage.tsx`
