import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace ui refinement keeps advice discoverable and removes legacy row", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(!source.includes("Legacy actions"));
  assert.ok(!source.includes("Run the legacy explain flow"));
  assert.ok(!source.includes("Generate the legacy diagnosis suggestion"));
  assert.ok(source.includes('runAiAction("diagnosis")'));
  assert.ok(source.includes("Suggest diagnosis"));
  assert.ok(source.includes("runClinicalReasoningAction()"));
  assert.ok(source.includes("Open Advice"));
  assert.ok(source.includes("adviceSectionRef"));
  assert.ok(source.includes("adviceInputRef"));
  assert.ok(source.includes("revealAdviceSection"));
  assert.ok(source.includes("appendAdviceAndReveal"));
  assert.ok(source.includes('label="Advice"'));
  assert.ok(source.includes("consultationForm.advice"));
  assert.ok(source.includes("Add to Advice"));
});
