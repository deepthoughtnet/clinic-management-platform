# Jeevanam Discover

Jeevanam Discover is the public-facing frontend for provider discovery, provider registration entry, public pages, product information, pricing, and booking initiation.

## Current Phase

Phase 3B renamed the patient application directory from `web-public` to `web-care` after the Phase 3A Care refocus. Discover continues to own anonymous public discovery and reuses the existing public catalog APIs without changing backend APIs, authentication, booking completion, or patient/private Care workflows.

## Responsibilities

- Public homepage with search and featured provider discovery
- Doctor, clinic, and speciality listings/details
- Hospital and service route shells
- Provider registration entry points
- Login chooser for Jeevanam Care and Jeevanam Healthcare
- Product and pricing placeholder routes
- Public footer and navigation

## Out Of Scope

- Care authentication
- Care workspace, private documents, personal care history, and related private experiences
- Clinic or hospital operational workflows
- Platform Administration
- Provider registration backend logic
- Booking-intent creation
- SEO/SSR hardening

## Public APIs Reused

Phase 2 reuses the existing anonymous public catalog endpoints:

- `GET /api/public/search`
- `GET /api/public/doctors`
- `GET /api/public/doctors/{doctorSlug}`
- `GET /api/public/clinics`
- `GET /api/public/clinics/{clinicSlug}`
- `GET /api/public/specialities`
- `GET /api/public/specialities/{specialitySlug}`

## Local Setup

```bash
cd web-discover
npm install
npm run dev
```

The local development server uses port `5177`.

## Environment Variables

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Optional API base URL for future public APIs. |
| `VITE_CARE_APP_URL` | External URL for Jeevanam Care patient login. |
| `VITE_HEALTHCARE_APP_URL` | External URL for Jeevanam Healthcare clinic/hospital login. |
| `VITE_AIVA_APP_URL` | Optional external AIVA URL. |
| `VITE_ENV_NAME` | Internal environment name. Not displayed in the UI. |
| `VITE_ANALYTICS_ID` | Reserved analytics identifier. |
| `VITE_ANALYTICS_ENABLED` | Disabled by default. Set to `true` only when analytics is approved. |

## Routes

- `/`
- `/doctors`
- `/doctors/:doctorSlug`
- `/clinics`
- `/clinics/:clinicSlug`
- `/hospitals`
- `/specialities`
- `/specialities/:specialitySlug`
- `/services`
- `/healthcare`
- `/pricing`
- `/list-your-practice`
- `/register/doctor`
- `/register/clinic`
- `/register/hospital`
- `/login`
- `/about`
- `/contact`
- `/privacy`
- `/terms`

## Application Relationship

- `web-discover`: public discovery, public provider pages, product marketing, and provider registration entry.
- `web-care`: Jeevanam Care patient application, previously named `web-public`.
- `web-admin`: remains Jeevanam Healthcare, including clinic/hospital operations and Platform Administration mode.
