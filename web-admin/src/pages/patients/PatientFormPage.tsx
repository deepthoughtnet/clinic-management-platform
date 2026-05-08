import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  FormControlLabel,
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
  createPatient,
  deactivatePatient,
  getPatient,
  searchPatients,
  updatePatient,
  type Patient,
  type PatientGender,
  type PatientInput,
} from "../../api/clinicApi";

type FormState = {
  firstName: string;
  lastName: string;
  gender: PatientGender;
  dateOfBirth: string;
  ageYears: number | null;
  mobile: string;
  email: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  emergencyContactName: string;
  emergencyContactMobile: string;
  bloodGroup: string;
  allergies: string;
  existingConditions: string;
  longTermMedications: string;
  surgicalHistory: string;
  notes: string;
  active: boolean;
};

const emptyForm = (): FormState => ({
  firstName: "",
  lastName: "",
  gender: "UNKNOWN",
  dateOfBirth: "",
  ageYears: null,
  mobile: "",
  email: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  state: "",
  country: "",
  postalCode: "",
  emergencyContactName: "",
  emergencyContactMobile: "",
  bloodGroup: "",
  allergies: "",
  existingConditions: "",
  longTermMedications: "",
  surgicalHistory: "",
  notes: "",
  active: true,
});

function patientToForm(patient: PatientInput): FormState {
  return {
    ...patient,
    lastName: patient.lastName || "",
    dateOfBirth: patient.dateOfBirth || "",
    ageYears: patient.ageYears ?? null,
    email: patient.email || "",
    addressLine1: patient.addressLine1 || "",
    addressLine2: patient.addressLine2 || "",
    city: patient.city || "",
    state: patient.state || "",
    country: patient.country || "",
    postalCode: patient.postalCode || "",
    emergencyContactName: patient.emergencyContactName || "",
    emergencyContactMobile: patient.emergencyContactMobile || "",
    bloodGroup: patient.bloodGroup || "",
    allergies: patient.allergies || "",
    existingConditions: patient.existingConditions || "",
    longTermMedications: patient.longTermMedications || "",
    surgicalHistory: patient.surgicalHistory || "",
    notes: patient.notes || "",
  };
}

function formToInput(form: FormState): PatientInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    gender: form.gender,
    dateOfBirth: form.dateOfBirth || null,
    ageYears: form.ageYears === null || form.ageYears === undefined || Number.isNaN(Number(form.ageYears)) ? null : Number(form.ageYears),
    mobile: form.mobile.trim(),
    email: form.email.trim() || null,
    addressLine1: form.addressLine1.trim() || null,
    addressLine2: form.addressLine2.trim() || null,
    city: form.city.trim() || null,
    state: form.state.trim() || null,
    country: form.country.trim() || null,
    postalCode: form.postalCode.trim() || null,
    emergencyContactName: form.emergencyContactName.trim() || null,
    emergencyContactMobile: form.emergencyContactMobile.trim() || null,
    bloodGroup: form.bloodGroup.trim() || null,
    allergies: form.allergies.trim() || null,
    existingConditions: form.existingConditions.trim() || null,
    longTermMedications: form.longTermMedications.trim() || null,
    surgicalHistory: form.surgicalHistory.trim() || null,
    notes: form.notes.trim() || null,
    active: form.active,
  };
}

const genderOptions: Array<{ value: PatientGender; label: string }> = [
  { value: "MALE", label: "Male" },
  { value: "FEMALE", label: "Female" },
  { value: "OTHER", label: "Other" },
  { value: "UNKNOWN", label: "Unknown" },
];

const bloodGroups = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"];
const commonAllergies = ["Penicillin", "Sulfa", "NSAIDs", "Dust", "Pollen", "Peanuts", "Shellfish", "Latex"];
const commonConditions = ["Diabetes", "Hypertension", "Asthma", "Thyroid", "CAD", "CKD", "COPD"];
const commonMedications = ["Metformin", "Amlodipine", "Telmisartan", "Insulin", "Thyroxine", "Atorvastatin"];

function toCsv(values: string[]) {
  return values.map((value) => value.trim()).filter(Boolean).join(", ");
}

function fromCsv(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function calculateAge(dateOfBirth: string) {
  if (!dateOfBirth) return null;
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const monthDiff = today.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age >= 0 && age <= 130 ? age : null;
}

function approximateDobFromAge(age: number | null) {
  if (age === null || Number.isNaN(age) || age < 0 || age > 130) return "";
  const year = new Date().getFullYear() - age;
  return `${year}-01-01`;
}

function mobileDigits(value: string) {
  return value.replace(/[\s-]/g, "").trim();
}

function isValidMobile(value: string) {
  return /^\+?[0-9]{7,15}$/.test(mobileDigits(value));
}

function friendlyError(err: unknown) {
  const message = err instanceof Error ? err.message : "Unable to save patient. Please check the details and try again.";
  if (message.includes("mobile is required")) return "Mobile number is required.";
  if (message.includes("mobile must be")) return "Enter a valid mobile number.";
  if (message.includes("same mobile")) return "Possible duplicate patient found. Open the existing patient or confirm that this is a new patient.";
  if (message.includes("firstName")) return "First name is required.";
  return "Unable to save patient. Please check the details and try again.";
}

function patientLabel(patient: Patient) {
  const ageGender = [patient.ageYears !== null ? `${patient.ageYears}y` : null, patient.gender].filter(Boolean).join(" / ");
  return `${patient.firstName} ${patient.lastName || ""}`.trim() + (ageGender ? ` • ${ageGender}` : "");
}

export default function PatientFormPage({ mode }: { mode: "create" | "edit" }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams();
  const id = params.id || "";
  const canEdit = mode === "create" ? auth.hasPermission("patient.create") : auth.hasPermission("patient.update");

  const [form, setForm] = React.useState<FormState>(emptyForm());
  const [loading, setLoading] = React.useState(mode === "edit");
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [duplicates, setDuplicates] = React.useState<Patient[]>([]);
  const [checkingDuplicates, setCheckingDuplicates] = React.useState(false);
  const [continueNew, setContinueNew] = React.useState(false);
  const mobileInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (mode === "create") {
      window.setTimeout(() => mobileInputRef.current?.focus(), 50);
    }
  }, [mode]);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (mode !== "edit" || !auth.accessToken || !auth.tenantId || !id) {
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        const detail = await getPatient(auth.accessToken, auth.tenantId, id);
        if (!cancelled) {
          setForm(patientToForm(detail.patient));
        }
      } catch (err) {
        if (!cancelled) {
          setError(friendlyError(err));
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
  }, [auth.accessToken, auth.tenantId, id, mode]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      const mobile = mobileDigits(form.mobile);
      const name = `${form.firstName} ${form.lastName}`.trim();
      if (mode !== "create" || continueNew || !auth.accessToken || !auth.tenantId) {
        setDuplicates([]);
        setCheckingDuplicates(false);
        return;
      }
      if (mobile.length < 6 && name.length < 2) {
        setDuplicates([]);
        setCheckingDuplicates(false);
        return;
      }
      setCheckingDuplicates(true);
      try {
        const rows = await searchPatients(auth.accessToken, auth.tenantId, mobile.length >= 6 ? { mobile, active: true } : { name, active: true });
        if (!cancelled) {
          setDuplicates(rows.slice(0, 5));
        }
      } catch {
        if (!cancelled) {
          setDuplicates([]);
        }
      } finally {
        if (!cancelled) {
          setCheckingDuplicates(false);
        }
      }
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [auth.accessToken, auth.tenantId, continueNew, form.firstName, form.lastName, form.mobile, mode]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const updateField = (field: Exclude<keyof FormState, "ageYears" | "active" | "gender" | "dateOfBirth" | "mobile">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setForm((current) => ({ ...current, [field]: event.target.value }));
    };

  const updateMobile = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setContinueNew(false);
    setForm((current) => ({ ...current, mobile: event.target.value.replace(/[^0-9+\s-]/g, "") }));
  };

  const updateDateOfBirth = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const dateOfBirth = event.target.value;
    setForm((current) => ({ ...current, dateOfBirth, ageYears: calculateAge(dateOfBirth) }));
  };

  const updateAge = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const value = event.target.value === "" ? null : Number(event.target.value);
    setForm((current) => ({ ...current, ageYears: value, dateOfBirth: approximateDobFromAge(value) }));
  };

  const validateQuickFields = () => {
    if (!form.mobile.trim()) return "Mobile number is required.";
    if (!isValidMobile(form.mobile)) return "Enter a valid mobile number.";
    if (!form.firstName.trim()) return "First name is required.";
    return null;
  };

  const savePatient = async (next: "detail" | "appointment" | "queue" = "detail") => {
    if (!auth.accessToken || !auth.tenantId) return;
    const validationMessage = validateQuickFields();
    if (validationMessage) {
      setError(validationMessage);
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const payload = formToInput({ ...form, mobile: mobileDigits(form.mobile) });
      const saved = mode === "create"
        ? await createPatient(auth.accessToken, auth.tenantId, payload)
        : await updatePatient(auth.accessToken, auth.tenantId, id, payload);
      setSuccess("Patient registered successfully.");
      if (next === "appointment") {
        navigate(`/appointments?patientId=${saved.id}`, { state: { patient: saved } });
      } else if (next === "queue") {
        navigate(`/appointments?patientId=${saved.id}&type=WALK_IN`, { state: { patient: saved } });
      } else {
        navigate(`/patients/${saved.id}`);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setSaving(false);
    }
  };

  const onSave = async (event: React.FormEvent) => {
    event.preventDefault();
    await savePatient("detail");
  };

  const onDeactivate = async () => {
    if (!auth.accessToken || !auth.tenantId || !id) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deactivatePatient(auth.accessToken, auth.tenantId, id);
      navigate(`/patients/${id}`);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setSaving(false);
    }
  };

  const disabled = !canEdit || saving;

  return (
    <Stack spacing={2.5} component="form" onSubmit={onSave}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 0.5 }}>
            {mode === "create" ? "New Patient" : "Edit Patient"}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Fast family registration. Start with mobile number to check for existing family members.
          </Typography>
        </Box>
        <Button type="button" variant="outlined" onClick={() => navigate("/patients")}>Back to Patients</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {!canEdit ? <Alert severity="info">You have read-only access to this patient record.</Alert> : null}

      <Card sx={{ border: "1px solid", borderColor: "primary.light", boxShadow: "0 18px 45px rgba(15, 23, 42, 0.08)" }}>
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Stack spacing={2.5}>
              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Quick Registration</Typography>
                  <Typography variant="body2" color="text.secondary">Required for walk-in intake: mobile, name, gender, and age or DOB.</Typography>
                </Box>
                <Chip color="primary" label="Mobile-first lookup" />
              </Box>

              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField
                    fullWidth
                    required
                    inputRef={mobileInputRef}
                    label="Mobile number"
                    value={form.mobile}
                    onChange={updateMobile}
                    disabled={disabled}
                    error={Boolean(form.mobile) && !isValidMobile(form.mobile)}
                    helperText="Primary lookup and duplicate check"
                    inputProps={{ inputMode: "tel", autoComplete: "tel" }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth required label="First name" value={form.firstName} onChange={updateField("firstName")} disabled={disabled} inputProps={{ autoComplete: "given-name" }} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="Last name" value={form.lastName} onChange={updateField("lastName")} disabled={disabled} inputProps={{ autoComplete: "family-name" }} />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth>
                    <InputLabel id="patient-gender-label">Gender</InputLabel>
                    <Select
                      labelId="patient-gender-label"
                      label="Gender"
                      value={form.gender}
                      onChange={(event) => setForm((current) => ({ ...current, gender: event.target.value as PatientGender }))}
                      disabled={disabled}
                    >
                      {genderOptions.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField fullWidth type="number" label="Age" value={form.ageYears ?? ""} onChange={updateAge} disabled={disabled} inputProps={{ min: 0, max: 130 }} helperText="DOB auto-filled approximately" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField fullWidth type="date" label="Date of birth" value={form.dateOfBirth} onChange={updateDateOfBirth} disabled={disabled} InputLabelProps={{ shrink: true }} helperText="Age updates automatically" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }} sx={{ display: "flex", alignItems: "center", justifyContent: { xs: "stretch", md: "flex-end" }, gap: 1 }}>
                  <Button type="submit" fullWidth variant="contained" size="large" disabled={disabled || checkingDuplicates}>
                    {saving ? "Saving..." : mode === "create" ? "Register" : "Save"}
                  </Button>
                </Grid>
              </Grid>

              {checkingDuplicates ? <Alert severity="info">Checking for existing patients...</Alert> : null}
              {duplicates.length > 0 ? (
                <Card variant="outlined" sx={{ bgcolor: "warning.50", borderColor: "warning.light" }}>
                  <CardContent sx={{ py: 1.5 }}>
                    <Stack spacing={1.25}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>Possible existing patients found</Typography>
                          <Typography variant="body2" color="text.secondary">Open an existing record if this is a repeat visit.</Typography>
                        </Box>
                        <Button type="button" size="small" onClick={() => setContinueNew(true)}>Continue creating new patient</Button>
                      </Box>
                      <List dense disablePadding>
                        {duplicates.map((patient) => (
                          <ListItemButton key={patient.id} divider onClick={() => navigate(`/patients/${patient.id}`)}>
                            <ListItemText
                              primary={`${patientLabel(patient)} • ${patient.patientNumber}`}
                              secondary={`Mobile ${patient.mobile}`}
                            />
                            <Button type="button" size="small" variant="outlined">Open</Button>
                          </ListItemButton>
                        ))}
                      </List>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {mode === "create" ? (
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <Button type="button" variant="outlined" disabled={disabled} onClick={() => void savePatient("appointment")}>Save & Create Appointment</Button>
                  <Button type="button" variant="outlined" disabled={disabled} onClick={() => void savePatient("queue")}>Save & Add to Queue</Button>
                </Stack>
              ) : null}
            </Stack>
          )}
        </CardContent>
      </Card>

      {!loading ? (
        <Stack spacing={1.5}>
          <Accordion defaultExpanded={false} disableGutters>
            <AccordionSummary expandIcon={<span>⌄</span>}>
              <Box>
                <Typography sx={{ fontWeight: 800 }}>Contact & Address</Typography>
                <Typography variant="body2" color="text.secondary">Optional details for reminders, billing, and emergency contact.</Typography>
              </Box>
            </AccordionSummary>
            <AccordionDetails>
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={form.email} onChange={updateField("email")} disabled={disabled} inputProps={{ autoComplete: "email" }} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Emergency contact mobile" value={form.emergencyContactMobile} onChange={updateField("emergencyContactMobile")} disabled={disabled} inputProps={{ inputMode: "tel" }} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Emergency contact name" value={form.emergencyContactName} onChange={updateField("emergencyContactName")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Address" value={form.addressLine1} onChange={updateField("addressLine1")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Address line 2" value={form.addressLine2} onChange={updateField("addressLine2")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="City" value={form.city} onChange={updateField("city")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="State" value={form.state} onChange={updateField("state")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Country" value={form.country} onChange={updateField("country")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Postal code" value={form.postalCode} onChange={updateField("postalCode")} disabled={disabled} /></Grid>
              </Grid>
            </AccordionDetails>
          </Accordion>

          <Accordion defaultExpanded={false} disableGutters>
            <AccordionSummary expandIcon={<span>⌄</span>}>
              <Box>
                <Typography sx={{ fontWeight: 800 }}>Clinical Profile</Typography>
                <Typography variant="body2" color="text.secondary">Capture only what is clinically useful today. Allergies stay visible in consultation.</Typography>
              </Box>
            </AccordionSummary>
            <AccordionDetails>
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth>
                    <InputLabel id="patient-blood-group-label">Blood group</InputLabel>
                    <Select labelId="patient-blood-group-label" label="Blood group" value={form.bloodGroup} onChange={(event) => setForm((current) => ({ ...current, bloodGroup: event.target.value }))} disabled={disabled}>
                      <MenuItem value="">Unknown</MenuItem>
                      {bloodGroups.map((group) => <MenuItem key={group} value={group}>{group}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 9 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonAllergies}
                    value={fromCsv(form.allergies)}
                    onChange={(_, values) => setForm((current) => ({ ...current, allergies: toCsv(values) }))}
                    disabled={disabled}
                    renderTags={(value, getTagProps) => value.map((option, index) => <Chip color="error" variant="outlined" label={option} {...getTagProps({ index })} key={option} />)}
                    renderInput={(params) => <TextField {...params} label="Allergies" helperText="Use chips for fast capture. Add custom values if needed." />}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonConditions}
                    value={fromCsv(form.existingConditions)}
                    onChange={(_, values) => setForm((current) => ({ ...current, existingConditions: toCsv(values) }))}
                    disabled={disabled}
                    renderInput={(params) => <TextField {...params} label="Chronic conditions" />}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonMedications}
                    value={fromCsv(form.longTermMedications)}
                    onChange={(_, values) => setForm((current) => ({ ...current, longTermMedications: toCsv(values) }))}
                    disabled={disabled}
                    renderInput={(params) => <TextField {...params} label="Long-term medications" />}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth multiline minRows={2} label="Surgeries / history" value={form.surgicalHistory} onChange={updateField("surgicalHistory")} disabled={disabled} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth multiline minRows={2} label="Notes" value={form.notes} onChange={updateField("notes")} disabled={disabled} /></Grid>
              </Grid>
            </AccordionDetails>
          </Accordion>
        </Stack>
      ) : null}

      {!loading ? (
        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }} justifyContent="space-between">
              <FormControlLabel
                control={<Switch checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} disabled={disabled} />}
                label={form.active ? "Active patient" : "Inactive patient"}
              />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                {mode === "edit" ? (
                  <Button type="button" variant="outlined" color="error" onClick={() => void onDeactivate()} disabled={disabled || !form.active}>Deactivate</Button>
                ) : null}
                <Button type="submit" variant="contained" disabled={disabled}>{saving ? "Saving..." : "Save Patient"}</Button>
              </Stack>
            </Stack>
            <Divider sx={{ mt: 1.5 }} />
            <Typography variant="caption" color="text.secondary">Mobile stays searchable across family members; duplicate warnings are advisory, not blocking.</Typography>
          </CardContent>
        </Card>
      ) : null}
    </Stack>
  );
}
