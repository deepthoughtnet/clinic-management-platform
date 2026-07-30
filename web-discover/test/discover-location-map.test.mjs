import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("reusable location map support is wired across Discover surfaces", () => {
  const config = read("src/config.ts");
  const main = read("src/main.tsx");
  const onboardingPage = read("src/pages/provider/ProviderOnboardingPage.tsx");
  const publicProfile = read("src/components/discovery/PublicProviderProfile.tsx");
  const publicPages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const landingRenderer = read("src/components/landing/LandingPageRenderer.tsx");
  const locationPicker = read("src/components/location/LocationPicker.tsx");
  const locationDisplayMap = read("src/components/location/LocationDisplayMap.tsx");
  const locationMap = read("src/components/location/LocationMap.tsx");
  const locationSearchInput = read("src/components/location/LocationSearchInput.tsx");
  const locationHelpers = read("src/components/location/locationHelpers.ts");
  const providerApi = read("src/api/providerOnboarding.ts");
  const publicApi = read("src/api/publicCatalog.ts");
  const landingApi = read("src/api/providerLandingPage.ts");
  const dockerfile = read("Dockerfile");
  const compose = read("../local/docker-compose.yml");
  const styles = read("src/styles.css");

  assert.ok(config.includes("VITE_MAP_TILE_URL"));
  assert.ok(config.includes("VITE_MAP_TILE_ATTRIBUTION"));
  assert.ok(config.includes("VITE_MAP_DEFAULT_LATITUDE"));
  assert.ok(config.includes("VITE_MAP_DEFAULT_LONGITUDE"));
  assert.ok(config.includes("VITE_MAP_DEFAULT_ZOOM"));
  assert.ok(config.includes("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"));
  assert.ok(config.includes("&copy; OpenStreetMap contributors"));
  assert.ok(config.includes("VITE_MAP_DIRECTIONS_URL_TEMPLATE"));
  assert.ok(config.includes("VITE_GEOCODING_PROVIDER"));
  assert.ok(config.includes("VITE_GEOCODING_BASE_URL"));
  assert.ok(config.includes("VITE_GEOCODING_SEARCH_PATH"));

  assert.ok(main.includes("leaflet/dist/leaflet.css"));
  assert.ok(onboardingPage.includes("LocationPicker"));
  assert.ok(publicProfile.includes("LocationDisplayMap"));
  assert.ok(publicPages.includes("PublicProviderProfile"));
  assert.ok(landingRenderer.includes("LocationDisplayMap"));
  assert.ok(locationPicker.includes("geocodeLocation"));
  assert.ok(locationPicker.includes("LocationSearchInput"));
  assert.ok(locationDisplayMap.includes("Get directions"));
  assert.ok(locationDisplayMap.includes("location-branch-list"));
  assert.ok(locationDisplayMap.includes("Map location has not been pinned yet."));
  assert.ok(locationDisplayMap.includes("Map is temporarily unavailable."));
  assert.ok(locationMap.includes("MapContainer"));
  assert.ok(locationMap.includes("TileLayer"));
  assert.ok(locationMap.includes("emptyState"));
  assert.ok(locationMap.includes("unavailableState"));
  assert.ok(locationMap.includes("location-map__canvas-shell"));
  assert.ok(locationMap.includes("location-map__status-overlay"));
  assert.ok(locationMap.includes("location-map__fallback"));
  assert.ok(!locationMap.includes("return <>{fallback}</>;"));
  assert.ok(locationSearchInput.includes("onSelectSuggestion"));
  assert.ok(locationSearchInput.includes("Use Current Location"));
  assert.ok(locationHelpers.includes("buildDirectionsUrl"));
  assert.ok(locationHelpers.includes("{latitude}"));
  assert.ok(locationHelpers.includes("{longitude}"));

  assert.ok(providerApi.includes("latitude?: number | null"));
  assert.ok(providerApi.includes("longitude?: number | null"));
  assert.ok(publicApi.includes("latitude: number | null"));
  assert.ok(publicApi.includes("longitude: number | null"));
  assert.ok(landingApi.includes("latitude: number | null"));
  assert.ok(landingApi.includes("longitude: number | null"));

  assert.ok(dockerfile.includes("VITE_MAP_TILE_URL"));
  assert.ok(dockerfile.includes("VITE_GEOCODING_BASE_URL"));
  assert.ok(compose.includes("VITE_MAP_TILE_URL"));
  assert.ok(compose.includes("VITE_GEOCODING_PROVIDER"));
  assert.ok(styles.includes(".location-map__canvas-shell"));
  assert.ok(styles.includes("isolation: isolate"));
  assert.ok(styles.includes(".location-map__status-overlay"));
  assert.ok(styles.includes(".location-map__fallback"));
  assert.ok(styles.includes(".jeevanam-location-map"));
  assert.ok(styles.includes(".location-display-map__map"));
  assert.ok(styles.includes(".location-search-result"));
  assert.ok(styles.includes(".chip-button.is-active"));
});
