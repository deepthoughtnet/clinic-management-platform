import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("discover header switches between anonymous and authenticated provider actions", () => {
  const app = read("src/App.tsx");
  const styles = read("src/styles.css");

  assert.ok(app.includes("function ProviderHeaderActions()"));
  assert.ok(app.includes('const isHydratingSession = (status === "idle" || status === "loading") && isProviderSessionRoute(location.pathname);'));
  assert.ok(app.includes('if (status === "authenticated") {'));
  assert.ok(app.includes("Provider Account"));
  assert.ok(app.includes("Switch account"));
  assert.ok(app.includes("Logout"));
  assert.ok(app.includes("DiscoverMobileMenu"));
  assert.ok(app.includes("discover-mobile-menu-backdrop"));
  assert.ok(app.includes("discover-mobile-menu__section"));
  assert.ok(app.includes("HeaderLocationSelector"));
  assert.ok(app.includes("discover-header-location-shell"));
  assert.ok(app.includes("discover-header-location-popover"));
  assert.ok(app.includes("header-location-selector-summary"));
  assert.ok(!app.includes("header-location-selector-backdrop"));
  assert.ok(app.includes('to={DISCOVER_ROUTES.providerLogin.path}'));
  assert.ok(app.includes('Patient Login'));
  assert.ok(app.includes('For Providers'));
  assert.ok(app.includes('target="_blank" rel="noopener noreferrer"'));
  assert.ok(styles.includes("--discover-primary-soft: #E7F7F6"));
  assert.ok(styles.includes("--discover-surface: #FFFFFF"));
  assert.ok(styles.includes(".provider-account-menu-summary"));
  assert.ok(styles.includes(".provider-account-menu-panel"));
  assert.ok(styles.includes(".nav-link.is-active"));
  assert.ok(styles.includes(".header-location-selector-summary"));
  assert.ok(styles.includes(".discover-mobile-menu-backdrop"));
  assert.ok(styles.includes(".discover-mobile-menu"));
  assert.ok(styles.includes(".discover-header-location-shell"));
});

test("patient login opens a new tab from the discover shell and login chooser", () => {
  const app = read("src/App.tsx");

  assert.ok(app.includes('href={discoverConfig.careAppUrl} target="_blank" rel="noopener noreferrer"'));
  assert.ok(app.includes('Open Jeevanam Care'));
  assert.ok(app.includes('Patient login'));
});

test("provider session hydration includes onboarding routes", () => {
  const sessionContext = read("src/context/ProviderSessionContext.tsx");
  const app = read("src/App.tsx");

  assert.ok(sessionContext.includes('pathname.startsWith("/register/")'));
  assert.ok(app.includes('pathname.startsWith("/register/")'));
});
