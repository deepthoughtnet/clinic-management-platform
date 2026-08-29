import test from "node:test";
import assert from "node:assert/strict";

import {
  computeInactivityWindow,
  createSharedSessionEvent,
  parseSharedActivityTimestamp,
  parseSharedSessionEvent,
  resolveInactivityTimeoutMinutes,
} from "../src/auth/sessionInactivity.js";

test("inactivity timeout defaults to 30 minutes and warns 2 minutes before expiry", () => {
  assert.equal(resolveInactivityTimeoutMinutes(undefined), 30);
  assert.equal(resolveInactivityTimeoutMinutes("45"), 45);
  assert.equal(resolveInactivityTimeoutMinutes("invalid"), 30);

  const base = 1_000_000;
  const timeoutMs = 30 * 60_000;

  const early = computeInactivityWindow(base, base + 27 * 60_000, timeoutMs);
  assert.equal(early.shouldWarn, false);
  assert.equal(early.shouldExpire, false);
  assert.equal(early.warningInMs, 60_000);

  const warning = computeInactivityWindow(base, base + 28 * 60_000, timeoutMs);
  assert.equal(warning.shouldWarn, true);
  assert.equal(warning.shouldExpire, false);
  assert.equal(warning.warningInMs, 0);
  assert.equal(warning.expiresInMs, 2 * 60_000);

  const expired = computeInactivityWindow(base, base + 30 * 60_000, timeoutMs);
  assert.equal(expired.shouldWarn, false);
  assert.equal(expired.shouldExpire, true);
  assert.equal(expired.expiresInMs, 0);
});

test("shared session event helpers parse logout propagation without exposing tokens", () => {
  const payload = createSharedSessionEvent("inactivity");
  const parsed = parseSharedSessionEvent(payload);

  assert.ok(parsed);
  assert.equal(parsed.type, "logout");
  assert.equal(parsed.reason, "inactivity");
  assert.equal(parseSharedSessionEvent("not-json"), null);
  assert.equal(parseSharedActivityTimestamp("12345"), 12345);
  assert.equal(parseSharedActivityTimestamp(""), null);
  assert.equal(parseSharedActivityTimestamp("bad"), null);
});
