import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("appointment quick register uses shared schema validation and field errors", () => {
  const source = readSource("pages/appointments/AppointmentsPage.tsx");
  assert.ok(source.includes("patientQuickRegisterSchema"));
  assert.ok(source.includes("const quickRegisterPreview = React.useMemo("));
  assert.ok(source.includes("const quickRegisterFieldErrors: Record<string, string> = quickRegisterPreview.success ? {} : mapZodErrors(quickRegisterPreview.error);"));
  assert.ok(source.includes("error={Boolean(quickRegisterFieldErrors.firstName)}"));
  assert.ok(source.includes("error={Boolean(quickRegisterFieldErrors.dateOfBirth)}"));
  assert.ok(source.includes("disabled={quickRegisterSaving || !quickRegisterPreview.success}"));
});
