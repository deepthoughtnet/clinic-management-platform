import * as React from "react";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  Tooltip,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { alpha, type Theme } from "@mui/material/styles";
import { useNavigate } from "react-router-dom";
import { doctorAvailabilitySchema, doctorUnavailabilitySchema, firstZodError, mapZodErrors } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, WorkflowStrip } from "../../components/compact/CompactUi";
import RequiredLabel from "../../components/forms/RequiredLabel";
import {
  createDoctorAvailability,
  createDoctorUnavailability,
  createWaitlist,
  deactivateDoctorUnavailability,
  getClinicClock,
  getClinicUsers,
  getDoctorAvailability,
  getDoctorSlots,
  getDoctorUnavailability,
  getWaitlist,
  searchAppointments,
  updateDoctorAvailability,
  type Appointment,
  type AppointmentWaitlist,
  type ClinicUser,
  type DoctorAvailability,
  type DoctorAvailabilityInput,
  type DoctorAvailabilitySlot,
  type DoctorAvailabilitySlotStatus,
  type DoctorUnavailability,
  type DoctorUnavailabilityInput,
  type DoctorUnavailabilityType,
  type WaitlistStatus,
} from "../../api/clinicApi";
import { formatClinicClockLabel, getClinicClockParts, getClinicDateKey, isBookingTimePast } from "../appointments/bookingValidation";
import { getAppointmentSlotPresentation } from "../appointments/slotState";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
const WEEKDAY_DAYS = DAYS.slice(0, 5);
const WEEKEND_DAYS = DAYS.slice(5);
const DOCTOR_AVAILABILITY_WORKFLOW_STEPS = [
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
type ViewMode = "day" | "week";
type StatusFilters = Record<DoctorAvailabilitySlotStatus | "WAITLIST", boolean>;

type DoctorOption = {
  appUserId: string;
  displayName: string;
  email: string | null;
};

type CalendarSlotRow = {
  date: string;
  doctorUserId: string;
  doctorName: string;
  slot: DoctorAvailabilitySlot;
  appointment: Appointment | null;
};

type TimeBucketKey = "morning" | "afternoon" | "evening" | "other";

type TimeBucketDefinition = {
  key: TimeBucketKey;
  label: string;
  rangeLabel: string;
};

type CalendarBucket = TimeBucketDefinition & {
  rows: CalendarSlotRow[];
  summary: {
    totalSlots: number;
    bookedCount: number;
    partialCount: number;
    fullCount: number;
    availableCount: number;
    checkedInCount: number;
    inConsultationCount: number;
  };
  hasBookings: boolean;
  autoExpanded: boolean;
  expanded: boolean;
};

type DoctorSessionGroup = {
  doctorUserId: string;
  doctorName: string;
  rows: DoctorAvailability[];
  activeCount: number;
  inactiveCount: number;
  totalCount: number;
};

type DaySessionBucket = {
  dayOfWeek: string;
  rows: DoctorSessionGroup[];
  doctorsCount: number;
  sessionsCount: number;
  activeCount: number;
  inactiveCount: number;
  autoExpanded: boolean;
  expanded: boolean;
};

const EMPTY_AVAILABILITY_FORM: DoctorAvailabilityInput = {
  dayOfWeek: "MONDAY",
  startTime: "09:00",
  endTime: "12:00",
  breakStartTime: null,
  breakEndTime: null,
  consultationDurationMinutes: 30,
  maxPatientsPerSlot: 2,
  active: true,
};

const EMPTY_UNAVAILABILITY_FORM: DoctorUnavailabilityInput = {
  startAt: `${getClinicDateKey("Asia/Kolkata")}T13:00:00`,
  endAt: `${getClinicDateKey("Asia/Kolkata")}T14:00:00`,
  type: "LEAVE",
  reason: null,
  active: true,
};

const DEFAULT_FILTERS: StatusFilters = {
  AVAILABLE: true,
  PARTIALLY_BOOKED: true,
  FULL: true,
  BREAK: true,
  LEAVE: true,
  HOLIDAY: true,
  UNAVAILABLE: true,
  CONFLICTED: true,
  WAITLIST: true,
};

const ALL_DOCTORS_OPTION: DoctorOption = {
  appUserId: "",
  displayName: "All Doctors",
  email: null,
};

const TIME_BUCKETS: TimeBucketDefinition[] = [
  { key: "morning", label: "Morning", rangeLabel: "06:00-12:00" },
  { key: "afternoon", label: "Afternoon", rangeLabel: "12:00-17:00" },
  { key: "evening", label: "Evening", rangeLabel: "17:00-22:00" },
  { key: "other", label: "Other", rangeLabel: "Outside schedule" },
];

const COMPACT_CHIP_SX = {
  height: 22,
  borderRadius: 999,
  fontSize: "0.69rem",
  "& .MuiChip-label": {
    px: 0.8,
    py: 0,
  },
} as const;

function timeToMinutes(time: string | null | undefined) {
  if (!time || time.length < 5) return null;
  const hours = Number(time.slice(0, 2));
  const minutes = Number(time.slice(3, 5));
  if (Number.isNaN(hours) || Number.isNaN(minutes)) return null;
  return hours * 60 + minutes;
}

function bucketForTime(time: string | null | undefined): TimeBucketKey {
  const minutes = timeToMinutes(time);
  if (minutes === null) return "other";
  if (minutes >= 6 * 60 && minutes < 12 * 60) return "morning";
  if (minutes >= 12 * 60 && minutes < 17 * 60) return "afternoon";
  if (minutes >= 17 * 60 && minutes < 22 * 60) return "evening";
  return "other";
}

function mondayFirstDayIndex(date: Date) {
  return (date.getUTCDay() + 6) % 7;
}

function currentDayKey(dateKey: string) {
  return DAYS[mondayFirstDayIndex(new Date(`${dateKey}T00:00:00Z`))];
}

function toIsoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function addDays(date: string, days: number) {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return toIsoDate(d);
}

function weekDates(date: string) {
  const d = new Date(`${date}T00:00:00Z`);
  const day = (d.getUTCDay() + 6) % 7;
  d.setUTCDate(d.getUTCDate() - day);
  const start = toIsoDate(d);
  return Array.from({ length: 7 }, (_, index) => addDays(start, index));
}

function dayIndex(day: string) {
  return DAYS.indexOf(day as (typeof DAYS)[number]);
}

function friendlyStatusLabel(value: string | null | undefined) {
  switch (value) {
    case "PARTIALLY_BOOKED":
      return "Partially booked";
    case "IN_CONSULTATION":
      return "In consultation";
    case "NO_SHOW":
      return "No-show";
    case "AVAILABLE":
      return "Available";
    case "CURRENT":
      return "Current";
    case "BOOKED":
      return "Booked";
    case "CANCELLED":
      return "Cancelled";
    case "COMPLETED":
      return "Completed";
    case "PAST":
      return "Past";
    case "WAITING":
      return "Checked in";
    case "BREAK":
      return "Break";
    case "LEAVE":
      return "Leave";
    case "HOLIDAY":
      return "Holiday";
    case "UNAVAILABLE":
      return "Unavailable";
    case "CONFLICTED":
      return "Conflicted";
    default:
      return value || "-";
  }
}

function slotColor(status: DoctorAvailabilitySlotStatus) {
  switch (status) {
    case "AVAILABLE":
      return "success";
    case "PARTIALLY_BOOKED":
      return "warning";
    case "FULL":
      return "error";
    case "BREAK":
      return "default";
    case "LEAVE":
      return "secondary";
    case "UNAVAILABLE":
      return "default";
    case "CONFLICTED":
      return "error";
    default:
      return "default";
  }
}

function slotStateColor(state: ReturnType<typeof getAppointmentSlotPresentation>["state"]) {
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
      return "default";
  }
}

function appointmentColor(status: Appointment["status"] | null | undefined) {
  switch (status) {
    case "BOOKED":
      return "info";
    case "WAITING":
      return "warning";
    case "IN_CONSULTATION":
      return "secondary";
    case "COMPLETED":
      return "success";
    case "CANCELLED":
      return "default";
    case "NO_SHOW":
      return "error";
    default:
      return "default";
  }
}

function sameTimeSlot(slot: DoctorAvailabilitySlot, appointment: Appointment) {
  return appointment.appointmentTime ? appointment.appointmentTime.slice(0, 5) === slot.slotTime.slice(0, 5) : false;
}

function compactDateLabel(date: string) {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: "UTC",
    weekday: "long",
    month: "short",
    day: "numeric",
  }).format(new Date(`${date}T00:00:00Z`));
}

function localDateKey(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function isPastDateTime(date: string, time: string | null | undefined, timeZone?: string | null, clinicNow?: string | null) {
  return isBookingTimePast(date, time, undefined, timeZone, clinicNow);
}

function slotPresentation(date: string, slot: DoctorAvailabilitySlot, timeZone?: string | null, clinicNow?: string | null) {
  return getAppointmentSlotPresentation(date, slot, timeZone, clinicNow);
}

function timeLabel(time: string) {
  return time.length >= 5 ? time.slice(0, 5) : time;
}

function doctorDisplayName(doctor: DoctorOption | undefined, fallback?: string | null) {
  if (doctor) return doctor.displayName;
  return fallback || "Doctor";
}

function appointmentLabel(appointment: Appointment | null) {
  if (!appointment) return "No booking";
  return appointment.patientName || appointment.patientNumber || appointment.patientId;
}

function slotTint(theme: Theme, status: string, selected: boolean) {
  const base = selected
    ? theme.palette.primary.main
    : status === "AVAILABLE" || status === "CURRENT"
      ? theme.palette.success.main
      : status === "PARTIALLY_BOOKED"
        ? theme.palette.warning.main
        : status === "FULL"
          ? theme.palette.error.main
          : status === "BREAK"
            ? theme.palette.grey[500]
            : status === "LEAVE"
        ? theme.palette.secondary.main
        : status === "PAST"
          ? theme.palette.grey[500]
        : status === "UNAVAILABLE"
                ? theme.palette.grey[600]
                : theme.palette.error.main;
  return alpha(base, selected ? 0.12 : 0.08);
}

function wrapWords(value: string | null | undefined) {
  return value || "—";
}

function quickChipLabel(count: number, label: string) {
  return `${count} ${label}`;
}

export default function DoctorAvailabilityPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";
  const canOpenConsultationWorkspace = isDoctor && auth.hasPermission("consultation.read");

  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [selectedDoctorId, setSelectedDoctorId] = React.useState("");
  const [viewMode, setViewMode] = React.useState<ViewMode>("day");
  const [date, setDate] = React.useState(() => getClinicDateKey("Asia/Kolkata"));

  const [availabilityRows, setAvailabilityRows] = React.useState<DoctorAvailability[]>([]);
  const [slotsByKey, setSlotsByKey] = React.useState<Record<string, DoctorAvailabilitySlot[]>>({});
  const [appointmentsByKey, setAppointmentsByKey] = React.useState<Record<string, Appointment[]>>({});
  const [unavailabilityByDoctor, setUnavailabilityByDoctor] = React.useState<Record<string, DoctorUnavailability[]>>({});
  const [waitlistByDoctor, setWaitlistByDoctor] = React.useState<Record<string, AppointmentWaitlist[]>>({});

  const [availabilityForm, setAvailabilityForm] = React.useState<DoctorAvailabilityInput>(EMPTY_AVAILABILITY_FORM);
  const [selectedDays, setSelectedDays] = React.useState<string[]>([EMPTY_AVAILABILITY_FORM.dayOfWeek]);
  const [unavailabilityForm, setUnavailabilityForm] = React.useState<DoctorUnavailabilityInput>(EMPTY_UNAVAILABILITY_FORM);
  const [availabilityFieldErrors, setAvailabilityFieldErrors] = React.useState<Record<string, string>>({});
  const [unavailabilityFieldErrors, setUnavailabilityFieldErrors] = React.useState<Record<string, string>>({});
  const [waitlistPatientId, setWaitlistPatientId] = React.useState("");
  const [waitlistReason, setWaitlistReason] = React.useState("");
  const [expandedAction, setExpandedAction] = React.useState<"availability" | "leave" | "waitlist" | null>("availability");
  const [sessionSearch, setSessionSearch] = React.useState("");
  const [sessionStatusFilter, setSessionStatusFilter] = React.useState<"all" | "active" | "inactive">("all");
  const [sessionOverrides, setSessionOverrides] = React.useState<Record<string, boolean>>({});

  const [filters, setFilters] = React.useState<StatusFilters>(DEFAULT_FILTERS);
  const [selectedSlot, setSelectedSlot] = React.useState<{ date: string; doctorUserId: string; slot: DoctorAvailabilitySlot } | null>(null);
  const [calendarOverrides, setCalendarOverrides] = React.useState<Record<string, boolean>>({});

  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [clinicTimeZone, setClinicTimeZone] = React.useState("Asia/Kolkata");
  const [clinicNowSnapshot, setClinicNowSnapshot] = React.useState<string | null>(null);
  const [clinicClockUnavailable, setClinicClockUnavailable] = React.useState(false);
  const [clockTick, setClockTick] = React.useState(0);

  const doctorOptions = React.useMemo<DoctorOption[]>(
    () =>
      users
        .filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR")
        .map((user) => ({
          appUserId: user.appUserId,
          displayName: user.displayName || user.email || user.appUserId,
          email: user.email,
        }))
        .sort((a, b) => a.displayName.localeCompare(b.displayName)),
    [users],
  );
  const doctorMap = React.useMemo(() => new Map([...doctorOptions.map((doctor) => [doctor.appUserId, doctor] as const)]), [doctorOptions]);
  const selectedDoctorOption = React.useMemo(() => {
    if (isDoctor) {
      return doctorMap.get(auth.appUserId || "") || { ...ALL_DOCTORS_OPTION, displayName: doctorDisplayName(undefined, auth.username || auth.appUserId || "Doctor") };
    }
    return selectedDoctorId ? doctorMap.get(selectedDoctorId) || ALL_DOCTORS_OPTION : ALL_DOCTORS_OPTION;
  }, [auth.appUserId, auth.username, doctorMap, isDoctor, selectedDoctorId]);
  const selectedDoctorLabel = isDoctor
    ? doctorDisplayName(doctorMap.get(auth.appUserId || ""), auth.username || auth.appUserId || "Doctor")
    : (selectedDoctorId ? doctorDisplayName(doctorMap.get(selectedDoctorId), selectedDoctorId) : "All Doctors");
  const effectiveDoctorIds = React.useMemo(() => {
    if (isDoctor) {
      return auth.appUserId ? [auth.appUserId] : [];
    }
    if (selectedDoctorId) {
      return [selectedDoctorId];
    }
    return doctorOptions.map((doctor) => doctor.appUserId);
  }, [auth.appUserId, doctorOptions, isDoctor, selectedDoctorId]);
  const visibleDates = React.useMemo(() => (viewMode === "day" ? [date] : weekDates(date)), [date, viewMode]);
  const visibleAvailabilityRows = React.useMemo(() => {
    if (isDoctor) {
      return availabilityRows.filter((row) => row.doctorUserId === auth.appUserId);
    }
    return selectedDoctorId ? availabilityRows.filter((row) => row.doctorUserId === selectedDoctorId) : availabilityRows;
  }, [auth.appUserId, availabilityRows, isDoctor, selectedDoctorId]);
  const clinicClock = React.useMemo(() => getClinicClockParts(clinicTimeZone, clinicNowSnapshot), [clinicNowSnapshot, clinicTimeZone, clockTick]);
  const calendarGroups = React.useMemo(() => {
    return visibleDates.map((currentDate) => {
      const rows: CalendarSlotRow[] = effectiveDoctorIds.flatMap((doctorUserId) => {
        const doctorName = doctorDisplayName(doctorMap.get(doctorUserId), doctorUserId);
        const key = `${doctorUserId}:${currentDate}`;
        const slots = [...(slotsByKey[key] || [])].filter((slot) => filters[slot.status]).sort((left, right) => left.slotTime.localeCompare(right.slotTime));
        const appointments = appointmentsByKey[key] || [];
        return slots.map((slot) => ({
          date: currentDate,
          doctorUserId,
          doctorName,
          slot,
          appointment: appointments.find((appointment) => appointment.id === slot.appointmentId)
            || appointments.find((appointment) => sameTimeSlot(slot, appointment))
            || null,
        })).filter((row) => !slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).hidden);
      }).sort((left, right) => {
        const dayDelta = left.date.localeCompare(right.date);
        if (dayDelta !== 0) return dayDelta;
        const doctorDelta = left.doctorName.localeCompare(right.doctorName);
        if (doctorDelta !== 0) return doctorDelta;
        return left.slot.slotTime.localeCompare(right.slot.slotTime);
      });
      const bookedCount = rows.filter((row) => Boolean(row.appointment)).length;
      const checkedInCount = rows.filter((row) => row.appointment?.status === "WAITING").length;
      const inConsultationCount = rows.filter((row) => row.appointment?.status === "IN_CONSULTATION").length;
      const completedCount = rows.filter((row) => row.appointment?.status === "COMPLETED").length;
      const partialCount = rows.filter((row) => slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "PARTIALLY_BOOKED").length;
      const fullCount = rows.filter((row) => slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "FULL").length;
      const availableCount = rows.filter((row) => {
        const presentation = slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot);
        return presentation.counterEligible;
      }).length;
      return {
        date: currentDate,
        rows,
        summary: { bookedCount, checkedInCount, inConsultationCount, completedCount, partialCount, fullCount, availableCount },
      };
    });
  }, [appointmentsByKey, clinicNowSnapshot, doctorMap, effectiveDoctorIds, filters, slotsByKey, visibleDates]);
  const selectedAppointment = React.useMemo(() => {
    if (!selectedSlot) return null;
    const key = `${selectedSlot.doctorUserId}:${selectedSlot.date}`;
    const appointments = appointmentsByKey[key] || [];
    return appointments.find((appointment) => appointment.id === selectedSlot.slot.appointmentId)
      || appointments.find((appointment) => sameTimeSlot(selectedSlot.slot, appointment))
      || null;
  }, [appointmentsByKey, selectedSlot]);
  const selectedWaitlist = React.useMemo(() => {
    const items = isDoctor
      ? (waitlistByDoctor[auth.appUserId || ""] || [])
      : (selectedDoctorId ? (waitlistByDoctor[selectedDoctorId] || []) : Object.values(waitlistByDoctor).flat());
    return items
      .filter((entry) => entry.preferredDate === date && entry.status === "WAITING")
      .sort((left, right) => (left.preferredStartTime || "").localeCompare(right.preferredStartTime || ""));
  }, [auth.appUserId, date, isDoctor, selectedDoctorId, waitlistByDoctor]);
  const selectedBlocks = React.useMemo(() => {
    const items = isDoctor
      ? (unavailabilityByDoctor[auth.appUserId || ""] || [])
      : (selectedDoctorId ? (unavailabilityByDoctor[selectedDoctorId] || []) : Object.values(unavailabilityByDoctor).flat());
    return [...items].sort((left, right) => left.startAt.localeCompare(right.startAt));
  }, [auth.appUserId, isDoctor, selectedDoctorId, unavailabilityByDoctor]);
  const filteredAvailabilityRows = React.useMemo(() => {
    const query = sessionSearch.trim().toLowerCase();
    return [...visibleAvailabilityRows]
      .filter((row) => {
        const doctorName = doctorDisplayName(doctorMap.get(row.doctorUserId), row.doctorUserId).toLowerCase();
        const matchesSearch = !query || doctorName.includes(query);
        const matchesStatus = sessionStatusFilter === "all" || (sessionStatusFilter === "active" ? row.active : !row.active);
        return matchesSearch && matchesStatus;
      })
      .sort((left, right) => {
        const dayDelta = dayIndex(left.dayOfWeek) - dayIndex(right.dayOfWeek);
        if (dayDelta !== 0) return dayDelta;
        const doctorDelta = doctorDisplayName(doctorMap.get(left.doctorUserId), left.doctorUserId).localeCompare(doctorDisplayName(doctorMap.get(right.doctorUserId), right.doctorUserId));
        if (doctorDelta !== 0) return doctorDelta;
        return left.startTime.localeCompare(right.startTime);
      });
  }, [doctorMap, sessionSearch, sessionStatusFilter, visibleAvailabilityRows]);
  const sessionBuckets = React.useMemo<DaySessionBucket[]>(() => {
    const today = currentDayKey(clinicClock.dateKey);
    return DAYS.map((dayOfWeek) => {
      const dayRows = filteredAvailabilityRows.filter((row) => row.dayOfWeek === dayOfWeek);
      const groupedByDoctor = new Map<string, DoctorSessionGroup>();
      for (const row of dayRows) {
        const doctorUserId = row.doctorUserId || "";
        if (!groupedByDoctor.has(doctorUserId)) {
          groupedByDoctor.set(doctorUserId, {
            doctorUserId,
            doctorName: doctorDisplayName(doctorMap.get(doctorUserId), doctorUserId),
            rows: [],
            activeCount: 0,
            inactiveCount: 0,
            totalCount: 0,
          });
        }
        const group = groupedByDoctor.get(doctorUserId)!;
        group.rows.push(row);
        group.totalCount += 1;
        if (row.active) group.activeCount += 1;
        else group.inactiveCount += 1;
      }
      const rows = Array.from(groupedByDoctor.values()).map((group) => ({
        ...group,
        rows: [...group.rows].sort((left, right) => left.startTime.localeCompare(right.startTime)),
      })).sort((left, right) => left.doctorName.localeCompare(right.doctorName));
      const doctorsCount = rows.length;
      const sessionsCount = dayRows.length;
      const activeCount = dayRows.filter((row) => row.active).length;
      const inactiveCount = dayRows.length - activeCount;
      const hasActive = activeCount > 0;
      const autoExpanded = dayOfWeek === today || hasActive;
      const expanded = sessionOverrides[dayOfWeek] ?? autoExpanded;
      return {
        dayOfWeek,
        rows,
        doctorsCount,
        sessionsCount,
        activeCount,
        inactiveCount,
        autoExpanded,
        expanded,
      };
    }).filter((group) => group.sessionsCount > 0);
  }, [clinicClock.dateKey, doctorMap, filteredAvailabilityRows, sessionOverrides]);
  const calendarGroupsWithBuckets = React.useMemo(() => {
    return calendarGroups.map((group) => {
      const rowsByBucket = new Map<TimeBucketKey, CalendarSlotRow[]>(
        TIME_BUCKETS.map((bucket) => [bucket.key, [] as CalendarSlotRow[]]),
      );
      for (const row of group.rows) {
        rowsByBucket.get(bucketForTime(row.slot.slotTime))?.push(row);
      }
      const hasBookings = group.rows.some((row) => Boolean(row.appointment) || row.slot.bookedCount > 0 || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "PARTIALLY_BOOKED" || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "FULL");
      const firstBucketKey = TIME_BUCKETS.find((bucket) => (rowsByBucket.get(bucket.key) || []).length > 0)?.key || "morning";
      return {
        ...group,
        buckets: TIME_BUCKETS.map((bucket) => {
          const rows = [...(rowsByBucket.get(bucket.key) || [])].sort((left, right) => {
            const doctorDelta = left.doctorName.localeCompare(right.doctorName);
            if (doctorDelta !== 0) return doctorDelta;
            return left.slot.slotTime.localeCompare(right.slot.slotTime);
          });
          const summary = {
            totalSlots: rows.length,
            bookedCount: rows.filter((row) => Boolean(row.appointment)).length,
            partialCount: rows.filter((row) => slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "PARTIALLY_BOOKED").length,
            fullCount: rows.filter((row) => slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "FULL").length,
            availableCount: rows.filter((row) => slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).counterEligible).length,
            checkedInCount: rows.filter((row) => row.appointment?.status === "WAITING").length,
            inConsultationCount: rows.filter((row) => row.appointment?.status === "IN_CONSULTATION").length,
          };
          const bucketHasBookings = rows.some((row) => Boolean(row.appointment) || row.slot.bookedCount > 0 || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "PARTIALLY_BOOKED" || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "FULL");
          const defaultExpanded = Boolean(group.date === date && bucket.key === bucketForTime(clinicClock.timeKey)) || bucketHasBookings || bucket.key === firstBucketKey;
          const expanded = calendarOverrides[`${group.date}:${bucket.key}`] ?? defaultExpanded;
          return {
            ...bucket,
            rows,
            summary,
            hasBookings: bucketHasBookings,
            autoExpanded: defaultExpanded,
            expanded,
          } satisfies CalendarBucket;
        }).filter((bucket) => bucket.rows.length > 0 || bucket.key === "other" && hasBookings),
      };
    });
  }, [calendarGroups, calendarOverrides, clinicClock.minutes, date]);

  const loadStatic = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const [userRows, allAvailability] = await Promise.all([
      getClinicUsers(auth.accessToken, auth.tenantId),
      getDoctorAvailability(auth.accessToken, auth.tenantId),
    ]);
    setUsers(userRows);
    setAvailabilityRows(allAvailability);
  }, [auth.accessToken, auth.tenantId]);

  const loadDynamic = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId) return;
    if (effectiveDoctorIds.length === 0) {
      setSlotsByKey({});
      setAppointmentsByKey({});
      setWaitlistByDoctor({});
      setUnavailabilityByDoctor({});
      return;
    }
    setLoading(true);
    try {
      const slotEntries = await Promise.all(
        effectiveDoctorIds.flatMap((doctorUserId) =>
          visibleDates.map(async (currentDate) => {
            const key = `${doctorUserId}:${currentDate}`;
            const slotRows = await getDoctorSlots(token, tenantId, doctorUserId, currentDate);
            return [key, slotRows] as const;
          }),
        ),
      );
      const appointmentEntries = await Promise.all(
        effectiveDoctorIds.flatMap((doctorUserId) =>
          visibleDates.map(async (currentDate) => {
            const key = `${doctorUserId}:${currentDate}`;
            const appointmentRows = await searchAppointments(token, tenantId, { doctorUserId, appointmentDate: currentDate });
            return [key, appointmentRows] as const;
          }),
        ),
      );
      const waitlistEntries = await Promise.all(
        effectiveDoctorIds.map(async (doctorUserId) => {
          const rows = await getWaitlist(token, tenantId, { doctorUserId, preferredDate: date, status: "WAITING" as WaitlistStatus });
          return [doctorUserId, rows] as const;
        }),
      );
      const blockEntries = await Promise.all(
        effectiveDoctorIds.map(async (doctorUserId) => {
          const rows = await getDoctorUnavailability(token, tenantId, doctorUserId);
          return [doctorUserId, rows] as const;
        }),
      );
      setSlotsByKey(Object.fromEntries(slotEntries));
      setAppointmentsByKey(Object.fromEntries(appointmentEntries));
      setWaitlistByDoctor(Object.fromEntries(waitlistEntries));
      setUnavailabilityByDoctor(Object.fromEntries(blockEntries));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load doctor schedule");
      setSlotsByKey({});
      setAppointmentsByKey({});
      setWaitlistByDoctor({});
      setUnavailabilityByDoctor({});
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, date, effectiveDoctorIds, visibleDates]);

  React.useEffect(() => {
    void loadStatic();
  }, [loadStatic]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinicTimeZone() {
      if (!auth.accessToken || !auth.tenantId) {
        setClinicTimeZone("Asia/Kolkata");
        setClinicNowSnapshot(null);
        setClinicClockUnavailable(true);
        console.info("[doctor-availability] clinic clock unavailable", {
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
          setClinicTimeZone(settings.clinicTimeZone?.trim() || "Asia/Kolkata");
          setClinicNowSnapshot(settings.clinicNow || null);
          setClinicClockUnavailable(false);
          console.info("[doctor-availability] clinic clock loaded", {
            tenantId: auth.tenantId,
            clinicTimeZone: settings.clinicTimeZone,
            clinicNow: settings.clinicNow,
            serverNowUtc: settings.serverNowUtc,
            effectiveTimezone: settings.clinicTimeZone?.trim() || "Asia/Kolkata",
            browserReportedTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          });
        }
      } catch (error) {
        if (!cancelled) {
          setClinicTimeZone("Asia/Kolkata");
          setClinicNowSnapshot(null);
          setClinicClockUnavailable(true);
          console.info("[doctor-availability] clinic clock unavailable", {
            tenantId: auth.tenantId,
            source: "clock-error",
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
    void loadDynamic();
  }, [loadDynamic]);

  React.useEffect(() => {
    if (isDoctor) return;
    if (selectedDoctorId && !doctorOptions.some((doctor) => doctor.appUserId === selectedDoctorId)) {
      setSelectedDoctorId("");
    }
  }, [doctorOptions, isDoctor, selectedDoctorId]);

  React.useEffect(() => {
    setSelectedSlot(null);
  }, [date, selectedDoctorId, viewMode]);

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;

  const quickCreateAvailability = async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorIds.length) return;
    const parsed = doctorAvailabilitySchema.safeParse(availabilityForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setAvailabilityFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = ["startTime", "endTime", "consultationDurationMinutes", "maxPatientsPerSlot", "breakStartTime", "breakEndTime"].find((field) => errors[field]);
        document.getElementById(firstField ? `availability-${firstField}` : "availability-startTime")?.focus();
      }, 0);
      return;
    }
    try {
      const days = selectedDays.length > 0 ? selectedDays : [availabilityForm.dayOfWeek];
      const doctorUserId = isDoctor ? auth.appUserId : selectedDoctorId;
      if (!doctorUserId) {
        setError("Select a specific doctor to add availability.");
        return;
      }
      const results = await Promise.allSettled(
        days.map((dayOfWeek) => createDoctorAvailability(auth.accessToken!, auth.tenantId!, doctorUserId, {
          ...parsed.data,
          dayOfWeek,
          breakStartTime: parsed.data.breakStartTime ?? null,
          breakEndTime: parsed.data.breakEndTime ?? null,
        })),
      );
      const successDays = results
        .map((result, index) => (result.status === "fulfilled" ? days[index] : null))
        .filter((day): day is string => Boolean(day));
      const failureMessages = results
        .map((result, index) => (result.status === "rejected" ? `${days[index]}: ${result.reason instanceof Error ? result.reason.message : "Failed to add availability"}` : null))
        .filter((message): message is string => Boolean(message));
      if (successDays.length > 0) {
        setInfo(`Availability added for ${successDays.join(", ")}`);
        setAvailabilityFieldErrors({});
      }
      if (failureMessages.length > 0) {
        setError(failureMessages.join(" "));
      }
      await loadStatic();
      await loadDynamic();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add availability");
    }
  };

  const toggleAvailabilityStatus = async (row: DoctorAvailability) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const updated = await updateDoctorAvailability(auth.accessToken, auth.tenantId, row.id, {
        dayOfWeek: row.dayOfWeek,
        startTime: row.startTime,
        endTime: row.endTime,
        breakStartTime: row.breakStartTime,
        breakEndTime: row.breakEndTime,
        consultationDurationMinutes: row.consultationDurationMinutes,
        maxPatientsPerSlot: row.maxPatientsPerSlot,
        active: !row.active,
      });
      setAvailabilityRows((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      setInfo(updated.active ? "Availability session activated" : "Availability session deactivated");
      await loadDynamic();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to update availability status");
    }
  };

  const quickCreateUnavailability = async () => {
    const doctorUserId = isDoctor ? auth.appUserId : selectedDoctorId;
    if (!auth.accessToken || !auth.tenantId || !doctorUserId) return;
    const parsed = doctorUnavailabilitySchema.safeParse(unavailabilityForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setUnavailabilityFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = ["startAt", "endAt", "type", "reason"].find((field) => errors[field]);
        document.getElementById(firstField ? `unavailability-${firstField}` : "unavailability-startAt")?.focus();
      }, 0);
      return;
    }
    try {
      await createDoctorUnavailability(auth.accessToken, auth.tenantId, doctorUserId, {
        ...parsed.data,
        reason: parsed.data.reason ?? null,
      });
      setInfo("Leave/unavailable block added");
      setUnavailabilityFieldErrors({});
      await loadDynamic();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add leave/unavailable block");
    }
  };

  const quickAddWaitlist = async () => {
    const doctorUserId = isDoctor ? auth.appUserId : selectedDoctorId;
    if (!auth.accessToken || !auth.tenantId || !doctorUserId || !waitlistPatientId.trim()) return;
    try {
      await createWaitlist(auth.accessToken, auth.tenantId, {
        patientId: waitlistPatientId.trim(),
        doctorUserId,
        preferredDate: date,
        preferredStartTime: selectedSlot?.slot.slotTime || null,
        preferredEndTime: selectedSlot?.slot.slotEndTime || null,
        reason: waitlistReason.trim() || null,
        notes: null,
      });
      setWaitlistPatientId("");
      setWaitlistReason("");
      setInfo("Waitlist entry added");
      await loadDynamic();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add waitlist entry");
    }
  };

  const selectSlot = (row: CalendarSlotRow) => {
    const presentation = slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot);
    if (presentation.isPast || !presentation.selectable) {
      return;
    }
    setSelectedSlot({ date: row.date, doctorUserId: row.doctorUserId, slot: row.slot });
  };

  const doctorScopeLabel = isDoctor ? selectedDoctorLabel : (selectedDoctorId ? selectedDoctorLabel : "All Doctors");
  const canMutateSchedule = Boolean(isDoctor ? auth.appUserId : selectedDoctorId);
  const selectedSlotBookingReason = React.useMemo(() => {
    if (!selectedSlot) return "Select an available slot";
    if (!auth.accessToken || !auth.tenantId) return "Clinic context is unavailable";
    const presentation = slotPresentation(selectedSlot.date, selectedSlot.slot, clinicTimeZone, clinicNowSnapshot);
    if (presentation.isPast) return "Selected time has already passed. Please choose a current or future slot.";
    if (!presentation.bookable) return presentation.tooltip;
    return null;
  }, [auth.accessToken, auth.tenantId, clinicNowSnapshot, clinicTimeZone, selectedSlot]);
  const selectedSlotCanBook = Boolean(selectedSlot && !selectedSlotBookingReason);
  const openBookingFlow = () => {
    if (!selectedSlot || !selectedSlotCanBook) return;
    const params = new URLSearchParams({
      doctorUserId: selectedSlot.doctorUserId,
      appointmentDate: selectedSlot.date,
      appointmentTime: timeLabel(selectedSlot.slot.slotTime),
    });
    navigate(`/appointments?${params.toString()}`);
  };

  return (
    <Stack spacing={1.75}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start", flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, letterSpacing: -0.4, mb: 0.5 }}>
            Doctor Availability
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Operational scheduling console for availability, booking visibility, waitlist handling, and leave / block management.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
          <Chip size="small" label={clinicClockUnavailable ? "Clinic time unavailable" : formatClinicClockLabel(clinicTimeZone, clinicNowSnapshot)} variant="outlined" sx={COMPACT_CHIP_SX} />
          <Button variant="outlined" onClick={() => navigate("/appointments")}>Open appointments</Button>
          <Button variant="outlined" onClick={() => navigate("/appointments/day-board")}>Open day board</Button>
          <Button variant="contained" onClick={() => navigate("/queue")}>Open queue</Button>
        </Stack>
      </Box>

      <WorkflowStrip steps={DOCTOR_AVAILABILITY_WORKFLOW_STEPS} />

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}

      <Grid container spacing={1.5} alignItems="stretch">
        <Grid size={{ xs: 12, lg: 3 }}>
          <Stack spacing={1.5}>
            <Card>
              <CardContent>
                <Stack spacing={1.5}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Scheduler Controls</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {isDoctor ? "Your calendar is fixed to your doctor profile." : "Choose All Doctors for overview or a specific doctor to manage sessions."}
                    </Typography>
                  </Box>
                  {!isDoctor ? (
                    <Autocomplete
                      options={[ALL_DOCTORS_OPTION, ...doctorOptions]}
                      value={selectedDoctorOption}
                      onChange={(_, option) => {
                        setSelectedDoctorId(option?.appUserId || "");
                        setInfo(null);
                      }}
                      getOptionLabel={(option) => option.displayName}
                      isOptionEqualToValue={(option, value) => option.appUserId === value.appUserId}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          label="Doctor"
                          helperText="Search and choose All Doctors for a full overview."
                        />
                      )}
                      size="small"
                    />
                  ) : (
                    <Alert severity="info" sx={{ py: 0.5 }}>Viewing {selectedDoctorLabel}.</Alert>
                  )}
                  <TextField
                    fullWidth
                    size="small"
                    type="date"
                    label="Date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    InputLabelProps={{ shrink: true }}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button
                      fullWidth
                      variant={viewMode === "day" ? "contained" : "outlined"}
                      onClick={() => setViewMode("day")}
                    >
                      Day
                    </Button>
                    <Button
                      fullWidth
                      variant={viewMode === "week" ? "contained" : "outlined"}
                      onClick={() => setViewMode("week")}
                    >
                      Week
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Status filters</Typography>
                  <Stack direction="row" gap={0.5} flexWrap="wrap">
                    {(Object.keys(filters) as Array<keyof StatusFilters>).map((key) => (
                      <Chip
                        key={key}
                        clickable
                        label={friendlyStatusLabel(key)}
                        color={filters[key] ? "primary" : "default"}
                        variant={filters[key] ? "filled" : "outlined"}
                        onClick={() => setFilters((current) => ({ ...current, [key]: !current[key] }))}
                        sx={COMPACT_CHIP_SX}
                      />
                    ))}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            <Card>
            <CardContent sx={{ p: 1.25, pb: 1.25 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Quick Actions</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                  Select a specific doctor to create sessions, leave blocks, or waitlist entries.
                </Typography>
                <Stack spacing={1}>
                  <Accordion expanded={expandedAction === "availability"} onChange={(_, expanded) => setExpandedAction(expanded ? "availability" : null)}>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>Add Availability</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Multi-day schedule creation with compact pills.
                        </Typography>
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1.5}>
                        <Stack spacing={0.75}>
                          <Stack direction="row" spacing={0.5} flexWrap="wrap">
                            <Chip size="small" label="All Weekdays" clickable variant="outlined" onClick={() => { setSelectedDays([...WEEKDAY_DAYS]); setAvailabilityForm((current) => ({ ...current, dayOfWeek: WEEKDAY_DAYS[0] })); }} sx={COMPACT_CHIP_SX} />
                            <Chip size="small" label="Weekend" clickable variant="outlined" onClick={() => { setSelectedDays([...WEEKEND_DAYS]); setAvailabilityForm((current) => ({ ...current, dayOfWeek: WEEKEND_DAYS[0] })); }} sx={COMPACT_CHIP_SX} />
                            <Chip size="small" label="Clear All" clickable variant="outlined" onClick={() => setSelectedDays([])} sx={COMPACT_CHIP_SX} />
                          </Stack>
                          <Stack direction="row" spacing={0.5} flexWrap="wrap">
                            {DAYS.map((day) => (
                              <Chip
                                key={day}
                                size="small"
                                label={day.slice(0, 3)}
                                clickable
                                color={selectedDays.includes(day) ? "primary" : "default"}
                                variant={selectedDays.includes(day) ? "filled" : "outlined"}
                                onClick={() => {
                                  setSelectedDays((current) => current.includes(day) ? current.filter((item) => item !== day) : [...current, day]);
                                  setAvailabilityForm((current) => ({ ...current, dayOfWeek: day }));
                                }}
                                sx={COMPACT_CHIP_SX}
                              />
                            ))}
                          </Stack>
                          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                            Selected: {selectedDays.length ? selectedDays.join(", ") : "No days selected"}
                          </Typography>
                        </Stack>
                        <Grid container spacing={1}>
                          <Grid size={{ xs: 6 }}><TextField id="availability-startTime" size="small" fullWidth type="time" label={<RequiredLabel text="Start" required />} value={availabilityForm.startTime} onChange={(e) => setAvailabilityForm((current) => ({ ...current, startTime: e.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(availabilityFieldErrors.startTime)} helperText={availabilityFieldErrors.startTime || "Required."} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField id="availability-endTime" size="small" fullWidth type="time" label={<RequiredLabel text="End" required />} value={availabilityForm.endTime} onChange={(e) => setAvailabilityForm((current) => ({ ...current, endTime: e.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(availabilityFieldErrors.endTime)} helperText={availabilityFieldErrors.endTime || "Must be after start."} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField id="availability-consultationDurationMinutes" size="small" fullWidth type="number" label={<RequiredLabel text="Duration" required />} value={availabilityForm.consultationDurationMinutes} onChange={(e) => setAvailabilityForm((current) => ({ ...current, consultationDurationMinutes: Number(e.target.value) }))} required error={Boolean(availabilityFieldErrors.consultationDurationMinutes)} helperText={availabilityFieldErrors.consultationDurationMinutes || "Positive integer."} inputProps={{ min: 1, step: 1 }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField id="availability-maxPatientsPerSlot" size="small" fullWidth type="number" label={<RequiredLabel text="Capacity" required />} value={availabilityForm.maxPatientsPerSlot || 1} onChange={(e) => setAvailabilityForm((current) => ({ ...current, maxPatientsPerSlot: Number(e.target.value) }))} required error={Boolean(availabilityFieldErrors.maxPatientsPerSlot)} helperText={availabilityFieldErrors.maxPatientsPerSlot || "Positive integer."} inputProps={{ min: 1, step: 1 }} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField id="availability-breakStartTime" size="small" fullWidth type="time" label="Break start" value={availabilityForm.breakStartTime || ""} onChange={(e) => setAvailabilityForm((current) => ({ ...current, breakStartTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} error={Boolean(availabilityFieldErrors.breakStartTime)} helperText={availabilityFieldErrors.breakStartTime} /></Grid>
                          <Grid size={{ xs: 6 }}><TextField id="availability-breakEndTime" size="small" fullWidth type="time" label="Break end" value={availabilityForm.breakEndTime || ""} onChange={(e) => setAvailabilityForm((current) => ({ ...current, breakEndTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} error={Boolean(availabilityFieldErrors.breakEndTime)} helperText={availabilityFieldErrors.breakEndTime} /></Grid>
                        </Grid>
                        <Button onClick={() => void quickCreateAvailability()} disabled={!canMutateSchedule} variant="contained">
                          Add availability
                        </Button>
                      </Stack>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion expanded={expandedAction === "leave"} onChange={(_, expanded) => setExpandedAction(expanded ? "leave" : null)}>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>Leave / Block</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Mark leave, holiday, unavailable, or emergency block.
                        </Typography>
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1.25}>
                        <TextField
                          id="unavailability-startAt"
                          size="small"
                          fullWidth
                          type="datetime-local"
                          label={<RequiredLabel text="Start" required />}
                          value={unavailabilityForm.startAt.slice(0, 16)}
                          onChange={(e) => setUnavailabilityForm((current) => ({ ...current, startAt: e.target.value ? `${e.target.value}:00` : "" }))}
                          InputLabelProps={{ shrink: true }}
                          required
                          error={Boolean(unavailabilityFieldErrors.startAt)}
                          helperText={unavailabilityFieldErrors.startAt || "Required."}
                        />
                        <TextField
                          id="unavailability-endAt"
                          size="small"
                          fullWidth
                          type="datetime-local"
                          label={<RequiredLabel text="End" required />}
                          value={unavailabilityForm.endAt.slice(0, 16)}
                          onChange={(e) => setUnavailabilityForm((current) => ({ ...current, endAt: e.target.value ? `${e.target.value}:00` : "" }))}
                          InputLabelProps={{ shrink: true }}
                          required
                          error={Boolean(unavailabilityFieldErrors.endAt)}
                          helperText={unavailabilityFieldErrors.endAt || "Must be after start."}
                        />
                        <FormControl fullWidth size="small">
                          <InputLabel id="quick-unavail-type"><RequiredLabel text="Type" required /></InputLabel>
                          <Select
                            id="unavailability-type"
                            labelId="quick-unavail-type"
                            label="Type"
                            value={unavailabilityForm.type}
                            onChange={(e) => setUnavailabilityForm((current) => ({ ...current, type: e.target.value as DoctorUnavailabilityType }))}
                            error={Boolean(unavailabilityFieldErrors.type)}
                          >
                            <MenuItem value="LEAVE">Leave</MenuItem>
                            <MenuItem value="HOLIDAY">Holiday</MenuItem>
                            <MenuItem value="UNAVAILABLE">Unavailable</MenuItem>
                            <MenuItem value="EMERGENCY_BLOCK">Emergency block</MenuItem>
                          </Select>
                        </FormControl>
                        <TextField id="unavailability-reason" size="small" fullWidth label="Reason" value={unavailabilityForm.reason || ""} onChange={(e) => setUnavailabilityForm((current) => ({ ...current, reason: e.target.value || null }))} error={Boolean(unavailabilityFieldErrors.reason)} helperText={unavailabilityFieldErrors.reason || "Optional, max 250 characters."} />
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Button
                          variant="outlined"
                          onClick={() => setUnavailabilityForm((current) => {
                              const day = (current.startAt || `${getClinicDateKey(clinicTimeZone, clinicNowSnapshot)}T00:00:00`).slice(0, 10);
                              const end = new Date(`${day}T00:00:00Z`);
                              end.setUTCDate(end.getUTCDate() + 1);
                              return {
                                ...current,
                                startAt: `${day}T00:00:00`,
                                endAt: `${end.toISOString().slice(0, 10)}T00:00:00`,
                              };
                            })}
                          >
                            Full day
                          </Button>
                          {selectedSlot ? (
                            <Button
                              variant="outlined"
                              onClick={() => setUnavailabilityForm((current) => ({
                                ...current,
                                startAt: `${selectedSlot.date}T${selectedSlot.slot.slotTime}:00`,
                                endAt: `${selectedSlot.date}T${selectedSlot.slot.slotEndTime}:00`,
                              }))}
                            >
                              Use selected slot
                            </Button>
                          ) : null}
                        </Stack>
                        <Button variant="outlined" onClick={() => void quickCreateUnavailability()} disabled={!canMutateSchedule}>
                          Add block
                        </Button>
                      </Stack>
                    </AccordionDetails>
                  </Accordion>

                  <Accordion expanded={expandedAction === "waitlist"} onChange={(_, expanded) => setExpandedAction(expanded ? "waitlist" : null)}>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>Waitlist</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Add a waitlist entry for the selected doctor and date.
                        </Typography>
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1.25}>
                        <TextField size="small" fullWidth label="Patient ID" value={waitlistPatientId} onChange={(e) => setWaitlistPatientId(e.target.value)} />
                        <TextField size="small" fullWidth label="Reason" value={waitlistReason} onChange={(e) => setWaitlistReason(e.target.value)} />
                        <Button variant="outlined" onClick={() => void quickAddWaitlist()} disabled={!canMutateSchedule}>
                          Add waitlist entry
                        </Button>
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Stack spacing={1.5}>
            <Card sx={{ minHeight: 240 }}>
              <CardContent sx={{ p: 1.25, pb: 1.25 }}>
                <Stack spacing={1.1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "flex-start" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>Operational Calendar</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Grouped schedule for {doctorScopeLabel}. Expand a day, then expand the time bucket you need.
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.rows.length, 0), "slots")} variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.summary.availableCount, 0), "available")} color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.summary.partialCount, 0), "partial")} color="warning" variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.summary.fullCount, 0), "full")} color="error" variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.summary.checkedInCount, 0), "checked in")} color="info" variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={quickChipLabel(calendarGroups.reduce((sum, group) => sum + group.summary.inConsultationCount, 0), "in consult")} color="secondary" variant="outlined" sx={COMPACT_CHIP_SX} />
                    </Stack>
                  </Box>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    <Chip size="small" label="Available" color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                    <Chip size="small" label="Partially booked" color="warning" variant="outlined" sx={COMPACT_CHIP_SX} />
                    <Chip size="small" label="Full" color="error" variant="outlined" sx={COMPACT_CHIP_SX} />
                    <Chip size="small" label="Break" variant="outlined" sx={COMPACT_CHIP_SX} />
                    <Chip size="small" label="Leave" color="secondary" variant="outlined" sx={COMPACT_CHIP_SX} />
                  </Stack>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap">
                    <Button size="small" variant="outlined" onClick={() => setCalendarOverrides(() => {
                      const next: Record<string, boolean> = {};
                      for (const group of calendarGroupsWithBuckets) {
                        for (const bucket of group.buckets) {
                          next[`${group.date}:${bucket.key}`] = true;
                        }
                      }
                      return next;
                    })}>
                      Expand all
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => setCalendarOverrides(() => {
                      const next: Record<string, boolean> = {};
                      for (const group of calendarGroupsWithBuckets) {
                        for (const bucket of group.buckets) {
                          next[`${group.date}:${bucket.key}`] = false;
                        }
                      }
                      return next;
                    })}>
                      Collapse all
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => setDate(clinicClock.dateKey)}>
                      Jump to now
                    </Button>
                  </Stack>

                  {loading && calendarGroups.every((group) => group.rows.length === 0) ? (
                    <CompactEmptyState title="Loading doctor schedule…" subtitle="Fetching availability and booking visibility." />
                  ) : calendarGroups.length === 0 ? (
                    <CompactEmptyState title="No schedule available" subtitle="The selected scope has no visible sessions." />
                  ) : (
                    <Stack spacing={1}>
                      {calendarGroupsWithBuckets.map((group) => (
                        <Accordion
                          key={group.date}
                          expanded={Boolean(calendarOverrides[`day:${group.date}`] ?? (group.date === date || group.summary.bookedCount > 0))}
                          onChange={(_, expanded) => setCalendarOverrides((current) => ({ ...current, [`day:${group.date}`]: expanded }))}
                          disableGutters
                          sx={{
                            border: "1px solid",
                            borderColor: "divider",
                            borderRadius: 2,
                            overflow: "hidden",
                            "&:before": { display: "none" },
                            "& .MuiAccordionSummary-root": { minHeight: 48, px: 1.1 },
                            "& .MuiAccordionSummary-content": { my: 0.55 },
                            "& .MuiAccordionDetails-root": { px: 1.1, pb: 1.1, pt: 0 },
                          }}
                        >
                          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Stack spacing={0.35} sx={{ width: "100%" }}>
                              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                                <Box>
                                  <Typography sx={{ fontWeight: 800 }}>{compactDateLabel(group.date)}</Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {group.rows.length} visible rows • {group.summary.availableCount} available • {group.summary.partialCount} partial • {group.summary.fullCount} full
                                  </Typography>
                                </Box>
                                <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                                  <Chip size="small" label={quickChipLabel(group.summary.bookedCount, "booked")} variant="outlined" sx={COMPACT_CHIP_SX} />
                                  <Chip size="small" label={quickChipLabel(group.summary.checkedInCount, "checked in")} color="info" variant="outlined" sx={COMPACT_CHIP_SX} />
                                  <Chip size="small" label={quickChipLabel(group.summary.inConsultationCount, "in consult")} color="secondary" variant="outlined" sx={COMPACT_CHIP_SX} />
                                  <Chip size="small" label={quickChipLabel(group.summary.completedCount, "completed")} color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                                </Stack>
                              </Box>
                            </Stack>
                          </AccordionSummary>
                          <AccordionDetails>
                            <Stack spacing={0.8}>
                              {group.buckets.map((bucket) => (
                                <Accordion
                                  key={`${group.date}:${bucket.key}`}
                                  expanded={bucket.expanded}
                                  onChange={(_, expanded) => setCalendarOverrides((current) => ({ ...current, [`${group.date}:${bucket.key}`]: expanded }))}
                                  disableGutters
                                  sx={{
                                    border: "1px solid",
                                    borderColor: "divider",
                                    borderRadius: 2,
                                    overflow: "hidden",
                                    "&:before": { display: "none" },
                                    "& .MuiAccordionSummary-root": { minHeight: 42, px: 1 },
                                    "& .MuiAccordionSummary-content": { my: 0.5 },
                                    "& .MuiAccordionDetails-root": { px: 1, pb: 1, pt: 0 },
                                  }}
                                >
                                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, width: "100%", flexWrap: "wrap" }}>
                                      <Box>
                                        <Typography sx={{ fontWeight: 800 }}>{bucket.label}</Typography>
                                        <Typography variant="caption" color="text.secondary">
                                          {bucket.rangeLabel} • {bucket.summary.totalSlots} slots • {bucket.summary.bookedCount} booked • {bucket.summary.fullCount} full
                                        </Typography>
                                      </Box>
                                      <Stack direction="row" spacing={0.4} flexWrap="wrap" justifyContent="flex-end">
                                        <Chip size="small" label={`${bucket.summary.availableCount} available`} color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                                        <Chip size="small" label={`${bucket.summary.checkedInCount} checked in`} color="info" variant="outlined" sx={COMPACT_CHIP_SX} />
                                        <Chip size="small" label={`${bucket.summary.inConsultationCount} in consult`} color="secondary" variant="outlined" sx={COMPACT_CHIP_SX} />
                                      </Stack>
                                    </Box>
                                  </AccordionSummary>
                                  <AccordionDetails>
                                    <Box
                                      sx={{
                                        maxHeight: 360,
                                        overflowX: "auto",
                                        overflowY: "auto",
                                        scrollbarGutter: "stable both-edges",
                                        pr: 1.5,
                                      }}
                                    >
                                      <Table size="small" sx={{ minWidth: isDoctor || selectedDoctorId ? 720 : 900, tableLayout: "fixed" }}>
                                        <TableHead>
                                          <TableRow>
                                            <TableCell sx={{ width: 132, fontWeight: 800, py: 0.75 }}>Time</TableCell>
                                            {!isDoctor && !selectedDoctorId ? <TableCell sx={{ width: 180, fontWeight: 800, py: 0.75 }}>Doctor</TableCell> : null}
                                            <TableCell sx={{ width: 130, fontWeight: 800, py: 0.75 }}>Status</TableCell>
                                            <TableCell sx={{ width: 100, fontWeight: 800, py: 0.75 }}>Capacity</TableCell>
                                            <TableCell sx={{ fontWeight: 800, py: 0.75 }}>Bookings</TableCell>
                                            <TableCell align="right" sx={{ width: 84, fontWeight: 800, py: 0.75 }}>Action</TableCell>
                                          </TableRow>
                                        </TableHead>
                                        <TableBody>
                                          {bucket.rows.length === 0 ? (
                                            <TableRow>
                                              <TableCell colSpan={isDoctor || selectedDoctorId ? 5 : 6} sx={{ py: 1.5 }}>
                                                <Typography variant="body2" color="text.secondary">—</Typography>
                                              </TableCell>
                                            </TableRow>
                                          ) : bucket.rows.map((row) => {
                                            const presentation = slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot);
                                            const selected = selectedSlot?.date === row.date && selectedSlot.doctorUserId === row.doctorUserId && selectedSlot.slot.slotTime === row.slot.slotTime;
                                            const rowState = presentation.state;
                                            return (
                                              <TableRow
                                                key={`${row.date}-${row.doctorUserId}-${row.slot.slotTime}-${row.slot.slotEndTime}`}
                                                hover
                                                onClick={() => {
                                                  if (!presentation.isPast && presentation.selectable) {
                                                    selectSlot(row);
                                                  }
                                                }}
                                                sx={{
                                                  cursor: presentation.selectable ? "pointer" : "default",
                                                  bgcolor: (theme) => slotTint(theme, rowState, selected),
                                                  "& td": { borderColor: selected ? "primary.main" : "divider" },
                                                  opacity: presentation.isPast ? 0.7 : 1,
                                                }}
                                              >
                                                <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 700, py: 0.75 }}>
                                                  {timeLabel(row.slot.slotTime)} - {timeLabel(row.slot.slotEndTime)}
                                                </TableCell>
                                                {!isDoctor && !selectedDoctorId ? <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>{row.doctorName}</TableCell> : null}
                                                <TableCell sx={{ py: 0.75 }}>
                                                  <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                                                    <Chip size="small" label={friendlyStatusLabel(rowState)} color={slotStateColor(rowState)} variant="outlined" sx={COMPACT_CHIP_SX} />
                                                    {presentation.selectable ? <Chip size="small" label="Selectable" variant="outlined" sx={COMPACT_CHIP_SX} /> : null}
                                                  </Stack>
                                                </TableCell>
                                                <TableCell sx={{ whiteSpace: "nowrap", py: 0.75, pr: 1 }}>
                                                  {row.slot.bookedCount}/{row.slot.maxPatientsPerSlot}
                                                </TableCell>
                                                <TableCell sx={{ py: 0.75 }}>
                                                  <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                                                    {row.appointment ? (
                                                      <Tooltip
                                                        arrow
                                                        placement="top-start"
                                                        title={
                                                          <Box sx={{ p: 0.5 }}>
                                                            <Typography sx={{ fontWeight: 800 }}>{appointmentLabel(row.appointment)}</Typography>
                                                            <Typography variant="body2">Doctor: {doctorDisplayName(doctorMap.get(row.doctorUserId), row.doctorName)}</Typography>
                                                            <Typography variant="body2">Type: {row.appointment.type}</Typography>
                                                            <Typography variant="body2">Status: {friendlyStatusLabel(row.appointment.status)}</Typography>
                                                            <Typography variant="body2">Phone: {row.appointment.patientMobile || "—"}</Typography>
                                                          </Box>
                                                        }
                                                      >
                                                        <Chip
                                                          size="small"
                                                          label={`${appointmentLabel(row.appointment)} • ${friendlyStatusLabel(row.appointment.status)}`}
                                                          color={appointmentColor(row.appointment.status)}
                                                          variant="outlined"
                                                          sx={COMPACT_CHIP_SX}
                                                        />
                                                      </Tooltip>
                                                    ) : null}
                                                    {!row.appointment && row.slot.bookedCount > 0 ? (
                                                      <Chip size="small" label={`${row.slot.bookedCount} booked`} color="warning" variant="outlined" sx={COMPACT_CHIP_SX} />
                                                    ) : null}
                                                    {!row.appointment && rowState === "AVAILABLE" ? (
                                                      <Chip size="small" label="Open slot" color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                                                    ) : null}
                                                    {!row.appointment && row.slot.bookedCount === 0 ? (
                                                      <Typography variant="body2" color="text.secondary">—</Typography>
                                                    ) : null}
                                                  </Stack>
                                                </TableCell>
                                                <TableCell align="right" sx={{ py: 0.75 }}>
                                                  <Button
                                                    size="small"
                                                    variant="text"
                                                    disabled={!presentation.selectable}
                                                    onClick={(event) => {
                                                      event.stopPropagation();
                                                      selectSlot(row);
                                                    }}
                                                  >
                                                    View
                                                  </Button>
                                                </TableCell>
                                              </TableRow>
                                            );
                                          })}
                                        </TableBody>
                                      </Table>
                                    </Box>
                                  </AccordionDetails>
                                </Accordion>
                              ))}
                            </Stack>
                          </AccordionDetails>
                        </Accordion>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1.1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "center" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        Availability Sessions - {doctorScopeLabel}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Sorted Monday to Sunday, then doctor, then start time.
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                      <Chip size="small" label={`${sessionBuckets.length} days`} variant="outlined" sx={COMPACT_CHIP_SX} />
                      <Chip size="small" label={`${filteredAvailabilityRows.length} sessions`} color="primary" variant="outlined" sx={COMPACT_CHIP_SX} />
                    </Stack>
                  </Box>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap">
                    <TextField
                      size="small"
                      label="Search doctor"
                      value={sessionSearch}
                      onChange={(e) => setSessionSearch(e.target.value)}
                      sx={{ minWidth: 180, flex: 1 }}
                    />
                    <FormControl size="small" sx={{ minWidth: 150 }}>
                      <InputLabel id="session-status-filter">Status</InputLabel>
                      <Select
                        labelId="session-status-filter"
                        label="Status"
                        value={sessionStatusFilter}
                        onChange={(e) => setSessionStatusFilter(e.target.value as "all" | "active" | "inactive")}
                      >
                        <MenuItem value="all">All</MenuItem>
                        <MenuItem value="active">Active</MenuItem>
                        <MenuItem value="inactive">Inactive</MenuItem>
                      </Select>
                    </FormControl>
                    <Button size="small" variant="outlined" onClick={() => setSessionOverrides(() => {
                      const next: Record<string, boolean> = {};
                      for (const group of sessionBuckets) {
                        next[group.dayOfWeek] = true;
                      }
                      return next;
                    })}>
                      Expand all
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => setSessionOverrides(() => {
                      const next: Record<string, boolean> = {};
                      for (const group of sessionBuckets) {
                        next[group.dayOfWeek] = false;
                      }
                      return next;
                    })}>
                      Collapse all
                    </Button>
                  </Stack>
                  {sessionBuckets.length === 0 ? (
                    <Alert severity="info">
                      No availability sessions for the selected scope. Add a session or choose All Doctors to see the full schedule.
                    </Alert>
                  ) : (
                    <Stack spacing={1}>
                      {sessionBuckets.map((group) => (
                        <Accordion
                          key={group.dayOfWeek}
                          expanded={group.expanded}
                          onChange={(_, expanded) => setSessionOverrides((current) => ({ ...current, [group.dayOfWeek]: expanded }))}
                          disableGutters
                          sx={{
                            border: "1px solid",
                            borderColor: "divider",
                            borderRadius: 2,
                            overflow: "hidden",
                            "&:before": { display: "none" },
                            "& .MuiAccordionSummary-root": { minHeight: 46, px: 1 },
                            "& .MuiAccordionSummary-content": { my: 0.5 },
                            "& .MuiAccordionDetails-root": { px: 1, pb: 1, pt: 0 },
                          }}
                        >
                          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, width: "100%", flexWrap: "wrap" }}>
                              <Box>
                                <Typography sx={{ fontWeight: 800 }}>{group.dayOfWeek}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {group.doctorsCount} doctor{group.doctorsCount === 1 ? "" : "s"} • {group.sessionsCount} session{group.sessionsCount === 1 ? "" : "s"} • {group.activeCount} active • {group.inactiveCount} inactive
                                </Typography>
                              </Box>
                              <Stack direction="row" spacing={0.4} flexWrap="wrap" justifyContent="flex-end">
                                <Chip size="small" label={`${group.sessionsCount} sessions`} variant="outlined" sx={COMPACT_CHIP_SX} />
                                <Chip size="small" label={`${group.doctorsCount} doctors`} variant="outlined" sx={COMPACT_CHIP_SX} />
                                <Chip size="small" label={`${group.activeCount} active`} color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                              </Stack>
                            </Box>
                          </AccordionSummary>
                          <AccordionDetails>
                            <Stack spacing={0.9}>
                              {group.rows.length === 0 ? (
                                <Alert severity="info">No sessions match the current filters.</Alert>
                              ) : isDoctor || Boolean(selectedDoctorId) ? (
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell sx={{ width: 144, fontWeight: 800, py: 0.75 }}>Session</TableCell>
                                      <TableCell sx={{ width: 126, fontWeight: 800, py: 0.75 }}>Break</TableCell>
                                      <TableCell sx={{ width: 92, fontWeight: 800, py: 0.75 }}>Duration</TableCell>
                                      <TableCell sx={{ width: 92, fontWeight: 800, py: 0.75 }}>Capacity</TableCell>
                                      <TableCell sx={{ width: 98, fontWeight: 800, py: 0.75 }}>Status</TableCell>
                                      <TableCell align="right" sx={{ width: 104, fontWeight: 800, py: 0.75 }}>Actions</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {group.rows.flatMap((doctorGroup) => doctorGroup.rows.map((row) => (
                                      <TableRow
                                        key={row.id}
                                        sx={{
                                          bgcolor: row.active ? "transparent" : "action.hover",
                                          "& td": { color: row.active ? "text.primary" : "text.secondary" },
                                        }}
                                      >
                                        <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 700, py: 0.75 }}>{row.startTime} - {row.endTime}</TableCell>
                                        <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>
                                          {row.breakStartTime && row.breakEndTime ? (
                                            <Chip size="small" label={`Break ${row.breakStartTime}–${row.breakEndTime}`} variant="outlined" sx={COMPACT_CHIP_SX} />
                                          ) : "—"}
                                        </TableCell>
                                        <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>{row.consultationDurationMinutes} min</TableCell>
                                        <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>{row.maxPatientsPerSlot || 1}</TableCell>
                                        <TableCell sx={{ py: 0.75 }}>
                                          <Chip
                                            size="small"
                                            label={row.active ? "Active" : "Inactive"}
                                            color={row.active ? "success" : "default"}
                                            variant={row.active ? "filled" : "outlined"}
                                            sx={COMPACT_CHIP_SX}
                                          />
                                        </TableCell>
                                        <TableCell align="right" sx={{ py: 0.75 }}>
                                          <Button
                                            size="small"
                                            color={row.active ? "warning" : "primary"}
                                            variant={row.active ? "outlined" : "contained"}
                                            onClick={() => void toggleAvailabilityStatus(row)}
                                          >
                                            {row.active ? "Deactivate" : "Activate"}
                                          </Button>
                                        </TableCell>
                                      </TableRow>
                                    )))}
                                  </TableBody>
                                </Table>
                              ) : (
                                <Stack spacing={0.8}>
                                  {group.rows.map((doctorGroup) => (
                                    <Box key={doctorGroup.doctorUserId} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                                      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap", mb: 0.75 }}>
                                        <Box>
                                          <Typography sx={{ fontWeight: 800 }}>{doctorGroup.doctorName}</Typography>
                                          <Typography variant="caption" color="text.secondary">
                                            {doctorGroup.totalCount} session{doctorGroup.totalCount === 1 ? "" : "s"} • {doctorGroup.activeCount} active • {doctorGroup.inactiveCount} inactive
                                          </Typography>
                                        </Box>
                                        <Stack direction="row" spacing={0.4} flexWrap="wrap">
                                          <Chip size="small" label={`${doctorGroup.totalCount} sessions`} variant="outlined" sx={COMPACT_CHIP_SX} />
                                          <Chip size="small" label={`${doctorGroup.activeCount} active`} color="success" variant="outlined" sx={COMPACT_CHIP_SX} />
                                        </Stack>
                                      </Box>
                                      <Table size="small">
                                        <TableHead>
                                          <TableRow>
                                            <TableCell sx={{ width: 144, fontWeight: 800, py: 0.75 }}>Session</TableCell>
                                            <TableCell sx={{ width: 126, fontWeight: 800, py: 0.75 }}>Break</TableCell>
                                            <TableCell sx={{ width: 92, fontWeight: 800, py: 0.75 }}>Duration</TableCell>
                                            <TableCell sx={{ width: 92, fontWeight: 800, py: 0.75 }}>Capacity</TableCell>
                                            <TableCell sx={{ width: 98, fontWeight: 800, py: 0.75 }}>Status</TableCell>
                                            <TableCell align="right" sx={{ width: 104, fontWeight: 800, py: 0.75 }}>Actions</TableCell>
                                          </TableRow>
                                        </TableHead>
                                        <TableBody>
                                          {doctorGroup.rows.map((row) => (
                                            <TableRow
                                              key={row.id}
                                              sx={{
                                                bgcolor: row.active ? "transparent" : "action.hover",
                                                "& td": { color: row.active ? "text.primary" : "text.secondary" },
                                              }}
                                            >
                                              <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 700, py: 0.75 }}>{row.startTime} - {row.endTime}</TableCell>
                                              <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>
                                                {row.breakStartTime && row.breakEndTime ? (
                                                  <Chip size="small" label={`Break ${row.breakStartTime}–${row.breakEndTime}`} variant="outlined" sx={COMPACT_CHIP_SX} />
                                                ) : "—"}
                                              </TableCell>
                                              <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>{row.consultationDurationMinutes} min</TableCell>
                                              <TableCell sx={{ whiteSpace: "nowrap", py: 0.75 }}>{row.maxPatientsPerSlot || 1}</TableCell>
                                              <TableCell sx={{ py: 0.75 }}>
                                                <Chip
                                                  size="small"
                                                  label={row.active ? "Active" : "Inactive"}
                                                  color={row.active ? "success" : "default"}
                                                  variant={row.active ? "filled" : "outlined"}
                                                  sx={COMPACT_CHIP_SX}
                                                />
                                              </TableCell>
                                              <TableCell align="right" sx={{ py: 0.75 }}>
                                                <Button
                                                  size="small"
                                                  color={row.active ? "warning" : "primary"}
                                                  variant={row.active ? "outlined" : "contained"}
                                                  onClick={() => void toggleAvailabilityStatus(row)}
                                                >
                                                  {row.active ? "Deactivate" : "Activate"}
                                                </Button>
                                              </TableCell>
                                            </TableRow>
                                          ))}
                                        </TableBody>
                                      </Table>
                                    </Box>
                                  ))}
                                </Stack>
                              )}
                            </Stack>
                          </AccordionDetails>
                        </Accordion>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 3 }}>
          <Stack spacing={1.5}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Selected Slot</Typography>
                  {!selectedSlot ? (
                    <Alert severity="info">
                      Select a slot to view details, open the appointment flow, or mark blocks around that time.
                    </Alert>
                  ) : (
                    <Stack spacing={1.1}>
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>
                          {compactDateLabel(selectedSlot.date)} • {timeLabel(selectedSlot.slot.slotTime)} - {timeLabel(selectedSlot.slot.slotEndTime)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Doctor: {doctorDisplayName(doctorMap.get(selectedSlot.doctorUserId), selectedSlot.slot.doctorName)}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={0.5} flexWrap="wrap">
                        <Chip size="small" label={friendlyStatusLabel(slotPresentation(selectedSlot.date, selectedSlot.slot, clinicTimeZone, clinicNowSnapshot).state)} color={slotStateColor(slotPresentation(selectedSlot.date, selectedSlot.slot, clinicTimeZone, clinicNowSnapshot).state)} variant="outlined" sx={COMPACT_CHIP_SX} />
                        <Chip size="small" label={`${selectedSlot.slot.bookedCount}/${selectedSlot.slot.maxPatientsPerSlot}`} variant="outlined" sx={COMPACT_CHIP_SX} />
                        {selectedSlot.slot.selectable ? <Chip size="small" label="Selectable" color="success" variant="outlined" sx={COMPACT_CHIP_SX} /> : <Chip size="small" label="Not selectable" variant="outlined" sx={COMPACT_CHIP_SX} />}
                      </Stack>
                      <Typography variant="body2">Capacity: {selectedSlot.slot.maxPatientsPerSlot}</Typography>
                      <Typography variant="body2">Booked: {selectedSlot.slot.bookedCount}</Typography>
                      <Typography variant="body2">Patient: {wrapWords(selectedAppointment ? appointmentLabel(selectedAppointment) : selectedSlot.slot.patientName)}</Typography>
                      <Stack spacing={1}>
                        <Button
                          size="small"
                          variant="contained"
                          disabled={!selectedSlotCanBook}
                          onClick={openBookingFlow}
                        >
                          {selectedAppointment ? "Open appointment flow" : "Book appointment"}
                        </Button>
                        {selectedSlotBookingReason ? (
                          <Typography variant="caption" color="text.secondary">
                            {selectedSlotBookingReason}
                          </Typography>
                        ) : null}
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => setWaitlistReason(`No slot available at ${selectedSlot.slot.slotTime}`)}
                        >
                          Add to waitlist
                        </Button>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => setUnavailabilityForm((current) => ({ ...current, startAt: `${selectedSlot.date}T${selectedSlot.slot.slotTime}:00`, endAt: `${selectedSlot.date}T${selectedSlot.slot.slotEndTime}:00` }))}
                        >
                          Mark unavailable
                        </Button>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => setAvailabilityForm((current) => ({
                            ...current,
                            dayOfWeek: DAYS[new Date(`${selectedSlot.date}T00:00:00Z`).getUTCDay() === 0 ? 6 : new Date(`${selectedSlot.date}T00:00:00Z`).getUTCDay() - 1],
                            breakStartTime: selectedSlot.slot.slotTime,
                            breakEndTime: selectedSlot.slot.slotEndTime,
                          }))}
                        >
                          Add break
                        </Button>
                      </Stack>
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Appointment Details</Typography>
                  {!selectedAppointment ? (
                    <Alert severity="info">
                      Hover or select a booked slot to inspect patient and booking details.
                    </Alert>
                  ) : (
                    <Stack spacing={1}>
                      <Typography sx={{ fontWeight: 800 }}>{appointmentLabel(selectedAppointment)}</Typography>
                      <Chip size="small" label={friendlyStatusLabel(selectedAppointment.status)} color={appointmentColor(selectedAppointment.status)} sx={{ width: "fit-content", ...COMPACT_CHIP_SX }} />
                      <Typography variant="body2">Doctor: {selectedAppointment.doctorName || selectedSlot?.slot.doctorName || selectedDoctorLabel}</Typography>
                      <Typography variant="body2">Type: {selectedAppointment.type}</Typography>
                      <Typography variant="body2">Phone: {selectedAppointment.patientMobile || "—"}</Typography>
                      <Typography variant="body2">Consultation: {selectedAppointment.consultationId ? "Linked" : "Not started"}</Typography>
                      <Stack spacing={1}>
                        <Button size="small" onClick={() => navigate("/appointments")}>Open appointments</Button>
                        {selectedAppointment.consultationId && canOpenConsultationWorkspace ? (
                          <Button size="small" variant="outlined" onClick={() => navigate(`/consultations/${selectedAppointment.consultationId}`)}>
                            Open consultation
                          </Button>
                        ) : null}
                        <Button size="small" variant="outlined" onClick={() => navigate(`/patients/${selectedAppointment.patientId}`)}>
                          Open patient
                        </Button>
                      </Stack>
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Waitlist</Typography>
                  {!filters.WAITLIST ? (
                    <Alert severity="info">Waitlist is hidden by the active filter.</Alert>
                  ) : selectedDoctorId || isDoctor ? null : (
                    <Alert severity="info">Use a specific doctor to create or review waitlist items in context.</Alert>
                  )}
                  {!filters.WAITLIST ? null : selectedWaitlist.length === 0 ? (
                    <Alert severity="info">No waiting patients for the selected date.</Alert>
                  ) : (
                    <Stack spacing={1}>
                      {selectedWaitlist.map((entry) => (
                        <Box key={entry.id} sx={{ p: 1, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                            <Box>
                              <Typography sx={{ fontWeight: 700 }}>{entry.patientName || entry.patientNumber || entry.patientId}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {entry.preferredStartTime || "Any time"} • {entry.reason || "No reason"}
                              </Typography>
                            </Box>
                            <Chip size="small" label={friendlyStatusLabel(entry.status)} variant="outlined" sx={COMPACT_CHIP_SX} />
                          </Stack>
                          <Stack direction="row" spacing={1} sx={{ mt: 0.75 }}>
                            <Button size="small" variant="outlined" onClick={() => navigate(`/appointments?doctorUserId=${entry.doctorUserId || selectedSlot?.doctorUserId || selectedDoctorId}&appointmentDate=${entry.preferredDate}`)}>
                              Open booking
                            </Button>
                          </Stack>
                        </Box>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Leave / Blocks</Typography>
                  {selectedBlocks.length === 0 ? (
                    <Alert severity="info">No leave or block entries for the selected scope.</Alert>
                  ) : (
                    <Stack spacing={1}>
                          {selectedBlocks.map((item) => (
                            <Box key={item.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                          <Typography sx={{ fontWeight: 700, mb: 0.25 }}>{friendlyStatusLabel(item.type)}</Typography>
                          <Typography variant="body2">{doctorDisplayName(doctorMap.get(item.doctorUserId), item.doctorUserId)}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {new Date(item.startAt).toLocaleString()} - {new Date(item.endAt).toLocaleString()}
                          </Typography>
                          {item.reason ? <Typography variant="body2" color="text.secondary">{item.reason}</Typography> : null}
                          {item.active ? (
                            <Button size="small" color="warning" onClick={() => auth.accessToken && auth.tenantId && void deactivateDoctorUnavailability(auth.accessToken, auth.tenantId, item.id).then(loadDynamic)}>
                              Deactivate
                            </Button>
                          ) : null}
                        </Box>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </Stack>
  );
}
