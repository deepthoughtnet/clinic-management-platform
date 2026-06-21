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
  assert.ok(source.includes("Please select a clinic or doctor before requesting OTP."));
  assert.ok(source.includes("aria-invalid={Boolean(phoneError)}"));
  assert.ok(source.includes("aria-invalid={Boolean(otpError)}"));
  assert.ok(source.includes("aria-invalid={Boolean(clinicFallbackError)}"));
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
  assert.ok(source.includes("sanitizePatientPhoneInput"));
  assert.ok(source.includes("sanitizePatientOtpInput"));
  assert.ok(source.includes("sanitizePatientPortalErrorMessage"));
  assert.ok(source.includes("isPatientPortalLocalDev"));
  assert.ok(source.includes("otpRequestSchema.shape.mobile.safeParse(phone)"));
  assert.ok(source.includes("otpVerifySchema.shape.otp.safeParse(otp)"));
  assert.ok(source.includes("noValidate"));
  assert.ok(!source.includes("pattern=\"^\\\\d{10}$\""));
  assert.ok(!source.includes("pattern=\"^\\\\d{6}$\""));
  assert.ok(!source.includes("pattern=\"^[A-Za-z0-9-]{1,60}$\""));
  assert.ok(source.includes('placeholder="Enter 10-digit mobile number"'));
  assert.ok(source.includes("disabled={requestPending || !canRequestOtp}"));
  assert.ok(source.includes("disabled={verifyPending || !canVerifyOtp}"));
  assert.ok(source.includes("OTP is not available in this environment. Use dev OTP mode or check mock OTP config."));
  assert.ok(source.includes("Patient mobile number is invalid."));
  assert.ok(source.includes("Enter a valid 10-digit Indian mobile number."));
  assert.ok(source.includes("Enter a valid 6-digit OTP."));
  assert.ok(source.includes("isPatientPortalLocalDev() && requestState?.accepted"));
  assert.ok(source.includes('requestState.devOtp || "123456"'));
  assert.ok(source.includes("Please select a clinic or doctor before requesting OTP."));
});

test("doctor booking routes through clinic context", () => {
  const source = readSource("pages/public/PublicDiscoveryPages.tsx");
  assert.ok(source.includes("patientPortalBookingTo(session, {"));
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
  assert.ok(source.includes("PatientPortalRouteState"));
  assert.ok(source.includes('searchParams.get("clinicCode")'));
  assert.ok(source.includes('searchParams.get("clinicId")'));
  assert.ok(source.includes('searchParams.get("clinic")'));
  assert.ok(source.includes('searchParams.get("clinicSlug")'));
  assert.ok(source.includes('searchParams.get("tenantId")'));
  assert.ok(source.includes('searchParams.get("tenant")'));
  assert.ok(source.includes('searchParams.get("tenantSlug")'));
  assert.ok(source.includes('searchParams.get("doctorSlug")'));
  assert.ok(source.includes('searchParams.get("doctorName")'));
  assert.ok(source.includes('searchParams.get("next")'));
  assert.ok(source.includes("patientPortalBookingPath("));
  assert.ok(source.includes("patientPortalBookingTo("));
  assert.ok(source.includes("selectedClinic"));
  assert.ok(source.includes("demo-clinic"));
  assert.ok(source.includes("Demo Clinic"));
});

test("public nav and footer branding are cleaned up", () => {
  const source = readSource("App.tsx");
  assert.equal(source.split('label: "AIVA"').length - 1, 1);
  assert.ok(source.includes('label: "AI Assistant"'));
  assert.ok(source.includes('brand-badge">JH</span>'));
  assert.ok(!source.includes('brand-badge">AR</span>'));
  assert.ok(source.includes("footer-brand-line"));
  assert.ok(source.includes("footer-environment-line"));
  assert.ok(source.includes("Intelligent Healthcare Platform for clinics, patients, and teams."));
  assert.ok(source.includes("© 2026 DeepThoughtNet."));
});

test("homepage location selector persists common cities", () => {
  const source = readSource("pages/public/PublicDiscoveryPages.tsx");
  assert.ok(source.includes("COMING SOON"));
  assert.ok(source.includes("DEMO / UAT ENVIRONMENT"));
  assert.ok(source.includes("Currently available for invited clinics, demonstrations and UAT testing."));
  assert.ok(source.includes("PUBLIC_LOCATION_STORAGE_KEY"));
  assert.ok(source.includes("PUBLIC_LOCATION_COORDS_STORAGE_KEY"));
  assert.ok(source.includes("PUBLIC_LOCATION_SOURCE_STORAGE_KEY"));
  assert.ok(source.includes("PUBLIC_LOCATION_OPTIONS"));
  assert.ok(source.includes("smart-location-selector"));
  assert.ok(source.includes("smart-location-actions"));
  assert.ok(source.includes("savePublicLocation"));
  assert.ok(source.includes("readStoredPublicLocation"));
  assert.ok(source.includes("selectedLocation"));
  assert.ok(source.includes("selectedCoordinates"));
  assert.ok(source.includes("locationDraft"));
  assert.ok(source.includes("hasSavedPublicLocation"));
  assert.ok(source.includes("hasHydratedLocation"));
  assert.ok(source.includes("const displayLocation = selectedLocation;"));
  assert.ok(source.includes("city: displayLocation"));
  assert.ok(source.includes("lat: `${selectedCoordinates.latitude}`"));
  assert.ok(source.includes("Pune"));
  assert.ok(source.includes("Mumbai"));
  assert.ok(source.includes("Bangalore"));
  assert.ok(source.includes("Delhi"));
  assert.ok(source.includes("Hyderabad"));
  assert.ok(source.includes("Chennai"));
  assert.ok(source.includes("Bhopal"));
  assert.ok(source.includes("Use my current location"));
  assert.ok(source.includes("Current location selected"));
  assert.ok(source.includes("navigator.geolocation"));
  assert.ok(source.includes("Location permission was not allowed. Please select your city manually."));
});
