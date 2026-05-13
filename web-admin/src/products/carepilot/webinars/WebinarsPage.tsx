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
import { useAuth } from "../../../auth/useAuth";
import {
  createCarePilotWebinar,
  getCarePilotWebinarAnalyticsSummary,
  listCarePilotWebinarRegistrations,
  listCarePilotWebinars,
  markCarePilotWebinarAttendance,
  registerCarePilotWebinarAttendee,
  updateCarePilotWebinar,
  updateCarePilotWebinarStatus,
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
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const [tab, setTab] = React.useState<"UPCOMING" | "LIVE" | "COMPLETED" | "CANCELLED">("UPCOMING");
  const [statusFilter, setStatusFilter] = React.useState<CarePilotWebinarStatus | "">("");
  const [typeFilter, setTypeFilter] = React.useState<CarePilotWebinarType | "">("");

  const [rows, setRows] = React.useState<CarePilotWebinar[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotWebinarAnalyticsSummary | null>(null);

  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<CarePilotWebinar | null>(null);
  const [form, setForm] = React.useState({
    title: "",
    description: "",
    webinarType: "HEALTH_AWARENESS" as CarePilotWebinarType,
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

  const [regOpen, setRegOpen] = React.useState(false);
  const [regWebinar, setRegWebinar] = React.useState<CarePilotWebinar | null>(null);
  const [registrations, setRegistrations] = React.useState<CarePilotWebinarRegistration[]>([]);
  const [regForm, setRegForm] = React.useState({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });

  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("RECEPTIONIST") || auth.rolesUpper.includes("AUDITOR") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("RECEPTIONIST") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [webinars, summary] = await Promise.all([
        listCarePilotWebinars(auth.accessToken, auth.tenantId, {
          status: statusFilter || undefined,
          webinarType: typeFilter || undefined,
          upcoming: tab === "UPCOMING" ? true : undefined,
          completed: tab === "COMPLETED" ? true : undefined,
          size: 100,
        }),
        getCarePilotWebinarAnalyticsSummary(auth.accessToken, auth.tenantId),
      ]);
      let filtered = webinars.rows;
      if (tab === "LIVE") filtered = filtered.filter((r) => r.status === "LIVE");
      if (tab === "CANCELLED") filtered = filtered.filter((r) => r.status === "CANCELLED");
      if (tab === "COMPLETED") filtered = filtered.filter((r) => r.status === "COMPLETED");
      setRows(filtered);
      setAnalytics(summary);
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
    if (!auth.accessToken || !auth.tenantId) return;
    setRegWebinar(row);
    setRegOpen(true);
    const list = await listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, row.id, 0, 100);
    setRegistrations(list.rows);
  };

  const addRegistration = async () => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canMutate) return;
    await registerCarePilotWebinarAttendee(auth.accessToken, auth.tenantId, regWebinar.id, {
      attendeeName: regForm.attendeeName,
      attendeeEmail: regForm.attendeeEmail || null,
      attendeePhone: regForm.attendeePhone || null,
      patientId: regForm.patientId || null,
      leadId: regForm.leadId || null,
    });
    const list = await listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100);
    setRegistrations(list.rows);
    setRegForm({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });
  };

  const markAttendance = async (registrationId: string, status: CarePilotWebinarRegistrationStatus) => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canMutate) return;
    await markCarePilotWebinarAttendance(auth.accessToken, auth.tenantId, regWebinar.id, { registrationId, registrationStatus: status });
    const list = await listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100);
    setRegistrations(list.rows);
  };

  const quickStatus = async (row: CarePilotWebinar, status: CarePilotWebinarStatus) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    await updateCarePilotWebinarStatus(auth.accessToken, auth.tenantId, row.id, status);
    await load();
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
          {canMutate ? <Button variant="contained" onClick={() => openEditor()}>New Webinar</Button> : null}
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
              {rows.map((row) => <TableRow key={row.id}><TableCell><Stack spacing={0.25}><Typography variant="body2" sx={{ fontWeight: 700 }}>{row.title}</Typography><Typography variant="caption" color="text.secondary">{row.organizerName || "-"}</Typography></Stack></TableCell><TableCell>{row.webinarType}</TableCell><TableCell>{new Date(row.scheduledStartAt).toLocaleString()}</TableCell><TableCell><Chip size="small" label={row.status} color={statusColor(row.status)} /></TableCell><TableCell>{row.reminderEnabled ? <Chip size="small" variant="outlined" label="Enabled" /> : <Chip size="small" label="Off" />}</TableCell><TableCell>{row.webinarUrl ? <Button size="small" href={row.webinarUrl} target="_blank" rel="noreferrer">Open</Button> : "-"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void openRegistrations(row)}>Registrations</Button>{canMutate ? <Button size="small" onClick={() => openEditor(row)}>Edit</Button> : null}{canMutate && row.status !== "LIVE" ? <Button size="small" onClick={() => void quickStatus(row, "LIVE")}>Mark Live</Button> : null}{canMutate && row.status !== "COMPLETED" ? <Button size="small" onClick={() => void quickStatus(row, "COMPLETED")}>Complete</Button> : null}{canMutate && row.status !== "CANCELLED" ? <Button size="small" color="error" onClick={() => void quickStatus(row, "CANCELLED")}>Cancel</Button> : null}</Stack></TableCell></TableRow>)}
            </TableBody></Table>
          )}
        </CardContent></Card>
      ) : null}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Webinar" : "Create Webinar"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Title" value={form.title} onChange={(e) => setForm((v) => ({ ...v, title: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Type</InputLabel><Select value={form.webinarType} label="Type" onChange={(e) => setForm((v) => ({ ...v, webinarType: e.target.value as CarePilotWebinarType }))}>{WEBINAR_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} label="Description" value={form.description} onChange={(e) => setForm((v) => ({ ...v, description: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="datetime-local" label="Start" value={form.scheduledStartAt} onChange={(e) => setForm((v) => ({ ...v, scheduledStartAt: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="datetime-local" label="End" value={form.scheduledEndAt} onChange={(e) => setForm((v) => ({ ...v, scheduledEndAt: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Webinar URL" value={form.webinarUrl} onChange={(e) => setForm((v) => ({ ...v, webinarUrl: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Organizer" value={form.organizerName} onChange={(e) => setForm((v) => ({ ...v, organizerName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Organizer Email" value={form.organizerEmail} onChange={(e) => setForm((v) => ({ ...v, organizerEmail: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Timezone" value={form.timezone} onChange={(e) => setForm((v) => ({ ...v, timezone: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Capacity" value={form.capacity} onChange={(e) => setForm((v) => ({ ...v, capacity: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Tags" value={form.tags} onChange={(e) => setForm((v) => ({ ...v, tags: e.target.value }))} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions><Button onClick={() => setEditorOpen(false)}>Close</Button><Button variant="contained" onClick={() => void saveWebinar()} disabled={saving}>{saving ? "Saving..." : "Save"}</Button></DialogActions>
      </Dialog>

      <Dialog open={regOpen} onClose={() => setRegOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Registrations{regWebinar ? ` · ${regWebinar.title}` : ""}</DialogTitle>
        <DialogContent>
          {canMutate ? (
            <Grid container spacing={1.5} sx={{ mb: 2, mt: 0.5 }}>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Attendee" value={regForm.attendeeName} onChange={(e) => setRegForm((v) => ({ ...v, attendeeName: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Email" value={regForm.attendeeEmail} onChange={(e) => setRegForm((v) => ({ ...v, attendeeEmail: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Phone" value={regForm.attendeePhone} onChange={(e) => setRegForm((v) => ({ ...v, attendeePhone: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Patient Id" value={regForm.patientId} onChange={(e) => setRegForm((v) => ({ ...v, patientId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Lead Id" value={regForm.leadId} onChange={(e) => setRegForm((v) => ({ ...v, leadId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 12 }}><Button variant="contained" onClick={() => void addRegistration()}>Register Attendee</Button></Grid>
            </Grid>
          ) : null}
          {registrations.length === 0 ? <Alert severity="info">No registrations yet.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Attendee</TableCell><TableCell>Email</TableCell><TableCell>Phone</TableCell><TableCell>Status</TableCell><TableCell>Patient</TableCell><TableCell>Lead</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {registrations.map((row) => <TableRow key={row.id}><TableCell>{row.attendeeName}</TableCell><TableCell>{row.attendeeEmail || "-"}</TableCell><TableCell>{row.attendeePhone || "-"}</TableCell><TableCell><Chip size="small" label={row.registrationStatus} /></TableCell><TableCell>{row.patientId || "-"}</TableCell><TableCell>{row.leadId || "-"}</TableCell><TableCell align="right">{canMutate ? <Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void markAttendance(row.id, "ATTENDED")}>Attended</Button><Button size="small" onClick={() => void markAttendance(row.id, "NO_SHOW")}>No-show</Button><Button size="small" color="error" onClick={() => void markAttendance(row.id, "CANCELLED")}>Cancel</Button></Stack> : "-"}</TableCell></TableRow>)}
            </TableBody></Table>
          )}
        </DialogContent>
      </Dialog>
    </Stack>
  );
}
