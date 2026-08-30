import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import * as esbuild from "esbuild";

async function buildModuleRegistryBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".web-admin-engage-landing-"));
  const entryPath = path.join(tempDir, "entry.ts");
  const bundlePath = path.join(tempDir, "bundle.mjs");

  fs.writeFileSync(
    entryPath,
    `export * from "${path.join(srcRoot, "modules", "moduleRegistry.ts").replace(/\\/g, "/")}";\n`,
    "utf8",
  );

  await esbuild.build({
    entryPoints: [entryPath],
    bundle: true,
    platform: "node",
    format: "esm",
    outfile: bundlePath,
    logLevel: "silent",
    external: [],
  });

  const mod = await import(bundlePath);
  fs.rmSync(tempDir, { recursive: true, force: true });
  return mod;
}

function buildAuth(overrides = {}) {
  return {
    tenantId: "tenant-1",
    tenantModules: null,
    enabledTenantModules: {
      CAREPILOT: true,
      REPORTS: true,
    },
    activeTenantMemberships: [],
    tenantRole: "ENGAGE_EXECUTIVE",
    rolesUpper: ["ENGAGE_EXECUTIVE"],
    permissions: ["prescription.read"],
    ...overrides,
  };
}

test("pure engage executives and managers land on the ops console instead of reports", async () => {
  const { resolveTenantLandingPage, isRouteAccessibleForAuth } = await buildModuleRegistryBundle();

  const executive = buildAuth({ tenantRole: "ENGAGE_EXECUTIVE", rolesUpper: ["ENGAGE_EXECUTIVE"] });
  const manager = buildAuth({ tenantRole: "ENGAGE_MANAGER", rolesUpper: ["ENGAGE_MANAGER"] });

  assert.equal(resolveTenantLandingPage(executive), "/carepilot/ops");
  assert.equal(resolveTenantLandingPage(manager), "/carepilot/ops");
  assert.equal(isRouteAccessibleForAuth(executive, "/carepilot/ops"), true);
  assert.equal(isRouteAccessibleForAuth(manager, "/carepilot/ops"), true);
  assert.notEqual(resolveTenantLandingPage(executive), "/reports");
  assert.notEqual(resolveTenantLandingPage(manager), "/reports");
});

test("clinic admin and mixed-role users keep their existing landing precedence", async () => {
  const { resolveTenantLandingPage } = await buildModuleRegistryBundle();

  const clinicAdmin = buildAuth({
    tenantRole: "CLINIC_ADMIN",
    rolesUpper: ["CLINIC_ADMIN"],
    enabledTenantModules: {
      APPOINTMENTS: true,
      CONSULTATION: true,
      PRESCRIPTION: true,
      BILLING: true,
      INVENTORY: true,
      REPORTS: true,
      CAREPILOT: true,
    },
  });
  const mixed = buildAuth({
    tenantRole: "CLINIC_ADMIN",
    rolesUpper: ["CLINIC_ADMIN", "ENGAGE_EXECUTIVE"],
    enabledTenantModules: {
      APPOINTMENTS: true,
      CONSULTATION: true,
      PRESCRIPTION: true,
      BILLING: true,
      INVENTORY: true,
      REPORTS: true,
      CAREPILOT: true,
    },
  });

  assert.equal(resolveTenantLandingPage(clinicAdmin), "/pharmacy/dashboard");
  assert.equal(resolveTenantLandingPage(mixed), "/pharmacy/dashboard");
});
