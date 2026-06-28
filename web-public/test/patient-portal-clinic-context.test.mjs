import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("global patient header and location context are shared", () => {
  const appSource = readSource("App.tsx");
  const headerSource = readSource("components/GlobalPatientHeader.tsx");
  const locationSource = readSource("context/publicLocation.tsx");
  assert.ok(appSource.includes("GlobalPatientHeader"));
  assert.ok(appSource.includes("PublicLocationProvider"));
  assert.ok(appSource.includes('path="/ai-assistant/*"'));
  assert.ok(headerSource.includes("Patient Login"));
  assert.ok(headerSource.includes("Demo Links"));
  assert.ok(headerSource.includes("global-location-pill"));
  assert.ok(headerSource.includes("patientPortalHomePath"));
  assert.ok(locationSource.includes("PUBLIC_LOCATION_OPTIONS"));
  assert.ok(locationSource.includes("PUBLIC_DEFAULT_LOCATION"));
  assert.ok(locationSource.includes("PublicLocationProvider"));
  assert.ok(locationSource.includes("normalizePublicLocation"));
});

test("patient login keeps otp flow phone-only and preserves next-step context", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  assert.ok(source.includes("sanitizePatientOtpInput"));
  assert.ok(source.includes("sanitizePatientPortalErrorMessage"));
  assert.ok(source.includes("patient-login-context"));
  assert.ok(source.includes("patient-login-otp-payload"));
  assert.ok(source.includes("registrationSessionToken"));
  assert.ok(source.includes("recoverableRegistrationSession"));
  assert.ok(source.includes("Cancel / Start over"));
  assert.ok(source.includes("Complete patient registration."));
});

test("booking pages still wire clinic and slot selection", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  const selector = readSource("pages/patient/DoctorClinicSelector.tsx");
  assert.ok(source.includes("DoctorClinicSelector"));
  assert.ok(source.includes("syncBookingClinicContext(clinic.clinicSlug)"));
  assert.ok(source.includes("loadPatientPortalDoctorSlots("));
  assert.ok(source.includes("doctorId: slotRequestDoctorId"));
  assert.ok(source.includes("tenantId: slotRequestTenantId"));
  assert.ok(source.includes("clinicId: slotRequestClinicId"));
  assert.ok(selector.includes("Select a clinic"));
});

test("public nav and footer branding are cleaned up", () => {
  const source = readSource("App.tsx");
  const headerSource = readSource("components/GlobalPatientHeader.tsx");
  const portalSource = readSource("pages/patient/PatientPortalPages.tsx");
  assert.ok(headerSource.includes('label: "AIVA"'));
  assert.ok(headerSource.includes('label: "Patient Login"'));
  assert.ok(!source.includes('label: "AI Assistant"'));
  assert.ok(source.includes("GlobalPatientHeader"));
  assert.ok(source.includes("path=\"/ai-assistant/*\""));
  assert.ok(headerSource.includes('brand-badge">JH</span>'));
  assert.ok(source.includes("footer-brand-line"));
  assert.ok(source.includes("footer-environment-line"));
  assert.ok(source.includes('Link to="/patient/login"'));
  assert.ok(source.includes('Link to="/contact"'));
  assert.ok(source.includes('Link to="/privacy-policy"'));
  assert.ok(headerSource.includes("Demo Links"));
  assert.ok(portalSource.includes("Jeevanam Healthcare Patient Portal"));
  assert.ok(portalSource.includes("GlobalPatientHeader"));
  assert.ok(portalSource.includes("patient-appointments-page"));
  assert.ok(portalSource.includes("patient-appointments-toolbar"));
  assert.ok(portalSource.includes("patient-appointment-list"));
  assert.ok(portalSource.includes("notificationSummaryLabel"));
  assert.ok(portalSource.includes("formatNotificationText"));
  assert.ok(portalSource.includes("profileValidation.success"));
  assert.ok(portalSource.includes("New appointment, bill, prescription, and lab updates will appear here."));
});

test("patient registration session cleanup is centralized", () => {
  const source = readSource("pages/patient/patientPortalSessionState.ts");
  assert.ok(source.includes("clearPatientRegistrationSession"));
  assert.ok(source.includes("clearPatientAuthSession"));
  assert.ok(source.includes("PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY"));
  assert.ok(source.includes("PATIENT_PORTAL_SESSION_STORAGE_KEY"));
  assert.ok(source.includes("clearPublicBookingContext"));
});

test("homepage location selector persists common cities", () => {
  const source = readSource("pages/public/PublicDiscoveryPages.tsx");
  const locationSource = readSource("context/publicLocation.tsx");
  assert.ok(source.includes("smart-location-selector"));
  assert.ok(source.includes("smart-location-actions"));
  assert.ok(source.includes("readStoredPublicLocation"));
  assert.ok(source.includes("selectedLocation"));
  assert.ok(source.includes("Use my current location"));
  assert.ok(locationSource.includes("savePublicLocation"));
  assert.ok(locationSource.includes("Current location selected"));
  assert.ok(locationSource.includes("PUBLIC_LOCATION_STORAGE_KEY"));
  assert.ok(locationSource.includes("PUBLIC_LOCATION_COORDS_STORAGE_KEY"));
  assert.ok(locationSource.includes("PUBLIC_LOCATION_SOURCE_STORAGE_KEY"));
  assert.ok(locationSource.includes("PUBLIC_LOCATION_OPTIONS"));
  assert.ok(locationSource.includes("Pune"));
  assert.ok(locationSource.includes("Bhopal"));
});
