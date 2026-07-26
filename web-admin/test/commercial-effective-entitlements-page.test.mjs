import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import * as esbuild from "esbuild";

const { AuthContext, CommercialEffectiveEntitlementsPage } = await buildBundle();

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

async function buildBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".commercial-entitlements-bundle-"));
  const entryPath = path.join(tempDir, "entry.tsx");
  const bundlePath = path.join(tempDir, "bundle.mjs");
  fs.writeFileSync(
    entryPath,
    `import { AuthContext } from "${path.join(srcRoot, "auth", "AuthContext.ts").replace(/\\/g, "/")}";\n` +
      `import CommercialEffectiveEntitlementsPage from "${path.join(srcRoot, "pages", "platform", "CommercialEffectiveEntitlementsPage.tsx").replace(/\\/g, "/")}";\n` +
      `export { AuthContext, CommercialEffectiveEntitlementsPage };\n`,
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

test("commercial effective entitlements route and navigation are registered", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const topBar = readSource("layout/TopBar.tsx");

  assert.ok(app.includes('path="/platform/commercial/entitlements"'));
  assert.ok(nav.includes('label: "Effective Entitlements"'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/entitlements")'));
});

test("commercial effective entitlements page renders the diagnostics workspace shell", () => {
  const authValue = {
    initialized: true,
    authenticated: true,
    username: "Platform Admin",
    rolesUpper: ["PLATFORM_ADMIN"],
    permissions: ["commercial.entitlements.view", "commercial.entitlements.regenerate", "commercial.overrides.view", "commercial.overrides.manage", "commercial.runtime.diagnostics.view"],
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
        { initialEntries: ["/platform/commercial/entitlements"] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, { path: "/platform/commercial/entitlements", element: React.createElement(CommercialEffectiveEntitlementsPage) }),
        ),
      ),
    ),
  );

  assert.ok(markup.includes("Effective Entitlements"));
  assert.ok(markup.includes("Create Override"));
  assert.ok(markup.includes("View Legacy Comparison"));
  assert.ok(markup.includes("Technical Details"));
  assert.ok(markup.includes("Tenant selector"));
  assert.ok(markup.includes("No selected clinic tenant is required"));
  assert.ok(markup.includes("Runtime Source"));
  assert.ok(markup.includes("Legacy Runtime"));
  assert.ok(markup.includes("Snapshot History"));
});
