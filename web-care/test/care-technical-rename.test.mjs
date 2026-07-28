import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("Care package metadata uses the web-care application name", () => {
  const pkg = JSON.parse(read("package.json"));
  const lock = JSON.parse(read("package-lock.json"));

  assert.equal(pkg.name, "web-care");
  assert.equal(lock.name, "web-care");
  assert.equal(lock.packages[""].name, "web-care");
});

test("Phase 3B preserves patient route URLs and legacy Discover redirects", () => {
  const app = read("src/App.tsx");

  for (const route of [
    "/patient/login",
    "/patient/register",
    "/patient/dashboard",
    "/patient/book-appointment",
    "/patient/appointments",
    "/patient/prescriptions",
    "/patient/bills",
    "/patient/notifications",
    "/patient/lab",
    "/patient/careai",
    "/patient/profile",
  ]) {
    assert.ok(app.includes(`path="${route}"`), `${route} route should remain unchanged`);
  }

  assert.ok(app.includes('path="/doctors" element={<LegacyDiscoverRedirectPage />}'));
  assert.ok(app.includes('path="/clinics" element={<LegacyDiscoverRedirectPage />}'));
  assert.ok(app.includes('path="/specialities" element={<LegacyDiscoverRedirectPage />}'));
});
