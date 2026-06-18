import test from "node:test";
import assert from "node:assert/strict";

import { userCreateSchema } from "../dist/index.js";

test("user schema accepts a valid payload", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    lastName: "Sharma",
    email: "anita@example.com",
    role: "DOCTOR",
    mobile: "9876543210",
    tempPassword: "Temp@1234",
    active: true,
  });
  assert.equal(result.success, true);
});

test("user schema rejects a missing first name", () => {
  const result = userCreateSchema.safeParse({
    firstName: "",
    email: "anita@example.com",
    role: "DOCTOR",
  });
  assert.equal(result.success, false);
});

test("user schema rejects an invalid email", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "not-an-email",
    role: "DOCTOR",
  });
  assert.equal(result.success, false);
});

test("user schema rejects an invalid mobile number", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "anita@example.com",
    role: "DOCTOR",
    mobile: "12345",
  });
  assert.equal(result.success, false);
});

test("user schema allows optional fields to be omitted", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "anita@example.com",
    role: "DOCTOR",
  });
  assert.equal(result.success, true);
});
