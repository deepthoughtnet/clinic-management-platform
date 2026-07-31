import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider requirement labels map backend codes to business labels", () => {
  const helper = read("src/features/provider/providerRequirementLabels.ts");

  assert.ok(helper.includes('CLINIC_NAME_REQUIRED: "Clinic name"'));
  assert.ok(helper.includes('CLINIC_REGISTRATION_NUMBER_REQUIRED: "Clinic registration number"'));
  assert.ok(helper.includes('PRIMARY_LOCATION_REQUIRED: "Primary location"'));
  assert.ok(helper.includes('SERVICES_REQUIRED: "At least one service"'));
  assert.ok(helper.includes("humanizeValue"));
  assert.ok(helper.includes("providerRequirementGroup"));
  assert.ok(helper.includes("groupProviderRequirements"));
});

test("unknown requirement codes are humanized safely rather than rendered raw", () => {
  const helper = read("src/features/provider/providerRequirementLabels.ts");

  assert.ok(helper.includes('return LABELS[code] ?? humanizeValue(code);'));
  assert.ok(helper.includes(".replace(/_/g, \" \")"));
  assert.ok(helper.includes(".replace(/\\b\\w/g, (char) => char.toUpperCase())"));
});
