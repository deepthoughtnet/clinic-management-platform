import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab payment receipt flow reuses billing receipt component and exposes post-payment actions", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('ReceiptPrintDialog'));
  assert.ok(source.includes('type ReceiptPrintData'));
  assert.ok(source.includes('getClinicProfile'));
  assert.ok(source.includes('getBill'));
  assert.ok(source.includes('sendReceipt'));
  assert.ok(source.includes('View Receipt'));
  assert.ok(source.includes('Download Receipt'));
  assert.ok(source.includes('Print Receipt'));
  assert.ok(source.includes('Email'));
  assert.ok(source.includes('WhatsApp'));
  assert.ok(source.includes('Continue to Sample Collection'));
  assert.ok(source.includes('loadReceiptPrintData(true)'));
  assert.ok(source.includes('open={receiptPrintOpen || receiptPrintLoading}'));
});
