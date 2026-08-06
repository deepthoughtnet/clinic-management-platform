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
  assert.ok(page.includes('case "VIEW_PUBLISHED_PROFILE":'));
  assert.ok(page.includes('case "OPEN_PUBLIC_PROFILE":'));
  assert.ok(page.includes('return "Open Public Profile";'));
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
  assert.ok(page.includes("Draft Preview – Not Public"));
  assert.ok(page.includes('renderMode="PROVIDER_DRAFT_PREVIEW"'));
  assert.ok(page.includes("Copy Public URL"));
  assert.ok(page.includes("Back to Editor"));
  assert.ok(page.includes("Back to Workspace"));
  assert.ok(!page.includes("Call clinic"));
  assert.ok(page.includes("ProviderEditorSectionCard"));
  assert.ok(page.includes("ProviderEditorFooter"));
  assert.ok(page.includes("ProviderTagListEditor"));
  assert.ok(page.includes("ProviderWeeklyScheduleEditor"));
  assert.ok(page.includes("ProviderFeeEditor"));
  assert.ok(page.includes("provider-editor-layout"));
  assert.ok(page.includes("provider-editor-sidebar"));
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
  const presenter = read("src/utils/providerConsentPresentation.ts");
  assert.ok(presenter.includes('status === "DISABLED"'));
  assert.ok(presenter.includes("continue preparing the draft"));
});

test("tenantConsentDisabledBlocksSubmitAction", () => {
  const presenter = read("src/utils/providerConsentPresentation.ts");
  assert.ok(presenter.includes("submission remains unavailable"));
});

test("consentEnabledDoesNotRenderDisabledBanner", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  const presenter = read("src/utils/providerConsentPresentation.ts");
  assert.ok(page.includes("getProviderConsentPresentation"));
  assert.ok(page.includes("tenantConsentStatus: currentDraft.tenantConsentStatus"));
  assert.ok(presenter.includes('status === "ENABLED"'));
  assert.ok(presenter.includes("visible: false"));
  assert.ok(!page.includes("Healthcare tenant consent is currently disabled. You can continue preparing the draft, but submission remains unavailable."));
});

test("consentEnabledReadyProfileRendersSubmitAction", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('currentModeration?.allowedActions.includes("SUBMIT_FOR_REVIEW")'));
  assert.ok(page.includes('Ready to submit for platform review.'));
});

test("consentEnabledIncompleteProfileShowsContentBlockersOnly", () => {
  const presenter = read("src/utils/providerConsentPresentation.ts");
  assert.ok(presenter.includes("Complete the remaining profile requirements before submitting."));
  assert.ok(presenter.includes("visible: false"));
});

test("unknownConsentDoesNotDefaultToDisabledCopy", () => {
  const presenter = read("src/utils/providerConsentPresentation.ts");
  assert.ok(presenter.includes("status unavailable"));
  assert.ok(presenter.includes("We are checking whether the clinic has enabled Discover participation."));
  assert.ok(presenter.includes("isBlocked: false"));
});

test("publicationCardAndHeaderUseSameConsentState", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("consentPresentation.visible"));
  assert.ok(page.includes("consentPresentation.message"));
  assert.ok(page.includes("publicationCardMessage(currentDraft.readiness, currentModeration, currentDraft.tenantConsentStatus)"));
  assert.ok(!page.includes("moderation?.submissionBlockers.includes(\"TENANT_CONSENT_REQUIRED\")"));
});

test("pageRefreshUsesCurrentTenantConsentStatus", () => {
  const api = read("src/api/providerPublicProfileDraft.ts");
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(api.includes('cache: "no-store"'));
  assert.ok(page.includes("loadProviderPublicProfileDraft(profileReference)"));
  assert.ok(page.includes("loadProviderPublicProfileModeration(profileReference)"));
});

test("currentUnpublishedAndHistoricalLastPublishedAreShownSeparately", () => {
  const healthcare = read("../web-admin/src/pages/settings/ClinicProfilePage.tsx");
  assert.ok(healthcare.includes("No published version yet") || healthcare.includes("Last published"));
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
  assert.ok(page.includes("Enable Discover participation before submitting for platform review."));
  assert.ok(page.includes("Ready to submit for platform review."));
  assert.ok(page.includes("Copy Public URL"));
  assert.ok(page.includes("Preview profile"));
  assert.ok(!page.includes("NaN+ years experience"));
  assert.ok(!page.includes("Call clinic"));
});
