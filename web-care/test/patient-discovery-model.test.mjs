import test from "node:test";
import assert from "node:assert/strict";

import {
  dedupeByExplicitKey,
  normalizeDiscoveryText,
  normalizePublicClinicDisplayName,
  resolveTelHref,
} from "../src/pages/patient/patientDiscoveryModel.js";

test("dedupeByExplicitKey collapses only explicit duplicate keys", () => {
  const items = [
    { id: "a", key: "provider-1", label: "First" },
    { id: "b", key: "provider-1", label: "Duplicate" },
    { id: "c", key: "provider-2", label: "Second" },
    { id: "d", key: "", label: "Unkeyed one" },
    { id: "e", key: null, label: "Unkeyed two" },
  ];

  const deduped = dedupeByExplicitKey(items, (item) => item.key);

  assert.deepEqual(deduped.map((item) => item.id), ["a", "c", "d", "e"]);
});

test("normalizePublicClinicDisplayName removes association labels only", () => {
  assert.equal(normalizePublicClinicDisplayName("Primary"), null);
  assert.equal(normalizePublicClinicDisplayName("  Primary  "), null);
  assert.equal(normalizePublicClinicDisplayName("Jeevanam Clinic"), "Jeevanam Clinic");
});

test("normalizeDiscoveryText and resolveTelHref keep search and call links safe", () => {
  assert.equal(normalizeDiscoveryText("  Care   Network  "), "care network");
  assert.equal(resolveTelHref(" +91 98765 01207 "), "tel:+919876501207");
  assert.equal(resolveTelHref(""), null);
});
