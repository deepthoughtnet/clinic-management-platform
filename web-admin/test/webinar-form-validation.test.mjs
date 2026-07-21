import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import { buildWebinarPayload, getWebinarDateFieldErrors, validateWebinarDraft } from "../src/products/carepilot/webinars/webinarFormUtils.js";
import { formatCarePilotDateTimeInput, parseCarePilotDateTimeInput } from "../src/products/carepilot/shared/carepilotFormatting.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

const baseDraft = {
  title: "Monthly Webinar",
  description: "Education session",
  webinarType: "EDUCATIONAL",
  campaignId: "",
  webinarUrl: "",
  organizerName: "",
  organizerEmail: "",
  scheduledStartAt: "",
  scheduledEndAt: "",
  timezone: "UTC",
  capacity: "",
  registrationEnabled: true,
  reminderEnabled: true,
  followupEnabled: true,
  tags: "",
  status: "DRAFT",
};

test("valid start clears start error", () => {
  const errors = getWebinarDateFieldErrors({ ...baseDraft, scheduledStartAt: "2026-07-21T10:00", scheduledEndAt: "" });
  assert.equal(errors.scheduledStartAt, undefined);
  assert.equal(errors.scheduledEndAt, "End date/time is required.");
});

test("valid end clears end error", () => {
  const errors = getWebinarDateFieldErrors({ ...baseDraft, scheduledStartAt: "2026-07-21T10:00", scheduledEndAt: "2026-07-21T11:00" });
  assert.equal(errors.scheduledStartAt, undefined);
  assert.equal(errors.scheduledEndAt, undefined);
});

test("end before start fails", () => {
  const errors = validateWebinarDraft({ ...baseDraft, scheduledStartAt: "2026-07-21T11:00", scheduledEndAt: "2026-07-21T10:00" });
  assert.equal(errors.scheduledEndAt, "End date/time must be on or after start date/time.");
});

test("empty values fail", () => {
  const errors = validateWebinarDraft(baseDraft);
  assert.equal(errors.scheduledStartAt, "Start date/time is required.");
  assert.equal(errors.scheduledEndAt, "End date/time is required.");
});

test("empty optional fields are valid once required fields are set", () => {
  const draft = {
    ...baseDraft,
    description: "",
    webinarUrl: "",
    organizerName: "",
    organizerEmail: "",
    campaignId: "",
    capacity: "",
    tags: "",
    scheduledStartAt: "2026-07-21T10:00",
    scheduledEndAt: "2026-07-21T11:00",
  };
  const errors = validateWebinarDraft(draft);
  assert.deepEqual(errors, {});
});

test("populated optional email and URL validate only when filled", () => {
  const invalid = validateWebinarDraft({
    ...baseDraft,
    scheduledStartAt: "2026-07-21T10:00",
    scheduledEndAt: "2026-07-21T11:00",
    webinarUrl: "not-a-url",
    organizerEmail: "bad-email",
  });
  assert.equal(invalid.webinarUrl, "Enter a valid webinar URL.");
  assert.equal(invalid.organizerEmail, "Enter a valid email address.");

  const valid = validateWebinarDraft({
    ...baseDraft,
    scheduledStartAt: "2026-07-21T10:00",
    scheduledEndAt: "2026-07-21T11:00",
    webinarUrl: "https://example.com/webinar",
    organizerEmail: "host@example.com",
  });
  assert.deepEqual(valid, {});
});

test("valid values allow save payload creation", () => {
  const draft = { ...baseDraft, scheduledStartAt: "2026-07-21T10:00", scheduledEndAt: "2026-07-21T11:00", capacity: "120" };
  const errors = validateWebinarDraft(draft);
  assert.deepEqual(errors, {});

  const payload = buildWebinarPayload(draft);
  assert.equal(payload.scheduledStartAt, "2026-07-21T10:00:00.000Z");
  assert.equal(payload.scheduledEndAt, "2026-07-21T11:00:00.000Z");
  assert.equal(payload.capacity, 120);
});

test("timezone-aware edit round-trip preserves Asia/Kolkata local time and UTC save values", () => {
  const createdDraft = {
    ...baseDraft,
    timezone: "Asia/Kolkata",
    scheduledStartAt: "2026-07-21T11:00",
    scheduledEndAt: "2026-07-21T12:00",
  };
  const createdPayload = buildWebinarPayload(createdDraft);
  assert.equal(createdPayload.scheduledStartAt, "2026-07-21T05:30:00.000Z");
  assert.equal(createdPayload.scheduledEndAt, "2026-07-21T06:30:00.000Z");

  const reopenedStart = formatCarePilotDateTimeInput(createdPayload.scheduledStartAt, "Asia/Kolkata");
  const reopenedEnd = formatCarePilotDateTimeInput(createdPayload.scheduledEndAt, "Asia/Kolkata");
  assert.equal(reopenedStart, "2026-07-21T11:00");
  assert.equal(reopenedEnd, "2026-07-21T12:00");

  const unsavedPayload = buildWebinarPayload({
    ...createdDraft,
    scheduledStartAt: reopenedStart,
    scheduledEndAt: reopenedEnd,
  });
  assert.equal(unsavedPayload.scheduledStartAt, createdPayload.scheduledStartAt);
  assert.equal(unsavedPayload.scheduledEndAt, createdPayload.scheduledEndAt);

  const shiftedPayload = buildWebinarPayload({
    ...createdDraft,
    timezone: "UTC",
    scheduledStartAt: reopenedStart,
    scheduledEndAt: reopenedEnd,
  });
  assert.equal(shiftedPayload.scheduledStartAt, "2026-07-21T11:00:00.000Z");
  assert.equal(shiftedPayload.scheduledEndAt, "2026-07-21T12:00:00.000Z");
});

test("parse helper rejects invalid datetime-local values", () => {
  assert.equal(parseCarePilotDateTimeInput("", "Asia/Kolkata"), null);
  assert.equal(parseCarePilotDateTimeInput("not-a-date", "Asia/Kolkata"), null);
});

test("webinar save path blocks API calls until validation passes", () => {
  const source = readSource("products/carepilot/webinars/WebinarsPage.tsx");
  assert.ok(source.includes("const validationErrors = validateWebinarDraft(draft);"));
  assert.ok(source.includes("if (Object.keys(validationErrors).length > 0) {"));
  assert.ok(source.includes("return;"));
  assert.ok(source.includes("await updateCarePilotWebinar(auth.accessToken, auth.tenantId, editing.id, payload);") || source.includes("await createCarePilotWebinar(auth.accessToken, auth.tenantId, payload);"));
});
