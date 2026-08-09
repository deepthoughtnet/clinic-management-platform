import test from "node:test";
import assert from "node:assert/strict";
import { decodePatientPortalSessionTokenPayload, isPatientPortalSessionTokenActive } from "../src/pages/patient/patientPortalSessionToken.js";

function encodeBase64Url(value) {
  return Buffer.from(JSON.stringify(value), "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

test("patient portal session token helper decodes payload and respects expiry", () => {
  const now = Date.parse("2026-08-09T10:00:00Z");
  const activeToken = `${encodeBase64Url({ exp: "2026-08-09T11:00:00Z", sub: "patient" })}.signature`;
  const expiredToken = `${encodeBase64Url({ exp: "2026-08-09T09:00:00Z", sub: "patient" })}.signature`;

  assert.deepEqual(decodePatientPortalSessionTokenPayload(activeToken), { exp: "2026-08-09T11:00:00Z", sub: "patient" });
  assert.equal(isPatientPortalSessionTokenActive(activeToken, now), true);
  assert.equal(isPatientPortalSessionTokenActive(expiredToken, now), false);
  assert.equal(isPatientPortalSessionTokenActive("bad-token", now), false);
});
