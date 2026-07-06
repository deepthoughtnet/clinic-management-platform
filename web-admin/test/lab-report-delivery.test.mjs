import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab report delivery UI exposes action menu and publish success panel", () => {
  const source = readSource("pages/lab/LabPage.tsx");

  assert.ok(source.includes("ReportActionMenuButton"));
  assert.ok(source.includes("View Report"));
  assert.ok(source.includes("Print Report"));
  assert.ok(source.includes("Download PDF"));
  assert.ok(source.includes("Email"));
  assert.ok(source.includes("WhatsApp"));
  assert.ok(source.includes("Share Link"));
  assert.ok(source.includes("Report Published"));
  assert.ok(source.includes("Delivery channels"));
  assert.ok(source.includes("Delivery audit is recorded on the published report record"));
  assert.ok(source.includes("reportDeliveryHistorySummary"));
  assert.ok(source.includes("Portal viewed"));
  assert.ok(source.includes("PDF downloaded"));
  assert.ok(source.includes("Printed"));
  assert.ok(source.includes("recordLabOrderReportDeliveryAction"));
});

test("lab report pdf metadata includes approval, publication, verification, and signature fields", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  const backend = fs.readFileSync(path.join(process.cwd(), "backend", "api", "api-bff", "src", "main", "java", "com", "deepthoughtnet", "clinic", "api", "lab", "service", "LabService.java"), "utf8");

  assert.ok(backend.includes("Approved By"));
  assert.ok(backend.includes("Published By"));
  assert.ok(backend.includes("QR / Verification URL"));
  assert.ok(backend.includes("Digital signature"));
  assert.ok(backend.includes("Page \" + (i + 1) + \" of \" + totalPages"));
  assert.ok(backend.includes("recordDeliveryChannelAudits"));
  assert.ok(source.includes("reportDeliveryHistory"));
});
