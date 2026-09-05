import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import { humanizeClinicalReasoningActionType, sanitizeClinicalReasoningNarrative } from "../src/pages/consultations/clinicalReasoningActionLabel.js";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("clinical reasoning action codes are humanized for doctor-facing display", () => {
  assert.equal(humanizeClinicalReasoningActionType("ORDER_TEST"), "Prepare Order");
  assert.equal(humanizeClinicalReasoningActionType("ORDER_LAB"), "Prepare Order");
  assert.equal(humanizeClinicalReasoningActionType("CREATE_ORDER"), "Create Order");
  assert.equal(humanizeClinicalReasoningActionType("COMPLETE_PENDING_ORDER"), "Continue Order");
  assert.equal(humanizeClinicalReasoningActionType("REVIEW_EXISTING_RESULT"), "Review Result");
  assert.equal(humanizeClinicalReasoningActionType("SOMETHING_NEW"), "Recommended action");
  assert.equal(humanizeClinicalReasoningActionType(null), null);
  assert.equal(
    sanitizeClinicalReasoningNarrative("To rule out COVID-19 given respiratory symptoms and fever. ORDER_TEST"),
    "To rule out COVID-19 given respiratory symptoms and fever."
  );
  assert.equal(
    sanitizeClinicalReasoningNarrative("To assess hydration and electrolytes ORDER_LAB."),
    "To assess hydration and electrolytes."
  );
  assert.equal(sanitizeClinicalReasoningNarrative("Clinical note only"), "Clinical note only");
});

test("reasoning console no longer renders raw actionType codes", () => {
  const source = readSource("pages/admin/ReasoningTestConsolePage.tsx");
  assert.ok(source.includes("humanizeClinicalReasoningActionType"));
  assert.ok(!source.includes("item.actionType.replaceAll(\"_\", \" \")"));
});

test("consultation workspace does not render raw actionType tokens directly", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("sanitizeClinicalReasoningNarrative([test.name, test.reason, test.source].filter(Boolean).join(\" \"))"));
  assert.ok(!source.includes("item.actionType.replaceAll(\"_\", \" \")"));
  assert.ok(!source.includes("actionType.replaceAll(\"_\", \" \")"));
});
