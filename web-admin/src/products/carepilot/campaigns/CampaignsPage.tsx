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
  IconButton,
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
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";

import { useAuth } from "../../../auth/useAuth";
import { engageCampaignSchema, mapZodErrors } from "@deepthoughtnet/form-validation-kit";
import { ConfirmationDialog } from "../../../components/clinical/ConfirmationDialog";
import { TextEntryDialog } from "../../../components/clinical/TextEntryDialog";
import {
  activateCarePilotCampaign,
  approveCarePilotCampaign,
  cancelCarePilotCampaign,
  createCarePilotCampaign,
  createCarePilotTemplate,
  completeCarePilotCampaign,
  deactivateCarePilotCampaign,
  editAndResubmitCarePilotCampaign,
  getCarePilotCampaign,
  getCarePilotCampaignApprovalHistory,
  getCarePilotCampaignTriggerPreview,
  getCarePilotCampaignRuntime,
  listCarePilotApprovalNeeded,
  listCarePilotCampaigns,
  listCarePilotDeliveryAttempts,
  listCarePilotExecutions,
  listCarePilotFailedExecutions,
  listCarePilotMessagingProviderStatuses,
  listCarePilotTemplates,
  patchCarePilotTemplate,
  pauseCarePilotCampaign,
  resumeCarePilotCampaign,
  requestChangesCarePilotCampaign,
  submitCarePilotCampaign,
  resendCarePilotExecution,
  retryCarePilotExecution,
  updateCarePilotCampaign,
  triggerCarePilotCampaign,
  withdrawCarePilotCampaign,
  type CarePilotAudienceType,
  type CarePilotCampaign,
  type CarePilotCampaignApprovalHistory,
  type CarePilotCampaignTriggerPreview,
  type CarePilotCampaignTriggerResult,
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
import {
  audienceTypeLabel,
  campaignEventLabel,
  campaignStatusLabel,
  campaignTypeLabel,
  channelTypeLabel,
  triggerTypeLabel,
} from "./campaignLabels";
import {
  formatCarePilotDateTime,
  humanizeCarePilotCode,
  providerLabel,
} from "../shared/carepilotFormatting";
import { useCarePilotTenantTimezone } from "../shared/useCarePilotTenantTimezone";

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
  const label = channelTypeLabel(channel);
  if (channel === "SMS" && providerStatusByChannel.SMS?.status !== "READY") return `${label} - Provider not ready`;
  if (channel === "WHATSAPP" && providerStatusByChannel.WHATSAPP?.status !== "READY") return `${label} - Provider not ready`;
  return label;
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
  if (status === "APPROVED") return "info" as const;
  if (status === "PENDING_APPROVAL") return "secondary" as const;
  if (status === "CHANGES_REQUESTED") return "warning" as const;
  if (status === "CANCELLED" || status === "COMPLETED") return "default" as const;
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

function campaignReferenceLabel(campaign: { campaignReference?: string | null; id: string }) {
  return campaign.campaignReference || "Unknown reference";
}

function actorDisplayLine(
  displayName?: string | null,
  roleLabel?: string | null,
  employeeCode?: string | null,
  username?: string | null,
) {
  const name = displayName?.trim() || employeeCode?.trim() || username?.trim() || "Unknown user";
  const parts = [name];
  if (roleLabel?.trim()) parts.push(`(${roleLabel.trim()})`);
  if (employeeCode?.trim()) parts.push(`• ${employeeCode.trim()}`);
  else if (!displayName?.trim() && username?.trim()) parts.push(`• ${username.trim()}`);
  return parts.join(" ");
}

type CreateCampaignForm = {
  name: string;
  campaignType: CarePilotCampaignType;
  triggerType: CarePilotTriggerType;
  audienceType: CarePilotAudienceType;
  templateId: string;
  notes: string;
};

type EditCampaignForm = CreateCampaignForm & {
  expectedVersion: number;
  resolutionNote: string;
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

function formFromCampaign(campaign: CarePilotCampaign): CreateCampaignForm {
  return {
    name: campaign.name || "",
    campaignType: campaign.campaignType,
    triggerType: campaign.triggerType,
    audienceType: campaign.audienceType,
    templateId: campaign.templateId || "",
    notes: campaign.notes || "",
  };
}

function editFormFromCampaign(campaign: CarePilotCampaign): EditCampaignForm {
  return {
    ...formFromCampaign(campaign),
    expectedVersion: campaign.version,
    resolutionNote: "",
  };
}

function isEditableCampaignStatus(status: CarePilotCampaignStatus) {
  return status === "DRAFT" || status === "CHANGES_REQUESTED";
}

function campaignTemplateReady(campaign: CarePilotCampaign, templateById: Map<string, CarePilotTemplate>) {
  if (!campaign.name?.trim()) return false;
  if (!campaign.campaignType) return false;
  if (!campaign.triggerType) return false;
  if (!campaign.audienceType) return false;
  if (!campaign.templateId) return false;
  const template = templateById.get(campaign.templateId);
  return Boolean(template?.active);
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
  const [tab, setTab] = React.useState<"campaigns" | "approval" | "templates" | "executions" | "failed">("campaigns");
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [approvalNeededCampaigns, setApprovalNeededCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [templates, setTemplates] = React.useState<CarePilotTemplate[]>([]);
  const [executions, setExecutions] = React.useState<CarePilotExecution[]>([]);
  const [failedExecutions, setFailedExecutions] = React.useState<CarePilotExecution[]>([]);
  const [campaignRuntime, setCampaignRuntime] = React.useState<CarePilotCampaignRuntime | null>(null);
  const [approvalHistory, setApprovalHistory] = React.useState<CarePilotCampaignApprovalHistory[]>([]);
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
  const [campaignErrors, setCampaignErrors] = React.useState<Record<string, string>>({});
  const [campaignTemplateSource, setCampaignTemplateSource] = React.useState<TemplateSource>("EXISTING");
  const [campaignPresetKey, setCampaignPresetKey] = React.useState("");
  const [campaignPresetSubject, setCampaignPresetSubject] = React.useState("");
  const [campaignPresetBody, setCampaignPresetBody] = React.useState("");
  const [presetOpen, setPresetOpen] = React.useState(false);
  const [presetStep, setPresetStep] = React.useState<1 | 2 | 3>(1);
  const [selectedPreset, setSelectedPreset] = React.useState<CampaignPreset | null>(null);
  const [presetForm, setPresetForm] = React.useState<PresetCreateForm | null>(null);
  const [templateForm, setTemplateForm] = React.useState<TemplateForm>(emptyTemplateForm());
  const [templateErrors, setTemplateErrors] = React.useState<Record<string, string>>({});
  const [templatePresetKey, setTemplatePresetKey] = React.useState("");
  const [editingTemplateId, setEditingTemplateId] = React.useState<string | null>(null);
  const [editCampaignOpen, setEditCampaignOpen] = React.useState(false);
  const [editCampaignTarget, setEditCampaignTarget] = React.useState<CarePilotCampaign | null>(null);
  const [editCampaignForm, setEditCampaignForm] = React.useState<EditCampaignForm>(() => ({
    ...emptyCampaignForm(),
    expectedVersion: 0,
    resolutionNote: "",
  }));
  const [editCampaignErrors, setEditCampaignErrors] = React.useState<Record<string, string>>({});
  const [editCampaignSaving, setEditCampaignSaving] = React.useState(false);
  const [editCampaignAddressed, setEditCampaignAddressed] = React.useState(false);
  const [editCampaignOriginal, setEditCampaignOriginal] = React.useState<CreateCampaignForm>(emptyCampaignForm());
  const [editCampaignConflictOpen, setEditCampaignConflictOpen] = React.useState(false);
  const [editCampaignConflictMessage, setEditCampaignConflictMessage] = React.useState("");
  const [editCampaignConflictReloading, setEditCampaignConflictReloading] = React.useState(false);

  const [attemptsOpen, setAttemptsOpen] = React.useState(false);
  const [attemptsTitle, setAttemptsTitle] = React.useState("");
  const [attemptsLoading, setAttemptsLoading] = React.useState(false);
  const [attempts, setAttempts] = React.useState<CarePilotDeliveryAttempt[]>([]);
  const attemptsTriggerRef = React.useRef<HTMLButtonElement | null>(null);
  const [providerStatusByChannel, setProviderStatusByChannel] = React.useState<Partial<Record<"SMS" | "WHATSAPP", CarePilotMessagingProviderStatus>>>({});
  const [requestChangesTarget, setRequestChangesTarget] = React.useState<CarePilotCampaign | null>(null);
  const [activateTarget, setActivateTarget] = React.useState<CarePilotCampaign | null>(null);
  const [triggerTarget, setTriggerTarget] = React.useState<CarePilotCampaign | null>(null);
  const [triggerPreview, setTriggerPreview] = React.useState<CarePilotCampaignTriggerPreview | null>(null);
  const [triggerPreviewLoading, setTriggerPreviewLoading] = React.useState(false);
  const [triggerPreviewError, setTriggerPreviewError] = React.useState<string | null>(null);
  const [triggerSubmitting, setTriggerSubmitting] = React.useState(false);
  const [triggerRequestKey, setTriggerRequestKey] = React.useState("");
  const [lastTriggerResult, setLastTriggerResult] = React.useState<CarePilotCampaignTriggerResult | null>(null);
  const [retryTarget, setRetryTarget] = React.useState<{ executionId: string; resend: boolean } | null>(null);

  const canViewProviderStatus = auth.hasPermission("engage.provider.view");
  const canView = auth.hasPermission("engage.campaign.view")
    || auth.hasPermission("engage.audit.view");
  const canManage = auth.hasPermission("engage.campaign.manage");
  const canSubmit = auth.hasPermission("engage.campaign.submit");
  const canReview = auth.hasPermission("engage.campaign.review");
  const canApprove = auth.hasPermission("engage.campaign.approve");
  const canActivate = auth.hasPermission("engage.campaign.activate");
  const canReviewApprovalQueue = canReview || canApprove;
  const { clinicTimeZone } = useCarePilotTenantTimezone(auth.accessToken, auth.tenantId);
  const canViewTemplates = canManage || auth.hasPermission("engage.template.view");
  const canRecoverFailedDeliveries = auth.hasPermission("engage.reminder.operate");
  const canTriggerManualCampaign = canActivate;

  React.useEffect(() => {
    if (!canReviewApprovalQueue && tab === "approval") {
      setTab("campaigns");
    }
    if (!canManage && tab === "templates") {
      setTab("campaigns");
    }
  }, [canManage, canReviewApprovalQueue, tab]);

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

  const loadRuntimeForCampaign = React.useCallback(async (campaignId: string) => {
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
  }, [auth.accessToken, auth.tenantId]);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [campaignResult, approvalResult, templateResult, executionResult, failedResult, providerResult] = await Promise.allSettled([
        listCarePilotCampaigns(auth.accessToken, auth.tenantId),
        canReviewApprovalQueue
          ? listCarePilotApprovalNeeded(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as CarePilotCampaign[]),
        canViewTemplates
          ? listCarePilotTemplates(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as CarePilotTemplate[]),
        listCarePilotExecutions(auth.accessToken, auth.tenantId),
        listCarePilotFailedExecutions(auth.accessToken, auth.tenantId),
        canViewProviderStatus
          ? listCarePilotMessagingProviderStatuses(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as CarePilotMessagingProviderStatus[]),
      ]);

      if (campaignResult.status === "fulfilled") {
        const campaignRows = campaignResult.value;
        setCampaigns(campaignRows);
        const queryCampaignId = new URLSearchParams(location.search).get("campaignId");
        if (queryCampaignId) {
          setSelectedCampaign(campaignRows.find((x) => x.id === queryCampaignId) || campaignRows[0] || null);
        } else {
          setSelectedCampaign((current) => campaignRows.find((x) => x.id === current?.id) || campaignRows[0] || null);
        }
      } else {
        setCampaigns([]);
        setSelectedCampaign(null);
        setError(campaignResult.reason instanceof Error ? campaignResult.reason.message : "Failed to load Jeevanam Engage campaigns");
      }

      if (approvalResult.status === "fulfilled") {
        setApprovalNeededCampaigns(approvalResult.value);
      } else {
        setApprovalNeededCampaigns([]);
      }

      if (templateResult.status === "fulfilled") {
        setTemplates(templateResult.value);
      } else {
        setTemplates([]);
      }

      if (executionResult.status === "fulfilled") {
        setExecutions(executionResult.value);
      } else {
        setExecutions([]);
      }

      if (failedResult.status === "fulfilled") {
        setFailedExecutions(failedResult.value);
      } else {
        setFailedExecutions([]);
      }

      if (providerResult.status === "fulfilled") {
        const providerRows = providerResult.value;
        setProviderStatusByChannel({
          SMS: providerRows.find((row) => row.channel === "SMS"),
          WHATSAPP: providerRows.find((row) => row.channel === "WHATSAPP"),
        });
      } else {
        setProviderStatusByChannel({});
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load Jeevanam Engage data");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canReviewApprovalQueue, canView, canViewProviderStatus, canViewTemplates, location.search]);

  React.useEffect(() => {
    void loadAll();
  }, [loadAll]);

  React.useEffect(() => {
    if (selectedCampaign?.id) {
      void loadRuntimeForCampaign(selectedCampaign.id);
      if (auth.accessToken && auth.tenantId) {
        void getCarePilotCampaignApprovalHistory(auth.accessToken, auth.tenantId, selectedCampaign.id)
          .then(setApprovalHistory)
          .catch(() => setApprovalHistory([]));
      }
    } else {
      setCampaignRuntime(null);
      setApprovalHistory([]);
    }
  }, [auth.accessToken, auth.tenantId, loadRuntimeForCampaign, selectedCampaign?.id]);

  React.useEffect(() => {
    if (!triggerTarget) {
      setTriggerPreview(null);
      setTriggerPreviewError(null);
      setTriggerPreviewLoading(false);
      setTriggerSubmitting(false);
      setTriggerRequestKey("");
    }
  }, [triggerTarget]);

  const filteredCampaigns = React.useMemo(() => {
    const q = campaignSearch.trim().toLowerCase();
    return campaigns.filter((campaign) => {
      if (campaignStatusFilter && campaign.status !== campaignStatusFilter) return false;
      if (campaignTypeFilter && campaign.campaignType !== campaignTypeFilter) return false;
      if (campaignTriggerFilter && campaign.triggerType !== campaignTriggerFilter) return false;
      const template = campaign.templateId ? templateById.get(campaign.templateId) : null;
      if (campaignChannelFilter && template?.channelType !== campaignChannelFilter) return false;
      if (!q) return true;
      return `${campaign.name} ${campaign.notes || ""} ${campaign.campaignReference || ""}`.toLowerCase().includes(q);
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

  const editCampaignHasChanges = React.useMemo(() => {
    if (!editCampaignTarget) return false;
    return (
      editCampaignForm.name.trim() !== editCampaignOriginal.name.trim()
      || editCampaignForm.campaignType !== editCampaignOriginal.campaignType
      || editCampaignForm.triggerType !== editCampaignOriginal.triggerType
      || editCampaignForm.audienceType !== editCampaignOriginal.audienceType
      || editCampaignForm.templateId.trim() !== editCampaignOriginal.templateId.trim()
      || editCampaignForm.notes.trim() !== editCampaignOriginal.notes.trim()
    );
  }, [editCampaignForm, editCampaignOriginal, editCampaignTarget]);

  const editCampaignCanResubmit = React.useMemo(() => {
    if (!editCampaignTarget || editCampaignTarget.status !== "CHANGES_REQUESTED") return false;
    return editCampaignHasChanges || editCampaignAddressed || editCampaignForm.resolutionNote.trim().length > 0;
  }, [editCampaignAddressed, editCampaignForm.resolutionNote, editCampaignHasChanges, editCampaignTarget]);

  const latestReviewHistoryEntry = React.useMemo(() => (
    [...approvalHistory].reverse().find((entry) => entry.eventType === "CHANGES_REQUESTED" || entry.eventType === "APPROVED") || null
  ), [approvalHistory]);
  const approvalHistoryNewestFirst = React.useMemo(() => [...approvalHistory].reverse(), [approvalHistory]);

  const closeEditCampaign = React.useCallback(() => {
    setEditCampaignOpen(false);
    setEditCampaignTarget(null);
    setEditCampaignErrors({});
    setEditCampaignAddressed(false);
    setEditCampaignConflictReloading(false);
    setEditCampaignConflictOpen(false);
    setEditCampaignConflictMessage("");
    setEditCampaignForm({
      ...emptyCampaignForm(),
      expectedVersion: 0,
      resolutionNote: "",
    });
    setEditCampaignOriginal(emptyCampaignForm());
  }, []);

  const openEditCampaign = (campaign: CarePilotCampaign) => {
    setEditCampaignTarget(campaign);
    const nextForm = editFormFromCampaign(campaign);
    setEditCampaignForm(nextForm);
    setEditCampaignOriginal(formFromCampaign(campaign));
    setEditCampaignErrors({});
    setEditCampaignAddressed(false);
    setEditCampaignOpen(true);
  };

  const reloadLatestEditCampaign = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !editCampaignTarget) return;
    setEditCampaignConflictReloading(true);
    try {
      const latest = await getCarePilotCampaign(auth.accessToken, auth.tenantId, editCampaignTarget.id);
      setEditCampaignTarget(latest);
      setEditCampaignForm({
        ...formFromCampaign(latest),
        expectedVersion: latest.version,
        resolutionNote: editCampaignForm.resolutionNote,
      });
      setEditCampaignOriginal(formFromCampaign(latest));
      setEditCampaignAddressed(false);
      setEditCampaignConflictOpen(false);
      setEditCampaignConflictMessage("");
      await loadAll();
    } catch (err) {
      setEditCampaignConflictMessage(err instanceof Error ? err.message : "Failed to reload the latest campaign state.");
    } finally {
      setEditCampaignConflictReloading(false);
    }
  }, [auth.accessToken, auth.tenantId, editCampaignForm.resolutionNote, editCampaignTarget, loadAll]);

  const onCreateCampaign = async () => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    const parsed = engageCampaignSchema.safeParse({
      name: campaignForm.name,
      description: campaignForm.notes,
      callType: campaignForm.campaignType === "CUSTOM" ? "IN_APP" : "SMS",
      status: "DRAFT",
      templateId: campaignForm.templateId || undefined,
      retryEnabled: true,
      maxAttempts: 3,
      escalationEnabled: false,
    });
    if (!parsed.success) {
      setCampaignErrors(mapZodErrors(parsed.error));
      return;
    }
    setCampaignErrors({});
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
    const parsed = engageCampaignSchema.safeParse({
      name: presetForm.name,
      description: presetForm.notes,
      callType: selectedPreset.defaultChannel,
      status: "DRAFT",
      templateId: undefined,
      retryEnabled: true,
      maxAttempts: 3,
      escalationEnabled: false,
    });
    if (!parsed.success) {
      setCampaignErrors(mapZodErrors(parsed.error));
      return;
    }
    setCampaignErrors({});
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
      if (presetForm.activateNow && canActivate) {
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

  const onActivateCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canActivate || campaign.status !== "APPROVED") return;
    setActivateTarget(campaign);
  };

  const submitActivateCampaign = async () => {
    if (!activateTarget || !auth.accessToken || !auth.tenantId || !canActivate || activateTarget.status !== "APPROVED") return;
    setWorkingId(`activate-${activateTarget.id}`);
    try {
      await activateCarePilotCampaign(auth.accessToken, auth.tenantId, activateTarget.id);
      setToast({ type: "success", text: "Campaign activated." });
      setActivateTarget(null);
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Campaign activation failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onSubmitForApproval = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canSubmit) return;
    if (!campaignTemplateReady(campaign, templateById)) {
      setToast({ type: "error", text: "Complete the required campaign configuration before submitting." });
      return;
    }
    setWorkingId(`submit-${campaign.id}`);
    try {
      await submitCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id, { expectedVersion: campaign.version });
      setToast({ type: "success", text: "Campaign submitted for approval." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Submission failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onSaveEditCampaign = async () => {
    if (!auth.accessToken || !auth.tenantId || !editCampaignTarget || !canManage) return;
    if (editCampaignTarget.status !== "DRAFT" && editCampaignTarget.status !== "CHANGES_REQUESTED") return;
    if (!editCampaignHasChanges) {
      setEditCampaignErrors({ form: "Make a campaign change before saving." });
      return;
    }
    const parsed = engageCampaignSchema.safeParse({
      name: editCampaignForm.name,
      description: editCampaignForm.notes,
      callType: editCampaignForm.campaignType === "CUSTOM" ? "IN_APP" : "SMS",
      status: "DRAFT",
      templateId: editCampaignForm.templateId || undefined,
      retryEnabled: true,
      maxAttempts: 3,
      escalationEnabled: false,
    });
    if (!parsed.success) {
      setEditCampaignErrors(mapZodErrors(parsed.error));
      return;
    }
    setEditCampaignErrors({});
    setEditCampaignSaving(true);
    try {
      const updated = await updateCarePilotCampaign(auth.accessToken, auth.tenantId, editCampaignTarget.id, {
        name: editCampaignForm.name.trim(),
        campaignType: editCampaignForm.campaignType,
        triggerType: editCampaignForm.triggerType,
        audienceType: editCampaignForm.audienceType,
        templateId: editCampaignForm.templateId.trim() || null,
        notes: editCampaignForm.notes.trim() || null,
        expectedVersion: editCampaignForm.expectedVersion,
      });
      setEditCampaignTarget(updated);
      setEditCampaignForm({
        ...formFromCampaign(updated),
        expectedVersion: updated.version,
        resolutionNote: editCampaignForm.resolutionNote,
      });
      setEditCampaignOriginal(formFromCampaign(updated));
      setEditCampaignAddressed(editCampaignAddressed || editCampaignHasChanges);
      setSelectedCampaign((current) => current?.id === updated.id ? updated : current);
      setToast({ type: "success", text: "Campaign updated." });
      await loadAll();
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 409) {
        setEditCampaignConflictMessage(err.message);
        setEditCampaignConflictOpen(true);
        return;
      }
      setToast({ type: "error", text: err instanceof Error ? err.message : "Campaign update failed" });
    } finally {
      setEditCampaignSaving(false);
    }
  };

  const onResubmitEditedCampaign = async () => {
    if (!auth.accessToken || !auth.tenantId || !editCampaignTarget || !canSubmit || editCampaignTarget.status !== "CHANGES_REQUESTED") return;
    if (!editCampaignCanResubmit) {
      setEditCampaignErrors({ resolutionNote: "Save a campaign change or provide a resolution note before resubmitting." });
      return;
    }
    setEditCampaignSaving(true);
    try {
      await editAndResubmitCarePilotCampaign(auth.accessToken, auth.tenantId, editCampaignTarget.id, {
        name: editCampaignForm.name.trim(),
        campaignType: editCampaignForm.campaignType,
        triggerType: editCampaignForm.triggerType,
        audienceType: editCampaignForm.audienceType,
        templateId: editCampaignForm.templateId.trim() || null,
        notes: editCampaignForm.notes.trim() || null,
        expectedVersion: editCampaignForm.expectedVersion,
        resolutionNote: editCampaignForm.resolutionNote.trim() || null,
      });
      setToast({ type: "success", text: "Campaign updated and resubmitted for approval." });
      closeEditCampaign();
      await loadAll();
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 409) {
        setEditCampaignConflictMessage(err.message);
        setEditCampaignConflictOpen(true);
        return;
      }
      setToast({ type: "error", text: err instanceof Error ? err.message : "Resubmission failed" });
    } finally {
      setEditCampaignSaving(false);
    }
  };

  const onWithdrawSubmission = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canSubmit) return;
    setWorkingId(`withdraw-${campaign.id}`);
    try {
      await withdrawCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      setToast({ type: "success", text: "Submission withdrawn." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Withdraw failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onApproveCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canApprove) return;
    setWorkingId(`approve-${campaign.id}`);
    try {
      await approveCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id, { expectedVersion: campaign.version });
      setToast({ type: "success", text: "Campaign approved." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Approval failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onRequestChanges = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canReview) return;
    setRequestChangesTarget(campaign);
  };

  const onPauseCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canActivate) return;
    setWorkingId(`pause-${campaign.id}`);
    try {
      await pauseCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      setToast({ type: "success", text: "Campaign paused." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Pause failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onResumeCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canActivate) return;
    setWorkingId(`resume-${campaign.id}`);
    try {
      await resumeCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      setToast({ type: "success", text: "Campaign resumed." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Resume failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onCancelCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canActivate) return;
    setWorkingId(`cancel-${campaign.id}`);
    try {
      await cancelCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      setToast({ type: "success", text: "Campaign cancelled." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Cancel failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onCompleteCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canActivate) return;
    setWorkingId(`complete-${campaign.id}`);
    try {
      await completeCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id);
      setToast({ type: "success", text: "Campaign completed." });
      await loadAll();
    } catch (err) {
      setToast({ type: "error", text: err instanceof Error ? err.message : "Complete failed" });
    } finally {
      setWorkingId(null);
    }
  };

  const onTriggerCampaign = async (campaign: CarePilotCampaign) => {
    if (!auth.accessToken || !auth.tenantId || !canTriggerManualCampaign || campaign.status !== "ACTIVE" || campaign.triggerType !== "MANUAL") return;
    setTriggerTarget(campaign);
    setTriggerRequestKey(crypto.randomUUID());
    setTriggerPreview(null);
    setTriggerPreviewError(null);
    setTriggerPreviewLoading(true);
    try {
      const preview = await getCarePilotCampaignTriggerPreview(auth.accessToken, auth.tenantId, campaign.id);
      setTriggerPreview(preview);
    } catch (err) {
      setTriggerPreviewError(err instanceof Error ? err.message : "Trigger preview failed");
    } finally {
      setTriggerPreviewLoading(false);
    }
  };

  const onSubmitTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId || !canManage) return;
    if (!templateForm.name.trim() || !templateForm.bodyTemplate.trim()) {
      setTemplateErrors({
        name: templateForm.name.trim() ? "" : "Template name is required.",
        bodyTemplate: templateForm.bodyTemplate.trim() ? "" : "Template body is required.",
      });
      return;
    }
    setTemplateErrors({});
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
    if (!auth.accessToken || !auth.tenantId || !canRecoverFailedDeliveries) return;
    setRetryTarget({ executionId, resend });
  };

  const submitRequestChanges = async (comment: string) => {
    if (!requestChangesTarget || !auth.accessToken || !auth.tenantId || !canReview) return;
    const campaign = requestChangesTarget;
    await requestChangesCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id, {
      comment,
      expectedVersion: campaign.version,
    });
    setToast({ type: "success", text: "Change request sent." });
    setRequestChangesTarget(null);
    await loadAll();
  };

  const submitTrigger = async () => {
    if (!triggerTarget || !auth.accessToken || !auth.tenantId || !canTriggerManualCampaign || triggerTarget.status !== "ACTIVE" || triggerTarget.triggerType !== "MANUAL") return;
    if (!triggerPreview?.canTrigger) {
      setTriggerPreviewError(triggerPreview?.blockingReasons?.[0] || "Campaign is not ready to run.");
      return;
    }
    const campaign = triggerTarget;
    setTriggerSubmitting(true);
    setWorkingId(`trigger-${campaign.id}`);
    try {
      const result = await triggerCarePilotCampaign(auth.accessToken, auth.tenantId, campaign.id, triggerRequestKey);
      setTriggerTarget(null);
      setTriggerPreview(null);
      setTriggerPreviewError(null);
      setTriggerRequestKey("");
      setTab("executions");
      await loadAll();
      await loadRuntimeForCampaign(campaign.id);
      setToast({
        type: result.queuedExecutions > 0 ? "success" : "error",
        text: result.queuedExecutions > 0 ? `Campaign queued for ${result.queuedExecutions} recipients.` : result.message,
      });
      setLastTriggerResult(result);
    } catch (err) {
      setTriggerPreviewError(err instanceof Error ? err.message : "Campaign trigger failed");
      setToast({ type: "error", text: err instanceof Error ? err.message : "Campaign trigger failed" });
    } finally {
      setWorkingId(null);
      setTriggerSubmitting(false);
    }
  };

  const submitRetry = async () => {
    if (!retryTarget || !auth.accessToken || !auth.tenantId || !canRecoverFailedDeliveries) return;
    const { executionId, resend } = retryTarget;
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
      setRetryTarget(null);
    }
  };

  React.useEffect(() => {
    if (attemptsOpen) return;
    attemptsTriggerRef.current?.focus();
  }, [attemptsOpen]);

  const closeAttempts = React.useCallback(() => {
    setAttemptsOpen(false);
  }, []);

  const onOpenAttempts = async (execution: CarePilotExecution, triggerButton?: HTMLButtonElement | null) => {
    if (!auth.accessToken || !auth.tenantId) return;
    attemptsTriggerRef.current = triggerButton || null;
    setAttemptsOpen(true);
    setAttemptsTitle("Delivery Attempts");
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
    return <Alert severity="info">Select a tenant from the top selector to manage Jeevanam Engage campaigns.</Alert>;
  }

  if (!canView) {
    return <Alert severity="error">You do not have access to Jeevanam Engage.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Jeevanam Engage Campaigns</Typography>
          <Typography variant="body2" color="text.secondary">Campaigns, lead management, patient engagement, reminders, and AI outreach.</Typography>
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
      {!canManage && !canReviewApprovalQueue && !canActivate ? <Alert severity="info">You have read-only access to Jeevanam Engage.</Alert> : null}

      <Card>
        <CardContent sx={{ pb: 1 }}>
          <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
            <Tab value="campaigns" label="Campaigns" />
            {canReviewApprovalQueue ? <Tab value="approval" label="Approval Needed" /> : null}
            {canManage ? <Tab value="templates" label="Templates" /> : null}
            <Tab value="executions" label="Executions" />
            <Tab value="failed" label="Failed" />
          </Tabs>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ display: "grid", placeItems: "center", minHeight: 260 }}><CircularProgress /></Box>
      ) : null}

      {!loading && tab === "approval" ? (
        <Card>
          <CardContent>
            <Stack spacing={2}>
              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Approval Needed</Typography>
                  <Typography variant="body2" color="text.secondary">Review readiness, approve submissions, or request changes with comments.</Typography>
                </Box>
                <Chip size="small" label={`${approvalNeededCampaigns.length} awaiting review`} />
              </Box>
              {approvalNeededCampaigns.length === 0 ? <Alert severity="info">No campaigns are currently awaiting approval.</Alert> : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Submitted</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Trigger</TableCell>
                      <TableCell>Audience</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {approvalNeededCampaigns.map((campaign) => (
                      <TableRow key={campaign.id} hover onClick={() => setSelectedCampaign(campaign)}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{campaign.name}</Typography>
                          <Typography variant="caption" color="text.secondary">{campaignReferenceLabel(campaign)}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{campaign.submittedAt ? new Date(campaign.submittedAt).toLocaleString() : "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{actorDisplayLine(campaign.submittedByDisplayName, campaign.submittedByRoleLabel, campaign.submittedByEmployeeCode, campaign.submittedByUsername)}</Typography>
                        </TableCell>
                        <TableCell>{campaignTypeLabel(campaign.campaignType)}</TableCell>
                        <TableCell>{triggerTypeLabel(campaign.triggerType)}</TableCell>
                        <TableCell>{audienceTypeLabel(campaign.audienceType)}</TableCell>
                        <TableCell><Chip size="small" label={campaignStatusLabel(campaign.status)} color={campaignStatusColor(campaign.status)} /></TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            {canApprove ? <Button size="small" onClick={() => void onApproveCampaign(campaign)}>Approve</Button> : null}
                            {canReview ? <Button size="small" color="warning" onClick={() => void onRequestChanges(campaign)}>Request Changes</Button> : null}
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
                          <MenuItem value="DRAFT">{campaignStatusLabel("DRAFT")}</MenuItem>
                          <MenuItem value="PENDING_APPROVAL">{campaignStatusLabel("PENDING_APPROVAL")}</MenuItem>
                          <MenuItem value="CHANGES_REQUESTED">{campaignStatusLabel("CHANGES_REQUESTED")}</MenuItem>
                          <MenuItem value="APPROVED">{campaignStatusLabel("APPROVED")}</MenuItem>
                          <MenuItem value="ACTIVE">{campaignStatusLabel("ACTIVE")}</MenuItem>
                          <MenuItem value="PAUSED">{campaignStatusLabel("PAUSED")}</MenuItem>
                          <MenuItem value="COMPLETED">{campaignStatusLabel("COMPLETED")}</MenuItem>
                          <MenuItem value="CANCELLED">{campaignStatusLabel("CANCELLED")}</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Type</InputLabel>
                        <Select value={campaignTypeFilter} label="Type" onChange={(e) => setCampaignTypeFilter(String(e.target.value) as CarePilotCampaignType | "")}> 
                          <MenuItem value="">All</MenuItem>
                          {CAMPAIGN_TYPES.map((type) => <MenuItem key={type} value={type}>{campaignTypeLabel(type)}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <FormControl fullWidth>
                        <InputLabel>Trigger</InputLabel>
                        <Select value={campaignTriggerFilter} label="Trigger" onChange={(e) => setCampaignTriggerFilter(String(e.target.value) as CarePilotTriggerType | "")}> 
                          <MenuItem value="">All</MenuItem>
                          {TRIGGER_TYPES.map((type) => <MenuItem key={type} value={type}>{triggerTypeLabel(type)}</MenuItem>)}
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
                          <Typography variant="caption" color="text.secondary">{campaignReferenceLabel(campaign)}</Typography>
                              </TableCell>
                              <TableCell>{campaignTypeLabel(campaign.campaignType)}</TableCell>
                              <TableCell>{triggerTypeLabel(campaign.triggerType)}</TableCell>
                              <TableCell>{audienceTypeLabel(campaign.audienceType)}</TableCell>
                              <TableCell>{linkedTemplate?.channelType ? channelTypeLabel(linkedTemplate.channelType) : "-"}</TableCell>
                              <TableCell>
                                <Stack direction="row" spacing={1}>
                                  <Chip size="small" label={campaignStatusLabel(campaign.status)} color={campaignStatusColor(campaign.status)} />
                                </Stack>
                              </TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                                  {canManage && isEditableCampaignStatus(campaign.status) ? (
                                    <Button
                                      size="small"
                                      disabled={workingId === `edit-${campaign.id}`}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        openEditCampaign(campaign);
                                      }}
                                    >
                                      Edit
                                    </Button>
                                  ) : null}
                                  {canSubmit && campaign.status === "DRAFT" && campaignTemplateReady(campaign, templateById) ? (
                                    <Button
                                      size="small"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onSubmitForApproval(campaign);
                                      }}
                                    >
                                      Submit
                                    </Button>
                                  ) : null}
                                  {canSubmit && campaign.status === "CHANGES_REQUESTED" ? (
                                    <Button
                                      size="small"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        openEditCampaign(campaign);
                                      }}
                                    >
                                      Resubmit
                                    </Button>
                                  ) : null}
                                  {canSubmit && campaign.status === "PENDING_APPROVAL" ? (
                                    <Button
                                      size="small"
                                      disabled={workingId === `withdraw-${campaign.id}`}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onWithdrawSubmission(campaign);
                                      }}
                                    >
                                      Withdraw
                                    </Button>
                                  ) : null}
                                  {canApprove && campaign.status === "PENDING_APPROVAL" ? (
                                    <>
                                      <Button
                                        size="small"
                                        disabled={workingId === `approve-${campaign.id}`}
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          void onApproveCampaign(campaign);
                                        }}
                                      >
                                        Approve
                                      </Button>
                                    </>
                                  ) : null}
                                  {canReview && campaign.status === "PENDING_APPROVAL" ? (
                                    <Button
                                      size="small"
                                      color="warning"
                                      disabled={workingId === `changes-${campaign.id}`}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onRequestChanges(campaign);
                                      }}
                                    >
                                      Request Changes
                                    </Button>
                                  ) : null}
                                  {canActivate && campaign.status === "APPROVED" ? (
                                    <Button
                                      size="small"
                                      disabled={workingId === `activate-${campaign.id}`}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onActivateCampaign(campaign);
                                      }}
                                    >
                                      Activate
                                    </Button>
                                  ) : null}
                                  {canActivate && campaign.status === "ACTIVE" ? (
                                    <Button size="small" disabled={workingId === `pause-${campaign.id}`} onClick={(e) => { e.stopPropagation(); void onPauseCampaign(campaign); }}>Pause</Button>
                                  ) : null}
                                  {canActivate && campaign.status === "PAUSED" ? (
                                    <Button size="small" disabled={workingId === `resume-${campaign.id}`} onClick={(e) => { e.stopPropagation(); void onResumeCampaign(campaign); }}>Resume</Button>
                                  ) : null}
                                  {canActivate && (campaign.status === "ACTIVE" || campaign.status === "PAUSED") ? (
                                    <Button size="small" disabled={workingId === `cancel-${campaign.id}`} color="error" onClick={(e) => { e.stopPropagation(); void onCancelCampaign(campaign); }}>Cancel</Button>
                                  ) : null}
                                  {canActivate && (campaign.status === "ACTIVE" || campaign.status === "PAUSED") ? (
                                    <Button size="small" disabled={workingId === `complete-${campaign.id}`} onClick={(e) => { e.stopPropagation(); void onCompleteCampaign(campaign); }}>Complete</Button>
                                  ) : null}
                                  {canTriggerManualCampaign && campaign.status === "ACTIVE" && campaign.triggerType === "MANUAL" ? (
                                    <Button
                                      size="small"
                                      disabled={workingId === `trigger-${campaign.id}`}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onTriggerCampaign(campaign);
                                      }}
                                    >
                                      Trigger
                                    </Button>
                                  ) : null}
                                </Stack>
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
                    <Typography variant="body2"><b>Campaign Type:</b> {campaignTypeLabel(selectedCampaign.campaignType)}</Typography>
                    <Typography variant="body2"><b>Trigger Type:</b> {triggerTypeLabel(selectedCampaign.triggerType)}</Typography>
                    <Typography variant="body2"><b>Audience Type:</b> {audienceTypeLabel(selectedCampaign.audienceType)}</Typography>
                    <Typography variant="body2"><b>Default Channel:</b> {selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.channelType ? channelTypeLabel(templateById.get(selectedCampaign.templateId)!.channelType) : "-") : "-"}</Typography>
                    <Typography variant="body2"><b>Template:</b> {selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.name || "Linked template unavailable") : "-"}</Typography>
                    <Typography variant="body2"><b>Template Subject:</b> {selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.subjectLine || "-") : "-"}</Typography>
                    <Typography variant="body2"><b>Template Body Preview:</b> {selectedCampaign.templateId ? (templateById.get(selectedCampaign.templateId)?.bodyTemplate || "-") : "-"}</Typography>
                    <Typography variant="body2"><b>Status:</b> {campaignStatusLabel(selectedCampaign.status)}</Typography>
                    <Typography variant="body2"><b>Active:</b> {selectedCampaign.active ? "Yes" : "No"}</Typography>
                    {selectedCampaign.status === "CHANGES_REQUESTED" ? (
                      <Alert severity="warning" sx={{ whiteSpace: "pre-line" }}>
                        <b>Review comment:</b> {selectedCampaign.reviewComment || "A reviewer has requested changes."}
                        {selectedCampaign.reviewedAt ? `\nReviewed ${new Date(selectedCampaign.reviewedAt).toLocaleString()}` : ""}
                        {selectedCampaign.reviewedByDisplayName ? `\nReviewer: ${actorDisplayLine(selectedCampaign.reviewedByDisplayName, selectedCampaign.reviewedByRoleLabel, selectedCampaign.reviewedByEmployeeCode, selectedCampaign.reviewedByUsername)}` : ""}
                        {"\n"}Update the campaign and resubmit it for review.
                      </Alert>
                    ) : null}
                    <Typography variant="body2"><b>Submitted:</b> {selectedCampaign.submittedAt ? new Date(selectedCampaign.submittedAt).toLocaleString() : "-"} {selectedCampaign.submittedByDisplayName ? `• ${actorDisplayLine(selectedCampaign.submittedByDisplayName, selectedCampaign.submittedByRoleLabel, selectedCampaign.submittedByEmployeeCode, selectedCampaign.submittedByUsername)}` : ""}</Typography>
                    <Typography variant="body2"><b>Reviewed:</b> {selectedCampaign.reviewedAt ? new Date(selectedCampaign.reviewedAt).toLocaleString() : "-"} {selectedCampaign.reviewedByDisplayName ? `• ${actorDisplayLine(selectedCampaign.reviewedByDisplayName, selectedCampaign.reviewedByRoleLabel, selectedCampaign.reviewedByEmployeeCode, selectedCampaign.reviewedByUsername)}` : ""}</Typography>
                    <Typography variant="body2"><b>Approval Comment:</b> {selectedCampaign.reviewComment || "-"}</Typography>
                    <Typography variant="body2"><b>Approved:</b> {selectedCampaign.approvedAt ? new Date(selectedCampaign.approvedAt).toLocaleString() : "-"} {selectedCampaign.approvedByDisplayName ? `• ${actorDisplayLine(selectedCampaign.approvedByDisplayName, selectedCampaign.approvedByRoleLabel, selectedCampaign.approvedByEmployeeCode, selectedCampaign.approvedByUsername)}` : ""}</Typography>
                    <Typography variant="body2"><b>Approval Invalidated:</b> {selectedCampaign.approvalInvalidatedReason || "-"}</Typography>
                    <Typography variant="body2"><b>Approved Version:</b> {selectedCampaign.approvedVersion ?? "-"}</Typography>
                    {canManage && isEditableCampaignStatus(selectedCampaign.status) ? (
                      <Button
                        size="small"
                        sx={{ alignSelf: "flex-start" }}
                        onClick={() => openEditCampaign(selectedCampaign)}
                      >
                        Edit
                      </Button>
                    ) : null}
                    {canActivate && selectedCampaign.status === "APPROVED" ? (
                      <Button
                        size="small"
                        sx={{ alignSelf: "flex-start" }}
                        disabled={workingId === `activate-${selectedCampaign.id}`}
                        onClick={() => void onActivateCampaign(selectedCampaign)}
                      >
                        Activate
                      </Button>
                    ) : null}
                    {canTriggerManualCampaign && selectedCampaign.status === "ACTIVE" && selectedCampaign.triggerType === "MANUAL" ? (
                      <Button
                        size="small"
                        sx={{ alignSelf: "flex-start" }}
                        disabled={workingId === `trigger-${selectedCampaign.id}`}
                        onClick={() => void onTriggerCampaign(selectedCampaign)}
                      >
                        Trigger
                      </Button>
                    ) : null}
                    {canActivate && selectedCampaign.status === "PAUSED" ? (
                      <Button
                        size="small"
                        sx={{ alignSelf: "flex-start" }}
                        disabled={workingId === `resume-${selectedCampaign.id}`}
                        onClick={() => void onResumeCampaign(selectedCampaign)}
                      >
                        Resume
                      </Button>
                    ) : null}
                    <Typography variant="caption" color="text.secondary">Updated {new Date(selectedCampaign.updatedAt).toLocaleString()}</Typography>
                    <Box sx={{ pt: 1 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Approval History</Typography>
                      {approvalHistory.length === 0 ? <Alert severity="info">No approval history yet.</Alert> : (
                        <Box
                          tabIndex={0}
                          role="region"
                          aria-label="Campaign approval history"
                          sx={{
                            maxHeight: { xs: 320, md: 400 },
                            overflowY: "auto",
                            pr: 1,
                            outline: 0,
                            "&:focus-visible": {
                              outline: "2px solid",
                              outlineColor: "primary.main",
                              outlineOffset: 2,
                            },
                          }}
                        >
                          <Box
                            sx={{
                              position: "sticky",
                              top: 0,
                              zIndex: 1,
                              bgcolor: "background.paper",
                              pb: 1,
                            }}
                          >
                            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                              Newest approval first
                            </Typography>
                          </Box>
                          <Stack spacing={1}>
                            {approvalHistoryNewestFirst.map((entry) => (
                              <Card key={entry.id} variant="outlined">
                                <CardContent sx={{ py: 1.2 }}>
                                  <Stack spacing={0.5}>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{campaignEventLabel(entry.eventType)}</Typography>
                                    <Typography variant="caption" color="text.secondary">{entry.createdAt ? new Date(entry.createdAt).toLocaleString() : "-"}</Typography>
                                    <Typography variant="caption" color="text.secondary">{actorDisplayLine(entry.actorDisplayName, entry.actorRoleLabel || entry.actorRole, entry.actorEmployeeCode, entry.actorUsername)}</Typography>
                                    <Typography variant="caption" color="text.secondary">From {entry.fromStatus ? campaignStatusLabel(entry.fromStatus) : "-"} to {entry.toStatus ? campaignStatusLabel(entry.toStatus) : "-"}</Typography>
                                    {entry.comment ? <Typography variant="body2">{entry.comment}</Typography> : null}
                                    {entry.resolutionNote ? <Typography variant="body2" color="text.secondary">{entry.resolutionNote}</Typography> : null}
                                    {entry.previousCampaignVersion !== null || entry.newCampaignVersion !== null ? (
                                      <Typography variant="caption" color="text.secondary">
                                        Version {entry.previousCampaignVersion ?? "-"} → {entry.newCampaignVersion ?? entry.campaignVersion ?? "-"}
                                      </Typography>
                                    ) : null}
                                    {entry.invalidationReason ? <Typography variant="body2">{entry.invalidationReason}</Typography> : null}
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        </Box>
                      )}
                    </Box>
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
                                      <Typography variant="caption" sx={{ fontWeight: 700, display: "block" }}>{row.recipientPatientName || "Unknown patient"}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.recipientEmail || row.recipientPhone || "-"}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.relatedEntityLabel || "-"}</Typography>
                                      {row.doctorName ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>Dr. {row.doctorName}</Typography> : null}
                                    </TableCell>
                                    <TableCell>
                                      <Chip size="small" color={runtimeStatusColor(row.status)} label={runtimeStatusLabel(row.status, campaignRuntime.summary.totalExecutions)} />
                                      {row.reminderWindow ? <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{row.reminderWindow}</Typography> : null}
                                    </TableCell>
                                    <TableCell>
                                      <Typography variant="caption" sx={{ display: "block" }}>Created: {formatCarePilotDateTime(row.createdAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Scheduled: {formatCarePilotDateTime(row.scheduledAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Attempted: {formatCarePilotDateTime(row.attemptedAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Sent: {formatCarePilotDateTime(row.sentAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Failed: {formatCarePilotDateTime(row.failedAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" sx={{ display: "block" }}>Next retry: {formatCarePilotDateTime(row.nextRetryAt, clinicTimeZone)}</Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                        {row.channel ? channelTypeLabel(row.channel as CarePilotChannelType) : "-"} • {providerLabel(row.providerName)} • retry {row.retryCount}
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

      {!loading && canManage && tab === "templates" ? (
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
                    label="Name *"
                    value={templateForm.name}
                    onChange={(e) => setTemplateForm((c) => ({ ...c, name: e.target.value }))}
                    disabled={!canManage}
                    required
                    inputProps={{ maxLength: 60 }}
                    error={Boolean(templateErrors.name)}
                    helperText={templateErrors.name || "Template name must be 60 characters or fewer."}
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
                    inputProps={{ maxLength: 60 }}
                  />
                  <TextField
                    label="Body *"
                    multiline
                    minRows={6}
                    value={templateForm.bodyTemplate}
                    onChange={(e) => setTemplateForm((c) => ({ ...c, bodyTemplate: e.target.value }))}
                    disabled={!canManage}
                    required
                    inputProps={{ maxLength: 250 }}
                    error={Boolean(templateErrors.bodyTemplate)}
                    helperText={templateErrors.bodyTemplate || "Template body is required and must be 250 characters or fewer."}
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
                      {campaigns.map((campaign) => <MenuItem key={campaign.id} value={campaign.id}>{campaign.name} • {campaignReferenceLabel(campaign)}</MenuItem>)}
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
                      <TableCell align="right">Delivery Attempts</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredExecutions.map((execution) => (
                      <TableRow key={execution.id} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{campaignById.get(execution.campaignId) ? `${campaignById.get(execution.campaignId)!.name} • ${campaignReferenceLabel(campaignById.get(execution.campaignId)!)}`
                            : "Campaign"}</Typography>
                          <Typography variant="caption" color="text.secondary">{campaignById.get(execution.campaignId) ? `${campaignById.get(execution.campaignId)!.name} • ${campaignReferenceLabel(campaignById.get(execution.campaignId)!)}`
                            : "Campaign"}</Typography>
                        </TableCell>
                        <TableCell>{execution.recipientPatientId ? "Patient record" : "-"}</TableCell>
                        <TableCell>{channelTypeLabel(execution.channelType)}</TableCell>
                        <TableCell><Chip size="small" label={humanizeCarePilotCode(execution.status)} color={executionStatusColor(execution.status)} /></TableCell>
                        <TableCell>{formatCarePilotDateTime(execution.scheduledAt, clinicTimeZone)}</TableCell>
                        <TableCell>{formatCarePilotDateTime(execution.executedAt, clinicTimeZone)}</TableCell>
                        <TableCell>{execution.deliveryAttemptCount}</TableCell>
                        <TableCell>
                          <Typography variant="body2">{providerLabel(execution.providerName)}</Typography>
                          <Typography variant="caption" color="text.secondary">{execution.providerMessageId ? "Recorded" : "-"}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" color="error.main">{execution.failureReason || execution.lastError || "-"}</Typography>
                        </TableCell>
                        <TableCell align="right"><Button size="small" onClick={(event) => void onOpenAttempts(execution, event.currentTarget)}>View</Button></TableCell>
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
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>Execution</Typography>
                          <Typography variant="caption" color="text.secondary">{formatCarePilotDateTime(execution.updatedAt, clinicTimeZone)}</Typography>
                        </TableCell>
                        <TableCell>{campaignById.get(execution.campaignId) ? `${campaignById.get(execution.campaignId)!.name} • ${campaignReferenceLabel(campaignById.get(execution.campaignId)!)}`
                          : "Unknown campaign"}</TableCell>
                        <TableCell><Chip size="small" label={humanizeCarePilotCode(execution.status)} color={executionStatusColor(execution.status)} /></TableCell>
                        <TableCell>{humanizeCarePilotCode(execution.deliveryStatus)}</TableCell>
                        <TableCell>{execution.attemptCount}</TableCell>
                        <TableCell>{execution.failureReason || execution.lastError || "-"}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" onClick={() => void onOpenAttempts(execution)}>Attempts</Button>
                            {canRecoverFailedDeliveries ? (
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
              <TextField label="Campaign Name *" value={presetForm.name} onChange={(e) => setPresetForm((c) => c ? ({ ...c, name: e.target.value }) : c)} required error={Boolean(campaignErrors.name)} helperText={campaignErrors.name || "Campaign name must be 60 characters or fewer."} inputProps={{ maxLength: 60 }} />
              <TextField label="Description" multiline minRows={2} value={presetForm.notes} onChange={(e) => setPresetForm((c) => c ? ({ ...c, notes: e.target.value }) : c)} inputProps={{ maxLength: 250 }} error={Boolean(campaignErrors.description)} helperText={campaignErrors.description || "Description must be 250 characters or fewer."} />
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
              <TextField label="Template Subject" value={presetForm.templateSubject} onChange={(e) => setPresetForm((c) => c ? ({ ...c, templateSubject: e.target.value }) : c)} inputProps={{ maxLength: 60 }} />
              <TextField label="Template Body" multiline minRows={8} value={presetForm.templateBody} onChange={(e) => setPresetForm((c) => c ? ({ ...c, templateBody: e.target.value }) : c)} inputProps={{ maxLength: 250 }} />
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
                  disabled={!canActivate}
                >
                  <MenuItem value="NO">No</MenuItem>
                  <MenuItem value="YES" disabled={selectedPreset.implementationStatus === "FUTURE"}>Yes</MenuItem>
                </Select>
              </FormControl>
              {!canActivate ? <Alert severity="info">Campaign activation is reserved for Clinic Admin.</Alert> : null}
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
            <TextField label="Name *" value={campaignForm.name} onChange={(e) => setCampaignForm((c) => ({ ...c, name: e.target.value }))} required error={Boolean(campaignErrors.name)} helperText={campaignErrors.name || "Campaign name must be 60 characters or fewer."} inputProps={{ maxLength: 60 }} />
            <FormControl fullWidth>
              <InputLabel>Campaign Type</InputLabel>
              <Select label="Campaign Type *" value={campaignForm.campaignType} onChange={(e) => setCampaignForm((c) => ({ ...c, campaignType: String(e.target.value) as CarePilotCampaignType }))}>
                {CAMPAIGN_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Trigger Type</InputLabel>
              <Select label="Trigger Type *" value={campaignForm.triggerType} onChange={(e) => setCampaignForm((c) => ({ ...c, triggerType: String(e.target.value) as CarePilotTriggerType }))}>
                {TRIGGER_TYPES.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Audience Type</InputLabel>
              <Select label="Audience Type *" value={campaignForm.audienceType} onChange={(e) => setCampaignForm((c) => ({ ...c, audienceType: String(e.target.value) as CarePilotAudienceType }))}>
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

      <Dialog
        open={attemptsOpen}
        onClose={closeAttempts}
        fullWidth
        maxWidth="md"
        aria-labelledby="campaign-delivery-attempts-title"
        aria-describedby="campaign-delivery-attempts-description"
      >
        <DialogTitle id="campaign-delivery-attempts-title" sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
          <span>{attemptsTitle}</span>
          <IconButton aria-label="Close campaign delivery attempts" onClick={closeAttempts} size="small">
            <CloseRoundedIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Box id="campaign-delivery-attempts-description" sx={{ position: "absolute", width: 1, height: 1, overflow: "hidden", clip: "rect(0 0 0 0)", whiteSpace: "nowrap" }}>
            Delivery attempt history for the selected campaign execution.
          </Box>
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
                    <TableCell>{providerLabel(attempt.providerName)}</TableCell>
                    <TableCell>{channelTypeLabel(attempt.channelType)}</TableCell>
                    <TableCell>{humanizeCarePilotCode(attempt.deliveryStatus)}</TableCell>
                    <TableCell>{attempt.errorCode || "-"}</TableCell>
                    <TableCell>{attempt.errorMessage || "-"}</TableCell>
                    <TableCell>{formatCarePilotDateTime(attempt.attemptedAt, clinicTimeZone)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAttempts}>Close</Button>
        </DialogActions>
      </Dialog>

      <TextEntryDialog
        open={Boolean(requestChangesTarget)}
        title="Request campaign changes"
        description={requestChangesTarget ? `Campaign: ${requestChangesTarget.name}` : undefined}
        label="Review comments"
        placeholder="Explain what the manager needs to change"
        value=""
        maxLength={1000}
        confirmLabel="Request Changes"
        submittingLabel="Requesting..."
        onCancel={() => setRequestChangesTarget(null)}
        onSubmit={submitRequestChanges}
      />

      <ConfirmationDialog
        open={Boolean(activateTarget)}
        title="Activate campaign"
        description={activateTarget ? `Campaign: ${activateTarget.name} • ${campaignReferenceLabel(activateTarget)}` : undefined}
        confirmLabel="Activate"
        confirmColor="warning"
        confirmLoading={workingId === `activate-${activateTarget?.id}`}
        onCancel={() => setActivateTarget(null)}
        onConfirm={() => void submitActivateCampaign()}
      />

      <ConfirmationDialog
        open={Boolean(triggerTarget)}
        title="Run campaign"
        description={triggerTarget ? `Campaign: ${triggerTarget.name} • ${campaignReferenceLabel(triggerTarget)}` : undefined}
        confirmLabel="Run campaign"
        confirmColor="warning"
        confirmDisabled={triggerPreviewLoading || Boolean(triggerPreviewError) || !triggerPreview?.canTrigger}
        confirmLoading={triggerSubmitting}
        onCancel={() => {
          setTriggerTarget(null);
          setTriggerPreview(null);
          setTriggerPreviewError(null);
          setTriggerPreviewLoading(false);
          setTriggerRequestKey("");
        }}
        onConfirm={() => void submitTrigger()}
      >
        {triggerTarget ? (
          <Stack spacing={1.25}>
            {triggerPreviewLoading ? <Alert severity="info">Loading trigger preview...</Alert> : null}
            {triggerPreviewError ? <Alert severity="error">{triggerPreviewError}</Alert> : null}
            {triggerPreview ? (
              <Stack spacing={0.75} sx={{ mt: 0.5 }}>
                <Typography variant="body2"><b>Channel:</b> {triggerPreview.channelType} • <b>Template:</b> {triggerPreview.templateName || "No template"}</Typography>
                <Typography variant="body2"><b>Provider:</b> {triggerPreview.providerName} • {triggerPreview.providerMode}</Typography>
                <Typography variant="body2"><b>Manual execution dispatcher:</b> {triggerPreview.manualDispatcherEnabled ? "Enabled" : "Disabled"}</Typography>
                <Typography variant="body2"><b>Eligible recipients:</b> {triggerPreview.eligibleRecipients}</Typography>
                <Typography variant="body2"><b>Excluded recipients:</b> {triggerPreview.excludedRecipients}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Missing contact {triggerPreview.missingEmailOrPhoneCount} • Invalid destination {triggerPreview.invalidDestinationCount} • Consent/opt-out {triggerPreview.consentOrOptOutCount} • Duplicate {triggerPreview.duplicateRecipientCount} • Inactive {triggerPreview.inactivePatientCount} • Template data {triggerPreview.missingRequiredTemplateDataCount}
                </Typography>
                <Typography variant="body2">
                  <b>Estimated messages:</b> {triggerPreview.estimatedMessages}
                  {triggerPreview.estimatedBillableCost ? ` • Est. cost ${triggerPreview.estimatedBillableCost}` : ""}
                </Typography>
                <Typography variant="body2"><b>Approved configuration:</b> {triggerPreview.approvedConfigurationValid ? "Valid" : "Invalid"}</Typography>
                <Typography variant="body2"><b>Environment:</b> {triggerPreview.environmentWarning || "Production/UAT"}</Typography>
                <Typography variant="body2"><b>Confirmation:</b> Type Run campaign to queue executions for the eligible recipients listed above.</Typography>
                {triggerPreview.blockingReasons.length > 0 ? (
                  <Alert severity="warning" sx={{ whiteSpace: "pre-line" }}>
                    {triggerPreview.blockingReasons.join("\n")}
                  </Alert>
                ) : null}
              </Stack>
            ) : null}
          </Stack>
        ) : null}
      </ConfirmationDialog>

      {lastTriggerResult ? (
        <Alert severity={lastTriggerResult.queuedExecutions > 0 ? "success" : "info"} sx={{ mb: 2 }}>
          Campaign queued for {lastTriggerResult.queuedExecutions} recipients. Execution reference: {lastTriggerResult.executionReference}. Campaign: {lastTriggerResult.campaignName} • {lastTriggerResult.campaignReference}. Status: {lastTriggerResult.status}.
        </Alert>
      ) : null}

      <ConfirmationDialog
        open={Boolean(retryTarget)}
        title={retryTarget?.resend ? "Resend execution" : "Retry execution"}
        description={retryTarget ? `This will ${retryTarget.resend ? "resend" : "retry"} the selected execution. Continue?` : undefined}
        confirmLabel={retryTarget?.resend ? "Resend" : "Retry"}
        confirmColor="warning"
        onCancel={() => setRetryTarget(null)}
        onConfirm={() => void submitRetry()}
      />

      <Dialog
        open={editCampaignOpen && Boolean(editCampaignTarget)}
        onClose={(_, reason) => {
          if (editCampaignSaving || reason === "backdropClick" || reason === "escapeKeyDown") {
            if (editCampaignSaving) return;
          }
          if (!editCampaignSaving) closeEditCampaign();
        }}
        fullWidth
        maxWidth="md"
        disableEscapeKeyDown={editCampaignSaving}
      >
        <DialogTitle>{editCampaignTarget?.status === "CHANGES_REQUESTED" ? "Edit campaign and resubmit" : "Edit campaign"}</DialogTitle>
        <DialogContent>
          {editCampaignTarget ? (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              {editCampaignTarget.status === "CHANGES_REQUESTED" ? (
                <Alert severity="warning" sx={{ whiteSpace: "pre-line" }}>
                  <b>Review comment:</b> {editCampaignTarget.reviewComment || "A reviewer has requested changes."}
                  {latestReviewHistoryEntry?.createdAt ? `\nReviewed ${new Date(latestReviewHistoryEntry.createdAt).toLocaleString()}` : editCampaignTarget.reviewedAt ? `\nReviewed ${new Date(editCampaignTarget.reviewedAt).toLocaleString()}` : ""}
                  {latestReviewHistoryEntry?.actorRoleLabel || latestReviewHistoryEntry?.actorRole ? `\nReviewer role: ${latestReviewHistoryEntry.actorRoleLabel || latestReviewHistoryEntry.actorRole}` : ""}
                  {latestReviewHistoryEntry?.actorDisplayName ? `\nReviewer: ${actorDisplayLine(latestReviewHistoryEntry.actorDisplayName, latestReviewHistoryEntry.actorRoleLabel || latestReviewHistoryEntry.actorRole, latestReviewHistoryEntry.actorEmployeeCode, latestReviewHistoryEntry.actorUsername)}` : editCampaignTarget.reviewedByDisplayName ? `\nReviewer: ${actorDisplayLine(editCampaignTarget.reviewedByDisplayName, editCampaignTarget.reviewedByRoleLabel, editCampaignTarget.reviewedByEmployeeCode, editCampaignTarget.reviewedByUsername)}` : ""}
                  {"\n"}Update the campaign and resubmit it for review.
                </Alert>
              ) : null}
              <TextField
                label="Campaign Name *"
                value={editCampaignForm.name}
                onChange={(e) => {
                  setEditCampaignForm((current) => ({ ...current, name: e.target.value }));
                  if (editCampaignErrors.name) setEditCampaignErrors((current) => ({ ...current, name: "" }));
                }}
                required
                inputProps={{ maxLength: 60 }}
                error={Boolean(editCampaignErrors.name)}
                helperText={editCampaignErrors.name || "Campaign name must be 60 characters or fewer."}
              />
              <TextField
                label="Description"
                multiline
                minRows={3}
                value={editCampaignForm.notes}
                onChange={(e) => setEditCampaignForm((current) => ({ ...current, notes: e.target.value }))}
                inputProps={{ maxLength: 250 }}
              />
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth>
                    <InputLabel>Campaign Type</InputLabel>
                    <Select
                      label="Campaign Type"
                      value={editCampaignForm.campaignType}
                      onChange={(e) => setEditCampaignForm((current) => ({ ...current, campaignType: String(e.target.value) as CarePilotCampaignType }))}
                    >
                      {CAMPAIGN_TYPES.map((type) => <MenuItem key={type} value={type}>{campaignTypeLabel(type)}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth>
                    <InputLabel>Trigger Type</InputLabel>
                    <Select
                      label="Trigger Type"
                      value={editCampaignForm.triggerType}
                      onChange={(e) => setEditCampaignForm((current) => ({ ...current, triggerType: String(e.target.value) as CarePilotTriggerType }))}
                    >
                      {TRIGGER_TYPES.map((type) => <MenuItem key={type} value={type}>{triggerTypeLabel(type)}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth>
                    <InputLabel>Audience Type</InputLabel>
                    <Select
                      label="Audience Type"
                      value={editCampaignForm.audienceType}
                      onChange={(e) => setEditCampaignForm((current) => ({ ...current, audienceType: String(e.target.value) as CarePilotAudienceType }))}
                    >
                      {AUDIENCE_TYPES.map((type) => <MenuItem key={type} value={type}>{audienceTypeLabel(type)}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>
              <FormControl fullWidth>
                <InputLabel>Template</InputLabel>
                <Select
                  label="Template"
                  value={editCampaignForm.templateId}
                  onChange={(e) => setEditCampaignForm((current) => ({ ...current, templateId: String(e.target.value) }))}
                >
                  <MenuItem value="">None</MenuItem>
                  {templates.map((template) => <MenuItem key={template.id} value={template.id}>{template.name} • {channelTypeLabel(template.channelType)}</MenuItem>)}
                </Select>
              </FormControl>
              <TextField
                label="Derived Channel"
                value={editCampaignForm.templateId ? (templateById.get(editCampaignForm.templateId)?.channelType ? channelTypeLabel(templateById.get(editCampaignForm.templateId)!.channelType) : "-") : "-"}
                InputProps={{ readOnly: true }}
              />
              {editCampaignTarget.status === "CHANGES_REQUESTED" ? (
                <>
                  <TextField
                    label="Manager Resolution Note"
                    multiline
                    minRows={3}
                    value={editCampaignForm.resolutionNote}
                    onChange={(e) => {
                      setEditCampaignForm((current) => ({ ...current, resolutionNote: e.target.value }));
                      if (editCampaignErrors.resolutionNote) setEditCampaignErrors((current) => ({ ...current, resolutionNote: "" }));
                    }}
                    placeholder="Explain what changed or confirm the reviewer comment was addressed"
                    helperText={editCampaignErrors.resolutionNote || "Optional if you have made a campaign change. Required only when resubmitting unchanged content."}
                    inputProps={{ maxLength: 1000 }}
                  />
                  <Alert severity={editCampaignCanResubmit ? "success" : "info"} sx={{ whiteSpace: "pre-line" }}>
                    {editCampaignCanResubmit
                      ? "This campaign is ready to resubmit."
                      : "Save a campaign change or provide a resolution note before resubmitting."}
                  </Alert>
                </>
              ) : null}
              {editCampaignErrors.form ? <Alert severity="error">{editCampaignErrors.form}</Alert> : null}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              if (!editCampaignSaving) closeEditCampaign();
            }}
            disabled={editCampaignSaving}
          >
            Cancel
          </Button>
          <Button
            variant="outlined"
            disabled={editCampaignSaving || !editCampaignHasChanges}
            onClick={() => void onSaveEditCampaign()}
          >
            {editCampaignSaving ? "Saving..." : "Save"}
          </Button>
          {editCampaignTarget?.status === "CHANGES_REQUESTED" ? (
            <Button
              variant="contained"
              color="warning"
              disabled={editCampaignSaving || !editCampaignCanResubmit}
              onClick={() => void onResubmitEditedCampaign()}
            >
              {editCampaignSaving ? "Submitting..." : "Resubmit"}
            </Button>
          ) : null}
        </DialogActions>
      </Dialog>

      <ConfirmationDialog
        open={editCampaignConflictOpen}
        title="Campaign version conflict"
        description={editCampaignConflictMessage || "The campaign was updated by another user."}
        cancelLabel="Cancel"
        confirmLabel={editCampaignConflictReloading ? "Reloading..." : "Reload latest"}
        confirmColor="warning"
        confirmDisabled={editCampaignConflictReloading}
        onCancel={() => setEditCampaignConflictOpen(false)}
        onConfirm={() => void reloadLatestEditCampaign()}
      />

      <Snackbar open={Boolean(toast)} autoHideDuration={3500} onClose={() => setToast(null)}>
        <Alert severity={toast?.type || "success"} onClose={() => setToast(null)}>{toast?.text}</Alert>
      </Snackbar>
    </Stack>
  );
}
