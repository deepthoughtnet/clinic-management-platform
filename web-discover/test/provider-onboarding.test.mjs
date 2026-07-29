import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider onboarding routes use one governed wizard for all provider types", () => {
  const app = read("src/App.tsx");
  const routes = read("src/routes.ts");
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  assert.ok(app.includes('<ProviderOnboardingPage type="doctor"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="clinic"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="hospital"'));
  assert.ok(routes.includes("REGISTRATION_PROVIDER_TYPE_BY_ROUTE"));
  assert.ok(routes.includes('doctor: "INDIVIDUAL_DOCTOR"'));
  assert.ok(routes.includes('clinic: "CLINIC"'));
  assert.ok(routes.includes('hospital: "HOSPITAL"'));
  assert.ok(portal.includes("const steps = ["));
  for (const label of ["Account", "Organisation", "Professional Details", "Services", "Locations", "Branding", "Preview", "Submit"]) {
    assert.ok(portal.includes(label), `${label} step should be present`);
  }
});

test("provider onboarding persists drafts, resumes by token, and keeps URL as step source", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  assert.ok(portal.includes("useSearchParams"));
  assert.ok(portal.includes('searchParams.get("step")'));
  assert.ok(portal.includes("setSearchParams({ step })"));
  assert.ok(portal.includes("type AccountStepValues"));
  assert.ok(portal.includes("mapAccountStepValues"));
  assert.ok(portal.includes("setAccountHydrated(true)"));
  assert.ok(portal.includes("accountHydrated"));
  assert.ok(portal.includes("window.setTimeout"));
  assert.ok(portal.includes("requestSave(\"autosave\""));
  assert.ok(portal.includes("requestSave(\"manual\""));
  assert.ok(portal.includes("requestSave(\"preview\""));
  assert.ok(portal.includes("goToStep("));
  assert.ok(portal.includes("savePromiseRef"));
  assert.ok(portal.includes("latestVersionRef"));
  assert.ok(portal.includes("EDITABLE_PROVIDER_STATUSES"));
  assert.ok(portal.includes("isApplicationEditable"));
  assert.ok(portal.includes("applicationEditable"));
  assert.ok(portal.includes("readOnlyApplication"));
  assert.ok(portal.includes("providerReferencePrefix"));
  assert.ok(portal.includes("ProviderDropdownField"));
  assert.ok(portal.includes("ProviderMultiSelectField"));
  assert.ok(portal.includes("ProviderOnboardingStepper"));
  assert.ok(portal.includes("ProviderSaveStatus"));
  assert.ok(portal.includes("PublicMediaImage"));
  assert.ok(portal.includes("providerDocumentContentPath"));
  assert.ok(portal.includes("loadProviderApplication(token)"));
  assert.ok(portal.includes("localStorage.setItem(tokenStorageKey"));
  assert.ok(portal.includes("`${TOKEN_KEY}.${routeProviderType}`"));
  assert.ok(portal.includes("providerType: routeProviderType ?? providerType"));
});

test("provider onboarding uses provider-registration APIs and not patient-private APIs", () => {
  const api = read("src/api/providerOnboarding.ts");
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  for (const endpoint of [
    "/api/provider-registration/providers",
    "/api/provider-registration/providers/me",
    "/documents",
    "/preview",
    "/submit",
  ]) {
    assert.ok(api.includes(endpoint), `${endpoint} should be used`);
  }
  assert.ok(api.includes("X-Provider-Onboarding-Token"));
  assert.ok(!api.includes("X-Patient-Session"));
  assert.ok(!api.includes("/api/patient-portal"));
  assert.ok(!portal.includes("PatientDashboard"));
});

test("provider onboarding upload and submission UX is explicit", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const styles = read("src/styles.css");

  assert.ok(portal.includes("Upload PNG, JPEG, or PDF files only."));
  assert.ok(portal.includes("Contact verification required"));
  assert.ok(portal.includes("Send verification email"));
  assert.ok(portal.includes("Send phone OTP"));
  assert.ok(portal.includes("Verify your contact details before submitting your profile."));
  assert.ok(portal.includes("Submit for verification"));
  assert.ok(portal.includes("Status messages"));
  assert.ok(portal.includes("Missing information"));
  assert.ok(portal.includes("Public profile preview"));
  assert.ok(portal.includes("provider-public-preview"));
  assert.ok(portal.includes("provider-public-hero-media"));
  assert.ok(portal.includes("provider-public-gallery"));
  assert.ok(portal.includes("Appointment CTA"));
  assert.ok(portal.includes("StatusTimeline"));
  assert.ok(portal.includes("verification-note"));
  assert.ok(portal.includes("provider-readonly-banner"));
  assert.ok(portal.includes("Application submitted"));
  assert.ok(!portal.includes("Contact verification placeholder"));
  assert.ok(styles.includes(".provider-stepper"));
  assert.ok(styles.includes(".upload-box"));
  assert.ok(styles.includes(".provider-field"));
  assert.ok(styles.includes(".provider-save-status"));
  assert.ok(styles.includes(".provider-public-preview"));
  assert.ok(styles.includes(".provider-readonly-banner"));
  assert.ok(styles.includes(".provider-readonly-fieldset"));
});

test("provider onboarding lifecycle gating disables editing outside editable statuses", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const locationPicker = read("src/components/location/LocationPicker.tsx");
  const searchInput = read("src/components/location/LocationSearchInput.tsx");

  assert.ok(portal.includes("const EDITABLE_PROVIDER_STATUSES: ProviderStatus[] = [\"DRAFT\", \"CHANGES_REQUESTED\"]"));
  assert.ok(portal.includes("if (!application || !token || !accountHydrated || !applicationEditable) return;"));
  assert.ok(portal.includes("if (!applicationEditable) return;"));
  assert.ok(portal.includes("traceSave(reason, \"skipped\", { snapshot: \"read-only\" })"));
  assert.ok(portal.includes("autosaveEnabled={application ? applicationEditable : false}"));
  assert.ok(portal.includes("editable={applicationEditable}"));
  assert.ok(portal.includes("application && applicationEditable ?"));
  assert.ok(locationPicker.includes("editable = true"));
  assert.ok(locationPicker.includes("interactive={editable}"));
  assert.ok(locationPicker.includes("disabled={!editable}"));
  assert.ok(searchInput.includes("disabled?: boolean"));
  assert.ok(searchInput.includes("Find on Map"));
  assert.ok(searchInput.includes("Use Current Location"));
});

test("provider onboarding no longer uses the old parallel saveDraft coordinator", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  assert.ok(!portal.includes("saveStateRef"));
  assert.ok(!portal.includes("saveDraft(false)"));
  assert.ok(!portal.includes("saveDraft(true)"));
});

test("provider onboarding selector primitives are structured and searchable", () => {
  const fields = read("src/components/provider-onboarding/ProviderOnboardingFields.tsx");
  const stepper = read("src/components/provider-onboarding/ProviderOnboardingStepper.tsx");

  assert.ok(fields.includes("Autocomplete"));
  assert.ok(fields.includes("Chip"));
  assert.ok(fields.includes("select"));
  assert.ok(fields.includes("filterSelectedOptions"));
  assert.ok(fields.includes("onChange={(event) => onChange(event.target.value)}"));
  assert.ok(stepper.includes("aria-current"));
  assert.ok(stepper.includes("provider-save-status"));
  assert.ok(stepper.includes("Autosave enabled"));
});
