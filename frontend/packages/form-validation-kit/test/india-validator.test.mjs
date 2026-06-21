import test from "node:test";
import assert from "node:assert/strict";

import {
  indianMobileNumber,
  normalizeIndianMobileInput,
  optionalIndianMobileNumber,
} from "../dist/index.js";

test("normalizes indian mobile prefixes and spacing", () => {
  assert.equal(normalizeIndianMobileInput("9400000024"), "9400000024");
  assert.equal(normalizeIndianMobileInput("+91 9400000024"), "9400000024");
  assert.equal(normalizeIndianMobileInput("919400000024"), "9400000024");
  assert.equal(normalizeIndianMobileInput("94000000244"), "94000000244");
});

test("validates indian mobile numbers with the shared schema", () => {
  assert.equal(indianMobileNumber().safeParse("9400000024").success, true);
  assert.equal(indianMobileNumber().safeParse("+91 9400000024").success, true);
  assert.equal(indianMobileNumber().safeParse("919400000024").success, true);
  assert.equal(indianMobileNumber().safeParse("9000000034").success, true);
  assert.equal(indianMobileNumber().safeParse("1234567890").success, false);
  assert.equal(indianMobileNumber().safeParse("5876543210").success, false);
  assert.equal(indianMobileNumber().safeParse("94000000244").success, false);
});

test("optional indian mobile numbers accept blanks and normalize valid values", () => {
  assert.equal(optionalIndianMobileNumber().safeParse("").success, true);
  assert.equal(optionalIndianMobileNumber().safeParse("+91 9000000034").success, true);
});
