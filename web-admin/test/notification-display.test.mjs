import test from "node:test";
import assert from "node:assert/strict";

import { formatNotificationSourceLabel, formatNotificationTargetLabel } from "../src/pages/notifications/notificationDisplay.js";

test("notification target labels resolve patient recipient ids to human readable text", () => {
  const patientId = "11111111-1111-4111-8111-111111111111";
  const label = formatNotificationTargetLabel(
    {
      id: "n1",
      tenantId: "t1",
      patientId: null,
      eventType: "APPOINTMENT_BOOKED",
      channel: "IN_APP",
      recipient: `patient:${patientId}`,
      subject: "Appointment booked",
      message: "Your appointment has been booked successfully.",
      status: "SENT",
      failureReason: null,
      sourceType: "APPOINTMENT",
      sourceId: null,
      deduplicationKey: null,
      outboxEventId: null,
      attemptCount: 1,
      sentAt: null,
      readAt: null,
      createdAt: "2026-06-27T00:00:00.000Z",
      updatedAt: "2026-06-27T00:00:00.000Z",
    },
    [
      { id: patientId, firstName: "Asha", lastName: "Rao", patientNumber: "PAT-1" },
    ],
  );

  assert.equal(label, "Asha Rao • PAT-1");
  assert.equal(label.includes(patientId), false);
});

test("notification source labels stay readable", () => {
  assert.equal(
    formatNotificationSourceLabel({ sourceType: "APPOINTMENT", subject: "Appointment booked 2026-06-28", eventType: "APPOINTMENT_BOOKED" }),
    "Appointment • Appointment booked 2026-06-28",
  );
});
