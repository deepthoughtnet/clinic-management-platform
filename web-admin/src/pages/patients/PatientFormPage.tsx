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
  FormHelperText,
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
import { mapZodErrors, normalizeIndianMobileInput, patientQuickRegisterSchema, patientRegistrationSchema } from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import {
  createPatient,
  deactivatePatient,
  getPatient,
  updatePatient,
  type Patient,
  type PatientGender,
  type PatientInput,
} from "../../api/clinicApi";
import {
  approximateDobFromAge,
  calculateAgeFromDob,
  patientDisplayName,
  patientIdentitySummary,
  useDuplicatePatientLookup,
} from "../../components/patients/patientQuickRegister";

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

function patientToForm(patient: Patient): FormState {
  return {
    firstName: patient.firstName,
    lastName: patient.lastName || "",
    gender: patient.gender,
    dateOfBirth: patient.dateOfBirth || "",
    ageYears: patient.ageYears ?? null,
    mobile: patient.mobile,
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
    active: patient.active,
  };
}

function formToInput(form: FormState): PatientInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    gender: form.gender,
    dateOfBirth: form.dateOfBirth || null,
    ageYears: form.ageYears === null || form.ageYears === undefined || Number.isNaN(Number(form.ageYears)) ? null : Number(form.ageYears),
    mobile: normalizeIndianMobileInput(form.mobile) as string,
    email: form.email.trim() || null,
    addressLine1: form.addressLine1.trim() || null,
    addressLine2: form.addressLine2.trim() || null,
    city: form.city.trim() || null,
    state: form.state.trim() || null,
    country: form.country.trim() || null,
    postalCode: form.postalCode.trim() || null,
    emergencyContactName: form.emergencyContactName.trim() || null,
    emergencyContactMobile: form.emergencyContactMobile.trim() ? (normalizeIndianMobileInput(form.emergencyContactMobile) as string) : null,
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

function normalizeCsvValues(values: string[]) {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const value of values) {
    const trimmed = value.trim().replace(/\s+/g, " ");
    if (!trimmed) continue;
    const key = trimmed.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    normalized.push(trimmed);
  }
  return normalized;
}

function mergeAllergiesValue(existing: string, pending: string, replaceSingleExisting = false) {
  const currentValues = normalizeCsvValues(fromCsv(existing));
  const draft = pending.trim().replace(/\s+/g, " ");
  if (!draft) {
    return toCsv(currentValues);
  }
  const draftKey = draft.toLowerCase();
  if (currentValues.some((value) => value.toLowerCase() === draftKey)) {
    return toCsv(currentValues);
  }
  const mergedValues = replaceSingleExisting && currentValues.length === 1
    ? [draft]
    : [...currentValues, draft];
  return toCsv(normalizeCsvValues(mergedValues));
}

function friendlyError(err: unknown) {
  const message = err instanceof Error ? err.message : "Unable to save patient. Please check the details and try again.";
  if (message.includes("mobile is required")) return "Mobile number is required.";
  if (message.includes("Enter a valid 10-digit Indian mobile number.")) return "Enter a valid 10-digit Indian mobile number.";
  if (message.includes("Enter a valid 10-digit mobile number.")) return "Enter a valid 10-digit Indian mobile number.";
  if (message.includes("mobile must be")) return "Enter a valid 10-digit Indian mobile number.";
  if (message.includes("emergencyContactMobile must be")) return "Enter a valid 10-digit Indian mobile number.";
  if (message.includes("same mobile")) return "Possible duplicate patient found. Open the existing patient or confirm that this is a new patient.";
  if (message.includes("firstName")) return "First name is required.";
  if (message.includes("Patient details can be edited by Clinic Admin after registration day.")) return "Patient details can be edited by Clinic Admin after registration day.";
  return "Unable to save patient. Please check the details and try again.";
}

export default function PatientFormPage({ mode }: { mode: "create" | "edit" }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams();
  const id = params.id || "";

  const [form, setForm] = React.useState<FormState>(emptyForm());
  const [loading, setLoading] = React.useState(mode === "edit");
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [loadedPatient, setLoadedPatient] = React.useState<Patient | null>(null);
  const [allergiesInput, setAllergiesInput] = React.useState("");
  const [existingConditionsInput, setExistingConditionsInput] = React.useState("");
  const [longTermMedicationsInput, setLongTermMedicationsInput] = React.useState("");
  const [dobEstimatedFromAge, setDobEstimatedFromAge] = React.useState(false);
  const mobileInputRef = React.useRef<HTMLInputElement | null>(null);
  const firstNameInputRef = React.useRef<HTMLInputElement | null>(null);
  const lastNameInputRef = React.useRef<HTMLInputElement | null>(null);
  const ageInputRef = React.useRef<HTMLInputElement | null>(null);
  const dobInputRef = React.useRef<HTMLInputElement | null>(null);
  const genderInputRef = React.useRef<HTMLDivElement | null>(null);
  const saveButtonRef = React.useRef<HTMLButtonElement | null>(null);
  const validationSchema = mode === "create" ? patientQuickRegisterSchema : patientRegistrationSchema;
  const validationPreview = React.useMemo(
    () => validationSchema.safeParse(formToInput({ ...form, mobile: normalizeIndianMobileInput(form.mobile) as string })),
    [form, validationSchema],
  );
  const liveFieldErrors: Record<string, string> = validationPreview.success ? {} : mapZodErrors(validationPreview.error);
  const { duplicates, checking, setContinueNew } = useDuplicatePatientLookup({
    accessToken: auth.accessToken,
    tenantId: auth.tenantId,
    mobile: form.mobile,
    enabled: mode === "create",
    debounceMs: 500,
  });

  const clearFieldError = (field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  React.useEffect(() => {
      if (mode === "create") {
        window.setTimeout(() => mobileInputRef.current?.focus(), 50);
      }
      setDobEstimatedFromAge(false);
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
          setLoadedPatient(detail.patient);
          setForm(patientToForm(detail.patient));
          setAllergiesInput("");
          setExistingConditionsInput("");
          setLongTermMedicationsInput("");
          setDobEstimatedFromAge(false);
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

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isDoctor = auth.rolesUpper.includes("DOCTOR") || tenantRole === "DOCTOR";
  const isClinicAdmin = auth.rolesUpper.includes("CLINIC_ADMIN") || tenantRole === "CLINIC_ADMIN";
  const canEditRecord = mode === "create"
    ? !isDoctor && auth.hasPermission("patient.create")
    : Boolean(auth.hasPermission("patient.update") && (isClinicAdmin || loadedPatient?.canEdit === true));

  const updateField = (field: Exclude<keyof FormState, "ageYears" | "active" | "gender" | "dateOfBirth" | "mobile">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      clearFieldError(field);
      setError(null);
      setForm((current) => ({ ...current, [field]: event.target.value }));
    };

  const updateMobile = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    clearFieldError("mobile");
    setError(null);
    setContinueNew(false);
    setForm((current) => ({ ...current, mobile: event.target.value }));
  };

  const updateDateOfBirth = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const dateOfBirth = event.target.value;
    clearFieldError("dateOfBirth");
    setError(null);
    setDobEstimatedFromAge(false);
    const calculatedAge = calculateAgeFromDob(dateOfBirth);
    setForm((current) => ({ ...current, dateOfBirth, ageYears: calculatedAge ? Number(calculatedAge) : null }));
  };

  const updateAge = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const value = event.target.value === "" ? null : Number(event.target.value);
    clearFieldError("ageYears");
    setError(null);
    setDobEstimatedFromAge(value !== null);
    setForm((current) => ({ ...current, ageYears: value, dateOfBirth: approximateDobFromAge(value) }));
  };

  const commitAllergiesInput = (replaceSingleExisting = false) => {
    const pending = allergiesInput.trim();
    if (!pending) {
      setAllergiesInput("");
      return;
    }
    setForm((current) => ({ ...current, allergies: mergeAllergiesValue(current.allergies, pending, replaceSingleExisting) }));
    setAllergiesInput("");
  };

  const commitExistingConditionsInput = () => {
    const pending = existingConditionsInput.trim();
    if (!pending) {
      setExistingConditionsInput("");
      return;
    }
    setForm((current) => ({ ...current, existingConditions: mergeAllergiesValue(current.existingConditions, pending, false) }));
    setExistingConditionsInput("");
  };

  const commitLongTermMedicationsInput = () => {
    const pending = longTermMedicationsInput.trim();
    if (!pending) {
      setLongTermMedicationsInput("");
      return;
    }
    setForm((current) => ({ ...current, longTermMedications: mergeAllergiesValue(current.longTermMedications, pending, false) }));
    setLongTermMedicationsInput("");
  };

  const focusFirstInvalidField = () => {
    const invalidFieldOrder = ["mobile", "firstName", "lastName", "gender", "ageYears", "dateOfBirth"];
    const errorFields = [...invalidFieldOrder, ...Object.keys(fieldErrors), ...Object.keys(liveFieldErrors)];
    const firstError = errorFields.find((field) => fieldErrors[field] || liveFieldErrors[field]);
    switch (firstError) {
      case "mobile":
        mobileInputRef.current?.focus();
        break;
      case "firstName":
        firstNameInputRef.current?.focus();
        break;
      case "lastName":
        lastNameInputRef.current?.focus();
        break;
      case "gender":
        genderInputRef.current?.focus();
        break;
      case "ageYears":
        ageInputRef.current?.focus();
        break;
      case "dateOfBirth":
        dobInputRef.current?.focus();
        break;
      default:
        mobileInputRef.current?.focus();
        break;
    }
  };

  const advance = (event: React.KeyboardEvent<HTMLElement>, next: { current: HTMLInputElement | HTMLButtonElement | null } | "save") => {
    if (event.key !== "Enter" || event.shiftKey) return;
    event.preventDefault();
    if (next === "save") {
      saveButtonRef.current?.click();
      return;
    }
    next.current?.focus();
  };

  const savePatient = async (next: "detail" | "appointment" | "queue" = "detail") => {
    if (!auth.accessToken || !auth.tenantId) return;
    const allergies = mergeAllergiesValue(form.allergies, allergiesInput, true);
    const existingConditions = mergeAllergiesValue(form.existingConditions, existingConditionsInput, false);
    const longTermMedications = mergeAllergiesValue(form.longTermMedications, longTermMedicationsInput, false);
    const nextForm = { ...form, allergies, existingConditions, longTermMedications };
    if (allergies !== form.allergies || existingConditions !== form.existingConditions || longTermMedications !== form.longTermMedications) {
      setForm(nextForm);
    }
    setAllergiesInput("");
    setExistingConditionsInput("");
    setLongTermMedicationsInput("");
    const payload = formToInput({ ...nextForm, mobile: normalizeIndianMobileInput(nextForm.mobile) as string });
    const parsed = validationSchema.safeParse(payload);
    if (!parsed.success) {
      setSuccess(null);
      setFieldErrors(mapZodErrors(parsed.error));
      setError("Please correct the highlighted fields.");
      focusFirstInvalidField();
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.debug("Patient form validation errors", mapZodErrors(parsed.error));
      }
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    setFieldErrors({});
    try {
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
      const message = err instanceof Error ? err.message.toLowerCase() : "";
      if (message.includes("invalid input") || message.includes("validation") || message.includes("required") || message.includes("must be")) {
        setError("Please correct the highlighted fields.");
      } else {
        setError(friendlyError(err));
      }
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

  const disabled = !canEditRecord || saving;

  return (
    <Stack spacing={2.5} component="form" onSubmit={onSave} noValidate>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 0.5 }}>
            {mode === "create" ? "New Patient" : "Edit Patient"}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Fast family registration. Start with mobile number to check for existing family members.
          </Typography>
          {loadedPatient ? (
            <Typography variant="caption" color="text.secondary">
              {patientIdentitySummary(loadedPatient)}
            </Typography>
          ) : null}
        </Box>
        <Button type="button" variant="outlined" onClick={() => navigate("/patients")}>Back to Patients</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {!canEditRecord ? (
        <Alert severity="info">
          {mode === "edit" && (loadedPatient?.canEdit === false)
            ? "Patient details can be edited by Clinic Admin after registration day."
            : "You have read-only access to this patient record."}
        </Alert>
      ) : null}

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
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>Register New Patient</Typography>
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
                    label="Mobile number *"
                    value={form.mobile}
                    onChange={updateMobile}
                    disabled={disabled}
                    error={Boolean(fieldErrors.mobile) || Boolean(liveFieldErrors.mobile)}
                    helperText={fieldErrors.mobile || liveFieldErrors.mobile || (checking ? "Checking for existing patients..." : duplicates.length > 0 ? "Existing patients found below." : "Primary lookup and duplicate check")}
                    inputProps={{ inputMode: "tel", autoComplete: "tel" }}
                    onKeyDown={(event) => advance(event, firstNameInputRef)}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField
                    fullWidth
                    required
                    inputRef={firstNameInputRef}
                    label="First name *"
                    value={form.firstName}
                    onChange={updateField("firstName")}
                    disabled={disabled}
                    error={Boolean(fieldErrors.firstName) || Boolean(liveFieldErrors.firstName)}
                    helperText={fieldErrors.firstName || liveFieldErrors.firstName || ""}
                    inputProps={{ autoComplete: "given-name" }}
                    onKeyDown={(event) => advance(event, lastNameInputRef)}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth inputRef={lastNameInputRef} label="Last name" value={form.lastName} onChange={updateField("lastName")} disabled={disabled} inputProps={{ autoComplete: "family-name" }} onKeyDown={(event) => advance(event, ageInputRef)} />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth required error={Boolean(fieldErrors.gender)} ref={genderInputRef} tabIndex={-1}>
                    <InputLabel id="patient-gender-label">Gender *</InputLabel>
                    <Select
                      labelId="patient-gender-label"
                      label="Gender *"
                      value={form.gender}
                      onChange={(event) => {
                        clearFieldError("gender");
                        setError(null);
                        setForm((current) => ({ ...current, gender: event.target.value as PatientGender }));
                      }}
                      disabled={disabled}
                    >
                      {genderOptions.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                    </Select>
                    <FormHelperText>{fieldErrors.gender || liveFieldErrors.gender || ""}</FormHelperText>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField
                    fullWidth
                    inputRef={ageInputRef}
                    type="number"
                    required
                    label="Age *"
                    value={form.ageYears ?? ""}
                    onChange={updateAge}
                    disabled={disabled}
                    error={Boolean(fieldErrors.ageYears) || Boolean(liveFieldErrors.ageYears)}
                    helperText={fieldErrors.ageYears || liveFieldErrors.ageYears || (dobEstimatedFromAge ? "DOB estimated from age" : "DOB auto-filled approximately")}
                    inputProps={{ min: 0, max: 120 }}
                    onKeyDown={(event) => advance(event, dobInputRef)}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField
                    fullWidth
                    inputRef={dobInputRef}
                    type="date"
                    required
                    label="Date of birth *"
                    value={form.dateOfBirth}
                    onChange={updateDateOfBirth}
                    disabled={disabled}
                    error={Boolean(fieldErrors.dateOfBirth) || Boolean(liveFieldErrors.dateOfBirth)}
                    helperText={fieldErrors.dateOfBirth || liveFieldErrors.dateOfBirth || "Age updates automatically"}
                    InputLabelProps={{ shrink: true }}
                    onKeyDown={(event) => advance(event, saveButtonRef)}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }} sx={{ display: "flex", alignItems: "center", justifyContent: { xs: "stretch", md: "flex-end" }, gap: 1 }}>
                  <Button ref={saveButtonRef} type="submit" fullWidth variant="contained" size="large" disabled={disabled || checking || !validationPreview.success}>
                    {saving ? "Saving..." : mode === "create" ? "Register New Patient" : "Save"}
                  </Button>
                </Grid>
              </Grid>

              {checking ? <Alert severity="info">Checking for existing patients...</Alert> : null}
              {!checking && form.mobile.trim().length >= 6 && duplicates.length === 0 ? <Alert severity="info">No existing patient found.</Alert> : null}
              {duplicates.length > 0 ? (
                <Card variant="outlined" sx={{ bgcolor: "warning.50", borderColor: "warning.light" }}>
                  <CardContent sx={{ py: 1.5 }}>
                    <Stack spacing={1.25}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>Possible existing patients found</Typography>
                          <Typography variant="body2" color="text.secondary">Open an existing record if this is a repeat visit.</Typography>
                        </Box>
                        <Button type="button" size="small" onClick={() => setContinueNew(true)}>Continue registration</Button>
                      </Box>
                      <List dense disablePadding>
                        {duplicates.map((patient) => (
                          <ListItemButton
                            key={patient.id}
                            divider
                            onClick={() => navigate(`/patients/${patient.id}`)}
                            onKeyDown={(event) => {
                              if (event.key === "Enter" || event.key === " ") {
                                event.preventDefault();
                                navigate(`/patients/${patient.id}`);
                              }
                            }}
                          >
                            <ListItemText
                              primary={patientDisplayName(patient)}
                              secondary={`${patient.patientNumber || "Patient No: Not assigned"} • Mobile ${patient.mobile}`}
                            />
                            <Stack direction="row" spacing={0.75}>
                              <Button type="button" size="small" variant="outlined" onClick={(event) => { event.stopPropagation(); navigate(`/patients/${patient.id}`); }}>Open Patient</Button>
                              <Button type="button" size="small" variant="outlined" onClick={(event) => { event.stopPropagation(); navigate(`/appointments?patientId=${patient.id}`, { state: { patient } }); }}>Book Appointment</Button>
                            </Stack>
                          </ListItemButton>
                        ))}
                      </List>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {mode === "create" && !isDoctor ? (
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <Button type="button" variant="outlined" disabled={disabled || !validationPreview.success} onClick={() => void savePatient("appointment")}>Save & Create Appointment</Button>
                  <Button type="button" variant="outlined" disabled={disabled || !validationPreview.success} onClick={() => void savePatient("queue")}>Save & Add to Queue</Button>
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
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="Emergency contact mobile"
                    value={form.emergencyContactMobile}
                    onChange={(event) => {
                      clearFieldError("emergencyContactMobile");
                      setError(null);
                      setForm((current) => ({ ...current, emergencyContactMobile: event.target.value }));
                    }}
                    disabled={disabled}
                    error={Boolean(fieldErrors.emergencyContactMobile) || Boolean(liveFieldErrors.emergencyContactMobile)}
                    helperText={fieldErrors.emergencyContactMobile || liveFieldErrors.emergencyContactMobile || ""}
                    inputProps={{ inputMode: "tel" }}
                  />
                </Grid>
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
                  <FormControl fullWidth error={Boolean(fieldErrors.bloodGroup)}>
                    <InputLabel id="patient-blood-group-label">Blood group</InputLabel>
                    <Select
                      labelId="patient-blood-group-label"
                      label="Blood group"
                      value={form.bloodGroup}
                      onChange={(event) => {
                        clearFieldError("bloodGroup");
                        setError(null);
                        setForm((current) => ({ ...current, bloodGroup: event.target.value }));
                      }}
                      disabled={disabled}
                    >
                      <MenuItem value="">Unknown</MenuItem>
                      {bloodGroups.map((group) => <MenuItem key={group} value={group}>{group}</MenuItem>)}
                    </Select>
                    <FormHelperText>{fieldErrors.bloodGroup || ""}</FormHelperText>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 9 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonAllergies}
                    value={fromCsv(form.allergies)}
                    inputValue={allergiesInput}
                    onChange={(_, values) => {
                      setForm((current) => ({ ...current, allergies: toCsv(normalizeCsvValues(values)) }));
                      setAllergiesInput("");
                    }}
                    onInputChange={(_, inputValue, reason) => {
                      if (reason === "reset") {
                        return;
                      }
                      setAllergiesInput(inputValue);
                    }}
                    disabled={disabled}
                    renderTags={(value, getTagProps) => value.map((option, index) => <Chip color="error" variant="outlined" label={option} {...getTagProps({ index })} key={option} />)}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="Allergies"
                        helperText="Use chips for fast capture. Add custom values if needed."
                        onBlur={() => commitAllergiesInput(false)}
                      />
                    )}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonConditions}
                    value={fromCsv(form.existingConditions)}
                    inputValue={existingConditionsInput}
                    onChange={(_, values) => {
                      setForm((current) => ({ ...current, existingConditions: toCsv(values) }));
                      setExistingConditionsInput("");
                    }}
                    onInputChange={(_, inputValue, reason) => {
                      if (reason === "reset") {
                        return;
                      }
                      setExistingConditionsInput(inputValue);
                    }}
                    disabled={disabled}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="Chronic conditions"
                        onBlur={commitExistingConditionsInput}
                      />
                    )}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={commonMedications}
                    value={fromCsv(form.longTermMedications)}
                    inputValue={longTermMedicationsInput}
                    onChange={(_, values) => {
                      setForm((current) => ({ ...current, longTermMedications: toCsv(values) }));
                      setLongTermMedicationsInput("");
                    }}
                    onInputChange={(_, inputValue, reason) => {
                      if (reason === "reset") {
                        return;
                      }
                      setLongTermMedicationsInput(inputValue);
                    }}
                    disabled={disabled}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="Long-term medications"
                        onBlur={commitLongTermMedicationsInput}
                      />
                    )}
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
                <Button type="submit" variant="contained" disabled={disabled || !validationPreview.success}>{saving ? "Saving..." : "Save Patient"}</Button>
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
