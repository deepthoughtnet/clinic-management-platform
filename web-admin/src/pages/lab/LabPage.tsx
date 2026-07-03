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
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import EditRoundedIcon from "@mui/icons-material/EditRounded";
import PaidRoundedIcon from "@mui/icons-material/PaidRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import { firstZodError, labDoctorReviewSchema, labOrderCreateSchema, labPaymentSchema, labResultEntrySchema, labTestMasterSchema } from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import { resolveEnabledTenantModules } from "../../modules/moduleRegistry";
import { CompactEmptyState, CompactStatCard, compactCardContentSx } from "../../components/compact/CompactUi";
import LabDashboard, { type LabDashboardActionKey, type LabDashboardData } from "./LabDashboard";
import RequiredLabel from "../../components/forms/RequiredLabel";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import {
  collectLabOrderSamples,
  collectLabOrderPayment,
  createLabTest,
  createLabOrder,
  deactivateLabTest,
  enterLabOrderResults,
  getLabCategories,
  getLabTestImportTemplate,
  getLabOrderPdf,
  getLabOrders,
  getLabTests,
  importLabTestsCsv,
  receiveLabSample,
  rejectLabSample,
  publishLabOrderReport,
  searchPatients,
  updateLabTest,
  verifyLabOrder,
  type LabTestCsvImportResult,
  type Patient,
  type LabOrder,
  type LabOrderOrigin,
  type LabOrderResult,
  type LabSample,
  type LabSampleStatus,
  type LabOrderStatus,
  type LabTest,
  type LabTestInput,
  type LabTestParameterInput,
  type PaymentMode,
} from "../../api/clinicApi";

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
  const [orders, setOrders] = React.useState<LabOrder[]>([]);
  const [categories, setCategories] = React.useState<string[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
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
  const [sampleCollectedBy, setSampleCollectedBy] = React.useState("");
  const [sampleCollectedAt, setSampleCollectedAt] = React.useState(toDatetimeLocal(new Date().toISOString()));
  const [sampleRows, setSampleRows] = React.useState<SampleCollectionFormRow[]>([]);
  const [collectedSamples, setCollectedSamples] = React.useState<LabSample[]>([]);
  const [receiveTarget, setReceiveTarget] = React.useState<LabSample | null>(null);
  const [rejectTarget, setRejectTarget] = React.useState<LabSample | null>(null);
  const [rejectReason, setRejectReason] = React.useState("");
  const [rejectNotes, setRejectNotes] = React.useState("");
  const [recollectionRequired, setRecollectionRequired] = React.useState(false);
  const [resultComments, setResultComments] = React.useState("");
  const [resultItems, setResultItems] = React.useState<ResultItemForm[]>([]);
  const [reviewComments, setReviewComments] = React.useState("");
  const [reviewDecision, setReviewDecision] = React.useState<"APPROVE" | "SEND_BACK">("APPROVE");
  const [reviewReason, setReviewReason] = React.useState("");
  const [publishTarget, setPublishTarget] = React.useState<LabOrder | null>(null);
  const [publishNotes, setPublishNotes] = React.useState("");
  const [publishChannels, setPublishChannels] = React.useState<string[]>(["PATIENT_PORTAL"]);
  const [importOpen, setImportOpen] = React.useState(false);
  const [importResult, setImportResult] = React.useState<LabTestCsvImportResult | null>(null);
  const [requestOpen, setRequestOpen] = React.useState(false);
  const [requestForm, setRequestForm] = React.useState<LabRequestForm>(emptyLabRequestForm());
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientOptions, setPatientOptions] = React.useState<Patient[]>([]);
  const [patientLoading, setPatientLoading] = React.useState(false);
  const [patientOptionsLoaded, setPatientOptionsLoaded] = React.useState(false);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  const canManageTests = auth.hasPermission("lab.test.manage");
  const canViewOrders = auth.hasPermission("lab.order.read")
    || auth.hasPermission("lab.order.collect_payment")
    || auth.hasPermission("lab.order.collect_sample")
    || auth.hasPermission("lab.order.result_entry")
    || auth.hasPermission("lab.order.generate_report")
    || auth.hasPermission("lab.order.create");
  const canCollectPayment = auth.hasPermission("lab.order.collect_payment");
  const canCollectSample = auth.hasPermission("lab.order.collect_sample");
  const canEnterResults = auth.hasPermission("lab.order.result_entry");
  const canGenerateReport = auth.hasPermission("lab.order.generate_report");
  const canReviewReport = auth.hasPermission("lab.order.review");
  const canCreateOrders = auth.hasPermission("lab.order.create");
  const enabledModules = React.useMemo(() => resolveEnabledTenantModules(auth), [auth]);
  const consultationEnabled = enabledModules.has("CONSULTATION");
  const laboratoryMode = consultationEnabled ? "INTEGRATED" : "STANDALONE";
  const primaryRegistrationLabel = "New Lab Order";
  const labBadgeLabel = consultationEnabled ? "Integrated Laboratory" : "Standalone Laboratory";
  const pageSubtitle = consultationEnabled
    ? "Manage consultation-linked and walk-in laboratory orders, billing, samples, results, and reports."
    : "Run standalone diagnostic registrations, billing, sample collection, results, and reports without consultation dependency.";
  const workflowTitle = consultationEnabled ? "Doctor Request / Walk-in" : "Patient Registration";
  const workflowSteps = consultationEnabled
    ? ["Billing", "Payment", "Sample Collection", "Result Entry", "Lab Review", "Report"]
    : ["Test Selection", "Billing", "Payment", "Sample Collection", "Result Entry", "Lab Review", "Report"];

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [categoryRows, testRows, orderRows] = await Promise.all([
        getLabCategories(auth.accessToken, auth.tenantId),
        getLabTests(auth.accessToken, auth.tenantId, { active: null }),
        canViewOrders ? getLabOrders(auth.accessToken, auth.tenantId, {}) : Promise.resolve([] as LabOrder[]),
      ]);
      setCategories(categoryRows);
      setTests(testRows);
      setOrders(orderRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load laboratory module");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canViewOrders]);

  React.useEffect(() => {
    void load();
  }, [load]);

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

  const activeTestCount = tests.filter((row) => row.active).length;
  const pendingStatusCount = orders.filter((row) => ["ORDERED", "PAYMENT_PENDING", "PAID", "READY_FOR_COLLECTION"].includes(row.status)).length;
  const sampleCollectedCount = orders.filter((row) => ["SAMPLE_COLLECTED", "PROCESSING"].includes(row.status)).length;
  const completedCount = orders.filter((row) => ["RESULT_ENTERED", "REPORT_READY", "REPORT_GENERATED", "DOCTOR_REVIEWED"].includes(row.status)).length;
  const deliveredCount = orders.filter((row) => row.status === "DELIVERED").length;
  const dashboardData = React.useMemo<LabDashboardData>(() => {
    const now = new Date();
    const todayOrders = orders.filter((row) => isSameLocalDay(row.orderedAt, now));
    const todayCollections = orders.filter((row) => isSameLocalDay(row.sampleCollectedAt, now));
    const todayResults = orders.filter((row) => isSameLocalDay(row.resultEnteredAt, now));
    const todayReports = orders.filter((row) => isSameLocalDay(row.reportPublishedAt, now));
    const todayRevenue = orders
      .filter((row) => isSameLocalDay(row.paymentCollectedAt, now))
      .reduce((sum, row) => sum + (row.billTotalAmount ?? 0), 0);

    const workflowSummary = [
      { key: "new-orders" as const, label: "New Orders", value: todayOrders.length, helper: "Created today", tone: "info" as const },
      { key: "payment-pending" as const, label: "Payment Pending", value: orders.filter((row) => row.status === "PAYMENT_PENDING").length, helper: "Awaiting billing clearance", tone: "warning" as const },
      { key: "pending-collection" as const, label: "Pending Collection", value: orders.filter((row) => row.status === "READY_FOR_COLLECTION").length, helper: "Ready for sample draw", tone: "info" as const },
      { key: "work-queue" as const, label: "Work Queue", value: sampleCollectedCount, helper: "Samples in lab flow", tone: "default" as const },
      { key: "pending-review" as const, label: "Pending Lab Review", value: orders.filter((row) => row.status === "RESULT_ENTERED").length, helper: "Awaiting verification", tone: "warning" as const },
      { key: "ready-to-publish" as const, label: "Ready to Publish", value: orders.filter((row) => row.status === "REPORT_READY" || row.status === "REPORT_GENERATED" || row.status === "DOCTOR_REVIEWED").length, helper: "Report actions pending", tone: "success" as const },
      { key: "published-today" as const, label: "Published Today", value: todayReports.length, helper: "Delivered to patients", tone: "success" as const },
    ];

    const alerts = [
      {
        key: "critical-results" as const,
        label: "Critical Results",
        value: orders.filter((row) => row.results.some((result) => result.criticalResult || /critical/i.test(result.resultFlag || ""))).length,
        helper: "Immediate attention",
        tone: "error" as const,
      },
      {
        key: "tat-breached" as const,
        label: "TAT Breached",
        value: orders.filter((row) => {
          if (row.status === "DELIVERED" || row.status === "CANCELLED") return false;
          const turnaroundHours = row.items
            .map((item) => parseTurnaroundHours(item.turnaroundTime))
            .filter((value): value is number => typeof value === "number");
          if (!turnaroundHours.length) return false;
          const maxTurnaroundHours = Math.max(...turnaroundHours);
          const ageHours = (now.getTime() - new Date(row.orderedAt).getTime()) / 3_600_000;
          return ageHours > maxTurnaroundHours;
        }).length,
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
      workflowSummary,
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
    };
  }, [orders, sampleCollectedCount]);

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
    if (!window.confirm(`Deactivate ${row.testName}?`)) return;
    setSaving(true);
    setError(null);
    try {
      await deactivateLabTest(auth.accessToken, auth.tenantId, row.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate lab test");
    } finally {
      setSaving(false);
    }
  };

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
      await collectLabOrderPayment(auth.accessToken, auth.tenantId, paymentTarget.id, {
        amount: parsed.data.amount,
        paymentMode: parsed.data.paymentMode,
        referenceNumber: parsed.data.referenceNumber || null,
        notes: parsed.data.notes || null,
        receivedBy: auth.appUserId || null,
      });
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
          collectedBy: auth.appUserId || null,
          notes: row.notes.trim() || null,
        })),
      });
      setCollectedSamples(savedSamples);
      setSampleCollectedBy("");
      setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
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

  const openResultsDialog = (row: LabOrder) => {
    setResultTarget(row);
    setResultComments(row.resultComments || "");
    setResultItems(row.items.map((item) => defaultResultsForItem(item, row.results)));
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
      setResultTarget(null);
      setResultComments("");
      setResultItems([]);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save results");
    } finally {
      setSaving(false);
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
      await publishLabOrderReport(auth.accessToken, auth.tenantId, publishTarget.id, {
        deliveryChannels: publishChannels,
        publishNotes: publishNotes.trim() || null,
      });
      setPublishTarget(null);
      setPublishNotes("");
      setPublishChannels(["PATIENT_PORTAL"]);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish lab report");
    } finally {
      setSaving(false);
    }
  };

  const generateReport = async (row: LabOrder) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const pdf = await getLabOrderPdf(auth.accessToken, auth.tenantId, row.id);
      openPdf(pdf.blob);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate lab report");
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
    setSelectedPatient(null);
    setPatientQuery("");
    setPatientOptions([]);
    setPatientOptionsLoaded(false);
    setRequestOpen(true);
  };

  const handleDashboardAction = (key: LabDashboardActionKey) => {
    switch (key) {
      case "new-orders":
      case "payment-pending":
      case "published-today":
        setTab(4);
        return;
      case "pending-collection":
      case "work-queue":
      case "critical-results":
      case "sample-rejected":
      case "recollection-required":
        setTab(1);
        return;
      case "pending-review":
      case "ready-to-publish":
        setTab(3);
        return;
      case "quick-new-order":
        openRequestDialog();
        return;
      case "quick-collect":
        setTab(1);
        return;
      case "quick-enter-results":
        setTab(2);
        return;
      case "quick-verify":
      case "quick-publish":
        setTab(3);
        return;
      default:
        return;
    }
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

      <Card variant="outlined" sx={{ boxShadow: "none", bgcolor: "background.paper" }}>
        <CardContent sx={{ py: 1.5 }}>
          <Stack spacing={0.75}>
            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
              {consultationEnabled ? "Integrated workflow" : "Standalone workflow"}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {[workflowTitle, ...workflowSteps].join(" \u2192 ")}
            </Typography>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Catalog items" value={tests.length} helper={`${activeTestCount} active`} tone="info" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Pending" value={pendingStatusCount} helper="Ordered through ready-for-collection" tone="warning" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Sample collected" value={sampleCollectedCount} helper="Processing or already collected" tone="info" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Completed" value={completedCount} helper="Results entered or reviewed" tone="success" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Delivered" value={deliveredCount} helper="Reports handed over" tone="info" />
        </Grid>
      </Grid>

      <Card variant="outlined" sx={{ boxShadow: "none" }}>
        <CardContent sx={compactCardContentSx}>
          <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ mb: 1 }}>
            <Tab label="Catalog" />
            <Tab label="Pending Sample Collection" />
            <Tab label="Work Queue / Result Entry" />
            <Tab label="Pending Lab Review" />
            <Tab label="Orders" />
          </Tabs>

          {tab === 0 ? (
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ alignItems: { xs: "stretch", md: "center" } }}>
                <TextField fullWidth size="small" label="Search tests" value={search} onChange={(e) => setSearch(e.target.value.slice(0, 60))} inputProps={{ maxLength: 60 }} />
                <Button variant="outlined" onClick={openCreate} disabled={!canManageTests} startIcon={<AddRoundedIcon />}>Add test</Button>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {categories.map((category) => <Chip key={category} size="small" label={category} variant="outlined" />)}
              </Stack>
              {!filteredTests.length ? (
                <CompactEmptyState title="No lab tests found" subtitle="Create the first lab test to start ordering." action={canManageTests ? <Button variant="contained" onClick={openCreate}>Create test</Button> : null} />
              ) : (
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small">
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
                      {filteredTests.map((row) => (
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
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
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
              onGenerateReport={generateReport}
              onPublishReport={openPublishDialog}
            />
          ) : tab === 2 ? (
            <OrderQueue
              rows={pendingResultsOrders}
              emptySubtitle="Collected or received samples ready for technician result entry will appear here."
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
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
              onGenerateReport={generateReport}
              onPublishReport={openPublishDialog}
            />
          ) : tab === 3 ? (
            <OrderQueue
              rows={pendingReviewOrders}
              emptySubtitle="Entered results waiting for lab verification will appear here."
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canManageSamples={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
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
              onGenerateReport={generateReport}
              onPublishReport={openPublishDialog}
            />
          ) : (
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
                                <Button size="small" variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => void generateReport(row)}>
                                  PDF
                                </Button>
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
          )}
        </CardContent>
      </Card>

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
                  {categories.length ? categories.map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>) : ["HEMATOLOGY", "BIOCHEMISTRY", "MICROBIOLOGY", "PATHOLOGY", "RADIOLOGY", "CARDIOLOGY", "OTHER"].map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>)}
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
                        label="Container Type"
                        value={row.containerType}
                        onChange={(e) => setSampleRows((current) => current.map((sampleRow, rowIndex) => rowIndex === index ? { ...sampleRow, containerType: e.target.value.slice(0, 128) } : sampleRow))}
                      />
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
            <TextField label="Collected By" value={sampleCollectedBy} onChange={(e) => setSampleCollectedBy(e.target.value.slice(0, 60))} inputProps={{ maxLength: 60 }} />
            <TextField
              label="Collected At"
              type="datetime-local"
              value={sampleCollectedAt}
              onChange={(e) => setSampleCollectedAt(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
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

      <Dialog open={Boolean(resultTarget)} onClose={() => setResultTarget(null)} fullWidth maxWidth="lg">
        <DialogTitle>Enter Results</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {resultTarget ? `${resultTarget.orderNumber} • ${resultTarget.patientName || "-"} • ${resultTarget.sampleAccessionNumber || "Accession pending"} • ${resultTarget.sampleSummaryStatus || "No sample"}` : ""}
            </Alert>
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
                            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Result Value" value={component.resultValue} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, resultValue: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
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
                        <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth size="small" label="Result Value" value={item.resultValue} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, resultValue: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Unit" value={item.unit} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, unit: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Reference Range" value={item.referenceRange} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, referenceRange: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth size="small" label="Critical Range" value={item.criticalRange} disabled /></Grid>
                      </Grid>
                    )}
                  </CardContent>
                </Card>
              ))}
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResultTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveResults()} disabled={saving || !resultTarget}>Save Results</Button>
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
              options={tests}
              value={tests.filter((test) => requestForm.testIds.includes(test.id))}
              getOptionLabel={(option) => `${option.testName}${option.testCode ? ` (${option.testCode})` : ""}`}
              onChange={(_, value) => setRequestForm((current) => ({ ...current, testIds: value.map((row) => row.id) }))}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Lab tests"
                  helperText="Select one or more tests."
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
    </Stack>
  );
}

function OrderQueue(props: {
  rows: LabOrder[];
  emptySubtitle: string;
  canCollectPayment: boolean;
  canCollectSample: boolean;
  canManageSamples: boolean;
  canEnterResults: boolean;
  canGenerateReport: boolean;
  canReviewReport: boolean;
  onCollectPayment: (row: LabOrder) => void;
  onCollectSample: (row: LabOrder) => void;
  onReceiveSample: (sample: LabSample) => void;
  onRejectSample: (sample: LabSample) => void;
  onEnterResults: (row: LabOrder) => void;
  onReview: (row: LabOrder) => void;
  onGenerateReport: (row: LabOrder) => void;
  onPublishReport: (row: LabOrder) => void;
}) {
  const { rows, emptySubtitle, canCollectPayment, canCollectSample, canManageSamples, canEnterResults, canGenerateReport, canReviewReport, onCollectPayment, onCollectSample, onReceiveSample, onRejectSample, onEnterResults, onReview, onGenerateReport, onPublishReport } = props;

  if (!rows.length) {
    return <CompactEmptyState title="No lab orders found" subtitle={emptySubtitle} />;
  }

  return (
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
                  {row.sampleCollectedAt ? <Typography variant="caption" color="text.secondary">Collected {new Date(row.sampleCollectedAt).toLocaleString()}</Typography> : null}
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.billNumber || "-"}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.billDueAmount != null ? `Due ${formatMoney(row.billDueAmount)}` : row.billStatus || "-"}</Typography>
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
                    <Button size="small" variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => onGenerateReport(row)}>
                      PDF
                    </Button>
                  ) : null}
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
  );
}
