import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("application foundation uses React, TypeScript, and Vite", () => {
  const pkg = JSON.parse(read("package.json"));
  const viteConfig = read("vite.config.ts");

  assert.equal(pkg.name, "web-discover");
  assert.equal(pkg.type, "module");
  assert.ok(pkg.dependencies.react);
  assert.ok(pkg.dependencies["react-router-dom"]);
  assert.ok(pkg.devDependencies.vite);
  assert.ok(viteConfig.includes("port: 5177"));
});

test("discover brand, public navigation, and homepage sections are present", () => {
  const app = read("src/App.tsx");
  const homepage = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const publicInfo = read("src/pages/public/PublicInfoPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");
  const publicDiscoveryUtils = read("src/utils/publicDiscovery.ts");
  const styles = read("src/styles.css");
  const branding = read("src/branding.ts");

  assert.ok(branding.includes("Jeevanam Discover"));
  assert.ok(homepage.includes("Find trusted healthcare"));
  assert.ok(homepage.includes("Discover doctors, clinics, hospitals and health services for a healthier life."));
  assert.ok(homepage.includes("Popular ways to explore care"));
  assert.ok(homepage.includes("Top doctors near you"));
  assert.ok(homepage.includes("Clinics near you"));
  assert.ok(homepage.includes("Hospitals near you"));
  assert.ok(homepage.includes("Grow your practice with Jeevanam"));
  assert.ok(homepage.includes("One connected healthcare experience"));
  assert.ok(homepage.includes("Jeevanam Connect"));
  assert.ok(homepage.includes("Provider workspace →"));
  assert.ok(homepage.includes('<SearchIntentSelector value={searchIntent} onChange={setSearchIntent} />'));
  assert.ok(homepage.includes('maxLength={DISCOVERY_SEARCH_MAX_LENGTH}'));
  assert.ok(homepage.includes('const validationError = validateDiscoverySearchQuery(query);'));
  assert.ok(publicDiscoveryUtils.includes('DISCOVERY_SEARCH_MAX_LENGTH = 120'));
  assert.ok(publicDiscoveryUtils.includes('buildDiscoveryNoResultsTitle'));
  assert.ok(publicDiscoveryUtils.includes('buildDiscoveryNoResultsMessage'));
  assert.ok(styles.includes(".ecosystem-grid {"));
  assert.ok(styles.includes("repeat(4, minmax(0, 1fr))"));
  assert.ok(app.includes("Patient Login"));
  assert.ok(app.includes("Home"));
  assert.ok(app.includes("For Providers"));
  assert.ok(homepage.includes("AivaDiscoveryAssistantCard"));
  assert.ok(components.includes("Your AI care guide"));
  assert.ok(components.includes("Coming Soon"));
  assert.ok(app.includes('aria-label="Patients"'));
  assert.ok(app.includes('aria-label="Providers"'));
  assert.ok(app.includes('aria-label="Support"'));
  assert.ok(app.includes('aria-label="Legal"'));
  assert.ok(app.includes("Accessibility"));
  assert.ok(app.includes("Sitemap"));
  assert.ok(app.includes("Help"));
  assert.ok(app.includes("Security"));
  assert.ok(app.includes("Cookies"));
  assert.ok(app.includes('path={DISCOVER_ROUTES.help.path}'));
  assert.ok(app.includes('path={DISCOVER_ROUTES.accessibility.path}'));
  assert.ok(app.includes('path={DISCOVER_ROUTES.sitemap.path}'));
  assert.ok(app.includes('path={DISCOVER_ROUTES.security.path}'));
  assert.ok(app.includes('path={DISCOVER_ROUTES.cookies.path}'));
  assert.ok(publicInfo.includes("Support channel"));
  assert.ok(publicInfo.includes("Send enquiry"));
  assert.ok(publicInfo.includes("Help and support"));
  assert.ok(publicInfo.includes("Accessibility at Jeevanam Discover"));
  assert.ok(publicInfo.includes("Jeevanam Discover sitemap"));
  assert.ok(publicInfo.includes("Privacy"));
  assert.ok(publicInfo.includes("Security"));
  assert.ok(publicInfo.includes("Cookies"));
  assert.ok(app.includes("Verified public information and clear discovery routes."));
});

test("required public routes are defined and patient routes are absent", () => {
  const routes = read("src/routes.ts");
  const app = read("src/App.tsx");
  const requiredRoutes = [
    'path: "/"',
    'path: "/discover/doctors"',
    'path: "/discover/clinics"',
    'path: "/discover/hospitals"',
    'path: "/discover/specialities"',
    'path: "/discover/services"',
    'path: "/healthcare"',
    'path: "/pricing"',
    'path: "/list-your-practice"',
    'path: "/register/doctor"',
    'path: "/register/clinic"',
    'path: "/register/hospital"',
    'path: "/login"',
    'path: "/about"',
    'path: "/contact"',
    'path: "/help"',
    'path: "/accessibility"',
    'path: "/sitemap"',
    'path: "/privacy"',
    'path: "/terms"',
    'path: "/security"',
    'path: "/cookies"',
  ];

  for (const route of requiredRoutes) {
    assert.ok(routes.includes(route), `${route} should be present`);
  }
  assert.ok(app.includes('path="/doctors"'));
  assert.ok(app.includes('path="/clinics"'));
  assert.ok(app.includes('path="/hospitals"'));
  assert.ok(app.includes('path="/specialities"'));
  assert.ok(app.includes('path="*"'));
  assert.ok(!routes.includes("/patient"));
  assert.ok(!app.includes("/patient/"));
  assert.ok(!app.includes("PatientDashboard"));
  assert.ok(!app.includes("PatientPortal"));
});

test("provider registration entry and placeholder routes are present", () => {
  const app = read("src/App.tsx");

  assert.ok(app.includes("Individual Doctor"));
  assert.ok(app.includes("Clinic"));
  assert.ok(app.includes("Hospital"));
  assert.ok(app.includes("Create account"));
  assert.ok(app.includes("Complete profile"));
  assert.ok(app.includes("Submit for review"));
  assert.ok(app.includes("Publish"));
  assert.ok(app.includes("Once approved, your profile can be published in Discover."));
  assert.ok(app.includes("ProviderRegistrationStartPage"));
  assert.ok(app.includes("startProviderApplication"));
});

test("login chooser uses configurable Care and Healthcare URLs", () => {
  const app = read("src/App.tsx");
  const config = read("src/config.ts");

  assert.ok(config.includes("VITE_CARE_APP_URL"));
  assert.ok(config.includes("VITE_HEALTHCARE_APP_URL"));
  assert.ok(config.includes("VITE_AIVA_APP_URL"));
  assert.ok(config.includes("VITE_ANALYTICS_ENABLED"));
  assert.ok(app.includes("Open Jeevanam Care"));
  assert.ok(app.includes("Open Jeevanam Healthcare"));
  assert.ok(app.includes("discoverConfig.careAppUrl"));
  assert.ok(app.includes("discoverConfig.healthcareAppUrl"));
  assert.ok(!app.includes("Platform Administration"));
});

test("shell has accessibility basics and mobile navigation support", () => {
  const app = read("src/App.tsx");
  const homepage = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(app.includes('aria-label="Discover navigation"'));
  assert.ok(app.includes('aria-expanded={menuOpen}'));
  assert.ok(homepage.includes('aria-label="Discover care search"'));
  assert.ok(app.includes('aria-label="Provider registration lifecycle"'));
  assert.ok(components.includes('role="status"'));
  assert.ok(styles.includes(":focus-visible"));
  assert.ok(styles.includes("@media (max-width: 980px)"));
  assert.ok(styles.includes("--discover-container: 1200px"));
  assert.ok(styles.includes("grid-template-columns: 1fr"));
});

test("demo branding, staff auth, and backend integration are not introduced", () => {
  const combined = [
    read("src/App.tsx"),
    read("src/routes.ts"),
    read("src/config.ts"),
    read("src/styles.css"),
  ].join("\n");

  assert.ok(!combined.includes("Demo / UAT"));
  assert.ok(!combined.includes("Demo Links"));
  assert.ok(!combined.includes("keycloak"));
  assert.ok(!combined.includes("X-Patient-Session"));
  assert.ok(!combined.includes("/api/patient-portal"));
  assert.ok(!combined.includes("/api/platform"));
  assert.ok(!combined.includes("web-admin"));
});

test("deployment and documentation foundation are present", () => {
  const dockerfile = read("Dockerfile");
  const nginx = read("nginx/default.conf");
  const readme = read("README.md");
  const index = read("index.html");

  assert.ok(dockerfile.includes("npm ci"));
  assert.ok(dockerfile.includes("VITE_CARE_APP_URL"));
  assert.ok(nginx.includes("try_files $uri $uri/ /index.html"));
  assert.ok(index.includes("/favicon.svg"));
  assert.ok(readme.includes("Phase 3B"));
  assert.ok(readme.includes("The local development server uses port `5177`."));
});
