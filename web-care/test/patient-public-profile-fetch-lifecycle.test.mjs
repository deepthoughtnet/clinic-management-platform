import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  PUBLIC_PROFILE_STATUS,
  createPublicProfileLifecycle,
} from "../src/pages/patient/publicProfileLifecycle.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((innerResolve, innerReject) => {
    resolve = innerResolve;
    reject = innerReject;
  });
  return { promise, resolve, reject };
}

test("public doctor profile lifecycle fetches once per slug, ignores same-slug rerenders, aborts, and advances on slug change", async () => {
  const initialDoctor = {
    publicDoctorId: "",
    doctorSlug: "",
    canonicalSlug: "",
    publicPath: "",
    doctorDisplayName: "",
    photoUrl: null,
    bookingMode: null,
    qualification: null,
    medicalCouncil: null,
    yearsOfExperience: null,
    summary: null,
    biography: null,
    specialities: [],
    subSpecialities: [],
    languages: [],
    consultationModes: [],
    services: [],
    locations: [],
    galleryImageUrls: [],
    coverUrl: null,
    logoUrl: null,
    contactPhone: null,
    contactEmail: null,
    website: null,
    area: null,
    city: null,
    state: null,
    country: null,
    primarySpeciality: null,
    consultationFee: null,
    reviewsComingSoon: false,
    subtitle: null,
    bookingSummary: null,
    practices: [],
    clinics: [],
    availableDays: [],
    nextAvailableSlots: [],
    availableToday: false,
    bookingReference: null,
    canBookOnline: false,
  };
  const firstResponse = {
    ...initialDoctor,
    doctorSlug: "dr-arjun-mehta",
    doctorDisplayName: "Dr Arjun Mehta",
  };
  const secondResponse = {
    ...initialDoctor,
    doctorSlug: "dr-anita-sen",
    doctorDisplayName: "Dr Anita Sen",
  };

  const requests = [];
  const abortSignals = [];
  const firstFetch = deferred();
  const secondFetch = deferred();
  const stateLog = [];
  let fetchCount = 0;

  const lifecycle = createPublicProfileLifecycle({
    fetchDetail: (requestPath, signal) => {
      fetchCount += 1;
      requests.push(requestPath);
      abortSignals.push(signal);
      if (requestPath.endsWith("/dr-arjun-mehta")) {
        return firstFetch.promise;
      }
      if (requestPath.endsWith("/dr-anita-sen")) {
        return secondFetch.promise;
      }
      throw new Error(`Unexpected path ${requestPath}`);
    },
    onState: (state) => {
      stateLog.push(state);
    },
    isNotFoundError: () => false,
    loadingErrorMessage: "We couldn't load this doctor profile right now.",
    notFoundErrorMessage: "Doctor profile not found.",
  });

  lifecycle.update("/api/public/doctors/dr-arjun-mehta", initialDoctor);
  assert.equal(fetchCount, 1);
  assert.equal(requests[0], "/api/public/doctors/dr-arjun-mehta");
  assert.equal(stateLog.at(-1)?.status, PUBLIC_PROFILE_STATUS.LOADING);

  lifecycle.update("/api/public/doctors/dr-arjun-mehta", initialDoctor);
  assert.equal(fetchCount, 1);
  assert.equal(stateLog.filter((entry) => entry.status === PUBLIC_PROFILE_STATUS.LOADING).length, 1);

  firstFetch.resolve(firstResponse);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(stateLog.at(-1)?.status, PUBLIC_PROFILE_STATUS.SUCCESS);
  assert.equal(stateLog.at(-1)?.data.doctorDisplayName, "Dr Arjun Mehta");

  lifecycle.update("/api/public/doctors/dr-anita-sen", initialDoctor);
  assert.equal(fetchCount, 2);
  assert.equal(requests[1], "/api/public/doctors/dr-anita-sen");
  assert.equal(stateLog.at(-1)?.status, PUBLIC_PROFILE_STATUS.LOADING);

  lifecycle.dispose();
  assert.equal(abortSignals[0]?.aborted, true);
  assert.equal(abortSignals[1]?.aborted, true);

  secondFetch.resolve(secondResponse);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(stateLog.at(-1)?.status, PUBLIC_PROFILE_STATUS.LOADING);
  assert.equal(stateLog.filter((entry) => entry.status === PUBLIC_PROFILE_STATUS.SUCCESS).length, 1);
});

test("public doctor profile lifecycle terminates into not-found and safe error states", async () => {
  const initialDoctor = {
    publicDoctorId: "",
    doctorSlug: "",
    canonicalSlug: "",
    publicPath: "",
    doctorDisplayName: "",
    photoUrl: null,
    bookingMode: null,
    qualification: null,
    medicalCouncil: null,
    yearsOfExperience: null,
    summary: null,
    biography: null,
    specialities: [],
    subSpecialities: [],
    languages: [],
    consultationModes: [],
    services: [],
    locations: [],
    galleryImageUrls: [],
    coverUrl: null,
    logoUrl: null,
    contactPhone: null,
    contactEmail: null,
    website: null,
    area: null,
    city: null,
    state: null,
    country: null,
    primarySpeciality: null,
    consultationFee: null,
    reviewsComingSoon: false,
    subtitle: null,
    bookingSummary: null,
    practices: [],
    clinics: [],
    availableDays: [],
    nextAvailableSlots: [],
    availableToday: false,
    bookingReference: null,
    canBookOnline: false,
  };

  const notFoundStates = [];
  const notFoundLifecycle = createPublicProfileLifecycle({
    fetchDetail: () => Promise.reject(Object.assign(new Error("missing"), { status: 404 })),
    onState: (state) => {
      notFoundStates.push(state);
    },
    isNotFoundError: (error) => Boolean(error && typeof error === "object" && error.status === 404),
    loadingErrorMessage: "We couldn't load this doctor profile right now.",
    notFoundErrorMessage: "Doctor profile not found.",
  });

  notFoundLifecycle.update("/api/public/doctors/dr-missing", initialDoctor);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(notFoundStates.at(-1)?.status, PUBLIC_PROFILE_STATUS.NOT_FOUND);
  assert.equal(notFoundStates.at(-1)?.errorMessage, "Doctor profile not found.");

  const errorStates = [];
  const errorLifecycle = createPublicProfileLifecycle({
    fetchDetail: () => Promise.reject(new Error("backend exploded")),
    onState: (state) => {
      errorStates.push(state);
    },
    isNotFoundError: () => false,
    loadingErrorMessage: "We couldn't load this doctor profile right now.",
    notFoundErrorMessage: "Doctor profile not found.",
  });

  errorLifecycle.update("/api/public/doctors/dr-error", initialDoctor);
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(errorStates.at(-1)?.status, PUBLIC_PROFILE_STATUS.ERROR);
  assert.equal(errorStates.at(-1)?.errorMessage, "We couldn't load this doctor profile right now.");
});

test("care doctor and clinic profile pages use the shared stable public detail lifecycle", () => {
  const pages = readSource("pages/patient/PatientDiscoveryPages.tsx");

  assert.match(pages, /function usePublicDetail<[^>]+>\(path: string, initialValue: T\): PublicProfileFetchState<T>/);
  assert.match(pages, /useEffect\(\(\) => lifecycleRef\.current\?\.update\(path, initialValueRef\.current\), \[path\]\);/);
  assert.match(pages, /useEffect\(\(\) => \(\) => lifecycleRef\.current\?\.dispose\(\), \[\]\);/);
  assert.match(pages, /PatientPublicDoctorProfilePage/);
  assert.match(pages, /PatientPublicClinicProfilePage/);
  assert.match(pages, /PatientPublicDoctorProfilePage[\s\S]*usePublicDetail<PublicDoctorDetailResponse>/);
  assert.match(pages, /PatientPublicClinicProfilePage[\s\S]*usePublicDetail<PublicClinicDetailResponse>/);
});
