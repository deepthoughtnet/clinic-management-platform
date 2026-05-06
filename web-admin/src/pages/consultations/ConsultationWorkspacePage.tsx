import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
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
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import AddRoundedIcon from "@mui/icons-material/AddRounded";

import { useAuth } from "../../auth/useAuth";
import {
  cancelConsultation,
  completeConsultation,
  createPrescription,
  finalizePrescription,
  getConsultation,
  getConsultationPrescription,
  getMedicines,
  getPatient,
  getPrescriptionPdf,
  markPrescriptionSent,
  printPrescription,
  startConsultationFromAppointment,
  updateConsultation,
  updatePrescription,
  type Consultation,
  type ConsultationInput,
  type PatientDetail,
  type Medicine,
  type Prescription,
  type PrescriptionInput,
  type PrescriptionMedicine,
  type PrescriptionTest,
  type MedicineType,
  type Timing,
} from "../../api/clinicApi";

type ConsultationFormState = {
  chiefComplaints: string;
  symptoms: string;
  diagnosis: string;
  clinicalNotes: string;
  advice: string;
  followUpDate: string;
  bloodPressureSystolic: string;
  bloodPressureDiastolic: string;
  pulseRate: string;
  temperature: string;
  temperatureUnit: "CELSIUS" | "FAHRENHEIT" | "";
  weightKg: string;
  heightCm: string;
  spo2: string;
};

type MedicineRow = PrescriptionMedicine & { localId: string };
type TestRow = PrescriptionTest & { localId: string };

type PrescriptionFormState = {
  diagnosisSnapshot: string;
  advice: string;
  followUpDate: string;
  medicines: MedicineRow[];
  recommendedTests: TestRow[];
};

const FREQUENCIES = ["1-0-0", "0-1-0", "0-0-1", "1-1-0", "1-0-1", "1-1-1"];
const DURATIONS = ["3 days", "5 days", "7 days", "10 days", "15 days"];
const TIMINGS: { label: string; value: Timing }[] = [
  { label: "Before food", value: "BEFORE_FOOD" },
  { label: "After food", value: "AFTER_FOOD" },
  { label: "With food", value: "WITH_FOOD" },
  { label: "Anytime", value: "ANYTIME" },
];

function statusColor(status: Consultation["status"] | Prescription["status"]) {
  switch (status) {
    case "COMPLETED":
    case "FINALIZED":
    case "PRINTED":
    case "SENT":
      return "success";
    case "DRAFT":
      return "warning";
    case "CANCELLED":
      return "default";
  }
}

function emptyConsultationForm(record?: Consultation | null): ConsultationFormState {
  return {
    chiefComplaints: record?.chiefComplaints || "",
    symptoms: record?.symptoms || "",
    diagnosis: record?.diagnosis || "",
    clinicalNotes: record?.clinicalNotes || "",
    advice: record?.advice || "",
    followUpDate: record?.followUpDate || "",
    bloodPressureSystolic: record?.bloodPressureSystolic?.toString() || "",
    bloodPressureDiastolic: record?.bloodPressureDiastolic?.toString() || "",
    pulseRate: record?.pulseRate?.toString() || "",
    temperature: record?.temperature?.toString() || "",
    temperatureUnit: record?.temperatureUnit || "",
    weightKg: record?.weightKg?.toString() || "",
    heightCm: record?.heightCm?.toString() || "",
    spo2: record?.spo2?.toString() || "",
  };
}

function newMedicineRow(index: number): MedicineRow {
  return {
    localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 8)}`,
    medicineName: "",
    medicineType: null,
    strength: "",
    dosage: "",
    frequency: "",
    duration: "",
    timing: null,
    instructions: "",
    sortOrder: index + 1,
  };
}

function newTestRow(index: number): TestRow {
  return {
    localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 8)}`,
    testName: "",
    instructions: "",
    sortOrder: index + 1,
  };
}

function emptyPrescriptionForm(record?: Prescription | null, consultation?: Consultation | null): PrescriptionFormState {
  return {
    diagnosisSnapshot: record?.diagnosisSnapshot || consultation?.diagnosis || "",
    advice: record?.advice || consultation?.advice || "",
    followUpDate: record?.followUpDate || consultation?.followUpDate || "",
    medicines: record?.medicines?.length
      ? record.medicines.map((item, index) => ({ ...item, localId: `${index}-${item.sortOrder ?? index}` }))
      : [newMedicineRow(0)],
    recommendedTests: record?.recommendedTests?.length
      ? record.recommendedTests.map((item, index) => ({ ...item, localId: `${index}-${item.sortOrder ?? index}` }))
      : [],
  };
}

function toConsultationInput(form: ConsultationFormState, consultation: Consultation): ConsultationInput {
  return {
    patientId: consultation.patientId,
    doctorUserId: consultation.doctorUserId,
    appointmentId: consultation.appointmentId,
    chiefComplaints: form.chiefComplaints.trim() || null,
    symptoms: form.symptoms.trim() || null,
    diagnosis: form.diagnosis.trim() || null,
    clinicalNotes: form.clinicalNotes.trim() || null,
    advice: form.advice.trim() || null,
    followUpDate: form.followUpDate || null,
    bloodPressureSystolic: form.bloodPressureSystolic ? Number(form.bloodPressureSystolic) : null,
    bloodPressureDiastolic: form.bloodPressureDiastolic ? Number(form.bloodPressureDiastolic) : null,
    pulseRate: form.pulseRate ? Number(form.pulseRate) : null,
    temperature: form.temperature ? Number(form.temperature) : null,
    temperatureUnit: form.temperatureUnit || null,
    weightKg: form.weightKg ? Number(form.weightKg) : null,
    heightCm: form.heightCm ? Number(form.heightCm) : null,
    spo2: form.spo2 ? Number(form.spo2) : null,
  };
}

function toPrescriptionInput(form: PrescriptionFormState, consultation: Consultation): PrescriptionInput {
  return {
    patientId: consultation.patientId,
    doctorUserId: consultation.doctorUserId,
    consultationId: consultation.id,
    appointmentId: consultation.appointmentId,
    diagnosisSnapshot: form.diagnosisSnapshot.trim() || null,
    advice: form.advice.trim() || null,
    followUpDate: form.followUpDate || null,
    medicines: form.medicines
      .filter((row) => row.medicineName.trim())
      .map((row, index) => ({
        medicineName: row.medicineName.trim(),
        medicineType: row.medicineType || null,
        strength: (row.strength || "").trim() || null,
        dosage: (row.dosage || "").trim(),
        frequency: (row.frequency || "").trim(),
        duration: (row.duration || "").trim(),
        timing: row.timing || null,
        instructions: (row.instructions || "").trim() || null,
        sortOrder: row.sortOrder ?? index + 1,
      })),
    recommendedTests: form.recommendedTests
      .filter((row) => row.testName.trim())
      .map((row, index) => ({
        testName: row.testName.trim(),
        instructions: (row.instructions || "").trim() || null,
        sortOrder: row.sortOrder ?? index + 1,
      })),
  };
}

export default function ConsultationWorkspacePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams();
  const [searchParams] = useSearchParams();
  const consultationId = params.id || "";
  const appointmentId = searchParams.get("appointmentId") || "";
  const [consultation, setConsultation] = React.useState<Consultation | null>(null);
  const [patient, setPatient] = React.useState<PatientDetail | null>(null);
  const [prescription, setPrescription] = React.useState<Prescription | null>(null);
  const [medicineCatalog, setMedicineCatalog] = React.useState<Medicine[]>([]);
  const [consultationForm, setConsultationForm] = React.useState<ConsultationFormState>(emptyConsultationForm());
  const [prescriptionForm, setPrescriptionForm] = React.useState<PrescriptionFormState>(emptyPrescriptionForm());
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      if (!consultationId && appointmentId) {
        try {
          const started = await startConsultationFromAppointment(auth.accessToken, auth.tenantId, appointmentId);
          navigate(`/consultations/${started.id}`, { replace: true });
          return;
        } catch (err) {
          if (!cancelled) {
            setError(err instanceof Error ? err.message : "Failed to start consultation");
          }
        }
      }
      if (!consultationId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const medicines = await getMedicines(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setMedicineCatalog(medicines);
        }
        const consult = await getConsultation(auth.accessToken, auth.tenantId, consultationId);
        if (!cancelled) {
          setConsultation(consult);
          setConsultationForm(emptyConsultationForm(consult));
          const detail = await getPatient(auth.accessToken, auth.tenantId, consult.patientId);
          setPatient(detail);
          try {
            const rx = await getConsultationPrescription(auth.accessToken, auth.tenantId, consult.id);
            setPrescription(rx);
            setPrescriptionForm(emptyPrescriptionForm(rx, consult));
          } catch {
            setPrescription(null);
            setPrescriptionForm(emptyPrescriptionForm(null, consult));
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load consultation workspace");
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
  }, [auth.accessToken, auth.tenantId, consultationId, appointmentId, navigate]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const readOnly = consultation ? consultation.status !== "DRAFT" : false;

  const persistConsultation = async (): Promise<Consultation | null> => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return null;
    }
    setSaving(true);
    setError(null);
    try {
      const input = toConsultationInput(consultationForm, consultation);
      const saved = await updateConsultation(auth.accessToken, auth.tenantId, consultation.id, input);
      setConsultation(saved);
      setConsultationForm(emptyConsultationForm(saved));
      setInfo("Consultation saved");
      return saved;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save consultation");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const completeCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return;
    }
    const saved = await persistConsultation();
    if (!saved) {
      return;
    }
    setSaving(true);
    try {
      const completed = await completeConsultation(auth.accessToken, auth.tenantId, consultation.id);
      setConsultation(completed);
      setInfo("Consultation completed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete consultation");
    } finally {
      setSaving(false);
    }
  };

  const cancelCurrentConsultation = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return;
    }
    setSaving(true);
    try {
      const cancelled = await cancelConsultation(auth.accessToken, auth.tenantId, consultation.id);
      setConsultation(cancelled);
      setInfo("Consultation cancelled");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel consultation");
    } finally {
      setSaving(false);
    }
  };

  const persistPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return null;
    }
    setSaving(true);
    setError(null);
    try {
      const body = toPrescriptionInput(prescriptionForm, consultation);
      const saved = prescription
        ? await updatePrescription(auth.accessToken, auth.tenantId, prescription.id, body)
        : await createPrescription(auth.accessToken, auth.tenantId, body);
      setPrescription(saved);
      setPrescriptionForm(emptyPrescriptionForm(saved, consultation));
      setInfo("Prescription saved");
      return saved;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save prescription");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const finalizeCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return;
    }
    const saved = await persistPrescription();
    if (!saved) {
      return;
    }
    setSaving(true);
    try {
      const finalized = await finalizePrescription(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(finalized);
      setInfo("Prescription finalized");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to finalize prescription");
    } finally {
      setSaving(false);
    }
  };

  const printCurrentPrescription = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return;
    }
    const saved = await persistPrescription();
    if (!saved) {
      return;
    }
    try {
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, saved.id);
      const printed = await printPrescription(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(printed);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      setInfo("Prescription PDF opened");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open prescription PDF");
    }
  };

  const markSent = async () => {
    if (!auth.accessToken || !auth.tenantId || !consultation) {
      return;
    }
    const saved = await persistPrescription();
    if (!saved) {
      return;
    }
    setSaving(true);
    try {
      const sent = await markPrescriptionSent(auth.accessToken, auth.tenantId, saved.id);
      setPrescription(sent);
      setInfo("Prescription marked sent");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark prescription sent");
    } finally {
      setSaving(false);
    }
  };

  const updateMedicine = (localId: string, patch: Partial<MedicineRow>) => {
    setPrescriptionForm((current) => ({
      ...current,
      medicines: current.medicines.map((row) => (row.localId === localId ? { ...row, ...patch } : row)),
    }));
  };

  const updateTest = (localId: string, patch: Partial<TestRow>) => {
    setPrescriptionForm((current) => ({
      ...current,
      recommendedTests: current.recommendedTests.map((row) => (row.localId === localId ? { ...row, ...patch } : row)),
    }));
  };

  const applyMedicineTemplate = (medicine: Medicine) => {
    setPrescriptionForm((current) => {
      const nextRow = current.medicines[0];
      const patch = {
        medicineName: medicine.medicineName,
        medicineType: medicine.medicineType,
        strength: medicine.strength || "",
        dosage: medicine.defaultDosage || "",
        frequency: medicine.defaultFrequency || "",
        duration: medicine.defaultDurationDays ? `${medicine.defaultDurationDays} days` : "",
        timing: medicine.defaultTiming || null,
        instructions: medicine.defaultInstructions || "",
      };
      if (nextRow && !nextRow.medicineName.trim()) {
        return {
          ...current,
          medicines: current.medicines.map((row, index) => (index === 0 ? { ...row, ...patch } : row)),
        };
      }
      return {
        ...current,
        medicines: [
          {
            ...newMedicineRow(current.medicines.length),
            ...patch,
          },
          ...current.medicines,
        ],
      };
    });
  };

  if (loading) {
    return (
      <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!consultation) {
    return (
      <Stack spacing={2}>
        {error ? <Alert severity="error">{error}</Alert> : <Alert severity="info">Open a consultation from the queue to start the workspace.</Alert>}
        <Button variant="outlined" onClick={() => navigate("/queue")}>Back to Queue</Button>
      </Stack>
    );
  }

  const detail = patient?.patient;

  return (
    <Stack spacing={3} sx={{ pb: 8 }}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}

      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Doctor Workspace
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Patient summary, consultation notes, clinical vitals, and prescription builder.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Button variant="outlined" onClick={() => navigate("/queue")}>Back to Queue</Button>
          <Button variant="outlined" onClick={() => navigate("/patients")}>Patients</Button>
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent>
                <Stack spacing={1.5}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Patient summary</Typography>
                    <Chip size="small" label={consultation.status} color={statusColor(consultation.status)} />
                  </Box>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {detail ? `${detail.firstName} ${detail.lastName}` : consultation.patientName || consultation.patientNumber || consultation.patientId}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {detail ? `${detail.patientNumber} • ${detail.mobile}` : consultation.patientNumber || consultation.patientId}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Doctor: {consultation.doctorName || consultation.doctorUserId}
                  </Typography>
                  <Button variant="outlined" size="small" onClick={() => navigate(`/patients/${consultation.patientId}`)}>
                    Open Patient Detail
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Previous consultations</Typography>
                  {!patient?.previousConsultations?.length ? (
                    <Alert severity="info">No prior consultations found for this patient.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Date</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Diagnosis</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {patient.previousConsultations.map((row) => (
                          <TableRow key={row.id}>
                            <TableCell>{new Date(row.createdAt).toLocaleDateString()}</TableCell>
                            <TableCell><Chip size="small" label={row.status} color={statusColor(row.status)} /></TableCell>
                            <TableCell>{row.diagnosis || "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 8 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Consultation notes</Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Button variant="outlined" disabled={saving || readOnly} onClick={() => void persistConsultation()}>Save Draft</Button>
                      <Button variant="contained" disabled={saving || readOnly} onClick={() => void completeCurrentConsultation()}>Complete</Button>
                      <Button variant="outlined" color="error" disabled={saving || readOnly} onClick={() => void cancelCurrentConsultation()}>Cancel</Button>
                    </Stack>
                  </Box>

                  <TextField label="Chief complaints" value={consultationForm.chiefComplaints} onChange={(e) => setConsultationForm((c) => ({ ...c, chiefComplaints: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                  <TextField label="Symptoms" value={consultationForm.symptoms} onChange={(e) => setConsultationForm((c) => ({ ...c, symptoms: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                  <TextField label="Diagnosis" value={consultationForm.diagnosis} onChange={(e) => setConsultationForm((c) => ({ ...c, diagnosis: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                  <TextField label="Clinical notes" value={consultationForm.clinicalNotes} onChange={(e) => setConsultationForm((c) => ({ ...c, clinicalNotes: e.target.value }))} multiline minRows={3} disabled={readOnly} />
                  <TextField label="Advice" value={consultationForm.advice} onChange={(e) => setConsultationForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} disabled={readOnly} />
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Follow-up date" type="date" value={consultationForm.followUpDate} onChange={(e) => setConsultationForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="BP systolic" value={consultationForm.bloodPressureSystolic} onChange={(e) => setConsultationForm((c) => ({ ...c, bloodPressureSystolic: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="BP diastolic" value={consultationForm.bloodPressureDiastolic} onChange={(e) => setConsultationForm((c) => ({ ...c, bloodPressureDiastolic: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Pulse rate" value={consultationForm.pulseRate} onChange={(e) => setConsultationForm((c) => ({ ...c, pulseRate: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth label="Temperature" value={consultationForm.temperature} onChange={(e) => setConsultationForm((c) => ({ ...c, temperature: e.target.value }))} disabled={readOnly} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl fullWidth>
                        <InputLabel id="temp-unit-label">Unit</InputLabel>
                        <Select labelId="temp-unit-label" label="Unit" value={consultationForm.temperatureUnit} onChange={(e) => setConsultationForm((c) => ({ ...c, temperatureUnit: String(e.target.value) as ConsultationFormState["temperatureUnit"] }))} disabled={readOnly}>
                          <MenuItem value="">Select</MenuItem>
                          <MenuItem value="CELSIUS">Celsius</MenuItem>
                          <MenuItem value="FAHRENHEIT">Fahrenheit</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Weight (kg)" value={consultationForm.weightKg} onChange={(e) => setConsultationForm((c) => ({ ...c, weightKg: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Height (cm)" value={consultationForm.heightCm} onChange={(e) => setConsultationForm((c) => ({ ...c, heightCm: e.target.value }))} disabled={readOnly} /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="SpO2" value={consultationForm.spo2} onChange={(e) => setConsultationForm((c) => ({ ...c, spo2: e.target.value }))} disabled={readOnly} /></Grid>
                  </Grid>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Prescription builder</Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Button variant="outlined" disabled={saving} onClick={() => void persistPrescription()}>Save Draft</Button>
                      <Button variant="contained" disabled={saving} onClick={() => void finalizeCurrentPrescription()}>Finalize</Button>
                      <Button variant="outlined" disabled={saving} onClick={() => void printCurrentPrescription()}>Print</Button>
                      <Button variant="outlined" disabled={saving} onClick={() => void markSent()}>WhatsApp</Button>
                      <Button variant="outlined" disabled={saving} onClick={() => void markSent()}>Email</Button>
                    </Stack>
                  </Box>

                  <TextField label="Diagnosis snapshot" value={prescriptionForm.diagnosisSnapshot} onChange={(e) => setPrescriptionForm((c) => ({ ...c, diagnosisSnapshot: e.target.value }))} multiline minRows={2} />
                  <TextField label="Advice" value={prescriptionForm.advice} onChange={(e) => setPrescriptionForm((c) => ({ ...c, advice: e.target.value }))} multiline minRows={2} />
                  <TextField label="Follow-up date" type="date" value={prescriptionForm.followUpDate} onChange={(e) => setPrescriptionForm((c) => ({ ...c, followUpDate: e.target.value }))} InputLabelProps={{ shrink: true }} />

                  <Card variant="outlined">
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                            Medicine catalogue
                          </Typography>
                          <Chip label={`${medicineCatalog.length} items`} variant="outlined" />
                        </Box>
                        {medicineCatalog.length === 0 ? (
                          <Alert severity="info">No medicine catalogue entries are available yet.</Alert>
                        ) : (
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {medicineCatalog.slice(0, 12).map((medicine) => (
                              <Chip
                                key={medicine.id}
                                clickable
                                label={medicine.medicineName}
                                onClick={() => applyMedicineTemplate(medicine)}
                                variant="outlined"
                              />
                            ))}
                          </Stack>
                        )}
                      </Stack>
                    </CardContent>
                  </Card>

                  <Divider />
                  <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Medicines</Typography>
                    <Button startIcon={<AddRoundedIcon />} onClick={() => setPrescriptionForm((c) => ({ ...c, medicines: [...c.medicines, newMedicineRow(c.medicines.length)] }))}>Add medicine</Button>
                  </Box>
                  <Stack spacing={2}>
                    {prescriptionForm.medicines.map((row, index) => (
                      <Card key={row.localId} variant="outlined">
                        <CardContent>
                          <Stack spacing={2}>
                            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Medicine {index + 1}</Typography>
                              <IconButton onClick={() => setPrescriptionForm((c) => ({ ...c, medicines: c.medicines.filter((item) => item.localId !== row.localId) }))} size="small">
                                <DeleteOutlineRoundedIcon fontSize="small" />
                              </IconButton>
                            </Box>
                            <Grid container spacing={2}>
                              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Medicine name" value={row.medicineName} onChange={(e) => updateMedicine(row.localId, { medicineName: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 3 }}>
                                <FormControl fullWidth>
                                  <InputLabel id={`medicine-type-${row.localId}`}>Type</InputLabel>
                                  <Select labelId={`medicine-type-${row.localId}`} label="Type" value={row.medicineType || ""} onChange={(e) => updateMedicine(row.localId, { medicineType: (String(e.target.value) || null) as MedicineType | null })}>
                                    <MenuItem value="">Select</MenuItem>
                                    <MenuItem value="TABLET">Tablet</MenuItem>
                                    <MenuItem value="SYRUP">Syrup</MenuItem>
                                    <MenuItem value="INJECTION">Injection</MenuItem>
                                    <MenuItem value="DROP">Drop</MenuItem>
                                    <MenuItem value="OINTMENT">Ointment</MenuItem>
                                    <MenuItem value="CAPSULE">Capsule</MenuItem>
                                    <MenuItem value="OTHER">Other</MenuItem>
                                  </Select>
                                </FormControl>
                              </Grid>
                              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Strength" value={row.strength || ""} onChange={(e) => updateMedicine(row.localId, { strength: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Dosage" value={row.dosage} onChange={(e) => updateMedicine(row.localId, { dosage: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Frequency" value={row.frequency} onChange={(e) => updateMedicine(row.localId, { frequency: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Duration" value={row.duration} onChange={(e) => updateMedicine(row.localId, { duration: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}>
                                <FormControl fullWidth>
                                  <InputLabel id={`timing-${row.localId}`}>Timing</InputLabel>
                                  <Select labelId={`timing-${row.localId}`} label="Timing" value={row.timing || ""} onChange={(e) => updateMedicine(row.localId, { timing: (String(e.target.value) || null) as Timing | null })}>
                                    <MenuItem value="">Select</MenuItem>
                                    {TIMINGS.map((chip) => <MenuItem key={chip.value} value={chip.value}>{chip.label}</MenuItem>)}
                                  </Select>
                                </FormControl>
                              </Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Instructions" value={row.instructions || ""} onChange={(e) => updateMedicine(row.localId, { instructions: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="Sort order" value={row.sortOrder ?? index + 1} onChange={(e) => updateMedicine(row.localId, { sortOrder: Number(e.target.value) })} /></Grid>
                            </Grid>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              {FREQUENCIES.map((chip) => <Chip key={chip} clickable label={chip} onClick={() => updateMedicine(row.localId, { frequency: chip })} />)}
                            </Stack>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              {DURATIONS.map((chip) => <Chip key={chip} clickable label={chip} onClick={() => updateMedicine(row.localId, { duration: chip })} />)}
                            </Stack>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              {TIMINGS.map((chip) => <Chip key={chip.value} clickable label={chip.label} onClick={() => updateMedicine(row.localId, { timing: chip.value })} />)}
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>

                  <Divider />
                  <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Recommended tests</Typography>
                    <Button startIcon={<AddRoundedIcon />} onClick={() => setPrescriptionForm((c) => ({ ...c, recommendedTests: [...c.recommendedTests, newTestRow(c.recommendedTests.length)] }))}>Add test</Button>
                  </Box>
                  <Stack spacing={2}>
                    {prescriptionForm.recommendedTests.map((row, index) => (
                      <Card key={row.localId} variant="outlined">
                        <CardContent>
                          <Stack spacing={2}>
                            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Test {index + 1}</Typography>
                              <IconButton onClick={() => setPrescriptionForm((c) => ({ ...c, recommendedTests: c.recommendedTests.filter((item) => item.localId !== row.localId) }))} size="small">
                                <DeleteOutlineRoundedIcon fontSize="small" />
                              </IconButton>
                            </Box>
                            <Grid container spacing={2}>
                              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Test name" value={row.testName} onChange={(e) => updateTest(row.localId, { testName: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Instructions" value={row.instructions || ""} onChange={(e) => updateTest(row.localId, { instructions: e.target.value })} /></Grid>
                              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth type="number" label="Sort order" value={row.sortOrder ?? index + 1} onChange={(e) => updateTest(row.localId, { sortOrder: Number(e.target.value) })} /></Grid>
                            </Grid>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      <Card sx={{ position: "sticky", bottom: 16, zIndex: 1, border: "1px solid", borderColor: "divider" }}>
        <CardContent sx={{ py: 1.5 }}>
          <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent="space-between" alignItems="center">
            <Typography variant="body2" color="text.secondary">
              {consultation.status === "DRAFT" ? "Draft consultation ready for doctor review." : "Consultation is locked."}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Button variant="outlined" disabled={saving || readOnly} onClick={() => void persistConsultation()}>Save Draft</Button>
              <Button variant="contained" disabled={saving || readOnly} onClick={() => void finalizeCurrentPrescription()}>Finalize Rx</Button>
              <Button variant="outlined" disabled={saving} onClick={() => void printCurrentPrescription()}>Print</Button>
              <Button variant="outlined" disabled={saving} onClick={() => void markSent()}>WhatsApp</Button>
              <Button variant="outlined" disabled={saving} onClick={() => void markSent()}>Email</Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
