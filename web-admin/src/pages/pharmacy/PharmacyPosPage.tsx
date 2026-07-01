import * as React from "react";
import {
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
  Collapse,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import AttachFileRoundedIcon from "@mui/icons-material/AttachFileRounded";
import CameraAltRoundedIcon from "@mui/icons-material/CameraAltRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import LocalPrintshopOutlinedIcon from "@mui/icons-material/LocalPrintshopOutlined";
import LocalPharmacyRoundedIcon from "@mui/icons-material/LocalPharmacyRounded";
import PauseCircleOutlineRoundedIcon from "@mui/icons-material/PauseCircleOutlineRounded";
import PointOfSaleRoundedIcon from "@mui/icons-material/PointOfSaleRounded";
import PreviewRoundedIcon from "@mui/icons-material/PreviewRounded";
import RestartAltRoundedIcon from "@mui/icons-material/RestartAltRounded";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import ShoppingCartCheckoutRoundedIcon from "@mui/icons-material/ShoppingCartCheckoutRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import { WorkflowGuide } from "../../components/compact/CompactUi";
import CodeScannerDialog from "../../components/pharmacy/CodeScannerDialog";
import RequiredLabel from "../../components/forms/RequiredLabel.js";
import { useNavigate } from "react-router-dom";
import {
  fileUploadSchema,
  firstZodError,
  mapZodErrors,
  normalizeIndianMobileInput,
  pharmacyPosCartLineSchema,
  pharmacyPosCheckoutSchema,
  pharmacyPosSearchSchema,
} from "@deepthoughtnet/form-validation-kit";
import {
  addPharmacyPosPayment,
  closePharmacyPosShift,
  createPharmacyPosSale,
  getCurrentPharmacyPosShift,
  getPharmacyPosAvailableBatches,
  getPharmacyPosPrescriptionDownloadUrl,
  getPharmacyPosReceiptPdf,
  listPharmacyPosSales,
  listPharmacyPosShifts,
  openPharmacyPosShift,
  returnPharmacyPosSale,
  searchPatients,
  searchPharmacyPosMedicines,
  uploadPharmacyPosPrescription,
  type Patient,
  type PaymentMode,
  type PharmacyPosBatch,
  type PharmacyPosMedicine,
  type PharmacyPosPrescriptionUpload,
  type PharmacyPosSale,
  type PharmacyPosShift,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

type CartLine = {
  medicineId: string;
  medicineName: string;
  quantity: string;
  unitPrice: string;
  discount: string;
  taxRate: string;
  availableQuantity: number;
  earliestExpiryDate: string | null;
};

type HeldDraft = {
  cart: CartLine[];
  customerName: string;
  customerMobile: string;
  notes: string;
  paidAmount: string;
  paymentMode: PaymentMode;
  paymentReference: string;
  patientId: string | null;
  patientLabel: string | null;
  prescription: PharmacyPosPrescriptionUpload | null;
};

type ReturnLineDraft = {
  selected: boolean;
  quantity: string;
  reusable: boolean;
};

type CodeScanMode = "BARCODE" | "QR";
type CartFieldName = "quantity" | "unitPrice" | "discount" | "taxRate";
type StockFilter = "ALL" | "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK" | "EXPIRING_SOON";

const PAYMENT_MODES: PaymentMode[] = ["CASH", "UPI", "CARD", "INSURANCE", "PHONEPE", "GOOGLE_PAY", "PAYTM", "BANK_TRANSFER", "CHEQUE", "OTHER"];
const POS_ROLES = new Set(["CLINIC_ADMIN", "PHARMACIST", "PHARMACY", "PHARMA", "PHARMACY_POS_USER"]);
const HELD_CART_STORAGE_KEY = "pharmacy-pos-held-cart";
const STICKY_TOP = 76;
const PRESCRIPTION_MAX_BYTES = 10 * 1024 * 1024;
const POS_LOW_STOCK_THRESHOLD = 5;

const panelSx = {
  border: "1px solid",
  borderColor: "divider",
  borderRadius: 2,
  bgcolor: "background.paper",
  p: 1.25,
};

function money(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value);
}

function numeric(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function lineGross(line: CartLine) {
  return numeric(line.quantity) * numeric(line.unitPrice);
}

function lineTaxAmount(line: CartLine) {
  const taxable = Math.max(0, lineGross(line) - numeric(line.discount));
  return taxable * (numeric(line.taxRate) / 100);
}

function lineTotal(line: CartLine) {
  return Math.max(0, lineGross(line) - numeric(line.discount) + lineTaxAmount(line));
}

function openPdf(blob: Blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function saleDisplayName(sale: PharmacyPosSale) {
  return sale.patientName ?? sale.customerName ?? "Walk-in";
}

function shortId(value: string | null | undefined) {
  return value ? value.slice(0, 8).toUpperCase() : "NA";
}

function compactBatchPreview(batches: PharmacyPosBatch[] | undefined) {
  if (!batches?.length) return "FEFO allocation auto-selects the earliest non-expired batch.";
  return `FEFO preview: ${batches
    .slice(0, 2)
    .map((batch) => `${batch.batchNumber ?? "NA"} ${batch.expiryDate ?? "NA"} (${batch.availableQuantity})`)
    .join(" | ")}`;
}

function isImageFile(name: string | null | undefined, mediaType?: string | null) {
  if (mediaType?.startsWith("image/")) return true;
  const value = (name || "").toLowerCase();
  return value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp");
}

function expiryWarning(expiryDate: string | null) {
  if (!expiryDate) return null;
  const expiry = new Date(expiryDate);
  const now = new Date();
  expiry.setHours(0, 0, 0, 0);
  now.setHours(0, 0, 0, 0);
  const diffDays = Math.ceil((expiry.getTime() - now.getTime()) / 86_400_000);
  if (diffDays < 0) {
    return { label: "Expired batch excluded from sale.", tone: "error" as const };
  }
  if (diffDays <= 30) {
    return { label: `Expires in ${diffDays} day${diffDays === 1 ? "" : "s"}.`, tone: "warning" as const };
  }
  if (diffDays <= 60) {
    return { label: `Expires within 60 days (${diffDays} left).`, tone: "warning" as const };
  }
  if (diffDays <= 90) {
    return { label: `Expires within 90 days (${diffDays} left).`, tone: "warning" as const };
  }
  return null;
}

function daysUntil(expiryDate: string | null) {
  if (!expiryDate) return Number.POSITIVE_INFINITY;
  const expiry = new Date(expiryDate);
  const now = new Date();
  expiry.setHours(0, 0, 0, 0);
  now.setHours(0, 0, 0, 0);
  return Math.ceil((expiry.getTime() - now.getTime()) / 86_400_000);
}

function isLowStock(quantity: number) {
  return quantity > 0 && quantity <= POS_LOW_STOCK_THRESHOLD;
}

function isExpiringSoon(expiryDate: string | null) {
  const diffDays = daysUntil(expiryDate);
  return diffDays >= 0 && diffDays <= 30;
}

function searchRank(medicine: PharmacyPosMedicine, query: string) {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) return 2;
  const haystacks = [
    medicine.medicineName,
    medicine.genericName,
    medicine.brandName,
    medicine.barcode,
    medicine.qrCode,
    medicine.externalCode,
  ]
    .map((value) => (value || "").toLowerCase())
    .filter(Boolean);
  if (haystacks.some((value) => value === normalizedQuery)) return 0;
  if (haystacks.some((value) => value.startsWith(normalizedQuery))) return 1;
  return 2;
}

function saleUsesMultipleBatches(sale: PharmacyPosSale) {
  const counts = new Map<string, number>();
  sale.items.forEach((item) => {
    counts.set(item.medicineId, (counts.get(item.medicineId) || 0) + 1);
  });
  return Array.from(counts.values()).some((count) => count > 1);
}

function returnableQuantity(item: PharmacyPosSale["items"][number]) {
  return Math.max(0, item.quantity - item.returnedQuantity);
}

function proratedRefundAmount(item: PharmacyPosSale["items"][number], quantity: number) {
  if (item.quantity <= 0 || quantity <= 0) return 0;
  return (item.lineTotal / item.quantity) * quantity;
}

function buildReturnDraft(sale: PharmacyPosSale | null) {
  const next: Record<string, ReturnLineDraft> = {};
  sale?.items.forEach((item) => {
    const remaining = returnableQuantity(item);
    next[item.id] = {
      selected: false,
      quantity: remaining > 0 ? "1" : "0",
      reusable: true,
    };
  });
  return next;
}

function mapPosError(raw: unknown, fallback: string) {
  const message = raw instanceof Error ? raw.message : fallback;
  const normalized = message.toLowerCase();
  if (normalized.includes("open cashier shift")) {
    return "No POS shift is open. Open a shift before collecting payment or completing sale.";
  }
  if (normalized.includes("insufficient stock")) {
    return "Inventory changed while preparing the sale. Please review the cart.";
  }
  if (normalized.includes("payment amount cannot exceed due")) {
    return "Payment amount cannot exceed the remaining due.";
  }
  if (normalized.includes("paid amount cannot exceed sale total")) {
    return "Paid amount cannot exceed sale total.";
  }
  if (normalized.includes("full payment is required")) {
    return "Full payment is required before completing the pharmacy sale.";
  }
  if (normalized.includes("paymentmode is required")) {
    return "Payment mode is required.";
  }
  if (normalized.includes("paidamount is required")) {
    return "Payment amount is required.";
  }
  if (normalized.includes("paid amount cannot be negative")) {
    return "Payment amount cannot be negative.";
  }
  if (normalized.includes("paymentreference is required")) {
    return "Payment reference is required for non-cash payments.";
  }
  if (normalized.includes("at least one sale item is required")) {
    return "Add at least one medicine to the cart.";
  }
  if (normalized.includes("quantity must be positive")) {
    return "Cart item quantity must be positive.";
  }
  if (normalized.includes("return quantity exceeds")) {
    return "Return quantity exceeds the remaining sold quantity.";
  }
  if (normalized.includes("batch expired and cannot be sold or dispensed")) {
    return "Batch expired and cannot be sold or dispensed.";
  }
  if (normalized.includes("medicine is inactive and cannot be sold")) {
    return "Selected medicine is inactive and cannot be sold.";
  }
  if (normalized.includes("unitprice cannot be negative")) {
    return "Rate cannot be negative.";
  }
  if (normalized.includes("discount cannot be negative")) {
    return "Discount cannot be negative.";
  }
  if (normalized.includes("tax cannot be negative")) {
    return "Tax cannot be negative.";
  }
  return fallback;
}

function validatePrescriptionFile(file: File) {
  const parsed = fileUploadSchema({
    required: true,
    allowedMimeTypes: ["application/pdf", "image/jpeg", "image/png", "image/webp"],
    allowedExtensions: ["pdf", "jpg", "jpeg", "png", "webp"],
    maxBytes: PRESCRIPTION_MAX_BYTES,
  }).safeParse(file);
  return parsed.success ? null : firstZodError(parsed.error);
}

function supportsSecureLocalMedia() {
  if (typeof window === "undefined") return false;
  const hostname = window.location.hostname;
  return window.isSecureContext || hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

function scanModeLabel(mode: CodeScanMode) {
  return mode === "QR" ? "QR" : "barcode";
}

function shiftStatusLabel(shift: PharmacyPosShift | null) {
  return shift ? shift.status : "No open shift";
}

export default function PharmacyPosPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const token = auth.accessToken;
  const tenantId = auth.tenantId;
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const canAccessPos = POS_ROLES.has(tenantRole)
    || (tenantRole === "PHARMACY_INVENTORY_MANAGER" && (auth.hasPermission("billing.create") || auth.hasPermission("payment.collect")));

  const searchInputRef = React.useRef<HTMLInputElement | null>(null);
  const customerNameInputRef = React.useRef<HTMLInputElement | null>(null);
  const customerMobileInputRef = React.useRef<HTMLInputElement | null>(null);
  const paidAmountInputRef = React.useRef<HTMLInputElement | null>(null);
  const paymentModeInputRef = React.useRef<HTMLInputElement | null>(null);
  const paymentReferenceInputRef = React.useRef<HTMLInputElement | null>(null);
  const prescriptionInputRef = React.useRef<HTMLInputElement | null>(null);
  const cameraVideoRef = React.useRef<HTMLVideoElement | null>(null);
  const cameraCanvasRef = React.useRef<HTMLCanvasElement | null>(null);
  const cameraStreamRef = React.useRef<MediaStream | null>(null);
  const actionLockRef = React.useRef<string | null>(null);
  const cartInputRefs = React.useRef<Record<string, Partial<Record<CartFieldName, HTMLInputElement | null>>>>({});

  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [activeAction, setActiveAction] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [lastCompletedSale, setLastCompletedSale] = React.useState<PharmacyPosSale | null>(null);
  const [recentDrawerOpen, setRecentDrawerOpen] = React.useState(false);
  const [scanDialogOpen, setScanDialogOpen] = React.useState(false);
  const [scanError, setScanError] = React.useState<string | null>(null);
  const [capturedImageUrl, setCapturedImageUrl] = React.useState<string | null>(null);
  const [capturedImageBlob, setCapturedImageBlob] = React.useState<Blob | null>(null);
  const [codeScanDialogOpen, setCodeScanDialogOpen] = React.useState(false);
  const [codeScanMode, setCodeScanMode] = React.useState<CodeScanMode>("BARCODE");
  const [pendingPrescriptionFile, setPendingPrescriptionFile] = React.useState<File | null>(null);
  const [pendingPrescriptionPreviewUrl, setPendingPrescriptionPreviewUrl] = React.useState<string | null>(null);
  const [prescriptionUploadError, setPrescriptionUploadError] = React.useState<string | null>(null);

  const [medicineQuery, setMedicineQuery] = React.useState("");
  const [medicineResults, setMedicineResults] = React.useState<PharmacyPosMedicine[]>([]);
  const [stockFilter, setStockFilter] = React.useState<StockFilter>("IN_STOCK");
  const [hideUnavailable, setHideUnavailable] = React.useState(true);
  const [batchPreview, setBatchPreview] = React.useState<Record<string, PharmacyPosBatch[]>>({});
  const [cart, setCart] = React.useState<CartLine[]>([]);
  const [heldDraft, setHeldDraft] = React.useState<HeldDraft | null>(null);

  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [selectedCartMedicineId, setSelectedCartMedicineId] = React.useState<string | null>(null);
  const [customerName, setCustomerName] = React.useState("");
  const [customerMobile, setCustomerMobile] = React.useState("");
  const [prescription, setPrescription] = React.useState<PharmacyPosPrescriptionUpload | null>(null);
  const [prescriptionPreviewUrl, setPrescriptionPreviewUrl] = React.useState<string | null>(null);
  const [paidAmount, setPaidAmount] = React.useState("");
  const [paymentMode, setPaymentMode] = React.useState<PaymentMode>("CASH");
  const [paymentReference, setPaymentReference] = React.useState("");
  const [notes, setNotes] = React.useState("");

  const [sales, setSales] = React.useState<PharmacyPosSale[]>([]);
  const [selectedSaleId, setSelectedSaleId] = React.useState<string | null>(null);
  const [selectedSale, setSelectedSale] = React.useState<PharmacyPosSale | null>(null);

  const [paymentAmount, setPaymentAmount] = React.useState("");
  const [paymentTopupMode, setPaymentTopupMode] = React.useState<PaymentMode>("CASH");
  const [paymentReferenceTopup, setPaymentReferenceTopup] = React.useState("");
  const [currentShift, setCurrentShift] = React.useState<PharmacyPosShift | null>(null);
  const [shiftHistory, setShiftHistory] = React.useState<PharmacyPosShift[]>([]);
  const [openShiftDialogOpen, setOpenShiftDialogOpen] = React.useState(false);
  const [closeShiftDialogOpen, setCloseShiftDialogOpen] = React.useState(false);
  const [openingCashAmount, setOpeningCashAmount] = React.useState("0");
  const [shiftOpenNotes, setShiftOpenNotes] = React.useState("");
  const [actualCashAmount, setActualCashAmount] = React.useState("");
  const [actualUpiAmount, setActualUpiAmount] = React.useState("");
  const [actualCardAmount, setActualCardAmount] = React.useState("");
  const [actualOtherAmount, setActualOtherAmount] = React.useState("");
  const [shiftCloseNotes, setShiftCloseNotes] = React.useState("");
  const [saleSearchQuery, setSaleSearchQuery] = React.useState("");
  const [returnDraft, setReturnDraft] = React.useState<Record<string, ReturnLineDraft>>({});
  const [returnReason, setReturnReason] = React.useState("");
  const [returnMode, setReturnMode] = React.useState<PaymentMode>("CASH");
  const [returnReference, setReturnReference] = React.useState("");
  const [returnDrawerOpen, setReturnDrawerOpen] = React.useState(false);
  const [shiftHistoryDrawerOpen, setShiftHistoryDrawerOpen] = React.useState(false);
  const [customerSectionOpen, setCustomerSectionOpen] = React.useState(true);
  const [prescriptionSectionOpen, setPrescriptionSectionOpen] = React.useState(false);
  const [saleConfirmOpen, setSaleConfirmOpen] = React.useState(false);
  const [clearCartConfirmOpen, setClearCartConfirmOpen] = React.useState(false);
  const [previewDialogOpen, setPreviewDialogOpen] = React.useState(false);
  const [previewDocumentName, setPreviewDocumentName] = React.useState<string | null>(null);
  const [previewDocumentUrl, setPreviewDocumentUrl] = React.useState<string | null>(null);
  const [previewDocumentIsImage, setPreviewDocumentIsImage] = React.useState(false);

  const subtotal = React.useMemo(() => cart.reduce((sum, line) => sum + lineGross(line), 0), [cart]);
  const discountTotal = React.useMemo(() => cart.reduce((sum, line) => sum + numeric(line.discount), 0), [cart]);
  const taxTotal = React.useMemo(() => cart.reduce((sum, line) => sum + lineTaxAmount(line), 0), [cart]);
  const total = React.useMemo(() => cart.reduce((sum, line) => sum + lineTotal(line), 0), [cart]);
  const duePreview = Math.max(0, total - numeric(paidAmount));
  const filteredSales = React.useMemo(() => {
    const term = saleSearchQuery.trim().toLowerCase();
    if (!term) return sales;
    return sales.filter((sale) =>
      sale.saleNumber.toLowerCase().includes(term)
      || saleDisplayName(sale).toLowerCase().includes(term)
      || (sale.customerMobile || "").toLowerCase().includes(term)
      || sale.saleDateTime.slice(0, 10).includes(term),
    );
  }, [saleSearchQuery, sales]);
  const selectedReturnRows = React.useMemo(() => {
    if (!selectedSale) return [];
    return selectedSale.items
      .map((item) => ({ item, draft: returnDraft[item.id] }))
      .filter((entry) => entry.draft?.selected);
  }, [returnDraft, selectedSale]);
  const refundEstimate = React.useMemo(() =>
    selectedReturnRows.reduce((sum, entry) => {
      const qty = Math.min(returnableQuantity(entry.item), Math.max(0, numeric(entry.draft.quantity)));
      return sum + proratedRefundAmount(entry.item, qty);
    }, 0), [selectedReturnRows]);
  const reusableSelectedCount = React.useMemo(() =>
    selectedReturnRows.filter((entry) => entry.draft.reusable).length, [selectedReturnRows]);
  const discardSelectedCount = selectedReturnRows.length - reusableSelectedCount;
  const paymentTopupBlocked = !currentShift;
  const canManageInventory = auth.hasPermission("inventory.manage") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PHARMACY_INVENTORY_MANAGER");
  const closeVariancePreview = React.useMemo(() => {
    const actualTotal = numeric(actualCashAmount) + numeric(actualUpiAmount) + numeric(actualCardAmount) + numeric(actualOtherAmount);
    return actualTotal - (currentShift?.expectedTotalAmount ?? 0);
  }, [actualCardAmount, actualCashAmount, actualOtherAmount, actualUpiAmount, currentShift]);
  const customerSummary = React.useMemo(() => {
    const name = selectedPatient
      ? `${selectedPatient.firstName} ${selectedPatient.lastName}`.trim()
      : customerName.trim();
    const mobile = customerMobile.trim();
    if (!name && !mobile) return "No customer selected";
    return `${name || "Walk-in"}${mobile ? ` • ${mobile}` : ""}`;
  }, [customerMobile, customerName, selectedPatient]);
  const saleSummary = React.useMemo(() => ({
    itemCount: cart.length,
    totalAmount: money(total),
    paymentMode,
    amountReceived: money(Math.max(0, numeric(paidAmount))),
  }), [cart.length, paidAmount, paymentMode, total]);
  const paidAmountValue = numeric(paidAmount);
  const paidAmountMissing = total > 0 && paidAmountValue <= 0;
  const requiresReference = paidAmountValue > 0 && paymentMode !== "CASH";
  const medicineResultsOrdered = React.useMemo(() => {
    const filtered = medicineResults.filter((medicine) => {
      const outOfStock = medicine.totalAvailableQuantity <= 0;
      const lowStock = isLowStock(medicine.totalAvailableQuantity);
      const expiringSoon = isExpiringSoon(medicine.earliestExpiryDate);
      if (hideUnavailable && stockFilter !== "OUT_OF_STOCK" && outOfStock) {
        return false;
      }
      switch (stockFilter) {
        case "IN_STOCK":
          return !outOfStock;
        case "LOW_STOCK":
          return lowStock;
        case "OUT_OF_STOCK":
          return outOfStock;
        case "EXPIRING_SOON":
          return expiringSoon;
        default:
          return true;
      }
    });
    return filtered.sort((left, right) => {
      const rankDiff = searchRank(left, medicineQuery) - searchRank(right, medicineQuery);
      if (rankDiff !== 0) return rankDiff;
      const leftInStock = left.totalAvailableQuantity > 0 ? 0 : 1;
      const rightInStock = right.totalAvailableQuantity > 0 ? 0 : 1;
      if (leftInStock !== rightInStock) return leftInStock - rightInStock;
      const expiryDiff = daysUntil(left.earliestExpiryDate) - daysUntil(right.earliestExpiryDate);
      if (expiryDiff !== 0) return expiryDiff;
      return left.medicineName.localeCompare(right.medicineName);
    });
  }, [hideUnavailable, medicineQuery, medicineResults, stockFilter]);

  const medicineSearchValidation = React.useMemo(
    () => pharmacyPosSearchSchema.safeParse(medicineQuery),
    [medicineQuery],
  );
  const recentSalesSearchValidation = React.useMemo(
    () => pharmacyPosSearchSchema.safeParse(saleSearchQuery),
    [saleSearchQuery],
  );
  const saleSubmissionDraft = React.useMemo(() => ({
    items: cart.map((line) => ({
      medicineId: line.medicineId,
      quantity: numeric(line.quantity),
      availableQuantity: line.availableQuantity,
      unitPrice: numeric(line.unitPrice),
      discount: numeric(line.discount),
      taxRate: numeric(line.taxRate),
    })),
    patientId: selectedPatient?.id ?? null,
    customerName: selectedPatient ? null : customerName,
    customerMobile: selectedPatient ? null : (customerMobile.trim() ? (normalizeIndianMobileInput(customerMobile) as string) : null),
    grandTotal: total,
    paidAmount: numeric(paidAmount),
    paymentMode: paymentMode,
    paymentReference: paymentReference,
    prescriptionDocumentId: prescription?.documentId ?? null,
    notes,
  }), [cart, customerMobile, customerName, notes, paidAmount, paymentMode, paymentReference, prescription?.documentId, selectedPatient?.id, total]);
  const saleSubmissionValidation = React.useMemo(
    () => pharmacyPosCheckoutSchema.safeParse(saleSubmissionDraft),
    [saleSubmissionDraft],
  );
  const saleFieldErrors = React.useMemo(
    () => (saleSubmissionValidation.success ? {} : mapZodErrors(saleSubmissionValidation.error)),
    [saleSubmissionValidation],
  );
  const saleValidationMessage = React.useMemo(() => {
    if (!currentShift) {
      return "No POS shift is open. Open a shift before collecting payment or completing sale.";
    }
    if (!cart.length) {
      return "Add at least one medicine to the cart.";
    }
    if (paidAmountMissing) {
      return "Enter paid amount.";
    }
    if (!saleSubmissionValidation.success) {
      return firstZodError(saleSubmissionValidation.error);
    }
    return null;
  }, [cart.length, currentShift, paidAmountMissing, saleSubmissionValidation]);
  const cartHasStockIssue = React.useMemo(
    () => cart.some((line, index) => Boolean(saleFieldErrors[`items.${index}.quantity`]) || Boolean(saleFieldErrors[`items.${index}.discount`]) || Boolean(saleFieldErrors[`items.${index}.unitPrice`]) || Boolean(saleFieldErrors[`items.${index}.taxRate`])),
    [cart, saleFieldErrors],
  );
  const completeSaleDisabledReason = React.useMemo(() => {
    if (!currentShift) {
      return "Open POS shift before sale.";
    }
    if (!cart.length) {
      return "Add at least one medicine.";
    }
    if (paidAmountMissing) {
      return "Enter paid amount.";
    }
    if (cartHasStockIssue) {
      return "Out of stock. Add stock before sale.";
    }
    if (saleValidationMessage) {
      return saleValidationMessage;
    }
    return null;
  }, [cart.length, cartHasStockIssue, currentShift, paidAmountMissing, saleValidationMessage]);
  const focusFirstSaleValidationError = React.useCallback(() => {
    for (let index = 0; index < cart.length; index += 1) {
      const medicineId = cart[index]?.medicineId;
      const refs = medicineId ? cartInputRefs.current[medicineId] : undefined;
      for (const field of ["quantity", "unitPrice", "discount", "taxRate"] as CartFieldName[]) {
        if (saleFieldErrors[`items.${index}.${field}`]) {
          refs?.[field]?.focus?.();
          return;
        }
      }
    }
    if (saleFieldErrors.customerName) {
      customerNameInputRef.current?.focus();
      return;
    }
    if (saleFieldErrors.customerMobile) {
      customerMobileInputRef.current?.focus();
      return;
    }
    if (saleFieldErrors.paidAmount) {
      paidAmountInputRef.current?.focus();
      paidAmountInputRef.current?.select();
      return;
    }
    if (saleFieldErrors.paymentMode) {
      paymentModeInputRef.current?.focus?.();
      return;
    }
    if (saleFieldErrors.paymentReference) {
      paymentReferenceInputRef.current?.focus();
      return;
    }
    if (saleFieldErrors.notes) {
      document.querySelector<HTMLTextAreaElement>('textarea[name="sale-notes"]')?.focus();
    }
  }, [cart, saleFieldErrors]);

  const beginAction = React.useCallback((name: string) => {
    if (actionLockRef.current) return false;
    actionLockRef.current = name;
    setActiveAction(name);
    setSubmitting(true);
    return true;
  }, []);

  const endAction = React.useCallback(() => {
    actionLockRef.current = null;
    setActiveAction(null);
    setSubmitting(false);
  }, []);

  const clearDraft = React.useCallback(() => {
    setSaleConfirmOpen(false);
    setClearCartConfirmOpen(false);
    setCart([]);
    setSelectedCartMedicineId(null);
    setCustomerName("");
    setCustomerMobile("");
    setPaidAmount("");
    setPaymentReference("");
    setNotes("");
    setPatientQuery("");
    setPatientResults([]);
    setSelectedPatient(null);
    setPrescription(null);
    setCustomerSectionOpen(true);
    setPrescriptionSectionOpen(false);
    window.setTimeout(() => searchInputRef.current?.focus(), 0);
  }, []);

  const confirmClearDraft = React.useCallback(() => {
    if (!cart.length && !customerName.trim() && !customerMobile.trim() && !paidAmount.trim() && !prescription) {
      clearDraft();
      return;
    }
    setClearCartConfirmOpen(true);
  }, [cart.length, clearDraft, customerMobile, customerName, paidAmount, prescription]);

  const refreshSales = React.useCallback(async () => {
    if (!token || !tenantId || !canAccessPos) return;
    const saleRows = await listPharmacyPosSales(token, tenantId);
    setSales(saleRows);
    setSelectedSale((current) => {
      if (!current) return saleRows[0] ?? null;
      return saleRows.find((row) => row.id === current.id) ?? saleRows[0] ?? null;
    });
    setSelectedSaleId((current) => current ?? saleRows[0]?.id ?? null);
  }, [canAccessPos, tenantId, token]);

  const refreshMedicineResults = React.useCallback(async (query: string = medicineQuery) => {
    if (!token || !tenantId || !canAccessPos) return;
    if (!pharmacyPosSearchSchema.safeParse(query).success) {
      setMedicineResults([]);
      return;
    }
    const rows = await searchPharmacyPosMedicines(token, tenantId, query);
    setMedicineResults(rows);
  }, [canAccessPos, medicineQuery, tenantId, token]);

  const refreshShifts = React.useCallback(async () => {
    if (!token || !tenantId || !canAccessPos) return;
    const [current, history] = await Promise.all([
      getCurrentPharmacyPosShift(token, tenantId),
      listPharmacyPosShifts(token, tenantId),
    ]);
    setCurrentShift(current);
    setShiftHistory(history);
  }, [canAccessPos, tenantId, token]);

  React.useEffect(() => {
    searchInputRef.current?.focus();
    try {
      const raw = window.sessionStorage.getItem(HELD_CART_STORAGE_KEY);
      if (raw) setHeldDraft(JSON.parse(raw) as HeldDraft);
    } catch {
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
    }
  }, []);

  const stopCameraStream = React.useCallback(() => {
    if (cameraStreamRef.current) {
      cameraStreamRef.current.getTracks().forEach((track) => track.stop());
      cameraStreamRef.current = null;
    }
    if (cameraVideoRef.current) {
      cameraVideoRef.current.srcObject = null;
    }
  }, []);

  const resetCapturedImage = React.useCallback(() => {
    setCapturedImageBlob(null);
    setCapturedImageUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return null;
    });
  }, []);

  const clearPendingPrescriptionPreview = React.useCallback(() => {
    setPendingPrescriptionFile(null);
    setPrescriptionUploadError(null);
    setPendingPrescriptionPreviewUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return null;
    });
  }, []);

  const startCameraStream = React.useCallback(async () => {
    if (!supportsSecureLocalMedia()) {
      setScanError("Camera permission required. You can also upload an image.");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setScanError("Camera permission required. You can also upload an image.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: "environment" },
        },
        audio: false,
      });
      cameraStreamRef.current = stream;
      if (cameraVideoRef.current) {
        cameraVideoRef.current.srcObject = stream;
        await cameraVideoRef.current.play();
      }
      setScanError(null);
    } catch (err) {
      if (err instanceof DOMException && (err.name === "NotAllowedError" || err.name === "SecurityError")) {
        setScanError("Camera permission required. You can also upload an image.");
        return;
      }
      if (err instanceof DOMException && (err.name === "NotFoundError" || err.name === "OverconstrainedError" || err.name === "DevicesNotFoundError")) {
        setScanError("No camera available on this device. Camera permission required. You can also upload an image.");
        return;
      }
      setScanError("Unable to start camera. Camera permission required. You can also upload an image.");
    }
  }, []);

  React.useEffect(() => {
    if (!scanDialogOpen) {
      stopCameraStream();
      return;
    }
    setScanError(null);
    resetCapturedImage();
    void startCameraStream();
    return () => {
      stopCameraStream();
    };
  }, [resetCapturedImage, scanDialogOpen, startCameraStream, stopCameraStream]);

  React.useEffect(() => () => {
    stopCameraStream();
    setCapturedImageUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return null;
    });
  }, [stopCameraStream]);

  React.useEffect(() => () => {
    if (pendingPrescriptionPreviewUrl) {
      URL.revokeObjectURL(pendingPrescriptionPreviewUrl);
    }
  }, [pendingPrescriptionPreviewUrl]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!token || !tenantId || !canAccessPos) {
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        const saleRows = await listPharmacyPosSales(token, tenantId);
        const [currentShiftRow, shiftRows] = await Promise.all([
          getCurrentPharmacyPosShift(token, tenantId),
          listPharmacyPosShifts(token, tenantId),
        ]);
        if (cancelled) return;
        setSales(saleRows);
        setSelectedSale(saleRows[0] ?? null);
        setSelectedSaleId(saleRows[0]?.id ?? null);
        setCurrentShift(currentShiftRow);
        setShiftHistory(shiftRows);
        setError(null);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load Pharmacy POS");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [canAccessPos, tenantId, token]);

  React.useEffect(() => {
    if (!token || !tenantId || !canAccessPos) return;
    if (!medicineSearchValidation.success) {
      setMedicineResults([]);
      return;
    }
    const handle = window.setTimeout(async () => {
      try {
        await refreshMedicineResults(medicineQuery);
      } catch (err) {
        setError("Medicine search could not be refreshed.");
      }
    }, 180);
    return () => window.clearTimeout(handle);
  }, [canAccessPos, medicineQuery, medicineSearchValidation.success, refreshMedicineResults, tenantId, token]);

  React.useEffect(() => {
    if (!token || !tenantId || !canAccessPos || patientQuery.trim().length < 2) {
      setPatientResults([]);
      return;
    }
    const handle = window.setTimeout(async () => {
      try {
        const rows = await searchPatients(token, tenantId, { name: patientQuery.trim(), active: true });
        setPatientResults(rows.slice(0, 6));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to search patients");
      }
    }, 200);
    return () => window.clearTimeout(handle);
  }, [canAccessPos, patientQuery, tenantId, token]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadPrescriptionPreview() {
      if (!prescription?.documentId || !token || !tenantId || !isImageFile(prescription.fileName, prescription.mediaType)) {
        setPrescriptionPreviewUrl(null);
        return;
      }
      try {
        const { url } = await getPharmacyPosPrescriptionDownloadUrl(token, tenantId, prescription.documentId);
        if (!cancelled) {
          setPrescriptionPreviewUrl(url);
        }
      } catch {
        if (!cancelled) {
          setPrescriptionPreviewUrl(null);
        }
      }
    }
    void loadPrescriptionPreview();
    return () => {
      cancelled = true;
    };
  }, [prescription, tenantId, token]);

  React.useEffect(() => {
    setReturnDraft(buildReturnDraft(selectedSale));
  }, [selectedSale]);

  const adjustCartQuantity = React.useCallback((medicineId: string, nextQuantity: number) => {
    const line = cart.find((item) => item.medicineId === medicineId);
    if (!line) return false;
    if (nextQuantity > line.availableQuantity) {
      setError("Requested quantity exceeds available stock.");
      return false;
    }
    setCart((current) => current.map((item) => (
      item.medicineId === medicineId
        ? { ...item, quantity: String(nextQuantity) }
        : item
    )));
    setSelectedCartMedicineId(medicineId);
    setError(null);
    return true;
  }, [cart]);

  const addMedicine = React.useCallback(async (medicine: PharmacyPosMedicine) => {
    if (medicine.totalAvailableQuantity <= 0) {
      setError("Out of stock. Add stock before sale.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return false;
    }
    let blocked = false;
    setCart((current) => {
      const existing = current.find((line) => line.medicineId === medicine.medicineId);
      if (existing) {
        const nextQuantity = numeric(existing.quantity) + 1;
        if (nextQuantity > medicine.totalAvailableQuantity) {
          blocked = true;
          return current;
        }
        return current.map((line) =>
          line.medicineId === medicine.medicineId
            ? { ...line, quantity: String(nextQuantity) }
            : line,
        );
      }
      return [
        {
          medicineId: medicine.medicineId,
          medicineName: medicine.medicineName,
          quantity: "1",
          unitPrice: String(medicine.defaultUnitPrice ?? 0),
          discount: "0",
          taxRate: String(medicine.taxRate ?? 0),
          availableQuantity: medicine.totalAvailableQuantity,
          earliestExpiryDate: medicine.earliestExpiryDate,
        },
        ...current,
      ];
    });
    if (blocked) {
      setError("Requested quantity exceeds available stock.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return false;
    }
    setSelectedCartMedicineId(medicine.medicineId);
    setMedicineQuery("");
    window.setTimeout(() => searchInputRef.current?.focus(), 0);
    setError(null);
    if (batchPreview[medicine.medicineId] || !token || !tenantId) return true;
    try {
      const rows = await getPharmacyPosAvailableBatches(token, tenantId, medicine.medicineId);
      setBatchPreview((current) => ({ ...current, [medicine.medicineId]: rows }));
    } catch (err) {
      setError("Available stock batches could not be loaded.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
    }
    return true;
  }, [batchPreview, tenantId, token]);

  const handleScannedCode = React.useCallback(async (rawValue: string, mode: CodeScanMode) => {
    if (!token || !tenantId) {
      return;
    }
    const value = rawValue.trim();
    if (!value) {
      setError("No code was detected. Try again or enter the code manually.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return;
    }
    const validation = pharmacyPosSearchSchema.safeParse(value);
    if (!validation.success) {
      setError(firstZodError(validation.error));
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return;
    }
    setMedicineQuery(value);
    try {
      const rows = await searchPharmacyPosMedicines(token, tenantId, validation.data ?? value);
      setMedicineResults(rows);
      if (!rows.length) {
        setError("No medicine found for this code. If stock exists, only expired stock may be available.");
      } else {
        setSuccess(`${scanModeLabel(mode)} scan filled search with ${value}.`);
      }
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
    } catch {
      setError("Medicine lookup failed after scanning. Please retry or use the search box.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
    }
  }, [tenantId, token]);

  const updateCartLine = React.useCallback((medicineId: string, patch: Partial<CartLine>) => {
    setSelectedCartMedicineId(medicineId);
    setCart((current) => current.map((line) => (line.medicineId === medicineId ? { ...line, ...patch } : line)));
  }, []);

  const removeCartLine = React.useCallback((medicineId: string) => {
    setCart((current) => {
      const next = current.filter((line) => line.medicineId !== medicineId);
      if (selectedCartMedicineId === medicineId) {
        setSelectedCartMedicineId(next[0]?.medicineId ?? null);
      }
      return next;
    });
  }, [selectedCartMedicineId]);

  const selectSale = React.useCallback((sale: PharmacyPosSale) => {
    setSelectedSaleId(sale.id);
    setSelectedSale(sale);
    setPaymentAmount(sale.dueAmount > 0 ? String(sale.dueAmount) : "");
    setPaymentTopupMode("CASH");
    setReturnDraft(buildReturnDraft(sale));
    setReturnMode("CASH");
    setReturnReason("");
    setReturnReference("");
  }, []);

  const clearCustomerSelection = React.useCallback(() => {
    setSelectedPatient(null);
    setPatientQuery("");
    setPatientResults([]);
    setCustomerName("");
    setCustomerMobile("");
    setCustomerSectionOpen(true);
  }, []);

  const holdCurrentCart = React.useCallback(() => {
    if (!cart.length) {
      if (!heldDraft) {
        setError("There is no active cart to hold.");
        return;
      }
      const restoredPatient = heldDraft.patientId
        ? {
            id: heldDraft.patientId,
            tenantId: tenantId ?? "",
            patientNumber: "",
            firstName: heldDraft.patientLabel ?? "Patient",
            lastName: "",
            gender: "UNKNOWN",
            dateOfBirth: null,
            ageYears: null,
            mobile: "",
            email: null,
            addressLine1: null,
            addressLine2: null,
            city: null,
            state: null,
            country: null,
            postalCode: null,
            emergencyContactName: null,
            emergencyContactMobile: null,
            bloodGroup: null,
            allergies: null,
            existingConditions: null,
            longTermMedications: null,
            surgicalHistory: null,
            notes: null,
            active: true,
            createdAt: "",
            updatedAt: "",
          } as Patient
        : null;
      setCart(heldDraft.cart);
      setSelectedCartMedicineId(heldDraft.cart[0]?.medicineId ?? null);
      setCustomerName(heldDraft.customerName);
      setCustomerMobile(heldDraft.customerMobile);
      setNotes(heldDraft.notes);
      setPaidAmount(heldDraft.paidAmount);
      setPaymentMode(heldDraft.paymentMode);
      setPaymentReference(heldDraft.paymentReference);
      setSelectedPatient(restoredPatient);
      setPrescription(heldDraft.prescription);
      setHeldDraft(null);
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
      setSuccess("Held cart restored.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return;
    }
    const draft: HeldDraft = {
      cart,
      customerName,
      customerMobile,
      notes,
      paidAmount,
      paymentMode,
      paymentReference,
      patientId: selectedPatient?.id ?? null,
      patientLabel: selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName}`.trim() : null,
      prescription,
    };
    setHeldDraft(draft);
    window.sessionStorage.setItem(HELD_CART_STORAGE_KEY, JSON.stringify(draft));
    clearDraft();
    setSuccess("Cart held. Use Hold Cart again on an empty cart to restore it.");
  }, [cart, clearDraft, customerMobile, customerName, heldDraft, notes, paidAmount, paymentMode, paymentReference, prescription, selectedPatient, tenantId]);

  const executeSale = React.useCallback(async () => {
    if (!token || !tenantId) return;
    if (!beginAction("sale")) return;
    if (!currentShift) {
      setError("No POS shift is open. Open a shift before collecting payment or completing sale.");
      endAction();
      return;
    }
    if (!saleSubmissionValidation.success) {
      setError(firstZodError(saleSubmissionValidation.error) || "Sale could not be completed. Stock was not deducted.");
      focusFirstSaleValidationError();
      endAction();
      return;
    }
    try {
      const sale = await createPharmacyPosSale(token, tenantId, {
        patientId: selectedPatient?.id ?? null,
        customerName: selectedPatient ? null : customerName.trim() || null,
        customerMobile: selectedPatient ? null : (customerMobile.trim() ? (normalizeIndianMobileInput(customerMobile) as string) : null),
        prescriptionDocumentId: prescription?.documentId ?? null,
        paidAmount: numeric(paidAmount),
        paymentMode,
        paymentReference: paymentReference.trim() || null,
        notes: notes.trim() || null,
        items: cart.map((line) => ({
          medicineId: line.medicineId,
          quantity: numeric(line.quantity),
          unitPrice: numeric(line.unitPrice),
          discount: numeric(line.discount),
          tax: lineTaxAmount(line),
        })),
      });
      clearDraft();
      setBatchPreview({});
      setLastCompletedSale(sale);
      setSuccess(`Sale ${sale.saleNumber} created. FEFO allocation used the earliest non-expired batches and stock movements were recorded.`);
      await Promise.all([refreshSales(), refreshShifts(), refreshMedicineResults("")]);
      selectSale(sale);
      setHeldDraft(null);
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
      setError(null);
    } catch (err) {
      setError(mapPosError(err, "Sale could not be completed. Stock was not deducted."));
    } finally {
      endAction();
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
    }
  }, [beginAction, cart, clearDraft, currentShift, customerMobile, customerName, endAction, focusFirstSaleValidationError, notes, paidAmount, paymentMode, paymentReference, prescription, refreshMedicineResults, refreshSales, refreshShifts, saleSubmissionValidation, selectSale, selectedPatient, tenantId, token]);

  const requestSaleConfirmation = React.useCallback(() => {
    if (saleValidationMessage) {
      setError(saleValidationMessage);
      focusFirstSaleValidationError();
      return;
    }
    setSaleConfirmOpen(true);
  }, [focusFirstSaleValidationError, saleValidationMessage]);

  const printReceipt = React.useCallback(async (saleId: string) => {
    if (!token || !tenantId) return;
    try {
      const pdf = await getPharmacyPosReceiptPdf(token, tenantId, saleId);
      openPdf(pdf.blob);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate receipt");
    }
  }, [tenantId, token]);

  const previewPrescription = React.useCallback(async (documentId: string) => {
    if (!token || !tenantId) return;
    try {
      const { url } = await getPharmacyPosPrescriptionDownloadUrl(token, tenantId, documentId);
      const currentName = prescription?.documentId === documentId
        ? prescription.fileName
        : selectedSale?.prescriptionDocumentId === documentId
          ? selectedSale.prescriptionFileName
          : "Prescription";
      const currentMediaType = prescription?.documentId === documentId ? prescription.mediaType : null;
      setPreviewDocumentName(currentName ?? "Prescription");
      setPreviewDocumentUrl(url);
      setPreviewDocumentIsImage(isImageFile(currentName, currentMediaType));
      setPreviewDialogOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open prescription preview");
    }
  }, [prescription, selectedSale, tenantId, token]);

  const uploadPrescription = React.useCallback(async (file: File | null) => {
    if (!file || !token || !tenantId) return;
    const validationError = validatePrescriptionFile(file);
    if (validationError) {
      setPrescriptionUploadError(validationError);
      setError(validationError);
      return;
    }
    clearPendingPrescriptionPreview();
    setPendingPrescriptionFile(file);
    setPrescriptionUploadError(null);
    if (isImageFile(file.name, file.type) || file.type === "application/pdf") {
      setPendingPrescriptionPreviewUrl(URL.createObjectURL(file));
    }
    if (!beginAction("prescription-upload")) return;
    try {
      const uploaded = await uploadPharmacyPosPrescription(token, tenantId, file);
      setPrescription(uploaded);
      setPrescriptionSectionOpen(true);
      setSuccess(`Prescription ${uploaded.fileName} uploaded for this draft sale.`);
      setError(null);
      setPrescriptionUploadError(null);
    } catch (err) {
      setError(mapPosError(err, "Prescription upload failed. Please retry or continue without upload."));
    } finally {
      endAction();
      if (prescriptionInputRef.current) {
        prescriptionInputRef.current.value = "";
      }
    }
  }, [beginAction, clearPendingPrescriptionPreview, endAction, tenantId, token]);

  const capturePrescriptionImage = React.useCallback(() => {
    const video = cameraVideoRef.current;
    const canvas = cameraCanvasRef.current;
    if (!video || !canvas) {
      setScanError("Camera preview is not ready yet.");
      return;
    }
    const width = video.videoWidth || 1280;
    const height = video.videoHeight || 720;
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
      setScanError("Unable to capture prescription image.");
      return;
    }
    context.drawImage(video, 0, 0, width, height);
    canvas.toBlob((blob) => {
      if (!blob) {
        setScanError("Unable to capture prescription image.");
        return;
      }
      stopCameraStream();
      resetCapturedImage();
      setCapturedImageBlob(blob);
      setCapturedImageUrl(URL.createObjectURL(blob));
      setScanError(null);
    }, "image/jpeg", 0.92);
  }, [resetCapturedImage, stopCameraStream]);

  const retakePrescriptionImage = React.useCallback(() => {
    resetCapturedImage();
    setScanError(null);
    void startCameraStream();
  }, [resetCapturedImage, startCameraStream]);

  const useCapturedPrescriptionImage = React.useCallback(async () => {
    if (!capturedImageBlob) {
      setScanError("Capture a prescription image before uploading.");
      return;
    }
    const filename = `prescription-scan-${new Date().toISOString().replace(/[:.]/g, "-")}.jpg`;
    const file = new File([capturedImageBlob], filename, { type: "image/jpeg" });
    await uploadPrescription(file);
    setScanDialogOpen(false);
    resetCapturedImage();
  }, [capturedImageBlob, resetCapturedImage, uploadPrescription]);

  const removePrescriptionAttachment = React.useCallback(() => {
    setPrescription(null);
    setPrescriptionPreviewUrl(null);
    clearPendingPrescriptionPreview();
    if (prescriptionInputRef.current) {
      prescriptionInputRef.current.value = "";
    }
  }, [clearPendingPrescriptionPreview]);

  const openCodeScanner = React.useCallback((mode: CodeScanMode) => {
    setCodeScanMode(mode);
    setCodeScanDialogOpen(true);
  }, []);

  const closeCodeScanner = React.useCallback(() => {
    setCodeScanDialogOpen(false);
    window.setTimeout(() => searchInputRef.current?.focus(), 0);
  }, []);

  React.useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      const inEditable = tag === "INPUT" || tag === "TEXTAREA" || target?.getAttribute("contenteditable") === "true";

      if (event.key === "Escape") {
        if (codeScanDialogOpen) {
          event.preventDefault();
          closeCodeScanner();
          return;
        }
        if (scanDialogOpen) {
          event.preventDefault();
          setScanDialogOpen(false);
          stopCameraStream();
          resetCapturedImage();
          setScanError(null);
          return;
        }
        if (recentDrawerOpen) {
          event.preventDefault();
          setRecentDrawerOpen(false);
          return;
        }
        if (medicineQuery.trim()) {
          event.preventDefault();
          setMedicineQuery("");
          window.setTimeout(() => searchInputRef.current?.focus(), 0);
        }
        return;
      }

      if (event.key === "F2") {
        event.preventDefault();
        paidAmountInputRef.current?.focus();
        paidAmountInputRef.current?.select();
        return;
      }

      if (event.key === "F4") {
        event.preventDefault();
        if (!submitting) {
          requestSaleConfirmation();
        }
        return;
      }

      if (!selectedCartMedicineId || scanDialogOpen || codeScanDialogOpen || recentDrawerOpen || inEditable) {
        return;
      }

      if ((event.key === "+" || event.key === "=") && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        if (selectedCartMedicineId) {
          const line = cart.find((item) => item.medicineId === selectedCartMedicineId);
          if (line) {
            void adjustCartQuantity(selectedCartMedicineId, numeric(line.quantity) + 1);
          }
        }
        return;
      }

      if (event.key === "-" && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        if (selectedCartMedicineId) {
          const line = cart.find((item) => item.medicineId === selectedCartMedicineId);
          if (line) {
            void adjustCartQuantity(selectedCartMedicineId, Math.max(1, numeric(line.quantity) - 1));
          }
        }
        return;
      }

      if (tag !== "BUTTON") {
        window.setTimeout(() => searchInputRef.current?.focus(), 0);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [
    cart,
    cart.length,
    cartHasStockIssue,
    adjustCartQuantity,
    closeCodeScanner,
    codeScanDialogOpen,
    medicineQuery,
    recentDrawerOpen,
    resetCapturedImage,
    scanDialogOpen,
    selectedCartMedicineId,
    stopCameraStream,
    requestSaleConfirmation,
    submitting,
  ]);

  const submitPayment = React.useCallback(async () => {
    if (!token || !tenantId || !selectedSale) return;
    if (!beginAction("payment")) return;
    if (!currentShift) {
      setError("Payment requires an open cashier shift.");
      endAction();
      return;
    }
    try {
      const sale = await addPharmacyPosPayment(token, tenantId, selectedSale.id, {
        amount: numeric(paymentAmount),
        paymentMode: paymentTopupMode,
        referenceNumber: paymentReferenceTopup.trim() || null,
      });
      await Promise.all([refreshSales(), refreshShifts(), refreshMedicineResults(medicineQuery)]);
      selectSale(sale);
      setPaymentReferenceTopup("");
      setSuccess(`Payment recorded against sale ${sale.saleNumber}.`);
      setError(null);
    } catch (err) {
      setError(mapPosError(err, "Payment could not be recorded."));
    } finally {
      endAction();
    }
  }, [beginAction, currentShift, endAction, medicineQuery, paymentAmount, paymentReferenceTopup, paymentTopupMode, refreshMedicineResults, refreshSales, refreshShifts, selectSale, selectedSale, tenantId, token]);

  const submitReturn = React.useCallback(async () => {
    if (!token || !tenantId || !selectedSale) return;
    if (!beginAction("return")) return;
    const requestItems = selectedSale.items.flatMap((item) => {
      const draft = returnDraft[item.id];
      if (!draft?.selected) return [];
      const quantity = Math.max(0, numeric(draft.quantity));
      if (quantity <= 0) return [];
      return [{
        saleItemId: item.id,
        quantity,
        reusable: draft.reusable,
      }];
    });
    if (!requestItems.length || !returnReason.trim()) {
      setError("Select at least one return item and provide a return reason.");
      endAction();
      return;
    }
    const excessiveItem = requestItems.find((entry) => {
      const item = selectedSale.items.find((row) => row.id === entry.saleItemId);
      return item ? entry.quantity > returnableQuantity(item) : false;
    });
    if (excessiveItem) {
      setError("One or more return quantities exceed the remaining returnable quantity.");
      endAction();
      return;
    }
    try {
      const existingReturnIds = new Set(selectedSale.returns.map((item) => item.id));
      const sale = await returnPharmacyPosSale(token, tenantId, selectedSale.id, {
        reason: returnReason.trim(),
        refundMode: returnMode,
        referenceNumber: returnReference.trim() || null,
        items: requestItems,
      });
      setBatchPreview({});
      await Promise.all([refreshSales(), refreshShifts(), refreshMedicineResults(medicineQuery)]);
      selectSale(sale);
      setReturnDrawerOpen(false);
      const newReturns = sale.returns.filter((item) => !existingReturnIds.has(item.id));
      const returnNumbers = Array.from(new Set(newReturns.map((item) => item.returnNumber))).join(", ");
      setSuccess(`Return ${returnNumbers || "processed"} for sale ${sale.saleNumber}. Refund can be processed from Billing / Refunds. Reusable items were restocked with customer return audit movements.`);
      setError(null);
    } catch (err) {
      setError(mapPosError(err, "Return could not be processed."));
    } finally {
      endAction();
    }
  }, [beginAction, endAction, medicineQuery, refreshMedicineResults, refreshSales, refreshShifts, returnDraft, returnMode, returnReason, returnReference, selectSale, selectedSale, tenantId, token]);

  const submitOpenShift = React.useCallback(async () => {
    if (!token || !tenantId) return;
    if (!beginAction("open-shift")) return;
    try {
      const shift = await openPharmacyPosShift(token, tenantId, {
        openingCashAmount: numeric(openingCashAmount),
        notes: shiftOpenNotes.trim() || null,
      });
      setCurrentShift(shift);
      setOpenShiftDialogOpen(false);
      setOpeningCashAmount("0");
      setShiftOpenNotes("");
      await refreshShifts();
      setSuccess("Cashier shift opened.");
      setError(null);
    } catch (err) {
      setError("Cashier shift could not be opened.");
    } finally {
      endAction();
    }
  }, [beginAction, endAction, openingCashAmount, refreshShifts, shiftOpenNotes, tenantId, token]);

  const openCloseShiftDialog = React.useCallback(() => {
    if (!currentShift) return;
    setActualCashAmount(String(currentShift.expectedCashAmount));
    setActualUpiAmount(String(currentShift.expectedUpiAmount));
    setActualCardAmount(String(currentShift.expectedCardAmount));
    setActualOtherAmount(String(currentShift.expectedOtherAmount));
    setShiftCloseNotes("");
    setCloseShiftDialogOpen(true);
  }, [currentShift]);

  const submitCloseShift = React.useCallback(async () => {
    if (!token || !tenantId || !currentShift) return;
    if (!beginAction("close-shift")) return;
    try {
      await closePharmacyPosShift(token, tenantId, currentShift.id, {
        actualCashAmount: numeric(actualCashAmount),
        actualUpiAmount: numeric(actualUpiAmount),
        actualCardAmount: numeric(actualCardAmount),
        actualOtherAmount: numeric(actualOtherAmount),
        closeNotes: shiftCloseNotes.trim() || null,
      });
      setCloseShiftDialogOpen(false);
      await refreshShifts();
      setSuccess("Cashier shift closed.");
      window.setTimeout(() => setSuccess(null), 2500);
      setError(null);
    } catch (err) {
      setError("Cashier shift could not be closed.");
    } finally {
      endAction();
    }
  }, [actualCardAmount, actualCashAmount, actualOtherAmount, actualUpiAmount, beginAction, currentShift, endAction, refreshShifts, shiftCloseNotes, tenantId, token]);

  if (!canAccessPos) {
    return <Alert severity="error">Pharmacy POS is restricted to Clinic Admin, pharmacy counter roles, or inventory managers with checkout permissions.</Alert>;
  }

  return (
    <Stack spacing={1.5}>
      <Stack direction={{ xs: "column", lg: "row" }} spacing={1} alignItems={{ xs: "stretch", lg: "center" }} sx={panelSx}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: 1 }}>
          <Chip icon={<PointOfSaleRoundedIcon />} label="Pharmacy POS" color="primary" />
          <TextField
            inputRef={searchInputRef}
            size="small"
            fullWidth
            label="Scan / Search medicine"
            value={medicineQuery}
            onChange={(event) => setMedicineQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                if (medicineResults[0]) {
                  void addMedicine(medicineResults[0]);
                  return;
                }
                setError("No FEFO-eligible medicine matched this scan.");
                window.setTimeout(() => searchInputRef.current?.focus(), 0);
              }
            }}
            placeholder="Name, generic, brand, barcode, QR, external code, batch"
            error={!medicineSearchValidation.success}
            helperText={medicineSearchValidation.success ? "Search medicine name, generic name, barcode, QR code, external code, or batch." : firstZodError(medicineSearchValidation.error)}
            inputProps={{ maxLength: 60 }}
          />
        </Stack>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={0.75}>
          <Tooltip title="Use camera to scan medicine barcode.">
            <Button size="small" variant="outlined" startIcon={<CameraAltRoundedIcon />} onClick={() => openCodeScanner("BARCODE")}>
              Scan Barcode
            </Button>
          </Tooltip>
          <Tooltip title="Use camera to scan QR code.">
            <Button size="small" variant="outlined" startIcon={<CameraAltRoundedIcon />} onClick={() => openCodeScanner("QR")}>
              Scan QR
            </Button>
          </Tooltip>
          <Button size="small" variant="outlined" startIcon={<AttachFileRoundedIcon />} onClick={() => { setPrescriptionSectionOpen(true); prescriptionInputRef.current?.click(); }}>
            Upload Prescription
          </Button>
          <Button size="small" variant="outlined" startIcon={<CameraAltRoundedIcon />} onClick={() => { setPrescriptionSectionOpen(true); setScanDialogOpen(true); }}>
            Scan Prescription
          </Button>
          <Tooltip title="Save this cart temporarily.">
            <Button
              size="small"
              variant="outlined"
              startIcon={<PauseCircleOutlineRoundedIcon />}
              onClick={holdCurrentCart}
            >
              {cart.length ? "Hold Cart" : heldDraft ? "Resume Held" : "Hold Cart"}
            </Button>
          </Tooltip>
          <Button size="small" variant="outlined" color="inherit" startIcon={<RestartAltRoundedIcon />} onClick={confirmClearDraft}>
            Clear Cart
          </Button>
          <Tooltip title="View completed POS sales.">
            <Button size="small" variant="outlined" startIcon={<HistoryRoundedIcon />} onClick={() => setRecentDrawerOpen(true)}>
              Recent Sales
            </Button>
          </Tooltip>
          <Chip
            size="small"
            label={shiftStatusLabel(currentShift)}
            color={currentShift ? "success" : "default"}
            variant={currentShift ? "filled" : "outlined"}
          />
        </Stack>
        <input
          ref={prescriptionInputRef}
          type="file"
          hidden
          accept=".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"
          onChange={(event) => void uploadPrescription(event.target.files?.[0] ?? null)}
        />
      </Stack>

      <Typography variant="body2" color="text.secondary">
        Enter adds the top FEFO-eligible result. Repeated scans increase quantity. Stock deduction is confirmed at checkout and audited as sale movements.
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Shortcuts: `Enter` add top result, `F2` paid amount, `F4` complete sale, `Esc` clear search or close modal, `+` / `-` adjust selected cart row quantity.
      </Typography>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}
      {lastCompletedSale ? (
        <Alert
          severity="success"
          onClose={() => setLastCompletedSale(null)}
          action={(
            <Button color="inherit" size="small" startIcon={<LocalPrintshopOutlinedIcon />} onClick={() => void printReceipt(lastCompletedSale.id)}>
              Print Receipt
            </Button>
          )}
        >
          Sale {lastCompletedSale.saleNumber} completed successfully.
        </Alert>
      ) : null}
      {saleValidationMessage ? <Alert severity="warning" sx={{ py: 0.5 }}>{saleValidationMessage}</Alert> : null}
      {cartHasStockIssue ? <Alert severity="warning">Some cart line values need correction. Review the highlighted fields before completing the sale.</Alert> : null}

      <WorkflowGuide
        title="POS Workflow"
        subtitle="Search medicine, build the cart, then complete checkout only after a shift is open."
        steps={[
          { label: "Search" },
          { label: "Cart" },
          { label: "Checkout", helper: "Paid Amount inside" },
          { label: "Shift" },
          { label: "Receipt" },
        ]}
      />

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, lg: 8.4 }}>
          <Stack spacing={1.25}>
            <Box sx={panelSx}>
              <Stack spacing={0.9}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Matched Medicines</Typography>
                  <Typography variant="caption" color="text.secondary">{loading ? "Loading..." : `${medicineResultsOrdered.length} results`}</Typography>
                </Stack>
                <Stack direction={{ xs: "column", md: "row" }} spacing={0.75} alignItems={{ xs: "stretch", md: "center" }} justifyContent="space-between">
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                    {([
                      ["ALL", "All"],
                      ["IN_STOCK", "In stock"],
                      ["LOW_STOCK", "Low stock"],
                      ["OUT_OF_STOCK", "Out of stock"],
                      ["EXPIRING_SOON", "Expiring soon"],
                    ] as const).map(([value, label]) => (
                      <Chip
                        key={value}
                        label={label}
                        clickable
                        color={stockFilter === value ? "primary" : "default"}
                        variant={stockFilter === value ? "filled" : "outlined"}
                        onClick={() => setStockFilter(value)}
                      />
                    ))}
                  </Stack>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                    <Chip
                      label="Hide unavailable"
                      clickable
                      color={hideUnavailable ? "primary" : "default"}
                      variant={hideUnavailable ? "filled" : "outlined"}
                      onClick={() => setHideUnavailable((current) => !current)}
                    />
                    <Chip size="small" label="In stock first" variant="outlined" />
                  </Stack>
                </Stack>
                <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, maxHeight: 245 }}>
                  <Table size="small" stickyHeader sx={{ tableLayout: "fixed" }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell width={150}>Lookup</TableCell>
                        <TableCell width={90}>Stock</TableCell>
                        <TableCell width={110}>Expiry</TableCell>
                        <TableCell width={110}>Rate</TableCell>
                        <TableCell width={70} />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {medicineResultsOrdered.map((medicine) => {
                        const resultExpiryWarning = expiryWarning(medicine.earliestExpiryDate);
                        const outOfStock = medicine.totalAvailableQuantity <= 0;
                        const lowStock = isLowStock(medicine.totalAvailableQuantity);
                        return (
                          <TableRow
                            key={medicine.medicineId}
                            hover
                            sx={{ cursor: outOfStock ? "default" : "pointer", opacity: outOfStock ? 0.7 : 1 }}
                            onClick={() => {
                              if (outOfStock) {
        setError("Out of stock. Add stock before sale.");
        return;
                              }
                              void addMedicine(medicine);
                            }}
                          >
                            <TableCell>
                              <Typography variant="body2" fontWeight={600}>{medicine.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {medicine.genericName || medicine.brandName || "No secondary name"}
                              </Typography>
                              {resultExpiryWarning ? (
                                <Typography variant="caption" color={`${resultExpiryWarning.tone}.main`} sx={{ display: "block" }}>
                                  {resultExpiryWarning.label}
                                </Typography>
                              ) : null}
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                {medicine.barcode || medicine.qrCode || medicine.externalCode || "Direct search"}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Stack spacing={0.5} alignItems="flex-start">
                                <Typography variant="body2">{medicine.totalAvailableQuantity}</Typography>
                                {outOfStock ? <Chip size="small" color="error" variant="outlined" label="OUT OF STOCK" /> : null}
                                {!outOfStock && lowStock ? <Chip size="small" color="warning" variant="outlined" label="LOW STOCK" /> : null}
                              </Stack>
                            </TableCell>
                            <TableCell>{medicine.earliestExpiryDate ?? "NA"}</TableCell>
                            <TableCell>{money(medicine.defaultUnitPrice ?? 0)}</TableCell>
                            <TableCell align="right">
                              <Tooltip title={outOfStock ? "Out of stock. Add stock before sale." : "Add to cart"}>
                                <span>
                                  <Button
                                    size="small"
                                    variant="text"
                                    disabled={outOfStock}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      if (outOfStock) return;
                                      void addMedicine(medicine);
                                    }}
                                  >
                                    Add
                                  </Button>
                                </span>
                              </Tooltip>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                      {!medicineResultsOrdered.length ? (
                        <TableRow>
                          <TableCell colSpan={6}>
                            <Stack spacing={1} alignItems="flex-start">
                              <Typography variant="body2">
                                {loading ? "Loading medicines..." : "No medicines are available for sale yet. Add medicines and receive stock before starting POS sale."}
                              </Typography>
                              {!loading && canManageInventory ? (
                                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                                  <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>
                                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier")}>Receive via Procurement</Button>
                                </Stack>
                              ) : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={0.9}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Cart</Typography>
                  <Typography variant="caption" color="text.secondary">Compact FEFO checkout table</Typography>
                </Stack>
                <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, maxHeight: 360 }}>
                  <Table size="small" stickyHeader sx={{ tableLayout: "fixed" }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell width={140}>Batch</TableCell>
                        <TableCell width={92}>Expiry</TableCell>
                        <TableCell width={74}><RequiredLabel text="Qty" required /></TableCell>
                        <TableCell width={88}>Rate</TableCell>
                        <TableCell width={88}>Disc</TableCell>
                        <TableCell width={84}>Tax</TableCell>
                        <TableCell width={108}>Total</TableCell>
                        <TableCell width={52} />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {cart.map((line, lineIndex) => {
                        const overLimit = numeric(line.quantity) > line.availableQuantity;
                        const primaryBatch = batchPreview[line.medicineId]?.[0];
                        const splitLikely = Boolean(primaryBatch && numeric(line.quantity) > primaryBatch.availableQuantity && batchPreview[line.medicineId] && batchPreview[line.medicineId]!.length > 1);
                        const nearExpiry = expiryWarning(primaryBatch?.expiryDate ?? line.earliestExpiryDate);
                        const quantityError = saleFieldErrors[`items.${lineIndex}.quantity`];
                        const unitPriceError = saleFieldErrors[`items.${lineIndex}.unitPrice`];
                        const discountError = saleFieldErrors[`items.${lineIndex}.discount`];
                        const taxError = saleFieldErrors[`items.${lineIndex}.taxRate`];
                        return (
                          <TableRow
                            key={line.medicineId}
                            hover
                            selected={selectedCartMedicineId === line.medicineId}
                            onClick={() => setSelectedCartMedicineId(line.medicineId)}
                            sx={{ cursor: "pointer" }}
                          >
                            <TableCell>
                              <Typography variant="body2" fontWeight={600}>{line.medicineName}</Typography>
                              <Typography variant="caption" color={overLimit ? "error.main" : "text.secondary"} sx={{ display: "block" }}>
                                {overLimit
                                  ? `Only ${line.availableQuantity} available across sellable batches.`
                                  : `Available ${line.availableQuantity}. Remaining after this line ${Math.max(0, line.availableQuantity - numeric(line.quantity))}. ${compactBatchPreview(batchPreview[line.medicineId])}`}
                              </Typography>
                              {nearExpiry ? (
                                <Typography variant="caption" color={`${nearExpiry.tone}.main`} sx={{ display: "block" }}>
                                  {nearExpiry.label}
                                </Typography>
                              ) : null}
                              {splitLikely ? (
                                <Typography variant="caption" color="info.main" sx={{ display: "block" }}>
                                  Sale fulfilled from multiple batches.
                                </Typography>
                              ) : null}
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary">
                                {primaryBatch?.batchNumber ?? "Auto FEFO"}
                              </Typography>
                            </TableCell>
                            <TableCell>{primaryBatch?.expiryDate ?? line.earliestExpiryDate ?? "NA"}</TableCell>
                            <TableCell>
                              <TextField
                                value={line.quantity}
                                onChange={(event) => updateCartLine(line.medicineId, { quantity: event.target.value })}
                                size="small"
                                type="number"
                                inputRef={(node) => {
                                  const refs = cartInputRefs.current[line.medicineId] ?? {};
                                  refs.quantity = node;
                                  cartInputRefs.current[line.medicineId] = refs;
                                }}
                                error={Boolean(quantityError)}
                                helperText={quantityError || "Whole number within available stock."}
                                inputProps={{ min: 1, step: 1, "aria-required": true }}
                              />
                            </TableCell>
                            <TableCell>
                              <TextField
                                value={line.unitPrice}
                                onChange={(event) => updateCartLine(line.medicineId, { unitPrice: event.target.value })}
                                size="small"
                                type="number"
                                inputRef={(node) => {
                                  const refs = cartInputRefs.current[line.medicineId] ?? {};
                                  refs.unitPrice = node;
                                  cartInputRefs.current[line.medicineId] = refs;
                                }}
                                error={Boolean(unitPriceError)}
                                helperText={unitPriceError || "Rate must be zero or greater."}
                                inputProps={{ min: 0, step: "0.01" }}
                              />
                            </TableCell>
                            <TableCell>
                              <TextField
                                value={line.discount}
                                onChange={(event) => updateCartLine(line.medicineId, { discount: event.target.value })}
                                size="small"
                                type="number"
                                inputRef={(node) => {
                                  const refs = cartInputRefs.current[line.medicineId] ?? {};
                                  refs.discount = node;
                                  cartInputRefs.current[line.medicineId] = refs;
                                }}
                                error={Boolean(discountError)}
                                helperText={discountError || "Discount must be zero or greater."}
                                inputProps={{ min: 0, step: "0.01" }}
                              />
                            </TableCell>
                            <TableCell>
                              <TextField
                                value={line.taxRate}
                                onChange={(event) => updateCartLine(line.medicineId, { taxRate: event.target.value })}
                                size="small"
                                type="number"
                                inputRef={(node) => {
                                  const refs = cartInputRefs.current[line.medicineId] ?? {};
                                  refs.taxRate = node;
                                  cartInputRefs.current[line.medicineId] = refs;
                                }}
                                error={Boolean(taxError)}
                                helperText={taxError || "Tax % must be between 0 and 100."}
                                inputProps={{ min: 0, max: 100, step: "0.01" }}
                              />
                            </TableCell>
                            <TableCell>{money(lineTotal(line))}</TableCell>
                            <TableCell align="right">
                              <Tooltip title="Remove">
                                <IconButton size="small" color="error" onClick={() => removeCartLine(line.medicineId)}>
                                  <DeleteOutlineRoundedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                      {!cart.length ? (
                        <TableRow>
                          <TableCell colSpan={9}>No medicines in the cart.</TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
                <Typography variant="caption" color="text.secondary">
                  FEFO explanation: checkout deducts stock from the earliest non-expired batch first and spills into the next batch only when required.
                </Typography>
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={0.9}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Checkout</Typography>
                  <Chip size="small" label={cart.length ? `${cart.length} line${cart.length === 1 ? "" : "s"}` : "Empty cart"} color={cart.length ? "primary" : "default"} />
                </Stack>
                <Grid container spacing={0.75}>
                  <Grid size={{ xs: 6, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">Subtotal</Typography>
                    <Typography variant="body2" fontWeight={600}>{money(subtotal)}</Typography>
                  </Grid>
                  <Grid size={{ xs: 6, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">Discount</Typography>
                    <Typography variant="body2" fontWeight={600}>{money(discountTotal)}</Typography>
                  </Grid>
                  <Grid size={{ xs: 6, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">Tax</Typography>
                    <Typography variant="body2" fontWeight={600}>{money(taxTotal)}</Typography>
                  </Grid>
                  <Grid size={{ xs: 6, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">Grand total</Typography>
                    <Typography variant="body2" fontWeight={700}>{money(total)}</Typography>
                  </Grid>
                  <Grid size={{ xs: 6, md: 2.4 }}>
                    <TextField
                      inputRef={paidAmountInputRef}
                      size="small"
                      label={<RequiredLabel text="Paid Amount" required={total > 0} />}
                      value={paidAmount}
                      onChange={(event) => setPaidAmount(event.target.value)}
                      fullWidth
                      type="number"
                      disabled={!currentShift}
                      error={Boolean(saleFieldErrors.paidAmount)}
                      helperText={saleFieldErrors.paidAmount || "Enter paid amount."}
                      inputProps={{ min: 0, step: "0.01", "aria-required": total > 0 }}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">Due</Typography>
                    <Typography variant="body2" fontWeight={700}>{money(duePreview)}</Typography>
                  </Grid>
                </Grid>
                <Grid container spacing={0.75}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      select
                      size="small"
                      label={<RequiredLabel text="Payment Mode" required={paidAmountValue > 0 || total > 0} />}
                      value={paymentMode}
                      onChange={(event) => setPaymentMode(event.target.value as PaymentMode)}
                      fullWidth
                      disabled={!currentShift}
                      inputRef={paymentModeInputRef}
                      error={Boolean(saleFieldErrors.paymentMode)}
                      helperText={saleFieldErrors.paymentMode || "Select the payment method used for checkout."}
                      inputProps={{ "aria-required": paidAmountValue > 0 || total > 0 }}
                    >
                      {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      size="small"
                      label={<RequiredLabel text="Reference" required={requiresReference} />}
                      value={paymentReference}
                      onChange={(event) => setPaymentReference(event.target.value)}
                      fullWidth
                      disabled={!currentShift}
                      inputRef={paymentReferenceInputRef}
                      error={Boolean(saleFieldErrors.paymentReference)}
                      helperText={saleFieldErrors.paymentReference || (requiresReference ? "Required for UPI, card, and insurance payments." : "Optional for cash payments.")}
                      inputProps={{ maxLength: 60, "aria-required": requiresReference }}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      size="small"
                      label="Notes"
                      value={notes}
                      onChange={(event) => setNotes(event.target.value)}
                      multiline
                      minRows={2}
                      fullWidth
                      error={Boolean(saleFieldErrors.notes)}
                      helperText={saleFieldErrors.notes || "Optional notes for the sale. Max 250 characters."}
                      inputProps={{ maxLength: 250, name: "sale-notes" }}
                    />
                  </Grid>
                </Grid>
                <Tooltip title={completeSaleDisabledReason || "Complete sale"}>
                  <span>
                    <Button
                      variant="contained"
                      size="large"
                      startIcon={<ShoppingCartCheckoutRoundedIcon />}
                      disabled={submitting || Boolean(completeSaleDisabledReason)}
                      onClick={requestSaleConfirmation}
                    >
                      {activeAction === "sale" ? "Completing Sale..." : "Complete Sale"}
                    </Button>
                  </span>
                </Tooltip>
                {completeSaleDisabledReason ? (
                  <Typography variant="caption" color="text.secondary">
                    Complete Sale is unavailable: {completeSaleDisabledReason}.
                  </Typography>
                ) : null}
              </Stack>
            </Box>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 3.6 }}>
          <Stack spacing={1.25} sx={{ position: { lg: "sticky" }, top: { lg: STICKY_TOP }, alignSelf: "flex-start" }}>
            <Accordion expanded={customerSectionOpen} onChange={(_, expanded) => setCustomerSectionOpen(expanded)} disableGutters sx={panelSx}>
              <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                <Stack spacing={0.25}>
                  <Typography variant="subtitle1" fontWeight={700}>Customer</Typography>
                  <Typography variant="caption" color="text.secondary">{customerSummary}</Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ px: 0, pb: 0 }}>
                <Stack spacing={1.25}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" color="text.secondary">
                      {selectedPatient || customerName.trim() || customerMobile.trim() ? "Customer summary ready for checkout." : "Select a patient or enter walk-in details."}
                    </Typography>
                    <Button size="small" color="inherit" onClick={clearCustomerSelection} disabled={!selectedPatient && !customerName.trim() && !customerMobile.trim() && !patientQuery.trim()}>
                      Clear
                    </Button>
                  </Stack>
                  <TextField
                    size="small"
                    label="Search patient"
                    value={patientQuery}
                    onFocus={() => setCustomerSectionOpen(true)}
                    onChange={(event) => setPatientQuery(event.target.value)}
                    placeholder="Type 2+ letters"
                    fullWidth
                  />
                  <Collapse in={patientResults.length > 0}>
                    <List dense sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, py: 0, maxHeight: 150, overflow: "auto" }}>
                      {patientResults.map((patient) => (
                        <ListItemButton
                          key={patient.id}
                          onClick={() => {
                            setSelectedPatient(patient);
                            setCustomerName(`${patient.firstName} ${patient.lastName}`.trim());
                            setCustomerMobile(patient.mobile);
                            setPatientResults([]);
                            setPatientQuery("");
                            setCustomerSectionOpen(false);
                          }}
                        >
                          <ListItemText primary={`${patient.firstName} ${patient.lastName}`} secondary={`${patient.patientNumber} | ${patient.mobile}`} />
                        </ListItemButton>
                      ))}
                    </List>
                  </Collapse>
                  {selectedPatient ? (
                    <Chip
                      color="primary"
                      label={`Patient: ${selectedPatient.firstName} ${selectedPatient.lastName}`}
                      onDelete={clearCustomerSelection}
                    />
                  ) : null}
                  <TextField
                    size="small"
                    label={<RequiredLabel text="Walk-in name" required={false} />}
                    value={customerName}
                    inputRef={customerNameInputRef}
                    onFocus={() => setCustomerSectionOpen(true)}
                    onChange={(event) => setCustomerName(event.target.value)}
                    fullWidth
                    error={Boolean(saleFieldErrors.customerName)}
                    helperText={saleFieldErrors.customerName || "Optional for OTC sales; include letters or numbers if entered."}
                    inputProps={{ maxLength: 60 }}
                  />
                  <TextField
                    size="small"
                    label={<RequiredLabel text="Mobile" required={false} />}
                    value={customerMobile}
                    inputRef={customerMobileInputRef}
                    onFocus={() => setCustomerSectionOpen(true)}
                    onChange={(event) => setCustomerMobile(event.target.value)}
                    fullWidth
                    error={Boolean(saleFieldErrors.customerMobile)}
                    helperText={saleFieldErrors.customerMobile || "Optional Indian mobile number."}
                    inputProps={{ inputMode: "tel" }}
                  />
                </Stack>
              </AccordionDetails>
            </Accordion>

            <Accordion expanded={prescriptionSectionOpen} onChange={(_, expanded) => setPrescriptionSectionOpen(expanded)} disableGutters sx={panelSx}>
              <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                <Stack spacing={0.25}>
                  <Typography variant="subtitle1" fontWeight={700}>Prescription</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {prescription ? "1 prescription attached" : "No prescription attached"}
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ px: 0, pb: 0 }}>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button size="small" variant="text" onClick={() => { setPrescriptionSectionOpen(true); prescriptionInputRef.current?.click(); }}>Upload File</Button>
                    <Button size="small" variant="text" onClick={() => { setPrescriptionSectionOpen(true); setScanDialogOpen(true); }}>Scan Prescription</Button>
                  </Stack>
                  {prescriptionUploadError ? <Alert severity="error">{prescriptionUploadError}</Alert> : null}
                  {activeAction === "prescription-upload" && pendingPrescriptionFile ? (
                    <Alert severity="info">Uploading {pendingPrescriptionFile.name}...</Alert>
                  ) : null}
                  {!prescription && pendingPrescriptionFile ? (
                    <Stack spacing={1}>
                      <Chip icon={<AttachFileRoundedIcon />} label={pendingPrescriptionFile.name} variant="outlined" color="info" />
                      {pendingPrescriptionPreviewUrl ? (
                        isImageFile(pendingPrescriptionFile.name, pendingPrescriptionFile.type) ? (
                          <Box
                            component="img"
                            src={pendingPrescriptionPreviewUrl}
                            alt={pendingPrescriptionFile.name}
                            sx={{ width: "100%", maxHeight: 160, objectFit: "cover", borderRadius: 2, border: "1px solid", borderColor: "divider" }}
                          />
                        ) : (
                          <Card variant="outlined">
                            <CardContent sx={{ p: 1.5 }}>
                              <Stack spacing={0.4}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{pendingPrescriptionFile.name}</Typography>
                                <Typography variant="caption" color="text.secondary">PDF selected. Preview opens after upload completes.</Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        )
                      ) : null}
                    </Stack>
                  ) : null}
                  {prescription ? (
                    <Stack spacing={1}>
                      <Chip icon={<AttachFileRoundedIcon />} label={prescription.fileName} variant="outlined" />
                      {prescriptionPreviewUrl && isImageFile(prescription.fileName, prescription.mediaType) ? (
                        <Box
                          component="img"
                          src={prescriptionPreviewUrl}
                          alt={prescription.fileName}
                          sx={{ width: "100%", maxHeight: 128, objectFit: "cover", borderRadius: 2, border: "1px solid", borderColor: "divider" }}
                        />
                      ) : null}
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Button size="small" startIcon={<PreviewRoundedIcon />} onClick={() => void previewPrescription(prescription.documentId)}>
                          Open Preview
                        </Button>
                        <Button size="small" color="inherit" onClick={removePrescriptionAttachment}>
                          Remove
                        </Button>
                        <Button size="small" color="inherit" onClick={() => prescriptionInputRef.current?.click()}>
                          Replace
                        </Button>
                      </Stack>
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      Upload a PDF or image when a medicine or local regulation requires a prescription. OTC sales still remain allowed.
                    </Typography>
                  )}
                  <Alert severity="info" sx={{ py: 0 }}>
                    Camera permission required. You can also upload an image.
                  </Alert>
                  <Typography variant="caption" color="text.secondary">
                    Supported files: PDF, JPG, PNG, WEBP up to 10 MB. Uploaded prescriptions stay attached to this draft sale and checkout context.
                  </Typography>
                </Stack>
              </AccordionDetails>
            </Accordion>

            <Box sx={panelSx}>
              <Stack spacing={0.9}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Stack direction="row" spacing={1} alignItems="center">
                    <MedicalServicesRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle1" fontWeight={700}>Shift</Typography>
                  </Stack>
                  <Chip
                    size="small"
                    label={shiftStatusLabel(currentShift)}
                    color={currentShift ? "success" : "default"}
                    variant={currentShift ? "filled" : "outlined"}
                  />
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  {currentShift
                    ? `Opened ${new Date(currentShift.openedAt).toLocaleString()} for cashier ${currentShift.cashierUserId}.`
                    : "No open shift. Open a shift before collecting payments or completing a sale."}
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Button size="small" variant={currentShift ? "outlined" : "contained"} startIcon={<LocalPharmacyRoundedIcon />} disabled={Boolean(currentShift)} onClick={() => setOpenShiftDialogOpen(true)}>Open Shift</Button>
                  <Button size="small" variant="outlined" color="inherit" disabled={!currentShift} onClick={openCloseShiftDialog}>Close Shift</Button>
                  <Button size="small" variant="outlined" color="inherit" onClick={() => setShiftHistoryDrawerOpen(true)}>View Shift History</Button>
                </Stack>
                {currentShift ? (
                  <>
                    <Stack direction="row" justifyContent="space-between">
                      <Typography variant="body2">Opening cash</Typography>
                      <Typography variant="body2">{money(currentShift.openingCashAmount)}</Typography>
                    </Stack>
                    {[
                      ["Expected cash", money(currentShift.expectedCashAmount)],
                      ["Expected UPI", money(currentShift.expectedUpiAmount)],
                      ["Expected card", money(currentShift.expectedCardAmount)],
                      ["Expected other", money(currentShift.expectedOtherAmount)],
                      ["Total expected", money(currentShift.expectedTotalAmount)],
                    ].map(([label, value]) => (
                      <Stack key={label} direction="row" justifyContent="space-between">
                        <Typography variant="body2">{label}</Typography>
                        <Typography variant="body2">{value}</Typography>
                      </Stack>
                    ))}
                  </>
                ) : null}
              </Stack>
            </Box>
          </Stack>
        </Grid>
      </Grid>

      <Drawer anchor="right" open={recentDrawerOpen} onClose={() => setRecentDrawerOpen(false)}>
        <Box sx={{ width: { xs: 360, md: 540 }, p: 2 }}>
          <Stack spacing={1.5}>
            <Typography variant="h6">Recent Sales</Typography>
            <TextField
              size="small"
              label="Find prior sale"
              value={saleSearchQuery}
              onChange={(event) => setSaleSearchQuery(event.target.value)}
              placeholder="Sale no, customer, mobile"
              fullWidth
              error={!recentSalesSearchValidation.success}
              helperText={recentSalesSearchValidation.success ? "Search sale number, customer, mobile, or date." : firstZodError(recentSalesSearchValidation.error)}
              inputProps={{ maxLength: 60 }}
            />
            <List dense sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, py: 0, maxHeight: 280, overflow: "auto" }}>
              {filteredSales.slice(0, 8).map((sale) => (
                <ListItemButton key={sale.id} selected={selectedSaleId === sale.id} onClick={() => selectSale(sale)}>
                  <ListItemText
                    primary={`${sale.saleNumber} | ${sale.status}`}
                    secondary={`${saleDisplayName(sale)} | ${money(sale.total)} | Due ${money(sale.dueAmount)}`}
                  />
                </ListItemButton>
              ))}
              {!filteredSales.length ? <ListItemText sx={{ px: 2, py: 1 }} primary="No matching pharmacy sales." /> : null}
            </List>
            {selectedSale ? (
              <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1.5 }}>
                <Stack spacing={1.1}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="subtitle1" fontWeight={700}>{selectedSale.saleNumber}</Typography>
                    <Chip size="small" label={selectedSale.status} color={selectedSale.status === "COMPLETED" ? "success" : "default"} />
                  </Stack>
                  <Typography variant="body2" fontWeight={600}>{saleDisplayName(selectedSale)}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Sale date {new Date(selectedSale.saleDateTime).toLocaleString()} | Payments {selectedSale.payments.length} | Returns {selectedSale.returns.length}
                  </Typography>
                  {selectedSale.payments.length ? (
                    <Typography variant="caption" color="text.secondary">
                      Latest payment {selectedSale.payments[selectedSale.payments.length - 1].paymentMode} | Receipt {selectedSale.payments[selectedSale.payments.length - 1].receiptNumber} | Shift ref {shortId(selectedSale.payments[selectedSale.payments.length - 1].cashierShiftId)}
                    </Typography>
                  ) : null}
                  {selectedSale.returns.length ? (
                    <Typography variant="caption" color="text.secondary">
                      Latest return {selectedSale.returns[selectedSale.returns.length - 1].returnNumber} | Refund {money(selectedSale.returns[selectedSale.returns.length - 1].refundAmount)} | Mode {selectedSale.returns[selectedSale.returns.length - 1].refundMode ?? "NA"}
                    </Typography>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      Return receipt is not available separately yet. Use the sale receipt for the current audit trail.
                    </Typography>
                  )}
                  <Typography variant="caption" color="text.secondary">{selectedSale.fefoExplanation}</Typography>
                  {saleUsesMultipleBatches(selectedSale) ? (
                    <Alert severity="info" sx={{ py: 0 }}>
                      Sale fulfilled from multiple batches.
                    </Alert>
                  ) : null}
                  {selectedSale.prescriptionDocumentId ? (
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip size="small" icon={<AttachFileRoundedIcon />} label={selectedSale.prescriptionFileName ?? "Prescription"} />
                      <IconButton size="small" onClick={() => void previewPrescription(selectedSale.prescriptionDocumentId!)}>
                        <VisibilityRoundedIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                  ) : null}
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button size="small" startIcon={<LocalPrintshopOutlinedIcon />} onClick={() => void printReceipt(selectedSale.id)}>
                      Receipt
                    </Button>
                    <Button size="small" variant="outlined" color="warning" onClick={() => setReturnDrawerOpen(true)}>
                      Return
                    </Button>
                  </Stack>
                  <Divider />
                  <Typography variant="body2" fontWeight={600}>Add payment</Typography>
                  <TextField size="small" label="Amount" value={paymentAmount} onChange={(event) => setPaymentAmount(event.target.value)} fullWidth />
                  <Select size="small" value={paymentTopupMode} onChange={(event) => setPaymentTopupMode(event.target.value as PaymentMode)} fullWidth disabled={paymentTopupBlocked}>
                    {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                  </Select>
                  <TextField size="small" label="Reference" value={paymentReferenceTopup} onChange={(event) => setPaymentReferenceTopup(event.target.value)} fullWidth disabled={paymentTopupBlocked} />
                  <Button size="small" variant="outlined" disabled={submitting || selectedSale.dueAmount <= 0 || paymentTopupBlocked} onClick={() => void submitPayment()}>
                    {activeAction === "payment" ? "Recording Payment..." : "Record Payment"}
                  </Button>
                  {paymentTopupBlocked ? <Typography variant="caption" color="warning.main">Open cashier shift before collecting payment.</Typography> : null}
                </Stack>
              </Box>
            ) : null}
          </Stack>
        </Box>
      </Drawer>

      <Drawer anchor="right" open={returnDrawerOpen} onClose={() => setReturnDrawerOpen(false)}>
        <Box sx={{ width: { xs: 360, md: 520 }, p: 2 }}>
          <Stack spacing={1.25}>
            <Typography variant="h6">Process Customer Return</Typography>
            {selectedSale ? (
              <>
                <Typography variant="body2" fontWeight={600}>{selectedSale.saleNumber} • {saleDisplayName(selectedSale)}</Typography>
                <Stack spacing={1} sx={{ maxHeight: "50vh", overflow: "auto", pr: 0.5 }}>
                  {selectedSale.items.map((item) => {
                    const draft = returnDraft[item.id] ?? { selected: false, quantity: "1", reusable: true };
                    const remaining = returnableQuantity(item);
                    const selectedQty = Math.min(remaining, Math.max(0, numeric(draft.quantity)));
                    return (
                      <Box key={item.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                        <Stack spacing={0.75}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Checkbox
                              size="small"
                              checked={draft.selected}
                              disabled={remaining <= 0}
                              onChange={(event) => setReturnDraft((current) => ({
                                ...current,
                                [item.id]: {
                                  ...(current[item.id] ?? draft),
                                  selected: event.target.checked,
                                },
                              }))}
                            />
                            <Box sx={{ minWidth: 0, flex: 1 }}>
                              <Typography variant="body2" fontWeight={600}>{item.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                Batch {item.batchNumber ?? "NA"} | Sold {item.quantity} | Returned {item.returnedQuantity} | Returnable {remaining}
                              </Typography>
                            </Box>
                          </Stack>
                          <Stack direction="row" spacing={1}>
                            <TextField
                              size="small"
                              label="Return qty"
                              value={draft.quantity}
                              disabled={!draft.selected || remaining <= 0}
                              onChange={(event) => setReturnDraft((current) => ({
                                ...current,
                                [item.id]: {
                                  ...(current[item.id] ?? draft),
                                  quantity: event.target.value,
                                },
                              }))}
                              fullWidth
                            />
                            <Select
                              size="small"
                              value={draft.reusable ? "true" : "false"}
                              disabled={!draft.selected || remaining <= 0}
                              onChange={(event) => setReturnDraft((current) => ({
                                ...current,
                                [item.id]: {
                                  ...(current[item.id] ?? draft),
                                  reusable: event.target.value === "true",
                                },
                              }))}
                              fullWidth
                            >
                              <MenuItem value="true">Reusable</MenuItem>
                              <MenuItem value="false">Discard</MenuItem>
                            </Select>
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            Refund estimate {money(proratedRefundAmount(item, selectedQty))}. {draft.reusable ? "Reusable items restock into inventory." : "Discarded items stay out of stock."}
                          </Typography>
                        </Stack>
                      </Box>
                    );
                  })}
                </Stack>
                <Select size="small" value={returnMode} onChange={(event) => setReturnMode(event.target.value as PaymentMode)} fullWidth>
                  {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                </Select>
                <TextField size="small" label="Reason" value={returnReason} onChange={(event) => setReturnReason(event.target.value)} fullWidth />
                <TextField size="small" label="Refund reference" value={returnReference} onChange={(event) => setReturnReference(event.target.value)} fullWidth />
                <Alert severity="info" sx={{ py: 0 }}>
                  Return estimate {money(refundEstimate)}. Refunds are handled separately in Billing / Refunds. Reusable lines: {reusableSelectedCount}. Non-sellable lines: {discardSelectedCount}.
                </Alert>
                <Stack direction="row" spacing={1}>
                  <Button onClick={() => setReturnDrawerOpen(false)}>Cancel</Button>
                  <Button size="small" variant="outlined" color="warning" disabled={submitting || !selectedSale.items.length} onClick={() => void submitReturn()}>
                    {activeAction === "return" ? "Processing Return..." : "Process Return"}
                  </Button>
                </Stack>
              </>
            ) : (
              <Typography variant="body2" color="text.secondary">Select a sale from Recent Sales first.</Typography>
            )}
          </Stack>
        </Box>
      </Drawer>

      <Drawer anchor="right" open={shiftHistoryDrawerOpen} onClose={() => setShiftHistoryDrawerOpen(false)}>
        <Box sx={{ width: { xs: 340, sm: 420 }, p: 2 }}>
          <Stack spacing={1.25}>
            <Typography variant="h6">Shift History</Typography>
            {shiftHistory.map((shift) => (
              <Box key={shift.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1.25 }}>
                <Typography variant="body2" fontWeight={600}>
                  {shift.status} • {new Date(shift.openedAt).toLocaleString()}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                  Cashier {shift.cashierUserId} • Closed {shift.closedAt ? new Date(shift.closedAt).toLocaleString() : "Open"}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                  Expected {money(shift.expectedTotalAmount)} • Actual {money(shift.actualTotalAmount ?? 0)} • Variance {money(shift.varianceAmount ?? 0)}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Box>
      </Drawer>

      <Dialog open={saleConfirmOpen} onClose={() => setSaleConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Confirm Pharmacy Sale</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info" sx={{ py: 0 }}>
              Stock will be deducted using FEFO allocation and inventory will be updated.
            </Alert>
            <Stack spacing={0.75}>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">Items count</Typography>
                <Typography variant="body2" fontWeight={600}>{saleSummary.itemCount}</Typography>
              </Stack>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">Total amount</Typography>
                <Typography variant="body2" fontWeight={600}>{saleSummary.totalAmount}</Typography>
              </Stack>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">Payment mode</Typography>
                <Typography variant="body2" fontWeight={600}>{saleSummary.paymentMode}</Typography>
              </Stack>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">Amount received</Typography>
                <Typography variant="body2" fontWeight={600}>{saleSummary.amountReceived}</Typography>
              </Stack>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSaleConfirmOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={async () => {
              setSaleConfirmOpen(false);
              await executeSale();
            }}
            disabled={submitting}
          >
            {activeAction === "sale" ? "Completing..." : "Confirm Sale"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={clearCartConfirmOpen} onClose={() => setClearCartConfirmOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Clear Pharmacy Draft?</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Clear the current POS cart, customer details, and attached prescription?
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClearCartConfirmOpen(false)}>Cancel</Button>
          <Button
            color="inherit"
            onClick={() => {
              setClearCartConfirmOpen(false);
              clearDraft();
            }}
          >
            Clear Draft
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={openShiftDialogOpen} onClose={() => setOpenShiftDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Open Cashier Shift</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              size="small"
              label="Opening cash"
              value={openingCashAmount}
              onChange={(event) => setOpeningCashAmount(event.target.value)}
              fullWidth
            />
            <TextField
              size="small"
              label="Notes"
              value={shiftOpenNotes}
              onChange={(event) => setShiftOpenNotes(event.target.value)}
              multiline
              minRows={2}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenShiftDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitOpenShift()} disabled={submitting}>{activeAction === "open-shift" ? "Opening..." : "Open Shift"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={closeShiftDialogOpen} onClose={() => setCloseShiftDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Close Cashier Shift</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info" sx={{ py: 0 }}>
              Expected cash {money(currentShift?.expectedCashAmount ?? 0)}, UPI {money(currentShift?.expectedUpiAmount ?? 0)}, card {money(currentShift?.expectedCardAmount ?? 0)}, other {money(currentShift?.expectedOtherAmount ?? 0)}.
            </Alert>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
              <TextField size="small" label="Actual cash" value={actualCashAmount} onChange={(event) => setActualCashAmount(event.target.value)} fullWidth />
              <TextField size="small" label="Actual UPI" value={actualUpiAmount} onChange={(event) => setActualUpiAmount(event.target.value)} fullWidth />
            </Stack>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
              <TextField size="small" label="Actual card" value={actualCardAmount} onChange={(event) => setActualCardAmount(event.target.value)} fullWidth />
              <TextField size="small" label="Actual other" value={actualOtherAmount} onChange={(event) => setActualOtherAmount(event.target.value)} fullWidth />
            </Stack>
            <TextField
              size="small"
              label="Close notes"
              value={shiftCloseNotes}
              onChange={(event) => setShiftCloseNotes(event.target.value)}
              multiline
              minRows={2}
              fullWidth
            />
            <Typography variant="body2" color={closeVariancePreview === 0 ? "text.primary" : closeVariancePreview > 0 ? "success.main" : "warning.main"}>
              Variance preview {money(closeVariancePreview)}
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCloseShiftDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitCloseShift()} disabled={submitting || !currentShift}>{activeAction === "close-shift" ? "Closing..." : "Close Shift"}</Button>
        </DialogActions>
      </Dialog>

      <CodeScannerDialog
        open={codeScanDialogOpen}
        title={codeScanMode === "QR" ? "Scan medicine QR code" : "Scan medicine barcode"}
        description="Point the camera at a medicine barcode or QR code. The scanned value fills the search field and refreshes the medicine list."
        value={medicineQuery}
        onClose={closeCodeScanner}
        onDetected={(code) => handleScannedCode(code, codeScanMode)}
        manualLabel="Scan or enter medicine code"
        manualPlaceholder="barcode / QR / external code"
      />

      <Dialog
        open={scanDialogOpen}
        onClose={() => {
          setScanDialogOpen(false);
          stopCameraStream();
          resetCapturedImage();
          setScanError(null);
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Scan Prescription</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {scanError ? <Alert severity="warning">{scanError}</Alert> : null}
            {!scanError ? (
              <Alert severity="info" sx={{ py: 0 }}>
                Camera permission required. You can also upload an image.
              </Alert>
            ) : null}
            <Typography variant="body2" color="text.secondary">
              Capture a clear image of the prescription and upload it through the same tenant-scoped POS prescription API.
            </Typography>
            <Box
              sx={{
                border: "1px solid",
                borderColor: "divider",
                borderRadius: 2,
                overflow: "hidden",
                bgcolor: "grey.100",
                minHeight: 280,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {capturedImageUrl ? (
                <Box component="img" src={capturedImageUrl} alt="Captured prescription" sx={{ width: "100%", display: "block" }} />
              ) : (
                <Box component="video" ref={cameraVideoRef} muted playsInline autoPlay sx={{ width: "100%", display: "block" }} />
              )}
            </Box>
            <canvas ref={cameraCanvasRef} style={{ display: "none" }} />
            {!capturedImageUrl ? (
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                <Button
                  variant="contained"
                  startIcon={<CameraAltRoundedIcon />}
                  onClick={capturePrescriptionImage}
                  disabled={Boolean(scanError)}
                >
                  Capture
                </Button>
                <Button variant="outlined" onClick={() => prescriptionInputRef.current?.click()}>
                  Upload Image Instead
                </Button>
              </Stack>
            ) : (
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" onClick={retakePrescriptionImage}>Retake</Button>
                <Button variant="contained" onClick={() => void useCapturedPrescriptionImage()} disabled={submitting}>
                  {activeAction === "prescription-upload" ? "Uploading..." : "Use Photo"}
                </Button>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setScanDialogOpen(false);
              stopCameraStream();
              resetCapturedImage();
              setScanError(null);
            }}
          >
            Close
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={previewDialogOpen} onClose={() => setPreviewDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{previewDocumentName ?? "Prescription Preview"}</DialogTitle>
        <DialogContent>
          <Box sx={{ minHeight: 480, pt: 1 }}>
            {previewDocumentUrl ? (
              previewDocumentIsImage ? (
                <Box component="img" src={previewDocumentUrl} alt={previewDocumentName ?? "Prescription"} sx={{ width: "100%", borderRadius: 2 }} />
              ) : (
                <Box component="iframe" src={previewDocumentUrl} title={previewDocumentName ?? "Prescription"} sx={{ width: "100%", minHeight: 520, border: 0, borderRadius: 2 }} />
              )
            ) : null}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
