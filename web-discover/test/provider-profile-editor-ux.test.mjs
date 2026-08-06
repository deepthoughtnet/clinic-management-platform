import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("editorUsesApplicationTypographyAndColorTokens", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  const helper = read("src/components/provider-profile-editor/ProviderProfileEditorControls.tsx");
  assert.ok(page.includes("ProviderEditorSectionCard"));
  assert.ok(helper.includes('variant="contained"'));
  assert.ok(helper.includes('variant="outlined"'));
  assert.ok(helper.includes('sx={{ fontWeight: 900 }}'));
});

test("primaryButtonsUseSharedApplicationVariant", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Save changes"));
  assert.ok(page.includes("Preview profile"));
  assert.ok(page.includes("Continue"));
  assert.ok(page.includes("provider-editor-layout"));
  assert.ok(page.includes("provider-editor-sidebar"));
  assert.ok(page.includes("provider-editor-main"));
  assert.ok(page.includes("provider-editor-page-header"));
});

test("sectionNavigationShowsActiveSection", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Profile Sections"));
  assert.ok(page.includes('SECTION_ORDER.map((item) => ('));
  assert.ok(page.includes('aria-current={item === currentSection ? "page" : undefined}'));
  assert.ok(page.includes("provider-editor-nav-item"));
  assert.ok(page.includes("provider-editor-nav-badge"));
  assert.ok(!page.includes('title="Workflow"'));
});

test("responsiveEditorDoesNotOverflow", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-editor-layout"));
  assert.ok(styles.includes(".provider-editor-sidebar"));
  assert.ok(styles.includes("position: sticky"));
  assert.ok(styles.includes("grid-template-columns: minmax(0, 3.2fr) minmax(286px, 296px)"));
  assert.ok(styles.includes("max-height: none"));
  assert.ok(styles.includes("overflow: visible"));
  assert.ok(styles.includes("@media (max-width: 980px)"));
  assert.ok(styles.includes("@media (max-width: 720px)"));
});

test("previewHeaderDoesNotReserveLargeBlankHeight", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  const styles = read("src/styles.css");
  assert.ok(page.includes("provider-editor-page-header"));
  assert.ok(page.includes("provider-editor-page-header__copy"));
  assert.ok(styles.includes("max-width: 76ch"));
  assert.ok(!page.includes("provider-status-hero-card"));
});

test("statusCardAndTitleShareNaturalHeightGrid", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("provider-editor-page-header"));
  assert.ok(page.includes("provider-status-layout provider-editor-layout"));
  assert.ok(page.includes("provider-status-main provider-editor-main"));
  assert.ok(page.includes("provider-status-side provider-editor-sidebar"));
});

test("contentFollowsHeaderWithStandardSpacing", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-status-page"));
  assert.ok(styles.includes(".provider-editor-page-header"));
  assert.ok(styles.includes(".provider-status-layout"));
  assert.ok(styles.includes("gap: 20px"));
});

test("sidebarDoesNotIncreaseHeaderRowHeight", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-status-side"));
  assert.ok(styles.includes("max-height: none"));
  assert.ok(styles.includes("overflow: visible"));
  assert.ok(styles.includes("top: 88px"));
});

test("stickyFooterShowsSaveState", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("ProviderEditorFooter"));
  assert.ok(page.includes("Save changes"));
  assert.ok(page.includes("Back to workspace"));
});

test("workflowCardUsesBusinessLabels", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Profile status"));
  assert.ok(page.includes("Draft Version"));
  assert.ok(page.includes("Content"));
  assert.ok(page.includes("Publication"));
  assert.ok(page.includes("Owner"));
  assert.ok(page.includes("Next step"));
  assert.ok(page.includes("Enable Discover") || page.includes("Submit for Platform Review"));
});

test("publicationCardUsesBusinessLanguage", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Publication"));
  assert.ok(page.includes("Not Published"));
  assert.ok(page.includes("Public URL"));
  assert.ok(page.includes("Enable Discover participation before submitting for platform review.") || page.includes("Ready to submit for platform review."));
});

test("duplicateWorkflowCardIsRemoved", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("Profile status"));
  assert.ok(page.includes("Profile Sections"));
  assert.ok(!page.includes('title="Workflow"'));
  assert.ok(!page.includes("Preview and submission controls stay consistent across the editor."));
});

test("mainAndSidebarUseIndependentColumns", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("provider-status-layout provider-editor-layout"));
  assert.ok(page.includes("provider-status-main provider-editor-main"));
  assert.ok(page.includes("provider-status-side provider-editor-sidebar"));
  assert.ok(page.includes("provider-editor-page-header"));
});

test("readinessShowsSinglePercentageIndicator", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('h2>{currentDraft.completenessPercentage}% complete</h2>'));
  assert.ok(page.includes('span>{isCompletionReady ? "Content Ready" : "Profile needs more information"}</span>'));
});

test("optionalBadgesUseSubtlePresentation", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-editor-nav-badge.is-optional"));
  assert.ok(styles.includes("background: rgba(148, 163, 184, 0.14)"));
  assert.ok(styles.includes("color: #475569"));
});

test("sidebarIsStickyOnDesktop", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-editor-sidebar"));
  assert.ok(styles.includes("position: sticky"));
});

test("sidebarStacksOnMobile", () => {
  const styles = read("src/styles.css");
  assert.ok(styles.includes(".provider-editor-sidebar {"));
  assert.ok(styles.includes("position: static"));
});

test("lifecycleStatusIsNotEditable", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('InputProps={{ readOnly: true }}'));
  assert.ok(page.includes("Summary status"));
});

test("taglineShowsCharacterCount", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('label="Short tagline"'));
  assert.ok(page.includes("Preview snippet"));
});

test("establishedYearUsesValidatedYearInput", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('label="Established year"'));
  assert.ok(page.includes("Four-digit year only."));
});

test("emergencyAvailabilityUsesGuidedOptions", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes('label="Emergency availability"'));
  assert.ok(page.includes("Available during clinic hours"));
});

test("servicesUseTagEditor", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("ProviderTagListEditor"));
  assert.ok(page.includes("SERVICE_SUGGESTIONS"));
  assert.ok(page.includes("Add service"));
});

test("specialitiesUseMultiSelect", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  const helper = read("src/components/provider-profile-editor/ProviderProfileEditorControls.tsx");
  assert.ok(page.includes("SPECIALITY_SUGGESTIONS"));
  assert.ok(helper.includes("Set as primary"));
  assert.ok(helper.includes("Primary speciality"));
});

test("facilitiesUseSelectableOptions", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("FACILITY_SUGGESTIONS"));
  assert.ok(page.includes("Add facility"));
});

test("languagesUseCanonicalMultiSelect", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(page.includes("LANGUAGE_SUGGESTIONS"));
  assert.ok(page.includes("Add language"));
});

test("rawJsonTimingInputIsAbsent", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  assert.ok(!page.includes('JSON.stringify(sectionContent(currentDraft, "timings")'));
  assert.ok(!page.includes('helperText="JSON array of day intervals."'));
  assert.ok(page.includes("ProviderWeeklyScheduleEditor"));
});

test("currencyAwareFeeInputsRender", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");
  const helper = read("src/components/provider-profile-editor/ProviderProfileEditorControls.tsx");
  assert.ok(page.includes("ProviderFeeEditor"));
  assert.ok(helper.includes("Consultation fees"));
  assert.ok(helper.includes("Currency"));
});
