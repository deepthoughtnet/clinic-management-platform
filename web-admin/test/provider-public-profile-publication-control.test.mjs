import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("discover provider applications page owns publication moderation controls", () => {
  const page = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(page.includes('openPublicationDialog("UNPUBLISH_PROFILE")'));
  assert.ok(page.includes('openPublicationDialog("REPUBLISH_PROFILE")'));
  assert.ok(page.includes("detail?.canUnpublish"));
  assert.ok(page.includes("detail?.canRepublish"));
  assert.ok(page.includes("currentPublicationStatus === \"PUBLISHED\""));
  assert.ok(page.includes("currentPublicationStatus === \"UNPUBLISHED\" && !canRepublish"));
  assert.ok(page.includes("Publication History"));
  assert.ok(page.includes("Unpublish profile"));
  assert.ok(page.includes("Republish profile"));
  assert.ok(page.includes("unpublishProviderConnectionsPublicProfileReview"));
  assert.ok(page.includes("publishDiscoverProviderApplication"));
  assert.ok(page.includes("republish_requires_review"));
});

test("provider connections page no longer owns publication moderation controls", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");

  assert.ok(!page.includes('openReviewCommand("UNPUBLISH_PROFILE")'));
  assert.ok(!page.includes('openReviewCommand("REPUBLISH_PROFILE")'));
  assert.ok(!page.includes('case "UNPUBLISH_PROFILE":'));
  assert.ok(!page.includes('case "REPUBLISH_PROFILE":'));
  assert.ok(!page.includes("Publication management"));
  assert.ok(!page.includes("Unpublish profile"));
  assert.ok(!page.includes("Republish profile"));
});
