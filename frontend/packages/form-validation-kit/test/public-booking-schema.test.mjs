import test from "node:test";
import assert from "node:assert/strict";

import { bookAppointmentSchema } from "../dist/index.js";

test("public booking schema accepts a valid payload", () => {
  const result = bookAppointmentSchema.safeParse({
    doctorId: "doctor-1",
    appointmentDate: "2026-06-18",
    slot: "09:30",
    reason: "Follow-up",
    appointmentType: "SCHEDULED",
  });
  assert.equal(result.success, true);
});

test("public booking schema rejects a missing doctor", () => {
  const result = bookAppointmentSchema.safeParse({
    doctorId: "",
    appointmentDate: "2026-06-18",
    slot: "09:30",
  });
  assert.equal(result.success, false);
});

test("public booking schema rejects a missing slot", () => {
  const result = bookAppointmentSchema.safeParse({
    doctorId: "doctor-1",
    appointmentDate: "2026-06-18",
    slot: "",
  });
  assert.equal(result.success, false);
});

test("public booking schema allows optional fields to be omitted", () => {
  const result = bookAppointmentSchema.safeParse({
    doctorId: "doctor-1",
    appointmentDate: "2026-06-18",
    slot: "09:30",
  });
  assert.equal(result.success, true);
});
