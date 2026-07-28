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
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");

  assert.ok(app.includes('<ProviderOnboardingPage type="doctor"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="clinic"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="hospital"'));
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
  assert.ok(portal.includes("window.setTimeout"));
  assert.ok(portal.includes("saveDraft(false)"));
  assert.ok(portal.includes("loadProviderApplication(token)"));
  assert.ok(portal.includes("localStorage.setItem(tokenStorageKey"));
  assert.ok(portal.includes("`${TOKEN_KEY}.${providerType}`"));
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
  assert.ok(portal.includes("Submit for verification"));
  assert.ok(portal.includes("Status messages"));
  assert.ok(portal.includes("Missing information"));
  assert.ok(portal.includes("Public profile preview"));
  assert.ok(portal.includes("StatusTimeline"));
  assert.ok(portal.includes("verification-note"));
  assert.ok(!portal.includes("Contact verification placeholder"));
  assert.ok(styles.includes(".provider-stepper"));
  assert.ok(styles.includes(".upload-box"));
});
