import * as React from "react";
import { useSearchParams } from "react-router-dom";
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
  FormControl,
  Grid,
  InputLabel,
  IconButton,
  MenuItem,
  Select,
  Snackbar,
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
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import { useAuth } from "../../../auth/useAuth";
import { ConfirmationDialog } from "../../../components/clinical/ConfirmationDialog";
import { engageOpsConsoleFilterSchema, mapZodErrors } from "@deepthoughtnet/form-validation-kit";
import {
  getCarePilotExecutionTimeline,
  getCarePilotOpsReadiness,
  listCarePilotOpsExecutions,
  lookupCarePilotCampaigns,
  resendCarePilotExecution,
  retryCarePilotExecution,
  type CarePilotCampaignLookup,
  type CarePilotExecutionTimeline,
  type CarePilotOpsExecution,
  type CarePilotOpsReadiness,
} from "../../../api/clinicApi";
import CampaignLookupField from "../components/CampaignLookupField";
import { campaignTypeLabel, channelTypeLabel } from "../campaigns/campaignLabels";
import {
  formatCarePilotDateTime,
  formatCarePilotDurationMinutes,
  humanizeCarePilotCode,
  providerLabel,
} from "../shared/carepilotFormatting";
import { useCarePilotTenantTimezone } from "../shared/useCarePilotTenantTimezone";
import {
  OPS_FAILED_STATUSES,
  OPS_QUEUED_STATUSES,
  parseOpsFilters,
  serializeOpsFilters,
  type OpsFilters,
} from "./opsConsoleFilters";

type OpsTab = "all" | "queued" | "failed" | "readiness";

function deliveryChipColor(status: string) {
  if (status === "READ" || status === "DELIVERED" || status === "SENT") return "success" as const;
  if (status === "FAILED" || status === "BOUNCED" || status === "UNDELIVERED") return "error" as const;
  if (status === "QUEUED" || status === "PROCESSING" || status === "RETRY_SCHEDULED") return "info" as const;
  return "default" as const;
}

function executionStatusColor(status: string) {
  if (status === "SUCCEEDED") return "success" as const;
  if (status === "FAILED" || status === "DEAD_LETTER") return "error" as const;
  if (status === "RETRY_SCHEDULED") return "warning" as const;
  if (status === "QUEUED" || status === "PROCESSING") return "info" as const;
  if (status === "CANCELLED" || status === "SUPPRESSED") return "default" as const;
  return "default" as const;
}

function queueAgeLabel(execution: CarePilotOpsExecution) {
  if (execution.status !== "QUEUED" && execution.status !== "PROCESSING" && execution.status !== "RETRY_SCHEDULED") {
    return "—";
  }
  return formatCarePilotDurationMinutes(execution.queueAgeMinutes);
}

function durationMinutes(startAt: string | null, endAt: string | null) {
  if (!startAt || !endAt) return null;
  const start = new Date(startAt).getTime();
  const end = new Date(endAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return null;
  return Math.floor((end - start) / 60000);
}

function queueWaitMinutes(execution: CarePilotExecutionTimeline["execution"]) {
  if (!execution.acquiredAt) return null;
  return durationMinutes(execution.createdAt, execution.acquiredAt);
}

function processingDurationMinutes(execution: CarePilotExecutionTimeline["execution"], now = new Date()) {
  if (!execution.acquiredAt) return null;
  if (execution.status === "PROCESSING") {
    return durationMinutes(execution.acquiredAt, now.toISOString());
  }
  return durationMinutes(execution.acquiredAt, execution.executedAt);
}

function durationLabel(minutes: number | null) {
  return minutes == null ? "Not recorded" : formatCarePilotDurationMinutes(minutes);
}

export default function OpsConsolePage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialFilters = React.useMemo(() => parseOpsFilters(searchParams), [searchParams]);
  const [draftFilters, setDraftFilters] = React.useState<OpsFilters>(initialFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<OpsFilters>(initialFilters);
  const [activeTab, setActiveTab] = React.useState<OpsTab>("all");

  const [executionRows, setExecutionRows] = React.useState<CarePilotOpsExecution[]>([]);
  const [campaignLookupRows, setCampaignLookupRows] = React.useState<CarePilotCampaignLookup[]>([]);
  const [readiness, setReadiness] = React.useState<CarePilotOpsReadiness | null>(null);

  const [resultsLoading, setResultsLoading] = React.useState(false);
  const [auxLoading, setAuxLoading] = React.useState(true);
  const [resultsError, setResultsError] = React.useState<string | null>(null);
  const [auxError, setAuxError] = React.useState<string | null>(null);
  const [filterErrors, setFilterErrors] = React.useState<Record<string, string>>({});
  const [toast, setToast] = React.useState<string | null>(null);

  const [timelineOpen, setTimelineOpen] = React.useState(false);
  const [timelineLoading, setTimelineLoading] = React.useState(false);
  const [timeline, setTimeline] = React.useState<CarePilotExecutionTimeline | null>(null);
  const [actionTarget, setActionTarget] = React.useState<{ executionId: string; resend: boolean } | null>(null);
  const viewAttemptsButtonRef = React.useRef<HTMLButtonElement | null>(null);

  const execSeq = React.useRef(0);
  const auxSeq = React.useRef(0);

  const canView = auth.hasPermission("engage.ops.view");
  const canMutate = auth.hasPermission("engage.reminder.operate");
  const { clinicTimeZone } = useCarePilotTenantTimezone(auth.accessToken, auth.tenantId);

  const resolvedCampaign = React.useMemo(
    () => campaignLookupRows.find((row) => row.id === draftFilters.campaignId) || null,
    [campaignLookupRows, draftFilters.campaignId],
  );

  const refreshAuxiliaryData = React.useCallback(async (campaignRefFilter: string) => {
    if (!auth.accessToken || !auth.tenantId || !canView) return;
    const seq = ++auxSeq.current;
    setAuxLoading(true);
    setAuxError(null);
    try {
      const query = campaignRefFilter || "";
      const [lookupRows, readinessRows] = await Promise.all([
        lookupCarePilotCampaigns(auth.accessToken, auth.tenantId, query, 50),
        getCarePilotOpsReadiness(auth.accessToken, auth.tenantId),
      ]);
      if (seq !== auxSeq.current) return;
      setCampaignLookupRows(lookupRows);
      setReadiness(readinessRows);
    } catch (err) {
      if (seq !== auxSeq.current) return;
      setAuxError(err instanceof Error ? err.message : "Failed to load ops context");
    } finally {
      if (seq === auxSeq.current) {
        setAuxLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  const loadExecutions = React.useCallback(async (filters: OpsFilters) => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      return;
    }
    const seq = ++execSeq.current;
    setResultsLoading(true);
    setResultsError(null);
    try {
      const parsed = engageOpsConsoleFilterSchema.safeParse({ startDate: filters.startDate, endDate: filters.endDate, providerName: filters.providerName });
      if (!parsed.success) {
        setFilterErrors(mapZodErrors(parsed.error));
        if (seq === execSeq.current) {
          setResultsLoading(false);
        }
        return;
      }
      setFilterErrors({});
      const rows = await listCarePilotOpsExecutions(auth.accessToken, auth.tenantId, {
        campaignRef: filters.campaignRef || undefined,
        channel: filters.channel || undefined,
        status: filters.status || undefined,
        providerName: filters.providerName || undefined,
        retryableOnly: filters.retryableOnly,
        startDate: filters.startDate || undefined,
        endDate: filters.endDate || undefined,
        reminderWindow: filters.reminderWindow || undefined,
      });
      if (seq !== execSeq.current) return;
      setExecutionRows(rows);
    } catch (err) {
      if (seq !== execSeq.current) return;
      setResultsError(err instanceof Error ? err.message : "Failed to load ops queue");
    } finally {
      if (seq === execSeq.current) {
        setResultsLoading(false);
      }
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  React.useEffect(() => {
    const next = parseOpsFilters(searchParams);
    setDraftFilters(next);
    setAppliedFilters(next);
  }, [searchParams]);

  React.useEffect(() => {
    void loadExecutions(appliedFilters);
  }, [appliedFilters, loadExecutions]);

  React.useEffect(() => {
    void refreshAuxiliaryData(appliedFilters.campaignRef);
  }, []);

  React.useEffect(() => {
    if (!draftFilters.campaignRef || draftFilters.campaignId || campaignLookupRows.length === 0) return;
    const match = campaignLookupRows.find((row) => row.campaignReference === draftFilters.campaignRef);
    if (match) {
      setDraftFilters((current) => current.campaignId ? current : { ...current, campaignId: match.id });
    }
  }, [campaignLookupRows, draftFilters.campaignId, draftFilters.campaignRef]);

  const handleApply = React.useCallback(() => {
      setSearchParams(serializeOpsFilters(draftFilters));
  }, [draftFilters, setSearchParams]);

  const handleClear = React.useCallback(() => {
    setSearchParams(new URLSearchParams());
  }, [setSearchParams]);

  const handleRefresh = React.useCallback(() => {
    void refreshAuxiliaryData(appliedFilters.campaignRef);
    void loadExecutions(appliedFilters);
  }, [appliedFilters, loadExecutions, refreshAuxiliaryData]);

  const openTimeline = React.useCallback(async (executionId: string, trigger?: HTMLButtonElement | null) => {
    if (!auth.accessToken || !auth.tenantId) return;
    viewAttemptsButtonRef.current = trigger || null;
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
  }, [auth.accessToken, auth.tenantId]);

  const closeTimeline = React.useCallback(() => {
    setTimelineOpen(false);
    window.setTimeout(() => viewAttemptsButtonRef.current?.focus(), 0);
  }, []);

  const runAction = React.useCallback((executionId: string, resend: boolean) => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    setActionTarget({ executionId, resend });
  }, [auth.accessToken, auth.tenantId, canMutate]);

  const submitAction = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate || !actionTarget) return;
    const { executionId, resend } = actionTarget;
    try {
      if (resend) {
        await resendCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      } else {
        await retryCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      }
      setToast(`Execution ${resend ? "resend" : "retry"} queued.`);
      await loadExecutions(appliedFilters);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "Action failed");
    } finally {
      setActionTarget(null);
    }
  }, [actionTarget, appliedFilters, auth.accessToken, auth.tenantId, canMutate, loadExecutions]);

  const filteredRows = React.useMemo(() => {
    if (activeTab === "queued") return executionRows.filter((row) => OPS_QUEUED_STATUSES.includes(row.status));
    if (activeTab === "failed") return executionRows.filter((row) => OPS_FAILED_STATUSES.includes(row.status));
    return executionRows;
  }, [activeTab, executionRows]);

  const counts = React.useMemo(() => ({
    all: executionRows.length,
    queued: executionRows.filter((row) => OPS_QUEUED_STATUSES.includes(row.status)).length,
    failed: executionRows.filter((row) => OPS_FAILED_STATUSES.includes(row.status)).length,
    stuck: executionRows.filter((row) => row.stuck).length,
  }), [executionRows]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use Jeevanam Engage ops console.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to Jeevanam Engage ops console.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 1.5 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Jeevanam Engage Ops Console</Typography>
          <Typography variant="body2" color="text.secondary">Campaign execution queues, stuck work, failures, retries, and readiness.</Typography>
        </Box>
        <Button type="button" variant="outlined" onClick={handleRefresh}>Refresh</Button>
      </Box>

      {!canMutate ? <Alert severity="info">Read-only mode: retry/resend actions are restricted.</Alert> : null}
      {resultsError ? <Alert severity="error">{resultsError}</Alert> : null}
      {auxError ? <Alert severity="warning">{auxError}</Alert> : null}

      <Card>
        <CardContent>
          <Box component="form" onSubmit={(event) => { event.preventDefault(); handleApply(); }}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  fullWidth
                  type="date"
                  label="Start"
                  value={draftFilters.startDate}
                  onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))}
                  InputLabelProps={{ shrink: true }}
                  error={Boolean(filterErrors.startDate)}
                  helperText={filterErrors.startDate || ""}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  fullWidth
                  type="date"
                  label="End"
                  value={draftFilters.endDate}
                  onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))}
                  InputLabelProps={{ shrink: true }}
                  error={Boolean(filterErrors.endDate)}
                  helperText={filterErrors.endDate || ""}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <CampaignLookupField
                  token={auth.accessToken}
                  tenantId={auth.tenantId}
                  value={draftFilters.campaignId}
                  onChange={(campaignId) => setDraftFilters((current) => ({ ...current, campaignId }))}
                  onSelectOption={(option) => setDraftFilters((current) => ({
                    ...current,
                    campaignId: option?.id || "",
                    campaignRef: option?.campaignReference || "",
                  }))}
                  label="Campaign"
                  helperText="Search by campaign name, type, or campaign reference."
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <FormControl fullWidth>
                  <InputLabel>Channel</InputLabel>
                  <Select
                    value={draftFilters.channel}
                    label="Channel"
                    onChange={(event) => setDraftFilters((current) => ({ ...current, channel: String(event.target.value) as OpsFilters["channel"] }))}
                  >
                    <MenuItem value="">All</MenuItem>
                    <MenuItem value="EMAIL">Email</MenuItem>
                    <MenuItem value="SMS">SMS</MenuItem>
                    <MenuItem value="WHATSAPP">WhatsApp</MenuItem>
                    <MenuItem value="IN_APP">In-app</MenuItem>
                    <MenuItem value="APP_NOTIFICATION">App notification</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <FormControl fullWidth>
                  <InputLabel>Status</InputLabel>
                  <Select
                    value={draftFilters.status}
                    label="Status"
                    onChange={(event) => setDraftFilters((current) => ({ ...current, status: String(event.target.value) as OpsFilters["status"] }))}
                  >
                    <MenuItem value="">All</MenuItem>
                      {["QUEUED", "PROCESSING", "RETRY_SCHEDULED", "FAILED", "DEAD_LETTER", "SUCCEEDED", "CANCELLED", "SUPPRESSED", "DELIVERED", "READ", "SKIPPED", "UNDELIVERED", "BOUNCED"].map((value) => <MenuItem key={value} value={value}>{humanizeCarePilotCode(value)}</MenuItem>)}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <FormControl fullWidth>
                  <InputLabel>Reminder</InputLabel>
                  <Select
                    value={draftFilters.reminderWindow}
                    label="Reminder"
                    onChange={(event) => setDraftFilters((current) => ({ ...current, reminderWindow: String(event.target.value) as OpsFilters["reminderWindow"] }))}
                  >
                    <MenuItem value="">All</MenuItem>
                    <MenuItem value="H24">24-hour</MenuItem>
                    <MenuItem value="H2">2-hour</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField
                  fullWidth
                  label="Provider"
                  value={draftFilters.providerName}
                  onChange={(event) => setDraftFilters((current) => ({ ...current, providerName: event.target.value }))}
                  error={Boolean(filterErrors.providerName)}
                  helperText={filterErrors.providerName || ""}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <Button fullWidth type="submit" variant="contained" disabled={resultsLoading}>Apply</Button>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <Button fullWidth type="button" variant="outlined" onClick={handleClear}>Clear</Button>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <Button
                  fullWidth
                  type="button"
                  variant={draftFilters.retryableOnly ? "contained" : "outlined"}
                  onClick={() => setDraftFilters((current) => ({ ...current, retryableOnly: !current.retryableOnly }))}
                >
                  {draftFilters.retryableOnly ? "Retryable Only: ON" : "Retryable Only: OFF"}
                </Button>
              </Grid>
              {resolvedCampaign ? (
                <Grid size={{ xs: 12 }}>
                  <Alert severity="info">
                    Selected campaign: {resolvedCampaign.name} • {campaignTypeLabel(resolvedCampaign.campaignType)} • {resolvedCampaign.campaignReference}
                  </Alert>
                </Grid>
              ) : null}
            </Grid>
          </Box>
        </CardContent>
      </Card>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">All Executions</Typography><Typography variant="h5">{counts.all}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Queued / Stuck</Typography><Typography variant="h5">{counts.queued}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Failed / Retrying</Typography><Typography variant="h5">{counts.failed}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 3 }}><Card><CardContent><Typography variant="caption">Stuck Indicators</Typography><Typography variant="h5">{counts.stuck}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent sx={{ pb: 0 }}>
          <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} variant="scrollable" allowScrollButtonsMobile>
            <Tab value="all" label="All Executions" />
            <Tab value="queued" label="Queued / Stuck" />
            <Tab value="failed" label="Failed / Retrying" />
            <Tab value="readiness" label="Readiness" />
          </Tabs>
        </CardContent>
      </Card>

      {activeTab === "readiness" ? (
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Queue Readiness</Typography>
            {auxLoading && !readiness ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}
            {!auxLoading && readiness ? (
              <Stack spacing={2}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Card variant="outlined"><CardContent>
                      <Typography variant="caption">Manual execution dispatcher</Typography>
                      <Typography variant="h6">{readiness.manualExecutionDispatcherEnabled ? "Enabled" : "Disabled"}</Typography>
                      <Typography variant="body2" color="text.secondary">Last acquired: {formatCarePilotDateTime(readiness.manualExecutionDispatcherLastAcquiredAt, clinicTimeZone)}</Typography>
                      <Typography variant="body2" color="text.secondary">Last skipped: {formatCarePilotDateTime(readiness.manualExecutionDispatcherLastSkippedAt, clinicTimeZone)}</Typography>
                    </CardContent></Card>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Card variant="outlined"><CardContent>
                      <Typography variant="caption">Reminder scheduler</Typography>
                      <Typography variant="h6">{readiness.reminderSchedulerEnabled ? "Enabled" : "Disabled"}</Typography>
                      <Typography variant="body2" color="text.secondary">Last scan: {formatCarePilotDateTime(readiness.reminderSchedulerLastScanAt, clinicTimeZone)}</Typography>
                    </CardContent></Card>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Card variant="outlined"><CardContent>
                      <Typography variant="caption">Queue depth</Typography>
                      <Typography variant="h6">{readiness.queueDepth}</Typography>
                      <Typography variant="body2" color="text.secondary">Oldest queued age: {readiness.oldestQueuedAgeMinutes ? `${readiness.oldestQueuedAgeMinutes}m` : "-"}</Typography>
                      <Typography variant="body2" color="text.secondary">Last successful dispatch: {formatCarePilotDateTime(readiness.lastSuccessfulDispatchAt, clinicTimeZone)}</Typography>
                    </CardContent></Card>
                  </Grid>
                </Grid>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Provider readiness by channel</Typography>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Channel</TableCell>
                          <TableCell>Provider</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Configured</TableCell>
                          <TableCell>Available</TableCell>
                          <TableCell>Message</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {readiness.providerStatuses.map((provider) => (
                          <TableRow key={provider.channel}>
                            <TableCell>{provider.channel}</TableCell>
                            <TableCell>{providerLabel(provider.providerName)}</TableCell>
                            <TableCell><Chip size="small" color={provider.status === "READY" ? "success" : provider.status === "ERROR" ? "error" : "default"} label={provider.status} /></TableCell>
                            <TableCell>{provider.configured ? "Yes" : "No"}</TableCell>
                            <TableCell>{provider.available ? "Yes" : "No"}</TableCell>
                            <TableCell>{provider.message}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CardContent>
                </Card>
              </Stack>
            ) : null}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
              {activeTab === "all" ? "All Executions" : activeTab === "queued" ? "Queued / Stuck" : "Failed / Retrying"}
            </Typography>
            {resultsLoading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}
            {!resultsLoading && filteredRows.length === 0 ? (
              <Alert severity="info">
                {activeTab === "all"
                  ? "No executions found for the selected filters."
                  : activeTab === "queued"
                    ? "No queued or stuck executions found."
                    : "No failed or retrying executions found."}
              </Alert>
            ) : null}
            {!resultsLoading && filteredRows.length > 0 ? (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Execution</TableCell>
                    <TableCell>Campaign</TableCell>
                    <TableCell>Patient / Entity</TableCell>
                    <TableCell>Channel</TableCell>
                    <TableCell>Execution Status</TableCell>
                    <TableCell>Delivery Status</TableCell>
                    <TableCell>Queued</TableCell>
                    <TableCell>Queue Age</TableCell>
                    <TableCell>Provider</TableCell>
                    <TableCell>Delivery Attempts</TableCell>
                    <TableCell>Retries</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredRows.map((row) => (
                    <TableRow key={row.executionReference} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.executionReference}</Typography>
                        <Typography variant="caption" color="text.secondary">{row.status === "QUEUED" || row.status === "PROCESSING" ? "Awaiting dispatch" : humanizeCarePilotCode(row.status)}</Typography>
                      </TableCell>
                      <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.campaignName}</Typography>
                    <Typography variant="caption" color="text.secondary">{`${row.campaignType ? campaignTypeLabel(row.campaignType as any) : "Campaign"} • ${row.campaignReference}`}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "Unknown patient"}</Typography>
                        <Typography variant="caption" color="text.secondary">{row.patientReference || row.relatedEntityLabel || "Business reference unavailable"}</Typography>
                      </TableCell>
                      <TableCell>{channelTypeLabel(row.channelType)}</TableCell>
                      <TableCell>
                        <Chip size="small" color={executionStatusColor(row.status)} label={humanizeCarePilotCode(row.status)} />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color={row.deliveryStatus ? deliveryChipColor(row.deliveryStatus) : "default"} label={humanizeCarePilotCode(row.deliveryStatus)} />
                        {row.stuck ? <Chip size="small" sx={{ ml: 1 }} color="warning" label="Stuck" /> : null}
                      </TableCell>
                      <TableCell>{formatCarePilotDateTime(row.queuedAt, clinicTimeZone)}</TableCell>
                      <TableCell>{queueAgeLabel(row)}</TableCell>
                      <TableCell>{providerLabel(row.providerName)}</TableCell>
                      <TableCell>{row.deliveryAttemptCount}</TableCell>
                      <TableCell>{row.retryCount}</TableCell>
                      <TableCell>{row.blockingReason || row.failureReason || "-"}</TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button type="button" size="small" onClick={(event) => void openTimeline(row.executionId, event.currentTarget)}>View Attempts</Button>
                          {canMutate && OPS_FAILED_STATUSES.includes(row.status) ? <Button type="button" size="small" onClick={() => void runAction(row.executionId, false)}>Retry</Button> : null}
                          {canMutate && OPS_FAILED_STATUSES.includes(row.status) ? <Button type="button" size="small" onClick={() => void runAction(row.executionId, true)}>Resend</Button> : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : null}
          </CardContent>
        </Card>
      )}

      <Dialog
        open={timelineOpen}
        onClose={closeTimeline}
        scroll="paper"
        fullWidth
        maxWidth="lg"
        aria-labelledby="ops-execution-timeline-title"
        aria-describedby="ops-execution-timeline-description"
      >
        <DialogTitle
          id="ops-execution-timeline-title"
          sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}
        >
          <span>Execution Timeline</span>
          <IconButton aria-label="Close execution timeline" onClick={closeTimeline} size="small">
            <CloseRoundedIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent id="ops-execution-timeline-description">
          {timelineLoading ? <Box sx={{ minHeight: 120, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
          {!timelineLoading && timeline ? (
            <Stack spacing={2}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Execution Metadata</Typography>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Execution Status: {humanizeCarePilotCode(timeline.execution.status)}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Delivery Status: {humanizeCarePilotCode(timeline.execution.deliveryStatus)}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Scheduled: {formatCarePilotDateTime(timeline.execution.scheduledAt, clinicTimeZone)}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Queued: {formatCarePilotDateTime(timeline.execution.createdAt, clinicTimeZone)}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Executed: {formatCarePilotDateTime(timeline.execution.executedAt, clinicTimeZone)}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Queue Wait: {durationLabel(queueWaitMinutes(timeline.execution))}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Processing Duration: {durationLabel(processingDurationMinutes(timeline.execution))}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Delivery Attempt Count: {timeline.deliveryAttempts.length}</Typography></Grid>
                    <Grid size={{ xs: 12, md: 6 }}><Typography variant="body2">Retry Count: {timeline.execution.attemptCount}</Typography></Grid>
                  </Grid>
                </CardContent>
              </Card>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Delivery Attempts</Typography>
                  {timeline.deliveryAttempts.length === 0 ? <Alert severity="info">No attempts recorded yet.</Alert> : (
                    <Table size="small">
                      <TableHead><TableRow><TableCell>#</TableCell><TableCell>Provider</TableCell><TableCell>Channel</TableCell><TableCell>Status</TableCell><TableCell>Error Code</TableCell><TableCell>Error Message</TableCell><TableCell>Attempted At</TableCell></TableRow></TableHead>
                      <TableBody>
                        {timeline.deliveryAttempts.map((attempt) => <TableRow key={attempt.id}><TableCell>{attempt.attemptNumber}</TableCell><TableCell>{providerLabel(attempt.providerName)}</TableCell><TableCell>{channelTypeLabel(attempt.channelType)}</TableCell><TableCell><Chip size="small" color={deliveryChipColor(attempt.deliveryStatus)} label={humanizeCarePilotCode(attempt.deliveryStatus)} /></TableCell><TableCell>{attempt.errorCode || "-"}</TableCell><TableCell>{attempt.errorMessage || "-"}</TableCell><TableCell>{formatCarePilotDateTime(attempt.attemptedAt, clinicTimeZone)}</TableCell></TableRow>)}
                      </TableBody>
                    </Table>
                  )}
                </CardContent>
              </Card>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Provider Delivery Events</Typography>
                  {timeline.deliveryEvents.length === 0 ? <Alert severity="info">No provider webhook events recorded yet.</Alert> : (
                    <Table size="small">
                      <TableHead><TableRow><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Provider</TableCell><TableCell>Message ID</TableCell><TableCell>External</TableCell><TableCell>Event Time</TableCell></TableRow></TableHead>
                      <TableBody>
                        {timeline.deliveryEvents.map((event, index) => (
                          <TableRow key={`${event.id || "event"}-${index}`}>
                            <TableCell>{event.eventType}</TableCell>
                            <TableCell><Chip size="small" color={event.internalStatus ? deliveryChipColor(event.internalStatus) : "default"} label={humanizeCarePilotCode(event.internalStatus)} /></TableCell>
                            <TableCell>{providerLabel(event.providerName)}</TableCell>
                            <TableCell>{event.providerMessageId ? "Recorded" : "-"}</TableCell>
                            <TableCell>{event.externalStatus || "-"}</TableCell>
                            <TableCell>{formatCarePilotDateTime(event.eventTimestamp, clinicTimeZone)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </CardContent>
              </Card>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button type="button" onClick={closeTimeline}>Close</Button>
        </DialogActions>
      </Dialog>

      <ConfirmationDialog
        open={Boolean(actionTarget)}
        title={actionTarget?.resend ? "Resend execution" : "Retry execution"}
        description="This will queue a new delivery attempt. Continue?"
        confirmLabel={actionTarget?.resend ? "Resend" : "Retry"}
        confirmColor="warning"
        onCancel={() => setActionTarget(null)}
        onConfirm={() => void submitAction()}
      />

      <Snackbar open={Boolean(toast)} autoHideDuration={3000} onClose={() => setToast(null)} message={toast || ""} />
    </Stack>
  );
}
