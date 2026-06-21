import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
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
import { mapZodErrors, engageAiCallCampaignSchema, engageAiCallManualCallSchema, engageAiCallRescheduleSchema, normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import {
  cancelCarePilotAiCallExecution,
  createCarePilotAiCallCampaign,
  createCarePilotAiCallManualCall,
  dispatchDueCarePilotAiCalls,
  getCarePilotAiCallAnalyticsSummary,
  getCarePilotAiCallCampaign,
  getCarePilotAiCallExecution,
  getCarePilotAiCallSchedulerHealth,
  getCarePilotAiCallTranscript,
  listAdminTemplates,
  listCarePilotAiCallCampaigns,
  listCarePilotAiCallEvents,
  listCarePilotAiCallExecutions,
  rescheduleCarePilotAiCallExecution,
  retryCarePilotAiCallExecution,
  suppressCarePilotAiCallExecution,
  triggerCarePilotAiCallCampaign,
  updateCarePilotAiCallCampaign,
  updateCarePilotAiCallCampaignStatus,
  type AdminTemplate,
  type CarePilotAiCallAnalyticsSummary,
  type CarePilotAiCallCampaign,
  type CarePilotAiCallCampaignStatus,
  type CarePilotAiCallCampaignUpsertInput,
  type CarePilotAiCallEvent,
  type CarePilotAiCallExecution,
  type CarePilotAiCallExecutionStatus,
  type CarePilotAiCallManualCallInput,
  type CarePilotAiCallSchedulerHealth,
  type CarePilotAiCallTranscript,
  type CarePilotAiCallTriggerTargetInput,
  type CarePilotAiCallType,
} from "../../../api/clinicApi";

const executionStatuses: CarePilotAiCallExecutionStatus[] = ["PENDING", "QUEUED", "DIALING", "IN_PROGRESS", "COMPLETED", "FAILED", "NO_ANSWER", "BUSY", "CANCELLED", "ESCALATED", "SKIPPED", "SUPPRESSED"];
const campaignStatuses: CarePilotAiCallCampaignStatus[] = ["DRAFT", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"];
const callTypes: CarePilotAiCallType[] = [
  "APPOINTMENT_REMINDER",
  "MISSED_APPOINTMENT",
  "REFILL_REMINDER",
  "BILLING_REMINDER",
  "WELLNESS_OUTREACH",
  "LEAD_FOLLOW_UP",
  "WEBINAR_REMINDER",
  "MANUAL_OUTREACH",
];

type CampaignFormState = {
  id: string | null;
  name: string;
  description: string;
  callType: CarePilotAiCallType;
  status: CarePilotAiCallCampaignStatus;
  templateId: string;
  retryEnabled: boolean;
  maxAttempts: number;
  escalationEnabled: boolean;
};

type ManualCallFormState = {
  phoneNumber: string;
  patientId: string;
  leadId: string;
  templateId: string;
  callType: CarePilotAiCallType;
  script: string;
  scheduledAt: string;
};

type TriggerTargetState = {
  patientId: string;
  leadId: string;
  phoneNumber: string;
  script: string;
  scheduledAt: string;
};

type RescheduleState = {
  scheduledAt: string;
  reason: string;
};

function emptyCampaignForm(): CampaignFormState {
  return {
    id: null,
    name: "",
    description: "",
    callType: "MANUAL_OUTREACH",
    status: "DRAFT",
    templateId: "",
    retryEnabled: true,
    maxAttempts: 3,
    escalationEnabled: false,
  };
}

function emptyManualCallForm(): ManualCallFormState {
  return {
    phoneNumber: "",
    patientId: "",
    leadId: "",
    templateId: "",
    callType: "MANUAL_OUTREACH",
    script: "",
    scheduledAt: "",
  };
}

function emptyTriggerTarget(): TriggerTargetState {
  return {
    patientId: "",
    leadId: "",
    phoneNumber: "",
    script: "",
    scheduledAt: "",
  };
}

function emptyRescheduleState(): RescheduleState {
  return {
    scheduledAt: "",
    reason: "",
  };
}

function toLocalDateTimeValue(iso: string | null | undefined) {
  if (!iso) {
    return "";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);
  return local.toISOString().slice(0, 16);
}

function toIsoOrNull(localValue: string) {
  if (!localValue) {
    return null;
  }
  const date = new Date(localValue);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function executionStatusColor(status: CarePilotAiCallExecutionStatus): "default" | "success" | "warning" | "error" {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "ESCALATED" || status === "NO_ANSWER" || status === "BUSY") {
    return "warning";
  }
  if (status === "FAILED" || status === "CANCELLED" || status === "SUPPRESSED") {
    return "error";
  }
  return "default";
}

export default function AiCallsPage() {
  const auth = useAuth();
  const canView =
    auth.rolesUpper.includes("CLINIC_ADMIN") ||
    auth.rolesUpper.includes("AUDITOR") ||
    auth.rolesUpper.includes("RECEPTIONIST") ||
    (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));
  const canMutate =
    auth.rolesUpper.includes("CLINIC_ADMIN") ||
    auth.rolesUpper.includes("RECEPTIONIST") ||
    (auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId));

  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null);
  const [dispatchSummary, setDispatchSummary] = React.useState<string | null>(null);
  const [tab, setTab] = React.useState<"CAMPAIGNS" | "EXECUTIONS" | "ESCALATIONS" | "ANALYTICS">("CAMPAIGNS");
  const [campaigns, setCampaigns] = React.useState<CarePilotAiCallCampaign[]>([]);
  const [executions, setExecutions] = React.useState<CarePilotAiCallExecution[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotAiCallAnalyticsSummary | null>(null);
  const [schedulerHealth, setSchedulerHealth] = React.useState<CarePilotAiCallSchedulerHealth | null>(null);
  const [voiceTemplates, setVoiceTemplates] = React.useState<AdminTemplate[]>([]);
  const [statusFilter, setStatusFilter] = React.useState<CarePilotAiCallExecutionStatus | "">("");
  const [selectedCampaignId, setSelectedCampaignId] = React.useState<string | null>(null);
  const [selectedCampaign, setSelectedCampaign] = React.useState<CarePilotAiCallCampaign | null>(null);
  const [selectedExecution, setSelectedExecution] = React.useState<CarePilotAiCallExecution | null>(null);
  const [executionDetailOpen, setExecutionDetailOpen] = React.useState(false);
  const [campaignDialogOpen, setCampaignDialogOpen] = React.useState(false);
  const [campaignForm, setCampaignForm] = React.useState<CampaignFormState>(emptyCampaignForm);
  const [campaignErrors, setCampaignErrors] = React.useState<Record<string, string>>({});
  const [manualCallOpen, setManualCallOpen] = React.useState(false);
  const [manualCallForm, setManualCallForm] = React.useState<ManualCallFormState>(emptyManualCallForm);
  const [manualCallErrors, setManualCallErrors] = React.useState<Record<string, string>>({});
  const [triggerOpen, setTriggerOpen] = React.useState(false);
  const [triggerCampaignId, setTriggerCampaignId] = React.useState<string | null>(null);
  const [triggerTargets, setTriggerTargets] = React.useState<TriggerTargetState[]>([emptyTriggerTarget()]);
  const [triggerErrors, setTriggerErrors] = React.useState<Record<string, string>>({});
  const [transcriptOpen, setTranscriptOpen] = React.useState(false);
  const [transcript, setTranscript] = React.useState<CarePilotAiCallTranscript | null>(null);
  const [eventsOpen, setEventsOpen] = React.useState(false);
  const [events, setEvents] = React.useState<CarePilotAiCallEvent[]>([]);
  const [rescheduleOpen, setRescheduleOpen] = React.useState(false);
  const [rescheduleExecutionId, setRescheduleExecutionId] = React.useState<string | null>(null);
  const [rescheduleForm, setRescheduleForm] = React.useState<RescheduleState>(emptyRescheduleState);
  const [rescheduleErrors, setRescheduleErrors] = React.useState<Record<string, string>>({});

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, executionRows, summary, health, templates] = await Promise.all([
        listCarePilotAiCallCampaigns(auth.accessToken, auth.tenantId),
        listCarePilotAiCallExecutions(auth.accessToken, auth.tenantId, { status: statusFilter || undefined, size: 50 }),
        getCarePilotAiCallAnalyticsSummary(auth.accessToken, auth.tenantId),
        getCarePilotAiCallSchedulerHealth(auth.accessToken, auth.tenantId),
        listAdminTemplates(auth.accessToken, auth.tenantId, { channel: "VOICE", active: true }),
      ]);
      setCampaigns(campaignRows);
      setExecutions(executionRows.rows);
      setAnalytics(summary);
      setSchedulerHealth(health);
      setVoiceTemplates(templates);
      if (selectedCampaignId) {
        const detail = campaignRows.find((row) => row.id === selectedCampaignId) ?? null;
        setSelectedCampaign(detail);
      } else {
        setSelectedCampaign(campaignRows[0] ?? null);
        setSelectedCampaignId(campaignRows[0]?.id ?? null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load AI calls data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, selectedCampaignId, statusFilter]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const selectedCampaignName = React.useMemo(
    () => campaigns.find((campaign) => campaign.id === triggerCampaignId)?.name ?? "Campaign",
    [campaigns, triggerCampaignId]
  );

  const escalations = React.useMemo(
    () => executions.filter((execution) => execution.escalationRequired || execution.executionStatus === "ESCALATED"),
    [executions]
  );

  const handleAction = React.useCallback(
    async (action: () => Promise<void>, success?: string) => {
      setSubmitting(true);
      setError(null);
      setSuccessMessage(null);
      try {
        await action();
        await load();
        if (success) {
          setSuccessMessage(success);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Request failed");
      } finally {
        setSubmitting(false);
      }
    },
    [load]
  );

  const openCreateCampaign = React.useCallback(() => {
    setCampaignForm(emptyCampaignForm());
    setCampaignDialogOpen(true);
  }, []);

  const openEditCampaign = React.useCallback(async (campaignId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const detail = await getCarePilotAiCallCampaign(auth.accessToken, auth.tenantId, campaignId);
      setCampaignForm({
        id: detail.id,
        name: detail.name,
        description: detail.description ?? "",
        callType: detail.callType,
        status: detail.status,
        templateId: detail.templateId ?? "",
        retryEnabled: detail.retryEnabled,
        maxAttempts: detail.maxAttempts,
        escalationEnabled: detail.escalationEnabled,
      });
      setCampaignDialogOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load campaign");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const saveCampaign = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId) {
      return;
    }
    const body: CarePilotAiCallCampaignUpsertInput = {
      name: campaignForm.name.trim(),
      description: campaignForm.description.trim() || null,
      callType: campaignForm.callType,
      status: campaignForm.status,
      templateId: campaignForm.templateId || null,
      retryEnabled: campaignForm.retryEnabled,
      maxAttempts: campaignForm.retryEnabled ? Math.max(1, campaignForm.maxAttempts) : 1,
      escalationEnabled: campaignForm.escalationEnabled,
    };
    const parsed = engageAiCallCampaignSchema.safeParse(body);
    if (!parsed.success) {
      setCampaignErrors(mapZodErrors(parsed.error));
      return;
    }
    setCampaignErrors({});
    await handleAction(async () => {
      if (campaignForm.id) {
        await updateCarePilotAiCallCampaign(token, tenantId, campaignForm.id, body);
      } else {
        await createCarePilotAiCallCampaign(token, tenantId, body);
      }
      setCampaignDialogOpen(false);
      setCampaignForm(emptyCampaignForm());
    }, campaignForm.id ? "Campaign updated." : "Campaign created.");
  }, [auth.accessToken, auth.tenantId, campaignForm, handleAction]);

  const openTriggerDialog = React.useCallback((campaignId: string) => {
    setTriggerCampaignId(campaignId);
    setTriggerTargets([emptyTriggerTarget()]);
    setTriggerOpen(true);
  }, []);

  const submitTrigger = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId || !triggerCampaignId) {
      return;
    }
    const normalizedTargets: CarePilotAiCallTriggerTargetInput[] = triggerTargets
      .map((target) => ({
        patientId: target.patientId.trim() || null,
        leadId: target.leadId.trim() || null,
        phoneNumber: target.phoneNumber.trim() ? (normalizeIndianMobileInput(target.phoneNumber) as string) : null,
        script: target.script.trim() || null,
        scheduledAt: toIsoOrNull(target.scheduledAt),
      }))
      .filter((target) => target.patientId || target.leadId || target.phoneNumber);
    if (normalizedTargets.length === 0) {
      setTriggerErrors({ _form: "Add at least one trigger target." });
      return;
    }
    if (normalizedTargets.some((target) => !target.patientId && !target.leadId && !target.phoneNumber)) {
      setTriggerErrors({ _form: "Phone number is required when patientId and leadId are empty." });
      return;
    }
    setTriggerErrors({});
    await handleAction(async () => {
      await triggerCarePilotAiCallCampaign(token, tenantId, triggerCampaignId, { targets: normalizedTargets });
      setTriggerOpen(false);
      setTriggerTargets([emptyTriggerTarget()]);
    }, "Campaign trigger queued.");
  }, [auth.accessToken, auth.tenantId, handleAction, triggerCampaignId, triggerTargets]);

  const submitManualCall = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId) {
      return;
    }
    const body: CarePilotAiCallManualCallInput = {
      phoneNumber: normalizeIndianMobileInput(manualCallForm.phoneNumber) as string,
      patientId: manualCallForm.patientId.trim() || null,
      leadId: manualCallForm.leadId.trim() || null,
      templateId: manualCallForm.templateId.trim() || null,
      callType: manualCallForm.callType,
      script: manualCallForm.script.trim() || null,
      scheduledAt: toIsoOrNull(manualCallForm.scheduledAt),
    };
    const parsed = engageAiCallManualCallSchema.safeParse(body);
    if (!parsed.success) {
      setManualCallErrors(mapZodErrors(parsed.error));
      return;
    }
    setManualCallErrors({});
    await handleAction(async () => {
      await createCarePilotAiCallManualCall(token, tenantId, body);
      setManualCallOpen(false);
      setManualCallForm(emptyManualCallForm());
    }, "Manual call queued.");
  }, [auth.accessToken, auth.tenantId, handleAction, manualCallForm]);

  const openExecutionDetails = React.useCallback(async (executionId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const detail = await getCarePilotAiCallExecution(auth.accessToken, auth.tenantId, executionId);
      setSelectedExecution(detail);
      setExecutionDetailOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load execution");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const openTranscript = React.useCallback(async (executionId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const detail = await getCarePilotAiCallTranscript(auth.accessToken, auth.tenantId, executionId);
      setTranscript(detail);
      setTranscriptOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load transcript");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const openEvents = React.useCallback(async (executionId: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const rows = await listCarePilotAiCallEvents(auth.accessToken, auth.tenantId, executionId);
      setEvents(rows);
      setEventsOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load events");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const openReschedule = React.useCallback((execution: CarePilotAiCallExecution) => {
    setRescheduleExecutionId(execution.id);
    setRescheduleForm({
      scheduledAt: toLocalDateTimeValue(execution.nextRetryAt || execution.scheduledAt),
      reason: "",
    });
    setRescheduleOpen(true);
  }, []);

  const submitReschedule = React.useCallback(async () => {
    const token = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!token || !tenantId || !rescheduleExecutionId) {
      return;
    }
    const scheduledAt = toIsoOrNull(rescheduleForm.scheduledAt);
    const parsed = engageAiCallRescheduleSchema.safeParse({
      scheduledAt: rescheduleForm.scheduledAt,
      reason: rescheduleForm.reason,
    });
    if (!parsed.success || !scheduledAt) {
      setRescheduleErrors(parsed.success ? { scheduledAt: "Reschedule time is required." } : mapZodErrors(parsed.error));
      return;
    }
    setRescheduleErrors({});
    await handleAction(async () => {
      await rescheduleCarePilotAiCallExecution(token, tenantId, rescheduleExecutionId, {
        scheduledAt,
        reason: rescheduleForm.reason.trim() || null,
      });
      setRescheduleOpen(false);
      setRescheduleExecutionId(null);
      setRescheduleForm(emptyRescheduleState());
    }, "Execution rescheduled.");
  }, [auth.accessToken, auth.tenantId, handleAction, rescheduleExecutionId, rescheduleForm]);

  const dispatchDue = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const result = await dispatchDueCarePilotAiCalls(auth.accessToken, auth.tenantId);
      setDispatchSummary(`Processed ${result.processed}, dispatched ${result.dispatched}, failed ${result.failed}, skipped ${result.skipped}.`);
      setSuccessMessage("Dispatch run completed.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Dispatch due failed");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId, load]);

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to use Jeevanam Engage AI calls.</Alert>;
  }
  if (!canView) {
    return <Alert severity="error">You do not have access to Jeevanam Engage AI calls.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Jeevanam Engage AI Calls</Typography>
          <Typography variant="body2" color="text.secondary">
            Mock-provider friendly voice orchestration with campaigns, manual queueing, dispatch, transcripts, events, escalations, and analytics.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {canMutate ? <Button variant="outlined" onClick={() => setManualCallOpen(true)} disabled={submitting}>New Manual Call</Button> : null}
          {canMutate ? <Button variant="outlined" onClick={() => void dispatchDue()} disabled={submitting}>Dispatch Due</Button> : null}
          {canMutate ? <Button variant="contained" onClick={openCreateCampaign} disabled={submitting}>New Campaign</Button> : null}
          <Button variant="text" onClick={() => void load()} disabled={submitting}>Refresh</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {successMessage ? <Alert severity="success" onClose={() => setSuccessMessage(null)}>{successMessage}</Alert> : null}
      {dispatchSummary ? <Alert severity="info" onClose={() => setDispatchSummary(null)}>{dispatchSummary}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Calls</Typography><Typography variant="h5">{analytics?.totalCalls ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Queued</Typography><Typography variant="h5">{analytics?.queuedCalls ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Completed</Typography><Typography variant="h5">{analytics?.completedCalls ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Escalations</Typography><Typography variant="h5">{analytics?.escalations ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Suppressed/Skipped</Typography><Typography variant="h5">{(analytics?.suppressedCalls ?? 0) + (analytics?.skippedCalls ?? 0)}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Scheduler Health</Typography>
          <Typography variant="body2" color="text.secondary">
            Enabled: {String(schedulerHealth?.enabled ?? false)} | Last run: {formatDateTime(schedulerHealth?.lastRunAt)} | Next estimated run: {formatDateTime(schedulerHealth?.nextEstimatedRunAt)} | Processed: {schedulerHealth?.lastProcessedCount ?? 0} | Dispatched: {schedulerHealth?.lastDispatchedCount ?? 0} | Failed: {schedulerHealth?.lastFailedCount ?? 0} | Skipped: {schedulerHealth?.lastSkippedCount ?? 0}
          </Typography>
        </CardContent>
      </Card>

      <Tabs value={tab} onChange={(_, value) => setTab(value)}>
        <Tab value="CAMPAIGNS" label="Campaigns" />
        <Tab value="EXECUTIONS" label="Executions" />
        <Tab value="ESCALATIONS" label="Escalations" />
        <Tab value="ANALYTICS" label="Analytics" />
      </Tabs>

      {loading ? <Box sx={{ minHeight: 180, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && tab === "CAMPAIGNS" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card>
              <CardContent>
                {campaigns.length === 0 ? (
                  <Alert severity="info">No AI call campaigns found.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Template</TableCell>
                        <TableCell>Retries</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {campaigns.map((campaign) => (
                        <TableRow
                          key={campaign.id}
                          hover
                          selected={selectedCampaignId === campaign.id}
                          onClick={() => {
                            setSelectedCampaignId(campaign.id);
                            setSelectedCampaign(campaign);
                          }}
                          sx={{ cursor: "pointer" }}
                        >
                          <TableCell>{campaign.name}</TableCell>
                          <TableCell>{campaign.callType}</TableCell>
                          <TableCell><Chip size="small" label={campaign.status} /></TableCell>
                          <TableCell>{voiceTemplates.find((template) => template.id === campaign.templateId)?.name || campaign.templateId || "-"}</TableCell>
                          <TableCell>{campaign.retryEnabled ? `Yes (${campaign.maxAttempts})` : "No"}</TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              {canMutate ? <Button size="small" onClick={(event) => { event.stopPropagation(); void openEditCampaign(campaign.id); }}>Edit</Button> : null}
                              {canMutate && campaign.status !== "ACTIVE" ? <Button size="small" onClick={(event) => { event.stopPropagation(); void handleAction(async () => { await updateCarePilotAiCallCampaignStatus(auth.accessToken!, auth.tenantId!, campaign.id, "ACTIVE"); }, "Campaign activated."); }}>Activate</Button> : null}
                              {canMutate && campaign.status === "ACTIVE" ? <Button size="small" onClick={(event) => { event.stopPropagation(); void handleAction(async () => { await updateCarePilotAiCallCampaignStatus(auth.accessToken!, auth.tenantId!, campaign.id, "PAUSED"); }, "Campaign paused."); }}>Pause</Button> : null}
                              {canMutate ? <Button size="small" onClick={(event) => { event.stopPropagation(); openTriggerDialog(campaign.id); }}>Trigger</Button> : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>Campaign Details</Typography>
                {!selectedCampaign ? (
                  <Alert severity="info">Select a campaign to inspect its configuration.</Alert>
                ) : (
                  <Stack spacing={1}>
                    <Typography variant="body2"><b>Name:</b> {selectedCampaign.name}</Typography>
                    <Typography variant="body2"><b>Status:</b> {selectedCampaign.status}</Typography>
                    <Typography variant="body2"><b>Type:</b> {selectedCampaign.callType}</Typography>
                    <Typography variant="body2"><b>Description:</b> {selectedCampaign.description || "-"}</Typography>
                    <Typography variant="body2"><b>Template:</b> {voiceTemplates.find((template) => template.id === selectedCampaign.templateId)?.name || selectedCampaign.templateId || "-"}</Typography>
                    <Typography variant="body2"><b>Retry enabled:</b> {selectedCampaign.retryEnabled ? "Yes" : "No"}</Typography>
                    <Typography variant="body2"><b>Max attempts:</b> {selectedCampaign.maxAttempts}</Typography>
                    <Typography variant="body2"><b>Escalation enabled:</b> {selectedCampaign.escalationEnabled ? "Yes" : "No"}</Typography>
                    <Typography variant="body2"><b>Created:</b> {formatDateTime(selectedCampaign.createdAt)}</Typography>
                    <Typography variant="body2"><b>Updated:</b> {formatDateTime(selectedCampaign.updatedAt)}</Typography>
                  </Stack>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "EXECUTIONS" ? (
        <Stack spacing={1}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
            <TextField
              select
              label="Status"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as CarePilotAiCallExecutionStatus | "")}
              sx={{ maxWidth: 280 }}
            >
              <MenuItem value="">All</MenuItem>
              {executionStatuses.map((status) => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </TextField>
          </Stack>
          <Card>
            <CardContent>
              {executions.length === 0 ? (
                <Alert severity="info">No executions found.</Alert>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Phone</TableCell>
                      <TableCell>Campaign</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Provider</TableCell>
                      <TableCell>Scheduled</TableCell>
                      <TableCell>Retries</TableCell>
                      <TableCell>Reason</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {executions.map((execution) => (
                      <TableRow key={execution.id}>
                        <TableCell>{execution.phoneNumber}</TableCell>
                        <TableCell>{campaigns.find((campaign) => campaign.id === execution.campaignId)?.name || execution.campaignId}</TableCell>
                        <TableCell><Chip size="small" label={execution.executionStatus} color={executionStatusColor(execution.executionStatus)} /></TableCell>
                        <TableCell>{execution.providerName || "-"}</TableCell>
                        <TableCell>{formatDateTime(execution.scheduledAt)}</TableCell>
                        <TableCell>{execution.retryCount}</TableCell>
                        <TableCell>{execution.suppressionReason || execution.failureReason || execution.escalationReason || "-"}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                            <Button size="small" onClick={() => void openExecutionDetails(execution.id)}>Details</Button>
                            <Button size="small" onClick={() => void openEvents(execution.id)}>Events</Button>
                            {execution.transcriptId ? <Button size="small" onClick={() => void openTranscript(execution.id)}>Transcript</Button> : null}
                            {canMutate ? (
                              <Button
                                size="small"
                                onClick={() => void handleAction(async () => {
                                  await retryCarePilotAiCallExecution(auth.accessToken!, auth.tenantId!, execution.id);
                                }, "Execution retried.")}
                              >
                                Retry
                              </Button>
                            ) : null}
                            {canMutate ? (
                              <Button
                                size="small"
                                onClick={() => void handleAction(async () => {
                                  await cancelCarePilotAiCallExecution(auth.accessToken!, auth.tenantId!, execution.id, "Operator cancelled");
                                }, "Execution cancelled.")}
                              >
                                Cancel
                              </Button>
                            ) : null}
                            {canMutate ? (
                              <Button
                                size="small"
                                onClick={() => void handleAction(async () => {
                                  await suppressCarePilotAiCallExecution(auth.accessToken!, auth.tenantId!, execution.id, "Operator suppressed");
                                }, "Execution suppressed.")}
                              >
                                Suppress
                              </Button>
                            ) : null}
                            {canMutate ? <Button size="small" onClick={() => openReschedule(execution)}>Reschedule</Button> : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Stack>
      ) : null}

      {!loading && tab === "ESCALATIONS" ? (
        <Card>
          <CardContent>
            {escalations.length === 0 ? (
              <Alert severity="info">No escalated calls currently.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Phone</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>Campaign</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {escalations.map((execution) => (
                    <TableRow key={execution.id}>
                      <TableCell>{execution.phoneNumber}</TableCell>
                      <TableCell><Chip size="small" color="warning" label={execution.executionStatus} /></TableCell>
                      <TableCell>{execution.escalationReason || execution.failureReason || execution.transcriptSummary || "-"}</TableCell>
                      <TableCell>{campaigns.find((campaign) => campaign.id === execution.campaignId)?.name || execution.campaignId}</TableCell>
                      <TableCell align="right"><Button size="small" onClick={() => void openExecutionDetails(execution.id)}>View Details</Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : null}

      {!loading && tab === "ANALYTICS" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={0.75}>
                  <Typography variant="body2">Total calls: {analytics?.totalCalls ?? 0}</Typography>
                  <Typography variant="body2">Completed calls: {analytics?.completedCalls ?? 0}</Typography>
                  <Typography variant="body2">Failed calls: {analytics?.failedCalls ?? 0}</Typography>
                  <Typography variant="body2">Escalations: {analytics?.escalations ?? 0}</Typography>
                  <Typography variant="body2">Queued calls: {analytics?.queuedCalls ?? 0}</Typography>
                  <Typography variant="body2">Suppressed calls: {analytics?.suppressedCalls ?? 0}</Typography>
                  <Typography variant="body2">Skipped calls: {analytics?.skippedCalls ?? 0}</Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={0.75}>
                  <Typography variant="body2">No-answer rate: {(analytics?.noAnswerRate ?? 0).toFixed(1)}%</Typography>
                  <Typography variant="body2">Retry rate: {(analytics?.retryRate ?? 0).toFixed(1)}%</Typography>
                  <Typography variant="body2">Average duration: {(analytics?.averageDurationSeconds ?? 0).toFixed(1)}s</Typography>
                  <Typography variant="body2">Scheduler enabled: {String(schedulerHealth?.enabled ?? false)}</Typography>
                  <Typography variant="body2">Last dispatch run: {formatDateTime(schedulerHealth?.lastRunAt)}</Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={campaignDialogOpen} onClose={() => setCampaignDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{campaignForm.id ? "Edit AI Call Campaign" : "New AI Call Campaign"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Name *" value={campaignForm.name} onChange={(event) => setCampaignForm((current) => ({ ...current, name: event.target.value }))} required error={Boolean(campaignErrors.name)} helperText={campaignErrors.name || "Campaign name must be 60 characters or fewer."} inputProps={{ maxLength: 60 }} />
            <TextField label="Description" value={campaignForm.description} onChange={(event) => setCampaignForm((current) => ({ ...current, description: event.target.value }))} multiline minRows={2} inputProps={{ maxLength: 250 }} error={Boolean(campaignErrors.description)} helperText={campaignErrors.description || "Description must be 250 characters or fewer."} />
            <TextField select label="Call Type *" value={campaignForm.callType} onChange={(event) => setCampaignForm((current) => ({ ...current, callType: event.target.value as CarePilotAiCallType }))}>
              {callTypes.map((callType) => <MenuItem key={callType} value={callType}>{callType}</MenuItem>)}
            </TextField>
            <TextField select label="Status *" value={campaignForm.status} onChange={(event) => setCampaignForm((current) => ({ ...current, status: event.target.value as CarePilotAiCallCampaignStatus }))}>
              {campaignStatuses.map((status) => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </TextField>
            <TextField
              select
              label="Voice Template"
              value={campaignForm.templateId}
              onChange={(event) => setCampaignForm((current) => ({ ...current, templateId: event.target.value }))}
              helperText="Optional. Uses existing admin templates with channel VOICE."
            >
              <MenuItem value="">None</MenuItem>
              {voiceTemplates.map((template) => <MenuItem key={template.id} value={template.id}>{template.name}</MenuItem>)}
            </TextField>
            <FormControlLabel
              control={<Checkbox checked={campaignForm.retryEnabled} onChange={(event) => setCampaignForm((current) => ({ ...current, retryEnabled: event.target.checked }))} />}
              label="Enable retries"
            />
            <TextField
              label="Max Attempts"
              type="number"
              value={campaignForm.maxAttempts}
              onChange={(event) => setCampaignForm((current) => ({ ...current, maxAttempts: Number(event.target.value) || 1 }))}
              disabled={!campaignForm.retryEnabled}
              inputProps={{ min: 1, max: 10 }}
              error={Boolean(campaignErrors.maxAttempts)}
              helperText={campaignErrors.maxAttempts || "Max attempts must be 1 or greater."}
            />
            <FormControlLabel
              control={<Checkbox checked={campaignForm.escalationEnabled} onChange={(event) => setCampaignForm((current) => ({ ...current, escalationEnabled: event.target.checked }))} />}
              label="Enable escalation"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCampaignDialogOpen(false)}>Close</Button>
          <Button onClick={() => void saveCampaign()} variant="contained" disabled={submitting}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={manualCallOpen} onClose={() => setManualCallOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Queue Manual Call</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Phone *" value={manualCallForm.phoneNumber} onChange={(event) => setManualCallForm((current) => ({ ...current, phoneNumber: event.target.value }))} required inputProps={{ inputMode: "tel" }} error={Boolean(manualCallErrors.phoneNumber)} helperText={manualCallErrors.phoneNumber || "Enter a valid 10-digit Indian mobile number."} />
            <TextField select label="Call Type *" value={manualCallForm.callType} onChange={(event) => setManualCallForm((current) => ({ ...current, callType: event.target.value as CarePilotAiCallType }))}>
              {callTypes.map((callType) => <MenuItem key={callType} value={callType}>{callType}</MenuItem>)}
            </TextField>
            <TextField label="Patient ID" value={manualCallForm.patientId} onChange={(event) => setManualCallForm((current) => ({ ...current, patientId: event.target.value }))} />
            <TextField label="Lead ID" value={manualCallForm.leadId} onChange={(event) => setManualCallForm((current) => ({ ...current, leadId: event.target.value }))} />
            <TextField select label="Voice Template" value={manualCallForm.templateId} onChange={(event) => setManualCallForm((current) => ({ ...current, templateId: event.target.value }))}>
              <MenuItem value="">None</MenuItem>
              {voiceTemplates.map((template) => <MenuItem key={template.id} value={template.id}>{template.name}</MenuItem>)}
            </TextField>
            <TextField label="Script" value={manualCallForm.script} onChange={(event) => setManualCallForm((current) => ({ ...current, script: event.target.value }))} multiline minRows={3} inputProps={{ maxLength: 250 }} helperText={manualCallErrors.script || "Optional script override. Max 250 characters."} error={Boolean(manualCallErrors.script)} />
            <TextField label="Schedule For" type="datetime-local" value={manualCallForm.scheduledAt} onChange={(event) => setManualCallForm((current) => ({ ...current, scheduledAt: event.target.value }))} InputLabelProps={{ shrink: true }} helperText={manualCallErrors.scheduledAt || "Leave empty to queue immediately."} error={Boolean(manualCallErrors.scheduledAt)} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setManualCallOpen(false)}>Close</Button>
          <Button onClick={() => void submitManualCall()} variant="contained" disabled={submitting}>Queue Call</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={triggerOpen} onClose={() => setTriggerOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Trigger {selectedCampaignName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {triggerErrors._form ? <Alert severity="error">{triggerErrors._form}</Alert> : null}
            <Alert severity="info">Add one or more targets. Phone is required only when patientId and leadId are both empty.</Alert>
            {triggerTargets.map((target, index) => (
              <Card key={index} variant="outlined">
                <CardContent>
                  <Stack spacing={2}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Target {index + 1}</Typography>
                      {triggerTargets.length > 1 ? <Button color="error" onClick={() => setTriggerTargets((current) => current.filter((_, currentIndex) => currentIndex !== index))}>Remove</Button> : null}
                    </Box>
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField fullWidth label="Phone" value={target.phoneNumber} onChange={(event) => setTriggerTargets((current) => current.map((row, currentIndex) => currentIndex === index ? { ...row, phoneNumber: event.target.value } : row))} inputProps={{ inputMode: "tel" }} error={Boolean(triggerErrors[`targets.${index}.phoneNumber`])} helperText={triggerErrors[`targets.${index}.phoneNumber`] || "Optional unless patient and lead IDs are empty."} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField fullWidth label="Schedule For" type="datetime-local" value={target.scheduledAt} onChange={(event) => setTriggerTargets((current) => current.map((row, currentIndex) => currentIndex === index ? { ...row, scheduledAt: event.target.value } : row))} InputLabelProps={{ shrink: true }} error={Boolean(triggerErrors[`targets.${index}.scheduledAt`])} helperText={triggerErrors[`targets.${index}.scheduledAt`] || "Optional scheduled time."} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField fullWidth label="Patient ID" value={target.patientId} onChange={(event) => setTriggerTargets((current) => current.map((row, currentIndex) => currentIndex === index ? { ...row, patientId: event.target.value } : row))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField fullWidth label="Lead ID" value={target.leadId} onChange={(event) => setTriggerTargets((current) => current.map((row, currentIndex) => currentIndex === index ? { ...row, leadId: event.target.value } : row))} />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <TextField fullWidth label="Script" value={target.script} onChange={(event) => setTriggerTargets((current) => current.map((row, currentIndex) => currentIndex === index ? { ...row, script: event.target.value } : row))} multiline minRows={2} inputProps={{ maxLength: 250 }} helperText={triggerErrors[`targets.${index}.script`] || "Optional script override."} error={Boolean(triggerErrors[`targets.${index}.script`])} />
                      </Grid>
                    </Grid>
                  </Stack>
                </CardContent>
              </Card>
            ))}
            <Box>
              <Button onClick={() => setTriggerTargets((current) => [...current, emptyTriggerTarget()])}>Add Target</Button>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTriggerOpen(false)}>Close</Button>
          <Button onClick={() => void submitTrigger()} variant="contained" disabled={submitting}>Queue Calls</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={executionDetailOpen} onClose={() => setExecutionDetailOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Execution Details</DialogTitle>
        <DialogContent>
          {!selectedExecution ? (
            <Alert severity="info">Execution not loaded.</Alert>
          ) : (
            <Grid container spacing={2} sx={{ pt: 1 }}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Typography variant="body2"><b>Execution ID:</b> {selectedExecution.id}</Typography>
                <Typography variant="body2"><b>Campaign:</b> {campaigns.find((campaign) => campaign.id === selectedExecution.campaignId)?.name || selectedExecution.campaignId}</Typography>
                <Typography variant="body2"><b>Status:</b> {selectedExecution.executionStatus}</Typography>
                <Typography variant="body2"><b>Phone:</b> {selectedExecution.phoneNumber}</Typography>
                <Typography variant="body2"><b>Patient ID:</b> {selectedExecution.patientId || "-"}</Typography>
                <Typography variant="body2"><b>Lead ID:</b> {selectedExecution.leadId || "-"}</Typography>
                <Typography variant="body2"><b>Provider:</b> {selectedExecution.providerName || "-"}</Typography>
                <Typography variant="body2"><b>Provider Call ID:</b> {selectedExecution.providerCallId || "-"}</Typography>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Typography variant="body2"><b>Scheduled:</b> {formatDateTime(selectedExecution.scheduledAt)}</Typography>
                <Typography variant="body2"><b>Started:</b> {formatDateTime(selectedExecution.startedAt)}</Typography>
                <Typography variant="body2"><b>Ended:</b> {formatDateTime(selectedExecution.endedAt)}</Typography>
                <Typography variant="body2"><b>Duration:</b> {selectedExecution.durationSeconds ? `${selectedExecution.durationSeconds}s` : "-"}</Typography>
                <Typography variant="body2"><b>Retry Count:</b> {selectedExecution.retryCount}</Typography>
                <Typography variant="body2"><b>Next Retry:</b> {formatDateTime(selectedExecution.nextRetryAt)}</Typography>
                <Typography variant="body2"><b>Escalation:</b> {selectedExecution.escalationRequired ? selectedExecution.escalationReason || "Required" : "No"}</Typography>
                <Typography variant="body2"><b>Failure Reason:</b> {selectedExecution.failureReason || "-"}</Typography>
                <Typography variant="body2"><b>Suppression Reason:</b> {selectedExecution.suppressionReason || "-"}</Typography>
                <Typography variant="body2"><b>Transcript Summary:</b> {selectedExecution.transcriptSummary || "-"}</Typography>
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          {selectedExecution?.transcriptId ? <Button onClick={() => void openTranscript(selectedExecution.id)}>Transcript</Button> : null}
          {selectedExecution ? <Button onClick={() => void openEvents(selectedExecution.id)}>Events</Button> : null}
          <Button onClick={() => setExecutionDetailOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={transcriptOpen} onClose={() => setTranscriptOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Call Transcript</DialogTitle>
        <DialogContent>
          {!transcript ? (
            <Alert severity="info">Transcript not found.</Alert>
          ) : (
            <Stack spacing={1} sx={{ pt: 1 }}>
              <Typography variant="body2"><b>Summary:</b> {transcript.summary || "-"}</Typography>
              <Typography variant="body2"><b>Outcome:</b> {transcript.outcome || "-"}</Typography>
              <Typography variant="body2"><b>Sentiment:</b> {transcript.sentiment || "-"}</Typography>
              <Typography variant="body2"><b>Intent:</b> {transcript.intent || "-"}</Typography>
              <Typography variant="body2"><b>Follow-up required:</b> {transcript.requiresFollowUp ? "Yes" : "No"}</Typography>
              <Typography variant="body2"><b>Escalation reason:</b> {transcript.escalationReason || "-"}</Typography>
              <TextField multiline minRows={8} value={transcript.transcriptText || ""} InputProps={{ readOnly: true }} />
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTranscriptOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={eventsOpen} onClose={() => setEventsOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Execution Events</DialogTitle>
        <DialogContent>
          {events.length === 0 ? (
            <Alert severity="info">No events found.</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Provider</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Detail</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell>{formatDateTime(event.eventTimestamp || event.createdAt)}</TableCell>
                    <TableCell>{event.eventType}</TableCell>
                    <TableCell>{event.providerName}</TableCell>
                    <TableCell>{event.internalStatus || event.externalStatus || "-"}</TableCell>
                    <TableCell>{event.rawPayloadRedacted || "-"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEventsOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Reschedule Execution</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="New Scheduled Time *" type="datetime-local" value={rescheduleForm.scheduledAt} onChange={(event) => setRescheduleForm((current) => ({ ...current, scheduledAt: event.target.value }))} InputLabelProps={{ shrink: true }} required error={Boolean(rescheduleErrors.scheduledAt)} helperText={rescheduleErrors.scheduledAt || "Reschedule time is required."} />
            <TextField label="Reason" value={rescheduleForm.reason} onChange={(event) => setRescheduleForm((current) => ({ ...current, reason: event.target.value }))} multiline minRows={2} inputProps={{ maxLength: 250 }} error={Boolean(rescheduleErrors.reason)} helperText={rescheduleErrors.reason || "Optional reason, max 250 characters."} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRescheduleOpen(false)}>Close</Button>
          <Button onClick={() => void submitReschedule()} variant="contained" disabled={submitting}>Save</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
