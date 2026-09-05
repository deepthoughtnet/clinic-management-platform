import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("patient document upload dialog marks all mandatory fields and keeps notes optional", () => {
  const source = readSource("components/clinical/PatientDocumentUploadDialog.tsx");

  assert.ok(source.includes('label={<RequiredLabel text="Document type" required />}') || source.includes('<InputLabel><RequiredLabel text="Document type" required /></InputLabel>'));
  assert.ok(source.includes('label={<RequiredLabel text="Title" required />}'));
  assert.ok(source.includes('label={<RequiredLabel text="Report date" required />}'));
  assert.ok(source.includes('<InputLabel><RequiredLabel text="Upload source" required /></InputLabel>'));
  assert.ok(source.includes('<InputLabel><RequiredLabel text="Visibility" required /></InputLabel>'));
  assert.ok(source.includes('<RequiredLabel text="File" required />'));
  assert.ok(source.includes('label="Notes"'));
  assert.ok(source.includes('setError("Report date is required")'));
  assert.ok(source.includes('setError("File is required")'));
  assert.ok(source.includes('setError("Title is required")'));
  assert.ok(!source.includes('Notes *'));
});
