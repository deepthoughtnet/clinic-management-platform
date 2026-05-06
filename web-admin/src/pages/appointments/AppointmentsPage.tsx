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
  Grid,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  createAppointment,
  createWalkInAppointment,
  getClinicUsers,
  getTodayAppointments,
  searchPatients,
  type Appointment,
  type AppointmentType,
  type ClinicUser,
  type Patient,
} from "../../api/clinicApi";

const appointmentTypes: AppointmentType[] = ["SCHEDULED", "FOLLOW_UP", "VACCINATION", "WALK_IN"];

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

export default function AppointmentsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [patientQuery, setPatientQuery] = React.useState("");
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [appointmentDate, setAppointmentDate] = React.useState(new Date().toISOString().slice(0, 10));
  const [appointmentTime, setAppointmentTime] = React.useState("09:00");
  const [type, setType] = React.useState<AppointmentType>("SCHEDULED");
  const [reason, setReason] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const doctorOptions = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const [userRows, appointmentRows] = await Promise.all([
          getClinicUsers(auth.accessToken, auth.tenantId),
          getTodayAppointments(auth.accessToken, auth.tenantId),
        ]);
        if (!cancelled) {
          setUsers(userRows);
          setAppointments(appointmentRows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load appointments");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientQuery.trim().length < 2) {
        setPatientResults([]);
        return;
      }
      try {
        const term = patientQuery.trim();
        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^\d{6,}$/.test(term) ? term : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^\d{6,}$/.test(term) ? undefined : term,
          active: true,
        });
        if (!cancelled) {
          setPatientResults(rows);
        }
      } catch {
        if (!cancelled) {
          setPatientResults([]);
        }
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, patientQuery]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !doctorUserId) {
      setError("Select a patient and doctor before saving.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const isWalkIn = type === "WALK_IN";
      if (isWalkIn) {
        await createWalkInAppointment(auth.accessToken, auth.tenantId, {
          patientId: selectedPatient.id,
          doctorUserId,
          appointmentDate,
          reason: reason.trim() || null,
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
        });
      }
      setPatientQuery("");
      setSelectedPatient(null);
      setReason("");
      setType("SCHEDULED");
      const updated = await getTodayAppointments(auth.accessToken, auth.tenantId);
      setAppointments(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save appointment");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Appointments</Typography>
          <Typography variant="body2" color="text.secondary">
            Scheduled visits and walk-in intake for the clinic tenant.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => navigate("/queue")}>Open Queue</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Create appointment</Typography>
                <TextField label="Search patient" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} helperText="Search by patient number, mobile, or name" />
                {selectedPatient ? (
                  <Chip
                    label={`${selectedPatient.firstName} ${selectedPatient.lastName} • ${selectedPatient.patientNumber}`}
                    onDelete={() => setSelectedPatient(null)}
                  />
                ) : null}
                {patientResults.length > 0 && !selectedPatient ? (
                  <Card variant="outlined">
                    <List dense disablePadding>
                      {patientResults.map((patient) => (
                        <ListItemButton key={patient.id} onClick={() => setSelectedPatient(patient)}>
                          <ListItemText
                            primary={`${patient.firstName} ${patient.lastName}`}
                            secondary={`${patient.patientNumber} • ${patient.mobile}`}
                          />
                        </ListItemButton>
                      ))}
                    </List>
                  </Card>
                ) : null}
                <FormControl fullWidth>
                  <InputLabel id="doctor-select-label">Doctor</InputLabel>
                  <Select labelId="doctor-select-label" label="Doctor" value={doctorUserId} onChange={(e) => setDoctorUserId(String(e.target.value))}>
                    {doctorOptions.map((doctor) => (
                      <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                        {doctor.displayName || doctor.email || doctor.appUserId}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="date" label="Date" value={appointmentDate} onChange={(e) => setAppointmentDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="time" label="Time" value={appointmentTime} onChange={(e) => setAppointmentTime(e.target.value)} InputLabelProps={{ shrink: true }} disabled={type === "WALK_IN"} /></Grid>
                </Grid>
                <FormControl fullWidth>
                  <InputLabel id="appointment-type-label">Type</InputLabel>
                  <Select labelId="appointment-type-label" label="Type" value={type} onChange={(e) => setType(e.target.value as AppointmentType)}>
                    {appointmentTypes.map((option) => (
                      <MenuItem key={option} value={option}>{option}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={3} />
                <Button variant="contained" onClick={() => void save()} disabled={saving}>
                  {type === "WALK_IN" ? "Create Walk-In" : "Create Appointment"}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Today's appointments</Typography>
                  <Button onClick={() => navigate("/queue")}>Go to Queue</Button>
                </Box>
                {loading ? (
                  <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
                    <CircularProgress />
                  </Box>
                ) : appointments.length === 0 ? (
                  <Alert severity="info">No appointments were found for today.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Patient</TableCell>
                        <TableCell>Doctor</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Token</TableCell>
                        <TableCell>Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {appointments.map((appointment) => (
                        <TableRow key={appointment.id}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                                {appointment.patientName || appointment.patientNumber || appointment.patientId}
                              </Button>
                              <Typography variant="caption" color="text.secondary">{appointment.patientNumber}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                          <TableCell>{appointment.type}</TableCell>
                          <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                          <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                          <TableCell>
                            <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)}>Patient</Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
