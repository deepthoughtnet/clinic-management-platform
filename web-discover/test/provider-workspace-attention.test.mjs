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
  assert.ok(page.includes("Each card shows the exact blocker that needs your next action."));
  assert.ok(page.includes("Provider profile"));
  assert.ok(page.includes("profileActionLabel(entry.profile)"));
  assert.ok(page.includes("Submit for Platform Review"));
  assert.ok(page.includes("Ownership: "));
  assert.ok(page.includes("Publication: "));
  assert.ok(page.includes("Connection: "));
});

test("attentionCountMatchesRenderedOwnershipClaims", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.equal(providerMeFixture.workItems.filter((item) => item.workItemType === "OWNERSHIP_CLAIM").length, 1);
  assert.ok(page.includes('value: workspace.needsAttentionCount'));
  assert.ok(page.includes('kind === "PUBLIC_PROFILE"'));
  assert.ok(page.includes('kind === "OWNERSHIP_CLAIM"'));
  assert.ok(page.includes("attentionItems.map((entry)"));
  assert.ok(page.includes("profileAttentionReason(entry.profile)"));
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
  assert.ok(page.includes("claimPrimaryActionLabel(entry.item)"));
  assert.ok(page.includes("claimPrimaryActionHref(entry.item)"));
  assert.ok(page.includes("connectionReference"));
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
  assert.ok(page.includes("My Provider Profiles"));
  assert.ok(page.includes("Active Profiles"));
  assert.ok(page.includes("providerProfiles"));
  assert.ok(page.includes("profile.lifecycleLabel"));
  assert.ok(page.includes('entry.kind === "OWNERSHIP_CLAIM"'));
  assert.ok(page.includes("Create another profile"));
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
  assert.ok(page.includes("Each card shows the exact blocker that needs your next action."));
  assert.ok(page.includes("DiscoverEmptyState"));
});

test("verifiedOwnershipUsesLifecycleStatusAndDoesNotShowOpenClaim", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  const api = read("../backend/domains/discover-domain/src/main/java/com/deepthoughtnet/clinic/discover/providerownership/ProviderOwnershipLifecyclePolicy.java");
  assert.ok(page.includes('item.workItemStatus === "OWNERSHIP_VERIFIED"'));
  assert.ok(page.includes('return "Ownership verified";'));
  assert.ok(page.includes('return item.publicDiscoveryConsent === "DISABLED"'));
  assert.ok(page.includes('case "VIEW_DETAILS":'));
  assert.ok(api.includes('case "OWNERSHIP_VERIFIED" -> List.of('));
  assert.ok(api.includes('"CREATE_PUBLIC_PROFILE_DRAFT"'));
  assert.ok(api.includes('"VIEW_PREVIEW"'));
  assert.ok(api.includes('"VIEW_READINESS"'));
  assert.ok(api.includes('"OPEN_PUBLIC_PROFILE"'));
  assert.ok(api.includes('"VIEW_DETAILS"'));
});
