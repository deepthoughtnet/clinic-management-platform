import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("vaccinations page hides vaccine master for non-managers and keeps operational workflow visible", () => {
  const source = readSource("pages/vaccinations/VaccinationsPage.tsx");

  assert.ok(source.includes("function canManageVaccineMasterAccess"));
  assert.ok(source.includes('roles.includes("CLINIC_ADMIN")'));
  assert.ok(source.includes('roles.includes("TENANT_ADMIN")'));
  assert.ok(source.includes('roles.includes("VACCINE_MASTER_MANAGER")'));
  assert.ok(source.includes('roles.includes("PLATFORM_ADMIN")'));
  assert.ok(source.includes("const canManageMaster = canManageVaccineMasterAccess(auth);"));
  assert.ok(source.includes("canManageMaster ? ("));
  assert.ok(source.includes("Vaccination recommendations"));
  assert.ok(source.includes("Recommended Today"));
  assert.ok(source.includes("Optional / Risk-Based"));
  assert.ok(source.includes("Select Vaccine"));
  assert.ok(source.includes("route"));
  assert.ok(source.includes("administrationSite"));
  assert.ok(source.includes("Routine childhood vaccines are not shown for this adult patient unless catch-up is configured."));
  assert.ok(source.includes("Download CSV Template"));
  assert.ok(source.includes("Upload CSV"));
  assert.ok(source.includes("Export CSV"));
  assert.ok(source.includes("Record vaccination"));
  assert.ok(source.includes("Due vaccinations"));
  assert.ok(source.includes("Overdue vaccinations"));
  assert.ok(source.includes("Vaccination history"));
  assert.ok(source.includes("Full-width clinical workspace for recorded, imported, and billed vaccinations."));
  assert.ok(source.includes("Vaccination Timeline"));
  assert.ok(source.includes("Age-based roadmap for the selected patient."));
  assert.ok(source.includes("Immunization Passport"));
  assert.ok(source.includes("View Passport"));
  assert.ok(source.includes("Print Passport"));
  assert.ok(source.includes("Download PDF"));
  assert.ok(source.includes("Completed / Applicable"));
  assert.ok(source.includes("Overdue count"));
  assert.ok(source.includes("Open Patient"));
  assert.ok(source.includes("View History"));
  assert.ok(source.includes("Select Vaccine"));
  assert.ok(source.includes("Record Vaccination"));
  assert.ok(source.includes("Billing status"));
  assert.ok(source.includes("Source"));
  assert.ok(source.includes("Load a patient to view the vaccination roadmap."));
  assert.ok(source.includes("No due vaccinations. Upcoming follow-ups will appear here."));
  assert.ok(source.includes("No overdue vaccinations. Missed vaccine follow-ups will appear here."));
});

test("vaccinations page keeps billing and receipt flow inline", () => {
  const source = readSource("pages/vaccinations/VaccinationsPage.tsx");

  assert.ok(source.includes("Billing / Actions"));
  assert.ok(source.includes("Add to Bill"));
  assert.ok(source.includes("Create Bill"));
  assert.ok(source.includes("Collect Payment"));
  assert.ok(source.includes("View Receipt"));
  assert.ok(source.includes("Print Receipt"));
  assert.ok(source.includes("Download Receipt PDF"));
  assert.ok(source.includes("Email Receipt"));
  assert.ok(source.includes("WhatsApp Receipt"));
  assert.ok(source.includes("Receipt Ready"));
  assert.ok(source.includes("MoreVertRoundedIcon"));
  assert.ok(source.includes("ExpandMoreRoundedIcon"));
  assert.ok(source.includes("Details"));
  assert.ok(source.includes("Open Bill"));
  assert.ok(source.includes("billPatientVaccination"));
  assert.ok(source.includes("Collect vaccination payment"));
  assert.ok(source.includes("Payment successful"));
  assert.ok(source.includes("listBillPayments"));
  assert.ok(source.includes("listBillReceipts"));
  assert.ok(source.includes("getReceiptPdf"));
  assert.ok(source.includes("sendReceipt"));
});

test("vaccination CSV helpers include template, preview and export support", () => {
  const source = readSource("pages/vaccinations/vaccinationCsv.ts");

  assert.ok(source.includes("VACCINE_IMPORT_COLUMNS"));
  assert.ok(source.includes('"vaccineName"'));
  assert.ok(source.includes('"description"'));
  assert.ok(source.includes('"manufacturer"'));
  assert.ok(source.includes('"brandName"'));
  assert.ok(source.includes('"vaccineGroup"'));
  assert.ok(source.includes('"doseNumber"'));
  assert.ok(source.includes('"route"'));
  assert.ok(source.includes('"administrationSite"'));
  assert.ok(source.includes('"storageTemperature"'));
  assert.ok(source.includes('"ndcBarcode"'));
  assert.ok(source.includes('"scheduleType"'));
  assert.ok(source.includes('"ageGroup"'));
  assert.ok(source.includes('"minAgeDays"'));
  assert.ok(source.includes('"recommendedAgeDays"'));
  assert.ok(source.includes('"maxAgeDays"'));
  assert.ok(source.includes('"gapDays"'));
  assert.ok(source.includes('"boosterGapDays"'));
  assert.ok(source.includes('"boosterRules"'));
  assert.ok(source.includes('"isRecurring"'));
  assert.ok(source.includes('"recurrenceDays"'));
  assert.ok(source.includes('"recommendationPolicy"'));
  assert.ok(source.includes('"catchUpPolicy"'));
  assert.ok(source.includes('"catchUpMaxAgeDays"'));
  assert.ok(source.includes('"applicableAgeGroup"'));
  assert.ok(source.includes('"clinicalIndications"'));
  assert.ok(source.includes('"defaultPrice"'));
  assert.ok(source.includes('"active"'));
  assert.ok(source.includes("Duplicate vaccine name"));
  assert.ok(source.includes("Active must be true or false"));
  assert.ok(source.includes("Default price must be 0 or greater"));
  assert.ok(source.includes("buildVaccineExportCsv"));
});
