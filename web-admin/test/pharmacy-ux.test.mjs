import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("pharmacy navigation uses the updated labels and medicine master entry", () => {
  const navSource = readSource("layout/nav.ts");
  const topBarSource = readSource("layout/TopBar.tsx");
  const sidebarSource = readSource("layout/SidebarNav.tsx");
  assert.ok(navSource.includes('label: "Dispense Queue"'));
  assert.ok(navSource.includes('label: "Medicine Master"'));
  assert.ok(navSource.includes('label: "Prescription Register"'));
  assert.ok(navSource.includes('label: "Inventory"'));
  assert.ok(navSource.includes('label: "Procurement"'));
  assert.ok(navSource.includes('label: "Reconciliation"'));
  assert.ok(navSource.includes('label: "Reports & Audit"'));
  assert.ok(!navSource.includes("Supplier Bill / Stock Reconciliation"));
  assert.ok(topBarSource.includes('return "Procurement"'));
  assert.ok(topBarSource.includes('return "Reconciliation"'));
  assert.ok(sidebarSource.includes('group.key === "clinical" && isPharmacyOnlyTenant'));
  assert.ok(sidebarSource.includes('tenantRole === "PHARMACY_POS_USER"'));
});

test("prescription register gates dispensing to eligible statuses", () => {
  const source = readSource("pages/prescriptions/PrescriptionsPage.tsx");
  assert.ok(source.includes("Prescription Register"));
  assert.ok(source.includes("Recent prescriptions for view, print, and pharmacy handoff."));
  assert.ok(source.includes('status === "FINALIZED" || status === "CORRECTED"'));
  assert.ok(source.includes("Dispense is unavailable for"));
});

test("medicine master and inventory keep bulky editors collapsed by default", () => {
  const medicineSource = readSource("pages/pharmacy/MedicineMasterPage.tsx");
  const inventorySource = readSource("pages/inventory/InventoryPage.tsx");
  assert.ok(medicineSource.includes("setEditorOpen(false);"));
  assert.ok(medicineSource.includes("Advanced details"));
  assert.ok(medicineSource.includes("Advanced filters"));
  assert.ok(medicineSource.includes("setAdvancedFiltersOpen(false);"));
  assert.ok(medicineSource.includes("Missing barcode"));
  assert.ok(inventorySource.includes('React.useState<"add" | "transaction" | "transfer" | "count" | null>(null)'));
  assert.ok(inventorySource.includes("Read-only inventory access is active for this role."));
  assert.ok(inventorySource.includes("Returns & Write-Off History"));
  assert.ok(inventorySource.includes("Add Stock Batch"));
  assert.ok(inventorySource.includes("Receive stock before starting sales."));
});

test("pharmacy pos shows the blocked-state guidance before shift open", () => {
  const source = readSource("pages/pharmacy/PharmacyPosPage.tsx");
  assert.ok(source.includes("No POS shift is open. Open a shift before collecting payment or completing sale."));
  assert.ok(source.includes("Complete Sale is unavailable"));
  assert.ok(source.includes("Open Shift"));
  assert.ok(source.includes("Camera permission required. You can also upload an image."));
  assert.ok(source.includes("Supported files: PDF, JPG, PNG, WEBP up to 10 MB."));
  assert.ok(source.includes("Uploading"));
  assert.ok(source.includes("Use camera to scan medicine barcode."));
  assert.ok(source.includes("Use camera to scan QR code."));
  assert.ok(source.includes("Save this cart temporarily."));
  assert.ok(source.includes("View completed POS sales."));
  assert.ok(source.includes("Open POS shift before sale."));
  assert.ok(source.includes("Add at least one medicine."));
  assert.ok(source.includes("Enter paid amount."));
  assert.ok(source.includes("Paid Amount"));
  assert.ok(source.includes("Payment Mode"));
  assert.ok(source.includes("Hide unavailable"));
  assert.ok(source.includes("In stock first"));
  assert.ok(source.includes("LOW STOCK"));
  assert.ok(source.includes("Out of stock. Add stock before sale."));
  assert.ok(source.includes("No medicines are available for sale yet. Add medicines and receive stock before starting POS sale."));
  assert.ok(source.includes("Opening cash"));
  assert.ok(!source.includes('bottom: { lg: 16 }'));
});

test("dispensing queue explains hidden results when filters suppress rows", () => {
  const source = readSource("pages/pharmacy/DispensingPage.tsx");
  assert.ok(source.includes("Records exist but are hidden by current filters."));
  assert.ok(source.includes("Dispense Queue"));
  assert.ok(source.includes("isActiveDispenseStatus"));
  assert.ok(source.includes('label="Out of stock"'));
  assert.ok(source.includes('setQueueFilter("OUT_OF_STOCK")'));
});

test("scanner and operations pages expose fallback and read-only pharmacy guidance", () => {
  const scannerSource = readSource("components/pharmacy/CodeScannerDialog.tsx");
  const operationsSource = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(scannerSource.includes("Camera permission required. You can also upload an image."));
  assert.ok(scannerSource.includes("Upload image"));
  assert.ok(scannerSource.includes("supportsSecureLocalCamera"));
  assert.ok(operationsSource.includes("Read-only procurement and reconciliation access is active for this role."));
  assert.ok(operationsSource.includes("disabled={!canManageOperations || saving}"));
  assert.ok(operationsSource.includes('label="Supplier Bill Reconciliation"'));
  assert.ok(operationsSource.includes('label="Physical Count"'));
  assert.ok(operationsSource.includes('label="Stock Adjustments"'));
  assert.ok(operationsSource.includes('label="Approval Review"'));
  assert.ok(operationsSource.includes("Manage supplier purchasing, invoices, and goods receipt for pharmacy stock."));
  assert.ok(operationsSource.includes("Review stock differences, supplier bill variances, and approval workflows."));
});

test("dashboard onboarding copy and quick actions guide a brand new tenant", () => {
  const source = readSource("pages/pharmacy/PharmacyDashboardPage.tsx");
  assert.ok(source.includes("Pharmacy Setup"));
  assert.ok(source.includes("Complete these steps to start selling."));
  assert.ok(source.includes("setupProgress"));
  assert.ok(source.includes('navigate("/pharmacy/procurement")'));
  assert.ok(source.includes('navigate("/pharmacy/reconciliation")'));
  assert.ok(source.includes("Add Medicine"));
  assert.ok(source.includes("Receive Stock"));
  assert.ok(source.includes("Open POS"));
  assert.ok(source.includes("Reports & Audit"));
  assert.ok(source.includes("Create staff"));
  assert.ok(source.includes("Open POS shift"));
});

test("procurement and reconciliation show lightweight workflow guidance", () => {
  const source = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(source.includes("Workflow guidance"));
  assert.ok(source.includes("Supplier → Purchase Order → Invoice → Goods Receipt → Stock Added"));
  assert.ok(source.includes("Create Session → Upload/Enter Count → Review Differences → Submit → Approve → Posted"));
  assert.ok(source.includes("No purchase orders yet."));
  assert.ok(source.includes("No reconciliation sessions yet."));
  assert.ok(source.includes("Start Reconciliation"));
});
