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
  const branding = read("src/branding.ts");

  assert.ok(branding.includes("Jeevanam Discover"));
  assert.ok(homepage.includes("Find trusted doctors, clinics and hospitals near you."));
  assert.ok(homepage.includes("Search healthcare providers, compare services and book appointments with confidence."));
  assert.ok(homepage.includes("Browse by speciality"));
  assert.ok(homepage.includes("Doctors you can explore"));
  assert.ok(homepage.includes("Clinics near you"));
  assert.ok(homepage.includes("Grow your practice with Jeevanam"));
  assert.ok(homepage.includes("One connected healthcare experience"));
  assert.ok(app.includes("Patient Login"));
  assert.ok(app.includes("For Providers"));
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
    'path: "/privacy"',
    'path: "/terms"',
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
  assert.ok(app.includes('type="doctor"'));
  assert.ok(app.includes('type="clinic"'));
  assert.ok(app.includes('type="hospital"'));
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
