import * as React from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
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
} from "@mui/material";
import { alpha } from "@mui/material/styles";

import { useAuth } from "../../auth/useAuth";
import {
  createAppointment,
  createWaitlist,
  createPatient,
  createWalkInAppointment,
  getDoctorSlots,
  getWaitlist,
  getClinicUsers,
  getPatient,
  rescheduleAppointment,
  searchAppointments,
  searchPatients,
  updateWaitlistStatus,
  type Appointment,
  type AppointmentWaitlist,
  type DoctorAvailabilitySlot,
  type AppointmentPriority,
  type AppointmentType,
  type ClinicUser,
  type Patient,
  type PatientGender,
  type PatientInput,
  type WaitlistStatus,
} from "../../api/clinicApi";
import {
  isBookingTimePast,
  isCurrentSlot,
  findSlotForTime,
  isSlotExpired,
} from "./bookingValidation";
import { CompactEmptyState, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";

type AppointmentTab = "today" | "upcoming" | "waitlist" | "completed" | "archive";

type AppointmentPageState = {
  patient?: Patient;
};

type QuickRegisterForm = {
  mobile: string;
  firstName: string;
  lastName: string;
  ageYears: string;
  dateOfBirth: string;
  gender: PatientGender;
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

function digits(value: string) {
  return value.replace(/[\s-]/g, "").trim();
}

function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function calculateAge(dateOfBirth: string) {
  if (!dateOfBirth) return "";
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return "";
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const monthDiff = today.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age >= 0 && age <= 130 ? String(age) : "";
}

function approximateDobFromAge(ageYears: string) {
  const parsed = Number(ageYears);
  if (!ageYears || Number.isNaN(parsed) || parsed < 0 || parsed > 130) return "";
  return `${new Date().getFullYear() - parsed}-01-01`;
}

function patientLabel(patient: Patient | null) {
  if (!patient) return "";
  const age = patient.ageYears !== null ? `${patient.ageYears}y` : null;
  const label = `${patient.firstName} ${patient.lastName || ""}`.trim();
  return [label, age, patient.gender].filter(Boolean).join(" • ");
}

function patientSummary(patient: Patient) {
  return `${patient.patientNumber} • ${patient.mobile}`;
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

function localDateKey(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function toFive(time: string | null | undefined) {
  if (!time) return "";
  return time.slice(0, 5);
}

function isPastDateTime(date: string, time: string | null | undefined) {
  return isBookingTimePast(date, time);
}

function isPastSlot(date: string, slot: DoctorAvailabilitySlot) {
  return isSlotExpired(date, slot);
}

function isBookableSlot(date: string, slot: DoctorAvailabilitySlot) {
  if (isPastSlot(date, slot)) return false;
  if (slot.status === "AVAILABLE") return slot.selectable;
  if (slot.status === "PARTIALLY_BOOKED") {
    return slot.selectable && slot.bookedCount < slot.maxPatientsPerSlot;
  }
  return false;
}

function slotTone(slot: DoctorAvailabilitySlot) {
  switch (slot.status) {
    case "AVAILABLE":
      return "success";
    case "PARTIALLY_BOOKED":
      return "warning";
    case "FULL":
      return "error";
    case "BREAK":
    case "LEAVE":
    case "UNAVAILABLE":
    case "CONFLICTED":
      return "inherit";
  }
}

function slotChipTone(slot: DoctorAvailabilitySlot) {
  const tone = slotTone(slot);
  return tone === "inherit" ? "default" : tone;
}

function slotLabel(slot: DoctorAvailabilitySlot, date: string) {
  const timeLabel = toFive(slot.slotTime);
  const capacityLabel = `${slot.bookedCount}/${slot.maxPatientsPerSlot}`;
  if (isCurrentSlot(date, slot)) {
    return `${timeLabel} • current • ${capacityLabel}`;
  }
  if (isPastSlot(date, slot)) {
    return `${timeLabel} • past • ${capacityLabel}`;
  }
  return `${timeLabel} • ${slot.status.toLowerCase().replace(/_/g, " ")} • ${capacityLabel}`;
}

function toPatientInput(form: QuickRegisterForm): PatientInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    gender: form.gender,
    dateOfBirth: form.dateOfBirth || null,
    ageYears: form.ageYears ? Number(form.ageYears) : null,
    mobile: digits(form.mobile),
    email: null,
    addressLine1: null,
    addressLine2: null,
    city: null,
    state: null,
    country: null,
    postalCode: null,
    emergencyContactName: null,
    emergencyContactMobile: null,
    bloodGroup: null,
    allergies: null,
    existingConditions: null,
    longTermMedications: null,
    surgicalHistory: null,
    notes: null,
    active: true,
  };
}

function emptyQuickRegisterForm(mobile = ""): QuickRegisterForm {
  return {
    mobile,
    firstName: "",
    lastName: "",
    ageYears: "",
    dateOfBirth: "",
    gender: "UNKNOWN",
  };
}

export default function AppointmentsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const statePatient = (location.state as AppointmentPageState | null)?.patient ?? null;
  const doctorUserIdFromQuery = searchParams.get("doctorUserId") || "";
  const appointmentDateFromQuery = searchParams.get("appointmentDate") || localDateKey();
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
  const [searchingPatients, setSearchingPatients] = React.useState(false);
  const [slots, setSlots] = React.useState<DoctorAvailabilitySlot[]>([]);
  const [quickRegisterOpen, setQuickRegisterOpen] = React.useState(false);
  const [quickRegisterSaving, setQuickRegisterSaving] = React.useState(false);
  const [quickRegisterError, setQuickRegisterError] = React.useState<string | null>(null);
  const [quickRegisterForm, setQuickRegisterForm] = React.useState<QuickRegisterForm>(emptyQuickRegisterForm());
  const [error, setError] = React.useState<string | null>(null);
  const [waitlist, setWaitlist] = React.useState<AppointmentWaitlist[]>([]);
  const [rescheduleOpen, setRescheduleOpen] = React.useState(false);
  const [rescheduleTarget, setRescheduleTarget] = React.useState<Appointment | null>(null);
  const [rescheduleDate, setRescheduleDate] = React.useState(new Date().toISOString().slice(0, 10));
  const [rescheduleTime, setRescheduleTime] = React.useState("");
  const [rescheduleDoctorUserId, setRescheduleDoctorUserId] = React.useState("");
  const [emergencyBooking, setEmergencyBooking] = React.useState(false);
  const [adHocConfirmOpen, setAdHocConfirmOpen] = React.useState(false);
  const [adHocConfirmMessage, setAdHocConfirmMessage] = React.useState("");
  const [adHocConfirmPending, setAdHocConfirmPending] = React.useState(false);

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const doctorOptions = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
  const doctorFilter = isDoctor && auth.appUserId ? auth.appUserId : undefined;
  const today = localDateKey();

  const selectedDoctorId = doctorFilter || doctorUserId || "";
  const requiresAppointmentTime = type !== "WALK_IN";
  const matchingSlot = React.useMemo(
    () => findSlotForTime(appointmentDate, appointmentTime, slots),
    [appointmentDate, appointmentTime, slots],
  );
  const bookableSlots = React.useMemo(
    () => slots.filter((slot) => isBookableSlot(appointmentDate, slot)),
    [appointmentDate, slots],
  );
  const visibleSlots = React.useMemo(
    () => slots.filter((slot) => isCurrentSlot(appointmentDate, slot) || !isPastSlot(appointmentDate, slot)),
    [appointmentDate, slots],
  );
  const currentSlot = React.useMemo(
    () => slots.find((slot) => isCurrentSlot(appointmentDate, slot)) || null,
    [appointmentDate, slots],
  );
  const adHocBookingNeeded = React.useMemo(
    () => Boolean(
      requiresAppointmentTime
    && selectedDoctorId
    && appointmentDate
    && appointmentTime
    && !matchingSlot
    && !isPastDateTime(appointmentDate, appointmentTime)
    && slots.length > 0
    && !emergencyBooking
    ),
    [appointmentDate, appointmentTime, emergencyBooking, matchingSlot, requiresAppointmentTime, selectedDoctorId, slots.length],
  );
  const canCreateAppointment = Boolean(
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

        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^\+?[0-9\s-]{6,}$/.test(term) ? digits(term) : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^\+?[0-9\s-]{6,}$/.test(term) ? undefined : term,
          active: true,
        });
        if (!cancelled) {
          setPatientResults(rows);
          if (rows.length === 0 && /^\+?[0-9\s-]{6,}$/.test(term)) {
            setQuickRegisterForm((current) => ({ ...current, mobile: digits(term) }));
            setQuickRegisterError(null);
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
  }, [auth.accessToken, auth.tenantId, patientQuery]);

  const todayRows = React.useMemo(
    () => appointments.filter((item) => item.appointmentDate === today && item.status !== "CANCELLED" && item.status !== "NO_SHOW"),
    [appointments, today],
  );
  const upcomingRows = React.useMemo(
    () => appointments.filter((item) => item.appointmentDate > today && item.status !== "CANCELLED" && item.status !== "NO_SHOW"),
    [appointments, today],
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
    available: bookableSlots.filter((slot) => slot.status === "AVAILABLE").length,
    partial: bookableSlots.filter((slot) => slot.status === "PARTIALLY_BOOKED").length,
    full: slots.filter((slot) => slot.status === "FULL").length,
    unavailable: slots.filter((slot) => slot.status === "BREAK" || slot.status === "LEAVE" || slot.status === "UNAVAILABLE" || slot.status === "CONFLICTED").length,
    past: slots.filter((slot) => isPastSlot(appointmentDate, slot)).length,
  }), [appointmentDate, bookableSlots, slots]);
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
    if (type === "WALK_IN") {
      await submitAppointment(false);
      return;
    }
    if (emergencyBooking && !reason.trim()) {
      setError("Reason is required for ad-hoc / emergency booking.");
      return;
    }

    if (!matchedSlot && isPastDateTime(appointmentDate, timeValue)) {
      setError("Selected time has already passed. Choose a current or future slot.");
      return;
    }

    if (matchedSlot) {
      if (isPastSlot(appointmentDate, matchedSlot)) {
        setError("Selected time has already passed. Choose a current or future slot.");
        return;
      }
      if (matchedSlot.status === "FULL" || matchedSlot.bookedCount >= matchedSlot.maxPatientsPerSlot) {
        setError("This slot is full.");
        return;
      }
      if (matchedSlot.status === "BREAK" || matchedSlot.status === "LEAVE" || matchedSlot.status === "UNAVAILABLE" || matchedSlot.status === "CONFLICTED") {
        setError("Doctor is unavailable during this time.");
        return;
      }
      await submitAppointment(false);
      return;
    }

    if (emergencyBooking) {
      await submitAppointment(true);
      return;
    }

    if (slots.length > 0) {
      setAdHocConfirmMessage(`The selected time (${appointmentTime}) is outside doctor availability. Continue as ad-hoc booking?`);
      setAdHocConfirmPending(false);
      setAdHocConfirmOpen(true);
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
        allowAdHocBooking: slots.length > 0 && !matchingSlot,
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
    try {
      await rescheduleAppointment(auth.accessToken, auth.tenantId, rescheduleTarget.id, {
        doctorUserId: rescheduleDoctorUserId || null,
        appointmentDate: rescheduleDate,
        appointmentTime: rescheduleTime,
        reason: "Rescheduled from calendar",
      });
      setRescheduleOpen(false);
      setRescheduleTarget(null);
      await loadAppointments();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reschedule appointment");
    }
  };

  const saveQuickPatient = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setQuickRegisterSaving(true);
    setQuickRegisterError(null);
    try {
      const saved = await createPatient(auth.accessToken, auth.tenantId, toPatientInput(quickRegisterForm));
      setSelectedPatient(saved);
      setPatientQuery(patientSummary(saved));
      setQuickRegisterOpen(false);
      setPatientResults([]);
    } catch (err) {
      setQuickRegisterError(err instanceof Error ? err.message : "Unable to create patient");
    } finally {
      setQuickRegisterSaving(false);
    }
  };

  const openQuickRegister = () => {
    const term = patientQuery.trim();
    setQuickRegisterForm((current) => ({
      ...current,
      mobile: current.mobile || (term && /^\+?[0-9\s-]{6,}$/.test(term) ? digits(term) : ""),
    }));
    setQuickRegisterError(null);
    setQuickRegisterOpen(true);
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box sx={{ maxWidth: 760 }}>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 0.75 }}>Appointments</Typography>
          <Typography variant="body2" color="text.secondary">
            Reception flow: search patient, pick doctor and slot, book, waitlist, or check in without scrolling through a long page.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
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

      <Grid container spacing={2} alignItems="stretch">
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
                ) : patientResults.length === 0 && patientQuery.trim().length >= 2 && !selectedPatient ? (
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
                    <TextField size="small" fullWidth type="date" label="Date" value={appointmentDate} onChange={(event) => setAppointmentDate(event.target.value)} InputLabelProps={{ shrink: true }} />
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

                {emergencyBooking || adHocBookingNeeded ? (
                  <Alert severity="warning">
                    This time is outside configured doctor availability. Use only for emergency/ad-hoc booking.
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

        <Grid size={{ xs: 12, lg: 7 }}>
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

                {currentSlot ? (
                  <Alert severity="info">
                    Current slot: {slotLabel(currentSlot, appointmentDate)}. If capacity remains, it can still be booked.
                  </Alert>
                ) : null}

                {visibleSlots.length === 0 ? (
                  <CompactEmptyState
                    title={slots.length === 0 ? "No schedule loaded" : "No current or future slots"}
                    subtitle={slots.length === 0
                      ? "Select a doctor and date to load configured availability."
                      : "Past slots are hidden. Select a current or future slot, or use emergency booking for a manual time."}
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
                    {visibleSlots.map((slot) => {
                      const timeLabel = toFive(slot.slotTime);
                      const selected = appointmentTime === timeLabel;
                      const current = isCurrentSlot(appointmentDate, slot);
                      const past = isPastSlot(appointmentDate, slot);
                      const bookable = isBookableSlot(appointmentDate, slot);
                      return (
                        <Button
                          key={`${slot.slotTime}-${slot.slotEndTime}`}
                          onClick={() => setAppointmentTime(timeLabel)}
                          disabled={!bookable && !current && !emergencyBooking}
                          variant={selected ? "contained" : "outlined"}
                          color={past ? "inherit" : slotTone(slot)}
                          sx={{
                            justifyContent: "flex-start",
                            alignItems: "flex-start",
                            textAlign: "left",
                            minHeight: 84,
                            borderColor: current ? "primary.main" : undefined,
                            bgcolor: current ? alpha("#1976d2", 0.06) : undefined,
                            boxShadow: current ? "inset 0 0 0 1px rgba(25,118,210,0.28)" : undefined,
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
                            <Chip size="small" label={slot.status.replace(/_/g, " ")} color={slotChipTone(slot)} variant="outlined" sx={compactChipSx} />
                          </Stack>
                        </Button>
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
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small" sx={{ minWidth: 980 }}>
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
                          <TableCell align="right">
                            <Button size="small" variant="outlined" onClick={() => void bookFromWaitlist(entry)} disabled={!appointmentTime}>Book</Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )
            ) : filteredVisibleAppointments.length === 0 ? (
              <Alert severity="info">No appointments were found for the selected tab.</Alert>
            ) : (
              <Box sx={{ overflowX: "auto" }}>
                <Table size="small" sx={{ minWidth: 920 }}>
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
                        <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                        <TableCell><Chip size="small" label={appointment.priority || "NORMAL"} color={priorityColor(appointment.priority)} variant="outlined" sx={compactChipSx} /></TableCell>
                        <TableCell>{arrivalLabel(appointment)}</TableCell>
                        <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} sx={compactChipSx} /></TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap">
                            <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)}>Patient</Button>
                            <Button size="small" onClick={() => openReschedule(appointment)}>Reschedule</Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
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

      <Dialog open={quickRegisterOpen} onClose={() => setQuickRegisterOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Quick Register Patient</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {quickRegisterError ? <Alert severity="error">{quickRegisterError}</Alert> : null}
            <TextField fullWidth label="Mobile" value={quickRegisterForm.mobile} onChange={(event) => setQuickRegisterForm((current) => ({ ...current, mobile: event.target.value }))} />
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="First name" value={quickRegisterForm.firstName} onChange={(event) => setQuickRegisterForm((current) => ({ ...current, firstName: event.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="Last name" value={quickRegisterForm.lastName} onChange={(event) => setQuickRegisterForm((current) => ({ ...current, lastName: event.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth type="number" label="Age" value={quickRegisterForm.ageYears} onChange={(event) => setQuickRegisterForm((current) => ({ ...current, ageYears: event.target.value, dateOfBirth: approximateDobFromAge(event.target.value) }))} inputProps={{ min: 0, max: 130 }} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth type="date" label="Date of birth" value={quickRegisterForm.dateOfBirth} onChange={(event) => setQuickRegisterForm((current) => ({ ...current, dateOfBirth: event.target.value, ageYears: calculateAge(event.target.value) }))} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <FormControl fullWidth>
                  <InputLabel id="quick-gender-label">Gender</InputLabel>
                  <Select
                    labelId="quick-gender-label"
                    label="Gender"
                    value={quickRegisterForm.gender}
                    onChange={(event) => setQuickRegisterForm((current) => ({ ...current, gender: event.target.value as PatientGender }))}
                  >
                    <MenuItem value="MALE">Male</MenuItem>
                    <MenuItem value="FEMALE">Female</MenuItem>
                    <MenuItem value="OTHER">Other</MenuItem>
                    <MenuItem value="UNKNOWN">Unknown</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuickRegisterOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveQuickPatient()} disabled={quickRegisterSaving}>Save patient</Button>
        </DialogActions>
      </Dialog>
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
    </Stack>
  );
}
