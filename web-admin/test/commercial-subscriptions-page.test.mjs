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
  CommercialSubscriptionsPage,
  CommercialSubscriptionAssignmentDialog,
  pickCurrentCommercialSubscription,
  subscriptionSummaryLine,
  subscriptionSummaryMeta,
  subscriptionSummaryTitle,
  formatCommercialPlanVersionSummary,
} = await buildBundle();

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

async function buildBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".commercial-subscriptions-bundle-"));
  const entryPath = path.join(tempDir, "entry.tsx");
  const bundlePath = path.join(tempDir, "bundle.mjs");
  fs.writeFileSync(
    entryPath,
    `import { AuthContext } from "${path.join(srcRoot, "auth", "AuthContext.ts").replace(/\\/g, "/")}";\n` +
      `import CommercialSubscriptionsPage from "${path.join(srcRoot, "pages", "platform", "CommercialSubscriptionsPage.tsx").replace(/\\/g, "/")}";\n` +
      `import CommercialSubscriptionAssignmentDialog from "${path.join(srcRoot, "pages", "platform", "CommercialSubscriptionAssignmentDialog.tsx").replace(/\\/g, "/")}";\n` +
      `export * from "${path.join(srcRoot, "pages", "platform", "commercialSubscriptionView.ts").replace(/\\/g, "/")}";\n` +
      `export { AuthContext, CommercialSubscriptionsPage, CommercialSubscriptionAssignmentDialog };\n`,
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

test("commercial subscriptions route and navigation are registered", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const topBar = readSource("layout/TopBar.tsx");

  assert.ok(app.includes('path="/platform/commercial/subscriptions"'));
  assert.ok(app.includes('path="/platform/commercial/subscriptions/:subscriptionId"'));
  assert.ok(nav.includes('path: "/platform/commercial/subscriptions"'));
  assert.ok(nav.includes('label: "Subscriptions"'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/subscriptions")'));
});

test("commercial subscriptions workspace renders the commercial subscription banner and controls", () => {
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
    accessToken: "token",
    initError: null,
    selectTenant: () => {},
    retryInit: () => {},
    clearSession: () => {},
    hasPermission: () => true,
    login: async () => {},
    logout: async () => {},
  };

  const markup = renderToStaticMarkup(
    React.createElement(
      AuthContext.Provider,
      { value: authValue },
      React.createElement(
        MemoryRouter,
        { initialEntries: ["/platform/commercial/subscriptions"] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, { path: "/platform/commercial/subscriptions", element: React.createElement(CommercialSubscriptionsPage) }),
        ),
      ),
    ),
  );

  assert.ok(markup.includes("Commercial Subscriptions"));
  assert.ok(markup.includes("Assign Subscription"));
  assert.ok(markup.includes("Refresh"));
  assert.ok(markup.includes("Published Plans"));
  assert.ok(markup.includes("Active Subscriptions"));
  assert.ok(markup.includes("Scheduled"));
  assert.ok(markup.includes("Paused"));
  assert.ok(markup.includes("Expired"));
  assert.ok(markup.includes("Cancelled"));
  assert.ok(markup.includes("Commercial subscriptions are assignment records only."));
});

test("subscription assignment dialog shows accessible review and discard controls", () => {
  const source = readSource("pages/platform/CommercialSubscriptionAssignmentDialog.tsx");

  assert.ok(source.includes("Commercial subscription"));
  assert.ok(source.includes("Create Assignment"));
  assert.ok(source.includes("Draft Assignment"));
  assert.ok(source.includes("Not Submitted"));
  assert.ok(source.includes("Step {step + 1} of 4"));
  assert.ok(source.includes("Keep Editing"));
  assert.ok(source.includes("Discard Assignment"));
  assert.ok(source.includes("Selected Tenant"));
  assert.ok(source.includes("Selected Plan"));
  assert.ok(source.includes("Current Subscription Impact"));
  assert.ok(source.includes("Activation"));
  assert.ok(source.includes("Effective immediately"));
  assert.ok(source.includes("Schedule for a future date"));
  assert.ok(source.includes('aria-current={step === index ? "step" : undefined}'));
  assert.ok(!source.includes("window.confirm"));
  assert.ok(!source.includes("alert("));
});

test("commercial subscription sources use button semantics and no browser prompts", () => {
  const dialog = readSource("pages/platform/CommercialPlanSelectionDialog.tsx");
  const assign = readSource("pages/platform/CommercialSubscriptionAssignmentDialog.tsx");
  const page = readSource("pages/platform/CommercialSubscriptionsPage.tsx");

  assert.ok(dialog.includes('component="button"'));
  assert.ok(dialog.includes('type="button"'));
  assert.ok(dialog.includes('aria-pressed={checked}'));
  assert.ok(dialog.includes('Selected ('));
  assert.ok(dialog.includes('Save Changes'));
  assert.ok(!dialog.includes('window.confirm'));
  assert.ok(!dialog.includes('alert('));
  assert.ok(assign.includes('Create Assignment'));
  assert.ok(assign.includes('Discard subscription assignment?'));
  assert.ok(assign.includes('Keep Editing'));
  assert.ok(assign.includes('Discard Assignment'));
  assert.ok(assign.includes('Draft Assignment'));
  assert.ok(assign.includes('Not Submitted'));
  assert.ok(page.includes('Commercial Subscriptions'));
  assert.ok(page.includes('Assign Subscription'));
  assert.ok(page.includes('Subscription Summary'));
  assert.ok(page.includes('Timeline'));
  assert.ok(page.includes('Replace'));
  assert.ok(page.includes('Activate'));
  assert.ok(page.includes('Pause'));
  assert.ok(page.includes('Resume'));
  assert.ok(page.includes('Cancel'));
  assert.ok(page.includes('Commercial subscriptions are assignment records only.'));
});

test("commercial subscription helper functions prefer business labels", () => {
  const current = pickCurrentCommercialSubscription(
    [
      {
        id: "sub-1",
        tenantId: "tenant-1",
        planTemplateId: "template-1",
        planTemplateCode: "SOLO_CLINIC",
        planTemplateName: "Solo Clinic",
        publishedVersionId: "version-1",
        publishedVersionNumber: 1,
        publishedVersionLabel: "Version 1",
        subscriptionStatus: "ACTIVE",
        startDate: "2026-07-25",
        endDate: null,
        autoRenew: true,
        displayName: "Demo Clinic Subscription",
        referenceNumber: null,
        notes: null,
        createdAt: "2026-07-25T00:00:00Z",
        updatedAt: "2026-07-25T00:00:00Z",
      },
      {
        id: "sub-2",
        tenantId: "tenant-1",
        planTemplateId: "template-2",
        planTemplateCode: "ENTERPRISE",
        planTemplateName: "Enterprise",
        publishedVersionId: "version-2",
        publishedVersionNumber: 2,
        publishedVersionLabel: "Version 2",
        subscriptionStatus: "SCHEDULED",
        startDate: "2026-08-01",
        endDate: null,
        autoRenew: false,
        displayName: "Future Assignment",
        referenceNumber: null,
        notes: null,
        createdAt: "2026-07-25T00:00:00Z",
        updatedAt: "2026-07-25T01:00:00Z",
      },
    ],
    "tenant-1",
  );

  assert.ok(current);
  assert.equal(subscriptionSummaryTitle(current), "Demo Clinic Subscription");
  assert.equal(subscriptionSummaryMeta(current), "Solo Clinic · Version 1");
  assert.ok(subscriptionSummaryLine(current).includes("Version 1"));
  assert.ok(subscriptionSummaryLine(current).includes("Active since"));
  assert.ok(!subscriptionSummaryLine(current).includes("tenant-1"));
  assert.ok(!subscriptionSummaryLine(current).includes("sub-1"));

  const versionSummary = formatCommercialPlanVersionSummary({
    versionNumber: 1,
    publishedAt: "2026-07-25T10:30:00Z",
    publishedBy: "Platform Admin",
    capabilityCount: 2,
    moduleCount: 5,
    featureCount: 2,
    limitCount: 5,
    addonCount: 1,
  });

  assert.ok(versionSummary.includes("Version 1"));
  assert.ok(versionSummary.includes("Published"));
  assert.ok(versionSummary.includes("Platform Admin"));
  assert.ok(versionSummary.includes("2 capabilities"));
  assert.ok(versionSummary.includes("5 modules"));
  assert.ok(versionSummary.includes("1 add-ons"));
});

test("commercial subscription assignment dialog initial step keeps create action gated to review", () => {
  const source = readSource("pages/platform/CommercialSubscriptionAssignmentDialog.tsx");

  assert.ok(source.includes('step < 3 ?'));
  assert.ok(source.includes('step === 3 ?'));
  assert.ok(source.includes('Next'));
  assert.ok(source.includes('Create Assignment'));
  assert.ok(source.includes('disabled={!canManage || submitting || (step === 0 && !canContinueTenant) || (step === 1 && !canContinuePlan) || (step === 2 && !canContinueSchedule)}'));
  assert.ok(source.includes('aria-current={step === index ? "step" : undefined}'));
});
