import test from "node:test";
import assert from "node:assert/strict";

import { labConsultationOrderCreateSchema, labOrderCreateSchema, labResultEntrySchema, labTestMasterSchema } from "../dist/index.js";

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

test("lab test master accepts optional sample type", () => {
  const result = labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "24",
    price: 250,
  });

  assert.equal(result.success, true);
});

test("lab test master trims and validates the test code format", () => {
  const trimmed = labTestMasterSchema.safeParse({
    testCode: "  cbc  ",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    price: 250,
  });
  assert.equal(trimmed.success, true);
  assert.equal(trimmed.data.testCode, "cbc");

  assert.equal(labTestMasterSchema.safeParse({
    testCode: "",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    price: 250,
  }).success, false);

  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC#",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    price: 250,
  }).success, false);

  assert.equal(labTestMasterSchema.safeParse({
    testCode: "C".repeat(31),
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    price: 250,
  }).success, false);
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

test("lab test master enforces numeric TAT and price precision", () => {
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "0",
    price: 99.5,
  }).success, true);
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "999",
    price: 99.99,
  }).success, true);
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "-1",
    price: 99.99,
  }).success, false);
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "6 hrs",
    price: 99.99,
  }).success, false);
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "1000",
    price: 99.99,
  }).success, false);
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "24",
    price: 99.999,
  }).success, false);
});

test("lab test master rejects blank and duplicate parameters", () => {
  assert.equal(labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "24",
    price: 250,
    parameters: [{ parameterName: "  ", unit: "", normalRange: "", criticalRange: "", sortOrder: 1 }],
  }).success, false);

  const duplicate = labTestMasterSchema.safeParse({
    testCode: "CBC",
    testName: "Complete Blood Count",
    category: "HEMATOLOGY",
    turnaroundTime: "24",
    price: 250,
    parameters: [
      { parameterName: "Hemoglobin", unit: "", normalRange: "", criticalRange: "", sortOrder: 1 },
      { parameterName: " hemoglobin ", unit: "", normalRange: "", criticalRange: "", sortOrder: 2 },
    ],
  });

  assert.equal(duplicate.success, false);
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

test("consultation lab order create accepts patientId, tests, and notes", () => {
  const result = labConsultationOrderCreateSchema.safeParse({
    patientId: "11111111-1111-4111-8111-111111111111",
    testIds: ["22222222-2222-4222-8222-222222222222"],
    notes: "Consultation request",
  });

  assert.equal(result.success, true);
});
