import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("publicProfilesInspectSelectsPublicProfileReference", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");

  assert.ok(page.includes("const [selectedPublicProfileReference"));
  assert.ok(page.includes("const [selectedPublicProfileSnapshot"));
  assert.ok(page.includes("setSelectedPublicProfileReference(row.publicReference)"));
  assert.ok(page.includes("setSelectedPublicProfileSnapshot(row)"));
  assert.ok(page.includes("selectedPublicProfileReference === row.publicReference"));
  assert.ok(page.includes("PublicProfileInspectionPanel"));
});

test("publicProfilesRenderDedicatedDetailsPanelInsteadOfLinksEmptyState", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");

  assert.ok(page.includes('section === "public-profiles" ?'));
  assert.ok(page.includes("Public Profile Details"));
  assert.ok(page.includes("Pick a row from Public Profiles to inspect a specific profile."));
  assert.ok(!page.includes('Pick a row from Links to inspect a specific link.'));
  assert.ok(page.includes('selectedPublicProfileLink'));
  assert.ok(page.includes('selectedPublicProfileOwnership'));
  assert.ok(page.includes('selectedPublicProfileReference === row.publicReference'));
  assert.ok(page.includes("Technical Details"));
  assert.ok(page.includes("Public URL"));
  assert.ok(!page.includes("Business ref ·"));
});

test("publicProfilesPreserveSeparatedLinkSelectionState", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");

  assert.ok(page.includes("const [selectedPublicProfileReference"));
  assert.ok(page.includes("const [selectedPlatformEntityReference"));
  assert.ok(page.includes("const [selectedSuggestionKey"));
  assert.ok(page.includes("const [selectedLinkReference"));
  assert.ok(page.includes("const [selectedOwnershipReference"));
  assert.ok(page.includes("const [selectedAuditEventReference"));
  assert.ok(page.includes("selectedLink ?"));
  assert.ok(page.includes("selectedPublicProfileSnapshot"));
  assert.ok(page.includes("selectedProfile.allowedActions.includes(\"PROPOSE_LINK\")"));
  assert.ok(page.includes("PublicProfileInspectionPanel"));
  assert.ok(page.includes("section === \"links\" ?"));
});
