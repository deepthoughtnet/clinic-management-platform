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
  Switch,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  createAppointment,
  createWaitlist,
  getClinicUsers,
  getDoctorSlots,
  getWaitlist,
  rescheduleAppointment,
  searchAppointments,
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
  type Patient,
} from "../../api/clinicApi";

type SlotFilterKey = DoctorAvailabilitySlotStatus | "BOOKED" | "CHECKED_IN" | "IN_CONSULTATION" | "COMPLETED" | "NO_SHOW" | "CANCELLED";

type SlotSelection = {
  kind: "slot";
  slot: DoctorAvailabilitySlot;
};

type AppointmentSelection = {
  kind: "appointment";
  appointment: Appointment;
};

type Selection = SlotSelection | AppointmentSelection;

const APPOINTMENT_TYPES: AppointmentType[] = ["SCHEDULED", "FOLLOW_UP", "VACCINATION", "WALK_IN"];

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

export default function DayBoardPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [doctorUserId, setDoctorUserId] = React.useState("");
  const [date, setDate] = React.useState(new Date().toISOString().slice(0, 10));
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
  const [selected, setSelected] = React.useState<Selection | null>(null);
  const [rescheduleOpen, setRescheduleOpen] = React.useState(false);
  const [rescheduleTarget, setRescheduleTarget] = React.useState<Appointment | null>(null);
  const [rescheduleDoctorUserId, setRescheduleDoctorUserId] = React.useState("");
  const [rescheduleDate, setRescheduleDate] = React.useState("");
  const [rescheduleTime, setRescheduleTime] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = tenantRole === "DOCTOR";
  const canManage = auth.hasPermission("appointment.manage") || tenantRole === "RECEPTIONIST" || tenantRole === "CLINIC_ADMIN";
  const canBook = auth.hasPermission("appointment.create") || tenantRole === "RECEPTIONIST" || tenantRole === "CLINIC_ADMIN";
  const canStartConsultation = auth.hasPermission("consultation.create");
  const doctorOptions = users.filter((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
  const effectiveDoctorId = isDoctor && auth.appUserId ? auth.appUserId : doctorUserId;
  const selectedSlot = selected?.kind === "slot" ? selected.slot : null;
  const selectedAppointment = selected?.kind === "appointment" ? selected.appointment : null;

  const loadCore = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [clinicUsers, appointmentRows] = await Promise.all([
        getClinicUsers(auth.accessToken, auth.tenantId),
        searchAppointments(auth.accessToken, auth.tenantId, { appointmentDate: date, doctorUserId: effectiveDoctorId || undefined }),
      ]);
      setUsers(clinicUsers);
      setAppointments(appointmentRows);
      if (!isDoctor && !doctorUserId) {
        const firstDoctor = clinicUsers.find((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
        if (firstDoctor) setDoctorUserId(firstDoctor.appUserId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load day board data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, date, doctorUserId, effectiveDoctorId, isDoctor]);

  const loadDoctorPanels = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) {
      setSlots([]);
      setWaitlist([]);
      return;
    }
    try {
      const [slotRows, waitRows] = await Promise.all([
        getDoctorSlots(auth.accessToken, auth.tenantId, effectiveDoctorId, date),
        getWaitlist(auth.accessToken, auth.tenantId, { doctorUserId: effectiveDoctorId, preferredDate: date, status: "WAITING" }),
      ]);
      setSlots(slotRows);
      setWaitlist(waitRows);
    } catch {
      setSlots([]);
      setWaitlist([]);
    }
  }, [auth.accessToken, auth.tenantId, date, effectiveDoctorId]);

  React.useEffect(() => {
    void loadCore();
  }, [loadCore]);

  React.useEffect(() => {
    void loadDoctorPanels();
  }, [loadDoctorPanels]);

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

  const appointmentsBySlot = React.useMemo(() => {
    const map = new Map<string, Appointment[]>();
    for (const slot of slots) {
      map.set(toFive(slot.slotTime), appointments.filter((a) => sameTimeSlot(slot, a)));
    }
    return map;
  }, [appointments, slots]);

  const filteredSlots = React.useMemo(() => slots.filter((slot) => filters[slot.status]), [filters, slots]);

  const refreshAll = async () => {
    await loadCore();
    await loadDoctorPanels();
  };

  const bookFromSlot = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !effectiveDoctorId) return;
    const slotTime = selectedSlot ? toFive(selectedSlot.slotTime) : manualTime;
    if (!slotTime) {
      setError("Pick a slot or enter manual time.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createAppointment(auth.accessToken, auth.tenantId, {
        patientId: selectedPatient.id,
        doctorUserId: effectiveDoctorId,
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
    if (!auth.accessToken || !auth.tenantId || !selectedPatient || !effectiveDoctorId) return;
    try {
      await createWaitlist(auth.accessToken, auth.tenantId, {
        patientId: selectedPatient.id,
        doctorUserId: effectiveDoctorId,
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

  const openReschedule = (appointment: Appointment) => {
    setRescheduleTarget(appointment);
    setRescheduleDoctorUserId(appointment.doctorUserId);
    setRescheduleDate(appointment.appointmentDate);
    setRescheduleTime(toFive(appointment.appointmentTime));
    setRescheduleOpen(true);
  };

  const saveReschedule = async () => {
    if (!auth.accessToken || !auth.tenantId || !rescheduleTarget || !rescheduleDate || !rescheduleTime) return;
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
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId || !selectedSlot) return;
    try {
      await createAppointment(auth.accessToken, auth.tenantId, {
        patientId: entry.patientId,
        doctorUserId: effectiveDoctorId,
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

  if (!auth.tenantId) return <Alert severity="warning">No tenant selected.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Day Board</Typography>
          <Typography variant="body2" color="text.secondary">Calendar to booking to queue to consultation handoff.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void refreshAll()}>Refresh</Button>
          <Button variant="outlined" onClick={() => navigate("/appointments")}>Appointments</Button>
          <Button variant="outlined" onClick={() => navigate("/queue")}>Queue</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}>
          <Card variant="outlined" sx={{ position: { md: "sticky" }, top: { md: 16 } }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Filters</Typography>
                <TextField size="small" type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                <FormControl size="small" fullWidth>
                  <InputLabel id="day-board-doctor">Doctor</InputLabel>
                  <Select
                    labelId="day-board-doctor"
                    label="Doctor"
                    value={effectiveDoctorId}
                    onChange={(e) => setDoctorUserId(String(e.target.value))}
                    disabled={isDoctor}
                  >
                    {doctorOptions.map((d) => (
                      <MenuItem key={d.appUserId} value={d.appUserId}>{d.displayName || d.email || d.appUserId}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
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
                  <List dense sx={{ maxHeight: 160, overflowY: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
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
                {STATUS_FILTERS.map((key) => (
                  <Box key={key} sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <Typography variant="body2">{key.replace("PARTIALLY_BOOKED", "Partially booked").replace("CHECKED_IN", "Checked-in")}</Typography>
                    <Switch size="small" checked={filters[key]} onChange={(_, checked) => setFilters((c) => ({ ...c, [key]: checked }))} />
                  </Box>
                ))}
                <Divider />
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add availability</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add break</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/doctors/availability")}>Add leave</Button>
                  <Button size="small" variant="outlined" onClick={() => setSelected(null)}>New appointment</Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Timeline</Typography>
                {loading ? (
                  <Box sx={{ minHeight: 240, display: "grid", placeItems: "center" }}><CircularProgress /></Box>
                ) : !effectiveDoctorId ? (
                  <Alert severity="info">Select a doctor to view slots.</Alert>
                ) : filteredSlots.length === 0 ? (
                  <Alert severity="info">No configured schedule for selected date. Manual appointment booking remains available.</Alert>
                ) : (
                  <Stack spacing={1}>
                    {filteredSlots.map((slot) => {
                      const slotAppointments = appointmentsBySlot.get(toFive(slot.slotTime)) || [];
                      return (
                        <Card
                          key={`${slot.slotTime}-${slot.slotEndTime}`}
                          variant="outlined"
                          sx={{
                            borderColor: selected?.kind === "slot" && selected.slot.slotTime === slot.slotTime ? "primary.main" : "divider",
                            bgcolor: slot.status === "FULL" ? "error.50" : slot.status === "PARTIALLY_BOOKED" ? "warning.50" : "background.paper",
                          }}
                        >
                          <CardContent sx={{ py: 1.25 }}>
                            <Stack spacing={1}>
                              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                                <Button size="small" onClick={() => setSelected({ kind: "slot", slot })} sx={{ px: 0, minWidth: 0 }}>
                                  {toFive(slot.slotTime)} - {toFive(slot.slotEndTime)}
                                </Button>
                                <Stack direction="row" spacing={1}>
                                  <Chip size="small" label={`${slot.bookedCount}/${slot.maxPatientsPerSlot} booked`} color={slotColor(slot.status)} />
                                  <Chip size="small" label={slot.status} variant="outlined" />
                                </Stack>
                              </Box>
                              {slotAppointments.length > 0 ? (
                                <Stack direction="row" gap={0.75} flexWrap="wrap">
                                  {slotAppointments.map((appt) => (
                                    <Chip
                                      key={appt.id}
                                      size="small"
                                      label={`${appt.patientName || appt.patientNumber || appt.patientId} • ${appt.status}`}
                                      color={appointmentColor(appt.status)}
                                      variant="outlined"
                                      onClick={() => setSelected({ kind: "appointment", appointment: appt })}
                                    />
                                  ))}
                                </Stack>
                              ) : null}
                            </Stack>
                          </CardContent>
                        </Card>
                      );
                    })}
                  </Stack>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 3 }}>
          <Card variant="outlined" sx={{ position: { md: "sticky" }, top: { md: 16 } }}>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Details</Typography>
                {!selected ? <Alert severity="info">Select a slot or appointment.</Alert> : null}

                {selectedSlot ? (
                  <Stack spacing={1}>
                    <Typography variant="body2">Doctor: {doctorOptions.find((d) => d.appUserId === effectiveDoctorId)?.displayName || effectiveDoctorId}</Typography>
                    <Typography variant="body2">Time: {toFive(selectedSlot.slotTime)} - {toFive(selectedSlot.slotEndTime)}</Typography>
                    <Typography variant="body2">Capacity: {selectedSlot.bookedCount}/{selectedSlot.maxPatientsPerSlot}</Typography>
                    <Chip size="small" label={selectedSlot.status} color={slotColor(selectedSlot.status)} sx={{ width: "fit-content" }} />
                    <FormControl size="small" fullWidth>
                      <InputLabel id="db-type">Type</InputLabel>
                      <Select labelId="db-type" label="Type" value={appointmentType} onChange={(e) => setAppointmentType(e.target.value as AppointmentType)}>
                        {APPOINTMENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                      </Select>
                    </FormControl>
                    <TextField size="small" label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
                    <Button variant="contained" disabled={!canBook || !selectedPatient || !selectedSlot.selectable || saving} onClick={() => void bookFromSlot()}>
                      Book appointment
                    </Button>
                    <Button variant="outlined" disabled={!canBook || !selectedPatient} onClick={() => void addWaitlistFromSelection()}>
                      Add to waitlist
                    </Button>
                  </Stack>
                ) : null}

                {selectedAppointment ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{selectedAppointment.patientName || selectedAppointment.patientNumber || selectedAppointment.patientId}</Typography>
                    <Typography variant="caption" color="text.secondary">{selectedAppointment.appointmentDate} {toFive(selectedAppointment.appointmentTime)}</Typography>
                    <Chip size="small" label={selectedAppointment.status} color={appointmentColor(selectedAppointment.status)} sx={{ width: "fit-content" }} />
                    <Typography variant="body2">Queue: {selectedAppointment.status === "WAITING" ? "Checked-in" : selectedAppointment.status}</Typography>
                    <Typography variant="body2">Consultation: {selectedAppointment.consultationId ? "Started" : "Not started"}</Typography>
                    <Stack direction="row" gap={1} flexWrap="wrap">
                      <Button size="small" variant="contained" disabled={!canManage} onClick={() => void transitionStatus(selectedAppointment.id, "WAITING")}>Check-in</Button>
                      <Button size="small" variant="outlined" disabled={!canManage} onClick={() => void transitionStatus(selectedAppointment.id, "NO_SHOW")}>No-show</Button>
                      <Button size="small" variant="outlined" disabled={!canManage} onClick={() => void transitionStatus(selectedAppointment.id, "CANCELLED")}>Cancel</Button>
                      <Button size="small" variant="outlined" onClick={() => openReschedule(selectedAppointment)}>Reschedule</Button>
                      <Button size="small" variant="outlined" disabled={!canStartConsultation} onClick={() => void startConsultation(selectedAppointment.id)}>Start consultation</Button>
                      <Button size="small" onClick={() => navigate(`/patients/${selectedAppointment.patientId}`)}>Open patient</Button>
                      <Button size="small" disabled={!selectedAppointment.consultationId} onClick={() => selectedAppointment.consultationId && navigate(`/consultations/${selectedAppointment.consultationId}`)}>Open consultation</Button>
                    </Stack>
                  </Stack>
                ) : null}

                {!slots.length ? (
                  <Stack spacing={1}>
                    <Alert severity="info">No configured schedule. Manual appointment time is enabled.</Alert>
                    <TextField size="small" type="time" label="Manual time" value={manualTime} onChange={(e) => setManualTime(e.target.value)} InputLabelProps={{ shrink: true }} />
                    <Button variant="contained" disabled={!canBook || !selectedPatient || !manualTime} onClick={() => void bookFromSlot()}>Book manual time</Button>
                  </Stack>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card variant="outlined" sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Waitlist</Typography>
              {waitlist.length === 0 ? <Alert severity="info">No waitlist entries.</Alert> : (
                <Stack spacing={1}>
                  {waitlist.map((entry) => (
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

      <Dialog open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Reschedule appointment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <FormControl fullWidth>
              <InputLabel id="reschedule-doctor">Doctor</InputLabel>
              <Select labelId="reschedule-doctor" label="Doctor" value={rescheduleDoctorUserId} onChange={(e) => setRescheduleDoctorUserId(String(e.target.value))}>
                {doctorOptions.map((d) => (
                  <MenuItem key={d.appUserId} value={d.appUserId}>{d.displayName || d.email || d.appUserId}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField type="date" label="Date" value={rescheduleDate} onChange={(e) => setRescheduleDate(e.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField type="time" label="Time" value={rescheduleTime} onChange={(e) => setRescheduleTime(e.target.value)} InputLabelProps={{ shrink: true }} />
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
