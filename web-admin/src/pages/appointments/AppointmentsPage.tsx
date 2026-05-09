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
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  createAppointment,
  createPatient,
  createWalkInAppointment,
  getDoctorSlots,
  getClinicUsers,
  getPatient,
  searchAppointments,
  searchPatients,
  type Appointment,
  type DoctorAvailabilitySlot,
  type AppointmentPriority,
  type AppointmentType,
  type ClinicUser,
  type Patient,
  type PatientGender,
  type PatientInput,
} from "../../api/clinicApi";

type AppointmentTab = "today" | "upcoming" | "completed" | "archive";

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

function formatDate(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString();
}

function isPastDateTime(date: string, time: string | null | undefined) {
  if (!date) return false;
  const now = new Date();
  const candidate = new Date(`${date}T${time && time.trim() ? time : "23:59"}:00`);
  return candidate.getTime() < now.getTime();
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

  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [appointments, setAppointments] = React.useState<Appointment[]>([]);
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [patientQuery, setPatientQuery] = React.useState(statePatient ? patientSummary(statePatient) : "");
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(statePatient);
  const [doctorUserId, setDoctorUserId] = React.useState(doctorUserIdFromQuery);
  const [appointmentDate, setAppointmentDate] = React.useState(new Date().toISOString().slice(0, 10));
  const [appointmentTime, setAppointmentTime] = React.useState("");
  const [type, setType] = React.useState<AppointmentType>("SCHEDULED");
  const [priority, setPriority] = React.useState<AppointmentPriority>("NORMAL");
  const [reason, setReason] = React.useState("");
  const [calendarDate, setCalendarDate] = React.useState(new Date().toISOString().slice(0, 10));
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

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const doctorOptions = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
  const doctorFilter = isDoctor && auth.appUserId ? auth.appUserId : undefined;
  const today = new Date().toISOString().slice(0, 10);

  const selectedDoctorId = doctorFilter || doctorUserId || "";
  const requiresAppointmentTime = type !== "WALK_IN";
  const manualTimeAllowed = requiresAppointmentTime && Boolean(selectedDoctorId) && slots.length === 0;
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
  const calendarRows = React.useMemo(
    () => appointments
      .filter((item) => item.appointmentDate === calendarDate)
      .sort((left, right) => (left.appointmentTime || "").localeCompare(right.appointmentTime || "")),
    [appointments, calendarDate],
  );

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const saveAppointment = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !doctorUserId) {
      setError("Select a patient and doctor before saving.");
      return;
    }
    if (type !== "WALK_IN" && !appointmentTime) {
      setError("Select an available slot or enter an appointment time before saving.");
      return;
    }
    if (isPastDateTime(appointmentDate, type === "WALK_IN" ? null : appointmentTime || null)) {
      setError("Appointment date/time cannot be in the past.");
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
        });
      }
      setPatientQuery("");
      setPatientResults([]);
      setSelectedPatient(null);
      setReason("");
      setType("SCHEDULED");
      setPriority("NORMAL");
      setAppointmentTime("");
      await loadAppointments();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save appointment");
    } finally {
      setSaving(false);
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
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Appointments</Typography>
          <Typography variant="body2" color="text.secondary">
            Search a patient by mobile, name, patient ID, or patient number. Create today’s visit without repeating lookup.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => navigate("/queue")}>Open Queue</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {isDoctor ? "My Calendar" : "Clinic Calendar"}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {isDoctor ? "Appointments assigned to you for the selected date." : "Clinic-wide appointment schedule for front-desk planning."}
                </Typography>
              </Box>
              <TextField
                type="date"
                label="Calendar date"
                value={calendarDate}
                onChange={(event) => setCalendarDate(event.target.value)}
                InputLabelProps={{ shrink: true }}
                size="small"
              />
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip size="small" label={`${calendarRows.length} total`} variant="outlined" />
              <Chip size="small" label={`${calendarRows.filter((item) => item.status === "BOOKED").length} booked`} color="warning" variant="outlined" />
              <Chip size="small" label={`${calendarRows.filter((item) => item.status === "WAITING").length} checked in`} color="warning" />
              <Chip size="small" label={`${calendarRows.filter((item) => item.status === "IN_CONSULTATION").length} in consultation`} color="info" />
            </Stack>
            {calendarRows.length === 0 ? (
              <Alert severity="info">No appointments scheduled for this date.</Alert>
            ) : (
              <Stack direction="row" flexWrap="wrap" gap={1}>
                {calendarRows.map((appointment) => (
                  <Button
                    key={appointment.id}
                    variant="outlined"
                    size="small"
                    onClick={() => navigate(`/patients/${appointment.patientId}`)}
                    sx={{ justifyContent: "flex-start" }}
                  >
                    {(appointment.appointmentTime || "No time") + " • " + (appointment.patientName || appointment.patientNumber || "Patient") + " • " + appointment.status}
                  </Button>
                ))}
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        {!isDoctor ? (
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Create appointment</Typography>
                  <TextField
                    label="Search patient"
                    value={patientQuery}
                    onChange={(event) => {
                      setSelectedPatient(null);
                      setPatientQuery(event.target.value);
                    }}
                    helperText="Search by patient ID, patient number, mobile, or name"
                  />
                  {selectedPatient ? (
                    <Card variant="outlined" sx={{ bgcolor: "primary.50" }}>
                      <CardContent sx={{ py: 1.5 }}>
                        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                          <Box>
                            <Typography sx={{ fontWeight: 800 }}>{patientLabel(selectedPatient)}</Typography>
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
                            <ListItemText
                              primary={patientLabel(patient)}
                              secondary={patientSummary(patient)}
                            />
                          </ListItemButton>
                        ))}
                      </List>
                    </Card>
                  ) : null}
                  {searchingPatients ? (
                    <Alert severity="info">Searching for matching patients...</Alert>
                  ) : patientResults.length === 0 && patientQuery.trim().length >= 2 && !selectedPatient ? (
                    <Alert severity="info" action={<Button color="inherit" size="small" onClick={openQuickRegister}>Quick Register Patient</Button>}>
                      No matching patient found. Quick register a family member and continue without searching again.
                    </Alert>
                  ) : null}
                    <FormControl fullWidth>
                      <InputLabel id="doctor-select-label">Doctor</InputLabel>
                      <Select labelId="doctor-select-label" label="Doctor" value={selectedDoctorId} onChange={(event) => setDoctorUserId(String(event.target.value))} disabled={Boolean(doctorFilter)}>
                        <MenuItem value="">Select doctor</MenuItem>
                        {doctorOptions.map((doctor) => (
                          <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                            {doctor.displayName || doctor.email || doctor.appUserId}
                          </MenuItem>
                        ))}
                    </Select>
                  </FormControl>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth type="date" label="Date" value={appointmentDate} onChange={(event) => setAppointmentDate(event.target.value)} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
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
                            : manualTimeAllowed
                              ? "No schedule is configured for this doctor/date. Enter the time manually."
                              : "Pick a slot below or review availability before booking."
                        }
                      />
                    </Grid>
                  </Grid>
                  {type !== "WALK_IN" ? (
                    <Card variant="outlined" sx={{ bgcolor: "background.default" }}>
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                            <Box>
                              <Typography sx={{ fontWeight: 800 }}>Available slots</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {slots.length ? "Booked and unavailable slots are shown alongside the open gaps." : "Select a doctor and date to load the schedule."}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip size="small" label={`${slots.filter((slot) => slot.status === "AVAILABLE").length} available`} variant="outlined" />
                              <Chip size="small" label={`${slots.filter((slot) => slot.status === "BOOKED").length} booked`} variant="outlined" />
                              <Chip size="small" label={`${slots.filter((slot) => slot.status === "UNAVAILABLE").length} unavailable`} variant="outlined" />
                            </Stack>
                          </Box>
                          {slots.length === 0 ? (
                            <Alert severity="info">No schedule configured. Enter time manually or configure doctor availability.</Alert>
                          ) : (
                            <Stack direction="row" flexWrap="wrap" gap={1}>
                              {slots.map((slot) => {
                                const timeLabel = slot.slotTime.length >= 5 ? slot.slotTime.slice(0, 5) : slot.slotTime;
                                const selected = appointmentTime === timeLabel;
                                const label = slot.status === "BOOKED" && slot.selectable
                                  ? `${timeLabel} • ${slot.bookedCount}/${slot.maxPatientsPerSlot}`
                                  : slot.status === "BOOKED"
                                    ? `${timeLabel} • Full`
                                    : timeLabel;
                                return (
                                  <Button
                                    key={`${slot.slotTime}-${slot.slotEndTime}`}
                                    size="small"
                                    variant={selected ? "contained" : "outlined"}
                                    color={slot.status === "UNAVAILABLE" ? "inherit" : slot.status === "BOOKED" ? "warning" : "primary"}
                                    disabled={slot.status === "UNAVAILABLE" || (!slot.selectable && slot.status === "BOOKED")}
                                    onClick={() => setAppointmentTime(timeLabel)}
                                  >
                                    {label}
                                  </Button>
                                );
                              })}
                            </Stack>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}
                  <FormControl fullWidth>
                    <InputLabel id="appointment-type-label">Type</InputLabel>
                    <Select labelId="appointment-type-label" label="Type" value={type} onChange={(event) => setType(event.target.value as AppointmentType)}>
                      {appointmentTypes.map((option) => (
                        <MenuItem key={option} value={option}>{option}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <FormControl fullWidth>
                    <InputLabel id="appointment-priority-label">Priority</InputLabel>
                    <Select labelId="appointment-priority-label" label="Priority" value={priority} onChange={(event) => setPriority(event.target.value as AppointmentPriority)}>
                      {appointmentPriorities.map((option) => (
                        <MenuItem key={option} value={option}>{option}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField label="Reason" value={reason} onChange={(event) => setReason(event.target.value)} multiline minRows={3} />
                  <Button variant="contained" onClick={() => void saveAppointment()} disabled={!canCreateAppointment}>
                    {type === "WALK_IN" ? "Create Walk-In" : "Create Appointment"}
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ) : null}

        <Grid size={{ xs: 12, lg: 7 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Appointment list</Typography>
                  <Button onClick={() => navigate("/queue")}>Go to Queue</Button>
                </Box>
                <TextField
                  size="small"
                  fullWidth
                  label="Search Today/Appointments"
                  value={listSearch}
                  onChange={(event) => setListSearch(event.target.value)}
                  placeholder="Appointment ID, token, consultation ID, patient, mobile, doctor"
                />

                <Tabs value={tab} onChange={(_, value) => setTab(value)}>
                  <Tab value="today" label={`Today (${todayRows.length})`} />
                  <Tab value="upcoming" label={`Upcoming (${upcomingRows.length})`} />
                  <Tab value="completed" label={`Completed (${completedRows.length})`} />
                  <Tab value="archive" label={`Cancelled / No-show (${archiveRows.length})`} />
                </Tabs>

                {loading ? (
                  <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
                    <Stack spacing={1} alignItems="center">
                      <CircularProgress />
                      <Typography variant="body2" color="text.secondary">
                        Loading appointments...
                      </Typography>
                    </Stack>
                  </Box>
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
                        <TableCell>Actions</TableCell>
                      </TableRow>
                      </TableHead>
                      <TableBody>
                      {filteredVisibleAppointments.map((appointment) => (
                        <TableRow key={appointment.id}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)} sx={{ justifyContent: "flex-start", p: 0, minWidth: 0 }}>
                                {appointment.patientName || appointment.patientNumber || appointment.patientId}
                              </Button>
                              <Typography variant="caption" color="text.secondary">{appointment.patientNumber}</Typography>
                              <Typography variant="caption" color="text.secondary">{appointment.patientMobile || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">Appt: {appointment.id}</Typography>
                              <Typography variant="caption" color="text.secondary">Consult: {appointment.consultationId || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                          <TableCell>{formatDate(appointment.appointmentDate)}</TableCell>
                          <TableCell>{appointment.appointmentTime || "-"}</TableCell>
                          <TableCell>{appointment.tokenNumber ?? "-"}</TableCell>
                          <TableCell><Chip size="small" label={appointment.priority || "NORMAL"} color={priorityColor(appointment.priority)} variant="outlined" /></TableCell>
                          <TableCell>{arrivalLabel(appointment)}</TableCell>
                          <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                          <TableCell>
                            <Button size="small" onClick={() => navigate(`/patients/${appointment.patientId}`)}>Patient</Button>
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
        </Grid>
      </Grid>

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
    </Stack>
  );
}
