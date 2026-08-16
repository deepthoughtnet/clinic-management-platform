import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("discover location selectors share a common panel and canonical location mode state", () => {
  const app = read("src/App.tsx");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const directory = read("src/components/directory/DirectoryComponents.tsx");
  const context = read("src/context/PublicLocationContext.tsx");
  const panel = read("src/components/location/PublicLocationSelectorPanel.tsx");
  const styles = read("src/styles.css");

  assert.ok(app.includes("PublicLocationSelectorPanel"));
  assert.ok(pages.includes("PublicLocationSelectorPanel"));
  assert.ok(directory.includes("PublicLocationSelectorPanel"));
  assert.ok(panel.includes("options: readonly string[]"));
  assert.ok(panel.includes("useCurrentLocationDisabled"));
  assert.ok(panel.includes("saveDisabled"));
  assert.ok(panel.includes("currentLocationButtonLabel"));
  assert.ok(context.includes('mode: "city"'));
  assert.ok(context.includes('mode: "current"'));
  assert.ok(context.includes('location: ""'));
  assert.ok(context.includes("PublicLocationMode"));
  assert.ok(context.includes("getPublicLocationDisplayLabel"));
  assert.ok(context.includes("getPublicLocationSearchCity"));
  assert.ok(context.includes("validatePublicLocationInput"));
  assert.ok(context.includes("mapPublicLocationGeolocationError"));
  assert.ok(context.includes("PUBLIC_LOCATION_MODE_STORAGE_KEY"));
  assert.ok(styles.includes(".public-location-selector"));
  assert.ok(styles.includes(".public-location-selector__field"));
  assert.ok(styles.includes(".public-location-selector__helper"));
  assert.ok(styles.includes(".discover-header-location-shell"));
  assert.ok(styles.includes(".discover-home-location-shell"));
  assert.ok(styles.includes(".discover-directory-location-shell"));
  assert.ok(styles.includes(".discover-header-location-popover.header-location-selector-panel"));
  assert.ok(styles.includes(".discover-home-location-popover.hero-location-selector"));
  assert.ok(styles.includes(".discover-directory-location-popover.directory-location-popover"));
  assert.ok(styles.includes(".header-location-selector-panel__content"));
  assert.ok(styles.includes(".hero-location-selector__content"));
  assert.ok(styles.includes(".directory-location-popover__content"));
  assert.ok(styles.includes("width: 100%;"));
  assert.ok(styles.includes("min-inline-size: 320px"));
  assert.ok(styles.includes("width: min(360px, calc(100vw - 32px))"));
  assert.ok(!app.includes("header-location-selector-backdrop"));
  assert.ok(!pages.includes("hero-location-selector-backdrop"));
  assert.ok(!directory.includes("directory-location-popover-backdrop"));
});

test("discover home mobile location selector uses an inline mobile presentation", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const styles = read("src/styles.css");
  const mobileBlockStart = styles.indexOf("@media (max-width: 420px)");
  const mobileBlockEnd = styles.indexOf(".landing-page", mobileBlockStart);
  const mobileBlock = styles.slice(mobileBlockStart, mobileBlockEnd === -1 ? undefined : mobileBlockEnd);

  assert.ok(pages.includes('className="discover-home-location-popover hero-location-selector public-location-selector-panel"'));
  assert.ok(mobileBlock.includes(".hero-location-selector {"));
  assert.ok(mobileBlock.includes("position: static;"));
  assert.ok(mobileBlock.includes("width: 100%;"));
  assert.ok(mobileBlock.includes("max-width: none;"));
  assert.ok(mobileBlock.includes("overflow: visible;"));
  assert.ok(mobileBlock.includes(".hero-location-selector-backdrop {"));
  assert.ok(mobileBlock.includes("display: none;"));
});

test("discover location validation and geolocation errors remain user-facing and contextual", () => {
  const context = read("src/context/PublicLocationContext.tsx");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const directory = read("src/components/directory/DirectoryComponents.tsx");

  assert.ok(context.includes("Enter a valid city or locality."));
  assert.ok(context.includes("Location access was denied. Allow location permission in your browser or select a city manually."));
  assert.ok(context.includes("We could not determine your current location. Please choose a city manually."));
  assert.ok(context.includes("Location lookup timed out. Please try again or choose a city manually."));
  assert.ok(pages.includes("Searching within"));
  assert.ok(pages.includes("Radius applies after using your current location."));
  assert.ok(directory.includes("Radius applies within"));
  assert.ok(directory.includes("Radius becomes active after using your current location."));
});
