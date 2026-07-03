import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("queue and day board use appointment consultation fee status fields", () => {
  const queueSource = readSource("pages/appointments/QueuePage.tsx");
  const dayBoardSource = readSource("pages/appointments/DayBoardPage.tsx");

  assert.ok(queueSource.includes("appointment.consultationFeeStatus"));
  assert.ok(queueSource.includes("appointment.consultationFeeAmount"));
  assert.ok(queueSource.includes("appointment.consultationFeeDueAmount"));
  assert.ok(queueSource.includes("consultationFeeSummary(appointment, consultationBills)"));

  assert.ok(dayBoardSource.includes("appointment.consultationFeeStatus"));
  assert.ok(dayBoardSource.includes("appointment.consultationFeeAmount"));
  assert.ok(dayBoardSource.includes("appointment.consultationFeeDueAmount"));
  assert.ok(dayBoardSource.includes("consultationFeeSummary(selectedAppointment, consultationBills)"));
});
