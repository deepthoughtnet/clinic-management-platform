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
  IconButton,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Menu,
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
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { engageAiCallRescheduleSchema, mapZodErrors } from "@deepthoughtnet/form-validation-kit";
import { ConfirmationDialog } from "../../../components/clinical/ConfirmationDialog";
import {
  cancelCarePilotReminder,
  getCarePilotReminder,
  listCarePilotReminders,
  rescheduleCarePilotReminder,
  resendCarePilotReminder,
  suppressCarePilotReminder,
  retryCarePilotReminder,
  type CarePilotCampaignType,
  type CarePilotChannelType,
  type CarePilotDeliveryStatus,
  type CarePilotReminderDetail,
  type CarePilotReminderRow,
} from "../../../api/clinicApi";
import CampaignLookupField from "../components/CampaignLookupField";
import { campaignTypeLabel, channelTypeLabel, triggerTypeLabel } from "../campaigns/campaignLabels";
import { formatCarePilotDateTime, humanizeCarePilotCode, providerLabel } from "../shared/carepilotFormatting";
import { useCarePilotTenantTimezone } from "../shared/useCarePilotTenantTimezone";

const TAB_FILTERS = ["Upcoming", "Pending", "Sent", "Retrying", "Failed", "Delivered", "Read", "Skipped", "All"] as const;
type ReminderTab = typeof TAB_FILTERS[number];

function deliveryColor(status: CarePilotDeliveryStatus | null) {
  if (!status) return "default" as const;
  if (status === "READ" || status === "DELIVERED" || status === "SENT") return "success" as const;
  if (status === "FAILED" || status === "BOUNCED" || status === "UNDELIVERED") return "error" as const;
  if (status === "QUEUED") return "info" as const;
  if (status === "SKIPPED") return "default" as const;
  return "warning" as const;
}

function humanizeSourceType(value: string | null | undefined) {
  if (!value) return "-";
  if (value === "CAMPAIGN_MANUAL_TRIGGER") return "Manual Campaign Run";
  return value.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}

function timelineReasonLabel(event: { reasonLabel: string | null; reasonCode: string | null; status: string | null }) {
  if (event.reasonLabel) return event.reasonLabel;
  if (event.reasonCode) return humanizeCarePilotCode(event.reasonCode);
  return humanizeCarePilotCode(event.status);
}

function shouldRenderTimelineEvent(event: { reasonCode: string | null; reasonLabel: string | null }) {
  if (event.reasonCode === "LAST_ATTEMPT") return false;
  return Boolean(event.reasonLabel || event.reasonCode);
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
  if (tab === "Sent") return "SENT";
  return undefined;
}

function isRowInTab(row: CarePilotReminderRow, tab: ReminderTab): boolean {
  const now = Date.now();
  const scheduled = row.scheduledAt ? new Date(row.scheduledAt).getTime() : 0;
  if (tab === "All") return true;
  if (tab === "Upcoming") return (row.executionStatus === "QUEUED" || row.executionStatus === "PROCESSING") && scheduled > now;
  if (tab === "Pending") return row.executionStatus === "QUEUED" || row.executionStatus === "PROCESSING";
  if (tab === "Sent") return row.deliveryStatus === "SENT" || row.executionStatus === "SUCCEEDED";
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
  const canView = auth.hasPermission("engage.reminder.view")
    || auth.hasPermission("engage.reminder.operate")
    || auth.hasPermission("engage.audit.view");
  const canMutate = auth.hasPermission("engage.reminder.operate");
  const canViewCampaigns = auth.hasPermission("engage.campaign.view")
    || auth.hasPermission("engage.campaign.manage")
    || auth.hasPermission("engage.audit.view");
  const { clinicTimeZone } = useCarePilotTenantTimezone(auth.accessToken, auth.tenantId);

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<CarePilotReminderRow[]>([]);
  const [tab, setTab] = React.useState<ReminderTab>("All");

  const [startDate, setStartDate] = React.useState("");
  const [endDate, setEndDate] = React.useState("");
  const [campaignId, setCampaignId] = React.useState("");
  const [campaignType, setCampaignType] = React.useState<CarePilotCampaignType | "">("");
  const [channel, setChannel] = React.useState<CarePilotChannelType | "">("");
  const [statusFilter, setStatusFilter] = React.useState("");
  const [patientQuery, setPatientQuery] = React.useState("");
  const [rescheduleTarget, setRescheduleTarget] = React.useState<CarePilotReminderRow | null>(null);
  const [rescheduleAt, setRescheduleAt] = React.useState("");
  const [rescheduleReason, setRescheduleReason] = React.useState("");
  const [filterErrors, setFilterErrors] = React.useState<Record<string, string>>({});
  const [rescheduleErrors, setRescheduleErrors] = React.useState<Record<string, string>>({});
  const [actionTarget, setActionTarget] = React.useState<{ row: CarePilotReminderRow; action: "retry" | "resend" | "cancel" | "suppress" } | null>(null);
  const [actionMenuAnchor, setActionMenuAnchor] = React.useState<HTMLElement | null>(null);
  const [actionMenuRow, setActionMenuRow] = React.useState<CarePilotReminderRow | null>(null);

  const [detail, setDetail] = React.useState<CarePilotReminderDetail | null>(null);
  const [detailOpen, setDetailOpen] = React.useState(false);
  const viewTimelineButtonRef = React.useRef<HTMLButtonElement | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (startDate && endDate && startDate > endDate) {
        setFilterErrors({ endDate: "End date must be on or after start date." });
        setLoading(false);
        return;
      }
      setFilterErrors({});
      const reminderRes = await listCarePilotReminders(auth.accessToken, auth.tenantId, {
        campaignId: campaignId || undefined,
        campaignType: campaignType || undefined,
        channel: channel || undefined,
        status: statusFilter || mapTabToStatus(tab),
        patientQuery: patientQuery || undefined,
        fromDate: startDate || undefined,
        toDate: endDate || undefined,
        size: 200,
      });
      setRows(reminderRes.rows);
    } catch (e) {
      setError((e as Error).message || "Failed to load reminders");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, campaignId, campaignType, channel, statusFilter, patientQuery, startDate, endDate, tab]);

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

  const closeDetail = React.useCallback(() => {
    setDetailOpen(false);
    window.setTimeout(() => viewTimelineButtonRef.current?.focus(), 0);
  }, []);

  const onAction = async (row: CarePilotReminderRow, action: "retry" | "resend" | "cancel" | "suppress") => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    setActionTarget({ row, action });
  };

  const openActionMenu = (event: React.MouseEvent<HTMLElement>, row: CarePilotReminderRow) => {
    setActionMenuAnchor(event.currentTarget);
    setActionMenuRow(row);
  };

  const closeActionMenu = () => {
    setActionMenuAnchor(null);
    setActionMenuRow(null);
  };

  const submitAction = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate || !actionTarget) return;
    const { row, action } = actionTarget;
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
    } finally {
      setActionTarget(null);
    }
  };

  const canMutateRow = (row: CarePilotReminderRow) =>
    row.executionStatus === "QUEUED" || row.executionStatus === "RETRY_SCHEDULED" || row.executionStatus === "PROCESSING";

  const submitReschedule = async () => {
    if (!auth.accessToken || !auth.tenantId || !rescheduleTarget) return;
    if (!rescheduleAt) {
      setRescheduleErrors({ scheduledAt: "Select a future date/time for reschedule." });
      return;
    }
    const parsed = engageAiCallRescheduleSchema.safeParse({ scheduledAt: rescheduleAt, reason: rescheduleReason });
    if (!parsed.success) {
      setRescheduleErrors(mapZodErrors(parsed.error));
      return;
    }
    setRescheduleErrors({});
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
    sent: rows.filter((r) => isRowInTab(r, "Sent")).length,
    retrying: rows.filter((r) => isRowInTab(r, "Retrying")).length,
    failed: rows.filter((r) => isRowInTab(r, "Failed")).length,
    delivered: rows.filter((r) => isRowInTab(r, "Delivered")).length,
    read: rows.filter((r) => isRowInTab(r, "Read")).length,
    skipped: rows.filter((r) => isRowInTab(r, "Skipped")).length,
  }), [rows]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to view Jeevanam Engage reminders.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to Jeevanam Engage reminders.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Jeevanam Engage Reminders</Typography>
          <Typography variant="body2" color="text.secondary">Operational view of scheduled, sent, failed, and retrying patient reminders.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      {!canMutate ? <Alert severity="info">Read-only mode: reminder mutation actions are disabled for your role.</Alert> : null}
      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card><CardContent>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="From" value={startDate} onChange={(e) => setStartDate(e.target.value)} InputLabelProps={{ shrink: true }} error={Boolean(filterErrors.startDate)} helperText={filterErrors.startDate || ""} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="To" value={endDate} onChange={(e) => setEndDate(e.target.value)} InputLabelProps={{ shrink: true }} error={Boolean(filterErrors.endDate)} helperText={filterErrors.endDate || ""} /></Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <CampaignLookupField
              token={auth.accessToken}
              tenantId={auth.tenantId}
              value={campaignId}
              onChange={setCampaignId}
              label="Campaign"
              helperText="Search by campaign name, type, or campaign reference."
            />
          </Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Campaign Type</InputLabel><Select value={campaignType} label="Campaign Type" onChange={(e) => setCampaignType(String(e.target.value) as CarePilotCampaignType | "")}><MenuItem value="">All</MenuItem>{(["APPOINTMENT_REMINDER","MISSED_APPOINTMENT_FOLLOW_UP","FOLLOW_UP_REMINDER","REFILL_REMINDER","VACCINATION_REMINDER","BILLING_REMINDER","WELLNESS_MESSAGE","CUSTOM"] as CarePilotCampaignType[]).map((s) => <MenuItem key={s} value={s}>{campaignTypeLabel(s)}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 1.5 }}><FormControl fullWidth><InputLabel>Channel</InputLabel><Select value={channel} label="Channel" onChange={(e) => setChannel(String(e.target.value) as CarePilotChannelType | "")}><MenuItem value="">All</MenuItem><MenuItem value="EMAIL">Email</MenuItem><MenuItem value="SMS">SMS</MenuItem><MenuItem value="WHATSAPP">WhatsApp</MenuItem></Select></FormControl></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth label="Patient" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} helperText="Search by patient name or patient reference." /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={statusFilter} label="Status" onChange={(e) => setStatusFilter(String(e.target.value))}><MenuItem value="">Auto (Tab)</MenuItem>{(["QUEUED","PROCESSING","RETRY_SCHEDULED","FAILED","DEAD_LETTER","SUCCEEDED","CANCELLED","SUPPRESSED","DELIVERED","READ","SKIPPED","UNDELIVERED","BOUNCED"] as string[]).map((s) => <MenuItem key={s} value={s}>{humanizeCarePilotCode(s)}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 1 }}><Button fullWidth variant="contained" onClick={() => void load()}>Apply</Button></Grid>
        </Grid>
      </CardContent></Card>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Scheduled Today", counts.scheduledToday)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Sent", counts.sent)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Delivered", counts.delivered)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Read", counts.read)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Failed", counts.failed)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Pending", counts.pending)}</Grid>
        <Grid size={{ xs: 6, md: 2 }}>{kpi("Retrying", counts.retrying)}</Grid>
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
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "Unknown patient"}</Typography>
                      <Typography variant="caption" color="text.secondary">{row.patientReference || row.patientEmail || row.patientPhone || "No contact"}</Typography>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.campaignName}</Typography>
                        <Typography variant="caption" color="text.secondary">{row.campaignReference || "Unknown reference"}</Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{row.campaignType ? campaignTypeLabel(row.campaignType) : "-"}</TableCell>
                    <TableCell>{channelTypeLabel(row.channel)}</TableCell>
                    <TableCell><Typography variant="body2" sx={{ whiteSpace: "nowrap" }}>{formatCarePilotDateTime(row.scheduledAt, clinicTimeZone)}</Typography></TableCell>
                    <TableCell><Chip size="small" color={executionColor(row.executionStatus)} label={humanizeCarePilotCode(row.executionStatus)} /></TableCell>
                    <TableCell><Chip size="small" color={deliveryColor(row.deliveryStatus)} label={humanizeCarePilotCode(row.deliveryStatus)} /></TableCell>
                    <TableCell>{row.retryCount}</TableCell>
                    <TableCell>{providerLabel(row.providerName)}</TableCell>
                    <TableCell>{row.reasonLabel || row.failureReason || "-"}</TableCell>
                    <TableCell align="right" sx={{ minWidth: 320 }}>
                      <Stack direction="row" spacing={1} justifyContent="flex-end" alignItems="center" flexWrap="wrap">
                        <Button
                          size="small"
                          onClick={(event) => {
                            viewTimelineButtonRef.current = event.currentTarget;
                            void openDetail(row.executionId);
                          }}
                        >
                          View Timeline
                        </Button>
                        <Button size="small" onClick={() => row.patientId && navigate(`/patients/${row.patientId}`)} disabled={!row.patientId}>Open Patient</Button>
                        {canViewCampaigns ? <Button size="small" onClick={() => navigate(`/carepilot/campaigns?campaignId=${encodeURIComponent(row.campaignId)}`)}>Open Campaign</Button> : null}
                        {canMutate && (row.executionStatus === "FAILED" || row.executionStatus === "DEAD_LETTER" || canMutateRow(row)) ? (
                          <>
                            <IconButton size="small" aria-label="More reminder actions" onClick={(event) => openActionMenu(event, row)}>
                              <MoreVertIcon fontSize="small" />
                            </IconButton>
                            <Menu anchorEl={actionMenuAnchor} open={actionMenuRow?.executionId === row.executionId && Boolean(actionMenuAnchor)} onClose={closeActionMenu}>
                              {(row.executionStatus === "FAILED" || row.executionStatus === "DEAD_LETTER") ? [
                                <MenuItem key="retry" onClick={() => { closeActionMenu(); void onAction(row, "retry"); }}>Retry</MenuItem>,
                                <MenuItem key="resend" onClick={() => { closeActionMenu(); void onAction(row, "resend"); }}>Resend</MenuItem>,
                              ] : null}
                              {canMutateRow(row) ? [
                                <MenuItem key="cancel" onClick={() => { closeActionMenu(); void onAction(row, "cancel"); }}>Cancel</MenuItem>,
                                <MenuItem key="suppress" onClick={() => { closeActionMenu(); void onAction(row, "suppress"); }}>Suppress</MenuItem>,
                                <MenuItem key="reschedule" onClick={() => {
                                  closeActionMenu();
                                  setRescheduleTarget(row);
                                  setRescheduleAt(row.scheduledAt ? row.scheduledAt.slice(0, 16) : "");
                                }}>Reschedule</MenuItem>,
                              ] : null}
                            </Menu>
                          </>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent></Card>
      )}

      <Dialog open={detailOpen} onClose={() => closeDetail()} fullWidth maxWidth="lg" aria-labelledby="reminder-timeline-title" aria-describedby="reminder-timeline-description">
        <DialogTitle id="reminder-timeline-title" sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
          <span>Reminder Timeline</span>
          <IconButton aria-label="Close reminder timeline" onClick={closeDetail} size="small">
            <CloseRoundedIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent id="reminder-timeline-description">
          {!detail ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : (
            <Stack spacing={2}>
              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Reminder Details</Typography>
                <Typography variant="body2">Patient: {detail.reminder.patientName || "Unknown patient"} {detail.reminder.patientReference ? `· ${detail.reminder.patientReference}` : ""}</Typography>
                <Typography variant="body2">Campaign: {detail.reminder.campaignName}</Typography>
                <Typography variant="body2" color="text.secondary">{detail.reminder.campaignReference || "Unknown reference"}</Typography>
                <Typography variant="body2">Reminder Type: {detail.reminder.campaignType ? campaignTypeLabel(detail.reminder.campaignType) : "-"}</Typography>
                <Typography variant="body2">Trigger: {detail.reminder.triggerType ? triggerTypeLabel(detail.reminder.triggerType) : "-"}</Typography>
                <Typography variant="body2">Execution Source: {humanizeSourceType(detail.timeline.execution.sourceType)}</Typography>
                <Typography variant="body2">Reason: {detail.reminder.reasonLabel || "-"}</Typography>
                <Typography variant="body2">Scheduled: {formatCarePilotDateTime(detail.reminder.scheduledAt, clinicTimeZone)}</Typography>
                <Typography variant="body2">Status: {humanizeCarePilotCode(detail.reminder.executionStatus)} / {humanizeCarePilotCode(detail.reminder.deliveryStatus)}</Typography>
                <Typography variant="body2">Retry Count: {detail.reminder.retryCount}</Typography>
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Status Timeline</Typography>
                {detail.timeline.statusEvents.length === 0 ? <Alert severity="info">No timeline events.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>Event</TableCell><TableCell>Status</TableCell><TableCell>Detail</TableCell><TableCell>At</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.statusEvents.filter(shouldRenderTimelineEvent).map((event, idx) => <TableRow key={`${event.reasonCode}-${idx}`}><TableCell>{timelineReasonLabel(event)}</TableCell><TableCell>{humanizeCarePilotCode(event.status)}</TableCell><TableCell>{event.detail ? event.detail : ""}</TableCell><TableCell>{formatCarePilotDateTime(event.at, clinicTimeZone)}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Attempts</Typography>
                {detail.timeline.deliveryAttempts.length === 0 ? <Alert severity="info">No delivery attempts.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>#</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Error Code</TableCell><TableCell>Error</TableCell><TableCell>Attempted At</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.deliveryAttempts.map((attempt) => <TableRow key={attempt.id}><TableCell>{attempt.attemptNumber}</TableCell><TableCell>{providerLabel(attempt.providerName)}</TableCell><TableCell>{humanizeCarePilotCode(attempt.deliveryStatus)}</TableCell><TableCell>{attempt.errorCode || ""}</TableCell><TableCell>{attempt.errorMessage || ""}</TableCell><TableCell>{formatCarePilotDateTime(attempt.attemptedAt, clinicTimeZone)}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>

              <Card variant="outlined"><CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Events</Typography>
                {detail.timeline.deliveryEvents.length === 0 ? <Alert severity="info">No provider delivery events.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>Provider Status</TableCell><TableCell>Internal Status</TableCell><TableCell>Provider</TableCell><TableCell>Message ID</TableCell><TableCell>Event Time</TableCell><TableCell>Received</TableCell></TableRow></TableHead><TableBody>
                    {detail.timeline.deliveryEvents.map((event, idx) => <TableRow key={`${event.id || idx}`}><TableCell>{humanizeCarePilotCode(event.externalStatus)}</TableCell><TableCell>{humanizeCarePilotCode(event.internalStatus)}</TableCell><TableCell>{providerLabel(event.providerName)}</TableCell><TableCell>{event.providerMessageId ? "Recorded" : ""}</TableCell><TableCell>{formatCarePilotDateTime(event.eventTimestamp, clinicTimeZone)}</TableCell><TableCell>{formatCarePilotDateTime(event.receivedAt, clinicTimeZone)}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button type="button" onClick={closeDetail}>Close</Button>
        </DialogActions>
      </Dialog>

      <ConfirmationDialog
        open={Boolean(actionTarget)}
        title={actionTarget ? `${actionTarget.action.charAt(0).toUpperCase()}${actionTarget.action.slice(1)} reminder execution` : "Reminder action"}
        description="This will update the reminder execution state. Continue?"
        confirmLabel={actionTarget?.action === "retry" ? "Retry" : actionTarget?.action === "resend" ? "Resend" : actionTarget?.action === "cancel" ? "Cancel" : "Suppress"}
        confirmColor="warning"
        onCancel={() => setActionTarget(null)}
        onConfirm={() => void submitAction()}
      />

      <Dialog open={Boolean(rescheduleTarget)} onClose={() => setRescheduleTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Reschedule Reminder</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="New Scheduled Time *"
              type="datetime-local"
              value={rescheduleAt}
              onChange={(e) => setRescheduleAt(e.target.value)}
              InputLabelProps={{ shrink: true }}
              error={Boolean(rescheduleErrors.scheduledAt)}
              helperText={rescheduleErrors.scheduledAt || "Select a future date/time."}
            />
            <TextField
              label="Reason (optional)"
              value={rescheduleReason}
              onChange={(e) => setRescheduleReason(e.target.value)}
              multiline
              minRows={2}
              inputProps={{ maxLength: 250 }}
              error={Boolean(rescheduleErrors.reason)}
              helperText={rescheduleErrors.reason || "Optional reason, max 250 characters."}
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
