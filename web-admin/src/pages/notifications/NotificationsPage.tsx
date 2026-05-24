import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
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
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  getNotifications,
  retryNotification,
  searchPatients,
  type NotificationChannel,
  type NotificationEventType,
  type NotificationHistory,
  type NotificationStatus,
  type Patient,
} from "../../api/clinicApi";

const STATUS_OPTIONS: Array<NotificationStatus | ""> = ["", "PENDING", "SENT", "FAILED", "SKIPPED"];
const CHANNEL_OPTIONS: Array<NotificationChannel | ""> = ["", "EMAIL", "WHATSAPP", "SMS", "PUSH"];
const EVENT_OPTIONS: Array<NotificationEventType | ""> = [
  "",
  "PRESCRIPTION_READY",
  "PRESCRIPTION_SENT",
  "BILL_PAID",
  "RECEIPT_SENT",
  "FOLLOW_UP_REMINDER",
  "VACCINATION_REMINDER",
  "APPOINTMENT_REMINDER",
  "PAYMENT_REMINDER",
  "MISSED_APPOINTMENT_REMINDER",
];

const REMINDER_EVENT_TYPES = new Set<NotificationEventType>([
  "FOLLOW_UP_REMINDER",
  "VACCINATION_REMINDER",
  "APPOINTMENT_REMINDER",
  "PAYMENT_REMINDER",
  "MISSED_APPOINTMENT_REMINDER",
]);

function statusColor(status: NotificationHistory["status"]) {
  switch (status) {
    case "SENT":
      return "success";
    case "PENDING":
      return "info";
    case "FAILED":
      return "error";
    case "SKIPPED":
      return "default";
  }
}

export default function NotificationsPage() {
  const auth = useAuth();
  const canView =
    auth.rolesUpper.includes("CLINIC_ADMIN") ||
    auth.rolesUpper.includes("RECEPTIONIST") ||
    auth.rolesUpper.includes("AUDITOR") ||
    (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const [rows, setRows] = React.useState<NotificationHistory[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [filters, setFilters] = React.useState({
    status: "" as NotificationStatus | "",
    eventType: "" as NotificationEventType | "",
    channel: "" as NotificationChannel | "",
    patientId: "",
    from: "",
    to: "",
  });
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
    setLoading(true);
    setError(null);
    try {
      const value = await getNotifications(auth.accessToken, auth.tenantId, {
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

  const reminderRows = rows.filter((row) => REMINDER_EVENT_TYPES.has(row.eventType));
  const pendingCount = rows.filter((row) => row.status === "PENDING").length;
  const failedCount = rows.filter((row) => row.status === "FAILED").length;

  const patientLabel = (row: NotificationHistory) => {
    const patient = patients.find((item) => item.id === row.patientId);
    if (patient) {
      return `${patient.firstName} ${patient.lastName}${patient.patientNumber ? ` • ${patient.patientNumber}` : ""}`;
    }
    return row.patientId ? "Patient record unavailable" : "Walk-in / system";
  };

  const eventLabel = (row: NotificationHistory) => row.eventType.replaceAll("_", " ");
  const categoryLabel = (row: NotificationHistory) => (REMINDER_EVENT_TYPES.has(row.eventType) ? "Reminder" : "Notification");
  const sourceLabel = (row: NotificationHistory) => {
    if (row.sourceType === "APPOINTMENT") return "Appointment";
    if (row.sourceType === "BILL") return "Bill";
    if (row.sourceType === "CONSULTATION") return "Consultation";
    if (row.sourceType === "PRESCRIPTION") return "Prescription";
    if (row.sourceType === "PATIENT_VACCINATION") return "Vaccination";
    if (row.sourceType === "RECEIPT") return "Receipt";
    return row.sourceType ? row.sourceType.replaceAll("_", " ") : "-";
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
                <Typography variant="overline" color="text.secondary">Queued / Pending</Typography>
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
                <Typography variant="overline" color="text.secondary">Reminder records</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900 }}>{reminderRows.length}</Typography>
                <Typography variant="body2" color="text.secondary">Appointment, follow-up, vaccination, and payment reminders.</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={0.75}>
                <Typography variant="overline" color="text.secondary">Failed / Skipped</Typography>
                <Typography variant="h5" sx={{ fontWeight: 900 }}>{failedCount}</Typography>
                <Typography variant="body2" color="text.secondary">Review failures and retry only when provider configuration is ready.</Typography>
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
                  <InputLabel id="notification-status-label">Status</InputLabel>
                  <Select
                    labelId="notification-status-label"
                    label="Status"
                    value={filters.status}
                    onChange={(e) => setFilters((current) => ({ ...current, status: String(e.target.value) as NotificationStatus | "" }))}
                  >
                    {STATUS_OPTIONS.map((value) => (
                      <MenuItem key={value || "all"} value={value}>
                        {value || "All"}
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
                <TextField fullWidth label="From" type="date" value={filters.from} onChange={(e) => setFilters((current) => ({ ...current, from: e.target.value }))} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="To" type="date" value={filters.to} onChange={(e) => setFilters((current) => ({ ...current, to: e.target.value }))} InputLabelProps={{ shrink: true }} />
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
                    <TableCell>Recipient</TableCell>
                    <TableCell>Channel</TableCell>
                    <TableCell>Source</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Message preview</TableCell>
                    <TableCell>Queued</TableCell>
                    <TableCell>Sent</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} hover>
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
                      <TableCell>{patientLabel(row)}</TableCell>
                      <TableCell>{row.recipient}</TableCell>
                      <TableCell>{row.channel}</TableCell>
                      <TableCell>{sourceLabel(row)}</TableCell>
                      <TableCell>
                        <Stack spacing={0.5} alignItems="flex-start">
                          <Chip size="small" label={row.status} color={statusColor(row.status)} />
                          {row.failureReason ? (
                            <Typography variant="caption" color="error.main">{row.failureReason}</Typography>
                          ) : null}
                        </Stack>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 360 }}>
                        <Typography variant="body2" noWrap title={row.message}>{row.message}</Typography>
                      </TableCell>
                      <TableCell>{new Date(row.createdAt).toLocaleString()}</TableCell>
                      <TableCell>{row.sentAt ? new Date(row.sentAt).toLocaleString() : "-"}</TableCell>
                      <TableCell align="right">
                        <Button size="small" disabled={workingId === row.id || (row.status !== "FAILED" && row.status !== "SKIPPED")} onClick={() => void retry(row.id)}>
                          Retry
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
