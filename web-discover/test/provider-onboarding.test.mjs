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
  const dashboard = read("src/pages/provider/ProviderDashboardPage.tsx");
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  assert.ok(app.includes("ProviderRegistrationStartPage"));
  assert.ok(app.includes("startProviderApplication"));
  assert.ok(routes.includes("REGISTRATION_PROVIDER_TYPE_BY_ROUTE"));
  assert.ok(routes.includes('providerApplicationDashboard: { path: "/provider/applications/:applicationReference"'));
  assert.ok(app.includes('<Route path={DISCOVER_ROUTES.providerApplicationDashboard.path} element={<ProviderDashboardPage />} />'));
  assert.ok(routes.includes('doctor: "INDIVIDUAL_DOCTOR"'));
  assert.ok(routes.includes('clinic: "CLINIC"'));
  assert.ok(routes.includes('hospital: "HOSPITAL"'));
  assert.ok(dashboard.includes("createProviderOnboardingAccess"));
  assert.ok(dashboard.includes("loadProviderApplicationDashboard"));
  assert.ok(dashboard.includes("navigate(`/provider/onboarding/${access.applicationId}/${step}`)"));
  assert.ok(portal.includes("const steps = ["));
  for (const label of ["Account", "Organisation", "Professional Details", "Services", "Locations", "Branding", "Preview", "Submit"]) {
    assert.ok(portal.includes(label), `${label} step should be present`);
  }
});

test("provider onboarding persists drafts, resumes by token, and keeps URL as step source", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");

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
  assert.ok(portal.includes("loadDiscoverReferenceCatalog"));
  assert.ok(portal.includes("referenceCatalog"));
  assert.ok(portal.includes("READ_ONLY_PROVIDER_STATUSES"));
  assert.ok(portal.includes("isApplicationEditable"));
  assert.ok(portal.includes("applicationEditable"));
  assert.ok(portal.includes("readOnlyApplication"));
  assert.ok(portal.includes("providerReferencePrefix"));
  assert.ok(portal.includes("ProviderDropdownField"));
  assert.ok(portal.includes("ProviderMultiSelectField"));
  assert.ok(portal.includes("ProviderOnboardingStepper"));
  assert.ok(portal.includes("ProviderSaveStatus"));
  assert.ok(profile.includes("PublicMediaImage"));
  assert.ok(portal.includes("providerDocumentContentPath"));
  assert.ok(portal.includes("loadProviderApplication(token)"));
  assert.ok(portal.includes("localStorage.setItem(tokenStorageKey"));
  assert.ok(portal.includes("`${TOKEN_KEY}.${routeProviderType}`"));
  assert.ok(portal.includes('const [token, setToken] = useState(() => (routeProviderType ? "" : readStoredToken(TOKEN_KEYS)));'));
  assert.ok(portal.includes("setToken(\"\");"));
  assert.ok(portal.includes("setApplication(null);"));
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
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");
  const styles = read("src/styles.css");

  assert.ok(portal.includes("Upload PNG, JPEG, or PDF files only."));
  assert.ok(portal.includes("Contact verification required"));
  assert.ok(portal.includes("Send verification email"));
  assert.ok(portal.includes("Send phone OTP"));
  assert.ok(portal.includes("Verify your contact details before submitting your profile."));
  assert.ok(portal.includes("Submit for verification"));
  assert.ok(portal.includes("Status messages"));
  assert.ok(portal.includes("Blocking items"));
  assert.ok(portal.includes("Profile completion"));
  assert.ok(portal.includes("Submission timeline"));
  assert.ok(portal.includes("Submission summary"));
  assert.ok(portal.includes("Public profile preview"));
  assert.ok(portal.includes("provider-portal-layout--preview"));
  assert.ok(portal.includes("provider-workspace--preview"));
  assert.ok(portal.includes("provider-preview-page"));
  assert.ok(portal.includes("provider-preview-banner"));
  assert.ok(portal.includes("provider-preview-page-body"));
  assert.ok(portal.includes("PublicProviderProfile"));
  assert.ok(profile.includes("provider-public-preview"));
  assert.ok(profile.includes("provider-public-hero"));
  assert.ok(profile.includes("provider-public-hero-media"));
  assert.ok(profile.includes("provider-preview-profile"));
  assert.ok(profile.includes("provider-preview-grid"));
  assert.ok(profile.includes("provider-preview-section--about"));
  assert.ok(profile.includes("Professional Information"));
  assert.ok(profile.includes("Trust and Verification"));
  assert.ok(portal.includes("Ready for submission"));
  assert.ok(portal.includes("Verified contact details"));
  assert.ok(portal.includes("provider-preview-checklist"));
  assert.ok(profile.includes("provider-preview-gallery-grid"));
  assert.ok(profile.includes("No clinic gallery images have been added yet."));
  assert.ok(portal.includes("Your public profile contains the required information."));
  assert.ok(portal.includes('goToStep(item.step, "route-transition")'));
  assert.ok(portal.includes("onGalleryEmptyAction"));
  assert.ok(portal.includes("Back to editing"));
  assert.ok(portal.includes("Continue to submission"));
  assert.ok(portal.includes("provider-branding-banner"));
  assert.ok(profile.includes("Book Appointment"));
  assert.ok(profile.includes("provider-public-hero--preview"));
  assert.ok(profile.includes("provider-preview-appointment-card"));
  assert.ok(profile.includes("provider-preview-location"));
  assert.ok(portal.includes("provider-preview-workflow"));
  assert.ok(portal.includes('dataTestId="provider-preview-canvas"'));
  assert.ok(profile.includes('data-testid={dataTestId}'));
  assert.ok(portal.includes('data-testid="provider-preview-readiness-panel"'));
  assert.ok(profile.includes("Consultation fee"));
  assert.ok(profile.includes("Consultation modes"));
  assert.ok(portal.includes('item.trim().toLowerCase() !== "online appointment"'));
  assert.ok(portal.includes("Currently showing verified contact details."));
  assert.ok(portal.includes("previewGalleryPaths.forEach"));
  assert.ok(!portal.includes("push(\n      previewDoctorPhotoPath"));
  assert.ok(!portal.includes("Quick Facts"));
  assert.ok(!portal.includes("<span>{publicPhone}</span>"));
  assert.ok(!portal.includes("Phone: {publicPhone}"));
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
  assert.ok(styles.includes(".provider-portal-layout--preview"));
  assert.ok(styles.includes(".provider-workspace--preview"));
  assert.ok(styles.includes(".provider-preview-banner"));
  assert.ok(styles.includes(".provider-preview-page-body"));
  assert.ok(styles.includes(".submission-summary-grid"));
  assert.ok(styles.includes(".submission-timeline"));
  assert.ok(styles.includes(".provider-public-preview"));
  assert.ok(styles.includes(".provider-public-hero"));
  assert.ok(styles.includes(".provider-public-hero--preview"));
  assert.ok(styles.includes(".provider-preview-grid--paired"));
  assert.ok(styles.includes(".provider-preview-definition-list"));
  assert.ok(styles.includes(".provider-preview-checklist-item"));
  assert.ok(styles.includes(".provider-preview-gallery-card"));
  assert.ok(styles.includes(".provider-preview-gallery-empty"));
  assert.ok(styles.includes(".provider-preview-workflow"));
  assert.ok(styles.includes(".provider-preview-workflow-actions"));
  assert.ok(styles.includes(".provider-preview-appointment-summary"));
  assert.ok(styles.includes(".provider-preview-definition-list__item--wide"));
});

test("provider login uses passwordless provider wording and local-only development codes", () => {
  const page = read("src/pages/provider/ProviderLoginPage.tsx");
  const api = read("src/api/providerAuth.ts");

  assert.ok(page.includes("Send Verification Code"));
  assert.ok(page.includes("List Your Practice"));
  assert.ok(page.includes("Enter your verification code"));
  assert.ok(page.includes("Development verification code"));
  assert.ok(page.includes("Shown only in Local/UAT"));
  assert.ok(page.includes("Change Email or Mobile Number"));
  assert.ok(page.includes("maskRecipient"));
  assert.ok(page.includes("codeInputRef"));
  assert.ok(page.includes("Enter the six-digit code we sent to"));
  assert.ok(page.includes("verificationMode"));
  assert.ok(page.includes("Provider Login"));
  assert.ok(api.includes("verificationMode"));
});

test("provider onboarding lifecycle gating disables editing outside editable statuses", () => {
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const locationPicker = read("src/components/location/LocationPicker.tsx");
  const searchInput = read("src/components/location/LocationSearchInput.tsx");

  assert.ok(portal.includes("const READ_ONLY_PROVIDER_STATUSES: ProviderStatus[] = [\"SUBMITTED\", \"UNDER_REVIEW\", \"APPROVED\", \"PUBLISHED\", \"SUSPENDED\", \"ARCHIVED\"]"));
  assert.ok(portal.includes("return Boolean(status && !READ_ONLY_PROVIDER_STATUSES.includes(status));"));
  assert.ok(portal.includes('providerType !== "INDIVIDUAL_DOCTOR" && !draft.ownership?.trim()'));
  assert.ok(portal.includes("if (!application || !token || !accountHydrated || !isApplicationEditable(application.status)) return;"));
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
