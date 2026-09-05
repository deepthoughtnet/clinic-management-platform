import test from "node:test";
import assert from "node:assert/strict";
import {
  getSectionCountLabel,
  getSectionToggleLabel,
  getVisibleSectionItems,
  shouldShowSectionToggle,
} from "../src/components/clinical/patientIntelligenceSectionVisibility.js";

test("section visibility helpers preserve order and apply latest labs limit", () => {
  const items = ["a", "b", "c", "d", "e", "f", "g"];
  assert.equal(shouldShowSectionToggle(6, 6), false);
  assert.equal(shouldShowSectionToggle(items.length, 6), true);
  assert.deepEqual(getVisibleSectionItems(items, false, 6), ["a", "b", "c", "d", "e", "f"]);
  assert.deepEqual(getVisibleSectionItems(items, true, 6), items);
  assert.equal(getSectionToggleLabel(false), "View all");
  assert.equal(getSectionToggleLabel(true), "Show less");
  assert.equal(getSectionCountLabel("Latest Labs", 9), "Latest Labs (9)");
});

test("section visibility helpers preserve verified findings boundary and empty state", () => {
  const items = ["v1", "v2", "v3", "v4", "v5", "v6"];
  assert.equal(shouldShowSectionToggle(5, 5), false);
  assert.equal(shouldShowSectionToggle(6, 5), true);
  assert.deepEqual(getVisibleSectionItems(items, false, 5), ["v1", "v2", "v3", "v4", "v5"]);
  assert.deepEqual(getVisibleSectionItems(items, true, 5), items);
  assert.deepEqual(getVisibleSectionItems([], false, 5), []);
  assert.equal(getSectionCountLabel("Verified Findings", 0), "Verified Findings");
});

test("section visibility helpers preserve report trends boundary", () => {
  const items = ["t1", "t2", "t3", "t4"];
  assert.equal(shouldShowSectionToggle(3, 3), false);
  assert.equal(shouldShowSectionToggle(items.length, 3), true);
  assert.deepEqual(getVisibleSectionItems(items, false, 3), ["t1", "t2", "t3"]);
  assert.deepEqual(getVisibleSectionItems(items, true, 3), items);
  assert.equal(getSectionCountLabel("Report Trends", 5), "Report Trends (5)");
});
