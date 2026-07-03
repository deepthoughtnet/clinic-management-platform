import * as React from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  appointmentCreateSchema,
  appointmentRescheduleSchema,
  normalizeIndianMobileInput,
} from "@deepthoughtnet/form-validation-kit";
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
  FormControlLabel,
  Grid,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Switch,
  Tooltip,
} from "@mui/material";
import { alpha } from "@mui/material/styles";

import { useAuth } from "../../auth/useAuth";
import PatientQuickRegisterDialog, { patientSummary } from "../../components/patients/PatientQuickRegisterDialog";
import {
  createAppointment,
  createWaitlist,
  createWalkInAppointment,
  getClinicClock,
  getDoctorSlots,
  getWaitlist,
  getClinicUsers,
  getPatient,
  rescheduleAppointment,
  searchAppointments,
  searchPatients,
  updateAppointmentStatus,
  updateWaitlistStatus,
  type Appointment,
  type AppointmentWaitlist,
  type DoctorAvailabilitySlot,
  type AppointmentPriority,
  type AppointmentType,
  type ClinicUser,
  type Patient,
  type PatientGender,
  type WaitlistStatus,
} from "../../api/clinicApi";
import {
  formatClinicClockLabel,
  getClinicDateKey,
  isBookingTimePast,
  findSlotForTime,
} from "./bookingValidation";
import { getAppointmentSlotPresentation } from "./slotState";
import { CompactEmptyState, CompactStatCard, CompactTableFrame, compactChipSx } from "../../components/compact/CompactUi";

type AppointmentTab = "today" | "upcoming" | "waitlist" | "completed" | "archive";

type AppointmentPageState = {
  patient?: Patient;
};

const appointmentTypes: AppointmentType[] = ["SCHEDULED", "FOLLOW_UP", "VACCINATION", "WALK_IN"];
const appointmentPriorities: AppointmentPriority[] = ["NORMAL", "URGENT", "ELDERLY", "CHILD", "FOLLOW_UP", "MANUAL_PRIORITY"];

function statusColor(status: Appointment["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_CONSULTATION":
      return "info";
    case "WAITING":
    case "BOOKED":
      return "warning";
    case "CANCELLED":
    case "NO_SHOW":
      return "default";
  }
}

function priorityColor(priority: Appointment["priority"]) {
  switch (priority) {
    case "URGENT":
    case "MANUAL_PRIORITY":
      return "error";
    case "ELDERLY":
    case "CHILD":
      return "secondary";
    case "FOLLOW_UP":
      return "info";
    default:
      return "default";
  }
}

function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function patientLabel(patient: Patient | null) {
  if (!patient) return "";
  const age = patient.ageYears !== null ? `${patient.ageYears}y` : null;
  const label = `${patient.firstName} ${patient.lastName || ""}`.trim();
  return [label, age, patient.gender].filter(Boolean).join(" • ");
}

function arrivalLabel(appointment: Appointment) {
  switch (appointment.status) {
    case "BOOKED":
      return "Booked";
    case "WAITING":
      return "Checked in";
    case "IN_CONSULTATION":
      return "In consultation";
    case "COMPLETED":
      return "Completed";
    case "CANCELLED":
      return "Cancelled";
    case "NO_SHOW":
      return "No show";
  }
}

function waitlistStatusLabel(status: WaitlistStatus) {
  switch (status) {
    case "WAITING":
      return "Waiting";
    case "CONTACTED":
      return "Contacted";
    case "BOOKED":
      return "Booked";
    case "CANCELLED":
      return "Cancelled";
  }
}

function formatDate(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString();
}

function appointmentReference(appointment: Appointment) {
  return appointment.displayReference || (appointment.tokenNumber != null ? `APT-${appointment.tokenNumber}` : "Pending");
}

function toFive(time: string | null | undefined) {
  if (!time) return "";
  return time.slice(0, 5);
}

function isPastDateTime(date: string, time: string | null | undefined, timeZone?: string | null, clinicNow?: string | null) {
  return isBookingTimePast(date, time, undefined, timeZone, clinicNow);
}

function shiftClinicNow(clinicNow: string | null, minutes: number) {
  if (!clinicNow) return null;
  const parsed = new Date(clinicNow);
  if (Number.isNaN(parsed.getTime())) return clinicNow;
  return new Date(parsed.getTime() + minutes * 60_000).toISOString();
}

function slotTone(state: ReturnType<typeof getAppointmentSlotPresentation>["state"]) {
  switch (state) {
    case "AVAILABLE":
    case "CURRENT":
    case "PARTIALLY_BOOKED":
      return "success";
    case "FULL":
      return "error";
    case "PAST":
    case "LEAVE":
    case "HOLIDAY":
    case "UNAVAILABLE":
    default:
      return "inherit";
  }
}

function slotChipTone(state: ReturnType<typeof getAppointmentSlotPresentation>["state"]) {
  const tone = slotTone(state);
  return tone === "inherit" ? "default" : tone;
}

function slotLabel(slot: DoctorAvailabilitySlot, date: string, timeZone?: string | null, clinicNow?: string | null) {
  const presentation = getAppointmentSlotPresentation(date, slot, timeZone, clinicNow);
  return `${toFive(slot.slotTime)} • ${presentation.state.toLowerCase().replace(/_/g, " ")} • ${slot.bookedCount}/${slot.maxPatientsPerSlot}`;
}


export default function AppointmentsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const statePatient = (location.state as AppointmentPageState | null)?.patient ?? null;
  const doctorUserIdFromQuery = searchParams.get("doctorUserId") || "";
  const appointmentDateFromQuery = searchParams.get("appointmentDate") || getClinicDateKey("Asia/Kolkata");
  const appointmentTimeFromQuery = searchParams.get("appointmentTime") || "";

  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [patientQuery, setPatientQuery] = React.useState(statePatient ? patientSummary(statePatient) : "");
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(statePatient);
  const [doctorUserId, setDoctorUserId] = React.useState(doctorUserIdFromQuery);
  const [appointmentDate, setAppointmentDate] = React.useState(appointmentDateFromQuery);
  const [appointmentTime, setAppointmentTime] = React.useState(appointmentTimeFromQuery);
  const [type, setType] = React.useState<AppointmentType>("SCHEDULED");
  const [priority, setPriority] = React.useState<AppointmentPriority>("NORMAL");
  const [reason, setReason] = React.useState("");
  const [tab, setTab] = React.useState<AppointmentTab>("today");
  const [listSearch, setListSearch] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [actionAppointmentId, setActionAppointmentId] = React.useState<string | null>(null);
  const [searchingPatients, setSearchingPatients] = React.useState(false);
  const [slots, setSlots] = React.useState<DoctorAvailabilitySlot[]>([]);
  const [quickRegisterOpen, setQuickRegisterOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [waitlist, setWaitlist] = React.useState<AppointmentWaitlist[]>([]);
  const [rescheduleOpen, setRescheduleOpen] = React.useState(false);
  const [rescheduleTarget, setRescheduleTarget] = React.useState<Appointment | null>(null);
  const [rescheduleDate, setRescheduleDate] = React.useState(() => getClinicDateKey("Asia/Kolkata"));
  const [rescheduleTime, setRescheduleTime] = React.useState("");
  const [rescheduleDoctorUserId, setRescheduleDoctorUserId] = React.useState("");
  const [emergencyBooking, setEmergencyBooking] = React.useState(false);
  const [adHocConfirmOpen, setAdHocConfirmOpen] = React.useState(false);
  const [adHocConfirmMessage, setAdHocConfirmMessage] = React.useState("");
  const [adHocConfirmPending, setAdHocConfirmPending] = React.useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = React.useState(false);
  const [cancelTarget, setCancelTarget] = React.useState<Appointment | null>(null);
  const [cancelComment, setCancelComment] = React.useState("");
  const [clinicTimeZone, setClinicTimeZone] = React.useState("Asia/Kolkata");
  const [clinicNowSnapshot, setClinicNowSnapshot] = React.useState<string | null>(null);
  const [clinicClockUnavailable, setClinicClockUnavailable] = React.useState(false);
  const [clockTick, setClockTick] = React.useState(0);
  const [hidePastSlots, setHidePastSlots] = React.useState(true);
  const [appointmentDateTouched, setAppointmentDateTouched] = React.useState(Boolean(searchParams.get("appointmentDate")));

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = auth.rolesUpper.includes("DOCTOR") || tenantRole === "DOCTOR";
  const canCreateAppointmentFlow = !isDoctor && auth.hasPermission("appointment.manage");
  const canQuickRegisterPatient = canCreateAppointmentFlow && auth.hasPermission("patient.create");
  const doctorOptions = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
  const doctorFilter = isDoctor && auth.appUserId ? auth.appUserId : undefined;
  const today = React.useMemo(() => getClinicDateKey(clinicTimeZone, clinicNowSnapshot), [clinicNowSnapshot, clinicTimeZone, clockTick]);
  const clinicClockLabel = React.useMemo(
    () => (clinicClockUnavailable ? "Clinic time unavailable" : formatClinicClockLabel(clinicTimeZone, clinicNowSnapshot)),
    [clinicClockUnavailable, clinicNowSnapshot, clinicTimeZone, clockTick],
  );

  React.useEffect(() => {
    if (import.meta.env.DEV && auth.tenantId) {
      console.debug("[appointments] clinic clock derived", {
        tenantId: auth.tenantId,
        clinicTimeZone,
        clinicNowSnapshot,
        today,
        clinicClockLabel,
      });
    }
  }, [auth.tenantId, clinicClockLabel, clinicNowSnapshot, clinicTimeZone, today]);

  const selectedDoctorId = doctorFilter || doctorUserId || "";
  const requiresAppointmentTime = type !== "WALK_IN";
  const matchingSlot = React.useMemo(
    () => findSlotForTime(appointmentDate, appointmentTime, slots),
    [appointmentDate, appointmentTime, slots],
  );
  const slotPresentations = React.useMemo(
    () => slots.map((slot) => ({
      slot,
      presentation: getAppointmentSlotPresentation(appointmentDate, slot, clinicTimeZone, clinicNowSnapshot),
    })),
    [appointmentDate, clinicNowSnapshot, clinicTimeZone, slots],
  );
  const bookableSlots = React.useMemo(
    () => slotPresentations.filter(({ presentation }) => presentation.counterEligible).map(({ slot }) => slot),
    [slotPresentations],
  );
  const hasStandardBookableSlots = bookableSlots.length > 0;
  const visibleSlots = React.useMemo(
    () => (hidePastSlots ? slotPresentations.filter(({ presentation }) => !presentation.isPast) : slotPresentations),
    [hidePastSlots, slotPresentations],
  );
  const currentSlot = React.useMemo(
    () => slotPresentations.find(({ presentation }) => presentation.isCurrent)?.slot || null,
    [slotPresentations],
  );
  const emergencyWarning = React.useMemo(
    () => (emergencyBooking && requiresAppointmentTime && appointmentTime && !matchingSlot && hasStandardBookableSlots)
      ? "Standard slots are available. Use emergency booking only when needed."
      : null,
    [appointmentTime, emergencyBooking, hasStandardBookableSlots, matchingSlot, requiresAppointmentTime],
  );
  const canCreateAppointment = Boolean(
    canCreateAppointmentFlow &&
    selectedPatient
    && doctorUserId
    && appointmentDate
    && (!requiresAppointmentTime || appointmentTime)
    && !saving,
  );

  const loadAppointments = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const effectiveDoctor = doctorFilter || doctorUserId || undefined;
      const rows = await searchAppointments(auth.accessToken, auth.tenantId, effectiveDoctor ? { doctorUserId: effectiveDoctor } : {});
      setAppointments(rows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load appointments");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, doctorFilter, doctorUserId]);

  const loadSlots = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedDoctorId || type === "WALK_IN") {
      setSlots([]);
      return;
    }
    try {
      const rows = await getDoctorSlots(auth.accessToken, auth.tenantId, selectedDoctorId, appointmentDate);
      setSlots(rows);
    } catch {
      setSlots([]);
    }
  }, [appointmentDate, auth.accessToken, auth.tenantId, selectedDoctorId, type]);
  const loadWaitlist = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      setWaitlist(await getWaitlist(auth.accessToken, auth.tenantId, { doctorUserId: selectedDoctorId || undefined, preferredDate: appointmentDate, status: "WAITING" }));
    } catch {
      setWaitlist([]);
    }
  }, [appointmentDate, auth.accessToken, auth.tenantId, selectedDoctorId]);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }

      try {
        const rows = await getClinicUsers(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setUsers(rows);
          if (!doctorFilter) {
            const firstDoctor = rows.find((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
            setDoctorUserId((current) => current || firstDoctor?.appUserId || "");
          }
        }
      } catch {
        if (!cancelled) {
          setError("Unable to load clinic users.");
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, doctorFilter]);

  React.useEffect(() => {
    void loadAppointments();
  }, [loadAppointments]);

  React.useEffect(() => {
    if (doctorFilter) {
      setDoctorUserId(doctorFilter);
    }
  }, [doctorFilter]);

  React.useEffect(() => {
    void loadSlots();
  }, [loadSlots]);
  React.useEffect(() => {
    void loadWaitlist();
  }, [loadWaitlist]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinicTimeZone() {
      if (!auth.accessToken || !auth.tenantId) {
        setClinicTimeZone("Asia/Kolkata");
        setClinicNowSnapshot(null);
        setClinicClockUnavailable(true);
        console.info("[appointments] clinic clock unavailable", {
          tenantId: auth.tenantId,
          source: "missing-auth",
          effectiveTimezone: "Asia/Kolkata",
          browserReportedTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        });
        return;
      }
      try {
        const settings = await getClinicClock(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          const effectiveTimezone = settings.clinicTimeZone?.trim() || "Asia/Kolkata";
          setClinicTimeZone(effectiveTimezone);
          setClinicNowSnapshot(settings.clinicNow || null);
          setClinicClockUnavailable(false);
          console.info("[appointments] clinic clock loaded", {
            tenantId: auth.tenantId,
            clinicTimeZone: settings.clinicTimeZone,
            clinicNow: settings.clinicNow,
            serverNowUtc: settings.serverNowUtc,
            effectiveTimezone,
            browserReportedTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          });
        }
      } catch (error) {
        if (!cancelled) {
          setClinicTimeZone("Asia/Kolkata");
          setClinicNowSnapshot(null);
          setClinicClockUnavailable(true);
          console.info("[appointments] clinic clock unavailable", {
            tenantId: auth.tenantId,
            source: "settings-error",
            effectiveTimezone: "Asia/Kolkata",
            error: error instanceof Error ? error.message : String(error),
            browserReportedTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          });
        }
      }
    }

    void loadClinicTimeZone();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    const handle = window.setInterval(() => setClockTick((value) => value + 1), 30_000);
    return () => window.clearInterval(handle);
  }, []);

  React.useEffect(() => {
    if (!appointmentDateTouched) {
      setAppointmentDate(today);
    }
  }, [appointmentDateTouched, today]);

  React.useEffect(() => {
    if (emergencyBooking && priority === "NORMAL") {
      setPriority("URGENT");
    }
  }, [emergencyBooking, priority]);

  React.useEffect(() => {
    let cancelled = false;
    async function hydrateHandoff() {
      if (!auth.accessToken || !auth.tenantId) return;
      if (statePatient) {
        setSelectedPatient((current) => current ?? statePatient);
        setPatientQuery((current) => current || patientSummary(statePatient));
        if (searchParams.get("type") === "WALK_IN") {
          setType("WALK_IN");
        }
        return;
      }

      if (selectedPatient) return;

      const patientId = searchParams.get("patientId");
      if (!patientId) return;

      try {
        const detail = await getPatient(auth.accessToken, auth.tenantId, patientId);
        if (!cancelled) {
          setSelectedPatient(detail.patient);
          setPatientQuery(patientSummary(detail.patient));
          if (searchParams.get("type") === "WALK_IN") {
            setType("WALK_IN");
          }
        }
      } catch {
        if (!cancelled) {
          setError("Patient registered. Select the patient again to continue.");
        }
      }
    }

    void hydrateHandoff();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, searchParams, selectedPatient, statePatient]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      const term = patientQuery.trim();
      if (!auth.accessToken || !auth.tenantId || term.length < 2) {
        setPatientResults([]);
        setSearchingPatients(false);
        return;
      }

      setSearchingPatients(true);
      try {
        if (isUuid(term)) {
          const detail = await getPatient(auth.accessToken, auth.tenantId, term);
          if (!cancelled) {
            setSelectedPatient(detail.patient);
            setPatientQuery(patientSummary(detail.patient));
            setPatientResults([]);
            setQuickRegisterOpen(false);
          }
          return;
        }

        const normalizedMobile = normalizeIndianMobileInput(term) as string;
        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^[6-9]\d{9}$/.test(normalizedMobile) ? normalizedMobile : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^[6-9]\d{9}$/.test(normalizedMobile) ? undefined : term,
          active: true,
        });
        if (!cancelled) {
          setPatientResults(rows);
          if (canQuickRegisterPatient && rows.length === 0 && /^[6-9]\d{9}$/.test(normalizedMobile)) {
            setQuickRegisterOpen(true);
          }
        }
      } catch {
        if (!cancelled) {
          setPatientResults([]);
        }
      } finally {
        if (!cancelled) {
          setSearchingPatients(false);
        }
      }
    }, 300);

    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, canQuickRegisterPatient, patientQuery]);

  const todayRows = React.useMemo(
    () => appointments.filter((item) => {
      if (item.status === "CANCELLED" || item.status === "NO_SHOW") {
        return false;
      }
      return item.appointmentDate === today && !isPastDateTime(item.appointmentDate, item.appointmentTime, clinicTimeZone, shiftClinicNow(clinicNowSnapshot, -60));
    }),
    [appointments, clinicNowSnapshot, clinicTimeZone, today],
  );
  const upcomingRows = React.useMemo(
    () => appointments.filter((item) => {
      if (item.status === "CANCELLED" || item.status === "NO_SHOW") {
        return false;
      }
      return !isPastDateTime(item.appointmentDate, item.appointmentTime, clinicTimeZone, clinicNowSnapshot);
    }),
    [appointments, clinicNowSnapshot, clinicTimeZone],
  );
  const completedRows = React.useMemo(
    () => appointments.filter((item) => item.status === "COMPLETED"),
    [appointments],
  );
  const archiveRows = React.useMemo(
    () => appointments.filter((item) => item.status === "CANCELLED" || item.status === "NO_SHOW"),
    [appointments],
  );

  const visibleAppointments = React.useMemo(() => {
    switch (tab) {
      case "today":
        return todayRows;
      case "upcoming":
        return upcomingRows;
      case "waitlist":
        return [];
      case "completed":
        return completedRows;
      case "archive":
        return archiveRows;
    }
  }, [archiveRows, completedRows, tab, todayRows, upcomingRows]);
  const filteredVisibleAppointments = React.useMemo(() => {
    const term = listSearch.trim().toLowerCase();
    if (!term) return visibleAppointments;
    return visibleAppointments.filter((appointment) => {
      const token = appointment.tokenNumber == null ? "" : String(appointment.tokenNumber);
      return [
        appointment.id,
        appointment.consultationId || "",
        token,
        appointment.patientName || "",
        appointment.patientNumber || "",
        appointment.patientMobile || "",
        appointment.doctorName || "",
        appointment.doctorUserId,
      ].some((value) => value.toLowerCase().includes(term));
    });
  }, [listSearch, visibleAppointments]);
  const filteredWaitlist = React.useMemo(() => {
    const term = listSearch.trim().toLowerCase();
    if (!term) return waitlist;
    return waitlist.filter((entry) => [
      entry.patientName || "",
      entry.patientNumber || "",
      entry.patientId,
      entry.doctorName || "",
      entry.doctorUserId || "",
      entry.reason || "",
      entry.status,
    ].some((value) => value.toLowerCase().includes(term)));
  }, [listSearch, waitlist]);
  const slotSummary = React.useMemo(() => ({
    total: slots.length,
    bookable: bookableSlots.length,
    available: slotPresentations.filter(({ presentation }) => presentation.state === "AVAILABLE" || presentation.state === "CURRENT" || presentation.state === "PARTIALLY_BOOKED").length,
    partial: slotPresentations.filter(({ presentation }) => presentation.state === "PARTIALLY_BOOKED").length,
    full: slotPresentations.filter(({ presentation }) => presentation.state === "FULL").length,
    unavailable: slotPresentations.filter(({ presentation }) => ["LEAVE", "HOLIDAY", "UNAVAILABLE"].includes(presentation.state)).length,
    past: slotPresentations.filter(({ presentation }) => presentation.isPast).length,
  }), [bookableSlots.length, slotPresentations]);
  const summaryStats = React.useMemo(() => ({
    totalToday: todayRows.length,
    booked: todayRows.filter((item) => item.status === "BOOKED").length,
    checkedIn: todayRows.filter((item) => item.status === "WAITING").length,
    inConsultation: todayRows.filter((item) => item.status === "IN_CONSULTATION").length,
    waitlist: waitlist.length,
    availableSlots: bookableSlots.length,
  }), [bookableSlots.length, todayRows, waitlist.length]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const submitAppointment = async (allowAdHocBooking = false) => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !doctorUserId) {
      setError("Select a patient and doctor before saving.");
      return;
    }
    if (type !== "WALK_IN" && !appointmentTime) {
      setError("Select an available slot or enter an appointment time before saving.");
      return;
    }
    const parsed = appointmentCreateSchema.safeParse({
      patientId: selectedPatient.id,
      doctorUserId,
      appointmentDate,
      appointmentTime: type === "WALK_IN" ? undefined : appointmentTime || undefined,
      reason: reason.trim() || undefined,
      type,
      status: null,
      priority,
      allowAdHocBooking,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message || "Failed to save appointment");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      if (type === "WALK_IN") {
        await createWalkInAppointment(auth.accessToken, auth.tenantId, {
          patientId: selectedPatient.id,
          doctorUserId,
          appointmentDate,
          reason: reason.trim() || null,
          priority,
        });
      } else {
        await createAppointment(auth.accessToken, auth.tenantId, {
          patientId: selectedPatient.id,
          doctorUserId,
          appointmentDate,
          appointmentTime: appointmentTime || null,
          reason: reason.trim() || null,
          type,
          status: null,
          priority,
          allowAdHocBooking,
        });
      }
      setPatientQuery("");
      setPatientResults([]);
      setSelectedPatient(null);
      setReason("");
      setType("SCHEDULED");
      setPriority("NORMAL");
      setAppointmentTime("");
      setEmergencyBooking(false);
      await loadAppointments();
      await loadSlots();
      await loadWaitlist();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save appointment");
    } finally {
      setSaving(false);
    }
  };

  const saveAppointment = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !doctorUserId) {
      setError("Select a patient and doctor before saving.");
      return;
    }
    if (type !== "WALK_IN" && !appointmentTime) {
      setError("Select an available slot or enter an appointment time before saving.");
      return;
    }
    const timeValue = appointmentTime || "";
    const matchedSlot = matchingSlot;
    const matchedSlotPresentation = matchedSlot
      ? getAppointmentSlotPresentation(appointmentDate, matchedSlot, clinicTimeZone, clinicNowSnapshot)
      : null;
    if (type === "WALK_IN") {
      await submitAppointment(false);
      return;
    }
    if (emergencyBooking && !reason.trim()) {
      setError("Reason is required for ad-hoc / emergency booking.");
      return;
    }

    if (!matchedSlot && isPastDateTime(appointmentDate, timeValue, clinicTimeZone, clinicNowSnapshot)) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }

    if (matchedSlot && matchedSlotPresentation) {
      if (matchedSlotPresentation.isPast) {
        setError("Selected time has already passed. Please choose a current or future slot.");
        return;
      }
      if (matchedSlotPresentation.state === "FULL") {
        setError("This slot is full.");
        return;
      }
      if (["LEAVE", "HOLIDAY", "UNAVAILABLE"].includes(matchedSlotPresentation.state)) {
        setError("Doctor is unavailable for the selected time.");
        return;
      }
      if (!matchedSlotPresentation.bookable) {
        setError(matchedSlotPresentation.tooltip);
        return;
      }
      await submitAppointment(false);
      return;
    }

    if (!emergencyBooking) {
      setError("Selected time is outside configured doctor availability. Please choose an available slot.");
      return;
    }

    await submitAppointment(true);
  };

  const confirmAdHocBooking = async () => {
    setAdHocConfirmOpen(false);
    setAdHocConfirmPending(true);
    try {
      await submitAppointment(true);
    } finally {
      setAdHocConfirmPending(false);
    }
  };
  const addToWaitlist = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !selectedDoctorId) return;
    try {
      await createWaitlist(auth.accessToken, auth.tenantId, {
        patientId: selectedPatient.id,
        doctorUserId: selectedDoctorId,
        preferredDate: appointmentDate,
        preferredStartTime: appointmentTime || null,
        preferredEndTime: null,
        reason: reason.trim() || null,
        notes: null,
      });
      await loadWaitlist();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add to waitlist");
    }
  };
  const bookFromWaitlist = async (entry: AppointmentWaitlist) => {
    if (!auth.accessToken || !auth.tenantId || !entry.id || !selectedDoctorId || !appointmentTime) return;
    if (isPastDateTime(appointmentDate, appointmentTime, clinicTimeZone, clinicNowSnapshot)) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }
    const parsed = appointmentCreateSchema.safeParse({
      patientId: entry.patientId,
      doctorUserId: selectedDoctorId,
      appointmentDate,
      appointmentTime,
      reason: entry.reason || undefined,
      type: "SCHEDULED",
      status: null,
      priority: "NORMAL",
      allowAdHocBooking: !hasStandardBookableSlots && !matchingSlot,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message || "Failed to save appointment");
      return;
    }
    try {
      await createAppointment(auth.accessToken, auth.tenantId, {
        patientId: entry.patientId,
        doctorUserId: selectedDoctorId,
        appointmentDate,
        appointmentTime,
        reason: entry.reason,
        type: "SCHEDULED",
        status: null,
        priority: "NORMAL",
        allowAdHocBooking: !hasStandardBookableSlots && !matchingSlot,
      });
      await updateWaitlistStatus(auth.accessToken, auth.tenantId, entry.id, "BOOKED");
      await loadAppointments();
      await loadSlots();
      await loadWaitlist();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to book from waitlist");
    }
  };
  const openReschedule = (appointment: Appointment) => {
    setRescheduleTarget(appointment);
    setRescheduleDoctorUserId(appointment.doctorUserId);
    setRescheduleDate(appointment.appointmentDate);
    setRescheduleTime((appointment.appointmentTime || "").slice(0, 5));
    setRescheduleOpen(true);
  };
  const saveReschedule = async () => {
    if (!auth.accessToken || !auth.tenantId || !rescheduleTarget || !rescheduleTime || !rescheduleDate) return;
    if (isPastDateTime(rescheduleDate, rescheduleTime, clinicTimeZone, clinicNowSnapshot)) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }
    const parsed = appointmentRescheduleSchema.safeParse({
      appointmentId: rescheduleTarget.id,
      appointmentDate: rescheduleDate,
      appointmentTime: rescheduleTime,
      reason: "Rescheduled from calendar",
      doctorUserId: rescheduleDoctorUserId || undefined,
      type: rescheduleTarget.type || undefined,
      status: rescheduleTarget.status || undefined,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message || "Failed to reschedule appointment");
      return;
    }
    try {
      await rescheduleAppointment(auth.accessToken, auth.tenantId, rescheduleTarget.id, {
        doctorUserId: parsed.data.doctorUserId || null,
        appointmentDate: parsed.data.appointmentDate,
        appointmentTime: parsed.data.appointmentTime,
        reason: parsed.data.reason || "Rescheduled from calendar",
      });
      setRescheduleOpen(false);
      setRescheduleTarget(null);
      await loadAppointments();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reschedule appointment");
    }
  };

  const cancelAppointment = async (appointment: Appointment) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setCancelTarget(appointment);
    setCancelComment(appointment.status === "WAITING" ? (appointment.reason || "Cancelled by front desk") : "Cancelled from appointments list");
    setCancelDialogOpen(true);
  };

  const confirmCancelAppointment = async () => {
    if (!auth.accessToken || !auth.tenantId || !cancelTarget) return;
    const requiresReason = cancelTarget.status === "WAITING";
    const comment = cancelComment.trim();
    if (requiresReason && !comment) {
      setError("Cancellation reason is required after check-in.");
      return;
    }
    setActionAppointmentId(cancelTarget.id);
    setError(null);
    try {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, cancelTarget.id, "CANCELLED", comment || "Cancelled from appointments list");
      setCancelDialogOpen(false);
      setCancelTarget(null);
      setCancelComment("");
      await loadAppointments();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel appointment");
    } finally {
      setActionAppointmentId(null);
    }
  };

  const openQuickRegister = () => {
    if (!canQuickRegisterPatient) return;
    setQuickRegisterOpen(true);
  };

  React.useEffect(() => {
    if (!canQuickRegisterPatient) {
      setQuickRegisterOpen(false);
    }
  }, [canQuickRegisterPatient]);

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box sx={{ maxWidth: 760 }}>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 0.75 }}>Appointments</Typography>
          <Typography variant="body2" color="text.secondary">
            Reception flow: search patient, pick doctor and slot, book, waitlist, or check in without scrolling through a long page.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
          <Chip size="small" label={clinicClockLabel} variant="outlined" sx={compactChipSx} />
          <Button variant="outlined" onClick={() => navigate("/queue")}>Open Queue</Button>
          <Button variant="outlined" onClick={() => navigate("/appointments/day-board")}>Day Board</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="Today appointments" value={summaryStats.totalToday} helper="All active appointments today" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="Booked" value={summaryStats.booked} tone="warning" helper="Scheduled but not checked in" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="Checked-in" value={summaryStats.checkedIn} tone="warning" helper="Waiting at reception" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="In consultation" value={summaryStats.inConsultation} tone="info" helper="With doctor" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="Waitlist" value={summaryStats.waitlist} tone="secondary" helper="Waiting list entries" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 2 }}><CompactStatCard label="Available slots" value={summaryStats.availableSlots} tone="success" helper="Current bookable slots" /></Grid>
      </Grid>

      {isDoctor ? (
        <Alert severity="info">
          Doctors can review their assigned appointments here. Use My Queue or Day Board for clinical workflow; appointment creation and quick patient registration are hidden.
        </Alert>
      ) : null}

      <Grid container spacing={2} alignItems="stretch">
        {canCreateAppointmentFlow ? (
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card variant="outlined" sx={{ height: "100%" }}>
              <CardContent sx={{ p: 1.5 }}>
                <Stack spacing={1.5}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "flex-start" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Create Appointment</Typography>
                      <Typography variant="body2" color="text.secondary">Patient search, doctor selection, time pick, waitlist, and ad-hoc emergency booking.</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap">
                      {selectedDoctorId ? <Chip size="small" label={`Doctor: ${doctorOptions.find((doctor) => doctor.appUserId === selectedDoctorId)?.displayName || selectedDoctorId}`} sx={compactChipSx} /> : null}
                      <Chip size="small" label={`Date: ${appointmentDate}`} variant="outlined" sx={compactChipSx} />
                    </Stack>
                  </Box>

                  <TextField
                    size="small"
                    label="Search patient"
                    value={patientQuery}
                    onChange={(event) => {
                      setSelectedPatient(null);
                      setPatientQuery(event.target.value);
                    }}
                    helperText="Search by patient ID, patient number, mobile, or name"
                  />

                {selectedPatient ? (
                  <Card variant="outlined" sx={{ bgcolor: alpha("#1976d2", 0.05), borderColor: "primary.light" }}>
                    <CardContent sx={{ py: 1.25 }}>
                      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                        <Box>
                          <Typography sx={{ fontWeight: 800, lineHeight: 1.2 }}>{patientLabel(selectedPatient)}</Typography>
                          <Typography variant="body2" color="text.secondary">{patientSummary(selectedPatient)}</Typography>
                        </Box>
                        <Button size="small" onClick={() => setSelectedPatient(null)}>Change</Button>
                      </Stack>
                    </CardContent>
                  </Card>
                ) : null}

                  {patientResults.length > 0 && !selectedPatient ? (
                    <Card variant="outlined">
                      <List dense disablePadding>
                        {patientResults.map((patient) => (
                          <ListItemButton key={patient.id} onClick={() => {
                            setSelectedPatient(patient);
                            setPatientQuery(patientSummary(patient));
                            setQuickRegisterOpen(false);
                          }}>
                            <ListItemText primary={patientLabel(patient)} secondary={patientSummary(patient)} />
                          </ListItemButton>
                        ))}
                      </List>
                    </Card>
                  ) : null}

                  {searchingPatients ? (
                    <Alert severity="info">Searching for matching patients...</Alert>
                  ) : canQuickRegisterPatient && patientResults.length === 0 && patientQuery.trim().length >= 2 && !selectedPatient ? (
                    <Alert severity="info" action={<Button color="inherit" size="small" onClick={openQuickRegister}>Quick Register Patient</Button>}>
                      No matching patient found. Quick register and continue.
                    </Alert>
                  ) : null}

                  <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="doctor-select-label">Doctor</InputLabel>
                      <Select
                        labelId="doctor-select-label"
                        label="Doctor"
                        value={selectedDoctorId}
                        onChange={(event) => setDoctorUserId(String(event.target.value))}
                        disabled={Boolean(doctorFilter)}
                      >
                        <MenuItem value="">Select doctor</MenuItem>
                        {doctorOptions.map((doctor) => (
                          <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                            {doctor.displayName || doctor.email || doctor.appUserId}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth type="date" label="Date" value={appointmentDate} onChange={(event) => { setAppointmentDateTouched(true); setAppointmentDate(event.target.value); }} InputLabelProps={{ shrink: true }} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      size="small"
                      fullWidth
                      type="time"
                      label="Time"
                      value={appointmentTime}
                      onChange={(event) => setAppointmentTime(event.target.value)}
                      InputLabelProps={{ shrink: true }}
                      disabled={type === "WALK_IN"}
                      helperText={
                        type === "WALK_IN"
                          ? "Walk-ins are tokenized on arrival."
                          : emergencyBooking
                            ? "Emergency mode allows manual times outside configured slots."
                            : bookableSlots.length === 0
                              ? "No bookable slots remain for this doctor/date. You can still book manually if needed."
                              : "Pick a slot below or enter a current/future time."
                      }
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }} sx={{ display: "flex", alignItems: "center" }}>
                    <FormControlLabel
                      control={<Switch checked={emergencyBooking} onChange={(event) => setEmergencyBooking(event.target.checked)} />}
                      label="Ad-hoc / Emergency booking"
                    />
                  </Grid>
                </Grid>

                {emergencyWarning ? (
                  <Alert severity="warning">
                    {emergencyWarning}
                  </Alert>
                ) : null}

                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="appointment-type-label">Type</InputLabel>
                      <Select labelId="appointment-type-label" label="Type" value={type} onChange={(event) => setType(event.target.value as AppointmentType)}>
                        {appointmentTypes.map((option) => (
                          <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="appointment-priority-label">Priority</InputLabel>
                      <Select labelId="appointment-priority-label" label="Priority" value={priority} onChange={(event) => setPriority(event.target.value as AppointmentPriority)}>
                        {appointmentPriorities.map((option) => (
                          <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                    <Grid size={12}>
                      <TextField
                        size="small"
                        label="Reason"
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        multiline
                        minRows={3}
                        required={emergencyBooking}
                        helperText={emergencyBooking ? "Required for emergency/ad-hoc booking." : "Optional unless you need a specific visit reason."}
                      />
                    </Grid>
                  </Grid>

                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <Button variant="contained" onClick={() => void saveAppointment()} disabled={!canCreateAppointment}>
                      {emergencyBooking ? "Create Emergency Booking" : (type === "WALK_IN" ? "Create Walk-In" : "Create Appointment")}
                    </Button>
                    <Button variant="outlined" onClick={() => void addToWaitlist()} disabled={!selectedPatient || !selectedDoctorId || !appointmentDate}>
                      Add to Waitlist
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ) : null}

        <Grid size={{ xs: 12, lg: canCreateAppointmentFlow ? 7 : 12 }}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent sx={{ p: 1.5 }}>
              <Stack spacing={1.5}>
                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "flex-start" }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Doctor Available Slots</Typography>
                    <Typography variant="body2" color="text.secondary">Compact slot timeline. Current slot is highlighted and remains bookable while capacity remains.</Typography>
                  </Box>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap">
                    <Chip size="small" label={`${slotSummary.bookable} bookable`} variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`${slotSummary.available} available`} color="success" variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`${slotSummary.partial} partial`} color="warning" variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`${slotSummary.full} full`} color="error" variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`${slotSummary.unavailable} unavailable`} variant="outlined" sx={compactChipSx} />
                    <Chip size="small" label={`${slotSummary.past} past`} variant="outlined" sx={compactChipSx} />
                  </Stack>
                </Box>
                <FormControlLabel
                  control={<Switch checked={hidePastSlots} onChange={(event) => setHidePastSlots(event.target.checked)} />}
                  label="Hide past slots"
                />

                {currentSlot ? (
                  <Alert severity="info">
                    Current slot: {slotLabel(currentSlot, appointmentDate, clinicTimeZone, clinicNowSnapshot)}. If capacity remains, it can still be booked.
                  </Alert>
                ) : null}

                {visibleSlots.length === 0 ? (
                  <CompactEmptyState
                    title={slots.length === 0 ? "No schedule loaded" : "No current or future slots"}
                    subtitle={slots.length === 0
                      ? "Select a doctor and date to load configured availability."
                      : hidePastSlots
                        ? "No current or future slots are available. Turn off Hide past slots to review historical slots."
                        : "Past slots remain visible but read-only. Select a current or future slot, or use emergency booking for a manual time."}
                  />
                ) : (
                  <Box
                    sx={{
                      display: "grid",
                      gap: 1,
                      gridTemplateColumns: {
                        xs: "repeat(auto-fit, minmax(128px, 1fr))",
                        sm: "repeat(auto-fit, minmax(140px, 1fr))",
                      },
                    }}
                  >
                    {visibleSlots.map(({ slot, presentation }) => {
                      const timeLabel = toFive(slot.slotTime);
                      const selected = appointmentTime === timeLabel;
                      const current = presentation.isCurrent;
                      const past = presentation.isPast;
                      const bookable = presentation.bookable;
                      return (
                        <Tooltip key={`${slot.slotTime}-${slot.slotEndTime}`} title={presentation.tooltip} arrow>
                          <span>
                            <Button
                              onClick={() => setAppointmentTime(timeLabel)}
                              disabled={past || !bookable}
                              variant={selected ? "contained" : "outlined"}
                              color={past ? "inherit" : slotTone(presentation.state)}
                              sx={{
                                justifyContent: "flex-start",
                                alignItems: "flex-start",
                                textAlign: "left",
                                minHeight: 84,
                                borderColor: current ? "primary.main" : undefined,
                                bgcolor: past ? alpha("#6b7280", 0.08) : current ? alpha("#1976d2", 0.06) : undefined,
                                boxShadow: current ? "inset 0 0 0 1px rgba(25,118,210,0.28)" : undefined,
                                opacity: past ? 0.68 : 1,
                              }}
                            >
                              <Stack spacing={0.35} sx={{ width: "100%" }}>
                                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center" }}>
                                  <Typography sx={{ fontWeight: 800, lineHeight: 1 }}>{timeLabel}</Typography>
                                  {current ? <Chip size="small" label="Current" color="primary" sx={compactChipSx} /> : null}
                                </Box>
                                <Typography variant="caption" color="text.secondary">
                                  {slot.slotEndTime.slice(0, 5)} • {slot.bookedCount}/{slot.maxPatientsPerSlot}
                                </Typography>
                                <Chip size="small" label={presentation.state.replace(/_/g, " ")} color={slotChipTone(presentation.state)} variant="outlined" sx={compactChipSx} />
                              </Stack>
                            </Button>
                          </span>
                        </Tooltip>
                      );
                    })}
                  </Box>
                )}

                {slots.length > 0 && bookableSlots.length === 0 ? (
                  <Alert severity="info">No bookable slots remain for this doctor today. Choose another date or use emergency booking.</Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={{ p: 1.5 }}>
          <Stack spacing={1.5}>
            <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.1 }}>Today / Upcoming / Waitlist</Typography>
                <Typography variant="body2" color="text.secondary">Bottom tabs keep the receptionist on one screen for fast booking and check-in.</Typography>
              </Box>
              <Button size="small" variant="outlined" onClick={() => void loadAppointments()}>Refresh</Button>
            </Box>

            <TextField
              size="small"
              fullWidth
              label="Search appointments and waitlist"
              value={listSearch}
              onChange={(event) => setListSearch(event.target.value)}
              placeholder="Appointment ID, token, consultation ID, patient, mobile, doctor"
            />

            <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
              <Tab value="today" label={`Today (${todayRows.length})`} />
              <Tab value="upcoming" label={`Upcoming (${upcomingRows.length})`} />
              <Tab value="waitlist" label={`Waitlist (${waitlist.length})`} />
              <Tab value="completed" label={`Completed (${completedRows.length})`} />
              <Tab value="archive" label={`Cancelled / No-show (${archiveRows.length})`} />
            </Tabs>

            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
                <Stack spacing={1} alignItems="center">
                  <CircularProgress />
                  <Typography variant="body2" color="text.secondary">Loading appointments...</Typography>
                </Stack>
              </Box>
            ) : tab === "waitlist" ? (
              filteredWaitlist.length === 0 ? (
                <Alert severity="info">No waitlist entries were found for the selected doctor/date.</Alert>
              ) : (
                <CompactTableFrame maxHeight={520}>
                  <Table size="small" stickyHeader sx={{ minWidth: 980 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Patient</TableCell>
                        <TableCell>Doctor</TableCell>
                        <TableCell>Preferred</TableCell>
                        <TableCell>Reason</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredWaitlist.map((entry) => (
                        <TableRow key={entry.id}>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{entry.patientName || entry.patientNumber || entry.patientId}</Typography>
                              <Typography variant="caption" color="text.secondary">{entry.patientNumber || entry.patientId}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{entry.doctorName || entry.doctorUserId || "Any"}</TableCell>
                          <TableCell>
                            <Typography variant="body2">{entry.preferredDate}</Typography>
                            <Typography variant="caption" color="text.secondary">{entry.preferredStartTime || "Any time"}</Typography>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 260, wordBreak: "break-word" }}>{entry.reason || "-"}</TableCell>
                          <TableCell><Chip size="small" label={waitlistStatusLabel(entry.status)} variant="outlined" sx={compactChipSx} /></TableCell>
                          <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                            <Button size="small" variant="outlined" sx={{ whiteSpace: "nowrap" }} onClick={() => void bookFromWaitlist(entry)} disabled={!appointmentTime}>Book</Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CompactTableFrame>
              )
            ) : filteredVisibleAppointments.length === 0 ? (
              <Alert severity="info">No appointments were found for the selected tab.</Alert>
            ) : (
              <CompactTableFrame maxHeight={540}>
                <Table size="small" stickyHeader sx={{ minWidth: 920 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Patient</TableCell>
                      <TableCell>Doctor</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Token</TableCell>
                      <TableCell>Priority</TableCell>
                      <TableCell>Arrival</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredVisibleAppointments.map((appointment) => (
                      <TableRow key={appointment.id}>
                        <TableCell>
                          <Stack spacing={0.2}>
                            <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                              {appointment.patientName || appointment.patientNumber || appointment.patientId}
                            </Button>
                            <Typography variant="caption" color="text.secondary">{appointment.patientNumber}</Typography>
                            <Typography variant="caption" color="text.secondary">{appointment.patientMobile || "-"}</Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                        <TableCell>{formatDate(appointment.appointmentDate)}</TableCell>
                        <TableCell>{appointment.appointmentTime || "-"}</TableCell>
                        <TableCell>{appointment.displayReference || appointment.tokenNumber || "-"}</TableCell>
                        <TableCell><Chip size="small" label={appointment.priority || "NORMAL"} color={priorityColor(appointment.priority)} variant="outlined" sx={compactChipSx} /></TableCell>
                        <TableCell>{arrivalLabel(appointment)}</TableCell>
                        <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} sx={compactChipSx} /></TableCell>
                        <TableCell align="right" sx={{ minWidth: 180 }}>
                          <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap" useFlexGap sx={{ "& .MuiButton-root": { whiteSpace: "nowrap" } }}>
                            <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)}>Patient</Button>
                            {appointment.status === "BOOKED" ? (
                              <Button size="small" onClick={() => openReschedule(appointment)}>Reschedule</Button>
                            ) : null}
                            {!isDoctor && (appointment.status === "BOOKED" || appointment.status === "WAITING") ? (
                              <Button size="small" color="error" onClick={() => void cancelAppointment(appointment)} disabled={actionAppointmentId === appointment.id}>
                                Cancel
                              </Button>
                            ) : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CompactTableFrame>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={adHocConfirmOpen} onClose={() => !adHocConfirmPending && setAdHocConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Confirm ad-hoc booking</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Alert severity="warning">{adHocConfirmMessage || "This time is outside doctor availability. Continue as ad-hoc booking?"}</Alert>
            <Typography variant="body2" color="text.secondary">
              The selected time does not match a generated availability slot. The booking will still be created for operational use if you continue.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAdHocConfirmOpen(false)} disabled={adHocConfirmPending}>Cancel</Button>
          <Button variant="contained" onClick={() => void confirmAdHocBooking()} disabled={adHocConfirmPending}>
            Continue
          </Button>
        </DialogActions>
      </Dialog>

      {canQuickRegisterPatient ? (
        <PatientQuickRegisterDialog
          open={quickRegisterOpen}
          token={auth.accessToken}
          tenantId={auth.tenantId}
          title="Quick Register Patient"
          subtitle="Create the patient in master and continue the appointment flow without leaving the page."
          initialMobile={patientQuery}
          onClose={() => setQuickRegisterOpen(false)}
          onCreated={(saved) => {
            setSelectedPatient(saved);
            setPatientQuery(patientSummary(saved));
            setQuickRegisterOpen(false);
            setPatientResults([]);
          }}
        />
      ) : null}
      <Dialog open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Reschedule Appointment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { sm: 480 } }}>
            <FormControl fullWidth>
              <InputLabel id="reschedule-doctor-label">Doctor</InputLabel>
              <Select labelId="reschedule-doctor-label" label="Doctor" value={rescheduleDoctorUserId} onChange={(event) => setRescheduleDoctorUserId(String(event.target.value))}>
                {doctorOptions.map((doctor) => (
                  <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                    {doctor.displayName || doctor.email || doctor.appUserId}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField fullWidth type="date" label="Date" value={rescheduleDate} onChange={(e) => setRescheduleDate(e.target.value)} InputLabelProps={{ shrink: true }} />
              <TextField fullWidth type="time" label="Time" value={rescheduleTime} onChange={(e) => setRescheduleTime(e.target.value)} InputLabelProps={{ shrink: true }} />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRescheduleOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveReschedule()} disabled={!rescheduleDate || !rescheduleTime}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={cancelDialogOpen} onClose={() => { setCancelDialogOpen(false); setCancelTarget(null); setCancelComment(""); }} fullWidth maxWidth="sm">
        <DialogTitle>Cancel appointment</DialogTitle>
        <DialogContent>
          {cancelTarget ? (
            <Stack spacing={1.25} sx={{ pt: 1 }}>
              <Alert severity="warning">This will cancel the appointment and remove it from active scheduling.</Alert>
              <Typography variant="body2">
                Patient: {cancelTarget.patientName || cancelTarget.patientNumber || cancelTarget.patientId}
              </Typography>
              <Typography variant="body2">
                Doctor: {cancelTarget.doctorName || cancelTarget.doctorUserId}
              </Typography>
              <Typography variant="body2">
                When: {formatDate(cancelTarget.appointmentDate)} {cancelTarget.appointmentTime || "-"}
              </Typography>
              <Typography variant="body2">
                Reference: {appointmentReference(cancelTarget)}
              </Typography>
              <TextField
                fullWidth
                label={cancelTarget.status === "WAITING" ? "Cancellation reason" : "Comment"}
                value={cancelComment}
                onChange={(event) => setCancelComment(event.target.value)}
                required={cancelTarget.status === "WAITING"}
                helperText={cancelTarget.status === "WAITING" ? "Reason is required after check-in." : "Optional note for the audit trail."}
                multiline
                minRows={2}
              />
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setCancelDialogOpen(false); setCancelTarget(null); setCancelComment(""); }} disabled={actionAppointmentId !== null}>Close</Button>
          <Button variant="contained" color="error" onClick={() => void confirmCancelAppointment()} disabled={!cancelTarget || actionAppointmentId === cancelTarget.id}>
            Cancel appointment
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
