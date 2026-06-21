import test from "node:test";
import assert from "node:assert/strict";

import { patientRegistrationSchema, patientProfileSchema } from "../dist/index.js";

const validPatientPayload = {
  firstName: "Riya",
  lastName: "Shah",
  mobile: "9876543210",
  email: "riya@example.com",
  gender: "FEMALE",
  dateOfBirth: "1994-02-18",
  ageYears: 31,
  bloodGroup: "O+",
  addressLine1: "12 Main Road",
  city: "Pune",
  state: "Maharashtra",
  country: "India",
  postalCode: "411001",
  emergencyContactName: "Amit Shah",
  emergencyContactMobile: "9876501234",
  active: true,
};

test("patient schema accepts a valid payload", () => {
  const result = patientRegistrationSchema.safeParse(validPatientPayload);
  assert.equal(result.success, true);
});

test("patient schema accepts a patient payload with a blank blood group", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    bloodGroup: null,
  });
  assert.equal(result.success, true);
});

test("patient schema accepts the sample new patient payload", () => {
  const result = patientRegistrationSchema.safeParse({
    firstName: "temp",
    lastName: "one",
    mobile: "9678000023",
    email: "riya@example.com",
    gender: "MALE",
    dateOfBirth: "1961-01-01",
    ageYears: 65,
    bloodGroup: null,
    addressLine1: "12 Main Road",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    postalCode: "411001",
    emergencyContactName: "Amit Shah",
    emergencyContactMobile: "9876501234",
    active: true,
  });
  assert.equal(result.success, true);
  if (result.success) {
    assert.equal(result.data.mobile, "9678000023");
  }
});

test("patient schema normalizes indian mobile prefixes", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    mobile: "+91 98765 43210",
    emergencyContactMobile: "919876543210",
  });
  assert.equal(result.success, true);
  if (result.success) {
    assert.equal(result.data.mobile, "9876543210");
    assert.equal(result.data.emergencyContactMobile, "9876543210");
  }
});

test("patient schema rejects a missing name", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    firstName: "",
  });
  assert.equal(result.success, false);
});

test("patient schema rejects an invalid email", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    email: "not-an-email",
  });
  assert.equal(result.success, false);
});

test("patient schema rejects an invalid Indian mobile", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    mobile: "12345",
  });
  assert.equal(result.success, false);
});

test("patient schema rejects an invalid first name", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    firstName: "temp-1",
  });
  assert.equal(result.success, false);
  if (!result.success) {
    assert.equal(result.error.issues[0]?.path[0], "firstName");
  }
});

test("patient schema rejects a future date of birth", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    dateOfBirth: "2999-01-01",
  });
  assert.equal(result.success, false);
  if (!result.success) {
    assert.equal(result.error.issues[0]?.path[0], "dateOfBirth");
  }
});

test("patient schema rejects an age above 120", () => {
  const result = patientRegistrationSchema.safeParse({
    ...validPatientPayload,
    ageYears: 121,
  });
  assert.equal(result.success, false);
  if (!result.success) {
    assert.equal(result.error.issues[0]?.path[0], "ageYears");
  }
});

test("patient schema allows optional fields to be omitted", () => {
  const result = patientRegistrationSchema.safeParse({
    firstName: "Riya",
    mobile: "9876543210",
  });
  assert.equal(result.success, true);
});

test("patient profile schema accepts sparse updates", () => {
  const result = patientProfileSchema.safeParse({
    firstName: "Riya",
    mobile: "9876543210",
  });
  assert.equal(result.success, true);
});

test("patient schema accepts a null date of birth", () => {
  const result = patientRegistrationSchema.safeParse({
    firstName: "Riya",
    mobile: "9876543210",
    dateOfBirth: null,
  });
  assert.equal(result.success, true);
});
