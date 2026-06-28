import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("appointments page filters upcoming rows by time and keeps today's actionable window", () => {
  const source = readSource("pages/appointments/AppointmentsPage.tsx");
  assert.ok(source.includes("shiftClinicNow(clinicNowSnapshot, -60)"));
  assert.ok(source.includes("!isPastDateTime(item.appointmentDate, item.appointmentTime, clinicTimeZone, clinicNowSnapshot)"));
  assert.ok(source.includes("item.appointmentDate === today && !isPastDateTime(item.appointmentDate, item.appointmentTime, clinicTimeZone, shiftClinicNow(clinicNowSnapshot, -60))"));
});
