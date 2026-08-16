import test from "node:test";
import assert from "node:assert/strict";

import {
  isValidPatientAccessCodeInput,
  isValidPatientAccessRequestClinicSlug,
  isValidPatientAccessRequestEmail,
  isValidPatientAccessRequestFullName,
  isValidPatientAccessRequestMobile,
  isValidPatientAccessRequestNote,
  sanitizePatientAccessErrorMessage,
} from "../src/pages/patient/patientAccessValidation.js";

test("patient access request validation accepts expected values", () => {
  assert.equal(isValidPatientAccessRequestFullName("Sushant Singh"), true);
  assert.equal(isValidPatientAccessRequestMobile("9876543210"), true);
  assert.equal(isValidPatientAccessRequestEmail("patient@example.com"), true);
  assert.equal(isValidPatientAccessRequestEmail(""), true);
  assert.equal(isValidPatientAccessRequestClinicSlug("demo-clinic"), true);
  assert.equal(isValidPatientAccessRequestNote("Preview access"), true);
  assert.equal(isValidPatientAccessCodeInput("28322812"), true);
});

test("patient access request validation rejects invalid values", () => {
  assert.equal(isValidPatientAccessRequestFullName(" "), false);
  assert.equal(isValidPatientAccessRequestMobile("123@@@"), false);
  assert.equal(isValidPatientAccessRequestEmail("invalid-email"), false);
  assert.equal(isValidPatientAccessRequestClinicSlug("123@@@"), false);
  assert.equal(isValidPatientAccessRequestNote("x".repeat(501)), false);
  assert.equal(isValidPatientAccessCodeInput("1234"), false);
});

test("patient access error mapping preserves business conflicts and maps validation errors", () => {
  assert.equal(
    sanitizePatientAccessErrorMessage("An access request for this mobile number is already pending."),
    "An access request for this mobile number is already pending.",
  );
  assert.equal(
    sanitizePatientAccessErrorMessage("Access has already been approved for this account."),
    "Access has already been approved for this account.",
  );
  assert.equal(
    sanitizePatientAccessErrorMessage("mobile: must match ^[0-9]{10}$"),
    "Enter a valid 10-digit Indian mobile number.",
  );
  assert.equal(
    sanitizePatientAccessErrorMessage("accessCode: must match ^[0-9]{8}$"),
    "Enter the valid temporary access code.",
  );
  assert.equal(
    sanitizePatientAccessErrorMessage("clinicSlug: must match ^[A-Za-z0-9][A-Za-z0-9-]{0,59}$"),
    "Please select or enter a valid clinic or hospital slug.",
  );
});
