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
import {
  addCarePilotLeadNote,
  convertCarePilotLead,
  createCarePilotLead,
  getCarePilotLeadAnalyticsSummary,
  getClinicUsers,
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
  type CarePilotLeadPriority,
  type CarePilotLeadSource,
  type CarePilotLeadStatus,
  type ClinicUser,
} from "../../../api/clinicApi";

const STATUSES: CarePilotLeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "FOLLOW_UP_REQUIRED", "APPOINTMENT_BOOKED", "CONVERTED", "LOST", "SPAM"];
const SOURCES: CarePilotLeadSource[] = ["WEBSITE", "WALK_IN", "PHONE_CALL", "WHATSAPP", "FACEBOOK", "GOOGLE_ADS", "REFERRAL", "CAMPAIGN", "MANUAL", "OTHER"];
const PRIORITIES: CarePilotLeadPriority[] = ["LOW", "MEDIUM", "HIGH"];

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

const emptyDraft = (): LeadDraft => ({ firstName: "", lastName: "", phone: "", email: "", source: "MANUAL", sourceDetails: "", status: "NEW", priority: "MEDIUM", notes: "", tags: "", nextFollowUpAt: "", campaignId: "" });
const emptyConvertDraft = (): ConvertDraft => ({ bookAppointment: false, doctorUserId: "", appointmentDate: "", appointmentTime: "", reason: "", notes: "", priority: "NORMAL" });

function activityColor(type: string) {
  if (type.includes("CONVERTED") || type.includes("BOOKED")) return "success" as const;
  if (type.includes("LOST") || type.includes("SPAM")) return "default" as const;
  if (type.includes("FOLLOW_UP")) return "warning" as const;
  return "info" as const;
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

  const [convertOpen, setConvertOpen] = React.useState(false);
  const [convertLead, setConvertLead] = React.useState<CarePilotLead | null>(null);
  const [convertDraft, setConvertDraft] = React.useState<ConvertDraft>(emptyConvertDraft());

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) { setLoading(false); return; }
    setLoading(true); setError(null);
    try {
      const forcedStatus = tab === "CONVERTED" ? "CONVERTED" : tab === "LOST" ? "LOST" : statusFilter || undefined;
      const followUpDue = tab === "FOLLOW_UPS";
      const [leadList, summary, campaignRows, userRows] = await Promise.all([
        listCarePilotLeads(auth.accessToken, auth.tenantId, { status: forcedStatus, source: sourceFilter || undefined, priority: priorityFilter || undefined, search: search || undefined, followUpDue, page, size }),
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
  }, [auth.accessToken, auth.tenantId, canView, tab, statusFilter, sourceFilter, priorityFilter, search, page, size]);

  React.useEffect(() => { void load(); }, [load]);

  const openCreate = () => { setEditorLead(null); setDraft(emptyDraft()); setActivities([]); setEditorOpen(true); };

  const openEdit = async (lead: CarePilotLead) => {
    setEditorLead(lead);
    setDraft({ firstName: lead.firstName, lastName: lead.lastName || "", phone: lead.phone, email: lead.email || "", source: lead.source, sourceDetails: lead.sourceDetails || "", status: lead.status, priority: lead.priority, notes: lead.notes || "", tags: lead.tags || "", nextFollowUpAt: lead.nextFollowUpAt ? lead.nextFollowUpAt.slice(0, 16) : "", campaignId: lead.campaignId || "" });
    setEditorOpen(true);
    if (!auth.accessToken || !auth.tenantId) return;
    setActivityLoading(true);
    try {
      const response = await listCarePilotLeadActivities(auth.accessToken, auth.tenantId, lead.id);
      setActivities(response.rows);
    } finally { setActivityLoading(false); }
  };

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    const payload = { firstName: draft.firstName, lastName: draft.lastName || null, phone: draft.phone, email: draft.email || null, source: draft.source, sourceDetails: draft.sourceDetails || null, status: draft.status, priority: draft.priority, notes: draft.notes || null, tags: draft.tags || null, campaignId: draft.campaignId || null, nextFollowUpAt: draft.nextFollowUpAt ? new Date(draft.nextFollowUpAt).toISOString() : null };
    try {
      if (editorLead) await updateCarePilotLead(auth.accessToken, auth.tenantId, editorLead.id, payload); else await createCarePilotLead(auth.accessToken, auth.tenantId, payload);
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

  const scheduleFollowUp = async (lead: CarePilotLead) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    const when = window.prompt("Next follow-up datetime (YYYY-MM-DDTHH:mm)");
    if (!when) return;
    await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, lead.id, { status: "FOLLOW_UP_REQUIRED", nextFollowUpAt: new Date(when).toISOString(), comment: "Follow-up scheduled" });
    setToast("Follow-up scheduled");
    await load();
  };

  const markFollowUpCompleted = async (lead: CarePilotLead) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    await updateCarePilotLeadStatus(auth.accessToken, auth.tenantId, lead.id, { status: "CONTACTED", nextFollowUpAt: null, comment: "Follow-up completed" });
    setToast("Follow-up completed");
    await load();
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

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box><Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot Leads</Typography><Typography variant="body2" color="text.secondary">Lead intake, timeline, follow-up, and conversion workflow.</Typography></Box>
        <Stack direction="row" spacing={1}><Button variant="outlined" onClick={() => void load()}>Refresh</Button>{canMutate ? <Button variant="contained" onClick={openCreate}>New Lead</Button> : null}</Stack>
      </Box>

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
          <Grid size={{ xs: 6, md: 3 }} sx={{ display: "flex", gap: 1 }}><Button variant="contained" onClick={() => { setPage(0); void load(); }}>Apply</Button><Button variant="text" onClick={() => { setSearch(""); setStatusFilter(""); setSourceFilter(""); setPriorityFilter(""); setPage(0); }}>Reset</Button></Grid>
        </Grid>
      </CardContent></Card>

      <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0); }}><Tab value="PIPELINE" label="Pipeline" /><Tab value="FOLLOW_UPS" label="Follow-ups Due" /><Tab value="CONVERTED" label="Converted" /><Tab value="LOST" label="Lost" /></Tabs>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
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
                    <TableCell>{campaigns.find((c) => c.id === lead.campaignId)?.name || "-"}</TableCell>
                    <TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void openEdit(lead)}>View/Edit</Button>{canMutate && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => void scheduleFollowUp(lead)}>Schedule Follow-up</Button> : null}{canMutate && lead.status === "FOLLOW_UP_REQUIRED" ? <Button size="small" onClick={() => void markFollowUpCompleted(lead)}>Mark Done</Button> : null}{canMutate && lead.status !== "CONVERTED" ? <Button size="small" onClick={() => openConvert(lead)}>Convert</Button> : null}{lead.convertedPatientId ? <Button size="small" onClick={() => navigate(`/patients/${lead.convertedPatientId}`)}>Open Patient</Button> : null}{lead.bookedAppointmentId ? <Button size="small" onClick={() => navigate(`/appointments`)}>Open Appointment</Button> : null}{lead.campaignId ? <Button size="small" onClick={() => navigate(`/carepilot/campaigns?campaignId=${lead.campaignId}`)}>Open Campaign</Button> : null}</Stack></TableCell>
                  </TableRow>
                );
              })}</TableBody>
            </Table>
          )}
          <Box sx={{ display: "flex", justifyContent: "space-between", mt: 1 }}><Typography variant="body2" color="text.secondary">Total: {total}</Typography><Stack direction="row" spacing={1}><Button size="small" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>Previous</Button><Typography variant="body2" sx={{ mt: 0.7 }}>Page {page + 1}</Typography><Button size="small" disabled={(page + 1) * size >= total} onClick={() => setPage((p) => p + 1)}>Next</Button></Stack></Box>
        </CardContent></Card>
      ) : null}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>{editorLead ? "Lead Detail" : "Create Lead"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ pt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="First name" value={draft.firstName} onChange={(e) => setDraft((d) => ({ ...d, firstName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Last name" value={draft.lastName} onChange={(e) => setDraft((d) => ({ ...d, lastName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Phone" value={draft.phone} onChange={(e) => setDraft((d) => ({ ...d, phone: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={draft.email} onChange={(e) => setDraft((d) => ({ ...d, email: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Source</InputLabel><Select value={draft.source} label="Source" onChange={(e) => setDraft((d) => ({ ...d, source: String(e.target.value) as CarePilotLeadSource }))}>{SOURCES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={draft.status} label="Status" onChange={(e) => setDraft((d) => ({ ...d, status: String(e.target.value) as CarePilotLeadStatus }))}>{STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Priority</InputLabel><Select value={draft.priority} label="Priority" onChange={(e) => setDraft((d) => ({ ...d, priority: String(e.target.value) as CarePilotLeadPriority }))}>{PRIORITIES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={draft.campaignId} label="Campaign" onChange={(e) => setDraft((d) => ({ ...d, campaignId: String(e.target.value) }))}><MenuItem value="">None</MenuItem>{campaigns.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</Select></FormControl></Grid>
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

      <Snackbar open={Boolean(toast)} autoHideDuration={3500} onClose={() => setToast(null)} message={toast || ""} />
    </Stack>
  );
}
