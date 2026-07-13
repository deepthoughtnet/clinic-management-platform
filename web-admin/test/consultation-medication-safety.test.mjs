import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation medication safety UI separates freshness from review state", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("function normalizeMedicationSafetyReview("));
  assert.ok(source.includes("function normalizeMedicationSafetyCoverageLabel("));
  assert.ok(source.includes("function formatCoverageStateLabel("));
  assert.ok(source.includes("function formatRenalFindingText("));
  assert.ok(source.includes("function formatRenalSourceLabels("));
  assert.ok(source.includes("const activeMedicationSafetyReview = React.useMemo("));
  assert.ok(source.includes("activeMedicationSafetyReview?.stale"));
  assert.ok(source.includes("activeMedicationSafetyReview?.readyForFinalization"));
  assert.ok(source.includes("Prescription changed after safety review. Run the safety check again."));
  assert.ok(source.includes("serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current"));
  assert.ok(source.includes("? await preserveViewport(() => persistPrescription())"));
  assert.ok(source.includes(": currentPrescription;"));
});

test("consultation medication safety coverage labels are clinician friendly", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("Exact duplicate: ${normalizeMedicationSafetyCoverageLabel"));
  assert.ok(source.includes("Ingredient: ${normalizeMedicationSafetyCoverageLabel"));
  assert.ok(source.includes("Class: ${normalizeMedicationSafetyCoverageLabel"));
  assert.ok(source.includes("Allergy: ${normalizeMedicationSafetyCoverageLabel"));
  assert.ok(source.includes("\"Evaluated\""));
  assert.ok(source.includes("\"Partial\""));
  assert.ok(source.includes("\"Unavailable\""));
  assert.ok(source.includes("Renal: ${formatCoverageStateLabel"));
});

test("consultation medication safety renal card hides uuid and normalizes display labels", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("Source: {renalSource.source} / Origin: {renalSource.origin}"));
  assert.ok(source.includes("Verification: {formatClinicalReasoningVerificationLabel(finding.verificationStatus) || finding.verificationStatus}"));
  assert.ok(source.includes("mL/min/1.73m²"));
  assert.ok(source.includes("isUuidLike(ref)"));
  assert.ok(source.includes("formatRenalFindingText(finding.summary)"));
  assert.ok(source.includes("formatRenalFindingText(item)"));
});

test("consultation medication safety finalized reviews render read-only persisted acknowledgements", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("const medicationSafetyReviewFinalized = activeMedicationSafetyReview?.decisionStatus === \"FINALIZED\" || prescription?.status === \"FINALIZED\";"));
  assert.ok(source.includes("Finalized safety review is read-only."));
  assert.ok(source.includes("Safety snapshot: Current at finalization"));
  assert.ok(source.includes("Acknowledged: {savedFindingReview?.acknowledged || savedFindingReview?.overrideApplied ? \"Yes\" : \"No\"}"));
  assert.ok(source.includes("Reviewed by: {activeMedicationSafetyReview?.reviewedByDisplayName || \"User unavailable\"}"));
  assert.ok(source.includes("Reviewed at: {compactDateTime(activeMedicationSafetyReview?.reviewedAt || null)}"));
  assert.ok(source.includes("medicationSafetyReviewFinalized ? null : ("));
  assert.ok(source.includes("getMedicationSafetyEvaluationForPrescription"));
  assert.ok(source.includes("getMedicationSafetyReviewForPrescription"));
  assert.ok(source.includes("reviewedByDisplayName"));
});

test("consultation medication safety continue draft shows explicit feedback", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("const [openingCorrectionDraft, setOpeningCorrectionDraft] = React.useState(false);"));
  assert.ok(source.includes("Opening correction draft..."));
  assert.ok(source.includes("Correction draft opened"));
  assert.ok(source.includes("Correction draft is already open"));
  assert.ok(source.includes("disabled={saving || openingCorrectionDraft}"));
  assert.ok(source.includes('{openingCorrectionDraft ? "Opening..." : "Continue Draft"}'));
});

test("consultation medication safety no-op save skips unnecessary prescription write", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("const serializedForm = serializePrescriptionForm(currentForm);"));
  assert.ok(source.includes("const prescriptionDirty = currentPrescription != null"));
  assert.ok(source.includes("if (currentPrescription && !prescriptionDirty)"));
  assert.ok(source.includes('setInfo("Prescription draft saved")'));
});
