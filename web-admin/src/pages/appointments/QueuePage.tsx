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
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { getClinicUsers, getDoctorQueueToday, startConsultationFromAppointment, updateAppointmentStatus, type Appointment, type ClinicUser } from "../../api/clinicApi";

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

export default function QueuePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [queue, setQueue] = React.useState<Appointment[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [savingId, setSavingId] = React.useState<string | null>(null);

  const doctors = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");

  React.useEffect(() => {
    let cancelled = false;
    async function loadUsers() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        const rows = await getClinicUsers(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setUsers(rows);
          const firstDoctor = rows.find((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
          setDoctorUserId((current) => current || firstDoctor?.appUserId || "");
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load doctors");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void loadUsers();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadQueue() {
      if (!auth.accessToken || !auth.tenantId || !doctorUserId) {
        setQueue([]);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const rows = await getDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId);
        if (!cancelled) {
          setQueue(rows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load queue");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void loadQueue();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, doctorUserId]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const updateStatus = async (appointmentId: string, status: Appointment["status"]) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSavingId(appointmentId);
    setError(null);
    try {
      await updateAppointmentStatus(auth.accessToken, auth.tenantId, appointmentId, status);
      const refreshed = await getDoctorQueueToday(auth.accessToken, auth.tenantId, doctorUserId);
      setQueue(refreshed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update status");
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
      setError(err instanceof Error ? err.message : "Failed to start consultation");
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
            <FormControl sx={{ maxWidth: 420 }}>
              <InputLabel id="queue-doctor-label">Doctor</InputLabel>
              <Select
                labelId="queue-doctor-label"
                label="Doctor"
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

            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
                <CircularProgress />
              </Box>
            ) : queue.length === 0 ? (
              <Alert severity="info">No queue items were found for the selected doctor today.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Token</TableCell>
                    <TableCell>Patient</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queue.map((appointment) => (
                    <TableRow key={appointment.id}>
                      <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                      <TableCell>
                        <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                          {appointment.patientName || appointment.patientNumber || appointment.patientId}
                        </Button>
                      </TableCell>
                      <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                      <TableCell>{appointment.type}</TableCell>
                      <TableCell>{appointment.reason || "-"}</TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void startConsultation(appointment.id)}>
                            Start Consultation
                          </Button>
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "WAITING")}>
                            Waiting
                          </Button>
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "IN_CONSULTATION")}>
                            In Consult
                          </Button>
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "COMPLETED")}>
                            Complete
                          </Button>
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "CANCELLED")}>
                            Cancel
                          </Button>
                          <Button size="small" disabled={savingId === appointment.id} onClick={() => void updateStatus(appointment.id, "NO_SHOW")}>
                            No Show
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
