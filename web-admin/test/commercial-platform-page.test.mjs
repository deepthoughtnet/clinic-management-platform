import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import * as esbuild from "esbuild";

  const {
    AuthContext,
    CommercialPlansPage,
    CommercialPlatformPage,
    CommercialPlanTemplateSummarySection,
    CommercialPlanTemplateCreateDialog,
    CommercialPlanTemplateFields,
    CommercialPlanSelectionDialog,
    ValidationFindingCard,
    defaultTemplateForm,
    mapDraftResponseState,
    buildDraftSavePayload,
    toggleSelectedIds,
    filterSelectionItems,
  selectedSelectionItems,
  computeConfiguredCount,
  isDialogDirty,
} = await buildCommercialPlatformBundle();

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

async function buildCommercialPlatformBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".commercial-platform-bundle-"));
  const entryPath = path.join(tempDir, "entry.tsx");
  const bundlePath = path.join(tempDir, "bundle.mjs");
    fs.writeFileSync(
    entryPath,
    `import { AuthContext } from "${path.join(srcRoot, "auth", "AuthContext.ts").replace(/\\/g, "/")}";\n` +
      `import CommercialPlansPage from "${path.join(srcRoot, "pages", "platform", "CommercialPlansPage.tsx").replace(/\\/g, "/")}";\n` +
      `import CommercialPlatformPage from "${path.join(srcRoot, "pages", "platform", "CommercialPlatformPage.tsx").replace(/\\/g, "/")}";\n` +
      `import { CommercialPlanTemplateCreateDialog } from "${path.join(srcRoot, "pages", "platform", "CommercialPlanTemplateEditor.tsx").replace(/\\/g, "/")}";\n` +
      `import { TemplateFields as CommercialPlanTemplateFields } from "${path.join(srcRoot, "pages", "platform", "CommercialPlanTemplateEditor.tsx").replace(/\\/g, "/")}";\n` +
      `import { CommercialPlanTemplateSummarySection, defaultTemplateForm } from "${path.join(srcRoot, "pages", "platform", "CommercialPlanTemplateEditor.tsx").replace(/\\/g, "/")}";\n` +
      `import CommercialPlanSelectionDialog, { toggleSelectedIds, filterSelectionItems, selectedSelectionItems, computeConfiguredCount, isDialogDirty } from "${path.join(srcRoot, "pages", "platform", "CommercialPlanSelectionDialog.tsx").replace(/\\/g, "/")}";\n` +
      `import { ValidationFindingCard } from "${path.join(srcRoot, "pages", "platform", "CommercialPlansPage.tsx").replace(/\\/g, "/")}";\n` +
      `import { mapDraftResponseState, buildDraftSavePayload } from "${path.join(srcRoot, "pages", "platform", "CommercialPlansPage.tsx").replace(/\\/g, "/")}";\n` +
      `export { AuthContext, CommercialPlansPage, CommercialPlatformPage, CommercialPlanTemplateCreateDialog, CommercialPlanTemplateFields, CommercialPlanTemplateSummarySection, CommercialPlanSelectionDialog, ValidationFindingCard, defaultTemplateForm, mapDraftResponseState, buildDraftSavePayload, toggleSelectedIds, filterSelectionItems, selectedSelectionItems, computeConfiguredCount, isDialogDirty };\n`,
    "utf8",
  );
  await esbuild.build({
    entryPoints: [entryPath],
    bundle: true,
    platform: "node",
    format: "esm",
    outfile: bundlePath,
    logLevel: "silent",
    jsx: "automatic",
    external: [
      "@emotion/react",
      "@emotion/styled",
      "@mui/icons-material",
      "@mui/material",
      "@mui/system",
      "@mui/utils",
      "react-transition-group",
      "react",
      "react/jsx-runtime",
      "react/jsx-dev-runtime",
      "react-dom",
      "react-dom/server",
      "react-router-dom",
    ],
    define: {
      "import.meta.env.VITE_KEYCLOAK_URL": JSON.stringify("http://localhost:8182"),
      "import.meta.env.VITE_KEYCLOAK_REALM": JSON.stringify("clinic-management"),
      "import.meta.env.VITE_KEYCLOAK_CLIENT_ID": JSON.stringify("clinic-web-admin"),
      "import.meta.env.VITE_API_BASE_URL": JSON.stringify(""),
      "import.meta.env.VITE_APP_VERSION": JSON.stringify("0.0.0"),
      "import.meta.env.DEV": "false",
      "import.meta.env.MODE": JSON.stringify("test"),
    },
  });
  const mod = await import(bundlePath);
  fs.rmSync(tempDir, { recursive: true, force: true });
  return mod;
}

test("commercial platform navigation and routes are registered", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const topBar = readSource("layout/TopBar.tsx");
  const registry = readSource("modules/moduleRegistry.ts");

  assert.ok(app.includes('path="/platform/commercial"'));
  assert.ok(app.includes('path="/platform/commercial/catalog"'));
  assert.ok(app.includes('path="/platform/commercial/plans"'));
  assert.ok(app.includes('path="/platform/commercial/plans/:templateId"'));
  assert.ok(app.includes('path="/platform/commercial/plans/:templateId/versions/:versionId"'));
  assert.ok(app.includes('path="/platform/commercial/subscriptions"'));
  assert.ok(app.includes('path="/platform/commercial/subscriptions/:subscriptionId"'));
  assert.ok(app.includes('to="/platform/commercial/catalog"'));

  assert.ok(nav.includes('label: "Commercial Platform"'));
  assert.ok(nav.includes('label: "Catalog"'));
  assert.ok(nav.includes('label: "Plans"'));
  assert.ok(nav.includes('label: "Subscriptions"'));
  assert.ok(nav.includes('label: "Usage"'));
  assert.ok(nav.includes('label: "Billing"'));

  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/plans")'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/subscriptions")'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/catalog")'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial")'));

  assert.ok(registry.includes('path === "/platform/commercial"'));
  assert.ok(registry.includes('path === "/platform/commercial/catalog"'));
  assert.ok(registry.includes('path === "/platform/commercial/plans"'));
  assert.ok(registry.includes('path.startsWith("/platform/commercial/plans/")'));
});

test("commercial platform overview and plans workspace use URL state and typed APIs", () => {
  const overview = readSource("pages/platform/CommercialPlatformPage.tsx");
  const plans = readSource("pages/platform/CommercialPlansPage.tsx");
  const editor = readSource("pages/platform/CommercialPlanTemplateEditor.tsx");
  const api = readSource("api/clinicApi.ts");

  assert.ok(overview.includes('Commercial Platform'));
  assert.ok(overview.includes('Catalog only. Tenant access still comes from the existing legacy plan and module entitlement path.'));
  assert.ok(overview.includes('getCommercialPlatformOverview'));
  assert.ok(overview.includes('(overview?.kpis || []).map'));
  assert.ok(overview.includes('(overview?.lifecycle || []).map'));

  assert.ok(plans.includes('Commercial Plans'));
  assert.ok(plans.includes('Create Plan Template'));
  assert.ok(plans.includes('Save Draft'));
  assert.ok(plans.includes('Publish Version'));
  assert.ok(plans.includes('Commercial Plan Builder'));
  assert.ok(plans.includes('value="validation"'));
  assert.ok(plans.includes('Run Validation'));
  assert.ok(plans.includes('Change Summary'));
  assert.ok(plans.includes('Metadata Changes'));
  assert.ok(plans.includes('Version History'));
  assert.ok(plans.includes('Identity'));
  assert.ok(plans.includes('Capabilities'));
  assert.ok(plans.includes('Modules'));
  assert.ok(plans.includes('Features'));
  assert.ok(plans.includes('Limits'));
  assert.ok(plans.includes('Add-ons'));
  assert.ok(plans.includes('Validate'));
  assert.ok(plans.includes('Publish'));
  assert.ok(plans.includes('Validation Workspace'));
  assert.ok(plans.includes('Affected configuration'));
  assert.ok(plans.includes('Required dependency'));
  assert.ok(plans.includes('Review Validation'));
  assert.ok(plans.includes('Publishing is blocked because'));
  assert.ok(plans.includes('Feature Dependencies'));
  assert.ok(plans.includes('Configure Modules'));
  assert.ok(plans.includes('useSearchParams'));
  assert.ok(plans.includes('No plan templates yet'));
  assert.ok(plans.includes('CommercialPlanTemplateCreateDialog'));
  assert.ok(plans.includes('CommercialPlanTemplateSummarySection'));
  assert.ok(plans.includes('CommercialPlanSelectionDialog'));
  assert.ok(plans.includes('compareCommercialPlanVersions'));
  assert.ok(plans.includes('cloneCommercialPlanTemplate'));
  assert.ok(plans.includes('validateCommercialPlanDraft'));
  assert.ok(plans.includes('Publishing creates an immutable commercial plan version.'));
  assert.ok(plans.includes('Existing tenant entitlement behavior remains authoritative.'));
  assert.ok(plans.includes('Discard template changes?'));
  assert.ok(plans.includes('Create Plan Template'));
  assert.ok(plans.includes('navigate(`/platform/commercial/plans/${created.id}`, { replace: true })'));
  assert.ok(plans.includes('createCommercialPlanTemplate(auth.accessToken, {'));
  assert.ok(!plans.includes('useBlocker'));
  assert.ok(!plans.includes('NEW_PLAN_TEMPLATE'));
  assert.ok(!plans.includes('New Plan Template", code: "NEW_PLAN_TEMPLATE"'));
  assert.ok(plans.includes('validationLabel('));
  assert.ok(plans.includes('templateDetail.validation.readyToPublish'));
  assert.ok(plans.includes('formatCommercialDateTime'));

  assert.ok(editor.includes('Name *'));
  assert.ok(editor.includes('Code *'));
  assert.ok(editor.includes('Description'));
  assert.ok(editor.includes('Target Segment *'));
  assert.ok(editor.includes('Display Order'));
  assert.ok(editor.includes('Status'));
  assert.ok(editor.includes('Create Template'));
  assert.ok(editor.includes('Clone Existing'));
  assert.ok(editor.includes('Source Template *'));
  assert.ok(editor.includes('Clone Template'));
  assert.ok(editor.includes('Discard new plan template?'));
  assert.ok(editor.includes('Keep Editing'));
  assert.ok(editor.includes('Discard'));
  assert.ok(editor.includes('Code is fixed after template creation.'));
  assert.ok(!editor.includes('useBlocker'));
  assert.ok(!editor.includes('window.confirm'));
  assert.ok(!editor.includes('confirm('));
  assert.ok(!editor.includes('alert('));

  assert.ok(api.includes('getCommercialPlatformOverview'));
  assert.ok(api.includes('listCommercialPlanTemplates'));
  assert.ok(api.includes('getCommercialPlanTemplate'));
  assert.ok(api.includes('saveCommercialPlanDraft'));
  assert.ok(api.includes('publishCommercialPlanVersion'));
  assert.ok(api.includes('compareCommercialPlanVersions'));
});

test("commercial plan editor and selection dialog avoid browser-native prompts and show business metadata", () => {
  const dialog = readSource("pages/platform/CommercialPlanSelectionDialog.tsx");
  const catalog = readSource("pages/platform/CommercialCatalogRelationshipDialog.tsx");
  const plans = readSource("pages/platform/CommercialPlansPage.tsx");

  assert.ok(dialog.includes('Save Changes'));
  assert.ok(dialog.includes('Discard changes?'));
  assert.ok(dialog.includes('Keep Editing'));
  assert.ok(dialog.includes('Discard Changes'));
  assert.ok(dialog.includes('component="button"'));
  assert.ok(dialog.includes('type="button"'));
  assert.ok(dialog.includes('aria-pressed={checked}'));
  assert.ok(dialog.includes('Selected ('));
  assert.ok(dialog.includes('Search and update the plan configuration for this catalog record.'));
  assert.ok(dialog.includes('Runtime module:'));
  assert.ok(dialog.includes('Parent module:'));
  assert.ok(dialog.includes('Configured value'));
  assert.ok(dialog.includes('Included'));
  assert.ok(dialog.includes('Available for Purchase'));
  assert.ok(dialog.includes('Unavailable'));
  assert.ok(catalog.includes('placeholder: "Search modules"'));
  assert.ok(catalog.includes('placeholder: "Search capabilities"'));
  assert.ok(catalog.includes('placeholder: "Search features"'));
  assert.ok(catalog.includes('placeholder: "Search limits"'));
  assert.ok(!dialog.includes('window.confirm'));
  assert.ok(!dialog.includes('confirm('));
  assert.ok(!dialog.includes('alert('));

  assert.ok(plans.includes('Published Version'));
  assert.ok(plans.includes('Publication notes'));
  assert.ok(dialog.includes('Discard changes?'));
});

test("commercial plan draft payload and response mapping keep selections stable after refresh", () => {
  const draft = {
    id: "draft-1",
    templateId: "template-1",
    revision: 3,
    status: "DRAFT",
    draftNotes: "Builder notes",
    validationStatus: "INVALID",
    publicationReady: false,
    validation: {
      validationState: "INVALID",
      readyToPublish: false,
      blockingFindingCount: 1,
      warningFindingCount: 0,
      findings: [],
      validatedDraftRevision: 3,
      validatedAt: null,
    },
    updatedAt: "2026-07-25T12:00:00Z",
    updatedBy: "admin",
    configuration: {
      capabilities: [{ capabilityId: "cap-1", capabilityCode: "HEALTHCARE_CORE", capabilityName: "Healthcare Core", description: null, displayOrder: 1, selected: true, retired: false }],
      modules: [{ moduleId: "mod-1", moduleCode: "APPOINTMENTS", moduleName: "Appointments", description: null, runtimeModuleCode: "APPOINTMENTS", displayOrder: 1, selected: true, inherited: false, selectionSource: "EXPLICIT", retired: false }],
      features: [{ featureId: "feat-1", featureCode: "REPORT_OCR", featureName: "Report OCR", description: null, moduleId: "mod-1", moduleCode: "REPORTS", moduleName: "Reports", displayOrder: 1, selected: true, retired: false }],
      limits: [{ limitDefinitionId: "limit-1", limitCode: "MAX_DOCTORS", limitName: "Max Doctors", description: null, unit: "count", valueType: "INTEGER", aggregationPeriod: "MONTHLY", enforcementMode: "INFORMATIONAL", configuredValue: "1", displayOrder: 1, selected: true, retired: false }],
      addons: [{ addonId: "addon-1", addonCode: "CLINICAL_AI_ADDON", addonName: "Clinical AI Add-on", description: null, addonType: "CAPABILITY", displayOrder: 1, selectionState: "AVAILABLE", retired: false }],
    },
    validationMessages: [],
  };

  const state = mapDraftResponseState(draft);
  assert.deepEqual(state.capabilities, ["cap-1"]);
  assert.deepEqual(state.modules.map((item) => item.moduleId), ["mod-1"]);
  assert.deepEqual(state.features, ["feat-1"]);
  assert.deepEqual(state.limits, [{ limitDefinitionId: "limit-1", configuredValue: "1" }]);
  assert.deepEqual(state.addons, [{ addonId: "addon-1", selectionState: "AVAILABLE" }]);

  const payload = buildDraftSavePayload({
    ...state,
    capabilities: ["cap-1", "cap-2"],
    modules: [
      { moduleId: "mod-1", selectionSource: "EXPLICIT", inherited: false, displayOrder: 1 },
      { moduleId: "mod-2", selectionSource: "INHERITED", inherited: true, displayOrder: 2 },
    ],
    features: ["feat-1", "feat-2"],
    limits: [
      { limitDefinitionId: "limit-1", configuredValue: "2" },
      { limitDefinitionId: "limit-2", configuredValue: "5" },
    ],
    addons: [
      { addonId: "addon-1", selectionState: "INCLUDED" },
      { addonId: "addon-2", selectionState: "AVAILABLE" },
    ],
    draftNotes: "Builder notes",
  });

  assert.deepEqual(payload, {
    draftNotes: "Builder notes",
    capabilities: [{ capabilityId: "cap-1" }, { capabilityId: "cap-2" }],
    modules: [
      { moduleId: "mod-1", selectionSource: "EXPLICIT", inherited: false, displayOrder: 1 },
      { moduleId: "mod-2", selectionSource: "INHERITED", inherited: true, displayOrder: 2 },
    ],
    features: [{ featureId: "feat-1" }, { featureId: "feat-2" }],
    limits: [
      { limitDefinitionId: "limit-1", configuredValue: "2" },
      { limitDefinitionId: "limit-2", configuredValue: "5" },
    ],
    addons: [
      { addonId: "addon-1", selectionState: "INCLUDED" },
      { addonId: "addon-2", selectionState: "AVAILABLE" },
    ],
  });
});

test("commercial plan selection helpers toggle rows, counts, and dirty state", () => {
  assert.deepEqual(toggleSelectedIds([], "cap-1"), ["cap-1"]);
  assert.deepEqual(toggleSelectedIds(["cap-1", "cap-2"], "cap-1"), ["cap-2"]);
  assert.deepEqual(
    filterSelectionItems(
      [
        { id: "a", code: "A", name: "Alpha", status: "ACTIVE" },
        { id: "b", code: "B", name: "Beta", status: "RETIRED" },
      ],
      "capabilities",
      "alp",
    ).map((item) => item.id),
    ["a"],
  );
  assert.deepEqual(
    selectedSelectionItems(
      [
        { id: "a", code: "A", name: "Alpha", status: "ACTIVE" },
        { id: "b", code: "B", name: "Beta", status: "ACTIVE" },
      ],
      ["b", "a"],
    ).map((item) => item.name),
    ["Beta", "Alpha"],
  );

  assert.equal(computeConfiguredCount("capabilities", ["cap-1"], {}, {}), 1);
  assert.equal(computeConfiguredCount("limits", ["limit-1"], { "limit-1": "5" }, {}), 1);
  assert.equal(computeConfiguredCount("addons", [], {}, { "addon-1": "AVAILABLE", "addon-2": "UNAVAILABLE" }), 1);

  assert.equal(
    isDialogDirty("capabilities", ["cap-1"], [], {}, {}, {}, {}),
    true,
  );
  assert.equal(
    isDialogDirty("features", ["feature-1"], ["feature-1"], {}, {}, {}, {}),
    false,
  );
});

test("commercial plan summary renders validation states and localized timestamps", () => {
  const summaryMarkup = renderToStaticMarkup(
    React.createElement(CommercialPlanTemplateSummarySection, {
      values: defaultTemplateForm(),
      onChange: () => {},
      onSave: () => {},
      saving: false,
      dirty: false,
      publicationReady: false,
      validation: {
        validationState: "NOT_VALIDATED",
        readyToPublish: false,
        blockingFindingCount: 2,
        warningFindingCount: 1,
        findings: [
          { field: "capabilities", code: "PLAN_CAPABILITY_REQUIRED", message: "Add at least one capability to define the commercial package.", remediation: "Select one or more commercial capabilities.", severity: "BLOCKING", blocking: true },
          { field: "modules", code: "PLAN_MODULE_REQUIRED", message: "Add at least one application module.", remediation: "Select one or more application modules.", severity: "BLOCKING", blocking: true },
          { field: "modules", code: "PLAN_NOTE", message: "Optional note", remediation: null, severity: "WARNING", blocking: false },
        ],
        validatedDraftRevision: 1,
        validatedAt: null,
      },
      draftRevision: 1,
      latestPublishedVersionNumber: null,
      updatedAt: "2026-07-24T16:47:46.552963Z",
      updatedBy: null,
      capabilityCount: 0,
      moduleCount: 0,
      featureCount: 0,
      limitCount: 0,
      addonCount: 0,
    }),
  );

  assert.ok(summaryMarkup.includes("Not validated"));
  assert.ok(summaryMarkup.includes("Validated draft revision 1"));
  assert.ok(summaryMarkup.includes("2 blocking finding"));
  assert.ok(summaryMarkup.includes("Updated"));
  assert.ok(!summaryMarkup.includes("2026-07-24T16:47:46.552963Z"));
  assert.ok(summaryMarkup.includes("Code is fixed after template creation."));
});

test("commercial plan validation findings render business context and actions", () => {
  const findingMarkup = renderToStaticMarkup(
    React.createElement(ValidationFindingCard, {
      finding: {
        field: "features",
        code: "FEATURE_PARENT_MODULE_REQUIRED",
        title: "Clinical Reasoning requires AI Copilot",
        message: 'The feature “Clinical Reasoning” is selected, but its parent module “AI Copilot” is not included in this plan.',
        remediation: "Add the AI Copilot module or remove Clinical Reasoning from the selected features.",
        severity: "BLOCKING",
        blocking: true,
        category: "FEATURE_DEPENDENCY",
        affectedItemType: "FEATURE",
        affectedItemCode: "CLINICAL_REASONING",
        affectedItemName: "Clinical Reasoning",
        expectedItemType: "MODULE",
        expectedItemCode: "AI_COPILOT",
        expectedItemName: "AI Copilot",
        currentValue: "Parent module not included",
        expectedValue: "Module included",
        targetBuilderTab: "modules",
        actionLabel: "Add Required Module",
      },
      onNavigateTab: () => {},
    }),
  );

  assert.ok(findingMarkup.includes("Blocking"));
  assert.ok(findingMarkup.includes("Feature Dependencies"));
  assert.ok(findingMarkup.includes("Clinical Reasoning requires AI Copilot"));
  assert.ok(findingMarkup.includes("Code: CLINICAL_REASONING"));
  assert.ok(findingMarkup.includes("AI Copilot"));
  assert.ok(findingMarkup.includes("Code: AI_COPILOT"));
  assert.ok(findingMarkup.includes("Parent module not included"));
  assert.ok(findingMarkup.includes("Module included"));
  assert.ok(findingMarkup.includes("Add Required Module"));
  assert.ok(findingMarkup.includes("Review Features"));
});

test("commercial plan template identity fields render without browser prompts", () => {
  const markup = renderToStaticMarkup(
    React.createElement(
      CommercialPlanTemplateFields,
      {
        values: defaultTemplateForm(),
        onChange: () => {},
        codeReadOnly: true,
      },
    ),
  );

  assert.ok(markup.includes("Name *"));
  assert.ok(markup.includes("Code *"));
  assert.ok(markup.includes("Description"));
  assert.ok(markup.includes("Target Segment *"));
  assert.ok(markup.includes("Display Order"));
  assert.ok(markup.includes("Status"));
  assert.ok(markup.includes("Code is fixed after template creation."));
  assert.ok(!markup.includes("window.confirm"));
  assert.ok(!markup.includes("alert("));
});

test("commercial platform routes render under the current browser-router architecture", () => {
  const authValue = {
    initialized: true,
    authenticated: true,
    username: "Platform Admin",
    rolesUpper: ["PLATFORM_ADMIN"],
    permissions: [],
    selectedTenant: null,
    tenantId: null,
    tenantName: null,
    appUserId: null,
    tenantRole: null,
    activeTenantMemberships: [],
    tenantModules: null,
    enabledTenantModules: null,
    accessToken: null,
    initError: null,
    selectTenant: () => {},
    retryInit: () => {},
    clearSession: () => {},
    hasPermission: () => true,
    login: async () => {},
    logout: async () => {},
  };

  const plansMarkup = renderToStaticMarkup(
    React.createElement(
      AuthContext.Provider,
      { value: authValue },
      React.createElement(
        MemoryRouter,
        { initialEntries: ["/platform/commercial/plans"] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, { path: "/platform/commercial/plans", element: React.createElement(CommercialPlansPage) }),
        ),
      ),
    ),
  );

  const platformMarkup = renderToStaticMarkup(
    React.createElement(
      AuthContext.Provider,
      { value: authValue },
      React.createElement(
        MemoryRouter,
        { initialEntries: ["/platform/commercial"] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, { path: "/platform/commercial", element: React.createElement(CommercialPlatformPage) }),
        ),
      ),
    ),
  );

  assert.ok(plansMarkup.includes("No plan templates yet"));
  assert.ok(plansMarkup.includes("Create Plan Template"));
  assert.ok(platformMarkup.includes("Commercial Platform"));
  assert.ok(platformMarkup.includes("Catalog only. Tenant access still comes from the existing legacy plan and module entitlement path."));
});
