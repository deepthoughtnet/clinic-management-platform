import test from "node:test";
import assert from "node:assert/strict";

import { buildRecentDashboardActivityItems } from "../src/pages/patient/patientDashboardActivity.js";

let nextId = 1;

function activity(overrides) {
  return {
    id: `item-${nextId++}`,
    eventType: "NOTIFICATION",
    subject: null,
    message: null,
    status: "UNREAD",
    readAt: null,
    sourceType: null,
    sourceId: null,
    createdAt: "2026-09-06T09:00:00.000Z",
    actionPath: null,
    ...overrides,
  };
}

test("recent activity collapses duplicate appointment notifications into one dashboard item", () => {
  const items = buildRecentDashboardActivityItems(
    [
      activity({
        id: "confirmed",
        eventType: "APPOINTMENT_CONFIRMED",
        sourceType: "APPOINTMENT",
        sourceId: "apt-1",
        createdAt: "2026-09-06T10:00:00.000Z",
        subject: "Appointment confirmed for Dr UAT Automation Doctor",
      }),
      activity({
        id: "booked",
        eventType: "APPOINTMENT_BOOKED",
        sourceType: "APPOINTMENT",
        sourceId: "apt-1",
        createdAt: "2026-09-06T09:55:00.000Z",
        subject: "Appointment booked for Dr UAT Automation Doctor",
      }),
    ],
    3,
  );

  assert.equal(items.length, 1);
  assert.equal(items[0].title, "Appointment booked");
  assert.equal(items[0].detail, "Appointment confirmed for Dr UAT Automation Doctor");
});

test("recent activity keeps distinct transactions and limits dashboard output to three items", () => {
  const items = buildRecentDashboardActivityItems(
    [
      activity({
        id: "lab",
        eventType: "LAB_REPORT_READY",
        sourceType: "LAB_REPORT",
        sourceId: "lab-1",
        createdAt: "2026-09-06T11:00:00.000Z",
        subject: "Lab report ready",
      }),
      activity({
        id: "bill",
        eventType: "BILL_PAID",
        sourceType: "BILL",
        sourceId: "bill-1",
        createdAt: "2026-09-06T10:30:00.000Z",
        subject: "Payment completed",
      }),
      activity({
        id: "rx",
        eventType: "PRESCRIPTION_SHARED",
        sourceType: "PRESCRIPTION",
        sourceId: "rx-1",
        createdAt: "2026-09-06T10:00:00.000Z",
        subject: "Prescription finalized",
      }),
      activity({
        id: "appt",
        eventType: "APPOINTMENT_BOOKED",
        sourceType: "APPOINTMENT",
        sourceId: "apt-2",
        createdAt: "2026-09-06T09:30:00.000Z",
        subject: "Appointment booked",
      }),
    ],
    3,
  );

  assert.equal(items.length, 3);
  assert.deepEqual(
    items.map((item) => item.id),
    ["lab", "bill", "rx"],
  );
});

test("recent activity keeps unsourced notifications distinct", () => {
  const items = buildRecentDashboardActivityItems(
    [
      activity({
        id: "unsourced-1",
        eventType: "BILL_PAID",
        createdAt: "2026-09-06T08:00:00.000Z",
        subject: "Payment completed",
      }),
      activity({
        id: "unsourced-2",
        eventType: "BILL_PAID",
        createdAt: "2026-09-06T07:55:00.000Z",
        subject: "Payment completed",
      }),
    ],
    3,
  );

  assert.equal(items.length, 2);
  assert.deepEqual(
    items.map((item) => item.id),
    ["unsourced-1", "unsourced-2"],
  );
});
