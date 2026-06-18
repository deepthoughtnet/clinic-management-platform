import test from "node:test";
import assert from "node:assert/strict";

import { appointmentCreateSchema, appointmentRescheduleSchema } from "../dist/index.js";

test("appointment schema accepts a valid scheduled payload", () => {
  const result = appointmentCreateSchema.safeParse({
    patientId: "patient-1",
    doctorUserId: "doctor-1",
    appointmentDate: "2026-06-18",
    appointmentTime: "09:30",
    reason: "Follow-up",
    type: "SCHEDULED",
    priority: "NORMAL",
  });
  assert.equal(result.success, true);
});

test("appointment schema rejects a missing patient", () => {
  const result = appointmentCreateSchema.safeParse({
    patientId: "",
    doctorUserId: "doctor-1",
    appointmentDate: "2026-06-18",
    appointmentTime: "09:30",
    type: "SCHEDULED",
  });
  assert.equal(result.success, false);
});

test("appointment schema rejects a missing appointment time for scheduled visits", () => {
  const result = appointmentCreateSchema.safeParse({
    patientId: "patient-1",
    doctorUserId: "doctor-1",
    appointmentDate: "2026-06-18",
    type: "SCHEDULED",
  });
  assert.equal(result.success, false);
});

test("appointment schema allows walk-in without an appointment time", () => {
  const result = appointmentCreateSchema.safeParse({
    patientId: "patient-1",
    doctorUserId: "doctor-1",
    appointmentDate: "2026-06-18",
    type: "WALK_IN",
  });
  assert.equal(result.success, true);
});

test("appointment reschedule schema accepts a valid payload", () => {
  const result = appointmentRescheduleSchema.safeParse({
    appointmentId: "appointment-1",
    appointmentDate: "2026-06-18",
    appointmentTime: "10:00",
    doctorUserId: "doctor-1",
  });
  assert.equal(result.success, true);
});
