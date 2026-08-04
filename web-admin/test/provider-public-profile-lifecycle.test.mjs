import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lifecycleWorkspaceListsProviderDraft", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("Public Profile Lifecycle"));
  assert.ok(page.includes("Draft"));
  assert.ok(page.includes("Draft last saved"));
});

test("inspectDraftIsReadOnly", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("Ownership actions are controlled by the backend `allowedActions` list for this row."));
  assert.ok(page.includes("allowedActions"));
  assert.ok(page.includes("View public profile"));
});

test("noApproveOrPublishActionsInE2B1", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("row.allowedActions || []"));
  assert.ok(page.includes("renderOwnershipAction(row, action)"));
});

test("legacyPublishedProfilesRemainVisible", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("publicationStatus"));
  assert.ok(page.includes("View public profile"));
});

