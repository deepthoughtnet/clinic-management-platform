import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  buildLeadCreatePayload,
  filterEligibleEngageAssignees,
  mapLeadApiErrorToFieldErrors,
  toLeadDateTimeInputValue,
  validateFollowUpScheduleDraft,
  validateLeadDraft,
} from "../src/products/carepilot/leads/leadFormUtils.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

const baseDraft = {
  firstName: "Asha",
  lastName: "Mehta",
  phone: "9000000001",
  email: "asha.lead.uat@example.com",
  source: "WALK_IN",
  sourceDetails: "UAT walk-in enquiry",
  status: "NEW",
  priority: "MEDIUM",
  notes: "Interested in diabetes consultation",
  tags: "UAT",
  nextFollowUpAt: "",
  campaignId: "",
  assignedToAppUserId: "",
};

const activeUsers = [
  { appUserId: "user-executive", membershipStatus: "ACTIVE", userStatus: "ACTIVE", membershipRole: "ENGAGE_EXECUTIVE" },
  { appUserId: "user-manager", membershipStatus: "ACTIVE", userStatus: "ACTIVE", membershipRole: "ENGAGE_MANAGER" },
  { appUserId: "user-clinic-admin", membershipStatus: "ACTIVE", userStatus: "ACTIVE", membershipRole: "CLINIC_ADMIN" },
  { appUserId: "user-inactive", membershipStatus: "INACTIVE", userStatus: "INACTIVE", membershipRole: "ENGAGE_EXECUTIVE" },
  { appUserId: "user-pharmacy", membershipStatus: "ACTIVE", userStatus: "ACTIVE", membershipRole: "PHARMACIST" },
];

test("lead form allows omission of next follow-up", () => {
  const validation = validateLeadDraft(baseDraft, activeUsers, "Asia/Kolkata");
  assert.deepEqual(validation.fieldErrors, {});

  const payload = buildLeadCreatePayload(baseDraft, validation.normalizedPhone);
  assert.equal(payload.nextFollowUpAt, null);
});

test("lead form accepts datetime-local input and round-trips through ISO", () => {
  const draft = { ...baseDraft, nextFollowUpAt: "2026-09-02T11:00" };
  const validation = validateLeadDraft(draft, activeUsers, "Asia/Kolkata");
  assert.deepEqual(validation.fieldErrors, {});

  const payload = buildLeadCreatePayload(draft, validation.normalizedPhone);
  assert.match(payload.nextFollowUpAt || "", /^\d{4}-\d{2}-\d{2}T/);
  assert.equal(toLeadDateTimeInputValue(payload.nextFollowUpAt), "2026-09-02T11:00");
});

test("lead form rejects malformed follow-up input with a field error", () => {
  const validation = validateLeadDraft({ ...baseDraft, nextFollowUpAt: "02/08/2026 11:00 AM" }, activeUsers, "Asia/Kolkata");
  assert.equal(validation.fieldErrors.nextFollowUpAt, "Select a valid follow-up date and time.");
});

test("lead form rejects past follow-up date time", () => {
  const validation = validateLeadDraft({ ...baseDraft, nextFollowUpAt: "2026-08-28T18:26" }, activeUsers, "Asia/Kolkata");
  assert.equal(validation.fieldErrors.nextFollowUpAt, "Follow-up date and time must be in the future.");
});

test("lead form validates phone and email with field-specific messages", () => {
  const validation = validateLeadDraft({ ...baseDraft, phone: "12345", email: "bad-email" }, activeUsers, "Asia/Kolkata");
  assert.equal(validation.fieldErrors.phone, "Enter a valid 10-digit mobile number.");
  assert.equal(validation.fieldErrors.email, "Enter a valid email address.");
});

test("lead form accepts optional campaign, assignee and tags values when empty", () => {
  const validation = validateLeadDraft({ ...baseDraft, campaignId: "", assignedToAppUserId: "", tags: "" }, activeUsers, "Asia/Kolkata");
  assert.deepEqual(validation.fieldErrors, {});
});

test("lead form rejects inactive or ineligible assignee selections", () => {
  const inactiveValidation = validateLeadDraft({ ...baseDraft, assignedToAppUserId: "user-inactive" }, activeUsers, "Asia/Kolkata");
  const pharmacyValidation = validateLeadDraft({ ...baseDraft, assignedToAppUserId: "user-pharmacy" }, activeUsers, "Asia/Kolkata");
  assert.equal(inactiveValidation.fieldErrors.assignedToAppUserId, "Select an active Engage user.");
  assert.equal(pharmacyValidation.fieldErrors.assignedToAppUserId, "Select an active Engage user.");
});

test("lead form filters assignee options to eligible active Engage users", () => {
  const filtered = filterEligibleEngageAssignees(activeUsers);
  assert.deepEqual(filtered.map((user) => user.appUserId), ["user-executive", "user-manager", "user-clinic-admin"]);
});

test("lead follow-up scheduling rejects past date time and accepts future date time", () => {
  const past = validateFollowUpScheduleDraft({ date: "2026-08-28", time: "18:26" }, "Asia/Kolkata");
  const future = validateFollowUpScheduleDraft({ date: "2026-09-01", time: "18:26" }, "Asia/Kolkata");
  assert.equal(past.fieldErrors.date, "Follow-up date and time must be in the future.");
  assert.equal(past.fieldErrors.time, "Follow-up date and time must be in the future.");
  assert.equal(future.nextFollowUpAt, "2026-09-01T12:56:00.000Z");
  assert.deepEqual(future.fieldErrors, {});
});

test("backend validation messages map to friendly field errors", () => {
  const mapped = mapLeadApiErrorToFieldErrors("phone: phone is required, nextFollowUpAt: invalid value, email: invalid");
  assert.equal(mapped.phone, "Enter a valid 10-digit mobile number.");
  assert.equal(mapped.nextFollowUpAt, "Select a valid follow-up date and time.");
  assert.equal(mapped.email, "Enter a valid email address.");
});

test("lead form submit path prevents duplicate clicks and shows inline errors", () => {
  const source = readSource("products/carepilot/leads/LeadsPage.tsx");
  assert.ok(source.includes("saving || saveInFlightRef.current"));
  assert.ok(source.includes("setFieldErrors(validation.fieldErrors)"));
  assert.ok(source.includes("mapLeadApiErrorToFieldErrors(message)"));
  assert.ok(source.includes("canPersistLeadForm ? ("));
  assert.ok(source.includes("error={Boolean(fieldErrors.nextFollowUpAt)}"));
  assert.ok(source.includes("Follow-up date and time must be in the future."));
  assert.ok(source.includes("Optional. Assign this lead to an active Engage user."));
  assert.ok(source.includes("clearSaveState()"));
  assert.ok(source.includes("validateFollowUpScheduleDraft(followUpDraft, clinicTimeZone)"));
});
