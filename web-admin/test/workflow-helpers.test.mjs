import test from "node:test";
import assert from "node:assert/strict";

import {
  derivePatientJourneyStage,
  formatRelativeBookingTime,
  getAppointmentTokenLabel,
  getNextWorkflowAction,
  getPatientJourneyStageLabel,
  getWorkflowStatusLabel,
} from "../src/components/workflow/workflowHelpers.js";

test("appointment token helper prefers display reference and falls back cleanly", () => {
  assert.equal(getAppointmentTokenLabel({ displayReference: "APT-1024" }), "Token APT-1024");
  assert.equal(getAppointmentTokenLabel({ tokenNumber: 7 }), "Token APT-7");
  assert.equal(getAppointmentTokenLabel({}), "Token not assigned");
});

test("relative booking time uses friendly business phrasing", () => {
  const now = new Date("2026-07-05T12:00:00.000Z");

  assert.equal(formatRelativeBookingTime("2026-07-05T11:59:30.000Z", now), "Booked just now");
  assert.equal(formatRelativeBookingTime("2026-07-05T11:55:00.000Z", now), "Booked 5 min ago");
  assert.equal(formatRelativeBookingTime("2026-07-05T10:00:00.000Z", now), "Booked 2 hr ago");
  assert.equal(formatRelativeBookingTime("2026-07-04T12:00:00.000Z", now), "Booked yesterday");
});

test("workflow labels map backend states to business-friendly text", () => {
  assert.equal(getWorkflowStatusLabel("AWAITING_PAYMENT"), "Awaiting Payment");
  assert.equal(getWorkflowStatusLabel("CHECKED_IN"), "Waiting for Doctor");
  assert.equal(getWorkflowStatusLabel("CONSULTATION_COMPLETED"), "Consultation Completed");
});

test("next workflow action highlights the primary action only", () => {
  assert.equal(getNextWorkflowAction({ status: "BOOKED", paymentStatus: "UNPAID" }).key, "collect-fee");
  assert.equal(getNextWorkflowAction({ status: "BOOKED", paymentStatus: "PAID" }).key, "check-in");
  assert.equal(getNextWorkflowAction({ status: "WAITING", paymentStatus: "PAID" }).key, "start-consultation");
  assert.equal(getNextWorkflowAction({ status: "IN_CONSULTATION", paymentStatus: "PAID" }).key, "continue-consultation");
});

test("patient journey tracker resolves the visible stage", () => {
  assert.equal(getPatientJourneyStageLabel(derivePatientJourneyStage({ status: "BOOKED", paymentStatus: "UNPAID" })), "Payment");
  assert.equal(getPatientJourneyStageLabel(derivePatientJourneyStage({ status: "CHECKED_IN", paymentStatus: "PAID" })), "Check-in");
  assert.equal(getPatientJourneyStageLabel(derivePatientJourneyStage({ status: "IN_CONSULTATION", paymentStatus: "PAID" })), "Consultation");
  assert.equal(getPatientJourneyStageLabel(derivePatientJourneyStage({ status: "COMPLETED", consultationStatus: "COMPLETED", prescriptionStatus: "FINALIZED" })), "Billing Complete");
  assert.equal(getPatientJourneyStageLabel(derivePatientJourneyStage({ status: "COMPLETED", consultationStatus: "COMPLETED", prescriptionStatus: "FINALIZED", paymentStatus: "PAID" })), "Billing Complete");
});
