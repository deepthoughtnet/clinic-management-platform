import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
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
  updatePatient,
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
  notes: "",
  active: true,
});

function patientToForm(patient: PatientInput): FormState {
  return {
    ...patient,
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
    notes: form.notes.trim() || null,
    active: form.active,
  };
}

const genderOptions: PatientGender[] = ["MALE", "FEMALE", "OTHER", "UNKNOWN"];

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
          setError(err instanceof Error ? err.message : "Failed to load patient");
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

  const updateField = (field: Exclude<keyof FormState, "ageYears" | "active" | "gender">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setForm((current) => ({ ...current, [field]: event.target.value }));
    };

  const onSave = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const payload = formToInput(form);
      const saved = mode === "create"
        ? await createPatient(auth.accessToken, auth.tenantId, payload)
        : await updatePatient(auth.accessToken, auth.tenantId, id, payload);
      setSuccess("Patient saved");
      navigate(`/patients/${saved.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save patient");
    } finally {
      setSaving(false);
    }
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
      setError(err instanceof Error ? err.message : "Failed to deactivate patient");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Stack spacing={3} component="form" onSubmit={onSave}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            {mode === "create" ? "New Patient" : "Edit Patient"}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Clinic-scoped patient registration and demographics.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => navigate("/patients")}>
          Back to Patients
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {!canEdit ? <Alert severity="info">You have read-only access to this patient record.</Alert> : null}

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="First name" value={form.firstName} onChange={updateField("firstName")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Last name" value={form.lastName} onChange={updateField("lastName")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <FormControl fullWidth>
                  <InputLabel id="patient-gender-label">Gender</InputLabel>
                  <Select
                    labelId="patient-gender-label"
                    label="Gender"
                    value={form.gender}
                    onChange={(e) => setForm((current) => ({ ...current, gender: e.target.value as PatientGender }))}
                    disabled={!canEdit || saving}
                  >
                    {genderOptions.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="date" label="Date of birth" value={form.dateOfBirth} onChange={updateField("dateOfBirth")} disabled={!canEdit || saving} InputLabelProps={{ shrink: true }} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  type="number"
                  label="Age years"
                  value={form.ageYears ?? ""}
                  onChange={(event) => setForm((current) => ({ ...current, ageYears: event.target.value === "" ? null : Number(event.target.value) }))}
                  disabled={!canEdit || saving}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Mobile" value={form.mobile} onChange={updateField("mobile")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={form.email} onChange={updateField("email")} disabled={!canEdit || saving} /></Grid>
              <Grid size={12}><TextField fullWidth label="Address line 1" value={form.addressLine1} onChange={updateField("addressLine1")} disabled={!canEdit || saving} /></Grid>
              <Grid size={12}><TextField fullWidth label="Address line 2" value={form.addressLine2} onChange={updateField("addressLine2")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="City" value={form.city} onChange={updateField("city")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="State" value={form.state} onChange={updateField("state")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Country" value={form.country} onChange={updateField("country")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Postal code" value={form.postalCode} onChange={updateField("postalCode")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Emergency contact name" value={form.emergencyContactName} onChange={updateField("emergencyContactName")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Emergency contact mobile" value={form.emergencyContactMobile} onChange={updateField("emergencyContactMobile")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Blood group" value={form.bloodGroup} onChange={updateField("bloodGroup")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Allergies" value={form.allergies} onChange={updateField("allergies")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Existing conditions" value={form.existingConditions} onChange={updateField("existingConditions")} disabled={!canEdit || saving} /></Grid>
              <Grid size={12}><TextField fullWidth multiline minRows={4} label="Notes" value={form.notes} onChange={updateField("notes")} disabled={!canEdit || saving} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <FormControlLabel
                  control={<Switch checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} disabled={!canEdit || saving} />}
                  label={form.active ? "Active" : "Inactive"}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }} sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
                {mode === "edit" ? (
                  <Button variant="outlined" color="error" onClick={() => void onDeactivate()} disabled={!canEdit || saving || !form.active}>
                    Deactivate
                  </Button>
                ) : null}
                <Button type="submit" variant="contained" disabled={!canEdit || saving}>
                  Save
                </Button>
              </Grid>
            </Grid>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
