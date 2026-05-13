import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
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
import { useAuth } from "../../../auth/useAuth";
import {
  getCarePilotAnalyticsSummary,
  getCarePilotEngagementOverview,
  listCarePilotCampaigns,
  type CarePilotAnalyticsSummary,
  type CarePilotCampaign,
  type CarePilotEngagementOverview,
} from "../../../api/clinicApi";

function kpi(title: string, value: string | number) {
  return (
    <Card>
      <CardContent>
        <Typography variant="caption" color="text.secondary">{title}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 900 }}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

export default function AnalyticsPage() {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [summary, setSummary] = React.useState<CarePilotAnalyticsSummary | null>(null);
  const [engagement, setEngagement] = React.useState<CarePilotEngagementOverview | null>(null);

  const [startDate, setStartDate] = React.useState("");
  const [endDate, setEndDate] = React.useState("");
  const [campaignId, setCampaignId] = React.useState("");

  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("AUDITOR") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, analytics, engagementOverview] = await Promise.all([
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        getCarePilotAnalyticsSummary(auth.accessToken, auth.tenantId, {
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          campaignId: campaignId || undefined,
        }),
        getCarePilotEngagementOverview(auth.accessToken, auth.tenantId),
      ]);
      setCampaigns(campaignRows);
      setSummary(analytics);
      setEngagement(engagementOverview);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, startDate, endDate, campaignId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to view CarePilot analytics.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to CarePilot analytics.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1.5 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot Analytics</Typography>
          <Typography variant="body2" color="text.secondary">Operational visibility across campaign execution and delivery health.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="End" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <FormControl fullWidth>
                <InputLabel>Campaign</InputLabel>
                <Select value={campaignId} label="Campaign" onChange={(e) => setCampaignId(String(e.target.value))}>
                  <MenuItem value="">All</MenuItem>
                  {campaigns.map((campaign) => <MenuItem key={campaign.id} value={campaign.id}>{campaign.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}><Button fullWidth variant="contained" onClick={() => void load()}>Apply</Button></Grid>
          </Grid>
        </CardContent>
      </Card>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 180, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && summary ? (
        <>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Total Executions", summary.totalExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Successful", summary.successfulExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Failed", summary.failedExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Pending", summary.pendingExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Retrying", summary.retryingExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Delivered", summary.deliveredExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Read", summary.readExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Undelivered", summary.undeliveredExecutions + summary.bouncedExecutions)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Success Rate", `${summary.successRate.toFixed(1)}%`)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Failure Rate", `${summary.failureRate.toFixed(1)}%`)}</Grid>
            <Grid size={{ xs: 6, md: 3 }}>{kpi("Active Campaigns", summary.activeCampaigns)}</Grid>
            {engagement ? (
              <>
                <Grid size={{ xs: 6, md: 3 }}>{kpi("Inactive Patients", engagement.inactivePatientsCount)}</Grid>
                <Grid size={{ xs: 6, md: 3 }}>{kpi("High Risk Patients", engagement.highRiskPatientsCount)}</Grid>
                <Grid size={{ xs: 6, md: 3 }}>{kpi("Refill Risk Patients", engagement.refillRiskCount)}</Grid>
                <Grid size={{ xs: 6, md: 3 }}>{kpi("Vaccination Risk", engagement.overdueVaccinationCount)}</Grid>
              </>
            ) : null}
          </Grid>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, lg: 6 }}>
              <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Executions by Status</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {Object.entries(summary.executionsByStatus).map(([key, value]) => <Chip key={key} label={`${key}: ${value}`} />)}
                </Stack>
              </CardContent></Card>
            </Grid>
            <Grid size={{ xs: 12, lg: 6 }}>
              <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Executions by Channel</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {Object.entries(summary.executionsByChannel).map(([key, value]) => <Chip key={key} color="primary" variant="outlined" label={`${key}: ${value}`} />)}
                </Stack>
              </CardContent></Card>
            </Grid>
          </Grid>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Campaign Performance</Typography>
              {summary.executionsByCampaign.length === 0 ? <Alert severity="info">No campaign execution data for this range.</Alert> : (
                <Table size="small"><TableHead><TableRow><TableCell>Campaign</TableCell><TableCell>Total</TableCell><TableCell>Success</TableCell><TableCell>Failed</TableCell><TableCell>Success Rate</TableCell></TableRow></TableHead>
                  <TableBody>{summary.executionsByCampaign.map((row) => <TableRow key={row.campaignId}><TableCell>{row.campaignName}</TableCell><TableCell>{row.totalExecutions}</TableCell><TableCell>{row.successfulExecutions}</TableCell><TableCell>{row.failedExecutions}</TableCell><TableCell>{row.successRate.toFixed(1)}%</TableCell></TableRow>)}</TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  );
}
