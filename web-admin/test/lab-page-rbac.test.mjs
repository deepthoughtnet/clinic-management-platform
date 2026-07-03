import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("lab page gates actions with explicit lab permissions", () => {
  const source = readSource("pages/lab/LabPage.tsx");

  assert.ok(source.includes('const canManageTests = auth.hasPermission("lab.test.manage");'));
  assert.ok(source.includes('const canCollectPayment = auth.hasPermission("lab.order.collect_payment");'));
  assert.ok(source.includes('const canCollectSample = auth.hasPermission("lab.order.collect_sample");'));
  assert.ok(source.includes('const canEnterResults = auth.hasPermission("lab.order.result_entry");'));
  assert.ok(source.includes('const canGenerateReport = auth.hasPermission("lab.order.generate_report");'));
  assert.ok(source.includes('const canReviewReport = auth.hasPermission("lab.order.review");'));
  assert.ok(source.includes('const canCreateOrders = auth.hasPermission("lab.order.create");'));
  assert.ok(!source.includes('auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN")'));
});

test("lab csv template download requests csv text and not json", () => {
  const apiSource = readSource("api/clinicApi.ts");
  assert.ok(apiSource.includes('return httpGetText("/api/lab/tests/import-template", { token, tenantId, accept: "text/csv, */*" });'));
});
