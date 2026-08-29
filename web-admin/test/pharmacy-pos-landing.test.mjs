import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import * as esbuild from "esbuild";

async function buildModuleRegistryBundle() {
  const srcRoot = path.join(process.cwd(), "src");
  const tempDir = fs.mkdtempSync(path.join(process.cwd(), ".web-admin-pos-landing-"));
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
      PRESCRIPTION: true,
      INVENTORY: true,
      BILLING: true,
      REPORTS: true,
    },
    activeTenantMemberships: [],
    tenantRole: "PHARMACY_POS_USER",
    rolesUpper: ["PHARMACY_POS_USER"],
    permissions: [
      "prescription.read",
      "medicine.read",
      "inventory.read",
      "report.read",
      "billing.create",
      "payment.collect",
    ],
    ...overrides,
  };
}

test("pure pharmacy pos users land on the POS workspace even with supporting read permissions", async () => {
  const { resolveEnabledTenantModules, resolveTenantLandingPage, isRouteAccessibleForAuth, canAccessFeature } = await buildModuleRegistryBundle();
  const auth = buildAuth({ permissions: buildAuth().permissions.concat(["prescription.print"]) });

  const enabled = resolveEnabledTenantModules(auth);
  assert.equal(enabled.has("PHARMACY_POS"), true);
  assert.equal(canAccessFeature(auth, "pharmacy-pos"), true);
  assert.equal(isRouteAccessibleForAuth(auth, "/pharmacy/pos"), true);
  assert.equal(resolveTenantLandingPage(auth), "/pharmacy/pos");
  assert.notEqual(resolveTenantLandingPage(auth), "/prescriptions");
});

test("pharmacy landing priority remains unchanged for pharmacist and inventory manager roles", async () => {
  const { resolveTenantLandingPage } = await buildModuleRegistryBundle();

  const pharmacist = buildAuth({
    tenantRole: "PHARMACIST",
    rolesUpper: ["PHARMACIST"],
  });
  const inventoryManager = buildAuth({
    tenantRole: "PHARMACY_INVENTORY_MANAGER",
    rolesUpper: ["PHARMACY_INVENTORY_MANAGER"],
  });
  const clinicAdmin = buildAuth({
    tenantRole: "CLINIC_ADMIN",
    rolesUpper: ["CLINIC_ADMIN"],
  });

  assert.equal(resolveTenantLandingPage(pharmacist), "/pharmacy/dashboard");
  assert.equal(resolveTenantLandingPage(inventoryManager), "/pharmacy/dashboard");
  assert.equal(resolveTenantLandingPage(clinicAdmin), "/pharmacy/dashboard");
});

