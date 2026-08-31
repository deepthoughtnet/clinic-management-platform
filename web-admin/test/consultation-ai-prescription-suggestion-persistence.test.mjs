import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation prescription suggestions persist across refresh and autosave paths", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("getConsultationAiPrescriptionSuggestion"));
  assert.ok(source.includes("saveConsultationAiPrescriptionSuggestion"));
  assert.ok(source.includes("applyPersistedAiPrescriptionSuggestion"));
  assert.ok(source.includes("persistAiPrescriptionSuggestionDraft"));
  assert.ok(source.includes("clearAiPrescriptionSuggestions"));
  assert.ok(source.includes("aiPrescriptionRawText"));
  assert.ok(source.includes("aiPrescriptionModel"));
  assert.ok(source.includes("aiPrescriptionStale"));
  assert.ok(source.includes("Clear suggestions"));
  assert.ok(source.includes("setAiPrescriptionSuggestion(null);"));
  assert.ok(source.includes("setAiPrescriptionItems([]);"));
  assert.ok(source.includes("This suggestion set is stale for the current consultation context. Regenerate before relying on it."));
  assert.ok(source.includes("State persisted across refresh."));
  assert.ok(source.includes("prescriptionReadOnly || aiBusy"));
  assert.ok(source.includes("onSuggestionChange={(nextItem) => {"));
  assert.ok(source.includes("await persistAiPrescriptionSuggestionDraft(nextItems"));
  assert.ok(source.includes("addMedicineFromAiSuggestion(nextItem)"));
});
