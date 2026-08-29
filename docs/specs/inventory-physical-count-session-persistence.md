# Inventory Physical Count Session Persistence

## Purpose

Persist physical count session progress server-side in the `inventory` bounded context so that:

- counted quantities and reasons survive refresh/reopen
- session lifecycle state survives refresh/reopen
- physical inventory quantities remain unchanged until the later posting stage

## Bounded context ownership

- Bounded context: `inventory`
- API module: `backend/api/api-bff`
- Domain / persistence module: `backend/domains/inventory-domain`
- Frontend area: `web-admin/src/pages/inventory`

## In scope

- Server-side persistence for physical count session headers, lines, audit fields, and lifecycle status
- Session list/reload support for the inventory workspace
- Save/update behavior that updates the same session record rather than creating duplicates
- Tenant isolation and `inventory.manage` authorization
- Preservation of the existing post-adjustment inventory movement workflow

## Out of scope

- Physical inventory posting logic
- Transfer stock, direct goods receipt, supplier invoice, PO, medicine master, POS, reconciliation, vaccination, or unrelated RBAC redesign
- Workflow redesign for draft/review/approve/post

## Compatibility and migration notes

- Add a forward-only Flyway migration for the physical count session table.
- Existing inventory quantities must not change during save or session-state transitions.
- Existing posted-count reconciliation records remain read-only and unaffected.

## Validation and test expectations

- create session
- save session progress
- reload/re-fetch session
- exact counted quantities restored
- reasons restored
- variance/completion metrics restored
- session remains in the expected lifecycle state
- second save updates the existing session
- unauthorized access rejected
- tenant isolation preserved
- submit path continues to work with persisted data

## File ownership map

- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/db/PhysicalCountSessionEntity.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/db/PhysicalCountSessionRepository.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/service/InventoryService.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/service/InventoryServiceImpl.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/service/model/PhysicalCount*.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/inventory/InventoryController.java`
- `web-admin/src/api/clinicApi.ts`
- `web-admin/src/pages/inventory/InventoryPage.tsx`
- focused tests under `backend/api/api-bff/src/test/java/com/deepthoughtnet/clinic/api/inventory`
- focused source tests under `web-admin/test`
