import * as React from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { mapZodErrors, patientQuickRegisterSchema } from "@deepthoughtnet/form-validation-kit";

import { createPatient, type Patient, type PatientGender } from "../../api/clinicApi";
import { approximateDobFromAge, calculateAge, emptyQuickRegisterForm, patientLabel, patientSummary, seedQuickRegisterMobile, toPatientInput, type QuickRegisterForm } from "./patientQuickRegister";

type Props = {
  open: boolean;
  token: string | null;
  tenantId: string | null;
  title?: string;
  subtitle?: string;
  initialMobile?: string;
  saveLabel?: string;
  onClose: () => void;
  onCreated: (patient: Patient) => void;
};

function friendlyError(message: string) {
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

export default function PatientQuickRegisterDialog({
  open,
  token,
  tenantId,
  title = "Quick Register Patient",
  subtitle,
  initialMobile = "",
  saveLabel = "Save patient",
  onClose,
  onCreated,
}: Props) {
  const [form, setForm] = React.useState<QuickRegisterForm>(emptyQuickRegisterForm(seedQuickRegisterMobile(initialMobile)));
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!open) return;
    setForm(emptyQuickRegisterForm(seedQuickRegisterMobile(initialMobile)));
    setError(null);
    setSaving(false);
  }, [initialMobile, open]);

  const preview = React.useMemo(
    () => patientQuickRegisterSchema.safeParse(toPatientInput(form)),
    [form],
  );
  const fieldErrors: Record<string, string> = preview.success ? {} : mapZodErrors(preview.error);

  const save = async () => {
    if (!token || !tenantId) return;
    if (!preview.success) {
      setError(friendlyError(preview.error.issues[0]?.message || "Unable to create patient"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await createPatient(token, tenantId, toPatientInput(form));
      onCreated(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create patient");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {subtitle ? <Typography variant="body2" color="text.secondary">{subtitle}</Typography> : null}
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField
            fullWidth
            label="Mobile"
            value={form.mobile}
            onChange={(event) => setForm((current) => ({ ...current, mobile: event.target.value }))}
            inputProps={{ inputMode: "tel" }}
            error={Boolean(fieldErrors.mobile)}
            helperText={fieldErrors.mobile || " "}
          />
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth label="First name" value={form.firstName} onChange={(event) => setForm((current) => ({ ...current, firstName: event.target.value }))} error={Boolean(fieldErrors.firstName)} helperText={fieldErrors.firstName || " "} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth label="Last name" value={form.lastName} onChange={(event) => setForm((current) => ({ ...current, lastName: event.target.value }))} error={Boolean(fieldErrors.lastName)} helperText={fieldErrors.lastName || " "} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth type="number" label="Age" value={form.ageYears} onChange={(event) => setForm((current) => ({ ...current, ageYears: event.target.value, dateOfBirth: approximateDobFromAge(event.target.value) }))} inputProps={{ min: 0, max: 130 }} error={Boolean(fieldErrors.ageYears)} helperText={fieldErrors.ageYears || " "}/>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth type="date" label="Date of birth" value={form.dateOfBirth} onChange={(event) => setForm((current) => ({ ...current, dateOfBirth: event.target.value, ageYears: calculateAge(event.target.value) }))} InputLabelProps={{ shrink: true }} error={Boolean(fieldErrors.dateOfBirth)} helperText={fieldErrors.dateOfBirth || " "}/>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <FormControl fullWidth error={Boolean(fieldErrors.gender)}>
                <InputLabel id="quick-gender-label">Gender</InputLabel>
                <Select
                  labelId="quick-gender-label"
                  label="Gender"
                  value={form.gender}
                  onChange={(event) => setForm((current) => ({ ...current, gender: event.target.value as PatientGender }))}
                >
                  <MenuItem value="MALE">Male</MenuItem>
                  <MenuItem value="FEMALE">Female</MenuItem>
                  <MenuItem value="OTHER">Other</MenuItem>
                  <MenuItem value="UNKNOWN">Unknown</MenuItem>
                </Select>
                {fieldErrors.gender ? <Typography variant="caption" color="error">{fieldErrors.gender}</Typography> : null}
              </FormControl>
            </Grid>
          </Grid>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={saving || !preview.success}>{saveLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}

export { emptyQuickRegisterForm, patientSummary, patientLabel };
