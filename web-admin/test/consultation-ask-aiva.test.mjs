import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace Ask AIVA enables only for consultation, patient, prompt, and busy state", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const start = source.indexOf("const canAskAiva = Boolean(");
  const end = source.indexOf("const currentAppointment = appointment;", start);
  const snippet = source.slice(start, end);
  assert.ok(start >= 0);
  assert.ok(snippet.includes("&& consultation"));
  assert.ok(snippet.includes("&& patient"));
  assert.ok(snippet.includes("&& aivaClinicalQuestion.trim()"));
  assert.ok(snippet.includes("&& !aiBusy"));
  assert.ok(source.includes("disabled={!canAskAiva}"));
  assert.ok(source.includes("onClick={() => void runAskAiva()}"));
  assert.ok(source.includes("aiConsultationAsk("));
  assert.ok(source.includes("AIVA_QUICK_PROMPTS"));
  assert.ok(source.includes("Clinical Chat"));
  assert.ok(source.includes("Ask anything about this consultation..."));
  assert.ok(source.includes("Add to SOAP"));
});
