import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Drawer,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  LinearProgress,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
  Tooltip,
} from "@mui/material";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import PersonRoundedIcon from "@mui/icons-material/PersonRounded";
import BadgeRoundedIcon from "@mui/icons-material/BadgeRounded";
import TimelineRoundedIcon from "@mui/icons-material/TimelineRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import ChatBubbleOutlineRoundedIcon from "@mui/icons-material/ChatBubbleOutlineRounded";
import HealingRoundedIcon from "@mui/icons-material/HealingRounded";
import PsychologyRoundedIcon from "@mui/icons-material/PsychologyRounded";
import FavoriteRoundedIcon from "@mui/icons-material/FavoriteRounded";
import CallRoundedIcon from "@mui/icons-material/CallRounded";
import EventRoundedIcon from "@mui/icons-material/EventRounded";
import TipsAndUpdatesRoundedIcon from "@mui/icons-material/TipsAndUpdatesRounded";
import HistoryEduRoundedIcon from "@mui/icons-material/HistoryEduRounded";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import TrendingUpRoundedIcon from "@mui/icons-material/TrendingUpRounded";
import InsightsRoundedIcon from "@mui/icons-material/InsightsRounded";
import BiotechRoundedIcon from "@mui/icons-material/BiotechRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import HealthAndSafetyRoundedIcon from "@mui/icons-material/HealthAndSafetyRounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import MonitorHeartRoundedIcon from "@mui/icons-material/MonitorHeartRounded";
import LightbulbRoundedIcon from "@mui/icons-material/LightbulbRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import ScheduleRoundedIcon from "@mui/icons-material/ScheduleRounded";
import PlaylistAddCheckRoundedIcon from "@mui/icons-material/PlaylistAddCheckRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import CompareArrowsRoundedIcon from "@mui/icons-material/CompareArrowsRounded";

import { consultationSchema, firstZodError, labConsultationOrderCreateSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { ClinicalAiDraftCard, type ClinicalAiDraftStatus } from "../../components/clinical/ClinicalAiDraftCard";
import { documentBusinessStatusLabel, documentTypeLabel, isPublishedLabDocument } from "../../components/clinical/documentTypeOptions";
import { AppointmentTokenChip, WorkflowStatusBadge } from "../../components/workflow/WorkflowUx";
import {
  aiClinicalSummary,
  aiConsultationAsk,
  aiPatientInstructions,
  aiStructureConsultationNotes,
  aiSuggestDiagnosis,
  aiSuggestPrescriptionTemplate,
  cancelConsultation,
  completeConsultation,
  createPrescriptionCorrection,
  cancelPrescription,
  createConsultationLabOrder,
  createPrescription,
  getAiStatus,
  getAppointment,
  finalizePrescription,
  getConsultation,
  getConsultationAiSummary,
  getClinicalDocument,
  getLabOrders,
  getLabTests,
  getConsultationPrescription,
  getMedicationSafetyEvaluation,
  getMedicationSafetyReview,
  runMedicationSafetyCheck,
  submitMedicationSafetyReview,
  getClinicalContext,
  getMedicines,
  getPatient,
  getPatientDocumentDownloadUrl,
  getPatientDocumentViewUrl,
  getPatientDocuments,
  getPatientPrescriptions,
  getPatientTimeline,
  getPrescriptionPdf,
  getPrescriptionHistory,
  getLabOrderPdf,
  generateConsultationDocument,
  printPrescription,
  previewPrescription,
  uploadPatientDocument,
  saveConsultationAiSummary,
  acceptConsultationSoapAiDraft,
  repairClinicalMemory as repairClinicalMemoryApi,
  reprocessClinicalDocumentExtraction,
  getMedicationSafetyEvaluationForPrescription,
  getMedicationSafetyReviewForPrescription,
  sendPrescription,
  getConsultationSoap,
  startConsultationFromAppointment,
  saveConsultationSoap,
  updateConsultation,
  updatePrescription,
  type AiStatus,
  type AiDraftResponse,
  type Appointment,
  type Consultation,
  type ConsultationAiSummary,
  type ConsultationSoapNote,
  type ConsultationInput,
  type ConsultationSoapInput,
  type LabOrder,
  type LabOrderStatus,
  type LabTest,
  type Medicine,
  type MedicineType,
  type PatientDetail,
  type ClinicalDocument,
  type ClinicalContextResponse,
  type ClinicalReasoningResult,
  type ClinicalDocumentType,
  type PatientTimelineItem,
  type MedicationSafetyEvaluationResult,
  type MedicationSafetyReviewResponse,
  type MedicationSafetyFindingReviewDecision,
  type MedicationSafetyFindingReviewStatus,
  type Prescription,
  type PrescriptionInput,
  type PrescriptionMedicine,
  type PrescriptionTest,
  type Timing,
} from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { ClinicalDocumentViewer } from "../../components/clinical/ClinicalDocumentViewer";
import { ConfirmationDialog } from "../../components/clinical/ConfirmationDialog";
import { PatientIntelligenceCard } from "../../components/clinical/PatientIntelligenceCard";
import { PatientDocumentUploadDialog } from "../../components/clinical/PatientDocumentUploadDialog";
import { useClinicalReasoning } from "../../hooks/useClinicalReasoning";
import { formatRelativeBookingTime } from "../../components/workflow/workflowHelpers";

type ConsultationFormState = {
  chiefComplaints: string;
  symptoms: string;
  diagnosis: string;
  clinicalNotes: string;
  advice: string;
  followUpDate: string;
  bloodPressureSystolic: string;
  bloodPressureDiastolic: string;
  pulseRate: string;
  temperature: string;
  temperatureUnit: "CELSIUS" | "FAHRENHEIT" | "";
  weightKg: string;
  heightCm: string;
  spo2: string;
  respiratoryRate: string;
};

type ConsultationSoapFormState = {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
};

type MedicineRow = PrescriptionMedicine & { localId: string; route?: string | null };
type TestRow = PrescriptionTest & { localId: string };
type AiSuggestionItem = {
  title: string;
  reason: string | null;
  confidence: string | null;
  redFlags: string[];
  recommendedInvestigations: string[];
  followUpSuggestions: string[];
  rawText: string;
};
type AiMedicineSuggestionItem = {
  medicine: string;
  dose: string | null;
  frequency: string | null;
  duration: string | null;
  reason: string | null;
  safetyNote: string | null;
  rawText: string;
};
type AiAssistAction = "ask" | "diagnosis" | "notes" | "prescription" | "instructions" | "summary" | "red_flags" | "drug_safety" | "tests";
type AiAssistEntry = {
  id: string;
  action: AiAssistAction;
  title: string;
  prompt: string | null;
  createdAt: string;
  status: "loading" | "success" | "error";
  response: AiDraftResponse | null;
  error: string | null;
};
type AivaChatRole = "DOCTOR" | "AIVA";
type AivaChatMessage = {
  id: string;
  role: AivaChatRole;
  text: string;
  createdAt: string;
  response: AiDraftResponse | null;
};
type ClinicalAiDraftKind = "chiefComplaint" | "symptoms" | "diagnosis" | "soap" | "advice" | "followUp";
type ClinicalAiDraftState = {
  kind: ClinicalAiDraftKind;
  title: string;
  status: ClinicalAiDraftStatus;
  generatedAt: string | null;
  provider: string | null;
  model: string | null;
  sourceSummary: string | null;
  draftText: string;
  structuredData: Record<string, unknown> | null;
  error: string | null;
  selectedItems: string[];
  selectedItem: string | null;
  traceId: string | null;
};
type ConsultationDocumentationKind = "visitSummary" | "referral" | "certificate";
type ConsultationDocumentPriority = "ROUTINE" | "URGENT" | "EMERGENCY";
type ConsultationDocumentDraftState = {
  kind: ConsultationDocumentationKind;
  title: string;
  status: ClinicalAiDraftStatus;
  generatedAt: string | null;
  sourceSummary: string | null;
  draftText: string;
  error: string | null;
  documentType: ClinicalDocumentType;
  language: "ENGLISH" | "HINDI" | "MARATHI";
  documentId: string | null;
  downloadUrl: string | null;
  notes: string | null;
};
type ConsultationCompletionTone = "success" | "warning" | "default" | "error" | "info";
type ConsultationCompletionItem = {
  label: string;
  stateLabel: string;
  detail: string;
  tone: ConsultationCompletionTone;
  prepared: boolean;
};
type ConsultationCompletionGroup = {
  title: string;
  helper: string;
  summaryLabel: string;
  summaryTone: ConsultationCompletionTone;
  items: ConsultationCompletionItem[];
};
type MedicationSafetyReviewDraftState = {
  findings: Record<string, MedicationSafetyFindingReviewDecision>;
};
type CoverageDisplayState = "Evaluated" | "Partial" | "Unavailable";

function normalizeMedicationSafetyCoverageLabel(
  evaluated: boolean,
  warnings: string[],
  partialHints: string[],
): CoverageDisplayState {
  if (evaluated) {
    return "Evaluated";
  }
  const loweredWarnings = (warnings || []).map((warning) => warning.toLowerCase());
  if (loweredWarnings.some((warning) => partialHints.some((hint) => warning.includes(hint)))) {
    return "Partial";
  }
  return "Unavailable";
}

function normalizeMedicationSafetyReview(
  review: MedicationSafetyReviewResponse | null,
  evaluation: MedicationSafetyEvaluationResult | null,
): MedicationSafetyReviewResponse | null {
  if (!review) {
    return null;
  }
  return review;
}

function formatMedicationSafetyReviewDecisionStatus(value: string | null | undefined) {
  const normalized = String(value || "").trim().toUpperCase();
  if (!normalized) return "Not reviewed";
  if (normalized === "NOT_REVIEWED") return "Not reviewed";
  if (normalized === "WARNINGS_ACKNOWLEDGED") return "Warnings acknowledged";
  if (normalized === "CRITICAL_OVERRIDE_APPROVED") return "Critical override approved";
  if (normalized === "REVIEWED_NO_BLOCKING_FINDINGS") return "Reviewed";
  if (normalized === "FINALIZED") return "Finalized";
  if (normalized === "STALE") return "Stale";
  return String(value).replaceAll("_", " ").toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}
type ClinicalDraftGenerationStep = ClinicalAiDraftKind | "prescriptionSuggestion" | "clinicalReasoning";
type ClinicalDraftStepStatus = "pending" | "generating" | "done" | "failed";
type ClinicalDraftGenerationStepState = {
  status: ClinicalDraftStepStatus;
  error: string | null;
  message: string | null;
};
type ClinicalReasoningSectionKey = "longitudinalContext" | "primaryDiagnosis" | "differentials" | "evidence" | "missingInformation" | "redFlags" | "recommendedTests" | "safetyNotes" | "debug";
type InvestigationIntelligenceStatus = "Already Available" | "Recently Completed" | "Pending" | "Recommended" | "Consider" | "Unknown";
type InvestigationIntelligenceRow = {
  testName: string;
  status: InvestigationIntelligenceStatus;
  evidence: string | null;
  doctorNote: string;
  duplicateRisk: boolean;
  matchedOrderId: string | null;
  matchedReportTitle: string | null;
  observedOn: string | null;
};
type LabOrderAiPreparation = {
  generatedAt: string;
  investigations: string[];
  confidence: string;
  suggestedPriority: string;
  reason: string;
  supportingEvidence: string[];
  duplicateWarnings: string[];
  reasoningId: string | null;
};
type MedicineShortcut = Pick<
  Medicine,
  "medicineName" | "strength" | "defaultDosage" | "defaultFrequency" | "defaultDurationDays" | "defaultTiming" | "defaultInstructions"
> & {
  medicineType: MedicineType | null;
};

type PrescriptionDialogState =
  | { kind: "fastPack"; templateLabel: string; hasContent: boolean; onConfirm: () => void }
  | { kind: "overwrite"; label: string; currentValue: string; allowAppend: boolean; onResolve: (choice: "replace" | "append" | "cancel") => void }
  | { kind: "confirm"; title: string; description: string; confirmLabel: string; onConfirm: () => void; onCancel?: () => void; confirmColor?: "primary" | "secondary" | "error" | "warning" | "info" }
  | null;

type PrescriptionFormState = {
  diagnosisSnapshot: string;
  advice: string;
  followUpDate: string;
  medicines: MedicineRow[];
  recommendedTests: TestRow[];
};

type AutosaveStatus = "idle" | "dirty" | "saving" | "saved" | "failed" | "readonly";
const CONSULTATION_COMPLETION_BLOCKED_MESSAGE = "Finalize the prescription before completing this consultation.";
const VALID_MEDICINE_TYPES: MedicineType[] = ["TABLET", "SYRUP", "INJECTION", "DROP", "OINTMENT", "CAPSULE", "OTHER"];
const VALID_TIMINGS: Timing[] = ["BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "ANYTIME"];
const CONSULTATION_TAB_KEYS = ["consultation", "prescription", "history", "investigations", "lab-orders", "ai-assist"] as const;

class PrescriptionPayloadValidationError extends Error {
  invalidMedicineRowIds: string[];

  constructor(message: string, invalidMedicineRowIds: string[] = []) {
    super(message);
    this.name = "PrescriptionPayloadValidationError";
    this.invalidMedicineRowIds = invalidMedicineRowIds;
  }
}

type RxTemplate = {
  key: string;
  label: string;
  diagnosis: string;
  advice: string;
  followUp: string;
  medicines: Array<{
    medicineName: string;
    medicineType: MedicineType | null;
    strength: string;
    dosage: string;
    frequency: string;
    duration: string;
    timing: Timing | null;
    instructions: string;
  }>;
  tests: string[];
};

const SYMPTOM_CHIPS = ["Fever", "Cough", "Cold", "Headache", "Vomiting", "Body Pain", "Sore Throat", "Acidity", "Weakness"];
const DIAGNOSIS_CHIPS = ["Viral Fever", "Upper Respiratory Infection", "Gastritis", "Hypertension Follow-up", "Diabetes Follow-up"];
const FOLLOWUP_CHIPS = ["3 days", "5 days", "7 days", "15 days", "1 month"];
const TEST_CHIPS = ["CBC", "Urine Routine", "Blood Sugar", "X-Ray", "ECG"];
const ADVICE_CHIPS = ["Hydration", "Rest", "Steam inhalation", "Avoid oily/spicy food", "Monitor warning signs", "Continue current medicines"];
const MEDICINE_ROUTE_OPTIONS = ["", "ORAL", "SYRUP", "IV", "IM", "TOPICAL", "EYE_DROPS", "EAR_DROPS", "NASAL", "INHALATION", "OTHER"] as const;
const MEDICINE_ROUTE_LABELS: Record<(typeof MEDICINE_ROUTE_OPTIONS)[number], string> = {
  "": "Blank",
  ORAL: "Oral",
  SYRUP: "Syrup",
  IV: "IV",
  IM: "IM",
  TOPICAL: "Topical",
  EYE_DROPS: "Eye drops",
  EAR_DROPS: "Ear drops",
  NASAL: "Nasal",
  INHALATION: "Inhalation",
  OTHER: "Other",
};
const AI_DISABLED_MESSAGE = "AI assistance is not enabled or configured for this clinic.";
const AI_PROVIDER_NOT_CONFIGURED_MESSAGE = "AI module is enabled for this clinic, but AI provider is not configured.";
const SOAP_TRACE_ENABLED = import.meta.env.DEV || import.meta.env.VITE_JEEVANAM_AI_SOAP_TRACE_ENABLED === "true";
const AIVA_DISABLED_TITLE = "AIVA Clinical Assistant is not enabled";
const AIVA_DISABLED_DESCRIPTION = "AI-powered suggestions, clinical chat, report interpretation and summaries are currently disabled for this clinic.";
const AIVA_DISABLED_NOTE = "Ask the clinic administrator to enable AI features from clinic settings.";

const FREQUENCIES = ["1-0-0", "0-1-0", "0-0-1", "1-0-1", "1-1-1"];
const DURATIONS = ["3 days", "5 days", "7 days", "10 days", "15 days"];
const PRESCRIPTION_FOLLOWUP_SHORTCUTS = ["3 days", "5 days", "7 days", "14 days"];
const TIMINGS: { label: string; value: Timing }[] = [
  { label: "Before food", value: "BEFORE_FOOD" },
  { label: "After food", value: "AFTER_FOOD" },
  { label: "With food", value: "WITH_FOOD" },
  { label: "Anytime", value: "ANYTIME" },
];

const LAB_ORDER_STATUS_LABELS: Record<LabOrderStatus, string> = {
  ORDERED: "Payment Pending",
  PAYMENT_PENDING: "Payment Pending",
  PAID: "Payment Pending",
  READY_FOR_COLLECTION: "Sample Pending",
  SAMPLE_COLLECTED: "Sample Collected",
  PROCESSING: "Sample Collected",
  RESULT_ENTERED: "Result Entered",
  REPORT_READY: "Report Ready",
  REPORT_GENERATED: "Report Ready",
  DOCTOR_REVIEWED: "Published",
  DELIVERED: "Published",
  CANCELLED: "Cancelled",
};

const LAB_ORDER_STATUS_COLORS: Record<LabOrderStatus, "default" | "warning" | "success" | "info" | "error"> = {
  ORDERED: "warning",
  PAYMENT_PENDING: "warning",
  PAID: "warning",
  READY_FOR_COLLECTION: "warning",
  SAMPLE_COLLECTED: "info",
  PROCESSING: "info",
  RESULT_ENTERED: "success",
  REPORT_READY: "success",
  REPORT_GENERATED: "success",
  DOCTOR_REVIEWED: "success",
  DELIVERED: "success",
  CANCELLED: "default",
};

function normalizeLookupKey(value: string | null | undefined) {
  return (value || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function formatMoney(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) return "Not available";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
}

function looksLikeFastingTest(testName: string, sampleType: string | null, category: string | null | undefined) {
  const tokens = normalizeLookupKey([testName, sampleType, category].filter(Boolean).join(" "));
  return /fasting|blood sugar|glucose|lipid|cholesterol|triglyceride|hba1c|hba1 c|ppbs|fbs|rbs/.test(tokens);
}

function labOrderReviewStatusLabel(status: LabOrderStatus) {
  return LAB_ORDER_STATUS_LABELS[status] || status.replaceAll("_", " ");
}

function labOrderReviewStatusColor(status: LabOrderStatus) {
  return LAB_ORDER_STATUS_COLORS[status] || "default";
}

function WorkflowStrip({
  text,
  action,
}: {
  text: string;
  action?: React.ReactNode;
}) {
  return (
    <Alert
      severity="info"
      variant="outlined"
      sx={{
        py: 0.45,
        px: 1,
        alignItems: "center",
        "& .MuiAlert-message": { py: 0.1, width: "100%" },
      }}
      action={action}
    >
      <Typography variant="caption" sx={{ fontWeight: 700, lineHeight: 1.25 }}>
        {text}
      </Typography>
    </Alert>
  );
}

const RX_TEMPLATES: RxTemplate[] = [
  {
    key: "fever-adult",
    label: "Fever pack",
    diagnosis: "Viral Fever",
    advice: "Hydrate well, tepid sponging if high fever, monitor warning signs.",
    followUp: "3 days",
    medicines: [
      { medicineName: "Paracetamol", medicineType: "TABLET", strength: "650 mg", dosage: "1 tab", frequency: "1-1-1", duration: "3 days", timing: "AFTER_FOOD", instructions: "If fever >100F" },
      { medicineName: "ORS", medicineType: "OTHER", strength: "", dosage: "200 ml", frequency: "1-1-1", duration: "3 days", timing: "ANYTIME", instructions: "Sip frequently" },
    ],
    tests: ["CBC"],
  },
  {
    key: "cold-cough-adult",
    label: "Common cold pack",
    diagnosis: "Upper Respiratory Infection",
    advice: "Warm fluids, steam inhalation, rest.",
    followUp: "5 days",
    medicines: [
      { medicineName: "Levocetirizine", medicineType: "TABLET", strength: "5 mg", dosage: "1 tab", frequency: "0-0-1", duration: "5 days", timing: "AFTER_FOOD", instructions: "Night" },
      { medicineName: "Cough Syrup", medicineType: "SYRUP", strength: "", dosage: "10 ml", frequency: "1-0-1", duration: "5 days", timing: "AFTER_FOOD", instructions: "" },
    ],
    tests: [],
  },
  {
    key: "acidity",
    label: "Gastric care",
    diagnosis: "Gastritis",
    advice: "Avoid spicy/oily food, small frequent meals.",
    followUp: "7 days",
    medicines: [
      { medicineName: "Pantoprazole", medicineType: "TABLET", strength: "40 mg", dosage: "1 tab", frequency: "1-0-0", duration: "7 days", timing: "BEFORE_FOOD", instructions: "Before breakfast" },
      { medicineName: "Antacid Gel", medicineType: "SYRUP", strength: "", dosage: "10 ml", frequency: "1-1-1", duration: "5 days", timing: "AFTER_FOOD", instructions: "" },
    ],
    tests: [],
  },
  {
    key: "bp-follow-up",
    label: "BP routine",
    diagnosis: "Hypertension Follow-up",
    advice: "Salt restriction, home BP log, regular exercise.",
    followUp: "15 days",
    medicines: [
      { medicineName: "Amlodipine", medicineType: "TABLET", strength: "5 mg", dosage: "1 tab", frequency: "1-0-0", duration: "15 days", timing: "AFTER_FOOD", instructions: "Same time daily" },
    ],
    tests: ["ECG"],
  },
  {
    key: "diabetes-routine",
    label: "Diabetes follow-up",
    diagnosis: "Diabetes Follow-up",
    advice: "Diet control, sugar log, foot care counseling.",
    followUp: "1 month",
    medicines: [
      { medicineName: "Metformin", medicineType: "TABLET", strength: "500 mg", dosage: "1 tab", frequency: "1-0-1", duration: "30 days", timing: "AFTER_FOOD", instructions: "" },
    ],
    tests: ["Blood Sugar"],
  },
];

function statusColor(status: Consultation["status"] | Prescription["status"]) {
  switch (status) {
    case "COMPLETED":
    case "FINALIZED":
    case "PRINTED":
    case "SENT":
      return "success";
    case "DRAFT":
    case "PREVIEWED":
      return "warning";
    case "CANCELLED":
      return "default";
  }
}

function calculateBmi(weightKg: string, heightCm: string) {
  const weight = Number(weightKg);
  const height = Number(heightCm);
  if (!weightKg || !heightCm || Number.isNaN(weight) || Number.isNaN(height) || weight <= 0 || height <= 0) {
    return null;
  }
  const meters = height / 100;
  if (meters <= 0) {
    return null;
  }
  const bmi = weight / (meters * meters);
  return Number.isFinite(bmi) ? bmi : null;
}

function bmiCategory(bmi: number | null) {
  if (bmi == null) return null;
  if (bmi < 18.5) return "Underweight";
  if (bmi < 25) return "Normal";
  if (bmi < 30) return "Overweight";
  return "Obese";
}

function emptyConsultationForm(record?: Consultation | null): ConsultationFormState {
  return {
    chiefComplaints: record?.chiefComplaints || "",
    symptoms: record?.symptoms || "",
    diagnosis: record?.diagnosis || "",
    clinicalNotes: record?.clinicalNotes || "",
    advice: record?.advice || "",
    followUpDate: record?.followUpDate || "",
    bloodPressureSystolic: record?.bloodPressureSystolic?.toString() || "",
    bloodPressureDiastolic: record?.bloodPressureDiastolic?.toString() || "",
    pulseRate: record?.pulseRate?.toString() || "",
    temperature: record?.temperature?.toString() || "",
    temperatureUnit: record?.temperatureUnit || "",
    weightKg: record?.weightKg?.toString() || "",
    heightCm: record?.heightCm?.toString() || "",
    spo2: record?.spo2?.toString() || "",
    respiratoryRate: record?.respiratoryRate?.toString() || "",
  };
}

function emptyConsultationSoapForm(record?: ConsultationSoapNote | null): ConsultationSoapFormState {
  return {
    subjective: record?.subjective || "",
    objective: record?.objective || "",
    assessment: record?.assessment || "",
    plan: record?.plan || "",
  };
}

function normalizeSoapWhitespace(text: string) {
  return text.replace(/\s+/g, " ").trim();
}

function splitSoapSentences(text: string): string[] {
  if (typeof Intl !== "undefined" && "Segmenter" in Intl) {
    const segmenter = new Intl.Segmenter("en", { granularity: "sentence" });
    return Array.from(segmenter.segment(text), (segment) => segment.segment.trim()).filter(Boolean);
  }
  return text.split(/(?<=[.!?])\s+/).map((segment) => segment.trim()).filter(Boolean);
}

function dedupeSoapSectionText(text: string | null | undefined) {
  const normalized = normalizeSoapWhitespace(String(text || ""));
  if (!normalized) return "";
  const sentences = splitSoapSentences(normalized);
  const seen = new Set<string>();
  const deduped: string[] = [];
  for (const sentence of sentences.length ? sentences : [normalized]) {
    const cleaned = normalizeSoapWhitespace(sentence);
    if (!cleaned) continue;
    const key = cleaned.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(cleaned);
  }
  return deduped.join(" ");
}

function buildSoapContextSummary(params: {
  generatedAt?: string | null;
  provider?: string | null;
}) {
  const lines = [
    "Generated using",
    "• Chief Complaint",
    "• Symptoms",
    "• Diagnosis",
    "• Vitals",
    "• Longitudinal History",
    params.generatedAt ? `Generated: ${compactDateTime(params.generatedAt)}` : null,
    params.provider ? `AI Provider: ${params.provider}` : null,
  ].filter(Boolean);
  return lines.join("\n");
}

function newMedicineRow(index: number): MedicineRow {
  return {
    localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 8)}`,
    medicineName: "",
    medicineType: null,
    strength: "",
    route: "",
    dosage: "",
    frequency: "",
    duration: "",
    timing: null,
    instructions: "",
    sortOrder: index + 1,
  };
}

function normalizeStringValue(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number") return String(value);
  if (value && typeof value === "object") {
    const candidate = value as Record<string, unknown>;
    for (const key of ["medicineName", "label", "value", "title", "name"]) {
      if (typeof candidate[key] === "string" && candidate[key]!.trim()) {
        return candidate[key]!.trim();
      }
    }
  }
  return "";
}

function normalizeNullableStringValue(value: unknown): string | null {
  const normalized = normalizeStringValue(value);
  return normalized || null;
}

function normalizeEnumValue<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  if (typeof value === "string") {
    const normalized = value.trim() as T;
    return allowed.includes(normalized) ? normalized : null;
  }
  if (value && typeof value === "object" && "value" in (value as Record<string, unknown>)) {
    return normalizeEnumValue((value as Record<string, unknown>).value, allowed);
  }
  return null;
}

function medicineRowHasAnyContent(row: MedicineRow): boolean {
  return Boolean(
    normalizeStringValue(row.medicineName)
    || normalizeStringValue(row.strength)
    || normalizeStringValue(row.dosage)
    || normalizeStringValue(row.frequency)
    || normalizeStringValue(row.duration)
    || normalizeStringValue(row.instructions)
    || normalizeEnumValue(row.medicineType, VALID_MEDICINE_TYPES)
    || normalizeEnumValue(row.timing, VALID_TIMINGS)
  );
}

function hasAtLeastOneValidMedicineRow(form: PrescriptionFormState): boolean {
  return form.medicines.some((row) =>
    normalizeStringValue(row.medicineName)
    && normalizeStringValue(row.dosage)
    && normalizeStringValue(row.frequency)
    && normalizeStringValue(row.duration)
  );
}

function buildPrescriptionInput(form: PrescriptionFormState, consultation: Consultation): PrescriptionInput {
  const normalizedMedicines: PrescriptionMedicine[] = [];
  const invalidMedicineRowIds: string[] = [];

  for (const [index, row] of form.medicines.entries()) {
    const medicineName = normalizeStringValue(row.medicineName);
    const strength = normalizeNullableStringValue(row.strength);
    const dosage = normalizeStringValue(row.dosage);
    const frequency = normalizeStringValue(row.frequency);
    const duration = normalizeStringValue(row.duration);
    const instructions = normalizeNullableStringValue(row.instructions);
    const medicineType = normalizeEnumValue(row.medicineType, VALID_MEDICINE_TYPES);
    const timing = normalizeEnumValue(row.timing, VALID_TIMINGS);

    if (!medicineRowHasAnyContent(row)) {
      continue;
    }
    if (!medicineName) {
      invalidMedicineRowIds.push(row.localId);
      continue;
    }
    if (!dosage || !frequency || !duration) {
      invalidMedicineRowIds.push(row.localId);
      continue;
    }

    normalizedMedicines.push({
      medicineName,
      medicineType,
      strength,
      dosage,
      frequency,
      duration,
      timing,
      instructions,
      sortOrder: row.sortOrder ?? index + 1,
    });
  }

  if (invalidMedicineRowIds.length) {
    throw new PrescriptionPayloadValidationError("Complete medicine name, dosage, frequency, and duration for highlighted rows.", invalidMedicineRowIds);
  }
  if (!normalizedMedicines.length && !hasPrescriptionContent(form)) {
    throw new PrescriptionPayloadValidationError("Add a medicine or prescription note before saving.");
  }

  const recommendedTests = form.recommendedTests
    .map((row, index) => ({
      testName: normalizeStringValue(row.testName),
      instructions: normalizeNullableStringValue(row.instructions),
      sortOrder: row.sortOrder ?? index + 1,
    }))
    .filter((row) => row.testName);

  const body: PrescriptionInput = {
    patientId: consultation.patientId,
    doctorUserId: consultation.doctorUserId,
    consultationId: consultation.id,
    appointmentId: consultation.appointmentId,
    diagnosisSnapshot: normalizeNullableStringValue(form.diagnosisSnapshot),
    advice: normalizeNullableStringValue(form.advice),
    followUpDate: normalizeStringValue(form.followUpDate) || null,
    medicines: normalizedMedicines,
    recommendedTests,
  };

  if (import.meta.env.DEV) {
    console.debug("[consultation] normalized prescription payload", body);
  }

  return body;
}

function newTestRow(index: number): TestRow {
  return {
    localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 8)}`,
    testName: "",
    instructions: "",
    sortOrder: index + 1,
  };
}

function emptyPrescriptionForm(record?: Prescription | null, consultation?: Consultation | null): PrescriptionFormState {
  return {
    diagnosisSnapshot: record?.diagnosisSnapshot || consultation?.diagnosis || "",
    advice: record?.advice || consultation?.advice || "",
    followUpDate: record?.followUpDate || consultation?.followUpDate || "",
    medicines: record?.medicines?.length
      ? record.medicines.map((item, index) => ({ ...item, route: (item as Partial<MedicineRow>).route ?? "", localId: `${index}-${item.sortOrder ?? index}` }))
      : [newMedicineRow(0)],
    recommendedTests: record?.recommendedTests?.length
      ? record.recommendedTests.map((item, index) => ({ ...item, localId: `${index}-${item.sortOrder ?? index}` }))
      : [],
  };
}

function consultationTabIndexToKey(tabIndex: number): string {
  return CONSULTATION_TAB_KEYS[tabIndex] || "consultation";
}

function consultationTabKeyToIndex(tabKey: string | null | undefined): number {
  const normalized = (tabKey || "").trim().toLowerCase();
  const index = CONSULTATION_TAB_KEYS.indexOf(normalized as (typeof CONSULTATION_TAB_KEYS)[number]);
  return index >= 0 ? index : 0;
}

function toConsultationInput(form: ConsultationFormState, consultation: Consultation): ConsultationInput {
  return {
    patientId: consultation.patientId,
    doctorUserId: consultation.doctorUserId,
    appointmentId: consultation.appointmentId,
    chiefComplaints: form.chiefComplaints.trim() || null,
    symptoms: form.symptoms.trim() || null,
    diagnosis: form.diagnosis.trim() || null,
    clinicalNotes: form.clinicalNotes.trim() || null,
    advice: form.advice.trim() || null,
    followUpDate: form.followUpDate || null,
    bloodPressureSystolic: form.bloodPressureSystolic ? Number(form.bloodPressureSystolic) : null,
    bloodPressureDiastolic: form.bloodPressureDiastolic ? Number(form.bloodPressureDiastolic) : null,
    pulseRate: form.pulseRate ? Number(form.pulseRate) : null,
    temperature: form.temperature ? Number(form.temperature) : null,
    temperatureUnit: form.temperatureUnit || null,
    weightKg: form.weightKg ? Number(form.weightKg) : null,
    heightCm: form.heightCm ? Number(form.heightCm) : null,
    spo2: form.spo2 ? Number(form.spo2) : null,
    respiratoryRate: form.respiratoryRate ? Number(form.respiratoryRate) : null,
  };
}

function serializeConsultationForm(form: ConsultationFormState): string {
  return JSON.stringify(form);
}

function serializeConsultationSoapForm(form: ConsultationSoapFormState): string {
  return JSON.stringify(form);
}

function serializePrescriptionForm(form: PrescriptionFormState): string {
  return JSON.stringify({
    diagnosisSnapshot: form.diagnosisSnapshot,
    advice: form.advice,
    followUpDate: form.followUpDate,
    medicines: form.medicines.map((row) => ({
      medicineName: row.medicineName,
      medicineType: row.medicineType,
      strength: row.strength,
      dosage: row.dosage,
      frequency: row.frequency,
      duration: row.duration,
      timing: row.timing,
      instructions: row.instructions,
      sortOrder: row.sortOrder,
    })),
    recommendedTests: form.recommendedTests.map((row) => ({
      testName: row.testName,
      instructions: row.instructions,
      sortOrder: row.sortOrder,
    })),
  });
}

function hasPrescriptionContent(form: PrescriptionFormState): boolean {
  return Boolean(
    normalizeStringValue(form.diagnosisSnapshot) ||
      normalizeStringValue(form.advice) ||
      normalizeStringValue(form.followUpDate) ||
      form.medicines.some((row) => medicineRowHasAnyContent(row)) ||
      form.recommendedTests.some((row) => normalizeStringValue(row.testName) || normalizeStringValue(row.instructions))
  );
}

function isPrescriptionReadyForConsultationCompletion(prescription: Prescription | null): boolean {
  return prescription ? ["FINALIZED", "PRINTED", "SENT"].includes(prescription.status) : false;
}

function isEditablePrescriptionStatus(status: Prescription["status"] | null | undefined): boolean {
  return status === "DRAFT" || status === "PREVIEWED";
}

function appendTokenLine(base: string, token: string): string {
  const current = base.trim();
  if (!token.trim()) return base;
  if (!current) return token;
  if (current.toLowerCase().includes(token.toLowerCase())) return base;
  return `${current}, ${token}`;
}

function appendSoapSectionText(base: string, addition: string): string {
  const current = base.trim();
  const next = addition.trim();
  if (!next) return base;
  if (!current) return next;
  if (current.toLowerCase().includes(next.toLowerCase())) return base;
  return `${current}\n${next}`;
}

function normalizeClinicalToken(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, " ");
}

function appendUniqueTokenLine(base: string, token: string): string {
  const nextToken = token.trim();
  if (!nextToken) return base;
  const current = base.trim();
  if (!current) return nextToken;
  const normalizedToken = normalizeClinicalToken(nextToken);
  const entries = current.split(/[\n,;•]+/).map((entry) => entry.trim()).filter(Boolean);
  if (entries.some((entry) => normalizeClinicalToken(entry) === normalizedToken)) {
    return base;
  }
  return appendTokenLine(base, nextToken);
}

function splitClinicalTokenEntries(value: string): string[] {
  return value
    .split(/[\n,;•]+/)
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function removeClinicalTokenEntry(value: string, tokenToRemove: string): string {
  const normalizedToken = normalizeClinicalToken(tokenToRemove);
  const remainingEntries = splitClinicalTokenEntries(value).filter(
    (entry) => normalizeClinicalToken(entry) !== normalizedToken
  );
  return remainingEntries.join(", ");
}

function pluralizeLabTests(count: number): string {
  return `${count} ${count === 1 ? "test" : "tests"}`;
}

function confidenceLevelFromPercent(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) return "Unknown";
  if (value >= 0.8) return "Very High";
  if (value >= 0.6) return "High";
  if (value >= 0.4) return "Moderate";
  return "Low";
}

function likelihoodLevelFromPercent(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) return "Unclear";
  if (value >= 0.8) return "Very likely";
  if (value >= 0.6) return "Likely";
  if (value >= 0.4) return "Possible";
  return "Less likely";
}

function confidenceLevelFromText(value: string | null | undefined) {
  const normalized = String(value || "").toUpperCase();
  if (normalized.includes("VERY_HIGH")) return "Very High";
  if (normalized.includes("HIGH")) return "High";
  if (normalized.includes("MEDIUM") || normalized.includes("MODERATE")) return "Moderate";
  if (normalized.includes("LOW")) return "Low";
  return "Unknown";
}

function classifyClinicalReasoningTests(tests: ClinicalReasoningResult["recommendedTests"]) {
  const buckets = {
    alreadyAvailable: [] as NonNullable<ClinicalReasoningResult["recommendedTests"]>,
    recommended: [] as NonNullable<ClinicalReasoningResult["recommendedTests"]>,
    optional: [] as NonNullable<ClinicalReasoningResult["recommendedTests"]>,
  };
  tests.forEach((test) => {
    const text = [test.name, test.reason, test.source, test.actionType].filter(Boolean).join(" ").toLowerCase();
    if (test.alreadyAvailable || test.pendingOrderExists || text.includes("already available") || text.includes("existing result") || text.includes("display only if clinically needed") || text.includes("already done")) {
      buckets.alreadyAvailable.push(test);
      return;
    }
    if (text.includes("consider") || text.includes("optional") || text.includes("if indicated")) {
      buckets.optional.push(test);
      return;
    }
    buckets.recommended.push(test);
  });
  return buckets;
}

function normalizeInvestigationCanonicalKey(value: string) {
  const normalized = normalizeLookupKey(value);
  if (!normalized) return "";
  if (/(hba1c|hba1 c|a1c|hemoglobin a1c)/.test(normalized)) return "hba1c";
  if (/(blood sugar|blood glucose|glucose|random blood sugar|fasting blood sugar|rbs|fbs|ppbs)/.test(normalized)) return "blood sugar";
  if (/(cbc|complete blood count)/.test(normalized)) return "cbc";
  if (/(crp|c reactive protein|c reactive)/.test(normalized)) return "crp";
  if (/(lipid profile|cholesterol|triglyceride|triglycerides|hdl|ldl|lipid)/.test(normalized)) return "lipid profile";
  if (/(chest x ray|xray chest|x ray chest|cxr|chest xray)/.test(normalized)) return "chest x ray";
  if (/(covid|sars cov 2|sars cov2|sarscov2)/.test(normalized)) return "covid 19";
  if (/(influenza|flu)/.test(normalized)) return "influenza";
  if (/(creatinine|rft|renal function|kidney function)/.test(normalized)) return "creatinine";
  return normalized;
}

function investigationLabelFromCanonicalKey(key: string) {
  switch (key) {
    case "hba1c":
      return "HbA1c";
    case "blood sugar":
      return "Blood Sugar";
    case "cbc":
      return "CBC";
    case "crp":
      return "CRP";
    case "lipid profile":
      return "Lipid Profile";
    case "chest x ray":
      return "Chest X-Ray";
    case "covid 19":
      return "COVID-19 test";
    case "influenza":
      return "Influenza test";
    case "creatinine":
      return "Creatinine";
    default:
      return key
        .split(" ")
        .filter(Boolean)
        .map((part) => `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)
        .join(" ");
  }
}

function investigationAliasesForCanonicalKey(key: string) {
  switch (key) {
    case "hba1c":
      return ["hba1c", "hb a1c", "hemoglobin a1c", "a1c"];
    case "blood sugar":
      return ["blood sugar", "blood glucose", "glucose", "random blood sugar", "fasting blood sugar", "rbs", "fbs", "ppbs"];
    case "cbc":
      return ["cbc", "complete blood count"];
    case "crp":
      return ["crp", "c reactive protein", "c-reactive protein", "c reactive"];
    case "lipid profile":
      return ["lipid profile", "cholesterol", "triglyceride", "triglycerides", "hdl", "ldl", "lipid"];
    case "chest x ray":
      return ["chest x ray", "xray chest", "x ray chest", "cxr", "chest xray"];
    case "covid 19":
      return ["covid", "covid 19", "covid-19", "sars cov 2", "sars cov2", "sarscov2"];
    case "influenza":
      return ["influenza", "flu"];
    case "creatinine":
      return ["creatinine", "rft", "renal function", "kidney function"];
    default:
      return [key];
  }
}

function investigationTextMatches(text: string, aliases: string[]) {
  const normalizedText = normalizeLookupKey(text);
  return aliases.some((alias) => {
    const normalizedAlias = normalizeLookupKey(alias);
    return Boolean(normalizedAlias) && (normalizedText.includes(normalizedAlias) || normalizedAlias.includes(normalizedText));
  });
}

function formatInvestigationEvidenceValue(valueText: string | null, unitText: string | null, observedOn: string | null) {
  const value = [valueText, unitText].filter(Boolean).join(" ").trim();
  const date = observedOn ? compactDate(observedOn) : null;
  if (value && date) return `${value} on ${date}`;
  if (value) return value;
  if (date) return date;
  return null;
}

function formatRelativeAge(value: string | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const now = new Date();
  const diffMs = Math.max(0, now.getTime() - date.getTime());
  const dayMs = 24 * 60 * 60 * 1000;
  const days = Math.floor(diffMs / dayMs);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} month${months === 1 ? "" : "s"} ago`;
  const years = Math.floor(months / 12);
  return `${years} year${years === 1 ? "" : "s"} ago`;
}

function pluralizeInvestigationCount(count: number) {
  return `${count} Investigation${count === 1 ? "" : "s"}`;
}

function getVisibleInvestigationStatusLabel(status: InvestigationIntelligenceStatus) {
  return status === "Consider" ? "Optional" : status;
}

function getInvestigationResultFlag(testName: string, valueText: string | null | undefined) {
  const key = normalizeInvestigationCanonicalKey(testName);
  const numericValue = Number.parseFloat((valueText || "").replace(/[^0-9.]+/g, ""));
  if (Number.isNaN(numericValue)) return null;
  if (key === "hba1c") return numericValue >= 5.7 ? "High" : "Normal";
  if (key === "blood sugar") return numericValue >= 140 ? "High" : "Normal";
  return null;
}

function formatClinicalReasoningMetaValue(value: unknown) {
  if (value == null || value === "") return null;
  return String(value);
}

function formatClinicalReasoningVerificationLabel(value: string | null | undefined) {
  const normalized = String(value || "").toUpperCase();
  if (["VERIFIED", "APPROVED", "ACCEPTED"].includes(normalized)) return "Verified";
  if (["PENDING_REVIEW", "PENDING_VERIFICATION", "UNVERIFIED", "NOT_REVIEWED", "REVIEW_REQUIRED", "AI_REVIEW_REQUIRED"].includes(normalized)) return "Pending verification";
  if (normalized === "REJECTED") return "Rejected";
  return null;
}

function formatCoverageStateLabel(value: string | null | undefined) {
  const normalized = String(value || "").trim().toUpperCase();
  if (!normalized) return "Unavailable";
  if (normalized === "EVALUATED" || normalized === "PARTIAL" || normalized === "UNAVAILABLE") {
    return normalized.charAt(0) + normalized.slice(1).toLowerCase();
  }
  return String(value);
}

function isUuidLike(value: string | null | undefined) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(value || "").trim());
}

function formatRenalFindingText(value: string | null | undefined) {
  if (!value) return "";
  return String(value).replaceAll("mL/min/1.73m2", "mL/min/1.73m²");
}

function formatRenalSourceLabels(sourceReferences: string[]) {
  const visible = (sourceReferences || []).filter((ref) => Boolean(ref) && !isUuidLike(ref));
  const source = visible.find((ref) => String(ref).trim().toLowerCase() !== "longitudinal memory") || visible[0] || "Longitudinal memory";
  const origin = visible.some((ref) => String(ref).trim().toLowerCase() === "longitudinal memory") ? "Longitudinal memory" : "Longitudinal memory";
  return { source, origin };
}

function clinicalReasoningHistoricalLabel(sourceDate: string | null | undefined) {
  if (!sourceDate) return "Historical result";
  return compactDate(sourceDate);
}

function investigationStatusColor(status: InvestigationIntelligenceStatus) {
  switch (status) {
    case "Already Available":
    case "Recently Completed":
      return "success";
    case "Pending":
      return "warning";
    case "Recommended":
      return "primary";
    case "Consider":
      return "info";
    case "Unknown":
    default:
      return "default";
  }
}

function investigationStatusBorderColor(status: InvestigationIntelligenceStatus) {
  switch (status) {
    case "Already Available":
    case "Recently Completed":
      return "success.main";
    case "Pending":
      return "warning.main";
    case "Recommended":
      return "primary.main";
    case "Consider":
      return "info.main";
    case "Unknown":
    default:
      return "divider";
  }
}

function investigationStatusIcon(status: InvestigationIntelligenceStatus) {
  switch (status) {
    case "Already Available":
    case "Recently Completed":
      return <CheckCircleRoundedIcon fontSize="inherit" />;
    case "Pending":
      return <ScheduleRoundedIcon fontSize="inherit" />;
    case "Consider":
      return <LightbulbRoundedIcon fontSize="inherit" />;
    case "Recommended":
      return <ScienceRoundedIcon fontSize="inherit" />;
    case "Unknown":
    default:
      return undefined;
  }
}

function getInvestigationIcon(testName: string) {
  const key = normalizeInvestigationCanonicalKey(testName);
  if (key === "cbc") return <FavoriteRoundedIcon fontSize="inherit" aria-hidden="true" />;
  if (key === "covid 19" || key === "influenza") return <BiotechRoundedIcon fontSize="inherit" aria-hidden="true" />;
  if (key === "chest x ray") return <InsightsRoundedIcon fontSize="inherit" aria-hidden="true" />;
  if (key === "blood sugar" || key === "hba1c" || key === "crp" || key === "creatinine" || key === "lipid profile") {
    return <ScienceRoundedIcon fontSize="inherit" aria-hidden="true" />;
  }
  return <BiotechRoundedIcon fontSize="inherit" aria-hidden="true" />;
}

function investigationEvidenceLines(row: InvestigationIntelligenceRow) {
  const evidence = row.evidence?.trim() || "";
  if (!evidence) {
    return ["No recent matching result found"];
  }
  if (row.status === "Pending") {
    const pendingLabel = evidence.replace(/^Pending lab order:\s*/i, "").trim();
    return ["Pending Lab Order", pendingLabel || row.testName];
  }
  if (row.status === "Already Available" || row.status === "Recently Completed") {
    const date = row.observedOn ? compactDate(row.observedOn) : null;
    const age = formatRelativeAge(row.observedOn);
    const parts = evidence
      .split(/\s*[•|;]\s*|\s{2,}/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 3);
    const resultValue = (parts[0] || evidence).replace(/\s+on\s+.+$/i, "").trim();
    const resultFlag = getInvestigationResultFlag(row.testName, resultValue);
    const lines = [resultFlag ? `${resultValue} · ${resultFlag}` : resultValue];
    if (date) lines.splice(1, 0, date);
    if (age) lines.splice(2, 0, age);
    return lines.slice(0, 3);
  }
  return evidence
    .split(/\s*[•|;]\s*|\s{2,}/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3);
}

function investigationDoctorNote(row: InvestigationIntelligenceRow) {
  const key = normalizeInvestigationCanonicalKey(row.testName);
  if (row.duplicateRisk) {
    return "Recent result or pending order already exists. Avoid repeat unless clinically indicated.";
  }
  if (key === "hba1c") {
    return row.status === "Pending"
      ? "Pending order exists. Review before creating another request."
      : "Recent result available. Repeat only if clinically indicated.";
  }
  if (key === "cbc") {
    return row.status === "Pending"
      ? "Pending order exists. Review before creating another request."
      : "Recent result available. Repeat only if clinically indicated.";
  }
  if (key === "crp") {
    return "Useful if fever persists or inflammatory signs increase.";
  }
  if (key === "covid 19") {
    return "Useful for respiratory infection or exposure assessment.";
  }
  if (key === "influenza") {
    return "Useful during influenza season or prominent viral symptoms.";
  }
  if (row.status === "Unknown") {
    return "Clinical judgement required.";
  }
  if (row.status === "Pending") {
    return "Pending order exists. Review before creating another request.";
  }
  if (row.status === "Already Available" || row.status === "Recently Completed") {
    return "Recent result available. Repeat only if clinically indicated.";
  }
  if (row.status === "Consider") {
    return "Useful if clinically needed.";
  }
  return "Clinical judgement required.";
}

function followUpChipToDate(value: string): string {
  const now = new Date();
  if (value === "1 month") {
    now.setMonth(now.getMonth() + 1);
  } else {
    const days = Number(value.replace(/\D+/g, ""));
    if (!Number.isNaN(days) && days > 0) now.setDate(now.getDate() + days);
  }
  return now.toISOString().slice(0, 10);
}

function templateToRows(template: RxTemplate): { meds: MedicineRow[]; tests: TestRow[] } {
  return {
    meds: template.medicines.map((m, index) => ({ ...newMedicineRow(index), ...m, sortOrder: index + 1 })),
    tests: template.tests.map((name, index) => ({ ...newTestRow(index), testName: name, instructions: "", sortOrder: index + 1 })),
  };
}

function formatPatientAgeGender(patient: PatientDetail["patient"] | undefined): string {
  if (!patient) return "Age/Gender not recorded";
  const age = patient.ageYears == null ? "Age -" : `${patient.ageYears}y`;
  return `${age} / ${patient.gender || "-"}`;
}

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((entry) => String(entry ?? "").trim()).filter(Boolean);
}

function isPromptLeak(value: string) {
  const normalized = value.toLowerCase().replace(/[\r\n\t]+/g, " ");
  return [
    "return only valid json",
    "use exactly this shape",
    "no markdown",
    "no extra text",
    "do not return a top-level array",
    "\"diagnosis\": \"short name\"",
    "\"medicine\": \"medicine name\"",
    "one short sentence up to 140 chars",
  ].some((pattern) => normalized.includes(pattern));
}

function extractJsonCandidate(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("```")) {
    const openEnd = trimmed.indexOf("\n");
    const fenceEnd = trimmed.lastIndexOf("```");
    if (openEnd >= 0 && fenceEnd > openEnd) {
      return trimmed.slice(openEnd + 1, fenceEnd).trim();
    }
  }
  const firstBrace = trimmed.indexOf("{");
  const lastBrace = trimmed.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1).trim();
  }
  const firstBracket = trimmed.indexOf("[");
  const lastBracket = trimmed.lastIndexOf("]");
  if (firstBracket >= 0 && lastBracket > firstBracket) {
    return trimmed.slice(firstBracket, lastBracket + 1).trim();
  }
  return trimmed;
}

function parseStructuredObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function normalizeAiSuggestionItem(entry: unknown): AiSuggestionItem | null {
  if (typeof entry === "string") {
    const title = entry.trim();
    if (!title || isPromptLeak(title)) return null;
    return {
      title,
      reason: null,
      confidence: null,
      redFlags: [],
      recommendedInvestigations: [],
      followUpSuggestions: [],
      rawText: title,
    };
  }
  if (!entry || typeof entry !== "object") return null;
  const row = entry as Record<string, unknown>;
  const title = String(
    row.title ??
    row.diagnosis ??
    row.condition ??
    row.name ??
    row.suggestion ??
    row.text ??
    row.label ??
    ""
  ).trim();
  if (!title || isPromptLeak(title)) return null;
  const reason = String(row.reason ?? row.reasoning ?? row.note ?? row.notes ?? "").trim() || null;
  const confidence = String(row.confidence ?? row.confidenceLevel ?? "").trim() || null;
  const redFlags = asStringList(row.redFlags ?? row.redFlagExclusions ?? row.flags);
  const recommendedInvestigations = asStringList(row.recommendedInvestigations ?? row.investigations ?? row.tests);
  const followUpSuggestions = asStringList(row.followUpSuggestions ?? row.followUps ?? row.followUp);
  const rawText = [title, reason, confidence ? `confidence: ${confidence}` : ""].filter(Boolean).join(" | ");
  return { title, reason, confidence, redFlags, recommendedInvestigations, followUpSuggestions, rawText };
}

function normalizeAiMedicineSuggestionItem(entry: unknown): AiMedicineSuggestionItem | null {
  if (typeof entry === "string") {
    const medicine = entry.trim();
    if (!medicine || isPromptLeak(medicine)) return null;
    return { medicine, dose: null, frequency: null, duration: null, reason: null, safetyNote: null, rawText: medicine };
  }
  if (!entry || typeof entry !== "object") return null;
  const row = entry as Record<string, unknown>;
  const medicine = String(
    row.medicine ??
    row.medicineName ??
    row.name ??
    row.title ??
    row.label ??
    ""
  ).trim();
  if (!medicine || isPromptLeak(medicine)) return null;
  const dose = String(row.dose ?? row.strength ?? row.dosage ?? "").trim() || null;
  const frequency = String(row.frequency ?? row.freq ?? "").trim() || null;
  const duration = String(row.duration ?? row.days ?? row.durationDays ?? "").trim() || null;
  const reason = String(row.reason ?? row.reasoning ?? row.note ?? row.notes ?? "").trim() || null;
  const safetyNote = String(row.safetyNote ?? row.safety ?? row.warning ?? row.warnings ?? "").trim() || null;
  const rawText = [medicine, dose, frequency, duration, reason].filter(Boolean).join(" | ");
  return { medicine, dose, frequency, duration, reason, safetyNote, rawText };
}

function parseAiSuggestionItems(draft: AiDraftResponse): { items: AiSuggestionItem[]; summary: string | null; rawText: string; unstructured: boolean; invalid: boolean } {
  const structured = parseStructuredObject(draft.structuredData);
  const summary = typeof structured["summary"] === "string" ? structured["summary"].trim() : null;
  const safetyNote = typeof structured["safetyNote"] === "string" ? structured["safetyNote"].trim() : "";
  const safetyNotes = asStringList(structured["safetyNotes"]);
  const rawText = (draft.draft || draft.message || "").trim();
  const source = Array.isArray(structured["suggestions"]) ? structured["suggestions"] : Array.isArray(structured["possibleDiagnosisCategories"]) ? structured["possibleDiagnosisCategories"] : [];
  const items = source.map(normalizeAiSuggestionItem).filter((item): item is AiSuggestionItem => Boolean(item));
  const invalid = !rawText
    || isPromptLeak(rawText)
    || (summary || "").toLowerCase().includes("incomplete")
    || safetyNotes.some((item) => item.toLowerCase().includes("invalid response") || item.toLowerCase().includes("incomplete"))
    || safetyNote.toLowerCase().includes("invalid response")
    || safetyNote.toLowerCase().includes("incomplete");
  const unstructured = !items.length && !invalid;
  return { items, summary, rawText, unstructured, invalid };
}

function parseAiMedicineSuggestionItems(draft: AiDraftResponse): { items: AiMedicineSuggestionItem[]; summary: string | null; rawText: string; unstructured: boolean; invalid: boolean } {
  const structured = parseStructuredObject(draft.structuredData);
  const summary = typeof structured["summary"] === "string" ? structured["summary"].trim() : null;
  const safetyNote = typeof structured["safetyNote"] === "string" ? structured["safetyNote"].trim() : null;
  const safetyNotes = asStringList(structured["safetyNotes"]);
  const rawText = (draft.draft || draft.message || "").trim();
  const source = Array.isArray(structured["suggestions"]) ? structured["suggestions"] : [];
  const items = source.map(normalizeAiMedicineSuggestionItem).filter((item): item is AiMedicineSuggestionItem => Boolean(item));
  const invalid = !rawText
    || isPromptLeak(rawText)
    || (summary || "").toLowerCase().includes("incomplete")
    || safetyNotes.some((item) => item.toLowerCase().includes("invalid response") || item.toLowerCase().includes("incomplete"))
    || (safetyNote || "").toLowerCase().includes("invalid response")
    || (safetyNote || "").toLowerCase().includes("incomplete");
  const unstructured = !items.length && !invalid;
  return { items, summary, rawText, unstructured, invalid };
}

function compactDate(value?: string | null): string {
  return value ? new Date(value).toLocaleDateString() : "-";
}

function compactDateTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : "-";
}

type StructuredLabTrend = NonNullable<NonNullable<ClinicalContextResponse["longitudinalClinicalContext"]>["labTrends"]>[number];

function formatTrendValue(value: string | null | undefined, unit: string | null | undefined) {
  return [value, unit].filter(Boolean).join(value && unit ? " " : "");
}

function formatTrendVerificationLabel(value: string | null | undefined) {
  const normalized = (value || "").trim().replaceAll("_", " ").toLowerCase();
  if (!normalized) return null;
  return normalized.split(/\s+/).map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(" ");
}

function StructuredTrendSummary({
  structuredTrends,
  legacyTrends,
  latestReport,
  summary,
  fallbackMode = "legacyAndReport",
  emptyStateLabel = "No comparable reports yet",
  emptyStateDetail = "Structured comparison is available when previous numeric results exist.",
}: {
  structuredTrends: StructuredLabTrend[];
  legacyTrends: string[];
  latestReport: string | null;
  summary: string | null;
  fallbackMode?: "legacyAndReport" | "legacyOnly";
  emptyStateLabel?: string;
  emptyStateDetail?: string;
}) {
  if (structuredTrends.length) {
    return (
      <Stack spacing={0.75}>
        {structuredTrends.map((trend, index) => {
          const sourceCount = trend.sourceDocumentIds?.length || 0;
          const directionLabel = (trend.direction || "").trim().replaceAll("_", " ");
          const verificationLabel = formatTrendVerificationLabel(trend.verificationStatus);
          return (
            <Card
              key={`${trend.analyteCode || trend.analyteName || "trend"}-${trend.newerDate || trend.olderDate || index}`}
              variant="outlined"
              sx={{ boxShadow: "none", borderRadius: 1.5 }}
            >
              <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                <Stack spacing={0.45}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" useFlexGap flexWrap="wrap">
                    <Typography variant="body2" sx={{ fontWeight: 900 }}>
                      {trend.analyteName || trend.analyteCode || "Trend"}
                    </Typography>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {directionLabel ? <Chip size="small" variant="outlined" color="primary" label={directionLabel} /> : null}
                      {verificationLabel ? <Chip size="small" variant="outlined" color="warning" label={verificationLabel} /> : null}
                      {sourceCount ? <Chip size="small" variant="outlined" color="default" label={`${sourceCount} source${sourceCount === 1 ? "" : "s"}`} /> : null}
                    </Stack>
                  </Stack>
                  <Typography variant="body2" sx={{ fontWeight: 900 }}>
                    {formatTrendValue(trend.olderValue, trend.olderUnit)}{" "}
                    <Box component="span" sx={{ color: "text.secondary", fontWeight: 700 }}>
                      →
                    </Box>{" "}
                    {formatTrendValue(trend.newerValue, trend.newerUnit)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.25 }}>
                    {compactDate(trend.olderDate)} • {compactDate(trend.newerDate)}
                  </Typography>
                  {trend.absoluteChange || trend.interval ? (
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.25 }}>
                      {[trend.absoluteChange, trend.interval ? `over ${trend.interval}` : null].filter(Boolean).join(" ")}
                    </Typography>
                  ) : null}
                  {trend.clinicalInterpretation ? (
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.35 }}>
                      {trend.clinicalInterpretation}
                    </Typography>
                  ) : null}
                </Stack>
              </CardContent>
            </Card>
          );
        })}
      </Stack>
    );
  }

  if (legacyTrends.length || (fallbackMode === "legacyAndReport" && (latestReport || summary))) {
    return (
      <Stack spacing={0.55}>
        <Typography variant="body2" sx={{ fontWeight: 800 }}>
          {latestReport || "AI-assisted trend summary"}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {legacyTrends.slice(0, 3).join(" • ") || summary || emptyStateDetail}
        </Typography>
      </Stack>
    );
  }

  return (
    <Stack spacing={0.55}>
      <Typography variant="body2" sx={{ fontWeight: 800 }}>
        {emptyStateLabel}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {emptyStateDetail}
      </Typography>
    </Stack>
  );
}

function relativeTimeLabel(value?: string | null): string | null {
  if (!value) return null;
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) return null;
  const diffMs = Date.now() - timestamp.getTime();
  if (diffMs < 0) return null;
  const diffMinutes = Math.floor(diffMs / 60000);
  if (diffMinutes <= 0) return "Just now";
  if (diffMinutes === 1) return "1 minute ago";
  if (diffMinutes < 60) return `${diffMinutes} minutes ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours === 1) return "1 hour ago";
  if (diffHours < 24) return `${diffHours} hours ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 30) return `${diffDays} days ago`;
  const diffMonths = Math.floor(diffDays / 30);
  if (diffMonths === 1) return "1 month ago";
  if (diffMonths < 12) return `${diffMonths} months ago`;
  const diffYears = Math.floor(diffMonths / 12);
  return diffYears === 1 ? "1 year ago" : `${diffYears} years ago`;
}

function compactTimelineTitle(value: string) {
  return value.trim() || "Untitled";
}

function timelineTypeLabel(itemType: PatientTimelineItem["itemType"]) {
  switch (itemType) {
    case "CONSULTATION":
      return "Consultation";
    case "PRESCRIPTION":
      return "Prescription";
    case "DOCUMENT":
      return "Document";
    default:
      return itemType;
  }
}

function timelineColor(itemType: PatientTimelineItem["itemType"]) {
  switch (itemType) {
    case "CONSULTATION":
      return "info";
    case "PRESCRIPTION":
      return "secondary";
    case "DOCUMENT":
      return "primary";
    default:
      return "default";
  }
}

const DOCUMENT_FILTERS: Array<{ key: "ALL" | "LAB" | "RADIOLOGY" | "REFERRAL" | "PRESCRIPTION" | "DISCHARGE" | "OTHER"; label: string }> = [
  { key: "ALL", label: "All" },
  { key: "LAB", label: "External Lab" },
  { key: "RADIOLOGY", label: "Radiology" },
  { key: "REFERRAL", label: "Referral" },
  { key: "PRESCRIPTION", label: "Prescription" },
  { key: "DISCHARGE", label: "Discharge" },
  { key: "OTHER", label: "Other" },
];

function documentFilterKey(documentType: string): "LAB" | "RADIOLOGY" | "REFERRAL" | "PRESCRIPTION" | "DISCHARGE" | "OTHER" {
  if (["EXTERNAL_LAB_REPORT", "INTERNAL_LAB_REPORT", "LAB_REPORT"].includes(documentType)) return "LAB";
  if (["RADIOLOGY_REPORT", "X_RAY", "MRI_CT"].includes(documentType)) return "RADIOLOGY";
  if (["REFERRAL_LETTER", "REFERRAL"].includes(documentType)) return "REFERRAL";
  if (["OLD_PRESCRIPTION", "PRESCRIPTION"].includes(documentType)) return "PRESCRIPTION";
  if (["DISCHARGE_SUMMARY"].includes(documentType)) return "DISCHARGE";
  return "OTHER";
}

function prescriptionVersionTitle(row: Prescription) {
  const parts = [`v${row.versionNumber || 1}`, row.status];
  if (row.correctionReason) parts.push(row.correctionReason);
  if (row.flowType) parts.push(row.flowType.replaceAll("_", " "));
  return parts.filter(Boolean).join(" • ");
}

const HISTORY_VIEW_KEYS = ["timeline", "consultations", "prescriptions", "documents", "trends"] as const;
type HistoryViewKey = typeof HISTORY_VIEW_KEYS[number];
const HISTORY_TIMELINE_FILTER_KEYS = ["ALL", "CONSULTATIONS", "PRESCRIPTIONS", "REPORTS", "SOAP", "LAB_ORDERS"] as const;
type HistoryTimelineFilterKey = typeof HISTORY_TIMELINE_FILTER_KEYS[number];

function normalizeHistoryViewKey(value: string | null | undefined): HistoryViewKey {
  const normalized = String(value || "").trim().toLowerCase();
  if (normalized === "consultation") return "consultations";
  if (normalized === "report") return "documents";
  if (normalized === "reports") return "documents";
  if (normalized === "document") return "documents";
  if (normalized === "documents") return "documents";
  if (normalized === "trend") return "trends";
  if (HISTORY_VIEW_KEYS.includes(normalized as HistoryViewKey)) return normalized as HistoryViewKey;
  return "timeline";
}

function documentReviewStatusKey(document: ClinicalDocument): "PENDING_REVIEW" | "VERIFIED" | "NEEDS_ATTENTION" | null {
  const aiStatus = String(document.aiExtractionStatus || "").trim().toUpperCase();
  const verificationStatus = String(document.verificationStatus || "").trim().toUpperCase();
  const ocrStatus = String(document.ocrStatus || "").trim().toUpperCase();
  if (aiStatus === "FAILED" || ocrStatus === "FAILED") return "NEEDS_ATTENTION";
  if (verificationStatus === "VERIFIED" || aiStatus === "APPROVED" || aiStatus === "AI_COMPLETED") return "VERIFIED";
  if (!aiStatus || aiStatus === "PENDING" || aiStatus === "QUEUED" || aiStatus === "PROCESSING" || aiStatus === "REVIEW_REQUIRED" || verificationStatus === "UNVERIFIED") {
    return "PENDING_REVIEW";
  }
  return null;
}

function documentReviewStatusLabel(value: "PENDING_REVIEW" | "VERIFIED" | "NEEDS_ATTENTION") {
  switch (value) {
    case "PENDING_REVIEW":
      return "Pending Review";
    case "VERIFIED":
      return "Verified";
    case "NEEDS_ATTENTION":
      return "Needs Attention";
    default:
      return value;
  }
}

function documentReviewStatusColor(value: "PENDING_REVIEW" | "VERIFIED" | "NEEDS_ATTENTION"): "warning" | "success" | "error" {
  switch (value) {
    case "VERIFIED":
      return "success";
    case "NEEDS_ATTENTION":
      return "error";
    default:
      return "warning";
  }
}

function collectDocumentHighlights(document: ClinicalDocument): string[] {
  const candidates = [
    document.aiExtractionSummary,
    document.aiExtractionReviewNotes,
    document.description,
  ]
    .flatMap((value) => splitCompactList(value))
    .map((value) => compactText(value, 120))
    .filter((value) => value && !["-", "--", "N/A", "NA", "UNKNOWN", "NOT AVAILABLE"].includes(value.toUpperCase()));
  return Array.from(new Set(candidates)).slice(0, 4);
}

function buildPrescriptionGroupKey(row: Prescription) {
  return row.consultationId || row.parentPrescriptionId || row.id;
}

function timelineFilterKey(item: PatientTimelineItem): HistoryTimelineFilterKey {
  const itemType = String(item.itemType || "").trim().toUpperCase();
  const title = String(item.title || "").trim().toUpperCase();
  const subtitle = String(item.subtitle || "").trim().toUpperCase();
  if (itemType === "CONSULTATION") return "CONSULTATIONS";
  if (itemType === "PRESCRIPTION") return "PRESCRIPTIONS";
  if (itemType === "SOAP" || title.includes("SOAP") || subtitle.includes("SOAP")) return "SOAP";
  if (itemType === "LAB_ORDER" || title.includes("LAB ORDER") || subtitle.includes("LAB ORDER")) return "LAB_ORDERS";
  if (itemType === "DOCUMENT" || title.includes("REPORT") || subtitle.includes("REPORT") || title.includes("DOCUMENT") || subtitle.includes("DOCUMENT")) return "REPORTS";
  return "ALL";
}

function timelineFilterLabel(value: HistoryTimelineFilterKey) {
  switch (value) {
    case "CONSULTATIONS":
      return "Consultations";
    case "PRESCRIPTIONS":
      return "Prescriptions";
    case "REPORTS":
      return "Reports";
    case "SOAP":
      return "SOAP";
    case "LAB_ORDERS":
      return "Lab Orders";
    default:
      return "All";
  }
}

function compactText(value: string | null | undefined, max = 120) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
}

function splitCompactList(value: string | null | undefined) {
  return (value || "")
    .split(/[\n,•;|]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalizeMedicationKey(value: string | null | undefined) {
  return (value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function medicationNameMatches(medicineName: string, keywords: string[]) {
  const normalized = normalizeMedicationKey(medicineName);
  return keywords.some((keyword) => normalized.includes(keyword));
}

function isAntibioticMedicine(name: string) {
  return medicationNameMatches(name, ["amoxicillin", "azithromycin", "cef", "cefixime", "cefpodoxime", "ciprofloxacin", "ofloxacin", "doxycycline", "metronidazole", "augmentin", "levofloxacin"]);
}

function isPainkillerMedicine(name: string) {
  return medicationNameMatches(name, ["paracetamol", "acetaminophen", "ibuprofen", "diclofenac", "naproxen", "ketorolac", "aceclofenac", "nimesulide", "meloxicam"]);
}

function parseSuggestionDraftText(value: string) {
  const result: Record<string, string> = {};
  value.split(/\r?\n/).forEach((line) => {
    const match = line.match(/^([^:]+):\s*(.*)$/);
    if (match) {
      const key = match[1].trim().toLowerCase();
      const text = match[2].trim();
      if (text) result[key] = text;
    }
  });
  return result;
}

function buildPrescriptionSummaryText(input: {
  diagnosis: string;
  medicines: MedicineRow[];
  tests: TestRow[];
  advice: string;
  followUpDate: string;
  warnings: string[];
  redFlags: string[];
}) {
  const medicineLines = input.medicines
    .filter((row) => normalizeStringValue(row.medicineName))
    .slice(0, 8)
    .map((row) => [row.medicineName.trim(), row.strength?.trim(), row.dosage?.trim(), row.frequency?.trim(), row.duration?.trim()].filter(Boolean).join(" • "));
  const testLines = input.tests.map((row) => row.testName.trim()).filter(Boolean).slice(0, 6);
  const sections = [
    input.diagnosis.trim() ? `Diagnosis: ${input.diagnosis.trim()}` : "",
    medicineLines.length ? `Medicines: ${medicineLines.join("; ")}` : "",
    testLines.length ? `Tests: ${testLines.join(", ")}` : "",
    input.advice.trim() ? `Advice: ${input.advice.trim()}` : "",
    input.followUpDate ? `Follow-up: ${input.followUpDate}` : "",
    input.warnings.length ? `Warnings: ${input.warnings.join(" • ")}` : "",
    input.redFlags.length ? `Red flags: ${input.redFlags.join(" • ")}` : "",
  ].filter(Boolean);
  return sections.join("\n");
}

function isoDateFromToday(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

function parseFollowUpDateSuggestion(value: string): string | null {
  const normalized = value.trim();
  if (!normalized) return null;
  const directDateMatch = normalized.match(/\b(\d{4}-\d{2}-\d{2})\b/);
  if (directDateMatch?.[1]) return directDateMatch[1];
  const inDaysMatch = normalized.match(/\b(\d+)\s*(day|days)\b/i);
  if (inDaysMatch?.[1]) return isoDateFromToday(Number(inDaysMatch[1]));
  const inWeeksMatch = normalized.match(/\b(\d+)\s*(week|weeks)\b/i);
  if (inWeeksMatch?.[1]) return isoDateFromToday(Number(inWeeksMatch[1]) * 7);
  const inMonthsMatch = normalized.match(/\b(\d+)\s*(month|months)\b/i);
  if (inMonthsMatch?.[1]) return isoDateFromToday(Number(inMonthsMatch[1]) * 30);
  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString().slice(0, 10);
}

function createClinicalAiDraftState(kind: ClinicalAiDraftKind, title: string): ClinicalAiDraftState {
  return {
    kind,
    title,
    status: "DRAFTED",
    generatedAt: null,
    provider: null,
    model: null,
    sourceSummary: null,
    draftText: "",
    structuredData: null,
    error: null,
    selectedItems: [],
    selectedItem: null,
    traceId: null,
  };
}

function createEmptyClinicalAiDrafts() {
  return {
    chiefComplaint: createClinicalAiDraftState("chiefComplaint", "Chief Complaint"),
    symptoms: createClinicalAiDraftState("symptoms", "Symptoms"),
    diagnosis: createClinicalAiDraftState("diagnosis", "Diagnosis"),
    soap: createClinicalAiDraftState("soap", "SOAP Notes"),
    advice: createClinicalAiDraftState("advice", "Patient Advice"),
    followUp: createClinicalAiDraftState("followUp", "Follow-up"),
  } satisfies Record<ClinicalAiDraftKind, ClinicalAiDraftState>;
}

function createConsultationDocumentDraftState(kind: ConsultationDocumentationKind, title: string, documentType: ClinicalDocumentType, language: ConsultationDocumentDraftState["language"] = "ENGLISH"): ConsultationDocumentDraftState {
  return {
    kind,
    title,
    status: "DRAFTED",
    generatedAt: null,
    sourceSummary: null,
    draftText: "",
    error: null,
    documentType,
    language,
    documentId: null,
    downloadUrl: null,
    notes: null,
  };
}

function createEmptyConsultationDocumentDrafts() {
  return {
    visitSummary: createConsultationDocumentDraftState("visitSummary", "Visit Summary", "ATTACHMENT"),
    referral: createConsultationDocumentDraftState("referral", "Referral Letter", "REFERRAL_LETTER"),
    certificate: createConsultationDocumentDraftState("certificate", "Medical Certificate", "OTHER"),
  } satisfies Record<ConsultationDocumentationKind, ConsultationDocumentDraftState>;
}

function createEmptyClinicalDraftGenerationSteps() {
  const baseStep: ClinicalDraftGenerationStepState = { status: "pending", error: null, message: null };
  return {
    chiefComplaint: { ...baseStep },
    symptoms: { ...baseStep },
    diagnosis: { ...baseStep },
    soap: { ...baseStep },
    advice: { ...baseStep },
    followUp: { ...baseStep },
    prescriptionSuggestion: { ...baseStep },
    clinicalReasoning: { ...baseStep },
  } satisfies Record<ClinicalDraftGenerationStep, ClinicalDraftGenerationStepState>;
}

function makeAiAssistEntryId() {
  return `ai-assist-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function makeClinicalDraftSourceSummary(params: {
  patientAgeGender?: string | null;
  chiefComplaints?: string | null;
  symptoms?: string | null;
  diagnosis?: string | null;
  notes?: string | null;
  vitals?: string | null;
  history?: string | null;
  prescription?: string | null;
  investigations?: string | null;
}) {
  return [
    params.patientAgeGender ? `Patient: ${params.patientAgeGender}` : null,
    params.chiefComplaints ? `Chief complaint: ${compactText(params.chiefComplaints, 80)}` : null,
    params.symptoms ? `Symptoms: ${compactText(params.symptoms, 80)}` : null,
    params.diagnosis ? `Diagnosis: ${compactText(params.diagnosis, 80)}` : null,
    params.notes ? `Notes: ${compactText(params.notes, 80)}` : null,
    params.vitals ? `Vitals: ${compactText(params.vitals, 80)}` : null,
    params.history ? `History: ${compactText(params.history, 80)}` : null,
    params.prescription ? `Rx: ${compactText(params.prescription, 80)}` : null,
    params.investigations ? `Investigations: ${compactText(params.investigations, 80)}` : null,
  ].filter(Boolean).join(" • ");
}

function extractClinicalDraftText(response: AiDraftResponse): string {
  const structured = parseStructuredObject(response.structuredData);
  const candidate = typeof structured.answer === "string"
    ? structured.answer
    : typeof structured.summary === "string"
      ? structured.summary
      : typeof structured.draft === "string"
        ? structured.draft
        : "";
  return String(candidate || response.draft || response.message || "").trim();
}

function extractClinicalSymptomsDraft(response: AiDraftResponse) {
  const structured = parseStructuredObject(response.structuredData);
  const items = asStringList(structured.symptoms || structured.suggestions || structured.extractedSymptoms || structured.items || structured.answer);
  const summary = typeof structured.summary === "string" ? structured.summary.trim() : null;
  const rawText = extractClinicalDraftText(response);
  return {
    items: items.length ? items : rawText.split(/\n|,|•/).map((value) => value.trim()).filter(Boolean),
    summary: summary || rawText || null,
    rawText,
  };
}

function extractClinicalSoapDraft(response: AiDraftResponse) {
  const structured = parseStructuredObject(response.structuredData);
  const subjective = String(structured.subjective ?? structured.s ?? structured["Subjective"] ?? "").trim();
  const objective = String(structured.objective ?? structured.o ?? structured["Objective"] ?? "").trim();
  const assessment = String(structured.assessment ?? structured.a ?? structured["Assessment"] ?? "").trim();
  const plan = String(structured.plan ?? structured.p ?? structured["Plan"] ?? "").trim();
  const rawText = extractClinicalDraftText(response);
  return {
    subjective: subjective || null,
    objective: objective || null,
    assessment: assessment || null,
    plan: plan || null,
    rawText,
  };
}

function isPlaceholderSoapValue(value: string | null | undefined) {
  const normalized = String(value || "").trim().toLowerCase();
  return !normalized || ["-", "--", "n/a", "na", "not available", "not documented"].includes(normalized);
}

function hasMeaningfulSoapDraft(sections: { subjective: string | null; objective: string | null; assessment: string | null; plan: string | null }) {
  return [sections.subjective, sections.objective, sections.assessment, sections.plan].some((section) => !isPlaceholderSoapValue(section));
}

function hasAnySoapDraftSection(sections: { subjective: string | null; objective: string | null; assessment: string | null; plan: string | null }) {
  return [sections.subjective, sections.objective, sections.assessment, sections.plan].some((section) => Boolean(String(section || "").trim()));
}

function summarizeSoapDraftSections(sections: { subjective: string | null; objective: string | null; assessment: string | null; plan: string | null }) {
  return {
    subjectivePresent: Boolean(String(sections.subjective || "").trim()),
    objectivePresent: Boolean(String(sections.objective || "").trim()),
    assessmentPresent: Boolean(String(sections.assessment || "").trim()),
    planPresent: Boolean(String(sections.plan || "").trim()),
  };
}

function formatSoapVitalsSummary(vitals: {
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  pulseRate: number | null;
  respiratoryRate: number | null;
  temperature: number | null;
  temperatureUnit?: string | null;
  spo2: number | null;
  bmi: number | null;
  randomBloodSugar?: number | null;
  painScore?: number | null;
}) {
  const parts: string[] = [];
  if (vitals.bloodPressureSystolic != null && vitals.bloodPressureDiastolic != null) {
    parts.push(`BP ${vitals.bloodPressureSystolic}/${vitals.bloodPressureDiastolic}`);
  }
  if (vitals.pulseRate != null) {
    parts.push(`Pulse ${vitals.pulseRate}`);
  }
  if (vitals.respiratoryRate != null) {
    parts.push(`Resp ${vitals.respiratoryRate}`);
  }
  if (vitals.temperature != null) {
    parts.push(`Temp ${vitals.temperature}${vitals.temperatureUnit ? ` ${vitals.temperatureUnit}` : ""}`);
  }
  if (vitals.spo2 != null) {
    parts.push(`SpO2 ${vitals.spo2}`);
  }
  if (vitals.bmi != null) {
    parts.push(`BMI ${vitals.bmi.toFixed(1)}`);
  }
  if (vitals.randomBloodSugar != null) {
    parts.push(`RBS ${vitals.randomBloodSugar}`);
  }
  if (vitals.painScore != null) {
    parts.push(`Pain ${vitals.painScore}/10`);
  }
  return parts.length ? parts.join(", ") : null;
}

function soapFieldCharCount(value: string | null | undefined) {
  return value ? value.length : 0;
}

function soapFieldPresent(value: string | null | undefined) {
  return Boolean(String(value || "").trim());
}

function extractClinicalFollowUpDraft(response: AiDraftResponse) {
  const structured = parseStructuredObject(response.structuredData);
  const followUpDate = String(structured.followUpDate ?? structured.followUp ?? structured.followUpOn ?? structured.date ?? "").trim();
  const interval = String(structured.followUpInterval ?? structured.interval ?? structured.schedule ?? "").trim();
  const reason = String(structured.reason ?? structured.reasoning ?? structured.note ?? "").trim();
  const rawText = extractClinicalDraftText(response);
  const resolvedDate = parseFollowUpDateSuggestion(followUpDate || interval || rawText);
  return {
    followUpDate: resolvedDate,
    interval: interval || (followUpDate && !resolvedDate ? followUpDate : null),
    reason: reason || null,
    rawText,
  };
}

function normalizeDraftedTextWithSections(value: string, sections: Array<[string, string | null]>) {
  const parts = sections
    .map(([label, section]) => {
      const content = (section || "").trim();
      return content ? `${label}: ${content}` : "";
    })
    .filter(Boolean);
  return [value.trim(), ...parts].filter(Boolean).join("\n");
}

function aiAssistActionLabel(action: AiAssistAction) {
  switch (action) {
    case "ask":
      return "Clinical chat";
    case "diagnosis":
      return "Clinical reasoning";
    case "notes":
      return "SOAP notes";
    case "prescription":
      return "Prescription template";
    case "instructions":
      return "Patient instructions";
    case "summary":
      return "Clinical summary";
    case "red_flags":
      return "Red flags";
    case "drug_safety":
      return "Drug safety";
    case "tests":
      return "Recommended tests";
    default:
      return action;
  }
}

function clinicalDraftKindLabel(kind: ClinicalAiDraftKind) {
  switch (kind) {
    case "chiefComplaint":
      return "Chief Complaint";
    case "symptoms":
      return "Symptoms";
    case "diagnosis":
      return "Diagnosis";
    case "soap":
      return "SOAP";
    case "advice":
      return "Advice";
    case "followUp":
      return "Follow-up";
    default:
      return kind;
  }
}

function clinicalDraftGenerationStepLabel(step: ClinicalDraftGenerationStep) {
  switch (step) {
    case "prescriptionSuggestion":
      return "Prescription suggestion";
    case "clinicalReasoning":
      return "Clinical reasoning";
    default:
      return clinicalDraftKindLabel(step);
  }
}

function summarizePrescriptionDraft(medicines: PrescriptionFormState["medicines"]) {
  const lines = medicines
    .filter((row) => row.medicineName.trim())
    .slice(0, 8)
    .map((row) => {
      const parts = [row.medicineName.trim(), row.strength?.trim(), row.dosage?.trim(), row.frequency?.trim(), row.duration?.trim()].filter(Boolean);
      return parts.join(" • ");
    });
  return lines.join("; ").trim();
}

function summarizeLabOrdersForAi(orders: LabOrder[]) {
  const lines = orders.slice(0, 5).map((order) => {
    const tests = order.items.map((item) => item.testName).filter(Boolean).slice(0, 6).join(", ");
    return `${order.orderNumber}${tests ? `: ${tests}` : ""}`;
  });
  return lines.join(" | ").trim();
}

function SectionCard({
  id,
  title,
  subtitle,
  icon,
  primaryAction,
  expanded,
  onToggle,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  primaryAction?: React.ReactNode;
  expanded: boolean;
  onToggle: (id: string) => void;
  children: React.ReactNode;
}) {
  return (
    <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
      <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
        <Stack spacing={0.75}>
          <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
            <Box sx={{ display: "flex", alignItems: "flex-start", gap: 0.85, minWidth: 0 }}>
              {icon ? (
                <Box sx={{ display: "grid", placeItems: "center", width: 28, height: 28, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main", flexShrink: 0, mt: 0.1 }}>
                  {icon}
                </Box>
              ) : null}
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 950, lineHeight: 1.15 }}>
                  {title}
                </Typography>
                {subtitle ? (
                  <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.1, mt: 0.1 }}>
                    {subtitle}
                  </Typography>
                ) : null}
              </Box>
            </Box>
            <Button
              type="button"
              size="small"
              variant="text"
              aria-label={`${expanded ? "Collapse" : "Expand"} ${title}`}
              onClick={() => onToggle(id)}
              sx={{ flexShrink: 0, mt: -0.2 }}
            >
              {expanded ? "Collapse" : "Expand"}
            </Button>
          </Box>
          {primaryAction ? <Box>{primaryAction}</Box> : null}
          <Collapse in={expanded} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
            <Box sx={{ pt: 0.25 }}>{children}</Box>
          </Collapse>
        </Stack>
      </CardContent>
    </Card>
  );
}

function QuickChipGroup({
  chips,
  onPick,
  color,
  disabled = false,
}: {
  chips: string[];
  onPick: (value: string) => void;
  color?: "default" | "primary" | "secondary";
  disabled?: boolean;
}) {
  return (
    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
      {chips.map((chip) => (
        <Chip key={chip} size="small" clickable={!disabled} disabled={disabled} color={color} variant="outlined" label={chip} onClick={() => onPick(chip)} />
      ))}
    </Stack>
  );
}

function medicineSuggestionToDraftText(item: AiMedicineSuggestionItem) {
  return [
    `Medicine: ${item.medicine}`,
    item.dose ? `Dose: ${item.dose}` : "",
    item.frequency ? `Frequency: ${item.frequency}` : "",
    item.duration ? `Duration: ${item.duration}` : "",
    item.reason ? `Reason: ${item.reason}` : "",
    item.safetyNote ? `Safety: ${item.safetyNote}` : "",
  ].filter(Boolean).join("\n");
}

function PrescriptionSuggestionDraftCard({
  item,
  generatedAt,
  disabled,
  onAcceptSuggestion,
  onRejectSuggestion,
}: {
  item: AiMedicineSuggestionItem;
  generatedAt: string | null;
  disabled: boolean;
  onAcceptSuggestion: (item: AiMedicineSuggestionItem, draftText: string) => void;
  onRejectSuggestion: (item: AiMedicineSuggestionItem) => void;
}) {
  const initialDraft = React.useMemo(() => medicineSuggestionToDraftText(item), [item]);
  const [draftText, setDraftText] = React.useState(initialDraft);
  const [status, setStatus] = React.useState<ClinicalAiDraftStatus>("DRAFTED");

  React.useEffect(() => {
    setDraftText(initialDraft);
    setStatus("DRAFTED");
  }, [initialDraft]);

  const parsed = React.useMemo(() => parseSuggestionDraftText(draftText), [draftText]);
  const viewText = draftText.trim() || initialDraft;

  return (
    <ClinicalAiDraftCard
      title={item.medicine}
      status={status}
      generatedAt={generatedAt}
      draftText={viewText}
      acceptLabel="Accept"
      editLabel="Edit"
      rejectLabel="Reject"
      copyLabel="Copy"
      acceptDisabled={disabled}
      editDisabled={disabled}
      rejectDisabled={disabled}
      copyDisabled={!viewText.trim()}
      onAccept={() => {
        onAcceptSuggestion({ ...item, ...parsed }, viewText);
        setStatus("ACCEPTED");
      }}
      onEdit={(nextText) => {
        setDraftText(nextText);
        setStatus("EDITED");
      }}
      onReject={() => {
        setStatus("REJECTED");
        onRejectSuggestion(item);
      }}
      onCopy={() => void navigator.clipboard.writeText(viewText)}
    >
      <Stack spacing={0.35}>
        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
          {item.dose ? <Chip size="small" variant="outlined" label={`Dose ${item.dose}`} /> : null}
          {item.frequency ? <Chip size="small" variant="outlined" label={item.frequency} /> : null}
          {item.duration ? <Chip size="small" variant="outlined" label={item.duration} /> : null}
          {item.safetyNote ? <Chip size="small" color="warning" variant="outlined" label="Safety note" /> : null}
        </Stack>
        {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.25 }}>Reason: {item.reason}</Typography> : null}
        {item.safetyNote ? <Typography variant="caption" color="warning.main" sx={{ lineHeight: 1.25 }}>Safety: {item.safetyNote}</Typography> : null}
      </Stack>
    </ClinicalAiDraftCard>
  );
}

const AI_SAFETY_NOTE = "AI suggestions are assistive. Doctor must verify before use.";
const AIVA_QUICK_PROMPTS = [
  "What else should I ask?",
  "Explain diagnosis",
  "Simplify for patient",
  "Suggest tests",
  "Draft referral",
  "Drug safety",
];

const AI_ASSIST_PROMPT_GROUPS: Array<{ label: string; prompts: string[] }> = [
  {
    label: "Clinical reasoning",
    prompts: ["What else should I ask?", "Explain diagnosis"],
  },
  {
    label: "Investigations",
    prompts: ["Suggest tests"],
  },
  {
    label: "Prescription and safety",
    prompts: ["Drug safety"],
  },
  {
    label: "Patient communication",
    prompts: ["Simplify for patient", "Draft referral"],
  },
];

// Final UX polish for R2 Consultation Workspace. Keep layout stable unless a functional defect requires change.

function numericTrendLabel(current: number | null | undefined, previous: number | null | undefined) {
  if (current == null || previous == null) return "No previous";
  if (current > previous) return "↑ Higher than previous";
  if (current < previous) return "↓ Lower than previous";
  return "→ Stable";
}

function formatContextValue(value: string | number | null | undefined) {
  if (value == null || value === "") return null;
  return String(value);
}

export default function ConsultationWorkspacePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const consultationId = params.id || "";
  const appointmentId = searchParams.get("appointmentId") || "";

  const [consultation, setConsultation] = React.useState<Consultation | null>(null);
  const [appointment, setAppointment] = React.useState<Appointment | null>(null);
  const [patient, setPatient] = React.useState<PatientDetail | null>(null);
  const [previousPrescriptions, setPreviousPrescriptions] = React.useState<Prescription[]>([]);
  const [clinicalDocuments, setClinicalDocuments] = React.useState<ClinicalDocument[]>([]);
  const [documentFilter, setDocumentFilter] = React.useState<"ALL" | "LAB" | "RADIOLOGY" | "REFERRAL" | "PRESCRIPTION" | "DISCHARGE" | "OTHER">("ALL");
  const [documentReviewFilter, setDocumentReviewFilter] = React.useState<"ALL" | "PENDING_REVIEW" | "VERIFIED" | "NEEDS_ATTENTION">("ALL");
  const [uploadDialogOpen, setUploadDialogOpen] = React.useState(false);
  const [clinicalContext, setClinicalContext] = React.useState<ClinicalContextResponse | null>(null);
  const [clinicalContextLoading, setClinicalContextLoading] = React.useState(false);
  const [clinicalContextError, setClinicalContextError] = React.useState<string | null>(null);
  const [patientTimeline, setPatientTimeline] = React.useState<PatientTimelineItem[]>([]);
  const [prescriptionHistory, setPrescriptionHistory] = React.useState<Prescription[]>([]);
  const [prescription, setPrescription] = React.useState<Prescription | null>(null);
  const [medicineCatalog, setMedicineCatalog] = React.useState<Medicine[]>([]);
  const [labTests, setLabTests] = React.useState<LabTest[]>([]);
  const [labOrders, setLabOrders] = React.useState<LabOrder[]>([]);

  const [consultationForm, setConsultationForm] = React.useState<ConsultationFormState>(emptyConsultationForm());
  const [consultationSoap, setConsultationSoap] = React.useState<ConsultationSoapNote | null>(null);
  const [consultationSoapForm, setConsultationSoapForm] = React.useState<ConsultationSoapFormState>(emptyConsultationSoapForm());
  const [prescriptionForm, setPrescriptionForm] = React.useState<PrescriptionFormState>(emptyPrescriptionForm());

  const [customSymptom, setCustomSymptom] = React.useState("");
  const [customDiagnosis, setCustomDiagnosis] = React.useState("");
  const [customTest, setCustomTest] = React.useState("");
  const [labOrderDialogOpen, setLabOrderDialogOpen] = React.useState(false);
  const [labOrderReviewOpen, setLabOrderReviewOpen] = React.useState(false);
  const [labOrderTestIds, setLabOrderTestIds] = React.useState<string[]>([]);
  const [labOrderNotes, setLabOrderNotes] = React.useState("");
  const [labTestSearch, setLabTestSearch] = React.useState("");
  const [debouncedLabTestSearch, setDebouncedLabTestSearch] = React.useState("");
  const [labTestCategoryFilter, setLabTestCategoryFilter] = React.useState("All");
  const [labOrderAiPreparation, setLabOrderAiPreparation] = React.useState<LabOrderAiPreparation | null>(null);
  const [labOrderSaving, setLabOrderSaving] = React.useState(false);
  const [selectedLabOrderId, setSelectedLabOrderId] = React.useState<string | null>(null);
  const [clinicalReasoningInvestigationHighlightKey, setClinicalReasoningInvestigationHighlightKey] = React.useState<string | null>(null);
  const [medicineSearch, setMedicineSearch] = React.useState("");
  const [correctionReason, setCorrectionReason] = React.useState("Same-day correction");
  const [activeTab, setActiveTab] = React.useState(() => consultationTabKeyToIndex(searchParams.get("tab")));
  const historyView = normalizeHistoryViewKey(searchParams.get("historyView"));
  const [historyTimelineFilter, setHistoryTimelineFilter] = React.useState<HistoryTimelineFilterKey>("ALL");
  const [expanded, setExpanded] = React.useState<Record<string, boolean>>({
    complaint: true,
    symptoms: true,
    diagnosis: true,
    notes: false,
    advice: false,
    followup: true,
    history: false,
    "patient-history-summary": false,
  });
  const [historyTimelineLimit, setHistoryTimelineLimit] = React.useState(6);
  const [historyConsultationLimit, setHistoryConsultationLimit] = React.useState(3);
  const [historyPrescriptionLimit, setHistoryPrescriptionLimit] = React.useState(3);
  const [historyDocumentLimit, setHistoryDocumentLimit] = React.useState(6);

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [openingCorrectionDraft, setOpeningCorrectionDraft] = React.useState(false);
  const [autosaveStatus, setAutosaveStatus] = React.useState<AutosaveStatus>("idle");
  const [aiBusy, setAiBusy] = React.useState(false);
  const [aiActiveAction, setAiActiveAction] = React.useState<AiAssistAction | null>(null);
  const [clinicalAiActiveDraft, setClinicalAiActiveDraft] = React.useState<ClinicalAiDraftKind | null>(null);
  const selectedDiagnosisEntries = React.useMemo(
    () => splitClinicalTokenEntries(consultationForm.diagnosis),
    [consultationForm.diagnosis]
  );
  const prescriptionDiagnosisSnapshotEntries = React.useMemo(
    () => splitClinicalTokenEntries(prescriptionForm.diagnosisSnapshot),
    [prescriptionForm.diagnosisSnapshot]
  );
  const handleRemoveDiagnosisEntry = React.useCallback((diagnosisEntry: string) => {
    setConsultationForm((current) => ({
      ...current,
      diagnosis: removeClinicalTokenEntry(current.diagnosis, diagnosisEntry),
    }));
  }, []);
  const [aiAssistEntries, setAiAssistEntries] = React.useState<AiAssistEntry[]>([]);
  const [aiAvailable, setAiAvailable] = React.useState(true);
  const [aiStatusMessage, setAiStatusMessage] = React.useState<string | null>(null);
  const [clinicalSummary, setClinicalSummary] = React.useState<AiDraftResponse | null>(null);
  const [aiDiagnosisSuggestion, setAiDiagnosisSuggestion] = React.useState<string | null>(null);
  const [aiDiagnosisSummary, setAiDiagnosisSummary] = React.useState<string | null>(null);
  const [aiDiagnosisItems, setAiDiagnosisItems] = React.useState<AiSuggestionItem[]>([]);
  const [aiDiagnosisUnstructured, setAiDiagnosisUnstructured] = React.useState(false);
  const [aiDiagnosisProvider, setAiDiagnosisProvider] = React.useState<string | null>(null);
  const [aiPrescriptionSuggestion, setAiPrescriptionSuggestion] = React.useState<string | null>(null);
  const [aiPrescriptionItems, setAiPrescriptionItems] = React.useState<AiMedicineSuggestionItem[]>([]);
  const [aiPrescriptionUnstructured, setAiPrescriptionUnstructured] = React.useState(false);
  const [aiPrescriptionProvider, setAiPrescriptionProvider] = React.useState<string | null>(null);
  const [prescriptionSuggestionGeneratedAt, setPrescriptionSuggestionGeneratedAt] = React.useState<string | null>(null);
  const [prescriptionInstructionsLanguage, setPrescriptionInstructionsLanguage] = React.useState<"ENGLISH" | "HINDI" | "MARATHI">("ENGLISH");
  const [prescriptionInstructionsDraft, setPrescriptionInstructionsDraft] = React.useState<AiDraftResponse | null>(null);
  const [prescriptionInstructionsLoading, setPrescriptionInstructionsLoading] = React.useState(false);
  const [prescriptionInstructionsGeneratedAt, setPrescriptionInstructionsGeneratedAt] = React.useState<string | null>(null);
  const [prescriptionSummaryDraft, setPrescriptionSummaryDraft] = React.useState<string>("");
  const [alternativeMedicineRowId, setAlternativeMedicineRowId] = React.useState<string | null>(null);
  const [clinicalAiDrafts, setClinicalAiDrafts] = React.useState<Record<ClinicalAiDraftKind, ClinicalAiDraftState>>(() => createEmptyClinicalAiDrafts());
  const [clinicalDraftGenerationSteps, setClinicalDraftGenerationSteps] = React.useState<Record<ClinicalDraftGenerationStep, ClinicalDraftGenerationStepState>>(() => createEmptyClinicalDraftGenerationSteps());
  const [savedAiSummary, setSavedAiSummary] = React.useState<ConsultationAiSummary | null>(null);
  const [aivaClinicalQuestion, setAivaClinicalQuestion] = React.useState("");
  const [aivaChatMessages, setAivaChatMessages] = React.useState<AivaChatMessage[]>([]);
  const [aivaQuestionSubmitting, setAivaQuestionSubmitting] = React.useState(false);
  const [aivaChatDetailsOpen, setAivaChatDetailsOpen] = React.useState(false);
  const [aiClinicalSummaryExpanded, setAiClinicalSummaryExpanded] = React.useState(true);
  const [medicineCatalogWarning, setMedicineCatalogWarning] = React.useState<string | null>(null);
  const [viewerDocument, setViewerDocument] = React.useState<ClinicalDocument | null>(null);
  const [viewerUrl, setViewerUrl] = React.useState<string | null>(null);
  const [selectedPrescriptionVersionId, setSelectedPrescriptionVersionId] = React.useState<string | null>(null);
  const [invalidMedicineRowIds, setInvalidMedicineRowIds] = React.useState<string[]>([]);
  const [selectedRxTemplateKey, setSelectedRxTemplateKey] = React.useState<string | null>(null);
  const [completeConsultationDialogOpen, setCompleteConsultationDialogOpen] = React.useState(false);
  const [readinessDetailsOpen, setReadinessDetailsOpen] = React.useState(false);
  const [timelinePreviewExpanded, setTimelinePreviewExpanded] = React.useState(false);
  const [diagnosisReasoningOpen, setDiagnosisReasoningOpen] = React.useState(false);
  const [diagnosisItemsExpanded, setDiagnosisItemsExpanded] = React.useState(false);
  const [showStickyConsultationProgress, setShowStickyConsultationProgress] = React.useState(false);
  const [prescriptionSafetyDetailsOpen, setPrescriptionSafetyDetailsOpen] = React.useState(false);
  const [prescriptionComparisonDetailsOpen, setPrescriptionComparisonDetailsOpen] = React.useState(false);
  const [prescriptionIntelligenceTab, setPrescriptionIntelligenceTab] = React.useState<"safety" | "comparison" | "instructions" | "summary">("safety");
  const [prescriptionIntelligenceExpanded, setPrescriptionIntelligenceExpanded] = React.useState(false);
  const [medicationSafetyEvaluation, setMedicationSafetyEvaluation] = React.useState<MedicationSafetyEvaluationResult | null>(null);
  const [medicationSafetyLoading, setMedicationSafetyLoading] = React.useState(false);
  const [medicationSafetyError, setMedicationSafetyError] = React.useState<string | null>(null);
  const [medicationSafetyReview, setMedicationSafetyReview] = React.useState<MedicationSafetyReviewResponse | null>(null);
  const [medicationSafetyReviewLoading, setMedicationSafetyReviewLoading] = React.useState(false);
  const [medicationSafetyReviewError, setMedicationSafetyReviewError] = React.useState<string | null>(null);
  const [medicationSafetyReviewSubmitting, setMedicationSafetyReviewSubmitting] = React.useState(false);
  const [medicationSafetyCheckRunning, setMedicationSafetyCheckRunning] = React.useState(false);
  const [medicationSafetyReviewDraft, setMedicationSafetyReviewDraft] = React.useState<MedicationSafetyReviewDraftState>({ findings: {} });
  const activeMedicationSafetyReview = React.useMemo(
    () => normalizeMedicationSafetyReview(medicationSafetyReview, medicationSafetyEvaluation),
    [medicationSafetyEvaluation, medicationSafetyReview],
  );
  const medicationSafetyReviewFinalized = activeMedicationSafetyReview?.decisionStatus === "FINALIZED" || prescription?.status === "FINALIZED";
  const [clinicalReasoningAskedMissingInfo, setClinicalReasoningAskedMissingInfo] = React.useState<Record<string, boolean>>({});
  const [clinicalReasoningLoadingStepIndex, setClinicalReasoningLoadingStepIndex] = React.useState(0);
  const [clinicalReasoningSectionsOpen, setClinicalReasoningSectionsOpen] = React.useState<Record<ClinicalReasoningSectionKey, boolean>>({
    longitudinalContext: true,
    primaryDiagnosis: true,
    differentials: true,
    evidence: false,
    missingInformation: false,
    redFlags: false,
    recommendedTests: false,
    safetyNotes: false,
    debug: false,
  });
  const [consultationDocumentDrafts, setConsultationDocumentDrafts] = React.useState<Record<ConsultationDocumentationKind, ConsultationDocumentDraftState>>(() => createEmptyConsultationDocumentDrafts());
  const [consultationDocumentationLanguage, setConsultationDocumentationLanguage] = React.useState<"ENGLISH" | "HINDI" | "MARATHI">("ENGLISH");
  const [consultationReferralPriority, setConsultationReferralPriority] = React.useState<ConsultationDocumentPriority>("ROUTINE");
  const [consultationReferralDestination, setConsultationReferralDestination] = React.useState("");
  const [consultationCertificateType, setConsultationCertificateType] = React.useState<"MEDICAL" | "FITNESS" | "SICK_LEAVE" | "RETURN_TO_WORK">("MEDICAL");
  const [followUpPlanNotes, setFollowUpPlanNotes] = React.useState("");
  const [consultationDocumentationSaving, setConsultationDocumentationSaving] = React.useState(false);
  const [consultationCompletionExpanded, setConsultationCompletionExpanded] = React.useState(false);
  const [consultationPackageGenerating, setConsultationPackageGenerating] = React.useState(false);
  const [completionValidationOpen, setCompletionValidationOpen] = React.useState(false);
  const [consultationAuditShareLog, setConsultationAuditShareLog] = React.useState<string[]>([]);
  const [documentRowBusyId, setDocumentRowBusyId] = React.useState<string | null>(null);
  const [workspaceConfirmation, setWorkspaceConfirmation] = React.useState<PrescriptionDialogState>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const consultationRef = React.useRef<Consultation | null>(null);
  const prescriptionRef = React.useRef<Prescription | null>(null);
  const consultationFormRef = React.useRef(consultationForm);
  const consultationSoapFormRef = React.useRef(consultationSoapForm);
  const prescriptionFormRef = React.useRef(prescriptionForm);
  const clinicalAiDraftsRef = React.useRef(clinicalAiDrafts);
  const consultationDocumentDraftsRef = React.useRef(consultationDocumentDrafts);
  const aiDiagnosisSuggestionRef = React.useRef(aiDiagnosisSuggestion);
  const aiDiagnosisItemsRef = React.useRef(aiDiagnosisItems);
  const aiPrescriptionSuggestionRef = React.useRef(aiPrescriptionSuggestion);
  const aiPrescriptionItemsRef = React.useRef(aiPrescriptionItems);
  const clinicalReasoningReviewRef = React.useRef<HTMLDivElement | null>(null);
  const consultationCompletionRef = React.useRef<HTMLDivElement | null>(null);
  const patientIntelligenceRef = React.useRef<HTMLDivElement | null>(null);
  const labOrderWorkflowRef = React.useRef<HTMLDivElement | null>(null);
  const adviceSectionRef = React.useRef<HTMLDivElement | null>(null);
  const adviceInputRef = React.useRef<HTMLTextAreaElement | HTMLInputElement | null>(null);
  const soapSubjectiveInputRef = React.useRef<HTMLTextAreaElement | HTMLInputElement | null>(null);
  const prescriptionFollowUpInputRef = React.useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);
  const clinicalReasoningSectionRefs = React.useRef<Record<Exclude<ClinicalReasoningSectionKey, "debug">, HTMLDivElement | null>>({
    longitudinalContext: null,
    primaryDiagnosis: null,
    differentials: null,
    evidence: null,
    missingInformation: null,
    redFlags: null,
    recommendedTests: null,
    safetyNotes: null,
  });
  const savedConsultationSnapshotRef = React.useRef(serializeConsultationForm(emptyConsultationForm()));
  const savedConsultationSoapSnapshotRef = React.useRef(serializeConsultationSoapForm(emptyConsultationSoapForm()));
  const savedPrescriptionSnapshotRef = React.useRef(serializePrescriptionForm(emptyPrescriptionForm()));
  const autosaveTimerRef = React.useRef<number | null>(null);
  const autosaveRetryTimerRef = React.useRef<number | null>(null);
  const autosaveInFlightRef = React.useRef(false);
  const autosavePromiseRef = React.useRef<Promise<Consultation | null> | null>(null);
  const savingRef = React.useRef(false);
  const hydratedConsultationIdRef = React.useRef<string | null>(null);
  const viewportRestoreRef = React.useRef<{ x: number; y: number } | null>(null);
  const consultationHeaderRef = React.useRef<HTMLDivElement | null>(null);
  const consultationChecklistRef = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    consultationRef.current = consultation;
  }, [consultation]);

  React.useEffect(() => {
    prescriptionRef.current = prescription;
  }, [prescription]);

  const refreshMedicationSafety = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation?.id) {
      setMedicationSafetyEvaluation(null);
      setMedicationSafetyReview(null);
      setMedicationSafetyError(null);
      setMedicationSafetyReviewError(null);
      return;
    }
    setMedicationSafetyLoading(true);
    setMedicationSafetyReviewLoading(true);
    try {
      const selectedHistoricalPrescriptionId = prescription && !isEditablePrescriptionStatus(prescription.status)
        && selectedPrescriptionVersionId && prescription?.id && selectedPrescriptionVersionId !== prescription.id
        ? selectedPrescriptionVersionId
        : null;
      const result = selectedHistoricalPrescriptionId
        ? await getMedicationSafetyEvaluationForPrescription(auth.accessToken, auth.tenantId, consultation.id, selectedHistoricalPrescriptionId)
        : await getMedicationSafetyEvaluation(auth.accessToken, auth.tenantId, consultation.id);
      setMedicationSafetyEvaluation(result);
      setMedicationSafetyError(null);
      try {
        const review = selectedHistoricalPrescriptionId
          ? await getMedicationSafetyReviewForPrescription(auth.accessToken, auth.tenantId, consultation.id, selectedHistoricalPrescriptionId)
          : await getMedicationSafetyReview(auth.accessToken, auth.tenantId, consultation.id);
        const activeReview = normalizeMedicationSafetyReview(review, result);
        setMedicationSafetyReview(activeReview);
        setMedicationSafetyReviewError(null);
        setMedicationSafetyReviewDraft({
          findings: activeReview
            ? Object.fromEntries((activeReview.findingReviews || []).map((finding) => [finding.findingId, {
                findingId: finding.findingId,
                ruleCode: finding.ruleCode,
                acknowledged: finding.acknowledged,
                overrideApplied: finding.overrideApplied,
                reasonCode: finding.reasonCode,
                reasonText: finding.reasonText,
              }]))
            : {},
        });
      } catch (reviewErr) {
        setMedicationSafetyReview(null);
        setMedicationSafetyReviewError(reviewErr instanceof Error ? reviewErr.message : "Medication safety review unavailable");
        setMedicationSafetyReviewDraft({ findings: {} });
      }
    } catch (err) {
      setMedicationSafetyEvaluation(null);
      setMedicationSafetyError(err instanceof Error ? err.message : "Medication safety evaluation unavailable");
      setMedicationSafetyReview(null);
      setMedicationSafetyReviewError(null);
      setMedicationSafetyReviewDraft({ findings: {} });
    } finally {
      setMedicationSafetyLoading(false);
      setMedicationSafetyReviewLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, consultation?.id, prescription?.id, selectedPrescriptionVersionId]);

  React.useEffect(() => {
    if (!consultation?.id || !prescription) {
      setMedicationSafetyEvaluation(null);
      setMedicationSafetyReview(null);
      setMedicationSafetyError(null);
      setMedicationSafetyReviewError(null);
      setMedicationSafetyReviewDraft({ findings: {} });
      return;
    }
    void refreshMedicationSafety();
  }, [consultation?.id, prescription?.id, prescription?.updatedAt, selectedPrescriptionVersionId, refreshMedicationSafety]);

  const updateMedicationSafetyFindingReview = React.useCallback((findingId: string, patch: Partial<MedicationSafetyFindingReviewDecision>) => {
    if (medicationSafetyReviewFinalized) {
      return;
    }
    setMedicationSafetyReviewDraft((current) => {
      const existing = current.findings[findingId] || {
        findingId,
        ruleCode: null,
        acknowledged: false,
        overrideApplied: false,
        reasonCode: null,
        reasonText: null,
      };
      return {
        findings: {
          ...current.findings,
          [findingId]: {
            ...existing,
            ...patch,
          },
        },
      };
    });
  }, [medicationSafetyReviewFinalized]);

  const saveMedicationSafetyReview = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation?.id || !medicationSafetyEvaluation || medicationSafetyReviewFinalized) {
      return;
    }
    setMedicationSafetyReviewSubmitting(true);
    try {
      const requestFindings = medicationSafetyEvaluation.findings.map((finding) => {
        const draft = medicationSafetyReviewDraft.findings[finding.findingId];
        return {
          findingId: finding.findingId,
          ruleCode: finding.ruleCode,
          acknowledged: draft?.acknowledged ?? false,
          overrideApplied: draft?.overrideApplied ?? false,
          reasonCode: draft?.reasonCode?.trim() || null,
          reasonText: draft?.reasonText?.trim() || null,
        };
      });
      const review = await submitMedicationSafetyReview(auth.accessToken, auth.tenantId, consultation.id, {
        evaluationId: medicationSafetyEvaluation.evaluationId,
        prescriptionHash: activeMedicationSafetyReview?.prescriptionHash ?? null,
        patientContextHash: activeMedicationSafetyReview?.patientContextHash ?? null,
        rulesVersion: activeMedicationSafetyReview?.rulesVersion ?? medicationSafetyEvaluation.rulesVersion,
        findings: requestFindings,
      });
      setMedicationSafetyReview(review);
      setMedicationSafetyReviewError(null);
      setMedicationSafetyReviewDraft({
        findings: Object.fromEntries((review.findingReviews || []).map((finding: MedicationSafetyFindingReviewStatus) => [finding.findingId, {
          findingId: finding.findingId,
          ruleCode: finding.ruleCode,
          acknowledged: finding.acknowledged,
          overrideApplied: finding.overrideApplied,
          reasonCode: finding.reasonCode,
          reasonText: finding.reasonText,
        }])),
      });
    } catch (err) {
      setMedicationSafetyReviewError(err instanceof Error ? err.message : "Medication safety review could not be saved");
    } finally {
      setMedicationSafetyReviewSubmitting(false);
    }
  }, [activeMedicationSafetyReview, auth.accessToken, auth.tenantId, consultation?.id, medicationSafetyEvaluation, medicationSafetyReviewDraft.findings, medicationSafetyReviewFinalized]);

  const runMedicationSafety = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation?.id || medicationSafetyCheckRunning) {
      return;
    }
    if (!prescription?.id) {
      setMedicationSafetyError("Save the prescription before running Medication Safety.");
      setPrescriptionSafetyDetailsOpen(true);
      return;
    }
    setMedicationSafetyCheckRunning(true);
    setInfo(null);
    setMedicationSafetyError(null);
    try {
      await runMedicationSafetyCheck(auth.accessToken, auth.tenantId, consultation.id);
      await refreshMedicationSafety();
      setInfo("Medication safety check completed");
    } catch (err) {
      if (err instanceof ApiClientError && err.code === "PRESCRIPTION_NOT_SAVED") {
        setMedicationSafetyError("Please save the prescription before running Medication Safety.");
        setPrescriptionSafetyDetailsOpen(true);
      } else {
        setMedicationSafetyError(err instanceof Error ? err.message : "Medication safety check could not be run");
      }
    } finally {
      setMedicationSafetyCheckRunning(false);
    }
  }, [auth.accessToken, auth.tenantId, consultation?.id, medicationSafetyCheckRunning, prescription?.id, refreshMedicationSafety]);

  const refreshClinicalArtifacts = React.useCallback(async (
    patientId: string,
    consultationValue: Consultation | null,
    options?: { includeContext?: boolean; keepLoadingState?: boolean },
  ) => {
    if (!auth.accessToken || !auth.tenantId || !patientId) {
      return;
    }
    const includeContext = options?.includeContext ?? true;
    if (options?.keepLoadingState !== false) {
      setClinicalContextLoading(true);
    }
    try {
      const [documents, timelineRows, context] = await Promise.all([
        getPatientDocuments(auth.accessToken, auth.tenantId, patientId),
        getPatientTimeline(auth.accessToken, auth.tenantId, patientId),
        includeContext
          ? getClinicalContext(auth.accessToken, auth.tenantId, patientId, consultationValue?.id || null).catch((contextError) => {
              setClinicalContextError(contextError instanceof Error ? contextError.message : "Clinical context unavailable");
              return null;
            })
          : Promise.resolve(null),
      ]);
      setClinicalDocuments(documents);
      setPatientTimeline(timelineRows);
      if (includeContext) {
        setClinicalContext(context);
        if (context) {
          setClinicalContextError(null);
        }
      }
    } finally {
      if (options?.keepLoadingState !== false) {
        setClinicalContextLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId]);

  const upsertClinicalDocumentRow = React.useCallback((updated: ClinicalDocument) => {
    setClinicalDocuments((current) => current.map((row) => (row.id === updated.id ? updated : row)));
    setViewerDocument((current) => (current && current.id === updated.id ? updated : current));
  }, []);

  const refreshClinicalDocumentRow = React.useCallback(async (documentId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const updated = await getClinicalDocument(auth.accessToken, auth.tenantId, documentId);
      upsertClinicalDocumentRow(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh document status");
    }
  }, [auth.accessToken, auth.tenantId, upsertClinicalDocumentRow]);

  const reprocessClinicalDocumentRow = React.useCallback(async (documentId: string) => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    if (!window.confirm("Reprocess OCR/AI for this document?")) {
      return;
    }
    setDocumentRowBusyId(documentId);
    setInfo("AI reprocessing started.");
    setError(null);
    try {
      const updated = await reprocessClinicalDocumentExtraction(auth.accessToken, auth.tenantId, documentId);
      upsertClinicalDocumentRow(updated);
      void refreshClinicalArtifacts(consultation.patientId, consultation, { includeContext: true, keepLoadingState: false });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reprocess AI extraction");
      setDocumentRowBusyId((current) => (current === documentId ? null : current));
    }
  }, [auth.accessToken, auth.tenantId, consultation, refreshClinicalArtifacts, upsertClinicalDocumentRow]);

  const repairClinicalMemoryRow = React.useCallback(async (documentId: string) => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    if (!window.confirm("Repair clinical memory for this document?")) {
      return;
    }
    setDocumentRowBusyId(documentId);
    setInfo("Clinical memory repair started.");
    setError(null);
    try {
      const result = await repairClinicalMemoryApi(auth.accessToken, auth.tenantId, documentId);
      const corrected = result.correctedValues?.[0];
      const correctedText = corrected ? `, ${corrected.conceptKey} corrected ${corrected.oldValue} → ${corrected.newValue}` : "";
      setInfo(
        result.status === "SUCCESS"
          ? `Memory repaired: ${result.insertedConceptCount} concepts inserted, ${result.filteredPollutedConceptCount} polluted concepts filtered${correctedText}`
          : `Memory repair failed: ${result.message}`
      );
      await refreshClinicalArtifacts(consultation.patientId, consultation, { includeContext: true, keepLoadingState: false });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to repair clinical memory");
    } finally {
      setDocumentRowBusyId((current) => (current === documentId ? null : current));
    }
  }, [auth.accessToken, auth.tenantId, consultation, refreshClinicalArtifacts, upsertClinicalDocumentRow]);

  const longitudinalSnapshotLabel = React.useCallback((concept: {
    label: string;
    verificationStatus?: string | null;
  } | null | undefined) => {
    if (!concept?.label) {
      return null;
    }
    return concept.verificationStatus === "PENDING_REVIEW"
      ? `${concept.label} (Pending Review)`
      : concept.label;
  }, []);

  const documentNeedsClinicalRefresh = React.useCallback((document: ClinicalDocument) => {
    const ocrStatus = String(document.ocrStatus || "").trim().toUpperCase();
    const aiStatus = String(document.aiExtractionStatus || "").trim().toUpperCase();
    return (ocrStatus !== "" && !["COMPLETED", "FAILED"].includes(ocrStatus))
      || ["QUEUED", "PROCESSING", "PENDING", "UNDER_REVIEW"].includes(aiStatus);
  }, []);

  const canRepairClinicalMemory = React.useCallback((document: ClinicalDocument) => {
    const aiStatus = String(document.aiExtractionStatus || "").trim().toUpperCase();
    const aiSummary = String(document.aiExtractionSummary || "").trim().toUpperCase();
    return aiStatus === "REVIEW_REQUIRED" || aiStatus === "APPROVED" || aiStatus === "AI_COMPLETED" || aiSummary.includes("AI COMPLETED");
  }, []);

  const formatDocumentOpsSummary = React.useCallback((document: ClinicalDocument) => {
    const parts: string[] = [];
    const aiStatus = String(document.aiExtractionStatus || "NOT_STARTED").replaceAll("_", " ");
    const confidence = document.aiExtractionConfidence != null ? ` · ${(document.aiExtractionConfidence * 100).toFixed(0)}%` : "";
    parts.push(`AI: ${aiStatus}${confidence}`);
    const repairStatus = String(document.aiOps?.lastMemoryRepairStatus || "").trim().toUpperCase();
    if (repairStatus) {
      const repairedAt = document.aiOps?.lastMemoryRepairAt ? formatShortTimestamp(document.aiOps.lastMemoryRepairAt) : null;
      const label = repairStatus === "SUCCESS" ? "Repaired" : repairStatus === "FAILED" ? "Repair Failed" : repairStatus.replaceAll("_", " ");
      parts.push(`Memory: ${label}${repairedAt ? ` ${repairedAt}` : ""}`);
    }
    return parts.join(" · ");
  }, []);

  React.useEffect(() => {
    if (!documentRowBusyId) {
      return;
    }
    const current = clinicalDocuments.find((row) => row.id === documentRowBusyId) || null;
    if (current && !documentNeedsClinicalRefresh(current)) {
      const aiStatus = String(current.aiExtractionStatus || "").trim().toUpperCase();
      const ocrStatus = String(current.ocrStatus || "").trim().toUpperCase();
      setInfo(aiStatus === "FAILED" || ocrStatus === "FAILED" ? "AI processing failed." : "AI processing completed.");
      setDocumentRowBusyId(null);
    }
  }, [clinicalDocuments, documentNeedsClinicalRefresh, documentRowBusyId]);

  React.useEffect(() => {
    const headerElement = consultationHeaderRef.current;
    if (!headerElement || typeof window === "undefined" || typeof IntersectionObserver === "undefined") {
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        setShowStickyConsultationProgress(!entry.isIntersecting);
      },
      {
        threshold: 0.08,
        rootMargin: "-72px 0px 0px 0px",
      },
    );
    observer.observe(headerElement);
    return () => observer.disconnect();
  }, []);

  React.useEffect(() => {
    consultationFormRef.current = consultationForm;
  }, [consultationForm]);

  React.useEffect(() => {
    consultationSoapFormRef.current = consultationSoapForm;
  }, [consultationSoapForm]);

  React.useLayoutEffect(() => {
    prescriptionFormRef.current = prescriptionForm;
  }, [prescriptionForm]);

  React.useEffect(() => {
    clinicalAiDraftsRef.current = clinicalAiDrafts;
  }, [clinicalAiDrafts]);

  React.useEffect(() => {
    consultationDocumentDraftsRef.current = consultationDocumentDrafts;
  }, [consultationDocumentDrafts]);

  React.useEffect(() => {
    aiDiagnosisSuggestionRef.current = aiDiagnosisSuggestion;
  }, [aiDiagnosisSuggestion]);

  React.useEffect(() => {
    aiDiagnosisItemsRef.current = aiDiagnosisItems;
  }, [aiDiagnosisItems]);

  React.useEffect(() => {
    aiPrescriptionSuggestionRef.current = aiPrescriptionSuggestion;
  }, [aiPrescriptionSuggestion]);

  React.useEffect(() => {
    aiPrescriptionItemsRef.current = aiPrescriptionItems;
  }, [aiPrescriptionItems]);

  React.useEffect(() => {
    setActiveTab(consultationTabKeyToIndex(searchParams.get("tab")));
  }, [searchParams]);

  React.useEffect(() => {
    setHistoryTimelineFilter("ALL");
    setHistoryTimelineLimit(6);
    setHistoryConsultationLimit(3);
    setHistoryPrescriptionLimit(3);
    setHistoryDocumentLimit(6);
    setDocumentFilter("ALL");
    setDocumentReviewFilter("ALL");
  }, [consultationId]);

  React.useEffect(() => {
    if (activeTab === 0) {
      setExpanded((current) => ({
        ...current,
        followup: true,
      }));
    }
  }, [activeTab]);

  const clearAutosaveTimers = React.useCallback(() => {
    if (autosaveTimerRef.current) {
      window.clearTimeout(autosaveTimerRef.current);
      autosaveTimerRef.current = null;
    }
    if (autosaveRetryTimerRef.current) {
      window.clearTimeout(autosaveRetryTimerRef.current);
      autosaveRetryTimerRef.current = null;
    }
  }, []);

  const preserveViewport = React.useCallback(async <T,>(action: () => Promise<T>): Promise<T> => {
    viewportRestoreRef.current = { x: window.scrollX, y: window.scrollY };
    try {
      return await action();
    } finally {
      const snapshot = viewportRestoreRef.current;
      viewportRestoreRef.current = null;
      if (snapshot) {
        window.requestAnimationFrame(() => {
          window.scrollTo(snapshot.x, snapshot.y);
        });
      }
    }
  }, []);

  const updateClinicalAiDraft = React.useCallback((kind: ClinicalAiDraftKind, patch: Partial<ClinicalAiDraftState>) => {
    setClinicalAiDrafts((current) => ({
      ...current,
      [kind]: {
        ...current[kind],
        ...patch,
      },
    }));
  }, []);

  const requestOverwriteChoice = React.useCallback((label: string, currentValue: string, allowAppend: boolean) => {
    if (!currentValue.trim()) return Promise.resolve<"replace" | "append" | "cancel">("replace");
    return new Promise<"replace" | "append" | "cancel">((resolve) => {
      setWorkspaceConfirmation({
        kind: "overwrite",
        label,
        currentValue,
        allowAppend,
        onResolve: (choice) => {
          resolve(choice);
          setWorkspaceConfirmation(null);
        },
      });
    });
  }, []);

  const requestBooleanConfirmation = React.useCallback((dialog: {
    title: string;
    description: string;
    confirmLabel: string;
    confirmColor?: "primary" | "secondary" | "error" | "warning" | "info";
  }) => new Promise<boolean>((resolve) => {
    setWorkspaceConfirmation({
      kind: "confirm",
      ...dialog,
      onConfirm: () => {
        resolve(true);
        setWorkspaceConfirmation(null);
      },
      onCancel: () => {
        resolve(false);
        setWorkspaceConfirmation(null);
      },
    });
  }), []);

  React.useEffect(() => {
    const listener = (event: Event) => {
      const consultationSnapshot = consultationRef.current;
      if (!auth.accessToken || !auth.tenantId || !consultationSnapshot) return;
      const custom = event as CustomEvent<{ patientId?: string | null }>;
      if (custom.detail?.patientId && custom.detail.patientId !== consultationSnapshot.patientId) {
        return;
      }
      refreshClinicalArtifacts(consultationSnapshot.patientId, consultationSnapshot)
        .then(() => {
          setClinicalContextError(null);
        })
        .catch((err) => {
          console.error("Clinical context refresh failed", err);
          setClinicalContextError(err instanceof Error ? err.message : "Clinical context unavailable");
        });
    };
    window.addEventListener("clinic:clinical-intake-updated", listener as EventListener);
    return () => {
      window.removeEventListener("clinic:clinical-intake-updated", listener as EventListener);
    };
  }, [auth.accessToken, auth.tenantId, refreshClinicalArtifacts]);

  React.useEffect(() => {
    if (!consultation || !clinicalDocuments.some(documentNeedsClinicalRefresh)) {
      return;
    }
    const intervalId = window.setInterval(() => {
      void refreshClinicalArtifacts(consultation.patientId, consultation, { includeContext: true, keepLoadingState: false });
    }, 6000);
    return () => {
      window.clearInterval(intervalId);
    };
  }, [clinicalDocuments, consultation, documentNeedsClinicalRefresh, refreshClinicalArtifacts]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }

      if (!consultationId && appointmentId) {
        try {
          const started = await startConsultationFromAppointment(auth.accessToken, auth.tenantId, appointmentId);
          navigate(`/consultations/${started.id}`, { replace: true });
          return;
        } catch (err) {
          if (!cancelled) setError(err instanceof Error ? err.message : "Failed to start consultation");
        }
      }

      if (!consultationId) {
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);
      setMedicineCatalogWarning(null);
      setClinicalContext(null);
      setClinicalContextError(null);
      setClinicalContextLoading(false);
      setClinicalSummary(null);
      setAiDiagnosisSuggestion(null);
      setAiDiagnosisSummary(null);
      setAiDiagnosisItems([]);
      setAiDiagnosisUnstructured(false);
      setAiDiagnosisProvider(null);
      setClinicalReasoningAskedMissingInfo({});
      setClinicalReasoningLoadingStepIndex(0);
      setClinicalReasoningSectionsOpen({
        longitudinalContext: true,
        primaryDiagnosis: true,
        differentials: true,
        evidence: false,
        missingInformation: false,
        redFlags: false,
        recommendedTests: false,
        safetyNotes: false,
        debug: false,
      });
      setAiPrescriptionSuggestion(null);
      setAiPrescriptionItems([]);
      setAiPrescriptionUnstructured(false);
      setAiPrescriptionProvider(null);
      setPrescriptionSuggestionGeneratedAt(null);
      setPrescriptionInstructionsDraft(null);
      setPrescriptionInstructionsLoading(false);
      setPrescriptionInstructionsLanguage("ENGLISH");
      setPrescriptionInstructionsGeneratedAt(null);
      setPrescriptionSummaryDraft("");
      setAlternativeMedicineRowId(null);
      setClinicalAiDrafts(createEmptyClinicalAiDrafts());
      setClinicalDraftGenerationSteps(createEmptyClinicalDraftGenerationSteps());
      setAiClinicalSummaryExpanded(true);
      setClinicalAiActiveDraft(null);
      setSavedAiSummary(null);
      setAivaChatMessages([]);
      setAivaQuestionSubmitting(false);
      setAivaChatDetailsOpen(false);
      setReadinessDetailsOpen(false);
      setDiagnosisReasoningOpen(false);
      setConsultationDocumentDrafts(createEmptyConsultationDocumentDrafts());
      setConsultationDocumentationLanguage("ENGLISH");
      setConsultationReferralPriority("ROUTINE");
      setConsultationReferralDestination("");
      setConsultationCertificateType("MEDICAL");
      setFollowUpPlanNotes("");
      setConsultationCompletionExpanded(false);
      setConsultationPackageGenerating(false);
      setConsultationAuditShareLog([]);
      try {
        const [medicines, consult] = await Promise.all([
          getMedicines(auth.accessToken, auth.tenantId).catch(() => {
            if (!cancelled) setMedicineCatalogWarning("Medicine catalog unavailable. You can still enter medicines manually.");
            return [] as Medicine[];
          }),
          getConsultation(auth.accessToken, auth.tenantId, consultationId),
        ]);
        if (cancelled) return;

        setMedicineCatalog(medicines);
        setConsultation(consult);
        const [labTestRows, labOrderRows] = await Promise.all([
          getLabTests(auth.accessToken, auth.tenantId, { active: true }).catch(() => [] as LabTest[]),
          getLabOrders(auth.accessToken, auth.tenantId, { consultationId: consult.id }).catch(() => [] as LabOrder[]),
        ]);
        if (cancelled) return;
        setLabTests(labTestRows);
        setLabOrders(labOrderRows);
        const persistedSummary = await getConsultationAiSummary(auth.accessToken, auth.tenantId, consult.id).catch(() => null);
        if (!cancelled) {
          setSavedAiSummary(persistedSummary);
        }
        const loadedAppointment = consult.appointmentId
          ? await getAppointment(auth.accessToken, auth.tenantId, consult.appointmentId).catch(() => null)
          : null;
        if (cancelled) return;
        setAppointment(loadedAppointment);
        const initialConsultationForm = emptyConsultationForm(consult);
        setConsultationForm(initialConsultationForm);
        savedConsultationSnapshotRef.current = serializeConsultationForm(initialConsultationForm);
        hydratedConsultationIdRef.current = consult.id;

        const persistedSoap = await getConsultationSoap(auth.accessToken, auth.tenantId, consult.id).catch(() => null);
        if (cancelled) return;
        setConsultationSoap(persistedSoap);
        const initialSoapForm = emptyConsultationSoapForm(persistedSoap);
        setConsultationSoapForm(initialSoapForm);
        savedConsultationSoapSnapshotRef.current = serializeConsultationSoapForm(initialSoapForm);

        setClinicalContextLoading(true);
        const [detail, previousRx, documents, timelineRows, context] = await Promise.all([
          getPatient(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientPrescriptions(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientDocuments(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientTimeline(auth.accessToken, auth.tenantId, consult.patientId),
          getClinicalContext(auth.accessToken, auth.tenantId, consult.patientId, consult.id).catch((contextError) => {
            console.error("Clinical context load failed", contextError);
            if (!cancelled) {
              setClinicalContextError(contextError instanceof Error ? contextError.message : "Clinical context unavailable");
            }
            return null;
          }),
        ]);
        if (cancelled) return;

        setPatient(detail);
        setPreviousPrescriptions(previousRx.slice(0, 10));
        setClinicalDocuments(documents);
        setPatientTimeline(timelineRows);
        setClinicalContext(context);
        if (context) {
          setClinicalContextError(null);
        }

        try {
          const rx = await getConsultationPrescription(auth.accessToken, auth.tenantId, consult.id);
          if (!cancelled) {
            setPrescription(rx);
            setInvalidMedicineRowIds([]);
            const initialPrescriptionForm = emptyPrescriptionForm(rx, consult);
            setPrescriptionForm(initialPrescriptionForm);
            savedPrescriptionSnapshotRef.current = serializePrescriptionForm(initialPrescriptionForm);
            const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, rx.id).catch(() => [rx]);
            if (!cancelled) {
              setPrescriptionHistory(historyRows);
              setSelectedPrescriptionVersionId(historyRows[historyRows.length - 1]?.id || rx.id);
            }
          }
        } catch {
          if (!cancelled) {
            setPrescription(null);
            setInvalidMedicineRowIds([]);
            const initialPrescriptionForm = emptyPrescriptionForm(null, consult);
            setPrescriptionForm(initialPrescriptionForm);
            savedPrescriptionSnapshotRef.current = serializePrescriptionForm(initialPrescriptionForm);
            setPrescriptionHistory([]);
            setSelectedPrescriptionVersionId(null);
          }
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load consultation workspace");
      } finally {
        if (!cancelled) setLoading(false);
        if (!cancelled) setClinicalContextLoading(false);
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, consultationId, appointmentId, navigate]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadAiStatus() {
      if (!auth.accessToken || !auth.tenantId) return;
      const canRunAi = auth.hasPermission("ai_copilot.run") || auth.hasPermission("ai_copilot.clinic.run");
      if (!canRunAi) {
        if (!cancelled) {
          setAiAvailable(false);
          setAiStatusMessage("You do not have permission to use AI assistance.");
        }
        return;
      }
      try {
        const status: AiStatus = await getAiStatus(auth.accessToken, auth.tenantId);
        if (cancelled) return;
        const ready = status.effectiveStatus === "READY";
        setAiAvailable(ready);
        setAiStatusMessage(ready ? null : (status.message || AI_DISABLED_MESSAGE));
      } catch {
        if (!cancelled) {
          setAiAvailable(false);
          setAiStatusMessage(AI_DISABLED_MESSAGE);
        }
      }
    }
    void loadAiStatus();
    return () => {
      cancelled = true;
    };
  }, [auth, auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (saving) {
      return;
    }
    const consultationEditable = consultation ? consultation.status === "DRAFT" : false;
    const prescriptionEditable = Boolean(prescription && isEditablePrescriptionStatus(prescription.status));
    if (hydratedConsultationIdRef.current !== consultation?.id) {
      setAutosaveStatus(consultation && !consultationEditable && !prescriptionEditable ? "readonly" : "idle");
      return;
    }

    if (!consultationEditable && !prescriptionEditable) {
      setAutosaveStatus("readonly");
      return;
    }

    const consultationDirty = consultationEditable && serializeConsultationForm(consultationForm) !== savedConsultationSnapshotRef.current;
    const prescriptionDirty = prescriptionEditable && serializePrescriptionForm(prescriptionForm) !== savedPrescriptionSnapshotRef.current;
    if (!consultationDirty && !prescriptionDirty) {
      return;
    }

    setAutosaveStatus("dirty");
    if (autosaveTimerRef.current) window.clearTimeout(autosaveTimerRef.current);
    autosaveTimerRef.current = window.setTimeout(() => {
      void runAutosave();
    }, 1000);

    return () => {
      if (autosaveTimerRef.current) {
        window.clearTimeout(autosaveTimerRef.current);
        autosaveTimerRef.current = null;
      }
    };
  }, [consultation, consultationForm, prescription, prescriptionForm, saving]);

  React.useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (savingRef.current || autosaveInFlightRef.current) {
        event.preventDefault();
        event.returnValue = "";
        return;
      }
      const consultationDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
      const soapDirty = serializeConsultationSoapForm(consultationSoapFormRef.current) !== savedConsultationSoapSnapshotRef.current;
      const prescriptionDirty = serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
      if (consultationDirty || soapDirty || prescriptionDirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, []);

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;

  const canEditConsultation = auth.hasPermission("consultation.update");
  const canCompleteConsultation = auth.hasPermission("consultation.complete");
  const canFinalizePrescription = auth.hasPermission("prescription.finalize");
  const canPrintPrescription = auth.hasPermission("prescription.print");
  const canRunAi = auth.hasPermission("ai_copilot.run") || auth.hasPermission("ai_copilot.clinic.run");
  const consultationReadOnly = consultation ? consultation.status !== "DRAFT" || !canEditConsultation : !canEditConsultation;
  const prescriptionReadOnly = prescription ? !isEditablePrescriptionStatus(prescription.status) : !canEditConsultation;
  const readOnly = consultationReadOnly;
  const prescriptionReadyForCompletion = isPrescriptionReadyForConsultationCompletion(prescription);
  const patientRow = patient?.patient;
  const patientAgeGender = React.useMemo(() => (patientRow ? formatPatientAgeGender(patientRow) || null : null), [patientRow]);
  const currentPrescriptionDraftSummary = React.useMemo(() => summarizePrescriptionDraft(prescriptionForm.medicines), [prescriptionForm.medicines]);
  const labOrdersSummary = React.useMemo(() => summarizeLabOrdersForAi(labOrders), [labOrders]);
  const buildClinicalReasoningRequest = React.useCallback((reasoningConsultationId: string) => {
    if (!consultation || consultation.id !== reasoningConsultationId || !patient) {
      return null;
    }
    const bloodPressure = consultationForm.bloodPressureSystolic && consultationForm.bloodPressureDiastolic
      ? `${consultationForm.bloodPressureSystolic}/${consultationForm.bloodPressureDiastolic}`
      : null;
    const temperature = consultationForm.temperature
      ? `${consultationForm.temperature}${consultationForm.temperatureUnit ? ` ${consultationForm.temperatureUnit}` : ""}`
      : null;
    const vitals = [
      bloodPressure ? `BP:${bloodPressure}` : null,
      consultationForm.pulseRate.trim() ? `Pulse:${consultationForm.pulseRate}` : null,
      consultationForm.respiratoryRate.trim() ? `Resp:${consultationForm.respiratoryRate}` : null,
      temperature ? `Temp:${temperature}` : null,
      consultationForm.spo2.trim() ? `SpO2:${consultationForm.spo2}` : null,
    ].filter(Boolean).join(", ");
    return {
      patientId: consultation.patientId,
      chiefComplaint: consultationForm.chiefComplaints.trim() || consultation.chiefComplaints || null,
      symptoms: consultationForm.symptoms.trim() || consultation.symptoms || null,
      findings: consultationForm.clinicalNotes.trim() || clinicalContext?.labIntelligence.latestLabReport || null,
      vitals: vitals || null,
      diagnosis: consultationForm.diagnosis.trim() || consultation.diagnosis || null,
      advice: consultationForm.advice.trim() || consultation.advice || null,
      notes: consultationForm.clinicalNotes.trim() || null,
      currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
      labOrdersSummary: labOrdersSummary || null,
    };
  }, [
    clinicalContext?.labIntelligence.latestLabReport,
    consultation,
    consultationForm.advice,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.symptoms,
    currentPrescriptionDraftSummary,
    labOrdersSummary,
    patient,
  ]);
  const clinicalReasoning = useClinicalReasoning({
    accessToken: auth.accessToken,
    tenantId: auth.tenantId,
    buildRequest: buildClinicalReasoningRequest,
  });
  const clinicalReasoningResult = clinicalReasoning.clinicalReasoningResult;
  const clinicalReasoningStatus = clinicalReasoning.reasoningStatus || (clinicalReasoningResult ? "CURRENT" : "NOT_GENERATED");
  const clinicalReasoningStatusLabel = clinicalReasoningStatus === "CURRENT"
    ? "Current"
    : clinicalReasoningStatus === "STALE"
      ? "Stale"
      : clinicalReasoningStatus === "FAILED"
        ? "Failed"
        : clinicalReasoningStatus === "SUPERSEDED"
          ? "Superseded"
          : "Not generated";
  React.useEffect(() => {
    if (!consultationId || !auth.accessToken || !auth.tenantId) {
      return;
    }
    void clinicalReasoning.loadReasoning(consultationId);
  }, [auth.accessToken, auth.tenantId, clinicalReasoning.loadReasoning, consultationId]);
  React.useEffect(() => {
    if (!clinicalReasoning.loading) {
      return;
    }
    setClinicalReasoningLoadingStepIndex(0);
    const messages = [
      "Reviewing symptoms...",
      "Reviewing vitals...",
      "Reviewing laboratory reports...",
      "Reviewing longitudinal memory...",
      "Generating clinical reasoning...",
    ];
    const intervalId = window.setInterval(() => {
      setClinicalReasoningLoadingStepIndex((current) => (current + 1) % messages.length);
    }, 1800);
    return () => window.clearInterval(intervalId);
  }, [clinicalReasoning.loading]);
  const hasClinicalReasoningContext = Boolean(
    consultation
      && patient
      && (
        consultationForm.chiefComplaints.trim()
        || consultationForm.symptoms.trim()
        || consultationForm.diagnosis.trim()
        || consultationForm.clinicalNotes.trim()
      )
  );
  const canAskAiva = Boolean(
    auth.accessToken
      && auth.tenantId
      && consultation
      && patient
      && aivaClinicalQuestion.trim()
      && !aiBusy,
  );
  const canGenerateClinicalDraft = Boolean(auth.accessToken && auth.tenantId && consultation && patient && aiAvailable && !aiBusy);
  const aiAssistantEnabled = Boolean(aiAvailable && canRunAi);
  const diagnosisSectionSubtitle = aiAssistantEnabled ? "Suggestions with inline AI" : "Manual diagnosis entry";
  const clinicalDraftEntries = React.useMemo(
    () => (Object.values(clinicalAiDrafts) as ClinicalAiDraftState[]).filter((draft) => draft.generatedAt || draft.error || draft.status !== "DRAFTED" || draft.draftText.trim()),
    [clinicalAiDrafts],
  );
  const latestClinicalReasoningEntry = React.useMemo(
    () => {
      const reversed = [...aiAssistEntries].reverse();
      return reversed.find((entry) => entry.action === "diagnosis")
        || reversed.find((entry) => entry.action === "red_flags")
        || reversed.find((entry) => entry.action === "tests")
        || reversed.find((entry) => entry.action === "notes")
        || null;
    },
    [aiAssistEntries],
  );
  const latestPrescriptionEntry = React.useMemo(
    () => [...aiAssistEntries].reverse().find((entry) => entry.action === "prescription" || entry.action === "drug_safety"),
    [aiAssistEntries],
  );
  const recommendedTestSuggestions = React.useMemo(
    () => Array.from(new Set(aiDiagnosisItems.flatMap((item) => item.recommendedInvestigations).filter(Boolean))).slice(0, 8),
    [aiDiagnosisItems],
  );
  const activeLabOrderTestNames = React.useMemo(
    () => labOrders.flatMap((order) => order.items.map((item) => item.testName)).filter(Boolean),
    [labOrders],
  );
  const isClinicalReasoningAdminView = Boolean(
    auth.rolesUpper.some((role) => ["PLATFORM_ADMIN", "CLINIC_ADMIN", "TENANT_ADMIN", "ADMIN"].includes(role)),
  );
  const clinicalReasoningLoadingMessages = React.useMemo(() => [
    "Reviewing symptoms...",
    "Reviewing vitals...",
    "Reviewing laboratory reports...",
    "Reviewing longitudinal memory...",
    "Generating clinical reasoning...",
  ], []);
  const clinicalReasoningTestBuckets = React.useMemo(
    () => classifyClinicalReasoningTests(clinicalReasoningResult?.recommendedTests || []),
    [clinicalReasoningResult?.recommendedTests],
  );
  const clinicalReasoningLongitudinalFindings = React.useMemo(
    () => clinicalReasoningResult?.longitudinalContext?.findings?.filter((item) => item && (item.title || item.summary)) || [],
    [clinicalReasoningResult?.longitudinalContext?.findings],
  );
  const clinicalReasoningPrimaryConfidenceLabel = confidenceLevelFromPercent(clinicalReasoningResult?.primaryDiagnosis?.confidence);
  const clinicalReasoningPrimaryConfidencePercent = clinicalReasoningResult?.primaryDiagnosis?.confidence != null
    ? `${Math.round((clinicalReasoningResult.primaryDiagnosis.confidence || 0) * 100)}%`
    : null;
  const clinicalReasoningSummaryConfidenceLabel = confidenceLevelFromText(clinicalReasoningResult?.confidence);
  const clinicalReasoningProviderLabel = clinicalReasoningResult?.provider || clinicalReasoningResult?.metadata?.provider || clinicalReasoningResult?.metadata?.model || "Unknown";
  const labOrderAiGeneratedRelativeLabel = React.useMemo(
    () => relativeTimeLabel(labOrderAiPreparation?.generatedAt) || (labOrderAiPreparation?.generatedAt ? compactDateTime(labOrderAiPreparation.generatedAt) : "-"),
    [labOrderAiPreparation?.generatedAt],
  );
  const triggerClinicalReasoning = React.useCallback(() => {
    if (!consultation) {
      return;
    }
    const runner = clinicalReasoning.reasoningStatus && clinicalReasoning.reasoningStatus !== "NOT_GENERATED"
      ? clinicalReasoning.refreshReasoning
      : clinicalReasoning.generateReasoning;
    void preserveViewport(() => runner(consultation.id));
  }, [clinicalReasoning.generateReasoning, clinicalReasoning.reasoningStatus, clinicalReasoning.refreshReasoning, consultation, preserveViewport]);
  const acceptClinicalReasoningDiagnosis = React.useCallback(async () => {
    const diagnosisText = clinicalReasoningResult?.primaryDiagnosis?.name?.trim() || clinicalReasoningResult?.reasoningSummary?.trim() || "";
    if (!diagnosisText) {
      return;
    }
    const currentDiagnosis = consultationForm.diagnosis;
    if (currentDiagnosis.trim()) {
      const choice = await requestOverwriteChoice("Diagnosis", currentDiagnosis, true);
      if (choice === "cancel") {
        return;
      }
      setConsultationForm((current) => ({
        ...current,
        diagnosis: choice === "replace" ? diagnosisText : appendUniqueTokenLine(current.diagnosis, diagnosisText),
      }));
      return;
    }
    setConsultationForm((current) => ({
      ...current,
      diagnosis: diagnosisText,
    }));
  }, [clinicalReasoningResult?.primaryDiagnosis?.name, clinicalReasoningResult?.reasoningSummary, consultationForm.diagnosis, requestOverwriteChoice]);
  const addClinicalReasoningDifferential = React.useCallback((name: string) => {
    const next = name.trim();
    if (!next) return;
    setConsultationForm((current) => ({ ...current, diagnosis: appendUniqueTokenLine(current.diagnosis, next) }));
  }, []);
  const addClinicalReasoningSafetyNote = React.useCallback((text: string) => {
    const next = text.trim();
    if (!next) return;
    setConsultationForm((current) => ({ ...current, advice: appendUniqueTokenLine(current.advice, next) }));
    setExpanded((current) => ({ ...current, advice: true }));
    window.setTimeout(() => {
      adviceSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      adviceInputRef.current?.focus();
    }, 0);
  }, []);
  const revealAdviceSection = React.useCallback((focusAdviceInput = false) => {
    setExpanded((current) => ({ ...current, advice: true }));
    window.setTimeout(() => {
      adviceSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      if (focusAdviceInput) {
        adviceInputRef.current?.focus();
      }
    }, 0);
  }, []);
  const appendAdviceAndReveal = React.useCallback((text: string) => {
    const next = text.trim();
    if (!next) return;
    setConsultationForm((current) => ({ ...current, advice: appendTokenLine(current.advice, next) }));
    revealAdviceSection(true);
  }, [revealAdviceSection]);
  const toggleClinicalReasoningMissingInfoAsked = React.useCallback((key: string) => {
    setClinicalReasoningAskedMissingInfo((current) => ({
      ...current,
      [key]: !current[key],
    }));
  }, []);
  const toggleClinicalReasoningSection = React.useCallback((key: ClinicalReasoningSectionKey) => {
    setClinicalReasoningSectionsOpen((current) => ({
      ...current,
      [key]: !current[key],
    }));
  }, []);
  const setClinicalReasoningSectionRef = React.useCallback(
    (key: Exclude<ClinicalReasoningSectionKey, "debug">) => (node: HTMLDivElement | null) => {
      clinicalReasoningSectionRefs.current[key] = node;
    },
    [],
  );
  const scrollToClinicalReasoningSection = React.useCallback((key: Exclude<ClinicalReasoningSectionKey, "debug">) => {
    clinicalReasoningSectionRefs.current[key]?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);
  const scrollToClinicalReasoningDetails = React.useCallback(() => {
    clinicalReasoningReviewRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);
  const focusPatientIntelligence = React.useCallback(() => {
    patientIntelligenceRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);
  const focusLabOrderWorkflow = React.useCallback(() => {
    labOrderWorkflowRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);
  const buildLabOrderAiPreparation = React.useCallback((rows: InvestigationIntelligenceRow[]): { selectedTestIds: string[]; preparation: LabOrderAiPreparation } => {
    const selectedItems = rows.map((row) => {
      const aliases = investigationAliasesForCanonicalKey(normalizeInvestigationCanonicalKey(row.testName));
      const matchedTests = labTests.filter((test) => investigationTextMatches(test.testName, aliases) || investigationTextMatches(test.testCode, aliases));
      const matchedRecommendation = clinicalReasoningResult?.recommendedTests.find((test) => investigationTextMatches(test.name || "", aliases) || investigationTextMatches(test.reason || "", aliases) || investigationTextMatches(test.source || "", aliases)) || null;
      return { row, aliases, matchedTests, matchedRecommendation };
    });

    const selectedTestIds = Array.from(new Set(selectedItems.flatMap((item) => item.matchedTests.map((test) => test.id))));
    const investigations = selectedItems.length
      ? selectedItems.map((item) => item.matchedTests[0]?.testName || item.row.testName)
      : rows.map((row) => row.testName);

    const priorityRank = (value: string | null | undefined) => {
      const normalized = normalizeLookupKey(value || "");
      if (/(urgent|emergency)/.test(normalized)) return 3;
      if (/optional/.test(normalized)) return 2;
      return 1;
    };
    const suggestedPriority = (() => {
      const highest = Math.max(
        0,
        ...selectedItems.map((item) => Math.max(priorityRank(item.matchedRecommendation?.priority), priorityRank(item.row.doctorNote), priorityRank(item.row.evidence))),
      );
      if (highest >= 3) return "Urgent";
      if (highest >= 2) return "Optional";
      return "Routine";
    })();

    const reasonCandidates = [
      clinicalReasoningResult?.primaryDiagnosis?.whyConsidered,
      clinicalReasoningResult?.primaryDiagnosis?.whyLessLikely,
      ...selectedItems.map((item) => item.matchedRecommendation?.reason || item.row.doctorNote),
    ].filter((value): value is string => Boolean(value && value.trim()));
    const reason = reasonCandidates[0] || "Clinical correlation required.";

    const supportingEvidence = [
      ...(clinicalReasoningResult?.primaryDiagnosis?.supportingEvidence || [])
        .map((item) => [item.text, item.sourceTitle || item.source, item.observationDate ? compactDate(item.observationDate) : null].filter(Boolean).join(" • ").trim())
        .filter((value): value is string => Boolean(value)),
      clinicalContext?.longitudinalMemory?.latestHbA1c?.valueText || clinicalContext?.longitudinalMemory?.latestHbA1c?.evidenceText
        ? `HbA1c ${formatInvestigationEvidenceValue(clinicalContext.longitudinalMemory.latestHbA1c.valueText, clinicalContext.longitudinalMemory.latestHbA1c.valueUnit, clinicalContext.longitudinalMemory.latestHbA1c.observedOn)}`
        : null,
      clinicalContext?.longitudinalMemory?.latestBloodSugar?.valueText || clinicalContext?.longitudinalMemory?.latestBloodSugar?.evidenceText
        ? `Blood Sugar ${formatInvestigationEvidenceValue(clinicalContext.longitudinalMemory.latestBloodSugar.valueText, clinicalContext.longitudinalMemory.latestBloodSugar.valueUnit, clinicalContext.longitudinalMemory.latestBloodSugar.observedOn)}`
        : null,
      clinicalContext?.longitudinalMemory?.knownConditions?.some((concept) => normalizeLookupKey(concept.label).includes("diabetes")) ? "Known Diabetes" : null,
    ].filter((value): value is string => Boolean(value));

    const duplicateWarnings = Array.from(new Set(
      selectedItems.flatMap((item) => item.row.duplicateRisk
        ? [`Existing ${item.row.testName} result/order detected. Review before creating another order.`]
        : []),
    ));

    const preparation: LabOrderAiPreparation = {
      generatedAt: clinicalReasoningResult?.generatedAt || new Date().toISOString(),
      investigations,
      confidence: clinicalReasoningResult?.confidence || "Moderate",
      suggestedPriority,
      reason,
      supportingEvidence: supportingEvidence.slice(0, 4),
      duplicateWarnings,
      reasoningId: null,
    };

    return { selectedTestIds, preparation };
  }, [clinicalContext?.longitudinalMemory, clinicalReasoningResult, labTests]);
  const openLabOrderPreparation = React.useCallback((rows: InvestigationIntelligenceRow[]) => {
    if (readOnly || !labTests.length) {
      setInfo("Laboratory ordering unavailable.");
      return;
    }
    const { selectedTestIds, preparation } = buildLabOrderAiPreparation(rows);
    setLabOrderAiPreparation(preparation);
    setLabOrderTestIds(selectedTestIds);
    setLabOrderNotes(
      [
        "Prepared from AI Recommendation",
        `Generated: ${compactDateTime(preparation.generatedAt)}`,
        preparation.reason ? `Reason: ${preparation.reason}` : null,
        preparation.supportingEvidence.length ? `Supporting evidence: ${preparation.supportingEvidence.join(" • ")}` : null,
        `Suggested priority: ${preparation.suggestedPriority}`,
        preparation.duplicateWarnings.length ? `Warnings: ${preparation.duplicateWarnings.join(" • ")}` : null,
        "Doctor review and confirmation are required before any laboratory request is created.",
      ].filter((line): line is string => Boolean(line && line.trim())).join("\n"),
    );
    setLabOrderReviewOpen(false);
    setActiveTab(4);
    setLabOrderDialogOpen(true);
    setInfo("Opening lab order workflow. Doctor must confirm before order is created.");
    focusLabOrderWorkflow();
  }, [buildLabOrderAiPreparation, focusLabOrderWorkflow, labTests.length, readOnly]);
  const reviewInvestigationResult = React.useCallback((row: InvestigationIntelligenceRow) => {
    if (row.status === "Pending" && row.matchedOrderId) {
      setActiveTab(4);
      setSelectedLabOrderId(row.matchedOrderId);
      focusLabOrderWorkflow();
      setInfo("Pending order review is available in Lab module.");
      return;
    }
    if (row.status === "Already Available" || row.status === "Recently Completed") {
      setActiveTab(2);
      focusPatientIntelligence();
      setClinicalReasoningInvestigationHighlightKey(row.testName);
      window.setTimeout(() => {
        setClinicalReasoningInvestigationHighlightKey((current) => (current === row.testName ? null : current));
      }, 2500);
      if (row.matchedReportTitle) {
        setInfo(`Review existing result before repeating ${row.testName}.`);
      } else {
        setInfo("Available in Patient Intelligence.");
      }
      return;
    }
    if (row.duplicateRisk) {
      setInfo("Existing result or pending order found. Please review before ordering.");
      if (row.matchedOrderId) {
        setActiveTab(4);
        setSelectedLabOrderId(row.matchedOrderId);
        focusLabOrderWorkflow();
      } else {
        focusPatientIntelligence();
      }
      return;
    }
    focusPatientIntelligence();
    setInfo("Available in Patient Intelligence.");
  }, [focusLabOrderWorkflow, focusPatientIntelligence, setInfo]);
  const prepareInvestigationOrder = React.useCallback((row: InvestigationIntelligenceRow) => {
    openLabOrderPreparation([row]);
  }, [openLabOrderPreparation]);
  const aiUnavailableCard = (
    <Box sx={{ p: 1.35, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={0.45}>
        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
          {AIVA_DISABLED_TITLE}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {AIVA_DISABLED_DESCRIPTION}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {AIVA_DISABLED_NOTE}
        </Typography>
      </Stack>
    </Box>
  );
  const selectedLabOrder = React.useMemo(
    () => (selectedLabOrderId ? labOrders.find((order) => order.id === selectedLabOrderId) || null : null),
    [labOrders, selectedLabOrderId],
  );
  const reportHistoryRows = React.useMemo(() => {
    const reportTypes: ClinicalDocument["documentType"][] = [
      "EXTERNAL_LAB_REPORT",
      "INTERNAL_LAB_REPORT",
      "LAB_REPORT",
      "RADIOLOGY_REPORT",
      "X_RAY",
      "MRI_CT",
      "REFERRAL_LETTER",
      "REFERRAL",
      "DISCHARGE_SUMMARY",
    ];
    return clinicalDocuments
      .filter((document) => reportTypes.includes(document.documentType))
      .slice()
      .sort((a, b) => (new Date(b.reportDate || b.createdAt).getTime() - new Date(a.reportDate || a.createdAt).getTime()));
  }, [clinicalDocuments]);
  const clinicalReasoningInvestigationIntelligence = React.useMemo(() => {
    const recommendedTests = clinicalReasoningResult?.recommendedTests || [];
    const labIntelligence = clinicalContext?.labIntelligence || null;
    const longitudinalMemory = clinicalContext?.longitudinalMemory || null;
    const pendingInvestigationNames = new Set([
      ...(labIntelligence?.pendingInvestigations || []),
      ...labOrders
        .filter((order) => ["ORDERED", "PAYMENT_PENDING", "PAID", "READY_FOR_COLLECTION", "SAMPLE_COLLECTED", "PROCESSING", "RESULT_ENTERED"].includes(order.status))
        .flatMap((order) => order.items.map((item) => item.testName).filter(Boolean)),
    ]);
    const sourceAvailable = Boolean(
      labIntelligence?.latestLabReport
      || labIntelligence?.abnormalValues?.length
      || labIntelligence?.previousTrends?.length
      || labIntelligence?.pendingInvestigations?.length
      || labIntelligence?.lastHbA1c
      || labIntelligence?.lastCbc
      || labIntelligence?.lastCreatinine
      || labIntelligence?.latestBloodSugar
      || labIntelligence?.latestLipidSummary
      || longitudinalMemory?.latestHbA1c
      || longitudinalMemory?.latestBloodSugar
      || longitudinalMemory?.latestLipidSummary?.length
      || longitudinalMemory?.latestBloodPressure
      || longitudinalMemory?.latestBmi
      || longitudinalMemory?.history?.length
      || reportHistoryRows.length
      || labOrders.length,
    );
    const thirtyDaysMs = 30 * 24 * 60 * 60 * 1000;

    const structuredMatches = [
      longitudinalMemory?.latestHbA1c ? {
        canonicalKey: "hba1c",
        evidence: formatInvestigationEvidenceValue(longitudinalMemory.latestHbA1c.valueText, longitudinalMemory.latestHbA1c.valueUnit, longitudinalMemory.latestHbA1c.observedOn),
        observedOn: longitudinalMemory.latestHbA1c.observedOn,
        searchText: [
          longitudinalMemory.latestHbA1c.label,
          longitudinalMemory.latestHbA1c.valueText,
          longitudinalMemory.latestHbA1c.evidenceText,
        ].filter(Boolean).join(" "),
      } : null,
      longitudinalMemory?.latestBloodSugar ? {
        canonicalKey: "blood sugar",
        evidence: formatInvestigationEvidenceValue(longitudinalMemory.latestBloodSugar.valueText, longitudinalMemory.latestBloodSugar.valueUnit, longitudinalMemory.latestBloodSugar.observedOn),
        observedOn: longitudinalMemory.latestBloodSugar.observedOn,
        searchText: [
          longitudinalMemory.latestBloodSugar.label,
          longitudinalMemory.latestBloodSugar.valueText,
          longitudinalMemory.latestBloodSugar.evidenceText,
        ].filter(Boolean).join(" "),
      } : null,
      longitudinalMemory?.latestLipidSummary?.length ? {
        canonicalKey: "lipid profile",
        evidence: longitudinalMemory.latestLipidSummary
          .slice(0, 4)
          .map((item) => formatInvestigationEvidenceValue(item.valueText, item.valueUnit, item.observedOn) || item.label)
          .filter(Boolean)
          .join(" • "),
        observedOn: longitudinalMemory.latestLipidSummary[0]?.observedOn || null,
        searchText: longitudinalMemory.latestLipidSummary.map((item) => [item.label, item.valueText, item.evidenceText].filter(Boolean).join(" ")).join(" "),
      } : null,
      longitudinalMemory?.latestBloodPressure ? {
        canonicalKey: "blood pressure",
        evidence: formatInvestigationEvidenceValue(longitudinalMemory.latestBloodPressure.valueText, longitudinalMemory.latestBloodPressure.valueUnit, longitudinalMemory.latestBloodPressure.observedOn),
        observedOn: longitudinalMemory.latestBloodPressure.observedOn,
        searchText: [
          longitudinalMemory.latestBloodPressure.label,
          longitudinalMemory.latestBloodPressure.valueText,
          longitudinalMemory.latestBloodPressure.evidenceText,
        ].filter(Boolean).join(" "),
      } : null,
      longitudinalMemory?.latestBmi ? {
        canonicalKey: "bmi",
        evidence: formatInvestigationEvidenceValue(longitudinalMemory.latestBmi.valueText, longitudinalMemory.latestBmi.valueUnit, longitudinalMemory.latestBmi.observedOn),
        observedOn: longitudinalMemory.latestBmi.observedOn,
        searchText: [
          longitudinalMemory.latestBmi.label,
          longitudinalMemory.latestBmi.valueText,
          longitudinalMemory.latestBmi.evidenceText,
        ].filter(Boolean).join(" "),
      } : null,
      labIntelligence?.lastHbA1c ? {
        canonicalKey: "hba1c",
        evidence: labIntelligence.lastHbA1c,
        observedOn: null,
        searchText: labIntelligence.lastHbA1c,
      } : null,
      labIntelligence?.lastCbc ? {
        canonicalKey: "cbc",
        evidence: labIntelligence.lastCbc,
        observedOn: null,
        searchText: labIntelligence.lastCbc,
      } : null,
      labIntelligence?.lastCreatinine ? {
        canonicalKey: "creatinine",
        evidence: labIntelligence.lastCreatinine,
        observedOn: null,
        searchText: labIntelligence.lastCreatinine,
      } : null,
      labIntelligence?.latestBloodSugar ? {
        canonicalKey: "blood sugar",
        evidence: labIntelligence.latestBloodSugar,
        observedOn: null,
        searchText: labIntelligence.latestBloodSugar,
      } : null,
      labIntelligence?.latestLipidSummary ? {
        canonicalKey: "lipid profile",
        evidence: labIntelligence.latestLipidSummary,
        observedOn: null,
        searchText: labIntelligence.latestLipidSummary,
      } : null,
    ].filter(Boolean) as Array<{
      canonicalKey: string;
      evidence: string | null;
      observedOn: string | null;
      searchText: string;
    }>;

    const reportRows = reportHistoryRows.map((row) => ({
      canonicalKey: normalizeInvestigationCanonicalKey([row.title, row.originalFilename, row.documentType, row.description, row.reportDate, row.createdAt].filter(Boolean).join(" ")),
      title: row.title || row.originalFilename || row.documentType.replaceAll("_", " "),
      evidence: [row.title || row.originalFilename || row.documentType.replaceAll("_", " "), row.reportDate || row.createdAt ? compactDate(row.reportDate || row.createdAt) : null].filter(Boolean).join(" • "),
      observedOn: row.reportDate || row.createdAt || null,
      searchText: [row.title, row.originalFilename, row.documentType, row.description, row.reportDate, row.createdAt].filter(Boolean).join(" "),
    }));

    const rows = recommendedTests.map((test) => {
      const rawText = [test.name, test.reason, test.source, test.actionType].filter(Boolean).join(" ");
      const canonicalKey = normalizeInvestigationCanonicalKey(rawText || test.name || test.reason || "");
      const aliases = investigationAliasesForCanonicalKey(canonicalKey);
      const reasonText = normalizeLookupKey(rawText);
      const consider = /\b(consider|optional|if indicated|if exposure|if symptoms persist|if clinically needed)\b/.test(reasonText);
      const recentCutoff = Date.now() - thirtyDaysMs;
      const structuredMatch = structuredMatches.find((entry) => entry.canonicalKey === canonicalKey || investigationTextMatches(entry.searchText, aliases));
      const reportMatch = reportRows.find((entry) => entry.canonicalKey === canonicalKey || investigationTextMatches(entry.searchText, aliases));
      const pendingMatch = Array.from(pendingInvestigationNames).find((pending) => investigationTextMatches(pending, aliases));
      const availableMatch = structuredMatch || reportMatch || null;
      const duplicateRisk = Boolean(availableMatch || pendingMatch);
      let status: InvestigationIntelligenceStatus;
      if (!sourceAvailable) {
        status = "Unknown";
      } else if (consider && !availableMatch && !pendingMatch) {
        status = "Consider";
      } else if (availableMatch) {
        const observedOn = availableMatch.observedOn ? new Date(availableMatch.observedOn).getTime() : null;
        status = observedOn != null && !Number.isNaN(observedOn) && observedOn >= recentCutoff ? "Recently Completed" : "Already Available";
      } else if (pendingMatch) {
        status = "Pending";
      } else if (consider) {
        status = "Consider";
      } else {
        status = "Recommended";
      }

      const evidence = availableMatch?.evidence
        || (pendingMatch ? `Pending lab order: ${pendingMatch}` : null)
        || (test.reason || null)
        || (test.source || null)
        || (sourceAvailable ? "No recent matching result found." : "Patient investigation history is not fully available.");
      const doctorNote = duplicateRisk
        ? "Recent result or pending order already exists. Avoid repeat unless clinically indicated."
        : status === "Already Available"
          ? "Recent result available. Repeat only if clinically indicated."
          : status === "Recently Completed"
            ? "Recent result available. Repeat only if clinically indicated."
            : status === "Pending"
              ? "Pending order exists. Review before creating another request."
              : status === "Consider"
                ? "Useful if clinically needed."
                : status === "Unknown"
                  ? "Clinical judgement required."
                  : "No recent matching result found.";

      return {
        testName: test.name || investigationLabelFromCanonicalKey(canonicalKey) || "Recommended investigation",
        status,
        evidence,
        doctorNote,
        duplicateRisk,
        matchedOrderId: pendingMatch ? labOrders.find((order) => order.items.some((item) => investigationTextMatches(item.testName, aliases)))?.id || null : null,
        matchedReportTitle: reportMatch?.title || null,
        observedOn: availableMatch?.observedOn || null,
      };
    });

    const summary = {
      alreadyAvailable: rows.filter((row) => row.status === "Already Available").length,
      pending: rows.filter((row) => row.status === "Pending").length,
      recommended: rows.filter((row) => row.status === "Recommended").length,
      consider: rows.filter((row) => row.status === "Consider").length,
      duplicateRisk: rows.filter((row) => row.duplicateRisk).length,
      sourceAvailable,
    };

    return { rows, summary };
  }, [clinicalContext?.labIntelligence, clinicalContext?.longitudinalMemory, clinicalReasoningResult?.recommendedTests, labOrders, reportHistoryRows]);
  const labOrderPreparationRows = React.useMemo(
    () => clinicalReasoningInvestigationIntelligence.rows.filter((row) => row.status === "Recommended" || row.status === "Consider"),
    [clinicalReasoningInvestigationIntelligence.rows],
  );
  const labTestCatalogLookup = React.useMemo(() => {
    const entries = new Map<string, LabTest>();
    labTests.forEach((test) => {
      entries.set(normalizeLookupKey(`${test.testName} ${test.testCode}`), test);
      entries.set(normalizeLookupKey(test.testName), test);
      entries.set(normalizeLookupKey(test.testCode), test);
    });
    return entries;
  }, [labTests]);
  const recommendedLabOrderGroups = React.useMemo(() => {
    const frequentNames = [
      "CBC",
      "Blood Sugar",
      "HbA1c",
      "Urine Routine",
      "Creatinine",
      "ECG",
      "LFT",
      "RFT",
    ];
    const diagnosisBase = new Set<string>();
    const diagnosisKey = normalizeLookupKey([
      consultationForm.diagnosis,
      clinicalContext?.diagnosisHistory.lastVisitDiagnosis,
      clinicalContext?.patientSummary.chronicConditions,
    ].filter(Boolean).join(" "));
    if (diagnosisKey.includes("diabetes")) {
      ["Blood Sugar", "HbA1c", "Creatinine", "Urine Routine"].forEach((test) => diagnosisBase.add(test));
    }
    if (diagnosisKey.includes("fever") || normalizeLookupKey([consultationForm.chiefComplaints, consultationForm.symptoms].join(" ")).includes("fever")) {
      ["CBC", "Urine Routine", "Blood Sugar"].forEach((test) => diagnosisBase.add(test));
    }
    if (diagnosisKey.includes("hypertension") || diagnosisKey.includes("bp")) {
      ["ECG", "Creatinine", "RFT"].forEach((test) => diagnosisBase.add(test));
    }
    if (diagnosisKey.includes("cough") || diagnosisKey.includes("chest")) {
      ["CBC", "X-Ray Chest"].forEach((test) => diagnosisBase.add(test));
    }
    const followUpNames = Array.from(new Set([
      ...(clinicalContext?.labIntelligence.pendingInvestigations || []),
      ...(recommendedTestSuggestions.length ? recommendedTestSuggestions : []),
    ]));
    const frequentItems = frequentNames
      .map((name) => labTestCatalogLookup.get(normalizeLookupKey(name)) || Array.from(labTests).find((test) => normalizeLookupKey(test.testName).includes(normalizeLookupKey(name))))
      .filter((test): test is LabTest => Boolean(test))
      .slice(0, 6)
      .map((test) => ({
        id: test.id,
        testName: test.testName,
        sampleType: test.sampleType,
        turnaroundTime: test.tenantTatOverride || test.turnaroundTime || null,
        price: test.tenantPriceOverride ?? test.price ?? null,
        fastingRequired: looksLikeFastingTest(test.testName, test.sampleType, test.category),
        reason: "Common screening test for this presentation.",
        group: "FREQUENTLY_ORDERED" as const,
      }));
    const diagnosisItems = Array.from(diagnosisBase)
      .map((name) => labTestCatalogLookup.get(normalizeLookupKey(name)) || Array.from(labTests).find((test) => normalizeLookupKey(test.testName).includes(normalizeLookupKey(name))))
      .filter((test): test is LabTest => Boolean(test))
      .slice(0, 6)
      .map((test) => ({
        id: test.id,
        testName: test.testName,
        sampleType: test.sampleType,
        turnaroundTime: test.tenantTatOverride || test.turnaroundTime || null,
        price: test.tenantPriceOverride ?? test.price ?? null,
        fastingRequired: looksLikeFastingTest(test.testName, test.sampleType, test.category),
        reason: diagnosisKey.includes("diabetes") ? "Suggested due to diabetes follow-up." : diagnosisKey.includes("hypertension") ? "Suggested due to hypertension follow-up." : diagnosisKey.includes("fever") ? "Suggested because fever + weakness documented." : "Suggested from diagnosis and symptoms context.",
        group: "DIAGNOSIS_BASED" as const,
      }));
    const followUpItems = followUpNames
      .map((name) => labTestCatalogLookup.get(normalizeLookupKey(name)) || Array.from(labTests).find((test) => normalizeLookupKey(test.testName).includes(normalizeLookupKey(name))))
      .filter((test): test is LabTest => Boolean(test))
      .slice(0, 6)
      .map((test) => ({
        id: test.id,
        testName: test.testName,
        sampleType: test.sampleType,
        turnaroundTime: test.tenantTatOverride || test.turnaroundTime || null,
        price: test.tenantPriceOverride ?? test.price ?? null,
        fastingRequired: looksLikeFastingTest(test.testName, test.sampleType, test.category),
        reason: "Previously advised for follow-up or present in current clinical context.",
        group: "PREVIOUS_FOLLOW_UP" as const,
      }));
    const customItems = labTests
      .filter((test) => !customTest.trim() || normalizeLookupKey(`${test.testName} ${test.testCode} ${test.category}`).includes(normalizeLookupKey(customTest)) || normalizeLookupKey(customTest).includes(normalizeLookupKey(test.testName)))
      .slice(0, 8)
      .map((test) => ({
        id: test.id,
        testName: test.testName,
        sampleType: test.sampleType,
        turnaroundTime: test.tenantTatOverride || test.turnaroundTime || null,
        price: test.tenantPriceOverride ?? test.price ?? null,
        fastingRequired: looksLikeFastingTest(test.testName, test.sampleType, test.category),
        reason: "Custom search result from active lab catalog.",
        group: "CUSTOM" as const,
      }));
    return {
      frequentItems,
      diagnosisItems,
      followUpItems,
      customItems,
    };
  }, [clinicalContext?.diagnosisHistory.lastVisitDiagnosis, clinicalContext?.labIntelligence.pendingInvestigations, clinicalContext?.patientSummary.chronicConditions, consultationForm.chiefComplaints, consultationForm.diagnosis, consultationForm.symptoms, customTest, labTestCatalogLookup, labTests, recommendedTestSuggestions]);
  const selectedLabOrderTests = React.useMemo(
    () => labTests.filter((test) => labOrderTestIds.includes(test.id)),
    [labOrderTestIds, labTests],
  );
  React.useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedLabTestSearch(labTestSearch.trim());
    }, 180);
    return () => window.clearTimeout(timeoutId);
  }, [labTestSearch]);
  React.useEffect(() => {
    if (!labOrderDialogOpen) {
      return;
    }
    setLabTestSearch("");
    setDebouncedLabTestSearch("");
    setLabTestCategoryFilter("All");
  }, [labOrderDialogOpen]);
  const labTestFilterOptions = React.useMemo(() => {
    const categories = Array.from(
      new Set(
        labTests
          .map((test) => String(test.category || "").trim())
          .filter(Boolean)
      )
    ).sort((a, b) => a.localeCompare(b));
    return [
      { value: "All", label: "All" },
      ...categories.map((category) => ({
        value: category,
        label: category === "Radiology" ? "Radiology / Imaging" : category,
      })),
    ];
  }, [labTests]);
  const filteredLabTests = React.useMemo(() => {
    const normalizedSearch = normalizeLookupKey(debouncedLabTestSearch);
    return [...labTests]
      .filter((test) => labTestCategoryFilter === "All" || String(test.category || "").trim() === labTestCategoryFilter)
      .filter((test) => {
        if (!normalizedSearch) {
          return true;
        }
        const haystack = normalizeLookupKey(
          [
            test.testName,
            test.testCode,
            test.department,
            test.category,
            test.sampleType,
          ].filter(Boolean).join(" ")
        );
        return haystack.includes(normalizedSearch);
      })
      .sort((left, right) => {
        const leftSelected = labOrderTestIds.includes(left.id);
        const rightSelected = labOrderTestIds.includes(right.id);
        if (leftSelected !== rightSelected) {
          return leftSelected ? -1 : 1;
        }
        const leftOrder = left.displayOrder ?? Number.MAX_SAFE_INTEGER;
        const rightOrder = right.displayOrder ?? Number.MAX_SAFE_INTEGER;
        if (leftOrder !== rightOrder) {
          return leftOrder - rightOrder;
        }
        return left.testName.localeCompare(right.testName);
      });
  }, [debouncedLabTestSearch, labOrderTestIds, labTestCategoryFilter, labTests]);
  const labTestResultCountLabel = React.useMemo(() => {
    if (filteredLabTests.length === labTests.length) {
      return pluralizeLabTests(labTests.length);
    }
    return `${pluralizeLabTests(filteredLabTests.length)} of ${pluralizeLabTests(labTests.length)}`;
  }, [filteredLabTests.length, labTests.length]);
  const labOrderReviewWarnings = React.useMemo(() => {
    const warningMap = new Map<string, string>();
    const reportText = reportHistoryRows
      .map((row) => `${row.title} ${row.originalFilename} ${row.description || ""} ${row.reportDate || ""}`)
      .join(" ")
      .toLowerCase();
    selectedLabOrderTests.forEach((test) => {
      const normalizedTestName = normalizeLookupKey(test.testName);
      if (activeLabOrderTestNames.some((name) => normalizeLookupKey(name) === normalizedTestName)) {
        warningMap.set(test.id, `${test.testName} already ordered today.`);
      } else if (reportText.includes(normalizedTestName)) {
        warningMap.set(test.id, `${test.testName} report available from recent history.`);
      } else if (clinicalContext?.labIntelligence.latestLabReport && normalizeLookupKey(clinicalContext.labIntelligence.latestLabReport).includes(normalizedTestName)) {
        warningMap.set(test.id, `${test.testName} already reflected in recent clinical intelligence.`);
      }
    });
    return warningMap;
  }, [activeLabOrderTestNames, clinicalContext?.labIntelligence.latestLabReport, reportHistoryRows, selectedLabOrderTests]);
  const labOrderEstimatedCost = React.useMemo(
    () => selectedLabOrderTests.reduce((sum, test) => sum + (test.tenantPriceOverride ?? test.price ?? 0), 0),
    [selectedLabOrderTests],
  );
  const diagnosisItemChips = diagnosisItemsExpanded ? aiDiagnosisItems : aiDiagnosisItems.slice(0, 6);
  const diagnosisItemOverflow = Math.max(aiDiagnosisItems.length - diagnosisItemChips.length, 0);
  const appendAiAssistEntry = React.useCallback((entry: AiAssistEntry) => {
    setAiAssistEntries((current) => [...current, entry]);
    return entry.id;
  }, []);
  const updateAiAssistEntry = React.useCallback((entryId: string, patch: Partial<AiAssistEntry>) => {
    setAiAssistEntries((current) => current.map((entry) => (entry.id === entryId ? { ...entry, ...patch } : entry)));
  }, []);
  const currentAppointment = appointment;
  const lastConsultation = patient?.previousConsultations?.find((row) => row.id !== consultation?.id);
  const currentBmi = calculateBmi(consultationForm.weightKg, consultationForm.heightCm);
  const currentBmiCategory = bmiCategory(currentBmi);
  const lastBmi = calculateBmi(lastConsultation?.weightKg?.toString() || "", lastConsultation?.heightCm?.toString() || "");
  const lastBmiCategory = bmiCategory(lastBmi);
  const intakeVitals = clinicalContext?.intakeSummary?.latestVitals || null;
  const previousVitals = {
    bloodPressureSystolic: lastConsultation?.bloodPressureSystolic ?? null,
    bloodPressureDiastolic: lastConsultation?.bloodPressureDiastolic ?? null,
    pulseRate: lastConsultation?.pulseRate ?? null,
    temperature: lastConsultation?.temperature ?? null,
    spo2: lastConsultation?.spo2 ?? null,
    respiratoryRate: lastConsultation?.respiratoryRate ?? null,
    weightKg: lastConsultation?.weightKg ?? null,
    bmi: lastBmi ?? null,
  };
  const latestVitals = {
    bloodPressureSystolic: consultationForm.bloodPressureSystolic.trim() ? Number(consultationForm.bloodPressureSystolic) : intakeVitals?.bloodPressureSystolic ?? null,
    bloodPressureDiastolic: consultationForm.bloodPressureDiastolic.trim() ? Number(consultationForm.bloodPressureDiastolic) : intakeVitals?.bloodPressureDiastolic ?? null,
    pulseRate: consultationForm.pulseRate.trim() ? Number(consultationForm.pulseRate) : intakeVitals?.pulseRate ?? null,
    temperature: consultationForm.temperature.trim() ? Number(consultationForm.temperature) : intakeVitals?.temperature ?? null,
    temperatureUnit: consultationForm.temperatureUnit || intakeVitals?.temperatureUnit || null,
    spo2: consultationForm.spo2.trim() ? Number(consultationForm.spo2) : intakeVitals?.spo2 ?? null,
    respiratoryRate: consultationForm.respiratoryRate.trim() ? Number(consultationForm.respiratoryRate) : intakeVitals?.respiratoryRate ?? null,
    weightKg: consultationForm.weightKg.trim() ? Number(consultationForm.weightKg) : intakeVitals?.weightKg ?? null,
    bmi: currentBmi ?? intakeVitals?.bmi ?? null,
    randomBloodSugar: intakeVitals?.randomBloodSugar ?? null,
    painScore: intakeVitals?.painScore ?? null,
  };
  const medicineUsageCounts = React.useMemo(() => {
    const counts = new Map<string, number>();
    previousPrescriptions.forEach((rx) => {
      rx.medicines.forEach((med) => {
        const key = normalizeMedicationKey(med.medicineName);
        if (!key) return;
        counts.set(key, (counts.get(key) || 0) + 1);
      });
    });
    return counts;
  }, [previousPrescriptions]);
  const allMedicineCatalogNames = React.useMemo(
    () => medicineCatalog.map((medicine) => medicine.medicineName).filter(Boolean),
    [medicineCatalog],
  );
  const frequentlyUsedMedicineNames = React.useMemo(
    () => Array.from(medicineUsageCounts.entries())
      .filter(([, count]) => count >= 2)
      .map(([key]) => previousPrescriptions.flatMap((rx) => rx.medicines.map((med) => med.medicineName)).find((name) => normalizeMedicationKey(name) === key) || "")
      .filter(Boolean)
      .slice(0, 8),
    [medicineUsageCounts, previousPrescriptions],
  );
  const recentMedicineNames = React.useMemo(
    () => Array.from(new Set(previousPrescriptions.flatMap((rx) => rx.medicines.map((med) => med.medicineName)).filter(Boolean))).slice(0, 10),
    [previousPrescriptions],
  );
  const lastPrescribedMedicineNames = React.useMemo(
    () => (previousPrescriptions[0]?.medicines || []).map((row) => row.medicineName).filter(Boolean).slice(0, 8),
    [previousPrescriptions],
  );
  const aiVitalsSummary = `BP:${consultationForm.bloodPressureSystolic}/${consultationForm.bloodPressureDiastolic}, Pulse:${consultationForm.pulseRate}, Resp:${consultationForm.respiratoryRate}, Temp:${consultationForm.temperature}, BMI:${currentBmi ? currentBmi.toFixed(1) : "-"}`;
  const soapVitalsSummary = formatSoapVitalsSummary(latestVitals);
  const aiAllergiesSummary = patientRow?.allergies?.trim() || null;
  const aiChronicConditionsSummary = clinicalContext?.patientSummary.chronicConditions?.trim() || patientRow?.existingConditions?.trim() || null;
  const verifiedClinicalConditions = clinicalContext?.longitudinalMemory?.knownConditions || [];
  const verifiedLongTermMedications = clinicalContext?.longitudinalMemory?.longTermMedications || [];
  const clinicalSnapshotConditions = verifiedClinicalConditions.length
    ? verifiedClinicalConditions.map((concept) => longitudinalSnapshotLabel(concept) || concept.label).join(", ")
    : patientRow?.existingConditions || "Not recorded";
  const clinicalSnapshotMedications = verifiedLongTermMedications.length
    ? verifiedLongTermMedications.map((concept) => longitudinalSnapshotLabel(concept) || concept.label).join(", ")
    : patientRow?.longTermMedications || "Not recorded";
  const currentMedicineRows = prescriptionForm.medicines.filter((row) => medicineRowHasAnyContent(row));
  const latestPreviousPrescription = previousPrescriptions[0] || null;
  const latestPreviousMedicineRows = latestPreviousPrescription?.medicines || [];
  const latestPreviousMedicineNames = latestPreviousMedicineRows.map((row) => row.medicineName).filter(Boolean);
  const activeTimeline = patientTimeline.length ? patientTimeline : [];
  const visiblePrescriptionHistory = prescriptionHistory.filter((row) => row.status !== "CANCELLED");
  const historyConsultationEntries = React.useMemo(() => {
    const consultations = (patient?.previousConsultations || [])
      .filter((row) => row.id && row.id !== consultation?.id)
      .slice()
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
    return consultations;
  }, [consultation?.id, patient?.previousConsultations]);
  const historyOverviewConsultations = React.useMemo(() => {
    const consultations = historyConsultationEntries;
    return consultations.slice(0, 3);
  }, [historyConsultationEntries]);
  const historyOverviewPrescriptionGroups = React.useMemo(() => {
    const groups = new Map<string, Prescription[]>();
    visiblePrescriptionHistory
      .slice()
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime() || (right.versionNumber || 0) - (left.versionNumber || 0))
      .forEach((row) => {
        const key = buildPrescriptionGroupKey(row);
        const bucket = groups.get(key);
        if (bucket) {
          bucket.push(row);
        } else {
          groups.set(key, [row]);
        }
      });
    return Array.from(groups.entries())
      .map(([key, rows]) => {
        const versions = rows
          .slice()
          .sort((left, right) => (right.versionNumber || 0) - (left.versionNumber || 0) || new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
        const current = versions[0] || rows[0];
        return {
          key,
          current,
          versions,
          versionCount: versions.length,
        };
      })
      .slice(0, 3);
  }, [visiblePrescriptionHistory]);
  const historyPrescriptionGroups = React.useMemo(() => {
    const groups = new Map<string, Prescription[]>();
    visiblePrescriptionHistory
      .slice()
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime() || (right.versionNumber || 0) - (left.versionNumber || 0))
      .forEach((row) => {
        const key = buildPrescriptionGroupKey(row);
        const bucket = groups.get(key);
        if (bucket) {
          bucket.push(row);
        } else {
          groups.set(key, [row]);
        }
      });
    return Array.from(groups.entries()).map(([key, rows]) => {
      const versions = rows
        .slice()
        .sort((left, right) => (right.versionNumber || 0) - (left.versionNumber || 0) || new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
      const current = versions[0] || rows[0];
      return {
        key,
        current,
        versions,
        versionCount: versions.length,
      };
    });
  }, [visiblePrescriptionHistory]);
  const historyTimelineEntries = React.useMemo(() => {
    const filtered = activeTimeline.filter((item) => historyTimelineFilter === "ALL" || timelineFilterKey(item) === historyTimelineFilter);
    return filtered.slice().sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime());
  }, [activeTimeline, historyTimelineFilter]);
  const historyTimelineGroups = React.useMemo(() => {
    const groups = new Map<string, PatientTimelineItem[]>();
    historyTimelineEntries.forEach((item) => {
      const key = compactDate(item.occurredAt);
      const bucket = groups.get(key);
      if (bucket) {
        bucket.push(item);
      } else {
        groups.set(key, [item]);
      }
    });
    return Array.from(groups.entries()).map(([dateLabel, items]) => ({
      dateLabel,
      items: items.slice().sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime()),
    }));
  }, [historyTimelineEntries]);
  const historyDocumentEntries = React.useMemo(() => {
    const filtered = clinicalDocuments.filter((row) => {
      if (documentFilter !== "ALL" && documentFilterKey(row.documentType) !== documentFilter) {
        return false;
      }
      const reviewStatus = documentReviewStatusKey(row);
      if (documentReviewFilter !== "ALL" && reviewStatus !== documentReviewFilter) {
        return false;
      }
      return true;
    });
    return filtered.slice().sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
  }, [clinicalDocuments, documentFilter, documentReviewFilter]);
  const availableDocumentReviewFilters = React.useMemo(() => {
    const statuses = new Set<"PENDING_REVIEW" | "VERIFIED" | "NEEDS_ATTENTION">();
    clinicalDocuments.forEach((row) => {
      const status = documentReviewStatusKey(row);
      if (status) statuses.add(status);
    });
    return Array.from(statuses);
  }, [clinicalDocuments]);
  const historyDocumentGroups = React.useMemo(() => historyDocumentEntries.slice(0, historyDocumentLimit), [historyDocumentEntries, historyDocumentLimit]);
  const historyConsultationRows = React.useMemo(() => historyConsultationEntries.slice(0, historyConsultationLimit), [historyConsultationEntries, historyConsultationLimit]);
  const historyPrescriptionOverviewRows = React.useMemo(() => historyOverviewPrescriptionGroups.slice(0, historyPrescriptionLimit), [historyOverviewPrescriptionGroups, historyPrescriptionLimit]);
  const historyTimelineVisibleGroups = React.useMemo(() => historyTimelineGroups.slice(0, historyTimelineLimit), [historyTimelineGroups, historyTimelineLimit]);
  const historyTrendEntries = React.useMemo(() => (clinicalContext?.longitudinalClinicalContext?.labTrends || []).slice(0, 3), [clinicalContext?.longitudinalClinicalContext?.labTrends]);
  const historySnapshotVitals = formatSoapVitalsSummary(latestVitals);
  const latestPrescriptionVersion = visiblePrescriptionHistory[visiblePrescriptionHistory.length - 1] || null;
  const selectedPrescriptionVersion = prescriptionHistory.find((row) => row.id === selectedPrescriptionVersionId) || latestPrescriptionVersion || null;
  const currentPrescriptionIsEditableDraft = Boolean(prescription && isEditablePrescriptionStatus(prescription.status));
  const currentPrescriptionIsActiveCorrectionDraft = Boolean(currentPrescriptionIsEditableDraft && prescription?.parentPrescriptionId);
  const selectedPrescriptionIsLatest = Boolean(selectedPrescriptionVersion && latestPrescriptionVersion && selectedPrescriptionVersion.id === latestPrescriptionVersion.id);
  const canCreateCorrectionFromLatest = Boolean(
    canFinalizePrescription &&
      prescription &&
      !currentPrescriptionIsEditableDraft &&
      selectedPrescriptionIsLatest
  );
  const filteredMedicines = medicineCatalog
    .filter((medicine) => medicine.active !== false)
    .filter((medicine) => !medicineSearch.trim() || `${medicine.medicineName} ${medicine.strength || ""}`.toLowerCase().includes(medicineSearch.trim().toLowerCase()))
    .slice(0, 24);
  const favoriteMedicineNames = React.useMemo(() => [] as string[], []);
  const matchesMedicineSearch = React.useCallback((name: string) => {
    const query = medicineSearch.trim().toLowerCase();
    if (!query) return true;
    return name.toLowerCase().includes(query);
  }, [medicineSearch]);
  const groupedMedicineNames = React.useMemo(() => {
    const frequentlyUsed = Array.from(medicineUsageCounts.entries())
      .filter(([, count]) => count >= 2)
      .map(([key]) => previousPrescriptions.flatMap((rx) => rx.medicines.map((med) => med.medicineName)).find((name) => normalizeMedicationKey(name) === key) || "")
      .filter((name) => name && matchesMedicineSearch(name))
      .slice(0, 8);
    const recent = recentMedicineNames.filter(matchesMedicineSearch).slice(0, 8);
    return { frequentlyUsed, recent };
  }, [matchesMedicineSearch, medicineUsageCounts, previousPrescriptions, recentMedicineNames]);
  const groupedMedicineCatalog = React.useMemo(() => {
    const favorite = favoriteMedicineNames.filter(matchesMedicineSearch);
    const frequent = filteredMedicines
      .filter((medicine) => groupedMedicineNames.frequentlyUsed.some((name) => normalizeMedicationKey(name) === normalizeMedicationKey(medicine.medicineName)))
      .slice(0, 8);
    const recent = filteredMedicines
      .filter((medicine) => groupedMedicineNames.recent.some((name) => normalizeMedicationKey(name) === normalizeMedicationKey(medicine.medicineName)))
      .slice(0, 8);
    const allMedicines = filteredMedicines;
    return { favorite, frequent, recent, allMedicines };
  }, [favoriteMedicineNames, filteredMedicines, groupedMedicineNames]);
  const previousMedicineGroups = React.useMemo(() => ({
    favorites: [] as string[],
    recent: recentMedicineNames.filter(matchesMedicineSearch).slice(0, 8),
    lastPrescribed: lastPrescribedMedicineNames.filter(matchesMedicineSearch).slice(0, 8),
  }), [lastPrescribedMedicineNames, matchesMedicineSearch, recentMedicineNames]);
  const fastPackRecentTemplates = React.useMemo(() => (previousPrescriptions.length ? [{ key: "repeat-previous", label: "Repeat previous prescription" } as const] : []), [previousPrescriptions.length]);
  const fastPackClinicTemplates = React.useMemo(() => RX_TEMPLATES, []);
  const fastPackAiSuggestions = React.useMemo(() => aiPrescriptionItems.slice(0, 4), [aiPrescriptionItems]);
  const prescriptionSafetyChecks = React.useMemo(() => {
    const allergies = splitCompactList(patientRow?.allergies);
    const duplicateNames = currentMedicineRows
      .map((row) => row.medicineName.trim())
      .filter(Boolean)
      .filter((name, index, array) => array.findIndex((item) => normalizeMedicationKey(item) === normalizeMedicationKey(name)) !== index);
    const allergyText = (patientRow?.allergies || "").trim();
    const noKnownAllergy = /^(?:nkda|none|no known allergies?|no allergies?)$/i.test(allergyText);
    const allergyMatch = allergies.find((allergy: string) => currentMedicineRows.some((row) => normalizeMedicationKey(row.medicineName).includes(normalizeMedicationKey(allergy)) || normalizeMedicationKey(allergy).includes(normalizeMedicationKey(row.medicineName))));
    const hasCurrentDraft = currentMedicineRows.length > 0;
    return [
      {
        label: "Drug-drug interaction",
        status: "not_enough_data",
        message: currentMedicineRows.length > 1 ? "Interaction checking is unavailable because no trusted interaction reference is configured." : "Not enough data",
      },
      {
        label: "Allergy check",
        status: allergyMatch ? "critical" : allergies.length || noKnownAllergy ? "safe" : "needs_review",
        message: allergyMatch ? `Recorded allergy matches ${currentMedicineRows.find((row) => normalizeMedicationKey(row.medicineName).includes(normalizeMedicationKey(allergyMatch)) || normalizeMedicationKey(allergyMatch).includes(normalizeMedicationKey(row.medicineName)))?.medicineName || "medicine"}.` : allergies.length || noKnownAllergy ? "No direct allergy match found." : "Allergy status is not recorded.",
      },
      {
        label: "Duplicate therapy",
        status: duplicateNames.length ? "warning" : hasCurrentDraft ? "safe" : "not_enough_data",
        message: duplicateNames.length ? "Duplicate therapy detected in the draft." : hasCurrentDraft ? "No obvious duplicate detected." : "Not enough data",
      },
      {
        label: "Contraindications",
        status: "not_enough_data",
        message: "Not enough data",
      },
      {
        label: "Pregnancy safety",
        status: "not_enough_data",
        message: "Not enough data",
      },
      {
        label: "Pediatric dose",
        status: patientRow?.ageYears != null && patientRow.ageYears < 18 ? "warning" : "not_enough_data",
        message: patientRow?.ageYears != null && patientRow.ageYears < 18 ? "Pediatric dose review needed." : "Not enough data",
      },
      {
        label: "Renal dose",
        status: "not_enough_data",
        message: "Not enough data",
      },
      {
        label: "Liver dose",
        status: "not_enough_data",
        message: "Not enough data",
      },
      {
        label: "Maximum dose",
        status: "not_enough_data",
        message: "Maximum-dose reference unavailable.",
      },
      {
        label: "Allergy conflict checked",
        status: allergyMatch ? "critical" : allergies.length || noKnownAllergy ? "complete" : "missing",
        message: allergyMatch ? "Review before prescribing." : allergies.length || noKnownAllergy ? "Checked against available allergy data." : "Allergy status is not recorded.",
      },
      {
        label: "Interaction checked",
        status: "not_enough_data",
        message: "No trusted interaction reference is configured.",
      },
    ] as Array<{
      label: string;
      status: "safe" | "warning" | "critical" | "not_enough_data" | "complete" | "needs_review";
      message: string;
    }>;
  }, [currentMedicineRows, patientRow?.ageYears, patientRow?.allergies]);
  const prescriptionSafetySummary = React.useMemo(() => ({
    critical: prescriptionSafetyChecks.filter((item) => item.status === "critical").length,
    review: prescriptionSafetyChecks.filter((item) => item.status === "warning" || item.status === "needs_review").length,
    safe: prescriptionSafetyChecks.filter((item) => item.status === "safe" || item.status === "complete").length,
    unavailable: prescriptionSafetyChecks.filter((item) => item.status === "not_enough_data").length,
  }), [prescriptionSafetyChecks]);
  const prescriptionQualityChecklist = React.useMemo(() => {
    const hasDoseIssue = currentMedicineRows.some((row) => normalizeStringValue(row.medicineName) && !normalizeStringValue(row.dosage));
    const hasFrequencyIssue = currentMedicineRows.some((row) => normalizeStringValue(row.medicineName) && !normalizeStringValue(row.frequency));
    const hasDurationIssue = currentMedicineRows.some((row) => normalizeStringValue(row.medicineName) && !normalizeStringValue(row.duration));
    const allergyCheck = prescriptionSafetyChecks.find((item) => item.label === "Allergy conflict checked");
    const interactionCheck = prescriptionSafetyChecks.find((item) => item.label === "Interaction checked");
    return [
      { label: "Diagnosis present", state: normalizeStringValue(prescriptionForm.diagnosisSnapshot || consultationForm.diagnosis) ? "complete" : "missing" },
      { label: "Medicine selected", state: currentMedicineRows.length ? "complete" : "missing" },
      { label: "Dose specified", state: hasDoseIssue ? "needs_review" : currentMedicineRows.length ? "complete" : "missing" },
      { label: "Frequency specified", state: hasFrequencyIssue ? "needs_review" : currentMedicineRows.length ? "complete" : "missing" },
      { label: "Duration specified", state: hasDurationIssue ? "needs_review" : currentMedicineRows.length ? "complete" : "missing" },
      { label: "Advice present", state: normalizeStringValue(prescriptionForm.advice || consultationForm.advice) ? "complete" : "missing" },
      { label: "Follow-up present", state: normalizeStringValue(prescriptionForm.followUpDate || consultationForm.followUpDate) ? "complete" : "missing" },
      { label: "Allergy conflict checked", state: allergyCheck?.status === "critical" ? "needs_review" : allergyCheck?.status === "needs_review" ? "needs_review" : allergyCheck?.status === "not_enough_data" ? "missing" : "complete" },
      { label: "Interaction checked", state: interactionCheck?.status === "not_enough_data" ? "missing" : "needs_review" },
    ] as Array<{ label: string; state: "complete" | "missing" | "needs_review" }>;
  }, [consultationForm.advice, consultationForm.diagnosis, consultationForm.followUpDate, currentMedicineRows, prescriptionForm.advice, prescriptionForm.diagnosisSnapshot, prescriptionForm.followUpDate, prescriptionSafetyChecks]);
  const prescriptionComparison = React.useMemo(() => {
    if (!latestPreviousPrescription) return null;
    const currentMap = new Map(currentMedicineRows.map((row) => [normalizeMedicationKey(row.medicineName), row]));
    const previousMap = new Map(latestPreviousMedicineRows.map((row) => [normalizeMedicationKey(row.medicineName), row]));
    const continued: string[] = [];
    const stopped: string[] = [];
    const newMeds: string[] = [];
    const doseChanged: string[] = [];
    currentMap.forEach((currentRow, key) => {
      const previousRow = previousMap.get(key);
      if (previousRow) {
        continued.push(currentRow.medicineName);
        if (
          normalizeStringValue(currentRow.dosage) !== normalizeStringValue(previousRow.dosage)
          || normalizeStringValue(currentRow.frequency) !== normalizeStringValue(previousRow.frequency)
          || normalizeStringValue(currentRow.duration) !== normalizeStringValue(previousRow.duration)
        ) {
          doseChanged.push(currentRow.medicineName);
        }
      } else {
        newMeds.push(currentRow.medicineName);
      }
    });
    previousMap.forEach((previousRow, key) => {
      if (!currentMap.has(key)) {
        stopped.push(previousRow.medicineName);
      }
    });
    const repeatedAntibiotic = currentMedicineRows.some((row) => isAntibioticMedicine(row.medicineName)) && latestPreviousMedicineNames.some((name) => isAntibioticMedicine(name));
    const repeatedPainkiller = currentMedicineRows.some((row) => isPainkillerMedicine(row.medicineName)) && latestPreviousMedicineNames.some((name) => isPainkillerMedicine(name));
    const longTermContinuity = continued.filter((name) => medicationNameMatches(name, ["amlodipine", "metformin", "telmisartan", "losartan", "atorvastatin", "levothyroxine", "insulin"]));
    return {
      previousPrescription: latestPreviousPrescription,
      continued,
      stopped,
      newMeds,
      doseChanged,
      repeatedAntibiotic,
      repeatedPainkiller,
      longTermContinuity,
    };
  }, [currentMedicineRows, latestPreviousMedicineNames, latestPreviousPrescription]);
  const workspaceTabSx = {
    minHeight: 48,
    textTransform: "none",
    fontWeight: 800,
    borderRadius: 999,
    px: 1.25,
    mr: 0.5,
    "&.Mui-selected": {
      color: "primary.main",
      bgcolor: "primary.50",
    },
  } as const;
  const reportComparisonPlaceholder = (
    <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
      <CardContent sx={{ p: 1.5 }}>
          <Stack spacing={1}>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <TrendingUpRoundedIcon fontSize="small" color="primary" />
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                AI-assisted trend summary
              </Typography>
            </Stack>
          <StructuredTrendSummary
            structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
            legacyTrends={clinicalContext?.labIntelligence.previousTrends || []}
            latestReport={clinicalContext?.labIntelligence.latestLabReport || null}
            summary={clinicalContext?.aiSummary || null}
          />
          <Typography variant="caption" color="text.secondary">
            AI-assisted. Uses existing clinical context only.
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );

  const patientPhone = patientRow?.mobile || currentAppointment?.patientMobile || "-";
  const patientBloodGroup = patientRow?.bloodGroup || null;
  const nextFollowUpLabel = consultationForm.followUpDate || prescriptionForm.followUpDate || patient?.upcomingAppointments?.[0]?.appointmentDate || null;
  const timelinePreview = activeTimeline.slice(0, 4);
  const labReportPreview = clinicalDocuments
    .filter((row) => ["LAB_REPORT", "X_RAY", "MRI_CT", "REFERRAL", "DISCHARGE_SUMMARY"].includes(row.documentType))
    .slice(0, 4);
  const summaryMetrics = [
    { label: "Age / Gender", value: formatPatientAgeGender(patientRow) || "Not recorded" },
    { label: "Patient number", value: patientRow?.patientNumber || consultation?.patientNumber || "No patient ID" },
    { label: "Phone", value: patientPhone },
    { label: "Blood group", value: patientBloodGroup || "Not recorded" },
    { label: "Allergies", value: patientRow?.allergies || "None recorded" },
    { label: "Chronic conditions", value: clinicalSnapshotConditions === "Not recorded" ? "None recorded" : clinicalSnapshotConditions },
    { label: "Last visit", value: compactDate(lastConsultation?.createdAt) },
    { label: "Next follow-up", value: nextFollowUpLabel ? compactDate(nextFollowUpLabel) : "None recorded" },
    { label: "Appointment", value: currentAppointment ? `${currentAppointment.status.replaceAll("_", " ")} • ${compactDate(currentAppointment.appointmentDate)}` : "No current appointment" },
  ];
  const summaryMetricIcon = (label: string) => {
    switch (label) {
      case "Age / Gender":
        return <PersonRoundedIcon fontSize="inherit" />;
      case "Patient number":
        return <BadgeRoundedIcon fontSize="inherit" />;
      case "Phone":
        return <CallRoundedIcon fontSize="inherit" />;
      case "Blood group":
        return <FavoriteRoundedIcon fontSize="inherit" />;
      case "Allergies":
        return <WarningAmberRoundedIcon fontSize="inherit" />;
      case "Chronic conditions":
        return <HealingRoundedIcon fontSize="inherit" />;
      case "Last visit":
        return <HistoryRoundedIcon fontSize="inherit" />;
      case "Next follow-up":
        return <EventRoundedIcon fontSize="inherit" />;
      case "Appointment":
        return <TimelineRoundedIcon fontSize="inherit" />;
      default:
        return <BadgeRoundedIcon fontSize="inherit" />;
    }
  };
  const openHistoryTab = () => {
    setActiveTab(2);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", consultationTabIndexToKey(2));
    nextParams.set("historyView", "timeline");
    setSearchParams(nextParams, { replace: true });
  };

  const setHistoryView = React.useCallback((nextView: HistoryViewKey) => {
    setActiveTab(2);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", consultationTabIndexToKey(2));
    nextParams.set("historyView", nextView);
    setSearchParams(nextParams, { replace: true });
  }, [searchParams, setSearchParams]);

  const toggleSection = (id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] }));

  const addMedicineFromCatalog = (medicine: MedicineShortcut) => {
    const row = {
      ...newMedicineRow(prescriptionForm.medicines.length),
      medicineName: medicine.medicineName,
      medicineType: medicine.medicineType,
      strength: medicine.strength || "",
      dosage: medicine.defaultDosage || "",
      frequency: medicine.defaultFrequency || "",
      duration: medicine.defaultDurationDays ? `${medicine.defaultDurationDays} days` : "",
      timing: medicine.defaultTiming || null,
      instructions: medicine.defaultInstructions || "",
    };
    if (!prescriptionReadOnly) {
      setInvalidMedicineRowIds([]);
      setPrescriptionForm((current) => ({ ...current, medicines: [...current.medicines.filter((item) => item.medicineName.trim()), row] }));
    }
  };

  const addMedicineFromAiSuggestion = (item: AiMedicineSuggestionItem) => {
    if (prescriptionReadOnly) return;
    const row = {
      ...newMedicineRow(prescriptionForm.medicines.length),
      medicineName: item.medicine,
      medicineType: null,
      strength: "",
      dosage: item.dose || "",
      frequency: item.frequency || "",
      duration: item.duration || "",
      timing: null,
      instructions: [item.reason, item.safetyNote].filter(Boolean).join(" • "),
    };
    setInvalidMedicineRowIds([]);
    setPrescriptionForm((current) => ({ ...current, medicines: [...current.medicines.filter((med) => med.medicineName.trim()), row] }));
  };

  const addManualMedicine = () => {
    if (!prescriptionReadOnly) {
      setInvalidMedicineRowIds([]);
      setPrescriptionForm((current) => ({ ...current, medicines: [...current.medicines, newMedicineRow(current.medicines.length)] }));
    }
  };

  const repeatPreviousPrescription = () => {
    const previous = previousPrescriptions[0];
    if (!previous || prescriptionReadOnly) return;
    const applyPrevious = () => {
      setPrescriptionForm((current) => ({
        ...current,
        diagnosisSnapshot: previous.diagnosisSnapshot || current.diagnosisSnapshot,
        advice: previous.advice || current.advice,
        followUpDate: previous.followUpDate || current.followUpDate,
        medicines: previous.medicines.map((item, index) => ({ ...item, localId: `repeat-${Date.now()}-${index}`, sortOrder: index + 1 })),
        recommendedTests: previous.recommendedTests.map((item, index) => ({ ...item, localId: `repeat-test-${Date.now()}-${index}`, sortOrder: index + 1 })),
      }));
      setInvalidMedicineRowIds([]);
      setSelectedRxTemplateKey("repeat-previous");
      setInfo("Previous prescription repeated into draft");
    };
    if (!hasPrescriptionContent(prescriptionForm)) {
      applyPrevious();
      return;
    }
    setWorkspaceConfirmation({
      kind: "fastPack",
      templateLabel: "Previous prescription",
      hasContent: true,
      onConfirm: applyPrevious,
    });
  };

  const updateMedicine = (localId: string, patch: Partial<MedicineRow>) => {
    if (!prescriptionReadOnly) {
      setInvalidMedicineRowIds((current) => current.filter((id) => id !== localId));
      setPrescriptionForm((current) => ({
        ...current,
        medicines: current.medicines.map((row) => (row.localId === localId ? { ...row, ...patch } : row)),
      }));
    }
  };

  const updateTest = (localId: string, patch: Partial<TestRow>) => {
    if (!prescriptionReadOnly) setPrescriptionForm((current) => ({
      ...current,
      recommendedTests: current.recommendedTests.map((row) => (row.localId === localId ? { ...row, ...patch } : row)),
    }));
  };

  const applyFollowUp = (chip: string) => {
    const date = followUpChipToDate(chip);
    setConsultationForm((current) => ({ ...current, followUpDate: date }));
    setPrescriptionForm((current) => ({ ...current, followUpDate: date }));
  };
  const applyPrescriptionFollowUpShortcut = React.useCallback((chip: string) => {
    setPrescriptionForm((current) => ({ ...current, followUpDate: followUpChipToDate(chip) }));
  }, []);
  const focusPrescriptionFollowUpInput = React.useCallback(() => {
    const input = prescriptionFollowUpInputRef.current;
    if (!input) return;
    const maybeInput = input as HTMLInputElement & { showPicker?: () => void };
    if (typeof maybeInput.showPicker === "function") {
      maybeInput.showPicker();
      return;
    }
    input.focus();
  }, []);

  const getAlternativeMedicineSuggestions = React.useCallback((row: MedicineRow) => {
    const normalizedCurrent = normalizeMedicationKey(row.medicineName);
    if (!normalizedCurrent) return [] as string[];
    const searchTokens = normalizedCurrent.split(" ").filter(Boolean).filter((token) => token.length > 2);
    const catalogMatches = medicineCatalog
      .filter((medicine) => medicine.active !== false)
      .filter((medicine) => normalizeMedicationKey(medicine.medicineName) !== normalizedCurrent)
      .filter((medicine) => {
        const candidate = normalizeMedicationKey(medicine.medicineName);
        const generic = normalizeMedicationKey(medicine.genericName);
        const brand = normalizeMedicationKey(medicine.brandName);
        return searchTokens.some((token) => candidate.includes(token) || generic.includes(token) || brand.includes(token));
      })
      .slice(0, 4)
      .map((medicine) => [medicine.genericName || medicine.brandName || medicine.medicineName, medicine.strength ? `• ${medicine.strength}` : ""].filter(Boolean).join(" "));
    const previousMatches = recentMedicineNames.filter((name) => normalizeMedicationKey(name) !== normalizedCurrent && searchTokens.some((token) => normalizeMedicationKey(name).includes(token)));
    return Array.from(new Set([...catalogMatches, ...previousMatches])).slice(0, 5);
  }, [medicineCatalog, recentMedicineNames]);

  const applyRxTemplate = (template: RxTemplate) => {
    if (prescriptionReadOnly) return;
    const applyTemplate = () => {
      const rows = templateToRows(template);
      setPrescriptionForm((current) => ({
        ...current,
        diagnosisSnapshot: template.diagnosis,
        advice: appendTokenLine(current.advice, template.advice),
        followUpDate: followUpChipToDate(template.followUp),
        medicines: rows.meds,
        recommendedTests: rows.tests,
      }));
      setConsultationForm((current) => ({
        ...current,
        diagnosis: current.diagnosis.trim() ? current.diagnosis : template.diagnosis,
        advice: appendTokenLine(current.advice, template.advice),
        followUpDate: followUpChipToDate(template.followUp),
      }));
      setInvalidMedicineRowIds([]);
      setSelectedRxTemplateKey(template.key);
      setInfo(`${template.label} applied`);
    };
    if (hasPrescriptionContent(prescriptionForm)) {
      setWorkspaceConfirmation({
        kind: "fastPack",
        templateLabel: template.label,
        hasContent: true,
        onConfirm: applyTemplate,
      });
      return;
    }
    applyTemplate();
  };

  const aiSummaryText = React.useMemo(() => {
    if (clinicalSummary) {
      const structured = parseStructuredObject(clinicalSummary.structuredData || {});
      const summary = typeof structured.summary === "string" ? structured.summary.trim() : "";
      return summary || (clinicalSummary.draft || clinicalSummary.message || "").trim();
    }
    return savedAiSummary?.summary?.trim() || "";
  }, [clinicalSummary, savedAiSummary?.summary]);
  const aiSummaryHasContent = Boolean(aiSummaryText);
  const aiSummaryHasState = Boolean(clinicalSummary || savedAiSummary);

  const aiSummaryProviderLabel = clinicalSummary?.provider || savedAiSummary?.provider || null;
  const aiSummaryModelLabel = clinicalSummary?.model || savedAiSummary?.model || null;
  const aiSummaryGeneratedAtLabel = clinicalSummary
    ? (savedAiSummary?.generatedAt || new Date().toISOString())
    : (savedAiSummary?.generatedAt || null);
  const lastAiActivityLabel = React.useMemo(() => {
    const timestamps = [
      aivaChatMessages[aivaChatMessages.length - 1]?.createdAt || null,
      clinicalSummary ? (savedAiSummary?.createdAt || savedAiSummary?.updatedAt || savedAiSummary?.generatedAt || null) : null,
      savedAiSummary?.generatedAt || savedAiSummary?.createdAt || savedAiSummary?.updatedAt || null,
      clinicalReasoningResult?.generatedAt || null,
      consultationSoap?.generatedAt || null,
      prescriptionSuggestionGeneratedAt || null,
      prescriptionInstructionsGeneratedAt || null,
      labOrderAiPreparation?.generatedAt || null,
    ].filter((value): value is string => Boolean(value));
    return timestamps.reduce<string | null>((latest, current) => (!latest || current > latest ? current : latest), null);
  }, [
    aivaChatMessages,
    clinicalReasoningResult?.generatedAt,
    clinicalSummary,
    consultationSoap?.generatedAt,
    labOrderAiPreparation?.generatedAt,
    prescriptionInstructionsGeneratedAt,
    prescriptionSuggestionGeneratedAt,
    savedAiSummary?.createdAt,
    savedAiSummary?.generatedAt,
    savedAiSummary?.updatedAt,
  ]);

  const consultationDocumentationGuide = React.useMemo(() => {
    const hasChiefComplaint = Boolean(consultationForm.chiefComplaints.trim());
    const hasSymptoms = Boolean(consultationForm.symptoms.trim());
    const hasVitals = Boolean(
      consultationForm.bloodPressureSystolic.trim()
        || consultationForm.bloodPressureDiastolic.trim()
        || consultationForm.pulseRate.trim()
        || consultationForm.temperature.trim()
        || consultationForm.spo2.trim()
        || consultationForm.respiratoryRate.trim()
        || consultationForm.weightKg.trim()
        || consultationForm.heightCm.trim(),
    ) || Boolean(clinicalContext?.intakeSummary?.latestVitals);
    const diagnosisNeedsReview = clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED");
    const hasDiagnosis = Boolean(consultationForm.diagnosis.trim());
    const hasSoap = Boolean(
      consultationSoapForm.subjective.trim()
      || consultationSoapForm.objective.trim()
      || consultationSoapForm.assessment.trim()
      || consultationSoapForm.plan.trim()
    );
    const hasAdvice = Boolean(consultationForm.advice.trim());
    const hasFollowUp = Boolean(consultationForm.followUpDate.trim());
    const items: ConsultationCompletionItem[] = [
      { label: "Chief Complaint", stateLabel: hasChiefComplaint ? "Recorded" : "Not recorded", detail: hasChiefComplaint ? "Recorded in consultation notes." : "Document the presenting complaint.", tone: hasChiefComplaint ? "success" : "default", prepared: hasChiefComplaint },
      { label: "Symptoms", stateLabel: hasSymptoms ? "Recorded" : "Not recorded", detail: hasSymptoms ? "Symptoms are captured." : "Document the current symptoms.", tone: hasSymptoms ? "success" : "default", prepared: hasSymptoms },
      { label: "Vitals", stateLabel: hasVitals ? "Recorded" : "Not recorded", detail: hasVitals ? "Vitals are available in the workspace." : "Capture vitals when clinically relevant.", tone: hasVitals ? "success" : "default", prepared: hasVitals },
      {
        label: "Diagnosis",
        stateLabel: hasDiagnosis ? (diagnosisNeedsReview ? "Review recommended" : "Recorded") : "Not recorded",
        detail: hasDiagnosis
          ? (diagnosisNeedsReview ? "AI diagnosis draft still needs review." : "Diagnosis is recorded.")
          : "Document the working diagnosis.",
        tone: hasDiagnosis ? (diagnosisNeedsReview ? "warning" : "success") : "default",
        prepared: hasDiagnosis && !diagnosisNeedsReview,
      },
      { label: "SOAP", stateLabel: hasSoap ? "Recorded" : "Not recorded", detail: hasSoap ? "SOAP note is present." : "Document the SOAP summary.", tone: hasSoap ? "success" : "default", prepared: hasSoap },
      { label: "Advice", stateLabel: hasAdvice ? "Recorded" : "Not recorded", detail: hasAdvice ? "Advice is recorded." : "Document advice when provided.", tone: hasAdvice ? "success" : "default", prepared: hasAdvice },
      { label: "Follow-up", stateLabel: hasFollowUp ? "Recorded" : "Not recorded", detail: hasFollowUp ? "Follow-up is recorded." : "Record follow-up when planned.", tone: hasFollowUp ? "success" : "default", prepared: hasFollowUp },
    ];
    return {
      items,
      recordedCount: items.filter((item) => item.stateLabel === "Recorded").length,
      reviewRecommendedCount: items.filter((item) => item.stateLabel === "Review recommended").length,
      notRecordedCount: items.filter((item) => item.stateLabel === "Not recorded").length,
    };
  }, [
    clinicalContext?.intakeSummary?.latestVitals,
    clinicalDraftEntries,
    consultationForm.advice,
    consultationForm.bloodPressureDiastolic,
    consultationForm.bloodPressureSystolic,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.followUpDate,
    consultationForm.heightCm,
    consultationForm.pulseRate,
    consultationForm.respiratoryRate,
    consultationForm.spo2,
    consultationForm.symptoms,
    consultationForm.temperature,
    consultationForm.weightKg,
  ]);
  const soapDirty = React.useMemo(
    () => serializeConsultationSoapForm(consultationSoapForm) !== savedConsultationSoapSnapshotRef.current,
    [consultationSoapForm],
  );
  const soapHasPersistedRecord = Boolean(consultationSoap?.id);
  const soapStale = consultationSoap?.status === "ACCEPTED" && consultationSoap.stale;
  const soapStatusLabel = consultationSoap?.status === "ACCEPTED"
    ? (soapStale ? "Review recommended" : "Current")
    : consultationSoap?.status === "DRAFT"
      ? "Draft"
      : null;
  const soapStatusSeverity = consultationSoap?.status === "ACCEPTED"
    ? (soapStale ? "warning" : "info")
    : consultationSoap?.status === "DRAFT"
      ? "info"
      : "info";

  const consultationCompletionSummary = React.useMemo(() => {
    const hasVisitSummary = consultationDocumentDrafts.visitSummary.status === "ACCEPTED" || consultationDocumentDrafts.visitSummary.status === "EDITED";
    const hasReferral = consultationDocumentDrafts.referral.status === "ACCEPTED" || consultationDocumentDrafts.referral.status === "EDITED";
    const hasCertificate = consultationDocumentDrafts.certificate.status === "ACCEPTED" || consultationDocumentDrafts.certificate.status === "EDITED";
    const hasInstructions = Boolean((prescriptionInstructionsDraft?.draft || prescriptionInstructionsDraft?.message || consultationForm.advice).trim());
    const hasFollowUp = Boolean((consultationForm.followUpDate || prescriptionForm.followUpDate).trim());
    const labWorkReviewed = Boolean(!labOrders.length || labOrders.some((order) => order.reportPublishedAt || order.reportFilename));
    const requiredItems: ConsultationCompletionItem[] = [
      {
        label: "Prescription",
        stateLabel: prescriptionReadyForCompletion ? "Ready" : "Not finalized",
        detail: prescriptionReadyForCompletion
          ? "Finalized, printed, or sent."
          : CONSULTATION_COMPLETION_BLOCKED_MESSAGE,
        tone: prescriptionReadyForCompletion ? "success" : "error",
        prepared: prescriptionReadyForCompletion,
      },
    ];
    const documentationItems: ConsultationCompletionItem[] = [
      {
        label: "Chief Complaint",
        stateLabel: consultationForm.chiefComplaints.trim() ? "Recorded" : "Not recorded",
        detail: consultationForm.chiefComplaints.trim() ? "Recorded in consultation notes." : "Document the presenting complaint.",
        tone: consultationForm.chiefComplaints.trim() ? "success" : "default",
        prepared: Boolean(consultationForm.chiefComplaints.trim()),
      },
      {
        label: "Symptoms",
        stateLabel: consultationForm.symptoms.trim() ? "Recorded" : "Not recorded",
        detail: consultationForm.symptoms.trim() ? "Symptoms are captured." : "Document the current symptoms.",
        tone: consultationForm.symptoms.trim() ? "success" : "default",
        prepared: Boolean(consultationForm.symptoms.trim()),
      },
      {
        label: "Diagnosis",
        stateLabel: consultationForm.diagnosis.trim()
          ? (clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED") ? "Review recommended" : "Recorded")
          : "Not recorded",
        detail: consultationForm.diagnosis.trim()
          ? (clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED")
            ? "AI diagnosis draft still needs review."
            : "Diagnosis is recorded.")
          : "Document the working diagnosis.",
        tone: consultationForm.diagnosis.trim()
          ? (clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED") ? "warning" : "success")
          : "default",
        prepared: Boolean(consultationForm.diagnosis.trim()) && !clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED"),
      },
      {
        label: "SOAP",
        stateLabel: consultationForm.clinicalNotes.trim() || consultationForm.diagnosis.trim() || consultationForm.advice.trim() ? "Recorded" : "Not recorded",
        detail: consultationForm.clinicalNotes.trim() || consultationForm.diagnosis.trim() || consultationForm.advice.trim()
          ? "SOAP assessment is present."
          : "Document the SOAP summary.",
        tone: consultationForm.clinicalNotes.trim() || consultationForm.diagnosis.trim() || consultationForm.advice.trim() ? "success" : "default",
        prepared: Boolean(consultationForm.clinicalNotes.trim() || consultationForm.diagnosis.trim() || consultationForm.advice.trim()),
      },
      {
        label: "Advice",
        stateLabel: consultationForm.advice.trim() ? "Recorded" : "Not recorded",
        detail: consultationForm.advice.trim() ? "Advice is recorded." : "Document advice when provided.",
        tone: consultationForm.advice.trim() ? "success" : "default",
        prepared: Boolean(consultationForm.advice.trim()),
      },
      {
        label: "Follow-up",
        stateLabel: hasFollowUp ? "Recorded" : "Not recorded",
        detail: hasFollowUp ? "Follow-up is recorded." : "Record follow-up when planned.",
        tone: hasFollowUp ? "success" : "default",
        prepared: hasFollowUp,
      },
    ];
    const optionalItems: ConsultationCompletionItem[] = [
      {
        label: "Investigations",
        stateLabel: recommendedTestSuggestions.length ? "Available" : "Not applicable",
        detail: recommendedTestSuggestions.length ? "Suggested investigations are available for review." : "No investigation suggestions currently.",
        tone: recommendedTestSuggestions.length ? "info" : "default",
        prepared: false,
      },
      {
        label: "Lab Orders",
        stateLabel: labOrders.length ? "Added" : (recommendedTestSuggestions.length ? "Not added" : "Not applicable"),
        detail: labOrders.length
          ? `${labOrders.length} order${labOrders.length === 1 ? "" : "s"} created.`
          : (recommendedTestSuggestions.length ? "No lab order created yet." : "No lab orders are needed."),
        tone: labOrders.length ? "success" : "default",
        prepared: Boolean(labOrders.length),
      },
      {
        label: "Patient Instructions",
        stateLabel: hasInstructions ? "Prepared" : "Not generated",
        detail: hasInstructions ? "Patient instructions are available." : "No patient instructions generated yet.",
        tone: hasInstructions ? "success" : "default",
        prepared: hasInstructions,
      },
      {
        label: "Visit Summary",
        stateLabel: hasVisitSummary ? "Generated" : "Not generated",
        detail: hasVisitSummary ? "Visit summary is ready." : "No visit summary generated yet.",
        tone: hasVisitSummary ? "success" : "default",
        prepared: hasVisitSummary,
      },
      {
        label: "Referral",
        stateLabel: hasReferral ? "Generated" : "Not created",
        detail: hasReferral ? "Referral is ready." : "No referral generated yet.",
        tone: hasReferral ? "success" : "default",
        prepared: hasReferral,
      },
      {
        label: "Certificate",
        stateLabel: hasCertificate ? "Generated" : "Not created",
        detail: hasCertificate ? "Certificate is ready." : "No certificate generated yet.",
        tone: hasCertificate ? "success" : "default",
        prepared: hasCertificate,
      },
    ];
    const blocked = !prescriptionReadyForCompletion;
    const documentationReviewCount = documentationItems.filter((item) => item.stateLabel === "Review recommended").length;
    const documentationRecordedCount = documentationItems.filter((item) => item.stateLabel === "Recorded").length;
    const documentationNotRecordedCount = documentationItems.filter((item) => item.stateLabel === "Not recorded").length;
    const optionalPreparedCount = optionalItems.filter((item) => item.prepared).length;
    const reviewRecommended = !blocked && (documentationReviewCount > 0 || documentationNotRecordedCount > 0);
    const statusTone: ConsultationCompletionTone = blocked ? "error" : reviewRecommended ? "warning" : "success";
    const statusLabel = blocked ? "Blocked" : reviewRecommended ? "Review recommended" : "Ready to complete";
    const statusDetail = blocked
      ? CONSULTATION_COMPLETION_BLOCKED_MESSAGE
      : reviewRecommended
        ? "Documentation gaps are advisory only."
        : "Consultation is ready for completion.";
    return {
      groups: [
        {
          title: "Required to complete",
          helper: "Only hard blockers are shown here.",
          summaryLabel: `${blocked ? 0 : 1}/1 ready`,
          summaryTone: statusTone,
          items: requiredItems,
        },
        {
          title: "Clinical documentation",
          helper: "Advisory documentation signals, not completion blockers.",
          summaryLabel: `${documentationRecordedCount} recorded${documentationReviewCount ? `, ${documentationReviewCount} review recommended` : ""}`,
          summaryTone: reviewRecommended ? "warning" : "success",
          items: documentationItems,
        },
        {
          title: "When applicable / Optional outputs",
          helper: "Optional outputs stay neutral when they are not relevant.",
          summaryLabel: `${optionalPreparedCount} prepared`,
          summaryTone: optionalPreparedCount ? "success" : "default",
          items: optionalItems,
        },
      ] satisfies ConsultationCompletionGroup[],
      statusTone,
      statusLabel,
      statusDetail,
      requiredReadyCount: blocked ? 0 : 1,
      documentationRecordedCount,
      documentationReviewCount,
      optionalPreparedCount,
    };
  }, [
    clinicalDraftEntries,
    consultationDocumentDrafts.certificate.status,
    consultationDocumentDrafts.referral.status,
    consultationDocumentDrafts.visitSummary.status,
    consultationForm.advice,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.followUpDate,
    consultationForm.chiefComplaints,
    consultationForm.symptoms,
    labOrders,
    prescriptionReadyForCompletion,
    prescriptionForm.followUpDate,
    prescriptionInstructionsDraft?.draft,
    prescriptionInstructionsDraft?.message,
    recommendedTestSuggestions.length,
  ]);

  const copyAiSummaryToClipboard = async () => {
    if (!aiSummaryText) return;
    try {
      await navigator.clipboard.writeText(aiSummaryText);
      setInfo("AI summary copied to clipboard");
    } catch {
      setError("Unable to copy AI summary. Please select and copy manually.");
    }
  };

  const applyAiSummaryToConsultationNotes = () => {
    if (!aiSummaryText) return;
    setConsultationForm((current) => ({
      ...current,
      clinicalNotes: appendTokenLine(current.clinicalNotes, aiSummaryText),
    }));
  };

  const saveConsultationDraft = async (showInfo = false): Promise<Consultation | null> => {
    const currentConsultation = consultationRef.current;
    const currentForm = consultationFormRef.current;
    if (!auth.accessToken || !auth.tenantId || !currentConsultation || currentConsultation.status !== "DRAFT") return currentConsultation;
    const parsed = consultationSchema.safeParse({
      chiefComplaint: currentForm.chiefComplaints,
      diagnosis: currentForm.diagnosis,
      followUpDate: currentForm.followUpDate || undefined,
      notes: currentForm.clinicalNotes,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message || "Failed to save consultation draft");
      return currentConsultation;
    }
    const saved = await updateConsultation(auth.accessToken, auth.tenantId, currentConsultation.id, toConsultationInput(currentForm, currentConsultation));
    const persisted = await getConsultation(auth.accessToken, auth.tenantId, currentConsultation.id).catch(() => saved);
    const merged = currentConsultation ? { ...currentConsultation, ...persisted } : persisted;
    const nextForm = emptyConsultationForm(merged);
    setConsultation(merged);
    consultationRef.current = merged;
    setConsultationForm(nextForm);
    consultationFormRef.current = nextForm;
    savedConsultationSnapshotRef.current = serializeConsultationForm(nextForm);
    void clinicalReasoning.loadReasoning(merged.id);
    if (showInfo) setInfo("Consultation draft saved");
    return saved;
  };

  const saveConsultationSoapDraft = async (showInfo = false): Promise<ConsultationSoapNote | null> => {
    const currentConsultation = consultationRef.current;
    const currentForm = consultationSoapFormRef.current;
    const currentSoap = consultationSoap;
    if (!auth.accessToken || !auth.tenantId || !currentConsultation) return currentSoap;
    const serializedForm = serializeConsultationSoapForm(currentForm);
    const soapDirty = serializedForm !== savedConsultationSoapSnapshotRef.current;
    if (!soapDirty && currentSoap) {
      if (showInfo) setInfo("SOAP saved");
      return currentSoap;
    }
    const body: ConsultationSoapInput = {
      subjective: currentForm.subjective.trim() || null,
      objective: currentForm.objective.trim() || null,
      assessment: currentForm.assessment.trim() || null,
      plan: currentForm.plan.trim() || null,
      aiProvider: currentSoap?.aiProvider || null,
      aiModel: currentSoap?.aiModel || null,
      generatedAt: currentSoap?.generatedAt || null,
    };
    const saved = await saveConsultationSoap(auth.accessToken, auth.tenantId, currentConsultation.id, body);
    setConsultationSoap(saved);
    const nextForm = emptyConsultationSoapForm(saved);
    setConsultationSoapForm(nextForm);
    consultationSoapFormRef.current = nextForm;
    savedConsultationSoapSnapshotRef.current = serializeConsultationSoapForm(nextForm);
    if (showInfo) setInfo("SOAP saved");
    return saved;
  };

  const copyConsultationSoapDraft = React.useCallback(async () => {
    const text = serializeConsultationSoapForm(consultationSoapFormRef.current).trim();
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setInfo("SOAP copied to clipboard");
  }, []);

  const focusConsultationSoapEditor = React.useCallback(() => {
    setExpanded((current) => ({ ...current, notes: true }));
    window.requestAnimationFrame(() => {
      soapSubjectiveInputRef.current?.focus();
    });
  }, []);

  const runAutosave = async (): Promise<Consultation | null> => {
    if (autosavePromiseRef.current) return autosavePromiseRef.current;

    const promise = (async () => {
      const currentConsultation = consultationRef.current;
      const currentPrescription = prescriptionRef.current;
      if (!currentConsultation) {
        setAutosaveStatus("readonly");
        return null;
      }
      if (currentConsultation.status !== "DRAFT" && (!currentPrescription || !isEditablePrescriptionStatus(currentPrescription.status))) {
        setAutosaveStatus("readonly");
        return currentConsultation;
      }
      if (autosaveInFlightRef.current) return currentConsultation;

      const consultationDirty = currentConsultation.status === "DRAFT" && serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
      const prescriptionDirty = currentPrescription != null && isEditablePrescriptionStatus(currentPrescription.status) && serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
      if (!consultationDirty && !prescriptionDirty) {
        setAutosaveStatus("saved");
        return currentConsultation;
      }

      autosaveInFlightRef.current = true;
      setAutosaveStatus("saving");
      try {
        const savedConsultation = consultationDirty ? await saveConsultationDraft(false) : currentConsultation;
        if (prescriptionDirty) await savePrescriptionDraft(false, { refreshHistory: false });
        setError(null);
        setAutosaveStatus("saved");
        return savedConsultation;
      } catch (err) {
        console.error("Autosave failed", err);
        setAutosaveStatus("failed");
        if (err instanceof PrescriptionPayloadValidationError) {
          setInvalidMedicineRowIds(err.invalidMedicineRowIds);
          setError(err.message);
          return null;
        }
        if (err instanceof ApiClientError && err.status >= 400 && err.status < 500) {
          setError(err.message);
          return null;
        }
        if (autosaveRetryTimerRef.current) window.clearTimeout(autosaveRetryTimerRef.current);
        autosaveRetryTimerRef.current = window.setTimeout(() => {
          void runAutosave();
        }, 5000);
        setError(err instanceof Error ? err.message : "Failed to save consultation draft");
        return null;
      } finally {
        autosaveInFlightRef.current = false;
        autosavePromiseRef.current = null;
      }
    })();

    autosavePromiseRef.current = promise;
    return promise;
  };

  const flushAutosave = async (): Promise<Consultation | null> => {
    clearAutosaveTimers();
    savingRef.current = true;
    setSaving(true);
    try {
      return autosavePromiseRef.current ? await autosavePromiseRef.current : await runAutosave();
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const backToQueue = async () => {
    const consultationDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
    const prescriptionDirty = serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
    if (consultationDirty || prescriptionDirty) {
      const saved = await flushAutosave();
      const stillDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current
        || serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
      if (!saved || stillDirty) {
        const proceed = await requestBooleanConfirmation({
          title: "Leave without saving?",
          description: "Unsaved consultation changes remain. Leave without saving?",
          confirmLabel: "Leave page",
          confirmColor: "warning",
        });
        if (!proceed) return;
      }
    }
    navigate("/queue");
  };

  const manualSaveDraft = async () => {
    if (savingRef.current || autosaveInFlightRef.current) {
      return;
    }
    setError(null);
    const saved = await preserveViewport(() => flushAutosave());
    if (saved) {
      setInfo("Draft saved");
      return;
    }
    const consultationDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
    const prescriptionDirty = serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
    if (consultationDirty || prescriptionDirty) {
      setError("Failed to save consultation draft. Please try again.");
    }
  };

  React.useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s" && canEditConsultation && (!consultationReadOnly || !prescriptionReadOnly)) {
        event.preventDefault();
        void manualSaveDraft();
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "p" && canEditConsultation && !prescriptionReadOnly && hasPrescriptionContent(prescriptionForm)) {
        event.preventDefault();
        void previewCurrentPrescription();
        return;
      }
      if (event.key === "Escape" && viewerDocument) {
        event.preventDefault();
        setViewerDocument(null);
        setViewerUrl(null);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [canEditConsultation, manualSaveDraft, prescriptionForm, prescriptionReadOnly, consultationReadOnly, viewerDocument]);

  const completeCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    if (!prescriptionReadyForCompletion) {
      setError(CONSULTATION_COMPLETION_BLOCKED_MESSAGE);
      return;
    }
    const saved = await flushAutosave();
    if (!saved) return;
    setSaving(true);
    try {
      const completed = await completeConsultation(auth.accessToken, auth.tenantId, consultation.id);
      const merged = consultation ? { ...consultation, ...completed } : completed;
      setConsultation(merged);
      consultationRef.current = merged;
      setAutosaveStatus("readonly");
      setInfo("Consultation completed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete consultation");
    } finally {
      setSaving(false);
    }
  };

  const continueActivePrescriptionDraft = async () => {
    if (!prescription || openingCorrectionDraft || saving) return;
    setError(null);
    setOpeningCorrectionDraft(true);
    try {
      setInfo("Opening correction draft...");
      await Promise.resolve();
      if (selectedPrescriptionVersionId === prescription.id) {
        setInfo("Correction draft is already open");
        return;
      }
      const activeForm = emptyPrescriptionForm(prescription, consultation || undefined);
      setSelectedPrescriptionVersionId(prescription.id);
      setPrescriptionForm(activeForm);
      savedPrescriptionSnapshotRef.current = serializePrescriptionForm(activeForm);
      setInfo("Correction draft opened");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open correction draft");
    } finally {
      setOpeningCorrectionDraft(false);
    }
  };

  const discardCurrentPrescriptionDraft = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !prescription) return;
    if (!currentPrescriptionIsActiveCorrectionDraft) return;
    setSaving(true);
    setError(null);
    try {
      await cancelPrescription(auth.accessToken, auth.tenantId, prescription.id);
      const activePrescription = await getConsultationPrescription(auth.accessToken, auth.tenantId, consultation.id);
      const merged = prescription ? { ...prescription, ...activePrescription } : activePrescription;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const activeForm = emptyPrescriptionForm(activePrescription, consultation);
      setPrescriptionForm(activeForm);
      savedPrescriptionSnapshotRef.current = serializePrescriptionForm(activeForm);
      setInvalidMedicineRowIds([]);
      const historyRows = activePrescription
        ? await getPrescriptionHistory(auth.accessToken, auth.tenantId, activePrescription.id).catch(() => [activePrescription])
        : [];
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return activePrescription?.id || historyRows[historyRows.length - 1]?.id || null;
      });
      setInfo("Prescription draft discarded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to discard prescription draft");
    } finally {
      setSaving(false);
    }
  };

  const cancelCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    setSaving(true);
    try {
      const cancelled = await cancelConsultation(auth.accessToken, auth.tenantId, consultation.id);
      const merged = consultation ? { ...consultation, ...cancelled } : cancelled;
      setConsultation(merged);
      consultationRef.current = merged;
      setInfo("Consultation cancelled");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel consultation");
    } finally {
      setSaving(false);
    }
  };

  const savePrescriptionDraft = async (
    showInfo = false,
    options?: { refreshHistory?: boolean },
  ): Promise<Prescription | null> => {
    const currentConsultation = consultationRef.current;
    const currentPrescription = prescriptionRef.current;
    const currentForm = prescriptionFormRef.current;
    if (!auth.accessToken || !auth.tenantId || !currentConsultation) return currentPrescription;
    if (!currentPrescription && currentConsultation.status !== "DRAFT") return null;
    if (currentPrescription && currentPrescription.status !== "DRAFT" && currentPrescription.status !== "PREVIEWED") return currentPrescription;
    if (!currentPrescription && !hasPrescriptionContent(currentForm)) return null;
    const serializedForm = serializePrescriptionForm(currentForm);
    const prescriptionDirty = currentPrescription != null
      && isEditablePrescriptionStatus(currentPrescription.status)
      && serializedForm !== savedPrescriptionSnapshotRef.current;
    if (currentPrescription && !prescriptionDirty) {
      if (showInfo) setInfo("Prescription draft saved");
      return currentPrescription;
    }
    const body = buildPrescriptionInput(currentForm, currentConsultation);
    const saved = currentPrescription
      ? await updatePrescription(auth.accessToken, auth.tenantId, currentPrescription.id, body)
      : await createPrescription(auth.accessToken, auth.tenantId, body);
    const merged = currentPrescription ? { ...currentPrescription, ...saved } : saved;
    setPrescription(merged);
    prescriptionRef.current = merged;
    setSelectedPrescriptionVersionId(saved.id);
    savedPrescriptionSnapshotRef.current = serializedForm;
    setInvalidMedicineRowIds([]);
    if (options?.refreshHistory !== false) {
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, saved.id).catch(() => [saved]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return historyRows[historyRows.length - 1]?.id || saved.id;
      });
    }
    if (showInfo) setInfo("Prescription draft saved");
    return saved;
  };

  const persistPrescription = async (): Promise<Prescription | null> => {
    setSaving(true);
    setError(null);
    setAutosaveStatus("saving");
    try {
      clearAutosaveTimers();
      if (autosavePromiseRef.current) {
        await autosavePromiseRef.current;
      }
      const saved = await savePrescriptionDraft(true, { refreshHistory: true });
      setError(null);
      setAutosaveStatus("saved");
      return saved;
    } catch (err) {
      setAutosaveStatus("failed");
      if (err instanceof PrescriptionPayloadValidationError) {
        setInvalidMedicineRowIds(err.invalidMedicineRowIds);
      }
      setError(err instanceof Error ? err.message : "Failed to save prescription");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const previewCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!hasPrescriptionContent(prescriptionForm)) {
      setError("Add consultation notes or a medicine before previewing.");
      return;
    }
    const popup = window.open("", "_blank", "noopener,noreferrer");
    const saved = await preserveViewport(() => persistPrescription());
    if (!saved) {
      popup?.close();
      return;
    }
    setSaving(true);
    try {
      const previewed = await previewPrescription(auth.accessToken, auth.tenantId, saved.id);
      const merged = prescription ? { ...prescription, ...previewed } : previewed;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, previewed.id).catch(() => [previewed]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return historyRows[historyRows.length - 1]?.id || previewed.id;
      });
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, previewed.id);
      const url = URL.createObjectURL(blob);
      if (popup) {
        popup.location.href = url;
      } else {
        window.open(url, "_blank", "noopener,noreferrer");
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription preview opened. You can still edit before finalizing.");
    } catch (err) {
      popup?.close();
      setError("Unable to preview prescription. Please try again.");
      console.error("Prescription preview failed", err);
    } finally {
      setSaving(false);
    }
  };

  const openClinicalDocument = async (document: ClinicalDocument) => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    setViewerDocument(document);
    setViewerUrl(null);
    try {
      const response = await getPatientDocumentViewUrl(auth.accessToken, auth.tenantId, consultation.patientId, document.id);
      setViewerUrl(URL.createObjectURL(response.blob));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open clinical document");
    }
  };

  const openClinicalDocumentById = React.useCallback(async (documentId: string) => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    const cachedDocument = clinicalDocuments.find((document) => document.id === documentId) || null;
    try {
      const document = cachedDocument || await getClinicalDocument(auth.accessToken, auth.tenantId, documentId);
      await openClinicalDocument(document);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open clinical document");
    }
  }, [auth.accessToken, auth.tenantId, clinicalDocuments, consultation, openClinicalDocument]);

  const createCorrectionDraft = async (flowType: "SAME_DAY_CORRECTION" | "FOLLOW_UP") => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !prescription) return;
    if (currentPrescriptionIsEditableDraft) {
      setError("Continue or finalize the active draft before starting a new correction.");
      return;
    }
    if (!selectedPrescriptionIsLatest) {
      setError("Only the latest prescription version can be corrected.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const corrected = await createPrescriptionCorrection(auth.accessToken, auth.tenantId, prescription.id, {
        correctionReason: correctionReason.trim() || (flowType === "FOLLOW_UP" ? "Follow-up prescription" : "Same-day correction"),
        flowType,
        prescription: buildPrescriptionInput(prescriptionForm, consultation),
      });
      const merged = prescription ? { ...prescription, ...corrected } : corrected;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const correctedForm = emptyPrescriptionForm(corrected, consultation);
      setPrescriptionForm(correctedForm);
      savedPrescriptionSnapshotRef.current = serializePrescriptionForm(correctedForm);
      setInvalidMedicineRowIds([]);
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, corrected.id).catch(() => [corrected]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId(corrected.id);
      setInfo(flowType === "FOLLOW_UP" ? "Follow-up prescription draft created" : "Correction draft created");
    } catch (err) {
      if (err instanceof PrescriptionPayloadValidationError) {
        setInvalidMedicineRowIds(err.invalidMedicineRowIds);
        setError(err.message);
      } else {
        setError("Unable to create prescription correction. Please try again.");
      }
      console.error("Prescription correction failed", err);
    } finally {
      setSaving(false);
    }
  };

  const finalizeCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!hasPrescriptionContent(prescriptionForm)) {
      setError("Add consultation notes or a medicine before finalizing.");
      return;
    }
    if (!hasAtLeastOneValidMedicineRow(prescriptionForm)) {
      const proceed = await requestBooleanConfirmation({
        title: "Continue without prescription?",
        description: "No medicines added. Continue without prescription?",
        confirmLabel: "Continue",
        confirmColor: "warning",
      });
      if (!proceed) {
        return;
      }
    }
    const currentPrescription = prescriptionRef.current;
    const prescriptionDirty = currentPrescription != null
      && isEditablePrescriptionStatus(currentPrescription.status)
      && serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
    const saved = prescriptionDirty
      ? await preserveViewport(() => persistPrescription())
      : currentPrescription;
    if (!saved) return;
    setSaving(true);
    try {
      const finalized = await finalizePrescription(auth.accessToken, auth.tenantId, saved.id);
      const merged = prescription ? { ...prescription, ...finalized } : finalized;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, finalized.id).catch(() => [finalized]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return historyRows[historyRows.length - 1]?.id || finalized.id;
      });
      setInfo("Prescription finalized");
    } catch (err) {
      if (err instanceof ApiClientError && typeof err.code === "string" && err.code.startsWith("SAFETY_")) {
        setPrescriptionIntelligenceTab("safety");
        setPrescriptionSafetyDetailsOpen(true);
        void refreshMedicationSafety();
      }
      setError(err instanceof Error ? err.message : "Failed to finalize prescription");
    } finally {
      setSaving(false);
    }
  };

  const printCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!hasPrescriptionContent(prescriptionForm)) {
      setError("Add consultation notes or a medicine before printing.");
      return;
    }
    const popup = window.open("", "_blank");
    const saved = await preserveViewport(() => persistPrescription());
    if (!saved) {
      popup?.close();
      return;
    }
    setSaving(true);
    try {
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, saved.id);
      const printed = await printPrescription(auth.accessToken, auth.tenantId, saved.id);
      const merged = prescription ? { ...prescription, ...printed } : printed;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, printed.id).catch(() => [printed]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return historyRows[historyRows.length - 1]?.id || printed.id;
      });
      const url = URL.createObjectURL(blob);
      if (popup) {
        popup.location.href = url;
        popup.onload = () => popup.print();
      } else {
        window.open(url, "_blank", "noopener,noreferrer");
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription PDF opened for printing");
    } catch (err) {
      popup?.close();
      setError(err instanceof Error ? err.message : "Failed to open prescription PDF");
    } finally {
      setSaving(false);
    }
  };

  const downloadCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!hasPrescriptionContent(prescriptionForm)) {
      setError("Add consultation notes or a medicine before downloading.");
      return;
    }
    const saved = await preserveViewport(() => persistPrescription());
    if (!saved) return;
    try {
      const { blob, filename } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, saved.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription PDF downloaded");
    } catch (err) {
      setError("Unable to download prescription PDF. Please try again.");
      console.error("Prescription download failed", err);
    }
  };

  const sendCurrentPrescription = async (channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId) return;
    const saved = await preserveViewport(() => persistPrescription());
    if (!saved) return;
    setSaving(true);
    try {
      const sent = await sendPrescription(auth.accessToken, auth.tenantId, saved.id, channel);
      const merged = prescription ? { ...prescription, ...sent } : sent;
      setPrescription(merged);
      prescriptionRef.current = merged;
      const historyRows = await getPrescriptionHistory(auth.accessToken, auth.tenantId, sent.id).catch(() => [sent]);
      setPrescriptionHistory(historyRows);
      setSelectedPrescriptionVersionId((current) => {
        if (current && historyRows.some((row) => row.id === current)) {
          return current;
        }
        return historyRows[historyRows.length - 1]?.id || sent.id;
      });
      setInfo(`Prescription sent via ${channel}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to send prescription via ${channel}`);
    } finally {
      setSaving(false);
    }
  };

  const openLabOrderDialog = () => {
    if (readOnly) return;
    setLabOrderTestIds((current) => current.filter((id) => labTests.some((test) => test.id === id)));
    setLabOrderDialogOpen(true);
  };
  const closeLabOrderDialog = React.useCallback(() => {
    setLabOrderDialogOpen(false);
    setLabOrderAiPreparation(null);
  }, []);

  const openLabOrderReview = () => {
    if (!labOrderTestIds.length) return;
    setLabOrderReviewOpen(true);
  };

  const submitLabOrder = async () => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    const currentConsultation = consultation;
    if (!accessToken || !tenantId || !currentConsultation) return;
    const parsed = labConsultationOrderCreateSchema.safeParse({
      patientId: currentConsultation.patientId,
      testIds: labOrderTestIds,
      notes: labOrderNotes.trim() || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setLabOrderSaving(true);
    setError(null);
    try {
      const created = await preserveViewport(() => createConsultationLabOrder(accessToken, tenantId, currentConsultation.id, {
        patientId: parsed.data.patientId,
        testIds: parsed.data.testIds,
        notes: parsed.data.notes || null,
      }));
      setLabOrders((current) => [created, ...current.filter((row) => row.id !== created.id)]);
      setLabOrderDialogOpen(false);
      setLabOrderReviewOpen(false);
      setLabOrderTestIds([]);
      setLabOrderNotes("");
      setLabOrderAiPreparation(null);
      setInfo(`Lab order ${created.orderNumber} created`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create lab order");
    } finally {
      setLabOrderSaving(false);
    }
  };

  const runAiAction = async (mode: "diagnosis" | "notes" | "instructions") => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    const title = mode === "diagnosis" ? "AI clinical reasoning" : mode === "notes" ? "SOAP notes" : "Patient instructions";
    const entryId = makeAiAssistEntryId();
    appendAiAssistEntry({
      id: entryId,
      action: mode,
      title,
      prompt: null,
      createdAt: new Date().toISOString(),
      status: "loading",
      response: null,
      error: null,
    });
    setAiBusy(true);
    setAiActiveAction(mode);
    setError(null);
    try {
      if (mode === "diagnosis") {
        if (import.meta.env.DEV) {
          console.debug("[AI_DIAGNOSIS_DEBUG] request", {
            consultationId: consultation.id,
            patientId: consultation.patientId,
            symptomsChars: consultationForm.symptoms.trim().length,
            findingsChars: consultationForm.clinicalNotes.trim().length,
            doctorNotesChars: consultationForm.chiefComplaints.trim().length,
            knownConditionsChars: (patient.patient.existingConditions || "").trim().length,
            allergiesChars: (patient.patient.allergies || "").trim().length,
          });
        }
        const draft = await aiSuggestDiagnosis(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          patientAgeGender,
          vitals: aiVitalsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          symptoms: consultationForm.symptoms,
          findings: consultationForm.clinicalNotes,
          doctorNotes: consultationForm.chiefComplaints,
          knownConditions: patient.patient.existingConditions,
          allergies: patient.patient.allergies,
        });
        const parsed = parseAiSuggestionItems(draft);
        if (import.meta.env.DEV) {
          const summaryText = parsed.summary || parsed.rawText || "";
          console.debug("[AI_DIAGNOSIS_DEBUG] response", {
            enabled: draft.enabled,
            fallbackUsed: draft.fallbackUsed,
            provider: draft.provider,
            model: draft.model,
            summaryChars: summaryText.length,
            rawTextLength: (draft.draft || draft.message || "").length,
            suggestionCount: parsed.items.length,
            keys: Object.keys(draft.structuredData || {}),
          });
        }
        if (!draft.enabled) {
          setAiAvailable(false);
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        setAiDiagnosisSuggestion(parsed.summary || parsed.rawText || null);
        setAiDiagnosisSummary(parsed.summary);
        setAiDiagnosisItems(parsed.items);
        setAiDiagnosisUnstructured(parsed.invalid);
        setAiDiagnosisProvider(draft.provider || null);
        updateAiAssistEntry(entryId, {
          status: "success",
          response: draft,
          error: parsed.invalid ? "AI returned an invalid response. Please retry." : null,
        });
        if (parsed.invalid) {
          setAiDiagnosisUnstructured(true);
          setError("AI returned an invalid response. Please retry.");
          return;
        }
      }

      if (mode === "notes") {
        const draft = await aiStructureConsultationNotes(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          patientAgeGender,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          doctorNotes: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          vitals: aiVitalsSummary,
          observations: consultationForm.clinicalNotes,
        });
        if (!draft.enabled) {
          setAiAvailable(false);
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      if (mode === "instructions") {
        const medicines = prescriptionForm.medicines
          .filter((row) => row.medicineName.trim())
          .map((row) => `${row.medicineName} ${row.strength || ""} ${row.frequency} ${row.duration}`.trim())
          .join("; ");
        const draft = await aiPatientInstructions(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          diagnosis: consultationForm.diagnosis,
          prescription: medicines,
          instructionsContext: consultationForm.advice,
          language: "English",
          literacyLevel: "General",
          allergies: patient.patient.allergies,
          warnings: "Doctor must verify before use",
        });
        if (!draft.enabled) {
          setAiAvailable(false);
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      setInfo("AI draft generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      const normalized = message.toLowerCase();
      const friendly =
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
          ? (
            normalized.includes("authorization failed") || normalized.includes("api key/provider configuration")
              ? "AI provider authorization failed. Check API key/provider configuration."
              : normalized.includes("enabled for this clinic")
                ? AI_PROVIDER_NOT_CONFIGURED_MESSAGE
                : (aiStatusMessage || AI_DISABLED_MESSAGE)
          )
          : "AI assistance is temporarily unavailable.";
      if (
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
      ) {
        setAiAvailable(false);
      }
      updateAiAssistEntry(entryId, { status: "error", error: friendly });
      setError(friendly);
      console.error("AI action failed", err);
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
    }
  };

  const runClinicalDraftAction = async (kind: ClinicalAiDraftKind) => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    const entryId = makeAiAssistEntryId();
    const generatedAt = new Date().toISOString();
    let soapTraceId: string | null = null;
    const baseSourceSummary = makeClinicalDraftSourceSummary({
      patientAgeGender,
      chiefComplaints: consultationForm.chiefComplaints,
      symptoms: consultationForm.symptoms,
      diagnosis: consultationForm.diagnosis,
      notes: consultationForm.clinicalNotes,
      vitals: aiVitalsSummary,
      history: patient.patient.existingConditions || patient.patient.longTermMedications || patient.patient.notes,
      prescription: currentPrescriptionDraftSummary || null,
      investigations: labOrdersSummary || null,
    });
  const soapSourceSummary = kind === "soap"
      ? buildSoapContextSummary({
          generatedAt,
          provider: null,
        })
      : baseSourceSummary;
    appendAiAssistEntry({
      id: entryId,
      action: kind === "diagnosis" ? "diagnosis" : kind === "advice" ? "instructions" : kind === "soap" ? "notes" : "ask",
      title:
        kind === "chiefComplaint" ? "Chief Complaint draft" :
        kind === "symptoms" ? "Symptoms draft" :
        kind === "diagnosis" ? "Diagnosis draft" :
        kind === "soap" ? "SOAP draft" :
        kind === "advice" ? "Advice draft" :
        "Follow-up draft",
      prompt: null,
      createdAt: generatedAt,
      status: "loading",
      response: null,
      error: null,
    });
    updateClinicalAiDraft(kind, {
      status: "DRAFTED",
      generatedAt,
      sourceSummary: kind === "soap" ? soapSourceSummary : baseSourceSummary,
      draftText: "",
      structuredData: null,
      error: null,
      selectedItems: [],
      selectedItem: null,
      provider: null,
      model: null,
    });
    setAiBusy(true);
    setClinicalAiActiveDraft(kind);
    setAiActiveAction(null);
    setError(null);

    const normalizeError = (message: string) => {
      const normalized = message.toLowerCase();
      if (
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
      ) {
        setAiAvailable(false);
        return normalized.includes("authorization failed") || normalized.includes("api key/provider configuration")
          ? "AI provider authorization failed. Check API key/provider configuration."
          : normalized.includes("enabled for this clinic")
            ? AI_PROVIDER_NOT_CONFIGURED_MESSAGE
            : (aiStatusMessage || AI_DISABLED_MESSAGE);
      }
      return "AI assistance is temporarily unavailable.";
    };

    try {
      if (kind === "chiefComplaint") {
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: "Rewrite the chief complaint as one clean professional sentence. Use the typed complaint, symptoms, age/gender, and any relevant history. Return only the final sentence.",
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        const text = extractClinicalDraftText(draft);
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: text,
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
        });
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      if (kind === "symptoms") {
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: "Extract the symptoms from the consultation context. Return a short clean list of symptom phrases only. Do not invent symptoms.",
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        const parsed = extractClinicalSymptomsDraft(draft);
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: parsed.items.join("\n"),
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
        });
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      if (kind === "diagnosis") {
        const draft = await aiSuggestDiagnosis(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          patientAgeGender,
          vitals: aiVitalsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          symptoms: consultationForm.symptoms,
          findings: consultationForm.clinicalNotes,
          doctorNotes: consultationForm.chiefComplaints,
          knownConditions: patient.patient.existingConditions,
          allergies: patient.patient.allergies,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        const parsed = parseAiSuggestionItems(draft);
        const summaryText = parsed.items.length
          ? parsed.items.map((item) => item.reason ? `${item.title} - ${item.reason}` : item.title).join("\n")
          : parsed.summary || parsed.rawText || "";
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: summaryText,
          structuredData: draft.structuredData,
          error: parsed.invalid ? "AI returned an invalid response. Please retry." : null,
          selectedItems: [],
          selectedItem: parsed.items[0]?.title || null,
        });
        updateAiAssistEntry(entryId, {
          status: "success",
          response: draft,
          error: parsed.invalid ? "AI returned an invalid response. Please retry." : null,
        });
        if (parsed.invalid) {
          setError("AI returned an invalid response. Please retry.");
        }
      }

      if (kind === "soap") {
        soapTraceId = crypto.randomUUID();
        const updateSoapFailure = (message: string, draft: AiDraftResponse | null = null) => {
          updateClinicalAiDraft(kind, {
            status: "REJECTED",
            generatedAt: new Date().toISOString(),
            sourceSummary: buildSoapContextSummary({
              generatedAt: new Date().toISOString(),
              provider: draft?.provider || null,
            }),
            draftText: "",
            structuredData: null,
            error: message,
            selectedItems: [],
            selectedItem: null,
            traceId: soapTraceId,
            provider: draft?.provider || null,
            model: draft?.model || null,
          });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
        };
        if (SOAP_TRACE_ENABLED) {
          console.debug("SOAP-DRAFT-TRACE-FE stage=REQUEST", {
            traceId: soapTraceId,
            consultationId: consultation.id,
            patientId: consultation.patientId,
            chiefComplaintPresent: soapFieldPresent(consultationForm.chiefComplaints),
            symptomsPresent: soapFieldPresent(consultationForm.symptoms),
            doctorNotesPresent: soapFieldPresent(consultationForm.chiefComplaints),
            diagnosisPresent: soapFieldPresent(consultationForm.diagnosis),
            advicePresent: soapFieldPresent(consultationForm.advice),
            vitalsPresent: Boolean(soapVitalsSummary),
            observationsPresent: soapFieldPresent(consultationForm.clinicalNotes),
            chiefComplaintChars: soapFieldCharCount(consultationForm.chiefComplaints),
            symptomsChars: soapFieldCharCount(consultationForm.symptoms),
            doctorNotesChars: soapFieldCharCount(consultationForm.chiefComplaints),
            diagnosisChars: soapFieldCharCount(consultationForm.diagnosis),
            adviceChars: soapFieldCharCount(consultationForm.advice),
            vitalsChars: soapFieldCharCount(soapVitalsSummary),
            observationsChars: soapFieldCharCount(consultationForm.clinicalNotes),
            soapVitalsSummary,
            sourceSummary: soapSourceSummary,
          });
        }
        const draft = await aiStructureConsultationNotes(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          patientAgeGender,
          chiefComplaint: consultationForm.chiefComplaints,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          doctorNotes: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
          vitals: soapVitalsSummary,
          observations: consultationForm.clinicalNotes,
        }, { correlationId: soapTraceId });
        if (SOAP_TRACE_ENABLED) {
          const parsed = extractClinicalSoapDraft(draft);
          const soapSectionFlags = summarizeSoapDraftSections(parsed);
          console.debug("SOAP-DRAFT-TRACE-FE stage=RESPONSE", {
            traceId: soapTraceId,
            consultationId: consultation.id,
            patientId: consultation.patientId,
            httpStatus: draft.enabled ? "SUCCESS" : "FAILURE",
            structuredDataPresent: Boolean(draft.structuredData && Object.keys(draft.structuredData).length),
            returnedKeys: draft.structuredData ? Object.keys(draft.structuredData) : [],
            ...soapSectionFlags,
            localValidationOutcome: hasMeaningfulSoapDraft(parsed) ? "ACCEPTED" : "REJECTED",
            rejectedDraftReason: hasMeaningfulSoapDraft(parsed)
              ? null
              : (draft.message || "ALL_PLACEHOLDER"),
            validationMessagePresent: Boolean(draft.message),
          });
        }
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateSoapFailure(message, draft);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateSoapFailure(message, draft);
          return;
        }
        const parsed = extractClinicalSoapDraft(draft);
        if (!hasMeaningfulSoapDraft(parsed)) {
          const message = draft.message || "Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry.";
          updateSoapFailure(message, draft);
          return;
        }
        const cleanedParsed = {
          subjective: dedupeSoapSectionText(parsed.subjective),
          objective: dedupeSoapSectionText(parsed.objective),
          assessment: dedupeSoapSectionText(parsed.assessment),
          plan: dedupeSoapSectionText(parsed.plan),
        };
        const combined = normalizeDraftedTextWithSections("", [
          ["Subjective", cleanedParsed.subjective],
          ["Objective", cleanedParsed.objective],
          ["Assessment", cleanedParsed.assessment],
          ["Plan", cleanedParsed.plan],
        ]) || extractClinicalDraftText(draft);
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: buildSoapContextSummary({
            generatedAt: new Date().toISOString(),
            provider: draft.provider || null,
          }),
          draftText: combined,
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
          traceId: soapTraceId,
          provider: draft.provider || null,
          model: draft.model || null,
        });
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      if (kind === "advice") {
        const draft = await aiPatientInstructions(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          diagnosis: consultationForm.diagnosis,
          prescription: currentPrescriptionDraftSummary || null,
          instructionsContext: consultationForm.advice,
          language: "English",
          literacyLevel: "General",
          allergies: patient.patient.allergies,
          warnings: "Doctor must verify before use",
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        const text = extractClinicalDraftText(draft);
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: text,
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
        });
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      if (kind === "followUp") {
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: "Suggest the follow-up interval or date and a short reason based on the diagnosis, severity, current treatment, and red flags. Return a concise answer with followUpDate or follow-up interval if available.",
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          updateClinicalAiDraft(kind, { error: message });
          setError(message);
          return;
        }
        const parsed = extractClinicalFollowUpDraft(draft);
        const text = [
          parsed.followUpDate ? `Suggested date: ${parsed.followUpDate}` : null,
          parsed.interval ? `Interval: ${parsed.interval}` : null,
          parsed.reason ? `Reason: ${parsed.reason}` : null,
          extractClinicalDraftText(draft) && extractClinicalDraftText(draft) !== parsed.reason ? extractClinicalDraftText(draft) : null,
        ].filter(Boolean).join("\n");
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: text,
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
        });
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }

      setInfo("AI draft generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      const friendly = normalizeError(message);
      if (kind === "soap") {
        updateClinicalAiDraft(kind, {
          status: "REJECTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: soapSourceSummary,
          draftText: "",
          structuredData: null,
          error: friendly,
          selectedItems: [],
          selectedItem: null,
          traceId: soapTraceId,
        });
        updateAiAssistEntry(entryId, { status: "error", error: friendly });
      } else {
        updateClinicalAiDraft(kind, { error: friendly });
        updateAiAssistEntry(entryId, { status: "error", error: friendly });
        setError(friendly);
      }
      console.error("Clinical AI draft failed", err);
    } finally {
      setAiBusy(false);
      setClinicalAiActiveDraft(null);
    }
  };

  const runClinicalReasoningAction = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return false;
    setAiBusy(true);
    setAiActiveAction("diagnosis");
    setError(null);
    try {
      const result = await clinicalReasoning.generateReasoning(consultation.id);
      const hasContent = Boolean(
        result?.primaryDiagnosis?.name
        || result?.differentialDiagnoses?.length
        || result?.reasoningSummary
        || result?.redFlags?.length
        || result?.recommendedTests?.length
      );
      const resultQuality = result?.metadata?.resultQuality || (result?.metadata?.parseStatus === "VALID" && result?.primaryDiagnosis?.name ? "COMPLETE" : null);
      if (resultQuality === "COMPLETE" && hasContent) {
        setInfo("Clinical reasoning generated. Doctor must verify before use.");
        return true;
      }
      setError("AI reasoning could not be generated. Please retry.");
      return false;
    } catch (err) {
      console.error("Clinical reasoning generation failed", err);
      setError("AI reasoning could not be generated. Please retry.");
      return false;
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
    }
  }, [
    auth.accessToken,
    auth.tenantId,
    consultation,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.symptoms,
    patient,
    clinicalReasoning.generateReasoning,
  ]);

  const rejectClinicalDraft = React.useCallback((kind: ClinicalAiDraftKind) => {
    updateClinicalAiDraft(kind, { status: "REJECTED" });
  }, [updateClinicalAiDraft]);

  const editClinicalDraft = React.useCallback((kind: ClinicalAiDraftKind, nextText: string) => {
    const current = clinicalAiDrafts[kind];
    updateClinicalAiDraft(kind, {
      status: "EDITED",
      draftText: nextText,
      generatedAt: current.generatedAt || new Date().toISOString(),
      error: null,
    });
  }, [clinicalAiDrafts, updateClinicalAiDraft]);

  const copyClinicalDraft = React.useCallback(async (kind: ClinicalAiDraftKind) => {
    const draft = clinicalAiDrafts[kind];
    const text = draft.draftText.trim();
    if (!text) return;
    await navigator.clipboard.writeText(text);
  }, [clinicalAiDrafts]);

  const updateConsultationDocumentDraft = React.useCallback((kind: ConsultationDocumentationKind, patch: Partial<ConsultationDocumentDraftState>) => {
    setConsultationDocumentDrafts((current) => ({
      ...current,
      [kind]: {
        ...current[kind],
        ...patch,
      },
    }));
  }, []);

  const setConsultationDocumentLanguage = React.useCallback((language: ConsultationDocumentDraftState["language"]) => {
    setConsultationDocumentationLanguage(language);
    setConsultationDocumentDrafts((current) => ({
      visitSummary: { ...current.visitSummary, language },
      referral: { ...current.referral, language },
      certificate: { ...current.certificate, language },
    }));
  }, []);

  const persistGeneratedConsultationDocument = React.useCallback(async (kind: ConsultationDocumentationKind) => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return null;
    const draft = consultationDocumentDraftsRef.current[kind];
    if (!draft.draftText.trim()) return null;
    setConsultationDocumentationSaving(true);
    try {
      const generated = await generateConsultationDocument(auth.accessToken, auth.tenantId, consultation.id, {
        title: draft.title,
        documentType: draft.documentType,
        body: draft.draftText.trim(),
        language: draft.language,
        notes: draft.notes,
        visibility: "INTERNAL_ONLY",
      });
      await refreshClinicalArtifacts(consultation.patientId, consultation);
      return generated;
    } finally {
      setConsultationDocumentationSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, consultation]);

  const generateConsultationDocumentDraft = React.useCallback(async (kind: ConsultationDocumentationKind, forceAll = false) => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    const current = consultationDocumentDraftsRef.current[kind];
    if (!forceAll && (current.status === "ACCEPTED" || current.status === "EDITED") && current.draftText.trim()) {
      const proceed = await requestBooleanConfirmation({
        title: `Regenerate ${current.title}?`,
        description: "This will replace the current editable draft. Previously saved documents remain in the patient record.",
        confirmLabel: "Regenerate",
        confirmColor: "secondary",
      });
      if (!proceed) return;
    }
    const entryId = makeAiAssistEntryId();
    const baseSourceSummary = makeClinicalDraftSourceSummary({
      patientAgeGender,
      chiefComplaints: consultationForm.chiefComplaints,
      symptoms: consultationForm.symptoms,
      diagnosis: consultationForm.diagnosis,
      notes: consultationForm.clinicalNotes,
      vitals: aiVitalsSummary,
      history: patient.patient.existingConditions || patient.patient.longTermMedications || patient.patient.surgicalHistory || patient.patient.notes,
      prescription: currentPrescriptionDraftSummary || null,
      investigations: labOrdersSummary || null,
    });
    appendAiAssistEntry({
      id: entryId,
      action: "ask",
      title: `${current.title} generation`,
      prompt: null,
      createdAt: new Date().toISOString(),
      status: "loading",
      response: null,
      error: null,
    });
    updateConsultationDocumentDraft(kind, {
      status: "DRAFTED",
      generatedAt: new Date().toISOString(),
      sourceSummary: baseSourceSummary,
      language: consultationDocumentationLanguage,
      error: null,
    });
    setAiBusy(true);
    setError(null);
    try {
      let draftText = "";
      if (kind === "visitSummary") {
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: `Draft a patient-friendly visit summary in ${consultationDocumentationLanguage}. Include reason for visit, diagnosis, medicines, investigations advised, diet/lifestyle advice, follow-up, emergency warning signs, and clinic contact. Keep it editable and concise.`,
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        draftText = extractClinicalDraftText(draft);
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }
      if (kind === "referral") {
        const referralTypeLabel = consultationReferralDestination.trim()
          ? consultationReferralDestination.trim()
          : "specialist referral";
        const priorityLabel = consultationReferralPriority === "EMERGENCY"
          ? "Emergency"
          : consultationReferralPriority === "URGENT"
            ? "Urgent"
            : "Routine";
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: `Draft a ${referralTypeLabel} in ${consultationDocumentationLanguage}. Include patient, doctor, clinic, diagnosis, reason, current medicines, investigations, clinical notes, referral destination, and priority (${priorityLabel}). Keep it printable and editable.`,
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        draftText = extractClinicalDraftText(draft);
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }
      if (kind === "certificate") {
        const certificateTypeLabel = consultationCertificateType === "FITNESS"
          ? "Fitness Certificate"
          : consultationCertificateType === "SICK_LEAVE"
            ? "Sick Leave Certificate"
            : consultationCertificateType === "RETURN_TO_WORK"
              ? "Return to Work Certificate"
              : "Medical Certificate";
        const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          prompt: `Draft an editable ${certificateTypeLabel} in ${consultationDocumentationLanguage}. Include patient details, date, clinical basis, duration if relevant, and a concise doctor signature block. Keep it professional and printable.`,
          patientAgeGender,
          vitals: aiVitalsSummary,
          allergies: aiAllergiesSummary,
          chronicConditions: aiChronicConditionsSummary,
          currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
          labOrdersSummary: labOrdersSummary || null,
          chiefComplaints: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          clinicalNotes: consultationForm.clinicalNotes,
          diagnosis: consultationForm.diagnosis,
          advice: consultationForm.advice,
        });
        if (!draft.enabled) {
          const message = aiStatusMessage || AI_DISABLED_MESSAGE;
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        if (!draft.provider && draft.fallbackUsed) {
          const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
          updateConsultationDocumentDraft(kind, { error: message });
          updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
          setError(message);
          return;
        }
        draftText = extractClinicalDraftText(draft);
        updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      }
      updateConsultationDocumentDraft(kind, {
        status: "DRAFTED",
        generatedAt: new Date().toISOString(),
        sourceSummary: baseSourceSummary,
        draftText,
        language: consultationDocumentationLanguage,
        error: null,
      });
      setInfo("AI-assisted draft generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to generate consultation document";
      updateConsultationDocumentDraft(kind, { error: message });
      updateAiAssistEntry(entryId, { status: "error", error: message });
      setError(message);
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
    }
  }, [
    aiAllergiesSummary,
    aiBusy,
    aiChronicConditionsSummary,
    aiStatusMessage,
    aiVitalsSummary,
    auth.accessToken,
    auth.tenantId,
    consultation,
    consultationCertificateType,
    consultationDocumentDraftsRef,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.symptoms,
    consultationForm.advice,
    currentPrescriptionDraftSummary,
    labOrdersSummary,
    patient,
    patientAgeGender,
    requestBooleanConfirmation,
    updateAiAssistEntry,
    updateConsultationDocumentDraft,
  ]);

  const finalizeConsultationDocumentDraft = React.useCallback(async (kind: ConsultationDocumentationKind) => {
    const draft = consultationDocumentDraftsRef.current[kind];
    if (!draft.draftText.trim()) return;
    const current = consultationDocumentDraftsRef.current[kind];
    if (current.status === "REJECTED") return;
    const saved = await persistGeneratedConsultationDocument(kind);
    if (!saved) return;
    updateConsultationDocumentDraft(kind, {
      status: "ACCEPTED",
      documentId: saved.documentId,
      downloadUrl: saved.downloadUrl,
      error: null,
    });
    setConsultationAuditShareLog((currentLog) => {
      const label = kind === "visitSummary" ? "Visit summary finalized" : kind === "referral" ? "Referral finalized" : "Certificate finalized";
      return currentLog.includes(label) ? currentLog : [...currentLog, label];
    });
    setInfo(`${draft.title} saved to patient documents`);
  }, [persistGeneratedConsultationDocument, updateConsultationDocumentDraft]);

  const rejectConsultationDocumentDraft = React.useCallback((kind: ConsultationDocumentationKind) => {
    updateConsultationDocumentDraft(kind, { status: "REJECTED" });
  }, [updateConsultationDocumentDraft]);

  const copyConsultationDocumentDraft = React.useCallback(async (kind: ConsultationDocumentationKind) => {
    const draft = consultationDocumentDraftsRef.current[kind];
    if (!draft.draftText.trim()) return;
    await navigator.clipboard.writeText(draft.draftText.trim());
  }, []);

  const buildConsultationPackageBody = React.useCallback(() => {
    const summaryText = consultationDocumentDraftsRef.current.visitSummary.draftText.trim() || "No visit summary generated yet.";
    const referralText = consultationDocumentDraftsRef.current.referral.draftText.trim() || "No referral generated yet.";
    const certificateText = consultationDocumentDraftsRef.current.certificate.draftText.trim() || "No certificate generated yet.";
    const instructionsText = (prescriptionInstructionsDraft?.draft || prescriptionInstructionsDraft?.message || "").trim() || "No patient instructions generated yet.";
    const prescriptionSummaryText = prescriptionSummaryDraft.trim() || buildPrescriptionSummaryText({
      diagnosis: prescriptionForm.diagnosisSnapshot || consultationForm.diagnosis,
      medicines: prescriptionForm.medicines,
      tests: prescriptionForm.recommendedTests,
      advice: prescriptionForm.advice || consultationForm.advice,
      followUpDate: prescriptionForm.followUpDate || consultationForm.followUpDate,
      warnings: prescriptionSafetyChecks.filter((item) => item.status === "warning" || item.status === "critical").map((item) => `${item.label}: ${item.message}`),
      redFlags: aiDiagnosisItems.flatMap((item) => item.redFlags).slice(0, 5),
    });
    const labSummary = reportHistoryRows.length
      ? reportHistoryRows.slice(0, 5).map((row) => `${row.title || row.originalFilename} • ${row.documentType.replaceAll("_", " ")} • ${row.reportDate || compactDate(row.createdAt)}`).join("\n")
      : "No lab orders or reports available.";
    const packagePatientName = patient?.patient ? [patient.patient.firstName, patient.patient.lastName].filter(Boolean).join(" ").trim() || patient.patient.patientNumber : patientRow?.patientNumber || consultation?.patientName || "-";
    return [
      "Consultation Package",
      `Patient: ${packagePatientName}`,
      `Doctor: ${consultation?.doctorName || "-"}`,
      `Generated at: ${new Date().toLocaleString()}`,
      "",
      "VISIT SUMMARY",
      summaryText,
      "",
      "PRESCRIPTION SUMMARY",
      prescriptionSummaryText,
      "",
      "REFERRAL",
      referralText,
      "",
      "CERTIFICATE",
      certificateText,
      "",
      "PATIENT INSTRUCTIONS",
      instructionsText,
      "",
      "LAB ORDER / REPORT SUMMARY",
      labSummary,
      "",
      "FOLLOW-UP PLAN",
      [consultationForm.followUpDate || prescriptionForm.followUpDate ? `Follow-up date: ${consultationForm.followUpDate || prescriptionForm.followUpDate}` : null, followUpPlanNotes.trim() ? `Notes: ${followUpPlanNotes.trim()}` : null].filter(Boolean).join("\n") || "No structured follow-up plan recorded.",
      "",
      "COMMUNICATION",
      consultationAuditShareLog.length ? consultationAuditShareLog.join("\n") : "No patient communication actions recorded yet.",
    ].join("\n");
  }, [
    aiDiagnosisItems,
    consultation,
    consultationAuditShareLog,
    consultationForm.advice,
    consultationForm.diagnosis,
    consultationForm.followUpDate,
    consultationDocumentDraftsRef,
    followUpPlanNotes,
    patient,
    patientRow?.patientNumber,
    prescriptionForm.advice,
    prescriptionForm.diagnosisSnapshot,
    prescriptionForm.followUpDate,
    prescriptionForm.medicines,
    prescriptionForm.recommendedTests,
    prescriptionInstructionsDraft?.draft,
    prescriptionInstructionsDraft?.message,
    prescriptionSafetyChecks,
    prescriptionSummaryDraft,
    reportHistoryRows,
  ]);

  const shareConsultationDocumentUrl = React.useCallback(async (kind: ConsultationDocumentationKind) => {
    const draft = consultationDocumentDraftsRef.current[kind];
    if (!draft.downloadUrl) return;
    try {
      if (navigator.share) {
        await navigator.share({
          title: draft.title,
          text: draft.draftText.slice(0, 200),
          url: draft.downloadUrl,
        });
      } else {
        await navigator.clipboard.writeText(draft.downloadUrl);
        setInfo(`${draft.title} link copied to clipboard`);
      }
      setConsultationAuditShareLog((current) => current.includes(`${draft.title} shared`) ? current : [...current, `${draft.title} shared`]);
    } catch {
      setError("Unable to share the document. Copy the download link instead.");
    }
  }, []);

  const generateConsultationPackage = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    setConsultationPackageGenerating(true);
    setError(null);
    try {
      const response = await generateConsultationDocument(auth.accessToken, auth.tenantId, consultation.id, {
        title: "Consultation Package",
        documentType: "ATTACHMENT",
        body: buildConsultationPackageBody(),
        language: consultationDocumentationLanguage,
        notes: "Compiled consultation package for patient communication and records",
        visibility: "INTERNAL_ONLY",
      });
      setConsultationAuditShareLog((current) => current.includes("Consultation package generated") ? current : [...current, "Consultation package generated"]);
      setInfo("Consultation package generated and saved to patient documents.");
      if (response?.downloadUrl) {
        const anchor = document.createElement("a");
        anchor.href = response.downloadUrl;
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        anchor.click();
      }
      await refreshClinicalArtifacts(consultation.patientId, consultation);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to generate consultation package";
      setError(message);
    } finally {
      setConsultationPackageGenerating(false);
    }
  }, [auth.accessToken, auth.tenantId, buildConsultationPackageBody, consultation, consultationDocumentationLanguage]);

  const appendAivaChatMessage = React.useCallback((message: AivaChatMessage) => {
    setAivaChatMessages((current) => [...current, message]);
  }, []);

  const acceptClinicalDraft = React.useCallback(async (kind: ClinicalAiDraftKind) => {
    const draft = clinicalAiDrafts[kind];
    const currentText = draft.draftText.trim();
    if (!currentText) return;

    if (kind === "chiefComplaint") {
      const choice = await requestOverwriteChoice("Chief complaint", consultationForm.chiefComplaints, true);
      if (choice === "cancel") return;
      setConsultationForm((current) => ({
        ...current,
        chiefComplaints: choice === "append" ? appendTokenLine(current.chiefComplaints, currentText) : currentText,
      }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
      return;
    }

    if (kind === "symptoms") {
      const structured = parseStructuredObject(draft.structuredData);
      const items = draft.selectedItems.length ? draft.selectedItems : asStringList(structured.symptoms || structured.suggestions || structured.extractedSymptoms || structured.items || currentText);
      const selectedItems = items.length ? items : currentText.split(/\n|,|•/).map((item) => item.trim()).filter(Boolean);
      if (!selectedItems.length) return;
      const choice = await requestOverwriteChoice("Symptoms", consultationForm.symptoms, true);
      if (choice === "cancel") return;
      const nextValue = choice === "replace"
        ? selectedItems.join(", ")
        : appendTokenLine(consultationForm.symptoms, selectedItems.join(", "));
      setConsultationForm((current) => ({ ...current, symptoms: nextValue }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
      return;
    }

    if (kind === "diagnosis") {
      const parsed = parseAiSuggestionItems({
        enabled: true,
        fallbackUsed: false,
        message: "",
        provider: null,
        model: null,
        draft: currentText,
        structuredData: draft.structuredData || {},
        confidence: null,
        suggestedActions: [],
        warnings: [],
      });
      const selected = draft.selectedItem || parsed.items[0]?.title || currentText.split(/\n/).map((item) => item.trim()).find(Boolean) || "";
      if (!selected) return;
      setConsultationForm((current) => ({ ...current, diagnosis: appendTokenLine(current.diagnosis, selected) }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
      return;
    }

    if (kind === "soap") {
      const currentSoapText = [
        consultationSoapForm.subjective,
        consultationSoapForm.objective,
        consultationSoapForm.assessment,
        consultationSoapForm.plan,
      ].filter((value) => Boolean(value.trim())).join("\n");
      const choice = await requestOverwriteChoice("SOAP notes", currentSoapText, true);
      if (choice === "cancel") return;
      const parsed = extractClinicalSoapDraft({
        enabled: true,
        fallbackUsed: false,
        message: "",
        provider: null,
        model: null,
        draft: currentText,
        structuredData: draft.structuredData || {},
        confidence: null,
        suggestedActions: [],
        warnings: [],
      });
      const nextSoapForm: ConsultationSoapFormState = {
        subjective: choice === "replace" ? (parsed.subjective || consultationSoapForm.subjective) : appendSoapSectionText(consultationSoapForm.subjective, parsed.subjective || ""),
        objective: choice === "replace" ? (parsed.objective || consultationSoapForm.objective) : appendSoapSectionText(consultationSoapForm.objective, parsed.objective || ""),
        assessment: choice === "replace" ? (parsed.assessment || consultationSoapForm.assessment) : appendSoapSectionText(consultationSoapForm.assessment, parsed.assessment || ""),
        plan: choice === "replace" ? (parsed.plan || consultationSoapForm.plan) : appendSoapSectionText(consultationSoapForm.plan, parsed.plan || ""),
      };
      const cleanedSoapForm: ConsultationSoapFormState = {
        subjective: dedupeSoapSectionText(nextSoapForm.subjective),
        objective: dedupeSoapSectionText(nextSoapForm.objective),
        assessment: dedupeSoapSectionText(nextSoapForm.assessment),
        plan: dedupeSoapSectionText(nextSoapForm.plan),
      };
      setConsultationSoapForm(cleanedSoapForm);
      consultationSoapFormRef.current = cleanedSoapForm;
      try {
        const accessToken = auth.accessToken;
        const tenantId = auth.tenantId;
        const consultationId = consultation?.id ?? "";
        if (!accessToken || !tenantId || !consultationId) return;
        const saved = await acceptConsultationSoapAiDraft(accessToken, tenantId, consultationId, {
          subjective: cleanedSoapForm.subjective,
          objective: cleanedSoapForm.objective,
          assessment: cleanedSoapForm.assessment,
          plan: cleanedSoapForm.plan,
          aiProvider: null,
          aiModel: null,
          generatedAt: draft.generatedAt || null,
        });
        setConsultationSoap(saved);
        const syncedSoapForm = emptyConsultationSoapForm(saved);
        setConsultationSoapForm(syncedSoapForm);
        consultationSoapFormRef.current = syncedSoapForm;
        savedConsultationSoapSnapshotRef.current = serializeConsultationSoapForm(syncedSoapForm);
        updateClinicalAiDraft(kind, { status: "ACCEPTED", error: null, provider: null, model: null });
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to save SOAP draft");
      }
      return;
    }

    if (kind === "advice") {
      const choice = await requestOverwriteChoice("Advice", consultationForm.advice, true);
      if (choice === "cancel") return;
      setConsultationForm((current) => ({
        ...current,
        advice: choice === "append" ? appendTokenLine(current.advice, currentText) : currentText,
      }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
      return;
    }

    if (kind === "followUp") {
      const structured = parseStructuredObject(draft.structuredData);
      const followUpDate = String(structured.followUpDate ?? structured.followUp ?? structured.date ?? parseFollowUpDateSuggestion(currentText) ?? "").trim();
      if (!followUpDate) return;
      if (consultationForm.followUpDate.trim()) {
        const proceed = await requestBooleanConfirmation({
          title: "Replace existing follow-up date?",
          description: "The current follow-up date will be replaced with the AI suggestion.",
          confirmLabel: "Replace",
          confirmColor: "secondary",
        });
        if (!proceed) return;
      }
      setConsultationForm((current) => ({ ...current, followUpDate }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
    }
  }, [clinicalAiDrafts, consultationForm.advice, consultationForm.chiefComplaints, consultationForm.clinicalNotes, consultationForm.diagnosis, consultationForm.followUpDate, requestBooleanConfirmation, requestOverwriteChoice, updateClinicalAiDraft]);

  const submitAivaQuestion = React.useCallback(async () => {
    const prompt = aivaClinicalQuestion.trim();
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient || !prompt || aivaQuestionSubmitting || aiBusy) return;
    const doctorMessageId = makeAiAssistEntryId();
    const requestAt = new Date().toISOString();
    appendAivaChatMessage({
      id: doctorMessageId,
      role: "DOCTOR",
      text: prompt,
      createdAt: requestAt,
      response: null,
    });
    setAivaClinicalQuestion("");
    setAivaQuestionSubmitting(true);
    setAiBusy(true);
    setAiActiveAction("ask");
    setError(null);
    try {
      const draft = await aiConsultationAsk(auth.accessToken, auth.tenantId, {
        consultationId: consultation.id,
        patientId: consultation.patientId,
        prompt,
        patientAgeGender,
        vitals: aiVitalsSummary,
        allergies: aiAllergiesSummary,
        chronicConditions: aiChronicConditionsSummary,
        currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
        labOrdersSummary: labOrdersSummary || null,
        chiefComplaints: consultationForm.chiefComplaints,
        symptoms: consultationForm.symptoms,
        clinicalNotes: consultationForm.clinicalNotes,
        diagnosis: consultationForm.diagnosis,
        advice: consultationForm.advice,
      });
      if (!draft.enabled) {
        setAiAvailable(false);
        const message = aiStatusMessage || AI_DISABLED_MESSAGE;
        appendAivaChatMessage({
          id: makeAiAssistEntryId(),
          role: "AIVA",
          text: message,
          createdAt: new Date().toISOString(),
          response: draft,
        });
        setError(message);
        return;
      }
      if (!draft.provider && draft.fallbackUsed) {
        const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
        appendAivaChatMessage({
          id: makeAiAssistEntryId(),
          role: "AIVA",
          text: message,
          createdAt: new Date().toISOString(),
          response: draft,
        });
        setError(message);
        return;
      }
      appendAivaChatMessage({
        id: makeAiAssistEntryId(),
        role: "AIVA",
        text: extractClinicalDraftText(draft) || draft.message || "No response captured.",
        createdAt: new Date().toISOString(),
        response: draft,
      });
      setInfo("AIVA response generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      const normalized = message.toLowerCase();
      const friendly =
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
          ? (
            normalized.includes("authorization failed") || normalized.includes("api key/provider configuration")
              ? "AI provider authorization failed. Check API key/provider configuration."
              : normalized.includes("enabled for this clinic")
                ? AI_PROVIDER_NOT_CONFIGURED_MESSAGE
                : (aiStatusMessage || AI_DISABLED_MESSAGE)
          )
          : "AI assistance is temporarily unavailable.";
      if (
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
      ) {
        setAiAvailable(false);
      }
      appendAivaChatMessage({
        id: makeAiAssistEntryId(),
        role: "AIVA",
        text: friendly,
        createdAt: new Date().toISOString(),
        response: null,
      });
      setError(friendly);
      console.error("AI ask failed", err);
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
      setAivaQuestionSubmitting(false);
    }
  }, [
    aiAllergiesSummary,
    aiBusy,
    aiChronicConditionsSummary,
    aiStatusMessage,
    aiVitalsSummary,
    appendAivaChatMessage,
    aivaClinicalQuestion,
    aivaQuestionSubmitting,
    auth.accessToken,
    auth.tenantId,
    consultation,
    consultationForm.advice,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.symptoms,
    currentPrescriptionDraftSummary,
    labOrdersSummary,
    patient,
    patientAgeGender,
  ]);

  const runAskAiva = submitAivaQuestion;

  const generateClinicalDraftStep = async (kind: ClinicalDraftGenerationStep, forceAll: boolean) => {
    const currentDraft = kind === "prescriptionSuggestion" || kind === "clinicalReasoning"
      ? null
      : clinicalAiDraftsRef.current[kind];
    const preserved = !forceAll && currentDraft && (currentDraft.status === "ACCEPTED" || currentDraft.status === "EDITED");
    const stepStatus = preserved ? "done" : "generating";
    setClinicalDraftGenerationSteps((current) => ({
      ...current,
      [kind]: {
        status: stepStatus,
        error: null,
        message: preserved ? "Preserved accepted draft" : null,
      },
    }));
    if (preserved) {
      return true;
    }
    const run = kind === "clinicalReasoning"
      ? runClinicalReasoningAction()
      : kind === "prescriptionSuggestion"
        ? applyAiPrescriptionTemplate()
        : runClinicalDraftAction(kind);
    try {
      const runSucceeded = await run;
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      });
      const latestDraft = kind === "prescriptionSuggestion" || kind === "clinicalReasoning"
        ? null
        : clinicalAiDraftsRef.current[kind];
      const latestResult = kind === "clinicalReasoning"
        ? {
            ok: Boolean(runSucceeded),
            error: runSucceeded ? null : "Clinical reasoning failed to generate.",
          }
        : kind === "prescriptionSuggestion"
          ? {
              ok: Boolean(aiPrescriptionSuggestionRef.current || aiPrescriptionItemsRef.current.length),
              error: aiPrescriptionSuggestionRef.current ? null : "Prescription suggestion failed to generate.",
            }
          : {
              ok: Boolean(latestDraft && (latestDraft.draftText.trim() || latestDraft.error == null)),
              error: latestDraft && latestDraft.error && !latestDraft.draftText.trim() ? latestDraft.error : null,
            };
      setClinicalDraftGenerationSteps((current) => ({
        ...current,
        [kind]: latestResult.ok
          ? { status: "done", error: null, message: null }
          : { status: "failed", error: latestResult.error || "AI assistance failed", message: null },
      }));
      return latestResult.ok;
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI assistance failed";
      setClinicalDraftGenerationSteps((current) => ({
        ...current,
        [kind]: {
          status: "failed",
          error: message,
          message: null,
        },
      }));
      return false;
    }
  };

  const generateConsultationDraft = async (forceAll = false) => {
    if (!canGenerateClinicalDraft) return;
    const steps: ClinicalDraftGenerationStep[] = [
      "chiefComplaint",
      "symptoms",
      "diagnosis",
      "soap",
      "advice",
      "followUp",
      "clinicalReasoning",
      "prescriptionSuggestion",
    ];
    setClinicalDraftGenerationSteps(createEmptyClinicalDraftGenerationSteps());
    let successCount = 0;
    for (const step of steps) {
      const ok = await generateClinicalDraftStep(step, forceAll);
      if (ok) successCount += 1;
    }
    if (successCount > 0) {
      setInfo(`AIVA generated ${successCount} draft suggestions. Doctor must verify before use.`);
    }
  };

  const regenerateAllClinicalDrafts = React.useCallback(async () => {
    const proceed = await requestBooleanConfirmation({
      title: "Regenerate all sections?",
      description: "This will refresh accepted and edited AI drafts.",
      confirmLabel: "Regenerate all",
      confirmColor: "secondary",
    });
    if (!proceed) return;
    void generateConsultationDraft(true);
  }, [generateConsultationDraft, requestBooleanConfirmation]);

  const canAutoAcceptClinicalDraft = React.useCallback((kind: ClinicalAiDraftKind) => {
    const current = clinicalAiDraftsRef.current[kind];
    if (!current || !(current.status === "DRAFTED" || current.status === "EDITED")) return false;
    if (!current.draftText.trim()) return false;
    if (kind === "chiefComplaint") return !consultationFormRef.current.chiefComplaints.trim();
    if (kind === "symptoms") return !consultationFormRef.current.symptoms.trim();
    if (kind === "diagnosis") return !consultationFormRef.current.diagnosis.trim();
    if (kind === "soap") {
      const form = consultationFormRef.current;
      return !(form.chiefComplaints.trim() || form.clinicalNotes.trim() || form.diagnosis.trim() || form.advice.trim());
    }
    if (kind === "advice") return !consultationFormRef.current.advice.trim();
    if (kind === "followUp") return !consultationFormRef.current.followUpDate.trim();
    return false;
  }, []);

  const renderClinicalAiDraftCard = React.useCallback((kind: ClinicalAiDraftKind) => {
    const draft = clinicalAiDrafts[kind];
    const loading = aiBusy && clinicalAiActiveDraft === kind;
    const commonProps = {
      title: draft.title,
      status: draft.status,
      generatedAt: draft.generatedAt,
      sourceSummary: draft.sourceSummary,
      sourceSummaryLabel: kind === "soap" ? "Generated using" : "Context",
      draftText: draft.draftText,
      error: draft.error,
      loading,
      acceptDisabled: readOnly || !draft.draftText.trim() || aiBusy,
      editDisabled: readOnly || !draft.draftText.trim() || aiBusy,
      rejectDisabled: aiBusy,
      copyDisabled: !draft.draftText.trim(),
      onAccept: () => acceptClinicalDraft(kind),
      onEdit: (nextText: string) => editClinicalDraft(kind, nextText),
      onReject: () => rejectClinicalDraft(kind),
      onCopy: () => void copyClinicalDraft(kind),
    };

    if (kind === "soap" && draft.status === "ACCEPTED") {
      return null;
    }

    if (kind === "symptoms") {
      const items = draft.selectedItems.length
        ? draft.selectedItems
        : (draft.draftText ? draft.draftText.split(/\n|,|•/).map((item) => item.trim()).filter(Boolean) : []);
      return (
        <ClinicalAiDraftCard {...commonProps} acceptLabel="Accept symptoms">
          <Stack spacing={0.75}>
            {items.length ? (
              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                {items.map((item) => (
                  <Chip
                    key={item}
                    size="small"
                    color={draft.selectedItems.includes(item) ? "primary" : "default"}
                    variant={draft.selectedItems.includes(item) ? "filled" : "outlined"}
                    label={item}
                    clickable={!readOnly && !aiBusy}
                    onClick={() => updateClinicalAiDraft(kind, {
                      selectedItems: draft.selectedItems.includes(item)
                        ? draft.selectedItems.filter((value) => value !== item)
                        : [...draft.selectedItems, item],
                      status: "DRAFTED",
                    })}
                  />
                ))}
              </Stack>
            ) : null}
            <Typography variant="caption" color="text.secondary">
              Select one or more symptoms, then accept to add them to the current list.
            </Typography>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    if (kind === "diagnosis") {
      const response: AiDraftResponse = {
        enabled: true,
        fallbackUsed: false,
        message: "",
        provider: null,
        model: null,
        draft: draft.draftText,
        structuredData: draft.structuredData || {},
        confidence: null,
        suggestedActions: [],
        warnings: [],
      };
      const parsed = parseAiSuggestionItems(response);
      const selected = draft.selectedItem || parsed.items[0]?.title || null;
      return (
        <ClinicalAiDraftCard {...commonProps} acceptLabel="Accept selected diagnosis">
          <Stack spacing={0.75}>
            {parsed.items.length ? (
              parsed.items.map((item, index) => (
                <Card key={`${item.title}-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                  <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                    <Stack spacing={0.55}>
                      <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
                        <Box sx={{ minWidth: 0, flex: 1 }}>
                          <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }}>{item.title}</Typography>
                          {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>{item.reason}</Typography> : null}
                          {item.confidence ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>Confidence: {item.confidence}</Typography> : null}
                        </Box>
                        <Button
                          type="button"
                          size="small"
                          variant={selected === item.title ? "contained" : "outlined"}
                          disabled={readOnly || aiBusy}
                          onClick={() => updateClinicalAiDraft(kind, { selectedItem: item.title, status: "DRAFTED" })}
                        >
                          Use
                        </Button>
                      </Stack>
                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        {item.redFlags.slice(0, 2).map((flag) => <Chip key={flag} size="small" color="warning" variant="outlined" label={`Red flag: ${flag}`} />)}
                        {item.recommendedInvestigations.slice(0, 2).map((test) => <Chip key={test} size="small" variant="outlined" label={test} />)}
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Typography variant="body2" color="text.secondary">
                No structured diagnosis list returned. Use the draft text below.
              </Typography>
            )}
            <Typography variant="caption" color="text.secondary">
              Select one diagnosis and accept it into the assessment field only.
            </Typography>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    if (kind === "soap") {
      const response: AiDraftResponse = {
        enabled: true,
        fallbackUsed: false,
        message: "",
        provider: null,
        model: null,
        draft: draft.draftText,
        structuredData: draft.structuredData || {},
        confidence: null,
        suggestedActions: [],
        warnings: [],
      };
      const parsed = extractClinicalSoapDraft(response);
      const soapDraftVisible = draft.status === "DRAFTED" || draft.status === "EDITED" || draft.status === "REJECTED";
      if (!soapDraftVisible) {
        return null;
      }
      if (SOAP_TRACE_ENABLED) {
        console.debug("SOAP-DRAFT-TRACE-FE stage=RENDER", {
          traceId: draft.traceId,
          consultationId: consultation?.id || null,
          patientId: consultation?.patientId || null,
          draftStatus: draft.status,
          validationMessagePresent: Boolean(draft.error),
          subjectivePresent: soapFieldPresent(parsed.subjective),
          objectivePresent: soapFieldPresent(parsed.objective),
          assessmentPresent: soapFieldPresent(parsed.assessment),
          planPresent: soapFieldPresent(parsed.plan),
          sourceSummaryVitalsPresent: Boolean(draft.sourceSummary && draft.sourceSummary.includes("Vitals")),
        });
      }
      if (loading) {
        return (
          <ClinicalAiDraftCard {...commonProps} acceptLabel="Accept SOAP draft">
            <Typography variant="body2" color="text.secondary">
              Generating SOAP draft...
            </Typography>
          </ClinicalAiDraftCard>
        );
      }
      if (!hasMeaningfulSoapDraft(parsed)) {
        return (
          <ClinicalAiDraftCard {...commonProps} acceptLabel="Accept SOAP draft">
            <Alert severity="warning">
              {draft.error || "Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry."}
            </Alert>
          </ClinicalAiDraftCard>
        );
      }
      return (
        <ClinicalAiDraftCard {...commonProps} acceptLabel="Accept SOAP draft">
          <Stack spacing={0.75}>
            <Grid container spacing={0.75}>
              {[
                ["Subjective", parsed.subjective],
                ["Objective", parsed.objective],
                ["Assessment", parsed.assessment],
                ["Plan", parsed.plan],
              ].map(([label, value]) => (
                <Grid key={label} size={{ xs: 12, md: 6 }}>
                  <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1.5, bgcolor: "background.paper" }}>
                    <Typography variant="caption" color="text.secondary">{label}</Typography>
                    <Typography variant="body2" sx={{ mt: 0.2, whiteSpace: "pre-wrap" }}>{value || "-"}</Typography>
                  </Box>
                </Grid>
              ))}
            </Grid>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    if (kind === "followUp") {
      const response: AiDraftResponse = {
        enabled: true,
        fallbackUsed: false,
        message: "",
        provider: null,
        model: null,
        draft: draft.draftText,
        structuredData: draft.structuredData || {},
        confidence: null,
        suggestedActions: [],
        warnings: [],
      };
      const parsed = extractClinicalFollowUpDraft(response);
      return (
        <ClinicalAiDraftCard {...commonProps} acceptLabel="Set follow-up date">
          <Stack spacing={0.5}>
            <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
              {parsed.followUpDate ? `Suggested date: ${parsed.followUpDate}` : null}
              {parsed.interval ? `${parsed.followUpDate ? "\n" : ""}Interval: ${parsed.interval}` : null}
              {parsed.reason ? `${parsed.followUpDate || parsed.interval ? "\n" : ""}Reason: ${parsed.reason}` : null}
            </Typography>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    return (
      <ClinicalAiDraftCard {...commonProps} />
    );
  }, [acceptClinicalDraft, aiBusy, clinicalAiActiveDraft, clinicalAiDrafts, copyClinicalDraft, editClinicalDraft, rejectClinicalDraft, readOnly, updateClinicalAiDraft]);

  const renderConsultationDocumentDraftCard = React.useCallback((kind: ConsultationDocumentationKind) => {
    const draft = consultationDocumentDrafts[kind];
    const loading = consultationDocumentationSaving;
    const commonProps = {
      title: draft.title,
      status: draft.status,
      generatedAt: draft.generatedAt,
      sourceSummary: draft.sourceSummary,
      draftText: draft.draftText,
      error: draft.error,
      loading,
      acceptDisabled: readOnly || !draft.draftText.trim() || consultationDocumentationSaving,
      editDisabled: readOnly || !draft.draftText.trim() || consultationDocumentationSaving,
      rejectDisabled: consultationDocumentationSaving,
      copyDisabled: !draft.draftText.trim(),
      onAccept: () => void finalizeConsultationDocumentDraft(kind),
      onEdit: (nextText: string) => updateConsultationDocumentDraft(kind, {
        status: "EDITED",
        draftText: nextText,
        generatedAt: draft.generatedAt || new Date().toISOString(),
        error: null,
      }),
      onReject: () => rejectConsultationDocumentDraft(kind),
      onCopy: () => void copyConsultationDocumentDraft(kind),
      acceptLabel: "Finalize",
      editLabel: "Edit",
      rejectLabel: "Reject",
      copyLabel: "Copy",
      disclaimer: "AI-generated draft. Doctor must verify before use.",
    };

    if (kind === "referral") {
      return (
        <ClinicalAiDraftCard {...commonProps} acceptLabel="Finalize referral">
          <Stack spacing={0.75}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={0.75}>
              <TextField
                size="small"
                fullWidth
                label="Referral destination"
                value={consultationReferralDestination}
                disabled={readOnly || consultationDocumentationSaving}
                onChange={(event) => setConsultationReferralDestination(event.target.value)}
              />
              <FormControl size="small" fullWidth>
                <InputLabel id="referral-priority-label">Priority</InputLabel>
                <Select
                  labelId="referral-priority-label"
                  label="Priority"
                  value={consultationReferralPriority}
                  disabled={readOnly || consultationDocumentationSaving}
                  onChange={(event) => setConsultationReferralPriority(event.target.value as ConsultationDocumentPriority)}
                >
                  <MenuItem value="ROUTINE">Routine</MenuItem>
                  <MenuItem value="URGENT">Urgent</MenuItem>
                  <MenuItem value="EMERGENCY">Emergency</MenuItem>
                </Select>
              </FormControl>
            </Stack>
            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
              <Chip size="small" variant="outlined" label={`Language: ${consultationDocumentationLanguage}`} />
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("ENGLISH")}>English</Button>
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("HINDI")}>Hindi</Button>
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("MARATHI")}>Marathi</Button>
            </Stack>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    if (kind === "certificate") {
      const certificateTypeLabel = consultationCertificateType === "FITNESS"
        ? "Fitness Certificate"
        : consultationCertificateType === "SICK_LEAVE"
          ? "Sick Leave Certificate"
          : consultationCertificateType === "RETURN_TO_WORK"
            ? "Return to Work Certificate"
            : "Medical Certificate";
      return (
        <ClinicalAiDraftCard {...commonProps} title={certificateTypeLabel} acceptLabel="Finalize certificate">
          <Stack spacing={0.75}>
            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
              {([
                ["MEDICAL", "Medical"],
                ["FITNESS", "Fitness"],
                ["SICK_LEAVE", "Sick leave"],
                ["RETURN_TO_WORK", "Return to work"],
              ] as const).map(([value, label]) => (
                <Chip
                  key={value}
                  size="small"
                  clickable={!readOnly && !consultationDocumentationSaving}
                  variant={consultationCertificateType === value ? "filled" : "outlined"}
                  color={consultationCertificateType === value ? "primary" : "default"}
                  label={label}
                  onClick={() => setConsultationCertificateType(value)}
                />
              ))}
            </Stack>
            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
              <Chip size="small" variant="outlined" label={`Language: ${consultationDocumentationLanguage}`} />
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("ENGLISH")}>English</Button>
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("HINDI")}>Hindi</Button>
              <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("MARATHI")}>Marathi</Button>
            </Stack>
          </Stack>
        </ClinicalAiDraftCard>
      );
    }

    return (
      <ClinicalAiDraftCard {...commonProps} acceptLabel="Finalize summary">
        <Stack spacing={0.75}>
          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
            <Chip size="small" variant="outlined" label={`Language: ${consultationDocumentationLanguage}`} />
            <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("ENGLISH")}>English</Button>
            <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("HINDI")}>Hindi</Button>
            <Button size="small" variant="outlined" onClick={() => setConsultationDocumentLanguage("MARATHI")}>Marathi</Button>
          </Stack>
          {draft.status === "REJECTED" ? null : draft.draftText ? (
            <Typography variant="caption" color="text.secondary">
              Review this summary before finalizing. It will be stored as a consultation document.
            </Typography>
          ) : (
            <Typography variant="caption" color="text.secondary">
              No visit summary generated yet. Use AIVA to draft a patient-friendly consultation summary.
            </Typography>
          )}
        </Stack>
      </ClinicalAiDraftCard>
    );
  }, [consultationCertificateType, consultationDocumentDrafts, consultationDocumentationSaving, consultationReferralDestination, consultationReferralPriority, copyConsultationDocumentDraft, finalizeConsultationDocumentDraft, readOnly, rejectConsultationDocumentDraft, updateConsultationDocumentDraft]);

  async function applyAiPrescriptionTemplate() {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    const entryId = makeAiAssistEntryId();
    appendAiAssistEntry({
      id: entryId,
      action: "prescription",
      title: "Prescription template",
      prompt: null,
      createdAt: new Date().toISOString(),
      status: "loading",
      response: null,
      error: null,
    });
    setAiBusy(true);
    setAiActiveAction("prescription");
    setError(null);
    try {
      if (import.meta.env.DEV) {
        console.debug("[AI_MEDICINE_DEBUG] request", {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          diagnosisChars: consultationForm.diagnosis.trim().length,
          symptomsChars: consultationForm.symptoms.trim().length,
          allergiesChars: (patient.patient.allergies || "").trim().length,
          currentMedicationsChars: (patient.patient.longTermMedications || "").trim().length,
          doctorNotesChars: consultationForm.clinicalNotes.trim().length,
        });
      }
      const draft = await aiSuggestPrescriptionTemplate(auth.accessToken, auth.tenantId, {
        consultationId: consultation.id,
        patientId: consultation.patientId,
        patientAgeGender,
        vitals: aiVitalsSummary,
        currentPrescriptionDraft: currentPrescriptionDraftSummary || null,
        labOrdersSummary: labOrdersSummary || null,
        diagnosis: consultationForm.diagnosis,
        symptoms: consultationForm.symptoms,
        allergies: patient.patient.allergies,
        currentMedications: "",
        doctorNotes: consultationForm.clinicalNotes,
      });
      if (import.meta.env.DEV) {
        const suggestionCount = Array.isArray(draft.structuredData?.["suggestions"]) ? (draft.structuredData["suggestions"] as unknown[]).length : 0;
        console.debug("[AI_MEDICINE_DEBUG] response", {
          enabled: draft.enabled,
          fallbackUsed: draft.fallbackUsed,
          provider: draft.provider,
          model: draft.model,
          draftChars: (draft.draft || "").length,
          rawTextLength: (draft.draft || draft.message || "").length,
          suggestionCount,
          keys: Object.keys(draft.structuredData || {}),
        });
      }
      if (!draft.enabled) {
        setAiAvailable(false);
        const message = aiStatusMessage || AI_DISABLED_MESSAGE;
        updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
        setError(message);
        return;
      }
      if (!draft.provider && draft.fallbackUsed) {
        const message = draft.message || "AI providers are temporarily unavailable. Please retry.";
        updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
        setError(message);
        return;
      }
      const parsed = parseAiMedicineSuggestionItems(draft);
      if (parsed.invalid) {
        setAiPrescriptionSuggestion(parsed.summary || "AI returned an invalid response. Please retry.");
        setAiPrescriptionItems([]);
        setAiPrescriptionUnstructured(true);
        setAiPrescriptionProvider(draft.provider || null);
        updateAiAssistEntry(entryId, {
          status: "success",
          response: draft,
          error: "AI returned an invalid response. Please retry.",
        });
        setError("AI returned an invalid response. Please retry.");
        return;
      }
      setAiPrescriptionSuggestion(parsed.summary || parsed.rawText || null);
      setAiPrescriptionItems(parsed.items);
      setAiPrescriptionUnstructured(parsed.invalid);
      setAiPrescriptionProvider(draft.provider || null);
      setPrescriptionSuggestionGeneratedAt(new Date().toISOString());
      updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      setInfo("AI prescription suggestion generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      const normalized = message.toLowerCase();
      if (
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("gemini authorization failed")
        || normalized.includes("api key/provider configuration")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
      ) {
        setAiAvailable(false);
        const friendly =
          normalized.includes("authorization failed") || normalized.includes("api key/provider configuration")
            ? "AI provider authorization failed. Check API key/provider configuration."
            : normalized.includes("enabled for this clinic")
              ? AI_PROVIDER_NOT_CONFIGURED_MESSAGE
              : (aiStatusMessage || AI_DISABLED_MESSAGE);
        updateAiAssistEntry(entryId, { status: "error", error: friendly });
        setError(friendly);
      } else {
        updateAiAssistEntry(entryId, { status: "error", error: "AI assistance is temporarily unavailable." });
        setError("AI assistance is temporarily unavailable.");
      }
      console.error("AI prescription suggestion failed", err);
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
    }
  }

  const generatePrescriptionPatientInstructions = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    setPrescriptionInstructionsLoading(true);
    setError(null);
    try {
      const draft = await aiPatientInstructions(auth.accessToken, auth.tenantId, {
        consultationId: consultation.id,
        patientId: consultation.patientId,
        diagnosis: consultationForm.diagnosis || prescriptionForm.diagnosisSnapshot || null,
        prescription: currentPrescriptionDraftSummary || null,
        instructionsContext: [
          `Language: ${prescriptionInstructionsLanguage}`,
          consultationForm.advice.trim() ? `Advice context: ${consultationForm.advice.trim()}` : "",
          consultationForm.clinicalNotes.trim() ? `Clinical notes: ${consultationForm.clinicalNotes.trim()}` : "",
          currentMedicineRows.length ? `Medicines: ${currentMedicineRows.map((row) => row.medicineName).join(", ")}` : "",
        ].filter(Boolean).join("\n"),
        language: prescriptionInstructionsLanguage,
        literacyLevel: "simple",
        allergies: patient.patient.allergies,
        warnings: currentMedicineRows.length ? "Doctor must verify before prescribing." : null,
      });
      if (!draft.enabled) {
        const message = aiStatusMessage || AI_DISABLED_MESSAGE;
        setPrescriptionInstructionsDraft({ ...draft, message });
        setError(message);
        return;
      }
      setPrescriptionInstructionsDraft(draft);
      setPrescriptionInstructionsGeneratedAt(new Date().toISOString());
      setInfo("Patient instructions generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Patient instructions generation failed";
      setPrescriptionInstructionsDraft(null);
      setError(message);
    } finally {
      setPrescriptionInstructionsLoading(false);
    }
  };

  const generatePrescriptionSummaryDraft = () => {
    const warnings = prescriptionSafetyChecks
      .filter((item) => item.status === "warning" || item.status === "critical")
      .map((item) => item.message)
      .filter(Boolean)
      .slice(0, 4);
    const redFlags = latestClinicalReasoningEntry ? [compactText(latestClinicalReasoningEntry.prompt || latestClinicalReasoningEntry.error || latestClinicalReasoningEntry.title, 80)] : [];
    setPrescriptionSummaryDraft(buildPrescriptionSummaryText({
      diagnosis: consultationForm.diagnosis || prescriptionForm.diagnosisSnapshot || "",
      medicines: currentMedicineRows,
      tests: prescriptionForm.recommendedTests,
      advice: prescriptionForm.advice || consultationForm.advice,
      followUpDate: prescriptionForm.followUpDate || consultationForm.followUpDate,
      warnings,
      redFlags,
    }));
  };

  const generateClinicalSummary = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    const entryId = makeAiAssistEntryId();
    appendAiAssistEntry({
      id: entryId,
      action: "summary",
      title: "Clinical summary",
      prompt: null,
      createdAt: new Date().toISOString(),
      status: "loading",
      response: null,
      error: null,
    });
    setAiBusy(true);
    setAiActiveAction("summary");
    setError(null);
    try {
      const draft = await aiClinicalSummary(auth.accessToken, auth.tenantId, {
        patientId: consultation.patientId,
        patientName: `${patient.patient.firstName} ${patient.patient.lastName}`.trim(),
        historyText: patient.patient.notes || "",
        chronicHistory: [patient.patient.existingConditions, patient.patient.longTermMedications, patient.patient.surgicalHistory].filter(Boolean).join(" • "),
        recentConsultationSummary: lastConsultation ? `${lastConsultation.createdAt}: ${lastConsultation.diagnosis || "Consultation"}${lastConsultation.advice ? ` | Advice: ${lastConsultation.advice}` : ""}` : "",
        recentConsultations: (patient.previousConsultations || []).slice(0, 5).map((row) => `${row.createdAt}: ${row.diagnosis || "Consultation"}${row.advice ? ` | ${row.advice}` : ""}`),
        currentMedications: patient.patient.longTermMedications ? [patient.patient.longTermMedications] : [],
        allergies: patient.patient.allergies ? [patient.patient.allergies] : [],
        uploadedReportsSummary: clinicalDocuments
          .slice(0, 8)
          .map((row) => `${row.documentType.replaceAll("_", " ")}: ${row.aiExtractionSummary || row.description || row.title || row.originalFilename}`)
          .join(" | "),
      });
      if (!draft.enabled) {
        setAiAvailable(false);
        const message = aiStatusMessage || AI_DISABLED_MESSAGE;
        updateAiAssistEntry(entryId, { status: "error", response: draft, error: message });
        setError(message);
        return;
      }
      updateAiAssistEntry(entryId, { status: "success", response: draft, error: null });
      setClinicalSummary(draft);
      const summaryText = (typeof draft.structuredData?.["summary"] === "string" && String(draft.structuredData["summary"]).trim())
        || (draft.draft || draft.message || "").trim();
      if (summaryText) {
        try {
          const savedSummary = await saveConsultationAiSummary(auth.accessToken, auth.tenantId, consultation.id, {
            summary: summaryText,
            provider: draft.provider || null,
            model: draft.model || null,
            generatedAt: new Date().toISOString(),
          });
          if (savedSummary) {
            setSavedAiSummary(savedSummary);
          }
        } catch (saveErr) {
          console.error("AI summary persistence failed", saveErr);
        }
      }
      setInfo("Clinical summary generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      const normalized = message.toLowerCase();
      if (
        message.includes("HTTP 403")
        || message.includes("HTTP 404")
        || message.includes("HTTP 400")
        || normalized.includes("not enabled")
        || normalized.includes("not configured")
        || normalized.includes("module_disabled")
        || normalized.includes("enabled for this clinic")
      ) {
        setAiAvailable(false);
        const friendly = normalized.includes("enabled for this clinic") ? AI_PROVIDER_NOT_CONFIGURED_MESSAGE : (aiStatusMessage || AI_DISABLED_MESSAGE);
        updateAiAssistEntry(entryId, { status: "error", error: friendly });
        setError(friendly);
      } else {
        updateAiAssistEntry(entryId, { status: "error", error: "AI assistance is temporarily unavailable." });
        setError("AI assistance is temporarily unavailable.");
      }
      console.error("AI clinical summary failed", err);
    } finally {
      setAiBusy(false);
      setAiActiveAction(null);
    }
  };

  const renderAiAssistEntry = (entry: AiAssistEntry) => {
    const response = entry.response;
    const structured = response ? parseStructuredObject(response.structuredData || {}) : {};
    const rawText = response ? ((response.draft || response.message || "").trim()) : "";
    const generalAnswer = typeof structured.answer === "string" ? structured.answer.trim() : "";
    const suggestedActions = asStringList(structured.suggestedActions);
    const limitations = asStringList(structured.limitations);
    const isDiagnosisAction = entry.action === "diagnosis" || entry.action === "red_flags" || entry.action === "tests";
    const isPrescriptionAction = entry.action === "prescription" || entry.action === "drug_safety";
    const parsedDiagnosis = response && isDiagnosisAction ? parseAiSuggestionItems(response) : null;
    const parsedPrescription = response && isPrescriptionAction ? parseAiMedicineSuggestionItems(response) : null;
    const displayText = generalAnswer || rawText || entry.error || "No response captured yet.";
    const missingInformation = isDiagnosisAction ? asStringList(structured.missingInformation || structured.missingData || structured.gaps || structured.gapAnalysis) : [];
    const supportingFindings = isDiagnosisAction ? asStringList(structured.supportingFindings || structured.supportingEvidence || structured.findings) : [];

    return (
      <Card key={entry.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
        <CardContent sx={{ p: 1 }}>
          <Stack spacing={1}>
            <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{entry.title}</Typography>
                  <Chip size="small" variant="outlined" label={aiAssistActionLabel(entry.action)} />
                  <Chip size="small" variant="outlined" label={compactDateTime(entry.createdAt)} />
                </Stack>
                {entry.prompt ? (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, whiteSpace: "pre-wrap" }}>
                    Doctor: {entry.prompt}
                  </Typography>
                ) : null}
              </Box>
              <Chip
                size="small"
                label={entry.status === "loading" ? "Loading" : entry.status === "error" ? "Error" : "Ready"}
                color={entry.status === "loading" ? "warning" : entry.status === "error" ? "error" : "success"}
                variant="outlined"
              />
            </Stack>

            {entry.status === "loading" ? (
              <Alert severity="info" sx={{ py: 0.5 }}>Generating clinical draft…</Alert>
            ) : null}

            {entry.status === "error" ? (
              <Alert severity="error" sx={{ py: 0.5 }}>{entry.error || "AI request failed."}</Alert>
            ) : null}

            {entry.status !== "loading" && entry.status !== "error" ? (
              <Stack spacing={1}>
                {entry.error ? (
                  <Alert severity="warning" sx={{ py: 0.5 }}>{entry.error}</Alert>
                ) : null}
                {isDiagnosisAction ? (
                  <Stack spacing={0.9}>
                    <Grid container spacing={0.75}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Likely assessment</Typography>
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                {String(structured.likelyAssessment || structured.assessment || parsedDiagnosis?.summary || parsedDiagnosis?.items[0]?.title || displayText)}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Supporting findings</Typography>
                              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                {supportingFindings.length ? supportingFindings.slice(0, 4).map((finding) => <Chip key={finding} size="small" variant="outlined" label={finding} />) : <Typography variant="body2" color="text.secondary">Use the consultation context and reasoning text for support.</Typography>}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Missing information</Typography>
                              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                {missingInformation.length ? missingInformation.slice(0, 4).map((item) => <Chip key={item} size="small" color="warning" variant="outlined" label={item} />) : <Typography variant="body2" color="text.secondary">No missing information highlighted.</Typography>}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Differential diagnosis</Typography>
                              {parsedDiagnosis?.items?.length ? (
                                <Stack spacing={0.75}>
                                  {parsedDiagnosis.items.map((item, index) => (
                                    <Card key={`${entry.id}-diag-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                      <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                        <Stack spacing={0.5}>
                                          <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
                                            <Box sx={{ minWidth: 0, flex: 1 }}>
                                              <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }}>{item.title}</Typography>
                                              {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>{item.reason}</Typography> : null}
                                            </Box>
                                            <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={() => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, item.title) }))}>
                                              Add diagnosis
                                            </Button>
                                          </Stack>
                                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                            {item.redFlags.slice(0, 2).map((flag) => <Chip key={flag} size="small" color="warning" variant="outlined" label={`Red flag: ${flag}`} />)}
                                            {item.recommendedInvestigations.slice(0, 2).map((test) => <Chip key={test} size="small" variant="outlined" label={test} />)}
                                          </Stack>
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  ))}
                                </Stack>
                              ) : (
                                <Typography variant="body2" color="text.secondary">Generate clinical reasoning to review likely assessment, differentials, red flags, and suggested tests.</Typography>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Red flags</Typography>
                              {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.redFlags) || [])).slice(0, 4).length ? (
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.redFlags) || [])).slice(0, 4).map((flag) => <Chip key={flag} size="small" color="warning" variant="outlined" label={flag} />)}
                                </Stack>
                              ) : (
                                <Typography variant="body2" color="text.secondary">No red flags returned.</Typography>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Suggested investigations</Typography>
                              {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || [])).slice(0, 5).length || asStringList(structured.recommendedInvestigations).slice(0, 5).length ? (
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || [])).slice(0, 5).map((test) => <Chip key={test} size="small" variant="outlined" label={test} />)}
                                  {!Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || [])).slice(0, 5).length ? asStringList(structured.recommendedInvestigations).slice(0, 5).map((test) => <Chip key={test} size="small" variant="outlined" label={test} />) : null}
                                </Stack>
                              ) : (
                                <Typography variant="body2" color="text.secondary">No investigations returned.</Typography>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Suggested patient advice</Typography>
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                {asStringList(structured.patientAdvice || structured.advice || structured.followUpSuggestions || suggestedActions).slice(0, 3).join(" • ") || displayText}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Button type="button" size="small" variant="outlined" disabled={readOnly || !parsedDiagnosis?.summary} onClick={() => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, parsedDiagnosis?.summary || parsedDiagnosis?.rawText || "") }))}>
                        Add diagnosis
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={readOnly || !displayText} onClick={() => setConsultationForm((c) => ({ ...c, clinicalNotes: appendTokenLine(c.clinicalNotes, displayText) }))}>
                        Add to SOAP
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={readOnly || !displayText} onClick={() => appendAdviceAndReveal(displayText)}>
                        Add to Advice
                      </Button>
                      <Button
                        type="button"
                        size="small"
                        variant="outlined"
                        disabled={prescriptionReadOnly || !Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || [])).length}
                        onClick={() => {
                          const tests = Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || []));
                          if (!tests.length || prescriptionReadOnly) return;
                          setPrescriptionForm((current) => ({
                            ...current,
                            recommendedTests: [...current.recommendedTests, ...tests.map((test, index) => ({ ...newTestRow(current.recommendedTests.length + index), testName: test }))],
                          }));
                        }}
                      >
                        Add to recommended tests
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={!displayText} onClick={() => void navigator.clipboard.writeText(displayText)}>
                        Copy
                      </Button>
                    </Stack>
                  </Stack>
                ) : isPrescriptionAction ? (
                  <Stack spacing={0.9}>
                    {parsedPrescription?.items?.length ? (
                      <Stack spacing={0.75}>
                        {parsedPrescription.items.map((item, index) => (
                          <Card key={`${entry.id}-rx-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.6}>
                                <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
                                  <Box sx={{ minWidth: 0, flex: 1 }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.3 }}>{item.medicine}</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>
                                      {[item.dose, item.frequency, item.duration].filter(Boolean).join(" • ") || "No dose details provided"}
                                    </Typography>
                                    {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>{item.reason}</Typography> : null}
                                    {item.safetyNote ? <Typography variant="caption" color="warning.main" sx={{ display: "block", mt: 0.15 }}>{item.safetyNote}</Typography> : null}
                                  </Box>
                                  <Button type="button" size="small" variant="outlined" disabled={prescriptionReadOnly} onClick={() => addMedicineFromAiSuggestion(item)}>
                                    Add
                                  </Button>
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    ) : (
                      <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                        <Typography variant="body2" sx={{ lineHeight: 1.5 }}>{displayText}</Typography>
                      </Box>
                    )}
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Button type="button" size="small" variant="outlined" disabled={prescriptionReadOnly || !displayText} onClick={() => setPrescriptionForm((current) => ({ ...current, advice: appendTokenLine(current.advice, displayText) }))}>
                        Add to advice
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={!displayText} onClick={() => void navigator.clipboard.writeText(displayText)}>
                        Copy
                      </Button>
                    </Stack>
                  </Stack>
                ) : (
                  <Stack spacing={0.9}>
                    <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>{displayText}</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      {entry.action === "notes" ? (
                        <Button type="button" size="small" variant="outlined" disabled={readOnly || !displayText} onClick={() => setConsultationForm((current) => ({ ...current, clinicalNotes: appendTokenLine(current.clinicalNotes, displayText) }))}>
                          Apply to notes
                        </Button>
                      ) : null}
                      {entry.action === "instructions" ? (
                        <Button type="button" size="small" variant="outlined" disabled={prescriptionReadOnly || !displayText} onClick={() => setPrescriptionForm((current) => ({ ...current, advice: appendTokenLine(current.advice, displayText) }))}>
                          Apply to advice
                        </Button>
                      ) : null}
                      <Button type="button" size="small" variant="outlined" disabled={!displayText} onClick={() => void navigator.clipboard.writeText(displayText)}>
                        Copy
                      </Button>
                    </Stack>
                  </Stack>
                )}
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
    );
  };

  if (loading) {
    return (
      <Box sx={{ display: "grid", placeItems: "center", minHeight: 260 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!consultation) {
    return (
      <Stack spacing={2}>
        {error ? <Alert severity="error">{error}</Alert> : <Alert severity="info">Open a consultation from the queue to start.</Alert>}
        <Button type="button" variant="outlined" onClick={() => void backToQueue()}>Back to Queue</Button>
      </Stack>
    );
  }

  const header = (
    <Card
      ref={consultationHeaderRef}
      sx={{
        position: "sticky",
        top: 12,
        zIndex: 5,
        borderColor: "primary.light",
        background: "linear-gradient(135deg, rgba(255,255,255,0.98), rgba(240,253,250,0.96))",
        backdropFilter: "blur(12px)",
      }}
    >
      <CardContent sx={{ py: 0.9, "&:last-child": { pb: 0.9 } }}>
        <Stack spacing={0.7}>
          <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: 1 }}>
              <Box
                sx={{
                  width: 44,
                  height: 44,
                  borderRadius: "50%",
                  display: "grid",
                  placeItems: "center",
                  bgcolor: "primary.50",
                  color: "primary.main",
                  border: 1,
                  borderColor: "primary.light",
                  fontWeight: 900,
                  flexShrink: 0,
                }}
              >
                {((patientRow ? `${patientRow.firstName} ${patientRow.lastName}` : consultation.patientName || consultation.patientId) || "P")
                  .split(/\s+/)
                  .filter(Boolean)
                  .slice(0, 2)
                  .map((part) => part[0]?.toUpperCase() || "")
                  .join("") || "P"}
              </Box>
              <Stack spacing={0.3} sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                  <Typography variant="h6" sx={{ fontWeight: 950, lineHeight: 1, fontSize: { xs: "1.12rem", md: "1.35rem" } }}>
                    {patientRow ? `${patientRow.firstName} ${patientRow.lastName}` : consultation.patientName || consultation.patientId}
                  </Typography>
                  <Chip size="small" label={formatPatientAgeGender(patientRow)} />
                  <WorkflowStatusBadge status={consultation.status} />
                  {appointment ? <AppointmentTokenChip appointment={appointment} compact /> : null}
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                  Patient summary, timeline, clinical notes, prescriptions, investigations, and AI companion in one workspace.
                </Typography>
                {appointment ? (
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                    {formatRelativeBookingTime(appointment.createdAt) || "Booked recently"}
                  </Typography>
                ) : null}
              </Stack>
            </Stack>

            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" justifyContent="flex-end" sx={{ maxWidth: { xs: "100%", md: 520 } }}>
              <Button type="button" size="small" variant="outlined" onClick={() => void backToQueue()}>Back to Queue</Button>
              {canEditConsultation && !readOnly ? <Button type="button" size="small" disabled={saving} onClick={() => void manualSaveDraft()}>Save Draft</Button> : null}
              {aiAssistantEnabled ? <Button type="button" size="small" variant="contained" title="Generate reviewable AI drafts for multiple consultation sections." startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void generateConsultationDraft(false)}>Generate AI Consultation Draft</Button> : null}
              {canRunAi && Object.values(clinicalAiDrafts).some((draft) => draft.status === "ACCEPTED" || draft.status === "EDITED") ? (
                <Button
                  type="button"
                  size="small"
                  variant="outlined"
                  disabled={!canGenerateClinicalDraft}
                  onClick={() => void regenerateAllClinicalDrafts()}
                >
                  Regenerate All
                </Button>
              ) : null}
              {canEditConsultation && !readOnly ? <Button type="button" size="small" variant="outlined" disabled={saving || !labTests.length} onClick={openLabOrderDialog}>Order Lab Tests</Button> : null}
              {canEditConsultation && !prescriptionReadOnly ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void previewCurrentPrescription()}>Preview Rx</Button> : null}
              {canCompleteConsultation && !readOnly ? <Button type="button" size="small" variant="contained" color="primary" disabled={saving || !prescriptionReadyForCompletion} onClick={() => setCompleteConsultationDialogOpen(true)}>Complete</Button> : null}
              {canPrintPrescription ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void printCurrentPrescription()}>Print Rx</Button> : null}
              {canPrintPrescription ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void downloadCurrentPrescription()}>Download PDF</Button> : null}
            </Stack>
          </Box>

          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            {summaryMetrics.map((metric) => (
              <Chip
                key={metric.label}
                variant="outlined"
                size="small"
                icon={
                  <Box sx={{ display: "grid", placeItems: "center", width: 18, height: 18, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main" }}>
                    {summaryMetricIcon(metric.label)}
                  </Box>
                }
                label={`${metric.label}: ${metric.value}`}
                sx={{
                  maxWidth: "100%",
                  "& .MuiChip-label": {
                    maxWidth: "100%",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  },
                }}
              />
            ))}
          </Stack>

          <Stack direction={{ xs: "column", lg: "row" }} spacing={0.55} justifyContent="space-between" alignItems={{ xs: "flex-start", lg: "center" }}>
            <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: { lg: "nowrap" }, lineHeight: 1.2 }}>
              Shortcuts: Ctrl/Cmd+S save draft, Ctrl/Cmd+P preview Rx, Esc closes document viewer.
            </Typography>
          </Stack>
          <Card variant="outlined" ref={consultationChecklistRef} sx={{ boxShadow: "none" }}>
            <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
              <Stack spacing={0.65}>
                <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 950, lineHeight: 1.15 }}>
                      Clinical Documentation Guide
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.15 }}>
                      Guidance only. This does not change completion rules.
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Button
                      type="button"
                      size="small"
                      variant="text"
                      aria-label="View guide"
                      onClick={() => setReadinessDetailsOpen((current) => !current)}
                    >
                      {readinessDetailsOpen ? "Hide" : "View guide"}
                    </Button>
                  </Stack>
                </Stack>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                  {consultationDocumentationGuide.items.slice(0, 4).map((item) => (
                    <Chip
                      key={item.label}
                      size="small"
                      variant="outlined"
                      color={item.tone === "success" ? "success" : item.tone === "warning" ? "warning" : item.tone === "error" ? "error" : "default"}
                      label={`${item.label} ${item.stateLabel}`}
                    />
                  ))}
                  {consultationDocumentationGuide.items.length > 4 ? (
                    <Chip size="small" variant="outlined" label={`+${consultationDocumentationGuide.items.length - 4} more`} />
                  ) : null}
                </Stack>
                <Collapse in={readinessDetailsOpen} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                  <Stack spacing={0.55}>
                    {consultationDocumentationGuide.items.map((item) => (
                      <Stack key={item.label} direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.label}</Typography>
                        <Chip
                          size="small"
                          variant="outlined"
                          color={item.tone === "success" ? "success" : item.tone === "warning" ? "warning" : item.tone === "error" ? "error" : "default"}
                          label={item.stateLabel}
                        />
                      </Stack>
                    ))}
                  </Stack>
                </Collapse>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      </CardContent>
    </Card>
  );

  return (
    <Stack spacing={1.25} sx={{ pb: 3.5 }}>
      {header}
      {showStickyConsultationProgress ? (
        <Box
          sx={{
            display: { xs: "none", md: "block" },
            position: "sticky",
            top: 72,
            zIndex: 4,
          }}
        >
          <Card variant="outlined" sx={{ boxShadow: 2, bgcolor: "background.paper", borderRadius: 2 }}>
            <CardContent sx={{ py: 0.6, "&:last-child": { pb: 0.6 } }}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Completion readiness</Typography>
                  <Chip size="small" color={consultationCompletionSummary.statusTone} variant="outlined" label={consultationCompletionSummary.statusLabel} />
                  <Chip size="small" variant="outlined" label={`Required ${consultationCompletionSummary.requiredReadyCount}/1 ready`} />
                  <Chip size="small" variant="outlined" label={`Documentation ${consultationCompletionSummary.documentationRecordedCount} recorded`} />
                  <Chip size="small" variant="outlined" label={`Optional ${consultationCompletionSummary.optionalPreparedCount} prepared`} />
                </Stack>
                <Button
                  type="button"
                  size="small"
                  variant="outlined"
                  aria-label="View completion readiness"
                  onClick={() => {
                    consultationCompletionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
                  }}
                >
                  View readiness
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Box>
      ) : null}
      {timelinePreview.length ? (
        <Card sx={{ boxShadow: "none", borderColor: "primary.light" }}>
          <CardContent sx={{ py: 0.55, px: 0.8, "&:last-child": { pb: 0.55 } }}>
            <Stack spacing={0.6}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                <Box>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <TimelineRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                      {`Patient timeline · ${timelinePreview.length} recent`}
                    </Typography>
                    <Chip size="small" variant="outlined" label={`${timelinePreview.length} recent`} sx={{ height: 20 }} />
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                    Recent visits, prescriptions, and documents
                  </Typography>
                </Box>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" justifyContent="flex-end">
                  <Button
                    type="button"
                    size="small"
                    variant="outlined"
                    onClick={() => setTimelinePreviewExpanded((current) => !current)}
                  >
                    {timelinePreviewExpanded ? "Hide timeline" : "Show timeline"}
                  </Button>
                  <Button type="button" size="small" variant="outlined" startIcon={<HistoryRoundedIcon fontSize="small" />} onClick={openHistoryTab}>
                    View Full History
                  </Button>
                </Stack>
              </Stack>
              {timelinePreviewExpanded ? (
                <Stack direction="row" spacing={0.65} sx={{ overflowX: "auto", pb: 0.2, minHeight: 74, maxHeight: 88 }}>
                  {timelinePreview.map((item) => (
                    <Card
                      key={item.id}
                      variant="outlined"
                      sx={{
                        boxShadow: "none",
                        minWidth: 102,
                        maxWidth: 112,
                        flex: "0 0 auto",
                        cursor: item.itemType === "DOCUMENT" || item.consultationId || item.prescriptionId ? "pointer" : "default",
                        borderColor: item.consultationId === consultation.id ? "primary.main" : "divider",
                        bgcolor: item.consultationId === consultation.id ? "primary.50" : "background.paper",
                        borderRadius: 1.5,
                        transition: "transform 120ms ease, box-shadow 120ms ease",
                        "&:hover": {
                          transform: "translateY(-1px)",
                          boxShadow: 1,
                        },
                      }}
                      onClick={() => {
                        if (item.itemType === "DOCUMENT" && item.documentId) {
                          const document = clinicalDocuments.find((row) => row.id === item.documentId);
                          if (document) void openClinicalDocument(document);
                          return;
                        }
                        if (item.consultationId && item.consultationId !== consultation.id) {
                          navigate(`/consultations/${item.consultationId}`);
                        }
                      }}
                    >
                      <CardContent sx={{ p: 0.5, "&:last-child": { pb: 0.5 } }}>
                        <Stack spacing={0.35}>
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            <Box sx={{ width: 20, height: 20, borderRadius: "50%", display: "grid", placeItems: "center", bgcolor: "primary.50", color: "primary.main", flexShrink: 0 }}>
                              {item.itemType === "PRESCRIPTION" ? <MedicationRoundedIcon fontSize="inherit" /> : item.itemType === "LAB_ORDER" || item.itemType === "DOCUMENT" ? <ScienceRoundedIcon fontSize="inherit" /> : item.itemType === "INVESTIGATION" ? <BiotechRoundedIcon fontSize="inherit" /> : <HistoryRoundedIcon fontSize="inherit" />}
                            </Box>
                            <Chip size="small" label={timelineTypeLabel(item.itemType)} color={timelineColor(item.itemType)} variant="outlined" sx={{ height: 18, maxWidth: 72, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
                          </Stack>
                          <Typography
                            variant="body2"
                            sx={{
                              fontWeight: 850,
                              lineHeight: 1.1,
                              fontSize: 11.25,
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden",
                              minHeight: 24,
                            }}
                          >
                            {compactTimelineTitle(item.title)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.05 }} noWrap>
                            {compactDateTime(item.occurredAt)}
                          </Typography>
                          {item.status ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.05 }} noWrap>{item.status.replaceAll("_", " ")}</Typography> : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Card sx={{ boxShadow: "none", borderColor: "divider" }}>
          <CardContent sx={{ py: 0.7, "&:last-child": { pb: 0.7 } }}>
            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
              <Box>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <TimelineRoundedIcon fontSize="small" color="primary" />
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Patient timeline</Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  No previous consultations available.
                </Typography>
              </Box>
              <Button type="button" size="small" variant="outlined" startIcon={<HistoryRoundedIcon fontSize="small" />} onClick={openHistoryTab}>
                View Full History
              </Button>
            </Stack>
          </CardContent>
        </Card>
      )}
      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}

      <Card sx={{ boxShadow: "none" }}>
        <CardContent sx={{ py: 0.5, "&:last-child": { pb: 0.5 } }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => {
              setActiveTab(value);
              const nextParams = new URLSearchParams(searchParams);
              nextParams.set("tab", consultationTabIndexToKey(value));
              if (value === 2) {
                nextParams.set("historyView", "timeline");
              } else {
                nextParams.delete("historyView");
              }
              setSearchParams(nextParams, { replace: true });
            }}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              minHeight: 48,
              "& .MuiTabs-indicator": {
                height: 3,
                borderRadius: 999,
              },
            }}
          >
            <Tab
              label="Consultation"
              icon={<ChatBubbleOutlineRoundedIcon fontSize="small" />}
              iconPosition="start"
              sx={workspaceTabSx}
            />
            <Tab
              label="Prescription"
              icon={<MedicationRoundedIcon fontSize="small" />}
              iconPosition="start"
              sx={workspaceTabSx}
            />
            <Tab
              label="History"
              icon={<HistoryRoundedIcon fontSize="small" />}
              iconPosition="start"
              sx={workspaceTabSx}
            />
            <Tab
              label="Investigations"
              icon={<BiotechRoundedIcon fontSize="small" />}
              iconPosition="start"
              sx={workspaceTabSx}
            />
            <Tab
              label="Lab Orders"
              icon={<ScienceRoundedIcon fontSize="small" />}
              iconPosition="start"
              sx={workspaceTabSx}
            />
            {aiAssistantEnabled ? (
              <Tab
                label="AI Assist"
                icon={<AutoAwesomeRoundedIcon fontSize="small" />}
                iconPosition="start"
                sx={workspaceTabSx}
              />
            ) : null}
          </Tabs>
        </CardContent>
      </Card>

      {activeTab === 0 ? (
        <Card sx={{ boxShadow: "none", overflow: "visible", borderColor: "divider" }}>
          <CardContent sx={{ p: 0.75, "&:last-child": { pb: 0.75 } }}>
            <Grid container spacing={0.65} alignItems="stretch" sx={{ minHeight: 0 }}>
              <Grid size={{ xs: 12 }}>
                <WorkflowStrip text="Patient Review → Clinical Assessment → Diagnosis → Treatment → Complete" />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Card ref={consultationCompletionRef} variant="outlined" sx={{ boxShadow: "none", borderColor: consultationCompletionSummary.statusTone === "success" ? "success.light" : consultationCompletionSummary.statusTone === "warning" ? "warning.light" : consultationCompletionSummary.statusTone === "error" ? "error.light" : "divider" }}>
                  <CardContent sx={{ p: 1, "&:last-child": { pb: 1 } }}>
                    <Stack spacing={0.85}>
                      <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                        <Box sx={{ minWidth: 0 }}>
                          <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                            <PlaylistAddCheckRoundedIcon fontSize="small" color="primary" />
                            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Consultation Completion</Typography>
                            <Chip size="small" color={consultationCompletionSummary.statusTone} variant="outlined" label={consultationCompletionSummary.statusLabel} />
                            <Chip size="small" variant="outlined" label={`Required ${consultationCompletionSummary.requiredReadyCount}/1 ready`} />
                          </Stack>
                          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                            Final completion is blocked only by prescription readiness. Documentation and optional outputs below are advisory signals.
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" justifyContent="flex-end">
                          <Button type="button" size="small" variant="outlined" onClick={() => setCompletionValidationOpen(true)}>
                            View validation
                          </Button>
                          <Button type="button" size="small" variant="text" aria-label={consultationCompletionExpanded ? "Collapse consultation completion" : "Expand consultation completion"} onClick={() => setConsultationCompletionExpanded((current) => !current)}>
                            {consultationCompletionExpanded ? "Collapse" : "Expand"}
                          </Button>
                        </Stack>
                      </Stack>
                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        <Chip size="small" variant="outlined" label={`Documentation ${consultationCompletionSummary.documentationRecordedCount} recorded${consultationCompletionSummary.documentationReviewCount ? `, ${consultationCompletionSummary.documentationReviewCount} review recommended` : ""}`} />
                        <Chip size="small" variant="outlined" label={`Optional ${consultationCompletionSummary.optionalPreparedCount} prepared`} />
                        <Chip size="small" variant="outlined" color={consultationCompletionSummary.statusTone} label={consultationCompletionSummary.statusLabel} />
                      </Stack>
                      <Alert severity={consultationCompletionSummary.statusTone === "error" ? "error" : consultationCompletionSummary.statusTone === "warning" ? "warning" : "success"} sx={{ py: 0.5 }}>
                        {consultationCompletionSummary.statusDetail}
                      </Alert>
                      <Collapse in={consultationCompletionExpanded} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                        <Stack spacing={1}>
                          {consultationCompletionSummary.groups.map((group) => (
                            <Card key={group.title} variant="outlined" sx={{ boxShadow: "none" }}>
                              <CardContent sx={{ p: 1, "&:last-child": { pb: 1 } }}>
                                <Stack spacing={0.8}>
                                  <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                                    <Box sx={{ minWidth: 0 }}>
                                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{group.title}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                        {group.helper}
                                      </Typography>
                                    </Box>
                                    <Chip size="small" variant="outlined" color={group.summaryTone} label={group.summaryLabel} />
                                  </Stack>
                                  <Stack spacing={0.55}>
                                    {group.items.map((item) => (
                                      <Stack key={item.label} direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                        <Box sx={{ minWidth: 0 }}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.label}</Typography>
                                          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                            {item.detail}
                                          </Typography>
                                        </Box>
                                        <Chip size="small" variant="outlined" color={item.tone} label={item.stateLabel} />
                                      </Stack>
                                    ))}
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          ))}
                        </Stack>
                      </Collapse>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, xl: 8 }}>
                <Stack spacing={0.75}>
              <SectionCard
                id="complaint"
                title="Chief Complaint"
                subtitle="Start with the visit reason"
                icon={<ChatBubbleOutlineRoundedIcon fontSize="small" />}
                expanded={expanded.complaint}
                onToggle={toggleSection}
                primaryAction={(
                  <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("chiefComplaint")}>
                    Draft
                  </Button>
                )}
              >
                <Stack spacing={1}>
                  <TextField size="small" fullWidth value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} placeholder="Type complaint and press Tab to continue" />
                  {clinicalAiDrafts.chiefComplaint.generatedAt || clinicalAiDrafts.chiefComplaint.error || clinicalAiDrafts.chiefComplaint.status !== "DRAFTED" || clinicalAiDrafts.chiefComplaint.draftText.trim() ? renderClinicalAiDraftCard("chiefComplaint") : null}
                  <QuickChipGroup disabled={readOnly} chips={SYMPTOM_CHIPS.slice(0, 6)} onPick={(chip) => setConsultationForm((c) => ({ ...c, chiefComplaints: appendTokenLine(c.chiefComplaints, chip) }))} />
                </Stack>
              </SectionCard>

              <SectionCard
                id="symptoms"
                title="Symptoms"
                subtitle="Chip-first symptom capture"
                icon={<HealingRoundedIcon fontSize="small" />}
                expanded={expanded.symptoms}
                onToggle={toggleSection}
                primaryAction={(
                  <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("symptoms")}>
                    Extract
                  </Button>
                )}
              >
                <Stack spacing={1}>
                  {clinicalAiDrafts.symptoms.generatedAt || clinicalAiDrafts.symptoms.error || clinicalAiDrafts.symptoms.status !== "DRAFTED" || clinicalAiDrafts.symptoms.draftText.trim() ? renderClinicalAiDraftCard("symptoms") : null}
                  <QuickChipGroup disabled={readOnly} chips={SYMPTOM_CHIPS} onPick={(chip) => setConsultationForm((c) => ({ ...c, symptoms: appendTokenLine(c.symptoms, chip) }))} />
                  <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
                    <TextField fullWidth size="small" label="Custom symptom" value={customSymptom} disabled={readOnly} onChange={(e) => setCustomSymptom(e.target.value)} onKeyDown={(e) => {
                      if (e.key === "Enter" && customSymptom.trim()) {
                        e.preventDefault();
                        setConsultationForm((c) => ({ ...c, symptoms: appendTokenLine(c.symptoms, customSymptom) }));
                        setCustomSymptom("");
                      }
                    }} />
                    <Button type="button" variant="outlined" disabled={readOnly} onClick={() => {
                      setConsultationForm((c) => ({ ...c, symptoms: appendTokenLine(c.symptoms, customSymptom) }));
                      setCustomSymptom("");
                    }}>Add</Button>
                  </Stack>
                  <TextField size="small" fullWidth label="Selected symptoms" value={consultationForm.symptoms} onChange={(e) => setConsultationForm((c) => ({ ...c, symptoms: e.target.value }))} disabled={readOnly} />
                </Stack>
              </SectionCard>

              <SectionCard
                id="diagnosis"
                title="Diagnosis"
                subtitle={diagnosisSectionSubtitle}
                icon={<PsychologyRoundedIcon fontSize="small" />}
                expanded={expanded.diagnosis}
                onToggle={toggleSection}
                primaryAction={null}
              >
                <Stack spacing={1}>
                  <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Manual diagnosis"
                      value={customDiagnosis}
                      disabled={readOnly}
                      onChange={(e) => setCustomDiagnosis(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && customDiagnosis.trim()) {
                          e.preventDefault();
                          setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, customDiagnosis) }));
                          setCustomDiagnosis("");
                        }
                      }}
                    />
                    <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                      <Button
                        type="button"
                        variant="outlined"
                        startIcon={<AddRoundedIcon fontSize="small" />}
                        disabled={readOnly}
                        onClick={() => {
                          setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, customDiagnosis) }));
                          setCustomDiagnosis("");
                        }}
                      >
                        Add
                      </Button>
                    </Stack>
                  </Stack>

                  <Stack spacing={0.75}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                      Selected Diagnoses
                    </Typography>
                    {selectedDiagnosisEntries.length ? (
                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                        {selectedDiagnosisEntries.map((diagnosisEntry) => (
                          <Chip
                            key={diagnosisEntry}
                            label={diagnosisEntry}
                            variant="outlined"
                            size="small"
                            onDelete={readOnly ? undefined : () => handleRemoveDiagnosisEntry(diagnosisEntry)}
                            deleteIcon={
                              readOnly ? undefined : <DeleteOutlineRoundedIcon fontSize="small" aria-hidden="true" />
                            }
                            aria-label={`Remove ${diagnosisEntry}`}
                            sx={{
                              maxWidth: "100%",
                              "& .MuiChip-label": {
                                display: "block",
                                whiteSpace: "normal",
                              },
                            }}
                          />
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No diagnosis added yet. Add manually or accept an AI suggestion.
                      </Typography>
                    )}
                  </Stack>

                  <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                    <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                      <Stack spacing={0.8}>
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <PsychologyRoundedIcon fontSize="small" color="primary" />
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                            Selected diagnosis
                          </Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          Confirmed diagnoses and manual entries
                        </Typography>
                        <QuickChipGroup
                          disabled={readOnly}
                          chips={DIAGNOSIS_CHIPS}
                          color="primary"
                          onPick={(chip) => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, chip) }))}
                        />
                        <TextField
                          size="small"
                          fullWidth
                          multiline
                          minRows={3}
                          label="Selected diagnosis"
                          value={consultationForm.diagnosis}
                          onChange={(e) => setConsultationForm((c) => ({ ...c, diagnosis: e.target.value }))}
                          disabled={readOnly}
                          InputProps={{
                            sx: {
                              maxHeight: 150,
                              overflowY: "auto",
                              alignItems: "flex-start",
                              "& textarea": {
                                whiteSpace: "pre-wrap",
                                overflowWrap: "anywhere",
                              },
                            },
                          }}
                        />
                      </Stack>
                    </CardContent>
                  </Card>

                  {aiAssistantEnabled ? (
                    <>
                      {clinicalAiDrafts.diagnosis.generatedAt || clinicalAiDrafts.diagnosis.error || clinicalAiDrafts.diagnosis.status !== "DRAFTED" || clinicalAiDrafts.diagnosis.draftText.trim() ? renderClinicalAiDraftCard("diagnosis") : null}

                      <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                    <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                          <Stack direction="row" spacing={0.75} alignItems="center">
                            <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                            <Typography variant="subtitle2" sx={{ fontWeight: 950 }}>Clinical Reasoning</Typography>
                          </Stack>
                          {!clinicalReasoning.hasLoaded ? (
                            <Chip size="small" variant="outlined" label="Loading saved reasoning..." />
                          ) : (
                            <Chip size="small" variant="outlined" label={clinicalReasoningStatusLabel} />
                          )}
                        </Stack>
                        <Alert severity="warning" sx={{ py: 0.5 }}>
                          AI suggestions are assistive only. Doctor must verify before use.
                        </Alert>
                        <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                          <Button
                            type="button"
                            size="small"
                            variant="contained"
                            startIcon={<AutoAwesomeRoundedIcon fontSize="small" />}
                            disabled={!consultation || !hasClinicalReasoningContext || clinicalReasoning.loading || aiBusy}
                            onClick={() => triggerClinicalReasoning()}
                          >
                            {clinicalReasoning.reasoningStatus && clinicalReasoning.reasoningStatus !== "NOT_GENERATED" ? "Regenerate Clinical Reasoning" : "Generate Clinical Reasoning"}
                          </Button>
                        </Stack>
                        {clinicalReasoning.loading ? (
                          <Alert severity="info" sx={{ py: 0.5 }}>
                            {!clinicalReasoning.hasLoaded ? "Loading saved reasoning..." : clinicalReasoningLoadingMessages[clinicalReasoningLoadingStepIndex]}
                          </Alert>
                        ) : null}
                        {clinicalReasoning.warning ? (
                          <Alert severity="warning" sx={{ py: 0.5 }}>
                            {clinicalReasoning.warning}
                          </Alert>
                        ) : null}
                        {clinicalReasoning.error ? (
                          <Alert
                            severity="error"
                            sx={{ py: 0.5 }}
                            action={(
                              <Button type="button" color="inherit" size="small" onClick={() => triggerClinicalReasoning()}>
                                Retry
                              </Button>
                            )}
                          >
                            Unable to generate AI reasoning. You may continue consultation normally.
                          </Alert>
                        ) : null}
                        {clinicalReasoningResult ? (
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                              <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 950 }}>Reasoning details</Typography>
                              {clinicalReasoning.generatedByDisplayName ? <Chip size="small" variant="outlined" label={`Generated by: ${clinicalReasoning.generatedByDisplayName}`} /> : null}
                              <Chip size="small" variant="outlined" label={`Status: ${clinicalReasoningStatusLabel}`} />
                              <Chip size="small" variant="outlined" label={`Provider: ${clinicalReasoningProviderLabel}`} />
                              <Chip size="small" variant="outlined" label={`Generated: ${compactDateTime(clinicalReasoning.lastGeneratedAt || clinicalReasoningResult.generatedAt)}`} />
                              <Chip size="small" color="primary" variant="outlined" label={`Confidence: ${clinicalReasoningSummaryConfidenceLabel}`} />
                            </Stack>
                              <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                                <Tooltip title="Refresh clinical reasoning">
                                  <span>
                                    <Button
                                      type="button"
                                      size="small"
                                      variant="contained"
                                      startIcon={<RefreshRoundedIcon fontSize="small" />}
                                      disabled={clinicalReasoning.loading || aiBusy}
                                      onClick={() => triggerClinicalReasoning()}
                                      aria-label="Refresh clinical reasoning"
                                    >
                                      Refresh
                                    </Button>
                                  </span>
                                </Tooltip>
                                <Tooltip title="Accept the primary diagnosis into the diagnosis field">
                                  <span>
                                    <Button
                                      type="button"
                                      size="small"
                                      variant="outlined"
                                      onClick={() => void acceptClinicalReasoningDiagnosis()}
                                      disabled={!clinicalReasoningResult.primaryDiagnosis?.name && !clinicalReasoningResult.reasoningSummary}
                                      aria-label="Accept diagnosis from clinical reasoning"
                                    >
                                      Accept Diagnosis
                                    </Button>
                                  </span>
                                </Tooltip>
                              </Stack>
                            </Stack>

                            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                              <CardContent sx={{ p: 1 }}>
                                <Stack spacing={1}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Typography variant="subtitle2" sx={{ fontWeight: 950 }}>AI Summary</Typography>
                                    <Tooltip title="Jump to detailed reasoning">
                                      <Button
                                        type="button"
                                        size="small"
                                        variant="text"
                                        onClick={scrollToClinicalReasoningDetails}
                                        aria-label="Review reasoning"
                                      >
                                        Review Reasoning →
                                      </Button>
                                    </Tooltip>
                                  </Stack>
                                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                    <Chip size="small" color="success" variant="outlined" label={clinicalReasoningResult.primaryDiagnosis?.name || "Primary diagnosis pending"} />
                                    <Chip size="small" color="primary" variant="outlined" label={`Confidence ${clinicalReasoningSummaryConfidenceLabel}`} />
                                    {clinicalReasoningLongitudinalFindings.length ? (
                                      <Chip
                                        size="small"
                                        color="primary"
                                        variant="outlined"
                                        clickable
                                        onClick={() => scrollToClinicalReasoningSection("longitudinalContext")}
                                        aria-label={`Longitudinal findings ${clinicalReasoningLongitudinalFindings.length}`}
                                        label={`Longitudinal ${clinicalReasoningLongitudinalFindings.length}`}
                                      />
                                    ) : null}
                                    <Chip
                                      size="small"
                                      color="info"
                                      variant="outlined"
                                      clickable
                                      onClick={() => scrollToClinicalReasoningSection("evidence")}
                                      aria-label={`Evidence ${clinicalReasoningResult.supportingEvidence.length}`}
                                      label={`Evidence ${clinicalReasoningResult.supportingEvidence.length}`}
                                    />
                                    <Chip
                                      size="small"
                                      color="warning"
                                      variant="outlined"
                                      clickable
                                      onClick={() => scrollToClinicalReasoningSection("missingInformation")}
                                      aria-label={`Questions still to ask ${clinicalReasoningResult.missingInformation.length}`}
                                      label={`Questions ${clinicalReasoningResult.missingInformation.length}`}
                                    />
                                    <Chip
                                      size="small"
                                      color="error"
                                      variant="outlined"
                                      clickable
                                      onClick={() => scrollToClinicalReasoningSection("redFlags")}
                                      aria-label={`Red flags ${clinicalReasoningResult.redFlags.length}`}
                                      label={`Red Flags ${clinicalReasoningResult.redFlags.length}`}
                                    />
                                    <Chip
                                      size="small"
                                      color="secondary"
                                      variant="outlined"
                                      clickable
                                      onClick={() => scrollToClinicalReasoningSection("recommendedTests")}
                                      aria-label={`Recommended investigations ${clinicalReasoningResult.recommendedTests.length}`}
                                      label={`Investigations ${clinicalReasoningResult.recommendedTests.length}`}
                                    />
                                    <Chip
                                      size="small"
                                      color="info"
                                      variant="outlined"
                                      clickable
                                      onClick={() => scrollToClinicalReasoningSection("safetyNotes")}
                                      aria-label={`Clinical safety advice ${clinicalReasoningResult.safetyNotes.length}`}
                                      label={`Safety ${clinicalReasoningResult.safetyNotes.length}`}
                                    />
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>

                            <Box ref={clinicalReasoningReviewRef}>
                              <Stack spacing={1}>
                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("longitudinalContext")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "primary.main" }}>Longitudinal Clinical Context</Typography>
                                      <Chip size="small" color="primary" variant="outlined" label={`${clinicalReasoningLongitudinalFindings.length} findings`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("longitudinalContext")} aria-label={`${clinicalReasoningSectionsOpen.longitudinalContext ? "Collapse" : "Expand"} longitudinal clinical context`}>
                                      {clinicalReasoningSectionsOpen.longitudinalContext ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.longitudinalContext} timeout={200} unmountOnExit>
                                    <Stack spacing={0.75}>
                                      {clinicalReasoningLongitudinalFindings.length ? clinicalReasoningLongitudinalFindings.map((item, index) => (
                                        <Card key={`longitudinal-finding-${index}-${item.title || item.summary || "item"}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "primary.main" }}>
                                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                            <Stack spacing={0.45}>
                                              <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                                                <Typography variant="body2" sx={{ fontWeight: 800 }}>{item.title || "Historical finding"}</Typography>
                                                <Chip size="small" variant="outlined" label={clinicalReasoningHistoricalLabel(item.sourceDate)} />
                                                {formatClinicalReasoningVerificationLabel(item.verificationStatus) ? (
                                                  <Chip size="small" variant="outlined" label={formatClinicalReasoningVerificationLabel(item.verificationStatus)} />
                                                ) : null}
                                              </Stack>
                                              {item.summary ? <Typography variant="caption" color="text.secondary">{item.summary}</Typography> : null}
                                              {item.clinicalRelevance ? <Typography variant="caption" color="text.secondary">Clinical relevance: {item.clinicalRelevance}</Typography> : null}
                                              {(item.sourceReference || item.sourceType) ? (
                                                <Typography variant="caption" color="text.secondary">
                                                  Source: {[item.sourceReference, item.sourceType].filter(Boolean).join(" • ")}
                                                </Typography>
                                              ) : null}
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      )) : (
                                        <Typography variant="body2" color="text.secondary">No longitudinal findings were returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("primaryDiagnosis")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "success.main" }}>Primary Diagnosis</Typography>
                                      <Chip size="small" color="success" variant="outlined" label={`Confidence: ${clinicalReasoningPrimaryConfidenceLabel}`} />
                                      {clinicalReasoningPrimaryConfidencePercent ? <Typography variant="caption" color="text.secondary">{clinicalReasoningPrimaryConfidencePercent}</Typography> : null}
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("primaryDiagnosis")} aria-label={`${clinicalReasoningSectionsOpen.primaryDiagnosis ? "Collapse" : "Expand"} primary diagnosis`}>
                                      {clinicalReasoningSectionsOpen.primaryDiagnosis ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.primaryDiagnosis} timeout={200} unmountOnExit>
                                    <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "success.main" }}>
                                      <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                        <Stack spacing={0.45}>
                                          <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                            {clinicalReasoningResult.primaryDiagnosis?.name || "Not available"}
                                          </Typography>
                                          {clinicalReasoningResult.primaryDiagnosis?.whyConsidered ? <Typography variant="caption" color="text.secondary">Why considered: {clinicalReasoningResult.primaryDiagnosis.whyConsidered}</Typography> : null}
                                          {clinicalReasoningResult.primaryDiagnosis?.whyLessLikely ? <Typography variant="caption" color="text.secondary">Why less likely: {clinicalReasoningResult.primaryDiagnosis.whyLessLikely}</Typography> : null}
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("differentials")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "success.main" }}>Differentials</Typography>
                                      <Chip size="small" color="success" variant="outlined" label={`${clinicalReasoningResult.differentialDiagnoses.length} items`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("differentials")} aria-label={`${clinicalReasoningSectionsOpen.differentials ? "Collapse" : "Expand"} differentials`}>
                                      {clinicalReasoningSectionsOpen.differentials ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.differentials} timeout={200} unmountOnExit>
                                    <Stack spacing={0.75}>
                                      {clinicalReasoningResult.differentialDiagnoses.length ? clinicalReasoningResult.differentialDiagnoses.map((item, index) => (
                                        <Card key={`differential-${index}-${item.name || "item"}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "success.light" }}>
                                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                            <Stack spacing={0.45}>
                                              <Stack direction="row" spacing={0.75} justifyContent="space-between" alignItems="flex-start" flexWrap="nowrap">
                                                <Box sx={{ minWidth: 0, flex: 1 }}>
                                                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                                    {item.name || "Differential diagnosis"}
                                                  </Typography>
                                                  <Typography variant="caption" color="text.secondary">
                                                    {likelihoodLevelFromPercent(item.confidence)} • {item.confidence != null ? `${Math.round((item.confidence || 0) * 100)}%` : "n/a"}
                                                  </Typography>
                                                  {item.whyConsidered ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{item.whyConsidered}</Typography> : null}
                                                  {item.whyLessLikely ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>Why less likely: {item.whyLessLikely}</Typography> : null}
                                                </Box>
                                                <Box sx={{ flexShrink: 0 }}>
                                                  <Tooltip title="Append this differential to the diagnosis field">
                                                    <Button
                                                      type="button"
                                                      size="small"
                                                      variant="outlined"
                                                      onClick={() => addClinicalReasoningDifferential(item.name || item.whyConsidered || "")}
                                                      disabled={!item.name && !item.whyConsidered}
                                                      aria-label={`Add differential diagnosis ${item.name || item.whyConsidered || "item"}`}
                                                    >
                                                      Add
                                                    </Button>
                                                  </Tooltip>
                                                </Box>
                                              </Stack>
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      )) : (
                                        <Typography variant="body2" color="text.secondary">No differential diagnoses returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("evidence")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "info.main" }}>Supporting evidence</Typography>
                                      <Chip size="small" color="info" variant="outlined" label={`${clinicalReasoningResult.supportingEvidence.length} findings`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("evidence")} aria-label={`${clinicalReasoningSectionsOpen.evidence ? "Collapse" : "Expand"} supporting evidence`}>
                                      {clinicalReasoningSectionsOpen.evidence ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.evidence} timeout={200} unmountOnExit>
                                    <Stack spacing={0.5}>
                                      {clinicalReasoningResult.supportingEvidence.length ? clinicalReasoningResult.supportingEvidence.map((item, index) => (
                                        <Stack key={`supporting-${index}`} direction="row" spacing={0.75} alignItems="flex-start">
                                          <Checkbox checked disabled size="small" sx={{ mt: -0.4 }} />
                                          <Box sx={{ minWidth: 0 }}>
                                            <Typography variant="body2">{item.text || item.source || "Supporting evidence"}</Typography>
                                            {item.source || item.observationDate ? (
                                              <Typography variant="caption" color="text.secondary">
                                                {[item.source, item.observationDate].filter(Boolean).join(" · ")}
                                              </Typography>
                                            ) : null}
                                          </Box>
                                        </Stack>
                                      )) : (
                                        <Typography variant="body2" color="text.secondary">No supporting evidence returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("missingInformation")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "warning.main" }}>Questions Still To Ask</Typography>
                                      <Chip size="small" color="warning" variant="outlined" label={`${clinicalReasoningResult.missingInformation.length} items`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("missingInformation")} aria-label={`${clinicalReasoningSectionsOpen.missingInformation ? "Collapse" : "Expand"} missing information`}>
                                      {clinicalReasoningSectionsOpen.missingInformation ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.missingInformation} timeout={200} unmountOnExit>
                                    <Stack spacing={0.5}>
                                      {clinicalReasoningResult.missingInformation.length ? clinicalReasoningResult.missingInformation.map((item, index) => {
                                        const key = `${item.name || item.whyItMatters || index}`;
                                        const asked = Boolean(clinicalReasoningAskedMissingInfo[key]);
                                        return (
                                          <Card key={`missing-${key}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "warning.main" }}>
                                            <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                              <Stack direction="row" spacing={0.75} alignItems="flex-start">
                                                <Checkbox checked={asked} onChange={() => toggleClinicalReasoningMissingInfoAsked(key)} size="small" sx={{ mt: -0.4 }} aria-label={`Mark asked for ${item.name || item.whyItMatters || "missing information"}`} />
                                                <Box sx={{ minWidth: 0, flex: 1 }}>
                                                  <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                                    <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                                      {item.name || item.whyItMatters || "Questions still to ask"}
                                                    </Typography>
                                                    <Chip size="small" variant="outlined" label="Mark Asked" />
                                                  </Stack>
                                                  {item.whyItMatters ? <Typography variant="caption" color="text.secondary">{item.whyItMatters}</Typography> : null}
                                                </Box>
                                              </Stack>
                                            </CardContent>
                                          </Card>
                                        );
                                      }) : (
                                        <Typography variant="body2" color="text.secondary">No missing information returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("redFlags")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "error.main" }}>Red flags</Typography>
                                      <Chip size="small" color="error" variant="outlined" label={`${clinicalReasoningResult.redFlags.length} items`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("redFlags")} aria-label={`${clinicalReasoningSectionsOpen.redFlags ? "Collapse" : "Expand"} red flags`}>
                                      {clinicalReasoningSectionsOpen.redFlags ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.redFlags} timeout={200} unmountOnExit>
                                    <Stack spacing={0.75}>
                                      {clinicalReasoningResult.redFlags.length ? clinicalReasoningResult.redFlags.map((flag, index) => (
                                        <Card key={`red-flag-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "error.main" }}>
                                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                            <Stack spacing={0.25}>
                                              <Typography variant="body2" sx={{ fontWeight: 800 }}>{flag.name || flag.reason || "Red flag"}</Typography>
                                              {flag.reason ? <Typography variant="caption" color="text.secondary">{flag.reason}</Typography> : null}
                                              {flag.action ? <Typography variant="caption" color="error.main">{flag.action}</Typography> : null}
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      )) : (
                                        <Typography variant="body2" color="text.secondary">No red flags returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("recommendedTests")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "secondary.main" }}>Recommended investigations</Typography>
                                      <Chip size="small" color="secondary" variant="outlined" label={`${clinicalReasoningResult.recommendedTests.length} items`} />
                                    </Stack>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("recommendedTests")} aria-label={`${clinicalReasoningSectionsOpen.recommendedTests ? "Collapse" : "Expand"} recommended tests`}>
                                      {clinicalReasoningSectionsOpen.recommendedTests ? "Collapse" : "Expand"}
                                    </Button>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.recommendedTests} timeout={200} unmountOnExit>
                                    <Stack spacing={1}>
                                      {clinicalReasoningTestBuckets.alreadyAvailable.length ? (
                                        <Stack spacing={0.5}>
                                          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Already Available</Typography>
                                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                            {clinicalReasoningTestBuckets.alreadyAvailable.map((test, index) => (
                                              <Chip
                                                key={`already-${index}-${test.name || test.reason || "test"}`}
                                                size="small"
                                                variant="outlined"
                                                label={test.name || test.reason || "Recommended test"}
                                                color="default"
                                              />
                                            ))}
                                          </Stack>
                                        </Stack>
                                      ) : null}
                                      {clinicalReasoningTestBuckets.recommended.length ? (
                                        <Stack spacing={0.5}>
                                          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Recommended Investigations</Typography>
                                          <Stack spacing={0.5}>
                                            {clinicalReasoningTestBuckets.recommended.map((test, index) => (
                                              <Card key={`recommended-${index}-${test.name || test.reason || "test"}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "secondary.main" }}>
                                                <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                                  <Stack spacing={0.25}>
                                                    <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                                      <Chip size="small" color="secondary" variant="outlined" label="Recommended" />
                                                      <Typography variant="body2" sx={{ fontWeight: 800 }}>{test.name || "Recommended test"}</Typography>
                                                    </Stack>
                                                    {test.reason ? <Typography variant="caption" color="text.secondary">{test.reason}</Typography> : null}
                                                  </Stack>
                                                </CardContent>
                                              </Card>
                                            ))}
                                          </Stack>
                                        </Stack>
                                      ) : null}
                                      {clinicalReasoningTestBuckets.optional.length ? (
                                        <Stack spacing={0.5}>
                                          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Optional Investigations</Typography>
                                          <Stack spacing={0.5}>
                                            {clinicalReasoningTestBuckets.optional.map((test, index) => (
                                              <Card key={`optional-${index}-${test.name || test.reason || "test"}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                                <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                                  <Stack spacing={0.25}>
                                                    <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                                      <Chip size="small" variant="outlined" label="Optional" />
                                                      <Typography variant="body2" sx={{ fontWeight: 800 }}>{test.name || "Optional test"}</Typography>
                                                    </Stack>
                                                    {test.reason ? <Typography variant="caption" color="text.secondary">{test.reason}</Typography> : null}
                                                  </Stack>
                                                </CardContent>
                                              </Card>
                                            ))}
                                          </Stack>
                                        </Stack>
                                      ) : null}
                                      {!clinicalReasoningTestBuckets.alreadyAvailable.length && !clinicalReasoningTestBuckets.recommended.length && !clinicalReasoningTestBuckets.optional.length ? (
                                        <Typography variant="body2" color="text.secondary">No recommended tests returned.</Typography>
                                      ) : null}
                                    </Stack>
                                  </Collapse>
                                </Stack>

                                <Stack spacing={0.5}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <ScienceRoundedIcon fontSize="small" color="secondary" />
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "secondary.main" }}>Investigation Intelligence</Typography>
                                    </Stack>
                                    <Tooltip title={labTests.length ? "Prepare recommended investigations using AI suggestions." : "Laboratory ordering unavailable."} arrow>
                                      <span>
                                        <Button
                                          size="small"
                                          variant="outlined"
                                          color="primary"
                                          disabled={!labTests.length || !labOrderPreparationRows.length}
                                          onClick={() => openLabOrderPreparation(labOrderPreparationRows)}
                                        >
                                          Prepare AI Lab Order
                                        </Button>
                                      </span>
                                    </Tooltip>
                                  </Stack>
                                  <Box
                                    sx={{
                                      display: "grid",
                                      gridTemplateColumns: { xs: "repeat(2, minmax(0, 1fr))", lg: "repeat(5, minmax(0, 1fr))" },
                                      gap: 0.75,
                                    }}
                                  >
                                    {[
                                      ["Already Available", clinicalReasoningInvestigationIntelligence.summary.alreadyAvailable, "success.main"],
                                      ["Pending", clinicalReasoningInvestigationIntelligence.summary.pending, "warning.main"],
                                      ["Recommended", clinicalReasoningInvestigationIntelligence.summary.recommended, "info.main"],
                                      ["Optional", clinicalReasoningInvestigationIntelligence.summary.consider, "text.secondary"],
                                      ["Duplicate Risk", clinicalReasoningInvestigationIntelligence.summary.duplicateRisk, "warning.dark"],
                                    ].map(([label, value, accent]) => (
                                      <Card key={label} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider" }}>
                                        <CardContent sx={{ p: 0.68, "&:last-child": { pb: 0.68 } }}>
                                          <Stack spacing={0.1} sx={{ minHeight: 40, justifyContent: "center" }}>
                                            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800, lineHeight: 1.1 }}>
                                              {label}
                                            </Typography>
                                            <Typography variant="h6" sx={{ fontWeight: 950, lineHeight: 1, color: accent }}>
                                              {pluralizeInvestigationCount(Number(value))}
                                            </Typography>
                                          </Stack>
                                        </CardContent>
                                      </Card>
                                    ))}
                                  </Box>
                                  <Typography variant="caption" color="text.secondary">
                                    Investigation intelligence is assistive only. Doctor must verify existing reports and clinical need before ordering.
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    No investigation is ordered until the doctor confirms in the lab order workflow.
                                  </Typography>
                                  {!clinicalReasoningInvestigationIntelligence.summary.sourceAvailable ? (
                                    <Alert severity="info" sx={{ py: 0.5 }}>
                                      Patient investigation history is not fully available. Please verify manually.
                                    </Alert>
                                  ) : null}
                                  {clinicalReasoningResult.recommendedTests.length ? (
                                    <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                      <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                                        <Stack spacing={0.7}>
                                    <Box
                                    sx={{
                                      display: "grid",
                                      gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1.1fr) minmax(0, 0.9fr) minmax(0, 1.25fr) minmax(0, 1.35fr) minmax(0, 0.9fr)" },
                                      gap: 0.75,
                                      px: 0.35,
                                    }}
                                  >
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Test</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Status</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Evidence</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>Doctor Note</Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800, textAlign: "right" }}>Action</Typography>
                                  </Box>
                                  <Stack spacing={0.5}>
                                    {clinicalReasoningInvestigationIntelligence.rows.map((row, index) => (
                                      <Box
                                                key={`${row.testName}-${index}`}
                                                id={`investigation-intelligence-${normalizeInvestigationCanonicalKey(row.testName)}`}
                                                sx={{
                                                  display: "grid",
                                                  gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1.1fr) minmax(0, 0.9fr) minmax(0, 1.25fr) minmax(0, 1.35fr) minmax(0, 0.9fr)" },
                                                  gap: 0.75,
                                                  px: 0.85,
                                                  py: 1.2,
                                                  border: 1,
                                                  borderColor: "divider",
                                                  borderLeft: 3,
                                                  borderLeftColor: investigationStatusBorderColor(row.status),
                                                  borderRadius: 1.5,
                                                  bgcolor: "background.paper",
                                                  transition: "background-color 150ms ease",
                                                  "&:hover": { bgcolor: "action.hover" },
                                                  ...(clinicalReasoningInvestigationHighlightKey && normalizeInvestigationCanonicalKey(clinicalReasoningInvestigationHighlightKey) === normalizeInvestigationCanonicalKey(row.testName) ? {
                                                    bgcolor: "action.hover",
                                                    outline: 2,
                                                    outlineColor: "primary.light",
                                                    outlineStyle: "solid",
                                                  } : {}),
                                                }}
                                              >
                                                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                                                  <Stack direction="row" spacing={0.6} alignItems="flex-start" sx={{ minWidth: 0 }}>
                                                    <Box sx={{ mt: 0.1, color: "text.secondary", display: "grid", placeItems: "center", flexShrink: 0 }}>
                                                      {getInvestigationIcon(row.testName)}
                                                    </Box>
                                                    <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.3, fontSize: 15, minWidth: 0 }}>
                                                      {row.testName}
                                                    </Typography>
                                                  </Stack>
                                                </Stack>
                                                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                                                  <Chip
                                                    size="small"
                                                    color={investigationStatusColor(row.status)}
                                                    variant="outlined"
                                                    icon={investigationStatusIcon(row.status)}
                                                    label={getVisibleInvestigationStatusLabel(row.status)}
                                                    aria-label={`${getVisibleInvestigationStatusLabel(row.status)} status`}
                                                    sx={{ width: "fit-content", fontWeight: 700 }}
                                                  />
                                                </Stack>
                                                <Stack spacing={0.1} sx={{ minWidth: 0 }}>
                                                  {investigationEvidenceLines(row).map((line, lineIndex) => (
                                                    <Typography key={`${row.testName}-evidence-${lineIndex}-${line}`} variant="body2" color="text.secondary" sx={{ minWidth: 0, lineHeight: 1.25, fontSize: 13 }}>
                                                      {line}
                                                    </Typography>
                                                  ))}
                                                </Stack>
                                                <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                                  {row.duplicateRisk ? (
                                                    <Chip
                                                      size="small"
                                                      color="warning"
                                                      variant="outlined"
                                                      label="Duplicate Risk"
                                                      sx={{ width: "fit-content", fontWeight: 700 }}
                                                    />
                                                  ) : null}
                                                  <Tooltip title={investigationDoctorNote(row)} arrow>
                                                    <Typography variant="body2" color="text.secondary" sx={{ minWidth: 0, lineHeight: 1.25, fontSize: 13 }}>
                                                      {compactText(investigationDoctorNote(row), 85)}
                                                    </Typography>
                                                  </Tooltip>
                                                </Stack>
                                                <Stack spacing={0.35} sx={{ minWidth: 0, alignItems: "flex-end" }}>
                                                  {row.status === "Unknown" ? (
                                                    <Tooltip title="Insufficient data. Review manually." arrow>
                                                      <span>
                                                        <Button size="small" variant="outlined" disabled>
                                                          Review Manually
                                                        </Button>
                                                      </span>
                                                    </Tooltip>
                                                  ) : row.status === "Pending" ? (
                                                    row.matchedOrderId ? (
                                                      <Tooltip title="Continue the pending laboratory order." arrow>
                                                        <Button
                                                          size="small"
                                                          variant="outlined"
                                                          color="warning"
                                                          onClick={() => reviewInvestigationResult(row)}
                                                        >
                                                          Continue Order
                                                        </Button>
                                                      </Tooltip>
                                                    ) : (
                                                      <Tooltip title="Pending order exists. Review in Laboratory module." arrow>
                                                        <span>
                                                          <Button size="small" variant="outlined" color="warning" disabled>
                                                            Continue Order
                                                          </Button>
                                                        </span>
                                                      </Tooltip>
                                                    )
                                                  ) : row.status === "Already Available" || row.status === "Recently Completed" ? (
                                                    <Tooltip title="Review the existing result before repeating this investigation." arrow>
                                                      <Button size="small" variant="outlined" color="success" onClick={() => reviewInvestigationResult(row)}>
                                                        Review Result
                                                      </Button>
                                                    </Tooltip>
                                                  ) : row.duplicateRisk ? (
                                                    <Tooltip title="Existing result or pending order found. Please review before ordering." arrow>
                                                      <Button size="small" variant="outlined" color="warning" onClick={() => reviewInvestigationResult(row)}>
                                                        Review Before Ordering
                                                      </Button>
                                                    </Tooltip>
                                                  ) : (
                                                    <Tooltip title="Prepare this investigation in the laboratory workflow." arrow>
                                                      <Button size="small" variant="outlined" color="primary" onClick={() => prepareInvestigationOrder(row)}>
                                                        Prepare Order
                                                      </Button>
                                                    </Tooltip>
                                                  )}
                                                </Stack>
                                              </Box>
                                            ))}
                                          </Stack>
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  ) : (
                                    <Alert severity="info" sx={{ py: 0.5 }}>
                                      No AI investigation suggestions were returned.
                                      <br />
                                      Review the consultation and use the existing Investigations section if a test is clinically required.
                                    </Alert>
                                  )}
                                </Stack>

                                <Stack spacing={0.5} ref={setClinicalReasoningSectionRef("safetyNotes")}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      <Typography variant="subtitle2" sx={{ fontWeight: 950, color: "info.main" }}>Clinical Safety Advice</Typography>
                                      <Chip size="small" color="info" variant="outlined" label={`${clinicalReasoningResult.safetyNotes.length} items`} />
                                    </Stack>
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                      <Button type="button" size="small" variant="text" onClick={() => revealAdviceSection(true)} aria-label="Open Advice">
                                        Open Advice
                                      </Button>
                                      <Button type="button" size="small" variant="text" onClick={() => toggleClinicalReasoningSection("safetyNotes")} aria-label={`${clinicalReasoningSectionsOpen.safetyNotes ? "Collapse" : "Expand"} safety notes`}>
                                        {clinicalReasoningSectionsOpen.safetyNotes ? "Collapse" : "Expand"}
                                      </Button>
                                    </Stack>
                                  </Stack>
                                  <Collapse in={clinicalReasoningSectionsOpen.safetyNotes} timeout={200} unmountOnExit>
                                    <Stack spacing={0.75}>
                                      {clinicalReasoningResult.safetyNotes.length ? clinicalReasoningResult.safetyNotes.map((note, index) => (
                                        <Card key={`safety-note-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "divider", borderLeft: 3, borderLeftColor: "info.main" }}>
                                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                            <Stack spacing={0.3}>
                                              <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                                                <Box sx={{ minWidth: 0 }}>
                                                  <Typography variant="body2" sx={{ fontWeight: 800 }}>{note.message || note.action || "Safety note"}</Typography>
                                                  {note.rationale ? <Typography variant="caption" color="text.secondary">{note.rationale}</Typography> : null}
                                                </Box>
                                                <Tooltip title="Append this safety guidance to advice">
                                                  <span>
                                                    <Button type="button" size="small" variant="outlined" onClick={() => addClinicalReasoningSafetyNote(note.message || note.action || "")} disabled={!note.message && !note.action} aria-label={`Add safety note ${note.message || note.action || "item"} to advice`}>
                                                      Add to Advice
                                                    </Button>
                                                  </span>
                                                </Tooltip>
                                              </Stack>
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      )) : (
                                        <Typography variant="body2" color="text.secondary">No safety notes returned.</Typography>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>
                              </Stack>
                            </Box>

                            {isClinicalReasoningAdminView ? (
                              <Accordion
                                expanded={clinicalReasoningSectionsOpen.debug}
                                onChange={(_, value) => setClinicalReasoningSectionsOpen((current) => ({ ...current, debug: value }))}
                                disableGutters
                                elevation={0}
                                sx={{ border: 1, borderColor: "divider", borderRadius: 2, "&:before": { display: "none" } }}
                              >
                                <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} aria-controls="clinical-reasoning-debug-content" id="clinical-reasoning-debug-header">
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Debug / Admin</Typography>
                                </AccordionSummary>
                                <AccordionDetails>
                                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.provider) ? <Chip size="small" label={`Provider: ${clinicalReasoningResult.metadata?.provider}`} /> : null}
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.model) ? <Chip size="small" label={`Model: ${clinicalReasoningResult.metadata?.model}`} /> : null}
                                    {clinicalReasoningResult.metadata?.latencyMs != null ? <Chip size="small" label={`Response Time: ${clinicalReasoningResult.metadata.latencyMs} ms`} /> : null}
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.generatedAt) ? <Chip size="small" label={`Generated At: ${compactDateTime(clinicalReasoningResult.generatedAt)}`} /> : null}
                                    <Chip size="small" label={`Fallback Used: ${clinicalReasoningResult.metadata?.fallbackUsed ? "Yes" : "No"}`} />
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.promptVersion) ? <Chip size="small" label={`Prompt Version: ${clinicalReasoningResult.metadata?.promptVersion}`} /> : null}
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.parseStatus) ? <Chip size="small" label={`Parse Status: ${clinicalReasoningResult.metadata?.parseStatus}`} /> : null}
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.contextVersion) ? <Chip size="small" label={`Memory Version: ${clinicalReasoningResult.metadata?.contextVersion}`} /> : null}
                                    {formatClinicalReasoningMetaValue(clinicalReasoningResult.metadata?.reasoningEngineVersion) ? <Chip size="small" label={`Reasoning Version: ${clinicalReasoningResult.metadata?.reasoningEngineVersion}`} /> : null}
                                  </Stack>
                                </AccordionDetails>
                              </Accordion>
                            ) : null}

                            <Divider />
                          </Stack>
                        ) : (
                          <Stack spacing={1}>
                            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                              <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                                <Stack spacing={1}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 950 }}>AI Summary</Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    Clinical reasoning has not been generated.
                                  </Typography>
                                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                    <Chip size="small" color="success" variant="outlined" label="Primary diagnosis" />
                                    <Chip size="small" color="success" variant="outlined" label="Differential diagnoses" />
                                    <Chip size="small" color="info" variant="outlined" label="Supporting evidence" />
                                    <Chip size="small" color="warning" variant="outlined" label="Questions still to ask" />
                                    <Chip size="small" color="error" variant="outlined" label="Red flags" />
                                    <Chip size="small" color="secondary" variant="outlined" label="Recommended investigations" />
                                    <Chip size="small" color="info" variant="outlined" label="Safety guidance" />
                                  </Stack>
                                  <Typography variant="body2" color="text.secondary">
                                    Generate reasoning to receive:
                                  </Typography>
                                  <Stack spacing={0.35}>
                                    {[
                                      "Primary diagnosis",
                                      "Differential diagnoses",
                                      "Supporting evidence",
                                      "Questions still to ask",
                                      "Red flags",
                                      "Recommended investigations",
                                      "Safety guidance",
                                    ].map((item) => (
                                      <Typography key={item} variant="body2" color="text.secondary">
                                        ✓ {item}
                                      </Typography>
                                    ))}
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          </Stack>
                        )}
                      </Stack>
                  </CardContent>
                  </Card>
                    </>
                  ) : null}
                </Stack>
                </SectionCard>

              <SectionCard
                id="notes"
                title="Clinical Notes / SOAP"
                subtitle="Subjective, objective, assessment, and plan"
                icon={<DescriptionRoundedIcon fontSize="small" />}
              expanded={expanded.notes}
              onToggle={toggleSection}
              primaryAction={(
                soapHasPersistedRecord ? (
                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("soap")}>
                      Generate New SOAP Draft
                    </Button>
                    <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={focusConsultationSoapEditor}>
                      Edit SOAP
                    </Button>
                    <Button type="button" size="small" variant="outlined" disabled={!serializeConsultationSoapForm(consultationSoapFormRef.current).trim()} onClick={() => void copyConsultationSoapDraft()}>
                      Copy
                    </Button>
                    <Button type="button" size="small" variant="outlined" disabled>
                      Version History
                    </Button>
                  </Stack>
                ) : (
                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("soap")}>
                      Draft SOAP
                    </Button>
                    <Button type="button" size="small" variant="outlined" disabled={readOnly || !soapDirty} onClick={() => void saveConsultationSoapDraft(true)}>
                      Save SOAP
                    </Button>
                  </Stack>
                )
                )}
              >
                <Stack spacing={1}>
                  {soapStatusLabel ? (
                    <Alert severity={soapStatusSeverity} variant="outlined" sx={{ py: 0.5 }}>
                      <Stack spacing={0.2}>
                        <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
                          SOAP status: {soapStatusLabel}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {consultationSoap?.status === "ACCEPTED"
                            ? (soapStale ? "Clinical information has changed since this SOAP note was accepted." : "Clinical information matches the accepted SOAP note.")
                            : "Manual SOAP changes are stored independently."}
                        </Typography>
                      </Stack>
                    </Alert>
                  ) : null}
                  {clinicalAiDrafts.soap.generatedAt || clinicalAiDrafts.soap.error || clinicalAiDrafts.soap.status !== "DRAFTED" || clinicalAiDrafts.soap.draftText.trim() ? renderClinicalAiDraftCard("soap") : null}
                  {!(consultationSoapForm.subjective.trim() || consultationSoapForm.objective.trim() || consultationSoapForm.assessment.trim() || consultationSoapForm.plan.trim()) ? (
                    <Typography variant="caption" color="text.secondary">
                      Generate SOAP using AIVA or complete manually.
                    </Typography>
                  ) : null}
                  <Grid container spacing={0.75}>
                    <Grid size={{ xs: 12, md: 6 }}><TextField inputRef={soapSubjectiveInputRef} fullWidth size="small" label="Subjective" value={consultationSoapForm.subjective} onChange={(e) => setConsultationSoapForm((c) => ({ ...c, subjective: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Objective" value={consultationSoapForm.objective} onChange={(e) => setConsultationSoapForm((c) => ({ ...c, objective: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Assessment" value={consultationSoapForm.assessment} onChange={(e) => setConsultationSoapForm((c) => ({ ...c, assessment: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Plan" value={consultationSoapForm.plan} onChange={(e) => setConsultationSoapForm((c) => ({ ...c, plan: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                  </Grid>
                </Stack>
              </SectionCard>
                </Stack>
              </Grid>

              <Grid
                size={{ xs: 12, xl: 4 }}
                sx={{
                  minWidth: 0,
                  minHeight: 0,
                  position: { xl: "sticky" },
                  top: { xl: 16 },
                  alignSelf: { xl: "flex-start" },
                }}
              >
                <Stack
                  spacing={0.85}
                  ref={labOrderWorkflowRef}
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 0.85,
                    minWidth: 0,
                    minHeight: 0,
                    overflowX: "hidden",
                    overflowY: { xl: "auto" },
                    pr: 0,
                    maxHeight: { xl: "calc(100vh - 32px)" },
                  }}
                >
                  <Box ref={patientIntelligenceRef}>
                  <PatientIntelligenceCard
                    context={clinicalContext}
                    loading={clinicalContextLoading}
                    error={clinicalContextError}
                    highlightLabLabel={clinicalReasoningInvestigationHighlightKey}
                    onViewSourceDocument={(documentId) => void openClinicalDocumentById(documentId)}
                  />
                  </Box>
                  <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto", minHeight: "auto" }}>
                    <CardContent sx={{ p: 0.95, "&:last-child": { pb: 0.95 } }}>
                        <Stack spacing={0.85}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                            <Box>
                              <Stack direction="row" spacing={0.75} alignItems="center">
                                <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>AI Assist</Typography>
                              </Stack>
                              <Typography variant="caption" color="text.secondary">Clinical chat and contextual AI tools for this consultation.</Typography>
                            </Box>
                            {aiAssistantEnabled ? <Chip size="small" color="success" variant="outlined" label="AI ready" /> : null}
                          </Stack>
                        {aiAssistantEnabled ? (
                          <>
                        <Alert severity="info" sx={{ py: 0.4 }}>
                          {AI_SAFETY_NOTE}
                        </Alert>
                        <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper", overflow: "visible" }}>
                          <Stack spacing={0.65}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinical Chat</Typography>
                            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "flex-end" }}>
                              <TextField
                                size="small"
                                fullWidth
                                placeholder="Ask anything about this consultation..."
                                value={aivaClinicalQuestion}
                                onChange={(e) => setAivaClinicalQuestion(e.target.value)}
                                onKeyDown={(e) => {
                                  if (e.key === "Enter" && (!e.shiftKey || e.ctrlKey || e.metaKey)) {
                                    e.preventDefault();
                                    void runAskAiva();
                                  }
                                }}
                                multiline
                                minRows={1}
                                disabled={aiBusy || !aiAssistantEnabled}
                                sx={{ flex: 1, minWidth: 0 }}
                              />
                              <Button
                                type="button"
                                size="small"
                                variant="contained"
                                disabled={!aiAssistantEnabled || !canAskAiva}
                                onClick={() => void runAskAiva()}
                                sx={{ flexShrink: 0, minWidth: 72 }}
                              >
                                Ask
                              </Button>
                            </Stack>
                          </Stack>
                        </Box>
                        <Box
                          sx={{
                            display: "grid",
                            gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" },
                            gap: 0.75,
                            overflow: "visible",
                          }}
                        >
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<PsychologyRoundedIcon fontSize="small" />} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Suggest diagnosis
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<WarningAmberRoundedIcon fontSize="small" />} sx={{ color: "warning.main", borderColor: "warning.light" }} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Red flags
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<ScienceRoundedIcon fontSize="small" />} sx={{ color: "secondary.main", borderColor: "secondary.light" }} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Recommended tests
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<HealthAndSafetyRoundedIcon fontSize="small" />} sx={{ color: "error.main", borderColor: "error.light" }} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void applyAiPrescriptionTemplate()}>
                            Drug interaction
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<TipsAndUpdatesRoundedIcon fontSize="small" />} sx={{ color: "success.main", borderColor: "success.light" }} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void runAiAction("instructions")}>
                            Patient advice
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<DescriptionRoundedIcon fontSize="small" />} sx={{ color: "info.main", borderColor: "info.light" }} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void runAiAction("notes")}>
                            SOAP notes
                          </Button>
                        </Box>
                        {aiStatusMessage ? <Alert severity="warning">{aiStatusMessage}</Alert> : null}
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={1}>
                              <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                <Stack direction="row" spacing={0.75} alignItems="center">
                                  <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinical Chat</Typography>
                                </Stack>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {aiBusy && aiActiveAction ? <Chip size="small" color="warning" variant="outlined" label={`Loading ${aiAssistActionLabel(aiActiveAction)}…`} /> : null}
                                  <Chip size="small" variant="outlined" label={`${aivaChatMessages.length} message${aivaChatMessages.length === 1 ? "" : "s"}`} />
                                </Stack>
                              </Stack>
                              {aivaChatMessages.length ? (
                                <Stack spacing={0.75} sx={{ maxHeight: 220, overflow: "auto" }}>
                                  {aivaChatMessages.map((message) => (
                                    <Box
                                      key={message.id}
                                      sx={{
                                        display: "flex",
                                        justifyContent: message.role === "DOCTOR" ? "flex-end" : "flex-start",
                                      }}
                                    >
                                      <Box
                                        sx={{
                                          maxWidth: "90%",
                                          p: 0.9,
                                          borderRadius: 2,
                                          border: 1,
                                          borderColor: message.role === "DOCTOR" ? "primary.light" : "divider",
                                          bgcolor: message.role === "DOCTOR" ? "primary.50" : "background.paper",
                                        }}
                                      >
                                        <Stack spacing={0.4}>
                                          <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                            <Typography variant="caption" sx={{ fontWeight: 900 }}>{message.role === "DOCTOR" ? "Doctor" : "AIVA"}</Typography>
                                            <Typography variant="caption" color="text.secondary">{compactDateTime(message.createdAt)}</Typography>
                                          </Stack>
                                          <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.45 }}>{message.text}</Typography>
                                          {message.role === "AIVA" && message.text.trim() ? (
                                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                              <Button type="button" size="small" variant="outlined" onClick={() => void navigator.clipboard.writeText(message.text)}>
                                                Copy
                                              </Button>
                                              <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={() => setConsultationForm((current) => ({ ...current, clinicalNotes: appendTokenLine(current.clinicalNotes, message.text) }))}>
                                                Add to SOAP
                                              </Button>
                                              <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={() => appendAdviceAndReveal(message.text)}>
                                                Add to Advice
                                              </Button>
                                            </Stack>
                                          ) : null}
                                        </Stack>
                                      </Box>
                                    </Box>
                                  ))}
                                </Stack>
                              ) : (
                                <Box sx={{ p: 1.2, borderRadius: 2, bgcolor: "action.hover" }}>
                                  <Stack spacing={0.75}>
                                  <Typography variant="body2" color="text.secondary">
                                      Ask anything about this consultation...
                                    </Typography>
                                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                      {AIVA_QUICK_PROMPTS.map((prompt) => (
                                        <Chip
                                          key={prompt}
                                          size="small"
                                          variant="outlined"
                                          clickable={!aiBusy && !aivaQuestionSubmitting}
                                          label={prompt}
                                          onClick={() => setAivaClinicalQuestion(prompt)}
                                        />
                                      ))}
                                    </Stack>
                                  </Stack>
                                </Box>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                          </>
                        ) : aiUnavailableCard}
                      </Stack>
                    </CardContent>
                  </Card>

                  <SectionCard
                    id="followup"
                    title="Vitals & Follow-up"
                    subtitle="Capture essentials, set next visit"
                    icon={<MonitorHeartRoundedIcon fontSize="small" />}
                    expanded={expanded.followup}
                    onToggle={toggleSection}
                    primaryAction={(
                      <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("followUp")}>
                        Suggest
                      </Button>
                    )}
                  >
                    <Stack spacing={1} sx={{ overflow: "visible", minHeight: 0 }}>
                      {clinicalAiDrafts.followUp.generatedAt || clinicalAiDrafts.followUp.error || clinicalAiDrafts.followUp.status !== "DRAFTED" || clinicalAiDrafts.followUp.draftText.trim() ? renderClinicalAiDraftCard("followUp") : null}
                      {!lastConsultation && !intakeVitals ? (
                        <Typography variant="caption" color="text.secondary">
                          No previous vitals available.
                        </Typography>
                      ) : null}
                      <Box
                        sx={{
                          display: "grid",
                          gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))", xl: "repeat(2, minmax(0, 1fr))" },
                          gap: 0.75,
                          alignItems: "stretch",
                          minHeight: 0,
                        }}
                      >
                        {[
                          {
                            label: "BP Sys",
                            icon: <FavoriteRoundedIcon fontSize="small" color="error" />,
                            value: consultationForm.bloodPressureSystolic,
                            todayValue: formatContextValue(latestVitals.bloodPressureSystolic),
                            lastValue: formatContextValue(previousVitals.bloodPressureSystolic),
                            trend: numericTrendLabel(latestVitals.bloodPressureSystolic, previousVitals.bloodPressureSystolic),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, bloodPressureSystolic: value })),
                          },
                          {
                            label: "BP Dia",
                            icon: <FavoriteRoundedIcon fontSize="small" color="error" />,
                            value: consultationForm.bloodPressureDiastolic,
                            todayValue: formatContextValue(latestVitals.bloodPressureDiastolic),
                            lastValue: formatContextValue(previousVitals.bloodPressureDiastolic),
                            trend: numericTrendLabel(latestVitals.bloodPressureDiastolic, previousVitals.bloodPressureDiastolic),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, bloodPressureDiastolic: value })),
                          },
                          {
                            label: "Pulse",
                            icon: <MonitorHeartRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.pulseRate,
                            todayValue: formatContextValue(latestVitals.pulseRate),
                            lastValue: formatContextValue(previousVitals.pulseRate),
                            trend: numericTrendLabel(latestVitals.pulseRate, previousVitals.pulseRate),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, pulseRate: value })),
                          },
                          {
                            label: "Temp",
                            icon: <EventRoundedIcon fontSize="small" color="warning" />,
                            value: consultationForm.temperature,
                            todayValue: formatContextValue(latestVitals.temperature),
                            lastValue: formatContextValue(previousVitals.temperature),
                            trend: numericTrendLabel(latestVitals.temperature, previousVitals.temperature),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, temperature: value })),
                          },
                          {
                            label: "SpO2",
                            icon: <HealthAndSafetyRoundedIcon fontSize="small" color="success" />,
                            value: consultationForm.spo2,
                            todayValue: formatContextValue(latestVitals.spo2),
                            lastValue: formatContextValue(previousVitals.spo2),
                            trend: numericTrendLabel(latestVitals.spo2, previousVitals.spo2),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, spo2: value })),
                          },
                          {
                            label: "Resp. Rate",
                            icon: <ScienceRoundedIcon fontSize="small" color="secondary" />,
                            value: consultationForm.respiratoryRate,
                            todayValue: formatContextValue(latestVitals?.respiratoryRate ?? null),
                            lastValue: formatContextValue(previousVitals?.respiratoryRate ?? null),
                            trend: "No previous",
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, respiratoryRate: value })),
                          },
                          {
                            label: "Weight (kg)",
                            icon: <PersonRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.weightKg,
                            todayValue: formatContextValue(latestVitals.weightKg),
                            lastValue: formatContextValue(previousVitals.weightKg),
                            trend: numericTrendLabel(latestVitals.weightKg, previousVitals.weightKg),
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, weightKg: value })),
                          },
                          {
                            label: "Height (cm)",
                            icon: <BadgeRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.heightCm,
                            todayValue: formatContextValue(consultationForm.heightCm.trim() ? Number(consultationForm.heightCm) : intakeVitals?.heightCm ?? null),
                            lastValue: formatContextValue(lastConsultation?.heightCm ?? null),
                            trend: "No previous",
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, heightCm: value })),
                          },
                        ].map((item) => (
                          <Card key={item.label} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25, height: "auto", minHeight: "auto", overflow: "visible" }}>
                            <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                              <Stack spacing={0.55} sx={{ height: "auto", minHeight: 0, overflow: "visible" }}>
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                  {item.icon}
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900, lineHeight: 1.05, fontSize: 12.5 }}>{item.label}</Typography>
                                </Stack>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Chip size="small" variant="outlined" label={`Current ${item.todayValue || "No value"}`} />
                                  <Chip size="small" variant="outlined" label={item.lastValue ? `Previous ${item.lastValue}` : "No previous value"} />
                                  <Chip size="small" variant="outlined" label={item.trend === "No previous" ? "No previous" : `Trend ${item.trend}`} />
                                </Stack>
                                <TextField
                                  size="small"
                                  fullWidth
                                  label={item.label}
                                  value={item.value}
                                  onChange={(e) => item.onChange(e.target.value)}
                                  disabled={readOnly}
                                  sx={{ flex: 1, minWidth: 0 }}
                                />
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Box>

                      <Box
                        sx={{
                          display: "grid",
                          gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))" },
                          gap: 0.75,
                          alignItems: "stretch",
                          minHeight: 0,
                        }}
                      >
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25, overflow: "visible", height: "auto", minHeight: "auto" }}>
                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                            <Stack spacing={0.55}>
                              <Stack direction="row" spacing={0.5} alignItems="center">
                                <EventRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900, lineHeight: 1.05, fontSize: 12.5 }}>Temperature unit</Typography>
                              </Stack>
                              <FormControl fullWidth size="small">
                                <InputLabel id="temp-unit-label">Unit</InputLabel>
                                <Select labelId="temp-unit-label" label="Unit" value={consultationForm.temperatureUnit} onChange={(e) => setConsultationForm((c) => ({ ...c, temperatureUnit: String(e.target.value) as ConsultationFormState["temperatureUnit"] }))}>
                                  <MenuItem value="">-</MenuItem>
                                  <MenuItem value="CELSIUS">C</MenuItem>
                                  <MenuItem value="FAHRENHEIT">F</MenuItem>
                                </Select>
                              </FormControl>
                            </Stack>
                          </CardContent>
                        </Card>

                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25, overflow: "visible", height: "auto", minHeight: "auto" }}>
                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                            <Stack spacing={0.55}>
                              <Stack direction="row" spacing={0.5} alignItems="center">
                                <MonitorHeartRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900, lineHeight: 1.05, fontSize: 12.5 }}>BMI</Typography>
                              </Stack>
                              <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                <Chip size="small" label={`Current ${currentBmi ? currentBmi.toFixed(1) : "No value"}`} variant="outlined" />
                                <Chip size="small" label={currentBmiCategory || "No BMI category"} variant="outlined" />
                                <Chip size="small" label={lastBmi ? `Previous ${lastBmi.toFixed(1)}` : "No previous value"} variant="outlined" />
                                <Chip size="small" label={lastBmiCategory || "No previous BMI"} variant="outlined" />
                                <Chip size="small" label={numericTrendLabel(currentBmi, lastBmi) === "No previous" ? "No previous" : `Trend ${numericTrendLabel(currentBmi, lastBmi)}`} variant="outlined" />
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Box>

                      <Box
                        sx={{
                          display: "grid",
                          gridTemplateColumns: "minmax(0, 1fr)",
                          gap: 1,
                          minHeight: 0,
                        }}
                      >
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25, overflow: "visible", height: "auto", minHeight: "auto" }}>
                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                            <Stack spacing={0.55}>
                              <Stack direction="row" spacing={0.5} alignItems="center">
                                <EventRoundedIcon fontSize="small" color="secondary" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900, lineHeight: 1.05, fontSize: 12.5 }}>Follow-up date</Typography>
                              </Stack>
                              <TextField size="small" fullWidth label="Follow-up date" type="date" value={consultationForm.followUpDate} disabled={readOnly} onChange={(e) => setConsultationForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
                              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.05 }}>
                                Next visit
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Box>

                      <Box sx={{ overflow: "visible" }}>
                        <QuickChipGroup disabled={readOnly} chips={FOLLOWUP_CHIPS} color="secondary" onPick={applyFollowUp} />
                      </Box>
                    </Stack>
                  </SectionCard>

                  <Box ref={adviceSectionRef}>
                    <SectionCard
                      id="advice"
                      title="Advice"
                      subtitle="Reusable advice shortcuts"
                      icon={<LightbulbRoundedIcon fontSize="small" />}
                      expanded={expanded.advice}
                      onToggle={toggleSection}
                      primaryAction={(
                        <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("advice")}>
                          Draft Advice
                        </Button>
                      )}
                    >
                      <Stack spacing={1.25} sx={{ overflow: "visible", minHeight: 0 }}>
                        {clinicalAiDrafts.advice.generatedAt || clinicalAiDrafts.advice.error || clinicalAiDrafts.advice.status !== "DRAFTED" || clinicalAiDrafts.advice.draftText.trim() ? renderClinicalAiDraftCard("advice") : null}
                        <Box sx={{ overflow: "visible", minHeight: 0 }}>
                          <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 0.4 }}>
                            Frequently Used Advice
                          </Typography>
                          <QuickChipGroup disabled={readOnly} chips={ADVICE_CHIPS} onPick={(chip) => setConsultationForm((c) => ({ ...c, advice: appendTokenLine(c.advice, chip) }))} />
                        </Box>
                        <TextField size="small" fullWidth label="Advice" inputRef={adviceInputRef} value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                      </Stack>
                    </SectionCard>
                  </Box>

                  <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto", minHeight: "auto" }}>
                    <CardContent sx={{ p: 0.95, "&:last-child": { pb: 0.95 } }}>
                      <Stack spacing={0.8}>
                        <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between">
                          <Box>
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <InsightsRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Report trends</Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.15 }}>
                              {clinicalContext?.longitudinalClinicalContext?.labTrends?.length
                                ? "Structured longitudinal trends from clinical context"
                                : clinicalContext?.labIntelligence.previousTrends?.length
                                  ? "Compare recent lab reports and findings"
                                  : "No comparable reports yet"}
                            </Typography>
                          </Box>
                          <Button type="button" size="small" variant="outlined" disabled={!labReportPreview.length} startIcon={<TrendingUpRoundedIcon fontSize="small" />}>
                            Compare
                          </Button>
                        </Stack>
                        <StructuredTrendSummary
                          structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
                          legacyTrends={clinicalContext?.labIntelligence.previousTrends || []}
                          latestReport={clinicalContext?.labIntelligence.latestLabReport || null}
                          summary={clinicalContext?.aiSummary || null}
                          fallbackMode="legacyOnly"
                          emptyStateLabel="No comparable reports yet"
                          emptyStateDetail="Structured comparison is available when previous numeric results exist."
                        />
                      </Stack>
                    </CardContent>
                  </Card>

                  <Box sx={{ overflow: "visible" }}>
                    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1, mb: 0.75 }}>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                        <Box sx={{ display: "grid", placeItems: "center", width: 30, height: 30, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main", flexShrink: 0 }}>
                          <HistoryRoundedIcon fontSize="small" />
                        </Box>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.15 }}>History at a glance</Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.1 }}>
                            {activeTimeline.length ? `${activeTimeline.length} previous consultation${activeTimeline.length === 1 ? "" : "s"}` : "No previous history"}
                          </Typography>
                        </Box>
                      </Box>
                      <Button type="button" size="small" variant="text" onClick={() => {
                        setExpanded((current) => ({ ...current, history: !current.history }));
                      }}>
                        {expanded.history ? "Hide" : "View"}
                      </Button>
                    </Box>
                    <Collapse in={expanded.history} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                    <Stack spacing={1}>
                      {!activeTimeline.length ? (
                        <Alert severity="info" sx={{ py: 0.5 }}>No previous clinical history recorded.</Alert>
                      ) : (
                        <Stack spacing={0.75}>
                          {activeTimeline.slice(0, 6).map((item) => (
                            <Card
                              key={item.id}
                              variant="outlined"
                              sx={{
                                boxShadow: "none",
                                cursor: item.itemType === "DOCUMENT" || item.consultationId || item.prescriptionId ? "pointer" : "default",
                                borderRadius: 1.5,
                                transition: "transform 120ms ease, box-shadow 120ms ease",
                                "&:hover": { boxShadow: 1, transform: "translateY(-1px)" },
                              }}
                              onClick={() => {
                                if (item.itemType === "DOCUMENT" && item.documentId) {
                                  const document = clinicalDocuments.find((row) => row.id === item.documentId);
                                  if (document) void openClinicalDocument(document);
                                  return;
                                }
                                if (item.consultationId && item.consultationId !== consultation.id) {
                                  navigate(`/consultations/${item.consultationId}`);
                                }
                              }}
                            >
                              <CardContent sx={{ p: 1 }}>
                                <Stack direction="row" spacing={1} alignItems="flex-start">
                                  <Box sx={{ width: 28, height: 28, borderRadius: "50%", display: "grid", placeItems: "center", bgcolor: "primary.50", color: "primary.main", flexShrink: 0 }}>
                                    {item.itemType === "PRESCRIPTION" ? <MedicationRoundedIcon fontSize="small" /> : item.itemType === "LAB_ORDER" || item.itemType === "DOCUMENT" ? <ScienceRoundedIcon fontSize="small" /> : item.itemType === "INVESTIGATION" ? <BiotechRoundedIcon fontSize="small" /> : <HistoryRoundedIcon fontSize="small" />}
                                  </Box>
                                  <Stack spacing={0.25} sx={{ minWidth: 0, flex: 1 }}>
                                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                                      <Chip size="small" label={timelineTypeLabel(item.itemType)} color={timelineColor(item.itemType)} variant="outlined" sx={{ height: 22 }} />
                                      {item.consultationId === consultation.id ? <Chip size="small" color="primary" label="Current" sx={{ height: 22 }} /> : null}
                                    </Stack>
                                    <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }} noWrap>
                                      {item.title}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {compactDateTime(item.occurredAt)}{item.subtitle ? ` • ${compactText(item.subtitle, 42)}` : ""}
                                    </Typography>
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          ))}
                        </Stack>
                      )}
                    </Stack>
                    </Collapse>
                  </Box>
                </Stack>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === 1 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12 }}>
            <WorkflowStrip text="Search Medicine → Prescribe → Safety Review → Preview → Finalize" />
          </Grid>
          <Grid
              size={{ xs: 12, lg: 4 }}
              sx={{
                minWidth: 0,
                minHeight: 0,
                position: { xl: "sticky" },
                top: { xl: 160 },
                alignSelf: { xl: "flex-start" },
              }}
            >
              <Stack spacing={1.25} sx={{ minWidth: 0 }}>
                <Card variant="outlined" sx={{ boxShadow: "none" }}>
                  <CardContent sx={{ p: 1.5 }}>
                    <Stack spacing={1.25}>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <MedicationRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Medicine Search</Typography>
                    </Stack>
                    {medicineCatalogWarning ? <Alert severity="warning">{medicineCatalogWarning}</Alert> : null}
                    <TextField size="small" placeholder="Search medicine or strength" value={medicineSearch} disabled={prescriptionReadOnly} onChange={(e) => setMedicineSearch(e.target.value)} />
                    <Stack spacing={1}>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Frequently Used</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {groupedMedicineCatalog.frequent.length ? groupedMedicineCatalog.frequent.map((medicine) => (
                            <Chip key={medicine.id} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={`${medicine.medicineName}${medicine.strength ? ` ${medicine.strength}` : ""}`} onClick={() => addMedicineFromCatalog(medicine)} variant="outlined" />
                          )) : (
                            <Typography variant="caption" color="text.secondary">No frequently used medicines.</Typography>
                          )}
                        </Stack>
                      </Stack>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Recent</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {previousMedicineGroups.recent.length ? previousMedicineGroups.recent.map((name) => (
                            <Chip key={name} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={name} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} />
                          )) : (
                            <Typography variant="caption" color="text.secondary">No recent medicines.</Typography>
                          )}
                        </Stack>
                      </Stack>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Favorites</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {groupedMedicineCatalog.favorite.length ? groupedMedicineCatalog.favorite.map((name) => (
                            <Chip key={name} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={name} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} variant="outlined" />
                          )) : (
                            <Typography variant="caption" color="text.secondary">No favorite medicines yet.</Typography>
                          )}
                        </Stack>
                      </Stack>
                      <Divider />
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">All Medicines</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {groupedMedicineCatalog.allMedicines.length ? groupedMedicineCatalog.allMedicines.map((medicine) => (
                            <Chip key={medicine.id} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={`${medicine.medicineName}${medicine.strength ? ` ${medicine.strength}` : ""}`} onClick={() => addMedicineFromCatalog(medicine)} variant="outlined" />
                          )) : (
                            <Alert severity="info">No catalog match. Add manually.</Alert>
                          )}
                        </Stack>
                      </Stack>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Fast Packs</Typography>
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Fast packs replace the current prescription draft after confirmation. Existing manual rows are not overwritten silently.
                    </Typography>
                    <Stack spacing={1}>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Favorite Packs</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          <Typography variant="caption" color="text.secondary">No favorite packs yet.</Typography>
                        </Stack>
                      </Stack>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Recent Packs</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {fastPackRecentTemplates.length ? fastPackRecentTemplates.map((template) => (
                            <Chip
                              key={template.key}
                              size="small"
                              clickable={!prescriptionReadOnly}
                              color="secondary"
                              variant={selectedRxTemplateKey === "repeat-previous" ? "filled" : "outlined"}
                              label="Repeat previous prescription"
                              disabled={!previousPrescriptions.length || prescriptionReadOnly}
                              onClick={repeatPreviousPrescription}
                            />
                          )) : (
                            <Typography variant="caption" color="text.secondary">No recent packs.</Typography>
                          )}
                        </Stack>
                      </Stack>
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Clinic Templates</Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {fastPackClinicTemplates.map((template) => (
                            <Chip
                              key={template.key}
                              size="small"
                              clickable={!prescriptionReadOnly}
                              disabled={prescriptionReadOnly}
                              color="primary"
                              variant={selectedRxTemplateKey === template.key ? "filled" : "outlined"}
                              label={template.label}
                              onClick={() => applyRxTemplate(template)}
                            />
                          ))}
                        </Stack>
                      </Stack>
                      <Divider />
                      <Stack spacing={0.45}>
                        <Typography variant="caption" color="text.secondary">Previous Medicines</Typography>
                        <Stack spacing={0.55}>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            <Typography variant="caption" color="text.secondary">Favorites</Typography>
                            {previousMedicineGroups.favorites.length ? previousMedicineGroups.favorites.map((name) => (
                              <Chip key={name} size="small" label={name} clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} />
                            )) : <Typography variant="caption" color="text.secondary">No favorite medicines yet.</Typography>}
                          </Stack>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            <Typography variant="caption" color="text.secondary">Recent</Typography>
                            {previousMedicineGroups.recent.length ? previousMedicineGroups.recent.map((name) => (
                              <Chip key={`recent-${name}`} size="small" label={name} clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} />
                            )) : <Typography variant="caption" color="text.secondary">No recent medicines.</Typography>}
                          </Stack>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            <Typography variant="caption" color="text.secondary">Last prescribed</Typography>
                            {previousMedicineGroups.lastPrescribed.length ? previousMedicineGroups.lastPrescribed.map((name) => (
                              <Chip key={`last-${name}`} size="small" label={name} clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} />
                            )) : <Typography variant="caption" color="text.secondary">No last prescribed medicines.</Typography>}
                          </Stack>
                        </Stack>
                      </Stack>
                      {fastPackAiSuggestions.length ? (
                        <Stack spacing={0.45}>
                          <Typography variant="caption" color="text.secondary">AI Suggested</Typography>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {fastPackAiSuggestions.map((item, index) => (
                              <Chip
                                key={`${item.medicine}-${index}`}
                                size="small"
                                clickable={!prescriptionReadOnly}
                                disabled={prescriptionReadOnly}
                                color="secondary"
                                variant="outlined"
                                label={`${item.medicine}${item.dose ? ` • ${item.dose}` : ""}`}
                                onClick={() => addMedicineFromAiSuggestion(item)}
                              />
                            ))}
                          </Stack>
                        </Stack>
                      ) : null}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, lg: 8 }}>
            <Stack spacing={1.5}>
              <Card>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Box>
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <MedicationRoundedIcon fontSize="small" color="primary" />
                          <Typography variant="h6" sx={{ fontWeight: 900 }}>Active Prescription</Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary">One-click medicines, compact rows, fast chips.</Typography>
                      </Box>
                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                        {canEditConsultation && !prescriptionReadOnly ? <Button type="button" size="small" startIcon={<AddRoundedIcon />} onClick={addManualMedicine}>Manual row</Button> : null}
                        {canEditConsultation && !prescriptionReadOnly ? <Button type="button" size="small" variant="outlined" disabled={saving} onClick={() => void persistPrescription()}>Save Rx</Button> : null}
                        {canEditConsultation && !prescriptionReadOnly ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void previewCurrentPrescription()}>Preview</Button> : null}
                        {canPrintPrescription ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void downloadCurrentPrescription()}>Download PDF</Button> : null}
                        {canFinalizePrescription ? <Button type="button" size="small" color="secondary" disabled={saving || prescriptionReadOnly || !hasPrescriptionContent(prescriptionForm)} onClick={() => void finalizeCurrentPrescription()}>Finalize</Button> : null}
                      </Stack>
                    </Box>
                    {selectedRxTemplateKey ? (
                      <Alert severity="info" sx={{ py: 0.5 }}>
                        {selectedRxTemplateKey === "repeat-previous" ? "Previous prescription loaded into the active draft." : `${RX_TEMPLATES.find((template) => template.key === selectedRxTemplateKey)?.label || "Template"} loaded into the active draft.`}
                      </Alert>
                    ) : null}
                    {currentPrescriptionIsActiveCorrectionDraft ? (
                      <Alert severity="warning">
                        Active correction draft in progress. You can continue editing, finalize the draft, or discard it before starting a new correction.
                        <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ mt: 1 }}>
                          <Button type="button" size="small" variant="outlined" disabled={saving || openingCorrectionDraft} onClick={() => void continueActivePrescriptionDraft()}>
                            {openingCorrectionDraft ? "Opening..." : "Continue Draft"}
                          </Button>
                          <Button type="button" size="small" color="secondary" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void finalizeCurrentPrescription()}>Finalize Draft</Button>
                          <Button type="button" size="small" variant="outlined" color="error" disabled={saving} onClick={() => void discardCurrentPrescriptionDraft()}>Discard Draft</Button>
                        </Stack>
                      </Alert>
                    ) : canCreateCorrectionFromLatest ? (
                      <Alert severity="info">
                        Finalized prescriptions are immutable. Create a correction or follow-up draft to make changes.
                        <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ mt: 1 }}>
                          <TextField size="small" label="Correction reason" value={correctionReason} onChange={(e) => setCorrectionReason(e.target.value)} />
                          <Button type="button" size="small" variant="outlined" onClick={() => void createCorrectionDraft("SAME_DAY_CORRECTION")}>Same-day correction</Button>
                          <Button type="button" size="small" variant="outlined" onClick={() => void createCorrectionDraft("FOLLOW_UP")}>Follow-up draft</Button>
                        </Stack>
                      </Alert>
                    ) : null}
                    {!prescriptionReadyForCompletion && !prescriptionReadOnly ? (
                      <Typography variant="caption" color="text.secondary">
                        Next: save or preview the prescription, then finalize it before completing the consultation.
                      </Typography>
                    ) : null}

                    <Card variant="outlined" sx={{ boxShadow: "none" }}>
                      <CardContent sx={{ p: 1 }}>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Smart medicine suggestions</Typography>
                            </Stack>
                            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                              <Chip size="small" variant="outlined" label={aiPrescriptionItems.length ? `${aiPrescriptionItems.length} suggestion${aiPrescriptionItems.length === 1 ? "" : "s"}` : "No suggestions"} />
                              <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!aiAssistantEnabled || !consultation || !patient || aiBusy} onClick={() => void applyAiPrescriptionTemplate()}>
                                Suggest medicines with AIVA
                              </Button>
                            </Stack>
                          </Stack>
                          <Alert severity="info">AI medication suggestions are assistive. Doctor must verify before prescribing.</Alert>
                          {aiPrescriptionUnstructured && !aiPrescriptionItems.length ? (
                            <Alert severity="error">
                              {aiPrescriptionSuggestion || "AI returned an invalid response. Please retry."}
                            </Alert>
                          ) : null}
                          {aiPrescriptionItems.length ? (
                            <Stack spacing={0.75}>
                              {aiPrescriptionItems.map((item, index) => (
                                <PrescriptionSuggestionDraftCard
                                  key={`${item.medicine}-${index}`}
                                  item={item}
                                  generatedAt={prescriptionSuggestionGeneratedAt}
                                  disabled={prescriptionReadOnly}
                                  onAcceptSuggestion={(nextItem) => addMedicineFromAiSuggestion(nextItem)}
                                  onRejectSuggestion={() => setInfo(`${item.medicine} suggestion rejected.`)}
                                />
                              ))}
                            </Stack>
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              Clinical medication suggestions will appear here when enough diagnosis, allergy, and medication context is available.
                            </Typography>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>

                    <Card variant="outlined" sx={{ boxShadow: "none" }}>
                      <CardContent sx={{ p: 1 }}>
                        <Stack spacing={0.75}>
                          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prescription Quality Checklist</Typography>
                            <Chip size="small" variant="outlined" color={prescriptionQualityChecklist.some((item) => item.state === "needs_review") ? "warning" : "success"} label={`${prescriptionQualityChecklist.filter((item) => item.state === "complete").length}/${prescriptionQualityChecklist.length} complete`} />
                          </Stack>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                            {prescriptionQualityChecklist.filter((item) => item.state === "complete").slice(0, 4).map((item) => (
                              <Chip
                                key={item.label}
                                size="small"
                                variant="outlined"
                                color="success"
                                label={`${item.label}: Complete`}
                              />
                            ))}
                            {prescriptionQualityChecklist.filter((item) => item.state !== "complete").slice(0, 4).map((item) => (
                              <Chip
                                key={item.label}
                                size="small"
                                variant="outlined"
                                color={item.state === "needs_review" ? "warning" : "default"}
                                label={`${item.label}: ${item.state === "needs_review" ? "Needs review" : "Missing"}`}
                              />
                            ))}
                            {prescriptionQualityChecklist.length > 4 ? (
                              <Typography variant="caption" color="text.secondary">
                                View checklist below
                              </Typography>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>

                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Stack spacing={0.5}>
                          <TextField size="small" fullWidth label="Diagnosis snapshot" value={prescriptionForm.diagnosisSnapshot} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, diagnosisSnapshot: e.target.value }))} />
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" aria-label="Diagnosis snapshot chips">
                            {prescriptionDiagnosisSnapshotEntries.length ? (
                              prescriptionDiagnosisSnapshotEntries.map((diagnosis) => (
                                <Chip key={diagnosis} size="small" variant="outlined" label={diagnosis} />
                              ))
                            ) : (
                              <Typography variant="caption" color="text.secondary">
                                No diagnosis recorded.
                              </Typography>
                            )}
                          </Stack>
                        </Stack>
                      </Grid>
                      <Grid size={{ xs: 12, md: 5 }}>
                        <TextField
                          size="small"
                          fullWidth
                          label="Advice"
                          value={prescriptionForm.advice}
                          disabled={prescriptionReadOnly}
                          onChange={(e) => setPrescriptionForm((c) => ({ ...c, advice: e.target.value }))}
                          multiline
                          minRows={3}
                          helperText="Clinical advice carried from consultation. Edit before finalizing if needed."
                          sx={{
                            "& .MuiInputBase-root": {
                              alignItems: "flex-start",
                            },
                            "& .MuiInputBase-inputMultiline": {
                              lineHeight: 1.45,
                            },
                          }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 3 }}>
                        <Stack spacing={0.5}>
                          <TextField
                            size="small"
                            fullWidth
                            label="Follow-up"
                            type="date"
                            value={prescriptionForm.followUpDate}
                            disabled={prescriptionReadOnly}
                            onChange={(e) => setPrescriptionForm((c) => ({ ...c, followUpDate: e.target.value }))}
                            InputLabelProps={{ shrink: true }}
                            inputRef={prescriptionFollowUpInputRef}
                          />
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" aria-label="Follow-up shortcuts">
                            {PRESCRIPTION_FOLLOWUP_SHORTCUTS.map((chip) => (
                              <Chip
                                key={chip}
                                size="small"
                                clickable={!prescriptionReadOnly}
                                disabled={prescriptionReadOnly}
                                variant="outlined"
                                color="secondary"
                                label={chip}
                                onClick={() => applyPrescriptionFollowUpShortcut(chip)}
                              />
                            ))}
                            <Chip
                              size="small"
                              clickable={!prescriptionReadOnly}
                              disabled={prescriptionReadOnly}
                              variant="outlined"
                              label="Custom"
                              onClick={focusPrescriptionFollowUpInput}
                            />
                          </Stack>
                        </Stack>
                      </Grid>
                    </Grid>

                    <Stack spacing={1}>
                      {prescriptionForm.medicines.map((row, index) => (
                        <Card key={row.localId} variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, borderColor: invalidMedicineRowIds.includes(row.localId) ? "error.main" : undefined }}>
                          <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                            <Stack spacing={1}>
                              <Grid container spacing={1} alignItems="end">
                                <Grid size={{ xs: 12, md: 4.5 }}>
                                  <TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} helperText={invalidMedicineRowIds.includes(row.localId) ? "Medicine name, dosage, frequency, and duration are required." : " "} label={`Medicine ${index + 1}`} value={row.medicineName} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { medicineName: e.target.value })} />
                                </Grid>
                                <Grid size={{ xs: 6, md: 1 }}>
                                  <TextField size="small" fullWidth label="Strength" value={row.strength || ""} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { strength: e.target.value })} />
                                </Grid>
                                <Grid size={{ xs: 6, md: 1 }}>
                                  <FormControl size="small" fullWidth disabled={prescriptionReadOnly}>
                                    <InputLabel id={`route-label-${row.localId}`}>Route</InputLabel>
                                    <Select
                                      labelId={`route-label-${row.localId}`}
                                      label="Route"
                                      value={row.route ?? ""}
                                      onChange={(e) => updateMedicine(row.localId, { route: e.target.value })}
                                    >
                                      <MenuItem value=""><em>Blank</em></MenuItem>
                                      {MEDICINE_ROUTE_OPTIONS.filter((option) => option).map((option) => (
                                        <MenuItem key={option} value={option}>{MEDICINE_ROUTE_LABELS[option]}</MenuItem>
                                      ))}
                                    </Select>
                                  </FormControl>
                                </Grid>
                                <Grid size={{ xs: 6, md: 1.25 }}>
                                  <TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Dosage" value={row.dosage} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { dosage: e.target.value })} />
                                </Grid>
                                <Grid size={{ xs: 6, md: 1.25 }}>
                                  <TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Frequency" value={row.frequency} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { frequency: e.target.value })} />
                                </Grid>
                                <Grid size={{ xs: 6, md: 1 }}>
                                  <TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Duration" value={row.duration} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { duration: e.target.value })} />
                                </Grid>
                                <Grid size={{ xs: 12, md: 0.75 }} sx={{ display: "flex", alignItems: "center", justifyContent: { xs: "flex-end", md: "center" } }}>
                                  <IconButton size="small" disabled={prescriptionReadOnly} aria-label={`Delete medicine row ${index + 1}`} onClick={() => { setInvalidMedicineRowIds((current) => current.filter((id) => id !== row.localId)); setPrescriptionForm((c) => ({ ...c, medicines: c.medicines.filter((item) => item.localId !== row.localId) })); }}>
                                    <DeleteOutlineRoundedIcon fontSize="small" />
                                  </IconButton>
                                </Grid>
                              </Grid>
                              <Box
                                sx={{
                                  display: "grid",
                                  gap: 1,
                                  gridTemplateColumns: {
                                    xs: "1fr",
                                    sm: "repeat(2, minmax(0, 1fr))",
                                    lg: "repeat(3, minmax(0, 1fr))",
                                  },
                                  alignItems: "start",
                                }}
                              >
                                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                                  <Typography variant="caption" color="text.secondary">Frequency</Typography>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    {FREQUENCIES.map((chip) => <Chip key={chip} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={chip} onClick={() => updateMedicine(row.localId, { frequency: chip })} />)}
                                  </Stack>
                                </Stack>
                                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                                  <Typography variant="caption" color="text.secondary">Duration</Typography>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    {DURATIONS.map((chip) => <Chip key={chip} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} variant="outlined" label={chip} onClick={() => updateMedicine(row.localId, { duration: chip })} />)}
                                  </Stack>
                                </Stack>
                                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                                  <Typography variant="caption" color="text.secondary">Timing</Typography>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    {TIMINGS.map((chip) => <Chip key={chip.value} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} color={row.timing === chip.value ? "primary" : "default"} label={chip.label} onClick={() => updateMedicine(row.localId, { timing: chip.value })} />)}
                                  </Stack>
                                </Stack>
                              </Box>
                              <TextField size="small" fullWidth label="Instructions" value={row.instructions || ""} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { instructions: e.target.value })} placeholder="Auto or manual instructions" />
                              <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                <Button type="button" size="small" variant="text" disabled={prescriptionReadOnly} onClick={() => setAlternativeMedicineRowId((current) => current === row.localId ? null : row.localId)}>
                                  Alternatives
                                </Button>
                                {alternativeMedicineRowId === row.localId ? (
                                  <Stack spacing={0.45} sx={{ flex: 1, minWidth: "100%" }}>
                                    <Typography variant="caption" color="text.secondary">
                                      {getAlternativeMedicineSuggestions(row).length ? "Alternative medicine suggestions" : "No alternative data available."}
                                    </Typography>
                                    {getAlternativeMedicineSuggestions(row).length ? (
                                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                        {getAlternativeMedicineSuggestions(row).map((suggestion) => (
                                          <Chip key={suggestion} size="small" variant="outlined" label={suggestion} clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} onClick={() => setPrescriptionForm((current) => ({ ...current, medicines: current.medicines.map((medicine) => medicine.localId === row.localId ? { ...medicine, instructions: appendTokenLine(medicine.instructions || "", `Alternative: ${suggestion}`) } : medicine) }))} />
                                        ))}
                                      </Stack>
                                    ) : null}
                                  </Stack>
                                ) : null}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                    </Stack>

                    <Card variant="outlined" sx={{ boxShadow: "none" }}>
                      <CardContent sx={{ p: 1 }}>
                        <Stack spacing={0.85}>
                          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <HealthAndSafetyRoundedIcon fontSize="small" color="warning" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prescription Intelligence</Typography>
                            </Stack>
                            <Button
                              type="button"
                              size="small"
                              variant="text"
                              aria-label={prescriptionIntelligenceExpanded ? "Collapse prescription intelligence" : "Expand prescription intelligence"}
                              onClick={() => setPrescriptionIntelligenceExpanded((current) => !current)}
                            >
                              {prescriptionIntelligenceExpanded ? "Collapse" : "Expand"}
                            </Button>
                          </Stack>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {([
                              ["safety", "Safety"],
                              ["comparison", "Comparison"],
                              ["instructions", "Instructions"],
                              ["summary", "Summary"],
                            ] as const).map(([value, label]) => (
                              <Chip
                                key={value}
                                size="small"
                                clickable
                                variant={prescriptionIntelligenceTab === value ? "filled" : "outlined"}
                                color={prescriptionIntelligenceTab === value ? "warning" : "default"}
                                label={label}
                                onClick={() => {
                                  setPrescriptionIntelligenceTab(value);
                                  setPrescriptionIntelligenceExpanded(true);
                                }}
                              />
                            ))}
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            Clinical medication suggestions will appear here when enough diagnosis, allergy, and medication context is available.
                          </Typography>
                          <Collapse in={prescriptionIntelligenceExpanded} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                            <Stack spacing={0.9}>
                              <Tabs
                                value={prescriptionIntelligenceTab}
                                onChange={(_, value) => setPrescriptionIntelligenceTab(value)}
                                variant="scrollable"
                                scrollButtons="auto"
                                allowScrollButtonsMobile
                                sx={{ minHeight: 30, "& .MuiTab-root": { minHeight: 30, py: 0.25, px: 1, fontSize: 12 } }}
                              >
                                <Tab value="safety" label="Safety" />
                                <Tab value="comparison" label="Comparison" />
                                <Tab value="instructions" label="Instructions" />
                                <Tab value="summary" label="Summary" />
                              </Tabs>

                              {prescriptionIntelligenceTab === "safety" ? (
                                <Stack spacing={0.75}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Stack spacing={0.15}>
                                      <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                        {medicationSafetyEvaluation ? `Medication safety: ${medicationSafetyEvaluation.overallSeverity.replaceAll("_", " ").toLowerCase()}` : "Medication safety: unavailable"}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {medicationSafetyEvaluation
                                          ? `Rules version ${medicationSafetyEvaluation.rulesVersion} · ${medicationSafetyEvaluation.findings.length} findings · ${medicationSafetyEvaluation.dataQualityWarnings.length} data-quality warnings`
                                          : "Fallback checks use available structured data; unsupported rules remain unavailable."}
                                      </Typography>
                                      {!prescription?.id ? (
                                        <Typography variant="caption" color="text.secondary">
                                          Medication Safety requires a saved prescription.
                                        </Typography>
                                      ) : null}
                                    </Stack>
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                      <Button type="button" size="small" variant="outlined" onClick={() => void runMedicationSafety()} disabled={medicationSafetyCheckRunning || !consultation || !prescription?.id}>
                                        Run safety check
                                      </Button>
                                      <Button type="button" size="small" variant="text" aria-label={prescriptionSafetyDetailsOpen ? "Hide medication safety details" : "View medication safety details"} onClick={() => setPrescriptionSafetyDetailsOpen((current) => !current)}>
                                        {prescriptionSafetyDetailsOpen ? "Hide details" : "View details"}
                                      </Button>
                                    </Stack>
                                  </Stack>
                                  <Collapse in={prescriptionSafetyDetailsOpen} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                    <Stack spacing={0.55}>
                                      {!prescription?.id ? (
                                        <Alert severity="info">Medication Safety requires a saved prescription.</Alert>
                                      ) : null}
                                      {medicationSafetyLoading ? <LinearProgress /> : null}
                                      {medicationSafetyError ? (
                                        <Alert severity="warning">{medicationSafetyError}</Alert>
                                      ) : null}
                                      {medicationSafetyEvaluation ? (
                                        <Stack spacing={0.7}>
                                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                            <Chip size="small" label={`Overall: ${medicationSafetyEvaluation.overallSeverity.replaceAll("_", " ")}`} color={medicationSafetyEvaluation.overallSeverity === "CRITICAL" ? "error" : medicationSafetyEvaluation.overallSeverity === "WARNING" ? "warning" : medicationSafetyEvaluation.overallSeverity === "INFO" ? "info" : "default"} variant="outlined" />
                                            <Chip
                                              size="small"
                                              label={`Exact duplicate: ${normalizeMedicationSafetyCoverageLabel(medicationSafetyEvaluation.evaluationCoverage.exactDuplicateEvaluated, medicationSafetyEvaluation.dataQualityWarnings, [])}`}
                                              variant="outlined"
                                            />
                                            <Chip
                                              size="small"
                                              label={`Ingredient: ${normalizeMedicationSafetyCoverageLabel(medicationSafetyEvaluation.evaluationCoverage.ingredientDuplicateEvaluated, medicationSafetyEvaluation.dataQualityWarnings, ["active ingredient metadata is unavailable"])}`}
                                              variant="outlined"
                                            />
                                            <Chip
                                              size="small"
                                              label={`Class: ${normalizeMedicationSafetyCoverageLabel(medicationSafetyEvaluation.evaluationCoverage.classDuplicateEvaluated, medicationSafetyEvaluation.dataQualityWarnings, ["therapeutic class metadata is too broad or unavailable"])}`}
                                              variant="outlined"
                                            />
                                            <Chip
                                              size="small"
                                              label={`Allergy: ${normalizeMedicationSafetyCoverageLabel(medicationSafetyEvaluation.evaluationCoverage.allergyEvaluated, medicationSafetyEvaluation.dataQualityWarnings, ["allergy text is present but could not be structured"])}`}
                                              variant="outlined"
                                            />
                                            <Chip
                                              size="small"
                                              label={`Renal: ${formatCoverageStateLabel(
                                                medicationSafetyEvaluation.evaluationCoverage.renalCoverageStatus ||
                                                (medicationSafetyEvaluation.evaluationCoverage.renalEvaluated ? "EVALUATED" : "UNAVAILABLE")
                                              )}`}
                                              variant="outlined"
                                            />
                                          </Stack>
                                          {medicationSafetyEvaluation.findings.length ? (
                                            <Stack spacing={0.55}>
                                              {medicationSafetyEvaluation.findings.map((finding) => (
                                                <Card key={finding.findingId} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                                  <CardContent sx={{ p: 1, "&:last-child": { pb: 1 } }}>
                                                    <Stack spacing={0.45}>
                                                      <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                                        <Typography variant="body2" sx={{ fontWeight: 900 }}>{finding.title}</Typography>
                                                        <Chip size="small" variant="outlined" color={finding.severity === "CRITICAL" ? "error" : finding.severity === "WARNING" ? "warning" : finding.severity === "INFO" ? "info" : "default"} label={finding.severity.replaceAll("_", " ").toLowerCase()} />
                                                      </Stack>
                                                      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.45 }}>
                                                        {finding.ruleCode?.startsWith("MED_RENAL") ? formatRenalFindingText(finding.summary) : finding.summary}
                                                      </Typography>
                                                      {finding.clinicalRationale ? <Typography variant="caption" color="text.secondary">{finding.clinicalRationale}</Typography> : null}
                                                      {finding.ruleCode?.startsWith("MED_RENAL") && finding.verificationStatus ? (
                                                        <Typography variant="caption" color="text.secondary">
                                                          Verification: {formatClinicalReasoningVerificationLabel(finding.verificationStatus) || finding.verificationStatus}
                                                        </Typography>
                                                      ) : null}
                                                      {finding.evidence.length ? (
                                                        <Typography variant="caption" color="text.secondary">
                                                          Evidence: {finding.ruleCode?.startsWith("MED_RENAL") ? finding.evidence.map((item) => formatRenalFindingText(item)).join(" • ") : finding.evidence.join(" • ")}
                                                        </Typography>
                                                      ) : null}
                                                      {finding.sourceReferences.length ? (
                                                        finding.ruleCode?.startsWith("MED_RENAL") ? (
                                                          (() => {
                                                            const renalSource = formatRenalSourceLabels(finding.sourceReferences);
                                                            return (
                                                              <Typography variant="caption" color="text.secondary">
                                                                Source: {renalSource.source} / Origin: {renalSource.origin}
                                                              </Typography>
                                                            );
                                                          })()
                                                        ) : (
                                                          <Typography variant="caption" color="text.secondary">Sources: {finding.sourceReferences.join(" • ")}</Typography>
                                                        )
                                                      ) : null}
                                                    </Stack>
                                                  </CardContent>
                                                </Card>
                                              ))}
                                            </Stack>
                                          ) : (
                                            <Alert severity="success">No alerts found in evaluated rules.</Alert>
                                          )}
                                          {medicationSafetyEvaluation.dataQualityWarnings.length ? (
                                            <Stack spacing={0.35}>
                                              <Typography variant="caption" color="text.secondary">Data quality warnings</Typography>
                                              {medicationSafetyEvaluation.dataQualityWarnings.map((warning) => (
                                                <Typography key={warning} variant="caption" color="text.secondary">• {warning}</Typography>
                                              ))}
                                            </Stack>
                                          ) : null}
                                          <Stack spacing={0.5}>
                                            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                                              <Chip
                                                size="small"
                                                variant="outlined"
                                                color={medicationSafetyReviewFinalized ? "success" : (activeMedicationSafetyReview?.stale ? "warning" : activeMedicationSafetyReview?.readyForFinalization ? "success" : "default")}
                                                label={`Review: ${formatMedicationSafetyReviewDecisionStatus(activeMedicationSafetyReview?.decisionStatus)}`}
                                              />
                                              <Chip
                                                size="small"
                                                variant="outlined"
                                                label={medicationSafetyReviewFinalized ? "Finalized" : (activeMedicationSafetyReview ? (activeMedicationSafetyReview.stale ? "Stale" : "Current") : "Not reviewed")}
                                                color={medicationSafetyReviewFinalized ? "success" : (activeMedicationSafetyReview?.stale ? "warning" : activeMedicationSafetyReview ? "default" : "default")}
                                              />
                                              {medicationSafetyReviewFinalized ? (
                                                <Chip size="small" variant="outlined" label="Safety snapshot: Current at finalization" color="success" />
                                              ) : (
                                                <Chip size="small" variant="outlined" label={activeMedicationSafetyReview?.readyForFinalization ? "Ready for finalization" : "Review required"} color={activeMedicationSafetyReview?.readyForFinalization ? "success" : "warning"} />
                                              )}
                                            </Stack>
                                            {activeMedicationSafetyReview?.stale ? (
                                              <Alert severity="warning">Prescription changed after safety review. Run the safety check again.</Alert>
                                            ) : null}
                                            {medicationSafetyReviewFinalized ? (
                                              <Alert severity="info">Finalized safety review is read-only.</Alert>
                                            ) : null}
                                            {medicationSafetyReviewError ? <Alert severity="warning">{medicationSafetyReviewError}</Alert> : null}
                                            {medicationSafetyReviewLoading ? <LinearProgress /> : null}
                                            {medicationSafetyEvaluation.findings.some((finding) => finding.severity === "WARNING" || finding.severity === "CRITICAL") ? (
                                              <Stack spacing={0.55}>
                                                <Typography variant="caption" color="text.secondary">Review findings</Typography>
                                                {medicationSafetyEvaluation.findings
                                                  .filter((finding) => finding.severity === "WARNING" || finding.severity === "CRITICAL")
                                                  .map((finding) => {
                                                    const draft = medicationSafetyReviewDraft.findings[finding.findingId] || {
                                                      findingId: finding.findingId,
                                                      ruleCode: finding.ruleCode,
                                                      acknowledged: false,
                                                      overrideApplied: false,
                                                      reasonCode: null,
                                                      reasonText: null,
                                                    };
                                                    const savedFindingReview = activeMedicationSafetyReview?.findingReviews?.find((reviewFinding) => reviewFinding.findingId === finding.findingId) || null;
                                                    const isCritical = finding.severity === "CRITICAL";
                                                    return (
                                                      <Card key={finding.findingId} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                                        <CardContent sx={{ p: 1, "&:last-child": { pb: 1 } }}>
                                                          <Stack spacing={0.6}>
                                                            <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                                              <Typography variant="body2" sx={{ fontWeight: 900 }}>{finding.title}</Typography>
                                                              <Chip size="small" variant="outlined" color={isCritical ? "error" : "warning"} label={isCritical ? "Critical" : "Warning"} />
                                                            </Stack>
                                                            <Typography variant="caption" color="text.secondary">{finding.summary}</Typography>
                                                            {finding.evidence.length ? <Typography variant="caption" color="text.secondary">Evidence: {finding.evidence.join(" • ")}</Typography> : null}
                                                            {finding.sourceReferences.length ? <Typography variant="caption" color="text.secondary">Sources: {finding.sourceReferences.join(" • ")}</Typography> : null}
                                                            {medicationSafetyReviewFinalized ? (
                                                              <Stack spacing={0.25}>
                                                                <Typography variant="caption" color="text.secondary">
                                                                  Acknowledged: {savedFindingReview?.acknowledged || savedFindingReview?.overrideApplied ? "Yes" : "No"}
                                                                </Typography>
                                                                {savedFindingReview?.reasonCode ? (
                                                                  <Typography variant="caption" color="text.secondary">
                                                                    Reason: {savedFindingReview.reasonCode.replaceAll("_", " ").toLowerCase()}
                                                                  </Typography>
                                                                ) : null}
                                                                {savedFindingReview?.reasonText ? (
                                                                  <Typography variant="caption" color="text.secondary">
                                                                    Review note: {savedFindingReview.reasonText}
                                                                  </Typography>
                                                                ) : null}
                                                                <Typography variant="caption" color="text.secondary">
                                                                  Reviewed by: {activeMedicationSafetyReview?.reviewedByDisplayName || "User unavailable"}
                                                                </Typography>
                                                                <Typography variant="caption" color="text.secondary">
                                                                  Reviewed at: {compactDateTime(activeMedicationSafetyReview?.reviewedAt || null)}
                                                                </Typography>
                                                              </Stack>
                                                            ) : (
                                                              <>
                                                                <FormControlLabel
                                                                  control={
                                                                    <Checkbox
                                                                      checked={isCritical ? draft.overrideApplied : draft.acknowledged}
                                                                      onChange={(_, checked) => updateMedicationSafetyFindingReview(finding.findingId, isCritical ? { overrideApplied: checked } : { acknowledged: checked })}
                                                                      size="small"
                                                                    />
                                                                  }
                                                                  label={isCritical ? "Override with clinical reason" : "Acknowledge reviewed"}
                                                                />
                                                                <FormControl size="small" fullWidth>
                                                                  <InputLabel id={`med-safety-reason-${finding.findingId}`}>Reason code</InputLabel>
                                                                  <Select
                                                                    labelId={`med-safety-reason-${finding.findingId}`}
                                                                    label="Reason code"
                                                                    value={draft.reasonCode || ""}
                                                                    onChange={(event) => updateMedicationSafetyFindingReview(finding.findingId, { reasonCode: String(event.target.value) })}
                                                                  >
                                                                    <MenuItem value="">Select reason</MenuItem>
                                                                    <MenuItem value="BENEFIT_OUTWEIGHS_RISK">Benefit outweighs risk</MenuItem>
                                                                    <MenuItem value="CONTINUATION_CONFIRMED">Continuation confirmed</MenuItem>
                                                                    <MenuItem value="DUPLICATE_INTENTIONAL">Duplicate intentional</MenuItem>
                                                                    <MenuItem value="PATIENT_HISTORY_VERIFIED">Patient history verified</MenuItem>
                                                                    <MenuItem value="OTHER">Other</MenuItem>
                                                                  </Select>
                                                                </FormControl>
                                                                <TextField
                                                                  label={isCritical ? "Override reason" : "Review note"}
                                                                  size="small"
                                                                  fullWidth
                                                                  multiline
                                                                  minRows={2}
                                                                  value={draft.reasonText || ""}
                                                                  onChange={(event) => updateMedicationSafetyFindingReview(finding.findingId, { reasonText: event.target.value })}
                                                                />
                                                              </>
                                                            )}
                                                          </Stack>
                                                        </CardContent>
                                                      </Card>
                                                    );
                                                  })}
                                                {medicationSafetyReviewFinalized ? null : (
                                                  <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                                    <Button type="button" size="small" variant="outlined" onClick={() => void saveMedicationSafetyReview()} disabled={medicationSafetyReviewSubmitting || medicationSafetyLoading || !medicationSafetyEvaluation}>
                                                      Save review
                                                    </Button>
                                                  </Stack>
                                                )}
                                              </Stack>
                                            ) : null}
                                          </Stack>
                                          {medicationSafetyReviewFinalized ? (
                                            <Typography variant="caption" color="text.secondary">
                                              Safety snapshot: Current at finalization
                                            </Typography>
                                          ) : medicationSafetyEvaluation.sourceSnapshotMetadata.prescriptionStatus ? (
                                            <Typography variant="caption" color="text.secondary">
                                              Source status: {medicationSafetyEvaluation.sourceSnapshotMetadata.prescriptionStatus.replaceAll("_", " ").toLowerCase()}
                                            </Typography>
                                          ) : null}
                                        </Stack>
                                      ) : (
                                        <Stack spacing={0.55}>
                                          {prescriptionSafetyChecks.map((check) => (
                                                <Stack key={check.label} direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{check.label}</Typography>
                                                  <Stack direction="row" spacing={0.5} alignItems="center">
                                                <Chip size="small" variant="outlined" color={check.status === "critical" ? "error" : check.status === "warning" ? "warning" : check.status === "safe" || check.status === "complete" ? "success" : "default"} label={check.status === "critical" ? "Critical" : check.status === "warning" ? "Warning" : check.status === "safe" || check.status === "complete" ? "Passed" : check.status === "needs_review" ? "Needs review" : "Unavailable"} />
                                                <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 180 }} noWrap title={check.message}>{check.message}</Typography>
                                              </Stack>
                                            </Stack>
                                          ))}
                                          <Typography variant="caption" color="text.secondary">
                                            Advanced checks require renal function, liver function, pregnancy status, pediatric age, or max-dose reference data.
                                          </Typography>
                                        </Stack>
                                      )}
                                    </Stack>
                                  </Collapse>
                                </Stack>
                              ) : null}

                              {prescriptionIntelligenceTab === "comparison" ? (
                                <Stack spacing={0.75}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Typography variant="body2" color="text.secondary">
                                      Continued medicines: {prescriptionComparison?.continued.length || 0} · Stopped medicines: {prescriptionComparison?.stopped.length || 0} · New medicines: {prescriptionComparison?.newMeds.length || 0} · Dose changes: {prescriptionComparison?.doseChanged.length || 0}
                                    </Typography>
                                    <Button type="button" size="small" variant="text" aria-label={prescriptionComparisonDetailsOpen ? "Hide comparison details" : "View comparison details"} onClick={() => setPrescriptionComparisonDetailsOpen((current) => !current)}>
                                      {prescriptionComparisonDetailsOpen ? "Hide details" : "View details"}
                                    </Button>
                                  </Stack>
                                  {prescriptionComparison ? (
                                    <Collapse in={prescriptionComparisonDetailsOpen} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                      <Stack spacing={0.4}>
                                        <Typography variant="caption" color="text.secondary" noWrap title={prescriptionComparison.continued.join(", ") || "None"}>Continued medicines: {prescriptionComparison.continued.join(", ") || "None"}</Typography>
                                        <Typography variant="caption" color="text.secondary" noWrap title={prescriptionComparison.stopped.join(", ") || "None"}>Stopped medicines: {prescriptionComparison.stopped.join(", ") || "None"}</Typography>
                                        <Typography variant="caption" color="text.secondary" noWrap title={prescriptionComparison.newMeds.join(", ") || "None"}>New medicines: {prescriptionComparison.newMeds.join(", ") || "None"}</Typography>
                                        <Typography variant="caption" color="text.secondary" noWrap title={prescriptionComparison.doseChanged.join(", ") || "None"}>Dose changes: {prescriptionComparison.doseChanged.join(", ") || "None"}</Typography>
                                        <Typography variant="caption" color="warning.main" noWrap>
                                          {prescriptionComparison.repeatedAntibiotic ? "Repeated antibiotic detected. Review continuity." : "No repeated antibiotic detected."}
                                        </Typography>
                                        <Typography variant="caption" color="warning.main" noWrap>
                                          {prescriptionComparison.repeatedPainkiller ? "Repeated NSAID/painkiller detected. Review continuity." : "No repeated NSAID/painkiller detected."}
                                        </Typography>
                                        {prescriptionComparison.longTermContinuity.length ? (
                                          <Typography variant="caption" color="text.secondary" noWrap title={prescriptionComparison.longTermContinuity.join(", ")}>
                                            Long-term continuity: {prescriptionComparison.longTermContinuity.join(", ")}
                                          </Typography>
                                        ) : null}
                                      </Stack>
                                    </Collapse>
                                  ) : (
                                    <Typography variant="caption" color="text.secondary">
                                      No previous finalized prescription available for comparison.
                                    </Typography>
                                  )}
                                </Stack>
                              ) : null}

                              {prescriptionIntelligenceTab === "instructions" ? (
                                <Stack spacing={0.75}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Typography variant="body2" color="text.secondary">1. Choose language  2. Generate  3. Review  4. Copy / Add</Typography>
                                    <Button type="button" size="small" variant="text" aria-label="Patient instructions summary" onClick={() => setPrescriptionIntelligenceTab("instructions")}>Instructions</Button>
                                  </Stack>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    {(["ENGLISH", "HINDI", "MARATHI"] as const).map((language) => (
                                      <Chip
                                        key={language}
                                        size="small"
                                        clickable={!prescriptionInstructionsLoading && !prescriptionReadOnly}
                                        variant={prescriptionInstructionsLanguage === language ? "filled" : "outlined"}
                                        color={prescriptionInstructionsLanguage === language ? "primary" : "default"}
                                        label={language === "ENGLISH" ? "English" : language === "HINDI" ? "Hindi" : "Marathi"}
                                        onClick={() => setPrescriptionInstructionsLanguage(language)}
                                      />
                                    ))}
                                  </Stack>
                                  <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!consultation || !patient || prescriptionInstructionsLoading || prescriptionReadOnly} onClick={() => void generatePrescriptionPatientInstructions()}>
                                    Generate patient instructions
                                  </Button>
                                  {prescriptionInstructionsDraft ? (
                                    <ClinicalAiDraftCard
                                      title="Patient Instructions"
                                      status="DRAFTED"
                                      generatedAt={prescriptionInstructionsGeneratedAt}
                                      sourceSummary={`Language: ${prescriptionInstructionsLanguage}`}
                                      draftText={(prescriptionInstructionsDraft.draft || prescriptionInstructionsDraft.message || "").trim()}
                                      acceptLabel="Add to Advice"
                                      editLabel="Edit"
                                      rejectLabel="Reject"
                                      copyLabel="Copy"
                                      acceptDisabled={prescriptionReadOnly}
                                      editDisabled={prescriptionReadOnly}
                                      rejectDisabled={false}
                                      copyDisabled={!((prescriptionInstructionsDraft.draft || prescriptionInstructionsDraft.message || "").trim())}
                                      onAccept={() => setPrescriptionForm((current) => ({ ...current, advice: appendTokenLine(current.advice, prescriptionInstructionsDraft.draft || prescriptionInstructionsDraft.message || "") }))}
                                      onEdit={(nextText) => setPrescriptionInstructionsDraft((current) => (current ? { ...current, draft: nextText, message: nextText } : current))}
                                      onReject={() => setPrescriptionInstructionsDraft(null)}
                                      onCopy={() => void navigator.clipboard.writeText((prescriptionInstructionsDraft.draft || prescriptionInstructionsDraft.message || "").trim())}
                                    />
                                  ) : (
                                    <Typography variant="caption" color="text.secondary">
                                      No instructions generated yet. Choose a language and generate patient-friendly instructions.
                                    </Typography>
                                  )}
                                </Stack>
                              ) : null}

                              {prescriptionIntelligenceTab === "summary" ? (
                                <Stack spacing={0.75}>
                                  <Typography variant="body2" color="text.secondary">
                                    Compact summary preview. The full prescription summary remains below the editor and is generated manually.
                                  </Typography>
                                  {prescriptionSummaryDraft ? (
                                    <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1.5, bgcolor: "background.paper", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                                      <Typography variant="body2" sx={{ lineHeight: 1.45 }}>{prescriptionSummaryDraft}</Typography>
                                    </Box>
                                  ) : (
                                    <Typography variant="caption" color="text.secondary">
                                      No summary generated yet. Use the Generate Prescription Summary action below the editor.
                                    </Typography>
                                  )}
                                </Stack>
                              ) : null}
                            </Stack>
                          </Collapse>
                        </Stack>
                      </CardContent>
                    </Card>

                    <Card variant="outlined" sx={{ boxShadow: "none" }}>
                      <CardContent sx={{ p: 1.25 }}>
                        <Stack spacing={0.75}>
                          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prescription summary</Typography>
                            <Button type="button" size="small" variant="outlined" onClick={generatePrescriptionSummaryDraft}>
                              Generate Prescription Summary
                            </Button>
                          </Stack>
                          {prescriptionSummaryDraft ? (
                            <Stack spacing={0.75}>
                              <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1.5, bgcolor: "background.paper", whiteSpace: "pre-wrap" }}>
                                <Typography variant="body2" sx={{ lineHeight: 1.45 }}>{prescriptionSummaryDraft}</Typography>
                              </Box>
                              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                <Button type="button" size="small" variant="outlined" onClick={() => void navigator.clipboard.writeText(prescriptionSummaryDraft)}>
                                  Copy
                                </Button>
                                <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={() => setConsultationForm((current) => ({ ...current, clinicalNotes: appendTokenLine(current.clinicalNotes, prescriptionSummaryDraft) }))}>
                                  Add to SOAP
                                </Button>
                                <Button type="button" size="small" variant="outlined" onClick={() => {
                                  void navigator.clipboard.writeText(prescriptionSummaryDraft);
                                  setInfo("Prescription summary copied for WhatsApp.");
                                }}>
                                  Use for WhatsApp message
                                </Button>
                                <Button type="button" size="small" variant="outlined" onClick={() => {
                                  void navigator.clipboard.writeText(prescriptionSummaryDraft);
                                  setInfo("Prescription summary copied for Email.");
                                }}>
                                  Use for Email message
                                </Button>
                              </Stack>
                            </Stack>
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              Diagnosis, medicines, tests, advice, follow-up, warnings, and red flags will appear here.
                            </Typography>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                      <Box>
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <HistoryRoundedIcon fontSize="small" color="primary" />
                          <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Prescription version history</Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary">Finalized versions stay immutable and traceable.</Typography>
                      </Box>
                      {selectedPrescriptionVersion ? <Chip size="small" label={prescriptionVersionTitle(selectedPrescriptionVersion)} color="secondary" variant="outlined" /> : null}
                    </Box>
                    {!prescriptionHistory.length ? (
                      <Alert severity="info">No finalized prescriptions yet. Once finalized, every version will be stored here for medico-legal audit.</Alert>
                    ) : (
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12, md: 5 }}>
                          <Stack spacing={0.75}>
                            {prescriptionHistory.map((row) => (
                              <Card
                                key={row.id}
                                variant="outlined"
                                sx={{
                                  boxShadow: "none",
                                  cursor: "pointer",
                                  borderColor: selectedPrescriptionVersion?.id === row.id ? "primary.main" : "divider",
                                  bgcolor: selectedPrescriptionVersion?.id === row.id ? "action.hover" : "background.paper",
                                }}
                                onClick={() => setSelectedPrescriptionVersionId(row.id)}
                              >
                                <CardContent sx={{ p: 1 }}>
                                  <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                                      <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap>{prescriptionVersionTitle(row)}</Typography>
                                      <Typography variant="caption" color="text.secondary" noWrap>{compactDateTime(row.createdAt)}{row.followUpDate ? ` • Follow-up ${row.followUpDate}` : ""}</Typography>
                                    </Stack>
                                    <Chip size="small" label={row.status} color={row.status === "FINALIZED" ? "success" : row.status === "CORRECTED" ? "warning" : row.status === "SUPERSEDED" ? "default" : "primary"} variant="outlined" />
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        </Grid>
                        <Grid size={{ xs: 12, md: 7 }}>
                          {selectedPrescriptionVersion ? (
                            <Card variant="outlined" sx={{ boxShadow: "none" }}>
                              <CardContent sx={{ p: 1.5 }}>
                                <Stack spacing={1}>
                                  <Stack direction="row" spacing={1} flexWrap="wrap">
                                    <Chip size="small" label={`Version ${selectedPrescriptionVersion.versionNumber || 1}`} />
                                    <Chip size="small" label={selectedPrescriptionVersion.status} color={selectedPrescriptionVersion.status === "FINALIZED" ? "success" : selectedPrescriptionVersion.status === "CORRECTED" ? "warning" : "default"} />
                                    {selectedPrescriptionVersion.flowType ? <Chip size="small" label={selectedPrescriptionVersion.flowType.replaceAll("_", " ")} /> : null}
                                  </Stack>
                                  <Typography variant="body2"><b>Correction reason:</b> {selectedPrescriptionVersion.correctionReason || "-"}</Typography>
                                  <Typography variant="body2"><b>Follow-up:</b> {selectedPrescriptionVersion.followUpDate || "-"}</Typography>
                                  <Typography variant="body2"><b>Parent:</b> {selectedPrescriptionVersion.parentPrescriptionId || "-"}</Typography>
                                  <Typography variant="body2"><b>Superseded by:</b> {selectedPrescriptionVersion.supersededByPrescriptionId || "-"}</Typography>
                                  <Typography variant="body2"><b>Finalized at:</b> {compactDateTime(selectedPrescriptionVersion.finalizedAt)}</Typography>
                                  <Typography variant="body2"><b>Created at:</b> {compactDateTime(selectedPrescriptionVersion.createdAt)}</Typography>
                                  <Divider />
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Medicines</Typography>
                                  {selectedPrescriptionVersion.medicines.length ? (
                                    <Stack spacing={0.5}>
                                      {selectedPrescriptionVersion.medicines.map((medicine, index) => (
                                        <Typography key={`${medicine.medicineName}-${index}`} variant="body2">
                                          {medicine.medicineName} {medicine.strength || ""} {medicine.dosage} {medicine.frequency} {medicine.duration}
                                        </Typography>
                                      ))}
                                    </Stack>
                                  ) : <Typography variant="body2" color="text.secondary">No medicines recorded.</Typography>}
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Tests</Typography>
                                  {selectedPrescriptionVersion.recommendedTests.length ? (
                                    <Stack spacing={0.5}>
                                      {selectedPrescriptionVersion.recommendedTests.map((test, index) => (
                                        <Typography key={`${test.testName}-${index}`} variant="body2">
                                          {test.testName}{test.instructions ? ` - ${test.instructions}` : ""}
                                        </Typography>
                                      ))}
                                    </Stack>
                                  ) : <Typography variant="body2" color="text.secondary">No tests recorded.</Typography>}
                                </Stack>
                              </CardContent>
                            </Card>
                          ) : <Alert severity="info">Select a version to inspect the medico-legal trail.</Alert>}
                        </Grid>
                      </Grid>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {/*
        <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
          <WorkflowStrip text="Timeline → Consultations → Prescriptions → Reports → Trends" />

          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <InsightsRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Clinical Snapshot</Typography>
                    </Stack>
                    <Typography variant="body2"><b>Latest diagnosis:</b> {lastConsultation?.diagnosis || consultation?.diagnosis || "Not recorded"}</Typography>
                    <Typography variant="body2"><b>Latest vitals:</b> {historySnapshotVitals || "Not recorded"}</Typography>
                    <Typography variant="body2"><b>Active chronic conditions:</b> {clinicalSnapshotConditions}</Typography>
                    <Typography variant="body2" color={patientRow?.allergies ? "error" : "text.primary"}><b>Allergies:</b> {patientRow?.allergies || "Not recorded"}</Typography>
                    <Typography variant="body2"><b>Long-term meds:</b> {clinicalSnapshotMedications}</Typography>
                    <Typography variant="body2"><b>Last note:</b> {compactText(lastConsultation?.clinicalNotes || lastConsultation?.advice || lastConsultation?.diagnosis || consultation?.clinicalNotes || consultation?.advice, 140) || "Not recorded"}</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={0.85}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <HistoryEduRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Recent Consultations</Typography>
                      </Stack>
                      <Button type="button" size="small" variant="text" onClick={() => setHistoryView("consultations")}>View all</Button>
                    </Stack>
                    {historyOverviewConsultations.length ? (
                      <Stack spacing={0.65}>
                        {historyOverviewConsultations.map((row) => (
                          <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                              <Stack spacing={0.45}>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Chip size="small" variant="outlined" label={compactDate(row.createdAt)} />
                                  <Chip size="small" variant="outlined" label={row.status || "Unknown"} color={row.status === "COMPLETED" ? "success" : row.status === "DRAFT" ? "warning" : "default"} />
                                </Stack>
                                <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.diagnosis || "No diagnosis"}>
                                  {row.diagnosis || "No diagnosis"}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" noWrap title={compactText(row.clinicalNotes || row.advice || row.symptoms, 140) || "Not recorded"}>
                                  {compactText(row.clinicalNotes || row.advice || row.symptoms, 96) || "Not recorded"}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" noWrap>
                                  Doctor: {row.doctorName || consultation?.doctorName || "Not recorded"}
                                </Typography>
                                <Button type="button" size="small" variant="outlined" onClick={() => navigate(`/consultations/${row.id}`)}>
                                  Open consultation
                                </Button>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                        {historyConsultationEntries.length > historyOverviewConsultations.length ? (
                          <Button type="button" size="small" variant="outlined" onClick={() => setHistoryView("consultations")}>
                            View all {historyConsultationEntries.length} consultations
                          </Button>
                        ) : null}
                      </Stack>
                    ) : (
                      <Alert severity="info">No previous consultations.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={0.85}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <MedicationRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Recent Prescriptions</Typography>
                      </Stack>
                      <Button type="button" size="small" variant="text" onClick={() => setHistoryView("prescriptions")}>View all</Button>
                    </Stack>
                    {historyOverviewPrescriptionGroups.length ? (
                      <Stack spacing={0.65}>
                        {historyOverviewPrescriptionGroups.map((group) => {
                          const current = group.current;
                          const statusColor = current.status === "FINALIZED" || current.status === "PRINTED" ? "success" : current.status === "CORRECTED" ? "warning" : current.status === "SUPERSEDED" ? "default" : current.status === "CANCELLED" ? "error" : "primary";
                          return (
                            <Card key={group.key} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                              <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                <Stack spacing={0.45}>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center" justifyContent="space-between">
                                    <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={current.diagnosisSnapshot || current.prescriptionNumber}>
                                      {current.diagnosisSnapshot || current.prescriptionNumber}
                                    </Typography>
                                    <Chip size="small" variant="outlined" color={statusColor} label={current.status.replaceAll("_", " ")} />
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" noWrap>
                                    {compactDate(current.createdAt)} • {current.doctorName || consultation?.doctorName || "Doctor"}
                                  </Typography>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" label={`Current ${current.versionNumber ? `v${current.versionNumber}` : "version"}`} />
                                    {group.versionCount > 1 ? <Chip size="small" variant="outlined" label={`${group.versionCount - 1} earlier version${group.versionCount - 1 === 1 ? "" : "s"}`} /> : null}
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          );
                        })}
                      </Stack>
                    ) : (
                      <Alert severity="info">No previous prescriptions.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                  <Stack spacing={0.85}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <InsightsRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Important Trends</Typography>
                      </Stack>
                      <Button type="button" size="small" variant="text" onClick={() => setHistoryView("trends")}>View all</Button>
                    </Stack>
                    <StructuredTrendSummary
                      structuredTrends={historyTrendEntries}
                      legacyTrends={[]}
                      latestReport={null}
                      summary={null}
                      fallbackMode="legacyOnly"
                      emptyStateLabel="No comparable trends yet"
                      emptyStateDetail="Structured longitudinal trends appear here when repeated numeric results are available."
                    />
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
            <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
              <Stack spacing={0.85}>
                <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.15 }}>History subviews</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                      Timeline is the default; use the subviews for focused review.
                    </Typography>
                  </Box>
                </Stack>
                <Tabs
                  value={historyView}
                  onChange={(_, value) => setHistoryView(value as HistoryViewKey)}
                  variant="scrollable"
                  scrollButtons="auto"
                  sx={{
                    minHeight: 44,
                    "& .MuiTabs-indicator": {
                      height: 3,
                      borderRadius: 999,
                    },
                  }}
                >
                  {HISTORY_VIEW_KEYS.map((view) => (
                    <Tab key={view} value={view} label={view === "timeline" ? "Timeline" : view === "consultations" ? "Consultations" : view === "prescriptions" ? "Prescriptions" : view === "documents" ? "Reports & Documents" : "Trends"} />
                  ))}
                </Tabs>
              </Stack>
            </CardContent>
          </Card>

          {historyView === "timeline" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <TimelineRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Unified Timeline</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        Grouped by date, with consultations, prescriptions, reports, SOAP and lab orders kept in one place.
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {historyTimelineEntries.length} item{historyTimelineEntries.length === 1 ? "" : "s"}
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {HISTORY_TIMELINE_FILTER_KEYS.map((filterKey) => (
                      <Chip
                        key={filterKey}
                        size="small"
                        clickable
                        variant={historyTimelineFilter === filterKey ? "filled" : "outlined"}
                        color={historyTimelineFilter === filterKey ? "primary" : "default"}
                        label={timelineFilterLabel(filterKey)}
                        onClick={() => setHistoryTimelineFilter(filterKey)}
                      />
                    ))}
                  </Stack>
                  {!historyTimelineVisibleGroups.length ? (
                    <Alert severity="info">No timeline events yet.</Alert>
                  ) : (
                    <Stack spacing={1}>
                      {historyTimelineVisibleGroups.map((group) => (
                        <Card key={group.dateLabel} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                          <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                            <Stack spacing={0.7}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{group.dateLabel}</Typography>
                              <Stack spacing={0.65}>
                                {group.items.map((item) => (
                                  <Card
                                    key={item.id}
                                    variant="outlined"
                                    sx={{ boxShadow: "none", borderRadius: 1.25, cursor: item.itemType === "DOCUMENT" || item.consultationId || item.prescriptionId ? "pointer" : "default" }}
                                    onClick={() => {
                                      if (item.itemType === "DOCUMENT" && item.documentId) {
                                        const document = clinicalDocuments.find((row) => row.id === item.documentId);
                                        if (document) void openClinicalDocument(document);
                                        return;
                                      }
                                      if (item.consultationId && item.consultationId !== consultation.id) {
                                        navigate(`/consultations/${item.consultationId}`);
                                      }
                                    }}
                                  >
                                    <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                                      <Stack spacing={0.4}>
                                        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                          <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                            <Chip size="small" variant="outlined" color={timelineColor(item.itemType)} label={timelineTypeLabel(item.itemType)} />
                                            {item.status ? <Chip size="small" variant="outlined" label={item.status.replaceAll("_", " ")} /> : null}
                                          </Stack>
                                          <Typography variant="caption" color="text.secondary">{compactDateTime(item.occurredAt)}</Typography>
                                        </Stack>
                                        <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={compactTimelineTitle(item.title)}>
                                          {compactTimelineTitle(item.title)}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" noWrap title={item.subtitle || "-"}>
                                          {item.subtitle || "Not recorded"}
                                        </Typography>
                                      </Stack>
                                    </CardContent>
                                  </Card>
                                ))}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                      {historyTimelineVisibleGroups.length < historyTimelineGroups.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryTimelineLimit((current) => Math.min(current + 6, historyTimelineGroups.length))}>
                          Show older entries
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "consultations" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <HistoryEduRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Consultation History</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Recent consultations first. Empty clinical fields are omitted.</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">{historyConsultationEntries.length} consultation{historyConsultationEntries.length === 1 ? "" : "s"}</Typography>
                  </Stack>
                  {!historyConsultationRows.length ? (
                    <Alert severity="info">No previous consultations.</Alert>
                  ) : (
                    <Stack spacing={0.65}>
                      {historyConsultationRows.map((row) => (
                        <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                            <Stack spacing={0.45}>
                              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                <Chip size="small" variant="outlined" label={compactDate(row.createdAt)} />
                                <Chip size="small" variant="outlined" label={row.status || "Unknown"} color={row.status === "COMPLETED" ? "success" : row.status === "DRAFT" ? "warning" : "default"} />
                              </Stack>
                              <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.diagnosis || "No diagnosis"}>
                                {row.diagnosis || "No diagnosis"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap title={compactText(row.clinicalNotes || row.advice || row.symptoms, 140) || "Not recorded"}>
                                {compactText(row.clinicalNotes || row.advice || row.symptoms, 96) || "Not recorded"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap>
                                Doctor: {row.doctorName || consultation?.doctorName || "Not recorded"}
                              </Typography>
                              <Button type="button" size="small" variant="outlined" onClick={() => navigate(`/consultations/${row.id}`)}>
                                Open consultation
                              </Button>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                      {historyConsultationRows.length < historyConsultationEntries.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryConsultationLimit((current) => Math.min(current + 3, historyConsultationEntries.length))}>
                          Show older consultations
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "prescriptions" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <MedicationRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Prescription History</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Grouped by encounter. Earlier versions stay available on demand.</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">{historyPrescriptionGroups.length} encounter{historyPrescriptionGroups.length === 1 ? "" : "s"}</Typography>
                  </Stack>
                  {!historyPrescriptionGroups.length ? (
                    <Alert severity="info">No previous prescriptions.</Alert>
                  ) : (
                    <Stack spacing={0.75}>
                      {historyPrescriptionGroups.slice(0, historyPrescriptionLimit).map((group) => {
                        const current = group.current;
                        const expandedGroup = Boolean(expanded[`history-prescription-${group.key}`]);
                        const statusColor = current.status === "FINALIZED" || current.status === "PRINTED" ? "success" : current.status === "CORRECTED" ? "warning" : current.status === "SUPERSEDED" ? "default" : current.status === "CANCELLED" ? "error" : "primary";
                        return (
                          <Card key={group.key} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.6}>
                                <Stack direction="row" spacing={0.75} justifyContent="space-between" alignItems="flex-start" flexWrap="wrap">
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={current.diagnosisSnapshot || current.prescriptionNumber}>
                                      {current.diagnosisSnapshot || current.prescriptionNumber}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {compactDate(current.createdAt)} • {current.doctorName || consultation?.doctorName || "Doctor"}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" color={statusColor} label={current.status.replaceAll("_", " ")} />
                                    <Chip size="small" variant="outlined" label={`Current ${current.versionNumber ? `v${current.versionNumber}` : ""}`.trim()} />
                                    {group.versionCount > 1 ? <Chip size="small" variant="outlined" label={`${group.versionCount - 1} earlier version${group.versionCount - 1 === 1 ? "" : "s"}`} /> : null}
                                  </Stack>
                                </Stack>
                                <Typography variant="caption" color="text.secondary" noWrap title={compactText(current.advice || current.diagnosisSnapshot || "", 120) || "Not recorded"}>
                                  {compactText(current.advice || current.diagnosisSnapshot || "", 96) || "Not recorded"}
                                </Typography>
                                {group.versions.length > 1 ? (
                                  <>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleSection(`history-prescription-${group.key}`)}>
                                      {expandedGroup ? "Hide earlier versions" : `View ${group.versions.length - 1} earlier version${group.versions.length - 1 === 1 ? "" : "s"}`}
                                    </Button>
                                    <Collapse in={expandedGroup} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                      <Stack spacing={0.65} sx={{ pt: 0.5 }}>
                                        {group.versions.map((version) => {
                                          const versionStatusColor = version.status === "FINALIZED" || version.status === "PRINTED" ? "success" : version.status === "CORRECTED" ? "warning" : version.status === "SUPERSEDED" ? "default" : version.status === "CANCELLED" ? "error" : "primary";
                                          return (
                                            <Card key={version.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25 }}>
                                              <CardContent sx={{ p: 0.75, "&:last-child": { pb: 0.75 } }}>
                                                <Stack spacing={0.45}>
                                                  <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                                    <Typography variant="body2" sx={{ fontWeight: 800 }}>v{version.versionNumber || 1}</Typography>
                                                    <Chip size="small" variant="outlined" color={versionStatusColor} label={version.status.replaceAll("_", " ")} />
                                                  </Stack>
                                                  <Typography variant="caption" color="text.secondary" noWrap>
                                                    {compactDateTime(version.createdAt)}{version.correctionReason ? ` • ${version.correctionReason}` : ""}{version.doctorName || version.finalizedByDoctorUserId ? ` • ${version.doctorName || version.finalizedByDoctorUserId}` : ""}
                                                  </Typography>
                                                </Stack>
                                              </CardContent>
                                            </Card>
                                          );
                                        })}
                                      </Stack>
                                    </Collapse>
                                  </>
                                ) : null}
                              </Stack>
                            </CardContent>
                          </Card>
                        );
                      })}
                      {historyPrescriptionLimit < historyPrescriptionGroups.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryPrescriptionLimit((current) => Math.min(current + 3, historyPrescriptionGroups.length))}>
                          Show older prescriptions
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "documents" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <InsightsRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Reports &amp; Documents</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Compact document cards hide raw payloads by default. Use View for the viewer and More for technical details.</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <Chip size="small" label={`${historyDocumentEntries.length} document${historyDocumentEntries.length === 1 ? "" : "s"}`} color="info" variant="outlined" />
                      <Button size="small" variant="contained" onClick={() => setUploadDialogOpen(true)}>Upload Report / Referral</Button>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {DOCUMENT_FILTERS.map((filter) => (
                      <Chip
                        key={filter.key}
                        size="small"
                        label={filter.label}
                        color={documentFilter === filter.key ? "primary" : "default"}
                        variant={documentFilter === filter.key ? "filled" : "outlined"}
                        onClick={() => setDocumentFilter(filter.key)}
                        clickable
                      />
                    ))}
                    {availableDocumentReviewFilters.length ? (
                      availableDocumentReviewFilters.map((filterKey) => (
                        <Chip
                          key={filterKey}
                          size="small"
                          label={documentReviewStatusLabel(filterKey)}
                          color={documentReviewFilter === filterKey ? documentReviewStatusColor(filterKey) : "default"}
                          variant={documentReviewFilter === filterKey ? "filled" : "outlined"}
                          onClick={() => setDocumentReviewFilter(filterKey)}
                          clickable
                        />
                      ))
                    ) : null}
                  </Stack>
                  {!historyDocumentGroups.length ? (
                    <Alert severity="info">No documents match the selected filter.</Alert>
                  ) : (
                    <Stack spacing={0.75}>
                      {historyDocumentGroups.map((row) => {
                        const reviewStatus = documentReviewStatusKey(row);
                        const technicalOpen = Boolean(expanded[`history-document-${row.id}`]);
                        const highlights = collectDocumentHighlights(row);
                        return (
                          <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.65}>
                                <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.title || row.originalFilename}>
                                      {row.title || row.originalFilename}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {documentTypeLabel(row.documentType)} • {row.reportDate || compactDate(row.createdAt)} • {isPublishedLabDocument(row) ? "Published" : row.uploadSource}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" label={row.verificationStatus} />
                                    {reviewStatus ? <Chip size="small" variant="outlined" color={documentReviewStatusColor(reviewStatus)} label={documentReviewStatusLabel(reviewStatus)} /> : null}
                                  </Stack>
                                </Stack>
                                <Stack spacing={0.25}>
                                  {highlights.length ? highlights.map((item) => (
                                    <Typography key={item} variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                                      {item}
                                    </Typography>
                                  )) : (
                                    <Typography variant="caption" color="text.secondary">No extraction highlights available.</Typography>
                                  )}
                                </Stack>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Button size="small" variant="outlined" onClick={() => void openClinicalDocument(row)}>View</Button>
                                  <Button size="small" variant="outlined" onClick={() => void openClinicalDocument(row)}>Review extraction</Button>
                                  <Button size="small" variant="outlined" onClick={() => toggleSection(`history-document-${row.id}`)}>
                                    {technicalOpen ? "Hide" : "More"}
                                  </Button>
                                </Stack>
                                <Collapse in={technicalOpen} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                  <Stack spacing={0.55} sx={{ pt: 0.5 }}>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Technical details</Typography>
                                    <Typography variant="caption" color="text.secondary">Source module: {row.sourceModule || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">Source entity: {row.sourceEntityId || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">Uploaded by: {row.uploadedByName || row.uploadedByUserId || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI summary: {row.aiExtractionSummary || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI review notes: {row.aiExtractionReviewNotes || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">OCR status: {row.ocrStatus || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI status: {row.aiExtractionStatus || "Not recorded"}</Typography>
                                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                      {canEditConsultation && canRepairClinicalMemory(row) ? (
                                        <Button size="small" variant="outlined" onClick={() => void repairClinicalMemoryRow(row.id)} disabled={documentRowBusyId === row.id}>
                                          Repair Memory
                                        </Button>
                                      ) : null}
                                      {canEditConsultation ? (
                                        <Button size="small" variant="outlined" onClick={() => void reprocessClinicalDocumentRow(row.id)} disabled={documentRowBusyId === row.id}>
                                          Retry AI
                                        </Button>
                                      ) : null}
                                    </Stack>
                                  </Stack>
                                </Collapse>
                              </Stack>
                            </CardContent>
                          </Card>
                        );
                      })}
                      {historyDocumentLimit < historyDocumentEntries.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryDocumentLimit((current) => Math.min(current + 6, historyDocumentEntries.length))}>
                          Show older documents
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <TrendingUpRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Trends</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Structured longitudinal trend data appears here without recalculation in React.</Typography>
                    </Box>
                    <Button type="button" size="small" variant="text" onClick={() => setHistoryView("trends")}>View all</Button>
                  </Stack>
                  <StructuredTrendSummary
                    structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
                    legacyTrends={[]}
                    latestReport={null}
                    summary={null}
                    fallbackMode="legacyOnly"
                    emptyStateLabel="No comparable trends yet"
                    emptyStateDetail="Structured comparison is available when previous numeric results exist."
                  />
                </Stack>
              </CardContent>
            </Card>
          )}

      {false ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12 }}>
            <WorkflowStrip text="Timeline → Previous Visits → Reports → Documents" />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Stack spacing={0.75}><Stack direction="row" spacing={0.75} alignItems="center"><InsightsRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Clinical Snapshot</Typography></Stack><Typography variant="body2"><b>Last diagnosis:</b> {lastConsultation?.diagnosis || "-"}</Typography><Typography variant="body2"><b>Last vitals:</b> {lastConsultation ? `BP ${lastConsultation.bloodPressureSystolic || "-"} / ${lastConsultation.bloodPressureDiastolic || "-"}, Pulse ${lastConsultation.pulseRate || "-"}, Temp ${lastConsultation.temperature || "-"}, Resp ${lastConsultation.respiratoryRate || "-"}` : "Not recorded"}</Typography><Typography variant="body2"><b>Last BMI:</b> {lastBmi ? `${lastBmi.toFixed(1)} (${lastBmiCategory || "n/a"})` : "Not recorded"}</Typography><Typography variant="body2"><b>Chronic:</b> {clinicalSnapshotConditions}</Typography><Typography variant="body2" color={patientRow?.allergies ? "error" : "text.primary"}><b>Allergies:</b> {patientRow?.allergies || "Not recorded"}</Typography><Typography variant="body2"><b>Long-term meds:</b> {clinicalSnapshotMedications}</Typography><Typography variant="body2"><b>History:</b> {patientRow?.surgicalHistory || "Not recorded"}</Typography></Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Stack spacing={0.75}><Stack direction="row" spacing={0.75} alignItems="center"><HistoryEduRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Previous Consultations</Typography></Stack>{!patient?.previousConsultations?.length ? <Alert severity="info">No previous consultations.</Alert> : <List dense>{patient.previousConsultations.slice(0, 8).map((row) => <ListItemButton key={row.id} onClick={() => navigate(`/consultations/${row.id}`)}><ListItemText primary={row.diagnosis || "No diagnosis"} secondary={`${compactDate(row.createdAt)} • ${row.status}`} /></ListItemButton>)}</List>}</Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Stack spacing={0.75}><Stack direction="row" spacing={0.75} alignItems="center"><MedicationRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Previous Prescriptions</Typography></Stack>{!previousPrescriptions.length ? <Alert severity="info">No previous prescriptions.</Alert> : <List dense>{previousPrescriptions.slice(0, 8).map((row) => <ListItemButton key={row.id} onClick={() => navigate(`/consultations/${row.consultationId}`)}><ListItemText primary={row.prescriptionNumber} secondary={`${compactDate(row.createdAt)} • ${row.status}`} /></ListItemButton>)}</List>}</Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <InsightsRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Patient Documents</Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip size="small" label={`${clinicalDocuments.length} document(s)`} color="info" />
                      <Button size="small" variant="contained" onClick={() => setUploadDialogOpen(true)}>Upload Report / Referral</Button>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                    {DOCUMENT_FILTERS.map((filter) => (
                      <Chip
                        key={filter.key}
                        size="small"
                        label={filter.label}
                        color={documentFilter === filter.key ? "primary" : "default"}
                        variant={documentFilter === filter.key ? "filled" : "outlined"}
                        onClick={() => setDocumentFilter(filter.key)}
                        clickable
                      />
                    ))}
                  </Stack>
                  <Stack spacing={1}>
                    {!clinicalDocuments.filter((row) => documentFilter === "ALL" || documentFilterKey(row.documentType) === documentFilter).length ? (
                      <Alert severity="info">No documents match the selected filter.</Alert>
                    ) : clinicalDocuments
                      .filter((row) => documentFilter === "ALL" || documentFilterKey(row.documentType) === documentFilter)
                      .slice(0, 10)
                      .map((row) => (
                        <Box key={row.id} sx={{ p: 1.25, border: "1px solid", borderColor: "divider", borderRadius: 2, display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                          <Box sx={{ minWidth: 0 }}>
                            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5, flexWrap: "wrap" }}>
                              <Chip size="small" label={row.title || row.documentType.replaceAll("_", " ")} color={documentFilterKey(row.documentType) === "REFERRAL" ? "secondary" : "default"} />
                              <Chip size="small" variant="outlined" label={row.uploadSource} />
                              <Typography variant="caption" color="text.secondary">{compactDate(row.createdAt)}</Typography>
                            </Stack>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.originalFilename}</Typography>
                            <Typography variant="caption" color="text.secondary" display="block">
                          {row.description || "No notes"}{row.aiExtractionSummary ? ` • AI: ${row.aiExtractionSummary}` : ""}{row.reportDate ? ` • Report date ${row.reportDate}` : ""}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                          {formatDocumentOpsSummary(row)}
                        </Typography>
                          </Box>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Button size="small" onClick={() => void openClinicalDocument(row)}>View</Button>
                            <IconButton size="small" onClick={() => void refreshClinicalDocumentRow(row.id)}>
                              <RefreshRoundedIcon fontSize="small" />
                            </IconButton>
                            {canEditConsultation && canRepairClinicalMemory(row) ? (
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => void repairClinicalMemoryRow(row.id)}
                                disabled={documentRowBusyId === row.id}
                              >
                                Repair Memory
                              </Button>
                            ) : null}
                            {canEditConsultation ? (
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => void reprocessClinicalDocumentRow(row.id)}
                                disabled={documentRowBusyId === row.id}
                              >
                                Retry AI
                              </Button>
                            ) : null}
                          </Stack>
                        </Box>
                      ))}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.75}>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <TimelineRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Unified Patient Timeline</Typography>
                  </Stack>
                  {!activeTimeline.length ? (
                    <Alert severity="info">No timeline events yet.</Alert>
                  ) : (
                    <List dense>
                      {activeTimeline.slice(0, 12).map((item) => (
                        <ListItemButton
                          key={item.id}
                          onClick={() => {
                            if (item.itemType === "DOCUMENT" && item.documentId) {
                              const document = clinicalDocuments.find((row) => row.id === item.documentId);
                              if (document) void openClinicalDocument(document);
                              return;
                            }
                            if (item.consultationId && item.consultationId !== consultation.id) {
                              navigate(`/consultations/${item.consultationId}`);
                            }
                          }}
                        >
                          <ListItemText
                            primary={`${timelineTypeLabel(item.itemType)} • ${item.title}`}
                            secondary={`${item.subtitle || "-"} • ${compactDateTime(item.occurredAt)}`}
                          />
                        </ListItemButton>
                      ))}
                    </List>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      */}

      {false ? (
        <Grid container spacing={1.5} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, md: 6 }}>
            {reportComparisonPlaceholder}
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.5 }}>
                <Stack spacing={0.75}>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <MonitorHeartRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Vitals / diagnosis trend</Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    No trend summary yet. The workspace will surface changes in vitals, recurring diagnoses, and long-term patterns here.
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 2 ? (
        <Stack spacing={1.25}>
          <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
            <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
              <Stack spacing={0.85}>
                <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.15 }}>History subviews</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                      Timeline is the default; use the subviews for focused review.
                    </Typography>
                  </Box>
                </Stack>
                <Tabs
                  value={historyView}
                  onChange={(_, value) => setHistoryView(value as HistoryViewKey)}
                  variant="scrollable"
                  scrollButtons="auto"
                  sx={{
                    minHeight: 44,
                    "& .MuiTabs-indicator": {
                      height: 3,
                      borderRadius: 999,
                    },
                  }}
                >
                  {HISTORY_VIEW_KEYS.map((view) => (
                    <Tab key={view} value={view} label={view === "timeline" ? "Timeline" : view === "consultations" ? "Consultations" : view === "prescriptions" ? "Prescriptions" : view === "documents" ? "Reports & Documents" : "Trends"} />
                  ))}
                </Tabs>
              </Stack>
            </CardContent>
          </Card>

          {historyView === "timeline" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <TimelineRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Unified Timeline</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        Grouped by date, with consultations, prescriptions, reports, SOAP and lab orders kept in one place.
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {historyTimelineEntries.length} item{historyTimelineEntries.length === 1 ? "" : "s"}
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {HISTORY_TIMELINE_FILTER_KEYS.map((filterKey) => (
                      <Chip
                        key={filterKey}
                        size="small"
                        clickable
                        variant={historyTimelineFilter === filterKey ? "filled" : "outlined"}
                        color={historyTimelineFilter === filterKey ? "primary" : "default"}
                        label={timelineFilterLabel(filterKey)}
                        onClick={() => setHistoryTimelineFilter(filterKey)}
                      />
                    ))}
                  </Stack>
                  {!historyTimelineVisibleGroups.length ? (
                    <Alert severity="info">No timeline events yet.</Alert>
                  ) : (
                    <Stack spacing={1}>
                      {historyTimelineVisibleGroups.map((group) => (
                        <Card key={group.dateLabel} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                          <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                            <Stack spacing={0.7}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{group.dateLabel}</Typography>
                              <Stack spacing={0.65}>
                                {group.items.map((item) => (
                                  <Card
                                    key={item.id}
                                    variant="outlined"
                                    sx={{ boxShadow: "none", borderRadius: 1.25, cursor: item.itemType === "DOCUMENT" || item.consultationId || item.prescriptionId ? "pointer" : "default" }}
                                    onClick={() => {
                                      if (item.itemType === "DOCUMENT" && item.documentId) {
                                        const document = clinicalDocuments.find((row) => row.id === item.documentId);
                                        if (document) void openClinicalDocument(document);
                                        return;
                                      }
                                      if (item.consultationId && item.consultationId !== consultation.id) {
                                        navigate(`/consultations/${item.consultationId}`);
                                      }
                                    }}
                                  >
                                    <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                                      <Stack spacing={0.4}>
                                        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                          <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                            <Chip size="small" variant="outlined" color={timelineColor(item.itemType)} label={timelineTypeLabel(item.itemType)} />
                                            {item.status ? <Chip size="small" variant="outlined" label={item.status.replaceAll("_", " ")} /> : null}
                                          </Stack>
                                          <Typography variant="caption" color="text.secondary">{compactDateTime(item.occurredAt)}</Typography>
                                        </Stack>
                                        <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={compactTimelineTitle(item.title)}>
                                          {compactTimelineTitle(item.title)}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" noWrap title={item.subtitle || "-"}>
                                          {item.subtitle || "Not recorded"}
                                        </Typography>
                                      </Stack>
                                    </CardContent>
                                  </Card>
                                ))}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                      {historyTimelineVisibleGroups.length < historyTimelineGroups.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryTimelineLimit((current) => Math.min(current + 6, historyTimelineGroups.length))}>
                          Show older entries
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "consultations" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <HistoryEduRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Consultation History</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Recent consultations first. Empty clinical fields are omitted.</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">{historyConsultationEntries.length} consultation{historyConsultationEntries.length === 1 ? "" : "s"}</Typography>
                  </Stack>
                  {!historyConsultationRows.length ? (
                    <Alert severity="info">No previous consultations.</Alert>
                  ) : (
                    <Stack spacing={0.65}>
                      {historyConsultationRows.map((row) => (
                        <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                          <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                            <Stack spacing={0.45}>
                              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                <Chip size="small" variant="outlined" label={compactDate(row.createdAt)} />
                                <Chip size="small" variant="outlined" label={row.status || "Unknown"} color={row.status === "COMPLETED" ? "success" : row.status === "DRAFT" ? "warning" : "default"} />
                              </Stack>
                              <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.diagnosis || "No diagnosis"}>
                                {row.diagnosis || "No diagnosis"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap title={compactText(row.clinicalNotes || row.advice || row.symptoms, 140) || "Not recorded"}>
                                {compactText(row.clinicalNotes || row.advice || row.symptoms, 96) || "Not recorded"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap>
                                Doctor: {row.doctorName || consultation?.doctorName || "Not recorded"}
                              </Typography>
                              <Button type="button" size="small" variant="outlined" onClick={() => navigate(`/consultations/${row.id}`)}>
                                Open consultation
                              </Button>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                      {historyConsultationRows.length < historyConsultationEntries.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryConsultationLimit((current) => Math.min(current + 3, historyConsultationEntries.length))}>
                          Show older consultations
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "prescriptions" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <MedicationRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Prescription History</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Grouped by encounter. Earlier versions stay available on demand.</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">{historyPrescriptionGroups.length} encounter{historyPrescriptionGroups.length === 1 ? "" : "s"}</Typography>
                  </Stack>
                  {!historyPrescriptionGroups.length ? (
                    <Alert severity="info">No previous prescriptions.</Alert>
                  ) : (
                    <Stack spacing={0.75}>
                      {historyPrescriptionGroups.slice(0, historyPrescriptionLimit).map((group) => {
                        const current = group.current;
                        const expandedGroup = Boolean(expanded[`history-prescription-${group.key}`]);
                        const statusColor = current.status === "FINALIZED" || current.status === "PRINTED" ? "success" : current.status === "CORRECTED" ? "warning" : current.status === "SUPERSEDED" ? "default" : current.status === "CANCELLED" ? "error" : "primary";
                        return (
                          <Card key={group.key} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.6}>
                                <Stack direction="row" spacing={0.75} justifyContent="space-between" alignItems="flex-start" flexWrap="wrap">
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={current.diagnosisSnapshot || current.prescriptionNumber}>
                                      {current.diagnosisSnapshot || current.prescriptionNumber}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {compactDate(current.createdAt)} • {current.doctorName || consultation?.doctorName || "Doctor"}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" color={statusColor} label={current.status.replaceAll("_", " ")} />
                                    <Chip size="small" variant="outlined" label={`Current ${current.versionNumber ? `v${current.versionNumber}` : ""}`.trim()} />
                                    {group.versionCount > 1 ? <Chip size="small" variant="outlined" label={`${group.versionCount - 1} earlier version${group.versionCount - 1 === 1 ? "" : "s"}`} /> : null}
                                  </Stack>
                                </Stack>
                                <Typography variant="caption" color="text.secondary" noWrap title={compactText(current.advice || current.diagnosisSnapshot || "", 120) || "Not recorded"}>
                                  {compactText(current.advice || current.diagnosisSnapshot || "", 96) || "Not recorded"}
                                </Typography>
                                {group.versions.length > 1 ? (
                                  <>
                                    <Button type="button" size="small" variant="text" onClick={() => toggleSection(`history-prescription-${group.key}`)}>
                                      {expandedGroup ? "Hide earlier versions" : `View ${group.versions.length - 1} earlier version${group.versions.length - 1 === 1 ? "" : "s"}`}
                                    </Button>
                                    <Collapse in={expandedGroup} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                      <Stack spacing={0.65} sx={{ pt: 0.5 }}>
                                        {group.versions.map((version) => {
                                          const versionStatusColor = version.status === "FINALIZED" || version.status === "PRINTED" ? "success" : version.status === "CORRECTED" ? "warning" : version.status === "SUPERSEDED" ? "default" : version.status === "CANCELLED" ? "error" : "primary";
                                          return (
                                            <Card key={version.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.25 }}>
                                              <CardContent sx={{ p: 0.75, "&:last-child": { pb: 0.75 } }}>
                                                <Stack spacing={0.45}>
                                                  <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                                    <Typography variant="body2" sx={{ fontWeight: 800 }}>v{version.versionNumber || 1}</Typography>
                                                    <Chip size="small" variant="outlined" color={versionStatusColor} label={version.status.replaceAll("_", " ")} />
                                                  </Stack>
                                                  <Typography variant="caption" color="text.secondary" noWrap>
                                                    {compactDateTime(version.createdAt)}{version.correctionReason ? ` • ${version.correctionReason}` : ""}{version.doctorName || version.finalizedByDoctorUserId ? ` • ${version.doctorName || version.finalizedByDoctorUserId}` : ""}
                                                  </Typography>
                                                </Stack>
                                              </CardContent>
                                            </Card>
                                          );
                                        })}
                                      </Stack>
                                    </Collapse>
                                  </>
                                ) : null}
                              </Stack>
                            </CardContent>
                          </Card>
                        );
                      })}
                      {historyPrescriptionLimit < historyPrescriptionGroups.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryPrescriptionLimit((current) => Math.min(current + 3, historyPrescriptionGroups.length))}>
                          Show older prescriptions
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : historyView === "documents" ? (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <InsightsRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Reports &amp; Documents</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Compact document cards hide raw payloads by default. Use View for the viewer and More for technical details.</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <Chip size="small" label={`${historyDocumentEntries.length} document${historyDocumentEntries.length === 1 ? "" : "s"}`} color="info" variant="outlined" />
                      <Button size="small" variant="contained" onClick={() => setUploadDialogOpen(true)}>Upload Report / Referral</Button>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {DOCUMENT_FILTERS.map((filter) => (
                      <Chip
                        key={filter.key}
                        size="small"
                        label={filter.label}
                        color={documentFilter === filter.key ? "primary" : "default"}
                        variant={documentFilter === filter.key ? "filled" : "outlined"}
                        onClick={() => setDocumentFilter(filter.key)}
                        clickable
                      />
                    ))}
                    {availableDocumentReviewFilters.length ? (
                      availableDocumentReviewFilters.map((filterKey) => (
                        <Chip
                          key={filterKey}
                          size="small"
                          label={documentReviewStatusLabel(filterKey)}
                          color={documentReviewFilter === filterKey ? documentReviewStatusColor(filterKey) : "default"}
                          variant={documentReviewFilter === filterKey ? "filled" : "outlined"}
                          onClick={() => setDocumentReviewFilter(filterKey)}
                          clickable
                        />
                      ))
                    ) : null}
                  </Stack>
                  {!historyDocumentGroups.length ? (
                    <Alert severity="info">No documents match the selected filter.</Alert>
                  ) : (
                    <Stack spacing={0.75}>
                      {historyDocumentGroups.map((row) => {
                        const reviewStatus = documentReviewStatusKey(row);
                        const technicalOpen = Boolean(expanded[`history-document-${row.id}`]);
                        const highlights = collectDocumentHighlights(row);
                        return (
                          <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.65}>
                                <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.title || row.originalFilename}>
                                      {row.title || row.originalFilename}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {documentTypeLabel(row.documentType)} • {row.reportDate || compactDate(row.createdAt)} • {isPublishedLabDocument(row) ? "Published" : row.uploadSource}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" label={row.verificationStatus} />
                                    {reviewStatus ? <Chip size="small" variant="outlined" color={documentReviewStatusColor(reviewStatus)} label={documentReviewStatusLabel(reviewStatus)} /> : null}
                                  </Stack>
                                </Stack>
                                <Stack spacing={0.25}>
                                  {highlights.length ? highlights.map((item) => (
                                    <Typography key={item} variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                                      {item}
                                    </Typography>
                                  )) : (
                                    <Typography variant="caption" color="text.secondary">No extraction highlights available.</Typography>
                                  )}
                                </Stack>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Button size="small" variant="outlined" onClick={() => void openClinicalDocument(row)}>View</Button>
                                  <Button size="small" variant="outlined" onClick={() => void openClinicalDocument(row)}>Review extraction</Button>
                                  <Button size="small" variant="outlined" onClick={() => toggleSection(`history-document-${row.id}`)}>
                                    {technicalOpen ? "Hide" : "More"}
                                  </Button>
                                </Stack>
                                <Collapse in={technicalOpen} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                                  <Stack spacing={0.55} sx={{ pt: 0.5 }}>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Technical details</Typography>
                                    <Typography variant="caption" color="text.secondary">Source module: {row.sourceModule || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">Source entity: {row.sourceEntityId || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">Uploaded by: {row.uploadedByName || row.uploadedByUserId || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI summary: {row.aiExtractionSummary || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI review notes: {row.aiExtractionReviewNotes || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">OCR status: {row.ocrStatus || "Not recorded"}</Typography>
                                    <Typography variant="caption" color="text.secondary">AI status: {row.aiExtractionStatus || "Not recorded"}</Typography>
                                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                      {canEditConsultation && canRepairClinicalMemory(row) ? (
                                        <Button size="small" variant="outlined" onClick={() => void repairClinicalMemoryRow(row.id)} disabled={documentRowBusyId === row.id}>
                                          Repair Memory
                                        </Button>
                                      ) : null}
                                      {canEditConsultation ? (
                                        <Button size="small" variant="outlined" onClick={() => void reprocessClinicalDocumentRow(row.id)} disabled={documentRowBusyId === row.id}>
                                          Retry AI
                                        </Button>
                                      ) : null}
                                    </Stack>
                                  </Stack>
                                </Collapse>
                              </Stack>
                            </CardContent>
                          </Card>
                        );
                      })}
                      {historyDocumentLimit < historyDocumentEntries.length ? (
                        <Button type="button" size="small" variant="outlined" onClick={() => setHistoryDocumentLimit((current) => Math.min(current + 6, historyDocumentEntries.length))}>
                          Show older documents
                        </Button>
                      ) : null}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : (
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
              <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                <Stack spacing={0.9}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Box>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <TrendingUpRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Trends</Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">Structured longitudinal trend data appears here without recalculation in React.</Typography>
                    </Box>
                    <Button type="button" size="small" variant="text" onClick={() => setHistoryView("trends")}>View all</Button>
                  </Stack>
                  <StructuredTrendSummary
                    structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
                    legacyTrends={[]}
                    latestReport={null}
                    summary={null}
                    fallbackMode="legacyOnly"
                    emptyStateLabel="No comparable trends yet"
                    emptyStateDetail="Structured comparison is available when previous numeric results exist."
                  />
                </Stack>
              </CardContent>
            </Card>
          )}
        </Stack>
      ) : null}

      {activeTab === 3 ? (
        <Stack spacing={1.5}>
          <Alert severity="info" sx={{ py: 0.65 }}>
            Review history → Select tests → Check duplicates → Confirm order → Track report
          </Alert>
          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, lg: 7 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.35 }}>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <ScienceRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>Recommended Tests</Typography>
                      </Stack>
                      <Button type="button" size="small" variant="outlined" onClick={openLabOrderDialog} disabled={readOnly || !labTests.length}>
                        Select from catalog
                      </Button>
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Select tests manually. AIVA only suggests context-aware options.
                    </Typography>
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={0.55}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Frequently Ordered</Typography>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {recommendedLabOrderGroups.frequentItems.length ? recommendedLabOrderGroups.frequentItems.map((item) => (
                              <Chip
                                key={item.id}
                                size="small"
                                variant="outlined"
                                color={labOrderTestIds.includes(item.id) ? "primary" : "default"}
                                label={item.testName}
                                clickable={!readOnly}
                                onClick={() => setLabOrderTestIds((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])}
                              />
                            )) : <Typography variant="caption" color="text.secondary">No frequent tests available.</Typography>}
                          </Stack>
                        </Stack>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={0.55}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Diagnosis Based</Typography>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {recommendedLabOrderGroups.diagnosisItems.length ? recommendedLabOrderGroups.diagnosisItems.map((item) => (
                              <Chip
                                key={item.id}
                                size="small"
                                variant="outlined"
                                color={labOrderTestIds.includes(item.id) ? "primary" : "default"}
                                label={item.testName}
                                clickable={!readOnly}
                                onClick={() => setLabOrderTestIds((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])}
                              />
                            )) : <Typography variant="caption" color="text.secondary">No diagnosis-based tests suggested yet.</Typography>}
                          </Stack>
                        </Stack>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={0.55}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Previous Follow-up</Typography>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {recommendedLabOrderGroups.followUpItems.length ? recommendedLabOrderGroups.followUpItems.map((item) => (
                              <Chip
                                key={item.id}
                                size="small"
                                variant="outlined"
                                color={labOrderTestIds.includes(item.id) ? "primary" : "default"}
                                label={item.testName}
                                clickable={!readOnly}
                                onClick={() => setLabOrderTestIds((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])}
                              />
                            )) : <Typography variant="caption" color="text.secondary">No follow-up tests suggested.</Typography>}
                          </Stack>
                        </Stack>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={0.55}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Custom</Typography>
                          <Stack direction="row" spacing={1}>
                            <TextField
                              fullWidth
                              size="small"
                              label="Search tests"
                              value={customTest}
                              disabled={readOnly}
                              onChange={(e) => setCustomTest(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === "Enter") {
                                  e.preventDefault();
                                  const exactMatch = labTests.find((test) => normalizeLookupKey(test.testName) === normalizeLookupKey(customTest) || normalizeLookupKey(test.testCode) === normalizeLookupKey(customTest));
                                  if (exactMatch) {
                                    setLabOrderTestIds((current) => current.includes(exactMatch.id) ? current : [...current, exactMatch.id]);
                                  }
                                }
                              }}
                              placeholder="Search active lab catalog"
                            />
                            <Button type="button" variant="outlined" disabled={readOnly || !labTests.length} onClick={() => {
                              const exactMatch = labTests.find((test) => normalizeLookupKey(test.testName) === normalizeLookupKey(customTest) || normalizeLookupKey(test.testCode) === normalizeLookupKey(customTest));
                              if (!exactMatch) return;
                              setLabOrderTestIds((current) => current.includes(exactMatch.id) ? current : [...current, exactMatch.id]);
                            }}>
                              Add
                            </Button>
                          </Stack>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {recommendedLabOrderGroups.customItems.length ? recommendedLabOrderGroups.customItems.slice(0, 6).map((item) => (
                              <Chip
                                key={item.id}
                                size="small"
                                variant="outlined"
                                color={labOrderTestIds.includes(item.id) ? "primary" : "default"}
                                label={item.testName}
                                clickable={!readOnly}
                                onClick={() => setLabOrderTestIds((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])}
                              />
                            )) : <Typography variant="caption" color="text.secondary">No matching active tests.</Typography>}
                          </Stack>
                        </Stack>
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      {recommendedTestSuggestions.slice(0, 6).map((test) => (
                        <Chip
                          key={test}
                          size="small"
                          variant="outlined"
                          color="secondary"
                          label={test}
                          clickable={!readOnly}
                          onClick={() => {
                            const exactMatch = labTests.find((row) => normalizeLookupKey(row.testName) === normalizeLookupKey(test));
                            if (exactMatch) {
                              setLabOrderTestIds((current) => current.includes(exactMatch.id) ? current : [...current, exactMatch.id]);
                            }
                          }}
                        />
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, lg: 5 }}>
              <Stack spacing={1.25}>
                <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                  <CardContent sx={{ p: 1.35 }}>
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <PlaylistAddCheckRoundedIcon fontSize="small" color="primary" />
                          <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Order Review</Typography>
                        </Stack>
                        <Chip size="small" variant="outlined" label={`${labOrderTestIds.length} selected`} />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        No auto-ordering. Review the selected tests, warnings, and sample requirements before creating the lab order.
                      </Typography>
                      {!labOrderTestIds.length ? (
                        <Alert severity="info">No lab orders yet. Select recommended tests or search tests to create an order.</Alert>
                      ) : (
                        <Stack spacing={0.75}>
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {selectedLabOrderTests.slice(0, 6).map((test) => (
                              <Chip
                                key={test.id}
                                size="small"
                                label={test.testName}
                                variant="outlined"
                                color="primary"
                                onDelete={readOnly ? undefined : () => setLabOrderTestIds((current) => current.filter((id) => id !== test.id))}
                              />
                            ))}
                          </Stack>
                          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                            <Chip size="small" variant="outlined" label={`Estimated cost: ${formatMoney(labOrderEstimatedCost)}`} />
                            <Chip size="small" variant="outlined" label={`Sample types: ${Array.from(new Set(selectedLabOrderTests.map((test) => test.sampleType || "Not specified"))).slice(0, 3).join(", ")}`} />
                          </Stack>
                          <Stack spacing={0.5}>
                            {selectedLabOrderTests.map((test) => {
                              const warning = labOrderReviewWarnings.get(test.id);
                              return (
                                <Stack key={test.id} direction="row" spacing={0.75} alignItems="flex-start">
                                  <Typography variant="body2" sx={{ fontWeight: 800, minWidth: 140 }}>{test.testName}</Typography>
                                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.35 }}>
                                    {warning || test.sampleType || "Sample not specified"}{test.turnaroundTime ? ` • TAT ${test.turnaroundTime}` : ""}{test.price != null ? ` • ${formatMoney(test.price)}` : ""}
                                  </Typography>
                                </Stack>
                              );
                            })}
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            {selectedLabOrderTests.some((test) => looksLikeFastingTest(test.testName, test.sampleType, test.category)) ? "Fasting may be required for one or more selected tests." : "No fasting requirement detected from current catalog data."}
                          </Typography>
                          {Array.from(labOrderReviewWarnings.values()).length ? (
                            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                              {Array.from(labOrderReviewWarnings.values()).slice(0, 4).map((warning) => (
                                <Chip key={warning} size="small" color="warning" variant="outlined" label={warning} />
                              ))}
                            </Stack>
                          ) : null}
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            <Button type="button" size="small" variant="outlined" onClick={openLabOrderDialog}>
                              Select more tests
                            </Button>
                            <Button type="button" size="small" variant="contained" disabled={!labOrderTestIds.length || readOnly} onClick={openLabOrderReview}>
                              Review &amp; Create Lab Order
                            </Button>
                          </Stack>
                        </Stack>
                      )}
                    </Stack>
                  </CardContent>
                </Card>

                <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                  <CardContent sx={{ p: 1.35 }}>
                    <Stack spacing={0.9}>
                      <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                          <CompareArrowsRoundedIcon fontSize="small" color="primary" />
                          <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Report Comparison</Typography>
                        </Stack>
                        <Button type="button" size="small" variant="outlined" onClick={() => setSelectedLabOrderId(selectedLabOrderId ? null : labOrders[0]?.id || null)} disabled={!labOrders.length}>
                          View order
                        </Button>
                      </Stack>
                      <StructuredTrendSummary
                        structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
                        legacyTrends={[]}
                        latestReport={null}
                        summary={null}
                        emptyStateLabel="No comparable reports are available yet."
                        emptyStateDetail="Structured comparison is available when previous numeric results exist."
                      />
                    </Stack>
                  </CardContent>
                </Card>
              </Stack>
            </Grid>
          </Grid>
          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.35, display: "flex", flexDirection: "column", minHeight: 0 }}>
                  <Stack spacing={0.9} sx={{ minHeight: 0 }}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <ReceiptLongRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Report / Test History</Typography>
                        <Chip size="small" variant="outlined" label={`${reportHistoryRows.length} report${reportHistoryRows.length === 1 ? "" : "s"}`} />
                      </Stack>
                      <Button size="small" variant="outlined" onClick={() => setUploadDialogOpen(true)}>Upload report / referral</Button>
                    </Stack>
                    <Box
                      role="region"
                      aria-label="Report and test history"
                      sx={{
                        display: "flex",
                        flexDirection: "column",
                        gap: 0.75,
                        minHeight: 260,
                        maxHeight: { xs: "60vh", sm: "52vh", md: "min(46vh, 480px)" },
                        overflowY: "auto",
                        overflowX: "hidden",
                        overscrollBehavior: "contain",
                        pr: 0.5,
                      }}
                    >
                      {!reportHistoryRows.length ? (
                        <Alert severity="info">No previous reports available. Upload an external report or order investigations to begin tracking trends.</Alert>
                      ) : (
                        <Stack spacing={0.75}>
                          {reportHistoryRows.map((row) => (
                            <Card key={row.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                              <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
                                <Stack spacing={0.4}>
                                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                    <Box sx={{ minWidth: 0 }}>
                                      <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={row.title || row.originalFilename}>
                                        {row.title || row.originalFilename}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary" noWrap>
                                        {documentTypeLabel(row.documentType)} • {row.reportDate || compactDate(row.createdAt)} • {isPublishedLabDocument(row) ? (documentBusinessStatusLabel(row) || "Published") : row.uploadSource}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                      <Chip size="small" variant="outlined" label={isPublishedLabDocument(row) ? "Published" : row.visibility} />
                                      {isPublishedLabDocument(row) ? null : <Chip size="small" variant="outlined" label={row.verificationStatus} />}
                                    </Stack>
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" noWrap title={row.description || row.originalFilename}>
                                    {row.description || "No notes"}
                                    {isPublishedLabDocument(row) ? "" : (row.aiExtractionSummary ? ` • AI: ${compactText(row.aiExtractionSummary, 42)}` : "")}
                                  </Typography>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Button size="small" variant="outlined" onClick={() => void openClinicalDocument(row)}>View report</Button>
                                  </Stack>
                                </Stack>
                              </CardContent>
                            </Card>
                          ))}
                        </Stack>
                      )}
                    </Box>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      {activeTab === 4 ? (
        <Stack spacing={1.5}>
          <WorkflowStrip text="Create Order → Payment → Sample → Result → Publish" />
          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.35 }}>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <ScienceRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Lab Order</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Select active lab tests for the current consultation.
                    </Typography>
                    <Button variant="contained" onClick={openLabOrderDialog} disabled={readOnly || !labTests.length}>
                      Review &amp; Create Lab Order
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      {labTests.length ? `${labTests.length} active tests available` : "No active tests configured."}
                    </Typography>
                    <Divider />
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Selected tests</Typography>
                    {labOrderTestIds.length ? (
                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        {selectedLabOrderTests.slice(0, 6).map((test) => (
                          <Chip key={test.id} size="small" variant="outlined" label={test.testName} />
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="caption" color="text.secondary">
                        No selected tests yet. Open the lab order dialog to create a request.
                      </Typography>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.35 }}>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <ScienceRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>Lab Orders</Typography>
                      </Stack>
                      <Chip size="small" variant="outlined" label={`${labOrders.length} order${labOrders.length === 1 ? "" : "s"}`} />
                    </Stack>
                    {!labOrders.length ? (
                      <Alert severity="info">No lab orders yet. Select recommended tests or search tests to create an order.</Alert>
                    ) : (
                      <Stack spacing={0.75}>
                        {labOrders.map((order) => (
                          <Card key={order.id} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                            <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                              <Stack spacing={0.55}>
                                <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="flex-start" flexWrap="wrap">
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }} noWrap>
                                      {order.orderNumber}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {compactDateTime(order.orderedAt)} • {order.items.map((item) => item.testName).join(", ")}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                    <Chip size="small" variant="outlined" color={labOrderReviewStatusColor(order.status)} label={labOrderReviewStatusLabel(order.status)} />
                                    <Chip size="small" variant="outlined" label={order.sampleSummaryStatus || "Sample status n/a"} />
                                    <Chip size="small" variant="outlined" label={order.billStatus || "Bill status n/a"} />
                                    <Chip size="small" variant="outlined" label={order.reportPublishedAt ? "Report Ready" : "Report Pending"} />
                                  </Stack>
                                </Stack>
                                <Typography variant="caption" color="text.secondary" noWrap title={`Bill ${order.billNumber || "-"} • Due ${order.billDueAmount != null ? order.billDueAmount : "-"}`}>
                                  Order no {order.orderNumber} • Bill {order.billNumber || "-"} • Due {order.billDueAmount != null ? formatMoney(order.billDueAmount) : "Not available"}
                                </Typography>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  <Button size="small" variant="outlined" onClick={() => setSelectedLabOrderId(order.id)}>
                                    View order
                                  </Button>
                                  <Button size="small" variant="outlined" disabled={!order.reportPublishedAt && !order.reportFilename} onClick={async () => {
                                    if (!auth.accessToken || !auth.tenantId) return;
                                    try {
                                      const { blob, filename } = await getLabOrderPdf(auth.accessToken, auth.tenantId, order.id);
                                      const url = URL.createObjectURL(blob);
                                      const anchor = document.createElement("a");
                                      anchor.href = url;
                                      anchor.download = filename;
                                      anchor.click();
                                      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
                                      setInfo("Lab report downloaded");
                                    } catch (err) {
                                      setError(err instanceof Error ? err.message : "Unable to open lab report");
                                    }
                                  }}>
                                    View report
                                  </Button>
                                  <Button size="small" variant="outlined" onClick={() => setSelectedLabOrderId(order.id)}>
                                    Compare report
                                  </Button>
                                  <Button size="small" variant="outlined" onClick={() => setUploadDialogOpen(true)}>
                                    Add/upload report
                                  </Button>
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      {activeTab === 5 ? (
        <Grid container spacing={1.5} alignItems="stretch">
          <Grid size={{ xs: 12 }}>
            <WorkflowStrip text="Review Context → Ask AI → Review Draft → Accept" />
          </Grid>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, height: "100%" }}>
              <CardContent sx={{ p: 1.5 }}>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Clinical Chat</Typography>
                    </Stack>
                    <Chip size="small" variant="outlined" color={aiAssistantEnabled ? "success" : "warning"} label={aiAssistantEnabled ? "AI ready" : "AI unavailable"} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Ask AIVA about this consultation. Review every response before using it.
                  </Typography>
                  {!aiAssistantEnabled ? aiUnavailableCard : null}
                  <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                    <CardContent sx={{ p: 1 }}>
                      <Stack spacing={1}>
                        <Stack spacing={0.75} sx={{ maxHeight: 260, overflow: "auto" }}>
                          {aivaChatMessages.length ? (
                            aivaChatMessages.map((message) => (
                              <Box
                                key={message.id}
                                sx={{
                                  display: "flex",
                                  justifyContent: message.role === "DOCTOR" ? "flex-end" : "flex-start",
                                }}
                              >
                                <Box
                                  sx={{
                                    maxWidth: "88%",
                                    p: 1,
                                    borderRadius: 2,
                                    border: 1,
                                    borderColor: message.role === "DOCTOR" ? "primary.light" : "divider",
                                    bgcolor: message.role === "DOCTOR" ? "primary.50" : "background.paper",
                                  }}
                                >
                                  <Stack spacing={0.5}>
                                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                      <Typography variant="caption" sx={{ fontWeight: 900 }}>
                                        {message.role === "DOCTOR" ? "Doctor" : "AIVA"}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {compactDateTime(message.createdAt)}
                                      </Typography>
                                    </Stack>
                                    <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                      {message.text}
                                    </Typography>
                                    {message.role === "AIVA" && message.text.trim() ? (
                                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        <Button type="button" size="small" variant="outlined" disabled={!message.text.trim()} onClick={() => void navigator.clipboard.writeText(message.text)}>
                                          Copy
                                        </Button>
                                        <Button type="button" size="small" variant="outlined" disabled={readOnly || !message.text.trim()} onClick={() => setConsultationForm((current) => ({ ...current, clinicalNotes: appendTokenLine(current.clinicalNotes, message.text) }))}>
                                          Add to SOAP
                                        </Button>
                                        <Button type="button" size="small" variant="outlined" disabled={readOnly || !message.text.trim()} onClick={() => appendAdviceAndReveal(message.text)}>
                                          Add to Advice
                                        </Button>
                                      </Stack>
                                    ) : null}
                                  </Stack>
                                </Box>
                              </Box>
                            ))
                          ) : (
                            <Box sx={{ p: 1.2, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                              <Typography variant="body2" color="text.secondary">
                                Ask anything about this consultation. Use the prompt shortcuts below for common questions.
                              </Typography>
                            </Box>
                          )}
                        </Stack>
                        <Stack spacing={0.75}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prompt shortcuts</Typography>
                          <Stack spacing={0.75}>
                            {AI_ASSIST_PROMPT_GROUPS.map((group) => (
                              <Stack key={group.label} spacing={0.5}>
                                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800, textTransform: "uppercase", letterSpacing: 0.3 }}>
                                  {group.label}
                                </Typography>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {group.prompts.map((prompt) => (
                                    <Chip
                                      key={prompt}
                                      size="small"
                                      variant="outlined"
                                      clickable={!aiBusy && !aivaQuestionSubmitting && aiAssistantEnabled}
                                      disabled={aiBusy || aivaQuestionSubmitting || !aiAssistantEnabled}
                                      label={prompt}
                                      onClick={() => setAivaClinicalQuestion(prompt)}
                                    />
                                  ))}
                                </Stack>
                              </Stack>
                            ))}
                            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                              <Chip size="small" icon={<PsychologyRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAssistantEnabled} disabled={aiBusy || !aiAssistantEnabled} variant="outlined" label="Suggest diagnosis" onClick={() => void runAiAction("diagnosis")} />
                              <Chip size="small" icon={<DescriptionRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAssistantEnabled} disabled={aiBusy || !aiAssistantEnabled} variant="outlined" label="Structure SOAP notes" onClick={() => void runAiAction("notes")} />
                              <Chip size="small" icon={<MedicationRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAssistantEnabled} disabled={aiBusy || !aiAssistantEnabled} variant="outlined" label="Prescription template" onClick={() => void applyAiPrescriptionTemplate()} />
                              <Chip size="small" icon={<TipsAndUpdatesRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAssistantEnabled} disabled={aiBusy || !aiAssistantEnabled} variant="outlined" label="Patient instructions" onClick={() => void runAiAction("instructions")} />
                            </Stack>
                          </Stack>
                        </Stack>
                        <TextField
                          size="small"
                          fullWidth
                          label="Ask anything about this consultation"
                          placeholder="Ask anything about this consultation..."
                          value={aivaClinicalQuestion}
                          disabled={aiBusy || aivaQuestionSubmitting || readOnly || !aiAssistantEnabled}
                          multiline
                          minRows={2}
                          onChange={(event) => setAivaClinicalQuestion(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter" && !event.shiftKey) {
                              event.preventDefault();
                              void submitAivaQuestion();
                            }
                          }}
                        />
                        <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                          <Button type="button" size="small" variant="contained" disabled={!aiAssistantEnabled || !canAskAiva || aivaQuestionSubmitting || aiBusy} onClick={() => void submitAivaQuestion()}>
                            Ask
                          </Button>
                          <Button type="button" size="small" variant="outlined" disabled={!aivaChatMessages.length} onClick={() => setAivaChatDetailsOpen((current) => !current)}>
                            {aivaChatDetailsOpen ? "Hide Transcript" : "Show Transcript"}
                          </Button>
                        </Stack>
                        <Collapse in={aivaChatDetailsOpen && aivaChatMessages.length > 0} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }} unmountOnExit>
                          <Stack spacing={0.75}>
                            <Typography variant="caption" color="text.secondary">
                              Doctor and AIVA messages stay in this session until the consultation is closed.
                            </Typography>
                          </Stack>
                        </Collapse>
                        {aiBusy ? <Typography variant="caption" color="text.secondary">AI assistance is preparing suggestions...</Typography> : null}
                      </Stack>
                    </CardContent>
                  </Card>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Stack spacing={1.5}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, height: "100%" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <InsightsRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>Consultation Snapshot</Typography>
                      </Stack>
                      <Chip size="small" variant="outlined" label={aiAssistantEnabled ? "Consultation context" : "AI unavailable"} />
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Deterministic read-only summary from the current consultation and patient context.
                    </Typography>
                    <Stack spacing={1}>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Current visit</Typography>
                        <Typography variant="body2"><b>Chief complaint:</b> {consultationForm.chiefComplaints.trim() || consultation?.chiefComplaints || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Symptoms:</b> {consultationForm.symptoms.trim() || consultation?.symptoms || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Status:</b> {consultation?.status || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Visit date:</b> {consultation?.createdAt ? compactDateTime(consultation.createdAt) : "Not recorded"}</Typography>
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Diagnoses</Typography>
                        {selectedDiagnosisEntries.length ? (
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                            {selectedDiagnosisEntries.map((item) => <Chip key={item} size="small" variant="outlined" label={item} />)}
                          </Stack>
                        ) : (
                          <Typography variant="body2" color="text.secondary">Not recorded yet</Typography>
                        )}
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prescription</Typography>
                        <Typography variant="body2"><b>Status:</b> {prescription?.status || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Medicines:</b> {(prescription?.medicines?.length ?? prescriptionForm.medicines.filter((row) => medicineRowHasAnyContent(row)).length) || 0}</Typography>
                        <Typography variant="body2"><b>Medication safety:</b> {medicationSafetyReview?.decisionStatus || (medicationSafetyEvaluation ? "Evaluated" : "Not run")}</Typography>
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Investigations</Typography>
                        <Typography variant="body2"><b>Orders:</b> {labOrders.length || 0}</Typography>
                        <Typography variant="body2"><b>Reports:</b> {clinicalDocuments.length || 0}</Typography>
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>SOAP</Typography>
                        <Typography variant="body2"><b>Status:</b> {consultationSoap ? (consultationSoap.stale ? "Review recommended" : consultationSoap.status === "ACCEPTED" ? "Accepted/current" : consultationSoap.status === "SUPERSEDED" ? "Superseded" : "Saved") : clinicalAiDrafts.soap.generatedAt ? "Pending draft" : "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Source:</b> {consultationSoap?.source || (clinicalAiDrafts.soap.generatedAt ? "AI_DRAFT" : "Not recorded")}</Typography>
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinical reasoning</Typography>
                        <Typography variant="body2"><b>Status:</b> {clinicalReasoningStatusLabel}</Typography>
                        <Typography variant="body2"><b>Provider:</b> {clinicalReasoningResult?.provider || "Not available"}</Typography>
                      </Stack>
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Important context</Typography>
                        <Typography variant="body2"><b>Allergies:</b> {aiAllergiesSummary || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Chronic conditions:</b> {aiChronicConditionsSummary || clinicalSnapshotConditions || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Vitals:</b> {soapVitalsSummary || "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Longitudinal trends:</b> {clinicalContext?.longitudinalClinicalContext?.labTrends?.length ? `${clinicalContext.longitudinalClinicalContext.labTrends.length} available` : "Not recorded"}</Typography>
                        <Typography variant="body2"><b>Last AI activity:</b> {lastAiActivityLabel || "Not recorded"}</Typography>
                      </Stack>
                      <Stack spacing={0.75}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Context available to AIVA</Typography>
                        <Typography variant="caption" color="text.secondary">
                          AIVA only assists with clinician-entered and available patient context. Doctor verification is required before use.
                        </Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {[
                            consultationForm.chiefComplaints.trim() || consultation?.chiefComplaints ? "Chief complaint" : null,
                            consultationForm.symptoms.trim() || consultation?.symptoms ? "Symptoms" : null,
                            soapVitalsSummary ? "Vitals" : null,
                            selectedDiagnosisEntries.length ? "Diagnoses" : null,
                            aiAllergiesSummary ? "Allergies" : null,
                            aiChronicConditionsSummary ? "Chronic conditions" : null,
                            clinicalContext?.longitudinalClinicalContext?.labTrends?.length ? "Longitudinal trends" : null,
                            prescription?.medicines?.length || prescriptionForm.medicines.some((row) => medicineRowHasAnyContent(row)) ? "Current prescription" : null,
                            clinicalDocuments.length || labOrders.length ? "Existing reports" : null,
                          ].filter(Boolean).map((label) => (
                            <Chip key={String(label)} size="small" variant="outlined" label={String(label)} />
                          ))}
                        </Stack>
                      </Stack>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <InsightsRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>AI Clinical Summary</Typography>
                      </Stack>
                      <Button type="button" size="small" variant="outlined" disabled={aiBusy || !aiAssistantEnabled} onClick={() => void generateClinicalSummary()}>
                        Generate summary
                      </Button>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Generated summary is assistive only. The consultation snapshot above remains the deterministic source of truth.
                    </Typography>
                    {aiSummaryHasState ? (
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" alignItems="center">
                          <Button size="small" variant="text" onClick={() => setAiClinicalSummaryExpanded((current) => !current)}>
                            {aiClinicalSummaryExpanded ? "Hide AI Clinical Summary" : "Show AI Clinical Summary"}
                          </Button>
                          {aiSummaryProviderLabel ? <Chip size="small" variant="outlined" label={`Provider: ${aiSummaryProviderLabel}`} /> : null}
                          {aiSummaryModelLabel ? <Chip size="small" variant="outlined" label={`Model: ${aiSummaryModelLabel}`} /> : null}
                          {aiSummaryGeneratedAtLabel ? <Chip size="small" variant="outlined" label={`Generated: ${compactDateTime(aiSummaryGeneratedAtLabel)}`} /> : null}
                          {clinicalSummary ? <Chip size="small" color={clinicalSummary.fallbackUsed ? "warning" : "success"} label={clinicalSummary.fallbackUsed ? "Fallback used" : "AI ready"} /> : null}
                        </Stack>
                        <Collapse in={aiClinicalSummaryExpanded} timeout={200} easing={{ enter: "ease-in-out", exit: "ease-in-out" }}>
                          <Stack spacing={1}>
                            <Alert severity="info">
                              AI suggestions are assistive only. Doctor must verify before use.
                            </Alert>
                            {aiSummaryHasContent ? (
                              <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1, bgcolor: "background.paper", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                                <Typography variant="body2" sx={{ lineHeight: 1.55 }}>{aiSummaryText}</Typography>
                              </Box>
                            ) : (
                              <Alert severity="info" sx={{ py: 0.5 }}>
                                No AI summary available yet.
                              </Alert>
                            )}
                            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                              <Button type="button" size="small" variant="outlined" disabled={!aiSummaryText} onClick={() => void copyAiSummaryToClipboard()}>Copy to Summary</Button>
                              <Button type="button" size="small" variant="outlined" disabled={!aiSummaryText || readOnly} onClick={applyAiSummaryToConsultationNotes}>Apply to Consultation Notes</Button>
                            </Stack>
                            {clinicalSummary ? (
                              <Stack spacing={1}>
                                {Array.isArray(clinicalSummary.structuredData["possibleDiagnosisCategories"]) && (clinicalSummary.structuredData["possibleDiagnosisCategories"] as Array<unknown>).length ? (
                                  <Card variant="outlined" sx={{ boxShadow: "none" }}>
                                    <CardContent sx={{ p: 1 }}>
                                      <Typography variant="subtitle2">Possible Diagnosis Categories</Typography>
                                      {(clinicalSummary.structuredData["possibleDiagnosisCategories"] as Array<Record<string, unknown>>).map((row, idx) => (
                                        <Typography key={idx} variant="caption" display="block">
                                          {String(row.name || "Category")} - {String(row.reason || "")} {row.confidence ? `(${String(row.confidence)})` : ""}
                                        </Typography>
                                      ))}
                                    </CardContent>
                                  </Card>
                                ) : null}
                                {Array.isArray(clinicalSummary.structuredData["recommendedInvestigations"]) && (clinicalSummary.structuredData["recommendedInvestigations"] as Array<unknown>).length ? (
                                  <Card variant="outlined" sx={{ boxShadow: "none" }}>
                                    <CardContent sx={{ p: 1 }}>
                                      <Typography variant="subtitle2">Recommended Investigations</Typography>
                                      <Typography variant="caption">{(clinicalSummary.structuredData["recommendedInvestigations"] as Array<unknown>).map((v) => String(v)).join(" • ")}</Typography>
                                    </CardContent>
                                  </Card>
                                ) : null}
                                {Array.isArray(clinicalSummary.structuredData["followUpSuggestions"]) && (clinicalSummary.structuredData["followUpSuggestions"] as Array<unknown>).length ? (
                                  <Card variant="outlined" sx={{ boxShadow: "none" }}>
                                    <CardContent sx={{ p: 1 }}>
                                      <Typography variant="subtitle2">Follow-up Suggestions</Typography>
                                      <Typography variant="caption">{(clinicalSummary.structuredData["followUpSuggestions"] as Array<unknown>).map((v) => String(v)).join(" • ")}</Typography>
                                    </CardContent>
                                  </Card>
                                ) : null}
                                {Array.isArray(clinicalSummary.structuredData["safetyNotes"]) && (clinicalSummary.structuredData["safetyNotes"] as Array<unknown>).some((v) => String(v).includes("unstructured")) ? (
                                  <Alert severity="warning">AI returned unstructured text. Review before use.</Alert>
                                ) : null}
                                {clinicalSummary.suggestedActions?.length ? <Typography variant="caption" color="text.secondary">{clinicalSummary.suggestedActions.join(" · ")}</Typography> : null}
                                {clinicalSummary.warnings?.length ? <Typography variant="caption" color="text.secondary">{clinicalSummary.warnings.join(" · ")}</Typography> : null}
                              </Stack>
                            ) : null}
                          </Stack>
                        </Collapse>
                      </Stack>
                    ) : (
                      <Alert severity="info" sx={{ py: 0.5 }}>
                        No AI clinical summary available yet.
                      </Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={labOrderDialogOpen} onClose={closeLabOrderDialog} fullWidth maxWidth="md">
        <DialogTitle>Select Lab Tests</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5} sx={{ pt: 0.5 }}>
            <Alert severity="info" sx={{ py: 0.45 }}>
              <Typography variant="caption" sx={{ fontWeight: 700, lineHeight: 1.3, letterSpacing: 0.1 }}>
                Review history  →  Select tests  →  Check duplicates  →  Confirm order  →  Track report
              </Typography>
            </Alert>
            {labOrderAiPreparation ? (
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5, borderColor: "secondary.light" }}>
                <CardContent sx={{ p: 1.15, "&:last-child": { pb: 1.15 } }}>
                  <Stack spacing={1.1}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack spacing={0.35}>
                        <Stack direction="row" spacing={0.75} alignItems="center">
                        <AutoAwesomeRoundedIcon fontSize="small" color="secondary" />
                          <Typography variant="subtitle1" sx={{ fontWeight: 900, color: "secondary.main" }}>
                            AI Recommended Laboratory Investigations
                          </Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.35 }}>
                          Prepared from current consultation. Doctor review and confirmation are required before creating any laboratory request.
                        </Typography>
                      </Stack>
                      <Tooltip title={compactDateTime(labOrderAiPreparation.generatedAt)} arrow>
                        <Chip size="small" variant="outlined" label={`Generated: ${labOrderAiGeneratedRelativeLabel}`} />
                      </Tooltip>
                    </Stack>
                    <Alert severity="info" sx={{ py: 0.45 }}>
                      AI prepared these investigations based on current consultation. Doctor review and confirmation are required before any laboratory request is created.
                    </Alert>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {labOrderAiPreparation.investigations.map((testName) => (
                        <Chip
                          key={testName}
                          size="small"
                          variant="outlined"
                          color="secondary"
                          icon={getInvestigationIcon(testName)}
                          label={testName}
                          sx={{ "& .MuiChip-icon": { fontSize: 16, color: "text.secondary" } }}
                        />
                      ))}
                      <Chip
                        size="small"
                        variant="outlined"
                        color={labOrderAiPreparation.confidence?.toUpperCase() === "HIGH" ? "success" : labOrderAiPreparation.confidence?.toUpperCase() === "LOW" ? "error" : "warning"}
                        label={labOrderAiPreparation.confidence === "HIGH" ? "High" : labOrderAiPreparation.confidence === "LOW" ? "Low" : "Medium"}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        color={labOrderAiPreparation.suggestedPriority.toUpperCase() === "STAT" ? "error" : labOrderAiPreparation.suggestedPriority.toUpperCase() === "URGENT" ? "warning" : "info"}
                        label={labOrderAiPreparation.suggestedPriority}
                      />
                    </Stack>
                    <Stack spacing={0.45}>
                      <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: 0.4, fontWeight: 800 }}>
                        Reason
                      </Typography>
                      <Typography variant="body2" sx={{ lineHeight: 1.4 }}>
                        {labOrderAiPreparation.reason}
                      </Typography>
                    </Stack>
                    <Stack spacing={0.45}>
                      <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: 0.4, fontWeight: 800 }}>
                        Supporting evidence
                      </Typography>
                      <Stack spacing={0.55}>
                        {labOrderAiPreparation.supportingEvidence.length ? labOrderAiPreparation.supportingEvidence.slice(0, 6).map((item) => (
                          <Stack key={item} direction="row" spacing={0.6} alignItems="flex-start">
                            <CheckCircleRoundedIcon fontSize="inherit" color="success" sx={{ mt: 0.15, fontSize: 16 }} />
                            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }}>
                              {item}
                            </Typography>
                          </Stack>
                        )) : (
                          <Typography variant="body2" color="text.secondary">
                            Clinical judgement required.
                          </Typography>
                        )}
                      </Stack>
                    </Stack>
                    {labOrderAiPreparation.duplicateWarnings.length ? (
                      <Alert severity="warning" sx={{ py: 0.45 }}>
                        <Stack spacing={0.2}>
                          <Typography variant="body2" sx={{ fontWeight: 800 }}>
                            Existing result/order detected.
                          </Typography>
                          {labOrderAiPreparation.duplicateWarnings.map((warning) => (
                            <Typography key={warning} variant="caption" color="text.secondary">
                              {warning}
                            </Typography>
                          ))}
                        </Stack>
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}
            <TextField
              fullWidth
              size="small"
              label="Clinical Order Notes"
              value={labOrderNotes}
              onChange={(e) => setLabOrderNotes(e.target.value)}
              multiline
              minRows={2}
              placeholder="Persistent fever in a diabetic patient."
              helperText="Editable by the doctor before laboratory request creation."
            />
            {labOrderTestIds.length ? (
              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                {selectedLabOrderTests.slice(0, 8).map((test) => <Chip key={test.id} size="small" variant="outlined" label={test.testName} />)}
              </Stack>
            ) : (
              <Stack spacing={0.45} alignItems="center" justifyContent="center" sx={{ py: 1.5, textAlign: "center" }}>
                <PlaylistAddCheckRoundedIcon fontSize="small" color="disabled" />
                <Typography variant="body2" color="text.secondary">No laboratory tests selected.</Typography>
                <Typography variant="caption" color="text.secondary">Select AI recommendations or search manually below.</Typography>
              </Stack>
            )}
            {labTests.length ? (
              <Stack spacing={1}>
                <Stack spacing={0.75}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={0.75} alignItems={{ xs: "stretch", sm: "center" }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Search laboratory tests"
                      placeholder="Search by test name, code, department, category, or specimen"
                      value={labTestSearch}
                      onChange={(e) => setLabTestSearch(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Escape" && labTestSearch.trim()) {
                          e.preventDefault();
                          setLabTestSearch("");
                          setDebouncedLabTestSearch("");
                          setLabTestCategoryFilter("All");
                        }
                      }}
                    />
                    <Button
                      type="button"
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        setLabTestSearch("");
                        setDebouncedLabTestSearch("");
                        setLabTestCategoryFilter("All");
                      }}
                      disabled={!labTestSearch.trim() && labTestCategoryFilter === "All"}
                    >
                      Clear Search
                    </Button>
                  </Stack>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {labTestFilterOptions.map((option) => (
                      <Chip
                        key={option.value}
                        size="small"
                        clickable
                        color={labTestCategoryFilter === option.value ? "primary" : "default"}
                        variant={labTestCategoryFilter === option.value ? "filled" : "outlined"}
                        label={option.label}
                        onClick={() => setLabTestCategoryFilter(option.value)}
                      />
                    ))}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    {labTestResultCountLabel}
                  </Typography>
                </Stack>
                <Box sx={{ maxHeight: 360, overflow: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.5 }}>
                  <Stack spacing={1}>
                    {filteredLabTests.map((test) => {
                  const selected = labOrderTestIds.includes(test.id);
                  return (
                    <Card key={test.id} variant="outlined" sx={{ boxShadow: "none", borderColor: selected ? "primary.main" : "divider" }}>
                      <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="flex-start">
                          <Box>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{test.testName}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {[test.testCode, test.department, test.category, test.sampleType || "Sample not specified", test.tenantTatOverride || test.turnaroundTime || "TAT not set", formatMoney(test.tenantPriceOverride ?? test.price ?? null)].filter(Boolean).join(" • ")}
                            </Typography>
                          </Box>
                          <Tooltip title={selected ? "Remove this test from the prepared laboratory order." : "Add this test to the prepared laboratory order."} arrow>
                            <Button
                              size="small"
                              variant={selected ? "contained" : "outlined"}
                              onClick={() => setLabOrderTestIds((current) => selected ? current.filter((id) => id !== test.id) : [...current, test.id])}
                            >
                              {selected ? "Selected" : "Select"}
                            </Button>
                          </Tooltip>
                        </Stack>
                      </CardContent>
                    </Card>
                  );
                })}
                    {!filteredLabTests.length ? (
                      <Stack spacing={0.75} alignItems="center" justifyContent="center" sx={{ py: 3, textAlign: "center" }}>
                        <Typography variant="body2" color="text.secondary">
                          No laboratory tests match your search.
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Try another test name, code, department, or category.
                        </Typography>
                        <Button
                          type="button"
                          size="small"
                          variant="outlined"
                          onClick={() => {
                            setLabTestSearch("");
                            setDebouncedLabTestSearch("");
                            setLabTestCategoryFilter("All");
                          }}
                        >
                          Clear Search
                        </Button>
                      </Stack>
                    ) : null}
                  </Stack>
                </Box>
              </Stack>
            ) : (
              <Alert severity="info">
                No laboratory tests are currently configured.
                <br />
                Contact the clinic administrator to configure the test catalog.
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeLabOrderDialog}>Cancel</Button>
          <Button variant="outlined" disabled={labOrderSaving || !labOrderTestIds.length} onClick={openLabOrderReview}>
            Review &amp; Create Lab Order
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={labOrderReviewOpen} onClose={() => setLabOrderReviewOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Review &amp; Create Lab Order</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.25} sx={{ pt: 0.5 }}>
            <Typography variant="body2" color="text.secondary">
              Selected tests, duplicate warnings, and order notes are shown below. Create the order only after doctor review.
            </Typography>
            {!selectedLabOrderTests.length ? (
              <Alert severity="info">No lab orders yet. Select recommended tests or search tests to create an order.</Alert>
            ) : (
              <Stack spacing={0.75}>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                  {selectedLabOrderTests.map((test) => (
                    <Chip
                      key={test.id}
                      size="small"
                      label={test.testName}
                      variant="outlined"
                      color={labOrderReviewWarnings.has(test.id) ? "warning" : "primary"}
                    />
                  ))}
                </Stack>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip size="small" variant="outlined" label={`Estimated cost: ${formatMoney(labOrderEstimatedCost)}`} />
                  <Chip size="small" variant="outlined" label={`Sample types: ${Array.from(new Set(selectedLabOrderTests.map((test) => test.sampleType || "Not specified"))).slice(0, 3).join(", ")}`} />
                  <Chip size="small" variant="outlined" label={selectedLabOrderTests.some((test) => looksLikeFastingTest(test.testName, test.sampleType, test.category)) ? "Fasting may be required" : "No fasting requirement detected"} />
                </Stack>
                <Stack spacing={0.5}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Duplicate / recent warnings</Typography>
                  {Array.from(labOrderReviewWarnings.values()).length ? (
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {Array.from(labOrderReviewWarnings.values()).map((warning) => <Chip key={warning} size="small" variant="outlined" color="warning" label={warning} />)}
                    </Stack>
                  ) : (
                    <Typography variant="caption" color="text.secondary">No duplicate warnings from current consultation context.</Typography>
                  )}
                </Stack>
                <Stack spacing={0.4}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Selected tests</Typography>
                  {selectedLabOrderTests.map((test) => (
                    <Stack key={test.id} direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{test.testName}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ textAlign: "right" }}>
                        {test.sampleType || "Sample not specified"}{test.turnaroundTime ? ` • TAT ${test.turnaroundTime}` : ""}{test.price != null ? ` • ${formatMoney(test.price)}` : ""}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: "pre-line" }}>
                  {labOrderNotes.trim() ? `Notes:\n${labOrderNotes.trim()}` : "No notes added."}
                </Typography>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLabOrderReviewOpen(false)}>Back</Button>
          <Button variant="contained" disabled={labOrderSaving || !labOrderTestIds.length} onClick={() => void submitLabOrder()}>
            Create Order
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={Boolean(selectedLabOrder)} onClose={() => setSelectedLabOrderId(null)} fullWidth maxWidth="sm">
        <DialogTitle>Lab Order Details</DialogTitle>
        <DialogContent dividers>
          {selectedLabOrder ? (
            <Stack spacing={1.25} sx={{ pt: 0.5 }}>
              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                <Chip size="small" variant="outlined" label={selectedLabOrder.orderNumber} />
                <Chip size="small" variant="outlined" color={labOrderReviewStatusColor(selectedLabOrder.status)} label={labOrderReviewStatusLabel(selectedLabOrder.status)} />
                <Chip size="small" variant="outlined" label={selectedLabOrder.reportPublishedAt ? "Report Ready" : "Report Pending"} />
                <Chip size="small" variant="outlined" label={selectedLabOrder.sampleSummaryStatus || "Sample status n/a"} />
                <Chip size="small" variant="outlined" label={selectedLabOrder.billStatus || "Bill status n/a"} />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {compactDateTime(selectedLabOrder.orderedAt)} • {selectedLabOrder.items.map((item) => item.testName).join(", ")}
              </Typography>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Tests</Typography>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                  {selectedLabOrder.items.map((item) => (
                    <Chip key={item.id} size="small" variant="outlined" label={item.testName} />
                  ))}
                </Stack>
              </Stack>
              <Stack spacing={0.5}>
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Billing and sample</Typography>
                <Typography variant="caption" color="text.secondary">
                  Order no {selectedLabOrder.orderNumber} • Bill {selectedLabOrder.billNumber || "-"} • Due {selectedLabOrder.billDueAmount != null ? formatMoney(selectedLabOrder.billDueAmount) : "Not available"}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Sample status {selectedLabOrder.sampleSummaryStatus || "n/a"} • Report status {selectedLabOrder.reportPublishedAt ? "Published" : "Pending"}
                </Typography>
              </Stack>
              {selectedLabOrder.notes ? (
                <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                  <b>Notes:</b> {selectedLabOrder.notes}
                </Typography>
              ) : null}
              <Card variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 1 }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <TrendingUpRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Report comparison</Typography>
                    </Stack>
                    <StructuredTrendSummary
                      structuredTrends={clinicalContext?.longitudinalClinicalContext?.labTrends || []}
                      legacyTrends={clinicalContext?.labIntelligence.previousTrends || []}
                      latestReport={clinicalContext?.labIntelligence.latestLabReport || null}
                      summary={clinicalContext?.aiSummary || null}
                    />
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedLabOrderId(null)}>Close</Button>
          <Button variant="outlined" onClick={() => setUploadDialogOpen(true)}>
            Add/upload report
          </Button>
          <Button
            variant="outlined"
            disabled={!selectedLabOrder || (!selectedLabOrder.reportPublishedAt && !selectedLabOrder.reportFilename)}
            onClick={async () => {
              if (!selectedLabOrder || !auth.accessToken || !auth.tenantId) return;
              try {
                const { blob, filename } = await getLabOrderPdf(auth.accessToken, auth.tenantId, selectedLabOrder.id);
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement("a");
                anchor.href = url;
                anchor.download = filename;
                anchor.click();
                window.setTimeout(() => URL.revokeObjectURL(url), 60000);
                setInfo("Lab report downloaded");
              } catch (err) {
                setError(err instanceof Error ? err.message : "Unable to open lab report");
              }
            }}
          >
            View report
          </Button>
        </DialogActions>
      </Dialog>

      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="flex-end">
        {canEditConsultation ? <Button type="button" variant="outlined" color="error" disabled={saving || readOnly} onClick={() => void cancelCurrentConsultation()}>Cancel Consultation</Button> : null}
        {canFinalizePrescription ? <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("email")}>Email Rx</Button> : null}
        {canFinalizePrescription ? <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("whatsapp")}>WhatsApp Rx</Button> : null}
      </Stack>
      {workspaceConfirmation?.kind === "fastPack" ? (
        <ConfirmationDialog
          open
          title="Replace Prescription Draft?"
          confirmLabel="Apply Fast Pack"
          cancelLabel="Cancel"
          confirmColor="secondary"
          onCancel={() => setWorkspaceConfirmation(null)}
          onConfirm={() => {
            workspaceConfirmation.onConfirm();
            setWorkspaceConfirmation(null);
          }}
        >
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-line" }}>
            {workspaceConfirmation.hasContent
              ? `Applying this Fast Pack will:
- Replace the current prescription draft
- Keep manual rows unchanged
- Preserve consultation notes
- Require doctor review before finalization`
              : "The selected Fast Pack will populate the prescription with recommended medicines."}
          </Typography>
        </ConfirmationDialog>
      ) : null}
      {workspaceConfirmation?.kind === "overwrite" ? (
        <ConfirmationDialog
          open
          title={`Replace ${workspaceConfirmation.label}?`}
          description={`Replace existing ${workspaceConfirmation.label.toLowerCase()} or append the AI suggestion?`}
          confirmLabel="Replace"
          cancelLabel={workspaceConfirmation.allowAppend ? "Cancel" : "Close"}
          confirmColor="secondary"
          onCancel={() => workspaceConfirmation.onResolve("cancel")}
          onConfirm={() => workspaceConfirmation.onResolve("replace")}
        >
          {workspaceConfirmation.allowAppend ? (
            <Button type="button" variant="text" onClick={() => workspaceConfirmation.onResolve("append")} sx={{ mt: 0.5 }}>
              Append instead
            </Button>
          ) : null}
        </ConfirmationDialog>
      ) : null}
      {workspaceConfirmation?.kind === "confirm" ? (
        <ConfirmationDialog
          open
          title={workspaceConfirmation.title}
          description={workspaceConfirmation.description}
          confirmLabel={workspaceConfirmation.confirmLabel}
          confirmColor={workspaceConfirmation.confirmColor}
          onCancel={() => {
            workspaceConfirmation.onCancel?.();
            setWorkspaceConfirmation(null);
          }}
          onConfirm={() => {
            workspaceConfirmation.onConfirm();
            setWorkspaceConfirmation(null);
          }}
        />
      ) : null}
      <Drawer
        anchor="right"
        open={completionValidationOpen}
        onClose={() => setCompletionValidationOpen(false)}
        PaperProps={{
          sx: {
            width: { xs: "100%", sm: 400, md: 460 },
            maxWidth: "100%",
            p: 1.25,
          },
        }}
      >
        <Stack spacing={1.1} sx={{ height: "100%" }}>
          <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between">
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                Completion Validation
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Guidance only. This does not change completion rules.
              </Typography>
            </Box>
            <Button type="button" size="small" variant="text" onClick={() => setCompletionValidationOpen(false)}>
              Close
            </Button>
          </Stack>
          <Alert severity={consultationCompletionSummary.statusTone === "error" ? "error" : consultationCompletionSummary.statusTone === "warning" ? "warning" : "success"} sx={{ py: 0.5 }}>
            {consultationCompletionSummary.statusLabel}
          </Alert>
          <Stack spacing={0.65}>
            {consultationCompletionSummary.groups.map((group) => (
              <Card key={group.title} variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                  <Stack spacing={0.65}>
                    <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between" flexWrap="wrap">
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {group.title}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                          {group.helper}
                        </Typography>
                      </Box>
                      <Chip size="small" variant="outlined" color={group.summaryTone} label={group.summaryLabel} />
                    </Stack>
                    <Stack spacing={0.5}>
                      {group.items.map((item) => (
                        <Stack key={item.label} direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {item.label}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                              {item.detail}
                            </Typography>
                          </Box>
                          <Chip size="small" variant="outlined" color={item.tone} label={item.stateLabel} />
                        </Stack>
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            ))}
          </Stack>
          <Divider />
          <Stack spacing={0.65}>
            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
              Final consultation audit
            </Typography>
            {consultationAuditShareLog.length ? (
              <Stack spacing={0.5}>
                {consultationAuditShareLog.slice(0, 8).map((item) => (
                  <Chip key={item} size="small" variant="outlined" label={item} />
                ))}
              </Stack>
            ) : (
              <Typography variant="caption" color="text.secondary">
                No patient communication actions recorded yet.
              </Typography>
            )}
          </Stack>
        </Stack>
      </Drawer>
      <Dialog open={completeConsultationDialogOpen} onClose={() => setCompleteConsultationDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Complete consultation?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Consultation notes become read-only after completion.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button type="button" onClick={() => setCompleteConsultationDialogOpen(false)}>Cancel</Button>
          <Button
            type="button"
            variant="contained"
            color="secondary"
            disabled={saving || !prescriptionReadyForCompletion}
            onClick={async () => {
              setCompleteConsultationDialogOpen(false);
              await completeCurrentConsultation();
            }}
          >
            Complete Consultation
          </Button>
        </DialogActions>
      </Dialog>
      <PatientDocumentUploadDialog
        open={uploadDialogOpen}
        onClose={() => setUploadDialogOpen(false)}
        defaultUploadSource="DOCTOR"
        consultationId={consultation?.id || consultationId}
        title="Upload patient document"
        onSubmit={async (body) => {
          if (!auth.accessToken || !auth.tenantId || !consultation) return;
          await uploadPatientDocument(auth.accessToken, auth.tenantId, consultation.patientId, {
            ...body,
            consultationId: consultation.id,
            sourceModule: "CONSULTATION",
            sourceEntityId: consultation.id,
          });
          await refreshClinicalArtifacts(consultation.patientId, consultation);
        }}
      />
      <ClinicalDocumentViewer
        open={!!viewerDocument}
        document={viewerDocument}
        url={viewerUrl}
        onClose={() => { setViewerDocument(null); setViewerUrl(null); }}
        onReprocess={viewerDocument && canEditConsultation ? () => void reprocessClinicalDocumentRow(viewerDocument.id) : undefined}
        reprocessBusy={Boolean(viewerDocument && documentRowBusyId === viewerDocument.id)}
        onRepairMemory={viewerDocument && canEditConsultation && canRepairClinicalMemory(viewerDocument) ? () => void repairClinicalMemoryRow(viewerDocument.id) : undefined}
        repairBusy={Boolean(viewerDocument && documentRowBusyId === viewerDocument.id)}
      />
    </Stack>
  );
}

function formatShortTimestamp(value: string | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString([], {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}
