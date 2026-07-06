import * as React from "react";
import {
  Alert,
  Box,
  Card,
  CardContent,
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
  List,
  ListItemButton,
  ListItemText,
} from "@mui/material";
import { mapZodErrors, patientQuickRegisterSchema } from "@deepthoughtnet/form-validation-kit";

import { createPatient, type Patient, type PatientGender } from "../../api/clinicApi";
import {
  approximateDobFromAge,
  calculateAgeFromDob,
  emptyQuickRegisterForm,
  patientLabel,
  patientSummary,
  seedQuickRegisterMobile,
  toPatientInput,
  useDuplicatePatientLookup,
  type QuickRegisterForm,
} from "./patientQuickRegister";

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
  title = "Register New Patient",
  subtitle,
  initialMobile = "",
  saveLabel = "Save patient",
  onClose,
  onCreated,
}: Props) {
  const [form, setForm] = React.useState<QuickRegisterForm>(emptyQuickRegisterForm(seedQuickRegisterMobile(initialMobile)));
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [dobEstimatedFromAge, setDobEstimatedFromAge] = React.useState(false);
  const mobileRef = React.useRef<HTMLInputElement | null>(null);
  const firstNameRef = React.useRef<HTMLInputElement | null>(null);
  const lastNameRef = React.useRef<HTMLInputElement | null>(null);
  const ageRef = React.useRef<HTMLInputElement | null>(null);
  const dobRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (!open) return;
    setForm(emptyQuickRegisterForm(seedQuickRegisterMobile(initialMobile)));
    setError(null);
    setSaving(false);
    setDobEstimatedFromAge(false);
  }, [initialMobile, open]);

  const { duplicates, checking, setContinueNew } = useDuplicatePatientLookup({
    accessToken: token,
    tenantId,
    mobile: form.mobile,
    enabled: open,
    debounceMs: 500,
  });

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

  const advance = (event: React.KeyboardEvent<HTMLElement>, next: { current: HTMLInputElement | HTMLButtonElement | null } | "save") => {
    if (event.key !== "Enter" || event.shiftKey) return;
    event.preventDefault();
    if (next === "save") {
      void save();
      return;
    }
    next.current?.focus();
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
            inputRef={mobileRef}
            label="Mobile"
            value={form.mobile}
            onChange={(event) => {
              setContinueNew(false);
              setForm((current) => ({ ...current, mobile: event.target.value }));
            }}
            onKeyDown={(event) => advance(event, firstNameRef)}
            inputProps={{ inputMode: "tel" }}
            error={Boolean(fieldErrors.mobile)}
            helperText={fieldErrors.mobile || (checking ? "Checking for existing patients..." : duplicates.length > 0 ? "Existing patients found below." : "Primary lookup and duplicate check")}
          />
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                inputRef={firstNameRef}
                label="First name"
                value={form.firstName}
                onChange={(event) => setForm((current) => ({ ...current, firstName: event.target.value }))}
                onKeyDown={(event) => advance(event, lastNameRef)}
                error={Boolean(fieldErrors.firstName)}
                helperText={fieldErrors.firstName || " "}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                inputRef={lastNameRef}
                label="Last name"
                value={form.lastName}
                onChange={(event) => setForm((current) => ({ ...current, lastName: event.target.value }))}
                onKeyDown={(event) => advance(event, ageRef)}
                error={Boolean(fieldErrors.lastName)}
                helperText={fieldErrors.lastName || " "}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                inputRef={ageRef}
                type="number"
                label="Age"
                value={form.ageYears}
                onChange={(event) => {
                  const ageYears = event.target.value;
                  setDobEstimatedFromAge(ageYears.trim() !== "");
                  setForm((current) => ({ ...current, ageYears, dateOfBirth: approximateDobFromAge(ageYears) }));
                }}
                onKeyDown={(event) => advance(event, dobRef)}
                inputProps={{ min: 0, max: 130 }}
                error={Boolean(fieldErrors.ageYears)}
                helperText={fieldErrors.ageYears || " "}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                inputRef={dobRef}
                type="date"
                label="Date of birth"
                value={form.dateOfBirth}
                onChange={(event) => {
                  const dateOfBirth = event.target.value;
                  setDobEstimatedFromAge(false);
                  setForm((current) => ({ ...current, dateOfBirth, ageYears: calculateAgeFromDob(dateOfBirth) }));
                }}
                onKeyDown={(event) => advance(event, "save")}
                InputLabelProps={{ shrink: true }}
                error={Boolean(fieldErrors.dateOfBirth)}
                helperText={fieldErrors.dateOfBirth || (dobEstimatedFromAge ? "DOB estimated from age" : " ")}
              />
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
          {duplicates.length > 0 ? (
            <Card variant="outlined" sx={{ bgcolor: "warning.50", borderColor: "warning.light" }}>
              <CardContent sx={{ py: 1.25 }}>
                <Stack spacing={1}>
                  <Box>
                    <Typography sx={{ fontWeight: 800 }}>Existing patient found</Typography>
                    <Typography variant="body2" color="text.secondary">Open the existing patient record or continue creating a new one if this is a genuinely new registration.</Typography>
                  </Box>
                      <List dense disablePadding>
                        {duplicates.map((patient) => (
                      <ListItemButton key={patient.id} divider onClick={() => onCreated(patient)}>
                        <ListItemText
                          primary={patientLabel(patient)}
                          secondary={patientSummary(patient)}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                  <Button type="button" size="small" onClick={() => setContinueNew(true)}>Continue registration</Button>
                </Stack>
              </CardContent>
            </Card>
          ) : null}
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
