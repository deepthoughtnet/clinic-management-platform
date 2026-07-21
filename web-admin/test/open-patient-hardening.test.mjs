import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("open patient is guarded by shared tenant-aware entitlement checks", () => {
  const helper = readSource("products/carepilot/shared/patientNavigation.ts");
  const leads = readSource("products/carepilot/leads/LeadsPage.tsx");
  const reminders = readSource("products/carepilot/reminders/RemindersPage.tsx");
  const engagement = readSource("products/carepilot/engagement/PatientEngagementPage.tsx");

  assert.ok(helper.includes('hasPermission("patient.read")'));
  assert.ok(helper.includes('hasTenantModule(auth, "APPOINTMENTS")'));
  assert.ok(helper.includes('hasTenantModule(auth, "CONSULTATION")'));
  assert.ok(helper.includes('isRouteAccessibleForAuth(auth, "/patients/:id")'));
  assert.ok(helper.includes('canViewLinkedPatientConsultationHistory'));

  assert.ok(leads.includes('canOpenLinkedPatient(auth)'));
  assert.ok(leads.includes('renderConvertedPatientActions'));
  assert.ok(leads.includes('renderLeadActions(lead)'));
  assert.ok(!leads.includes('lead.convertedPatientId && canOpenPatient ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null'));
  assert.ok(leads.includes('View Consultation History'));
  assert.ok(reminders.includes('canOpenLinkedPatient(auth)'));
  assert.ok(reminders.includes('row.patientId && canOpenPatient'));
  assert.ok(engagement.includes('canOpenLinkedPatient(auth)'));
  assert.ok(engagement.includes('row.patientId && canOpenPatient'));
});
