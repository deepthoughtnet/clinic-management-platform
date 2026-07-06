import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "src", ...relPath.split("/")), "utf8");
}

test("single-doctor auto-select wiring is present across reception-facing pages", () => {
  const hook = readSource("hooks/useAutoSelectSingleDoctor.ts");
  const dashboard = readSource("pages/DashboardPage.tsx");
  const dayBoard = readSource("pages/appointments/DayBoardPage.tsx");
  const appointments = readSource("pages/appointments/AppointmentsPage.tsx");
  const queue = readSource("pages/appointments/QueuePage.tsx");
  const availability = readSource("pages/doctors/DoctorAvailabilityPage.tsx");

  assert.ok(hook.includes("manualOverrideRef.current = false"));
  assert.ok(hook.includes("doctors.length === 1"));
  assert.ok(hook.includes("setDoctorIdProgrammatically(singleDoctorId)"));
  assert.ok(hook.includes("previousTenantIdRef.current = tenantId"));

  for (const source of [dashboard, dayBoard, appointments, queue, availability]) {
    assert.ok(source.includes("useAutoSelectSingleDoctor"), "missing auto-select hook usage");
  }

  assert.ok(dashboard.includes("isActiveDoctorUser"));
  assert.ok(dayBoard.includes('setDoctorUserId(doctorUserIdFromQuery)'));
  assert.ok(appointments.includes("activeDoctorOptions"));
  assert.ok(!appointments.includes("firstDoctor"));
  assert.ok(queue.includes("activeDoctorOptions"));
  assert.ok(availability.includes("activeDoctorOptions"));
});
