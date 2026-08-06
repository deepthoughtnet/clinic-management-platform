import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), relPath), "utf8");
}

test("platform moderation review preview stays inside web-admin", () => {
  const page = read("src/pages/platform/ProviderConnectionsPage.tsx");
  const preview = read("src/components/platform-review/PlatformPublicProfileReviewPreview.tsx");
  const api = read("src/api/clinicApi.ts");

  assert.ok(!page.includes("web-discover/src"));
  assert.ok(page.includes("PlatformPublicProfileReviewPreview"));
  assert.ok(page.includes("row.allowedActions || []"));
  assert.ok(page.includes("Actions are rendered only from backend allowedActions."));
  assert.ok(preview.includes("review.contentSnapshot"));
  assert.ok(preview.includes("review.mediaSnapshot"));
  assert.ok(preview.includes("Timings"));
  assert.ok(preview.includes("Closed"));
  assert.ok(preview.includes("Asia/Kolkata"));
  assert.ok(preview.includes("Submitted version"));
  assert.ok(preview.includes("Decision reason"));
  assert.ok(preview.includes("No provider-facing message recorded."));
  assert.ok(preview.includes("Loading media..."));
  assert.ok(!preview.includes('<Typography variant="caption" color="text.secondary">{reference}</Typography>'));
  assert.ok(preview.includes("Reviewer-only SEO"));
  assert.ok(preview.includes("review.findings"));
  assert.ok(api.includes("providerPublicProfileReviewMediaContentPath"));
  assert.ok(api.includes("/api/platform/provider-connections/public-profile-reviews/"));
});
