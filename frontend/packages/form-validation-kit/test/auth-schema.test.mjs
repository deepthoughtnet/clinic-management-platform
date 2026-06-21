import test from "node:test";
import assert from "node:assert/strict";

import { loginSchema, otpRequestSchema, otpVerifySchema } from "../dist/index.js";

test("login schema accepts a valid payload", () => {
  const result = loginSchema.safeParse({
    email: "admin@example.com",
    password: "Admin@1234",
  });
  assert.equal(result.success, true);
});

test("otp request schema accepts a valid payload", () => {
  const result = otpRequestSchema.safeParse({
    tenantCode: "clinic-demo",
    mobile: "9876543210",
  });
  assert.equal(result.success, true);
});

test("otp request schema normalizes indian mobile prefixes", () => {
  const result = otpRequestSchema.safeParse({
    tenantCode: "clinic-demo",
    mobile: "+91 98765 43210",
  });
  assert.equal(result.success, true);
  if (result.success) {
    assert.equal(result.data.mobile, "9876543210");
  }
});

test("otp verify schema accepts a valid payload", () => {
  const result = otpVerifySchema.safeParse({
    tenantCode: "clinic-demo",
    mobile: "9876543210",
    otp: "123456",
  });
  assert.equal(result.success, true);
});

test("otp verify schema rejects an invalid otp", () => {
  const result = otpVerifySchema.safeParse({
    tenantCode: "clinic-demo",
    mobile: "9876543210",
    otp: "1234",
  });
  assert.equal(result.success, false);
});
