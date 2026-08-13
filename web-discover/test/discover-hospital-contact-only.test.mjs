import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("hospital directory and profile use contact-only booking semantics", () => {
  const pages = read("pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("components/discovery/PublicProviderProfile.tsx");
  const directory = read("components/directory/DirectoryComponents.tsx");
  const cards = read("components/DiscoveryComponents.tsx");

  assert.ok(pages.includes('bookingLabel: contactPhone ? "Call Hospital" : "View hospital"'));
  assert.ok(pages.includes('callHref: secondaryHref'));
  assert.ok(pages.includes('galleryTitle: "Hospital image gallery"'));
  assert.ok(pages.includes('associatedDoctors: detail.doctors ?? []'));
  assert.ok(pages.includes('appointmentSectionContent: ('));
  assert.ok(pages.includes('Contact Hospital'));
  assert.ok(pages.includes('Fees vary by doctor or service.'));
  assert.ok(components.includes('appointmentSectionContent?: ReactNode;'));
  assert.ok(components.includes('appointmentSectionContent ?? ('));
  assert.ok(components.includes('id="doctors"'));
  assert.ok(components.includes('Doctors at this hospital'));
  assert.ok(components.includes('These doctors are associated with this hospital and shown on its public profile.'));
  assert.ok(components.includes('context={providerType === "HOSPITAL" ? "hospital" : "directory"}'));
  assert.ok(components.includes('hostProviderName={providerType === "HOSPITAL" ? displayName : null}'));
  assert.ok(cards.includes('dedupeDisplayParts('));
  assert.ok(directory.includes('const hospitalPath = hospital.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(hospital.hospitalSlug);'));
  assert.ok(directory.includes('to={`${hospitalPath}#doctors`}'));
  assert.ok(directory.includes('Call Hospital'));
  assert.ok(directory.includes('View hospital'));
  assert.ok(directory.includes('View doctors'));
  assert.ok(cards.includes('Call Hospital'));
});
