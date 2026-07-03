import * as React from "react";
import { Navigate, useNavigate } from "react-router-dom";
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
import { dashboardFilterSchema, firstZodError } from "@deepthoughtnet/form-validation-kit";
import { WorkflowStrip } from "../components/compact/CompactUi";
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

function friendlyStatusLabel(value: string | null | undefined) {
  if (!value) return "-";
  switch (value.toUpperCase()) {
    case "PARTIALLY_BOOKED":
      return "Partially booked";
    case "IN_CONSULTATION":
      return "In consultation";
    case "NO_SHOW":
      return "No-show";
    case "AVAILABLE":
      return "Available";
    case "BOOKED":
      return "Booked";
    case "CHECKED_IN":
      return "Checked in";
    case "CANCELLED":
      return "Cancelled";
    case "COMPLETED":
      return "Completed";
    default:
      return value.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\S/g, (match) => match.toUpperCase());
  }
}

function displayNameForUser(user: ClinicUser | undefined, fallback: string) {
  if (!user) return fallback;
  return user.displayName || user.email || fallback;
}

const DASHBOARD_CHIP_SX = {
  height: 24,
  borderRadius: 999,
  fontSize: "0.72rem",
  "& .MuiChip-label": {
    px: 0.85,
    py: 0,
  },
} as const;

const DASHBOARD_WORKFLOW_STEPS = [
  { label: "Appointment" },
  { label: "Check-in" },
  { label: "Queue" },
  { label: "Consultation" },
  { label: "Billing" },
  { label: "Follow-up" },
] as const;

function KpiCard({
  label,
  value,
  tone,
  onClick,
}: {
  label: string;
  value: string | number;
  tone: "primary" | "success" | "warning" | "error" | "info";
  onClick?: () => void;
}) {
  const bg = {
    primary: "linear-gradient(180deg, rgba(25,118,210,0.12), rgba(25,118,210,0.03))",
    success: "linear-gradient(180deg, rgba(46,125,50,0.12), rgba(46,125,50,0.03))",
    warning: "linear-gradient(180deg, rgba(237,108,2,0.14), rgba(237,108,2,0.03))",
    error: "linear-gradient(180deg, rgba(211,47,47,0.12), rgba(211,47,47,0.03))",
    info: "linear-gradient(180deg, rgba(2,136,209,0.12), rgba(2,136,209,0.03))",
  }[tone];
  const interactive = Boolean(onClick);

  return (
    <Card
      variant="outlined"
      role={interactive ? "button" : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={onClick}
      onKeyDown={interactive ? (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick?.();
        }
      } : undefined}
      sx={{
        minHeight: 96,
        background: bg,
        transition: "transform 0.2s ease, box-shadow 0.2s ease",
        cursor: interactive ? "pointer" : "default",
        "&:hover": { transform: interactive ? "translateY(-2px)" : "none", boxShadow: interactive ? 2 : 0 },
      }}
    >
      <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
        <Stack spacing={0.35} sx={{ height: "100%", justifyContent: "space-between" }}>
          <Typography variant="caption" sx={{ opacity: 0.8, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.3 }}>
            {label}
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1 }}>
            {value}
          </Typography>
        </Stack>
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
  const isPharmacyRole = tenantRole === "PHARMA"
    || tenantRole === "PHARMACY"
    || tenantRole === "PHARMACIST"
    || tenantRole === "PHARMACY_INVENTORY_MANAGER"
    || tenantRole === "PHARMACY_POS_USER";
  const canBilling = auth.hasPermission("billing.read") || auth.hasPermission("payment.collect") || tenantRole === "CLINIC_ADMIN" || isBillingUser;
  const canUseAppointmentShortcuts = !isBillingUser && !isAuditor && (auth.hasPermission("appointment.manage") || auth.hasPermission("appointment.read"));
  const canCreateAppointments = !isDoctor && !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");
  const canOpenDayBoard = !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");
  const canOpenQueue = !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");

  const [dashboard, setDashboard] = React.useState<ClinicDashboard | null>(null);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [preset, setPreset] = React.useState<DatePreset>("TODAY");
  const initialRange = dateRangeForPreset("TODAY");
  const [startDate, setStartDate] = React.useState(initialRange.startDate);
  const [endDate, setEndDate] = React.useState(initialRange.endDate);
  const [dateFieldErrors, setDateFieldErrors] = React.useState<Record<string, string>>({});
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
      } catch {
        if (!cancelled) setUsers([]);
      }
    }
    void loadUsers();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, isDoctor]);

  React.useEffect(() => {
    if (isDoctor && auth.appUserId) {
      setDoctorUserId(auth.appUserId);
      return;
    }
    if (!isDoctor && doctorUserId === auth.appUserId) {
      setDoctorUserId("");
    }
  }, [auth.appUserId, doctorUserId, isDoctor]);

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
    const parsed = dashboardFilterSchema.safeParse({
      startDate,
      endDate,
      doctorUserId: isDoctor ? undefined : (doctorUserId || undefined),
    });
    if (!parsed.success) {
      setDateFieldErrors({
        startDate: parsed.error.issues.find((issue) => issue.path.join(".") === "startDate")?.message || "",
        endDate: parsed.error.issues.find((issue) => issue.path.join(".") === "endDate")?.message || "",
        doctorUserId: parsed.error.issues.find((issue) => issue.path.join(".") === "doctorUserId")?.message || "",
      });
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        document.getElementById(parsed.error.issues[0]?.path[0] === "endDate" ? "dashboard-endDate" : "dashboard-startDate")?.focus();
      }, 0);
      return;
    }
    setDateFieldErrors({});
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
  if (isPharmacyRole) return <Navigate to="/pharmacy/dashboard" replace />;

  const doctorOptions = users.filter((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
  const selectedDoctor = doctorOptions.find((doctor) => doctor.appUserId === doctorUserId) || null;
  const selectedDoctorLabel = doctorUserId ? displayNameForUser(selectedDoctor || undefined, doctorUserId) : "All Doctors";
  const appt = dashboard?.appointmentSummary;
  const queue = dashboard?.queueSummary;
  const consult = dashboard?.consultationSummary;
  const billing = dashboard?.billingSummary;
  const followUp = dashboard?.followUpSummary;
  const rx = dashboard?.prescriptionSummary;
  const waitByDoctor = React.useMemo(() => {
    const map = new Map<string, number>();
    for (const item of dashboard?.currentWaitingList || []) {
      const key = item.doctorUserId || "__unassigned__";
      map.set(key, (map.get(key) || 0) + 1);
    }
    return map;
  }, [dashboard?.currentWaitingList]);
  const showOperational = Boolean(appt || queue || consult || rx || followUp);
  const showBilling = Boolean(billing);
  const withDoctorFilter = React.useCallback((path: string) => {
    if (isDoctor || !doctorUserId) return path;
    return `${path}${path.includes("?") ? "&" : "?"}doctorUserId=${encodeURIComponent(doctorUserId)}`;
  }, [doctorUserId, isDoctor]);
  const openDashboardSection = React.useCallback((section: "appointments" | "queue" | "consultations" | "prescriptions" | "followups" | "billing") => {
    switch (section) {
      case "appointments":
        navigate(withDoctorFilter("/appointments"));
        return;
      case "queue":
        navigate(withDoctorFilter("/queue"));
        return;
      case "consultations":
        navigate("/consultations");
        return;
      case "prescriptions":
        navigate(withDoctorFilter("/prescriptions"));
        return;
      case "followups":
        navigate(withDoctorFilter("/appointments/day-board"));
        return;
      case "billing":
        navigate(withDoctorFilter("/billing"));
        return;
    }
  }, [navigate, withDoctorFilter, isDoctor]);
  const openDoctorSummaryRow = React.useCallback((doctorId: string) => {
    if (isDoctor) {
      navigate("/queue");
      return;
    }
    navigate(`/appointments?doctorUserId=${encodeURIComponent(doctorId)}`);
  }, [isDoctor, navigate]);

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
    { label: "Appointments", value: appt?.totalToday || 0, tone: "primary" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Checked-in", value: appt?.checkedIn || 0, tone: "warning" as const, onClick: () => openDashboardSection("queue") },
    { label: "Waiting Queue", value: queue?.waiting || 0, tone: "warning" as const, onClick: () => openDashboardSection("queue") },
    { label: "In Consultation", value: appt?.inConsultation || 0, tone: "info" as const, onClick: () => openDashboardSection("consultations") },
    { label: "Completed", value: consult?.completed || 0, tone: "success" as const, onClick: () => openDashboardSection("consultations") },
    { label: "No-shows", value: appt?.noShow || 0, tone: "error" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Cancelled", value: appt?.cancelled || 0, tone: "error" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Prescriptions", value: rx?.prescriptionsGenerated || 0, tone: "info" as const, onClick: () => openDashboardSection("prescriptions") },
    { label: "Pending Bills", value: billing?.pendingBills || 0, tone: "warning" as const, onClick: () => openDashboardSection("billing") },
    { label: "Revenue", value: formatMoney(billing?.totalBilled), tone: "success" as const, onClick: () => openDashboardSection("billing") },
  ] : [];

  const financeCards = dashboard ? [
    { label: "Bills Created", value: billing?.billsCreated || 0, tone: "info" as const, onClick: () => openDashboardSection("billing") },
    { label: "Payments Received", value: formatMoney(billing?.totalPaid), tone: "success" as const, onClick: () => openDashboardSection("billing") },
    { label: "Pending Amount", value: formatMoney(billing?.pendingAmount), tone: "warning" as const, onClick: () => openDashboardSection("billing") },
    { label: "Pending Invoices", value: billing?.pendingBills || 0, tone: "error" as const, onClick: () => openDashboardSection("billing") },
  ] : [];

  return (
    <Stack spacing={1.75}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1.1 }}>{dashboardTitle}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            Operational and analytics summary from {startDate} to {endDate} • {selectedDoctorLabel}
          </Typography>
        </Box>
        <Chip label={auth.tenantName || "Clinic"} variant="outlined" size="small" sx={DASHBOARD_CHIP_SX} />
      </Box>

      <WorkflowStrip steps={DASHBOARD_WORKFLOW_STEPS} />

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card variant="outlined">
        <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
          <Stack direction={{ xs: "column", lg: "row" }} spacing={0.9} alignItems={{ lg: "center" }} useFlexGap flexWrap="wrap">
            <FormControl size="small" sx={{ minWidth: 160 }}>
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
            <TextField id="dashboard-startDate" size="small" sx={{ minWidth: 150 }} type="date" label="Start" value={startDate} onChange={(e) => { setPreset("CUSTOM"); setStartDate(e.target.value); }} InputLabelProps={{ shrink: true }} error={Boolean(dateFieldErrors.startDate)} helperText={dateFieldErrors.startDate || "Required."} />
            <TextField id="dashboard-endDate" size="small" sx={{ minWidth: 150 }} type="date" label="End" value={endDate} onChange={(e) => { setPreset("CUSTOM"); setEndDate(e.target.value); }} InputLabelProps={{ shrink: true }} error={Boolean(dateFieldErrors.endDate)} helperText={dateFieldErrors.endDate || "Must be on or after start."} />
            {!isDoctor && !isBillingUser ? (
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel id="dashboard-doctor">Doctor</InputLabel>
                <Select labelId="dashboard-doctor" label="Doctor" value={doctorUserId} onChange={(e) => setDoctorUserId(String(e.target.value))}>
                  <MenuItem value="">All Doctors</MenuItem>
                  {doctorOptions.map((doctor) => (
                    <MenuItem key={doctor.appUserId} value={doctor.appUserId}>{doctor.displayName || doctor.email || doctor.appUserId}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            ) : null}
            <Button size="small" sx={{ minHeight: 36, px: 1.5 }} variant="contained" onClick={() => void loadDashboard()}>Refresh</Button>
            {canCreateAppointments ? (
              <Button size="small" sx={{ minHeight: 36, px: 1.5 }} variant="outlined" onClick={() => navigate("/appointments")}>New appointment</Button>
            ) : null}
            {canOpenDayBoard ? (
              <Button size="small" sx={{ minHeight: 36, px: 1.5 }} variant="outlined" onClick={() => navigate("/appointments/day-board")}>Open day board</Button>
            ) : null}
            {canOpenQueue ? (
              <Button size="small" sx={{ minHeight: 36, px: 1.5 }} variant="outlined" onClick={() => navigate("/queue")}>Open queue</Button>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box>
      ) : !dashboard ? (
        <Alert severity="info">No dashboard data available for the selected filters.</Alert>
      ) : (
        <Stack spacing={1.5}>
          <Box
            sx={{
              display: "grid",
              gap: 1.25,
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2, minmax(0, 1fr))",
                lg: "repeat(auto-fit, minmax(280px, 1fr))",
              },
            }}
          >
            {((showBilling && (!showOperational || !canUseAppointmentShortcuts)) ? financeCards : cards).map((card) => (
              <Box key={card.label}>
                <KpiCard {...card} />
              </Box>
            ))}
          </Box>

          <Grid container spacing={1.5}>
            {showOperational && canUseAppointmentShortcuts ? (
              <Grid size={{ xs: 12, lg: 7 }}>
                <Card variant="outlined">
                  <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                    <Stack spacing={1}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.25, flexWrap: "wrap", alignItems: "center" }}>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>Today at a glance</Typography>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap">
                          <Chip size="small" label={`Filter: ${selectedDoctorLabel}`} color={doctorUserId ? "primary" : "default"} sx={DASHBOARD_CHIP_SX} />
                          <Chip size="small" clickable label={`Waiting ${queue?.waiting || 0}`} color="warning" sx={DASHBOARD_CHIP_SX} onClick={() => openDashboardSection("queue")} />
                          <Chip size="small" clickable label={`Checked in ${appt?.checkedIn || 0}`} color="info" sx={DASHBOARD_CHIP_SX} onClick={() => openDashboardSection("queue")} />
                          <Chip size="small" clickable label={`In consultation ${appt?.inConsultation || 0}`} color="success" sx={DASHBOARD_CHIP_SX} onClick={() => openDashboardSection("consultations")} />
                        </Stack>
                      </Box>
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12, sm: 6 }}>
                          <Card variant="outlined" sx={{ bgcolor: "background.paper", minHeight: 96, cursor: "pointer", transition: "transform 0.2s ease, box-shadow 0.2s ease", "&:hover": { transform: "translateY(-2px)", boxShadow: 2 } }} onClick={() => openDashboardSection("appointments")} role="button" tabIndex={0}>
                            <CardContent sx={{ py: 1.2, "&:last-child": { pb: 1.2 } }}>
                              <Typography variant="overline" color="text.secondary">Appointments today</Typography>
                              <Typography variant="h5" sx={{ fontWeight: 900 }}>{appt?.totalToday || 0}</Typography>
                              <Typography variant="body2" color="text.secondary">
                                Scheduled {appt?.scheduled || 0} • Completed {appt?.completed || 0} • No-show {appt?.noShow || 0} • Cancelled {appt?.cancelled || 0}
                              </Typography>
                            </CardContent>
                          </Card>
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6 }}>
                          <Card variant="outlined" sx={{ bgcolor: "background.paper", minHeight: 96, cursor: "pointer", transition: "transform 0.2s ease, box-shadow 0.2s ease", "&:hover": { transform: "translateY(-2px)", boxShadow: 2 } }} onClick={() => openDashboardSection("queue")} role="button" tabIndex={0}>
                            <CardContent sx={{ py: 1.2, "&:last-child": { pb: 1.2 } }}>
                              <Typography variant="overline" color="text.secondary">Queue state</Typography>
                              <Typography variant="h5" sx={{ fontWeight: 900 }}>{queue?.waiting || 0}</Typography>
                              <Typography variant="body2" color="text.secondary">
                                In consultation {queue?.inConsultation || 0} • Completed {queue?.completed || 0} • No-show {queue?.noShow || 0}
                              </Typography>
                            </CardContent>
                          </Card>
                        </Grid>
                      </Grid>
                    </Stack>
                  </CardContent>
                </Card>

                <Card variant="outlined" sx={{ mt: 1.25 }}>
                  <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.75 }}>Current waiting patients</Typography>
                    {dashboard.currentWaitingList.length === 0 ? <Alert severity="info">No waiting patients for the selected filters.</Alert> : (
                      <Stack spacing={1}>
                        {dashboard.currentWaitingList.slice(0, 8).map((item) => (
                          <Box key={item.appointmentId} sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", border: "1px solid", borderColor: "divider", borderRadius: 1.25, p: 1.1 }}>
                            <Box>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.patientName || item.patientNumber || item.patientId}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {item.doctorName || item.doctorUserId || "Unassigned"} • Token {item.tokenNumber ?? "-"} • {formatTime(item.appointmentTime)}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.75} flexWrap="wrap" alignItems="center">
                              <Chip size="small" label={friendlyStatusLabel(item.status)} color={item.status === "WAITING" ? "warning" : "info"} />
                              <Button size="small" variant="outlined" onClick={() => navigate(`/patients/${item.patientId}`)}>Open patient</Button>
                            </Stack>
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </CardContent>
                </Card>

                <Card variant="outlined" sx={{ mt: 1.25 }}>
                  <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.75 }}>Doctor-wise summary</Typography>
                    {dashboard.doctorSummaries.length === 0 ? <Alert severity="info">No doctor metrics for selected range.</Alert> : (
                      <Box sx={{ overflowX: "auto" }}>
                        <Table size="small" sx={{ minWidth: 980 }}>
                          <TableHead>
                            <TableRow>
                              <TableCell>Doctor</TableCell>
                              <TableCell>Appointments</TableCell>
                              <TableCell>Checked-in</TableCell>
                              <TableCell>Waiting</TableCell>
                              <TableCell>Completed</TableCell>
                              <TableCell>Next slot</TableCell>
                              <TableCell>No-show</TableCell>
                              <TableCell>Cancelled</TableCell>
                              <TableCell>Rx</TableCell>
                              {canBilling && showBilling ? <TableCell>Revenue</TableCell> : null}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dashboard.doctorSummaries.map((row) => (
                              <TableRow key={row.doctorUserId} hover sx={{ cursor: "pointer" }} onClick={() => openDoctorSummaryRow(row.doctorUserId)}>
                                <TableCell>{row.doctorName || row.doctorUserId}</TableCell>
                                <TableCell><Chip size="small" label={row.appointmentsToday} variant="outlined" /></TableCell>
                                <TableCell><Chip size="small" label={row.checkedIn} color="info" variant="outlined" /></TableCell>
                                <TableCell><Chip size="small" label={waitByDoctor.get(row.doctorUserId) || 0} color="warning" variant="outlined" /></TableCell>
                                <TableCell><Chip size="small" label={row.completed} color="success" variant="outlined" /></TableCell>
                                <TableCell>{formatTime(row.nextAppointmentTime)}</TableCell>
                                <TableCell><Chip size="small" label={row.noShow} variant="outlined" /></TableCell>
                                <TableCell><Chip size="small" label={row.cancelled} variant="outlined" /></TableCell>
                                <TableCell><Chip size="small" label={row.prescriptionsGenerated} color="primary" variant="outlined" /></TableCell>
                                {canBilling && showBilling ? <TableCell>{formatMoney(row.revenue)}</TableCell> : null}
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </Box>
                    )}
                  </CardContent>
                </Card>

                <Card variant="outlined" sx={{ mt: 1.25 }}>
                  <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.75 }}>Recent activity</Typography>
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
            ) : null}

            <Grid size={{ xs: 12, lg: showOperational ? 5 : 12 }}>
              {showBilling ? (
              <Card variant="outlined">
                <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.75 }}>{isBillingUser ? "Finance Snapshot" : "Billing Snapshot"}</Typography>
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
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" sx={{ mt: 1 }}>
                      <Chip size="small" label={`Due in range ${followUp?.dueInRange || 0}`} color="warning" sx={DASHBOARD_CHIP_SX} />
                      <Chip size="small" label={`Overdue ${followUp?.overdue || 0}`} color="error" sx={DASHBOARD_CHIP_SX} />
                      <Chip size="small" label={`Next 7 days ${followUp?.upcomingNext7Days || 0}`} color="info" sx={DASHBOARD_CHIP_SX} />
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

            </Grid>
          </Grid>
          {!showOperational && !showBilling ? <Alert severity="info">No report sections are available for your current access level.</Alert> : null}
        </Stack>
      )}
    </Stack>
  );
}
