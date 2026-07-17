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
  assert.ok(source.includes("Patient timeline · ${timelinePreview.length} recent"));
  assert.ok(source.includes("const [timelinePreviewExpanded, setTimelinePreviewExpanded] = React.useState(false);"));
  assert.ok(source.includes("{timelinePreviewExpanded ? \"Hide timeline\" : \"Show timeline\"}"));
  assert.ok(source.includes("View Full History"));
  assert.ok(source.includes("Generate AI Consultation Draft"));
  assert.ok(source.includes("title=\"Generate reviewable AI drafts for multiple consultation sections.\""));
  assert.ok(source.includes("onClick={() => void generateConsultationDraft(false)}"));
  assert.ok(source.includes('label="AI Assist"'));
  assert.ok(source.includes("patientSnapshotFallback={patientSnapshotFallback}"));
  const rightRailStart = source.indexOf("ref={labOrderWorkflowRef}");
  const rightRailSnippet = source.slice(Math.max(0, rightRailStart - 260), rightRailStart + 520);
  assert.ok(rightRailSnippet.includes('alignSelf: "flex-start"'));
  assert.ok(rightRailSnippet.includes('overflow: "visible"'));
  assert.ok(!rightRailSnippet.includes('position: { xl: "sticky" }'));
  assert.ok(!rightRailSnippet.includes('overflowY: { xl: "auto" }'));
  assert.ok(source.includes('gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))"'));
});
