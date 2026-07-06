import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation workspace lab workflow stays compact and review driven", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("documentBusinessStatusLabel"));
  assert.ok(source.includes("Review History → Select Tests → Check Duplicates → Confirm Order → Track Report"));
  assert.ok(source.includes("Create Order → Payment → Sample → Result → Publish"));
  assert.ok(source.includes("Recommended Tests"));
  assert.ok(source.includes("Review &amp; Create Lab Order"));
  assert.ok(source.includes("Duplicate / recent warnings"));
  assert.ok(source.includes("Report / Test History"));
  assert.ok(source.includes('label={isPublishedLabDocument(row) ? "Published" : row.visibility}'));
  assert.ok(source.includes('isPublishedLabDocument(row) ? null : <Chip size="small" variant="outlined" label={row.verificationStatus} />'));
  assert.ok(source.includes("AI-assisted trend summary"));
  assert.ok(source.includes("Lab Order Details"));
  assert.ok(source.includes("No previous reports available. Upload an external report or order investigations to begin tracking trends."));
});
