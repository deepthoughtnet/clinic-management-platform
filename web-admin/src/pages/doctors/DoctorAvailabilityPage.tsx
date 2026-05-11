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
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  createDoctorAvailability,
  deactivateDoctorAvailability,
  getClinicUsers,
  getDoctorAvailability,
  getDoctorSlots,
  updateDoctorAvailability,
  type ClinicUser,
  type DoctorAvailability,
  type DoctorAvailabilityInput,
  type DoctorAvailabilitySlot,
} from "../../api/clinicApi";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const EMPTY_FORM: DoctorAvailabilityInput = {
  dayOfWeek: "MONDAY",
  startTime: "09:00",
  endTime: "17:00",
  breakStartTime: null,
  breakEndTime: null,
  consultationDurationMinutes: 15,
  maxPatientsPerSlot: 1,
  active: true,
};

export default function DoctorAvailabilityPage() {
  const auth = useAuth();
  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [selectedDoctor, setSelectedDoctor] = React.useState("");
  const [rows, setRows] = React.useState<DoctorAvailability[]>([]);
  const [slots, setSlots] = React.useState<DoctorAvailabilitySlot[]>([]);
  const [date, setDate] = React.useState(new Date().toISOString().slice(0, 10));
  const [form, setForm] = React.useState<DoctorAvailabilityInput>(EMPTY_FORM);
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const doctorOptions = users.filter((user) => (user.membershipRole || "").toUpperCase() === "DOCTOR");
  const effectiveDoctorId = isDoctor ? (auth.appUserId || "") : selectedDoctor;

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setError(null);
    try {
      const [userRows, allAvailability] = await Promise.all([
        getClinicUsers(auth.accessToken, auth.tenantId),
        getDoctorAvailability(auth.accessToken, auth.tenantId),
      ]);
      setUsers(userRows);
      const preferredDoctor = isDoctor
        ? (auth.appUserId || "")
        : (selectedDoctor || userRows.find((item) => (item.membershipRole || "").toUpperCase() === "DOCTOR")?.appUserId || "");
      if (!isDoctor && !selectedDoctor) {
        setSelectedDoctor(preferredDoctor);
      }
      setRows(allAvailability.filter((item) => item.doctorUserId === preferredDoctor));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load doctor availability");
    }
  }, [auth.accessToken, auth.appUserId, auth.tenantId, isDoctor, selectedDoctor]);

  const loadSlots = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) {
      setSlots([]);
      return;
    }
    try {
      const slotRows = await getDoctorSlots(auth.accessToken, auth.tenantId, effectiveDoctorId, date);
      setSlots(slotRows);
    } catch {
      setSlots([]);
    }
  }, [auth.accessToken, auth.tenantId, effectiveDoctorId, date]);

  React.useEffect(() => { void load(); }, [load]);
  React.useEffect(() => { void loadSlots(); }, [loadSlots]);

  React.useEffect(() => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) {
      setRows([]);
      return;
    }
    void getDoctorAvailability(auth.accessToken, auth.tenantId)
      .then((all) => setRows(all.filter((item) => item.doctorUserId === effectiveDoctorId)))
      .catch(() => setRows([]));
  }, [auth.accessToken, auth.tenantId, effectiveDoctorId]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
  };

  const editRow = (row: DoctorAvailability) => {
    setEditingId(row.id);
    setForm({
      dayOfWeek: row.dayOfWeek,
      startTime: row.startTime,
      endTime: row.endTime,
      breakStartTime: row.breakStartTime,
      breakEndTime: row.breakEndTime,
      consultationDurationMinutes: row.consultationDurationMinutes,
      maxPatientsPerSlot: row.maxPatientsPerSlot,
      active: row.active,
    });
  };

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId || !effectiveDoctorId) return;
    setError(null);
    try {
      if (editingId) {
        await updateDoctorAvailability(auth.accessToken, auth.tenantId, editingId, form);
        setInfo("Availability updated");
      } else {
        await createDoctorAvailability(auth.accessToken, auth.tenantId, effectiveDoctorId, form);
        setInfo("Availability added");
      }
      resetForm();
      await load();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save availability");
    }
  };

  const deactivate = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setError(null);
    try {
      await deactivateDoctorAvailability(auth.accessToken, auth.tenantId, id);
      setInfo("Availability deactivated");
      await load();
      await loadSlots();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate availability");
    }
  };

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Doctor Availability</Typography>
        <Typography variant="body2" color="text.secondary">Manage calendar availability and review slot status for booking operations.</Typography>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}

      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2}>
            {!isDoctor ? (
              <Grid size={{ xs: 12, md: 5 }}>
                <FormControl fullWidth>
                  <InputLabel id="doctor-availability-select-label">Doctor</InputLabel>
                  <Select
                    labelId="doctor-availability-select-label"
                    label="Doctor"
                    value={selectedDoctor}
                    onChange={(event) => setSelectedDoctor(String(event.target.value))}
                  >
                    {doctorOptions.map((doctor) => (
                      <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                        {doctor.displayName || doctor.email || doctor.appUserId}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            ) : null}
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${slots.filter((item) => item.status === "AVAILABLE").length} available`} />
                <Chip size="small" label={`${slots.filter((item) => item.status === "BOOKED").length} booked`} color="warning" />
                <Chip size="small" label={`${slots.filter((item) => item.status === "UNAVAILABLE").length} unavailable`} variant="outlined" />
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <Card>
            <CardContent>
              {rows.length === 0 ? (
                <Alert severity="info">No active schedule rows. Manual appointment time is still supported.</Alert>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Day</TableCell>
                      <TableCell>Start</TableCell>
                      <TableCell>End</TableCell>
                      <TableCell>Break</TableCell>
                      <TableCell>Duration</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{row.dayOfWeek}</TableCell>
                        <TableCell>{row.startTime}</TableCell>
                        <TableCell>{row.endTime}</TableCell>
                        <TableCell>{row.breakStartTime && row.breakEndTime ? `${row.breakStartTime}-${row.breakEndTime}` : "-"}</TableCell>
                        <TableCell>{row.consultationDurationMinutes}m</TableCell>
                        <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} /></TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" onClick={() => editRow(row)}>Edit</Button>
                            <Button size="small" color="warning" onClick={() => void deactivate(row.id)}>Deactivate</Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>{editingId ? "Edit availability" : "Add availability"}</Typography>
                <FormControl fullWidth>
                  <InputLabel id="day-select-label">Day</InputLabel>
                  <Select labelId="day-select-label" label="Day" value={form.dayOfWeek} onChange={(e) => setForm((c) => ({ ...c, dayOfWeek: String(e.target.value) }))}>
                    {DAYS.map((day) => <MenuItem key={day} value={day}>{day}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField fullWidth type="time" label="Start time" value={form.startTime} onChange={(e) => setForm((c) => ({ ...c, startTime: e.target.value }))} InputLabelProps={{ shrink: true }} />
                <TextField fullWidth type="time" label="End time" value={form.endTime} onChange={(e) => setForm((c) => ({ ...c, endTime: e.target.value }))} InputLabelProps={{ shrink: true }} />
                <TextField fullWidth type="time" label="Break start (optional)" value={form.breakStartTime || ""} onChange={(e) => setForm((c) => ({ ...c, breakStartTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} />
                <TextField fullWidth type="time" label="Break end (optional)" value={form.breakEndTime || ""} onChange={(e) => setForm((c) => ({ ...c, breakEndTime: e.target.value || null }))} InputLabelProps={{ shrink: true }} />
                <TextField fullWidth type="number" label="Slot duration (minutes)" value={form.consultationDurationMinutes} onChange={(e) => setForm((c) => ({ ...c, consultationDurationMinutes: Number(e.target.value) }))} />
                <TextField fullWidth type="number" label="Max patients per slot" value={form.maxPatientsPerSlot || 1} onChange={(e) => setForm((c) => ({ ...c, maxPatientsPerSlot: Number(e.target.value) }))} />
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" onClick={() => void save()} disabled={!effectiveDoctorId}>Save</Button>
                  <Button variant="text" onClick={resetForm}>Reset</Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
