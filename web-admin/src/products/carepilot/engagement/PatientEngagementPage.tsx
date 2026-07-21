import * as React from "react";
import {
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  Button,
  Chip,
  Grid,
  CircularProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { ENGAGE_ANALYTICS_VIEW } from "../../../auth/permissions";
import {
  getCarePilotEngagementOverview,
  listCarePilotCampaigns,
  listCarePilotEngagementCohort,
  listCarePilotEngagementProfiles,
  type CarePilotCampaign,
  type CarePilotCampaignType,
  type CarePilotEngagementCohort,
  type CarePilotEngagementOverview,
  type CarePilotEngagementProfile,
  type CarePilotEngagementSelection,
  type CarePilotRiskLevel,
} from "../../../api/clinicApi";
import { campaignTypeLabel } from "../campaigns/campaignLabels";
import { formatCarePilotDateTime } from "../shared/carepilotFormatting";
import { canOpenLinkedPatient } from "../shared/patientNavigation";
import { useCarePilotTenantTimezone } from "../shared/useCarePilotTenantTimezone";

const COHORT_OPTIONS: Array<{ value: CarePilotEngagementCohort; label: string }> = [
  { value: "HIGH_RISK_PATIENTS", label: "High-Risk Patients" },
  { value: "INACTIVE_PATIENTS", label: "Inactive Patients" },
  { value: "REFILL_RISK_PATIENTS", label: "Refill Risk" },
  { value: "FOLLOW_UP_OVERDUE_PATIENTS", label: "Follow-up Overdue" },
  { value: "VACCINATION_OVERDUE_PATIENTS", label: "Vaccination Due/Risk" },
];

function riskColor(level: CarePilotRiskLevel) {
  if (level === "HIGH") return "error" as const;
  if (level === "MEDIUM") return "warning" as const;
  return "success" as const;
}

function engagementColor(level: string) {
  if (level === "HIGH") return "success" as const;
  if (level === "MEDIUM") return "info" as const;
  if (level === "LOW") return "warning" as const;
  return "error" as const;
}

function engagementLabel(level: string) {
  const map: Record<string, string> = {
    HIGH: "High Engagement",
    MEDIUM: "Medium Engagement",
    LOW: "Low Engagement",
    CRITICAL: "Critical Engagement",
  };
  return map[level] || level;
}

function formatLocalDate(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Kolkata",
  }).format(date);
}

function formatRisk(value: CarePilotRiskLevel) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function campaignTypeHumanLabel(value: string) {
  return campaignTypeLabel(value as CarePilotCampaignType);
}

function cohortHelper(value: CarePilotEngagementCohort) {
  const map: Record<CarePilotEngagementCohort, string> = {
    HIGH_RISK_PATIENTS: "Patients with one or more high-risk signals.",
    INACTIVE_PATIENTS: "Patients with no recent appointment or consultation activity.",
    HIGH_NO_SHOW_RISK: "Patients with repeated missed appointments.",
    OVERDUE_BILL_PATIENTS: "Patients with overdue balances.",
    REFILL_RISK_PATIENTS: "Patients with refill risk prescriptions.",
    VACCINATION_OVERDUE_PATIENTS: "Patients with overdue vaccinations.",
    HIGH_ENGAGEMENT_PATIENTS: "Patients with strong recent engagement.",
    LOW_ENGAGEMENT_PATIENTS: "Patients with low or critical engagement.",
    FOLLOW_UP_OVERDUE_PATIENTS: "Patients with overdue follow-up activity.",
  };
  return map[value];
}

function campaignReferenceText(campaign: { campaignReference?: string | null }) {
  return campaign.campaignReference || "Unknown reference";
}

function patientCountLabel(count: number) {
  return `${count} patient${count === 1 ? "" : "s"}`;
}

const DEFAULT_COHORT: CarePilotEngagementCohort = "HIGH_RISK_PATIENTS";

type CohortCardDefinition = {
  value: CarePilotEngagementCohort;
  title: string;
  helper: string;
};

const ENGAGEMENT_LEVEL_CARDS: Array<{ value: CarePilotEngagementSelection; title: string; helper: string }> = [
  { value: "ALL", title: "All Scored Patients", helper: "Every scored patient in this tenant." },
  { value: "HIGH", title: "High Engagement", helper: "Patients with strong recent engagement." },
  { value: "MEDIUM", title: "Medium Engagement", helper: "Patients with moderate recent engagement." },
  { value: "LOW", title: "Low Engagement", helper: "Patients with limited recent engagement." },
  { value: "CRITICAL", title: "Critical Engagement", helper: "Patients needing immediate outreach." },
];

const ACTIONABLE_COHORT_CARDS: CohortCardDefinition[] = [
  { value: "INACTIVE_PATIENTS", title: "Inactive Patients", helper: "No recent appointment or consultation activity." },
  { value: "HIGH_RISK_PATIENTS", title: "High-Risk Patients", helper: "Patients with one or more high-risk signals." },
  { value: "REFILL_RISK_PATIENTS", title: "Refill Risk", helper: "Patients likely to run out of medication soon." },
  { value: "FOLLOW_UP_OVERDUE_PATIENTS", title: "Follow-up Overdue", helper: "Patients with overdue follow-up activity." },
  { value: "VACCINATION_OVERDUE_PATIENTS", title: "Vaccination Due/Risk", helper: "Patients with overdue vaccinations." },
];

function CohortSelectCard({
  definition,
  count,
  selected,
  onClick,
}: {
  definition: CohortCardDefinition;
  count: number;
  selected: boolean;
  onClick: (value: CarePilotEngagementCohort) => void;
}) {
  return (
    <Card
      variant="outlined"
      sx={{
        height: "100%",
        borderColor: selected ? "primary.main" : "divider",
        boxShadow: selected ? 3 : 0,
      }}
    >
      <CardActionArea
        onClick={() => onClick(definition.value)}
        sx={{ height: "100%", alignItems: "stretch" }}
      >
        <CardContent>
          <Stack direction="row" alignItems="start" justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{definition.title}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{definition.helper}</Typography>
            </Box>
            <Chip size="small" color={selected ? "primary" : "default"} label={count} />
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

function LevelSelectCard({
  definition,
  count,
  selected,
  onClick,
}: {
  definition: { value: CarePilotEngagementSelection; title: string; helper: string };
  count: number;
  selected: boolean;
  onClick: (value: CarePilotEngagementSelection) => void;
}) {
  return (
    <Card
      variant="outlined"
      sx={{
        height: "100%",
        borderColor: selected ? "primary.main" : "divider",
        boxShadow: selected ? 3 : 0,
        bgcolor: selected ? "action.selected" : "background.paper",
      }}
    >
      <CardActionArea
        onClick={() => onClick(definition.value)}
        sx={{ height: "100%", alignItems: "stretch" }}
        aria-pressed={selected}
      >
        <CardContent>
          <Stack direction="row" alignItems="start" justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{definition.title}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{definition.helper}</Typography>
            </Box>
            <Chip size="small" color={selected ? "primary" : "default"} label={count} />
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

export default function PatientEngagementPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const canOpenPatient = canOpenLinkedPatient(auth);
  const canView = auth.hasPermission(ENGAGE_ANALYTICS_VIEW);
  const { clinicTimeZone } = useCarePilotTenantTimezone(auth.accessToken, auth.tenantId);

  const parsedLevel = searchParams.get("level");
  const selectedLevel: CarePilotEngagementSelection = parsedLevel && ["ALL", "HIGH", "MEDIUM", "LOW", "CRITICAL"].includes(parsedLevel)
    ? (parsedLevel as CarePilotEngagementSelection)
    : "ALL";
  const parsedCohort = searchParams.get("cohort") as CarePilotEngagementCohort | null;
  const selectedCohort = COHORT_OPTIONS.some((option) => option.value === parsedCohort) ? parsedCohort! : DEFAULT_COHORT;

  const [overview, setOverview] = React.useState<CarePilotEngagementOverview | null>(null);
  const [levelRows, setLevelRows] = React.useState<CarePilotEngagementProfile[]>([]);
  const [cohortRows, setCohortRows] = React.useState<CarePilotEngagementProfile[]>([]);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const requestSeq = React.useRef(0);

  React.useEffect(() => {
    const next = new URLSearchParams(searchParams);
    let changed = false;
    if (!next.get("level") || !["ALL", "HIGH", "MEDIUM", "LOW", "CRITICAL"].includes(next.get("level") || "")) {
      next.set("level", "ALL");
      changed = true;
    }
    if (searchParams.get("cohort") !== selectedCohort) {
      next.set("cohort", selectedCohort);
      changed = true;
    }
    if (changed) {
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, selectedCohort, setSearchParams]);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      return;
    }
    const requestId = ++requestSeq.current;
    setLoading(true);
    setError(null);
    try {
      const [overviewResult, levelResult, cohortResult, campaignResult] = await Promise.allSettled([
        getCarePilotEngagementOverview(auth.accessToken, auth.tenantId),
        listCarePilotEngagementProfiles(auth.accessToken, auth.tenantId, { level: selectedLevel, limit: 2000 }),
        listCarePilotEngagementCohort(auth.accessToken, auth.tenantId, selectedCohort, { limit: 200 }),
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
      ]);
      if (requestId !== requestSeq.current) return;

      if (overviewResult.status === "fulfilled") setOverview(overviewResult.value);
      if (levelResult.status === "fulfilled") setLevelRows(levelResult.value.rows);
      else setLevelRows([]);
      if (cohortResult.status === "fulfilled") setCohortRows(cohortResult.value.rows);
      else setCohortRows([]);
      if (campaignResult.status === "fulfilled") setCampaigns(campaignResult.value);

      const firstError = [overviewResult, levelResult, cohortResult].find((result) => result.status === "rejected");
      if (firstError) {
        setError(firstError.reason instanceof Error ? firstError.reason.message : "Failed to load patient engagement");
      }
    } finally {
      if (requestId === requestSeq.current) setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, selectedCohort, selectedLevel]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const eligibleCampaigns = React.useMemo(
    () => campaigns.filter((campaign) => campaign.status === "APPROVED" || campaign.status === "ACTIVE"),
    [campaigns],
  );

  const levelCount = levelRows.length;
  const cohortCount = cohortRows.length;

  const selectedCohortTitle = React.useMemo(
    () => COHORT_OPTIONS.find((option) => option.value === selectedCohort)?.label || "Patients",
    [selectedCohort],
  );

  const selectedLevelTitle = React.useMemo(
    () => ENGAGEMENT_LEVEL_CARDS.find((option) => option.value === selectedLevel)?.title || "All Scored Patients",
    [selectedLevel],
  );

  const suggestedCampaignLabel = React.useCallback((row: CarePilotEngagementProfile) => {
    const match = eligibleCampaigns.find((campaign) => campaign.campaignType === row.suggestedCampaignType);
    if (match) {
      return `${match.name} · ${campaignReferenceText(match)}`;
    }
    return `${campaignTypeHumanLabel(row.suggestedCampaignType)} (no eligible campaign available)`;
  }, [eligibleCampaigns]);

  const engagementHelper = React.useMemo(() => {
    if (selectedLevel === "ALL") return "All scored patients in the current tenant.";
    if (selectedLevel === "HIGH") return "Patients with strong recent engagement.";
    if (selectedLevel === "MEDIUM") return "Patients with moderate recent engagement.";
    if (selectedLevel === "LOW") return "Patients with limited recent engagement.";
    return "Patients needing immediate outreach.";
  }, [selectedLevel]);

  const openCohort = React.useCallback((value: CarePilotEngagementCohort) => {
    const next = new URLSearchParams(searchParams);
    next.set("cohort", value);
    setSearchParams(next);
  }, [searchParams, setSearchParams]);

  const openLevel = React.useCallback((value: CarePilotEngagementSelection) => {
    const next = new URLSearchParams(searchParams);
    next.set("level", value);
    setSearchParams(next);
  }, [searchParams, setSearchParams]);

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to view Jeevanam Engage patient engagement.</Alert>;
  }
  if (!canView) {
    return <Alert severity="error">You do not have access to Jeevanam Engage patient engagement.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>Patient Engagement</Typography>
        <Typography variant="body2" color="text.secondary">
          Rule-based engagement scoring and separate risk-based patient cohorts.
        </Typography>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Stack spacing={1.5}>
        <Typography variant="h6" sx={{ fontWeight: 800 }}>Engagement Levels</Typography>
        <Grid container spacing={1.5}>
          {ENGAGEMENT_LEVEL_CARDS.map((item) => (
            <Grid key={item.value} size={{ xs: 12, sm: 6, md: 3 }}>
              <LevelSelectCard
                definition={item}
                count={overview ? (item.value === "ALL" ? overview.totalActivePatients : item.value === "HIGH" ? overview.highEngagementCount : item.value === "MEDIUM" ? overview.mediumEngagementCount : item.value === "LOW" ? overview.lowEngagementCount : overview.criticalEngagementCount) : 0}
                selected={selectedLevel === item.value}
                onClick={openLevel}
              />
            </Grid>
          ))}
        </Grid>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }} justifyContent="space-between">
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{selectedLevelTitle}</Typography>
              <Typography variant="body2" color="text.secondary">
                {engagementHelper}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              <Chip label={patientCountLabel(levelCount)} color="primary" variant="outlined" />
              <Button type="button" variant="outlined" onClick={() => void load()} disabled={loading}>Refresh</Button>
            </Stack>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Last evaluated: {levelRows.length > 0 ? formatCarePilotDateTime(levelRows[0].generatedAt, clinicTimeZone) : "-"}
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
        {loading ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Patient</TableCell>
              <TableCell>Score</TableCell>
              <TableCell>Engagement Level</TableCell>
              <TableCell>Supporting Signals / Reason</TableCell>
              <TableCell>Last Activity / Visit</TableCell>
              <TableCell>Last Evaluated</TableCell>
              <TableCell align="right">Permitted Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {levelRows.map((row) => (
              <TableRow key={row.patientId} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.patientName}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.patientNumber}</Typography>
                </TableCell>
                <TableCell>{row.engagementScore}</TableCell>
                <TableCell>
                  <Chip size="small" label={engagementLabel(row.engagementLevel)} color={engagementColor(row.engagementLevel)} />
                </TableCell>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      <Chip size="small" label={`Inactive: ${formatRisk(row.inactiveRisk)}`} color={riskColor(row.inactiveRisk)} />
                      <Chip size="small" label={`No-show: ${formatRisk(row.noShowRisk)}`} color={riskColor(row.noShowRisk)} />
                      <Chip size="small" label={`Refill: ${formatRisk(row.refillRisk)}`} color={riskColor(row.refillRisk)} />
                      <Chip size="small" label={`Follow-up: ${formatRisk(row.followUpRisk)}`} color={riskColor(row.followUpRisk)} />
                      <Chip size="small" label={`Vaccination: ${formatRisk(row.vaccinationCompliance)}`} color={riskColor(row.vaccinationCompliance)} />
                    </Stack>
                    <Typography variant="body2">{row.riskReasons.length > 0 ? row.riskReasons.join("; ") : "No actionable risk rule triggered."}</Typography>
                  </Stack>
                </TableCell>
                <TableCell>{formatLocalDate(row.lastConsultationAt || row.lastAppointmentAt || null)}</TableCell>
                <TableCell>{formatCarePilotDateTime(row.generatedAt, clinicTimeZone)}</TableCell>
                <TableCell align="right">
                  {row.patientId && canOpenPatient ? (
                    <Button type="button" size="small" onClick={() => navigate(`/patients/${row.patientId}`)}>Open Patient</Button>
                  ) : null}
                </TableCell>
              </TableRow>
            ))}
            {!loading && levelRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7}>
                  <Alert severity="info">No {selectedLevelTitle} in the selected tenant and filters.</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Paper>

      <Stack spacing={1.5}>
        <Typography variant="h6" sx={{ fontWeight: 800 }}>Actionable Patient Cohorts</Typography>
        <Grid container spacing={1.5}>
          {ACTIONABLE_COHORT_CARDS.map((item) => (
            <Grid key={item.value} size={{ xs: 12, sm: 6, md: 4 }}>
              <CohortSelectCard
                definition={item}
                count={overview?.cohortCounts?.[item.value] ?? 0}
                selected={selectedCohort === item.value}
                onClick={openCohort}
              />
            </Grid>
          ))}
        </Grid>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }} justifyContent="space-between">
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{selectedCohortTitle} · {patientCountLabel(cohortCount)}</Typography>
              <Typography variant="body2" color="text.secondary">
                {cohortHelper(selectedCohort)}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <Chip label={patientCountLabel(cohortCount)} color="primary" variant="outlined" />
              <Button type="button" variant="outlined" onClick={() => void load()} disabled={loading}>Refresh</Button>
            </Stack>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Selected cohort count matches the current table result set for this tenant and filter state.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Last evaluated: {cohortRows.length > 0 ? formatCarePilotDateTime(cohortRows[0].generatedAt, clinicTimeZone) : "-"}
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
        {loading ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Patient</TableCell>
              <TableCell>Score</TableCell>
              <TableCell>Engagement Level</TableCell>
              <TableCell>Risk Signals</TableCell>
              <TableCell>Last Visit</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>Suggested Campaign</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {cohortRows.map((row) => (
              <TableRow key={row.patientId} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.patientName}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.patientNumber}</Typography>
                </TableCell>
                <TableCell>{row.engagementScore}</TableCell>
                <TableCell>
                  <Chip size="small" label={engagementLabel(row.engagementLevel)} color={engagementColor(row.engagementLevel)} />
                </TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={`Inactive: ${formatRisk(row.inactiveRisk)}`} color={riskColor(row.inactiveRisk)} />
                    <Chip size="small" label={`No-show: ${formatRisk(row.noShowRisk)}`} color={riskColor(row.noShowRisk)} />
                    <Chip size="small" label={`Refill: ${formatRisk(row.refillRisk)}`} color={riskColor(row.refillRisk)} />
                    <Chip size="small" label={`Follow-up: ${formatRisk(row.followUpRisk)}`} color={riskColor(row.followUpRisk)} />
                    <Chip size="small" label={`Vaccination: ${formatRisk(row.vaccinationCompliance)}`} color={riskColor(row.vaccinationCompliance)} />
                  </Stack>
                </TableCell>
                <TableCell>{formatLocalDate(row.lastConsultationAt || row.lastAppointmentAt || null)}</TableCell>
                <TableCell>{row.riskReasons.length > 0 ? row.riskReasons.join("; ") : "No actionable risk rule triggered."}</TableCell>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{suggestedCampaignLabel(row)}</Typography>
                    <Typography variant="caption" color="text.secondary">{campaignTypeHumanLabel(row.suggestedCampaignType)}</Typography>
                  </Stack>
                </TableCell>
                <TableCell align="right">
                  {row.patientId && canOpenPatient ? (
                    <Button type="button" size="small" onClick={() => navigate(`/patients/${row.patientId}`)}>Open Patient</Button>
                  ) : null}
                </TableCell>
              </TableRow>
            ))}
            {!loading && cohortRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8}>
                  <Alert severity="info">No {selectedCohortTitle} in the selected tenant and filters.</Alert>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}
