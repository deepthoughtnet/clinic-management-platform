import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace freeze keeps AI surfaces compact and reviewable", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const cardSource = readSource("components/clinical/PatientIntelligenceCard.tsx");

  assert.ok(source.includes("Review Context → Ask AI → Review Draft → Accept"));
  assert.ok(source.includes("Clinical Chat"));
  assert.ok(source.includes("Ready to complete"));
  assert.ok(source.includes("Review reasoning"));
  assert.ok(source.includes("Generate SOAP using AIVA or complete manually."));
  assert.ok(source.includes("Generate AI Consultation Draft"));
  assert.ok(source.includes("Generate reviewable AI drafts for multiple consultation sections."));
  assert.ok(source.includes("Use Generate AI Consultation Draft or a section action to start a review."));
  assert.ok(!source.includes("Use Generate Consultation Draft or a section action to start a review."));
  assert.ok(source.includes("aiAssistantEnabled ? <Button type=\"button\" size=\"small\" variant=\"contained\""));
  assert.ok(source.includes("onClick={() => void generateConsultationDraft(false)}"));
  assert.ok(source.includes("Manual diagnosis entry"));
  assert.ok(source.includes("AIVA Draft Review"));
  assert.ok(source.includes("StructuredTrendSummary"));
  assert.ok(source.includes("longitudinalClinicalContext?.labTrends"));
  assert.ok(source.includes("clinicalInterpretation"));
  assert.ok(source.includes("absoluteChange"));
  assert.ok(source.includes("verificationStatus"));
  assert.ok(source.includes("legacyTrends={clinicalContext?.labIntelligence.previousTrends || []}"));
  assert.ok(source.includes('fallbackMode="legacyOnly"'));
  assert.ok(source.includes('emptyStateLabel="No comparable reports yet"'));
  assert.ok(source.includes('disabled={!labReportPreview.length}'));
  assert.ok(!source.includes("newerValue - olderValue"));
  assert.ok(!source.includes("olderValue - newerValue"));
  assert.ok(cardSource.includes("No uploaded reports available."));
  assert.ok(cardSource.includes("No previous consultations available."));
});
