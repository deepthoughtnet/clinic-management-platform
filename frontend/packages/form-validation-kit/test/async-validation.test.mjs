import assert from "node:assert/strict";

import { createDebouncedValidator, createUniqueEmailValidator, createUniquePhoneValidator } from "../dist/index.js";

const originalSetTimeout = globalThis.setTimeout;
const originalClearTimeout = globalThis.clearTimeout;

try {
  globalThis.setTimeout = ((handler, _delay, ...args) => {
    handler(...args);
    return 0;
  });
  globalThis.clearTimeout = () => {};

  const duplicateValidator = createDebouncedValidator(async () => false, { delayMs: 1 });
  assert.equal((await duplicateValidator("taken")).valid, false);

  const emailValidator = createUniqueEmailValidator(async (value) => value !== "taken@example.com", { delayMs: 1 });
  assert.equal((await emailValidator("Taken@Example.com")).valid, false);

  const phoneValidator = createUniquePhoneValidator(async (value) => value !== "9876543210", { delayMs: 1 });
  assert.equal((await phoneValidator("98765-43210")).valid, false);
} finally {
  globalThis.setTimeout = originalSetTimeout;
  globalThis.clearTimeout = originalClearTimeout;
}
