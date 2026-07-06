import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

function readFrontendSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "frontend", "packages", "form-validation-kit", "src", ...relPath.split("/")), "utf8");
}

test("dashboard page wires onboarding wizard and guided empty states", () => {
  const source = readWebAdminSource("pages/DashboardPage.tsx");
  assert.ok(source.includes("TenantOnboardingWizardDialog"));
  assert.ok(source.includes("Clinic setup"));
  assert.ok(source.includes("Hide setup guide"));
  assert.ok(source.includes("Show setup guide"));
  assert.ok(source.includes("Setup complete"));
  assert.ok(source.includes("Add Doctor"));
  assert.ok(source.includes("Book First Appointment"));
});

test("clinic profile page exposes resume setup entry point", () => {
  const source = readWebAdminSource("pages/settings/ClinicProfilePage.tsx");
  assert.ok(source.includes("Resume setup"));
  assert.ok(source.includes("TenantOnboardingWizardDialog"));
  assert.ok(source.includes("getTenantOnboardingStatus"));
});

test("users page exposes username employee code mobile and department", () => {
  const source = readWebAdminSource("pages/settings/UsersRolesPage.tsx");
  assert.ok(source.includes("Username / Login ID"));
  assert.ok(source.includes("Employee Code"));
  assert.ok(source.includes("Mobile Number"));
  assert.ok(source.includes("Department"));
  assert.ok(source.includes("user.username || user.email"));
});

test("user schema accepts the expanded identity fields", () => {
  const source = readFrontendSource("schemas/user.ts");
  assert.ok(source.includes("username"));
  assert.ok(source.includes("employeeCode"));
  assert.ok(source.includes("department"));
  assert.ok(source.includes("optionalIndianMobileNumber"));
});
