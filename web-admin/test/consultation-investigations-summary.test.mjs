import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation investigations content renders comparison once and omits duplicate placeholders", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");
  const investigationsStart = source.indexOf("{activeTab === 3 ? (");
  const investigationsEnd = source.indexOf("{activeTab === 4 ? (");

  assert.ok(investigationsStart >= 0, "live investigations block should exist");
  assert.ok(investigationsEnd > investigationsStart, "live investigations block should end before lab orders");

  const investigationsBlock = source.slice(investigationsStart, investigationsEnd);
  const reportComparisonHeadings = investigationsBlock.match(/Report Comparison/g) || [];

  assert.equal(reportComparisonHeadings.length, 1);
  assert.ok(investigationsBlock.includes("StructuredTrendSummary"));
  assert.ok(investigationsBlock.includes('structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}'));
  assert.ok(investigationsBlock.includes("No comparable reports are available yet."));
  assert.ok(investigationsBlock.includes('legacyTrends={[]}'));
  assert.ok(investigationsBlock.includes('latestReport={null}'));
  assert.ok(investigationsBlock.includes('summary={null}'));
  assert.ok(investigationsBlock.includes('onClick={() => setSelectedLabOrderId(selectedLabOrderId ? null : labOrders[0]?.id || null)}'));
  assert.ok(!investigationsBlock.includes("AI interpretation"));
  assert.ok(!investigationsBlock.includes("Trend summary"));
});
