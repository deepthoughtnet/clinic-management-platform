import * as React from "react";
import {
  Alert,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Checkbox,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  GlobalStyles,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Menu,
  Drawer,
  Tab,
  Tabs,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import AccessTimeRoundedIcon from "@mui/icons-material/AccessTimeRounded";
import BabyChangingStationRoundedIcon from "@mui/icons-material/BabyChangingStationRounded";
import CalendarMonthRoundedIcon from "@mui/icons-material/CalendarMonthRounded";
import ChildCareRoundedIcon from "@mui/icons-material/ChildCareRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import DoNotDisturbOnRoundedIcon from "@mui/icons-material/DoNotDisturbOnRounded";
import ElderlyRoundedIcon from "@mui/icons-material/ElderlyRounded";
import EditRoundedIcon from "@mui/icons-material/EditRounded";
import InfoRoundedIcon from "@mui/icons-material/InfoRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import MoreVertRoundedIcon from "@mui/icons-material/MoreVertRounded";
import PersonRoundedIcon from "@mui/icons-material/PersonRounded";
import SchoolRoundedIcon from "@mui/icons-material/SchoolRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import { useNavigate } from "react-router-dom";

import { fileUploadSchema, firstZodError, mapZodErrors, vaccinationMasterSchema, vaccinationRecordSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { resolveEnabledTenantModules } from "../../modules/moduleRegistry";
import { CompactEmptyState, CompactStatCard, CompactTableFrame, WorkflowStrip, compactAccordionSx, compactCardContentSx, compactChipSx } from "../../components/compact/CompactUi";
import ConsultationFeeDialog, { type ConsultationFeeDialogValue } from "../../components/ConsultationFeeDialog";
import RequiredLabel from "../../components/forms/RequiredLabel";
import { ReceiptPrintDialog, type ReceiptPrintData } from "../../components/finance/PrintableBillingDocuments";
import {
  addBillPayment,
  createVaccine,
  deactivateVaccine,
  exportVaccinesCsv,
  getDueVaccinations,
  getBill,
  getClinicProfile,
  getClinicUsers,
  getMedicines,
  getStocks,
  getPatientNotifications,
  getOverdueVaccinations,
  getReceiptPdf,
  getVaccinationHistory,
  getVaccinationRecommendations,
  generateVaccinationCertificate,
  generateVaccinationPassport,
  getVaccinationDocumentPdf,
  getVaccineImportTemplate,
  getVaccines,
  billPatientVaccination,
  getPatientDocuments,
  getPatientDocumentViewUrl,
  getPatientVaccinations,
  importVaccinesCsv,
  uploadPatientDocument,
  searchBills,
  queueVaccinationReminders,
  recordPatientVaccination,
  updatePatientVaccinationAefi,
  listBillPayments,
  listBillReceipts,
  sendReceipt,
  sendVaccinationDocument,
  verifyPatientVaccination,
  searchPatients,
  updateVaccine,
  type ClinicalDocument,
  type Bill,
  type ClinicProfile,
  type ClinicUser,
  type GeneratedVaccinationDocumentResponse,
  type Medicine,
  type NotificationHistory,
  type Patient,
  type PatientVaccination,
  type Payment,
  type Stock,
  type Receipt,
  type VaccineCsvImportResponse,
  type VaccineInput,
  type VaccineMaster,
  type VaccinationRecommendation,
  type VaccinationRecommendationSummary,
} from "../../api/clinicApi";
import { buildVaccineExportCsv, buildVaccineTemplateCsv, parseVaccineImportPreview, type VaccineImportPreview } from "./vaccinationCsv";
import { staffDisplayName } from "../../utils/staffDisplay";

type VaccineFormState = VaccineInput;

type RecommendationSection = keyof Pick<
  VaccinationRecommendationSummary,
  "recommendedToday" | "overdue" | "upcoming" | "completed" | "optionalRiskBased" | "notApplicable"
>;

function normalizeRole(role: string | null | undefined): string {
  return (role || "").trim().replace(/[-\s]+/g, "_").toUpperCase();
}

type VaccinationFormState = {
  patientId: string;
  vaccineId: string;
  doseNumber: string;
  givenDate: string;
  nextDueDate: string;
  batchNumber: string;
  stockBatchId: string;
  notes: string;
  administeredByUserId: string;
  route: string;
  administrationSite: string;
  addToBill: boolean;
  billItemUnitPrice: string;
};

type ExternalVaccinationFormState = {
  vaccineName: string;
  vaccineId: string;
  doseNumber: string;
  givenDate: string;
  externalPlace: string;
  batchNumber: string;
  brandName: string;
  manufacturer: string;
  notes: string;
  proofDocumentId: string;
  proofFile: File | null;
  source: "EXTERNAL";
  verifiedStatus: "UNVERIFIED" | "VERIFIED" | "REJECTED";
};

function emptyVaccineForm(): VaccineFormState {
  return {
    vaccineName: "",
    description: null,
    manufacturer: null,
    brandName: null,
    vaccineGroup: null,
    doseNumber: null,
    route: null,
    administrationSite: null,
    storageTemperature: null,
    ndcBarcode: null,
    inventoryItemId: null,
    inventoryItemCode: null,
    stockTrackingEnabled: false,
    scheduleType: null,
    ageGroup: null,
    minAgeDays: null,
    recommendedAgeDays: null,
    maxAgeDays: null,
    gapDays: null,
    recommendedGapDays: null,
    defaultPrice: null,
    boosterGapDays: null,
    boosterRules: null,
    recurring: false,
    recurrenceDays: null,
    recommendationPolicy: null,
    catchUpPolicy: null,
    catchUpMaxAgeDays: null,
    applicableAgeGroup: null,
    clinicalIndications: null,
    active: true,
  };
}

function formForVaccine(vaccine: VaccineMaster): VaccineFormState {
  return {
    vaccineName: vaccine.vaccineName,
    description: vaccine.description,
    manufacturer: vaccine.manufacturer,
    brandName: vaccine.brandName,
    vaccineGroup: vaccine.vaccineGroup,
    doseNumber: vaccine.doseNumber,
    route: vaccine.route,
    administrationSite: vaccine.administrationSite,
    storageTemperature: vaccine.storageTemperature,
    ndcBarcode: vaccine.ndcBarcode,
    inventoryItemId: vaccine.inventoryItemId,
    inventoryItemCode: vaccine.inventoryItemCode,
    stockTrackingEnabled: vaccine.stockTrackingEnabled,
    scheduleType: vaccine.scheduleType,
    ageGroup: vaccine.ageGroup,
    minAgeDays: vaccine.minAgeDays,
    recommendedAgeDays: vaccine.recommendedAgeDays,
    maxAgeDays: vaccine.maxAgeDays,
    gapDays: vaccine.gapDays ?? vaccine.recommendedGapDays,
    recommendedGapDays: vaccine.recommendedGapDays ?? vaccine.gapDays,
    defaultPrice: vaccine.defaultPrice,
    boosterGapDays: vaccine.boosterGapDays,
    boosterRules: vaccine.boosterRules,
    recurring: vaccine.recurring,
    recurrenceDays: vaccine.recurrenceDays,
    recommendationPolicy: vaccine.recommendationPolicy,
    catchUpPolicy: vaccine.catchUpPolicy,
    catchUpMaxAgeDays: vaccine.catchUpMaxAgeDays,
    applicableAgeGroup: vaccine.applicableAgeGroup,
    clinicalIndications: vaccine.clinicalIndications,
    active: vaccine.active,
  };
}

function downloadCsv(filename: string, csv: string) {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

function emptyVaccinationForm(): VaccinationFormState {
  return {
    patientId: "",
    vaccineId: "",
    doseNumber: "",
    givenDate: new Date().toISOString().slice(0, 10),
    nextDueDate: "",
    batchNumber: "",
    stockBatchId: "",
    notes: "",
    administeredByUserId: "",
    route: "",
    administrationSite: "",
    addToBill: false,
    billItemUnitPrice: "",
  };
}

function billEffectiveDueAmount(bill: Bill) {
  return Math.max(0, bill.dueAmount ?? (bill.totalAmount - bill.paidAmount));
}

function billHasCollectableDue(bill: Bill) {
  return billEffectiveDueAmount(bill) > 0;
}

function billCountsAsSettled(bill: Bill) {
  return bill.status === "PAID" || bill.status === "PARTIALLY_REFUNDED" || bill.status === "REFUNDED" || bill.status === "CANCELLED_REFUNDED";
}

function billStatusLabel(status: Bill["status"]) {
  switch (status) {
    case "DRAFT":
      return "Bill Created";
    case "UNPAID":
      return "Unbilled";
    case "ISSUED":
      return "Bill Created";
    case "PARTIALLY_PAID":
      return "Bill Created";
    case "PAID":
      return "Paid";
    case "REFUND_PENDING":
      return "Refund Pending";
    case "PARTIALLY_REFUNDED":
      return "Partially Refunded";
    case "REFUNDED":
      return "Refunded";
    case "CANCELLED":
      return "Cancelled";
    case "CANCELLED_REFUNDED":
      return "Cancelled";
    default:
      return status;
  }
}

function billStatusColor(status: Bill["status"]) {
  switch (status) {
    case "PAID":
      return "success";
    case "PARTIALLY_PAID":
    case "UNPAID":
    case "DRAFT":
    case "ISSUED":
      return "warning";
    case "REFUND_PENDING":
    case "PARTIALLY_REFUNDED":
      return "secondary";
    case "REFUNDED":
      return "info";
    case "CANCELLED":
    case "CANCELLED_REFUNDED":
      return "default";
    default:
      return "default";
  }
}

type VaccinationBillingStatus = {
  primaryLabel: string;
  primaryColor: "default" | "warning" | "success" | "info" | "secondary";
  badges: Array<{ label: string; color: "default" | "warning" | "success" | "info" | "secondary" }>;
  isUnbilled: boolean;
  hasPaymentDue: boolean;
  isPaid: boolean;
  isRefundedOrCancelled: boolean;
  hasReceipt: boolean;
};

type VaccinationHistoryBillingFilter = "ALL" | "UNBILLED" | "BILL_CREATED" | "PAYMENT_PENDING" | "PAID" | "REFUNDED" | "CANCELLED";

type VaccinationDocumentReference = {
  documentId: string;
  title: string;
  filename: string;
  documentNumber?: string;
  generatedAt?: string;
  generatedBy?: string;
};

function vaccinationDocumentReference(document: ClinicalDocument | GeneratedVaccinationDocumentResponse): VaccinationDocumentReference {
  if ("downloadUrl" in document) {
    return {
      documentId: document.documentId,
      title: document.title,
      filename: document.filename,
      documentNumber: document.documentNumber,
      generatedAt: document.generatedAt,
      generatedBy: document.generatedBy,
    };
  }
  return {
    documentId: document.id,
    title: document.title,
    filename: document.originalFilename || `${document.title}.pdf`,
    generatedAt: document.createdAt,
    generatedBy: document.uploadedByName,
  };
}

function vaccinationBillingStatus(row: PatientVaccination): VaccinationBillingStatus {
  const status = (row.billStatus || "").toUpperCase();
  const hasBill = Boolean(row.billId || row.billNumber);
  const base = {
    primaryLabel: "Unbilled",
    primaryColor: "default" as const,
    badges: [{ label: "Unbilled", color: "default" as const }],
    isUnbilled: true,
    hasPaymentDue: false,
    isPaid: false,
    isRefundedOrCancelled: false,
    hasReceipt: false,
  };
  if (!hasBill) {
    return base;
  }
  if (status === "PAID") {
    return {
      primaryLabel: "View Receipt",
      primaryColor: "success",
      badges: [
        { label: "Paid", color: "success" },
        { label: "Receipt Ready", color: "success" },
      ],
      isUnbilled: false,
      hasPaymentDue: false,
      isPaid: true,
      isRefundedOrCancelled: false,
      hasReceipt: true,
    };
  }
  if (status === "REFUNDED" || status === "PARTIALLY_REFUNDED" || status === "CANCELLED_REFUNDED") {
    return {
      primaryLabel: "View Bill",
      primaryColor: "info",
      badges: [
        { label: "Refunded", color: "info" },
        { label: "Receipt Ready", color: "success" },
      ],
      isUnbilled: false,
      hasPaymentDue: false,
      isPaid: false,
      isRefundedOrCancelled: true,
      hasReceipt: true,
    };
  }
  if (status === "CANCELLED") {
    return {
      primaryLabel: "View Bill",
      primaryColor: "default",
      badges: [{ label: "Cancelled", color: "default" }],
      isUnbilled: false,
      hasPaymentDue: false,
      isPaid: false,
      isRefundedOrCancelled: true,
      hasReceipt: false,
    };
  }
  return {
    primaryLabel: "Collect Payment",
    primaryColor: "warning",
    badges: [
      { label: "Bill Created", color: "warning" },
      { label: "Payment Pending", color: "warning" },
    ],
    isUnbilled: false,
    hasPaymentDue: true,
    isPaid: false,
    isRefundedOrCancelled: false,
    hasReceipt: false,
  };
}

function vaccinationBillingStatusKey(row: PatientVaccination): VaccinationHistoryBillingFilter {
  const status = (row.billStatus || "").toUpperCase();
  const hasBill = Boolean(row.billId || row.billNumber);
  if (!hasBill) {
    return "UNBILLED";
  }
  if (status === "PAID") return "PAID";
  if (status === "REFUNDED" || status === "PARTIALLY_REFUNDED" || status === "CANCELLED_REFUNDED") return "REFUNDED";
  if (status === "CANCELLED") return "CANCELLED";
  if (status === "DRAFT" || status === "ISSUED" || status === "PARTIALLY_PAID") return "BILL_CREATED";
  return "PAYMENT_PENDING";
}

function vaccinationAefiBadge(row: PatientVaccination) {
  const severity = String(row.adverseEventSeverity || "").toUpperCase();
  const status = String(row.adverseEventStatus || "").toUpperCase();
  if (!status || status === "NONE") return { label: "No AEFI", color: "default" as const };
  if (row.adverseEventFollowUpRequired && row.adverseEventFollowUpDate && status !== "RESOLVED" && !severity) {
    return { label: "Follow-up due", color: "warning" as const };
  }
  switch (severity) {
    case "MILD":
      return { label: "Mild AEFI", color: "success" as const };
    case "MODERATE":
      return { label: "Moderate AEFI", color: "warning" as const };
    case "SEVERE":
      return { label: "Severe AEFI", color: "error" as const };
    case "SERIOUS":
      return { label: "Serious AEFI", color: "error" as const };
    default:
      if (status === "RESOLVED") return { label: "Resolved", color: "success" as const };
      return { label: "AEFI", color: "info" as const };
  }
}

function vaccinationBillingFilterLabel(filter: VaccinationHistoryBillingFilter) {
  switch (filter) {
    case "UNBILLED":
      return "Unbilled";
    case "BILL_CREATED":
      return "Bill Created";
    case "PAYMENT_PENDING":
      return "Payment Pending";
    case "PAID":
      return "Paid";
    case "REFUNDED":
      return "Refunded";
    case "CANCELLED":
      return "Cancelled";
    default:
      return "All";
  }
}

function latestReceiptForBill(receipts: Receipt[]) {
  return receipts.slice().sort((left, right) => `${right.receiptDate}|${right.createdAt}`.localeCompare(`${left.receiptDate}|${left.createdAt}`))[0] || null;
}

type VaccinationMilestone = {
  label: string;
  minDays: number;
  maxDays: number;
};

type VaccinationRoadmapStageKey = "newborn" | "infant" | "toddler" | "childhood" | "schoolAge" | "adult" | "senior";

type VaccinationRoadmapStageDefinition = {
  key: VaccinationRoadmapStageKey;
  label: string;
  helper: string;
  icon: React.ReactNode;
  minDays: number;
  maxDays: number;
  milestoneLabels: string[];
};

type VaccinationTimelineItem = VaccinationRecommendation & {
  source: "RECOMMENDATION" | "EXTERNAL_HISTORY";
  milestoneLabel: string;
  roadmapStageKey: VaccinationRoadmapStageKey;
  milestoneTone: "success" | "warning" | "error" | "default" | "info" | "secondary";
};

type VaccinationTimelineMilestoneGroup = {
  label: string;
  items: VaccinationTimelineItem[];
};

type VaccinationRoadmapStage = VaccinationRoadmapStageDefinition & {
  items: VaccinationTimelineItem[];
  milestoneGroups: VaccinationTimelineMilestoneGroup[];
  completedCount: number;
  dueCount: number;
  overdueCount: number;
  upcomingCount: number;
  optionalCount: number;
  notApplicableCount: number;
  isCurrent: boolean;
  hasDueOrOverdue: boolean;
  isEmpty: boolean;
};

type VaccinationTimelineSection = VaccinationRoadmapStage;

const VACCINATION_MILESTONES: VaccinationMilestone[] = [
  { label: "Birth", minDays: 0, maxDays: 41 },
  { label: "6 Weeks", minDays: 42, maxDays: 69 },
  { label: "10 Weeks", minDays: 70, maxDays: 97 },
  { label: "14 Weeks", minDays: 98, maxDays: 181 },
  { label: "6 Months", minDays: 182, maxDays: 273 },
  { label: "9 Months", minDays: 274, maxDays: 364 },
  { label: "12 Months", minDays: 365, maxDays: 455 },
  { label: "15 Months", minDays: 456, maxDays: 547 },
  { label: "18 Months", minDays: 548, maxDays: 730 },
  { label: "5 Years", minDays: 731, maxDays: 3284 },
  { label: "10 Years", minDays: 3285, maxDays: 6570 },
  { label: "Adult", minDays: 6571, maxDays: Number.POSITIVE_INFINITY },
];

const VACCINATION_ROADMAP_STAGES: VaccinationRoadmapStageDefinition[] = [
  {
    key: "newborn",
    label: "Newborn",
    helper: "Birth",
    icon: <BabyChangingStationRoundedIcon fontSize="small" />,
    minDays: 0,
    maxDays: 41,
    milestoneLabels: ["Birth"],
  },
  {
    key: "infant",
    label: "Infant",
    helper: "6 Weeks - 12 Months",
    icon: <ChildCareRoundedIcon fontSize="small" />,
    minDays: 42,
    maxDays: 455,
    milestoneLabels: ["6 Weeks", "10 Weeks", "14 Weeks", "6 Months", "9 Months", "12 Months"],
  },
  {
    key: "toddler",
    label: "Toddler",
    helper: "15 Months - 2 Years",
    icon: <ChildCareRoundedIcon fontSize="small" />,
    minDays: 456,
    maxDays: 1095,
    milestoneLabels: ["15 Months", "18 Months"],
  },
  {
    key: "childhood",
    label: "Childhood",
    helper: "2 - 8 Years",
    icon: <PersonRoundedIcon fontSize="small" />,
    minDays: 1096,
    maxDays: 3284,
    milestoneLabels: ["5 Years"],
  },
  {
    key: "schoolAge",
    label: "School Age",
    helper: "9 - 16 Years",
    icon: <SchoolRoundedIcon fontSize="small" />,
    minDays: 3285,
    maxDays: 6570,
    milestoneLabels: ["10 Years"],
  },
  {
    key: "adult",
    label: "Adult",
    helper: "Adult routine vaccines",
    icon: <PersonRoundedIcon fontSize="small" />,
    minDays: 6571,
    maxDays: 18249,
    milestoneLabels: ["Adult"],
  },
  {
    key: "senior",
    label: "Senior",
    helper: "50+ / 60+ vaccines",
    icon: <ElderlyRoundedIcon fontSize="small" />,
    minDays: 18250,
    maxDays: Number.POSITIVE_INFINITY,
    milestoneLabels: ["Adult"],
  },
];

const VACCINATION_SCHEDULE_TYPES = ["UIP", "IAP", "CDC", "WHO", "CLINIC_CUSTOM", "ADULT", "TRAVEL"] as const;

type VaccinationScheduleTypeFilter = typeof VACCINATION_SCHEDULE_TYPES[number];

type VaccinationSchedulePolicyFilter = "ALL" | "MANDATORY" | "OPTIONAL" | "RISK_BASED";

type VaccinationScheduleMilestone = {
  label: string;
  centerDays: number;
};

type VaccinationScheduleItem = VaccineMaster & {
  milestoneLabel: string;
};

type VaccinationScheduleMilestoneGroup = {
  label: string;
  items: VaccinationScheduleItem[];
};

const VACCINATION_SCHEDULE_MILESTONES: VaccinationScheduleMilestone[] = [
  { label: "Birth", centerDays: 0 },
  { label: "6 Weeks", centerDays: 42 },
  { label: "10 Weeks", centerDays: 70 },
  { label: "14 Weeks", centerDays: 98 },
  { label: "6 Months", centerDays: 182 },
  { label: "9 Months", centerDays: 274 },
  { label: "12 Months", centerDays: 365 },
  { label: "15 Months", centerDays: 456 },
  { label: "18 Months", centerDays: 548 },
  { label: "2 Years", centerDays: 730 },
  { label: "5 Years", centerDays: 1825 },
  { label: "9 Years", centerDays: 3285 },
  { label: "10 Years", centerDays: 3650 },
  { label: "16 Years", centerDays: 5840 },
  { label: "Adult", centerDays: 6570 },
  { label: "Senior", centerDays: 18250 },
];

function normalizeScheduleToken(value: string | null | undefined) {
  return String(value || "").trim().replace(/[_-]+/g, " ").replace(/\s+/g, " ").toUpperCase();
}

function schedulePolicyMatches(policy: string | null | undefined, filter: VaccinationSchedulePolicyFilter) {
  if (filter === "ALL") {
    return true;
  }
  const normalized = normalizeScheduleToken(policy);
  if (!normalized) {
    return filter === "MANDATORY";
  }
  if (filter === "MANDATORY") {
    return !normalized.includes("OPTIONAL") && !normalized.includes("RISK");
  }
  if (filter === "OPTIONAL") {
    return normalized.includes("OPTIONAL");
  }
  return normalized.includes("RISK");
}

function scheduleMilestoneLabelForVaccine(vaccine: VaccineMaster) {
  const ageGroup = normalizeScheduleToken(vaccine.ageGroup);
  const recommendedAgeDays = vaccine.recommendedAgeDays ?? vaccine.minAgeDays ?? vaccine.gapDays ?? vaccine.recommendedGapDays ?? null;
  const ageGroupToMilestone: Array<[string, string]> = [
    ["BIRTH", "Birth"],
    ["6 WEEK", "6 Weeks"],
    ["10 WEEK", "10 Weeks"],
    ["14 WEEK", "14 Weeks"],
    ["6 MONTH", "6 Months"],
    ["9 MONTH", "9 Months"],
    ["12 MONTH", "12 Months"],
    ["15 MONTH", "15 Months"],
    ["18 MONTH", "18 Months"],
    ["2 YEAR", "2 Years"],
    ["5 YEAR", "5 Years"],
    ["9 YEAR", "9 Years"],
    ["10 YEAR", "10 Years"],
    ["16 YEAR", "16 Years"],
    ["ADULT", "Adult"],
    ["SENIOR", "Senior"],
    ["NEWBORN", "Birth"],
    ["INFANT", "6 Weeks"],
    ["TODDLER", "15 Months"],
    ["CHILD", "5 Years"],
    ["SCHOOL", "9 Years"],
  ];
  for (const [token, label] of ageGroupToMilestone) {
    if (ageGroup.includes(token)) {
      return label;
    }
  }
  if (recommendedAgeDays != null) {
    const nearest = VACCINATION_SCHEDULE_MILESTONES
      .slice()
      .sort((left, right) => Math.abs(left.centerDays - recommendedAgeDays) - Math.abs(right.centerDays - recommendedAgeDays))[0];
    return nearest?.label || "Adult";
  }
  if (normalizeScheduleToken(vaccine.scheduleType) === "ADULT") {
    return "Adult";
  }
  return "Adult";
}

function buildVaccinationScheduleCalendar(vaccines: VaccineMaster[], options: {
  scheduleType: VaccinationScheduleTypeFilter;
  ageGroup: string;
  route: string;
  vaccineGroup: string;
  policy: VaccinationSchedulePolicyFilter;
  activeOnly: boolean;
}) {
  const normalizedScheduleType = normalizeScheduleToken(options.scheduleType);
  const normalizedAgeGroup = normalizeScheduleToken(options.ageGroup);
  const normalizedRoute = normalizeScheduleToken(options.route);
  const normalizedGroup = normalizeScheduleToken(options.vaccineGroup);
  const filtered = vaccines.filter((vaccine) => {
    if (options.activeOnly && !vaccine.active) {
      return false;
    }
    if (normalizeScheduleToken(vaccine.scheduleType) !== normalizedScheduleType) {
      return false;
    }
    if (normalizedAgeGroup && !normalizeScheduleToken(vaccine.ageGroup).includes(normalizedAgeGroup)) {
      return false;
    }
    if (normalizedRoute && !normalizeScheduleToken(vaccine.route).includes(normalizedRoute)) {
      return false;
    }
    if (normalizedGroup && !normalizeScheduleToken(vaccine.vaccineGroup).includes(normalizedGroup)) {
      return false;
    }
    if (!schedulePolicyMatches(vaccine.recommendationPolicy, options.policy)) {
      return false;
    }
    return true;
  });

  const byMilestone = new Map<string, VaccinationScheduleItem[]>();
  VACCINATION_SCHEDULE_MILESTONES.forEach((milestone) => byMilestone.set(milestone.label, []));

  filtered.forEach((vaccine) => {
    const milestoneLabel = scheduleMilestoneLabelForVaccine(vaccine);
    const items = byMilestone.get(milestoneLabel) || [];
    items.push({ ...vaccine, milestoneLabel });
    byMilestone.set(milestoneLabel, items);
  });

  const groups: VaccinationScheduleMilestoneGroup[] = VACCINATION_SCHEDULE_MILESTONES.map((milestone) => ({
    label: milestone.label,
    items: (byMilestone.get(milestone.label) || []).sort((left, right) => {
      const leftAge = left.recommendedAgeDays ?? left.minAgeDays ?? Number.POSITIVE_INFINITY;
      const rightAge = right.recommendedAgeDays ?? right.minAgeDays ?? Number.POSITIVE_INFINITY;
      if (leftAge !== rightAge) {
        return leftAge - rightAge;
      }
      return `${left.vaccineName}|${left.doseNumber ?? ""}`.localeCompare(`${right.vaccineName}|${right.doseNumber ?? ""}`);
    }),
  }));

  return {
    filtered,
    groups,
  };
}

const ADULT_VISIBLE_MIN_DAYS = 6571;

function roadmapStageForDays(days: number | null | undefined): VaccinationRoadmapStageDefinition {
  if (days === null || days === undefined || Number.isNaN(days)) {
    return VACCINATION_ROADMAP_STAGES[0];
  }
  return VACCINATION_ROADMAP_STAGES.find((stage) => days >= stage.minDays && days <= stage.maxDays)
    || VACCINATION_ROADMAP_STAGES[VACCINATION_ROADMAP_STAGES.length - 1];
}

function roadmapStageForKey(key: VaccinationRoadmapStageKey) {
  return VACCINATION_ROADMAP_STAGES.find((stage) => stage.key === key) || VACCINATION_ROADMAP_STAGES[0];
}

function statusTone(status: VaccinationRecommendation["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "DUE":
      return "warning";
    case "OVERDUE":
      return "error";
    case "UPCOMING":
      return "default";
    case "OPTIONAL_RISK_BASED":
      return "secondary";
    default:
      return "default";
  }
}

function statusMeta(status: VaccinationRecommendation["status"]) {
  switch (status) {
    case "COMPLETED":
      return { label: "Completed", tone: "success" as const, icon: <CheckCircleRoundedIcon fontSize="inherit" /> };
    case "DUE":
      return { label: "Due", tone: "warning" as const, icon: <AccessTimeRoundedIcon fontSize="inherit" /> };
    case "OVERDUE":
      return { label: "Overdue", tone: "error" as const, icon: <WarningAmberRoundedIcon fontSize="inherit" /> };
    case "UPCOMING":
      return { label: "Upcoming", tone: "info" as const, icon: <CalendarMonthRoundedIcon fontSize="inherit" /> };
    case "OPTIONAL_RISK_BASED":
      return { label: "Optional / Risk-Based", tone: "secondary" as const, icon: <InfoRoundedIcon fontSize="inherit" /> };
    default:
      return { label: "Not applicable", tone: "default" as const, icon: <DoNotDisturbOnRoundedIcon fontSize="inherit" /> };
  }
}

function milestoneSortWeight(label: string) {
  const index = VACCINATION_MILESTONES.findIndex((milestone) => milestone.label === label);
  return index >= 0 ? index : VACCINATION_MILESTONES.length;
}

function roadmapStageSortWeight(key: VaccinationRoadmapStageKey) {
  return VACCINATION_ROADMAP_STAGES.findIndex((stage) => stage.key === key);
}

function timelineItemSort(left: VaccinationTimelineItem, right: VaccinationTimelineItem) {
  const statusOrder: Record<VaccinationRecommendation["status"], number> = {
    OVERDUE: 0,
    DUE: 1,
    UPCOMING: 2,
    OPTIONAL_RISK_BASED: 3,
    COMPLETED: 4,
    NOT_APPLICABLE: 5,
  };
  const leftStatus = statusOrder[left.status];
  const rightStatus = statusOrder[right.status];
  if (leftStatus !== rightStatus) {
    return leftStatus - rightStatus;
  }
  const leftAge = left.recommendedAgeDays ?? left.patientAgeDays ?? Number.POSITIVE_INFINITY;
  const rightAge = right.recommendedAgeDays ?? right.patientAgeDays ?? Number.POSITIVE_INFINITY;
  if (leftAge !== rightAge) {
    return leftAge - rightAge;
  }
  return `${left.vaccineName}|${left.doseNumber ?? ""}`.localeCompare(`${right.vaccineName}|${right.doseNumber ?? ""}`);
}

function ageLabelFromDays(days: number | null | undefined) {
  if (days == null || Number.isNaN(days)) {
    return "Age unavailable";
  }
  const totalMonths = Math.max(0, Math.round(days / 30.4375));
  const years = Math.floor(totalMonths / 12);
  const months = totalMonths % 12;
  if (years <= 0) {
    return `${Math.max(0, totalMonths)} month${totalMonths === 1 ? "" : "s"}`;
  }
  if (months <= 0) {
    return `${years} year${years === 1 ? "" : "s"}`;
  }
  return `${years} year${years === 1 ? "" : "s"} ${months} month${months === 1 ? "" : "s"}`;
}

function formatScheduleType(scheduleType: string | null | undefined) {
  if (!scheduleType) {
    return "Standard";
  }
  return scheduleType
    .toString()
    .trim()
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase());
}

function VaccinationRoadmapStrip({
  stages,
  currentStageKey,
}: {
  stages: readonly VaccinationRoadmapStageDefinition[];
  currentStageKey: VaccinationRoadmapStageKey;
}) {
  const currentIndex = stages.findIndex((stage) => stage.key === currentStageKey);
  const stripLabels: Record<VaccinationRoadmapStageKey, string> = {
    newborn: "Birth",
    infant: "Infant",
    toddler: "Toddler",
    childhood: "Child",
    schoolAge: "School Age",
    adult: "Adult",
    senior: "Senior",
  };
  return (
    <WorkflowStrip
      label="Life stage roadmap"
      currentStepIndex={currentIndex >= 0 ? currentIndex : undefined}
      steps={stages.map((stage) => ({
        label: (
          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="center">
            {stage.icon}
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, lineHeight: 1.1 }} noWrap>
                {stripLabels[stage.key]}
              </Typography>
            </Box>
          </Stack>
        ),
        helper: stage.helper,
      }))}
    />
  );
}

function VaccinationTimelineItemCard({
  item,
  onSelectVaccine,
  onRecordVaccination,
  onViewHistory,
}: {
  item: VaccinationTimelineItem;
  onSelectVaccine: (item: VaccinationTimelineItem) => void;
  onRecordVaccination: (item: VaccinationTimelineItem) => void;
  onViewHistory: () => void;
}) {
  const meta = statusMeta(item.status);
  const canAct = item.status === "DUE" || item.status === "OVERDUE" || item.status === "UPCOMING";
  const timingLabel = item.status === "COMPLETED"
    ? `Completed ${item.completedDate || item.dueDate || "-"}`
    : item.dueDate
      ? `Due ${item.dueDate}`
      : item.reasonText;
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, bgcolor: "background.paper" }}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={0.9}>
          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.25, alignItems: "flex-start", flexWrap: "wrap" }}>
            <Box sx={{ minWidth: 0, flex: "1 1 280px" }}>
              <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap>
                {item.vaccineName}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {[
                  `Dose ${item.doseNumber ?? "-"}`,
                  item.brandName,
                  item.manufacturer,
                ].filter(Boolean).join(" • ") || "Dose details unavailable"}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {[
                  item.route ? `Route ${item.route}` : null,
                  item.administrationSite ? `Site ${item.administrationSite}` : null,
                  item.milestoneLabel ? `${item.milestoneLabel}` : null,
                ].filter(Boolean).join(" • ") || "Schedule details unavailable"}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {item.reasonText}
              </Typography>
            </Box>
            <Stack spacing={0.5} alignItems="flex-end">
              <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                <Chip size="small" icon={meta.icon as any} label={meta.label} color={meta.tone} variant="outlined" sx={compactChipSx} />
                <Chip
                  size="small"
                  label={item.source === "EXTERNAL_HISTORY" ? "External" : "Internal"}
                  color={item.source === "EXTERNAL_HISTORY" ? "secondary" : "default"}
                  variant="outlined"
                  sx={compactChipSx}
                />
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {timingLabel}
              </Typography>
            </Stack>
          </Box>
          <Stack direction="row" spacing={0.75} flexWrap="wrap">
            {canAct ? (
              <Button size="small" variant="outlined" onClick={() => onSelectVaccine(item)}>
                Select Vaccine
              </Button>
            ) : null}
            {canAct ? (
              <Button size="small" variant="outlined" onClick={() => onRecordVaccination(item)}>
                Record Vaccination
              </Button>
            ) : null}
            <Button size="small" variant="text" onClick={onViewHistory}>
              View History
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

function VaccinationScheduleItemCard({
  vaccine,
  onOpenDetails,
}: {
  vaccine: VaccinationScheduleItem;
  onOpenDetails: (vaccine: VaccinationScheduleItem) => void;
}) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, bgcolor: "background.paper" }}>
      <CardActionArea onClick={() => onOpenDetails(vaccine)} sx={{ alignItems: "stretch" }}>
        <CardContent sx={compactCardContentSx}>
          <Stack spacing={0.75}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "flex-start" }}>
              <Box sx={{ minWidth: 0, flex: "1 1 280px" }}>
                <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap>
                  {vaccine.vaccineName}
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block">
                  {[
                    `Dose ${vaccine.doseNumber ?? "-"}`,
                    vaccine.brandName,
                    vaccine.manufacturer,
                  ].filter(Boolean).join(" • ") || "Dose details unavailable"}
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block">
                  {[
                    vaccine.route ? `Route ${vaccine.route}` : null,
                    vaccine.administrationSite ? `Site ${vaccine.administrationSite}` : null,
                    vaccine.milestoneLabel ? vaccine.milestoneLabel : null,
                  ].filter(Boolean).join(" • ") || "Schedule details unavailable"}
                </Typography>
              </Box>
              <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                <Chip size="small" label={formatScheduleType(vaccine.scheduleType)} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={vaccine.active ? "Active" : "Inactive"} color={vaccine.active ? "success" : "default"} variant="outlined" sx={compactChipSx} />
              </Stack>
            </Box>
            <Stack direction="row" spacing={0.5} flexWrap="wrap">
              <Chip size="small" label={`Min ${vaccine.minAgeDays ?? "-"}`} variant="outlined" sx={compactChipSx} />
              <Chip size="small" label={`Rec ${vaccine.recommendedAgeDays ?? vaccine.gapDays ?? "-"}`} variant="outlined" sx={compactChipSx} />
              <Chip size="small" label={`Max ${vaccine.maxAgeDays ?? "-"}`} variant="outlined" sx={compactChipSx} />
              <Chip size="small" label={`Price ${vaccine.defaultPrice != null ? vaccine.defaultPrice.toFixed(2) : "-"}`} variant="outlined" sx={compactChipSx} />
            </Stack>
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

function VaccinationScheduleDetailDrawer({
  vaccine,
  open,
  onClose,
}: {
  vaccine: VaccineMaster | null;
  open: boolean;
  onClose: () => void;
}) {
  return (
    <Drawer anchor="right" open={open} onClose={onClose} PaperProps={{ sx: { width: { xs: "100%", sm: 440, md: 520 }, p: 2 } }}>
      <Stack spacing={1.5} sx={{ height: "100%" }}>
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 900 }}>
            {vaccine?.vaccineName || "Vaccine details"}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {vaccine ? formatScheduleType(vaccine.scheduleType) : "Schedule reference"}
          </Typography>
        </Box>
        <Divider />
        {vaccine ? (
          <Stack spacing={1}>
            <Stack direction="row" spacing={0.75} flexWrap="wrap">
              <Chip size="small" label={vaccine.active ? "Active" : "Inactive"} color={vaccine.active ? "success" : "default"} variant="outlined" />
              <Chip size="small" label={vaccine.vaccineGroup || "No group"} variant="outlined" />
              <Chip size="small" label={vaccine.scheduleType || "Standard"} variant="outlined" />
            </Stack>
            <Typography variant="body2">{vaccine.description || "No description available."}</Typography>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Manufacturer</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.manufacturer || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Brand</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.brandName || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Dose number</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.doseNumber ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Route</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.route || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Administration site</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.administrationSite || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Storage temperature</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.storageTemperature || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">NDC / barcode</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.ndcBarcode || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Price</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.defaultPrice != null ? vaccine.defaultPrice.toFixed(2) : "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Minimum age</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.minAgeDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Recommended age</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.recommendedAgeDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Maximum age</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.maxAgeDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Gap days</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.gapDays ?? vaccine.recommendedGapDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Booster gap</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.boosterGapDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Booster rules</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.boosterRules || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Recurrence</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.recurring ? `${vaccine.recurrenceDays ?? "-"} days` : "No"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Recommendation policy</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.recommendationPolicy || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Catch-up policy</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.catchUpPolicy || "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Catch-up max age</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.catchUpMaxAgeDays ?? "-"}</Typography></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><Typography variant="caption" color="text.secondary">Clinical indications</Typography><Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.clinicalIndications || "-"}</Typography></Grid>
            </Grid>
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Select a vaccine to view schedule reference details.
          </Typography>
        )}
      </Stack>
    </Drawer>
  );
}

function milestoneForDays(days: number | null | undefined) {
  if (days === null || days === undefined || Number.isNaN(days)) {
    return VACCINATION_MILESTONES[VACCINATION_MILESTONES.length - 1];
  }
  return VACCINATION_MILESTONES.find((milestone) => days >= milestone.minDays && days <= milestone.maxDays)
    || VACCINATION_MILESTONES[VACCINATION_MILESTONES.length - 1];
}

function ageDaysFromPatient(patient: Patient | null | undefined) {
  if (!patient) return null;
  if (patient.dateOfBirth) {
    const birth = new Date(patient.dateOfBirth);
    if (!Number.isNaN(birth.getTime())) {
      return Math.max(0, Math.floor((Date.now() - birth.getTime()) / 86_400_000));
    }
  }
  if (patient.ageYears != null) {
    return Math.max(0, Math.floor(patient.ageYears * 365.25));
  }
  return null;
}

function ageDaysAtDate(patient: Patient | null | undefined, date: string | null | undefined) {
  if (!patient) return null;
  if (patient.dateOfBirth && date) {
    const birth = new Date(patient.dateOfBirth);
    const eventDate = new Date(date);
    if (!Number.isNaN(birth.getTime()) && !Number.isNaN(eventDate.getTime())) {
      return Math.max(0, Math.floor((eventDate.getTime() - birth.getTime()) / 86_400_000));
    }
  }
  return ageDaysFromPatient(patient);
}

function buildExternalHistoryTimelineItems(rows: PatientVaccination[], existingKeys: Set<string>, patient: Patient | null | undefined): VaccinationTimelineItem[] {
  return rows
    .filter((row) => String(row.source || "").toUpperCase() === "EXTERNAL")
    .map((row) => {
      const key = `${row.vaccineId || row.vaccineName}-${row.doseNumber ?? "x"}-COMPLETED`;
      if (existingKeys.has(key)) {
        return null;
      }
      const historyAgeDays = ageDaysAtDate(patient, row.givenDate);
      const milestone = milestoneForDays(historyAgeDays);
      const item: VaccinationTimelineItem = {
        vaccineId: row.vaccineId || row.id,
        vaccineName: row.vaccineName,
        brandName: row.inventoryBatchNumber || null,
        manufacturer: row.inventoryBatchManufacturer || null,
        vaccineGroup: null,
        doseNumber: row.doseNumber,
        route: null,
        administrationSite: null,
        scheduleType: null,
        dueDate: row.givenDate,
        status: "COMPLETED",
        overdueDays: null,
        recommendedAgeDays: null,
        patientAgeDays: historyAgeDays,
        patientAgeGroup: null,
        reasonText: row.externalPlace ? `External vaccination at ${row.externalPlace}` : "External vaccination history",
        completedDate: row.givenDate,
        source: "EXTERNAL_HISTORY",
        milestoneLabel: milestone.label,
        roadmapStageKey: roadmapStageForDays(historyAgeDays).key,
        milestoneTone: "success",
      };
      return item;
    })
    .filter((item): item is VaccinationTimelineItem => Boolean(item));
}

function buildVaccinationTimelineSections(
  summary: VaccinationRecommendationSummary | null,
  patient: Patient | null,
  historyRows: PatientVaccination[],
  options: {
    showCompleted: boolean;
    showOptionalRiskBased: boolean;
    showNotApplicable: boolean;
  },
): VaccinationTimelineSection[] {
  const ageDays = ageDaysFromPatient(patient);
  const entries = [
    ...(summary?.recommendedToday || []),
    ...(summary?.overdue || []),
    ...(summary?.upcoming || []),
    ...(options.showCompleted ? (summary?.completed || []) : []),
    ...(options.showOptionalRiskBased ? (summary?.optionalRiskBased || []) : []),
    ...(options.showNotApplicable ? (summary?.notApplicable || []) : []),
  ].map((item) => ({ ...item, source: "RECOMMENDATION" as const }));
  const seen = new Set<string>();
  const normalized: VaccinationTimelineItem[] = [];
  entries.forEach((item) => {
    const key = `${item.vaccineId}-${item.doseNumber ?? "x"}-${item.status}`;
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    const milestone = milestoneForDays(item.recommendedAgeDays ?? item.patientAgeDays ?? ageDays);
    const roadmapStageKey = roadmapStageForDays(item.recommendedAgeDays ?? item.patientAgeDays ?? ageDays).key;
    normalized.push({
      ...item,
      source: item.source,
      milestoneLabel: milestone.label,
      roadmapStageKey,
      milestoneTone: statusTone(item.status),
    });
  });
  buildExternalHistoryTimelineItems(historyRows, seen, patient).forEach((item) => normalized.push(item));
  return VACCINATION_ROADMAP_STAGES.map((stage) => {
    const items = normalized
      .filter((item) => item.roadmapStageKey === stage.key)
      .sort(timelineItemSort);
    const milestoneGroups = stage.milestoneLabels
      .map((label) => ({
        label,
        items: items.filter((item) => item.milestoneLabel === label),
      }))
      .filter((group) => group.items.length > 0);
    const completedCount = items.filter((item) => item.status === "COMPLETED").length;
    const dueCount = items.filter((item) => item.status === "DUE").length;
    const overdueCount = items.filter((item) => item.status === "OVERDUE").length;
    const upcomingCount = items.filter((item) => item.status === "UPCOMING").length;
    const optionalCount = items.filter((item) => item.status === "OPTIONAL_RISK_BASED").length;
    const notApplicableCount = items.filter((item) => item.status === "NOT_APPLICABLE").length;
    return {
      ...stage,
      items,
      milestoneGroups,
      completedCount,
      dueCount,
      overdueCount,
      upcomingCount,
      optionalCount,
      notApplicableCount,
      isCurrent: roadmapStageForDays(ageDays).key === stage.key,
      hasDueOrOverdue: dueCount > 0 || overdueCount > 0,
      isEmpty: items.length === 0,
    };
  });
}

function timelineProgress(summary: VaccinationRecommendationSummary | null, historyRows: PatientVaccination[]) {
  const completedKeys = new Set((summary?.completed || []).map((item) => `${item.vaccineId}-${item.doseNumber ?? "x"}-COMPLETED`));
  const externalCompleted = historyRows
    .filter((row) => String(row.source || "").toUpperCase() === "EXTERNAL")
    .filter((row) => !completedKeys.has(`${row.vaccineId || row.id}-${row.doseNumber ?? "x"}-COMPLETED`))
    .length;
  const completed = (summary?.completed.length || 0) + externalCompleted;
  const applicable = (summary?.recommendedToday.length || 0)
    + (summary?.overdue.length || 0)
    + (summary?.upcoming.length || 0)
    + (summary?.completed.length || 0);
  const completionPercent = applicable > 0 ? Math.round((Math.min(completed, applicable) / applicable) * 100) : 0;
  const nextDue = summary?.recommendedToday[0] || summary?.overdue[0] || summary?.upcoming[0] || null;
  const overdueCount = summary?.overdue.length || 0;
  return { completed, applicable, completionPercent, nextDue, overdueCount };
}

function emptyExternalVaccinationForm(): ExternalVaccinationFormState {
  return {
    vaccineName: "",
    vaccineId: "",
    doseNumber: "",
    givenDate: new Date().toISOString().slice(0, 10),
    externalPlace: "",
    batchNumber: "",
    brandName: "",
    manufacturer: "",
    notes: "",
    proofDocumentId: "",
    proofFile: null,
    source: "EXTERNAL",
    verifiedStatus: "UNVERIFIED",
  };
}

function statusColor(date: string | null | undefined) {
  if (!date) {
    return "default";
  }
  return new Date(date) < new Date(new Date().toISOString().slice(0, 10)) ? "error" : "warning";
}

function dueStatusForVaccination(vaccination: PatientVaccination) {
  if (!vaccination.nextDueDate) return "NOT_DUE";
  return new Date(vaccination.nextDueDate) < new Date(new Date().toISOString().slice(0, 10)) ? "OVERDUE" : "DUE";
}

function dueStatusLabel(status: string) {
  switch (status) {
    case "OVERDUE":
      return "Overdue";
    case "DUE":
      return "Due";
    case "NOT_DUE":
      return "Not due";
    default:
      return "All";
  }
}

function dueStatusColor(status: string) {
  switch (status) {
    case "OVERDUE":
      return "error";
    case "DUE":
      return "warning";
    default:
      return "default";
  }
}

function canManageVaccineMasterAccess(auth: ReturnType<typeof useAuth>) {
  const tenantRole = normalizeRole(auth.tenantRole);
  const roles = auth.rolesUpper;
  return roles.includes("CLINIC_ADMIN")
    || roles.includes("TENANT_ADMIN")
    || roles.includes("VACCINE_MASTER_MANAGER")
    || ["CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER"].includes(tenantRole)
    || (roles.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
}

function recommendationStatusLabel(status: VaccinationRecommendation["status"]) {
  switch (status) {
    case "DUE":
      return "Recommended Today";
    case "OVERDUE":
      return "Overdue";
    case "UPCOMING":
      return "Upcoming";
    case "COMPLETED":
      return "Completed";
    case "OPTIONAL_RISK_BASED":
      return "Optional / Risk-Based";
    default:
      return "Not applicable";
  }
}

function recommendationStatusColor(status: VaccinationRecommendation["status"]) {
  switch (status) {
    case "DUE":
      return "warning";
    case "OVERDUE":
      return "error";
    case "UPCOMING":
      return "info";
    case "COMPLETED":
      return "success";
    case "OPTIONAL_RISK_BASED":
      return "secondary";
    default:
      return "default";
  }
}

export default function VaccinationsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const enabledModules = React.useMemo(() => resolveEnabledTenantModules(auth), [auth]);
  const canManageMaster = canManageVaccineMasterAccess(auth);
  const canBookAppointment = enabledModules.has("APPOINTMENTS") || enabledModules.has("CONSULTATION");
  const canManageVaccinationHistory = auth.hasPermission("vaccination.manage")
    || auth.hasPermission("vaccination.update")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("TENANT_ADMIN")
    || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canCollectVaccinationPayment = auth.hasPermission("payment.collect")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("BILLING_USER")
    || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canSendVaccinationReceipt = canCollectVaccinationPayment
    || auth.hasPermission("notification.send")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("BILLING_USER")
    || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canVerifyExternalVaccination = auth.hasPermission("vaccination.update")
    || auth.rolesUpper.includes("DOCTOR")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("TENANT_ADMIN")
    || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const [vaccines, setVaccines] = React.useState<VaccineMaster[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [dueRows, setDueRows] = React.useState<PatientVaccination[]>([]);
  const [overdueRows, setOverdueRows] = React.useState<PatientVaccination[]>([]);
  const [historyRows, setHistoryRows] = React.useState<PatientVaccination[]>([]);
  const [patientBills, setPatientBills] = React.useState<Bill[]>([]);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientSearchResults, setPatientSearchResults] = React.useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [patientHistoryRows, setPatientHistoryRows] = React.useState<PatientVaccination[]>([]);
  const [patientDocuments, setPatientDocuments] = React.useState<ClinicalDocument[]>([]);
  const [patientNotifications, setPatientNotifications] = React.useState<NotificationHistory[]>([]);
  const [vaccineForm, setVaccineForm] = React.useState<VaccineFormState>(emptyVaccineForm());
  const [vaccinationForm, setVaccinationForm] = React.useState<VaccinationFormState>(emptyVaccinationForm());
  const [externalVaccinationForm, setExternalVaccinationForm] = React.useState<ExternalVaccinationFormState>(emptyExternalVaccinationForm());
  const [vaccineFieldErrors, setVaccineFieldErrors] = React.useState<Record<string, string>>({});
  const [vaccinationFieldErrors, setVaccinationFieldErrors] = React.useState<Record<string, string>>({});
  const [externalFieldErrors, setExternalFieldErrors] = React.useState<Record<string, string>>({});
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [workflowNotice, setWorkflowNotice] = React.useState<string | null>(null);
  const [editingVaccineId, setEditingVaccineId] = React.useState<string | null>(null);
  const [importPreviewOpen, setImportPreviewOpen] = React.useState(false);
  const [importResultOpen, setImportResultOpen] = React.useState(false);
  const [importPreview, setImportPreview] = React.useState<VaccineImportPreview | null>(null);
  const [importPreviewFile, setImportPreviewFile] = React.useState<File | null>(null);
  const [importResult, setImportResult] = React.useState<VaccineCsvImportResponse | null>(null);
  const [historyPatientFilter, setHistoryPatientFilter] = React.useState("");
  const [historyVaccineFilter, setHistoryVaccineFilter] = React.useState("");
  const [historyFromDate, setHistoryFromDate] = React.useState("");
  const [historyToDate, setHistoryToDate] = React.useState("");
  const [historyDueStatus, setHistoryDueStatus] = React.useState<"ALL" | "DUE" | "OVERDUE" | "NOT_DUE">("ALL");
  const [historySourceFilter, setHistorySourceFilter] = React.useState<"ALL" | "INTERNAL" | "EXTERNAL">("ALL");
  const [historyBillingFilter, setHistoryBillingFilter] = React.useState<VaccinationHistoryBillingFilter>("ALL");
  const [recommendations, setRecommendations] = React.useState<VaccinationRecommendationSummary | null>(null);
  const [recommendationsLoading, setRecommendationsLoading] = React.useState(false);
  const [recommendationsError, setRecommendationsError] = React.useState<string | null>(null);
  const [externalPromptOpen, setExternalPromptOpen] = React.useState(false);
  const [externalPromptSeenPatientId, setExternalPromptSeenPatientId] = React.useState<string | null>(null);
  const [noHistoryConfirmOpen, setNoHistoryConfirmOpen] = React.useState(false);
  const [externalDialogOpen, setExternalDialogOpen] = React.useState(false);
  const [autoOpenCardUpload, setAutoOpenCardUpload] = React.useState(false);
  const [pendingVaccinationSubmit, setPendingVaccinationSubmit] = React.useState<null | { body: Parameters<typeof recordPatientVaccination>[3] }>(null);
  const [vaccinationPaymentDialog, setVaccinationPaymentDialog] = React.useState<null | { bill: Bill; patientLabel: string; vaccineName: string }>(null);
  const [vaccinationReceiptPanel, setVaccinationReceiptPanel] = React.useState<null | { patientName: string; vaccineName: string; bill: Bill; receipt: Receipt; payment: Payment; receiptPrintData: ReceiptPrintData }>(null);
  const [receiptPreview, setReceiptPreview] = React.useState<ReceiptPrintData | null>(null);
  const [receiptPreviewLoading, setReceiptPreviewLoading] = React.useState(false);
  const [receiptAutoPrint, setReceiptAutoPrint] = React.useState(false);
  const [receiptActionLoading, setReceiptActionLoading] = React.useState(false);
  const [historyActionAnchorEl, setHistoryActionAnchorEl] = React.useState<HTMLElement | null>(null);
  const [historyActionRow, setHistoryActionRow] = React.useState<PatientVaccination | null>(null);
  const [expandedHistoryRowIds, setExpandedHistoryRowIds] = React.useState<Record<string, boolean>>({});
  const [passportDialogOpen, setPassportDialogOpen] = React.useState(false);
  const [passportAutoPrint, setPassportAutoPrint] = React.useState(false);
  const [documentPanel, setDocumentPanel] = React.useState<null | { kind: "passport" | "certificate"; document: GeneratedVaccinationDocumentResponse; certificateType?: string }>(null);
  const [certificateType, setCertificateType] = React.useState<"CHILD_IMMUNIZATION" | "SCHOOL_VACCINATION" | "TRAVEL_VACCINATION" | "SINGLE_VACCINATION">("SINGLE_VACCINATION");
  const [aefiDialogOpen, setAefiDialogOpen] = React.useState(false);
  const [aefiDialogMode, setAefiDialogMode] = React.useState<"prompt" | "form">("prompt");
  const [aefiDialogVaccination, setAefiDialogVaccination] = React.useState<PatientVaccination | null>(null);
  const [aefiForm, setAefiForm] = React.useState({
    adverseEventStatus: "OBSERVED",
    eventDateTime: new Date().toISOString(),
    onsetTimeAfterVaccination: "",
    severity: "MILD",
    symptoms: [] as string[],
    otherSymptoms: "",
    actionTaken: "",
    treatmentNotes: "",
    outcome: "ONGOING",
    followUpRequired: false,
    followUpDate: "",
    reportedToAuthority: false,
    reportReferenceNumber: "",
    notes: "",
  });
  const [roadmapExpandedStageIds, setRoadmapExpandedStageIds] = React.useState<Record<string, boolean>>({});
  const [showCompletedTimelineItems, setShowCompletedTimelineItems] = React.useState(true);
  const [showOptionalRiskBasedTimelineItems, setShowOptionalRiskBasedTimelineItems] = React.useState(false);
  const [showNotApplicableTimelineItems, setShowNotApplicableTimelineItems] = React.useState(false);
  const [showChildhoodSchedule, setShowChildhoodSchedule] = React.useState(false);
  const [activeVaccinationSection, setActiveVaccinationSection] = React.useState<"record" | "timeline" | "history" | "schedule" | "master">("record");
  const [scheduleCalendarScheduleType, setScheduleCalendarScheduleType] = React.useState<VaccinationScheduleTypeFilter>("UIP");
  const [scheduleCalendarAgeGroup, setScheduleCalendarAgeGroup] = React.useState("");
  const [scheduleCalendarRoute, setScheduleCalendarRoute] = React.useState("");
  const [scheduleCalendarVaccineGroup, setScheduleCalendarVaccineGroup] = React.useState("");
  const [scheduleCalendarPolicy, setScheduleCalendarPolicy] = React.useState<VaccinationSchedulePolicyFilter>("ALL");
  const [scheduleCalendarActiveOnly, setScheduleCalendarActiveOnly] = React.useState(true);
  const [scheduleCalendarExpanded, setScheduleCalendarExpanded] = React.useState(false);
  const [scheduleDetailVaccine, setScheduleDetailVaccine] = React.useState<VaccineMaster | null>(null);
  const historySectionRef = React.useRef<HTMLDivElement | null>(null);
  const scheduleSectionRef = React.useRef<HTMLDivElement | null>(null);
  const vaccineMasterSectionRef = React.useRef<HTMLDivElement | null>(null);
  const roadmapStageDefaultsPatientIdRef = React.useRef<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const externalCardFileRef = React.useRef<HTMLInputElement | null>(null);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [vaccineRows, medicineRows, stockRows, due, overdue, history, userRows, patientRows, clinic] = await Promise.all([
      getVaccines(auth.accessToken, auth.tenantId),
      getMedicines(auth.accessToken, auth.tenantId),
      getStocks(auth.accessToken, auth.tenantId),
      getDueVaccinations(auth.accessToken, auth.tenantId),
      getOverdueVaccinations(auth.accessToken, auth.tenantId),
      getVaccinationHistory(auth.accessToken, auth.tenantId),
      getClinicUsers(auth.accessToken, auth.tenantId),
      searchPatients(auth.accessToken, auth.tenantId, { active: true }),
      getClinicProfile(auth.accessToken, auth.tenantId),
    ]);
    setVaccines(vaccineRows);
    setMedicines(medicineRows);
    setStocks(stockRows);
    setDueRows(due);
    setOverdueRows(overdue);
    setHistoryRows(history);
    setUsers(userRows);
    setPatients(patientRows);
    setClinicProfile(clinic);
  }, [auth.accessToken, auth.tenantId]);

  const refreshPatientBilling = React.useCallback(async (patientId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const bills = await searchBills(auth.accessToken, auth.tenantId, { patientId });
      setPatientBills(bills);
    } catch {
      setPatientBills([]);
    }
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
      setWorkflowNotice(null);
      try {
        await loadAll();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load vaccination data");
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

  const openVaccineEditor = React.useCallback((vaccine?: VaccineMaster) => {
    if (!vaccine) {
      setEditingVaccineId(null);
      setVaccineForm(emptyVaccineForm());
    } else {
      setEditingVaccineId(vaccine.id);
      setVaccineForm(formForVaccine(vaccine));
    }
    setVaccineFieldErrors({});
    setError(null);
    setSuccess(null);
  }, []);

  const closeImportDialogs = React.useCallback(() => {
    setImportPreviewOpen(false);
    setImportResultOpen(false);
    setImportPreview(null);
    setImportPreviewFile(null);
    setImportResult(null);
  }, []);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientQuery.trim().length < 2) {
        setPatientSearchResults([]);
        return;
      }
      try {
        const term = patientQuery.trim();
        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^\d{6,}$/.test(term) ? term : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^\d{6,}$/.test(term) ? undefined : term,
          active: true,
        });
        if (!cancelled) {
          setPatientSearchResults(rows);
        }
      } catch {
        if (!cancelled) {
          setPatientSearchResults([]);
        }
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, patientQuery]);

  const refreshRecommendations = React.useCallback(async (patientId: string, options?: { silent?: boolean }) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    if (!options?.silent) {
      setRecommendationsLoading(true);
    }
    setRecommendationsError(null);
    try {
      const summary = await getVaccinationRecommendations(auth.accessToken, auth.tenantId, patientId);
      setRecommendations(summary);
    } catch (err) {
      setRecommendations(null);
      setRecommendationsError(err instanceof Error ? err.message : "Failed to load vaccination recommendations");
    } finally {
      if (!options?.silent) {
        setRecommendationsLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId]);

  const refreshSelectedPatientContext = React.useCallback(async (patientId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const [vaccinations, documents, notifications] = await Promise.all([
        getPatientVaccinations(auth.accessToken, auth.tenantId, patientId),
        getPatientDocuments(auth.accessToken, auth.tenantId, patientId, { documentType: "VACCINATION" }),
        getPatientNotifications(auth.accessToken, auth.tenantId, patientId),
      ]);
      setPatientHistoryRows(vaccinations);
      setPatientDocuments(documents);
      setPatientNotifications(notifications);
      await refreshPatientBilling(patientId);
    } catch {
      setPatientHistoryRows([]);
      setPatientDocuments([]);
      setPatientNotifications([]);
      setPatientBills([]);
    }
  }, [auth.accessToken, auth.tenantId, refreshPatientBilling]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadRecommendations() {
      if (!selectedPatient?.id) {
        setRecommendations(null);
        setRecommendationsError(null);
        return;
      }
      if (cancelled) {
        return;
      }
      await refreshRecommendations(selectedPatient.id);
    }
    void loadRecommendations();
    return () => {
      cancelled = true;
    };
  }, [refreshRecommendations, selectedPatient?.id]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadSelectedPatientContext() {
      if (!selectedPatient?.id) {
        setPatientHistoryRows([]);
        setPatientDocuments([]);
        setPatientBills([]);
        return;
      }
      if (cancelled) {
        return;
      }
      await refreshSelectedPatientContext(selectedPatient.id);
    }
    void loadSelectedPatientContext();
    return () => {
      cancelled = true;
    };
  }, [refreshSelectedPatientContext, selectedPatient?.id]);

  React.useEffect(() => {
    if (!selectedPatient?.id) {
      setExternalPromptOpen(false);
      return;
    }
    if (externalPromptSeenPatientId !== selectedPatient.id) {
      setExternalPromptOpen(true);
    }
  }, [externalPromptSeenPatientId, selectedPatient?.id]);

  React.useEffect(() => {
    if (externalDialogOpen && autoOpenCardUpload) {
      window.setTimeout(() => externalCardFileRef.current?.click(), 0);
      setAutoOpenCardUpload(false);
    }
  }, [autoOpenCardUpload, externalDialogOpen]);

  React.useEffect(() => {
    if (!receiptPreview || !receiptAutoPrint || receiptPreviewLoading) {
      return;
    }
    const handle = window.setTimeout(() => {
      window.print();
      setReceiptAutoPrint(false);
    }, 0);
    return () => window.clearTimeout(handle);
  }, [receiptAutoPrint, receiptPreview, receiptPreviewLoading]);

  React.useEffect(() => {
    if (!passportDialogOpen || !passportAutoPrint) {
      return;
    }
    const handle = window.setTimeout(() => {
      window.print();
      setPassportAutoPrint(false);
    }, 0);
    return () => window.clearTimeout(handle);
  }, [passportAutoPrint, passportDialogOpen]);

  const selectedPatientSummary = React.useMemo(() => {
    if (!selectedPatient) return null;
    return patients.find((patient) => patient.id === selectedPatient.id) || selectedPatient;
  }, [patients, selectedPatient]);

  const selectedPatientAgeDays = React.useMemo(() => ageDaysFromPatient(selectedPatientSummary), [selectedPatientSummary]);
  const selectedPatientAgeLabel = React.useMemo(() => ageLabelFromDays(selectedPatientAgeDays), [selectedPatientAgeDays]);
  const selectedPatientCurrentStage = React.useMemo(() => roadmapStageForDays(selectedPatientAgeDays), [selectedPatientAgeDays]);

  const selectedPatientBill = React.useMemo(() => {
    return patientBills.find((bill) => billHasCollectableDue(bill))
      || patientBills.find((bill) => !billCountsAsSettled(bill))
      || patientBills[0]
      || null;
  }, [patientBills]);

  const vaccinationRoadmapStages = React.useMemo(
    () => buildVaccinationTimelineSections(recommendations, selectedPatientSummary, patientHistoryRows, {
      showCompleted: showCompletedTimelineItems,
      showOptionalRiskBased: showOptionalRiskBasedTimelineItems,
      showNotApplicable: showNotApplicableTimelineItems,
    }),
    [
      patientHistoryRows,
      recommendations,
      selectedPatientSummary,
      showCompletedTimelineItems,
      showNotApplicableTimelineItems,
      showOptionalRiskBasedTimelineItems,
    ],
  );

  const vaccinationRoadmapVisibleStages = React.useMemo(() => {
    if (!selectedPatientAgeDays || selectedPatientAgeDays < ADULT_VISIBLE_MIN_DAYS) {
      return vaccinationRoadmapStages;
    }
    if (showChildhoodSchedule) {
      return vaccinationRoadmapStages;
    }
    return vaccinationRoadmapStages.filter((stage) => stage.key === "adult" || stage.key === "senior");
  }, [selectedPatientAgeDays, showChildhoodSchedule, vaccinationRoadmapStages]);

  const vaccinationRoadmapCurrentStage = React.useMemo(
    () => vaccinationRoadmapVisibleStages.find((stage) => stage.key === selectedPatientCurrentStage.key)
      || vaccinationRoadmapStages.find((stage) => stage.key === selectedPatientCurrentStage.key)
      || null,
    [selectedPatientCurrentStage.key, vaccinationRoadmapStages, vaccinationRoadmapVisibleStages],
  );

  const vaccinationRoadmapProgress = React.useMemo(
    () => timelineProgress(recommendations, patientHistoryRows),
    [patientHistoryRows, recommendations],
  );

  const vaccinationRoadmapStripStages = React.useMemo(
    () => VACCINATION_ROADMAP_STAGES,
    [],
  );

  const vaccinationRoadmapCurrentStageDueItems = React.useMemo(
    () => vaccinationRoadmapCurrentStage?.items.filter((item) => item.status === "DUE" || item.status === "OVERDUE") || [],
    [vaccinationRoadmapCurrentStage?.items],
  );

  const vaccinationRoadmapCurrentStageUpcomingItems = React.useMemo(
    () => vaccinationRoadmapCurrentStage?.items.filter((item) => item.status === "UPCOMING" || item.status === "OPTIONAL_RISK_BASED") || [],
    [vaccinationRoadmapCurrentStage?.items],
  );

  React.useEffect(() => {
    if (!selectedPatientSummary?.id) {
      setRoadmapExpandedStageIds({});
      roadmapStageDefaultsPatientIdRef.current = null;
      return;
    }
    if (roadmapStageDefaultsPatientIdRef.current === selectedPatientSummary.id) {
      return;
    }
    const defaults: Record<string, boolean> = {};
    vaccinationRoadmapVisibleStages.forEach((stage) => {
      defaults[stage.key] = stage.isCurrent || stage.hasDueOrOverdue;
    });
    setRoadmapExpandedStageIds(defaults);
    roadmapStageDefaultsPatientIdRef.current = selectedPatientSummary.id;
  }, [selectedPatientSummary?.id, vaccinationRoadmapVisibleStages]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const saveVaccine = async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const normalized = {
      vaccineName: vaccineForm.vaccineName,
      description: vaccineForm.description ?? null,
      manufacturer: vaccineForm.manufacturer ?? null,
      brandName: vaccineForm.brandName ?? null,
      vaccineGroup: vaccineForm.vaccineGroup ?? null,
      doseNumber: vaccineForm.doseNumber ?? null,
      route: vaccineForm.route ?? null,
      administrationSite: vaccineForm.administrationSite ?? null,
      storageTemperature: vaccineForm.storageTemperature ?? null,
      ndcBarcode: vaccineForm.ndcBarcode ?? null,
      inventoryItemId: vaccineForm.inventoryItemId ?? null,
      inventoryItemCode: vaccineForm.inventoryItemCode ?? null,
      stockTrackingEnabled: vaccineForm.stockTrackingEnabled,
      scheduleType: vaccineForm.scheduleType ?? null,
      ageGroup: vaccineForm.ageGroup ?? null,
      minAgeDays: vaccineForm.minAgeDays ?? null,
      recommendedAgeDays: vaccineForm.recommendedAgeDays ?? null,
      maxAgeDays: vaccineForm.maxAgeDays ?? null,
      gapDays: vaccineForm.gapDays ?? null,
      recommendedGapDays: vaccineForm.recommendedGapDays ?? null,
      boosterGapDays: vaccineForm.boosterGapDays ?? null,
      boosterRules: vaccineForm.boosterRules ?? null,
      recurring: vaccineForm.recurring,
      recurrenceDays: vaccineForm.recurrenceDays ?? null,
      recommendationPolicy: vaccineForm.recommendationPolicy ?? null,
      catchUpPolicy: vaccineForm.catchUpPolicy ?? null,
      catchUpMaxAgeDays: vaccineForm.catchUpMaxAgeDays ?? null,
      applicableAgeGroup: vaccineForm.applicableAgeGroup ?? null,
      clinicalIndications: vaccineForm.clinicalIndications ?? null,
      defaultPrice: vaccineForm.defaultPrice ?? null,
      active: vaccineForm.active,
    } satisfies VaccineInput;
    const parsed = vaccinationMasterSchema.safeParse({
      ...normalized,
    });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setVaccineFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = [
          "vaccineName",
          "description",
          "manufacturer",
          "brandName",
          "vaccineGroup",
          "doseNumber",
          "route",
          "administrationSite",
          "storageTemperature",
          "ndcBarcode",
          "scheduleType",
          "ageGroup",
          "minAgeDays",
          "recommendedAgeDays",
          "maxAgeDays",
          "gapDays",
          "boosterGapDays",
          "boosterRules",
          "recurring",
          "recurrenceDays",
          "recommendationPolicy",
          "catchUpPolicy",
          "catchUpMaxAgeDays",
          "applicableAgeGroup",
          "clinicalIndications",
          "defaultPrice",
          "active",
        ].find((field) => errors[field]);
        document.getElementById(firstField ? `vaccine-${firstField}` : "vaccine-vaccineName")?.focus();
      }, 0);
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setVaccineFieldErrors({});
    try {
      const payload: VaccineInput = {
        vaccineName: parsed.data.vaccineName,
        description: parsed.data.description ?? null,
        manufacturer: parsed.data.manufacturer ?? null,
        brandName: parsed.data.brandName ?? null,
        vaccineGroup: parsed.data.vaccineGroup ?? null,
        doseNumber: parsed.data.doseNumber ?? null,
        route: parsed.data.route ?? null,
        administrationSite: parsed.data.administrationSite ?? null,
        storageTemperature: parsed.data.storageTemperature ?? null,
        ndcBarcode: parsed.data.ndcBarcode ?? null,
        inventoryItemId: parsed.data.inventoryItemId ?? null,
        inventoryItemCode: parsed.data.inventoryItemCode ?? null,
        stockTrackingEnabled: parsed.data.stockTrackingEnabled,
        scheduleType: parsed.data.scheduleType ?? null,
        ageGroup: parsed.data.ageGroup ?? null,
        minAgeDays: parsed.data.minAgeDays ?? null,
        recommendedAgeDays: parsed.data.recommendedAgeDays ?? null,
        maxAgeDays: parsed.data.maxAgeDays ?? null,
        gapDays: parsed.data.gapDays ?? null,
        recommendedGapDays: parsed.data.recommendedGapDays ?? null,
        boosterGapDays: parsed.data.boosterGapDays ?? null,
        boosterRules: parsed.data.boosterRules ?? null,
        recurring: parsed.data.recurring,
        recurrenceDays: parsed.data.recurrenceDays ?? null,
        recommendationPolicy: parsed.data.recommendationPolicy ?? null,
        catchUpPolicy: parsed.data.catchUpPolicy ?? null,
        catchUpMaxAgeDays: parsed.data.catchUpMaxAgeDays ?? null,
        applicableAgeGroup: parsed.data.applicableAgeGroup ?? null,
        clinicalIndications: parsed.data.clinicalIndications ?? null,
        defaultPrice: parsed.data.defaultPrice ?? null,
        active: parsed.data.active,
      };
      if (editingVaccineId) {
        await updateVaccine(auth.accessToken, auth.tenantId, editingVaccineId, payload);
      } else {
        await createVaccine(auth.accessToken, auth.tenantId, payload);
      }
      setVaccineForm(emptyVaccineForm());
      setEditingVaccineId(null);
      await loadAll();
      setSuccess("Vaccine saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save vaccine");
    } finally {
      setSaving(false);
    }
  };

  const submitVaccination = React.useCallback(async (body: Parameters<typeof recordPatientVaccination>[3]) => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient) {
      return null;
    }
    const patientId = selectedPatient.id;
    setSaving(true);
    setError(null);
    setSuccess(null);
    setWorkflowNotice(null);
    setVaccinationFieldErrors({});
    try {
      const recorded = await recordPatientVaccination(auth.accessToken, auth.tenantId, patientId, body);
      setVaccinationForm(emptyVaccinationForm());
      await Promise.all([
        loadAll(),
        refreshSelectedPatientContext(patientId),
        refreshRecommendations(patientId, { silent: true }),
      ]);
      setSuccess(body.source === "EXTERNAL" ? "External vaccination history saved" : "Vaccination recorded");
      setWorkflowNotice(recorded.workflowWarnings?.length ? recorded.workflowWarnings.join(" ") : null);
      if (body.source !== "EXTERNAL") {
        setAefiDialogVaccination(recorded);
        setAefiDialogMode("prompt");
        setAefiForm({
          adverseEventStatus: "OBSERVED",
          eventDateTime: new Date().toISOString(),
          onsetTimeAfterVaccination: "",
          severity: "MILD",
          symptoms: [],
          otherSymptoms: "",
          actionTaken: "",
          treatmentNotes: "",
          outcome: "ONGOING",
          followUpRequired: false,
          followUpDate: "",
          reportedToAuthority: false,
          reportReferenceNumber: "",
          notes: "",
        });
        setAefiDialogOpen(true);
      }
      return recorded;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record vaccination");
      return null;
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, loadAll, refreshRecommendations, refreshSelectedPatientContext, selectedPatient]);

  const selectedVaccinationVaccine = React.useMemo(
    () => vaccines.find((item) => item.id === vaccinationForm.vaccineId) || null,
    [vaccines, vaccinationForm.vaccineId],
  );

  const selectedVaccinationStocks = React.useMemo(() => {
    if (!selectedVaccinationVaccine?.stockTrackingEnabled || !selectedVaccinationVaccine.inventoryItemId) {
      return [];
    }
    return stocks
      .filter((stock) => stock.medicineId === selectedVaccinationVaccine.inventoryItemId && stock.quantityOnHand > 0)
      .sort((left, right) => {
        const leftExpiry = left.expiryDate ? new Date(left.expiryDate).getTime() : Number.POSITIVE_INFINITY;
        const rightExpiry = right.expiryDate ? new Date(right.expiryDate).getTime() : Number.POSITIVE_INFINITY;
        if (leftExpiry !== rightExpiry) {
          return leftExpiry - rightExpiry;
        }
        return new Date(left.updatedAt).getTime() - new Date(right.updatedAt).getTime();
      });
  }, [selectedVaccinationVaccine, stocks]);

  const selectedVaccinationStock = React.useMemo(
    () => selectedVaccinationStocks.find((stock) => stock.id === vaccinationForm.stockBatchId) || selectedVaccinationStocks[0] || null,
    [selectedVaccinationStocks, vaccinationForm.stockBatchId],
  );

  React.useEffect(() => {
    if (!selectedVaccinationVaccine?.stockTrackingEnabled) {
      return;
    }
    setVaccinationForm((current) => {
      if (current.vaccineId !== selectedVaccinationVaccine.id) {
        return current;
      }
      const nextStockBatchId = selectedVaccinationStocks.some((stock) => stock.id === current.stockBatchId)
        ? current.stockBatchId
        : (selectedVaccinationStocks[0]?.id || "");
      if (nextStockBatchId === current.stockBatchId) {
        return current;
      }
      return { ...current, stockBatchId: nextStockBatchId };
    });
  }, [selectedVaccinationStocks, selectedVaccinationVaccine?.id, selectedVaccinationVaccine?.stockTrackingEnabled]);

  const recordVaccination = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient) {
      return;
    }
    const parsed = vaccinationRecordSchema.safeParse({
      patientId: selectedPatient.id,
      vaccineId: vaccinationForm.vaccineId,
      doseNumber: vaccinationForm.doseNumber.trim() ? Number(vaccinationForm.doseNumber) : null,
      givenDate: vaccinationForm.givenDate || null,
      nextDueDate: vaccinationForm.nextDueDate || null,
      batchNumber: vaccinationForm.batchNumber,
      notes: vaccinationForm.notes,
      source: "INTERNAL",
      externalPlace: null,
      proofDocumentId: null,
      verifiedStatus: null,
      administeredByUserId: vaccinationForm.administeredByUserId || null,
      billId: null,
      addToBill: vaccinationForm.addToBill,
      billItemUnitPrice: vaccinationForm.billItemUnitPrice.trim() ? Number(vaccinationForm.billItemUnitPrice) : null,
    });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setVaccinationFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = ["vaccineId", "givenDate", "patientId", "billItemUnitPrice", "batchNumber", "notes"].find((field) => errors[field]);
        document.getElementById(firstField ? `vaccination-${firstField}` : "vaccination-vaccineId")?.focus();
      }, 0);
      return;
    }
    const body = {
      vaccineId: parsed.data.vaccineId,
      vaccineName: null,
      doseNumber: parsed.data.doseNumber ?? null,
      givenDate: parsed.data.givenDate ?? null,
      nextDueDate: parsed.data.nextDueDate ?? null,
      batchNumber: parsed.data.batchNumber ?? null,
      stockBatchId: selectedVaccinationStock?.id || null,
      notes: parsed.data.notes ?? null,
      source: "INTERNAL" as const,
      externalPlace: null,
      proofDocumentId: null,
      verifiedStatus: null,
      administeredByUserId: parsed.data.administeredByUserId ?? null,
      billId: null,
      addToBill: parsed.data.addToBill ?? false,
      billItemUnitPrice: parsed.data.billItemUnitPrice ?? null,
    };
    if (!noHistoryConfirmOpen && patientHistoryRows.length === 0) {
      setPendingVaccinationSubmit({ body });
      setNoHistoryConfirmOpen(true);
      return;
    }
    await submitVaccination(body);
  };

  const buildVaccinationReceiptPrintData = React.useCallback((
    bill: Bill,
    receipt: Receipt,
    payment: Payment,
  ): ReceiptPrintData => ({
    clinicProfile,
    bill,
    receipt,
    payment,
    patient: selectedPatientSummary,
    appointment: null,
    consultation: null,
  }), [clinicProfile, selectedPatientSummary]);

  const submitVaccinationPayment = async (value: ConsultationFeeDialogValue) => {
    if (!auth.accessToken || !auth.tenantId || !vaccinationPaymentDialog) {
      return;
    }
    const patientId = selectedPatient?.id;
    if (!patientId) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const bill = vaccinationPaymentDialog.bill;
      const payment = await addBillPayment(auth.accessToken, auth.tenantId, bill.id, {
        paymentDate: new Date().toISOString().slice(0, 10),
        amount: billEffectiveDueAmount(bill),
        paymentMode: value.paymentMode,
        referenceNumber: value.referenceNumber || null,
        notes: value.notes || null,
      });
      const updatedBill = await getBill(auth.accessToken, auth.tenantId, bill.id);
      const receipt: Receipt = {
        id: payment.receiptId || payment.id,
        tenantId: payment.tenantId,
        receiptNumber: payment.receiptNumber || `RCPT-${payment.id.slice(0, 8).toUpperCase()}`,
        billId: updatedBill.id,
        paymentId: payment.id,
        receiptDate: payment.receiptDate || payment.paymentDate,
        amount: payment.amount,
        createdAt: payment.createdAt,
      };
      const patientName = vaccinationPaymentDialog.patientLabel || (selectedPatientSummary ? `${selectedPatientSummary.firstName} ${selectedPatientSummary.lastName}`.trim() : selectedPatient?.patientNumber || "Patient");
      const vaccineName = vaccinationPaymentDialog.vaccineName || "Vaccine";
      const receiptPrintData = buildVaccinationReceiptPrintData(updatedBill, receipt, payment);
      setVaccinationReceiptPanel({
        patientName,
        vaccineName,
        bill: updatedBill,
        receipt,
        payment,
        receiptPrintData,
      });
      setVaccinationPaymentDialog(null);
      await Promise.all([
        loadAll(),
        refreshSelectedPatientContext(patientId),
        refreshRecommendations(patientId, { silent: true }),
      ]);
      setSuccess("Payment collected");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect vaccination payment");
    } finally {
      setSaving(false);
    }
  };

  const saveExternalVaccination = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient) {
      return;
    }
    const patientId = selectedPatient.id;
    const errors: Record<string, string> = {};
    if (!externalVaccinationForm.vaccineName.trim()) {
      errors.vaccineName = "Vaccine name is required.";
    }
    if (!externalVaccinationForm.givenDate.trim()) {
      errors.givenDate = "Given date is required.";
    }
    if (externalVaccinationForm.doseNumber.trim() && Number.isNaN(Number(externalVaccinationForm.doseNumber))) {
      errors.doseNumber = "Dose number must be a number.";
    }
    if (Object.keys(errors).length > 0) {
      setExternalFieldErrors(errors);
      setError(Object.values(errors)[0]);
      window.setTimeout(() => {
        const firstField = ["vaccineName", "doseNumber", "givenDate", "externalPlace", "batchNumber", "notes", "proofDocumentId"].find((field) => errors[field]);
        document.getElementById(firstField ? `external-vaccination-${firstField}` : "external-vaccination-vaccineName")?.focus();
      }, 0);
      return;
    }
    const matchedVaccine = vaccines.find((item) => item.vaccineName.trim().toLowerCase() === externalVaccinationForm.vaccineName.trim().toLowerCase());
    await submitVaccination({
      vaccineId: matchedVaccine?.id ?? null,
      vaccineName: externalVaccinationForm.vaccineName.trim(),
      doseNumber: externalVaccinationForm.doseNumber.trim() ? Number(externalVaccinationForm.doseNumber) : null,
      givenDate: externalVaccinationForm.givenDate || null,
      nextDueDate: null,
      batchNumber: externalVaccinationForm.batchNumber.trim() || null,
      notes: externalVaccinationForm.notes.trim() || null,
      source: "EXTERNAL",
      externalPlace: externalVaccinationForm.externalPlace.trim() || null,
      proofDocumentId: externalVaccinationForm.proofDocumentId.trim() || null,
      verifiedStatus: externalVaccinationForm.verifiedStatus,
      administeredByUserId: vaccinationForm.administeredByUserId || null,
      billId: null,
      addToBill: false,
      billItemUnitPrice: null,
    });
    setExternalVaccinationForm(emptyExternalVaccinationForm());
    setExternalDialogOpen(false);
    setExternalPromptOpen(false);
    setExternalPromptSeenPatientId(patientId);
    setNoHistoryConfirmOpen(false);
    setPendingVaccinationSubmit(null);
  };

  const continueWithoutHistory = async () => {
    if (!pendingVaccinationSubmit) {
      return;
    }
    const patientId = selectedPatient?.id || null;
    setNoHistoryConfirmOpen(false);
    await submitVaccination(pendingVaccinationSubmit.body);
    setPendingVaccinationSubmit(null);
    if (patientId) {
      setExternalPromptSeenPatientId(patientId);
    }
  };

  const openVaccinationReceiptPreview = React.useCallback((autoPrint = false) => {
    if (!vaccinationReceiptPanel) {
      return;
    }
    setReceiptPreview(vaccinationReceiptPanel.receiptPrintData);
    setReceiptAutoPrint(autoPrint);
  }, [vaccinationReceiptPanel]);

  const handleVaccinationReceiptDownload = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !vaccinationReceiptPanel?.receipt.id) {
      return;
    }
    setReceiptActionLoading(true);
    setError(null);
    try {
      const file = await getReceiptPdf(auth.accessToken, auth.tenantId, vaccinationReceiptPanel.receipt.id);
      const url = URL.createObjectURL(file.blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename || `${vaccinationReceiptPanel.receipt.receiptNumber || vaccinationReceiptPanel.bill.billNumber}-receipt.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download receipt PDF");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, vaccinationReceiptPanel]);

  const handleVaccinationReceiptSend = React.useCallback(async (channel: "EMAIL" | "WHATSAPP") => {
    if (!auth.accessToken || !auth.tenantId || !vaccinationReceiptPanel?.receipt.id) {
      return;
    }
    setReceiptActionLoading(true);
    setError(null);
    try {
      await sendReceipt(auth.accessToken, auth.tenantId, vaccinationReceiptPanel.receipt.id, channel);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, vaccinationReceiptPanel]);

  const openSelectedPatientBillReceipt = React.useCallback(async (autoPrint = false) => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatientBill || !selectedPatientSummary) {
      return;
    }
    setReceiptActionLoading(true);
    setError(null);
    try {
      const [payments, receipts, bill] = await Promise.all([
        listBillPayments(auth.accessToken, auth.tenantId, selectedPatientBill.id),
        listBillReceipts(auth.accessToken, auth.tenantId, selectedPatientBill.id),
        getBill(auth.accessToken, auth.tenantId, selectedPatientBill.id),
      ]);
      const payment = payments[0] || null;
      const receipt = latestReceiptForBill(receipts) || (payment ? {
        id: payment.receiptId || payment.id,
        tenantId: payment.tenantId,
        receiptNumber: payment.receiptNumber || `RCPT-${payment.id.slice(0, 8).toUpperCase()}`,
        billId: payment.billId,
        paymentId: payment.id,
        receiptDate: payment.receiptDate || payment.paymentDate,
        amount: payment.amount,
        createdAt: payment.createdAt,
      } : null);
      if (!receipt || !payment) {
        throw new Error("Receipt not available yet.");
      }
      const patientName = `${selectedPatientSummary.firstName} ${selectedPatientSummary.lastName}`.trim();
      const receiptPrintData = buildVaccinationReceiptPrintData(bill, receipt, payment);
      setReceiptPreview(receiptPrintData);
      setReceiptAutoPrint(autoPrint);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, buildVaccinationReceiptPrintData, selectedPatientBill, selectedPatientSummary]);

  const resolveVaccinationReceiptContext = React.useCallback(async (row: PatientVaccination) => {
    if (!auth.accessToken || !auth.tenantId || !row.billId) {
      throw new Error("Receipt not available yet.");
    }
    const [payments, receipts, bill] = await Promise.all([
      listBillPayments(auth.accessToken, auth.tenantId, row.billId),
      listBillReceipts(auth.accessToken, auth.tenantId, row.billId),
      getBill(auth.accessToken, auth.tenantId, row.billId),
    ]);
    const payment = payments[0] || null;
    const receipt = latestReceiptForBill(receipts) || (payment ? {
      id: payment.receiptId || payment.id,
      tenantId: payment.tenantId,
      receiptNumber: payment.receiptNumber || `RCPT-${payment.id.slice(0, 8).toUpperCase()}`,
      billId: payment.billId,
      paymentId: payment.id,
      receiptDate: payment.receiptDate || payment.paymentDate,
      amount: payment.amount,
      createdAt: payment.createdAt,
    } : null);
    if (!receipt || !payment) {
      throw new Error("Receipt not available yet.");
    }
    const patientName = row.patientName || row.patientNumber || "Patient";
    const vaccineName = row.vaccineName || "Vaccine";
    const receiptPrintData = buildVaccinationReceiptPrintData(bill, receipt, payment);
    const context = { patientName, vaccineName, bill, receipt, payment, receiptPrintData };
    setVaccinationReceiptPanel(context);
    return context;
  }, [auth.accessToken, auth.tenantId, buildVaccinationReceiptPrintData]);

  const openVaccinationReceiptForRow = React.useCallback(async (row: PatientVaccination, autoPrint = false) => {
    setReceiptActionLoading(true);
    setError(null);
    try {
      const context = await resolveVaccinationReceiptContext(row);
      setReceiptPreview(context.receiptPrintData);
      setReceiptAutoPrint(autoPrint);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [resolveVaccinationReceiptContext]);

  const downloadVaccinationReceiptForRow = React.useCallback(async (row: PatientVaccination) => {
    setReceiptActionLoading(true);
    setError(null);
    try {
      const context = await resolveVaccinationReceiptContext(row);
      const file = await getReceiptPdf(auth.accessToken!, auth.tenantId!, context.receipt.id);
      const url = URL.createObjectURL(file.blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename || `${context.receipt.receiptNumber || context.bill.billNumber}-receipt.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download receipt PDF");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, resolveVaccinationReceiptContext]);

  const sendVaccinationReceiptForRow = React.useCallback(async (row: PatientVaccination, channel: "EMAIL" | "WHATSAPP") => {
    setReceiptActionLoading(true);
    setError(null);
    try {
      const context = await resolveVaccinationReceiptContext(row);
      await sendReceipt(auth.accessToken!, auth.tenantId!, context.receipt.id, channel);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, resolveVaccinationReceiptContext]);

  const collectPaymentForRow = React.useCallback(async (row: PatientVaccination) => {
    if (!auth.accessToken || !auth.tenantId || !row.billId) {
      return;
    }
    try {
      const bill = await getBill(auth.accessToken, auth.tenantId, row.billId);
      setVaccinationPaymentDialog({
        bill,
        patientLabel: row.patientName || row.patientNumber || "Patient",
        vaccineName: row.vaccineName || "Vaccine",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open payment dialog");
    }
  }, [auth.accessToken, auth.tenantId]);

  const billVaccinationRow = React.useCallback(async (row: PatientVaccination, createNewBill: boolean) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      setSaving(true);
      await billPatientVaccination(auth.accessToken, auth.tenantId, row.patientId, row.id, {
        billId: createNewBill ? null : row.billId || null,
        createNewBill,
        billItemUnitPrice: null,
      });
      await Promise.all([
        loadAll(),
        row.patientId === selectedPatient?.id ? refreshSelectedPatientContext(row.patientId) : Promise.resolve(),
        row.patientId === selectedPatient?.id ? refreshRecommendations(row.patientId, { silent: true }) : Promise.resolve(),
      ]);
      setSuccess(createNewBill ? "Bill created" : "Added to bill");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to bill vaccination");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, loadAll, refreshRecommendations, refreshSelectedPatientContext, selectedPatient?.id]);

  const verifyExternalVaccinationRow = React.useCallback(async (row: PatientVaccination) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    if ((row.source || "").toUpperCase() !== "EXTERNAL") {
      return;
    }
    try {
      setSaving(true);
      await verifyPatientVaccination(auth.accessToken, auth.tenantId, row.patientId, row.id, {
        externalPlace: row.externalPlace || null,
        proofDocumentId: row.proofDocumentId || null,
        verifiedStatus: "VERIFIED",
      });
      await Promise.all([
        loadAll(),
        row.patientId === selectedPatient?.id ? refreshSelectedPatientContext(row.patientId) : Promise.resolve(),
        row.patientId === selectedPatient?.id ? refreshRecommendations(row.patientId, { silent: true }) : Promise.resolve(),
      ]);
      setSuccess("External vaccination marked verified");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to verify external vaccination");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, loadAll, refreshRecommendations, refreshSelectedPatientContext, selectedPatient?.id]);

  const openEditVaccinationRow = React.useCallback((row: PatientVaccination) => {
    const patient = patients.find((entry) => entry.id === row.patientId);
    if (patient) {
      setSelectedPatient(patient);
    }
    setPatientQuery(row.patientName || row.patientNumber || "");
    setVaccinationForm((current) => ({
      ...current,
      vaccineId: row.vaccineId || "",
      doseNumber: row.doseNumber != null ? String(row.doseNumber) : "",
      givenDate: row.givenDate || current.givenDate,
      nextDueDate: row.nextDueDate || "",
      batchNumber: row.batchNumber || "",
      notes: row.notes || "",
      administeredByUserId: row.administeredByUserId || "",
      addToBill: Boolean(row.billId),
      billItemUnitPrice: "",
    }));
    window.setTimeout(() => document.getElementById("vaccination-vaccineId")?.focus(), 0);
  }, [patients]);

  const openHistoryRowActions = React.useCallback((event: React.MouseEvent<HTMLElement>, row: PatientVaccination) => {
    setHistoryActionAnchorEl(event.currentTarget);
    setHistoryActionRow(row);
  }, []);

  const closeHistoryRowActions = React.useCallback(() => {
    setHistoryActionAnchorEl(null);
    setHistoryActionRow(null);
  }, []);

  const toggleHistoryRowExpanded = React.useCallback((rowId: string) => {
    setExpandedHistoryRowIds((current) => ({ ...current, [rowId]: !current[rowId] }));
  }, []);

  const aefiSymptomsOptions = React.useMemo(() => [
    "fever",
    "pain at injection site",
    "swelling",
    "rash",
    "vomiting",
    "dizziness",
    "fainting",
    "breathing difficulty",
    "anaphylaxis",
    "seizure",
    "hospitalization",
    "other",
  ], []);

  const openAefiDialog = React.useCallback((row: PatientVaccination, mode: "prompt" | "form" = row.adverseEventStatus && row.adverseEventStatus !== "NONE" ? "form" : "prompt") => {
    setAefiDialogVaccination(row);
    setAefiDialogMode(mode);
    setAefiForm({
      adverseEventStatus: row.adverseEventStatus && row.adverseEventStatus !== "NONE" ? String(row.adverseEventStatus) : "OBSERVED",
      eventDateTime: row.adverseEventEventDateTime || new Date().toISOString(),
      onsetTimeAfterVaccination: row.adverseEventOnsetTimeAfterVaccination || "",
      severity: row.adverseEventSeverity || "MILD",
      symptoms: row.adverseEventSymptoms || [],
      otherSymptoms: row.adverseEventOtherSymptoms || "",
      actionTaken: row.adverseEventActionTaken || "",
      treatmentNotes: row.adverseEventTreatmentNotes || "",
      outcome: row.adverseEventOutcome || "ONGOING",
      followUpRequired: Boolean(row.adverseEventFollowUpRequired),
      followUpDate: row.adverseEventFollowUpDate || "",
      reportedToAuthority: Boolean(row.adverseEventReportedToAuthority),
      reportReferenceNumber: row.adverseEventReportReferenceNumber || "",
      notes: row.adverseEventNotes || "",
    });
    setAefiDialogOpen(true);
  }, []);

  const saveAefi = React.useCallback(async (skipClinicalEvent: boolean = false) => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !aefiDialogVaccination) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const recorded = await updatePatientVaccinationAefi(auth.accessToken, auth.tenantId, selectedPatient.id, aefiDialogVaccination.id, {
        adverseEventStatus: skipClinicalEvent ? "NONE" : String(aefiForm.adverseEventStatus || "OBSERVED"),
        eventDateTime: aefiForm.eventDateTime || new Date().toISOString(),
        onsetTimeAfterVaccination: aefiForm.onsetTimeAfterVaccination || null,
        severity: skipClinicalEvent ? null : (aefiForm.severity || null),
        symptoms: skipClinicalEvent ? [] : aefiForm.symptoms,
        otherSymptoms: skipClinicalEvent ? null : (aefiForm.otherSymptoms || null),
        actionTaken: skipClinicalEvent ? "observed only" : (aefiForm.actionTaken || null),
        treatmentNotes: skipClinicalEvent ? null : (aefiForm.treatmentNotes || null),
        outcome: skipClinicalEvent ? "UNKNOWN" : (aefiForm.outcome || null),
        followUpRequired: skipClinicalEvent ? false : aefiForm.followUpRequired,
        followUpDate: skipClinicalEvent ? null : (aefiForm.followUpDate || null),
        reportedToAuthority: skipClinicalEvent ? false : aefiForm.reportedToAuthority,
        reportReferenceNumber: skipClinicalEvent ? null : (aefiForm.reportReferenceNumber || null),
        notes: skipClinicalEvent ? null : (aefiForm.notes || null),
      });
      setAefiDialogOpen(false);
      setAefiDialogVaccination(null);
      await Promise.all([
        loadAll(),
        refreshSelectedPatientContext(selectedPatient.id),
        refreshRecommendations(selectedPatient.id, { silent: true }),
      ]);
      setSuccess(recorded.adverseEventStatus && recorded.adverseEventStatus !== "NONE" ? "AEFI recorded" : "No adverse event recorded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record adverse event");
    } finally {
      setSaving(false);
    }
  }, [aefiDialogVaccination, aefiForm, auth.accessToken, auth.tenantId, loadAll, refreshRecommendations, refreshSelectedPatientContext, selectedPatient]);

  const uploadVaccinationCard = async (file: File) => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient) {
      return;
    }
    setSaving(true);
    try {
      const document = await uploadPatientDocument(auth.accessToken, auth.tenantId, selectedPatient.id, {
        file,
        documentType: "VACCINATION",
        title: `Vaccination card - ${selectedPatient.firstName} ${selectedPatient.lastName}`,
        notes: "Vaccination card uploaded for external history review.",
        uploadSource: "RECEPTION",
        sourceModule: "VACCINATION",
        sourceEntityId: selectedPatient.id,
        visibility: "INTERNAL_ONLY",
      });
      setExternalVaccinationForm((current) => ({ ...current, proofDocumentId: document.id }));
      setSuccess("Vaccination card uploaded for review");
      setWorkflowNotice("Vaccination card uploaded but not yet mapped to vaccine history.");
      const documents = await getPatientDocuments(auth.accessToken, auth.tenantId, selectedPatient.id, { documentType: "VACCINATION" });
      setPatientDocuments(documents);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload vaccination card");
    } finally {
      setSaving(false);
    }
  };

  const handleExternalCardFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    await uploadVaccinationCard(file);
    event.target.value = "";
  };

  const deactivate = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deactivateVaccine(auth.accessToken, auth.tenantId, id);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate vaccine");
    } finally {
      setSaving(false);
    }
  };

  const downloadTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const csv = await getVaccineImportTemplate(auth.accessToken, auth.tenantId);
      downloadCsv("vaccine-import-template.csv", csv);
    } catch {
      downloadCsv("vaccine-import-template.csv", buildVaccineTemplateCsv());
    }
  };

  const downloadExport = async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const csv = await exportVaccinesCsv(auth.accessToken, auth.tenantId);
      downloadCsv("vaccine-master-export.csv", csv);
    } catch {
      downloadCsv("vaccine-master-export.csv", buildVaccineExportCsv(vaccines));
    }
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    try {
      const parsed = fileUploadSchema({
        required: true,
        allowedMimeTypes: ["text/csv", "application/csv", "text/plain", "application/vnd.ms-excel"],
        allowedExtensions: ["csv"],
        maxBytes: 5 * 1024 * 1024,
      }).safeParse(file);
      if (!parsed.success) {
        setError(firstZodError(parsed.error));
        return;
      }
      const text = await file.text();
      setImportPreview(parseVaccineImportPreview(text, vaccines.map((vaccine) => vaccine.vaccineName)));
      setImportPreviewFile(file);
      setImportPreviewOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to read vaccine CSV");
    } finally {
      event.target.value = "";
    }
  };

  const confirmImport = async () => {
    if (!auth.accessToken || !auth.tenantId || !importPreviewFile) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const result = await importVaccinesCsv(auth.accessToken, auth.tenantId, importPreviewFile);
      setImportResult(result);
      setImportPreviewOpen(false);
      setImportResultOpen(true);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import vaccine CSV");
    } finally {
      setSaving(false);
    }
  };

  const filteredHistoryRows = React.useMemo(() => {
    const patientFilter = historyPatientFilter.trim().toLowerCase();
    const vaccineFilter = historyVaccineFilter.trim().toLowerCase();
    const from = historyFromDate ? new Date(historyFromDate) : null;
    const to = historyToDate ? new Date(historyToDate) : null;
    return historyRows.filter((row) => {
      const patientMatch = !patientFilter || [
        row.patientName,
        row.patientNumber,
        row.patientMobile,
      ].some((value) => String(value || "").toLowerCase().includes(patientFilter));
      const vaccineMatch = !vaccineFilter || String(row.vaccineName || "").toLowerCase().includes(vaccineFilter);
      const given = row.givenDate ? new Date(row.givenDate) : null;
      const fromMatch = !from || (given != null && !Number.isNaN(given.getTime()) && given >= from);
      const toMatch = !to || (given != null && !Number.isNaN(given.getTime()) && given <= to);
      const dueStatus = dueStatusForVaccination(row);
      const dueMatch = historyDueStatus === "ALL" || dueStatus === historyDueStatus;
      const sourceMatch = historySourceFilter === "ALL" || String(row.source || "INTERNAL").toUpperCase() === historySourceFilter;
      const billingMatch = historyBillingFilter === "ALL" || vaccinationBillingStatusKey(row) === historyBillingFilter;
      return patientMatch && vaccineMatch && fromMatch && toMatch && dueMatch && sourceMatch && billingMatch;
    });
  }, [historyRows, historyPatientFilter, historyVaccineFilter, historyFromDate, historyToDate, historyDueStatus, historySourceFilter, historyBillingFilter]);

  const unmappedVaccinationCards = React.useMemo(() => {
    const linkedProofIds = new Set(patientHistoryRows.map((row) => row.proofDocumentId).filter(Boolean));
    return patientDocuments.filter((document) => !linkedProofIds.has(document.id));
  }, [patientDocuments, patientHistoryRows]);

  const handleRecordFromRow = React.useCallback((row: PatientVaccination) => {
    const patient = patients.find((entry) => entry.id === row.patientId);
    if (patient) {
      setSelectedPatient(patient);
    }
    setPatientQuery(row.patientName || row.patientNumber || "");
    window.setTimeout(() => document.getElementById("vaccination-vaccineId")?.focus(), 0);
  }, [patients]);

  const handleSelectRecommendation = React.useCallback((item: VaccinationRecommendation) => {
    const vaccine = vaccines.find((entry) => entry.id === item.vaccineId);
    if (!vaccine) {
      setVaccinationForm((current) => ({
        ...current,
        vaccineId: item.vaccineId,
        doseNumber: item.doseNumber != null ? String(item.doseNumber) : current.doseNumber,
        route: item.route ?? current.route,
        administrationSite: item.administrationSite ?? current.administrationSite,
        nextDueDate: item.dueDate ?? current.nextDueDate,
        stockBatchId: "",
        billItemUnitPrice: current.billItemUnitPrice || "",
      }));
      return;
    }
    const mappedStocks = vaccine.stockTrackingEnabled && vaccine.inventoryItemId
      ? stocks
          .filter((stock) => stock.medicineId === vaccine.inventoryItemId && stock.quantityOnHand > 0)
          .sort((left, right) => {
            const leftExpiry = left.expiryDate ? new Date(left.expiryDate).getTime() : Number.POSITIVE_INFINITY;
            const rightExpiry = right.expiryDate ? new Date(right.expiryDate).getTime() : Number.POSITIVE_INFINITY;
            if (leftExpiry !== rightExpiry) {
              return leftExpiry - rightExpiry;
            }
            return new Date(left.updatedAt).getTime() - new Date(right.updatedAt).getTime();
          })
      : [];
    setVaccinationForm((current) => ({
      ...current,
      vaccineId: vaccine.id,
      doseNumber: vaccine.doseNumber != null ? String(vaccine.doseNumber) : (item.doseNumber != null ? String(item.doseNumber) : current.doseNumber),
      route: vaccine.route ?? item.route ?? current.route,
      administrationSite: vaccine.administrationSite ?? item.administrationSite ?? current.administrationSite,
      nextDueDate: item.dueDate ?? current.nextDueDate,
      stockBatchId: mappedStocks[0]?.id || "",
      billItemUnitPrice: vaccine.defaultPrice != null ? vaccine.defaultPrice.toFixed(2) : current.billItemUnitPrice,
    }));
    setWorkflowNotice(`Selected ${vaccine.vaccineName} from recommendations. Recommendations are assistive. Verify against clinic protocol.`);
    window.setTimeout(() => document.getElementById("vaccination-vaccineId")?.focus(), 0);
  }, [stocks, vaccines]);

  const recommendationGroups = React.useMemo(() => {
    const summary = recommendations;
    return {
      recommendedToday: summary?.recommendedToday || [],
      overdue: summary?.overdue || [],
      upcoming: summary?.upcoming || [],
      completed: summary?.completed || [],
      optionalRiskBased: summary?.optionalRiskBased || [],
      notApplicable: summary?.notApplicable || [],
    };
  }, [recommendations]);

  const vaccinationTimelineSections = vaccinationRoadmapStages;
  const vaccinationTimelineProgress = vaccinationRoadmapProgress;

  const immunizationPassportData = React.useMemo(() => ({
    patient: selectedPatientSummary,
    patientHistoryRows,
    timelineSections: vaccinationTimelineSections,
    progress: vaccinationTimelineProgress,
  }), [patientHistoryRows, selectedPatientSummary, vaccinationTimelineProgress, vaccinationTimelineSections]);

  const vaccinationGeneratedDocuments = React.useMemo(() => {
    const passport = [...patientDocuments].reverse().find((document) => document.title.toLowerCase().includes("passport")) || null;
    const certificates = [...patientDocuments].reverse().filter((document) => document.title.toLowerCase().includes("certificate"));
    return { passport, certificates };
  }, [patientDocuments]);

  const vaccinationPassportReference = React.useMemo(
    () => (vaccinationGeneratedDocuments.passport ? vaccinationDocumentReference(vaccinationGeneratedDocuments.passport) : null),
    [vaccinationGeneratedDocuments.passport],
  );
  const vaccinationCertificateReference = React.useMemo(
    () => (vaccinationGeneratedDocuments.certificates[0] ? vaccinationDocumentReference(vaccinationGeneratedDocuments.certificates[0]) : null),
    [vaccinationGeneratedDocuments.certificates],
  );

  const scheduleCalendarFilterOptions = React.useMemo(() => {
    const normalize = (value: string | null | undefined) => String(value || "").trim();
    const unique = (values: Array<string | null | undefined>) => [...new Set(values.map(normalize).filter(Boolean))].sort((left, right) => left.localeCompare(right));
    return {
      ageGroups: unique(vaccines.map((vaccine) => vaccine.ageGroup)),
      routes: unique(vaccines.map((vaccine) => vaccine.route)),
      vaccineGroups: unique(vaccines.map((vaccine) => vaccine.vaccineGroup)),
    };
  }, [vaccines]);

  const vaccinationScheduleCalendar = React.useMemo(() => buildVaccinationScheduleCalendar(vaccines, {
    scheduleType: scheduleCalendarScheduleType,
    ageGroup: scheduleCalendarAgeGroup,
    route: scheduleCalendarRoute,
    vaccineGroup: scheduleCalendarVaccineGroup,
    policy: scheduleCalendarPolicy,
    activeOnly: scheduleCalendarActiveOnly,
  }), [
    vaccines,
    scheduleCalendarActiveOnly,
    scheduleCalendarAgeGroup,
    scheduleCalendarPolicy,
    scheduleCalendarRoute,
    scheduleCalendarScheduleType,
    scheduleCalendarVaccineGroup,
  ]);

  const vaccinationScheduleCalendarSummary = React.useMemo(() => {
    const activeCount = vaccinationScheduleCalendar.filtered.filter((vaccine) => vaccine.active).length;
    const inactiveCount = vaccinationScheduleCalendar.filtered.length - activeCount;
    return {
      totalConfigured: vaccinationScheduleCalendar.filtered.length,
      milestoneCount: vaccinationScheduleCalendar.groups.filter((group) => group.items.length > 0).length,
      activeCount,
      inactiveCount,
    };
  }, [vaccinationScheduleCalendar]);

  const vaccinationReminderNotifications = React.useMemo(() => (
    patientNotifications.filter((notification) => notification.eventType === "VACCINATION_REMINDER")
  ), [patientNotifications]);

  const vaccinationReminderSummary = React.useMemo(() => {
    const latest = [...vaccinationReminderNotifications].sort((left, right) => `${right.createdAt}|${right.updatedAt}`.localeCompare(`${left.createdAt}|${left.updatedAt}`))[0] || null;
    const pending = vaccinationReminderNotifications.filter((notification) => notification.status === "PENDING").length;
    const sent = vaccinationReminderNotifications.filter((notification) => notification.status === "SENT").length;
    return {
      pending,
      sent,
      latest,
      nextReminderLabel: vaccinationRoadmapProgress.nextDue?.vaccineName || "None",
      nextReminderDate: vaccinationRoadmapProgress.nextDue?.dueDate || null,
      nextReminderChannel: latest?.channel || "EMAIL",
    };
  }, [vaccinationReminderNotifications, vaccinationRoadmapProgress.nextDue?.dueDate, vaccinationRoadmapProgress.nextDue?.vaccineName]);

  const openPatient = React.useCallback((patientId: string) => {
    navigate(`/patients/${patientId}`);
  }, [navigate]);

  const openVaccinationHistoryWorkspace = React.useCallback(() => {
    historySectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  const openVaccinationScheduleWorkspace = React.useCallback(() => {
    scheduleSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  const openVaccinationMasterWorkspace = React.useCallback(() => {
    vaccineMasterSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  const handleVaccinationSectionTabChange = React.useCallback((_: React.SyntheticEvent, value: "record" | "timeline" | "history" | "schedule" | "master") => {
    setActiveVaccinationSection(value);
    if (value === "record") {
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    if (value === "timeline") {
      document.getElementById("vaccination-timeline-section")?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    if (value === "history") {
      openVaccinationHistoryWorkspace();
      return;
    }
    if (value === "schedule") {
      openVaccinationScheduleWorkspace();
      return;
    }
    openVaccinationMasterWorkspace();
  }, [openVaccinationHistoryWorkspace, openVaccinationMasterWorkspace, openVaccinationScheduleWorkspace]);

  const bookAppointment = React.useCallback((row: PatientVaccination) => {
    navigate(`/appointments?patientId=${row.patientId}`, { state: { patient: patients.find((entry) => entry.id === row.patientId) || null } });
  }, [navigate, patients]);

  const openImmunizationPassport = React.useCallback((autoPrint = false) => {
    setPassportDialogOpen(true);
    setPassportAutoPrint(autoPrint);
  }, []);

  const openVaccinationDocument = React.useCallback(async (generatedDocument: VaccinationDocumentReference, patientId: string, mode: "VIEW" | "PRINT" | "DOWNLOAD" = "VIEW") => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const file = await getVaccinationDocumentPdf(auth.accessToken, auth.tenantId, patientId, generatedDocument.documentId, mode);
      const url = URL.createObjectURL(file.blob);
      if (mode === "DOWNLOAD") {
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = generatedDocument.filename || `${generatedDocument.title}.pdf`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        return;
      }
      if (mode === "PRINT") {
        const popup = window.open(url, "_blank", "noopener,noreferrer");
        if (popup) {
          popup.onload = () => popup.print();
        } else {
          window.print();
        }
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        return;
      }
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open vaccination document");
    }
  }, [auth.accessToken, auth.tenantId]);

  const sendVaccinationGeneratedDocument = React.useCallback(async (document: VaccinationDocumentReference, patientId: string, channel: "EMAIL" | "WHATSAPP" | "SMS") => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    await sendVaccinationDocument(auth.accessToken, auth.tenantId, patientId, document.documentId, { channel });
    setSuccess(`${document.title} sent via ${channel.toLowerCase()}`);
  }, [auth.accessToken, auth.tenantId]);

  const generatePassportDocument = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatientSummary?.id) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const generated = await generateVaccinationPassport(auth.accessToken, auth.tenantId, selectedPatientSummary.id);
      setDocumentPanel({ kind: "passport", document: generated });
      await refreshSelectedPatientContext(selectedPatientSummary.id);
      setSuccess("Immunization passport generated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate passport");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, refreshSelectedPatientContext, selectedPatientSummary?.id]);

  const generateCertificateDocument = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatientSummary?.id) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const vaccinationId = patientHistoryRows[0]?.id || null;
      const generated = await generateVaccinationCertificate(auth.accessToken, auth.tenantId, selectedPatientSummary.id, {
        certificateType,
        vaccinationId,
      });
      setDocumentPanel({ kind: "certificate", document: generated, certificateType });
      await refreshSelectedPatientContext(selectedPatientSummary.id);
      setSuccess("Vaccination certificate generated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate certificate");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, certificateType, patientHistoryRows, refreshSelectedPatientContext, selectedPatientSummary?.id]);

  const queuePatientVaccinationReminders = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatientSummary?.id) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await queueVaccinationReminders(auth.accessToken, auth.tenantId, selectedPatientSummary.id);
      await refreshSelectedPatientContext(selectedPatientSummary.id);
      setSuccess("Vaccination reminders queued");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to queue vaccination reminders");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, refreshSelectedPatientContext, selectedPatientSummary?.id]);

  const openVaccinationProof = React.useCallback(async (row: PatientVaccination) => {
    if (!auth.accessToken || !auth.tenantId || !row.proofDocumentId) {
      return;
    }
    try {
      const response = await getPatientDocumentViewUrl(auth.accessToken, auth.tenantId, row.patientId, row.proofDocumentId);
      const url = URL.createObjectURL(response.blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open vaccination card");
    }
  }, [auth.accessToken, auth.tenantId]);

  const openExternalVaccinationDialog = React.useCallback(() => {
    setExternalVaccinationForm(emptyExternalVaccinationForm());
    setExternalFieldErrors({});
    setExternalDialogOpen(true);
    setExternalPromptOpen(false);
    if (selectedPatient?.id) {
      setExternalPromptSeenPatientId(selectedPatient.id);
    }
  }, [selectedPatient?.id]);

  const openExternalVaccinationCardUpload = React.useCallback(() => {
    openExternalVaccinationDialog();
    setAutoOpenCardUpload(true);
  }, [openExternalVaccinationDialog]);

  const skipExternalPrompt = React.useCallback(() => {
    if (selectedPatient?.id) {
      setExternalPromptSeenPatientId(selectedPatient.id);
    }
    setExternalPromptOpen(false);
  }, [selectedPatient?.id]);

  const handleRoadmapSelectItem = React.useCallback((item: VaccinationTimelineItem) => {
    handleSelectRecommendation(item);
  }, [handleSelectRecommendation]);

  const handleRoadmapRecordItem = React.useCallback((item: VaccinationTimelineItem) => {
    handleSelectRecommendation(item);
    window.setTimeout(() => document.getElementById("vaccination-vaccineId")?.focus(), 0);
  }, [handleSelectRecommendation]);

  const toggleRoadmapStage = React.useCallback((stageKey: string) => {
    setRoadmapExpandedStageIds((current) => ({
      ...current,
      [stageKey]: !current[stageKey],
    }));
  }, []);

  const expandAllRoadmapStages = React.useCallback(() => {
    const next: Record<string, boolean> = {};
    vaccinationRoadmapVisibleStages.forEach((stage) => {
      next[stage.key] = true;
    });
    setRoadmapExpandedStageIds(next);
  }, [vaccinationRoadmapVisibleStages]);

  const collapseAllRoadmapStages = React.useCallback(() => {
    const next: Record<string, boolean> = {};
    vaccinationRoadmapVisibleStages.forEach((stage) => {
      next[stage.key] = false;
    });
    setRoadmapExpandedStageIds(next);
  }, [vaccinationRoadmapVisibleStages]);

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Vaccinations
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Vaccine master data, recording, and due/overdue follow-up tracking.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void loadAll()} disabled={loading || saving}>
          {loading ? "Refreshing..." : "Refresh"}
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {workflowNotice ? <Alert severity="warning">{workflowNotice}</Alert> : null}
      {selectedPatient && unmappedVaccinationCards.length > 0 ? (
        <Alert severity="warning">
          Vaccination card uploaded but not yet mapped to vaccine history.
        </Alert>
      ) : null}

      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent sx={{ py: 1, "&:last-child": { pb: 1 } }}>
          <Tabs
            value={activeVaccinationSection}
            onChange={handleVaccinationSectionTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{ minHeight: 40 }}
          >
            <Tab value="record" label="Record Vaccination" />
            <Tab value="timeline" label="Timeline" />
            <Tab value="history" label="History" />
            <Tab value="schedule" label="Schedule Calendar" />
            <Tab value="master" label="Vaccine Master" />
          </Tabs>
        </CardContent>
      </Card>

      <Dialog open={externalPromptOpen && Boolean(selectedPatient)} onClose={skipExternalPrompt} maxWidth="sm" fullWidth>
        <DialogTitle>External vaccination history</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.25}>
            <Typography variant="body2">
              Has this patient taken any vaccines outside this clinic?
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Adding external history helps recommendations avoid repeating vaccines already taken elsewhere.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={skipExternalPrompt}>Skip for now</Button>
          <Button onClick={openExternalVaccinationDialog}>Add External Vaccination</Button>
          <Button variant="contained" onClick={openExternalVaccinationCardUpload}>
            Upload Vaccination Card
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={noHistoryConfirmOpen} onClose={() => setNoHistoryConfirmOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>No vaccination history recorded</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2">
            No previous vaccination history is recorded. Continue with today&apos;s vaccination?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setNoHistoryConfirmOpen(false);
            openExternalVaccinationDialog();
          }}>
            Add External History First
          </Button>
          <Button variant="contained" onClick={() => void continueWithoutHistory()}>
            Continue
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={externalDialogOpen} onClose={() => setExternalDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Add external vaccination</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info">Source = EXTERNAL. Add completed doses taken at another clinic, hospital, or pharmacy.</Alert>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12, md: 8 }}>
                <TextField
                  id="external-vaccination-vaccineName"
                  fullWidth
                  size="small"
                  label="Vaccine name"
                  value={externalVaccinationForm.vaccineName}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, vaccineName: e.target.value }))}
                  error={Boolean(externalFieldErrors.vaccineName)}
                  helperText={externalFieldErrors.vaccineName || "Required."}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  id="external-vaccination-doseNumber"
                  fullWidth
                  size="small"
                  label="Dose number"
                  value={externalVaccinationForm.doseNumber}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, doseNumber: e.target.value }))}
                  error={Boolean(externalFieldErrors.doseNumber)}
                  helperText={externalFieldErrors.doseNumber || "Optional."}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="external-vaccination-givenDate"
                  fullWidth
                  size="small"
                  type="date"
                  label="Given date"
                  value={externalVaccinationForm.givenDate}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, givenDate: e.target.value }))}
                  InputLabelProps={{ shrink: true }}
                  error={Boolean(externalFieldErrors.givenDate)}
                  helperText={externalFieldErrors.givenDate || "Required."}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="external-vaccination-externalPlace"
                  fullWidth
                  size="small"
                  label="Place / clinic / hospital"
                  value={externalVaccinationForm.externalPlace}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, externalPlace: e.target.value }))}
                  error={Boolean(externalFieldErrors.externalPlace)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="external-vaccination-batchNumber"
                  fullWidth
                  size="small"
                  label="Batch number"
                  value={externalVaccinationForm.batchNumber}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, batchNumber: e.target.value }))}
                  error={Boolean(externalFieldErrors.batchNumber)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="external-vaccination-brandName"
                  fullWidth
                  size="small"
                  label="Brand name"
                  value={externalVaccinationForm.brandName}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, brandName: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  id="external-vaccination-manufacturer"
                  fullWidth
                  size="small"
                  label="Manufacturer"
                  value={externalVaccinationForm.manufacturer}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, manufacturer: e.target.value }))}
                />
              </Grid>
              <Grid size={12}>
                <TextField
                  id="external-vaccination-notes"
                  fullWidth
                  size="small"
                  multiline
                  minRows={2}
                  label="Notes"
                  value={externalVaccinationForm.notes}
                  onChange={(e) => setExternalVaccinationForm((current) => ({ ...current, notes: e.target.value }))}
                />
              </Grid>
              <Grid size={12}>
                <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
                  <Button variant="outlined" onClick={() => externalCardFileRef.current?.click()}>
                    Upload Vaccination Card
                  </Button>
                  <Typography variant="caption" color="text.secondary">
                    PDF, JPG, or PNG stored as a clinical document.
                  </Typography>
                  {externalVaccinationForm.proofDocumentId ? (
                    <Chip size="small" label="Uploaded for review" color="warning" variant="outlined" />
                  ) : null}
                </Stack>
                <input ref={externalCardFileRef} hidden type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={(e) => void handleExternalCardFile(e)} />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setExternalDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveExternalVaccination()}>Save External History</Button>
        </DialogActions>
      </Dialog>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.25}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Record vaccination
                </Typography>
                <TextField size="small" label="Search patient" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} helperText="Search by patient number, mobile, or name" />
                {patientSearchResults.length > 0 && !selectedPatient ? (
                  <Card variant="outlined">
                    <List dense disablePadding>
                      {patientSearchResults.map((patient) => (
                        <ListItemButton key={patient.id} onClick={() => setSelectedPatient(patient)}>
                          <ListItemText primary={`${patient.firstName} ${patient.lastName}`} secondary={`${patient.patientNumber} • ${patient.mobile}`} />
                        </ListItemButton>
                      ))}
                    </List>
                  </Card>
                ) : null}
                {selectedPatient ? (
                  <Chip
                    label={`${selectedPatient.firstName} ${selectedPatient.lastName} • ${selectedPatient.patientNumber}`}
                    onDelete={() => setSelectedPatient(null)}
                    sx={compactChipSx}
                  />
                ) : null}

                {selectedPatientSummary ? (
                  <Card variant="outlined">
                    <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                      <Stack spacing={0.75}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Patient summary</Typography>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap">
                          <Chip size="small" label={selectedPatientSummary.patientNumber} variant="outlined" />
                          <Chip size="small" label={selectedPatientSummary.gender} variant="outlined" />
                          <Chip size="small" label={selectedPatientSummary.ageYears != null ? `${selectedPatientSummary.ageYears} yrs` : "Age n/a"} variant="outlined" />
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{selectedPatientSummary.firstName} {selectedPatientSummary.lastName}</Typography>
                        <Typography variant="caption" color="text.secondary">Mobile: {selectedPatientSummary.mobile || "-"}</Typography>
                        <Typography variant="caption" color="text.secondary">Allergies: {selectedPatientSummary.allergies || "None recorded"}</Typography>
                        {selectedPatientSummary.ageYears != null && selectedPatientSummary.ageYears >= 18 ? (
                          <Alert severity="info" sx={{ py: 0.5 }}>
                            Routine childhood vaccines are not shown for this adult patient unless catch-up is configured.
                          </Alert>
                        ) : null}
                      </Stack>
                    </CardContent>
                  </Card>
                ) : null}

                {selectedPatient ? (
                  <Card variant="outlined" sx={{ bgcolor: "background.paper" }}>
                    <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                      <Stack spacing={1}>
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                            Vaccination recommendations
                          </Typography>
                          {recommendationsLoading ? <CircularProgress size={16} /> : null}
                        </Box>
                        <Typography variant="caption" color="text.secondary">
                          Recommendations are assistive. Verify against clinic protocol.
                        </Typography>
                        {recommendationsError ? <Alert severity="warning">{recommendationsError}</Alert> : null}
                        {["recommendedToday", "overdue", "upcoming", "completed", "optionalRiskBased"].map((key) => {
                          const rows = recommendationGroups[key as RecommendationSection];
                          if (!rows.length) {
                            return null;
                          }
                          return (
                            <Box key={key} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1, p: 1 }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.75 }}>
                                {key === "recommendedToday"
                                  ? "Recommended Today"
                                  : key === "overdue"
                                    ? "Overdue"
                                    : key === "upcoming"
                                      ? "Upcoming"
                                      : key === "completed"
                                        ? "Completed"
                                        : key === "optionalRiskBased"
                                          ? "Optional / Risk-Based"
                                          : "Not Applicable"}
                              </Typography>
                              {key === "optionalRiskBased" ? (
                                <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.75 }}>
                                  Optional or risk-based vaccines are separated from the main due list.
                                </Typography>
                              ) : null}
                              <Stack spacing={0.75}>
                                {rows.map((item) => (
                                  <Box key={`${item.vaccineId}-${item.status}-${item.reasonText}`} sx={{ display: "flex", gap: 1, justifyContent: "space-between", flexWrap: "wrap", alignItems: "center" }}>
                                    <Box>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                        {item.vaccineName}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {[item.brandName, item.manufacturer, item.route, item.administrationSite].filter(Boolean).join(" • ") || "No metadata"}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary" display="block">
                                        {item.reasonText}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                                      <Chip size="small" label={recommendationStatusLabel(item.status)} color={recommendationStatusColor(item.status)} variant="outlined" />
                                      {item.status !== "COMPLETED" ? (
                                        <Button size="small" variant="outlined" onClick={() => handleSelectRecommendation(item)}>
                                          Select Vaccine
                                        </Button>
                                      ) : null}
                                    </Stack>
                                  </Box>
                                ))}
                              </Stack>
                            </Box>
                          );
                        })}
                        {recommendationGroups.notApplicable.length ? (
                          <Typography variant="caption" color="text.secondary">
                            Some vaccines are not applicable for this patient and are hidden from the main recommendation list.
                          </Typography>
                        ) : null}
                        {!recommendationsLoading && !recommendationsError && !recommendations
                          ? <Typography variant="caption" color="text.secondary">Load a patient to view age-based recommendations.</Typography>
                          : null}
                      </Stack>
                    </CardContent>
                  </Card>
                ) : null}

                <FormControl fullWidth size="small">
                  <InputLabel id="vaccination-vaccine-label"><RequiredLabel text="Vaccine" required /></InputLabel>
                    <Select
                      id="vaccination-vaccineId"
                      labelId="vaccination-vaccine-label"
                      label="Vaccine"
                      value={vaccinationForm.vaccineId}
                      disabled={!vaccines.length}
                      onChange={(e) => {
                        const vaccineId = String(e.target.value);
                        const vaccine = vaccines.find((item) => item.id === vaccineId);
                        const mappedStocks = vaccine?.stockTrackingEnabled && vaccine.inventoryItemId
                          ? stocks
                              .filter((stock) => stock.medicineId === vaccine.inventoryItemId && stock.quantityOnHand > 0)
                              .sort((left, right) => {
                                const leftExpiry = left.expiryDate ? new Date(left.expiryDate).getTime() : Number.POSITIVE_INFINITY;
                                const rightExpiry = right.expiryDate ? new Date(right.expiryDate).getTime() : Number.POSITIVE_INFINITY;
                                if (leftExpiry !== rightExpiry) {
                                  return leftExpiry - rightExpiry;
                                }
                                return new Date(left.updatedAt).getTime() - new Date(right.updatedAt).getTime();
                              })
                          : [];
                        setVaccinationForm((current) => ({
                          ...current,
                          vaccineId,
                          stockBatchId: mappedStocks[0]?.id || "",
                          billItemUnitPrice: vaccine?.defaultPrice != null ? vaccine.defaultPrice.toFixed(2) : current.billItemUnitPrice,
                        }));
                      }}
                      error={Boolean(vaccinationFieldErrors.vaccineId)}
                    >
                    {vaccines.map((vaccine) => (
                      <MenuItem key={vaccine.id} value={vaccine.id}>
                        {vaccine.vaccineName}
                      </MenuItem>
                    ))}
                  </Select>
                  {vaccinationFieldErrors.vaccineId ? <Typography variant="caption" color="error">{vaccinationFieldErrors.vaccineId}</Typography> : null}
                </FormControl>
                {!vaccines.length ? (
                  <Alert severity="info">No vaccine master records. Ask Clinic Admin to import vaccines.</Alert>
                ) : null}

                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-doseNumber" label="Dose number" value={vaccinationForm.doseNumber} onChange={(e) => setVaccinationForm((current) => ({ ...current, doseNumber: e.target.value }))} error={Boolean(vaccinationFieldErrors.doseNumber)} helperText={vaccinationFieldErrors.doseNumber || "Optional positive whole number."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-givenDate" label={<RequiredLabel text="Given date" required />} type="date" value={vaccinationForm.givenDate} onChange={(e) => setVaccinationForm((current) => ({ ...current, givenDate: e.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(vaccinationFieldErrors.givenDate)} helperText={vaccinationFieldErrors.givenDate || "Required."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-route" label="Route" value={vaccinationForm.route} onChange={(e) => setVaccinationForm((current) => ({ ...current, route: e.target.value }))} helperText="Optional. Populated from recommendations when available." />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-administrationSite" label="Administration site" value={vaccinationForm.administrationSite} onChange={(e) => setVaccinationForm((current) => ({ ...current, administrationSite: e.target.value }))} helperText="Optional. Populated from recommendations when available." />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-nextDueDate" label="Next due date" type="date" value={vaccinationForm.nextDueDate} onChange={(e) => setVaccinationForm((current) => ({ ...current, nextDueDate: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(vaccinationFieldErrors.nextDueDate)} helperText={vaccinationFieldErrors.nextDueDate} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-batchNumber" label="Batch number" value={vaccinationForm.batchNumber} onChange={(e) => setVaccinationForm((current) => ({ ...current, batchNumber: e.target.value }))} error={Boolean(vaccinationFieldErrors.batchNumber)} helperText={vaccinationFieldErrors.batchNumber || "Optional, max 60 characters."} />
                  </Grid>
                  {selectedVaccinationVaccine?.stockTrackingEnabled ? (
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="vaccination-stock-batch-label">Inventory batch</InputLabel>
                        <Select
                          labelId="vaccination-stock-batch-label"
                          label="Inventory batch"
                          value={vaccinationForm.stockBatchId}
                          onChange={(e) => setVaccinationForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                          error={Boolean(vaccinationFieldErrors.stockBatchId)}
                        >
                          <MenuItem value="">Auto-select FEFO batch</MenuItem>
                          {selectedVaccinationStocks.map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {stock.batchNumber || stock.id} {stock.expiryDate ? `• Exp ${stock.expiryDate}` : ""} {stock.quantityOnHand != null ? `• Qty ${stock.quantityOnHand}` : ""}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                      {selectedVaccinationStocks.length === 0 ? (
                        <Typography variant="caption" color="warning.main">
                          No stock available for this vaccine.
                        </Typography>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          FEFO batch: {selectedVaccinationStock?.batchNumber || selectedVaccinationStock?.id || "-"}
                        </Typography>
                      )}
                    </Grid>
                  ) : null}
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccination-notes" label="Notes" value={vaccinationForm.notes} onChange={(e) => setVaccinationForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} error={Boolean(vaccinationFieldErrors.notes)} helperText={vaccinationFieldErrors.notes || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="vaccination-admin-label">Administered by</InputLabel>
                      <Select
                        labelId="vaccination-admin-label"
                        label="Administered by"
                        value={vaccinationForm.administeredByUserId}
                        onChange={(e) => setVaccinationForm((current) => ({ ...current, administeredByUserId: String(e.target.value) }))}
                      >
                        <MenuItem value="">Current user</MenuItem>
                        {users.map((user) => (
                          <MenuItem key={user.appUserId} value={user.appUserId}>
                            {user.displayName || user.email || user.appUserId}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-billItemUnitPrice" label={<RequiredLabel text="Bill item unit price" required={vaccinationForm.addToBill} />} value={vaccinationForm.billItemUnitPrice} onChange={(e) => setVaccinationForm((current) => ({ ...current, billItemUnitPrice: e.target.value }))} disabled={!vaccinationForm.addToBill} error={Boolean(vaccinationFieldErrors.billItemUnitPrice)} helperText={vaccinationForm.addToBill ? (vaccinationFieldErrors.billItemUnitPrice || "Required when adding to bill.") : ""} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={vaccinationForm.addToBill}
                          onChange={(e) => setVaccinationForm((current) => ({
                            ...current,
                            addToBill: e.target.checked,
                            billItemUnitPrice: e.target.checked
                              ? current.billItemUnitPrice || vaccines.find((item) => item.id === current.vaccineId)?.defaultPrice?.toFixed(2) || ""
                              : current.billItemUnitPrice,
                          }))}
                        />
                      }
                      label="Add to bill"
                    />
                  </Grid>
                </Grid>

                <Button variant="contained" size="small" onClick={() => void recordVaccination()} disabled={saving}>
                  {saving ? "Saving..." : "Record Vaccination"}
                </Button>
              </Stack>
            </CardContent>
          </Card>

                {canManageMaster ? (
                  <Card ref={vaccineMasterSectionRef} sx={{ mt: 2 }}>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Vaccine master
                    </Typography>
                    <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => openVaccineEditor()}>
                      {editingVaccineId ? "New vaccine" : "Reset"}
                    </Button>
                  </Box>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Button size="small" variant="outlined" onClick={() => void downloadTemplate()}>
                      Download CSV Template
                    </Button>
                    <Button size="small" variant="outlined" onClick={openImportFilePicker}>
                      Upload CSV
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => void downloadExport()}>
                      Export CSV
                    </Button>
                  </Box>
                  <Grid container spacing={1}>
                    <Grid size={12}>
                      <TextField size="small" fullWidth id="vaccine-vaccineName" label={<RequiredLabel text="Vaccine name" required />} value={vaccineForm.vaccineName} onChange={(e) => setVaccineForm((current) => ({ ...current, vaccineName: e.target.value }))} required error={Boolean(vaccineFieldErrors.vaccineName)} helperText={vaccineFieldErrors.vaccineName || "Required, max 100 characters."} />
                    </Grid>
                    <Grid size={12}>
                      <TextField size="small" fullWidth id="vaccine-description" label="Description" value={vaccineForm.description ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, description: e.target.value }))} multiline minRows={2} error={Boolean(vaccineFieldErrors.description)} helperText={vaccineFieldErrors.description || "Optional, max 250 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-manufacturer" label="Manufacturer" value={vaccineForm.manufacturer ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, manufacturer: e.target.value }))} error={Boolean(vaccineFieldErrors.manufacturer)} helperText={vaccineFieldErrors.manufacturer || "Optional, max 250 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-brandName" label="Brand name" value={vaccineForm.brandName ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, brandName: e.target.value }))} error={Boolean(vaccineFieldErrors.brandName)} helperText={vaccineFieldErrors.brandName || "Optional, max 250 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-vaccineGroup" label="Vaccine group" value={vaccineForm.vaccineGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, vaccineGroup: e.target.value }))} error={Boolean(vaccineFieldErrors.vaccineGroup)} helperText={vaccineFieldErrors.vaccineGroup || "Optional, max 128 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-doseNumber" label="Dose number" value={vaccineForm.doseNumber ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, doseNumber: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.doseNumber)} helperText={vaccineFieldErrors.doseNumber || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-route" label="Route" value={vaccineForm.route ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, route: e.target.value || null }))} error={Boolean(vaccineFieldErrors.route)} helperText={vaccineFieldErrors.route || "IM, SC, ORAL, NASAL, or ID."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-administrationSite" label="Administration site" value={vaccineForm.administrationSite ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, administrationSite: e.target.value }))} error={Boolean(vaccineFieldErrors.administrationSite)} helperText={vaccineFieldErrors.administrationSite || "Optional, max 128 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-storageTemperature" label="Storage temperature" value={vaccineForm.storageTemperature ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, storageTemperature: e.target.value }))} error={Boolean(vaccineFieldErrors.storageTemperature)} helperText={vaccineFieldErrors.storageTemperature || "Optional, max 128 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-ndcBarcode" label="NDC barcode" value={vaccineForm.ndcBarcode ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, ndcBarcode: e.target.value }))} error={Boolean(vaccineFieldErrors.ndcBarcode)} helperText={vaccineFieldErrors.ndcBarcode || "Optional, max 128 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        select
                        size="small"
                        fullWidth
                        id="vaccine-inventoryItemId"
                        label="Inventory item"
                        value={vaccineForm.inventoryItemId ?? ""}
                        onChange={(e) => {
                          const selectedMedicine = medicines.find((medicine) => medicine.id === String(e.target.value)) || null;
                          setVaccineForm((current) => ({
                            ...current,
                            inventoryItemId: selectedMedicine?.id || null,
                            inventoryItemCode: selectedMedicine?.barcode || selectedMedicine?.externalCode || current.inventoryItemCode,
                          }));
                        }}
                        helperText="Optional mapping to Inventory item for stock deduction."
                      >
                        <MenuItem value="">No inventory mapping</MenuItem>
                        {medicines.map((medicine) => (
                          <MenuItem key={medicine.id} value={medicine.id}>
                            {medicine.medicineName} {medicine.externalCode ? `• ${medicine.externalCode}` : ""}
                          </MenuItem>
                        ))}
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        id="vaccine-inventoryItemCode"
                        label="Inventory item code"
                        value={vaccineForm.inventoryItemCode ?? ""}
                        onChange={(e) => setVaccineForm((current) => ({ ...current, inventoryItemCode: e.target.value || null }))}
                        helperText="Optional code used for inventory mapping."
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={Boolean(vaccineForm.stockTrackingEnabled)}
                            onChange={(e) => setVaccineForm((current) => ({ ...current, stockTrackingEnabled: e.target.checked }))}
                          />
                        }
                        label="Track stock in inventory"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-scheduleType" label="Schedule type" value={vaccineForm.scheduleType ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, scheduleType: e.target.value || null }))} error={Boolean(vaccineFieldErrors.scheduleType)} helperText={vaccineFieldErrors.scheduleType || "UIP, IAP, CLINIC_CUSTOM, TRAVEL, or ADULT."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-ageGroup" label="Age group" value={vaccineForm.ageGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, ageGroup: e.target.value }))} error={Boolean(vaccineFieldErrors.ageGroup)} helperText={vaccineFieldErrors.ageGroup || "Optional, max 60 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField size="small" fullWidth id="vaccine-minAgeDays" label="Min age days" value={vaccineForm.minAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, minAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.minAgeDays)} helperText={vaccineFieldErrors.minAgeDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField size="small" fullWidth id="vaccine-recommendedAgeDays" label="Recommended age days" value={vaccineForm.recommendedAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recommendedAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.recommendedAgeDays)} helperText={vaccineFieldErrors.recommendedAgeDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField size="small" fullWidth id="vaccine-maxAgeDays" label="Max age days" value={vaccineForm.maxAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, maxAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.maxAgeDays)} helperText={vaccineFieldErrors.maxAgeDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField
                        size="small"
                        fullWidth
                        id="vaccine-gapDays"
                        label="Gap days"
                        value={vaccineForm.gapDays ?? ""}
                        onChange={(e) => setVaccineForm((current) => ({ ...current, gapDays: e.target.value ? Number(e.target.value) : null }))}
                        error={Boolean(vaccineFieldErrors.gapDays)}
                        helperText={vaccineFieldErrors.gapDays || "Optional, zero or greater."}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-boosterGapDays" label="Booster gap days" value={vaccineForm.boosterGapDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, boosterGapDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.boosterGapDays)} helperText={vaccineFieldErrors.boosterGapDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 8 }}>
                      <TextField size="small" fullWidth id="vaccine-boosterRules" label="Booster rules" value={vaccineForm.boosterRules ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, boosterRules: e.target.value }))} error={Boolean(vaccineFieldErrors.boosterRules)} helperText={vaccineFieldErrors.boosterRules || "Optional, max 500 characters."} multiline minRows={2} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-recurrenceDays" label="Recurrence days" value={vaccineForm.recurrenceDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recurrenceDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.recurrenceDays)} helperText={vaccineFieldErrors.recurrenceDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-recommendationPolicy" label="Recommendation policy" value={vaccineForm.recommendationPolicy ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recommendationPolicy: e.target.value || null }))} error={Boolean(vaccineFieldErrors.recommendationPolicy)} helperText={vaccineFieldErrors.recommendationPolicy || "STANDARD_CHILDHOOD, ADULT_ROUTINE, RECURRING, etc."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth id="vaccine-catchUpPolicy" label="Catch-up policy" value={vaccineForm.catchUpPolicy ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, catchUpPolicy: e.target.value || null }))} error={Boolean(vaccineFieldErrors.catchUpPolicy)} helperText={vaccineFieldErrors.catchUpPolicy || "NONE, ALLOWED_UNTIL_AGE, LIFETIME, or CLINICIAN_DECISION."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-catchUpMaxAgeDays" label="Catch-up max age days" value={vaccineForm.catchUpMaxAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, catchUpMaxAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.catchUpMaxAgeDays)} helperText={vaccineFieldErrors.catchUpMaxAgeDays || "Optional, zero or greater."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-applicableAgeGroup" label="Applicable age group" value={vaccineForm.applicableAgeGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, applicableAgeGroup: e.target.value || null }))} error={Boolean(vaccineFieldErrors.applicableAgeGroup)} helperText={vaccineFieldErrors.applicableAgeGroup || "NEWBORN, INFANT, TODDLER, CHILD, ADOLESCENT, ADULT, OLDER_ADULT, or ALL."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField size="small" fullWidth id="vaccine-clinicalIndications" label="Clinical indications" value={vaccineForm.clinicalIndications ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, clinicalIndications: e.target.value || null }))} error={Boolean(vaccineFieldErrors.clinicalIndications)} helperText={vaccineFieldErrors.clinicalIndications || "Optional comma-separated indications, max 500 characters."} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={vaccineForm.recurring}
                            onChange={(e) => setVaccineForm((current) => ({ ...current, recurring: e.target.checked }))}
                          />
                        }
                        label="Recurring"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        size="small"
                        fullWidth
                        id="vaccine-defaultPrice"
                        label="Default price"
                        value={vaccineForm.defaultPrice ?? ""}
                        onChange={(e) => setVaccineForm((current) => ({ ...current, defaultPrice: e.target.value ? Number(e.target.value) : null }))}
                        error={Boolean(vaccineFieldErrors.defaultPrice)}
                        helperText={vaccineFieldErrors.defaultPrice || "Optional, zero or greater, up to 2 decimals."}
                      />
                    </Grid>
                    <Grid size={12}>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={vaccineForm.active}
                            onChange={(e) => setVaccineForm((current) => ({ ...current, active: e.target.checked }))}
                          />
                        }
                        label={vaccineForm.active ? "Active" : "Inactive"}
                      />
                    </Grid>
                  </Grid>
                  <Button variant="contained" size="small" onClick={() => void saveVaccine()} disabled={saving}>
                    {editingVaccineId ? "Update Vaccine" : "Save Vaccine"}
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          ) : null}
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Due vaccinations
                  </Typography>
                  {loading ? (
                    <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                      <CircularProgress />
                    </Box>
                  ) : dueRows.length === 0 ? (
                    <CompactEmptyState title="No due vaccinations. Upcoming follow-ups will appear here." subtitle="Patients with upcoming vaccine follow-up will appear here." />
                  ) : (
                    <Box sx={{ maxHeight: 380, overflow: "auto", pr: 0.5 }}>
                      <Stack spacing={1}>
                        {dueRows.map((row) => (
                          <Card key={row.id} variant="outlined">
                            <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                            <Stack spacing={1}>
                              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                                <Box>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{row.patientName || row.patientNumber || row.patientId}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                                </Box>
                                <Chip size="small" label={dueStatusLabel(dueStatusForVaccination(row))} color={dueStatusColor(dueStatusForVaccination(row))} variant="outlined" />
                              </Box>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.vaccineName}</Typography>
                              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                <Chip size="small" variant="outlined" label={`Due: ${row.nextDueDate || "-"}`} />
                                <Chip size="small" variant="outlined" label={`Mobile: ${row.patientMobile || "-"}`} />
                                {row.reminderNotificationId ? <Chip size="small" color="info" variant="outlined" label={row.reminderStatus ? `Reminder ${row.reminderStatus.toLowerCase()}` : "Reminder queued"} /> : null}
                              </Stack>
                              <Stack direction="row" spacing={1} flexWrap="wrap">
                                <Button size="small" variant="outlined" onClick={() => openPatient(row.patientId)}>Open Patient</Button>
                                <Button size="small" variant="contained" onClick={() => handleRecordFromRow(row)}>Record Vaccination</Button>
                                {canBookAppointment ? (
                                  <Button size="small" variant="text" onClick={() => bookAppointment(row)}>Book Appointment</Button>
                                ) : null}
                              </Stack>
                            </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Overdue vaccinations
                  </Typography>
                  {loading ? (
                    <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                      <CircularProgress />
                    </Box>
                  ) : overdueRows.length === 0 ? (
                    <CompactEmptyState title="No overdue vaccinations. Missed vaccine follow-ups will appear here." subtitle="Overdue follow-up entries will appear here when patients miss a due date." />
                  ) : (
                    <Box sx={{ maxHeight: 380, overflow: "auto", pr: 0.5 }}>
                      <Stack spacing={1}>
                        {overdueRows.map((row) => (
                          <Card key={row.id} variant="outlined">
                            <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                              <Stack spacing={1}>
                                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                                  <Box>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{row.patientName || row.patientNumber || row.patientId}</Typography>
                                    <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                                  </Box>
                                  <Chip size="small" label={`Overdue by ${row.nextDueDate ? Math.max(0, Math.ceil((Date.now() - new Date(row.nextDueDate).getTime()) / 86_400_000)) : 0} days`} color="error" variant="outlined" />
                                </Box>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.vaccineName}</Typography>
                                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                  <Chip size="small" variant="outlined" label={`Due: ${row.nextDueDate || "-"}`} />
                                  <Chip size="small" variant="outlined" label={`Mobile: ${row.patientMobile || "-"}`} />
                                  {row.reminderNotificationId ? <Chip size="small" color="info" variant="outlined" label={row.reminderStatus ? `Reminder ${row.reminderStatus.toLowerCase()}` : "Reminder queued"} /> : null}
                                </Stack>
                                <Stack direction="row" spacing={1} flexWrap="wrap">
                                  <Button size="small" variant="outlined" onClick={() => openPatient(row.patientId)}>Open Patient</Button>
                                  <Button size="small" variant="contained" onClick={() => handleRecordFromRow(row)}>Record Vaccination</Button>
                                  {canBookAppointment ? (
                                    <Button size="small" variant="text" onClick={() => bookAppointment(row)}>Book Appointment</Button>
                                  ) : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>

            {selectedPatientSummary ? (
              <Card variant="outlined">
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={1}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Passport and certificates</Typography>
                      <Stack direction="row" spacing={0.5} flexWrap="wrap">
                        <Button size="small" variant="outlined" onClick={() => void generatePassportDocument()} disabled={saving}>
                          Generate Passport
                        </Button>
                        <Button size="small" variant="contained" onClick={() => void generateCertificateDocument()} disabled={saving}>
                          Generate Certificate
                        </Button>
                      </Stack>
                    </Box>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      <Chip size="small" label={vaccinationPassportReference ? "Passport ready" : "Passport not generated"} color={vaccinationPassportReference ? "success" : "default"} variant="outlined" />
                      <Chip size="small" label={`${vaccinationGeneratedDocuments.certificates.length} certificate(s)`} variant="outlined" />
                    </Stack>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      <FormControl size="small" sx={{ minWidth: 220 }}>
                        <InputLabel id="vaccination-certificate-type-label">Certificate type</InputLabel>
                        <Select
                          labelId="vaccination-certificate-type-label"
                          label="Certificate type"
                          value={certificateType}
                          onChange={(e) => setCertificateType(e.target.value as typeof certificateType)}
                        >
                          <MenuItem value="CHILD_IMMUNIZATION">Child Immunization Certificate</MenuItem>
                          <MenuItem value="SCHOOL_VACCINATION">School Vaccination Certificate</MenuItem>
                          <MenuItem value="TRAVEL_VACCINATION">Travel Vaccination Certificate</MenuItem>
                          <MenuItem value="SINGLE_VACCINATION">Single Vaccination Certificate</MenuItem>
                        </Select>
                      </FormControl>
                    </Stack>
                    {vaccinationPassportReference ? (
                      <Stack direction="row" spacing={0.75} flexWrap="wrap">
                        <Button size="small" variant="outlined" onClick={() => void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id!, "VIEW")}>View Passport</Button>
                        <Button size="small" variant="outlined" onClick={() => void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id!, "PRINT")}>Print Passport</Button>
                        <Button size="small" variant="outlined" onClick={() => void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id!, "DOWNLOAD")}>Download Passport PDF</Button>
                        {canSendVaccinationReceipt ? (
                          <>
                            <Button size="small" variant="text" onClick={() => void sendVaccinationGeneratedDocument(vaccinationPassportReference, selectedPatientSummary.id!, "EMAIL")}>Email Passport</Button>
                            <Button size="small" variant="text" onClick={() => void sendVaccinationGeneratedDocument(vaccinationPassportReference, selectedPatientSummary.id!, "WHATSAPP")}>WhatsApp Passport</Button>
                          </>
                        ) : null}
                      </Stack>
                    ) : null}
                    {vaccinationCertificateReference ? (
                      <Stack direction="row" spacing={0.75} flexWrap="wrap">
                        <Button size="small" variant="outlined" onClick={() => void openVaccinationDocument(vaccinationCertificateReference, selectedPatientSummary.id!, "VIEW")}>View Certificate</Button>
                        <Button size="small" variant="outlined" onClick={() => void openVaccinationDocument(vaccinationCertificateReference, selectedPatientSummary.id!, "DOWNLOAD")}>Download Certificate</Button>
                      </Stack>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {selectedPatientSummary ? (
              <Card variant="outlined">
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={1}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Reminder center</Typography>
                      <Button size="small" variant="outlined" onClick={() => void queuePatientVaccinationReminders()} disabled={saving}>
                        Queue reminders
                      </Button>
                    </Box>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      <Chip size="small" label={`Reminder pending: ${vaccinationReminderSummary.pending}`} variant="outlined" />
                      <Chip size="small" label={`Reminder sent: ${vaccinationReminderSummary.sent}`} variant="outlined" />
                      <Chip size="small" label={`Last reminder: ${vaccinationReminderSummary.latest?.createdAt ? vaccinationReminderSummary.latest.createdAt : "None"}`} variant="outlined" />
                      <Chip size="small" label={`Next reminder: ${vaccinationReminderSummary.nextReminderLabel}${vaccinationReminderSummary.nextReminderDate ? ` • ${vaccinationReminderSummary.nextReminderDate}` : ""}`} variant="outlined" />
                      <Chip size="small" label={`Channel: ${vaccinationReminderSummary.nextReminderChannel}`} variant="outlined" />
                    </Stack>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      <Chip size="small" label={vaccinationReminderSummary.latest?.status || "Pending"} color={vaccinationReminderSummary.latest?.status === "FAILED" ? "error" : vaccinationReminderSummary.latest?.status === "SENT" ? "success" : "warning"} variant="outlined" />
                      <Chip size="small" label={vaccinationReminderSummary.latest?.recipient || "No reminder history"} variant="outlined" />
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {canManageMaster ? (
              <Card>
                <CardContent sx={{ p: 1.25 }}>
                  <Stack spacing={1.25}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Vaccine list
                    </Typography>
                    {vaccines.length === 0 ? (
                      <CompactEmptyState title="No vaccines were found." subtitle="Create a vaccine in the master list to start recording doses." />
                    ) : (
                      <CompactTableFrame maxHeight={420}>
                        <Table size="small" stickyHeader sx={{ minWidth: 760 }}>
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ py: 0.7 }}>Name</TableCell>
                            <TableCell sx={{ py: 0.7 }}>Age group</TableCell>
                            <TableCell sx={{ py: 0.7 }}>Gap days</TableCell>
                            <TableCell sx={{ py: 0.7 }}>Default price</TableCell>
                            <TableCell sx={{ py: 0.7 }}>Status</TableCell>
                            <TableCell sx={{ py: 0.7 }} align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {vaccines.map((vaccine) => (
                            <TableRow key={vaccine.id}>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    {vaccine.vaccineName}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {vaccine.description || "No description"}
                                  </Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{vaccine.ageGroup || "-"}</TableCell>
                              <TableCell>{vaccine.gapDays ?? vaccine.recommendedGapDays ?? "-"}</TableCell>
                              <TableCell>{vaccine.defaultPrice?.toFixed(2) || "-"}</TableCell>
                              <TableCell>
                                <Chip size="small" label={vaccine.active ? "Active" : "Inactive"} color={vaccine.active ? "success" : "default"} />
                              </TableCell>
                              <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                <Button size="small" startIcon={<EditRoundedIcon fontSize="small" />} sx={{ whiteSpace: "nowrap" }} onClick={() => openVaccineEditor(vaccine)}>
                                  Edit
                                </Button>
                                <Button size="small" sx={{ whiteSpace: "nowrap" }} onClick={() => void deactivate(vaccine.id)} disabled={!vaccine.active || saving}>
                                  Deactivate
                                </Button>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                        </Table>
                      </CompactTableFrame>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}
          </Stack>
        </Grid>
      </Grid>

      <Box ref={historySectionRef}>
        <Card>
        <CardContent sx={{ p: 1.25 }}>
          <Stack spacing={1.25}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "baseline" }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Vaccination history
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Full-width clinical workspace for recorded, imported, and billed vaccinations.
                </Typography>
              </Box>
            </Box>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" label="Search patient" value={historyPatientFilter} onChange={(e) => setHistoryPatientFilter(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" label="Search vaccine" value={historyVaccineFilter} onChange={(e) => setHistoryVaccineFilter(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField fullWidth size="small" type="date" label="From" value={historyFromDate} onChange={(e) => setHistoryFromDate(e.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField fullWidth size="small" type="date" label="To" value={historyToDate} onChange={(e) => setHistoryToDate(e.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl fullWidth size="small">
                  <InputLabel id="vaccination-due-status-label">Due status</InputLabel>
                  <Select labelId="vaccination-due-status-label" label="Due status" value={historyDueStatus} onChange={(e) => setHistoryDueStatus(e.target.value as typeof historyDueStatus)}>
                    {["ALL", "DUE", "OVERDUE", "NOT_DUE"].map((status) => (
                      <MenuItem key={status} value={status}>{dueStatusLabel(status)}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl fullWidth size="small">
                  <InputLabel id="vaccination-source-filter-label">Source</InputLabel>
                  <Select labelId="vaccination-source-filter-label" label="Source" value={historySourceFilter} onChange={(e) => setHistorySourceFilter(e.target.value as typeof historySourceFilter)}>
                    {["ALL", "INTERNAL", "EXTERNAL"].map((status) => (
                      <MenuItem key={status} value={status}>{status === "ALL" ? "All" : status === "INTERNAL" ? "Internal" : "External"}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <FormControl fullWidth size="small">
                  <InputLabel id="vaccination-billing-filter-label">Billing status</InputLabel>
                  <Select labelId="vaccination-billing-filter-label" label="Billing status" value={historyBillingFilter} onChange={(e) => setHistoryBillingFilter(e.target.value as VaccinationHistoryBillingFilter)}>
                    {(["ALL", "UNBILLED", "BILL_CREATED", "PAYMENT_PENDING", "PAID", "REFUNDED", "CANCELLED"] as const).map((status) => (
                      <MenuItem key={status} value={status}>{vaccinationBillingFilterLabel(status)}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
            {!filteredHistoryRows.length ? (
              <CompactEmptyState
                title="No vaccinations have been recorded yet."
                subtitle="Vaccinations recorded internally or imported from external providers will appear here."
              />
            ) : (
              <CompactTableFrame maxHeight={610}>
                <Table size="small" stickyHeader sx={{ width: "100%", tableLayout: "fixed" }}>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ width: 128 }}>Date</TableCell>
                      <TableCell sx={{ width: 180 }}>Patient</TableCell>
                      <TableCell sx={{ width: 170 }}>Vaccine</TableCell>
                      <TableCell sx={{ width: 68 }}>Dose</TableCell>
                      <TableCell sx={{ width: 96 }}>Source</TableCell>
                      <TableCell sx={{ width: 118 }}>Next Due</TableCell>
                      <TableCell sx={{ width: 160 }}>Administered By</TableCell>
                      <TableCell align="right" sx={{ width: 260 }}>Billing / Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredHistoryRows.map((row) => {
                      const billing = vaccinationBillingStatus(row);
                      const expanded = Boolean(expandedHistoryRowIds[row.id]);
                      const vaccineMaster = vaccines.find((vaccine) => vaccine.id === row.vaccineId) || null;
                      const canSeeMenu = canManageVaccinationHistory || canCollectVaccinationPayment || canSendVaccinationReceipt || canVerifyExternalVaccination;
                      const primaryAction = billing.isUnbilled && canManageVaccinationHistory
                        ? (
                          <Button size="small" variant="outlined" onClick={() => void billVaccinationRow(row, false)} disabled={saving}>
                            Add to Bill
                          </Button>
                        )
                        : billing.hasPaymentDue && canCollectVaccinationPayment
                          ? (
                            <Button size="small" variant="outlined" onClick={() => void collectPaymentForRow(row)} disabled={saving || !row.billId || !canCollectVaccinationPayment}>
                              Collect Payment
                            </Button>
                          )
                          : billing.isPaid && canSendVaccinationReceipt
                            ? (
                              <Button size="small" variant="outlined" onClick={() => void openVaccinationReceiptForRow(row)} disabled={receiptActionLoading || !row.billId}>
                                View Receipt
                              </Button>
                            )
                            : null;
                      return (
                        <React.Fragment key={row.id}>
                          <TableRow hover>
                            <TableCell sx={{ width: 128 }}>
                              <Stack direction="row" spacing={0.5} alignItems="center">
                                <IconButton size="small" onClick={() => toggleHistoryRowExpanded(row.id)} aria-label={expanded ? "Collapse row details" : "Expand row details"}>
                                  <ExpandMoreRoundedIcon fontSize="small" sx={{ transform: expanded ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 150ms ease" }} />
                                </IconButton>
                                <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                  <Typography variant="body2" noWrap>{row.givenDate}</Typography>
                                  <Typography variant="caption" color="text.secondary" noWrap>{row.createdAt}</Typography>
                                </Stack>
                              </Stack>
                            </TableCell>
                            <TableCell sx={{ width: 180 }}>
                              <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{row.patientName || row.patientNumber || "-"}</Typography>
                                <Typography variant="caption" color="text.secondary" noWrap>{row.patientNumber || row.patientMobile || "-"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell sx={{ width: 170 }}>
                              <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{row.vaccineName}</Typography>
                                <Typography variant="caption" color="text.secondary" noWrap>{vaccineMaster?.brandName || vaccineMaster?.manufacturer || row.batchNumber || "-"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell sx={{ width: 68 }}>{row.doseNumber ?? "-"}</TableCell>
                            <TableCell sx={{ width: 96 }}>
                              <Chip size="small" label={row.source || "INTERNAL"} color={row.source === "EXTERNAL" ? "secondary" : "default"} variant="outlined" />
                            </TableCell>
                            <TableCell sx={{ width: 118 }}>
                              <Chip size="small" label={row.nextDueDate || "-"} color={statusColor(row.nextDueDate)} />
                            </TableCell>
                            <TableCell sx={{ width: 160 }}>
                              <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                <Typography variant="body2" noWrap>{row.administeredByUserName || "-"}</Typography>
                                <Typography variant="caption" color="text.secondary" noWrap>{row.recordedByUserName || "-"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell align="right" sx={{ width: 260 }}>
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" alignItems="center" flexWrap="wrap">
                                <Chip size="small" label={vaccinationAefiBadge(row).label} color={vaccinationAefiBadge(row).color as any} variant="outlined" sx={compactChipSx} />
                                {billing.badges.map((badge) => (
                                  <Chip key={badge.label} size="small" label={badge.label} color={badge.color as any} variant="outlined" sx={compactChipSx} />
                                ))}
                                {row.billNumber ? <Chip size="small" label={row.billNumber} variant="outlined" sx={compactChipSx} /> : null}
                                {primaryAction}
                                {canSeeMenu ? (
                                  <IconButton size="small" onClick={(e) => openHistoryRowActions(e, row)} aria-label="Vaccination row actions">
                                    <MoreVertRoundedIcon fontSize="small" />
                                  </IconButton>
                                ) : null}
                              </Stack>
                            </TableCell>
                          </TableRow>
                          <TableRow>
                            <TableCell colSpan={8} sx={{ py: 0, borderBottom: expanded ? "none" : undefined }}>
                              <Collapse in={expanded} timeout="auto" unmountOnExit>
                                <Box sx={{ py: 1, px: 1.5, mb: 1, bgcolor: "background.default", borderRadius: 1 }}>
                                  <Stack spacing={1}>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase" }}>Details</Typography>
                                    <Grid container spacing={0.75}>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Manufacturer: ${vaccineMaster?.manufacturer || row.inventoryBatchManufacturer || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Brand: ${vaccineMaster?.brandName || row.vaccineName || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Inventory item: ${vaccineMaster?.inventoryItemCode || row.inventoryItemCode || row.inventoryItemId || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Batch number: ${row.batchNumber || row.inventoryBatchNumber || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Expiry: ${row.inventoryBatchExpiryDate || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Stock movement: ${row.inventoryTransactionId || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Route: ${vaccineMaster?.route || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Administration site: ${vaccineMaster?.administrationSite || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Proof: ${row.proofDocumentId ? "Available" : "None"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Verification: ${row.verifiedStatus || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`AEFI: ${vaccinationAefiBadge(row).label}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`AEFI status: ${row.adverseEventStatus || "NONE"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Severity: ${row.adverseEventSeverity || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Outcome: ${row.adverseEventOutcome || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Follow-up: ${row.adverseEventFollowUpRequired ? (row.adverseEventFollowUpDate || "Required") : "No"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Authority: ${row.adverseEventReportedToAuthority ? "Reported" : "Unreported"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Recorded by: ${row.recordedByUserName || "-"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Reminder: ${row.reminderStatus || "None"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Passport: ${row.patientId === selectedPatientSummary?.id ? (vaccinationPassportReference ? "Generated" : "Not generated") : "—"}`} /></Grid>
                                      <Grid size={{ xs: 12, sm: 6, md: 4 }}><Chip size="small" label={`Certificate: ${row.patientId === selectedPatientSummary?.id ? (vaccinationCertificateReference ? "Generated" : "Not generated") : "—"}`} /></Grid>
                                      <Grid size={12}>
                                        <Typography variant="body2" color="text.secondary">
                                          Notes: {row.notes || "-"}
                                        </Typography>
                                      </Grid>
                                    </Grid>
                                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                                      {row.billNumber ? <Chip size="small" label={`Bill: ${row.billNumber}`} /> : null}
                                      {row.billStatus ? <Chip size="small" label={`Receipt: ${billing.hasReceipt ? "Ready" : "Not ready"}`} color={billing.hasReceipt ? "success" : "default"} /> : null}
                                    </Stack>
                                  </Stack>
                                </Box>
                              </Collapse>
                            </TableCell>
                          </TableRow>
                        </React.Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </CompactTableFrame>
            )}
          </Stack>
        </CardContent>
        </Card>
      </Box>

      <Card id="vaccination-timeline-section">
        <CardContent sx={{ p: 1.25 }}>
          <Stack spacing={1.25}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "baseline" }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Vaccination Timeline
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Age-based immunization roadmap for the selected patient.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Button size="small" variant="outlined" onClick={() => { if (selectedPatient) openPatient(selectedPatient.id); }}>
                  Open Patient
                </Button>
                <Button size="small" variant="outlined" onClick={openVaccinationHistoryWorkspace}>
                  View History
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    if (selectedPatientSummary?.id && vaccinationPassportReference) {
                      void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id, "VIEW");
                      return;
                    }
                    void generatePassportDocument();
                  }}
                  disabled={!selectedPatientSummary}
                >
                  View Passport
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    if (selectedPatientSummary?.id && vaccinationPassportReference) {
                      void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id, "PRINT");
                      return;
                    }
                    void generatePassportDocument();
                  }}
                  disabled={!selectedPatientSummary}
                >
                  Print Passport
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    if (selectedPatientSummary?.id && vaccinationPassportReference) {
                      void openVaccinationDocument(vaccinationPassportReference, selectedPatientSummary.id, "DOWNLOAD");
                      return;
                    }
                    void generatePassportDocument();
                  }}
                  disabled={!selectedPatientSummary}
                >
                  Download PDF
                </Button>
              </Stack>
            </Box>
            {selectedPatientSummary ? (
              <Stack spacing={1.25}>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Completed / Applicable"
                      value={`${vaccinationRoadmapProgress.completed}/${vaccinationRoadmapProgress.applicable || 0}`}
                      tone="success"
                      helper={selectedPatientSummary.patientNumber}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Completion %"
                      value={`${vaccinationRoadmapProgress.completionPercent}%`}
                      tone="info"
                      helper={`${selectedPatientAgeLabel}`}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Current Age"
                      value={selectedPatientAgeLabel}
                      tone="default"
                      helper={`Current stage: ${selectedPatientCurrentStage.label}`}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Next due vaccine"
                      value={vaccinationRoadmapProgress.nextDue?.vaccineName || "None"}
                      tone={vaccinationRoadmapProgress.nextDue ? recommendationStatusColor(vaccinationRoadmapProgress.nextDue.status) : "default"}
                      helper={vaccinationRoadmapProgress.nextDue?.reasonText || "No due vaccine"}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Overdue count"
                      value={vaccinationRoadmapProgress.overdueCount}
                      tone={vaccinationRoadmapProgress.overdueCount > 0 ? "error" : "success"}
                      helper={vaccinationRoadmapProgress.overdueCount > 0 ? "Action required" : "Up to date"}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}>
                    <CompactStatCard
                      label="Schedule Type"
                      value={formatScheduleType(recommendations?.scheduleType)}
                      tone="secondary"
                      helper="Recommendation schedule"
                    />
                  </Grid>
                </Grid>

                <VaccinationRoadmapStrip stages={vaccinationRoadmapStripStages} currentStageKey={selectedPatientCurrentStage.key} />

                <Card variant="outlined" sx={{ borderRadius: 2, bgcolor: "background.default" }}>
                  <CardContent sx={compactCardContentSx}>
                    <Stack spacing={1}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "flex-start" }}>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                            Current Stage
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
                            {vaccinationRoadmapCurrentStage?.label || selectedPatientCurrentStage.label}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Patient age: {selectedPatientAgeLabel} • Schedule: {formatScheduleType(recommendations?.scheduleType)}
                          </Typography>
                        </Box>
                        <Chip
                          label={vaccinationRoadmapCurrentStage?.helper || selectedPatientCurrentStage.helper}
                          color="primary"
                          variant="outlined"
                          sx={compactChipSx}
                        />
                      </Box>
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <Card variant="outlined" sx={{ borderRadius: 2, height: "100%" }}>
                            <CardContent sx={compactCardContentSx}>
                              <Stack spacing={0.75}>
                                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase" }}>
                                  Due now
                                </Typography>
                                {vaccinationRoadmapCurrentStageDueItems.length ? (
                                  <Stack spacing={0.5}>
                                    {vaccinationRoadmapCurrentStageDueItems.slice(0, 4).map((item) => {
                                      const meta = statusMeta(item.status);
                                      return (
                                        <Box key={`${item.vaccineId}-${item.doseNumber ?? "x"}-${item.status}`} sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center" }}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{item.vaccineName}</Typography>
                                          <Chip size="small" icon={meta.icon as any} label={meta.label} color={meta.tone} variant="outlined" sx={compactChipSx} />
                                        </Box>
                                      );
                                    })}
                                  </Stack>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">No due vaccines in this stage.</Typography>
                                )}
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <Card variant="outlined" sx={{ borderRadius: 2, height: "100%" }}>
                            <CardContent sx={compactCardContentSx}>
                              <Stack spacing={0.75}>
                                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase" }}>
                                  Upcoming
                                </Typography>
                                {vaccinationRoadmapCurrentStageUpcomingItems.length ? (
                                  <Stack spacing={0.5}>
                                    {vaccinationRoadmapCurrentStageUpcomingItems.slice(0, 4).map((item) => {
                                      const meta = statusMeta(item.status);
                                      return (
                                        <Box key={`${item.vaccineId}-${item.doseNumber ?? "x"}-${item.status}`} sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center" }}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{item.vaccineName}</Typography>
                                          <Chip size="small" icon={meta.icon as any} label={meta.label} color={meta.tone} variant="outlined" sx={compactChipSx} />
                                        </Box>
                                      );
                                    })}
                                  </Stack>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">No upcoming vaccines in this stage.</Typography>
                                )}
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      </Grid>
                    </Stack>
                  </CardContent>
                </Card>

                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button size="small" variant="outlined" onClick={expandAllRoadmapStages}>
                      Expand all
                    </Button>
                    <Button size="small" variant="outlined" onClick={collapseAllRoadmapStages}>
                      Collapse all
                    </Button>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <FormControlLabel
                      control={<Switch checked={showCompletedTimelineItems} onChange={(_, checked) => setShowCompletedTimelineItems(checked)} />}
                      label="Show completed"
                    />
                    <FormControlLabel
                      control={<Switch checked={showOptionalRiskBasedTimelineItems} onChange={(_, checked) => setShowOptionalRiskBasedTimelineItems(checked)} />}
                      label="Show optional/risk-based"
                    />
                    <FormControlLabel
                      control={<Switch checked={showNotApplicableTimelineItems} onChange={(_, checked) => setShowNotApplicableTimelineItems(checked)} />}
                      label="Show not applicable"
                    />
                    {selectedPatientAgeDays != null && selectedPatientAgeDays >= ADULT_VISIBLE_MIN_DAYS ? (
                      <FormControlLabel
                        control={<Switch checked={showChildhoodSchedule} onChange={(_, checked) => setShowChildhoodSchedule(checked)} />}
                        label="Show childhood schedule"
                      />
                    ) : null}
                  </Stack>
                </Box>

                {vaccinationRoadmapVisibleStages.length ? (
                  <Stack spacing={1}>
                    {vaccinationRoadmapVisibleStages.map((stage) => {
                      const expanded = Boolean(roadmapExpandedStageIds[stage.key]);
                      const stageGroups = stage.milestoneGroups.length
                        ? stage.milestoneGroups
                        : stage.items.length
                          ? [{ label: "Milestone", items: stage.items }]
                          : [];
                      return (
                        <Accordion
                          key={stage.key}
                          expanded={expanded}
                          onChange={() => toggleRoadmapStage(stage.key)}
                          disableGutters
                          sx={compactAccordionSx}
                        >
                          <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} sx={{ px: 1.25, py: 0.25, minHeight: 48 }}>
                            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", width: "100%", alignItems: "center" }}>
                              <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: "1 1 auto" }}>
                                <Box sx={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: "50%", bgcolor: stage.isCurrent ? "primary.main" : "action.hover", color: stage.isCurrent ? "primary.contrastText" : "text.secondary", flex: "0 0 auto" }}>
                                  {stage.icon}
                                </Box>
                                <Box sx={{ minWidth: 0 }}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                      {stage.label}
                                    </Typography>
                                    {stage.isCurrent ? <Chip size="small" label="Current stage" color="primary" variant="outlined" sx={compactChipSx} /> : null}
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary">
                                    {stage.helper}
                                  </Typography>
                                </Box>
                              </Stack>
                              <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                                <Chip size="small" label={`${stage.items.length} scheduled`} variant="outlined" sx={compactChipSx} />
                                {stage.overdueCount ? <Chip size="small" label={`${stage.overdueCount} overdue`} color="error" variant="outlined" sx={compactChipSx} /> : null}
                                {stage.dueCount ? <Chip size="small" label={`${stage.dueCount} due`} color="warning" variant="outlined" sx={compactChipSx} /> : null}
                                {stage.upcomingCount ? <Chip size="small" label={`${stage.upcomingCount} upcoming`} color="info" variant="outlined" sx={compactChipSx} /> : null}
                                {stage.completedCount ? <Chip size="small" label={`${stage.completedCount} completed`} color="success" variant="outlined" sx={compactChipSx} /> : null}
                                {stage.optionalCount ? <Chip size="small" label={`${stage.optionalCount} optional`} color="secondary" variant="outlined" sx={compactChipSx} /> : null}
                                {stage.notApplicableCount ? <Chip size="small" label={`${stage.notApplicableCount} n/a`} variant="outlined" sx={compactChipSx} /> : null}
                              </Stack>
                            </Box>
                          </AccordionSummary>
                          <AccordionDetails sx={{ pt: 0, pb: 1.25 }}>
                            {stageGroups.length ? (
                              <Stack spacing={1}>
                                {stageGroups.map((group) => (
                                  <Box key={`${stage.key}-${group.label}`} sx={{ pt: 0.5 }}>
                                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap", mb: 0.5 }}>
                                      <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                                        <Chip size="small" label={group.label} variant="outlined" sx={compactChipSx} />
                                        <Chip size="small" label={`${group.items.length}`} variant="outlined" sx={compactChipSx} />
                                      </Stack>
                                    </Box>
                                    <Stack spacing={0.75}>
                                      {group.items.map((item) => (
                                        <VaccinationTimelineItemCard
                                          key={`${item.vaccineId}-${item.doseNumber ?? "x"}-${item.status}-${item.source}`}
                                          item={item}
                                          onSelectVaccine={handleRoadmapSelectItem}
                                          onRecordVaccination={handleRoadmapRecordItem}
                                          onViewHistory={openVaccinationHistoryWorkspace}
                                        />
                                      ))}
                                    </Stack>
                                  </Box>
                                ))}
                              </Stack>
                            ) : (
                              <Typography variant="body2" color="text.secondary">
                                0 scheduled
                              </Typography>
                            )}
                          </AccordionDetails>
                        </Accordion>
                      );
                    })}
                  </Stack>
                ) : (
                  <CompactEmptyState
                    title="No age-based vaccination roadmap is available yet."
                    subtitle="Load a patient with vaccination recommendations or recorded history to populate the timeline."
                  />
                )}
              </Stack>
            ) : (
              <CompactEmptyState
                title="Load a patient to view the vaccination roadmap."
                subtitle="The timeline and immunization passport are calculated from the selected patient."
              />
            )}
          </Stack>
        </CardContent>
      </Card>

      <Box ref={scheduleSectionRef}>
        <Accordion
          expanded={scheduleCalendarExpanded}
          onChange={(_, expanded) => setScheduleCalendarExpanded(expanded)}
          disableGutters
          sx={compactAccordionSx}
        >
          <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} sx={{ px: 1.25, py: 0.25, minHeight: 56 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", width: "100%", alignItems: "center" }}>
              <Box sx={{ minWidth: 0, flex: "1 1 auto" }}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Vaccination Schedule Calendar
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Reference schedule from Vaccine Master by age milestone and schedule type.
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block">
                  Reference schedule only. Patient timeline, billing, and recording remain unchanged.
                </Typography>
              </Box>
              <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                <Chip size="small" label={formatScheduleType(scheduleCalendarScheduleType)} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`${vaccinationScheduleCalendarSummary.totalConfigured} configured`} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`${vaccinationScheduleCalendarSummary.milestoneCount} milestones`} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`${vaccinationScheduleCalendarSummary.activeCount} active`} color="success" variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`${vaccinationScheduleCalendarSummary.inactiveCount} inactive`} color="default" variant="outlined" sx={compactChipSx} />
              </Stack>
            </Box>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0, pb: 1.25 }}>
            <Stack spacing={1.25}>
              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "baseline" }}>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Reference schedule sourced from vaccine master data. Patient history and billing are not used here.
                  </Typography>
                </Box>
                {canManageMaster ? (
                  <Button size="small" variant="outlined" onClick={openVaccinationMasterWorkspace}>
                    Open Vaccine Master
                  </Button>
                ) : null}
              </Box>

              <Grid container spacing={1}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="schedule-calendar-schedule-type-label">Schedule type</InputLabel>
                    <Select
                      labelId="schedule-calendar-schedule-type-label"
                      label="Schedule type"
                      value={scheduleCalendarScheduleType}
                      onChange={(e) => setScheduleCalendarScheduleType(e.target.value as VaccinationScheduleTypeFilter)}
                    >
                      {VACCINATION_SCHEDULE_TYPES.map((type) => (
                        <MenuItem key={type} value={type}>{formatScheduleType(type)}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="schedule-calendar-age-group-label">Age group</InputLabel>
                    <Select
                      labelId="schedule-calendar-age-group-label"
                      label="Age group"
                      value={scheduleCalendarAgeGroup}
                      onChange={(e) => setScheduleCalendarAgeGroup(String(e.target.value))}
                    >
                      <MenuItem value="">All age groups</MenuItem>
                      {scheduleCalendarFilterOptions.ageGroups.map((value) => (
                        <MenuItem key={value} value={value}>{value}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="schedule-calendar-route-label">Route</InputLabel>
                    <Select
                      labelId="schedule-calendar-route-label"
                      label="Route"
                      value={scheduleCalendarRoute}
                      onChange={(e) => setScheduleCalendarRoute(String(e.target.value))}
                    >
                      <MenuItem value="">All routes</MenuItem>
                      {scheduleCalendarFilterOptions.routes.map((value) => (
                        <MenuItem key={value} value={value}>{value}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="schedule-calendar-group-label">Vaccine group</InputLabel>
                    <Select
                      labelId="schedule-calendar-group-label"
                      label="Vaccine group"
                      value={scheduleCalendarVaccineGroup}
                      onChange={(e) => setScheduleCalendarVaccineGroup(String(e.target.value))}
                    >
                      <MenuItem value="">All groups</MenuItem>
                      {scheduleCalendarFilterOptions.vaccineGroups.map((value) => (
                        <MenuItem key={value} value={value}>{value}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="schedule-calendar-policy-label">Policy</InputLabel>
                    <Select
                      labelId="schedule-calendar-policy-label"
                      label="Policy"
                      value={scheduleCalendarPolicy}
                      onChange={(e) => setScheduleCalendarPolicy(e.target.value as VaccinationSchedulePolicyFilter)}
                    >
                      <MenuItem value="ALL">All</MenuItem>
                      <MenuItem value="MANDATORY">Mandatory</MenuItem>
                      <MenuItem value="OPTIONAL">Optional</MenuItem>
                      <MenuItem value="RISK_BASED">Risk-based</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControlLabel
                    control={<Switch checked={scheduleCalendarActiveOnly} onChange={(_, checked) => setScheduleCalendarActiveOnly(checked)} />}
                    label="Active only"
                  />
                </Grid>
              </Grid>

              <Alert severity="info" variant="outlined">
                Schedule comparison will be available in a future release.
              </Alert>

              {!vaccinationScheduleCalendar.filtered.length ? (
                <CompactEmptyState
                  title="No vaccines configured for this schedule."
                  subtitle="Clinic Admin can import Vaccine Master records."
                />
              ) : (
                <Stack spacing={1}>
                  {vaccinationScheduleCalendar.groups.map((group) => {
                    const hasItems = group.items.length > 0;
                    return (
                      <Accordion key={group.label} defaultExpanded={hasItems} disableGutters sx={compactAccordionSx}>
                        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} sx={{ px: 1.25, py: 0.25, minHeight: 48 }}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", width: "100%", alignItems: "center" }}>
                            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: "1 1 auto" }}>
                              <Box sx={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: "50%", bgcolor: hasItems ? "primary.main" : "action.hover", color: hasItems ? "primary.contrastText" : "text.secondary", flex: "0 0 auto" }}>
                                {group.label === "Birth" ? <BabyChangingStationRoundedIcon fontSize="small" /> : group.label === "Adult" ? <PersonRoundedIcon fontSize="small" /> : group.label === "Senior" ? <ElderlyRoundedIcon fontSize="small" /> : <ChildCareRoundedIcon fontSize="small" />}
                              </Box>
                              <Box sx={{ minWidth: 0 }}>
                                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{group.label}</Typography>
                                <Typography variant="caption" color="text.secondary">Schedule milestone</Typography>
                              </Box>
                            </Stack>
                            <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                              <Chip size="small" label={hasItems ? `${group.items.length} vaccine${group.items.length === 1 ? "" : "s"}` : "0 scheduled"} variant="outlined" sx={compactChipSx} />
                            </Stack>
                          </Box>
                        </AccordionSummary>
                        <AccordionDetails sx={{ pt: 0, pb: 1.25 }}>
                          {hasItems ? (
                            <Stack spacing={0.75}>
                              {group.items.map((vaccine) => (
                                <VaccinationScheduleItemCard key={vaccine.id} vaccine={vaccine} onOpenDetails={(next) => setScheduleDetailVaccine(next)} />
                              ))}
                            </Stack>
                          ) : (
                            <Typography variant="body2" color="text.secondary">0 scheduled</Typography>
                          )}
                        </AccordionDetails>
                      </Accordion>
                    );
                  })}
                </Stack>
              )}
            </Stack>
          </AccordionDetails>
        </Accordion>
      </Box>

      <VaccinationScheduleDetailDrawer
        vaccine={scheduleDetailVaccine}
        open={Boolean(scheduleDetailVaccine)}
        onClose={() => setScheduleDetailVaccine(null)}
      />

      <Dialog open={passportDialogOpen} onClose={() => { setPassportDialogOpen(false); setPassportAutoPrint(false); }} maxWidth="md" fullWidth>
        <GlobalStyles
          styles={{
            "@media print": {
              "body *": { visibility: "hidden" },
              "#vaccination-passport-print, #vaccination-passport-print *": { visibility: "visible" },
              "#vaccination-passport-print": {
                position: "absolute",
                left: 0,
                top: 0,
                width: "100%",
              },
            },
          }}
        />
        <DialogTitle>Immunization Passport</DialogTitle>
        <DialogContent dividers>
          {immunizationPassportData.patient ? (
            <Box id="vaccination-passport-print" sx={{ bgcolor: "background.paper", p: 1 }}>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {immunizationPassportData.patient.firstName} {immunizationPassportData.patient.lastName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {immunizationPassportData.patient.patientNumber} • {immunizationPassportData.patient.gender} • {immunizationPassportData.patient.ageYears != null ? `${immunizationPassportData.patient.ageYears} years` : "Age unavailable"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Mobile: {immunizationPassportData.patient.mobile || "-"} • Allergies: {immunizationPassportData.patient.allergies || "None recorded"}
                  </Typography>
                </Box>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <CompactStatCard label="Completed / Applicable" value={`${vaccinationTimelineProgress.completed}/${vaccinationTimelineProgress.applicable || 0}`} tone="success" />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <CompactStatCard label="Completion %" value={`${vaccinationTimelineProgress.completionPercent}%`} tone="info" />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <CompactStatCard label="Next due vaccine" value={vaccinationTimelineProgress.nextDue?.vaccineName || "None"} tone={vaccinationTimelineProgress.nextDue ? recommendationStatusColor(vaccinationTimelineProgress.nextDue.status) : "default"} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <CompactStatCard label="Overdue count" value={vaccinationTimelineProgress.overdueCount} tone={vaccinationTimelineProgress.overdueCount > 0 ? "error" : "success"} />
                  </Grid>
                </Grid>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Vaccination history</Typography>
                  {patientHistoryRows.length ? (
                    <Stack spacing={0.75}>
                      {patientHistoryRows.slice(0, 12).map((row) => (
                        <Box key={row.id} sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", p: 1, border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.vaccineName}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {row.givenDate} • {row.source || "INTERNAL"} • {row.administeredByUserName || "-"}
                            </Typography>
                          </Box>
                          <Chip size="small" label={row.billStatus ? billStatusLabel(row.billStatus as Bill["status"]) : "Recorded"} color={row.source === "EXTERNAL" ? "secondary" : "default"} variant="outlined" />
                        </Box>
                      ))}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">No vaccination history has been recorded yet.</Typography>
                  )}
                </Stack>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Due and upcoming vaccines</Typography>
                  {vaccinationTimelineSections.length ? (
                    <Stack spacing={0.75}>
                      {vaccinationTimelineSections
                        .flatMap((section) => section.items.filter((item) => item.status === "DUE" || item.status === "OVERDUE" || item.status === "UPCOMING"))
                        .slice(0, 12)
                        .map((item) => (
                          <Box key={`${item.vaccineId}-${item.doseNumber ?? "x"}-${item.status}`} sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", p: 1, border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                            <Box>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.vaccineName}</Typography>
                              <Typography variant="caption" color="text.secondary">{item.reasonText}</Typography>
                            </Box>
                            <Chip size="small" label={recommendationStatusLabel(item.status)} color={item.milestoneTone as any} variant="outlined" />
                          </Box>
                        ))}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">No due or upcoming vaccines are currently identified.</Typography>
                  )}
                </Stack>
              </Stack>
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap" }}>
          <Button onClick={() => { setPassportDialogOpen(false); setPassportAutoPrint(false); }}>Close</Button>
          <Button variant="outlined" onClick={() => window.print()} disabled={!immunizationPassportData.patient}>
            Print
          </Button>
          <Button variant="outlined" onClick={() => window.print()} disabled={!immunizationPassportData.patient}>
            Download PDF
          </Button>
        </DialogActions>
      </Dialog>

      <Menu anchorEl={historyActionAnchorEl} open={Boolean(historyActionAnchorEl && historyActionRow)} onClose={closeHistoryRowActions}>
        {historyActionRow ? (
          [
            <MenuItem key="open-patient" onClick={() => { openPatient(historyActionRow.patientId); closeHistoryRowActions(); }}>
              Open Patient
            </MenuItem>,
            <MenuItem key="view-vaccination" onClick={() => { toggleHistoryRowExpanded(historyActionRow.id); closeHistoryRowActions(); }}>
              View Vaccination
            </MenuItem>,
            <MenuItem key="record-aefi" onClick={() => { openAefiDialog(historyActionRow, historyActionRow.adverseEventStatus && historyActionRow.adverseEventStatus !== "NONE" ? "form" : "prompt"); closeHistoryRowActions(); }}>
              Record / View Adverse Event
            </MenuItem>,
            canManageVaccinationHistory ? (
              <MenuItem key="edit" onClick={() => { openEditVaccinationRow(historyActionRow); closeHistoryRowActions(); }}>
                Edit
              </MenuItem>
            ) : null,
            vaccinationBillingStatus(historyActionRow).isUnbilled ? [
              <MenuItem key="add-to-bill" onClick={() => { void billVaccinationRow(historyActionRow, false); closeHistoryRowActions(); }}>
                Add to Bill
              </MenuItem>,
              <MenuItem key="create-bill" onClick={() => { void billVaccinationRow(historyActionRow, true); closeHistoryRowActions(); }}>
                Create Bill
              </MenuItem>,
            ] : vaccinationBillingStatus(historyActionRow).hasPaymentDue ? [
              canCollectVaccinationPayment ? (
                <MenuItem key="collect-payment" onClick={() => { void collectPaymentForRow(historyActionRow); closeHistoryRowActions(); }}>
                  Collect Payment
                </MenuItem>
              ) : null,
              <MenuItem key="open-bill" onClick={() => { navigate(`/billing?billId=${historyActionRow.billId}`); closeHistoryRowActions(); }}>
                Open Bill
              </MenuItem>,
            ] : vaccinationBillingStatus(historyActionRow).isPaid ? [
              <MenuItem key="view-receipt" onClick={() => { void openVaccinationReceiptForRow(historyActionRow); closeHistoryRowActions(); }}>
                View Receipt
              </MenuItem>,
              <MenuItem key="print-receipt" onClick={() => { void openVaccinationReceiptForRow(historyActionRow, true); closeHistoryRowActions(); }}>
                Print Receipt
              </MenuItem>,
              <MenuItem key="download-receipt" onClick={() => { void downloadVaccinationReceiptForRow(historyActionRow); closeHistoryRowActions(); }}>
                Download Receipt PDF
              </MenuItem>,
              canSendVaccinationReceipt ? (
                <MenuItem key="email-receipt" onClick={() => { void sendVaccinationReceiptForRow(historyActionRow, "EMAIL"); closeHistoryRowActions(); }}>
                  Email Receipt
                </MenuItem>
              ) : null,
              canSendVaccinationReceipt ? (
                <MenuItem key="whatsapp-receipt" onClick={() => { void sendVaccinationReceiptForRow(historyActionRow, "WHATSAPP"); closeHistoryRowActions(); }}>
                  WhatsApp Receipt
                </MenuItem>
              ) : null,
            ] : [
              vaccinationBillingStatus(historyActionRow).hasReceipt ? (
                <MenuItem key="view-receipt" onClick={() => { void openVaccinationReceiptForRow(historyActionRow); closeHistoryRowActions(); }}>
                  View Receipt
                </MenuItem>
              ) : null,
            ],
            historyActionRow.source === "EXTERNAL" && String(historyActionRow.verifiedStatus || "").toUpperCase() !== "VERIFIED" && canVerifyExternalVaccination ? (
              <MenuItem key="mark-external-verified" onClick={() => { void verifyExternalVaccinationRow(historyActionRow); closeHistoryRowActions(); }}>
                Mark External Verified
              </MenuItem>
            ) : null,
            canManageVaccinationHistory ? (
              <MenuItem key="view-bill" onClick={() => { if (historyActionRow.billId) { navigate(`/billing?billId=${historyActionRow.billId}`); } closeHistoryRowActions(); }}>
                View Bill
              </MenuItem>
            ) : null,
          ].flat()
        ) : null}
      </Menu>

      <Dialog open={aefiDialogOpen && Boolean(aefiDialogVaccination)} onClose={() => { setAefiDialogOpen(false); setAefiDialogVaccination(null); }} fullWidth maxWidth="md">
        <DialogTitle>Adverse Event Following Immunization</DialogTitle>
        <DialogContent dividers>
          {aefiDialogVaccination ? (
            <Stack spacing={2} sx={{ pt: 0.5 }}>
              <Alert severity={aefiDialogMode === "prompt" ? "info" : "warning"}>
                {aefiDialogMode === "prompt"
                  ? "You can record an adverse event now or mark that no adverse event was observed."
                  : "Serious adverse event requires clinical review and follow-up."}
              </Alert>
              {aefiDialogMode === "prompt" ? (
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Button variant="outlined" onClick={() => setAefiDialogMode("form")}>Record Adverse Event</Button>
                  <Button variant="contained" onClick={() => void saveAefi(true)}>No adverse event observed</Button>
                </Stack>
              ) : null}
              {aefiDialogMode === "form" ? (
                <Grid container spacing={1.5}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <FormControl fullWidth>
                      <InputLabel id="aefi-status-label">AEFI status</InputLabel>
                      <Select
                        labelId="aefi-status-label"
                        label="AEFI status"
                        value={aefiForm.adverseEventStatus}
                        onChange={(e) => setAefiForm((current) => ({ ...current, adverseEventStatus: e.target.value }))}
                      >
                        {["OBSERVED", "REPORTED", "RESOLVED", "NONE"].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField fullWidth label="Event date/time" type="datetime-local" InputLabelProps={{ shrink: true }} value={aefiForm.eventDateTime.slice(0, 16)} onChange={(e) => setAefiForm((current) => ({ ...current, eventDateTime: `${e.target.value}:00.000Z` }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField fullWidth label="Onset after vaccination" value={aefiForm.onsetTimeAfterVaccination} onChange={(e) => setAefiForm((current) => ({ ...current, onsetTimeAfterVaccination: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <FormControl fullWidth>
                      <InputLabel id="aefi-severity-label">Severity</InputLabel>
                      <Select
                        labelId="aefi-severity-label"
                        label="Severity"
                        value={aefiForm.severity}
                        onChange={(e) => setAefiForm((current) => ({ ...current, severity: e.target.value }))}
                      >
                        {["MILD", "MODERATE", "SEVERE", "SERIOUS"].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField
                      fullWidth
                      select
                      SelectProps={{
                        multiple: true,
                        renderValue: (selected) => (selected as string[]).join(", "),
                      }}
                      label="Symptoms"
                      value={aefiForm.symptoms}
                      onChange={(e) => setAefiForm((current) => ({ ...current, symptoms: typeof e.target.value === "string" ? e.target.value.split(",") : (e.target.value as string[]) }))}
                    >
                      {aefiSymptomsOptions.map((symptom) => (
                        <MenuItem key={symptom} value={symptom}>
                          <Checkbox checked={aefiForm.symptoms.indexOf(symptom) > -1} />
                          {symptom}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField fullWidth label="Other symptoms" value={aefiForm.otherSymptoms} onChange={(e) => setAefiForm((current) => ({ ...current, otherSymptoms: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField fullWidth label="Action taken" value={aefiForm.actionTaken} onChange={(e) => setAefiForm((current) => ({ ...current, actionTaken: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField fullWidth label="Outcome" value={aefiForm.outcome} onChange={(e) => setAefiForm((current) => ({ ...current, outcome: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField fullWidth multiline minRows={3} label="Treatment notes" value={aefiForm.treatmentNotes} onChange={(e) => setAefiForm((current) => ({ ...current, treatmentNotes: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField fullWidth multiline minRows={2} label="Notes" value={aefiForm.notes} onChange={(e) => setAefiForm((current) => ({ ...current, notes: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <FormControlLabel control={<Switch checked={aefiForm.followUpRequired} onChange={(e) => setAefiForm((current) => ({ ...current, followUpRequired: e.target.checked }))} />} label="Follow-up required" />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField fullWidth label="Follow-up date" type="date" InputLabelProps={{ shrink: true }} value={aefiForm.followUpDate} onChange={(e) => setAefiForm((current) => ({ ...current, followUpDate: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <FormControlLabel control={<Switch checked={aefiForm.reportedToAuthority} onChange={(e) => setAefiForm((current) => ({ ...current, reportedToAuthority: e.target.checked }))} />} label="Reported to authority" />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField fullWidth label="Report reference number" value={aefiForm.reportReferenceNumber} onChange={(e) => setAefiForm((current) => ({ ...current, reportReferenceNumber: e.target.value }))} />
                  </Grid>
                </Grid>
              ) : null}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap" }}>
          <Button onClick={() => { setAefiDialogOpen(false); setAefiDialogVaccination(null); }}>Close</Button>
          {aefiDialogMode === "form" ? <Button variant="contained" onClick={() => void saveAefi(false)}>Save AEFI</Button> : null}
        </DialogActions>
      </Dialog>

      <Dialog open={importPreviewOpen} onClose={() => setImportPreviewOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Preview vaccine CSV import</DialogTitle>
        <DialogContent dividers>
          {importPreview ? (
            <Stack spacing={2}>
              {importPreview.headerWarnings.length > 0 ? (
                <Alert severity="warning">{importPreview.headerWarnings.join(" ")}</Alert>
              ) : null}
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${importPreview.summary.totalRows} rows`} />
                <Chip size="small" color="success" variant="outlined" label={`${importPreview.summary.validRows} valid`} />
                <Chip size="small" color={importPreview.summary.invalidRows ? "error" : "default"} variant="outlined" label={`${importPreview.summary.invalidRows} invalid`} />
              </Stack>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Row</TableCell>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Gap days</TableCell>
                    <TableCell>Default price</TableCell>
                    <TableCell>Active</TableCell>
                    <TableCell>Validation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {importPreview.rows.map((row) => (
                    <TableRow key={row.rowNumber} hover={!row.errors.length}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.vaccineName || "-"}</TableCell>
                      <TableCell>{row.gapDays || "-"}</TableCell>
                      <TableCell>{row.defaultPrice || "-"}</TableCell>
                      <TableCell>{row.active || "-"}</TableCell>
                      <TableCell>
                        {row.errors.length > 0 ? (
                          <Stack spacing={0.35}>
                            {row.errors.map((message) => (
                              <Typography key={message} variant="caption" color="error">
                                {message}
                              </Typography>
                            ))}
                          </Stack>
                        ) : (
                          <Chip size="small" color="success" label="Valid" />
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeImportDialogs}>Cancel</Button>
          <Button variant="contained" onClick={() => void confirmImport()} disabled={saving || !importPreview?.summary.validRows}>
            {saving ? "Importing..." : "Import valid rows"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importResultOpen} onClose={() => setImportResultOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Vaccine CSV import result</DialogTitle>
        <DialogContent dividers>
          {importResult ? (
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${importResult.totalRows} rows`} />
                <Chip size="small" color="success" variant="outlined" label={`${importResult.createdCount} created`} />
                <Chip size="small" color={importResult.failedCount ? "error" : "default"} variant="outlined" label={`${importResult.failedCount} failed`} />
              </Stack>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Row</TableCell>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Message</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {importResult.rows.map((row) => (
                    <TableRow key={`${row.rowNumber}-${row.vaccineName}`}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.vaccineName || "-"}</TableCell>
                      <TableCell>{row.status}</TableCell>
                      <TableCell>{row.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeImportDialogs}>Close</Button>
        </DialogActions>
      </Dialog>

      {vaccinationPaymentDialog ? (
        <ConsultationFeeDialog
          open
          title="Collect vaccination payment"
          reasonLabel="Vaccination billing"
          appointmentLabel={`Bill: ${vaccinationPaymentDialog.bill.billNumber}`}
          doctorLabel={`Vaccine: ${vaccinationPaymentDialog.vaccineName}`}
          patientLabel={`Patient: ${vaccinationPaymentDialog.patientLabel}`}
          feeLabel={`Amount due: ₹${billEffectiveDueAmount(vaccinationPaymentDialog.bill).toFixed(2)}`}
          submitLabel="Collect Payment"
          onClose={() => setVaccinationPaymentDialog(null)}
          onSubmit={submitVaccinationPayment}
        />
      ) : null}

      <Dialog open={Boolean(vaccinationReceiptPanel)} onClose={() => setVaccinationReceiptPanel(null)} fullWidth maxWidth="sm">
        <DialogTitle>Payment successful</DialogTitle>
        <DialogContent>
          {vaccinationReceiptPanel ? (
            <Stack spacing={1.25} sx={{ pt: 0.5 }}>
              <Alert severity="success">Receipt ready for {vaccinationReceiptPanel.patientName}.</Alert>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Receipt number</Typography><Typography variant="body2">{vaccinationReceiptPanel.receipt.receiptNumber}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Bill number</Typography><Typography variant="body2">{vaccinationReceiptPanel.bill.billNumber}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Vaccine</Typography><Typography variant="body2">{vaccinationReceiptPanel.vaccineName}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Amount</Typography><Typography variant="body2">₹{vaccinationReceiptPanel.payment.amount.toFixed(2)}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Payment mode</Typography><Typography variant="body2">{vaccinationReceiptPanel.payment.paymentMode}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Collected by</Typography><Typography variant="body2">{staffDisplayName(vaccinationReceiptPanel.payment.receivedByLabel, vaccinationReceiptPanel.payment.receivedBy)}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Timestamp</Typography><Typography variant="body2">{vaccinationReceiptPanel.payment.paymentDateTime || vaccinationReceiptPanel.payment.paymentDate || vaccinationReceiptPanel.payment.receiptDate || vaccinationReceiptPanel.payment.createdAt}</Typography></Grid>
              </Grid>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap" }}>
          <Button onClick={() => setVaccinationReceiptPanel(null)}>Close</Button>
          <Button variant="outlined" onClick={() => { if (vaccinationReceiptPanel) openVaccinationReceiptPreview(false); setVaccinationReceiptPanel(null); }}>View Receipt</Button>
          <Button variant="outlined" onClick={() => { if (vaccinationReceiptPanel) openVaccinationReceiptPreview(true); setVaccinationReceiptPanel(null); }}>Print Receipt</Button>
          <Button variant="outlined" onClick={() => { void handleVaccinationReceiptDownload(); }}>Download Receipt PDF</Button>
          <Button variant="outlined" onClick={() => { void handleVaccinationReceiptSend("EMAIL"); }}>Email Receipt</Button>
          <Button variant="outlined" onClick={() => { void handleVaccinationReceiptSend("WHATSAPP"); }}>WhatsApp Receipt</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(documentPanel)} onClose={() => setDocumentPanel(null)} fullWidth maxWidth="sm">
        <DialogTitle>{documentPanel?.kind === "passport" ? "Passport ready" : "Certificate ready"}</DialogTitle>
        <DialogContent>
          {documentPanel ? (
            <Stack spacing={1.25} sx={{ pt: 0.5 }}>
              <Alert severity="success">{documentPanel.document.title} has been generated.</Alert>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Document number</Typography><Typography variant="body2">{documentPanel.document.documentNumber}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Generated by</Typography><Typography variant="body2">{documentPanel.document.generatedBy}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Generated at</Typography><Typography variant="body2">{documentPanel.document.generatedAt}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Passport / certificate</Typography><Typography variant="body2">{documentPanel.kind === "passport" ? "Passport" : documentPanel.certificateType || "Certificate"}</Typography></Grid>
              </Grid>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap" }}>
          <Button onClick={() => setDocumentPanel(null)}>Close</Button>
          <Button variant="outlined" onClick={() => { if (documentPanel && selectedPatientSummary?.id) void openVaccinationDocument(documentPanel.document, selectedPatientSummary.id, "VIEW"); }}>
            View
          </Button>
          <Button variant="outlined" onClick={() => { if (documentPanel && selectedPatientSummary?.id) void openVaccinationDocument(documentPanel.document, selectedPatientSummary.id, "PRINT"); }}>
            Print
          </Button>
          <Button variant="outlined" onClick={() => { if (documentPanel && selectedPatientSummary?.id) void openVaccinationDocument(documentPanel.document, selectedPatientSummary.id, "DOWNLOAD"); }}>
            Download PDF
          </Button>
          <Button variant="outlined" onClick={() => { if (documentPanel && selectedPatientSummary?.id) void sendVaccinationGeneratedDocument(documentPanel.document, selectedPatientSummary.id, "EMAIL"); }}>
            Email
          </Button>
          <Button variant="outlined" onClick={() => { if (documentPanel && selectedPatientSummary?.id) void sendVaccinationGeneratedDocument(documentPanel.document, selectedPatientSummary.id, "WHATSAPP"); }}>
            WhatsApp
          </Button>
        </DialogActions>
      </Dialog>

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

      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={(event) => void handleImportFile(event)} />
    </Stack>
  );
}
