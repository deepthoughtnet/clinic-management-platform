import test from "node:test";
import assert from "node:assert/strict";

import {
  isValidPatientOtpInput,
  isValidPatientPhoneInput,
  sanitizePatientOtpInput,
  sanitizePatientPhoneInput,
} from "../src/pages/patient/patientLoginInput.js";

test("patient phone input normalizes prefixes and preserves typed digits", () => {
  assert.equal(sanitizePatientPhoneInput("9191900"), "9191900");
  assert.equal(sanitizePatientPhoneInput("+91 98765 43210"), "9876543210");
  assert.equal(sanitizePatientPhoneInput("9800000000000000000"), "9800000000000000000");
  assert.equal(sanitizePatientPhoneInput("9876543210"), "9876543210");
});

test("patient phone validation requires a valid indian mobile number", () => {
  assert.equal(isValidPatientPhoneInput("9191900"), false);
  assert.equal(isValidPatientPhoneInput("9876543210"), true);
  assert.equal(isValidPatientPhoneInput("+91 98765 43210"), true);
  assert.equal(isValidPatientPhoneInput("5876543210"), false);
});

test("patient otp input trims pasted overflow", () => {
  assert.equal(sanitizePatientOtpInput("777777777777"), "777777");
  assert.equal(sanitizePatientOtpInput("12ab34cd56ef78"), "123456");
});

test("patient otp validation requires exactly six digits", () => {
  assert.equal(isValidPatientOtpInput("77777"), false);
  assert.equal(isValidPatientOtpInput("777777"), true);
  assert.equal(isValidPatientOtpInput("7777777"), false);
});
