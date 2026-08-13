import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider review status page uses dedicated review rendering and safe reviewer copy", () => {
  const page = read("src/pages/provider/ProviderPublicProfileReviewPage.tsx");
  const renderer = read("src/components/landing/LandingPageRenderer.tsx");
  const api = read("src/api/providerPublicProfileReview.ts");

  assert.ok(page.includes('renderMode="PROVIDER_REVIEW_STATUS"'));
  assert.ok(page.includes("Submitted version"));
  assert.ok(page.includes("Platform review team"));
  assert.ok(page.includes("View submitted preview"));
  assert.ok(page.includes("Decision reason"));
  assert.ok(page.includes("No provider-facing message recorded."));
  assert.ok(api.includes("providerPublicProfileReviewMediaContentPath"));
  assert.ok(api.includes("/submissions/"));
  assert.ok(renderer.includes("Submission under Platform review"));
  assert.ok(renderer.includes("Later draft changes will not affect this submitted snapshot."));
  assert.ok(renderer.includes("renderMode === \"PROVIDER_REVIEW_STATUS\""));
  assert.ok(renderer.includes("Public visibility: Not published"));
});
