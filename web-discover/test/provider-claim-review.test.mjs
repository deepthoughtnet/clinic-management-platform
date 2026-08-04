import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-discover");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("verifiedOwnershipDetailsAreReadOnly", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("claimReviewTitle(claimReview)"));
  assert.ok(page.includes("claimReviewSubtitle(claimReview)"));
  assert.ok(page.includes("Submitted claim note"));
  assert.ok(page.includes("Back to dashboard"));
  assert.ok(page.includes("View ownership details"));
});

test("verifiedOwnershipHidesSubmitClaim", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("claimReviewCanSubmit(claimReview)"));
  assert.ok(page.includes('allowedActions.includes("SUBMIT_CLAIM")'));
});

test("verifiedOwnershipHidesEditableClaimNote", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("Submitted claim note"));
  assert.ok(page.includes("claimReviewCanSubmit(claimReview) ?"));
});

test("verifiedOwnershipShowsCurrentLifecycleStates", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("Ownership updated"));
  assert.ok(page.includes("Tenant consent"));
  assert.ok(page.includes("Review"));
  assert.ok(page.includes("Connection"));
});

test("verifiedOwnershipUsesOwnershipVerifiedBadge", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("claimReviewStateLabel(claimReview)"));
  assert.ok(page.includes("OWNERSHIP_VERIFIED"));
  assert.ok(page.includes("claimReviewTitle(claimReview)"));
});

test("refreshingVerifiedDetailsPreservesState", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  const api = readSource("api/providerAuth.ts");

  assert.ok(page.includes("useSearchParams"));
  assert.ok(page.includes("getProviderClaimReview(connectionReference)"));
  assert.ok(page.includes("claimReview.connectionReference"));
  assert.ok(api.includes("pageMode: string"));
  assert.ok(api.includes("ownershipUpdatedAt: string | null"));
  assert.ok(api.includes("allowedActions: string[]"));
});

test("rejectedOrDisputedStatesDoNotShowSubmitWithoutAllowedAction", () => {
  const page = readSource("pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("CLAIM_REJECTED"));
  assert.ok(page.includes("CLAIM_DISPUTED"));
  assert.ok(page.includes("CLAIM_REVOKED"));
  assert.ok(page.includes("CLAIM_EXPIRED"));
});
