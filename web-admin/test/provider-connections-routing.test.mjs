import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("provider connections nested review routes resolve to the review workspace", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");

  assert.ok(page.includes("export function providerConnectionsSectionFromPathname"));
  assert.ok(page.includes('pathname === "/platform/provider-connections/public-profile-lifecycle"'));
  assert.ok(page.includes('normalized.startsWith(`${section.path}/`)'));
  assert.ok(page.includes('return prefix?.key || "overview";'));
  assert.ok(page.includes('const match = location.pathname.match(/^\\/platform\\/provider-connections\\/public-profile-reviews\\/([^/]+)$/)'));
  assert.ok(page.includes('reviewDetailPath(row.submissionReference)'));
  assert.ok(page.includes('selectedReviewReference !== selectedReviewRouteReference'));
  assert.ok(page.includes('section !== "public-profile-reviews" ?'));
});
