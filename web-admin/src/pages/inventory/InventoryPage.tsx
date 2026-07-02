import * as React from "react";
import {
  Autocomplete,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
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
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import ExpandMoreRounded from "@mui/icons-material/ExpandMoreRounded";
import CameraAltRoundedIcon from "@mui/icons-material/CameraAltRounded";
import { useNavigate, useSearchParams } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, OperationalTableCard, WorkflowGuide, compactAccordionSx, compactCardContentSx, compactFormSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import CodeScannerDialog from "../../components/pharmacy/CodeScannerDialog";
import RequiredLabel from "../../components/forms/RequiredLabel.js";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import { FieldHelpTooltip } from "../../shared/components/help";
import { getFieldHelpText } from "../../shared/components/help/fieldHelpCatalog";
import {
  inventoryBatchEditSchema,
  inventoryCustomerReturnSchema,
  inventoryPhysicalCountSchema,
  inventoryTransactionFormSchema,
  inventoryVendorReturnSchema,
  inventoryWriteOffSchema,
  medicineMasterSchema,
} from "@deepthoughtnet/form-validation-kit";
import {
  createInventoryTransaction,
  createMedicine,
  getInventoryLocations,
  getInventoryTransactions,
  getLowStock,
  getMedicines,
  listPharmacyPosSales,
  getStocks,
  returnPharmacyPosSale,
  transferInventoryStock,
  updateStock,
  type InventoryLocation,
  type InventoryTransaction,
  type InventoryTransactionInput,
  type InventoryTransactionType,
  type LowStockItem,
  type Medicine,
  type MedicineInput,
  type MedicineType,
  type PharmacyPosSale,
  type Stock,
  type StockInput,
  type PaymentMode,
} from "../../api/clinicApi";

type StockFormState = {
  medicineId: string;
  locationId: string;
  batchNumber: string;
  purchaseReferenceNumber: string;
  barcode: string;
  qrCode: string;
  externalCode: string;
  expiryDate: string;
  quantityOnHand: string;
  lowStockThreshold: string;
  unitCost: string;
  sellingPrice: string;
  active: boolean;
};

type TransactionFormState = {
  medicineId: string;
  stockBatchId: string;
  transactionType: InventoryTransactionType;
  quantity: string;
  reason: string;
  referenceType: string;
  referenceId: string;
  notes: string;
};

type StockCountFormState = {
  medicineId: string;
  locationId: string;
  stockBatchId: string;
  countedQuantity: string;
  reason: string;
  remarks: string;
};

type PhysicalCountScope = "ENTIRE_INVENTORY" | "CATEGORY" | "SELECTED_MEDICINES";
type PhysicalCountReason = "MONTHLY_COUNT" | "QUARTERLY_AUDIT" | "CYCLE_COUNT" | "ANNUAL_AUDIT";
type PhysicalCountSessionStatus = "DRAFT" | "IN_PROGRESS" | "SUBMITTED" | "REVIEWED" | "APPROVED" | "POSTED" | "REJECTED";
type PhysicalCountWorkspaceMode = "continue" | "review" | "view";
type PhysicalCountLineStatus = "PENDING" | "MATCHED" | "SHORT" | "EXCESS";
type PhysicalCountTimelineState = "completed" | "current" | "pending";

type PhysicalCountReviewChecklist = {
  randomSampleVerified: boolean;
  largeVariancesInvestigated: boolean;
  batchVerificationComplete: boolean;
  supportingRemarksAdded: boolean;
};

type PhysicalCountAuditFields = {
  createdBy: string;
  createdAt: string;
  startedBy: string | null;
  startedAt: string | null;
  lastUpdatedAt: string | null;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewer: string | null;
  reviewedDate: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  approvalNotes: string;
  rejectedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string;
  returnedBy: string | null;
  returnedAt: string | null;
  returnReason: string;
  postedBy: string | null;
  postedAt: string | null;
  sessionDuration: string | null;
  generalNotes: string;
  counterNotes: string;
  reviewerNotes: string;
  auditNotes: string;
  reviewChecklist: PhysicalCountReviewChecklist;
};

type PhysicalCountSessionFormState = {
  sessionName: string;
  locationId: string;
  scope: PhysicalCountScope;
  category: string;
  selectedMedicineIds: string[];
  reason: PhysicalCountReason;
};

type PhysicalCountSessionLine = {
  id: string;
  medicineId: string;
  medicineName: string;
  batchNumber: string;
  locationId: string;
  locationName: string;
  stockBatchId: string;
  systemQty: number;
  countedQty: string;
  reason: string;
  reviewerRemarks: string;
  flagged: boolean;
  reviewed: boolean;
};

type PhysicalCountSession = {
  id: string;
  sessionName: string;
  locationId: string;
  locationName: string;
  scope: PhysicalCountScope;
  scopeLabel: string;
  reason: PhysicalCountReason;
  status: PhysicalCountSessionStatus;
  lines: PhysicalCountSessionLine[];
  audit: PhysicalCountAuditFields;
};

type FormErrorMap = Record<string, string>;

const TABS = [
  { value: "stocks", label: "Stock" },
  { value: "count", label: "Physical count" },
  { value: "expiry-report", label: "Expiry report" },
  { value: "low-stock", label: "Low stock" },
  { value: "returns", label: "Returns & write-offs" },
] as const;

const TRANSACTION_TYPES: InventoryTransactionType[] = [
  "OPENING",
  "PURCHASE",
  "SALE",
  "ADJUSTMENT",
  "RETURN",
  "CUSTOMER_RETURN_IN",
  "CUSTOMER_RETURN_NON_SELLABLE",
  "VENDOR_RETURN_OUT",
  "WRITE_OFF",
  "EXPIRED",
  "CANCELLED_DISPENSE",
  "STOCK_IN",
  "ADJUSTMENT_IN",
  "ADJUSTMENT_OUT",
];
const STOCK_ADJUSTMENT_OPTIONS: Array<{ value: InventoryTransactionType; label: string; helper: string }> = [
  { value: "OPENING", label: "Opening Balance", helper: "Use for opening stock or initial stock setup." },
  { value: "ADJUSTMENT_IN", label: "Increase", helper: "Adds stock to the selected batch." },
  { value: "ADJUSTMENT_OUT", label: "Decrease", helper: "Removes stock from the selected batch." },
  { value: "ADJUSTMENT", label: "Correction", helper: "Manual correction using current adjustment engine." },
  { value: "WRITE_OFF", label: "Damage", helper: "Reduces stock for damaged units." },
  { value: "EXPIRED", label: "Expiry", helper: "Reduces stock for expired units." },
  { value: "VENDOR_RETURN_OUT", label: "Vendor Return", helper: "Reduces stock for items sent back to supplier." },
  { value: "CUSTOMER_RETURN_IN", label: "Customer Return", helper: "Adds stock back for reusable customer returns." },
];
const MEDICINE_TYPES: MedicineType[] = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "OTHER"];

type MedicineAutocompleteOption =
  | { kind: "existing"; medicine: Medicine }
  | { kind: "create"; inputValue: string };

function emptyStockForm(): StockFormState {
  return {
    medicineId: "",
    locationId: "",
    batchNumber: "",
    purchaseReferenceNumber: "",
    barcode: "",
    qrCode: "",
    externalCode: "",
    expiryDate: "",
    quantityOnHand: "",
    lowStockThreshold: "",
    unitCost: "",
    sellingPrice: "",
    active: true,
  };
}

function emptyTransactionForm(): TransactionFormState {
  return {
    medicineId: "",
    stockBatchId: "",
    transactionType: "OPENING",
    quantity: "",
    reason: "",
    referenceType: "",
    referenceId: "",
    notes: "",
  };
}

function emptyStockCountForm(): StockCountFormState {
  return {
    medicineId: "",
    locationId: "",
    stockBatchId: "",
    countedQuantity: "",
    reason: "",
    remarks: "",
  };
}

function emptyPhysicalCountSessionForm(): PhysicalCountSessionFormState {
  return {
    sessionName: "",
    locationId: "",
    scope: "ENTIRE_INVENTORY",
    category: "",
    selectedMedicineIds: [],
    reason: "MONTHLY_COUNT",
  };
}

function emptyPhysicalCountReviewChecklist(): PhysicalCountReviewChecklist {
  return {
    randomSampleVerified: false,
    largeVariancesInvestigated: false,
    batchVerificationComplete: false,
    supportingRemarksAdded: false,
  };
}

function emptyPhysicalCountAuditFields(createdBy: string): PhysicalCountAuditFields {
  return {
    createdBy,
    createdAt: new Date().toISOString(),
    startedBy: null,
    startedAt: null,
    lastUpdatedAt: null,
    submittedBy: null,
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewer: null,
    reviewedDate: null,
    approvedBy: null,
    approvedAt: null,
    approvalNotes: "",
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: "",
    returnedBy: null,
    returnedAt: null,
    returnReason: "",
    postedBy: null,
    postedAt: null,
    sessionDuration: null,
    generalNotes: "",
    counterNotes: "",
    reviewerNotes: "",
    auditNotes: "",
    reviewChecklist: emptyPhysicalCountReviewChecklist(),
  };
}

function emptyQuickMedicineForm(): MedicineInput {
  return {
    medicineName: "",
    medicineType: "TABLET",
    barcode: null,
    qrCode: null,
    externalCode: null,
    genericName: null,
    brandName: null,
    category: null,
    dosageForm: null,
    strength: null,
    unit: null,
    manufacturer: null,
    defaultDosage: null,
    defaultFrequency: null,
    defaultDurationDays: null,
    defaultTiming: null,
    defaultInstructions: null,
    defaultPrice: null,
    taxRate: null,
    active: true,
  };
}

function stockInput(form: StockFormState): StockInput {
  return {
    medicineId: form.medicineId,
    locationId: form.locationId || null,
    barcode: form.barcode.trim() || null,
    qrCode: form.qrCode.trim() || null,
    externalCode: form.externalCode.trim() || null,
    batchNumber: form.batchNumber.trim() || null,
    purchaseReferenceNumber: form.purchaseReferenceNumber.trim() || null,
    expiryDate: form.expiryDate || null,
    purchaseDate: null,
    supplierName: null,
    quantityReceived: null,
    quantityOnHand: Number(form.quantityOnHand || "0"),
    lowStockThreshold: form.lowStockThreshold.trim() ? Number(form.lowStockThreshold) : null,
    unitCost: form.unitCost.trim() ? Number(form.unitCost) : null,
    purchasePrice: null,
    sellingPrice: form.sellingPrice.trim() ? Number(form.sellingPrice) : null,
    active: form.active,
  };
}

function normalizeInventoryText(value: string | null | undefined) {
  return (value || "").trim();
}

function parseOptionalNumberInput(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const numeric = Number(trimmed);
  return Number.isFinite(numeric) ? numeric : null;
}

function batchSetupMissingFromValues(sellingPrice: number | null | undefined, lowStockThreshold: number | null | undefined) {
  const missing: string[] = [];
  if (sellingPrice == null) missing.push("Missing MRP");
  if (lowStockThreshold == null) missing.push("Missing Reorder Level");
  return missing;
}

function batchSetupMissing(stock: Pick<Stock, "sellingPrice" | "lowStockThreshold">) {
  return batchSetupMissingFromValues(stock.sellingPrice, stock.lowStockThreshold);
}

function stockUpdateInputFromRecord(
  stock: Stock,
  overrides: Partial<Pick<StockInput, "locationId" | "barcode" | "qrCode" | "externalCode" | "batchNumber" | "purchaseReferenceNumber" | "expiryDate" | "quantityOnHand" | "lowStockThreshold" | "unitCost" | "sellingPrice" | "active">> = {},
): StockInput {
  return {
    medicineId: stock.medicineId,
    locationId: overrides.locationId ?? stock.locationId ?? null,
    barcode: overrides.barcode ?? stock.barcode ?? null,
    qrCode: overrides.qrCode ?? stock.qrCode ?? null,
    externalCode: overrides.externalCode ?? stock.externalCode ?? null,
    batchNumber: overrides.batchNumber ?? stock.batchNumber ?? null,
    purchaseReferenceNumber: overrides.purchaseReferenceNumber ?? stock.purchaseReferenceNumber ?? null,
    expiryDate: overrides.expiryDate ?? stock.expiryDate ?? null,
    purchaseDate: stock.purchaseDate ?? null,
    supplierName: stock.supplierName ?? null,
    quantityReceived: stock.quantityReceived ?? null,
    quantityOnHand: overrides.quantityOnHand ?? stock.quantityOnHand,
    lowStockThreshold: overrides.lowStockThreshold ?? stock.lowStockThreshold ?? null,
    unitCost: overrides.unitCost ?? stock.unitCost ?? null,
    purchasePrice: stock.purchasePrice ?? null,
    sellingPrice: overrides.sellingPrice ?? stock.sellingPrice ?? null,
    active: overrides.active ?? stock.active,
  };
}

function transactionInput(form: TransactionFormState): InventoryTransactionInput {
  return {
    medicineId: form.medicineId,
    stockBatchId: form.stockBatchId.trim() || null,
    transactionType: form.transactionType,
    quantity: Number(form.quantity || "0"),
    reason: form.reason.trim() || null,
    referenceType: form.referenceType.trim() || null,
    referenceId: form.referenceId.trim() || null,
    notes: [form.reason.trim() || null, form.notes.trim() || null].filter(Boolean).join(" • ") || null,
  };
}

function stockAdjustmentActionLabel(type: InventoryTransactionType) {
  return STOCK_ADJUSTMENT_OPTIONS.find((option) => option.value === type)?.label || transactionLabel(type);
}

function stockAdjustmentNextQuantity(currentQty: number, type: InventoryTransactionType, quantity: number) {
  switch (type) {
    case "ADJUSTMENT_OUT":
    case "WRITE_OFF":
    case "EXPIRED":
    case "VENDOR_RETURN_OUT":
      return currentQty - quantity;
    case "OPENING":
    case "ADJUSTMENT":
    case "ADJUSTMENT_IN":
    case "CUSTOMER_RETURN_IN":
      return currentQty + quantity;
    default:
      return currentQty + quantity;
  }
}

function stockAdjustmentIsDecrease(type: InventoryTransactionType) {
  return ["ADJUSTMENT_OUT", "WRITE_OFF", "EXPIRED", "VENDOR_RETURN_OUT"].includes(type);
}

function statusColor(quantity: number, threshold: number | null) {
  if (threshold === null) return "default";
  return quantity <= threshold ? "error" : "success";
}

function transactionLabel(type: InventoryTransactionType) {
  const labels: Record<InventoryTransactionType, string> = {
    OPENING: "Opening",
    PURCHASE: "Stock In",
    SALE: "Sale",
    ADJUSTMENT: "Adjustment",
    RETURN: "Patient Return",
    CUSTOMER_RETURN_IN: "Customer Return In",
    CUSTOMER_RETURN_NON_SELLABLE: "Customer Return Non-sellable",
    VENDOR_RETURN_OUT: "Vendor Return Out",
    WRITE_OFF: "Write-off",
    DISPENSED: "Dispensed",
    EXPIRED: "Expired",
    CANCELLED_DISPENSE: "Cancelled Dispense",
    STOCK_IN: "Stock In",
    ADJUSTMENT_IN: "Adjustment In",
    ADJUSTMENT_OUT: "Adjustment Out",
    TRANSFER_IN: "Transfer In",
    TRANSFER_OUT: "Transfer Out",
  };
  return labels[type] || type;
}

function physicalCountReasonLabel(reason: PhysicalCountReason) {
  const labels: Record<PhysicalCountReason, string> = {
    MONTHLY_COUNT: "Monthly Count",
    QUARTERLY_AUDIT: "Quarterly Audit",
    CYCLE_COUNT: "Cycle Count",
    ANNUAL_AUDIT: "Annual Audit",
  };
  return labels[reason];
}

function physicalCountScopeLabel(scope: PhysicalCountScope) {
  const labels: Record<PhysicalCountScope, string> = {
    ENTIRE_INVENTORY: "Entire Inventory",
    CATEGORY: "Category",
    SELECTED_MEDICINES: "Selected Medicines",
  };
  return labels[scope];
}

function physicalCountStatusColor(status: PhysicalCountSessionStatus) {
  switch (status) {
    case "IN_PROGRESS":
      return "warning" as const;
    case "SUBMITTED":
      return "info" as const;
    case "REVIEWED":
    case "APPROVED":
      return "success" as const;
    case "POSTED":
      return "primary" as const;
    case "REJECTED":
      return "error" as const;
    case "DRAFT":
    default:
      return "default" as const;
  }
}

function physicalCountStatusLabel(status: PhysicalCountSessionStatus) {
  switch (status) {
    case "IN_PROGRESS":
      return "In Progress";
    case "SUBMITTED":
      return "Submitted";
    case "REVIEWED":
      return "Reviewed";
    case "APPROVED":
      return "Approved";
    case "POSTED":
      return "Posted";
    case "REJECTED":
      return "Rejected";
    case "DRAFT":
    default:
      return "Draft";
  }
}

function physicalCountLineDifference(line: Pick<PhysicalCountSessionLine, "countedQty" | "systemQty">) {
  const trimmed = line.countedQty.trim();
  if (!trimmed) return null;
  const countedQty = Number(trimmed);
  if (!Number.isFinite(countedQty)) return null;
  return countedQty - line.systemQty;
}

function physicalCountLineStatus(line: Pick<PhysicalCountSessionLine, "countedQty" | "systemQty">): PhysicalCountLineStatus {
  const difference = physicalCountLineDifference(line);
  if (difference == null) return "PENDING";
  if (difference === 0) return "MATCHED";
  return difference < 0 ? "SHORT" : "EXCESS";
}

function physicalCountLineStatusColor(status: PhysicalCountLineStatus) {
  switch (status) {
    case "MATCHED":
      return "success" as const;
    case "SHORT":
      return "warning" as const;
    case "EXCESS":
      return "info" as const;
    case "PENDING":
    default:
      return "default" as const;
  }
}

function physicalCountLineStatusLabel(status: PhysicalCountLineStatus) {
  switch (status) {
    case "MATCHED":
      return "Matched";
    case "SHORT":
      return "Short";
    case "EXCESS":
      return "Excess";
    case "PENDING":
    default:
      return "Pending";
  }
}

function physicalCountCompletionPercent(lines: PhysicalCountSessionLine[]) {
  if (!lines.length) return 0;
  const counted = lines.filter((line) => physicalCountLineDifference(line) != null).length;
  return Math.round((counted / lines.length) * 100);
}

function physicalCountLargeVarianceCount(lines: PhysicalCountSessionLine[]) {
  return lines.filter((line) => {
    const difference = physicalCountLineDifference(line);
    return difference != null && Math.abs(difference) >= 10;
  }).length;
}

function physicalCountDurationLabel(startedAt: string | null, endAt: string | null) {
  if (!startedAt || !endAt) return null;
  const durationMs = new Date(endAt).getTime() - new Date(startedAt).getTime();
  if (!Number.isFinite(durationMs) || durationMs <= 0) return null;
  const minutes = Math.round(durationMs / (1000 * 60));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
}

function physicalCountRecommendation(status: PhysicalCountSessionStatus) {
  switch (status) {
    case "DRAFT":
      return "Start counting to capture physical quantities.";
    case "IN_PROGRESS":
      return "Complete pending items before submitting.";
    case "SUBMITTED":
      return "Review variances and mark the session reviewed.";
    case "REVIEWED":
      return "Reviewed and ready for approval.";
    case "APPROVED":
      return "Approved and ready to post stock adjustments.";
    case "POSTED":
      return "Posted to inventory and audit trail completed.";
    case "REJECTED":
      return "Rejected. Review the reason before creating a new session.";
    default:
      return "";
  }
}

function physicalCountTimeline(session: PhysicalCountSession): Array<{ label: string; state: PhysicalCountTimelineState; at: string | null }> {
  const steps = [
    { label: "Created", at: session.audit.createdAt },
    { label: "Counting Started", at: session.audit.startedAt },
    { label: "Last Saved", at: session.audit.lastUpdatedAt },
    { label: "Submitted", at: session.audit.submittedAt },
    { label: "Reviewed", at: session.audit.reviewedAt },
    { label: "Approved", at: session.audit.approvedAt },
    { label: "Returned for Recount", at: session.audit.returnedAt },
    { label: "Rejected", at: session.audit.rejectedAt },
    { label: "Posted", at: session.audit.postedAt },
  ];
  const currentLabel = session.status === "DRAFT"
    ? "Created"
    : session.status === "IN_PROGRESS"
      ? (session.audit.returnedAt ? "Returned for Recount" : (session.audit.lastUpdatedAt ? "Last Saved" : "Counting Started"))
      : session.status === "SUBMITTED"
        ? "Submitted"
        : session.status === "REVIEWED"
          ? "Reviewed"
          : session.status === "APPROVED"
            ? "Approved"
            : session.status === "REJECTED"
              ? "Rejected"
              : "Posted";
  return steps.map((step, index) => ({
    label: step.label,
    at: step.at,
    state: step.at
      ? "completed"
      : step.label === currentLabel
        ? "current"
        : "pending",
  }));
}

function formatInventoryDateTime(value: string) {
  return new Date(value).toLocaleString();
}

function buildPhysicalCountSessionLines(
  stocks: Stock[],
  medicines: Medicine[],
  locations: InventoryLocation[],
  form: PhysicalCountSessionFormState,
) {
  const categoriesByMedicineId = new Map(medicines.map((medicine) => [medicine.id, normalizeInventoryText(medicine.category).toLowerCase()]));
  return stocks
    .filter((stock) => stock.locationId === form.locationId)
    .filter((stock) => {
      if (form.scope === "ENTIRE_INVENTORY") return true;
      if (form.scope === "CATEGORY") {
        return categoriesByMedicineId.get(stock.medicineId) === normalizeInventoryText(form.category).toLowerCase();
      }
      if (form.scope === "SELECTED_MEDICINES") {
        return form.selectedMedicineIds.includes(stock.medicineId);
      }
      return true;
    })
    .map((stock) => ({
      id: `line-${stock.id}`,
      medicineId: stock.medicineId,
      medicineName: stock.medicineName,
      batchNumber: stock.batchNumber || "No batch",
      locationId: stock.locationId || "",
      locationName: stock.locationName || locations.find((location) => location.id === stock.locationId)?.locationName || "Main Pharmacy",
      stockBatchId: stock.id,
      systemQty: stock.quantityOnHand,
      countedQty: "",
      reason: "",
      reviewerRemarks: "",
      flagged: false,
      reviewed: false,
    }));
}

function buildDemoPhysicalCountSessions(
  stocks: Stock[],
  medicines: Medicine[],
  locations: InventoryLocation[],
  createdBy: string,
) {
  if (!stocks.length || !locations.length) {
    return [] as PhysicalCountSession[];
  }
  const defaultLocation = locations.find((location) => location.defaultLocation) || locations[0];
  const secondaryLocation = locations.find((location) => location.id !== defaultLocation.id) || defaultLocation;

  const monthlyLines = buildPhysicalCountSessionLines(stocks, medicines, locations, {
    sessionName: "Monthly Main Pharmacy Count",
    locationId: defaultLocation.id,
    scope: "ENTIRE_INVENTORY",
    category: "",
    selectedMedicineIds: [],
    reason: "MONTHLY_COUNT",
  }).slice(0, 6).map((line, index) => ({
    ...line,
    countedQty: index % 3 === 0 ? String(line.systemQty) : index % 3 === 1 ? String(Math.max(0, line.systemQty - 2)) : String(line.systemQty + 1),
    reason: index % 3 === 0 ? "" : index % 3 === 1 ? "Shelf shortage" : "Additional loose units found",
  }));

  const cycleLines = buildPhysicalCountSessionLines(stocks, medicines, locations, {
    sessionName: "Cycle Count Fast Movers",
    locationId: secondaryLocation.id,
    scope: "ENTIRE_INVENTORY",
    category: "",
    selectedMedicineIds: [],
    reason: "CYCLE_COUNT",
  }).slice(0, 5).map((line, index) => ({
    ...line,
    countedQty: index < 2 ? String(line.systemQty) : "",
    reason: index < 2 ? "Verified on rack" : "",
  }));

  const submittedLines = monthlyLines.slice(0, Math.min(monthlyLines.length, 4)).map((line, index) => ({
    ...line,
    countedQty: index % 2 === 0 ? String(line.systemQty) : String(Math.max(0, line.systemQty - 1)),
    reason: index % 2 === 0 ? "Checked" : "Damaged pack on shelf",
  }));

  const sessions: PhysicalCountSession[] = [
    {
      id: "physical-count-demo-draft",
      sessionName: "Annual Audit Preparation",
      locationId: defaultLocation.id,
      locationName: defaultLocation.locationName,
      scope: "ENTIRE_INVENTORY",
      scopeLabel: physicalCountScopeLabel("ENTIRE_INVENTORY"),
      reason: "ANNUAL_AUDIT",
      status: "DRAFT",
      lines: buildPhysicalCountSessionLines(stocks, medicines, locations, {
        sessionName: "Annual Audit Preparation",
        locationId: defaultLocation.id,
        scope: "ENTIRE_INVENTORY",
        category: "",
        selectedMedicineIds: [],
        reason: "ANNUAL_AUDIT",
      }).slice(0, 4),
      audit: {
        ...emptyPhysicalCountAuditFields(createdBy),
        createdAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 2)).toISOString(),
        generalNotes: "Annual count scope drafted for audit preparation.",
      },
    },
    {
      id: "physical-count-demo-progress",
      sessionName: "Cycle Count Fast Movers",
      locationId: secondaryLocation.id,
      locationName: secondaryLocation.locationName,
      scope: "ENTIRE_INVENTORY",
      scopeLabel: physicalCountScopeLabel("ENTIRE_INVENTORY"),
      reason: "CYCLE_COUNT",
      status: "IN_PROGRESS",
      lines: cycleLines,
      audit: {
        ...emptyPhysicalCountAuditFields(createdBy),
        createdAt: new Date(Date.now() - (1000 * 60 * 60 * 10)).toISOString(),
        startedBy: createdBy,
        startedAt: new Date(Date.now() - (1000 * 60 * 60 * 9)).toISOString(),
        lastUpdatedAt: new Date(Date.now() - (1000 * 60 * 30)).toISOString(),
        sessionDuration: "8h 30m",
        counterNotes: "Fast-moving items started by afternoon shift.",
      },
    },
    {
      id: "physical-count-demo-submitted",
      sessionName: "Monthly Main Pharmacy Count",
      locationId: defaultLocation.id,
      locationName: defaultLocation.locationName,
      scope: "ENTIRE_INVENTORY",
      scopeLabel: physicalCountScopeLabel("ENTIRE_INVENTORY"),
      reason: "MONTHLY_COUNT",
      status: "SUBMITTED",
      lines: submittedLines.map((line, index) => ({
        ...line,
        reviewerRemarks: index % 2 === 0 ? "Variance explanation sufficient." : "",
        flagged: index % 2 === 1,
        reviewed: false,
      })),
      audit: {
        ...emptyPhysicalCountAuditFields(createdBy),
        createdAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 12)).toISOString(),
        startedBy: createdBy,
        startedAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 11)).toISOString(),
        lastUpdatedAt: new Date(Date.now() - (1000 * 60 * 60 * 24)).toISOString(),
        submittedBy: createdBy,
        submittedAt: new Date(Date.now() - (1000 * 60 * 60 * 22)).toISOString(),
        sessionDuration: "13h",
        generalNotes: "Monthly count ready for review.",
        counterNotes: "Variance notes added against damaged shelf stock.",
      },
    },
    {
      id: "physical-count-demo-reviewed",
      sessionName: "Quarterly Audit OTC Shelf",
      locationId: defaultLocation.id,
      locationName: defaultLocation.locationName,
      scope: "ENTIRE_INVENTORY",
      scopeLabel: physicalCountScopeLabel("ENTIRE_INVENTORY"),
      reason: "QUARTERLY_AUDIT",
      status: "REVIEWED",
      lines: monthlyLines.slice(0, 3).map((line, index) => ({
        ...line,
        countedQty: index === 0 ? String(line.systemQty) : String(Math.max(0, line.systemQty - 1)),
        reason: index === 0 ? "Matched" : "Pack opened",
        reviewerRemarks: "Reviewed against shelf note.",
        flagged: index === 1,
        reviewed: true,
      })),
      audit: {
        ...emptyPhysicalCountAuditFields(createdBy),
        createdAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 18)).toISOString(),
        startedBy: createdBy,
        startedAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 17)).toISOString(),
        lastUpdatedAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 16)).toISOString(),
        submittedBy: createdBy,
        submittedAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 15)).toISOString(),
        reviewedBy: createdBy,
        reviewedAt: new Date(Date.now() - (1000 * 60 * 60 * 24 * 14)).toISOString(),
        reviewer: createdBy,
        reviewedDate: new Date(Date.now() - (1000 * 60 * 60 * 24 * 14)).toISOString(),
        sessionDuration: "3h",
        reviewerNotes: "Variance sample reviewed and accepted for next phase.",
        auditNotes: "Ready for approval workflow in Batch 3.",
        reviewChecklist: {
          randomSampleVerified: true,
          largeVariancesInvestigated: true,
          batchVerificationComplete: true,
          supportingRemarksAdded: true,
        },
      },
    },
  ];
  return sessions.filter((session) => session.lines.length > 0);
}

function utcDayNumber(dateValue: string) {
  const [year, month, day] = dateValue.split("-").map((value) => Number(value));
  return Date.UTC(year, month - 1, day);
}

function todayUtcDayNumber() {
  const now = new Date();
  return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
}

function daysUntil(dateValue: string | null) {
  if (!dateValue) return Number.POSITIVE_INFINITY;
  return Math.floor((utcDayNumber(dateValue) - todayUtcDayNumber()) / (1000 * 60 * 60 * 24));
}

function expiryState(expiryDate: string | null) {
  if (!expiryDate) {
    return { label: "No expiry", color: "default" as const, bucket: "No expiry" as const };
  }
  const diffDays = daysUntil(expiryDate);
  if (diffDays < 0) return { label: "EXPIRED", color: "error" as const, bucket: "Expired" as const };
  if (diffDays <= 30) return { label: "0-30 days", color: "warning" as const, bucket: "0-30" as const };
  if (diffDays <= 60) return { label: "31-60 days", color: "info" as const, bucket: "31-60" as const };
  if (diffDays <= 90) return { label: "61-90 days", color: "secondary" as const, bucket: "61-90" as const };
  return { label: "91+ days", color: "success" as const, bucket: "91+" as const };
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
}

function zodFieldErrors(error: { issues?: Array<{ path?: ReadonlyArray<unknown>; message: string }> } | null | undefined) {
  return (error?.issues || []).reduce<FormErrorMap>((acc, issue) => {
    const key = String(issue.path?.[0] ?? "summary");
    if (!acc[key]) {
      acc[key] = issue.message;
    }
    return acc;
  }, {});
}

function focusFirstInventoryField(errorMap: FormErrorMap, idMap: Record<string, string>) {
  const firstKey = Object.keys(errorMap)[0];
  if (!firstKey) return;
  const targetId = idMap[firstKey];
  if (!targetId || typeof document === "undefined") return;
  const target = document.getElementById(targetId) as HTMLElement | null;
  target?.focus?.();
}

export default function InventoryPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const canManageInventory = auth.hasPermission("inventory.manage") || auth.rolesUpper.includes("CLINIC_ADMIN");
  const [tab, setTab] = React.useState<(typeof TABS)[number]["value"]>("stocks");
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [transactions, setTransactions] = React.useState<InventoryTransaction[]>([]);
  const [lowStock, setLowStock] = React.useState<LowStockItem[]>([]);
  const [locations, setLocations] = React.useState<InventoryLocation[]>([]);
  const [stockForm, setStockForm] = React.useState<StockFormState>(emptyStockForm());
  const [transactionForm, setTransactionForm] = React.useState<TransactionFormState>(emptyTransactionForm());
  const [transactionSearchInput, setTransactionSearchInput] = React.useState("");
  const [selectedStockId, setSelectedStockId] = React.useState<string | null>(null);
  const [selectedLocationId, setSelectedLocationId] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [deepLinkBanner, setDeepLinkBanner] = React.useState<string | null>(null);
  const [stockSearch, setStockSearch] = React.useState("");
  const [stockSearchScannerOpen, setStockSearchScannerOpen] = React.useState(false);
  const [transferForm, setTransferForm] = React.useState({ medicineId: "", stockBatchId: "", fromLocationId: "", toLocationId: "", quantity: "", reason: "" });
  const [stockCountForm, setStockCountForm] = React.useState<StockCountFormState>(emptyStockCountForm());
  const [expiryReportMedicineId, setExpiryReportMedicineId] = React.useState("");
  const [stockActionPanel, setStockActionPanel] = React.useState<"add" | "transaction" | "transfer" | null>(null);
  const [medicineSearchInput, setMedicineSearchInput] = React.useState("");
  const [quickMedicineOpen, setQuickMedicineOpen] = React.useState(false);
  const [quickMedicineForm, setQuickMedicineForm] = React.useState<MedicineInput>(emptyQuickMedicineForm());
  const [physicalCountSessions, setPhysicalCountSessions] = React.useState<PhysicalCountSession[]>([]);
  const [physicalCountForm, setPhysicalCountForm] = React.useState<PhysicalCountSessionFormState>(emptyPhysicalCountSessionForm());
  const [physicalCountWorkspaceSessionId, setPhysicalCountWorkspaceSessionId] = React.useState<string | null>(null);
  const [physicalCountWorkspaceMode, setPhysicalCountWorkspaceMode] = React.useState<PhysicalCountWorkspaceMode>("continue");
  const [physicalCountWorkspaceLineId, setPhysicalCountWorkspaceLineId] = React.useState<string | null>(null);
  const [physicalCountSearch, setPhysicalCountSearch] = React.useState("");
  const [physicalCountBatchSearch, setPhysicalCountBatchSearch] = React.useState("");
  const [physicalCountCreateOpen, setPhysicalCountCreateOpen] = React.useState(true);
  const [physicalCountQuickOpen, setPhysicalCountQuickOpen] = React.useState(true);
  const [physicalCountDrawerSessionId, setPhysicalCountDrawerSessionId] = React.useState<string | null>(null);
  const [physicalCountDrawerMode, setPhysicalCountDrawerMode] = React.useState<"view" | "review">("view");
  const [sales, setSales] = React.useState<PharmacyPosSale[]>([]);
  const [saleSearch, setSaleSearch] = React.useState("");
  const [customerReturnSaleId, setCustomerReturnSaleId] = React.useState("");
  const [customerReturnLineId, setCustomerReturnLineId] = React.useState("");
  const [customerReturnQuantity, setCustomerReturnQuantity] = React.useState("1");
  const [customerReturnReusable, setCustomerReturnReusable] = React.useState(true);
  const [customerReturnReason, setCustomerReturnReason] = React.useState("");
  const [customerReturnNotes, setCustomerReturnNotes] = React.useState("");
  const [customerReturnMode, setCustomerReturnMode] = React.useState<PaymentMode>("CASH");
  const [customerReturnReference, setCustomerReturnReference] = React.useState("");
  const [vendorReturnForm, setVendorReturnForm] = React.useState({ medicineId: "", stockBatchId: "", quantity: "", supplierReference: "", reason: "", notes: "" });
  const [writeOffForm, setWriteOffForm] = React.useState({ medicineId: "", stockBatchId: "", quantity: "", reason: "", notes: "" });
  const [stockFieldErrors, setStockFieldErrors] = React.useState<FormErrorMap>({});
  const [transactionFieldErrors, setTransactionFieldErrors] = React.useState<FormErrorMap>({});
  const [countFieldErrors, setCountFieldErrors] = React.useState<FormErrorMap>({});
  const [customerReturnFieldErrors, setCustomerReturnFieldErrors] = React.useState<FormErrorMap>({});
  const [vendorReturnFieldErrors, setVendorReturnFieldErrors] = React.useState<FormErrorMap>({});
  const [writeOffFieldErrors, setWriteOffFieldErrors] = React.useState<FormErrorMap>({});
  const [quickMedicineFieldErrors, setQuickMedicineFieldErrors] = React.useState<FormErrorMap>({});
  const [setupQueueEdits, setSetupQueueEdits] = React.useState<Record<string, { sellingPrice: string; lowStockThreshold: string }>>({});
  const addStockBatchRef = React.useRef<HTMLDivElement | null>(null);
  const handledReferenceRef = React.useRef("");

  const medicineById = React.useMemo(() => new Map(medicines.map((medicine) => [medicine.id, medicine])), [medicines]);
  const inventoryActorLabel = auth.username || auth.appUserId || "Inventory User";
  const medicineCategories = React.useMemo(
    () => Array.from(new Set(medicines.map((medicine) => normalizeInventoryText(medicine.category)).filter(Boolean))).sort((left, right) => left.localeCompare(right)),
    [medicines],
  );
  const medicineAutocompleteOptions = React.useMemo<MedicineAutocompleteOption[]>(
    () => medicines.map((medicine) => ({ kind: "existing", medicine })),
    [medicines],
  );
  const selectedMedicineOption = React.useMemo<MedicineAutocompleteOption | null>(
    () => medicineAutocompleteOptions.find((option) => option.kind === "existing" && option.medicine.id === stockForm.medicineId) ?? null,
    [medicineAutocompleteOptions, stockForm.medicineId],
  );
  const visibleStocks = React.useMemo(() => {
    const term = stockSearch.trim().toLowerCase();
    return stocks.filter((stock) => {
      const matchesLocation = !selectedLocationId || stock.locationId === selectedLocationId;
      const matchesTerm = !term || [stock.medicineName, stock.batchNumber, stock.purchaseReferenceNumber, stock.barcode, stock.qrCode, stock.externalCode, stock.supplierName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
      return matchesLocation && matchesTerm;
    });
  }, [stocks, stockSearch, selectedLocationId]);
  const incompleteSetupStocks = React.useMemo(
    () => visibleStocks.filter((stock) => batchSetupMissing(stock).length > 0),
    [visibleStocks],
  );
  const expiringSoonCount = React.useMemo(() => stocks.filter((stock) => {
    const diff = daysUntil(stock.expiryDate);
    return diff >= 0 && diff <= 30;
  }).length, [stocks]);
  const countableStocks = React.useMemo(() => stocks.filter((stock) => {
    const matchesMedicine = !stockCountForm.medicineId || stock.medicineId === stockCountForm.medicineId;
    const matchesLocation = !stockCountForm.locationId || stock.locationId === stockCountForm.locationId;
    return matchesMedicine && matchesLocation;
  }), [stockCountForm.locationId, stockCountForm.medicineId, stocks]);
  const selectedCountStock = React.useMemo(
    () => stocks.find((stock) => stock.id === stockCountForm.stockBatchId) || null,
    [stockCountForm.stockBatchId, stocks],
  );
  const countVariance = React.useMemo(() => {
    if (!selectedCountStock || !stockCountForm.countedQuantity.trim()) {
      return null;
    }
    const countedQuantityValue = Number(stockCountForm.countedQuantity);
    if (Number.isNaN(countedQuantityValue)) {
      return null;
    }
    return countedQuantityValue - selectedCountStock.quantityOnHand;
  }, [selectedCountStock, stockCountForm.countedQuantity]);
  const physicalCountWorkspaceSession = React.useMemo(
    () => physicalCountSessions.find((session) => session.id === physicalCountWorkspaceSessionId) || null,
    [physicalCountSessions, physicalCountWorkspaceSessionId],
  );
  const physicalCountDrawerSession = React.useMemo(
    () => physicalCountSessions.find((session) => session.id === physicalCountDrawerSessionId) || null,
    [physicalCountDrawerSessionId, physicalCountSessions],
  );
  const physicalCountWorkspaceLines = React.useMemo(() => {
    if (!physicalCountWorkspaceSession) return [] as PhysicalCountSessionLine[];
    const medicineTerm = physicalCountSearch.trim().toLowerCase();
    const batchTerm = physicalCountBatchSearch.trim().toLowerCase();
    return physicalCountWorkspaceSession.lines.filter((line) => {
      const matchesMedicine = !medicineTerm || line.medicineName.toLowerCase().includes(medicineTerm);
      const matchesBatch = !batchTerm || line.batchNumber.toLowerCase().includes(batchTerm);
      return matchesMedicine && matchesBatch;
    });
  }, [physicalCountBatchSearch, physicalCountSearch, physicalCountWorkspaceSession]);
  const physicalCountWorkspaceLineIndex = React.useMemo(
    () => physicalCountWorkspaceLines.findIndex((line) => line.id === physicalCountWorkspaceLineId),
    [physicalCountWorkspaceLineId, physicalCountWorkspaceLines],
  );
  const physicalCountWorkspaceLine = React.useMemo(
    () => physicalCountWorkspaceLines.find((line) => line.id === physicalCountWorkspaceLineId) || physicalCountWorkspaceLines[0] || null,
    [physicalCountWorkspaceLineId, physicalCountWorkspaceLines],
  );
  const physicalCountDashboard = React.useMemo(() => {
    return {
      draft: physicalCountSessions.filter((session) => session.status === "DRAFT").length,
      inProgress: physicalCountSessions.filter((session) => session.status === "IN_PROGRESS").length,
      submitted: physicalCountSessions.filter((session) => session.status === "SUBMITTED").length,
      reviewed: physicalCountSessions.filter((session) => session.status === "REVIEWED").length,
      largeVariances: physicalCountSessions.reduce((sum, session) => sum + physicalCountLargeVarianceCount(session.lines), 0),
    };
  }, [physicalCountSessions]);
  const physicalCountInventorySummary = React.useMemo(() => {
    const postedSessions = physicalCountSessions.filter((session) => session.status === "POSTED");
    const pendingSessions = physicalCountSessions.filter((session) => session.status !== "POSTED" && session.status !== "REJECTED");
    const latestPostedSession = [...postedSessions].sort((left, right) => {
      const leftDate = left.audit.postedAt || left.audit.reviewedAt || left.audit.submittedAt || left.audit.lastUpdatedAt || left.audit.createdAt;
      const rightDate = right.audit.postedAt || right.audit.reviewedAt || right.audit.submittedAt || right.audit.lastUpdatedAt || right.audit.createdAt;
      return rightDate.localeCompare(leftDate);
    })[0] || null;
    const latestAdjustmentTransaction = [...transactions]
      .filter((transaction) => transaction.referenceType === "PHYSICAL_COUNT" || transaction.notes?.includes("Source: PHYSICAL_COUNT"))
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0] || null;
    const now = new Date();
    const postedThisMonth = postedSessions.filter((session) => {
      const postedAt = session.audit.postedAt || session.audit.reviewedAt || session.audit.lastUpdatedAt || session.audit.createdAt;
      const postedDate = new Date(postedAt);
      return postedDate.getUTCFullYear() === now.getUTCFullYear() && postedDate.getUTCMonth() === now.getUTCMonth();
    }).length;
    const varianceAdjusted = postedSessions.reduce((sum, session) => sum + session.lines.reduce((lineSum, line) => {
      const difference = physicalCountLineDifference(line);
      return lineSum + Math.abs(difference ?? 0);
    }, 0), 0);
    const totalCountedMedicines = postedSessions.reduce((sum, session) => sum + session.lines.filter((line) => physicalCountLineDifference(line) != null).length, 0);
    return {
      lastPhysicalCount: latestPostedSession ? (latestPostedSession.audit.postedAt || latestPostedSession.audit.reviewedAt || latestPostedSession.audit.createdAt) : null,
      pendingCountSessions: pendingSessions.length,
      postedThisMonth,
      varianceAdjusted,
      lastAdjustmentDate: latestAdjustmentTransaction?.createdAt || latestPostedSession?.audit.postedAt || null,
      sessionsCreated: physicalCountSessions.length,
      sessionsPosted: postedSessions.length,
      varianceItems: postedSessions.reduce((sum, session) => sum + physicalCountLargeVarianceCount(session.lines), 0),
      totalCountedMedicines,
    };
  }, [physicalCountSessions, transactions]);
  const physicalCountDrawerSummary = React.useMemo(() => {
    if (!physicalCountDrawerSession) {
      return { totalMedicines: 0, matched: 0, variance: 0, missingQty: 0, excessQty: 0, counted: 0, pending: 0, largeVariance: 0, completionPercent: 0, short: 0, excess: 0 };
    }
    return physicalCountDrawerSession.lines.reduce((acc, line) => {
      const difference = physicalCountLineDifference(line);
      acc.totalMedicines += 1;
      if (difference == null) {
        acc.pending += 1;
        return acc;
      }
      acc.counted += 1;
      if (difference === 0) {
        acc.matched += 1;
      } else {
        acc.variance += 1;
        if (difference < 0) {
          acc.short += 1;
          acc.missingQty += Math.abs(difference);
        }
        if (difference > 0) {
          acc.excess += 1;
          acc.excessQty += difference;
        }
        if (Math.abs(difference) >= 10) {
          acc.largeVariance += 1;
        }
      }
      return acc;
    }, {
      totalMedicines: 0,
      matched: 0,
      variance: 0,
      missingQty: 0,
      excessQty: 0,
      counted: 0,
      pending: 0,
      largeVariance: 0,
      completionPercent: physicalCountCompletionPercent(physicalCountDrawerSession.lines),
      short: 0,
      excess: 0,
    });
  }, [physicalCountDrawerSession]);
  const physicalCountSessionSummary = React.useMemo(() => {
    if (!physicalCountWorkspaceSession) {
      return { totalMedicines: 0, matched: 0, variance: 0, missingQty: 0, excessQty: 0, counted: 0, pending: 0 };
    }
    return physicalCountWorkspaceSession.lines.reduce((acc, line) => {
      const difference = physicalCountLineDifference(line);
      acc.totalMedicines += 1;
      if (difference == null) {
        acc.pending += 1;
        return acc;
      }
      acc.counted += 1;
      if (difference === 0) {
        acc.matched += 1;
      } else {
        acc.variance += 1;
        if (difference < 0) acc.missingQty += Math.abs(difference);
        if (difference > 0) acc.excessQty += difference;
      }
      return acc;
    }, { totalMedicines: 0, matched: 0, variance: 0, missingQty: 0, excessQty: 0, counted: 0, pending: 0 });
  }, [physicalCountWorkspaceSession]);
  const expiryReportRows = React.useMemo(() => {
    const term = expiryReportMedicineId.trim().toLowerCase();
    return stocks
      .filter((stock) => !!stock.expiryDate)
      .filter((stock) => !selectedLocationId || stock.locationId === selectedLocationId)
      .filter((stock) => !term || [stock.medicineName, stock.batchNumber, stock.purchaseReferenceNumber, stock.locationName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)))
      .map((stock) => {
        const state = expiryState(stock.expiryDate);
        return {
          ...stock,
          expiryBucket: state.bucket,
          expiryLabel: state.label,
          expiryColor: state.color,
          expiryDays: daysUntil(stock.expiryDate),
        };
      })
      .sort((a, b) => a.expiryDays - b.expiryDays || a.medicineName.localeCompare(b.medicineName));
  }, [expiryReportMedicineId, selectedLocationId, stocks]);
  const expiryBucketCounts = React.useMemo(() => expiryReportRows.reduce((acc, row) => {
    acc[row.expiryBucket] = (acc[row.expiryBucket] || 0) + 1;
    return acc;
  }, { "Expired": 0, "0-30": 0, "31-60": 0, "61-90": 0, "91+": 0 } as Record<string, number>), [expiryReportRows]);
  const totalQuantity = React.useMemo(() => stocks.reduce((sum, stock) => sum + stock.quantityOnHand, 0), [stocks]);
  const estimatedStockValue = React.useMemo(() => stocks.reduce((sum, stock) => {
    const rate = stock.sellingPrice ?? stock.unitCost ?? 0;
    return sum + (stock.quantityOnHand * rate);
  }, 0), [stocks]);
  const todayMovementCount = React.useMemo(() => {
    const todayKey = new Date().toISOString().slice(0, 10);
    return transactions.filter((transaction) => transaction.createdAt.slice(0, 10) === todayKey).length;
  }, [transactions]);
  const paidLocationsLabel = React.useMemo(
    () => locations.find((location) => location.id === selectedLocationId)?.locationName || "All locations",
    [locations, selectedLocationId],
  );
  const filteredTransactionStocks = React.useMemo(
    () => stocks.filter((stock) => !transactionForm.medicineId || stock.medicineId === transactionForm.medicineId),
    [stocks, transactionForm.medicineId],
  );
  const transactionSearchResults = React.useMemo(() => {
    const term = transactionSearchInput.trim().toLowerCase();
    if (term.length < 2) return [] as Stock[];
    return stocks.filter((stock) => {
      const medicine = medicineById.get(stock.medicineId);
      return [
        stock.medicineName,
        medicine?.genericName,
        stock.batchNumber,
        stock.barcode,
        stock.qrCode,
        stock.externalCode,
        stock.purchaseReferenceNumber,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    }).slice(0, 15);
  }, [medicineById, stocks, transactionSearchInput]);
  const selectedTransactionStock = React.useMemo(
    () => stocks.find((stock) => stock.id === transactionForm.stockBatchId) ?? null,
    [stocks, transactionForm.stockBatchId],
  );
  const transactionBatchOptions = React.useMemo(() => {
    if (selectedTransactionStock) {
      return stocks.filter((stock) => stock.medicineId === selectedTransactionStock.medicineId);
    }
    if (transactionForm.medicineId) {
      return stocks.filter((stock) => stock.medicineId === transactionForm.medicineId);
    }
    return [] as Stock[];
  }, [selectedTransactionStock, stocks, transactionForm.medicineId]);
  const selectedTransactionMedicine = React.useMemo(
    () => medicineById.get(selectedTransactionStock?.medicineId || transactionForm.medicineId || "") ?? null,
    [medicineById, selectedTransactionStock?.medicineId, transactionForm.medicineId],
  );
  const transactionSetupMissing = React.useMemo(
    () => selectedTransactionStock ? batchSetupMissing(selectedTransactionStock) : [],
    [selectedTransactionStock],
  );
  const transactionAdjustmentQuantity = React.useMemo(() => Number(transactionForm.quantity || "0"), [transactionForm.quantity]);
  const transactionAdjustmentNextQty = React.useMemo(() => {
    if (!selectedTransactionStock || !Number.isFinite(transactionAdjustmentQuantity) || transactionAdjustmentQuantity <= 0) return null;
    return stockAdjustmentNextQuantity(selectedTransactionStock.quantityOnHand, transactionForm.transactionType, transactionAdjustmentQuantity);
  }, [selectedTransactionStock, transactionAdjustmentQuantity, transactionForm.transactionType]);
  const filteredTransferStocks = React.useMemo(
    () => stocks.filter((stock) => !transferForm.medicineId || stock.medicineId === transferForm.medicineId),
    [stocks, transferForm.medicineId],
  );
  const filteredCustomerSales = React.useMemo(() => {
    const term = saleSearch.trim().toLowerCase();
    if (!term) {
      return sales;
    }
    return sales.filter((sale) =>
      [sale.saleNumber, sale.patientName, sale.customerName, sale.customerMobile, sale.items.map((item) => item.medicineName).join(" ")]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [saleSearch, sales]);
  const selectedCustomerSale = React.useMemo(
    () => sales.find((sale) => sale.id === customerReturnSaleId) ?? null,
    [customerReturnSaleId, sales],
  );
  const selectedCustomerReturnItem = React.useMemo(
    () => selectedCustomerSale?.items.find((item) => item.id === customerReturnLineId) ?? null,
    [customerReturnLineId, selectedCustomerSale],
  );
  const customerReturnHistory = React.useMemo(
    () => transactions.filter((row) => ["RETURN", "CUSTOMER_RETURN_IN", "CUSTOMER_RETURN_NON_SELLABLE"].includes(row.transactionType)),
    [transactions],
  );
  const vendorReturnHistory = React.useMemo(
    () => transactions.filter((row) => row.transactionType === "VENDOR_RETURN_OUT"),
    [transactions],
  );
  const writeOffHistory = React.useMemo(
    () => transactions.filter((row) => row.transactionType === "WRITE_OFF"),
    [transactions],
  );
  const vendorReturnBatches = React.useMemo(
    () => stocks.filter((stock) => !vendorReturnForm.medicineId || stock.medicineId === vendorReturnForm.medicineId),
    [stocks, vendorReturnForm.medicineId],
  );
  const writeOffBatches = React.useMemo(
    () => stocks.filter((stock) => !writeOffForm.medicineId || stock.medicineId === writeOffForm.medicineId),
    [stocks, writeOffForm.medicineId],
  );

  React.useEffect(() => {
    if (selectedCountStock) {
      setStockCountForm((current) => ({
        ...current,
        medicineId: selectedCountStock.medicineId,
        locationId: selectedCountStock.locationId || "",
      }));
    }
  }, [selectedCountStock]);

  React.useEffect(() => {
    setPhysicalCountForm((current) => {
      if (current.locationId || !locations.length) return current;
      return {
        ...current,
        locationId: selectedLocationId || locations.find((location) => location.defaultLocation)?.id || locations[0]?.id || "",
      };
    });
  }, [locations, selectedLocationId]);

  React.useEffect(() => {
    if (!physicalCountSessions.length && stocks.length && locations.length) {
      setPhysicalCountSessions(buildDemoPhysicalCountSessions(stocks, medicines, locations, inventoryActorLabel));
    }
  }, [inventoryActorLabel, locations, medicines, physicalCountSessions.length, stocks]);

  React.useEffect(() => {
    setSetupQueueEdits((current) => {
      const next = { ...current };
      let changed = false;
      for (const stock of incompleteSetupStocks) {
        if (!next[stock.id]) {
          next[stock.id] = {
            sellingPrice: stock.sellingPrice?.toString() || "",
            lowStockThreshold: stock.lowStockThreshold?.toString() || "",
          };
          changed = true;
        }
      }
      for (const key of Object.keys(next)) {
        if (!stocks.some((stock) => stock.id === key && batchSetupMissing(stock).length > 0)) {
          delete next[key];
          changed = true;
        }
      }
      return changed ? next : current;
    });
  }, [incompleteSetupStocks, stocks]);

  React.useEffect(() => {
    if (stockCountForm.stockBatchId && !countableStocks.some((stock) => stock.id === stockCountForm.stockBatchId)) {
      setStockCountForm((current) => ({ ...current, stockBatchId: "", countedQuantity: "" }));
    }
  }, [countableStocks, stockCountForm.stockBatchId]);

  React.useEffect(() => {
    if (!physicalCountWorkspaceLines.length) {
      setPhysicalCountWorkspaceLineId(null);
      return;
    }
    if (!physicalCountWorkspaceLineId || !physicalCountWorkspaceLines.some((line) => line.id === physicalCountWorkspaceLineId)) {
      setPhysicalCountWorkspaceLineId(physicalCountWorkspaceLines[0].id);
    }
  }, [physicalCountWorkspaceLineId, physicalCountWorkspaceLines]);

  const openQuickCreateMedicine = React.useCallback((seedText = "") => {
    setQuickMedicineForm((current) => ({
      ...emptyQuickMedicineForm(),
      medicineName: seedText.trim(),
      barcode: stockForm.barcode.trim() || current.barcode,
      qrCode: stockForm.qrCode.trim() || current.qrCode,
      externalCode: stockForm.externalCode.trim() || current.externalCode,
      defaultPrice: stockForm.sellingPrice.trim() ? Number(stockForm.sellingPrice) : null,
      active: true,
    }));
    setQuickMedicineOpen(true);
  }, [stockForm.barcode, stockForm.externalCode, stockForm.qrCode, stockForm.sellingPrice]);

  const saveQuickMedicine = async () => {
    if (!auth.accessToken || !auth.tenantId || !quickMedicineForm.medicineName.trim()) {
      setError("Enter a medicine name before saving.");
      return;
    }
    const parsedMedicine = medicineMasterSchema.safeParse(quickMedicineForm);
    if (!parsedMedicine.success) {
      const fieldErrors = zodFieldErrors(parsedMedicine.error);
      setQuickMedicineFieldErrors(fieldErrors);
      setError(parsedMedicine.error.issues[0]?.message || "Medicine could not be saved.");
      return;
    }
    setSaving(true);
    setError(null);
    setQuickMedicineFieldErrors({});
    try {
      const created = await createMedicine(auth.accessToken, auth.tenantId, {
        ...quickMedicineForm,
        medicineName: quickMedicineForm.medicineName.trim(),
        genericName: quickMedicineForm.genericName?.trim() || null,
        brandName: quickMedicineForm.brandName?.trim() || null,
        category: quickMedicineForm.category?.trim() || null,
        dosageForm: quickMedicineForm.dosageForm?.trim() || null,
        strength: quickMedicineForm.strength?.trim() || null,
        unit: quickMedicineForm.unit?.trim() || null,
        manufacturer: quickMedicineForm.manufacturer?.trim() || null,
        barcode: quickMedicineForm.barcode?.trim() || null,
        qrCode: quickMedicineForm.qrCode?.trim() || null,
        externalCode: quickMedicineForm.externalCode?.trim() || null,
      });
      await loadAll();
      setStockForm((current) => ({ ...current, medicineId: created.id }));
      setMedicineSearchInput(created.medicineName);
      setQuickMedicineOpen(false);
      setSuccess("Medicine created. Continue adding the stock batch.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create medicine");
    } finally {
      setSaving(false);
    }
  };

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [medicineRows, stockRows, transactionRows, lowStockRows, locationRows, saleRows] = await Promise.all([
      getMedicines(auth.accessToken, auth.tenantId),
      getStocks(auth.accessToken, auth.tenantId),
      getInventoryTransactions(auth.accessToken, auth.tenantId),
      getLowStock(auth.accessToken, auth.tenantId),
      getInventoryLocations(auth.accessToken, auth.tenantId),
      listPharmacyPosSales(auth.accessToken, auth.tenantId).catch(() => [] as PharmacyPosSale[]),
    ]);
    setMedicines(medicineRows);
    setStocks(stockRows);
    setTransactions(transactionRows);
    setLowStock(lowStockRows);
    setLocations(locationRows);
    setSales(saleRows);
    setSelectedLocationId((current) => current || locationRows.find((location) => location.defaultLocation)?.id || null);
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        await loadAll();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load inventory");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadAll]);

  React.useEffect(() => {
    const next = searchParams.get("tab");
    if (!next) return;
    if (TABS.some((item) => item.value === next)) {
      setTab(next as (typeof TABS)[number]["value"]);
    }
  }, [searchParams]);

  React.useEffect(() => {
    const ref = searchParams.get("ref");
    if (!ref) return;
    setTab("stocks");
    setStockSearch(ref);
  }, [searchParams]);

  React.useEffect(() => {
    const source = searchParams.get("source");
    const reference = searchParams.get("reference");
    if (!source && !reference) return;
    const key = `${source || ""}:${reference || ""}`;
    if (handledReferenceRef.current === key) return;
    handledReferenceRef.current = key;
    setDeepLinkBanner(reference ? `Opened from reconciliation for ${source || "reference"}: ${reference}` : "Opened from reconciliation.");
    if (!searchParams.get("ref") && reference) {
      setTab("stocks");
      setStockSearch(reference);
    }
  }, [searchParams]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const updatePhysicalCountSession = (
    sessionId: string,
    updater: (session: PhysicalCountSession) => PhysicalCountSession,
  ) => {
    setPhysicalCountSessions((current) => current.map((session) => session.id === sessionId ? updater(session) : session));
  };

  const updatePhysicalCountSessionLine = (
    sessionId: string,
    lineId: string,
    patch: Partial<Pick<PhysicalCountSessionLine, "countedQty" | "reason" | "reviewerRemarks" | "flagged" | "reviewed">>,
  ) => {
    updatePhysicalCountSession(sessionId, (session) => {
      const nextLines = session.lines.map((line) => line.id === lineId ? { ...line, ...patch } : line);
      const now = new Date().toISOString();
      const startedAt = session.audit.startedAt ?? now;
      const startedBy = session.audit.startedBy ?? inventoryActorLabel;
      return {
        ...session,
        status: session.status === "DRAFT" ? "IN_PROGRESS" : session.status,
        lines: nextLines,
        audit: {
          ...session.audit,
          startedAt,
          startedBy,
          lastUpdatedAt: now,
          sessionDuration: physicalCountDurationLabel(startedAt, now),
        },
      };
    });
  };

  const updatePhysicalCountSessionNotes = (
    sessionId: string,
    patch: Partial<Pick<PhysicalCountAuditFields, "generalNotes" | "counterNotes" | "reviewerNotes" | "auditNotes" | "approvalNotes" | "rejectionReason" | "returnReason">>,
  ) => {
    updatePhysicalCountSession(sessionId, (session) => ({
      ...session,
      audit: {
        ...session.audit,
        ...patch,
        lastUpdatedAt: new Date().toISOString(),
      },
    }));
  };

  const updatePhysicalCountReviewChecklist = (sessionId: string, key: keyof PhysicalCountReviewChecklist, value: boolean) => {
    updatePhysicalCountSession(sessionId, (session) => ({
      ...session,
      audit: {
        ...session.audit,
        reviewChecklist: {
          ...session.audit.reviewChecklist,
          [key]: value,
        },
        lastUpdatedAt: new Date().toISOString(),
      },
    }));
  };

  const openPhysicalCountDrawer = (sessionId: string, mode: "view" | "review") => {
    setPhysicalCountDrawerSessionId(sessionId);
    setPhysicalCountDrawerMode(mode);
  };

  const openPhysicalCountWorkspace = (sessionId: string, mode: PhysicalCountWorkspaceMode) => {
    setPhysicalCountWorkspaceSessionId(sessionId);
    setPhysicalCountWorkspaceMode(mode);
    setPhysicalCountSearch("");
    setPhysicalCountBatchSearch("");
    updatePhysicalCountSession(sessionId, (session) => {
      if (mode !== "continue") return session;
      if (session.status !== "DRAFT") return session;
      const now = new Date().toISOString();
      return {
        ...session,
        status: "IN_PROGRESS",
        audit: {
          ...session.audit,
          startedBy: session.audit.startedBy ?? inventoryActorLabel,
          startedAt: session.audit.startedAt ?? now,
          lastUpdatedAt: session.audit.lastUpdatedAt ?? now,
          sessionDuration: physicalCountDurationLabel(session.audit.startedAt ?? now, now),
        },
      };
    });
  };

  const savePhysicalCountSessionDraft = (nextMessage = "Physical count session saved locally. Inventory quantity is unchanged.") => {
    setError(null);
    setSuccess(nextMessage);
  };

  const createPhysicalCountSession = () => {
    if (!physicalCountForm.sessionName.trim()) {
      setError("Enter a session name.");
      return;
    }
    if (!physicalCountForm.locationId) {
      setError("Select a location.");
      return;
    }
    if (physicalCountForm.scope === "CATEGORY" && !physicalCountForm.category.trim()) {
      setError("Select a category for this count session.");
      return;
    }
    if (physicalCountForm.scope === "SELECTED_MEDICINES" && !physicalCountForm.selectedMedicineIds.length) {
      setError("Select at least one medicine for this count session.");
      return;
    }

    const lines = buildPhysicalCountSessionLines(stocks, medicines, locations, physicalCountForm);
    if (!lines.length) {
      setError("No stock batches match the selected location and scope.");
      return;
    }

    const locationName = locations.find((location) => location.id === physicalCountForm.locationId)?.locationName || "Main Pharmacy";
    const scopeDetail = physicalCountForm.scope === "CATEGORY"
      ? `${physicalCountScopeLabel(physicalCountForm.scope)}: ${physicalCountForm.category.trim()}`
      : physicalCountForm.scope === "SELECTED_MEDICINES"
        ? `${physicalCountScopeLabel(physicalCountForm.scope)}: ${physicalCountForm.selectedMedicineIds.length} medicines`
        : physicalCountScopeLabel(physicalCountForm.scope);

    const createdSession: PhysicalCountSession = {
      id: `physical-count-${Date.now()}`,
      sessionName: physicalCountForm.sessionName.trim(),
      locationId: physicalCountForm.locationId,
      locationName,
      scope: physicalCountForm.scope,
      scopeLabel: scopeDetail,
      reason: physicalCountForm.reason,
      status: "DRAFT",
      lines,
      audit: emptyPhysicalCountAuditFields(inventoryActorLabel),
    };

    setPhysicalCountSessions((current) => [createdSession, ...current]);
    setPhysicalCountForm({
      ...emptyPhysicalCountSessionForm(),
      locationId: physicalCountForm.locationId,
    });
    setPhysicalCountCreateOpen(false);
    setError(null);
    setSuccess(`Session created locally. System quantities were captured for ${lines.length} batch${lines.length === 1 ? "" : "es"} for comparison.`);
  };

  const submitPhysicalCountSession = () => {
    if (!physicalCountWorkspaceSession) {
      setError("Open a session before submitting it.");
      return;
    }
    if (physicalCountWorkspaceSession.status !== "IN_PROGRESS") {
      setError("Only in-progress sessions can be submitted.");
      return;
    }
    const counted = physicalCountWorkspaceSession.lines.filter((line) => physicalCountLineDifference(line) != null).length;
    if (counted !== physicalCountWorkspaceSession.lines.length) {
      setError("Complete pending counted quantities before submitting this session.");
      return;
    }
    updatePhysicalCountSession(physicalCountWorkspaceSession.id, (session) => {
      const now = new Date().toISOString();
      return {
        ...session,
        status: "SUBMITTED",
        audit: {
          ...session.audit,
          submittedBy: inventoryActorLabel,
          submittedAt: now,
          lastUpdatedAt: now,
          sessionDuration: physicalCountDurationLabel(session.audit.startedAt, now),
        },
      };
    });
    setError(null);
    setSuccess("Physical count session submitted in local workflow only. Inventory quantity is unchanged.");
  };

  const markPhysicalCountSessionReviewed = () => {
    if (!physicalCountDrawerSession || physicalCountDrawerSession.status !== "SUBMITTED") {
      setError("Only submitted sessions can be marked reviewed.");
      return;
    }
    updatePhysicalCountSession(physicalCountDrawerSession.id, (session) => {
      const now = new Date().toISOString();
      return {
        ...session,
        status: "REVIEWED",
        lines: session.lines.map((line) => ({ ...line, reviewed: true })),
        audit: {
          ...session.audit,
          reviewedBy: inventoryActorLabel,
          reviewedAt: now,
          reviewer: inventoryActorLabel,
          reviewedDate: now,
          lastUpdatedAt: now,
          sessionDuration: physicalCountDurationLabel(session.audit.startedAt, now),
        },
      };
    });
    setError(null);
    setSuccess("Physical count session marked reviewed in local workflow only. Inventory quantity is unchanged.");
  };

  const approvePhysicalCountSession = () => {
    if (!physicalCountDrawerSession || physicalCountDrawerSession.status !== "REVIEWED") {
      setError("Only reviewed sessions can be approved.");
      return;
    }
    const now = new Date().toISOString();
    updatePhysicalCountSession(physicalCountDrawerSession.id, (session) => ({
      ...session,
      status: "APPROVED",
      audit: {
        ...session.audit,
        approvedBy: inventoryActorLabel,
        approvedAt: now,
        lastUpdatedAt: now,
      },
    }));
    setError(null);
    setSuccess("Physical count session approved. Inventory quantity is unchanged until posting.");
  };

  const rejectPhysicalCountSession = () => {
    if (!physicalCountDrawerSession || physicalCountDrawerSession.status !== "REVIEWED") {
      setError("Only reviewed sessions can be rejected.");
      return;
    }
    if (!physicalCountDrawerSession.audit.rejectionReason.trim()) {
      setError("Enter a rejection reason before rejecting this session.");
      return;
    }
    const now = new Date().toISOString();
    updatePhysicalCountSession(physicalCountDrawerSession.id, (session) => ({
      ...session,
      status: "REJECTED",
      audit: {
        ...session.audit,
        rejectedBy: inventoryActorLabel,
        rejectedAt: now,
        lastUpdatedAt: now,
      },
    }));
    setError(null);
    setSuccess("Physical count session rejected. Inventory quantity is unchanged.");
  };

  const returnPhysicalCountSessionForRecount = () => {
    if (!physicalCountDrawerSession || physicalCountDrawerSession.status !== "REVIEWED") {
      setError("Only reviewed sessions can be returned for recount.");
      return;
    }
    if (!physicalCountDrawerSession.audit.returnReason.trim()) {
      setError("Enter a return reason before sending this session back for recount.");
      return;
    }
    const now = new Date().toISOString();
    updatePhysicalCountSession(physicalCountDrawerSession.id, (session) => ({
      ...session,
      status: "IN_PROGRESS",
      audit: {
        ...session.audit,
        returnedBy: inventoryActorLabel,
        returnedAt: now,
        lastUpdatedAt: now,
      },
    }));
    setError(null);
    setSuccess("Physical count session returned for recount. Inventory quantity is unchanged.");
  };

  const postPhysicalCountAdjustments = async () => {
    if (!auth.accessToken || !auth.tenantId || !physicalCountDrawerSession) {
      setError("Open an approved session before posting.");
      return;
    }
    if (physicalCountDrawerSession.status !== "APPROVED") {
      setError("Only approved sessions can be posted.");
      return;
    }
    if (physicalCountDrawerSession.audit.postedAt) {
      setError("This session has already been posted.");
      return;
    }
    const varianceLines = physicalCountDrawerSession.lines.filter((line) => {
      const difference = physicalCountLineDifference(line);
      return difference != null && difference !== 0;
    });
    for (const line of physicalCountDrawerSession.lines) {
      const difference = physicalCountLineDifference(line);
      if (difference == null) {
        setError(`Counted quantity is missing for ${line.medicineName}.`);
        return;
      }
      if (difference !== 0 && !line.reason.trim()) {
        setError(`Reason is required for variance line ${line.medicineName} / ${line.batchNumber}.`);
        return;
      }
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      for (const line of varianceLines) {
        const difference = physicalCountLineDifference(line) ?? 0;
        const movementType: InventoryTransactionType = difference > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT";
        const beforeQty = line.systemQty;
        const afterQty = line.systemQty + difference;
        await createInventoryTransaction(auth.accessToken, auth.tenantId, {
          medicineId: line.medicineId,
          stockBatchId: line.stockBatchId,
          transactionType: movementType,
          quantity: Math.abs(difference),
          reason: `Physical Count ${physicalCountDrawerSession.sessionName}`,
          referenceType: "PHYSICAL_COUNT",
          referenceId: physicalCountDrawerSession.id,
          notes: [
            `Source: PHYSICAL_COUNT`,
            `Session: ${physicalCountDrawerSession.sessionName}`,
            `Location: ${physicalCountDrawerSession.locationName}`,
            `Scope: ${physicalCountDrawerSession.scopeLabel}`,
            `Batch: ${line.batchNumber}`,
            `Before Qty: ${beforeQty}`,
            `After Qty: ${afterQty}`,
            `Adjustment Qty: ${Math.abs(difference)}`,
            `Reason: ${line.reason.trim()}`,
            `Posted By: ${inventoryActorLabel}`,
            `Posted At: ${new Date().toISOString()}`,
            line.reviewerRemarks.trim() || null,
          ].filter(Boolean).join(" • "),
        });
      }
      const now = new Date().toISOString();
      updatePhysicalCountSession(physicalCountDrawerSession.id, (session) => ({
        ...session,
        status: "POSTED",
        audit: {
          ...session.audit,
          postedBy: inventoryActorLabel,
          postedAt: now,
          lastUpdatedAt: now,
        },
      }));
      await loadAll();
      setSuccess(`Physical count variances posted for ${varianceLines.length} line${varianceLines.length === 1 ? "" : "s"}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post physical count adjustments.");
    } finally {
      setSaving(false);
    }
  };

  const saveStock = async () => {
    if (!auth.accessToken || !auth.tenantId || !stockForm.medicineId) {
      setError("Select a medicine before saving stock.");
      return;
    }
    if (!selectedStockId) {
      openDirectGoodsReceipt();
      return;
    }
    const parsedStock = inventoryBatchEditSchema.safeParse(stockInput(stockForm));
    if (!parsedStock.success) {
      const fieldErrors = zodFieldErrors(parsedStock.error);
      setStockFieldErrors(fieldErrors);
      setError(parsedStock.error.issues[0]?.message || "Stock could not be saved.");
      focusFirstInventoryField(fieldErrors, {
        medicineId: "inventory-stock-medicine",
        locationId: "inventory-stock-location",
        batchNumber: "inventory-stock-batch",
        expiryDate: "inventory-stock-expiry",
        quantityOnHand: "inventory-stock-quantity",
        lowStockThreshold: "inventory-stock-reorder",
        unitCost: "inventory-stock-purchase-rate",
        sellingPrice: "inventory-stock-mrp",
        barcode: "inventory-stock-barcode",
        qrCode: "inventory-stock-qrcode",
        externalCode: "inventory-stock-external",
      });
      return;
    }
    if (!selectedMedicineForStock?.active) {
      setStockFieldErrors({ medicineId: "Cannot add stock for an inactive medicine." });
      setError("Cannot add stock for an inactive medicine.");
      return;
    }
    if (currentStock && currentStockHasMovements) {
      if (currentStock.medicineId !== stockForm.medicineId || (stockForm.locationId || null) !== (currentStock.locationId || "") || (stockForm.batchNumber.trim() || null) !== (currentStock.batchNumber || null)) {
        setStockFieldErrors({
          medicineId: "Medicine cannot be changed after stock movement exists.",
          locationId: "Location cannot be changed after stock movement exists.",
          batchNumber: "Batch number cannot be changed after stock movement exists.",
        });
        setError("Medicine, location, and batch number cannot be changed after stock movement exists.");
        return;
      }
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setStockFieldErrors({});
    try {
      const body = stockInput(stockForm);
      await updateStock(auth.accessToken, auth.tenantId, selectedStockId, body);
      setStockForm(emptyStockForm());
      setSelectedStockId(null);
      setMedicineSearchInput("");
      await loadAll();
      setSuccess("Batch metadata saved.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save stock";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const saveSetupRow = async (stockId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const stock = stocks.find((row) => row.id === stockId);
    if (!stock) {
      setError("Selected batch could not be found.");
      return;
    }
    const draft = setupQueueEdits[stockId] ?? {
      sellingPrice: stock.sellingPrice?.toString() || "",
      lowStockThreshold: stock.lowStockThreshold?.toString() || "",
    };
    const sellingPrice = parseOptionalNumberInput(draft.sellingPrice);
    const lowStockThreshold = parseOptionalNumberInput(draft.lowStockThreshold);
    if (draft.sellingPrice.trim() && sellingPrice == null) {
      setError("MRP must be a valid number.");
      return;
    }
    if (draft.lowStockThreshold.trim() && lowStockThreshold == null) {
      setError("Reorder level must be a valid number.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await updateStock(
        auth.accessToken,
        auth.tenantId,
        stockId,
        stockUpdateInputFromRecord(stock, {
          sellingPrice,
          lowStockThreshold,
        }),
      );
      await loadAll();
      setSuccess(`Batch metadata saved for ${stock.medicineName}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save batch setup metadata.");
    } finally {
      setSaving(false);
    }
  };

  const saveAllSetupRows = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const dirtyRows = incompleteSetupStocks
      .map((stock) => {
        const draft = setupQueueEdits[stock.id] ?? {
          sellingPrice: stock.sellingPrice?.toString() || "",
          lowStockThreshold: stock.lowStockThreshold?.toString() || "",
        };
        const draftSellingPrice = draft.sellingPrice.trim();
        const draftLowStock = draft.lowStockThreshold.trim();
        const dirty = draftSellingPrice !== (stock.sellingPrice?.toString() || "")
          || draftLowStock !== (stock.lowStockThreshold?.toString() || "");
        return { stock, draft, dirty };
      })
      .filter((row) => row.dirty);
    if (!dirtyRows.length) {
      setError("No setup changes are pending.");
      return;
    }

    for (const row of dirtyRows) {
      if (row.draft.sellingPrice.trim() && parseOptionalNumberInput(row.draft.sellingPrice) == null) {
        setError(`MRP must be a valid number for ${row.stock.medicineName}.`);
        return;
      }
      if (row.draft.lowStockThreshold.trim() && parseOptionalNumberInput(row.draft.lowStockThreshold) == null) {
        setError(`Reorder level must be a valid number for ${row.stock.medicineName}.`);
        return;
      }
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      for (const row of dirtyRows) {
        await updateStock(
          auth.accessToken,
          auth.tenantId,
          row.stock.id,
          stockUpdateInputFromRecord(row.stock, {
            sellingPrice: parseOptionalNumberInput(row.draft.sellingPrice),
            lowStockThreshold: parseOptionalNumberInput(row.draft.lowStockThreshold),
          }),
        );
      }
      await loadAll();
      setSuccess(`Batch setup metadata saved for ${dirtyRows.length} batch${dirtyRows.length === 1 ? "" : "es"}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save batch setup metadata.");
    } finally {
      setSaving(false);
    }
  };

  const editStock = (stock: Stock) => {
    setSelectedStockId(stock.id);
    setMedicineSearchInput(stock.medicineName);
    setStockForm({
      medicineId: stock.medicineId,
      locationId: stock.locationId || "",
      batchNumber: stock.batchNumber || "",
      purchaseReferenceNumber: stock.purchaseReferenceNumber || "",
      barcode: stock.barcode || "",
      qrCode: stock.qrCode || "",
      externalCode: stock.externalCode || "",
      expiryDate: stock.expiryDate || "",
      quantityOnHand: stock.quantityOnHand.toString(),
      lowStockThreshold: stock.lowStockThreshold?.toString() || "",
      unitCost: stock.unitCost?.toString() || "",
      sellingPrice: stock.sellingPrice?.toString() || "",
      active: stock.active,
    });
  };

  const startStockAdjustment = (stock: Stock) => {
    setTab("stocks");
    setStockActionPanel("transaction");
    setTransactionSearchInput([
      stock.medicineName,
      stock.batchNumber || "No batch",
      stock.locationName || "Main Pharmacy",
    ].join(" • "));
    setTransactionForm((current) => ({
      ...current,
      medicineId: stock.medicineId,
      stockBatchId: stock.id,
    }));
  };

  React.useEffect(() => {
    if (!stockForm.medicineId) {
      return;
    }
    const medicine = medicines.find((row) => row.id === stockForm.medicineId);
    if (medicine && medicine.medicineName !== medicineSearchInput) {
      setMedicineSearchInput(medicine.medicineName);
    }
  }, [medicines, medicineSearchInput, stockForm.medicineId]);

  React.useEffect(() => {
    if (transactionBatchOptions.length === 1 && transactionForm.stockBatchId !== transactionBatchOptions[0].id) {
      setTransactionForm((current) => ({
        ...current,
        medicineId: transactionBatchOptions[0].medicineId,
        stockBatchId: transactionBatchOptions[0].id,
      }));
    }
  }, [transactionBatchOptions, transactionForm.stockBatchId]);

  const saveTransaction = async () => {
    if (!auth.accessToken || !auth.tenantId || !transactionForm.medicineId || !transactionForm.stockBatchId || !transactionForm.quantity.trim()) {
      setError("Select an inventory batch and quantity before saving a transaction.");
      return;
    }
    if (!transactionForm.reason.trim()) {
      setTransactionFieldErrors({ notes: "Reason is required for this stock movement." });
      setError("Enter a reason before saving a transaction.");
      return;
    }
    if (transactionForm.reason.trim().length > 60) {
      setTransactionFieldErrors({ notes: "Reason must be 60 characters or fewer." });
      setError("Reason must be 60 characters or fewer.");
      return;
    }
    if (transactionForm.notes.trim().length > 250) {
      setTransactionFieldErrors({ notes: "Notes must be 250 characters or fewer." });
      setError("Notes must be 250 characters or fewer.");
      return;
    }
    if (!selectedTransactionStock) {
      setError("Select a valid stock batch before saving a transaction.");
      return;
    }
    const quantity = Number(transactionForm.quantity);
    if (!Number.isInteger(quantity) || quantity <= 0) {
      setTransactionFieldErrors({ quantity: "Quantity must be a positive whole number." });
      setError("Quantity must be a positive whole number.");
      return;
    }
    if (stockAdjustmentIsDecrease(transactionForm.transactionType) && quantity > selectedTransactionStock.quantityOnHand) {
      setTransactionFieldErrors({ quantity: "Quantity cannot exceed current stock for this movement." });
      setError("Quantity cannot exceed current stock for this movement.");
      return;
    }
    const parsedTransaction = inventoryTransactionFormSchema.safeParse({
      ...transactionForm,
      notes: transactionForm.reason.trim(),
    });
    if (!parsedTransaction.success) {
      const fieldErrors = zodFieldErrors(parsedTransaction.error);
      setTransactionFieldErrors(fieldErrors);
      setError(parsedTransaction.error.issues[0]?.message || "Stock movement could not be saved.");
      focusFirstInventoryField(fieldErrors, {
        medicineId: "inventory-transaction-medicine",
        stockBatchId: "inventory-transaction-batch",
        quantity: "inventory-transaction-quantity",
        referenceType: "inventory-transaction-reference-type",
        referenceId: "inventory-transaction-reference-id",
        notes: "inventory-transaction-notes",
      });
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setTransactionFieldErrors({});
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, transactionInput(transactionForm));
      setTransactionForm(emptyTransactionForm());
      setTransactionSearchInput("");
      await loadAll();
      setSuccess("Inventory transaction saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save inventory transaction");
    } finally {
      setSaving(false);
    }
  };

  const submitCustomerReturn = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedCustomerSale || !selectedCustomerReturnItem) {
      setError("Select a sale line before submitting a customer return.");
      return;
    }
    const returnable = selectedCustomerReturnItem.quantity - selectedCustomerReturnItem.returnedQuantity;
    const parsed = inventoryCustomerReturnSchema.safeParse({
      saleId: selectedCustomerSale.id,
      saleLineId: selectedCustomerReturnItem.id,
      returnQuantity: customerReturnQuantity,
      condition: customerReturnReusable ? "REUSABLE" : "NOT_SELLABLE",
      refundMode: customerReturnMode,
      reason: customerReturnReason,
      referenceNumber: customerReturnReference,
      notes: customerReturnNotes,
    });
    if (!parsed.success) {
      const fieldErrors = zodFieldErrors(parsed.error);
      setCustomerReturnFieldErrors(fieldErrors);
      setError(parsed.error.issues[0]?.message || "Customer return could not be processed.");
      focusFirstInventoryField(fieldErrors, {
        saleId: "customer-return-sale",
        saleLineId: "customer-return-line",
        returnQuantity: "inventory-customer-return-quantity",
        reason: "customer-return-reason",
        notes: "customer-return-notes",
        referenceNumber: "customer-return-reference",
        condition: "customer-return-condition",
      });
      return;
    }
    const quantity = Number(customerReturnQuantity);
    if (quantity > returnable) {
      setError("Return quantity exceeds the remaining sold quantity.");
      return;
    }
    if (customerReturnReusable && selectedCustomerReturnItem.expiryDate && new Date(selectedCustomerReturnItem.expiryDate) < new Date()) {
      setError("Batch expired and cannot be sold or dispensed.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setCustomerReturnFieldErrors({});
    try {
      await returnPharmacyPosSale(auth.accessToken, auth.tenantId, selectedCustomerSale.id, {
        reason: customerReturnReason.trim(),
        refundMode: customerReturnMode,
        referenceNumber: customerReturnReference.trim() || null,
        notes: customerReturnNotes.trim() || `Customer return from Inventory: ${customerReturnReusable ? "Reusable" : "Non-sellable"} - ${customerReturnReason.trim()}`,
        items: [{
          saleItemId: selectedCustomerReturnItem.id,
          quantity,
          reusable: customerReturnReusable,
        }],
      });
      await loadAll();
      setCustomerReturnQuantity("1");
      setCustomerReturnReason("");
      setCustomerReturnNotes("");
      setCustomerReturnReference("");
      setSuccess(selectedCustomerSale.paidAmount > 0
        ? "Customer return processed. Refund can be processed from Billing / Refunds."
        : "Customer return processed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Customer return could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const submitVendorReturn = async () => {
    if (!auth.accessToken || !auth.tenantId || !vendorReturnForm.stockBatchId || !vendorReturnForm.medicineId) {
      setError("Select a medicine batch before submitting a vendor return.");
      return;
    }
    const batch = stocks.find((stock) => stock.id === vendorReturnForm.stockBatchId) ?? null;
    if (!batch) {
      setError("Select a valid batch.");
      return;
    }
    const parsed = inventoryVendorReturnSchema.safeParse({
      medicineId: vendorReturnForm.medicineId,
      stockBatchId: vendorReturnForm.stockBatchId,
      returnQuantity: vendorReturnForm.quantity,
      supplierReference: vendorReturnForm.supplierReference,
      reason: vendorReturnForm.reason,
      notes: vendorReturnForm.notes,
    });
    if (!parsed.success) {
      const fieldErrors = zodFieldErrors(parsed.error);
      setVendorReturnFieldErrors(fieldErrors);
      setError(parsed.error.issues[0]?.message || "Vendor return could not be processed.");
      focusFirstInventoryField(fieldErrors, {
        medicineId: "vendor-return-medicine",
        stockBatchId: "vendor-return-batch",
        quantity: "vendor-return-quantity",
        supplierReference: "vendor-return-supplier",
        reason: "vendor-return-reason",
        notes: "vendor-return-notes",
      });
      return;
    }
    const quantity = Number(vendorReturnForm.quantity);
    if (quantity > batch.quantityOnHand) {
      setError("Insufficient stock available.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setVendorReturnFieldErrors({});
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, {
        medicineId: batch.medicineId,
        stockBatchId: batch.id,
        transactionType: "VENDOR_RETURN_OUT",
        quantity,
        reason: vendorReturnForm.reason.trim(),
        referenceType: "VENDOR_RETURN",
        referenceId: batch.id,
        notes: [
          batch.supplierName ? `Supplier ${batch.supplierName}` : null,
          batch.purchaseReferenceNumber ? `Invoice/GRN ${batch.purchaseReferenceNumber}` : null,
          vendorReturnForm.supplierReference.trim() || null,
          vendorReturnForm.notes.trim() || null,
        ].filter(Boolean).join(" • "),
      });
      await loadAll();
      setVendorReturnForm({ medicineId: "", stockBatchId: "", quantity: "", supplierReference: "", reason: "", notes: "" });
      setSuccess("Vendor return posted and stock reduced.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Vendor return could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const submitWriteOff = async () => {
    if (!auth.accessToken || !auth.tenantId || !writeOffForm.stockBatchId || !writeOffForm.medicineId) {
      setError("Select a medicine batch before posting a write-off.");
      return;
    }
    const batch = stocks.find((stock) => stock.id === writeOffForm.stockBatchId) ?? null;
    if (!batch) {
      setError("Select a valid batch.");
      return;
    }
    const parsed = inventoryWriteOffSchema.safeParse({
      medicineId: writeOffForm.medicineId,
      stockBatchId: writeOffForm.stockBatchId,
      writeOffQuantity: writeOffForm.quantity,
      reason: writeOffForm.reason,
      notes: writeOffForm.notes,
    });
    if (!parsed.success) {
      const fieldErrors = zodFieldErrors(parsed.error);
      setWriteOffFieldErrors(fieldErrors);
      setError(parsed.error.issues[0]?.message || "Write-off could not be processed.");
      focusFirstInventoryField(fieldErrors, {
        medicineId: "writeoff-medicine",
        stockBatchId: "writeoff-batch",
        writeOffQuantity: "writeoff-quantity",
        reason: "writeoff-reason",
        notes: "writeoff-notes",
      });
      return;
    }
    const quantity = Number(writeOffForm.quantity);
    if (quantity > batch.quantityOnHand) {
      setError("Insufficient stock available.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setWriteOffFieldErrors({});
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, {
        medicineId: batch.medicineId,
        stockBatchId: batch.id,
        transactionType: "WRITE_OFF",
        quantity,
        reason: writeOffForm.reason.trim(),
        referenceType: "WRITE_OFF",
        referenceId: batch.id,
        notes: [
          `Batch ${batch.batchNumber || "NA"}`,
          batch.locationName || null,
          writeOffForm.notes.trim() || null,
        ].filter(Boolean).join(" • "),
      });
      await loadAll();
      setWriteOffForm({ medicineId: "", stockBatchId: "", quantity: "", reason: "", notes: "" });
      setSuccess("Write-off posted and stock reduced.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Write-off could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const currentStock = selectedStockId ? stocks.find((stock) => stock.id === selectedStockId) || null : null;
  const currentStockMovementCount = React.useMemo(
    () => (selectedStockId ? transactions.filter((transaction) => transaction.stockBatchId === selectedStockId).length : 0),
    [selectedStockId, transactions],
  );
  const currentStockHistory = React.useMemo(() => {
    if (!currentStock) {
      return [] as Array<{ label: string; tone: "default" | "success" | "warning" | "info" | "primary"; helper: string }>;
    }
    const batchTransactions = transactions
      .filter((transaction) => transaction.stockBatchId === currentStock.id)
      .sort((left, right) => left.createdAt.localeCompare(right.createdAt));
    const receivedTransaction = batchTransactions.find((transaction) => ["OPENING", "PURCHASE", "STOCK_IN", "CUSTOMER_RETURN_IN"].includes(transaction.transactionType)) || null;
    const physicalCountTransaction = batchTransactions.find((transaction) => transaction.referenceType === "PHYSICAL_COUNT" || transaction.notes?.includes("Source: PHYSICAL_COUNT")) || null;
    const adjustmentTransaction = batchTransactions.find((transaction) => transaction.transactionType === "ADJUSTMENT" || transaction.transactionType === "ADJUSTMENT_IN" || transaction.transactionType === "ADJUSTMENT_OUT") || null;
    const setupComplete = batchSetupMissing(currentStock).length === 0;
    return [
      {
        label: "GRN",
        tone: receivedTransaction ? "success" as const : "default" as const,
        helper: receivedTransaction?.businessReference || receivedTransaction?.reason || "Goods receipt created the batch",
      },
      {
        label: "Commercial Setup",
        tone: setupComplete ? "success" as const : "warning" as const,
        helper: setupComplete ? "MRP and reorder level are maintained" : "Commercial setup is incomplete",
      },
      {
        label: "Physical Count",
        tone: physicalCountTransaction ? "info" as const : "default" as const,
        helper: physicalCountTransaction ? "Posted physical count adjustment exists" : "No posted physical count adjustment yet",
      },
      {
        label: "Stock Adjustment",
        tone: adjustmentTransaction ? "warning" as const : "default" as const,
        helper: adjustmentTransaction ? "Manual or operational adjustment recorded" : "No manual adjustment recorded",
      },
      {
        label: "Current Quantity",
        tone: "primary" as const,
        helper: `${currentStock.quantityOnHand} units on hand`,
      },
    ];
  }, [currentStock, transactions]);
  const currentStockHasMovements = currentStockMovementCount > 0;
  const editingExistingBatch = Boolean(selectedStockId && currentStock);
  const canEditBatchLocation = Boolean(editingExistingBatch && !currentStockHasMovements);
  const metadataDirty = React.useMemo(() => {
    if (!currentStock) return false;
    return (
      normalizeInventoryText(stockForm.barcode) !== normalizeInventoryText(currentStock.barcode)
      || normalizeInventoryText(stockForm.qrCode) !== normalizeInventoryText(currentStock.qrCode)
      || normalizeInventoryText(stockForm.externalCode) !== normalizeInventoryText(currentStock.externalCode)
      || normalizeInventoryText(stockForm.lowStockThreshold) !== normalizeInventoryText(currentStock.lowStockThreshold?.toString() || "")
      || normalizeInventoryText(stockForm.sellingPrice) !== normalizeInventoryText(currentStock.sellingPrice?.toString() || "")
      || stockForm.active !== currentStock.active
      || (canEditBatchLocation && normalizeInventoryText(stockForm.locationId) !== normalizeInventoryText(currentStock.locationId || ""))
    );
  }, [canEditBatchLocation, currentStock, stockForm.active, stockForm.barcode, stockForm.externalCode, stockForm.locationId, stockForm.lowStockThreshold, stockForm.qrCode, stockForm.sellingPrice]);
  const selectedMedicineForStock = React.useMemo(
    () => medicines.find((medicine) => medicine.id === stockForm.medicineId) ?? null,
    [medicines, stockForm.medicineId],
  );
  const stockBatchIsSellable = React.useMemo(
    () => ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "DROPS", "OINTMENT", "SACHET"].includes(selectedMedicineForStock?.medicineType || ""),
    [selectedMedicineForStock?.medicineType],
  );
  const openDirectGoodsReceipt = React.useCallback(() => {
    navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct");
  }, [navigate]);

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
            <Inventory2RoundedIcon color="primary" />
            <Typography variant="h4" sx={{ fontWeight: 900 }}>
              Inventory
            </Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Physical stock control, batch visibility, expiry monitoring, and inventory movements.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Tooltip title="Purchase medicines from suppliers using Purchase Orders, Invoices and Goods Receipt.">
            <span>
              <Button variant="outlined" size="small" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
            </span>
          </Tooltip>
          <Tooltip title="Receive medicines directly without Purchase Order. Used for opening stock, emergency purchase, migration, local distributor, or donation.">
            <span>
              <Button variant="outlined" size="small" onClick={openDirectGoodsReceipt}>Direct Goods Receipt</Button>
            </span>
          </Tooltip>
          <Button variant="outlined" size="small" onClick={() => navigate("/pharmacy/medicines")}>Medicine Master</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {deepLinkBanner ? <Alert severity="info" onClose={() => setDeepLinkBanner(null)}>{deepLinkBanner}</Alert> : null}
      {!canManageInventory ? <Alert severity="info">Read-only inventory access is active for this role. Stock creation, adjustment, transfer, return, and write-off posting are hidden or disabled.</Alert> : null}

      <Card>
        <CardContent>
          <Tabs value={tab} onChange={(_, next) => setTab(next)} variant="scrollable" scrollButtons="auto">
            {TABS.map((item) => (
              <Tab key={item.value} value={item.value} label={item.label} />
            ))}
          </Tabs>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
          <CircularProgress />
        </Box>
      ) : null}

      <WorkflowGuide
        title="Inventory Workflow"
        subtitle="Inventory updates automatically after GRN. Use procurement for planned purchases or direct goods receipt for exception stock."
        steps={[
          { label: "Medicine Master" },
          { label: "Receive via Procurement", helper: "or Direct Goods Receipt", tone: "primary" },
          { label: "Inventory Batch" },
          { label: "Dispensing / POS" },
          { label: "Stock Movement" },
          { label: "Reconciliation", tone: "info" },
        ]}
      />

      {tab === "stocks" ? (
        <Grid container spacing={2}>
          <Grid size={12}>
            <Grid container spacing={1.5}>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Stock batches" value={stocks.length} helper={`${paidLocationsLabel} workspace`} />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Low stock" value={lowStock.length} tone={lowStock.length ? "error" : "success"} helper="Needs replenishment" />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Expiring soon" value={expiringSoonCount} tone={expiringSoonCount ? "warning" : "success"} helper="Within 30 days" />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Total quantity" value={totalQuantity} helper={`Est. value ${formatCurrency(estimatedStockValue)}`} />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Today movements" value={todayMovementCount} helper="Transactions posted today" />
              </Grid>
            </Grid>
          </Grid>

          <Grid size={12}>
            <CompactFilterCard
              title="Stock operations"
              subtitle="Inventory manages batch-wise physical stock. Medicine catalogue is maintained in Medicine Master."
              actions={(
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/reconciliation")}>Open Reconciliation</Button>
                </Stack>
              )}
            >
              {medicines.length === 0 ? (
                <CompactEmptyState
                  title="Add your first medicine to start building the catalogue."
                  subtitle="Create the medicine master first, then add stock through procurement or direct goods receipt."
                  action={(
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>
                      <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Upload CSV</Button>
                    </Stack>
                  )}
                />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Use this workspace for batch numbers, expiry, quantity, thresholds, stock adjustments, and transfers.
                </Typography>
              )}
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 4.2 }}>
            <Stack spacing={1.25} ref={addStockBatchRef}>
              <CompactFilterCard
                title="How stock reaches inventory"
                subtitle="Single source of truth: receipts create inventory batches automatically."
              >
                <Stack spacing={0.5}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    Purchase Order → Invoice → Goods Receipt
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Planned supplier purchasing updates stock once GRN is posted.
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, pt: 0.5 }}>
                    Direct Goods Receipt
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Opening stock, emergency purchase, donation, and migration can be receipted without a purchase order.
                  </Typography>
                </Stack>
              </CompactFilterCard>

              <Accordion expanded={canManageInventory && stockActionPanel === "add"} onChange={(_, expanded) => setStockActionPanel(expanded ? "add" : null)} disableGutters disabled={!canManageInventory} sx={compactAccordionSx}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      {selectedStockId ? "Edit Stock Batch" : "Batch management"}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Batch metadata can be edited here. New batches are created automatically from GRN.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
                  <Stack spacing={1}>
                    <Alert severity="info" sx={{ py: 0 }}>
                      New inventory batches are created by GRN. Use this workspace to adjust batch metadata, thresholds, and status.
                    </Alert>
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <Autocomplete<MedicineAutocompleteOption, false, false, false>
                          options={medicineAutocompleteOptions}
                          value={selectedMedicineOption}
                          inputValue={medicineSearchInput}
                          onInputChange={(_, value, reason) => {
                            if (reason !== "reset") {
                              setMedicineSearchInput(value);
                            }
                          }}
                          filterOptions={(options, state) => {
                            const term = state.inputValue.trim().toLowerCase();
                            const filtered = !term
                              ? options
                              : options.filter((option) => {
                                  if (option.kind !== "existing") return false;
                                  const medicine = option.medicine;
                                  return [
                                    medicine.medicineName,
                                    medicine.genericName,
                                    medicine.brandName,
                                    medicine.barcode,
                                    medicine.qrCode,
                                    medicine.externalCode,
                                  ]
                                    .filter(Boolean)
                                    .some((value) => String(value).toLowerCase().includes(term));
                                });
                            const hasExactMatch = options.some((option) => option.kind === "existing" && option.medicine.medicineName.toLowerCase() === term);
                            if (term && !hasExactMatch) {
                              filtered.push({ kind: "create", inputValue: state.inputValue });
                            }
                            return filtered.slice(0, 20);
                          }}
                          onChange={(_, value) => {
                            if (!value) {
                              setStockForm((current) => ({ ...current, medicineId: "" }));
                              return;
                            }
                            if (value.kind === "create") {
                              openQuickCreateMedicine(value.inputValue);
                              return;
                            }
                            setStockForm((current) => ({ ...current, medicineId: value.medicine.id }));
                            setMedicineSearchInput(value.medicine.medicineName);
                          }}
                          getOptionLabel={(option) => option.kind === "create" ? `Create new medicine: ${option.inputValue}` : option.medicine.medicineName}
                          isOptionEqualToValue={(option, value) => {
                            if (option.kind === "create" && value.kind === "create") {
                              return option.inputValue === value.inputValue;
                            }
                            if (option.kind === "existing" && value.kind === "existing") {
                              return option.medicine.id === value.medicine.id;
                            }
                            return false;
                          }}
                          noOptionsText="No medicines found"
                          renderOption={(props, option) => (
                            <Box component="li" {...props}>
                              {option.kind === "create" ? (
                                <Stack spacing={0.2}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    Create new medicine: {option.inputValue}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    Quick add to Medicine Master and continue this stock batch.
                                  </Typography>
                                </Stack>
                              ) : (
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    {option.medicine.medicineName}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {[option.medicine.genericName, option.medicine.brandName].filter(Boolean).join(" / ") || "No generic or brand"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {[option.medicine.medicineType, option.medicine.strength, option.medicine.defaultPrice != null ? formatCurrency(option.medicine.defaultPrice) : null].filter(Boolean).join(" • ")}
                                  </Typography>
                                </Stack>
                              )}
                            </Box>
                          )}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              id="inventory-stock-medicine"
                              size="small"
                              label={<FieldHelpTooltip label="Medicine" required helpText={getFieldHelpText("medicine")} />}
                              placeholder="Search by name, brand, generic, barcode, QR, or code"
                              helperText="Search medicine name, generic, brand, barcode, QR code, or external code."
                              required
                              error={Boolean(stockFieldErrors.medicineId)}
                              inputProps={{ ...params.inputProps, "aria-required": true }}
                              FormHelperTextProps={{ sx: { minHeight: 20 } }}
                            />
                          )}
                          disabled={editingExistingBatch}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Button fullWidth variant="outlined" startIcon={<MedicationRoundedIcon fontSize="small" />} sx={{ height: 40 }} onClick={() => openQuickCreateMedicine(medicineSearchInput)} disabled={!canManageInventory || saving}>
                          Quick Add Medicine
                        </Button>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="stock-location-label">
                            <FieldHelpTooltip label="Location" required helpText={getFieldHelpText("location")} />
                          </InputLabel>
                          <Select
                            labelId="stock-location-label"
                            label="Location"
                            value={stockForm.locationId}
                            onChange={(e) => setStockForm((current) => ({ ...current, locationId: String(e.target.value) }))}
                            required
                            disabled={editingExistingBatch && !canEditBatchLocation}
                            error={Boolean(stockFieldErrors.locationId)}
                            inputProps={{ id: "inventory-stock-location", "aria-required": true }}
                          >
                            <MenuItem value="">Select location</MenuItem>
                            {locations.map((location) => (
                              <MenuItem key={location.id} value={location.id}>
                                {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-batch"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="Batch number" required helpText={getFieldHelpText("batchNumber")} />}
                          value={stockForm.batchNumber}
                          onChange={(e) => setStockForm((current) => ({ ...current, batchNumber: e.target.value }))}
                          required
                          InputProps={{ readOnly: editingExistingBatch }}
                          error={Boolean(stockFieldErrors.batchNumber)}
                          helperText={stockFieldErrors.batchNumber || (editingExistingBatch ? "Batch number is controlled by the receiving document and cannot be edited here." : getFieldHelpText("batchNumber"))}
                          inputProps={{ "aria-required": true, maxLength: 30 }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          label="Purchase reference"
                          value={stockForm.purchaseReferenceNumber}
                          onChange={(e) => setStockForm((current) => ({ ...current, purchaseReferenceNumber: e.target.value }))}
                          InputProps={{ readOnly: editingExistingBatch }}
                          helperText={editingExistingBatch ? "Purchase reference is captured by GRN or Direct Goods Receipt and is read-only here." : "Reference from GRN or Direct Goods Receipt."}
                        />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Grid container spacing={1.25}>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label={<FieldHelpTooltip label="Barcode" helpText={getFieldHelpText("barcode")} />} value={stockForm.barcode} onChange={(value) => setStockForm((current) => ({ ...current, barcode: value }))} placeholder="Scan or enter barcode" helperText={stockFieldErrors.barcode || getFieldHelpText("barcode")} error={Boolean(stockFieldErrors.barcode)} />
                          </Grid>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label={<FieldHelpTooltip label="QR code" helpText={getFieldHelpText("qrCode")} />} value={stockForm.qrCode} onChange={(value) => setStockForm((current) => ({ ...current, qrCode: value }))} placeholder="Scan or enter QR code" error={Boolean(stockFieldErrors.qrCode)} helperText={stockFieldErrors.qrCode || getFieldHelpText("qrCode")} />
                          </Grid>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label={<FieldHelpTooltip label="External code" helpText={getFieldHelpText("externalCode")} />} value={stockForm.externalCode} onChange={(value) => setStockForm((current) => ({ ...current, externalCode: value }))} placeholder="Scan or enter code" error={Boolean(stockFieldErrors.externalCode)} helperText={stockFieldErrors.externalCode || getFieldHelpText("externalCode")} />
                          </Grid>
                        </Grid>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-expiry"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="Expiry date" required helpText={getFieldHelpText("expiryDate")} />}
                          type="date"
                          value={stockForm.expiryDate}
                          onChange={(e) => setStockForm((current) => ({ ...current, expiryDate: e.target.value }))}
                          InputLabelProps={{ shrink: true }}
                          InputProps={{ readOnly: editingExistingBatch }}
                          required
                          error={Boolean(stockFieldErrors.expiryDate)}
                          helperText={stockFieldErrors.expiryDate || (editingExistingBatch ? "Expiry is captured at receipt time and should be corrected through the receiving workflow if required." : getFieldHelpText("expiryDate"))}
                          inputProps={{ "aria-required": true }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-quantity"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="Quantity on hand" required helpText={getFieldHelpText("quantityOnHand")} />}
                          value={stockForm.quantityOnHand}
                          onChange={(e) => setStockForm((current) => ({ ...current, quantityOnHand: e.target.value }))}
                          InputProps={{ readOnly: editingExistingBatch }}
                          required
                          error={Boolean(stockFieldErrors.quantityOnHand)}
                          helperText={stockFieldErrors.quantityOnHand || (editingExistingBatch ? "Quantity is controlled by GRN, Direct Goods Receipt, Stock Adjustment, or Physical Count adjustment." : getFieldHelpText("quantityOnHand"))}
                          inputProps={{ "aria-required": true, inputMode: "numeric" }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-reorder"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="Reorder level" helpText={getFieldHelpText("reorderLevel")} />}
                          value={stockForm.lowStockThreshold}
                          onChange={(e) => setStockForm((current) => ({ ...current, lowStockThreshold: e.target.value }))}
                          error={Boolean(stockFieldErrors.lowStockThreshold)}
                          helperText={stockFieldErrors.lowStockThreshold || "Editable in Inventory. Used for low-stock alerts and reorder planning."}
                          inputProps={{ inputMode: "numeric" }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="stock-active-label"><RequiredLabel text="Status" required /></InputLabel>
                          <Select id="inventory-stock-status" labelId="stock-active-label" label="Status" value={stockForm.active ? "true" : "false"} onChange={(e) => setStockForm((current) => ({ ...current, active: String(e.target.value) === "true" }))} required inputProps={{ "aria-required": true }}>
                            <MenuItem value="true">Active</MenuItem>
                            <MenuItem value="false">Inactive</MenuItem>
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-purchase-rate"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="Purchase rate" helpText={getFieldHelpText("purchaseRate")} />}
                          value={stockForm.unitCost}
                          onChange={(e) => setStockForm((current) => ({ ...current, unitCost: e.target.value }))}
                          InputProps={{ readOnly: editingExistingBatch }}
                          error={Boolean(stockFieldErrors.unitCost)}
                          helperText={stockFieldErrors.unitCost || (editingExistingBatch ? "Purchase rate is captured by GRN or Direct Goods Receipt and is read-only here." : getFieldHelpText("purchaseRate"))}
                          inputProps={{ inputMode: "decimal" }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          id="inventory-stock-mrp"
                          size="small"
                          fullWidth
                          label={<FieldHelpTooltip label="MRP" helpText={getFieldHelpText("mrp")} />}
                          value={stockForm.sellingPrice}
                          onChange={(e) => setStockForm((current) => ({ ...current, sellingPrice: e.target.value }))}
                          error={Boolean(stockFieldErrors.sellingPrice)}
                          helperText={stockFieldErrors.sellingPrice || "Editable in Inventory. GRN captures purchase cost; MRP can be maintained here for POS/billing."}
                          inputProps={{ inputMode: "decimal" }}
                        />
                      </Grid>
                    </Grid>
                    <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Button
                        onClick={async () => {
                          await saveStock();
                        }}
                        disabled={!canManageInventory || saving || (editingExistingBatch ? !metadataDirty : false)}
                      >
                        {editingExistingBatch ? "Save Batch Metadata" : "Open Direct Goods Receipt"}
                      </Button>
                      <Button
                        variant="text"
                        onClick={() => {
                          setStockForm(emptyStockForm());
                          setSelectedStockId(null);
                          setMedicineSearchInput("");
                        }}
                      >
                        Clear
                      </Button>
                      {currentStock ? <Chip size="small" label={`${currentStock.medicineName} • ${currentStock.batchNumber || "No batch"}`} variant="outlined" /> : null}
                    </Box>
                    {editingExistingBatch ? (
                      <Alert severity="info" sx={{ py: 0 }}>
                        GRN owns receiving facts. Inventory owns editable metadata such as reorder level, MRP, status, barcode, QR code, and external code.
                      </Alert>
                    ) : null}
                    {!selectedStockId ? (
                      <Alert severity="warning" sx={{ py: 0 }}>
                        Batch creation is handled by Goods Receipt. Select an existing batch to edit, or use Direct Goods Receipt for new inventory.
                      </Alert>
                    ) : null}
                  </Stack>
                </AccordionDetails>
              </Accordion>

              <Accordion expanded={canManageInventory && stockActionPanel === "transaction"} onChange={(_, expanded) => setStockActionPanel(expanded ? "transaction" : null)} disableGutters disabled={!canManageInventory} sx={compactAccordionSx}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Stock adjustment
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Post opening, purchase, adjustment, return, and cancellation transactions without leaving the stock workspace.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
                  <Box sx={compactFormSx}>
                  <Grid container spacing={1}>
                    <Grid size={12}>
                      <Autocomplete
                        freeSolo
                        options={transactionSearchResults}
                        size="small"
                        value={selectedTransactionStock}
                        inputValue={transactionSearchInput}
                        open={transactionSearchInput.trim().length >= 2}
                        isOptionEqualToValue={(option, value) => option.id === value.id}
                        filterOptions={(options) => options.slice(0, 15)}
                        onInputChange={(_, value, reason) => {
                          setTransactionSearchInput(value);
                          if (reason === "clear") {
                            setTransactionForm((current) => ({ ...current, medicineId: "", stockBatchId: "", quantity: "", reason: "", referenceType: "", referenceId: "", notes: "" }));
                          }
                        }}
                        onChange={(_, stock) => {
                          if (!stock || typeof stock === "string") {
                            setTransactionForm((current) => ({ ...current, medicineId: "", stockBatchId: "" }));
                            return;
                          }
                          setTransactionSearchInput([
                            stock.medicineName,
                            stock.batchNumber || "No batch",
                            stock.locationName || "Main Pharmacy",
                          ].join(" • "));
                          setTransactionForm((current) => ({
                            ...current,
                            medicineId: stock.medicineId,
                            stockBatchId: stock.id,
                          }));
                        }}
                        getOptionLabel={(option) => typeof option === "string"
                          ? option
                          : [option.medicineName, option.batchNumber || "No batch", option.locationName || "Main Pharmacy"].join(" • ")}
                        noOptionsText={transactionSearchInput.trim().length < 2 ? "Type at least 2 characters to search inventory." : "No inventory item found."}
                        ListboxProps={{
                          style: { maxHeight: 360, overflowY: "auto" },
                        }}
                        renderOption={(props, stock) => {
                          const setupMissing = batchSetupMissing(stock);
                          return (
                            <Box component="li" {...props}>
                              <Stack spacing={0.25} sx={{ width: "100%" }}>
                                <Stack direction="row" justifyContent="space-between" spacing={1} useFlexGap flexWrap="wrap">
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{stock.medicineName}</Typography>
                                  <Chip size="small" label={`Qty ${stock.quantityOnHand}`} variant="outlined" />
                                </Stack>
                                <Typography variant="caption" color="text.secondary">
                                  Batch {stock.batchNumber || "No batch"} • {stock.locationName || "Main Pharmacy"} • Expiry {stock.expiryDate || "No expiry"}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {setupMissing.length === 0 ? "Setup complete" : `Setup incomplete • ${setupMissing.join(" • ")}`}
                                </Typography>
                              </Stack>
                            </Box>
                          );
                        }}
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            id="inventory-transaction-medicine"
                            label={<RequiredLabel text="Find Inventory Item" required />}
                            placeholder="Search medicine, generic, batch, barcode, QR, external code, GRN"
                            error={Boolean(transactionFieldErrors.medicineId || transactionFieldErrors.stockBatchId)}
                            helperText={transactionFieldErrors.medicineId || transactionFieldErrors.stockBatchId || "Search by medicine, generic name, batch, barcode, QR code, external code, or reference."}
                          />
                        )}
                      />
                    </Grid>
                    {transactionBatchOptions.length > 1 ? (
                      <Grid size={12}>
                        <Stack spacing={0.75}>
                          <Typography variant="caption" color="text.secondary">Select batch</Typography>
                          <Grid container spacing={1}>
                            {transactionBatchOptions.map((stock) => {
                              const selected = stock.id === transactionForm.stockBatchId;
                              return (
                                <Grid key={stock.id} size={{ xs: 12, md: 6 }}>
                                  <Card variant="outlined" sx={{ borderColor: selected ? "primary.main" : "divider", boxShadow: selected ? 1 : 0 }}>
                                    <CardContent sx={compactCardContentSx}>
                                      <Stack spacing={0.4}>
                                        <Stack direction="row" justifyContent="space-between" spacing={1}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{stock.batchNumber || "No batch"}</Typography>
                                          <Chip size="small" label={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "Expired" : stock.active ? "Active" : "Inactive"} color={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "error" : stock.active ? statusColor(stock.quantityOnHand, stock.lowStockThreshold) : "default"} />
                                        </Stack>
                                        <Typography variant="caption" color="text.secondary">{stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}</Typography>
                                        <Typography variant="caption" color="text.secondary">Expiry {stock.expiryDate || "No expiry"} • {stock.purchaseReferenceNumber || "No GRN/reference"}</Typography>
                                        <Button size="small" variant={selected ? "contained" : "outlined"} onClick={() => setTransactionForm((current) => ({ ...current, medicineId: stock.medicineId, stockBatchId: stock.id }))}>
                                          {selected ? "Selected" : "Select Batch"}
                                        </Button>
                                      </Stack>
                                    </CardContent>
                                  </Card>
                                </Grid>
                              );
                            })}
                          </Grid>
                        </Stack>
                      </Grid>
                    ) : null}
                    {!selectedTransactionStock && transactionForm.medicineId && transactionBatchOptions.length === 0 ? (
                      <Grid size={12}>
                        <Alert severity="warning" sx={{ py: 0 }}>
                          No available batch for this medicine.
                        </Alert>
                      </Grid>
                    ) : null}
                    {selectedTransactionStock ? (
                      <Grid size={12}>
                        <Card variant="outlined">
                          <CardContent sx={compactCardContentSx}>
                            <Grid container spacing={1}>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Medicine" value={selectedTransactionStock.medicineName} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Batch" value={selectedTransactionStock.batchNumber || "No batch"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Location" value={selectedTransactionStock.locationName || "Main Pharmacy"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Current Qty" value={String(selectedTransactionStock.quantityOnHand)} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Expiry" value={selectedTransactionStock.expiryDate || "No expiry"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Operational Status" value={selectedTransactionStock.expiryDate && daysUntil(selectedTransactionStock.expiryDate) < 0 ? "Expired" : selectedTransactionStock.active ? "Active" : "Inactive"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Setup Status" value={transactionSetupMissing.length === 0 ? "Complete" : "Incomplete"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Purchase Reference / GRN" value={selectedTransactionStock.purchaseReferenceNumber || "-"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="MRP" value={selectedTransactionStock.sellingPrice != null ? String(selectedTransactionStock.sellingPrice) : "-"} InputProps={{ readOnly: true }} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Reorder Level" value={selectedTransactionStock.lowStockThreshold != null ? String(selectedTransactionStock.lowStockThreshold) : "-"} InputProps={{ readOnly: true }} /></Grid>
                            </Grid>
                            {transactionSetupMissing.length ? (
                              <Alert severity="warning" sx={{ mt: 1, py: 0 }}>
                                Commercial setup incomplete. Stock can be adjusted, but POS sale remains blocked until setup is complete.
                              </Alert>
                            ) : null}
                          </CardContent>
                        </Card>
                      </Grid>
                    ) : null}
                    {selectedTransactionStock ? (
                      <>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transaction-type-label">Adjustment Type</InputLabel>
                        <Select labelId="transaction-type-label" label="Adjustment Type" value={transactionForm.transactionType} onChange={(e) => setTransactionForm((current) => ({ ...current, transactionType: String(e.target.value) as InventoryTransactionType }))}>
                          {STOCK_ADJUSTMENT_OPTIONS.map((type) => (
                            <MenuItem key={type.value} value={type.value}>
                              {type.label}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField id="inventory-transaction-quantity" size="small" fullWidth label={<RequiredLabel text="Quantity" required />} value={transactionForm.quantity} onChange={(e) => setTransactionForm((current) => ({ ...current, quantity: e.target.value }))} required error={Boolean(transactionFieldErrors.quantity)} helperText={transactionFieldErrors.quantity || "Positive whole number."} inputProps={{ min: 1, step: 1, "aria-required": true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField id="inventory-transaction-reason" size="small" fullWidth label={<RequiredLabel text="Reason" required />} value={transactionForm.reason} onChange={(e) => setTransactionForm((current) => ({ ...current, reason: e.target.value }))} required error={Boolean(transactionFieldErrors.notes)} helperText={transactionFieldErrors.notes || "Required for audit trail."} inputProps={{ "aria-required": true, maxLength: 60 }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField id="inventory-transaction-reference-type" size="small" fullWidth label="Reference type" value={transactionForm.referenceType} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceType: e.target.value }))} error={Boolean(transactionFieldErrors.referenceType)} helperText={transactionFieldErrors.referenceType || "Optional reference type."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField id="inventory-transaction-reference-id" size="small" fullWidth label="Reference ID" value={transactionForm.referenceId} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceId: e.target.value }))} error={Boolean(transactionFieldErrors.referenceId)} helperText={transactionFieldErrors.referenceId || "Optional UUID reference identifier."} />
                    </Grid>
                    <Grid size={12}>
                      <TextField id="inventory-transaction-notes" size="small" fullWidth label="Notes" value={transactionForm.notes} onChange={(e) => setTransactionForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} helperText="Optional operational note." />
                    </Grid>
                    <Grid size={12}>
                      <Alert severity="info" sx={{ py: 0 }}>
                        Transfer uses the dedicated Transfer stock workflow because destination location is required.
                      </Alert>
                    </Grid>
                    {transactionAdjustmentNextQty !== null ? (
                      <Grid size={12}>
                        <Card variant="outlined">
                          <CardContent sx={compactCardContentSx}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Review before save</Typography>
                              <Typography variant="body2">You are about to {stockAdjustmentIsDecrease(transactionForm.transactionType) ? "decrease" : "increase"} stock.</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {selectedTransactionStock.medicineName} • Batch {selectedTransactionStock.batchNumber || "No batch"} • Current Qty {selectedTransactionStock.quantityOnHand} • Adjustment Qty {transactionForm.quantity || "0"} • New Qty {transactionAdjustmentNextQty} • Reason {transactionForm.reason || "-"}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    ) : null}
                    <Grid size={12}>
                      <Button onClick={() => void saveTransaction()} disabled={!canManageInventory || saving || !selectedTransactionStock}>
                        Confirm & Save
                      </Button>
                    </Grid>
                      </>
                    ) : null}
                  </Grid>
                  </Box>
                </AccordionDetails>
              </Accordion>
              <Accordion expanded={canManageInventory && stockActionPanel === "transfer"} onChange={(_, expanded) => setStockActionPanel(expanded ? "transfer" : null)} disableGutters disabled={!canManageInventory} sx={compactAccordionSx}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Transfer stock
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Move available stock between locations with a short reason and keep the transaction log in sync.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
                  <Box sx={compactFormSx}>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-medicine-label">Medicine</InputLabel>
                        <Select labelId="transfer-medicine-label" label="Medicine" value={transferForm.medicineId} onChange={(e) => setTransferForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}>
                          <MenuItem value="">Select medicine</MenuItem>
                          {medicines.map((medicine) => (
                            <MenuItem key={medicine.id} value={medicine.id}>
                              {medicine.medicineName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-stock-label">Batch</InputLabel>
                        <Select labelId="transfer-stock-label" label="Batch" value={transferForm.stockBatchId} onChange={(e) => setTransferForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}>
                          <MenuItem value="">Select batch</MenuItem>
                          {filteredTransferStocks.map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {(stock.batchNumber || "No batch")} • {stock.locationName || "Main Pharmacy"}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-from-label">From location</InputLabel>
                        <Select labelId="transfer-from-label" label="From location" value={transferForm.fromLocationId} onChange={(e) => setTransferForm((current) => ({ ...current, fromLocationId: String(e.target.value) }))}>
                          <MenuItem value="">Select location</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-to-label">To location</InputLabel>
                        <Select labelId="transfer-to-label" label="To location" value={transferForm.toLocationId} onChange={(e) => setTransferForm((current) => ({ ...current, toLocationId: String(e.target.value) }))}>
                          <MenuItem value="">Select location</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Quantity" value={transferForm.quantity} onChange={(e) => setTransferForm((current) => ({ ...current, quantity: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Reason" value={transferForm.reason} onChange={(e) => setTransferForm((current) => ({ ...current, reason: e.target.value }))} />
                    </Grid>
                    <Grid size={12}>
                      <Button
                        variant="outlined"
                        disabled={!canManageInventory || saving}
                        onClick={async () => {
                          if (!auth.accessToken || !auth.tenantId) return;
                          if (!transferForm.medicineId || !transferForm.fromLocationId || !transferForm.toLocationId || !transferForm.quantity.trim()) {
                            setError("Select medicine, source location, destination location, and quantity.");
                            return;
                          }
                          setSaving(true);
                          setError(null);
                          try {
                            await transferInventoryStock(auth.accessToken, auth.tenantId, {
                              medicineId: transferForm.medicineId,
                              stockBatchId: transferForm.stockBatchId || null,
                              fromLocationId: transferForm.fromLocationId,
                              toLocationId: transferForm.toLocationId,
                              quantity: Number(transferForm.quantity),
                              reason: transferForm.reason.trim() || null,
                            });
                            setTransferForm({ medicineId: "", stockBatchId: "", fromLocationId: "", toLocationId: "", quantity: "", reason: "" });
                            await loadAll();
                            setSuccess("Stock transfer recorded");
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Failed to transfer stock");
                          } finally {
                            setSaving(false);
                          }
                        }}
                      >
                        Transfer
                      </Button>
                    </Grid>
                  </Grid>
                  </Box>
                </AccordionDetails>
              </Accordion>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.8 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard
                title="Stock workspace"
                subtitle="Batches, quantities, expiry, and activity in one compact view."
              >
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl size="small" fullWidth>
                      <InputLabel id="stock-filter-location-label">Location</InputLabel>
                      <Select
                        labelId="stock-filter-location-label"
                        label="Location"
                        value={selectedLocationId || ""}
                        onChange={(e) => setSelectedLocationId(String(e.target.value) || null)}
                      >
                        <MenuItem value="">All locations</MenuItem>
                        {locations.map((location) => (
                          <MenuItem key={location.id} value={location.id}>
                            {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 8 }}>
                    <TextField
                      size="small"
                      fullWidth
                      label="Scan or enter code"
                      value={stockSearch}
                      onChange={(e) => setStockSearch(e.target.value)}
                      placeholder="barcode / QR / batch / reference"
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton size="small" onClick={() => setStockSearchScannerOpen(true)} aria-label="Scan stock code">
                              <CameraAltRoundedIcon fontSize="small" />
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  </Grid>
                </Grid>
              </CompactFilterCard>
              <CodeScannerDialog
                open={stockSearchScannerOpen}
                title="Scan stock code"
                description="Scan a barcode or QR code to fill the stock workspace search field."
                value={stockSearch}
                onClose={() => setStockSearchScannerOpen(false)}
                onDetected={(code) => setStockSearch(code)}
                manualLabel="Enter stock code"
                manualPlaceholder="barcode / QR / batch / reference"
              />

              <OperationalTableCard
                title="Stock list / batches"
                countLabel={`${visibleStocks.length} visible batches`}
                emptyState={stocks.length === 0 ? (
                  <CompactEmptyState
                    title="No inventory available."
                    subtitle="Choose how you would like to add stock."
                    action={(
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="center">
                        <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
                        <Button size="small" variant="outlined" onClick={openDirectGoodsReceipt}>Direct Goods Receipt</Button>
                      </Stack>
                    )}
                  />
                ) : visibleStocks.length === 0 ? (
                  <CompactEmptyState
                    title="No matching stock batches."
                    subtitle="Adjust the filter or search to show the batches already in inventory."
                    action={<Button size="small" onClick={() => { setSelectedLocationId(""); setStockSearch(""); }}>Clear filters</Button>}
                  />
                ) : undefined}
              >
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Location</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell>Expiry</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">Reorder Level</TableCell>
                      <TableCell>Setup Status</TableCell>
                      <TableCell>Operational Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visibleStocks.map((stock) => {
                      const setupMissing = batchSetupMissing(stock);
                      const setupComplete = setupMissing.length === 0;
                      return (
                        <TableRow key={stock.id} hover selected={stock.id === selectedStockId} sx={{ "& td": { py: 0.85, verticalAlign: "top" } }}>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {stock.medicineName}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {stock.medicineType}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {stock.barcode || stock.externalCode || stock.qrCode || stock.purchaseReferenceNumber || "-"}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{stock.locationName || "Main Pharmacy"}</TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{stock.batchNumber || "-"}</Typography>
                              {stock.purchaseReferenceNumber ? (
                                <Button size="small" sx={{ px: 0, minWidth: 0, justifyContent: "flex-start" }} onClick={() => navigate(`/pharmacy/procure?workspace=goods-receipt&receipt=${encodeURIComponent(stock.purchaseReferenceNumber || "")}`)}>
                                  {stock.purchaseReferenceNumber}
                                </Button>
                              ) : (
                                <Typography variant="caption" color="text.secondary">-</Typography>
                              )}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.3}>
                              <Chip size="small" label={expiryState(stock.expiryDate).label} color={expiryState(stock.expiryDate).color} />
                              <Typography variant="caption" color="text.secondary">{stock.expiryDate || "No expiry date"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell align="right">{stock.quantityOnHand}</TableCell>
                          <TableCell align="right">{stock.lowStockThreshold ?? "-"}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={setupComplete ? "Complete" : "Incomplete"}
                              color={setupComplete ? "success" : "warning"}
                            />
                            {!setupComplete ? (
                              <Typography variant="caption" display="block" color="text.secondary">
                                {setupMissing.join(" • ")}
                              </Typography>
                            ) : null}
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "EXPIRED" : stock.active ? "Active" : "Inactive"}
                              color={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "error" : stock.active ? statusColor(stock.quantityOnHand, stock.lowStockThreshold) : "default"}
                            />
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                              <Button size="small" disabled={!canManageInventory} onClick={() => { editStock(stock); setStockActionPanel("add"); }}>
                                Edit Batch
                              </Button>
                              <Button size="small" variant="outlined" disabled={!canManageInventory} onClick={() => startStockAdjustment(stock)}>
                                Adjust Stock
                              </Button>
                              <Tooltip title="Quick Count remains available in the Physical Count tab.">
                                <span><Button size="small" variant="outlined" disabled>Quick Count</Button></span>
                              </Tooltip>
                              <Tooltip title="Use the dedicated transfer workflow below.">
                                <span><Button size="small" variant="outlined" disabled>Transfer</Button></span>
                              </Tooltip>
                              <Tooltip title="Use Returns & Write-offs for controlled write-off workflow.">
                                <span><Button size="small" variant="outlined" disabled>Write Off</Button></span>
                              </Tooltip>
                              <Tooltip title="Batch-level transaction drill-down will be added after stock movement filtering is wired.">
                                <span><Button size="small" variant="outlined" disabled>View Transactions</Button></span>
                              </Tooltip>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </OperationalTableCard>

              <OperationalTableCard
                title="Batch Setup Queue"
                subtitle="GRN creates received stock. Inventory completes commercial setup for POS readiness by maintaining MRP and reorder level."
                countLabel={`${incompleteSetupStocks.length} incomplete`}
                actions={(
                  <Button size="small" variant="contained" disabled={!canManageInventory || saving || !incompleteSetupStocks.some((stock) => {
                    const draft = setupQueueEdits[stock.id] ?? { sellingPrice: stock.sellingPrice?.toString() || "", lowStockThreshold: stock.lowStockThreshold?.toString() || "" };
                    return draft.sellingPrice.trim() !== (stock.sellingPrice?.toString() || "")
                      || draft.lowStockThreshold.trim() !== (stock.lowStockThreshold?.toString() || "");
                  })} onClick={() => void saveAllSetupRows()}>
                    Save All
                  </Button>
                )}
                emptyState={incompleteSetupStocks.length === 0 ? (
                  <CompactEmptyState
                    title="All visible batches are commercially ready."
                    subtitle="MRP and reorder level are already maintained for the current inventory filters."
                  />
                ) : undefined}
              >
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell>Location</TableCell>
                      <TableCell>Expiry</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">MRP</TableCell>
                      <TableCell align="right">Reorder Level</TableCell>
                      <TableCell>Setup Status</TableCell>
                      <TableCell align="right">Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {incompleteSetupStocks.map((stock) => {
                      const draft = setupQueueEdits[stock.id] ?? {
                        sellingPrice: stock.sellingPrice?.toString() || "",
                        lowStockThreshold: stock.lowStockThreshold?.toString() || "",
                      };
                      const draftMissing = batchSetupMissingFromValues(parseOptionalNumberInput(draft.sellingPrice), parseOptionalNumberInput(draft.lowStockThreshold));
                      const dirty = draft.sellingPrice.trim() !== (stock.sellingPrice?.toString() || "")
                        || draft.lowStockThreshold.trim() !== (stock.lowStockThreshold?.toString() || "");
                      return (
                        <TableRow key={stock.id} sx={{ "& td": { verticalAlign: "top" } }}>
                          <TableCell>{stock.medicineName}</TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{stock.batchNumber || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {stock.purchaseReferenceNumber || "No purchase reference"} • Rate {stock.unitCost != null ? formatCurrency(stock.unitCost) : "-"}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{stock.locationName || "Main Pharmacy"}</TableCell>
                          <TableCell>{stock.expiryDate || "-"}</TableCell>
                          <TableCell align="right">{stock.quantityOnHand}</TableCell>
                          <TableCell align="right">
                            <TextField
                              size="small"
                              value={draft.sellingPrice}
                              onChange={(event) => setSetupQueueEdits((current) => ({
                                ...current,
                                [stock.id]: {
                                  sellingPrice: event.target.value,
                                  lowStockThreshold: current[stock.id]?.lowStockThreshold ?? draft.lowStockThreshold,
                                },
                              }))}
                              inputProps={{ inputMode: "decimal" }}
                              placeholder="MRP"
                              disabled={!canManageInventory || saving}
                            />
                          </TableCell>
                          <TableCell align="right">
                            <TextField
                              size="small"
                              value={draft.lowStockThreshold}
                              onChange={(event) => setSetupQueueEdits((current) => ({
                                ...current,
                                [stock.id]: {
                                  sellingPrice: current[stock.id]?.sellingPrice ?? draft.sellingPrice,
                                  lowStockThreshold: event.target.value,
                                },
                              }))}
                              inputProps={{ inputMode: "numeric" }}
                              placeholder="Reorder"
                              disabled={!canManageInventory || saving}
                            />
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={draftMissing.length === 0 ? "Complete" : "Incomplete"} color={draftMissing.length === 0 ? "success" : "warning"} />
                            {draftMissing.length ? (
                              <Typography variant="caption" display="block" color="text.secondary">
                                {draftMissing.join(" • ")}
                              </Typography>
                            ) : null}
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={0.75} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                              <Button size="small" disabled={!canManageInventory || saving || !dirty} onClick={() => void saveSetupRow(stock.id)}>
                                Save Row
                              </Button>
                              <Button size="small" variant="outlined" disabled={!canManageInventory} onClick={() => { editStock(stock); setStockActionPanel("add"); }}>
                                Edit Details
                              </Button>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </OperationalTableCard>

              <OperationalTableCard
                title="Inventory transactions"
                countLabel={`${transactions.length} logged`}
                emptyState={transactions.length === 0 ? (
                  <CompactEmptyState
                    title="No inventory movements yet."
                    subtitle="Adjustments, purchases, dispenses, returns, and transfers will appear here once posted."
                  />
                ) : undefined}
              >
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell align="right">Before</TableCell>
                      <TableCell align="right">After</TableCell>
                      <TableCell align="right">Quantity</TableCell>
                      <TableCell>Reference</TableCell>
                      <TableCell>Adjusted by</TableCell>
                      <TableCell>Notes</TableCell>
                      <TableCell>Created</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {transactions.map((transaction) => (
                      <TableRow key={transaction.id} sx={{ "& td": { py: 0.8, verticalAlign: "top" } }}>
                        <TableCell>{medicineById.get(transaction.medicineId)?.medicineName || transaction.medicineId}</TableCell>
                        <TableCell>{transactionLabel(transaction.transactionType)}</TableCell>
                        <TableCell align="right">{transaction.beforeQuantity ?? "-"}</TableCell>
                        <TableCell align="right">{transaction.afterQuantity ?? "-"}</TableCell>
                        <TableCell align="right">{transaction.quantity}</TableCell>
                        <TableCell>
                          <Stack spacing={0.2}>
                            <Typography variant="caption" color="text.secondary">{transaction.referenceType || "-"}</Typography>
                            {transaction.businessReference ? (
                              <Button size="small" sx={{ px: 0, minWidth: 0, justifyContent: "flex-start" }} onClick={() => navigate(`/pharmacy/procure?workspace=goods-receipt&receipt=${encodeURIComponent(transaction.businessReference || "")}`)}>
                                {transaction.businessReference}
                              </Button>
                            ) : (
                              <Typography variant="body2">{transaction.referenceId || "-"}</Typography>
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell>{transaction.adjustedByName || transaction.createdBy || "-"}</TableCell>
                        <TableCell sx={{ maxWidth: 240 }}>{transaction.notes || "-"}</TableCell>
                        <TableCell>{new Date(transaction.createdAt).toLocaleString()}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </OperationalTableCard>

              <CompactFilterCard
                title="Batch history"
                subtitle="Read-only movement path for the selected inventory batch."
              >
                {currentStock ? (
                  <WorkflowGuide
                    title="Adjustment trail"
                    subtitle={`${currentStock.medicineName} • ${currentStock.batchNumber || "No batch"} • ${currentStock.locationName || "Main Pharmacy"}`}
                    steps={currentStockHistory}
                  />
                ) : (
                  <CompactEmptyState
                    title="Select a batch to review its history."
                    subtitle="The batch history shows GRN, commercial setup, physical count, stock adjustment, and current quantity."
                  />
                )}
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {tab === "count" ? (
        <Stack spacing={2}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Draft" value={physicalCountDashboard.draft} helper="Ready to start" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="In Progress" value={physicalCountDashboard.inProgress} tone={physicalCountDashboard.inProgress ? "warning" : "default"} helper="Counting in progress" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Submitted" value={physicalCountDashboard.submitted} tone={physicalCountDashboard.submitted ? "info" : "default"} helper="Awaiting review" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Reviewed" value={physicalCountDashboard.reviewed} tone={physicalCountDashboard.reviewed ? "success" : "default"} helper="Ready for Batch 3" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Large Variances" value={physicalCountDashboard.largeVariances} tone={physicalCountDashboard.largeVariances ? "error" : "success"} helper="Difference >= 10 units" />
            </Grid>
          </Grid>

          <Grid container spacing={1.5}>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Last Physical Count" value={physicalCountInventorySummary.lastPhysicalCount ? formatInventoryDateTime(physicalCountInventorySummary.lastPhysicalCount) : "-"} helper="Most recent posted count" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Pending Count Sessions" value={physicalCountInventorySummary.pendingCountSessions} tone={physicalCountInventorySummary.pendingCountSessions ? "warning" : "default"} helper="Not yet posted" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Posted This Month" value={physicalCountInventorySummary.postedThisMonth} tone={physicalCountInventorySummary.postedThisMonth ? "success" : "default"} helper="Completed sessions" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Variance Adjusted" value={physicalCountInventorySummary.varianceAdjusted} tone={physicalCountInventorySummary.varianceAdjusted ? "info" : "default"} helper="Total counted difference" />
            </Grid>
            <Grid size={{ xs: 6, md: 2.4 }}>
              <CompactStatCard label="Last Adjustment Date" value={physicalCountInventorySummary.lastAdjustmentDate ? formatInventoryDateTime(physicalCountInventorySummary.lastAdjustmentDate) : "-"} helper="Latest PHYSICAL_COUNT movement" />
            </Grid>
          </Grid>

          <WorkflowGuide
            title="Physical Count Workflow"
            subtitle="Session workflow is UI-first only in this batch. System quantities are captured for comparison, but no stock adjustment is posted from sessions yet."
            steps={[
              { label: "Create Session", tone: "primary" },
              { label: "Count Medicines" },
              { label: "Submit Session", tone: "info" },
              { label: "Review Session", tone: "success" },
              { label: "Approve", helper: "Batch 3" },
              { label: "Post Adjustments", helper: "Batch 3" },
            ]}
          />

          <CompactFilterCard
            title="Session Controls"
            subtitle="Use session-based counting for audits and cycle counts. Quick one-off batch correction remains available below."
            actions={(
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Tooltip title="Coming after count session backend is enabled.">
                  <span><Button size="small" variant="outlined" disabled>Import Count Sheet</Button></span>
                </Tooltip>
                <Tooltip title="Coming after count session backend is enabled.">
                  <span><Button size="small" variant="outlined" disabled>Export Count Sheet</Button></span>
                </Tooltip>
                <Tooltip title="Coming after count session backend is enabled.">
                  <span><Button size="small" variant="outlined" disabled>Mobile Scanner</Button></span>
                </Tooltip>
              </Stack>
            )}
          >
            <Typography variant="body2" color="text.secondary">
              System quantities are captured for comparison when the session is created.
            </Typography>
          </CompactFilterCard>

          <Accordion expanded={physicalCountCreateOpen} onChange={(_, expanded) => setPhysicalCountCreateOpen(expanded)} disableGutters sx={compactAccordionSx}>
            <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
              <Stack spacing={0.4}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  Create Physical Count Session
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Create a local session shell and freeze system quantities for variance review.
                </Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
              <Box sx={compactFormSx}>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      size="small"
                      fullWidth
                      label={<RequiredLabel text="Session Name" required />}
                      value={physicalCountForm.sessionName}
                      onChange={(event) => setPhysicalCountForm((current) => ({ ...current, sessionName: event.target.value }))}
                      required
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="physical-count-session-location-label"><RequiredLabel text="Location" required /></InputLabel>
                      <Select
                        labelId="physical-count-session-location-label"
                        label="Location"
                        value={physicalCountForm.locationId}
                        onChange={(event) => setPhysicalCountForm((current) => ({ ...current, locationId: String(event.target.value) }))}
                      >
                        {locations.map((location) => (
                          <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="physical-count-session-reason-label"><RequiredLabel text="Reason" required /></InputLabel>
                      <Select
                        labelId="physical-count-session-reason-label"
                        label="Reason"
                        value={physicalCountForm.reason}
                        onChange={(event) => setPhysicalCountForm((current) => ({ ...current, reason: event.target.value as PhysicalCountReason }))}
                      >
                        <MenuItem value="MONTHLY_COUNT">Monthly Count</MenuItem>
                        <MenuItem value="QUARTERLY_AUDIT">Quarterly Audit</MenuItem>
                        <MenuItem value="CYCLE_COUNT">Cycle Count</MenuItem>
                        <MenuItem value="ANNUAL_AUDIT">Annual Audit</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="physical-count-session-scope-label"><RequiredLabel text="Scope" required /></InputLabel>
                      <Select
                        labelId="physical-count-session-scope-label"
                        label="Scope"
                        value={physicalCountForm.scope}
                        onChange={(event) => setPhysicalCountForm((current) => ({
                          ...current,
                          scope: event.target.value as PhysicalCountScope,
                          category: "",
                          selectedMedicineIds: [],
                        }))}
                      >
                        <MenuItem value="ENTIRE_INVENTORY">Entire Inventory</MenuItem>
                        <MenuItem value="CATEGORY">Category</MenuItem>
                        <MenuItem value="SELECTED_MEDICINES">Selected Medicines</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  {physicalCountForm.scope === "CATEGORY" ? (
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="physical-count-session-category-label"><RequiredLabel text="Category" required /></InputLabel>
                        <Select
                          labelId="physical-count-session-category-label"
                          label="Category"
                          value={physicalCountForm.category}
                          onChange={(event) => setPhysicalCountForm((current) => ({ ...current, category: String(event.target.value) }))}
                        >
                          {medicineCategories.map((category) => (
                            <MenuItem key={category} value={category}>{category}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                  ) : null}
                  {physicalCountForm.scope === "SELECTED_MEDICINES" ? (
                    <Grid size={{ xs: 12, md: 8 }}>
                      <Autocomplete
                        multiple
                        options={medicines}
                        size="small"
                        value={medicines.filter((medicine) => physicalCountForm.selectedMedicineIds.includes(medicine.id))}
                        onChange={(_, value) => setPhysicalCountForm((current) => ({ ...current, selectedMedicineIds: value.map((medicine) => medicine.id) }))}
                        getOptionLabel={(option) => option.medicineName}
                        renderInput={(params) => <TextField {...params} label={<RequiredLabel text="Selected Medicines" required />} />}
                      />
                    </Grid>
                  ) : null}
                  <Grid size={12}>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button variant="contained" disabled={!canManageInventory || saving || !stocks.length} onClick={createPhysicalCountSession}>
                        Create Session
                      </Button>
                      <Button variant="outlined" onClick={() => setPhysicalCountForm({
                        ...emptyPhysicalCountSessionForm(),
                        locationId: physicalCountForm.locationId,
                      })}>
                        Cancel
                      </Button>
                    </Stack>
                  </Grid>
                </Grid>
              </Box>
            </AccordionDetails>
          </Accordion>

          <OperationalTableCard
            title="Count Sessions"
            subtitle="Local session register for ongoing counts, review, and submission."
            countLabel={`${physicalCountSessions.length} sessions`}
            maxVisibleRows={5}
            emptyState={physicalCountSessions.length === 0 ? (
              <CompactEmptyState title="No physical count sessions yet." subtitle="Create the first session to start a count without changing inventory quantity." />
            ) : undefined}
          >
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Session</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell>Created By</TableCell>
                  <TableCell>Created Date</TableCell>
                  <TableCell>Last Updated</TableCell>
                  <TableCell align="right">Medicines</TableCell>
                  <TableCell align="right">Counted</TableCell>
                  <TableCell align="right">Pending</TableCell>
                  <TableCell align="right">Variance</TableCell>
                  <TableCell align="right">Completion %</TableCell>
                  <TableCell>Reviewer</TableCell>
                  <TableCell>Reviewed Date</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {physicalCountSessions.map((session) => {
                  const counted = session.lines.filter((line) => physicalCountLineDifference(line) != null).length;
                  const pending = session.lines.length - counted;
                  const variance = session.lines.filter((line) => {
                    const difference = physicalCountLineDifference(line);
                    return difference != null && difference !== 0;
                  }).length;
                  const completion = physicalCountCompletionPercent(session.lines);
                  const disableContinue = !["DRAFT", "IN_PROGRESS"].includes(session.status);
                  const disableReview = session.status !== "SUBMITTED";
                  return (
                    <TableRow key={session.id} hover selected={session.id === physicalCountWorkspaceSessionId || session.id === physicalCountDrawerSessionId} sx={{ "& td": { verticalAlign: "top" } }}>
                      <TableCell>
                        <Stack spacing={0.2}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{session.sessionName}</Typography>
                          <Typography variant="caption" color="text.secondary">{session.scopeLabel} • {physicalCountReasonLabel(session.reason)}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{session.locationName}</TableCell>
                      <TableCell>{session.audit.createdBy}</TableCell>
                      <TableCell>{formatInventoryDateTime(session.audit.createdAt)}</TableCell>
                      <TableCell>{session.audit.lastUpdatedAt ? formatInventoryDateTime(session.audit.lastUpdatedAt) : "-"}</TableCell>
                      <TableCell align="right">{session.lines.length}</TableCell>
                      <TableCell align="right">{counted}</TableCell>
                      <TableCell align="right">{pending}</TableCell>
                      <TableCell align="right">{variance}</TableCell>
                      <TableCell align="right">{completion}%</TableCell>
                      <TableCell>{session.audit.reviewer || "-"}</TableCell>
                      <TableCell>{session.audit.reviewedDate ? formatInventoryDateTime(session.audit.reviewedDate) : "-"}</TableCell>
                      <TableCell>
                        <Chip size="small" label={physicalCountStatusLabel(session.status)} color={physicalCountStatusColor(session.status)} />
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.75} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                          <Button size="small" disabled={disableContinue} onClick={() => openPhysicalCountWorkspace(session.id, "continue")}>Continue</Button>
                          <Button size="small" variant="outlined" disabled={disableReview} onClick={() => openPhysicalCountDrawer(session.id, "review")}>Review Session</Button>
                          <Button size="small" variant="outlined" onClick={() => openPhysicalCountDrawer(session.id, "view")}>View</Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </OperationalTableCard>

          {physicalCountWorkspaceSession ? (
            <Card>
              <CardContent sx={compactCardContentSx}>
                <Stack spacing={1.25}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                        Counting Workspace
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {physicalCountWorkspaceSession.sessionName} • {physicalCountWorkspaceSession.locationName} • {physicalCountWorkspaceMode === "view" ? "View mode" : "Editable count mode"}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Chip size="small" label={`Total ${physicalCountSessionSummary.totalMedicines}`} variant="outlined" />
                      <Chip size="small" color="success" label={`Matched ${physicalCountSessionSummary.matched}`} />
                      <Chip size="small" color="warning" label={`Variance ${physicalCountSessionSummary.variance}`} />
                    </Stack>
                  </Box>

                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="Search Medicine"
                        value={physicalCountSearch}
                        onChange={(event) => setPhysicalCountSearch(event.target.value)}
                        placeholder="medicine name"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="Batch Search"
                        value={physicalCountBatchSearch}
                        onChange={(event) => setPhysicalCountBatchSearch(event.target.value)}
                        placeholder="batch number"
                      />
                    </Grid>
                  </Grid>

                  {physicalCountWorkspaceLines.length === 0 ? (
                    <CompactEmptyState title="No lines match the current count filters." subtitle="Clear the medicine or batch search to continue counting." />
                  ) : (
                    <TableContainer sx={{ maxHeight: 440 }}>
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Medicine</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell align="right">System Qty</TableCell>
                            <TableCell align="right">Counted Qty</TableCell>
                            <TableCell align="right">Difference</TableCell>
                            <TableCell>Reason</TableCell>
                            <TableCell>Status</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {physicalCountWorkspaceLines.map((line) => {
                            const difference = physicalCountLineDifference(line);
                            const status = physicalCountLineStatus(line);
                            const isActive = line.id === physicalCountWorkspaceLine?.id;
                            const readOnly = physicalCountWorkspaceMode === "view" || physicalCountWorkspaceSession.status === "SUBMITTED" || physicalCountWorkspaceSession.status === "REVIEWED";
                            return (
                              <TableRow key={line.id} hover selected={isActive} onClick={() => setPhysicalCountWorkspaceLineId(line.id)} sx={{ "& td": { verticalAlign: "top" } }}>
                                <TableCell>
                                  <Stack spacing={0.2}>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{line.medicineName}</Typography>
                                    <Typography variant="caption" color="text.secondary">{line.locationName}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{line.batchNumber}</TableCell>
                                <TableCell align="right">{line.systemQty}</TableCell>
                                <TableCell align="right" sx={{ minWidth: 112 }}>
                                  <TextField
                                    size="small"
                                    type="number"
                                    value={line.countedQty}
                                    onChange={(event) => updatePhysicalCountSessionLine(physicalCountWorkspaceSession.id, line.id, { countedQty: event.target.value })}
                                    inputProps={{ min: 0, step: 1 }}
                                    disabled={readOnly}
                                  />
                                </TableCell>
                                <TableCell align="right">{difference == null ? "-" : `${difference > 0 ? "+" : ""}${difference}`}</TableCell>
                                <TableCell sx={{ minWidth: 220 }}>
                                  <TextField
                                    size="small"
                                    fullWidth
                                    value={line.reason}
                                    onChange={(event) => updatePhysicalCountSessionLine(physicalCountWorkspaceSession.id, line.id, { reason: event.target.value })}
                                    placeholder="Count note / variance reason"
                                    disabled={readOnly}
                                  />
                                </TableCell>
                                <TableCell>
                                  <Chip size="small" label={physicalCountLineStatusLabel(status)} color={physicalCountLineStatusColor(status)} />
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}

                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 6, md: 2.4 }}>
                      <CompactStatCard label="Total Medicines" value={physicalCountSessionSummary.totalMedicines} helper="Batches in the session" />
                    </Grid>
                    <Grid size={{ xs: 6, md: 2.4 }}>
                      <CompactStatCard label="Matched" value={physicalCountSessionSummary.matched} tone="success" helper="No difference" />
                    </Grid>
                    <Grid size={{ xs: 6, md: 2.4 }}>
                      <CompactStatCard label="Variance" value={physicalCountSessionSummary.variance} tone={physicalCountSessionSummary.variance ? "warning" : "success"} helper="Short or excess lines" />
                    </Grid>
                    <Grid size={{ xs: 6, md: 2.4 }}>
                      <CompactStatCard label="Missing Qty" value={physicalCountSessionSummary.missingQty} tone={physicalCountSessionSummary.missingQty ? "warning" : "default"} helper="Counted below system" />
                    </Grid>
                    <Grid size={{ xs: 6, md: 2.4 }}>
                      <CompactStatCard label="Excess Qty" value={physicalCountSessionSummary.excessQty} tone={physicalCountSessionSummary.excessQty ? "info" : "default"} helper="Counted above system" />
                    </Grid>
                  </Grid>

                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="space-between">
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button
                        variant="outlined"
                        disabled={!physicalCountWorkspaceLine || physicalCountWorkspaceLineIndex <= 0}
                        onClick={() => {
                          const previous = physicalCountWorkspaceLines[physicalCountWorkspaceLineIndex - 1];
                          if (previous) setPhysicalCountWorkspaceLineId(previous.id);
                        }}
                      >
                        Previous
                      </Button>
                      <Button
                        variant="outlined"
                        disabled={!physicalCountWorkspaceLine || physicalCountWorkspaceLineIndex < 0 || physicalCountWorkspaceLineIndex >= physicalCountWorkspaceLines.length - 1}
                        onClick={() => {
                          const next = physicalCountWorkspaceLines[physicalCountWorkspaceLineIndex + 1];
                          if (next) setPhysicalCountWorkspaceLineId(next.id);
                        }}
                      >
                        Next
                      </Button>
                    </Stack>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button
                        variant="outlined"
                        disabled={physicalCountWorkspaceMode === "view" || physicalCountWorkspaceSession.status !== "IN_PROGRESS"}
                        onClick={() => savePhysicalCountSessionDraft()}
                      >
                        Save
                      </Button>
                      <Button
                        variant="contained"
                        disabled={physicalCountWorkspaceMode === "view" || physicalCountWorkspaceSession.status !== "IN_PROGRESS" || physicalCountWorkspaceLineIndex < 0 || physicalCountWorkspaceLineIndex >= physicalCountWorkspaceLines.length - 1}
                        onClick={() => {
                          savePhysicalCountSessionDraft("Physical count line saved locally. Inventory quantity is unchanged.");
                          const next = physicalCountWorkspaceLines[physicalCountWorkspaceLineIndex + 1];
                          if (next) setPhysicalCountWorkspaceLineId(next.id);
                        }}
                      >
                        Save & Next
                      </Button>
                      <Button
                        variant="contained"
                        color="warning"
                        disabled={physicalCountWorkspaceMode === "view" || physicalCountWorkspaceSession.status !== "IN_PROGRESS" || physicalCountSessionSummary.pending > 0}
                        onClick={submitPhysicalCountSession}
                      >
                        Submit Session
                      </Button>
                      <Tooltip title="Coming after count session backend is enabled.">
                        <span><Button variant="outlined" disabled>Approve</Button></span>
                      </Tooltip>
                      <Tooltip title="Coming after count session backend is enabled.">
                        <span><Button variant="outlined" disabled>Post</Button></span>
                      </Tooltip>
                    </Stack>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          ) : null}

          <Drawer
            anchor="right"
            open={Boolean(physicalCountDrawerSession)}
            onClose={() => setPhysicalCountDrawerSessionId(null)}
            ModalProps={{ keepMounted: false }}
            PaperProps={{
              sx: {
                width: { xs: "100%", sm: 760, lg: 820 },
                maxWidth: "100%",
                boxSizing: "border-box",
              },
            }}
          >
            <Box sx={{ p: 2, height: "100%", overflow: "auto" }}>
              {physicalCountDrawerSession ? (
                <Stack spacing={2}>
                  <Stack direction="row" justifyContent="space-between" spacing={2} alignItems="flex-start">
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>Physical Count Session</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {physicalCountDrawerSession.sessionName} · {physicalCountDrawerSession.locationName}
                      </Typography>
                    </Box>
                    <Button size="small" onClick={() => setPhysicalCountDrawerSessionId(null)}>Close</Button>
                  </Stack>

                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    <Chip size="small" label={physicalCountStatusLabel(physicalCountDrawerSession.status)} color={physicalCountStatusColor(physicalCountDrawerSession.status)} />
                    <Chip size="small" label={`${physicalCountDrawerSummary.completionPercent}% complete`} variant="outlined" />
                    <Chip size="small" color={physicalCountDrawerSummary.variance ? "warning" : "success"} label={`${physicalCountDrawerSummary.variance} variance`} />
                  </Stack>

                  <CompactFilterCard title="Review Summary" subtitle="Lifecycle state, completion, and next recommendation.">
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField size="small" fullWidth label="Status" value={physicalCountStatusLabel(physicalCountDrawerSession.status)} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField size="small" fullWidth label="Completion %" value={`${physicalCountDrawerSummary.completionPercent}%`} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField size="small" fullWidth label="Variance Count" value={String(physicalCountDrawerSummary.variance)} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={12}>
                        <TextField size="small" fullWidth label="Recommendation" value={physicalCountRecommendation(physicalCountDrawerSession.status)} InputProps={{ readOnly: true }} />
                      </Grid>
                    </Grid>
                  </CompactFilterCard>

                  <CompactFilterCard title="Session Summary" subtitle="Core session metadata captured at creation.">
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Session Name" value={physicalCountDrawerSession.sessionName} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Location" value={physicalCountDrawerSession.locationName} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Reason" value={physicalCountReasonLabel(physicalCountDrawerSession.reason)} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Scope" value={physicalCountDrawerSession.scopeLabel} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Created By" value={physicalCountDrawerSession.audit.createdBy} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Created Date" value={formatInventoryDateTime(physicalCountDrawerSession.audit.createdAt)} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Status" value={physicalCountStatusLabel(physicalCountDrawerSession.status)} InputProps={{ readOnly: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField size="small" fullWidth label="Session Duration" value={physicalCountDrawerSession.audit.sessionDuration || "-"} InputProps={{ readOnly: true }} />
                      </Grid>
                    </Grid>
                  </CompactFilterCard>

                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <CompactFilterCard title="Progress" subtitle="Counting progress across all batches in the session.">
                        <Grid container spacing={1.25}>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Medicines" value={String(physicalCountDrawerSummary.totalMedicines)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Counted" value={String(physicalCountDrawerSummary.counted)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Pending" value={String(physicalCountDrawerSummary.pending)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Completion %" value={`${physicalCountDrawerSummary.completionPercent}%`} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 12 }}><TextField size="small" fullWidth label="Variance" value={String(physicalCountDrawerSummary.variance)} InputProps={{ readOnly: true }} /></Grid>
                        </Grid>
                      </CompactFilterCard>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <CompactFilterCard title="Variance Summary" subtitle="Difference profile from the captured system quantities.">
                        <Grid container spacing={1.25}>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Matched" value={String(physicalCountDrawerSummary.matched)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Short" value={String(physicalCountDrawerSummary.short)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Excess" value={String(physicalCountDrawerSummary.excess)} InputProps={{ readOnly: true }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField size="small" fullWidth label="Large Variance" value={String(physicalCountDrawerSummary.largeVariance)} InputProps={{ readOnly: true }} /></Grid>
                        </Grid>
                      </CompactFilterCard>
                    </Grid>
                  </Grid>

                  <CompactFilterCard title="Comments / Notes" subtitle="Session-wide notes remain local in this phase.">
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="General Notes"
                          value={physicalCountDrawerSession.audit.generalNotes}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { generalNotes: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Counter Notes"
                          value={physicalCountDrawerSession.audit.counterNotes}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { counterNotes: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Reviewer Notes"
                          value={physicalCountDrawerSession.audit.reviewerNotes}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { reviewerNotes: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Audit Notes"
                          value={physicalCountDrawerSession.audit.auditNotes}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { auditNotes: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                        />
                      </Grid>
                    </Grid>
                  </CompactFilterCard>

                  <CompactFilterCard title="Review Checklist" subtitle="Editable only while reviewing a submitted session.">
                    <Grid container spacing={1}>
                      {([
                        ["randomSampleVerified", "Random sample verified"],
                        ["largeVariancesInvestigated", "Large variances investigated"],
                        ["batchVerificationComplete", "Batch verification complete"],
                        ["supportingRemarksAdded", "Supporting remarks added"],
                      ] as Array<[keyof PhysicalCountReviewChecklist, string]>).map(([key, label]) => (
                        <Grid key={key} size={{ xs: 12, md: 6 }}>
                          <FormControlLabel
                            control={(
                              <Checkbox
                                checked={physicalCountDrawerSession.audit.reviewChecklist[key]}
                                onChange={(event) => updatePhysicalCountReviewChecklist(physicalCountDrawerSession.id, key, event.target.checked)}
                                disabled={!(physicalCountDrawerMode === "review" && physicalCountDrawerSession.status === "SUBMITTED")}
                              />
                            )}
                            label={label}
                          />
                        </Grid>
                      ))}
                    </Grid>
                  </CompactFilterCard>

                  <CompactFilterCard title="Approval" subtitle="Approve reviewed sessions, reject them, or send them back for recount before posting.">
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Approval Notes"
                          value={physicalCountDrawerSession.audit.approvalNotes}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { approvalNotes: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                          disabled={physicalCountDrawerSession.status !== "REVIEWED"}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Rejection Reason"
                          value={physicalCountDrawerSession.audit.rejectionReason}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { rejectionReason: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                          disabled={physicalCountDrawerSession.status !== "REVIEWED"}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Return Reason"
                          value={physicalCountDrawerSession.audit.returnReason}
                          onChange={(event) => updatePhysicalCountSessionNotes(physicalCountDrawerSession.id, { returnReason: event.target.value.slice(0, 500) })}
                          inputProps={{ maxLength: 500 }}
                          disabled={physicalCountDrawerSession.status !== "REVIEWED"}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          size="small"
                          fullWidth
                          label="Posted"
                          value={physicalCountDrawerSession.audit.postedAt ? formatInventoryDateTime(physicalCountDrawerSession.audit.postedAt) : "Not posted"}
                          InputProps={{ readOnly: true }}
                        />
                      </Grid>
                    </Grid>
                  </CompactFilterCard>

                  {physicalCountDrawerMode === "review" && physicalCountDrawerSession.status === "SUBMITTED" ? (
                    <OperationalTableCard
                      title="Review Workspace"
                      subtitle="Review counted lines, add reviewer remarks, and flag exceptions before marking reviewed."
                      countLabel={`${physicalCountDrawerSession.lines.length} lines`}
                      maxVisibleRows={5}
                    >
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Medicine</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell align="right">System Qty</TableCell>
                            <TableCell align="right">Counted Qty</TableCell>
                            <TableCell align="right">Difference</TableCell>
                            <TableCell>Reason</TableCell>
                            <TableCell>Reviewer Remarks</TableCell>
                            <TableCell>Result</TableCell>
                            <TableCell>Flag</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {physicalCountDrawerSession.lines.map((line) => {
                            const difference = physicalCountLineDifference(line);
                            const result = physicalCountLineStatus(line);
                            return (
                              <TableRow key={line.id} sx={{ "& td": { verticalAlign: "top" } }}>
                                <TableCell>{line.medicineName}</TableCell>
                                <TableCell>{line.batchNumber}</TableCell>
                                <TableCell align="right">{line.systemQty}</TableCell>
                                <TableCell align="right">{line.countedQty || "-"}</TableCell>
                                <TableCell align="right">{difference == null ? "-" : `${difference > 0 ? "+" : ""}${difference}`}</TableCell>
                                <TableCell>{line.reason || "-"}</TableCell>
                                <TableCell sx={{ minWidth: 220 }}>
                                  <TextField
                                    size="small"
                                    fullWidth
                                    value={line.reviewerRemarks}
                                    onChange={(event) => updatePhysicalCountSessionLine(physicalCountDrawerSession.id, line.id, { reviewerRemarks: event.target.value })}
                                    placeholder="Reviewer remarks"
                                  />
                                </TableCell>
                                <TableCell>
                                  <Chip size="small" label={physicalCountLineStatusLabel(result)} color={physicalCountLineStatusColor(result)} />
                                </TableCell>
                                <TableCell>
                                  <Checkbox
                                    checked={line.flagged}
                                    onChange={(event) => updatePhysicalCountSessionLine(physicalCountDrawerSession.id, line.id, { flagged: event.target.checked })}
                                  />
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </OperationalTableCard>
                  ) : null}

                  {(physicalCountDrawerSession.status === "REVIEWED" || physicalCountDrawerSession.status === "APPROVED" || physicalCountDrawerSession.status === "POSTED") ? (
                    <OperationalTableCard
                      title="Post Preview"
                      subtitle="Variance lines only. Posting will reuse the existing stock adjustment engine and create stock movement audit."
                      countLabel={`${physicalCountDrawerSession.lines.filter((line) => {
                        const difference = physicalCountLineDifference(line);
                        return difference != null && difference !== 0;
                      }).length} variance lines`}
                      maxVisibleRows={5}
                    >
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Medicine</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell align="right">System Qty</TableCell>
                            <TableCell align="right">Counted Qty</TableCell>
                            <TableCell align="right">Difference</TableCell>
                            <TableCell>Reason</TableCell>
                            <TableCell>Movement Type</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {physicalCountDrawerSession.lines.filter((line) => {
                            const difference = physicalCountLineDifference(line);
                            return difference != null && difference !== 0;
                          }).map((line) => {
                            const difference = physicalCountLineDifference(line) ?? 0;
                            return (
                              <TableRow key={`post-${line.id}`}>
                                <TableCell>{line.medicineName}</TableCell>
                                <TableCell>{line.batchNumber}</TableCell>
                                <TableCell align="right">{line.systemQty}</TableCell>
                                <TableCell align="right">{line.countedQty}</TableCell>
                                <TableCell align="right">{difference > 0 ? `+${difference}` : difference}</TableCell>
                                <TableCell>{line.reason || "-"}</TableCell>
                                <TableCell>{difference > 0 ? "Adjustment In" : "Adjustment Out"}</TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </OperationalTableCard>
                  ) : null}

                  <CompactFilterCard title="Audit Timeline" subtitle="Lifecycle progression derived from local session audit fields.">
                    <Stack spacing={1}>
                      {physicalCountTimeline(physicalCountDrawerSession).map((timelineStep, index, rows) => (
                        <Stack key={`${physicalCountDrawerSession.id}-${timelineStep.label}`} direction="row" spacing={1.25} alignItems="stretch">
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
                            {index < rows.length - 1 ? (
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
                          <Stack spacing={0.25} sx={{ pb: index < rows.length - 1 ? 1 : 0 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{timelineStep.label}</Typography>
                            <Chip
                              size="small"
                              label={timelineStep.state === "completed" ? "Completed" : timelineStep.state === "current" ? "Current" : "Pending"}
                              color={timelineStep.state === "completed" ? "success" : timelineStep.state === "current" ? "primary" : "default"}
                              variant={timelineStep.state === "pending" ? "outlined" : "filled"}
                            />
                            <Typography variant="caption" color="text.secondary">
                              {timelineStep.at ? formatInventoryDateTime(timelineStep.at) : "Not recorded yet"}
                            </Typography>
                          </Stack>
                        </Stack>
                      ))}
                    </Stack>
                  </CompactFilterCard>

                  <Divider />

                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    <Button size="small" onClick={() => setPhysicalCountDrawerSessionId(null)}>Close</Button>
                    {physicalCountDrawerMode === "review" && physicalCountDrawerSession.status === "SUBMITTED" ? (
                      <Button size="small" variant="contained" color="success" onClick={markPhysicalCountSessionReviewed}>Mark Reviewed</Button>
                    ) : null}
                    <Tooltip title="Available in next workflow phase.">
                      <span><Button size="small" variant="outlined" disabled>Print</Button></span>
                    </Tooltip>
                    <Tooltip title="Available in next workflow phase.">
                      <span><Button size="small" variant="outlined" disabled>Export PDF</Button></span>
                    </Tooltip>
                    <Button size="small" variant="outlined" disabled={physicalCountDrawerSession.status !== "REVIEWED" || saving} onClick={approvePhysicalCountSession}>Approve Session</Button>
                    <Button size="small" variant="outlined" color="error" disabled={physicalCountDrawerSession.status !== "REVIEWED" || saving} onClick={rejectPhysicalCountSession}>Reject</Button>
                    <Button size="small" variant="outlined" color="warning" disabled={physicalCountDrawerSession.status !== "REVIEWED" || saving} onClick={returnPhysicalCountSessionForRecount}>Return for Recount</Button>
                    <Button size="small" variant="contained" disabled={physicalCountDrawerSession.status !== "APPROVED" || Boolean(physicalCountDrawerSession.audit.postedAt) || saving} onClick={() => void postPhysicalCountAdjustments()}>Post Adjustments</Button>
                  </Stack>
                </Stack>
              ) : null}
            </Box>
          </Drawer>

          <Accordion expanded={physicalCountQuickOpen} onChange={(_, expanded) => setPhysicalCountQuickOpen(expanded)} disableGutters sx={compactAccordionSx}>
            <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
              <Stack spacing={0.4}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  Quick Quantity Correction
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Use this for emergency correction of one medicine/batch. For audits, use Physical Count Sessions.
                </Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
              <Box sx={compactFormSx}>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="count-medicine-label"><RequiredLabel text="Medicine" required /></InputLabel>
                      <Select
                        id="inventory-count-medicine"
                        labelId="count-medicine-label"
                        label="Medicine"
                        value={stockCountForm.medicineId}
                        onChange={(e) => setStockCountForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                        required
                        error={Boolean(countFieldErrors.medicineId)}
                        inputProps={{ "aria-required": true }}
                      >
                        <MenuItem value="">Select medicine</MenuItem>
                        {medicines.map((medicine) => (
                          <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>
                        ))}
                      </Select>
                      {countFieldErrors.medicineId ? <Typography variant="caption" color="error">{countFieldErrors.medicineId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="count-location-label"><RequiredLabel text="Location" required /></InputLabel>
                      <Select
                        id="inventory-count-location"
                        labelId="count-location-label"
                        label="Location"
                        value={stockCountForm.locationId}
                        onChange={(e) => setStockCountForm((current) => ({ ...current, locationId: String(e.target.value), stockBatchId: "" }))}
                        required
                        error={Boolean(countFieldErrors.locationId)}
                        inputProps={{ "aria-required": true }}
                      >
                        <MenuItem value="">All locations</MenuItem>
                        {locations.map((location) => (
                          <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>
                        ))}
                      </Select>
                      {countFieldErrors.locationId ? <Typography variant="caption" color="error">{countFieldErrors.locationId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="count-batch-label"><RequiredLabel text="Batch" required /></InputLabel>
                      <Select
                        id="inventory-count-batch"
                        labelId="count-batch-label"
                        label="Batch"
                        value={stockCountForm.stockBatchId}
                        onChange={(e) => setStockCountForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                        required
                        error={Boolean(countFieldErrors.stockBatchId)}
                        inputProps={{ "aria-required": true }}
                      >
                        <MenuItem value="">Select batch</MenuItem>
                        {countableStocks.map((stock) => (
                          <MenuItem key={stock.id} value={stock.id}>
                            {(stock.batchNumber || "No batch")} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                          </MenuItem>
                        ))}
                      </Select>
                      {countFieldErrors.stockBatchId ? <Typography variant="caption" color="error">{countFieldErrors.stockBatchId}</Typography> : null}
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      size="small"
                      fullWidth
                      label="System quantity"
                      value={selectedCountStock ? selectedCountStock.quantityOnHand : ""}
                      InputProps={{ readOnly: true }}
                      helperText="Quantity currently recorded in the system."
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      id="inventory-count-quantity"
                      size="small"
                      fullWidth
                      type="number"
                      label={<RequiredLabel text="Physical count" required />}
                      value={stockCountForm.countedQuantity}
                      onChange={(e) => setStockCountForm((current) => ({ ...current, countedQuantity: e.target.value }))}
                      required
                      error={Boolean(countFieldErrors.physicalQuantity)}
                      helperText={countFieldErrors.physicalQuantity || "Enter the counted quantity from shelves."}
                      inputProps={{ min: 0, step: 1, "aria-required": true }}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Box id="inventory-count-reason" tabIndex={-1}>
                      <CommentSuggestions
                        category="INVENTORY_ADJUSTMENT"
                        selectedReason={stockCountForm.reason}
                        remarks={stockCountForm.remarks}
                        onReasonChange={(value) => setStockCountForm((current) => ({ ...current, reason: value }))}
                        onRemarksChange={(value) => setStockCountForm((current) => ({ ...current, remarks: value }))}
                        requiredReason
                        maxRemarksLength={250}
                        reasonLabel="Adjustment reason"
                        remarksLabel="Remarks"
                        reasonHelperText={countFieldErrors.reason || "Reason is required for audit trail."}
                        remarksHelperText={countFieldErrors.remarks || `${stockCountForm.remarks.length}/250`}
                      />
                    </Box>
                  </Grid>
                  <Grid size={12}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      <Chip
                        size="small"
                        label={countVariance === null ? "Variance: -" : `Variance: ${countVariance > 0 ? "+" : ""}${countVariance}`}
                        color={countVariance === null ? "default" : countVariance === 0 ? "default" : countVariance > 0 ? "success" : "warning"}
                      />
                      <Button
                        variant="contained"
                        disabled={!canManageInventory || saving || !selectedCountStock || !stockCountForm.reason.trim() || stockCountForm.countedQuantity.trim() === "" || countVariance === 0}
                        onClick={async () => {
                          if (!auth.accessToken || !auth.tenantId || !selectedCountStock) {
                            setError("Select a batch before posting the stock count.");
                            return;
                          }
                          const parsedCount = inventoryPhysicalCountSchema.safeParse({
                            stockBatchId: selectedCountStock.id,
                            medicineId: selectedCountStock.medicineId,
                            locationId: selectedCountStock.locationId || selectedLocationId || "",
                            physicalQuantity: stockCountForm.countedQuantity,
                            reason: stockCountForm.reason,
                            remarks: stockCountForm.remarks,
                          });
                          if (!parsedCount.success) {
                            const fieldErrors = zodFieldErrors(parsedCount.error);
                            setCountFieldErrors(fieldErrors);
                            setError(parsedCount.error.issues[0]?.message || "Stock count could not be posted.");
                            focusFirstInventoryField(fieldErrors, {
                              medicineId: "inventory-count-medicine",
                              locationId: "inventory-count-location",
                              stockBatchId: "inventory-count-batch",
                              physicalQuantity: "inventory-count-quantity",
                              reason: "inventory-count-reason",
                            });
                            return;
                          }
                          const counted = Number(stockCountForm.countedQuantity);
                          const variance = counted - selectedCountStock.quantityOnHand;
                          if (variance === 0) {
                            setError("No variance to post for this stock count.");
                            return;
                          }
                          setSaving(true);
                          setError(null);
                          setSuccess(null);
                          setCountFieldErrors({});
                          try {
                            await createInventoryTransaction(auth.accessToken, auth.tenantId, {
                              medicineId: selectedCountStock.medicineId,
                              stockBatchId: selectedCountStock.id,
                              transactionType: variance > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT",
                              quantity: Math.abs(variance),
                              reason: `Physical stock count: ${stockCountForm.reason.trim()}`,
                              referenceType: "PHYSICAL_STOCK_COUNT",
                              referenceId: selectedCountStock.id,
                              notes: `System ${selectedCountStock.quantityOnHand}, counted ${counted}, variance ${variance > 0 ? "+" : ""}${variance}${stockCountForm.remarks.trim() ? ` • ${stockCountForm.remarks.trim()}` : ""}`,
                            });
                            await loadAll();
                            setSuccess(`Physical stock count posted. Variance ${variance > 0 ? "+" : ""}${variance}.`);
                            setStockCountForm(emptyStockCountForm());
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Failed to post stock count");
                          } finally {
                            setSaving(false);
                          }
                        }}
                      >
                        Post count correction
                      </Button>
                    </Stack>
                  </Grid>
                </Grid>
              </Box>
            </AccordionDetails>
          </Accordion>
        </Stack>
      ) : null}

      {tab === "expiry-report" ? (
        <Grid container spacing={2}>
          <Grid size={12}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        Near expiry / expired report
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Buckets are grouped by expiry date so you can move medicine before it becomes unusable.
                      </Typography>
                    </Box>
                    <Chip size="small" label={`${expiryReportRows.length} batches`} variant="outlined" />
                  </Box>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="expiry-report-location-label">Location</InputLabel>
                        <Select
                          labelId="expiry-report-location-label"
                          label="Location"
                          value={selectedLocationId || ""}
                          onChange={(e) => setSelectedLocationId(String(e.target.value) || null)}
                        >
                          <MenuItem value="">All locations</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="Search medicine / batch"
                        value={expiryReportMedicineId}
                        onChange={(e) => setExpiryReportMedicineId(e.target.value)}
                        placeholder="medicine name / batch / reference"
                      />
                    </Grid>
                  </Grid>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip size="small" color="error" label={`Expired ${expiryBucketCounts.Expired || 0}`} />
                    <Chip size="small" color="warning" label={`0-30 days ${expiryBucketCounts["0-30"] || 0}`} />
                    <Chip size="small" color="info" label={`31-60 days ${expiryBucketCounts["31-60"] || 0}`} />
                    <Chip size="small" color="secondary" label={`61-90 days ${expiryBucketCounts["61-90"] || 0}`} />
                    <Chip size="small" color="success" label={`91+ days ${expiryBucketCounts["91+"] || 0}`} />
                  </Stack>
                  {expiryReportRows.length === 0 ? (
                    <CompactEmptyState title="No expiry rows found." subtitle="Adjust the medicine or location filter to inspect another slice of inventory." />
                  ) : (
                    <TableContainer sx={{ maxHeight: 420 }}>
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Medicine</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell>Location</TableCell>
                            <TableCell>Expiry</TableCell>
                            <TableCell align="right">Qty</TableCell>
                            <TableCell>Bucket</TableCell>
                            <TableCell>Status</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {expiryReportRows.map((row) => (
                            <TableRow key={row.id} sx={{ "& td": { py: 0.8, verticalAlign: "top" } }}>
                              <TableCell>{row.medicineName}</TableCell>
                              <TableCell>{row.batchNumber || "-"}</TableCell>
                              <TableCell>{row.locationName || "Main Pharmacy"}</TableCell>
                              <TableCell>{row.expiryDate || "-"}</TableCell>
                              <TableCell align="right">{row.quantityOnHand}</TableCell>
                              <TableCell>
                                <Chip size="small" label={row.expiryLabel} color={row.expiryColor} />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.expiryBucket === "Expired" ? "EXPIRED" : "AVAILABLE"} color={row.expiryBucket === "Expired" ? "error" : "success"} />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {tab === "low-stock" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Low stock medicines
                  </Typography>
                  {lowStock.length === 0 ? (
                    <Alert severity="info">No low stock items are currently blocking dispensing. Add or replenish stock batches to keep the queue moving.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Medicine</TableCell>
                          <TableCell>Batch</TableCell>
                          <TableCell>Expiry</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell align="right">Threshold</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {lowStock.map((row) => (
                          <TableRow key={row.stockId} hover sx={{ "& td": { fontWeight: row.quantityOnHand <= (row.lowStockThreshold ?? 5) ? 700 : 400 } }}>
                            <TableCell>{row.medicineName}</TableCell>
                            <TableCell>{row.batchNumber || "-"}</TableCell>
                            <TableCell>
                              <Chip size="small" label={expiryState(row.expiryDate).label} color={expiryState(row.expiryDate).color} />
                            </TableCell>
                            <TableCell align="right">{row.quantityOnHand}</TableCell>
                            <TableCell align="right">{row.lowStockThreshold ?? "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Quick summary
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Inventory transactions and low-stock checks run tenant-side only. Use the stock tab to add batches, monitor expiry, and keep medicine availability current.
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Chip label={`${medicines.length} medicines`} />
                    <Chip label={`${stocks.length} stock records`} />
                    <Chip label={`${lowStock.length} low stock`} color={lowStock.length ? "error" : "success"} />
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {tab === "returns" ? (
        <Stack spacing={2}>
          <CompactFilterCard
            title="Returns & Write-Off History"
            subtitle="Recent customer returns, vendor returns, and write-offs stay visible above the posting forms."
            actions={(
              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                <Chip size="small" label={`Customer ${customerReturnHistory.length}`} variant="outlined" />
                <Chip size="small" label={`Vendor ${vendorReturnHistory.length}`} variant="outlined" />
                <Chip size="small" label={`Write-off ${writeOffHistory.length}`} variant="outlined" />
              </Stack>
            )}
          >
            <TableContainer sx={{ maxHeight: 280 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Medicine</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell>Reference</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>Adjusted by</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {[...customerReturnHistory, ...vendorReturnHistory, ...writeOffHistory].slice(0, 12).map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                      <TableCell>{transactionLabel(row.transactionType)}</TableCell>
                      <TableCell align="right">{row.quantity}</TableCell>
                      <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                      <TableCell>{row.reason || "-"}</TableCell>
                      <TableCell>{row.adjustedByName || row.createdBy || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CompactFilterCard>
          <Card>
            <CardContent sx={compactCardContentSx}>
              <Stack spacing={1}>
                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Customer Returns</Typography>
                    <Typography variant="body2" color="text.secondary">Search a completed pharmacy sale, choose the line, and record a reusable or non-sellable return.</Typography>
                  </Box>
                  <Chip size="small" label={`${customerReturnHistory.length} return movements`} variant="outlined" />
                </Box>
                <Box sx={compactFormSx}>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth label="Search sale / receipt" value={saleSearch} onChange={(e) => setSaleSearch(e.target.value)} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-sale-label"><RequiredLabel text="Sale / receipt" required /></InputLabel>
                      <Select
                        id="customer-return-sale"
                        labelId="customer-return-sale-label"
                        label="Sale / receipt"
                        value={customerReturnSaleId}
                        onChange={(e) => {
                          const value = String(e.target.value);
                          setCustomerReturnSaleId(value);
                          setCustomerReturnLineId("");
                        }}
                      >
                        <MenuItem value="">Select sale</MenuItem>
                        {filteredCustomerSales.map((sale) => (
                          <MenuItem key={sale.id} value={sale.id}>
                            {sale.saleNumber} • {sale.patientName || sale.customerName || "Walk-in"} • Due {sale.dueAmount}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-line-label"><RequiredLabel text="Medicine line" required /></InputLabel>
                      <Select
                        id="customer-return-line"
                        labelId="customer-return-line-label"
                        label="Medicine line"
                        value={customerReturnLineId}
                        onChange={(e) => setCustomerReturnLineId(String(e.target.value))}
                      >
                        <MenuItem value="">Select line</MenuItem>
                        {selectedCustomerSale?.items.map((item) => {
                          const remaining = item.quantity - item.returnedQuantity;
                          return (
                            <MenuItem key={item.id} value={item.id} disabled={remaining <= 0}>
                              {item.medicineName} • Sold {item.quantity} • Returned {item.returnedQuantity} • Remaining {remaining}
                            </MenuItem>
                          );
                        })}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField
                      id="inventory-customer-return-quantity"
                      size="small"
                      fullWidth
                      type="number"
                      label={<RequiredLabel text="Return quantity" required />}
                      value={customerReturnQuantity}
                      onChange={(e) => setCustomerReturnQuantity(e.target.value)}
                      required
                      error={Boolean(customerReturnFieldErrors.returnQuantity)}
                      helperText={customerReturnFieldErrors.returnQuantity || "Return quantity must be within the remaining sold quantity."}
                      inputProps={{ min: 1, step: 1, "aria-required": true }}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-mode-label">
                        <RequiredLabel text="Refund mode" required />
                      </InputLabel>
                      <Select
                        id="customer-return-mode"
                        labelId="customer-return-mode-label"
                        label="Refund mode"
                        value={customerReturnMode}
                        onChange={(e) => setCustomerReturnMode(e.target.value as PaymentMode)}
                        required
                        error={Boolean(customerReturnFieldErrors.refundMode)}
                        inputProps={{ "aria-required": true }}
                      >
                        <MenuItem value="CASH">CASH</MenuItem>
                        <MenuItem value="UPI">UPI</MenuItem>
                        <MenuItem value="CARD">CARD</MenuItem>
                        <MenuItem value="NO_REFUND">NO_REFUND</MenuItem>
                        <MenuItem value="ORIGINAL_PAYMENT_MODE">ORIGINAL_PAYMENT_MODE</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField id="customer-return-reference" size="small" fullWidth label="Reference number" value={customerReturnReference} onChange={(e) => setCustomerReturnReference(e.target.value)} error={Boolean(customerReturnFieldErrors.referenceNumber)} helperText={customerReturnFieldErrors.referenceNumber || "Optional reference number."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-reusable-label"><RequiredLabel text="Condition" required /></InputLabel>
                      <Select id="customer-return-condition" labelId="customer-return-reusable-label" label="Condition" value={customerReturnReusable ? "reusable" : "non_sellable"} onChange={(e) => setCustomerReturnReusable(String(e.target.value) === "reusable")} error={Boolean(customerReturnFieldErrors.condition)} required inputProps={{ "aria-required": true }}>
                        <MenuItem value="reusable">Reusable</MenuItem>
                        <MenuItem value="non_sellable">Damaged / Expired / Non-sellable</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 5 }}>
                    <Box id="customer-return-reason" tabIndex={-1}>
                      <CommentSuggestions
                        category="INVENTORY_CUSTOMER_RETURN"
                        selectedReason={customerReturnReason}
                        remarks={customerReturnNotes}
                        onReasonChange={setCustomerReturnReason}
                        onRemarksChange={setCustomerReturnNotes}
                        requiredReason
                        dense
                        maxRemarksLength={250}
                        reasonLabel="Reason"
                        remarksLabel="Notes"
                        reasonHelperText={customerReturnFieldErrors.reason || "Required for customer return."}
                        remarksHelperText={customerReturnFieldErrors.notes || `${customerReturnNotes.length}/250`}
                      />
                    </Box>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Alert severity="info" sx={{ py: 0.5, minHeight: 40, alignItems: "center" }}>
                      {selectedCustomerSale?.paidAmount && selectedCustomerSale.paidAmount > 0
                        ? "Refund can be processed from Billing / Refunds."
                        : "No payment recorded. Return will only adjust inventory and history."}
                    </Alert>
                  </Grid>
                </Grid>
                </Box>
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mt: 0.25 }}>
                  <Button
                    variant="contained"
                    disabled={!canManageInventory || saving || !selectedCustomerSale || !selectedCustomerReturnItem}
                    onClick={() => void submitCustomerReturn()}
                  >
                    Process Customer Return
                  </Button>
                    <Button variant="outlined" onClick={() => {
                    setCustomerReturnSaleId("");
                    setCustomerReturnLineId("");
                    setCustomerReturnQuantity("1");
                    setCustomerReturnReason("");
                    setCustomerReturnNotes("");
                    setCustomerReturnReference("");
                    setSaleSearch("");
                    setCustomerReturnReusable(true);
                  }}>
                    Clear
                  </Button>
                </Box>
                {customerReturnHistory.length ? (
                  <TableContainer sx={{ maxHeight: 260 }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>Medicine</TableCell>
                          <TableCell>Type</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell>Reference</TableCell>
                          <TableCell>Reason</TableCell>
                          <TableCell>Adjusted by</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {customerReturnHistory.slice(0, 8).map((row) => (
                          <TableRow key={row.id}>
                            <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                            <TableCell>{transactionLabel(row.transactionType)}</TableCell>
                            <TableCell align="right">{row.quantity}</TableCell>
                            <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                            <TableCell>{row.reason || "-"}</TableCell>
                            <TableCell>{row.adjustedByName || row.createdBy || "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card>
                <CardContent sx={compactCardContentSx}>
                  <Stack spacing={1.5}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                      <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>Vendor Returns</Typography>
                        <Typography variant="body2" color="text.secondary">Return supplied stock back to the vendor and record the movement for audit.</Typography>
                      </Box>
                      <Chip size="small" label={`${vendorReturnHistory.length} movements`} variant="outlined" />
                    </Box>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 5 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="vendor-return-medicine-label"><RequiredLabel text="Medicine" required /></InputLabel>
                          <Select
                            id="vendor-return-medicine"
                            labelId="vendor-return-medicine-label"
                            label="Medicine"
                            value={vendorReturnForm.medicineId}
                            onChange={(e) => setVendorReturnForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                            required
                            error={Boolean(vendorReturnFieldErrors.medicineId)}
                            inputProps={{ "aria-required": true }}
                          >
                            <MenuItem value="">Select medicine</MenuItem>
                            {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                          </Select>
                          {vendorReturnFieldErrors.medicineId ? <Typography variant="caption" color="error">{vendorReturnFieldErrors.medicineId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 7 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="vendor-return-batch-label"><RequiredLabel text="Batch" required /></InputLabel>
                          <Select
                            id="vendor-return-batch"
                            labelId="vendor-return-batch-label"
                            label="Batch"
                            value={vendorReturnForm.stockBatchId}
                            onChange={(e) => setVendorReturnForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                            required
                            error={Boolean(vendorReturnFieldErrors.stockBatchId)}
                            inputProps={{ "aria-required": true }}
                          >
                            <MenuItem value="">Select batch</MenuItem>
                            {vendorReturnBatches.map((stock) => (
                              <MenuItem key={stock.id} value={stock.id}>
                                {stock.batchNumber || "No batch"} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                              </MenuItem>
                            ))}
                          </Select>
                          {vendorReturnFieldErrors.stockBatchId ? <Typography variant="caption" color="error">{vendorReturnFieldErrors.stockBatchId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField id="vendor-return-quantity" size="small" fullWidth type="number" label={<RequiredLabel text="Return quantity" required />} value={vendorReturnForm.quantity} onChange={(e) => setVendorReturnForm((current) => ({ ...current, quantity: e.target.value }))} required error={Boolean(vendorReturnFieldErrors.returnQuantity)} helperText={vendorReturnFieldErrors.returnQuantity || "Return quantity must be within available stock."} inputProps={{ min: 1, step: 1, "aria-required": true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <TextField id="vendor-return-supplier" size="small" fullWidth label={<RequiredLabel text="Supplier / invoice reference" required />} value={vendorReturnForm.supplierReference} onChange={(e) => setVendorReturnForm((current) => ({ ...current, supplierReference: e.target.value }))} required error={Boolean(vendorReturnFieldErrors.supplierReference)} helperText={vendorReturnFieldErrors.supplierReference || "Required for vendor returns."} inputProps={{ "aria-required": true, maxLength: 60 }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Box id="vendor-return-reason" tabIndex={-1}>
                          <CommentSuggestions
                            category="INVENTORY_VENDOR_RETURN"
                            selectedReason={vendorReturnForm.reason}
                            remarks={vendorReturnForm.notes}
                            onReasonChange={(value) => setVendorReturnForm((current) => ({ ...current, reason: value }))}
                            onRemarksChange={(value) => setVendorReturnForm((current) => ({ ...current, notes: value }))}
                            requiredReason
                            dense
                            maxRemarksLength={250}
                            reasonLabel="Reason"
                            remarksLabel="Notes"
                            reasonHelperText={vendorReturnFieldErrors.reason || "Reason is required for vendor returns."}
                            remarksHelperText={vendorReturnFieldErrors.notes || `${vendorReturnForm.notes.length}/250`}
                          />
                        </Box>
                      </Grid>
                    </Grid>
                    <Button variant="contained" disabled={!canManageInventory || saving} onClick={() => void submitVendorReturn()}>Post Vendor Return</Button>
                    {vendorReturnHistory.length ? (
                      <TableContainer sx={{ maxHeight: 240 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Qty</TableCell>
                              <TableCell>Reference</TableCell>
                              <TableCell>Reason</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {vendorReturnHistory.slice(0, 6).map((row) => (
                              <TableRow key={row.id}>
                                <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                                <TableCell align="right">{row.quantity}</TableCell>
                                <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                                <TableCell>{row.reason || "-"}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Card>
                <CardContent sx={compactCardContentSx}>
                    <Stack spacing={1}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                      <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>Write-Offs</Typography>
                        <Typography variant="body2" color="text.secondary">Remove damaged, lost, or expired stock with a clear audit movement.</Typography>
                      </Box>
                      <Chip size="small" label={`${writeOffHistory.length} movements`} variant="outlined" />
                    </Box>
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 5 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="writeoff-medicine-label"><RequiredLabel text="Medicine" required /></InputLabel>
                          <Select
                            id="writeoff-medicine"
                            labelId="writeoff-medicine-label"
                            label="Medicine"
                            value={writeOffForm.medicineId}
                            onChange={(e) => setWriteOffForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                            required
                            error={Boolean(writeOffFieldErrors.medicineId)}
                            inputProps={{ "aria-required": true }}
                          >
                            <MenuItem value="">Select medicine</MenuItem>
                            {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                          </Select>
                          {writeOffFieldErrors.medicineId ? <Typography variant="caption" color="error">{writeOffFieldErrors.medicineId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 7 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="writeoff-batch-label"><RequiredLabel text="Batch" required /></InputLabel>
                          <Select
                            id="writeoff-batch"
                            labelId="writeoff-batch-label"
                            label="Batch"
                            value={writeOffForm.stockBatchId}
                            onChange={(e) => setWriteOffForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                            required
                            error={Boolean(writeOffFieldErrors.stockBatchId)}
                            inputProps={{ "aria-required": true }}
                          >
                            <MenuItem value="">Select batch</MenuItem>
                            {writeOffBatches.map((stock) => (
                              <MenuItem key={stock.id} value={stock.id}>
                                {stock.batchNumber || "No batch"} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                              </MenuItem>
                            ))}
                          </Select>
                          {writeOffFieldErrors.stockBatchId ? <Typography variant="caption" color="error">{writeOffFieldErrors.stockBatchId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField id="writeoff-quantity" size="small" fullWidth type="number" label={<RequiredLabel text="Write-off quantity" required />} value={writeOffForm.quantity} onChange={(e) => setWriteOffForm((current) => ({ ...current, quantity: e.target.value }))} required error={Boolean(writeOffFieldErrors.writeOffQuantity)} helperText={writeOffFieldErrors.writeOffQuantity || "Enter a quantity within available stock."} inputProps={{ min: 1, step: 1, "aria-required": true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <Box id="writeoff-reason" tabIndex={-1}>
                          <CommentSuggestions
                            category="INVENTORY_WRITE_OFF"
                            selectedReason={writeOffForm.reason}
                            remarks={writeOffForm.notes}
                            onReasonChange={(value) => setWriteOffForm((current) => ({ ...current, reason: value }))}
                            onRemarksChange={(value) => setWriteOffForm((current) => ({ ...current, notes: value }))}
                            requiredReason
                            dense
                            maxRemarksLength={250}
                            reasonLabel="Reason"
                            remarksLabel="Notes"
                            reasonHelperText={writeOffFieldErrors.reason || "Reason is required for write-offs."}
                            remarksHelperText={writeOffFieldErrors.notes || `${writeOffForm.notes.length}/250`}
                          />
                        </Box>
                      </Grid>
                    </Grid>
                    <Button variant="contained" disabled={!canManageInventory || saving} onClick={() => void submitWriteOff()}>Post Write-Off</Button>
                    {writeOffHistory.length ? (
                      <TableContainer sx={{ maxHeight: 240 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Qty</TableCell>
                              <TableCell>Reference</TableCell>
                              <TableCell>Reason</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {writeOffHistory.slice(0, 6).map((row) => (
                              <TableRow key={row.id}>
                                <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                                <TableCell align="right">{row.quantity}</TableCell>
                                <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                                <TableCell>{row.reason || "-"}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      <Dialog open={quickMedicineOpen} onClose={() => setQuickMedicineOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Quick Add Medicine</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.25} sx={{ mt: 0.25 }}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Medicine name" value={quickMedicineForm.medicineName} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineName: e.target.value }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="quick-medicine-type-label">Type</InputLabel>
                <Select labelId="quick-medicine-type-label" label="Type" value={quickMedicineForm.medicineType} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineType: String(e.target.value) as MedicineType }))}>
                  {MEDICINE_TYPES.map((type) => (
                    <MenuItem key={type} value={type}>{type}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="quick-medicine-active-label">Active</InputLabel>
                <Select labelId="quick-medicine-active-label" label="Active" value={quickMedicineForm.active ? "true" : "false"} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, active: String(e.target.value) === "true" }))}>
                  <MenuItem value="true">Active</MenuItem>
                  <MenuItem value="false">Inactive</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Generic name" value={quickMedicineForm.genericName || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, genericName: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Brand name" value={quickMedicineForm.brandName || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, brandName: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Category" value={quickMedicineForm.category || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, category: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Strength" value={quickMedicineForm.strength || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, strength: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Unit" value={quickMedicineForm.unit || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, unit: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Manufacturer" value={quickMedicineForm.manufacturer || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, manufacturer: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="Barcode" value={quickMedicineForm.barcode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, barcode: value || null }))} placeholder="Scan or enter barcode" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="QR code" value={quickMedicineForm.qrCode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, qrCode: value || null }))} placeholder="Scan or enter QR code" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="External code" value={quickMedicineForm.externalCode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, externalCode: value || null }))} placeholder="Scan or enter code" />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Default price" value={quickMedicineForm.defaultPrice ?? ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, defaultPrice: e.target.value ? Number(e.target.value) : null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Tax %" value={quickMedicineForm.taxRate ?? ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, taxRate: e.target.value ? Number(e.target.value) : null }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuickMedicineOpen(false)}>Cancel</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
          <Button disabled={saving} onClick={() => void saveQuickMedicine()}>
            {saving ? "Saving..." : "Create Medicine"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
