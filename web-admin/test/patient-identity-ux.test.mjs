import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("patient registration keeps DOB and age synchronized with estimated helper text", () => {
  const quickRegister = readSource("components/patients/patientQuickRegister.ts");
  const quickDialog = readSource("components/patients/PatientQuickRegisterDialog.tsx");
  const patientForm = readSource("pages/patients/PatientFormPage.tsx");

  assert.ok(quickRegister.includes("calculateAgeFromDob"));
  assert.ok(quickRegister.includes("approximateDobFromAge(ageYears"));
  assert.ok(quickRegister.includes('ageYears === null || ageYears === undefined || ageYears === ""'));
  assert.ok(quickRegister.includes("patientIdentitySummary"));
  assert.ok(quickRegister.includes("useDuplicatePatientLookup"));

  assert.ok(quickDialog.includes("DOB estimated from age"));
  assert.ok(quickDialog.includes("setDobEstimatedFromAge(ageYears.trim() !== \"\")"));
  assert.ok(quickDialog.includes("calculateAgeFromDob(dateOfBirth)"));
  assert.ok(quickDialog.includes("Register New Patient"));
  assert.ok(quickDialog.includes("Existing patient found"));

  assert.ok(patientForm.includes("DOB estimated from age"));
  assert.ok(patientForm.includes("calculateAgeFromDob(dateOfBirth)"));
  assert.ok(patientForm.includes("patientIdentitySummary(loadedPatient)"));
  assert.ok(patientForm.includes("Register New Patient"));
  assert.ok(patientForm.includes("useDuplicatePatientLookup"));
  assert.ok(patientForm.includes("focusFirstInvalidField"));
  assert.ok(patientForm.includes("onKeyDown={(event) => advance(event, firstNameInputRef)}"));
});

test("appointment booking and patient management show human-friendly patient identity and workflow labels", () => {
  const appointments = readSource("pages/appointments/AppointmentsPage.tsx");
  const queue = readSource("pages/appointments/QueuePage.tsx");
  const dayBoard = readSource("pages/appointments/DayBoardPage.tsx");
  const dashboard = readSource("pages/DashboardPage.tsx");
  const patients = readSource("pages/patients/PatientsPage.tsx");
  const patientDetail = readSource("pages/patients/PatientDetailPage.tsx");
  const doctorAvailability = readSource("pages/doctors/DoctorAvailabilityPage.tsx");

  assert.ok(appointments.includes("Search patient (Name / Mobile / Patient No)"));
  assert.ok(appointments.includes("Register New Patient"));
  assert.ok(appointments.includes("patientMobileLine(selectedPatient)"));
  assert.ok(appointments.includes("patientNumberLine(selectedPatient)"));
  assert.ok(appointments.includes("WorkflowStatusBadge status={appointment.status} compact"));

  assert.ok(queue.includes("const nextAction = getNextWorkflowAction({"));
  assert.ok(queue.includes("Patient No:"));
  assert.ok(queue.includes("Appointment Booked"));

  assert.ok(dayBoard.includes("Next: ${getNextWorkflowAction({ status: selectedAppointment.status"));
  assert.ok(dayBoard.includes("Patient No:"));
  assert.ok(dayBoard.includes("Billing Complete"));

  assert.ok(dashboard.includes("label={`Next: ${nextAction.label}`}"));
  assert.ok(dashboard.includes("Visit Completed"));

  assert.ok(patients.includes('label="Patient No"'));
  assert.ok(patients.includes("patientMobileLine(patient)"));
  assert.ok(patients.includes("patientNumberLine(patient)"));
  assert.ok(patients.includes("Registration"));

  assert.ok(patientDetail.includes("patientMobileLine(patient)"));
  assert.ok(patientDetail.includes("patientNumberLine(patient)"));
  assert.ok(patientDetail.includes("Reprocess OCR/AI for this document?"));
  assert.ok(patientDetail.includes("AI reprocessing started."));
  assert.ok(patientDetail.includes("Failed to reprocess AI extraction"));

  assert.ok(doctorAvailability.includes("Break ${row.breakStartTime}–${row.breakEndTime}"));
  assert.ok(doctorAvailability.includes("No availability sessions for the selected scope. Add a session or choose All Doctors to see the full schedule."));
  assert.ok(doctorAvailability.includes("Appointment Booked"));
});
