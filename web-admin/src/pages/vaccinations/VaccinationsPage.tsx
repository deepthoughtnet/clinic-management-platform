import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";

import { firstZodError, mapZodErrors, vaccinationMasterSchema, vaccinationRecordSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactTableFrame, compactChipSx } from "../../components/compact/CompactUi";
import RequiredLabel from "../../components/forms/RequiredLabel";
import {
  createVaccine,
  deactivateVaccine,
  getDueVaccinations,
  getClinicUsers,
  getOverdueVaccinations,
  getVaccines,
  recordPatientVaccination,
  searchPatients,
  type ClinicUser,
  type Patient,
  type PatientVaccination,
  type VaccineInput,
  type VaccineMaster,
} from "../../api/clinicApi";

type VaccineFormState = VaccineInput;

type VaccinationFormState = {
  patientId: string;
  vaccineId: string;
  doseNumber: string;
  givenDate: string;
  nextDueDate: string;
  batchNumber: string;
  notes: string;
  administeredByUserId: string;
  addToBill: boolean;
  billId: string;
  billItemUnitPrice: string;
};

function emptyVaccineForm(): VaccineFormState {
  return {
    vaccineName: "",
    description: "",
    ageGroup: "",
    recommendedGapDays: null,
    defaultPrice: null,
    active: true,
  };
}

function emptyVaccinationForm(): VaccinationFormState {
  return {
    patientId: "",
    vaccineId: "",
    doseNumber: "",
    givenDate: new Date().toISOString().slice(0, 10),
    nextDueDate: "",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    addToBill: false,
    billId: "",
    billItemUnitPrice: "",
  };
}

function statusColor(date: string | null | undefined) {
  if (!date) {
    return "default";
  }
  return new Date(date) < new Date(new Date().toISOString().slice(0, 10)) ? "error" : "warning";
}

export default function VaccinationsPage() {
  const auth = useAuth();
  const [vaccines, setVaccines] = React.useState<VaccineMaster[]>([]);
  const [dueRows, setDueRows] = React.useState<PatientVaccination[]>([]);
  const [overdueRows, setOverdueRows] = React.useState<PatientVaccination[]>([]);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientSearchResults, setPatientSearchResults] = React.useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [vaccineForm, setVaccineForm] = React.useState<VaccineFormState>(emptyVaccineForm());
  const [vaccinationForm, setVaccinationForm] = React.useState<VaccinationFormState>(emptyVaccinationForm());
  const [vaccineFieldErrors, setVaccineFieldErrors] = React.useState<Record<string, string>>({});
  const [vaccinationFieldErrors, setVaccinationFieldErrors] = React.useState<Record<string, string>>({});
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [vaccineRows, due, overdue, userRows, patientRows] = await Promise.all([
      getVaccines(auth.accessToken, auth.tenantId),
      getDueVaccinations(auth.accessToken, auth.tenantId),
      getOverdueVaccinations(auth.accessToken, auth.tenantId),
      getClinicUsers(auth.accessToken, auth.tenantId),
      searchPatients(auth.accessToken, auth.tenantId, { active: true }),
    ]);
    setVaccines(vaccineRows);
    setDueRows(due);
    setOverdueRows(overdue);
    setUsers(userRows);
    setPatients(patientRows);
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        await loadAll();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load vaccination data");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadAll]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientQuery.trim().length < 2) {
        setPatientSearchResults([]);
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
          setPatientSearchResults(rows);
        }
      } catch {
        if (!cancelled) {
          setPatientSearchResults([]);
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

  const saveVaccine = async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const parsed = vaccinationMasterSchema.safeParse({
      vaccineName: vaccineForm.vaccineName,
      description: vaccineForm.description,
      ageGroup: vaccineForm.ageGroup,
      recommendedGapDays: vaccineForm.recommendedGapDays,
      defaultPrice: vaccineForm.defaultPrice,
      active: vaccineForm.active,
    });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setVaccineFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = ["vaccineName", "defaultPrice", "recommendedGapDays", "ageGroup", "description"].find((field) => errors[field]);
        document.getElementById(firstField ? `vaccine-${firstField}` : "vaccine-vaccineName")?.focus();
      }, 0);
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setVaccineFieldErrors({});
      try {
        await createVaccine(auth.accessToken, auth.tenantId, {
          vaccineName: parsed.data.vaccineName,
          description: parsed.data.description ?? null,
          ageGroup: parsed.data.ageGroup ?? null,
          recommendedGapDays: parsed.data.recommendedGapDays ?? null,
          defaultPrice: parsed.data.defaultPrice ?? null,
          active: parsed.data.active,
        });
      setVaccineForm(emptyVaccineForm());
      await loadAll();
      setSuccess("Vaccine saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save vaccine");
    } finally {
      setSaving(false);
    }
  };

  const recordVaccination = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedPatient) {
      return;
    }
    const parsed = vaccinationRecordSchema.safeParse({
      patientId: selectedPatient.id,
      vaccineId: vaccinationForm.vaccineId,
      doseNumber: vaccinationForm.doseNumber.trim() ? Number(vaccinationForm.doseNumber) : null,
      givenDate: vaccinationForm.givenDate || null,
      nextDueDate: vaccinationForm.nextDueDate || null,
      batchNumber: vaccinationForm.batchNumber,
      notes: vaccinationForm.notes,
      administeredByUserId: vaccinationForm.administeredByUserId || null,
      billId: vaccinationForm.addToBill && vaccinationForm.billId.trim() ? vaccinationForm.billId.trim() : null,
      addToBill: vaccinationForm.addToBill,
      billItemUnitPrice: vaccinationForm.billItemUnitPrice.trim() ? Number(vaccinationForm.billItemUnitPrice) : null,
    });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setVaccinationFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => {
        const firstField = ["vaccineId", "givenDate", "patientId", "billItemUnitPrice", "batchNumber", "notes"].find((field) => errors[field]);
        document.getElementById(firstField ? `vaccination-${firstField}` : "vaccination-vaccineId")?.focus();
      }, 0);
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    setVaccinationFieldErrors({});
    try {
      await recordPatientVaccination(auth.accessToken, auth.tenantId, selectedPatient.id, {
        vaccineId: parsed.data.vaccineId,
        doseNumber: parsed.data.doseNumber ?? null,
        givenDate: parsed.data.givenDate ?? null,
        nextDueDate: parsed.data.nextDueDate ?? null,
        batchNumber: parsed.data.batchNumber ?? null,
        notes: parsed.data.notes ?? null,
        administeredByUserId: parsed.data.administeredByUserId ?? null,
        billId: parsed.data.billId ?? null,
        addToBill: parsed.data.addToBill ?? false,
        billItemUnitPrice: parsed.data.billItemUnitPrice ?? null,
      });
      setVaccinationForm(emptyVaccinationForm());
      setPatientQuery("");
      setSelectedPatient(null);
      await loadAll();
      setSuccess("Vaccination recorded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record vaccination");
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deactivateVaccine(auth.accessToken, auth.tenantId, id);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate vaccine");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Vaccinations
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Vaccine master data, recording, and due/overdue follow-up tracking.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void loadAll()}>
          Refresh
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.25}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Record vaccination
                </Typography>
                <TextField size="small" label="Search patient" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} helperText="Search by patient number, mobile, or name" />
                {patientSearchResults.length > 0 && !selectedPatient ? (
                  <Card variant="outlined">
                    <List dense disablePadding>
                      {patientSearchResults.map((patient) => (
                        <ListItemButton key={patient.id} onClick={() => setSelectedPatient(patient)}>
                          <ListItemText primary={`${patient.firstName} ${patient.lastName}`} secondary={`${patient.patientNumber} • ${patient.mobile}`} />
                        </ListItemButton>
                      ))}
                    </List>
                  </Card>
                ) : null}
                {selectedPatient ? (
                  <Chip
                    label={`${selectedPatient.firstName} ${selectedPatient.lastName} • ${selectedPatient.patientNumber}`}
                    onDelete={() => setSelectedPatient(null)}
                    sx={compactChipSx}
                  />
                ) : null}

                <FormControl fullWidth size="small">
                  <InputLabel id="vaccination-vaccine-label"><RequiredLabel text="Vaccine" required /></InputLabel>
                  <Select
                    id="vaccination-vaccineId"
                    labelId="vaccination-vaccine-label"
                    label="Vaccine"
                    value={vaccinationForm.vaccineId}
                    onChange={(e) => setVaccinationForm((current) => ({ ...current, vaccineId: String(e.target.value) }))}
                    error={Boolean(vaccinationFieldErrors.vaccineId)}
                  >
                    {vaccines.map((vaccine) => (
                      <MenuItem key={vaccine.id} value={vaccine.id}>
                        {vaccine.vaccineName}
                      </MenuItem>
                    ))}
                  </Select>
                  {vaccinationFieldErrors.vaccineId ? <Typography variant="caption" color="error">{vaccinationFieldErrors.vaccineId}</Typography> : null}
                </FormControl>

                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-doseNumber" label="Dose number" value={vaccinationForm.doseNumber} onChange={(e) => setVaccinationForm((current) => ({ ...current, doseNumber: e.target.value }))} error={Boolean(vaccinationFieldErrors.doseNumber)} helperText={vaccinationFieldErrors.doseNumber || "Optional positive whole number."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-givenDate" label={<RequiredLabel text="Given date" required />} type="date" value={vaccinationForm.givenDate} onChange={(e) => setVaccinationForm((current) => ({ ...current, givenDate: e.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(vaccinationFieldErrors.givenDate)} helperText={vaccinationFieldErrors.givenDate || "Required."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-nextDueDate" label="Next due date" type="date" value={vaccinationForm.nextDueDate} onChange={(e) => setVaccinationForm((current) => ({ ...current, nextDueDate: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(vaccinationFieldErrors.nextDueDate)} helperText={vaccinationFieldErrors.nextDueDate} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-batchNumber" label="Batch number" value={vaccinationForm.batchNumber} onChange={(e) => setVaccinationForm((current) => ({ ...current, batchNumber: e.target.value }))} error={Boolean(vaccinationFieldErrors.batchNumber)} helperText={vaccinationFieldErrors.batchNumber || "Optional, max 60 characters."} />
                  </Grid>
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccination-notes" label="Notes" value={vaccinationForm.notes} onChange={(e) => setVaccinationForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} error={Boolean(vaccinationFieldErrors.notes)} helperText={vaccinationFieldErrors.notes || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="vaccination-admin-label">Administered by</InputLabel>
                      <Select
                        labelId="vaccination-admin-label"
                        label="Administered by"
                        value={vaccinationForm.administeredByUserId}
                        onChange={(e) => setVaccinationForm((current) => ({ ...current, administeredByUserId: String(e.target.value) }))}
                      >
                        <MenuItem value="">Current user</MenuItem>
                        {users.map((user) => (
                          <MenuItem key={user.appUserId} value={user.appUserId}>
                            {user.displayName || user.email || user.appUserId}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-billId" label="Bill ID" value={vaccinationForm.billId} onChange={(e) => setVaccinationForm((current) => ({ ...current, billId: e.target.value }))} disabled={!vaccinationForm.addToBill} error={Boolean(vaccinationFieldErrors.billId)} helperText={vaccinationForm.addToBill ? (vaccinationFieldErrors.billId || "Optional when bill linking is enabled.") : ""} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccination-billItemUnitPrice" label={<RequiredLabel text="Bill item unit price" required={vaccinationForm.addToBill} />} value={vaccinationForm.billItemUnitPrice} onChange={(e) => setVaccinationForm((current) => ({ ...current, billItemUnitPrice: e.target.value }))} disabled={!vaccinationForm.addToBill} error={Boolean(vaccinationFieldErrors.billItemUnitPrice)} helperText={vaccinationForm.addToBill ? (vaccinationFieldErrors.billItemUnitPrice || "Required when adding to bill.") : ""} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={vaccinationForm.addToBill}
                          onChange={(e) => setVaccinationForm((current) => ({ ...current, addToBill: e.target.checked }))}
                        />
                      }
                      label="Add to bill"
                    />
                  </Grid>
                </Grid>

                <Button variant="contained" size="small" onClick={() => void recordVaccination()} disabled={saving}>
                  {saving ? "Saving..." : "Record Vaccination"}
                </Button>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.25}>
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Vaccine master
                  </Typography>
                  <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => setVaccineForm(emptyVaccineForm())}>
                    Reset
                  </Button>
                </Box>
                <Grid container spacing={1}>
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccine-vaccineName" label={<RequiredLabel text="Vaccine name" required />} value={vaccineForm.vaccineName} onChange={(e) => setVaccineForm((current) => ({ ...current, vaccineName: e.target.value }))} required error={Boolean(vaccineFieldErrors.vaccineName)} helperText={vaccineFieldErrors.vaccineName || "Required, max 100 characters."} />
                  </Grid>
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccine-description" label="Description" value={vaccineForm.description ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, description: e.target.value }))} multiline minRows={2} error={Boolean(vaccineFieldErrors.description)} helperText={vaccineFieldErrors.description || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-ageGroup" label="Age group" value={vaccineForm.ageGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, ageGroup: e.target.value }))} error={Boolean(vaccineFieldErrors.ageGroup)} helperText={vaccineFieldErrors.ageGroup || "Optional, max 60 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField
                      size="small"
                      fullWidth
                      id="vaccine-recommendedGapDays"
                      label="Gap days"
                      value={vaccineForm.recommendedGapDays ?? ""}
                      onChange={(e) => setVaccineForm((current) => ({ ...current, recommendedGapDays: e.target.value ? Number(e.target.value) : null }))}
                      error={Boolean(vaccineFieldErrors.recommendedGapDays)}
                      helperText={vaccineFieldErrors.recommendedGapDays || "Optional, zero or greater."}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField
                      size="small"
                      fullWidth
                      id="vaccine-defaultPrice"
                      label="Default price"
                      value={vaccineForm.defaultPrice ?? ""}
                      onChange={(e) => setVaccineForm((current) => ({ ...current, defaultPrice: e.target.value ? Number(e.target.value) : null }))}
                      error={Boolean(vaccineFieldErrors.defaultPrice)}
                      helperText={vaccineFieldErrors.defaultPrice || "Required, zero or greater, up to 2 decimals."}
                    />
                  </Grid>
                  <Grid size={12}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={vaccineForm.active}
                          onChange={(e) => setVaccineForm((current) => ({ ...current, active: e.target.checked }))}
                        />
                      }
                      label={vaccineForm.active ? "Active" : "Inactive"}
                    />
                  </Grid>
                </Grid>
                <Button variant="contained" size="small" onClick={() => void saveVaccine()} disabled={saving}>
                  Save Vaccine
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Due vaccinations
                  </Typography>
                  {loading ? (
                    <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                      <CircularProgress />
                    </Box>
                  ) : dueRows.length === 0 ? (
                    <CompactEmptyState title="No due vaccinations found." subtitle="Patients with upcoming vaccine follow-up will appear here." />
                  ) : (
                    <CompactTableFrame maxHeight={360}>
                      <Table size="small" stickyHeader sx={{ minWidth: 620 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ py: 0.7 }}>Patient</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Vaccine</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Given</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Next due</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {dueRows.map((row) => (
                          <TableRow key={row.id}>
                            <TableCell>{row.patientName || row.patientNumber || row.patientId}</TableCell>
                            <TableCell>{row.vaccineName}</TableCell>
                            <TableCell>{row.givenDate}</TableCell>
                            <TableCell>
                              <Chip size="small" label={row.nextDueDate || "-"} color={statusColor(row.nextDueDate)} />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                      </Table>
                    </CompactTableFrame>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Overdue vaccinations
                  </Typography>
                  {loading ? (
                    <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                      <CircularProgress />
                    </Box>
                  ) : overdueRows.length === 0 ? (
                    <CompactEmptyState title="No overdue vaccinations found." subtitle="Overdue follow-up entries will appear here when patients miss a due date." />
                  ) : (
                    <CompactTableFrame maxHeight={360}>
                      <Table size="small" stickyHeader sx={{ minWidth: 620 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ py: 0.7 }}>Patient</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Vaccine</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Given</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Next due</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {overdueRows.map((row) => (
                          <TableRow key={row.id}>
                            <TableCell>{row.patientName || row.patientNumber || row.patientId}</TableCell>
                            <TableCell>{row.vaccineName}</TableCell>
                            <TableCell>{row.givenDate}</TableCell>
                            <TableCell>
                              <Chip size="small" label={row.nextDueDate || "-"} color="error" />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                      </Table>
                    </CompactTableFrame>
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 1.25 }}>
                <Stack spacing={1.25}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Vaccine list
                  </Typography>
                  {vaccines.length === 0 ? (
                    <CompactEmptyState title="No vaccines were found." subtitle="Create a vaccine in the master list to start recording doses." />
                  ) : (
                    <CompactTableFrame maxHeight={420}>
                      <Table size="small" stickyHeader sx={{ minWidth: 760 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ py: 0.7 }}>Name</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Age group</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Gap days</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Default price</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Status</TableCell>
                          <TableCell sx={{ py: 0.7 }} align="right">Actions</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {vaccines.map((vaccine) => (
                          <TableRow key={vaccine.id}>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {vaccine.vaccineName}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {vaccine.description || "No description"}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{vaccine.ageGroup || "-"}</TableCell>
                            <TableCell>{vaccine.recommendedGapDays ?? "-"}</TableCell>
                            <TableCell>{vaccine.defaultPrice?.toFixed(2) || "-"}</TableCell>
                            <TableCell>
                              <Chip size="small" label={vaccine.active ? "Active" : "Inactive"} color={vaccine.active ? "success" : "default"} />
                            </TableCell>
                            <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                              <Button size="small" sx={{ whiteSpace: "nowrap" }} onClick={() => setVaccineForm({
                                vaccineName: vaccine.vaccineName,
                                description: vaccine.description || "",
                                ageGroup: vaccine.ageGroup || "",
                                recommendedGapDays: vaccine.recommendedGapDays,
                                defaultPrice: vaccine.defaultPrice,
                                active: vaccine.active,
                              })}>
                                Edit
                              </Button>
                              <Button size="small" sx={{ whiteSpace: "nowrap" }} onClick={() => void deactivate(vaccine.id)} disabled={!vaccine.active || saving}>
                                Deactivate
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                      </Table>
                    </CompactTableFrame>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </Stack>
  );
}
