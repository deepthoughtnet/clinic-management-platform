import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("vaccinations page keeps the operational workspace visible and removes the master entry point", () => {
  const source = readSource("pages/vaccinations/VaccinationsPage.tsx");

  assert.ok(source.includes("function canManageVaccineMasterAccess"));
  assert.ok(source.includes('roles.includes("CLINIC_ADMIN")'));
  assert.ok(source.includes('roles.includes("TENANT_ADMIN")'));
  assert.ok(source.includes('roles.includes("VACCINE_MASTER_MANAGER")'));
  assert.ok(source.includes('roles.includes("PLATFORM_ADMIN")'));
  assert.ok(source.includes("Read-only operational list for vaccine selection. Administration edits live in Vaccine Master."));
  assert.ok(source.includes("Search vaccine"));
  assert.ok(source.includes("Clear filters"));
  assert.ok(source.includes("filteredOperationalVaccines"));
  assert.ok(source.includes("operationalVaccineFilterOptions"));
  assert.ok(source.includes("Active only"));
  assert.ok(source.includes("formatScheduleType(vaccine.scheduleType)"));
  assert.equal(source.includes('Tab value="master" label="Vaccine Master"'), false);
  assert.equal(source.includes('section=master'), false);
  assert.equal(source.includes("Open Vaccine Master"), false);
  assert.equal(source.includes("{canManageMaster ? ("), false);
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
  assert.ok(source.includes("Age-based immunization roadmap for the selected patient."));
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
  assert.ok(source.includes('label={<RequiredLabel text="Given date" required />}'));
  assert.equal(source.includes('id="vaccination-givenDate" label={<RequiredLabel text="Given date" required />} type="date" value={vaccinationForm.givenDate} onChange={(e) => setVaccinationForm((current) => ({ ...current, givenDate: e.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(vaccinationFieldErrors.givenDate)}'), false);
  assert.ok(source.includes("No active vaccines are configured. Ask Clinic Admin to configure or import vaccines in Vaccine Master."));
  assert.ok(source.includes("Load a patient to view the vaccination roadmap."));
  assert.ok(source.includes("No due vaccinations. Upcoming follow-ups will appear here."));
  assert.ok(source.includes("No overdue vaccinations. Missed vaccine follow-ups will appear here."));
});

test("record vaccination selection hydrates master defaults without carrying stale values", () => {
  const source = readSource("pages/vaccinations/VaccinationsPage.tsx");

  assert.ok(source.includes("applyVaccinationSelectionDefaults"));
  assert.ok(source.includes("formatVaccinationSelectionPrice"));
  assert.ok(source.includes('doseNumber: vaccine.doseNumber != null'));
  assert.ok(source.includes('route: vaccine.route ?? recommendation?.route ?? ""'));
  assert.ok(source.includes('administrationSite: vaccine.administrationSite ?? recommendation?.administrationSite ?? ""'));
  assert.ok(source.includes('billItemUnitPrice: formatVaccinationSelectionPrice(vaccine.defaultPrice)'));
  assert.ok(source.includes('nextDueDate: recommendation?.dueDate ?? current.nextDueDate'));
  assert.ok(source.includes('doseNumber: ""'));
  assert.ok(source.includes('route: ""'));
  assert.ok(source.includes('administrationSite: ""'));
  assert.ok(source.includes('billItemUnitPrice: ""'));
  assert.ok(source.includes('stockBatchId: mappedStocks[0]?.id || ""'));
  assert.ok(source.includes('stockBatchId: ""'));
  assert.ok(source.includes('Selected vaccine is inactive.'));
  assert.ok(source.includes('Optional. Populated from recommendations when available.'));
});

test("dedicated Vaccine Master page contains master configuration only", () => {
  const source = readSource("pages/vaccinations/VaccineMasterPage.tsx");

  assert.ok(source.includes("Vaccine Master"));
  assert.ok(source.includes("Download CSV Template"));
  assert.ok(source.includes("Upload CSV"));
  assert.ok(source.includes("Export CSV"));
  assert.ok(source.includes("Vaccine list"));
  assert.ok(source.includes("Create vaccine"));
  assert.ok(source.includes("Edit"));
  assert.ok(source.includes("Deactivate"));
  assert.ok(source.includes('label={<RequiredLabel text="Vaccine name" required />}'));
  assert.equal(source.includes('id="vaccine-vaccineName" label={<RequiredLabel text="Vaccine name" required />} required'), false);
  assert.equal(source.includes('RequiredLabel text="Active"'), false);
  assert.ok(source.includes("Inventory item"));
  assert.ok(source.includes('label={<RequiredLabel text="Inventory item" required={inventoryItemRequired} />}'));
  assert.ok(source.includes("Schedule type"));
  assert.ok(source.includes("Default price"));
  assert.ok(source.includes("ROUTE_OPTIONS"));
  assert.ok(source.includes("SCHEDULE_TYPE_OPTIONS"));
  assert.ok(source.includes("RECOMMENDATION_POLICY_OPTIONS"));
  assert.ok(source.includes("CATCH_UP_POLICY_OPTIONS"));
  assert.ok(source.includes("APPLICABLE_AGE_GROUP_OPTIONS"));
  assert.ok(source.includes('select size="small" fullWidth id="vaccine-route"'));
  assert.ok(source.includes('select size="small" fullWidth id="vaccine-scheduleType"'));
  assert.ok(source.includes('select size="small" fullWidth id="vaccine-recommendationPolicy"'));
  assert.ok(source.includes('select size="small" fullWidth id="vaccine-catchUpPolicy"'));
  assert.ok(source.includes('select size="small" fullWidth id="vaccine-applicableAgeGroup"'));
  assert.ok(source.includes("max 1000 characters"));
  assert.ok(source.includes('label={<RequiredLabel text="Catch-up max age days" required={catchUpMaxAgeRequired} />}'));
  assert.ok(source.includes("Vaccine CSV import result"));
  assert.equal(source.includes("Record Vaccination"), false);
  assert.equal(source.includes("Timeline"), false);
  assert.equal(source.includes("Vaccination history"), false);
  assert.equal(source.includes("Due vaccinations"), false);
  assert.equal(source.includes("Overdue vaccinations"), false);
});

test("vaccine master navigation and route separation are explicit", () => {
  const appSource = readSource("../src/app/App.tsx");
  const source = readSource("../src/layout/nav.ts");
  const moduleRegistrySource = readSource("../src/modules/moduleRegistry.ts");

  assert.ok(source.includes('label: "Vaccine Master"'));
  assert.ok(source.includes('path: "/admin/vaccine-master"'));
  assert.ok(source.includes('rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER"]'));
  assert.ok(appSource.includes("VaccinationLegacyRedirect"));
  assert.ok(appSource.includes('RouteAccessGate><VaccinationLegacyRedirect />'));
  assert.ok(appSource.includes('if (searchParams.get("section") === "master")'));
  assert.ok(appSource.includes('Navigate to="/admin/vaccine-master"'));
  assert.ok(appSource.includes('path="/admin/vaccine-master"'));
  assert.ok(appSource.includes('if (pathname === "/admin/vaccine-master") return "Vaccine Master";'));
  assert.ok(moduleRegistrySource.includes('hasAnyRole(getActiveRoles(auth), "VACCINE_MASTER_MANAGER") ? "/admin/vaccine-master" : null'));
  assert.ok(moduleRegistrySource.includes('return canAccessFeature(auth, "vaccinations") && !hasAnyRole(activeRoles, "VACCINE_MASTER_MANAGER");'));
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
  assert.equal(source.includes("getReceiptPdf"), false);
  assert.ok(source.includes("openVaccinationReceiptPreview(true)"));
  assert.ok(source.includes("setReceiptAutoPrint(true)"));
  assert.ok(source.includes("sendReceipt"));
});

test("external vaccination history keeps provenance separate from billing actions", () => {
  const source = readSource("pages/vaccinations/VaccinationsPage.tsx");

  assert.ok(source.includes("function vaccinationSource(row: PatientVaccination)"));
  assert.ok(source.includes("function vaccinationAdministeredByLabel(row: PatientVaccination)"));
  assert.ok(source.includes('return administeredBy || "—";'));
  assert.ok(source.includes('return administeredBy || "-"'));
  assert.ok(source.includes('const isExternalVaccination = vaccinationSource(row) === "EXTERNAL";'));
  assert.ok(source.includes('const hasLegacyBill = Boolean(row.billId || row.billNumber);'));
  assert.ok(source.includes("External vaccinations cannot be billed."));
  assert.ok(source.includes('historyActionRow.billId ? ('));
  assert.ok(source.includes('View Bill'));
  assert.ok(source.includes('Administered by: ${vaccinationAdministeredByLabel(row)}'));
  assert.ok(source.includes('Recorded by: ${row.recordedByUserName || "-"}'));
  assert.ok(source.includes('vaccinationSource(historyActionRow) === "EXTERNAL"'));
  assert.ok(source.includes('Mark External Verified'));
  assert.ok(source.includes('Open Patient'));
  assert.ok(source.includes('View Vaccination'));
  assert.ok(source.includes('Record / View Adverse Event'));
  assert.ok(source.includes('Edit'));
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
