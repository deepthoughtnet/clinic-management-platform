import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("homepage prioritizes search with URL-driven query and location parameters", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(page.includes('id="find-care"'));
  assert.ok(page.includes('aria-label="Discover care search"'));
  assert.ok(page.includes("Search doctors, specialities, clinics or treatments"));
  assert.ok(page.includes("buildDirectorySearchParams"));
  assert.ok(page.includes("params.set(\"q\""));
  assert.ok(page.includes("params.set(\"city\""));
  assert.ok(page.includes("navigate(`/?${params.toString()}`)"));
  assert.ok(page.includes("event.preventDefault()"));
});

test("homepage includes production discovery sections without fabricated metrics", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  for (const text of [
    "Provider Information",
    "Easy Booking",
    "Your Care Workspace",
    "Clinics & Hospitals",
    "Browse by speciality",
    "Doctors you can explore",
    "Clinics near you",
    "Explore healthcare services",
    "A simpler way to find and manage care",
    "Grow your practice with Jeevanam",
    "One connected healthcare experience",
  ]) {
    assert.ok(page.includes(text), `${text} should render on the homepage`);
  }

  assert.ok(!page.includes("12,000"));
  assert.ok(!page.includes("1.2 million"));
  assert.ok(!page.includes("top-rated"));
});

test("homepage cards route to profiles and Care booking without exposing raw IDs as labels", () => {
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(components.includes('to={`/doctors/${doctor.doctorSlug}`'));
  assert.ok(components.includes('to={`/clinics/${clinic.clinicSlug}`'));
  assert.ok(components.includes("Book appointment"));
  assert.ok(components.includes("careBookingUrl"));
  assert.ok(!components.includes("{doctor.publicDoctorId}</"));
});

test("homepage has loading, error, empty, and responsive accessibility states", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");
  const app = read("src/App.tsx");
  const styles = read("src/styles.css");

  assert.ok(components.includes("InlineDirectoryState"));
  assert.ok(components.includes("DiscoverEmptyState"));
  assert.ok(components.includes("skeleton-grid"));
  assert.ok(components.includes("We could not load this directory right now."));
  assert.ok(page.includes("We could not load doctors right now."));
  assert.ok(page.includes("Specialities are being prepared"));
  assert.ok(app.includes('aria-expanded={menuOpen}'));
  assert.ok(app.includes('aria-label="Discover navigation"'));
  assert.ok(styles.includes("@media (max-width: 760px)"));
  assert.ok(styles.includes("@media (prefers-reduced-motion: reduce)"));
});

test("Discover UI does not reintroduce legacy web-public wording or patient-private ownership", () => {
  const discoverUi = [
    read("src/App.tsx"),
    read("src/pages/discovery/PublicDiscoveryPages.tsx"),
    read("src/components/DiscoveryComponents.tsx"),
  ].join("\n");

  assert.ok(!discoverUi.includes("web-public"));
  assert.ok(!discoverUi.includes("PatientDashboard"));
  assert.ok(!discoverUi.includes("X-Patient-Session"));
  assert.ok(!discoverUi.includes("/api/patient-portal"));
  assert.ok(!discoverUi.includes("implementation phase"));
  assert.ok(!discoverUi.includes("public-safe"));
  assert.ok(!discoverUi.includes("route is established"));
});

test("visual refinement removes architecture panel and duplicate final CTA", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(!page.includes("Search nearby care"));
  assert.ok(!page.includes("Doctor profiles"));
  assert.ok(!page.includes("Clinic pages"));
  assert.ok(!page.includes("Jeevanam Care handoff"));
  assert.ok(!page.includes("Find care or bring your practice online."));
  assert.ok(!styles.includes("final-cta"));
  assert.ok(page.includes("Dr. Anjali Sharma"));
  assert.ok(page.includes("Sunrise Family Clinic"));
  assert.ok(page.includes('className="visually-hidden">Search'));
  assert.ok(page.includes('className="visually-hidden">Location'));
  assert.ok(page.includes("hero-profile-card"));
  assert.ok(page.includes("hero-clinic-card"));
  assert.ok(page.includes('<article className="service-card service-card-disabled"'));
  assert.ok(!components.includes("clinic-card-pattern"));
  assert.ok(styles.includes("grid-template-columns: minmax(0, 3fr) minmax(150px, 1fr) minmax(132px, 1fr);"));
});

test("provider onboarding routes render governed wizard instead of technical placeholders", () => {
  const app = read("src/App.tsx");
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const api = read("src/api/providerOnboarding.ts");

  assert.ok(app.includes("Hospital discovery is coming to Jeevanam Discover"));
  assert.ok(app.includes('<ProviderOnboardingPage type="doctor"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="clinic"'));
  assert.ok(app.includes('<ProviderOnboardingPage type="hospital"'));
  assert.ok(portal.includes("Save draft"));
  assert.ok(portal.includes("Submit for verification"));
  assert.ok(portal.includes("Public profile preview"));
  assert.ok(api.includes("/api/provider-registration/providers"));
  assert.ok(!app.includes("Planned for the next implementation phase"));
  assert.ok(!app.includes("ownership can be validated"));
});
