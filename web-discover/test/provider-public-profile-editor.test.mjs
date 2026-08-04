import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("verifiedOwnershipShowsOpenPublicProfile", () => {
  const page = read("src/pages/provider/ProviderWorkspacePage.tsx");
  assert.ok(page.includes('item.allowedActions.includes("OPEN_PUBLIC_PROFILE")'));
  assert.ok(page.includes('return "Open public profile";'));
});

test("editorUsesUrlDrivenSections", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("useParams<{ profileReference?: string; section?: string }>()"));
  assert.ok(page.includes("sectionRoute(profileReference, item)"));
  assert.ok(page.includes("const currentSection = useMemo(() => SECTION_ORDER.includes(section) ? section : \"overview\", [section]);"));
});

test("missingFieldLinksOpenCorrectSection", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("missingFieldSection"));
  assert.ok(page.includes("sectionMissingLabel"));
});

test("saveDraftDisplaysSavedTimestamp", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Last saved"));
  assert.ok(page.includes("formatDateTime(activeDraft.lastSavedAt ?? activeDraft.updatedAt)"));
});

test("previewShowsDraftNotPublicBanner", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("DRAFT PREVIEW - NOT PUBLIC"));
  assert.ok(page.includes('renderMode="PROVIDER_DRAFT_PREVIEW"'));
  assert.ok(page.includes("Copy Public URL"));
  assert.ok(page.includes("Back to editor"));
  assert.ok(page.includes("Back to workspace"));
  assert.ok(!page.includes("Call clinic"));
  assert.ok(page.includes('currentDraft.publicProfileStatus === "PUBLISHED" ? "View Public Profile" : "Preview Draft"'));
});

test("mediaEditorDoesNotRenderDocumentIdInputs", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(!page.includes("Logo document id"));
  assert.ok(!page.includes("Cover document id"));
  assert.ok(!page.includes("Gallery document ids"));
  assert.ok(page.includes("Upload logo"));
  assert.ok(page.includes("Upload cover image"));
  assert.ok(page.includes("Add more images"));
});

test("mediaPreviewUsesPersistedDraftMedia", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("providerPublicProfileDraftMediaContentPath"));
  assert.ok(page.includes("PublicMediaImage"));
  assert.ok(page.includes("galleryAltTextByDocumentId"));
  assert.ok(page.includes("primaryGalleryDocumentId"));
});

test("tenantConsentDisabledDoesNotBlockEditing", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Healthcare tenant consent is currently disabled"));
  assert.ok(page.includes("continue preparing the draft"));
});

test("tenantConsentDisabledBlocksSubmitAction", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("submission remains unavailable"));
});

test("readinessChecklistRendersGroupedMissingItems", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Profile readiness"));
  assert.ok(page.includes("missingFieldCategorySection"));
  assert.ok(page.includes("missingFieldChipLabel"));
  assert.ok(page.includes("readableLifecycleLabel"));
});

test("draftPreviewUsesSafeAddressAndExperienceFormatting", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("buildPublicAddressView"));
  assert.ok(page.includes("resolveClinicEstablishedYear"));
  assert.ok(page.includes("publicationBlockSummary"));
  assert.ok(page.includes("Content Ready"));
  assert.ok(page.includes("Waiting for the clinic administrator to enable Discover publishing."));
  assert.ok(page.includes("Copy Public URL"));
  assert.ok(!page.includes("NaN+ years experience"));
});
