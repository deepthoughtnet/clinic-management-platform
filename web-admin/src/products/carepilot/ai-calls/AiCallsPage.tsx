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
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  Tab,
  Tabs,
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
  cancelCarePilotAiCallExecution,
  dispatchDueCarePilotAiCalls,
  getCarePilotAiCallAnalyticsSummary,
  getCarePilotAiCallSchedulerHealth,
  getCarePilotAiCallTranscript,
  listCarePilotAiCallCampaigns,
  listCarePilotAiCallEvents,
  listCarePilotAiCallExecutions,
  rescheduleCarePilotAiCallExecution,
  retryCarePilotAiCallExecution,
  suppressCarePilotAiCallExecution,
  triggerCarePilotAiCallCampaign,
  updateCarePilotAiCallCampaignStatus,
  type CarePilotAiCallCampaign,
  type CarePilotAiCallCampaignStatus,
  type CarePilotAiCallEvent,
  type CarePilotAiCallExecution,
  type CarePilotAiCallExecutionStatus,
} from "../../../api/clinicApi";

const statuses: CarePilotAiCallExecutionStatus[] = ["PENDING", "QUEUED", "DIALING", "IN_PROGRESS", "COMPLETED", "FAILED", "NO_ANSWER", "BUSY", "CANCELLED", "ESCALATED", "SKIPPED", "SUPPRESSED"];
const campaignStatuses: CarePilotAiCallCampaignStatus[] = ["DRAFT", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"];

export default function AiCallsPage() {
  const auth = useAuth();
  const canView = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("AUDITOR") || auth.rolesUpper.includes("RECEPTIONIST") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("RECEPTIONIST") || (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [tab, setTab] = React.useState<"CAMPAIGNS" | "EXECUTIONS" | "ESCALATIONS" | "ANALYTICS">("CAMPAIGNS");
  const [campaigns, setCampaigns] = React.useState<CarePilotAiCallCampaign[]>([]);
  const [executions, setExecutions] = React.useState<CarePilotAiCallExecution[]>([]);
  const [statusFilter, setStatusFilter] = React.useState<CarePilotAiCallExecutionStatus | "">("");
  const [analytics, setAnalytics] = React.useState<any>(null);
  const [schedulerHealth, setSchedulerHealth] = React.useState<any>(null);
  const [transcriptOpen, setTranscriptOpen] = React.useState(false);
  const [transcript, setTranscript] = React.useState<any>(null);
  const [eventsOpen, setEventsOpen] = React.useState(false);
  const [events, setEvents] = React.useState<CarePilotAiCallEvent[]>([]);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, executionRows, summary, health] = await Promise.all([
        listCarePilotAiCallCampaigns(auth.accessToken, auth.tenantId),
        listCarePilotAiCallExecutions(auth.accessToken, auth.tenantId, { status: statusFilter || undefined, size: 50 }),
        getCarePilotAiCallAnalyticsSummary(auth.accessToken, auth.tenantId),
        getCarePilotAiCallSchedulerHealth(auth.accessToken, auth.tenantId),
      ]);
      setCampaigns(campaignRows);
      setExecutions(executionRows.rows);
      setAnalytics(summary);
      setSchedulerHealth(health);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load AI calls data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, statusFilter]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use CarePilot AI calls.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to CarePilot AI calls.</Alert>;

  const escalations = executions.filter((e) => e.escalationRequired || e.executionStatus === "ESCALATED");

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>CarePilot AI Calls</Typography>
          <Typography variant="body2" color="text.secondary">Scheduler-driven voice queue with retries, suppression, failover, webhooks, and transcript/event visibility.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          {canMutate ? <Button variant="outlined" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await dispatchDueCarePilotAiCalls(auth.accessToken, auth.tenantId); await load(); }}>Dispatch Due</Button> : null}
          <Button variant="contained" onClick={() => void load()}>Refresh</Button>
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Calls</Typography><Typography variant="h5">{analytics?.totalCalls ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Queued</Typography><Typography variant="h5">{analytics?.queuedCalls ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Success Rate</Typography><Typography variant="h5">{analytics && analytics.totalCalls ? `${((analytics.completedCalls * 100) / analytics.totalCalls).toFixed(1)}%` : "0%"}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Escalations</Typography><Typography variant="h5">{analytics?.escalations ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Suppressed/Skipped</Typography><Typography variant="h5">{(analytics?.suppressedCalls ?? 0) + (analytics?.skippedCalls ?? 0)}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card><CardContent><Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Scheduler Health</Typography><Typography variant="body2" color="text.secondary">Enabled: {String(schedulerHealth?.enabled ?? false)} | Last run: {schedulerHealth?.lastRunAt ? new Date(schedulerHealth.lastRunAt).toLocaleString() : "-"} | Processed: {schedulerHealth?.lastProcessedCount ?? 0} | Dispatched: {schedulerHealth?.lastDispatchedCount ?? 0} | Failed: {schedulerHealth?.lastFailedCount ?? 0} | Skipped: {schedulerHealth?.lastSkippedCount ?? 0}</Typography></CardContent></Card>

      <Tabs value={tab} onChange={(_, v) => setTab(v)}>
        <Tab value="CAMPAIGNS" label="Campaigns" />
        <Tab value="EXECUTIONS" label="Executions" />
        <Tab value="ESCALATIONS" label="Escalations" />
        <Tab value="ANALYTICS" label="Analytics" />
      </Tabs>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ minHeight: 180, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && tab === "CAMPAIGNS" ? (
        <Card><CardContent>
          {campaigns.length === 0 ? <Alert severity="info">No AI call campaigns found.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Retries</TableCell><TableCell>Escalation</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {campaigns.map((c) => (
                <TableRow key={c.id}><TableCell>{c.name}</TableCell><TableCell>{c.callType}</TableCell><TableCell><Chip size="small" label={c.status} /></TableCell><TableCell>{c.retryEnabled ? `Yes (${c.maxAttempts})` : "No"}</TableCell><TableCell>{c.escalationEnabled ? "Enabled" : "Disabled"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end">{canMutate && c.status !== "ACTIVE" ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await updateCarePilotAiCallCampaignStatus(auth.accessToken, auth.tenantId, c.id, "ACTIVE"); await load(); }}>Activate</Button> : null}{canMutate && c.status === "ACTIVE" ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await updateCarePilotAiCallCampaignStatus(auth.accessToken, auth.tenantId, c.id, "PAUSED"); await load(); }}>Pause</Button> : null}{canMutate ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await triggerCarePilotAiCallCampaign(auth.accessToken, auth.tenantId, c.id, { targets: [] }); await load(); }}>Trigger</Button> : null}</Stack></TableCell></TableRow>
              ))}
            </TableBody></Table>
          )}
        </CardContent></Card>
      ) : null}

      {!loading && tab === "EXECUTIONS" ? (
        <Stack spacing={1}>
          <TextField select label="Status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as CarePilotAiCallExecutionStatus | "")} sx={{ maxWidth: 280 }}><MenuItem value="">All</MenuItem>{statuses.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField>
          <Card><CardContent>
            {executions.length === 0 ? <Alert severity="info">No executions found.</Alert> : (
              <Table size="small"><TableHead><TableRow><TableCell>Phone</TableCell><TableCell>Campaign</TableCell><TableCell>Status</TableCell><TableCell>Provider</TableCell><TableCell>Duration</TableCell><TableCell>Retries</TableCell><TableCell>Reason</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
                {executions.map((e) => (
                  <TableRow key={e.id}><TableCell>{e.phoneNumber}</TableCell><TableCell>{campaigns.find((c) => c.id === e.campaignId)?.name || e.campaignId}</TableCell><TableCell><Chip size="small" label={e.executionStatus} color={e.executionStatus === "COMPLETED" ? "success" : e.executionStatus === "ESCALATED" ? "warning" : e.executionStatus === "FAILED" ? "error" : "default"} /></TableCell><TableCell>{e.providerName || "-"}</TableCell><TableCell>{e.durationSeconds ? `${e.durationSeconds}s` : "-"}</TableCell><TableCell>{e.retryCount}</TableCell><TableCell>{e.suppressionReason || e.failureReason || "-"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end">{e.transcriptId ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; const t = await getCarePilotAiCallTranscript(auth.accessToken, auth.tenantId, e.id); setTranscript(t); setTranscriptOpen(true); }}>Transcript</Button> : null}<Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; const rows = await listCarePilotAiCallEvents(auth.accessToken, auth.tenantId, e.id); setEvents(rows); setEventsOpen(true); }}>Events</Button>{canMutate ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await retryCarePilotAiCallExecution(auth.accessToken, auth.tenantId, e.id); await load(); }}>Retry</Button> : null}{canMutate ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await cancelCarePilotAiCallExecution(auth.accessToken, auth.tenantId, e.id, "Operator cancelled"); await load(); }}>Cancel</Button> : null}{canMutate ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await suppressCarePilotAiCallExecution(auth.accessToken, auth.tenantId, e.id, "Operator suppressed"); await load(); }}>Suppress</Button> : null}{canMutate ? <Button size="small" onClick={async () => { if (!auth.accessToken || !auth.tenantId) return; await rescheduleCarePilotAiCallExecution(auth.accessToken, auth.tenantId, e.id, { scheduledAt: new Date(Date.now() + 10 * 60000).toISOString(), reason: "Rescheduled +10m" }); await load(); }}>Reschedule</Button> : null}</Stack></TableCell></TableRow>
                ))}
              </TableBody></Table>
            )}
          </CardContent></Card>
        </Stack>
      ) : null}

      {!loading && tab === "ESCALATIONS" ? (
        <Card><CardContent>
          {escalations.length === 0 ? <Alert severity="info">No escalated calls currently.</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Phone</TableCell><TableCell>Status</TableCell><TableCell>Reason</TableCell><TableCell>Campaign</TableCell></TableRow></TableHead><TableBody>{escalations.map((e) => <TableRow key={e.id}><TableCell>{e.phoneNumber}</TableCell><TableCell><Chip size="small" color="warning" label={e.executionStatus} /></TableCell><TableCell>{e.escalationReason || "-"}</TableCell><TableCell>{campaigns.find((c) => c.id === e.campaignId)?.name || e.campaignId}</TableCell></TableRow>)}</TableBody></Table>
          )}
        </CardContent></Card>
      ) : null}

      {!loading && tab === "ANALYTICS" ? (
        <Card><CardContent><Stack spacing={0.75}><Typography variant="body2">Total calls: {analytics?.totalCalls ?? 0}</Typography><Typography variant="body2">Completed calls: {analytics?.completedCalls ?? 0}</Typography><Typography variant="body2">Failed calls: {analytics?.failedCalls ?? 0}</Typography><Typography variant="body2">Escalations: {analytics?.escalations ?? 0}</Typography><Typography variant="body2">Queued calls: {analytics?.queuedCalls ?? 0}</Typography><Typography variant="body2">Suppressed calls: {analytics?.suppressedCalls ?? 0}</Typography><Typography variant="body2">Skipped calls: {analytics?.skippedCalls ?? 0}</Typography><Typography variant="body2">No-answer rate: {(analytics?.noAnswerRate ?? 0).toFixed(1)}%</Typography><Typography variant="body2">Retry rate: {(analytics?.retryRate ?? 0).toFixed(1)}%</Typography></Stack></CardContent></Card>
      ) : null}

      <Dialog open={transcriptOpen} onClose={() => setTranscriptOpen(false)} fullWidth maxWidth="md"><DialogTitle>Call Transcript</DialogTitle><DialogContent>{!transcript ? <Alert severity="info">Transcript not found.</Alert> : (<Stack spacing={1} sx={{ pt: 1 }}><Typography variant="body2"><b>Summary:</b> {transcript.summary || "-"}</Typography><Typography variant="body2"><b>Outcome:</b> {transcript.outcome || "-"}</Typography><Typography variant="body2"><b>Sentiment:</b> {transcript.sentiment || "-"}</Typography><Typography variant="body2"><b>Intent:</b> {transcript.intent || "-"}</Typography><Typography variant="body2"><b>Follow-up required:</b> {transcript.requiresFollowUp ? "Yes" : "No"}</Typography><Typography variant="body2"><b>Escalation reason:</b> {transcript.escalationReason || "-"}</Typography><TextField multiline minRows={8} value={transcript.transcriptText || ""} InputProps={{ readOnly: true }} /></Stack>)}</DialogContent><DialogActions><Button onClick={() => setTranscriptOpen(false)}>Close</Button></DialogActions></Dialog>

      <Dialog open={eventsOpen} onClose={() => setEventsOpen(false)} fullWidth maxWidth="md"><DialogTitle>Execution Events</DialogTitle><DialogContent>{events.length === 0 ? <Alert severity="info">No events found.</Alert> : <Table size="small"><TableHead><TableRow><TableCell>Time</TableCell><TableCell>Type</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Detail</TableCell></TableRow></TableHead><TableBody>{events.map((e) => <TableRow key={e.id}><TableCell>{new Date(e.createdAt).toLocaleString()}</TableCell><TableCell>{e.eventType}</TableCell><TableCell>{e.providerName}</TableCell><TableCell>{e.internalStatus || e.externalStatus || "-"}</TableCell><TableCell>{e.rawPayloadRedacted || "-"}</TableCell></TableRow>)}</TableBody></Table>}</DialogContent><DialogActions><Button onClick={() => setEventsOpen(false)}>Close</Button></DialogActions></Dialog>

      {campaignStatuses.length === 0 ? null : null}
    </Stack>
  );
}
