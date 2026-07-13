import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("patient form maps validation issues to field errors", () => {
  const source = readSource("pages/patients/PatientFormPage.tsx");
  assert.ok(source.includes("patientQuickRegisterSchema"));
  assert.ok(source.includes("mapZodErrors(parsed.error)"));
  assert.ok(source.includes("setFieldErrors(mapZodErrors(parsed.error))"));
  assert.ok(source.includes("const validationPreview = React.useMemo("));
  assert.ok(source.includes("const liveFieldErrors: Record<string, string> = validationPreview.success ? {} : mapZodErrors(validationPreview.error);"));
  assert.ok(source.includes("Please correct the highlighted fields."));
  assert.ok(source.includes("fieldErrors.firstName"));
  assert.ok(source.includes("fieldErrors.dateOfBirth"));
  assert.ok(source.includes("fieldErrors.emergencyContactMobile"));
  assert.ok(source.includes("const isClinicAdmin = auth.rolesUpper.includes(\"CLINIC_ADMIN\") || tenantRole === \"CLINIC_ADMIN\";"));
  assert.ok(source.includes("loadedPatient?.canEdit === true"));
  assert.ok(source.includes("disabled={disabled || checking || !validationPreview.success}"));
  assert.ok(source.includes("existingConditionsInput"));
  assert.ok(source.includes("longTermMedicationsInput"));
  assert.ok(source.includes("commitExistingConditionsInput"));
  assert.ok(source.includes("commitLongTermMedicationsInput"));
  assert.ok(source.includes("inputValue={existingConditionsInput}"));
  assert.ok(source.includes("inputValue={longTermMedicationsInput}"));
  assert.ok(source.includes("const existingConditions = mergeAllergiesValue(form.existingConditions, existingConditionsInput, false);"));
  assert.ok(source.includes("const longTermMedications = mergeAllergiesValue(form.longTermMedications, longTermMedicationsInput, false);"));
});
