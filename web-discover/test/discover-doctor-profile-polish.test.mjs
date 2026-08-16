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

test("doctor profile page reuses shared verification, booking, and discovery components without fabricated reviews", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const profile = read("src/components/discovery/PublicProviderProfile.tsx");
  const helpers = read("src/components/discovery/DoctorProfileExperiences.tsx");

  assert.ok(page.includes("DoctorBreadcrumb"));
  assert.ok(page.includes("RelatedDoctorCard"));
  assert.ok(page.includes("SpecialtyCard"));
  assert.ok(page.includes("StickyBookingCTA"));
  assert.ok(page.includes("doctorSampleSpecialties"));
  assert.ok(page.includes("doctorSampleVerificationBadges"));
  assert.ok(page.includes("/api/public/doctors"));
  assert.ok(page.includes("consultationFeeLabel"));
  assert.ok(page.includes("detail.data?.consultationFee"));
  assert.ok(page.includes("matchedDoctor?.consultationFee"));
  assert.ok(page.includes("buildDoctorBookingGroups"));
  assert.ok(page.includes("buildDoctorWorkingSchedule"));
  assert.ok(page.includes("const canBookOnline = Boolean(detail.canBookOnline);"));
  assert.ok(page.includes("resolveDoctorBookingMode(detail.bookingMode, canBookOnline, detail.contactPhone)"));
  assert.ok(page.includes("canBookOnline && detail.nextAvailableSlots[0]"));
  assert.ok(page.includes("providerBookingPrimaryLabel(bookingMode)"));
  assert.ok(page.includes("AvailabilityTimeline"));
  assert.ok(page.includes("fallbackWorkingHours={profile.locationWorkingHours}"));
  assert.ok(page.includes("No patient reviews yet"));

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
  assert.ok(helpers.includes("export function BookingPanel"));
  assert.ok(helpers.includes("export function AvailabilityTimeline"));
  assert.ok(helpers.includes("export function RelatedDoctorCard"));
  assert.ok(helpers.includes("export function SpecialtyCard"));
  assert.ok(helpers.includes("export function StickyBookingCTA"));
  assert.ok(helpers.includes("Online booking is not available yet."));
  assert.ok(helpers.includes("Call the clinic to book this visit."));
  assert.ok(helpers.includes("Working hours:"));
  assert.ok(helpers.includes("fallbackWorkingHours"));
  assert.ok(!page.includes('averageWaitTime="15 min"'));
  assert.ok(!page.includes('appointmentDuration="20 min"'));
  assert.ok(!page.includes("RatingSummary rating={4.8}"));
  assert.ok(!page.includes("Patients Treated: 12,400+"));
  assert.ok(!page.includes("doctorSampleReviews.slice"));
  assert.ok(!helpers.includes("RatingSummary rating={4.8}"));
});

test("doctor profile review section renders patient-facing copy instead of raw backend data", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(page.includes("Patient Reviews"));
  assert.ok(page.includes("No patient reviews yet"));
  assert.ok(page.includes("Public review summaries will appear here once verified patient feedback has been published for this doctor."));
  assert.ok(!page.includes("245 Patient Reviews"));
  assert.ok(!page.includes("98% Recommend"));
  assert.ok(!page.includes("Patients Treated: 12,400+"));
});
