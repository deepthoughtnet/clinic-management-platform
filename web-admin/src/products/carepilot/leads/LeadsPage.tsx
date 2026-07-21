import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
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
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import {
  ENGAGE_ANALYTICS_VIEW,
  ENGAGE_LEAD_ASSIGN,
  ENGAGE_LEAD_BOOK_APPOINTMENT,
  ENGAGE_LEAD_CONVERT,
  ENGAGE_LEAD_CREATE,
  ENGAGE_LEAD_EDIT,
  ENGAGE_LEAD_EXPORT,
  ENGAGE_LEAD_FOLLOW_UP,
  ENGAGE_LEAD_IMPORT,
  ENGAGE_LEAD_VIEW,
  ENGAGE_LEAD_VIEW_ALL,
  ENGAGE_LEAD_VIEW_AUDIT,
} from "../../../auth/permissions";
import { firstZodError, leadFilterSchema, leadImportSchema } from "@deepthoughtnet/form-validation-kit";
import {
  addCarePilotLeadNote,
  convertCarePilotLead,
  createCarePilotLead,
  exportCarePilotLeadsCsv,
  getCarePilotLeadAnalyticsSummary,
  getCarePilotLead,
  getCarePilotLeadImportTemplate,
  getPatient,
  getClinicUsers,
  importCarePilotLeadsCsv,
  lookupCarePilotCampaigns,
  listCarePilotLeadActivities,
  listCarePilotLeads,
  updateCarePilotLead,
  updateCarePilotLeadStatus,
  type AppointmentPriority,
  type CarePilotCampaignLookup,
  type CarePilotLead,
  type CarePilotLeadActivity,
  type CarePilotLeadAnalyticsSummary,
  type CarePilotLeadCsvImportResult,
  type CarePilotLeadPriority,
  type CarePilotLeadSource,
  type CarePilotLeadStatus,
  type ClinicUser,
  type PatientDetail,
} from "../../../api/clinicApi";
import RequiredLabel from "../../../components/forms/RequiredLabel";
import CampaignLookupField from "../components/CampaignLookupField";
import { formatCarePilotAssigneeLabel, formatCarePilotDateTime } from "../shared/carepilotFormatting";
import { canOpenLinkedPatient, canViewLinkedPatientConsultationHistory } from "../shared/patientNavigation";
import {
  LEAD_PRIORITY_OPTIONS,
  LEAD_SELECT_MENU_PROPS,
  LEAD_SOURCE_OPTIONS,
  LEAD_STATUS_OPTIONS,
  formatLeadTimelineDescription,
  leadActivityLabel,
  leadPriorityLabel,
  leadSourceLabel,
  leadStatusLabel,
} from "../shared/leadFormatting";
import { useCarePilotTenantTimezone } from "../shared/useCarePilotTenantTimezone";
import { buildLeadCreatePayload, mapLeadApiErrorToFieldErrors, toLeadDateTimeInputValue, validateLeadDraft } from "./leadFormUtils";
const KANBAN_COLUMNS: CarePilotLeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "FOLLOW_UP_REQUIRED", "APPOINTMENT_BOOKED", "CONVERTED", "LOST", "SPAM"];

type LeadDraft = {
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  source: CarePilotLeadSource;
  sourceDetails: string;
  status: CarePilotLeadStatus;
  priority: CarePilotLeadPriority;
  notes: string;
  tags: string;
  nextFollowUpAt: string;
  campaignId: string;
  assignedToAppUserId: string;
};

type ConvertDraft = {
  bookAppointment: boolean;
  doctorUserId: string;
  appointmentDate: string;
  appointmentTime: string;
  reason: string;
  notes: string;
  priority: AppointmentPriority;
};

type FollowUpDraft = {
  date: string;
  time: string;
  note: string;
};

const emptyDraft = (): LeadDraft => ({ firstName: "", lastName: "", phone: "", email: "", source: "MANUAL", sourceDetails: "", status: "NEW", priority: "MEDIUM", notes: "", tags: "", nextFollowUpAt: "", campaignId: "", assignedToAppUserId: "" });
const emptyConvertDraft = (): ConvertDraft => ({ bookAppointment: false, doctorUserId: "", appointmentDate: "", appointmentTime: "", reason: "", notes: "", priority: "NORMAL" });
const emptyFollowUpDraft = (): FollowUpDraft => ({ date: "", time: "", note: "" });

function validationSummaryMessage(count: number) {
  return `Please correct ${count} highlighted field${count === 1 ? "" : "s"}.`;
}

function leadTabForStatus(status: CarePilotLeadStatus): "PIPELINE" | "FOLLOW_UPS" | "CONVERTED" | "LOST" {
  if (status === "CONVERTED") return "CONVERTED";
  if (status === "LOST" || status === "SPAM") return "LOST";
  if (status === "FOLLOW_UP_REQUIRED") return "FOLLOW_UPS";
  return "PIPELINE";
}

function isTerminalLeadStatus(status?: CarePilotLeadStatus | null) {
  return status === "CONVERTED" || status === "LOST" || status === "SPAM";
}

function toDateTimeInputParts(value?: string | null): FollowUpDraft {
  if (!value) return emptyFollowUpDraft();
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return emptyFollowUpDraft();
  const local = new Date(parsed.getTime() - (parsed.getTimezoneOffset() * 60 * 1000)).toISOString();
  return { date: local.slice(0, 10), time: local.slice(11, 16), note: "" };
}

function activityColor(type: string) {
  if (type.includes("CONVERTED") || type.includes("BOOKED")) return "success" as const;
  if (type.includes("LOST") || type.includes("SPAM")) return "default" as const;
  if (type.includes("FOLLOW_UP")) return "warning" as const;
  return "info" as const;
}

function downloadCsv(filename: string, csv: string) {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export default function LeadsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const canView = auth.hasPermission(ENGAGE_LEAD_VIEW)
    || auth.hasPermission(ENGAGE_LEAD_VIEW_ALL)
    || auth.hasPermission(ENGAGE_LEAD_VIEW_AUDIT);
  const canCreate = auth.hasPermission(ENGAGE_LEAD_CREATE);
  const canEdit = auth.hasPermission(ENGAGE_LEAD_EDIT);
  const canAssign = auth.hasPermission(ENGAGE_LEAD_ASSIGN);
  const canFollowUp = auth.hasPermission(ENGAGE_LEAD_FOLLOW_UP);
  const canConvert = auth.hasPermission(ENGAGE_LEAD_CONVERT);
  const canBookAppointment = auth.hasPermission(ENGAGE_LEAD_BOOK_APPOINTMENT);
  const canSaveLead = canCreate || canEdit;
  const canMutate = canSaveLead || canAssign || canFollowUp || canConvert || canBookAppointment;
  const canViewAnalytics = auth.hasPermission(ENGAGE_ANALYTICS_VIEW);
  const canViewCampaigns = auth.hasPermission("engage.campaign.view")
    || auth.hasPermission("engage.campaign.manage")
    || auth.hasPermission("engage.audit.view");
  const canImport = auth.hasPermission(ENGAGE_LEAD_IMPORT);
  const canExport = auth.hasPermission(ENGAGE_LEAD_EXPORT);
  const canViewUsers = auth.hasPermission("user.read");
  const canOpenPatient = canOpenLinkedPatient(auth);
  const { clinicTimeZone } = useCarePilotTenantTimezone(auth.accessToken, auth.tenantId);

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [saveError, setSaveError] = React.useState<string | null>(null);
  const [saving, setSaving] = React.useState(false);
  const saveInFlightRef = React.useRef(false);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [rows, setRows] = React.useState<CarePilotLead[]>([]);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaignLookup[]>([]);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotLeadAnalyticsSummary | null>(null);
  const [viewMode, setViewMode] = React.useState<"TABLE" | "KANBAN">("TABLE");

  const [tab, setTab] = React.useState<"PIPELINE" | "FOLLOW_UPS" | "CONVERTED" | "LOST">(() => {
    const raw = (searchParams.get("tab") || "").trim().toLowerCase();
    if (raw === "follow-ups" || raw === "follow_ups" || raw === "followups") return "FOLLOW_UPS";
    if (raw === "converted") return "CONVERTED";
    if (raw === "lost") return "LOST";
    return "PIPELINE";
  });
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<CarePilotLeadStatus | "">("");
  const [sourceFilter, setSourceFilter] = React.useState<CarePilotLeadSource | "">("");
  const [priorityFilter, setPriorityFilter] = React.useState<CarePilotLeadPriority | "">("");
  const [page, setPage] = React.useState(0);
  const [size] = React.useState(25);
  const [total, setTotal] = React.useState(0);

  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editorLead, setEditorLead] = React.useState<CarePilotLead | null>(null);
  const [draft, setDraft] = React.useState<LeadDraft>(emptyDraft());
  const editorContentRef = React.useRef<HTMLDivElement | null>(null);
  const firstNameInputRef = React.useRef<HTMLInputElement | null>(null);
  const phoneInputRef = React.useRef<HTMLInputElement | null>(null);
  const emailInputRef = React.useRef<HTMLInputElement | null>(null);
  const nextFollowUpInputRef = React.useRef<HTMLInputElement | null>(null);

  const [activities, setActivities] = React.useState<CarePilotLeadActivity[]>([]);
  const [activityLoading, setActivityLoading] = React.useState(false);
  const [note, setNote] = React.useState("");
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [importResult, setImportResult] = React.useState<CarePilotLeadCsvImportResult | null>(null);
  const [importResultOpen, setImportResultOpen] = React.useState(false);
  const [followUpOpen, setFollowUpOpen] = React.useState(false);
  const [followUpLead, setFollowUpLead] = React.useState<CarePilotLead | null>(null);
  const [followUpDraft, setFollowUpDraft] = React.useState<FollowUpDraft>(emptyFollowUpDraft());

  const [convertOpen, setConvertOpen] = React.useState(false);
  const [convertLead, setConvertLead] = React.useState<CarePilotLead | null>(null);
  const [convertDraft, setConvertDraft] = React.useState<ConvertDraft>(emptyConvertDraft());
  const openedLeadIdRef = React.useRef<string | null>(null);
  const [linkedPatientDetail, setLinkedPatientDetail] = React.useState<PatientDetail | null>(null);

  React.useEffect(() => {
    const rawTab = (searchParams.get("tab") || "").trim().toLowerCase();
    const nextTab = rawTab === "follow-ups" || rawTab === "follow_ups" || rawTab === "followups"
      ? "FOLLOW_UPS"
      : rawTab === "converted"
        ? "CONVERTED"
        : rawTab === "lost"
          ? "LOST"
          : "PIPELINE";
    setTab((current) => (current === nextTab ? current : nextTab));
  }, [searchParams]);

  const clearSaveState = () => {
    setSaveError(null);
    setFieldErrors({});
    saveInFlightRef.current = false;
  };

  const clearLeadFieldError = (field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setSaveError(null);
  };

  const focusLeadField = (field: string) => {
    const refs: Record<string, React.RefObject<HTMLInputElement | null>> = {
      firstName: firstNameInputRef,
      phone: phoneInputRef,
      email: emailInputRef,
      nextFollowUpAt: nextFollowUpInputRef,
    };
    const ref = refs[field];
    if (ref?.current) {
      ref.current.focus();
      ref.current.scrollIntoView({ block: "center" });
      return;
    }
    const element = editorContentRef.current?.querySelector(`[data-lead-field="${field}"]`) as HTMLElement | null
      || document.querySelector(`[data-lead-field="${field}"]`) as HTMLElement | null;
    element?.scrollIntoView({ block: "center" });
    element?.focus?.();
  };

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) { setLoading(false); return; }
    const parsed = leadFilterSchema.safeParse({
      search,
      status: statusFilter || undefined,
      source: sourceFilter || undefined,
      priority: priorityFilter || undefined,
      page: viewMode === "KANBAN" ? 0 : page,
      size: viewMode === "KANBAN" ? 200 : size,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      setLoading(false);
      return;
    }
      setLoading(true); setError(null);
    try {
      const forcedStatus = tab === "CONVERTED" ? "CONVERTED" : tab === "LOST" ? "LOST" : tab === "PIPELINE" ? undefined : parsed.data.status || undefined;
      const followUpDue = tab === "FOLLOW_UPS";
      const pipelineOnly = tab === "PIPELINE";
      const requestPage = parsed.data.page ?? 0;
      const requestSize = parsed.data.size ?? (viewMode === "KANBAN" ? 200 : size);
      const [leadList, summary, campaignRows, userRows] = await Promise.all([
        listCarePilotLeads(auth.accessToken, auth.tenantId, { status: forcedStatus, source: parsed.data.source || undefined, priority: parsed.data.priority || undefined, search: parsed.data.search || undefined, followUpDue, pipelineOnly, page: requestPage, size: requestSize }),
        canViewAnalytics
          ? getCarePilotLeadAnalyticsSummary(auth.accessToken, auth.tenantId)
          : Promise.resolve(null),
        canViewCampaigns
          ? lookupCarePilotCampaigns(auth.accessToken, auth.tenantId, "", 50)
          : Promise.resolve([] as CarePilotCampaignLookup[]),
        canViewUsers
          ? getClinicUsers(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as ClinicUser[]),
      ]);
      setRows(leadList.rows); setTotal(leadList.total); setAnalytics(summary); setCampaigns(campaignRows); setUsers(userRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load leads");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, canViewAnalytics, canViewCampaigns, canViewUsers, tab, statusFilter, sourceFilter, priorityFilter, search, page, size, viewMode]);

  React.useEffect(() => { void load(); }, [load]);

  const loadActivitiesForLead = React.useCallback(async (leadId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setActivityLoading(true);
    try {
      const response = await listCarePilotLeadActivities(auth.accessToken, auth.tenantId, leadId);
      setActivities(response.rows);
    } finally {
      setActivityLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadLinkedPatient() {
      if (!editorOpen || !editorLead?.convertedPatientId || !canOpenPatient || !auth.accessToken || !auth.tenantId) {
        setLinkedPatientDetail(null);
        return;
      }
      try {
        const detail = await getPatient(auth.accessToken, auth.tenantId, editorLead.convertedPatientId);
        if (!cancelled) {
          setLinkedPatientDetail(detail);
        }
      } catch {
        if (!cancelled) {
          setLinkedPatientDetail(null);
        }
      }
    }
    void loadLinkedPatient();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, canOpenPatient, editorOpen, editorLead?.convertedPatientId]);

  React.useEffect(() => {
    const leadId = searchParams.get("leadId") || "";
    if (!leadId) {
      openedLeadIdRef.current = null;
      return;
    }
    if (!auth.accessToken || !auth.tenantId || !canView) return;
    if (openedLeadIdRef.current === leadId) return;
    let cancelled = false;
    async function openLeadFromRoute() {
      try {
        const lead = await getCarePilotLead(auth.accessToken as string, auth.tenantId as string, leadId);
        if (cancelled) return;
        openedLeadIdRef.current = leadId;
        const nextTab = leadTabForStatus(lead.status);
        const nextParams = new URLSearchParams(searchParams);
        nextParams.set("tab", nextTab === "FOLLOW_UPS" ? "follow-ups" : nextTab.toLowerCase());
        nextParams.set("status", lead.status.toLowerCase());
        nextParams.set("leadId", lead.id);
        setSearchParams(nextParams, { replace: true });
        setTab(nextTab);
        setStatusFilter(lead.status);
        setEditorLead(lead);
        setLinkedPatientDetail(null);
        setDraft({
          firstName: lead.firstName,
          lastName: lead.lastName || "",
          phone: lead.phone,
          email: lead.email || "",
          source: lead.source,
          sourceDetails: lead.sourceDetails || "",
          status: lead.status,
          priority: lead.priority,
          notes: lead.notes || "",
          tags: lead.tags || "",
          nextFollowUpAt: isTerminalLeadStatus(lead.status) ? "" : (lead.nextFollowUpAt ? toLeadDateTimeInputValue(lead.nextFollowUpAt) : ""),
          campaignId: lead.campaignId || "",
          assignedToAppUserId: lead.assignedToAppUserId || "",
        });
        clearSaveState();
        setEditorOpen(true);
        await loadActivitiesForLead(lead.id);
      } catch {
        if (!cancelled) {
          setToast("Unable to open lead");
        }
      }
    }
    void openLeadFromRoute();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, canView, loadActivitiesForLead, searchParams, setSearchParams]);

  const openCreate = () => {
    setEditorLead(null);
    setDraft(emptyDraft());
    setActivities([]);
    setLinkedPatientDetail(null);
    clearSaveState();
    setEditorOpen(true);
  };

  const openEdit = async (lead: CarePilotLead) => {
    setEditorLead(lead);
    setLinkedPatientDetail(null);
    setDraft({
      firstName: lead.firstName,
      lastName: lead.lastName || "",
      phone: lead.phone,
      email: lead.email || "",
      source: lead.source,
      sourceDetails: lead.sourceDetails || "",
      status: lead.status,
      priority: lead.priority,
      notes: lead.notes || "",
      tags: lead.tags || "",
      nextFollowUpAt: isTerminalLeadStatus(lead.status) ? "" : (lead.nextFollowUpAt ? toLeadDateTimeInputValue(lead.nextFollowUpAt) : ""),
      campaignId: lead.campaignId || "",
      assignedToAppUserId: lead.assignedToAppUserId || "",
    });
    clearSaveState();
    setEditorOpen(true);
    await loadActivitiesForLead(lead.id);
  };

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId || !canPersistLeadForm || saving || saveInFlightRef.current) return;
    const validation = validateLeadDraft(draft, users);
    if (Object.keys(validation.fieldErrors).length > 0) {
      const summary = validationSummaryMessage(Object.keys(validation.fieldErrors).length);
      setFieldErrors(validation.fieldErrors);
      setSaveError(summary);
      setToast(summary);
      window.setTimeout(() => focusLeadField(Object.keys(validation.fieldErrors)[0]), 0);
      return;
    }

    const payload = buildLeadCreatePayload(draft, validation.normalizedPhone) as Partial<CarePilotLead>;
    setSaving(true);
    saveInFlightRef.current = true;
    setSaveError(null);
    setFieldErrors({});
    try {
      if (editorLead) {
        await updateCarePilotLead(auth.accessToken, auth.tenantId, editorLead.id, payload);
      } else {
        await createCarePilotLead(auth.accessToken, auth.tenantId, payload);
      }
      setToast(editorLead ? "Lead updated" : "Lead created");
      setEditorOpen(false);
      clearSaveState();
      await load();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Save failed";
      const mappedErrors = mapLeadApiErrorToFieldErrors(message);
      if (Object.keys(mappedErrors).length > 0) {
        const summary = validationSummaryMessage(Object.keys(mappedErrors).length);
        setFieldErrors(mappedErrors);
        setSaveError(summary);
        setToast(summary);
        window.setTimeout(() => focusLeadField(Object.keys(mappedErrors)[0]), 0);
        return;
      }
      setSaveError(null);
      setToast("Unable to save lead. Please try again.");
    }
    finally {
      setSaving(false);
      saveInFlightRef.current = false;
    }
  };

  const addNote = async () => {
    if (!editorLead || !auth.accessToken || !auth.tenantId || !canAddLeadNote || !note.trim()) return;
    try {
      await addCarePilotLeadNote(auth.accessToken, auth.tenantId, editorLead.id, note.trim());
      setNote("");
      await openEdit(editorLead);
      await load();
    } catch (err) { setToast(err instanceof Error ? err.message : "Failed to add note"); }
  };

  const openFollowUpDialog = (lead: CarePilotLead) => {
    setFollowUpLead(lead);
    setFollowUpDraft({ ...toDateTimeInputParts(lead.nextFollowUpAt), note: "" });
    setFollowUpOpen(true);
  };

  const scheduleFollowUp = async () => {
    if (!followUpLead || !auth.accessToken || !auth.tenantId || !canFollowUp) return;
    if (!followUpDraft.date || !followUpDraft.time) {
      setToast("Date and time are required");
      return;
    }
    try {
      const nextFollowUpAt = new Date(`${followUpDraft.date}T${followUpDraft.time}`).toISOString();
      const followUpComment = followUpDraft.note.trim() || null;
      await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, followUpLead.id, {
        status: "FOLLOW_UP_REQUIRED",
        nextFollowUpAt,
        comment: followUpComment,
      });
      if (editorLead?.id === followUpLead.id) {
        setDraft((current) => ({ ...current, nextFollowUpAt: `${followUpDraft.date}T${followUpDraft.time}` }));
        await loadActivitiesForLead(followUpLead.id);
      }
      setFollowUpOpen(false);
      setFollowUpLead(null);
      setFollowUpDraft(emptyFollowUpDraft());
      setToast("Follow-up scheduled");
      await load();
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to schedule follow-up");
    }
  };

  const markFollowUpCompleted = async (lead: CarePilotLead) => {
    if (!auth.accessToken || !auth.tenantId || !canFollowUp) return;
    await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, lead.id, { status: "CONTACTED", nextFollowUpAt: null, comment: null });
    if (editorLead?.id === lead.id) {
      setDraft((current) => ({ ...current, nextFollowUpAt: "" }));
      await loadActivitiesForLead(lead.id);
    }
    setToast("Follow-up completed");
    await load();
  };

  const downloadTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId || !canImport) return;
    try {
      const csv = await getCarePilotLeadImportTemplate(auth.accessToken, auth.tenantId);
      downloadCsv("carepilot-leads-import-template.csv", csv);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to download template");
    }
  };

  const exportCsv = async () => {
    if (!auth.accessToken || !auth.tenantId || !canExport) return;
    try {
      const forcedStatus = tab === "CONVERTED" ? "CONVERTED" : tab === "LOST" ? "LOST" : tab === "PIPELINE" ? undefined : statusFilter || undefined;
      const followUpDue = tab === "FOLLOW_UPS";
      const pipelineOnly = tab === "PIPELINE";
      const csv = await exportCarePilotLeadsCsv(auth.accessToken, auth.tenantId, {
        status: forcedStatus,
        source: sourceFilter || undefined,
        priority: priorityFilter || undefined,
        search: search || undefined,
        followUpDue,
        pipelineOnly,
      });
      downloadCsv("carepilot-leads-export.csv", csv);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to export leads");
    }
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !auth.accessToken || !auth.tenantId || !canImport) return;
    try {
      const parsed = leadImportSchema.safeParse({ file });
      if (!parsed.success) {
        setToast(firstZodError(parsed.error));
        return;
      }
      const result = await importCarePilotLeadsCsv(auth.accessToken, auth.tenantId, file);
      setImportResult(result);
      setImportResultOpen(true);
      setToast("Lead CSV import completed");
      await load();
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to import leads");
    } finally {
      event.target.value = "";
    }
  };

  const openConvert = (lead: CarePilotLead) => {
    setConvertLead(lead);
    setConvertDraft(emptyConvertDraft());
    setConvertOpen(true);
  };

  const runConvert = async () => {
    if (!convertLead || !auth.accessToken || !auth.tenantId || !canConvert) return;
    try {
      const response = await convertCarePilotLead(auth.accessToken, auth.tenantId, convertLead.id, convertDraft.bookAppointment ? {
        bookAppointment: true,
        appointment: {
          doctorUserId: convertDraft.doctorUserId,
          appointmentDate: convertDraft.appointmentDate,
          appointmentTime: convertDraft.appointmentTime || null,
          reason: convertDraft.reason || null,
          notes: convertDraft.notes || null,
          priority: convertDraft.priority,
        },
      } : { bookAppointment: false });
      setConvertOpen(false);
      setToast(response.appointmentError ? `Converted, appointment failed: ${response.appointmentError}` : "Lead converted");
      await load();
    } catch (err) { setToast(err instanceof Error ? err.message : "Conversion failed"); }
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use Jeevanam Engage leads.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to Jeevanam Engage leads.</Alert>;

  const convertedLead = editorLead?.status === "CONVERTED";
  const canPersistLeadForm = editorLead ? (convertedLead ? canSaveLead : canMutate) : canCreate;
  const canAddLeadNote = editorLead ? (convertedLead ? canSaveLead : canMutate) : canMutate;
  const conversionActivity = activities.find((activity) => activity.activityType === "CONVERTED_TO_PATIENT") || null;

  const userName = (id?: string | null) => formatCarePilotAssigneeLabel(users.find((u) => u.appUserId === id), id);
  const campaignName = (id?: string | null) => {
    if (!id) return "-";
    const campaign = campaigns.find((c) => c.id === id);
    if (!campaign) return "Unknown campaign";
    return `${campaign.name} • ${campaign.campaignReference}`;
  };
  const formatLeadDateTime = (value?: string | null) => formatCarePilotDateTime(value, clinicTimeZone);
  const visibleNextFollowUp = (lead: CarePilotLead) => (isTerminalLeadStatus(lead.status) ? null : lead.nextFollowUpAt);
  const leadEmptyState = () => {
    if (search.trim() || sourceFilter || priorityFilter) return "No leads match the current filters.";
    if (tab === "CONVERTED") return "No converted leads found.";
    if (tab === "LOST") return "No lost leads found.";
    if (tab === "FOLLOW_UPS") return "No active follow-ups are due.";
    return "No active leads in the pipeline.";
  };
  const groupedKanbanRows = KANBAN_COLUMNS.map((status) => ({ status, rows: rows.filter((lead) => lead.status === status) }));

  const renderLeadActions = (lead: CarePilotLead, compact = false) => (
    <Stack direction={compact ? "column" : "row"} spacing={1} justifyContent={compact ? "flex-start" : "flex-end"} alignItems={compact ? "stretch" : "center"}>
      <Button size="small" onClick={() => void openEdit(lead)}>{lead.status === "CONVERTED" ? "View Details" : "View/Edit"}</Button>
      {canFollowUp && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => openFollowUpDialog(lead)}>Follow-up</Button> : null}
      {canFollowUp && lead.status === "FOLLOW_UP_REQUIRED" ? <Button size="small" onClick={() => void markFollowUpCompleted(lead)}>Mark Done</Button> : null}
      {canConvert && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => openConvert(lead)}>Convert</Button> : null}
      {canViewCampaigns && lead.campaignId ? <Button size="small" onClick={() => navigate(`/carepilot/campaigns?campaignId=${lead.campaignId}`)}>Open Campaign</Button> : null}
      {lead.status === "CONVERTED" && lead.convertedPatientId && canOpenPatient ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null}
      {lead.status === "CONVERTED" && lead.convertedPatientId && canViewLinkedPatientConsultationHistory(auth) ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>View Consultation History</Button> : null}
    </Stack>
  );

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box><Typography variant="h4" sx={{ fontWeight: 900 }}>Jeevanam Engage Leads</Typography><Typography variant="body2" color="text.secondary">Lead intake, timeline, follow-up, and conversion workflow.</Typography></Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canExport ? <Button variant="outlined" onClick={() => void exportCsv()}>Export CSV</Button> : null}
          {canImport ? <Button variant="outlined" onClick={() => void downloadTemplate()}>Download Template</Button> : null}
          {canImport ? <Button variant="outlined" onClick={openImportFilePicker}>Import CSV</Button> : null}
          {canCreate ? <Button variant="contained" onClick={openCreate}>New Lead</Button> : null}
        </Stack>
      </Box>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImportFile} />

      {!canMutate ? <Alert severity="info">Read-only mode for your role.</Alert> : null}

      {canViewAnalytics ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">New Leads</Typography><Typography variant="h5">{analytics?.newLeads ?? 0}</Typography><Typography variant="caption" color="text.secondary">Current leads with status NEW.</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Qualified</Typography><Typography variant="h5">{analytics?.qualifiedLeads ?? 0}</Typography><Typography variant="caption" color="text.secondary">Current leads with status QUALIFIED.</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Due Today</Typography><Typography variant="h5">{analytics?.followUpsDueToday ?? 0}</Typography><Typography variant="caption" color="text.secondary">Active non-terminal leads due today in tenant time.</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Overdue</Typography><Typography variant="h5">{analytics?.overdueFollowUps ?? 0}</Typography><Typography variant="caption" color="text.secondary">Active non-terminal leads overdue in tenant time.</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Converted</Typography><Typography variant="h5">{analytics?.convertedLeads ?? 0}</Typography><Typography variant="caption" color="text.secondary">Converted leads within the current dashboard scope.</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Conv. Rate</Typography><Typography variant="h5">{(analytics?.conversionRate ?? 0).toFixed(1)}%</Typography><Typography variant="caption" color="text.secondary">Converted leads divided by scoped eligible leads.</Typography></CardContent></Card></Grid>
        </Grid>
      ) : null}

      <Card><CardContent>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Search" value={search} onChange={(e) => setSearch(e.target.value)} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <FormControl fullWidth>
              <InputLabel>Status</InputLabel>
              <Select value={statusFilter} label="Status" onChange={(e) => setStatusFilter(String(e.target.value) as CarePilotLeadStatus | "")} MenuProps={LEAD_SELECT_MENU_PROPS}>
                <MenuItem value="">All</MenuItem>
                {LEAD_STATUS_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <FormControl fullWidth>
              <InputLabel>Source</InputLabel>
              <Select value={sourceFilter} label="Source" onChange={(e) => setSourceFilter(String(e.target.value) as CarePilotLeadSource | "")} MenuProps={LEAD_SELECT_MENU_PROPS}>
                <MenuItem value="">All</MenuItem>
                {LEAD_SOURCE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}>
            <FormControl fullWidth>
              <InputLabel>Priority</InputLabel>
              <Select value={priorityFilter} label="Priority" onChange={(e) => setPriorityFilter(String(e.target.value) as CarePilotLeadPriority | "")} MenuProps={LEAD_SELECT_MENU_PROPS}>
                <MenuItem value="">All</MenuItem>
                {LEAD_PRIORITY_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 6, md: 3 }} sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            <Button variant="contained" onClick={() => { setPage(0); void load(); }}>Apply</Button>
            <Button variant="text" onClick={() => { setSearch(""); setStatusFilter(""); setSourceFilter(""); setPriorityFilter(""); setPage(0); }}>Reset</Button>
            <Button variant={viewMode === "TABLE" ? "contained" : "outlined"} onClick={() => setViewMode("TABLE")}>Table View</Button>
            <Button variant={viewMode === "KANBAN" ? "contained" : "outlined"} onClick={() => setViewMode("KANBAN")}>Kanban View</Button>
          </Grid>
        </Grid>
      </CardContent></Card>

      <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0); }}><Tab value="PIPELINE" label="Pipeline" /><Tab value="FOLLOW_UPS" label="Follow-ups Due" /><Tab value="CONVERTED" label="Converted" /><Tab value="LOST" label="Lost" /></Tabs>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && viewMode === "TABLE" ? (
        <Card><CardContent>
          {rows.length === 0 ? <Alert severity="info">{leadEmptyState()}</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Phone</TableCell><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>Priority</TableCell><TableCell>Assigned To</TableCell><TableCell>Next Follow-up</TableCell><TableCell>Last Activity</TableCell><TableCell>Campaign</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
              <TableBody>{rows.map((lead) => {
                const nextFollowUpAt = visibleNextFollowUp(lead);
                const overdue = !!nextFollowUpAt && new Date(nextFollowUpAt).getTime() < Date.now() && !isTerminalLeadStatus(lead.status);
                return (
                  <TableRow key={lead.id}>
                    <TableCell>{lead.fullName || `${lead.firstName} ${lead.lastName || ""}`.trim()}</TableCell>
                    <TableCell>{lead.phone}</TableCell>
                    <TableCell title={leadSourceLabel(lead.source)}>{leadSourceLabel(lead.source)}</TableCell>
                    <TableCell><Chip size="small" label={leadStatusLabel(lead.status)} color={lead.status === "CONVERTED" ? "success" : lead.status === "LOST" || lead.status === "SPAM" ? "default" : "info"} /></TableCell>
                    <TableCell><Chip size="small" label={leadPriorityLabel(lead.priority)} color={lead.priority === "HIGH" ? "error" : lead.priority === "MEDIUM" ? "warning" : "default"} /></TableCell>
                    <TableCell>{userName(lead.assignedToAppUserId)}</TableCell>
                    <TableCell>{nextFollowUpAt ? <Stack direction="row" spacing={1} alignItems="center"><span>{formatLeadDateTime(nextFollowUpAt)}</span>{overdue ? <Chip size="small" color="error" label="Overdue" /> : null}</Stack> : "—"}</TableCell>
                    <TableCell>{formatLeadDateTime(lead.lastActivityAt)}</TableCell>
                    <TableCell>{campaignName(lead.campaignId)}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {renderLeadActions(lead)}
                        {lead.convertedPatientId && canOpenPatient ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null}
                        {lead.bookedAppointmentId ? <Button size="small" onClick={() => navigate(`/appointments`)}>Open Appointment</Button> : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}</TableBody>
            </Table>
          )}
          <Box sx={{ display: "flex", justifyContent: "space-between", mt: 1 }}><Typography variant="body2" color="text.secondary">Total: {total}</Typography><Stack direction="row" spacing={1}><Button size="small" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>Previous</Button><Typography variant="body2" sx={{ mt: 0.7 }}>Page {page + 1}</Typography><Button size="small" disabled={(page + 1) * size >= total} onClick={() => setPage((p) => p + 1)}>Next</Button></Stack></Box>
        </CardContent></Card>
      ) : null}

      {!loading && viewMode === "KANBAN" ? (
        rows.length === 0 ? <Alert severity="info">{leadEmptyState()}</Alert> : (
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", alignItems: "start" }}>
            {groupedKanbanRows.map((column) => (
              <Card key={column.status} variant="outlined">
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{leadStatusLabel(column.status)}</Typography>
                    <Chip size="small" label={column.rows.length} />
                  </Stack>
                  <Divider sx={{ mb: 1.5 }} />
                  <Stack spacing={1.25}>
                    {column.rows.length === 0 ? <Typography variant="body2" color="text.secondary">No leads</Typography> : column.rows.map((lead) => {
                      const nextFollowUpAt = visibleNextFollowUp(lead);
                      const overdue = !!nextFollowUpAt && new Date(nextFollowUpAt).getTime() < Date.now() && !isTerminalLeadStatus(lead.status);
                      return (
                        <Card key={lead.id} variant="outlined">
                          <CardContent sx={{ display: "grid", gap: 1.1 }}>
                            <Stack direction="row" justifyContent="space-between" spacing={1}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{lead.fullName || `${lead.firstName} ${lead.lastName || ""}`.trim()}</Typography>
                              <Chip size="small" label={leadPriorityLabel(lead.priority)} color={lead.priority === "HIGH" ? "error" : lead.priority === "MEDIUM" ? "warning" : "default"} />
                            </Stack>
                            <Typography variant="body2">{lead.phone}</Typography>
                            <Typography variant="caption" color="text.secondary">Source: {leadSourceLabel(lead.source)}</Typography>
                            <Typography variant="caption" color="text.secondary">Campaign: {campaignName(lead.campaignId)}</Typography>
                            <Typography variant="caption" color="text.secondary">Assigned: {userName(lead.assignedToAppUserId)}</Typography>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                              <Typography variant="caption" color="text.secondary">
                                Next follow-up: {nextFollowUpAt ? formatLeadDateTime(nextFollowUpAt) : "—"}
                              </Typography>
                              {overdue ? <Chip size="small" color="error" label="Overdue" /> : null}
                            </Stack>
                            {renderLeadActions(lead, true)}
                          </CardContent>
                        </Card>
                      );
                    })}
                  </Stack>
                </CardContent>
              </Card>
            ))}
          </Box>
        )
      ) : null}

      <Dialog open={editorOpen} onClose={() => { setEditorOpen(false); clearSaveState(); }} fullWidth maxWidth="lg" scroll="paper" PaperProps={{ sx: { overflowX: "hidden" } }}>
        <DialogTitle>{editorLead ? (convertedLead ? "Converted Lead Details" : "Lead Detail") : "Create Lead"}</DialogTitle>
        <DialogContent ref={editorContentRef} sx={{ overflowX: "hidden" }}>
          {saveError ? <Alert severity="error" role="alert" aria-live="assertive" sx={{ mb: 1.5 }}>{saveError}</Alert> : null}
          {convertedLead ? (
            <Card variant="outlined" sx={{ mb: 2 }}>
              <CardContent sx={{ display: "grid", gap: 1 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Converted to Patient</Typography>
                {editorLead?.convertedPatientId && canOpenPatient ? (
                  linkedPatientDetail?.patient ? (
                    <Stack spacing={0.5}>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        Patient • {`${linkedPatientDetail.patient.firstName} ${linkedPatientDetail.patient.lastName || ""}`.trim()}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {linkedPatientDetail.patient.patientNumber}
                      </Typography>
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">Loading patient summary...</Typography>
                  )
                ) : null}
                {conversionActivity ? (
                  <>
                    <Typography variant="body2">Converted: {formatLeadDateTime(conversionActivity.createdAt)}</Typography>
                    <Typography variant="body2">Converted by: {userName(conversionActivity.createdByAppUserId)}</Typography>
                  </>
                ) : null}
                <Typography variant="body2">Source: {leadSourceLabel(editorLead?.source)}{editorLead?.sourceDetails ? ` · ${editorLead.sourceDetails}` : ""}</Typography>
                {editorLead?.convertedPatientId && canOpenPatient ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button size="small" onClick={() => navigate(`/patients/${editorLead.convertedPatientId}`)}>Open Patient</Button>
                    {canViewLinkedPatientConsultationHistory(auth) ? <Button size="small" onClick={() => navigate(`/patients/${editorLead.convertedPatientId}`)}>View Consultation History</Button> : null}
                  </Stack>
                ) : null}
                {editorLead && !editorLead.convertedPatientId && canOpenPatient ? (
                  <Alert severity="warning">Converted lead has no linked patient.</Alert>
                ) : null}
              </CardContent>
            </Card>
          ) : null}
          <Grid container spacing={1.5} sx={{ pt: 0.5, minWidth: 0 }}>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="firstName"><TextField fullWidth inputRef={firstNameInputRef} label="First name" value={draft.firstName} onChange={(e) => { clearLeadFieldError("firstName"); setDraft((d) => ({ ...d, firstName: e.target.value })); }} inputProps={{ maxLength: 60, readOnly: convertedLead }} disabled={convertedLead} error={Boolean(fieldErrors.firstName)} helperText={fieldErrors.firstName || (convertedLead ? "Read-only after conversion." : "")} /></Grid>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="lastName"><TextField fullWidth label="Last name" value={draft.lastName} onChange={(e) => setDraft((d) => ({ ...d, lastName: e.target.value }))} inputProps={{ maxLength: 60, readOnly: convertedLead }} disabled={convertedLead} helperText={convertedLead ? "Read-only after conversion." : ""} /></Grid>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="phone">
              <TextField
                fullWidth
                inputRef={phoneInputRef}
                label={<RequiredLabel text="Phone" />}
                value={draft.phone}
                onChange={(e) => { clearLeadFieldError("phone"); setDraft((d) => ({ ...d, phone: e.target.value })); }}
                inputProps={{ inputMode: "tel", readOnly: convertedLead }}
                disabled={convertedLead}
                error={Boolean(fieldErrors.phone)}
                helperText={fieldErrors.phone || (convertedLead ? "Read-only after conversion." : "")}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="email"><TextField fullWidth inputRef={emailInputRef} label="Email" value={draft.email} onChange={(e) => { clearLeadFieldError("email"); setDraft((d) => ({ ...d, email: e.target.value })); }} inputProps={{ maxLength: 120, readOnly: convertedLead }} disabled={convertedLead} error={Boolean(fieldErrors.email)} helperText={fieldErrors.email || (convertedLead ? "Read-only after conversion." : "")} /></Grid>
            <Grid size={{ xs: 12, md: 6, lg: 3 }} sx={{ minWidth: 0 }} data-lead-field="source">
              <FormControl fullWidth error={Boolean(fieldErrors.source)} disabled={convertedLead}>
                <InputLabel>Source</InputLabel>
                <Select value={draft.source} label="Source" onChange={(e) => { clearLeadFieldError("source"); setDraft((d) => ({ ...d, source: String(e.target.value) as CarePilotLeadSource })); }} MenuProps={LEAD_SELECT_MENU_PROPS}>
                  {LEAD_SOURCE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
                </Select>
                <FormHelperText>{fieldErrors.source || (convertedLead ? "Read-only after conversion." : "")}</FormHelperText>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6, lg: 3 }} sx={{ minWidth: 0 }} data-lead-field="status">
              <FormControl fullWidth error={Boolean(fieldErrors.status)} disabled={convertedLead}>
                <InputLabel>Status</InputLabel>
                <Select value={draft.status} label="Status" onChange={(e) => { clearLeadFieldError("status"); setDraft((d) => ({ ...d, status: String(e.target.value) as CarePilotLeadStatus })); }} MenuProps={LEAD_SELECT_MENU_PROPS}>
                  {LEAD_STATUS_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
                </Select>
                <FormHelperText>{fieldErrors.status || (convertedLead ? "Read-only after conversion." : "")}</FormHelperText>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6, lg: 3 }} sx={{ minWidth: 0 }} data-lead-field="priority">
              <FormControl fullWidth error={Boolean(fieldErrors.priority)} disabled={convertedLead}>
                <InputLabel>Priority</InputLabel>
                <Select value={draft.priority} label="Priority" onChange={(e) => { clearLeadFieldError("priority"); setDraft((d) => ({ ...d, priority: String(e.target.value) as CarePilotLeadPriority })); }} MenuProps={LEAD_SELECT_MENU_PROPS}>
                  {LEAD_PRIORITY_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value} title={option.label}>{option.label}</MenuItem>)}
                </Select>
                <FormHelperText>{fieldErrors.priority || (convertedLead ? "Read-only after conversion." : "")}</FormHelperText>
              </FormControl>
            </Grid>
            {canViewCampaigns ? (
              <Grid size={{ xs: 12, md: 6, lg: 3 }} sx={{ minWidth: 0 }} data-lead-field="campaignId">
                <CampaignLookupField
                  token={auth.accessToken}
                  tenantId={auth.tenantId}
                  value={draft.campaignId}
                  onChange={(value) => { clearLeadFieldError("campaignId"); setDraft((d) => ({ ...d, campaignId: value })); }}
                  label="Campaign"
                  helperText={fieldErrors.campaignId || (convertedLead && !canPersistLeadForm ? "Read-only after conversion." : "Optional. Associate this lead with a campaign.")}
                  error={Boolean(fieldErrors.campaignId)}
                  disabled={convertedLead && !canPersistLeadForm}
                />
              </Grid>
            ) : null}
            {canViewUsers ? (
              <Grid size={{ xs: 12, md: 6, lg: 4 }} sx={{ minWidth: 0 }} data-lead-field="assignedToAppUserId">
                <FormControl fullWidth error={Boolean(fieldErrors.assignedToAppUserId)} disabled={convertedLead && !canPersistLeadForm}>
                  <InputLabel>Assigned To</InputLabel>
                  <Select
                    value={draft.assignedToAppUserId}
                    label="Assigned To"
                    displayEmpty
                    renderValue={(selected) => {
                      const current = String(selected || "");
                      if (!current) {
                        return <Typography component="span" color="text.secondary">Select assignee</Typography>;
                      }
                      return formatCarePilotAssigneeLabel(users.find((u) => u.appUserId === current), current);
                    }}
                    onChange={(e) => { clearLeadFieldError("assignedToAppUserId"); setDraft((d) => ({ ...d, assignedToAppUserId: String(e.target.value) })); }}
                    MenuProps={LEAD_SELECT_MENU_PROPS}
                  >
                    <MenuItem value="">Unassigned</MenuItem>
                    {users.map((u) => <MenuItem key={u.appUserId} value={u.appUserId} title={formatCarePilotAssigneeLabel(u, u.appUserId)}>{formatCarePilotAssigneeLabel(u, u.appUserId)}</MenuItem>)}
                  </Select>
                  <FormHelperText>{fieldErrors.assignedToAppUserId || (convertedLead && !canPersistLeadForm ? "Read-only after conversion." : "Optional. Assign this lead to an active Engage user.")}</FormHelperText>
                </FormControl>
              </Grid>
            ) : null}
            <Grid size={{ xs: 12, md: 6, lg: 8 }} sx={{ minWidth: 0 }} data-lead-field="sourceDetails"><TextField fullWidth label="Source details" value={draft.sourceDetails} onChange={(e) => { clearLeadFieldError("sourceDetails"); setDraft((d) => ({ ...d, sourceDetails: e.target.value })); }} inputProps={{ maxLength: 120, readOnly: convertedLead && !canPersistLeadForm }} disabled={convertedLead && !canPersistLeadForm} helperText={convertedLead && !canPersistLeadForm ? "Read-only after conversion." : "Example: Google Search, referral name, event name"} /></Grid>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="nextFollowUpAt"><TextField fullWidth inputRef={nextFollowUpInputRef} type="datetime-local" label="Next follow-up" value={draft.nextFollowUpAt} onChange={(e) => { clearLeadFieldError("nextFollowUpAt"); setDraft((d) => ({ ...d, nextFollowUpAt: e.target.value })); }} InputLabelProps={{ shrink: true }} disabled={convertedLead} error={Boolean(fieldErrors.nextFollowUpAt)} helperText={fieldErrors.nextFollowUpAt || (convertedLead ? "Read-only after conversion." : `Optional follow-up date and time. Tenant time: ${clinicTimeZone}.`)} /></Grid>
            <Grid size={{ xs: 12, md: 6 }} sx={{ minWidth: 0 }} data-lead-field="tags"><TextField fullWidth label="Tags" value={draft.tags} onChange={(e) => { clearLeadFieldError("tags"); setDraft((d) => ({ ...d, tags: e.target.value })); }} inputProps={{ maxLength: 120, readOnly: convertedLead && !canPersistLeadForm }} disabled={convertedLead && !canPersistLeadForm} helperText={convertedLead && !canPersistLeadForm ? "Read-only after conversion." : "Add tags separated by commas."} /></Grid>
            <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }} data-lead-field="notes"><TextField fullWidth multiline minRows={3} label="Notes" value={draft.notes} onChange={(e) => { clearLeadFieldError("notes"); setDraft((d) => ({ ...d, notes: e.target.value })); }} inputProps={{ maxLength: 250, readOnly: convertedLead && !canPersistLeadForm }} disabled={convertedLead && !canPersistLeadForm} helperText={convertedLead && !canPersistLeadForm ? "Read-only after conversion." : ""} /></Grid>
          </Grid>

          {editorLead ? (
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Timeline</Typography>
              {activityLoading ? <CircularProgress size={20} /> : activities.length === 0 ? <Alert severity="info">No activity yet.</Alert> : (
                <Stack spacing={1}>
                  {activities.map((a) => (
                    <Card key={a.id} variant="outlined">
                      <CardContent sx={{ py: 1.2 }}>
                        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" sx={{ minWidth: 0, gap: 1 }}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                            <Chip size="small" color={activityColor(a.activityType)} label={leadActivityLabel(a.activityType)} />
                            <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 0, overflowWrap: "anywhere" }}>{leadActivityLabel(a.activityType)}</Typography>
                          </Stack>
                          <Typography component="time" dateTime={a.createdAt} variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
                            {formatLeadDateTime(a.createdAt)}
                          </Typography>
                        </Stack>
                        {formatLeadTimelineDescription(a, clinicTimeZone) ? (
                          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, minWidth: 0, overflowWrap: "anywhere" }}>
                            {formatLeadTimelineDescription(a, clinicTimeZone)}
                          </Typography>
                        ) : null}
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              )}
              {canAddLeadNote ? <Stack direction="row" spacing={1} sx={{ mt: 1 }}><TextField size="small" fullWidth label="Add note" value={note} onChange={(e) => setNote(e.target.value)} /><Button variant="contained" onClick={() => void addNote()}>Add</Button></Stack> : null}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions><Button onClick={() => { setEditorOpen(false); clearSaveState(); }}>Close</Button>{canPersistLeadForm ? <Button variant="contained" onClick={() => void save()} disabled={saving}>{saving ? "Saving..." : "Save"}</Button> : null}</DialogActions>
      </Dialog>

      <Dialog open={convertOpen} onClose={() => setConvertOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Convert Lead</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Alert severity="info">Convert lead to patient. Optionally book appointment immediately.</Alert>
            <FormControl>
              <InputLabel>Conversion Mode</InputLabel>
              <Select
                value={convertDraft.bookAppointment ? "CONVERT_AND_BOOK" : "CONVERT_ONLY"}
                label="Conversion Mode"
                onChange={(e) => setConvertDraft((d) => ({ ...d, bookAppointment: String(e.target.value) === "CONVERT_AND_BOOK" && canBookAppointment }))}
              >
                <MenuItem value="CONVERT_ONLY">Convert only</MenuItem>
                {canBookAppointment ? <MenuItem value="CONVERT_AND_BOOK">Convert and book appointment</MenuItem> : null}
              </Select>
            </FormControl>
            {convertDraft.bookAppointment && canBookAppointment ? (
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Doctor</InputLabel><Select value={convertDraft.doctorUserId} label="Doctor" onChange={(e) => setConvertDraft((d) => ({ ...d, doctorUserId: String(e.target.value) }))}>{users.filter((u) => (u.membershipRole || "").includes("DOCTOR")).map((u) => <MenuItem key={u.appUserId} value={u.appUserId}>{formatCarePilotAssigneeLabel(u, u.appUserId)}</MenuItem>)}</Select></FormControl></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="date" label="Date" value={convertDraft.appointmentDate} onChange={(e) => setConvertDraft((d) => ({ ...d, appointmentDate: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="time" label="Time" value={convertDraft.appointmentTime} onChange={(e) => setConvertDraft((d) => ({ ...d, appointmentTime: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Reason" value={convertDraft.reason} onChange={(e) => setConvertDraft((d) => ({ ...d, reason: e.target.value }))} inputProps={{ maxLength: 250 }} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Notes" value={convertDraft.notes} onChange={(e) => setConvertDraft((d) => ({ ...d, notes: e.target.value }))} inputProps={{ maxLength: 250 }} /></Grid>
              </Grid>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setConvertOpen(false)}>Cancel</Button><Button variant="contained" onClick={() => void runConvert()}>Convert</Button></DialogActions>
      </Dialog>

      <Dialog open={followUpOpen} onClose={() => setFollowUpOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Schedule Follow-up</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Alert severity="info">This will set the lead to follow-up required and schedule the next contact time.</Alert>
            {followUpLead ? (
              <Card variant="outlined">
                <CardContent sx={{ py: 1.25 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{followUpLead.fullName || `${followUpLead.firstName} ${followUpLead.lastName || ""}`.trim()}</Typography>
                  <Typography variant="body2" color="text.secondary">{followUpLead.phone}</Typography>
                </CardContent>
              </Card>
            ) : null}
            <Grid container spacing={1.5}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  type="date"
                  label="Follow-up date"
                  value={followUpDraft.date}
                  onChange={(e) => setFollowUpDraft((current) => ({ ...current, date: e.target.value }))}
                  InputLabelProps={{ shrink: true }}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  type="time"
                  label="Follow-up time"
                  value={followUpDraft.time}
                  onChange={(e) => setFollowUpDraft((current) => ({ ...current, time: e.target.value }))}
                  InputLabelProps={{ shrink: true }}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  label="Note"
                  value={followUpDraft.note}
                  onChange={(e) => setFollowUpDraft((current) => ({ ...current, note: e.target.value }))}
                  multiline
                  minRows={2}
                  helperText="Optional note saved to the lead timeline."
                  inputProps={{ maxLength: 250 }}
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFollowUpOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void scheduleFollowUp()}>Schedule</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importResultOpen} onClose={() => setImportResultOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Lead CSV Import Summary</DialogTitle>
        <DialogContent>
          {importResult ? (
            <Stack spacing={1.5} sx={{ pt: 1 }}>
              <Alert severity={importResult.failedCount > 0 ? "warning" : "success"}>
                Imported: {importResult.importedCount} | Skipped duplicates: {importResult.skippedDuplicateCount} | Failed: {importResult.failedCount}
              </Alert>
              {importResult.rowErrors.length > 0 ? (
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
          <Button onClick={() => setImportResultOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={Boolean(toast)} autoHideDuration={3500} onClose={() => setToast(null)} message={toast || ""} />
    </Stack>
  );
}
