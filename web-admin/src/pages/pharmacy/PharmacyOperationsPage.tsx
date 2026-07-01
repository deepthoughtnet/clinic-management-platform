import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
  IconButton,
  MenuItem,
  Menu,
  Select,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import MoreVertRoundedIcon from "@mui/icons-material/MoreVertRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import RuleRoundedIcon from "@mui/icons-material/RuleRounded";
import UploadFileRoundedIcon from "@mui/icons-material/UploadFileRounded";
import { useAuth } from "../../auth/useAuth";
import RequiredLabel from "../../components/forms/RequiredLabel";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import {
  fileUploadSchema,
  firstZodError,
  hasDuplicateSupplierName,
  normalizeIndianMobileInput,
  mapZodErrors,
  purchaseOrderSchema,
  goodsReceiptSchema,
  procurementLineSchema,
  stockInwardSchema,
  supplierSchema,
  supplierInvoiceSchema,
  vendorReconciliationSchema,
  type GoodsReceiptValues,
  type ProcurementLineValues,
  type PurchaseOrderValues,
  type StockInwardValues,
  type SupplierValues,
  type SupplierInvoiceValues,
  type VendorReconciliationValues,
} from "@deepthoughtnet/form-validation-kit";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, WorkflowDependencyGate, WorkflowGuide, compactCardContentSx, compactChipSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import {
  approveReconciliation,
  confirmGoodsReceipt,
  createReconciliation,
  createGoodsReceipt,
  createPurchaseOrder,
  createStockInward,
  createSupplier,
  createSupplierInvoice,
  getGoodsReceipts,
  getInventoryLocations,
  getMedicines,
  getPharmacyAnalytics,
  getPharmacyDashboard,
  getPurchaseOrders,
  getStocks,
  getSupplierInvoices,
  listReconciliations,
  listSuppliers,
  postReconciliation,
  rejectReconciliation,
  reviewReconciliationSheet,
  submitReconciliation as submitReconciliationApi,
  updateSupplier,
  uploadReconciliationSheet,
  type GoodsReceipt,
  type GoodsReceiptInput,
  type InventoryLocation,
  type Medicine,
  type OcrExtractionRow,
  type PharmacyAnalytics,
  type PharmacyDashboard,
  type PharmacyReconciliation,
  type PurchaseOrder,
  type PurchaseOrderInput,
  type ProcurementLineInput,
  type Stock,
  type StockInwardInput,
  type Supplier,
  type SupplierInput,
  type SupplierInvoice,
  type SupplierInvoiceInput,
} from "../../api/clinicApi";

type OpsTab = "suppliers" | "goods-receipt" | "reconciliation" | "procurement" | "physical-count" | "stock-adjustments" | "approval-review";
type ProcurementWorkspace = "suppliers" | "purchase-orders" | "supplier-invoices" | "goods-receipt";
type ProcurementTab = "po" | "invoice" | "grn";
type SupplierEditorMode = "create" | "edit" | "view";
type PurchaseOrderEditorMode = "create" | "edit" | "view";
type MedicineOption = { medicine: Medicine };
type ProcurementWorkflowItem = ProcurementLineInput;
type PurchaseOrderLifecycleStatus = "Draft" | "Generated" | "Sent" | "Partially Received" | "Received" | "Cancelled";
type LocalPurchaseOrderDraft = {
  id: string;
  supplierId: string;
  poNumber: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  approvalNote: string | null;
  items: ProcurementWorkflowItem[];
  savedAt: string;
};
type LocalPurchaseOrderMeta = {
  status: Exclude<PurchaseOrderLifecycleStatus, "Draft" | "Partially Received" | "Received">;
  generatedAt: string | null;
  printedAt: string | null;
  downloadedAt: string | null;
  sentAt: string | null;
  cancelledAt: string | null;
};
type PurchaseOrderWorkspaceRecord = {
  id: string;
  supplierId: string;
  supplierName: string | null;
  poNumber: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  approvalNote: string | null;
  items: ProcurementWorkflowItem[];
  itemCount: number;
  totals: ReturnType<typeof calculateProcurementTotals>;
  status: PurchaseOrderLifecycleStatus;
  isDraft: boolean;
  sourceIds: string[];
};

const emptySupplier: SupplierInput = {
  supplierName: "",
  contactPerson: null,
  phone: null,
  email: null,
  gstNumber: null,
  address: null,
  active: true,
};

const emptyInward: StockInwardInput = {
  medicineId: "",
  supplierId: null,
  locationId: null,
  purchaseReferenceNumber: null,
  batchNumber: null,
  barcode: null,
  qrCode: null,
  externalCode: null,
  expiryDate: null,
  purchaseDate: null,
  quantity: 0,
  unitCost: null,
  sellingPrice: null,
  lowStockThreshold: null,
};

const emptyReconciliation = {
  medicineId: "",
  stockBatchId: "",
  supplierId: "",
  physicalQuantity: "",
  reason: "",
};

type FormErrors = Record<string, string>;

const emptyErrors = (): FormErrors => ({});

function pickFirstError(errors: FormErrors, fallback = "Please fix the highlighted fields.") {
  return Object.values(errors)[0] || fallback;
}

function focusFirstInvalidField(...ids: string[]) {
  queueMicrotask(() => {
    for (const id of ids) {
      const element = document.getElementById(id) as HTMLElement | null;
      if (element && typeof element.focus === "function") {
        element.focus();
        break;
      }
    }
  });
}

function remapErrorPaths(errors: FormErrors, prefix = "") {
  const mapped: FormErrors = {};
  for (const [key, value] of Object.entries(errors)) {
    const cleaned = key.startsWith(prefix) ? key.slice(prefix.length) : key;
    mapped[cleaned.replace(/^\.+/, "")] = value;
  }
  return mapped;
}

const stickyTableSx = {
  maxHeight: 420,
  borderRadius: 2,
  border: "1px solid",
  borderColor: "divider",
  "& .MuiTableCell-root": {
    py: 0.85,
    verticalAlign: "top",
  },
  "& .MuiTableHead-root .MuiTableCell-root": {
    position: "sticky",
    top: 0,
    zIndex: 1,
    bgcolor: "background.paper",
  },
} as const;

function money(value: number | null | undefined) {
  return (value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function badgeTone(text: string) {
  if (text === "OUT_OF_STOCK" || text === "EXPIRED") return "error" as const;
  if (text === "LOW_STOCK" || text === "NEAR_EXPIRY") return "warning" as const;
  if (text === "AVAILABLE" || text === "OK") return "success" as const;
  if (text === "SUBMITTED") return "warning" as const;
  if (text === "APPROVED") return "info" as const;
  if (text === "POSTED") return "success" as const;
  if (text === "REJECTED") return "error" as const;
  return "default" as const;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function formatDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleDateString();
}

function actorLabel(actorId: string | null | undefined, currentUserId: string | null, fallback: string) {
  if (!actorId) return "-";
  if (actorId === currentUserId) return "You";
  return fallback;
}

function renderMedicineDescriptor(medicine: Medicine) {
  return [medicine.medicineType, medicine.strength, medicine.defaultPrice != null ? `INR ${money(medicine.defaultPrice)}` : null].filter(Boolean).join(" • ");
}

function normalizeSupplierPayload(values: SupplierValues): SupplierInput {
  return {
    supplierName: values.supplierName,
    contactPerson: values.contactPerson ?? null,
    phone: values.phone ? (normalizeIndianMobileInput(values.phone) as string) : null,
    email: values.email ?? null,
    gstNumber: values.gstNumber ?? null,
    address: values.address ?? null,
    active: values.active,
  };
}

function parseProcurementItems(itemsJson: string | null | undefined): ProcurementWorkflowItem[] {
  if (!itemsJson) return [];
  try {
    const parsed = JSON.parse(itemsJson);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item) => ({
        medicineId: typeof item?.medicineId === "string" ? item.medicineId : null,
        medicineName: typeof item?.medicineName === "string" ? item.medicineName : null,
        quantity: Number(item?.quantity || 0),
        expectedUnitCost: item?.expectedUnitCost != null ? Number(item.expectedUnitCost) : null,
        unitCost: item?.unitCost != null ? Number(item.unitCost) : null,
        taxPercent: item?.taxPercent != null ? Number(item.taxPercent) : null,
        batchNumber: typeof item?.batchNumber === "string" ? item.batchNumber : null,
        expiryDate: typeof item?.expiryDate === "string" ? item.expiryDate : null,
        sellingPrice: item?.sellingPrice != null ? Number(item.sellingPrice) : null,
      }))
      .filter((item) => item.medicineId || item.medicineName || item.quantity > 0);
  } catch {
    return [];
  }
}

function calculateProcurementTotals(items: ProcurementWorkflowItem[]) {
  return items.reduce(
    (acc, item) => {
      const quantity = Number(item.quantity || 0);
      const unitCost = Number(item.unitCost ?? item.expectedUnitCost ?? item.sellingPrice ?? 0);
      const base = quantity * unitCost;
      const tax = base * (Number(item.taxPercent || 0) / 100);
      acc.quantity += quantity;
      acc.subtotal += base;
      acc.tax += tax;
      acc.total += base + tax;
      return acc;
    },
    { quantity: 0, subtotal: 0, tax: 0, total: 0 },
  );
}

function procurementLineKey(item: ProcurementWorkflowItem) {
  return [item.medicineId || "", item.medicineName || ""].join("::").toLowerCase().trim();
}

function normalizeProcurementWorkspace(value: string | null, workflow: string | null): ProcurementWorkspace | null {
  if (value === "suppliers" || value === "purchase-orders" || value === "supplier-invoices" || value === "goods-receipt") return value;
  if (value === "procurement") return "purchase-orders";
  if (value === "invoices") return "supplier-invoices";
  if (value === "invoice") return "supplier-invoices";
  if (value === "grn") return "goods-receipt";
  if (workflow === "invoice") return "supplier-invoices";
  if (workflow === "grn") return "goods-receipt";
  return null;
}

function defaultProcurementWorkspace(supplierCount: number): ProcurementWorkspace {
  return supplierCount === 0 ? "suppliers" : "purchase-orders";
}

function workspaceFromPurchaseOrderTab(value: ProcurementTab): ProcurementWorkspace {
  if (value === "invoice") return "supplier-invoices";
  if (value === "grn") return "goods-receipt";
  return "purchase-orders";
}

function safeLocalStorageGet(key: string) {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeLocalStorageSet(key: string, value: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Ignore storage failures and keep the workflow usable.
  }
}

function draftStorageKey(tenantId: string) {
  return `pharmacy.procurementDrafts.${tenantId}`;
}

function lifecycleStorageKey(tenantId: string) {
  return `pharmacy.procurementLifecycle.${tenantId}`;
}

function parseJsonStorage<T>(value: string | null, fallback: T): T {
  if (!value) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function derivePurchaseOrderStatus(
  items: ProcurementWorkflowItem[],
  receipts: GoodsReceipt[],
  meta?: LocalPurchaseOrderMeta,
): PurchaseOrderLifecycleStatus {
  if (meta?.status === "Cancelled") return "Cancelled";
  const orderedQty = items.reduce((total, item) => total + Number(item.quantity || 0), 0);
  const receivedQty = receipts.reduce((total, receipt) => {
    const receiptItems = parseProcurementItems(receipt.itemsJson);
    return total + receiptItems.reduce((qty, item) => qty + Number(item.quantity || 0), 0);
  }, 0);
  if (receivedQty > 0 && orderedQty > 0 && receivedQty < orderedQty) return "Partially Received";
  if (receivedQty > 0 && orderedQty > 0 && receivedQty >= orderedQty) return "Received";
  if (meta?.status === "Sent" || meta?.sentAt) return "Sent";
  return "Generated";
}

function normalizePoNumber(value: string) {
  return value.trim().toLowerCase();
}

type PharmacyOperationsPageProps = {
  mode: "procurement" | "reconciliation";
};

function deriveReconciliationTab(searchParams: URLSearchParams): OpsTab {
  const nextTab = searchParams.get("tab");
  if (nextTab === "physical-count") return "physical-count";
  if (nextTab === "stock-adjustments") return "stock-adjustments";
  if (nextTab === "approval-review") return "approval-review";
  return "reconciliation";
}

export default function PharmacyOperationsPage({ mode }: PharmacyOperationsPageProps) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const pageMode = mode;
  const goodsReceiptMode: "po" | "direct" = searchParams.get("mode") === "direct" ? "direct" : "po";
  const canManageOperations = auth.hasPermission("inventory.manage") || auth.rolesUpper.includes("CLINIC_ADMIN");
  const [tab, setTab] = React.useState<OpsTab>("reconciliation");
  const [supplierCountForWorkspaceFallback, setSupplierCountForWorkspaceFallback] = React.useState(0);
  const workspace = React.useMemo<ProcurementWorkspace>(() => {
    const explicit = normalizeProcurementWorkspace(searchParams.get("workspace"), searchParams.get("workflow"));
    if (explicit) return explicit;
    const legacyTab = searchParams.get("tab");
    const legacyWorkspace = normalizeProcurementWorkspace(legacyTab, searchParams.get("workflow"));
    if (legacyWorkspace) return legacyWorkspace;
    return defaultProcurementWorkspace(supplierCountForWorkspaceFallback);
  }, [searchParams, supplierCountForWorkspaceFallback]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  const [dashboard, setDashboard] = React.useState<PharmacyDashboard | null>(null);
  const [analytics, setAnalytics] = React.useState<PharmacyAnalytics | null>(null);
  const [suppliers, setSuppliers] = React.useState<Supplier[]>([]);
  const [reconciliations, setReconciliations] = React.useState<PharmacyReconciliation[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [locations, setLocations] = React.useState<InventoryLocation[]>([]);
  const [purchaseOrders, setPurchaseOrders] = React.useState<PurchaseOrder[]>([]);
  const [supplierInvoices, setSupplierInvoices] = React.useState<SupplierInvoice[]>([]);
  const [goodsReceipts, setGoodsReceipts] = React.useState<GoodsReceipt[]>([]);

  const [supplierForm, setSupplierForm] = React.useState<SupplierInput>(emptySupplier);
  const [supplierId, setSupplierId] = React.useState<string | null>(null);
  const [supplierNotes, setSupplierNotes] = React.useState("");
  const [supplierSearch, setSupplierSearch] = React.useState("");
  const [supplierFieldErrors, setSupplierFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [inwardForm, setInwardForm] = React.useState<StockInwardInput>(emptyInward);
  const [inwardFieldErrors, setInwardFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [inwardMedicineSearch, setInwardMedicineSearch] = React.useState("");
  const [reconForm, setReconForm] = React.useState(emptyReconciliation);
  const [reconFieldErrors, setReconFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [reconReason, setReconReason] = React.useState("");
  const [reconRemarks, setReconRemarks] = React.useState("");
  const [sheetFile, setSheetFile] = React.useState<File | null>(null);
  const [reviewRows, setReviewRows] = React.useState<OcrExtractionRow[]>([]);
  const [selectedReconciliationId, setSelectedReconciliationId] = React.useState<string | null>(null);
  const [selectedLocationId, setSelectedLocationId] = React.useState<string>("");
  const [poForm, setPoForm] = React.useState<PurchaseOrderInput>({ supplierId: "", poNumber: "", orderDate: "", expectedDeliveryDate: null, items: [], approvalNote: null });
  const [poFieldErrors, setPoFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [poDrafts, setPoDrafts] = React.useState<LocalPurchaseOrderDraft[]>([]);
  const [poLifecycleMeta, setPoLifecycleMeta] = React.useState<Record<string, LocalPurchaseOrderMeta>>({});
  const [editingDraftId, setEditingDraftId] = React.useState<string | null>(null);
  const [editingPurchaseOrderNumber, setEditingPurchaseOrderNumber] = React.useState<string | null>(null);
  const [invoiceForm, setInvoiceForm] = React.useState<SupplierInvoiceInput>({ supplierId: "", purchaseOrderId: null, invoiceNumber: "", invoiceDate: "", taxAmount: null, totalAmount: null, items: [], approvalNote: null });
  const [invoiceFieldErrors, setInvoiceFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [invoiceDiscount, setInvoiceDiscount] = React.useState("");
  const [invoiceFreight, setInvoiceFreight] = React.useState("");
  const [invoiceVariance, setInvoiceVariance] = React.useState("");
  const [grnForm, setGrnForm] = React.useState<GoodsReceiptInput>({ supplierId: "", purchaseOrderId: null, supplierInvoiceId: null, receiptNumber: "", receivedAt: "", locationId: "", items: [], approvalNote: null });
  const [grnFieldErrors, setGrnFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [supplierEditorOpen, setSupplierEditorOpen] = React.useState(false);
  const [supplierEditorMode, setSupplierEditorMode] = React.useState<SupplierEditorMode>("create");
  const [poEditorOpen, setPoEditorOpen] = React.useState(false);
  const [poEditorMode, setPoEditorMode] = React.useState<PurchaseOrderEditorMode>("create");
  const [poActionsAnchor, setPoActionsAnchor] = React.useState<HTMLElement | null>(null);
  const [poActionsRowId, setPoActionsRowId] = React.useState<string | null>(null);
  const [invoiceEditorOpen, setInvoiceEditorOpen] = React.useState(false);
  const [grnEditorOpen, setGrnEditorOpen] = React.useState(false);
  const [procurementLine, setProcurementLine] = React.useState({
    medicineId: "",
    medicineName: "",
    quantity: "",
    expectedUnitCost: "",
    unitCost: "",
    taxPercent: "",
    batchNumber: "",
    expiryDate: "",
    sellingPrice: "",
  });
  const [procurementLineFieldErrors, setProcurementLineFieldErrors] = React.useState<FormErrors>(emptyErrors);
  const [poItems, setPoItems] = React.useState<ProcurementWorkflowItem[]>([]);
  const [poItemEditIndex, setPoItemEditIndex] = React.useState<number | null>(null);
  const procurementTab: ProcurementTab = React.useMemo(() => {
    if (workspace === "supplier-invoices") return "invoice";
    if (workspace === "goods-receipt") return "grn";
    return "po";
  }, [workspace]);
  const loadRevisionRef = React.useRef(0);

  const activeMedicines = React.useMemo(() => medicines.filter((m) => m.active), [medicines]);
  const currentUserId = auth.appUserId || null;

  const medicineOptions = React.useMemo<MedicineOption[]>(() => activeMedicines.map((medicine) => ({ medicine })), [activeMedicines]);
  const selectedReconciliation = React.useMemo(
    () => reconciliations.find((row) => row.id === selectedReconciliationId) ?? null,
    [reconciliations, selectedReconciliationId],
  );
  const selectedReconciliationRows = reviewRows.length ? reviewRows : selectedReconciliation?.extractedRows ?? [];
  const stockMap = React.useMemo(() => new Map(stocks.map((stock) => [stock.id, stock])), [stocks]);
  const purchaseOrderMap = React.useMemo(() => new Map(purchaseOrders.map((row) => [row.id, row])), [purchaseOrders]);
  const supplierInvoiceMap = React.useMemo(() => new Map(supplierInvoices.map((row) => [row.id, row])), [supplierInvoices]);
  const activeSuppliers = React.useMemo(() => suppliers.filter((supplier) => supplier.active), [suppliers]);
  const supplierCount = suppliers.length;
  const stockBatchCount = stocks.length;
  const medicineCount = medicines.length;
  const purchaseOrderItemsTotal = React.useMemo(() => calculateProcurementTotals(poItems), [poItems]);
  const goodsReceiptsByPoId = React.useMemo(() => {
    const grouped = new Map<string, GoodsReceipt[]>();
    goodsReceipts.forEach((receipt) => {
      if (!receipt.purchaseOrderId) return;
      const current = grouped.get(receipt.purchaseOrderId) ?? [];
      current.push(receipt);
      grouped.set(receipt.purchaseOrderId, current);
    });
    return grouped;
  }, [goodsReceipts]);
  const selectedInvoicePurchaseOrder = React.useMemo(
    () => (invoiceForm.purchaseOrderId ? purchaseOrders.find((row) => row.id === invoiceForm.purchaseOrderId) ?? null : null),
    [invoiceForm.purchaseOrderId, purchaseOrders],
  );
  const selectedInvoicePurchaseOrderItems = React.useMemo(
    () => parseProcurementItems(selectedInvoicePurchaseOrder?.itemsJson),
    [selectedInvoicePurchaseOrder],
  );

  const draftCount = React.useMemo(() => reconciliations.filter((row) => ["DRAFT", "REJECTED"].includes((row.status || "").toUpperCase())).length, [reconciliations]);
  const submittedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "SUBMITTED").length, [reconciliations]);
  const approvedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "APPROVED").length, [reconciliations]);
  const postedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "POSTED").length, [reconciliations]);
  const duplicateReviewRows = React.useMemo(() => {
    const seen = new Set<string>();
    let count = 0;
    selectedReconciliationRows.forEach((row) => {
      const key = `${(row.medicineName || "").toLowerCase()}::${(row.batchNumber || "").toLowerCase()}`;
      if (!row.medicineName && !row.batchNumber) return;
      if (seen.has(key)) {
        count += 1;
      } else {
        seen.add(key);
      }
    });
    return count;
  }, [selectedReconciliationRows]);
  const unresolvedRows = React.useMemo(() => selectedReconciliationRows.filter((row) => row.needsReview).length, [selectedReconciliationRows]);
  const missingMedicineRows = React.useMemo(() => selectedReconciliationRows.filter((row) => !(row.medicineName || "").trim()).length, [selectedReconciliationRows]);

  const filteredSuppliers = React.useMemo(() => {
    const term = supplierSearch.trim().toLowerCase();
    if (!term) return suppliers;
    return suppliers.filter((row) =>
      [row.supplierName, row.contactPerson, row.phone, row.email, row.gstNumber]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [supplierSearch, suppliers]);

  const purchaseOrderWorkspaceRecords = React.useMemo<PurchaseOrderWorkspaceRecord[]>(() => {
    const records = new Map<string, PurchaseOrderWorkspaceRecord>();
    purchaseOrders.forEach((row) => {
      const items = parseProcurementItems(row.itemsJson);
      const key = normalizePoNumber(row.poNumber);
      const current = records.get(key);
      const receiptRows = goodsReceiptsByPoId.get(row.id) ?? [];
      const status = derivePurchaseOrderStatus(items, receiptRows, poLifecycleMeta[row.id]);
      const totals = calculateProcurementTotals(items);
      if (!current) {
        records.set(key, {
          id: row.id,
          supplierId: row.supplierId,
          supplierName: row.supplierName,
          poNumber: row.poNumber,
          orderDate: row.orderDate,
          expectedDeliveryDate: row.expectedDeliveryDate,
          approvalNote: row.approvalNote,
          items,
          itemCount: items.length,
          totals,
          status,
          isDraft: false,
          sourceIds: [row.id],
        });
        return;
      }
      const mergedItems = [...current.items, ...items];
      const mergedStatus = current.status === "Received" || status === "Received"
        ? "Received"
        : current.status === "Partially Received" || status === "Partially Received"
          ? "Partially Received"
          : current.status === "Sent" || status === "Sent"
            ? "Sent"
            : current.status === "Cancelled" && status === "Cancelled"
              ? "Cancelled"
              : "Generated";
      records.set(key, {
        ...current,
        items: mergedItems,
        itemCount: mergedItems.length,
        totals: calculateProcurementTotals(mergedItems),
        status: mergedStatus,
        sourceIds: [...current.sourceIds, row.id],
      });
    });
    poDrafts.forEach((draft) => {
      const key = normalizePoNumber(draft.poNumber || draft.id);
      if (records.has(key)) return;
      records.set(key, {
        id: draft.id,
        supplierId: draft.supplierId,
        supplierName: suppliers.find((row) => row.id === draft.supplierId)?.supplierName || null,
        poNumber: draft.poNumber || "Draft PO",
        orderDate: draft.orderDate,
        expectedDeliveryDate: draft.expectedDeliveryDate,
        approvalNote: draft.approvalNote,
        items: draft.items,
        itemCount: draft.items.length,
        totals: calculateProcurementTotals(draft.items),
        status: "Draft",
        isDraft: true,
        sourceIds: [draft.id],
      });
    });
    return [...records.values()].sort((a, b) => {
      const left = new Date(a.orderDate || a.expectedDeliveryDate || 0).getTime();
      const right = new Date(b.orderDate || b.expectedDeliveryDate || 0).getTime();
      return right - left;
    });
  }, [goodsReceiptsByPoId, poDrafts, poLifecycleMeta, purchaseOrders, suppliers]);

  function syncInvoiceFromPurchaseOrder(purchaseOrderId: string) {
    const purchaseOrder = purchaseOrderId ? purchaseOrders.find((row) => row.id === purchaseOrderId) ?? null : null;
    const nextItems = parseProcurementItems(purchaseOrder?.itemsJson);
    setInvoiceForm((current) => ({
      ...current,
      purchaseOrderId: purchaseOrderId || null,
      supplierId: purchaseOrder?.supplierId || current.supplierId,
      items: nextItems,
    }));
  }

  function syncGoodsReceiptFromPurchaseOrder(purchaseOrderId: string) {
    const purchaseOrder = purchaseOrderId ? purchaseOrders.find((row) => row.id === purchaseOrderId) ?? null : null;
    const nextItems = parseProcurementItems(purchaseOrder?.itemsJson).map((item) => ({
      ...item,
      quantity: Math.max(Number(item.quantity || 0), 1),
    }));
    setGrnForm((current) => ({
      ...current,
      purchaseOrderId: purchaseOrderId || null,
      supplierId: purchaseOrder?.supplierId || current.supplierId,
      items: nextItems,
    }));
  }

  function updateGoodsReceiptItem(index: number, patch: Partial<ProcurementWorkflowItem>) {
    setGrnForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
    }));
  }

  const logApiCall = React.useCallback(function <T>(pageName: string, endpoint: string, loader: () => Promise<T>): Promise<T> {
    console.info("[api]", pageName, endpoint);
    return loader();
  }, []);

  const loadProcurementPageData = React.useCallback(async (options: { includeMedicines: boolean; includeInventory: boolean }) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const revision = ++loadRevisionRef.current;
    setLoading(true);
    setError(null);
    try {
      const loadErrors: string[] = [];
      const settle = async <T,>(endpoint: string, promise: Promise<T>, fallbackMessage?: string): Promise<T | null> => {
        try {
          return await promise;
        } catch (err) {
          loadErrors.push(fallbackMessage || (err instanceof Error ? err.message : `Failed to load ${endpoint}`));
          return null;
        }
      };
      const [dashboardRow, analyticsRow, supplierRows, poRows, invoiceRows, grnRows, medicineRows, stockRows, locationRows] = await Promise.all([
        settle("/api/pharmacy/dashboard", logApiCall("ProcurementPage", "/api/pharmacy/dashboard", () => getPharmacyDashboard(auth.accessToken!, auth.tenantId!)), "Unable to load procurement dashboard. Procurement data is still available."),
        settle("/api/pharmacy/analytics", logApiCall("ProcurementPage", "/api/pharmacy/analytics", () => getPharmacyAnalytics(auth.accessToken!, auth.tenantId!)), "Unable to load procurement summary. Procurement data is still available."),
        settle("/api/pharmacy/suppliers", logApiCall("ProcurementPage", "/api/pharmacy/suppliers", () => listSuppliers(auth.accessToken!, auth.tenantId!)), "Unable to load suppliers. Procurement data is still available."),
        settle("/api/pharmacy/purchase-orders", logApiCall("ProcurementPage", "/api/pharmacy/purchase-orders", () => getPurchaseOrders(auth.accessToken!, auth.tenantId!)), "Unable to load purchase orders. Procurement data is still available."),
        settle("/api/pharmacy/supplier-invoices", logApiCall("ProcurementPage", "/api/pharmacy/supplier-invoices", () => getSupplierInvoices(auth.accessToken!, auth.tenantId!)), "Unable to load supplier invoices. Procurement data is still available."),
        settle("/api/pharmacy/goods-receipts", logApiCall("ProcurementPage", "/api/pharmacy/goods-receipts", () => getGoodsReceipts(auth.accessToken!, auth.tenantId!)), "Unable to load goods receipts. Procurement data is still available."),
        options.includeMedicines
          ? settle("/api/medicines", logApiCall("ProcurementPage", "/api/medicines", () => getMedicines(auth.accessToken!, auth.tenantId!)), "Unable to load medicine catalogue. Procurement data is still available.")
          : Promise.resolve<Medicine[] | null>(null),
        options.includeInventory
          ? settle("/api/inventory/stocks", logApiCall("ProcurementPage", "/api/inventory/stocks", () => getStocks(auth.accessToken!, auth.tenantId!)), "Unable to load stock summary. Procurement data is still available.")
          : Promise.resolve<Stock[] | null>(null),
        options.includeInventory
          ? settle("/api/inventory/locations", logApiCall("ProcurementPage", "/api/inventory/locations", () => getInventoryLocations(auth.accessToken!, auth.tenantId!)), "Unable to load inventory locations. Procurement data is still available.")
          : Promise.resolve<InventoryLocation[] | null>(null),
      ]);
      if (revision !== loadRevisionRef.current) return;
      setDashboard(dashboardRow);
      setAnalytics(analyticsRow);
      setSuppliers(supplierRows ?? []);
      setPurchaseOrders(poRows ?? []);
      setSupplierInvoices(invoiceRows ?? []);
      setGoodsReceipts(grnRows ?? []);
      if (medicineRows) setMedicines(medicineRows);
      if (stockRows) setStocks(stockRows);
      if (locationRows) {
        setLocations(locationRows);
        setSelectedLocationId((current) => current || locationRows.find((location) => location.defaultLocation)?.id || "");
        setGrnForm((current) => ({ ...current, locationId: current.locationId || locationRows.find((location) => location.defaultLocation)?.id || "" }));
      }
      if (loadErrors.length) {
        setError(loadErrors[0]);
      }
    } catch (err) {
      if (revision === loadRevisionRef.current) {
        setError(err instanceof Error ? err.message : "Failed to load procurement data");
      }
    } finally {
      if (revision === loadRevisionRef.current) {
        setLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId, logApiCall]);

  const loadReconciliationPageData = React.useCallback(async (options: { includeFormData: boolean; includeInventory: boolean }) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const revision = ++loadRevisionRef.current;
    setLoading(true);
    setError(null);
    try {
      const loadErrors: string[] = [];
      const settle = async <T,>(promise: Promise<T>, fallbackMessage: string): Promise<T | null> => {
        try {
          return await promise;
        } catch {
          loadErrors.push(fallbackMessage);
          return null;
        }
      };
      const baseResults = await Promise.all([
        settle(logApiCall("ReconciliationPage", "/api/pharmacy/reconciliations", () => listReconciliations(auth.accessToken!, auth.tenantId!)), "Unable to load reconciliations. Reconciliation data is still available."),
        options.includeFormData
          ? settle(logApiCall("ReconciliationPage", "/api/pharmacy/suppliers", () => listSuppliers(auth.accessToken!, auth.tenantId!)), "Unable to load suppliers. Reconciliation data is still available.")
          : Promise.resolve<Supplier[] | null>(null),
        options.includeFormData
          ? settle(logApiCall("ReconciliationPage", "/api/medicines", () => getMedicines(auth.accessToken!, auth.tenantId!)), "Unable to load medicine catalogue. Reconciliation data is still available.")
          : Promise.resolve<Medicine[] | null>(null),
        options.includeInventory
          ? settle(logApiCall("ReconciliationPage", "/api/inventory/stocks", () => getStocks(auth.accessToken!, auth.tenantId!)), "Unable to load stock summary. Reconciliation data is still available.")
          : Promise.resolve<Stock[] | null>(null),
        options.includeInventory
          ? settle(logApiCall("ReconciliationPage", "/api/inventory/locations", () => getInventoryLocations(auth.accessToken!, auth.tenantId!)), "Unable to load inventory locations. Reconciliation data is still available.")
          : Promise.resolve<InventoryLocation[] | null>(null),
      ]);
      if (revision !== loadRevisionRef.current) return;
      const [reconRows, supplierRows, medicineRows, stockRows, locationRows] = baseResults;
      const nextReconciliations = reconRows ?? [];
      setReconciliations(nextReconciliations);
      if (supplierRows) setSuppliers(supplierRows);
      if (medicineRows) setMedicines(medicineRows);
      if (stockRows) setStocks(stockRows);
      if (locationRows) {
        setLocations(locationRows);
        setSelectedLocationId((current) => current || locationRows.find((location) => location.defaultLocation)?.id || "");
      }
      if (nextReconciliations.length) {
        setSelectedReconciliationId((current) => current || nextReconciliations[0].id);
      }
      if (loadErrors.length) {
        setError(loadErrors[0]);
      }
    } catch (err) {
      if (revision === loadRevisionRef.current) {
        setError(err instanceof Error ? err.message : "Failed to load reconciliation data");
      }
    } finally {
      if (revision === loadRevisionRef.current) {
        setLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId, logApiCall]);

  React.useEffect(() => {
    setSupplierCountForWorkspaceFallback(supplierCount);
  }, [supplierCount]);

  React.useEffect(() => {
    if (pageMode !== "reconciliation") {
      setTab("reconciliation");
      return;
    }
    setTab(deriveReconciliationTab(searchParams));
  }, [pageMode, searchParams]);

  React.useEffect(() => {
    if (pageMode !== "procurement") return;
    void loadProcurementPageData({
      includeMedicines: workspace !== "suppliers",
      includeInventory: workspace === "goods-receipt",
    });
  }, [loadProcurementPageData, pageMode, workspace]);

  React.useEffect(() => {
    if (pageMode !== "reconciliation") return;
    void loadReconciliationPageData({
      includeFormData: tab !== "approval-review",
      includeInventory: tab !== "approval-review",
    });
  }, [loadReconciliationPageData, pageMode, tab]);

  const refreshCurrentPageData = React.useCallback(async () => {
    if (pageMode === "procurement") {
      await loadProcurementPageData({
        includeMedicines: workspace !== "suppliers",
        includeInventory: workspace === "goods-receipt",
      });
      return;
    }
    await loadReconciliationPageData({
      includeFormData: tab !== "approval-review",
      includeInventory: tab !== "approval-review",
    });
  }, [loadProcurementPageData, loadReconciliationPageData, pageMode, tab, workspace]);

  React.useEffect(() => {
    if (!auth.tenantId) return;
    setPoDrafts(parseJsonStorage<LocalPurchaseOrderDraft[]>(safeLocalStorageGet(draftStorageKey(auth.tenantId)), []));
    setPoLifecycleMeta(parseJsonStorage<Record<string, LocalPurchaseOrderMeta>>(safeLocalStorageGet(lifecycleStorageKey(auth.tenantId)), {}));
  }, [auth.tenantId]);

  React.useEffect(() => {
    if (!auth.tenantId) return;
    safeLocalStorageSet(draftStorageKey(auth.tenantId), JSON.stringify(poDrafts));
  }, [auth.tenantId, poDrafts]);

  React.useEffect(() => {
    if (!auth.tenantId) return;
    safeLocalStorageSet(lifecycleStorageKey(auth.tenantId), JSON.stringify(poLifecycleMeta));
  }, [auth.tenantId, poLifecycleMeta]);

  const updateProcurementWorkspaceRoute = React.useCallback((nextWorkspace: ProcurementWorkspace, focus?: string | null) => {
    const nextSearch = new URLSearchParams(searchParams);
    nextSearch.set("workspace", nextWorkspace);
    nextSearch.delete("tab");
    nextSearch.delete("workflow");
    const nextFocus = nextWorkspace === "suppliers" ? (focus ?? "supplier") : focus;
    console.log("[PROCUREMENT_TAB_CLICK]", nextWorkspace);
    if (nextFocus) {
      nextSearch.set("focus", nextFocus);
    } else {
      nextSearch.delete("focus");
    }
    if (nextWorkspace !== "goods-receipt") {
      nextSearch.delete("mode");
    }
    navigate(`/pharmacy/procurement?${nextSearch.toString()}`, { replace: false });
  }, [navigate, searchParams]);
  const updateProcurementRoute = updateProcurementWorkspaceRoute;

  const updateGoodsReceiptMode = React.useCallback((mode: "po" | "direct") => {
    const nextSearch = new URLSearchParams(searchParams);
    nextSearch.set("workspace", "goods-receipt");
    nextSearch.delete("tab");
    nextSearch.delete("workflow");
    if (mode === "direct") {
      nextSearch.set("mode", "direct");
    } else {
      nextSearch.delete("mode");
    }
    setSearchParams(nextSearch, { replace: true });
  }, [searchParams, setSearchParams]);

  React.useEffect(() => {
    if (pageMode !== "procurement" || workspace !== "suppliers") return;
    if (searchParams.get("focus") !== "supplier") return;
    window.setTimeout(() => {
      const field = document.getElementById("supplier-name") as HTMLElement | null;
      field?.focus?.();
    }, 0);
  }, [pageMode, searchParams, workspace]);

  React.useEffect(() => {
    if (selectedReconciliation?.extractedRows?.length) {
      setReviewRows(selectedReconciliation.extractedRows);
    } else {
      setReviewRows([]);
    }
  }, [selectedReconciliationId, selectedReconciliation]);

  React.useEffect(() => {
    if (!inwardForm.medicineId) return;
    const match = activeMedicines.find((row) => row.id === inwardForm.medicineId);
    if (match && inwardMedicineSearch !== match.medicineName) {
      setInwardMedicineSearch(match.medicineName);
    }
  }, [activeMedicines, inwardForm.medicineId, inwardMedicineSearch]);

  React.useEffect(() => {
    if (invoiceForm.purchaseOrderId) {
      syncInvoiceFromPurchaseOrder(invoiceForm.purchaseOrderId);
    } else {
      setInvoiceForm((current) => ({ ...current, items: [] }));
    }
  }, [invoiceForm.purchaseOrderId, syncInvoiceFromPurchaseOrder]);

  React.useEffect(() => {
    if (grnForm.purchaseOrderId) {
      syncGoodsReceiptFromPurchaseOrder(grnForm.purchaseOrderId);
    } else {
      setGrnForm((current) => ({ ...current, items: [] }));
    }
  }, [grnForm.purchaseOrderId, syncGoodsReceiptFromPurchaseOrder]);

  const validateSupplierForm = React.useCallback((): SupplierValues | null => {
    const parsed = supplierSchema.safeParse(supplierForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      if (supplierId == null && hasDuplicateSupplierName(supplierForm.supplierName, suppliers, null)) {
        errors.supplierName = "Supplier already exists";
      }
      setSupplierFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("supplier-name", "supplier-gst", "supplier-contact-person", "supplier-phone", "supplier-email", "supplier-address", "supplier-active");
      return null;
    }
    if (hasDuplicateSupplierName(parsed.data.supplierName, suppliers, supplierId)) {
      const errors = { supplierName: "Supplier already exists" };
      setSupplierFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("supplier-name");
      return null;
    }
    setSupplierFieldErrors(emptyErrors());
    return parsed.data;
  }, [supplierForm, supplierId, suppliers]);

  const validateInwardForm = React.useCallback((): StockInwardValues | null => {
    const parsed = stockInwardSchema.safeParse(inwardForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setInwardFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("inward-medicine", "inward-supplier", "inward-location", "inward-inward-date", "inward-grn", "inward-expiry", "inward-qty", "inward-threshold", "inward-unit-cost", "inward-selling-price");
      return null;
    }
    const selectedSupplier = parsed.data.supplierId ? suppliers.find((supplier) => supplier.id === parsed.data.supplierId) : null;
    if (selectedSupplier && !selectedSupplier.active) {
      const errors = { supplierId: "Inactive supplier cannot be used for stock inward." };
      setInwardFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("inward-supplier");
      return null;
    }
    setInwardFieldErrors(emptyErrors());
    return parsed.data;
  }, [inwardForm, suppliers]);

  const validateProcurementLineForm = React.useCallback((): ProcurementLineValues | null => {
    const payload = {
      medicineId: procurementLine.medicineId || null,
      medicineName: procurementLine.medicineName || null,
      quantity: Number(procurementLine.quantity || "0"),
      expectedUnitCost: procurementLine.expectedUnitCost ? Number(procurementLine.expectedUnitCost) : null,
      unitCost: procurementLine.unitCost ? Number(procurementLine.unitCost) : null,
      taxPercent: procurementLine.taxPercent ? Number(procurementLine.taxPercent) : null,
      batchNumber: procurementLine.batchNumber || null,
      expiryDate: procurementLine.expiryDate || null,
      sellingPrice: procurementLine.sellingPrice ? Number(procurementLine.sellingPrice) : null,
    };
    const parsed = procurementLineSchema.safeParse(payload);
    if (!parsed.success) {
      const errors = remapErrorPaths(mapZodErrors(parsed.error));
      setProcurementLineFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("procurement-line-name", "procurement-line-medicine", "procurement-line-qty", "procurement-line-unit-cost", "procurement-line-tax", "procurement-line-batch", "procurement-line-expiry", "procurement-line-selling");
      return null;
    }
    if (parsed.data.medicineId) {
      const selectedMedicine = medicines.find((medicine) => medicine.id === parsed.data.medicineId);
      if (selectedMedicine && !selectedMedicine.active) {
        const errors = { medicineId: "Inactive medicine cannot be used." };
        setProcurementLineFieldErrors(errors);
        setError(pickFirstError(errors));
        focusFirstInvalidField("procurement-line-medicine");
        return null;
      }
    }
    setProcurementLineFieldErrors(emptyErrors());
    return parsed.data;
  }, [medicines, procurementLine]);

  const resetProcurementLineEditor = React.useCallback(() => {
    setProcurementLine({
      medicineId: "",
      medicineName: "",
      quantity: "",
      expectedUnitCost: "",
      unitCost: "",
      taxPercent: "",
      batchNumber: "",
      expiryDate: "",
      sellingPrice: "",
    });
    setProcurementLineFieldErrors(emptyErrors());
    setPoItemEditIndex(null);
  }, []);

  const resetPurchaseOrderEditor = React.useCallback(() => {
    setPoForm({ supplierId: "", poNumber: "", orderDate: "", expectedDeliveryDate: null, items: [], approvalNote: null });
    setPoItems([]);
    setEditingDraftId(null);
    setEditingPurchaseOrderNumber(null);
    setPoFieldErrors(emptyErrors());
    resetProcurementLineEditor();
    setPoEditorOpen(false);
    setPoEditorMode("create");
  }, [resetProcurementLineEditor]);

  const loadWorkspacePurchaseOrder = React.useCallback((record: PurchaseOrderWorkspaceRecord) => {
    updateProcurementWorkspaceRoute("purchase-orders", null);
    setPoForm({
      supplierId: record.supplierId,
      poNumber: record.poNumber,
      orderDate: record.orderDate,
      expectedDeliveryDate: record.expectedDeliveryDate,
      items: record.items,
      approvalNote: record.approvalNote,
    });
    setPoItems(record.items);
    setEditingDraftId(record.isDraft ? record.id : null);
    setEditingPurchaseOrderNumber(record.isDraft ? null : record.poNumber);
    setPoEditorMode(record.isDraft ? "edit" : "view");
    setPoEditorOpen(true);
    resetProcurementLineEditor();
  }, [resetProcurementLineEditor, updateProcurementWorkspaceRoute]);

  const updatePoLifecycle = React.useCallback((poIds: string[], patch: Partial<LocalPurchaseOrderMeta>) => {
    setPoLifecycleMeta((current) => {
      const next = { ...current };
      poIds.forEach((poId) => {
        const existing = next[poId] ?? {
          status: "Generated",
          generatedAt: null,
          printedAt: null,
          downloadedAt: null,
          sentAt: null,
          cancelledAt: null,
        };
        next[poId] = {
          ...existing,
          ...patch,
        };
      });
      return next;
    });
  }, []);

  const renderPurchaseOrderDocument = React.useCallback((record: PurchaseOrderWorkspaceRecord) => {
    const supplier = suppliers.find((row) => row.id === record.supplierId);
    const address = supplier?.address || "Address pending";
    const gst = supplier?.gstNumber || "GST pending";
    const rows = record.items.map((item, index) => {
      const quantity = Number(item.quantity || 0);
      const unitCost = Number(item.expectedUnitCost ?? item.unitCost ?? item.sellingPrice ?? 0);
      const base = quantity * unitCost;
      const tax = base * (Number(item.taxPercent || 0) / 100);
      const total = base + tax;
      return `<tr>
        <td>${index + 1}</td>
        <td>${item.medicineName || "Medicine"}</td>
        <td>${quantity}</td>
        <td>${money(unitCost)}</td>
        <td>${item.sellingPrice != null ? money(item.sellingPrice) : "-"}</td>
        <td>${item.taxPercent != null ? `${item.taxPercent}%` : "-"}</td>
        <td>${item.batchNumber || "-"}</td>
        <td>${item.expiryDate || "-"}</td>
        <td>${money(total)}</td>
      </tr>`;
    }).join("");
    return `<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>Purchase Order ${record.poNumber}</title>
    <style>
      body { font-family: Arial, sans-serif; padding: 24px; color: #17202a; }
      h1 { margin: 0 0 4px; font-size: 22px; }
      .muted { color: #5f6b7a; font-size: 12px; }
      .header, .section { margin-bottom: 18px; }
      table { width: 100%; border-collapse: collapse; margin-top: 12px; }
      th, td { border: 1px solid #d7dde4; padding: 8px; font-size: 12px; text-align: left; }
      th { background: #f6f8fb; }
      .totals { margin-top: 12px; font-weight: 700; }
      .footer { margin-top: 28px; font-size: 12px; color: #5f6b7a; }
    </style>
  </head>
  <body>
    <div class="header">
      <h1>Jeevan Pharmacy</h1>
      <div class="muted">Purchase Order ${record.poNumber}</div>
    </div>
    <div class="section">
      <strong>Supplier:</strong> ${record.supplierName || "Supplier pending"}<br />
      <strong>Address:</strong> ${address}<br />
      <strong>GST:</strong> ${gst}<br />
      <strong>Order date:</strong> ${record.orderDate || "-"}<br />
      <strong>Expected delivery:</strong> ${record.expectedDeliveryDate || "-"}<br />
      <strong>Terms:</strong> Pharmacy purchase order generated from procurement workspace.
    </div>
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Medicine</th>
          <th>Qty</th>
          <th>Purchase Price</th>
          <th>MRP</th>
          <th>Tax</th>
          <th>Batch</th>
          <th>Expiry</th>
          <th>Line Total</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
    <div class="totals">
      Subtotal: INR ${money(record.totals.subtotal)}<br />
      GST: INR ${money(record.totals.tax)}<br />
      Grand Total: INR ${money(record.totals.total)}
    </div>
    <div class="footer">
      Generated from the standalone pharmacy procurement workflow. Print directly or Save as PDF for supplier sharing.
    </div>
  </body>
</html>`;
  }, [suppliers]);

  const openPurchaseOrderDocument = React.useCallback((record: PurchaseOrderWorkspaceRecord, mode: "print" | "download" | "pdf") => {
    const documentHtml = renderPurchaseOrderDocument(record);
    if (mode === "download") {
      const blob = new Blob([documentHtml], { type: "text/html;charset=utf-8" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `${record.poNumber || "purchase-order"}.html`;
      link.click();
      URL.revokeObjectURL(link.href);
      updatePoLifecycle(record.sourceIds, { downloadedAt: new Date().toISOString(), status: "Generated" });
      setSuccess("PO downloaded. Share the file or print it as needed.");
      return;
    }
    const popup = window.open("", "_blank", "width=980,height=760");
    if (!popup) {
      setError("Unable to open purchase order document. Allow popups and try again.");
      return;
    }
    popup.document.open();
    popup.document.write(documentHtml);
    popup.document.close();
    popup.focus();
    updatePoLifecycle(record.sourceIds, { generatedAt: new Date().toISOString(), printedAt: new Date().toISOString(), status: "Generated" });
    if (mode === "print" || mode === "pdf") {
      popup.print();
      setSuccess(mode === "pdf" ? "PO opened for printing. Use Save as PDF to generate the supplier copy." : "PO opened for printing.");
    }
  }, [renderPurchaseOrderDocument, updatePoLifecycle]);

  const queuePurchaseOrderForInvoice = React.useCallback((record: PurchaseOrderWorkspaceRecord) => {
    setInvoiceForm((current) => ({
      ...current,
      supplierId: record.supplierId,
      purchaseOrderId: record.isDraft ? null : record.sourceIds[0] || null,
      items: record.items,
    }));
    if (!record.isDraft && record.sourceIds[0]) {
      syncInvoiceFromPurchaseOrder(record.sourceIds[0]);
    }
    setInvoiceEditorOpen(true);
    updateProcurementWorkspaceRoute("supplier-invoices", null);
  }, [syncInvoiceFromPurchaseOrder, updateProcurementWorkspaceRoute]);

  const queuePurchaseOrderForGoodsReceipt = React.useCallback((record: PurchaseOrderWorkspaceRecord) => {
    if (!record.isDraft && record.sourceIds[0]) {
      syncGoodsReceiptFromPurchaseOrder(record.sourceIds[0]);
      setGrnEditorOpen(true);
      updateProcurementWorkspaceRoute("goods-receipt", null);
      return;
    }
    setGrnEditorOpen(true);
    updateProcurementWorkspaceRoute("goods-receipt", null);
  }, [syncGoodsReceiptFromPurchaseOrder, updateProcurementWorkspaceRoute]);

  const upsertPurchaseOrderItem = React.useCallback(() => {
    const line = validateProcurementLineForm();
    if (!line) return;
    const duplicateIndex = poItems.findIndex((item, index) => {
      if (poItemEditIndex === index) return false;
      if (line.medicineId && item.medicineId && item.medicineId === line.medicineId) return true;
      if (!line.medicineId && !item.medicineId) return procurementLineKey(item) === procurementLineKey(line);
      return false;
    });
    if (duplicateIndex >= 0) {
      setError("This medicine is already added. Update quantity instead?");
      setProcurementLineFieldErrors({ medicineId: "This medicine is already added. Update quantity instead?" });
      focusFirstInvalidField("procurement-line-medicine");
      return;
    }
    setPoItems((current) => {
      const next = [...current];
      if (poItemEditIndex != null) {
        next[poItemEditIndex] = line;
      } else {
        next.push(line);
      }
      return next;
    });
    resetProcurementLineEditor();
  }, [poItemEditIndex, poItems, resetProcurementLineEditor, validateProcurementLineForm]);

  const editPurchaseOrderItem = React.useCallback((index: number) => {
    const item = poItems[index];
    if (!item) return;
    setPoItemEditIndex(index);
    setProcurementLine({
      medicineId: item.medicineId || "",
      medicineName: item.medicineName || "",
      quantity: String(item.quantity ?? ""),
      expectedUnitCost: item.expectedUnitCost != null ? String(item.expectedUnitCost) : "",
      unitCost: item.unitCost != null ? String(item.unitCost) : "",
      taxPercent: item.taxPercent != null ? String(item.taxPercent) : "",
      batchNumber: item.batchNumber || "",
      expiryDate: item.expiryDate || "",
      sellingPrice: item.sellingPrice != null ? String(item.sellingPrice) : "",
    });
    setProcurementLineFieldErrors(emptyErrors());
  }, [poItems]);

  const removePurchaseOrderItem = React.useCallback((index: number) => {
    setPoItems((current) => current.filter((_, itemIndex) => itemIndex !== index));
    setPoItemEditIndex((current) => (current != null && current === index ? null : current != null && current > index ? current - 1 : current));
  }, []);

  const composeApprovalNote = React.useCallback(() => {
    const parts = [reconReason.trim(), reconRemarks.trim()].filter(Boolean);
    return parts.length ? parts.join(" - ") : null;
  }, [reconReason, reconRemarks]);

  const validateReconciliationForm = React.useCallback((): VendorReconciliationValues | null => {
    const payload = {
      locationId: selectedLocationId || null,
      supplierId: reconForm.supplierId || null,
      medicineId: reconForm.medicineId || null,
      stockBatchId: reconForm.stockBatchId || null,
      physicalQuantity: reconForm.physicalQuantity ? Number(reconForm.physicalQuantity) : null,
      reason: composeApprovalNote(),
      sheetFile: sheetFile,
    };
    const parsed = vendorReconciliationSchema.safeParse(payload);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setReconFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("recon-location", "recon-supplier", "recon-medicine", "recon-batch", "recon-qty");
      return null;
    }
    const supplier = parsed.data.supplierId ? suppliers.find((row) => row.id === parsed.data.supplierId) : null;
    if (supplier && !supplier.active) {
      const errors = { supplierId: "Inactive supplier cannot be used for reconciliation." };
      setReconFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("recon-supplier");
      return null;
    }
    setReconFieldErrors(emptyErrors());
    return parsed.data;
  }, [composeApprovalNote, reconForm.medicineId, reconForm.physicalQuantity, reconForm.stockBatchId, reconForm.supplierId, selectedLocationId, sheetFile, suppliers]);

  const validatePurchaseOrderForm = React.useCallback((): PurchaseOrderValues | null => {
    if (!supplierCount) {
      setError("Create a supplier before creating a Purchase Order.");
      setPoFieldErrors({ supplierId: "Create a supplier before creating a Purchase Order." });
      return null;
    }
    if (!poItems.length) {
      setError("Add at least one item to the Purchase Order.");
      setPoFieldErrors({ items: "Add at least one item to the Purchase Order." });
      return null;
    }
    const parsed = purchaseOrderSchema.safeParse({ ...poForm, supplierId: poForm.supplierId || "", items: poItems });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setPoFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("po-supplier", "po-number", "po-order-date", "po-expected-delivery");
      return null;
    }
    const supplier = suppliers.find((row) => row.id === parsed.data.supplierId);
    if (supplier && !supplier.active) {
      const errors = { supplierId: "Inactive supplier cannot be used for procurement." };
      setPoFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("po-supplier");
      return null;
    }
    const currentPoNumber = editingPurchaseOrderNumber?.trim().toLowerCase() || null;
    const duplicateSavedPo = purchaseOrders.some((row) => {
      const normalized = row.poNumber.trim().toLowerCase();
      if (currentPoNumber && normalized === currentPoNumber) return false;
      return normalized === parsed.data.poNumber.trim().toLowerCase();
    });
    const duplicateDraftPo = poDrafts.some((row) => row.id !== editingDraftId && row.poNumber.trim().toLowerCase() === parsed.data.poNumber.trim().toLowerCase());
    if (duplicateSavedPo || duplicateDraftPo) {
      const errors = { poNumber: "Purchase order already exists" };
      setPoFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("po-number");
      return null;
    }
    setPoFieldErrors(emptyErrors());
    return parsed.data;
  }, [editingDraftId, editingPurchaseOrderNumber, poDrafts, poForm, poItems, purchaseOrders, supplierCount, suppliers]);

  const savePurchaseOrderDraft = React.useCallback(() => {
    const poNumber = poForm.poNumber.trim() || `DRAFT-${Date.now()}`;
    const duplicateSavedPo = purchaseOrders.some((row) => normalizePoNumber(row.poNumber) === normalizePoNumber(poNumber));
    const duplicateDraftPo = poDrafts.some((row) => row.id !== editingDraftId && normalizePoNumber(row.poNumber) === normalizePoNumber(poNumber));
    if (duplicateSavedPo || duplicateDraftPo) {
      setError("Purchase order already exists");
      setPoFieldErrors({ poNumber: "Purchase order already exists" });
      focusFirstInvalidField("po-number");
      return;
    }
    const draft: LocalPurchaseOrderDraft = {
      id: editingDraftId || `draft-${Date.now()}`,
      supplierId: poForm.supplierId,
      poNumber,
      orderDate: poForm.orderDate,
      expectedDeliveryDate: poForm.expectedDeliveryDate,
      approvalNote: poForm.approvalNote,
      items: poItems,
      savedAt: new Date().toISOString(),
    };
    setPoDrafts((current) => [draft, ...current.filter((row) => row.id !== draft.id)]);
    setEditingDraftId(draft.id);
    setPoForm((current) => ({ ...current, poNumber }));
    setSuccess("Purchase order draft saved");
  }, [editingDraftId, poDrafts, poForm, poItems, purchaseOrders]);

  const validateInvoiceForm = React.useCallback((): SupplierInvoiceValues | null => {
    if (!supplierCount) {
      setError("Create a supplier before creating a supplier invoice.");
      setInvoiceFieldErrors({ supplierId: "Create a supplier before creating a supplier invoice." });
      return null;
    }
    const invoiceItems = invoiceForm.purchaseOrderId ? selectedInvoicePurchaseOrderItems : invoiceForm.items;
    if (!invoiceItems.length) {
      setError("Select a purchase order before creating a supplier invoice.");
      setInvoiceFieldErrors({ purchaseOrderId: "Select a purchase order before creating a supplier invoice." });
      return null;
    }
    const parsed = supplierInvoiceSchema.safeParse({ ...invoiceForm, supplierId: invoiceForm.supplierId || "", items: invoiceItems });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setInvoiceFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("invoice-supplier", "invoice-po", "invoice-number", "invoice-date", "invoice-tax", "invoice-total");
      return null;
    }
    const supplier = suppliers.find((row) => row.id === parsed.data.supplierId);
    if (supplier && !supplier.active) {
      const errors = { supplierId: "Inactive supplier cannot be used for invoicing." };
      setInvoiceFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("invoice-supplier");
      return null;
    }
    setInvoiceFieldErrors(emptyErrors());
    return parsed.data;
  }, [invoiceForm, selectedInvoicePurchaseOrderItems, supplierCount, suppliers]);

  const validateGoodsReceiptForm = React.useCallback((): GoodsReceiptValues | null => {
    if (!supplierCount) {
      setError("Create a supplier before creating goods receipt.");
      setGrnFieldErrors({ supplierId: "Create a supplier before creating goods receipt." });
      return null;
    }
    if (!grnForm.purchaseOrderId) {
      setError("Select a purchase order before creating goods receipt.");
      setGrnFieldErrors({ purchaseOrderId: "Select a purchase order before creating goods receipt." });
      return null;
    }
    if (!grnForm.items.length) {
      setError("No purchase order line items are available for goods receipt.");
      setGrnFieldErrors({ items: "No purchase order line items are available for goods receipt." });
      return null;
    }
    const parsed = goodsReceiptSchema.safeParse({ ...grnForm, supplierId: grnForm.supplierId || "", locationId: grnForm.locationId || "", items: grnForm.items });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setGrnFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("grn-supplier", "grn-location", "grn-receipt", "grn-received-at");
      return null;
    }
    const supplier = suppliers.find((row) => row.id === parsed.data.supplierId);
    if (supplier && !supplier.active) {
      const errors = { supplierId: "Inactive supplier cannot be used for goods receipt." };
      setGrnFieldErrors(errors);
      setError(pickFirstError(errors));
      focusFirstInvalidField("grn-supplier");
      return null;
    }
    setGrnFieldErrors(emptyErrors());
    return parsed.data;
  }, [grnForm, supplierCount, suppliers]);

  const submitSupplier = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validateSupplierForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      const payload = normalizeSupplierPayload(parsed);
      if (supplierId) {
        await updateSupplier(auth.accessToken, auth.tenantId, supplierId, payload);
      } else {
        await createSupplier(auth.accessToken, auth.tenantId, payload);
      }
      setSupplierForm(emptySupplier);
      setSupplierId(null);
      setSupplierNotes("");
      setSupplierFieldErrors(emptyErrors());
      setSuccess("Supplier saved successfully.");
      if (!supplierId) {
        updateProcurementWorkspaceRoute("purchase-orders");
      }
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save supplier");
    } finally {
      setSaving(false);
    }
  };

  const submitInward = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validateInwardForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      await createStockInward(auth.accessToken, auth.tenantId, parsed);
      setInwardForm(emptyInward);
      setInwardMedicineSearch("");
      setInwardFieldErrors(emptyErrors());
      setSuccess("Direct goods receipt posted and inventory updated.");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record stock inward");
    } finally {
      setSaving(false);
    }
  };

  const createDraftReconciliation = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validateReconciliationForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      const created = await createStockReconciliationDraft(parsed);
      setSelectedReconciliationId(created.id);
      setReconForm(emptyReconciliation);
      setReconReason("");
      setReconRemarks("");
      setSheetFile(null);
      setSuccess(sheetFile ? "Reconciliation draft created and vendor sheet uploaded" : "Reconciliation draft created");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create reconciliation draft");
    } finally {
      setSaving(false);
    }
  };

  const createStockReconciliationDraft = React.useCallback(async (parsed: VendorReconciliationValues) => {
    if (!auth.accessToken || !auth.tenantId) {
      throw new Error("Missing pharmacy session");
    }
    const created = await createReconciliation(auth.accessToken, auth.tenantId, {
      medicineId: parsed.medicineId,
      stockBatchId: parsed.stockBatchId || null,
      supplierId: parsed.supplierId || null,
      locationId: parsed.locationId || null,
      physicalQuantity: parsed.physicalQuantity,
      reason: parsed.reason || null,
    });
    if (sheetFile) {
      const uploaded = await uploadReconciliationSheet(auth.accessToken, auth.tenantId, created.id, sheetFile);
      setReviewRows(uploaded.extractedRows);
    } else {
      setReviewRows([]);
    }
    return created;
  }, [auth.accessToken, auth.tenantId, sheetFile]);

  const saveReviewedRows = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedReconciliationId || !reviewRows.length) return;
    setSaving(true);
    setError(null);
    try {
      await reviewReconciliationSheet(auth.accessToken, auth.tenantId, selectedReconciliationId, {
        rows: reviewRows,
        reviewNote: "Row review completed from compact reconciliation workspace",
      });
      setSuccess("Reconciliation rows reviewed");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save reviewed rows");
    } finally {
      setSaving(false);
    }
  };

  const submitRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await submitReconciliationApi(auth.accessToken, auth.tenantId, row.id);
      setSuccess("Reconciliation submitted for review");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const approveRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await approveReconciliation(auth.accessToken, auth.tenantId, row.id, { reason: row.reason || null });
      setSuccess("Reconciliation approved");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const rejectRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await rejectReconciliation(auth.accessToken, auth.tenantId, row.id, { reason: row.reason || "Reconciliation rejected" });
      setSuccess("Reconciliation rejected");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const postRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const physical = row.physicalQuantity ?? row.systemQuantity;
    setSaving(true);
    setError(null);
    try {
      await postReconciliation(auth.accessToken, auth.tenantId, row.id, {
        physicalQuantity: physical,
        reason: row.reason || "Stock count adjustment",
      });
      setSuccess("Reconciliation posted");
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const savePurchaseOrder = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validatePurchaseOrderForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      const created = await createPurchaseOrder(auth.accessToken, auth.tenantId, parsed);
      updatePoLifecycle([created.id], { status: "Generated", generatedAt: new Date().toISOString() });
      if (editingDraftId) {
        setPoDrafts((current) => current.filter((row) => row.id !== editingDraftId));
        setEditingDraftId(null);
      }
      setSuccess("Purchase order saved.");
      resetPurchaseOrderEditor();
      setPoEditorOpen(false);
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save purchase order");
    } finally {
      setSaving(false);
    }
  };

  const saveSupplierInvoice = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validateInvoiceForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      const noteParts = [
        invoiceVariance.trim() ? `Variance: ${invoiceVariance.trim()}` : null,
        invoiceDiscount.trim() ? `Discount: ${invoiceDiscount.trim()}` : null,
        invoiceFreight.trim() ? `Freight: ${invoiceFreight.trim()}` : null,
        invoiceForm.approvalNote?.trim() || null,
      ].filter(Boolean);
      await createSupplierInvoice(auth.accessToken, auth.tenantId, {
        ...parsed,
        approvalNote: noteParts.length ? noteParts.join(" | ") : parsed.approvalNote,
      });
      setSuccess("Supplier invoice saved successfully.");
      setInvoiceForm({ supplierId: "", purchaseOrderId: null, invoiceNumber: "", invoiceDate: "", taxAmount: null, totalAmount: null, items: [], approvalNote: null });
      setInvoiceDiscount("");
      setInvoiceFreight("");
      setInvoiceVariance("");
      setInvoiceFieldErrors(emptyErrors());
      setInvoiceEditorOpen(false);
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save supplier invoice");
    } finally {
      setSaving(false);
    }
  };

  const saveGoodsReceipt = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = validateGoodsReceiptForm();
    if (!parsed) return;
    setSaving(true);
    setError(null);
    try {
      await createGoodsReceipt(auth.accessToken, auth.tenantId, parsed);
      setSuccess("Goods receipt saved successfully.");
      setGrnForm({ supplierId: "", purchaseOrderId: null, supplierInvoiceId: null, receiptNumber: "", receivedAt: "", locationId: "", items: [], approvalNote: null });
      setGrnFieldErrors(emptyErrors());
      setGrnEditorOpen(false);
      await refreshCurrentPageData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save goods receipt");
    } finally {
      setSaving(false);
    }
  };

  const procurementRecentRows = workspace === "purchase-orders" ? purchaseOrders : workspace === "supplier-invoices" ? supplierInvoices : goodsReceipts;
  const isPurchaseOrderWorkspace = workspace === "purchase-orders";
  const hasEmptyProcurementRecords = isPurchaseOrderWorkspace ? purchaseOrderWorkspaceRecords.length === 0 : procurementRecentRows.length === 0;
  const activePurchaseOrderActionRow = React.useMemo(
    () => purchaseOrderWorkspaceRecords.find((row) => row.id === poActionsRowId) ?? null,
    [poActionsRowId, purchaseOrderWorkspaceRecords],
  );

  const handleSheetFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] || null;
    if (!file) {
      setSheetFile(null);
      return;
    }
    const parsed = fileUploadSchema({
      required: true,
      allowedMimeTypes: ["application/pdf", "image/png", "image/jpeg", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "application/csv"],
      allowedExtensions: ["pdf", "png", "jpg", "jpeg", "csv", "xls", "xlsx"],
      maxBytes: 25 * 1024 * 1024,
    }).safeParse(file);
    if (!parsed.success) {
      setSheetFile(null);
      setError(firstZodError(parsed.error));
      event.target.value = "";
      return;
    }
    setSheetFile(file);
  };

  const showAnalytics = false;

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access pharmacy procurement and reconciliation.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>{pageMode === "procurement" ? "Procurement" : "Reconciliation"}</Typography>
          <Typography variant="body2" color="text.secondary">
            {pageMode === "procurement"
              ? "Manage supplier purchasing, invoices, and goods receipt for pharmacy stock."
              : "Review stock differences, supplier bill variances, and approval workflows."}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button size="small" variant="outlined" onClick={() => navigate("/inventory")}>Open Inventory</Button>
          <Button size="small" variant="outlined" onClick={() => void refreshCurrentPageData()}>Refresh</Button>
        </Stack>
      </Box>

      <CompactFilterCard
        title="Workflow guidance"
        subtitle={pageMode === "procurement"
          ? "Supplier → Purchase Order → Generate / Send PO → Invoice → Goods Receipt → Stock Added"
          : "Medicine Master → Receive via Procurement / Direct Goods Receipt → Create Session → Upload/Enter Count → Review Differences → Submit → Approve → Posted"}
      >
        <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
          {(pageMode === "procurement"
            ? ["Supplier", "Purchase Order", "Generate / Send PO", "Invoice", "Goods Receipt", "Stock Added"]
            : ["Medicine Master", "Receive via Procurement", "Create Session", "Upload/Enter Count", "Review Differences", "Submit", "Approve", "Posted"]
          ).map((step, index) => (
            <Chip
              key={step}
              size="small"
              label={`${index + 1}. ${step}`}
              color={pageMode === "procurement"
                ? (
                  (index === 0 && workspace === "suppliers") ||
                  (index === 1 && workspace === "purchase-orders") ||
                  (index === 2 && workspace === "purchase-orders" && purchaseOrders.length > 0) ||
                  (index === 3 && workspace === "supplier-invoices") ||
                  (index === 4 && workspace === "goods-receipt") ||
                  (index === 5 && goodsReceipts.length > 0)
                    ? "primary"
                    : index === 0 && supplierCount === 0
                      ? "warning"
                      : index === 5 && goodsReceipts.length > 0
                        ? "success"
                        : "default"
                )
                : (index === 0 ? (medicineCount ? "success" : "warning") : index === 1 ? (stockBatchCount ? "success" : "warning") : index === 2 ? (stockBatchCount ? "primary" : "warning") : step === "Posted" ? "success" : "default")}
              variant="outlined"
              sx={compactChipSx}
            />
          ))}
        </Stack>
      </CompactFilterCard>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}
      {!canManageOperations ? (
        <Alert severity="info">
          Read-only procurement and reconciliation access is active for this role. Supplier, inward, procurement, and reconciliation write actions are hidden or disabled.
        </Alert>
      ) : null}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Stock batches" value={dashboard?.stockBatchesCount ?? 0} helper="Across locations" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Pending dispense" value={dashboard?.pendingDispensingCount ?? 0} tone="warning" helper="Queue for pharmacy" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Low stock" value={dashboard?.lowStockCount ?? 0} tone={dashboard?.lowStockCount ? "error" : "success"} helper="Needs reorder" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Near expiry" value={dashboard?.nearExpiryCount ?? 0} tone={dashboard?.nearExpiryCount ? "warning" : "success"} helper="Watch batches" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Today dispensed" value={dashboard?.todayDispensedCount ?? 0} helper="Operational output" />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          {pageMode === "procurement" ? (
            <Box
              role="tablist"
              aria-label="Procurement workspaces"
              sx={{
                display: "flex",
                flexWrap: "wrap",
                gap: 1,
              }}
            >
              {([
                { value: "suppliers", label: "Suppliers", focus: "supplier" },
                { value: "purchase-orders", label: "Purchase Orders" },
                { value: "supplier-invoices", label: "Supplier Invoices" },
                { value: "goods-receipt", label: "Goods Receipt" },
              ] as Array<{ value: ProcurementWorkspace; label: string; focus?: string }>).map((item) => {
                const selected = workspace === item.value;
                const focus = item.value === "suppliers" ? (item.focus ?? "supplier") : null;
                return (
                  <ButtonBase
                    key={item.value}
                    role="tab"
                    aria-selected={selected}
                    tabIndex={0}
                    onClick={() => updateProcurementWorkspaceRoute(item.value, focus)}
                    onKeyDown={(event) => {
                      if (event.key !== "Enter" && event.key !== " ") return;
                      event.preventDefault();
                      updateProcurementWorkspaceRoute(item.value, focus);
                    }}
                    sx={{
                      px: 1.5,
                      py: 1,
                      borderRadius: 999,
                      border: "1px solid",
                      borderColor: selected ? "primary.main" : "divider",
                      bgcolor: selected ? "action.selected" : "background.paper",
                      color: selected ? "primary.main" : "text.secondary",
                      fontWeight: selected ? 800 : 600,
                      cursor: "pointer",
                      transition: "background-color 120ms ease, border-color 120ms ease, color 120ms ease",
                      "&:hover": {
                        bgcolor: selected ? "action.selected" : "action.hover",
                      },
                      "&:focus-visible": {
                        outline: "2px solid",
                        outlineColor: "primary.main",
                        outlineOffset: 2,
                      },
                    }}
                  >
                    {item.label}
                  </ButtonBase>
                );
              })}
            </Box>
          ) : (
            <Tabs
              value={tab}
              onChange={(_, value) => {
                setTab(value as OpsTab);
              }}
              variant="scrollable"
              scrollButtons="auto"
            >
              <Tab value="reconciliation" label="Supplier Bill Reconciliation" />
              <Tab value="physical-count" label="Physical Count" />
              <Tab value="stock-adjustments" label="Stock Adjustments" />
              <Tab value="approval-review" label="Approval Review" />
            </Tabs>
          )}
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 240, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && pageMode === "procurement" && workspace === "suppliers" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4.2 }}>
            <CompactFilterCard
              title={supplierId ? "Edit supplier" : "Add supplier"}
              subtitle="Compact supplier master for procurement and inward stock."
            >
              <Grid container spacing={1.25}>
                <Grid size={12}>
                  <TextField
                    id="supplier-name"
                    size="small"
                    fullWidth
                    label={<RequiredLabel text="Supplier name" required />}
                    value={supplierForm.supplierName}
                    onChange={(e) => setSupplierForm((s) => ({ ...s, supplierName: e.target.value }))}
                    required
                    error={Boolean(supplierFieldErrors.supplierName)}
                    helperText={supplierFieldErrors.supplierName || "Required, 2-100 characters."}
                    inputProps={{ "aria-required": true, maxLength: 100 }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField id="supplier-gst" size="small" fullWidth label="GSTIN" value={supplierForm.gstNumber || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, gstNumber: e.target.value || null }))} error={Boolean(supplierFieldErrors.gstNumber)} helperText={supplierFieldErrors.gstNumber || "Optional. Valid GSTIN if entered."} inputProps={{ maxLength: 15 }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField id="supplier-contact-person" size="small" fullWidth label="Contact person" value={supplierForm.contactPerson || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, contactPerson: e.target.value || null }))} error={Boolean(supplierFieldErrors.contactPerson)} helperText={supplierFieldErrors.contactPerson || "Optional. Must include a letter or number if entered."} inputProps={{ maxLength: 60 }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField id="supplier-phone" size="small" fullWidth label="Phone" value={supplierForm.phone || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, phone: e.target.value || null }))} error={Boolean(supplierFieldErrors.phone)} helperText={supplierFieldErrors.phone || "Optional Indian mobile."} inputProps={{ inputMode: "tel" }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField id="supplier-email" size="small" fullWidth label="Email" value={supplierForm.email || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, email: e.target.value || null }))} error={Boolean(supplierFieldErrors.email)} helperText={supplierFieldErrors.email || "Optional valid email."} inputProps={{ maxLength: 120 }} />
                </Grid>
                <Grid size={12}>
                  <TextField id="supplier-address" size="small" fullWidth label="Address" value={supplierForm.address || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, address: e.target.value || null }))} multiline minRows={2} error={Boolean(supplierFieldErrors.address)} helperText={supplierFieldErrors.address || "Optional."} inputProps={{ maxLength: 250 }} />
                </Grid>
                <Grid size={12}>
                  <TextField id="supplier-notes" size="small" fullWidth label="Notes" value={supplierNotes} onChange={(e) => setSupplierNotes(e.target.value)} multiline minRows={2} helperText="Optional internal note kept in the procurement workspace." inputProps={{ maxLength: 250 }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControl fullWidth size="small" error={Boolean(supplierFieldErrors.active)}>
                    <InputLabel id="supplier-active-label"><RequiredLabel text="Active" required /></InputLabel>
                    <Select id="supplier-active" labelId="supplier-active-label" label="Active" value={supplierForm.active ? "true" : "false"} onChange={(e) => setSupplierForm((s) => ({ ...s, active: String(e.target.value) === "true" }))} required inputProps={{ "aria-required": true }}>
                      <MenuItem value="true">Active</MenuItem>
                      <MenuItem value="false">Inactive</MenuItem>
                    </Select>
                    {supplierFieldErrors.active ? <Typography variant="caption" color="error">{supplierFieldErrors.active}</Typography> : null}
                  </FormControl>
                </Grid>
              </Grid>
              <Stack direction="row" spacing={1}>
                <Button size="small" variant="contained" onClick={() => void submitSupplier()} disabled={!canManageOperations || saving}>{supplierId ? "Update" : "Save supplier"}</Button>
                <Button size="small" onClick={() => { setSupplierForm(emptySupplier); setSupplierId(null); setSupplierNotes(""); }}>Reset</Button>
              </Stack>
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.8 }}>
            <CompactFilterCard
              title="Supplier list"
              subtitle={`${filteredSuppliers.length} visible suppliers`}
              actions={(
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant="contained" onClick={() => { setSupplierId(null); setSupplierForm(emptySupplier); setSupplierNotes(""); }}>
                    Add Supplier
                  </Button>
                  <TextField size="small" placeholder="Search supplier" value={supplierSearch} onChange={(e) => setSupplierSearch(e.target.value)} sx={{ minWidth: 220 }} />
                </Stack>
              )}
            >
              {supplierCount === 0 ? (
                <CompactEmptyState
                  title="No suppliers have been created."
                  subtitle="Create your first supplier before creating Purchase Orders."
                  action={<Button size="small" variant="contained" onClick={() => updateProcurementWorkspaceRoute("suppliers", "supplier")}>Create Supplier</Button>}
                />
              ) : filteredSuppliers.length === 0 ? (
                <CompactEmptyState title="No suppliers found." subtitle="Adjust the search or create another supplier." />
              ) : (
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 500 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Supplier</TableCell>
                        <TableCell>Last procurement</TableCell>
                        <TableCell align="right">Outstanding</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredSuppliers.map((row) => {
                        const lastInvoice = supplierInvoices.find((invoice) => invoice.supplierId === row.id);
                        const lastReceipt = goodsReceipts.find((receipt) => receipt.supplierId === row.id);
                        const lastProcurement = lastReceipt?.receivedAt || lastInvoice?.invoiceDate || null;
                        return (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.supplierName}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {[row.contactPerson, row.phone, row.email].filter(Boolean).join(" • ") || "No contact details"}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(lastProcurement)}</TableCell>
                            <TableCell align="right">{lastInvoice?.totalAmount != null ? `INR ${money(lastInvoice.totalAmount)}` : "-"}</TableCell>
                            <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                                <Button
                                  size="small"
                                  onClick={() => {
                                    setSupplierId(row.id);
                                    setSupplierForm({
                                      supplierName: row.supplierName,
                                      contactPerson: row.contactPerson,
                                      phone: row.phone,
                                      email: row.email,
                                      gstNumber: row.gstNumber,
                                      address: row.address,
                                      active: row.active,
                                    });
                                    setSupplierNotes("");
                                  }}
                                >
                                  View
                                </Button>
                                <Button
                                  size="small"
                                  onClick={() => {
                                    setSupplierId(row.id);
                                    setSupplierForm({
                                      supplierName: row.supplierName,
                                      contactPerson: row.contactPerson,
                                      phone: row.phone,
                                      email: row.email,
                                      gstNumber: row.gstNumber,
                                      address: row.address,
                                      active: row.active,
                                    });
                                    setSupplierNotes("");
                                  }}
                                >
                                  Edit
                                </Button>
                                <Button
                                  size="small"
                                  color="inherit"
                                  disabled={!canManageOperations || saving}
                                  onClick={async () => {
                                    if (!auth.accessToken || !auth.tenantId) return;
                                    setSaving(true);
                                    setError(null);
                                    try {
                                      await updateSupplier(auth.accessToken, auth.tenantId, row.id, {
                                        supplierName: row.supplierName,
                                        contactPerson: row.contactPerson,
                                        phone: row.phone,
                                        email: row.email,
                                        gstNumber: row.gstNumber,
                                        address: row.address,
                                        active: !row.active,
                                      });
                                      setSuccess(row.active ? "Supplier deactivated" : "Supplier activated");
                                      await refreshCurrentPageData();
                                    } catch (err) {
                                      setError(err instanceof Error ? err.message : "Failed to update supplier status");
                                    } finally {
                                      setSaving(false);
                                    }
                                  }}
                                >
                                  {row.active ? "Deactivate" : "Activate"}
                                </Button>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {!loading && pageMode === "procurement" && workspace === "goods-receipt" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4.5 }}>
            <CompactFilterCard
              title={goodsReceiptMode === "direct" ? "Direct Goods Receipt" : "Goods Receipt"}
              subtitle={goodsReceiptMode === "direct"
                ? "Receive opening stock, emergency purchases, donations, and migration stock without a purchase order."
                : "Compact GRN-style inward entry aligned with the inventory batch workflow."}
              actions={(
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant={goodsReceiptMode === "po" ? "contained" : "outlined"} onClick={() => updateGoodsReceiptMode("po")}>PO Goods Receipt</Button>
                  <Button size="small" variant={goodsReceiptMode === "direct" ? "contained" : "outlined"} onClick={() => updateGoodsReceiptMode("direct")}>Direct Goods Receipt</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/inventory")}>Open Inventory Batch Workspace</Button>
                  <Button size="small" variant="outlined" onClick={() => updateProcurementWorkspaceRoute("purchase-orders")}>Open Purchase Orders / Invoices</Button>
                </Stack>
              )}
            >
              {goodsReceiptMode === "direct" ? (
                medicineCount === 0 ? (
                  <CompactEmptyState
                    title="No medicines are available for direct goods receipt."
                    subtitle="Add medicines before receiving opening stock, migrated stock, or emergency inward quantities."
                    action={<Button size="small" variant="contained" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>}
                  />
                ) : (
                  <Stack spacing={1.25}>
                    <Alert severity="info" sx={{ py: 0 }}>
                      Direct goods receipt is for opening stock, migration, emergency purchase, donation, or local distributor inward. Inventory updates immediately after posting.
                    </Alert>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Autocomplete<MedicineOption, false, false, false>
                          options={medicineOptions}
                          value={medicineOptions.find((option) => option.medicine.id === inwardForm.medicineId) ?? null}
                          onChange={(_, value) => setInwardForm((current) => ({ ...current, medicineId: value?.medicine.id || "" }))}
                          getOptionLabel={(option) => option.medicine.medicineName}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              id="inward-medicine"
                              size="small"
                              label={<RequiredLabel text="Medicine" required />}
                              required
                              error={Boolean(inwardFieldErrors.medicineId)}
                              helperText={inwardFieldErrors.medicineId || "Select a medicine from the catalogue."}
                              inputProps={{ ...params.inputProps, "aria-required": true }}
                            />
                          )}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small" error={Boolean(inwardFieldErrors.supplierId)}>
                          <InputLabel id="inward-supplier-label">Supplier (optional)</InputLabel>
                          <Select id="inward-supplier" labelId="inward-supplier-label" label="Supplier (optional)" value={inwardForm.supplierId || ""} onChange={(e) => setInwardForm((current) => ({ ...current, supplierId: String(e.target.value) || null }))}>
                            <MenuItem value="">No supplier</MenuItem>
                            {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                          </Select>
                          {inwardFieldErrors.supplierId ? <Typography variant="caption" color="error">{inwardFieldErrors.supplierId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small" error={Boolean(inwardFieldErrors.locationId)}>
                          <InputLabel id="inward-location-label"><RequiredLabel text="Location" required /></InputLabel>
                          <Select id="inward-location" labelId="inward-location-label" label="Location" value={inwardForm.locationId || selectedLocationId} onChange={(e) => setInwardForm((current) => ({ ...current, locationId: String(e.target.value) || null }))} required inputProps={{ "aria-required": true }}>
                            <MenuItem value="">Default location</MenuItem>
                            {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>)}
                          </Select>
                          {inwardFieldErrors.locationId ? <Typography variant="caption" color="error">{inwardFieldErrors.locationId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-inward-date" size="small" fullWidth type="date" label={<RequiredLabel text="Received / inward date" required />} InputLabelProps={{ shrink: true }} value={inwardForm.purchaseDate || ""} onChange={(e) => setInwardForm((current) => ({ ...current, purchaseDate: e.target.value || null }))} required error={Boolean(inwardFieldErrors.purchaseDate)} helperText={inwardFieldErrors.purchaseDate || "Required. Cannot be future dated."} inputProps={{ "aria-required": true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-grn" size="small" fullWidth label="Receipt / reference number" value={inwardForm.purchaseReferenceNumber || ""} onChange={(e) => setInwardForm((current) => ({ ...current, purchaseReferenceNumber: e.target.value || null }))} error={Boolean(inwardFieldErrors.purchaseReferenceNumber)} helperText={inwardFieldErrors.purchaseReferenceNumber || "Optional direct receipt or migration reference."} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-batch" size="small" fullWidth label="Batch number" value={inwardForm.batchNumber || ""} onChange={(e) => setInwardForm((current) => ({ ...current, batchNumber: e.target.value || null }))} error={Boolean(inwardFieldErrors.batchNumber)} helperText={inwardFieldErrors.batchNumber || "Optional if batch not available yet."} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-expiry" size="small" fullWidth type="date" label="Expiry date" InputLabelProps={{ shrink: true }} value={inwardForm.expiryDate || ""} onChange={(e) => setInwardForm((current) => ({ ...current, expiryDate: e.target.value || null }))} error={Boolean(inwardFieldErrors.expiryDate)} helperText={inwardFieldErrors.expiryDate || "Optional, future date if provided."} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-qty" size="small" fullWidth type="number" label={<RequiredLabel text="Quantity" required />} value={inwardForm.quantity || ""} onChange={(e) => setInwardForm((current) => ({ ...current, quantity: Number(e.target.value || "0") }))} required error={Boolean(inwardFieldErrors.quantity)} helperText={inwardFieldErrors.quantity || "Required whole number greater than zero."} inputProps={{ min: 1, step: 1, "aria-required": true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-threshold" size="small" fullWidth type="number" label="Reorder level" value={inwardForm.lowStockThreshold ?? ""} onChange={(e) => setInwardForm((current) => ({ ...current, lowStockThreshold: e.target.value ? Number(e.target.value) : null }))} error={Boolean(inwardFieldErrors.lowStockThreshold)} helperText={inwardFieldErrors.lowStockThreshold || "Optional threshold for replenishment alerts."} inputProps={{ min: 0, step: 1 }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-unit-cost" size="small" fullWidth type="number" label="Unit cost" value={inwardForm.unitCost ?? ""} onChange={(e) => setInwardForm((current) => ({ ...current, unitCost: e.target.value ? Number(e.target.value) : null }))} error={Boolean(inwardFieldErrors.unitCost)} helperText={inwardFieldErrors.unitCost || "Optional purchase cost."} inputProps={{ min: 0, step: "0.01" }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField id="inward-selling-price" size="small" fullWidth type="number" label="Selling price" value={inwardForm.sellingPrice ?? ""} onChange={(e) => setInwardForm((current) => ({ ...current, sellingPrice: e.target.value ? Number(e.target.value) : null }))} error={Boolean(inwardFieldErrors.sellingPrice)} helperText={inwardFieldErrors.sellingPrice || "Optional MRP / selling price."} inputProps={{ min: 0, step: "0.01" }} />
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button size="small" variant="contained" onClick={() => void submitInward()} disabled={!canManageOperations || saving}>Post Direct Goods Receipt</Button>
                      <Button size="small" onClick={() => { setInwardForm(emptyInward); setInwardFieldErrors(emptyErrors()); setInwardMedicineSearch(""); }}>Clear</Button>
                      {supplierCount === 0 ? <Button size="small" variant="outlined" onClick={() => updateProcurementWorkspaceRoute("suppliers", "supplier")}>Add Supplier</Button> : null}
                    </Stack>
                  </Stack>
                )
              ) : !purchaseOrders.length ? (
                <CompactEmptyState
                  title="No purchase orders are available for goods receipt."
                  subtitle="Create a purchase order before posting GRN or receiving stock."
                  action={<Button size="small" variant="contained" onClick={() => updateProcurementWorkspaceRoute("purchase-orders")}>Create PO</Button>}
                />
              ) : (
                <Stack spacing={1.25}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="grn-po-label"><RequiredLabel text="Purchase order" required /></InputLabel>
                    <Select id="grn-po" labelId="grn-po-label" label="Purchase order" value={grnForm.purchaseOrderId || ""} onChange={(e) => syncGoodsReceiptFromPurchaseOrder(String(e.target.value))} required inputProps={{ "aria-required": true }}>
                      <MenuItem value="">Select purchase order</MenuItem>
                      {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber} • {po.supplierName || "Supplier pending"}</MenuItem>)}
                    </Select>
                  </FormControl>
                  <Alert severity="info" sx={{ py: 0 }}>
                    Received quantities are edited per PO line. Keep batch, expiry, and pricing aligned with stock receipt.
                  </Alert>
                  {grnForm.purchaseOrderId ? (
                    <TableContainer sx={{ ...stickyTableSx, maxHeight: 260 }}>
                      <Table stickyHeader size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Item</TableCell>
                            <TableCell align="right">Received qty</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell>Expiry</TableCell>
                            <TableCell align="right">MRP</TableCell>
                            <TableCell align="right">Selling price</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {grnForm.items.length === 0 ? (
                            <TableRow>
                              <TableCell colSpan={6}>
                                <CompactEmptyState title="Selected purchase order has no item rows." subtitle="Create a purchase order with line items before receiving goods." />
                              </TableCell>
                            </TableRow>
                          ) : grnForm.items.map((item, index) => (
                            <TableRow key={`${procurementLineKey(item)}-${index}`}>
                              <TableCell>
                                <Stack spacing={0.2}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.medicineName || medicines.find((medicine) => medicine.id === item.medicineId)?.medicineName || "Medicine"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{item.medicineId || "Manual line item"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                <TextField size="small" type="number" value={item.quantity} onChange={(event) => updateGoodsReceiptItem(index, { quantity: Number(event.target.value || "0") })} sx={{ width: 100 }} inputProps={{ min: 1, step: 1 }} />
                              </TableCell>
                              <TableCell><TextField size="small" value={item.batchNumber || ""} onChange={(event) => updateGoodsReceiptItem(index, { batchNumber: event.target.value || null })} /></TableCell>
                              <TableCell><TextField size="small" type="date" InputLabelProps={{ shrink: true }} value={item.expiryDate || ""} onChange={(event) => updateGoodsReceiptItem(index, { expiryDate: event.target.value || null })} /></TableCell>
                              <TableCell align="right"><TextField size="small" type="number" value={item.expectedUnitCost ?? ""} onChange={(event) => updateGoodsReceiptItem(index, { expectedUnitCost: event.target.value ? Number(event.target.value) : null })} sx={{ width: 110 }} inputProps={{ min: 0, step: "0.01" }} /></TableCell>
                              <TableCell align="right"><TextField size="small" type="number" value={item.sellingPrice ?? ""} onChange={(event) => updateGoodsReceiptItem(index, { sellingPrice: event.target.value ? Number(event.target.value) : null })} sx={{ width: 110 }} inputProps={{ min: 0, step: "0.01" }} /></TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  ) : (
                    <CompactEmptyState title="Select a purchase order to load goods receipt line items." subtitle="The selected order will populate item rows, batch numbers, and expected pricing." />
                  )}
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small" error={Boolean(grnFieldErrors.supplierId)}>
                        <InputLabel id="grn-supplier-label"><RequiredLabel text="Supplier" required /></InputLabel>
                        <Select id="grn-supplier" labelId="grn-supplier-label" label="Supplier" value={grnForm.supplierId || ""} onChange={(e) => setGrnForm((s) => ({ ...s, supplierId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                          <MenuItem value="">Supplier from PO</MenuItem>
                          {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                        </Select>
                        {grnFieldErrors.supplierId ? <Typography variant="caption" color="error">{grnFieldErrors.supplierId}</Typography> : null}
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small" error={Boolean(grnFieldErrors.locationId)}>
                        <InputLabel id="grn-location-select"><RequiredLabel text="Location" required /></InputLabel>
                        <Select id="grn-location" labelId="grn-location-select" label="Location" value={grnForm.locationId || selectedLocationId} onChange={(e) => setGrnForm((s) => ({ ...s, locationId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                          <MenuItem value="">Default location</MenuItem>
                          {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>)}
                        </Select>
                        {grnFieldErrors.locationId ? <Typography variant="caption" color="error">{grnFieldErrors.locationId}</Typography> : null}
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField id="grn-receipt" size="small" fullWidth label={<RequiredLabel text="Receipt number" required />} value={grnForm.receiptNumber} onChange={(e) => setGrnForm((s) => ({ ...s, receiptNumber: e.target.value }))} required error={Boolean(grnFieldErrors.receiptNumber)} helperText={grnFieldErrors.receiptNumber || "Required, max 60 characters."} inputProps={{ "aria-required": true, maxLength: 60 }} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField id="grn-received-at" size="small" fullWidth label={<RequiredLabel text="Received at" required />} type="datetime-local" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((s) => ({ ...s, receivedAt: e.target.value }))} required error={Boolean(grnFieldErrors.receivedAt)} helperText={grnFieldErrors.receivedAt || "Required."} inputProps={{ "aria-required": true }} /></Grid>
                  </Grid>
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="contained" onClick={() => void saveGoodsReceipt()} disabled={!canManageOperations || saving || !grnForm.items.length}>Post GRN</Button>
                    <Button size="small" onClick={() => { setGrnForm({ supplierId: "", purchaseOrderId: null, supplierInvoiceId: null, receiptNumber: "", receivedAt: "", locationId: selectedLocationId || "", items: [], approvalNote: null }); }}>Clear</Button>
                  </Stack>
                </Stack>
              )}
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.5 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard
                title="Recent stock inward activity"
                subtitle="Inventory batches and movements appear here immediately after receipt posting."
              >
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 240 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell>Batch</TableCell>
                        <TableCell align="right">On hand</TableCell>
                        <TableCell>Expiry</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(dashboard?.recentStockMovements || []).slice(0, 12).map((row) => (
                        <TableRow key={row.id} hover>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {medicines.find((m) => m.id === row.medicineId)?.medicineName || "Medicine"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {row.businessReference || row.transactionType.replace(/_/g, " ")}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.batchNumber || stockMap.get(row.stockBatchId || "")?.batchNumber || "-"}</TableCell>
                          <TableCell align="right">{row.afterQuantity ?? "-"}</TableCell>
                          <TableCell>{formatDate(row.createdAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CompactFilterCard>

              <CompactFilterCard
                title="Operational guidance"
                subtitle="Keep the inward form focused on receipting. Use Inventory for broad batch edits and Medicine Master for catalogue changes."
                actions={<Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>}
              >
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`Active medicines ${activeMedicines.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Suppliers ${suppliers.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Locations ${locations.length}`} sx={compactChipSx} />
                </Stack>
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "reconciliation" && medicineCount === 0 ? (
        <CompactFilterCard
          title="No medicines are available for reconciliation."
          subtitle="Add medicines before creating reconciliation sessions."
        >
          <CompactEmptyState
            title="No medicines are available for reconciliation."
            subtitle="Add medicines before creating reconciliation sessions."
            action={(
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>
                <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Upload CSV</Button>
              </Stack>
            )}
          />
        </CompactFilterCard>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "reconciliation" && medicineCount > 0 && stockBatchCount === 0 ? (
        <CompactFilterCard
          title="No stock batches are available for reconciliation."
          subtitle="Receive stock before running physical count or supplier bill reconciliation."
        >
          <CompactEmptyState
            title="No stock batches are available for reconciliation."
            subtitle="Receive stock before running physical count or supplier bill reconciliation."
            action={(
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
                <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct")}>Direct Goods Receipt</Button>
              </Stack>
            )}
          />
        </CompactFilterCard>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "reconciliation" && medicineCount > 0 && stockBatchCount > 0 ? (
        <Stack spacing={2}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, xl: 4 }}>
              <CompactFilterCard
                title="Upload and draft"
                subtitle="Create a maker draft, attach the vendor sheet, then review extracted rows before submit."
              >
                <Alert severity="warning" sx={{ py: 0 }}>
                  AI/OCR extraction is assistive only. Pharmacist review is mandatory before submit, approve, and post. Uploaded sheets never auto-post stock.
                </Alert>
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small" error={Boolean(reconFieldErrors.locationId)}>
                      <InputLabel id="recon-location-label"><RequiredLabel text="Location" required /></InputLabel>
                      <Select id="recon-location" labelId="recon-location-label" label="Location" value={selectedLocationId} onChange={(e) => setSelectedLocationId(String(e.target.value))} required inputProps={{ "aria-required": true }}>
                        <MenuItem value="">Default location</MenuItem>
                        {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}{location.defaultLocation ? " (Default)" : ""}</MenuItem>)}
                      </Select>
                      {reconFieldErrors.locationId ? <Typography variant="caption" color="error">{reconFieldErrors.locationId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small" error={Boolean(reconFieldErrors.supplierId)}>
                      <InputLabel id="recon-supplier-label">Supplier</InputLabel>
                      <Select id="recon-supplier" labelId="recon-supplier-label" label="Supplier" value={reconForm.supplierId} onChange={(e) => setReconForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                        <MenuItem value="">No supplier</MenuItem>
                        {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                      {reconFieldErrors.supplierId ? <Typography variant="caption" color="error">{reconFieldErrors.supplierId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={12}>
                    <Autocomplete<MedicineOption, false, false, false>
                      options={medicineOptions}
                      value={medicineOptions.find((option) => option.medicine.id === reconForm.medicineId) ?? null}
                      onChange={(_, value) => setReconForm((current) => ({ ...current, medicineId: value?.medicine.id || "" }))}
                      getOptionLabel={(option) => option.medicine.medicineName}
                      renderInput={(params) => <TextField {...params} id="recon-medicine" size="small" label={<RequiredLabel text="Primary stock medicine" required />} placeholder="Required by current reconciliation API" required error={Boolean(reconFieldErrors.medicineId)} helperText={reconFieldErrors.medicineId || "Required."} inputProps={{ ...params.inputProps, "aria-required": true }} />}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="recon-stock-label"><RequiredLabel text="Stock batch" required /></InputLabel>
                      <Select
                        id="recon-batch"
                        labelId="recon-stock-label"
                        label="Stock batch"
                        value={reconForm.stockBatchId}
                        onChange={(e) => setReconForm((s) => ({ ...s, stockBatchId: String(e.target.value) }))}
                        required
                        inputProps={{ "aria-required": true }}
                      >
                        <MenuItem value="">Select batch</MenuItem>
                        {stocks
                          .filter((stock) => (!reconForm.medicineId || stock.medicineId === reconForm.medicineId) && (!selectedLocationId || stock.locationId === selectedLocationId))
                          .map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {stock.medicineName} - {stock.batchNumber || "Batch pending"}
                            </MenuItem>
                          ))}
                      </Select>
                      {reconFieldErrors.stockBatchId ? <Typography variant="caption" color="error">{reconFieldErrors.stockBatchId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField id="recon-qty" size="small" fullWidth label={<RequiredLabel text="Physical qty" required />} value={reconForm.physicalQuantity} onChange={(e) => setReconForm((s) => ({ ...s, physicalQuantity: e.target.value }))} required error={Boolean(reconFieldErrors.physicalQuantity)} helperText={reconFieldErrors.physicalQuantity || "Whole number zero or greater."} inputProps={{ min: 0, step: 1, "aria-required": true }} />
                  </Grid>
                  <Grid size={12}>
                    <CommentSuggestions
                      category="INVENTORY_VENDOR_RECONCILIATION"
                      selectedReason={reconReason}
                      remarks={reconRemarks}
                      onReasonChange={setReconReason}
                      onRemarksChange={setReconRemarks}
                      requiredReason={false}
                      maxRemarksLength={250}
                      reasonLabel="Review reason"
                      remarksLabel="Supplier bill / invoice reference"
                      reasonError={Boolean(reconFieldErrors.reason)}
                      reasonHelperText={reconFieldErrors.reason}
                      remarksError={Boolean(reconFieldErrors.reason)}
                      remarksHelperText={reconFieldErrors.reason}
                    />
                  </Grid>
                  <Grid size={12}>
                    <Button size="small" variant="outlined" component="label">
                      Upload supplier bill / invoice
                      <input hidden type="file" accept=".pdf,image/*,.csv,.xls,.xlsx" onChange={handleSheetFileChange} />
                    </Button>
                    {sheetFile ? (
                      <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5 }}>
                        Attached: {sheetFile.name}
                      </Typography>
                    ) : null}
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant="contained" onClick={() => void createDraftReconciliation()} disabled={!canManageOperations || saving}>Save Reconciliation Draft</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Backend gap: the current reconciliation API is draft/session based, not a true vendor-document row ledger. This UI keeps the workflow row-focused, but per-row persisted map/accept/reject actions are not exposed by the backend yet.
                </Typography>
              </CompactFilterCard>
            </Grid>

            <Grid size={{ xs: 12, xl: 8 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, lg: 8.6 }}>
                  <CompactFilterCard
                    title={<Stack direction="row" spacing={1} alignItems="center"><RuleRoundedIcon fontSize="small" color="primary" /><span>Review Differences</span></Stack>}
                    subtitle={selectedReconciliation ? `Reviewing ${selectedReconciliation.status.toLowerCase()} session for ${selectedReconciliation.medicineName || "stock item"}` : "Select a reconciliation session to review rows."}
                    actions={<Button size="small" variant="outlined" onClick={() => void saveReviewedRows()} disabled={!canManageOperations || saving || !selectedReconciliationId || !reviewRows.length}>Save Review</Button>}
                  >
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
                      <Chip size="small" icon={<UploadFileRoundedIcon fontSize="small" />} label="1. Upload / Enter Count" color="info" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" icon={<RuleRoundedIcon fontSize="small" />} label="2. Review Differences" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" icon={<DescriptionRoundedIcon fontSize="small" />} label="3. Submit" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" icon={<Inventory2RoundedIcon fontSize="small" />} label="4. Approve" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label="5. Posted" color="success" variant="outlined" sx={compactChipSx} />
                    </Stack>
                    {selectedReconciliationRows.length === 0 ? (
                      <CompactEmptyState title="No extracted rows yet." subtitle="Upload a vendor sheet to start row-based review. Drafts without a sheet still appear in the session list below." />
                    ) : (
                      <TableContainer sx={{ ...stickyTableSx, maxHeight: 460 }}>
                        <Table stickyHeader size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Vendor medicine</TableCell>
                              <TableCell>Matched medicine</TableCell>
                              <TableCell>Batch</TableCell>
                              <TableCell>Expiry</TableCell>
                              <TableCell align="right">Vendor qty</TableCell>
                              <TableCell align="right">System qty</TableCell>
                              <TableCell align="right">Variance</TableCell>
                              <TableCell>Status</TableCell>
                              <TableCell align="right">Actions</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {selectedReconciliationRows.map((row, index) => {
                              const systemQty = index === 0 ? selectedReconciliation?.systemQuantity ?? null : null;
                              const variance = systemQty != null && row.physicalQuantity != null ? row.physicalQuantity - systemQty : null;
                              return (
                                <TableRow key={`${row.rowNumber}-${index}`} hover>
                                  <TableCell>
                                    <TextField
                                      size="small"
                                      fullWidth
                                      value={row.medicineName || ""}
                                      onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, medicineName: e.target.value } : item))}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <Stack spacing={0.2}>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                        {row.medicineCode || selectedReconciliation?.medicineName || "Row-level mapping backend pending"}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Use Medicine Master for missing catalogue items.
                                      </Typography>
                                    </Stack>
                                  </TableCell>
                                  <TableCell>
                                    <TextField size="small" fullWidth value={row.batchNumber || ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, batchNumber: e.target.value } : item))} />
                                  </TableCell>
                                  <TableCell>
                                    <TextField size="small" fullWidth type="date" InputLabelProps={{ shrink: true }} value={row.expiryDate || ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, expiryDate: e.target.value || null } : item))} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <TextField size="small" type="number" value={row.physicalQuantity ?? ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, physicalQuantity: e.target.value ? Number(e.target.value) : null } : item))} sx={{ width: 96 }} />
                                  </TableCell>
                                  <TableCell align="right">{systemQty ?? "-"}</TableCell>
                                  <TableCell align="right">{variance ?? "-"}</TableCell>
                                  <TableCell>
                                    <Chip size="small" label={row.needsReview ? "Needs review" : "Accepted"} color={row.needsReview ? "warning" : "success"} sx={compactChipSx} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                                      <Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Map</Button>
                                      <Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Create</Button>
                                      <Button size="small" onClick={() => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, needsReview: false } : item))}>Accept</Button>
                                      <Button size="small" color="inherit" disabled={!canManageOperations} onClick={() => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, needsReview: true, notes: "Rejected during manual review" } : item))}>Reject</Button>
                                    </Stack>
                                  </TableCell>
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </CompactFilterCard>
                </Grid>

                <Grid size={{ xs: 12, lg: 3.4 }}>
                  <Stack spacing={1.5} sx={{ position: { lg: "sticky" }, top: { lg: 84 } }}>
                    <CompactFilterCard title="Summary" subtitle="Review progress">
                      <Stack spacing={0.75}>
                        <Chip size="small" label={`Total rows ${selectedReconciliationRows.length}`} sx={compactChipSx} />
                        <Chip size="small" label={`Unresolved ${unresolvedRows}`} color={unresolvedRows ? "warning" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Missing medicines ${missingMedicineRows}`} color={missingMedicineRows ? "error" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Duplicates ${duplicateReviewRows}`} color={duplicateReviewRows ? "warning" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Variance ${selectedReconciliation?.varianceQuantity ?? 0}`} sx={compactChipSx} />
                      </Stack>
                    </CompactFilterCard>

                    <CompactFilterCard
                      title="Selected session"
                      subtitle={
                        selectedReconciliation
                          ? `${selectedReconciliation.batchNumber || "No batch"} • ${selectedReconciliation.locationName || "Default location"}`
                          : "No session selected"
                      }
                    >
                      {selectedReconciliation ? (
                        <Stack spacing={0.45}>
                          <Typography variant="caption" color="text.secondary">Created {formatDateTime(selectedReconciliation.createdAt)}</Typography>
                          <Typography variant="caption" color="text.secondary">Maker: {actorLabel(selectedReconciliation.createdBy, currentUserId, "Pharmacy user")}</Typography>
                          <Typography variant="caption" color="text.secondary">Submitted: {actorLabel(selectedReconciliation.submittedBy, currentUserId, "Pharmacy reviewer")}</Typography>
                          <Typography variant="caption" color="text.secondary">Reviewer: {actorLabel(selectedReconciliation.reviewedBy, currentUserId, "Approving reviewer")}</Typography>
                          <Typography variant="caption" color="text.secondary">Posted: {actorLabel(selectedReconciliation.postedBy, currentUserId, "Posting pharmacist")}</Typography>
                          <Chip size="small" label={selectedReconciliation.status} color={badgeTone(selectedReconciliation.status)} sx={compactChipSx} />
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">Select a row below to review and act on the session.</Typography>
                      )}
                    </CompactFilterCard>
                  </Stack>
                </Grid>
              </Grid>
            </Grid>
          </Grid>

          <CompactFilterCard
            title="Session queue"
            subtitle="Maker-checker sessions remain compact and actionable."
            actions={(
              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                <Chip size="small" label={`Draft ${draftCount}`} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Submitted ${submittedCount}`} color="warning" variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Approved ${approvedCount}`} color="info" variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Posted ${postedCount}`} color="success" variant="outlined" sx={compactChipSx} />
              </Stack>
            )}
          >
            {reconciliations.length === 0 ? (
              <CompactEmptyState
                title="No reconciliation sessions yet."
                subtitle="Create a reconciliation session when stock or supplier bill differs."
                action={<Button size="small" variant="contained" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>Start Reconciliation</Button>}
              />
            ) : (
              <TableContainer sx={{ ...stickyTableSx, maxHeight: 280 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Session</TableCell>
                      <TableCell align="right">System</TableCell>
                      <TableCell align="right">Physical</TableCell>
                      <TableCell align="right">Variance</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {reconciliations.map((row) => (
                      <TableRow key={row.id} hover selected={row.id === selectedReconciliationId} onClick={() => setSelectedReconciliationId(row.id)} sx={{ cursor: "pointer" }}>
                        <TableCell>
                          <Stack spacing={0.2}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.medicineName || "Medicine"}</Typography>
                            <Typography variant="caption" color="text.secondary">{row.batchNumber || "No batch"} • {row.locationName || "Default location"}</Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">{row.systemQuantity}</TableCell>
                        <TableCell align="right">{row.physicalQuantity ?? "-"}</TableCell>
                        <TableCell align="right">{row.varianceQuantity ?? "-"}</TableCell>
                        <TableCell>
                          <Chip size="small" label={row.status} color={badgeTone(row.status)} sx={compactChipSx} />
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                            {["DRAFT", "REJECTED"].includes((row.status || "").toUpperCase()) ? (
                              <Button size="small" onClick={(event) => { event.stopPropagation(); void submitRecon(row); }} disabled={!canManageOperations || saving}>Submit</Button>
                            ) : null}
                            {row.status === "SUBMITTED" ? (
                              currentUserId && row.createdBy === currentUserId ? (
                                <Chip size="small" label="Maker cannot approve own reconciliation" color="warning" variant="outlined" sx={compactChipSx} />
                              ) : (
                                <>
                                  <Button size="small" onClick={(event) => { event.stopPropagation(); void approveRecon(row); }} disabled={!canManageOperations || saving}>Approve</Button>
                                  <Button size="small" color="inherit" onClick={(event) => { event.stopPropagation(); void rejectRecon(row); }} disabled={!canManageOperations || saving}>Reject</Button>
                                </>
                              )
                            ) : null}
                            {row.status === "APPROVED" ? (
                              <Button size="small" onClick={(event) => { event.stopPropagation(); void postRecon(row); }} disabled={!canManageOperations || saving}>Post</Button>
                            ) : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CompactFilterCard>
        </Stack>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "physical-count" ? (
        <CompactFilterCard
          title="Physical Count"
          subtitle="Use Inventory for physical counts against live batches, then return here for reconciliation approval."
          actions={<Button size="small" variant="outlined" onClick={() => navigate("/inventory?tab=count")}>Open Physical Count</Button>}
        >
          {medicineCount === 0 ? (
            <CompactEmptyState
              title="No medicines are available for reconciliation."
              subtitle="Add medicines before creating reconciliation sessions."
              action={<Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>}
            />
          ) : stockBatchCount === 0 ? (
            <CompactEmptyState
              title="No stock batches are available for reconciliation."
              subtitle="Receive stock before running physical count or supplier bill reconciliation."
              action={<Button size="small" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>}
            />
          ) : (
            <CompactEmptyState
              title="Physical count lives in Inventory."
              subtitle="Open the inventory physical count workflow to reconcile counted quantities before review."
              action={<Button size="small" onClick={() => navigate("/inventory?tab=count")}>Open Inventory Count</Button>}
            />
          )}
        </CompactFilterCard>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "stock-adjustments" ? (
        <CompactFilterCard
          title="Stock Adjustments"
          subtitle="Use Inventory for stock adjustments, write-offs, and return posting."
          actions={<Button size="small" variant="outlined" onClick={() => navigate("/inventory?tab=returns")}>Open Adjustments</Button>}
        >
          {medicineCount === 0 ? (
            <CompactEmptyState
              title="No medicines are available for reconciliation."
              subtitle="Add medicines before creating reconciliation sessions."
              action={<Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>}
            />
          ) : stockBatchCount === 0 ? (
            <CompactEmptyState
              title="No stock batches are available for reconciliation."
              subtitle="Stock adjustments require an existing inventory batch."
              action={(
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct")}>Direct Goods Receipt</Button>
                </Stack>
              )}
            />
          ) : (
            <CompactEmptyState
              title="Stock adjustments are managed in Inventory."
              subtitle="Open Inventory to post adjustments, write-offs, and batch-level corrections."
              action={<Button size="small" onClick={() => navigate("/inventory")}>Open Inventory</Button>}
            />
          )}
        </CompactFilterCard>
      ) : null}

      {!loading && pageMode === "reconciliation" && tab === "approval-review" ? (
        <Stack spacing={2}>
          <CompactFilterCard
            title="Approval Review"
            subtitle="Queue review, approval, and posting actions for reconciliation sessions."
          >
            {reconciliations.length === 0 ? (
              <CompactEmptyState
                title="No reconciliation sessions yet."
                subtitle="Create a session after receiving stock, or review pending approval sessions here."
                action={(
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    <Button size="small" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
                    <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct")}>Direct Goods Receipt</Button>
                    <Button size="small" variant="contained" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>Start Reconciliation</Button>
                  </Stack>
                )}
              />
            ) : (
              <Typography variant="body2" color="text.secondary">
                Review submitted supplier bill differences, approve or reject sessions, and post finalized stock corrections.
              </Typography>
            )}
          </CompactFilterCard>
          <CompactFilterCard title="Session queue" subtitle="Maker-checker sessions remain compact and actionable.">
            <Typography variant="body2" color="text.secondary">
              Open a reconciliation session above to review the latest supplier bill workflow and approval state.
            </Typography>
          </CompactFilterCard>
        </Stack>
      ) : null}

      {!loading && pageMode === "procurement" && (workspace === "purchase-orders" || workspace === "supplier-invoices" || workspace === "goods-receipt") ? (
        <Grid container spacing={2}>
                <Grid size={{ xs: 12, lg: 4.8 }}>
            <CompactFilterCard
              title="Procurement workflow"
              subtitle="Compact tabs instead of three oversized forms."
            >
              <Tabs
                value={procurementTab}
                onChange={(_, value) => {
                  const nextWorkflow = value as ProcurementTab;
                  updateProcurementWorkspaceRoute(workspaceFromPurchaseOrderTab(nextWorkflow), null);
                }}
                variant="scrollable"
                scrollButtons="auto"
                sx={{ display: "none" }}
              >
                <Tab value="po" label="PO" />
                <Tab value="invoice" label="Invoice" />
                <Tab value="grn" label="GRN" />
              </Tabs>

                  {procurementTab === "po" ? (
                    <Stack spacing={1.25}>
                      {!supplierCount ? (
                        <Alert severity="warning" sx={{ py: 0 }}>
                          Create a supplier before creating a Purchase Order.
                        </Alert>
                      ) : null}
                      <Grid container spacing={1.25}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth size="small" error={Boolean(poFieldErrors.supplierId)}>
                            <InputLabel id="po-supplier-label"><RequiredLabel text="Supplier" required /></InputLabel>
                            <Select id="po-supplier" labelId="po-supplier-label" label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((s) => ({ ...s, supplierId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                              <MenuItem value="">{supplierCount ? "Select supplier" : "Create supplier first"}</MenuItem>
                              {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                            </Select>
                            {poFieldErrors.supplierId ? <Typography variant="caption" color="error">{poFieldErrors.supplierId}</Typography> : null}
                          </FormControl>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="po-number" size="small" fullWidth label={<RequiredLabel text="PO number" required />} value={poForm.poNumber} onChange={(e) => setPoForm((s) => ({ ...s, poNumber: e.target.value }))} required error={Boolean(poFieldErrors.poNumber)} helperText={poFieldErrors.poNumber || "Required, max 60 characters."} inputProps={{ "aria-required": true, maxLength: 60 }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="po-order-date" size="small" fullWidth label={<RequiredLabel text="Order date" required />} type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((s) => ({ ...s, orderDate: e.target.value }))} required error={Boolean(poFieldErrors.orderDate)} helperText={poFieldErrors.orderDate || "Cannot be future dated."} inputProps={{ "aria-required": true }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="po-expected-delivery" size="small" fullWidth label="Expected delivery" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDeliveryDate || ""} onChange={(e) => setPoForm((s) => ({ ...s, expectedDeliveryDate: e.target.value || null }))} error={Boolean(poFieldErrors.expectedDeliveryDate)} helperText={poFieldErrors.expectedDeliveryDate || "Optional, must be on or after order date."} /></Grid>
                        <Grid size={12}><TextField id="po-notes" size="small" fullWidth label="Notes" value={poForm.approvalNote || ""} onChange={(e) => setPoForm((s) => ({ ...s, approvalNote: e.target.value || null }))} helperText="Optional supplier terms, delivery instructions, or payment notes." multiline minRows={2} /></Grid>
                      </Grid>
                    </Stack>
                  ) : null}

                {procurementTab === "invoice" ? (
                  !purchaseOrders.length ? (
                    <CompactEmptyState
                      title="No purchase orders are available for invoice matching."
                      subtitle="Create a purchase order before linking supplier invoices."
                      action={<Button size="small" variant="contained" onClick={() => updateProcurementWorkspaceRoute("purchase-orders")}>Create PO</Button>}
                    />
                  ) : (
                    <Stack spacing={1.25}>
                      <Grid container spacing={1.25}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth size="small" error={Boolean(invoiceFieldErrors.supplierId)}>
                            <InputLabel id="invoice-supplier-label"><RequiredLabel text="Supplier" required /></InputLabel>
                            <Select id="invoice-supplier" labelId="invoice-supplier-label" label="Supplier" value={invoiceForm.supplierId} onChange={(e) => setInvoiceForm((s) => ({ ...s, supplierId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                              <MenuItem value="">Select supplier</MenuItem>
                              {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                            </Select>
                            {invoiceFieldErrors.supplierId ? <Typography variant="caption" color="error">{invoiceFieldErrors.supplierId}</Typography> : null}
                          </FormControl>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth size="small">
                            <InputLabel id="invoice-po-label">Purchase order</InputLabel>
                            <Select id="invoice-po" labelId="invoice-po-label" label="Purchase order" value={invoiceForm.purchaseOrderId || ""} onChange={(e) => syncInvoiceFromPurchaseOrder(String(e.target.value))}>
                              <MenuItem value="">Select purchase order</MenuItem>
                              {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                            </Select>
                          </FormControl>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="invoice-number" size="small" fullWidth label={<RequiredLabel text="Invoice number" required />} value={invoiceForm.invoiceNumber} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceNumber: e.target.value }))} required error={Boolean(invoiceFieldErrors.invoiceNumber)} helperText={invoiceFieldErrors.invoiceNumber || "Required, max 60 characters."} inputProps={{ "aria-required": true, maxLength: 60 }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="invoice-date" size="small" fullWidth label={<RequiredLabel text="Invoice date" required />} type="date" InputLabelProps={{ shrink: true }} value={invoiceForm.invoiceDate} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceDate: e.target.value }))} required error={Boolean(invoiceFieldErrors.invoiceDate)} helperText={invoiceFieldErrors.invoiceDate || "Cannot be future dated."} inputProps={{ "aria-required": true }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="invoice-tax" size="small" fullWidth type="number" label="Tax amount" value={invoiceForm.taxAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, taxAmount: e.target.value ? Number(e.target.value) : null }))} error={Boolean(invoiceFieldErrors.taxAmount)} helperText={invoiceFieldErrors.taxAmount || "Optional."} inputProps={{ min: 0, max: 999999, step: "0.01" }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="invoice-total" size="small" fullWidth type="number" label="Total amount" value={invoiceForm.totalAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, totalAmount: e.target.value ? Number(e.target.value) : null }))} error={Boolean(invoiceFieldErrors.totalAmount)} helperText={invoiceFieldErrors.totalAmount || "Optional."} inputProps={{ min: 0, max: 999999, step: "0.01" }} /></Grid>
                        <Grid size={{ xs: 12, md: 4 }}><TextField id="invoice-discount" size="small" fullWidth type="number" label="Discount" value={invoiceDiscount} onChange={(e) => setInvoiceDiscount(e.target.value)} helperText="Optional invoice discount." inputProps={{ min: 0, step: "0.01" }} /></Grid>
                        <Grid size={{ xs: 12, md: 4 }}><TextField id="invoice-freight" size="small" fullWidth type="number" label="Freight" value={invoiceFreight} onChange={(e) => setInvoiceFreight(e.target.value)} helperText="Optional freight or loading charges." inputProps={{ min: 0, step: "0.01" }} /></Grid>
                        <Grid size={{ xs: 12, md: 4 }}><TextField id="invoice-variance" size="small" fullWidth label="Variance" value={invoiceVariance} onChange={(e) => setInvoiceVariance(e.target.value)} helperText="Optional supplier bill variance note." /></Grid>
                        <Grid size={12}><TextField id="invoice-note" size="small" fullWidth label="Invoice notes" value={invoiceForm.approvalNote || ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, approvalNote: e.target.value || null }))} helperText="Optional finance or inward notes linked to this invoice." multiline minRows={2} /></Grid>
                      </Grid>
                      <Alert severity="info" sx={{ py: 0 }}>
                        {invoiceForm.purchaseOrderId ? `Invoice linked to PO. ${selectedInvoicePurchaseOrderItems.length} PO line items linked to the invoice.` : "Select a purchase order to auto-link line items."}
                      </Alert>
                      <Stack direction="row" spacing={1}>
                        <Button size="small" variant="contained" onClick={() => void saveSupplierInvoice()} disabled={!canManageOperations || saving || !purchaseOrders.length}>Save invoice</Button>
                        <Button size="small" onClick={() => { setInvoiceForm({ supplierId: "", purchaseOrderId: null, invoiceNumber: "", invoiceDate: "", taxAmount: null, totalAmount: null, items: [], approvalNote: null }); setInvoiceDiscount(""); setInvoiceFreight(""); setInvoiceVariance(""); }}>Clear</Button>
                      </Stack>
                    </Stack>
                  )
                ) : null}

                {procurementTab === "grn" ? (
                  !purchaseOrders.length ? (
                    <CompactEmptyState
                      title="No purchase orders are available for goods receipt."
                      subtitle="Create a purchase order before posting GRN or receiving stock."
                      action={<Button size="small" variant="contained" onClick={() => updateProcurementWorkspaceRoute("purchase-orders")}>Create PO</Button>}
                    />
                  ) : (
                    <Stack spacing={1.25}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="grn-po-workspace-label"><RequiredLabel text="Purchase order" required /></InputLabel>
                        <Select id="grn-po-workspace" labelId="grn-po-workspace-label" label="Purchase order" value={grnForm.purchaseOrderId || ""} onChange={(e) => syncGoodsReceiptFromPurchaseOrder(String(e.target.value))} required inputProps={{ "aria-required": true }}>
                          <MenuItem value="">Select purchase order</MenuItem>
                          {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber} • {po.supplierName || "Supplier pending"}</MenuItem>)}
                        </Select>
                      </FormControl>
                      <Alert severity="info" sx={{ py: 0 }}>
                        {grnForm.purchaseOrderId ? `${grnForm.items.length} PO line items loaded for stock receipt.` : "Select a purchase order to load line items."}
                      </Alert>
                      <Grid container spacing={1.25}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth size="small" error={Boolean(grnFieldErrors.supplierId)}>
                            <InputLabel id="grn-supplier-label"><RequiredLabel text="Supplier" required /></InputLabel>
                        <Select id="grn-supplier" labelId="grn-supplier-label" label="Supplier" value={grnForm.supplierId || ""} onChange={(e) => setGrnForm((s) => ({ ...s, supplierId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                              <MenuItem value="">Supplier from PO</MenuItem>
                              {activeSuppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                            </Select>
                            {grnFieldErrors.supplierId ? <Typography variant="caption" color="error">{grnFieldErrors.supplierId}</Typography> : null}
                          </FormControl>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth size="small" error={Boolean(grnFieldErrors.locationId)}>
                            <InputLabel id="grn-location-select"><RequiredLabel text="Location" required /></InputLabel>
                            <Select id="grn-location" labelId="grn-location-select" label="Location" value={grnForm.locationId || selectedLocationId} onChange={(e) => setGrnForm((s) => ({ ...s, locationId: String(e.target.value) }))} required inputProps={{ "aria-required": true }}>
                              <MenuItem value="">Default location</MenuItem>
                              {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>)}
                            </Select>
                            {grnFieldErrors.locationId ? <Typography variant="caption" color="error">{grnFieldErrors.locationId}</Typography> : null}
                          </FormControl>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="grn-receipt" size="small" fullWidth label={<RequiredLabel text="Receipt number" required />} value={grnForm.receiptNumber} onChange={(e) => setGrnForm((s) => ({ ...s, receiptNumber: e.target.value }))} required error={Boolean(grnFieldErrors.receiptNumber)} helperText={grnFieldErrors.receiptNumber || "Required, max 60 characters."} inputProps={{ "aria-required": true, maxLength: 60 }} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField id="grn-received-at" size="small" fullWidth label={<RequiredLabel text="Received at" required />} type="datetime-local" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((s) => ({ ...s, receivedAt: e.target.value }))} required error={Boolean(grnFieldErrors.receivedAt)} helperText={grnFieldErrors.receivedAt || "Required."} inputProps={{ "aria-required": true }} /></Grid>
                      </Grid>
                      <Stack direction="row" spacing={1}>
                        <Button size="small" variant="contained" onClick={() => void saveGoodsReceipt()} disabled={!canManageOperations || saving || !grnForm.items.length}>Post GRN</Button>
                        <Button size="small" onClick={() => { setGrnForm({ supplierId: "", purchaseOrderId: null, supplierInvoiceId: null, receiptNumber: "", receivedAt: "", locationId: selectedLocationId || "", items: [], approvalNote: null }); }}>Clear</Button>
                      </Stack>
                    </Stack>
                  )
                ) : null}

              <Grid container spacing={1.25}>
                <Grid size={{ xs: 12, md: 7 }}><TextField id="procurement-line-name" size="small" fullWidth label={<RequiredLabel text="Line item name" required />} value={procurementLine.medicineName} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineName: e.target.value }))} required error={Boolean(procurementLineFieldErrors.medicineName)} helperText={procurementLineFieldErrors.medicineName || "Required, 1-100 characters."} inputProps={{ "aria-required": true, maxLength: 100 }} /></Grid>
                <Grid size={{ xs: 12, md: 5 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="procurement-medicine-label">Medicine master</InputLabel>
                    <Select id="procurement-line-medicine" labelId="procurement-medicine-label" label="Medicine master" value={procurementLine.medicineId} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineId: String(e.target.value) }))}>
                      <MenuItem value="">Manual line</MenuItem>
                      {activeMedicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-qty" size="small" fullWidth type="number" label={<RequiredLabel text="Qty" required />} value={procurementLine.quantity} onChange={(e) => setProcurementLine((s) => ({ ...s, quantity: e.target.value }))} required error={Boolean(procurementLineFieldErrors.quantity)} helperText={procurementLineFieldErrors.quantity || "Required whole number greater than zero."} inputProps={{ min: 1, step: 1, "aria-required": true }} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-unit-cost" size="small" fullWidth type="number" label="Expected/unit cost" value={procurementTab === "po" ? procurementLine.expectedUnitCost : procurementLine.unitCost} onChange={(e) => setProcurementLine((s) => ({ ...s, [procurementTab === "po" ? "expectedUnitCost" : "unitCost"]: e.target.value }))} error={Boolean(procurementLineFieldErrors.expectedUnitCost || procurementLineFieldErrors.unitCost)} helperText={(procurementTab === "po" ? procurementLineFieldErrors.expectedUnitCost : procurementLineFieldErrors.unitCost) || "Optional, >= 0."} inputProps={{ min: 0, step: "0.01" }} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-tax" size="small" fullWidth type="number" label="Tax %" value={procurementLine.taxPercent} onChange={(e) => setProcurementLine((s) => ({ ...s, taxPercent: e.target.value }))} error={Boolean(procurementLineFieldErrors.taxPercent)} helperText={procurementLineFieldErrors.taxPercent || "Optional, 0-100."} inputProps={{ min: 0, max: 100, step: "0.01" }} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-batch" size="small" fullWidth label="Batch" value={procurementLine.batchNumber} onChange={(e) => setProcurementLine((s) => ({ ...s, batchNumber: e.target.value }))} error={Boolean(procurementLineFieldErrors.batchNumber)} helperText={procurementLineFieldErrors.batchNumber || "Optional, max 30 characters."} inputProps={{ maxLength: 30 }} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-expiry" size="small" fullWidth label="Expiry" type="date" InputLabelProps={{ shrink: true }} value={procurementLine.expiryDate} onChange={(e) => setProcurementLine((s) => ({ ...s, expiryDate: e.target.value }))} error={Boolean(procurementLineFieldErrors.expiryDate)} helperText={procurementLineFieldErrors.expiryDate || "Optional, future date if provided."} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField id="procurement-line-selling" size="small" fullWidth type="number" label="Selling price" value={procurementLine.sellingPrice} onChange={(e) => setProcurementLine((s) => ({ ...s, sellingPrice: e.target.value }))} error={Boolean(procurementLineFieldErrors.sellingPrice)} helperText={procurementLineFieldErrors.sellingPrice || "Optional, must be >= expected/unit cost."} inputProps={{ min: 0, step: "0.01" }} /></Grid>
              </Grid>

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="contained" onClick={upsertPurchaseOrderItem} disabled={!canManageOperations || saving}>
                  {poItemEditIndex != null ? "Update Item" : "Add Item"}
                </Button>
                <Button size="small" onClick={resetProcurementLineEditor}>Clear Item</Button>
                <Chip size="small" label={`Items ${poItems.length}`} color={poItems.length ? "info" : "default"} variant="outlined" sx={compactChipSx} />
              </Stack>

              <TableContainer sx={{ ...stickyTableSx, maxHeight: 280 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine / item</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">Unit cost</TableCell>
                      <TableCell align="right">Tax %</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell>Expiry</TableCell>
                      <TableCell align="right">Selling price</TableCell>
                      <TableCell align="right">Line total</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {poItems.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={9}>
                          <CompactEmptyState
                            title="No PO items yet."
                            subtitle="Add one or more medicine lines before saving the purchase order."
                          />
                        </TableCell>
                      </TableRow>
                    ) : (
                      poItems.map((item, index) => {
                        const quantity = Number(item.quantity || 0);
                        const unitCost = Number(item.expectedUnitCost ?? item.unitCost ?? item.sellingPrice ?? 0);
                        const base = quantity * unitCost;
                        const tax = base * (Number(item.taxPercent || 0) / 100);
                        const total = base + tax;
                        return (
                          <TableRow key={`${procurementLineKey(item)}-${index}`}>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.medicineName || medicines.find((medicine) => medicine.id === item.medicineId)?.medicineName || "Medicine"}</Typography>
                                <Typography variant="caption" color="text.secondary">{item.medicineId || "Manual line item"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell align="right">{item.quantity}</TableCell>
                            <TableCell align="right">{item.expectedUnitCost != null ? money(item.expectedUnitCost) : "-"}</TableCell>
                            <TableCell align="right">{item.taxPercent != null ? `${item.taxPercent}%` : "-"}</TableCell>
                            <TableCell>{item.batchNumber || "-"}</TableCell>
                            <TableCell>{formatDate(item.expiryDate)}</TableCell>
                            <TableCell align="right">{item.sellingPrice != null ? money(item.sellingPrice) : "-"}</TableCell>
                            <TableCell align="right">INR {money(total)}</TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                                <Button size="small" onClick={() => editPurchaseOrderItem(index)}>Edit</Button>
                                <Button size="small" color="inherit" onClick={() => removePurchaseOrderItem(index)}>Remove</Button>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </TableContainer>

              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                <Chip size="small" label={`Total qty ${purchaseOrderItemsTotal.quantity}`} sx={compactChipSx} />
                <Chip size="small" label={`Subtotal INR ${money(purchaseOrderItemsTotal.subtotal)}`} sx={compactChipSx} />
                <Chip size="small" label={`Tax INR ${money(purchaseOrderItemsTotal.tax)}`} sx={compactChipSx} />
                <Chip size="small" label={`Grand total INR ${money(purchaseOrderItemsTotal.total)}`} color="success" sx={compactChipSx} />
              </Stack>

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="outlined" onClick={savePurchaseOrderDraft} disabled={!canManageOperations || saving}>Save Draft</Button>
                <Button size="small" variant="contained" onClick={() => void savePurchaseOrder()} disabled={!canManageOperations || saving || !poItems.length}>Generate PO</Button>
                <Button size="small" onClick={() => { setPoItems([]); resetProcurementLineEditor(); }}>Clear PO items</Button>
                <Button size="small" color="inherit" onClick={resetPurchaseOrderEditor}>Reset PO</Button>
              </Stack>
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.2 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard title="Workflow summary" subtitle="Compact counters instead of large procurement blocks.">
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`POs ${purchaseOrders.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Drafts ${poDrafts.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Invoices ${supplierInvoices.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`GRNs ${goodsReceipts.length}`} sx={compactChipSx} />
                  <Chip size="small" label="Draft" variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label="Generated" variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label="Sent" variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label="Partially Received" variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label="Received" color="success" variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label="Cancelled" color="default" variant="outlined" sx={compactChipSx} />
                </Stack>
              </CompactFilterCard>

              <CompactFilterCard title="Recent workflow records" subtitle={`Showing recent ${procurementTab.toUpperCase()} entries`}>
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 420 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Reference</TableCell>
                        <TableCell>Date</TableCell>
                        <TableCell>Status</TableCell>
                        {isPurchaseOrderWorkspace ? <TableCell align="right">Value</TableCell> : null}
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {hasEmptyProcurementRecords ? (
                        <TableRow>
                          <TableCell colSpan={isPurchaseOrderWorkspace ? 5 : 4}>
                            <CompactEmptyState
                              title={
                                isPurchaseOrderWorkspace
                                  ? "No purchase orders yet."
                                  : procurementTab === "invoice"
                                    ? "No supplier invoices yet."
                                    : "No goods receipts yet."
                              }
                              subtitle={
                                isPurchaseOrderWorkspace
                                  ? "Create a supplier and purchase order to receive stock."
                                  : procurementTab === "invoice"
                                    ? "Create a supplier invoice after the purchase order is ready."
                                    : "Create a goods receipt once stock arrives."
                              }
                              action={(
                                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                  <Button size="small" variant="outlined" onClick={() => updateProcurementWorkspaceRoute("suppliers", "supplier")}>Add Supplier</Button>
                                  <Button size="small" variant="contained" onClick={() => updateProcurementWorkspaceRoute("purchase-orders")}>Create PO</Button>
                                </Stack>
                              )}
                            />
                          </TableCell>
                        </TableRow>
                      ) : isPurchaseOrderWorkspace ? (
                        purchaseOrderWorkspaceRecords.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.poNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.supplierName || "Supplier pending"} • {row.itemCount} items
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(row.orderDate)}</TableCell>
                            <TableCell><Chip size="small" label={row.status} color={badgeTone(row.status.toUpperCase().replaceAll(" ", "_"))} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">INR {money(row.totals.total)}</TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                                <Button size="small" onClick={() => loadWorkspacePurchaseOrder(row)}>{row.isDraft ? "Edit" : "View"}</Button>
                                {!row.isDraft ? <Button size="small" onClick={() => queuePurchaseOrderForInvoice(row)}>Create Invoice</Button> : null}
                                <Button size="small" onClick={() => queuePurchaseOrderForGoodsReceipt(row)}>Create GRN</Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="inherit"
                                  onClick={(event) => {
                                    setPoActionsAnchor(event.currentTarget);
                                    setPoActionsRowId(row.id);
                                  }}
                                >
                                  More
                                </Button>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        ))
                      ) : procurementTab === "invoice" ? (
                        supplierInvoices.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.invoiceNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.supplierName || "Supplier pending"}
                                  {row.purchaseOrderId ? ` • PO ${purchaseOrderMap.get(row.purchaseOrderId)?.poNumber || "linked"}` : ""}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(row.invoiceDate)}</TableCell>
                            <TableCell><Chip size="small" label={row.matchingStatus || "Draft"} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">-</TableCell>
                          </TableRow>
                        ))
                      ) : (
                        goodsReceipts.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.receiptNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.supplierName || "Supplier pending"}
                                  {row.supplierInvoiceId ? ` • Inv ${supplierInvoiceMap.get(row.supplierInvoiceId)?.invoiceNumber || "linked"}` : ""}
                                  {row.purchaseOrderId ? ` • PO ${purchaseOrderMap.get(row.purchaseOrderId)?.poNumber || "linked"}` : ""}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDateTime(row.receivedAt)}</TableCell>
                            <TableCell><Chip size="small" label={row.matchingStatus} color={badgeTone(row.matchingStatus)} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">
                              <Button
                                size="small"
                                disabled={saving || row.confirmedAt !== null}
                                onClick={async () => {
                                  if (!auth.accessToken || !auth.tenantId) return;
                                  setSaving(true);
                                  setError(null);
                                  try {
                                    await confirmGoodsReceipt(auth.accessToken, auth.tenantId, row.id, row.approvalNote || "Variance approved");
                                    await refreshCurrentPageData();
                                    setSuccess("GRN confirmed");
                                  } catch (err) {
                                    setError(err instanceof Error ? err.message : "Failed to confirm GRN");
                                  } finally {
                                    setSaving(false);
                                  }
                                }}
                              >
                                Confirm
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      <Menu
        anchorEl={poActionsAnchor}
        open={Boolean(poActionsAnchor && activePurchaseOrderActionRow)}
        onClose={() => {
          setPoActionsAnchor(null);
          setPoActionsRowId(null);
        }}
      >
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) openPurchaseOrderDocument(activePurchaseOrderActionRow, "pdf");
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Generate PDF
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) openPurchaseOrderDocument(activePurchaseOrderActionRow, "print");
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Print
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) openPurchaseOrderDocument(activePurchaseOrderActionRow, "download");
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Download
        </MenuItem>
        {!activePurchaseOrderActionRow?.isDraft ? (
          <MenuItem
            onClick={() => {
              if (activePurchaseOrderActionRow) {
                updatePoLifecycle(activePurchaseOrderActionRow.sourceIds, { sentAt: new Date().toISOString(), status: "Sent" });
                setSuccess("Purchase order marked as sent. Share the generated copy with the supplier.");
              }
              setPoActionsAnchor(null);
              setPoActionsRowId(null);
            }}
          >
            Send
          </MenuItem>
        ) : null}
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) queuePurchaseOrderForInvoice(activePurchaseOrderActionRow);
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Create Invoice
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) queuePurchaseOrderForGoodsReceipt(activePurchaseOrderActionRow);
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Create GRN
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (activePurchaseOrderActionRow) {
              updatePoLifecycle(activePurchaseOrderActionRow.sourceIds, { cancelledAt: new Date().toISOString(), status: "Cancelled" });
              setSuccess("Purchase order cancelled");
            }
            setPoActionsAnchor(null);
            setPoActionsRowId(null);
          }}
        >
          Cancel
        </MenuItem>
      </Menu>

      {showAnalytics && !loading ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <CompactFilterCard title="Fast moving medicines" subtitle="Dense pharmacy demand view.">
              {analytics?.fastMovingMedicines?.length ? (
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 340 }}>
                  <Table stickyHeader size="small">
                    <TableHead><TableRow><TableCell>Medicine</TableCell><TableCell align="right">Dispensed</TableCell><TableCell align="right">Available</TableCell></TableRow></TableHead>
                    <TableBody>
                      {analytics.fastMovingMedicines.map((row) => (
                        <TableRow key={row.medicineId}>
                          <TableCell>{row.medicineName || "Medicine"}</TableCell>
                          <TableCell align="right">{row.dispensedQuantity}</TableCell>
                          <TableCell align="right">{row.availableQuantity}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              ) : <CompactEmptyState title="No fast-moving analytics yet." subtitle="Dispensing data will populate this panel." />}
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <CompactFilterCard title="Expiry and value" subtitle="Compact stock risk summary.">
              <Stack spacing={1}>
                <Chip size="small" label={`Stock value estimate INR ${money(analytics?.stockValueEstimate ?? 0)}`} color="info" sx={compactChipSx} />
                <Chip size="small" label={`Low stock medicines ${analytics?.lowStockMedicines?.length ?? 0}`} sx={compactChipSx} />
                <Chip size="small" label={`Expiry-risk batches ${analytics?.expiryRiskMedicines?.length ?? 0}`} sx={compactChipSx} />
              </Stack>
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
