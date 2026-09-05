import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("top bar patient routes use business labels instead of raw ids", () => {
  const source = readSource("layout/TopBar.tsx");

  assert.ok(source.includes('if (pathname === "/patients/new") return "Patients";'));
  assert.ok(source.includes('return "Patient Details";'));
  assert.ok(source.includes('^\\/patients\\/[^/]+\\/edit$'));
  assert.ok(source.includes('^\\/patients\\/[^/]+$'));
});

test("doctor header identity links the logged-in doctor to the read-only profile", () => {
  const source = readSource("layout/TopBar.tsx");

  assert.ok(source.includes('const isDoctor = auth.rolesUpper.includes("DOCTOR") || (auth.tenantRole || "").toUpperCase() === "DOCTOR";'));
  assert.ok(source.includes('const doctorProfilePath = isDoctor && auth.appUserId ? `/doctors/${auth.appUserId}/profile` : null;'));
  assert.ok(source.includes('state={{ returnTo: doctorProfileReturnTo }}'));
  assert.ok(source.includes('aria-label="View my doctor profile"'));
  assert.ok(source.includes('title="View my doctor profile"'));
  assert.ok(source.includes('component={RouterLink}'));
  assert.ok(source.includes('cursor: "pointer"'));
  assert.ok(source.includes('auth.username'));
});
