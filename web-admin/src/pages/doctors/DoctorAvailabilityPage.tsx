import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
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
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import {
  createDoctorAvailability,
  createDoctorUnavailability,
  createWaitlist,
  deactivateDoctorUnavailability,
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
} from "../../api/clinicApi";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
type ViewMode = "day" | "week";

type StatusFilters = Record<DoctorAvailabilitySlotStatus | "WAITLIST", boolean>;

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
  startAt: `${new Date().toISOString().slice(0, 10)}T13:00:00Z`,
  endAt: `${new Date().toISOString().slice(0, 10)}T14:00:00Z`,
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
  UNAVAILABLE: true,
  CONFLICTED: true,
  WAITLIST: true,
};

function toIsoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

function addDays(date: string, days: number) {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() + days);
  return toIsoDate(d);
}

function weekDates(date: string) {
  const d = new Date(`${date}T00:00:00`);
  const day = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - day);
  const start = toIsoDate(d);
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
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
    case "LEAVE":
    case "UNAVAILABLE":
    case "CONFLICTED":
      return "default";
  }
}

export default function DoctorAvailabilityPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";

  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [selectedDoctor, setSelectedDoctor] = React.useState("");
  const [viewMode, setViewMode] = React.useState<ViewMode>("day");
  const [date, setDate] = React.useState(toIsoDate(new Date()));

  const [availabilityRows, setAvailabilityRows] = React.useState<DoctorAvailability[]>([]);
  const [slotsByDate, setSlotsByDate] = React.useState<Record<string, DoctorAvailabilitySlot[]>>({});
  const [appointmentsByDate, setAppointmentsByDate] = React.useState<Record<string, Appointment[]>>({});
  const [unavailability, setUnavailability] = React.useState<DoctorUnavailability[]>([]);
  const [waitlist, setWaitlist] = React.useState<AppointmentWaitlist[]>([]);

  const [availabilityForm, setAvailabilityForm] = React.useState<DoctorAvailabilityInput>(EMPTY_AVAILABILITY_FORM);
  const [unavailabilityForm, setUnavailabilityForm] = React.useState<DoctorUnavailabilityInput>(EMPTY_UNAVAILABILITY_FORM);
  const [waitlistPatientId, setWaitlistPatientId] = React.useState("");
  const [waitlistReason, setWaitlistReason] = React.useState("");

  const [filters, setFilters] = React.useState<StatusFilters>(DEFAULT_FILTERS);
  const [selectedSlot, setSelectedSlot] = React.useState<{ date: string; slot: DoctorAvailabilitySlot } | null>(null);
  const [selectedAppointment, setSelectedAppointment] = React.useState<Appointment | null>(null);

  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const doctorOptions = users.filter((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR");
  const effectiveDoctorId = isDoctor ? (auth.appUserId || "") : selectedDoctor;
  const visibleDates = viewMode === "day" ? [date] : weekDates(date);

  const loadStatic = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const [userRows, allAvailability] = await Promise.all([
        getClinicUsers(auth.accessToken, auth.tenantId),
        getDoctorAvailability(auth.accessToken, auth.tenantId),
      ]);
      setUsers(userRows);
      const defaultDoctor = isDoctor
        ? (auth.appUserId || "")
        : (selectedDoctor || userRows.find((u) => (u.membershipRole || "").toUpperCase() === "DOCTOR")?.appUserId || "");
      if (!isDoctor && !selectedDoctor) setSelectedDoctor(defaultDoctor);
      setAvailabilityRows(allAvailability.filter((row) => row.doctorUserId === defaultDoctor));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load doctors/availability");
    }
  }, [auth.accessToken, auth.appUserId, auth.tenantId, isDoctor, selectedDoctor]);

  const loadDynamic = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) {
      setSlotsByDate({});
      setAppointmentsByDate({});
      setWaitlist([]);
      setUnavailability([]);
      return;
    }
    try {
      const slotsEntries = await Promise.all(
        visibleDates.map(async (d) => [d, await getDoctorSlots(auth.accessToken!, auth.tenantId!, effectiveDoctorId, d)] as const),
      );
      const appointmentEntries = await Promise.all(
        visibleDates.map(async (d) => [d, await searchAppointments(auth.accessToken!, auth.tenantId!, { doctorUserId: effectiveDoctorId, appointmentDate: d })] as const),
      );
      setSlotsByDate(Object.fromEntries(slotsEntries));
      setAppointmentsByDate(Object.fromEntries(appointmentEntries));
      setWaitlist(await getWaitlist(auth.accessToken, auth.tenantId, { doctorUserId: effectiveDoctorId, preferredDate: date, status: "WAITING" }));
      setUnavailability(await getDoctorUnavailability(auth.accessToken, auth.tenantId, effectiveDoctorId));
    } catch {
      setSlotsByDate({});
      setAppointmentsByDate({});
    }
  }, [auth.accessToken, auth.tenantId, effectiveDoctorId, visibleDates, date]);

  React.useEffect(() => { void loadStatic(); }, [loadStatic]);
  React.useEffect(() => { void loadDynamic(); }, [loadDynamic]);

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;

  const filteredSlots = (d: string) => (slotsByDate[d] || []).filter((s) => filters[s.status]);

  const quickCreateAvailability = async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) return;
    try {
      await createDoctorAvailability(auth.accessToken, auth.tenantId, effectiveDoctorId, availabilityForm);
      setInfo("Availability session added");
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
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) return;
    try {
      await createDoctorUnavailability(auth.accessToken, auth.tenantId, effectiveDoctorId, unavailabilityForm);
      setInfo("Leave/unavailable block added");
      await loadDynamic();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add leave/unavailable block");
    }
  };

  const quickAddWaitlist = async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId || !waitlistPatientId.trim()) return;
    try {
      await createWaitlist(auth.accessToken, auth.tenantId, {
        patientId: waitlistPatientId.trim(),
        doctorUserId: effectiveDoctorId,
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

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 3 }}>
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Scheduler Controls</Typography>
                {!isDoctor ? (
                  <FormControl fullWidth>
                    <InputLabel id="doctor-scheduler-select-label">Doctor</InputLabel>
                    <Select
                      labelId="doctor-scheduler-select-label"
                      label="Doctor"
                      value={selectedDoctor}
                      onChange={(e) => setSelectedDoctor(String(e.target.value))}
                    >
                      {doctorOptions.map((doctor) => (
                        <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                          {doctor.displayName || doctor.email || doctor.appUserId}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                ) : null}
                <TextField fullWidth type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                <ToggleButtonGroup exclusive value={viewMode} onChange={(_, value) => value && setViewMode(value)} size="small" fullWidth>
                  <ToggleButton value="day">Day</ToggleButton>
                  <ToggleButton value="week">Week</ToggleButton>
                </ToggleButtonGroup>
                <TextField fullWidth type="month" label="Mini Month" value={date.slice(0, 7)} onChange={(e) => setDate(`${e.target.value}-01`)} InputLabelProps={{ shrink: true }} />
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ mb: 1 }}>Filters</Typography>
              <Stack direction="row" gap={1} flexWrap="wrap">
                {Object.keys(filters).map((k) => {
                  const key = k as keyof StatusFilters;
                  return (
                    <Chip
                      key={k}
                      clickable
                      label={k.toLowerCase().replace("_", " ")}
                      color={filters[key] ? "primary" : "default"}
                      variant={filters[key] ? "filled" : "outlined"}
                      onClick={() => setFilters((f) => ({ ...f, [key]: !f[key] }))}
                    />
                  );
                })}
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ mb: 1 }}>Quick Actions</Typography>
              <Stack spacing={1.5}>
                <Typography variant="caption" color="text.secondary">Add availability</Typography>
                <FormControl fullWidth size="small">
                  <InputLabel id="quick-day">Day</InputLabel>
                  <Select labelId="quick-day" label="Day" value={availabilityForm.dayOfWeek} onChange={(e) => setAvailabilityForm((c) => ({ ...c, dayOfWeek: String(e.target.value) }))}>
                    {DAYS.map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                  </Select>
                </FormControl>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="time" label="Start" value={availabilityForm.startTime} onChange={(e) => setAvailabilityForm((c) => ({ ...c, startTime: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="time" label="End" value={availabilityForm.endTime} onChange={(e) => setAvailabilityForm((c) => ({ ...c, endTime: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="number" label="Duration" value={availabilityForm.consultationDurationMinutes} onChange={(e) => setAvailabilityForm((c) => ({ ...c, consultationDurationMinutes: Number(e.target.value) }))} /></Grid>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="number" label="Capacity" value={availabilityForm.maxPatientsPerSlot || 1} onChange={(e) => setAvailabilityForm((c) => ({ ...c, maxPatientsPerSlot: Number(e.target.value) }))} /></Grid>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="time" label="Break start" value={availabilityForm.breakStartTime || ""} onChange={(e) => setAvailabilityForm((c) => ({ ...c, breakStartTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} /></Grid>
                  <Grid size={{ xs: 6 }}><TextField size="small" fullWidth type="time" label="Break end" value={availabilityForm.breakEndTime || ""} onChange={(e) => setAvailabilityForm((c) => ({ ...c, breakEndTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} /></Grid>
                </Grid>
                <Button onClick={() => void quickCreateAvailability()} disabled={!effectiveDoctorId}>Add availability</Button>

                <Typography variant="caption" color="text.secondary">Add leave / unavailable</Typography>
                <TextField size="small" fullWidth type="datetime-local" label="Start" value={unavailabilityForm.startAt.slice(0, 16)} onChange={(e) => setUnavailabilityForm((c) => ({ ...c, startAt: `${e.target.value}:00Z` }))} InputLabelProps={{ shrink: true }} />
                <TextField size="small" fullWidth type="datetime-local" label="End" value={unavailabilityForm.endAt.slice(0, 16)} onChange={(e) => setUnavailabilityForm((c) => ({ ...c, endAt: `${e.target.value}:00Z` }))} InputLabelProps={{ shrink: true }} />
                <FormControl fullWidth size="small">
                  <InputLabel id="quick-unavail-type">Type</InputLabel>
                  <Select labelId="quick-unavail-type" label="Type" value={unavailabilityForm.type} onChange={(e) => setUnavailabilityForm((c) => ({ ...c, type: e.target.value as DoctorUnavailabilityType }))}>
                    <MenuItem value="LEAVE">LEAVE</MenuItem>
                    <MenuItem value="HOLIDAY">HOLIDAY</MenuItem>
                    <MenuItem value="UNAVAILABLE">UNAVAILABLE</MenuItem>
                    <MenuItem value="EMERGENCY_BLOCK">EMERGENCY_BLOCK</MenuItem>
                  </Select>
                </FormControl>
                <TextField size="small" fullWidth label="Reason" value={unavailabilityForm.reason || ""} onChange={(e) => setUnavailabilityForm((c) => ({ ...c, reason: e.target.value || null }))} />
                <Button variant="outlined" onClick={() => void quickCreateUnavailability()} disabled={!effectiveDoctorId}>Add leave</Button>

                <Typography variant="caption" color="text.secondary">Add waitlist entry</Typography>
                <TextField size="small" fullWidth label="Patient ID" value={waitlistPatientId} onChange={(e) => setWaitlistPatientId(e.target.value)} />
                <TextField size="small" fullWidth label="Reason" value={waitlistReason} onChange={(e) => setWaitlistReason(e.target.value)} />
                <Button variant="outlined" onClick={() => void quickAddWaitlist()} disabled={!effectiveDoctorId}>Add waitlist entry</Button>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      </Grid>

      <Grid size={{ xs: 12, lg: 6 }}>
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Operational Calendar</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Morning and evening sessions are visually grouped by time. Click a slot to inspect and act.
              </Typography>
              <Grid container spacing={1}>
                {visibleDates.map((d) => {
                  const slots = filteredSlots(d);
                  return (
                    <Grid key={d} size={{ xs: 12, md: viewMode === "week" ? 6 : 12, lg: viewMode === "week" ? 4 : 12 }}>
                      <Card variant="outlined" sx={{ borderStyle: "dashed" }}>
                        <CardContent>
                          <Typography sx={{ fontWeight: 800, mb: 1 }}>{new Date(`${d}T00:00:00`).toDateString()}</Typography>
                          {slots.length === 0 ? <Alert severity="info">No visible slots for selected filters.</Alert> : (
                            <Stack spacing={1}>
                              {slots.map((slot) => {
                                const time = slot.slotTime.slice(0, 5);
                                const end = slot.slotEndTime.slice(0, 5);
                                const isMorning = Number(time.slice(0, 2)) < 12;
                                const selected = selectedSlot?.date === d && selectedSlot.slot.slotTime === slot.slotTime;
                                return (
                                  <Box
                                    key={`${d}-${slot.slotTime}-${slot.slotEndTime}`}
                                    onClick={() => {
                                      setSelectedSlot({ date: d, slot });
                                      const appt = (appointmentsByDate[d] || []).find((a) => a.id === slot.appointmentId) || null;
                                      setSelectedAppointment(appt);
                                    }}
                                    sx={{
                                      p: 1.2,
                                      borderRadius: 2,
                                      border: "1px solid",
                                      borderColor: selected ? "primary.main" : "divider",
                                      bgcolor: selected ? "primary.50" : (isMorning ? "#f8fffc" : "#fffaf5"),
                                      cursor: "pointer",
                                    }}
                                  >
                                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                                      <Typography sx={{ fontWeight: 700 }}>{time} - {end}</Typography>
                                      <Chip size="small" label={slot.status.replace("_", " ")} color={slotColor(slot.status)} />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                      {slot.bookedCount} / {slot.maxPatientsPerSlot} booked {slot.status === "FULL" ? "• Full" : ""}
                                    </Typography>
                                  </Box>
                                );
                              })}
                            </Stack>
                          )}
                        </CardContent>
                      </Card>
                    </Grid>
                  );
                })}
              </Grid>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Availability Sessions</Typography>
              {availabilityRows.length === 0 ? <Alert severity="info">No sessions configured for this doctor.</Alert> : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Day</TableCell>
                      <TableCell>Session</TableCell>
                      <TableCell>Break</TableCell>
                      <TableCell>Duration</TableCell>
                      <TableCell>Capacity</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {availabilityRows.map((row) => (
                      <TableRow
                        key={row.id}
                        sx={{
                          bgcolor: row.active ? "transparent" : "action.hover",
                          "& td": { color: row.active ? "text.primary" : "text.secondary" },
                        }}
                      >
                        <TableCell>{row.dayOfWeek}</TableCell>
                        <TableCell>{row.startTime} - {row.endTime}</TableCell>
                        <TableCell>{row.breakStartTime && row.breakEndTime ? `${row.breakStartTime} - ${row.breakEndTime}` : "-"}</TableCell>
                        <TableCell>{row.consultationDurationMinutes} min</TableCell>
                        <TableCell>{row.maxPatientsPerSlot || 1}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={row.active ? "Active" : "Inactive"}
                            color={row.active ? "success" : "default"}
                            variant={row.active ? "filled" : "outlined"}
                          />
                        </TableCell>
                        <TableCell align="right">
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
              )}
            </CardContent>
          </Card>
        </Stack>
      </Grid>

      <Grid size={{ xs: 12, lg: 3 }}>
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Slot Details</Typography>
              {!selectedSlot ? <Alert severity="info">Select a slot to view details and actions.</Alert> : (
                <Stack spacing={1.2}>
                  <Typography sx={{ fontWeight: 700 }}>{selectedSlot.date} • {selectedSlot.slot.slotTime.slice(0, 5)} - {selectedSlot.slot.slotEndTime.slice(0, 5)}</Typography>
                  <Typography variant="body2">Doctor: {doctorOptions.find((d) => d.appUserId === effectiveDoctorId)?.displayName || effectiveDoctorId}</Typography>
                  <Typography variant="body2">Capacity: {selectedSlot.slot.maxPatientsPerSlot}</Typography>
                  <Typography variant="body2">Booked: {selectedSlot.slot.bookedCount}</Typography>
                  <Stack spacing={1}>
                    <Button size="small" onClick={() => navigate(`/appointments?doctorUserId=${effectiveDoctorId}`)}>Book appointment</Button>
                    <Button size="small" variant="outlined" onClick={() => setWaitlistReason(`No slot available at ${selectedSlot.slot.slotTime}`)}>Add to waitlist</Button>
                    <Button size="small" variant="outlined" onClick={() => setUnavailabilityForm((c) => ({ ...c, startAt: `${selectedSlot.date}T${selectedSlot.slot.slotTime}:00Z`, endAt: `${selectedSlot.date}T${selectedSlot.slot.slotEndTime}:00Z` }))}>Mark unavailable</Button>
                    <Button size="small" variant="outlined" onClick={() => setAvailabilityForm((c) => ({ ...c, dayOfWeek: DAYS[new Date(`${selectedSlot.date}T00:00:00`).getDay() === 0 ? 6 : new Date(`${selectedSlot.date}T00:00:00`).getDay() - 1], breakStartTime: selectedSlot.slot.slotTime, breakEndTime: selectedSlot.slot.slotEndTime }))}>Add break</Button>
                  </Stack>
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Appointment Details</Typography>
              {!selectedAppointment ? <Alert severity="info">Select a slot with booking to view patient/appointment.</Alert> : (
                <Stack spacing={1}>
                  <Typography sx={{ fontWeight: 700 }}>{selectedAppointment.patientName || selectedAppointment.patientNumber}</Typography>
                  <Typography variant="body2">Status: {selectedAppointment.status}</Typography>
                  <Typography variant="body2">Consultation: {selectedAppointment.consultationId ? "Started" : "Not started"}</Typography>
                  <Typography variant="body2">Billing: Not linked in this view</Typography>
                  <Stack spacing={1}>
                    <Button size="small" onClick={() => navigate("/appointments")}>Reschedule</Button>
                    <Button size="small" variant="outlined">Cancel</Button>
                    <Button size="small" variant="outlined" disabled={!selectedAppointment.consultationId}>Start consultation</Button>
                    <Button size="small" variant="outlined" onClick={() => navigate(`/patients/${selectedAppointment.patientId}`)}>View patient</Button>
                  </Stack>
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Waitlist</Typography>
              {!filters.WAITLIST ? <Alert severity="info">Waitlist hidden by filter.</Alert> : waitlist.length === 0 ? <Alert severity="info">No waitlist for selected doctor/date.</Alert> : (
                <Stack spacing={1}>
                  {waitlist.map((entry) => (
                    <Box key={entry.id} sx={{ p: 1, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                      <Typography sx={{ fontWeight: 700 }}>{entry.patientName || entry.patientNumber || entry.patientId}</Typography>
                      <Typography variant="caption" color="text.secondary">{entry.preferredStartTime || "Any time"} • {entry.reason || "No reason"}</Typography>
                      <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                        <Button size="small" variant="outlined" onClick={() => navigate(`/appointments?doctorUserId=${effectiveDoctorId}`)}>Book</Button>
                      </Stack>
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>Leave / Blocks</Typography>
              {unavailability.length === 0 ? <Alert severity="info">No leave/unavailable blocks.</Alert> : (
                <Stack spacing={1}>
                  {unavailability.map((item) => (
                    <Box key={item.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                      <Typography sx={{ fontWeight: 700 }}>{item.type}</Typography>
                      <Typography variant="caption" color="text.secondary">{new Date(item.startAt).toLocaleString()} - {new Date(item.endAt).toLocaleString()}</Typography>
                      {item.active ? <Button size="small" color="warning" onClick={() => auth.accessToken && auth.tenantId && void deactivateDoctorUnavailability(auth.accessToken, auth.tenantId, item.id).then(loadDynamic)}>Deactivate</Button> : null}
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Stack>
      </Grid>

      {error ? <Grid size={{ xs: 12 }}><Alert severity="error">{error}</Alert></Grid> : null}
      {info ? <Grid size={{ xs: 12 }}><Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert></Grid> : null}
    </Grid>
  );
}
