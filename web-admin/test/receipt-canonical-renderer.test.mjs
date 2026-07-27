import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("admin receipt download actions reuse the receipt print renderer instead of the legacy PDF endpoint", () => {
  const pages = [
    "pages/appointments/QueuePage.tsx",
    "pages/billing/BillsPage.tsx",
    "pages/finance/PaymentsPage.tsx",
    "pages/lab/LabPage.tsx",
    "pages/vaccinations/VaccinationsPage.tsx",
  ];

  for (const page of pages) {
    const source = readSource(page);
    assert.ok(source.includes("ReceiptPrintDialog"), `${page} must render receipts through ReceiptPrintDialog`);
    assert.equal(source.includes("getReceiptPdf"), false, `${page} must not use the legacy receipt PDF endpoint`);
  }

  assert.ok(readSource("pages/appointments/QueuePage.tsx").includes("openReceiptPrintPreview(row, true)"));
  assert.ok(readSource("pages/billing/BillsPage.tsx").includes("openReceiptPreviewAction(resolved.receipt, resolved.payment, true)"));
  assert.ok(readSource("pages/finance/PaymentsPage.tsx").includes("loadReceiptPreview(row, true)"));
  assert.ok(readSource("pages/lab/LabPage.tsx").includes("loadReceiptPrintData(true)"));
  assert.ok(readSource("pages/lab/LabPage.tsx").includes("openOrderReceiptPreview(order, true)"));
  assert.ok(readSource("pages/vaccinations/VaccinationsPage.tsx").includes("openVaccinationReceiptPreview(true)"));
  assert.ok(readSource("pages/vaccinations/VaccinationsPage.tsx").includes("setReceiptAutoPrint(true)"));
});
