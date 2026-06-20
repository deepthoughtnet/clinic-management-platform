import test from "node:test";
import assert from "node:assert/strict";

import { labOrderCreateSchema, labResultEntrySchema, labTestMasterSchema } from "../dist/index.js";

test("lab test master accepts a valid payload", () => {
  const result = labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    sampleType: "Blood",
    price: 250,
    active: true,
  });

  assert.equal(result.success, true);
});

test("lab test master rejects a missing test name", () => {
  const result = labTestMasterSchema.safeParse({
    testCode: "CBC#",
    testName: "",
    category: "HEMATOLOGY",
    price: 250,
  });

  assert.equal(result.success, false);
});

test("lab result entry accepts optional values", () => {
  const result = labResultEntrySchema.safeParse({
    comments: "",
    items: [
      {
        labOrderItemId: "11111111-1111-4111-8111-111111111111",
        resultValue: "13.4",
        unit: "",
        referenceRange: "",
        componentResults: [],
      },
    ],
  });

  assert.equal(result.success, true);
});

test("lab order create requires at least one test", () => {
  const result = labOrderCreateSchema.safeParse({
    patientId: "11111111-1111-4111-8111-111111111111",
    testIds: [],
    notes: "",
  });

  assert.equal(result.success, false);
});
