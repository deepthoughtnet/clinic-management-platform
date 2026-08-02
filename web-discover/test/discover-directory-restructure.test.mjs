import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("shared directory shell keeps URL state, search, filters, and safe media in one place", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/directory/DirectoryComponents.tsx");
  const app = read("src/App.tsx");
  const styles = read("src/styles.css");

  for (const text of [
    "useDirectoryPageState",
    "commitSearch",
    "commitLocation",
    "updateParams",
    "clearParams",
    "DirectoryPageShell",
    "DirectoryHero",
    "DirectorySearchPanel",
    "DirectoryResultsToolbar",
    "DirectorySortMenu",
    "DirectoryFiltersDrawer",
    "DirectoryPageStickyPanel",
    "ResultCount",
    "pageAccentClass",
    "pageAccentTone",
    "pageSearchButtonLabel",
    "pageSearchPlaceholder",
    "selectedFilterCount",
  ]) {
    assert.ok(pages.includes(text) || components.includes(text), `${text} should be wired into the directory shell`);
  }

  assert.ok(components.includes('resolved.pathname.startsWith("/api/public/")'));
  assert.ok(!components.includes("http://minio:9000"));
  assert.ok(components.includes('aria-modal="true"'));
  assert.ok(components.includes("directory-drawer-backdrop"));
  assert.ok(app.includes("discover-shell--doctors-directory-wide"));
  assert.ok(styles.includes(".directory-page-shell"));
  assert.ok(styles.includes(".directory-results-toolbar"));
  assert.ok(styles.includes(".directory-search-panel"));
  assert.ok(styles.includes(".directory-drawer"));
  assert.ok(styles.includes(".directory-sort-menu"));
  assert.ok(styles.includes(".discover-shell--doctors-directory-wide"));
});

test("doctor directory emphasizes comparison, popularity, and booking without a stretched single-card layout", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/directory/DirectoryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(pages.includes('DirectoryHero'));
  assert.ok(pages.includes('title="Compare healthcare professionals"'));
  assert.ok(pages.includes("Popular specialities"));
  assert.ok(pages.includes("directory-card-grid--doctors"));
  assert.ok(components.includes("DoctorDirectoryCard"));
  assert.ok(components.includes("providerBookingPrimaryLabel(bookingMode)"));
  assert.ok(components.includes("View profile"));
  assert.ok(pages.includes("Consultation fee"));
  assert.ok(components.includes("Experience"));
  assert.ok(!components.includes("Compare checkbox"));
  assert.ok(styles.includes(".directory-card-grid--doctors"));
  assert.ok(styles.includes(".directory-card--doctor"));
  assert.ok(styles.includes(".directory-hero > div:first-child"));
  assert.ok(styles.includes("grid-template-columns: minmax(0, 1.45fr) minmax(180px, 0.58fr) auto"));
  assert.ok(styles.includes("grid-template-columns: minmax(220px, 240px) minmax(0, 1fr)"));
  assert.ok(styles.includes("width: min(100%, 900px)"));
  assert.ok(styles.includes(".directory-sort-trigger"));
});

test("clinic and hospital directory cards stay landscape and only render supported fields", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/directory/DirectoryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(pages.includes('title="Find clinics near you"'));
  assert.ok(pages.includes('title="Explore hospitals and specialty care"'));
  assert.ok(pages.includes("Popular areas"));
  assert.ok(pages.includes("Popular departments"));
  assert.ok(pages.includes("Clinic services"));
  assert.ok(components.includes("ClinicDirectoryCard"));
  assert.ok(components.includes("HospitalDirectoryCard"));
  assert.ok(components.includes("View clinic"));
  assert.ok(components.includes("View doctors"));
  assert.ok(components.includes("BookingCapabilityBadge"));
  assert.ok(styles.includes(".directory-card-grid--clinics"));
  assert.ok(styles.includes(".directory-card-grid--hospitals"));
  assert.ok(styles.includes(".directory-card--clinic"));
  assert.ok(styles.includes(".directory-card--hospital"));
  assert.ok(!components.includes("ICU"));
  assert.ok(!components.includes("bed count"));
  assert.ok(!components.includes("accreditation"));
  assert.ok(!components.includes("insurance"));
});

test("specialities page uses the new browse hierarchy and AIVA panel", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/directory/DirectoryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(pages.includes('title="Explore specialities"'));
  assert.ok(pages.includes('pageAccentClass("specialities")'));
  assert.ok(pages.includes("PopularSpecialityGrid"));
  assert.ok(pages.includes("AlphabetNavigation"));
  assert.ok(pages.includes("SpecialityCard"));
  assert.ok(pages.includes("AivaComingSoonPanel"));
  assert.ok(pages.includes("Search doctors"));
  assert.ok(pages.includes("Search clinics"));
  assert.ok(components.includes("Coming soon"));
  assert.ok(!pages.includes("Describe your symptoms in natural language."));
  assert.ok(components.includes("directory-speciality-card"));
  assert.ok(components.includes("directory-aiva-panel"));
  assert.ok(components.includes("Browse A to Z"));
  assert.ok(styles.includes(".directory-speciality-results"));
  assert.ok(styles.includes(".directory-alphabet-nav"));
  assert.ok(styles.includes(".directory-aiva-panel"));
});

test("result summary and footer regression remain intact", () => {
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const app = read("src/App.tsx");

  assert.ok(pages.includes("buildDirectoryResultLabel"));
  assert.ok(pages.includes("DirectoryResultsToolbar"));
  assert.ok(pages.includes("pageSearchPlaceholder(\"doctors\")"));
  assert.ok(pages.includes("pageSearchPlaceholder(\"clinics\")"));
  assert.ok(pages.includes("pageSearchPlaceholder(\"hospitals\")"));
  assert.ok(pages.includes("pageSearchPlaceholder(\"specialities\")"));

  for (const text of ["Patients", "Providers", "Support", "Legal", "Find doctors", "Find clinics", "Find hospitals", "Specialities", "Register practice", "Provider login", "About", "Contact", "Privacy", "Terms"]) {
    assert.ok(app.includes(text), `${text} should remain in the shared footer`);
  }
});
