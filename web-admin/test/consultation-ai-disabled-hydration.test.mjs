import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace hydrates deterministic vitals and hides technical AI-disabled text", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const vitalsHydration = readSource("pages/consultations/vitalsHydration.ts");

  assert.ok(source.includes("getClinicalIntake"));
  assert.ok(source.includes("const [clinicalIntake, setClinicalIntake] = React.useState<ClinicalIntakeResponse | null>(null);"));
  assert.ok(source.includes("clinicalIntakeToVitalsSnapshot"));
  assert.ok(source.includes("mergeConsultationVitalsFromIntake"));
  assert.ok(source.includes("getClinicalIntake(auth.accessToken, auth.tenantId, consult.patientId, consult.appointmentId, consult.id).catch(() => null)"));
  assert.ok(source.includes("consultationValue?.appointmentId || null"));
  assert.ok(source.includes("consultationValue?.id || null"));
  assert.ok(source.includes("includeIntake"));
  assert.ok(source.includes("includeContext: false, includeIntake: false, keepLoadingState: false"));
  assert.ok(source.includes("clinicalContext?.intakeSummary?.latestVitals || clinicalIntakeToVitalsSnapshot(clinicalIntake)"));
  assert.ok(source.includes("const hydratedConsultationForm = mergeConsultationVitalsFromIntake("));
  assert.ok(source.includes("setConsultationForm(hydratedConsultationForm);"));
  assert.ok(source.includes("consultationFormRef.current = hydratedConsultationForm;"));
  assert.ok(source.includes("savedConsultationSnapshotRef.current = serializeConsultationForm(hydratedConsultationForm);"));
  assert.ok(source.includes("clinicalContext?.intakeSummary?.latestVitals"));
  assert.ok(!source.includes("hydrateConsultationVitalsFromIntake"));
  assert.ok(!source.includes("hydratedVitalsConsultationIdRef"));
  assert.ok(vitalsHydration.includes("assignIfBlank"));
  assert.ok(vitalsHydration.includes("bloodPressureSystolic"));
  assert.ok(vitalsHydration.includes("respiratoryRate"));
  assert.ok(vitalsHydration.includes("temperatureUnit"));
  assert.ok(source.includes("function normalizeAiStatusMessage("));
  assert.ok(source.includes("lowered.includes(\"ai_copilot\") || lowered.includes(\"module is disabled\") || lowered.includes(\"not enabled for this clinic\") || lowered.includes(\"disabled for this clinic\")"));
  assert.ok(source.includes("AIVA_DISABLED_DESCRIPTION"));
  assert.ok(source.includes("severity={aiStatusMessage === AIVA_DISABLED_DESCRIPTION ? \"info\" : \"warning\"}"));
  assert.ok(source.includes("role={aiStatusMessage === AIVA_DISABLED_DESCRIPTION ? \"status\" : undefined}"));
  assert.ok(source.includes("AI Clinical Assistance Unavailable"));
  assert.ok(!source.includes("Module is disabled: AI_COPILOT"));
});
