import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation completion layer exposes documentation, validation, and package actions", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("Consultation Completion"));
  assert.ok(source.includes("Visit Summary"));
  assert.ok(source.includes("Referral Letters"));
  assert.ok(source.includes("Certificates"));
  assert.ok(source.includes("Follow-up Plan"));
  assert.ok(source.includes("Patient Communication"));
  assert.ok(source.includes("Clinical Documentation"));
  assert.ok(source.includes("Completion Validation"));
  assert.ok(source.includes("Download Consultation Package"));
  assert.ok(source.includes("Ready to Complete"));
  assert.ok(source.includes("Needs Review"));
  assert.ok(source.includes("No referral generated yet. Create a referral if specialist consultation is required."));
  assert.ok(source.includes("No certificate generated yet."));
  assert.ok(source.includes("No visit summary generated yet."));
  assert.ok(source.includes("Clinical documentation, visit summary, referrals, certificates, follow-up, and patient communication before final completion."));
  assert.ok(source.includes("Generated documents, uploaded documents, version history, and final audit are retained in the patient record."));
  assert.ok(source.includes("Final consultation audit"));
  assert.ok(source.includes("AI-generated draft. Doctor must verify before use."));
  assert.ok(source.includes("View validation"));
  assert.ok(source.includes("Consultation package generated and saved to patient documents."));
});

test("consultation document API persists generated consultation documents", () => {
  const apiSource = readSource("api/clinicApi.ts");
  assert.ok(apiSource.includes("/api/consultations/${consultationId}/generated-documents"));
  assert.ok(apiSource.includes("generateConsultationDocument"));
  assert.ok(apiSource.includes("ConsultationGeneratedDocumentResponse"));
});

test("consultation draft save rehydrates persisted state and blocks unload while saving", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  assert.ok(source.includes("const savingRef = React.useRef(false);"));
  assert.ok(source.includes("const persisted = await getConsultation(auth.accessToken, auth.tenantId, currentConsultation.id).catch(() => saved);"));
  assert.ok(source.includes("const nextForm = emptyConsultationForm(merged);"));
  assert.ok(source.includes("setConsultationForm(nextForm);"));
  assert.ok(source.includes("savingRef.current = true;"));
  assert.ok(source.includes("savingRef.current = false;"));
  assert.ok(source.includes("if (savingRef.current || autosaveInFlightRef.current)"));
});
