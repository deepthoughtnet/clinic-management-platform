# Solution Architecture

## C4 - Context
```mermaid
flowchart TB
  ClinicUsers[Clinic Users] --> WebAdmin[Web Admin]
  PlatformOps[Platform Admins] --> WebAdmin
  WebAdmin --> APIGW[API BFF]
  APIGW --> Postgres[(PostgreSQL)]
  APIGW --> Keycloak[(Keycloak)]
  APIGW --> Redis[(Redis)]
  APIGW --> MinIO[(MinIO)]
  APIGW --> MsgProviders[Email/SMS/WhatsApp Providers]
  APIGW --> VoiceProviders[Voice Providers via voice-spi]
  APIGW --> AIProviders[Gemini/Groq/Mock]
```

## C4 - Container
```mermaid
flowchart LR
  subgraph Frontend
    FE[React web-admin]
  end
  subgraph Backend
    API[api-bff]
    DOM[Domain Modules]
    PLT[Platform Modules]
    PRV[Provider Modules]
  end
  FE --> API
  API --> DOM
  API --> PLT
  DOM --> PRV
  API --> DB[(PostgreSQL)]
```

## Component View - CarePilot
```mermaid
flowchart LR
  CCtrl[CarePilot Controllers] --> CService[Campaign/Reminder/Execution Services]
  CService --> MsgResolver[Messaging Provider Resolver]
  CService --> RetryEngine[Retry/Backoff Logic]
  CService --> ExecRepo[(Execution Tables)]
  WebhookCtrl[Delivery Webhook Controller] --> WebhookSvc[Delivery Webhook Service]
  WebhookSvc --> ExecRepo
```

## Component View - AI Calls
```mermaid
flowchart LR
  AiCallCtrl[AI Call Controller] --> Orch[AiCallOrchestrationService]
  Orch --> Registry[VoiceCallProviderRegistry]
  Registry --> Primary[Primary Provider]
  Registry --> Fallback[Fallback Provider]
  Orch --> Exec[(ai_call_executions)]
  Orch --> Events[(ai_call_events)]
  Orch --> Transcript[(ai_call_transcripts)]
  Scheduler[Dispatch Scheduler] --> Orch
  Reconcile[Reconciliation Scheduler] --> Orch
  Webhook[Webhook Endpoint] --> Orch
```

## Sequence - Lead Conversion with Optional Appointment
```mermaid
sequenceDiagram
  participant UI as Leads UI
  participant API as CarePilotLeadController
  participant LCS as LeadConversionService
  participant PS as Patient Domain Service
  participant AS as Appointment Service

  UI->>API: POST /api/carepilot/leads/{id}/convert
  API->>LCS: convert(tenant, lead, actor, booking?)
  LCS->>PS: create/reuse patient
  alt appointment requested
    LCS->>AS: create appointment
    AS-->>LCS: success or error
  end
  LCS-->>API: LeadConversionResult
  API-->>UI: patientId + appointmentId/error
```

## Sequence - AI Invocation Logging
```mermaid
sequenceDiagram
  participant Client
  participant Ctrl as AI Controller
  participant Orch as AiOrchestrationServiceImpl
  participant Guard as AiGuardrailService
  participant Provider as AiProvider
  participant Audit as AiRequestAuditService
  participant InvLog as AiInvocationLogService

  Client->>Ctrl: AI request
  Ctrl->>Orch: complete(request)
  Orch->>Guard: validatePreExecution
  Orch->>Provider: complete(providerRequest)
  Provider-->>Orch: response
  Orch->>Audit: record
  Orch->>InvLog: record
  Orch-->>Ctrl: normalized response
```

## Deployment Integration View
```mermaid
flowchart TB
  subgraph Cluster/VM
    FE[web-admin static hosting]
    API[Spring Boot api-bff]
    DB[(PostgreSQL)]
    KC[(Keycloak)]
    RD[(Redis)]
    MO[(MinIO)]
  end
  FE --> API
  API --> DB
  API --> KC
  API --> RD
  API --> MO
  API --> ExtMsg[SMTP/SMS/WhatsApp]
  API --> ExtAI[Gemini/Groq]
```

