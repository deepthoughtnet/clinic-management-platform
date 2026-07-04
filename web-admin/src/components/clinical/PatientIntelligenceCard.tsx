import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Drawer,
  IconButton,
  Stack,
  Typography,
} from "@mui/material";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import TimelineRoundedIcon from "@mui/icons-material/TimelineRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import HealingRoundedIcon from "@mui/icons-material/HealingRounded";
import { type ClinicalContextResponse } from "../../api/clinicApi";

function compactText(value: string | null | undefined, max = 110) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
}

function splitCompactList(value: string | null | undefined) {
  return (value || "")
    .split(/[\n,•;|]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function CompactRow({
  label,
  value,
}: {
  label: string;
  value: string | null | undefined;
}) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="flex-start">
      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 90, lineHeight: 1.3 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.3 }} noWrap title={value || ""}>
        {value || "Not recorded"}
      </Typography>
    </Stack>
  );
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
    <Box sx={{ p: 0.85, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={0.65}>
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ display: "grid", placeItems: "center", width: 22, height: 22, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main" }}>
            {icon}
          </Box>
          <Typography variant="subtitle2" sx={{ fontWeight: 950, lineHeight: 1.2 }}>
            {title}
          </Typography>
        </Stack>
        {children}
      </Stack>
    </Box>
  );
}

function CompactChipRow({
  items,
  color = "default",
  limit = 4,
  emptyLabel = "Not recorded",
}: {
  items: string[];
  color?: "default" | "primary" | "secondary" | "warning" | "success";
  limit?: number;
  emptyLabel?: string;
}) {
  const [expanded, setExpanded] = React.useState(false);
  const visible = expanded ? items : items.slice(0, limit);

  return (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
      {visible.length ? (
        visible.map((item) => <Chip key={item} size="small" color={color} variant="outlined" label={item} />)
      ) : (
        <Typography variant="caption" color="text.secondary">
          {emptyLabel}
        </Typography>
      )}
      {items.length > limit ? (
        <Button type="button" size="small" variant="text" onClick={() => setExpanded((current) => !current)}>
          {expanded ? "Show less" : "View more"}
        </Button>
      ) : null}
    </Stack>
  );
}

function TimelineLine({
  date,
  title,
  detail,
}: {
  date: string | null | undefined;
  title: string;
  detail?: string | null;
}) {
  return (
    <Stack spacing={0.2}>
      <Stack direction="row" spacing={0.5} alignItems="center">
        <Chip size="small" variant="outlined" label={date || "-"} sx={{ height: 20, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
        <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.15 }} noWrap>
          {title}
        </Typography>
      </Stack>
      {detail ? (
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.15 }} noWrap>
          {detail}
        </Typography>
      ) : null}
    </Stack>
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
  const [detailsOpen, setDetailsOpen] = React.useState(false);
  const snapshot = context?.patientSummary;
  const medicationAlerts = context?.medicationHistory.alerts || [];
  const intakeSummary = context?.intakeSummary;
  const timelineEvents = context?.timelineSummary.events || [];
  const allergies = splitCompactList(snapshot?.allergies);
  const chronicConditions = splitCompactList(snapshot?.chronicConditions);
  const currentMedications = snapshot?.currentMedications || [];
  const previousDiagnoses = context?.diagnosisHistory.previousDiagnoses || [];
  const labAbnormalValues = context?.labIntelligence.abnormalValues || [];
  const recentReports = context?.documentIntelligence.recentReports || [];
  const radiologyReports = context?.documentIntelligence.radiology || [];
  const referrals = context?.documentIntelligence.referrals || [];
  const dischargeSummaries = context?.documentIntelligence.dischargeSummaries || [];
  const timelinePreview = timelineEvents.slice(0, 3);

  const snapshotAgeGender = snapshot?.ageYears != null || snapshot?.gender
    ? `${snapshot?.ageYears != null ? `${snapshot.ageYears}y` : ""}${snapshot?.ageYears != null && snapshot?.gender ? " • " : ""}${snapshot?.gender || ""}`
    : null;
  const intakeLabel = intakeSummary?.complete ? "Complete" : intakeSummary ? "Pending" : "Not recorded";

  return (
    <>
      <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto" }}>
        <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
          <Stack spacing={0.75}>
            <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                  <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                  <Typography variant="subtitle1" sx={{ fontWeight: 950 }}>
                    Patient Intelligence
                  </Typography>
                  <Chip size="small" variant="outlined" label={loading ? "Loading" : `${timelineEvents.length} events`} />
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Longitudinal patient context. Not AI generated.
                </Typography>
              </Box>
              <Button type="button" size="small" variant="outlined" onClick={() => setDetailsOpen(true)}>
                View Details
              </Button>
            </Stack>

            {error ? <Alert severity="warning" sx={{ py: 0.45 }}>{error}</Alert> : null}

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.4}>
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <CompactRow label="Known conditions" value={compactText(chronicConditions.slice(0, 3).join(", "), 72)} />
                <CompactRow label="Allergies" value={compactText(allergies.slice(0, 3).join(", "), 72)} />
                <CompactRow label="Current medications" value={compactText(currentMedications.slice(0, 3).join(", "), 72)} />
                <CompactRow label="Last visit" value={snapshot?.lastConsultationDate || null} />
                <CompactRow label="Intake status" value={intakeLabel} />
              </Stack>
            </SectionBox>

            <SectionBox title="Clinical Highlights" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                <CompactRow label="Previous diagnosis" value={compactText(context?.diagnosisHistory.lastVisitDiagnosis, 72)} />
                <CompactRow label="Medication alerts" value={compactText(medicationAlerts[0], 72)} />
                <CompactRow label="Recent lab alerts" value={compactText(labAbnormalValues[0], 72)} />
                <CompactRow
                  label="Recent reports"
                  value={compactText(recentReports[0] || radiologyReports[0] || referrals[0] || dischargeSummaries[0], 72) || "No uploaded reports available."}
                />
                <Stack spacing={0.35}>
                  <Typography variant="caption" color="text.secondary">
                    Timeline summary
                  </Typography>
                  {timelinePreview.length ? (
                    <Stack spacing={0.4}>
                      {timelinePreview.map((event) => (
                        <TimelineLine
                          key={`${event.occurredOn || "unknown"}-${event.title}-${event.type}`}
                          date={event.occurredOn || "-"}
                          title={event.title}
                          detail={event.detail ? compactText(event.detail, 56) : null}
                        />
                      ))}
                    </Stack>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      No previous consultations available.
                    </Typography>
                  )}
                </Stack>
              </Stack>
            </SectionBox>
          </Stack>
        </CardContent>
      </Card>

      <Drawer anchor="right" open={detailsOpen} onClose={() => setDetailsOpen(false)}>
        <Box sx={{ width: { xs: "88vw", sm: 420 }, p: 2, maxWidth: "100vw" }}>
          <Stack spacing={1.2}>
            <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 950 }}>
                  Patient Intelligence
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Expanded longitudinal context and supporting details.
                </Typography>
              </Box>
              <IconButton aria-label="Close patient intelligence drawer" onClick={() => setDetailsOpen(false)} size="small">
                <CloseRoundedIcon fontSize="small" />
              </IconButton>
            </Stack>

            {error ? <Alert severity="warning">{error}</Alert> : null}

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                <CompactRow label="Patient" value={snapshot?.patientName || null} />
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <CompactRow label="Known conditions" value={chronicConditions.join(", ") || null} />
                <CompactRow label="Allergies" value={allergies.join(", ") || null} />
                <CompactRow label="Current medications" value={currentMedications.join(", ") || null} />
                <CompactRow label="Last visit" value={snapshot?.lastConsultationDate || null} />
              </Stack>
            </SectionBox>

            <SectionBox title="Clinical Highlights" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                <CompactRow label="Previous diagnosis" value={context?.diagnosisHistory.lastVisitDiagnosis || null} />
                <CompactRow label="All diagnoses" value={previousDiagnoses.join(", ") || null} />
                <CompactRow label="Medication alerts" value={medicationAlerts.join(" • ") || null} />
                <CompactRow label="Recent lab alerts" value={labAbnormalValues.join(" • ") || null} />
                <CompactRow label="Recent reports" value={[...recentReports, ...radiologyReports, ...referrals, ...dischargeSummaries].join(" • ") || null} />
                <CompactRow label="Intake notes" value={intakeSummary?.notes || null} />
                <CompactRow label="Chief complaint" value={intakeSummary?.chiefComplaint || null} />
              </Stack>
            </SectionBox>

            <SectionBox title="Intake status" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.4}>
                <CompactRow label="Status" value={intakeLabel} />
                <CompactRow label="Recorded by" value={intakeSummary?.recordedByName || null} />
                <CompactRow label="Recorded at" value={intakeSummary?.recordedAt ? new Date(intakeSummary.recordedAt).toLocaleString() : null} />
                {intakeSummary?.latestVitals ? (
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Latest vitals
                    </Typography>
                    <CompactChipRow
                      items={[
                        intakeSummary.latestVitals.bloodPressureSystolic != null && intakeSummary.latestVitals.bloodPressureDiastolic != null ? `BP ${intakeSummary.latestVitals.bloodPressureSystolic}/${intakeSummary.latestVitals.bloodPressureDiastolic}` : null,
                        intakeSummary.latestVitals.pulseRate != null ? `Pulse ${intakeSummary.latestVitals.pulseRate}` : null,
                        intakeSummary.latestVitals.spo2 != null ? `SpO2 ${intakeSummary.latestVitals.spo2}%` : null,
                        intakeSummary.latestVitals.temperature != null ? `Temp ${intakeSummary.latestVitals.temperature}${intakeSummary.latestVitals.temperatureUnit === "FAHRENHEIT" ? "F" : "C"}` : null,
                        intakeSummary.vitalsTrendSummary ? compactText(intakeSummary.vitalsTrendSummary, 48) : null,
                      ].filter((item): item is string => Boolean(item))}
                      color="primary"
                      limit={4}
                      emptyLabel="No latest vitals yet"
                    />
                  </Stack>
                ) : null}
                {intakeSummary?.abnormalVitalsAlerts?.length ? (
                  <CompactChipRow items={intakeSummary.abnormalVitalsAlerts.map((item) => compactText(item, 42))} color="warning" limit={3} emptyLabel="No abnormal vitals" />
                ) : null}
              </Stack>
            </SectionBox>

            <SectionBox title="Timeline summary" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                {context?.timelineSummary.recentImportantEvents ? (
                  <Typography variant="caption" color="text.secondary">
                    {context.timelineSummary.recentImportantEvents}
                  </Typography>
                ) : null}
                {timelineEvents.length ? (
                  <Stack spacing={0.45}>
                    {timelineEvents.slice(0, 6).map((event) => (
                      <TimelineLine
                        key={`${event.occurredOn || "unknown"}-${event.title}-${event.type}`}
                        date={event.occurredOn || "-"}
                        title={event.title}
                        detail={event.detail ? compactText(event.detail, 72) : null}
                      />
                    ))}
                  </Stack>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    No previous consultations available.
                  </Typography>
                )}
              </Stack>
            </SectionBox>

            <Divider />

            <SectionBox title="Uploaded documents" icon={<DescriptionRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                <CompactChipRow items={recentReports.slice(0, 6)} color="default" limit={3} emptyLabel="No uploaded reports available." />
                <CompactChipRow items={radiologyReports.slice(0, 6)} color="secondary" limit={3} emptyLabel="No radiology reports available." />
                <CompactChipRow items={referrals.slice(0, 6)} color="primary" limit={3} emptyLabel="No referral letters available." />
                <CompactChipRow items={dischargeSummaries.slice(0, 6)} color="default" limit={3} emptyLabel="No discharge summaries available." />
              </Stack>
            </SectionBox>

            <SectionBox title="Lab intelligence" icon={<ScienceRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                {context?.labIntelligence.latestLabReport ? (
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                    {context.labIntelligence.latestLabReport}
                  </Typography>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    No recent lab report found. Order investigations or upload a report to track trends.
                  </Typography>
                )}
                  <CompactChipRow items={labAbnormalValues.slice(0, 6).map((item) => compactText(item, 42))} color="warning" limit={3} emptyLabel="No abnormal values detected yet." />
                <CompactChipRow
                  items={[
                    context?.labIntelligence.lastHbA1c ? `HbA1c ${compactText(context.labIntelligence.lastHbA1c, 24)}` : null,
                    context?.labIntelligence.lastCbc ? `CBC ${compactText(context.labIntelligence.lastCbc, 24)}` : null,
                    context?.labIntelligence.lastCreatinine ? `Creatinine ${compactText(context.labIntelligence.lastCreatinine, 24)}` : null,
                  ].filter((item): item is string => Boolean(item))}
                  color="primary"
                  limit={3}
                  emptyLabel="No recent trend markers."
                />
                {context?.labIntelligence.previousTrends?.length ? (
                  <Typography variant="caption" color="text.secondary">
                    Trends: {context.labIntelligence.previousTrends.slice(0, 3).join(" • ")}
                  </Typography>
                ) : null}
                {context?.labIntelligence.pendingInvestigations?.length ? (
                  <Typography variant="caption" color="text.secondary">
                    Pending: {context.labIntelligence.pendingInvestigations.slice(0, 3).join(", ")}
                  </Typography>
                ) : null}
              </Stack>
            </SectionBox>
          </Stack>
        </Box>
      </Drawer>
    </>
  );
}
