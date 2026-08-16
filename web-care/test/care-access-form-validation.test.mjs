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

test("care access forms enforce required markers and max lengths", () => {
  const pages = read("src/pages/patient/PatientPortalPages.tsx");

  assert.ok(pages.includes('Phone number <span className="patient-field-required" aria-hidden="true">*</span>'));
  assert.ok(pages.includes('Temporary access code <span className="patient-field-required" aria-hidden="true">*</span>'));
  assert.ok(pages.includes('Full name <span className="patient-field-required" aria-hidden="true">*</span>'));
  assert.ok(pages.includes('Mobile number <span className="patient-field-required" aria-hidden="true">*</span>'));
  assert.ok(pages.includes('Clinic or hospital slug <span className="patient-field-required" aria-hidden="true">*</span>'));
  assert.ok(pages.includes('maxLength={10}'));
  assert.ok(pages.includes('maxLength={8}'));
  assert.ok(pages.includes('maxLength={120}'));
  assert.ok(pages.includes('maxLength={254}'));
  assert.ok(pages.includes('maxLength={60}'));
  assert.ok(pages.includes('maxLength={500}'));
  assert.ok(pages.includes('Enter a valid 10-digit Indian mobile number.'));
  assert.ok(pages.includes('Enter the valid 8-digit temporary access code.'));
  assert.ok(pages.includes('Please select or enter a valid clinic or hospital slug.'));
});
