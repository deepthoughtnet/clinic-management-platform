import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient login is simplified", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  assert.ok(source.includes("Sign in with phone number and OTP."));
  assert.ok(source.includes("Access appointments, prescriptions, bills, reports, and care updates securely."));
  assert.ok(source.includes("DoctorClinicSelector"));
  assert.ok(source.includes("Have a clinic code?"));
  assert.ok(source.includes("Clinic code or clinic link"));
  assert.ok(source.includes("Please select a clinic first or use a clinic link."));
  assert.ok(!source.includes("<span>Clinic code</span>"));
  assert.ok(!source.includes("Read-only patient rollout"));
  assert.ok(!source.includes("Tenant-aware session"));
  assert.ok(!source.includes("Patient-safe cards"));
  assert.ok(!source.includes("Separate from admin"));
  assert.ok(!source.includes("Clinic code is managed internally"));
});

test("patient login blocks otp actions when no clinic context is available", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  assert.ok(source.includes("MISSING_CLINIC_CODE_MESSAGE"));
  assert.ok(source.includes("disabled={requestPending || !clinicFallbackValue}"));
  assert.ok(source.includes("disabled={verifyPending || !clinicFallbackValue}"));
});

test("doctor booking routes through clinic context", () => {
  const source = readSource("pages/public/PublicDiscoveryPages.tsx");
  assert.ok(source.includes("patientPortalBookingPath(session, {"));
  assert.ok(source.includes("doctorSlug: doctor.doctorSlug"));
  assert.ok(source.includes("doctorName: doctor.doctorDisplayName"));
  assert.ok(source.includes("clinicCode: clinic.clinicSlug"));
  assert.ok(source.includes("clinicName: clinic.clinicDisplayName"));
});

test("single clinic is auto-applied and multi-clinic selector exists", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");
  const selector = readSource("pages/patient/DoctorClinicSelector.tsx");
  assert.ok(source.includes("doctorDetail.data.clinics.length !== 1"));
  assert.ok(source.includes("setTenantCode(onlyClinic.clinicSlug)"));
  assert.ok(source.includes("DoctorClinicSelector"));
  assert.ok(selector.includes("Select a clinic"));
});

test("clinic context helper resolves query params and booking path", () => {
  const source = readSource("pages/patient/patientPortalClinicContext.ts");
  assert.ok(source.includes('searchParams.get("clinicCode")'));
  assert.ok(source.includes('searchParams.get("clinic")'));
  assert.ok(source.includes('searchParams.get("clinicSlug")'));
  assert.ok(source.includes('searchParams.get("tenant")'));
  assert.ok(source.includes('searchParams.get("tenantSlug")'));
  assert.ok(source.includes('searchParams.get("doctorSlug")'));
  assert.ok(source.includes('searchParams.get("doctorName")'));
  assert.ok(source.includes('searchParams.get("next")'));
  assert.ok(source.includes("patientPortalBookingPath("));
});

test("public nav and footer branding are cleaned up", () => {
  const source = readSource("App.tsx");
  assert.equal(source.split('label: "AIVA"').length - 1, 1);
  assert.ok(source.includes('label: "AI Assistant"'));
  assert.ok(source.includes('brand-badge">JH</span>'));
  assert.ok(!source.includes('brand-badge">AR</span>'));
  assert.ok(source.includes("footer-brand-line"));
  assert.ok(source.includes("Intelligent Healthcare Platform for clinics, patients, and teams."));
  assert.ok(source.includes("© 2026 DeepThoughtNet."));
});

test("homepage location selector persists common cities", () => {
  const source = readSource("pages/public/PublicDiscoveryPages.tsx");
  assert.ok(source.includes("PUBLIC_LOCATION_STORAGE_KEY"));
  assert.ok(source.includes("PUBLIC_LOCATION_OPTIONS"));
  assert.ok(source.includes("smart-location-selector"));
  assert.ok(source.includes("savePublicLocation"));
  assert.ok(source.includes("readStoredPublicLocation"));
  assert.ok(source.includes("selectedLocation"));
  assert.ok(source.includes("Pune"));
  assert.ok(source.includes("Mumbai"));
  assert.ok(source.includes("Bangalore"));
  assert.ok(source.includes("Delhi"));
  assert.ok(source.includes("Hyderabad"));
  assert.ok(source.includes("Chennai"));
});
