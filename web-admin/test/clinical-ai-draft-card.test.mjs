import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("clinical ai draft card exposes consistent status and disclaimer copy", () => {
  const source = readSource("components/clinical/ClinicalAiDraftCard.tsx");
  assert.ok(source.includes("AI-generated draft. Doctor must verify before use."));
  assert.ok(source.includes("sourceSummaryLabel?: string;"));
  assert.ok(source.includes("sourceSummaryLabel = \"Context\""));
  assert.ok(source.includes("Pending"));
  assert.ok(source.includes("Accepted"));
  assert.ok(source.includes("Edited"));
  assert.ok(source.includes("Rejected"));
  assert.ok(source.includes("Save edit"));
  assert.ok(source.includes("Rejected draft is retained until you clear all drafts."));
  assert.ok(source.includes("Reject"));
  assert.ok(source.includes("Copy"));
});
