import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Autocomplete,
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  Divider,
  FormControl,
  Grid,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
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
  Tooltip,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { alpha, type Theme } from "@mui/material/styles";

import { useAuth } from "../../auth/useAuth";
import {
  createAppointment,
  collectConsultationFee,
  createWaitlist,
  getClinicClock,
  getClinicUsers,
  getDoctorSlots,
  getWaitlist,
  rescheduleAppointment,
  searchAppointments,
  searchBills,
  searchPatients,
  startConsultationFromAppointment,
  updateAppointmentStatus,
  updateWaitlistStatus,
  type Appointment,
  type AppointmentWaitlist,
  type AppointmentType,
  type ClinicUser,
  type DoctorAvailabilitySlot,
  type DoctorAvailabilitySlotStatus,
  type PaymentMode,
  type Patient,
  type Bill,
} from "../../api/clinicApi";
import ConsultationFeeDialog from "../../components/ConsultationFeeDialog";
import { CompactEmptyState, WorkflowStrip } from "../../components/compact/CompactUi";
import { AppointmentTokenChip, PatientJourneyTracker, WorkflowStatusBadge } from "../../components/workflow/WorkflowUx";
import { getClinicClockParts, getClinicDateKey, isBookingTimePast, formatClinicClockLabel } from "./bookingValidation";
import { getAppointmentSlotPresentation } from "./slotState";
import { formatRelativeBookingTime, getNextWorkflowAction } from "../../components/workflow/workflowHelpers";

type SlotFilterKey = DoctorAvailabilitySlotStatus | "BOOKED" | "CHECKED_IN" | "IN_CONSULTATION" | "COMPLETED" | "NO_SHOW" | "CANCELLED";

type DoctorPanel = {
  doctorUserId: string;
  doctorName: string;
  slots: DoctorAvailabilitySlot[];
  waitlist: AppointmentWaitlist[];
};

type DoctorOption = {
  appUserId: string;
  displayName: string;
  email: string | null;
};

type SlotSelection = {
  kind: "slot";
  slot: DoctorAvailabilitySlot;
};

type AppointmentSelection = {
  kind: "appointment";
  appointment: Appointment;
};

type Selection = SlotSelection | AppointmentSelection;

type CalendarSlotRow = {
  date: string;
  doctorUserId: string;
  doctorName: string;
  slot: DoctorAvailabilitySlot;
  appointment: Appointment | null;
};

type SchedulerSectionKey = "morning" | "afternoon" | "evening" | "other";

type SchedulerSectionDefinition = {
  key: SchedulerSectionKey;
  label: string;
  rangeLabel: string;
  startMinute: number;
  endMinute: number;
};

type SchedulerSectionSummary = {
  totalSlots: number;
  availableCount: number;
  bookedCount: number;
  partialCount: number;
  fullCount: number;
  checkedInCount: number;
  inConsultationCount: number;
};

const APPOINTMENT_TYPES: AppointmentType[] = ["SCHEDULED", "FOLLOW_UP", "VACCINATION", "WALK_IN"];

const ALL_DOCTORS_OPTION: DoctorOption = {
  appUserId: "",
  displayName: "All Doctors",
  email: null,
};

const STATUS_FILTERS: SlotFilterKey[] = [
  "AVAILABLE",
  "PARTIALLY_BOOKED",
  "FULL",
  "BOOKED",
  "CHECKED_IN",
  "IN_CONSULTATION",
  "COMPLETED",
  "NO_SHOW",
  "CANCELLED",
];

function toFive(time: string | null | undefined) {
  if (!time) return "";
  return time.slice(0, 5);
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
      return "secondary";
    case "LEAVE":
    case "UNAVAILABLE":
      return "default";
    case "CONFLICTED":
      return "error";
  }
}

function appointmentColor(status: Appointment["status"]) {
  switch (status) {
    case "BOOKED":
      return "warning";
    case "WAITING":
      return "warning";
    case "IN_CONSULTATION":
      return "info";
    case "COMPLETED":
      return "success";
    case "NO_SHOW":
    case "CANCELLED":
      return "default";
  }
}

function sameTimeSlot(slot: DoctorAvailabilitySlot, appointment: Appointment) {
  return toFive(appointment.appointmentTime) === toFive(slot.slotTime);
}

function friendlyStatusLabel(value: string | null | undefined) {
  if (!value) return "-";
  switch (value.toUpperCase()) {
    case "PARTIALLY_BOOKED":
      return "Partially booked";
    case "CHECKED_IN":
      return "Checked in";
    case "IN_CONSULTATION":
      return "In consultation";
    case "COMPLETED":
      return "Completed";
    case "NO_SHOW":
      return "No-show";
    case "CANCELLED":
      return "Cancelled";
    case "AVAILABLE":
      return "Available";
    case "CURRENT":
      return "Current";
    case "BOOKED":
      return "Booked";
    case "FULL":
      return "Full";
    case "PAST":
      return "Past";
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
      return value.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\S/g, (match) => match.toUpperCase());
  }
}

function displayDoctorName(users: ClinicUser[], doctorUserId: string | null | undefined) {
  if (!doctorUserId) return "Unassigned";
  return users.find((u) => u.appUserId === doctorUserId)?.displayName || doctorUserId;
}

function timeLabel(time: string) {
  return time.length >= 5 ? time.slice(0, 5) : time;
}

function compactDateLabel(date: string) {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: "UTC",
    weekday: "long",
    month: "short",
    day: "numeric",
  }).format(new Date(`${date}T00:00:00Z`));
}

const DAY_BOARD_WORKFLOW_STEPS = [
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

function isPastDateTime(date: string, time: string | null | undefined, timeZone?: string | null, clinicNow?: string | null) {
  return isBookingTimePast(date, time, undefined, timeZone, clinicNow);
}

function slotPresentation(date: string, slot: DoctorAvailabilitySlot, timeZone?: string | null, clinicNow?: string | null) {
  return getAppointmentSlotPresentation(date, slot, timeZone, clinicNow);
}

function slotDisplayStatus(slot: DoctorAvailabilitySlot, appointments: Appointment[]) {
  const activeAppointments = appointments.filter((appointment) => appointment.status !== "CANCELLED" && appointment.status !== "NO_SHOW");
  if (activeAppointments.some((appointment) => appointment.status === "IN_CONSULTATION")) return "IN_CONSULTATION";
  if (activeAppointments.some((appointment) => appointment.status === "WAITING")) return "CHECKED_IN";
  if (activeAppointments.some((appointment) => appointment.status === "COMPLETED")) return "COMPLETED";
  if (activeAppointments.some((appointment) => appointment.status === "BOOKED")) return "BOOKED";
  if (appointments.some((appointment) => appointment.status === "NO_SHOW")) return "NO_SHOW";
  if (appointments.some((appointment) => appointment.status === "CANCELLED")) return "CANCELLED";
  return slot.status;
}

function slotCellColor(status: string) {
  switch (status) {
    case "AVAILABLE":
    case "CURRENT":
      return "success";
    case "PARTIALLY_BOOKED":
      return "warning";
    case "FULL":
      return "error";
    case "BOOKED":
      return "info";
    case "CHECKED_IN":
      return "info";
    case "IN_CONSULTATION":
      return "secondary";
    case "COMPLETED":
      return "success";
    case "PAST":
      return "default";
    case "HOLIDAY":
      return "default";
    case "NO_SHOW":
      return "error";
    case "CANCELLED":
      return "default";
    case "BREAK":
    case "LEAVE":
    case "UNAVAILABLE":
      return "default";
    case "CONFLICTED":
      return "error";
    default:
      return "default";
  }
}

function slotTint(status: string) {
  switch (status) {
    case "AVAILABLE":
    case "CURRENT":
      return "success.50";
    case "PARTIALLY_BOOKED":
      return "warning.50";
    case "FULL":
      return "error.50";
    case "BOOKED":
      return "info.50";
    case "CHECKED_IN":
      return "info.100";
    case "IN_CONSULTATION":
      return "secondary.50";
    case "COMPLETED":
      return "success.100";
    case "NO_SHOW":
      return "error.100";
    case "CANCELLED":
      return "grey.100";
    case "PAST":
      return "grey.200";
    case "BREAK":
    case "LEAVE":
    case "HOLIDAY":
    case "UNAVAILABLE":
      return "grey.200";
    case "CONFLICTED":
      return "error.100";
    default:
      return "background.paper";
  }
}

function statusLabel(status: string) {
  return friendlyStatusLabel(status);
}

function formatTimeRange(start: string | null | undefined, end: string | null | undefined) {
  if (!start || !end) return "-";
  return `${toFive(start)} - ${toFive(end)}`;
}

function appointmentTooltip(appointment: Appointment, doctorName: string, slot: DoctorAvailabilitySlot) {
  return (
    <Box sx={{ p: 0.5, maxWidth: 280 }}>
      <Typography sx={{ fontWeight: 800 }}>{appointment.patientName || appointment.patientNumber || appointment.patientId}</Typography>
      <Typography variant="body2">Phone: {appointment.patientMobile || "—"}</Typography>
      <Typography variant="body2">Purpose: {appointment.type}</Typography>
      <Typography variant="body2">Status: {friendlyStatusLabel(appointment.status)}</Typography>
      <Typography variant="body2">Doctor: {doctorName}</Typography>
      <Typography variant="body2">Slot: {toFive(slot.slotTime)} - {toFive(slot.slotEndTime)}</Typography>
      <Typography variant="body2">Reference: {appointment.displayReference || (appointment.tokenNumber != null ? `APT-${appointment.tokenNumber}` : "Pending")}</Typography>
    </Box>
  );
}

function appointmentTitle(appointment: Appointment) {
  return appointment.patientName || appointment.patientNumber || appointment.patientId;
}

function isDragEligibleAppointment(appointment: Appointment) {
  return appointment.status === "BOOKED";
}

function summarizeCellStatus(slot: DoctorAvailabilitySlot | null, appointments: Appointment[], date: string, timeZone?: string | null, clinicNow?: string | null) {
  if (!slot && appointments.length === 0) {
    return {
      status: "UNAVAILABLE",
      displayState: "UNAVAILABLE",
      label: "No slot",
    };
  }
  if (appointments.some((appointment) => appointment.status === "IN_CONSULTATION")) {
    return { status: "IN_CONSULTATION", displayState: "IN_CONSULTATION", label: "In consultation" };
  }
  if (appointments.some((appointment) => appointment.status === "WAITING")) {
    return { status: "CHECKED_IN", displayState: "CHECKED_IN", label: "Checked in" };
  }
  if (appointments.length > 0 && appointments.every((appointment) => appointment.status === "COMPLETED")) {
    return { status: "COMPLETED", displayState: "COMPLETED", label: "Completed" };
  }
  if (slot) {
    const presentation = slotPresentation(date, slot, timeZone, clinicNow);
    return { status: slot.status, displayState: presentation.state, label: friendlyStatusLabel(presentation.state) };
  }
  if (appointments.some((appointment) => appointment.status === "BOOKED")) {
    return { status: "BOOKED", displayState: "BOOKED", label: "Booked" };
  }
  return { status: "UNAVAILABLE", displayState: "UNAVAILABLE", label: "No slot" };
}

function cellBackground(status: string) {
  switch (status) {
    case "AVAILABLE":
    case "CURRENT":
      return (theme: Theme) => alpha(theme.palette.success.main, 0.08);
    case "PARTIALLY_BOOKED":
      return (theme: Theme) => alpha(theme.palette.warning.main, 0.12);
    case "FULL":
      return (theme: Theme) => alpha(theme.palette.error.main, 0.10);
    case "BOOKED":
      return (theme: Theme) => alpha(theme.palette.info.main, 0.10);
    case "CHECKED_IN":
      return (theme: Theme) => alpha(theme.palette.info.main, 0.16);
    case "IN_CONSULTATION":
      return (theme: Theme) => alpha(theme.palette.secondary.main, 0.12);
    case "COMPLETED":
      return (theme: Theme) => alpha(theme.palette.success.main, 0.14);
    case "PAST":
      return (theme: Theme) => alpha(theme.palette.grey[500], 0.10);
    case "NO_SHOW":
      return (theme: Theme) => alpha(theme.palette.error.main, 0.14);
    case "CANCELLED":
      return (theme: Theme) => alpha(theme.palette.grey[500], 0.12);
    case "BREAK":
    case "LEAVE":
    case "HOLIDAY":
    case "UNAVAILABLE":
      return (theme: Theme) => alpha(theme.palette.grey[500], 0.12);
    case "CONFLICTED":
      return (theme: Theme) => alpha(theme.palette.error.main, 0.18);
    default:
      return (theme: Theme) => alpha(theme.palette.background.paper, 1);
  }
}

function cellBorder(status: string, selected = false) {
  if (selected) return "primary.main";
  switch (status) {
    case "AVAILABLE":
    case "CURRENT":
      return "success.200";
    case "PARTIALLY_BOOKED":
      return "warning.200";
    case "FULL":
      return "error.200";
    case "BOOKED":
    case "CHECKED_IN":
    case "IN_CONSULTATION":
      return "info.200";
    case "COMPLETED":
      return "success.300";
    case "PAST":
      return "grey.300";
    case "NO_SHOW":
    case "CONFLICTED":
      return "error.200";
    case "CANCELLED":
    case "BREAK":
    case "LEAVE":
    case "HOLIDAY":
    case "UNAVAILABLE":
      return "divider";
    default:
      return "divider";
  }
}

const SCHEDULER_SECTIONS: SchedulerSectionDefinition[] = [
  { key: "morning", label: "Morning", rangeLabel: "06:00-12:00", startMinute: 6 * 60, endMinute: 12 * 60 },
  { key: "afternoon", label: "Afternoon", rangeLabel: "12:00-17:00", startMinute: 12 * 60, endMinute: 17 * 60 },
  { key: "evening", label: "Evening", rangeLabel: "17:00-22:00", startMinute: 17 * 60, endMinute: 22 * 60 },
  { key: "other", label: "Other", rangeLabel: "Outside schedule", startMinute: -1, endMinute: -1 },
];

function minutesFromTime(time: string | null | undefined) {
  if (!time || time.length < 5) return null;
  const hours = Number(time.slice(0, 2));
  const minutes = Number(time.slice(3, 5));
  if (Number.isNaN(hours) || Number.isNaN(minutes)) return null;
  return (hours * 60) + minutes;
}

function sectionForTime(time: string | null | undefined): SchedulerSectionKey {
  const minutes = minutesFromTime(time);
  if (minutes === null) return "other";
  if (minutes >= 6 * 60 && minutes < 12 * 60) return "morning";
  if (minutes >= 12 * 60 && minutes < 17 * 60) return "afternoon";
  if (minutes >= 17 * 60 && minutes < 22 * 60) return "evening";
  return "other";
}

function sectionTone(sectionKey: SchedulerSectionKey) {
  switch (sectionKey) {
    case "morning":
      return "success";
    case "afternoon":
      return "warning";
    case "evening":
      return "secondary";
    case "other":
      return "default";
  }
}

function summarizeRows(rows: CalendarSlotRow[], timeZone?: string | null, clinicNow?: string | null): SchedulerSectionSummary {
  return {
    totalSlots: rows.length,
    availableCount: rows.filter((row) => {
      const presentation = slotPresentation(row.date, row.slot, timeZone, clinicNow);
      return presentation.counterEligible;
    }).length,
    bookedCount: rows.filter((row) => Boolean(row.appointment)).length,
    partialCount: rows.filter((row) => slotPresentation(row.date, row.slot, timeZone, clinicNow).state === "PARTIALLY_BOOKED").length,
    fullCount: rows.filter((row) => slotPresentation(row.date, row.slot, timeZone, clinicNow).state === "FULL").length,
    checkedInCount: rows.filter((row) => row.appointment?.status === "WAITING").length,
    inConsultationCount: rows.filter((row) => row.appointment?.status === "IN_CONSULTATION").length,
  };
}

function formatMoney(value: number | null | undefined) {
  if (value == null) return "—";
  return value.toFixed(2);
}

function billHasConsultationLine(bill: Bill) {
  return bill.lines.some((line) => line.itemType === "CONSULTATION");
}

function consultationBillsByAppointment(bills: Bill[], appointmentId: string) {
  return bills
    .filter((bill) => bill.appointmentId === appointmentId && billHasConsultationLine(bill) && bill.status !== "CANCELLED")
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

function consultationEffectiveBill(bills: Bill[]) {
  return bills.find((bill) => bill.dueAmount > 0) || bills[0] || null;
}

type FeeStatus = "NOT_CONFIGURED" | "UNPAID" | "PARTIAL" | "PAID";

function feeStatusColor(status: FeeStatus) {
  switch (status) {
    case "PAID":
      return "success";
    case "PARTIAL":
    case "UNPAID":
      return "warning";
    case "NOT_CONFIGURED":
    default:
      return "default";
  }
}

function feeStatusLabel(status: FeeStatus, amount: number | null | undefined) {
  switch (status) {
    case "PAID":
      return `Paid${amount != null ? ` • ${formatMoney(amount)}` : ""}`;
    case "PARTIAL":
      return `Partial${amount != null ? ` • Due ${formatMoney(amount)}` : ""}`;
    case "UNPAID":
      return `Unpaid${amount != null ? ` • ${formatMoney(amount)}` : ""}`;
    case "NOT_CONFIGURED":
    default:
      return "Not configured";
  }
}

function consultationFeeSummary(appointment: Appointment, bills: Bill[]) {
  const effectiveFee = appointment.consultationFeeAmount ?? (bills.length > 0 ? Math.max(...bills.map((bill) => bill.totalAmount)) : null);
  const netPaid = appointment.consultationFeePaidAmount ?? bills.reduce((sum, bill) => sum + Math.max(0, bill.netPaidAmount ?? (bill.paidAmount - bill.refundedAmount)), 0);
  const due = appointment.consultationFeeDueAmount ?? (effectiveFee == null ? null : Math.max(0, effectiveFee - netPaid));
  const feeStatus: FeeStatus = appointment.consultationFeeStatus
    ? appointment.consultationFeeStatus
    : (effectiveFee == null || effectiveFee <= 0
      ? "NOT_CONFIGURED"
      : (due ?? 0) <= 0
        ? "PAID"
        : netPaid > 0
          ? "PARTIAL"
          : "UNPAID");
  return {
    bill: consultationEffectiveBill(bills),
    consultationFee: effectiveFee,
    feeStatus,
    dueAmount: due,
    paidAmount: netPaid,
  };
}

export default function DayBoardPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const doctorUserIdFromQuery = searchParams.get("doctorUserId") || "";
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState(doctorUserIdFromQuery);
  const [date, setDate] = React.useState(() => getClinicDateKey("Asia/Kolkata"));
  const [filters, setFilters] = React.useState<Record<SlotFilterKey, boolean>>(() => Object.fromEntries(STATUS_FILTERS.map((f) => [f, true])) as Record<SlotFilterKey, boolean>);
  const [patientSearch, setPatientSearch] = React.useState("");
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [appointmentType, setAppointmentType] = React.useState<AppointmentType>("SCHEDULED");
  const [reason, setReason] = React.useState("");
  const [manualTime, setManualTime] = React.useState("");
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [slots, setSlots] = React.useState<DoctorAvailabilitySlot[]>([]);
  const [waitlist, setWaitlist] = React.useState<AppointmentWaitlist[]>([]);
  const [doctorPanels, setDoctorPanels] = React.useState<DoctorPanel[]>([]);
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [selected, setSelected] = React.useState<Selection | null>(null);
  const [rescheduleOpen, setRescheduleOpen] = React.useState(false);
  const [rescheduleTarget, setRescheduleTarget] = React.useState<Appointment | null>(null);
  const [rescheduleDoctorUserId, setRescheduleDoctorUserId] = React.useState("");
  const [rescheduleDate, setRescheduleDate] = React.useState("");
  const [rescheduleTime, setRescheduleTime] = React.useState("");
  const [draggedAppointment, setDraggedAppointment] = React.useState<Appointment | null>(null);
  const [moveConfirmOpen, setMoveConfirmOpen] = React.useState(false);
  const [moveConfirmTarget, setMoveConfirmTarget] = React.useState<DoctorAvailabilitySlot | null>(null);
  const [feeDialog, setFeeDialog] = React.useState<{ appointment: Appointment; action: "collect" | "collect-and-check-in" } | null>(null);
  const [sectionOverrides, setSectionOverrides] = React.useState<Partial<Record<SchedulerSectionKey, boolean>>>({});
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [clinicTimeZone, setClinicTimeZone] = React.useState("Asia/Kolkata");
  const [clinicNowSnapshot, setClinicNowSnapshot] = React.useState<string | null>(null);
  const [clinicClockUnavailable, setClinicClockUnavailable] = React.useState(false);
  const [clockTick, setClockTick] = React.useState(0);
  const gridTopScrollRef = React.useRef<HTMLDivElement | null>(null);
  const gridBodyScrollRef = React.useRef<HTMLDivElement | null>(null);
  const sectionScrollRefs = React.useRef<Record<SchedulerSectionKey, HTMLDivElement | null>>({
    morning: null,
    afternoon: null,
    evening: null,
    other: null,
  });

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = auth.rolesUpper.includes("DOCTOR") || tenantRole === "DOCTOR";
  const canManage = auth.hasPermission("appointment.manage") || tenantRole === "RECEPTIONIST" || tenantRole === "CLINIC_ADMIN";
  const canBook = !isDoctor && (auth.hasPermission("appointment.create") || tenantRole === "RECEPTIONIST" || tenantRole === "CLINIC_ADMIN");
  const canCollect = !isDoctor && (auth.hasPermission("billing.create") || auth.hasPermission("payment.collect"));
  const canStartConsultation = isDoctor && auth.hasPermission("consultation.create");
  const canOpenWorkspace = isDoctor && auth.hasPermission("consultation.read");
  const doctorOptions = users.filter((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
  const selectedDoctorLabel = isDoctor && auth.appUserId
    ? displayDoctorName(users, auth.appUserId)
    : doctorUserId
      ? displayDoctorName(users, doctorUserId)
      : "All Doctors";
  const effectiveDoctorId = isDoctor && auth.appUserId ? auth.appUserId : doctorUserId;
  const selectedSlot = selected?.kind === "slot" ? selected.slot : null;
  const selectedAppointment = selected?.kind === "appointment" ? selected.appointment : null;
  const selectedDoctorOption = React.useMemo(() => {
    if (isDoctor && auth.appUserId) {
      return doctorOptions.find((doctor) => doctor.appUserId === auth.appUserId) || { ...ALL_DOCTORS_OPTION, displayName: displayDoctorName(users, auth.appUserId) };
    }
    if (!doctorUserId) return ALL_DOCTORS_OPTION;
    return doctorOptions.find((doctor) => doctor.appUserId === doctorUserId) || ALL_DOCTORS_OPTION;
  }, [auth.appUserId, doctorOptions, doctorUserId, isDoctor, users]);

  const visibleDoctorPanels = React.useMemo(() => {
    if (effectiveDoctorId) return doctorPanels;
    return doctorPanels;
  }, [doctorPanels, effectiveDoctorId]);

  const selectedSlotAppointments = React.useMemo(() => {
    if (!selectedSlot) return [];
    return appointments.filter((appointment) => appointment.doctorUserId === selectedSlot.doctorUserId && sameTimeSlot(selectedSlot, appointment));
  }, [appointments, selectedSlot]);

  const selectedSlotPanel = React.useMemo(() => {
    if (!selectedSlot) return null;
    return visibleDoctorPanels.find((panel) => panel.doctorUserId === selectedSlot.doctorUserId) || null;
  }, [selectedSlot, visibleDoctorPanels]);

  const selectedAppointmentFee = React.useMemo(() => {
    if (!selectedAppointment) return null;
    const consultationBills = consultationBillsByAppointment(bills, selectedAppointment.id);
    return consultationFeeSummary(selectedAppointment, consultationBills);
  }, [bills, selectedAppointment]);

  const activeWaitlist = React.useMemo(() => {
    if (effectiveDoctorId) return waitlist;
    return selectedSlotPanel?.waitlist || [];
  }, [effectiveDoctorId, selectedSlotPanel, waitlist]);
  const gridMinWidth = React.useMemo(() => Math.max(720, 92 + (visibleDoctorPanels.length * 188)), [visibleDoctorPanels.length]);

  React.useEffect(() => {
    if (!isDoctor) {
      setDoctorUserId(doctorUserIdFromQuery);
    }
  }, [doctorUserIdFromQuery, isDoctor]);
  const calendarRows = React.useMemo(() => {
    return visibleDoctorPanels.flatMap((panel) => {
      return panel.slots
        .filter((slot) => filters[slot.status])
        .map((slot) => ({
          date,
          doctorUserId: panel.doctorUserId,
          doctorName: panel.doctorName,
          slot,
          appointment: appointments.find((appointment) => appointment.id === slot.appointmentId)
            || appointments.find((appointment) => sameTimeSlot(slot, appointment))
            || null,
        }) satisfies CalendarSlotRow)
        .filter((row) => !slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).hidden);
    }).sort((left, right) => {
      const doctorDelta = left.doctorName.localeCompare(right.doctorName);
      if (doctorDelta !== 0) return doctorDelta;
      return left.slot.slotTime.localeCompare(right.slot.slotTime);
    });
  }, [appointments, clinicNowSnapshot, clinicTimeZone, date, filters, visibleDoctorPanels]);
  const calendarSummary = React.useMemo(() => summarizeRows(calendarRows, clinicTimeZone, clinicNowSnapshot), [calendarRows, clinicNowSnapshot, clinicTimeZone]);
  const clinicClock = React.useMemo(() => getClinicClockParts(clinicTimeZone, clinicNowSnapshot), [clinicNowSnapshot, clinicTimeZone, clockTick]);
  const todayDate = React.useMemo(() => clinicClock.dateKey, [clinicClock.dateKey]);
  const currentTimeSection = React.useMemo<SchedulerSectionKey | null>(() => {
    if (date !== todayDate) return null;
    const minutes = clinicClock.minutes;
    if (minutes >= 6 * 60 && minutes < 12 * 60) return "morning";
    if (minutes >= 12 * 60 && minutes < 17 * 60) return "afternoon";
    if (minutes >= 17 * 60 && minutes < 22 * 60) return "evening";
    return "other";
  }, [clinicClock.minutes, date, todayDate]);
  const schedulerSections = React.useMemo(() => {
    const rowsBySection = new Map<SchedulerSectionKey, CalendarSlotRow[]>(
      SCHEDULER_SECTIONS.map((section) => [section.key, [] as CalendarSlotRow[]]),
    );
    for (const row of calendarRows) {
      rowsBySection.get(sectionForTime(row.slot.slotTime))?.push(row);
    }
    return SCHEDULER_SECTIONS.map((section) => {
      const rows = [...(rowsBySection.get(section.key) || [])].sort((left, right) => {
        const doctorDelta = left.doctorName.localeCompare(right.doctorName);
        if (doctorDelta !== 0) return doctorDelta;
        return left.slot.slotTime.localeCompare(right.slot.slotTime);
      });
      const times = Array.from(new Set(rows.map((row) => toFive(row.slot.slotTime)))).sort((left, right) => left.localeCompare(right));
      const summary = summarizeRows(rows, clinicTimeZone, clinicNowSnapshot);
      const hasBookings = rows.some((row) => Boolean(row.appointment) || row.slot.bookedCount > 0 || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "PARTIALLY_BOOKED" || slotPresentation(row.date, row.slot, clinicTimeZone, clinicNowSnapshot).state === "FULL");
      const autoExpanded = Boolean((currentTimeSection && currentTimeSection === section.key) || hasBookings);
      const override = sectionOverrides[section.key];
      const expanded = override === undefined ? autoExpanded : override;
      return { ...section, rows, times, summary, hasBookings, autoExpanded, expanded };
    });
  }, [calendarRows, currentTimeSection, sectionOverrides]);

  const syncGridScroll = (source: HTMLDivElement | null, target: HTMLDivElement | null) => {
    if (!source || !target) return;
    target.scrollLeft = source.scrollLeft;
  };

  const handleTopGridScroll = () => syncGridScroll(gridTopScrollRef.current, gridBodyScrollRef.current);
  const handleBodyGridScroll = () => syncGridScroll(gridBodyScrollRef.current, gridTopScrollRef.current);

  React.useEffect(() => {
    setSectionOverrides({});
  }, [date]);

  const loadCore = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const appointmentRows = await searchAppointments(auth.accessToken, auth.tenantId, { appointmentDate: date, doctorUserId: effectiveDoctorId || undefined });
      const clinicUsersResult = await Promise.allSettled([
        getClinicUsers(auth.accessToken, auth.tenantId),
      ]);
      setAppointments(appointmentRows);
      setUsers(clinicUsersResult[0].status === "fulfilled" ? clinicUsersResult[0].value : []);
      if (isDoctor) {
        setBills([]);
        return;
      }
      const billRows = await searchBills(auth.accessToken, auth.tenantId, { fromDate: date, toDate: date });
      setBills(billRows);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load day board data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, date, effectiveDoctorId]);

  const loadDoctorPanels = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId) {
      setSlots([]);
      setWaitlist([]);
      setDoctorPanels([]);
      return;
    }
    try {
      if (effectiveDoctorId) {
        const [slotRows, waitRows] = await Promise.all([
          getDoctorSlots(token, tenantId, effectiveDoctorId, date),
          getWaitlist(token, tenantId, { doctorUserId: effectiveDoctorId, preferredDate: date, status: "WAITING" }),
        ]);
        setSlots(slotRows);
        setWaitlist(waitRows);
        setDoctorPanels([
          {
            doctorUserId: effectiveDoctorId,
            doctorName: displayDoctorName(users, effectiveDoctorId),
            slots: slotRows,
            waitlist: waitRows,
          },
        ]);
        return;
      }
      const doctors = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
      if (doctors.length === 0) {
        setSlots([]);
        setWaitlist([]);
        setDoctorPanels([]);
        return;
      }
      const rows = await Promise.all(doctors.map(async (doctor) => {
        const doctorId: string = doctor.appUserId || "";
        if (!doctorId) {
          return null;
        }
        const [slotRows, waitRows] = await Promise.all([
          getDoctorSlots(token, tenantId, doctorId, date),
          getWaitlist(token, tenantId, { doctorUserId: doctorId, preferredDate: date, status: "WAITING" }),
        ]);
        return {
          doctorUserId: doctorId,
          doctorName: displayDoctorName(users, doctorId),
          slots: slotRows,
          waitlist: waitRows,
        } satisfies DoctorPanel;
      }));
      setSlots([]);
      setWaitlist([]);
      setDoctorPanels(rows.filter((row): row is DoctorPanel => Boolean(row)).sort((a, b) => a.doctorName.localeCompare(b.doctorName)));
    } catch {
      setSlots([]);
      setWaitlist([]);
      setDoctorPanels([]);
    }
  }, [auth.accessToken, auth.tenantId, date, effectiveDoctorId, users]);

  React.useEffect(() => {
    void loadCore();
  }, [loadCore]);

  React.useEffect(() => {
    void loadDoctorPanels();
  }, [loadDoctorPanels]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinicTimeZone() {
      if (!auth.accessToken || !auth.tenantId) {
        setClinicTimeZone("Asia/Kolkata");
        setClinicNowSnapshot(null);
        setClinicClockUnavailable(true);
        console.info("[day-board] clinic clock unavailable", {
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
          console.info("[day-board] clinic clock loaded", {
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
          console.info("[day-board] clinic clock unavailable", {
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
    setSelected(null);
  }, [date]);

  React.useEffect(() => {
    if (isDoctor && auth.appUserId) {
      setDoctorUserId(auth.appUserId);
    }
  }, [auth.appUserId, isDoctor]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientSearch.trim().length < 2) {
        setPatientResults([]);
        return;
      }
      try {
        const rows = await searchPatients(auth.accessToken, auth.tenantId, { name: patientSearch.trim(), active: true });
        if (!cancelled) setPatientResults(rows);
      } catch {
        if (!cancelled) setPatientResults([]);
      }
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, patientSearch]);

  const refreshAll = async () => {
    await loadCore();
    await loadDoctorPanels();
  };

  const selectedSlotBookingReason = React.useMemo(() => {
    if (!selectedSlot) return "Select an available slot";
    if (!auth.tenantId || !auth.accessToken) return "Clinic context is unavailable";
    const presentation = slotPresentation(date, selectedSlot, clinicTimeZone, clinicNowSnapshot);
    if (presentation.isPast) return "Selected time has already passed. Please choose a current or future slot.";
    if (!presentation.bookable) return presentation.tooltip;
    return null;
  }, [auth.accessToken, auth.tenantId, clinicNowSnapshot, clinicTimeZone, date, selectedSlot]);

  const selectedSlotCanBook = Boolean(selectedSlot && !selectedSlotBookingReason);

  const openBookingFlow = () => {
    if (!selectedSlot || !selectedSlotCanBook) return;
    const params = new URLSearchParams({
      doctorUserId: selectedSlot.doctorUserId,
      appointmentDate: date,
      appointmentTime: toFive(selectedSlot.slotTime),
    });
    if (selectedPatient) {
      params.set("patientId", selectedPatient.id);
    }
    navigate(`/appointments?${params.toString()}`);
  };

  const bookManualAppointment = async () => {
    const bookingDoctorId = selectedSlot?.doctorUserId || effectiveDoctorId;
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !bookingDoctorId) return;
    const slotTime = selectedSlot ? toFive(selectedSlot.slotTime) : manualTime;
    if (!slotTime) {
      setError("Pick a slot or enter manual time.");
      return;
    }
    const matchingSlot = selectedSlot || slots.find((slot) => toFive(slot.slotTime) === slotTime) || null;
    const matchingPresentation = matchingSlot ? slotPresentation(date, matchingSlot, clinicTimeZone, clinicNowSnapshot) : null;
    if (matchingPresentation?.isPast || isPastDateTime(date, slotTime, clinicTimeZone, clinicNowSnapshot)) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }
    if (matchingPresentation && !matchingPresentation.bookable) {
      setError(matchingPresentation.tooltip);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createAppointment(auth.accessToken, auth.tenantId, {
        patientId: selectedPatient.id,
        doctorUserId: bookingDoctorId,
        appointmentDate: date,
        appointmentTime: slotTime,
        reason: reason.trim() || null,
        type: appointmentType,
        status: null,
        priority: "NORMAL",
      });
      setReason("");
      setManualTime("");
      await refreshAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to create appointment");
    } finally {
      setSaving(false);
    }
  };

  const addWaitlistFromSelection = async () => {
    const bookingDoctorId = selectedSlot?.doctorUserId || effectiveDoctorId;
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !bookingDoctorId) return;
    try {
      await createWaitlist(auth.accessToken, auth.tenantId, {
        patientId: selectedPatient.id,
        doctorUserId: bookingDoctorId,
        preferredDate: date,
        preferredStartTime: selectedSlot ? toFive(selectedSlot.slotTime) : null,
        preferredEndTime: selectedSlot ? toFive(selectedSlot.slotEndTime) : null,
        reason: reason.trim() || null,
        notes: null,
      });
      await loadDoctorPanels();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add waitlist entry");
    }
  };

  const checkInAppointment = async (appointment: Appointment) => {
    const consultationBills = consultationBillsByAppointment(bills, appointment.id);
    const fee = consultationFeeSummary(appointment, consultationBills);
    if ((fee.dueAmount ?? 0) > 0) {
      setError("Consultation fee is pending. Collect fee before check-in.");
      if (canCollect) {
        setFeeDialog({ appointment, action: "collect-and-check-in" });
      }
      return;
    }
    if (appointment.status !== "BOOKED") {
      await transitionStatus(appointment.id, "WAITING");
      return;
    }
    await transitionStatus(appointment.id, "WAITING");
  };

  const transitionStatus = async (appointmentId: string, status: Appointment["status"]) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointmentId, status, null);
      await refreshAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to update appointment status");
    }
  };

  const startConsultation = async (appointmentId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const consultation = await startConsultationFromAppointment(auth.accessToken, auth.tenantId, appointmentId);
      await refreshAll();
      navigate(`/consultations/${consultation.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to start consultation");
    }
  };

  const submitFeeDialog = async (value: { paymentMode: PaymentMode; referenceNumber: string; notes: string }) => {
    if (!feeDialog || !auth.accessToken || !auth.tenantId) {
      return;
    }
    const action = feeDialog.action;
    const current = feeDialog.appointment;
    await collectConsultationFee(auth.accessToken, auth.tenantId, {
      appointmentId: current.id,
      paymentMode: value.paymentMode,
      referenceNumber: value.referenceNumber || null,
      notes: value.notes || null,
    });
    setFeeDialog(null);
    await refreshAll();
    if (action === "collect-and-check-in") {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, current.id, "WAITING", null);
      await refreshAll();
    }
  };

  const openReschedule = (appointment: Appointment) => {
    setRescheduleTarget(appointment);
    setRescheduleDoctorUserId(appointment.doctorUserId);
    setRescheduleDate(appointment.appointmentDate);
    setRescheduleTime(toFive(appointment.appointmentTime));
    setRescheduleOpen(true);
  };

  const saveReschedule = async () => {
    if (!auth.accessToken || !auth.tenantId || !rescheduleTarget || !rescheduleDate || !rescheduleTime) return;
    if (isPastDateTime(rescheduleDate, rescheduleTime, clinicTimeZone, clinicNowSnapshot)) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }
    try {
      await rescheduleAppointment(auth.accessToken, auth.tenantId, rescheduleTarget.id, {
        doctorUserId: rescheduleDoctorUserId || null,
        appointmentDate: rescheduleDate,
        appointmentTime: rescheduleTime,
        reason: "Rescheduled from day board",
      });
      setRescheduleOpen(false);
      await refreshAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to reschedule appointment");
    }
  };

  const bookWaitlistEntry = async (entry: AppointmentWaitlist) => {
    const bookingDoctorId = selectedSlot?.doctorUserId || effectiveDoctorId;
    if (!auth.accessToken || !auth.tenantId || !bookingDoctorId || !selectedSlot) return;
    const presentation = slotPresentation(date, selectedSlot, clinicTimeZone, clinicNowSnapshot);
    if (presentation.isPast) {
      setError("Selected time has already passed. Please choose a current or future slot.");
      return;
    }
    if (!presentation.bookable) {
      setError(presentation.tooltip);
      return;
    }
    try {
      await createAppointment(auth.accessToken, auth.tenantId, {
        patientId: entry.patientId,
        doctorUserId: bookingDoctorId,
        appointmentDate: date,
        appointmentTime: toFive(selectedSlot.slotTime),
        reason: entry.reason,
        type: "SCHEDULED",
        status: null,
        priority: "NORMAL",
      });
      await updateWaitlistStatus(auth.accessToken, auth.tenantId, entry.id, "BOOKED");
      await refreshAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to book from waitlist");
    }
  };

  const startDragAppointment = (appointment: Appointment) => {
    if (!isDragEligibleAppointment(appointment)) return;
    setDraggedAppointment(appointment);
  };

  const canDropToSlot = (slot: DoctorAvailabilitySlot | null) => Boolean(slot && slotPresentation(date, slot, clinicTimeZone, clinicNowSnapshot).selectable);

  const openMoveConfirm = (slot: DoctorAvailabilitySlot) => {
    if (!draggedAppointment || !canDropToSlot(slot)) return;
    if (draggedAppointment.doctorUserId === slot.doctorUserId && toFive(draggedAppointment.appointmentTime) === toFive(slot.slotTime)) {
      setDraggedAppointment(null);
      return;
    }
    setMoveConfirmTarget(slot);
    setMoveConfirmOpen(true);
  };

  const confirmMoveAppointment = async () => {
    if (!auth.accessToken || !auth.tenantId || !draggedAppointment || !moveConfirmTarget) return;
    const targetTime = toFive(moveConfirmTarget.slotTime);
    try {
      await rescheduleAppointment(auth.accessToken, auth.tenantId, draggedAppointment.id, {
        doctorUserId: moveConfirmTarget.doctorUserId,
        appointmentDate: date,
        appointmentTime: targetTime,
        reason: `Moved from day board to ${targetTime}`,
      });
      setMoveConfirmOpen(false);
      setMoveConfirmTarget(null);
      setDraggedAppointment(null);
      await refreshAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to move appointment");
    }
  };

  const cellAppointments = (doctorUserIdValue: string, timeValue: string) =>
    appointments.filter((appointment) => appointment.doctorUserId === doctorUserIdValue && toFive(appointment.appointmentTime) === timeValue);

  if (!auth.tenantId) return <Alert severity="warning">No tenant selected.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Day Board</Typography>
          <Typography variant="body2" color="text.secondary">
            Calendar to booking to queue to consultation handoff • {selectedDoctorLabel}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void refreshAll()}>Refresh</Button>
          {!isDoctor ? <Button variant="outlined" onClick={() => navigate("/appointments")}>New appointment</Button> : null}
          <Button variant="outlined" onClick={() => navigate("/appointments")}>Appointments</Button>
          <Button variant="outlined" onClick={() => navigate("/queue")}>Queue</Button>
        </Stack>
      </Box>

      <WorkflowStrip steps={DAY_BOARD_WORKFLOW_STEPS} />

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}>
          <Card variant="outlined" sx={{ position: { md: "sticky" }, top: { md: 16 } }}>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Filters</Typography>
                <TextField size="small" type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                {isDoctor ? (
                  <Chip label={`Doctor: ${selectedDoctorLabel}`} color="primary" variant="outlined" />
                ) : (
                  <Autocomplete
                    options={[ALL_DOCTORS_OPTION, ...doctorOptions]}
                    value={selectedDoctorOption}
                    onChange={(_, value) => setDoctorUserId(value?.appUserId || "")}
                    getOptionLabel={(option) => option.displayName || option.email || option.appUserId || "All Doctors"}
                    isOptionEqualToValue={(option, value) => option.appUserId === value.appUserId}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        size="small"
                        label="Doctor"
                        placeholder="Search doctor or select All Doctors"
                      />
                    )}
                  />
                )}
                <TextField
                  size="small"
                  label="Patient quick search"
                  value={patientSearch}
                  onChange={(e) => {
                    setPatientSearch(e.target.value);
                    setSelectedPatient(null);
                  }}
                />
                {patientResults.length > 0 && !selectedPatient ? (
                  <List dense sx={{ maxHeight: 132, overflowY: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                    {patientResults.slice(0, 8).map((p) => (
                      <ListItemButton key={p.id} onClick={() => setSelectedPatient(p)}>
                        <ListItemText primary={`${p.firstName} ${p.lastName || ""}`.trim()} secondary={`${p.patientNumber} • ${p.mobile}`} />
                      </ListItemButton>
                    ))}
                  </List>
                ) : null}
                {selectedPatient ? <Chip label={`Patient: ${selectedPatient.firstName} ${selectedPatient.lastName || ""}`.trim()} color="primary" /> : null}
                <Divider />
                <Typography variant="subtitle2">Status filters</Typography>
                <Stack direction="row" spacing={0.5} flexWrap="wrap">
                  {STATUS_FILTERS.map((key) => (
                    <Chip
                      key={key}
                      size="small"
                      clickable
                      label={friendlyStatusLabel(key)}
                      color={filters[key] ? "primary" : "default"}
                      variant={filters[key] ? "filled" : "outlined"}
                      onClick={() => setFilters((c) => ({ ...c, [key]: !c[key] }))}
                      sx={{ mb: 0.5 }}
                    />
                  ))}
                </Stack>
                <Divider />
                {!isDoctor ? (
                  <Stack direction="row" spacing={0.75} flexWrap="wrap">
                    <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add availability</Button>
                    <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add break</Button>
                    <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add leave</Button>
                    <Button size="small" variant="outlined" onClick={() => setSelected(null)}>New appointment</Button>
                  </Stack>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined" sx={{ height: "100%" }}>
            <CardContent sx={{ height: "100%", p: 1.25, pb: 1.25 }}>
              <Stack spacing={1} sx={{ height: "100%", minHeight: 0 }}>
                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 1, flexWrap: "wrap" }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Operational Calendar</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Grouped clinic scheduler for {effectiveDoctorId ? selectedDoctorLabel : "All Doctors"} on {compactDateLabel(date)}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                    <Chip size="small" label={effectiveDoctorId ? "Specific doctor" : "All Doctors"} color={effectiveDoctorId ? "primary" : "default"} />
                    <Chip size="small" label={date} variant="outlined" />
                  </Stack>
                </Box>

                <Stack direction="row" spacing={0.5} flexWrap="wrap">
                  <Chip size="small" label={`Doctor: ${selectedDoctorLabel}`} color={effectiveDoctorId ? "primary" : "default"} variant="outlined" />
                  <Chip size="small" label={`Date: ${compactDateLabel(date)}`} variant="outlined" />
                  <Chip size="small" label={`Time period: ${currentTimeSection ? currentTimeSection.charAt(0).toUpperCase() + currentTimeSection.slice(1) : "Outside current day"}`} variant="outlined" />
                  <Chip size="small" label={clinicClockUnavailable ? "Clinic time unavailable" : formatClinicClockLabel(clinicTimeZone, clinicNowSnapshot)} variant="outlined" />
                </Stack>

                <Stack direction="row" spacing={0.5} flexWrap="wrap">
                  <Chip size="small" label={`Total ${calendarSummary.totalSlots}`} variant="outlined" />
                  <Chip size="small" label={`Available ${calendarSummary.availableCount}`} color="success" variant="outlined" />
                  <Chip size="small" label={`Booked ${calendarSummary.bookedCount}`} color="info" variant="outlined" />
                  <Chip size="small" label={`Partial ${calendarSummary.partialCount}`} color="warning" variant="outlined" />
                  <Chip size="small" label={`Full ${calendarSummary.fullCount}`} color="error" variant="outlined" />
                  <Chip size="small" label={`Checked in ${calendarSummary.checkedInCount}`} color="info" variant="outlined" />
                  <Chip size="small" label={`In consult ${calendarSummary.inConsultationCount}`} color="secondary" variant="outlined" />
                </Stack>

                <Stack direction="row" spacing={0.75} flexWrap="wrap">
                  <Chip size="small" label="Available" color="success" variant="outlined" />
                  <Chip size="small" label="Booked" color="info" variant="outlined" />
                  <Chip size="small" label="Partial" color="warning" variant="outlined" />
                  <Chip size="small" label="Full" color="error" variant="outlined" />
                  <Chip size="small" label="Checked in" color="info" variant="outlined" />
                  <Chip size="small" label="In consultation" color="secondary" variant="outlined" />
                  <Chip size="small" label="Break / Leave" variant="outlined" />
                  <Chip size="small" label="No slot" variant="outlined" />
                </Stack>

                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Button size="small" variant="outlined" onClick={() => setSectionOverrides({ morning: true, afternoon: true, evening: true, other: true })}>
                    Expand all
                  </Button>
                  <Button size="small" variant="outlined" onClick={() => setSectionOverrides({ morning: false, afternoon: false, evening: false, other: false })}>
                    Collapse all
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => {
                      const liveMinutes = clinicClock.minutes;
                      const liveSection: SchedulerSectionKey = liveMinutes >= 6 * 60 && liveMinutes < 12 * 60
                        ? "morning"
                        : liveMinutes >= 12 * 60 && liveMinutes < 17 * 60
                          ? "afternoon"
                          : liveMinutes >= 17 * 60 && liveMinutes < 22 * 60
                            ? "evening"
                            : "other";
                      setDate(todayDate);
                      setSectionOverrides((current) => ({ ...current, [liveSection]: true }));
                      window.setTimeout(() => {
                        sectionScrollRefs.current[liveSection]?.scrollIntoView({ behavior: "smooth", block: "start" });
                      }, 0);
                    }}
                  >
                    Jump to now
                  </Button>
                </Stack>

                {loading && calendarRows.length === 0 ? (
                  <Box sx={{ minHeight: 260, display: "grid", placeItems: "center" }}>
                    <CircularProgress />
                  </Box>
                ) : visibleDoctorPanels.length === 0 ? (
                  <Alert severity="info">
                    {doctorOptions.length === 0
                      ? "No doctors available for the selected clinic."
                      : "Select All Doctors or a specific doctor to view the day board."}
                  </Alert>
                ) : (
                  <Box
                    ref={gridBodyScrollRef}
                    onScroll={handleBodyGridScroll}
                    sx={{ flex: 1, minHeight: 0, overflowX: "auto", overflowY: "visible" }}
                  >
                    <Stack spacing={1} sx={{ minWidth: gridMinWidth, minHeight: 0 }}>
                    <Box
                      ref={gridTopScrollRef}
                      onScroll={handleTopGridScroll}
                      sx={{
                        overflowX: "auto",
                        overflowY: "hidden",
                        borderBottom: "1px solid",
                        borderColor: "divider",
                        pb: 0.25,
                      }}
                    >
                      <Box sx={{ minWidth: gridMinWidth, height: 1 }} />
                    </Box>

                    {calendarRows.length === 0 ? (
                    <CompactEmptyState
                      title="No visible slots"
                      subtitle={effectiveDoctorId
                        ? "Add availability to generate slots for the selected date and doctor."
                        : "No rows match the current filters."}
                    />
                    ) : null}

                    <Stack spacing={1} sx={{ flex: 1, minHeight: 0 }}>
                      {schedulerSections.map((section) => (
                        <Accordion
                          key={section.key}
                          ref={(node) => {
                            sectionScrollRefs.current[section.key] = node as HTMLDivElement | null;
                          }}
                          expanded={section.expanded}
                          onChange={(_, expanded) => setSectionOverrides((current) => ({ ...current, [section.key]: expanded }))}
                          disableGutters
                          sx={{
                            minWidth: gridMinWidth,
                            border: "1px solid",
                            borderColor: "divider",
                            borderRadius: 2,
                            overflow: "hidden",
                            "&:before": { display: "none" },
                            "& .MuiAccordionSummary-root": { minHeight: 52, px: 1.25 },
                            "& .MuiAccordionSummary-content": { my: 0.75 },
                            "& .MuiAccordionDetails-root": { px: 1.25, pb: 1.25, pt: 0 },
                          }}
                        >
                          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Stack spacing={0.5} sx={{ width: "100%" }}>
                              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                                <Stack spacing={0.1}>
                                  <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, flexWrap: "wrap" }}>
                                    <Typography sx={{ fontWeight: 800 }}>{section.label}</Typography>
                                    <Chip size="small" label={section.rangeLabel} color={sectionTone(section.key)} variant="outlined" sx={{ height: 20 }} />
                                    {section.autoExpanded ? <Chip size="small" label="Auto" color="primary" variant="outlined" sx={{ height: 20 }} /> : null}
                                  </Box>
                                    <Typography variant="caption" color="text.secondary">
                                      {section.times.length} visible times • {section.summary.bookedCount} booked • {section.summary.partialCount} partial • {section.summary.fullCount} full
                                    </Typography>
                                </Stack>
                                <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                                  <Chip size="small" label={`${section.summary.totalSlots} total`} variant="outlined" />
                                  <Chip size="small" label={`${section.summary.availableCount} available`} color="success" variant="outlined" />
                                  <Chip size="small" label={`${section.summary.checkedInCount} checked in`} color="info" variant="outlined" />
                                  <Chip size="small" label={`${section.summary.inConsultationCount} in consult`} color="secondary" variant="outlined" />
                                </Stack>
                              </Box>
                            </Stack>
                          </AccordionSummary>
                          <AccordionDetails>
                            <Box
                              sx={{
                                maxHeight: 372,
                                overflowY: "auto",
                                overflowX: "hidden",
                                scrollbarGutter: "stable both-edges",
                                pr: 1.5,
                              }}
                            >
                              <Table stickyHeader size="small" sx={{ minWidth: gridMinWidth, tableLayout: "fixed" }}>
                                <TableHead>
                                  <TableRow>
                                    <TableCell
                                      sx={{
                                        position: "sticky",
                                        left: 0,
                                        zIndex: 4,
                                        bgcolor: "background.paper",
                                        minWidth: 92,
                                        width: 92,
                                        fontWeight: 800,
                                        py: 0.6,
                                      }}
                                    >
                                      Time
                                    </TableCell>
                                    {visibleDoctorPanels.map((panel) => (
                                      <TableCell
                                        key={panel.doctorUserId}
                                        sx={{
                                          minWidth: 188,
                                          fontWeight: 800,
                                          verticalAlign: "top",
                                          py: 0.6,
                                        }}
                                      >
                                        <Stack spacing={0.25}>
                                          <Typography variant="subtitle2" sx={{ fontWeight: 800, lineHeight: 1.1 }}>{panel.doctorName}</Typography>
                                          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.1 }}>
                                            {panel.slots.length} slots • {appointments.filter((appt) => appt.doctorUserId === panel.doctorUserId).length} appointments
                                          </Typography>
                                        </Stack>
                                      </TableCell>
                                    ))}
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {section.times.length === 0 ? (
                                    <TableRow>
                                      <TableCell colSpan={Math.max(1, visibleDoctorPanels.length + 1)} sx={{ py: 2 }}>
                                        <CompactEmptyState
                                          title="No visible slots in this section"
                                          subtitle="Try another time bucket or expand the filters."
                                        />
                                      </TableCell>
                                    </TableRow>
                                  ) : section.times.map((time) => (
                                    <TableRow key={`${section.key}-${time}`} hover>
                                      <TableCell
                                        sx={{
                                          position: "sticky",
                                          left: 0,
                                          zIndex: 3,
                                          bgcolor: "background.paper",
                                          fontWeight: 800,
                                          whiteSpace: "nowrap",
                                          minWidth: 92,
                                          width: 92,
                                          py: 0.4,
                                        }}
                                      >
                                        {time}
                                      </TableCell>
                                      {visibleDoctorPanels.map((panel) => {
                                        const slot = panel.slots.find((candidate) => toFive(candidate.slotTime) === time) || null;
                                        const slotAppointments = cellAppointments(panel.doctorUserId, time);
                                        const summary = summarizeCellStatus(slot, slotAppointments, date, clinicTimeZone, clinicNowSnapshot);
                                        const slotContext = slot ? slotPresentation(date, slot, clinicTimeZone, clinicNowSnapshot) : null;
                                        const primaryAppointment = slotAppointments[0] || null;
                                        const selectedCell = (selectedSlot?.doctorUserId === panel.doctorUserId && toFive(selectedSlot.slotTime) === time)
                                          || (selectedAppointment?.doctorUserId === panel.doctorUserId && toFive(selectedAppointment.appointmentTime) === time);
                                        const cellVisible = filters[summary.status as SlotFilterKey] !== false;
                                        const moreCount = Math.max(0, slotAppointments.length - 1);
                                        const capacityText = slot
                                          ? `${slot.bookedCount}/${slot.maxPatientsPerSlot}`
                                          : slotAppointments.length > 0
                                            ? `${slotAppointments.length} booking${slotAppointments.length > 1 ? "s" : ""}`
                                            : "—";
                                        let tooltip: React.ReactNode = null;
                                        if (primaryAppointment) {
                                          if (slot) {
                                            tooltip = appointmentTooltip(primaryAppointment, panel.doctorName, slot);
                                          } else if (slotContext?.tooltip) {
                                            tooltip = slotContext.tooltip;
                                          } else {
                                            tooltip = (
                                              <Box sx={{ p: 0.5, maxWidth: 280 }}>
                                                <Typography sx={{ fontWeight: 800 }}>{appointmentTitle(primaryAppointment)}</Typography>
                                                <Typography variant="body2">Phone: {primaryAppointment.patientMobile || "—"}</Typography>
                                                <Typography variant="body2">Purpose: {primaryAppointment.type}</Typography>
                                                <Typography variant="body2">Status: {friendlyStatusLabel(primaryAppointment.status)}</Typography>
                                                <Typography variant="body2">Doctor: {panel.doctorName}</Typography>
                                                <Typography variant="body2">Slot: {toFive(primaryAppointment.appointmentTime)}</Typography>
                                                <Typography variant="body2">Reference: {primaryAppointment.displayReference || (primaryAppointment.tokenNumber != null ? `APT-${primaryAppointment.tokenNumber}` : "Pending")}</Typography>
                                              </Box>
                                            );
                                          }
                                        }
                                        const firstPatient = primaryAppointment ? appointmentTitle(primaryAppointment) : slot?.patientName || slot?.patientNumber || slot?.patientId || null;
                                        const cellContent = !slot && slotAppointments.length === 0 ? (
                                          <Box
                                            sx={{
                                              minHeight: 38,
                                              display: "grid",
                                              placeItems: "center",
                                              borderRadius: 1,
                                              border: "1px dashed",
                                              borderColor: "divider",
                                              color: "text.disabled",
                                              px: 0.5,
                                            }}
                                          >
                                            —
                                          </Box>
                                        ) : (
                                          <Box
                                            role="button"
                                            tabIndex={0}
                                            onClick={() => {
                                              if (slotContext?.isPast) {
                                                return;
                                              }
                                              if (slotAppointments.length > 0 && primaryAppointment) {
                                                setSelected({ kind: "appointment", appointment: primaryAppointment });
                                                return;
                                              }
                                              if (slot && slotContext?.selectable) {
                                                setSelected({ kind: "slot", slot });
                                              }
                                            }}
                                            onDragOver={(event) => {
                                              if (!isDoctor && canDropToSlot(slot)) {
                                                event.preventDefault();
                                                event.dataTransfer.dropEffect = "move";
                                              }
                                            }}
                                            onDrop={(event) => {
                                              event.preventDefault();
                                              if (!isDoctor && slot) {
                                                openMoveConfirm(slot);
                                              }
                                            }}
                                            sx={{
                                              minHeight: 38,
                                              p: 0.4,
                                              pr: 0.75,
                                              display: "flex",
                                              flexDirection: "column",
                                              gap: 0.2,
                                              justifyContent: "space-between",
                                              borderRadius: 1,
                                              border: "1px solid",
                                              borderColor: cellBorder(summary.displayState, Boolean(selectedCell)),
                                              bgcolor: cellBackground(summary.displayState),
                                              opacity: cellVisible ? 1 : 0.42,
                                              cursor: (slotContext?.selectable || slotAppointments.length > 0) ? "pointer" : "default",
                                              transition: "all 120ms ease",
                                              minWidth: 0,
                                              "&:hover": {
                                                boxShadow: 2,
                                                transform: "translateY(-1px)",
                                              },
                                            }}
                                            onKeyDown={(event) => {
                                              if (event.key === "Enter" || event.key === " ") {
                                                event.preventDefault();
                                                if (slotContext?.isPast) {
                                                  return;
                                                }
                                                if (slotAppointments.length > 0 && primaryAppointment) {
                                                  setSelected({ kind: "appointment", appointment: primaryAppointment });
                                                } else if (slot && slotContext?.selectable) {
                                                  setSelected({ kind: "slot", slot });
                                                }
                                              }
                                            }}
                                          >
                                            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 0.5, pr: 0.5 }}>
                                              <Chip
                                                size="small"
                                                label={summary.label}
                                                color={slotCellColor(summary.displayState)}
                                                variant="outlined"
                                                sx={{ height: 20, fontSize: "0.68rem", "& .MuiChip-label": { px: 0.75 } }}
                                              />
                                              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, lineHeight: 1 }}>
                                                {capacityText}
                                              </Typography>
                                            </Box>
                                            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.25, minHeight: 0 }}>
                                              {slotAppointments.length > 0 && primaryAppointment ? (
                                                <>
                                                  <Chip
                                                    size="small"
                                                    label={firstPatient}
                                                    color={appointmentColor(primaryAppointment.status)}
                                                    variant="filled"
                                                    draggable={!isDoctor && isDragEligibleAppointment(primaryAppointment)}
                                                    onDragStart={(event) => {
                                                      event.dataTransfer.setData("text/plain", primaryAppointment.id);
                                                      event.dataTransfer.effectAllowed = "move";
                                                      startDragAppointment(primaryAppointment);
                                                    }}
                                                    onDragEnd={() => setDraggedAppointment(null)}
                                                    sx={{
                                                      height: 20,
                                                      fontSize: "0.68rem",
                                                      maxWidth: "100%",
                                                      "& .MuiChip-label": {
                                                        px: 0.75,
                                                        overflow: "hidden",
                                                        textOverflow: "ellipsis",
                                                      },
                                                    }}
                                                  />
                                                  {moreCount > 0 ? <Chip size="small" label={`+${moreCount}`} variant="outlined" sx={{ height: 18, fontSize: "0.64rem", "& .MuiChip-label": { px: 0.75 } }} /> : null}
                                                </>
                                              ) : slot ? (
                                                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>
                                                  {summary.displayState === "AVAILABLE" ? "Open" : friendlyStatusLabel(summary.displayState)}
                                                </Typography>
                                              ) : null}
                                            </Box>
                                          </Box>
                                        );
                                        const contentNode = tooltip ? (
                                          <Tooltip title={tooltip} arrow placement="top">
                                            <Box component="div">{cellContent}</Box>
                                          </Tooltip>
                                        ) : cellContent;

                                        return (
                                          <TableCell
                                            key={`${section.key}-${panel.doctorUserId}-${time}`}
                                            sx={{
                                              minWidth: 188,
                                              p: 0.25,
                                              verticalAlign: "top",
                                              borderBottom: "1px solid",
                                              borderColor: "divider",
                                            }}
                                          >
                                            {contentNode}
                                          </TableCell>
                                        );
                                      })}
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </Box>
                          </AccordionDetails>
                        </Accordion>
                      ))}
                    </Stack>
                  </Stack>
                </Box>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 3 }}>
          <Card variant="outlined" sx={{ position: { md: "sticky" }, top: { md: 16 } }}>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Details</Typography>
                {!selected ? <CompactEmptyState title="Select a slot or appointment" /> : null}

                {selectedSlot ? (
                  <Stack spacing={1}>
                    <Typography variant="body2">Doctor: {selectedSlot.doctorName || displayDoctorName(users, selectedSlot.doctorUserId)}</Typography>
                    <Typography variant="body2">Time: {toFive(selectedSlot.slotTime)} - {toFive(selectedSlot.slotEndTime)}</Typography>
                    <Typography variant="body2">Capacity: {selectedSlot.bookedCount}/{selectedSlot.maxPatientsPerSlot}</Typography>
                    <Chip size="small" label={friendlyStatusLabel(selectedSlot.status)} color={slotColor(selectedSlot.status)} sx={{ width: "fit-content" }} />
                    {selectedPatient ? (
                      <Chip size="small" label={`Patient: ${selectedPatient.firstName} ${selectedPatient.lastName || ""}`.trim()} color="primary" variant="outlined" sx={{ width: "fit-content" }} />
                    ) : null}
                    {selectedSlotAppointments.length > 0 ? (
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">Appointments in this slot</Typography>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap">
                          {selectedSlotAppointments.map((appointment) => (
                            <Chip
                              key={appointment.id}
                              size="small"
                              label={appointmentTitle(appointment)}
                              color={appointmentColor(appointment.status)}
                              variant="outlined"
                              onClick={() => setSelected({ kind: "appointment", appointment })}
                            />
                          ))}
                        </Stack>
                      </Stack>
                    ) : null}
                    {!isDoctor ? (
                      <>
                        <FormControl size="small" fullWidth>
                          <InputLabel id="db-type">Type</InputLabel>
                          <Select labelId="db-type" label="Type" value={appointmentType} onChange={(e) => setAppointmentType(e.target.value as AppointmentType)}>
                            {APPOINTMENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                          </Select>
                        </FormControl>
                        <TextField size="small" label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
                        <Button variant="contained" disabled={!selectedSlotCanBook || saving} onClick={openBookingFlow}>
                          Book appointment
                        </Button>
                        {selectedSlotBookingReason ? (
                          <Typography variant="caption" color="text.secondary">
                            {selectedSlotBookingReason}
                          </Typography>
                        ) : null}
                        <Button variant="outlined" disabled={!canBook || !selectedPatient} onClick={() => void addWaitlistFromSelection()}>
                          Add to waitlist
                        </Button>
                      </>
                    ) : (
                      <Alert severity="info">Doctor schedule is read-only. Use Queue to start consultation.</Alert>
                    )}
                  </Stack>
                ) : null}

                {selectedAppointment ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{selectedAppointment.patientName || selectedAppointment.patientNumber || selectedAppointment.patientId}</Typography>
                    <Typography variant="caption" color="text.secondary">{selectedAppointment.appointmentDate} {toFive(selectedAppointment.appointmentTime)}</Typography>
                    <AppointmentTokenChip appointment={selectedAppointment} />
                    <Typography variant="caption" color="text.secondary">{formatRelativeBookingTime(selectedAppointment.createdAt) || "Booked recently"}</Typography>
                    <Typography variant="caption" color="text.secondary">{selectedAppointment.patientNumber ? `Patient No: ${selectedAppointment.patientNumber}` : "Patient No: Not assigned"}</Typography>
                    <Typography variant="body2">Doctor: {selectedAppointment.doctorName || displayDoctorName(users, selectedAppointment.doctorUserId)}</Typography>
                    <Typography variant="body2">Phone: {selectedAppointment.patientMobile || "—"}</Typography>
                    <Typography variant="body2">Reference: {selectedAppointment.displayReference || (selectedAppointment.tokenNumber != null ? `APT-${selectedAppointment.tokenNumber}` : "Pending")}</Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap">
                      <WorkflowStatusBadge status={selectedAppointment.status} />
                      <Chip
                        size="small"
                        label={`Next: ${getNextWorkflowAction({ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus, billDueAmount: selectedAppointmentFee?.dueAmount }).label}`}
                        variant="outlined"
                      />
                    </Stack>
                    <Typography variant="body2">Consultation fee: {formatMoney(selectedAppointmentFee?.consultationFee)}</Typography>
                    <Chip
                      size="small"
                      label={feeStatusLabel(selectedAppointmentFee?.feeStatus || "NOT_CONFIGURED", selectedAppointmentFee?.feeStatus === "PAID" ? selectedAppointmentFee?.consultationFee : selectedAppointmentFee?.dueAmount)}
                      color={feeStatusColor(selectedAppointmentFee?.feeStatus || "NOT_CONFIGURED")}
                      variant="outlined"
                      sx={{ width: "fit-content" }}
                    />
                    <Typography variant="body2">Queue: {selectedAppointment.status === "WAITING" ? "Checked in" : friendlyStatusLabel(selectedAppointment.status)}</Typography>
                    <Typography variant="body2">Consultation: {selectedAppointment.consultationId ? "Started" : "Not started"}</Typography>
                    <PatientJourneyTracker context={{ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus, billDueAmount: selectedAppointmentFee?.dueAmount }} compact />
                    <Stack direction="row" gap={0.75} flexWrap="wrap">
                      {canCollect && selectedAppointmentFee?.consultationFee != null && selectedAppointmentFee.consultationFee > 0 && selectedAppointmentFee.feeStatus !== "PAID" ? (
                        <Button size="small" variant={getNextWorkflowAction({ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus, billDueAmount: selectedAppointmentFee?.dueAmount }).key === "collect-fee" ? "contained" : "outlined"} onClick={() => setFeeDialog({ appointment: selectedAppointment, action: "collect" })}>Collect Fee</Button>
                      ) : null}
                      {selectedAppointmentFee?.bill ? (
                        <Button size="small" variant="outlined" onClick={() => navigate(`/billing?appointmentId=${selectedAppointment.id}`)}>View Payment</Button>
                      ) : null}
                      {!isDoctor ? (
                        <>
                          <Button size="small" variant={getNextWorkflowAction({ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus, billDueAmount: selectedAppointmentFee?.dueAmount }).key === "check-in" ? "contained" : "outlined"} disabled={!canManage} onClick={() => void checkInAppointment(selectedAppointment)}>Check-in</Button>
                          <Button size="small" variant="outlined" disabled={!canManage} onClick={() => void transitionStatus(selectedAppointment.id, "NO_SHOW")}>No-show</Button>
                          <Button size="small" variant="outlined" disabled={!canManage} onClick={() => void transitionStatus(selectedAppointment.id, "CANCELLED")}>Cancel</Button>
                        </>
                      ) : null}
                      {!isDoctor && selectedAppointment.status === "BOOKED" ? (
                        <Button size="small" variant="outlined" onClick={() => openReschedule(selectedAppointment)}>Reschedule</Button>
                      ) : null}
                      {isDoctor && canStartConsultation && (selectedAppointment.status === "WAITING" || selectedAppointment.status === "IN_CONSULTATION") ? (
                        <Button size="small" variant={getNextWorkflowAction({ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus }).key === "continue-consultation" ? "contained" : "outlined"} disabled={!canStartConsultation} onClick={() => void startConsultation(selectedAppointment.id)}>
                          {selectedAppointment.consultationId ? "Continue consultation" : "Start consultation"}
                        </Button>
                      ) : null}
                      {!isDoctor && canStartConsultation ? (
                        <Button size="small" variant={getNextWorkflowAction({ status: selectedAppointment.status, paymentStatus: selectedAppointmentFee?.feeStatus }).key === "start-consultation" ? "contained" : "outlined"} disabled={!canStartConsultation} onClick={() => void startConsultation(selectedAppointment.id)}>Start consultation</Button>
                      ) : null}
                      <Button size="small" onClick={() => navigate(`/patients/${selectedAppointment.patientId}`)}>Open patient</Button>
                      {canOpenWorkspace && selectedAppointment.consultationId ? (
                        <Button size="small" disabled={!selectedAppointment.consultationId} onClick={() => selectedAppointment.consultationId && navigate(`/consultations/${selectedAppointment.consultationId}`)}>Open consultation</Button>
                      ) : null}
                    </Stack>
                  </Stack>
                ) : null}

                {!effectiveDoctorId && !selectedSlot ? (
                  <Alert severity="info">Select a slot in the grid to create bookings. Manual time booking requires selecting a specific doctor.</Alert>
                ) : effectiveDoctorId && !slots.length ? (
                  <Stack spacing={1}>
                    <CompactEmptyState title="No configured schedule" subtitle="Manual appointment time is enabled." />
                    <TextField size="small" type="time" label="Manual time" value={manualTime} onChange={(e) => setManualTime(e.target.value)} InputLabelProps={{ shrink: true }} />
                    <Button variant="contained" disabled={!canBook || !selectedPatient || !manualTime} onClick={() => void bookManualAppointment()}>Book manual time</Button>
                  </Stack>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card variant="outlined" sx={{ mt: 2 }}>
            <CardContent sx={{ p: 1.25 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Waitlist</Typography>
              {!effectiveDoctorId && !selectedSlot ? (
                <CompactEmptyState title="Select a doctor or grid cell" subtitle="Waitlist entries appear here." />
              ) : activeWaitlist.length === 0 ? <Alert severity="info">No waitlist entries.</Alert> : (
                <Stack spacing={1}>
                  {activeWaitlist.map((entry) => (
                    <Box key={entry.id} sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1 }}>
                      <Typography variant="caption">{entry.patientName || entry.patientNumber || entry.patientId}</Typography>
                      <Button size="small" variant="outlined" disabled={!selectedSlot || !selectedSlot.selectable || !canBook} onClick={() => void bookWaitlistEntry(entry)}>Book</Button>
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Reschedule appointment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1, minWidth: { sm: 480 } }}>
            <FormControl fullWidth>
              <InputLabel id="reschedule-doctor">Doctor</InputLabel>
              <Select labelId="reschedule-doctor" label="Doctor" value={rescheduleDoctorUserId} onChange={(e) => setRescheduleDoctorUserId(String(e.target.value))}>
                {doctorOptions.map((d) => (
                  <MenuItem key={d.appUserId} value={d.appUserId}>{d.displayName || d.email || d.appUserId}</MenuItem>
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

      <Dialog open={moveConfirmOpen} onClose={() => { setMoveConfirmOpen(false); setMoveConfirmTarget(null); setDraggedAppointment(null); }} fullWidth maxWidth="sm">
        <DialogTitle>Move appointment</DialogTitle>
        <DialogContent>
          {draggedAppointment && moveConfirmTarget ? (
            <Stack spacing={1.25} sx={{ pt: 1 }}>
              <Alert severity="info">
                Move {appointmentTitle(draggedAppointment)} to {moveConfirmTarget.doctorName || displayDoctorName(users, moveConfirmTarget.doctorUserId)} at {toFive(moveConfirmTarget.slotTime)}?
              </Alert>
              <Typography variant="body2" color="text.secondary">
                This will reschedule the appointment using the existing reschedule flow and refresh the schedule after save.
              </Typography>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setMoveConfirmOpen(false); setMoveConfirmTarget(null); setDraggedAppointment(null); }}>Cancel</Button>
          <Button variant="contained" onClick={() => void confirmMoveAppointment()} disabled={!draggedAppointment || !moveConfirmTarget}>Move</Button>
        </DialogActions>
      </Dialog>

      {feeDialog ? (
        <ConsultationFeeDialog
          open
          title={feeDialog.action === "collect-and-check-in" ? "Collect consultation fee and check in" : "Collect consultation fee"}
          appointmentLabel={`${feeDialog.appointment.appointmentDate} ${toFive(feeDialog.appointment.appointmentTime)}`}
          doctorLabel={`Doctor: ${feeDialog.appointment.doctorName || displayDoctorName(users, feeDialog.appointment.doctorUserId)}`}
          patientLabel={`Patient: ${feeDialog.appointment.patientName || feeDialog.appointment.patientNumber || feeDialog.appointment.patientId}`}
          feeLabel={`Consultation fee: ${formatMoney(selectedAppointmentFee?.consultationFee ?? null)}`}
          submitLabel={feeDialog.action === "collect-and-check-in" ? "Collect & Check-in" : "Collect Fee"}
          onClose={() => setFeeDialog(null)}
          onSubmit={submitFeeDialog}
        />
      ) : null}
    </Stack>
  );
}
