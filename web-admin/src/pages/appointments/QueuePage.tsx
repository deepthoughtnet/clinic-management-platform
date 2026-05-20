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

import ConsultationFeeDialog, { type ConsultationFeeDialogValue } from "../../components/ConsultationFeeDialog";
import { CompactEmptyState, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";
import { useAuth } from "../../auth/useAuth";
import {
  collectConsultationFee,
  getClinicUsers,
  getDoctorProfile,
  reorderDoctorQueueToday,
  searchAppointments,
  searchBills,
  startConsultationFromAppointment,
  updateAppointmentPriority,
  updateAppointmentStatus,
  type Appointment,
  type AppointmentPriority,
  type Bill,
  type ClinicUser,
  type DoctorProfile,
  type PaymentMode,
} from "../../api/clinicApi";

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

function friendlyStatusLabel(value: string | null | undefined) {
  if (!value) return "-";
  switch (value.toUpperCase()) {
    case "BOOKED":
      return "Booked";
    case "WAITING":
      return "Waiting";
    case "IN_CONSULTATION":
      return "In consultation";
    case "COMPLETED":
      return "Completed";
    case "CANCELLED":
      return "Cancelled";
    case "NO_SHOW":
      return "No-show";
    default:
      return value.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\S/g, (match) => match.toUpperCase());
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

export default function QueuePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const doctorUserIdFromQuery = searchParams.get("doctorUserId") || "";
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [doctorProfiles, setDoctorProfiles] = React.useState<Record<string, DoctorProfile>>({});
  const [doctorUserId, setDoctorUserId] = React.useState(doctorUserIdFromQuery);
  const [queueSearch, setQueueSearch] = React.useState("");
  const [loadingDoctors, setLoadingDoctors] = React.useState(true);
  const [loadingQueue, setLoadingQueue] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [savingId, setSavingId] = React.useState<string | null>(null);
  const [draggingId, setDraggingId] = React.useState<string | null>(null);
  const [feeDialog, setFeeDialog] = React.useState<FeeDialogState | null>(null);

  const today = React.useMemo(() => localDateKey(), []);
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const isClinicAdmin = tenantRole === "CLINIC_ADMIN";
  const isReceptionist = tenantRole === "RECEPTIONIST";
  const canCollectFee = auth.hasPermission("billing.create") || auth.hasPermission("payment.collect");
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

  const queueBillingByAppointmentId = React.useMemo(() => {
    const map = new Map<string, Bill>();
    for (const bill of bills) {
      if (!bill.appointmentId || !billHasConsultationLine(bill)) {
        continue;
      }
      if (!map.has(bill.appointmentId)) {
        map.set(bill.appointmentId, bill);
      }
    }
    return map;
  }, [bills]);

  const queueRowsWithFee = React.useMemo<QueueViewRow[]>(() => {
    return queueRows.map((appointment) => {
      const doctorProfile = doctorProfiles[appointment.doctorUserId];
      const consultationBill = appointment.id ? queueBillingByAppointmentId.get(appointment.id) || null : null;
      const consultationFeeAmount = doctorProfile?.consultationFee ?? consultationBill?.totalAmount ?? null;
      let feeStatus: FeeStatus = "NOT_CONFIGURED";
      let feeDueAmount: number | null = null;
      let feePaidAmount: number | null = null;
      if (consultationBill) {
        feeDueAmount = consultationBill.dueAmount;
        feePaidAmount = consultationBill.paidAmount;
        feeStatus = consultationBill.dueAmount > 0
          ? consultationBill.paidAmount > 0 ? "PARTIAL" : "UNPAID"
          : "PAID";
      } else if (consultationFeeAmount != null && consultationFeeAmount > 0) {
        feeStatus = "UNPAID";
        feeDueAmount = consultationFeeAmount;
        feePaidAmount = 0;
      }
      return {
        ...appointment,
        consultationFeeAmount,
        consultationBill,
        feeStatus,
        feeDueAmount,
        feePaidAmount,
      };
    });
  }, [doctorProfiles, queueBillingByAppointmentId, queueRows]);

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
        friendlyStatusLabel(appointment.status),
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

  React.useEffect(() => {
    let cancelled = false;
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
        const firstDoctor = rows.find((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
        if (isDoctor && auth.appUserId) {
          setDoctorUserId(auth.appUserId);
        } else {
          setDoctorUserId((current) => current || firstDoctor?.appUserId || "");
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
    void loadUsers();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.appUserId, auth.tenantId, isDoctor, tenantReady]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadQueue() {
      if (!auth.accessToken || !auth.tenantId || !tenantReady) {
        setAppointments([]);
        setLoadingQueue(false);
        return;
      }
      setLoadingQueue(true);
      setError(null);
      try {
        const [rows, billRows] = await Promise.all([
          searchAppointments(auth.accessToken, auth.tenantId, {
            doctorUserId: effectiveDoctorId || undefined,
            appointmentDate: today,
          }),
          searchBills(auth.accessToken, auth.tenantId, {
            fromDate: today,
            toDate: today,
          }),
        ]);
        if (cancelled) return;
        setAppointments(rows);
        setBills(billRows);
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
  }, [auth.accessToken, auth.tenantId, effectiveDoctorId, tenantReady, today]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadProfiles() {
      const token = auth.accessToken;
      const tenantId = auth.tenantId;
      if (!token || !tenantId || !tenantReady || doctors.length === 0) {
        setDoctorProfiles({});
        return;
      }
      try {
        const rows = await Promise.all(
          doctors.map(async (doctor) => {
            const doctorId = doctor.appUserId;
            if (!doctorId) {
              return null;
            }
            const profile = await getDoctorProfile(token, tenantId, doctorId as string).catch(() => null);
            return profile ? [doctorId as string, profile] as const : null;
          }),
        );
        if (cancelled) return;
        const map: Record<string, DoctorProfile> = {};
        for (const entry of rows) {
          if (entry) {
            map[entry[0]] = entry[1];
          }
        }
        setDoctorProfiles(map);
      } catch {
        if (!cancelled) {
          setDoctorProfiles({});
        }
      }
    }
    void loadProfiles();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, doctors, tenantReady]);

  const refreshQueue = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingQueue(true);
    setError(null);
    try {
      const [rows, billRows] = await Promise.all([
        searchAppointments(auth.accessToken, auth.tenantId, {
          doctorUserId: effectiveDoctorId || undefined,
          appointmentDate: today,
        }),
        searchBills(auth.accessToken, auth.tenantId, {
          fromDate: today,
          toDate: today,
        }),
      ]);
      setAppointments(rows);
      setBills(billRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to refresh queue");
    } finally {
      setLoadingQueue(false);
    }
  };

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
      let comment: string | null = null;
      const requiresComment = (status === "CANCELLED" || status === "NO_SHOW") && current?.status === "WAITING";
      if (requiresComment) {
        comment = window.prompt("Cancellation/No-show reason is required after check-in.")?.trim() || null;
        if (!comment) {
          setError("Reason/comment is required after check-in.");
          return;
        }
      }
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointmentId, status, comment);
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
    await collectConsultationFee(auth.accessToken, auth.tenantId, {
      appointmentId: current.id,
      paymentMode: value.paymentMode,
      referenceNumber: value.referenceNumber || null,
      notes: value.notes || null,
    });
    setFeeDialog(null);
    await refreshQueue();
    if (action === "collect-and-check-in") {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, current.id, "WAITING", null);
      await refreshQueue();
    }
  };

  const openConsultationBilling = (appointmentId: string) => {
    const row = queueRowsWithFee.find((item) => item.id === appointmentId);
    if (!row) {
      navigate(`/billing?appointmentId=${appointmentId}`);
      return;
    }
    const params = new URLSearchParams();
    params.set("appointmentId", row.id);
    params.set("patientId", row.patientId);
    if (row.doctorUserId) {
      params.set("doctorUserId", row.doctorUserId);
    }
    if (row.consultationFeeAmount != null && row.consultationFeeAmount > 0) {
      params.set("consultationFee", row.consultationFeeAmount.toFixed(2));
    }
    params.set("collectConsultationFee", "1");
    params.set("returnTo", "/queue");
    navigate(`/billing?${params.toString()}`);
  };

  const openBillHistory = (appointmentId: string) => {
    navigate(`/billing?appointmentId=${appointmentId}`);
  };

  const renderFeeStatus = (row: QueueViewRow) => {
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
  };

  const queueTitle = effectiveDoctorId ? displayDoctorName(users, effectiveDoctorId) : "All Doctors";

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
              <Box sx={{ overflowX: "auto" }}>
                <Table size="small" sx={{ minWidth: 1180 }}>
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
                          <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                                {appointment.patientName || appointment.patientNumber || appointment.patientId}
                              </Button>
                              <Typography variant="caption" color="text.secondary">{appointment.patientMobile || "—"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{toFive(appointment.appointmentTime)}</TableCell>
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
                          <TableCell><Chip size="small" label={friendlyStatusLabel(appointment.status)} color={statusColor(appointment.status)} sx={compactChipSx} /></TableCell>
                          <TableCell>{formatMoney(appointment.consultationFeeAmount)}</TableCell>
                          <TableCell>{renderFeeStatus(appointment)}</TableCell>
                          <TableCell>
                            <Stack spacing={0.35}>
                              <Button
                                size="small"
                                variant="contained"
                                disabled={!canManageDeskStatus || appointment.status !== "BOOKED" || savingId === appointment.id || appointment.feeStatus !== "PAID"}
                                onClick={() => void updateStatus(appointment.id, "WAITING")}
                              >
                                Check-in
                              </Button>
                              {appointment.feeStatus === "NOT_CONFIGURED" ? (
                                <Typography variant="caption" color="text.secondary">Doctor consultation fee is not configured.</Typography>
                              ) : checkInBlocked ? (
                                <Typography variant="caption" color="text.secondary">Consultation fee pending. Collect fee before check-in.</Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap">
                              {appointment.feeStatus !== "PAID" ? (
                                <Button size="small" variant="outlined" disabled={savingId === appointment.id} onClick={() => openConsultationBilling(appointment.id)}>
                                  {appointment.feeStatus === "NOT_CONFIGURED" ? "Open Billing" : "Collect Fee"}
                                </Button>
                              ) : null}
                              <Button size="small" variant="text" onClick={() => navigate(`/patients/${appointment.patientId}`)}>
                                Open Patient
                              </Button>
                              {appointment.consultationBill ? (
                                <Button size="small" variant="outlined" onClick={() => openBillHistory(appointment.id)}>
                                  View Billing
                                </Button>
                              ) : null}
                              {canStartConsultation && appointment.status === "WAITING" ? (
                                <Button size="small" disabled={savingId === appointment.id} onClick={() => void startConsultation(appointment.id)}>
                                  Start Consultation
                                </Button>
                              ) : null}
                              {canStartConsultation && appointment.status === "IN_CONSULTATION" ? (
                                <Button size="small" disabled={savingId === appointment.id} onClick={() => void startConsultation(appointment.id)}>
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
              </Box>
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
    </Stack>
  );
}
