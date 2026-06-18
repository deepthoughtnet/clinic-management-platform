import test from "node:test";
import assert from "node:assert/strict";

import { consultationSchema } from "../dist/index.js";

test("consultation schema accepts sparse notes", () => {
  const result = consultationSchema.safeParse({
    chiefComplaint: "Fever",
    diagnosis: "Viral illness",
    followUpDate: "2026-06-25",
    notes: "Observe at home",
  });
  assert.equal(result.success, true);
});

test("consultation schema rejects nothing for optional fields", () => {
  const result = consultationSchema.safeParse({});
  assert.equal(result.success, true);
});

test("consultation schema allows blank optional fields", () => {
  const result = consultationSchema.safeParse({
    chiefComplaint: "",
    diagnosis: "",
    followUpDate: "",
    notes: "",
  });
  assert.equal(result.success, true);
});
