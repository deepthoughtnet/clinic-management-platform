# Jeevanam Healthcare UAT Execution Guide

## 1. Purpose

This guide explains how to execute UAT against the implemented Jeevanam platform using the current repository as the source of truth. It is written for testers, release validators, product owners, and support personnel.

## 2. Environment Prerequisites

- A running local or staging deployment of `web-admin`, `web-discover`, `web-care`, `web-aiva`, and `api-bff`
- A healthy database and object storage backend
- Working authentication provider / local auth setup
- Test browser with cookies and localStorage enabled
- Stable tenant seed data for at least one clinic/hospital tenant

## 3. Browser / Runtime Prerequisites

- Chromium-based browser recommended for parity with existing tests
- Disable ad blockers / script blockers for local runtime verification
- Allow third-party redirects where Keycloak or equivalent auth flow is used
- Retain session storage during refresh/back-navigation tests

## 4. Required Accounts / Personas

Use canonical UAT personas where available. Do not hard-code private customer data in test plans.

| Persona | Purpose | Example attributes |
|---|---|---|
| `TEST-PATIENT-01` | Patient portal and booking | Verified patient OTP or login, appointment-capable |
| `TEST-DOCTOR-01` | Consultation/doctor workspace | Assigned to a tenant, can open consultations |
| `TEST-CLINIC-01` | Clinic admin / operations | Tenant admin or clinic admin permissions |
| `TEST-HOSPITAL-01` | Hospital provider profile | Provider account with publication lifecycle access |
| `TEST-PROVIDER-01` | Provider workspace user | Provider session, ownership, draft/public profile access |
| `TEST-PHARMACY-SUPPLIER-01` | Supplier / procurement | Procurement fixture and inventory access |
| `TEST-LAB-ORDER-01` | Laboratory | Lab test/order fixture with result workflow |
| `TEST-CAMPAIGN-01` | Engage / CarePilot | Campaign maker/checker access |

Repository fixtures may use named live examples. Treat those as evidence fixtures, not the recommended long-term test data contract.

## 5. Role Matrix

At minimum, verify UAT with:

- Platform Admin
- Tenant / Clinic Admin
- Doctor
- Receptionist
- Billing User
- Auditor
- Pharmacist / Pharmacy Inventory Manager / Pharmacy POS User
- Lab Front Desk / Lab Technician / Lab Assistant / Lab Approver
- Engage Manager / Engage Executive
- Provider / provider ownership user
- Patient user

## 6. Recommended Seed / Master Data

- One clinic tenant with all major modules enabled
- One hospital provider profile
- One clinic provider profile
- One doctor public profile
- One patient with known appointments
- One lab test catalogue and one lab order fixture
- One pharmacy inventory fixture with stock movements
- One Engage campaign fixture with approval lifecycle
- One provider connection/ownership fixture

## 7. UAT Execution Order

Recommended sequence:

1. Platform / auth / tenant routing
2. Patient / reception / appointments
3. Doctor workspace / consultation
4. Clinical AI / medication safety
5. Laboratory
6. Pharmacy
7. Billing / finance
8. Engage / CRM
9. Discover public site
10. Connect / provider workspace
11. Care / patient portal
12. Platform Admin workspaces
13. Cross-product E2E regression
14. Final sanity and production smoke

## 8. Golden-Path Scenarios

### GP-01 OPD

Patient registration -> appointment -> reception -> queue -> consultation -> prescription -> billing -> completion

### GP-02 Lab

Consultation -> lab order -> collection -> result entry -> verification -> report publication

### GP-03 Pharmacy

Supplier -> PO -> invoice -> GRN -> inventory -> POS -> reconciliation

### GP-04 Discover-to-Care

Patient searches Discover -> opens provider detail -> books or follows call-to-book -> lands in Care

### GP-05 Provider publication

Provider register -> verify -> draft -> preview -> submit -> platform review -> approve -> publish -> public projection

### GP-06 Hospital publication

Hospital profile draft -> doctor association draft -> preview -> review -> approve -> publish

### GP-07 Engage

Campaign create -> submit -> approve -> activate -> execute -> delivery attempts -> reminders -> closure

### GP-08 Platform admin

Platform mode -> provider applications -> provider connections -> public profile reviews -> audit / conflicts / links

## 9. Evidence Capture Requirements

Capture the following for every UAT run:

- URL
- Role used
- Tenant context
- Timestamp
- Status or lifecycle label observed
- Screenshot of key state
- If applicable, network/API response summary
- If applicable, database record or audit event reference

For critical lifecycle flows, also capture:

- submission/version number
- review state
- publication state
- route after refresh
- role/permission gate result

## 10. Defect Reporting Template

Use this template:

| Field | Value |
|---|---|
| Title | Short symptom summary |
| Environment | local / staging / UAT |
| URL | Exact route |
| Persona | Role used |
| Steps | Minimal reproduction steps |
| Expected | What the implemented system should do |
| Actual | What happened |
| Severity | P0 / P1 / P2 / P3 |
| Evidence | Screenshot / response / log reference |
| Regression risk | What other module might be affected |

## 11. Data Reset / Isolation Strategy

- Prefer isolated seeded fixtures per run when possible.
- Use fresh provider/patient/campaign identifiers for destructive tests.
- Do not reuse a published provider profile if the test will publish or unpublish.
- For read-only verification, use stable published fixtures.
- Clear local browser session / localStorage between persona changes where the app stores state.

## 12. Repeatability Guidance

- Re-run refresh/back-navigation tests after submission, review, and publish actions.
- Verify both direct route entry and in-app navigation.
- Check that URL-backed filters survive page reload where the workspace uses URL-as-state.
- Validate that permission-denied and tenant-missing states are stable and explanatory.

## 13. Production Smoke Subset

Safe smoke pack:

- application shell loads
- login works
- tenant switching works
- dashboard loads
- patient search loads
- doctor workspace opens
- public Discover search loads
- public provider profile loads
- provider login loads
- provider workspace loads
- Platform Admin login and global workspace load
- health/status endpoints respond

Avoid destructive mutations in smoke unless the environment has isolated smoke fixtures.

## 14. Sign-off Checklist

- [ ] All required personas verified
- [ ] All critical routes open
- [ ] No tenant leakage in platform mode
- [ ] No draft leakage to public endpoints
- [ ] Provider publication lifecycle verified
- [ ] Hospital/clinic/doctor public pages verified
- [ ] OPD workflow verified
- [ ] Lab workflow verified
- [ ] Pharmacy workflow verified
- [ ] Engage workflow verified
- [ ] Billing workflow verified
- [ ] Care workflow verified
- [ ] Platform admin workspaces verified
- [ ] Regression evidence captured
- [ ] No unresolved P0/P1 issues remain

