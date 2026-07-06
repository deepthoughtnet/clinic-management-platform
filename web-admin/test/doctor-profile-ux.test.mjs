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
  const restClientSource = readWebAdminSource("api/restClient.ts");

  assert.ok(hookSource.includes("fetchAuthenticatedBlob"));
  assert.ok(restClientSource.includes("Authorization: `Bearer ${token}`"));
  assert.ok(restClientSource.includes('"X-Tenant-Id"'));
  assert.ok(avatarSource.includes("useAuthenticatedImage(photoUrl)"));
  assert.ok(detailSource.includes("DoctorAvatar"));
  assert.ok(identityCardSource.includes("DoctorAvatar"));
});
