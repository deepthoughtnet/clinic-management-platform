import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  buildLeadCreatePayload,
  mapLeadApiErrorToFieldErrors,
  toLeadDateTimeInputValue,
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
  { appUserId: "user-active", membershipStatus: "ACTIVE" },
  { appUserId: "user-inactive", membershipStatus: "INACTIVE" },
];

test("lead form allows omission of next follow-up", () => {
  const validation = validateLeadDraft(baseDraft, activeUsers);
  assert.deepEqual(validation.fieldErrors, {});

  const payload = buildLeadCreatePayload(baseDraft, validation.normalizedPhone);
  assert.equal(payload.nextFollowUpAt, null);
});

test("lead form accepts datetime-local input and round-trips through ISO", () => {
  const draft = { ...baseDraft, nextFollowUpAt: "2026-08-02T11:00" };
  const validation = validateLeadDraft(draft, activeUsers);
  assert.deepEqual(validation.fieldErrors, {});

  const payload = buildLeadCreatePayload(draft, validation.normalizedPhone);
  assert.match(payload.nextFollowUpAt || "", /^\d{4}-\d{2}-\d{2}T/);
  assert.equal(toLeadDateTimeInputValue(payload.nextFollowUpAt), "2026-08-02T11:00");
});

test("lead form rejects malformed follow-up input with a field error", () => {
  const validation = validateLeadDraft({ ...baseDraft, nextFollowUpAt: "02/08/2026 11:00 AM" }, activeUsers);
  assert.equal(validation.fieldErrors.nextFollowUpAt, "Select a valid follow-up date and time.");
});

test("lead form validates phone and email with field-specific messages", () => {
  const validation = validateLeadDraft({ ...baseDraft, phone: "12345", email: "bad-email" }, activeUsers);
  assert.equal(validation.fieldErrors.phone, "Enter a valid 10-digit mobile number.");
  assert.equal(validation.fieldErrors.email, "Enter a valid email address.");
});

test("lead form accepts optional campaign, assignee and tags values when empty", () => {
  const validation = validateLeadDraft({ ...baseDraft, campaignId: "", assignedToAppUserId: "", tags: "" }, activeUsers);
  assert.deepEqual(validation.fieldErrors, {});
});

test("lead form rejects inactive assignee selections", () => {
  const validation = validateLeadDraft({ ...baseDraft, assignedToAppUserId: "user-inactive" }, activeUsers);
  assert.equal(validation.fieldErrors.assignedToAppUserId, "Select an active assignee.");
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
  assert.ok(source.includes("disabled={saving}"));
  assert.ok(source.includes("error={Boolean(fieldErrors.nextFollowUpAt)}"));
  assert.ok(source.includes("clearSaveState()"));
});
