import test from "node:test";
import assert from "node:assert/strict";
import { buildDocumentTypeOptions, documentTypeLabel } from "../src/components/clinical/documentTypeOptions.js";

test("document type options keep the preferred standard order exactly once", () => {
  const options = buildDocumentTypeOptions();
  assert.deepEqual(options.map((item) => item.value), [
    "EXTERNAL_LAB_REPORT",
    "INTERNAL_LAB_REPORT",
    "RADIOLOGY_REPORT",
    "REFERRAL_LETTER",
    "DISCHARGE_SUMMARY",
    "OLD_PRESCRIPTION",
    "INSURANCE_DOCUMENT",
    "IDENTITY_DOCUMENT",
    "OTHER",
  ]);
});

test("document type options dedupe duplicate backend values by canonical code", () => {
  const options = buildDocumentTypeOptions([
    { value: "LAB_REPORT", label: "Lab Report" },
    { value: "LAB_REPORT", label: "Duplicate Lab Report" },
    { value: "X_RAY", label: "X-Ray" },
    { value: "X_RAY", label: "Duplicate X-Ray" },
  ]);
  assert.equal(options.filter((item) => item.value === "LAB_REPORT").length, 1);
  assert.equal(options.filter((item) => item.value === "X_RAY").length, 1);
});

test("document type options dedupe duplicate default values", () => {
  const options = buildDocumentTypeOptions([
    { value: "EXTERNAL_LAB_REPORT", label: "Duplicate External Lab Report" },
    { value: "OTHER", label: "Other" },
  ]);
  assert.equal(options.filter((item) => item.value === "EXTERNAL_LAB_REPORT").length, 1);
  assert.equal(options.find((item) => item.value === "EXTERNAL_LAB_REPORT")?.label, "External Lab Report");
});

test("document type options keep mixed backend values after standard list without duplicates", () => {
  const options = buildDocumentTypeOptions([
    { value: "RADIOLOGY_REPORT", label: "Duplicate Radiology Report" },
    { value: "EMPLOYER_MEDICAL_CERTIFICATE", label: "Employer Medical Certificate" },
    { value: "EXTERNAL_LAB_REPORT", label: "Duplicate External Lab Report" },
    { value: "FITNESS_CERTIFICATE", label: "Fitness Certificate" },
  ]);
  assert.equal(options.filter((item) => item.value === "EXTERNAL_LAB_REPORT").length, 1);
  assert.equal(options.filter((item) => item.value === "RADIOLOGY_REPORT").length, 1);
  assert.equal(options.find((item) => item.value === "RADIOLOGY_REPORT")?.label, "Radiology Report");
  assert.deepEqual(options.slice(-2).map((item) => item.value), ["EMPLOYER_MEDICAL_CERTIFICATE", "FITNESS_CERTIFICATE"]);
});

test("document type options keep custom backend types after standard list", () => {
  const options = buildDocumentTypeOptions([
    { value: "EMPLOYER_MEDICAL_CERTIFICATE", label: "Employer Medical Certificate" },
    { value: "FITNESS_CERTIFICATE", label: "Fitness Certificate" },
  ]);
  assert.deepEqual(options.slice(-2).map((item) => item.value), ["EMPLOYER_MEDICAL_CERTIFICATE", "FITNESS_CERTIFICATE"]);
  assert.deepEqual(options.slice(-2).map((item) => item.label), ["Employer Medical Certificate", "Fitness Certificate"]);
});

test("document type label resolves legacy and custom values", () => {
  assert.equal(documentTypeLabel("LAB_REPORT"), "External Lab Report");
  assert.equal(documentTypeLabel("  referral_letter "), "Referral Letter");
  assert.equal(documentTypeLabel("EMPLOYER_MEDICAL_CERTIFICATE"), "EMPLOYER MEDICAL CERTIFICATE");
});
