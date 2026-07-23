import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Collapse,
  FormControl,
  Grid,
  InputLabel,
  IconButton,
  MenuItem,
  Select,
  Stack,
  TableContainer,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { firstZodError, notificationsFilterSchema } from "@deepthoughtnet/form-validation-kit";
import {
  getGroupedNotifications,
  markNotificationRead,
  markNotificationUnread,
  retryNotification,
  searchPatients,
  type NotificationChannel,
  type NotificationEventType,
  type NotificationHistory,
  type NotificationHistoryGroup,
  type Patient,
} from "../../api/clinicApi";
import { KeyboardArrowDown, KeyboardArrowUp } from "@mui/icons-material";

const STATUS_OPTIONS: Array<NotificationHistoryGroup["overallStatus"] | ""> = ["", "DELIVERED", "PARTIAL", "PENDING", "FAILED", "NOT_DELIVERED"];
const CHANNEL_OPTIONS: Array<NotificationChannel | ""> = ["", "IN_APP", "EMAIL", "SMS", "WHATSAPP"];
const EVENT_OPTIONS: Array<NotificationEventType | ""> = [
  "",
  "APPOINTMENT_BOOKED",
  "APPOINTMENT_RESCHEDULED",
  "APPOINTMENT_CANCELLED",
  "APPOINTMENT_NO_SHOW",
  "PRESCRIPTION_READY",
  "PRESCRIPTION_SENT",
  "BILL_GENERATED",
  "BILL_PAID",
  "PAYMENT_RECEIVED",
  "RECEIPT_SENT",
  "REFUND_PROCESSED",
  "LAB_ORDER_CREATED",
  "LAB_SAMPLE_COLLECTED",
  "LAB_REPORT_READY",
  "LAB_REPORT_PUBLISHED",
  "LAB_REPORT_REVIEWED",
  "LAB_CRITICAL_RESULT",
  "FOLLOW_UP_DUE",
  "FOLLOW_UP_REMINDER",
  "VACCINATION_REMINDER",
  "VACCINATION_DUE",
  "APPOINTMENT_REMINDER",
  "PAYMENT_REMINDER",
  "MISSED_APPOINTMENT_REMINDER",
];

const REMINDER_EVENT_TYPES = new Set<NotificationEventType>([
  "APPOINTMENT_REMINDER",
  "FOLLOW_UP_REMINDER",
  "FOLLOW_UP_DUE",
  "VACCINATION_REMINDER",
  "VACCINATION_DUE",
  "PAYMENT_REMINDER",
  "MISSED_APPOINTMENT_REMINDER",
]);

const EVENT_LABELS: Partial<Record<NotificationEventType, string>> = {
  APPOINTMENT_BOOKED: "Appointment Booked",
  APPOINTMENT_RESCHEDULED: "Appointment Rescheduled",
  APPOINTMENT_CANCELLED: "Appointment Cancelled",
  APPOINTMENT_NO_SHOW: "Appointment No-show",
  PRESCRIPTION_READY: "Prescription Ready",
  PRESCRIPTION_SENT: "Prescription Sent",
  BILL_GENERATED: "Bill Generated",
  BILL_PAID: "Bill Paid",
  PAYMENT_RECEIVED: "Payment Received",
  RECEIPT_SENT: "Receipt Sent",
  REFUND_PROCESSED: "Refund Processed",
  LAB_ORDER_CREATED: "Lab Order Created",
  LAB_SAMPLE_COLLECTED: "Lab Sample Collected",
  LAB_REPORT_READY: "Lab Report Ready",
  LAB_REPORT_PUBLISHED: "Lab Report Published",
  LAB_REPORT_REVIEWED: "Lab Report Reviewed",
  LAB_CRITICAL_RESULT: "Lab Critical Result",
  FOLLOW_UP_DUE: "Follow-up Due",
  FOLLOW_UP_REMINDER: "Follow-up Reminder",
  VACCINATION_REMINDER: "Vaccination Reminder",
  VACCINATION_DUE: "Vaccination Due",
  APPOINTMENT_REMINDER: "Appointment Reminder",
  PAYMENT_REMINDER: "Payment Reminder",
  MISSED_APPOINTMENT_REMINDER: "Missed Appointment Reminder",
};

const CHANNEL_ORDER: NotificationChannel[] = ["IN_APP", "EMAIL", "SMS", "WHATSAPP"];
const DISABLED_REASON_PATTERNS = [
  /clinic\.carepilot\.messaging\.(sms|whatsapp)\.enabled\s*=\s*false/i,
  /(?:provider|channel)\s+disabled/i,
  /notifications?\s+disabled/i,
];

function overallStatusLabel(status: NotificationHistoryGroup["overallStatus"]) {
  switch (status) {
    case "DELIVERED":
      return "Delivered";
    case "PARTIAL":
      return "Partial";
    case "PENDING":
      return "Pending";
    case "FAILED":
      return "Failed";
    case "NOT_DELIVERED":
      return "Not delivered";
  }
  return status;
}

function overallStatusColor(status: NotificationHistoryGroup["overallStatus"]) {
  switch (status) {
    case "DELIVERED":
      return "success";
    case "PARTIAL":
      return "warning";
    case "PENDING":
      return "info";
    case "FAILED":
      return "error";
    case "NOT_DELIVERED":
      return "default";
  }
  return "default";
}

function channelLabel(channel: NotificationChannel) {
  switch (channel) {
    case "IN_APP":
      return "In-App";
    case "EMAIL":
      return "Email";
    case "SMS":
      return "SMS";
    case "WHATSAPP":
      return "WhatsApp";
    default:
      return channel;
  }
}

function normalizeNotificationChannel(channel: string | null | undefined): NotificationChannel | null {
  if (!channel) {
    return null;
  }
  switch (channel.trim().toLowerCase()) {
    case "in_app":
    case "in-app":
      return "IN_APP";
    case "email":
      return "EMAIL";
    case "sms":
      return "SMS";
    case "whatsapp":
      return "WHATSAPP";
    default:
      return null;
  }
}

function normalizeNotificationReason(channel: NotificationChannel, reason: string | null): string | null {
  if (!reason) {
    return null;
  }
  const compact = reason.replace(/\s+/g, " ").trim();
  const normalized = compact.toLowerCase();
  if (normalized.includes("patient record unavailable") || normalized.includes("no active recipient available")) {
    return "Patient record unavailable";
  }
  if (normalized.includes("patient email unavailable")) {
    return "Patient email unavailable";
  }
  if (normalized.includes("invalid patient email")) {
    return "Invalid patient email";
  }
  if (normalized.includes("patient mobile unavailable")) {
    return "Patient mobile unavailable";
  }
  if (normalized.includes("invalid patient mobile")) {
    return "Invalid patient mobile";
  }
  if (normalized.includes("patient opted out")) {
    return "Patient opted out";
  }
  const keyMatch = compact.match(/clinic\.carepilot\.messaging\.(sms|whatsapp)\.enabled\s*=\s*false/i);
  if (keyMatch) {
    return `${channelLabel(channel)} notifications disabled`;
  }
  if (normalized.includes("provider disabled") && normalized.includes("enabled=false")) {
    return `${channelLabel(channel)} notifications disabled`;
  }
  if (normalized.includes("provider is not configured") || normalized.includes("provider not configured") || normalized.includes("provider unavailable")) {
    return `${channelLabel(channel)} provider not configured`;
  }
  if (DISABLED_REASON_PATTERNS.some((pattern) => pattern.test(compact))) {
    return `${channelLabel(channel)} notifications disabled`;
  }
  if (normalized.startsWith("skipped:")) {
    return normalizeNotificationReason(channel, compact.slice(compact.indexOf(":") + 1).trim());
  }
  if (normalized.startsWith("provider disabled:")) {
    const remainder = compact.slice(compact.indexOf(":") + 1).trim();
    return normalizeNotificationReason(channel, remainder) ?? `${channelLabel(channel)} notifications disabled`;
  }
  return compact;
}

function channelPresentation(delivery?: NotificationHistory | null) {
  if (!delivery) {
    return { statusLabel: "Not enabled", tone: "neutral" as const, title: "Not enabled", reason: null as string | null };
  }
  const normalizedReason = normalizeNotificationReason(delivery.channel, delivery.failureReason);
  const status = delivery.status.toUpperCase();
  if (status === "SENT" || status === "DELIVERED") {
    return { statusLabel: "Sent", tone: "success" as const, title: normalizedReason ? `Sent — ${normalizedReason}` : "Sent", reason: normalizedReason };
  }
  if (status === "PENDING" || status === "QUEUED" || status === "RETRYING") {
    return { statusLabel: "Pending", tone: "warning" as const, title: normalizedReason ? `Pending — ${normalizedReason}` : "Pending", reason: normalizedReason };
  }
  if (status === "FAILED") {
    return { statusLabel: "Failed", tone: "error" as const, title: normalizedReason ? `Failed — ${normalizedReason}` : "Failed", reason: normalizedReason };
  }
  if (status === "SKIPPED" && normalizedReason?.toLowerCase().includes("notifications disabled")) {
    return { statusLabel: "Disabled", tone: "neutral" as const, title: `Disabled — ${normalizedReason}`, reason: normalizedReason };
  }
  if (status === "SKIPPED") {
    return { statusLabel: "Skipped", tone: "neutral" as const, title: normalizedReason ? `Skipped — ${normalizedReason}` : "Skipped", reason: normalizedReason };
  }
  return { statusLabel: status.charAt(0) + status.slice(1).toLowerCase(), tone: "neutral" as const, title: normalizedReason ? `${status.charAt(0) + status.slice(1).toLowerCase()} — ${normalizedReason}` : status, reason: normalizedReason };
}

function maskRecipient(recipient: string | null) {
  if (!recipient) {
    return "Not available";
  }
  if (recipient.includes("@")) {
    const [local, domain] = recipient.split("@");
    if (!local || !domain) {
      return recipient;
    }
    return `${local.slice(0, 1)}***@${domain}`;
  }
  const digits = recipient.replace(/\D/g, "");
  if (digits.length >= 8) {
    return `${digits.slice(0, 2)}***${digits.slice(-2)}`;
  }
  return recipient;
}

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString() : "-";
}

export default function NotificationsPage() {
  const auth = useAuth();
  const canView = Boolean(auth.tenantId) && auth.hasPermission("notification.read");
  const canRetry = auth.hasPermission("notification.retry") || auth.hasPermission("settings.manage");
  const [rows, setRows] = React.useState<NotificationHistoryGroup[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [expandedRows, setExpandedRows] = React.useState<Record<string, boolean>>({});
  const [filters, setFilters] = React.useState({
    status: "" as NotificationHistoryGroup["overallStatus"] | "",
    eventType: "" as NotificationEventType | "",
    channel: "" as NotificationChannel | "",
    patientId: "",
    from: "",
    to: "",
  });
  const [filterFieldErrors, setFilterFieldErrors] = React.useState<Record<string, string>>({});
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [workingId, setWorkingId] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function loadPatients() {
      if (!auth.accessToken || !auth.tenantId || !canView) {
        return;
      }
      try {
        const value = await searchPatients(auth.accessToken, auth.tenantId, { active: null });
        if (!cancelled) {
          setPatients(value);
        }
      } catch {
        if (!cancelled) {
          setPatients([]);
        }
      }
    }
    void loadPatients();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, canView]);

  const loadRows = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      return;
    }
    const parsed = notificationsFilterSchema.safeParse(filters);
    if (!parsed.success) {
      setFilterFieldErrors({
        from: parsed.error.issues.find((issue) => issue.path.join(".") === "from")?.message || "",
        to: parsed.error.issues.find((issue) => issue.path.join(".") === "to")?.message || "",
        search: parsed.error.issues.find((issue) => issue.path.join(".") === "search")?.message || "",
        status: parsed.error.issues.find((issue) => issue.path.join(".") === "status")?.message || "",
        eventType: parsed.error.issues.find((issue) => issue.path.join(".") === "eventType")?.message || "",
        channel: parsed.error.issues.find((issue) => issue.path.join(".") === "channel")?.message || "",
      });
      setError(firstZodError(parsed.error));
      return;
    }
    setFilterFieldErrors({});
    setLoading(true);
    setError(null);
    try {
      const value = await getGroupedNotifications(auth.accessToken, auth.tenantId, {
        status: filters.status || null,
        eventType: filters.eventType || null,
        channel: filters.channel || null,
        patientId: filters.patientId || null,
        from: filters.from || null,
        to: filters.to || null,
      });
      setRows(value);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load notifications");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, filters, canView]);

  React.useEffect(() => {
    void loadRows();
  }, [loadRows]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }
  if (!canView) {
    return <Alert severity="error">You do not have access to operational notifications.</Alert>;
  }

  const retry = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setWorkingId(id);
    try {
      await retryNotification(auth.accessToken, auth.tenantId, id);
      await loadRows();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to retry notification");
    } finally {
      setWorkingId(null);
    }
  };

  const markRead = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setWorkingId(id);
    try {
      await markNotificationRead(auth.accessToken, auth.tenantId, id);
      await loadRows();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark notification read");
    } finally {
      setWorkingId(null);
    }
  };

  const markUnread = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setWorkingId(id);
    try {
      await markNotificationUnread(auth.accessToken, auth.tenantId, id);
      await loadRows();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark notification unread");
    } finally {
      setWorkingId(null);
    }
  };

  const reminderRows = rows.filter((row) => REMINDER_EVENT_TYPES.has(row.eventType));
  const deliveredCount = rows.filter((row) => row.overallStatus === "DELIVERED").length;
  const pendingCount = rows.filter((row) => row.overallStatus === "PENDING").length;
  const issueCount = rows.filter((row) => row.overallStatus === "FAILED" || row.overallStatus === "PARTIAL" || row.overallStatus === "NOT_DELIVERED").length;

  const eventLabel = (row: NotificationHistoryGroup) => EVENT_LABELS[row.eventType] ?? row.eventType.replaceAll("_", " ");
  const categoryLabel = (row: NotificationHistoryGroup) => (REMINDER_EVENT_TYPES.has(row.eventType) ? "Reminder" : "Notification");

  const channelSummary = (row: NotificationHistoryGroup) => CHANNEL_ORDER.map((channel) => {
    const delivery = row.deliveries.find((item) => normalizeNotificationChannel(item.channel) === channel) ?? null;
    const label = channelLabel(channel);
    const presentation = channelPresentation(delivery);
    return { channel, label, delivery, ...presentation };
  }) as Array<{
    channel: NotificationChannel;
    label: string;
    delivery: NotificationHistory | null;
    tone: "success" | "warning" | "error" | "neutral";
    title: string;
    reason: string | null;
    statusLabel: string;
  }>;

  const patientName = (patientId: string | null) => {
    if (!patientId) {
      return "Patient record unavailable";
    }
    const directPatient = patients.find((patient) => patient.id === patientId);
    return directPatient ? `${directPatient.firstName} ${directPatient.lastName || ""}`.trim() : "Patient record unavailable";
  };

  const toggleExpanded = (id: string) => {
    setExpandedRows((current) => ({ ...current, [id]: !current[id] }));
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Notifications & Reminders
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Tenant-scoped operational reminder inbox and notification history for appointments, billing, prescriptions, and follow-ups.
          </Typography>
        </Box>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={0.75}>
                <Typography variant="overline" color="text.secondary">Delivered</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900 }}>{deliveredCount}</Typography>
                <Typography variant="body2" color="text.secondary">Logical notifications delivered in-app, with optional channels skipped or sent independently.</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={0.75}>
                <Typography variant="overline" color="text.secondary">Pending</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900 }}>{pendingCount}</Typography>
                <Typography variant="body2" color="text.secondary">Notifications waiting for dispatch or retry.</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={0.75}>
                <Typography variant="overline" color="text.secondary">Issues</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900 }}>{issueCount}</Typography>
                <Typography variant="body2" color="text.secondary">Failed, partial, or not delivered notifications need review.</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Filters
            </Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="notification-status-label">Overall status</InputLabel>
                  <Select
                    labelId="notification-status-label"
                    label="Overall status"
                    value={filters.status}
                    onChange={(e) => setFilters((current) => ({ ...current, status: String(e.target.value) as NotificationHistoryGroup["overallStatus"] | "" }))}
                  >
                    {STATUS_OPTIONS.map((value) => (
                      <MenuItem key={value || "all"} value={value}>
                        {value ? overallStatusLabel(value) : "All"}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="notification-event-label">Event</InputLabel>
                  <Select
                    labelId="notification-event-label"
                    label="Event"
                    value={filters.eventType}
                    onChange={(e) => setFilters((current) => ({ ...current, eventType: String(e.target.value) as NotificationEventType | "" }))}
                  >
                    {EVENT_OPTIONS.map((value) => (
                      <MenuItem key={value || "all"} value={value}>
                        {value || "All"}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="notification-channel-label">Channel</InputLabel>
                  <Select
                    labelId="notification-channel-label"
                    label="Channel"
                    value={filters.channel}
                    onChange={(e) => setFilters((current) => ({ ...current, channel: String(e.target.value) as NotificationChannel | "" }))}
                  >
                    {CHANNEL_OPTIONS.map((value) => (
                      <MenuItem key={value || "all"} value={value}>
                        {value || "All"}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="notification-patient-label">Patient</InputLabel>
                  <Select
                    labelId="notification-patient-label"
                    label="Patient"
                    value={filters.patientId}
                    onChange={(e) => setFilters((current) => ({ ...current, patientId: String(e.target.value) }))}
                  >
                    <MenuItem value="">All</MenuItem>
                    {patients.map((patient) => (
                      <MenuItem key={patient.id} value={patient.id}>
                        {patient.firstName} {patient.lastName} • {patient.patientNumber}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth id="notifications-from" label="From" type="date" value={filters.from} onChange={(e) => setFilters((current) => ({ ...current, from: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(filterFieldErrors.from)} helperText={filterFieldErrors.from || "Optional."} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth id="notifications-to" label="To" type="date" value={filters.to} onChange={(e) => setFilters((current) => ({ ...current, to: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(filterFieldErrors.to)} helperText={filterFieldErrors.to || "Optional."} />
              </Grid>
            </Grid>
            <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end" }}>
              <Button onClick={() => setFilters({ status: "", eventType: "", channel: "", patientId: "", from: "", to: "" })}>Clear</Button>
              <Button variant="contained" onClick={() => void loadRows()}>
                Refresh
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
              <CircularProgress />
            </Box>
          ) : rows.length === 0 ? (
            <Alert severity="info">No notifications matched the selected filters. Try widening the date range or clearing one of the filters.</Alert>
          ) : (
            <TableContainer sx={{ maxHeight: "calc(100vh - 320px)" }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Category</TableCell>
                    <TableCell>Event</TableCell>
                    <TableCell>Patient</TableCell>
                    <TableCell>Channels</TableCell>
                    <TableCell>Overall</TableCell>
                    <TableCell>Read</TableCell>
                    <TableCell>Message preview</TableCell>
                    <TableCell>Queued</TableCell>
                    <TableCell>Last activity</TableCell>
                    <TableCell align="right">Expand</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => {
                    const expanded = Boolean(expandedRows[row.logicalNotificationId]);
                    const channels = channelSummary(row);
                    const channelTitle = channels.map((entry) => `${entry.label}: ${entry.title}`).join(" · ");
                    return (
                      <React.Fragment key={row.logicalNotificationId}>
                        <TableRow hover>
                          <TableCell>
                            <Chip
                              size="small"
                              label={categoryLabel(row)}
                              color={REMINDER_EVENT_TYPES.has(row.eventType) ? "warning" : "default"}
                              variant={REMINDER_EVENT_TYPES.has(row.eventType) ? "filled" : "outlined"}
                            />
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{eventLabel(row)}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.subject || "No subject"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{patientName(row.patientId)}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" aria-label={channelTitle} sx={{ maxWidth: 420, rowGap: 0.75 }}>
                              {channels.map((entry) => {
                                const tone = entry.tone === "success"
                                  ? { dot: "#2e7d32", bg: "success.50", border: "success.200" }
                                  : entry.tone === "warning"
                                    ? { dot: "#ed6c02", bg: "warning.50", border: "warning.200" }
                                    : entry.tone === "error"
                                      ? { dot: "#d32f2f", bg: "error.50", border: "error.200" }
                                      : { dot: "#6b7280", bg: "grey.100", border: "grey.300" };
                                const title = `${entry.label}: ${entry.title}`;
                                return (
                                  <Tooltip key={entry.channel} title={title} arrow>
                                    <Box
                                      component="span"
                                      aria-label={title}
                                      sx={{
                                        display: "inline-flex",
                                        alignItems: "center",
                                        gap: 0.75,
                                        px: 1,
                                        py: 0.35,
                                        borderRadius: 999,
                                        border: "1px solid",
                                        borderColor: tone.border,
                                        backgroundColor: tone.bg,
                                        maxWidth: "100%",
                                        flexShrink: 0,
                                      }}
                                    >
                                      <Box
                                        sx={{
                                          width: 8,
                                          height: 8,
                                          borderRadius: "50%",
                                          backgroundColor: tone.dot,
                                          flexShrink: 0,
                                        }}
                                      />
                                      <Typography variant="caption" sx={{ fontWeight: 700, whiteSpace: "nowrap", lineHeight: 1.2 }}>
                                        {entry.label}
                                      </Typography>
                                    </Box>
                                  </Tooltip>
                                );
                              })}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={overallStatusLabel(row.overallStatus)} color={overallStatusColor(row.overallStatus)} />
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={row.readState === "READ" ? "Read" : "Unread"} color={row.readState === "READ" ? "success" : "warning"} />
                          </TableCell>
                          <TableCell sx={{ width: 420, maxWidth: 420, verticalAlign: "top" }}>
                            <Tooltip title={row.message} arrow placement="top-start">
                              <Box
                                tabIndex={0}
                                sx={{
                                  display: "-webkit-box",
                                  WebkitLineClamp: 2,
                                  WebkitBoxOrient: "vertical",
                                  overflow: "hidden",
                                  textOverflow: "ellipsis",
                                  whiteSpace: "normal",
                                  lineHeight: 1.35,
                                  maxHeight: "calc(2 * 1.35em)",
                                  outline: "none",
                                }}
                              >
                                <Typography variant="body2" component="span" sx={{ lineHeight: 1.35 }}>
                                  {row.message}
                                </Typography>
                              </Box>
                            </Tooltip>
                          </TableCell>
                          <TableCell>{formatTimestamp(row.queuedAt)}</TableCell>
                          <TableCell>{formatTimestamp(row.lastActivityAt)}</TableCell>
                          <TableCell align="right">
                            <IconButton
                              aria-label={expanded ? "Collapse notification details" : "Expand notification details"}
                              aria-expanded={expanded}
                              aria-controls={`notification-details-${row.logicalNotificationId}`}
                              onClick={() => toggleExpanded(row.logicalNotificationId)}
                              size="small"
                            >
                              {expanded ? <KeyboardArrowUp fontSize="small" /> : <KeyboardArrowDown fontSize="small" />}
                            </IconButton>
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell colSpan={10} sx={{ py: 0, borderBottom: 0 }}>
                            <Collapse in={expanded} timeout="auto" unmountOnExit>
                              <Box id={`notification-details-${row.logicalNotificationId}`} sx={{ py: 2, pl: 1, pr: 1 }}>
                                <Stack spacing={1}>
                                  {channels.map((entry) => {
                                    const delivery = entry.delivery;
                                    return (
                                      <Box key={entry.channel} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1.5 }}>
                                        <Stack spacing={1}>
                                          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={2} flexWrap="wrap">
                                            <Box>
                                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                                {entry.label}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                Display status: {delivery ? channelPresentation(delivery).statusLabel : "Not enabled"}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                Recipient: {delivery ? (entry.channel === "IN_APP" ? patientName(row.patientId) : maskRecipient(delivery.recipient)) : "Not enabled"}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                Provider: {delivery ? (entry.channel === "IN_APP" ? "Internal" : channelLabel(entry.channel) + " provider") : "Not enabled"}
                                              </Typography>
                                            </Box>
                                            {canRetry ? (
                                              <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent="flex-end">
                                                {delivery ? (
                                                  <>
                                                    {delivery.readAt ? (
                                                      <Button size="small" disabled={workingId === delivery.id} onClick={() => void markUnread(delivery.id)}>
                                                        Mark unread
                                                      </Button>
                                                    ) : (
                                                      <Button size="small" disabled={workingId === delivery.id} onClick={() => void markRead(delivery.id)}>
                                                        Mark read
                                                      </Button>
                                                    )}
                                                    <Button size="small" disabled={workingId === delivery.id || (delivery.status !== "FAILED" && delivery.status !== "SKIPPED")} onClick={() => void retry(delivery.id)}>
                                                      Retry
                                                    </Button>
                                                  </>
                                                ) : null}
                                              </Stack>
                                            ) : null}
                                          </Stack>
                                          <Stack direction="row" spacing={2} flexWrap="wrap">
                                          <Typography variant="body2">
                                            <strong>Display status:</strong> {delivery ? channelPresentation(delivery).statusLabel : "Not enabled"}
                                          </Typography>
                                            <Typography variant="body2">
                                              <strong>Queued:</strong> {formatTimestamp(delivery?.createdAt ?? null)}
                                            </Typography>
                                            <Typography variant="body2">
                                              <strong>Sent:</strong> {formatTimestamp(delivery?.sentAt ?? null)}
                                            </Typography>
                                            <Typography variant="body2">
                                              <strong>Failure/skip:</strong> {delivery?.failureReason ? normalizeNotificationReason(entry.channel, delivery.failureReason) : delivery ? "None" : "Not enabled"}
                                            </Typography>
                                          </Stack>
                                        </Stack>
                                      </Box>
                                    );
                                  })}
                                </Stack>
                              </Box>
                            </Collapse>
                          </TableCell>
                        </TableRow>
                      </React.Fragment>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
