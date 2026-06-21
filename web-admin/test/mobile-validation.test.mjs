import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("admin mobile forms use the shared normalization helper", () => {
  const patientForm = readSource("pages/patients/PatientFormPage.tsx");
  const appointments = readSource("pages/appointments/AppointmentsPage.tsx");
  const leads = readSource("products/carepilot/leads/LeadsPage.tsx");
  const messaging = readSource("products/carepilot/messaging/MessagingPage.tsx");
  const aiCalls = readSource("products/carepilot/ai-calls/AiCallsPage.tsx");
  const webinars = readSource("products/carepilot/webinars/WebinarsPage.tsx");
  const pharmacyOps = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  const pharmacyPos = readSource("pages/pharmacy/PharmacyPosPage.tsx");

  for (const source of [patientForm, appointments, leads, messaging, aiCalls, webinars, pharmacyOps, pharmacyPos]) {
    assert.ok(source.includes("normalizeIndianMobileInput"));
  }
});

test("admin mobile inputs do not rely on browser pattern validation", () => {
  const patientForm = readSource("pages/patients/PatientFormPage.tsx");
  const appointments = readSource("pages/appointments/AppointmentsPage.tsx");
  const leads = readSource("products/carepilot/leads/LeadsPage.tsx");
  assert.ok(!patientForm.includes('pattern: "[0-9]*"'));
  assert.ok(!appointments.includes('pattern: "[0-9]*"'));
  assert.ok(!leads.includes('pattern: "[0-9]*"'));
});
