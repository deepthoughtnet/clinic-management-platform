import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("converted lead detail renders historical mode and conversion summary", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");

  assert.ok(page.includes('lead.status === "CONVERTED" ? "View Details" : "View/Edit"'));
  assert.ok(page.includes('Converted Lead Details'));
  assert.ok(page.includes('Converted to Patient'));
  assert.ok(page.includes('Patient •'));
  assert.ok(page.includes('Converted:'));
  assert.ok(page.includes('Converted by:'));
  assert.ok(page.includes('Read-only after conversion.'));
  assert.ok(page.includes('canPersistLeadForm'));
  assert.ok(page.includes('convertedLeadCanSave'));
  assert.ok(page.includes('convertedLeadDirty'));
  assert.ok(page.includes('disabled={saving || (convertedLead ? !convertedLeadDirty : false)}'));
  assert.ok(page.includes('updateCarePilotConvertedLeadMetadata'));
  assert.ok(page.includes('buildConvertedLeadMetadataPayload'));
  assert.ok(page.includes('Converted lead marketing details saved.'));
  assert.ok(page.includes('Unable to save converted lead details.'));
  assert.ok(page.includes('canAddLeadNote'));
  assert.ok(page.includes('View Consultation History'));
  assert.ok(page.includes('Open Patient'));
  assert.ok(page.includes('renderConvertedPatientActions'));
  assert.ok(!page.includes('lead.convertedPatientId && canOpenPatient ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null'));
});
