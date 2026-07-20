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
import { mapZodErrors, engageWebinarRegistrationSchema, engageWebinarSchema, normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import {
  ENGAGE_WEBINAR_CANCEL,
  ENGAGE_WEBINAR_CREATE,
  ENGAGE_WEBINAR_EDIT,
  ENGAGE_WEBINAR_MANAGE_REGISTRATIONS,
  ENGAGE_WEBINAR_PUBLISH,
  ENGAGE_WEBINAR_RECORD_ATTENDANCE,
  ENGAGE_WEBINAR_RUN_AUTOMATION,
  ENGAGE_WEBINAR_VIEW,
  ENGAGE_WEBINAR_VIEW_ANALYTICS,
  ENGAGE_WEBINAR_VIEW_AUDIT,
} from "../../../auth/permissions";
import {
  createCarePilotWebinar,
  getCarePilotWebinarAnalyticsSummary,
  listCarePilotCampaigns,
  listCarePilotWebinarRegistrations,
  listCarePilotWebinars,
  markCarePilotWebinarAttendance,
  registerCarePilotWebinarAttendee,
  updateCarePilotWebinar,
  updateCarePilotWebinarStatus,
  type CarePilotCampaign,
  type CarePilotWebinar,
  type CarePilotWebinarAnalyticsSummary,
  type CarePilotWebinarRegistration,
  type CarePilotWebinarRegistrationStatus,
  type CarePilotWebinarStatus,
  type CarePilotWebinarType,
} from "../../../api/clinicApi";

const WEBINAR_TYPES: CarePilotWebinarType[] = ["HEALTH_AWARENESS", "WELLNESS", "CLINIC_EVENT", "MARKETING", "EDUCATIONAL", "OTHER"];

function statusColor(status: CarePilotWebinarStatus) {
  if (status === "SCHEDULED") return "info" as const;
  if (status === "LIVE") return "success" as const;
  if (status === "COMPLETED") return "default" as const;
  if (status === "CANCELLED") return "error" as const;
  return "warning" as const;
}

export default function WebinarsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const [tab, setTab] = React.useState<"UPCOMING" | "LIVE" | "COMPLETED" | "CANCELLED">("UPCOMING");
  const [statusFilter, setStatusFilter] = React.useState<CarePilotWebinarStatus | "">("");
  const [typeFilter, setTypeFilter] = React.useState<CarePilotWebinarType | "">("");

  const [rows, setRows] = React.useState<CarePilotWebinar[]>([]);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotWebinarAnalyticsSummary | null>(null);

  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<CarePilotWebinar | null>(null);
  const [form, setForm] = React.useState({
    title: "",
    description: "",
    webinarType: "HEALTH_AWARENESS" as CarePilotWebinarType,
    campaignId: "",
    webinarUrl: "",
    organizerName: "",
    organizerEmail: "",
    scheduledStartAt: "",
    scheduledEndAt: "",
    timezone: "UTC",
    capacity: "",
    registrationEnabled: true,
    reminderEnabled: true,
    followupEnabled: true,
    tags: "",
  });
  const [formErrors, setFormErrors] = React.useState<Record<string, string>>({});

  const [regOpen, setRegOpen] = React.useState(false);
  const [regWebinar, setRegWebinar] = React.useState<CarePilotWebinar | null>(null);
  const [registrations, setRegistrations] = React.useState<CarePilotWebinarRegistration[]>([]);
  const [regForm, setRegForm] = React.useState({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });
  const [regFormErrors, setRegFormErrors] = React.useState<Record<string, string>>({});
  const [regLoading, setRegLoading] = React.useState(false);
  const [regSaving, setRegSaving] = React.useState(false);
  const [regError, setRegError] = React.useState<string | null>(null);

  const canView = auth.hasPermission(ENGAGE_WEBINAR_VIEW)
    || auth.hasPermission(ENGAGE_WEBINAR_VIEW_ANALYTICS)
    || auth.hasPermission(ENGAGE_WEBINAR_VIEW_AUDIT);
  const canCreate = auth.hasPermission(ENGAGE_WEBINAR_CREATE);
  const canEdit = auth.hasPermission(ENGAGE_WEBINAR_EDIT);
  const canPublish = auth.hasPermission(ENGAGE_WEBINAR_PUBLISH);
  const canCancel = auth.hasPermission(ENGAGE_WEBINAR_CANCEL);
  const canManageRegistrations = auth.hasPermission(ENGAGE_WEBINAR_MANAGE_REGISTRATIONS);
  const canRecordAttendance = auth.hasPermission(ENGAGE_WEBINAR_RECORD_ATTENDANCE);
  const canRunAutomation = auth.hasPermission(ENGAGE_WEBINAR_RUN_AUTOMATION);
  const canMutate = canCreate || canEdit || canPublish || canCancel || canManageRegistrations || canRecordAttendance || canRunAutomation;

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [webinars, summary, campaignRows] = await Promise.all([
        listCarePilotWebinars(auth.accessToken, auth.tenantId, {
          status: statusFilter || undefined,
          webinarType: typeFilter || undefined,
          upcoming: tab === "UPCOMING" ? true : undefined,
          completed: tab === "COMPLETED" ? true : undefined,
          size: 100,
        }),
        getCarePilotWebinarAnalyticsSummary(auth.accessToken, auth.tenantId),
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
      ]);
      let filtered = webinars.rows;
      if (tab === "LIVE") filtered = filtered.filter((r) => r.status === "LIVE");
      if (tab === "CANCELLED") filtered = filtered.filter((r) => r.status === "CANCELLED");
      if (tab === "COMPLETED") filtered = filtered.filter((r) => r.status === "COMPLETED");
      setRows(filtered);
      setAnalytics(summary);
      setCampaigns(campaignRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load webinars");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, statusFilter, typeFilter, tab]);

  React.useEffect(() => { void load(); }, [load]);

  const openEditor = (row?: CarePilotWebinar) => {
    if (row) {
      setEditing(row);
      setForm({
        title: row.title,
        description: row.description || "",
        webinarType: row.webinarType,
        campaignId: row.campaignId || "",
        webinarUrl: row.webinarUrl || "",
        organizerName: row.organizerName || "",
        organizerEmail: row.organizerEmail || "",
        scheduledStartAt: row.scheduledStartAt.slice(0, 16),
        scheduledEndAt: row.scheduledEndAt.slice(0, 16),
        timezone: row.timezone,
        capacity: row.capacity == null ? "" : String(row.capacity),
        registrationEnabled: row.registrationEnabled,
        reminderEnabled: row.reminderEnabled,
        followupEnabled: row.followupEnabled,
        tags: row.tags || "",
      });
    } else {
      setEditing(null);
      setForm({
        title: "",
        description: "",
        webinarType: "HEALTH_AWARENESS",
        campaignId: "",
        webinarUrl: "",
        organizerName: "",
        organizerEmail: "",
        scheduledStartAt: "",
        scheduledEndAt: "",
        timezone: "UTC",
        capacity: "",
        registrationEnabled: true,
        reminderEnabled: true,
        followupEnabled: true,
        tags: "",
      });
    }
    setEditorOpen(true);
  };

  const saveWebinar = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    setSaving(true);
    try {
      const payload = {
        title: form.title,
        description: form.description || null,
        webinarType: form.webinarType,
        campaignId: form.campaignId || null,
        webinarUrl: form.webinarUrl || null,
        organizerName: form.organizerName || null,
        organizerEmail: form.organizerEmail || null,
        scheduledStartAt: new Date(form.scheduledStartAt).toISOString(),
        scheduledEndAt: new Date(form.scheduledEndAt).toISOString(),
        timezone: form.timezone,
        capacity: form.capacity ? Number(form.capacity) : null,
        registrationEnabled: form.registrationEnabled,
        reminderEnabled: form.reminderEnabled,
        followupEnabled: form.followupEnabled,
        tags: form.tags || null,
        status: editing?.status || "DRAFT",
      };
      const parsed = engageWebinarSchema.safeParse(payload);
      if (!parsed.success) {
        setFormErrors(mapZodErrors(parsed.error));
        return;
      }
      setFormErrors({});
      if (editing) await updateCarePilotWebinar(auth.accessToken, auth.tenantId, editing.id, payload);
      else await createCarePilotWebinar(auth.accessToken, auth.tenantId, payload);
      setEditorOpen(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save webinar");
    } finally {
      setSaving(false);
    }
  };

  const openRegistrations = async (row: CarePilotWebinar) => {
    if (!auth.accessToken || !auth.tenantId || !(canManageRegistrations || canRecordAttendance || canView)) return;
    setRegWebinar(row);
    setRegOpen(true);
    setRegistrations([]);
    setRegError(null);
    setRegLoading(true);
    try {
      const list = await listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, row.id, 0, 100);
      setRegistrations(list.rows);
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to load registrations");
    } finally {
      setRegLoading(false);
    }
  };

  const addRegistration = async () => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canManageRegistrations) return;
    setRegError(null);
    setRegSaving(true);
    try {
      const parsed = engageWebinarRegistrationSchema.safeParse(regForm);
      if (!parsed.success) {
        setRegFormErrors(mapZodErrors(parsed.error));
        return;
      }
      setRegFormErrors({});
      await registerCarePilotWebinarAttendee(auth.accessToken, auth.tenantId, regWebinar.id, {
        attendeeName: regForm.attendeeName,
        attendeeEmail: regForm.attendeeEmail || null,
        attendeePhone: regForm.attendeePhone ? (normalizeIndianMobileInput(regForm.attendeePhone) as string) : null,
        patientId: regForm.patientId || null,
        leadId: regForm.leadId || null,
      });
      const [list] = await Promise.all([
        listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100),
        load(),
      ]);
      setRegistrations(list.rows);
      setRegForm({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to register attendee");
    } finally {
      setRegSaving(false);
    }
  };

  const markAttendance = async (registrationId: string, status: CarePilotWebinarRegistrationStatus) => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canRecordAttendance) return;
    setRegError(null);
    setRegSaving(true);
    try {
      await markCarePilotWebinarAttendance(auth.accessToken, auth.tenantId, regWebinar.id, { registrationId, registrationStatus: status });
      const [list] = await Promise.all([
        listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100),
        load(),
      ]);
      setRegistrations(list.rows);
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to update attendance");
    } finally {
      setRegSaving(false);
    }
  };

  const quickStatus = async (row: CarePilotWebinar, status: CarePilotWebinarStatus) => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (status === "CANCELLED" && !canCancel) return;
    if (status !== "CANCELLED" && !canPublish) return;
    await updateCarePilotWebinarStatus(auth.accessToken, auth.tenantId, row.id, status);
    await load();
  };

  const openLead = (row: CarePilotWebinarRegistration) => {
    const searchValue = row.attendeePhone || row.attendeeEmail || row.leadName || row.leadId;
    if (!searchValue) return;
    navigate(`/carepilot/leads?search=${encodeURIComponent(searchValue)}`);
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use Webinar Automation.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to Webinar Automation.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Webinar Automation</Typography>
          <Typography variant="body2" color="text.secondary">Create events, track registrations/attendance, and run reminder/follow-up automation.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canCreate ? <Button variant="contained" onClick={() => openEditor()}>New Webinar</Button> : null}
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Upcoming</Typography><Typography variant="h5">{analytics?.upcomingWebinars ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Registrations</Typography><Typography variant="h5">{analytics?.totalRegistrations ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Attendance Rate</Typography><Typography variant="h5">{(analytics?.attendanceRate ?? 0).toFixed(1)}%</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">No-Shows</Typography><Typography variant="h5">{analytics?.noShowCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Completed</Typography><Typography variant="h5">{analytics?.completedWebinars ?? 0}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card><CardContent>
        <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
          <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Status</InputLabel><Select value={statusFilter} label="Status" onChange={(e) => setStatusFilter(e.target.value as CarePilotWebinarStatus | "")}><MenuItem value="">All</MenuItem>{["DRAFT", "SCHEDULED", "LIVE", "COMPLETED", "CANCELLED"].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl>
          <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Type</InputLabel><Select value={typeFilter} label="Type" onChange={(e) => setTypeFilter(e.target.value as CarePilotWebinarType | "")}><MenuItem value="">All</MenuItem>{WEBINAR_TYPES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl>
          <Button variant="outlined" onClick={() => { setStatusFilter(""); setTypeFilter(""); }}>Reset</Button>
        </Stack>
      </CardContent></Card>

      <Tabs value={tab} onChange={(_, next) => setTab(next)}><Tab value="UPCOMING" label="Upcoming" /><Tab value="LIVE" label="Live" /><Tab value="COMPLETED" label="Completed" /><Tab value="CANCELLED" label="Cancelled" /></Tabs>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card><CardContent>
          {rows.length === 0 ? <Alert severity="info">No webinars found for selected filters.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Webinar</TableCell><TableCell>Type</TableCell><TableCell>Start</TableCell><TableCell>Status</TableCell><TableCell>Reminder</TableCell><TableCell>URL</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {rows.map((row) => <TableRow key={row.id}><TableCell><Stack spacing={0.25}><Typography variant="body2" sx={{ fontWeight: 700 }}>{row.title}</Typography><Typography variant="caption" color="text.secondary">{row.organizerName || "-"}</Typography><Typography variant="caption" color="text.secondary">{row.campaignName ? `Campaign: ${row.campaignName}` : "Campaign: None"}</Typography></Stack></TableCell><TableCell>{row.webinarType}</TableCell><TableCell>{new Date(row.scheduledStartAt).toLocaleString()}</TableCell><TableCell><Chip size="small" label={row.status} color={statusColor(row.status)} /></TableCell><TableCell>{row.reminderEnabled ? <Chip size="small" variant="outlined" label="Enabled" /> : <Chip size="small" label="Off" />}</TableCell><TableCell>{row.webinarUrl ? <Button size="small" href={row.webinarUrl} target="_blank" rel="noreferrer">Open</Button> : "-"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void openRegistrations(row)}>Registrations</Button>{canEdit ? <Button size="small" onClick={() => openEditor(row)}>Edit</Button> : null}{canPublish && row.status !== "LIVE" ? <Button size="small" onClick={() => void quickStatus(row, "LIVE")}>Mark Live</Button> : null}{canPublish && row.status !== "COMPLETED" ? <Button size="small" onClick={() => void quickStatus(row, "COMPLETED")}>Complete</Button> : null}{canCancel && row.status !== "CANCELLED" ? <Button size="small" color="error" onClick={() => void quickStatus(row, "CANCELLED")}>Cancel</Button> : null}</Stack></TableCell></TableRow>)}
            </TableBody></Table>
          )}
        </CardContent></Card>
      ) : null}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Webinar" : "Create Webinar"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required inputProps={{ maxLength: 60 }} label="Title *" value={form.title} onChange={(e) => setForm((v) => ({ ...v, title: e.target.value }))} error={Boolean(formErrors.title)} helperText={formErrors.title || "Title must be 60 characters or fewer."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Type *</InputLabel><Select value={form.webinarType} label="Type *" onChange={(e) => setForm((v) => ({ ...v, webinarType: e.target.value as CarePilotWebinarType }))}>{WEBINAR_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={form.campaignId} label="Campaign" onChange={(e) => setForm((v) => ({ ...v, campaignId: e.target.value }))}><MenuItem value="">None</MenuItem>{campaigns.map((campaign) => <MenuItem key={campaign.id} value={campaign.id}>{campaign.name}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} inputProps={{ maxLength: 250 }} label="Description" value={form.description} onChange={(e) => setForm((v) => ({ ...v, description: e.target.value }))} error={Boolean(formErrors.description)} helperText={formErrors.description || "Description must be 250 characters or fewer."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required type="datetime-local" label="Start *" value={form.scheduledStartAt} onChange={(e) => setForm((v) => ({ ...v, scheduledStartAt: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(formErrors.scheduledStartAt)} helperText={formErrors.scheduledStartAt || "Start date/time is required."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required type="datetime-local" label="End *" value={form.scheduledEndAt} onChange={(e) => setForm((v) => ({ ...v, scheduledEndAt: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(formErrors.scheduledEndAt)} helperText={formErrors.scheduledEndAt || "End date/time is required and must be on or after start."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth inputProps={{ maxLength: 250 }} label="Webinar URL" value={form.webinarUrl} onChange={(e) => setForm((v) => ({ ...v, webinarUrl: e.target.value }))} error={Boolean(formErrors.webinarUrl)} helperText={formErrors.webinarUrl || "Optional webinar URL."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth inputProps={{ maxLength: 60 }} label="Organizer" value={form.organizerName} onChange={(e) => setForm((v) => ({ ...v, organizerName: e.target.value }))} error={Boolean(formErrors.organizerName)} helperText={formErrors.organizerName || "Optional organizer name."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Organizer Email" value={form.organizerEmail} onChange={(e) => setForm((v) => ({ ...v, organizerEmail: e.target.value }))} error={Boolean(formErrors.organizerEmail)} helperText={formErrors.organizerEmail || "Enter a valid email address if provided."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth required inputProps={{ maxLength: 60 }} label="Timezone *" value={form.timezone} onChange={(e) => setForm((v) => ({ ...v, timezone: e.target.value }))} error={Boolean(formErrors.timezone)} helperText={formErrors.timezone || "Timezone is required."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Capacity" value={form.capacity} onChange={(e) => setForm((v) => ({ ...v, capacity: e.target.value }))} inputProps={{ min: 0 }} error={Boolean(formErrors.capacity)} helperText={formErrors.capacity || "Capacity must be zero or greater."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth inputProps={{ maxLength: 250 }} label="Tags" value={form.tags} onChange={(e) => setForm((v) => ({ ...v, tags: e.target.value }))} error={Boolean(formErrors.tags)} helperText={formErrors.tags || "Optional tags, comma separated."} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions><Button onClick={() => setEditorOpen(false)}>Close</Button><Button variant="contained" onClick={() => void saveWebinar()} disabled={saving}>{saving ? "Saving..." : "Save"}</Button></DialogActions>
      </Dialog>

      <Dialog open={regOpen} onClose={() => setRegOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Registrations{regWebinar ? ` · ${regWebinar.title}` : ""}</DialogTitle>
        <DialogContent>
          {regError ? <Alert severity="error" sx={{ mb: 2 }}>{regError}</Alert> : null}
          {canManageRegistrations ? (
            <Grid container spacing={1.5} sx={{ mb: 2, mt: 0.5 }}>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" required label="Attendee *" value={regForm.attendeeName} onChange={(e) => setRegForm((v) => ({ ...v, attendeeName: e.target.value }))} error={Boolean(regFormErrors.attendeeName)} helperText={regFormErrors.attendeeName || "Attendee name is required."} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Email" value={regForm.attendeeEmail} onChange={(e) => setRegForm((v) => ({ ...v, attendeeEmail: e.target.value }))} error={Boolean(regFormErrors.attendeeEmail)} helperText={regFormErrors.attendeeEmail || "Enter a valid email address if provided."} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Phone" value={regForm.attendeePhone} onChange={(e) => setRegForm((v) => ({ ...v, attendeePhone: e.target.value }))} inputProps={{ inputMode: "tel" }} error={Boolean(regFormErrors.attendeePhone)} helperText={regFormErrors.attendeePhone || "Enter a valid Indian mobile number if provided."} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Patient Id" value={regForm.patientId} onChange={(e) => setRegForm((v) => ({ ...v, patientId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Lead Id" value={regForm.leadId} onChange={(e) => setRegForm((v) => ({ ...v, leadId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 12 }}><Button variant="contained" onClick={() => void addRegistration()} disabled={regSaving}>{regSaving ? "Registering..." : "Register Attendee"}</Button></Grid>
            </Grid>
          ) : null}
          {regLoading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
          {!regLoading && registrations.length === 0 ? <Alert severity="info">No registrations yet.</Alert> : null}
          {!regLoading && registrations.length > 0 ? (
            <Table size="small"><TableHead><TableRow><TableCell>Attendee</TableCell><TableCell>Email</TableCell><TableCell>Phone</TableCell><TableCell>Status</TableCell><TableCell>Patient</TableCell><TableCell>Lead</TableCell><TableCell>Campaign</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {registrations.map((row) => <TableRow key={row.id}><TableCell>{row.attendeeName}</TableCell><TableCell>{row.attendeeEmail || "-"}</TableCell><TableCell>{row.attendeePhone || "-"}</TableCell><TableCell><Chip size="small" label={row.registrationStatus} /></TableCell><TableCell>{row.patientId ? "Patient record" : "-"}</TableCell><TableCell>{row.leadId ? <Stack direction="row" spacing={1} alignItems="center"><Typography variant="body2">{row.leadName || "Linked lead"}</Typography><Button size="small" onClick={() => openLead(row)}>Open Lead</Button></Stack> : "-"}</TableCell><TableCell>{row.campaignName || "-"}</TableCell><TableCell align="right">{canRecordAttendance ? <Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void markAttendance(row.id, "ATTENDED")} disabled={regSaving}>Attended</Button><Button size="small" onClick={() => void markAttendance(row.id, "NO_SHOW")} disabled={regSaving}>No-show</Button><Button size="small" color="error" onClick={() => void markAttendance(row.id, "CANCELLED")} disabled={regSaving}>Cancel</Button></Stack> : "-"}</TableCell></TableRow>)}
            </TableBody></Table>
          ) : null}
        </DialogContent>
      </Dialog>
    </Stack>
  );
}
