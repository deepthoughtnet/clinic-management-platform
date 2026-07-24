# Module Boundaries

This repository is organized as a modular monolith with explicit module ownership.

## Backend modules

### `api-bff`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `api-bff` | HTTP adapter and orchestration layer | REST controllers, API DTOs, mapping, endpoint security, request validation, orchestration | Domain/application modules, platform abstractions, shared contracts | `api-bff` persistence ownership, domain persistence implementations, `commercial-domain` internals | JPA entities, Spring Data repositories, owned `db` packages | `PatientController`, `PlatformTenantController`, `CommercialCatalogController` |

### `commercial-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `commercial-domain` | Commercial catalog bounded context | Capability/module/feature/limit/add-on models, lifecycle validation, repositories, persistence mapping, domain/application services | `platform-core`, `platform-audit`, `platform-spring`, JPA, Jackson, validation | `api-bff` | REST controllers, API DTOs, browser concerns | Commercial catalog entities, repositories, and service logic |

### `identity-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `identity-domain` | Tenant and identity lifecycle | Tenant persistence, memberships, subscriptions, module entitlement support | Platform core/audit/contracts, JPA, Keycloak client | `api-bff` | HTTP adapters | `TenantSubscriptionService`, tenant repositories |

### `clinic-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `clinic-domain` | Clinic profile and related platform records | Clinic persistence and clinic-specific services | Platform core/audit/security/storage, JPA | `api-bff` | HTTP adapters | `ClinicProfile` model/services |

### `appointment-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `appointment-domain` | Appointment lifecycle | Appointment persistence, booking workflows, related events | Identity, patient, platform audit/events, JPA | `api-bff` | HTTP adapters | Appointment entities and services |

### `consultation-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `consultation-domain` | Consultation workflows | Consultation persistence and service logic | Appointment, identity, patient, platform audit, JPA | `api-bff` | HTTP adapters | SOAP/consultation service classes |

### `billing-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `billing-domain` | Billing lifecycle | Bills, payments, refunds, financial records | Clinic, patient, appointment, consultation, inventory, platform audit, JPA | `api-bff` | HTTP adapters | Bill and payment repositories |

### `inventory-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `inventory-domain` | Inventory and pharmacy stock | Inventory and pharmacy persistence/services | Platform audit/core, JPA | `api-bff` | HTTP adapters | Medicine and stock repositories |

### `prescription-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `prescription-domain` | Prescription lifecycle | Prescription persistence and service logic | Clinic, consultation, identity, patient, platform contracts/audit, JPA | `api-bff` | HTTP adapters | Prescription entities and services |

### `vaccination-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `vaccination-domain` | Vaccination workflows | Vaccination persistence and logic | Clinic, patient, billing, inventory, notification, platform audit, JPA | `api-bff` | HTTP adapters | Vaccination service and repositories |

### `notification-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `notification-domain` | Internal notification and outbox processing | Notification persistence, outbox processing, notification center services | Event infrastructure, platform contracts/audit, partner domain modules, JPA | `api-bff` | HTTP adapters | Notification history/outbox services |

### `ai-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `ai-domain` | AI orchestration and platform AI state | AI workflow persistence, orchestration, prompt registry | Platform contracts/audit, AI provider SPI, JPA | `api-bff` | HTTP adapters | AI orchestration services and repositories |

### `carepilot-domain`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `carepilot-domain` | CarePilot workflows | Campaigns, reminders, engagement, notifications, and related persistence | Partner domain modules, platform events/audit/contracts, messaging and voice SPIs, JPA | `api-bff` | HTTP adapters | CarePilot campaign/reminder services |

## Frontend module

### `web-admin`

| Module | Purpose | Owns | May depend on | Must not depend on | Must not contain | Examples |
|---|---|---|---|---|---|---|
| `web-admin` | Browser application | Routes, navigation, typed API client, dialogs, workflow UX | Backend HTTP API, frontend libraries | Security authority, backend-only invariants | Server-side entitlement logic | Platform admin pages, commercial catalog UI |

## Commercial Catalog Ownership

Commercial Catalog is owned by `commercial-domain`.

`api-bff` may only contain:

- `CommercialCatalogController`
- `CommercialCatalogApiService`
- API request/response DTOs
- API mapping/orchestration

`commercial-domain` owns:

- catalog business validation
- JPA entities
- Spring Data repositories
- catalog lifecycle service
- catalog enums and model records

If a future module placement conflicts with the existing reactor convention, prefer a new domain module over placing domain-owned persistence in `api-bff`.
