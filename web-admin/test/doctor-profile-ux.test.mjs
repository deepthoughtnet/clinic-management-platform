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

test("doctor update schema accepts future-ready specialization and fee fields", () => {
  const parsed = doctorUpdateSchema.safeParse({
    doctorName: "Dr. Asha",
    specialization: "Dermatology",
    specializations: ["Dermatology", "Skin Care"],
    mobile: "9876543210",
    email: "doctor@example.com",
    registrationNumber: "REG-123",
    consultationRoom: "Room 1",
    qualification: "MBBS",
    consultationFee: 500,
    opdFee: 500,
    followUpFee: 300,
    emergencyFee: 800,
    yearsOfExperience: 12,
    age: 40,
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

test("doctor profile edit payload keeps live form values and specialization draft", () => {
  const detailSource = readWebAdminSource("pages/doctors/DoctorDetailPage.tsx");

  assert.ok(detailSource.includes("specializationsInput: \"\""));
  assert.ok(detailSource.includes("inputValue={form.specializationsInput}"));
  assert.ok(detailSource.includes("onInputChange={(_, value) => setForm"));
  assert.ok(detailSource.includes("normalizeSpecializations(form.specializations, form.specializationsInput)"));
  assert.ok(detailSource.includes("normalizeText(form.mobile)"));
  assert.ok(detailSource.includes("normalizeText(form.qualification)"));
  assert.ok(detailSource.includes("normalizeText(form.registrationNumber)"));
  assert.ok(detailSource.includes("normalizeText(form.consultationRoom)"));
  assert.ok(detailSource.includes("normalizeNumber(form.opdFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.followUpFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.emergencyFee)"));
  assert.ok(detailSource.includes("normalizeNumber(form.yearsOfExperience)"));
  assert.ok(detailSource.includes("normalizeNumber(form.age)"));
  assert.ok(detailSource.includes("specialization: specializations[0] || normalizeText(profile.specialization || \"\")"));
  assert.ok(detailSource.includes("publicListingEnabled: form.publicListingEnabled"));
  assert.ok(detailSource.includes("slug: normalizeText(form.slug)"));
  assert.ok(detailSource.includes("updateDoctorProfileWithPhoto"));
  assert.ok(detailSource.includes("updateDoctorProfile("));
});
