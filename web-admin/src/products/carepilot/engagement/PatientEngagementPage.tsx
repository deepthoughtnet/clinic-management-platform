import * as React from "react";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import {
  getCarePilotEngagementOverview,
  listCarePilotEngagementCohort,
  type CarePilotEngagementCohort,
  type CarePilotEngagementOverview,
  type CarePilotEngagementProfile,
  type CarePilotRiskLevel,
} from "../../../api/clinicApi";

const COHORT_OPTIONS: Array<{ value: CarePilotEngagementCohort; label: string }> = [
  { value: "HIGH_RISK_PATIENTS", label: "High Risk" },
  { value: "INACTIVE_PATIENTS", label: "Inactive" },
  { value: "REFILL_RISK_PATIENTS", label: "Refill Risk" },
  { value: "FOLLOW_UP_OVERDUE_PATIENTS", label: "Follow-up Risk" },
  { value: "VACCINATION_OVERDUE_PATIENTS", label: "Vaccination Risk" },
];

function riskColor(level: CarePilotRiskLevel) {
  if (level === "HIGH") return "error" as const;
  if (level === "MEDIUM") return "warning" as const;
  return "success" as const;
}

function kpi(label: string, value: number | string) {
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">{label}</Typography>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

export default function PatientEngagementPage() {
  const auth = useAuth();
  const [overview, setOverview] = React.useState<CarePilotEngagementOverview | null>(null);
  const [rows, setRows] = React.useState<CarePilotEngagementProfile[]>([]);
  const [cohort, setCohort] = React.useState<CarePilotEngagementCohort>("HIGH_RISK_PATIENTS");
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [overviewRes, cohortRes] = await Promise.all([
        getCarePilotEngagementOverview(auth.accessToken, auth.tenantId),
        listCarePilotEngagementCohort(auth.accessToken, auth.tenantId, cohort, { limit: 100 }),
      ]);
      setOverview(overviewRes);
      setRows(cohortRes.rows);
    } catch (e) {
      setError((e as Error).message || "Failed to load patient engagement");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, cohort]);

  React.useEffect(() => {
    void load();
  }, [load]);

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>Patient Engagement</Typography>
        <Typography variant="body2" color="text.secondary">
          Rule-based engagement scoring and risk cohorts for operational targeting.
        </Typography>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      {overview ? (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("High", overview.highEngagementCount)}</Grid>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("Low/Critical", overview.lowEngagementCount + overview.criticalEngagementCount)}</Grid>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("Inactive", overview.inactivePatientsCount)}</Grid>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("High Risk", overview.highRiskPatientsCount)}</Grid>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("Refill Risk", overview.refillRiskCount)}</Grid>
          <Grid size={{ xs: 6, md: 2 }}>{kpi("Follow-up Risk", overview.followUpRiskCount)}</Grid>
        </Grid>
      ) : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ sm: "center" }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Cohort</Typography>
          <Select size="small" value={cohort} onChange={(e) => setCohort(e.target.value as CarePilotEngagementCohort)}>
            {COHORT_OPTIONS.map((option) => (
              <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
            ))}
          </Select>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Patient</TableCell>
              <TableCell>Score</TableCell>
              <TableCell>Level</TableCell>
              <TableCell>Risks</TableCell>
              <TableCell>Last Visit</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>Suggested Campaign</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.patientId} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.patientEmail || row.patientMobile || "No contact"}</Typography>
                </TableCell>
                <TableCell>{row.engagementScore}</TableCell>
                <TableCell>
                  <Chip size="small" label={row.engagementLevel} color={row.engagementLevel === "HIGH" ? "success" : row.engagementLevel === "MEDIUM" ? "warning" : "error"} />
                </TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    <Chip size="small" label={`Inactive ${row.inactiveRisk}`} color={riskColor(row.inactiveRisk)} />
                    <Chip size="small" label={`No-show ${row.noShowRisk}`} color={riskColor(row.noShowRisk)} />
                    <Chip size="small" label={`Refill ${row.refillRisk}`} color={riskColor(row.refillRisk)} />
                  </Stack>
                </TableCell>
                <TableCell>{row.lastConsultationAt || row.lastAppointmentAt || "-"}</TableCell>
                <TableCell>{row.riskReasons[0] || "-"}</TableCell>
                <TableCell>{row.suggestedCampaignType}</TableCell>
              </TableRow>
            ))}
            {!loading && rows.length === 0 ? (
              <TableRow><TableCell colSpan={7}><Alert severity="info">No patients in selected cohort.</Alert></TableCell></TableRow>
            ) : null}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}
