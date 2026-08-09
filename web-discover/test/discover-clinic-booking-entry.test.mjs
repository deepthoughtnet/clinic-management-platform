import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("clinic booking entry derives capability from associated doctors and renders a doctor section", () => {
  const facade = read("../backend/api/api-bff/src/main/java/com/deepthoughtnet/clinic/api/publicsite/PublicCatalogFacade.java");
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");
  const pages = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(facade.includes("resolveClinicBookingMode(PublicProviderProfileDetailRecord clinicDetail)"));
  assert.ok(facade.includes("listPublishedDoctorReferencesByPractice(clinicDetail.providerId())"));
  assert.ok(facade.includes("BookingCapability.ONLINE_BOOKING"));
  assert.ok(facade.includes("publicClinicDoctors(detail)"));
  assert.ok(facade.includes("resolveClinicBookingMode(record.providerId(), record.canonicalSlug(), record.bookingMode(), record.contactPhone())"));

  assert.ok(profile.includes("Doctors at this clinic"));
  assert.ok(profile.includes("associatedDoctors"));
  assert.ok(profile.includes("Consultation fees"));
  assert.ok(profile.includes("providerType === \"CLINIC\""));
  assert.ok(profile.includes("Clinic booking entry follows the published doctor associations for this practice."));

  assert.ok(pages.includes("associatedDoctors: detail.doctors ?? []"));
  assert.ok(pages.includes('appointmentEmptyFeeText: "Fees vary by doctor"'));
  assert.ok(pages.includes('appointmentEmptyModesText: "Shown when you choose a doctor"'));
  assert.ok(pages.includes('careBookingUrl({ clinicSlug: detail.clinicSlug })'));

  assert.ok(components.includes('bookingCapabilityLabel(bookingMode)'));
  assert.ok(components.includes('careBookingUrl({ clinicSlug: clinic.clinicSlug })'));
  assert.ok(components.includes('doctorId: doctor.publicDoctorId'));
  assert.ok(components.includes('clinicSlug: doctor.clinicSlug'));
});
