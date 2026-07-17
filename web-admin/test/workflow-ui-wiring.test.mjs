import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("queue, day board, billing, dashboard and consultation reuse shared workflow UX", () => {
  const queue = readSource("pages/appointments/QueuePage.tsx");
  const dayBoard = readSource("pages/appointments/DayBoardPage.tsx");
  const billing = readSource("pages/billing/BillsPage.tsx");
  const dashboard = readSource("pages/DashboardPage.tsx");
  const consultation = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const appointments = readSource("pages/appointments/AppointmentsPage.tsx");

  assert.ok(queue.includes("PatientJourneyTracker"));
  assert.ok(queue.includes("formatRelativeBookingTime"));
  assert.ok(queue.includes("getNextWorkflowAction"));
  assert.ok(queue.includes("Appointment Booked"));

  assert.ok(dayBoard.includes("PatientJourneyTracker"));
  assert.ok(dayBoard.includes("WorkflowStatusBadge"));
  assert.ok(dayBoard.includes("Start consultation"));
  assert.ok(dayBoard.includes("Billing Complete"));

  assert.ok(billing.includes("PatientJourneyTracker"));
  assert.ok(billing.includes("WorkflowStatusBadge"));

  assert.ok(dashboard.includes("PatientJourneyTracker"));
  assert.ok(dashboard.includes("WorkflowStatusBadge"));
  assert.ok(dashboard.includes("Visit Completed"));

  assert.ok(appointments.includes("AppointmentTokenChip"));

  assert.equal(consultation.includes("PatientJourneyTracker"), false);
  assert.ok(consultation.includes("WorkflowStatusBadge"));
  assert.ok(consultation.includes('label="AI Assist"'));
  assert.ok(consultation.includes("AI Clinical Assistance Unavailable"));
  assert.ok(consultation.includes("AI-assisted patient insights are currently disabled by your organization."));
  assert.ok(consultation.includes("You can continue documenting the consultation, diagnoses, prescriptions, investigations and laboratory orders normally."));
  assert.ok(consultation.includes("role=\"status\""));
  assert.ok(consultation.includes("aiUnavailableCard"));
});
