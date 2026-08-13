import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("homepage search selector submits into canonical entity-aware routes with URL-driven query and location parameters", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(page.includes('id="find-care"'));
  assert.ok(page.includes('aria-label="Discover care search"'));
  assert.ok(page.includes("SearchIntentSelector"));
  assert.ok(page.includes('role="radiogroup"'));
  assert.ok(page.includes('aria-checked={selected}'));
  assert.ok(page.includes("Search doctors, clinics, hospitals, services..."));
  assert.ok(page.includes("Search doctor or speciality"));
  assert.ok(page.includes("Search clinic or service"));
  assert.ok(page.includes("Search hospital or speciality"));
  assert.ok(page.includes("Search treatment, service or speciality"));
  assert.ok(page.includes("buildSearchIntentTarget"));
  assert.ok(page.includes("const query = filters.query.trim();"));
  assert.ok(page.includes("const area = filters.area.trim();"));
  assert.ok(page.includes("const hasExplicitLocation = searchableLocation !== PUBLIC_DEFAULT_LOCATION;"));
  assert.ok(page.includes('navigate(".", { replace: true, state: { discoverHomeSearch: homeSearchDraft } })'));
  assert.ok(page.includes("navigate(buildSearchIntentTarget({"));
  assert.ok(page.includes('DISCOVER_ROUTES.search.path'));
  assert.ok(page.includes("Use my current location"));
  assert.ok(page.includes("radiusKm"));
  assert.ok(page.includes("event.preventDefault()"));
});

test("homepage includes production discovery sections without fabricated metrics", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  for (const text of [
    "Popular searches",
    "Popular ways to explore care",
    "Top doctors near you",
    "Clinics near you",
    "Hospitals near you",
    "Grow your practice with Jeevanam",
    "One connected healthcare experience",
    "Jeevanam Discover",
    "Jeevanam Connect",
    "Jeevanam Care",
    "Jeevanam Healthcare",
    "Provider workspace →",
    "Doctors",
    "Clinics",
    "Hospitals",
    "Specialities",
    "Health Packages",
    "Tests & Diagnostics",
  ]) {
    assert.ok(page.includes(text), `${text} should render on the homepage`);
  }

  const discoverIndex = page.indexOf('ecosystem-card ecosystem-discover');
  const connectIndex = page.indexOf('ecosystem-card ecosystem-connect');
  const careIndex = page.indexOf('ecosystem-card ecosystem-care');
  const healthcareIndex = page.indexOf('ecosystem-card ecosystem-healthcare');
  assert.ok(discoverIndex < connectIndex);
  assert.ok(connectIndex < careIndex);
  assert.ok(careIndex < healthcareIndex);
  assert.ok(page.includes('to={DISCOVER_ROUTES.listPractice.path}'));

  assert.ok(!page.includes("12,000"));
  assert.ok(!page.includes("1.2 million"));
  assert.ok(!page.includes("top-rated"));
});

test("homepage cards route to profiles and Care booking without exposing raw IDs as labels", () => {
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(components.includes("DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)"));
  assert.ok(components.includes("DISCOVER_DETAIL_PATHS.clinic(clinic.clinicSlug)"));
  assert.ok(components.includes("DISCOVER_DETAIL_PATHS.speciality(slugify(item))"));
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
  const config = read("src/config.ts");
  const styles = read("src/styles.css");

  assert.ok(!page.includes("Search nearby care"));
  assert.ok(!page.includes("Doctor profiles"));
  assert.ok(!page.includes("Clinic pages"));
  assert.ok(!page.includes("Jeevanam Care handoff"));
  assert.ok(!page.includes("Find care or bring your practice online."));
  assert.ok(!page.includes("Premium public discovery"));
  assert.ok(!page.includes("Search, compare and book with confidence."));
  assert.ok(!page.includes("Explore doctors nearby"));
  assert.ok(!page.includes("Location aware search"));
  assert.ok(!page.includes("Public-safe data"));
  assert.ok(!page.includes("home-search-meta"));
  assert.ok(!page.includes("Health packages"));
  assert.ok(!page.includes("Tests & diagnostics"));
  assert.ok(!styles.includes("final-cta"));
  assert.ok(page.includes("Find trusted healthcare near you."));
  assert.ok(!page.includes("Find trusted healthcare\n              <br />\n              near you."));
  assert.ok(page.includes('className="visually-hidden">Search'));
  assert.ok(page.includes('className="visually-hidden">Location'));
  assert.ok(page.includes("home-hero-grid"));
  assert.ok(page.includes("home-category-strip"));
  assert.ok(page.includes("home-hero-visual-image"));
  assert.ok(page.includes('src={discoverHeroIllustrationUrl}'));
  assert.ok(page.includes('alt="Jeevanam Discover healthcare network illustration"'));
  assert.ok(page.includes("home-doctors-layout"));
  assert.ok(page.includes("AivaDiscoveryAssistantCard"));
  assert.ok(styles.includes(".home-aiva-panel"));
  assert.ok(styles.includes(".home-aiva-card"));
  assert.ok(styles.includes(".home-aiva-coming-soon"));
  assert.ok(styles.includes(".home-preview-label"));
  assert.ok(styles.includes(".home-hero-visual {"));
  assert.ok(styles.includes("min-height: 432px"));
  assert.ok(styles.includes("background:"));
  assert.ok(styles.includes("scrollbar-width: none"));
  assert.ok(styles.includes("flex-wrap: wrap"));
  assert.ok(styles.includes("overflow-x: auto"));
  assert.ok(styles.includes(".popular-searches .chip-row::-webkit-scrollbar"));
  assert.ok(styles.includes("text-decoration: underline"));
  assert.ok(page.includes("homepage-hospital-grid"));
  assert.ok(page.includes("Nearby radius"));
  assert.ok(!page.includes("home-hero-visual-frame"));
  assert.ok(!page.includes("hero-visual-stack"));
  assert.ok(!page.includes("hero-visual-badges"));
  assert.ok(!components.includes("clinic-card-pattern"));
  assert.ok(styles.includes(".header-location-selector-summary"));
  assert.ok(styles.includes(".home-hero-visual-image"));
  assert.ok(styles.includes("repeat(6, minmax(0, 1fr))"));
  assert.ok(styles.includes("max-width: 32ch"));
  assert.ok(styles.includes("font-size: clamp(2rem, 2.55vw, 2.62rem)"));
  assert.ok(styles.includes("grid-template-columns: minmax(0, 3.35fr) minmax(176px, 1fr) minmax(136px, 160px)"));
  assert.ok(styles.includes("padding-inline: 14px"));
  assert.ok(styles.includes("min-height: 72px"));
  assert.ok(styles.includes("margin-top: 16px"));
  assert.ok(styles.includes("min-height: 146px"));
  assert.ok(styles.includes("padding: 16px 15px 13px"));
  assert.ok(styles.includes("object-fit: contain"));
  assert.ok(styles.includes("object-position: center center"));
  assert.ok(styles.includes("transform: translateY(-4px)"));
  assert.ok(styles.includes(".chip--success"));
  assert.ok(styles.includes(".chip--info"));
  assert.ok(styles.includes(".chip--muted"));
  assert.ok(styles.includes(".directory-avatar-illustration"));
  assert.ok(styles.includes(".footer-placeholder-link"));
  assert.ok(config.includes("showHomeDemoProviders"));
  assert.ok(config.includes("VITE_SHOW_HOME_DEMO_PROVIDERS"));
  assert.ok(page.includes("homeDemoProviders"));
});

test("provider onboarding routes render governed wizard instead of technical placeholders", () => {
  const app = read("src/App.tsx");
  const portal = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const api = read("src/api/providerOnboarding.ts");

  assert.ok(app.includes("PublicHospitalsPage"));
  assert.ok(app.includes("PublicHospitalDetailPage"));
  assert.ok(app.includes("ProviderRegistrationStartPage"));
  assert.ok(app.includes("startProviderApplication"));
  assert.ok(portal.includes("Save draft"));
  assert.ok(portal.includes("Submit for verification"));
  assert.ok(portal.includes("Public profile preview"));
  assert.ok(api.includes("/api/provider-registration/providers"));
  assert.ok(!app.includes("Planned for the next implementation phase"));
  assert.ok(!app.includes("ownership can be validated"));
});
