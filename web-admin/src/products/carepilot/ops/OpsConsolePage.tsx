import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
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
  getCarePilotExecutionTimeline,
  getCarePilotLeadAnalyticsSummary,
  listCarePilotEngagementCohort,
  listCarePilotCampaigns,
  listCarePilotOpsFailedExecutions,
  resendCarePilotExecution,
  retryCarePilotExecution,
  type CarePilotCampaign,
  type CarePilotChannelType,
  type CarePilotEngagementProfile,
  type CarePilotExecution,
  type CarePilotExecutionStatus,
  type CarePilotExecutionTimeline,
  type CarePilotLeadAnalyticsSummary,
} from "../../../api/clinicApi";

function deliveryChipColor(status: string) {
  if (status === "READ" || status === "DELIVERED" || status === "SENT") return "success" as const;
  if (status === "FAILED" || status === "BOUNCED" || status === "UNDELIVERED") return "error" as const;
  if (status === "QUEUED") return "info" as const;
  return "default" as const;
}

export default function OpsConsolePage() {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);

  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [rows, setRows] = React.useState<CarePilotExecution[]>([]);

  const [startDate, setStartDate] = React.useState("");
  const [endDate, setEndDate] = React.useState("");
  const [campaignId, setCampaignId] = React.useState("");
  const [channel, setChannel] = React.useState<CarePilotChannelType | "">("");
  const [status, setStatus] = React.useState<CarePilotExecutionStatus | "">("");
  const [providerName, setProviderName] = React.useState("");
  const [reminderWindow, setReminderWindow] = React.useState<"" | "H24" | "H2">("");
  const [retryableOnly, setRetryableOnly] = React.useState(false);

  const [timelineOpen, setTimelineOpen] = React.useState(false);
  const [timelineLoading, setTimelineLoading] = React.useState(false);
  const [timeline, setTimeline] = React.useState<CarePilotExecutionTimeline | null>(null);
  const [highRiskRows, setHighRiskRows] = React.useState<CarePilotEngagementProfile[]>([]);
  const [inactiveRows, setInactiveRows] = React.useState<CarePilotEngagementProfile[]>([]);
  const [leadOps, setLeadOps] = React.useState<CarePilotLeadAnalyticsSummary | null>(null);

  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("AUDITOR") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, failedRows, highRisk, inactive, leadSummary] = await Promise.all([
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        listCarePilotOpsFailedExecutions(auth.accessToken, auth.tenantId, {
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          campaignId: campaignId || undefined,
          channel: channel || undefined,
          status: status || undefined,
          providerName: providerName || undefined,
          retryableOnly,
        }),
        listCarePilotEngagementCohort(auth.accessToken, auth.tenantId, "HIGH_RISK_PATIENTS", { limit: 8 }),
        listCarePilotEngagementCohort(auth.accessToken, auth.tenantId, "INACTIVE_PATIENTS", { limit: 8 }),
        getCarePilotLeadAnalyticsSummary(auth.accessToken, auth.tenantId),
      ]);
      setCampaigns(campaignRows);
      setRows(reminderWindow ? failedRows.filter((row) => row.reminderWindow === reminderWindow) : failedRows);
      setHighRiskRows(highRisk.rows);
      setInactiveRows(inactive.rows);
      setLeadOps(leadSummary);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load ops queue");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, startDate, endDate, campaignId, channel, status, providerName, retryableOnly, reminderWindow]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const openTimeline = async (executionId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setTimelineOpen(true);
    setTimelineLoading(true);
    setTimeline(null);
    try {
      setTimeline(await getCarePilotExecutionTimeline(auth.accessToken, auth.tenantId, executionId));
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Failed to load timeline");
    } finally {
      setTimelineLoading(false);
    }
  };

  const runAction = async (executionId: string, resend: boolean) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    const confirmed = window.confirm(resend ? "Resend this execution?" : "Retry this execution?");
    if (!confirmed) return;
    try {
      if (resend) await resendCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      else await retryCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      setToast(`Execution ${resend ? "resend" : "retry"} queued.`);
      await load();
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Action failed");
    }
  };

  const campaignById = React.useMemo(() => new Map(campaigns.map((c) => [c.id, c])), [campaigns]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use CarePilot ops console.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to CarePilot ops console.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1.5 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot Ops Console</Typography>
          <Typography variant="body2" color="text.secondary">Failed execution queue, retries, resends, and delivery attempt drill-down.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      {!canMutate ? <Alert severity="info">Read-only mode: retry/resend actions are restricted.</Alert> : null}

      <Card><CardContent>
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="Start" value={startDate} onChange={(e) => setStartDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 6, md: 2 }}><TextField fullWidth type="date" label="End" value={endDate} onChange={(e) => setEndDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={campaignId} label="Campaign" onChange={(e) => setCampaignId(String(e.target.value))}><MenuItem value="">All</MenuItem>{campaigns.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Channel</InputLabel><Select value={channel} label="Channel" onChange={(e) => setChannel(String(e.target.value) as CarePilotChannelType | "")}><MenuItem value="">All</MenuItem><MenuItem value="EMAIL">EMAIL</MenuItem><MenuItem value="SMS">SMS</MenuItem><MenuItem value="WHATSAPP">WHATSAPP</MenuItem><MenuItem value="IN_APP">IN_APP</MenuItem><MenuItem value="APP_NOTIFICATION">APP_NOTIFICATION</MenuItem></Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Status</InputLabel><Select value={status} label="Status" onChange={(e) => setStatus(String(e.target.value) as CarePilotExecutionStatus | "")}><MenuItem value="">All</MenuItem><MenuItem value="FAILED">FAILED</MenuItem><MenuItem value="DEAD_LETTER">DEAD_LETTER</MenuItem></Select></FormControl></Grid>
          <Grid size={{ xs: 6, md: 2 }}><FormControl fullWidth><InputLabel>Reminder</InputLabel><Select value={reminderWindow} label="Reminder" onChange={(e) => setReminderWindow(String(e.target.value) as "" | "H24" | "H2")}><MenuItem value="">All</MenuItem><MenuItem value="H24">24-hour</MenuItem><MenuItem value="H2">2-hour</MenuItem></Select></FormControl></Grid>
          <Grid size={{ xs: 12, md: 1 }}><Button fullWidth variant="contained" onClick={() => void load()}>Apply</Button></Grid>
          <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Provider" value={providerName} onChange={(e) => setProviderName(e.target.value)} /></Grid>
          <Grid size={{ xs: 12, md: 4 }}><Button variant={retryableOnly ? "contained" : "outlined"} onClick={() => setRetryableOnly((v) => !v)}>{retryableOnly ? "Retryable Only: ON" : "Retryable Only: OFF"}</Button></Grid>
        </Grid>
      </CardContent></Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Follow-ups Due</Typography><Typography variant="h5">{leadOps?.followUpsDue ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Stale Leads</Typography><Typography variant="h5">{leadOps?.staleLeads ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">High Priority Leads</Typography><Typography variant="h5">{leadOps?.highPriorityActiveLeads ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Lead Conversion</Typography><Typography variant="h5">{(leadOps?.conversionRate ?? 0).toFixed(1)}%</Typography></CardContent></Card></Grid>
      </Grid>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card><CardContent>
          <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Failed Execution Queue</Typography>
          {rows.length === 0 ? <Alert severity="info">No failed executions found for the selected filters.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Campaign</TableCell><TableCell>Reminder</TableCell><TableCell>Appointment Ref</TableCell><TableCell>Patient/Entity</TableCell><TableCell>Channel</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Retry Count</TableCell><TableCell>Last Attempt</TableCell><TableCell>Next Retry</TableCell><TableCell>Failure Reason</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
              <TableBody>{rows.map((row) => <TableRow key={row.id}><TableCell>{campaignById.get(row.campaignId)?.name || row.campaignId}</TableCell><TableCell>{row.reminderWindow ? <Chip size="small" variant="outlined" label={row.reminderWindow} /> : "-"}</TableCell><TableCell>{row.sourceReferenceId || "-"}</TableCell><TableCell>{row.recipientPatientId || "-"}</TableCell><TableCell>{row.channelType}</TableCell><TableCell>{row.providerName || "-"}</TableCell><TableCell><Chip size="small" color={row.status === "DEAD_LETTER" ? "error" : "warning"} label={row.status} /></TableCell><TableCell>{row.attemptCount}</TableCell><TableCell>{row.lastAttemptAt ? new Date(row.lastAttemptAt).toLocaleString() : "-"}</TableCell><TableCell>{row.nextAttemptAt ? new Date(row.nextAttemptAt).toLocaleString() : "-"}</TableCell><TableCell>{row.failureReason || row.lastError || "-"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void openTimeline(row.id)}>View Attempts</Button>{canMutate ? <Button size="small" onClick={() => void runAction(row.id, false)}>Retry</Button> : null}{canMutate ? <Button size="small" onClick={() => void runAction(row.id, true)}>Resend</Button> : null}</Stack></TableCell></TableRow>)}</TableBody>
            </Table>
          )}
        </CardContent></Card>
      ) : null}
      {!loading ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>High-Risk Patients</Typography>
              {highRiskRows.length === 0 ? <Alert severity="info">No high-risk patients currently identified.</Alert> : (
                <Table size="small"><TableHead><TableRow><TableCell>Patient</TableCell><TableCell>Score</TableCell><TableCell>Reason</TableCell><TableCell>Suggested Campaign</TableCell></TableRow></TableHead><TableBody>
                  {highRiskRows.map((row) => <TableRow key={row.patientId}><TableCell>{row.patientName}</TableCell><TableCell>{row.engagementScore}</TableCell><TableCell>{row.riskReasons[0] || "-"}</TableCell><TableCell>{row.suggestedCampaignType}</TableCell></TableRow>)}
                </TableBody></Table>
              )}
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Inactive Patients</Typography>
              {inactiveRows.length === 0 ? <Alert severity="info">No inactive patients currently identified.</Alert> : (
                <Table size="small"><TableHead><TableRow><TableCell>Patient</TableCell><TableCell>Score</TableCell><TableCell>Last Visit</TableCell><TableCell>Suggested Campaign</TableCell></TableRow></TableHead><TableBody>
                  {inactiveRows.map((row) => <TableRow key={row.patientId}><TableCell>{row.patientName}</TableCell><TableCell>{row.engagementScore}</TableCell><TableCell>{row.lastConsultationAt || row.lastAppointmentAt || "-"}</TableCell><TableCell>{row.suggestedCampaignType}</TableCell></TableRow>)}
                </TableBody></Table>
              )}
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={timelineOpen} onClose={() => setTimelineOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Execution Timeline</DialogTitle>
        <DialogContent>
          {timelineLoading ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
          {!timelineLoading && timeline ? (
            <Stack spacing={2}>
              <Card variant="outlined"><CardContent><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Execution Metadata</Typography>
                <Grid container spacing={1}><Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Execution: {timeline.execution.id}</Typography></Grid><Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Scheduled: {new Date(timeline.execution.scheduledAt).toLocaleString()}</Typography></Grid><Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Executed: {timeline.execution.executedAt ? new Date(timeline.execution.executedAt).toLocaleString() : "-"}</Typography></Grid><Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Retry Count: {timeline.execution.attemptCount}</Typography></Grid></Grid>
              </CardContent></Card>
              <Card variant="outlined"><CardContent><Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Attempts</Typography>
                {timeline.deliveryAttempts.length === 0 ? <Alert severity="info">No attempts recorded yet.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>#</TableCell><TableCell>Provider</TableCell><TableCell>Channel</TableCell><TableCell>Status</TableCell><TableCell>Error Code</TableCell><TableCell>Error Message</TableCell><TableCell>Attempted At</TableCell></TableRow></TableHead><TableBody>
                    {timeline.deliveryAttempts.map((a) => <TableRow key={a.id}><TableCell>{a.attemptNumber}</TableCell><TableCell>{a.providerName || "-"}</TableCell><TableCell>{a.channelType}</TableCell><TableCell><Chip size="small" color={deliveryChipColor(a.deliveryStatus)} label={a.deliveryStatus} /></TableCell><TableCell>{a.errorCode || "-"}</TableCell><TableCell>{a.errorMessage || "-"}</TableCell><TableCell>{a.attemptedAt ? new Date(a.attemptedAt).toLocaleString() : "-"}</TableCell></TableRow>)}
                  </TableBody></Table>
                )}
              </CardContent></Card>
              <Card variant="outlined"><CardContent><Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Provider Delivery Events</Typography>
                {timeline.deliveryEvents.length === 0 ? <Alert severity="info">No provider webhook events recorded yet.</Alert> : (
                  <Table size="small"><TableHead><TableRow><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Provider</TableCell><TableCell>Message ID</TableCell><TableCell>External</TableCell><TableCell>Event Time</TableCell></TableRow></TableHead><TableBody>
                    {timeline.deliveryEvents.map((event, idx) => (
                      <TableRow key={`${event.id || "event"}-${idx}`}>
                        <TableCell>{event.eventType}</TableCell>
                        <TableCell><Chip size="small" color={deliveryChipColor(event.internalStatus)} label={event.internalStatus} /></TableCell>
                        <TableCell>{event.providerName || "-"}</TableCell>
                        <TableCell>{event.providerMessageId || "-"}</TableCell>
                        <TableCell>{event.externalStatus || "-"}</TableCell>
                        <TableCell>{event.eventTimestamp ? new Date(event.eventTimestamp).toLocaleString() : "-"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody></Table>
                )}
              </CardContent></Card>
            </Stack>
          ) : null}
        </DialogContent>
      </Dialog>

      <Snackbar open={Boolean(toast)} autoHideDuration={3000} onClose={() => setToast(null)} message={toast || ""} />
    </Stack>
  );
}
