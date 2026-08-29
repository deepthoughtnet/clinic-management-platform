# Pharmacy Inventory Location Management and Direct Goods Receipt Parity

## Purpose

Close the confirmed Pharmacy UAT gaps in the `inventory` bounded context without redesigning existing workflows:

- expose existing inventory location CRUD in `web-admin`
- align inventory RBAC visibility and routing for `TENANT_ADMIN` and `AUDITOR`
- fix stock-adjustment reference UX so users do not manually type internal UUIDs
- preserve the existing Direct Goods Receipt location and date validation contract
- clear stale Supplier Invoice variance reasons when the canonical variance becomes zero

## Bounded context ownership

- Bounded context: `inventory`
- API module: `backend/api/api-bff`
- Domain / persistence module: `backend/domains/inventory-domain`
- Frontend area: `web-admin/src/pages/inventory`, `web-admin/src/pages/pharmacy`

## In scope

- Inventory location list/create/edit/activate/deactivate using existing backend APIs
- Default location support according to current backend rules
- Location duplicate validation with a user-friendly error
- `TENANT_ADMIN` access to inventory routes and sidebar visibility
- `AUDITOR` read-only inventory access with matching sidebar and route behavior
- Inventory stock adjustment reference UX that keeps internal UUID linkage separate from business-facing text
- Direct Goods Receipt location submission parity and expiry-date validation parity
- Supplier Invoice zero-variance stale variance reason neutralization

## Out of scope

- New inventory architecture or new location persistence model
- Procurement, POS, GRN, medicine master, vaccination, or authentication redesign
- Payment posting / mark-paid behavior
- Supplier invoice canonical variance math changes
- Any new email or notification provider work

## Compatibility and migration notes

- No schema migration is expected for this batch unless a code inspection reveals a missing constraint that is already required by the current contract.
- Existing inventory location records remain tenant-scoped and editable.
- Existing stock, GRN, and supplier invoice records must remain readable.

## Validation and test expectations

- location CRUD happy path
- location duplicate validation
- activate/deactivate behavior
- default location handling
- inventory route and sidebar parity for `TENANT_ADMIN` and `AUDITOR`
- transfer stock location availability
- stock-adjustment reference UX
- DGR selected-location submission and date validation parity
- supplier invoice zero-variance stale reason cleanup

## File ownership map

- `web-admin/src/pages/inventory/InventoryPage.tsx`
- `web-admin/src/pages/pharmacy/PharmacyOperationsPage.tsx`
- `web-admin/src/pages/pharmacy/PharmacyProcurePage.tsx`
- `web-admin/src/layout/nav.ts`
- `web-admin/src/modules/moduleRegistry.ts`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/inventory/InventoryController.java`
- `backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/pharmacy/PharmacyOperationsService.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/service/InventoryServiceImpl.java`
- `backend/domains/inventory-domain/src/main/java/com/deepthoughtnet/clinic/inventory/db/InventoryLocationRepository.java`
- relevant focused tests under `backend/api/api-bff/src/test/java` and `web-admin/src/**/__tests__`
