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

import { useAuth } from "../../auth/useAuth";
import { getClinicUsers, getDoctorQueueToday, reorderDoctorQueueToday, startConsultationFromAppointment, updateAppointmentPriority, updateAppointmentStatus, type Appointment, type ClinicUser, type AppointmentPriority } from "../../api/clinicApi";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

function isValidTenantId(tenantId: string | null | undefined) {
  if (!tenantId) {
    return false;
  }
  return UUID_PATTERN.test(tenantId) && !tenantId.toUpperCase().startsWith("DEFAULT-ROLES");
}

export default function QueuePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [queue, setQueue] = React.useState<Appointment[]>([]);
  const [queueSearch, setQueueSearch] = React.useState("");
  const [loadingDoctors, setLoadingDoctors] = React.useState(true);
  const [loadingQueue, setLoadingQueue] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [savingId, setSavingId] = React.useState<string | null>(null);

  const doctors = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const isClinicAdmin = tenantRole === "CLINIC_ADMIN";
  const isReceptionist = tenantRole === "RECEPTIONIST";
  const canStartConsultation = isDoctor && auth.hasPermission("consultation.create");
  const canManageDeskStatus = (isClinicAdmin || isReceptionist) && auth.hasPermission("appointment.manage");
  const canReorderQueue = (isDoctor || isClinicAdmin || isReceptionist) && auth.hasPermission("appointment.manage");
  const tenantReady = isValidTenantId(auth.tenantId);
  const [draggingId, setDraggingId] = React.useState<string | null>(null);
  const visibleQueue = queue.filter((appointment) => {
    const term = queueSearch.trim().toLowerCase();
    if (!term) return true;
    return [
      appointment.tokenNumber?.toString(),
      appointment.patientName,
      appointment.patientNumber,
      appointment.reason,
      appointment.status,
      appointment.priority,
    ].filter(Boolean).some((value) => String(value).toLowerCase().includes(term));
  });

  React.useEffect(() => {
    let cancelled = false;
    async function loadUsers() {
      if (!auth.accessToken || !tenantReady || !auth.tenantId) {
        setLoadingDoctors(false);
        return;
      }
      setLoadingDoctors(true);
      try {
        if (isDoctor && auth.appUserId) {
          setDoctorUserId(auth.appUserId);
          return;
        }
        const rows = await getClinicUsers(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setUsers(rows);
          const firstDoctor = rows.find((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
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
  }, [auth.accessToken, auth.tenantId, auth.appUserId, isDoctor, tenantReady]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadQueue() {
      if (!auth.accessToken || !tenantReady || !auth.tenantId || !doctorUserId) {
        setQueue([]);
        setLoadingQueue(false);
        return;
      }
      setLoadingQueue(true);
      setError(null);
      try {
        const rows = await getDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId);
        if (!cancelled) {
          setQueue(rows);
        }
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
  }, [auth.accessToken, auth.tenantId, doctorUserId, tenantReady]);

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
      let comment: string | null = null;
      const current = queue.find((item) => item.id === appointmentId);
      const requiresComment = (status === "CANCELLED" || status === "NO_SHOW") && current?.status === "WAITING";
      if (requiresComment) {
        comment = window.prompt("Cancellation/No-show reason is required after check-in.")?.trim() || null;
        if (!comment) {
          setError("Reason/comment is required after check-in.");
          return;
        }
      }
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointmentId, status, comment);
      const refreshed = await getDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId);
      setQueue(refreshed);
    } catch (err) {
      setError("Queue action failed. Please refresh and verify the appointment status.");
      console.error("Queue status update failed", err);
    } finally {
      setSavingId(null);
    }
  };

  const reorderableIds = React.useMemo(
    () => queue.filter((item) => item.status === "BOOKED" || item.status === "WAITING").map((item) => item.id),
    [queue],
  );

  const reorderQueue = async (orderedIds: string[]) => {
    if (!auth.accessToken || !auth.tenantId || !doctorUserId || !canReorderQueue) return;
    setSavingId("__queue__");
    setError(null);
    try {
      const refreshed = await reorderDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId, orderedIds);
      setQueue(refreshed);
    } catch (err) {
      setError("Unable to reorder queue. Please refresh and try again.");
      console.error("Queue reorder failed", err);
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

  const changePriority = async (appointmentId: string, nextPriority: AppointmentPriority) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSavingId(appointmentId);
    setError(null);
    try {
      await updateAppointmentPriority(auth.accessToken, auth.tenantId, appointmentId, nextPriority);
      const refreshed = await getDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId);
      setQueue(refreshed);
    } catch (err) {
      setError("Unable to update priority. Please refresh and try again.");
      console.error("Queue priority update failed", err);
    } finally {
      setSavingId(null);
    }
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Queue</Typography>
          <Typography variant="body2" color="text.secondary">
            Doctor-wise queue view with status transitions.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => navigate("/appointments")}>Appointments</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            {isDoctor ? (
              <Alert severity="info">Showing only queue items assigned to you.</Alert>
            ) : (
              <FormControl sx={{ maxWidth: 420 }}>
                <InputLabel id="queue-doctor-label">Assigned doctor</InputLabel>
                <Select
                  labelId="queue-doctor-label"
                  label="Assigned doctor"
                  value={doctorUserId}
                  onChange={(e) => setDoctorUserId(String(e.target.value))}
                >
                  {doctors.map((doctor) => (
                    <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                      {doctor.displayName || doctor.email || doctor.appUserId}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}

            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label={`Booked ${queue.filter((item) => item.status === "BOOKED").length}`} color="warning" variant="outlined" />
              <Chip label={`Waiting ${queue.filter((item) => item.status === "WAITING").length}`} color="warning" />
              <Chip label={`In consultation ${queue.filter((item) => item.status === "IN_CONSULTATION").length}`} color="info" />
              <Chip label={`Completed ${queue.filter((item) => item.status === "COMPLETED").length}`} color="success" />
            </Stack>
            {!doctorUserId ? (
              <Alert severity="info">
                {loadingDoctors ? "Loading available doctors..." : "Select a doctor to view the queue."}
              </Alert>
            ) : null}
            <TextField
              size="small"
              label="Find queue patient"
              placeholder="Search by token, UHID, patient name, status, or reason"
              value={queueSearch}
              onChange={(event) => setQueueSearch(event.target.value)}
              sx={{ maxWidth: 520 }}
            />

            {loadingQueue ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
                <Stack spacing={1} alignItems="center">
                  <CircularProgress />
                  <Typography variant="body2" color="text.secondary">
                    Loading queue items...
                  </Typography>
                </Stack>
              </Box>
            ) : visibleQueue.length === 0 ? (
              <Alert severity="info">No queue items were found for the selected doctor today.</Alert>
            ) : (
              <Box sx={{ overflowX: "auto" }}>
                <Table size="small" sx={{ minWidth: 920 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Token</TableCell>
                      <TableCell>Patient</TableCell>
                      <TableCell>Priority</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Arrival</TableCell>
                      <TableCell>Reason</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                  {visibleQueue.map((appointment) => (
                      <TableRow
                        key={appointment.id}
                        draggable={canReorderQueue && (appointment.status === "BOOKED" || appointment.status === "WAITING")}
                        onDragStart={() => setDraggingId(appointment.id)}
                        onDragOver={(event) => {
                          if (draggingId) {
                            event.preventDefault();
                          }
                        }}
                        onDrop={(event) => {
                          event.preventDefault();
                          if (!draggingId || draggingId === appointment.id) return;
                          if (!(appointment.status === "BOOKED" || appointment.status === "WAITING")) return;
                          const next = reorderableIds.filter((id) => id !== draggingId);
                          const targetIndex = next.indexOf(appointment.id);
                          if (targetIndex < 0) return;
                          next.splice(targetIndex, 0, draggingId);
                          void reorderQueue(next);
                          setDraggingId(null);
                        }}
                        onDragEnd={() => setDraggingId(null)}
                      >
                        <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                        <TableCell>
                          <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                            {appointment.patientName || appointment.patientNumber || appointment.patientId}
                          </Button>
                        </TableCell>
                        <TableCell>
                          {canManageDeskStatus ? (
                            <FormControl size="small" fullWidth sx={{ minWidth: 160 }}>
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
                        <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                        <TableCell>{appointment.type}</TableCell>
                        <TableCell>{appointment.status === "BOOKED" ? "Not checked in" : appointment.status === "WAITING" ? "Checked in" : appointment.status}</TableCell>
                        <TableCell>{appointment.reason || "-"}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
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
                          {canManageDeskStatus && appointment.status === "BOOKED" ? <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "WAITING")}>
                            Check In
                          </Button> : null}
                          {canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING") ? <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "CANCELLED")}>
                            Cancel
                          </Button> : null}
                          {canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING") ? <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "NO_SHOW")}>
                            No Show
                          </Button> : null}
                          {!(
                            (canStartConsultation && (appointment.status === "WAITING" || appointment.status === "IN_CONSULTATION")) ||
                            (canManageDeskStatus && (appointment.status === "BOOKED" || appointment.status === "WAITING"))
                          ) ? (
                            <Typography variant="caption" color="text.secondary">No actions</Typography>
                          ) : null}
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
    </Stack>
  );
}
