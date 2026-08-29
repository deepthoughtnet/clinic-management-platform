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
  assert.ok(sidebarSource.includes('const posUserAllowed = new Set(["pharmacy-pos"]);'));
  assert.ok(sidebarSource.includes('if (tenantRole === "PHARMACY_POS_USER" && !posUserAllowed.has(item.key)) return false;'));
  assert.ok(appSource.includes('function PathnameKeyedRoute'));
  assert.ok(appSource.includes('path="/pharmacy/dashboard"'));
  assert.ok(appSource.includes('path="/pharmacy/pos"'));
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
  assert.ok(medicineSource.includes('{ value: "SACHET", label: "Sachet" }'));
  assert.ok(medicineSource.includes('{ value: "BEFORE_FOOD", label: "Before food" }'));
  assert.ok(medicineSource.includes('select'));
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
  assert.ok(operationsSource.includes("defaultInwardLocationId"));
  assert.ok(operationsSource.includes("locationId: current.locationId || locationRows.find((location) => location.defaultLocation)?.id || locationRows[0]?.id || null"));
  assert.ok(operationsSource.includes("value={inwardForm.locationId || defaultInwardLocationId}"));
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

test("pharmacy pos users keep only the POS workspace visible", () => {
  const navSource = readSource("layout/nav.ts");
  const appSource = readSource("app/App.tsx");
  const sidebarSource = readSource("layout/SidebarNav.tsx");
  const moduleRegistry = readSource("modules/moduleRegistry.ts");

  assert.ok(navSource.includes('rolesAny: ["CLINIC_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER"]'));
  assert.ok(!navSource.includes('"PHARMACY_POS_USER"], moduleAny: ["INVENTORY"]'));
  assert.ok(sidebarSource.includes('const posUserAllowed = new Set(["pharmacy-pos"]);'));
  assert.ok(sidebarSource.includes('if (tenantRole === "PHARMACY_POS_USER" && !posUserAllowed.has(item.key)) return false;'));
  assert.ok(moduleRegistry.includes("isPharmacyPosOnlyRole(auth)"));
  assert.ok(moduleRegistry.includes('"/pharmacy/pos"'));
  assert.ok(moduleRegistry.includes('!pharmacyPosOnlyRole'));
  assert.ok(appSource.includes('path="/pharmacy/dashboard"'));
  assert.ok(appSource.includes('path="/pharmacy/pos"'));
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

test("purchase order drafts preserve notes and line discounts across save and reload", () => {
  const source = readSource("pages/pharmacy/PharmacyProcurePage.tsx");
  assert.ok(source.includes('notes: record.notes || ""'));
  assert.ok(source.includes('notes: mapped.notes || ""'));
  assert.ok(source.includes('notes: fallback.notes || ""'));
  assert.ok(source.includes('notes: validated.notes || null'));
  assert.ok(source.includes('discount: parsed.discount'));
  assert.ok(source.includes('discount: line.discount'));
  assert.ok(source.includes('acc.totalDiscount = roundCurrency(acc.totalDiscount + discount);'));
  assert.ok(source.includes('function roundCurrency(value: number)'));
  assert.ok(source.includes('return roundCurrency(Math.max(0, amount + gstAmount - discount));'));
  assert.ok(source.includes('return roundCurrency(invoiceComputedPayable - selectedInvoicePoTotal);'));
  assert.ok(source.includes('roundCurrency(invoice.varianceAmount) === 0'));
  assert.ok(source.includes('roundCurrency(invoice.varianceAmount) !== 0'));
  assert.ok(source.includes('const payload: PurchaseOrderInput ='));
  assert.ok(source.includes('getPurchaseOrderPdf'));
  assert.ok(source.includes('sendPurchaseOrder'));
  assert.ok(source.includes('window.open("", "_blank", "width=1024,height=768")'));
  assert.ok(source.includes('renderPurchaseOrderPrintDocument(clinicName, supplier, purchaseOrder, formatPurchaseOrderTimestamp(purchaseOrder.updatedAt))'));
  assert.ok(source.includes('Download PDF'));
  assert.ok(!source.includes('disabled>Download PDF'));
  assert.ok(source.includes('Send'));
  assert.ok(source.includes('Reference / Notes'));
  assert.ok(source.includes('Supplier Contact'));
  assert.ok(source.includes('Status Date'));
  assert.ok(source.includes('supplierEmail'));
  assert.ok(source.includes('canSendPo'));
  assert.ok(source.includes('invoice.status === "DRAFT" && roundCurrency(invoice.varianceAmount) === 0'));
});

test("supplier bill reconciliation aggregates all eligible GRNs and keeps the canonical ready-for-payment state", () => {
  const source = readSource("pages/pharmacy/PharmacyReconcilePage.tsx");
  const stripSource = readSource("components/pharmacy/DocumentRelationshipStrip.tsx");

  assert.ok(source.includes("function collectRelatedGoodsReceipts("));
  assert.ok(source.includes("function goodsReceiptDisplayLabel(receipts"));
  assert.ok(source.includes("function goodsReceiptDisplayValue(receipts"));
  assert.ok(source.includes("const relatedGrns = collectRelatedGoodsReceipts(invoice, grnsByInvoiceId, grnsByPoId);"));
  assert.ok(source.includes("const latestGoodsReceipt = relatedGrns[relatedGrns.length - 1] ?? null;"));
  assert.ok(source.includes("const grnItems = relatedGrns.flatMap((receipt) => parseProcurementItems(receipt.itemsJson || \"[]\"));"));
  assert.ok(source.includes('goodsReceiptCount: relatedGrns.length,'));
  assert.ok(source.includes('grnReference: goodsReceiptDisplayValue(relatedGrns),'));
  assert.ok(source.includes('state: row.goodsReceiptCount > 0 ? "completed" as const : "pending" as const'));
  assert.ok(source.includes('badgeLabel: selectedInvoice.readyForPayment ? "Ready for Payment" : selectedInvoice.status'));
  assert.ok(source.includes('label={displayReconciliationStatus(selectedInvoice)}'));
  assert.ok(source.includes('label={displayReconciliationStatus(inspectedInvoice)}'));
  assert.ok(source.includes('label={displayReconciliationStatus(row)}'));
  assert.ok(source.includes('function timelineDetailText('));
  assert.ok(source.includes('if (step.state === "completed") return detail || "Completed";'));
  assert.ok(source.includes('expectedNextStep: "Reconciliation complete. Continue with payment processing."'));
  assert.ok(source.includes('disabled={selectedInvoice.status !== "Approved" || selectedInvoice.readyForPayment}'));
  assert.ok(source.includes('disabled={(selectedInvoice?.goodsReceiptCount ?? 0) === 0}'));
  assert.ok(source.includes('receipts.at(-1)?.receiptNumber ?? row?.grnReference'));
  assert.ok(source.includes('This invoice is already ready for payment.'));
  assert.ok(stripSource.includes('function formatStageDetail(stage: DocumentRelationshipStage)'));
  assert.ok(stripSource.includes('if (stage.state === "completed") {'));
  assert.ok(stripSource.includes('return "Completed";'));
  assert.ok(!source.includes('timelineStep.at ? formatDateTime(timelineStep.at) : "Pending"'));
});

test("physical count reconciliation uses persisted sessions for totals and posted movements for adjustment refs", () => {
  const source = readSource("pages/pharmacy/PharmacyReconcilePage.tsx");
  assert.ok(source.includes("listPhysicalCountSessions"));
  assert.ok(source.includes("function collectPhysicalCountArchiveTransactions(session: PhysicalCountSession"));
  assert.ok(source.includes("function isPostedPhysicalCountArchiveSession(session: PhysicalCountSession"));
  assert.ok(source.includes("physicalCountSessions.filter((session) => isPostedPhysicalCountArchiveSession(session, inventoryTransactions))"));
  assert.ok(source.includes("buildPhysicalCountMovementRows(session, inventoryTransactions, inventoryStockById, inventoryMedicineById, auth)"));
  assert.ok(source.includes("itemsCounted: session.lines.length"));
  assert.ok(source.includes("movementCount: lines.length"));
  assert.ok(source.includes("reason: physicalCountReasonLabel(session.reason)"));
  assert.ok(source.includes("scope: session.scopeLabel || physicalCountScopeLabel(session.scope)"));
  assert.ok(source.includes("const createdBy = resolvePhysicalCountCreatedByLabel(session, auth);"));
  assert.ok(source.includes("const postedBy = resolvePhysicalCountActorLabel(["));
  assert.ok(source.includes("countLabel={`${selectedPhysicalCount.movementCount} rows`}"));
  assert.ok(source.includes("countLabel={`${selectedPhysicalCount.movementCount} movements`}"));
  assert.ok(source.includes("Posted physical count sessions are captured from persisted session records"));
});

test("stock adjustment reconciliation keeps posted audit records out of approval-needed states", () => {
  const source = readSource("pages/pharmacy/PharmacyReconcilePage.tsx");
  assert.ok(source.includes('Stock adjustments are posted in Inventory and appear here as read-only audit records.'));
  assert.ok(source.includes('Pending approval-controlled items, if any, would appear in the review queue separately.'));
  assert.ok(source.includes('status: row.status,'));
  assert.ok(source.includes('CompactStatCard label="Stock Adjustment Requests" value={stockAdjustmentRows.filter((row) => row.status !== "Posted").length}'));
  assert.ok(source.includes('CompactFilterCard title="Approval Review" subtitle="Pending reconciliation decisions and posted adjustment audit records."'));
  assert.ok(source.includes('status: row.status === "Posted" ? "Posted" as const : "Reviewed" as const'));
  assert.ok(!source.includes('status: "Needs Approval" as const,'));
});
