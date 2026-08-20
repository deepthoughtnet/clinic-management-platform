import test from "node:test";
import assert from "node:assert/strict";
import { doctorUpdateSchema } from "../../frontend/packages/form-validation-kit/dist/index.js";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("doctor update schema accepts required details including dob", () => {
  const parsed = doctorUpdateSchema.safeParse({
    mobile: "9876543210",
    specializations: ["Dermatology", "Skin Care"],
    qualification: "MBBS, MD",
    registrationNumber: "MCI/12345",
    consultationRoom: "Room 1",
    consultationFee: 500,
    opdFee: 500,
    followUpFee: 300,
    emergencyFee: 800,
    yearsOfExperience: 12,
    dateOfBirth: "1985-03-15",
    active: true,
    publicListingEnabled: false,
    slug: "dr-asha",
  });

  assert.equal(parsed.success, true);
});

test("doctor avatar rendering uses authenticated image loading", () => {
  const hookSource = readWebAdminSource("hooks/useAuthenticatedImage.ts");
  const avatarSource = readWebAdminSource("components/doctor/DoctorAvatar.tsx");
  const detailSource = readWebAdminSource("pages/doctors/DoctorDetailPage.tsx");
  const identityCardSource = readWebAdminSource("components/doctor/DoctorIdentityCard.tsx");
  const availabilitySource = readWebAdminSource("pages/doctors/DoctorAvailabilityPage.tsx");
  const dayBoardSource = readWebAdminSource("pages/appointments/DayBoardPage.tsx");
  const restClientSource = readWebAdminSource("api/restClient.ts");

  assert.ok(hookSource.includes("fetchAuthenticatedBlob"));
  assert.ok(restClientSource.includes("Authorization: `Bearer ${token}`"));
  assert.ok(restClientSource.includes('"X-Tenant-Id"'));
  assert.ok(avatarSource.includes("useAuthenticatedImage(photoUrl)"));
  assert.ok(detailSource.includes("DoctorAvatar"));
  assert.ok(identityCardSource.includes('variant?: "avatar" | "compact" | "full"'));
  assert.ok(identityCardSource.includes("avatarSize?: number"));
  assert.ok(identityCardSource.includes("loading?: boolean"));
  assert.ok(identityCardSource.includes("updatedAt?: string"));
  assert.ok(availabilitySource.includes('variant="avatar"'));
  assert.ok(availabilitySource.includes("getDoctorProfile"));
  assert.ok(availabilitySource.includes("photoUrl: selectedDoctorProfile?.photoUrl || undefined"));
  assert.ok(dayBoardSource.includes('variant="avatar"'));
  assert.ok(dayBoardSource.includes("getDoctorProfile"));
  assert.ok(dayBoardSource.includes("photoUrl: selectedDoctorProfile?.photoUrl || undefined"));
});

test("doctor profile edit payload and labels keep dob-based validation", () => {
  const detailSource = readWebAdminSource("pages/doctors/DoctorDetailPage.tsx");

  assert.ok(detailSource.includes("specializationsInput: \"\""));
  assert.ok(detailSource.includes("inputValue={form.specializationsInput}"));
  assert.ok(detailSource.includes("onInputChange={(_, value) =>"));
  assert.ok(detailSource.includes("normalizeSpecializations(form.specializations, form.specializationsInput)"));
  assert.ok(detailSource.includes("normalizeText(form.mobile)"));
  assert.ok(detailSource.includes("normalizeText(form.qualification)"));
  assert.ok(detailSource.includes("normalizeText(form.registrationNumber)"));
  assert.ok(detailSource.includes("normalizeText(form.consultationRoom)"));
  assert.ok(detailSource.includes("normalizeNumber(form.opdFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.followUpFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.emergencyFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.yearsOfExperience)"));
  assert.ok(detailSource.includes("normalizeText(form.dateOfBirth)"));
  assert.ok(detailSource.includes("Age (derived)"));
  assert.ok(detailSource.includes("Date of Birth"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Mobile\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Specialization\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Qualification\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Registration Number\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"OPD Fee\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Follow-up Fee\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Emergency Fee\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Years of Experience\" required"));
  assert.ok(detailSource.includes("RequiredLabel text=\"Date of Birth\" required"));
  assert.ok(detailSource.includes("label={<RequiredLabel text=\"Public slug\" required={false} />}"));
  assert.ok(detailSource.includes("setFieldErrors(mapZodErrors(parsed.error))"));
  assert.ok(detailSource.includes("setError(firstZodError(parsed.error))"));
  assert.ok(detailSource.includes("updateDoctorProfileWithPhoto"));
  assert.ok(detailSource.includes("updateDoctorProfile("));
});
