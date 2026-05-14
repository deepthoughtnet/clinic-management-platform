# Clinic Management Platform Documentation

This documentation suite is implementation-grounded and generated from the current repository state (`backend/*`, `web-admin/*`, migrations `V001..V042`, local infra, and admin/carepilot/AI modules).

## Navigation
- Business
  - [Business Requirements Document](./business/business-requirements-document.md)
  - [Functional Requirements Document](./business/functional-requirements-document.md)
- Architecture
  - [Solution Architecture](./architecture/solution-architecture.md)
  - [AI Orchestration Platform Architecture](./architecture/ai-orchestration-platform.md)
- Technical
  - [Technical Design Document](./technical/technical-design-document.md)
  - [Database Design](./technical/database-design.md)
- API
  - [API Reference](./api/api-reference.md)
- Security
  - [Security Architecture](./security/security-architecture.md)
- CarePilot
  - [CarePilot Platform](./carepilot/carepilot-platform.md)
- AI Platform
  - [AI Orchestration Platform](./ai-platform/ai-orchestration-platform.md)
- Operations
  - [Operations Runbook](./runbooks/operations-runbook.md)
- Deployment
  - [Deployment Guide](./deployment/deployment-guide.md)
- User Manual
  - [User Manual](./user-manual/user-manual.md)
- Roadmap
  - [Platform Roadmap](./roadmap/platform-roadmap.md)

## Diagram Library
- [Architecture Overview](./diagrams/high-level-architecture.md)
- [CarePilot Flows](./diagrams/carepilot-flows.md)
- [AI Calls Flows](./diagrams/ai-calls-flows.md)
- [Scheduler and Retry Lifecycles](./diagrams/scheduler-retry-lifecycle.md)
- [Lead/Webinar/Billing/Pharmacy Lifecycles](./diagrams/domain-lifecycles.md)
- [AI Orchestration Lifecycle](./diagrams/ai-orchestration-lifecycle.md)
- [RBAC and Deployment Topology](./diagrams/rbac-and-deployment.md)

## Implementation Baseline Used
- Backend API controllers: `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/**`
- Domain modules: `backend/domains/*`
- Provider SPI/adapters: `backend/providers/*`
- Platform security/context: `backend/platform/*`
- Migrations: `backend/api/api-bff/src/main/resources/db/migration/V001..V042`
- Frontend routes/nav: `web-admin/src/app/App.tsx`, `web-admin/src/layout/nav.ts`
- Environment and local infra: `backend/api/api-bff/src/main/resources/application.yml`, `local/docker-compose.yml`, `local/keycloak/realm-export.json`

