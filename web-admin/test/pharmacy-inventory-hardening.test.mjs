import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("inventory route access and navigation stay aligned for tenant admins and auditors", () => {
  const moduleRegistry = readSource("modules/moduleRegistry.ts");
  const nav = readSource("layout/nav.ts");

  assert.ok(moduleRegistry.includes('const inventoryRole = hasAnyRole(activeRoles, "CLINIC_ADMIN", "TENANT_ADMIN", "AUDITOR"'));
  assert.ok(moduleRegistry.includes('return canAccessFeature(auth, "inventory") && inventoryRole && !pharmacyPosOnlyRole;'));
  assert.ok(nav.includes('rolesAny: ["CLINIC_ADMIN", "TENANT_ADMIN", "AUDITOR", "PHARMA", "PHARMACY", "PHARMACIST", "PHARMACY_INVENTORY_MANAGER"]'));
});

test("inventory page exposes managed locations and manual business references without UUID entry", () => {
  const inventoryPage = readSource("pages/inventory/InventoryPage.tsx");
  const validation = fs.readFileSync(
    path.join(process.cwd(), "..", "frontend", "packages", "form-validation-kit", "src", "schemas", "inventory.ts"),
    "utf8",
  );

  assert.ok(inventoryPage.includes("Inventory locations"));
  assert.ok(inventoryPage.includes("Add Location"));
  assert.ok(inventoryPage.includes("inventory-transaction-business-reference"));
  assert.ok(inventoryPage.includes("Business reference"));
  assert.ok(inventoryPage.includes("Location name already exists."));
  assert.ok(validation.includes('businessReference: optionalTrimmedString(160, "Business reference must be 160 characters or fewer.")'));
});

test("physical count sessions now load and persist through the inventory API instead of local-only state", () => {
  const inventoryPage = readSource("pages/inventory/InventoryPage.tsx");
  const clinicApi = readSource("api/clinicApi.ts");

  assert.ok(inventoryPage.includes("listPhysicalCountSessions"));
  assert.ok(inventoryPage.includes("setPhysicalCountSessions(physicalCountRows.map(normalizePhysicalCountSessionForUi));"));
  assert.ok(inventoryPage.includes("savePhysicalCountSession(auth.accessToken, auth.tenantId, session.id, buildPhysicalCountSaveInput(session))"));
  assert.ok(inventoryPage.includes("const normalizedSaved = normalizePhysicalCountSessionForUi(saved);"));
  assert.ok(inventoryPage.includes("return normalizedSaved;"));
  assert.ok(!inventoryPage.includes("setPhysicalCountSessions((current) => [createdSession, ...current]);"));
  assert.ok(!inventoryPage.includes("Physical count session saved locally. Inventory quantity is unchanged."));
  assert.ok(clinicApi.includes("export async function listPhysicalCountSessions"));
  assert.ok(clinicApi.includes("export async function savePhysicalCountSession"));
});

test("physical count maker and checker actions are split in the inventory page", () => {
  const inventoryPage = readSource("pages/inventory/InventoryPage.tsx");

  assert.ok(inventoryPage.includes('const canCreatePhysicalCountSession = hasAnyNormalizedRole(auth, "PHARMACIST", "PHARMA", "PHARMACY", "CLINIC_ADMIN");'));
  assert.ok(inventoryPage.includes('const canCheckPhysicalCountSession = hasAnyNormalizedRole(auth, "PHARMACY_INVENTORY_MANAGER", "CLINIC_ADMIN");'));
  assert.ok(inventoryPage.includes('{canCreatePhysicalCountSession ? ('));
  assert.ok(inventoryPage.includes('const canContinueSession = canCreatePhysicalCountSession && ["DRAFT", "IN_PROGRESS"].includes(session.status);'));
  assert.ok(inventoryPage.includes('const canReviewSession = canCheckPhysicalCountSession && session.status === "SUBMITTED";'));
  assert.ok(inventoryPage.includes('{canCheckPhysicalCountSession && physicalCountDrawerSession.status === "REVIEWED" ? ('));
  assert.ok(inventoryPage.includes('{canCheckPhysicalCountSession && physicalCountDrawerSession.status === "APPROVED" && !Boolean(physicalCountDrawerSession.audit.postedAt) ? ('));
});

test("physical count session rendering tolerates nullable backend strings without trim crashes", () => {
  const inventoryPage = readSource("pages/inventory/InventoryPage.tsx");

  assert.ok(inventoryPage.includes("function normalizePhysicalCountSessionLineForUi(line: PhysicalCountSessionLine)"));
  assert.ok(inventoryPage.includes("countedQty: normalizeInventoryText(line.countedQty),"));
  assert.ok(inventoryPage.includes("reason: normalizeInventoryText(line.reason),"));
  assert.ok(inventoryPage.includes("reviewerRemarks: normalizeInventoryText(line.reviewerRemarks),"));
  assert.ok(inventoryPage.includes("function normalizePhysicalCountAuditFieldsForUi(audit: PhysicalCountAuditFields)"));
  assert.ok(inventoryPage.includes("generalNotes: normalizeInventoryText(audit.generalNotes),"));
  assert.ok(inventoryPage.includes("const trimmed = normalizeInventoryText(line.countedQty);"));
  assert.ok(inventoryPage.includes("const reason = normalizeInventoryText(line.reason);"));
  assert.ok(inventoryPage.includes("const reviewerRemarks = normalizeInventoryText(line.reviewerRemarks);"));
  assert.ok(!inventoryPage.includes("line.countedQty.trim()"));
  assert.ok(!inventoryPage.includes("line.reason.trim()"));
  assert.ok(!inventoryPage.includes("line.reviewerRemarks.trim()"));
});

test("supplier invoice zero variance clears stale discrepancy reasons while preserving canonical math", () => {
  const procurePage = readSource("pages/pharmacy/PharmacyProcurePage.tsx");

  assert.ok(procurePage.includes('roundCurrency(invoice.varianceAmount) === 0 ? "" : invoice.varianceReason || ""'));
  assert.ok(procurePage.includes('invoiceVsPoDifference === 0 && invoiceForm.varianceReason'));
  assert.ok(procurePage.includes('varianceReason: variance === 0 ? null : parsed.data.varianceReason?.trim() || null'));
  assert.ok(procurePage.includes('const totalAmount = roundCurrency(Math.max(0, parsed.data.invoiceAmount + parsed.data.gstAmount - parsed.data.discount));'));
  assert.ok(procurePage.includes('const variance = roundCurrency(totalAmount - selectedPo.totalValue);'));
});
