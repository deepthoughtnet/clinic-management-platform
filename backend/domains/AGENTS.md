# Domain Module Instructions

- Every new business concept requires an explicit bounded-context owner.
- Domain modules own business rules and persistence.
- Domain modules must not depend on `api-bff`.
- Public interaction should occur through explicit services or interfaces.
- Avoid cross-domain repository access.
- Use audited lifecycle operations instead of physical deletion where applicable.
