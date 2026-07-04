import * as React from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { documentUploadSchema, firstZodError } from "@deepthoughtnet/form-validation-kit";
import {
  getClinicalIntake,
  saveClinicalIntake,
  uploadPatientDocument,
  type ClinicalDocumentType,
  type ClinicalDocumentUploadSource,
  type ClinicalIntakeResponse,
  type TemperatureUnit,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";
import { PatientDocumentUploadDialog } from "./PatientDocumentUploadDialog";

const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;

function toNumber(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

type Props = {
  open: boolean;
  onClose: () => void;
  patientId: string;
  patientLabel: string;
  appointmentId?: string | null;
  consultationId?: string | null;
  onSaved?: (intake: ClinicalIntakeResponse) => void;
};

export function ClinicalIntakeDialog({ open, onClose, patientId, patientLabel, appointmentId = null, consultationId = null, onSaved }: Props) {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [latestIntake, setLatestIntake] = React.useState<ClinicalIntakeResponse | null>(null);
  const [documentDialogOpen, setDocumentDialogOpen] = React.useState(false);
  const [chiefComplaint, setChiefComplaint] = React.useState("");
  const [heightCm, setHeightCm] = React.useState("");
  const [weightKg, setWeightKg] = React.useState("");
  const [bloodPressureSystolic, setBloodPressureSystolic] = React.useState("");
  const [bloodPressureDiastolic, setBloodPressureDiastolic] = React.useState("");
  const [pulseRate, setPulseRate] = React.useState("");
  const [temperature, setTemperature] = React.useState("");
  const [temperatureUnit, setTemperatureUnit] = React.useState<TemperatureUnit>("CELSIUS");
  const [spo2, setSpo2] = React.useState("");
  const [respiratoryRate, setRespiratoryRate] = React.useState("");
  const [randomBloodSugar, setRandomBloodSugar] = React.useState("");
  const [painScore, setPainScore] = React.useState("");
  const [notes, setNotes] = React.useState("");
  const [complete, setComplete] = React.useState(true);

  const dispatchIntakeUpdated = React.useCallback((intake: ClinicalIntakeResponse) => {
    onSaved?.(intake);
    window.dispatchEvent(new CustomEvent("clinic:clinical-intake-updated", {
      detail: { patientId, appointmentId, consultationId, intakeId: intake.id },
    }));
  }, [appointmentId, consultationId, onSaved, patientId]);

  React.useEffect(() => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!open || !accessToken || !tenantId) {
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const response = await getClinicalIntake(accessToken!, tenantId!, patientId, appointmentId || undefined);
        if (cancelled) return;
        setLatestIntake(response);
        if (response) {
          setChiefComplaint(response.chiefComplaint || "");
          setHeightCm(response.heightCm?.toString() || "");
          setWeightKg(response.weightKg?.toString() || "");
          setBloodPressureSystolic(response.bloodPressureSystolic?.toString() || "");
          setBloodPressureDiastolic(response.bloodPressureDiastolic?.toString() || "");
          setPulseRate(response.pulseRate?.toString() || "");
          setTemperature(response.temperature?.toString() || "");
          setTemperatureUnit(response.temperatureUnit || "CELSIUS");
          setSpo2(response.spo2?.toString() || "");
          setRespiratoryRate(response.respiratoryRate?.toString() || "");
          setRandomBloodSugar(response.randomBloodSugar?.toString() || "");
          setPainScore(response.painScore?.toString() || "");
          setNotes(response.notes || "");
          setComplete(response.complete);
        } else {
          setChiefComplaint("");
          setHeightCm("");
          setWeightKg("");
          setBloodPressureSystolic("");
          setBloodPressureDiastolic("");
          setPulseRate("");
          setTemperature("");
          setTemperatureUnit("CELSIUS");
          setSpo2("");
          setRespiratoryRate("");
          setRandomBloodSugar("");
          setPainScore("");
          setNotes("");
          setComplete(true);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load clinical intake");
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
  }, [appointmentId, auth.accessToken, auth.tenantId, open, patientId]);

  React.useEffect(() => {
    if (!open) {
      setLoading(false);
      setSaving(false);
      setError(null);
      setDocumentDialogOpen(false);
    }
  }, [open]);

  const submit = async () => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!accessToken || !tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const intake = await saveClinicalIntake(accessToken, tenantId, patientId, {
        appointmentId,
        consultationId,
        chiefComplaint: chiefComplaint.trim() || null,
        heightCm: toNumber(heightCm),
        weightKg: toNumber(weightKg),
        bloodPressureSystolic: toNumber(bloodPressureSystolic),
        bloodPressureDiastolic: toNumber(bloodPressureDiastolic),
        pulseRate: toNumber(pulseRate),
        temperature: toNumber(temperature),
        temperatureUnit,
        spo2: toNumber(spo2),
        respiratoryRate: toNumber(respiratoryRate),
        randomBloodSugar: toNumber(randomBloodSugar),
        painScore: toNumber(painScore),
        notes: notes.trim() || null,
        complete,
      });
      setLatestIntake(intake);
      dispatchIntakeUpdated(intake);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save clinical intake");
      return;
    } finally {
      setSaving(false);
    }
  };

  const handleDocumentUpload = async (body: {
    file: File;
    documentType: ClinicalDocumentType;
    title: string;
    reportDate: string | null;
    notes: string | null;
    consultationId: string | null;
    uploadSource: ClinicalDocumentUploadSource;
    visibility: "INTERNAL_ONLY" | "PATIENT_VISIBLE";
  }) => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!accessToken || !tenantId) return;
    const parsed = documentUploadSchema({
      required: true,
      allowedMimeTypes: ["application/pdf", "image/jpeg", "image/png"],
      allowedExtensions: ["pdf", "jpg", "jpeg", "png"],
      maxBytes: MAX_DOCUMENT_BYTES,
    }).safeParse(body.file);
    if (!parsed.success) {
      throw new Error(firstZodError(parsed.error));
    }
    const uploaded = await uploadPatientDocument(accessToken, tenantId, patientId, {
      ...body,
      consultationId: body.consultationId || consultationId || undefined,
      sourceModule: "RECEPTION",
      sourceEntityId: appointmentId || consultationId || patientId,
      uploadSource: "RECEPTION",
      visibility: "INTERNAL_ONLY",
    });
    window.dispatchEvent(new CustomEvent("clinic:clinical-intake-updated", {
      detail: { patientId, appointmentId, consultationId, documentId: uploaded.id },
    }));
  };

  return (
    <>
      <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="md">
        <DialogTitle>Clinical Intake - {patientLabel}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <Alert severity="info">Reception can capture vitals and upload reports before the consultation starts.</Alert>
            {error ? <Alert severity="error">{error}</Alert> : null}
            {latestIntake ? (
              <Alert severity="success">
                Latest intake: {latestIntake.status === "INTAKE_COMPLETE" ? "Complete" : "Pending"}{latestIntake.recordedByName ? ` • ${latestIntake.recordedByName}` : ""}
              </Alert>
            ) : null}
            <Grid container spacing={1}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField size="small" fullWidth label="Optional chief complaint" value={chiefComplaint} onChange={(event) => setChiefComplaint(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Height (cm)" value={heightCm} onChange={(event) => setHeightCm(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Weight (kg)" value={weightKg} onChange={(event) => setWeightKg(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="BP Systolic" value={bloodPressureSystolic} onChange={(event) => setBloodPressureSystolic(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="BP Diastolic" value={bloodPressureDiastolic} onChange={(event) => setBloodPressureDiastolic(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Pulse" value={pulseRate} onChange={(event) => setPulseRate(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Temperature" value={temperature} onChange={(event) => setTemperature(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField select size="small" fullWidth label="Temp Unit" value={temperatureUnit} onChange={(event) => setTemperatureUnit(event.target.value as TemperatureUnit)}>
                  <MenuItem value="CELSIUS">Celsius</MenuItem>
                  <MenuItem value="FAHRENHEIT">Fahrenheit</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="SpO2" value={spo2} onChange={(event) => setSpo2(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Respiratory rate" value={respiratoryRate} onChange={(event) => setRespiratoryRate(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Random blood sugar" value={randomBloodSugar} onChange={(event) => setRandomBloodSugar(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField size="small" fullWidth label="Pain score" value={painScore} onChange={(event) => setPainScore(event.target.value)} />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField size="small" fullWidth multiline minRows={3} label="Notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
              </Grid>
            </Grid>
            <FormControlLabel control={<Checkbox checked={complete} onChange={(event) => setComplete(event.target.checked)} />} label="Mark intake complete" />
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Button variant="outlined" onClick={() => setDocumentDialogOpen(true)} disabled={saving || loading}>
                Upload report / referral
              </Button>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Close</Button>
          <Button variant="contained" onClick={() => void submit()} disabled={saving || loading}>
            {saving ? "Saving..." : "Save Intake"}
          </Button>
        </DialogActions>
      </Dialog>
      {documentDialogOpen ? (
        <PatientDocumentUploadDialog
          open
          onClose={() => setDocumentDialogOpen(false)}
          defaultUploadSource="RECEPTION"
          title="Upload intake document"
          submitLabel="Add report"
          consultationId={consultationId}
          onSubmit={handleDocumentUpload}
        />
      ) : null}
    </>
  );
}
