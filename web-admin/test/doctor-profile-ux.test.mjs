import test from "node:test";
import assert from "node:assert/strict";
import { doctorUpdateSchema } from "../../frontend/packages/form-validation-kit/dist/index.js";

test("doctor update schema accepts future-ready specialization and fee fields", () => {
  const parsed = doctorUpdateSchema.safeParse({
    doctorName: "Dr. Asha",
    specialization: "Dermatology",
    specializations: ["Dermatology", "Skin Care"],
    mobile: "9876543210",
    email: "doctor@example.com",
    registrationNumber: "REG-123",
    consultationRoom: "Room 1",
    qualification: "MBBS",
    consultationFee: 500,
    opdFee: 500,
    followUpFee: 300,
    emergencyFee: 800,
    yearsOfExperience: 12,
    age: 40,
    active: true,
    publicListingEnabled: false,
    slug: "dr-asha",
  });

  assert.equal(parsed.success, true);
});
