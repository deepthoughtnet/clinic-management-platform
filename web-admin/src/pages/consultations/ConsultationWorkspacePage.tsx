import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Collapse,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
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

import { consultationSchema, firstZodError, labConsultationOrderCreateSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { ClinicalAiDraftCard, type ClinicalAiDraftStatus } from "../../components/clinical/ClinicalAiDraftCard";
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
  getLabOrders,
  getLabTests,
  getConsultationPrescription,
  getMedicines,
  getPatient,
  getPatientDocumentDownloadUrl,
  getPatientDocumentViewUrl,
  getPatientDocuments,
  getPatientPrescriptions,
  getPatientTimeline,
  getPrescriptionPdf,
  getPrescriptionHistory,
  printPrescription,
  previewPrescription,
  uploadPatientDocument,
  saveConsultationAiSummary,
  sendPrescription,
  startConsultationFromAppointment,
  updateConsultation,
  updatePrescription,
  type AiStatus,
  type AiDraftResponse,
  type Appointment,
  type Consultation,
  type ConsultationAiSummary,
  type ConsultationInput,
  type LabOrder,
  type LabTest,
  type Medicine,
  type MedicineType,
  type PatientDetail,
  type ClinicalDocument,
  type PatientTimelineItem,
  type Prescription,
  type PrescriptionInput,
  type PrescriptionMedicine,
  type PrescriptionTest,
  type Timing,
} from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { ClinicalDocumentViewer } from "../../components/clinical/ClinicalDocumentViewer";
import { PatientDocumentUploadDialog } from "../../components/clinical/PatientDocumentUploadDialog";

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

type MedicineRow = PrescriptionMedicine & { localId: string };
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
  sourceSummary: string | null;
  draftText: string;
  structuredData: Record<string, unknown> | null;
  error: string | null;
  selectedItems: string[];
  selectedItem: string | null;
};
type ClinicalDraftGenerationStep = ClinicalAiDraftKind | "prescriptionSuggestion" | "clinicalReasoning";
type ClinicalDraftStepStatus = "pending" | "generating" | "done" | "failed";
type ClinicalDraftGenerationStepState = {
  status: ClinicalDraftStepStatus;
  error: string | null;
  message: string | null;
};
type MedicineShortcut = Pick<
  Medicine,
  "medicineName" | "strength" | "defaultDosage" | "defaultFrequency" | "defaultDurationDays" | "defaultTiming" | "defaultInstructions"
> & {
  medicineType: MedicineType | null;
};

type PrescriptionFormState = {
  diagnosisSnapshot: string;
  advice: string;
  followUpDate: string;
  medicines: MedicineRow[];
  recommendedTests: TestRow[];
};

type AutosaveStatus = "idle" | "dirty" | "saving" | "saved" | "failed" | "readonly";
const CONSULTATION_COMPLETION_BLOCKED_MESSAGE = "Please complete/finalize prescription before completing consultation.";
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
const AI_DISABLED_MESSAGE = "AI assistance is not enabled or configured for this clinic.";
const AI_PROVIDER_NOT_CONFIGURED_MESSAGE = "AI module is enabled for this clinic, but AI provider is not configured.";

const FREQUENCIES = ["1-0-0", "0-1-0", "0-0-1", "1-0-1", "1-1-1"];
const DURATIONS = ["3 days", "5 days", "7 days", "10 days", "15 days"];
const TIMINGS: { label: string; value: Timing }[] = [
  { label: "Before food", value: "BEFORE_FOOD" },
  { label: "After food", value: "AFTER_FOOD" },
  { label: "With food", value: "WITH_FOOD" },
  { label: "Anytime", value: "ANYTIME" },
];

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

function newMedicineRow(index: number): MedicineRow {
  return {
    localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 8)}`,
    medicineName: "",
    medicineType: null,
    strength: "",
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
      ? record.medicines.map((item, index) => ({ ...item, localId: `${index}-${item.sortOrder ?? index}` }))
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

function compactText(value: string | null | undefined, max = 120) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
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
    sourceSummary: null,
    draftText: "",
    structuredData: null,
    error: null,
    selectedItems: [],
    selectedItem: null,
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
      return "Ask AIVA";
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
  expanded,
  onToggle,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  expanded: boolean;
  onToggle: (id: string) => void;
  children: React.ReactNode;
}) {
  return (
    <Card variant="outlined" sx={{ boxShadow: "none" }}>
      <CardContent sx={{ py: 1.1, "&:last-child": { pb: 1.1 } }}>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
            {icon ? (
              <Box sx={{ display: "grid", placeItems: "center", width: 30, height: 30, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main", flexShrink: 0 }}>
                {icon}
              </Box>
            ) : null}
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>{title}</Typography>
            </Box>
          </Box>
          <Button type="button" size="small" variant="text" onClick={() => onToggle(id)}>{expanded ? "Hide" : "Open"}</Button>
        </Box>
        {subtitle ? (
          <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
            {subtitle}
          </Typography>
        ) : null}
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Box sx={{ pt: 1.1 }}>{children}</Box>
        </Collapse>
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
  const [uploadDialogOpen, setUploadDialogOpen] = React.useState(false);
  const [patientTimeline, setPatientTimeline] = React.useState<PatientTimelineItem[]>([]);
  const [prescriptionHistory, setPrescriptionHistory] = React.useState<Prescription[]>([]);
  const [prescription, setPrescription] = React.useState<Prescription | null>(null);
  const [medicineCatalog, setMedicineCatalog] = React.useState<Medicine[]>([]);
  const [labTests, setLabTests] = React.useState<LabTest[]>([]);
  const [labOrders, setLabOrders] = React.useState<LabOrder[]>([]);

  const [consultationForm, setConsultationForm] = React.useState<ConsultationFormState>(emptyConsultationForm());
  const [prescriptionForm, setPrescriptionForm] = React.useState<PrescriptionFormState>(emptyPrescriptionForm());

  const [customSymptom, setCustomSymptom] = React.useState("");
  const [customDiagnosis, setCustomDiagnosis] = React.useState("");
  const [customTest, setCustomTest] = React.useState("");
  const [labOrderDialogOpen, setLabOrderDialogOpen] = React.useState(false);
  const [labOrderTestIds, setLabOrderTestIds] = React.useState<string[]>([]);
  const [labOrderNotes, setLabOrderNotes] = React.useState("");
  const [labOrderSaving, setLabOrderSaving] = React.useState(false);
  const [medicineSearch, setMedicineSearch] = React.useState("");
  const [correctionReason, setCorrectionReason] = React.useState("Same-day correction");
  const [activeTab, setActiveTab] = React.useState(() => consultationTabKeyToIndex(searchParams.get("tab")));
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

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [autosaveStatus, setAutosaveStatus] = React.useState<AutosaveStatus>("idle");
  const [aiBusy, setAiBusy] = React.useState(false);
  const [aiActiveAction, setAiActiveAction] = React.useState<AiAssistAction | null>(null);
  const [clinicalAiActiveDraft, setClinicalAiActiveDraft] = React.useState<ClinicalAiDraftKind | null>(null);
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
  const [clinicalAiDrafts, setClinicalAiDrafts] = React.useState<Record<ClinicalAiDraftKind, ClinicalAiDraftState>>(() => createEmptyClinicalAiDrafts());
  const [clinicalDraftGenerationSteps, setClinicalDraftGenerationSteps] = React.useState<Record<ClinicalDraftGenerationStep, ClinicalDraftGenerationStepState>>(() => createEmptyClinicalDraftGenerationSteps());
  const [aivaDraftsExpanded, setAivaDraftsExpanded] = React.useState(true);
  const [savedAiSummary, setSavedAiSummary] = React.useState<ConsultationAiSummary | null>(null);
  const [aivaClinicalQuestion, setAivaClinicalQuestion] = React.useState("");
  const [aivaChatMessages, setAivaChatMessages] = React.useState<AivaChatMessage[]>([]);
  const [aivaQuestionSubmitting, setAivaQuestionSubmitting] = React.useState(false);
  const [aivaChatDetailsOpen, setAivaChatDetailsOpen] = React.useState(false);
  const [medicineCatalogWarning, setMedicineCatalogWarning] = React.useState<string | null>(null);
  const [viewerDocument, setViewerDocument] = React.useState<ClinicalDocument | null>(null);
  const [viewerUrl, setViewerUrl] = React.useState<string | null>(null);
  const [selectedPrescriptionVersionId, setSelectedPrescriptionVersionId] = React.useState<string | null>(null);
  const [invalidMedicineRowIds, setInvalidMedicineRowIds] = React.useState<string[]>([]);
  const [selectedRxTemplateKey, setSelectedRxTemplateKey] = React.useState<string | null>(null);
  const [completeConsultationDialogOpen, setCompleteConsultationDialogOpen] = React.useState(false);
  const [readinessDetailsOpen, setReadinessDetailsOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const consultationRef = React.useRef<Consultation | null>(null);
  const prescriptionRef = React.useRef<Prescription | null>(null);
  const consultationFormRef = React.useRef(consultationForm);
  const prescriptionFormRef = React.useRef(prescriptionForm);
  const clinicalAiDraftsRef = React.useRef(clinicalAiDrafts);
  const aiDiagnosisSuggestionRef = React.useRef(aiDiagnosisSuggestion);
  const aiDiagnosisItemsRef = React.useRef(aiDiagnosisItems);
  const aiPrescriptionSuggestionRef = React.useRef(aiPrescriptionSuggestion);
  const aiPrescriptionItemsRef = React.useRef(aiPrescriptionItems);
  const savedConsultationSnapshotRef = React.useRef(serializeConsultationForm(emptyConsultationForm()));
  const savedPrescriptionSnapshotRef = React.useRef(serializePrescriptionForm(emptyPrescriptionForm()));
  const autosaveTimerRef = React.useRef<number | null>(null);
  const autosaveRetryTimerRef = React.useRef<number | null>(null);
  const autosaveInFlightRef = React.useRef(false);
  const autosavePromiseRef = React.useRef<Promise<Consultation | null> | null>(null);
  const hydratedConsultationIdRef = React.useRef<string | null>(null);
  const viewportRestoreRef = React.useRef<{ x: number; y: number } | null>(null);

  React.useEffect(() => {
    consultationRef.current = consultation;
  }, [consultation]);

  React.useEffect(() => {
    prescriptionRef.current = prescription;
  }, [prescription]);

  React.useEffect(() => {
    consultationFormRef.current = consultationForm;
  }, [consultationForm]);

  React.useEffect(() => {
    prescriptionFormRef.current = prescriptionForm;
  }, [prescriptionForm]);

  React.useEffect(() => {
    clinicalAiDraftsRef.current = clinicalAiDrafts;
  }, [clinicalAiDrafts]);

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

  const resolveOverwriteChoice = React.useCallback((label: string, currentValue: string, allowAppend: boolean) => {
    if (!currentValue.trim()) return "replace" as const;
    const choice = window.prompt(
      `${label} already has content. Replace existing content or append? Type Replace${allowAppend ? ", Append" : ""}, or Cancel.`,
      allowAppend ? "Append" : "Replace",
    );
    const normalized = (choice || "").trim().toLowerCase();
    if (normalized === "replace") return "replace" as const;
    if (allowAppend && normalized === "append") return "append" as const;
    return "cancel" as const;
  }, []);

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
      setClinicalSummary(null);
      setAiDiagnosisSuggestion(null);
      setAiDiagnosisSummary(null);
      setAiDiagnosisItems([]);
      setAiDiagnosisUnstructured(false);
      setAiDiagnosisProvider(null);
      setAiPrescriptionSuggestion(null);
      setAiPrescriptionItems([]);
      setAiPrescriptionUnstructured(false);
      setAiPrescriptionProvider(null);
      setClinicalAiDrafts(createEmptyClinicalAiDrafts());
      setClinicalDraftGenerationSteps(createEmptyClinicalDraftGenerationSteps());
      setAivaDraftsExpanded(true);
      setClinicalAiActiveDraft(null);
      setSavedAiSummary(null);
      setAivaChatMessages([]);
      setAivaQuestionSubmitting(false);
      setAivaChatDetailsOpen(false);
      setReadinessDetailsOpen(false);
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

        const [detail, previousRx, documents, timelineRows] = await Promise.all([
          getPatient(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientPrescriptions(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientDocuments(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientTimeline(auth.accessToken, auth.tenantId, consult.patientId),
        ]);
        if (cancelled) return;

        setPatient(detail);
        setPreviousPrescriptions(previousRx.slice(0, 10));
        setClinicalDocuments(documents);
        setPatientTimeline(timelineRows);

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
  }, [consultation, consultationForm, prescription, prescriptionForm]);

  React.useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      const consultationDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
      const prescriptionDirty = serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
      if (consultationDirty || prescriptionDirty) {
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
  const canGenerateClinicalReasoning = Boolean(hasClinicalReasoningContext && !aiBusy);
  const canAskAiva = Boolean(
    auth.accessToken
      && auth.tenantId
      && consultation
      && patient
      && aivaClinicalQuestion.trim()
      && !aiBusy,
  );
  const canGenerateClinicalDraft = Boolean(auth.accessToken && auth.tenantId && consultation && patient && aiAvailable && !aiBusy);
  const clinicalDraftEntries = React.useMemo(
    () => (Object.values(clinicalAiDrafts) as ClinicalAiDraftState[]).filter((draft) => draft.generatedAt || draft.error || draft.status !== "DRAFTED" || draft.draftText.trim()),
    [clinicalAiDrafts],
  );
  const clinicalDraftStats = React.useMemo(() => {
    const values = Object.values(clinicalAiDrafts);
    return {
      total: values.filter((draft) => draft.generatedAt || draft.error || draft.status !== "DRAFTED" || draft.draftText.trim()).length,
      pending: values.filter((draft) => draft.status === "DRAFTED" || draft.status === "EDITED").length,
      accepted: values.filter((draft) => draft.status === "ACCEPTED").length,
      rejected: values.filter((draft) => draft.status === "REJECTED").length,
    };
  }, [clinicalAiDrafts]);
  const pendingAiDraftCount = clinicalDraftStats.pending;
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
  const recentMedicineNames = Array.from(new Set(previousPrescriptions.flatMap((rx) => rx.medicines.map((med) => med.medicineName)).filter(Boolean))).slice(0, 10);
  const aiVitalsSummary = `BP:${consultationForm.bloodPressureSystolic}/${consultationForm.bloodPressureDiastolic}, Pulse:${consultationForm.pulseRate}, Resp:${consultationForm.respiratoryRate}, Temp:${consultationForm.temperature}, BMI:${currentBmi ? currentBmi.toFixed(1) : "-"}`;
  const aiAllergiesSummary = patientRow?.allergies?.trim() || null;
  const aiChronicConditionsSummary = patientRow?.existingConditions?.trim() || null;
  const activeTimeline = patientTimeline.length ? patientTimeline : [];
  const visiblePrescriptionHistory = prescriptionHistory.filter((row) => row.status !== "CANCELLED");
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
  const workflowNextAction = React.useMemo(() => {
    if (readOnly) {
      return { title: "Consultation is read-only", detail: "Use version history or create a correction draft if changes are needed." };
    }
    if (!consultationForm.chiefComplaints.trim() || !consultationForm.diagnosis.trim()) {
      return { title: "Fill consultation details", detail: "Capture the complaint and diagnosis, then save the draft." };
    }
    if (!hasPrescriptionContent(prescriptionForm)) {
      return { title: "Build the prescription", detail: "Add medicines, advice, follow-up, or tests, then preview or save the Rx draft." };
    }
    if (!prescriptionReadyForCompletion) {
      return { title: "Finalize the prescription", detail: hasAtLeastOneValidMedicineRow(prescriptionForm) ? "Preview or finalize the prescription before completing the consultation." : "No medicines added. Finalize the prescription or continue with the consultation notes only." };
    }
    return { title: "Complete consultation", detail: "Prescription is finalized. Review once, then complete the consultation." };
  }, [consultationForm.chiefComplaints, consultationForm.diagnosis, prescription, prescriptionForm, prescriptionReadyForCompletion, readOnly]);
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
              Report comparison
            </Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Report comparison will appear here once two or more reports are available.
          </Typography>
          <Typography variant="caption" color="text.secondary">
            AIVA can compare trends and highlight changes once enough history exists.
          </Typography>
          <Button type="button" size="small" variant="outlined" disabled startIcon={<AutoAwesomeRoundedIcon fontSize="small" />}>
            Compare reports
          </Button>
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
    { label: "Chronic conditions", value: patientRow?.existingConditions || "None recorded" },
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
    setSearchParams(nextParams, { replace: true });
  };

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
    if (hasPrescriptionContent(prescriptionForm) && !window.confirm("Replace the current prescription draft with the previous prescription?")) {
      return;
    }
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

  const applyRxTemplate = (template: RxTemplate) => {
    if (prescriptionReadOnly) return;
    if (hasPrescriptionContent(prescriptionForm) && !window.confirm(`Replace the current prescription draft with ${template.label}?`)) {
      return;
    }
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

  const aiSummaryText = React.useMemo(() => {
    if (clinicalSummary) {
      const structured = parseStructuredObject(clinicalSummary.structuredData || {});
      const summary = typeof structured.summary === "string" ? structured.summary.trim() : "";
      return summary || (clinicalSummary.draft || clinicalSummary.message || "").trim();
    }
    return savedAiSummary?.summary?.trim() || "";
  }, [clinicalSummary, savedAiSummary?.summary]);

  const aiSummaryProviderLabel = clinicalSummary?.provider || savedAiSummary?.provider || null;
  const aiSummaryModelLabel = clinicalSummary?.model || savedAiSummary?.model || null;
  const aiSummaryGeneratedAtLabel = clinicalSummary
    ? (savedAiSummary?.generatedAt || new Date().toISOString())
    : (savedAiSummary?.generatedAt || null);

  const consultationReadiness = React.useMemo(() => {
    const missingItems: string[] = [];
    if (!consultationForm.chiefComplaints.trim()) missingItems.push("Chief complaint missing");
    if (!consultationForm.symptoms.trim()) missingItems.push("Symptoms missing");
    if (!consultationForm.diagnosis.trim()) missingItems.push("Diagnosis missing");
    const hasSoapSignal = Boolean(consultationForm.clinicalNotes.trim() || consultationForm.diagnosis.trim() || consultationForm.advice.trim());
    if (!hasSoapSignal) missingItems.push("SOAP assessment or plan missing");
    const prescriptionFinalizedOrEmpty = Boolean(prescription?.status === "FINALIZED" || !hasPrescriptionContent(prescriptionForm));
    if (!prescriptionFinalizedOrEmpty) missingItems.push("Prescription not finalized");
    if (!consultationForm.advice.trim()) missingItems.push("Advice missing");
    if (!consultationForm.followUpDate.trim()) missingItems.push("Follow-up missing");
    if (clinicalDraftEntries.some((draft) => draft.kind === "diagnosis" && draft.status === "DRAFTED")) {
      missingItems.push("AI diagnosis still pending review");
    }
    const completedChecks = 8 - missingItems.length;
    const percent = Math.max(0, Math.min(100, Math.round((completedChecks / 8) * 100)));
    return { percent, missingItems };
  }, [
    consultationForm.advice,
    consultationForm.chiefComplaints,
    consultationForm.clinicalNotes,
    consultationForm.diagnosis,
    consultationForm.followUpDate,
    consultationForm.symptoms,
    clinicalDraftEntries,
    prescription?.status,
    prescriptionForm,
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
    const merged = currentConsultation ? { ...currentConsultation, ...saved } : saved;
    setConsultation(merged);
    consultationRef.current = merged;
    savedConsultationSnapshotRef.current = serializeConsultationForm(currentForm);
    if (showInfo) setInfo("Consultation draft saved");
    return saved;
  };

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
    setSaving(true);
    try {
      return autosavePromiseRef.current ? await autosavePromiseRef.current : await runAutosave();
    } finally {
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
        const proceed = window.confirm("Unsaved consultation changes remain. Leave without saving?");
        if (!proceed) return;
      }
    }
    navigate("/queue");
  };

  const manualSaveDraft = async () => {
    const saved = await preserveViewport(() => flushAutosave());
    if (saved) setInfo("Draft saved");
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

  const continueActivePrescriptionDraft = () => {
    if (!prescription) return;
    const activeForm = emptyPrescriptionForm(prescription, consultation || undefined);
    setSelectedPrescriptionVersionId(prescription.id);
    setPrescriptionForm(activeForm);
    savedPrescriptionSnapshotRef.current = serializePrescriptionForm(activeForm);
    setInfo("Continuing the active correction draft");
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
    const body = buildPrescriptionInput(currentForm, currentConsultation);
    const saved = currentPrescription
      ? await updatePrescription(auth.accessToken, auth.tenantId, currentPrescription.id, body)
      : await createPrescription(auth.accessToken, auth.tenantId, body);
    const merged = currentPrescription ? { ...currentPrescription, ...saved } : saved;
    setPrescription(merged);
    prescriptionRef.current = merged;
    savedPrescriptionSnapshotRef.current = serializePrescriptionForm(currentForm);
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
      setViewerUrl(response.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open clinical document");
    }
  };

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
    if (!hasAtLeastOneValidMedicineRow(prescriptionForm) && !window.confirm("No medicines added. Continue without prescription?")) {
      return;
    }
    const saved = await preserveViewport(() => persistPrescription());
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
      setLabOrderTestIds([]);
      setLabOrderNotes("");
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
      sourceSummary: baseSourceSummary,
      draftText: "",
      structuredData: null,
      error: null,
      selectedItems: [],
      selectedItem: null,
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
        const parsed = extractClinicalSoapDraft(draft);
        const combined = normalizeDraftedTextWithSections("", [
          ["Subjective", parsed.subjective],
          ["Objective", parsed.objective],
          ["Assessment", parsed.assessment],
          ["Plan", parsed.plan],
        ]) || extractClinicalDraftText(draft);
        updateClinicalAiDraft(kind, {
          status: "DRAFTED",
          generatedAt: new Date().toISOString(),
          sourceSummary: baseSourceSummary,
          draftText: combined,
          structuredData: draft.structuredData,
          error: null,
          selectedItems: [],
          selectedItem: null,
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
      updateClinicalAiDraft(kind, { error: friendly });
      updateAiAssistEntry(entryId, { status: "error", error: friendly });
      setError(friendly);
      console.error("Clinical AI draft failed", err);
    } finally {
      setAiBusy(false);
      setClinicalAiActiveDraft(null);
    }
  };

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

  const appendAivaChatMessage = React.useCallback((message: AivaChatMessage) => {
    setAivaChatMessages((current) => [...current, message]);
  }, []);

  const acceptClinicalDraft = React.useCallback((kind: ClinicalAiDraftKind) => {
    const draft = clinicalAiDrafts[kind];
    const currentText = draft.draftText.trim();
    if (!currentText) return;

    if (kind === "chiefComplaint") {
      const choice = resolveOverwriteChoice("Chief complaint", consultationForm.chiefComplaints, true);
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
      const choice = resolveOverwriteChoice("Symptoms", consultationForm.symptoms, true);
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
      const choice = resolveOverwriteChoice("SOAP notes", `${consultationForm.chiefComplaints}\n${consultationForm.clinicalNotes}\n${consultationForm.diagnosis}\n${consultationForm.advice}`.trim(), true);
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
      setConsultationForm((current) => ({
        ...current,
        chiefComplaints: choice === "replace" ? (parsed.subjective || current.chiefComplaints) : appendTokenLine(current.chiefComplaints, parsed.subjective || ""),
        clinicalNotes: choice === "replace" ? (parsed.objective || current.clinicalNotes) : appendTokenLine(current.clinicalNotes, parsed.objective || ""),
        diagnosis: choice === "replace" ? (parsed.assessment || current.diagnosis) : appendTokenLine(current.diagnosis, parsed.assessment || ""),
        advice: choice === "replace" ? (parsed.plan || current.advice) : appendTokenLine(current.advice, parsed.plan || ""),
      }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
      return;
    }

    if (kind === "advice") {
      const choice = resolveOverwriteChoice("Advice", consultationForm.advice, true);
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
      if (consultationForm.followUpDate.trim() && !window.confirm("Replace the existing follow-up date?")) return;
      setConsultationForm((current) => ({ ...current, followUpDate }));
      updateClinicalAiDraft(kind, { status: "ACCEPTED" });
    }
  }, [clinicalAiDrafts, consultationForm.advice, consultationForm.chiefComplaints, consultationForm.clinicalNotes, consultationForm.diagnosis, consultationForm.followUpDate, resolveOverwriteChoice, updateClinicalAiDraft]);

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

  const generateClinicalDraftStep = React.useCallback(async (kind: ClinicalDraftGenerationStep, forceAll: boolean) => {
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
      ? runAiAction("diagnosis")
      : kind === "prescriptionSuggestion"
        ? applyAiPrescriptionTemplate()
        : runClinicalDraftAction(kind);
    try {
      await run;
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      });
      const latestDraft = kind === "prescriptionSuggestion" || kind === "clinicalReasoning"
        ? null
        : clinicalAiDraftsRef.current[kind];
      const latestResult = kind === "clinicalReasoning"
        ? {
            ok: Boolean(aiDiagnosisSuggestionRef.current || aiDiagnosisItemsRef.current.length),
            error: aiDiagnosisSuggestionRef.current ? null : "Clinical reasoning failed to generate.",
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
  }, [applyAiPrescriptionTemplate, runAiAction, runClinicalDraftAction]);

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
    setAivaDraftsExpanded(true);
    if (successCount > 0) {
      setInfo(`AIVA generated ${successCount} draft suggestions. Doctor must verify before use.`);
    }
  };

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

  const acceptAllPendingClinicalDrafts = React.useCallback(() => {
    const pendingKinds = (Object.entries(clinicalAiDraftsRef.current) as Array<[ClinicalAiDraftKind, ClinicalAiDraftState]>)
      .filter(([, draft]) => draft.status === "DRAFTED" || draft.status === "EDITED")
      .map(([kind]) => kind);
    pendingKinds.forEach((kind) => {
      if (canAutoAcceptClinicalDraft(kind)) {
        acceptClinicalDraft(kind);
      }
    });
  }, [acceptClinicalDraft, canAutoAcceptClinicalDraft]);

  const rejectAllPendingClinicalDrafts = React.useCallback(() => {
    setClinicalAiDrafts((current) => {
      const next = { ...current };
      (Object.keys(next) as ClinicalAiDraftKind[]).forEach((kind) => {
        if (next[kind].status === "DRAFTED" || next[kind].status === "EDITED") {
          next[kind] = {
            ...next[kind],
            status: "REJECTED",
          };
        }
      });
      return next;
    });
  }, []);

  const clearRejectedClinicalDrafts = React.useCallback(() => {
    setClinicalAiDrafts((current) => {
      const next = { ...current };
      (Object.keys(next) as ClinicalAiDraftKind[]).forEach((kind) => {
        if (next[kind].status === "REJECTED") {
          next[kind] = createClinicalAiDraftState(kind, next[kind].title);
        }
      });
      return next;
    });
  }, []);

  const renderClinicalAiDraftCard = React.useCallback((kind: ClinicalAiDraftKind) => {
    const draft = clinicalAiDrafts[kind];
    const loading = aiBusy && clinicalAiActiveDraft === kind;
    const commonProps = {
      title: draft.title,
      status: draft.status,
      generatedAt: draft.generatedAt,
      sourceSummary: draft.sourceSummary,
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
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                {asStringList(structured.supportingFindings || structured.supportingEvidence || structured.findings).join(" • ") || "Use the consultation context and reasoning text for support."}
                              </Typography>
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
                                <Stack spacing={0.5}>
                                  {parsedDiagnosis.items.map((item, index) => (
                                    <Box key={`${entry.id}-diag-${index}`} sx={{ p: 0.75, border: 1, borderColor: "divider", borderRadius: 1.5 }}>
                                      <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }}>{item.title}</Typography>
                                      {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>{item.reason}</Typography> : null}
                                    </Box>
                                  ))}
                                </Stack>
                              ) : (
                                <Typography variant="body2" color="text.secondary">No structured differentials returned.</Typography>
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
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.redFlags) || [])).join(" • ") || "No red flags returned."}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Suggested investigations</Typography>
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                                {Array.from(new Set(parsedDiagnosis?.items?.flatMap((item) => item.recommendedInvestigations) || [])).join(" • ") || asStringList(structured.recommendedInvestigations).join(" • ") || "No investigations returned."}
                              </Typography>
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
                                {asStringList(structured.patientAdvice || structured.advice || structured.followUpSuggestions || suggestedActions).join(" • ") || displayText}
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
                      <Button type="button" size="small" variant="outlined" disabled={readOnly || !displayText} onClick={() => setConsultationForm((c) => ({ ...c, advice: appendTokenLine(c.advice, displayText) }))}>
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
        <Stack spacing={0.85}>
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
              <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                  <Typography variant="h6" sx={{ fontWeight: 950, lineHeight: 1, fontSize: { xs: "1.12rem", md: "1.35rem" } }}>
                    {patientRow ? `${patientRow.firstName} ${patientRow.lastName}` : consultation.patientName || consultation.patientId}
                  </Typography>
                  <Chip size="small" label={formatPatientAgeGender(patientRow)} />
                  <Chip size="small" color={statusColor(consultation.status)} label={consultation.status} />
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                  Patient summary, timeline, clinical notes, prescriptions, investigations, and AI companion in one workspace.
                </Typography>
              </Stack>
            </Stack>

            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" justifyContent="flex-end" sx={{ maxWidth: { xs: "100%", md: 520 } }}>
              <Button type="button" size="small" variant="outlined" onClick={() => void backToQueue()}>Back to Queue</Button>
              {canEditConsultation && !readOnly ? <Button type="button" size="small" disabled={saving} onClick={() => void manualSaveDraft()}>Save Draft</Button> : null}
              {canRunAi ? <Button type="button" size="small" variant="contained" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void generateConsultationDraft(false)}>Generate Consultation Draft</Button> : null}
              {canRunAi && Object.values(clinicalAiDrafts).some((draft) => draft.status === "ACCEPTED" || draft.status === "EDITED") ? (
                <Button
                  type="button"
                  size="small"
                  variant="outlined"
                  disabled={!canGenerateClinicalDraft}
                  onClick={() => {
                    const proceed = window.confirm("Regenerate all sections? This will refresh accepted and edited AI drafts.");
                    if (!proceed) return;
                    void generateConsultationDraft(true);
                  }}
                >
                  Regenerate All
                </Button>
              ) : null}
              {canEditConsultation && !readOnly ? <Button type="button" size="small" variant="outlined" disabled={saving || !labTests.length} onClick={openLabOrderDialog}>Order Lab Tests</Button> : null}
              {canEditConsultation && !prescriptionReadOnly ? <Button type="button" size="small" variant="outlined" disabled={saving || !hasPrescriptionContent(prescriptionForm)} onClick={() => void previewCurrentPrescription()}>Preview Rx</Button> : null}
              {canCompleteConsultation && !readOnly ? <Button type="button" size="small" color="secondary" disabled={saving || !prescriptionReadyForCompletion} onClick={() => setCompleteConsultationDialogOpen(true)}>Complete</Button> : null}
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

          <Stack direction={{ xs: "column", lg: "row" }} spacing={0.75} justifyContent="space-between" alignItems={{ xs: "flex-start", lg: "center" }}>
            <Alert severity={readOnly ? "info" : prescriptionReadyForCompletion ? "success" : "warning"} sx={{ py: 0.25, width: "100%" }}>
              <strong>{workflowNextAction.title}</strong> {workflowNextAction.detail}
            </Alert>
            <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: { lg: "nowrap" }, lineHeight: 1.2 }}>
              Shortcuts: Ctrl/Cmd+S save draft, Ctrl/Cmd+P preview Rx, Esc closes document viewer.
            </Typography>
          </Stack>
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" alignItems="center">
            <Chip
              size="small"
              color={consultationReadiness.percent >= 75 ? "success" : consultationReadiness.percent >= 50 ? "warning" : "default"}
              variant="outlined"
              label={`Consultation readiness: ${consultationReadiness.percent}%`}
              clickable
              onClick={() => setReadinessDetailsOpen((current) => !current)}
            />
            <Typography variant="caption" color="text.secondary">
              {readinessDetailsOpen ? "Hide missing items" : "Show missing items"}
            </Typography>
          </Stack>
          <Collapse in={readinessDetailsOpen} timeout="auto" unmountOnExit>
            <Card variant="outlined" sx={{ boxShadow: "none" }}>
              <CardContent sx={{ p: 1 }}>
                <Stack spacing={0.5}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Missing items</Typography>
                  {consultationReadiness.missingItems.length ? (
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {consultationReadiness.missingItems.map((item) => (
                        <Chip key={item} size="small" variant="outlined" label={item} />
                      ))}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">Consultation looks ready to complete.</Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Collapse>
          {canCompleteConsultation && !readOnly && !prescriptionReadyForCompletion ? (
            <Typography variant="caption" color="warning.main" sx={{ display: "block" }}>
              {CONSULTATION_COMPLETION_BLOCKED_MESSAGE}
            </Typography>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );

  return (
    <Stack spacing={1.25} sx={{ pb: 3.5 }}>
      {header}
      {timelinePreview.length ? (
        <Card sx={{ boxShadow: "none", borderColor: "primary.light" }}>
          <CardContent sx={{ py: 0.65, "&:last-child": { pb: 0.65 } }}>
            <Stack spacing={0.6}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                <Box>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <TimelineRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Patient timeline</Typography>
                    <Chip size="small" variant="outlined" label={`${timelinePreview.length} recent`} sx={{ height: 20 }} />
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                    Recent visits, prescriptions, and documents
                  </Typography>
                </Box>
                <Button type="button" size="small" variant="outlined" startIcon={<HistoryRoundedIcon fontSize="small" />} onClick={openHistoryTab}>
                  View Full History
                </Button>
              </Stack>
              {timelinePreview.length ? (
                <Stack direction="row" spacing={0.75} sx={{ overflowX: "auto", pb: 0.25, minHeight: 82, maxHeight: 92 }}>
                  {timelinePreview.map((item) => (
                    <Card
                      key={item.id}
                      variant="outlined"
                      sx={{
                        boxShadow: "none",
                        minWidth: 114,
                        maxWidth: 124,
                        flex: "0 0 auto",
                        cursor: item.itemType === "DOCUMENT" || item.consultationId || item.prescriptionId ? "pointer" : "default",
                        borderColor: item.consultationId === consultation.id ? "primary.main" : "divider",
                        bgcolor: item.consultationId === consultation.id ? "primary.50" : "background.paper",
                        borderRadius: 1.75,
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
                      <CardContent sx={{ p: 0.65, "&:last-child": { pb: 0.65 } }}>
                        <Stack spacing={0.35}>
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            <Box sx={{ width: 20, height: 20, borderRadius: "50%", display: "grid", placeItems: "center", bgcolor: "primary.50", color: "primary.main", flexShrink: 0 }}>
                              {item.itemType === "PRESCRIPTION" ? <MedicationRoundedIcon fontSize="inherit" /> : item.itemType === "LAB_ORDER" || item.itemType === "DOCUMENT" ? <ScienceRoundedIcon fontSize="inherit" /> : item.itemType === "INVESTIGATION" ? <BiotechRoundedIcon fontSize="inherit" /> : <HistoryRoundedIcon fontSize="inherit" />}
                            </Box>
                            <Chip size="small" label={timelineTypeLabel(item.itemType)} color={timelineColor(item.itemType)} variant="outlined" sx={{ height: 18, maxWidth: 72, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
                          </Stack>
                          <Typography variant="body2" sx={{ fontWeight: 850, lineHeight: 1.1, fontSize: 11.5 }} noWrap>
                            {item.title}
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
      ) : null}
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
            {canRunAi ? (
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
            <Grid container spacing={0.75} alignItems="stretch" sx={{ minHeight: 0 }}>
              <Grid size={{ xs: 12, xl: 8 }}>
                <Stack spacing={0.75}>
              <SectionCard id="complaint" title="Chief Complaint" subtitle="Start with the visit reason" icon={<ChatBubbleOutlineRoundedIcon fontSize="small" />} expanded={expanded.complaint} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <TextField size="small" fullWidth value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} placeholder="Type complaint and press Tab to continue" />
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("chiefComplaint")}>
                      Draft
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      AI suggestions are assistive. Doctor must verify before use.
                    </Typography>
                  </Stack>
                  {clinicalAiDrafts.chiefComplaint.generatedAt || clinicalAiDrafts.chiefComplaint.error || clinicalAiDrafts.chiefComplaint.status !== "DRAFTED" || clinicalAiDrafts.chiefComplaint.draftText.trim() ? renderClinicalAiDraftCard("chiefComplaint") : null}
                  <QuickChipGroup disabled={readOnly} chips={SYMPTOM_CHIPS.slice(0, 6)} onPick={(chip) => setConsultationForm((c) => ({ ...c, chiefComplaints: appendTokenLine(c.chiefComplaints, chip) }))} />
                </Stack>
              </SectionCard>

              <SectionCard id="symptoms" title="Symptoms" subtitle="Chip-first symptom capture" icon={<HealingRoundedIcon fontSize="small" />} expanded={expanded.symptoms} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("symptoms")}>
                      Extract
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      AI suggestions are assistive. Doctor must verify before use.
                    </Typography>
                  </Stack>
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

              <SectionCard id="diagnosis" title="Diagnosis" subtitle="Suggestions with inline AI" icon={<PsychologyRoundedIcon fontSize="small" />} expanded={expanded.diagnosis} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("diagnosis")}>
                      Suggest
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      AI suggestions are assistive. Doctor must verify before use.
                    </Typography>
                  </Stack>
                  {clinicalAiDrafts.diagnosis.generatedAt || clinicalAiDrafts.diagnosis.error || clinicalAiDrafts.diagnosis.status !== "DRAFTED" || clinicalAiDrafts.diagnosis.draftText.trim() ? renderClinicalAiDraftCard("diagnosis") : null}
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
                      {canRunAi ? (
                        <Button
                          type="button"
                          variant="contained"
                          startIcon={<AutoAwesomeRoundedIcon fontSize="small" />}
                          disabled={!canGenerateClinicalReasoning}
                          onClick={() => void runAiAction("diagnosis")}
                        >
                          Generate clinical reasoning
                        </Button>
                      ) : null}
                    </Stack>
                  </Stack>

                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, height: "100%" }}>
                        <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                          <Stack spacing={0.8}>
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <PsychologyRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Selected diagnosis</Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">Confirmed diagnoses and manual entries</Typography>
                            <QuickChipGroup disabled={readOnly} chips={DIAGNOSIS_CHIPS} color="primary" onPick={(chip) => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, chip) }))} />
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
                                  "& textarea": { whiteSpace: "pre-wrap", overflowWrap: "anywhere" },
                                },
                              }}
                            />
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, height: "100%" }}>
                        <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                          <Stack spacing={0.8}>
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <HistoryEduRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Suggested differential</Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">AI suggestions and red flags</Typography>
                            {aiDiagnosisSuggestion || aiDiagnosisItems.length ? (
                              <Stack spacing={0.75}>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                                  {aiDiagnosisProvider ? <Chip size="small" variant="outlined" label={`Provider: ${aiDiagnosisProvider}`} /> : null}
                                  {aiDiagnosisSummary ? <Chip size="small" color="primary" variant="outlined" label="AI ready" /> : null}
                                </Stack>
                                {aiDiagnosisUnstructured && !aiDiagnosisItems.length ? (
                                  <Alert severity="error" sx={{ py: 0.5 }}>
                                    {aiDiagnosisSuggestion || "AI returned an invalid response. Please retry."}
                                  </Alert>
                                ) : null}
                                <Stack spacing={0.65}>
                                  {aiDiagnosisItems.length ? aiDiagnosisItems.map((item, index) => (
                                    <Card key={`${item.title}-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                      <CardContent sx={{ p: 0.9, "&:last-child": { pb: 0.9 } }}>
                                        <Stack spacing={0.5}>
                                          <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
                                            <Box sx={{ minWidth: 0, flex: 1 }}>
                                              <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }}>
                                                {item.title}
                                              </Typography>
                                              {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.15 }}>{item.reason}</Typography> : null}
                                            </Box>
                                            <Button type="button" size="small" variant="outlined" disabled={readOnly} onClick={() => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, item.title) }))}>
                                              Add
                                            </Button>
                                          </Stack>
                                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                            {item.redFlags.length ? item.redFlags.slice(0, 2).map((flag) => <Chip key={flag} size="small" color="warning" variant="outlined" label={`Red flag: ${flag}`} />) : null}
                                            {item.recommendedInvestigations.length ? item.recommendedInvestigations.slice(0, 2).map((test) => <Chip key={test} size="small" variant="outlined" label={test} />) : null}
                                          </Stack>
                                        </Stack>
                                      </CardContent>
                                    </Card>
                                  )) : null}
                                </Stack>
                              </Stack>
                            ) : (
                              <Box sx={{ p: 1.1, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                                <Stack spacing={0.4}>
                                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                    Ask AIVA to suggest differential diagnoses based on current symptoms.
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    AI suggestions are assistive only. Doctor must verify before use.
                                  </Typography>
                                </Stack>
                              </Box>
                            )}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, height: "100%" }}>
                        <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
                          <Stack spacing={0.8}>
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>AI clinical reasoning</Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">Reasoning summary and next steps</Typography>
                            <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                              {aiBusy && aiActiveAction === "diagnosis" ? (
                                <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.45 }}>
                                  Generating reasoning...
                                </Typography>
                              ) : aiDiagnosisSuggestion ? (
                                <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.45 }}>
                                  {aiDiagnosisSuggestion}
                                </Typography>
                              ) : (
                                <Typography variant="body2" color="text.secondary">
                                  Generate clinical reasoning to see differential diagnoses, suggested tests, and next steps.
                                </Typography>
                              )}
                            </Box>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                              <Button
                                type="button"
                                size="small"
                                variant="outlined"
                                disabled={readOnly || !aiDiagnosisSuggestion}
                                onClick={() => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, aiDiagnosisSummary || aiDiagnosisSuggestion || "") }))}
                              >
                                Add to diagnosis
                              </Button>
                              <Button
                                type="button"
                                size="small"
                                variant="outlined"
                                disabled={readOnly || !aiDiagnosisSummary}
                                onClick={() => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, aiDiagnosisSummary || "") }))}
                              >
                                Add summary
                              </Button>
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  </Grid>
                </Stack>
              </SectionCard>

              <SectionCard id="notes" title="Clinical Notes / SOAP" subtitle="Subjective, objective, assessment, and plan" icon={<DescriptionRoundedIcon fontSize="small" />} expanded={expanded.notes} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                    <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("soap")}>
                      Draft SOAP
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      AI suggestions are assistive. Doctor must verify before use.
                    </Typography>
                  </Stack>
                  {clinicalAiDrafts.soap.generatedAt || clinicalAiDrafts.soap.error || clinicalAiDrafts.soap.status !== "DRAFTED" || clinicalAiDrafts.soap.draftText.trim() ? renderClinicalAiDraftCard("soap") : null}
                  <Grid container spacing={0.75}>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Subjective" value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Objective" value={consultationForm.clinicalNotes} onChange={(e) => setConsultationForm((c) => ({ ...c, clinicalNotes: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Assessment" value={consultationForm.diagnosis} onChange={(e) => setConsultationForm((c) => ({ ...c, diagnosis: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Plan" value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
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
                }}
              >
                <Stack
                  spacing={1}
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 1,
                    minWidth: 0,
                    minHeight: 0,
                    overflowX: "hidden",
                    overflowY: "visible",
                    pr: 0,
                  }}
                >
                  <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto", minHeight: "auto" }}>
                    <CardContent sx={{ p: 0.95, "&:last-child": { pb: 0.95 } }}>
                      <Stack spacing={0.85}>
                        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                          <Box>
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>AIVA Chat</Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">Ask AIVA anything about this consultation.</Typography>
                          </Box>
                          <Chip size="small" color={aiAvailable ? "success" : "warning"} variant="outlined" label={aiAvailable ? "AI ready" : "AI unavailable"} />
                        </Stack>
                        <Alert severity="info" sx={{ py: 0.4 }}>
                          AI suggestions are assistive only. Doctor must verify.
                        </Alert>
                        <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper", overflow: "visible" }}>
                          <Stack spacing={0.65}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Ask AIVA</Typography>
                            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "flex-end" }}>
                              <TextField
                                size="small"
                                fullWidth
                                placeholder="Ask about diagnosis, red flags, tests, or prescription safety…"
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
                                disabled={aiBusy}
                                sx={{ flex: 1, minWidth: 0 }}
                              />
                              <Button
                                type="button"
                                size="small"
                                variant="contained"
                                disabled={!canAskAiva}
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
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<PsychologyRoundedIcon fontSize="small" />} disabled={!consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Suggest diagnosis
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<WarningAmberRoundedIcon fontSize="small" />} sx={{ color: "warning.main", borderColor: "warning.light" }} disabled={!consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Red flags
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<ScienceRoundedIcon fontSize="small" />} sx={{ color: "secondary.main", borderColor: "secondary.light" }} disabled={!consultation || !patient || aiBusy} onClick={() => void runAiAction("diagnosis")}>
                            Recommended tests
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<HealthAndSafetyRoundedIcon fontSize="small" />} sx={{ color: "error.main", borderColor: "error.light" }} disabled={!consultation || !patient || aiBusy} onClick={() => void applyAiPrescriptionTemplate()}>
                            Drug interaction
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<TipsAndUpdatesRoundedIcon fontSize="small" />} sx={{ color: "success.main", borderColor: "success.light" }} disabled={!consultation || !patient || aiBusy} onClick={() => void runAiAction("instructions")}>
                            Patient advice
                          </Button>
                          <Button type="button" size="small" variant="outlined" fullWidth startIcon={<DescriptionRoundedIcon fontSize="small" />} sx={{ color: "info.main", borderColor: "info.light" }} disabled={!consultation || !patient || aiBusy} onClick={() => void runAiAction("notes")}>
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
                                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>AIVA Chat</Typography>
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
                                        </Stack>
                                      </Box>
                                    </Box>
                                  ))}
                                </Stack>
                              ) : (
                                <Box sx={{ p: 1.2, borderRadius: 2, bgcolor: "action.hover" }}>
                                  <Typography variant="body2" color="text.secondary">
                                    Ask AIVA anything about this consultation.
                                  </Typography>
                                </Box>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Stack>
                    </CardContent>
                  </Card>

                  <SectionCard id="followup" title="Vitals & Follow-up" subtitle="Capture essentials, set next visit" icon={<MonitorHeartRoundedIcon fontSize="small" />} expanded={expanded.followup} onToggle={toggleSection}>
                    <Stack spacing={1} sx={{ overflow: "visible", minHeight: 0 }}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                        <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("followUp")}>
                          Suggest
                        </Button>
                        <Typography variant="caption" color="text.secondary">
                          AI suggestions are assistive. Doctor must verify before use.
                        </Typography>
                      </Stack>
                      {clinicalAiDrafts.followUp.generatedAt || clinicalAiDrafts.followUp.error || clinicalAiDrafts.followUp.status !== "DRAFTED" || clinicalAiDrafts.followUp.draftText.trim() ? renderClinicalAiDraftCard("followUp") : null}
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
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, bloodPressureSystolic: value })),
                          },
                          {
                            label: "BP Dia",
                            icon: <FavoriteRoundedIcon fontSize="small" color="error" />,
                            value: consultationForm.bloodPressureDiastolic,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, bloodPressureDiastolic: value })),
                          },
                          {
                            label: "Pulse",
                            icon: <MonitorHeartRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.pulseRate,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, pulseRate: value })),
                          },
                          {
                            label: "Temp",
                            icon: <EventRoundedIcon fontSize="small" color="warning" />,
                            value: consultationForm.temperature,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, temperature: value })),
                          },
                          {
                            label: "SpO2",
                            icon: <HealthAndSafetyRoundedIcon fontSize="small" color="success" />,
                            value: consultationForm.spo2,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, spo2: value })),
                          },
                          {
                            label: "Resp. Rate",
                            icon: <ScienceRoundedIcon fontSize="small" color="secondary" />,
                            value: consultationForm.respiratoryRate,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, respiratoryRate: value })),
                          },
                          {
                            label: "Weight (kg)",
                            icon: <PersonRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.weightKg,
                            onChange: (value: string) => setConsultationForm((c) => ({ ...c, weightKg: value })),
                          },
                          {
                            label: "Height (cm)",
                            icon: <BadgeRoundedIcon fontSize="small" color="primary" />,
                            value: consultationForm.heightCm,
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
                                <TextField
                                  size="small"
                                  fullWidth
                                  label={item.label}
                                  value={item.value}
                                  onChange={(e) => item.onChange(e.target.value)}
                                  disabled={readOnly}
                                  sx={{ flex: 1, minWidth: 0 }}
                                />
                                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.05 }}>
                                  Trend n/a
                                </Typography>
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
                              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.05 }}>
                                Trend n/a
                              </Typography>
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
                                <Chip size="small" label={`BMI ${currentBmi ? currentBmi.toFixed(1) : "-"}`} variant="outlined" />
                                <Chip size="small" label={currentBmiCategory || "BMI n/a"} variant="outlined" />
                                <Chip size="small" label={`Last ${lastBmi ? lastBmi.toFixed(1) : "-"}`} variant="outlined" />
                                <Chip size="small" label={lastBmiCategory || "Last BMI n/a"} variant="outlined" />
                              </Stack>
                              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.05 }}>
                                Trend n/a
                              </Typography>
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

                  <SectionCard id="advice" title="Advice" subtitle="Reusable advice shortcuts" icon={<LightbulbRoundedIcon fontSize="small" />} expanded={expanded.advice} onToggle={toggleSection}>
                    <Stack spacing={1.25} sx={{ overflow: "visible", minHeight: 0 }}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                        <Button type="button" size="small" variant="outlined" startIcon={<AutoAwesomeRoundedIcon fontSize="small" />} disabled={!canGenerateClinicalDraft} onClick={() => void runClinicalDraftAction("advice")}>
                          Draft Advice
                        </Button>
                        <Typography variant="caption" color="text.secondary">
                          AI suggestions are assistive. Doctor must verify before use.
                        </Typography>
                      </Stack>
                      {clinicalAiDrafts.advice.generatedAt || clinicalAiDrafts.advice.error || clinicalAiDrafts.advice.status !== "DRAFTED" || clinicalAiDrafts.advice.draftText.trim() ? renderClinicalAiDraftCard("advice") : null}
                      <Box sx={{ overflow: "visible", minHeight: 0 }}>
                        <QuickChipGroup disabled={readOnly} chips={ADVICE_CHIPS} onPick={(chip) => setConsultationForm((c) => ({ ...c, advice: appendTokenLine(c.advice, chip) }))} />
                      </Box>
                      <TextField size="small" fullWidth label="Advice" value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                    </Stack>
                  </SectionCard>

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
                              {labReportPreview.length ? "Compare recent lab reports and findings" : "No comparable reports yet"}
                            </Typography>
                          </Box>
                          <Button type="button" size="small" variant="outlined" disabled={!labReportPreview.length} startIcon={<TrendingUpRoundedIcon fontSize="small" />}>
                            Compare
                          </Button>
                        </Stack>
                        {!labReportPreview.length ? (
                          <Box sx={{ p: 0.9, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper", overflow: "visible" }}>
                            <Stack spacing={0.45} alignItems="flex-start" sx={{ width: "100%" }}>
                              <Stack direction="row" spacing={0.75} alignItems="center">
                                <InsightsRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="body2" sx={{ fontWeight: 800 }}>
                                  No previous comparable reports.
                                </Typography>
                              </Stack>
                              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                When previous reports become available, AIVA will compare trends automatically.
                              </Typography>
                            </Stack>
                          </Box>
                        ) : (
                          <Stack spacing={0.5}>
                            {labReportPreview.slice(0, 2).map((document) => (
                              <Card key={document.id} variant="outlined" sx={{ boxShadow: "none", height: "auto", minHeight: "auto", overflow: "visible" }}>
                                <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                                  <Stack spacing={0.25}>
                                    <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>
                                      {document.documentType.replaceAll("_", " ")} • {document.originalFilename}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" noWrap>
                                      {compactDate(document.createdAt)}{document.aiExtractionSummary ? ` • ${compactText(document.aiExtractionSummary, 48)}` : ""}
                                    </Typography>
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        )}
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
                    <Collapse in={expanded.history} timeout="auto" unmountOnExit>
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
          <Grid size={{ xs: 12, lg: 4 }}>
            <Stack spacing={1.25}>
              <Card variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <MedicationRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Medicine Search</Typography>
                    </Stack>
                    {medicineCatalogWarning ? <Alert severity="warning">{medicineCatalogWarning}</Alert> : null}
                    <TextField size="small" placeholder="Search medicine or strength" value={medicineSearch} disabled={prescriptionReadOnly} onChange={(e) => setMedicineSearch(e.target.value)} />
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ maxHeight: 180, overflow: "auto", pr: 0.5 }}>
                      {filteredMedicines.length ? filteredMedicines.map((medicine) => (
                        <Chip key={medicine.id} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={`${medicine.medicineName}${medicine.strength ? ` ${medicine.strength}` : ""}`} onClick={() => addMedicineFromCatalog(medicine)} variant="outlined" />
                      )) : <Alert severity="info">No catalog match. Add manually.</Alert>}
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
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Chip size="small" clickable={!prescriptionReadOnly} color="secondary" variant={selectedRxTemplateKey === "repeat-previous" ? "filled" : "outlined"} label="Repeat previous prescription" disabled={!previousPrescriptions.length || prescriptionReadOnly} onClick={repeatPreviousPrescription} />
                      {RX_TEMPLATES.map((template) => <Chip key={template.key} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} color="primary" variant={selectedRxTemplateKey === template.key ? "filled" : "outlined"} label={template.label} onClick={() => applyRxTemplate(template)} />)}
                    </Stack>
                    <Divider />
                    <Typography variant="caption" color="text.secondary">Recent medicines</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      {recentMedicineNames.length ? recentMedicineNames.map((name) => (
                        <Chip key={name} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={name} onClick={() => addMedicineFromCatalog({ medicineName: name, medicineType: null, strength: "", defaultDosage: "", defaultFrequency: "", defaultDurationDays: null, defaultTiming: null, defaultInstructions: "" })} />
                      )) : <Typography variant="caption" color="text.secondary">No recent medicines.</Typography>}
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
                          <Button type="button" size="small" variant="outlined" onClick={continueActivePrescriptionDraft}>Continue Draft</Button>
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

                    {aiPrescriptionSuggestion || aiPrescriptionItems.length ? (
                      <Card variant="outlined" sx={{ boxShadow: "none" }}>
                        <CardContent sx={{ p: 1 }}>
                          <Stack spacing={1}>
                            <Typography variant="subtitle2">AI Medicine / Prescription Suggestions</Typography>
                            <Alert severity="info">
                              AI suggestions are assistive only. Doctor must verify before use.
                            </Alert>
                            {aiPrescriptionUnstructured && !aiPrescriptionItems.length ? (
                              <Alert severity="error">
                                {aiPrescriptionSuggestion || "AI returned an invalid response. Please retry."}
                              </Alert>
                            ) : null}
                            {aiPrescriptionItems.length ? (
                              <Stack spacing={0.75}>
                                {aiPrescriptionItems.map((item, index) => (
                                  <Card key={`${item.medicine}-${index}`} variant="outlined" sx={{ boxShadow: "none", borderRadius: 1.5 }}>
                                    <CardContent sx={{ p: 1 }}>
                                      <Stack spacing={0.75}>
                                        <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
                                          <Box sx={{ minWidth: 0, flex: 1 }}>
                                            <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
                                              {item.medicine}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                              {[item.dose, item.frequency, item.duration].filter(Boolean).join(" • ") || "No dose details provided"}
                                            </Typography>
                                            {item.reason ? <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>{item.reason}</Typography> : null}
                                            {item.safetyNote ? <Typography variant="caption" color="warning.main" sx={{ display: "block", mt: 0.25 }}>{item.safetyNote}</Typography> : null}
                                          </Box>
                                          <Button
                                            type="button"
                                            size="small"
                                            variant="outlined"
                                            disabled={prescriptionReadOnly}
                                            onClick={() => addMedicineFromAiSuggestion(item)}
                                          >
                                            Add
                                          </Button>
                                        </Stack>
                                      </Stack>
                                    </CardContent>
                                  </Card>
                                ))}
                              </Stack>
                            ) : aiPrescriptionSuggestion ? (
                              <Box
                                sx={{
                                  maxHeight: 180,
                                  overflowY: "auto",
                                  overflowX: "hidden",
                                  p: 1,
                                  border: 1,
                                  borderColor: "divider",
                                  borderRadius: 1,
                                  whiteSpace: "pre-wrap",
                                  wordBreak: "break-word",
                                  fontSize: 13,
                                  lineHeight: 1.45,
                                }}
                              >
                                {aiPrescriptionSuggestion}
                              </Box>
                            ) : null}
                            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                              <Button
                                type="button"
                                size="small"
                                variant="outlined"
                                disabled={prescriptionReadOnly || !aiPrescriptionSuggestion}
                                onClick={() => setPrescriptionForm((current) => ({ ...current, advice: appendTokenLine(current.advice, aiPrescriptionSuggestion || "") }))}
                              >
                                Add to advice
                              </Button>
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}

                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                          <CardContent sx={{ p: 1.25 }}>
                            <Stack spacing={0.75}>
                              <Stack direction="row" spacing={0.75} alignItems="center">
                                <HealthAndSafetyRoundedIcon fontSize="small" color="warning" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Drug safety</Typography>
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                Safety checks remain clinician-controlled. Review allergies, interactions, and contraindications before finalizing.
                              </Typography>
                              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                <Chip size="small" variant="outlined" label={patientRow?.allergies ? "Allergy review needed" : "No allergies recorded"} />
                                <Chip size="small" variant="outlined" label="Interaction check" />
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                          <CardContent sx={{ p: 1.25 }}>
                            <Stack spacing={0.75}>
                              <Stack direction="row" spacing={0.75} alignItems="center">
                                <TrendingUpRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Previous prescription comparison</Typography>
                              </Stack>
                              {previousPrescriptions.length ? (
                                <Typography variant="body2" color="text.secondary">
                                  Compare against the most recent finalized prescription to preserve continuity and spot changes quickly.
                                </Typography>
                              ) : (
                                <Typography variant="body2" color="text.secondary">
                                  No previous prescription comparison available yet.
                                </Typography>
                              )}
                              <Button type="button" size="small" variant="outlined" disabled={!previousPrescriptions.length}>
                                Compare previous
                              </Button>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                          <CardContent sx={{ p: 1.25 }}>
                            <Stack spacing={0.75}>
                              <Stack direction="row" spacing={0.75} alignItems="center">
                                <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>AI prescription suggestions</Typography>
                              </Stack>
                              {aiPrescriptionSuggestion ? (
                                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.45 }}>
                                  {compactText(aiPrescriptionSuggestion, 120)}
                                </Typography>
                              ) : (
                                <Typography variant="body2" color="text.secondary">
                                  AI prescription suggestions will appear here when available.
                                </Typography>
                              )}
                              <Button type="button" size="small" variant="outlined" disabled={!aiPrescriptionSuggestion}>
                                Apply suggestion
                              </Button>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Diagnosis snapshot" value={prescriptionForm.diagnosisSnapshot} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, diagnosisSnapshot: e.target.value }))} /></Grid>
                      <Grid size={{ xs: 12, md: 5 }}><TextField size="small" fullWidth label="Advice" value={prescriptionForm.advice} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, advice: e.target.value }))} /></Grid>
                      <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Follow-up" type="date" value={prescriptionForm.followUpDate} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                    </Grid>

                    <Stack spacing={1}>
                      {prescriptionForm.medicines.map((row, index) => (
                        <Card key={row.localId} variant="outlined" sx={{ boxShadow: "none", borderRadius: 2, borderColor: invalidMedicineRowIds.includes(row.localId) ? "error.main" : undefined }}>
                          <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                            <Stack spacing={1}>
                              <Grid container spacing={1} alignItems="center">
                                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} helperText={invalidMedicineRowIds.includes(row.localId) ? "Medicine name, dosage, frequency, and duration are required." : " "} label={`Medicine ${index + 1}`} value={row.medicineName} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { medicineName: e.target.value })} /></Grid>
                                <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth label="Strength" value={row.strength || ""} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { strength: e.target.value })} /></Grid>
                                <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Dosage" value={row.dosage} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { dosage: e.target.value })} /></Grid>
                                <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Frequency" value={row.frequency} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { frequency: e.target.value })} /></Grid>
                                <Grid size={{ xs: 6, md: 1.5 }}><TextField size="small" fullWidth error={invalidMedicineRowIds.includes(row.localId)} label="Duration" value={row.duration} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { duration: e.target.value })} /></Grid>
                                <Grid size={{ xs: 12, md: 0.5 }} sx={{ display: "flex", justifyContent: { xs: "flex-end", md: "center" } }}><IconButton size="small" disabled={prescriptionReadOnly} onClick={() => { setInvalidMedicineRowIds((current) => current.filter((id) => id !== row.localId)); setPrescriptionForm((c) => ({ ...c, medicines: c.medicines.filter((item) => item.localId !== row.localId) })); }}><DeleteOutlineRoundedIcon fontSize="small" /></IconButton></Grid>
                              </Grid>
                              <Stack direction="row" spacing={0.6} useFlexGap flexWrap="wrap">
                                {FREQUENCIES.map((chip) => <Chip key={chip} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} label={chip} onClick={() => updateMedicine(row.localId, { frequency: chip })} />)}
                                {DURATIONS.map((chip) => <Chip key={chip} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} variant="outlined" label={chip} onClick={() => updateMedicine(row.localId, { duration: chip })} />)}
                                {TIMINGS.map((chip) => <Chip key={chip.value} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} color={row.timing === chip.value ? "primary" : "default"} label={chip.label} onClick={() => updateMedicine(row.localId, { timing: chip.value })} />)}
                              </Stack>
                              <TextField size="small" fullWidth label="Instructions" value={row.instructions || ""} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { instructions: e.target.value })} placeholder="Auto or manual instructions" />
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                    </Stack>
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
                      <Alert severity="info">No prescription history yet.</Alert>
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

      {activeTab === 2 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Stack spacing={0.75}><Stack direction="row" spacing={0.75} alignItems="center"><InsightsRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Clinical Snapshot</Typography></Stack><Typography variant="body2"><b>Last diagnosis:</b> {lastConsultation?.diagnosis || "-"}</Typography><Typography variant="body2"><b>Last vitals:</b> {lastConsultation ? `BP ${lastConsultation.bloodPressureSystolic || "-"} / ${lastConsultation.bloodPressureDiastolic || "-"}, Pulse ${lastConsultation.pulseRate || "-"}, Temp ${lastConsultation.temperature || "-"}, Resp ${lastConsultation.respiratoryRate || "-"}` : "Not recorded"}</Typography><Typography variant="body2"><b>Last BMI:</b> {lastBmi ? `${lastBmi.toFixed(1)} (${lastBmiCategory || "n/a"})` : "Not recorded"}</Typography><Typography variant="body2"><b>Chronic:</b> {patientRow?.existingConditions || "Not recorded"}</Typography><Typography variant="body2" color={patientRow?.allergies ? "error" : "text.primary"}><b>Allergies:</b> {patientRow?.allergies || "Not recorded"}</Typography><Typography variant="body2"><b>Long-term meds:</b> {patientRow?.longTermMedications || "Not recorded"}</Typography><Typography variant="body2"><b>History:</b> {patientRow?.surgicalHistory || "Not recorded"}</Typography></Stack></CardContent></Card>
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
                          </Box>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Button size="small" onClick={() => void openClinicalDocument(row)}>View</Button>
                          </Stack>
                        </Box>
                      ))}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card><CardContent><Stack spacing={0.75}><Stack direction="row" spacing={0.75} alignItems="center"><TimelineRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Unified Patient Timeline</Typography></Stack>{!activeTimeline.length ? <Alert severity="info">No timeline events yet.</Alert> : <List dense>{activeTimeline.slice(0, 12).map((item) => <ListItemButton key={item.id} onClick={() => {
              if (item.itemType === "DOCUMENT" && item.documentId) {
                const document = clinicalDocuments.find((row) => row.id === item.documentId);
                if (document) void openClinicalDocument(document);
                return;
              }
              if (item.consultationId && item.consultationId !== consultation.id) {
                navigate(`/consultations/${item.consultationId}`);
              }
            }}><ListItemText primary={`${timelineTypeLabel(item.itemType)} • ${item.title}`} secondary={`${item.subtitle || "-"} • ${compactDateTime(item.occurredAt)}`} /></ListItemButton>)}</List>}</Stack></CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 2 ? (
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

      {activeTab === 3 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card><CardContent><Stack spacing={1.25}><Stack direction="row" spacing={0.75} alignItems="center"><ScienceRoundedIcon fontSize="small" color="primary" /><Typography variant="h6" sx={{ fontWeight: 900 }}>Recommended Tests</Typography></Stack><QuickChipGroup disabled={prescriptionReadOnly} chips={TEST_CHIPS} color="primary" onPick={(chip) => setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: chip }] }))} /><Stack direction="row" spacing={1}><TextField fullWidth size="small" label="Custom test" value={customTest} disabled={prescriptionReadOnly} onChange={(e) => setCustomTest(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter" && customTest.trim() && !prescriptionReadOnly) { e.preventDefault(); setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: customTest.trim() }] })); setCustomTest(""); } }} /><Button type="button" variant="outlined" disabled={prescriptionReadOnly} onClick={() => { if (!customTest.trim()) return; setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: customTest.trim() }] })); setCustomTest(""); }}>Add</Button></Stack>{prescriptionForm.recommendedTests.map((row, index) => <Card key={row.localId} variant="outlined" sx={{ boxShadow: "none" }}><CardContent sx={{ p: 1 }}><Grid container spacing={1} alignItems="center"><Grid size={{ xs: 12, md: 5 }}><TextField size="small" fullWidth label={`Test ${index + 1}`} value={row.testName} disabled={prescriptionReadOnly} onChange={(e) => updateTest(row.localId, { testName: e.target.value })} /></Grid><Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Instructions" value={row.instructions || ""} disabled={prescriptionReadOnly} onChange={(e) => updateTest(row.localId, { instructions: e.target.value })} /></Grid><Grid size={{ xs: 12, md: 1 }}><IconButton size="small" disabled={prescriptionReadOnly} onClick={() => setPrescriptionForm((c) => ({ ...c, recommendedTests: c.recommendedTests.filter((item) => item.localId !== row.localId) }))}><DeleteOutlineRoundedIcon fontSize="small" /></IconButton></Grid></Grid></CardContent></Card>)}</Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={1}>
                  <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <InsightsRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Reports & Test History</Typography>
                    </Stack>
                    <Button size="small" variant="outlined" onClick={() => setUploadDialogOpen(true)}>Upload</Button>
                  </Stack>
                  {!clinicalDocuments.length ? <Alert severity="info">No uploaded reports yet.</Alert> : <Stack spacing={1}>{clinicalDocuments.filter((row) => ["EXTERNAL_LAB_REPORT", "INTERNAL_LAB_REPORT", "LAB_REPORT", "X_RAY", "MRI_CT", "RADIOLOGY_REPORT", "REFERRAL", "REFERRAL_LETTER", "DISCHARGE_SUMMARY"].includes(row.documentType)).slice(0, 8).map((row) => <Box key={row.id} sx={{ p: 1, border: "1px solid", borderColor: "divider", borderRadius: 1.5, display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}><Box><Typography variant="body2" sx={{ fontWeight: 800 }}>{row.title || row.documentType.replaceAll("_", " ")} • {row.originalFilename}</Typography><Typography variant="caption" color="text.secondary">{row.description || "No description"}{row.reportDate ? ` • ${row.reportDate}` : ""}</Typography></Box><Button size="small" onClick={() => void openClinicalDocument(row)}>View</Button></Box>)}</Stack>}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 3 ? (
        <Stack spacing={1.5} sx={{ mt: 1.5 }}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <TrendingUpRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Report comparison</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Report comparison will appear here once two or more reports are available.
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>AI interpretation</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      AIVA will interpret report trends and highlight noteworthy changes when results are available.
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <InsightsRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Trend summary</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      The report trend summary remains compact so the investigation workflow stays above the fold.
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      {activeTab === 4 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <ScienceRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Lab Order</Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Select active lab tests for the current consultation.
                  </Typography>
                  <Button variant="contained" onClick={openLabOrderDialog} disabled={readOnly || !labTests.length}>
                    Order Lab Tests
                  </Button>
                  <Typography variant="caption" color="text.secondary">
                    {labTests.length ? `${labTests.length} active tests available` : "No active tests configured."}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 8 }}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <ScienceRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Lab Orders</Typography>
                  </Stack>
                  {!labOrders.length ? (
                    <Alert severity="info">No lab orders have been created for this consultation.</Alert>
                  ) : (
                    <Stack spacing={1}>
                      {labOrders.map((order) => (
                        <Card key={order.id} variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1.25 }}>
                            <Stack spacing={0.75}>
                              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center" flexWrap="wrap">
                                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                  {order.orderNumber}
                                </Typography>
                                <Chip size="small" variant="outlined" color={order.status === "READY_FOR_COLLECTION" ? "success" : order.status === "PAYMENT_PENDING" ? "warning" : "default"} label={order.status.replaceAll("_", " ")} />
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {order.items.map((item) => item.testName).join(", ")}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                Bill: {order.billNumber || "-"} {order.billDueAmount != null ? `• Due ${order.billDueAmount.toFixed(2)}` : ""}
                              </Typography>
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
      ) : null}

      {activeTab === 4 ? (
        <Stack spacing={1.5} sx={{ mt: 1.5 }}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={0.75}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <ScienceRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Selected Tests</Typography>
                    </Stack>
                    {labOrders.length ? (
                      <Typography variant="body2" color="text.secondary">
                        {labOrders[0].items.slice(0, 5).map((item) => item.testName).join(", ")}
                        {labOrders[0].items.length > 5 ? "…" : ""}
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No selected tests yet. Open the lab order dialog to create a request.
                      </Typography>
                    )}
                    <Chip size="small" variant="outlined" label={`Active tests ${labTests.length}`} sx={{ width: "fit-content" }} />
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              {reportComparisonPlaceholder}
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      {canRunAi && activeTab === 5 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Stack spacing={1.5}>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>AIVA Draft Review</Typography>
                      </Stack>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <Chip size="small" color={clinicalDraftStats.total ? "primary" : "default"} variant="outlined" label={`Total ${clinicalDraftStats.total}`} />
                        <Chip size="small" color={pendingAiDraftCount ? "warning" : "default"} variant="outlined" label={`AIVA Drafts: ${pendingAiDraftCount} pending`} />
                        <Chip size="small" color={pendingAiDraftCount ? "warning" : "default"} variant="outlined" label={`Pending ${pendingAiDraftCount}`} />
                        <Chip size="small" color={clinicalDraftStats.accepted ? "success" : "default"} variant="outlined" label={`Accepted ${clinicalDraftStats.accepted}`} />
                        <Chip size="small" color={clinicalDraftStats.rejected ? "default" : "default"} variant="outlined" label={`Rejected ${clinicalDraftStats.rejected}`} />
                        <Button type="button" size="small" variant="outlined" onClick={() => setAivaDraftsExpanded((current) => !current)}>
                          {aivaDraftsExpanded ? "Collapse" : "Expand"}
                        </Button>
                      </Stack>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Review all AI-drafted clinical content here before you accept, edit, or reject it.
                    </Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Button type="button" size="small" variant="contained" disabled={!pendingAiDraftCount || aiBusy} onClick={() => void acceptAllPendingClinicalDrafts()}>
                        Accept All Pending
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={!pendingAiDraftCount || aiBusy} onClick={() => void rejectAllPendingClinicalDrafts()}>
                        Reject All Pending
                      </Button>
                      <Button type="button" size="small" variant="outlined" disabled={!clinicalDraftStats.rejected || aiBusy} onClick={() => void clearRejectedClinicalDrafts()}>
                        Clear Rejected
                      </Button>
                      <Chip size="small" variant="outlined" color={pendingAiDraftCount ? "warning" : "default"} label={`Needs review ${Object.values(clinicalAiDrafts).filter((draft) => (draft.status === "DRAFTED" || draft.status === "EDITED") && !canAutoAcceptClinicalDraft(draft.kind)).length}`} />
                    </Stack>
                    {(Object.values(clinicalDraftGenerationSteps).some((step) => step.status !== "pending")) ? (
                      <Card variant="outlined" sx={{ boxShadow: "none" }}>
                        <CardContent sx={{ p: 1 }}>
                          <Stack spacing={0.75}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Generation progress</Typography>
                            <Stack spacing={0.6}>
                              {(Object.entries(clinicalDraftGenerationSteps) as Array<[ClinicalDraftGenerationStep, ClinicalDraftGenerationStepState]>).map(([step, state]) => (
                                <Stack key={step} direction="row" spacing={0.75} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                                  <Stack direction="row" spacing={0.75} alignItems="center">
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{clinicalDraftGenerationStepLabel(step)}</Typography>
                                    <Chip
                                      size="small"
                                      variant="outlined"
                                      color={state.status === "done" ? "success" : state.status === "failed" ? "error" : state.status === "generating" ? "warning" : "default"}
                                      label={state.status}
                                    />
                                  </Stack>
                                  {state.status === "failed" ? (
                                    <Button type="button" size="small" variant="text" disabled={aiBusy} onClick={() => void generateClinicalDraftStep(step, false)}>
                                      Retry
                                    </Button>
                                  ) : state.message ? (
                                    <Typography variant="caption" color="text.secondary">{state.message}</Typography>
                                  ) : null}
                                </Stack>
                              ))}
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                    <Collapse in={aivaDraftsExpanded} timeout="auto" unmountOnExit>
                      <Stack spacing={1}>
                        {clinicalDraftEntries.length || latestClinicalReasoningEntry || latestPrescriptionEntry || recommendedTestSuggestions.length ? (
                          <>
                            <Typography variant="caption" color="text.secondary">
                              AI suggestions are assistive. Doctor must verify before use.
                            </Typography>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Chief Complaint</Typography>
                            {renderClinicalAiDraftCard("chiefComplaint")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Symptoms</Typography>
                            {renderClinicalAiDraftCard("symptoms")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Diagnosis</Typography>
                            {renderClinicalAiDraftCard("diagnosis")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>SOAP</Typography>
                            {renderClinicalAiDraftCard("soap")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Advice</Typography>
                            {renderClinicalAiDraftCard("advice")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Follow-up</Typography>
                            {renderClinicalAiDraftCard("followUp")}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinical Reasoning</Typography>
                              {latestClinicalReasoningEntry ? renderAiAssistEntry(latestClinicalReasoningEntry) : (
                                <Box sx={{ p: 1.2, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                                  <Typography variant="body2" color="text.secondary">No clinical reasoning draft yet.</Typography>
                                </Box>
                              )}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Prescription Suggestion</Typography>
                              {latestPrescriptionEntry ? renderAiAssistEntry(latestPrescriptionEntry) : (
                                <Box sx={{ p: 1.2, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                                  <Typography variant="body2" color="text.secondary">No prescription suggestion generated yet.</Typography>
                                </Box>
                              )}
                            </Stack>
                            <Stack spacing={0.75}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Recommended Tests</Typography>
                              {recommendedTestSuggestions.length ? (
                                <Card variant="outlined" sx={{ boxShadow: "none" }}>
                                  <CardContent sx={{ p: 1 }}>
                                    <Stack spacing={0.75}>
                                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                        {recommendedTestSuggestions.map((test) => (
                                          <Chip key={test} size="small" variant="outlined" label={test} />
                                        ))}
                                      </Stack>
                                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        <Button type="button" size="small" variant="outlined" disabled={prescriptionReadOnly || !recommendedTestSuggestions.length} onClick={() => setPrescriptionForm((current) => ({ ...current, recommendedTests: [...current.recommendedTests, ...recommendedTestSuggestions.map((test, index) => ({ ...newTestRow(current.recommendedTests.length + index), testName: test }))] }))}>
                                          Add to recommended tests
                                        </Button>
                                        <Button type="button" size="small" variant="outlined" disabled={!recommendedTestSuggestions.length} onClick={() => void navigator.clipboard.writeText(recommendedTestSuggestions.join("\n"))}>
                                          Copy
                                        </Button>
                                      </Stack>
                                    </Stack>
                                  </CardContent>
                                </Card>
                              ) : (
                                <Box sx={{ p: 1.2, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                                  <Typography variant="body2" color="text.secondary">No recommended tests returned yet.</Typography>
                                </Box>
                              )}
                            </Stack>
                          </>
                        ) : (
                          <Box sx={{ p: 1.2, border: 1, borderStyle: "dashed", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
                            <Typography variant="body2" color="text.secondary">
                              No consultation draft generated yet. Use Generate Consultation Draft or a section action to start a review.
                            </Typography>
                          </Box>
                        )}
                      </Stack>
                    </Collapse>
                  </Stack>
                </CardContent>
              </Card>
              <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" alignItems="center">
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>AIVA Chat</Typography>
                      </Stack>
                      <Chip size="small" variant="outlined" color={aiAvailable ? "success" : "warning"} label={aiAvailable ? "AI ready" : "AI unavailable"} />
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Ask AIVA anything about this consultation.
                    </Typography>
                    {!aiAvailable ? (
                      <Alert severity="info">
                        AIVA Clinical Assist is not enabled for this clinic.
                      </Alert>
                    ) : null}
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
                                          <Button type="button" size="small" variant="outlined" disabled={readOnly || !message.text.trim()} onClick={() => setConsultationForm((current) => ({ ...current, advice: appendTokenLine(current.advice, message.text) }))}>
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
                                  Ask AIVA anything about this consultation.
                                </Typography>
                              </Box>
                            )}
                          </Stack>
                          <TextField
                            size="small"
                            fullWidth
                            label="Ask AIVA anything about this consultation"
                            placeholder="Ask AIVA anything about this consultation"
                            value={aivaClinicalQuestion}
                            disabled={aiBusy || aivaQuestionSubmitting || readOnly || !aiAvailable}
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
                            <Button type="button" size="small" variant="contained" disabled={!canAskAiva || aivaQuestionSubmitting || aiBusy} onClick={() => void submitAivaQuestion()}>
                              Ask AIVA
                            </Button>
                            <Button type="button" size="small" variant="outlined" disabled={!aivaChatMessages.length} onClick={() => setAivaChatDetailsOpen((current) => !current)}>
                              {aivaChatDetailsOpen ? "Hide Transcript" : "Show Transcript"}
                            </Button>
                          </Stack>
                          <Collapse in={aivaChatDetailsOpen && aivaChatMessages.length > 0} timeout="auto" unmountOnExit>
                            <Stack spacing={0.75}>
                              <Typography variant="caption" color="text.secondary">
                                Doctor and AIVA messages stay in this session until the consultation is closed.
                              </Typography>
                            </Stack>
                          </Collapse>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            <Chip size="small" icon={<PsychologyRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAvailable} disabled={aiBusy || !aiAvailable} variant="outlined" label="Suggest diagnosis" onClick={() => void runAiAction("diagnosis")} />
                            <Chip size="small" icon={<DescriptionRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAvailable} disabled={aiBusy || !aiAvailable} variant="outlined" label="Structure SOAP notes" onClick={() => void runAiAction("notes")} />
                            <Chip size="small" icon={<MedicationRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAvailable} disabled={aiBusy || !aiAvailable} variant="outlined" label="Prescription template" onClick={() => void applyAiPrescriptionTemplate()} />
                            <Chip size="small" icon={<TipsAndUpdatesRoundedIcon fontSize="small" />} clickable={!aiBusy && aiAvailable} disabled={aiBusy || !aiAvailable} variant="outlined" label="Patient instructions" onClick={() => void runAiAction("instructions")} />
                          </Stack>
                          {aiBusy ? <Typography variant="caption" color="text.secondary">AI assistance is preparing suggestions...</Typography> : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Stack>
                </CardContent>
              </Card>
              <Card>
                <CardContent>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <InsightsRoundedIcon fontSize="small" color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Clinical Summary</Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Previous visit summary, chronic history, and recent consultation summary are generated as assistive context only.
                    </Typography>
                    <Button type="button" variant="contained" disabled={aiBusy || !aiAvailable} onClick={() => void generateClinicalSummary()}>Generate summary</Button>
                    {aiSummaryText || clinicalSummary ? (
                      <Stack spacing={1}>
                        <Card variant="outlined" sx={{ boxShadow: "none" }}>
                          <CardContent sx={{ p: 1 }}>
                            <Stack spacing={1}>
                              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" alignItems="center">
                                <Typography variant="subtitle2">Clinical Summary Draft</Typography>
                                {aiSummaryProviderLabel ? <Chip size="small" variant="outlined" label={`Provider: ${aiSummaryProviderLabel}`} /> : null}
                                {aiSummaryModelLabel ? <Chip size="small" variant="outlined" label={`Model: ${aiSummaryModelLabel}`} /> : null}
                                {aiSummaryGeneratedAtLabel ? <Chip size="small" variant="outlined" label={`Generated: ${compactDateTime(aiSummaryGeneratedAtLabel)}`} /> : null}
                                {clinicalSummary ? <Chip size="small" color={clinicalSummary.fallbackUsed ? "warning" : "success"} label={clinicalSummary.fallbackUsed ? "Fallback used" : "AI ready"} /> : null}
                              </Stack>
                              <Alert severity="info">
                                AI suggestions are assistive only. Doctor must verify before use.
                              </Alert>
                              {aiSummaryText ? (
                                <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1, bgcolor: "background.paper", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                                  <Typography variant="body2" sx={{ lineHeight: 1.55 }}>{aiSummaryText}</Typography>
                                </Box>
                              ) : null}
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
                          </CardContent>
                        </Card>
                      </Stack>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={0.75}>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Context Sent to AI</Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Symptoms, diagnosis, vitals, allergies, chronic conditions, notes, and draft prescription are used contextually. Doctor verification is required before use.
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    AIVA only rewrites and summarizes clinician-entered context.
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={labOrderDialogOpen} onClose={() => setLabOrderDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Order Lab Tests</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <TextField
              fullWidth
              size="small"
              label="Notes"
              value={labOrderNotes}
              onChange={(e) => setLabOrderNotes(e.target.value)}
              multiline
              minRows={2}
              placeholder="Add order notes for the lab team"
            />
            <Box sx={{ maxHeight: 360, overflow: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.25 }}>
              <Stack spacing={1}>
                {labTests.map((test) => {
                  const selected = labOrderTestIds.includes(test.id);
                  return (
                    <Card key={test.id} variant="outlined" sx={{ boxShadow: "none", borderColor: selected ? "primary.main" : "divider" }}>
                      <CardContent sx={{ p: 1 }}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="flex-start">
                          <Box>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{test.testName}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {test.testCode} • {test.category} • {test.sampleType || "Sample not specified"} • {test.turnaroundTime || "TAT not set"}
                            </Typography>
                          </Box>
                          <Button
                            size="small"
                            variant={selected ? "contained" : "outlined"}
                            onClick={() => setLabOrderTestIds((current) => selected ? current.filter((id) => id !== test.id) : [...current, test.id])}
                          >
                            {selected ? "Selected" : "Select"}
                          </Button>
                        </Stack>
                      </CardContent>
                    </Card>
                  );
                })}
                {!labTests.length ? <Alert severity="info">No active lab tests found.</Alert> : null}
              </Stack>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLabOrderDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={labOrderSaving || !labOrderTestIds.length} onClick={() => void submitLabOrder()}>
            Create Order
          </Button>
        </DialogActions>
      </Dialog>

      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="flex-end">
        {canEditConsultation ? <Button type="button" variant="outlined" color="error" disabled={saving || readOnly} onClick={() => void cancelCurrentConsultation()}>Cancel Consultation</Button> : null}
        {canFinalizePrescription ? <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("email")}>Email Rx</Button> : null}
        {canFinalizePrescription ? <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("whatsapp")}>WhatsApp Rx</Button> : null}
      </Stack>
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
          const [documents, timelineRows] = await Promise.all([
            getPatientDocuments(auth.accessToken, auth.tenantId, consultation.patientId),
            getPatientTimeline(auth.accessToken, auth.tenantId, consultation.patientId),
          ]);
          setClinicalDocuments(documents);
          setPatientTimeline(timelineRows);
        }}
      />
      <ClinicalDocumentViewer open={!!viewerDocument} document={viewerDocument} url={viewerUrl} onClose={() => { setViewerDocument(null); setViewerUrl(null); }} />
    </Stack>
  );
}
