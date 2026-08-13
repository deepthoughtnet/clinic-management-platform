import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("doctor directory availableToday filter uses projected summary state", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");

  assert.ok(page.includes('.filter((doctor) => (availableToday ? doctor.availableToday : true))'));
  assert.ok(page.includes("Available today"));
});

test("doctor profile no longer fabricates slot or schedule fallbacks", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const helpers = read("src/components/discovery/DoctorProfileExperiences.tsx");

  assert.ok(!page.includes('["Today", "Tomorrow"]'));
  assert.ok(!page.includes('["10:00 AM", "11:30 AM", "5:30 PM", "6:00 PM"]'));
  assert.ok(!page.includes("Mon\", hours: \"9:00 AM - 6:00 PM\""));
  assert.ok(helpers.includes("Live slot availability is shown when you continue to booking."));
  assert.ok(helpers.includes("Working hours not published on this profile yet."));
});
