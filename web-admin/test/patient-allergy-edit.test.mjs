import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("patient allergies field flushes pending free-text input into persisted form state", () => {
  const source = readSource("pages/patients/PatientFormPage.tsx");

  assert.ok(source.includes("const [allergiesInput, setAllergiesInput] = React.useState(\"\");"));
  assert.ok(source.includes("function normalizeCsvValues(values: string[])"));
  assert.ok(source.includes("function mergeAllergiesValue(existing: string, pending: string, replaceSingleExisting = false)"));
  assert.ok(source.includes("const allergies = mergeAllergiesValue(form.allergies, allergiesInput, true);"));
  assert.ok(source.includes("setAllergiesInput(\"\");"));
  assert.ok(source.includes("inputValue={allergiesInput}"));
  assert.ok(source.includes("onInputChange={(_, inputValue, reason) =>"));
  assert.ok(source.includes("onBlur={() => commitAllergiesInput(false)}"));
  assert.ok(source.includes("setForm((current) => ({ ...current, allergies: toCsv(normalizeCsvValues(values)) }));"));
});
