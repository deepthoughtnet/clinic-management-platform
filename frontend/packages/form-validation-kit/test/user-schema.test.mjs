import test from "node:test";
import assert from "node:assert/strict";

import { userCreateSchema, userUpdateSchema } from "../dist/index.js";

test("user schema accepts a valid payload", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    lastName: "Sharma",
    email: "anita@example.com",
    role: "DOCTOR",
    mobile: "9876543210",
    tempPassword: "Temp@1234",
    active: true,
    department: "General Medicine",
  });
  assert.equal(result.success, true);
});

test("user schema rejects a missing first name", () => {
  const result = userCreateSchema.safeParse({
    firstName: "",
    email: "anita@example.com",
    role: "DOCTOR",
    department: "General Medicine",
  });
  assert.equal(result.success, false);
});

test("user schema rejects an invalid email", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "not-an-email",
    role: "DOCTOR",
    department: "General Medicine",
  });
  assert.equal(result.success, false);
});

test("user schema rejects an invalid mobile number", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "anita@example.com",
    role: "DOCTOR",
    mobile: "12345",
    department: "General Medicine",
  });
  assert.equal(result.success, false);
});

test("user schema allows optional fields to be omitted", () => {
  const result = userCreateSchema.safeParse({
    firstName: "Anita",
    email: "anita@example.com",
    role: "DOCTOR",
    department: "General Medicine",
  });
  assert.equal(result.success, true);
});

test("user update schema accepts a valid payload", () => {
  const result = userUpdateSchema.safeParse({
    displayName: "Anita Sharma",
    email: "anita@example.com",
    username: "anita.sharma",
    employeeCode: "EMP-001",
    mobile: "9876543210",
    department: "General Medicine",
    role: "DOCTOR",
    active: true,
  });
  assert.equal(result.success, true);
});

test("user update schema rejects a missing department", () => {
  const result = userUpdateSchema.safeParse({
    displayName: "Anita Sharma",
    email: "anita@example.com",
    role: "DOCTOR",
    active: true,
  });
  assert.equal(result.success, false);
});

test("user update schema rejects a suspicious role and department combination", () => {
  const result = userUpdateSchema.safeParse({
    displayName: "Anita Sharma",
    email: "anita@example.com",
    department: "Reception",
    role: "DOCTOR",
    active: true,
  });
  assert.equal(result.success, false);
});
