import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("converted lead detail renders historical mode and conversion summary", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");

  assert.ok(page.includes('lead.status === "CONVERTED" ? "View Details" : "View/Edit"'));
  assert.ok(page.includes('Converted Lead Details'));
  assert.ok(page.includes('Converted to Patient'));
  assert.ok(page.includes('Patient •'));
  assert.ok(page.includes('Converted:'));
  assert.ok(page.includes('Converted by:'));
  assert.ok(page.includes('Read-only after conversion.'));
  assert.ok(page.includes('canPersistLeadForm'));
  assert.ok(page.includes('canAddLeadNote'));
  assert.ok(page.includes('View Consultation History'));
  assert.ok(page.includes('Open Patient'));
});
