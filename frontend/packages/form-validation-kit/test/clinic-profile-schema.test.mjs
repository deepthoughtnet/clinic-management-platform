import test from "node:test";
import assert from "node:assert/strict";

import { clinicProfileSchema } from "../dist/index.js";

function basePayload(overrides = {}) {
  return {
    clinicName: "Jeevanam Healthcare Clinic",
    displayName: "Jeevanam Healthcare",
    phone: "9876543210",
    email: "clinic@example.com",
    addressLine1: "123 Main Road",
    addressLine2: "",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    postalCode: "411001",
    registrationNumber: "REG-123",
    gstNumber: "",
    logoDocumentId: "",
    active: true,
    publicListingEnabled: false,
    slug: "",
    ...overrides,
  };
}

function issueFor(result, field) {
  return result.error.issues.find((issue) => issue.path[0] === field);
}

test("clinic profile schema accepts a valid payload", () => {
  const result = clinicProfileSchema.safeParse(basePayload({
    slug: "jeevanam-healthcare",
    gstNumber: "27ABCDE1234F1Z5",
  }));

  assert.equal(result.success, true);
});

test("clinic profile schema accepts optional blanks and auto-generated slug input", () => {
  const result = clinicProfileSchema.safeParse(basePayload());

  assert.equal(result.success, true);
});

for (const [field, message] of [
  ["clinicName", "Clinic name is required."],
  ["displayName", "Display name is required."],
  ["phone", "Phone is required."],
  ["email", "Email is required."],
  ["addressLine1", "Address line 1 is required."],
  ["city", "City is required."],
  ["state", "State is required."],
  ["country", "Country is required."],
  ["postalCode", "Postal code is required."],
  ["registrationNumber", "Registration number is required."],
]) {
  test(`clinic profile schema rejects missing ${field}`, () => {
    const result = clinicProfileSchema.safeParse(basePayload({ [field]: "" }));

    assert.equal(result.success, false);
    assert.equal(issueFor(result, field)?.message, message);
  });
}

for (const [field, message] of [
  ["clinicName", "Clinic name is required."],
  ["displayName", "Display name is required."],
  ["phone", "Phone is required."],
  ["email", "Email is required."],
  ["addressLine1", "Address line 1 is required."],
  ["city", "City is required."],
  ["state", "State is required."],
  ["country", "Country is required."],
  ["postalCode", "Postal code is required."],
  ["registrationNumber", "Registration number is required."],
]) {
  test(`clinic profile schema rejects whitespace-only ${field}`, () => {
    const result = clinicProfileSchema.safeParse(basePayload({ [field]: "   " }));

    assert.equal(result.success, false);
    assert.equal(issueFor(result, field)?.message, message);
  });
}

test("clinic profile schema rejects invalid phone numbers", () => {
  const result = clinicProfileSchema.safeParse(basePayload({ phone: "abc123" }));

  assert.equal(result.success, false);
  assert.equal(issueFor(result, "phone")?.message, "Enter a valid 10-digit Indian mobile number.");
});

test("clinic profile schema rejects invalid email addresses", () => {
  const result = clinicProfileSchema.safeParse(basePayload({ email: "clinic@" }));

  assert.equal(result.success, false);
  assert.equal(issueFor(result, "email")?.message, "Enter a valid email address.");
});

test("clinic profile schema rejects invalid postal codes for India", () => {
  const result = clinicProfileSchema.safeParse(basePayload({ postalCode: "41A001" }));

  assert.equal(result.success, false);
  assert.equal(issueFor(result, "postalCode")?.message, "Enter a valid 6-digit PIN code.");
});

test("clinic profile schema rejects invalid GST numbers when provided", () => {
  const result = clinicProfileSchema.safeParse(basePayload({ gstNumber: "1234" }));

  assert.equal(result.success, false);
  assert.equal(issueFor(result, "gstNumber")?.message, "Enter a valid GSTIN.");
});

test("clinic profile schema rejects invalid public slugs", () => {
  const result = clinicProfileSchema.safeParse(basePayload({ slug: "bad slug!" }));

  assert.equal(result.success, false);
  assert.equal(issueFor(result, "slug")?.message, "Enter a valid public slug.");
});
