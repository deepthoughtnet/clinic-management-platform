import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
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
  Grid,
  InputLabel,
  IconButton,
  MenuItem,
  Menu,
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
import MoreVertRoundedIcon from "@mui/icons-material/MoreVertRounded";

import ConsultationFeeDialog, { type ConsultationFeeDialogValue } from "../../components/ConsultationFeeDialog";
import { ClinicalIntakeDialog } from "../../components/clinical/ClinicalIntakeDialog";
import { CompactEmptyState, CompactStatCard, CompactTableFrame, compactChipSx, WorkflowStrip } from "../../components/compact/CompactUi";
import { ReceiptPrintDialog, type ReceiptPrintData } from "../../components/finance/PrintableBillingDocuments";
import { AppointmentTokenChip, PatientJourneyTracker, WorkflowStatusBadge } from "../../components/workflow/WorkflowUx";
import { useAuth } from "../../auth/useAuth";
import {
  collectConsultationFee,
  getClinicProfile,
  getReceiptPdf,
  getClinicUsers,
  listBillPayments,
  reorderDoctorQueueToday,
  searchAppointments,
  searchBills,
  startConsultationFromAppointment,
  sendReceipt,
  updateAppointmentPriority,
  updateAppointmentStatus,
  type Appointment,
  type AppointmentPriority,
  type Bill,
  type ClinicUser,
  type ClinicProfile,
  type Payment,
  type PaymentMode,
  type Receipt,
} from "../../api/clinicApi";
import { formatRelativeBookingTime, getNextWorkflowAction, getWorkflowStatusLabel } from "../../components/workflow/workflowHelpers";
import { staffDisplayName } from "../../utils/staffDisplay";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

function formatMoney(value: number | null | undefined) {
  if (value == null) return "—";
  return value.toFixed(2);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function priorityColor(priority: AppointmentPriority | null | undefined) {
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

function queuePriorityRank(priority: AppointmentPriority | null | undefined) {
  switch ((priority || "NORMAL").toUpperCase()) {
    case "URGENT":
      return 0;
    case "MANUAL_PRIORITY":
      return 1;
    case "FOLLOW_UP":
      return 2;
    case "ELDERLY":
      return 3;
    case "CHILD":
      return 4;
    default:
      return 5;
  }
}

function queueTier(status: Appointment["status"]) {
  switch (status) {
    case "WAITING":
      return 0;
    case "BOOKED":
      return 1;
    case "IN_CONSULTATION":
      return 2;
    case "COMPLETED":
    case "CANCELLED":
    case "NO_SHOW":
      return 3;
  }
}

function isValidTenantId(tenantId: string | null | undefined) {
  if (!tenantId) {
    return false;
  }
  return UUID_PATTERN.test(tenantId) && !tenantId.toUpperCase().startsWith("DEFAULT-ROLES");
}

function isQueueAppointment(status: Appointment["status"]) {
  return status === "BOOKED" || status === "WAITING" || status === "IN_CONSULTATION" || status === "COMPLETED" || status === "CANCELLED" || status === "NO_SHOW";
}

function isActiveQueueAppointment(status: Appointment["status"]) {
  return status === "BOOKED" || status === "WAITING";
}

function displayDoctorName(users: ClinicUser[], doctorUserId: string | null | undefined) {
  if (!doctorUserId) return "Unassigned";
  return users.find((u) => u.appUserId === doctorUserId)?.displayName || doctorUserId;
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
    consultationBill: consultationEffectiveBill(bills),
    consultationFee: effectiveFee,
    feeStatus,
    feeDueAmount: due,
    feePaidAmount: effectiveFee == null ? null : Math.min(effectiveFee, netPaid),
  };
}

function canShowCollectFee(row: QueueViewRow) {
  if (row.status === "CANCELLED" || row.status === "COMPLETED") {
    return false;
  }
  if (row.status === "NO_SHOW" && !row.consultationBill) {
    return false;
  }
  return row.feeStatus !== "PAID";
}

function canShowPayAfterConsultation(row: QueueViewRow) {
  return row.status === "BOOKED" && row.feeStatus !== "PAID" && row.feeStatus !== "NOT_CONFIGURED";
}

function canShowViewBilling(row: QueueViewRow, canViewBillingData: boolean) {
  return Boolean(row.consultationBill) || canViewBillingData;
}

type FeeStatus = "NOT_CONFIGURED" | "UNPAID" | "PARTIAL" | "PAID";

type QueueViewRow = Appointment & {
  consultationFeeAmount: number | null;
  consultationBill: Bill | null;
  feeStatus: FeeStatus;
  feeDueAmount: number | null;
  feePaidAmount: number | null;
};

type FeeDialogState = {
  appointment: QueueViewRow;
  action: "collect" | "collect-and-check-in";
};

type QueueReceiptRecord = {
  billId: string;
  billNumber: string;
  payment: Payment;
};

type QueueReceiptPanelState = {
  appointment: QueueViewRow;
  action: FeeDialogState["action"];
  receipt: Receipt;
  payment: Payment;
  bill: Bill;
  receiptPrintData: ReceiptPrintData;
};

type CheckInBypassReason =
  | "EMERGENCY"
  | "DOCTOR_APPROVED"
  | "PATIENT_WILL_PAY_AFTER_CONSULTATION"
  | "BILLING_COUNTER_UNAVAILABLE"
  | "OTHER";

type CheckInBypassDialogState = {
  appointment: QueueViewRow;
  reason: CheckInBypassReason;
  notes: string;
};

type CancellationDialogState = {
  appointment: QueueViewRow;
  nextStatus: "CANCELLED" | "NO_SHOW";
  variant: "paid-cancel" | "reason";
  reason: string;
};

const CHECK_IN_BYPASS_REASON_OPTIONS: Array<{ value: CheckInBypassReason; label: string }> = [
  { value: "EMERGENCY", label: "Emergency" },
  { value: "DOCTOR_APPROVED", label: "Doctor approved" },
  { value: "PATIENT_WILL_PAY_AFTER_CONSULTATION", label: "Patient will pay after consultation" },
  { value: "BILLING_COUNTER_UNAVAILABLE", label: "Billing counter unavailable" },
  { value: "OTHER", label: "Other" },
];

const QUEUE_WORKFLOW_STEPS = [
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

export default function QueuePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const doctorUserIdFromQuery = searchParams.get("doctorUserId") || "";
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState(doctorUserIdFromQuery);
  const [queueSearch, setQueueSearch] = React.useState("");
  const [loadingDoctors, setLoadingDoctors] = React.useState(true);
  const [loadingQueue, setLoadingQueue] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [savingId, setSavingId] = React.useState<string | null>(null);
  const [draggingId, setDraggingId] = React.useState<string | null>(null);
  const [feeDialog, setFeeDialog] = React.useState<FeeDialogState | null>(null);
  const [bypassDialog, setBypassDialog] = React.useState<CheckInBypassDialogState | null>(null);
  const [cancellationDialog, setCancellationDialog] = React.useState<CancellationDialogState | null>(null);
  const [clinicalIntakeAppointment, setClinicalIntakeAppointment] = React.useState<QueueViewRow | null>(null);
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [receiptRecordsByAppointmentId, setReceiptRecordsByAppointmentId] = React.useState<Record<string, QueueReceiptRecord>>({});
  const [receiptPanel, setReceiptPanel] = React.useState<QueueReceiptPanelState | null>(null);
  const [receiptPreview, setReceiptPreview] = React.useState<ReceiptPrintData | null>(null);
  const [receiptPreviewLoading, setReceiptPreviewLoading] = React.useState(false);
  const [receiptAutoPrint, setReceiptAutoPrint] = React.useState(false);
  const [rowMenuAnchor, setRowMenuAnchor] = React.useState<HTMLElement | null>(null);
  const [rowMenuAppointmentId, setRowMenuAppointmentId] = React.useState<string | null>(null);
  const [receiptActionLoading, setReceiptActionLoading] = React.useState(false);

  const today = React.useMemo(() => localDateKey(), []);
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = auth.rolesUpper.includes("DOCTOR") || tenantRole === "DOCTOR";
  const isClinicAdmin = tenantRole === "CLINIC_ADMIN";
  const isReceptionist = tenantRole === "RECEPTIONIST";
  const canCollectFee = !isDoctor && (auth.hasPermission("billing.create") || auth.hasPermission("payment.collect"));
  const canViewBillingData = !isDoctor && (auth.hasPermission("billing.read") || canCollectFee);
  const canSendReceipt = !isDoctor && (auth.hasPermission("payment.collect") || auth.hasPermission("notification.send"));
  const canBypassPaymentCheckIn = auth.hasPermission("appointment.checkin.payment_bypass");
  const canStartConsultation = isDoctor && auth.hasPermission("consultation.create");
  const canManageDeskStatus = (isClinicAdmin || isReceptionist) && auth.hasPermission("appointment.manage");
  const canReorderQueue = (isDoctor || isClinicAdmin || isReceptionist) && auth.hasPermission("appointment.manage") && Boolean(doctorUserId || isDoctor);
  const tenantReady = isValidTenantId(auth.tenantId);
  const effectiveDoctorId = isDoctor && auth.appUserId ? auth.appUserId : doctorUserId || null;

  React.useEffect(() => {
    if (!isDoctor) {
      setDoctorUserId(doctorUserIdFromQuery);
    }
  }, [doctorUserIdFromQuery, isDoctor]);

  const doctors = React.useMemo(
    () => users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR"),
    [users],
  );

  const queueRows = React.useMemo(() => {
    const filtered = appointments.filter((appointment) => isQueueAppointment(appointment.status));
    return [...filtered].sort((left, right) => {
      if (!effectiveDoctorId) {
        const doctorDelta = (left.doctorName || displayDoctorName(users, left.doctorUserId)).localeCompare(right.doctorName || displayDoctorName(users, right.doctorUserId));
        if (doctorDelta !== 0) return doctorDelta;
        const timeDelta = (left.appointmentTime || "").localeCompare(right.appointmentTime || "");
        if (timeDelta !== 0) return timeDelta;
        return left.createdAt.localeCompare(right.createdAt);
      }
      const tierDelta = queueTier(left.status) - queueTier(right.status);
      if (tierDelta !== 0) return tierDelta;
      if (left.status === "WAITING" || left.status === "BOOKED") {
        const priorityDelta = queuePriorityRank(left.priority) - queuePriorityRank(right.priority);
        if (priorityDelta !== 0) return priorityDelta;
      }
      const tokenDelta = (left.tokenNumber ?? Number.MAX_SAFE_INTEGER) - (right.tokenNumber ?? Number.MAX_SAFE_INTEGER);
      if (tokenDelta !== 0) return tokenDelta;
      const timeDelta = (left.appointmentTime || "").localeCompare(right.appointmentTime || "");
      if (timeDelta !== 0) return timeDelta;
      return left.createdAt.localeCompare(right.createdAt);
    });
  }, [appointments, effectiveDoctorId, users]);

  const queueRowsWithFee = React.useMemo<QueueViewRow[]>(() => {
    return queueRows.map((appointment) => {
      const consultationBills = appointment.id ? consultationBillsByAppointment(bills, appointment.id) : [];
      const feeSummary = consultationFeeSummary(appointment, consultationBills);
      const receiptRecord = receiptRecordsByAppointmentId[appointment.id] || null;
      const feeStatus: FeeStatus = receiptRecord?.payment.receiptId
        ? "PAID"
        : feeSummary.feeStatus;
      const feeAmount = receiptRecord?.payment.receiptId
        ? receiptRecord.payment.amount
        : feeSummary.consultationFee;
      return {
        ...appointment,
        consultationBill: feeSummary.consultationBill,
        consultationFeeAmount: feeAmount,
        feeStatus,
        feeDueAmount: receiptRecord?.payment.receiptId ? 0 : feeSummary.feeDueAmount,
        feePaidAmount: receiptRecord?.payment.receiptId ? receiptRecord.payment.amount : feeSummary.feePaidAmount,
      };
    });
  }, [bills, queueRows, receiptRecordsByAppointmentId]);

  const visibleRows = React.useMemo(() => {
    const term = queueSearch.trim().toLowerCase();
    if (!term) return queueRowsWithFee;
    return queueRowsWithFee.filter((appointment) => {
      const doctorName = appointment.doctorName || displayDoctorName(users, appointment.doctorUserId);
      return [
        appointment.tokenNumber?.toString(),
        doctorName,
        appointment.patientName,
        appointment.patientNumber,
        appointment.reason,
        appointment.status,
        appointment.priority,
        getWorkflowStatusLabel(appointment.status),
        appointment.appointmentTime,
        appointment.consultationBill?.billNumber,
      ].filter(Boolean).some((value) => String(value).toLowerCase().includes(term));
    });
  }, [queueRowsWithFee, queueSearch, users]);

  const summary = React.useMemo(() => {
    const rows = queueRowsWithFee;
    return {
      totalBooked: rows.length,
      paymentPending: rows.filter((row) => row.feeStatus === "UNPAID" || row.feeStatus === "PARTIAL").length,
      readyForCheckIn: rows.filter((row) => row.status === "BOOKED" && (row.feeStatus === "PAID" || row.feeStatus === "NOT_CONFIGURED")).length,
      checkedIn: rows.filter((row) => row.status === "WAITING").length,
      inConsultation: rows.filter((row) => row.status === "IN_CONSULTATION").length,
      completed: rows.filter((row) => row.status === "COMPLETED").length,
      cancelled: rows.filter((row) => row.status === "NO_SHOW" || row.status === "CANCELLED").length,
    };
  }, [queueRowsWithFee]);

  const loadReceiptRecords = React.useCallback(async (billRows: Bill[]) => {
    if (!auth.accessToken || !auth.tenantId || !canViewBillingData || billRows.length === 0) {
      return {} as Record<string, QueueReceiptRecord>;
    }
    const receiptEntries = await Promise.all(
      billRows
        .filter((bill) => Boolean(bill.appointmentId) && (bill.netPaidAmount > 0 || bill.dueAmount <= 0 || bill.status === "PAID"))
        .map(async (bill) => {
          try {
            const payments = await listBillPayments(auth.accessToken!, auth.tenantId!, bill.id);
            const payment = [...payments]
              .sort((left, right) => `${right.paymentDateTime || right.paymentDate || right.createdAt}|${right.createdAt}`.localeCompare(`${left.paymentDateTime || left.paymentDate || left.createdAt}|${left.createdAt}`))
              .find((row) => Boolean(row.receiptId)) || payments[0] || null;
            if (!payment || !payment.receiptId || !bill.appointmentId) return null;
            return [bill.appointmentId, { billId: bill.id, billNumber: bill.billNumber, payment }] as const;
          } catch (err) {
            console.error("Queue receipt load failed", err);
            return null;
          }
        }),
    );
    return Object.fromEntries(receiptEntries.filter((entry): entry is readonly [string, QueueReceiptRecord] => Boolean(entry)));
  }, [auth.accessToken, auth.tenantId, canViewBillingData]);

  const loadQueueSnapshot = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !tenantReady) {
      return {
        rows: [] as Appointment[],
        billRows: [] as Bill[],
        receiptRecords: {} as Record<string, QueueReceiptRecord>,
      };
    }
    const rowsPromise = searchAppointments(auth.accessToken, auth.tenantId, {
      doctorUserId: effectiveDoctorId || undefined,
      appointmentDate: today,
    });
    const billRowsPromise = canViewBillingData
      ? searchBills(auth.accessToken, auth.tenantId, {
        fromDate: today,
        toDate: today,
      }).catch((err) => {
        console.error("Queue bill load failed", err);
        return [];
      })
      : Promise.resolve([]);
    const [rows, billRows] = await Promise.all([rowsPromise, billRowsPromise]);
    const receiptRecords = await loadReceiptRecords(billRows);
    return { rows, billRows, receiptRecords };
  }, [auth.accessToken, auth.tenantId, canViewBillingData, effectiveDoctorId, loadReceiptRecords, tenantReady, today]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinicProfile() {
      if (!auth.accessToken || !auth.tenantId) return;
      try {
        const profile = await getClinicProfile(auth.accessToken, auth.tenantId);
        if (!cancelled) setClinicProfile(profile);
      } catch {
        if (!cancelled) setClinicProfile(null);
      }
    }
    async function loadUsers() {
      if (!auth.accessToken || !auth.tenantId || !tenantReady) {
        setLoadingDoctors(false);
        return;
      }
      setLoadingDoctors(true);
      try {
        const rows = await getClinicUsers(auth.accessToken, auth.tenantId);
        if (cancelled) return;
        setUsers(rows);
        if (isDoctor && auth.appUserId) {
          setDoctorUserId(auth.appUserId);
        } else {
          setDoctorUserId((current) => current || doctorUserIdFromQuery || "");
        }
      } catch (err) {
        if (!cancelled) {
          setError("Unable to load doctors. Please verify clinic selection and try again.");
          console.error("Queue doctor load failed", err);
        }
      } finally {
        if (!cancelled) {
          setLoadingDoctors(false);
        }
      }
    }
    void loadClinicProfile();
    void loadUsers();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.appUserId, auth.tenantId, doctorUserIdFromQuery, isDoctor, tenantReady]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadQueue() {
      if (!auth.accessToken || !auth.tenantId || !tenantReady) {
        setAppointments([]);
        setBills([]);
        setReceiptRecordsByAppointmentId({});
        setLoadingQueue(false);
        return;
      }
      setLoadingQueue(true);
      setError(null);
      setBills([]);
      try {
        const { rows, billRows, receiptRecords } = await loadQueueSnapshot();
        if (cancelled) return;
        setAppointments(rows);
        setBills(billRows);
        setReceiptRecordsByAppointmentId(receiptRecords);
      } catch (err) {
        if (!cancelled) {
          setError("Unable to load queue. Please verify clinic selection and network connection.");
          console.error("Queue load failed", err);
        }
      } finally {
        if (!cancelled) {
          setLoadingQueue(false);
        }
      }
    }
    void loadQueue();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadQueueSnapshot, tenantReady]);

  const refreshQueue = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingQueue(true);
    setError(null);
    setBills([]);
    try {
      const { rows, billRows, receiptRecords } = await loadQueueSnapshot();
      setAppointments(rows);
      setBills(billRows);
      setReceiptRecordsByAppointmentId(receiptRecords);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to refresh queue");
    } finally {
      setLoadingQueue(false);
    }
  };

  React.useEffect(() => {
    const listener = (event: Event) => {
      const custom = event as CustomEvent<{ patientId?: string | null }>;
      const patientId = custom.detail?.patientId;
      if (patientId && appointments.some((appointment) => appointment.patientId === patientId)) {
        void refreshQueue();
      }
    };
    window.addEventListener("clinic:clinical-intake-updated", listener as EventListener);
    return () => {
      window.removeEventListener("clinic:clinical-intake-updated", listener as EventListener);
    };
  }, [appointments]);

  if (!tenantReady) {
    return <Alert severity="warning">Invalid selected clinic. Please reselect your clinic before opening the queue.</Alert>;
  }

  const updateStatus = async (appointmentId: string, status: Appointment["status"]) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSavingId(appointmentId);
    setError(null);
    try {
      const current = queueRowsWithFee.find((item) => item.id === appointmentId);
      if (status === "WAITING" && current && current.feeStatus !== "PAID") {
        setError(current.feeStatus === "NOT_CONFIGURED"
          ? "Doctor consultation fee is not configured."
          : "Consultation fee is pending. Collect fee before check-in.");
        if (canCollectFee && current.feeStatus !== "NOT_CONFIGURED") {
          setFeeDialog({ appointment: current, action: "collect-and-check-in" });
        }
        return;
      }
      const requiresComment = (status === "CANCELLED" || status === "NO_SHOW") && current?.status === "WAITING";
      if (status === "CANCELLED" && current?.feeStatus === "PAID") {
        setCancellationDialog({
          appointment: current,
          nextStatus: status,
          variant: "paid-cancel",
          reason: "",
        });
        return;
      }
      if (requiresComment && current) {
        setCancellationDialog({
          appointment: current,
          nextStatus: status,
          variant: "reason",
          reason: "",
        });
        return;
      }
      const comment = null;
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointmentId, status, comment);
      await refreshQueue();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Queue action failed. Please refresh and verify the appointment status.");
      console.error("Queue status update failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const confirmCancellationDialog = async () => {
    if (!auth.accessToken || !auth.tenantId || !cancellationDialog) {
      return;
    }
    setSavingId(cancellationDialog.appointment.id);
    setError(null);
    try {
      const comment = cancellationDialog.variant === "reason" ? cancellationDialog.reason.trim() || null : null;
      if (cancellationDialog.variant === "reason" && !comment) {
        setError("Reason/comment is required after check-in.");
        return;
      }
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, cancellationDialog.appointment.id, cancellationDialog.nextStatus, comment);
      setCancellationDialog(null);
      await refreshQueue();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Queue action failed. Please refresh and verify the appointment status.");
      console.error("Queue status update failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const changePriority = async (appointmentId: string, nextPriority: AppointmentPriority) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSavingId(appointmentId);
    setError(null);
    try {
      await updateAppointmentPriority(auth.accessToken, auth.tenantId, appointmentId, nextPriority);
      await refreshQueue();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update priority. Please refresh and try again.");
      console.error("Queue priority update failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const startConsultation = async (appointmentId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSavingId(appointmentId);
    setError(null);
    try {
      const consultation = await startConsultationFromAppointment(auth.accessToken, auth.tenantId, appointmentId);
      navigate(`/consultations/${consultation.id}`);
    } catch (err) {
      setError("Unable to start consultation. Confirm the patient is checked in and assigned to you.");
      console.error("Consultation start failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const reorderableIds = React.useMemo(
    () => queueRowsWithFee.filter((item) => item.status === "BOOKED" || item.status === "WAITING").map((item) => item.id),
    [queueRowsWithFee],
  );

  const reorderQueue = async (orderedIds: string[]) => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId || !canReorderQueue) return;
    setSavingId("__queue__");
    setError(null);
    try {
      await reorderDoctorQueueToday(auth.accessToken, auth.tenantId, effectiveDoctorId, orderedIds);
      await refreshQueue();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to reorder queue. Please refresh and try again.");
      console.error("Queue reorder failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const openFeeDialog = (appointment: QueueViewRow, action: FeeDialogState["action"]) => {
    setError(null);
    setFeeDialog({ appointment, action });
  };

  const submitFeeDialog = async (value: ConsultationFeeDialogValue) => {
    if (!feeDialog || !auth.accessToken || !auth.tenantId) {
      return;
    }
    const action = feeDialog.action;
    const current = feeDialog.appointment;
    const payment = await collectConsultationFee(auth.accessToken, auth.tenantId, {
      appointmentId: current.id,
      paymentMode: value.paymentMode,
      referenceNumber: value.referenceNumber || null,
      notes: value.notes || null,
    });
    const bill = current.consultationBill;
    if (bill) {
      const receipt: Receipt = {
        id: payment.receiptId || payment.id,
        tenantId: payment.tenantId,
        receiptNumber: payment.receiptNumber || `RCPT-${payment.id.slice(0, 8).toUpperCase()}`,
        billId: bill.id,
        paymentId: payment.id,
        receiptDate: payment.receiptDate || payment.paymentDate,
        amount: payment.amount,
        createdAt: payment.createdAt,
      };
      const receiptPrintData: ReceiptPrintData = {
        clinicProfile,
        bill,
        receipt,
        payment,
        patient: null,
        appointment: current,
        consultation: null,
      };
      setReceiptRecordsByAppointmentId((currentMap) => ({
        ...currentMap,
        [current.id]: {
          billId: bill.id,
          billNumber: bill.billNumber,
          payment,
        },
      }));
      setReceiptPanel({
        appointment: current,
        action,
        receipt,
        payment,
        bill,
        receiptPrintData,
      });
    }
    setFeeDialog(null);
    try {
      await refreshQueue();
    } catch (err) {
      console.error("Queue refresh after fee collection failed", err);
    }
  };

  const openBypassDialog = (appointment: QueueViewRow) => {
    setError(null);
    setBypassDialog({
      appointment,
      reason: "EMERGENCY",
      notes: "",
    });
  };

  const submitBypassDialog = async () => {
    if (!bypassDialog || !auth.accessToken || !auth.tenantId) {
      return;
    }
    const { appointment, reason, notes } = bypassDialog;
    if (reason === "OTHER" && !notes.trim()) {
      setError("Please add notes when selecting Other for payment bypass.");
      return;
    }
    setSavingId(appointment.id);
    setError(null);
    try {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointment.id, "WAITING", null, {
        paymentBypassReason: reason,
        paymentBypassNotes: notes.trim() || null,
      });
      setBypassDialog(null);
      await refreshQueue();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to check in with payment pending.");
      console.error("Queue payment bypass failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const openConsultationBilling = (appointmentId: string) => {
    const row = queueRowsWithFee.find((item) => item.id === appointmentId);
    if (!row) return;
    if (row.feeStatus === "NOT_CONFIGURED") {
      setError("Doctor consultation fee is not configured.");
      return;
    }
    openFeeDialog(row, "collect");
  };

  const openBillHistory = (appointmentId: string) => {
    navigate(`/billing?appointmentId=${appointmentId}`);
  };

  const getQueueReceiptRecord = React.useCallback((row: QueueViewRow) => receiptRecordsByAppointmentId[row.id] || null, [receiptRecordsByAppointmentId]);
  const buildReceiptPrintData = React.useCallback((row: QueueViewRow, record: QueueReceiptRecord): ReceiptPrintData | null => {
    if (!row.consultationBill) return null;
    const receipt: Receipt = {
      id: record.payment.receiptId || record.payment.id,
      tenantId: record.payment.tenantId,
      receiptNumber: record.payment.receiptNumber || `RCPT-${record.payment.id.slice(0, 8).toUpperCase()}`,
      billId: row.consultationBill.id,
      paymentId: record.payment.id,
      receiptDate: record.payment.receiptDate || record.payment.paymentDate,
      amount: record.payment.amount,
      createdAt: record.payment.createdAt,
    };
    return {
      clinicProfile,
      bill: row.consultationBill,
      receipt,
      payment: record.payment,
      patient: null,
      appointment: row,
      consultation: null,
    };
  }, [clinicProfile]);

  const openReceiptPreview = React.useCallback((row: QueueViewRow) => {
    const record = getQueueReceiptRecord(row);
    if (!record) return;
    const data = buildReceiptPrintData(row, record);
    if (!data) return;
    setReceiptPreview(data);
    setReceiptAutoPrint(false);
  }, [buildReceiptPrintData, getQueueReceiptRecord]);

  const openReceiptPrintPreview = React.useCallback((row: QueueViewRow, autoPrint = false) => {
    const record = getQueueReceiptRecord(row);
    if (!record) return;
    const data = buildReceiptPrintData(row, record);
    if (!data) return;
    setReceiptPreview(data);
    setReceiptAutoPrint(autoPrint);
  }, [buildReceiptPrintData, getQueueReceiptRecord]);

  const handleReceiptDownload = React.useCallback(async (row: QueueViewRow) => {
    const record = getQueueReceiptRecord(row);
    if (!auth.accessToken || !auth.tenantId || !record?.payment.receiptId) return;
    setReceiptActionLoading(true);
    setError(null);
    try {
      const file = await getReceiptPdf(auth.accessToken, auth.tenantId, record.payment.receiptId);
      const url = URL.createObjectURL(file.blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename || `${record.payment.receiptNumber || row.consultationBill?.billNumber || row.id}-receipt.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download receipt PDF");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, getQueueReceiptRecord]);

  const handleReceiptSend = React.useCallback(async (row: QueueViewRow, channel: "EMAIL" | "WHATSAPP") => {
    const record = getQueueReceiptRecord(row);
    if (!auth.accessToken || !auth.tenantId || !record?.payment.receiptId) return;
    setReceiptActionLoading(true);
    setError(null);
    try {
      await sendReceipt(auth.accessToken, auth.tenantId, record.payment.receiptId, channel);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send receipt");
    } finally {
      setReceiptActionLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, getQueueReceiptRecord]);

  const renderFeeStatus = (row: QueueViewRow) => {
    const record = getQueueReceiptRecord(row);
    if (record?.payment.receiptId) {
      const payment = record.payment;
      return (
        <Stack spacing={0.35}>
          <Chip size="small" label={`Paid • ${formatMoney(payment.amount)}`} color="success" variant="outlined" />
          <Typography variant="caption" color="text.secondary">
            Receipt: {payment.receiptNumber || "—"}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Paid {formatMoney(payment.amount)} • {payment.paymentMode}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Ref: {payment.referenceNumber || "—"} • By {staffDisplayName(payment.receivedByLabel, payment.receivedBy)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {formatDateTime(payment.paymentDateTime || payment.paymentDate || payment.receiptDate || payment.createdAt)}
          </Typography>
        </Stack>
      );
    }
    const paymentChip = (() => {
      switch (row.feeStatus) {
        case "PAID":
          return <Chip size="small" label={`Paid${row.consultationFeeAmount != null ? ` • ${formatMoney(row.consultationFeeAmount)}` : ""}`} color="success" variant="outlined" />;
        case "PARTIAL":
          return <Chip size="small" label={`Partial${row.feeDueAmount != null ? ` • Due ${formatMoney(row.feeDueAmount)}` : ""}`} color="warning" variant="outlined" />;
        case "UNPAID":
          return <Chip size="small" label={`Unpaid${row.consultationFeeAmount != null ? ` • ${formatMoney(row.consultationFeeAmount)}` : ""}`} color="warning" />;
        case "NOT_CONFIGURED":
        default:
          return <Chip size="small" label="Doctor fee missing" color="default" variant="outlined" />;
      }
    })();
    if (!row.paymentBypassedAt) {
      return (
        <Stack spacing={0.35}>
          {paymentChip}
          {row.feeStatus === "PAID" ? (
            <Typography variant="caption" color="text.secondary">Paid - receipt details unavailable</Typography>
          ) : null}
        </Stack>
      );
    }
    const bypassDueLabel = row.feeDueAmount != null && row.feeDueAmount > 0
      ? `Pay later • Due ${formatMoney(row.feeDueAmount)}`
      : "Payment bypassed";
    return (
      <Stack spacing={0.35} alignItems="flex-start">
        {paymentChip}
        <Chip size="small" label={bypassDueLabel} color="secondary" variant="outlined" />
      </Stack>
    );
  };

  const queueTitle = effectiveDoctorId ? displayDoctorName(users, effectiveDoctorId) : "All Doctors";

  React.useEffect(() => {
    if (!receiptPreview || !receiptAutoPrint || receiptPreviewLoading) return;
    const handle = window.setTimeout(() => window.print(), 60);
    setReceiptAutoPrint(false);
    return () => window.clearTimeout(handle);
  }, [receiptAutoPrint, receiptPreview, receiptPreviewLoading]);

  return (
    <Stack spacing={2.25}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 0.5 }}>Queue</Typography>
          <Typography variant="body2" color="text.secondary">
            All-doctor booked-patient overview with consultation fee collection before check-in.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/appointments")}>Appointments</Button>
          <Button variant="outlined" onClick={() => void refreshQueue()}>Refresh</Button>
        </Stack>
      </Box>

      <WorkflowStrip steps={QUEUE_WORKFLOW_STEPS} />

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card variant="outlined">
        <CardContent sx={{ p: 1.5 }}>
          <Stack spacing={1.25}>
            <Grid container spacing={1.25}>
              <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Booked" value={summary.totalBooked} helper="All queue entries today" /></Grid>
              <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Pending" value={summary.paymentPending} tone="warning" helper="Collect before check-in" /></Grid>
              <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Ready" value={summary.readyForCheckIn} tone="success" helper="Eligible for desk check-in" /></Grid>
              <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Active" value={summary.checkedIn + summary.inConsultation} tone="info" helper="Checked-in and in consultation" /></Grid>
            </Grid>

            <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ md: "center" }} flexWrap="wrap">
              <FormControl sx={{ minWidth: 240, maxWidth: 360 }} size="small">
                <InputLabel id="queue-doctor-label">Doctor</InputLabel>
                <Select
                  labelId="queue-doctor-label"
                  label="Doctor"
                  value={isDoctor && auth.appUserId ? auth.appUserId : doctorUserId}
                  onChange={(e) => setDoctorUserId(String(e.target.value))}
                  disabled={isDoctor}
                >
                  <MenuItem value="">All Doctors</MenuItem>
                  {doctors.map((doctor) => (
                    <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                      {doctor.displayName || doctor.email || doctor.appUserId}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                size="small"
                label="Search queue"
                placeholder="Token, patient, doctor, status, reason"
                value={queueSearch}
                onChange={(event) => setQueueSearch(event.target.value)}
                sx={{ minWidth: 240, flex: 1 }}
              />
              <Typography variant="body2" color="text.secondary">
                Viewing: <strong>{queueTitle}</strong>
              </Typography>
            </Stack>

            <Stack direction="row" spacing={0.5} flexWrap="wrap">
              <Chip size="small" label={`Checked-in ${summary.checkedIn}`} color="warning" sx={compactChipSx} />
              <Chip size="small" label={`In consultation ${summary.inConsultation}`} color="info" sx={compactChipSx} />
              <Chip size="small" label={`Completed ${summary.completed}`} color="success" variant="outlined" sx={compactChipSx} />
              <Chip size="small" label={`No-show / Cancelled ${summary.cancelled}`} color="default" variant="outlined" sx={compactChipSx} />
            </Stack>

            {isDoctor ? (
              <Alert severity="info">Showing only queue items assigned to you.</Alert>
            ) : null}

            {loadingDoctors || loadingQueue ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                <Stack spacing={1} alignItems="center">
                  <CircularProgress />
                  <Typography variant="body2" color="text.secondary">
                    Loading queue items...
                  </Typography>
                </Stack>
              </Box>
            ) : visibleRows.length === 0 ? (
              <CompactEmptyState title="No queue items found" subtitle="Try a different doctor or clear the search box." />
            ) : (
              <CompactTableFrame maxHeight={620}>
                <Table size="small" stickyHeader sx={{ minWidth: 1180 }}>
                  <TableHead>
                    <TableRow>
                      {!effectiveDoctorId ? <TableCell>Doctor</TableCell> : null}
                      <TableCell>Token</TableCell>
                      <TableCell>Patient</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Priority</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Fee</TableCell>
                      <TableCell>Payment</TableCell>
                      <TableCell>Check-in</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visibleRows.map((appointment) => {
                      const isActive = appointment.status === "BOOKED" || appointment.status === "WAITING";
                      const checkInBlocked = appointment.consultationFeeAmount != null && appointment.consultationFeeAmount > 0 && appointment.feeStatus !== "PAID";
                      const nextAction = getNextWorkflowAction({
                        status: appointment.status,
                        paymentStatus: appointment.feeStatus,
                        billDueAmount: appointment.consultationFeeDueAmount,
                      });
                      return (
                        <TableRow
                          key={appointment.id}
                          draggable={Boolean(effectiveDoctorId && canReorderQueue && isActive)}
                          onDragStart={() => setDraggingId(appointment.id)}
                          onDragOver={(event) => {
                            if (draggingId) {
                              event.preventDefault();
                            }
                          }}
                          onDrop={(event) => {
                            event.preventDefault();
                            if (!draggingId || draggingId === appointment.id || !effectiveDoctorId) return;
                            if (!isActive) return;
                            const next = reorderableIds.filter((id) => id !== draggingId);
                            const targetIndex = next.indexOf(appointment.id);
                            if (targetIndex < 0) return;
                            next.splice(targetIndex, 0, draggingId);
                            void reorderQueue(next);
                            setDraggingId(null);
                          }}
                          onDragEnd={() => setDraggingId(null)}
                          sx={{ opacity: draggingId === appointment.id ? 0.72 : 1 }}
                        >
                          {!effectiveDoctorId ? (
                            <TableCell>{appointment.doctorName || displayDoctorName(users, appointment.doctorUserId)}</TableCell>
                          ) : null}
                          <TableCell><AppointmentTokenChip appointment={appointment} compact /></TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                                {appointment.patientName || appointment.patientNumber || appointment.patientId}
                              </Button>
                              <Typography variant="caption" color="text.secondary">{appointment.patientNumber ? `Patient No: ${appointment.patientNumber}` : "Patient No: Not assigned"}</Typography>
                              <Typography variant="caption" color="text.secondary">{appointment.patientMobile || "—"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.15}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{toFive(appointment.appointmentTime)}</Typography>
                              <Typography variant="caption" color="text.secondary">{formatRelativeBookingTime(appointment.createdAt) || "Booked recently"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            {canManageDeskStatus ? (
                              <FormControl size="small" fullWidth sx={{ minWidth: 150 }}>
                                <Select
                                  value={appointment.priority || "NORMAL"}
                                  onChange={(event) => void changePriority(appointment.id, event.target.value as AppointmentPriority)}
                                  disabled={savingId === appointment.id}
                                >
                                  <MenuItem value="NORMAL">NORMAL</MenuItem>
                                  <MenuItem value="FOLLOW_UP">FOLLOW_UP</MenuItem>
                                  <MenuItem value="CHILD">CHILD</MenuItem>
                                  <MenuItem value="ELDERLY">ELDERLY</MenuItem>
                                  <MenuItem value="URGENT">URGENT</MenuItem>
                                  <MenuItem value="MANUAL_PRIORITY">MANUAL_PRIORITY</MenuItem>
                                </Select>
                              </FormControl>
                            ) : (
                              <Chip size="small" label={appointment.priority || "NORMAL"} color={priorityColor(appointment.priority)} variant="outlined" />
                            )}
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.4}>
                              <WorkflowStatusBadge status={appointment.status} compact />
                              <Chip
                                size="small"
                                variant="outlined"
                                label={`Next: ${getNextWorkflowAction({ status: appointment.status, paymentStatus: appointment.feeStatus, billDueAmount: appointment.consultationFeeDueAmount }).label}`}
                                sx={compactChipSx}
                              />
                              {appointment.clinicalIntakeStatus ? (
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  color={appointment.clinicalIntakeStatus === "INTAKE_COMPLETE" ? "success" : "default"}
                                  label={appointment.clinicalIntakeStatus === "INTAKE_COMPLETE" ? "Intake complete" : "Pending intake"}
                                  sx={compactChipSx}
                                />
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>{formatMoney(appointment.consultationFeeAmount)}</TableCell>
                          <TableCell sx={{ minWidth: 220 }}>{renderFeeStatus(appointment)}</TableCell>
                          <TableCell sx={{ minWidth: 180 }}>
                            <Stack spacing={0.35}>
                              <Button
                                size="small"
                                variant={nextAction.key === "check-in" ? "contained" : "outlined"}
                                disabled={!canManageDeskStatus || appointment.status !== "BOOKED" || savingId === appointment.id || (appointment.feeStatus !== "PAID" && !getQueueReceiptRecord(appointment))}
                                onClick={() => void updateStatus(appointment.id, "WAITING")}
                              >
                                Check-in
                              </Button>
                              {appointment.feeStatus === "NOT_CONFIGURED" ? (
                                <Typography variant="caption" color="text.secondary">Doctor consultation fee is not configured.</Typography>
                              ) : checkInBlocked ? (
                                <Typography variant="caption" color="text.secondary">
                                  {appointment.paymentBypassedAt ? "Checked in with payment pending override." : "Consultation fee pending. Collect fee before check-in."}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell align="right" sx={{ minWidth: 300 }}>
                            <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap" useFlexGap sx={{ "& .MuiButton-root": { whiteSpace: "nowrap" } }}>
                              {!isDoctor && canShowCollectFee(appointment) ? (
                                <Button size="small" variant={nextAction.key === "collect-fee" ? "contained" : "outlined"} disabled={savingId === appointment.id || appointment.feeStatus === "NOT_CONFIGURED"} onClick={() => openConsultationBilling(appointment.id)}>
                                  Collect Fee
                                </Button>
                              ) : null}
                              <Button size="small" variant="text" onClick={() => navigate(`/patients/${appointment.patientId}`)}>
                                Open Patient
                              </Button>
                              {!isDoctor && canShowViewBilling(appointment, canViewBillingData) && !isReceptionist ? (
                                <Button size="small" variant="outlined" onClick={() => openBillHistory(appointment.id)}>
                                  {appointment.consultationBill ? "View Billing" : "Open Billing"}
                                </Button>
                              ) : null}
                              {canManageDeskStatus && canBypassPaymentCheckIn && canShowPayAfterConsultation(appointment) ? (
                                <Button size="small" variant="outlined" color="secondary" disabled={savingId === appointment.id} onClick={() => openBypassDialog(appointment)}>
                                  Pay after consultation
                                </Button>
                              ) : null}
                              {canStartConsultation && appointment.status === "WAITING" ? (
                                <Button size="small" variant={nextAction.key === "start-consultation" ? "contained" : "outlined"} disabled={savingId === appointment.id} onClick={() => void startConsultation(appointment.id)}>
                                  Start Consultation
                                </Button>
                              ) : null}
                              {canStartConsultation && appointment.status === "IN_CONSULTATION" ? (
                                <Button size="small" variant={nextAction.key === "continue-consultation" ? "contained" : "outlined"} disabled={savingId === appointment.id} onClick={() => void startConsultation(appointment.id)}>
                                  Continue Consultation
                                </Button>
                              ) : null}
                              {canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING") ? (
                                <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "CANCELLED")}>
                                  Cancel
                                </Button>
                              ) : null}
                              {canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING") ? (
                                <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "NO_SHOW")}>
                                  No Show
                                </Button>
                              ) : null}
                              {(appointment.status === "BOOKED" || appointment.status === "WAITING") ? (
                                <Button size="small" variant="outlined" onClick={() => setClinicalIntakeAppointment(appointment)}>
                                  Clinical Intake
                                </Button>
                              ) : null}
                              <IconButton
                                size="small"
                                aria-label={`Open actions for ${appointment.patientName || appointment.patientNumber || appointment.id}`}
                                onClick={(event) => {
                                  setRowMenuAnchor(event.currentTarget);
                                  setRowMenuAppointmentId(appointment.id);
                                }}
                              >
                                <MoreVertRoundedIcon fontSize="small" />
                              </IconButton>
                              {!(
                                (canStartConsultation && (appointment.status === "WAITING" || appointment.status === "IN_CONSULTATION")) ||
                                (canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING"))
                              ) ? (
                                <Typography variant="caption" color="text.secondary">No actions</Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </CompactTableFrame>
            )}
          </Stack>
        </CardContent>
      </Card>

      {feeDialog ? (
        <ConsultationFeeDialog
          open
          title={feeDialog.action === "collect-and-check-in" ? "Collect consultation fee and check in" : "Collect consultation fee"}
          appointmentLabel={`${feeDialog.appointment.appointmentDate} ${toFive(feeDialog.appointment.appointmentTime)}`}
          doctorLabel={`Doctor: ${feeDialog.appointment.doctorName || displayDoctorName(users, feeDialog.appointment.doctorUserId)}`}
          patientLabel={`Patient: ${feeDialog.appointment.patientName || feeDialog.appointment.patientNumber || feeDialog.appointment.patientId}`}
          feeLabel={`Consultation fee: ${formatMoney(feeDialog.appointment.consultationFeeAmount)}`}
          submitLabel={feeDialog.action === "collect-and-check-in" ? "Collect & Check-in" : "Collect Fee"}
          onClose={() => setFeeDialog(null)}
          onSubmit={submitFeeDialog}
        />
      ) : null}

      {cancellationDialog ? (
        <Dialog open onClose={() => setCancellationDialog(null)} fullWidth maxWidth="sm">
          <DialogTitle>Appointment Cancellation</DialogTitle>
          <DialogContent>
            <Stack spacing={1.5} sx={{ pt: 0.5 }}>
              {cancellationDialog.variant === "paid-cancel" ? (
                <>
                  <Typography variant="body2" color="text.secondary">
                    Payment has already been collected for this appointment.
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    If you continue, the appointment will be cancelled. The consultation fee remains recorded. If money should be returned, process the refund from Billing.
                  </Typography>
                  <Typography variant="body2">
                    Appointment: <strong>{cancellationDialog.appointment.patientName || cancellationDialog.appointment.patientNumber || cancellationDialog.appointment.patientId}</strong>
                  </Typography>
                </>
              ) : (
                <>
                  <Typography variant="body2" color="text.secondary">
                    Provide a reason before cancelling or marking the appointment as no-show after check-in.
                  </Typography>
                  <TextField
                    label="Reason"
                    size="small"
                    multiline
                    minRows={2}
                    value={cancellationDialog.reason}
                    onChange={(event) => setCancellationDialog((current) => current ? { ...current, reason: event.target.value } : current)}
                    required
                  />
                </>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setCancellationDialog(null)}>Cancel</Button>
            {cancellationDialog.variant === "paid-cancel" ? (
              <>
                <Button variant="outlined" onClick={() => { setCancellationDialog(null); navigate("/billing"); }}>Go To Billing</Button>
                <Button variant="contained" color="error" disabled={savingId === cancellationDialog.appointment.id} onClick={() => void confirmCancellationDialog()}>
                  Cancel Appointment
                </Button>
              </>
            ) : (
              <Button variant="contained" disabled={savingId === cancellationDialog.appointment.id} onClick={() => void confirmCancellationDialog()}>
                Confirm
              </Button>
            )}
          </DialogActions>
        </Dialog>
      ) : null}

      {bypassDialog ? (
        <Dialog open onClose={() => setBypassDialog(null)} fullWidth maxWidth="sm">
          <DialogTitle>Check-in with payment pending</DialogTitle>
          <DialogContent>
            <Stack spacing={1.5} sx={{ pt: 0.5 }}>
              <Typography variant="body2" color="text.secondary">
                Consultation fee will remain due and auditable. Select a reason before allowing check-in.
              </Typography>
              <Typography variant="body2">
                Patient: <strong>{bypassDialog.appointment.patientName || bypassDialog.appointment.patientNumber || bypassDialog.appointment.patientId}</strong>
              </Typography>
              <Typography variant="body2">
                Due: <strong>{formatMoney(bypassDialog.appointment.feeDueAmount ?? bypassDialog.appointment.consultationFeeAmount)}</strong>
              </Typography>
              <FormControl fullWidth size="small">
                <InputLabel id="payment-bypass-reason-label">Reason</InputLabel>
                <Select
                  labelId="payment-bypass-reason-label"
                  label="Reason"
                  value={bypassDialog.reason}
                  onChange={(event) => setBypassDialog((current) => current ? {
                    ...current,
                    reason: event.target.value as CheckInBypassReason,
                  } : current)}
                >
                  {CHECK_IN_BYPASS_REASON_OPTIONS.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Notes"
                size="small"
                multiline
                minRows={2}
                value={bypassDialog.notes}
                onChange={(event) => setBypassDialog((current) => current ? {
                  ...current,
                  notes: event.target.value,
                } : current)}
                required={bypassDialog.reason === "OTHER"}
                helperText={bypassDialog.reason === "OTHER" ? "Notes are required when selecting Other." : "Optional operational notes for audit."}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setBypassDialog(null)}>Cancel</Button>
            <Button variant="contained" color="secondary" disabled={savingId === bypassDialog.appointment.id} onClick={() => void submitBypassDialog()}>
              Confirm check-in
            </Button>
          </DialogActions>
        </Dialog>
      ) : null}

      {clinicalIntakeAppointment ? (
        <ClinicalIntakeDialog
          open
          onClose={() => setClinicalIntakeAppointment(null)}
          patientId={clinicalIntakeAppointment.patientId}
          patientLabel={clinicalIntakeAppointment.patientName || clinicalIntakeAppointment.patientNumber || clinicalIntakeAppointment.patientId}
          appointmentId={clinicalIntakeAppointment.id}
          consultationId={clinicalIntakeAppointment.consultationId}
          onSaved={() => {
            setClinicalIntakeAppointment(null);
            void refreshQueue();
          }}
        />
      ) : null}

      <Menu
        open={Boolean(rowMenuAnchor && rowMenuAppointmentId)}
        anchorEl={rowMenuAnchor}
        onClose={() => {
          setRowMenuAnchor(null);
          setRowMenuAppointmentId(null);
        }}
      >
        {rowMenuAppointmentId ? (() => {
          const row = queueRowsWithFee.find((item) => item.id === rowMenuAppointmentId) || null;
          if (!row) return null;
          const record = getQueueReceiptRecord(row);
          return [
            <MenuItem key="view-patient" onClick={() => { navigate(`/patients/${row.patientId}`); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>View Patient</MenuItem>,
            ...(!isReceptionist && canViewBillingData ? [<MenuItem key="open-billing" onClick={() => { openBillHistory(row.id); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>Open Billing</MenuItem>] : []),
            ...(record?.payment.receiptId ? [
              <MenuItem key="view-receipt" onClick={() => { openReceiptPreview(row); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>View Receipt</MenuItem>,
              <MenuItem key="print-receipt" onClick={() => { openReceiptPrintPreview(row, true); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>Print Receipt</MenuItem>,
              <MenuItem key="download-receipt" disabled={receiptActionLoading} onClick={() => { void handleReceiptDownload(row); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>Download Receipt PDF</MenuItem>,
              ...(canSendReceipt ? [
                <MenuItem key="email-receipt" disabled={receiptActionLoading} onClick={() => { void handleReceiptSend(row, "EMAIL"); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>Email Receipt</MenuItem>,
                <MenuItem key="whatsapp-receipt" disabled={receiptActionLoading} onClick={() => { void handleReceiptSend(row, "WHATSAPP"); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>WhatsApp Receipt</MenuItem>,
              ] : []),
            ] : []),
            canManageDeskStatus && (row.status === "BOOKED" || row.status === "WAITING") ? <MenuItem key="cancel" onClick={() => { void updateStatus(row.id, "CANCELLED"); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>Cancel</MenuItem> : null,
            canManageDeskStatus && (row.status === "BOOKED" || row.status === "WAITING") ? <MenuItem key="no-show" onClick={() => { void updateStatus(row.id, "NO_SHOW"); setRowMenuAnchor(null); setRowMenuAppointmentId(null); }}>No Show</MenuItem> : null,
          ].filter(Boolean);
        })() : null}
      </Menu>

      <Dialog open={Boolean(receiptPanel)} onClose={() => setReceiptPanel(null)} fullWidth maxWidth="sm">
        <DialogTitle>Payment successful</DialogTitle>
        <DialogContent>
          {receiptPanel ? (
            <Stack spacing={1.25} sx={{ pt: 0.5 }}>
              <Alert severity="success">Receipt ready for {receiptPanel.appointment.patientName || receiptPanel.appointment.patientNumber || receiptPanel.appointment.patientId}.</Alert>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Receipt number</Typography><Typography variant="body2">{receiptPanel.payment.receiptNumber || "-"}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Bill number</Typography><Typography variant="body2">{receiptPanel.bill.billNumber}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Amount</Typography><Typography variant="body2">₹{formatMoney(receiptPanel.payment.amount)}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Payment mode</Typography><Typography variant="body2">{receiptPanel.payment.paymentMode}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Reference number</Typography><Typography variant="body2">{receiptPanel.payment.referenceNumber || "—"}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Collected by</Typography><Typography variant="body2">{staffDisplayName(receiptPanel.payment.receivedByLabel, receiptPanel.payment.receivedBy)}</Typography></Grid>
                <Grid size={{ xs: 12, sm: 6 }}><Typography variant="body2" sx={{ fontWeight: 800 }}>Timestamp</Typography><Typography variant="body2">{formatDateTime(receiptPanel.payment.paymentDateTime || receiptPanel.payment.paymentDate || receiptPanel.payment.receiptDate || receiptPanel.payment.createdAt)}</Typography></Grid>
              </Grid>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ flexWrap: "wrap" }}>
          <Button onClick={() => setReceiptPanel(null)}>Close</Button>
          <Button variant="outlined" onClick={() => { if (receiptPanel) openReceiptPreview(receiptPanel.appointment); setReceiptPanel(null); }}>View Receipt</Button>
          <Button variant="outlined" onClick={() => { if (receiptPanel) openReceiptPrintPreview(receiptPanel.appointment, true); setReceiptPanel(null); }}>Print Receipt</Button>
          <Button variant="outlined" onClick={() => { if (receiptPanel) void handleReceiptDownload(receiptPanel.appointment); }}>Download Receipt PDF</Button>
          {canSendReceipt ? (
            <>
              <Button variant="outlined" onClick={() => { if (receiptPanel) void handleReceiptSend(receiptPanel.appointment, "EMAIL"); }}>Email Receipt</Button>
              <Button variant="outlined" onClick={() => { if (receiptPanel) void handleReceiptSend(receiptPanel.appointment, "WHATSAPP"); }}>WhatsApp Receipt</Button>
            </>
          ) : null}
          <Button
            variant="contained"
            disabled={!receiptPanel || receiptPanel.appointment.status !== "BOOKED" || savingId === receiptPanel.appointment.id}
            onClick={async () => {
              if (!receiptPanel || !auth.accessToken || !auth.tenantId) return;
              setSavingId(receiptPanel.appointment.id);
              try {
                await updateAppointmentStatus(auth.accessToken, auth.tenantId, receiptPanel.appointment.id, "WAITING", null);
                setReceiptPanel(null);
                await refreshQueue();
              } catch (err) {
                setError(err instanceof Error ? err.message : "Unable to check in the patient.");
              } finally {
                setSavingId(null);
              }
            }}
          >
            Continue to Check-in
          </Button>
        </DialogActions>
      </Dialog>

      <ReceiptPrintDialog
        open={Boolean(receiptPreview || receiptPreviewLoading)}
        loading={receiptPreviewLoading}
        data={receiptPreview}
        onClose={() => {
          setReceiptPreview(null);
          setReceiptPreviewLoading(false);
          setReceiptAutoPrint(false);
        }}
        onPrint={() => window.print()}
      />
    </Stack>
  );
}
