import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("unclaimedOwnershipShowsConnectProviderAccount", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(page.includes("clinicPresenceCanCreateClaim(presence)"));
  assert.ok(page.includes("Connect a Provider account"));
});

test("pendingOwnershipDoesNotShowCreateAnotherClaim", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(page.includes("Provider ownership is pending"));
  assert.ok(page.includes("allowedActions?.includes(\"OPEN_PROVIDER_DASHBOARD\")"));
});

test("verifiedOwnershipShowsOpenProviderDashboard", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(page.includes("Open Provider Dashboard"));
  assert.ok(page.includes("Provider ownership is verified. Manage public profile details in the Provider workspace."));
});

test("verifiedOwnershipDoesNotShowConnectProviderAccount", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(page.includes("clinicPresenceCanCreateClaim(presence)"));
  assert.ok(page.includes("clinicPresenceActionLabel(presence)"));
  assert.ok(page.includes("allowedActions?.includes(\"CONNECT_PROVIDER_ACCOUNT\")"));
});

test("ownershipUpdateTimestampRenders", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(page.includes("Last ownership update"));
  assert.ok(page.includes("formatPresenceDateTime(presence?.ownershipUpdatedAt)"));
});

test("publicProfileSynchronizationLabelIsPrecise", () => {
  const page = readSource("pages/settings/ClinicProfilePage.tsx");
  const api = readSource("api/clinicApi.ts");
  const config = readSource("config.ts");

  assert.ok(page.includes("Public profile last synchronized"));
  assert.ok(page.includes("formatPresenceDateTime(presence?.publicProfileSynchronizedAt ?? presence?.lastSynchronizedAt)"));
  assert.ok(page.includes("Draft lifecycle"));
  assert.ok(page.includes("presence?.draftStatus ?? \"NO_DRAFT\""));
  assert.ok(page.includes("Copy connection reference"));
  assert.ok(page.includes("Revoke tenant consent"));
  assert.ok(page.includes("clinicPresenceConnectionReference(presence, claimIntent)"));
  assert.ok(page.includes("returnTo=${encodeURIComponent(intent.returnTo)}"));
  assert.ok(api.includes("ownershipUpdatedAt: string | null"));
  assert.ok(api.includes("publicProfileSynchronizedAt: string | null"));
  assert.ok(api.includes("draftStatus: string | null"));
  assert.ok(api.includes("allowedActions: string[]"));
  assert.ok(api.includes("connectionReference: string | null"));
  assert.ok(config.includes("providerAppUrl"));
  assert.ok(config.includes("VITE_PROVIDER_APP_URL"));
  assert.ok(config.includes("5177"));
  assert.ok(!config.includes("5173"));
});
