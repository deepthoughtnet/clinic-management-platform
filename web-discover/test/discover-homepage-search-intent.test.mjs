import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("home search intent selector defaults to Any and keeps search text while switching intents", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const styles = read("src/styles.css");
  const routes = read("src/routes.ts");

  assert.ok(page.includes("type DiscoverSearchIntent = \"any\" | \"doctors\" | \"clinics\" | \"hospitals\" | \"services\""));
  assert.ok(page.includes('label: "Any"'));
  assert.ok(page.includes('placeholder: "Search doctors, clinics, hospitals, services..."'));
  assert.ok(page.includes('placeholder: "Search doctor or speciality"'));
  assert.ok(page.includes('placeholder: "Search clinic or service"'));
  assert.ok(page.includes('placeholder: "Search hospital or speciality"'));
  assert.ok(page.includes('placeholder: "Search treatment, service or speciality"'));
  assert.ok(page.includes('role="radiogroup"'));
  assert.ok(page.includes('aria-orientation="horizontal"'));
  assert.ok(page.includes('role="radio"'));
  assert.ok(page.includes('aria-checked={selected}'));
  assert.ok(page.includes('onKeyDown={handleKeyDown}'));
  assert.ok(page.includes('ArrowRight'));
  assert.ok(page.includes('ArrowLeft'));
  assert.ok(page.includes('Home'));
  assert.ok(page.includes('End'));
  assert.ok(page.includes('<SearchIntentSelector value={searchIntent} onChange={setSearchIntent} />'));
  assert.ok(page.includes('navigate(buildSearchIntentTarget({'));
  assert.ok(page.includes('navigate(".", { replace: true, state: { discoverHomeSearch: homeSearchDraft } })'));
  assert.ok(page.includes('const homeSearchDraft: HomeSearchState = {'));
  assert.ok(page.includes('Search doctors, clinics, hospitals, services...'));
  assert.ok(styles.includes('.home-search-intent-selector'));
  assert.ok(styles.includes('grid-column: 1 / -1'));
  assert.ok(routes.includes('search: { path: "/discover/search", label: "Search" }'));
  assert.ok(!page.includes('navigate(`${DISCOVER_ROUTES.doctors.path}?${params.toString()}`)'));
});

test("Any routes to the global search page and category view-all links preserve location context", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const app = read("src/App.tsx");

  assert.ok(app.includes('path={DISCOVER_ROUTES.search.path} element={<PublicSearchPage />}'));
  assert.ok(app.includes('title: "Search | Jeevanam Discover"'));
  assert.ok(page.includes('"/api/public/search"'));
  assert.ok(page.includes('const latitude = searchParams.get("lat")?.trim() ?? "";'));
  assert.ok(page.includes('const longitude = searchParams.get("lng")?.trim() ?? "";'));
  assert.ok(page.includes('const radiusKm = searchParams.get("radiusKm")?.trim() ?? "10";'));
  assert.ok(page.includes('latitude && longitude && Number.isFinite(Number(latitude)) && Number.isFinite(Number(longitude))'));
  assert.ok(page.includes('Search results for "${query}"'));
  assert.ok(page.includes('emptyTitle="No matching healthcare providers or services found."'));
  assert.ok(page.includes('viewAllLabel="View all doctors"'));
  assert.ok(page.includes('viewAllLabel="View all clinics"'));
  assert.ok(page.includes('viewAllLabel="View all hospitals"'));
  assert.ok(page.includes('viewAllLabel="View all services"'));
  assert.ok(page.includes('title="Doctors"'));
  assert.ok(page.includes('title="Clinics"'));
  assert.ok(page.includes('title="Hospitals"'));
  assert.ok(page.includes('title="Services"'));
  assert.ok(page.includes('buildCategoryViewAllLink(DISCOVER_ROUTES.doctors.path)'));
  assert.ok(page.includes('buildCategoryViewAllLink(DISCOVER_ROUTES.clinics.path)'));
  assert.ok(page.includes('buildCategoryViewAllLink(DISCOVER_ROUTES.hospitals.path)'));
  assert.ok(page.includes('buildCategoryViewAllLink(DISCOVER_ROUTES.specialities.path, 24)'));
  assert.ok(page.includes('Search across doctors, clinics, hospitals, and services using published public catalog data.'));
  assert.ok(page.includes('No matching healthcare providers or services found.'));
  assert.ok(page.includes('Change search'));
  assert.ok(page.includes('Browse doctors'));
  assert.ok(page.includes('Browse clinics'));
  assert.ok(page.includes('View all services'));
  assert.ok(page.includes('search.data.doctors.items.slice(0, 4)'));
  assert.ok(page.includes('search.data.clinics.items.slice(0, 4)'));
  assert.ok(page.includes('search.data.hospitals?.items.slice(0, 4) ?? []'));
  assert.ok(page.includes('search.data.specialities.slice(0, 4)'));
});
