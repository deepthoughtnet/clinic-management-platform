import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("platform review media uses the admin authenticated blob helper", () => {
  const api = readSource("api/clinicApi.ts");
  const hook = readSource("hooks/useAuthenticatedImage.ts");
  const preview = readSource("components/platform-review/PlatformPublicProfileReviewPreview.tsx");

  assert.ok(api.includes("fetchPlatformPublicProfileReviewMedia"));
  assert.ok(api.includes("providerPublicProfileReviewMediaContentPath"));
  assert.ok(api.includes("platformOperation: true"));
  assert.ok(api.includes("accept: \"image/*,*/*\""));
  assert.ok(hook.includes("parsePlatformPublicProfileReviewMediaPath"));
  assert.ok(hook.includes("fetchPlatformPublicProfileReviewMedia"));
  assert.ok(hook.includes("!auth.tenantId && !platformReviewMedia"));
  assert.ok(hook.includes("abortController.abort()"));
  assert.ok(hook.includes("revokeObjectUrl(current)"));
  assert.ok(preview.includes("Loading media..."));
  assert.ok(preview.includes("Image unavailable"));
  assert.ok(preview.includes("media.coverDocumentId"));
  assert.ok(preview.includes("media.logoDocumentId"));
  assert.ok(preview.includes("media.gallery"));
  assert.ok(preview.includes("function profileInitials"));
  assert.ok(preview.includes("function ReviewLogo"));
  assert.ok(preview.includes("<Avatar"));
  assert.ok(preview.includes("objectFit: \"contain\""));
  assert.ok(preview.includes("initials={profileInitials(reviewText(about, \"displayName\") || review.publicProfileReference)}"));
  assert.ok(preview.includes("mediaReference={media.logoDocumentId}"));
});

test("platform review media path remains submission scoped", () => {
  const api = readSource("api/clinicApi.ts");
  assert.ok(api.includes("/api/platform/provider-connections/public-profile-reviews/${encodeURIComponent(submissionReference)}/media/${encodeURIComponent(mediaReference)}/content"));
});
