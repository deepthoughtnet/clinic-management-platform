# Commercial Catalog Foundation

Batch S1 introduces the commercial catalog foundation for Jeevanam Healthcare only.

Scope:

- PLATFORM_ADMIN-only catalog administration
- additive schema for capabilities, modules, features, limits, and add-ons
- seed/backfill of currently active runtime module codes
- relationship management between catalog records
- audit recording for catalog mutations

Out of scope for Batch S1:

- tenant subscription assignment
- billing and renewals
- usage metering and quota enforcement
- tenant-specific overrides
- effective entitlement cutover
- plan template/version builders
- Jeevanam Discover subscription flows
- Jeevanam Care subscription flows

Compatibility:

- existing tenant plans, tenant subscriptions, tenant modules, legacy boolean module columns, /api/me, route gating, and backend entitlement enforcement remain unchanged
- commercial catalog records are informational only in this batch

Next phase:

- immutable commercial plan templates and versions
- tenant subscription assignment to catalog plans
- catalog-driven entitlement evaluation
