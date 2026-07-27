import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("Phase 2 migrates real anonymous discovery pages into web-discover", () => {
  const app = read("src/App.tsx");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(app.includes("<PublicHomePage />"));
  assert.ok(app.includes("<PublicDoctorsPage />"));
  assert.ok(app.includes("<PublicDoctorDetailPage />"));
  assert.ok(app.includes("<PublicClinicsPage />"));
  assert.ok(app.includes("<PublicClinicDetailPage />"));
  assert.ok(app.includes("<PublicSpecialitiesPage />"));
  assert.ok(app.includes("<PublicSpecialityDetailPage />"));
  assert.ok(pages.includes("Featured providers"));
  assert.ok(pages.includes("Browse public doctor profiles."));
  assert.ok(pages.includes("Browse public clinic profiles."));
  assert.ok(pages.includes("Explore specialities across public providers."));
});

test("Phase 2 reuses existing public catalog APIs without backend or auth changes", () => {
  const api = read("src/api/publicCatalog.ts");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(pages.includes('"/api/public/search"'));
  assert.ok(pages.includes('"/api/public/doctors"'));
  assert.ok(pages.includes("`/api/public/doctors/${doctorSlug}`"));
  assert.ok(pages.includes('"/api/public/clinics"'));
  assert.ok(pages.includes("`/api/public/clinics/${clinicSlug}`"));
  assert.ok(pages.includes('"/api/public/specialities"'));
  assert.ok(pages.includes("`/api/public/specialities/${specialitySlug}`"));
  assert.ok(api.includes("VITE_API_BASE_URL"));
  assert.ok(!api.includes("Authorization"));
  assert.ok(!api.includes("X-Patient-Session"));
  assert.ok(!api.includes("keycloak"));
});

test("migrated discovery has no patient session, portal, or dashboard dependency", () => {
  const migrated = [
    read("src/api/publicCatalog.ts"),
    read("src/components/DiscoveryComponents.tsx"),
    read("src/context/PublicLocationContext.tsx"),
    read("src/pages/discovery/PublicDiscoveryPages.tsx"),
    read("src/utils/publicDiscovery.ts"),
  ].join("\n");

  assert.ok(!migrated.includes("PatientPortalSession"));
  assert.ok(!migrated.includes("patientPortal"));
  assert.ok(!migrated.includes("PatientDashboard"));
  assert.ok(!migrated.includes("/api/patient-portal"));
  assert.ok(!migrated.includes("X-Patient-Session"));
  assert.ok(!migrated.includes("localStorage.getItem(PATIENT"));
});

test("public booking entry is an external Care handoff and does not implement booking completion", () => {
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(components.includes("careBookingUrl"));
  assert.ok(components.includes('url.pathname = "/patient/book-appointment"'));
  assert.ok(components.includes("discoverConfig.careAppUrl"));
  assert.ok(!components.includes("POST"));
  assert.ok(!components.includes("/api/patient-portal/appointments"));
});

test("Phase 2 keeps web-public sources intact for temporary duplicated discovery", () => {
  const webPublicApp = fs.readFileSync(path.join(root, "../web-public/src/App.tsx"), "utf8");

  assert.ok(webPublicApp.includes("PublicHomePage"));
  assert.ok(webPublicApp.includes("PublicDoctorsPage"));
  assert.ok(webPublicApp.includes("PublicClinicsPage"));
  assert.ok(webPublicApp.includes("PatientLoginPage"));
});
