import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider application detail page shows business labels, grouped blockers, and discard modal", () => {
  const page = read("src/pages/provider/ProviderDashboardPage.tsx");

  assert.ok(page.includes("Your {providerTypeLabel(application.providerType)} application"));
  assert.ok(page.includes("Continue registration"));
  assert.ok(page.includes("More actions"));
  assert.ok(page.includes("Preview profile"));
  assert.ok(page.includes("Complete all required items before submission."));
  assert.ok(page.includes("What remains"));
  assert.ok(page.includes("Current step:"));
  assert.ok(page.includes("Discard onboarding"));
  assert.ok(page.includes("This application will be removed from active onboarding."));
  assert.ok(page.includes("providerRequirementLabel"));
  assert.ok(page.includes("groupProviderRequirements"));
  assert.ok(page.includes("provider-dashboard-actions-menu"));
  assert.ok(page.includes("provider-dashboard-modal"));
  assert.ok(!page.includes("CLINIC_NAME_REQUIRED"));
  assert.ok(!page.includes("PRIMARY_LOCATION_REQUIRED"));
  assert.ok(!page.includes("No actions currently require your attention."));
});

test("provider application detail page uses readable timeline labels and current state text", () => {
  const page = read("src/pages/provider/ProviderDashboardPage.tsx");
  const backend = read("../backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/onboarding/ProviderOnboardingService.java");

  assert.ok(page.includes("currentTimelineLabel"));
  assert.ok(page.includes("formatDateTime"));
  assert.ok(backend.includes('case DRAFT -> "Registration started";'));
  assert.ok(backend.includes('case CONTACT_VERIFIED -> "Contact verified";'));
  assert.ok(backend.includes('case CHANGES_REQUESTED -> "Changes requested";'));
  assert.ok(backend.includes('case PUBLISHED -> "Published";'));
});
