import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";

import { AuthContext } from "../src/auth/AuthContext.js";
import FieldHelpTooltip from "../src/shared/components/help/FieldHelpTooltip.js";
import GlobalHelpButton from "../src/shared/components/help/GlobalHelpButton.js";
import GlobalHelpDrawer from "../src/shared/components/help/GlobalHelpDrawer.js";
import HelpProvider, { useHelp } from "../src/shared/components/help/HelpProvider.js";
import { getHelpErrorMessage, HELP_LOAD_FAILED_MESSAGE, HELP_NOT_FOUND_MESSAGE, HELP_PERMISSION_DENIED_MESSAGE, HELP_SESSION_EXPIRED_MESSAGE, isHelpNotFoundError } from "../src/shared/components/help/helpErrors.js";
import { HELP_CLOSE_EVENT, HELP_OPEN_EVENT, closeGlobalHelp, openGlobalHelp, subscribeGlobalHelpEvents } from "../src/shared/components/help/helpEvents.js";
import PageHelpButton from "../src/shared/components/help/PageHelpButton.js";
import { buildDefaultExpandedHelpSections, filterHelpSections, getHelpSectionPresentation, normalizeHelpSearchQuery, safeParseHelpJson } from "../src/shared/components/help/helpContent.js";
import { loadRecentHelpPages, recordRecentHelpPage, getRecentHelpStorageKey } from "../src/shared/components/help/helpRecentPages.js";
import { isHelpShortcutEvent } from "../src/shared/components/help/helpShortcuts.js";
import { resolveHelpPageMeta, resolveHelpRouteByPageKey } from "../src/shared/components/help/helpPageRegistry.js";
import { buildHelpRequestOptions, resolveHelpAccessToken } from "../src/api/helpClient.js";
import { keycloak } from "../src/auth/keycloak.js";
import {
  HelpAudit,
  HelpBestPractices,
  HelpCommonErrors,
  HelpDescription,
  HelpFAQ,
  HelpFieldTable,
  HelpExportCsv,
  HelpKnownLimitations,
  HelpPermissions,
  HelpReportFilters,
  HelpReportTypes,
  HelpRoles,
  HelpTips,
  HelpValidationRules,
  HelpWorkflow,
  HelpRelatedPages,
} from "../src/shared/components/help/sectionRenderers.js";

const page = {
  id: "page-1",
  moduleKey: "PHARMACY",
  pageKey: "PHARMACY_INVENTORY",
  title: "Inventory Help",
  icon: "inventory",
  status: "PUBLISHED",
  version: 1,
  active: true,
  createdBy: null,
  updatedBy: null,
  createdAt: "2026-06-19T00:00:00.000Z",
  updatedAt: "2026-06-19T00:00:00.000Z",
  availableVersions: [1],
  sections: [
    {
      id: "section-1",
      sectionKey: "DESCRIPTION",
      sectionType: "DESCRIPTION",
      displayOrder: 1,
      collapsible: true,
      active: true,
      contentJson: JSON.stringify({ title: "Inventory", description: "Inventory manages stock." }),
      contentLanguageCode: "en",
      contentVersion: 1,
      contentStatus: "PUBLISHED",
      attachments: [],
      contents: [],
    },
    {
      id: "section-2",
      sectionKey: "FIELD_TABLE",
      sectionType: "FIELD_TABLE",
      displayOrder: 2,
      collapsible: true,
      active: true,
      contentJson: JSON.stringify({ fields: [{ fieldName: "Batch Number", required: true, description: "Batch code", example: "B-001", maxLength: 60 }] }),
      contentLanguageCode: "en",
      contentVersion: 1,
      contentStatus: "PUBLISHED",
      attachments: [],
      contents: [],
    },
  ],
};

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("help search query normalizes whitespace and case", () => {
  assert.equal(normalizeHelpSearchQuery("  How   do I Add Stock? "), "how do i add stock?");
});

test("pharmacy pos route resolves to the pharmacy pos help page", () => {
  const meta = resolveHelpPageMeta("/pharmacy/pos");
  assert.equal(meta.pageKey, "PHARMACY_POS");
  assert.equal(meta.cmsPageKey, "PHARMACY_POS");
  assert.equal(meta.title, "Pharmacy POS");
  assert.equal(resolveHelpRouteByPageKey("PHARMACY_POS")?.pageKey, "PHARMACY_POS");
  assert.equal(resolveHelpRouteByPageKey("POS")?.pageKey, "PHARMACY_POS");
});

test("pharmacy procurement and reconciliation routes resolve to dedicated help pages", () => {
  const procurement = resolveHelpPageMeta("/pharmacy/procurement");
  const reconciliation = resolveHelpPageMeta("/pharmacy/reconciliation");
  assert.equal(procurement.pageKey, "PHARMACY_PROCUREMENT");
  assert.equal(procurement.title, "Procurement");
  assert.equal(reconciliation.pageKey, "PHARMACY_RECONCILIATION");
  assert.equal(reconciliation.title, "Reconciliation");
  assert.equal(resolveHelpRouteByPageKey("PROCUREMENT")?.path, "/pharmacy/procurement");
  assert.equal(resolveHelpRouteByPageKey("RECONCILIATION")?.path, "/pharmacy/reconciliation");
});

test("pharmacy dashboard route resolves to the dashboard help page", () => {
  const meta = resolveHelpPageMeta("/pharmacy/dashboard");
  assert.equal(meta.pageKey, "PHARMACY_DASHBOARD");
  assert.equal(meta.cmsPageKey, "PHARMACY_DASHBOARD");
  assert.equal(meta.title, "Pharmacy Dashboard");
  assert.equal(resolveHelpRouteByPageKey("PHARMACY_DASHBOARD")?.pageKey, "PHARMACY_DASHBOARD");
  assert.equal(resolveHelpRouteByPageKey("PHARMACY")?.pageKey, "PHARMACY_DASHBOARD");
});

test("clinic dashboard route resolves to the clinic dashboard help page", () => {
  const meta = resolveHelpPageMeta("/dashboard");
  assert.equal(meta.pageKey, "CLINIC_DASHBOARD");
  assert.equal(meta.cmsPageKey, "CLINIC_DASHBOARD");
  assert.equal(meta.title, "Clinic Dashboard");
  assert.equal(resolveHelpRouteByPageKey("CLINIC_DASHBOARD")?.path, "/dashboard");
});

test("consultation route resolves tab-specific help pages", () => {
  assert.equal(resolveHelpPageMeta("/consultations/123?tab=prescription").pageKey, "CONSULTATION_PRESCRIPTION");
  assert.equal(resolveHelpPageMeta("/consultations/123?tab=history").pageKey, "CONSULTATION_HISTORY");
  assert.equal(resolveHelpPageMeta("/consultations/123?tab=investigations").pageKey, "CONSULTATION_INVESTIGATIONS");
  assert.equal(resolveHelpPageMeta("/consultations/123?tab=lab-orders").pageKey, "CONSULTATION_LAB_ORDERS");
  assert.equal(resolveHelpPageMeta("/consultations/123?tab=ai-assist").pageKey, "CONSULTATION_AI_ASSIST");
});

test("clinic operations routes resolve to the seeded help pages", () => {
  assert.equal(resolveHelpPageMeta("/appointments/day-board").pageKey, "DAY_BOARD");
  assert.equal(resolveHelpPageMeta("/notifications").pageKey, "NOTIFICATIONS");
  assert.equal(resolveHelpPageMeta("/vaccinations").pageKey, "VACCINATIONS");
});

test("reports route resolves to the reports help page", () => {
  const meta = resolveHelpPageMeta("/reports");
  assert.equal(meta.pageKey, "REPORTS");
  assert.equal(meta.cmsPageKey, "REPORTS");
  assert.equal(meta.title, "Reports");
  assert.equal(resolveHelpRouteByPageKey("REPORTS")?.path, "/reports");
  assert.equal(resolveHelpRouteByPageKey("FINANCE_REPORTS")?.path, "/reports");
  assert.equal(resolveHelpRouteByPageKey("TENANT_REPORTS")?.path, "/reports");
});

test("top bar labels the new pharmacy routes clearly", () => {
  const source = readSource("layout/TopBar.tsx");
  assert.ok(source.includes('return "Prescription Register"'));
  assert.ok(source.includes('return "Inventory"'));
  assert.ok(source.includes('return "Procurement"'));
  assert.ok(source.includes('return "Reconciliation"'));
  assert.ok(source.includes('return "Reports & Audit"'));
});

test("left navigation labels the Engage section as ENGAGE", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "src", "layout", "nav.ts"), "utf8");
  assert.ok(source.includes('label: "ENGAGE"'));
});

test("top bar uses a branded lockup and sticky shell", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "src", "layout", "TopBar.tsx"), "utf8");
  assert.ok(source.includes('position="fixed"'));
  assert.ok(source.includes("zIndex: (theme) => theme.zIndex.drawer + 2"));
  assert.ok(source.includes("minHeight: 84"));
  assert.ok(source.includes("drawerWidth"));
  assert.ok(source.includes("BrandMark"));
  assert.ok(source.includes('showCopy'));
  assert.ok(source.includes('subtitle="Intelligent Healthcare Platform"'));
  assert.equal(source.split('label="DEMO / UAT"').length - 1, 1);
});

test("app shell offsets the main content below the fixed header", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "src", "layout", "AppShell.tsx"), "utf8");
  assert.ok(source.includes('height: 84'));
  assert.ok(source.includes("drawerWidth={drawerWidth}"));
  assert.ok(source.includes("isMobile={isMobile}"));
  assert.ok(!source.includes('label="DEMO / UAT"'));
});

test("sidebar header reuses the branded lockup", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "src", "layout", "SidebarNav.tsx"), "utf8");
  assert.ok(source.includes("BrandMark"));
});

test("footer layout keeps version text and centered branding", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "src", "layout", "Footer.tsx"), "utf8");
  assert.ok(source.includes("versionLabel"));
  assert.ok(source.includes("footerBrandingLine()"));
  assert.ok(source.includes('textAlign: "center"'));
  assert.ok(source.includes("Demo / UAT Environment"));
});

test("engage routes resolve to the Engage help pages", () => {
  const expectations = [
    ["/carepilot/campaigns", "JEEVANAM_ENGAGE_CAMPAIGNS", "CAMPAIGNS"],
    ["/carepilot/analytics", "ENGAGE_ANALYTICS", "ENGAGE_ANALYTICS"],
    ["/carepilot/ops", "ENGAGE_OPS_CONSOLE", "ENGAGE_OPS_CONSOLE"],
    ["/carepilot/messaging", "ENGAGE_MESSAGING", "ENGAGE_MESSAGING"],
    ["/carepilot/reminders", "ENGAGE_REMINDERS", "ENGAGE_REMINDERS"],
    ["/carepilot/engagement", "ENGAGE_PATIENT_ENGAGEMENT", "ENGAGE_PATIENT_ENGAGEMENT"],
    ["/carepilot/leads", "ENGAGE_LEADS", "ENGAGE_LEADS"],
    ["/carepilot/webinars", "ENGAGE_WEBINAR_AUTOMATION", "ENGAGE_WEBINAR_AUTOMATION"],
    ["/carepilot/ai-calls", "ENGAGE_AI_CALLS", "ENGAGE_AI_CALLS"],
    ["/carepilot/ai-receptionist/active-conversations", "ENGAGE_AI_RECEPTIONIST_ACTIVE", "ENGAGE_AI_RECEPTIONIST_ACTIVE"],
    ["/carepilot/ai-receptionist/callback-queue", "ENGAGE_AI_RECEPTIONIST_CALLBACK", "ENGAGE_AI_RECEPTIONIST_CALLBACK"],
    ["/carepilot/ai-receptionist/escalation-queue", "ENGAGE_AI_RECEPTIONIST_ESCALATION", "ENGAGE_AI_RECEPTIONIST_ESCALATION"],
    ["/carepilot/ai-receptionist/appointment-handoffs", "ENGAGE_AI_RECEPTIONIST_APPOINTMENT_HANDOFF", "ENGAGE_AI_RECEPTIONIST_APPOINTMENT_HANDOFF"],
    ["/carepilot/receptionist-queue", "ENGAGE_RECEPTIONIST_QUEUE", "ENGAGE_RECEPTIONIST_QUEUE"],
  ];
  for (const [path, pageKey, cmsPageKey] of expectations) {
    const meta = resolveHelpPageMeta(path);
    assert.equal(meta.pageKey, pageKey);
    assert.equal(meta.cmsPageKey, cmsPageKey);
    assert.equal(resolveHelpRouteByPageKey(pageKey)?.path, path);
  }
});

test("billing route resolves to the billing help page", () => {
  const meta = resolveHelpPageMeta("/billing");
  assert.equal(meta.pageKey, "BILLING");
  assert.equal(meta.cmsPageKey, "BILLING");
  assert.equal(meta.title, "Billing");
  assert.equal(resolveHelpRouteByPageKey("BILLING")?.path, "/billing");
  assert.equal(resolveHelpRouteByPageKey("FINANCE_BILLING")?.path, "/billing");
  assert.equal(resolveHelpRouteByPageKey("BILL_BUILDER")?.path, "/billing");
});

test("laboratory route resolves to the laboratory help page", () => {
  const meta = resolveHelpPageMeta("/laboratory");
  assert.equal(meta.pageKey, "LABORATORY");
  assert.equal(meta.cmsPageKey, "LABORATORY");
  assert.equal(meta.title, "Laboratory");
  assert.equal(resolveHelpRouteByPageKey("LABORATORY")?.path, "/laboratory");
  assert.equal(resolveHelpRouteByPageKey("LAB")?.path, "/laboratory");
  assert.equal(resolveHelpRouteByPageKey("LAB_OPERATIONS")?.path, "/laboratory");
});

test("laboratory help is seeded in the db-backed help cms", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "..", "backend", "api", "api-bff", "src", "main", "java", "com", "deepthoughtnet", "clinic", "api", "help", "HelpCmsSeeder.java"), "utf8");
  assert.ok(source.includes('"LABORATORY"'));
  assert.ok(source.includes("Laboratory manages lab test catalog"));
});

test("clinic help pages are seeded in the db-backed help cms", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "..", "backend", "api", "api-bff", "src", "main", "java", "com", "deepthoughtnet", "clinic", "api", "help", "HelpCmsSeeder.java"), "utf8");
  assert.ok(source.includes('"CLINIC_DASHBOARD"'));
  assert.ok(source.includes('"DAY_BOARD"'));
  assert.ok(source.includes('"NOTIFICATIONS"'));
  assert.ok(source.includes('"VACCINATIONS"'));
  assert.ok(source.includes('"CONSULTATION_WORKSPACE"'));
  assert.ok(source.includes('"CONSULTATION_PRESCRIPTION"'));
});

test("engage help pages are seeded in the db-backed help cms", () => {
  const source = fs.readFileSync(path.join(process.cwd(), "..", "backend", "api", "api-bff", "src", "main", "java", "com", "deepthoughtnet", "clinic", "api", "help", "HelpCmsSeeder.java"), "utf8");
  assert.ok(source.includes('"CAMPAIGNS"'));
  assert.ok(source.includes('"ENGAGE_ANALYTICS"'));
  assert.ok(source.includes('"ENGAGE_OPS_CONSOLE"'));
  assert.ok(source.includes('"ENGAGE_MESSAGING"'));
  assert.ok(source.includes('"ENGAGE_REMINDERS"'));
  assert.ok(source.includes('"ENGAGE_PATIENT_ENGAGEMENT"'));
  assert.ok(source.includes('"ENGAGE_LEADS"'));
  assert.ok(source.includes('"ENGAGE_WEBINAR_AUTOMATION"'));
  assert.ok(source.includes('"ENGAGE_AI_CALLS"'));
  assert.ok(source.includes('"ENGAGE_RECEPTIONIST_QUEUE"'));
});

test("engage pages do not retain visible CarePilot branding", () => {
  const files = [
    "web-admin/src/products/carepilot/campaigns/CampaignsPage.tsx",
    "web-admin/src/products/carepilot/messaging/MessagingPage.tsx",
    "web-admin/src/products/carepilot/ops/OpsConsolePage.tsx",
    "web-admin/src/products/carepilot/reminders/RemindersPage.tsx",
    "web-admin/src/products/carepilot/leads/LeadsPage.tsx",
    "web-admin/src/products/carepilot/ai-calls/AiCallsPage.tsx",
    "web-admin/src/products/carepilot/webinars/WebinarsPage.tsx",
  ];
  const banned = [
    "CarePilot Campaigns",
    "CarePilot Leads",
    "CarePilot Reminders",
    "CarePilot Ops Console",
    "CarePilot AI Calls",
    "CarePilot messaging provider status",
    "CarePilot provider readiness",
    "CarePilot data",
  ];
  for (const file of files) {
    const source = fs.readFileSync(path.join(process.cwd(), "..", file), "utf8");
    for (const token of banned) {
      assert.ok(!source.includes(token), `${file} still includes ${token}`);
    }
  }
});

test("common issues help section uses the expected label", () => {
  assert.equal(getHelpSectionPresentation("COMMON_ISSUES").label, "Common Issues");
});

test("help section filtering hides field table content from the visible drawer", () => {
  const matches = filterHelpSections(page, "batch");
  assert.equal(matches.length, 0);
});

test("help section filtering matches parsed description content", () => {
  const matches = filterHelpSections(
    {
      ...page,
      sections: [
        {
          ...page.sections[0],
          contentJson: JSON.stringify({ title: "Inventory", description: "Manage weekly stock review." }),
        },
      ],
    },
    "weekly",
  );
  assert.equal(matches.length, 1);
});

test("help content safely parses json", () => {
  assert.deepEqual(safeParseHelpJson('{"ok":true}'), { ok: true });
  assert.equal(safeParseHelpJson("{not json}"), null);
});

test("reports help search finds operational report sections", () => {
  const reportsPage = {
    ...page,
    pageKey: "REPORTS",
    title: "Reports",
    sections: [
      {
        id: "reports-description",
        sectionKey: "DESCRIPTION",
        sectionType: "DESCRIPTION",
        displayOrder: 1,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ title: "Reports", description: "Reports provide tenant-scoped operational, clinical, pharmacy, laboratory, and financial reporting." }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "reports-export",
        sectionKey: "EXPORT_CSV",
        sectionType: "EXPORT_CSV",
        displayOrder: 5,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ items: [{ title: "Current tab only", description: "Exports the active report tab using the current filters and date range." }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "reports-types",
        sectionKey: "REPORT_TYPES",
        sectionType: "REPORT_TYPES",
        displayOrder: 3,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ items: [{ title: "Revenue", description: "Shows clinic revenue, pharmacy revenue, gross revenue, discounts, tax, refunds, and net revenue." }, { title: "Low Stock", description: "Shows low-stock medicines based on reorder level." }, { title: "Lab Operations", description: "Shows lab order, sample, result, report, turnaround, and revenue summaries." }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "reports-field-table",
        sectionKey: "FIELD_TABLE",
        sectionType: "FIELD_TABLE",
        displayOrder: 99,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ fields: [{ fieldName: "Internal ID", required: false }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
    ],
  };

  assert.equal(filterHelpSections(reportsPage, "csv export").some((section) => section.sectionType === "EXPORT_CSV"), true);
  assert.equal(filterHelpSections(reportsPage, "revenue").some((section) => section.sectionType === "REPORT_TYPES"), true);
  assert.equal(filterHelpSections(reportsPage, "low stock").some((section) => section.sectionType === "REPORT_TYPES"), true);
  assert.equal(filterHelpSections(reportsPage, "lab operations").some((section) => section.sectionType === "REPORT_TYPES"), true);
  assert.equal(filterHelpSections(reportsPage, "internal").some((section) => section.sectionType === "FIELD_TABLE"), false);
});

test("billing help search finds seeded content and excludes field table sections", () => {
  const billingPage = {
    ...page,
    pageKey: "BILLING",
    title: "Billing",
    sections: [
      {
        id: "billing-description",
        sectionKey: "DESCRIPTION",
        sectionType: "DESCRIPTION",
        displayOrder: 1,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ title: "Billing", description: "Billing is used to create patient bills, discounts, payments, and invoice printing." }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "billing-validation",
        sectionKey: "VALIDATION_RULES",
        sectionType: "VALIDATION_RULES",
        displayOrder: 3,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ rules: [{ field: "Payment reference", rule: "Required for non-cash payments." }, { field: "Discount reason", rule: "Required when discount is applied." }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "billing-workflow",
        sectionKey: "WORKFLOW",
        sectionType: "WORKFLOW",
        displayOrder: 2,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ steps: [{ title: "Review bill in ledger", description: "Use ledger filters to find historical bills." }, { title: "Process refund", description: "Use the Refunds page for controlled refund processing." }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "billing-field-table",
        sectionKey: "FIELD_TABLE",
        sectionType: "FIELD_TABLE",
        displayOrder: 99,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ fields: [{ fieldName: "Internal UUID", required: false }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
    ],
  };

  assert.equal(filterHelpSections(billingPage, "billing").length > 0, true);
  assert.equal(filterHelpSections(billingPage, "discount").some((section) => section.sectionType === "VALIDATION_RULES"), true);
  assert.equal(filterHelpSections(billingPage, "payment reference").some((section) => section.sectionType === "VALIDATION_RULES"), true);
  assert.equal(filterHelpSections(billingPage, "invoice").some((section) => section.sectionType === "DESCRIPTION"), true);
  assert.equal(filterHelpSections(billingPage, "ledger").some((section) => section.sectionType === "WORKFLOW"), true);
  assert.equal(filterHelpSections(billingPage, "refund").some((section) => section.sectionType === "WORKFLOW"), true);
  assert.equal(filterHelpSections(billingPage, "internal").some((section) => section.sectionType === "FIELD_TABLE"), false);
});

test("field help tooltip renders required marker", () => {
  const markup = renderToStaticMarkup(React.createElement(FieldHelpTooltip, { label: "Batch number", helpText: "Batch code", required: true }));
  assert.ok(markup.includes("Batch number"));
  assert.ok(markup.includes("*"));
});

test("report section renderers show structured content", () => {
  assert.ok(renderToStaticMarkup(React.createElement(HelpReportTypes, { items: [{ title: "Revenue", description: "Shows clinic revenue." }] })).includes("Revenue"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpReportFilters, { items: [{ title: "Date range", description: "From date and To date." }] })).includes("Date range"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpExportCsv, { items: [{ title: "Current tab only", description: "Exports the active report tab." }] })).includes("Current tab only"));
});

test("billing help sections are expanded by default for description and workflow", () => {
  const billingPage = {
    ...page,
    pageKey: "BILLING",
    sections: [
      {
        id: "billing-description",
        sectionKey: "DESCRIPTION",
        sectionType: "DESCRIPTION",
        displayOrder: 1,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ title: "Billing", description: "Billing." }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "billing-workflow",
        sectionKey: "WORKFLOW",
        sectionType: "WORKFLOW",
        displayOrder: 2,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ steps: [{ title: "Step 1", description: "Do the thing." }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
      {
        id: "billing-faq",
        sectionKey: "FAQ",
        sectionType: "FAQ",
        displayOrder: 3,
        collapsible: true,
        active: true,
        contentJson: JSON.stringify({ items: [{ question: "Q", answer: "A" }] }),
        contentLanguageCode: "en",
        contentVersion: 1,
        contentStatus: "PUBLISHED",
        attachments: [],
        contents: [],
      },
    ],
  };
  assert.deepEqual(buildDefaultExpandedHelpSections(billingPage), {
    DESCRIPTION: true,
    WORKFLOW: true,
    FAQ: false,
  });
});

test("page help button renders a help action", () => {
  const markup = renderToStaticMarkup(React.createElement(PageHelpButton, { pageKey: "PHARMACY_INVENTORY", title: "Inventory Help" }));
  assert.ok(markup.includes("Help"));
  assert.ok(markup.includes("Inventory Help") === false);
});

test("global help button renders a single help action", () => {
  const authValue = {
    initialized: true,
    authenticated: true,
    username: "admin",
    rolesUpper: ["CLINIC_ADMIN"],
    permissions: [],
    selectedTenant: { id: "tenant-1", code: "T-1", name: "Clinic 1" },
    tenantId: "tenant-1",
    tenantName: "Clinic 1",
    appUserId: "user-1",
    tenantRole: "CLINIC_ADMIN",
    activeTenantMemberships: [],
    tenantModules: null,
    accessToken: "token-1",
    initError: null,
    selectTenant: () => undefined,
    retryInit: () => undefined,
    clearSession: () => undefined,
    hasPermission: () => true,
    login: async () => undefined,
    logout: async () => undefined,
  };
  const markup = renderToStaticMarkup(
    React.createElement(AuthContext.Provider, { value: authValue },
      React.createElement(MemoryRouter, { initialEntries: ["/inventory"] },
        React.createElement(HelpProvider, null, React.createElement(GlobalHelpButton, null)),
      ),
    ),
  );
  assert.ok(markup.includes("Help"));
  assert.ok(markup.includes("Inventory Help") === false);
});

test("help event bus dispatches open and close events", () => {
  const listeners = new Map();
  const events = [];
  const originalWindow = globalThis.window;
  const originalCustomEvent = globalThis.CustomEvent;
  globalThis.CustomEvent = class CustomEvent {
    constructor(type, init = {}) {
      this.type = type;
      this.detail = init.detail || {};
    }
  };
  globalThis.window = {
    addEventListener: (type, handler) => {
      const handlers = listeners.get(type) || [];
      handlers.push(handler);
      listeners.set(type, handlers);
    },
    removeEventListener: (type, handler) => {
      const handlers = (listeners.get(type) || []).filter((item) => item !== handler);
      listeners.set(type, handlers);
    },
    dispatchEvent: (event) => {
      events.push(event.type);
      for (const handler of listeners.get(event.type) || []) {
        handler(event);
      }
      return true;
    },
  };

  try {
    let openDetail = null;
    let closed = false;
    const unsubscribe = subscribeGlobalHelpEvents((detail) => {
      openDetail = detail;
    }, () => {
      closed = true;
    });
    openGlobalHelp({ pageKey: "PHARMACY_MEDICINE_MASTER", source: "test" });
    closeGlobalHelp();
    unsubscribe();
    assert.deepEqual(events, [HELP_OPEN_EVENT, HELP_CLOSE_EVENT]);
    assert.equal(openDetail.pageKey, "PHARMACY_MEDICINE_MASTER");
    assert.equal(openDetail.source, "test");
    assert.equal(closed, true);
  } finally {
    globalThis.window = originalWindow;
    globalThis.CustomEvent = originalCustomEvent;
  }
});

test("help provider exposes help context", () => {
  function Probe() {
    const { isOpen } = useHelp();
    return React.createElement("span", null, isOpen ? "open" : "closed");
  }

  const authValue = {
    initialized: true,
    authenticated: true,
    username: "admin",
    rolesUpper: ["CLINIC_ADMIN"],
    permissions: [],
    selectedTenant: { id: "tenant-1", code: "T-1", name: "Clinic 1" },
    tenantId: "tenant-1",
    tenantName: "Clinic 1",
    appUserId: "user-1",
    tenantRole: "CLINIC_ADMIN",
    activeTenantMemberships: [],
    tenantModules: null,
    accessToken: "token-1",
    initError: null,
    selectTenant: () => undefined,
    retryInit: () => undefined,
    clearSession: () => undefined,
    hasPermission: () => true,
    login: async () => undefined,
    logout: async () => undefined,
  };

  const markup = renderToStaticMarkup(
    React.createElement(AuthContext.Provider, { value: authValue },
      React.createElement(MemoryRouter, { initialEntries: ["/inventory"] },
        React.createElement(HelpProvider, null, React.createElement(Probe, null)),
      ),
    ),
  );
  assert.ok(markup.includes("closed"));
});

test("help provider renders mounted marker", () => {
  const authValue = {
    initialized: true,
    authenticated: true,
    username: "admin",
    rolesUpper: ["CLINIC_ADMIN"],
    permissions: [],
    selectedTenant: { id: "tenant-1", code: "T-1", name: "Clinic 1" },
    tenantId: "tenant-1",
    tenantName: "Clinic 1",
    appUserId: "user-1",
    tenantRole: "CLINIC_ADMIN",
    activeTenantMemberships: [],
    tenantModules: null,
    accessToken: "token-1",
    initError: null,
    selectTenant: () => undefined,
    retryInit: () => undefined,
    clearSession: () => undefined,
    hasPermission: () => true,
    login: async () => undefined,
    logout: async () => undefined,
  };

  const markup = renderToStaticMarkup(
    React.createElement(AuthContext.Provider, { value: authValue },
      React.createElement(MemoryRouter, { initialEntries: ["/inventory"] },
        React.createElement(HelpProvider, null, React.createElement("span", null, "child")),
      ),
    ),
  );

  assert.ok(markup.includes('data-testid="help-provider-mounted"'));
});

test("global help drawer renders fallback state immediately", () => {
  const markup = renderToStaticMarkup(
    React.createElement(GlobalHelpDrawer, {
      open: true,
      pageKey: "UNKNOWN_PAGE",
      pageTitle: "Help",
      onClose: () => undefined,
    }),
  );

  assert.ok(markup.includes('data-testid="global-help-drawer"'));
  assert.ok(markup.includes("Help Center"));
  assert.ok(markup.includes("Close"));
  assert.ok(markup.includes("No page-specific help is available for this page yet."));
  assert.ok(markup.includes("Loading help..."));
  assert.ok(markup.includes("Search help, workflows, common errors, and FAQs..."));
  assert.ok(markup.includes("Ask AIVA"));
  assert.ok(markup.includes("No FAQs have been added yet."));
  assert.ok(markup.includes("No related pages configured."));
  assert.equal((markup.match(/Loading help\.\.\./g) || []).length, 1);
  assert.equal(markup.includes("Help drawer opened"), false);
});

test("help api token resolution falls back to keycloak token", () => {
  const originalToken = keycloak.token;
  try {
    keycloak.token = "kc-token";
    assert.equal(resolveHelpAccessToken(null), "kc-token");
    assert.equal(resolveHelpAccessToken("  explicit-token  "), "explicit-token");
    assert.equal(buildHelpRequestOptions(null).requireTenant, false);
    assert.equal(buildHelpRequestOptions(null).token, "kc-token");
  } finally {
    keycloak.token = originalToken;
  }
});

test("help api error messages map status codes", () => {
  assert.equal(getHelpErrorMessage({ status: 401 }), HELP_SESSION_EXPIRED_MESSAGE);
  assert.equal(getHelpErrorMessage({ status: 403 }), HELP_PERMISSION_DENIED_MESSAGE);
  assert.equal(getHelpErrorMessage({ status: 404 }), HELP_NOT_FOUND_MESSAGE);
  assert.equal(getHelpErrorMessage({ status: 500 }), HELP_LOAD_FAILED_MESSAGE);
  assert.equal(isHelpNotFoundError({ status: 404 }), true);
  assert.equal(isHelpNotFoundError({ status: 500 }), false);
});

test("global help drawer shows page title inside drawer", () => {
  const markup = renderToStaticMarkup(
    React.createElement(GlobalHelpDrawer, {
      open: true,
      pageKey: "PHARMACY_INVENTORY",
      pageTitle: "Inventory",
      onClose: () => undefined,
    }),
  );

  assert.ok(markup.includes('data-testid="global-help-drawer"'));
  assert.ok(markup.includes("Help Center"));
  assert.ok(markup.includes("Inventory"));
  assert.ok(markup.includes("Loading help..."));
  assert.ok(markup.includes("Recent Pages"));
  assert.ok(markup.includes("Related Pages"));
  assert.ok(markup.includes("FAQ"));
  assert.ok(markup.includes("Ask AIVA"));
});

test("help drawer source uses authenticated help api wrappers", () => {
  const source = readSource("shared/components/help/GlobalHelpDrawer.tsx");
  const pageSource = readSource("shared/components/help/PageHelpDrawer.tsx");
  const clinicApiSource = readSource("api/clinicApi.ts");
  assert.match(source, /getHelpPage\(/);
  assert.match(source, /searchHelp\(/);
  assert.match(pageSource, /getHelpPage\(/);
  assert.match(source, /help drawer opened/i);
  assert.match(clinicApiSource, /calling help API endpoint/i);
  assert.match(clinicApiSource, /help API status/i);
  assert.match(source, /help search/i);
});

test("help api request wrapper keeps bearer token path", () => {
  const clinicApiSource = readSource("api/clinicApi.ts");
  const restClientSource = readSource("api/restClient.ts");
  const helpClientSource = readSource("api/helpClient.ts");
  assert.match(clinicApiSource, /export async function getHelpPageByKey/);
  assert.match(clinicApiSource, /export async function searchHelp/);
  assert.match(restClientSource, /Authorization: `Bearer \$\{token\}`/);
  assert.match(helpClientSource, /keycloak\.token/);
});

test("help route registry resolves exact and fallback routes", () => {
  assert.equal(resolveHelpPageMeta("/inventory").pageKey, "PHARMACY_INVENTORY");
  assert.equal(resolveHelpPageMeta("/pharmacy/inventory").pageKey, "PHARMACY_INVENTORY");
  assert.equal(resolveHelpPageMeta("/pharmacy/medicine-master").pageKey, "PHARMACY_MEDICINE_MASTER");
  assert.equal(resolveHelpPageMeta("/dispensing").pageKey, "PHARMACY_DISPENSING");
  assert.equal(resolveHelpPageMeta("/reports").pageKey, "REPORTS");
  assert.equal(resolveHelpPageMeta("/settings/clinic-profile").pageKey, "CLINIC_PROFILE");
  assert.equal(resolveHelpPageMeta("/settings/users-roles").pageKey, "USERS");
  assert.equal(resolveHelpPageMeta("/admin/templates").pageKey, "PLATFORM_ADMIN");
  assert.equal(resolveHelpPageMeta("/patients").pageKey, "PATIENT_MASTER");
  assert.equal(resolveHelpPageMeta("/patients/123/edit").pageKey, "PATIENT_DETAILS");
  assert.equal(resolveHelpPageMeta("/custom/route").pageKey, "UNKNOWN_PAGE");
  assert.equal(resolveHelpRouteByPageKey("PHARMACY_MEDICINE_MASTER")?.path, "/pharmacy/medicine-master");
  assert.equal(resolveHelpRouteByPageKey("DISPENSING")?.path, "/pharmacy/dispensing");
  assert.equal(resolveHelpRouteByPageKey("REPORTS")?.path, "/reports");
  assert.equal(resolveHelpRouteByPageKey("CLINIC_PROFILE")?.path, "/settings/clinic-profile");
  assert.equal(resolveHelpRouteByPageKey("USERS")?.path, "/platform/users");
});

test("help shortcut helper detects ctrl slash", () => {
  assert.equal(isHelpShortcutEvent({ ctrlKey: true, metaKey: false, key: "/", code: "Slash" }), true);
  assert.equal(isHelpShortcutEvent({ ctrlKey: false, metaKey: false, key: "/", code: "Slash" }), false);
});

test("recent help pages persist and dedupe", () => {
  const store = new Map();
  const originalWindow = globalThis.window;
  globalThis.window = {
    localStorage: {
      getItem: (key) => store.get(key) || null,
      setItem: (key, value) => {
        store.set(key, value);
      },
      removeItem: (key) => {
        store.delete(key);
      },
      clear: () => store.clear(),
      key: (index) => Array.from(store.keys())[index] || null,
      get length() {
        return store.size;
      },
    },
  };

  try {
    const route = resolveHelpPageMeta("/inventory");
    const recent = recordRecentHelpPage(route);
    assert.equal(getRecentHelpStorageKey(), "arogia.help.recentPages.v1");
    assert.equal(recent[0].pageKey, "PHARMACY_INVENTORY");
    assert.equal(loadRecentHelpPages()[0].pageKey, "PHARMACY_INVENTORY");
    recordRecentHelpPage(route);
    assert.equal(loadRecentHelpPages().length, 1);
  } finally {
    globalThis.window = originalWindow;
  }
});

test("help section renderers show structured content", () => {
  assert.ok(renderToStaticMarkup(React.createElement(HelpDescription, { content: { title: "Inventory", description: "Inventory manages stock." } })).includes("Inventory manages stock."));
  assert.ok(renderToStaticMarkup(React.createElement(HelpWorkflow, { steps: [{ title: "Medicine Master", description: "Create medicine" }] })).includes("Medicine Master"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpFieldTable, { rows: [{ fieldName: "Batch Number", required: true, description: "Batch code", example: "B-001", maxLength: 60 }] })).includes("Batch Number"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpValidationRules, { rules: [{ field: "Quantity", rule: "Greater than 0" }] })).includes("Quantity"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpPermissions, { permissions: ["PHARMACY_ADMIN"] })).includes("PHARMACY_ADMIN"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpFAQ, { items: [{ question: "How do I add stock?", answer: "Open Inventory." }] })).includes("How do I add stock?"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpRoles, { allowedRoles: ["PHARMACY_ADMIN"], notIntendedFor: ["PHARMACY_POS_USER"], description: "Who should use this page" })).includes("PHARMACY_ADMIN"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpCommonErrors, { items: [{ error: "Duplicate batch", cause: "Batch already exists", resolution: "Use another batch" }] })).includes("Duplicate batch"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpBestPractices, { items: [{ title: "Review weekly", description: "Check expiry" }] })).includes("Review weekly"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpTips, { items: [{ title: "Tip", description: "Use standardized names" }] })).includes("Tip"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpKnownLimitations, { items: [{ title: "No upload", description: "Uploads not supported yet" }] })).includes("No upload"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpAudit, { items: [{ title: "Audit", description: "Store user and timestamp" }] })).includes("Audit"));
  assert.ok(renderToStaticMarkup(React.createElement(HelpRelatedPages, { pages: [{ title: "Inventory", pageKey: "INVENTORY", description: "Stock batches" }] })).includes("Inventory"));
});
