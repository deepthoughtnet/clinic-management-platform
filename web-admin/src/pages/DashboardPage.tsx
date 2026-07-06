import * as React from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
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
  Collapse,
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
import TenantOnboardingWizardDialog from "../components/onboarding/TenantOnboardingWizardDialog";
import { AppointmentTokenChip, PatientJourneyTracker, WorkflowStatusBadge } from "../components/workflow/WorkflowUx";
import {
  getClinicProfile,
  getClinicDashboard,
  getClinicUsers,
  getTenantOnboardingStatus,
  getPlatformPlans,
  getPlatformTenants,
  type ClinicDashboard,
  type ClinicProfile,
  type ClinicUser,
  type TenantOnboardingStatus,
} from "../api/clinicApi";
import { formatRelativeBookingTime, getNextWorkflowAction } from "../components/workflow/workflowHelpers";

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

function displayNameForUser(user: ClinicUser | undefined, fallback: string) {
  if (!user) return fallback;
  return user.displayName || user.email || fallback;
}

type SetupGuidePreference = {
  hidden: boolean;
  collapsed: boolean;
};

function setupGuidePreferenceKey(tenantId: string | null | undefined, userId: string | null | undefined) {
  if (!tenantId || !userId) return null;
  return `dashboard.setup-guide.${tenantId}.${userId}`;
}

function readSetupGuidePreference(key: string | null, fallback: SetupGuidePreference): SetupGuidePreference {
  if (!key || typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<SetupGuidePreference>;
    return {
      hidden: Boolean(parsed.hidden),
      collapsed: typeof parsed.collapsed === "boolean" ? parsed.collapsed : fallback.collapsed,
    };
  } catch {
    return fallback;
  }
}

function saveSetupGuidePreference(key: string | null, preference: SetupGuidePreference) {
  if (!key || typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, JSON.stringify(preference));
  } catch {
    // Ignore localStorage failures and keep the dashboard usable.
  }
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
  { label: "Appointment Booked" },
  { label: "Registration" },
  { label: "Payment" },
  { label: "Check-in" },
  { label: "Waiting" },
  { label: "Consultation" },
  { label: "Prescription" },
  { label: "Laboratory" },
  { label: "Pharmacy" },
  { label: "Billing Complete" },
  { label: "Visit Completed" },
] as const;

type WaitingPatientFilter = "ALL" | "WAITING_FOR_DOCTOR" | "IN_CONSULTATION" | "CHECKED_IN" | "PAYMENT_PENDING" | "NO_SHOW_CANCELLED";

const WAITING_PATIENT_FILTERS: Array<{ value: WaitingPatientFilter; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "WAITING_FOR_DOCTOR", label: "Waiting for Doctor" },
  { value: "IN_CONSULTATION", label: "In Consultation" },
  { value: "CHECKED_IN", label: "Checked-in" },
  { value: "PAYMENT_PENDING", label: "Payment Pending" },
  { value: "NO_SHOW_CANCELLED", label: "No-show / Cancelled" },
];

const COMPACT_WAITING_JOURNEY_STEPS = ["Appointment", "Payment", "Check-in", "Waiting", "Consultation"] as const;

function normalizeSearchValue(value: string | null | undefined) {
  return String(value || "").trim().toLowerCase();
}

function isPaymentPendingStatus(status: string | null | undefined) {
  const normalized = normalizeSearchValue(status).toUpperCase();
  return normalized === "AWAITING_PAYMENT" || normalized === "PAYMENT_PENDING" || normalized === "UNPAID";
}

function waitingPatientState(item: ClinicDashboard["currentWaitingList"][number]): WaitingPatientFilter {
  const status = normalizeSearchValue(item.status).toUpperCase();
  if (status === "IN_CONSULTATION") return "IN_CONSULTATION";
  if (status === "WAITING") return "WAITING_FOR_DOCTOR";
  if (status === "NO_SHOW" || status === "CANCELLED") return "NO_SHOW_CANCELLED";
  if (status === "BOOKED") {
    return isPaymentPendingStatus(item.consultationFeeStatus) ? "PAYMENT_PENDING" : "CHECKED_IN";
  }
  if (isPaymentPendingStatus(item.consultationFeeStatus)) return "PAYMENT_PENDING";
  return "CHECKED_IN";
}

function waitingPatientStatusLabel(state: WaitingPatientFilter) {
  switch (state) {
    case "WAITING_FOR_DOCTOR":
      return "Waiting for Doctor";
    case "IN_CONSULTATION":
      return "In Consultation";
    case "CHECKED_IN":
      return "Checked-in";
    case "PAYMENT_PENDING":
      return "Payment Pending";
    case "NO_SHOW_CANCELLED":
      return "No-show / Cancelled";
    default:
      return "All";
  }
}

function waitingPatientStatusTone(state: WaitingPatientFilter) {
  switch (state) {
    case "WAITING_FOR_DOCTOR":
      return "warning";
    case "IN_CONSULTATION":
      return "secondary";
    case "CHECKED_IN":
      return "info";
    case "PAYMENT_PENDING":
      return "warning";
    case "NO_SHOW_CANCELLED":
      return "default";
    default:
      return "default";
  }
}

function waitingPatientSearchText(item: ClinicDashboard["currentWaitingList"][number]) {
  return [
    item.patientName,
    item.patientMobile,
    item.patientNumber,
    item.tokenNumber != null ? `token ${item.tokenNumber}` : null,
    item.tokenNumber != null ? String(item.tokenNumber) : null,
    item.doctorName,
    item.doctorUserId,
    item.status,
  ]
    .filter(Boolean)
    .map((value) => normalizeSearchValue(String(value)))
    .join(" ");
}

function waitingPatientNextAction(item: ClinicDashboard["currentWaitingList"][number]) {
  return getNextWorkflowAction({
    status: item.status,
    paymentStatus: item.consultationFeeStatus,
    feeStatus: item.consultationFeeStatus,
  });
}

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
  const canSeeFinancialDashboard = isBillingUser || isClinicAdmin || isAuditor || isPlatformAdmin;
  const canBilling = canSeeFinancialDashboard;
  const canUseAppointmentShortcuts = !isBillingUser && !isAuditor && (auth.hasPermission("appointment.manage") || auth.hasPermission("appointment.read"));
  const canCreateAppointments = !isDoctor && !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");
  const canOpenDayBoard = !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");
  const canOpenQueue = !isBillingUser && !isAuditor && auth.hasPermission("appointment.manage");

  const [dashboard, setDashboard] = React.useState<ClinicDashboard | null>(null);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [onboardingStatus, setOnboardingStatus] = React.useState<TenantOnboardingStatus | null>(null);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [waitingPatientFilter, setWaitingPatientFilter] = React.useState<WaitingPatientFilter>("ALL");
  const [waitingPatientSearch, setWaitingPatientSearch] = React.useState("");
  const [waitingPatientDoctorUserId, setWaitingPatientDoctorUserId] = React.useState("");
  const [expandedJourneyAppointmentId, setExpandedJourneyAppointmentId] = React.useState<string | null>(null);
  const [preset, setPreset] = React.useState<DatePreset>("TODAY");
  const initialRange = dateRangeForPreset("TODAY");
  const [startDate, setStartDate] = React.useState(initialRange.startDate);
  const [endDate, setEndDate] = React.useState(initialRange.endDate);
  const [dateFieldErrors, setDateFieldErrors] = React.useState<Record<string, string>>({});
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [setupWizardOpen, setSetupWizardOpen] = React.useState(false);
  const [setupWizardDismissed, setSetupWizardDismissed] = React.useState(false);
  const [setupHideConfirmOpen, setSetupHideConfirmOpen] = React.useState(false);
  const setupPreferenceKey = React.useMemo(() => setupGuidePreferenceKey(auth.tenantId, auth.appUserId || auth.username || null), [auth.appUserId, auth.username, auth.tenantId]);

  const [platformTenants, setPlatformTenants] = React.useState(0);
  const [platformActiveTenants, setPlatformActiveTenants] = React.useState(0);
  const [platformPlans, setPlatformPlans] = React.useState(0);
  const location = useLocation();
  const setupRequested = React.useMemo(() => new URLSearchParams(location.search).get("setup") === "1", [location.search]);

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
      const [data, onboarding, profile] = await Promise.all([
        getClinicDashboard(auth.accessToken, auth.tenantId, {
          startDate,
          endDate,
          doctorUserId: isDoctor ? undefined : (doctorUserId || undefined),
        }),
        getTenantOnboardingStatus(auth.accessToken, auth.tenantId),
        getClinicProfile(auth.accessToken, auth.tenantId).catch(() => null),
      ]);
      setDashboard(data);
      setOnboardingStatus(onboarding);
      setClinicProfile(profile);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load clinic dashboard");
      setDashboard(null);
      setOnboardingStatus(null);
      setClinicProfile(null);
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, startDate, endDate, doctorUserId, isDoctor]);

  React.useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  React.useEffect(() => {
    if (!auth.tenantId || !onboardingStatus || setupWizardDismissed) return;
    if (isClinicAdmin && (!onboardingStatus.completed || setupRequested)) {
      setSetupWizardOpen(true);
    }
    if (!isClinicAdmin) {
      setSetupWizardOpen(false);
    }
  }, [auth.tenantId, isClinicAdmin, onboardingStatus, setupRequested, setupWizardDismissed]);

  const closeSetupWizard = React.useCallback(() => {
    setSetupWizardOpen(false);
    setSetupWizardDismissed(true);
    if (setupRequested) {
      navigate("/dashboard", { replace: true });
    }
  }, [navigate, setupRequested]);

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
  const filteredWaitingPatients = React.useMemo(() => {
    const search = normalizeSearchValue(waitingPatientSearch);
    const localDoctorId = doctorUserId || waitingPatientDoctorUserId;
    return (dashboard?.currentWaitingList || []).filter((item) => {
      if (waitingPatientFilter !== "ALL" && waitingPatientState(item) !== waitingPatientFilter) {
        return false;
      }
      if (localDoctorId && item.doctorUserId !== localDoctorId) {
        return false;
      }
      if (search && !waitingPatientSearchText(item).includes(search)) {
        return false;
      }
      return true;
    });
  }, [dashboard?.currentWaitingList, doctorUserId, waitingPatientDoctorUserId, waitingPatientFilter, waitingPatientSearch]);
  const waitingPatientDoctorFilterEnabled = !doctorUserId && doctorOptions.length > 0;
  const waitingPatientListMaxHeight = React.useMemo(() => {
    if (filteredWaitingPatients.length <= 2) {
      return undefined;
    }
    const visibleCards = waitingPatientFilter === "ALL" ? 4 : 3;
    const estimatedCardHeight = 226;
    const gapHeight = 12;
    return visibleCards * estimatedCardHeight + Math.max(0, visibleCards - 1) * gapHeight;
  }, [filteredWaitingPatients.length, waitingPatientFilter]);
  const showOperational = Boolean(appt || queue || consult || rx || followUp);
  const showBilling = Boolean(billing) && canSeeFinancialDashboard;
  const isZeroDashboard = Boolean(dashboard)
    && (appt?.totalToday || 0) === 0
    && (queue?.waiting || 0) === 0
    && (appt?.checkedIn || 0) === 0
    && (queue?.inConsultation || 0) === 0
    && (consult?.completed || 0) === 0
    && (billing?.pendingBills || 0) === 0
    && (billing?.totalPaid || 0) === 0
    && (rx?.prescriptionsGenerated || 0) === 0;
  const showSetupGuide = Boolean(!loading && isClinicAdmin && onboardingStatus && (!onboardingStatus.completed || setupRequested || isZeroDashboard));
  const setupChecklist = React.useMemo(() => [
    { label: "Clinic basics configured", done: Boolean(clinicProfile?.clinicName && clinicProfile?.addressLine1), helper: clinicProfile?.clinicName || "Add clinic profile and contact details." },
    { label: "Doctors added", done: users.some((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR"), helper: "Add at least one doctor user." },
    { label: "Reception users added", done: users.some((user) => (user.membershipRole || "").toUpperCase() === "RECEPTIONIST"), helper: "Add front-desk users for operations." },
    { label: "Services / billing ready", done: Boolean(clinicProfile?.registrationNumber), helper: clinicProfile?.registrationNumber || "Review fees and receipt settings." },
    { label: "Pharmacy enabled", done: Boolean(auth.enabledTenantModules?.INVENTORY || auth.enabledTenantModules?.PRESCRIPTION), helper: "Enable pharmacy when the clinic dispenses medicines." },
    { label: "Laboratory enabled", done: Boolean(auth.enabledTenantModules?.LABORATORY), helper: "Enable laboratory when the clinic runs lab workflows." },
    { label: "First appointment booked", done: (appt?.totalToday || 0) > 0, helper: "Book the first appointment to begin the clinic journey." },
  ], [appt?.totalToday, auth.enabledTenantModules?.INVENTORY, auth.enabledTenantModules?.LABORATORY, auth.enabledTenantModules?.PRESCRIPTION, clinicProfile?.addressLine1, clinicProfile?.clinicName, clinicProfile?.registrationNumber, users]);
  const setupProgress = React.useMemo(() => {
    const total = setupChecklist.length;
    const completed = setupChecklist.filter((item) => item.done).length;
    return {
      total,
      completed,
      percent: total > 0 ? Math.round((completed / total) * 100) : 0,
      complete: total > 0 && completed === total,
      partiallyComplete: completed > 0 && completed < total,
      pendingSummary: setupChecklist.filter((item) => !item.done).slice(0, 2).map((item) => item.label).join(" · "),
    };
  }, [setupChecklist]);
  const setupDefaultCollapsed = setupProgress.complete || setupProgress.partiallyComplete;
  const [setupGuidePreference, setSetupGuidePreference] = React.useState<SetupGuidePreference>(() => readSetupGuidePreference(setupPreferenceKey, { hidden: false, collapsed: setupDefaultCollapsed }));
  React.useEffect(() => {
    const storedPreference = readSetupGuidePreference(setupPreferenceKey, { hidden: false, collapsed: setupDefaultCollapsed });
    setSetupGuidePreference(setupProgress.complete
      ? { hidden: storedPreference.hidden, collapsed: true }
      : storedPreference);
  }, [setupDefaultCollapsed, setupPreferenceKey, setupProgress.complete]);
  React.useEffect(() => {
    if (!setupPreferenceKey) return;
    saveSetupGuidePreference(setupPreferenceKey, {
      hidden: setupGuidePreference.hidden,
      collapsed: setupProgress.complete ? true : (setupGuidePreference.hidden ? true : setupGuidePreference.collapsed),
    });
  }, [setupGuidePreference.collapsed, setupGuidePreference.hidden, setupPreferenceKey, setupProgress.complete]);
  const setupGuideCollapsed = setupGuidePreference.hidden ? true : setupGuidePreference.collapsed;
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

  const operationalCards = dashboard ? [
    { label: "Appointments", value: appt?.totalToday || 0, tone: "primary" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Checked-in", value: appt?.checkedIn || 0, tone: "warning" as const, onClick: () => openDashboardSection("queue") },
    { label: "Waiting Queue", value: queue?.waiting || 0, tone: "warning" as const, onClick: () => openDashboardSection("queue") },
    { label: "In Consultation", value: appt?.inConsultation || 0, tone: "info" as const, onClick: () => openDashboardSection("consultations") },
    { label: "Completed", value: consult?.completed || 0, tone: "success" as const, onClick: () => openDashboardSection("consultations") },
    { label: "No-shows", value: appt?.noShow || 0, tone: "error" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Cancelled", value: appt?.cancelled || 0, tone: "error" as const, onClick: () => openDashboardSection("appointments") },
    { label: "Prescriptions", value: rx?.prescriptionsGenerated || 0, tone: "info" as const, onClick: () => openDashboardSection("prescriptions") },
  ] : [];

  const financialCards = dashboard ? [
    { label: "Bills Created", value: billing?.billsCreated || 0, tone: "info" as const, onClick: () => openDashboardSection("billing") },
    { label: "Payments Received", value: formatMoney(billing?.totalPaid), tone: "success" as const, onClick: () => openDashboardSection("billing") },
    { label: "Pending Amount", value: formatMoney(billing?.pendingAmount), tone: "warning" as const, onClick: () => openDashboardSection("billing") },
    { label: "Pending Invoices", value: billing?.pendingBills || 0, tone: "error" as const, onClick: () => openDashboardSection("billing") },
  ] : [];

  const setupActions = [
    { label: "Add Doctor", description: "Create the first doctor user.", action: () => navigate("/settings/users-roles"), tone: "primary" as const },
    { label: "Add Receptionist", description: "Create front-desk access for appointments and queues.", action: () => navigate("/settings/users-roles"), tone: "secondary" as const },
    { label: "Configure Availability", description: "Set working hours and breaks.", action: () => navigate("/doctors/availability"), tone: "info" as const },
    { label: "Add Services / Fees", description: "Review clinic profile and billing settings.", action: () => navigate("/settings/clinic-profile"), tone: "info" as const },
    { label: "Import Medicines", description: "Open the medicine master workspace.", action: () => navigate("/pharmacy/medicines"), tone: "success" as const },
    { label: "Import Lab Tests", description: "Open the laboratory workspace.", action: () => navigate("/lab"), tone: "success" as const },
    { label: "Book First Appointment", description: "Create the first appointment booking.", action: () => navigate("/appointments"), tone: "primary" as const },
  ];
  const roleEmptyStateActions = isDoctor
    ? [
        { label: "Open Day Board", action: () => navigate("/appointments/day-board") },
        { label: "Open Queue", action: () => navigate("/queue") },
        { label: "View Consultations", action: () => navigate("/consultations") },
      ]
    : isReceptionist
      ? [
          { label: "Book Appointment", action: () => navigate("/appointments") },
          { label: "Open Queue", action: () => navigate("/queue") },
          { label: "View Patients", action: () => navigate("/patients") },
        ]
      : [
          { label: "Open Appointments", action: () => navigate("/appointments") },
          { label: "Open Day Board", action: () => navigate("/appointments/day-board") },
          { label: "Open Queue", action: () => navigate("/queue") },
        ];

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

      {isClinicAdmin ? (
        showSetupGuide && setupGuidePreference.hidden ? (
          <Card variant="outlined">
            <CardContent sx={{ py: 1, "&:last-child": { pb: 1 } }}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }} justifyContent="space-between">
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinic setup</Typography>
                    <Chip size="small" color={setupProgress.complete ? "success" : "primary"} label={setupProgress.complete ? "Setup complete" : `${setupProgress.percent}% complete`} />
                    <Chip size="small" variant="outlined" label={`${setupProgress.completed}/${setupProgress.total} steps`} />
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    {setupProgress.complete ? "Setup complete. You can reopen the guide anytime." : setupProgress.pendingSummary || "Use the setup guide to complete your clinic configuration."}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={() => setSetupGuidePreference({ hidden: false, collapsed: setupDefaultCollapsed })}>Show setup guide</Button>
                  <Button size="small" variant="text" onClick={() => setSetupWizardOpen(true)}>Setup wizard</Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ) : showSetupGuide ? (
          <Card variant="outlined">
            <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
              <Stack spacing={1.15}>
                <Box
                  role="button"
                  tabIndex={0}
                  onClick={() => setSetupGuidePreference((current) => ({ ...current, collapsed: !current.collapsed }))}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setSetupGuidePreference((current) => ({ ...current, collapsed: !current.collapsed }));
                    }
                  }}
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    gap: 1.25,
                    alignItems: "flex-start",
                    cursor: "pointer",
                    borderRadius: 1.25,
                    px: 0.25,
                    py: 0.1,
                    "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
                  }}
                >
                  <Box>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.1 }}>Clinic setup</Typography>
                      <Chip size="small" color={setupProgress.complete ? "success" : "primary"} label={setupProgress.complete ? "Setup complete" : `${setupProgress.percent}% complete`} />
                      <Chip size="small" variant="outlined" label={`${setupProgress.completed}/${setupProgress.total} steps`} />
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      {setupProgress.complete ? "Setup complete. Keep this compact card available from Dashboard → Setup Guide." : setupProgress.pendingSummary || "Complete the remaining clinic setup steps."}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={0.75} alignItems="center">
                    <Button size="small" variant="outlined" onClick={(event) => { event.stopPropagation(); setSetupGuidePreference((current) => ({ ...current, collapsed: !current.collapsed })); }}>
                      {setupGuideCollapsed ? "Expand" : "Collapse"}
                    </Button>
                    <Button size="small" variant="text" color="inherit" onClick={(event) => { event.stopPropagation(); setSetupHideConfirmOpen(true); }}>
                      Hide setup guide
                    </Button>
                  </Stack>
                </Box>

                <Collapse in={!setupGuideCollapsed} timeout={180}>
                  <Stack spacing={1.35}>
                    <Grid container spacing={1.25}>
                      {setupActions.map((action) => (
                        <Grid key={action.label} size={{ xs: 12, sm: 6, lg: 4 }}>
                          <Card
                            variant="outlined"
                            sx={{
                              minHeight: 112,
                              cursor: "pointer",
                              transition: "transform 0.2s ease, box-shadow 0.2s ease",
                              borderColor: action.tone === "primary" ? "primary.main" : action.tone === "success" ? "success.main" : action.tone === "info" ? "info.main" : "divider",
                              "&:hover": { transform: "translateY(-2px)", boxShadow: 2 },
                            }}
                            role="button"
                            tabIndex={0}
                            onClick={action.action}
                            onKeyDown={(event) => {
                              if (event.key === "Enter" || event.key === " ") {
                                event.preventDefault();
                                action.action();
                              }
                            }}
                          >
                            <CardContent>
                              <Stack spacing={0.75}>
                                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{action.label}</Typography>
                                <Typography variant="caption" color="text.secondary">{action.description}</Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ))}
                    </Grid>
                    <Divider />
                    <Grid container spacing={1.25}>
                      {setupChecklist.map((item) => (
                        <Grid key={item.label} size={{ xs: 12, sm: 6, lg: 4 }}>
                          <Card variant="outlined" sx={{ borderColor: item.done ? "success.main" : "divider" }}>
                            <CardContent sx={{ py: 1.1, "&:last-child": { pb: 1.1 } }}>
                              <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="center">
                                <Box>
                                  <Typography variant="body2" sx={{ fontWeight: 800 }}>{item.label}</Typography>
                                  <Typography variant="caption" color="text.secondary">{item.helper}</Typography>
                                </Box>
                                <Chip size="small" color={item.done ? "success" : "default"} label={item.done ? "Done" : "Pending"} />
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ))}
                    </Grid>
                    {onboardingStatus && !onboardingStatus.completed ? (
                      <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => setSetupWizardOpen(true)}>Resume setup</Button>}>
                        Setup is incomplete. You can continue the wizard now or later from Settings.
                      </Alert>
                    ) : null}
                  </Stack>
                </Collapse>
              </Stack>
            </CardContent>
          </Card>
        ) : setupProgress.complete ? (
          <Card variant="outlined">
            <CardContent sx={{ py: 1, "&:last-child": { pb: 1 } }}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }} justifyContent="space-between">
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Clinic setup</Typography>
                    <Chip size="small" color="success" label="Setup complete" />
                    <Chip size="small" variant="outlined" label={`${setupProgress.completed}/${setupProgress.total} steps`} />
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    All required setup steps are complete.
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={() => setSetupGuidePreference((current) => ({ ...current, hidden: false, collapsed: true }))}>Show setup guide</Button>
                  <Button size="small" variant="text" onClick={() => setSetupWizardOpen(true)}>Setup wizard</Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ) : null
      ) : null}

      {dashboard && isZeroDashboard && !isClinicAdmin ? (
        <Card variant="outlined">
          <CardContent sx={{ p: 1.75, "&:last-child": { pb: 1.75 } }}>
            <Stack spacing={1.25}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>
                  {isDoctor ? "Consultation workspace is ready." : isReceptionist ? "Operational dashboard is empty." : "No activity yet."}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {isDoctor
                    ? "Check the day board and queue to begin consultations."
                    : isReceptionist
                      ? "Start with appointments, queue management, and patient registration."
                      : "Use the available actions to begin operations."}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {roleEmptyStateActions.map((action) => (
                  <Button key={action.label} variant="outlined" size="small" onClick={action.action}>
                    {action.label}
                  </Button>
                ))}
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={setupHideConfirmOpen} onClose={() => setSetupHideConfirmOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Hide setup guide?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            This only hides the setup guide for your dashboard. It does not mark onboarding complete.
          </Typography>
          <Typography variant="body2" sx={{ mt: 1 }}>
            You can reopen this from Dashboard → Setup Guide.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSetupHideConfirmOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              setSetupGuidePreference((current) => ({ ...current, hidden: true, collapsed: true }));
              setSetupHideConfirmOpen(false);
            }}
          >
            Hide setup guide
          </Button>
        </DialogActions>
      </Dialog>

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
        <Alert severity="info">{isClinicAdmin ? "No dashboard data available yet. Use the setup actions above to start operations." : "No dashboard data available for the selected filters."}</Alert>
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
            {((showBilling && (!showOperational || !canUseAppointmentShortcuts)) ? financialCards : operationalCards).map((card) => (
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
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.25, flexWrap: "wrap", alignItems: "flex-start", mb: 1 }}>
                      <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>Current waiting patients</Typography>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
                          {WAITING_PATIENT_FILTERS.map((filter) => (
                            <Chip
                              key={filter.value}
                              clickable
                              size="small"
                              label={filter.label}
                              color={waitingPatientFilter === filter.value ? "primary" : "default"}
                              variant={waitingPatientFilter === filter.value ? "filled" : "outlined"}
                              onClick={() => setWaitingPatientFilter(filter.value)}
                              sx={DASHBOARD_CHIP_SX}
                            />
                          ))}
                          {waitingPatientDoctorFilterEnabled ? (
                            <FormControl size="small" sx={{ minWidth: 180 }}>
                              <InputLabel id="waiting-patient-doctor-label">Doctor</InputLabel>
                              <Select
                                labelId="waiting-patient-doctor-label"
                                label="Doctor"
                                value={waitingPatientDoctorUserId}
                                onChange={(event) => setWaitingPatientDoctorUserId(String(event.target.value))}
                              >
                                <MenuItem value="">All Doctors</MenuItem>
                                {doctorOptions.map((doctor) => (
                                  <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                                    {doctor.displayName || doctor.email || doctor.appUserId}
                                  </MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                          ) : (
                            <Chip
                              size="small"
                              label={`Doctor: ${selectedDoctorLabel}`}
                              color={doctorUserId ? "primary" : "default"}
                              variant={doctorUserId ? "filled" : "outlined"}
                              sx={DASHBOARD_CHIP_SX}
                            />
                          )}
                        </Stack>
                      </Box>
                      <TextField
                        size="small"
                        value={waitingPatientSearch}
                        onChange={(event) => setWaitingPatientSearch(event.target.value)}
                        placeholder="Search name, mobile, token, patient number"
                        label="Search"
                        sx={{ minWidth: { xs: "100%", sm: 320, lg: 360 }, flex: "0 1 auto" }}
                        InputProps={{ sx: { borderRadius: 999 } }}
                      />
                    </Box>

                    {filteredWaitingPatients.length === 0 ? (
                      <Alert severity="info">No patients match the selected filter or search.</Alert>
                    ) : (
                      <Box
                        sx={{
                          maxHeight: waitingPatientListMaxHeight ? `${waitingPatientListMaxHeight}px` : "none",
                          overflowY: waitingPatientListMaxHeight ? "auto" : "visible",
                          overflowX: "hidden",
                          pr: 0.75,
                          scrollbarWidth: "thin",
                          display: "flex",
                          flexDirection: "column",
                          gap: 1,
                          minHeight: 0,
                        }}
                      >
                        {filteredWaitingPatients.map((item) => {
                          const statusGroup = waitingPatientState(item);
                          const nextAction = waitingPatientNextAction(item);
                          const compactJourneyCurrent = waitingPatientStatusLabel(statusGroup);
                          const compactJourneyText = COMPACT_WAITING_JOURNEY_STEPS.join(" \u2192 ");
                          const isExpanded = expandedJourneyAppointmentId === item.appointmentId;
                          return (
                            <Box
                              key={item.appointmentId}
                            sx={{
                              border: "1px solid",
                              borderColor: "divider",
                              borderRadius: 1.5,
                              p: 1.1,
                              bgcolor: "background.paper",
                              overflow: "visible",
                            }}
                          >
                            <Stack spacing={0.9}>
                                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "flex-start" }}>
                                  <Box sx={{ minWidth: 0, flex: 1 }}>
                                    <Typography variant="body1" sx={{ fontWeight: 800, lineHeight: 1.2, overflowWrap: "anywhere" }}>
                                      {item.patientName || item.patientNumber || item.patientId}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.25 }}>
                                      {item.patientMobile ? `Mobile: ${item.patientMobile}` : "Mobile: -"}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end" sx={{ maxWidth: "100%" }}>
                                    <AppointmentTokenChip appointment={item} compact />
                                    <WorkflowStatusBadge status={statusGroup} label={waitingPatientStatusLabel(statusGroup)} tone={waitingPatientStatusTone(statusGroup)} compact />
                                  </Stack>
                                </Box>

                                <Box
                                  sx={{
                                    display: "grid",
                                    gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" },
                                    gap: 0.75,
                                  }}
                                >
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", fontWeight: 700 }}>
                                      Doctor
                                    </Typography>
                                    <Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: "anywhere" }}>
                                      {item.doctorName || item.doctorUserId || "Unassigned"}
                                    </Typography>
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", fontWeight: 700 }}>
                                      Appointment time
                                    </Typography>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      {formatTime(item.appointmentTime)}
                                    </Typography>
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", fontWeight: 700 }}>
                                      Waiting since
                                    </Typography>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      {(item.waitingSince ? formatRelativeBookingTime(item.waitingSince)?.replace(/^Booked /, "Waiting since ") : null) || "Booked recently"}
                                    </Typography>
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", fontWeight: 700 }}>
                                      Patient number
                                    </Typography>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      {item.patientNumber || "Not assigned"}
                                    </Typography>
                                  </Box>
                                </Box>

                                <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap alignItems="center" sx={{ minWidth: 0 }}>
                                  <Chip size="small" variant="outlined" label={`Token: ${item.tokenNumber != null ? item.tokenNumber : "Not assigned"}`} sx={{ height: 22 }} />
                                  <Chip size="small" variant="outlined" label={`Next: ${nextAction.label}`} color={nextAction.tone} sx={{ height: 22 }} />
                                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                                    {compactJourneyText}
                                  </Typography>
                                </Stack>

                                <Stack direction={{ xs: "column", sm: "row" }} spacing={0.75} justifyContent="space-between" alignItems={{ sm: "center" }}>
                                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, lineHeight: 1.25 }}>
                                    Current: {compactJourneyCurrent}
                                  </Typography>
                                  <Stack direction="row" spacing={0.75} flexWrap="wrap" justifyContent="flex-end">
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => setExpandedJourneyAppointmentId((current) => (current === item.appointmentId ? null : item.appointmentId))}
                                    >
                                      {isExpanded ? "Hide full journey" : "View full journey"}
                                    </Button>
                                    <Button size="small" variant="outlined" onClick={() => navigate(`/patients/${item.patientId}`)}>
                                      Open Patient
                                    </Button>
                                  </Stack>
                                </Stack>

                                <Collapse in={isExpanded} timeout="auto" unmountOnExit sx={{ overflow: "visible" }}>
                                  <Box sx={{ pt: 0.5, overflow: "visible" }}>
                                    <PatientJourneyTracker context={{ status: item.status, paymentStatus: item.consultationFeeStatus }} compact={false} title="Patient Journey" />
                                  </Box>
                                </Collapse>
                              </Stack>
                            </Box>
                          );
                        })}
                      </Box>
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

      {isClinicAdmin ? (
        <TenantOnboardingWizardDialog
          open={setupWizardOpen}
          auth={auth}
          onClose={closeSetupWizard}
          onCompleted={() => void loadDashboard()}
        />
      ) : null}
    </Stack>
  );
}
