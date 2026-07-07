import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
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
import EditRoundedIcon from "@mui/icons-material/EditRounded";
import { useNavigate } from "react-router-dom";

import { fileUploadSchema, firstZodError, mapZodErrors, vaccinationMasterSchema, vaccinationRecordSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { resolveEnabledTenantModules } from "../../modules/moduleRegistry";
import { CompactEmptyState, CompactTableFrame, compactChipSx } from "../../components/compact/CompactUi";
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
  getOverdueVaccinations,
  getReceiptPdf,
  getVaccinationHistory,
  getVaccinationRecommendations,
  getVaccineImportTemplate,
  getVaccines,
  getPatientDocuments,
  getPatientDocumentViewUrl,
  getPatientVaccinations,
  importVaccinesCsv,
  uploadPatientDocument,
  searchBills,
  recordPatientVaccination,
  listBillPayments,
  listBillReceipts,
  sendReceipt,
  searchPatients,
  updateVaccine,
  type ClinicalDocument,
  type Bill,
  type ClinicProfile,
  type ClinicUser,
  type Patient,
  type PatientVaccination,
  type Payment,
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

function latestReceiptForBill(receipts: Receipt[]) {
  return receipts.slice().sort((left, right) => `${right.receiptDate}|${right.createdAt}`.localeCompare(`${left.receiptDate}|${left.createdAt}`))[0] || null;
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
  const [vaccines, setVaccines] = React.useState<VaccineMaster[]>([]);
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
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const externalCardFileRef = React.useRef<HTMLInputElement | null>(null);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [vaccineRows, due, overdue, history, userRows, patientRows, clinic] = await Promise.all([
      getVaccines(auth.accessToken, auth.tenantId),
      getDueVaccinations(auth.accessToken, auth.tenantId),
      getOverdueVaccinations(auth.accessToken, auth.tenantId),
      getVaccinationHistory(auth.accessToken, auth.tenantId),
      getClinicUsers(auth.accessToken, auth.tenantId),
      searchPatients(auth.accessToken, auth.tenantId, { active: true }),
      getClinicProfile(auth.accessToken, auth.tenantId),
    ]);
    setVaccines(vaccineRows);
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
      const [vaccinations, documents] = await Promise.all([
        getPatientVaccinations(auth.accessToken, auth.tenantId, patientId),
        getPatientDocuments(auth.accessToken, auth.tenantId, patientId, { documentType: "VACCINATION" }),
      ]);
      setPatientHistoryRows(vaccinations);
      setPatientDocuments(documents);
      await refreshPatientBilling(patientId);
    } catch {
      setPatientHistoryRows([]);
      setPatientDocuments([]);
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

  const selectedPatientSummary = React.useMemo(() => {
    if (!selectedPatient) return null;
    return patients.find((patient) => patient.id === selectedPatient.id) || selectedPatient;
  }, [patients, selectedPatient]);

  const selectedPatientBill = React.useMemo(() => {
    return patientBills.find((bill) => billHasCollectableDue(bill))
      || patientBills.find((bill) => !billCountsAsSettled(bill))
      || patientBills[0]
      || null;
  }, [patientBills]);

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
      return recorded;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record vaccination");
      return null;
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, loadAll, refreshRecommendations, refreshSelectedPatientContext, selectedPatient]);

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
    const recorded = await submitVaccination(body);
    if (recorded?.billId && body.addToBill) {
      try {
        const bill = await getBill(auth.accessToken, auth.tenantId, recorded.billId);
        if (billHasCollectableDue(bill)) {
          setVaccinationPaymentDialog({
            bill,
            patientLabel: selectedPatientSummary ? `${selectedPatientSummary.firstName} ${selectedPatientSummary.lastName}`.trim() : selectedPatient.patientNumber || "Patient",
            vaccineName: vaccines.find((item) => item.id === recorded.vaccineId)?.vaccineName || recorded.vaccineName || "Vaccine",
          });
        }
      } catch {
        // Keep the vaccination record saved even if the follow-up billing lookup fails.
      }
    }
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
      return patientMatch && vaccineMatch && fromMatch && toMatch && dueMatch;
    });
  }, [historyRows, historyPatientFilter, historyVaccineFilter, historyFromDate, historyToDate, historyDueStatus]);

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
        billItemUnitPrice: current.billItemUnitPrice || "",
      }));
      return;
    }
    setVaccinationForm((current) => ({
      ...current,
      vaccineId: vaccine.id,
      doseNumber: vaccine.doseNumber != null ? String(vaccine.doseNumber) : (item.doseNumber != null ? String(item.doseNumber) : current.doseNumber),
      route: vaccine.route ?? item.route ?? current.route,
      administrationSite: vaccine.administrationSite ?? item.administrationSite ?? current.administrationSite,
      nextDueDate: item.dueDate ?? current.nextDueDate,
      billItemUnitPrice: vaccine.defaultPrice != null ? vaccine.defaultPrice.toFixed(2) : current.billItemUnitPrice,
    }));
    setWorkflowNotice(`Selected ${vaccine.vaccineName} from recommendations. Recommendations are assistive. Verify against clinic protocol.`);
    window.setTimeout(() => document.getElementById("vaccination-vaccineId")?.focus(), 0);
  }, [vaccines]);

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

  const openPatient = React.useCallback((patientId: string) => {
    navigate(`/patients/${patientId}`);
  }, [navigate]);

  const bookAppointment = React.useCallback((row: PatientVaccination) => {
    navigate(`/appointments?patientId=${row.patientId}`, { state: { patient: patients.find((entry) => entry.id === row.patientId) || null } });
  }, [navigate, patients]);

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
                      setVaccinationForm((current) => ({
                        ...current,
                        vaccineId,
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
                  {selectedPatientBill ? (
                    <Grid size={12}>
                      <Card variant="outlined" sx={{ bgcolor: "background.default" }}>
                        <CardContent sx={{ p: 1 }}>
                          <Stack spacing={0.75}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Current billing status</Typography>
                            <Stack direction="row" spacing={0.75} flexWrap="wrap">
                              <Chip size="small" label={billStatusLabel(selectedPatientBill.status)} color={billStatusColor(selectedPatientBill.status)} variant="outlined" />
                              <Chip size="small" label={selectedPatientBill.billNumber} variant="outlined" />
                              <Chip size="small" label={`Due: ₹${selectedPatientBill.dueAmount.toFixed(2)}`} color={billHasCollectableDue(selectedPatientBill) ? "warning" : "success"} variant="outlined" />
                              {billCountsAsSettled(selectedPatientBill) ? <Chip size="small" label="Receipt ready" color="success" variant="outlined" /> : null}
                            </Stack>
                            {billHasCollectableDue(selectedPatientBill) ? (
                              <Button size="small" variant="outlined" onClick={() => setVaccinationPaymentDialog({
                                bill: selectedPatientBill,
                                patientLabel: selectedPatientSummary ? `${selectedPatientSummary.firstName} ${selectedPatientSummary.lastName}`.trim() : selectedPatient?.patientNumber || "Patient",
                                vaccineName: vaccines.find((item) => item.id === vaccinationForm.vaccineId)?.vaccineName || "Vaccine",
                              })}>
                                Collect Payment
                              </Button>
                            ) : billCountsAsSettled(selectedPatientBill) ? (
                              <Stack direction="row" spacing={0.75} flexWrap="wrap">
                                <Button size="small" variant="outlined" onClick={() => void openSelectedPatientBillReceipt(false)} disabled={receiptActionLoading}>
                                  View Receipt
                                </Button>
                                <Button size="small" variant="outlined" onClick={() => void openSelectedPatientBillReceipt(true)} disabled={receiptActionLoading}>
                                  Print Receipt
                                </Button>
                                <Button size="small" variant="outlined" onClick={() => void handleVaccinationReceiptDownload()} disabled={receiptActionLoading}>
                                  Download Receipt PDF
                                </Button>
                                <Button size="small" variant="outlined" onClick={() => void handleVaccinationReceiptSend("EMAIL")} disabled={receiptActionLoading}>
                                  Email Receipt
                                </Button>
                                <Button size="small" variant="outlined" onClick={() => void handleVaccinationReceiptSend("WHATSAPP")} disabled={receiptActionLoading}>
                                  WhatsApp Receipt
                                </Button>
                              </Stack>
                            ) : null}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  ) : null}
                </Grid>

                <Button variant="contained" size="small" onClick={() => void recordVaccination()} disabled={saving}>
                  {saving ? "Saving..." : "Record Vaccination"}
                </Button>
              </Stack>
            </CardContent>
          </Card>

          {canManageMaster ? (
            <Card sx={{ mt: 2 }}>
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

            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Vaccination history
                  </Typography>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="Search patient" value={historyPatientFilter} onChange={(e) => setHistoryPatientFilter(e.target.value)} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="Search vaccine" value={historyVaccineFilter} onChange={(e) => setHistoryVaccineFilter(e.target.value)} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" type="date" label="From" value={historyFromDate} onChange={(e) => setHistoryFromDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" type="date" label="To" value={historyToDate} onChange={(e) => setHistoryToDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="vaccination-due-status-label">Due status</InputLabel>
                        <Select labelId="vaccination-due-status-label" label="Due status" value={historyDueStatus} onChange={(e) => setHistoryDueStatus(e.target.value as typeof historyDueStatus)}>
                          {["ALL", "DUE", "OVERDUE", "NOT_DUE"].map((status) => (
                            <MenuItem key={status} value={status}>{dueStatusLabel(status)}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                  </Grid>
                  {!filteredHistoryRows.length ? (
                    <CompactEmptyState title="No vaccination history found." subtitle="Use the filters above to narrow the vaccination history." />
                  ) : (
                    <CompactTableFrame maxHeight={440}>
                      <Table size="small" stickyHeader sx={{ minWidth: 1320 }}>
                        <TableHead>
                          <TableRow>
                            <TableCell>Date</TableCell>
                            <TableCell>Patient</TableCell>
                            <TableCell>Vaccine</TableCell>
                            <TableCell>Source</TableCell>
                            <TableCell>Dose</TableCell>
                            <TableCell>Batch number</TableCell>
                            <TableCell>Expiry</TableCell>
                            <TableCell>Manufacturer</TableCell>
                            <TableCell>Administered by</TableCell>
                            <TableCell>Proof</TableCell>
                            <TableCell>Verification</TableCell>
                            <TableCell>Next due date</TableCell>
                            <TableCell>Bill number</TableCell>
                            <TableCell>Bill status</TableCell>
                            <TableCell>Recorded by</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {filteredHistoryRows.map((row) => (
                            <TableRow key={row.id}>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2">{row.givenDate}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.createdAt}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || row.patientNumber || "-"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{row.patientMobile || "-"}</Typography>
                                  <Typography variant="caption" color="text.secondary">{[row.patientGender, row.patientAgeYears != null ? `${row.patientAgeYears} yrs` : null].filter(Boolean).join(" • ") || "-"}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{row.vaccineName}</TableCell>
                              <TableCell>
                                <Chip size="small" label={row.source || "INTERNAL"} color={row.source === "EXTERNAL" ? "secondary" : "default"} variant="outlined" />
                              </TableCell>
                              <TableCell>{row.doseNumber ?? "-"}</TableCell>
                              <TableCell>{row.inventoryBatchNumber || row.batchNumber || "-"}</TableCell>
                              <TableCell>{row.inventoryBatchExpiryDate || "-"}</TableCell>
                              <TableCell>{row.inventoryBatchManufacturer || "-"}</TableCell>
                              <TableCell>{row.administeredByUserName || "-"}</TableCell>
                              <TableCell>
                                {row.proofDocumentId ? (
                                  <Button size="small" onClick={() => void openVaccinationProof(row)}>View Card</Button>
                                ) : (
                                  "-"
                                )}
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.verifiedStatus || "-"} color={row.verifiedStatus === "VERIFIED" ? "success" : row.verifiedStatus === "REJECTED" ? "error" : "warning"} variant="outlined" />
                              </TableCell>
                              <TableCell><Chip size="small" label={row.nextDueDate || "-"} color={statusColor(row.nextDueDate)} /></TableCell>
                              <TableCell>{row.billNumber || "-"}</TableCell>
                              <TableCell>
                                <Stack spacing={0.5}>
                                  <Chip size="small" label={row.billStatus || "Unbilled"} color={row.billStatus === "PAID" ? "success" : row.billStatus ? "warning" : "default"} variant="outlined" />
                                  {row.billStatus === "PAID" ? <Chip size="small" label="Receipt ready" color="success" variant="outlined" /> : null}
                                </Stack>
                              </TableCell>
                              <TableCell>{row.recordedByUserName || row.administeredByUserName || "-"}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </CompactTableFrame>
                  )}
                </Stack>
              </CardContent>
            </Card>

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
