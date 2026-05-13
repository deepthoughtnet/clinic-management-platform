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
import {
  cancelCarePilotReminder,
  getCarePilotReminder,
  listCarePilotCampaigns,
  listCarePilotReminders,
  rescheduleCarePilotReminder,
  resendCarePilotReminder,
  suppressCarePilotReminder,
  retryCarePilotReminder,
  type CarePilotCampaign,
  type CarePilotCampaignType,
  type CarePilotChannelType,
  type CarePilotDeliveryStatus,
  type CarePilotReminderDetail,
  type CarePilotReminderRow,
} from "../../../api/clinicApi";

const TAB_FILTERS = ["Upcoming", "Pending", "Retrying", "Failed", "Delivered", "Read", "Skipped", "All"] as const;
type ReminderTab = typeof TAB_FILTERS[number];

function deliveryColor(status: CarePilotDeliveryStatus | null) {
  if (!status) return "default" as const;
  if (status === "READ" || status === "DELIVERED" || status === "SENT") return "success" as const;
  if (status === "FAILED" || status === "BOUNCED" || status === "UNDELIVERED") return "error" as const;
  if (status === "QUEUED") return "info" as const;
  if (status === "SKIPPED") return "default" as const;
  return "warning" as const;
}

function executionColor(status: string) {
  if (status === "SUCCEEDED") return "success" as const;
  if (status === "FAILED" || status === "DEAD_LETTER") return "error" as const;
  if (status === "RETRY_SCHEDULED") return "warning" as const;
  if (status === "QUEUED" || status === "PROCESSING") return "info" as const;
  if (status === "CANCELLED" || status === "SUPPRESSED") return "default" as const;
  return "default" as const;
}

function mapTabToStatus(tab: ReminderTab): string | undefined {
  if (tab === "Failed") return "FAILED";
  if (tab === "Delivered") return "DELIVERED";
  if (tab === "Read") return "READ";
  if (tab === "Skipped") return "SKIPPED";
  return undefined;
}

function isRowInTab(row: CarePilotReminderRow, tab: ReminderTab): boolean {
  const now = Date.now();
  const scheduled = row.scheduledAt ? new Date(row.scheduledAt).getTime() : 0;
  if (tab === "All") return true;
  if (tab === "Upcoming") return (row.executionStatus === "QUEUED" || row.executionStatus === "PROCESSING") && scheduled > now;
  if (tab === "Pending") return row.executionStatus === "QUEUED" || row.executionStatus === "PROCESSING";
  if (tab === "Retrying") return row.executionStatus === "RETRY_SCHEDULED" || (row.retryCount > 0 && !!row.nextRetryAt);
  if (tab === "Failed") return row.executionStatus === "FAILED" || row.executionStatus === "DEAD_LETTER" || row.deliveryStatus === "FAILED" || row.deliveryStatus === "BOUNCED" || row.deliveryStatus === "UNDELIVERED";
  if (tab === "Delivered") return row.deliveryStatus === "DELIVERED";
  if (tab === "Read") return row.deliveryStatus === "READ";
  if (tab === "Skipped") return row.deliveryStatus === "SKIPPED" || row.executionStatus === "CANCELLED" || row.executionStatus === "SUPPRESSED";
  return true;
}

function kpi(title: string, value: number) {
  return (
    <Card>
      <CardContent>
        <Typography variant="caption" color="text.secondary">{title}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 800 }}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

export default function RemindersPage() {
  const navigate = useNavigate();
  const auth = useAuth();
  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("AUDITOR") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [rows, setRows] = React.useState<CarePilotReminderRow[]>([]);
  const [tab, setTab] = React.useState<ReminderTab>("All");

  const [startDate, setStartDate] = React.useState("");
  const [endDate, setEndDate] = React.useState("");
  const [campaignId, setCampaignId] = React.useState("");
  const [campaignType, setCampaignType] = React.useState<CarePilotCampaignType | "">("");
  const [channel, setChannel] = React.useState<CarePilotChannelType | "">("");
  const [statusFilter, setStatusFilter] = React.useState("");
  const [patientId, setPatientId] = React.useState("");
  const [patientSearchInput, setPatientSearchInput] = React.useState("");
  const [patientNameFilter, setPatientNameFilter] = React.useState("");
  const [rescheduleTarget, setRescheduleTarget] = React.useState<CarePilotReminderRow | null>(null);
  const [rescheduleAt, setRescheduleAt] = React.useState("");
  const [rescheduleReason, setRescheduleReason] = React.useState("");

  const [detail, setDetail] = React.useState<CarePilotReminderDetail | null>(null);
  const [detailOpen, setDetailOpen] = React.useState(false);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, reminderRes] = await Promise.all([
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        listCarePilotReminders(auth.accessToken, auth.tenantId, {
          campaignId: campaignId || undefined,
          campaignType: campaignType || undefined,
          channel: channel || undefined,
          status: statusFilter || mapTabToStatus(tab),
          patientId: patientId || undefined,
          patientName: patientNameFilter || undefined,
          fromDate: startDate || undefined,
          toDate: endDate || undefined,
          size: 200,
        }),
      ]);
      setCampaigns(campaignRows);
      setRows(reminderRes.rows);
    } catch (e) {
      setError((e as Error).message || "Failed to load reminders");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, campaignId, campaignType, channel, statusFilter, patientId, patientNameFilter, startDate, endDate, tab]);

  React.useEffect(() => {
    const handle = window.setTimeout(() => setPatientNameFilter(patientSearchInput.trim()), 300);
    return () => window.clearTimeout(handle);
  }, [patientSearchInput]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const openDetail = async (executionId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setDetailOpen(true);
    setDetail(null);
    try {
      const data = await getCarePilotReminder(auth.accessToken, auth.tenantId, executionId);
      setDetail(data);
    } catch (e) {
      setError((e as Error).message || "Failed to load reminder timeline");
    }
  };

  const onAction = async (row: CarePilotReminderRow, action: "retry" | "resend" | "cancel" | "suppress") => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    const verb = action === "retry" ? "Retry" : action === "resend" ? "Resend" : action === "cancel" ? "Cancel" : "Suppress";
    const confirmed = window.confirm(`${verb} this reminder execution?`);
    if (!confirmed) return;
    try {
      if (action === "retry") {
        await retryCarePilotReminder(auth.accessToken, auth.tenantId, row.executionId);
      } else if (action === "resend") {
        await resendCarePilotReminder(auth.accessToken, auth.tenantId, row.executionId);
      } else if (action === "cancel") {
        await cancelCarePilotReminder(auth.accessToken, auth.tenantId, row.executionId);
      } else {
        await suppressCarePilotReminder(auth.accessToken, auth.tenantId, row.executionId);
      }
      await load();
    } catch (e) {
      setError((e as Error).message || `Failed to ${action} reminder`);
    }
  };

  const canMutateRow = (row: CarePilotReminderRow) =>
    row.executionStatus === "QUEUED" || row.executionStatus === "RETRY_SCHEDULED" || row.executionStatus === "PROCESSING";

  const submitReschedule = async () => {
    if (!auth.accessToken || !auth.tenantId || !rescheduleTarget) return;
    if (!rescheduleAt) {
      setError("Select a future date/time for reschedule.");
      return;
    }
    const iso = new Date(rescheduleAt).toISOString();
    try {
      await rescheduleCarePilotReminder(auth.accessToken, auth.tenantId, rescheduleTarget.executionId, {
        newScheduledAt: iso,
        reason: rescheduleReason || undefined,
      });
      setRescheduleTarget(null);
      setRescheduleAt("");
      setRescheduleReason("");
      await load();
    } catch (e) {
      setError((e as Error).message || "Failed to reschedule reminder");
    }
  };

  const displayed = rows.filter((row) => isRowInTab(row, tab));

  const counts = React.useMemo(() => ({
    scheduledToday: rows.filter((r) => r.scheduledAt?.slice(0, 10) === new Date().toISOString().slice(0, 10)).length,
    pending: rows.filter((r) => isRowInTab(r, "Pending")).length,
    retrying: rows.filter((r) => isRowInTab(r, "Retrying")).length,
    failed: rows.filter((r) => isRowInTab(r, "Failed")).length,
    delivered: rows.filter((r) => isRowInTab(r, "Delivered")).length,
    read: rows.filter((r) => isRowInTab(r, "Read")).length,
    skipped: rows.filter((r) => isRowInTab(r, "Skipped")).length,
  }), [rows]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to view CarePilot reminders.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to CarePilot reminders.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot Reminders</Typography>
          <Typography variant="body2" color="text.secondary">Operational view of scheduled, sent, failed, and retrying patient reminders.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      {!canMutate ? <Alert severity="info">Read-only mode: reminder mutation actions are disabled for your role.</Alert> : null}
      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card><CardContent>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="From" value={startDate} onChange={(e) => setStartDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="To" value={endDate} onChange={(e) => setEndDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={campaignId} label="Campaign" onChange={(e) => setCampaignId(String(e.target.value))}><MenuItem value="">All</MenuItem>{campaigns.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Campaign Type</InputLabel><Select value={campaignType} label="Campaign Type" onChange={(e) => setCampaignType(String(e.target.value) as CarePilotCampaignType | "")}><MenuItem value="">All</MenuItem>{["APPOINTMENT_REMINDER","MISSED_APPOINTMENT_FOLLOW_UP","FOLLOW_UP_REMINDER","REFILL_REMINDER","VACCINATION_REMINDER","BILLING_REMINDER","WELLNESS_MESSAGE","CUSTOM"].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 1.5 }}><FormControl fullWidth><InputLabel>Channel</InputLabel><Select value={channel} label="Channel" onChange={(e) => setChannel(String(e.target.value) as CarePilotChannelType | "")}><MenuItem value="">All</MenuItem><MenuItem value="EMAIL">EMAIL</MenuItem><MenuItem value="SMS">SMS</MenuItem><MenuItem value="WHATSAPP">WHATSAPP</MenuItem></Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 1.5 }}><TextField fullWidth label="Patient ID" value={patientId} onChange={(e) => setPatientId(e.target.value)} /></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth label="Search Patient" value={patientSearchInput} onChange={(e) => setPatientSearchInput(e.target.value)} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={statusFilter} label="Status" onChange={(e) => setStatusFilter(String(e.target.value))}><MenuItem value="">Auto (Tab)</MenuItem>{["QUEUED","PROCESSING","RETRY_SCHEDULED","FAILED","DEAD_LETTER","SUCCEEDED","CANCELLED","SUPPRESSED","DELIVERED","READ","SKIPPED","UNDELIVERED","BOUNCED"].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 1 }}><Button fullWidth variant="contained" onClick={() => void load()}>Apply</Button></Grid>
        </Grid>
      </CardContent></Card>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Scheduled Today", counts.scheduledToday)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Pending", counts.pending)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Retrying", counts.retrying)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Failed", counts.failed)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Delivered", counts.delivered)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Read", counts.read)}</Grid>
      </Grid>

      <Card><CardContent sx={{ pb: 0 }}>
        <Tabs value={tab} onChange={(_, next) => setTab(next)} variant="scrollable" allowScrollButtonsMobile>
          {TAB_FILTERS.map((item) => <Tab key={item} value={item} label={item} />)}
        </Tabs>
      </CardContent></Card>

      {loading ? <Box sx={{ minHeight: 180, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : (
        <Card><CardContent>
          {displayed.length === 0 ? <Alert severity="info">No reminders found for selected filters.</Alert> : (
            <Table size="small">
              <TableHead><TableRow><TableCell>Patient</TableCell><TableCell>Campaign</TableCell><TableCell>Reminder Type</TableCell><TableCell>Channel</TableCell><TableCell>Scheduled</TableCell><TableCell>Status</TableCell><TableCell>Delivery</TableCell><TableCell>Retry</TableCell><TableCell>Provider</TableCell><TableCell>Reason</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
              <TableBody>
                {displayed.map((row) => (
                  <TableRow key={row.executionId} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || row.patientId || "-"}</Typography>
                      <Typography variant="caption" color="text.secondary">{row.patientEmail || row.patientPhone || "No contact"}</Typography>
                    </TableCell>
                    <TableCell>{row.campaignName}</TableCell>
                    <TableCell>{row.campaignType || "-"}</TableCell>
                    <TableCell>{row.channel}</TableCell>
                    <TableCell>{row.scheduledAt ? new Date(row.scheduledAt).toLocaleString() : "-"}</TableCell>
                    <TableCell><Chip size="small" color={executionColor(row.executionStatus)} label={row.executionStatus} /></TableCell>
                    <TableCell><Chip size="small" color={deliveryColor(row.deliveryStatus)} label={row.deliveryStatus || "-"} /></TableCell>
                    <TableCell>{row.retryCount}</TableCell>
                    <TableCell>{row.providerName || "-"}</TableCell>
                    <TableCell>{row.reminderReason || row.failureReason || "-"}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => void openDetail(row.executionId)}>View Timeline</Button>
                        <Button size="small" onClick={() => row.patientId && navigate(`/patients/${row.patientId}`)} disabled={!row.patientId}>Open Patient</Button>
                        <Button size="small" onClick={() => navigate(`/carepilot/campaigns?campaignId=${encodeURIComponent(row.campaignId)}`)}>Open Campaign</Button>
                        {canMutate ? <Button size="small" disabled={!(row.executionStatus === "FAILED" || row.executionStatus === "DEAD_LETTER")} onClick={() => void onAction(row, "retry")}>Retry</Button> : null}
                        {canMutate ? <Button size="small" disabled={!(row.executionStatus === "FAILED" || row.executionStatus === "DEAD_LETTER")} onClick={() => void onAction(row, "resend")}>Resend</Button> : null}
                        {canMutate ? <Button size="small" disabled={!canMutateRow(row)} onClick={() => void onAction(row, "cancel")}>Cancel</Button> : null}
                        {canMutate ? <Button size="small" disabled={!canMutateRow(row)} onClick={() => void onAction(row, "suppress")}>Suppress</Button> : null}
                        {canMutate ? <Button size="small" disabled={!canMutateRow(row)} onClick={() => {
                          setRescheduleTarget(row);
                          setRescheduleAt(row.scheduledAt ? row.scheduledAt.slice(0, 16) : "");
                        }}>Reschedule</Button> : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent></Card>
      )}

      <Dialog open={detailOpen} onClose={() => setDetailOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Reminder Timeline</DialogTitle>
        <DialogContent>
          {!detail ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : (
            <Stack spacing={2}>
              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Reminder Details</Typography>
                <Typography variant="body2">Patient: {detail.reminder.patientName || detail.reminder.patientId || "-"}</Typography>
                <Typography variant="body2">Campaign: {detail.reminder.campaignName}</Typography>
                <Typography variant="body2">Trigger: {detail.reminder.triggerType || "-"}</Typography>
                <Typography variant="body2">Reason: {detail.reminder.reminderReason || "-"}</Typography>
                <Typography variant="body2">Scheduled: {detail.reminder.scheduledAt ? new Date(detail.reminder.scheduledAt).toLocaleString() : "-"}</Typography>
                <Typography variant="body2">Status: {detail.reminder.executionStatus} / {detail.reminder.deliveryStatus || "-"}</Typography>
                <Typography variant="body2">Retry Count: {detail.reminder.retryCount}</Typography>
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Status Timeline</Typography>
                {detail.timeline.statusEvents.length === 0 ? <Alert severity="info">No timeline events.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Detail</TableCell><TableCell>At</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.statusEvents.map((event, idx) => <TableRow key={`${event.type}-${idx}`}><TableCell>{event.type}</TableCell><TableCell>{event.status}</TableCell><TableCell>{event.detail || "-"}</TableCell><TableCell>{event.at ? new Date(event.at).toLocaleString() : "-"}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Attempts</Typography>
                {detail.timeline.deliveryAttempts.length === 0 ? <Alert severity="info">No delivery attempts.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>#</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Error Code</TableCell><TableCell>Error</TableCell><TableCell>Attempted At</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.deliveryAttempts.map((attempt) => <TableRow key={attempt.id}><TableCell>{attempt.attemptNumber}</TableCell><TableCell>{attempt.providerName || "-"}</TableCell><TableCell>{attempt.deliveryStatus}</TableCell><TableCell>{attempt.errorCode || "-"}</TableCell><TableCell>{attempt.errorMessage || "-"}</TableCell><TableCell>{attempt.attemptedAt ? new Date(attempt.attemptedAt).toLocaleString() : "-"}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Events</Typography>
                {detail.timeline.deliveryEvents.length === 0 ? <Alert severity="info">No provider delivery events.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>Provider Status</TableCell><TableCell>Internal Status</TableCell><TableCell>Provider</TableCell><TableCell>Message ID</TableCell><TableCell>Event Time</TableCell><TableCell>Received</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.deliveryEvents.map((event, idx) => <TableRow key={`${event.id || idx}`}><TableCell>{event.externalStatus || "-"}</TableCell><TableCell>{event.internalStatus}</TableCell><TableCell>{event.providerName || "-"}</TableCell><TableCell>{event.providerMessageId || "-"}</TableCell><TableCell>{event.eventTimestamp ? new Date(event.eventTimestamp).toLocaleString() : "-"}</TableCell><TableCell>{event.receivedAt ? new Date(event.receivedAt).toLocaleString() : "-"}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>
            </Stack>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(rescheduleTarget)} onClose={() => setRescheduleTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Reschedule Reminder</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="New Scheduled Time"
              type="datetime-local"
              value={rescheduleAt}
              onChange={(e) => setRescheduleAt(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Reason (optional)"
              value={rescheduleReason}
              onChange={(e) => setRescheduleReason(e.target.value)}
              multiline
              minRows={2}
            />
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={() => setRescheduleTarget(null)}>Close</Button>
              <Button variant="contained" onClick={() => void submitReschedule()}>Save</Button>
            </Stack>
          </Stack>
        </DialogContent>
      </Dialog>
    </Stack>
  );
}
