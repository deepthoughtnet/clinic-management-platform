import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("queue collects consultation fees in place and exposes receipt actions", () => {
  const source = readSource("pages/appointments/QueuePage.tsx");

  assert.ok(source.includes('openFeeDialog(row, "collect")'));
  assert.ok(source.includes("ReceiptPrintDialog"));
  assert.ok(source.includes("Continue to Check-in"));
  assert.ok(source.includes("Download Receipt PDF"));
  assert.ok(source.includes("Email Receipt"));
  assert.ok(source.includes("WhatsApp Receipt"));
  assert.ok(source.includes("MoreVertRoundedIcon"));
  assert.ok(source.includes("listBillPayments"));
  assert.ok(source.includes("getReceiptPdf"));
  assert.ok(source.includes("sendReceipt"));
  assert.ok(source.includes("receiptRecordsByAppointmentId"));
  assert.ok(source.includes("const loadReceiptRecords = React.useCallback"));
  assert.ok(source.includes("const loadQueueSnapshot = React.useCallback"));
  assert.ok(source.includes("setReceiptRecordsByAppointmentId(receiptRecords);"));
  assert.ok(source.includes("Payment successful"));
  assert.equal(source.includes("collectConsultationFee=1"), false);
  assert.equal(source.includes("returnTo=/queue"), false);
});
