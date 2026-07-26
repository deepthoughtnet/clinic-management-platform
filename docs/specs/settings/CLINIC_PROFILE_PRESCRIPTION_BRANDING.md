---
spec_id: JCP-SHARED-CLINIC-BRANDING
title: Clinic Profile Prescription Template and Branding
status: approved
product: Jeevanam Healthcare
owner: Jeevanam Platform
bounded_context: clinic-domain
api_module: api-bff
frontend_module: web-admin
destructive_migration_allowed: false
---

# Purpose

Improve tenant-scoped clinic branding for prescription PDFs without changing finalized prescription data or replacing existing PDF generation.

# Boundary

- In scope: clinic logo upload/removal, logo preview, primary/accent color pickers, live branding preview, prescription footer cleanup, QR/signature layout fix, and responsive settings UX.
- In scope: existing prescription template persistence and rendering improvements.
- Out of scope: payment, billing, usage metering, and runtime entitlement behavior.
- Out of scope: changing finalized prescription clinical content.

# Ownership

`clinic-domain` owns clinic profile branding metadata.

`api-bff` owns:

- REST controllers
- multipart upload orchestration
- DTO mapping
- authorization
- document/media upload integration

`prescription-domain` owns PDF rendering.

`web-admin` owns the settings UI.

# Persistence model

- The prescription template remains the canonical branding record for prescription PDFs.
- The template continues to reference a logo document/media asset by ID.
- Logo bytes are stored in the existing tenant document/media storage, not in profile payloads.
- Existing branding rows remain valid and backward compatible.

# Upload rules

- Supported logo formats: PNG, JPG, JPEG, WEBP
- Maximum size: 2 MB
- Empty, corrupt, or invalid MIME uploads are rejected
- Logo preview, replace, and remove operations are tenant-scoped
- Logo removal clears the stored reference without affecting finalized prescription clinical data

# Color rules

- Primary and accent colors use a color picker plus hex input
- Values are normalized to six-digit hex
- Invalid colors are rejected
- Preview updates immediately

# PDF rules

- Remove technical footer strings from patient prescriptions
- Keep clinical and visit information visible
- Preserve QR support
- Place QR in the left footer section and signature in the right footer section
- Hide the QR section cleanly when disabled
- Keep layout multi-page safe
- Keep browser print, preview, and PDF output visually consistent

# Acceptance criteria

- Logo upload, replace, preview, and remove work
- Colors persist and preview immediately
- QR does not overlap signature
- Generated-at/generated-by footer strings do not appear on patient prescriptions
- Existing prescriptions remain printable
- PDF generation remains backward compatible
- No existing migration is renamed

