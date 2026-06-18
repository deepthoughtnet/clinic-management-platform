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
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { firstZodError, leadCreateSchema, leadFilterSchema, leadImportSchema, leadUpdateSchema } from "@deepthoughtnet/form-validation-kit";
import {
  addCarePilotLeadNote,
  convertCarePilotLead,
  createCarePilotLead,
  exportCarePilotLeadsCsv,
  getCarePilotLeadAnalyticsSummary,
  getCarePilotLeadImportTemplate,
  getClinicUsers,
  importCarePilotLeadsCsv,
  listCarePilotCampaigns,
  listCarePilotLeadActivities,
  listCarePilotLeads,
  updateCarePilotLead,
  updateCarePilotLeadStatus,
  type AppointmentPriority,
  type CarePilotCampaign,
  type CarePilotLead,
  type CarePilotLeadActivity,
  type CarePilotLeadAnalyticsSummary,
  type CarePilotLeadCsvImportResult,
  type CarePilotLeadPriority,
  type CarePilotLeadSource,
  type CarePilotLeadStatus,
  type ClinicUser,
} from "../../../api/clinicApi";
import RequiredLabel from "../../../components/forms/RequiredLabel";

const STATUSES: CarePilotLeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "FOLLOW_UP_REQUIRED", "APPOINTMENT_BOOKED", "CONVERTED", "LOST", "SPAM"];
const SOURCES: CarePilotLeadSource[] = ["WEBSITE", "WEBINAR", "WALK_IN", "PHONE_CALL", "WHATSAPP", "FACEBOOK", "GOOGLE_ADS", "REFERRAL", "CAMPAIGN", "MANUAL", "AI_RECEPTIONIST", "OTHER"];
const PRIORITIES: CarePilotLeadPriority[] = ["LOW", "MEDIUM", "HIGH"];
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
};

const emptyDraft = (): LeadDraft => ({ firstName: "", lastName: "", phone: "", email: "", source: "MANUAL", sourceDetails: "", status: "NEW", priority: "MEDIUM", notes: "", tags: "", nextFollowUpAt: "", campaignId: "", assignedToAppUserId: "" });
const emptyConvertDraft = (): ConvertDraft => ({ bookAppointment: false, doctorUserId: "", appointmentDate: "", appointmentTime: "", reason: "", notes: "", priority: "NORMAL" });
const emptyFollowUpDraft = (): FollowUpDraft => ({ date: "", time: "" });

function normalizeIndianMobile(value: string) {
  return value.replace(/[^0-9]/g, "").slice(0, 10);
}

function isValidIndianMobile(value: string) {
  return /^[0-9]{10}$/.test(normalizeIndianMobile(value));
}

function toDateTimeInputParts(value?: string | null): FollowUpDraft {
  if (!value) return emptyFollowUpDraft();
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return emptyFollowUpDraft();
  const local = new Date(parsed.getTime() - (parsed.getTimezoneOffset() * 60 * 1000)).toISOString();
  return { date: local.slice(0, 10), time: local.slice(11, 16) };
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
  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("AUDITOR") || auth.rolesUpper.includes("RECEPTIONIST") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("RECEPTIONIST") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<CarePilotLead[]>([]);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotLeadAnalyticsSummary | null>(null);
  const [viewMode, setViewMode] = React.useState<"TABLE" | "KANBAN">("TABLE");

  const [tab, setTab] = React.useState<"PIPELINE" | "FOLLOW_UPS" | "CONVERTED" | "LOST">("PIPELINE");
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
      const forcedStatus = tab === "CONVERTED" ? "CONVERTED" : tab === "LOST" ? "LOST" : parsed.data.status || undefined;
      const followUpDue = tab === "FOLLOW_UPS";
      const requestPage = parsed.data.page ?? 0;
      const requestSize = parsed.data.size ?? (viewMode === "KANBAN" ? 200 : size);
      const [leadList, summary, campaignRows, userRows] = await Promise.all([
        listCarePilotLeads(auth.accessToken, auth.tenantId, { status: forcedStatus, source: parsed.data.source || undefined, priority: parsed.data.priority || undefined, search: parsed.data.search || undefined, followUpDue, page: requestPage, size: requestSize }),
        getCarePilotLeadAnalyticsSummary(auth.accessToken, auth.tenantId),
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        getClinicUsers(auth.accessToken, auth.tenantId),
      ]);
      setRows(leadList.rows); setTotal(leadList.total); setAnalytics(summary); setCampaigns(campaignRows); setUsers(userRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load leads");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, tab, statusFilter, sourceFilter, priorityFilter, search, page, size, viewMode]);

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

  const openCreate = () => { setEditorLead(null); setDraft(emptyDraft()); setActivities([]); setEditorOpen(true); };

  const openEdit = async (lead: CarePilotLead) => {
    setEditorLead(lead);
    setDraft({
      firstName: lead.firstName,
      lastName: lead.lastName || "",
      phone: normalizeIndianMobile(lead.phone),
      email: lead.email || "",
      source: lead.source,
      sourceDetails: lead.sourceDetails || "",
      status: lead.status,
      priority: lead.priority,
      notes: lead.notes || "",
      tags: lead.tags || "",
      nextFollowUpAt: lead.nextFollowUpAt ? lead.nextFollowUpAt.slice(0, 16) : "",
      campaignId: lead.campaignId || "",
      assignedToAppUserId: lead.assignedToAppUserId || "",
    });
    setEditorOpen(true);
    await loadActivitiesForLead(lead.id);
  };

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    if (!isValidIndianMobile(draft.phone)) {
      setToast("Enter a valid 10-digit mobile number.");
      return;
    }
    const payload = {
      firstName: draft.firstName,
      lastName: draft.lastName || null,
      phone: normalizeIndianMobile(draft.phone),
      email: draft.email || null,
      source: draft.source,
      sourceDetails: draft.sourceDetails || null,
      status: draft.status,
      priority: draft.priority,
      notes: draft.notes || null,
      tags: draft.tags || null,
      campaignId: draft.campaignId || null,
      assignedToAppUserId: draft.assignedToAppUserId || null,
      nextFollowUpAt: draft.nextFollowUpAt ? new Date(draft.nextFollowUpAt).toISOString() : null,
    };
    const parsed = (editorLead ? leadUpdateSchema : leadCreateSchema).safeParse(payload);
    if (!parsed.success) {
      setToast(firstZodError(parsed.error));
      return;
    }
    try {
      if (editorLead) await updateCarePilotLead(auth.accessToken, auth.tenantId, editorLead.id, parsed.data); else await createCarePilotLead(auth.accessToken, auth.tenantId, parsed.data);
      setToast(editorLead ? "Lead updated" : "Lead created");
      setEditorOpen(false);
      await load();
    } catch (err) { setToast(err instanceof Error ? err.message : "Save failed"); }
  };

  const addNote = async () => {
    if (!editorLead || !auth.accessToken || !auth.tenantId || !canMutate || !note.trim()) return;
    try {
      await addCarePilotLeadNote(auth.accessToken, auth.tenantId, editorLead.id, note.trim());
      setNote("");
      await openEdit(editorLead);
      await load();
    } catch (err) { setToast(err instanceof Error ? err.message : "Failed to add note"); }
  };

  const openFollowUpDialog = (lead: CarePilotLead) => {
    setFollowUpLead(lead);
    setFollowUpDraft(toDateTimeInputParts(lead.nextFollowUpAt));
    setFollowUpOpen(true);
  };

  const scheduleFollowUp = async () => {
    if (!followUpLead || !auth.accessToken || !auth.tenantId || !canMutate) return;
    if (!followUpDraft.date || !followUpDraft.time) {
      setToast("Date and time are required");
      return;
    }
    try {
      const nextFollowUpAt = new Date(`${followUpDraft.date}T${followUpDraft.time}`).toISOString();
      await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, followUpLead.id, {
        status: "FOLLOW_UP_REQUIRED",
        nextFollowUpAt,
        comment: "Follow-up scheduled",
      });
      if (editorLead?.id === followUpLead.id) {
        setDraft((current) => ({ ...current, nextFollowUpAt: `${followUpDraft.date}T${followUpDraft.time}` }));
        await loadActivitiesForLead(followUpLead.id);
      }
      setFollowUpOpen(false);
      setFollowUpLead(null);
      setToast("Follow-up scheduled");
      await load();
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to schedule follow-up");
    }
  };

  const markFollowUpCompleted = async (lead: CarePilotLead) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, lead.id, { status: "CONTACTED", nextFollowUpAt: null, comment: "Follow-up completed" });
    if (editorLead?.id === lead.id) {
      setDraft((current) => ({ ...current, nextFollowUpAt: "" }));
      await loadActivitiesForLead(lead.id);
    }
    setToast("Follow-up completed");
    await load();
  };

  const downloadTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    try {
      const csv = await getCarePilotLeadImportTemplate(auth.accessToken, auth.tenantId);
      downloadCsv("carepilot-leads-import-template.csv", csv);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to download template");
    }
  };

  const exportCsv = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const forcedStatus = tab === "CONVERTED" ? "CONVERTED" : tab === "LOST" ? "LOST" : statusFilter || undefined;
      const followUpDue = tab === "FOLLOW_UPS";
      const csv = await exportCarePilotLeadsCsv(auth.accessToken, auth.tenantId, {
        status: forcedStatus,
        source: sourceFilter || undefined,
        priority: priorityFilter || undefined,
        search: search || undefined,
        followUpDue,
      });
      downloadCsv("carepilot-leads-export.csv", csv);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to export leads");
    }
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !auth.accessToken || !auth.tenantId || !canMutate) return;
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
    if (!convertLead || !auth.accessToken || !auth.tenantId || !canMutate) return;
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

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use CarePilot leads.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to CarePilot leads.</Alert>;

  const userName = (id?: string | null) => users.find((u) => u.appUserId === id)?.displayName || id || "-";
  const campaignName = (id?: string | null) => campaigns.find((c) => c.id === id)?.name || "-";
  const groupedKanbanRows = KANBAN_COLUMNS.map((status) => ({ status, rows: rows.filter((lead) => lead.status === status) }));

  const renderLeadActions = (lead: CarePilotLead, compact = false) => (
    <Stack direction={compact ? "column" : "row"} spacing={1} justifyContent={compact ? "flex-start" : "flex-end"} alignItems={compact ? "stretch" : "center"}>
      <Button size="small" onClick={() => void openEdit(lead)}>View/Edit</Button>
      {canMutate && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => openFollowUpDialog(lead)}>Follow-up</Button> : null}
      {canMutate && lead.status === "FOLLOW_UP_REQUIRED" ? <Button size="small" onClick={() => void markFollowUpCompleted(lead)}>Mark Done</Button> : null}
      {canMutate && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => openConvert(lead)}>Convert</Button> : null}
    </Stack>
  );

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box><Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot Leads</Typography><Typography variant="body2" color="text.secondary">Lead intake, timeline, follow-up, and conversion workflow.</Typography></Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          <Button variant="outlined" onClick={() => void exportCsv()}>Export CSV</Button>
          {canMutate ? <Button variant="outlined" onClick={() => void downloadTemplate()}>Download Template</Button> : null}
          {canMutate ? <Button variant="outlined" onClick={openImportFilePicker}>Import CSV</Button> : null}
          {canMutate ? <Button variant="contained" onClick={openCreate}>New Lead</Button> : null}
        </Stack>
      </Box>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImportFile} />

      {!canMutate ? <Alert severity="info">Read-only mode for your role.</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">New Leads</Typography><Typography variant="h5">{analytics?.newLeads ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Qualified</Typography><Typography variant="h5">{analytics?.qualifiedLeads ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Due Today</Typography><Typography variant="h5">{analytics?.followUpsDueToday ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Overdue</Typography><Typography variant="h5">{analytics?.overdueFollowUps ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Converted</Typography><Typography variant="h5">{analytics?.convertedLeads ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2 }}><Card><CardContent><Typography variant="caption">Conv. Rate</Typography><Typography variant="h5">{(analytics?.conversionRate ?? 0).toFixed(1)}%</Typography></CardContent></Card></Grid>
      </Grid>

      <Card><CardContent>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Search" value={search} onChange={(e) => setSearch(e.target.value)} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={statusFilter} label="Status" onChange={(e) => setStatusFilter(String(e.target.value) as CarePilotLeadStatus | "")}><MenuItem value="">All</MenuItem>{STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Source</InputLabel><Select value={sourceFilter} label="Source" onChange={(e) => setSourceFilter(String(e.target.value) as CarePilotLeadSource | "")}><MenuItem value="">All</MenuItem>{SOURCES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Priority</InputLabel><Select value={priorityFilter} label="Priority" onChange={(e) => setPriorityFilter(String(e.target.value) as CarePilotLeadPriority | "")}><MenuItem value="">All</MenuItem>{PRIORITIES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
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
          {rows.length === 0 ? <Alert severity="info">No leads found for current filters.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Phone</TableCell><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>Priority</TableCell><TableCell>Assigned To</TableCell><TableCell>Next Follow-up</TableCell><TableCell>Last Activity</TableCell><TableCell>Campaign</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
              <TableBody>{rows.map((lead) => {
                const overdue = !!lead.nextFollowUpAt && new Date(lead.nextFollowUpAt).getTime() < Date.now() && !["CONVERTED", "LOST", "SPAM"].includes(lead.status);
                return (
                  <TableRow key={lead.id}>
                    <TableCell>{lead.fullName || `${lead.firstName} ${lead.lastName || ""}`.trim()}</TableCell>
                    <TableCell>{lead.phone}</TableCell>
                    <TableCell>{lead.source}</TableCell>
                    <TableCell><Chip size="small" label={lead.status} color={lead.status === "CONVERTED" ? "success" : lead.status === "LOST" || lead.status === "SPAM" ? "default" : "info"} /></TableCell>
                    <TableCell><Chip size="small" label={lead.priority} color={lead.priority === "HIGH" ? "error" : lead.priority === "MEDIUM" ? "warning" : "default"} /></TableCell>
                    <TableCell>{userName(lead.assignedToAppUserId)}</TableCell>
                    <TableCell>{lead.nextFollowUpAt ? <Stack direction="row" spacing={1} alignItems="center"><span>{new Date(lead.nextFollowUpAt).toLocaleString()}</span>{overdue ? <Chip size="small" color="error" label="Overdue" /> : null}</Stack> : "-"}</TableCell>
                    <TableCell>{lead.lastActivityAt ? new Date(lead.lastActivityAt).toLocaleString() : "-"}</TableCell>
                    <TableCell>{campaignName(lead.campaignId)}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {renderLeadActions(lead)}
                        {lead.convertedPatientId ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null}
                        {lead.bookedAppointmentId ? <Button size="small" onClick={() => navigate(`/appointments`)}>Open Appointment</Button> : null}
                        {lead.campaignId ? <Button size="small" onClick={() => navigate(`/carepilot/campaigns?campaignId=${lead.campaignId}`)}>Open Campaign</Button> : null}
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
        rows.length === 0 ? <Alert severity="info">No leads found for current filters.</Alert> : (
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", alignItems: "start" }}>
            {groupedKanbanRows.map((column) => (
              <Card key={column.status} variant="outlined">
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{column.status}</Typography>
                    <Chip size="small" label={column.rows.length} />
                  </Stack>
                  <Divider sx={{ mb: 1.5 }} />
                  <Stack spacing={1.25}>
                    {column.rows.length === 0 ? <Typography variant="body2" color="text.secondary">No leads</Typography> : column.rows.map((lead) => {
                      const overdue = !!lead.nextFollowUpAt && new Date(lead.nextFollowUpAt).getTime() < Date.now() && !["CONVERTED", "LOST", "SPAM"].includes(lead.status);
                      return (
                        <Card key={lead.id} variant="outlined">
                          <CardContent sx={{ display: "grid", gap: 1.1 }}>
                            <Stack direction="row" justifyContent="space-between" spacing={1}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{lead.fullName || `${lead.firstName} ${lead.lastName || ""}`.trim()}</Typography>
                              <Chip size="small" label={lead.priority} color={lead.priority === "HIGH" ? "error" : lead.priority === "MEDIUM" ? "warning" : "default"} />
                            </Stack>
                            <Typography variant="body2">{lead.phone}</Typography>
                            <Typography variant="caption" color="text.secondary">Source: {lead.source}</Typography>
                            <Typography variant="caption" color="text.secondary">Campaign: {campaignName(lead.campaignId)}</Typography>
                            <Typography variant="caption" color="text.secondary">Assigned: {userName(lead.assignedToAppUserId)}</Typography>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                              <Typography variant="caption" color="text.secondary">
                                Next follow-up: {lead.nextFollowUpAt ? new Date(lead.nextFollowUpAt).toLocaleString() : "-"}
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

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>{editorLead ? "Lead Detail" : "Create Lead"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ pt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="First name" value={draft.firstName} onChange={(e) => setDraft((d) => ({ ...d, firstName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Last name" value={draft.lastName} onChange={(e) => setDraft((d) => ({ ...d, lastName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Phone" />}
                value={draft.phone}
                onChange={(e) => setDraft((d) => ({ ...d, phone: normalizeIndianMobile(e.target.value) }))}
                inputProps={{ inputMode: "numeric", maxLength: 10, pattern: "[0-9]*" }}
                error={Boolean(draft.phone) && !isValidIndianMobile(draft.phone)}
                helperText={Boolean(draft.phone) && !isValidIndianMobile(draft.phone) ? "Enter a valid 10-digit mobile number." : ""}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={draft.email} onChange={(e) => setDraft((d) => ({ ...d, email: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Source</InputLabel><Select value={draft.source} label="Source" onChange={(e) => setDraft((d) => ({ ...d, source: String(e.target.value) as CarePilotLeadSource }))}>{SOURCES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={draft.status} label="Status" onChange={(e) => setDraft((d) => ({ ...d, status: String(e.target.value) as CarePilotLeadStatus }))}>{STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Priority</InputLabel><Select value={draft.priority} label="Priority" onChange={(e) => setDraft((d) => ({ ...d, priority: String(e.target.value) as CarePilotLeadPriority }))}>{PRIORITIES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={draft.campaignId} label="Campaign" onChange={(e) => setDraft((d) => ({ ...d, campaignId: String(e.target.value) }))}><MenuItem value="">None</MenuItem>{campaigns.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Assigned To</InputLabel><Select value={draft.assignedToAppUserId} label="Assigned To" onChange={(e) => setDraft((d) => ({ ...d, assignedToAppUserId: String(e.target.value) }))}><MenuItem value="">Unassigned</MenuItem>{users.map((u) => <MenuItem key={u.appUserId} value={u.appUserId}>{u.displayName || u.appUserId}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Source details" value={draft.sourceDetails} onChange={(e) => setDraft((d) => ({ ...d, sourceDetails: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="datetime-local" label="Next follow-up" value={draft.nextFollowUpAt} onChange={(e) => setDraft((d) => ({ ...d, nextFollowUpAt: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Tags" value={draft.tags} onChange={(e) => setDraft((d) => ({ ...d, tags: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth multiline minRows={3} label="Notes" value={draft.notes} onChange={(e) => setDraft((d) => ({ ...d, notes: e.target.value }))} /></Grid>
          </Grid>

          {editorLead ? (
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Timeline</Typography>
              {activityLoading ? <CircularProgress size={20} /> : activities.length === 0 ? <Alert severity="info">No activity yet.</Alert> : (
                <Stack spacing={1}>{activities.map((a) => <Card key={a.id} variant="outlined"><CardContent sx={{ py: 1.2 }}><Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between"><Stack direction="row" spacing={1} alignItems="center"><Chip size="small" color={activityColor(a.activityType)} label={a.activityType} /><Typography variant="body2" sx={{ fontWeight: 700 }}>{a.title}</Typography></Stack><Typography variant="caption" color="text.secondary">{new Date(a.createdAt).toLocaleString()}</Typography></Stack>{a.description ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{a.description}</Typography> : null}</CardContent></Card>)}</Stack>
              )}
              {canMutate ? <Stack direction="row" spacing={1} sx={{ mt: 1 }}><TextField size="small" fullWidth label="Add note" value={note} onChange={(e) => setNote(e.target.value)} /><Button variant="contained" onClick={() => void addNote()}>Add</Button></Stack> : null}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions><Button onClick={() => setEditorOpen(false)}>Close</Button>{canMutate ? <Button variant="contained" onClick={() => void save()}>Save</Button> : null}</DialogActions>
      </Dialog>

      <Dialog open={convertOpen} onClose={() => setConvertOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Convert Lead</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Alert severity="info">Convert lead to patient. Optionally book appointment immediately.</Alert>
            <FormControl><InputLabel>Conversion Mode</InputLabel><Select value={convertDraft.bookAppointment ? "CONVERT_AND_BOOK" : "CONVERT_ONLY"} label="Conversion Mode" onChange={(e) => setConvertDraft((d) => ({ ...d, bookAppointment: String(e.target.value) === "CONVERT_AND_BOOK" }))}><MenuItem value="CONVERT_ONLY">Convert only</MenuItem><MenuItem value="CONVERT_AND_BOOK">Convert and book appointment</MenuItem></Select></FormControl>
            {convertDraft.bookAppointment ? (
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Doctor</InputLabel><Select value={convertDraft.doctorUserId} label="Doctor" onChange={(e) => setConvertDraft((d) => ({ ...d, doctorUserId: String(e.target.value) }))}>{users.filter((u) => (u.membershipRole || "").includes("DOCTOR")).map((u) => <MenuItem key={u.appUserId} value={u.appUserId}>{u.displayName || u.appUserId}</MenuItem>)}</Select></FormControl></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="date" label="Date" value={convertDraft.appointmentDate} onChange={(e) => setConvertDraft((d) => ({ ...d, appointmentDate: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="time" label="Time" value={convertDraft.appointmentTime} onChange={(e) => setConvertDraft((d) => ({ ...d, appointmentTime: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Reason" value={convertDraft.reason} onChange={(e) => setConvertDraft((d) => ({ ...d, reason: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Notes" value={convertDraft.notes} onChange={(e) => setConvertDraft((d) => ({ ...d, notes: e.target.value }))} /></Grid>
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
