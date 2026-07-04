import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("pharmacy navigation uses the updated labels and medicine master entry", () => {
  const navSource = readSource("layout/nav.ts");
  const topBarSource = readSource("layout/TopBar.tsx");
  const sidebarSource = readSource("layout/SidebarNav.tsx");
  const appSource = readSource("app/App.tsx");
  assert.ok(navSource.includes('label: "Dispense Queue"'));
  assert.ok(navSource.includes('label: "Medicine Master"'));
  assert.ok(topBarSource.includes('pathname === "/pharmacy/procurement"'));
  assert.ok(sidebarSource.includes('const prefixMatchPaths = new Set(['));
  assert.ok(appSource.includes('function PathnameKeyedRoute'));
  assert.ok(appSource.includes('<PharmacyProcurementPage />'));
  assert.ok(appSource.includes('<PharmacyReconciliationPage />'));
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
  assert.ok(medicineSource.includes("Medicine Workflow"));
  assert.ok(inventorySource.includes("Inventory Workflow"));
  assert.ok(inventorySource.includes("No inventory available."));
});

test("pharmacy pos shows the blocked-state guidance before shift open", () => {
  const source = readSource("pages/pharmacy/PharmacyPosPage.tsx");
  assert.ok(source.includes("Open POS shift before sale."));
  assert.ok(source.includes("Open Shift"));
  assert.ok(source.includes("Camera permission required. You can also upload an image."));
  assert.ok(source.includes("Out of stock. Add stock before sale."));
  assert.ok(source.includes("No medicines are available for sale yet. Add medicines and receive stock before starting POS sale."));
  assert.ok(source.includes("Opening cash"));
  assert.ok(source.includes("Open Shift before checkout"));
});

test("dispensing queue explains hidden results when filters suppress rows", () => {
  const source = readSource("pages/pharmacy/DispensingPage.tsx");
  assert.ok(source.includes("Records exist but are hidden by current filters."));
  assert.ok(source.includes("Dispense Queue"));
  assert.ok(source.includes('label="Out of stock"'));
});

test("scanner and operations pages expose fallback and read-only pharmacy guidance", () => {
  const scannerSource = readSource("components/pharmacy/CodeScannerDialog.tsx");
  const operationsSource = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(scannerSource.includes("Camera permission required. You can also upload an image."));
  assert.ok(scannerSource.includes("Upload image"));
  assert.ok(operationsSource.includes("Create a supplier before creating a Purchase Order."));
  assert.ok(operationsSource.includes("No purchase orders are available for goods receipt."));
  assert.ok(operationsSource.includes("No purchase orders are available for invoice matching."));
  assert.ok(operationsSource.includes("No medicines are available for reconciliation."));
  assert.ok(operationsSource.includes("No stock batches are available for reconciliation."));
});

test("dashboard onboarding copy and quick actions guide a brand new tenant", () => {
  const source = readSource("pages/pharmacy/PharmacyDashboardPage.tsx");
  assert.ok(source.includes("Pharmacy Setup"));
  assert.ok(source.includes("Complete these steps to start selling."));
  assert.ok(source.includes("setupProgress"));
  assert.ok(source.includes('navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier")'));
  assert.ok(source.includes('navigate("/pharmacy/procurement?workspace=purchase-orders")'));
  assert.ok(source.includes('navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct")'));
  assert.ok(source.includes('navigate("/pharmacy/reconciliation")'));
  assert.ok(source.includes("Add Medicine"));
  assert.ok(source.includes("Add Supplier"));
  assert.ok(source.includes("Create PO"));
  assert.ok(source.includes("Receive via Procurement"));
  assert.ok(source.includes("Direct Goods Receipt"));
  assert.ok(source.includes("Open POS"));
  assert.ok(source.includes("Reports & Audit"));
  assert.ok(source.includes("Create staff"));
  assert.ok(source.includes("Open POS shift"));
});

test("procurement and reconciliation show lightweight workflow guidance", () => {
  const source = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(source.includes("Workflow guidance"));
  assert.ok(source.includes("Supplier → Purchase Order → Generate / Send PO → Invoice → Goods Receipt → Stock Added"));
  assert.ok(source.includes("Medicine Master"));
  assert.ok(source.includes("Receive via Procurement / Direct Goods Receipt"));
  assert.ok(source.includes("Create Session → Upload/Enter Count → Review Differences → Submit → Approve → Posted"));
  assert.ok(source.includes("No purchase orders yet."));
  assert.ok(source.includes("No reconciliation sessions yet."));
  assert.ok(source.includes("Start Reconciliation"));
  assert.ok(source.includes("Generate / Send PO"));
});

test("procurement workspace routing stays local and canonical", () => {
  const appSource = readSource("app/App.tsx");
  const operationsSource = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  const procurementSource = readSource("pages/pharmacy/PharmacyProcurementPage.tsx");
  const reconciliationSource = readSource("pages/pharmacy/PharmacyReconciliationPage.tsx");
  assert.ok(appSource.includes('path="/pharmacy/procurement"'));
  assert.ok(appSource.includes('path="/pharmacy/reconciliation"'));
  assert.ok(appSource.includes('path="/pharmacy/operations"'));
  assert.ok(appSource.includes('return <Navigate to={target} replace />;'));
  assert.ok(procurementSource.includes('workspace=suppliers&focus=supplier'));
  assert.ok(reconciliationSource.includes('export default function PharmacyReconciliationPage() {'));
  assert.ok(operationsSource.includes('type PharmacyOperationsPageProps = {'));
});

test("procurement and reconciliation enforce dependency-aware empty states", () => {
  const source = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(source.includes("No suppliers have been created."));
  assert.ok(source.includes("Create your first supplier before creating Purchase Orders."));
  assert.ok(source.includes("Create a supplier before creating a Purchase Order."));
  assert.ok(source.includes("Create a supplier before creating goods receipt."));
  assert.ok(source.includes("No purchase orders are available for invoice matching."));
  assert.ok(source.includes("No medicines are available for reconciliation."));
  assert.ok(source.includes("No stock batches are available for reconciliation."));
  assert.ok(source.includes("Receive stock before running physical count or supplier bill reconciliation."));
  assert.ok(source.includes("Direct Goods Receipt"));
  assert.ok(source.includes("Create supplier first"));
  assert.ok(source.includes("Purchase order already exists"));
  assert.ok(source.includes("Supplier saved successfully."));
});

test("procurement lifecycle includes draft, grouped PO records, and supplier actions", () => {
  const source = readSource("pages/pharmacy/PharmacyOperationsPage.tsx");
  assert.ok(source.includes("Drafts"));
  assert.ok(source.includes("Partially Received"));
  assert.ok(source.includes("Received"));
  assert.ok(source.includes("Cancelled"));
  assert.ok(source.includes("Create Invoice"));
  assert.ok(source.includes("Create GRN"));
  assert.ok(source.includes("Supplier deactivated"));
  assert.ok(source.includes("Purchase order saved."));
  assert.ok(source.includes("Supplier saved successfully."));
  assert.ok(source.includes("Print directly or Save as PDF for supplier sharing."));
  assert.ok(source.includes("Invoice linked to PO"));
  assert.ok(source.includes("Discount"));
  assert.ok(source.includes("Freight"));
  assert.ok(source.includes("Variance"));
});
