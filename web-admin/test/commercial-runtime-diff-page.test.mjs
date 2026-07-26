import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import * as esbuild from "esbuild";

const { AuthContext, CommercialRuntimeDiffPage } = await buildBundle();

async function buildBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".commercial-runtime-diff-bundle-"));
  const entryPath = path.join(tempDir, "entry.tsx");
  const bundlePath = path.join(tempDir, "bundle.mjs");
  fs.writeFileSync(
    entryPath,
    `import { AuthContext } from "${path.join(srcRoot, "auth", "AuthContext.ts").replace(/\\/g, "/")}";\n` +
      `import CommercialRuntimeDiffPage from "${path.join(srcRoot, "pages", "platform", "CommercialRuntimeDiffPage.tsx").replace(/\\/g, "/")}";\n` +
      `export { AuthContext, CommercialRuntimeDiffPage };\n`,
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

test("commercial runtime diff route and shell are registered", () => {
  const app = fs.readFileSync(path.join(process.cwd(), "src", "app", "App.tsx"), "utf8");
  const nav = fs.readFileSync(path.join(process.cwd(), "src", "layout", "nav.ts"), "utf8");
  const topBar = fs.readFileSync(path.join(process.cwd(), "src", "layout", "TopBar.tsx"), "utf8");

  assert.ok(app.includes('path="/platform/commercial/runtime-diff"'));
  assert.ok(nav.includes('label: "Runtime Diff"'));
  assert.ok(topBar.includes('pathname.startsWith("/platform/commercial/runtime-diff")'));
});

test("commercial runtime diff page renders workspace shell", () => {
  const authValue = {
    initialized: true,
    authenticated: true,
    username: "Platform Admin",
    rolesUpper: ["PLATFORM_ADMIN"],
    permissions: ["commercial.runtime.readiness.view", "commercial.runtime.diagnostics.view", "commercial.entitlements.view"],
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
        { initialEntries: ["/platform/commercial/runtime-diff"] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, { path: "/platform/commercial/runtime-diff", element: React.createElement(CommercialRuntimeDiffPage) }),
        ),
      ),
    ),
  );

  assert.ok(markup.includes("Runtime Diff"));
  assert.ok(markup.includes("Tenant Readiness"));
  assert.ok(markup.includes("Rollout Readiness"));
  assert.ok(markup.includes("Snapshot History"));
});
