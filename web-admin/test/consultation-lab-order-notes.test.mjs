import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import { labConsultationOrderCreateSchema } from "../../frontend/packages/form-validation-kit/dist/index.js";
import { LAB_ORDER_NOTES_MAX_LENGTH, buildAiLabOrderNotes } from "../src/pages/consultations/labOrderNotesFormatter.js";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("ai-generated clinical order notes fit the lab order limit", () => {
  const notes = buildAiLabOrderNotes({
    generatedAt: "29 Aug 2026, 01:32 PM",
    reason: "Complete pending blood sample analysis because the clinical picture still needs correlation and follow-up confirmation.",
    supportingEvidence: [
      "Persistent fever for 5 days with escalating fatigue, poor intake, and borderline oxygen saturation on review.",
      "Pending blood work and prior trend review suggest additional confirmation is clinically useful.",
    ],
    suggestedPriority: "Routine",
    duplicateWarnings: [
      "Existing CBC order detected in the consultation context. Review before creating another order.",
    ],
    maxLength: LAB_ORDER_NOTES_MAX_LENGTH,
  });

  assert.equal(notes.length <= LAB_ORDER_NOTES_MAX_LENGTH, true);
  assert.ok(notes.includes("Prepared from AI Recommendation"));
  assert.ok(notes.includes("Doctor review and confirmation are required before any laboratory request is created.") || notes.length === LAB_ORDER_NOTES_MAX_LENGTH);
});

test("notes exactly at the maximum length are accepted by the shared consultation schema", () => {
  const result = labConsultationOrderCreateSchema.safeParse({
    patientId: "11111111-1111-4111-8111-111111111111",
    testIds: ["22222222-2222-4222-8222-222222222222"],
    notes: "A".repeat(250),
  });

  assert.equal(result.success, true);
});

test("consultation lab order review keeps validation and error rendering inside the modal", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const submitStart = source.indexOf("const submitLabOrder = async () => {");
  const submitEnd = source.indexOf("const runAiAction =", submitStart);
  const submitSnippet = source.slice(submitStart, submitEnd);

  assert.ok(source.includes("labOrderSubmissionInFlightRef.current"));
  assert.ok(source.includes("Notes must be 250 characters or fewer."));
  assert.ok(source.includes('role="alert"'));
  assert.ok(source.includes("labOrderReviewError"));
  assert.ok(source.includes("inputProps={{ maxLength: LAB_ORDER_NOTES_MAX_LENGTH }}"));
  assert.ok(source.includes("labOrderNotes.trim().length > LAB_ORDER_NOTES_MAX_LENGTH"));
  assert.ok(submitSnippet.includes("setLabOrderTestIds([]);"));
  assert.ok(submitSnippet.includes("setLabOrderNotes(\"\");"));
  assert.ok(submitSnippet.includes("setLabOrderAiPreparation(null);"));
  assert.ok(!submitSnippet.includes("catch (err) {\n      setLabOrderTestIds([]);"));
  assert.ok(!submitSnippet.includes("catch (err) {\n      setLabOrderNotes(\"\");"));
});
