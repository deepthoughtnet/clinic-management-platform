import * as React from "react";
import {
  Alert,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import {
  generateClinicalReasoning,
  getClinicalContext,
  getConsultations,
  type ClinicalContextResponse,
  type ClinicalReasoningResult,
  type ClinicalReasoningResponse,
  type Consultation,
} from "../../api/clinicApi";

const ADMIN_ROLES = new Set(["PLATFORM_ADMIN", "CLINIC_ADMIN", "TENANT_ADMIN", "ADMIN"]);

function confidenceTone(confidence: string | null | undefined) {
  const normalized = (confidence || "").toUpperCase();
  if (normalized.includes("VERY_HIGH")) return "success" as const;
  if (normalized.includes("HIGH")) return "primary" as const;
  if (normalized.includes("MEDIUM")) return "warning" as const;
  if (normalized.includes("LOW")) return "error" as const;
  return "default" as const;
}

function chipLabel(item: { label?: string | null; valueText?: string | null; valueUnit?: string | null; verificationStatus?: string | null; observedOn?: string | null }) {
  const value = [item.valueText, item.valueUnit].filter(Boolean).join(" ").trim();
  return `${item.label || "-" }${value ? `: ${value}` : ""}${item.verificationStatus ? ` · ${item.verificationStatus}` : ""}${item.observedOn ? ` · ${item.observedOn}` : ""}`;
}

function sourceLabel(item: { sourceType?: string | null; sourceTitle?: string | null; source?: string | null; observationDate?: string | null; verificationStatus?: string | null }) {
  const parts = [item.sourceType, item.sourceTitle || item.source, item.observationDate, item.verificationStatus].filter((value) => value && String(value).trim());
  return parts.length ? ` (${parts.join(" · ")})` : "";
}

function safeJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function downloadText(filename: string, text: string) {
  const blob = new Blob([text], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export default function ReasoningTestConsolePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [consultations, setConsultations] = React.useState<Consultation[]>([]);
  const [loadingConsultations, setLoadingConsultations] = React.useState(false);
  const [loadingContext, setLoadingContext] = React.useState(false);
  const [generating, setGenerating] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [selectedConsultationId, setSelectedConsultationId] = React.useState<string>("");
  const [clinicalContext, setClinicalContext] = React.useState<ClinicalContextResponse | null>(null);
  const [reasoningResult, setReasoningResult] = React.useState<ClinicalReasoningResult | null>(null);
  const [reasoningResponse, setReasoningResponse] = React.useState<ClinicalReasoningResponse | null>(null);
  const [rawOpen, setRawOpen] = React.useState(false);
  const [debugMode, setDebugMode] = React.useState(true);

  const hasConsoleRole = React.useMemo(() => auth.rolesUpper.some((role) => ADMIN_ROLES.has(role)), [auth.rolesUpper]);
  const isPlatformAdminWithoutTenant = auth.rolesUpper.includes("PLATFORM_ADMIN") && !auth.tenantId;
  const isUnauthorized = !hasConsoleRole;

  React.useEffect(() => {
    let cancelled = false;
    async function loadConsultations() {
      if (!auth.accessToken || !auth.tenantId || !hasConsoleRole) {
        setLoadingConsultations(false);
        return;
      }
      setLoadingConsultations(true);
      setError(null);
      try {
        const rows = await getConsultations(auth.accessToken, auth.tenantId);
        if (cancelled) return;
        setConsultations(rows);
        setSelectedConsultationId((current) => current || rows[0]?.id || "");
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load consultations");
      } finally {
        if (!cancelled) setLoadingConsultations(false);
      }
    }
    void loadConsultations();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, hasConsoleRole]);

  const filteredConsultations = React.useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return consultations;
    return consultations.filter((row) => {
      const haystack = [
        row.id,
        row.patientName,
        row.patientNumber,
        row.patientId,
        row.doctorName,
        row.doctorUserId,
        row.chiefComplaints,
        row.symptoms,
        row.diagnosis,
      ].filter(Boolean).join(" ").toLowerCase();
      return haystack.includes(q);
    });
  }, [consultations, search]);

  const selectedConsultation = React.useMemo(() => consultations.find((row) => row.id === selectedConsultationId) || null, [consultations, selectedConsultationId]);
  const selectedConsultationVisible = React.useMemo(() => filteredConsultations.some((row) => row.id === selectedConsultationId), [filteredConsultations, selectedConsultationId]);
  const latestVitals = clinicalContext?.intakeSummary?.latestVitals || null;
  const isMockReasoning = (reasoningResult?.provider || "").toUpperCase() === "MOCK";

  const refreshClinicalContext = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedConsultation) return;
    setLoadingContext(true);
    setError(null);
    try {
      const context = await getClinicalContext(auth.accessToken, auth.tenantId, selectedConsultation.patientId, selectedConsultation.id);
      setClinicalContext(context);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load clinical context");
    } finally {
      setLoadingContext(false);
    }
  }, [auth.accessToken, auth.tenantId, selectedConsultation]);

  React.useEffect(() => {
    if (!selectedConsultation) {
      setClinicalContext(null);
      setReasoningResult(null);
      setReasoningResponse(null);
      return;
    }
    void refreshClinicalContext();
  }, [selectedConsultation?.id, refreshClinicalContext]);

  const generateReasoning = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedConsultation) return;
    if (!clinicalContext) {
      await refreshClinicalContext();
    }
    setGenerating(true);
    setError(null);
    try {
      const response = await generateClinicalReasoning(auth.accessToken, auth.tenantId, selectedConsultation.id, {
        patientId: selectedConsultation.patientId,
        chiefComplaint: selectedConsultation.chiefComplaints || clinicalContext?.intakeSummary?.chiefComplaint || null,
        symptoms: selectedConsultation.symptoms || null,
        findings: clinicalContext?.labIntelligence.latestLabReport || selectedConsultation.diagnosis || null,
        vitals: latestVitals
          ? [
              latestVitals.bloodPressureSystolic != null && latestVitals.bloodPressureDiastolic != null ? `BP ${latestVitals.bloodPressureSystolic}/${latestVitals.bloodPressureDiastolic}` : null,
              latestVitals.pulseRate != null ? `Pulse ${latestVitals.pulseRate}` : null,
              latestVitals.temperature != null ? `Temp ${latestVitals.temperature}${latestVitals.temperatureUnit ? ` ${latestVitals.temperatureUnit}` : ""}` : null,
              latestVitals.spo2 != null ? `SpO2 ${latestVitals.spo2}` : null,
              latestVitals.randomBloodSugar != null ? `RBS ${latestVitals.randomBloodSugar}` : null,
            ].filter(Boolean).join(", ")
          : null,
        diagnosis: selectedConsultation.diagnosis || null,
        advice: selectedConsultation.advice || null,
        notes: selectedConsultation.clinicalNotes || null,
        currentPrescriptionDraft: null,
        labOrdersSummary: clinicalContext?.labIntelligence.latestLabReport || null,
      }, { debug: debugMode });
      setReasoningResponse(response);
      setReasoningResult(response.reasoningResult || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Clinical reasoning generation failed");
    } finally {
      setGenerating(false);
    }
  }, [auth.accessToken, auth.tenantId, clinicalContext, debugMode, refreshClinicalContext, selectedConsultation, latestVitals]);

  const copyJson = React.useCallback(async () => {
    const payload = {
      consultation: selectedConsultation,
      clinicalContext,
      reasoningResponse,
    };
    await navigator.clipboard.writeText(safeJson(payload));
  }, [clinicalContext, reasoningResponse, selectedConsultation]);

  const downloadJson = React.useCallback(() => {
    const payload = {
      consultation: selectedConsultation,
      clinicalContext,
      reasoningResponse,
    };
    downloadText(`reasoning-console-${selectedConsultation?.id || "export"}.json`, safeJson(payload));
  }, [clinicalContext, reasoningResponse, selectedConsultation]);

  if (isPlatformAdminWithoutTenant) {
    return <Alert severity="info">Select a tenant before opening the AI Reasoning Test Console.</Alert>;
  }

  if (isUnauthorized) {
    return <Alert severity="error">You do not have access to the AI Reasoning Console.</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
        <Stack spacing={0.5}>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>AI Reasoning Test Console</Typography>
          <Typography variant="body2" color="text.secondary">
            Validate ClinicalReasoningEngine from the UI without Postman or curl.
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button variant="outlined" onClick={() => void refreshClinicalContext()} disabled={!selectedConsultation || loadingContext}>
            {loadingContext ? "Refreshing..." : "Refresh Clinical Context"}
          </Button>
          <Button variant="contained" onClick={() => void generateReasoning()} disabled={!selectedConsultation || generating}>
            {generating ? "Generating..." : "Generate Clinical Reasoning"}
          </Button>
          <Button variant="outlined" onClick={() => void copyJson()} disabled={!selectedConsultation}>
            Copy JSON
          </Button>
          <Button variant="outlined" onClick={() => downloadJson()} disabled={!selectedConsultation}>
            Download JSON
          </Button>
          <Button variant="outlined" color={debugMode ? "primary" : "inherit"} onClick={() => setDebugMode((value) => !value)}>
            {debugMode ? "Debug On" : "Debug Off"}
          </Button>
        </Stack>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField
                label="Search consultations"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                fullWidth
                placeholder="Patient name, consultation id, doctor, complaint..."
              />
              <FormControl fullWidth>
                <InputLabel id="reasoning-consultation-label">Consultation</InputLabel>
                <Select
                  labelId="reasoning-consultation-label"
                  label="Consultation"
                  value={selectedConsultationVisible ? selectedConsultationId : ""}
                  onChange={(e) => setSelectedConsultationId(String(e.target.value))}
                >
                  <MenuItem value="">
                    {filteredConsultations.length ? "Select consultation" : "No consultations match search"}
                  </MenuItem>
                  {filteredConsultations.map((row) => (
                    <MenuItem key={row.id} value={row.id}>
                      {row.patientName || row.patientNumber || row.patientId} · {row.chiefComplaints || row.diagnosis || row.id}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
            <Divider />
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} alignItems="center">
              <Box sx={{ flex: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                  {selectedConsultation ? (selectedConsultation.patientName || selectedConsultation.patientNumber || selectedConsultation.patientId) : "No consultation selected"}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {selectedConsultation ? `${selectedConsultation.id} · ${selectedConsultation.status} · ${selectedConsultation.doctorName || selectedConsultation.doctorUserId}` : "Search and select a consultation to load context."}
                </Typography>
              </Box>
              <Button variant="text" onClick={() => navigate(`/consultations/${selectedConsultationId}`)} disabled={!selectedConsultationId}>
                Open Consultation
              </Button>
            </Stack>
            {loadingConsultations ? <Stack direction="row" spacing={1} alignItems="center"><CircularProgress size={18} /><Typography variant="body2" color="text.secondary">Loading consultations...</Typography></Stack> : null}
          </Stack>
        </CardContent>
      </Card>

      <Stack spacing={2}>
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 850 }}>Clinical Context Snapshot</Typography>
              {!clinicalContext ? (
                <Alert severity="info">No clinical context loaded yet.</Alert>
              ) : (
                <Stack spacing={1.2}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={`Patient: ${clinicalContext.patientSummary.patientName || clinicalContext.patientId}`} />
                    <Chip size="small" label={`Age: ${clinicalContext.patientSummary.ageYears ?? "-"}`} />
                    <Chip size="small" label={`Gender: ${clinicalContext.patientSummary.gender || "-"}`} />
                    <Chip size="small" label={`Last visit: ${clinicalContext.patientSummary.lastConsultationDate || "-"}`} />
                  </Stack>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Chief complaint</Typography>
                    <Typography variant="body2">{clinicalContext.intakeSummary?.chiefComplaint || "-"}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Vitals</Typography>
                    <Typography variant="body2">
                      {latestVitals
                        ? [
                            latestVitals.bloodPressureSystolic != null && latestVitals.bloodPressureDiastolic != null ? `BP ${latestVitals.bloodPressureSystolic}/${latestVitals.bloodPressureDiastolic}` : null,
                            latestVitals.pulseRate != null ? `Pulse ${latestVitals.pulseRate}` : null,
                            latestVitals.temperature != null ? `Temp ${latestVitals.temperature}${latestVitals.temperatureUnit ? ` ${latestVitals.temperatureUnit}` : ""}` : null,
                            latestVitals.spo2 != null ? `SpO2 ${latestVitals.spo2}` : null,
                            latestVitals.randomBloodSugar != null ? `RBS ${latestVitals.randomBloodSugar}` : null,
                          ].filter(Boolean).join(" · ")
                        : "-"}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Longitudinal memory</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                      {clinicalContext.longitudinalMemory?.knownConditions?.length ? clinicalContext.longitudinalMemory.knownConditions.map((item, index) => <Chip key={`cond-${index}`} size="small" color="primary" variant="outlined" label={chipLabel(item)} />) : <Typography variant="body2" color="text.secondary">No known conditions returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Recent reports</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                      {clinicalContext.documentIntelligence?.recentReports?.length ? clinicalContext.documentIntelligence.recentReports.map((report) => <Chip key={report} size="small" label={report} />) : <Typography variant="body2" color="text.secondary">No recent reports.</Typography>}
                    </Stack>
                  </Box>
                </Stack>
              )}
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 850 }}>Reasoning Result</Typography>
              {!reasoningResult ? (
                <Alert severity="info">Generate reasoning to inspect the structured result.</Alert>
              ) : (
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" color={confidenceTone(reasoningResult.confidence)} label={`Confidence: ${reasoningResult.confidence}`} />
                    <Chip size="small" variant="outlined" color={isMockReasoning ? "warning" : "default"} label={`Provider: ${reasoningResult.provider || "-"}`} />
                    <Chip size="small" variant="outlined" color={isMockReasoning ? "warning" : "default"} label={`Model: ${reasoningResult.model || "-"}`} />
                    <Chip size="small" variant="outlined" label={`Parse: ${reasoningResult.metadata.parseStatus}`} />
                    <Chip size="small" variant="outlined" label={`Fallback: ${reasoningResult.metadata.fallbackUsed ? "Yes" : "No"}`} />
                  </Stack>
                  {isMockReasoning ? (
                    <Alert severity="warning">
                      Mock AI provider is active. Clinical reasoning quality cannot be validated.
                    </Alert>
                  ) : null}
                  {reasoningResult.metadata.parseStatus !== "VALID" ? (
                    <Alert severity={reasoningResult.metadata.parseStatus === "TRUNCATED" ? "warning" : "error"}>
                      {reasoningResult.metadata.errorMessage || (reasoningResult.metadata.parseStatus === "TRUNCATED"
                        ? "AI reasoning response was truncated. Please retry."
                        : "AI reasoning could not be generated. Please retry.")}
                    </Alert>
                  ) : null}
                  <Box>
                    <Typography variant="caption" color="text.secondary">Primary diagnosis</Typography>
                    <Typography variant="body1" sx={{ fontWeight: 800 }}>{reasoningResult.primaryDiagnosis?.name || "-"}</Typography>
                    <Typography variant="body2" color="text.secondary">{reasoningResult.primaryDiagnosis?.whyConsidered || reasoningResult.reasoningSummary || "-"}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Differentials</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                      {reasoningResult.differentialDiagnoses.length ? reasoningResult.differentialDiagnoses.map((item, index) => (
                        <Chip key={`${item.name || index}`} size="small" variant="outlined" label={`${item.name || "-"}${item.confidence != null ? ` (${Math.round(item.confidence * 100)}%)` : ""}`} />
                      )) : <Typography variant="body2" color="text.secondary">No differentials returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Evidence</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {reasoningResult.supportingEvidence.length ? reasoningResult.supportingEvidence.map((item, index) => <Typography key={`support-${index}`} variant="body2">• {item.text || "-"}{sourceLabel(item)}</Typography>) : <Typography variant="body2" color="text.secondary">No evidence returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Missing information</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {reasoningResult.missingInformation.length ? reasoningResult.missingInformation.map((item, index) => <Typography key={`missing-${index}`} variant="body2">• {item.name || item.whyItMatters || "-"}</Typography>) : <Typography variant="body2" color="text.secondary">No missing information returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Red flags</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {reasoningResult.redFlags.length ? reasoningResult.redFlags.map((item, index) => <Typography key={`red-${index}`} variant="body2">• {item.name || item.reason || "-"}{sourceLabel(item)}</Typography>) : <Typography variant="body2" color="text.secondary">No red flags returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Recommended tests</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {reasoningResult.recommendedTests.length ? reasoningResult.recommendedTests.map((item, index) => <Typography key={`test-${index}`} variant="body2">• {item.name || item.reason || "-"}{item.actionType ? ` · ${item.actionType.replaceAll("_", " ")}` : ""}{sourceLabel(item)}</Typography>) : <Typography variant="body2" color="text.secondary">No test suggestions returned.</Typography>}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Safety notes</Typography>
                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                      {reasoningResult.safetyNotes.length ? reasoningResult.safetyNotes.map((item, index) => <Typography key={`note-${index}`} variant="body2">• {item.message || item.action || "-"}{item.actionType ? ` · ${item.actionType.replaceAll("_", " ")}` : ""}{sourceLabel(item)}</Typography>) : <Typography variant="body2" color="text.secondary">No safety notes returned.</Typography>}
                    </Stack>
                  </Box>
                </Stack>
              )}
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 850 }}>Technical Metadata</Typography>
              {!reasoningResult ? (
                <Alert severity="info">Run reasoning to view metadata.</Alert>
              ) : (
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip size="small" color={debugMode ? "primary" : "default"} label={`Debug: ${debugMode ? "On" : "Off"}`} />
                  <Chip size="small" label={`Request ID: ${reasoningResult.metadata.requestId || "-"}`} />
                  <Chip size="small" label={`Correlation ID: ${reasoningResult.metadata.correlationId || "-"}`} />
                  <Chip size="small" label={`Provider: ${reasoningResult.metadata.provider || "-"}`} />
                  <Chip size="small" label={`Model: ${reasoningResult.metadata.model || "-"}`} />
                  <Chip size="small" label={`Prompt: ${reasoningResult.metadata.promptVersion || "-"}`} />
                  <Chip size="small" label={`Context: ${reasoningResult.metadata.contextVersion || "-"}`} />
                  <Chip size="small" label={`Latency: ${reasoningResult.metadata.latencyMs != null ? `${reasoningResult.metadata.latencyMs} ms` : "-"}`} />
                  <Chip size="small" label={`Parse: ${reasoningResult.metadata.parseStatus}`} />
                  <Chip size="small" label={`Fallback: ${reasoningResult.metadata.fallbackUsed ? "Yes" : "No"}`} />
                  <Chip size="small" label={`Finish: ${reasoningResult.metadata.finishReason || "-"}`} />
                  <Chip size="small" label={`Raw chars: ${reasoningResult.metadata.rawChars ?? "-"}`} />
                  <Chip size="small" label={`Tokens: ${Object.keys(reasoningResult.metadata.tokens || {}).length ? "Available" : "-"}`} />
                </Stack>
              )}
            </Stack>
          </CardContent>
        </Card>

        <Accordion expanded={rawOpen} onChange={(_, value) => setRawOpen(value)}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="h6" sx={{ fontWeight: 850 }}>Raw JSON</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box component="pre" sx={{ m: 0, p: 2, borderRadius: 2, bgcolor: "background.paper", overflow: "auto", fontSize: 12 }}>
              {safeJson({ consultation: selectedConsultation, clinicalContext, reasoningResponse })}
            </Box>
          </AccordionDetails>
        </Accordion>
      </Stack>
    </Stack>
  );
}
