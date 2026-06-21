import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient form maps validation issues to field errors", () => {
  const source = readSource("pages/patients/PatientFormPage.tsx");
  assert.ok(source.includes("patientQuickRegisterSchema"));
  assert.ok(source.includes("mapZodErrors(parsed.error)"));
  assert.ok(source.includes("setFieldErrors(mapZodErrors(parsed.error))"));
  assert.ok(source.includes("Please correct the highlighted fields."));
  assert.ok(source.includes("fieldErrors.firstName"));
  assert.ok(source.includes("fieldErrors.dateOfBirth"));
  assert.ok(source.includes("fieldErrors.emergencyContactMobile"));
});
