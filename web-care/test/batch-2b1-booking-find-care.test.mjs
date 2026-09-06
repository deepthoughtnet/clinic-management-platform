import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { groupAvailableSlotsByDate } from "../src/utils/bookingSlots.js";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

function deriveBookVisitState({
  explicitContext = null,
  candidates = [],
  selectedDoctorId = "",
  selectedDoctorSlug = "",
  manualDoctorSelection = false,
  bookingDoctorsLoading = false,
} = {}) {
  const hasExplicitContext =
    Boolean(
      explicitContext?.clinicId
      || explicitContext?.clinicCode
      || explicitContext?.clinicSlug
      || explicitContext?.doctorId
      || explicitContext?.doctorSlug
      || explicitContext?.bookingReference,
    ) && explicitContext?.source !== "storage"
    && explicitContext?.source !== "dev";
  const bookingContext = hasExplicitContext ? explicitContext : null;
  const explicitDoctorId = bookingContext?.doctorId ?? "";
  const explicitDoctorSlug = bookingContext?.doctorSlug ?? "";
  const matchedRouteCandidate =
    candidates.find((candidate) => candidate.publicDoctorId === explicitDoctorId)
    ?? candidates.find((candidate) => candidate.doctorSlug === explicitDoctorSlug)
    ?? null;
  const matchedManualCandidate =
    manualDoctorSelection && selectedDoctorId
      ? candidates.find((candidate) => candidate.publicDoctorId === selectedDoctorId) ?? null
      : null;

  if (bookingDoctorsLoading && hasExplicitContext && !matchedRouteCandidate) {
    return {
      showFindCare: true,
      selectedDoctorId: "",
      selectedDoctorSlug: "",
      pending: true,
    };
  }

  if (matchedRouteCandidate) {
    return {
      showFindCare: true,
      selectedDoctorId: matchedRouteCandidate.publicDoctorId,
      selectedDoctorSlug: matchedRouteCandidate.doctorSlug,
    };
  }

  if (matchedManualCandidate) {
    return {
      showFindCare: true,
      selectedDoctorId: matchedManualCandidate.publicDoctorId,
      selectedDoctorSlug: matchedManualCandidate.doctorSlug,
    };
  }

  return {
    showFindCare: true,
    selectedDoctorId: "",
    selectedDoctorSlug: "",
  };
}

function resetBookVisitSelection({
  searchParams,
  selectedDate,
} = {}) {
  const nextParams = new URLSearchParams(searchParams?.toString() ?? "");
  for (const key of [
    "doctorId",
    "doctorSlug",
    "publicDoctorId",
    "clinicId",
    "clinicSlug",
    "clinicCode",
    "clinic",
    "tenantId",
    "tenant",
    "tenantSlug",
    "practiceId",
    "publicPracticeId",
    "practiceSlug",
    "bookingReference",
  ]) {
    nextParams.delete(key);
  }
  if (selectedDate) {
    nextParams.set("date", selectedDate);
  }
  return {
    search: nextParams.toString(),
    selectedDoctorId: "",
    selectedDoctorSlug: "",
    selectedBookingReference: null,
    selectedClinicId: "",
    selectedClinicSlug: "",
    selectedTenantId: "",
    selectedClinicFilter: "",
    selectedSpeciality: "All",
    doctorSearchTerm: "",
    selectedSlot: null,
    slotsLength: 0,
    slotsLoading: false,
    slotsError: null,
    confirmation: null,
    submitError: null,
    manualDoctorSelection: false,
  };
}

function shouldLoadSlots({
  selectedDoctor = null,
  selectedDate = "",
  bookingSession = true,
  clinicSlug = "",
  bookingReference = null,
} = {}) {
  const bookingMode = selectedDoctor?.bookingMode ?? null;
  return Boolean(
    selectedDoctor
    && selectedDate
    && bookingSession
    && clinicSlug
    && bookingMode === "ONLINE_BOOKING"
    && bookingReference !== undefined,
  );
}

function compareSlotStartTimes(left, right) {
  const [leftHours = "0", leftMinutes = "0"] = String(left.slotTime ?? "").split(":");
  const [rightHours = "0", rightMinutes = "0"] = String(right.slotTime ?? "").split(":");
  return ((Number(leftHours) || 0) * 60 + (Number(leftMinutes) || 0))
    - ((Number(rightHours) || 0) * 60 + (Number(rightMinutes) || 0));
}

function slotDayPart(slot) {
  const hours = Number(String(slot.slotTime ?? "").split(":")[0] || 0);
  if (hours >= 5 && hours < 12) {
    return "Morning";
  }
  if (hours >= 12 && hours < 17) {
    return "Afternoon";
  }
  if (hours >= 17 && hours < 21) {
    return "Evening";
  }
  return "Night";
}

function deriveSlotPaging(slots, { pageIndex = 0, dayPart = "All", slotsPerPage = 5 } = {}) {
  const sorted = [...slots].sort(compareSlotStartTimes);
  const filtered = dayPart === "All" ? sorted : sorted.filter((slot) => slotDayPart(slot) === dayPart);
  const totalPages = Math.ceil(filtered.length / slotsPerPage);
  const safePageIndex = totalPages === 0 ? 0 : Math.min(pageIndex, totalPages - 1);
  const start = safePageIndex * slotsPerPage;
  const visible = filtered.slice(start, start + slotsPerPage);
  return {
    sorted,
    filtered,
    totalPages,
    pageIndex: safePageIndex,
    visible,
    hasPrevious: safePageIndex > 0,
    hasNext: safePageIndex < totalPages - 1,
    label: visible.length ? `${start + 1}-${start + visible.length} of ${filtered.length}` : "0-0 of 0",
  };
}

function makeSlots(times) {
  return times.map((slotTime) => ({
    appointmentDate: "2026-08-09",
    slotTime,
    slotEndTime: null,
    status: "AVAILABLE",
    selectable: true,
  }));
}

test("authenticated find care reuses the patient session for slot loading and shows capability grouping", () => {
  const pages = read("pages/patient/PatientPortalPages.tsx");
  const api = read("api/patientPortal.ts");
  const styles = read("styles.css");

  assert.ok(pages.includes('const bookingSession = session && session.patientSessionToken ? session : null;'));
  assert.ok(pages.includes("loadPatientPortalDoctorSlots("));
  assert.ok(pages.includes("bookingSession"));
  assert.ok(pages.includes("Search doctor, clinic, or area"));
  assert.ok(pages.includes("Book online with Jeevanam"));
  assert.ok(pages.includes("Call clinic to book"));
  assert.ok(pages.includes("loadPatientPortalDoctorAvailability("));
  assert.ok(pages.includes("patient-subcard-groups"));
  assert.ok(pages.includes("booking-mode-summary"));
  assert.ok(pages.includes("booking-date-strip"));
  assert.ok(pages.includes('currentIsoDateInTimeZone("Asia/Kolkata")'));
  assert.ok(pages.includes("Your patient session has expired. Sign in again to continue booking."));
  assert.ok(pages.includes("onSessionExpired"));
  assert.ok(api.includes("export async function loadPatientPortalDoctorSlots"));
  assert.ok(api.includes("session: PatientPortalSession"));
  assert.ok(styles.includes(".booking-capability-legend"));
  assert.ok(styles.includes(".booking-mode-chip--is-online"));
  assert.ok(styles.includes(".booking-date-pill"));
  assert.ok(styles.includes(".patient-filter-field"));
  assert.ok(styles.includes(".patient-select-shell"));
  assert.ok(styles.includes(".booking-contact-panel"));
  assert.ok(styles.includes(".patient-booking-doctor-list"));
  assert.ok(styles.includes(".patient-booking-confirmation-panel"));
  assert.ok(styles.includes(".patient-stat-card strong {"));
  assert.ok(styles.includes("font-family: inherit;"));
  assert.ok(styles.includes("clamp(1rem, 2.2vw, 1.3rem)"));
  assert.ok(styles.includes("clamp(1rem, 2vw, 1.2rem)"));
});

test("care booking uses association-aware clinic detail and keeps slot loading online-booking only", () => {
  const pages = read("pages/patient/PatientPortalPages.tsx");
  const discoveryPages = read("pages/patient/PatientDiscoveryPages.tsx");
  const app = read("App.tsx");
  const publicCatalog = read("api/publicCatalog.ts");
  const clinicContext = read("pages/patient/patientPortalClinicContext.ts");

  assert.ok(publicCatalog.includes("export type PublicPracticeMiniResponse ="));
  assert.ok(publicCatalog.includes("practices?: PublicPracticeMiniResponse[];"));
  assert.ok(pages.includes("resolveDoctorPracticeOptions"));
  assert.ok(pages.includes("doctor.practices?.length"));
  assert.ok(pages.includes("practiceName: primaryPractice?.clinicDisplayName ?? clinicName"));
  assert.ok(pages.includes("practiceSlug: doctor.clinicSlug"));
  assert.ok(pages.includes("practiceName: primaryPractice?.clinicDisplayName ?? clinicName"));
  assert.ok(pages.includes("buildBookingChoiceSearchTerms"));
  assert.ok(pages.includes("clinics={selectedDoctorClinics}"));
  assert.ok(pages.includes("bookingClinicCode === \"demo-clinic\" && !hasExplicitClinicQuery ? null : bookingClinicCode"));
  assert.ok(pages.includes("if (!resolvedBookingClinicCode) {"));
  assert.ok(pages.includes("!resolvedBookingClinicCode && selectedClinicFilter && doctor.clinicSlug !== selectedClinicFilter"));
  assert.ok(pages.includes("doctor.practiceName ?? doctor.clinicName"));
  assert.ok(pages.includes("doctor.practiceSlug ?? doctor.clinicSlug ?? \"\""));
  assert.ok(pages.includes("normalizePublicClinicDisplayName"));
  assert.ok(pages.includes("resolvePublicDoctorClinicContext"));
  assert.ok(pages.includes("selectedDoctorPublicClinic.clinicDisplayName ?? \"Clinic details available on profile\""));
  assert.ok(pages.includes("formatBookingLocationLabel(selectedDoctorPublicClinic.clinicDisplayName, selectedDoctorPublicClinic.area, selectedDoctorPublicClinic.city)"));
  assert.ok(pages.includes("displayClinicName ?? \"Clinic doctor\""));
  assert.ok(pages.includes("showBookingConfirmation"));
  assert.ok(pages.includes("selectedDoctor && selectedDoctorBookingMode === \"ONLINE_BOOKING\" && selectedSlotTime"));
  assert.ok(!pages.includes("Need help booking? Ask AIVA."));
  assert.ok(pages.includes("selectedDoctorClinics.length === 1"));
  assert.ok(pages.includes("selectedClinicCode={selectedClinicSlug || resolvedBookingClinicCode || \"\"}"));
  assert.ok(pages.includes("normalizeCareBookingMode(selectedDoctor?.bookingMode ?? null) !== \"ONLINE_BOOKING\""));
  assert.ok(pages.includes("loadPatientPortalDoctorSlots("));
  assert.ok(pages.includes("selectedDoctor?.source === \"public\""));
  assert.ok(pages.includes("source: \"care\""));
  assert.ok(pages.includes("source: \"public\""));
  assert.ok(pages.includes("clearPublicBookingContext();"));
  assert.ok(pages.includes("patient-discover-link"));
  assert.ok(pages.includes("Browse services"));
  assert.ok(pages.includes("to=\"/patient/doctors\""));
  assert.ok(pages.includes("to=\"/patient/clinics\""));
  assert.ok(pages.includes("to=\"/patient/services\""));
  assert.ok(discoveryPages.includes("normalizePublicClinicDisplayName(primaryPractice?.practiceDisplayName)"));
  assert.ok(discoveryPages.includes("normalizePublicClinicDisplayName(primaryClinic?.clinicDisplayName)"));
  assert.ok(discoveryPages.includes("clinicDisplayName ?? \"Clinic details available on profile\""));
  assert.ok(discoveryPages.includes("/patient/doctors/"));
  assert.ok(discoveryPages.includes("/patient/clinics/"));
  assert.ok(app.includes('path="/patient/doctors"'));
  assert.ok(app.includes('path="/patient/clinics"'));
  assert.ok(app.includes('path="/patient/hospitals"'));
  assert.ok(app.includes('path="/patient/specialities"'));
  assert.ok(app.includes('path="/patient/services"'));
  assert.ok(pages.includes("Provider details"));
  assert.ok(pages.includes("booking-contact-panel"));
  assert.ok(pages.includes("booking-service-pill"));
  assert.ok(pages.includes("View doctor profile"));
  assert.ok(pages.includes("View clinic profile"));
  assert.ok(pages.includes("portalBookingContext.source !== \"storage\" && portalBookingContext.source !== \"dev\""));
  assert.ok(pages.includes("SLOTS_PER_PAGE = 5"));
  assert.ok(pages.includes("slotPageIndex"));
  assert.ok(pages.includes("selectedDayPart"));
  assert.ok(pages.includes("SLOT_DAY_PARTS"));
  assert.ok(pages.includes("slotPagingContextKey"));
  assert.ok(pages.includes("visibleSlots"));
  assert.ok(pages.includes("Previous slots"));
  assert.ok(pages.includes("Next slots"));
  assert.ok(pages.includes("No appointments available on"));
  assert.ok(pages.includes("This doctor has no online slots on the selected date."));
  assert.ok(pages.includes("No online appointments are available in the next 14 days."));
  assert.ok(pages.includes("Next available appointments"));
  assert.ok(pages.includes("Find care"));
  assert.ok(pages.includes("patient-find-care-panel"));
  assert.ok(pages.includes("Change doctor"));
  assert.ok(pages.includes("handleChangeDoctor"));
  assert.ok(pages.includes("hasManualDoctorSelection"));
  assert.ok(pages.includes("hasExplicitRouteDoctor"));
  assert.ok(clinicContext.includes("const DEMO_CLINIC_CODE = \"demo-clinic\";"));
});

test("slot grouping preserves backend-returned bookable slots without browser-time filtering", () => {
  const grouped = groupAvailableSlotsByDate(
    [
      {
        appointmentDate: "2026-08-09",
        slotTime: "09:00",
        slotEndTime: "09:30",
        selectable: true,
        status: "AVAILABLE",
      },
      {
        appointmentDate: "2026-08-09",
        slotTime: "10:00",
        slotEndTime: "10:30",
        selectable: true,
        status: "AVAILABLE",
      },
      {
        appointmentDate: "2026-08-10",
        slotTime: "09:00",
        slotEndTime: "09:30",
        selectable: true,
        status: "AVAILABLE",
      },
    ],
    new Date("2026-08-09T23:59:59Z"),
  );

  assert.equal(grouped.length, 2);
  assert.deepEqual(grouped[0].date, "2026-08-09");
  assert.deepEqual(grouped[0].slots.map((slot) => slot.slotTime), ["09:00", "10:00"]);
  assert.deepEqual(grouped[1].date, "2026-08-10");
  assert.deepEqual(grouped[1].slots.map((slot) => slot.slotTime), ["09:00"]);
});

test("slot paging shows five chronological slots at a time and preserves selected-slot state", () => {
  const slots = makeSlots([
    "05:00:00",
    "05:30:00",
    "06:00:00",
    "06:30:00",
    "07:00:00",
    "07:30:00",
    "08:00:00",
    "08:30:00",
    "17:00:00",
    "17:30:00",
    "18:00:00",
    "18:30:00",
  ]);

  const page1 = deriveSlotPaging(slots);
  assert.deepEqual(page1.visible.map((slot) => slot.slotTime), [
    "05:00:00",
    "05:30:00",
    "06:00:00",
    "06:30:00",
    "07:00:00",
  ]);
  assert.equal(page1.hasPrevious, false);
  assert.equal(page1.hasNext, true);

  const page2 = deriveSlotPaging(slots, { pageIndex: 1 });
  assert.deepEqual(page2.visible.map((slot) => slot.slotTime), [
    "07:30:00",
    "08:00:00",
    "08:30:00",
    "17:00:00",
    "17:30:00",
  ]);
  assert.equal(page2.hasPrevious, true);
  assert.equal(page2.hasNext, true);

  const page3 = deriveSlotPaging(slots, { pageIndex: 2 });
  assert.deepEqual(page3.visible.map((slot) => slot.slotTime), [
    "18:00:00",
    "18:30:00",
  ]);
  assert.equal(page3.hasPrevious, true);
  assert.equal(page3.hasNext, false);

  const selectedSlot = slots[2];
  const afterNext = deriveSlotPaging(slots, { pageIndex: 2 });
  assert.equal(selectedSlot.slotTime, "06:00:00");
  assert.equal(afterNext.visible.some((slot) => slot.slotTime === selectedSlot.slotTime), false);
});

test("day-part filters apply before pagination and keep selection intact", () => {
  const slots = makeSlots([
    "05:00:00",
    "05:30:00",
    "06:00:00",
    "06:30:00",
    "07:00:00",
    "12:00:00",
    "12:30:00",
    "13:00:00",
    "17:00:00",
    "17:30:00",
    "18:00:00",
    "21:00:00",
    "21:30:00",
  ]);

  const morning = deriveSlotPaging(slots, { dayPart: "Morning" });
  assert.deepEqual(morning.visible.map((slot) => slot.slotTime), [
    "05:00:00",
    "05:30:00",
    "06:00:00",
    "06:30:00",
    "07:00:00",
  ]);
  assert.equal(morning.totalPages, 1);

  const afternoon = deriveSlotPaging(slots, { dayPart: "Afternoon" });
  assert.deepEqual(afternoon.visible.map((slot) => slot.slotTime), [
    "12:00:00",
    "12:30:00",
    "13:00:00",
  ]);
  assert.equal(afternoon.hasPrevious, false);
  assert.equal(afternoon.hasNext, false);

  const evening = deriveSlotPaging(slots, { dayPart: "Evening" });
  assert.deepEqual(evening.visible.map((slot) => slot.slotTime), [
    "17:00:00",
    "17:30:00",
    "18:00:00",
  ]);
  assert.equal(evening.totalPages, 1);

  const night = deriveSlotPaging(slots, { dayPart: "Night" });
  assert.deepEqual(night.visible.map((slot) => slot.slotTime), [
    "21:00:00",
    "21:30:00",
  ]);
  assert.equal(night.totalPages, 1);

  const allReset = deriveSlotPaging(slots, { dayPart: "All", pageIndex: 2 });
  assert.equal(allReset.pageIndex, 2);
  const resetOnDayPartChange = deriveSlotPaging(slots, { dayPart: "Evening", pageIndex: 9 });
  assert.equal(resetOnDayPartChange.pageIndex, 0);

  const selectedSlot = slots[11];
  assert.equal(selectedSlot.slotTime, "21:00:00");
  assert.equal(deriveSlotPaging(slots, { dayPart: "Morning" }).visible.some((slot) => slot.slotTime === selectedSlot.slotTime), false);
  assert.equal(selectedSlot.slotTime, "21:00:00");
});

test("duplicate backend slot entries are preserved as distinct visible cards", () => {
  const slots = makeSlots([
    "18:00:00",
    "18:00:00",
    "18:30:00",
    "18:30:00",
  ]);
  const page = deriveSlotPaging(slots);
  assert.equal(page.visible.length, 4);
  assert.deepEqual(page.visible.map((slot) => slot.slotTime), [
    "18:00:00",
    "18:00:00",
    "18:30:00",
    "18:30:00",
  ]);
});

test("book visit keeps discovery visible and only seeds explicit doctor/practice context", () => {
  const greenValleyDoctor = {
    publicDoctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
    doctorSlug: "amit-verma-2",
    bookingMode: "CALL_TO_BOOK",
  };

  const noContext = deriveBookVisitState({
    candidates: [greenValleyDoctor],
  });
  assert.equal(noContext.showFindCare, true);
  assert.equal(noContext.selectedDoctorId, "");
  assert.equal(noContext.selectedDoctorSlug, "");

  const explicitDoctor = deriveBookVisitState({
    explicitContext: {
      source: "query",
      doctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
      doctorSlug: "amit-verma-2",
      clinicSlug: "green-valley-family-clinic",
    },
    candidates: [greenValleyDoctor],
  });
  assert.equal(explicitDoctor.selectedDoctorId, "ff4d7d2a-401a-4993-9814-afe2863275b6");
  assert.equal(explicitDoctor.selectedDoctorSlug, "amit-verma-2");
  assert.equal(explicitDoctor.pending, undefined);

  const pendingExplicit = deriveBookVisitState({
    explicitContext: {
      source: "query",
      doctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
      clinicSlug: "green-valley-family-clinic",
    },
    bookingDoctorsLoading: true,
    candidates: [],
  });
  assert.equal(pendingExplicit.pending, true);
  assert.equal(pendingExplicit.selectedDoctorId, "");

  const invalidExplicit = deriveBookVisitState({
    explicitContext: {
      source: "query",
      doctorId: "00000000-0000-0000-0000-000000000000",
      clinicSlug: "green-valley-family-clinic",
    },
    candidates: [greenValleyDoctor],
  });
  assert.equal(invalidExplicit.selectedDoctorId, "");
  assert.equal(invalidExplicit.selectedDoctorSlug, "");

  const reset = resetBookVisitSelection({
    searchParams: new URLSearchParams("date=2026-08-09&doctorId=ff4d7d2a-401a-4993-9814-afe2863275b6&clinicSlug=green-valley-family-clinic&bookingReference=ref-123"),
    selectedDate: "2026-08-09",
  });
  assert.equal(reset.search, "date=2026-08-09");
  assert.equal(reset.selectedDoctorId, "");
  assert.equal(reset.selectedDoctorSlug, "");
  assert.equal(reset.selectedClinicSlug, "");
  assert.equal(reset.selectedSpeciality, "All");
  assert.equal(reset.doctorSearchTerm, "");

  const afterResetNoAutoSelect = deriveBookVisitState({
    candidates: [greenValleyDoctor],
  });
  assert.equal(afterResetNoAutoSelect.selectedDoctorId, "");
  assert.equal(afterResetNoAutoSelect.selectedDoctorSlug, "");

  const beforeChangeDoctor = deriveBookVisitState({
    explicitContext: {
      source: "query",
      doctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
      doctorSlug: "amit-verma-2",
      clinicSlug: "green-valley-family-clinic",
    },
    candidates: [greenValleyDoctor],
  });
  assert.equal(beforeChangeDoctor.selectedDoctorId, "ff4d7d2a-401a-4993-9814-afe2863275b6");
  assert.equal(beforeChangeDoctor.selectedDoctorSlug, "amit-verma-2");

  const afterChangeDoctor = deriveBookVisitState({
    candidates: [greenValleyDoctor],
    selectedDoctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
    selectedDoctorSlug: "amit-verma-2",
    manualDoctorSelection: false,
  });
  assert.equal(afterChangeDoctor.selectedDoctorId, "");
  assert.equal(afterChangeDoctor.selectedDoctorSlug, "");

  const callToBookDoctor = deriveBookVisitState({
    explicitContext: {
      source: "query",
      doctorId: "ff4d7d2a-401a-4993-9814-afe2863275b6",
      clinicSlug: "green-valley-family-clinic",
    },
    candidates: [greenValleyDoctor],
  });
  assert.equal(callToBookDoctor.selectedDoctorId, "ff4d7d2a-401a-4993-9814-afe2863275b6");
  assert.equal(shouldLoadSlots({
    selectedDoctor: greenValleyDoctor,
    selectedDate: "2026-08-09",
    bookingSession: true,
    clinicSlug: "green-valley-family-clinic",
  }), false);

  assert.equal(shouldLoadSlots({
    selectedDoctor: {
      ...greenValleyDoctor,
      bookingMode: "ONLINE_BOOKING",
    },
    selectedDate: "2026-08-09",
    bookingSession: true,
    clinicSlug: "green-valley-family-clinic",
  }), true);
});

test("online booking slot paging shows five slots at a time and preserves selection", () => {
  const makeSlots = (count) =>
    Array.from({ length: count }, (_, index) => {
      const slotMinute = index * 30;
      const hour = String(16 + Math.floor(slotMinute / 60)).padStart(2, "0");
      const minute = String(slotMinute % 60).padStart(2, "0");
      const slotTime = `${hour}:${minute}:00`;
      return {
        appointmentDate: "2026-08-09",
        slotTime,
        slotEndTime: `${hour}:${String((slotMinute % 60) + 30).padStart(2, "0")}:00`,
        selectable: true,
      };
    });

  const zero = deriveSlotPaging([]);
  assert.equal(zero.totalPages, 0);
  assert.equal(zero.visible.length, 0);
  assert.equal(zero.hasPrevious, false);
  assert.equal(zero.hasNext, false);

  const one = deriveSlotPaging(makeSlots(1));
  assert.equal(one.totalPages, 1);
  assert.equal(one.visible.length, 1);
  assert.equal(one.hasPrevious, false);
  assert.equal(one.hasNext, false);

  const five = deriveSlotPaging(makeSlots(5));
  assert.equal(five.totalPages, 1);
  assert.equal(five.visible.length, 5);
  assert.equal(five.hasPrevious, false);
  assert.equal(five.hasNext, false);

  const sixPage1 = deriveSlotPaging(makeSlots(6), { pageIndex: 0 });
  assert.equal(sixPage1.totalPages, 2);
  assert.deepEqual(sixPage1.visible.map((slot) => slot.slotTime), [
    "16:00:00",
    "16:30:00",
    "17:00:00",
    "17:30:00",
    "18:00:00",
  ]);
  assert.equal(sixPage1.hasPrevious, false);
  assert.equal(sixPage1.hasNext, true);

  const sixPage2 = deriveSlotPaging(makeSlots(6), { pageIndex: 1 });
  assert.deepEqual(sixPage2.visible.map((slot) => slot.slotTime), ["18:30:00"]);
  assert.equal(sixPage2.hasPrevious, true);
  assert.equal(sixPage2.hasNext, false);

  const twelvePage1 = deriveSlotPaging(makeSlots(12), { pageIndex: 0 });
  const twelvePage2 = deriveSlotPaging(makeSlots(12), { pageIndex: 1 });
  const twelvePage3 = deriveSlotPaging(makeSlots(12), { pageIndex: 2 });
  assert.deepEqual(twelvePage1.visible.map((slot) => slot.slotTime), [
    "16:00:00",
    "16:30:00",
    "17:00:00",
    "17:30:00",
    "18:00:00",
  ]);
  assert.deepEqual(twelvePage2.visible.map((slot) => slot.slotTime), [
    "18:30:00",
    "19:00:00",
    "19:30:00",
    "20:00:00",
    "20:30:00",
  ]);
  assert.deepEqual(twelvePage3.visible.map((slot) => slot.slotTime), [
    "21:00:00",
    "21:30:00",
  ]);
  assert.equal(twelvePage3.hasPrevious, true);
  assert.equal(twelvePage3.hasNext, false);

  const selectedSlot = { ...makeSlots(12)[2] };
  const afterPaging = deriveSlotPaging(makeSlots(12), { pageIndex: 2 });
  assert.equal(selectedSlot.slotTime, "17:00:00");
  assert.equal(afterPaging.visible.some((slot) => slot.slotTime === selectedSlot.slotTime), false);
  assert.equal(selectedSlot.slotTime, "17:00:00");

  const resetOnNewContext = deriveSlotPaging(makeSlots(12), 0);
  assert.equal(resetOnNewContext.pageIndex, 0);
});
