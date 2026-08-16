import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider dashboard renders a shared post-submission status experience", () => {
  const page = read("src/pages/provider/ProviderDashboardPage.tsx");

  assert.ok(page.includes("ProviderApplicationStatusBanner"));
  assert.ok(page.includes("ProviderApplicationStatusCard"));
  assert.ok(page.includes("ProviderApplicationReviewSummary"));
  assert.ok(page.includes("ProviderApplicationSummary"));
  assert.ok(page.includes("ProviderApplicationNextSteps"));
  assert.ok(page.includes("ProviderApplicationTimeline"));
  assert.ok(page.includes("ProviderSubmittedPreview"));
  assert.ok(page.includes("Application Submitted Successfully"));
  assert.ok(page.includes("Reference"));
  assert.ok(page.includes("View Submitted Preview"));
  assert.ok(page.includes("View Public Profile"));
  assert.ok(page.includes("Continue registration"));
  assert.ok(page.includes("Discard onboarding"));
  assert.ok(page.includes("provider-status-page"));
  assert.ok(page.includes("provider-status-banner"));
  assert.ok(page.includes("provider-status-card"));
  assert.ok(page.includes("provider-status-timeline"));
  assert.ok(page.includes("provider-status-preview"));
  assert.ok(page.includes("provider-status-history-resolved"));
  assert.ok(page.includes("provider-dashboard-modal"));
  assert.ok(page.includes("Current stage"));
  assert.ok(page.includes("Next steps"));
  assert.ok(page.includes("What has been completed"));
  assert.ok(page.includes("Submitted snapshot"));
  assert.ok(page.includes("copyToClipboard"));
  assert.ok(page.includes("providerDocumentContentPath"));
  assert.ok(page.includes("publicProfilePath"));
  assert.ok(page.includes("Your application has been approved and is waiting for publication."));
  assert.ok(page.includes("Your published profile is live on Jeevanam Discover."));
  assert.ok(page.includes("Resolved reviewer comments"));
  assert.ok(page.includes("currentStateMessage"));
  assert.ok(!page.includes("No actions currently require your attention."));
});

test("provider dashboard uses readable lifecycle labels and immutable snapshot data", () => {
  const page = read("src/pages/provider/ProviderDashboardPage.tsx");
  const backend = read("../backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/onboarding/ProviderOnboardingService.java");

  assert.ok(page.includes("statusLabel(application.status)"));
  assert.ok(page.includes("currentStageLabel(application.status)"));
  assert.ok(page.includes("timelineStages(dashboard.application.status)"));
  assert.ok(page.includes("provider-status-history"));
  assert.ok(page.includes("submittedSnapshot?.submittedAt"));
  assert.ok(page.includes("snapshot.versionNumber"));
  assert.ok(page.includes("snapshot.displayName"));
  assert.ok(page.includes("snapshot.galleryDocumentIds"));
  assert.ok(page.includes("provider-status-history-item"));
  assert.ok(page.includes("Resolved reviewer comment"));
  assert.ok(backend.includes('case DRAFT -> "Registration started";'));
  assert.ok(backend.includes('case CONTACT_VERIFIED -> "Contact verified";'));
  assert.ok(backend.includes('case SUBMITTED -> "Submitted for verification";'));
  assert.ok(backend.includes('case CHANGES_REQUESTED -> "Changes requested";'));
  assert.ok(backend.includes('case PUBLISHED -> "Published";'));
});
