import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import * as esbuild from "esbuild";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

async function buildLandingBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".web-admin-landing-"));
  const entryPath = path.join(tempDir, "entry.tsx");
  const bundlePath = path.join(tempDir, "bundle.mjs");

  fs.writeFileSync(
    entryPath,
    `import { AuthContext } from "${path.join(srcRoot, "auth", "AuthContext.ts").replace(/\\/g, "/")}";\n` +
      `import HealthcareLandingPage from "${path.join(srcRoot, "pages", "public", "HealthcareLandingPage.tsx").replace(/\\/g, "/")}";\n` +
      `export { AuthContext, HealthcareLandingPage };\n`,
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
      "import.meta.env": JSON.stringify({
        VITE_PROVIDER_APP_URL: "",
        VITE_PRODUCT_NAME: "Jeevanam Healthcare",
        VITE_PRODUCT_TAGLINE: "Intelligent Healthcare Platform",
        VITE_COMPANY_NAME: "DeepThoughtNet",
        VITE_AI_PLATFORM_NAME: "AIVA",
        DEV: false,
        MODE: "test",
      }),
    },
  });

  const mod = await import(bundlePath);
  fs.rmSync(tempDir, { recursive: true, force: true });
  return mod;
}

test("landing page renders the healthcare homepage copy and ordered ecosystem cards", async () => {
  const { AuthContext, HealthcareLandingPage } = await buildLandingBundle();

  const auth = {
    initialized: true,
    authenticated: false,
    username: "",
    rolesUpper: [],
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
    hasPermission: () => false,
    login: async () => {},
    logout: async () => {},
  };

  const markup = renderToStaticMarkup(
    React.createElement(
      AuthContext.Provider,
      { value: auth },
      React.createElement(
        MemoryRouter,
        { initialEntries: ["/"] },
        React.createElement(HealthcareLandingPage),
      ),
    ),
  );

  const ecosystemSection = markup.slice(markup.indexOf("One connected healthcare ecosystem"));
  assert.ok(markup.includes("Run your clinic or hospital on one connected healthcare platform."));
  assert.ok(markup.includes("Sign in to Healthcare"));
  assert.ok(markup.includes('href="/login"'));
  assert.ok(markup.includes("Explore capabilities"));
  assert.ok(markup.includes("Built for healthcare operations"));
  assert.ok(markup.includes("One platform. Connected workflows."));
  assert.ok(markup.includes("Designed around the way healthcare teams actually work."));
  assert.ok(markup.includes("One connected healthcare ecosystem"));
  assert.ok(ecosystemSection.includes("Jeevanam Discover"));
  assert.ok(ecosystemSection.includes("Jeevanam Connect"));
  assert.ok(ecosystemSection.includes("Jeevanam Care"));
  assert.ok(ecosystemSection.includes("Jeevanam Healthcare"));
  assert.ok(ecosystemSection.indexOf("Jeevanam Discover") < ecosystemSection.indexOf("Jeevanam Connect"));
  assert.ok(ecosystemSection.indexOf("Jeevanam Connect") < ecosystemSection.indexOf("Jeevanam Care"));
  assert.ok(ecosystemSection.indexOf("Jeevanam Care") < ecosystemSection.indexOf("Jeevanam Healthcare"));
  assert.ok(markup.includes("Provider workspace"));
  assert.ok(markup.includes("provider/login"));
});

test("authenticated users are redirected away from the public landing page", async () => {
  const { AuthContext, HealthcareLandingPage } = await buildLandingBundle();

  const auth = {
    initialized: true,
    authenticated: true,
    username: "platform-admin",
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
    hasPermission: () => false,
    login: async () => {},
    logout: async () => {},
  };

  const markup = renderToStaticMarkup(
    React.createElement(
      AuthContext.Provider,
      { value: auth },
      React.createElement(
        MemoryRouter,
        { initialEntries: ["/"] },
        React.createElement(HealthcareLandingPage),
      ),
    ),
  );

  assert.ok(!markup.includes("Run your clinic or hospital on one connected healthcare platform."));
  assert.ok(!markup.includes("Jeevanam Discover"));
});

test("app routing keeps public landing on / and login unchanged", () => {
  const appSource = readSource("app/App.tsx");
  const landingSource = readSource("pages/public/HealthcareLandingPage.tsx");

  assert.ok(appSource.includes('import HealthcareLandingPage from "../pages/public/HealthcareLandingPage";'));
  assert.ok(appSource.includes('path="/" element={<PublicLandingRoute />}'));
  assert.ok(appSource.includes('path="/login" element={<LoginPage />}'));
  assert.ok(appSource.includes('return <Navigate to={resolveTenantLandingPage(auth)} replace />;'));
  assert.ok(appSource.includes('document.title = productTitle();'));
  assert.ok(appSource.includes('Sign in to {branding.productName} Admin Console'));
  assert.ok(landingSource.includes("scrollIntoView"));
  assert.ok(landingSource.includes('behavior: "smooth"'));
});
