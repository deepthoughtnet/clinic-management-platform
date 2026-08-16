import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("reviewWorkspaceListsProviderReviewQueue", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("Public Profile Reviews"));
  assert.ok(page.includes("Open review workspace"));
  assert.ok(page.includes("Pending review"));
});

test("reviewDetailIsReadOnly", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("PlatformPublicProfileReviewPreview"));
  assert.ok(page.includes("You are reviewing the exact profile version submitted by the Provider."));
  assert.ok(page.includes("allowedActions"));
  assert.ok(page.includes("Action panel"));
  assert.ok(page.includes("Review actions"));
  assert.ok(!page.includes("Publication management"));
});

test("reviewActionsRenderFromBackendAllowedActions", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("row.allowedActions || []"));
  assert.ok(page.includes("Actions are rendered only from backend allowedActions."));
});

test("publishedProfilesStillExposeReviewVisibility", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("publicationStatus"));
  assert.ok(page.includes("View public profile"));
});

test("reviewPreviewUsesAdminMediaPath", () => {
  const page = readSource("components/platform-review/PlatformPublicProfileReviewPreview.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(page.includes("Timings"));
  assert.ok(page.includes("Reviewer-only SEO"));
  assert.ok(page.includes("review.mediaSnapshot"));
  assert.ok(api.includes("providerPublicProfileReviewMediaContentPath"));
  assert.ok(api.includes("/api/platform/provider-connections/public-profile-reviews/"));
});
