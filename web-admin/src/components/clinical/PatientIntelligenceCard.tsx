import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Grid,
  Stack,
  Typography,
} from "@mui/material";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import TimelineRoundedIcon from "@mui/icons-material/TimelineRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import HealingRoundedIcon from "@mui/icons-material/HealingRounded";
import { type ClinicalContextResponse } from "../../api/clinicApi";

function compactText(value: string | null | undefined, max = 110) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
}

function SectionBox({
  title,
  icon,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={0.75}>
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ display: "grid", placeItems: "center", width: 24, height: 24, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main" }}>
            {icon}
          </Box>
          <Typography variant="subtitle2" sx={{ fontWeight: 900, lineHeight: 1.2 }}>
            {title}
          </Typography>
        </Stack>
        {children}
      </Stack>
    </Box>
  );
}

export function PatientIntelligenceCard({
  context,
  loading = false,
  error = null,
}: {
  context: ClinicalContextResponse | null;
  loading?: boolean;
  error?: string | null;
}) {
  const [expanded, setExpanded] = React.useState(false);
  const snapshot = context?.patientSummary;
  const medicationAlerts = context?.medicationHistory.alerts || [];
  const intakeSummary = context?.intakeSummary;
  const timelineEvents = context?.timelineSummary.events || [];

  return (
    <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto" }}>
      <CardContent sx={{ p: 0.95, "&:last-child": { pb: 0.95 } }}>
        <Stack spacing={0.85}>
          <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
            <Box sx={{ minWidth: 0 }}>
              <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                  Patient Intelligence
                </Typography>
                <Chip size="small" variant="outlined" label={loading ? "Loading" : `${timelineEvents.length} events`} />
              </Stack>
              <Typography variant="caption" color="text.secondary">
                Longitudinal patient context. Not AI generated.
              </Typography>
            </Box>
            <Button type="button" size="small" variant="outlined" onClick={() => setExpanded((current) => !current)}>
              {expanded ? "Hide" : "Show"}
            </Button>
          </Stack>

          {error ? <Alert severity="warning" sx={{ py: 0.5 }}>{error}</Alert> : null}

          <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
            <Stack spacing={0.5}>
              <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
                {context?.aiSummary || snapshot?.patientName || "Patient context will appear here once loaded."}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {context?.aiPromptContext ? compactText(context.aiPromptContext, 180) : "Clinical context is reused by AIVA across consultation actions."}
              </Typography>
            </Stack>
          </Box>

          {intakeSummary ? (
            <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "primary.50" }}>
              <Stack spacing={0.5}>
                <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
                  <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                    Intake
                  </Typography>
                  <Chip size="small" color={intakeSummary.complete ? "success" : "default"} label={intakeSummary.complete ? "Intake complete" : "Pending intake"} />
                </Stack>
                {intakeSummary.chiefComplaint ? <Typography variant="caption" color="text.secondary">Chief complaint: {intakeSummary.chiefComplaint}</Typography> : null}
                {intakeSummary.latestVitals ? (
                  <Typography variant="caption" color="text.secondary">
                    Latest vitals: {[
                      intakeSummary.latestVitals.bloodPressureSystolic != null && intakeSummary.latestVitals.bloodPressureDiastolic != null ? `${intakeSummary.latestVitals.bloodPressureSystolic}/${intakeSummary.latestVitals.bloodPressureDiastolic} BP` : null,
                      intakeSummary.latestVitals.pulseRate != null ? `${intakeSummary.latestVitals.pulseRate} pulse` : null,
                      intakeSummary.latestVitals.spo2 != null ? `${intakeSummary.latestVitals.spo2}% SpO2` : null,
                      intakeSummary.latestVitals.temperature != null ? `${intakeSummary.latestVitals.temperature}${intakeSummary.latestVitals.temperatureUnit === "FAHRENHEIT" ? "F" : "C"} temp` : null,
                    ].filter(Boolean).join(" • ")}
                  </Typography>
                ) : null}
                {intakeSummary.abnormalVitalsAlerts?.length ? (
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {intakeSummary.abnormalVitalsAlerts.slice(0, 3).map((item) => <Chip key={item} size="small" color="warning" variant="outlined" label={compactText(item, 40)} />)}
                  </Stack>
                ) : null}
                {intakeSummary.uploadedDocumentSummary ? <Typography variant="caption" color="text.secondary">Reports: {compactText(intakeSummary.uploadedDocumentSummary, 180)}</Typography> : null}
                {intakeSummary.notes ? <Typography variant="caption" color="text.secondary">Notes: {compactText(intakeSummary.notes, 180)}</Typography> : null}
              </Stack>
            </Box>
          ) : null}

          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <Stack spacing={0.75}>
              <Grid container spacing={0.75}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.35}>
                      <Typography variant="body2" sx={{ fontWeight: 800 }}>
                        {snapshot?.patientName || "-"}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {snapshot?.ageYears != null ? `${snapshot.ageYears}y` : "Age -"} {snapshot?.gender ? `• ${snapshot.gender}` : ""}
                      </Typography>
                      {snapshot?.chronicConditions ? <Typography variant="caption" color="text.secondary">Chronic: {snapshot.chronicConditions}</Typography> : null}
                      {snapshot?.allergies ? <Typography variant="caption" color="text.secondary">Allergies: {snapshot.allergies}</Typography> : null}
                      {snapshot?.currentMedications?.length ? (
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {snapshot.currentMedications.slice(0, 4).map((item) => <Chip key={item} size="small" variant="outlined" label={item} />)}
                        </Stack>
                      ) : null}
                    </Stack>
                  </SectionBox>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <SectionBox title="Previous Diagnosis" icon={<TimelineRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.35}>
                      <Typography variant="body2" sx={{ fontWeight: 800 }}>
                        {context?.diagnosisHistory.lastVisitDiagnosis || "No diagnosis recorded"}
                      </Typography>
                      {context?.diagnosisHistory.previousDiagnoses?.length ? (
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {context.diagnosisHistory.previousDiagnoses.slice(0, 4).map((item) => <Chip key={item} size="small" variant="outlined" label={item} />)}
                        </Stack>
                      ) : null}
                    </Stack>
                  </SectionBox>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <SectionBox title="Medication Alerts" icon={<MedicationRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.5}>
                      {medicationAlerts.length ? (
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {medicationAlerts.slice(0, 4).map((item) => <Chip key={item} size="small" color="warning" variant="outlined" label={compactText(item, 42)} />)}
                        </Stack>
                      ) : (
                        <Typography variant="caption" color="text.secondary">No medication alerts detected.</Typography>
                      )}
                      {context?.medicationHistory.activeMedicines?.length ? (
                        <Typography variant="caption" color="text.secondary">
                          Active: {context.medicationHistory.activeMedicines.slice(0, 4).join(", ")}
                        </Typography>
                      ) : null}
                    </Stack>
                  </SectionBox>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <SectionBox title="Recent Lab Alerts" icon={<ScienceRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.5}>
                      {context?.labIntelligence.latestLabReport ? <Typography variant="body2" sx={{ fontWeight: 800 }}>{context.labIntelligence.latestLabReport}</Typography> : <Typography variant="caption" color="text.secondary">No recent lab report found.</Typography>}
                      {context?.labIntelligence.abnormalValues?.length ? (
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {context.labIntelligence.abnormalValues.slice(0, 3).map((item) => <Chip key={item} size="small" color="warning" variant="outlined" label={compactText(item, 42)} />)}
                        </Stack>
                      ) : null}
                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        {context?.labIntelligence.lastHbA1c ? <Chip size="small" variant="outlined" label={`HbA1c: ${compactText(context.labIntelligence.lastHbA1c, 28)}`} /> : null}
                        {context?.labIntelligence.lastCbc ? <Chip size="small" variant="outlined" label={`CBC: ${compactText(context.labIntelligence.lastCbc, 28)}`} /> : null}
                        {context?.labIntelligence.lastCreatinine ? <Chip size="small" variant="outlined" label={`Creatinine: ${compactText(context.labIntelligence.lastCreatinine, 28)}`} /> : null}
                      </Stack>
                      {context?.labIntelligence.pendingInvestigations?.length ? (
                        <Typography variant="caption" color="text.secondary">
                          Pending: {context.labIntelligence.pendingInvestigations.slice(0, 3).join(", ")}
                        </Typography>
                      ) : null}
                    </Stack>
                  </SectionBox>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <SectionBox title="Recent Uploaded Reports" icon={<DescriptionRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.5}>
                      {context?.documentIntelligence.recentReports?.length ? (
                        <Typography variant="caption" color="text.secondary">
                          Recent: {context.documentIntelligence.recentReports.slice(0, 3).join(" • ")}
                        </Typography>
                      ) : (
                        <Typography variant="caption" color="text.secondary">No recent uploaded reports.</Typography>
                      )}
                      {context?.documentIntelligence.radiology?.length ? <Typography variant="caption" color="text.secondary">Radiology: {context.documentIntelligence.radiology.slice(0, 3).join(" • ")}</Typography> : null}
                      {context?.documentIntelligence.referrals?.length ? <Typography variant="caption" color="text.secondary">Referrals: {context.documentIntelligence.referrals.slice(0, 3).join(" • ")}</Typography> : null}
                      {context?.documentIntelligence.dischargeSummaries?.length ? <Typography variant="caption" color="text.secondary">Discharge: {context.documentIntelligence.dischargeSummaries.slice(0, 3).join(" • ")}</Typography> : null}
                    </Stack>
                  </SectionBox>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <SectionBox title="Timeline Summary" icon={<TimelineRoundedIcon fontSize="inherit" />}>
                    <Stack spacing={0.5}>
                      {context?.timelineSummary.recentImportantEvents ? (
                        <Typography variant="caption" color="text.secondary">
                          {context.timelineSummary.recentImportantEvents}
                        </Typography>
                      ) : null}
                      {timelineEvents.length ? (
                        <Stack spacing={0.4}>
                          {timelineEvents.slice(0, 4).map((event) => (
                            <Box key={`${event.occurredOn || "unknown"}-${event.title}-${event.type}`} sx={{ display: "flex", gap: 1, alignItems: "flex-start" }}>
                              <Chip size="small" variant="outlined" label={event.occurredOn || "-"} />
                              <Box sx={{ minWidth: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 800 }}>{event.title}</Typography>
                                {event.detail ? <Typography variant="caption" color="text.secondary">{event.detail}</Typography> : null}
                              </Box>
                            </Box>
                          ))}
                        </Stack>
                      ) : null}
                    </Stack>
                  </SectionBox>
                </Grid>
              </Grid>
            </Stack>
          </Collapse>
        </Stack>
      </CardContent>
    </Card>
  );
}
