import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  Chip,
  Divider,
  Drawer,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";

import { CompactEmptyState, CompactFilterCard, CompactStatCard, OperationalTableCard, compactCardContentSx, compactChipSx } from "../../components/compact/CompactUi";
import { useAuth } from "../../auth/useAuth";
import {
  getGoodsReceipts,
  getInventoryTransactions,
  getMedicines,
  getPurchaseOrders,
  getSupplierInvoices,
  getStocks,
  type GoodsReceipt,
  type InventoryTransaction,
  type Medicine,
  type ProcurementLineInput,
  type PurchaseOrder,
  type SupplierInvoice,
  type SupplierInvoiceStatus,
  type Stock,
} from "../../api/clinicApi";
import DocumentRelationshipStrip, { type DocumentRelationshipStage } from "../../components/pharmacy/DocumentRelationshipStrip";

type ReconcileTab = "supplier-bill-reconciliation" | "physical-count" | "stock-adjustments" | "approval-review";

type ReconciliationRow = {
  id: string;
  invoiceNumber: string;
  supplierId: string;
  supplier: string;
  poReference: string;
  grnReference: string;
  inventoryUpdated: boolean;
  invoiceDate: string;
  invoiceAmount: number;
  varianceAmount: number;
  varianceQuantity: number;
  matchResult: "Matched" | "Awaiting GRN" | "Quantity Mismatch" | "Price Mismatch" | "Tax Difference" | "Duplicate Invoice" | "Needs Review";
  status: "Draft" | "Submitted" | "Reviewed" | "Approved" | "Posted" | "Cancelled";
  variance: number;
  lastActivity: string;
  comparisonRows: Array<{
    medicine: string;
    orderedQty: number;
    receivedQty: number;
    invoicedQty: number;
    difference: number;
    variance: number;
    result: string;
  }>;
  exception: {
    severity: "Info" | "Warning" | "Critical";
    recommendedAction: string;
    responsibleRole: string;
    expectedNextStep: string;
  };
  timeline: Array<{
    label: string;
    state: "completed" | "current" | "pending";
  }>;
};

type ReconciliationLine = {
  medicineKey: string;
  medicine: string;
  orderedQty: number;
  receivedQty: number;
  invoicedQty: number;
  poUnitPrice: number | null;
  invoiceUnitPrice: number | null;
  poTaxPercent: number | null;
  invoiceTaxPercent: number | null;
};

type MatchFilter = "all" | "matched" | "pending" | "exception";

type ApprovalQueueRow = {
  reference: string;
  type: "Supplier Bill" | "Physical Count" | "Stock Adjustment";
  source: string;
  requestedBy: string;
  variance: string;
  risk: "Low" | "Medium" | "High";
  status: "Draft" | "Reviewed" | "Needs Approval" | "Approved" | "Rejected" | "Posted";
};

const TABS: Array<{ value: ReconcileTab; label: string }> = [
  { value: "supplier-bill-reconciliation", label: "Supplier Bill Reconciliation" },
  { value: "physical-count", label: "Physical Count" },
  { value: "stock-adjustments", label: "Stock Adjustments" },
  { value: "approval-review", label: "Approval Review" },
];

function parseTab(value: string | null): ReconcileTab {
  if (value === "physical-count" || value === "stock-adjustments" || value === "approval-review") return value;
  return "supplier-bill-reconciliation";
}

function numberFromUnknown(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function stringFromUnknown(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function parseProcurementItems(itemsJson: string): ProcurementLineInput[] {
  try {
    const parsed = JSON.parse(itemsJson || "[]");
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => ({
      medicineId: stringFromUnknown(item?.medicineId),
      medicineName: stringFromUnknown(item?.medicineName),
      quantity: numberFromUnknown(item?.quantity ?? item?.qty),
      expectedUnitCost: numberFromUnknown(item?.expectedUnitCost) || null,
      unitCost: numberFromUnknown(item?.unitCost) || null,
      taxPercent: numberFromUnknown(item?.taxPercent) || null,
      batchNumber: stringFromUnknown(item?.batchNumber) || null,
      expiryDate: stringFromUnknown(item?.expiryDate) || null,
      sellingPrice: numberFromUnknown(item?.sellingPrice) || null,
      unit: stringFromUnknown(item?.unit) || null,
      locationId: stringFromUnknown(item?.locationId) || null,
      remarks: stringFromUnknown(item?.remarks) || null,
    }));
  } catch {
    return [];
  }
}

function invoiceStatusLabel(status: SupplierInvoiceStatus): ReconciliationRow["status"] {
  switch (status) {
    case "DRAFT":
      return "Draft";
    case "MATCHED":
      return "Submitted";
    case "READY_FOR_PAYMENT":
    case "APPROVED_FOR_PAYMENT":
      return "Approved";
    case "PAID":
      return "Posted";
    case "CANCELLED":
      return "Cancelled";
    default:
      return "Draft";
  }
}

function buildTimeline(status: ReconciliationRow["status"], matchResult: ReconciliationRow["matchResult"]) {
  const hasGrn = matchResult !== "Awaiting GRN";
  const reviewed = status === "Reviewed" || status === "Approved" || status === "Posted";
  const approved = status === "Approved" || status === "Posted";
  const posted = status === "Posted";
  return [
    { label: "Invoice Submitted", state: "completed" as const },
    { label: "Matched to PO", state: "completed" as const },
    { label: "Matched to GRN", state: hasGrn ? "completed" as const : "current" as const },
    { label: "Reviewed", state: reviewed ? "completed" as const : hasGrn ? "current" as const : "pending" as const },
    { label: "Approved", state: approved ? "completed" as const : reviewed ? "current" as const : "pending" as const },
    { label: "Posted", state: posted ? "completed" as const : approved ? "current" as const : "pending" as const },
  ];
}

function buildException(matchResult: ReconciliationRow["matchResult"]) {
  switch (matchResult) {
    case "Duplicate Invoice":
      return {
        severity: "Critical" as const,
        recommendedAction: "Hold processing until duplicate invoice verification is completed.",
        responsibleRole: "Accounts Payable",
        expectedNextStep: "Validate whether this supplier invoice number already exists for the same supplier.",
      };
    case "Awaiting GRN":
      return {
        severity: "Warning" as const,
        recommendedAction: "Hold payment until goods receipt is completed.",
        responsibleRole: "Store Manager",
        expectedNextStep: "Receive the pending goods and post the GRN.",
      };
    case "Quantity Mismatch":
      return {
        severity: "Warning" as const,
        recommendedAction: "Review invoice quantity against received quantity before approval.",
        responsibleRole: "Procurement Lead",
        expectedNextStep: "Resolve the shortage or excess with stores and supplier.",
      };
    case "Price Mismatch":
      return {
        severity: "Warning" as const,
        recommendedAction: "Validate billed unit prices against the purchase order.",
        responsibleRole: "Procurement Lead",
        expectedNextStep: "Confirm approved rate or request invoice correction.",
      };
    case "Tax Difference":
      return {
        severity: "Info" as const,
        recommendedAction: "Validate tax treatment and confirm whether the difference is acceptable.",
        responsibleRole: "Accounts Payable",
        expectedNextStep: "Accept the corrected tax or request revised documentation.",
      };
    case "Matched":
      return {
        severity: "Info" as const,
        recommendedAction: "Reconciliation is aligned. Keep the bill read-only until approval workflow is enabled.",
        responsibleRole: "Accounts Payable",
        expectedNextStep: "Review supporting documents and continue approval.",
      };
    default:
      return {
        severity: "Warning" as const,
        recommendedAction: "Review incomplete reconciliation data before approval.",
        responsibleRole: "Procurement Officer",
        expectedNextStep: "Complete missing document links or line item data.",
      };
  }
}

function lineKey(item: ProcurementLineInput, index: number) {
  return item.medicineId || item.medicineName || `line-${index}`;
}

function parseNoteFields(notes: string | null | undefined) {
  const fields: Record<string, string> = {};
  (notes || "")
    .split("•")
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      const separator = part.indexOf(":");
      if (separator === -1) {
        return;
      }
      const key = part.slice(0, separator).trim().toLowerCase();
      const value = part.slice(separator + 1).trim();
      if (key && value && !fields[key]) {
        fields[key] = value;
      }
    });
  return fields;
}

function formatDateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : "-";
}

export default function PharmacyReconcilePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const tab = parseTab(searchParams.get("tab"));
  const [rows, setRows] = React.useState<ReconciliationRow[]>([]);
  const [inventoryTransactions, setInventoryTransactions] = React.useState<InventoryTransaction[]>([]);
  const [inventoryMedicines, setInventoryMedicines] = React.useState<Medicine[]>([]);
  const [inventoryStocks, setInventoryStocks] = React.useState<Stock[]>([]);
  const [loadingRows, setLoadingRows] = React.useState(true);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [selectedRowId, setSelectedRowId] = React.useState<string | null>(null);
  const [selectedPhysicalCountId, setSelectedPhysicalCountId] = React.useState<string | null>(null);
  const [matchFilter, setMatchFilter] = React.useState<MatchFilter>("all");
  const [supplierFilter, setSupplierFilter] = React.useState("all");
  const [searchTerm, setSearchTerm] = React.useState("");
  const [physicalCountSearchTerm, setPhysicalCountSearchTerm] = React.useState("");
  const [physicalCountStatusFilter, setPhysicalCountStatusFilter] = React.useState<"all" | "Posted">("Posted");
  const [physicalCountLocationFilter, setPhysicalCountLocationFilter] = React.useState("all");
  const [physicalCountPostedByFilter, setPhysicalCountPostedByFilter] = React.useState("all");
  const [physicalCountVarianceOnly, setPhysicalCountVarianceOnly] = React.useState(false);
  const [physicalCountFromDate, setPhysicalCountFromDate] = React.useState("");
  const [physicalCountToDate, setPhysicalCountToDate] = React.useState("");
  const [localMessage, setLocalMessage] = React.useState<string | null>(null);
  const [selectedInvoiceId, setSelectedInvoiceId] = React.useState<string | null>(null);

  const updateTab = (nextTab: ReconcileTab) => {
    navigate(`/pharmacy/reconcile?tab=${nextTab}`);
  };

  const supplierOptions = React.useMemo(
    () => Array.from(new Set(rows.map((row) => row.supplier))),
    [rows],
  );

  const filteredRows = React.useMemo(() => {
    return rows.filter((row) => {
      if (supplierFilter !== "all" && row.supplier !== supplierFilter) return false;
      if (matchFilter === "matched" && row.matchResult !== "Matched") return false;
      if (matchFilter === "pending" && row.matchResult !== "Awaiting GRN" && row.status !== "Draft" && row.status !== "Submitted") return false;
      if (matchFilter === "exception" && (row.matchResult === "Matched" || row.status === "Posted")) return false;
      if (searchTerm) {
        const haystack = `${row.invoiceNumber} ${row.supplier} ${row.poReference} ${row.grnReference}`.toLowerCase();
        if (!haystack.includes(searchTerm.toLowerCase())) return false;
      }
      return true;
    });
  }, [matchFilter, rows, searchTerm, supplierFilter]);

  const pendingCount = rows.filter((row) => row.status === "Draft" || row.status === "Submitted").length;
  const reviewedCount = rows.filter((row) => row.status === "Reviewed").length;
  const approvedCount = rows.filter((row) => row.status === "Approved").length;
  const postedCount = rows.filter((row) => row.status === "Posted").length;
  const inventoryMedicineById = React.useMemo(() => new Map(inventoryMedicines.map((medicine) => [medicine.id, medicine] as const)), [inventoryMedicines]);
  const inventoryStockById = React.useMemo(() => new Map(inventoryStocks.map((stock) => [stock.id, stock] as const)), [inventoryStocks]);
  const physicalCountArchiveRows = React.useMemo(() => {
    const archiveTransactions = inventoryTransactions.filter((transaction) => transaction.referenceType === "PHYSICAL_COUNT" || transaction.notes?.includes("Source: PHYSICAL_COUNT"));
    const groups = new Map<string, InventoryTransaction[]>();
    archiveTransactions.forEach((transaction) => {
      const key = transaction.referenceId || transaction.businessReference || transaction.id;
      groups.set(key, [...(groups.get(key) || []), transaction]);
    });
    const currentTime = new Date();
    return [...groups.entries()]
      .map(([sessionRef, sessionTransactions]) => {
        const sortedTransactions = [...sessionTransactions].sort((left, right) => left.createdAt.localeCompare(right.createdAt));
        const firstTransaction = sortedTransactions[0];
        const lastTransaction = sortedTransactions[sortedTransactions.length - 1];
        const firstNotes = parseNoteFields(firstTransaction.notes);
        const lastNotes = parseNoteFields(lastTransaction.notes);
        const sessionName = firstNotes.session || firstTransaction.reason?.replace(/^Physical Count\s*/i, "") || firstTransaction.referenceId || "Physical Count Session";
        const locationName = firstNotes.location
          || (firstTransaction.stockBatchId ? inventoryStockById.get(firstTransaction.stockBatchId)?.locationName : null)
          || "Main Pharmacy";
        const postedBy = lastNotes["posted by"] || lastTransaction.adjustedByName || lastTransaction.createdBy || "System";
        const createdBy = firstTransaction.createdBy || postedBy;
        const scope = firstNotes.scope || "Entire Inventory";
        const reason = firstTransaction.reason || "Physical Count";
        const timeline = [
          { label: "Created", state: "completed" as const },
          { label: "Started", state: "completed" as const },
          { label: "Submitted", state: "completed" as const },
          { label: "Reviewed", state: "completed" as const },
          { label: "Approved", state: "completed" as const },
          { label: "Posted", state: "completed" as const },
          { label: "Archived", state: "completed" as const },
        ];
        const lines = sortedTransactions.map((transaction) => {
          const stock = transaction.stockBatchId ? inventoryStockById.get(transaction.stockBatchId) || null : null;
          const medicine = inventoryMedicineById.get(transaction.medicineId)?.medicineName || stock?.medicineName || transaction.medicineId;
          const batch = transaction.batchNumber || stock?.batchNumber || "No batch";
          const beforeQty = transaction.beforeQuantity ?? 0;
          const signedQuantity = transaction.transactionType === "ADJUSTMENT_OUT" ? -transaction.quantity : transaction.quantity;
          const afterQty = transaction.afterQuantity ?? beforeQty + signedQuantity;
          const countedQty = afterQty;
          const difference = afterQty - beforeQty;
          const rate = stock?.unitCost ?? stock?.purchasePrice ?? stock?.sellingPrice ?? 0;
          return {
            movementId: transaction.id,
            adjustmentId: transaction.referenceId || transaction.id,
            medicine,
            batch,
            location: stock?.locationName || locationName,
            systemQty: beforeQty,
            countedQty,
            difference,
            reason: transaction.reason || reason,
            result: difference === 0 ? "Matched" : difference < 0 ? "Short" : "Excess",
            movementType: difference < 0 ? "ADJUSTMENT_OUT" as const : "ADJUSTMENT_IN" as const,
            postedTimestamp: transaction.createdAt,
            postedBy,
            notes: transaction.notes || "",
            varianceValue: Math.abs(difference) * rate,
          };
        });
        const varianceItems = lines.filter((line) => line.difference !== 0).length;
        const varianceValue = lines.reduce((sum, line) => sum + line.varianceValue, 0);
        const matched = lines.filter((line) => line.difference === 0).length;
        const short = lines.filter((line) => line.difference < 0).length;
        const excess = lines.filter((line) => line.difference > 0).length;
        const largeVariance = lines.filter((line) => Math.abs(line.difference) >= 10).length;
        const countDate = firstTransaction.createdAt;
        const postedDate = lastTransaction.createdAt;
        return {
          id: sessionRef,
          session: sessionName,
          location: locationName,
          countDate,
          postedDate,
          postedBy,
          createdBy,
          reason,
          scope,
          status: "Posted" as const,
          itemsCounted: lines.length,
          varianceItems,
          varianceValue,
          matched,
          short,
          excess,
          largeVariance,
          lines,
          timeline,
          archiveAgeDays: Math.max(0, Math.floor((currentTime.getTime() - new Date(postedDate).getTime()) / (1000 * 60 * 60 * 24))),
        };
      })
      .sort((left, right) => right.postedDate.localeCompare(left.postedDate));
  }, [inventoryMedicines, inventoryStocks, inventoryTransactions]);
  const physicalCountLocationOptions = React.useMemo(
    () => Array.from(new Set(physicalCountArchiveRows.map((row) => row.location))).sort((left, right) => left.localeCompare(right)),
    [physicalCountArchiveRows],
  );
  const physicalCountPostedByOptions = React.useMemo(
    () => Array.from(new Set(physicalCountArchiveRows.map((row) => row.postedBy))).sort((left, right) => left.localeCompare(right)),
    [physicalCountArchiveRows],
  );
  const filteredPhysicalCountArchiveRows = React.useMemo(() => {
    const term = physicalCountSearchTerm.trim().toLowerCase();
    return physicalCountArchiveRows.filter((row) => {
      if (physicalCountStatusFilter !== "all" && row.status !== physicalCountStatusFilter) return false;
      if (physicalCountLocationFilter !== "all" && row.location !== physicalCountLocationFilter) return false;
      if (physicalCountPostedByFilter !== "all" && row.postedBy !== physicalCountPostedByFilter) return false;
      if (physicalCountVarianceOnly && row.varianceItems === 0) return false;
      const postedDate = new Date(row.postedDate);
      if (physicalCountFromDate && postedDate < new Date(`${physicalCountFromDate}T00:00:00`)) return false;
      if (physicalCountToDate && postedDate > new Date(`${physicalCountToDate}T23:59:59`)) return false;
      if (term) {
        const haystack = [
          row.session,
          row.location,
          row.createdBy,
          row.postedBy,
          row.reason,
          row.scope,
          ...row.lines.map((line) => [line.medicine, line.batch, line.location, line.reason, line.notes].join(" ")),
        ].join(" ").toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [physicalCountArchiveRows, physicalCountFromDate, physicalCountLocationFilter, physicalCountPostedByFilter, physicalCountSearchTerm, physicalCountStatusFilter, physicalCountToDate, physicalCountVarianceOnly]);
  const selectedPhysicalCount = React.useMemo(
    () => filteredPhysicalCountArchiveRows.find((row) => row.id === selectedPhysicalCountId) || null,
    [filteredPhysicalCountArchiveRows, selectedPhysicalCountId],
  );
  const selectedInvoice = React.useMemo(
    () => rows.find((row) => row.id === selectedInvoiceId) ?? null,
    [rows, selectedInvoiceId],
  );
  const selectedInvoiceRelationshipStages = React.useMemo<DocumentRelationshipStage[]>(() => {
    if (!selectedInvoice) return [];
    const poMissing = !selectedInvoice.poReference || selectedInvoice.poReference === "Unlinked";
    const grnMissing = !selectedInvoice.grnReference || selectedInvoice.grnReference === "-";
    const invoiceCancelled = selectedInvoice.status === "Cancelled";
    return [
      {
        label: "Purchase Order",
        documentNumber: poMissing ? "Missing" : selectedInvoice.poReference,
        badgeLabel: poMissing ? "Missing" : "Completed",
        state: poMissing ? "future" : "completed",
      },
      {
        label: "Supplier Invoice",
        documentNumber: selectedInvoice.invoiceNumber,
        badgeLabel: selectedInvoice.status,
        state: invoiceCancelled ? "cancelled" : "current",
      },
      {
        label: "Goods Receipt",
        documentNumber: grnMissing ? "Pending" : selectedInvoice.grnReference,
        badgeLabel: grnMissing ? "Pending" : "Completed",
        state: grnMissing ? "future" : "completed",
      },
      {
        label: "Inventory Updated",
        documentNumber: selectedInvoice.inventoryUpdated ? "Completed" : "Pending",
        badgeLabel: selectedInvoice.inventoryUpdated ? "Completed" : "Pending",
        state: selectedInvoice.inventoryUpdated ? "completed" : "future",
      },
      {
        label: "Supplier Bill Reconciliation",
        documentNumber: selectedInvoice.id,
        badgeLabel: invoiceCancelled ? "Cancelled" : "Current",
        state: invoiceCancelled ? "cancelled" : "current",
      },
    ];
  }, [selectedInvoice]);
  const inspectedInvoice = React.useMemo(
    () => rows.find((row) => row.id === selectedRowId) ?? null,
    [rows, selectedRowId],
  );
  const physicalCountRows = React.useMemo(() => [] as Array<{
    countSession: string;
    location: string;
    countDate: string;
    itemsCounted: number;
    varianceItems: number;
    varianceValue: number;
    status: "Draft" | "Reviewed" | "Needs Approval" | "Approved" | "Posted";
  }>, []);
  const stockAdjustmentRows = React.useMemo(() => [] as Array<{
    adjustmentRef: string;
    medicine: string;
    batch: string;
    location: string;
    type: "Damage" | "Expiry" | "Correction" | "Transfer" | "Lost" | "Found";
    quantity: number;
    varianceValue: number;
    status: "Draft" | "Reviewed" | "Needs Approval" | "Approved" | "Posted";
  }>, []);
  const approvalQueueRows = React.useMemo<ApprovalQueueRow[]>(() => {
    const supplierBillRows = rows
      .filter((row) =>
        row.status === "Reviewed"
        || row.matchResult === "Quantity Mismatch"
        || row.matchResult === "Price Mismatch"
        || row.matchResult === "Tax Difference"
        || row.matchResult === "Duplicate Invoice",
      )
      .map((row) => ({
        reference: row.invoiceNumber,
        type: "Supplier Bill" as const,
        source: row.supplier,
        requestedBy: "Accounts Payable",
        variance: `INR ${money(row.varianceAmount)} / Qty ${row.varianceQuantity}`,
        risk: (row.matchResult === "Duplicate Invoice" ? "High" : row.matchResult === "Matched" ? "Low" : "Medium") as ApprovalQueueRow["risk"],
        status: row.status === "Approved" || row.status === "Posted"
          ? row.status
          : row.status === "Reviewed"
            ? "Reviewed"
            : "Needs Approval" as ApprovalQueueRow["status"],
      }));
    return supplierBillRows;
  }, [rows]);

  const matchTone = (matchResult: ReconciliationRow["matchResult"]) => {
    if (matchResult === "Matched") return "success" as const;
    if (matchResult === "Awaiting GRN" || matchResult === "Needs Review") return "warning" as const;
    return "error" as const;
  };

  const statusTone = (status: ReconciliationRow["status"]) => {
    if (status === "Approved" || status === "Posted") return "success" as const;
    if (status === "Reviewed") return "info" as const;
    if (status === "Submitted") return "primary" as const;
    return "default" as const;
  };

  const severityTone = (severity: ReconciliationRow["exception"]["severity"]) => {
    if (severity === "Critical") return "error" as const;
    if (severity === "Warning") return "warning" as const;
    return "info" as const;
  };
  function money(value: number) {
    return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  const queueStatusTone = (status: ApprovalQueueRow["status"]) => {
    if (status === "Approved" || status === "Posted") return "success" as const;
    if (status === "Reviewed") return "primary" as const;
    if (status === "Needs Approval") return "warning" as const;
    if (status === "Rejected") return "error" as const;
    return "default" as const;
  };
  const queueRiskTone = (risk: ApprovalQueueRow["risk"]) => {
    if (risk === "High") return "error" as const;
    if (risk === "Medium") return "warning" as const;
    return "success" as const;
  };

  const openPurchaseOrder = React.useCallback((row: ReconciliationRow | null) => {
    if (!row?.poReference || row.poReference === "Unlinked") return;
    setLocalMessage(null);
    setSelectedInvoiceId(null);
    navigate(`/pharmacy/procurement?workspace=purchase-orders&po=${encodeURIComponent(row.poReference)}`);
  }, [navigate]);

  const openGoodsReceipt = React.useCallback((row: ReconciliationRow | null) => {
    if (!row?.grnReference || row.grnReference === "-") return;
    setLocalMessage(null);
    setSelectedInvoiceId(null);
    navigate(`/pharmacy/procurement?workspace=goods-receipt&grn=${encodeURIComponent(row.grnReference)}`);
  }, [navigate]);

  const openInventory = React.useCallback((row: ReconciliationRow | null) => {
    setLocalMessage(null);
    setSelectedInvoiceId(null);
    if (row?.grnReference && row.grnReference !== "-") {
      navigate(`/pharmacy/inventory?source=grn&reference=${encodeURIComponent(row.grnReference)}`);
      return;
    }
    if (row?.poReference && row.poReference !== "Unlinked") {
      navigate(`/pharmacy/inventory?source=po&reference=${encodeURIComponent(row.poReference)}`);
      return;
    }
    navigate("/pharmacy/inventory");
  }, [navigate]);

  React.useEffect(() => {
    if (!auth.accessToken || !auth.tenantId) {
      setRows([]);
      setInventoryTransactions([]);
      setInventoryStocks([]);
      setInventoryMedicines([]);
      setLoadingRows(false);
      return;
    }
    let cancelled = false;
    const load = async () => {
      setLoadingRows(true);
      setLoadError(null);
      try {
        const [purchaseOrders, supplierInvoices, goodsReceipts, inventoryMovementRows, inventoryStockRows, inventoryMedicineRows] = await Promise.all([
          getPurchaseOrders(auth.accessToken!, auth.tenantId!),
          getSupplierInvoices(auth.accessToken!, auth.tenantId!),
          getGoodsReceipts(auth.accessToken!, auth.tenantId!),
          getInventoryTransactions(auth.accessToken!, auth.tenantId!),
          getStocks(auth.accessToken!, auth.tenantId!),
          getMedicines(auth.accessToken!, auth.tenantId!),
        ]);

        const poMap = new Map(purchaseOrders.map((po) => [po.id, po]));
        const grnsByInvoiceId = new Map<string, GoodsReceipt[]>();
        const grnsByPoId = new Map<string, GoodsReceipt[]>();
        goodsReceipts.forEach((grn) => {
          if (grn.supplierInvoiceId) {
            grnsByInvoiceId.set(grn.supplierInvoiceId, [...(grnsByInvoiceId.get(grn.supplierInvoiceId) ?? []), grn]);
          }
          if (grn.purchaseOrderId) {
            grnsByPoId.set(grn.purchaseOrderId, [...(grnsByPoId.get(grn.purchaseOrderId) ?? []), grn]);
          }
        });

        const duplicateCounts = supplierInvoices.reduce((acc, invoice) => {
          const key = `${invoice.supplierId}::${invoice.invoiceNumber.trim().toLowerCase()}`;
          acc.set(key, (acc.get(key) ?? 0) + 1);
          return acc;
        }, new Map<string, number>());

        const nextRows = supplierInvoices.map((invoice) => {
          const purchaseOrder = invoice.purchaseOrderId ? poMap.get(invoice.purchaseOrderId) ?? null : null;
          const relatedGrns = grnsByInvoiceId.get(invoice.id)
            ?? (invoice.purchaseOrderId ? grnsByPoId.get(invoice.purchaseOrderId) ?? [] : []);
          const goodsReceipt = [...relatedGrns]
            .sort((left, right) => (right.confirmedAt || right.receivedAt || right.createdAt).localeCompare(left.confirmedAt || left.receivedAt || left.createdAt))[0] ?? null;

          const poItems = parseProcurementItems(purchaseOrder?.itemsJson || "[]");
          const invoiceItems = parseProcurementItems(invoice.itemsJson || "[]");
          const grnItems = parseProcurementItems(goodsReceipt?.itemsJson || "[]");

          const lineMap = new Map<string, ReconciliationLine>();
          poItems.forEach((item, index) => {
            const key = lineKey(item, index);
            lineMap.set(key, {
              medicineKey: key,
              medicine: item.medicineName || item.medicineId || `Item ${index + 1}`,
              orderedQty: item.quantity || 0,
              receivedQty: 0,
              invoicedQty: 0,
              poUnitPrice: item.unitCost ?? item.expectedUnitCost ?? null,
              invoiceUnitPrice: null,
              poTaxPercent: item.taxPercent ?? null,
              invoiceTaxPercent: null,
            });
          });
          invoiceItems.forEach((item, index) => {
            const key = lineKey(item, index);
            const current = lineMap.get(key) ?? {
              medicineKey: key,
              medicine: item.medicineName || item.medicineId || `Item ${index + 1}`,
              orderedQty: 0,
              receivedQty: 0,
              invoicedQty: 0,
              poUnitPrice: null,
              invoiceUnitPrice: null,
              poTaxPercent: null,
              invoiceTaxPercent: null,
            };
            current.invoicedQty += item.quantity || 0;
            current.invoiceUnitPrice = item.unitCost ?? item.expectedUnitCost ?? current.invoiceUnitPrice;
            current.invoiceTaxPercent = item.taxPercent ?? current.invoiceTaxPercent;
            lineMap.set(key, current);
          });
          grnItems.forEach((item, index) => {
            const key = lineKey(item, index);
            const current = lineMap.get(key) ?? {
              medicineKey: key,
              medicine: item.medicineName || item.medicineId || `Item ${index + 1}`,
              orderedQty: 0,
              receivedQty: 0,
              invoicedQty: 0,
              poUnitPrice: null,
              invoiceUnitPrice: null,
              poTaxPercent: null,
              invoiceTaxPercent: null,
            };
            current.receivedQty += item.quantity || 0;
            lineMap.set(key, current);
          });

          const lines = [...lineMap.values()];
          const comparisonRows = lines.map((line) => {
            const difference = line.invoicedQty - line.receivedQty;
            const priceDelta = line.poUnitPrice != null && line.invoiceUnitPrice != null
              ? (line.invoiceUnitPrice - line.poUnitPrice) * (line.invoicedQty || line.orderedQty || 0)
              : 0;
            const qtyDelta = difference * (line.invoiceUnitPrice ?? line.poUnitPrice ?? 0);
            const variance = Math.round((priceDelta + qtyDelta) * 100) / 100;
            let result = "Matched";
            if (!goodsReceipt) result = "Awaiting GRN";
            else if (difference !== 0) result = "Quantity Mismatch";
            else if ((line.poUnitPrice ?? null) !== (line.invoiceUnitPrice ?? null)) result = "Price Mismatch";
            else if ((line.poTaxPercent ?? null) !== (line.invoiceTaxPercent ?? null)) result = "Tax Difference";
            return {
              medicine: line.medicine,
              orderedQty: line.orderedQty,
              receivedQty: line.receivedQty,
              invoicedQty: line.invoicedQty,
              difference,
              variance,
              result,
            };
          });

          const duplicateKey = `${invoice.supplierId}::${invoice.invoiceNumber.trim().toLowerCase()}`;
          const isDuplicate = (duplicateCounts.get(duplicateKey) ?? 0) > 1;
          const quantityMismatch = comparisonRows.some((row) => row.difference !== 0);
          const priceMismatch = lines.some((line) => line.poUnitPrice != null && line.invoiceUnitPrice != null && Math.abs(line.poUnitPrice - line.invoiceUnitPrice) > 0.0001);
          const taxDifference = lines.some((line) => line.poTaxPercent != null && line.invoiceTaxPercent != null && Math.abs(line.poTaxPercent - line.invoiceTaxPercent) > 0.0001)
            || (invoice.taxAmount != null && invoice.totalAmount != null && invoice.invoiceAmount != null
              ? Math.abs((invoice.totalAmount - invoice.invoiceAmount + (invoice.discountAmount ?? 0)) - invoice.taxAmount) > 0.0001
              : false);

          let matchResult: ReconciliationRow["matchResult"] = "Needs Review";
          if (isDuplicate) matchResult = "Duplicate Invoice";
          else if (!purchaseOrder) matchResult = "Needs Review";
          else if (!goodsReceipt) matchResult = "Awaiting GRN";
          else if (quantityMismatch) matchResult = "Quantity Mismatch";
          else if (priceMismatch) matchResult = "Price Mismatch";
          else if (taxDifference) matchResult = "Tax Difference";
          else if (purchaseOrder && goodsReceipt) matchResult = "Matched";

          const status = invoiceStatusLabel(invoice.status);
          const lastActivity = invoice.updatedAt
            ? `${status} on ${new Date(invoice.updatedAt).toLocaleDateString()}`
            : `${status} status loaded from supplier invoice`;
          const varianceAmount = invoice.varianceAmount ?? comparisonRows.reduce((sum, row) => sum + row.variance, 0);
          const varianceQuantity = comparisonRows.reduce((sum, row) => sum + Math.abs(row.difference), 0);

          return {
            id: invoice.id,
            invoiceNumber: invoice.invoiceNumber,
            supplierId: invoice.supplierId,
            supplier: invoice.supplierName || purchaseOrder?.supplierName || "Unknown supplier",
            poReference: purchaseOrder?.poNumber || "Unlinked",
            grnReference: goodsReceipt?.receiptNumber || "-",
            inventoryUpdated: Boolean(goodsReceipt?.confirmedAt),
            invoiceDate: invoice.invoiceDate,
            invoiceAmount: invoice.totalAmount ?? invoice.invoiceAmount ?? 0,
            varianceAmount,
            varianceQuantity,
            matchResult,
            status,
            variance: varianceAmount,
            lastActivity,
            comparisonRows,
            exception: buildException(matchResult),
            timeline: buildTimeline(status, matchResult),
          } satisfies ReconciliationRow;
        });

        if (!cancelled) {
          setRows(nextRows);
          setInventoryTransactions(inventoryMovementRows);
          setInventoryStocks(inventoryStockRows);
          setInventoryMedicines(inventoryMedicineRows);
        }
      } catch (err) {
        if (!cancelled) {
          setRows([]);
          setLoadError(err instanceof Error ? err.message : "Unable to load reconciliation data.");
        }
      } finally {
        if (!cancelled) {
          setLoadingRows(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (selectedRowId && !rows.some((row) => row.id === selectedRowId)) {
      setSelectedRowId(null);
    }
    if (selectedInvoiceId && !rows.some((row) => row.id === selectedInvoiceId)) {
      setSelectedInvoiceId(null);
    }
  }, [rows, selectedInvoiceId, selectedRowId]);

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Reconcile</Typography>
          <Typography variant="body2" color="text.secondary">Local reconciliation workspace with URL-synced tabs.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/inventory")}>Inventory</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/pos")}>POS Sale</Button>
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Draft / Submitted" value={pendingCount} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Reviewed" value={reviewedCount} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Approved" value={approvedCount} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Posted" value={postedCount} />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          <Box role="tablist" aria-label="Reconcile tabs" sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
            {TABS.map((item) => {
              const active = tab === item.value;
              return (
                <ButtonBase
                  key={item.value}
                  role="tab"
                  aria-selected={active}
                  tabIndex={0}
                  onClick={() => updateTab(item.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      updateTab(item.value);
                    }
                  }}
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 999,
                    border: "1px solid",
                    borderColor: active ? "primary.main" : "divider",
                    bgcolor: active ? "action.selected" : "background.paper",
                    color: active ? "primary.main" : "text.secondary",
                    fontWeight: active ? 800 : 600,
                    cursor: "pointer",
                    "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
                  }}
                >
                  {item.label}
                </ButtonBase>
              );
            })}
          </Box>
        </CardContent>
      </Card>

      {tab === "supplier-bill-reconciliation" ? (
        <Stack spacing={2}>
          {localMessage ? <Alert severity="info" onClose={() => setLocalMessage(null)}>{localMessage}</Alert> : null}
          {loadError ? <Alert severity="error">Unable to load reconciliation data.</Alert> : null}

          <CompactFilterCard title="Supplier Bill Reconciliation" subtitle="Local-only table. No drawer, no inspector, no document navigation.">
            <Grid container spacing={1.25}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  size="small"
                  fullWidth
                  label="Search"
                  value={searchTerm}
                  onChange={(event) => setSearchTerm(event.target.value)}
                  placeholder="Invoice, supplier, PO, or GRN"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <FormControl size="small" fullWidth>
                  <InputLabel id="reconcile-match-filter-label">Match Result</InputLabel>
                  <Select
                    labelId="reconcile-match-filter-label"
                    label="Match Result"
                    value={matchFilter}
                    onChange={(event) => setMatchFilter(event.target.value as MatchFilter)}
                  >
                    <MenuItem value="all">All</MenuItem>
                    <MenuItem value="matched">Matched</MenuItem>
                    <MenuItem value="pending">Pending</MenuItem>
                    <MenuItem value="exception">Exception</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <FormControl size="small" fullWidth>
                  <InputLabel id="reconcile-supplier-filter-label">Supplier</InputLabel>
                  <Select
                    labelId="reconcile-supplier-filter-label"
                    label="Supplier"
                    value={supplierFilter}
                    onChange={(event) => setSupplierFilter(String(event.target.value))}
                  >
                    <MenuItem value="all">All suppliers</MenuItem>
                    {supplierOptions.map((supplier) => (
                      <MenuItem key={supplier} value={supplier}>{supplier}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </CompactFilterCard>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, xl: 8 }}>
              <CompactFilterCard title="Reconciliation List" subtitle="Rows update local component state only.">
                {loadingRows ? (
                  <Typography variant="body2" color="text.secondary">Loading reconciliation data...</Typography>
                ) : loadError ? (
                  <CompactEmptyState title="Unable to load reconciliation data." subtitle="Check supplier invoice, purchase order, and goods receipt connectivity, then refresh." />
                ) : filteredRows.length ? (
                  <TableContainer sx={{ width: "100%", maxWidth: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Invoice No</TableCell>
                          <TableCell>Supplier</TableCell>
                          <TableCell>PO</TableCell>
                          <TableCell>GRN</TableCell>
                          <TableCell>Match Result</TableCell>
                          <TableCell align="right">Variance</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell align="right">Action</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {filteredRows.map((row) => (
                          <TableRow
                            key={row.id}
                            hover
                            selected={selectedRowId === row.id}
                            onClick={() => setSelectedRowId(row.id)}
                            sx={{ cursor: "pointer" }}
                          >
                            <TableCell>{row.invoiceNumber}</TableCell>
                            <TableCell>{row.supplier}</TableCell>
                            <TableCell>{row.poReference}</TableCell>
                            <TableCell>{row.grnReference}</TableCell>
                            <TableCell>
                              <Chip size="small" label={row.matchResult} color={matchTone(row.matchResult)} sx={compactChipSx} />
                            </TableCell>
                            <TableCell align="right">{row.variance}</TableCell>
                            <TableCell>
                              <Chip size="small" label={row.status} color={statusTone(row.status)} sx={compactChipSx} />
                            </TableCell>
                            <TableCell align="right">
                              <Button
                                size="small"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setSelectedRowId(row.id);
                                  setLocalMessage(null);
                                  setSelectedInvoiceId(row.id);
                                }}
                              >
                                View
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                ) : (
                  <CompactEmptyState title="No supplier bills available for reconciliation." subtitle="No supplier invoices are currently available for three-way matching." />
                )}
              </CompactFilterCard>
            </Grid>

            <Grid size={{ xs: 12, xl: 4 }}>
              <CompactFilterCard title="Selected Invoice" subtitle={inspectedInvoice ? inspectedInvoice.invoiceNumber : "No invoice selected."}>
                {inspectedInvoice ? (
                  <Stack spacing={1.1}>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">Invoice No</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{inspectedInvoice.invoiceNumber}</Typography>
                    </Stack>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">Supplier</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{inspectedInvoice.supplier}</Typography>
                    </Stack>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">PO</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{inspectedInvoice.poReference}</Typography>
                    </Stack>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">GRN</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{inspectedInvoice.grnReference}</Typography>
                    </Stack>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Chip size="small" label={inspectedInvoice.matchResult} color={matchTone(inspectedInvoice.matchResult)} sx={compactChipSx} />
                      <Chip size="small" label={inspectedInvoice.status} color={statusTone(inspectedInvoice.status)} sx={compactChipSx} />
                    </Stack>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">Variance</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>INR {money(inspectedInvoice.varianceAmount)} / Qty {inspectedInvoice.varianceQuantity}</Typography>
                    </Stack>
                    <Stack spacing={0.25}>
                      <Typography variant="caption" color="text.secondary">Last Activity</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{inspectedInvoice.lastActivity}</Typography>
                    </Stack>

                    <Divider />

                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() => {
                          setLocalMessage(null);
                          setSelectedInvoiceId(inspectedInvoice.id);
                        }}
                      >
                        Open Detail Drawer
                      </Button>
                      <Tooltip title="PO reference available. Direct navigation will be enabled after procurement deep-link routing is stable.">
                        <span>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled
                          >
                            View PO
                          </Button>
                        </span>
                      </Tooltip>
                      <Tooltip title="GRN reference available. Direct navigation will be enabled after goods receipt deep-link routing is stable.">
                        <span>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled
                          >
                            View GRN
                          </Button>
                        </span>
                      </Tooltip>
                      <Tooltip title="Open inventory using the most relevant document reference.">
                        <span>
                          <Button size="small" variant="outlined" onClick={() => openInventory(inspectedInvoice)}>Open Inventory</Button>
                        </span>
                      </Tooltip>
                      <Tooltip title="Stock movement drill-down will be enabled after inventory reference filtering.">
                        <span>
                          <Button size="small" variant="outlined" disabled>View Stock Movement</Button>
                        </span>
                      </Tooltip>
                    </Stack>
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No invoice selected. Select a bill to inspect reconciliation details.
                  </Typography>
                )}
              </CompactFilterCard>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      <Drawer
        anchor="right"
        open={Boolean(selectedInvoice)}
        onClose={() => {
          setLocalMessage(null);
          setSelectedInvoiceId(null);
        }}
        ModalProps={{ keepMounted: false }}
        PaperProps={{
          sx: {
            width: { xs: "100%", sm: 720, lg: 780 },
            maxWidth: "100%",
            boxSizing: "border-box",
          },
        }}
      >
        <Box sx={{ p: 2, height: "100%", overflow: "auto" }}>
          {selectedInvoice ? (
            <Stack spacing={2}>
              <Stack direction="row" justifyContent="space-between" spacing={2} alignItems="flex-start">
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Supplier Bill Reconciliation</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedInvoice.invoiceNumber} · {selectedInvoice.supplier}
                  </Typography>
                </Box>
                <Button size="small" onClick={() => {
                  setLocalMessage(null);
                  setSelectedInvoiceId(null);
                }}
                >
                  Close
                </Button>
              </Stack>

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip size="small" label={selectedInvoice.status} color={statusTone(selectedInvoice.status)} sx={compactChipSx} />
                <Chip size="small" label={selectedInvoice.matchResult} color={matchTone(selectedInvoice.matchResult)} sx={compactChipSx} />
              </Stack>

              <DocumentRelationshipStrip
                title="Document Relationship"
                stages={selectedInvoiceRelationshipStages}
              />

              <CompactFilterCard title="Summary" subtitle="Read-only invoice and document match details.">
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Invoice number" value={selectedInvoice.invoiceNumber} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Supplier" value={selectedInvoice.supplier} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Invoice date" value={selectedInvoice.invoiceDate} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Invoice amount" value={`INR ${money(selectedInvoice.invoiceAmount)}`} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="PO reference" value={selectedInvoice.poReference} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="GRN reference" value={selectedInvoice.grnReference} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Variance amount" value={`INR ${money(selectedInvoice.varianceAmount)}`} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Variance quantity" value={String(selectedInvoice.varianceQuantity)} InputProps={{ readOnly: true }} />
                  </Grid>
                </Grid>
              </CompactFilterCard>

              <CompactFilterCard title="Three-way Comparison" subtitle="PO, GRN, and invoice line comparison.">
                <TableContainer sx={{ width: "100%", maxWidth: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell align="right">Ordered Qty</TableCell>
                        <TableCell align="right">Received Qty</TableCell>
                        <TableCell align="right">Invoiced Qty</TableCell>
                        <TableCell align="right">Difference</TableCell>
                        <TableCell align="right">Variance</TableCell>
                        <TableCell>Result</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {selectedInvoice.comparisonRows.map((comparisonRow) => (
                        <TableRow key={`${selectedInvoice.id}-${comparisonRow.medicine}`}>
                          <TableCell>{comparisonRow.medicine}</TableCell>
                          <TableCell align="right">{comparisonRow.orderedQty}</TableCell>
                          <TableCell align="right">{comparisonRow.receivedQty}</TableCell>
                          <TableCell align="right">{comparisonRow.invoicedQty}</TableCell>
                          <TableCell align="right">{comparisonRow.difference}</TableCell>
                          <TableCell align="right">INR {money(comparisonRow.variance)}</TableCell>
                          <TableCell>
                            <Chip size="small" label={comparisonRow.result} color={matchTone(selectedInvoice.matchResult)} sx={compactChipSx} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CompactFilterCard>

              <CompactFilterCard title="Exception Explanation" subtitle="Operational handling guidance for the current variance.">
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">Severity</Typography>
                      <Chip size="small" label={selectedInvoice.exception.severity} color={severityTone(selectedInvoice.exception.severity)} sx={compactChipSx} />
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, md: 8 }}>
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">Recommended Action</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedInvoice.exception.recommendedAction}</Typography>
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">Responsible Role</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedInvoice.exception.responsibleRole}</Typography>
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">Expected Next Step</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{selectedInvoice.exception.expectedNextStep}</Typography>
                    </Stack>
                  </Grid>
                </Grid>
              </CompactFilterCard>

              <CompactFilterCard title="Timeline" subtitle="Read-only progression through reconciliation stages.">
                <Stack spacing={1}>
                  {selectedInvoice.timeline.map((timelineStep, index) => (
                    <Stack key={`${selectedInvoice.id}-${timelineStep.label}`} direction="row" spacing={1.25} alignItems="stretch">
                      <Stack alignItems="center" spacing={0.35} sx={{ width: 18, flex: "0 0 auto" }}>
                        <Box
                          aria-hidden
                          sx={{
                            width: 14,
                            height: 14,
                            borderRadius: "50%",
                            bgcolor: timelineStep.state === "completed" ? "success.main" : timelineStep.state === "current" ? "primary.main" : "transparent",
                            border: "2px solid",
                            borderColor: timelineStep.state === "pending" ? "divider" : timelineStep.state === "current" ? "primary.main" : "success.main",
                          }}
                        />
                        {index < selectedInvoice.timeline.length - 1 ? (
                          <Box
                            aria-hidden
                            sx={{
                              width: 2,
                              flex: 1,
                              bgcolor: timelineStep.state === "pending" ? "divider" : timelineStep.state === "current" ? "primary.light" : "success.light",
                            }}
                          />
                        ) : null}
                      </Stack>
                      <Stack spacing={0.25} sx={{ pb: index < selectedInvoice.timeline.length - 1 ? 1 : 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{timelineStep.label}</Typography>
                        <Chip
                          size="small"
                          label={timelineStep.state === "completed" ? "Completed" : timelineStep.state === "current" ? "Current" : "Pending"}
                          color={timelineStep.state === "completed" ? "success" : timelineStep.state === "current" ? "primary" : "default"}
                          variant={timelineStep.state === "pending" ? "outlined" : "filled"}
                          sx={compactChipSx}
                        />
                      </Stack>
                    </Stack>
                  ))}
                </Stack>
              </CompactFilterCard>

              <Divider />

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Tooltip title="Local-only action. Backend workflow is not wired from this drawer yet.">
                  <span>
                    <Button size="small" variant="outlined" disabled>
                      Mark Reviewed
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="Local-only action. Backend workflow is not wired from this drawer yet.">
                  <span>
                    <Button size="small" variant="contained" disabled>
                      Send for Approval
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="PO reference available. Direct navigation will be enabled after procurement deep-link routing is stable.">
                  <span>
                    <Button
                      size="small"
                      variant="outlined"
                      disabled
                    >
                      View PO
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="GRN reference available. Direct navigation will be enabled after goods receipt deep-link routing is stable.">
                  <span>
                    <Button
                      size="small"
                      variant="outlined"
                      disabled
                    >
                      View GRN
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="Open inventory using the most relevant document reference.">
                  <span>
                    <Button size="small" variant="outlined" onClick={() => openInventory(selectedInvoice)}>
                      Open Inventory
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="Stock movement drill-down will be enabled after inventory reference filtering.">
                  <span>
                    <Button size="small" variant="outlined" disabled>
                      View Stock Movement
                    </Button>
                  </span>
                </Tooltip>
                <Button size="small" onClick={() => {
                  setLocalMessage(null);
                  setSelectedInvoiceId(null);
                }}
                >
                  Close
                </Button>
              </Stack>
            </Stack>
          ) : null}
        </Box>
      </Drawer>

      {tab === "physical-count" ? (
        <Stack spacing={2}>
          <CompactFilterCard
            title="Physical Count Reconciliation"
            subtitle="Posted physical count sessions are captured as read-only reconciliation records from inventory adjustments."
          >
            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {["Create Session", "Count Medicines", "Submit", "Review", "Approve", "Post Adjustments", "Reconciliation", "Archive"].map((step) => (
                  <Chip key={step} size="small" label={step} variant="outlined" sx={compactChipSx} />
                ))}
              </Stack>
              <Typography variant="body2" color="text.secondary">
                The archive below is derived from posted PHYSICAL_COUNT stock movements. It is read-only and does not modify inventory.
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="contained" onClick={() => openInventory(null)}>Open Physical Count</Button>
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Export PDF</Button></span>
                </Tooltip>
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Export Excel</Button></span>
                </Tooltip>
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Print Count Sheet</Button></span>
                </Tooltip>
              </Stack>
            </Stack>
          </CompactFilterCard>

          <Grid container spacing={1.25}>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Sessions Posted" value={physicalCountArchiveRows.length} helper="Read-only archive records" />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Variance Items" value={physicalCountArchiveRows.reduce((sum, row) => sum + row.varianceItems, 0)} tone="warning" helper="Counted lines with differences" />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Variance Value" value={`INR ${money(physicalCountArchiveRows.reduce((sum, row) => sum + row.varianceValue, 0))}`} tone="info" helper="Estimated movement value" />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Total Counted Medicines" value={physicalCountArchiveRows.reduce((sum, row) => sum + row.itemsCounted, 0)} helper="Posted session lines" />
            </Grid>
          </Grid>

          <CompactFilterCard title="Filters" subtitle="Search session, location, medicine, batch, and posting details.">
            <Grid container spacing={1.25}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField size="small" fullWidth label="Search" value={physicalCountSearchTerm} onChange={(event) => setPhysicalCountSearchTerm(event.target.value)} placeholder="Session, location, created by, medicine, or batch" />
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl size="small" fullWidth>
                  <InputLabel id="physical-count-status-filter-label">Status</InputLabel>
                  <Select labelId="physical-count-status-filter-label" label="Status" value={physicalCountStatusFilter} onChange={(event) => setPhysicalCountStatusFilter(event.target.value as "all" | "Posted")}>
                    <MenuItem value="all">All</MenuItem>
                    <MenuItem value="Posted">Posted</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl size="small" fullWidth>
                  <InputLabel id="physical-count-location-filter-label">Location</InputLabel>
                  <Select labelId="physical-count-location-filter-label" label="Location" value={physicalCountLocationFilter} onChange={(event) => setPhysicalCountLocationFilter(String(event.target.value))}>
                    <MenuItem value="all">All</MenuItem>
                    {physicalCountLocationOptions.map((location) => (
                      <MenuItem key={location} value={location}>{location}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl size="small" fullWidth>
                  <InputLabel id="physical-count-posted-by-filter-label">Posted By</InputLabel>
                  <Select labelId="physical-count-posted-by-filter-label" label="Posted By" value={physicalCountPostedByFilter} onChange={(event) => setPhysicalCountPostedByFilter(String(event.target.value))}>
                    <MenuItem value="all">All</MenuItem>
                    {physicalCountPostedByOptions.map((postedBy) => (
                      <MenuItem key={postedBy} value={postedBy}>{postedBy}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 1 }}>
                <Tooltip title="Show only records with variances.">
                  <span>
                    <Button size="small" variant={physicalCountVarianceOnly ? "contained" : "outlined"} onClick={() => setPhysicalCountVarianceOnly((current) => !current)}>
                      Variance Only
                    </Button>
                  </span>
                </Tooltip>
              </Grid>
              <Grid size={{ xs: 12, md: 1.5 }}>
                <TextField size="small" fullWidth type="date" label="From" value={physicalCountFromDate} onChange={(event) => setPhysicalCountFromDate(event.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12, md: 1.5 }}>
                <TextField size="small" fullWidth type="date" label="To" value={physicalCountToDate} onChange={(event) => setPhysicalCountToDate(event.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
            </Grid>
          </CompactFilterCard>

          <OperationalTableCard
            title="Completed Sessions"
            subtitle="Read-only archive of posted physical count sessions."
            countLabel={`${filteredPhysicalCountArchiveRows.length} posted`}
            maxVisibleRows={5}
            emptyState={physicalCountArchiveRows.length === 0 ? (
              <CompactEmptyState title="No posted physical count sessions yet." subtitle="Post a reviewed and approved count session from Inventory to create the archive." />
            ) : filteredPhysicalCountArchiveRows.length === 0 ? (
              <CompactEmptyState title="No completed sessions match these filters." subtitle="Clear one or more filters to review the posted archive." />
            ) : undefined}
          >
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Session</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell>Count Date</TableCell>
                  <TableCell align="right">Items Counted</TableCell>
                  <TableCell align="right">Variance Items</TableCell>
                  <TableCell align="right">Variance Value</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Posted By</TableCell>
                  <TableCell>Posted Date</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredPhysicalCountArchiveRows.map((row) => (
                  <TableRow key={row.id} hover selected={selectedPhysicalCountId === row.id} sx={{ "& td": { verticalAlign: "top" } }}>
                    <TableCell>
                      <Stack spacing={0.2}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.session}</Typography>
                        <Typography variant="caption" color="text.secondary">{row.scope} • {row.reason}</Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{row.location}</TableCell>
                    <TableCell>{formatDateTime(row.countDate)}</TableCell>
                    <TableCell align="right">{row.itemsCounted}</TableCell>
                    <TableCell align="right">{row.varianceItems}</TableCell>
                    <TableCell align="right">INR {money(row.varianceValue)}</TableCell>
                    <TableCell><Chip size="small" label={row.status} color="success" sx={compactChipSx} /></TableCell>
                    <TableCell>{row.postedBy}</TableCell>
                    <TableCell>{formatDateTime(row.postedDate)}</TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={() => setSelectedPhysicalCountId(row.id)}>View</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </OperationalTableCard>
        </Stack>
      ) : null}

      {tab === "stock-adjustments" ? (
        <Stack spacing={2}>
          <CompactFilterCard title="Stock Adjustment Reconciliation" subtitle="Stock adjustments are created in Inventory. This tab reviews damage, expiry, correction, and transfer adjustments before approval.">
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {["Adjustment Created", "Inventory Review", "Reconciliation Review", "Approval"].map((step) => (
                  <Chip key={step} size="small" label={step} variant="outlined" sx={compactChipSx} />
                ))}
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {["Damage", "Expiry", "Correction", "Transfer", "Lost", "Found"].map((type) => (
                  <Chip key={type} size="small" label={type} sx={compactChipSx} />
                ))}
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Stock adjustments are created in Inventory. This tab reviews damage, expiry, correction, and transfer adjustments before approval.
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button size="small" variant="contained" onClick={() => openInventory(null)}>Open Inventory Adjustments</Button>
                <Tooltip title="Adjustment drill-down will be enabled after adjustment reconciliation records are available.">
                  <span>
                    <Button size="small" variant="outlined" disabled>View Adjustment</Button>
                  </span>
                </Tooltip>
              </Stack>
            </Stack>
          </CompactFilterCard>

          <CompactFilterCard title="Adjustment Review Queue" subtitle="Read-only reconciliation queue for stock adjustment impact.">
            <TableContainer sx={{ width: "100%", maxWidth: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Adjustment Ref</TableCell>
                    <TableCell>Medicine</TableCell>
                    <TableCell>Batch</TableCell>
                    <TableCell>Location</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Quantity</TableCell>
                    <TableCell align="right">Variance Value</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {stockAdjustmentRows.length ? stockAdjustmentRows.map((row) => (
                    <TableRow key={row.adjustmentRef}>
                      <TableCell>{row.adjustmentRef}</TableCell>
                      <TableCell>{row.medicine}</TableCell>
                      <TableCell>{row.batch}</TableCell>
                      <TableCell>{row.location}</TableCell>
                      <TableCell>{row.type}</TableCell>
                      <TableCell align="right">{row.quantity}</TableCell>
                      <TableCell align="right">INR {money(row.varianceValue)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={row.status} color={queueStatusTone(row.status)} sx={compactChipSx} />
                      </TableCell>
                      <TableCell align="right">
                        <Button size="small" disabled>View Adjustment</Button>
                      </TableCell>
                    </TableRow>
                  )) : (
                    <TableRow>
                      <TableCell colSpan={9}>
                        <Typography variant="body2" color="text.secondary">No stock adjustments awaiting reconciliation.</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </CompactFilterCard>
        </Stack>
      ) : null}

      {tab === "approval-review" ? (
        <Stack spacing={2}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Supplier Bill Exceptions" value={rows.filter((row) => row.matchResult !== "Matched").length} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Physical Count Variances" value={physicalCountArchiveRows.length} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Stock Adjustment Requests" value={stockAdjustmentRows.length} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <CompactStatCard label="Ready for Approval" value={approvalQueueRows.filter((row) => row.status === "Reviewed" || row.status === "Needs Approval").length} />
            </Grid>
          </Grid>

          <CompactFilterCard title="Approval Review" subtitle="Unified review queue for reconciliation decisions.">
            <TableContainer sx={{ width: "100%", maxWidth: "100%", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Reference</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Source</TableCell>
                    <TableCell>Requested By</TableCell>
                    <TableCell>Variance</TableCell>
                    <TableCell>Risk</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {approvalQueueRows.length ? approvalQueueRows.map((row) => (
                    <TableRow key={`${row.type}-${row.reference}`}>
                      <TableCell>{row.reference}</TableCell>
                      <TableCell>{row.type}</TableCell>
                      <TableCell>{row.source}</TableCell>
                      <TableCell>{row.requestedBy}</TableCell>
                      <TableCell>{row.variance}</TableCell>
                      <TableCell>
                        <Chip size="small" label={row.risk} color={queueRiskTone(row.risk)} sx={compactChipSx} />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={row.status} color={queueStatusTone(row.status)} sx={compactChipSx} />
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.75} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                          <Button size="small" disabled={row.type !== "Supplier Bill"} onClick={() => {
                            const target = rows.find((invoiceRow) => invoiceRow.invoiceNumber === row.reference);
                            if (target) {
                              setSelectedRowId(target.id);
                              setSelectedInvoiceId(target.id);
                            }
                          }}
                          >
                            Review
                          </Button>
                          <Tooltip title="Approval workflow will be enabled after backend reconciliation actions are available.">
                            <span>
                              <Button size="small" variant="outlined" disabled>Approve</Button>
                            </span>
                          </Tooltip>
                          <Tooltip title="Rejection workflow will be enabled after backend reconciliation actions are available.">
                            <span>
                              <Button size="small" variant="outlined" disabled>Reject</Button>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  )) : (
                    <TableRow>
                      <TableCell colSpan={8}>
                        <Typography variant="body2" color="text.secondary">No reconciliation requests awaiting approval.</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </CompactFilterCard>
        </Stack>
      ) : null}

      <Drawer
        anchor="right"
        open={Boolean(selectedPhysicalCount)}
        onClose={() => setSelectedPhysicalCountId(null)}
        ModalProps={{ keepMounted: false }}
        PaperProps={{
          sx: {
            width: { xs: "100%", sm: 760, lg: 860 },
            maxWidth: "100%",
            boxSizing: "border-box",
          },
        }}
      >
        <Box sx={{ p: 2, height: "100%", overflow: "auto" }}>
          {selectedPhysicalCount ? (
            <Stack spacing={2}>
              <Stack direction="row" justifyContent="space-between" spacing={2} alignItems="flex-start">
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Physical Count Session</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedPhysicalCount.session} · {selectedPhysicalCount.location}
                  </Typography>
                </Box>
                <Button size="small" onClick={() => setSelectedPhysicalCountId(null)}>Close</Button>
              </Stack>

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip size="small" label={selectedPhysicalCount.status} color="success" sx={compactChipSx} />
                <Chip size="small" label={`${selectedPhysicalCount.itemsCounted} items`} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`${selectedPhysicalCount.varianceItems} variances`} color={selectedPhysicalCount.varianceItems ? "warning" : "default"} sx={compactChipSx} />
              </Stack>

              <CompactFilterCard title="Session Summary" subtitle="Read-only posted count session details.">
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Session" value={selectedPhysicalCount.session} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Location" value={selectedPhysicalCount.location} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Reason" value={selectedPhysicalCount.reason} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Scope" value={selectedPhysicalCount.scope} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Created By" value={selectedPhysicalCount.createdBy} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Posted By" value={selectedPhysicalCount.postedBy} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Count Date" value={formatDateTime(selectedPhysicalCount.countDate)} InputProps={{ readOnly: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField size="small" fullWidth label="Posted Date" value={formatDateTime(selectedPhysicalCount.postedDate)} InputProps={{ readOnly: true }} />
                  </Grid>
                </Grid>
              </CompactFilterCard>

              <CompactFilterCard title="Variance Summary" subtitle="Posted count variance at a glance.">
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <CompactStatCard label="Matched" value={selectedPhysicalCount.matched} tone="success" helper="No difference" />
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <CompactStatCard label="Short" value={selectedPhysicalCount.short} tone={selectedPhysicalCount.short ? "warning" : "default"} helper="Counted below system" />
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <CompactStatCard label="Excess" value={selectedPhysicalCount.excess} tone={selectedPhysicalCount.excess ? "info" : "default"} helper="Counted above system" />
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <CompactStatCard label="Variance Value" value={`INR ${money(selectedPhysicalCount.varianceValue)}`} tone={selectedPhysicalCount.varianceValue ? "warning" : "default"} helper="Estimated valuation impact" />
                  </Grid>
                </Grid>
              </CompactFilterCard>

              <OperationalTableCard
                title="Inventory Adjustments"
                subtitle="Stock changes created by the posted physical count."
                countLabel={`${selectedPhysicalCount.lines.length} rows`}
                maxVisibleRows={5}
                emptyState={selectedPhysicalCount.lines.length === 0 ? (
                  <CompactEmptyState title="No inventory adjustments recorded." subtitle="This session posted no variances." />
                ) : undefined}
              >
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell align="right">Before</TableCell>
                      <TableCell align="right">Counted</TableCell>
                      <TableCell align="right">Adjustment</TableCell>
                      <TableCell align="right">After</TableCell>
                      <TableCell>Reason</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {selectedPhysicalCount.lines.map((line) => (
                      <TableRow key={line.movementId}>
                        <TableCell>{line.medicine}</TableCell>
                        <TableCell>{line.batch}</TableCell>
                        <TableCell align="right">{line.systemQty}</TableCell>
                        <TableCell align="right">{line.countedQty}</TableCell>
                        <TableCell align="right">{line.difference > 0 ? `+${line.difference}` : line.difference}</TableCell>
                        <TableCell align="right">{line.countedQty}</TableCell>
                        <TableCell>{line.reason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </OperationalTableCard>

              <OperationalTableCard
                title="Stock Movement References"
                subtitle="Audit references for the posted physical count adjustments."
                countLabel={`${selectedPhysicalCount.lines.length} movements`}
                maxVisibleRows={5}
                emptyState={selectedPhysicalCount.lines.length === 0 ? (
                  <CompactEmptyState title="No movement references available." subtitle="Movement records are attached to posted variance rows." />
                ) : undefined}
              >
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Movement ID</TableCell>
                      <TableCell>Adjustment ID</TableCell>
                      <TableCell>Movement Type</TableCell>
                      <TableCell>Posted Timestamp</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {selectedPhysicalCount.lines.map((line) => (
                      <TableRow key={`${line.movementId}-ref`}>
                        <TableCell>{line.movementId}</TableCell>
                        <TableCell>{line.adjustmentId}</TableCell>
                        <TableCell>{line.difference < 0 ? "ADJUSTMENT_OUT" : "ADJUSTMENT_IN"}</TableCell>
                        <TableCell>{formatDateTime(line.postedTimestamp)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </OperationalTableCard>

              <CompactFilterCard title="Timeline" subtitle="Full audit timeline for the posted session.">
                <Stack spacing={1}>
                  {selectedPhysicalCount.timeline.map((timelineStep, index) => (
                    <Stack key={`${selectedPhysicalCount.id}-${timelineStep.label}`} direction="row" spacing={1.25} alignItems="stretch">
                      <Stack alignItems="center" spacing={0.35} sx={{ width: 18, flex: "0 0 auto" }}>
                        <Box
                          aria-hidden
                          sx={{
                            width: 14,
                            height: 14,
                            borderRadius: "50%",
                            bgcolor: "success.main",
                            border: "2px solid",
                            borderColor: "success.main",
                          }}
                        />
                        {index < selectedPhysicalCount.timeline.length - 1 ? (
                          <Box
                            aria-hidden
                            sx={{
                              width: 2,
                              flex: 1,
                              bgcolor: "success.light",
                            }}
                          />
                        ) : null}
                      </Stack>
                      <Stack spacing={0.25} sx={{ pb: index < selectedPhysicalCount.timeline.length - 1 ? 1 : 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{timelineStep.label}</Typography>
                        <Chip size="small" label="Completed" color="success" sx={compactChipSx} />
                      </Stack>
                    </Stack>
                  ))}
                </Stack>
              </CompactFilterCard>

              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Print</Button></span>
                </Tooltip>
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Export PDF</Button></span>
                </Tooltip>
                <Tooltip title="Available in future release.">
                  <span><Button size="small" variant="outlined" disabled>Export Excel</Button></span>
                </Tooltip>
              </Stack>
            </Stack>
          ) : null}
        </Box>
      </Drawer>
    </Stack>
  );
}
