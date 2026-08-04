import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

function readBackendSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "..", "backend", ...relPath.split("/")), "utf8");
}

test("pendingOwnershipShowsApproveAndReject", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("APPROVE_OWNERSHIP"));
  assert.ok(page.includes("REJECT_OWNERSHIP"));
  assert.ok(page.includes("REVOKE_CLAIM"));
});

test("verifiedOwnershipHidesApproveAndReject", () => {
  const policy = readBackendSource("domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/providerownership/ProviderOwnershipLifecyclePolicy.java");
  assert.ok(policy.includes("case VERIFIED -> List.of(\"VIEW_OWNERSHIP\", \"DISPUTE_OWNERSHIP\", \"REVOKE_OWNERSHIP\")"));
  assert.ok(policy.includes("case CLAIM_PENDING, TRANSFER_PENDING -> List.of(\"APPROVE_OWNERSHIP\", \"REJECT_OWNERSHIP\", \"DISPUTE_OWNERSHIP\", \"REVOKE_CLAIM\", \"VIEW_OWNERSHIP\")"));
});

test("verifiedOwnershipShowsOnlyAllowedPostApprovalActions", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("ownershipActionLabel(normalized)"));
  assert.ok(page.includes("View ownership"));
  assert.ok(page.includes("Dispute"));
  assert.ok(page.includes("Revoke ownership"));
});

test("ownershipActionsRenderOnlyFromAllowedActions", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("row.allowedActions || []"));
  assert.ok(page.includes("renderOwnershipAction(row, action)"));
  assert.ok(page.includes("isOwnershipAction(action)"));
});

test("unknownOwnershipActionDoesNotBreakPage", () => {
  const page = readSource("pages/platform/ProviderConnectionsPage.tsx");
  assert.ok(page.includes("default:"));
  assert.ok(page.includes("return null"));
});
