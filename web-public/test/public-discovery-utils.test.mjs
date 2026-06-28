import test from "node:test";
import assert from "node:assert/strict";

import {
  discoveryEmptyMessage,
  matchesDiscoveryQuery,
  scoreDiscoveryLocation,
} from "../src/utils/publicDiscovery.js";

test("discovery search matches doctor, clinic, speciality, symptom, and city text", () => {
  assert.equal(
    matchesDiscoveryQuery(
      ["Dr Neha Mehta", "Cardiology", "Sunrise Clinic", "Baner", "Pune", "Chest pain"],
      "neha pune chest",
    ),
    true,
  );
  assert.equal(
    matchesDiscoveryQuery(
      ["Dr Neha Mehta", "Cardiology", "Sunrise Clinic", "Baner", "Pune", "Chest pain"],
      "dermatology",
    ),
    false,
  );
});

test("discovery location scoring prefers the selected city", () => {
  const puneScore = scoreDiscoveryLocation("Pune", "Baner", "Pune");
  const bhopalScore = scoreDiscoveryLocation("Bhopal", "Kolar", "Pune");
  assert.ok(puneScore > bhopalScore);
});

test("discovery empty state guides patients to adjust location or search term", () => {
  assert.equal(
    discoveryEmptyMessage({ query: "neha", selectedLocation: "Pune", defaultMessage: "No profiles yet." }),
    "No matching results found for Pune. Try changing location or search term.",
  );
  assert.equal(
    discoveryEmptyMessage({ query: "", selectedLocation: "", defaultMessage: "No profiles yet." }),
    "No profiles yet.",
  );
});
