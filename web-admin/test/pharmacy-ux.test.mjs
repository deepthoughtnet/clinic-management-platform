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
  const appSource = readSource("app/App.tsx");
  assert.ok(navSource.includes('label: "Dispense Queue"'));
  assert.ok(navSource.includes('label: "Medicine Master"'));
  assert.ok(navSource.includes('label: "Prescription Register"'));
  assert.ok(navSource.includes('label: "Inventory"'));
  assert.ok(navSource.includes('label: "Procurement"'));
  assert.ok(navSource.includes('label: "Reconciliation"'));
  assert.ok(navSource.includes('label: "Reports & Audit"'));
  assert.ok(navSource.indexOf('label: "Procurement"') < navSource.indexOf('label: "Medicine Master"'));
  assert.ok(topBarSource.includes('pathname === "/pharmacy/procurement"'));
  assert.ok(topBarSource.includes('pathname === "/pharmacy/reconciliation"'));
  assert.ok(topBarSource.includes('pathname === "/pharmacy/operations"'));
  assert.ok(sidebarSource.includes('const prefixMatchPaths = new Set(['));
  assert.ok(sidebarSource.includes('"/patients"'));
  assert.ok(sidebarSource.includes('"/appointments"'));
  assert.ok(sidebarSource.includes('"/consultations"'));
  assert.ok(sidebarSource.includes('"/platform/tenants"'));
  assert.ok(sidebarSource.includes('group.key === "clinical" && isPharmacyOnlyTenant'));
  assert.ok(sidebarSource.includes('tenantRole === "PHARMACY_POS_USER"'));
  assert.ok(appSource.includes('function PathnameKeyedRoute'));
  assert.ok(appSource.includes('console.info("[route]", location.pathname);'));
  assert.ok(appSource.includes('<PharmacyProcurementPage />'));
  assert.ok(appSource.includes('<PharmacyReconciliationPage />'));
  assert.ok(appSource.includes('pathname === "/patients" || pathname.startsWith("/patients/")'));
  assert.ok(appSource.includes('pathname === "/pharmacy/operations"'));
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
  assert.ok(medicineSource.includes("Missing barcode"));
  assert.ok(medicineSource.includes("Medicine Workflow"));
  assert.ok(inventorySource.includes('React.useState<"add" | "transaction" | "transfer" | "count" | null>(null)'));
  assert.ok(inventorySource.includes("Read-only inventory access is active for this role."));
  assert.ok(inventorySource.includes("Returns & Write-Off History"));
  assert.ok(inventorySource.includes("Batch management"));
  assert.ok(inventorySource.includes("Inventory Workflow"));
  assert.ok(inventorySource.includes("Receive via Procurement"));
  assert.ok(inventorySource.includes("Direct Goods Receipt"));
  assert.ok(inventorySource.includes("No inventory available."));
});

test("pharmacy pos shows the blocked-state guidance before shift open", () => {
  const source = readSource("pages/pharmacy/PharmacyPosPage.tsx");
  assert.ok(source.includes("No POS shift is open. Open a shift before collecting payment or completing sale."));
  assert.ok(source.includes("Complete Sale is unavailable"));
  assert.ok(source.includes("Open Shift"));
  assert.ok(source.includes("POS Workflow"));
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
  assert.ok(operationsSource.includes('label="Suppliers"'));
  assert.ok(operationsSource.includes('label="Purchase Orders"'));
  assert.ok(operationsSource.includes('label="Supplier Invoices"'));
  assert.ok(operationsSource.includes('label="Goods Receipt"'));
  assert.ok(operationsSource.includes('workspace=purchase-orders'));
  assert.ok(operationsSource.includes('workspace=goods-receipt'));
  assert.ok(operationsSource.includes('workspace=suppliers&focus=supplier'));
  assert.ok(operationsSource.includes('updateProcurementWorkspaceRoute'));
  assert.ok(operationsSource.includes('label="Supplier Bill Reconciliation"'));
  assert.ok(operationsSource.includes('label="Physical Count"'));
  assert.ok(operationsSource.includes('label="Stock Adjustments"'));
  assert.ok(operationsSource.includes('label="Approval Review"'));
  assert.ok(operationsSource.includes("Manage supplier purchasing, invoices, and goods receipt for pharmacy stock."));
  assert.ok(operationsSource.includes("Review stock differences, supplier bill variances, and approval workflows."));
  assert.ok(operationsSource.includes("Create a supplier before creating a Purchase Order."));
  assert.ok(operationsSource.includes("Add Item"));
  assert.ok(operationsSource.includes("Save Draft"));
  assert.ok(operationsSource.includes("Generate PO"));
  assert.ok(operationsSource.includes("Purchase order saved."));
  assert.ok(operationsSource.includes("Post GRN"));
  assert.ok(operationsSource.includes("Post Direct Goods Receipt"));
  assert.ok(operationsSource.includes("Direct goods receipt posted and inventory updated."));
  assert.ok(operationsSource.includes("No purchase orders are available for goods receipt."));
  assert.ok(operationsSource.includes("No purchase orders are available for invoice matching."));
  assert.ok(operationsSource.includes("No medicines are available for reconciliation."));
  assert.ok(operationsSource.includes("No stock batches are available for reconciliation."));
  assert.ok(operationsSource.includes("Generate PDF"));
  assert.ok(operationsSource.includes("Print"));
  assert.ok(operationsSource.includes("Download"));
  assert.ok(operationsSource.includes("Send"));
  assert.ok(operationsSource.includes("Cancel"));
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
  assert.ok(source.includes('workspace === "suppliers"'));
  assert.ok(source.includes('workspace === "purchase-orders"'));
  assert.ok(source.includes('workspace === "invoices"'));
  assert.ok(source.includes('workspace === "goods-receipt"'));
  assert.ok(source.includes('workspace === "purchase-orders"'));
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
  assert.ok(procurementSource.includes("if (!workspace)"));
  assert.ok(procurementSource.includes("return <Navigate"));
  assert.ok(procurementSource.includes('[mount] ProcurementPage'));
  assert.ok(procurementSource.includes('[unmount] ProcurementPage'));
  assert.ok(reconciliationSource.includes('[mount] ReconciliationPage'));
  assert.ok(reconciliationSource.includes('[unmount] ReconciliationPage'));
  assert.ok(operationsSource.includes('type PharmacyOperationsPageProps = {'));
  assert.ok(operationsSource.includes('mode: "procurement" | "reconciliation"'));
  assert.ok(operationsSource.includes('const pageMode = mode;'));
  assert.ok(operationsSource.includes('if (pageMode !== "procurement") return;'));
  assert.ok(operationsSource.includes('const loadProcurementPageData = React.useCallback'));
  assert.ok(operationsSource.includes('const loadReconciliationPageData = React.useCallback'));
  assert.ok(operationsSource.includes('const refreshCurrentPageData = React.useCallback'));
  assert.ok(operationsSource.includes('Unable to load stock summary. Procurement data is still available.'));
  assert.ok(operationsSource.includes('setSearchParams(nextSearch, { replace: true });'));
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
