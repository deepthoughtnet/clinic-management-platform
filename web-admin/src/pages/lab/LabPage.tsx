import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Autocomplete,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  Menu,
  MenuItem,
  Tooltip,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { ConfirmationDialog } from "../../components/clinical/ConfirmationDialog";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import EditRoundedIcon from "@mui/icons-material/EditRounded";
import MoreVertRoundedIcon from "@mui/icons-material/MoreVertRounded";
import PaidRoundedIcon from "@mui/icons-material/PaidRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import { firstZodError, labDoctorReviewSchema, labOrderCreateSchema, labPaymentSchema, labResultEntrySchema, labTestMasterSchema } from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import { resolveEnabledTenantModules } from "../../modules/moduleRegistry";
import { CompactEmptyState, compactCardContentSx } from "../../components/compact/CompactUi";
import {
  ReceiptPrintDialog,
  type ReceiptPrintData,
} from "../../components/finance/PrintableBillingDocuments";
import PatientQuickRegisterDialog, { patientSummary } from "../../components/patients/PatientQuickRegisterDialog";
import LabDashboard, { LabAnalyticsPanel, type LabDashboardActionKey, type LabDashboardData } from "./LabDashboard";
import LabConfigurationPanel from "./LabConfigurationPanel";
import RequiredLabel from "../../components/forms/RequiredLabel";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import {
  getLabCategoryConfig,
  getClinicProfile,
  collectLabOrderSamples,
  collectLabOrderPayment,
  createLabTest,
  createLabOrder,
  deactivateLabTest,
  enterLabOrderResults,
  getLabCategories,
  getLabTestImportTemplate,
  getLabTestConfig,
  getLabOrderPdf,
  getLabOrderAttachmentBlob,
  getLabOrders,
  getLabTests,
  getReceiptPdf,
  listBillPayments,
  listBillReceipts,
  importLabTestsCsv,
  receiveLabSample,
  rejectLabSample,
  publishLabOrderReport,
  recordLabOrderReportDeliveryAction,
  sendReceipt,
  searchPatients,
  updateLabTest,
  updateLabCategoryConfig,
  updateLabTestConfig,
  verifyLabOrder,
  type LabCategoryConfig,
  type LabTestCatalogueConfig,
  type LabTestCsvImportResult,
  type Patient,
  type LabOrder,
  type LabReportDeliveryEvent,
  type LabOrderOrigin,
  type LabOrderResult,
  type LabSample,
  type LabSampleStatus,
  type LabOrderStatus,
  type LabTest,
  type LabTestInput,
  type LabTestParameterInput,
  type Appointment,
  type Bill,
  type BillLine,
  type ClinicProfile,
  type PaymentMode,
  type Payment,
  type Receipt,
} from "../../api/clinicApi";
import { staffDisplayName } from "../../utils/staffDisplay";

const emptyTestForm: LabTestInput = {
  testCode: "",
  testName: "",
  category: "HEMATOLOGY",
  department: "",
  sampleType: "",
  unit: "",
  referenceRange: "",
  turnaroundTime: "",
  price: 0,
  active: true,
  parameters: [],
};

type ResultComponentForm = {
  parameterName: string;
  componentName: string;
  resultValue: string;
  unit: string;
  referenceRange: string;
  criticalRange: string;
};

type LabParameterForm = LabTestParameterInput;

type ResultItemForm = {
  labOrderItemId: string;
  testName: string;
  testCode: string;
  resultValue: string;
  unit: string;
  referenceRange: string;
  criticalRange: string;
  componentResults: ResultComponentForm[];
};

type LabRequestForm = {
  patientId: string;
  orderOrigin: LabOrderOrigin;
  requestedByInternalDoctorId: string;
  externalDoctorName: string;
  externalDoctorMobile: string;
  externalClinicName: string;
  referralSource: string;
  testIds: string[];
  notes: string;
};

const emptyLabRequestForm = (): LabRequestForm => ({
  patientId: "",
  orderOrigin: "WALK_IN",
  requestedByInternalDoctorId: "",
  externalDoctorName: "",
  externalDoctorMobile: "",
  externalClinicName: "",
  referralSource: "",
  testIds: [],
  notes: "",
});

type SampleCollectionFormRow = {
  labOrderItemId: string | null;
  testName: string;
  specimenType: string;
  containerType: string;
  notes: string;
};
type SampleCollectionStatus = (typeof SAMPLE_COLLECTION_STATUS_OPTIONS)[number];

const SAMPLE_REJECTION_REASONS = [
  "Hemolysed sample",
  "Insufficient quantity",
  "Wrong container",
  "Clotted sample",
  "Leaked sample",
  "Patient not fasting",
  "Label mismatch",
  "Other",
] as const;
const SAMPLE_CONTAINER_TYPE_OPTIONS = [
  "EDTA",
  "Plain Tube",
  "Fluoride",
  "Citrate",
  "Urine Container",
  "Stool Container",
  "Slide",
  "Imaging",
  "Other",
] as const;
const SAMPLE_COLLECTION_STATUS_OPTIONS = [
  "Collected",
  "Partial Collection",
  "Sample Not Obtained",
  "Patient Refused",
] as const;

const DIRECT_ORDER_ORIGIN_OPTIONS: LabOrderOrigin[] = ["WALK_IN", "DOCTOR_REFERRAL"];
const APPROVED_REPORT_STATUSES = new Set<LabOrderStatus>(["DOCTOR_REVIEWED", "REPORT_GENERATED", "DELIVERED"]);
const DELIVERY_CHANNEL_OPTIONS = [
  { value: "PATIENT_PORTAL", label: "Patient portal" },
  { value: "DOCTOR_NOTIFICATION", label: "Doctor notification" },
  { value: "PRINT", label: "Print" },
] as const;

function formatOrderOrigin(origin: LabOrderOrigin | string | null | undefined) {
  switch (origin) {
    case "CONSULTATION":
      return "Consultation";
    case "WALK_IN":
      return "Walk-in";
    case "DOCTOR_REFERRAL":
      return "Doctor Referral";
    case "HEALTH_PACKAGE":
      return "Health Package";
    case "CORPORATE":
      return "Corporate";
    case "HOME_COLLECTION":
      return "Home Collection";
    case "FOLLOW_UP":
      return "Follow-up";
    default:
      return "Walk-in";
  }
}

function requestedByLabel(row: LabOrder) {
  if (row.doctorName) return `Dr. ${row.doctorName.replace(/^Dr\.?\s*/i, "")}`;
  if (row.externalDoctorName) return `External Dr. ${row.externalDoctorName}`;
  if (row.referralSource) return row.referralSource;
  return "—";
}

function formatMoney(value: number | null | undefined) {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return value.toFixed(2);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
}

function formatDateChip(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "2-digit" }).format(date);
}

function formatTimeChip(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date);
}

function formatReferenceBadge(value: string | null | undefined) {
  return value && value.trim() ? value.trim() : "-";
}

function parseRangeBound(value: string) {
  const trimmed = value.trim();
  const normalized = trimmed.replace(/\s+/g, "");
  const match = normalized.match(/^([<>]=?|=)?(-?\d+(?:\.\d+)?)$/);
  if (!match) {
    return null;
  }
  return { operator: match[1] || "=", value: Number(match[2]) };
}

function parseRangeWindow(range: string | null | undefined) {
  if (!range || !range.trim()) return null;
  const text = range.trim().replace(/\s+/g, "");
  const between = text.match(/^(-?\d+(?:\.\d+)?)[:-](-?\d+(?:\.\d+)?)$/);
  if (between) {
    const left = Number(between[1]);
    const right = Number(between[2]);
    if (Number.isFinite(left) && Number.isFinite(right)) {
      return { min: Math.min(left, right), max: Math.max(left, right) };
    }
  }
  const values = text.split(/[,/]/).map((part) => part.trim()).filter(Boolean);
  if (values.length === 2 && values.every((part) => Number.isFinite(Number(part)))) {
    const first = Number(values[0]);
    const second = Number(values[1]);
    return { min: Math.min(first, second), max: Math.max(first, second) };
  }
  const bound = parseRangeBound(text);
  if (bound) {
    if (bound.operator === ">" || bound.operator === ">=") {
      return { min: bound.value, max: Number.POSITIVE_INFINITY, inclusiveMin: bound.operator === ">=" };
    }
    if (bound.operator === "<" || bound.operator === "<=") {
      return { min: Number.NEGATIVE_INFINITY, max: bound.value, inclusiveMax: bound.operator === "<=" };
    }
    return { min: bound.value, max: bound.value, inclusiveMin: true, inclusiveMax: true };
  }
  return null;
}

function getResultSeverity(value: string, referenceRange: string | null | undefined, criticalRange: string | null | undefined) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "default";
  const critical = parseRangeWindow(criticalRange);
  const reference = parseRangeWindow(referenceRange);
  const inWindow = (window: ReturnType<typeof parseRangeWindow> | null) => {
    if (!window) return true;
    const minOk = window.min === Number.NEGATIVE_INFINITY
      ? true
      : window.inclusiveMin === false
        ? numeric > window.min
        : numeric >= window.min;
    const maxOk = window.max === Number.POSITIVE_INFINITY
      ? true
      : window.inclusiveMax === false
        ? numeric < window.max
        : numeric <= window.max;
    return minOk && maxOk;
  };
  if (critical && !inWindow(critical)) return "error";
  if (reference && !inWindow(reference)) return "warning";
  return "default";
}

function resultFieldSx(severity: string) {
  if (severity === "error") {
    return {
      "& .MuiOutlinedInput-root fieldset": { borderColor: "error.main" },
      "& .MuiInputBase-input": { fontWeight: 700 },
    };
  }
  if (severity === "warning") {
    return {
      "& .MuiOutlinedInput-root fieldset": { borderColor: "warning.main" },
      "& .MuiInputBase-input": { fontWeight: 700 },
    };
  }
  return undefined;
}

function latestReceiptForBill(receipts: Receipt[], billId: string) {
  return receipts
    .filter((receipt) => receipt.billId === billId)
    .slice()
    .sort((left, right) => `${right.receiptDate}|${right.createdAt}`.localeCompare(`${left.receiptDate}|${left.createdAt}`))[0] || null;
}

function paymentForReceipt(payments: Payment[], receipt: Receipt | null) {
  if (!receipt) return null;
  return payments.find((payment) => payment.id === receipt.paymentId) || null;
}

function buildReceiptPaymentSummary(row: LabOrder) {
  const receipt = row.paymentReceipt;
  if (receipt) {
    return {
      receiptId: receipt.receiptId,
      receiptNumber: receipt.receiptNumber,
      receiptDate: receipt.collectedAt ? receipt.collectedAt.slice(0, 10) : row.receiptDate || null,
      paymentId: row.paymentId || receipt.receiptId || null,
      paymentDateTime: receipt.collectedAt || row.paymentDateTime || row.paymentCollectedAt || null,
      paymentDate: receipt.collectedAt ? receipt.collectedAt.slice(0, 10) : row.paymentDate || row.paymentCollectedAt?.slice(0, 10) || null,
      paymentAmount: receipt.amount ?? row.paymentAmount ?? row.billTotalAmount ?? row.billDueAmount ?? null,
      paymentMode: receipt.paymentMode || row.paymentMode || null,
      referenceNumber: receipt.referenceNumber || row.referenceNumber || null,
      receivedBy: staffDisplayName(receipt.collectedBy || null, row.receivedBy),
    };
  }
  if (!row.receiptId || !row.receiptNumber) return null;
  return {
    receiptId: row.receiptId,
    receiptNumber: row.receiptNumber,
    receiptDate: row.receiptDate || row.paymentDate || row.paymentCollectedAt?.slice(0, 10) || null,
    paymentId: row.paymentId || null,
    paymentDateTime: row.paymentDateTime || row.paymentCollectedAt || null,
    paymentDate: row.paymentDate || row.paymentCollectedAt?.slice(0, 10) || null,
    paymentAmount: row.paymentAmount ?? row.billTotalAmount ?? row.billDueAmount ?? null,
    paymentMode: row.paymentMode || null,
    referenceNumber: row.referenceNumber || null,
    receivedBy: staffDisplayName(null, row.receivedBy),
  };
}

function buildLabReceiptBill(row: LabOrder): Bill {
  const lines: BillLine[] = row.items.map((item, index) => ({
    id: item.id,
    itemType: "TEST",
    itemName: item.testName,
    quantity: 1,
    unitPrice: item.price || 0,
    totalPrice: item.price || 0,
    referenceId: item.labTestId,
    sortOrder: item.sortOrder ?? index + 1,
  }));
  const subtotalAmount = lines.reduce((sum, line) => sum + line.totalPrice, 0);
  const totalAmount = row.billTotalAmount ?? subtotalAmount;
  const dueAmount = row.billDueAmount ?? Math.max(0, totalAmount - (row.paymentAmount ?? 0));
  const paidAmount = row.paymentAmount ?? Math.max(0, totalAmount - dueAmount);
  return {
    id: row.billId || row.id,
    tenantId: row.tenantId,
    billNumber: row.billNumber || row.orderNumber,
    patientId: row.patientId,
    patientNumber: row.patientNumber,
    patientName: row.patientName,
    consultationId: row.consultationId,
    appointmentId: null,
    billDate: row.paymentDate || row.paymentCollectedAt?.slice(0, 10) || row.orderedAt.slice(0, 10),
    status: (row.billStatus || (row.billDueAmount != null && row.billDueAmount > 0 ? "PARTIALLY_PAID" : "PAID")) as Bill["status"],
    subtotalAmount,
    discountType: "NONE",
    discountValue: 0,
    discountAmount: 0,
    discountReason: null,
    taxAmount: 0,
    totalAmount,
    paidAmount,
    refundedAmount: 0,
    netPaidAmount: paidAmount,
    invoiceEmailedAt: null,
    dueAmount,
    notes: row.notes,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    lines,
  };
}

function receiptTimestampText(row: LabOrder) {
  return formatDateTime(row.paymentReceipt?.collectedAt || row.paymentDateTime || row.paymentCollectedAt || row.receiptDate || row.paymentDate || null);
}

function statusTone(status: LabOrderStatus | string) {
  switch (status) {
    case "READY_FOR_COLLECTION":
      return "info";
    case "SAMPLE_COLLECTED":
    case "PROCESSING":
      return "warning";
    case "RESULT_ENTERED":
    case "REPORT_READY":
    case "REPORT_GENERATED":
    case "DOCTOR_REVIEWED":
    case "DELIVERED":
    case "PAID":
      return "success";
    case "PAYMENT_PENDING":
      return "warning";
    case "ORDERED":
      return "default";
    case "CANCELLED":
      return "default";
    default:
      return "default";
  }
}

function receiptActionLabel(row: LabOrder) {
  if (row.billDueAmount != null && row.billDueAmount > 0) return "Collect";
  return "View Receipt";
}

function resultTone(flag: string | null | undefined) {
  switch ((flag || "").toUpperCase()) {
    case "CRITICAL":
    case "CRITICAL_LOW":
    case "CRITICAL_HIGH":
      return "error";
    case "LOW":
    case "HIGH":
      return "warning";
    case "NORMAL":
      return "success";
    default:
      return "default";
  }
}

function resultFlagLabel(flag: string | null | undefined) {
  switch ((flag || "").toUpperCase()) {
    case "CRITICAL_LOW":
      return "Critical Low";
    case "CRITICAL_HIGH":
      return "Critical High";
    case "CRITICAL":
      return "Critical";
    case "LOW":
      return "Low";
    case "HIGH":
      return "High";
    case "NORMAL":
      return "Normal";
    default:
      return flag || "Pending";
  }
}

function isSameLocalDay(value: string | null | undefined, reference: Date) {
  if (!value) return false;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return false;
  return (
    parsed.getFullYear() === reference.getFullYear()
    && parsed.getMonth() === reference.getMonth()
    && parsed.getDate() === reference.getDate()
  );
}

function parseTurnaroundHours(value: string | null | undefined) {
  if (!value) return null;
  const numeric = Number(value);
  if (Number.isFinite(numeric)) return numeric;
  const match = value.trim().match(/^(\d+(?:\.\d+)?)\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)?$/i);
  if (!match) return null;
  const amount = Number(match[1]);
  const unit = (match[2] || "h").toLowerCase();
  if (!Number.isFinite(amount)) return null;
  switch (unit) {
    case "m":
    case "min":
    case "mins":
    case "minute":
    case "minutes":
      return amount / 60;
    case "d":
    case "day":
    case "days":
      return amount * 24;
    default:
      return amount;
  }
}

function sampleStatusTone(status: LabSampleStatus | null | undefined) {
  switch (status) {
    case "REJECTED":
    case "RECOLLECTION_REQUIRED":
      return "error";
    case "RECEIVED":
      return "success";
    case "COLLECTED":
      return "warning";
    default:
      return "default";
  }
}

function sampleSummaryLabel(order: LabOrder) {
  switch (order.sampleSummaryStatus) {
    case "COLLECTED":
      return "Collected";
    case "RECEIVED":
      return "Received";
    case "REJECTED":
      return "Rejected";
    case "RECOLLECTION_REQUIRED":
      return "Recollection required";
    case "CANCELLED":
      return "Cancelled";
    default:
      return "No sample";
  }
}

function deliveryChannelsLabel(channels: string[] | null | undefined) {
  if (!channels || !channels.length) return "Portal";
  return channels.map((channel) => channel.replaceAll("_", " ").toLowerCase()).join(", ");
}

function actionableSample(order: LabOrder) {
  return [...order.samples].reverse().find((sample) => sample.status === "COLLECTED" || sample.status === "RECEIVED") || null;
}

function toDatetimeLocal(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);
  return local.toISOString().slice(0, 16);
}

function toIsoFromDatetimeLocal(value: string) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function openPdf(blob: Blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function reportDeliveryHistorySummary(events: LabReportDeliveryEvent[] | null | undefined) {
  if (!events || !events.length) return [];
  return events.slice(0, 4).map((event) => event.label || reportActionHistoryLabel(event.action));
}

function copyTextToClipboard(value: string) {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(value);
  }
  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  document.execCommand("copy");
  textarea.remove();
  return Promise.resolve();
}

function reportActionHistoryLabel(action: string) {
  switch (action) {
    case "lab_order.report_viewed":
      return "Portal viewed";
    case "lab_order.report_printed":
      return "Printed";
    case "lab_order.report_downloaded":
      return "PDF downloaded";
    case "lab_order.report_email_sent":
      return "Email sent";
    case "lab_order.report_whatsapp_sent":
      return "WhatsApp sent";
    case "lab_order.report_shared_link":
      return "Share link copied";
    default:
      return action.replaceAll("_", " ");
  }
}

function ReportActionMenuButton(props: {
  row: LabOrder;
  disabled?: boolean;
  onViewReport: (row: LabOrder) => void;
  onPrintReport: (row: LabOrder) => void;
  onDownloadReport: (row: LabOrder) => void;
  onEmailReport: (row: LabOrder) => void;
  onWhatsappReport: (row: LabOrder) => void;
  onShareLink: (row: LabOrder) => void;
}) {
  const { row, disabled, onViewReport, onPrintReport, onDownloadReport, onEmailReport, onWhatsappReport, onShareLink } = props;
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);

  return (
    <>
      <Tooltip title="Report actions">
        <IconButton size="small" onClick={(event) => setAnchorEl(event.currentTarget)} disabled={disabled}>
          <MoreVertRoundedIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}>
        <MenuItem onClick={() => { setAnchorEl(null); onViewReport(row); }}>View Report</MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onPrintReport(row); }}>Print Report</MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onDownloadReport(row); }}>Download PDF</MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onEmailReport(row); }}>Email</MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onWhatsappReport(row); }}>WhatsApp</MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onShareLink(row); }}>Share Link</MenuItem>
      </Menu>
    </>
  );
}

function defaultResultsForItem(orderItem: LabOrder["items"][number], existingResults: LabOrderResult[]): ResultItemForm {
  const existingForItem = existingResults.filter((row) => row.labOrderItemId === orderItem.id);
  if (existingForItem.length > 0) {
    if (existingForItem.some((row) => row.componentName)) {
      return {
        labOrderItemId: orderItem.id,
        testName: orderItem.testName,
        testCode: orderItem.testCode,
        resultValue: "",
        unit: orderItem.unit || "",
        referenceRange: orderItem.referenceRange || "",
        criticalRange: "",
        componentResults: existingForItem.map((row) => ({
          parameterName: row.parameterName || "",
          componentName: row.componentName || "",
          resultValue: row.resultValue || "",
          unit: row.unit || "",
          referenceRange: row.referenceRange || "",
          criticalRange: orderItem.parameters.find((parameter) => parameter.parameterName === (row.parameterName || row.componentName || ""))?.criticalRange || "",
        })),
      };
    }
    const first = existingForItem[0];
    return {
      labOrderItemId: orderItem.id,
      testName: orderItem.testName,
      testCode: orderItem.testCode,
      resultValue: first.resultValue || "",
      unit: first.unit || orderItem.unit || "",
      referenceRange: first.referenceRange || orderItem.referenceRange || "",
      criticalRange: orderItem.parameters[0]?.criticalRange || "",
      componentResults: [],
    };
  }
  const defaultComponents = orderItem.parameters.length
    ? orderItem.parameters.map((parameter) => ({
        parameterName: parameter.parameterName,
        componentName: parameter.parameterName,
        resultValue: "",
        unit: parameter.unit || "",
        referenceRange: parameter.normalRange || "",
        criticalRange: parameter.criticalRange || "",
      }))
    : orderItem.testName.toUpperCase().includes("CBC")
      ? ["Hemoglobin", "WBC", "RBC", "Platelets"].map((componentName) => ({
          parameterName: componentName,
          componentName,
          resultValue: "",
          unit: "",
          referenceRange: "",
          criticalRange: "",
        }))
      : [];
  return {
    labOrderItemId: orderItem.id,
    testName: orderItem.testName,
    testCode: orderItem.testCode,
    resultValue: "",
    unit: orderItem.unit || "",
    referenceRange: orderItem.referenceRange || "",
    criticalRange: orderItem.parameters[0]?.criticalRange || "",
    componentResults: defaultComponents,
  };
}

export default function LabPage() {
  const auth = useAuth();
  const [tests, setTests] = React.useState<LabTest[]>([]);
  const [categoryConfigs, setCategoryConfigs] = React.useState<LabCategoryConfig[]>([]);
  const [testConfigs, setTestConfigs] = React.useState<LabTestCatalogueConfig[]>([]);
  const [orders, setOrders] = React.useState<LabOrder[]>([]);
  const [categories, setCategories] = React.useState<string[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [deactivateTarget, setDeactivateTarget] = React.useState<LabTest | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [tab, setTab] = React.useState(0);
  const [search, setSearch] = React.useState("");
  const [orderSearch, setOrderSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<"ALL" | LabOrderStatus>("ALL");
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<LabTest | null>(null);
  const [form, setForm] = React.useState<LabTestInput>(emptyTestForm);
  const [paymentTarget, setPaymentTarget] = React.useState<LabOrder | null>(null);
  const [sampleTarget, setSampleTarget] = React.useState<LabOrder | null>(null);
  const [resultTarget, setResultTarget] = React.useState<LabOrder | null>(null);
  const [reviewTarget, setReviewTarget] = React.useState<LabOrder | null>(null);
  const [paymentMode, setPaymentMode] = React.useState<PaymentMode>("CASH");
  const [paymentReference, setPaymentReference] = React.useState("");
  const [paymentNotes, setPaymentNotes] = React.useState("");
  const [receiptPreview, setReceiptPreview] = React.useState<{ order: LabOrder; receipt: Receipt; payment: Payment } | null>(null);
  const [receiptActionLoading, setReceiptActionLoading] = React.useState(false);
  const [receiptActionError, setReceiptActionError] = React.useState<string | null>(null);
  const [receiptPrintLoading, setReceiptPrintLoading] = React.useState(false);
  const [receiptPrintOpen, setReceiptPrintOpen] = React.useState(false);
  const [receiptPrintAutoPrint, setReceiptPrintAutoPrint] = React.useState(false);
  const [receiptPrintData, setReceiptPrintData] = React.useState<ReceiptPrintData | null>(null);
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [sampleCollectedBy, setSampleCollectedBy] = React.useState("");
  const [sampleCollectedAt, setSampleCollectedAt] = React.useState(toDatetimeLocal(new Date().toISOString()));
  const [sampleCollectionStatus, setSampleCollectionStatus] = React.useState<SampleCollectionStatus>("Collected");
  const [sampleRows, setSampleRows] = React.useState<SampleCollectionFormRow[]>([]);
  const [collectedSamples, setCollectedSamples] = React.useState<LabSample[]>([]);
  const [receiveTarget, setReceiveTarget] = React.useState<LabSample | null>(null);
  const [rejectTarget, setRejectTarget] = React.useState<LabSample | null>(null);
  const [rejectReason, setRejectReason] = React.useState("");
  const [rejectNotes, setRejectNotes] = React.useState("");
  const [recollectionRequired, setRecollectionRequired] = React.useState(false);
  const [resultComments, setResultComments] = React.useState("");
  const [resultItems, setResultItems] = React.useState<ResultItemForm[]>([]);
  const [resultDraftLoaded, setResultDraftLoaded] = React.useState(false);
  const [resultSavingDraft, setResultSavingDraft] = React.useState(false);
  const [resultSaveMessage, setResultSaveMessage] = React.useState<string | null>(null);
  const [resultAttachmentPreview, setResultAttachmentPreview] = React.useState<{
    attachment: LabOrder["attachments"][number];
    blobUrl: string;
    mediaType: string;
  } | null>(null);
  const [resultAttachmentLoading, setResultAttachmentLoading] = React.useState(false);
  const [reviewComments, setReviewComments] = React.useState("");
  const [reviewDecision, setReviewDecision] = React.useState<"APPROVE" | "SEND_BACK">("APPROVE");
  const [reviewReason, setReviewReason] = React.useState("");
  const [publishTarget, setPublishTarget] = React.useState<LabOrder | null>(null);
  const [publishNotes, setPublishNotes] = React.useState("");
  const [publishChannels, setPublishChannels] = React.useState<string[]>(["PATIENT_PORTAL"]);
  const [publishSuccessTarget, setPublishSuccessTarget] = React.useState<LabOrder | null>(null);
  const [importOpen, setImportOpen] = React.useState(false);
  const [importResult, setImportResult] = React.useState<LabTestCsvImportResult | null>(null);
  const [requestOpen, setRequestOpen] = React.useState(false);
  const [requestForm, setRequestForm] = React.useState<LabRequestForm>(emptyLabRequestForm());
  const [quickRegisterOpen, setQuickRegisterOpen] = React.useState(false);
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientOptions, setPatientOptions] = React.useState<Patient[]>([]);
  const [patientLoading, setPatientLoading] = React.useState(false);
  const [patientOptionsLoaded, setPatientOptionsLoaded] = React.useState(false);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  const canManageTests = auth.hasPermission("lab.test.manage");
  const canUseLabReception = auth.hasPermission("lab.reception.access") || auth.rolesUpper.includes("LAB_FRONT_DESK");
  const canViewOrders = auth.hasPermission("lab.order.read")
    || canUseLabReception
    || auth.hasPermission("lab.order.collect_payment")
    || auth.hasPermission("lab.order.collect_sample")
    || auth.hasPermission("lab.order.result_entry")
    || auth.hasPermission("lab.order.generate_report")
    || auth.hasPermission("lab.order.create");
  const canCollectPayment = auth.hasPermission("lab.order.collect_payment");
  const canCollectSample = canUseLabReception || auth.hasPermission("lab.order.collect_sample");
  const canEnterResults = auth.hasPermission("lab.order.result_entry");
  const canGenerateReport = auth.hasPermission("lab.order.generate_report");
  const canSendReceipt = auth.hasPermission("notification.send") || auth.hasPermission("billing.read") || auth.hasPermission("payment.collect");
  const canAccessLabReceipts = canUseLabReception
    || canCollectPayment
    || auth.hasPermission("billing.read")
    || auth.hasPermission("billing.receipt")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("BILLING_USER")
    || auth.rolesUpper.includes("LAB_APPROVER")
    || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canReviewReport = auth.hasPermission("lab.order.review");
  const canCreateOrders = canUseLabReception || auth.hasPermission("lab.order.create");
  const canQuickRegisterPatient = canCreateOrders && auth.hasPermission("patient.create") && auth.hasPermission("patient.read");
  const enabledModules = React.useMemo(() => resolveEnabledTenantModules(auth), [auth]);
  const consultationEnabled = enabledModules.has("CONSULTATION");
  const laboratoryMode = consultationEnabled ? "INTEGRATED" : "STANDALONE";
  const primaryRegistrationLabel = "New Lab Order";
  const labBadgeLabel = consultationEnabled ? "Integrated Laboratory" : "Standalone Laboratory";
  const pageSubtitle = consultationEnabled
    ? "Manage consultation-linked and walk-in laboratory orders, billing, samples, results, and reports."
    : "Run standalone diagnostic registrations, billing, sample collection, results, and reports without consultation dependency.";
  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [categoryRows, testRows, orderRows, categoryConfigRows, testConfigRows] = await Promise.all([
        getLabCategories(auth.accessToken, auth.tenantId),
        getLabTests(auth.accessToken, auth.tenantId, { active: null }),
        canViewOrders ? getLabOrders(auth.accessToken, auth.tenantId, {}) : Promise.resolve([] as LabOrder[]),
        canManageTests ? getLabCategoryConfig(auth.accessToken, auth.tenantId) : Promise.resolve([] as LabCategoryConfig[]),
        canManageTests ? getLabTestConfig(auth.accessToken, auth.tenantId) : Promise.resolve([] as LabTestCatalogueConfig[]),
      ]);
      setCategories(categoryRows);
      setTests(testRows);
      setOrders(orderRows);
      setCategoryConfigs(categoryConfigRows);
      setTestConfigs(testConfigRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load laboratory module");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canManageTests, canViewOrders]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    orders
      .filter((row) => row.status === "PAID" || (row.billDueAmount != null && row.billDueAmount <= 0))
      .filter((row) => !row.paymentReceipt)
      .forEach((row) => {
        console.warn("[lab] paid order is missing paymentReceipt metadata", {
          orderId: row.id,
          orderNumber: row.orderNumber,
          billId: row.billId,
          billNumber: row.billNumber,
        });
      });
  }, [orders]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinicProfile() {
      if (!auth.accessToken || !auth.tenantId) return;
      try {
        const profile = await getClinicProfile(auth.accessToken, auth.tenantId);
        if (!cancelled) setClinicProfile(profile);
      } catch {
        if (!cancelled) setClinicProfile(null);
      }
    }
    void loadClinicProfile();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (receiptPrintAutoPrint && receiptPrintData && !receiptPrintLoading) {
      const handle = window.setTimeout(() => window.print(), 60);
      setReceiptPrintAutoPrint(false);
      return () => window.clearTimeout(handle);
    }
    return undefined;
  }, [receiptPrintAutoPrint, receiptPrintData, receiptPrintLoading]);

  React.useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!saving) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [saving]);

  React.useEffect(() => {
    if (!requestOpen || !auth.accessToken || !auth.tenantId) {
      setPatientLoading(false);
      return;
    }
    if (patientQuery.trim().length < 2) {
      setPatientOptions(selectedPatient ? [selectedPatient] : []);
      setPatientOptionsLoaded(true);
      setPatientLoading(false);
      return;
    }
    setPatientLoading(true);
    const timer = window.setTimeout(() => {
      const query = patientQuery.trim();
      const attempts = [
        { patientNumber: query, active: true as const },
        { mobile: query, active: true as const },
        { name: query, active: true as const },
      ];
      void attempts.reduce<Promise<Patient[]>>(
        (promise, params) => promise.then((rows) => (rows.length ? rows : searchPatients(auth.accessToken!, auth.tenantId!, params))),
        Promise.resolve([] as Patient[]),
      ).then((rows) => {
        setPatientOptions(rows);
        setPatientOptionsLoaded(true);
      })
        .catch((err) => {
          setError(err instanceof Error ? err.message : "Failed to search patients");
        })
        .finally(() => {
          setPatientLoading(false);
        });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [auth.accessToken, auth.tenantId, patientQuery, requestOpen, selectedPatient]);

  const filteredTests = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return tests.filter((row) => {
      if (!term) return true;
      return [
        row.testCode,
        row.testName,
        row.category,
        row.department,
        row.sampleType,
        row.unit,
        row.referenceRange,
        row.turnaroundTime,
      ].filter(Boolean).some((value) => String(value).toLowerCase().includes(term));
    });
  }, [search, tests]);
  const visibleCatalogTests = React.useMemo(() => filteredTests.slice(0, 50), [filteredTests]);
  const activeCategoryCodes = React.useMemo(() => new Set(categories), [categories]);
  const availableTests = React.useMemo(
    () => tests.filter((row) => row.active && row.enabled && activeCategoryCodes.has(row.category)),
    [activeCategoryCodes, tests],
  );

  const pendingSampleOrders = React.useMemo(() => orders.filter((row) => row.status === "READY_FOR_COLLECTION"), [orders]);
  const pendingResultsOrders = React.useMemo(() => orders.filter((row) => row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED"), [orders]);
  const pendingReviewOrders = React.useMemo(() => orders.filter((row) => row.status === "RESULT_ENTERED"), [orders]);
  const visibleStatusFilters = React.useMemo(() => {
    const defaultStatuses: Array<"ALL" | LabOrderStatus> = ["ALL", "PAYMENT_PENDING", "READY_FOR_COLLECTION", "SAMPLE_COLLECTED", "RESULT_ENTERED", "REPORT_READY", "DOCTOR_REVIEWED", "DELIVERED", "CANCELLED"];
    const presentStatuses = orders.map((row) => row.status).filter((status) => !defaultStatuses.includes(status));
    return [...defaultStatuses, ...presentStatuses.filter((status, index) => presentStatuses.indexOf(status) === index)];
  }, [orders]);
  const filteredOrders = React.useMemo(
    () => orders.filter((row) => {
      if (statusFilter !== "ALL" && row.status !== statusFilter) return false;
      const term = orderSearch.trim().toLowerCase();
      if (!term) return true;
      return [row.orderNumber, row.patientNumber, row.patientName, row.doctorName, row.notes, row.items.map((item) => `${item.testCode} ${item.testName}`).join(" ")]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    }),
    [orders, orderSearch, statusFilter],
  );

  const dashboardData = React.useMemo<LabDashboardData>(() => {
    const now = new Date();
    const todayOrders = orders.filter((row) => isSameLocalDay(row.orderedAt, now));
    const todayCollections = orders.filter((row) => isSameLocalDay(row.sampleCollectedAt, now));
    const todayResults = orders.filter((row) => isSameLocalDay(row.resultEnteredAt, now));
    const todayReports = orders.filter((row) => isSameLocalDay(row.reportPublishedAt, now));
    const walkInOrdersToday = todayOrders.filter((row) => row.orderOrigin === "WALK_IN");
    const todayRevenue = orders
      .filter((row) => isSameLocalDay(row.paymentCollectedAt, now))
      .reduce((sum, row) => sum + (row.billTotalAmount ?? 0), 0);
    const pendingPaymentCount = orders.filter((row) => row.status === "PAYMENT_PENDING").length;
    const pendingCollectionCount = orders.filter((row) => row.status === "READY_FOR_COLLECTION").length;
    const workQueueCount = orders.filter((row) => ["SAMPLE_COLLECTED", "PROCESSING"].includes(row.status)).length;
    const pendingReviewCount = pendingReviewOrders.length;
    const readyToPublishCount = orders.filter((row) => row.status === "REPORT_READY" || row.status === "REPORT_GENERATED" || row.status === "DOCTOR_REVIEWED").length;
    const criticalResultsCount = orders.filter((row) => row.results.some((result) => result.criticalResult || /critical/i.test(result.resultFlag || ""))).length;
    const tatBreachedCount = orders.filter((row) => {
      if (row.status === "DELIVERED" || row.status === "CANCELLED") return false;
      const turnaroundHours = row.items
        .map((item) => parseTurnaroundHours(item.turnaroundTime))
        .filter((value): value is number => typeof value === "number");
      if (!turnaroundHours.length) return false;
      const maxTurnaroundHours = Math.max(...turnaroundHours);
      const ageHours = (now.getTime() - new Date(row.orderedAt).getTime()) / 3_600_000;
      return ageHours > maxTurnaroundHours;
    }).length;
    const sampleRejectedCount = orders.filter((row) => row.samples.some((sample) => sample.status === "REJECTED")).length;
    const recollectionRequiredCount = orders.filter((row) => row.samples.some((sample) => sample.status === "RECOLLECTION_REQUIRED" || sample.recollectionRequired)).length;
    const resultsPendingEntryCount = pendingResultsOrders.length;

    const workToday: LabDashboardData["myWorkToday"] = [];
    const addWorkCard = (metric: LabDashboardData["myWorkToday"][number]) => {
      if (workToday.some((row) => row.key === metric.key)) return;
      workToday.push(metric);
    };

    if (canCreateOrders || canCollectPayment || canManageTests) {
      addWorkCard({ key: "work-pending-payment", label: "Pending Payment", value: pendingPaymentCount, helper: "Awaiting billing clearance", tone: "warning" });
      addWorkCard({ key: "work-walk-in-orders-today", label: "Walk-in Orders Today", value: walkInOrdersToday.length, helper: "Front-desk registrations", tone: "info" });
      addWorkCard({ key: "work-new-orders", label: "New Orders", value: todayOrders.length, helper: "Created since opening", tone: "info" });
    }
    if (canCollectSample || canManageTests) {
      addWorkCard({ key: "work-pending-sample-collection", label: "Pending Sample Collection", value: pendingCollectionCount, helper: "Ready for collection", tone: "info" });
      addWorkCard({ key: "work-recollection-required", label: "Recollection Required", value: recollectionRequiredCount, helper: "Return to collection", tone: "warning" });
      addWorkCard({ key: "work-sample-rejected", label: "Sample Rejected", value: sampleRejectedCount, helper: "Requires follow-up", tone: "error" });
    }
    if (canEnterResults || canManageTests) {
      addWorkCard({ key: "work-work-queue", label: "Work Queue", value: workQueueCount, helper: "Active lab workflow", tone: "default" });
      addWorkCard({ key: "work-samples-collected", label: "Samples Collected", value: todayCollections.length, helper: "Collected today", tone: "info" });
      addWorkCard({ key: "work-results-pending-entry", label: "Results Pending Entry", value: resultsPendingEntryCount, helper: "Awaiting result entry", tone: "warning" });
      addWorkCard({ key: "work-critical-results", label: "Critical Results", value: criticalResultsCount, helper: "Immediate attention", tone: "error" });
    }
    if (canReviewReport || canGenerateReport || canManageTests) {
      addWorkCard({ key: "work-pending-lab-review", label: "Pending Lab Review", value: pendingReviewCount, helper: "Awaiting verification", tone: "warning" });
      addWorkCard({ key: "work-ready-to-publish", label: "Ready to Publish", value: readyToPublishCount, helper: "Report actions pending", tone: "success" });
      addWorkCard({ key: "work-published-today", label: "Published Today", value: todayReports.length, helper: "Delivered to patients", tone: "success" });
      addWorkCard({ key: "work-tat-breached", label: "TAT Breached", value: tatBreachedCount, helper: "Needs escalation", tone: "warning" });
    }
    if (!workToday.length) {
      addWorkCard({ key: "work-published-today", label: "Published Today", value: todayReports.length, helper: "Delivered to patients", tone: "success" });
      addWorkCard({ key: "work-critical-results", label: "Critical Results", value: criticalResultsCount, helper: "Immediate attention", tone: "error" });
      addWorkCard({ key: "work-tat-breached", label: "TAT Breached", value: tatBreachedCount, helper: "Needs escalation", tone: "warning" });
    }

    const alerts = [
      {
        key: "critical-results" as const,
        label: "Critical Results",
        value: criticalResultsCount,
        helper: "Immediate attention",
        tone: "error" as const,
      },
      {
        key: "tat-breached" as const,
        label: "TAT Breached",
        value: tatBreachedCount,
        helper: "Needs escalation",
        tone: "warning" as const,
      },
      {
        key: "sample-rejected" as const,
        label: "Sample Rejected",
        value: orders.filter((row) => row.samples.some((sample) => sample.status === "REJECTED")).length,
        helper: "Requires follow-up",
        tone: "error" as const,
      },
      {
        key: "recollection-required" as const,
        label: "Recollection Required",
        value: orders.filter((row) => row.samples.some((sample) => sample.status === "RECOLLECTION_REQUIRED" || sample.recollectionRequired)).length,
        helper: "Return to collection",
        tone: "warning" as const,
      },
    ];

    const sevenDaySeries = (selector: (row: LabOrder) => string | null | undefined) => {
      const dayStarts = Array.from({ length: 7 }, (_, index) => {
        const day = new Date(now);
        day.setHours(0, 0, 0, 0);
        day.setDate(day.getDate() - (6 - index));
        return day;
      });
      return dayStarts.map((day) => orders.filter((row) => isSameLocalDay(selector(row), day)).length);
    };

    return {
      myWorkToday: workToday,
      analytics: {
        alerts,
        activity: [
          { key: "new-orders" as const, label: "Orders", value: todayOrders.length, helper: "Today", tone: "info" as const },
          { key: "work-queue" as const, label: "Collections", value: todayCollections.length, helper: "Samples logged", tone: "info" as const },
          { key: "pending-review" as const, label: "Results Entered", value: todayResults.length, helper: "Moved to review", tone: "success" as const },
          { key: "ready-to-publish" as const, label: "Reports Published", value: todayReports.length, helper: "Completed", tone: "success" as const },
          { key: "payment-pending" as const, label: "Revenue", value: new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(todayRevenue), helper: "Collected today", tone: "success" as const },
        ],
        sparklines: [
          { key: "sparkline-orders" as const, label: "Orders", subtitle: "7-day trend", series: sevenDaySeries((row) => row.orderedAt), tone: "info" as const },
          { key: "sparkline-reports" as const, label: "Reports Published", subtitle: "7-day trend", series: sevenDaySeries((row) => row.reportPublishedAt), tone: "success" as const },
        ],
      },
    };
  }, [orders, canCollectPayment, canCollectSample, canCreateOrders, canEnterResults, canGenerateReport, canManageTests, canReviewReport, pendingResultsOrders.length, pendingReviewOrders.length]);

  const openSampleDialog = React.useCallback((row: LabOrder) => {
    setSampleTarget(row);
    setCollectedSamples([]);
    setSampleRows((row.samples.length ? row.samples : row.items).map((item) => ({
      labOrderItemId: "labOrderItemId" in item ? item.labOrderItemId : item.id,
      testName: "testName" in item ? item.testName : "Sample",
      specimenType: ("specimenType" in item ? item.specimenType : item.sampleType) || row.sampleType || "",
      containerType: "containerType" in item ? (item.containerType || "") : "",
        notes: ("notes" in item ? item.notes : row.sampleCollectionNotes) || "",
      })));
    setSampleCollectedBy(auth.username || auth.appUserId || "");
    setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
    setSampleCollectionStatus("Collected");
  }, [auth.appUserId, auth.username]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyTestForm);
    setEditorOpen(true);
  };

  const openEdit = (row: LabTest) => {
    setEditing(row);
    setForm({
      testCode: row.testCode,
      testName: row.testName,
      category: row.category,
      department: row.department,
      sampleType: row.sampleType,
      unit: row.unit,
      referenceRange: row.referenceRange,
      turnaroundTime: row.turnaroundTime,
      price: row.price,
      active: row.active,
      parameters: row.parameters.map((parameter, index) => ({
        parameterName: parameter.parameterName,
        unit: parameter.unit || "",
        normalRange: parameter.normalRange || "",
        criticalRange: parameter.criticalRange || "",
        sortOrder: parameter.sortOrder || index + 1,
      })),
    });
    setEditorOpen(true);
  };

  const saveTest = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = labTestMasterSchema.safeParse(form);
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    const payload = {
      testCode: parsed.data.testCode || "",
      testName: parsed.data.testName,
      category: parsed.data.category,
      department: parsed.data.department || null,
      sampleType: parsed.data.sampleType || null,
      unit: parsed.data.unit || null,
      referenceRange: parsed.data.referenceRange || null,
      turnaroundTime: parsed.data.turnaroundTime || null,
      price: parsed.data.price,
      active: parsed.data.active ?? true,
      parameters: parsed.data.parameters.map((parameter) => ({
        parameterName: parameter.parameterName,
        unit: parameter.unit || null,
        normalRange: parameter.normalRange || null,
        criticalRange: parameter.criticalRange || null,
        sortOrder: parameter.sortOrder ?? 1,
      })),
    };
    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await updateLabTest(auth.accessToken, auth.tenantId, editing.id, payload);
      } else {
        await createLabTest(auth.accessToken, auth.tenantId, payload);
      }
      setEditorOpen(false);
      setEditing(null);
      setForm(emptyTestForm);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save lab test");
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (row: LabTest) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setDeactivateTarget(row);
  };

  async function submitDeactivate() {
    if (!auth.accessToken || !auth.tenantId || !deactivateTarget) return;
    setSaving(true);
    setError(null);
    try {
      await deactivateLabTest(auth.accessToken, auth.tenantId, deactivateTarget.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate lab test");
    } finally {
      setSaving(false);
      setDeactivateTarget(null);
    }
  }

  const collectPayment = async () => {
    if (!auth.accessToken || !auth.tenantId || !paymentTarget) return;
    const parsed = labPaymentSchema.safeParse({
      amount: paymentTarget.billDueAmount ?? 0,
      paymentMode,
      referenceNumber: paymentReference.trim() || undefined,
      notes: paymentNotes.trim() || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const savedOrder = await collectLabOrderPayment(auth.accessToken, auth.tenantId, paymentTarget.id, {
        amount: parsed.data.amount,
        paymentMode: parsed.data.paymentMode,
        referenceNumber: parsed.data.referenceNumber || null,
        notes: parsed.data.notes || null,
        receivedBy: auth.appUserId || null,
      });
      if (!savedOrder.billId) {
        throw new Error("Payment completed, but the lab order has no linked bill.");
      }
      if (savedOrder.receiptId && savedOrder.receiptNumber && savedOrder.paymentId) {
        const receipt: Receipt = {
          id: savedOrder.receiptId,
          tenantId: savedOrder.tenantId,
          receiptNumber: savedOrder.receiptNumber,
          billId: savedOrder.billId,
          paymentId: savedOrder.paymentId,
          receiptDate: savedOrder.receiptDate || savedOrder.paymentDate || savedOrder.paymentCollectedAt?.slice(0, 10) || "",
          amount: savedOrder.paymentAmount ?? savedOrder.billDueAmount ?? parsed.data.amount,
          createdAt: savedOrder.updatedAt,
        };
        const payment: Payment = {
          id: savedOrder.paymentId,
          tenantId: savedOrder.tenantId,
          billId: savedOrder.billId,
          paymentDate: savedOrder.paymentDate || savedOrder.paymentCollectedAt?.slice(0, 10) || "",
          paymentDateTime: savedOrder.paymentDateTime || savedOrder.paymentCollectedAt || null,
          amount: savedOrder.paymentAmount ?? parsed.data.amount,
          paymentMode: savedOrder.paymentMode || parsed.data.paymentMode,
          referenceNumber: savedOrder.referenceNumber ?? null,
          notes: parsed.data.notes ?? null,
          receivedBy: savedOrder.receivedBy ?? auth.appUserId ?? null,
          receiptId: savedOrder.receiptId,
          receiptNumber: savedOrder.receiptNumber,
          receiptDate: savedOrder.receiptDate || savedOrder.paymentDate || savedOrder.paymentCollectedAt?.slice(0, 10) || "",
          createdAt: savedOrder.updatedAt,
        };
        setReceiptPreview({ order: savedOrder, receipt, payment });
      } else {
        const [paymentRows, receiptRows] = await Promise.all([
          listBillPayments(auth.accessToken, auth.tenantId, savedOrder.billId),
          listBillReceipts(auth.accessToken, auth.tenantId, savedOrder.billId),
        ]);
        const receipt = latestReceiptForBill(receiptRows, savedOrder.billId);
        const payment = paymentForReceipt(paymentRows, receipt);
        if (!receipt || !payment) {
          throw new Error("Payment completed, but the receipt is not available yet.");
        }
        setReceiptPreview({ order: savedOrder, receipt, payment });
      }
      setPaymentTarget(null);
      setPaymentReference("");
      setPaymentNotes("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect payment");
    } finally {
      setSaving(false);
    }
  };

  const loadReceiptPrintDataFromOrder = async (order: LabOrder, autoPrint = false) => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!order.billId) {
      setReceiptActionError("Payment completed, but the lab bill is not available yet.");
      return;
    }
    setReceiptPrintLoading(true);
    setReceiptPrintAutoPrint(autoPrint);
    setReceiptPrintOpen(true);
    setReceiptPrintData(null);
    setReceiptActionError(null);
    try {
      const bill = buildLabReceiptBill(order);
      const receiptSummary = buildReceiptPaymentSummary(order);
      const paymentMode = receiptSummary?.paymentMode || order.paymentMode || receiptPreview?.payment.paymentMode || "CASH";
      const receiptId = receiptSummary?.receiptId || order.receiptId || receiptPreview?.receipt.id || "";
      const receiptNumber = receiptSummary?.receiptNumber || order.receiptNumber || receiptPreview?.receipt.receiptNumber || "";
      const receiptDate = receiptSummary?.receiptDate || order.receiptDate || order.paymentDate || order.paymentCollectedAt?.slice(0, 10) || receiptPreview?.receipt.receiptDate || "";
      const paymentDate = receiptSummary?.paymentDate || order.paymentDate || order.paymentCollectedAt?.slice(0, 10) || receiptDate;
      const paymentDateTime = receiptSummary?.paymentDateTime || order.paymentDateTime || order.paymentCollectedAt || receiptPreview?.payment.paymentDateTime || null;
      const paymentAmount = receiptSummary?.paymentAmount ?? order.paymentAmount ?? order.billTotalAmount ?? order.billDueAmount ?? receiptPreview?.receipt.amount ?? 0;
      const paymentId = receiptSummary?.paymentId || order.paymentId || receiptPreview?.payment.id || receiptId;
      setReceiptPrintData({
        clinicProfile,
        bill,
        receipt: {
          id: receiptId,
          tenantId: order.tenantId,
          receiptNumber,
          billId: order.billId,
          paymentId,
          receiptDate,
          amount: paymentAmount,
          createdAt: order.updatedAt,
        },
        payment: {
          id: paymentId,
          tenantId: order.tenantId,
          billId: order.billId,
          paymentDate,
          paymentDateTime,
          amount: paymentAmount,
          paymentMode: paymentMode as PaymentMode,
          referenceNumber: receiptSummary?.referenceNumber || order.referenceNumber || null,
          notes: null,
          receivedBy: order.receivedBy || null,
          receivedByLabel: receiptSummary?.receivedBy || null,
          receiptId,
          receiptNumber,
          receiptDate,
          createdAt: order.updatedAt,
        },
        patient: null,
        appointment: null,
        consultation: null,
      });
    } catch (err) {
      setReceiptPrintOpen(false);
      setReceiptActionError(err instanceof Error ? err.message : "Failed to load receipt preview");
    } finally {
      setReceiptPrintLoading(false);
    }
  };

  const loadReceiptPrintData = async (autoPrint = false) => {
    if (!receiptPreview) return;
    await loadReceiptPrintDataFromOrder(receiptPreview.order, autoPrint);
  };

  const openReceiptPdf = async () => {
    if (!auth.accessToken || !auth.tenantId || !receiptPreview) return;
    setReceiptActionLoading(true);
    setReceiptActionError(null);
    try {
      const { blob, filename } = await getReceiptPdf(auth.accessToken, auth.tenantId, receiptPreview.receipt.id);
      if (!blob.size) {
        throw new Error("Receipt PDF is empty.");
      }
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = filename || `${receiptPreview.receipt.receiptNumber || receiptPreview.order.orderNumber}-receipt.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (err) {
      setReceiptActionError(err instanceof Error ? err.message : "Failed to download receipt PDF");
    } finally {
      setReceiptActionLoading(false);
    }
  };

  const openOrderReceiptPreview = async (order: LabOrder, autoPrint = false) => {
    await loadReceiptPrintDataFromOrder(order, autoPrint);
  };

  const downloadOrderReceipt = async (order: LabOrder) => {
    const receiptSummary = buildReceiptPaymentSummary(order);
    const receiptId = receiptSummary?.receiptId || order.receiptId;
    if (!auth.accessToken || !auth.tenantId || !receiptId) return;
    setReceiptActionLoading(true);
    setReceiptActionError(null);
    try {
      const { blob, filename } = await getReceiptPdf(auth.accessToken, auth.tenantId, receiptId);
      if (!blob.size) {
        throw new Error("Receipt PDF is empty.");
      }
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = filename || `${receiptSummary?.receiptNumber || order.receiptNumber || order.orderNumber}-receipt.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (err) {
      setReceiptActionError(err instanceof Error ? err.message : "Failed to download receipt PDF");
    } finally {
      setReceiptActionLoading(false);
    }
  };

  const sendReceiptAction = async (channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId || !receiptPreview) return;
    setReceiptActionLoading(true);
    setError(null);
    try {
      await sendReceipt(auth.accessToken, auth.tenantId, receiptPreview.receipt.id, channel);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  };

  const collectSample = async () => {
    if (!auth.accessToken || !auth.tenantId || !sampleTarget) return;
    const collectedAt = toIsoFromDatetimeLocal(sampleCollectedAt);
    if (!collectedAt) {
      setError("Collection date/time is required.");
      return;
    }
    const invalidRow = sampleRows.find((row) => !row.specimenType.trim());
    if (invalidRow) {
      setError(`Specimen type is required for ${invalidRow.testName}.`);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const savedSamples = await collectLabOrderSamples(auth.accessToken, auth.tenantId, sampleTarget.id, {
        samples: sampleRows.map((row) => ({
          labOrderItemId: row.labOrderItemId,
          specimenType: row.specimenType.trim(),
          containerType: row.containerType.trim() || null,
          collectedAt,
          notes: row.notes.trim() || null,
        })),
      });
      setCollectedSamples(savedSamples);
      setSampleCollectedBy("");
      setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
      setSampleCollectionStatus("Collected");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect sample");
    } finally {
      setSaving(false);
    }
  };

  const receiveSample = async () => {
    if (!auth.accessToken || !auth.tenantId || !receiveTarget) return;
    setSaving(true);
    setError(null);
    try {
      await receiveLabSample(auth.accessToken, auth.tenantId, receiveTarget.id, {
        receivedAt: new Date().toISOString(),
        receivedBy: auth.appUserId || null,
      });
      setReceiveTarget(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to receive sample");
    } finally {
      setSaving(false);
    }
  };

  const rejectSample = async () => {
    if (!auth.accessToken || !auth.tenantId || !rejectTarget) return;
    if (!rejectReason.trim()) {
      setError("Rejection reason is required.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await rejectLabSample(auth.accessToken, auth.tenantId, rejectTarget.id, {
        rejectionReason: rejectReason.trim(),
        recollectionRequired,
        notes: rejectNotes.trim() || null,
      });
      setRejectTarget(null);
      setRejectReason("");
      setRejectNotes("");
      setRecollectionRequired(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject sample");
    } finally {
      setSaving(false);
    }
  };

  const resultDraftStorageKey = React.useCallback((orderId: string) => {
    const tenant = auth.tenantId || "unknown-tenant";
    return `lab.result-entry.draft.${tenant}.${orderId}`;
  }, [auth.tenantId]);

  const readResultDraft = React.useCallback((orderId: string) => {
    if (!orderId || typeof window === "undefined") return null;
    try {
      const raw = window.localStorage.getItem(resultDraftStorageKey(orderId));
      if (!raw) return null;
      return JSON.parse(raw) as { comments: string; items: ResultItemForm[]; updatedAt: string };
    } catch {
      return null;
    }
  }, [resultDraftStorageKey]);

  const clearResultDraft = React.useCallback((orderId: string) => {
    if (!orderId || typeof window === "undefined") return;
    try {
      window.localStorage.removeItem(resultDraftStorageKey(orderId));
    } catch {
      // ignore
    }
  }, [resultDraftStorageKey]);

  const saveResultDraft = React.useCallback((order: LabOrder, comments: string, items: ResultItemForm[]) => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(resultDraftStorageKey(order.id), JSON.stringify({
        comments,
        items,
        updatedAt: new Date().toISOString(),
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save result draft");
    }
  }, [resultDraftStorageKey]);

  const closeAttachmentPreview = React.useCallback(() => {
    setResultAttachmentPreview((current) => {
      if (current?.blobUrl) {
        window.URL.revokeObjectURL(current.blobUrl);
      }
      return null;
    });
  }, []);

  React.useEffect(() => () => {
    closeAttachmentPreview();
  }, [closeAttachmentPreview]);

  const openResultsDialog = (row: LabOrder) => {
    setResultTarget(row);
    const draft = readResultDraft(row.id);
    if (draft) {
      setResultComments(draft.comments || "");
      setResultItems(draft.items.length ? draft.items : row.items.map((item) => defaultResultsForItem(item, row.results)));
      setResultDraftLoaded(true);
      setResultSaveMessage(`Draft loaded for ${row.orderNumber}.`);
    } else {
      setResultComments(row.resultComments || "");
      setResultItems(row.items.map((item) => defaultResultsForItem(item, row.results)));
      setResultDraftLoaded(false);
      setResultSaveMessage(null);
    }
  };

  const openReviewDialog = (row: LabOrder) => {
    setReviewTarget(row);
    setReviewDecision((row.doctorReviewDecision as "APPROVE" | "SEND_BACK" | null) || "APPROVE");
    setReviewReason(row.doctorReviewReason || "");
    setReviewComments(row.doctorComments || "");
  };

  const saveResults = async () => {
    if (!auth.accessToken || !auth.tenantId || !resultTarget) return;
    const parsed = labResultEntrySchema.safeParse({
      comments: resultComments.trim() || undefined,
      items: resultItems.map((item) => ({
        labOrderItemId: item.labOrderItemId,
        resultValue: item.resultValue.trim() || undefined,
        unit: item.unit.trim() || undefined,
        referenceRange: item.referenceRange.trim() || undefined,
        componentResults: item.componentResults.map((component) => ({
          parameterName: component.parameterName.trim() || undefined,
          componentName: component.componentName.trim() || undefined,
          resultValue: component.resultValue.trim() || undefined,
          unit: component.unit.trim() || undefined,
          referenceRange: component.referenceRange.trim() || undefined,
        })),
      })),
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await enterLabOrderResults(auth.accessToken, auth.tenantId, resultTarget.id, {
        comments: parsed.data.comments || null,
        items: parsed.data.items.map((item) => ({
          labOrderItemId: item.labOrderItemId,
          resultValue: item.resultValue || null,
          unit: item.unit || null,
          referenceRange: item.referenceRange || null,
          componentResults: item.componentResults.map((component) => ({
            parameterName: component.parameterName || null,
            componentName: component.componentName || null,
            resultValue: component.resultValue || null,
            unit: component.unit || null,
            referenceRange: component.referenceRange || null,
          })),
        })),
      });
      clearResultDraft(resultTarget.id);
      setResultTarget(null);
      setResultComments("");
      setResultItems([]);
      setResultDraftLoaded(false);
      setResultSaveMessage("Results saved. Order moved to Pending Lab Review.");
      setTab(3);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save results");
    } finally {
      setSaving(false);
    }
  };

  const saveResultsDraft = () => {
    if (!resultTarget) return;
    setResultSavingDraft(true);
    try {
      saveResultDraft(resultTarget, resultComments, resultItems);
      setResultDraftLoaded(true);
      setResultSaveMessage(`Draft saved for ${resultTarget.orderNumber}.`);
    } finally {
      setResultSavingDraft(false);
    }
  };

  const openAttachmentPreview = async (attachment: LabOrder["attachments"][number]) => {
    if (!resultTarget || !auth.accessToken || !auth.tenantId) return;
    setResultAttachmentLoading(true);
    try {
      const preview = await getLabOrderAttachmentBlob(auth.accessToken, auth.tenantId, resultTarget.id, attachment.id, true);
      if (resultAttachmentPreview?.blobUrl) {
        window.URL.revokeObjectURL(resultAttachmentPreview.blobUrl);
      }
      const blobUrl = window.URL.createObjectURL(preview.blob);
      setResultAttachmentPreview({
        attachment,
        blobUrl,
        mediaType: preview.mediaType,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to preview attachment");
    } finally {
      setResultAttachmentLoading(false);
    }
  };

  const downloadAttachment = async (attachment: LabOrder["attachments"][number]) => {
    if (!resultTarget || !auth.accessToken || !auth.tenantId) return;
    setResultAttachmentLoading(true);
    try {
      const preview = await getLabOrderAttachmentBlob(auth.accessToken, auth.tenantId, resultTarget.id, attachment.id, false);
      const objectUrl = window.URL.createObjectURL(preview.blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = preview.filename || attachment.originalFilename || attachment.id;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download attachment");
    } finally {
      setResultAttachmentLoading(false);
    }
  };

  const saveReview = async () => {
    if (!auth.accessToken || !auth.tenantId || !reviewTarget) return;
    const parsed = labDoctorReviewSchema.safeParse({
      decision: reviewDecision,
      reason: reviewReason.trim() || undefined,
      remarks: reviewComments.trim() || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await verifyLabOrder(auth.accessToken, auth.tenantId, reviewTarget.id, {
        decision: parsed.data.decision,
        reason: parsed.data.reason || null,
        comments: parsed.data.remarks || null,
      });
      setReviewTarget(null);
      setReviewDecision("APPROVE");
      setReviewReason("");
      setReviewComments("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to review lab report");
    } finally {
      setSaving(false);
    }
  };

  const openPublishDialog = (row: LabOrder) => {
    setPublishTarget(row);
    setPublishNotes("");
    setPublishChannels([
      "PATIENT_PORTAL",
      ...(row.requestedByInternalDoctorId || row.consultationId ? ["DOCTOR_NOTIFICATION"] : []),
    ]);
  };

  const savePublish = async () => {
    if (!auth.accessToken || !auth.tenantId || !publishTarget) return;
    setSaving(true);
    setError(null);
    try {
      const published = await publishLabOrderReport(auth.accessToken, auth.tenantId, publishTarget.id, {
        deliveryChannels: publishChannels,
        publishNotes: publishNotes.trim() || null,
      });
      setPublishTarget(null);
      setPublishNotes("");
      setPublishChannels(["PATIENT_PORTAL"]);
      setPublishSuccessTarget(published);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish lab report");
    } finally {
      setSaving(false);
    }
  };

  const recordAndOpenLabReport = async (row: LabOrder, action: string, mode: "view" | "print" | "download") => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const pdf = await getLabOrderPdf(auth.accessToken, auth.tenantId, row.id);
      await recordLabOrderReportDeliveryAction(auth.accessToken, auth.tenantId, row.id, { action, channel: mode === "print" ? "PRINT" : mode === "download" ? "DOWNLOAD" : "PATIENT_PORTAL" });
      if (mode === "download") {
        const anchor = document.createElement("a");
        const objectUrl = window.URL.createObjectURL(pdf.blob);
        anchor.href = objectUrl;
        anchor.download = pdf.filename || `${row.orderNumber}-lab-report.pdf`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
      } else if (mode === "print") {
        const objectUrl = window.URL.createObjectURL(pdf.blob);
        const printWindow = window.open(objectUrl, "_blank");
        if (printWindow) {
          printWindow.addEventListener("load", () => {
            printWindow.focus();
            printWindow.print();
          });
          window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
        } else {
          openPdf(pdf.blob);
        }
      } else {
        openPdf(pdf.blob);
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open lab report");
    } finally {
      setSaving(false);
    }
  };

  const viewReport = (row: LabOrder) => void recordAndOpenLabReport(row, "lab_order.report_viewed", "view");
  const printReport = (row: LabOrder) => void recordAndOpenLabReport(row, "lab_order.report_printed", "print");
  const downloadReport = (row: LabOrder) => void recordAndOpenLabReport(row, "lab_order.report_downloaded", "download");
  const emailReport = async (row: LabOrder) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await recordLabOrderReportDeliveryAction(auth.accessToken, auth.tenantId, row.id, { action: "lab_order.report_email_sent", channel: "EMAIL" });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record email delivery");
    } finally {
      setSaving(false);
    }
  };
  const whatsappReport = async (row: LabOrder) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await recordLabOrderReportDeliveryAction(auth.accessToken, auth.tenantId, row.id, { action: "lab_order.report_whatsapp_sent", channel: "WHATSAPP" });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record WhatsApp delivery");
    } finally {
      setSaving(false);
    }
  };
  const shareReportLink = async (row: LabOrder) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const link = `${window.location.origin}/api/lab/orders/${row.id}/pdf`;
      await copyTextToClipboard(link);
      await recordLabOrderReportDeliveryAction(auth.accessToken, auth.tenantId, row.id, { action: "lab_order.report_shared_link", channel: "SHARE_LINK", notes: link });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to copy report link");
    } finally {
      setSaving(false);
    }
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const downloadImportTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const csv = await getLabTestImportTemplate(auth.accessToken, auth.tenantId);
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "lab-tests-import-template.csv";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download import template");
    }
  };

  const handleImportCsv = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !auth.accessToken || !auth.tenantId) return;
    try {
      setSaving(true);
      setError(null);
      const result = await importLabTestsCsv(auth.accessToken, auth.tenantId, file);
      setImportResult(result);
      setImportOpen(true);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import lab tests");
    } finally {
      setSaving(false);
    }
  };

  const openRequestDialog = () => {
    setRequestForm({
      ...emptyLabRequestForm(),
      orderOrigin: "WALK_IN",
    });
    setQuickRegisterOpen(false);
    setSelectedPatient(null);
    setPatientQuery("");
    setPatientOptions([]);
    setPatientOptionsLoaded(false);
    setRequestOpen(true);
  };

  const handleDashboardAction = (key: LabDashboardActionKey) => {
    const paymentKeys = new Set<LabDashboardActionKey>([
      "new-orders",
      "payment-pending",
      "work-pending-payment",
      "work-walk-in-orders-today",
      "work-new-orders",
      "work-published-today",
      "work-ready-to-publish",
      "published-today",
    ]);
    const sampleKeys = new Set<LabDashboardActionKey>([
      "quick-collect",
      "pending-collection",
      "work-pending-sample-collection",
      "work-recollection-required",
      "work-sample-rejected",
      "sample-rejected",
      "recollection-required",
    ]);
    const queueKeys = new Set<LabDashboardActionKey>([
      "quick-enter-results",
      "work-queue",
      "work-work-queue",
      "work-samples-collected",
      "work-results-pending-entry",
      "work-tat-breached",
      "critical-results",
      "work-critical-results",
    ]);
    const reviewKeys = new Set<LabDashboardActionKey>([
      "quick-verify",
      "quick-publish",
      "pending-review",
      "ready-to-publish",
      "work-pending-lab-review",
    ]);

    if (key === "quick-new-order") {
      openRequestDialog();
      return;
    }
    if (key === "quick-collect-payment" || paymentKeys.has(key)) {
      setTab(4);
      return;
    }
    if (sampleKeys.has(key)) {
      setTab(1);
      return;
    }
    if (queueKeys.has(key)) {
      setTab(canEnterResults ? 2 : 3);
      return;
    }
    if (reviewKeys.has(key)) {
      setTab(3);
      return;
    }
    if (key === "quick-collect") {
      setTab(1);
    }
  };

  const saveCategoryConfig = async (code: string, patch: { displayName?: string | null; active?: boolean | null; displayOrder?: number | null }) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    try {
      await updateLabCategoryConfig(auth.accessToken, auth.tenantId, code, patch);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const saveTestConfig = async (id: string, patch: { enabled?: boolean | null; active?: boolean | null; tenantPriceOverride?: number | null; tenantTatOverride?: string | null; displayOrder?: number | null }) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    try {
      await updateLabTestConfig(auth.accessToken, auth.tenantId, id, patch);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const readOnlyAnalyticsRole = !canCreateOrders && !canCollectPayment && !canCollectSample && !canEnterResults && !canReviewReport && !canGenerateReport && !canManageTests;
  const analyticsExpandedByDefault = canManageTests || readOnlyAnalyticsRole;

  const onQuickPatientCreated = (patient: Patient) => {
    setSelectedPatient(patient);
    setRequestForm((current) => ({ ...current, patientId: patient.id }));
    setPatientQuery(patientSummary(patient));
    setPatientOptions([patient]);
    setPatientOptionsLoaded(true);
    setQuickRegisterOpen(false);
  };

  const submitLabRequest = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = labOrderCreateSchema.safeParse({
      patientId: requestForm.patientId,
      orderOrigin: requestForm.orderOrigin,
      requestedByInternalDoctorId: requestForm.requestedByInternalDoctorId || undefined,
      externalDoctorName: requestForm.externalDoctorName || undefined,
      externalDoctorMobile: requestForm.externalDoctorMobile || undefined,
      externalClinicName: requestForm.externalClinicName || undefined,
      referralSource: requestForm.referralSource || undefined,
      testIds: requestForm.testIds,
      notes: requestForm.notes || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createLabOrder(auth.accessToken, auth.tenantId, {
        patientId: parsed.data.patientId,
        orderOrigin: parsed.data.orderOrigin,
        requestedByInternalDoctorId: parsed.data.requestedByInternalDoctorId || null,
        externalDoctorName: parsed.data.externalDoctorName || null,
        externalDoctorMobile: parsed.data.externalDoctorMobile || null,
        externalClinicName: parsed.data.externalClinicName || null,
        referralSource: parsed.data.referralSource || null,
        testIds: parsed.data.testIds,
        notes: parsed.data.notes || null,
      });
      setRequestOpen(false);
      setRequestForm(emptyLabRequestForm());
      setSelectedPatient(null);
      setPatientQuery("");
      setPatientOptions([]);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create lab request");
    } finally {
      setSaving(false);
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (loading) {
    return (
      <Box sx={{ minHeight: 240, display: "grid", placeItems: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={2.25}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start", flexWrap: "wrap" }}>
        <Box>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
            <Typography variant="h4" sx={{ fontWeight: 900 }}>Laboratory</Typography>
            <Chip size="small" color={consultationEnabled ? "primary" : "default"} label={labBadgeLabel} />
          </Stack>
          <Typography variant="body2" color="text.secondary">{pageSubtitle}</Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {canManageTests ? <Button variant="outlined" onClick={() => void downloadImportTemplate()} disabled={saving}>Download CSV Template</Button> : null}
          {canManageTests ? <Button variant="outlined" onClick={openImportFilePicker} disabled={saving}>Import CSV</Button> : null}
          {canManageTests ? <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>New Test</Button> : null}
          {canCreateOrders ? <Button variant="outlined" onClick={openRequestDialog} startIcon={<ScienceRoundedIcon />}>{primaryRegistrationLabel}</Button> : null}
        </Stack>
      </Box>

      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImportCsv} />

      {error ? <Alert severity="error">{error}</Alert> : null}
      {resultSaveMessage && !resultTarget ? <Alert severity="success">{resultSaveMessage}</Alert> : null}

      <LabDashboard
        permissions={{
          canCreateOrders,
          canCollectPayment,
          canCollectSample,
          canEnterResults,
          canReviewReport,
          canGenerateReport,
          canManageTests,
        }}
        data={dashboardData}
        onAction={handleDashboardAction}
      />

      <Card variant="outlined" sx={{ boxShadow: "none" }}>
        <CardContent sx={compactCardContentSx}>
          <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ mb: 1 }}>
            <Tab label="Catalog" />
            <Tab label="Pending Sample Collection" />
            <Tab label="Work Queue / Result Entry" />
            <Tab label="Pending Lab Review" />
            <Tab label="Orders" />
            {canManageTests ? <Tab label="Lab Configuration" /> : null}
          </Tabs>

          {tab === 0 ? (
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ alignItems: { xs: "stretch", md: "center" } }}>
                <TextField fullWidth size="small" label="Search tests" value={search} onChange={(e) => setSearch(e.target.value.slice(0, 60))} inputProps={{ maxLength: 60 }} />
                <Button variant="outlined" onClick={openCreate} disabled={!canManageTests} startIcon={<AddRoundedIcon />}>Add test</Button>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                Showing first 50 tests. Use search to find more.
              </Typography>
              <Box sx={{ display: "flex", gap: 0.75, overflowX: "auto", pb: 0.25, flexWrap: "nowrap" }}>
                {categories.map((category) => <Chip key={category} size="small" label={category} variant="outlined" />)}
              </Box>
              {!filteredTests.length ? (
                <CompactEmptyState title="No lab tests found" subtitle="Create the first lab test to start ordering." action={canManageTests ? <Button variant="contained" onClick={openCreate}>Create test</Button> : null} />
              ) : (
                <Box sx={{ maxHeight: 520, overflow: "auto" }}>
                  <Table size="small" stickyHeader sx={{ minWidth: 900 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Code</TableCell>
                        <TableCell>Name</TableCell>
                        <TableCell>Category</TableCell>
                        <TableCell>Sample</TableCell>
                        <TableCell>Price</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {visibleCatalogTests.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell sx={{ fontWeight: 700 }}>{row.testCode}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.testName}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.department || row.turnaroundTime || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.category}</TableCell>
                          <TableCell>{row.sampleType || "-"}</TableCell>
                          <TableCell>{formatMoney(row.price)}</TableCell>
                          <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} variant="outlined" /></TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              {canManageTests ? <Button size="small" variant="outlined" startIcon={<EditRoundedIcon />} onClick={() => openEdit(row)}>Edit</Button> : null}
                              {canManageTests && row.active ? <Button size="small" variant="text" onClick={() => void deactivate(row)}>Deactivate</Button> : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Stack>
          ) : tab === 1 ? (
            <OrderQueue
              rows={pendingSampleOrders}
              emptySubtitle="Paid lab registrations ready for sample collection will appear here."
              actionDisabled={saving}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              canAccessLabReceipts={canAccessLabReceipts}
              onViewReceipt={(row) => void openOrderReceiptPreview(row)}
              onPrintReceipt={(row) => void openOrderReceiptPreview(row, true)}
              onDownloadReceipt={(row) => void downloadOrderReceipt(row)}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={openSampleDialog}
              onReceiveSample={setReceiveTarget}
              onRejectSample={(sample) => {
                setRejectTarget(sample);
                setRejectReason(sample.rejectionReason || "");
                setRejectNotes(sample.notes || "");
                setRecollectionRequired(sample.recollectionRequired);
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onPublishReport={openPublishDialog}
              onViewReport={viewReport}
              onPrintReport={printReport}
              onDownloadReport={downloadReport}
              onEmailReport={emailReport}
              onWhatsappReport={whatsappReport}
              onShareReportLink={shareReportLink}
            />
          ) : tab === 2 ? (
            <OrderQueue
              rows={pendingResultsOrders}
              emptySubtitle="Collected or received samples ready for technician result entry will appear here."
              actionDisabled={saving}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              canAccessLabReceipts={canAccessLabReceipts}
              onViewReceipt={(row) => void openOrderReceiptPreview(row)}
              onPrintReceipt={(row) => void openOrderReceiptPreview(row, true)}
              onDownloadReceipt={(row) => void downloadOrderReceipt(row)}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={openSampleDialog}
              onReceiveSample={setReceiveTarget}
              onRejectSample={(sample) => {
                setRejectTarget(sample);
                setRejectReason(sample.rejectionReason || "");
                setRejectNotes(sample.notes || "");
                setRecollectionRequired(sample.recollectionRequired);
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onPublishReport={openPublishDialog}
              onViewReport={viewReport}
              onPrintReport={printReport}
              onDownloadReport={downloadReport}
              onEmailReport={emailReport}
              onWhatsappReport={whatsappReport}
              onShareReportLink={shareReportLink}
            />
          ) : tab === 3 ? (
            <OrderQueue
              rows={pendingReviewOrders}
              emptySubtitle="Entered results waiting for lab verification will appear here."
              actionDisabled={saving}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              canAccessLabReceipts={canAccessLabReceipts}
              onViewReceipt={(row) => void openOrderReceiptPreview(row)}
              onPrintReceipt={(row) => void openOrderReceiptPreview(row, true)}
              onDownloadReceipt={(row) => void downloadOrderReceipt(row)}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={openSampleDialog}
              onReceiveSample={setReceiveTarget}
              onRejectSample={(sample) => {
                setRejectTarget(sample);
                setRejectReason(sample.rejectionReason || "");
                setRejectNotes(sample.notes || "");
                setRecollectionRequired(sample.recollectionRequired);
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onPublishReport={openPublishDialog}
              onViewReport={viewReport}
              onPrintReport={printReport}
              onDownloadReport={downloadReport}
              onEmailReport={emailReport}
              onWhatsappReport={whatsappReport}
              onShareReportLink={shareReportLink}
            />
          ) : tab === 4 ? (
            <Stack spacing={2}>
              <TextField
                fullWidth
                size="small"
                label="Search orders"
                value={orderSearch}
                onChange={(e) => setOrderSearch(e.target.value.slice(0, 60))}
                helperText="Search by order number, patient, doctor, or test."
                inputProps={{ maxLength: 60 }}
              />
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {visibleStatusFilters.map((status) => (
                  <Chip
                    key={status}
                    clickable
                    label={status.replaceAll("_", " ")}
                    color={statusFilter === status ? "primary" : "default"}
                    variant={statusFilter === status ? "filled" : "outlined"}
                    onClick={() => setStatusFilter(status)}
                  />
                ))}
              </Stack>
              {!filteredOrders.length ? (
                <CompactEmptyState title="No lab orders found" subtitle="Lab registrations and orders will appear here." />
              ) : (
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Order</TableCell>
                        <TableCell>Patient</TableCell>
                        <TableCell>Source</TableCell>
                        <TableCell>Tests</TableCell>
                        <TableCell>Sample</TableCell>
                        <TableCell>Bill</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredOrders.map((row) => {
                        const sampleAction = actionableSample(row);
                        return (
                        <TableRow key={row.id}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.orderNumber}</Typography>
                              <Typography variant="caption" color="text.secondary">{new Date(row.orderedAt).toLocaleString()}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{formatOrderOrigin(row.orderOrigin)}</Typography>
                              <Typography variant="caption" color="text.secondary">Requested by: {requestedByLabel(row)}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.items.length} tests</Typography>
                              <Typography variant="caption" color="text.secondary">{row.results.length ? `${row.results.length} result rows` : "No results yet"}</Typography>
                              {row.results.length ? (
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {row.results.slice(0, 3).map((result, resultIndex) => (
                                    <Chip
                                      key={`${row.id}-result-${resultIndex}`}
                                      size="small"
                                      label={`${result.parameterName || result.componentName || result.testName}: ${result.resultFlag || "NORMAL"}`}
                                      color={resultTone(result.resultFlag)}
                                      variant="outlined"
                                    />
                                  ))}
                                </Stack>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Chip size="small" label={sampleSummaryLabel(row)} color={sampleStatusTone(row.sampleSummaryStatus)} variant="outlined" />
                              <Typography variant="caption" color="text.secondary">{row.sampleAccessionNumber || "Accession pending"}</Typography>
                              {row.sampleBarcodeValue ? <Typography variant="caption" color="text.secondary">Barcode: {row.sampleBarcodeValue}</Typography> : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.billNumber || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.billDueAmount != null ? `Due ${formatMoney(row.billDueAmount)}` : row.billStatus || "-"}</Typography>
                              {(() => {
                                const receiptSummary = buildReceiptPaymentSummary(row);
                                return canAccessLabReceipts && receiptSummary ? (
                                <Stack spacing={0.25} sx={{ pt: 0.5 }}>
                                  <Typography variant="caption" color="text.secondary">
                                    Receipt: {receiptSummary.receiptNumber || "-"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    Paid {formatMoney(receiptSummary.paymentAmount ?? row.billTotalAmount ?? row.billDueAmount ?? 0)} • {receiptSummary.paymentMode || "-"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    Ref: {receiptSummary.referenceNumber || "—"} • By {receiptSummary.receivedBy || "Staff User"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {receiptSummary.paymentDateTime ? formatDateTime(receiptSummary.paymentDateTime) : receiptTimestampText(row)}
                                  </Typography>
                                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                    <Button size="small" variant="text" onClick={() => void openOrderReceiptPreview(row)}>
                                      View Receipt
                                    </Button>
                                    <Button size="small" variant="text" onClick={() => void openOrderReceiptPreview(row, true)}>
                                      Print Receipt
                                    </Button>
                                    <Button size="small" variant="text" onClick={() => void downloadOrderReceipt(row)}>
                                      Download PDF
                                    </Button>
                                  </Stack>
                                </Stack>
                                ) : row.status === "PAID" || (row.billDueAmount != null && row.billDueAmount <= 0) ? (
                                <Typography variant="caption" color="text.secondary">
                                  Paid - receipt details unavailable
                                </Typography>
                                ) : null;
                              })()}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Chip size="small" label={row.status.replaceAll("_", " ")} color={statusTone(row.status)} variant="outlined" />
                              {row.reportDeliveryStatus ? (
                                <Typography variant="caption" color="text.secondary">
                                  Delivery: {row.reportDeliveryStatus.replaceAll("_", " ")}{row.reportDeliveryChannels?.length ? ` • ${deliveryChannelsLabel(row.reportDeliveryChannels)}` : ""}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                              {canCollectPayment && row.billDueAmount != null && row.billDueAmount > 0 ? (
                                <Button size="small" variant="contained" startIcon={<PaidRoundedIcon />} onClick={() => {
                                  setPaymentTarget(row);
                                  setPaymentMode("CASH");
                                  setPaymentReference("");
                                  setPaymentNotes("");
                                }}>
                                  Collect
                                </Button>
                              ) : null}
                              {canCollectSample && row.status === "READY_FOR_COLLECTION" ? (
                                <Button size="small" variant="outlined" startIcon={<ScienceRoundedIcon />} onClick={() => openSampleDialog(row)}>
                                  Collect sample
                                </Button>
                              ) : null}
                              {canCollectSample && sampleAction?.status === "COLLECTED" ? (
                                <Button size="small" variant="outlined" onClick={() => setReceiveTarget(sampleAction)}>
                                  Receive
                                </Button>
                              ) : null}
                              {canCollectSample && sampleAction ? (
                                <Button size="small" variant="outlined" color="error" onClick={() => {
                                  setRejectTarget(sampleAction);
                                  setRejectReason(sampleAction.rejectionReason || "");
                                  setRejectNotes(sampleAction.notes || "");
                                  setRecollectionRequired(sampleAction.recollectionRequired);
                                }}>
                                  Reject
                                </Button>
                              ) : null}
                              {canEnterResults && (row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED") ? (
                                <Button size="small" variant="outlined" onClick={() => openResultsDialog(row)}>Enter results</Button>
                              ) : null}
                              {canGenerateReport && row.status === "REPORT_READY" ? (
                                <Button size="small" variant="outlined" onClick={() => openPublishDialog(row)}>
                                  Publish report
                                </Button>
                              ) : null}
                              {canGenerateReport && (row.status === "DOCTOR_REVIEWED" || row.status === "REPORT_GENERATED" || row.status === "DELIVERED") ? (
                                <ReportActionMenuButton
                                  row={row}
                                  disabled={saving}
                                  onViewReport={viewReport}
                                  onPrintReport={printReport}
                                  onDownloadReport={downloadReport}
                                  onEmailReport={emailReport}
                                  onWhatsappReport={whatsappReport}
                                  onShareLink={shareReportLink}
                                />
                              ) : null}
                              {canReviewReport && row.status === "RESULT_ENTERED" ? (
                                <Button size="small" variant="outlined" onClick={() => openReviewDialog(row)}>
                                  Review
                                </Button>
                              ) : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                      })}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Stack>
          ) : canManageTests && tab === 5 ? (
            <LabConfigurationPanel
              categories={categoryConfigs}
              tests={testConfigs}
              saving={saving}
              onSaveCategory={saveCategoryConfig}
              onSaveTest={saveTestConfig}
            />
          ) : null}
        </CardContent>
      </Card>

      <LabAnalyticsPanel data={dashboardData.analytics} defaultExpanded={analyticsExpandedByDefault} onAction={handleDashboardAction} />

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Lab Test" : "New Lab Test"}</DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ pt: 0.5 }}>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Test Code" value={form.testCode} onChange={(e) => setForm((current) => ({ ...current, testCode: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 5 }}><TextField fullWidth label={<RequiredLabel text="Test Name" required />} value={form.testName} onChange={(e) => setForm((current) => ({ ...current, testName: e.target.value }))} required /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <FormControl fullWidth>
                <InputLabel id="lab-category-label"><RequiredLabel text="Category" required /></InputLabel>
                <Select labelId="lab-category-label" label="Category" value={form.category} onChange={(e) => setForm((current) => ({ ...current, category: String(e.target.value) }))}>
                  {categories.length ? categories.map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>) : ["HEMATOLOGY", "BIOCHEMISTRY", "MICROBIOLOGY", "PATHOLOGY", "RADIOLOGY", "CARDIOLOGY", "IMMUNOLOGY", "SEROLOGY", "ENDOCRINOLOGY", "VIROLOGY", "MOLECULAR", "CYTOLOGY", "HISTOPATHOLOGY", "OTHER"].map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Department" value={form.department || ""} onChange={(e) => setForm((current) => ({ ...current, department: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Sample Type" value={form.sampleType || ""} onChange={(e) => setForm((current) => ({ ...current, sampleType: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Unit" value={form.unit || ""} onChange={(e) => setForm((current) => ({ ...current, unit: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Reference Range" value={form.referenceRange || ""} onChange={(e) => setForm((current) => ({ ...current, referenceRange: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Turnaround Time" value={form.turnaroundTime || ""} onChange={(e) => setForm((current) => ({ ...current, turnaroundTime: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth type="number" label={<RequiredLabel text="Price" required />} value={form.price} onChange={(e) => setForm((current) => ({ ...current, price: Number(e.target.value) }))} required /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth label="Status" value={form.active ? "Active" : "Inactive"} disabled /></Grid>
          </Grid>
          <Stack spacing={1.5} sx={{ mt: 3 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Parameters</Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={() => setForm((current) => ({
                  ...current,
                  parameters: [...(current.parameters || []), { parameterName: "", unit: "", normalRange: "", criticalRange: "", sortOrder: (current.parameters?.length || 0) + 1 }],
                }))}
              >
                Add parameter
              </Button>
            </Box>
            {!form.parameters?.length ? (
              <Alert severity="info">Add parameter rows for tests such as CBC or other multi-parameter panels.</Alert>
            ) : (
              <Stack spacing={1}>
                {form.parameters.map((parameter, index) => (
                  <Grid container spacing={1} key={`${parameter.parameterName || "parameter"}-${index}`}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="Parameter Name" value={parameter.parameterName} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, parameterName: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" label="Unit" value={parameter.unit || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, unit: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField fullWidth size="small" label="Normal Range" value={parameter.normalRange || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, normalRange: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" label="Critical Range" value={parameter.criticalRange || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, criticalRange: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                      <IconButton size="small" onClick={() => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.filter((_, rowIndex) => rowIndex !== index),
                      }))}>
                        <Typography variant="caption">x</Typography>
                      </IconButton>
                    </Grid>
                  </Grid>
                ))}
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditorOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveTest()} disabled={saving}>{editing ? "Update" : "Create"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(paymentTarget)} onClose={() => setPaymentTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Collect Payment</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {paymentTarget ? `Collect ${formatMoney(paymentTarget.billDueAmount)} for ${paymentTarget.orderNumber}` : ""}
            </Alert>
            <FormControl fullWidth>
              <InputLabel id="payment-mode-label">Payment Mode</InputLabel>
                <Select labelId="payment-mode-label" label="Payment Mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value as PaymentMode)}>
                  <MenuItem value="CASH">Cash</MenuItem>
                  <MenuItem value="UPI">UPI</MenuItem>
                  <MenuItem value="CARD">Card</MenuItem>
                  <MenuItem value="INSURANCE">Insurance</MenuItem>
                  <MenuItem value="CHEQUE">Cheque</MenuItem>
                  <MenuItem value="BANK_TRANSFER">Bank Transfer</MenuItem>
                  <MenuItem value="OTHER">Other</MenuItem>
                </Select>
            </FormControl>
            <TextField label="Reference Number" value={paymentReference} onChange={(e) => setPaymentReference(e.target.value.slice(0, 60))} inputProps={{ maxLength: 60 }} />
            <TextField label="Notes" value={paymentNotes} onChange={(e) => setPaymentNotes(e.target.value.slice(0, 250))} multiline minRows={2} inputProps={{ maxLength: 250 }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void collectPayment()} disabled={saving || !paymentTarget}>Collect Payment</Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(receiptPreview)}
        onClose={() => {
          setReceiptPreview(null);
          setReceiptPrintOpen(false);
          setReceiptPrintLoading(false);
          setReceiptPrintAutoPrint(false);
          setReceiptPrintData(null);
          setReceiptActionError(null);
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Lab Payment Receipt</DialogTitle>
        <DialogContent dividers>
          {receiptPreview ? (
            <Stack spacing={2}>
              {receiptActionError ? <Alert severity="error">{receiptActionError}</Alert> : null}
              <Alert severity="success">
                Payment successful for {receiptPreview.order.orderNumber}. Receipt is ready.
              </Alert>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1.25}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Lab Payment Success</Typography>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Lab Order Number</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.order.orderNumber || "-"}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Bill Number</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.order.billNumber || "-"}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Patient Name</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.order.patientName || "-"}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Receipt Number</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.receipt.receiptNumber || "-"}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Amount</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{formatMoney(receiptPreview.receipt.amount)}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Payment Mode</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.payment.paymentMode}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Transaction Reference</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{receiptPreview.payment.referenceNumber || "-"}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Collected By</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{staffDisplayName(receiptPreview.payment.receivedByLabel, receiptPreview.payment.receivedBy)}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Typography variant="caption" color="text.secondary">Collected Timestamp</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{formatDateTime(receiptPreview.payment.paymentDateTime || receiptPreview.payment.paymentDate || receiptPreview.receipt.receiptDate)}</Typography>
                      </Grid>
                    </Grid>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReceiptPreview(null)}>Proceed to Sample Collection</Button>
          {canAccessLabReceipts ? (
            <>
              <Button
                variant="outlined"
                onClick={() => void loadReceiptPrintData(false)}
                disabled={!receiptPreview || receiptActionLoading || receiptPrintLoading}
              >
                View Receipt
              </Button>
              <Button
                variant="outlined"
                onClick={() => void openReceiptPdf()}
                disabled={!receiptPreview || receiptActionLoading || receiptPrintLoading}
                startIcon={<DownloadRoundedIcon />}
              >
                Download PDF
              </Button>
              <Button
                variant="contained"
                onClick={() => void loadReceiptPrintData(true)}
                disabled={!receiptPreview || receiptActionLoading || receiptPrintLoading}
                startIcon={<PaidRoundedIcon />}
              >
                Print Receipt
              </Button>
            </>
          ) : null}
          <Button
            variant="outlined"
            onClick={() => void sendReceiptAction("email")}
            disabled={!receiptPreview || receiptActionLoading || receiptPrintLoading || !canSendReceipt}
          >
            Email
          </Button>
          <Button
            variant="outlined"
            onClick={() => void sendReceiptAction("whatsapp")}
            disabled={!receiptPreview || receiptActionLoading || receiptPrintLoading || !canSendReceipt}
          >
            WhatsApp
          </Button>
        </DialogActions>
      </Dialog>

      <ReceiptPrintDialog
        open={receiptPrintOpen || receiptPrintLoading}
        loading={receiptPrintLoading}
        data={receiptPrintData}
        onClose={() => {
          setReceiptPrintOpen(false);
          setReceiptPrintLoading(false);
          setReceiptPrintAutoPrint(false);
          setReceiptPrintData(null);
        }}
        onPrint={() => window.print()}
      />

      <Dialog open={Boolean(sampleTarget)} onClose={() => {
        setSampleTarget(null);
        setCollectedSamples([]);
      }} fullWidth maxWidth="md">
        <DialogTitle>Collect Sample</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{sampleTarget ? `${sampleTarget.orderNumber} • ${sampleTarget.patientName || "-"}` : ""}</Alert>
            {sampleRows.map((row, index) => (
              <Card key={`${row.labOrderItemId || "sample"}-${index}`} variant="outlined">
                <CardContent sx={{ display: "grid", gap: 1.25 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{row.testName}</Typography>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label={<RequiredLabel text="Specimen Type" required />}
                        value={row.specimenType}
                        onChange={(e) => setSampleRows((current) => current.map((sampleRow, rowIndex) => rowIndex === index ? { ...sampleRow, specimenType: e.target.value.slice(0, 128) } : sampleRow))}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        select
                        label="Container Type"
                        value={row.containerType}
                        onChange={(e) => setSampleRows((current) => current.map((sampleRow, rowIndex) => rowIndex === index ? { ...sampleRow, containerType: String(e.target.value).slice(0, 128) } : sampleRow))}
                      >
                        <MenuItem value="">Select container type</MenuItem>
                        {SAMPLE_CONTAINER_TYPE_OPTIONS.map((option) => (
                          <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label="Collection Notes"
                        value={row.notes}
                        onChange={(e) => setSampleRows((current) => current.map((sampleRow, rowIndex) => rowIndex === index ? { ...sampleRow, notes: e.target.value.slice(0, 250) } : sampleRow))}
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </Card>
            ))}
            <TextField
              fullWidth
              label="Collected By"
              value={sampleCollectedBy}
              InputProps={{ readOnly: true }}
              helperText="Auto-populated from the signed-in user and sent by the server audit trail."
            />
            <TextField
              fullWidth
              label="Collected At"
              type="datetime-local"
              value={sampleCollectedAt}
              onChange={(e) => setSampleCollectedAt(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <FormControl fullWidth>
              <InputLabel id="sample-collection-status-label">Collection Status</InputLabel>
              <Select
                labelId="sample-collection-status-label"
                label="Collection Status"
                value={sampleCollectionStatus}
                onChange={(e) => setSampleCollectionStatus(String(e.target.value) as SampleCollectionStatus)}
              >
                {SAMPLE_COLLECTION_STATUS_OPTIONS.map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </Select>
            </FormControl>
            {collectedSamples.length ? (
              <Alert severity="success">
                {collectedSamples.map((sample) => `${sample.accessionNumber} • ${sample.barcodeValue}`).join(" | ")}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setSampleTarget(null);
            setCollectedSamples([]);
          }}>Close</Button>
          <Button variant="contained" startIcon={<ScienceRoundedIcon />} onClick={() => void collectSample()} disabled={saving || !sampleTarget}>Collect Sample</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(receiveTarget)} onClose={() => setReceiveTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Receive Sample</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{receiveTarget ? `${receiveTarget.accessionNumber} • ${receiveTarget.specimenType}` : ""}</Alert>
            <Typography variant="body2" color="text.secondary">Barcode text only in Batch 2. Label printing remains pending.</Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReceiveTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void receiveSample()} disabled={saving || !receiveTarget}>Mark Received</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(rejectTarget)} onClose={() => setRejectTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Reject Sample</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="warning">{rejectTarget ? `${rejectTarget.accessionNumber} • ${rejectTarget.specimenType}` : ""}</Alert>
            <FormControl fullWidth>
              <InputLabel id="sample-reject-reason-label"><RequiredLabel text="Rejection Reason" required /></InputLabel>
              <Select
                labelId="sample-reject-reason-label"
                label="Rejection Reason"
                value={rejectReason}
                onChange={(e) => setRejectReason(String(e.target.value))}
              >
                {SAMPLE_REJECTION_REASONS.map((reason) => <MenuItem key={reason} value={reason}>{reason}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel id="sample-recollect-label">Recollection Required</InputLabel>
              <Select
                labelId="sample-recollect-label"
                label="Recollection Required"
                value={recollectionRequired ? "YES" : "NO"}
                onChange={(e) => setRecollectionRequired(String(e.target.value) === "YES")}
              >
                <MenuItem value="NO">No</MenuItem>
                <MenuItem value="YES">Yes</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Notes" value={rejectNotes} onChange={(e) => setRejectNotes(e.target.value.slice(0, 250))} multiline minRows={2} inputProps={{ maxLength: 250 }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={() => void rejectSample()} disabled={saving || !rejectTarget}>Reject Sample</Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(resultTarget)}
        onClose={() => {
          setResultTarget(null);
          setResultComments("");
          setResultItems([]);
          setResultDraftLoaded(false);
          setResultSaveMessage(null);
          closeAttachmentPreview();
        }}
        fullWidth
        maxWidth="lg"
      >
        <DialogTitle>Enter Results</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {resultTarget ? `${resultTarget.orderNumber} • ${resultTarget.patientName || "-"} • ${resultTarget.sampleAccessionNumber || "Accession pending"} • ${resultTarget.sampleSummaryStatus || "No sample"}` : ""}
            </Alert>
            {resultSaveMessage ? <Alert severity="success">{resultSaveMessage}</Alert> : null}
            {resultDraftLoaded ? <Alert severity="info">Draft restored from your last save.</Alert> : null}
            <TextField label="Comments" value={resultComments} onChange={(e) => setResultComments(e.target.value)} multiline minRows={2} />
            <Stack spacing={1.5}>
              {resultItems.map((item, index) => (
                <Card key={item.labOrderItemId} variant="outlined">
                  <CardContent sx={{ display: "grid", gap: 1.25 }}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{item.testName}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.testCode}</Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                          Reference {item.referenceRange || "-"} | Critical {item.criticalRange || "-"}
                        </Typography>
                      </Box>
                      <Button size="small" onClick={() => {
                        setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: [...row.componentResults, { parameterName: "", componentName: "", resultValue: "", unit: "", referenceRange: "", criticalRange: "" }],
                        }) : row));
                      }}>
                        Add component
                      </Button>
                    </Box>
                    {item.componentResults.length ? (
                      <Stack spacing={1}>
                        {item.componentResults.map((component, componentIndex) => (
                          <Grid container spacing={1} key={`${item.labOrderItemId}-component-${componentIndex}`}>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Parameter" value={component.parameterName} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, parameterName: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Component" value={component.componentName} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, componentName: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 2 }}>
                              {(() => {
                                const severity = getResultSeverity(component.resultValue, component.referenceRange, component.criticalRange);
                                return (
                                  <TextField
                                    fullWidth
                                    size="small"
                                    label="Result Value"
                                    value={component.resultValue}
                                    onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                                      ...row,
                                      componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, resultValue: e.target.value } : currentComponent),
                                    }) : row))}
                                    error={severity === "error"}
                                    helperText={`Ref ${formatReferenceBadge(component.referenceRange)} • Crit ${formatReferenceBadge(component.criticalRange)}`}
                                    sx={resultFieldSx(severity)}
                                  />
                                );
                              })()}
                            </Grid>
                            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Unit" value={component.unit} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, unit: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Reference Range" value={component.referenceRange} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, referenceRange: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Critical Range" value={component.criticalRange} disabled /></Grid>
                            <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                              <IconButton size="small" onClick={() => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                                ...row,
                                componentResults: row.componentResults.filter((_, currentIndex) => currentIndex !== componentIndex),
                              }) : row))}>
                                <Typography variant="caption">x</Typography>
                              </IconButton>
                            </Grid>
                          </Grid>
                        ))}
                      </Stack>
                    ) : (
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12, md: 4 }}>
                          {(() => {
                            const severity = getResultSeverity(item.resultValue, item.referenceRange, item.criticalRange);
                            return (
                              <TextField
                                fullWidth
                                size="small"
                                label="Result Value"
                                value={item.resultValue}
                                onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, resultValue: e.target.value } : row))}
                                error={severity === "error"}
                                helperText={`Ref ${formatReferenceBadge(item.referenceRange)} • Crit ${formatReferenceBadge(item.criticalRange)}`}
                                sx={resultFieldSx(severity)}
                              />
                            );
                          })()}
                        </Grid>
                        <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Unit" value={item.unit} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, unit: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Reference Range" value={item.referenceRange} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, referenceRange: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth size="small" label="Critical Range" value={item.criticalRange} disabled /></Grid>
                      </Grid>
                    )}
                  </CardContent>
                </Card>
              ))}
              {resultTarget?.attachments?.length ? (
                <Card variant="outlined">
                  <CardContent sx={{ display: "grid", gap: 1.25 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Attachments</Typography>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      {resultTarget.attachments.map((attachment) => (
                        <Chip
                          key={attachment.id}
                          variant="outlined"
                          label={`${attachment.originalFilename} • ${attachment.mediaType}`}
                          onClick={() => void openAttachmentPreview(attachment)}
                          onDelete={() => void downloadAttachment(attachment)}
                          deleteIcon={<DownloadRoundedIcon />}
                        />
                      ))}
                    </Stack>
                    {resultAttachmentPreview ? (
                      <Card variant="outlined" sx={{ overflow: "hidden" }}>
                        <CardContent sx={{ display: "grid", gap: 1 }}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                            <Box>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{resultAttachmentPreview.attachment.originalFilename}</Typography>
                              <Typography variant="caption" color="text.secondary">{resultAttachmentPreview.mediaType}</Typography>
                            </Box>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Button size="small" variant="outlined" onClick={() => void downloadAttachment(resultAttachmentPreview.attachment)} disabled={resultAttachmentLoading}>
                                Download
                              </Button>
                              <Button size="small" variant="text" onClick={closeAttachmentPreview}>Close preview</Button>
                            </Stack>
                          </Box>
                          {resultAttachmentPreview.mediaType.startsWith("image/") ? (
                            <Box component="img" alt={resultAttachmentPreview.attachment.originalFilename} src={resultAttachmentPreview.blobUrl} sx={{ maxWidth: "100%", maxHeight: 420, objectFit: "contain", borderRadius: 1 }} />
                          ) : resultAttachmentPreview.mediaType === "application/pdf" ? (
                            <Box component="iframe" title={resultAttachmentPreview.attachment.originalFilename} src={resultAttachmentPreview.blobUrl} sx={{ width: "100%", height: 480, border: 0, borderRadius: 1 }} />
                          ) : (
                            <Alert severity="info">Preview is available for images and PDF files. Download to view this attachment type.</Alert>
                          )}
                        </CardContent>
                      </Card>
                    ) : null}
                  </CardContent>
                </Card>
              ) : null}
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setResultTarget(null);
            setResultComments("");
            setResultItems([]);
            setResultDraftLoaded(false);
            setResultSaveMessage(null);
            closeAttachmentPreview();
          }}>Cancel</Button>
          <Button variant="outlined" onClick={saveResultsDraft} disabled={!resultTarget || resultSavingDraft || saving}>
            Save Draft
          </Button>
          <Button variant="contained" onClick={() => void saveResults()} disabled={saving || !resultTarget}>
            Save Results
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(reviewTarget)} onClose={() => setReviewTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Lab Verification</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{reviewTarget ? `${reviewTarget.orderNumber} • ${reviewTarget.patientName || "-"} • ${reviewTarget.sampleAccessionNumber || "Accession pending"}` : ""}</Alert>
            {reviewTarget?.results.some((result) => result.criticalResult) ? (
              <Alert severity="warning">
                Critical results: {reviewTarget.results.filter((result) => result.criticalResult).map((result) => `${result.parameterName || result.componentName || result.testName} (${resultFlagLabel(result.resultFlag)})`).join(", ")}
              </Alert>
            ) : null}
            <FormControl fullWidth>
              <InputLabel id="lab-review-decision-label">
                <RequiredLabel text="Verification decision" required />
              </InputLabel>
              <Select
                labelId="lab-review-decision-label"
                label="Verification decision"
                value={reviewDecision}
                onChange={(e) => setReviewDecision(e.target.value as "APPROVE" | "SEND_BACK")}
              >
                <MenuItem value="APPROVE">Approve</MenuItem>
                <MenuItem value="SEND_BACK">Send back</MenuItem>
              </Select>
            </FormControl>
            <CommentSuggestions
              category="LAB_REJECTION"
              selectedReason={reviewReason}
              remarks={reviewComments}
              onReasonChange={setReviewReason}
              onRemarksChange={setReviewComments}
              requiredReason={reviewDecision === "SEND_BACK"}
              reasonLabel="Reason"
              remarksLabel="Comments"
              maxRemarksLength={250}
              reasonHelperText={reviewDecision === "SEND_BACK" ? "Select a reason before sending the result back." : "Reason is optional when approving."}
              remarksHelperText={reviewDecision === "SEND_BACK" ? "Add comments for the technician." : "Optional verification comments."}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReviewTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveReview()} disabled={saving || !reviewTarget}>{reviewDecision === "SEND_BACK" ? "Send Back" : "Verify Results"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(publishTarget)} onClose={() => setPublishTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Publish Lab Report</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {publishTarget ? `${publishTarget.orderNumber} • ${publishTarget.patientName || "-"} • ${publishTarget.sampleAccessionNumber || "Accession pending"}` : ""}
            </Alert>
            {publishTarget?.results.some((result) => result.criticalResult) ? (
              <Alert severity="warning">
                Critical results: {publishTarget.results.filter((result) => result.criticalResult).map((result) => `${result.parameterName || result.componentName || result.testName} (${resultFlagLabel(result.resultFlag)})`).join(", ")}
              </Alert>
            ) : null}
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Delivery channels</Typography>
              <Stack spacing={0.5}>
                {DELIVERY_CHANNEL_OPTIONS.map((option) => (
                  <FormControlLabel
                    key={option.value}
                    control={
                      <Checkbox
                        checked={publishChannels.includes(option.value)}
                        onChange={(event) => {
                          setPublishChannels((current) => event.target.checked
                            ? Array.from(new Set([...current, option.value]))
                            : current.filter((channel) => channel !== option.value));
                        }}
                      />
                    }
                    label={option.label}
                  />
                ))}
              </Stack>
            </Box>
            <TextField
              fullWidth
              label="Publish notes"
              value={publishNotes}
              onChange={(e) => setPublishNotes(e.target.value)}
              multiline
              minRows={3}
              placeholder="Optional notes about publication or delivery"
            />
            <Alert severity="info">
              Published reports move to the patient-visible state. REPORT_READY remains internal until this action runs.
            </Alert>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void savePublish()} disabled={saving || !publishTarget}>Publish</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(publishSuccessTarget)} onClose={() => setPublishSuccessTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Report Published</DialogTitle>
        <DialogContent dividers>
          {publishSuccessTarget ? (
            <Stack spacing={2} sx={{ pt: 0.5 }}>
              <Alert severity="success">
                {publishSuccessTarget.orderNumber} is now published and available to the selected delivery channels.
              </Alert>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1.25}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Delivery channels</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      {[
                        ["Patient Portal", publishSuccessTarget.reportDeliveryChannels.includes("PATIENT_PORTAL")],
                        ["Doctor Notification", publishSuccessTarget.reportDeliveryChannels.includes("DOCTOR_NOTIFICATION")],
                        ["Email", publishSuccessTarget.reportDeliveryChannels.includes("EMAIL")],
                        ["WhatsApp", publishSuccessTarget.reportDeliveryChannels.includes("WHATSAPP")],
                        ["Print", publishSuccessTarget.reportDeliveryChannels.includes("PRINT")],
                      ].map(([label, enabled]) => (
                        <Chip
                          key={String(label)}
                          size="small"
                          label={`${label} • ${enabled ? "Queued" : "Skipped"}`}
                          color={enabled ? "success" : "default"}
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Delivery audit is recorded on the published report record and can be reviewed from the order row.
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Button variant="outlined" onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) viewReport(publishSuccessTarget); }}>View Report</Button>
                <Button variant="outlined" onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) printReport(publishSuccessTarget); }}>Print Report</Button>
                <Button variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) downloadReport(publishSuccessTarget); }}>Download PDF</Button>
                <Button variant="outlined" onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) emailReport(publishSuccessTarget); }}>Email</Button>
                <Button variant="outlined" onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) whatsappReport(publishSuccessTarget); }}>WhatsApp</Button>
                <Button variant="outlined" onClick={() => { setPublishSuccessTarget(null); if (publishSuccessTarget) shareReportLink(publishSuccessTarget); }}>Share Link</Button>
              </Stack>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishSuccessTarget(null)}>Continue to Sample Collection</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importOpen} onClose={() => setImportOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Lab Test CSV Import Result</DialogTitle>
        <DialogContent dividers>
          {importResult ? (
            <Stack spacing={1.5} sx={{ pt: 0.5 }}>
              <Alert severity={importResult.failedCount > 0 ? "warning" : "success"}>
                Total rows: {importResult.totalRows} | Created: {importResult.createdCount} | Updated: {importResult.updatedCount} | Failed: {importResult.failedCount}
              </Alert>
              {importResult.rowErrors.length ? (
                <Stack spacing={1}>
                  {importResult.rowErrors.map((rowError) => (
                    <Alert key={`${rowError.rowNumber}-${rowError.message}`} severity="error">
                      Row {rowError.rowNumber}: {rowError.message}
                    </Alert>
                  ))}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">All uploaded rows were processed successfully.</Typography>
              )}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={requestOpen} onClose={() => setRequestOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>New Laboratory Order</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {consultationEnabled
                ? "Create a walk-in or doctor referral laboratory registration directly from the laboratory desk."
                : "Create a walk-in laboratory registration directly from the laboratory desk without consultation dependency."}
            </Alert>
            <Autocomplete
              value={selectedPatient}
              options={patientOptions}
              loading={patientLoading}
              getOptionLabel={(option) => `${option.patientNumber} • ${option.firstName} ${option.lastName || ""} • ${option.mobile}`.trim()}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              onChange={(_, value) => {
                setSelectedPatient(value);
                setRequestForm((current) => ({ ...current, patientId: value?.id || "" }));
                if (!value) {
                  setPatientQuery("");
                }
              }}
              inputValue={patientQuery}
              onInputChange={(_, value) => setPatientQuery(value)}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Search patient"
                  helperText={patientOptionsLoaded && !patientLoading ? "Search by patient name, mobile number, or patient number." : "Type at least 2 characters."}
                />
              )}
            />
            {canQuickRegisterPatient ? (
              <Alert
              severity={consultationEnabled ? "info" : "success"}
              action={(
                <Button color="inherit" size="small" onClick={() => setQuickRegisterOpen(true)}>
                    Register New Patient
                </Button>
              )}
            >
                {consultationEnabled
                  ? "If the patient is not found, quick register and continue the lab order."
                  : "Walk-in lab registration can create and select a patient without leaving this modal."}
              </Alert>
            ) : null}
            <FormControl fullWidth>
              <InputLabel id="lab-registration-origin-label">
                <RequiredLabel text="Requested By" required />
              </InputLabel>
              <Select
                labelId="lab-registration-origin-label"
                label="Requested By"
                value={requestForm.orderOrigin}
                onChange={(e) => setRequestForm((current) => ({ ...current, orderOrigin: e.target.value as LabOrderOrigin }))}
              >
                {(consultationEnabled ? DIRECT_ORDER_ORIGIN_OPTIONS : ["WALK_IN"]).map((origin) => (
                  <MenuItem key={origin} value={origin}>{formatOrderOrigin(origin)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <Autocomplete
              multiple
              options={availableTests}
              value={availableTests.filter((test) => requestForm.testIds.includes(test.id))}
              getOptionLabel={(option) => `${option.testName}${option.testCode ? ` (${option.testCode})` : ""}`}
              onChange={(_, value) => setRequestForm((current) => ({ ...current, testIds: value.map((row) => row.id) }))}
              noOptionsText="No enabled tests are available for ordering."
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Lab tests"
                  helperText="Select one or more enabled tests from active categories."
                />
              )}
            />
            <Card variant="outlined" sx={{ boxShadow: "none" }}>
              <CardContent sx={{ py: 1.5 }}>
                <Stack spacing={1.5}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Referral details</Typography>
                  <Grid container spacing={1.5}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="External Doctor Name" value={requestForm.externalDoctorName} onChange={(e) => setRequestForm((current) => ({ ...current, externalDoctorName: e.target.value.slice(0, 256) }))} inputProps={{ maxLength: 256 }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="External Doctor Mobile" value={requestForm.externalDoctorMobile} onChange={(e) => setRequestForm((current) => ({ ...current, externalDoctorMobile: e.target.value.slice(0, 32) }))} inputProps={{ maxLength: 32 }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="External Clinic Name" value={requestForm.externalClinicName} onChange={(e) => setRequestForm((current) => ({ ...current, externalClinicName: e.target.value.slice(0, 256) }))} inputProps={{ maxLength: 256 }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="Referral Source" value={requestForm.referralSource} onChange={(e) => setRequestForm((current) => ({ ...current, referralSource: e.target.value.slice(0, 128) }))} inputProps={{ maxLength: 128 }} />
                    </Grid>
                  </Grid>
                </Stack>
              </CardContent>
            </Card>
            <TextField
              label="Notes"
              value={requestForm.notes}
              onChange={(e) => setRequestForm((current) => ({ ...current, notes: e.target.value.slice(0, 250) }))}
              multiline
              minRows={2}
              inputProps={{ maxLength: 250 }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRequestOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => void submitLabRequest()}
            disabled={saving || !requestForm.patientId || requestForm.testIds.length === 0}
          >
            Create Lab Order
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmationDialog
        open={Boolean(deactivateTarget)}
        title="Deactivate lab test"
        description={deactivateTarget ? `Deactivate ${deactivateTarget.testName}?` : undefined}
        confirmLabel="Deactivate"
        confirmColor="error"
        onCancel={() => setDeactivateTarget(null)}
        onConfirm={() => void submitDeactivate()}
      />

      {canQuickRegisterPatient ? (
        <PatientQuickRegisterDialog
          open={quickRegisterOpen}
          token={auth.accessToken}
          tenantId={auth.tenantId}
          title="Register New Patient"
          subtitle={consultationEnabled
            ? "Create a patient record without leaving laboratory order entry."
            : "Create the patient master record and continue the walk-in lab order."}
          initialMobile={patientQuery}
          onClose={() => setQuickRegisterOpen(false)}
          onCreated={onQuickPatientCreated}
        />
      ) : null}
    </Stack>
  );
}

function OrderQueue(props: {
  rows: LabOrder[];
  emptySubtitle: string;
  actionDisabled: boolean;
  canCollectPayment: boolean;
  canCollectSample: boolean;
  canManageSamples: boolean;
  canEnterResults: boolean;
  canGenerateReport: boolean;
  canReviewReport: boolean;
  canAccessLabReceipts: boolean;
  onViewReceipt: (row: LabOrder) => void;
  onPrintReceipt: (row: LabOrder) => void;
  onDownloadReceipt: (row: LabOrder) => void;
  onCollectPayment: (row: LabOrder) => void;
  onCollectSample: (row: LabOrder) => void;
  onReceiveSample: (sample: LabSample) => void;
  onRejectSample: (sample: LabSample) => void;
  onEnterResults: (row: LabOrder) => void;
  onReview: (row: LabOrder) => void;
  onPublishReport: (row: LabOrder) => void;
  onViewReport: (row: LabOrder) => void;
  onPrintReport: (row: LabOrder) => void;
  onDownloadReport: (row: LabOrder) => void;
  onEmailReport: (row: LabOrder) => void;
  onWhatsappReport: (row: LabOrder) => void;
  onShareReportLink: (row: LabOrder) => void;
}) {
  const { rows, emptySubtitle, actionDisabled, canCollectPayment, canCollectSample, canManageSamples, canEnterResults, canGenerateReport, canReviewReport, canAccessLabReceipts, onViewReceipt, onPrintReceipt, onDownloadReceipt, onCollectPayment, onCollectSample, onReceiveSample, onRejectSample, onEnterResults, onReview, onPublishReport, onViewReport, onPrintReport, onDownloadReport, onEmailReport, onWhatsappReport, onShareReportLink } = props;

  if (!rows.length) {
    return <CompactEmptyState title="No lab orders found" subtitle={emptySubtitle} />;
  }

  return (
    <>
      <Box sx={{ overflowX: "auto" }}>
        <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Order</TableCell>
            <TableCell>Patient</TableCell>
            <TableCell>Source</TableCell>
            <TableCell>Tests</TableCell>
            <TableCell>Sample</TableCell>
            <TableCell>Bill</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => {
            const sampleAction = actionableSample(row);
            return (
            <TableRow key={row.id}>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.orderNumber}</Typography>
                  <Typography variant="caption" color="text.secondary">{new Date(row.orderedAt).toLocaleString()}</Typography>
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "-"}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{formatOrderOrigin(row.orderOrigin)}</Typography>
                  <Typography variant="caption" color="text.secondary">Requested by: {requestedByLabel(row)}</Typography>
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.items.length} tests</Typography>
                  <Typography variant="caption" color="text.secondary">{row.results.length ? `${row.results.length} result rows` : "No results yet"}</Typography>
                  {row.results.length ? (
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {row.results.slice(0, 3).map((result, resultIndex) => (
                        <Chip
                          key={`${row.id}-result-${resultIndex}`}
                          size="small"
                          label={`${result.parameterName || result.componentName || result.testName}: ${result.resultFlag || "NORMAL"}`}
                          color={resultTone(result.resultFlag)}
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.5}>
                              <Chip size="small" label={sampleSummaryLabel(row)} color={sampleStatusTone(row.sampleSummaryStatus)} variant="outlined" />
                              <Typography variant="caption" color="text.secondary">{row.sampleAccessionNumber || "Accession pending"}</Typography>
                              {row.sampleBarcodeValue ? <Typography variant="caption" color="text.secondary">Barcode: {row.sampleBarcodeValue}</Typography> : null}
                              {(row.sampleCollectedBy || row.sampleCollectedAt) ? (
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Chip size="small" variant="outlined" label={`Collected by: ${row.sampleCollectedBy || row.sampleCollectedByUserId || "—"}`} />
                                  <Chip size="small" variant="outlined" label={`Date: ${formatDateChip(row.sampleCollectedAt)}`} />
                                  <Chip size="small" variant="outlined" label={`Time: ${formatTimeChip(row.sampleCollectedAt)}`} />
                                </Stack>
                              ) : null}
                            </Stack>
                          </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.billNumber || "-"}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.billDueAmount != null ? `Due ${formatMoney(row.billDueAmount)}` : row.billStatus || "-"}</Typography>
                              {canAccessLabReceipts && (() => {
                                const receiptSummary = buildReceiptPaymentSummary(row);
                                return receiptSummary ? (
                                <Stack spacing={0.25} sx={{ pt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">
                        Receipt: {receiptSummary.receiptNumber || "-"}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Paid {formatMoney(receiptSummary.paymentAmount ?? row.billTotalAmount ?? row.billDueAmount ?? 0)} • {receiptSummary.paymentMode || "-"}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Ref: {receiptSummary.referenceNumber || "—"} • By {receiptSummary.receivedBy || "Staff User"}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {receiptSummary.paymentDateTime ? formatDateTime(receiptSummary.paymentDateTime) : receiptTimestampText(row)}
                      </Typography>
                    </Stack>
                                ) : row.status === "PAID" || (row.billDueAmount != null && row.billDueAmount <= 0) ? (
                                <Typography variant="caption" color="text.secondary">
                                  Paid - receipt details unavailable
                                </Typography>
                                ) : null;
                              })()}
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Chip size="small" label={row.status.replaceAll("_", " ")} color={statusTone(row.status)} variant="outlined" />
                  {row.reportDeliveryStatus ? (
                    <Typography variant="caption" color="text.secondary">
                      Delivery: {row.reportDeliveryStatus.replaceAll("_", " ")}{row.reportDeliveryChannels?.length ? ` • ${deliveryChannelsLabel(row.reportDeliveryChannels)}` : ""}
                    </Typography>
                  ) : null}
                  {row.reportDeliveryHistory?.length ? (
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {reportDeliveryHistorySummary(row.reportDeliveryHistory).map((label, index) => (
                        <Chip key={`${row.id}-delivery-${index}`} size="small" label={label} variant="outlined" />
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              </TableCell>
              <TableCell align="right">
                <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                  {canCollectPayment && row.billDueAmount != null && row.billDueAmount > 0 ? (
                    <Button size="small" variant="contained" startIcon={<PaidRoundedIcon />} onClick={() => onCollectPayment(row)}>
                      Collect
                    </Button>
                  ) : null}
                  {canCollectSample && row.status === "READY_FOR_COLLECTION" ? (
                    <Button size="small" variant="outlined" startIcon={<ScienceRoundedIcon />} onClick={() => onCollectSample(row)}>
                      Collect sample
                    </Button>
                  ) : null}
                  {canManageSamples && sampleAction?.status === "COLLECTED" ? (
                    <Button size="small" variant="outlined" onClick={() => onReceiveSample(sampleAction)}>Receive</Button>
                  ) : null}
                  {canManageSamples && sampleAction ? (
                    <Button size="small" variant="outlined" color="error" onClick={() => onRejectSample(sampleAction)}>Reject</Button>
                  ) : null}
                  {canEnterResults && (row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED") ? (
                    <Button size="small" variant="outlined" onClick={() => onEnterResults(row)}>Enter results</Button>
                  ) : null}
                  {canGenerateReport && row.status === "REPORT_READY" ? (
                    <Button size="small" variant="outlined" onClick={() => onPublishReport(row)}>
                      Publish report
                    </Button>
                  ) : null}
                  {canGenerateReport && APPROVED_REPORT_STATUSES.has(row.status) ? (
                    <ReportActionMenuButton
                      row={row}
                      disabled={actionDisabled}
                      onViewReport={onViewReport}
                      onPrintReport={onPrintReport}
                      onDownloadReport={onDownloadReport}
                      onEmailReport={onEmailReport}
                      onWhatsappReport={onWhatsappReport}
                      onShareLink={onShareReportLink}
                    />
                  ) : null}
                  {canAccessLabReceipts && (() => {
                    const receiptSummary = buildReceiptPaymentSummary(row);
                    return receiptSummary ? (
                    <>
                      <Button size="small" variant="outlined" onClick={() => onViewReceipt(row)}>
                        View Receipt
                      </Button>
                      <Button size="small" variant="outlined" onClick={() => onPrintReceipt(row)}>
                        Print Receipt
                      </Button>
                      <Button size="small" variant="outlined" onClick={() => onDownloadReceipt(row)}>
                        Download PDF
                      </Button>
                    </>
                    ) : null;
                  })()}
                  {canReviewReport && row.status === "RESULT_ENTERED" ? (
                    <Button size="small" variant="outlined" onClick={() => onReview(row)}>
                      Review
                    </Button>
                  ) : null}
                </Stack>
              </TableCell>
            </TableRow>
          );
          })}
        </TableBody>
        </Table>
      </Box>
    </>
  );
}
