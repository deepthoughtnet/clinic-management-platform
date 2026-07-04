import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("product implementation page reflects OPD UAT readiness and current milestone", () => {
  const source = readSource("pages/platform/ProductImplementationPage.tsx");
  assert.ok(source.includes("Jeevanam Product Implementation Status"));
  assert.ok(source.includes("OPD platform is feature-complete for UAT and entering customer validation / production hardening."));
  assert.ok(source.includes("jeevanam-v1-opd-uat-ready"));
  assert.ok(source.includes("Feature Completion"));
  assert.ok(source.includes("UAT Readiness"));
  assert.ok(source.includes("Production Readiness"));
  assert.ok(source.includes("Risk Level"));
  assert.ok(source.includes("Next Phase"));
  assert.ok(source.includes("Customer UAT + Production Hardening"));
  assert.ok(source.includes("Module status overview"));
  assert.ok(source.includes("Platform Foundation"));
  assert.ok(source.includes("Reception"));
  assert.ok(source.includes("Doctor Workspace"));
  assert.ok(source.includes("Laboratory"));
  assert.ok(source.includes("Billing"));
  assert.ok(source.includes("Pharmacy"));
  assert.ok(source.includes("Patient Portal / Public Booking"));
  assert.ok(source.includes("AI / AIVA"));
  assert.ok(source.includes("Reports / Admin"));
  assert.ok(source.includes("Production Operations"));
  assert.ok(source.includes("Customer UAT checklist"));
  assert.ok(source.includes("Production hardening checklist"));
  assert.ok(source.includes("Ready for integrated OPD UAT. Not yet production-certified."));
});
