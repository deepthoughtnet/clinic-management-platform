import * as React from "react";
import { useNavigate } from "react-router-dom";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../auth/useAuth";
import {
  getClinicDashboard,
  getClinicUsers,
  getPlatformPlans,
  getPlatformTenants,
  type ClinicDashboard,
  type ClinicUser,
} from "../api/clinicApi";

type DatePreset = "TODAY" | "YESTERDAY" | "LAST_7_DAYS" | "LAST_30_DAYS" | "THIS_MONTH" | "CUSTOM";

function formatMoney(value: number | null | undefined) {
  return (value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatTime(value: string | null | undefined) {
  if (!value) return "-";
  return value.slice(0, 5);
}

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

function dateRangeForPreset(preset: DatePreset) {
  const today = new Date();
  const end = new Date(today);
  const start = new Date(today);
  if (preset === "YESTERDAY") {
    start.setDate(start.getDate() - 1);
    end.setDate(end.getDate() - 1);
  } else if (preset === "LAST_7_DAYS") {
    start.setDate(start.getDate() - 6);
  } else if (preset === "LAST_30_DAYS") {
    start.setDate(start.getDate() - 29);
  } else if (preset === "THIS_MONTH") {
    start.setDate(1);
  }
  return { startDate: isoDate(start), endDate: isoDate(end) };
}

function KpiCard({ label, value, tone }: { label: string; value: string | number; tone: "primary" | "success" | "warning" | "error" | "info" }) {
  const bg = {
    primary: "linear-gradient(180deg, rgba(25,118,210,0.12), rgba(25,118,210,0.03))",
    success: "linear-gradient(180deg, rgba(46,125,50,0.12), rgba(46,125,50,0.03))",
    warning: "linear-gradient(180deg, rgba(237,108,2,0.14), rgba(237,108,2,0.03))",
    error: "linear-gradient(180deg, rgba(211,47,47,0.12), rgba(211,47,47,0.03))",
    info: "linear-gradient(180deg, rgba(2,136,209,0.12), rgba(2,136,209,0.03))",
  }[tone];

  return (
    <Card variant="outlined" sx={{ background: bg, transition: "transform 0.2s ease", "&:hover": { transform: "translateY(-2px)" } }}>
      <CardContent>
        <Typography variant="overline" sx={{ opacity: 0.75 }}>{label}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const isBillingUser = tenantRole === "BILLING_USER";
  const isReceptionist = tenantRole === "RECEPTIONIST";
  const isAuditor = tenantRole === "AUDITOR";
  const isClinicAdmin = tenantRole === "CLINIC_ADMIN";
  const canBilling = auth.hasPermission("billing.read") || auth.hasPermission("payment.collect") || tenantRole === "CLINIC_ADMIN" || isBillingUser;

  const [dashboard, setDashboard] = React.useState<ClinicDashboard | null>(null);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [preset, setPreset] = React.useState<DatePreset>("TODAY");
  const initialRange = dateRangeForPreset("TODAY");
  const [startDate, setStartDate] = React.useState(initialRange.startDate);
  const [endDate, setEndDate] = React.useState(initialRange.endDate);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const [platformTenants, setPlatformTenants] = React.useState(0);
  const [platformActiveTenants, setPlatformActiveTenants] = React.useState(0);
  const [platformPlans, setPlatformPlans] = React.useState(0);

  React.useEffect(() => {
    let cancelled = false;
    async function loadPlatform() {
      if (!auth.accessToken || !isPlatformAdmin || auth.tenantId) return;
      try {
        const [tenants, plans] = await Promise.all([getPlatformTenants(auth.accessToken), getPlatformPlans(auth.accessToken)]);
        if (cancelled) return;
        setPlatformTenants(tenants.length);
        setPlatformActiveTenants(tenants.filter((t) => (t.status || "").toUpperCase() === "ACTIVE").length);
        setPlatformPlans(plans.length);
      } catch {
        if (!cancelled) {
          setPlatformTenants(0);
          setPlatformActiveTenants(0);
          setPlatformPlans(0);
        }
      }
    }
    void loadPlatform();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, isPlatformAdmin]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadUsers() {
      if (!auth.accessToken || !auth.tenantId) return;
      try {
        const rows = await getClinicUsers(auth.accessToken, auth.tenantId);
        if (cancelled) return;
        setUsers(rows);
        if (!isDoctor && !doctorUserId) {
          const firstDoctor = rows.find((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
          if (firstDoctor) setDoctorUserId(firstDoctor.appUserId);
        }
      } catch {
        if (!cancelled) setUsers([]);
      }
    }
    void loadUsers();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, doctorUserId, isDoctor]);

  const applyPreset = (next: DatePreset) => {
    setPreset(next);
    if (next !== "CUSTOM") {
      const range = dateRangeForPreset(next);
      setStartDate(range.startDate);
      setEndDate(range.endDate);
    }
  };

  const loadDashboard = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getClinicDashboard(auth.accessToken, auth.tenantId, {
        startDate,
        endDate,
        doctorUserId: isDoctor ? undefined : (doctorUserId || undefined),
      });
      setDashboard(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load clinic dashboard");
      setDashboard(null);
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, startDate, endDate, doctorUserId, isDoctor]);

  React.useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  if (!auth.tenantId && isPlatformAdmin) {
    return (
      <Stack spacing={2}>
        <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => navigate("/platform/tenants")}>Open Tenants</Button>}>
          Select a clinic tenant to view operational dashboard.
        </Alert>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}><KpiCard label="Total tenants" value={platformTenants} tone="primary" /></Grid>
          <Grid size={{ xs: 12, sm: 4 }}><KpiCard label="Active tenants" value={platformActiveTenants} tone="success" /></Grid>
          <Grid size={{ xs: 12, sm: 4 }}><KpiCard label="Plans" value={platformPlans} tone="info" /></Grid>
        </Grid>
      </Stack>
    );
  }

  if (!auth.tenantId) return <Alert severity="warning">No tenant selected for this session.</Alert>;

  const doctorOptions = users.filter((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
  const appt = dashboard?.appointmentSummary;
  const queue = dashboard?.queueSummary;
  const consult = dashboard?.consultationSummary;
  const billing = dashboard?.billingSummary;
  const followUp = dashboard?.followUpSummary;
  const rx = dashboard?.prescriptionSummary;
  const showOperational = Boolean(appt || queue || consult || rx || followUp);
  const showBilling = Boolean(billing);

  const dashboardTitle = isPlatformAdmin && !auth.tenantId
    ? "Platform Dashboard"
    : isBillingUser
      ? "Billing Dashboard"
      : isDoctor
        ? "My Doctor Dashboard"
        : isReceptionist
          ? "Reception Dashboard"
          : isAuditor
            ? "Audit Dashboard"
            : isClinicAdmin
              ? "Clinic Dashboard"
              : "Dashboard";

  const cards = dashboard ? [
    { label: "Appointments", value: appt?.totalToday || 0, tone: "primary" as const },
    { label: "Checked-in", value: appt?.checkedIn || 0, tone: "warning" as const },
    { label: "Waiting Queue", value: queue?.waiting || 0, tone: "warning" as const },
    { label: "In Consultation", value: appt?.inConsultation || 0, tone: "info" as const },
    { label: "Completed", value: consult?.completed || 0, tone: "success" as const },
    { label: "No-shows", value: appt?.noShow || 0, tone: "error" as const },
    { label: "Cancelled", value: appt?.cancelled || 0, tone: "error" as const },
    { label: "Prescriptions", value: rx?.prescriptionsGenerated || 0, tone: "info" as const },
    { label: "Pending Bills", value: billing?.pendingBills || 0, tone: "warning" as const },
    { label: "Revenue", value: formatMoney(billing?.totalBilled), tone: "success" as const },
  ] : [];

  const financeCards = dashboard ? [
    { label: "Bills Created", value: billing?.billsCreated || 0, tone: "info" as const },
    { label: "Payments Received", value: formatMoney(billing?.totalPaid), tone: "success" as const },
    { label: "Pending Amount", value: formatMoney(billing?.pendingAmount), tone: "warning" as const },
    { label: "Pending Invoices", value: billing?.pendingBills || 0, tone: "error" as const },
  ] : [];

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>{dashboardTitle}</Typography>
          <Typography variant="body2" color="text.secondary">Operational and analytics summary from {startDate} to {endDate}.</Typography>
        </Box>
        <Chip label={auth.tenantName || "Clinic"} variant="outlined" />
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card variant="outlined">
        <CardContent>
          <Stack direction={{ xs: "column", lg: "row" }} spacing={1.25} alignItems={{ lg: "center" }}>
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel id="preset-label">Preset</InputLabel>
              <Select labelId="preset-label" label="Preset" value={preset} onChange={(e) => applyPreset(e.target.value as DatePreset)}>
                <MenuItem value="TODAY">Today</MenuItem>
                <MenuItem value="YESTERDAY">Yesterday</MenuItem>
                <MenuItem value="LAST_7_DAYS">Last 7 Days</MenuItem>
                <MenuItem value="LAST_30_DAYS">Last 30 Days</MenuItem>
                <MenuItem value="THIS_MONTH">This Month</MenuItem>
                <MenuItem value="CUSTOM">Custom Range</MenuItem>
              </Select>
            </FormControl>
            <TextField size="small" type="date" label="Start" value={startDate} onChange={(e) => { setPreset("CUSTOM"); setStartDate(e.target.value); }} InputLabelProps={{ shrink: true }} />
            <TextField size="small" type="date" label="End" value={endDate} onChange={(e) => { setPreset("CUSTOM"); setEndDate(e.target.value); }} InputLabelProps={{ shrink: true }} />
            {!isDoctor && !isBillingUser ? (
              <FormControl size="small" sx={{ minWidth: 220 }}>
                <InputLabel id="dashboard-doctor">Doctor</InputLabel>
                <Select labelId="dashboard-doctor" label="Doctor" value={doctorUserId} onChange={(e) => setDoctorUserId(String(e.target.value))}>
                  <MenuItem value="">All doctors</MenuItem>
                  {doctorOptions.map((doctor) => (
                    <MenuItem key={doctor.appUserId} value={doctor.appUserId}>{doctor.displayName || doctor.email || doctor.appUserId}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            ) : null}
            <Button variant="contained" onClick={() => void loadDashboard()}>Refresh</Button>
          </Stack>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box>
      ) : !dashboard ? (
        <Alert severity="info">No dashboard data available for the selected filters.</Alert>
      ) : (
        <Stack spacing={2}>
          <Grid container spacing={2}>
            {(showBilling && !showOperational ? financeCards : cards).map((card) => (
              <Grid key={card.label} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                <KpiCard {...card} />
              </Grid>
            ))}
          </Grid>

          <Grid container spacing={2}>
            {showOperational ? (
              <Grid size={{ xs: 12, lg: 7 }}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Doctor-wise Summary</Typography>
                    {dashboard.doctorSummaries.length === 0 ? <Alert severity="info">No doctor metrics for selected range.</Alert> : (
                      <Box sx={{ overflowX: "auto" }}>
                        <Table size="small" sx={{ minWidth: 880 }}>
                          <TableHead>
                            <TableRow>
                              <TableCell>Doctor</TableCell>
                              <TableCell>Appointments</TableCell>
                              <TableCell>Checked-in</TableCell>
                              <TableCell>Completed</TableCell>
                              <TableCell>Prescriptions</TableCell>
                              <TableCell>Avg Load</TableCell>
                              <TableCell>No-show</TableCell>
                              <TableCell>Cancelled</TableCell>
                              <TableCell>Next</TableCell>
                              {canBilling && showBilling ? <TableCell>Revenue</TableCell> : null}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dashboard.doctorSummaries.map((row) => (
                              <TableRow key={row.doctorUserId} hover>
                                <TableCell>{row.doctorName || row.doctorUserId}</TableCell>
                                <TableCell>{row.appointmentsToday}</TableCell>
                                <TableCell>{row.checkedIn}</TableCell>
                                <TableCell>{row.completed}</TableCell>
                                <TableCell>{row.prescriptionsGenerated}</TableCell>
                                <TableCell>{row.avgConsultationLoad}</TableCell>
                                <TableCell>{row.noShow}</TableCell>
                                <TableCell>{row.cancelled}</TableCell>
                                <TableCell>{formatTime(row.nextAppointmentTime)}</TableCell>
                                {canBilling && showBilling ? <TableCell>{formatMoney(row.revenue)}</TableCell> : null}
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </Box>
                    )}
                  </CardContent>
                </Card>

                <Card variant="outlined" sx={{ mt: 2 }}>
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Queue Snapshot</Typography>
                    {dashboard.currentWaitingList.length === 0 ? <Alert severity="info">No active queue items.</Alert> : (
                      <Stack spacing={1}>
                        {dashboard.currentWaitingList.map((item) => (
                          <Box key={item.appointmentId} sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.25 }}>
                            <Box>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.patientName || item.patientNumber || item.patientId}</Typography>
                              <Typography variant="caption" color="text.secondary">{item.doctorName || item.doctorUserId} • Token {item.tokenNumber ?? "-"} • {formatTime(item.appointmentTime)}</Typography>
                            </Box>
                            <Chip size="small" label={item.status} color={item.status === "WAITING" ? "warning" : "info"} />
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </CardContent>
                </Card>
              </Grid>
            ) : null}

            <Grid size={{ xs: 12, lg: showOperational ? 5 : 12 }}>
              {showBilling ? (
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>{isBillingUser ? "Finance Snapshot" : "Billing Snapshot"}</Typography>
                  <Stack spacing={0.75}>
                    <Typography variant="body2">Total billed: <b>{formatMoney(billing?.totalBilled)}</b></Typography>
                    <Typography variant="body2">Paid: <b>{formatMoney(billing?.totalPaid)}</b></Typography>
                    <Typography variant="body2">Pending amount: <b>{formatMoney(billing?.pendingAmount)}</b></Typography>
                    <Typography variant="body2">Pending bill count: <b>{billing?.pendingBills || 0}</b></Typography>
                  </Stack>
                  <Typography variant="subtitle2" sx={{ mt: 1.5, mb: 0.75 }}>Recent unpaid bills</Typography>
                  {dashboard.recentUnpaidBills.length === 0 ? <Alert severity="info">No unpaid bills in range.</Alert> : (
                    <Stack spacing={0.75}>
                      {dashboard.recentUnpaidBills.map((bill) => (
                        <Box key={bill.billId} sx={{ display: "flex", justifyContent: "space-between", gap: 1 }}>
                          <Typography variant="caption">{bill.billNumber} • {bill.patientName || bill.patientId}</Typography>
                          <Typography variant="caption" sx={{ fontWeight: 700 }}>{formatMoney(bill.dueAmount)}</Typography>
                        </Box>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
              ) : null}

              {showOperational ? (
                <Card variant="outlined" sx={{ mt: 2 }}>
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Prescription & Follow-up</Typography>
                    <Stack spacing={0.75}>
                      <Typography variant="body2">Prescriptions generated: <b>{rx?.prescriptionsGenerated || 0}</b></Typography>
                      <Typography variant="body2">Consultations with prescriptions: <b>{rx?.consultationsWithPrescriptions || 0}</b></Typography>
                      <Typography variant="body2">Avg Rx per consultation: <b>{rx?.avgPrescriptionsPerConsultation || 0}</b></Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1.25 }}>
                      <Chip size="small" label={`Due in range ${followUp?.dueInRange || 0}`} color="warning" />
                      <Chip size="small" label={`Overdue ${followUp?.overdue || 0}`} color="error" />
                      <Chip size="small" label={`Next 7 days ${followUp?.upcomingNext7Days || 0}`} color="info" />
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              <Card variant="outlined" sx={{ mt: 2, position: { lg: "sticky" }, top: { lg: 16 } }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Recent Activity</Typography>
                  {dashboard.recentActivity.length === 0 ? <Alert severity="info">No recent activity.</Alert> : (
                    <Stack spacing={1}>
                      {dashboard.recentActivity.slice(0, 14).map((item, idx) => (
                        <Box key={`${item.timestamp}-${idx}`} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1.25, p: 1 }}>
                          <Typography variant="caption" color="text.secondary">{item.timestamp ? new Date(item.timestamp).toLocaleString() : "-"} • {item.type}</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.title}</Typography>
                          <Typography variant="caption" color="text.secondary">{item.description}</Typography>
                        </Box>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
          {!showOperational && !showBilling ? <Alert severity="info">No report sections are available for your current access level.</Alert> : null}
        </Stack>
      )}
    </Stack>
  );
}
