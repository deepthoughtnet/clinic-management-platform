import test from "node:test";
import assert from "node:assert/strict";

import { createTenantSchema } from "../dist/index.js";

test("tenant schema accepts a valid payload", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    postalCode: "411001",
    phone: "9876543210",
    clinicEmail: "clinic@example.com",
    addressLine1: "123 Main Road",
    addressLine2: "",
    registrationNumber: "JEEVANAM-AUTO-REG-001",
    planId: "FREE",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    tempPassword: "",
    modules: { APPOINTMENTS: true },
    publicListingEnabled: false,
  });

  assert.equal(result.success, true);
});

test("tenant schema rejects a blank registration number", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    registrationNumber: "   ",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, false);
  assert.match(result.error.issues.find((issue) => issue.path.join(".") === "registrationNumber")?.message || "", /required/i);
});

test("tenant schema rejects an overlong registration number", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    registrationNumber: "J".repeat(129),
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, false);
  assert.ok(result.error.issues.some((issue) => issue.path.join(".") === "registrationNumber"));
});

test("tenant schema rejects a missing clinic name", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, false);
  assert.match(result.error.issues[0]?.message || "", /required/i);
});

test("tenant schema rejects an invalid email", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    clinicEmail: "not-an-email",
    registrationNumber: "JEEVANAM-AUTO-REG-001",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, false);
  assert.ok(result.error.issues.some((issue) => issue.path.join(".") === "clinicEmail"));
});

test("tenant schema rejects an invalid Indian mobile number", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    registrationNumber: "JEEVANAM-AUTO-REG-001",
    phone: "12345",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, false);
  assert.ok(result.error.issues.some((issue) => issue.path.join(".") === "phone"));
});

test("tenant schema allows optional fields to be omitted", () => {
  const result = createTenantSchema.safeParse({
    clinicName: "Arogia Clinic",
    tenantCode: "arogia-clinic",
    displayName: "Arogia Clinic",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    registrationNumber: "JEEVANAM-AUTO-REG-001",
    adminEmail: "admin@example.com",
    adminFirstName: "Admin",
    adminLastName: "User",
    planId: "FREE",
  });

  assert.equal(result.success, true);
});
