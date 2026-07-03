import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  IconButton,
  InputAdornment,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Menu,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import MoreVertRoundedIcon from "@mui/icons-material/MoreVertRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";

import { useNavigate, useSearchParams } from "react-router-dom";
import {
  consultationFeeSchema,
} from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactTableFrame, compactChipSx } from "../../components/compact/CompactUi";
import {
  InvoicePrintDialog,
  ReceiptPrintDialog,
  type InvoicePrintData,
  type ReceiptPrintData,
} from "../../components/finance/PrintableBillingDocuments";
import ConsultationFeeDialog, { type ConsultationFeeDialogValue } from "../../components/ConsultationFeeDialog";
import RequiredLabel from "../../components/forms/RequiredLabel.js";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import {
  getFirstBillingInvalidField,
  validateBillingDraft,
  validateBillingPayment,
  validateBillingRefund,
  type FieldErrorMap,
} from "./billingValidation";
import {
  addBillPayment,
  addBillRefund,
  cancelBill,
  createBill,
  collectConsultationFee,
  getAppointment,
  getBill,
  getBillPdf,
  getClinicProfile,
  getDoctorProfile,
  getConsultation,
  getPatientBillingContext,
  getReceiptPdf,
  getPatient,
  issueBill,
  listBillPayments,
  listBillReceipts,
  listBillRefunds,
  getClinicUsers,
  listPaymentsLedger,
  sendBillInvoiceEmail,
  sendReceipt,
  searchBills,
  searchMedicines,
  searchStocks,
  searchPatients,
  type Bill,
  type BillInput,
  type BillItemType,
  type DiscountType,
  type Appointment,
  type ClinicProfile,
  type ClinicUser,
  type Payment,
  type PaymentLedgerRow,
  type PaymentMode,
  type Medicine,
  type Patient,
  type DoctorProfile,
  type PatientBillingContext,
  type PendingConsultationFee,
  type Receipt,
  type Refund,
  type Stock,
} from "../../api/clinicApi";

type BillItemCategory = BillItemType | "SERVICE" | "PACKAGE";

type BillLineForm = {
  itemType: BillItemCategory;
  itemName: string;
  quantity: string;
  unitPrice: string;
  lineDiscountAmount: string;
  taxAmount: string;
  referenceId: string;
  scanCode: string;
  sortOrder: string;
};

type BillFormState = {
  patientId: string;
  consultationId: string;
  appointmentId: string;
  billDate: string;
  discountType: DiscountType;
  discountValue: string;
  discountReason: string;
  taxAmount: string;
  notes: string;
  lines: BillLineForm[];
};

type DraftTotals = {
  subtotal: number;
  lineDiscount: number;
  billDiscount: number;
  tax: number;
  total: number;
};

type QuickChargePreset = {
  label: string;
  itemType: BillItemCategory;
  itemName: string;
  unitPrice?: string;
};

type DoctorOption = {
  appUserId: string;
  displayName: string;
  consultationFee: number | null;
};

type CatalogItem = {
  itemName: string;
  itemType: BillItemCategory;
  unitPrice: string;
  referenceId: string | null;
  availabilityLabel: string | null;
};

type PaymentFormState = {
  paymentDate: string;
  amount: string;
  paymentMode: PaymentMode;
  referenceNumber: string;
  notes: string;
};

type RefundFormState = {
  amount: string;
  refundMode: PaymentMode;
  reason: string;
  notes: string;
};

const BILL_ITEM_CATEGORIES: BillItemCategory[] = ["CONSULTATION", "MEDICINE", "TEST", "VACCINATION", "PROCEDURE", "SERVICE", "PACKAGE", "OTHER"];
const SCAN_ITEM_TYPE_OPTIONS: Array<{ value: ChargeSearchType; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "MEDICINE", label: "Medicine" },
  { value: "TEST", label: "Lab/Test" },
  { value: "SERVICE", label: "Service" },
  { value: "PROCEDURE", label: "Procedure" },
  { value: "PACKAGE", label: "Package" },
  { value: "OTHER", label: "Other" },
];
const QUICK_CHARGE_PRESETS: QuickChargePreset[] = [
  { label: "Consultation Fee", itemType: "CONSULTATION", itemName: "Consultation Fee" },
  { label: "Registration Fee", itemType: "OTHER", itemName: "Registration Fee" },
  { label: "Follow-up Fee", itemType: "CONSULTATION", itemName: "Follow-up Fee" },
  { label: "Emergency Fee", itemType: "OTHER", itemName: "Emergency Fee" },
  { label: "Lab Test", itemType: "TEST", itemName: "Lab Test" },
  { label: "Medicine", itemType: "MEDICINE", itemName: "Medicine" },
  { label: "Other Charge", itemType: "OTHER", itemName: "Other Charge" },
];
const PAYMENT_MODES: PaymentMode[] = ["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"];
const DISCOUNT_TYPES: DiscountType[] = ["NONE", "AMOUNT", "PERCENTAGE"];
type ChargeSearchType = "ALL" | Exclude<BillItemCategory, "CONSULTATION">;

type BillingWorkflowStep = "PATIENT" | "CHARGES" | "REVIEW_PAYMENT" | "RECEIPT";

const BILLING_WORKFLOW_STEPS: Array<{ key: BillingWorkflowStep; label: string }> = [
  { key: "PATIENT", label: "Patient" },
  { key: "CHARGES", label: "Charges" },
  { key: "REVIEW_PAYMENT", label: "Review" },
  { key: "RECEIPT", label: "Receipt" },
];

function resolveItemType(value: BillItemCategory): BillItemType {
  if (value === "SERVICE" || value === "PACKAGE") return "OTHER";
  return value;
}

function billItemCategoryLabel(value: BillItemCategory) {
  switch (value) {
    case "CONSULTATION": return "Consultation";
    case "MEDICINE": return "Medicine";
    case "TEST": return "Lab/Test";
    case "VACCINATION": return "Vaccination";
    case "PROCEDURE": return "Procedure";
    case "SERVICE": return "Service";
    case "PACKAGE": return "Package";
    case "OTHER":
    default:
      return "Other";
  }
}

function chargeSuggestionTypeLabel(value: BillItemCategory) {
  return billItemCategoryLabel(value).replace("Lab/Test", "Lab Test");
}

function discountTypeLabel(value: DiscountType) {
  switch (value) {
    case "AMOUNT":
      return "FLAT";
    case "PERCENTAGE":
      return "PERCENT";
    default:
      return value;
  }
}

function emptyBillForm(): BillFormState {
  return {
    patientId: "",
    consultationId: "",
    appointmentId: "",
    billDate: new Date().toISOString().slice(0, 10),
    discountType: "NONE",
    discountValue: "",
    discountReason: "",
    taxAmount: "",
    notes: "",
    lines: [{ itemType: "CONSULTATION", itemName: "", quantity: "1", unitPrice: "", lineDiscountAmount: "", taxAmount: "", referenceId: "", scanCode: "", sortOrder: "1" }],
  };
}

function emptyPaymentForm(): PaymentFormState {
  return { paymentDate: new Date().toISOString().slice(0, 10), amount: "", paymentMode: "CASH", referenceNumber: "", notes: "" };
}

function emptyRefundForm(): RefundFormState {
  return { amount: "", refundMode: "CASH", reason: "", notes: "" };
}

function toBillInput(form: BillFormState): BillInput {
  const taxAmount = form.lines.reduce((sum, row) => sum + Number(row.taxAmount || "0"), 0);
  return {
    patientId: form.patientId,
    consultationId: form.consultationId.trim() || null,
    appointmentId: form.appointmentId.trim() || null,
    billDate: form.billDate,
    discountType: form.discountType,
    discountValue: form.discountType === "NONE" ? null : (form.discountValue.trim() ? Number(form.discountValue) : 0),
    discountReason: form.discountType === "NONE" ? null : (form.discountReason.trim() || null),
    taxAmount: taxAmount > 0 ? taxAmount : null,
    notes: form.notes.trim() || null,
    lines: form.lines.filter((row) => row.itemName.trim()).map((row, index) => ({
      itemType: resolveItemType(row.itemType),
      itemName: row.itemName.trim(),
      quantity: Number(row.quantity || "1"),
      unitPrice: Number(row.unitPrice || "0"),
      referenceId: row.referenceId.trim() || null,
      sortOrder: row.sortOrder.trim() ? Number(row.sortOrder) : index + 1,
      lineDiscountAmount: Number(row.lineDiscountAmount || "0"),
      batchNumber: null,
      dispensationReferenceId: null,
    })),
  };
}

function draftBillTotals(form: BillFormState): DraftTotals {
  const subtotal = form.lines.reduce((sum, row) => sum + (Number(row.quantity || "0") * Number(row.unitPrice || "0")), 0);
  const lineDiscount = form.lines.reduce((sum, row) => sum + Number(row.lineDiscountAmount || "0"), 0);
  const taxableSubtotal = Math.max(0, subtotal - lineDiscount);
  const billDiscount = (() => {
    if (form.discountType === "NONE") return 0;
    const discountValue = Number(form.discountValue || "0");
    if (form.discountType === "PERCENTAGE") return Math.min(100, Math.max(0, discountValue)) * taxableSubtotal / 100;
    return Math.min(taxableSubtotal, Math.max(0, discountValue));
  })();
  const tax = form.lines.reduce((sum, row) => sum + Number(row.taxAmount || "0"), 0);
  const total = Math.max(0, taxableSubtotal - billDiscount + tax);
  return { subtotal, lineDiscount, billDiscount, tax, total };
}

function normalizeDraftText(value: string) {
  return value.trim().replace(/\s+/g, " ").toLowerCase();
}

function formatCatalogPrice(value: number | null | undefined) {
  if (value == null || Number.isNaN(Number(value))) return "";
  return Number(value).toFixed(2);
}

function mergeMedicineCatalog(stocks: Stock[], medicines: Medicine[]): CatalogItem[] {
  const merged = new Map<string, CatalogItem & { quantityOnHand: number; earliestExpiry: string | null }>();

  for (const stock of stocks) {
    if (!stock.active) continue;
    const key = stock.medicineId;
    const current = merged.get(key) || {
      itemName: stock.medicineName,
      itemType: "MEDICINE" as const,
      unitPrice: "",
      referenceId: key,
      availabilityLabel: null,
      quantityOnHand: 0,
      earliestExpiry: null,
    };
    current.quantityOnHand += Number(stock.quantityOnHand || 0);
    const sellingPrice = stock.sellingPrice != null && Number(stock.sellingPrice) > 0 ? stock.sellingPrice : null;
    if (!current.unitPrice && sellingPrice != null) {
      current.unitPrice = formatCatalogPrice(sellingPrice);
    }
    if (!current.availabilityLabel) {
      current.availabilityLabel = current.quantityOnHand > 0 ? `${current.quantityOnHand} in stock` : "No stock";
    } else if (current.quantityOnHand > 0) {
      current.availabilityLabel = `${current.quantityOnHand} in stock`;
    }
    if (stock.expiryDate && (!current.earliestExpiry || stock.expiryDate < current.earliestExpiry)) {
      current.earliestExpiry = stock.expiryDate;
    }
    merged.set(key, current);
  }

  for (const medicine of medicines) {
    const key = medicine.id;
    const current = merged.get(key);
    if (current) {
      if (!current.unitPrice) {
        current.unitPrice = formatCatalogPrice(medicine.defaultPrice);
      }
      if (!current.availabilityLabel) {
        current.availabilityLabel = medicine.active ? "No stock" : "Inactive";
      }
      merged.set(key, current);
      continue;
    }
    merged.set(key, {
      itemName: medicine.medicineName,
      itemType: "MEDICINE",
      unitPrice: formatCatalogPrice(medicine.defaultPrice),
      referenceId: key,
      availabilityLabel: medicine.active ? "No stock" : "Inactive",
      quantityOnHand: 0,
      earliestExpiry: null,
    });
  }

  return Array.from(merged.values())
    .map(({ quantityOnHand: _quantityOnHand, earliestExpiry: _earliestExpiry, ...item }) => item)
    .sort((left, right) => left.itemName.localeCompare(right.itemName));
}

function statusColor(status: Bill["status"]) {
  switch (status) {
    case "PAID": return "success";
    case "PARTIALLY_PAID":
    case "PARTIALLY_REFUNDED": return "warning";
    case "REFUND_PENDING": return "warning";
    case "UNPAID":
    case "ISSUED": return "info";
    case "CANCELLED_REFUNDED": return "success";
    case "REFUNDED":
    case "CANCELLED":
    case "DRAFT":
    default: return "default";
  }
}

function lineTotal(row: BillLineForm) {
  const quantity = Number(row.quantity || "0");
  const unitPrice = Number(row.unitPrice || "0");
  const discount = Number(row.lineDiscountAmount || "0");
  const tax = Number(row.taxAmount || "0");
  return Math.max(0, quantity * unitPrice - discount + tax).toFixed(2);
}

function parseMoney(value: string | null) {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

function formatAmount(value: number | null | undefined) {
  if (value == null) return "—";
  return `₹${value.toFixed(2)}`;
}

function formatAppointmentSummary(appointment: Appointment | null) {
  if (!appointment) return null;
  const time = appointment.appointmentTime ? appointment.appointmentTime.slice(0, 5) : null;
  const dateLabel = new Date(`${appointment.appointmentDate}T00:00:00`).toLocaleDateString(undefined, {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const timeLabel = time ? new Date(`1970-01-01T${time}:00`).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }) : "—";
  const doctorLabel = appointment.doctorName || "Doctor";
  return `${dateLabel} • ${timeLabel} • ${doctorLabel}`;
}

function formatPendingConsultationFeeSummary(fee: PendingConsultationFee) {
  const time = fee.appointmentTime ? fee.appointmentTime.slice(0, 5) : null;
  const dateLabel = new Date(`${fee.appointmentDate}T00:00:00`).toLocaleDateString(undefined, {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const timeLabel = time ? new Date(`1970-01-01T${time}:00`).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }) : "—";
  const doctorLabel = fee.doctorName || "Doctor";
  return `${dateLabel} • ${timeLabel} • ${doctorLabel}`;
}

function billHasConsultationLine(bill: Bill) {
  return bill.lines.some((line) => line.itemType === "CONSULTATION");
}

function isConsultationDraftLine(line: BillLineForm) {
  return line.itemType === "CONSULTATION"
    && (
      normalizeDraftText(line.itemName).includes("consultation fee")
      || normalizeDraftText(line.scanCode).startsWith("consultation:")
    );
}

function createConsultationDraftLine(doctorName: string, doctorUserId: string, consultationFee: number): BillLineForm {
  return {
    itemType: "CONSULTATION",
    itemName: `Consultation Fee - ${doctorName}`,
    quantity: "1",
    unitPrice: consultationFee.toFixed(2),
    lineDiscountAmount: "",
    taxAmount: "",
    referenceId: doctorUserId,
    scanCode: `CONSULTATION:${doctorUserId}`,
    sortOrder: "1",
  };
}

function consultationAppointmentBills(bills: Bill[], appointmentId: string) {
  return bills
    .filter((bill) => bill.appointmentId === appointmentId && billHasConsultationLine(bill) && bill.status !== "CANCELLED" && bill.status !== "REFUND_PENDING" && bill.status !== "CANCELLED_REFUNDED")
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

function consultationEffectiveBill(bills: Bill[]) {
  return bills.find((bill) => bill.dueAmount > 0) || bills[0] || null;
}

function consultationFeeState(consultationFee: number | null, bills: Bill[]) {
  const effectiveFee = consultationFee ?? (bills.length > 0 ? Math.max(...bills.map((bill) => bill.totalAmount)) : null);
  const grossPaid = bills.reduce((sum, bill) => sum + Math.max(0, bill.paidAmount), 0);
  const due = effectiveFee == null ? null : Math.max(0, effectiveFee - grossPaid);
  return {
    effectiveFee,
    netPaid: grossPaid,
    due,
    paid: effectiveFee != null && due != null && due <= 0,
    effectiveBill: consultationEffectiveBill(bills),
  };
}

function billNetPaidAmount(bill: Bill) {
  return Math.max(0, bill.netPaidAmount ?? (bill.paidAmount - bill.refundedAmount));
}

function billRefundableAmount(bill: Bill) {
  return Math.max(0, bill.paidAmount - bill.refundedAmount);
}

function billEffectiveDueAmount(bill: Bill) {
  return Math.max(0, bill.dueAmount ?? (bill.totalAmount - bill.paidAmount));
}

function billRefundedAmount(bill: Bill) {
  return Math.max(0, bill.refundedAmount);
}

function billHasCollectableDue(bill: Bill) {
  return billEffectiveDueAmount(bill) > 0;
}

function billCountsAsSettled(bill: Bill) {
  return bill.status !== "CANCELLED"
    && bill.paidAmount > 0
    && billEffectiveDueAmount(bill) <= 0;
}

function latestReceiptForBill(receipts: Receipt[], billId: string) {
  return receipts
    .filter((receipt) => receipt.billId === billId)
    .slice()
    .sort((left, right) => `${right.receiptDate}|${right.createdAt}`.localeCompare(`${left.receiptDate}|${left.createdAt}`))[0] || null;
}

function paymentForReceipt(payments: Payment[], receipt: Receipt | null) {
  if (!receipt) {
    return null;
  }
  return payments.find((payment) => payment.id === receipt.paymentId) || null;
}

function preferredPatientCollectBill(bills: Bill[]) {
  return bills.find((bill) => billHasCollectableDue(bill)) || null;
}

function preferredPatientReceiptBill(bills: Bill[]) {
  return bills.find((bill) => billCountsAsSettled(bill)) || null;
}

function preferredPatientBill(bills: Bill[]) {
  return bills.find((bill) => billHasCollectableDue(bill))
    || bills.find((bill) => bill.status !== "CANCELLED" && bill.status !== "REFUND_PENDING" && bill.status !== "CANCELLED_REFUNDED")
    || bills[0]
    || null;
}

function getBillingWorkflowStep(args: {
  patientId: string;
  lineCount: number;
  hasSettledBill: boolean;
}): BillingWorkflowStep {
  if (args.hasSettledBill) return "RECEIPT";
  if (!args.patientId) return "PATIENT";
  if (args.lineCount <= 0) return "CHARGES";
  return "REVIEW_PAYMENT";
}

type InvoicePreviewState = InvoicePrintData | null;
type ReceiptPreviewState = ReceiptPrintData | null;

export default function BillsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const consultationAppointmentId = searchParams.get("appointmentId") || "";
  const consultationPatientId = searchParams.get("patientId") || "";
  const consultationDoctorUserId = searchParams.get("doctorUserId") || "";
  const consultationFeeAmount = parseMoney(searchParams.get("consultationFee"));
  const consultationReturnTo = searchParams.get("returnTo") || "";
  const consultationReason = searchParams.get("reason") || "Consultation Fee";
  const consultationCollectionRequested = searchParams.get("collectConsultationFee") === "1";
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [selectedBill, setSelectedBill] = React.useState<Bill | null>(null);
  const [payments, setPayments] = React.useState<Payment[]>([]);
  const [receipts, setReceipts] = React.useState<Receipt[]>([]);
  const [refunds, setRefunds] = React.useState<Refund[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [clinicUsers, setClinicUsers] = React.useState<ClinicUser[]>([]);
  const [doctorProfiles, setDoctorProfiles] = React.useState<Record<string, DoctorProfile>>({});
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientSearchResults, setPatientSearchResults] = React.useState<Patient[]>([]);
  const [billFilterPatient, setBillFilterPatient] = React.useState(consultationPatientId);
  const [billFilterStatus, setBillFilterStatus] = React.useState<string>("");
  const [billFilterText, setBillFilterText] = React.useState("");
  const [billFilterAppointmentId, setBillFilterAppointmentId] = React.useState(consultationAppointmentId);
  const [billFilterMode, setBillFilterMode] = React.useState<string>("");
  const [billFilterFromDate, setBillFilterFromDate] = React.useState("");
  const [billFilterToDate, setBillFilterToDate] = React.useState("");
  const [form, setForm] = React.useState<BillFormState>(() => ({
    ...emptyBillForm(),
    patientId: consultationPatientId,
    appointmentId: consultationAppointmentId,
  }));
  const [paymentForm, setPaymentForm] = React.useState<PaymentFormState>(emptyPaymentForm());
  const [refundForm, setRefundForm] = React.useState<RefundFormState>(emptyRefundForm());
  const [consultationFeeOpen, setConsultationFeeOpen] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [paymentOpen, setPaymentOpen] = React.useState(false);
  const [refundOpen, setRefundOpen] = React.useState(false);
  const [billFieldErrors, setBillFieldErrors] = React.useState<FieldErrorMap>({});
  const [paymentFieldErrors, setPaymentFieldErrors] = React.useState<FieldErrorMap>({});
  const [refundFieldErrors, setRefundFieldErrors] = React.useState<FieldErrorMap>({});
  const [patientScopedBills, setPatientScopedBills] = React.useState<Bill[]>([]);
  const [patientBillingContext, setPatientBillingContext] = React.useState<PatientBillingContext | null>(null);
  const [refundBillSearch, setRefundBillSearch] = React.useState("");
  const [refundSearchResults, setRefundSearchResults] = React.useState<Bill[]>([]);
  const [refundSearchLoading, setRefundSearchLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [workingId, setWorkingId] = React.useState<string | null>(null);
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [invoicePreview, setInvoicePreview] = React.useState<InvoicePreviewState>(null);
  const [invoicePreviewLoading, setInvoicePreviewLoading] = React.useState(false);
  const [receiptPreview, setReceiptPreview] = React.useState<ReceiptPreviewState>(null);
  const [receiptPreviewLoading, setReceiptPreviewLoading] = React.useState(false);
  const [invoiceAutoPrint, setInvoiceAutoPrint] = React.useState(false);
  const [receiptAutoPrint, setReceiptAutoPrint] = React.useState(false);
  const [consultationAppointment, setConsultationAppointment] = React.useState<Appointment | null>(null);
  const [consultationDoctorProfile, setConsultationDoctorProfile] = React.useState<DoctorProfile | null>(null);
  const [consultationContextLoading, setConsultationContextLoading] = React.useState(false);
  const [consultationContextError, setConsultationContextError] = React.useState<string | null>(null);
  const [scanQuery, setScanQuery] = React.useState("");
  const [scanItemType, setScanItemType] = React.useState<ChargeSearchType>("ALL");
  const [medicineCatalog, setMedicineCatalog] = React.useState<CatalogItem[]>([]);
  const [consultationDoctorUserIdForDraft, setConsultationDoctorUserIdForDraft] = React.useState("");
  const [manualScanPrompt, setManualScanPrompt] = React.useState<string | null>(null);
  const [chargeSearchFocused, setChargeSearchFocused] = React.useState(false);
  const [chargeHighlightIndex, setChargeHighlightIndex] = React.useState(0);
  const [paymentLedger, setPaymentLedger] = React.useState<PaymentLedgerRow[]>([]);
  const [pendingConsultationFeeDialog, setPendingConsultationFeeDialog] = React.useState<PendingConsultationFee | null>(null);
  const [pendingConsultationFeeOpen, setPendingConsultationFeeOpen] = React.useState(false);
  const [ledgerActionBill, setLedgerActionBill] = React.useState<Bill | null>(null);
  const [ledgerActionAnchorEl, setLedgerActionAnchorEl] = React.useState<HTMLElement | null>(null);
  const [manualLineItemsOpen, setManualLineItemsOpen] = React.useState(false);
  const [moreBillingOptionsOpen, setMoreBillingOptionsOpen] = React.useState(false);
  const [ledgerCollapsed, setLedgerCollapsed] = React.useState(true);
  const scanInputRef = React.useRef<HTMLInputElement | null>(null);
  const patientSearchRef = React.useRef<HTMLInputElement | null>(null);
  const billDateInputRef = React.useRef<HTMLInputElement | null>(null);
  const discountReasonInputRef = React.useRef<HTMLInputElement | null>(null);
  const consultationSectionRef = React.useRef<HTMLDivElement | null>(null);
  const consultationDoctorSelectRef = React.useRef<HTMLInputElement | null>(null);

  const canCreateBill = auth.hasPermission("billing.create");
  const canUpdateBill = auth.hasPermission("billing.update") || auth.hasPermission("billing.create");
  const canCancelBill = auth.hasPermission("billing.update");
  const canCollectPayment = auth.hasPermission("payment.collect");
  const canSendReceipt = canCollectPayment || auth.hasPermission("notification.send");
  const canRefund = auth.hasPermission("billing.update");
  const canSendInvoice = canCreateBill || canCollectPayment || auth.hasPermission("notification.send");
  const canReadMedicineMaster = auth.permissions.includes("medicine.read");
  const denseTextFieldProps = { size: "small" as const, fullWidth: true };
  const denseSelectProps = { size: "small" as const, fullWidth: true };
  const consultationFeeRequested = React.useMemo(
    () => consultationCollectionRequested && Boolean(consultationAppointmentId),
    [consultationCollectionRequested, consultationAppointmentId],
  );
  const resolvedConsultationFee = consultationFeeAmount ?? consultationDoctorProfile?.consultationFee ?? null;
  const consultationPatientLabel = consultationAppointment
    ? [
        consultationAppointment.patientName || consultationAppointment.patientNumber || "Patient",
        consultationAppointment.patientNumber && consultationAppointment.patientName ? consultationAppointment.patientNumber : null,
        consultationAppointment.patientMobile || null,
      ].filter(Boolean).join(" • ")
      : consultationPatientId
        ? (patients.find((patient) => patient.id === consultationPatientId)?.patientNumber || consultationPatientId)
      : null;
  const consultationDoctorUserProfileId = consultationAppointment?.doctorUserId || consultationDoctorUserId || "";
  const consultationDoctorLabel = consultationDoctorProfile?.doctorName || consultationAppointment?.doctorName || null;
  const consultationAppointmentLabel = formatAppointmentSummary(consultationAppointment);
  const consultationBills = React.useMemo(
    () => consultationAppointmentId ? consultationAppointmentBills(bills, consultationAppointmentId) : [],
    [bills, consultationAppointmentId],
  );
  const consultationPaymentState = React.useMemo(
    () => consultationFeeState(resolvedConsultationFee, consultationBills),
    [consultationBills, resolvedConsultationFee],
  );
  const consultationExistingBill = React.useMemo(
    () => consultationPaymentState.effectiveBill,
    [consultationPaymentState.effectiveBill],
  );
  const doctorOptions = React.useMemo(
    () => clinicUsers
      .filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR")
      .map((user) => {
        const profile = doctorProfiles[user.appUserId];
        const fallbackLabel = user.displayName || user.email || user.appUserId;
        return {
          appUserId: user.appUserId,
          displayName: profile?.doctorName || fallbackLabel,
          consultationFee: profile?.consultationFee ?? null,
        } satisfies DoctorOption;
      })
      .sort((left, right) => left.displayName.localeCompare(right.displayName)),
    [clinicUsers, doctorProfiles],
  );
  const selectedConsultationDoctor = React.useMemo(
    () => doctorOptions.find((doctor) => doctor.appUserId === consultationDoctorUserIdForDraft) || null,
    [consultationDoctorUserIdForDraft, doctorOptions],
  );
  const consultationFeeQuickAmount = consultationFeeAmount ?? selectedConsultationDoctor?.consultationFee ?? consultationDoctorProfile?.consultationFee ?? null;
  const consultationDraftHasLine = React.useMemo(
    () => form.lines.some((line) => isConsultationDraftLine(line)),
    [form.lines],
  );
  const selectedPatient = React.useMemo(
    () => patients.find((patient) => patient.id === form.patientId) || null,
    [patients, form.patientId],
  );
  const patientBills = React.useMemo(
    () => patientScopedBills.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [patientScopedBills],
  );
  const preferredPatientCollectPaymentBill = React.useMemo(
    () => preferredPatientCollectBill(patientBills),
    [patientBills],
  );
  const preferredPatientReceiptViewBill = React.useMemo(
    () => preferredPatientReceiptBill(patientBills),
    [patientBills],
  );
  const preferredPatientPaymentBill = React.useMemo(
    () => preferredPatientBill(patientBills),
    [patientBills],
  );
  const recentPatientPayments = React.useMemo(
    () => paymentLedger.filter((payment) => payment.patientId === form.patientId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [paymentLedger, form.patientId],
  );
  const patientPendingConsultationFees = React.useMemo(
    () => patientBillingContext?.pendingConsultationFees || [],
    [patientBillingContext],
  );
  const patientBillDueAmount = React.useMemo(
    () => patientBillingContext?.billDueAmount ?? patientBills.reduce((sum, bill) => sum + billEffectiveDueAmount(bill), 0),
    [patientBillingContext, patientBills],
  );
  const patientPendingConsultationDueAmount = React.useMemo(
    () => patientBillingContext?.pendingConsultationFeeAmount ?? patientPendingConsultationFees.reduce((sum, fee) => sum + Math.max(0, fee.dueAmount), 0),
    [patientBillingContext, patientPendingConsultationFees],
  );
  const itemCatalog = React.useMemo(() => {
    const items = new Map<string, CatalogItem>();
    bills.forEach((bill) => {
      bill.lines.forEach((line) => {
        if (line.itemType === "CONSULTATION") return;
        const key = normalizeDraftText(`${line.itemName}|${line.itemType}`);
        if (!items.has(key)) {
          items.set(key, {
            itemName: line.itemName,
            itemType: line.itemType,
            unitPrice: line.unitPrice ? line.unitPrice.toFixed(2) : "",
            referenceId: line.referenceId,
            availabilityLabel: null,
          });
        }
      });
    });
    return Array.from(items.values()).slice(0, 24);
  }, [bills]);
  const consultationCatalogHint = React.useMemo(
    () => doctorOptions.slice(0, 5).map((doctor) => ({
      title: `Consultation Fee - ${doctor.displayName}`,
      subtitle: `Consultation • ${doctor.consultationFee != null ? formatAmount(doctor.consultationFee) : "Fee unavailable"}`,
      doctorUserId: doctor.appUserId,
    })),
    [doctorOptions],
  );
  React.useEffect(() => {
    if (!auth.accessToken || !auth.tenantId || (scanItemType !== "MEDICINE" && scanItemType !== "ALL")) {
      setMedicineCatalog([]);
      return;
    }
    const query = scanQuery.trim();
    if (!query) {
      setMedicineCatalog([]);
      return;
    }
    let cancelled = false;
    const handle = window.setTimeout(() => {
      void (async () => {
        try {
          const [stockRows, medicineRows] = await Promise.all([
            searchStocks(auth.accessToken!, auth.tenantId!, query),
            canReadMedicineMaster ? searchMedicines(auth.accessToken!, auth.tenantId!, query) : Promise.resolve([] as Medicine[]),
          ]);
          if (!cancelled) {
            setMedicineCatalog(mergeMedicineCatalog(stockRows, medicineRows));
          }
        } catch {
          if (!cancelled) {
            setMedicineCatalog([]);
          }
        }
      })();
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, canReadMedicineMaster, scanItemType, scanQuery]);
  const chargeCatalog = React.useMemo(() => {
    const items = new Map<string, CatalogItem>();
    [...itemCatalog, ...medicineCatalog].forEach((item) => {
      const key = normalizeDraftText(`${item.itemName}|${item.itemType}|${item.unitPrice}|${item.referenceId || ""}`);
      if (!items.has(key)) {
        items.set(key, item);
      }
    });
    return Array.from(items.values());
  }, [itemCatalog, medicineCatalog]);
  const chargeSuggestions = React.useMemo(() => {
    const query = normalizeDraftText(scanQuery);
    if (!query || !chargeSearchFocused) return [] as CatalogItem[];
    const source = chargeCatalog.filter((item) => scanItemType === "ALL" || item.itemType === scanItemType);
    const exactMatches = source.filter((item) => normalizeDraftText(item.itemName) === query);
    const startMatches = source.filter((item) => normalizeDraftText(item.itemName).startsWith(query) && normalizeDraftText(item.itemName) !== query);
    const containMatches = source.filter((item) => !normalizeDraftText(item.itemName).startsWith(query) && normalizeDraftText(item.itemName).includes(query));
    const typeMatches = source.filter((item) => normalizeDraftText(billItemCategoryLabel(item.itemType)).includes(query) && !normalizeDraftText(item.itemName).includes(query));
    const ranked = [...exactMatches, ...startMatches, ...containMatches, ...typeMatches];
    const seen = new Set<string>();
    return ranked.filter((item) => {
      const key = normalizeDraftText(`${item.itemName}|${item.itemType}|${item.unitPrice}|${item.referenceId || ""}`);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    }).slice(0, 8);
  }, [chargeCatalog, chargeSearchFocused, scanItemType, scanQuery]);
  const chargeSuggestionGroups = React.useMemo(() => {
    const groupOrder: BillItemCategory[] = ["TEST", "MEDICINE", "PROCEDURE", "SERVICE", "PACKAGE", "OTHER"];
    const groups = new Map<BillItemCategory, CatalogItem[]>();
    chargeSuggestions.forEach((item) => {
      const current = groups.get(item.itemType) || [];
      current.push(item);
      groups.set(item.itemType, current);
    });
    return groupOrder
      .map((itemType) => ({
        itemType,
        label: chargeSuggestionTypeLabel(itemType),
        items: groups.get(itemType) || [],
      }))
      .filter((group) => group.items.length > 0);
  }, [chargeSuggestions]);
  React.useEffect(() => {
    if (!chargeSearchFocused || chargeSuggestions.length === 0) {
      setChargeHighlightIndex(-1);
      return;
    }
    setChargeHighlightIndex((current) => {
      if (current < 0) return 0;
      return Math.min(current, chargeSuggestions.length - 1);
    });
  }, [chargeSearchFocused, chargeSuggestions.length]);
  const currentDraftTotals = React.useMemo(() => draftBillTotals(form), [form]);
  const isNoDiscount = form.discountType === "NONE";
  const discountRequired = !isNoDiscount;
  const discountLabel = form.discountType === "PERCENTAGE" ? "Discount (%)" : "Discount value";
  const consultationDraftReady = React.useMemo(
    () => consultationDraftHasLine && currentDraftTotals.total > 0
      && form.appointmentId === consultationAppointmentId
      && Boolean(form.patientId),
    [consultationDraftHasLine, currentDraftTotals.total, form.appointmentId, form.patientId, consultationAppointmentId],
  );
  const selectedPatientTotalDue = React.useMemo(
    () => patientBillDueAmount + patientPendingConsultationDueAmount,
    [patientBillDueAmount, patientPendingConsultationDueAmount],
  );
  const billingWorkflowStep = React.useMemo(() => getBillingWorkflowStep({
    patientId: form.patientId,
    lineCount: form.lines.filter((line) => line.itemName.trim()).length,
    hasSettledBill: Boolean(selectedBill && billCountsAsSettled(selectedBill)),
  }), [form.lines, form.patientId, selectedBill]);
  const ledgerSummary = React.useMemo(() => {
    const visibleBills = bills;
    return {
      visible: visibleBills.length,
      paid: visibleBills.filter((bill) => billCountsAsSettled(bill)).length,
      pending: visibleBills.filter((bill) => billHasCollectableDue(bill)).length,
      dueTotal: visibleBills.reduce((sum, bill) => sum + billEffectiveDueAmount(bill), 0),
    };
  }, [bills]);
  const consultationDraftSeededRef = React.useRef<string | null>(null);
  const refundCandidateBills = React.useMemo(() => {
    const fallbackBills = refundBillSearch.trim() ? refundSearchResults : [...patientBills, ...bills];
    const seen = new Set<string>();
    return fallbackBills
      .filter((bill) => billRefundableAmount(bill) > 0)
      .filter((bill) => {
        if (seen.has(bill.id)) return false;
        seen.add(bill.id);
        return true;
      })
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
      .slice(0, refundBillSearch.trim() ? 12 : 8);
  }, [bills, patientBills, refundBillSearch, refundSearchResults]);
  const activePatientCollectBill = selectedBill && billHasCollectableDue(selectedBill)
    ? selectedBill
    : preferredPatientCollectPaymentBill;
  const activePatientReceiptBill = selectedBill && billCountsAsSettled(selectedBill)
    ? selectedBill
    : preferredPatientReceiptViewBill;
  const preferredPatientPendingConsultationFee = React.useMemo(
    () => patientPendingConsultationFees[0] || null,
    [patientPendingConsultationFees],
  );
  const patientPrimaryAction = React.useMemo(() => {
    if (selectedBill && billCountsAsSettled(selectedBill)) {
      return { kind: "receipt" as const, bill: selectedBill };
    }
    if (selectedBill && billHasCollectableDue(selectedBill)) {
      return { kind: "collect" as const, bill: selectedBill };
    }
    if (preferredPatientCollectPaymentBill) {
      return { kind: "collect" as const, bill: preferredPatientCollectPaymentBill };
    }
    if (preferredPatientReceiptViewBill) {
      return { kind: "receipt" as const, bill: preferredPatientReceiptViewBill };
    }
    if (preferredPatientPendingConsultationFee) {
      return { kind: "consultation-fee" as const, fee: preferredPatientPendingConsultationFee };
    }
    return null;
  }, [preferredPatientCollectPaymentBill, preferredPatientPendingConsultationFee, preferredPatientReceiptViewBill, selectedBill]);

  const loadBills = React.useCallback(async (override?: {
    patientId?: string;
    appointmentId?: string;
    status?: string;
    fromDate?: string;
    toDate?: string;
    paymentMode?: string;
    text?: string;
  }) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const patientId = override?.patientId ?? billFilterPatient;
    const appointmentId = override?.appointmentId ?? billFilterAppointmentId;
    const status = override?.status ?? billFilterStatus;
    const fromDate = override?.fromDate ?? billFilterFromDate;
    const toDate = override?.toDate ?? billFilterToDate;
    const paymentMode = override?.paymentMode ?? billFilterMode;
    const text = override?.text ?? billFilterText;
    const rows = await searchBills(auth.accessToken, auth.tenantId, {
      patientId: patientId.trim() || undefined,
      appointmentId: appointmentId.trim() || undefined,
      status: status ? (status as Bill["status"]) : null,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
      paymentMode: paymentMode ? (paymentMode as PaymentMode) : null,
      search: text.trim() || undefined,
    });
    setBills(rows);
  }, [auth.accessToken, auth.tenantId, billFilterPatient, billFilterAppointmentId, billFilterStatus, billFilterFromDate, billFilterToDate, billFilterMode, billFilterText]);

  const loadPatientBills = React.useCallback(async (patientId: string) => {
    if (!auth.accessToken || !auth.tenantId || !patientId) {
      setPatientScopedBills([]);
      return;
    }
    try {
      const rows = await searchBills(auth.accessToken, auth.tenantId, {
        patientId,
      });
      setPatientScopedBills(rows);
    } catch {
      setPatientScopedBills([]);
    }
  }, [auth.accessToken, auth.tenantId]);

  const loadPatientBillingContext = React.useCallback(async (patientId: string) => {
    if (!auth.accessToken || !auth.tenantId || !patientId) {
      setPatientBillingContext(null);
      return;
    }
    try {
      const context = await getPatientBillingContext(auth.accessToken, auth.tenantId, patientId);
      setPatientBillingContext(context);
    } catch {
      setPatientBillingContext(null);
    }
  }, [auth.accessToken, auth.tenantId]);

  const loadPatientPayments = React.useCallback(async (patientId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const rows = await listPaymentsLedger(auth.accessToken, auth.tenantId, { patientId, size: 8 });
      setPaymentLedger(rows);
    } catch {
      setPaymentLedger([]);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) { setLoading(false); return; }
      setLoading(true);
      setError(null);
      try {
        const [billRows, patientRows, userRows] = await Promise.all([
          searchBills(auth.accessToken, auth.tenantId, {}),
          searchPatients(auth.accessToken, auth.tenantId, { active: true }),
          getClinicUsers(auth.accessToken, auth.tenantId).catch(() => []),
        ]);
        if (!cancelled) {
          setBills(billRows);
          setPatients(patientRows);
          setClinicUsers(userRows);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load billing data");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void bootstrap();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadConsultationContext() {
      if (!consultationAppointmentId || !auth.accessToken || !auth.tenantId) {
        setConsultationAppointment(null);
        setConsultationDoctorProfile(null);
        setConsultationContextLoading(false);
        setConsultationContextError(null);
        return;
      }
      setConsultationContextLoading(true);
      setConsultationContextError(null);
      try {
        const appointment = await getAppointment(auth.accessToken, auth.tenantId, consultationAppointmentId);
        if (cancelled) return;
        setConsultationAppointment(appointment);
        if (appointment.doctorUserId) {
          const profile = await getDoctorProfile(auth.accessToken, auth.tenantId, appointment.doctorUserId).catch(() => null);
          if (cancelled) return;
          setConsultationDoctorProfile(profile);
        } else {
          setConsultationDoctorProfile(null);
        }
      } catch {
        if (!cancelled) {
          setConsultationAppointment(null);
          setConsultationDoctorProfile(null);
          setConsultationContextError("Unable to load appointment billing context.");
        }
      } finally {
        if (!cancelled) setConsultationContextLoading(false);
      }
    }
    void loadConsultationContext();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId, consultationAppointmentId]);

  React.useEffect(() => {
    if (!consultationFeeRequested) {
      return;
    }
    if (consultationContextLoading) {
      return;
    }
    if (resolvedConsultationFee == null) {
      setError(consultationContextError || "Doctor consultation fee is not configured.");
    }
  }, [consultationFeeRequested, consultationContextLoading, resolvedConsultationFee, consultationContextError]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinic() {
      if (!auth.accessToken || !auth.tenantId) return;
      try {
        const profile = await getClinicProfile(auth.accessToken, auth.tenantId);
        if (!cancelled) setClinicProfile(profile);
      } catch {
        if (!cancelled) setClinicProfile(null);
      }
    }
    void loadClinic();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (!form.patientId) {
      setPaymentLedger([]);
      setPatientScopedBills([]);
      setPatientBillingContext(null);
      return;
    }
    void loadPatientBills(form.patientId);
    void loadPatientPayments(form.patientId);
    void loadPatientBillingContext(form.patientId);
  }, [form.patientId, loadPatientBillingContext, loadPatientBills, loadPatientPayments]);

  React.useEffect(() => {
    let cancelled = false;
    async function hydrateDoctorProfiles() {
      if (!auth.accessToken || !auth.tenantId || clinicUsers.length === 0) {
        setDoctorProfiles({});
        return;
      }
      const doctors = clinicUsers.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
      const entries = await Promise.all(
        doctors.map(async (doctor) => {
          const profile = await getDoctorProfile(auth.accessToken!, auth.tenantId!, doctor.appUserId).catch(() => null);
          return [doctor.appUserId, profile] as const;
        }),
      );
      if (!cancelled) {
        const nextProfiles: Record<string, DoctorProfile> = {};
        entries.forEach(([doctorUserId, profile]) => {
          if (profile) nextProfiles[doctorUserId] = profile;
        });
        setDoctorProfiles(nextProfiles);
      }
    }
    void hydrateDoctorProfiles();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId, clinicUsers]);

  React.useEffect(() => {
    if (!consultationDoctorUserIdForDraft) {
      const initialDoctor = consultationAppointment?.doctorUserId || consultationDoctorUserId || doctorOptions[0]?.appUserId || "";
      setConsultationDoctorUserIdForDraft(initialDoctor);
    }
  }, [consultationAppointment?.doctorUserId, consultationDoctorUserId, doctorOptions, consultationDoctorUserIdForDraft]);

  React.useEffect(() => {
    if (!consultationAppointmentId) {
      consultationDraftSeededRef.current = null;
      return;
    }
    setForm((current) => ({
      ...current,
      patientId: current.patientId || consultationAppointment?.patientId || consultationPatientId,
      appointmentId: consultationAppointmentId,
      consultationId: current.consultationId || consultationAppointment?.consultationId || "",
    }));
  }, [
    consultationAppointment?.consultationId,
    consultationAppointment?.patientId,
    consultationAppointmentId,
    consultationPatientId,
  ]);

  const prepareConsultationDraft = React.useCallback(() => {
    if (consultationExistingBill) {
      void selectBill(consultationExistingBill);
      if ((consultationPaymentState.due ?? 0) > 0) {
        setSuccess("Consultation fee is already billed. Collect the pending payment from the existing bill.");
      } else {
        setSuccess("Consultation fee is already paid for this appointment.");
      }
      return false;
    }
    if (!consultationDoctorUserIdForDraft) {
      setError("Select the consultant before adding the consultation fee.");
      return false;
    }
    if (consultationFeeQuickAmount == null || consultationFeeQuickAmount <= 0) {
      setError("Doctor consultation fee is not configured.");
      return false;
    }
    const doctorName = selectedConsultationDoctor?.displayName || consultationDoctorLabel || "Doctor";
    const patientId = consultationAppointment?.patientId || consultationPatientId || form.patientId;
    let added = false;
    let alreadyPresent = false;
    setForm((current) => {
      const nextLine = createConsultationDraftLine(doctorName, consultationDoctorUserIdForDraft, consultationFeeQuickAmount);
      const nextLines = current.lines.slice();
      const existingIndex = nextLines.findIndex((line) => isConsultationDraftLine(line));
      if (existingIndex >= 0) {
        alreadyPresent = true;
        nextLines[existingIndex] = {
          ...nextLines[existingIndex],
          ...nextLine,
          sortOrder: nextLines[existingIndex].sortOrder || String(existingIndex + 1),
        };
      } else if (nextLines.length === 1 && !nextLines[0].itemName.trim()) {
        added = true;
        nextLines[0] = { ...nextLine, sortOrder: nextLines[0].sortOrder || "1" };
      } else {
        added = true;
        nextLines.push({ ...nextLine, sortOrder: String(nextLines.length + 1) });
      }
      return {
        ...current,
        patientId: patientId || current.patientId,
        appointmentId: consultationAppointmentId || current.appointmentId,
        consultationId: current.consultationId || consultationAppointment?.consultationId || "",
        lines: nextLines,
      };
    });
    consultationDraftSeededRef.current = consultationAppointmentId || consultationDraftSeededRef.current;
    setSuccess(alreadyPresent ? "Consultation fee draft is already ready." : "Consultation fee draft ready.");
    return added || alreadyPresent;
  }, [
    consultationAppointment?.consultationId,
    consultationAppointment?.patientId,
    consultationAppointmentId,
    consultationDoctorLabel,
    consultationDoctorUserIdForDraft,
    consultationExistingBill,
    consultationFeeQuickAmount,
    consultationPatientId,
    form.patientId,
    selectedConsultationDoctor?.displayName,
  ]);

  React.useEffect(() => {
    if (!consultationFeeRequested || consultationContextLoading || consultationExistingBill) {
      return;
    }
    if (consultationDraftSeededRef.current === consultationAppointmentId) {
      return;
    }
    if (resolvedConsultationFee == null || resolvedConsultationFee <= 0 || !consultationDoctorUserIdForDraft) {
      return;
    }
    prepareConsultationDraft();
  }, [
    consultationAppointmentId,
    consultationContextLoading,
    consultationDoctorUserIdForDraft,
    consultationExistingBill,
    consultationFeeRequested,
    prepareConsultationDraft,
    resolvedConsultationFee,
  ]);

  React.useEffect(() => {
    if (!consultationExistingBill) {
      return;
    }
    if (selectedBill?.id === consultationExistingBill.id) {
      return;
    }
    void selectBill(consultationExistingBill);
  }, [consultationExistingBill, selectedBill?.id]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientQuery.trim().length < 2) { setPatientSearchResults([]); return; }
      try {
        const term = patientQuery.trim();
        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^\d{6,}$/.test(term) ? term : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^\d{6,}$/.test(term) ? undefined : term,
          active: true,
        });
        if (!cancelled) setPatientSearchResults(rows);
      } catch {
        if (!cancelled) setPatientSearchResults([]);
      }
    }, 300);
    return () => { cancelled = true; window.clearTimeout(handle); };
  }, [auth.accessToken, auth.tenantId, patientQuery]);

  React.useEffect(() => {
    if (!canCreateBill) return;
    window.setTimeout(() => scanInputRef.current?.focus(), 80);
  }, [canCreateBill]);

  React.useEffect(() => {
    if (invoiceAutoPrint && invoicePreview && !invoicePreviewLoading) {
      const handle = window.setTimeout(() => window.print(), 60);
      setInvoiceAutoPrint(false);
      return () => window.clearTimeout(handle);
    }
    return undefined;
  }, [invoiceAutoPrint, invoicePreview, invoicePreviewLoading]);

  React.useEffect(() => {
    if (receiptAutoPrint && receiptPreview && !receiptPreviewLoading) {
      const handle = window.setTimeout(() => window.print(), 60);
      setReceiptAutoPrint(false);
      return () => window.clearTimeout(handle);
    }
    return undefined;
  }, [receiptAutoPrint, receiptPreview, receiptPreviewLoading]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadRefundSearchResults() {
      if (!refundOpen || !auth.accessToken || !auth.tenantId) {
        return;
      }
      const term = refundBillSearch.trim();
      if (term.length < 2) {
        setRefundSearchResults([]);
        setRefundSearchLoading(false);
        return;
      }
      setRefundSearchLoading(true);
      try {
        const rows = await searchBills(auth.accessToken, auth.tenantId, { search: term });
        if (!cancelled) {
          setRefundSearchResults(rows.filter((bill) => billRefundableAmount(bill) > 0));
        }
      } catch {
        if (!cancelled) {
          setRefundSearchResults([]);
        }
      } finally {
        if (!cancelled) {
          setRefundSearchLoading(false);
        }
      }
    }
    void loadRefundSearchResults();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId, refundBillSearch, refundOpen]);

  const loadInvoicePreview = React.useCallback(async (bill: Bill, autoPrint = false) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setInvoicePreviewLoading(true);
    setInvoiceAutoPrint(autoPrint);
    setInvoicePreview(null);
    try {
      const freshBill = await getBill(auth.accessToken, auth.tenantId, bill.id).catch(() => bill);
      const [patient, appointment, consultation] = await Promise.all([
        getPatient(auth.accessToken, auth.tenantId, freshBill.patientId).then((result) => result.patient).catch(() => null),
        freshBill.appointmentId ? getAppointment(auth.accessToken, auth.tenantId, freshBill.appointmentId).catch(() => null) : Promise.resolve(null),
        freshBill.consultationId ? getConsultation(auth.accessToken, auth.tenantId, freshBill.consultationId).catch(() => null) : Promise.resolve(null),
      ]);
      setInvoicePreview({
        clinicProfile,
        bill: freshBill,
        patient,
        appointment,
        consultation,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load invoice preview");
    } finally {
      setInvoicePreviewLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, clinicProfile]);

  const loadReceiptPreview = React.useCallback(async (receipt: Receipt, payment: Payment | null, autoPrint = false) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setReceiptPreviewLoading(true);
    setReceiptAutoPrint(autoPrint);
    setReceiptPreview(null);
    try {
      const freshBill = selectedBill?.id === receipt.billId
        ? selectedBill
        : await getBill(auth.accessToken, auth.tenantId, receipt.billId).catch(() => null);
      if (!freshBill) {
        throw new Error("Bill not found for receipt");
      }
      const [patient, appointment, consultation] = await Promise.all([
        getPatient(auth.accessToken, auth.tenantId, freshBill.patientId).then((result) => result.patient).catch(() => null),
        freshBill.appointmentId ? getAppointment(auth.accessToken, auth.tenantId, freshBill.appointmentId).catch(() => null) : Promise.resolve(null),
        freshBill.consultationId ? getConsultation(auth.accessToken, auth.tenantId, freshBill.consultationId).catch(() => null) : Promise.resolve(null),
      ]);
      setReceiptPreview({
        clinicProfile,
        bill: freshBill,
        receipt,
        payment,
        patient,
        appointment,
        consultation,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load receipt preview");
    } finally {
      setReceiptPreviewLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, clinicProfile, selectedBill]);

  const refreshSelectedBill = React.useCallback(async (billId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const [billRows, paymentRows, receiptRows, refundRows] = await Promise.all([
      searchBills(auth.accessToken, auth.tenantId, {
        patientId: billFilterPatient.trim() || undefined,
        appointmentId: billFilterAppointmentId.trim() || undefined,
        status: billFilterStatus ? (billFilterStatus as Bill["status"]) : null,
        fromDate: billFilterFromDate || undefined,
        toDate: billFilterToDate || undefined,
        paymentMode: billFilterMode ? (billFilterMode as PaymentMode) : null,
      }),
      listBillPayments(auth.accessToken, auth.tenantId, billId),
      listBillReceipts(auth.accessToken, auth.tenantId, billId),
      listBillRefunds(auth.accessToken, auth.tenantId, billId),
    ]);
    setBills(billRows);
    setPayments(paymentRows);
    setReceipts(receiptRows);
    setRefunds(refundRows);
    const refreshedBill = billRows.find((bill) => bill.id === billId) || null;
    setSelectedBill(refreshedBill);
    if (refreshedBill?.patientId) {
      await loadPatientBills(refreshedBill.patientId);
    }
  }, [auth.accessToken, auth.tenantId, billFilterPatient, billFilterAppointmentId, billFilterStatus, billFilterFromDate, billFilterToDate, billFilterMode, loadPatientBills]);

  const selectBill = async (bill: Bill) => {
    setSelectedBill(bill);
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const [paymentRows, receiptRows, refundRows] = await Promise.all([
        listBillPayments(auth.accessToken, auth.tenantId, bill.id),
        listBillReceipts(auth.accessToken, auth.tenantId, bill.id),
        listBillRefunds(auth.accessToken, auth.tenantId, bill.id),
      ]);
      setPayments(paymentRows);
      setReceipts(receiptRows);
      setRefunds(refundRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load bill detail");
    }
  };

  const resolveBillReceipt = React.useCallback(async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) {
      return null;
    }
    const [billPayments, billReceipts] = bill.id === selectedBill?.id
      ? [payments, receipts]
      : await Promise.all([
        listBillPayments(auth.accessToken, auth.tenantId, bill.id),
        listBillReceipts(auth.accessToken, auth.tenantId, bill.id),
      ]);
    const receipt = latestReceiptForBill(billReceipts, bill.id);
    if (!receipt) {
      return null;
    }
    return {
      receipt,
      payment: paymentForReceipt(billPayments, receipt),
    };
  }, [auth.accessToken, auth.tenantId, payments, receipts, selectedBill?.id]);

  const openBillReceiptPreview = React.useCallback(async (bill: Bill, autoPrint = false) => {
    const resolved = await resolveBillReceipt(bill);
    if (!resolved) {
      setError("Receipt is not available for this bill yet.");
      return;
    }
    await openReceiptPreviewAction(resolved.receipt, resolved.payment, autoPrint);
  }, [resolveBillReceipt]);

  const openBillReceiptPdf = React.useCallback(async (bill: Bill) => {
    const resolved = await resolveBillReceipt(bill);
    if (!resolved) {
      setError("Receipt is not available for this bill yet.");
      return;
    }
    await openReceiptPdf(resolved.receipt);
  }, [resolveBillReceipt]);

  const sendBillReceiptEmail = React.useCallback(async (bill: Bill) => {
    const resolved = await resolveBillReceipt(bill);
    if (!resolved) {
      setError("Receipt is not available for this bill yet.");
      return;
    }
    await sendReceiptAction(resolved.receipt, "email");
  }, [resolveBillReceipt]);

  const refundableAmount = selectedBill ? billRefundableAmount(selectedBill) : 0;

  const submitRefund = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedBill) return;
    const parsed = validateBillingRefund(refundForm);
    if (!parsed.success) {
      setRefundFieldErrors(parsed.fieldErrors);
      setError(Object.values(parsed.fieldErrors)[0] || "Failed to refund");
      return;
    }
    if (Number(parsed.data.amount) > refundableAmount) {
      setRefundFieldErrors({ amount: "Refund amount cannot exceed refundable amount." });
      setError("Refund amount cannot exceed refundable amount.");
      return;
    }
    setRefundFieldErrors({});
    setSaving(true); setError(null); setSuccess(null);
    try {
      await addBillRefund(auth.accessToken, auth.tenantId, selectedBill.id, {
        paymentId: payments[0]?.id || null,
        amount: parsed.data.amount,
        reason: parsed.data.reason.trim(),
        refundMode: parsed.data.refundMode,
        refundedAt: null,
        notes: parsed.data.notes?.trim() || null,
      });
      setRefundOpen(false);
      setRefundForm(emptyRefundForm());
      await refreshSelectedBill(selectedBill.id);
      if (selectedBill.patientId) {
        await loadPatientBills(selectedBill.patientId);
      }
      setSuccess("Refund recorded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refund");
    } finally { setSaving(false); }
  };

  const sendInvoiceAction = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try {
      const result = await sendBillInvoiceEmail(auth.accessToken, auth.tenantId, bill.id);
      await refreshSelectedBill(bill.id);
      setSuccess(result.message || "Invoice email sent");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send invoice email");
    } finally { setWorkingId(null); }
  };

  const openPaymentDialog = (bill: Bill) => {
    setSelectedBill(bill);
    const dueAmount = billEffectiveDueAmount(bill);
    setPaymentForm({ ...emptyPaymentForm(), amount: dueAmount > 0 ? dueAmount.toFixed(2) : bill.totalAmount.toFixed(2) });
    setPaymentFieldErrors({});
    setPaymentOpen(true);
  };

  const openPendingConsultationFeeDialog = (fee: PendingConsultationFee) => {
    setPendingConsultationFeeDialog(fee);
    setPendingConsultationFeeOpen(true);
  };

  const openRefundDialog = (bill: Bill | null = selectedBill) => {
    if (bill) {
      setSelectedBill(bill);
      void selectBill(bill);
    }
    setRefundForm(emptyRefundForm());
    setRefundFieldErrors({});
    setRefundBillSearch("");
    setRefundSearchResults([]);
    setRefundOpen(true);
  };

  const submitPayment = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedBill) return;
    const parsed = validateBillingPayment(paymentForm);
    if (!parsed.success) {
      setPaymentFieldErrors(parsed.fieldErrors);
      setError(Object.values(parsed.fieldErrors)[0] || "Failed to collect payment");
      return;
    }
    if (parsed.data.paymentAmount > billEffectiveDueAmount(selectedBill)) {
      setPaymentFieldErrors({ paymentAmount: "Payment amount cannot exceed the remaining due amount." });
      setError("Payment amount cannot exceed the remaining due amount.");
      return;
    }
    setPaymentFieldErrors({});
    setSaving(true); setError(null); setSuccess(null);
    try {
      await addBillPayment(auth.accessToken, auth.tenantId, selectedBill.id, {
        paymentDate: paymentForm.paymentDate,
        amount: parsed.data.paymentAmount,
        paymentMode: parsed.data.paymentMode,
        referenceNumber: parsed.data.referenceNumber?.trim() || null,
        notes: parsed.data.notes?.trim() || null,
      });
      setPaymentOpen(false);
      await refreshSelectedBill(selectedBill.id);
      if (selectedBill.patientId) {
        await loadPatientBills(selectedBill.patientId);
      }
      setSuccess("Payment collected");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect payment");
    } finally { setSaving(false); }
  };

  const submitConsultationFee = async (value: ConsultationFeeDialogValue) => {
    if (!auth.accessToken || !auth.tenantId || !consultationAppointmentId) {
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const parsed = consultationFeeSchema.safeParse({
        amount: Number(consultationFeeQuickAmount ?? 0),
        paymentMethod: value.paymentMode,
        invoiceNumber: value.referenceNumber,
        notes: value.notes,
      });
      if (!parsed.success) {
        setError(parsed.error.issues[0]?.message || "Unable to create consultation fee bill.");
        return;
      }
      const payment = await collectConsultationFee(auth.accessToken, auth.tenantId, {
        appointmentId: consultationAppointmentId,
        paymentMode: parsed.data.paymentMethod,
        referenceNumber: parsed.data.invoiceNumber || null,
        notes: parsed.data.notes || null,
      });
      await loadBills();
      await refreshSelectedBill(payment.billId);
      setConsultationFeeOpen(false);
      setSuccess("Payment collected successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create consultation fee bill.");
    } finally {
      setSaving(false);
    }
  };

  const submitPendingConsultationFee = async (value: ConsultationFeeDialogValue) => {
    if (!auth.accessToken || !auth.tenantId || !pendingConsultationFeeDialog) {
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const parsed = consultationFeeSchema.safeParse({
        amount: Number(pendingConsultationFeeDialog.dueAmount || 0),
        paymentMethod: value.paymentMode,
        invoiceNumber: value.referenceNumber,
        notes: value.notes,
      });
      if (!parsed.success) {
        setError(parsed.error.issues[0]?.message || "Unable to collect pending consultation fee.");
        return;
      }
      const payment = await collectConsultationFee(auth.accessToken, auth.tenantId, {
        appointmentId: pendingConsultationFeeDialog.appointmentId,
        paymentMode: parsed.data.paymentMethod,
        referenceNumber: parsed.data.invoiceNumber || null,
        notes: parsed.data.notes || null,
      });
      await loadBills();
      await loadPatientBills(form.patientId);
      await loadPatientBillingContext(form.patientId);
      await refreshSelectedBill(payment.billId);
      setPendingConsultationFeeOpen(false);
      setPendingConsultationFeeDialog(null);
      setSuccess("Payment collected successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to collect pending consultation fee.");
    } finally {
      setSaving(false);
    }
  };

  const issueCurrentBill = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try { const updated = await issueBill(auth.accessToken, auth.tenantId, bill.id); await refreshSelectedBill(updated.id); setSuccess("Bill issued"); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to issue bill"); }
    finally { setWorkingId(null); }
  };

  const cancelCurrentBill = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try { const updated = await cancelBill(auth.accessToken, auth.tenantId, bill.id); await refreshSelectedBill(updated.id); setSuccess("Bill cancelled"); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to cancel bill"); }
    finally { setWorkingId(null); }
  };

  const openInvoicePdf = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try { const { blob } = await getBillPdf(auth.accessToken, auth.tenantId, bill.id); const url = URL.createObjectURL(blob); window.open(url, "_blank", "noopener,noreferrer"); window.setTimeout(() => URL.revokeObjectURL(url), 60000); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to open bill PDF"); }
  };

  const openInvoicePreviewAction = async (bill: Bill, autoPrint = false) => {
    await loadInvoicePreview(bill, autoPrint);
  };

  const openReceiptPdf = async (receipt: Receipt) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try { const { blob } = await getReceiptPdf(auth.accessToken, auth.tenantId, receipt.id); const url = URL.createObjectURL(blob); window.open(url, "_blank", "noopener,noreferrer"); window.setTimeout(() => URL.revokeObjectURL(url), 60000); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to open receipt PDF"); }
  };

  const openReceiptPreviewAction = async (receipt: Receipt, payment: Payment | null, autoPrint = false) => {
    await loadReceiptPreview(receipt, payment, autoPrint);
  };

  const sendReceiptAction = async (receipt: Receipt, channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(receipt.id);
    try { await sendReceipt(auth.accessToken, auth.tenantId, receipt.id, channel); setSuccess(`Receipt sent via ${channel}`); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to send receipt"); }
    finally { setWorkingId(null); }
  };

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;

  const patchBillLine = (index: number, patch: Partial<BillLineForm>) => {
    setForm((current) => {
      const lines = current.lines.slice();
      lines[index] = { ...lines[index], ...patch };
      return { ...current, lines };
    });
  };

  const removeBillLine = (index: number) => {
    setForm((current) => (current.lines.length === 1
      ? current
      : { ...current, lines: current.lines.filter((_, lineIndex) => lineIndex !== index) }));
  };

  const addBillLine = (preset?: Partial<BillLineForm> & { itemType?: BillItemCategory }) => {
    setForm((current) => ({
      ...current,
      lines: [
        ...current.lines,
        {
          itemType: preset?.itemType || "OTHER",
          itemName: preset?.itemName || "",
          quantity: "1",
          unitPrice: preset?.unitPrice || "",
          lineDiscountAmount: preset?.lineDiscountAmount || "",
          taxAmount: preset?.taxAmount || "",
          referenceId: preset?.referenceId || "",
          scanCode: preset?.scanCode || "",
          sortOrder: String(current.lines.length + 1),
        },
      ],
    }));
  };

  const loadMedicineCatalog = React.useCallback(async (query: string) => {
    if (!auth.accessToken || !auth.tenantId) return [] as CatalogItem[];
    const trimmed = query.trim();
    if (!trimmed) return [] as CatalogItem[];
    try {
      const [stockRows, medicineRows] = await Promise.all([
        searchStocks(auth.accessToken, auth.tenantId, trimmed),
        canReadMedicineMaster ? searchMedicines(auth.accessToken, auth.tenantId, trimmed) : Promise.resolve([] as Medicine[]),
      ]);
      return mergeMedicineCatalog(stockRows, medicineRows).slice(0, 12);
    } catch {
      return [] as CatalogItem[];
    }
  }, [auth.accessToken, auth.tenantId, canReadMedicineMaster]);

  const addPresetLine = (preset: QuickChargePreset) => {
    addBillLine({
      itemType: preset.itemType,
      itemName: preset.itemName,
      unitPrice: preset.unitPrice || "",
    });
  };

  const commitChargeSuggestion = (item: CatalogItem, scanCode?: string) => {
    const query = scanCode || item.itemName;
    const normalized = normalizeDraftText(query);
    const currentMatchIndex = form.lines.findIndex((row) => {
      const rowName = normalizeDraftText(row.itemName);
      const rowScan = normalizeDraftText(row.scanCode);
      const rowReference = normalizeDraftText(row.referenceId);
      return rowName === normalized || rowScan === normalized || rowReference === normalized;
    });
    if (currentMatchIndex >= 0) {
      patchBillLine(currentMatchIndex, {
        quantity: String(Number(form.lines[currentMatchIndex].quantity || "1") + 1),
        scanCode: query,
      });
    } else {
      addBillLine({
        itemType: item.itemType,
        itemName: item.itemName,
        unitPrice: item.unitPrice,
        referenceId: item.referenceId || undefined,
        scanCode: query,
      });
    }
    setSuccess("Item added");
    setManualScanPrompt(null);
    setScanQuery("");
    setChargeHighlightIndex(0);
    window.setTimeout(() => scanInputRef.current?.focus(), 0);
  };

  const addManualScanItem = () => {
    const value = manualScanPrompt || scanQuery.trim();
    if (!value) return;
    addBillLine({
      itemType: scanItemType === "ALL" ? "OTHER" : scanItemType,
      itemName: value,
      scanCode: value,
    });
    setScanQuery("");
    setManualScanPrompt(null);
    setChargeHighlightIndex(0);
    window.setTimeout(() => scanInputRef.current?.focus(), 0);
  };

  const addScannedItem = async () => {
    const query = scanQuery.trim();
    if (!query) return;
    let suggestions = chargeSuggestions;
    if (suggestions.length === 0 && (scanItemType === "MEDICINE" || scanItemType === "ALL")) {
      const liveCatalog = medicineCatalog.length > 0 ? medicineCatalog : await loadMedicineCatalog(query);
      suggestions = liveCatalog.length > 0
        ? liveCatalog.filter((item) => scanItemType === "ALL" || item.itemType === scanItemType)
        : suggestions;
    }
    const normalized = normalizeDraftText(query);
    const catalogMatch = suggestions.find((item) => normalizeDraftText(item.itemName) === normalized)
      || suggestions.find((item) => normalizeDraftText(item.itemName).includes(normalized) || normalizeDraftText(item.itemType) === normalized)
      || (suggestions[0] || null);
    if (catalogMatch) {
      commitChargeSuggestion(catalogMatch, query);
      return;
    }
    setManualScanPrompt(query);
  };

  const createBillFromDraft = async (collectPayment = false) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setError(null);
    setSuccess(null);
    const source = form.appointmentId.trim()
      ? "APPOINTMENT"
      : form.consultationId.trim()
        ? "CONSULTATION"
        : "MANUAL_BILLING";
    const parsed = validateBillingDraft(form, source);
    if (!parsed.success) {
      setBillFieldErrors(parsed.fieldErrors);
      setError(Object.values(parsed.fieldErrors)[0] || "Failed to create bill");
      window.setTimeout(() => {
        const firstField = getFirstBillingInvalidField(parsed.fieldErrors);
        if (firstField === "patientId") patientSearchRef.current?.focus();
        if (firstField === "billDate") billDateInputRef.current?.focus();
        if (firstField === "discountReason") discountReasonInputRef.current?.focus();
        if (firstField && firstField.startsWith("lines.")) scanInputRef.current?.focus();
      }, 0);
      return;
    }
    setBillFieldErrors({});
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await createBill(auth.accessToken, auth.tenantId, toBillInput(form));
      setSuccess("Bill created");
      setForm({
        ...emptyBillForm(),
        patientId: form.patientId,
        consultationId: form.consultationId,
        appointmentId: form.appointmentId,
      });
      setScanQuery("");
      setManualScanPrompt(null);
      window.setTimeout(() => scanInputRef.current?.focus(), 50);
      await loadBills();
      await loadPatientBills(saved.patientId);
      await selectBill(saved);
      if (collectPayment) {
        setPaymentForm({ ...emptyPaymentForm(), amount: saved.dueAmount.toFixed(2) });
        setPaymentOpen(true);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create bill");
    } finally {
      setSaving(false);
    }
  };

  const openLedgerActions = (bill: Bill, anchorEl: HTMLElement) => {
    setLedgerActionBill(bill);
    setLedgerActionAnchorEl(anchorEl);
  };

  const closeLedgerActions = () => {
    setLedgerActionBill(null);
    setLedgerActionAnchorEl(null);
  };

  const ledgerBillIsSettled = ledgerActionBill ? billCountsAsSettled(ledgerActionBill) : false;
  const ledgerMenuItems = ledgerActionBill ? [
    <MenuItem key="view" onClick={() => { void selectBill(ledgerActionBill); closeLedgerActions(); }}>View Bill</MenuItem>,
    ledgerBillIsSettled
      ? <MenuItem key="receipt" onClick={() => { void openBillReceiptPreview(ledgerActionBill); closeLedgerActions(); }}>View Receipt</MenuItem>
      : <MenuItem key="invoice" onClick={() => { void openInvoicePreviewAction(ledgerActionBill); closeLedgerActions(); }}>View Invoice</MenuItem>,
    ledgerBillIsSettled
      ? <MenuItem key="print-receipt" onClick={() => { void openBillReceiptPreview(ledgerActionBill, true); closeLedgerActions(); }}>Print Receipt</MenuItem>
      : <MenuItem key="print" onClick={() => { void openInvoicePreviewAction(ledgerActionBill, true); closeLedgerActions(); }}>Print Invoice</MenuItem>,
    ledgerBillIsSettled
      ? <MenuItem key="receipt-pdf" onClick={() => { void openBillReceiptPdf(ledgerActionBill); closeLedgerActions(); }}>Download Receipt PDF</MenuItem>
      : <MenuItem key="pdf" onClick={() => { void openInvoicePdf(ledgerActionBill); closeLedgerActions(); }}>Download PDF</MenuItem>,
    canCollectPayment ? <MenuItem key="pay" disabled={!billHasCollectableDue(ledgerActionBill)} onClick={() => { openPaymentDialog(ledgerActionBill); closeLedgerActions(); }}>Add payment</MenuItem> : null,
    canRefund ? <MenuItem key="refund" disabled={billRefundableAmount(ledgerActionBill) <= 0} onClick={() => { openRefundDialog(ledgerActionBill); closeLedgerActions(); }}>Refund</MenuItem> : null,
    canSendInvoice && !ledgerBillIsSettled ? <MenuItem key="send" disabled={workingId === ledgerActionBill.id || !ledgerActionBill.patientId} onClick={() => { void sendInvoiceAction(ledgerActionBill); closeLedgerActions(); }}>Send invoice email</MenuItem> : null,
    canSendReceipt && ledgerBillIsSettled ? <MenuItem key="send-receipt" onClick={() => { void sendBillReceiptEmail(ledgerActionBill); closeLedgerActions(); }}>Send receipt email</MenuItem> : null,
    canUpdateBill ? <MenuItem key="issue" disabled={workingId === ledgerActionBill.id || ledgerActionBill.status !== "DRAFT"} onClick={() => { void issueCurrentBill(ledgerActionBill); closeLedgerActions(); }}>Issue</MenuItem> : null,
    canCancelBill ? <MenuItem key="cancel" disabled={workingId === ledgerActionBill.id || ledgerActionBill.status === "CANCELLED" || ledgerActionBill.status === "REFUND_PENDING" || ledgerActionBill.status === "REFUNDED" || ledgerActionBill.status === "CANCELLED_REFUNDED"} onClick={() => { void cancelCurrentBill(ledgerActionBill); closeLedgerActions(); }}>Cancel</MenuItem> : null,
  ].filter(Boolean) : null;
  const consultationAppointmentDisplay = consultationAppointmentLabel ? `Appointment: ${consultationAppointmentLabel}` : "Appointment: —";
  const consultationDoctorDisplay = consultationDoctorLabel ? `Doctor: ${consultationDoctorLabel}` : "Doctor: —";
  const consultationPatientDisplay = consultationPatientLabel ? `Patient: ${consultationPatientLabel}` : "Patient: —";
  const consultationFeeDisplay = `Consultation fee: ${formatAmount(resolvedConsultationFee)}`;
  let consultationFeeDialog: React.ReactNode = null;
  if (consultationFeeRequested && resolvedConsultationFee != null) {
    consultationFeeDialog = (
      <ConsultationFeeDialog
        open={consultationFeeOpen}
        title="Collect consultation fee"
        reasonLabel={consultationReason}
        appointmentLabel={consultationAppointmentDisplay}
        doctorLabel={consultationDoctorDisplay}
        patientLabel={consultationPatientDisplay}
        feeLabel={consultationFeeDisplay}
        submitLabel="Collect Fee"
        onClose={() => setConsultationFeeOpen(false)}
        onSubmit={submitConsultationFee}
      />
    );
  }
  let pendingConsultationFeeDialogNode: React.ReactNode = null;
  if (pendingConsultationFeeOpen && pendingConsultationFeeDialog) {
    const appointmentLabel = formatPendingConsultationFeeSummary(pendingConsultationFeeDialog);
    const doctorLabel = pendingConsultationFeeDialog.doctorName || "Doctor";
    const patientLabel = selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName}`.trim() : "Patient";
    const feeLabel = `Consultation fee: ${formatAmount(pendingConsultationFeeDialog.dueAmount)}`;
    pendingConsultationFeeDialogNode = (
      <ConsultationFeeDialog
        open={pendingConsultationFeeOpen}
        title="Collect pending consultation fee"
        reasonLabel={pendingConsultationFeeDialog.paymentBypassReason || "Consultation Fee"}
        appointmentLabel={appointmentLabel}
        doctorLabel={doctorLabel}
        patientLabel={patientLabel}
        feeLabel={feeLabel}
        submitLabel="Collect Fee"
        onClose={() => {
          setPendingConsultationFeeOpen(false);
          setPendingConsultationFeeDialog(null);
        }}
        onSubmit={submitPendingConsultationFee}
      />
    );
  }

  return (
    <Box>
      <Stack className="no-print" spacing={3}>
        <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Billing</Typography>
            <Typography variant="body2" color="text.secondary">Split-screen cashier workspace with scan-first item entry.</Typography>
          </Box>
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" onClick={() => void loadBills()}>Refresh</Button>
            <Button variant="text" onClick={() => scanInputRef.current?.focus()}>Focus scan</Button>
          </Stack>
        </Box>

        {error ? <Alert severity="error">{error}</Alert> : null}
        {success ? <Alert severity="success">{success}</Alert> : null}

        {consultationAppointmentId ? (
          <Card variant="outlined">
            <CardContent sx={{ p: 1.5 }}>
              <Stack spacing={1.25}>
                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Consultation fee collection</Typography>
                    <Typography variant="body2" color="text.secondary">Payment reason: Consultation Fee</Typography>
                  </Box>
                  {consultationReturnTo ? (
                    <Button variant="outlined" disabled={!success} onClick={() => navigate(consultationReturnTo)}>Return to Queue</Button>
                  ) : null}
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {consultationAppointmentLabel ? <Chip size="small" label={consultationAppointmentLabel} variant="outlined" /> : null}
                  {consultationPatientLabel ? <Chip size="small" label={`Patient: ${consultationPatientLabel}`} variant="outlined" /> : null}
                  {consultationDoctorLabel ? <Chip size="small" label={`Doctor: ${consultationDoctorLabel}`} variant="outlined" /> : null}
                  {consultationContextLoading
                    ? <Chip size="small" label="Consultation fee: loading…" variant="outlined" />
                    : resolvedConsultationFee != null
                      ? <Chip size="small" label={`Consultation fee: ${formatAmount(resolvedConsultationFee)}`} color="warning" variant="outlined" />
                      : <Chip size="small" label="Consultation fee missing" color="default" variant="outlined" />}
                </Stack>
                {consultationContextError ? <Alert severity="warning">{consultationContextError}</Alert> : null}
                {consultationExistingBill ? (
                  <Alert severity={(consultationPaymentState.due ?? 0) > 0 ? "warning" : "success"}>
                    {(consultationPaymentState.due ?? 0) > 0
                      ? `Already billed. Pending due ${formatAmount(consultationPaymentState.due)}.`
                      : "Consultation fee already paid for this appointment."}
                  </Alert>
                ) : null}
                {consultationContextLoading ? (
                  <Alert severity="info">Loading appointment billing context…</Alert>
                ) : resolvedConsultationFee != null ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button variant="contained" onClick={() => {
                      if (consultationPaymentState.paid) {
                        if (consultationExistingBill) {
                          void selectBill(consultationExistingBill);
                          void openBillReceiptPreview(consultationExistingBill);
                        }
                        return;
                      }
                      if (consultationExistingBill) {
                        openPaymentDialog(consultationExistingBill);
                        return;
                      }
                      setConsultationFeeOpen(true);
                    }}>
                      {consultationExistingBill
                        ? consultationPaymentState.paid ? "View receipt" : "Collect remaining payment"
                        : "Collect consultation fee"}
                    </Button>
                    {!consultationExistingBill ? (
                      <Button variant="outlined" onClick={() => { prepareConsultationDraft(); }}>
                        {consultationDraftReady ? "Open draft" : "Prepare draft"}
                      </Button>
                    ) : null}
                    {!consultationExistingBill && consultationDraftReady ? <Chip size="small" label="Draft ready" color="success" variant="outlined" /> : null}
                    {consultationExistingBill ? (
                      <Chip
                        size="small"
                        label={consultationPaymentState.paid ? "Already paid" : "Already billed"}
                        color={consultationPaymentState.paid ? "success" : "warning"}
                        variant="outlined"
                      />
                    ) : (
                      <Chip size="small" label="Fee pending" color="warning" variant="outlined" />
                    )}
                  </Stack>
                ) : (
                  <Alert severity="warning">Doctor consultation fee is not configured. Open the doctor profile to configure the fee before collecting payment.</Alert>
                )}
                {consultationDoctorUserProfileId ? (
                  <Button variant="text" onClick={() => navigate(`/doctors/${consultationDoctorUserProfileId}`)}>Open doctor profile</Button>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        ) : null}

        <Grid container spacing={2} alignItems="stretch">
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card variant="outlined" sx={{ height: "100%" }}>
              <CardContent sx={{ p: 1.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
                <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
                  <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: 0.7 }}>Workflow</Typography>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" alignItems="center">
                    {BILLING_WORKFLOW_STEPS.map((step, index) => {
                      const active = billingWorkflowStep === step.key;
                      return (
                        <React.Fragment key={step.key}>
                          <Chip
                            size="small"
                            label={step.label}
                            color={active ? "primary" : "default"}
                            variant={active ? "filled" : "outlined"}
                            sx={{ fontWeight: active ? 800 : 600 }}
                          />
                          {index < BILLING_WORKFLOW_STEPS.length - 1 ? (
                            <Typography variant="body2" color="text.secondary" sx={{ mx: 0.25 }}>→</Typography>
                          ) : null}
                        </React.Fragment>
                      );
                    })}
                  </Stack>
                </Box>

                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "flex-start" }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Cashier Workspace</Typography>
                    <Typography variant="body2" color="text.secondary">Select a patient, add visit charges, review totals, and collect payment.</Typography>
                  </Box>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" justifyContent="flex-end">
                    <Chip size="small" label={form.patientId ? (selectedPatient?.patientNumber || selectedPatient?.mobile || "Selected patient") : "No patient selected"} variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`Lines: ${form.lines.filter((row) => row.itemName.trim()).length}`} variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`Draft total: ${formatAmount(currentDraftTotals.total)}`} color="warning" variant="outlined" sx={compactChipSx} />
                  </Stack>
                </Box>

                <Box sx={{ display: "flex", flexDirection: "column", gap: 1.25 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Patient</Typography>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        {...denseTextFieldProps}
                        inputRef={patientSearchRef}
                        label={<RequiredLabel text="Patient" required />}
                        value={patientQuery}
                        onChange={(e) => setPatientQuery(e.target.value)}
                        helperText="Search by patient number, mobile, or name"
                      />
                      {patientSearchResults.length > 0 && !form.patientId ? (
                        <Card variant="outlined" sx={{ mt: 1, borderRadius: 2 }}>
                          <List dense disablePadding>
                            {patientSearchResults.map((patient) => (
                              <ListItemButton key={patient.id} onClick={() => setForm((current) => ({ ...current, patientId: patient.id }))}>
                                <ListItemText
                                  primary={`${patient.firstName} ${patient.lastName}`.trim()}
                                  secondary={`${patient.patientNumber} • ${patient.mobile}`}
                                />
                              </ListItemButton>
                            ))}
                          </List>
                        </Card>
                      ) : null}
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      {selectedPatient ? (
                        <Card variant="outlined" sx={{ height: "100%" }}>
                          <CardContent sx={{ p: 1.25 }}>
                            <Stack spacing={0.75}>
                              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start" }}>
                                <Box>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{`${selectedPatient.firstName} ${selectedPatient.lastName}`.trim()}</Typography>
                                  <Typography variant="body2" color="text.secondary">{selectedPatient.patientNumber} • {selectedPatient.mobile}</Typography>
                                </Box>
                                <Button size="small" variant="text" onClick={() => setForm((current) => ({ ...current, patientId: "", consultationId: "", appointmentId: "" }))}>Clear</Button>
                              </Box>
                              <Stack direction="row" spacing={0.75} flexWrap="wrap">
                                <Chip size="small" label={`Due: ${formatAmount(selectedPatientTotalDue)}`} color="warning" variant="outlined" sx={compactChipSx} />
                                {consultationAppointmentLabel ? <Chip size="small" label={consultationAppointmentLabel} variant="outlined" sx={compactChipSx} /> : null}
                                {consultationDoctorLabel ? <Chip size="small" label={consultationDoctorLabel} variant="outlined" sx={compactChipSx} /> : null}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      ) : (
                        <CompactEmptyState title="Search or select a patient to start billing." subtitle="Patient summary and due context appear here once a patient is selected." />
                      )}
                    </Grid>
                  </Grid>
                </Box>

                <Box ref={consultationSectionRef} sx={{ display: "flex", flexDirection: "column", gap: 1.25 }}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Consultation Fee</Typography>
                      <Typography variant="body2" color="text.secondary">Use this when collecting a doctor consultation fee. Fee is populated from doctor configuration.</Typography>
                    </Box>
                    {consultationAppointmentLabel ? <Chip size="small" label={consultationAppointmentLabel} variant="outlined" sx={compactChipSx} /> : null}
                  </Box>
                  <Grid container spacing={1.25} alignItems="stretch">
                    <Grid size={{ xs: 12, md: 5 }}>
                      <FormControl {...denseSelectProps}>
                        <InputLabel id="consultation-doctor-label">Consultant</InputLabel>
                        <Select
                          labelId="consultation-doctor-label"
                          inputRef={consultationDoctorSelectRef}
                          label="Consultant"
                          value={consultationDoctorUserIdForDraft}
                          onChange={(e) => setConsultationDoctorUserIdForDraft(String(e.target.value))}
                        >
                          {doctorOptions.map((doctor) => (
                            <MenuItem key={doctor.appUserId} value={doctor.appUserId}>{doctor.displayName}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField
                        {...denseTextFieldProps}
                        label="Configured fee"
                        value={consultationFeeQuickAmount != null ? formatAmount(consultationFeeQuickAmount) : "Fee unavailable"}
                        InputProps={{ readOnly: true }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Button
                        fullWidth
                        variant="contained"
                        sx={{ height: "100%" }}
                        onClick={() => {
                          prepareConsultationDraft();
                        }}
                        disabled={!consultationDoctorUserIdForDraft || consultationExistingBill != null || consultationDraftHasLine}
                      >
                        {consultationExistingBill ? "Already billed" : consultationDraftHasLine ? "Added to current bill" : "Add Consultation Fee"}
                      </Button>
                    </Grid>
                  </Grid>
                  {consultationDraftHasLine ? (
                    <Alert severity="success" sx={{ py: 0.5 }}>Consultation fee added to current bill.</Alert>
                  ) : null}
                  {consultationAppointment ? (
                    <Alert severity="info" sx={{ py: 0.5 }}>
                      {consultationAppointment.patientName || consultationAppointment.patientNumber || "Patient"} • {consultationAppointmentLabel}
                    </Alert>
                  ) : null}
                  {consultationCatalogHint.length > 0 ? (
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      {consultationCatalogHint.map((hint) => (
                        <Chip
                          key={hint.doctorUserId}
                          label={hint.title}
                          size="small"
                          variant="outlined"
                          sx={compactChipSx}
                          onClick={() => {
                            setConsultationDoctorUserIdForDraft(hint.doctorUserId);
                            consultationDoctorSelectRef.current?.focus();
                          }}
                        />
                      ))}
                    </Stack>
                  ) : null}
                </Box>

                <Box sx={{ display: "flex", flexDirection: "column", gap: 1.25 }}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Add Other Charges</Typography>
                      <Typography variant="body2" color="text.secondary">Search or scan anything, then add it directly to the current bill.</Typography>
                    </Box>
                  </Box>
                  <Box sx={{ position: "relative" }}>
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems="stretch">
                      <Box sx={{ flex: 1, position: "relative" }}>
                        <TextField
                          {...denseTextFieldProps}
                          inputRef={scanInputRef}
                          label="Search or scan anything"
                          placeholder="Search medicine, lab test, service, procedure, package, barcode"
                          value={scanQuery}
                          onFocus={() => setChargeSearchFocused(true)}
                          onBlur={() => {
                            window.setTimeout(() => {
                              setChargeSearchFocused(false);
                              setChargeHighlightIndex(0);
                            }, 120);
                          }}
                          onChange={(e) => {
                            setScanQuery(e.target.value);
                            setChargeSearchFocused(true);
                            setManualScanPrompt(null);
                            setChargeHighlightIndex(0);
                          }}
                          onKeyDown={(e) => {
                            if (e.key === "ArrowDown" && chargeSuggestions.length > 0) {
                              e.preventDefault();
                              setChargeHighlightIndex((current) => Math.min(current + 1, chargeSuggestions.length - 1));
                              return;
                            }
                            if (e.key === "ArrowUp" && chargeSuggestions.length > 0) {
                              e.preventDefault();
                              setChargeHighlightIndex((current) => Math.max(current - 1, 0));
                              return;
                            }
                            if (e.key === "Escape") {
                              setChargeSearchFocused(false);
                              setChargeHighlightIndex(0);
                              return;
                            }
                            if (e.key === "Enter") {
                              e.preventDefault();
                              const activeSuggestion = chargeSuggestions[chargeHighlightIndex] || chargeSuggestions[0];
                              if (activeSuggestion) {
                                commitChargeSuggestion(activeSuggestion, scanQuery.trim());
                              } else {
                                void addScannedItem();
                              }
                            }
                          }}
                          InputProps={{
                            startAdornment: (
                              <InputAdornment position="start">
                                <SearchRoundedIcon fontSize="small" />
                              </InputAdornment>
                            ),
                          }}
                        />
                        {chargeSuggestions.length > 0 && chargeSearchFocused ? (
                          <Paper
                            variant="outlined"
                            sx={{
                              position: "absolute",
                              top: "calc(100% + 6px)",
                              left: 0,
                              right: 0,
                              zIndex: 4,
                              borderRadius: 2,
                              overflow: "hidden",
                              boxShadow: (theme) => theme.shadows[4],
                              maxHeight: 360,
                            }}
                          >
                            <Stack sx={{ maxHeight: 360, overflow: "auto" }} divider={<Box sx={{ borderTop: "1px solid", borderColor: "divider" }} />}>
                              {chargeSuggestionGroups.map((group) => (
                                <Box key={group.itemType} sx={{ py: 0.75 }}>
                                  <Typography variant="caption" color="text.secondary" sx={{ px: 1.25, display: "block", fontWeight: 700 }}>
                                    {group.label}
                                  </Typography>
                                  <Stack>
                                    {group.items.map((item) => {
                                      const suggestionIndex = chargeSuggestions.findIndex((entry) => entry.itemName === item.itemName && entry.itemType === item.itemType && entry.unitPrice === item.unitPrice && entry.referenceId === item.referenceId);
                                      const active = suggestionIndex === chargeHighlightIndex;
                                      return (
                                        <Button
                                          key={`${item.itemName}-${item.itemType}-${item.unitPrice}-${item.referenceId || ""}`}
                                          fullWidth
                                          variant="text"
                                          onMouseEnter={() => setChargeHighlightIndex(suggestionIndex)}
                                          onMouseDown={(event) => {
                                            event.preventDefault();
                                            commitChargeSuggestion(item, scanQuery.trim());
                                          }}
                                          sx={{
                                            justifyContent: "space-between",
                                            textAlign: "left",
                                            px: 1.25,
                                            py: 0.85,
                                            borderRadius: 0,
                                            bgcolor: active ? "action.selected" : "transparent",
                                          }}
                                        >
                                          <Box sx={{ minWidth: 0, flex: 1, pr: 1 }}>
                                            <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>
                                              {item.itemName}
                                            </Typography>
                                          </Box>
                                          <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flexShrink: 0 }}>
                                            <Chip size="small" label={chargeSuggestionTypeLabel(item.itemType)} variant="outlined" sx={compactChipSx} />
                                            <Typography variant="body2" sx={{ fontWeight: 800, whiteSpace: "nowrap" }}>
                                              {item.unitPrice ? formatAmount(Number(item.unitPrice)) : "—"}
                                            </Typography>
                                          </Stack>
                                        </Button>
                                      );
                                    })}
                                  </Stack>
                                </Box>
                              ))}
                            </Stack>
                          </Paper>
                        ) : null}
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="stretch" sx={{ flexShrink: 0 }}>
                        <FormControl {...denseSelectProps} sx={{ minWidth: 132, maxWidth: 160 }}>
                          <InputLabel id="scan-item-type-label">Type</InputLabel>
                          <Select
                            labelId="scan-item-type-label"
                            label="Type"
                            value={scanItemType}
                            onChange={(e) => setScanItemType(e.target.value as ChargeSearchType)}
                          >
                            {SCAN_ITEM_TYPE_OPTIONS.map((option) => (
                              <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                        {scanQuery.trim() ? (
                          <Button size="small" variant="outlined" onMouseDown={(event) => event.preventDefault()} onClick={() => { void addScannedItem(); }}>
                            Add
                          </Button>
                        ) : null}
                      </Stack>
                    </Stack>
                    {manualScanPrompt ? (
                      <Alert
                        severity="warning"
                        action={(
                          <Button color="inherit" size="small" onClick={addManualScanItem}>
                            Add as manual item
                          </Button>
                        )}
                        sx={{ mt: 1 }}
                      >
                        No matching charge found.
                      </Alert>
                    ) : null}
                  </Box>
                </Box>

                <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Frequently Used</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {QUICK_CHARGE_PRESETS.map((preset) => (
                      <Button
                        key={preset.label}
                        size="small"
                        variant="outlined"
                        onClick={() => {
                          if (preset.itemType === "CONSULTATION") {
                            consultationSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
                            consultationDoctorSelectRef.current?.focus();
                            return;
                          }
                          addPresetLine(preset);
                        }}
                      >
                        {preset.label}
                      </Button>
                    ))}
                  </Stack>
                </Box>

                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Manual Line Items</Typography>
                    <Typography variant="body2" color="text.secondary">Use this for custom entries when presets are not enough.</Typography>
                  </Box>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<AddRoundedIcon />}
                      onClick={() => {
                        if (!manualLineItemsOpen) {
                          setManualLineItemsOpen(true);
                          addBillLine();
                          return;
                        }
                        addBillLine();
                      }}
                    >
                      {manualLineItemsOpen ? "Add line" : "Add manual line item"}
                    </Button>
                  </Stack>
                </Box>
                <Collapse in={manualLineItemsOpen} timeout="auto" unmountOnExit={false}>
                  <Box sx={{ display: "flex", flexDirection: "column", gap: 0.75 }}>
                    <CompactTableFrame maxHeight={420}>
                      <Table size="small" stickyHeader sx={{ width: "100%", minWidth: 860, tableLayout: "fixed", "& .MuiTableCell-root": { py: 0.75, px: 0.75, verticalAlign: "top" } }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ width: "28%" }}>Item</TableCell>
                          <TableCell sx={{ width: "13%" }}>Type</TableCell>
                          <TableCell sx={{ width: "8%" }} align="right">Qty</TableCell>
                          <TableCell sx={{ width: "12%" }} align="right">Unit</TableCell>
                          <TableCell sx={{ width: "12%" }} align="right">Discount</TableCell>
                          <TableCell sx={{ width: "10%" }} align="right">Tax</TableCell>
                          <TableCell sx={{ width: "11%" }} align="right">Total</TableCell>
                          <TableCell sx={{ width: "6%" }} align="right">Remove</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {form.lines.map((row, index) => (
                          <TableRow key={`${index}-${row.sortOrder}`} hover>
                            {(() => {
                              const itemError = billFieldErrors[`lines.${index}.item`];
                              const typeError = billFieldErrors[`lines.${index}.type`];
                              const quantityError = billFieldErrors[`lines.${index}.quantity`];
                              const unitError = billFieldErrors[`lines.${index}.unit`];
                              const discountError = billFieldErrors[`lines.${index}.discount`];
                              const taxError = billFieldErrors[`lines.${index}.tax`];
                              return (
                                <>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <TextField size="small" label={<RequiredLabel text="Item" required />} value={row.itemName} onChange={(e) => patchBillLine(index, { itemName: e.target.value })} fullWidth error={Boolean(itemError)} helperText={itemError || "Required, max 100 characters."} />
                              </Stack>
                            </TableCell>
                            <TableCell>
                              <FormControl fullWidth size="small">
                                <InputLabel><RequiredLabel text="Type" required /></InputLabel>
                                <Select label="Type" value={row.itemType} error={Boolean(typeError)} onChange={(e) => patchBillLine(index, { itemType: String(e.target.value) as BillItemCategory })}>
                                  {BILL_ITEM_CATEGORIES.map((option) => <MenuItem key={option} value={option}>{billItemCategoryLabel(option)}</MenuItem>)}
                                </Select>
                                {typeError ? <Typography variant="caption" color="error">{typeError}</Typography> : null}
                              </FormControl>
                            </TableCell>
                            <TableCell align="right">
                              <TextField size="small" label={<RequiredLabel text="Qty" required />} fullWidth type="number" value={row.quantity} onChange={(e) => patchBillLine(index, { quantity: e.target.value })} error={Boolean(quantityError)} helperText={quantityError || "Whole number greater than zero."} />
                            </TableCell>
                            <TableCell align="right">
                              <TextField size="small" label={<RequiredLabel text="Unit" required />} fullWidth type="number" value={row.unitPrice} onChange={(e) => patchBillLine(index, { unitPrice: e.target.value })} error={Boolean(unitError)} helperText={unitError || "Zero or greater, up to 2 decimals."} />
                            </TableCell>
                            <TableCell align="right">
                              <TextField size="small" fullWidth type="number" value={row.lineDiscountAmount} onChange={(e) => patchBillLine(index, { lineDiscountAmount: e.target.value })} error={Boolean(discountError)} helperText={discountError || "Optional discount for this line."} />
                            </TableCell>
                            <TableCell align="right">
                              <TextField size="small" fullWidth type="number" value={row.taxAmount} onChange={(e) => patchBillLine(index, { taxAmount: e.target.value })} error={Boolean(taxError)} helperText={taxError || "Optional tax for this line."} />
                            </TableCell>
                            <TableCell align="right">
                              <Typography variant="body2" sx={{ fontWeight: 800, whiteSpace: "nowrap" }}>{lineTotal(row)}</Typography>
                            </TableCell>
                            <TableCell align="right">
                              <IconButton size="small" onClick={() => removeBillLine(index)} disabled={form.lines.length === 1}>
                                <DeleteOutlineRoundedIcon fontSize="small" />
                              </IconButton>
                            </TableCell>
                                </>
                              );
                            })()}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CompactTableFrame>
                  </Box>
                </Collapse>

                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>More Billing Options</Typography>
                    <Typography variant="body2" color="text.secondary">Advanced billing fields used for corrections, notes, and source tracking.</Typography>
                  </Box>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => setMoreBillingOptionsOpen((current) => !current)}
                  >
                    {moreBillingOptionsOpen ? "Hide options" : "Show options"}
                  </Button>
                </Box>
                <Collapse in={moreBillingOptionsOpen} timeout="auto" unmountOnExit={false}>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        {...denseTextFieldProps}
                        inputRef={billDateInputRef}
                        type="date"
                        label={<RequiredLabel text="Bill date" required />}
                        value={form.billDate}
                        onChange={(e) => setForm((current) => ({ ...current, billDate: e.target.value }))}
                        InputLabelProps={{ shrink: true }}
                        error={Boolean(billFieldErrors.billDate)}
                        helperText={billFieldErrors.billDate || "Cannot be future dated."}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl {...denseSelectProps}>
                        <InputLabel id="discount-type-label"><RequiredLabel text="Discount type" required /></InputLabel>
                        <Select labelId="discount-type-label" label="Discount type" value={form.discountType} onChange={(e) => setForm((current) => ({ ...current, discountType: e.target.value as DiscountType }))}>
                          {DISCOUNT_TYPES.map((d) => <MenuItem key={d} value={d}>{discountTypeLabel(d)}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        {...denseTextFieldProps}
                        label={<RequiredLabel text={discountLabel} required={discountRequired} />}
                        value={form.discountValue}
                        onChange={(e) => setForm((current) => ({ ...current, discountValue: e.target.value }))}
                        disabled={isNoDiscount}
                        error={Boolean(billFieldErrors.discountValue)}
                        helperText={billFieldErrors.discountValue || "Flat amount or percentage, depending on the selected type."}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField {...denseTextFieldProps} label="Consultation ID" value={form.consultationId} onChange={(e) => setForm((current) => ({ ...current, consultationId: e.target.value }))} error={Boolean(billFieldErrors.consultationId)} helperText={billFieldErrors.consultationId || "Optional, max 60 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField {...denseTextFieldProps} label="Appointment ID" value={form.appointmentId} onChange={(e) => setForm((current) => ({ ...current, appointmentId: e.target.value }))} error={Boolean(billFieldErrors.appointmentId)} helperText={billFieldErrors.appointmentId || "Optional, max 60 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField {...denseTextFieldProps} label={<RequiredLabel text="Source" required />} value={consultationAppointmentId ? "APPOINTMENT" : "MANUAL_BILLING"} InputProps={{ readOnly: true }} />
                    </Grid>
                    <Grid size={12}>
                      <TextField
                        {...denseTextFieldProps}
                        inputRef={discountReasonInputRef}
                        label={<RequiredLabel text="Discount reason" required={discountRequired && Number(form.discountValue || "0") > 0} />}
                        value={form.discountReason}
                        onChange={(e) => setForm((current) => ({ ...current, discountReason: e.target.value }))}
                        disabled={isNoDiscount}
                        error={Boolean(billFieldErrors.discountReason)}
                        helperText={billFieldErrors.discountReason || "Required when discount is greater than zero."}
                      />
                    </Grid>
                    <Grid size={12}>
                      <TextField {...denseTextFieldProps} label="Notes" value={form.notes} onChange={(e) => setForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} />
                    </Grid>
                  </Grid>
                </Collapse>

                <Box sx={{ position: "sticky", bottom: 0, zIndex: 1, mt: 0.5, pt: 1, bgcolor: "background.paper", borderTop: "1px solid", borderColor: "divider" }}>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip size="small" label={`Subtotal: ${formatAmount(currentDraftTotals.subtotal)}`} variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label={`Discount: ${formatAmount(currentDraftTotals.lineDiscount + currentDraftTotals.billDiscount)}`} variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label={`Tax: ${formatAmount(currentDraftTotals.tax)}`} variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label={`Grand total: ${formatAmount(currentDraftTotals.total)}`} color="warning" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label="Paid: ₹0.00" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label={`Due: ${formatAmount(currentDraftTotals.total)}`} color="warning" variant="outlined" sx={compactChipSx} />
                    </Stack>
                    <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                      <Button variant="outlined" size="small" onClick={() => { setForm(emptyBillForm()); setBillFieldErrors({}); setScanQuery(""); setManualScanPrompt(null); }} disabled={saving}>Reset draft</Button>
                      <Button variant="contained" size="small" onClick={() => void createBillFromDraft(false)} disabled={saving || !canCreateBill || currentDraftTotals.total <= 0}>
                        {saving ? "Saving..." : "Create Bill"}
                      </Button>
                      {canCollectPayment ? (
                        <Button variant="outlined" size="small" onClick={() => void createBillFromDraft(true)} disabled={saving || !canCreateBill || currentDraftTotals.total <= 0}>
                          Create &amp; Collect Payment
                        </Button>
                      ) : null}
                      <Button variant="text" size="small" onClick={() => { if (selectedBill) void openInvoicePreviewAction(selectedBill, true); }} disabled={!selectedBill}>
                        Print Invoice
                      </Button>
                    </Stack>
                  </Stack>
                </Box>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, lg: 4 }}>
            <Stack spacing={2}>
              {selectedPatient ? (
                <Card variant="outlined">
                  <CardContent sx={{ p: 1.25 }}>
                    <Stack spacing={1}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start" }}>
                        <Box>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Patient context</Typography>
                          <Typography variant="body2" color="text.secondary">{selectedPatient.patientNumber}</Typography>
                        </Box>
                        <Button size="small" variant="text" onClick={() => navigate(`/patients/${selectedPatient.id}`)}>Open patient</Button>
                      </Box>
                      <Stack direction="row" spacing={0.75} flexWrap="wrap">
                        <Chip size="small" label={selectedPatient.mobile || "No mobile"} variant="outlined" sx={compactChipSx} />
                        <Chip size="small" label={`Due: ${formatAmount(selectedPatientTotalDue)}`} color="warning" variant="outlined" sx={compactChipSx} />
                        <Chip size="small" label={`Bills: ${patientBills.length}`} variant="outlined" sx={compactChipSx} />
                      </Stack>
                      {preferredPatientPaymentBill ? (
                        <Box sx={{ display: "grid", gap: 0.5 }}>
                          <Typography variant="body2"><strong>Current bill:</strong> {preferredPatientPaymentBill.billNumber}</Typography>
                          <Typography variant="body2" color="text.secondary">{preferredPatientPaymentBill.billDate} • Due {formatAmount(billEffectiveDueAmount(preferredPatientPaymentBill))}</Typography>
                        </Box>
                      ) : patientPendingConsultationFees.length > 0 ? (
                        <Box sx={{ display: "grid", gap: 0.5 }}>
                          <Typography variant="body2" color="text.secondary">Pending consultation fee is available for collection.</Typography>
                        </Box>
                      ) : (
                        <Typography variant="body2" color="text.secondary">No previous bills found for this patient.</Typography>
                      )}
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Button size="small" variant="outlined" onClick={() => { if (form.patientId) { setBillFilterPatient(form.patientId); void loadBills({ patientId: form.patientId }); } }}>View bills</Button>
                        {patientPrimaryAction?.kind === "collect" ? (
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openPaymentDialog(patientPrimaryAction.bill)}
                          >
                            Collect payment
                          </Button>
                        ) : patientPrimaryAction?.kind === "receipt" ? (
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => { void openBillReceiptPreview(patientPrimaryAction.bill); }}
                          >
                            View receipt
                          </Button>
                        ) : patientPrimaryAction?.kind === "consultation-fee" ? (
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openPendingConsultationFeeDialog(patientPrimaryAction.fee)}
                          >
                            Collect pending fee
                          </Button>
                        ) : null}
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => {
                            if (patientPrimaryAction?.kind === "collect") {
                              void openInvoicePreviewAction(patientPrimaryAction.bill, true);
                              return;
                            }
                            if (patientPrimaryAction?.kind === "receipt") {
                              void openBillReceiptPreview(patientPrimaryAction.bill, true);
                              return;
                            }
                            if (patientPrimaryAction?.kind === "consultation-fee") {
                              openPendingConsultationFeeDialog(patientPrimaryAction.fee);
                            }
                          }}
                          disabled={!patientPrimaryAction}
                        >
                          {patientPrimaryAction?.kind === "collect"
                            ? "Print last invoice"
                            : patientPrimaryAction?.kind === "consultation-fee"
                              ? "Collect pending fee"
                              : "Print receipt"}
                        </Button>
                      </Stack>
                      {patientPendingConsultationFees.length > 0 ? (
                        <Box>
                          <Typography variant="caption" color="text.secondary">Pending consultation fee</Typography>
                          <Stack spacing={0.75} sx={{ mt: 0.5 }}>
                            {patientPendingConsultationFees.map((fee) => (
                              <Card key={fee.appointmentId} variant="outlined" sx={{ borderRadius: 2 }}>
                                <CardContent sx={{ p: 1 }}>
                                  <Stack spacing={0.75}>
                                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{fee.doctorName || "Doctor"}</Typography>
                                      <Typography variant="body2" color="warning.main" sx={{ fontWeight: 700 }}>{formatAmount(fee.dueAmount)}</Typography>
                                    </Box>
                                    <Typography variant="body2" color="text.secondary">{formatPendingConsultationFeeSummary(fee)}</Typography>
                                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                                      <Chip size="small" label={`Appointment: ${fee.appointmentId.slice(0, 8)}`} variant="outlined" sx={compactChipSx} />
                                      <Chip size="small" label={`Due: ${formatAmount(fee.dueAmount)}`} color="warning" variant="outlined" sx={compactChipSx} />
                                    </Stack>
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      onClick={() => openPendingConsultationFeeDialog(fee)}
                                    >
                                      Collect pending consultation fee
                                    </Button>
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        </Box>
                      ) : null}
                      {patientBills.length > 0 ? (
                        <Box>
                          <Typography variant="caption" color="text.secondary">Recent bills</Typography>
                          <List dense disablePadding>
                            {patientBills.slice(0, 4).map((bill) => (
                              <ListItemButton key={bill.id} onClick={() => void selectBill(bill)} sx={{ borderRadius: 1 }}>
                                <ListItemText
                                  primary={`${bill.billNumber} • ${formatAmount(billEffectiveDueAmount(bill))}`}
                                  secondary={`${bill.billDate} • ${bill.status}`}
                                />
                              </ListItemButton>
                            ))}
                          </List>
                        </Box>
                      ) : null}
                    </Stack>
                  </CardContent>
                </Card>
              ) : (
                <CompactEmptyState title="Search or select a patient to start billing." subtitle="Patient summary, due context, and quick actions appear here once a patient is selected." />
              )}

              {recentPatientPayments.length > 0 ? (
                <Card variant="outlined">
                  <CardContent sx={{ p: 1.25 }}>
                    <Stack spacing={1}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center" }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Recent payments</Typography>
                        <Typography variant="caption" color="text.secondary">{recentPatientPayments.length}</Typography>
                      </Box>
                      <List dense disablePadding>
                        {recentPatientPayments.slice(0, 5).map((payment) => (
                          <ListItemButton
                            key={payment.id}
                            sx={{ borderRadius: 1 }}
                            onClick={() => {
                              if (!payment.receiptId) return;
                              void openReceiptPreviewAction({
                                id: payment.receiptId,
                                tenantId: auth.tenantId || "",
                                receiptNumber: payment.receiptNumber || "",
                                billId: payment.billId,
                                paymentId: payment.id,
                                receiptDate: payment.receiptDate || payment.paymentDate,
                                amount: payment.amount,
                                createdAt: payment.createdAt,
                              }, payment);
                            }}
                          >
                            <ListItemText
                              primary={`${formatAmount(payment.amount)} • ${payment.paymentMode}`}
                              secondary={`${payment.billNumber} • ${payment.paymentDateTime || payment.paymentDate}`}
                            />
                          </ListItemButton>
                        ))}
                      </List>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {selectedBill ? (
                <Card variant="outlined">
                  <CardContent sx={{ p: 1.25 }}>
                    <Stack spacing={1}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start" }}>
                        <Box>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{selectedBill.billNumber}</Typography>
                          <Typography variant="body2" color="text.secondary">{selectedBill.patientName || selectedBill.patientNumber || selectedBill.patientId}</Typography>
                        </Box>
                        <Chip label={selectedBill.status} color={statusColor(selectedBill.status)} sx={compactChipSx} />
                      </Box>
                      <Stack direction="row" spacing={0.75} flexWrap="wrap">
                        <Chip size="small" label={`Due: ${formatAmount(billEffectiveDueAmount(selectedBill))}`} color="warning" variant="outlined" sx={compactChipSx} />
                        <Chip size="small" label={`Net paid: ${formatAmount(billNetPaidAmount(selectedBill))}`} variant="outlined" sx={compactChipSx} />
                        <Chip size="small" label={`Refundable: ${formatAmount(billRefundableAmount(selectedBill))}`} variant="outlined" sx={compactChipSx} />
                        {billRefundedAmount(selectedBill) > 0 ? <Chip size="small" label={`Gross paid: ${formatAmount(selectedBill.paidAmount)}`} variant="outlined" sx={compactChipSx} /> : null}
                        <Chip size="small" label={`Refunded: ${formatAmount(selectedBill.refundedAmount)}`} variant="outlined" sx={compactChipSx} />
                      </Stack>
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 6 }}><Typography variant="body2">Subtotal: {formatAmount(selectedBill.subtotalAmount)}</Typography></Grid>
                        <Grid size={{ xs: 6 }}><Typography variant="body2">Discount: {formatAmount(selectedBill.discountAmount)}</Typography></Grid>
                        <Grid size={{ xs: 6 }}><Typography variant="body2">Tax: {formatAmount(selectedBill.taxAmount)}</Typography></Grid>
                        <Grid size={{ xs: 6 }}><Typography variant="body2">Total: {formatAmount(selectedBill.totalAmount)}</Typography></Grid>
                      </Grid>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {billCountsAsSettled(selectedBill) ? (
                          <>
                            <Button size="small" variant="outlined" onClick={() => void openBillReceiptPreview(selectedBill)}>View Receipt</Button>
                            <Button size="small" variant="outlined" onClick={() => void openBillReceiptPreview(selectedBill, true)}>Print Receipt</Button>
                            <Button size="small" variant="outlined" onClick={() => void openBillReceiptPdf(selectedBill)}>Download Receipt PDF</Button>
                          </>
                        ) : (
                          <>
                            <Button size="small" variant="outlined" onClick={() => void openInvoicePreviewAction(selectedBill)}>View Invoice</Button>
                            <Button size="small" variant="outlined" onClick={() => void openInvoicePreviewAction(selectedBill, true)}>Print Invoice</Button>
                            <Button size="small" variant="outlined" onClick={() => void openInvoicePdf(selectedBill)}>Download PDF</Button>
                          </>
                        )}
                        {canCollectPayment ? <Button size="small" variant="outlined" onClick={() => openPaymentDialog(selectedBill)} disabled={!billHasCollectableDue(selectedBill)}>Add payment</Button> : null}
                        {canRefund ? <Button size="small" variant="outlined" onClick={() => { openRefundDialog(selectedBill); }} disabled={billRefundableAmount(selectedBill) <= 0}>Refund</Button> : null}
                        {canSendInvoice && !billCountsAsSettled(selectedBill) ? <Button size="small" variant="outlined" onClick={() => void sendInvoiceAction(selectedBill)} disabled={workingId === selectedBill.id || !selectedBill.patientId}>Send invoice email</Button> : null}
                        {canUpdateBill ? <Button size="small" variant="outlined" onClick={() => void issueCurrentBill(selectedBill)} disabled={workingId === selectedBill.id || selectedBill.status !== "DRAFT"}>Issue</Button> : null}
                        {canCancelBill ? <Button size="small" variant="outlined" onClick={() => void cancelCurrentBill(selectedBill)} disabled={workingId === selectedBill.id || selectedBill.status === "CANCELLED" || selectedBill.status === "REFUND_PENDING" || selectedBill.status === "REFUNDED" || selectedBill.status === "CANCELLED_REFUNDED"}>Cancel</Button> : null}
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}
            </Stack>
          </Grid>

        </Grid>

        <Card variant="outlined">
          <CardContent sx={{ p: 1.25 }}>
            <Stack spacing={1}>
              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start", flexWrap: "wrap" }}>
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Bill Ledger</Typography>
                    <Chip size="small" label={`Bills ${ledgerSummary.visible}`} variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`Paid ${ledgerSummary.paid}`} color="success" variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`Pending ${ledgerSummary.pending}`} color="warning" variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`Due ${formatAmount(ledgerSummary.dueTotal)}`} color="warning" variant="outlined" sx={compactChipSx} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">Compact bill and receipt ledger with payment, refund, invoice, and receipt actions.</Typography>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Button size="small" variant="outlined" onClick={() => setLedgerCollapsed((current) => !current)}>
                    {ledgerCollapsed ? "Expand" : "Collapse"}
                  </Button>
                  {canRefund ? <Button size="small" variant="outlined" onClick={() => openRefundDialog()}>Find bill to refund</Button> : null}
                  <Button size="small" variant="outlined" onClick={() => void loadBills()}>Refresh</Button>
                </Stack>
              </Box>
              <Collapse in={!ledgerCollapsed} timeout="auto" unmountOnExit={false}>
                <Stack spacing={1}>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField {...denseTextFieldProps} label="Ledger search" value={billFilterText} onChange={(e) => setBillFilterText(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") void loadBills(); }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl {...denseSelectProps}>
                        <InputLabel id="bill-status-label">Status</InputLabel>
                        <Select labelId="bill-status-label" label="Status" value={billFilterStatus} onChange={(e) => setBillFilterStatus(String(e.target.value))}>
                          <MenuItem value="">All</MenuItem>
                          {["DRAFT", "ISSUED", "UNPAID", "PARTIALLY_PAID", "PAID", "REFUND_PENDING", "CANCELLED", "CANCELLED_REFUNDED", "REFUNDED", "PARTIALLY_REFUNDED"].map((status) => (
                            <MenuItem key={status} value={status}>{status}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField {...denseTextFieldProps} type="date" label="From" value={billFilterFromDate} onChange={(e) => setBillFilterFromDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField {...denseTextFieldProps} type="date" label="To" value={billFilterToDate} onChange={(e) => setBillFilterToDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                        <Button size="small" variant="outlined" onClick={() => void loadBills()}>Apply filters</Button>
                        <Button size="small" variant="text" onClick={() => { setBillFilterText(""); setBillFilterStatus(""); setBillFilterFromDate(""); setBillFilterToDate(""); setBillFilterMode(""); setBillFilterPatient(consultationPatientId); setBillFilterAppointmentId(consultationAppointmentId); void loadBills({ patientId: "", appointmentId: "", status: "", fromDate: "", toDate: "", paymentMode: "", text: "" }); }}>Clear</Button>
                      </Stack>
                    </Grid>
                  </Grid>
                  <CompactTableFrame maxHeight={560}>
                    <Table size="small" stickyHeader sx={{ width: "100%", minWidth: 900, tableLayout: "fixed", "& .MuiTableCell-root": { py: 0.75, px: 0.75 } }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ width: "20%" }}>Bill</TableCell>
                          <TableCell sx={{ width: "22%" }}>Patient</TableCell>
                          <TableCell sx={{ width: "12%" }}>Status</TableCell>
                          <TableCell sx={{ width: "10%" }} align="right">Due</TableCell>
                          <TableCell sx={{ width: "10%" }} align="right">Paid</TableCell>
                          <TableCell sx={{ width: "12%" }} align="right">Date</TableCell>
                          <TableCell sx={{ width: "14%" }} align="right">Actions</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {bills.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 12).map((bill) => (
                          <TableRow key={bill.id} hover selected={selectedBill?.id === bill.id}>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 800 }}>{bill.billNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {billCountsAsSettled(bill)
                                    ? (bill.consultationId ? "Consultation receipt ready" : "Receipt ready")
                                    : (bill.consultationId ? "Consultation bill" : "Bill")}
                                  {bill.appointmentId ? ` • Appointment ${bill.appointmentId}` : ""}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" noWrap>{bill.patientName || bill.patientNumber || bill.patientId}</Typography>
                              <Typography variant="caption" color="text.secondary" noWrap>{bill.patientNumber || "-"}</Typography>
                            </TableCell>
                            <TableCell>
                              <Chip size="small" label={bill.status} color={statusColor(bill.status)} variant="outlined" sx={compactChipSx} />
                            </TableCell>
                            <TableCell align="right"><Typography variant="body2" sx={{ fontWeight: 700 }}>{formatAmount(billEffectiveDueAmount(bill))}</Typography></TableCell>
                            <TableCell align="right">
                              <Stack spacing={0.25} alignItems="flex-end">
                                <Typography variant="body2">{formatAmount(billNetPaidAmount(bill))}</Typography>
                                {billRefundedAmount(bill) > 0 ? <Typography variant="caption" color="text.secondary">Refunded {formatAmount(billRefundedAmount(bill))}</Typography> : null}
                              </Stack>
                            </TableCell>
                            <TableCell align="right">{bill.billDate}</TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" flexWrap="wrap">
                                <Button size="small" variant="outlined" onClick={() => void selectBill(bill)}>View</Button>
                                <IconButton size="small" onClick={(e) => openLedgerActions(bill, e.currentTarget)}>
                                  <MoreVertRoundedIcon fontSize="small" />
                                </IconButton>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CompactTableFrame>
                </Stack>
              </Collapse>
            </Stack>
          </CardContent>
        </Card>
      </Stack>

      <Menu anchorEl={ledgerActionAnchorEl} open={Boolean(ledgerActionBill && ledgerActionAnchorEl)} onClose={closeLedgerActions}>
        {ledgerMenuItems}
      </Menu>

      <Dialog open={paymentOpen} onClose={() => setPaymentOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Collect payment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {selectedBill ? (
              <Alert severity="info">
                {selectedBill.billNumber} • Total {formatAmount(selectedBill.totalAmount)} • Paid {formatAmount(billNetPaidAmount(selectedBill))} • Due {formatAmount(billEffectiveDueAmount(selectedBill))}
                <br />
                You can record a full or partial payment up to the remaining due amount.
              </Alert>
            ) : null}
            <TextField fullWidth label={<RequiredLabel text="Payment date" required />} type="date" value={paymentForm.paymentDate} onChange={(e) => setPaymentForm((c) => ({ ...c, paymentDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
            <TextField fullWidth label={<RequiredLabel text="Payment amount" required />} helperText={paymentFieldErrors.paymentAmount || "Enter full due amount or any partial amount to keep the balance open."} error={Boolean(paymentFieldErrors.paymentAmount)} value={paymentForm.amount} onChange={(e) => setPaymentForm((c) => ({ ...c, amount: e.target.value }))} />
            <FormControl fullWidth error={Boolean(paymentFieldErrors.paymentMode)}><InputLabel id="payment-mode-label"><RequiredLabel text="Mode" required /></InputLabel><Select labelId="payment-mode-label" label="Mode" value={paymentForm.paymentMode} onChange={(e) => setPaymentForm((c) => ({ ...c, paymentMode: e.target.value as PaymentMode }))}>{PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}</Select>{paymentFieldErrors.paymentMode ? <Typography variant="caption" color="error">{paymentFieldErrors.paymentMode}</Typography> : null}</FormControl>
            <TextField fullWidth label={paymentForm.paymentMode === "CASH" ? "Reference number (optional)" : <RequiredLabel text="Reference number" required />} required={paymentForm.paymentMode !== "CASH"} value={paymentForm.referenceNumber} onChange={(e) => setPaymentForm((c) => ({ ...c, referenceNumber: e.target.value }))} error={Boolean(paymentFieldErrors.referenceNumber)} helperText={paymentFieldErrors.referenceNumber || (paymentForm.paymentMode === "CASH" ? "Optional for cash payments." : "Required for non-cash payments.")} />
            <TextField fullWidth label="Notes" multiline minRows={2} value={paymentForm.notes} onChange={(e) => setPaymentForm((c) => ({ ...c, notes: e.target.value }))} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitPayment()} disabled={saving}>{saving ? "Collecting..." : "Collect Payment"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={refundOpen} onClose={() => setRefundOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Refund</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              fullWidth
              label="Find paid bill"
              helperText="Search by bill number, patient name, patient number, or mobile."
              value={refundBillSearch}
              onChange={(e) => setRefundBillSearch(e.target.value)}
            />
            {refundSearchLoading ? <Alert severity="info">Searching existing bills…</Alert> : null}
            {refundCandidateBills.length > 0 ? (
              <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden", maxHeight: 220, overflowY: "auto" }}>
                <List dense disablePadding>
                  {refundCandidateBills.map((bill) => (
                    <ListItemButton
                      key={bill.id}
                      selected={selectedBill?.id === bill.id}
                      onClick={() => { void selectBill(bill); }}
                    >
                      <ListItemText
                        primary={`${bill.billNumber} • ${bill.patientName || bill.patientNumber || "Patient"}`}
                        secondary={`Net paid ${formatAmount(billNetPaidAmount(bill))} • Refundable ${formatAmount(billRefundableAmount(bill))} • Due ${formatAmount(billEffectiveDueAmount(bill))}`}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </Paper>
            ) : refundBillSearch.trim() ? (
              <Alert severity="warning">No refundable bill matched that search.</Alert>
            ) : null}
            <Alert severity="info">
              {selectedBill ? `Selected bill: ${selectedBill.billNumber} • Refundable amount ${formatAmount(refundableAmount)}` : "Select an existing paid bill to issue a refund."}
            </Alert>
            <TextField fullWidth label={<RequiredLabel text="Amount" required />} value={refundForm.amount} onChange={(e) => setRefundForm((c) => ({ ...c, amount: e.target.value }))} error={Boolean(refundFieldErrors.amount)} helperText={refundFieldErrors.amount || "Enter the amount to refund."} />
            <FormControl fullWidth error={Boolean(refundFieldErrors.refundMode)}><InputLabel id="refund-mode-label"><RequiredLabel text="Mode" required /></InputLabel><Select labelId="refund-mode-label" label="Mode" value={refundForm.refundMode} onChange={(e) => setRefundForm((c) => ({ ...c, refundMode: e.target.value as PaymentMode }))}>{PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}</Select>{refundFieldErrors.refundMode ? <Typography variant="caption" color="error">{refundFieldErrors.refundMode}</Typography> : null}</FormControl>
            <CommentSuggestions
              category="REFUND"
              selectedReason={refundForm.reason}
              remarks={refundForm.notes}
              onReasonChange={(value) => setRefundForm((c) => ({ ...c, reason: value }))}
              onRemarksChange={(value) => setRefundForm((c) => ({ ...c, notes: value }))}
              requiredReason
              reasonLabel="Reason"
              remarksLabel="Notes"
              reasonError={Boolean(refundFieldErrors.reason)}
              reasonHelperText={refundFieldErrors.reason || "Select a refund reason."}
              remarksHelperText={refundFieldErrors.notes || "Optional refund remarks, max 250 characters."}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRefundOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitRefund()} disabled={saving || !selectedBill}>{saving ? "Saving..." : "Refund"}</Button>
        </DialogActions>
      </Dialog>

      {consultationFeeDialog}
      {pendingConsultationFeeDialogNode}

      <InvoicePrintDialog
        open={Boolean(invoicePreview || invoicePreviewLoading)}
        loading={invoicePreviewLoading}
        data={invoicePreview}
        onClose={() => {
          setInvoicePreview(null);
          setInvoicePreviewLoading(false);
          setInvoiceAutoPrint(false);
        }}
        onPrint={() => window.print()}
      />
      <ReceiptPrintDialog
        open={Boolean(receiptPreview || receiptPreviewLoading)}
        loading={receiptPreviewLoading}
        data={receiptPreview}
        onClose={() => {
          setReceiptPreview(null);
          setReceiptPreviewLoading(false);
          setReceiptAutoPrint(false);
        }}
        onPrint={() => window.print()}
      />
    </Box>
  );
}
