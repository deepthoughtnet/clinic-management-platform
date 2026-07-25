import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import {
  getCommercialPlanTemplate,
  type CommercialPlanTemplateDetail,
  type CommercialPlanTemplateSummary,
  type CommercialSubscriptionDetail,
  type CommercialSubscriptionSummary,
  type CommercialSubscriptionStatus,
  type PlatformTenant,
} from "../../api/clinicApi";
import {
  formatCommercialDate,
  formatCommercialPlanVersionSummary,
  pickCurrentCommercialSubscription,
  subscriptionStatusLabel,
  subscriptionSummaryLine,
  subscriptionSummaryMeta,
  subscriptionSummaryTitle,
} from "./commercialSubscriptionView";

type ActivationType = "IMMEDIATE" | "SCHEDULED";

type AssignmentForm = {
  tenantId: string;
  planTemplateId: string;
  publishedVersionId: string;
  activationType: ActivationType;
  startDate: string;
  endDate: string;
  autoRenew: boolean;
  displayName: string;
  referenceNumber: string;
  notes: string;
};

type Props = {
  open: boolean;
  mode: "create" | "replace";
  token: string;
  tenants: PlatformTenant[];
  tenantSubscriptions: CommercialSubscriptionSummary[];
  templates: CommercialPlanTemplateSummary[];
  initialSubscription: CommercialSubscriptionDetail | null;
  canManage: boolean;
  submitting: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (payload: {
    tenantId: string;
    publishedVersionId: string;
    startDate: string;
    endDate?: string | null;
    autoRenew: boolean;
    displayName?: string | null;
    referenceNumber?: string | null;
    notes?: string | null;
  }) => Promise<void>;
};

function todayIsoDate() {
  const date = new Date();
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 10);
}

function tomorrowIsoDate() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 10);
}

function sameForm(left: AssignmentForm, right: AssignmentForm) {
  return Object.keys(left).every((key) => left[key as keyof AssignmentForm] === right[key as keyof AssignmentForm]);
}

function isFutureDate(value: string) {
  return value > todayIsoDate();
}

function defaultForm(): AssignmentForm {
  return {
    tenantId: "",
    planTemplateId: "",
    publishedVersionId: "",
    activationType: "IMMEDIATE",
    startDate: todayIsoDate(),
    endDate: "",
    autoRenew: true,
    displayName: "",
    referenceNumber: "",
    notes: "",
  };
}

function formFromSubscription(subscription: CommercialSubscriptionDetail): AssignmentForm {
  return {
    tenantId: subscription.tenantId,
    planTemplateId: subscription.planTemplateId,
    publishedVersionId: subscription.publishedVersionId,
    activationType: isFutureDate(subscription.startDate) ? "SCHEDULED" : "IMMEDIATE",
    startDate: subscription.startDate,
    endDate: subscription.endDate || "",
    autoRenew: subscription.autoRenew,
    displayName: subscription.displayName || `${subscription.planTemplateName} Subscription`,
    referenceNumber: subscription.referenceNumber || "",
    notes: subscription.notes || "",
  };
}

function formatStatusTone(status: string): NonNullable<React.ComponentProps<typeof Chip>["color"]> {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "SCHEDULED":
      return "info";
    case "PAUSED":
      return "warning";
    case "DRAFT":
      return "default";
    case "EXPIRED":
    case "CANCELLED":
    case "SUPERSEDED":
      return "default";
    default:
      return "default";
  }
}

function formatStateTone(activated: boolean): NonNullable<React.ComponentProps<typeof Chip>["color"]> {
  return activated ? "success" : "default";
}

function validate(form: AssignmentForm, mode: "create" | "replace", tenantFixed: boolean) {
  const errors: Partial<Record<keyof AssignmentForm, string>> = {};
  if (!tenantFixed && !form.tenantId) {
    errors.tenantId = "Select a tenant.";
  }
  if (!form.planTemplateId) {
    errors.planTemplateId = "Select a published plan.";
  }
  if (!form.publishedVersionId) {
    errors.publishedVersionId = "Select a published version.";
  }
  if (!form.startDate) {
    errors.startDate = "Select an effective start date.";
  } else if (form.activationType === "SCHEDULED" && !isFutureDate(form.startDate)) {
    errors.startDate = "Choose a future start date for a scheduled assignment.";
  }
  if (form.endDate && form.startDate && form.endDate <= form.startDate) {
    errors.endDate = "End date must be after the start date.";
  }
  if (!form.displayName.trim()) {
    errors.displayName = "Display name is required.";
  }
  if (mode === "replace" && !tenantFixed) {
    errors.tenantId = "Tenant is fixed for replacements.";
  }
  return errors;
}

function firstErrorField(errors: Partial<Record<keyof AssignmentForm, string>>) {
  return Object.keys(errors)[0] as keyof AssignmentForm | undefined;
}

function NativeCard({
  selected,
  disabled,
  onClick,
  title,
  code,
  description,
  status,
  selectedLabel,
  children,
}: {
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
  title: string;
  code?: string | null;
  description?: string | null;
  status?: string | null;
  selectedLabel: string;
  children?: React.ReactNode;
}) {
  return (
    <Paper
      component="button"
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={disabled ? undefined : onClick}
      variant="outlined"
      sx={{
        width: "100%",
        p: 1.5,
        textAlign: "left",
        cursor: disabled ? "not-allowed" : "pointer",
        borderColor: selected ? "primary.main" : "divider",
        bgcolor: selected ? "action.selected" : "background.paper",
        display: "block",
        transition: "background-color 120ms ease, border-color 120ms ease, transform 120ms ease",
        "&:hover": disabled ? undefined : { borderColor: "primary.main", bgcolor: "action.hover" },
        "&:focus-visible": {
          outline: "2px solid",
          outlineColor: "primary.main",
          outlineOffset: 2,
        },
      }}
    >
      <Stack spacing={1}>
        <Stack direction="row" spacing={1.25} alignItems="flex-start" justifyContent="space-between">
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
              {title}
            </Typography>
            {code ? (
              <Typography variant="body2" color="text.secondary">
                Code: {code}
              </Typography>
            ) : null}
            {description ? (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                {description}
              </Typography>
            ) : null}
          </Box>
          <Stack spacing={0.5} alignItems="flex-end" sx={{ flexShrink: 0 }}>
            {status ? <Chip size="small" label={status} color={formatStatusTone(status)} /> : null}
            <Chip size="small" label={selectedLabel} color={formatStateTone(selected)} variant={selected ? "filled" : "outlined"} />
          </Stack>
        </Stack>
        {children}
      </Stack>
    </Paper>
  );
}

export default function CommercialSubscriptionAssignmentDialog({
  open,
  mode,
  token,
  tenants,
  tenantSubscriptions,
  templates,
  initialSubscription,
  canManage,
  submitting,
  error,
  onClose,
  onSubmit,
}: Props) {
  const [step, setStep] = React.useState(0);
  const [searchTenant, setSearchTenant] = React.useState("");
  const [searchPlan, setSearchPlan] = React.useState("");
  const [form, setForm] = React.useState<AssignmentForm>(defaultForm());
  const [initialForm, setInitialForm] = React.useState<AssignmentForm>(defaultForm());
  const [selectedTemplate, setSelectedTemplate] = React.useState<CommercialPlanTemplateDetail | null>(null);
  const [loadingTemplate, setLoadingTemplate] = React.useState(false);
  const [discardOpen, setDiscardOpen] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    const next = mode === "replace" && initialSubscription ? formFromSubscription(initialSubscription) : defaultForm();
    setForm(next);
    setInitialForm(next);
    setStep(0);
    setSearchTenant("");
    setSearchPlan("");
    setSelectedTemplate(null);
    setDiscardOpen(false);
  }, [initialSubscription, mode, open]);

  React.useEffect(() => {
    if (!open || !form.planTemplateId) {
      setSelectedTemplate(null);
      return;
    }
    let cancelled = false;
    async function loadTemplate() {
      setLoadingTemplate(true);
      try {
        const template = await getCommercialPlanTemplate(token, form.planTemplateId);
        if (cancelled) return;
        setSelectedTemplate(template);
        const publishedVersion = template.latestPublishedVersion;
        if (publishedVersion) {
          setForm((current) => {
            if (current.publishedVersionId === publishedVersion.id) {
              return current;
            }
            return {
              ...current,
              publishedVersionId: publishedVersion.id,
              displayName: current.displayName.trim() ? current.displayName : `${template.name} Subscription`,
            };
          });
        }
      } catch {
        if (!cancelled) {
          setSelectedTemplate(null);
        }
      } finally {
        if (!cancelled) {
          setLoadingTemplate(false);
        }
      }
    }
    void loadTemplate();
    return () => {
      cancelled = true;
    };
  }, [form.planTemplateId, open, token]);

  const currentTenantId = mode === "replace" && initialSubscription ? initialSubscription.tenantId : form.tenantId;
  const selectedTenant = React.useMemo(() => tenants.find((tenant) => tenant.id === currentTenantId) || null, [currentTenantId, tenants]);
  const currentCommercialSubscription = React.useMemo(
    () => pickCurrentCommercialSubscription(tenantSubscriptions, currentTenantId),
    [currentTenantId, tenantSubscriptions],
  );
  const selectedPlan = selectedTemplate;
  const selectedVersion = selectedTemplate?.latestPublishedVersion || null;
  const tenantFixed = mode === "replace" && Boolean(initialSubscription);
  const formErrors = React.useMemo(() => validate(form, mode, tenantFixed), [form, mode, tenantFixed]);
  const dirty = !sameForm(form, initialForm);
  const canSubmit = canManage && !submitting && Object.keys(formErrors).length === 0 && Boolean(currentTenantId) && Boolean(form.planTemplateId) && Boolean(form.publishedVersionId);
  const canContinueTenant = tenantFixed || Boolean(form.tenantId);
  const canContinuePlan = canContinueTenant && Boolean(form.planTemplateId) && Boolean(form.publishedVersionId);
  const canContinueSchedule = canContinuePlan && Object.keys(formErrors).filter((field) => field === "startDate" || field === "endDate" || field === "displayName").length === 0;
  const maxAccessibleStep = canContinueSchedule ? 3 : canContinuePlan ? 2 : canContinueTenant ? 1 : 0;

  React.useEffect(() => {
    if (step > maxAccessibleStep) {
      setStep(maxAccessibleStep);
    }
  }, [maxAccessibleStep, step]);

  const activeTenants = tenants.filter((tenant) => {
    const search = searchTenant.trim().toLowerCase();
    if (!search) return true;
    return [tenant.name, tenant.code, tenant.status].some((value) => (value || "").toLowerCase().includes(search));
  });
  const publishableTemplates = templates.filter((template) => template.latestPublishedVersionNumber != null).filter((template) => {
    const search = searchPlan.trim().toLowerCase();
    if (!search) return true;
    return [template.name, template.code, template.description || "", template.targetSegment].some((value) => String(value).toLowerCase().includes(search));
  });

  function requestClose() {
    if (dirty) {
      setDiscardOpen(true);
      return;
    }
    onClose();
  }

  async function submit() {
    if (!canSubmit) {
      const invalidField = firstErrorField(formErrors);
      if (invalidField) {
        const selector = invalidField === "tenantId"
          ? '[name="commercial-subscription-searchTenant"]'
          : invalidField === "planTemplateId"
            ? '[name="commercial-subscription-searchPlan"]'
            : invalidField === "publishedVersionId"
              ? '[name="commercial-subscription-searchPlan"]'
              : invalidField === "startDate"
                ? '[name="commercial-subscription-startDate"]'
                : invalidField === "endDate"
                  ? '[name="commercial-subscription-endDate"]'
                  : '[name="commercial-subscription-displayName"]';
        document.querySelector<HTMLElement>(selector)?.focus();
      }
      return;
    }
    await onSubmit({
      tenantId: currentTenantId,
      publishedVersionId: form.publishedVersionId,
      startDate: form.startDate,
      endDate: form.endDate || null,
      autoRenew: form.autoRenew,
      displayName: form.displayName.trim(),
      referenceNumber: form.referenceNumber.trim() || null,
      notes: form.notes.trim() || null,
    });
  }

  function advance() {
    if (step === 0) {
      if (!canContinueTenant) {
        document.querySelector<HTMLInputElement>('[name="commercial-subscription-searchTenant"]')?.focus();
        return;
      }
      setStep(1);
      return;
    }
    if (step === 1) {
      if (!canContinuePlan) {
        document.querySelector<HTMLInputElement>('[name="commercial-subscription-searchPlan"]')?.focus();
        return;
      }
      setStep(2);
      return;
    }
    if (step === 2) {
      if (!canContinueSchedule) {
        const invalidField = firstErrorField(formErrors);
        const selector = invalidField === "endDate"
          ? '[name="commercial-subscription-endDate"]'
          : invalidField === "displayName"
            ? '[name="commercial-subscription-displayName"]'
            : '[name="commercial-subscription-startDate"]';
        document.querySelector<HTMLInputElement>(selector)?.focus();
        return;
      }
      setStep(3);
    }
  }

  if (!open) return null;

  const currentStepLabel = ["Tenant", "Published Version", "Schedule", "Review"][step];
  const currentTenantSummary = currentCommercialSubscription ? subscriptionSummaryTitle(currentCommercialSubscription) : "No commercial subscription";
  const currentTenantSummaryLine = currentCommercialSubscription ? subscriptionSummaryLine(currentCommercialSubscription) : "No commercial subscription";
  const currentTenantCommercialStatus = currentCommercialSubscription ? subscriptionStatusLabel(currentCommercialSubscription.subscriptionStatus) : "None";

  return (
    <>
      <Dialog
        open
        fullWidth
        maxWidth="lg"
        onClose={(_, reason) => {
          if (reason === "backdropClick" || reason === "escapeKeyDown") {
            requestClose();
            return;
          }
          requestClose();
        }}
        PaperProps={{
          sx: {
            height: "min(92vh, 980px)",
            overflow: "hidden",
          },
        }}
      >
        <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
          <Box sx={{ px: 3, pt: 2.5, pb: 2, borderBottom: 1, borderColor: "divider", flexShrink: 0 }}>
            <Typography variant="overline" sx={{ letterSpacing: 1.2, display: "block" }}>
              Commercial subscription
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              {mode === "replace" ? "Replace Subscription" : "Assign Subscription"}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Commercial subscription records are design-time commercial assignments only. Tenant runtime access remains governed by the existing legacy model.
            </Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: "wrap" }}>
              <Chip label="Draft Assignment" size="small" variant="outlined" />
              <Chip label="Not Submitted" size="small" variant="outlined" />
              {dirty ? <Chip label="Changes pending" size="small" color="warning" /> : <Chip label="Ready to review" size="small" color="success" />}
            </Stack>
          </Box>

          <Box sx={{ px: 3, pt: 2, pb: 1 }}>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              {["Tenant", "Published Version", "Schedule", "Review"].map((label, index) => (
                <Button
                  key={label}
                  type="button"
                  size="small"
                  variant={step === index ? "contained" : "outlined"}
                  color={step === index ? "primary" : index < step ? "success" : "inherit"}
                  disabled={index > maxAccessibleStep && index !== step}
                  aria-current={step === index ? "step" : undefined}
                  onClick={() => {
                    if (index <= maxAccessibleStep) {
                      setStep(index);
                    }
                  }}
                >
                  {index + 1}. {label}
                </Button>
              ))}
            </Stack>
          </Box>

          <Box sx={{ flex: 1, overflowY: "auto", px: 3, py: 1 }}>
            <Stack spacing={2}>
              {error ? <Alert severity="error">{error}</Alert> : null}
              <Alert severity="info" variant="outlined">
                Step {step + 1} of 4: {currentStepLabel}
              </Alert>

              {step === 0 ? (
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    Select Tenant
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Choose the tenant that will receive the commercial subscription assignment.
                  </Typography>
                  <TextField
                    name="commercial-subscription-searchTenant"
                    label="Search tenants"
                    value={searchTenant}
                    onChange={(event) => setSearchTenant(event.target.value)}
                    placeholder="Search by tenant name or code"
                    autoFocus
                  />
                  <Stack spacing={1}>
                    {mode === "replace" && initialSubscription ? (
                      <Alert severity="info">
                        Tenant is fixed for replacements: {selectedTenant?.name || "Selected tenant"}
                      </Alert>
                    ) : null}
                    {activeTenants.map((tenant) => {
                      const checked = currentTenantId === tenant.id;
                      const selectable = mode === "create" ? tenant.status === "ACTIVE" : checked;
                      const currentSubscription = pickCurrentCommercialSubscription(tenantSubscriptions, tenant.id);
                      return (
                        <NativeCard
                          key={tenant.id}
                          title={tenant.name}
                          code={tenant.code}
                          description={tenant.status === "ACTIVE" ? "Active tenant available for assignment." : "Inactive tenant."}
                          status={tenant.status}
                          selected={checked}
                          selectedLabel={checked ? "Selected" : "Available"}
                          disabled={!selectable}
                          onClick={() => {
                            if (!selectable) return;
                            if (mode === "create") {
                              setForm((current) => ({
                                ...current,
                                tenantId: tenant.id,
                                displayName: current.displayName.trim() ? current.displayName : `${tenant.name} Subscription`,
                              }));
                            }
                          }}
                        >
                          <Box sx={{ mt: 0.75 }}>
                            <Typography variant="body2" color="text.secondary">
                              Current subscription: {subscriptionSummaryTitle(currentSubscription)}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {subscriptionSummaryMeta(currentSubscription)}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {subscriptionSummaryLine(currentSubscription)}
                            </Typography>
                          </Box>
                        </NativeCard>
                      );
                    })}
                    {activeTenants.length === 0 ? <Alert severity="info">No tenants match your search.</Alert> : null}
                  </Stack>
                  <Divider />
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={0.75}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Selected Tenant
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedTenant?.name || "No tenant selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Tenant code: {selectedTenant?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Status: {selectedTenant?.status || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Current subscription: {currentTenantSummary || "No commercial subscription"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {currentTenantSummaryLine}
                      </Typography>
                    </Stack>
                  </Paper>
                </Stack>
              ) : null}

              {step === 1 ? (
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    Select Published Version
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Choose the published commercial plan version to assign to the selected tenant.
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={0.5}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Selected Tenant
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedTenant?.name || "No tenant selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Tenant code: {selectedTenant?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Current subscription: {currentTenantSummary || "No commercial subscription"}
                      </Typography>
                    </Stack>
                  </Paper>
                  <TextField
                    name="commercial-subscription-searchPlan"
                    label="Search published plans"
                    value={searchPlan}
                    onChange={(event) => setSearchPlan(event.target.value)}
                    placeholder="Search by plan name, code, description, or target segment"
                    autoFocus
                  />
                  {loadingTemplate ? <Alert severity="info">Loading plan details…</Alert> : null}
                  <Stack spacing={1}>
                    {publishableTemplates.map((template) => {
                      const checked = form.planTemplateId === template.id;
                      const publishedVersionLabel = template.latestPublishedVersionNumber != null ? `Version ${template.latestPublishedVersionNumber}` : "Published version";
                      return (
                        <NativeCard
                          key={template.id}
                          title={template.name}
                          code={template.code}
                          description={template.description || `Target segment: ${template.targetSegment}`}
                          status={template.status}
                          selected={checked}
                          selectedLabel={checked ? "Selected" : "Available"}
                          disabled={!canManage}
                          onClick={() => {
                            if (!canManage) return;
                            setForm((current) => ({
                              ...current,
                              planTemplateId: template.id,
                              publishedVersionId: "",
                              displayName: current.displayName.trim() ? current.displayName : `${template.name} Subscription`,
                            }));
                            setSelectedTemplate(null);
                          }}
                        >
                          <Box sx={{ mt: 0.75 }}>
                            <Typography variant="body2" color="text.secondary">
                              {publishedVersionLabel}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              Target segment: {template.targetSegment}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {template.capabilityCount} capabilities · {template.moduleCount} modules · {template.featureCount} features · {template.limitCount} limits · {template.addonCount} add-ons
                            </Typography>
                          </Box>
                        </NativeCard>
                      );
                    })}
                    {publishableTemplates.length === 0 ? <Alert severity="info">No published plans match your search.</Alert> : null}
                  </Stack>
                  {selectedVersion ? (
                    <Paper variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={0.75}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                          Selected Plan
                        </Typography>
                        <Typography variant="body1" sx={{ fontWeight: 700 }}>
                          {selectedPlan?.name || "Selected plan"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Code: {selectedPlan?.code || "—"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {formatCommercialPlanVersionSummary(selectedVersion)}
                        </Typography>
                      </Stack>
                    </Paper>
                  ) : form.planTemplateId ? (
                    <Alert severity="warning">Select a published template to load its latest published version.</Alert>
                  ) : null}
                </Stack>
              ) : null}

              {step === 2 ? (
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    Schedule
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Define when the commercial subscription assignment becomes effective.
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Selected Tenant
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedTenant?.name || "No tenant selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Tenant code: {selectedTenant?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Current subscription: {currentTenantSummary || "No commercial subscription"}
                      </Typography>
                    </Stack>
                  </Paper>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Selected Plan
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedPlan?.name || "No plan selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Code: {selectedPlan?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {selectedVersion ? formatCommercialPlanVersionSummary(selectedVersion) : "Published version not loaded yet"}
                      </Typography>
                    </Stack>
                  </Paper>
                  <FormControl>
                    <Typography variant="subtitle2" sx={{ fontWeight: 900, mb: 1 }}>
                      Activation
                    </Typography>
                    <RadioGroup
                      row
                      value={form.activationType}
                      onChange={(event) => {
                        const nextType = event.target.value as ActivationType;
                        setForm((current) => ({
                          ...current,
                          activationType: nextType,
                          startDate: nextType === "IMMEDIATE" ? todayIsoDate() : (current.startDate && isFutureDate(current.startDate) ? current.startDate : tomorrowIsoDate()),
                        }));
                      }}
                    >
                      <FormControlLabel value="IMMEDIATE" control={<Radio />} label="Effective immediately" />
                      <FormControlLabel value="SCHEDULED" control={<Radio />} label="Schedule for a future date" />
                    </RadioGroup>
                  </FormControl>
                  <TextField
                    name="commercial-subscription-startDate"
                    label="Start Date *"
                    type="date"
                    value={form.startDate}
                    onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    error={Boolean(formErrors.startDate)}
                    helperText={formErrors.startDate || (form.activationType === "IMMEDIATE" ? "Effective immediately uses today's date." : "Choose a future activation date.")}
                    disabled={form.activationType === "IMMEDIATE"}
                  />
                  <TextField
                    name="commercial-subscription-endDate"
                    label="End Date"
                    type="date"
                    value={form.endDate}
                    onChange={(event) => setForm((current) => ({ ...current, endDate: event.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    error={Boolean(formErrors.endDate)}
                    helperText={formErrors.endDate || "Optional end date."}
                  />
                  <TextField
                    name="commercial-subscription-displayName"
                    label="Display Name *"
                    value={form.displayName}
                    onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
                    error={Boolean(formErrors.displayName)}
                    helperText={formErrors.displayName || "Business-friendly label shown to commercial admins."}
                  />
                  <TextField
                    name="commercial-subscription-referenceNumber"
                    label="Reference Number"
                    value={form.referenceNumber}
                    onChange={(event) => setForm((current) => ({ ...current, referenceNumber: event.target.value }))}
                    helperText="Optional external reference or invoice number."
                  />
                  <TextField
                    name="commercial-subscription-notes"
                    label="Notes"
                    value={form.notes}
                    onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
                    multiline
                    minRows={3}
                    helperText="Optional assignment notes."
                  />
                  <FormControlLabel
                    control={<Checkbox checked={form.autoRenew} onChange={(event) => setForm((current) => ({ ...current, autoRenew: event.target.checked }))} />}
                    label="Auto renew"
                  />
                </Stack>
              ) : null}

              {step === 3 ? (
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    Review
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Review the commercial assignment before creating the record.
                  </Typography>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Tenant
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedTenant?.name || "No tenant selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Tenant code: {selectedTenant?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Status: {selectedTenant?.status || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Current subscription: {currentCommercialSubscription ? subscriptionSummaryTitle(currentCommercialSubscription) : "No commercial subscription"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {currentCommercialSubscription ? `${subscriptionSummaryMeta(currentCommercialSubscription)} · ${subscriptionSummaryLine(currentCommercialSubscription)}` : "This tenant has no existing commercial subscription."}
                      </Typography>
                    </Stack>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Commercial Plan
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {selectedPlan?.name || "No plan selected"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Code: {selectedPlan?.code || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Version {selectedVersion?.versionNumber || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Published {selectedVersion ? formatCommercialDate(selectedVersion.publishedAt) : "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {selectedVersion ? formatCommercialPlanVersionSummary(selectedVersion) : "Published version not loaded yet"}
                      </Typography>
                    </Stack>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Schedule
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {form.activationType === "IMMEDIATE" ? "Effective immediately" : "Scheduled for a future date"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Effective date: {formatCommercialDate(form.startDate)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        End date: {form.endDate ? formatCommercialDate(form.endDate) : "No end date"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Auto renew: {form.autoRenew ? "Enabled" : "Disabled"}
                      </Typography>
                    </Stack>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Assignment Details
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Display Name
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {form.displayName || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Reference Number
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {form.referenceNumber || "—"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Notes
                      </Typography>
                      <Typography variant="body1" sx={{ whiteSpace: "pre-wrap" }}>
                        {form.notes || "—"}
                      </Typography>
                    </Stack>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                        Current Subscription Impact
                      </Typography>
                      {currentCommercialSubscription ? (
                        <>
                          <Typography variant="body1" sx={{ fontWeight: 700 }}>
                            {subscriptionSummaryTitle(currentCommercialSubscription)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {subscriptionSummaryMeta(currentCommercialSubscription)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {subscriptionSummaryLine(currentCommercialSubscription)}
                          </Typography>
                          <Alert severity={currentCommercialSubscription.subscriptionStatus === "ACTIVE" || currentCommercialSubscription.subscriptionStatus === "SCHEDULED" ? "warning" : "info"} sx={{ mt: 1 }}>
                            This assignment will be validated against the existing commercial subscription. Use Replace if you need to supersede the current record.
                          </Alert>
                        </>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No existing commercial subscription.
                        </Typography>
                      )}
                    </Stack>
                  </Paper>
                </Stack>
              ) : null}
            </Stack>
          </Box>

          <Box sx={{ px: 3, py: 2, borderTop: 1, borderColor: "divider", flexShrink: 0, bgcolor: "background.paper" }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
              <Button onClick={requestClose}>Cancel</Button>
              <Stack direction="row" spacing={1}>
                {step > 0 ? (
                  <Button onClick={() => setStep((value) => Math.max(0, value - 1))}>Back</Button>
                ) : null}
                {step < 3 ? (
                  <Button variant="contained" onClick={advance} disabled={!canManage || submitting || (step === 0 && !canContinueTenant) || (step === 1 && !canContinuePlan) || (step === 2 && !canContinueSchedule)}>
                    Next
                  </Button>
                ) : (
                  <Button variant="contained" onClick={() => void submit()} disabled={!canSubmit}>
                    {mode === "replace" ? "Replace Subscription" : "Create Assignment"}
                  </Button>
                )}
              </Stack>
            </Stack>
          </Box>
        </Box>
      </Dialog>

      <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Discard subscription assignment?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            The tenant, plan, and schedule details you entered will be lost.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDiscardOpen(false)}>Keep Editing</Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => {
              setDiscardOpen(false);
              onClose();
            }}
          >
            Discard Assignment
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
