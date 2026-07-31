import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("Phase 4 publishes doctor, clinic, hospital, and speciality discovery pages", () => {
  const app = read("src/App.tsx");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(app.includes("<PublicHomePage />"));
  assert.ok(app.includes("<PublicDoctorsPage />"));
  assert.ok(app.includes("<PublicDoctorDetailPage />"));
  assert.ok(app.includes("<PublicClinicsPage />"));
  assert.ok(app.includes("<PublicClinicDetailPage />"));
  assert.ok(app.includes("<PublicHospitalsPage />"));
  assert.ok(app.includes("<PublicHospitalDetailPage />"));
  assert.ok(app.includes("<PublicSpecialitiesPage />"));
  assert.ok(app.includes("<PublicSpecialityDetailPage />"));
  assert.ok(pages.includes("Top doctors near you"));
  assert.ok(pages.includes("Clinics near you"));
  assert.ok(pages.includes("Hospitals near you"));
  assert.ok(pages.includes("Popular ways to explore care"));
  assert.ok(pages.includes("Doctors"));
  assert.ok(pages.includes("Clinics"));
  assert.ok(pages.includes("Hospitals"));
  assert.ok(pages.includes("Specialities"));
  assert.ok(pages.includes("home-category-strip"));
  assert.ok(pages.includes("Health Packages"));
  assert.ok(pages.includes("Tests & Diagnostics"));
  assert.ok(pages.includes("homepage-hospital-grid"));
});

test("published profiles reuse public catalog APIs without backend or auth changes", () => {
  const api = read("src/api/publicCatalog.ts");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");

  assert.ok(pages.includes('"/api/public/doctors"'));
  assert.ok(pages.includes("`/api/public/doctors/${doctorSlug}`"));
  assert.ok(pages.includes('"/api/public/clinics"'));
  assert.ok(pages.includes("`/api/public/clinics/${clinicSlug}`"));
  assert.ok(pages.includes('"/api/public/hospitals"'));
  assert.ok(pages.includes("`/api/public/hospitals/${hospitalSlug}`"));
  assert.ok(pages.includes('"/api/public/specialities"'));
  assert.ok(pages.includes("`/api/public/specialities/${specialitySlug}`"));
  assert.ok(pages.includes("buildDoctorProfile"));
  assert.ok(pages.includes("buildClinicProfile"));
  assert.ok(pages.includes("buildHospitalProfile"));
  assert.ok(pages.includes("<PublicProviderProfile {...profile} />"));
  assert.ok(profile.includes("export function PublicProviderProfile"));
  assert.ok(api.includes("VITE_API_BASE_URL"));
  assert.ok(!api.includes("Authorization"));
  assert.ok(!api.includes("X-Patient-Session"));
  assert.ok(!api.includes("keycloak"));
});

test("doctor directory summary cards use published values instead of placeholder copy", () => {
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(components.includes("doctor.subtitle?.trim() || doctor.speciality || null"));
  assert.ok(components.includes("doctor.yearsOfExperience != null ?"));
  assert.ok(components.includes("doctor.languages.length ?"));
  assert.ok(components.includes("patientFacingLocationParts(doctor.clinicDisplayName, doctor.area, doctor.city).join(\" · \")"));
  assert.ok(components.includes('value.toLowerCase() !== "primary"'));
  assert.ok(!components.includes("Fee available on profile"));
  assert.ok(components.includes("formatDistanceKm"));
});

test("published discovery has no patient session, portal, or dashboard dependency", () => {
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

test("Discover exposes canonical /discover routes while Care keeps legacy redirects", () => {
  const app = read("src/App.tsx");
  const webCareApp = fs.readFileSync(path.join(root, "../web-care/src/App.tsx"), "utf8");

  assert.ok(app.includes('path="/doctors" element={<LegacyRedirect to={DISCOVER_ROUTES.doctors.path} />}'));
  assert.ok(app.includes('path="/clinics" element={<LegacyRedirect to={DISCOVER_ROUTES.clinics.path} />}'));
  assert.ok(app.includes('path="/hospitals" element={<LegacyRedirect to={DISCOVER_ROUTES.hospitals.path} />}'));
  assert.ok(app.includes('path="/specialities" element={<LegacyRedirect to={DISCOVER_ROUTES.specialities.path} />}'));
  assert.ok(webCareApp.includes("LegacyDiscoverRedirectPage"));
  assert.ok(webCareApp.includes('path="/doctors" element={<LegacyDiscoverRedirectPage />}'));
  assert.ok(webCareApp.includes('path="/clinics" element={<LegacyDiscoverRedirectPage />}'));
  assert.ok(webCareApp.includes('path="/specialities" element={<LegacyDiscoverRedirectPage />}'));
  assert.ok(webCareApp.includes("PatientLoginPage"));
});
