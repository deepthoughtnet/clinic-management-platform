import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import { resolveLabRecommendationMatch } from "../src/pages/consultations/labRecommendationMatcher.js";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("exact catalog match selects the unique catalog test", () => {
  const resolution = resolveLabRecommendationMatch("Complete Blood Count", [
    { id: "cbc-1", testCode: "CBC", testName: "Complete Blood Count" },
  ]);

  assert.equal(resolution.matchState, "MAPPED");
  assert.equal(resolution.matchType, "EXACT");
  assert.equal(resolution.safeToAutoSelect, true);
  assert.deepEqual(resolution.matchedTestIds, ["cbc-1"]);
  assert.deepEqual(resolution.matchedCatalogNames, ["Complete Blood Count"]);
  assert.equal(resolution.displayLabel, "Complete Blood Count");
});

test("alias configured mapping selects the unique mapped test", () => {
  const resolution = resolveLabRecommendationMatch("Blood sugar", [
    { id: "glucose-1", testCode: "RBS", testName: "Random Blood Sugar" },
  ]);

  assert.equal(resolution.matchState, "MAPPED");
  assert.equal(resolution.matchType, "ALIAS");
  assert.equal(resolution.safeToAutoSelect, true);
  assert.deepEqual(resolution.matchedTestIds, ["glucose-1"]);
  assert.equal(resolution.displayLabel, "Random Blood Sugar");
});

test("ambiguous recommendation remains unmapped and does not auto-select", () => {
  const resolution = resolveLabRecommendationMatch("CBC", [
    { id: "mandatory-1", testCode: "CBC", testName: "Mandatory Code Test" },
    { id: "cbc-2", testCode: "CBC-2", testName: "Complete Blood Count" },
  ]);

  assert.equal(resolution.matchState, "AMBIGUOUS");
  assert.equal(resolution.safeToAutoSelect, false);
  assert.deepEqual(resolution.matchedTestIds, []);
  assert.equal(resolution.displayLabel, "CBC");
  assert.match(resolution.mappingLabel, /Ambiguous catalog matches/);
});

test("no match remains unmapped and keeps the recommendation visible for manual selection", () => {
  const resolution = resolveLabRecommendationMatch("Complete pending blood sample analysis", [
    { id: "mandatory-1", testCode: "CBC", testName: "Mandatory Code Test" },
    { id: "cbc-2", testCode: "CBC-2", testName: "Complete Blood Count" },
  ]);

  assert.equal(resolution.matchState, "UNMAPPED");
  assert.equal(resolution.safeToAutoSelect, false);
  assert.deepEqual(resolution.matchedTests, []);
  assert.deepEqual(resolution.matchedTestIds, []);
  assert.equal(resolution.displayLabel, "Complete pending blood sample analysis");
  assert.match(resolution.mappingLabel, /No safe catalog match/);
});

test("mapping helper is stateless across consultation contexts", () => {
  const first = resolveLabRecommendationMatch("CBC", [
    { id: "cbc-1", testCode: "CBC", testName: "Complete Blood Count" },
  ]);
  const second = resolveLabRecommendationMatch("Blood sugar", [
    { id: "glucose-1", testCode: "RBS", testName: "Random Blood Sugar" },
  ]);

  assert.equal(first.safeToAutoSelect, true);
  assert.equal(second.safeToAutoSelect, true);
  assert.deepEqual(first.matchedTestIds, ["cbc-1"]);
  assert.deepEqual(second.matchedTestIds, ["glucose-1"]);
});

test("consultation lab order submission still relies on doctor-reviewed selected ids", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("testIds: labOrderTestIds"));
  assert.ok(source.includes("Review &amp; Create Lab Order"));
  assert.ok(source.includes("No safe catalog match. Select a test manually."));
  assert.ok(source.includes("Ambiguous mapping"));
});
