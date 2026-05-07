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

import { useAuth } from "../../auth/useAuth";
import {
  aiPatientInstructions,
  aiStructureConsultationNotes,
  aiSuggestDiagnosis,
  aiSuggestPrescriptionTemplate,
  cancelConsultation,
  completeConsultation,
  createPrescriptionCorrection,
  createPrescription,
  finalizePrescription,
  getConsultation,
  getConsultationPrescription,
  getMedicines,
  getPatient,
  getPatientDocumentDownloadUrl,
  getPatientDocuments,
  getPatientPrescriptions,
  getPrescriptionPdf,
  printPrescription,
  previewPrescription,
  sendPrescription,
  startConsultationFromAppointment,
  updateConsultation,
  updatePrescription,
  type Consultation,
  type ConsultationInput,
  type Medicine,
  type MedicineType,
  type PatientDetail,
  type ClinicalDocument,
  type Prescription,
  type PrescriptionInput,
  type PrescriptionMedicine,
  type PrescriptionTest,
  type Timing,
} from "../../api/clinicApi";
import { ClinicalDocumentViewer } from "../../components/clinical/ClinicalDocumentViewer";

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
};

type MedicineRow = PrescriptionMedicine & { localId: string };
type TestRow = PrescriptionTest & { localId: string };
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
  };
}

function toPrescriptionInput(form: PrescriptionFormState, consultation: Consultation): PrescriptionInput {
  return {
    patientId: consultation.patientId,
    doctorUserId: consultation.doctorUserId,
    consultationId: consultation.id,
    appointmentId: consultation.appointmentId,
    diagnosisSnapshot: form.diagnosisSnapshot.trim() || null,
    advice: form.advice.trim() || null,
    followUpDate: form.followUpDate || null,
    medicines: form.medicines
      .filter((row) => row.medicineName.trim())
      .map((row, index) => ({
        medicineName: row.medicineName.trim(),
        medicineType: row.medicineType || null,
        strength: (row.strength || "").trim() || null,
        dosage: (row.dosage || "").trim(),
        frequency: (row.frequency || "").trim(),
        duration: (row.duration || "").trim(),
        timing: row.timing || null,
        instructions: (row.instructions || "").trim() || null,
        sortOrder: row.sortOrder ?? index + 1,
      })),
    recommendedTests: form.recommendedTests
      .filter((row) => row.testName.trim())
      .map((row, index) => ({
        testName: row.testName.trim(),
        instructions: (row.instructions || "").trim() || null,
        sortOrder: row.sortOrder ?? index + 1,
      })),
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
    form.diagnosisSnapshot.trim() ||
      form.advice.trim() ||
      form.followUpDate ||
      form.medicines.some((row) => row.medicineName.trim()) ||
      form.recommendedTests.some((row) => row.testName.trim())
  );
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

function compactDate(value?: string | null): string {
  return value ? new Date(value).toLocaleDateString() : "-";
}

function SectionCard({
  id,
  title,
  subtitle,
  expanded,
  onToggle,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  expanded: boolean;
  onToggle: (id: string) => void;
  children: React.ReactNode;
}) {
  return (
    <Card variant="outlined" sx={{ boxShadow: "none" }}>
      <CardContent sx={{ py: 1.5, "&:last-child": { pb: 1.5 } }}>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>{title}</Typography>
            {subtitle ? <Typography variant="caption" color="text.secondary">{subtitle}</Typography> : null}
          </Box>
          <Button type="button" size="small" variant="text" onClick={() => onToggle(id)}>{expanded ? "Hide" : "Open"}</Button>
        </Box>
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Box sx={{ pt: 1.5 }}>{children}</Box>
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
  const [searchParams] = useSearchParams();
  const consultationId = params.id || "";
  const appointmentId = searchParams.get("appointmentId") || "";

  const [consultation, setConsultation] = React.useState<Consultation | null>(null);
  const [patient, setPatient] = React.useState<PatientDetail | null>(null);
  const [previousPrescriptions, setPreviousPrescriptions] = React.useState<Prescription[]>([]);
  const [clinicalDocuments, setClinicalDocuments] = React.useState<ClinicalDocument[]>([]);
  const [prescription, setPrescription] = React.useState<Prescription | null>(null);
  const [medicineCatalog, setMedicineCatalog] = React.useState<Medicine[]>([]);

  const [consultationForm, setConsultationForm] = React.useState<ConsultationFormState>(emptyConsultationForm());
  const [prescriptionForm, setPrescriptionForm] = React.useState<PrescriptionFormState>(emptyPrescriptionForm());

  const [customSymptom, setCustomSymptom] = React.useState("");
  const [customDiagnosis, setCustomDiagnosis] = React.useState("");
  const [customTest, setCustomTest] = React.useState("");
  const [medicineSearch, setMedicineSearch] = React.useState("");
  const [correctionReason, setCorrectionReason] = React.useState("Same-day correction");
  const [activeTab, setActiveTab] = React.useState(0);
  const [expanded, setExpanded] = React.useState<Record<string, boolean>>({
    complaint: true,
    symptoms: true,
    diagnosis: true,
    notes: false,
    advice: false,
    followup: true,
  });

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [autosaveStatus, setAutosaveStatus] = React.useState<AutosaveStatus>("idle");
  const [aiBusy, setAiBusy] = React.useState(false);
  const [aiAvailable, setAiAvailable] = React.useState(true);
  const [medicineCatalogWarning, setMedicineCatalogWarning] = React.useState<string | null>(null);
  const [viewerDocument, setViewerDocument] = React.useState<ClinicalDocument | null>(null);
  const [viewerUrl, setViewerUrl] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const consultationRef = React.useRef<Consultation | null>(null);
  const prescriptionRef = React.useRef<Prescription | null>(null);
  const consultationFormRef = React.useRef(consultationForm);
  const prescriptionFormRef = React.useRef(prescriptionForm);
  const savedConsultationSnapshotRef = React.useRef(serializeConsultationForm(emptyConsultationForm()));
  const savedPrescriptionSnapshotRef = React.useRef(serializePrescriptionForm(emptyPrescriptionForm()));
  const autosaveTimerRef = React.useRef<number | null>(null);
  const autosaveRetryTimerRef = React.useRef<number | null>(null);
  const autosaveInFlightRef = React.useRef(false);
  const hydratedConsultationIdRef = React.useRef<string | null>(null);

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
        const initialConsultationForm = emptyConsultationForm(consult);
        setConsultationForm(initialConsultationForm);
        savedConsultationSnapshotRef.current = serializeConsultationForm(initialConsultationForm);
        hydratedConsultationIdRef.current = consult.id;

        const [detail, previousRx, documents] = await Promise.all([
          getPatient(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientPrescriptions(auth.accessToken, auth.tenantId, consult.patientId),
          getPatientDocuments(auth.accessToken, auth.tenantId, consult.patientId),
        ]);
        if (cancelled) return;

        setPatient(detail);
        setPreviousPrescriptions(previousRx.slice(0, 10));
        setClinicalDocuments(documents);

        try {
          const rx = await getConsultationPrescription(auth.accessToken, auth.tenantId, consult.id);
          if (!cancelled) {
            setPrescription(rx);
            const initialPrescriptionForm = emptyPrescriptionForm(rx, consult);
            setPrescriptionForm(initialPrescriptionForm);
            savedPrescriptionSnapshotRef.current = serializePrescriptionForm(initialPrescriptionForm);
          }
        } catch {
          if (!cancelled) {
            setPrescription(null);
            const initialPrescriptionForm = emptyPrescriptionForm(null, consult);
            setPrescriptionForm(initialPrescriptionForm);
            savedPrescriptionSnapshotRef.current = serializePrescriptionForm(initialPrescriptionForm);
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
    if (!consultation || consultation.status !== "DRAFT" || hydratedConsultationIdRef.current !== consultation.id) {
      setAutosaveStatus(consultation && consultation.status !== "DRAFT" ? "readonly" : "idle");
      return;
    }

    const consultationDirty = serializeConsultationForm(consultationForm) !== savedConsultationSnapshotRef.current;
    const prescriptionDirty = serializePrescriptionForm(prescriptionForm) !== savedPrescriptionSnapshotRef.current;
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
  }, [consultation, consultationForm, prescriptionForm]);

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

  const readOnly = consultation ? consultation.status !== "DRAFT" : false;
  const prescriptionReadOnly = readOnly || (prescription ? !["DRAFT", "PREVIEWED"].includes(prescription.status) : false);
  const patientRow = patient?.patient;
  const lastConsultation = patient?.previousConsultations?.find((row) => row.id !== consultation?.id);
  const recentMedicineNames = Array.from(new Set(previousPrescriptions.flatMap((rx) => rx.medicines.map((med) => med.medicineName)).filter(Boolean))).slice(0, 10);
  const filteredMedicines = medicineCatalog
    .filter((medicine) => medicine.active !== false)
    .filter((medicine) => !medicineSearch.trim() || `${medicine.medicineName} ${medicine.strength || ""}`.toLowerCase().includes(medicineSearch.trim().toLowerCase()))
    .slice(0, 24);

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
    if (!prescriptionReadOnly) setPrescriptionForm((current) => ({ ...current, medicines: [...current.medicines.filter((item) => item.medicineName.trim()), row] }));
  };

  const addManualMedicine = () => {
    if (!prescriptionReadOnly) setPrescriptionForm((current) => ({ ...current, medicines: [...current.medicines, newMedicineRow(current.medicines.length)] }));
  };

  const repeatPreviousPrescription = () => {
    const previous = previousPrescriptions[0];
    if (!previous || prescriptionReadOnly) return;
    setPrescriptionForm((current) => ({
      ...current,
      diagnosisSnapshot: previous.diagnosisSnapshot || current.diagnosisSnapshot,
      advice: previous.advice || current.advice,
      followUpDate: previous.followUpDate || current.followUpDate,
      medicines: previous.medicines.map((item, index) => ({ ...item, localId: `repeat-${Date.now()}-${index}`, sortOrder: index + 1 })),
      recommendedTests: previous.recommendedTests.map((item, index) => ({ ...item, localId: `repeat-test-${Date.now()}-${index}`, sortOrder: index + 1 })),
    }));
    setInfo("Previous prescription repeated into draft");
  };

  const updateMedicine = (localId: string, patch: Partial<MedicineRow>) => {
    if (!prescriptionReadOnly) setPrescriptionForm((current) => ({
      ...current,
      medicines: current.medicines.map((row) => (row.localId === localId ? { ...row, ...patch } : row)),
    }));
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
    setInfo(`${template.label} applied`);
  };

  const saveConsultationDraft = async (showInfo = false): Promise<Consultation | null> => {
    const currentConsultation = consultationRef.current;
    const currentForm = consultationFormRef.current;
    if (!auth.accessToken || !auth.tenantId || !currentConsultation || currentConsultation.status !== "DRAFT") return currentConsultation;
    const saved = await updateConsultation(auth.accessToken, auth.tenantId, currentConsultation.id, toConsultationInput(currentForm, currentConsultation));
    setConsultation(saved);
    savedConsultationSnapshotRef.current = serializeConsultationForm(currentForm);
    if (showInfo) setInfo("Consultation draft saved");
    return saved;
  };

  const runAutosave = async (): Promise<Consultation | null> => {
    const currentConsultation = consultationRef.current;
    if (!currentConsultation || currentConsultation.status !== "DRAFT") {
      setAutosaveStatus("readonly");
      return currentConsultation;
    }
    if (autosaveInFlightRef.current) return currentConsultation;

    const consultationDirty = serializeConsultationForm(consultationFormRef.current) !== savedConsultationSnapshotRef.current;
    const prescriptionDirty = serializePrescriptionForm(prescriptionFormRef.current) !== savedPrescriptionSnapshotRef.current;
    if (!consultationDirty && !prescriptionDirty) {
      setAutosaveStatus("saved");
      return currentConsultation;
    }

    autosaveInFlightRef.current = true;
    setAutosaveStatus("saving");
    try {
      const savedConsultation = consultationDirty ? await saveConsultationDraft(false) : currentConsultation;
      if (prescriptionDirty) await savePrescriptionDraft(false);
      setAutosaveStatus("saved");
      return savedConsultation;
    } catch (err) {
      console.error("Autosave failed", err);
      setAutosaveStatus("failed");
      if (autosaveRetryTimerRef.current) window.clearTimeout(autosaveRetryTimerRef.current);
      autosaveRetryTimerRef.current = window.setTimeout(() => {
        void runAutosave();
      }, 5000);
      return null;
    } finally {
      autosaveInFlightRef.current = false;
    }
  };

  const flushAutosave = async (): Promise<Consultation | null> => {
    if (autosaveTimerRef.current) {
      window.clearTimeout(autosaveTimerRef.current);
      autosaveTimerRef.current = null;
    }
    if (autosaveRetryTimerRef.current) {
      window.clearTimeout(autosaveRetryTimerRef.current);
      autosaveRetryTimerRef.current = null;
    }
    setSaving(true);
    try {
      return await runAutosave();
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
    const saved = await flushAutosave();
    if (saved) setInfo("Draft saved");
  };

  const completeCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    const saved = await flushAutosave();
    if (!saved) return;
    setSaving(true);
    try {
      const completed = await completeConsultation(auth.accessToken, auth.tenantId, consultation.id);
      setConsultation(completed);
      setAutosaveStatus("readonly");
      setInfo("Consultation completed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete consultation");
    } finally {
      setSaving(false);
    }
  };

  const cancelCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) return;
    setSaving(true);
    try {
      const cancelled = await cancelConsultation(auth.accessToken, auth.tenantId, consultation.id);
      setConsultation(cancelled);
      setInfo("Consultation cancelled");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel consultation");
    } finally {
      setSaving(false);
    }
  };

  const savePrescriptionDraft = async (showInfo = false): Promise<Prescription | null> => {
    const currentConsultation = consultationRef.current;
    const currentPrescription = prescriptionRef.current;
    const currentForm = prescriptionFormRef.current;
    if (!auth.accessToken || !auth.tenantId || !currentConsultation || currentConsultation.status !== "DRAFT") return currentPrescription;
    if (currentPrescription && currentPrescription.status !== "DRAFT") return currentPrescription;
    if (!currentPrescription && !hasPrescriptionContent(currentForm)) return null;
    const body = toPrescriptionInput(currentForm, currentConsultation);
    const saved = currentPrescription
      ? await updatePrescription(auth.accessToken, auth.tenantId, currentPrescription.id, body)
      : await createPrescription(auth.accessToken, auth.tenantId, body);
    setPrescription(saved);
    prescriptionRef.current = saved;
    savedPrescriptionSnapshotRef.current = serializePrescriptionForm(currentForm);
    if (showInfo) setInfo("Prescription draft saved");
    return saved;
  };

  const persistPrescription = async (): Promise<Prescription | null> => {
    setSaving(true);
    setError(null);
    setAutosaveStatus("saving");
    try {
      const saved = await savePrescriptionDraft(true);
      setAutosaveStatus("saved");
      return saved;
    } catch (err) {
      setAutosaveStatus("failed");
      setError(err instanceof Error ? err.message : "Failed to save prescription");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const previewCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const saved = await persistPrescription();
    if (!saved) return;
    setSaving(true);
    try {
      const previewed = await previewPrescription(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(previewed);
      prescriptionRef.current = previewed;
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, previewed.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription preview opened. You can still edit before finalizing.");
    } catch (err) {
      setError("Unable to preview prescription. Please try again.");
      console.error("Prescription preview failed", err);
    } finally {
      setSaving(false);
    }
  };

  const openClinicalDocument = async (document: ClinicalDocument) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setViewerDocument(document);
    setViewerUrl(null);
    try {
      const response = await getPatientDocumentDownloadUrl(auth.accessToken, auth.tenantId, document.id);
      setViewerUrl(response.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open clinical document");
    }
  };

  const createCorrectionDraft = async (flowType: "SAME_DAY_CORRECTION" | "FOLLOW_UP") => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !prescription) return;
    setSaving(true);
    setError(null);
    try {
      const corrected = await createPrescriptionCorrection(auth.accessToken, auth.tenantId, prescription.id, {
        correctionReason: correctionReason.trim() || (flowType === "FOLLOW_UP" ? "Follow-up prescription" : "Same-day correction"),
        flowType,
        prescription: toPrescriptionInput(prescriptionForm, consultation),
      });
      setPrescription(corrected);
      prescriptionRef.current = corrected;
      savedPrescriptionSnapshotRef.current = serializePrescriptionForm(prescriptionForm);
      setInfo(flowType === "FOLLOW_UP" ? "Follow-up prescription draft created" : "Correction draft created");
    } catch (err) {
      setError("Unable to create prescription correction. Please try again.");
      console.error("Prescription correction failed", err);
    } finally {
      setSaving(false);
    }
  };

  const finalizeCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const saved = await persistPrescription();
    if (!saved) return;
    setSaving(true);
    try {
      const finalized = await finalizePrescription(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(finalized);
      setInfo("Prescription finalized");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to finalize prescription");
    } finally {
      setSaving(false);
    }
  };

  const printCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const saved = await persistPrescription();
    if (!saved) return;
    try {
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, saved.id);
      const printed = await printPrescription(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(printed);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription PDF opened");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open prescription PDF");
    }
  };

  const sendCurrentPrescription = async (channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId) return;
    const saved = await persistPrescription();
    if (!saved) return;
    setSaving(true);
    try {
      const sent = await sendPrescription(auth.accessToken, auth.tenantId, saved.id, channel);
      setPrescription(sent);
      setInfo(`Prescription sent via ${channel}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to send prescription via ${channel}`);
    } finally {
      setSaving(false);
    }
  };

  const runAiAction = async (mode: "diagnosis" | "notes" | "instructions") => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    setAiBusy(true);
    setError(null);
    try {
      if (mode === "diagnosis") {
        const draft = await aiSuggestDiagnosis(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          symptoms: consultationForm.symptoms,
          findings: consultationForm.clinicalNotes,
          doctorNotes: consultationForm.chiefComplaints,
          knownConditions: patient.patient.existingConditions,
          allergies: patient.patient.allergies,
        });
        if (draft.draft) setConsultationForm((current) => ({ ...current, diagnosis: appendTokenLine(current.diagnosis, draft.draft || "") }));
      }

      if (mode === "notes") {
        const draft = await aiStructureConsultationNotes(auth.accessToken, auth.tenantId, {
          consultationId: consultation.id,
          patientId: consultation.patientId,
          doctorNotes: consultationForm.chiefComplaints,
          symptoms: consultationForm.symptoms,
          vitals: `BP:${consultationForm.bloodPressureSystolic}/${consultationForm.bloodPressureDiastolic}, Pulse:${consultationForm.pulseRate}, Temp:${consultationForm.temperature}`,
          observations: consultationForm.clinicalNotes,
        });
        if (draft.draft) setConsultationForm((current) => ({ ...current, clinicalNotes: `${current.clinicalNotes.trim()}\n\nAI Draft:\n${draft.draft}`.trim() }));
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
        if (draft.draft) setPrescriptionForm((current) => ({ ...current, advice: `${current.advice.trim()}\n\nPatient Instructions Draft:\n${draft.draft}`.trim() }));
      }

      setInfo("AI draft generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      if (message.includes("HTTP 403") || message.includes("HTTP 404")) {
        setAiAvailable(false);
        setError("AI module is not enabled for your clinic.");
      } else {
        setError("AI assistance is temporarily unavailable.");
      }
      console.error("AI action failed", err);
    } finally {
      setAiBusy(false);
    }
  };

  const applyAiPrescriptionTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation || !patient) return;
    setAiBusy(true);
    setError(null);
    try {
      const draft = await aiSuggestPrescriptionTemplate(auth.accessToken, auth.tenantId, {
        consultationId: consultation.id,
        patientId: consultation.patientId,
        diagnosis: consultationForm.diagnosis,
        symptoms: consultationForm.symptoms,
        allergies: patient.patient.allergies,
        currentMedications: "",
        doctorNotes: consultationForm.clinicalNotes,
      });
      if (draft.draft) setPrescriptionForm((current) => ({ ...current, advice: `${current.advice.trim()}\n\nAI Rx Suggestion:\n${draft.draft}`.trim() }));
      setInfo("AI prescription suggestion generated. Doctor must verify before use.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "AI action failed";
      if (message.includes("HTTP 403") || message.includes("HTTP 404")) {
        setAiAvailable(false);
        setError("AI module is not enabled for your clinic.");
      } else {
        setError("AI assistance is temporarily unavailable.");
      }
      console.error("AI prescription suggestion failed", err);
    } finally {
      setAiBusy(false);
    }
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
      <CardContent sx={{ py: 1.25, "&:last-child": { pb: 1.25 } }}>
        <Stack direction={{ xs: "column", xl: "row" }} spacing={1.25} justifyContent="space-between" alignItems={{ xs: "stretch", xl: "center" }}>
          <Stack spacing={0.75} sx={{ minWidth: 0 }}>
            <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
              <Typography variant="h6" sx={{ fontWeight: 950 }}>{patientRow ? `${patientRow.firstName} ${patientRow.lastName}` : consultation.patientName || consultation.patientId}</Typography>
              <Chip size="small" label={formatPatientAgeGender(patientRow)} />
              <Chip size="small" variant="outlined" label={patientRow?.patientNumber || consultation.patientNumber || "No patient ID"} />
              <Chip size="small" color={statusColor(consultation.status)} label={consultation.status} />
            </Stack>
            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
              <Chip
                size="small"
                color={patientRow?.allergies ? "error" : "default"}
                variant={patientRow?.allergies ? "filled" : "outlined"}
                sx={patientRow?.allergies ? { fontWeight: 950, animation: "pulse 1.8s ease-in-out infinite" } : undefined}
                label={`ALLERGIES: ${patientRow?.allergies || "None recorded"}`}
              />
              <Chip size="small" color={patientRow?.existingConditions ? "warning" : "default"} label={`Chronic: ${patientRow?.existingConditions || "None recorded"}`} />
              <Chip size="small" variant="outlined" label={`Long-term meds: ${patientRow?.longTermMedications || "None recorded"}`} />
              <Chip size="small" variant="outlined" label={`History: ${patientRow?.surgicalHistory || "None recorded"}`} />
              <Chip size="small" variant="outlined" label={`Last visit: ${compactDate(lastConsultation?.createdAt)}`} />
              <Chip size="small" variant="outlined" label={`Doctor: ${consultation.doctorName || consultation.doctorUserId}`} />
              <Chip
                size="small"
                variant={autosaveStatus === "saved" ? "filled" : "outlined"}
                color={autosaveStatus === "failed" ? "error" : autosaveStatus === "saving" ? "info" : autosaveStatus === "dirty" ? "warning" : "default"}
                label={
                  autosaveStatus === "dirty" ? "Unsaved changes" :
                  autosaveStatus === "saving" ? "Saving..." :
                  autosaveStatus === "saved" ? "Saved" :
                  autosaveStatus === "failed" ? "Save failed - retrying" :
                  autosaveStatus === "readonly" ? "Read-only" :
                  "Autosave ready"
                }
              />
            </Stack>
          </Stack>
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" justifyContent={{ xs: "flex-start", xl: "flex-end" }}>
            <Button type="button" size="small" variant="outlined" onClick={() => void backToQueue()}>Back to Queue</Button>
            <Button type="button" size="small" disabled={saving || readOnly} onClick={() => void manualSaveDraft()}>Save Draft</Button>
            <Button type="button" size="small" color="secondary" disabled={saving || readOnly} onClick={() => void completeCurrentConsultation()}>Complete</Button>
            <Button type="button" size="small" variant="outlined" disabled={saving} onClick={() => void printCurrentPrescription()}>Print</Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );

  return (
    <Stack spacing={1.5} sx={{ pb: 4 }}>
      {header}
      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}

      <Card sx={{ boxShadow: "none" }}>
        <CardContent sx={{ py: 0.5, "&:last-child": { pb: 0.5 } }}>
          <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} variant="scrollable" scrollButtons="auto">
            <Tab label="Consultation" />
            <Tab label="Prescription" />
            <Tab label="History" />
            <Tab label="Investigations" />
            <Tab label="AI Assist" />
          </Tabs>
        </CardContent>
      </Card>

      {activeTab === 0 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Stack spacing={1.25}>
              <SectionCard id="complaint" title="Chief Complaint" subtitle="Start with the visit reason" expanded={expanded.complaint} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <TextField size="small" fullWidth value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} placeholder="Type complaint and press Tab to continue" />
                  <QuickChipGroup disabled={readOnly} chips={SYMPTOM_CHIPS.slice(0, 6)} onPick={(chip) => setConsultationForm((c) => ({ ...c, chiefComplaints: appendTokenLine(c.chiefComplaints, chip) }))} />
                </Stack>
              </SectionCard>

              <SectionCard id="symptoms" title="Symptoms" subtitle="Chip-first symptom capture" expanded={expanded.symptoms} onToggle={toggleSection}>
                <Stack spacing={1}>
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

              <SectionCard id="diagnosis" title="Diagnosis" subtitle="Suggestions with inline AI" expanded={expanded.diagnosis} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <QuickChipGroup disabled={readOnly} chips={DIAGNOSIS_CHIPS} color="primary" onPick={(chip) => setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, chip) }))} />
                  <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
                    <TextField fullWidth size="small" label="Manual diagnosis" value={customDiagnosis} disabled={readOnly} onChange={(e) => setCustomDiagnosis(e.target.value)} onKeyDown={(e) => {
                      if (e.key === "Enter" && customDiagnosis.trim()) {
                        e.preventDefault();
                        setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, customDiagnosis) }));
                        setCustomDiagnosis("");
                      }
                    }} />
                    <Button type="button" variant="outlined" disabled={readOnly} onClick={() => {
                      setConsultationForm((c) => ({ ...c, diagnosis: appendTokenLine(c.diagnosis, customDiagnosis) }));
                      setCustomDiagnosis("");
                    }}>Add</Button>
                    {aiAvailable ? <Button type="button" variant="outlined" disabled={aiBusy || readOnly} onClick={() => void runAiAction("diagnosis")}>AI Suggest</Button> : null}
                  </Stack>
                  <TextField size="small" fullWidth label="Selected diagnosis" value={consultationForm.diagnosis} onChange={(e) => setConsultationForm((c) => ({ ...c, diagnosis: e.target.value }))} disabled={readOnly} />
                </Stack>
              </SectionCard>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, lg: 5 }}>
            <Stack spacing={1.25}>
              <SectionCard id="notes" title="Clinical Notes" subtitle="SOAP-style compact note" expanded={expanded.notes} onToggle={toggleSection}>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Subjective" value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Objective" value={consultationForm.clinicalNotes} onChange={(e) => setConsultationForm((c) => ({ ...c, clinicalNotes: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Assessment" value={consultationForm.diagnosis} onChange={(e) => setConsultationForm((c) => ({ ...c, diagnosis: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Plan" value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} /></Grid>
                </Grid>
              </SectionCard>

              <SectionCard id="advice" title="Advice" subtitle="Reusable advice shortcuts" expanded={expanded.advice} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <QuickChipGroup disabled={readOnly} chips={ADVICE_CHIPS} onPick={(chip) => setConsultationForm((c) => ({ ...c, advice: appendTokenLine(c.advice, chip) }))} />
                  <TextField size="small" fullWidth label="Advice" value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                </Stack>
              </SectionCard>

              <SectionCard id="followup" title="Vitals & Follow-up" subtitle="Capture essentials, set next visit" expanded={expanded.followup} onToggle={toggleSection}>
                <Stack spacing={1}>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 6, md: 4 }}><TextField size="small" fullWidth label="BP Sys" value={consultationForm.bloodPressureSystolic} onChange={(e) => setConsultationForm((c) => ({ ...c, bloodPressureSystolic: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 6, md: 4 }}><TextField size="small" fullWidth label="BP Dia" value={consultationForm.bloodPressureDiastolic} onChange={(e) => setConsultationForm((c) => ({ ...c, bloodPressureDiastolic: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 6, md: 4 }}><TextField size="small" fullWidth label="Pulse" value={consultationForm.pulseRate} onChange={(e) => setConsultationForm((c) => ({ ...c, pulseRate: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 6, md: 4 }}><TextField size="small" fullWidth label="Temp" value={consultationForm.temperature} onChange={(e) => setConsultationForm((c) => ({ ...c, temperature: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 6, md: 4 }}><TextField size="small" fullWidth label="SpO2" value={consultationForm.spo2} onChange={(e) => setConsultationForm((c) => ({ ...c, spo2: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 6, md: 4 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="temp-unit-label">Unit</InputLabel>
                        <Select labelId="temp-unit-label" label="Unit" value={consultationForm.temperatureUnit} onChange={(e) => setConsultationForm((c) => ({ ...c, temperatureUnit: String(e.target.value) as ConsultationFormState["temperatureUnit"] }))}>
                          <MenuItem value="">-</MenuItem>
                          <MenuItem value="CELSIUS">C</MenuItem>
                          <MenuItem value="FAHRENHEIT">F</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                  </Grid>
                  <QuickChipGroup disabled={readOnly} chips={FOLLOWUP_CHIPS} color="secondary" onPick={applyFollowUp} />
                  <TextField size="small" label="Follow-up date" type="date" value={consultationForm.followUpDate} disabled={readOnly} onChange={(e) => setConsultationForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
                </Stack>
              </SectionCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 1 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Stack spacing={1.25}>
              <Card variant="outlined" sx={{ boxShadow: "none" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack spacing={1.25}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Medicine Search</Typography>
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
                    <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Fast Packs</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Chip size="small" clickable={!prescriptionReadOnly} color="secondary" label="Repeat previous prescription" disabled={!previousPrescriptions.length || prescriptionReadOnly} onClick={repeatPreviousPrescription} />
                      {RX_TEMPLATES.map((template) => <Chip key={template.key} size="small" clickable={!prescriptionReadOnly} disabled={prescriptionReadOnly} color="primary" variant="outlined" label={template.label} onClick={() => applyRxTemplate(template)} />)}
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
            <Card>
              <CardContent sx={{ p: 1.5 }}>
                <Stack spacing={1.25}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Active Prescription</Typography>
                      <Typography variant="caption" color="text.secondary">One-click medicines, compact rows, fast chips.</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Button type="button" size="small" startIcon={<AddRoundedIcon />} disabled={prescriptionReadOnly} onClick={addManualMedicine}>Manual row</Button>
                      <Button type="button" size="small" variant="outlined" disabled={saving || prescriptionReadOnly} onClick={() => void persistPrescription()}>Save Rx</Button>
                      <Button type="button" size="small" variant="outlined" disabled={saving || prescriptionReadOnly} onClick={() => void previewCurrentPrescription()}>Preview</Button>
                      <Button type="button" size="small" color="secondary" disabled={saving || prescriptionReadOnly} onClick={() => void finalizeCurrentPrescription()}>Finalize</Button>
                    </Stack>
                  </Box>

                  {prescription && !["DRAFT", "PREVIEWED"].includes(prescription.status) ? (
                    <Alert severity="info">
                      Finalized prescriptions are immutable. Create a correction or follow-up draft to make changes.
                      <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ mt: 1 }}>
                        <TextField size="small" label="Correction reason" value={correctionReason} onChange={(e) => setCorrectionReason(e.target.value)} />
                        <Button type="button" size="small" variant="outlined" onClick={() => void createCorrectionDraft("SAME_DAY_CORRECTION")}>Same-day correction</Button>
                        <Button type="button" size="small" variant="outlined" onClick={() => void createCorrectionDraft("FOLLOW_UP")}>Follow-up draft</Button>
                      </Stack>
                    </Alert>
                  ) : null}

                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Diagnosis snapshot" value={prescriptionForm.diagnosisSnapshot} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, diagnosisSnapshot: e.target.value }))} /></Grid>
                    <Grid size={{ xs: 12, md: 5 }}><TextField size="small" fullWidth label="Advice" value={prescriptionForm.advice} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, advice: e.target.value }))} /></Grid>
                    <Grid size={{ xs: 12, md: 3 }}><TextField size="small" fullWidth label="Follow-up" type="date" value={prescriptionForm.followUpDate} disabled={prescriptionReadOnly} onChange={(e) => setPrescriptionForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                  </Grid>

                  <Stack spacing={1}>
                    {prescriptionForm.medicines.map((row, index) => (
                      <Card key={row.localId} variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
                        <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                          <Stack spacing={1}>
                            <Grid container spacing={1} alignItems="center">
                              <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label={`Medicine ${index + 1}`} value={row.medicineName} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { medicineName: e.target.value })} /></Grid>
                              <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth label="Strength" value={row.strength || ""} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { strength: e.target.value })} /></Grid>
                              <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth label="Dosage" value={row.dosage} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { dosage: e.target.value })} /></Grid>
                              <Grid size={{ xs: 6, md: 2 }}><TextField size="small" fullWidth label="Frequency" value={row.frequency} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { frequency: e.target.value })} /></Grid>
                              <Grid size={{ xs: 6, md: 1.5 }}><TextField size="small" fullWidth label="Duration" value={row.duration} disabled={prescriptionReadOnly} onChange={(e) => updateMedicine(row.localId, { duration: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 0.5 }}><IconButton size="small" disabled={prescriptionReadOnly} onClick={() => setPrescriptionForm((c) => ({ ...c, medicines: c.medicines.filter((item) => item.localId !== row.localId) }))}><DeleteOutlineRoundedIcon fontSize="small" /></IconButton></Grid>
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
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 2 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Typography variant="h6">Clinical Snapshot</Typography><Typography variant="body2"><b>Last diagnosis:</b> {lastConsultation?.diagnosis || "-"}</Typography><Typography variant="body2"><b>Chronic:</b> {patientRow?.existingConditions || "Not recorded"}</Typography><Typography variant="body2" color={patientRow?.allergies ? "error" : "text.primary"}><b>Allergies:</b> {patientRow?.allergies || "Not recorded"}</Typography><Typography variant="body2"><b>Long-term meds:</b> {patientRow?.longTermMedications || "Not recorded"}</Typography><Typography variant="body2"><b>History:</b> {patientRow?.surgicalHistory || "Not recorded"}</Typography></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Typography variant="h6">Previous Consultations</Typography>{!patient?.previousConsultations?.length ? <Alert severity="info">No previous consultations.</Alert> : <List dense>{patient.previousConsultations.slice(0, 8).map((row) => <ListItemButton key={row.id} onClick={() => navigate(`/consultations/${row.id}`)}><ListItemText primary={row.diagnosis || "No diagnosis"} secondary={`${compactDate(row.createdAt)} • ${row.status}`} /></ListItemButton>)}</List>}</CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent><Typography variant="h6">Previous Prescriptions</Typography>{!previousPrescriptions.length ? <Alert severity="info">No previous prescriptions.</Alert> : <List dense>{previousPrescriptions.slice(0, 8).map((row) => <ListItemButton key={row.id} onClick={() => navigate(`/consultations/${row.consultationId}`)}><ListItemText primary={row.prescriptionNumber} secondary={`${compactDate(row.createdAt)} • ${row.status}`} /></ListItemButton>)}</List>}</CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card><CardContent><Typography variant="h6">Uploaded Reports & Referrals</Typography>{!clinicalDocuments.length ? <Alert severity="info">No uploaded reports or referral documents.</Alert> : <List dense>{clinicalDocuments.slice(0, 10).map((row) => <ListItemButton key={row.id} onClick={() => void openClinicalDocument(row)}><ListItemText primary={`${row.documentType.replaceAll("_", " ")} • ${row.originalFilename}`} secondary={`${compactDate(row.createdAt)}${row.referredDoctor || row.referredHospital ? ` • Referral: ${[row.referredDoctor, row.referredHospital].filter(Boolean).join(" · ")}` : ""}`} /></ListItemButton>)}</List>}</CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 3 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card><CardContent><Stack spacing={1.25}><Typography variant="h6">Recommended Tests</Typography><QuickChipGroup disabled={prescriptionReadOnly} chips={TEST_CHIPS} color="primary" onPick={(chip) => setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: chip }] }))} /><Stack direction="row" spacing={1}><TextField fullWidth size="small" label="Custom test" value={customTest} disabled={prescriptionReadOnly} onChange={(e) => setCustomTest(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter" && customTest.trim() && !prescriptionReadOnly) { e.preventDefault(); setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: customTest.trim() }] })); setCustomTest(""); } }} /><Button type="button" variant="outlined" disabled={prescriptionReadOnly} onClick={() => { if (!customTest.trim()) return; setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, { ...newTestRow(c.recommendedTests.length), testName: customTest.trim() }] })); setCustomTest(""); }}>Add</Button></Stack>{prescriptionForm.recommendedTests.map((row, index) => <Card key={row.localId} variant="outlined" sx={{ boxShadow: "none" }}><CardContent sx={{ p: 1 }}><Grid container spacing={1} alignItems="center"><Grid size={{ xs: 12, md: 5 }}><TextField size="small" fullWidth label={`Test ${index + 1}`} value={row.testName} disabled={prescriptionReadOnly} onChange={(e) => updateTest(row.localId, { testName: e.target.value })} /></Grid><Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Instructions" value={row.instructions || ""} disabled={prescriptionReadOnly} onChange={(e) => updateTest(row.localId, { instructions: e.target.value })} /></Grid><Grid size={{ xs: 12, md: 1 }}><IconButton size="small" disabled={prescriptionReadOnly} onClick={() => setPrescriptionForm((c) => ({ ...c, recommendedTests: c.recommendedTests.filter((item) => item.localId !== row.localId) }))}><DeleteOutlineRoundedIcon fontSize="small" /></IconButton></Grid></Grid></CardContent></Card>)}</Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card><CardContent><Typography variant="h6">Reports & Test History</Typography>{!clinicalDocuments.length ? <Alert severity="info">No uploaded reports yet.</Alert> : <List dense>{clinicalDocuments.filter((row) => ["LAB_REPORT", "X_RAY", "MRI_CT", "REFERRAL", "DISCHARGE_SUMMARY"].includes(row.documentType)).slice(0, 8).map((row) => <ListItemButton key={row.id} onClick={() => void openClinicalDocument(row)}><ListItemText primary={`${row.documentType.replaceAll("_", " ")} • ${row.originalFilename}`} secondary={row.notes || row.referralNotes || compactDate(row.createdAt)} /></ListItemButton>)}</List>}</CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {activeTab === 4 ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent><Stack spacing={1.25}><Typography variant="h6">AI Clinical Assist</Typography>{!aiAvailable ? <Alert severity="warning">AI module is not enabled for your clinic.</Alert> : null}<Button type="button" disabled={aiBusy || readOnly || !aiAvailable} onClick={() => void runAiAction("diagnosis")}>Suggest diagnosis</Button><Button type="button" variant="outlined" disabled={aiBusy || readOnly || !aiAvailable} onClick={() => void runAiAction("notes")}>Structure notes</Button><Button type="button" variant="outlined" disabled={aiBusy || !aiAvailable} onClick={() => void applyAiPrescriptionTemplate()}>Suggest prescription template</Button><Button type="button" variant="outlined" disabled={aiBusy || !aiAvailable} onClick={() => void runAiAction("instructions")}>Patient-friendly instructions</Button>{aiBusy ? <Typography variant="caption" color="text.secondary">AI assistance is preparing suggestions...</Typography> : null}</Stack></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent><Typography variant="h6">Context Sent to AI</Typography><Typography variant="body2" color="text.secondary">Symptoms, diagnosis, vitals, allergies, chronic conditions, notes, and draft prescription are used contextually. Doctor verification is required before use.</Typography></CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="flex-end">
        <Button type="button" variant="outlined" color="error" disabled={saving || readOnly} onClick={() => void cancelCurrentConsultation()}>Cancel Consultation</Button>
        <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("email")}>Email Rx</Button>
        <Button type="button" variant="outlined" disabled={saving} onClick={() => void sendCurrentPrescription("whatsapp")}>WhatsApp Rx</Button>
      </Stack>
      <ClinicalDocumentViewer open={!!viewerDocument} document={viewerDocument} url={viewerUrl} onClose={() => { setViewerDocument(null); setViewerUrl(null); }} />
    </Stack>
  );
}
