import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("doctor identity cards navigate to the read-only profile route", () => {
  const identityCardSource = readWebAdminSource("components/doctor/DoctorIdentityCard.tsx");
  const appSource = readWebAdminSource("app/App.tsx");
  const dayBoardSource = readWebAdminSource("pages/appointments/DayBoardPage.tsx");
  const availabilitySource = readWebAdminSource("pages/doctors/DoctorAvailabilityPage.tsx");
  const usersRolesSource = readWebAdminSource("pages/settings/UsersRolesPage.tsx");
  const billsSource = readWebAdminSource("pages/billing/BillsPage.tsx");

  assert.ok(identityCardSource.includes("profileTo = doctorId && !placeholder ? `/doctors/${doctorId}/profile` : null;"));
  assert.ok(identityCardSource.includes('aria-label={`View profile for ${doctorDisplayName(resolvedName, "Doctor")}`}'));
  assert.ok(identityCardSource.includes("component={RouterLink}"));
  assert.ok(identityCardSource.includes("cursor: \"pointer\""));
  assert.ok(identityCardSource.includes("state={{ returnTo }}"));
  assert.ok(appSource.includes('path="/doctors/:id/profile"'));
  assert.ok(appSource.includes("DoctorProfilePage"));
  assert.ok(dayBoardSource.includes("doctorProfileContext"));
  assert.ok(dayBoardSource.includes("profileReturnTo={{"));
  assert.ok(dayBoardSource.includes("doctorUserIdFromQuery || restoreContext?.doctorUserId || \"\""));
  assert.ok(dayBoardSource.includes("restoreContext?.date || getClinicDateKey(\"Asia/Kolkata\")"));
  assert.ok(dayBoardSource.includes("if (doctorUserIdFromQuery)"));
  assert.ok(availabilitySource.includes("doctorProfileContext"));
  assert.ok(availabilitySource.includes("profileReturnTo={{"));
  assert.ok(availabilitySource.includes("restoreContext?.doctorUserId || \"\""));
  assert.ok(availabilitySource.includes("restoreContext?.date || getClinicDateKey(\"Asia/Kolkata\")"));
  assert.ok(availabilitySource.includes("restoreContext?.viewMode || \"day\""));
  assert.ok(usersRolesSource.includes("navigate(`/doctors/${user.appUserId}/profile`, {"));
  assert.ok(usersRolesSource.includes("returnTo: {"));
  assert.ok(usersRolesSource.includes("pathname: location.pathname"));
  assert.ok(billsSource.includes("navigate(`/doctors/${consultationDoctorUserProfileId}/profile`, {"));
  assert.ok(billsSource.includes("returnTo: {"));
  assert.ok(billsSource.includes("pathname: location.pathname"));
});

test("doctor profile page stays read-only by default and gates Edit Profile", () => {
  const profileSource = readWebAdminSource("pages/doctors/DoctorProfilePage.tsx");

  assert.ok(profileSource.includes("Edit Profile"));
  assert.ok(profileSource.includes("Read-only profile"));
  assert.ok(profileSource.includes("canEditProfile(auth, profile)"));
  assert.ok(profileSource.includes("resolveBackTarget(location.state)"));
  assert.ok(profileSource.includes("const pathname = returnTo?.pathname;"));
  assert.ok(profileSource.includes("isSafeInternalPath(pathname)"));
  assert.ok(profileSource.includes("navigate(`${backTarget.pathname}${backTarget.search || \"\"}${backTarget.hash || \"\"}`, { replace: true, state: backTarget.state })"));
  assert.ok(profileSource.includes('label="Mobile"'));
  assert.ok(profileSource.includes('value={profile.mobile || ""}'));
  assert.ok(profileSource.includes("disabled"));
  assert.ok(profileSource.includes('label="Specialization(s)"'));
  assert.ok(profileSource.includes('label="Qualification(s)"'));
  assert.ok(profileSource.includes('profile.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"'));
  assert.ok(profileSource.includes('profile.active ? "Active" : "Inactive"'));
});
