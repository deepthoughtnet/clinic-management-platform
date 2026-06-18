import test from "node:test";
import assert from "node:assert/strict";

import {
  getCitySuggestions,
  getCountrySuggestions,
  getIndiaCitySuggestions,
  getIndiaStateSuggestions,
  normalizeLocationText,
} from "../dist/index.js";

test("country prefix match", () => {
  assert.deepEqual(getCountrySuggestions("Ind"), ["India"]);
});

test("city prefix match", () => {
  assert.deepEqual(getIndiaCitySuggestions("Pun"), ["Pune"]);
});

test("case-insensitive matching", () => {
  assert.deepEqual(getIndiaStateSuggestions("mah"), ["Maharashtra"]);
  const citySuggestions = getCitySuggestions("mUm", "India");
  assert.equal(citySuggestions[0], "Mumbai");
  assert.ok(citySuggestions.includes("Navi Mumbai"));
});

test("limit behavior", () => {
  const suggestions = getCountrySuggestions("", 3);
  assert.equal(suggestions.length, 3);
});

test("no-match returns empty array", () => {
  assert.deepEqual(getCitySuggestions("zzzz", "India"), []);
});

test("normalizeLocationText trims and collapses spaces", () => {
  assert.equal(normalizeLocationText("  New   Delhi  "), "new delhi");
});
