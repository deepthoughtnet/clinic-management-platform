import test from "node:test";
import assert from "node:assert/strict";

import { doctorCreateSchema, doctorUpdateSchema } from "../dist/index.js";

const validDoctorPayload = {
  doctorName: "Dr. Meera Iyer",
  specialization: "Dermatology",
  email: "meera@example.com",
  mobile: "9876543210",
  registrationNumber: "MCI/12345",
  active: true,
  publicListingEnabled: false,
};

test("doctor schema accepts a valid payload", () => {
  const result = doctorCreateSchema.safeParse(validDoctorPayload);
  assert.equal(result.success, true);
});

test("doctor schema rejects a missing name", () => {
  const result = doctorCreateSchema.safeParse({
    ...validDoctorPayload,
    doctorName: "",
  });
  assert.equal(result.success, false);
});

test("doctor schema rejects an invalid email", () => {
  const result = doctorCreateSchema.safeParse({
    ...validDoctorPayload,
    email: "invalid",
  });
  assert.equal(result.success, false);
});

test("doctor schema rejects an invalid mobile", () => {
  const result = doctorCreateSchema.safeParse({
    ...validDoctorPayload,
    mobile: "12345",
  });
  assert.equal(result.success, false);
});

test("doctor update schema allows sparse updates", () => {
  const result = doctorUpdateSchema.safeParse({
    specialization: "General Medicine",
  });
  assert.equal(result.success, true);
});
