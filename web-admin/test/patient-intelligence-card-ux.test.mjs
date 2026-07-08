import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "src", ...relPath.split("/")), "utf8");
}

test("patient intelligence card groups longitudinal findings and exposes review/source actions", () => {
  const card = readSource("components/clinical/PatientIntelligenceCard.tsx");
  const consultationPage = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(card.includes("AI Extracted Summary"));
  assert.ok(card.includes("Conditions"));
  assert.ok(card.includes("Latest Labs"));
  assert.ok(card.includes("Risk Flags"));
  assert.ok(card.includes("Document"));
  assert.ok(card.includes("Doctor verification required before becoming permanent patient history."));
  assert.ok(card.includes("Review"));
  assert.ok(card.includes("View Source"));
  assert.ok(card.includes("Very High"));
  assert.ok(card.includes("High"));
  assert.ok(card.includes("Medium"));
  assert.ok(card.includes("Low"));
  assert.ok(card.includes("join(\"-\")"));
  assert.ok(consultationPage.includes("onViewSourceDocument={(documentId) => void openClinicalDocumentById(documentId)}"));
});
