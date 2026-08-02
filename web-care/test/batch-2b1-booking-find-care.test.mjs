import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("authenticated find care reuses the patient session for slot loading and shows capability grouping", () => {
  const pages = read("pages/patient/PatientPortalPages.tsx");
  const api = read("api/patientPortal.ts");
  const styles = read("styles.css");

  assert.ok(pages.includes('const bookingSession = session && session.patientSessionToken ? session : null;'));
  assert.ok(pages.includes("loadPatientPortalDoctorSlots("));
  assert.ok(pages.includes("bookingSession"));
  assert.ok(pages.includes("Search doctor, clinic, or area"));
  assert.ok(pages.includes("Book online with Jeevanam"));
  assert.ok(pages.includes("Call clinic to book"));
  assert.ok(pages.includes("patient-subcard-groups"));
  assert.ok(pages.includes("booking-mode-summary"));
  assert.ok(pages.includes("booking-date-strip"));
  assert.ok(pages.includes('currentIsoDateInTimeZone("Asia/Kolkata")'));
  assert.ok(pages.includes("Your patient session has expired. Sign in again to continue booking."));
  assert.ok(api.includes("export async function loadPatientPortalDoctorSlots"));
  assert.ok(api.includes("session: PatientPortalSession"));
  assert.ok(styles.includes(".booking-capability-legend"));
  assert.ok(styles.includes(".booking-mode-chip--is-online"));
  assert.ok(styles.includes(".booking-date-pill"));
});
