import test from "node:test";
import assert from "node:assert/strict";

import { doctorCreateSchema, doctorUpdateSchema } from "../dist/index.js";

const validDoctorPayload = {
  doctorName: "Dr. Meera Iyer",
  specialization: "Dermatology",
  specializations: ["Dermatology"],
  email: "meera@example.com",
  mobile: "9876543210",
  qualification: "MBBS, MD",
  registrationNumber: "MCI/12345",
  opdFee: 500,
  followUpFee: 300,
  emergencyFee: 800,
  yearsOfExperience: 12,
  dateOfBirth: "1985-03-15",
  active: true,
  publicListingEnabled: false,
  slug: "dr-meera-iyer",
};

test("doctor schema accepts a valid payload", () => {
  const result = doctorCreateSchema.safeParse(validDoctorPayload);
  assert.equal(result.success, true);
});

test("doctor schema rejects blank mandatory fields", () => {
  const cases = [
    ["mobile", ""],
    ["specializations", []],
    ["qualification", ""],
    ["registrationNumber", ""],
    ["opdFee", ""],
    ["followUpFee", ""],
    ["emergencyFee", ""],
    ["yearsOfExperience", ""],
    ["dateOfBirth", ""],
  ];

  for (const [field, value] of cases) {
    const result = doctorCreateSchema.safeParse({
      ...validDoctorPayload,
      [field]: value,
    });
    assert.equal(result.success, false, `Expected ${field} to be rejected`);
  }
});

test("doctor schema rejects invalid mobile, dob, and slug formats", () => {
  const result = doctorCreateSchema.safeParse({
    ...validDoctorPayload,
    mobile: "12345",
    dateOfBirth: "2050-01-01",
    slug: "Invalid Slug!",
  });
  assert.equal(result.success, false);
});

test("doctor update schema requires the same mandatory fields", () => {
  const result = doctorUpdateSchema.safeParse({
    mobile: "9876543210",
    specializations: ["Dermatology"],
    qualification: "MBBS",
    registrationNumber: "MCI/12345",
    opdFee: 500,
    followUpFee: 300,
    emergencyFee: 800,
    yearsOfExperience: 12,
    dateOfBirth: "1985-03-15",
  });
  assert.equal(result.success, true);
});
