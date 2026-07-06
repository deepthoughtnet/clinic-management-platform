import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab page gates actions with explicit lab permissions", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const canManageTests = auth.hasPermission("lab.test.manage");'));
  assert.ok(source.includes('const canUseLabReception = auth.hasPermission("lab.reception.access") || auth.rolesUpper.includes("LAB_FRONT_DESK");'));
  assert.ok(source.includes('const canGenerateReport = auth.hasPermission("lab.order.generate_report");'));
  assert.ok(source.includes('const canQuickRegisterPatient = canCreateOrders && auth.hasPermission("patient.create") && auth.hasPermission("patient.read");'));
  assert.ok(source.includes('Register New Patient'));
});

test("lab csv template download requests csv text and not json", () => {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  const apiSource = fs.readFileSync(path.join(root, "src", "api", "clinicApi.ts"), "utf8");
  assert.ok(apiSource.includes('return httpGetText("/api/lab/tests/import-template", { token, tenantId, accept: "text/csv, */*" });'));
});

test("lab ordering hides disabled tests and admin configuration stays hidden from lab roles", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const availableTests = React.useMemo('));
  assert.ok(source.includes('{canManageTests ? <Tab label="Lab Configuration" /> : null}'));
  assert.ok(source.includes('stickyHeader'));
});

test("lab dashboard prioritizes quick actions and my work before analytics", () => {
  const source = readSource("pages/lab/LabDashboard.tsx");
  assert.ok(source.includes('title="Quick Actions"'));
  assert.ok(source.includes('My Work Today'));
  assert.ok(source.includes('Workflow guide'));
  assert.ok(source.includes('Dashboard Analytics'));
});
