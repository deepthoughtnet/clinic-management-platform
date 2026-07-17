import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("consultation history tab uses compact overview cards and query-driven subviews", () => {
  const source = readSource("pages/consultations/ConsultationWorkspacePage.tsx");

  assert.ok(source.includes("function normalizeHistoryViewKey(value: string | null | undefined): HistoryViewKey"));
  assert.ok(source.includes('if (normalized === "consultation") return "consultations";'));
  assert.ok(source.includes('if (normalized === "report") return "documents";'));
  assert.ok(source.includes('if (normalized === "trend") return "trends";'));
  assert.ok(source.includes('return "timeline";'));
  assert.ok(source.includes('nextParams.set("historyView", "timeline");'));
  assert.ok(source.includes("const historyView = normalizeHistoryViewKey(searchParams.get(\"historyView\"));"));
  assert.ok(source.includes("{activeTab === 2 ? ("));
  assert.ok(source.includes("History subviews"));
  assert.ok(source.includes("Timeline → Consultations → Prescriptions → Reports → Trends"));
  assert.ok(source.includes("Unified Timeline"));
  assert.ok(source.includes("Show older entries"));
  assert.ok(source.includes("Recent Consultations"));
  assert.ok(source.includes("Recent Prescriptions"));
  assert.ok(source.includes("Important Trends"));
  assert.ok(source.includes("Reports &amp; Documents"));
  assert.ok(source.includes("collectDocumentHighlights"));
  assert.ok(source.includes("documentReviewFilter"));
  assert.ok(source.includes("Review extraction"));
  assert.ok(source.includes("View all"));
  assert.ok(source.includes("history-prescription-"));
});
