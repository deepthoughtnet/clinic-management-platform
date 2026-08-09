import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient portal shell keeps branded care entry and authenticated chrome", () => {
  const appSource = readSource("App.tsx");
  const headerSource = readSource("components/GlobalPatientHeader.tsx");
  const careShell = readSource("components/CareShell.tsx");

  assert.ok(appSource.includes("GlobalPatientHeader"));
  assert.ok(appSource.includes("CareFooter"));
  assert.ok(headerSource.includes("Patient Login"));
  assert.ok(headerSource.includes("Find Care"));
  assert.ok(headerSource.includes("Clinic / Hospital Login"));
  assert.ok(careShell.includes("CarePublicEntryHeader"));
  assert.ok(careShell.includes("CareLoginHero"));
  assert.ok(careShell.includes("CareEntrySecurityStrip"));
  assert.ok(careShell.includes("CareEntrySessionNotice"));
  assert.ok(careShell.includes("care-authenticated-header"));
  assert.ok(careShell.includes("care-authenticated-header__badge"));
});

test("patient login and dashboard remain branded and data-driven", () => {
  const portalSource = readSource("pages/patient/PatientPortalPages.tsx");

  assert.ok(portalSource.includes("Welcome to Jeevanam Care"));
  assert.ok(portalSource.includes("Sign in to Jeevanam Care"));
  assert.ok(portalSource.includes("patient-dashboard-stack"));
  assert.ok(portalSource.includes("patient-dashboard-quick-actions"));
  assert.ok(portalSource.includes("patient-dashboard-attention"));
  assert.ok(portalSource.includes("patient-sidebar-branding"));
  assert.ok(portalSource.includes("patient-widget-heading__icon"));
  assert.ok(portalSource.includes("patient-dashboard-quick-action__icon"));
  assert.ok(portalSource.includes("patient-stat-card__featured-date"));
  assert.ok(portalSource.includes("patient-highlight-card--appointment"));
  assert.ok(portalSource.includes("status-pill--compact"));
  assert.ok(portalSource.includes("No visit scheduled"));
  assert.ok(portalSource.includes("No payment due"));
  assert.ok(portalSource.includes("No recent care activity"));
  assert.ok(portalSource.includes("Ask AIVA about your care journey"));
  assert.ok(portalSource.includes("Latest consultation bill paid"));
  assert.ok(portalSource.includes("Vaccination records are not currently available in Jeevanam Care"));
});

test("dashboard hover and compactness styles are present", () => {
  const stylesSource = readSource("styles.css");

  assert.ok(stylesSource.includes(".patient-dashboard-quick-action:hover"));
  assert.ok(stylesSource.includes(".patient-dashboard-attention-item:hover"));
  assert.ok(stylesSource.includes(".patient-dashboard-activity-item:hover"));
  assert.ok(stylesSource.includes(".patient-stat-card--featured"));
  assert.ok(stylesSource.includes(".patient-highlight-card--appointment"));
  assert.ok(stylesSource.includes(".patient-sidebar-branding"));
  assert.ok(stylesSource.includes(".care-authenticated-header__badge"));
  assert.ok(stylesSource.includes(".status-pill--compact"));
  assert.ok(stylesSource.includes(".patient-dashboard-quick-actions-grid"));
  assert.ok(stylesSource.includes("repeat(5, minmax(0, 1fr))"));
  assert.ok(stylesSource.includes(".patient-dashboard-dual-grid"));
  assert.ok(stylesSource.includes("repeat(2, minmax(0, 1fr))"));
});

test("patient registration session cleanup is centralized", () => {
  const source = readSource("pages/patient/patientPortalSessionState.ts");
  assert.ok(source.includes("clearPatientRegistrationSession"));
  assert.ok(source.includes("clearPatientAuthSession"));
  assert.ok(source.includes("PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY"));
  assert.ok(source.includes("PATIENT_PORTAL_SESSION_STORAGE_KEY"));
  assert.ok(source.includes("clearPublicBookingContext"));
  assert.ok(source.includes("isPatientPortalSessionTokenActive"));
});
