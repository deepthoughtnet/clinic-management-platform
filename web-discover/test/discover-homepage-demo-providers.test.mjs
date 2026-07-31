import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("home demo providers are controlled by one discover config flag", () => {
  const config = read("src/config.ts");
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const demo = read("src/features/home/homeDemoProviders.ts");

  assert.ok(config.includes("showHomeDemoProviders"));
  assert.ok(config.includes("VITE_SHOW_HOME_DEMO_PROVIDERS"));
  assert.ok(config.includes("defaultHomeDemoProviders"));
  assert.ok(page.includes("mergeHomeCards(homeDoctors, demoDoctors, 3, discoverConfig.showHomeDemoProviders)"));
  assert.ok(page.includes("mergeHomeCards(homeClinics, demoClinics, 3, discoverConfig.showHomeDemoProviders)"));
  assert.ok(page.includes("mergeHomeCards(homeHospitals, demoHospitals, 3, discoverConfig.showHomeDemoProviders)"));
  assert.ok(page.includes("demo={Boolean(doctor.demo)}"));
  assert.ok(page.includes("demo={Boolean(clinic.demo)}"));
  assert.ok(page.includes("demo={Boolean(hospital.demo)}"));
  assert.ok(demo.includes("Temporary local/UAT layout data."));
  assert.ok(demo.includes("Dr. Neha Sharma"));
  assert.ok(demo.includes("Jeevanam Medical Centre"));
  assert.ok(demo.includes("Jeevanam Multispeciality Hospital"));
});

test("demo actions remain non-interactive and are home-page only", () => {
  const components = read("src/components/DiscoveryComponents.tsx");
  const app = read("src/App.tsx");
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const styles = read("src/styles.css");

  assert.ok(components.includes("Demo profile"));
  assert.ok(components.includes("Demo booking"));
  assert.ok(components.includes("chip--demo"));
  assert.ok(components.includes("chip--success"));
  assert.ok(components.includes("chip--info"));
  assert.ok(components.includes("chip--muted"));
  assert.ok(components.includes("availabilityText"));
  assert.ok(!components.includes('nextAvailableSlotSummary ? <span className="chip">{doctor.nextAvailableSlotSummary}</span>'));
  assert.ok(styles.includes(".public-directory-card.is-demo"));
  assert.ok(styles.includes(".chip--demo"));
  assert.ok(page.includes("Hospitals near you"));
  assert.ok(page.includes("Preview examples"));
  assert.ok(!app.includes("demoDoctors"));
  assert.ok(!app.includes("demoClinics"));
  assert.ok(!app.includes("demoHospitals"));
  assert.ok(!page.includes("public/directory/demo"));
});
