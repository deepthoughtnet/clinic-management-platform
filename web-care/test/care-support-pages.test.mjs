import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

function read(relPath) {
  const testDir = path.dirname(fileURLToPath(import.meta.url));
  const webCareRoot = path.resolve(testDir, "..");
  return fs.readFileSync(path.join(webCareRoot, relPath), "utf8");
}

test("care support pages expose real public destinations and content", () => {
  const app = read("src/App.tsx");
  const supportPages = read("src/pages/public/CareSupportPages.tsx");

  assert.ok(app.includes('path="/contact"'));
  assert.ok(app.includes('path="/help-centre"'));
  assert.ok(app.includes('path="/privacy-policy"'));
  assert.ok(app.includes('path="/terms"'));

  assert.ok(supportPages.includes("Contact Jeevanam Care"));
  assert.ok(supportPages.includes("Email support"));
  assert.ok(supportPages.includes("Jeevanam Care help centre"));
  assert.ok(supportPages.includes("Privacy Policy"));
  assert.ok(supportPages.includes("Terms and Conditions"));
  assert.ok(supportPages.includes("support@jeevanam.health"));
  assert.ok(!supportPages.includes("Replace this page"));
  assert.ok(!supportPages.includes("can be replaced"));
  assert.ok(!supportPages.includes("deployment"));
  assert.ok(!supportPages.includes("deployment-specific"));
  assert.ok(!supportPages.includes("preview environment"));
  assert.ok(!supportPages.includes("production environment"));
  assert.ok(!supportPages.includes("configured support destination"));
  assert.ok(!supportPages.includes("configured environment"));
});
