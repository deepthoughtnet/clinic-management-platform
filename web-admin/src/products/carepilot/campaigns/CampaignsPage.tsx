import * as React from "react";
import { useLocation } from "react-router-dom";
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

import { useAuth } from "../../../auth/useAuth";
import {
  activateCarePilotCampaign,
  createCarePilotCampaign,
  createCarePilotTemplate,
  deactivateCarePilotCampaign,
  getCarePilotCampaignRuntime,
  listCarePilotCampaigns,
  listCarePilotDeliveryAttempts,
  listCarePilotExecutions,
  listCarePilotFailedExecutions,
  listCarePilotMessagingProviderStatuses,
  listCarePilotTemplates,
  patchCarePilotTemplate,
  resendCarePilotExecution,
  retryCarePilotExecution,
  type CarePilotAudienceType,
  type CarePilotCampaign,
  type CarePilotCampaignStatus,
  type CarePilotCampaignRuntime,
  type CarePilotCampaignType,
  type CarePilotChannelType,
  type CarePilotDeliveryAttempt,
  type CarePilotExecution,
  type CarePilotExecutionStatus,
  type CarePilotMessagingProviderStatus,
  type CarePilotTemplate,
  type CarePilotTriggerType,
} from "../../../api/clinicApi";
import { ApiClientError } from "../../../api/restClient";
import { CAMPAIGN_PRESETS, presetStatusLabel, type CampaignPreset } from "./campaignPresets";

const CAMPAIGN_TYPES: CarePilotCampaignType[] = [
  "APPOINTMENT_REMINDER",
  "MISSED_APPOINTMENT_FOLLOW_UP",
  "FOLLOW_UP_REMINDER",
  "REFILL_REMINDER",
  "VACCINATION_REMINDER",
  "BILLING_REMINDER",
  "WELLNESS_MESSAGE",
  "CUSTOM",
];
const TRIGGER_TYPES: CarePilotTriggerType[] = ["MANUAL", "SCHEDULED", "EVENT_BASED"];
const AUDIENCE_TYPES: CarePilotAudienceType[] = [
  "ALL_PATIENTS",
  "SPECIFIC_PATIENTS",
  "TAG_BASED",
  "RULE_BASED",
  "HIGH_RISK_PATIENTS",
  "INACTIVE_PATIENTS",
  "REFILL_RISK_PATIENTS",
  "FOLLOW_UP_OVERDUE_PATIENTS",
];
const CHANNEL_TYPES: CarePilotChannelType[] = ["EMAIL", "SMS", "WHATSAPP", "IN_APP", "APP_NOTIFICATION"];
const EXECUTION_STATUSES: CarePilotExecutionStatus[] = ["QUEUED", "PROCESSING", "SUCCEEDED", "FAILED", "DEAD_LETTER", "RETRY_SCHEDULED", "CANCELLED"];

function channelOptionLabel(channel: CarePilotChannelType, providerStatusByChannel: Partial<Record<"SMS" | "WHATSAPP", CarePilotMessagingProviderStatus>>): string {
  if (channel === "SMS" && providerStatusByChannel.SMS?.status !== "READY") return "SMS - Provider not ready";
  if (channel === "WHATSAPP" && providerStatusByChannel.WHATSAPP?.status !== "READY") return "WhatsApp - Provider not ready";
  return channel;
}

function isProviderNotReadyChannel(
  channel: CarePilotChannelType,
  providerStatusByChannel: Partial<Record<"SMS" | "WHATSAPP", CarePilotMessagingProviderStatus>>,
): boolean {
  if (channel === "SMS") return providerStatusByChannel.SMS?.status !== "READY";
  if (channel === "WHATSAPP") return providerStatusByChannel.WHATSAPP?.status !== "READY";
  return false;
}

function executionStatusColor(status: CarePilotExecutionStatus) {
  if (status === "SUCCEEDED") return "success" as const;
  if (status === "FAILED" || status === "DEAD_LETTER") return "error" as const;
  if (status === "PROCESSING" || status === "RETRY_SCHEDULED") return "warning" as const;
  if (status === "QUEUED") return "info" as const;
  return "default" as const;
}

function campaignStatusColor(status: CarePilotCampaignStatus) {
  if (status === "ACTIVE") return "success" as const;
  if (status === "PAUSED") return "warning" as const;
  return "default" as const;
}

function runtimeStatusColor(status: string | null) {
  if (status === "SUCCEEDED") return "success" as const;
  if (status === "FAILED" || status === "DEAD_LETTER") return "error" as const;
  if (status === "RETRY_SCHEDULED" || status === "PROCESSING") return "warning" as const;
  if (status === "QUEUED") return "info" as const;
  return "default" as const;
}

function runtimeStatusLabel(status: string | null, total: number) {
  if (total === 0) return "No executions yet";
  if (status === "SUCCEEDED") return "Sent";
  if (status === "FAILED" || status === "DEAD_LETTER") return "Failed";
  if (status === "RETRY_SCHEDULED") return "Retrying";
  if (status === "PROCESSING") return "Sending";
  if (status === "QUEUED") return "Scheduled";
  return status || "Scheduled";
}

type CreateCampaignForm = {
  name: string;
  campaignType: CarePilotCampaignType;
  triggerType: CarePilotTriggerType;
  audienceType: CarePilotAudienceType;
  templateId: string;
  notes: string;
};

type TemplateForm = {
  name: string;
  channelType: CarePilotChannelType;
  subjectLine: string;
  bodyTemplate: string;
  active: boolean;
};

type PresetCreateForm = {
  name: string;
  notes: string;
  audienceType: CarePilotAudienceType;
  triggerConfigText: string;
  templateSubject: string;
  templateBody: string;
  activateNow: boolean;
};
type TemplateSource = "EXISTING" | "PREDEFINED" | "NONE";

function emptyCampaignForm(): CreateCampaignForm {
  return {
    name: "",
    campaignType: "CUSTOM",
    triggerType: "MANUAL",
    audienceType: "ALL_PATIENTS",
    templateId: "",
    notes: "",
  };
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function emptyTemplateForm(): TemplateForm {
  return {
    name: "",
    channelType: "EMAIL",
    subjectLine: "",
    bodyTemplate: "",
    active: true,
  };
}

function emptyPresetForm(preset: CampaignPreset): PresetCreateForm {
  return {
    name: preset.displayName,
    notes: preset.description,
    audienceType: preset.audienceType,
    triggerConfigText: JSON.stringify(preset.defaultTriggerConfig, null, 2),
    templateSubject: preset.defaultTemplateSubject,
    templateBody: preset.defaultTemplateBody,
    activateNow: false,
  };
}

function previewTemplate(body: string): string {
  return body
    .replaceAll("{{patientName}}", "John Doe")
    .replaceAll("{{doctorName}}", "Dr. Priya Shah")
    .replaceAll("{{clinicName}}", "Sunrise Clinic")
    .replaceAll("{{appointmentDate}}", "2026-05-20")
    .replaceAll("{{appointmentTime}}", "10:30 AM")
    .replaceAll("{{followUpDate}}", "2026-05-28")
    .replaceAll("{{billNumber}}", "BILL-1024")
    .replaceAll("{{amountDue}}", "₹ 1,250")
    .replaceAll("{{medicineName}}", "Atorvastatin 10mg")
    .replaceAll("{{refillDueDate}}", "2026-06-01")
    .replaceAll("{{vaccineName}}", "Flu Vaccine")
    .replaceAll("{{vaccinationDueDate}}", "2026-06-10")
    .replaceAll("{{clinicPhone}}", "+1 555 0100");
}

export default function CampaignsPage() {
  const location = useLocation();
  const auth = useAuth();
  const [tab, setTab] = React.useState<"campaigns" | "templates" | "executions" | "failed">("campaigns");
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [templates, setTemplates] = React.useState<CarePilotTemplate[]>([]);
  const [executions, setExecutions] = React.useState<CarePilotExecution[]>([]);
  const [failedExecutions, setFailedExecutions] = React.useState<CarePilotExecution[]>([]);
  const [campaignRuntime, setCampaignRuntime] = React.useState<CarePilotCampaignRuntime | null>(null);
  const [runtimeLoading, setRuntimeLoading] = React.useState(false);
  const [selectedCampaign, setSelectedCampaign] = React.useState<CarePilotCampaign | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [workingId, setWorkingId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<{ type: "success" | "error"; text: string } | null>(null);

  const [campaignSearch, setCampaignSearch] = React.useState("");
  const [campaignStatusFilter, setCampaignStatusFilter] = React.useState<CarePilotCampaignStatus | "">("");
  const [campaignTypeFilter, setCampaignTypeFilter] = React.useState<CarePilotCampaignType | "">("");
  const [campaignTriggerFilter, setCampaignTriggerFilter] = React.useState<CarePilotTriggerType | "">("");
  const [campaignChannelFilter, setCampaignChannelFilter] = React.useState<CarePilotChannelType | "">("");

  const [executionStatusFilter, setExecutionStatusFilter] = React.useState<CarePilotExecutionStatus | "">("");
  const [executionChannelFilter, setExecutionChannelFilter] = React.useState<CarePilotChannelType | "">("");
  const [executionCampaignFilter, setExecutionCampaignFilter] = React.useState("");
  const [executionFromDate, setExecutionFromDate] = React.useState("");
  const [executionToDate, setExecutionToDate] = React.useState("");

  const [createCampaignOpen, setCreateCampaignOpen] = React.useState(false);
  const [campaignForm, setCampaignForm] = React.useState<CreateCampaignForm>(emptyCampaignForm());
  const [campaignTemplateSource, setCampaignTemplateSource] = React.useState<TemplateSource>("EXISTING");
  const [campaignPresetKey, setCampaignPresetKey] = React.useState("");
  const [campaignPresetSubject, setCampaignPresetSubject] = React.useState("");
  const [campaignPresetBody, setCampaignPresetBody] = React.useState("");
  const [presetOpen, setPresetOpen] = React.useState(false);
  const [presetStep, setPresetStep] = React.useState<1 | 2 | 3>(1);
  const [selectedPreset, setSelectedPreset] = React.useState<CampaignPreset | null>(null);
  const [presetForm, setPresetForm] = React.useState<PresetCreateForm | null>(null);
  const [templateForm, setTemplateForm] = React.useState<TemplateForm>(emptyTemplateForm());
  const [templatePresetKey, setTemplatePresetKey] = React.useState("");
  const [editingTemplateId, setEditingTemplateId] = React.useState<string | null>(null);

  const [attemptsOpen, setAttemptsOpen] = React.useState(false);
  const [attemptsTitle, setAttemptsTitle] = React.useState("");
  const [attemptsLoading, setAttemptsLoading] = React.useState(false);
  const [attempts, setAttempts] = React.useState<CarePilotDeliveryAttempt[]>([]);
  const [providerStatusByChannel, setProviderStatusByChannel] = React.useState<Partial<Record<"SMS" | "WHATSAPP", CarePilotMessagingProviderStatus>>>({});

  const isClinicAdmin = auth.rolesUpper.includes("CLINIC_ADMIN");
  const isAuditor = auth.rolesUpper.includes("AUDITOR");
  const isPlatformAdminWithTenant = auth.rolesUpper.includes("PLATFORM_ADMIN") && Boolean(auth.tenantId);
  // Platform admins can operate CarePilot only in an explicit tenant context.
  // This keeps platform-global mode isolated from tenant-scoped campaign data.
  const canView = isClinicAdmin || isAuditor || isPlatformAdminWithTenant;
  const canManage = isClinicAdmin || isPlatformAdminWithTenant;

  const templateById = React.useMemo(() => {
    const map = new Map<string, CarePilotTemplate>();
    for (const template of templates) map.set(template.id, template);
    return map;
  }, [templates]);

  const campaignById = React.useMemo(() => {
    const map = new Map<string, CarePilotCampaign>();
    for (const campaign of campaigns) map.set(campaign.id, campaign);
    return map;
  }, [campaigns]);

  const selectedCampaignPreset = React.useMemo(
    () => CAMPAIGN_PRESETS.find((p) => p.presetKey === campaignPresetKey) || null,
    [campaignPresetKey],
  );
  const selectedExistingTemplate = React.useMemo(
    () => templates.find((t) => t.id === campaignForm.templateId) || null,
    [templates, campaignForm.templateId],
  );

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignRows, templateRows, executionRows, failedRows, providerRows] = await Promise.all([
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        listCarePilotTemplates(auth.accessToken, auth.tenantId),
        listCarePilotExecutions(auth.accessToken, auth.tenantId),
        listCarePilotFailedExecutions(auth.accessToken, auth.tenantId),
        listCarePilotMessagingProviderStatuses(auth.accessToken, auth.tenantId),
      ]);
      setCampaigns(campaignRows);
      setTemplates(templateRows);
      setExecutions(executionRows);
      setFailedExecutions(failedRows);
      setProviderStatusByChannel({
        SMS: providerRows.find((row) => row.channel === "SMS"),
        WHATSAPP: providerRows.find((row) => row.channel === "WHATSAPP"),
      });
      const queryCampaignId = new URLSearchParams(location.search).get("campaignId");
      if (queryCampaignId) {
        setSelectedCampaign(campaignRows.find((x) => x.id === queryCampaignId) || campaignRows[0] || null);
      } else {
        setSelectedCampaign((current) => campaignRows.find((x) => x.id === current?.id) || campaignRows[0] || null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load CarePilot data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, location.search]);

  React.useEffect(() => {
    void loadAll();
  }, [loadAll]);

  React.useEffect(() => {
    async function loadRuntime(campaignId: string) {
      if (!auth.accessToken || !auth.tenantId) return;
      setRuntimeLoading(true);
      try {
        const runtime = await getCarePilotCampaignRuntime(auth.accessToken, auth.tenantId, campaignId);
        setCampaignRuntime(runtime);
      } catch {
        setCampaignRuntime(null);
      } finally {
        setRuntimeLoading(false);
      }
    }
    if (selectedCampaign?.id) {
      void loadRuntime(selectedCampaign.id);
    } else {
      setCampaignRuntime(null);
    }
  }, [auth.accessToken, auth.tenantId, selectedCampaign?.id]);

  const filteredCampaigns = React.useMemo(() => {
    const q = campaignSearch.trim().toLowerCase();
    return campaigns.filter((campaign) => {
      if (campaignStatusFilter && campaign.status !== campaignStatusFilter) return false;
      if (campaignTypeFilter && campaign.campaignType !== campaignTypeFilter) return false;
      if (campaignTriggerFilter && campaign.triggerType !== campaignTriggerFilter) return false;
      const template = campaign.templateId ? templateById.get(campaign.templateId) : null;
      if (campaignChannelFilter && template?.channelType !== campaignChannelFilter) return false;
      if (!q) return true;
      return `${campaign.name} ${campaign.notes || ""} ${campaign.id}`.toLowerCase().includes(q);
    });
  }, [campaigns, campaignSearch, campaignStatusFilter, campaignTypeFilter, campaignTriggerFilter, campaignChannelFilter, templateById]);

  const filteredExecutions = React.useMemo(() => {
    const from = executionFromDate ? new Date(executionFromDate) : null;
    const to = executionToDate ? new Date(executionToDate) : null;
    return executions.filter((execution) => {
      if (executionStatusFilter && execution.status !== executionStatusFilter) return false;
      if (executionChannelFilter && execution.channelType !== executionChannelFilter) return false;
      if (executionCampaignFilter && execution.campaignId !== executionCampaignFilter) return false;
      const scheduled = new Date(execution.scheduledAt);
      if (from && scheduled < from) return false;
      if (to && scheduled > new Date(`${executionToDate}T23:59:59`)) return false;
      return true;
    });
  }, [executions, executionStatusFilter, executionChannelFilter, executionCampaignFilter, executionFromDate, executionToDate]);

  const onCreateCampaign = async () => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    if (!campaignForm.name.trim()) {
      setToast({ type: "error", text: "Campaign name is required." });
      return;
    }
    const normalizedTemplateId = campaignForm.templateId.trim();
    if (campaignTemplateSource === "EXISTING" && normalizedTemplateId && !UUID_RE.test(normalizedTemplateId)) {
      setToast({ type: "error", text: "Invalid template selection. Please choose a valid template." });
      return;
    }
    if (campaignTemplateSource === "PREDEFINED" && !selectedCampaignPreset) {
      setToast({ type: "error", text: "Select a predefined template preset." });
      return;
    }
    if (campaignTemplateSource === "PREDEFINED" && selectedCampaignPreset?.implementationStatus === "FUTURE") {
      setToast({ type: "error", text: "This preset is coming soon and cannot be used yet." });
      return;
    }
    setWorkingId("campaign-create");
    try {
      let resolvedTemplateId: string | null = null;
      if (campaignTemplateSource === "EXISTING") {
        resolvedTemplateId = normalizedTemplateId || null;
      }
      if (campaignTemplateSource === "PREDEFINED" && selectedCampaignPreset) {
        const seededTemplate = await createCarePilotTemplate(auth.accessToken, auth.tenantId, {
          name: `${campaignForm.name.trim()} Template`,
          channelType: selectedCampaignPreset.defaultChannel,
          subjectLine: campaignPresetSubject.trim() || null,
          bodyTemplate: campaignPresetBody,
          active: true,
        });
        resolvedTemplateId = seededTemplate.id;
      }
      const payload = {
        name: campaignForm.name.trim(),
        campaignType: campaignForm.campaignType,
        triggerType: campaignForm.triggerType,
        audienceType: campaignForm.audienceType,
        templateId: resolvedTemplateId,
        notes: campaignForm.notes.trim() || null,
      };
      await createCarePilotCampaign(auth.accessToken, auth.tenantId, {
        ...payload,
      });
      setCreateCampaignOpen(false);
      setCampaignForm(emptyCampaignForm());
      setCampaignTemplateSource("EXISTING");
      setCampaignPresetKey("");
      setCampaignPresetSubject("");
      setCampaignPresetBody("");
      setToast({ type: "success", text: "Campaign created." });
      await loadAll();
    } catch (err) {
      if (err instanceof ApiClientError) {
        setToast({ type: "error", text: `Unable to create campaign. ${err.message}` });
      } else {
        setToast({ type: "error", text: err instanceof Error ? err.message : "Failed to create campaign" });
      }
    } finally {
      setWorkingId(null);
    }
  };

  const onChooseCampaignPreset = (presetKey: string) => {
    setCampaignPresetKey(presetKey);
    const preset = CAMPAIGN_PRESETS.find((row) => row.presetKey === presetKey);
    if (!preset) return;
    setCampaignForm((c) => ({
      ...c,
      campaignType: preset.campaignType,
      triggerType: preset.triggerType,
      audienceType: preset.audienceType,
      name: c.name || preset.displayName,
      notes: c.notes || preset.description,
    }));
    setCampaignPresetSubject(preset.defaultTemplateSubject);
    setCampaignPresetBody(preset.defaultTemplateBody);
  };

  const onSelectPreset = (preset: CampaignPreset) => {
    setSelectedPreset(preset);
    setPresetForm(emptyPresetForm(preset));
    setPresetStep(2);
  };

  const onCreateFromPreset = async () => {
    if (!auth.accessToken || !auth.tenantId || !canManage || !selectedPreset || !presetForm) return;
    if (selectedPreset.implementationStatus === "FUTURE") return;
    if (!presetForm.name.trim()) {
      setToast({ type: "error", text: "Campaign name is required." });
      return;
    }
    setWorkingId("campaign-preset-create");
    try {
      const notes = `${presetForm.notes.trim() || ""}\n\n[Trigger Config]\n${presetForm.triggerConfigText}`.trim();
      const template = await createCarePilotTemplate(auth.accessToken, auth.tenantId, {
        name: `${presetForm.name.trim()} Template`,
        channelType: "EMAIL",
        subjectLine: presetForm.templateSubject.trim() || null,
        bodyTemplate: presetForm.templateBody,
        active: true,
      });
      const campaign = await createCarePilotCampaign(auth.accessToken, auth.tenantId, {
        name: presetForm.name.trim(),
        campaignType: selectedPreset.campaignType,
        triggerType: selectedPreset.triggerType,
        audienceType: presetForm.audienceType,
        templateId: template.id,
        notes: notes || null,
      });
      if (presetForm.activateNow) {
        await activateCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      }
      setToast({
        type: "success",
        text: selectedPreset.implementationStatus === "FOUNDATION_ONLY"
          ? "Campaign created. Trigger support is partial; executions may not generate until backend support is completed."
          : "Campaign created from preset.",
      });
      setPresetOpen(false);
      setPresetStep(1);
      setSelectedPreset(null);
      setPresetForm(null);
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Preset campaign creation failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onToggleCampaignStatus = async (campaign: CarePilotCampaign, activate: boolean) => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    setWorkingId(campaign.id);
    try {
      if (activate) {
        await activateCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      } else {
        await deactivateCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      }
      setToast({ type: "success", text: `Campaign ${activate ? "activated" : "deactivated"}.` });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Campaign status update failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onSubmitTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    if (!templateForm.name.trim() || !templateForm.bodyTemplate.trim()) {
      setToast({ type: "error", text: "Template name and body are required." });
      return;
    }
    setWorkingId("template-save");
    try {
      if (editingTemplateId) {
        await patchCarePilotTemplate(auth.accessToken, auth.tenantId, editingTemplateId, {
          name: templateForm.name.trim(),
          subjectLine: templateForm.subjectLine.trim() || null,
          bodyTemplate: templateForm.bodyTemplate,
          active: templateForm.active,
        });
        setToast({ type: "success", text: "Template updated." });
      } else {
        await createCarePilotTemplate(auth.accessToken, auth.tenantId, {
          name: templateForm.name.trim(),
          channelType: templateForm.channelType,
          subjectLine: templateForm.subjectLine.trim() || null,
          bodyTemplate: templateForm.bodyTemplate,
          active: templateForm.active,
        });
        setToast({ type: "success", text: "Template created." });
      }
      setTemplateForm(emptyTemplateForm());
      setTemplatePresetKey("");
      setEditingTemplateId(null);
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Template save failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onRetryExecution = async (executionId: string, resend: boolean) => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    const confirmed = window.confirm(resend ? "Resend this execution?" : "Retry this execution?");
    if (!confirmed) return;
    setWorkingId(executionId);
    try {
      if (resend) {
        await resendCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      } else {
        await retryCarePilotExecution(auth.accessToken, auth.tenantId, executionId);
      }
      setToast({ type: "success", text: `Execution ${resend ? "resend" : "retry"} queued.` });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Action failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onOpenAttempts = async (execution: CarePilotExecution) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setAttemptsOpen(true);
    setAttemptsTitle(`Delivery Attempts • ${execution.id.slice(0, 8)}`);
    setAttemptsLoading(true);
    setAttempts([]);
    try {
      const rows = await listCarePilotDeliveryAttempts(auth.accessToken, auth.tenantId, execution.id);
      setAttempts(rows);
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Failed to load attempts" });
    } finally {
      setAttemptsLoading(false);
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant from the top selector to manage CarePilot campaigns.</Alert>;
  }

  if (!canView) {
    return <Alert severity="error">You do not have access to CarePilot.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>CarePilot Campaigns</Typography>
          <Typography variant="body2" color="text.secondary">Manage campaign setup, templates, and delivery execution reliability.</Typography>
        </Box>
        {canManage ? (
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              onClick={() => {
                setCampaignTemplateSource("PREDEFINED");
                setCampaignForm(emptyCampaignForm());
                setCampaignPresetKey("");
                setCampaignPresetSubject("");
                setCampaignPresetBody("");
                setCreateCampaignOpen(true);
              }}
            >
              Create From Preset
            </Button>
            <Button
              variant="contained"
              onClick={() => {
                setCampaignTemplateSource("EXISTING");
                setCampaignForm(emptyCampaignForm());
                setCampaignPresetKey("");
                setCampaignPresetSubject("");
                setCampaignPresetBody("");
                setCreateCampaignOpen(true);
              }}
            >
              Create Campaign
            </Button>
          </Stack>
        ) : null}
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {!canManage ? <Alert severity="info">You have read-only access to CarePilot.</Alert> : null}

      <Card>
        <CardContent sx={{ pb: 1 }}>
          <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
            <Tab value="campaigns" label="Campaigns" />
            <Tab value="templates" label="Templates" />
            <Tab value="executions" label="Executions" />
            <Tab value="failed" label="Failed" />
          </Tabs>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ display: "grid", placeItems: "center", minHeight: 260 }}><CircularProgress /></Box>
      ) : null}

      {!loading && tab === "campaigns" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Campaigns</Typography>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField fullWidth label="Search" value={campaignSearch} onChange={(e) => setCampaignSearch(e.target.value)} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Status</InputLabel>
                        <Select value={campaignStatusFilter} label="Status" onChange={(e) => setCampaignStatusFilter(String(e.target.value) as CarePilotCampaignStatus | "")}> 
                          <MenuItem value="">All</MenuItem>
                          <MenuItem value="DRAFT">DRAFT</MenuItem>
                          <MenuItem value="ACTIVE">ACTIVE</MenuItem>
                          <MenuItem value="PAUSED">PAUSED</MenuItem>
                          <MenuItem value="ARCHIVED">ARCHIVED</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Type</InputLabel>
                        <Select value={campaignTypeFilter} label="Type" onChange={(e) => setCampaignTypeFilter(String(e.target.value) as CarePilotCampaignType | "")}> 
                          <MenuItem value="">All</MenuItem>
                          {CAMPAIGN_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Trigger</InputLabel>
                        <Select value={campaignTriggerFilter} label="Trigger" onChange={(e) => setCampaignTriggerFilter(String(e.target.value) as CarePilotTriggerType | "")}> 
                          <MenuItem value="">All</MenuItem>
                          {TRIGGER_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Channel</InputLabel>
                        <Select value={campaignChannelFilter} label="Channel" onChange={(e) => setCampaignChannelFilter(String(e.target.value) as CarePilotChannelType | "")}> 
                          <MenuItem value="">All</MenuItem>
                          {CHANNEL_TYPES.map((channel) => <MenuItem key={channel} value={channel}>{channelOptionLabel(channel, providerStatusByChannel)}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 1 }}>
                      <Button fullWidth variant="outlined" onClick={() => {
                        setCampaignSearch("");
                        setCampaignStatusFilter("");
                        setCampaignTypeFilter("");
                        setCampaignTriggerFilter("");
                        setCampaignChannelFilter("");
                      }}>Clear</Button>
                    </Grid>
                  </Grid>

                  {filteredCampaigns.length === 0 ? <Alert severity="info">No campaigns found for the selected filters.</Alert> : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>Type</TableCell>
                          <TableCell>Trigger</TableCell>
                          <TableCell>Audience</TableCell>
                          <TableCell>Channel</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell align="right">Actions</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {filteredCampaigns.map((campaign) => {
                          const linkedTemplate = campaign.templateId ? templateById.get(campaign.templateId) : null;
                          return (
                            <TableRow key={campaign.id} hover selected={selectedCampaign?.id === campaign.id} onClick={() => setSelectedCampaign(campaign)}>
                              <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{campaign.name}</Typography>
                                <Typography variant="caption" color="text.secondary">{campaign.id}</Typography>
                              </TableCell>
                              <TableCell>{campaign.campaignType}</TableCell>
                              <TableCell>{campaign.triggerType}</TableCell>
                              <TableCell>{campaign.audienceType}</TableCell>
                              <TableCell>{linkedTemplate?.channelType || "-"}</TableCell>
                              <TableCell>
                                <Stack direction="row" spacing={1}>
                                  <Chip size="small" label={campaign.status} color={campaignStatusColor(campaign.status)} />
                                  {campaign.active ? <Chip size="small" label="ACTIVE" color="success" variant="outlined" /> : null}
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                {canManage ? (
                                  <Button
                                    size="small"
                                    disabled={workingId === campaign.id}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      void onToggleCampaignStatus(campaign, !campaign.active);
                                    }}
                                  >
                                    {campaign.active ? "Deactivate" : "Activate"}
                                  </Button>
                                ) : null}
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Campaign Details</Typography>
                {!selectedCampaign ? <Alert severity="info">Select a campaign to view details.</Alert> : (
                  <Stack spacing={1.25}>
                    <Typography variant="body2"><b>Name:</b> {selectedCampaign.name}</Typography>
                    <Typography variant="body2"><b>Description:</b> {selectedCampaign.notes || "-"}</Typography>
                    <Typography variant="body2"><b>Campaign Type:</b> {selectedCampaign.campaignType}</Typography>
                    <Typography variant="body2"><b>Trigger Type:</b> {selectedCampaign.triggerType}</Typography>
                    <Typography variant="body2"><b>Audience Type:</b> {selectedCampaign.audienceType}</Typography>
                    <Typography variant="body2"><b>Default Channel:</b> {selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.channelType || "-") : "-"}</Typography>
                    <Typography variant="body2"><b>Status:</b> {selectedCampaign.status}</Typography>
                    <Typography variant="body2"><b>Active:</b> {selectedCampaign.active ? "Yes" : "No"}</Typography>
                    <Typography variant="caption" color="text.secondary">Updated {new Date(selectedCampaign.updatedAt).toLocaleString()}</Typography>
                    <Box sx={{ pt: 1 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Runtime / Activity</Typography>
                      {runtimeLoading ? <CircularProgress size={18} /> : null}
                      {!runtimeLoading && !campaignRuntime ? <Alert severity="info">Runtime data unavailable.</Alert> : null}
                      {campaignRuntime ? (
                        <Stack spacing={1}>
                          <Typography variant="caption" color="text.secondary">
                            Trigger: {campaignRuntime.triggerType} • Scheduler: {campaignRuntime.schedulerStatus}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Next expected: {campaignRuntime.nextExpectedExecutionAt ? new Date(campaignRuntime.nextExpectedExecutionAt).toLocaleString() : "-"}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Last scan: {campaignRuntime.lastSchedulerScanAt ? new Date(campaignRuntime.lastSchedulerScanAt).toLocaleString() : "-"}
                          </Typography>
                          <Stack direction="row" spacing={0.75} flexWrap="wrap">
                            <Chip size="small" label={`Total ${campaignRuntime.summary.totalExecutions}`} />
                            <Chip size="small" label={`Scheduled ${campaignRuntime.summary.scheduled}`} color="info" />
                            <Chip size="small" label={`Sent ${campaignRuntime.summary.sent}`} color="success" />
                            <Chip size="small" label={`Failed ${campaignRuntime.summary.failed}`} color="error" />
                            <Chip size="small" label={`Retrying ${campaignRuntime.summary.retrying}`} color="warning" />
                            <Chip size="small" label={`Skipped ${campaignRuntime.summary.skipped}`} />
                          </Stack>
                          {campaignRuntime.recentExecutions.length === 0 ? (
                            <Alert severity="info">
                              No executions yet. Check: campaign active={selectedCampaign.active ? "yes" : "no"}, scheduler={campaignRuntime.schedulerStatus},
                              template active={selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.active ? "yes" : "no") : "no template"}.
                              Matching patients with email and eligible records are also required.
                            </Alert>
                          ) : (
                            <Table size="small">
                              <TableHead>
                                <TableRow>
                                  <TableCell>Recipient</TableCell>
                                  <TableCell>Status</TableCell>
                                  <TableCell>Timeline</TableCell>
                                </TableRow>
                              </TableHead>
                              <TableBody>
                                {campaignRuntime.recentExecutions.slice(0, 8).map((row) => (
                                  <TableRow key={row.executionId}>
                                    <TableCell>
                                      <Typography variant="caption" sx={{ fontWeight: 700, display: "block" }}>{row.recipientPatientName || row.recipientPatientId || "-"}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.recipientEmail || row.recipientPhone || "-"}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.relatedEntityLabel || "-"}</Typography>
                                      {row.doctorName ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>Dr. {row.doctorName}</Typography> : null}
                                    </TableCell>
                                    <TableCell>
                                      <Chip size="small" color={runtimeStatusColor(row.status)} label={runtimeStatusLabel(row.status, campaignRuntime.summary.totalExecutions)} />
                                      {row.reminderWindow ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.reminderWindow}</Typography> : null}
                                    </TableCell>
                                    <TableCell>
                                      <Typography variant="caption" sx={{ display: "block" }}>Created: {new Date(row.createdAt).toLocaleString()}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Scheduled: {row.scheduledAt ? new Date(row.scheduledAt).toLocaleString() : "-"}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Attempted: {row.attemptedAt ? new Date(row.attemptedAt).toLocaleString() : "-"}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Sent: {row.sentAt ? new Date(row.sentAt).toLocaleString() : "-"}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Failed: {row.failedAt ? new Date(row.failedAt).toLocaleString() : "-"}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Next retry: {row.nextRetryAt ? new Date(row.nextRetryAt).toLocaleString() : "-"}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                        {row.channel || "-"} • {row.providerName || "-"} • retry {row.retryCount}
                                      </Typography>
                                    </TableCell>
                                  </TableRow>
                                ))}
                              </TableBody>
                            </Table>
                          )}
                        </Stack>
                      ) : null}
                    </Box>
                  </Stack>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "templates" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card>
              <CardContent>
                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2, gap: 1 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Templates</Typography>
                  {canManage ? (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        const preset = CAMPAIGN_PRESETS.find((p) => p.implementationStatus !== "FUTURE") || CAMPAIGN_PRESETS[0];
                        setEditingTemplateId(null);
                        setTemplatePresetKey(preset.presetKey);
                        setTemplateForm({
                          name: `${preset.displayName} Template`,
                          channelType: "EMAIL",
                          subjectLine: preset.defaultTemplateSubject,
                          bodyTemplate: preset.defaultTemplateBody,
                          active: true,
                        });
                      }}
                    >
                      Create from predefined
                    </Button>
                  ) : null}
                </Box>
                {templates.length === 0 ? <Alert severity="info">No templates available.</Alert> : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Channel</TableCell>
                        <TableCell>Subject</TableCell>
                        <TableCell>Active</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {templates.map((template) => (
                        <TableRow key={template.id} hover>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{template.name}</Typography>
                            <Typography variant="caption" color="text.secondary">{template.id}</Typography>
                          </TableCell>
                          <TableCell>{template.channelType}</TableCell>
                          <TableCell>{template.subjectLine || "-"}</TableCell>
                          <TableCell><Chip size="small" label={template.active ? "ACTIVE" : "INACTIVE"} color={template.active ? "success" : "default"} /></TableCell>
                          <TableCell align="right">
                            {canManage ? <Button size="small" onClick={() => {
                              setEditingTemplateId(template.id);
                              setTemplatePresetKey("");
                              setTemplateForm({
                                name: template.name,
                                channelType: template.channelType,
                                subjectLine: template.subjectLine || "",
                                bodyTemplate: template.bodyTemplate,
                                active: template.active,
                              });
                            }}>Edit</Button> : null}
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
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>{editingTemplateId ? "Edit Template" : "Create Template"}</Typography>
                <Stack spacing={1.5}>
                  {!editingTemplateId ? (
                    <FormControl fullWidth>
                      <InputLabel>Predefined Starter (Optional)</InputLabel>
                      <Select
                        label="Predefined Starter (Optional)"
                        value={templatePresetKey}
                        onChange={(e) => {
                          const value = String(e.target.value);
                          setTemplatePresetKey(value);
                          const preset = CAMPAIGN_PRESETS.find((p) => p.presetKey === value);
                          if (!preset) return;
                          setTemplateForm((c) => ({
                            ...c,
                            name: `${preset.displayName} Template`,
                            channelType: "EMAIL",
                            subjectLine: preset.defaultTemplateSubject,
                            bodyTemplate: preset.defaultTemplateBody,
                          }));
                        }}
                        disabled={!canManage}
                      >
                        <MenuItem value="">None</MenuItem>
                        {CAMPAIGN_PRESETS.map((preset) => (
                          <MenuItem key={preset.presetKey} value={preset.presetKey} disabled={preset.implementationStatus === "FUTURE"}>
                            {preset.displayName} • {presetStatusLabel(preset.implementationStatus)}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  ) : null}
                  <TextField
                    label="Name"
                    value={templateForm.name}
                    onChange={(e) => setTemplateForm((c) => ({ ...c, name: e.target.value }))}
                    disabled={!canManage}
                  />
                  <FormControl fullWidth>
                    <InputLabel>Channel</InputLabel>
                    <Select
                      label="Channel"
                      value={templateForm.channelType}
                      onChange={(e) => setTemplateForm((c) => ({ ...c, channelType: String(e.target.value) as CarePilotChannelType }))}
                      disabled={!canManage || Boolean(editingTemplateId)}
                    >
                      {CHANNEL_TYPES.map((channel) => <MenuItem key={channel} value={channel}>{channelOptionLabel(channel, providerStatusByChannel)}</MenuItem>)}
                    </Select>
                  </FormControl>
                  {isProviderNotReadyChannel(templateForm.channelType, providerStatusByChannel) ? (
                    <Alert severity="warning">
                      Selected channel provider is not READY. Campaign sends may fail until provider configuration is completed.
                    </Alert>
                  ) : null}
                  <TextField
                    label="Subject"
                    value={templateForm.subjectLine}
                    onChange={(e) => setTemplateForm((c) => ({ ...c, subjectLine: e.target.value }))}
                    disabled={!canManage}
                  />
                  <TextField
                    label="Body"
                    multiline
                    minRows={6}
                    value={templateForm.bodyTemplate}
                    onChange={(e) => setTemplateForm((c) => ({ ...c, bodyTemplate: e.target.value }))}
                    disabled={!canManage}
                  />
                  <FormControl fullWidth>
                    <InputLabel>Active</InputLabel>
                    <Select
                      label="Active"
                      value={templateForm.active ? "YES" : "NO"}
                      onChange={(e) => setTemplateForm((c) => ({ ...c, active: String(e.target.value) === "YES" }))}
                      disabled={!canManage}
                    >
                      <MenuItem value="YES">Yes</MenuItem>
                      <MenuItem value="NO">No</MenuItem>
                    </Select>
                  </FormControl>
                  {templateForm.bodyTemplate ? (
                    <Alert severity="info">Preview: {templateForm.bodyTemplate.slice(0, 180)}{templateForm.bodyTemplate.length > 180 ? "..." : ""}</Alert>
                  ) : null}
                  <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end" }}>
                    {editingTemplateId ? <Button onClick={() => { setEditingTemplateId(null); setTemplatePresetKey(""); setTemplateForm(emptyTemplateForm()); }} disabled={!canManage}>Cancel</Button> : null}
                    <Button variant="contained" onClick={() => void onSubmitTemplate()} disabled={!canManage || workingId === "template-save"}>Save</Button>
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "executions" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>Execution History</Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 2 }}>
                  <FormControl fullWidth>
                    <InputLabel>Status</InputLabel>
                    <Select value={executionStatusFilter} label="Status" onChange={(e) => setExecutionStatusFilter(String(e.target.value) as CarePilotExecutionStatus | "")}>
                      <MenuItem value="">All</MenuItem>
                      {EXECUTION_STATUSES.map((status) => <MenuItem key={status} value={status}>{status}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 2 }}>
                  <FormControl fullWidth>
                    <InputLabel>Channel</InputLabel>
                    <Select value={executionChannelFilter} label="Channel" onChange={(e) => setExecutionChannelFilter(String(e.target.value) as CarePilotChannelType | "")}>
                      <MenuItem value="">All</MenuItem>
                      {CHANNEL_TYPES.map((channel) => <MenuItem key={channel} value={channel}>{channelOptionLabel(channel, providerStatusByChannel)}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth>
                    <InputLabel>Campaign</InputLabel>
                    <Select value={executionCampaignFilter} label="Campaign" onChange={(e) => setExecutionCampaignFilter(String(e.target.value))}>
                      <MenuItem value="">All</MenuItem>
                      {campaigns.map((campaign) => <MenuItem key={campaign.id} value={campaign.id}>{campaign.name}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 2 }}>
                  <TextField type="date" label="From" fullWidth value={executionFromDate} onChange={(e) => setExecutionFromDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid size={{ xs: 12, md: 2 }}>
                  <TextField type="date" label="To" fullWidth value={executionToDate} onChange={(e) => setExecutionToDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid size={{ xs: 12, md: 1 }}>
                  <Button fullWidth variant="outlined" onClick={() => {
                    setExecutionStatusFilter("");
                    setExecutionChannelFilter("");
                    setExecutionCampaignFilter("");
                    setExecutionFromDate("");
                    setExecutionToDate("");
                  }}>Clear</Button>
                </Grid>
              </Grid>

              {filteredExecutions.length === 0 ? <Alert severity="info">No executions found for the selected filters.</Alert> : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Campaign</TableCell>
                      <TableCell>Patient/Entity</TableCell>
                      <TableCell>Channel</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Scheduled</TableCell>
                      <TableCell>Executed</TableCell>
                      <TableCell>Retry Count</TableCell>
                      <TableCell>Provider</TableCell>
                      <TableCell>Failure</TableCell>
                      <TableCell align="right">Attempts</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredExecutions.map((execution) => (
                      <TableRow key={execution.id} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{campaignById.get(execution.campaignId)?.name || execution.campaignId}</Typography>
                          <Typography variant="caption" color="text.secondary">{execution.campaignId}</Typography>
                        </TableCell>
                        <TableCell>{execution.recipientPatientId || "-"}</TableCell>
                        <TableCell>{execution.channelType}</TableCell>
                        <TableCell><Chip size="small" label={execution.status} color={executionStatusColor(execution.status)} /></TableCell>
                        <TableCell>{new Date(execution.scheduledAt).toLocaleString()}</TableCell>
                        <TableCell>{execution.executedAt ? new Date(execution.executedAt).toLocaleString() : "-"}</TableCell>
                        <TableCell>{execution.attemptCount}</TableCell>
                        <TableCell>
                          <Typography variant="body2">{execution.providerName || "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{execution.providerMessageId || "-"}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" color="error.main">{execution.failureReason || execution.lastError || "-"}</Typography>
                        </TableCell>
                        <TableCell align="right"><Button size="small" onClick={() => void onOpenAttempts(execution)}>View</Button></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      {!loading && tab === "failed" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>Failed Executions</Typography>
              {failedExecutions.length === 0 ? <Alert severity="info">No failed executions found.</Alert> : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Execution</TableCell>
                      <TableCell>Campaign</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Delivery</TableCell>
                      <TableCell>Retry Count</TableCell>
                      <TableCell>Reason</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {failedExecutions.map((execution) => (
                      <TableRow key={execution.id} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{execution.id}</Typography>
                          <Typography variant="caption" color="text.secondary">{new Date(execution.updatedAt).toLocaleString()}</Typography>
                        </TableCell>
                        <TableCell>{campaignById.get(execution.campaignId)?.name || execution.campaignId}</TableCell>
                        <TableCell><Chip size="small" label={execution.status} color={executionStatusColor(execution.status)} /></TableCell>
                        <TableCell>{execution.deliveryStatus || "-"}</TableCell>
                        <TableCell>{execution.attemptCount}</TableCell>
                        <TableCell>{execution.failureReason || execution.lastError || "-"}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" onClick={() => void onOpenAttempts(execution)}>Attempts</Button>
                            {canManage ? (
                              <>
                                <Button size="small" disabled={workingId === execution.id} onClick={() => void onRetryExecution(execution.id, false)}>Retry</Button>
                                <Button size="small" disabled={workingId === execution.id} onClick={() => void onRetryExecution(execution.id, true)}>Resend</Button>
                              </>
                            ) : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={presetOpen} onClose={() => setPresetOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Create Campaign From Preset</DialogTitle>
        <DialogContent>
          {presetStep === 1 ? (
            <Stack spacing={1.5} sx={{ mt: 0.5 }}>
              {CAMPAIGN_PRESETS.map((preset) => (
                <Card key={preset.presetKey} variant="outlined">
                  <CardContent sx={{ py: 1.5 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1}>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{preset.displayName}</Typography>
                        <Typography variant="caption" color="text.secondary">{preset.description}</Typography>
                        <Typography variant="caption" sx={{ display: "block" }} color="text.secondary">
                          {preset.campaignType} • {preset.triggerLabel} • {preset.defaultChannel}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip
                          size="small"
                          label={presetStatusLabel(preset.implementationStatus)}
                          color={preset.implementationStatus === "READY" ? "success" : preset.implementationStatus === "FOUNDATION_ONLY" ? "warning" : "default"}
                        />
                        <Button size="small" disabled={preset.implementationStatus === "FUTURE"} onClick={() => onSelectPreset(preset)}>Select</Button>
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          ) : null}
          {presetStep === 2 && selectedPreset && presetForm ? (
            <Stack spacing={1.5} sx={{ mt: 0.5 }}>
              {selectedPreset.implementationStatus === "FOUNDATION_ONLY" ? (
                <Alert severity="warning">Trigger support is partially implemented. Executions may not generate until backend trigger support is completed.</Alert>
              ) : null}
              <TextField label="Campaign Name" value={presetForm.name} onChange={(e) => setPresetForm((c) => c ? ({ ...c, name: e.target.value }) : c)} />
              <TextField label="Description" multiline minRows={2} value={presetForm.notes} onChange={(e) => setPresetForm((c) => c ? ({ ...c, notes: e.target.value }) : c)} />
              <TextField label="Trigger" value={selectedPreset.triggerLabel} disabled />
              <FormControl fullWidth>
                <InputLabel>Audience</InputLabel>
                <Select label="Audience" value={presetForm.audienceType} onChange={(e) => setPresetForm((c) => c ? ({ ...c, audienceType: String(e.target.value) as CarePilotAudienceType }) : c)}>
                  {AUDIENCE_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                </Select>
              </FormControl>
              <TextField
                label="Trigger Config (JSON)"
                multiline
                minRows={5}
                value={presetForm.triggerConfigText}
                onChange={(e) => setPresetForm((c) => c ? ({ ...c, triggerConfigText: e.target.value }) : c)}
                helperText="Stored in campaign notes for v1 compatibility."
              />
            </Stack>
          ) : null}
          {presetStep === 3 && selectedPreset && presetForm ? (
            <Stack spacing={1.5} sx={{ mt: 0.5 }}>
              <TextField label="Template Subject" value={presetForm.templateSubject} onChange={(e) => setPresetForm((c) => c ? ({ ...c, templateSubject: e.target.value }) : c)} />
              <TextField label="Template Body" multiline minRows={8} value={presetForm.templateBody} onChange={(e) => setPresetForm((c) => c ? ({ ...c, templateBody: e.target.value }) : c)} />
              <Alert severity="info">
                Supported placeholders: {selectedPreset.supportedPlaceholders.join(", ")}
              </Alert>
              <Alert severity="success">
                Preview:
                {"\n"}
                {previewTemplate(presetForm.templateBody)}
              </Alert>
              <FormControl fullWidth>
                <InputLabel>Activate After Create</InputLabel>
                <Select
                  label="Activate After Create"
                  value={presetForm.activateNow ? "YES" : "NO"}
                  onChange={(e) => setPresetForm((c) => c ? ({ ...c, activateNow: String(e.target.value) === "YES" }) : c)}
                >
                  <MenuItem value="NO">No</MenuItem>
                  <MenuItem value="YES" disabled={selectedPreset.implementationStatus === "FUTURE"}>Yes</MenuItem>
                </Select>
              </FormControl>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPresetOpen(false)}>Cancel</Button>
          {presetStep > 1 ? <Button onClick={() => setPresetStep((s) => (s === 3 ? 2 : 1) as 1 | 2 | 3)}>Back</Button> : null}
          {presetStep < 3 ? (
            <Button
              variant="contained"
              onClick={() => setPresetStep((s) => (s === 1 ? 2 : 3) as 1 | 2 | 3)}
              disabled={(presetStep === 1 && !selectedPreset)}
            >
              Next
            </Button>
          ) : (
            <Button variant="contained" disabled={workingId === "campaign-preset-create"} onClick={() => void onCreateFromPreset()}>Create</Button>
          )}
        </DialogActions>
      </Dialog>

      <Dialog open={createCampaignOpen} onClose={() => setCreateCampaignOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create Campaign</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <FormControl fullWidth>
              <InputLabel>Template Source</InputLabel>
              <Select
                label="Template Source"
                value={campaignTemplateSource}
                onChange={(e) => setCampaignTemplateSource(String(e.target.value) as TemplateSource)}
              >
                <MenuItem value="EXISTING">Existing Template</MenuItem>
                <MenuItem value="PREDEFINED">Predefined Template</MenuItem>
                <MenuItem value="NONE">No Template</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Name" value={campaignForm.name} onChange={(e) => setCampaignForm((c) => ({ ...c, name: e.target.value }))} />
            <FormControl fullWidth>
              <InputLabel>Campaign Type</InputLabel>
              <Select label="Campaign Type" value={campaignForm.campaignType} onChange={(e) => setCampaignForm((c) => ({ ...c, campaignType: String(e.target.value) as CarePilotCampaignType }))}>
                {CAMPAIGN_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Trigger Type</InputLabel>
              <Select label="Trigger Type" value={campaignForm.triggerType} onChange={(e) => setCampaignForm((c) => ({ ...c, triggerType: String(e.target.value) as CarePilotTriggerType }))}>
                {TRIGGER_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Audience Type</InputLabel>
              <Select label="Audience Type" value={campaignForm.audienceType} onChange={(e) => setCampaignForm((c) => ({ ...c, audienceType: String(e.target.value) as CarePilotAudienceType }))}>
                {AUDIENCE_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </Select>
            </FormControl>
            {campaignTemplateSource === "EXISTING" ? (
              <>
                <FormControl fullWidth>
                  <InputLabel>Template</InputLabel>
                  <Select label="Template" value={campaignForm.templateId} onChange={(e) => setCampaignForm((c) => ({ ...c, templateId: String(e.target.value) }))}>
                    <MenuItem value="">None</MenuItem>
                    {templates.map((template) => <MenuItem key={template.id} value={template.id}>{template.name} • {template.channelType}</MenuItem>)}
                  </Select>
                </FormControl>
                {selectedExistingTemplate ? (
                  <Alert severity="info">
                    <b>Channel:</b> {selectedExistingTemplate.channelType} • <b>Active:</b> {selectedExistingTemplate.active ? "Yes" : "No"}
                    {"\n"}
                    <b>Subject:</b> {selectedExistingTemplate.subjectLine || "-"}
                    {"\n"}
                    <b>Body:</b> {selectedExistingTemplate.bodyTemplate}
                    {"\n"}Edit content from Templates tab if needed.
                  </Alert>
                ) : null}
              </>
            ) : null}
            {campaignTemplateSource === "PREDEFINED" ? (
              <>
                <Alert severity="info">Using predefined template as starting point. Your edits will be saved as a tenant custom template.</Alert>
                <FormControl fullWidth>
                  <InputLabel>Predefined Template</InputLabel>
                  <Select label="Predefined Template" value={campaignPresetKey} onChange={(e) => onChooseCampaignPreset(String(e.target.value))}>
                    <MenuItem value="">Select preset</MenuItem>
                    {CAMPAIGN_PRESETS.map((preset) => (
                      <MenuItem key={preset.presetKey} value={preset.presetKey} disabled={preset.implementationStatus === "FUTURE"}>
                        {preset.displayName} • {presetStatusLabel(preset.implementationStatus)}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                {selectedCampaignPreset ? (
                  <>
                    <Alert severity={selectedCampaignPreset.implementationStatus === "FOUNDATION_ONLY" ? "warning" : "success"}>
                      {selectedCampaignPreset.description} {"\n"}
                      {selectedCampaignPreset.campaignType} • {selectedCampaignPreset.triggerType} • {selectedCampaignPreset.defaultChannel}
                      {selectedCampaignPreset.implementationStatus === "FOUNDATION_ONLY"
                        ? "\nBackend trigger is partially implemented. Executions may not be generated yet."
                        : ""}
                    </Alert>
                    <TextField
                      label="Template Subject"
                      value={campaignPresetSubject}
                      onChange={(e) => setCampaignPresetSubject(e.target.value)}
                    />
                    <TextField
                      label="Template Body"
                      multiline
                      minRows={6}
                      value={campaignPresetBody}
                      onChange={(e) => setCampaignPresetBody(e.target.value)}
                    />
                    <Alert severity="info">Supported placeholders: {selectedCampaignPreset.supportedPlaceholders.join(", ")}</Alert>
                    <Alert severity="success">Preview:{"\n"}{previewTemplate(campaignPresetBody)}</Alert>
                  </>
                ) : null}
              </>
            ) : null}
            <TextField label="Description" multiline minRows={3} value={campaignForm.notes} onChange={(e) => setCampaignForm((c) => ({ ...c, notes: e.target.value }))} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateCampaignOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={workingId === "campaign-create"} onClick={() => void onCreateCampaign()}>Create</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={attemptsOpen} onClose={() => setAttemptsOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{attemptsTitle}</DialogTitle>
        <DialogContent>
          {attemptsLoading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 160 }}><CircularProgress /></Box>
          ) : attempts.length === 0 ? (
            <Alert severity="info">No delivery attempts recorded.</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Attempt</TableCell>
                  <TableCell>Provider</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Error Code</TableCell>
                  <TableCell>Error Message</TableCell>
                  <TableCell>Attempted At</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {attempts.map((attempt) => (
                  <TableRow key={attempt.id}>
                    <TableCell>{attempt.attemptNumber}</TableCell>
                    <TableCell>{attempt.providerName || "-"}</TableCell>
                    <TableCell>{attempt.channelType}</TableCell>
                    <TableCell>{attempt.deliveryStatus}</TableCell>
                    <TableCell>{attempt.errorCode || "-"}</TableCell>
                    <TableCell>{attempt.errorMessage || "-"}</TableCell>
                    <TableCell>{new Date(attempt.attemptedAt).toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAttemptsOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={Boolean(toast)} autoHideDuration={3500} onClose={() => setToast(null)}>
        <Alert severity={toast?.type || "success"} onClose={() => setToast(null)}>{toast?.text}</Alert>
      </Snackbar>
    </Stack>
  );
}
