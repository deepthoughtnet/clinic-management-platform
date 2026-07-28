# Jeevanam Care

Jeevanam Care is the patient-facing frontend for OTP login, patient registration, appointments, booking completion, prescriptions, laboratory reports, bills, notifications, profile management, and patient AIVA.

This application was previously named `web-public`. Phase 3B renamed the repository directory, package metadata, and Docker service to `web-care` without changing routes, authentication, session headers, session storage, or booking behavior.

## Local Setup

```bash
npm install
npm run dev
```

The local development server keeps the existing Care port assignment used before the rename.

## Docker

The Docker Compose frontend service is now `web-care`.

Local compose defaults:

- `web-care`: `5175`
- `web-discover`: `5177`
- `web-admin`: `5174`
- `web-aiva`: `5176`

UAT compose defaults:

- `web-care`: `5176`
- `web-discover`: `5178`
- `web-admin`: `5175`
- `web-aiva`: `5177`

`WEB_CARE_PORT` is the preferred host-port variable. Compose files retain `WEB_PUBLIC_PORT` as a fallback compatibility alias.

## Environment Variables

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Backend API base URL. |
| `VITE_PUBLIC_API_BASE_URL` | Compatibility API base URL used by existing patient booking/public provider lookups. |
| `VITE_DISCOVER_APP_URL` | External Jeevanam Discover URL for legacy discovery redirects and Find Care links. |
| `VITE_HEALTHCARE_APP_URL` | External Jeevanam Healthcare URL for clinic/hospital login links. |
| `VITE_CLINIC_LOGIN_URL` | Backward-compatible Healthcare login URL. |
| `VITE_SUPPORT_URL` | Optional support URL. |
| `VITE_AIVA_APP_URL` | Optional AIVA application URL. |
| `VITE_CAREAI_RUNTIME_URL` | Patient AIVA voice WebSocket/runtime URL. |

## Out Of Scope

- Public provider discovery ownership
- Provider registration
- Public page builder
- Healthcare operational workflows
- Platform Administration
- Backend API namespace migration
- Patient authentication migration

## Compatibility Notes

Patient session headers remain unchanged:

- `X-Patient-Session`
- `X-Tenant-Id`

Some localStorage keys intentionally retain `clinic-web-public-*` names to avoid invalidating active patient sessions during this technical rename. Rename those only through a dedicated patient-session migration.
