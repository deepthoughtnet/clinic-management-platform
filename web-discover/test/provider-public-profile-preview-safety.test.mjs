import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("landing page renderer tolerates missing draft sections and media", () => {
  const renderer = read("src/components/landing/LandingPageRenderer.tsx");
  const locationMap = read("src/components/location/LocationDisplayMap.tsx");
  const formatting = read("src/utils/publicProfileFormatting.ts");

  assert.ok(renderer.includes("safeList"));
  assert.ok(renderer.includes("safeLocationList"));
  assert.ok(renderer.includes("profile.locations?.[0]"));
  assert.ok(renderer.includes("Array.isArray(profile.galleryImageUrls)"));
  assert.ok(renderer.includes("Array.isArray(profile.gallery)"));
  assert.ok(renderer.includes("landing-contact-row"));
  assert.ok(renderer.includes("landing-route-link"));
  assert.ok(renderer.includes("buildPublicAddressView"));
  assert.ok(renderer.includes("resolveClinicEstablishedYear"));
  assert.ok(renderer.includes("formatWeeklyTimings"));
  assert.ok(renderer.includes("locationView.compact || \"Location not pinned.\""));
  assert.ok(renderer.includes("specialities.slice(0, 3)"));
  assert.ok(renderer.includes("Contact"));
  assert.ok(renderer.includes("renderMode === \"PUBLIC_PROFILE\""));
  assert.ok(renderer.includes("No services added yet."));
  assert.ok(renderer.includes("No facilities configured."));
  assert.ok(renderer.includes("No gallery images uploaded."));
  assert.ok(renderer.includes("Qualifications not provided."));
  assert.ok(renderer.includes("Contact details not configured."));
  assert.ok(renderer.includes("Location not pinned."));
  assert.ok(renderer.includes("No landing page content is available yet."));
  assert.ok(locationMap.includes('whiteSpace: "pre-line"'));
  assert.ok(locationMap.includes("noopener noreferrer"));
  assert.ok(formatting.includes("singleLine"));
});

test("provider draft preview tolerates missing profile fields without relying on hook order", () => {
  const page = read("src/pages/provider/ProviderPublicProfileDraftPage.tsx");

  assert.ok(page.includes("DRAFT PREVIEW - NOT PUBLIC"));
  assert.ok(page.includes("const groupedMissingFields = (() =>"));
  assert.ok(page.includes("missingMandatoryFields ?? []"));
  assert.ok(page.includes("Preview Draft"));
  assert.ok(page.includes("Copy Public URL"));
  assert.ok(page.includes("currentDraft.readiness.missingMandatoryFields.length || currentDraft.readiness.invalidFields.length ? \"Required before review:\" : \"No blocking content items remain.\""));
  assert.ok(!page.includes("Call clinic"));
});
