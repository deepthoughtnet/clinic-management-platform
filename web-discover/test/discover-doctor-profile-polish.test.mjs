import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("doctor directory cards surface trust signals and fee consistency", () => {
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(components.includes("doctor-directory-rating-row"));
  assert.ok(components.includes("4.8"));
  assert.ok(components.includes("(245 Reviews)"));
  assert.ok(components.includes("Book appointment"));
  assert.ok(components.includes("careBookingUrl"));
  assert.ok(components.includes("DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)"));
});

test("doctor profile page reuses shared review, rating, verification, booking, and discovery components", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");
  const helpers = read("src/components/discovery/DoctorProfileExperiences.tsx");

  assert.ok(page.includes("DoctorBreadcrumb"));
  assert.ok(page.includes("RatingSummary"));
  assert.ok(page.includes("ReviewCard"));
  assert.ok(page.includes("RelatedDoctorCard"));
  assert.ok(page.includes("SpecialtyCard"));
  assert.ok(page.includes("StickyBookingCTA"));
  assert.ok(page.includes("doctorSampleReviews"));
  assert.ok(page.includes("doctorSampleSpecialties"));
  assert.ok(page.includes("doctorSampleVerificationBadges"));
  assert.ok(page.includes("visibleReviews"));
  assert.ok(page.includes("/api/public/doctors"));
  assert.ok(page.includes("consultationFeeLabel"));
  assert.ok(page.includes("buildDoctorBookingGroups"));
  assert.ok(page.includes("buildDoctorWorkingSchedule"));
  assert.ok(page.includes("providerBookingPrimaryLabel(bookingMode)"));
  assert.ok(page.includes("AvailabilityTimeline days={profile.workingHoursSchedule ?? []}"));

  assert.ok(profile.includes("heroSupplement"));
  assert.ok(profile.includes("verificationBadges"));
  assert.ok(profile.includes("afterBiographyContent"));
  assert.ok(profile.includes("serviceCards"));
  assert.ok(profile.includes("galleryInteractive"));
  assert.ok(profile.includes("workingHoursSchedule"));
  assert.ok(profile.includes("showAppointmentSection"));
  assert.ok(profile.includes("provider-preview-gallery-lightbox"));

  assert.ok(helpers.includes("export function RatingSummary"));
  assert.ok(helpers.includes("export function VerificationBadge"));
  assert.ok(helpers.includes("export function ReviewCard"));
  assert.ok(helpers.includes("export function BookingPanel"));
  assert.ok(helpers.includes("export function AvailabilityTimeline"));
  assert.ok(helpers.includes("export function RelatedDoctorCard"));
  assert.ok(helpers.includes("export function SpecialtyCard"));
  assert.ok(helpers.includes("export function StickyBookingCTA"));
  assert.ok(helpers.includes("Live slot availability is shown when you continue to booking."));
  assert.ok(helpers.includes("Working hours not published on this profile yet."));
  assert.ok(!page.includes("10:00 AM"));
  assert.ok(!page.includes("11:30 AM"));
  assert.ok(!page.includes("5:30 PM"));
});

test("doctor profile review section renders patient-facing copy instead of raw backend data", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const helpers = read("src/components/discovery/DoctorProfileExperiences.tsx");

  assert.ok(page.includes("Patient Reviews"));
  assert.ok(page.includes("Load More"));
  assert.ok(helpers.includes("recommendationPercent"));
  assert.ok(helpers.includes("patientFirstName"));
  assert.ok(helpers.includes("verifiedPatient"));
  assert.ok(helpers.includes("visitType"));
  assert.ok(helpers.includes("reviewDate"));
  assert.ok(helpers.includes("reviewText"));
  assert.ok(helpers.includes("Average rating"));
});
