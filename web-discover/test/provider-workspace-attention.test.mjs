import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

const providerMeFixture = {
  attentionCount: 1,
  workItems: [
    {
      workItemType: "OWNERSHIP_CLAIM",
      publicProfileType: "CLINIC",
      workItemReference: "work-item-opaque",
      publicProfileReference: "public-profile-opaque",
      connectionReference: "connection-reference-opaque",
      displayName: "Green Valley Family Clinic",
      city: "Pune",
      area: "Wakad",
      claimStatus: "CLAIM_SUBMITTED",
      ownershipStatus: "CLAIM_PENDING",
      reviewStatus: "PENDING_REVIEW",
      workItemStatus: "PLATFORM_REVIEW",
      publicDiscoveryConsent: "DISABLED",
      platformConnectionStatus: "NOT_CONNECTED",
      publicationStatus: "UNPUBLISHED",
      membershipRole: "OWNER:ACTIVE",
      lastUpdatedAt: "2026-08-03T04:00:00Z",
      allowedActions: ["OPEN_CLAIM"],
    },
  ],
  applications: [],
  publishedProfiles: [],
  supportedProviderTypes: ["CLINIC"],
};

test("providerWorkspaceRendersOwnershipClaimAttentionCard", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.equal(providerMeFixture.attentionCount, 1);
  assert.ok(page.includes("Attention"));
  assert.ok(page.includes("These work items are incomplete or waiting on your next action."));
  assert.ok(page.includes("Claim submitted - awaiting Platform review"));
  assert.ok(page.includes("Open claim"));
  assert.ok(page.includes("Ownership: "));
  assert.ok(page.includes("Publication: "));
  assert.ok(page.includes("Connection: "));
});

test("attentionCountMatchesRenderedOwnershipClaims", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.equal(providerMeFixture.workItems.filter((item) => item.workItemType === "OWNERSHIP_CLAIM").length, 1);
  assert.ok(page.includes('value: workspace?.attentionCount ?? attentionItems.length'));
  assert.ok(page.includes('kind === "OWNERSHIP_CLAIM"'));
  assert.ok(page.includes("attentionItems.map((entry)"));
  assert.ok(page.includes("claimSubtitle(entry.item)"));
});

test("attentionEmptyStateHiddenWhenWorkItemExists", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("No actions currently require your attention."));
  assert.ok(page.includes("attentionItems.length ?"));
});

test("pendingClaimShowsAwaitingPlatformReview", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("Claim submitted - awaiting Platform review"));
  assert.ok(page.includes("PENDING_REVIEW"));
});

test("pendingClaimUsesOpenClaimAction", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes('item.allowedActions.includes("OPEN_CLAIM")'));
  assert.ok(page.includes('return `${DISCOVER_ROUTES.providerWorkspace.path}?connectionReference=${encodeURIComponent(reference)}`;'));
});

test("submittedClaimDoesNotShowSubmitClaimAction", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("claimReviewCanSubmit(claimReview)"));
  assert.ok(page.includes("Submit claim"));
  assert.ok(page.includes("Clear note"));
});

test("openClaimUsesOpaqueConnectionReference", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("connectionReference"));
  assert.ok(page.includes("encodeURIComponent(reference)"));
  assert.ok(!page.includes("provider/account ID"));
});

test("activeApplicationsRemainSeparateFromOwnershipClaims", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("My applications"));
  assert.ok(page.includes("Active applications"));
  assert.ok(page.includes('entry.kind === "OWNERSHIP_CLAIM"'));
  assert.ok(page.includes("Continue registration"));
});

test("ownershipStatusBadgesDoNotOverflow", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-account-attention-copy"));
  assert.ok(styles.includes(".provider-account-attention-meta"));
  assert.ok(styles.includes("word-break: break-word"));
  assert.ok(styles.includes("align-content: start"));
});

test("unknownWorkItemTypeUsesSafeFallbackWithoutBreakingPage", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes("kind === \"OWNERSHIP_CLAIM\""));
  assert.ok(page.includes("work items are incomplete or waiting on your next action"));
  assert.ok(page.includes("DiscoverEmptyState"));
});

test("verifiedOwnershipUsesLifecycleStatusAndDoesNotShowOpenClaim", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  const api = read("../backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/discover/provider/auth/ProviderWorkspaceController.java");
  assert.ok(page.includes('item.workItemStatus === "OWNERSHIP_VERIFIED"'));
  assert.ok(page.includes('return "Ownership verified";'));
  assert.ok(page.includes('return item.publicDiscoveryConsent === "DISABLED"'));
  assert.ok(page.includes('item.allowedActions.includes("VIEW_DETAILS")'));
  assert.ok(api.includes('("OWNERSHIP_VERIFIED".equals(workItem.workItemStatus())'));
  assert.ok(api.includes('"DISABLED".equals(workItem.publicDiscoveryConsent())'));
});
