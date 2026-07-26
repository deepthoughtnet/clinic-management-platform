import * as React from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Paper,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Snackbar,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  compareCommercialPlanVersions,
  cloneCommercialPlanTemplate,
  createCommercialPlanTemplate,
  compareCommercialPlanPricing,
  getCommercialPlanTemplate,
  getCommercialPlanPricing,
  getCommercialPlanVersion,
  listCommercialAddons,
  listCommercialCapabilities,
  listCommercialFeatures,
  listCommercialLimits,
  listCommercialModules,
  listCommercialPlanTemplates,
  listCommercialPlanVersions,
  publishCommercialPlanVersion,
  retireCommercialPlanTemplate,
  saveCommercialPlanDraft,
  saveCommercialPlanPricing,
  updateCommercialPlanTemplate,
  validateCommercialPlanPricing,
  validateCommercialPlanDraft,
  type CommercialAddonSummary,
  type CommercialCapabilitySummary,
  type CommercialFeatureSummary,
  type CommercialLimitDefinitionSummary,
  type CommercialModuleSummary,
  type CommercialPlanTemplateDetail,
  type CommercialPlanVersionComparison,
  type CommercialPlanVersionDetail,
  type CommercialPlanVersionSummary,
  type CommercialPlanTemplateSummary,
  type CommercialPlanPricingSnapshot,
  type CommercialPlanPricingBillingCycle,
  type CommercialPlanPricingTaxModel,
  type CommercialPlanAddonPurchaseType,
  type CommercialPlanPricingMeteredRate,
  type CommercialPlanPricingAddon,
  type CommercialPlanPricingResponse,
  type CommercialPlanPricingValidationResult,
  type CommercialPlanPricingComparison,
  type CommercialPlanValidationResult,
  type CommercialPlanValidationFinding,
  type CommercialPlanSelectionSource,
  type CommercialPlanSelectionState,
} from "../../api/clinicApi";
import CommercialPlanSelectionDialog from "./CommercialPlanSelectionDialog";
import CommercialPricingWorkspace from "./CommercialPricingWorkspace";
import {
  CommercialPlanTemplateCreateDialog,
  CommercialPlanTemplateSummarySection,
  type CommercialPlanTemplateFormErrors,
  type CommercialPlanTemplateFormValue,
  defaultTemplateForm,
  templateFormFromDetail,
} from "./CommercialPlanTemplateEditor";

type WorkspaceTab = "summary" | "capabilities" | "modules" | "features" | "limits" | "pricing" | "addons" | "validation" | "versions" | "compare";
const WORKSPACE_TABS: WorkspaceTab[] = ["summary", "capabilities", "modules", "features", "limits", "pricing", "addons", "validation", "versions", "compare"];

type ModuleSelection = {
  moduleId: string;
  selectionSource: CommercialPlanSelectionSource;
  inherited: boolean;
  displayOrder: number;
};

type LimitSelection = {
  limitDefinitionId: string;
  configuredValue: string;
};

type AddonSelection = {
  addonId: string;
  selectionState: CommercialPlanSelectionState;
};

type PricingState = CommercialPlanPricingSnapshot;

type BuilderState = {
  capabilities: string[];
  modules: ModuleSelection[];
  features: string[];
  limits: LimitSelection[];
  addons: AddonSelection[];
  pricing: PricingState;
  draftNotes: string;
};

export function formatTrialDaysDisplay(trialDays: number | null | undefined): string {
  if (trialDays == null) {
    return "No trial";
  }
  return `${trialDays} day${trialDays === 1 ? "" : "s"}`;
}

export function parseTrialDaysInput(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isInteger(parsed) ? parsed : null;
}

function emptyPricingState(): PricingState {
  return {
    currency: "INR",
    billingCycle: "MONTHLY",
    monthlyPrice: "",
    annualPrice: "",
    setupFee: "",
    trialDays: null,
    taxModel: "NONE",
    taxPercentage: "",
    discountAllowed: false,
    meteredRates: [],
    addonPricing: [],
  };
}

function parseTab(value: string | null): WorkspaceTab {
  return WORKSPACE_TABS.includes(value as WorkspaceTab) ? (value as WorkspaceTab) : "summary";
}

function previewNames(items: Array<{ name: string; code: string }>) {
  return items.slice(0, 3).map((item) => item.name || item.code);
}

function previewChips(items: Array<{ name: string; code: string }>, moreLabel = "more") {
  const preview = previewNames(items);
  const extra = Math.max(0, items.length - preview.length);
  return (
    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
      {preview.map((label) => <Chip key={label} size="small" label={label} variant="outlined" />)}
      {extra > 0 ? <Chip size="small" label={`+${extra} ${moreLabel}`} variant="outlined" /> : null}
    </Stack>
  );
}

function formatCommercialDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function validationLabel(validation: CommercialPlanValidationResult | null | undefined) {
  if (!validation) return "Not validated";
  switch (validation.validationState) {
    case "VALID":
      return "Ready to publish";
    case "INVALID":
      return "Configuration incomplete";
    case "STALE":
      return "Validation is outdated";
    default:
      return "Not validated";
  }
}

function validationStatusTone(validation: CommercialPlanValidationResult | null | undefined) {
  if (!validation) return "default" as const;
  switch (validation.validationState) {
    case "VALID":
      return "success" as const;
    case "INVALID":
    case "STALE":
      return "warning" as const;
    default:
      return "default" as const;
  }
}

function validationCategoryLabel(category: string | null | undefined) {
  switch (category) {
    case "FEATURE_DEPENDENCY":
      return "Feature Dependencies";
    case "LIMIT_CONFIGURATION":
      return "Limits";
    case "CATALOG_STATUS":
      return "Catalog Status";
    case "ADDON_COMPATIBILITY":
      return "Add-on Compatibility";
    case "PLAN_CAPABILITY_REQUIRED":
      return "Capabilities";
    case "PLAN_MODULE_REQUIRED":
      return "Modules";
    case "DUPLICATE_SELECTION":
      return "Duplicate Selections";
    default:
      return "Validation";
  }
}

function validationTabLabel(tab: string | null | undefined) {
  switch (tab) {
    case "capabilities":
      return "Configure Capabilities";
    case "modules":
      return "Configure Modules";
    case "features":
      return "Review Features";
    case "limits":
      return "Configure Limits";
    case "addons":
      return "Configure Add-ons";
    default:
      return "Review Validation";
  }
}

type PricingValidationGroupKey = "subscription" | "currency" | "trial" | "tax" | "metered" | "addons";

const PRICING_MODEL_OPTIONS: Array<{
  value: CommercialPlanPricingBillingCycle;
  label: string;
  description: string;
}> = [
  { value: "MONTHLY", label: "Monthly Subscription", description: "Billed every month." },
  { value: "ANNUAL", label: "Annual Subscription", description: "Billed once per year." },
  { value: "QUARTERLY", label: "Quarterly Subscription", description: "Billed every three months." },
  { value: "ONE_TIME", label: "One-time Purchase", description: "A single non-recurring charge." },
  { value: "TRIAL", label: "Trial-only Plan", description: "Trial period only." },
];

function pricingModelLabel(value: CommercialPlanPricingBillingCycle | null | undefined) {
  return PRICING_MODEL_OPTIONS.find((option) => option.value === value)?.label || "Pricing not set";
}

function pricingModelDescription(value: CommercialPlanPricingBillingCycle | null | undefined) {
  return PRICING_MODEL_OPTIONS.find((option) => option.value === value)?.description || "Choose how customers pay for this plan.";
}

function pricingCurrencySymbol(currency: string | null | undefined) {
  switch ((currency || "").trim().toUpperCase()) {
    case "USD":
      return "$";
    case "EUR":
      return "€";
    case "INR":
    default:
      return "₹";
  }
}

function normalizeMoneyText(value: string) {
  const trimmed = value.replace(/,/g, "").trim();
  if (!trimmed) {
    return "";
  }
  let next = "";
  let seenDecimal = false;
  for (const char of trimmed) {
    if (char >= "0" && char <= "9") {
      next += char;
      continue;
    }
    if (char === "." && !seenDecimal) {
      next += char;
      seenDecimal = true;
    }
  }
  return next;
}

function formatMoneyPreview(value: string | null | undefined, currency: string | null | undefined) {
  if (!value) {
    return "—";
  }
  return `${pricingCurrencySymbol(currency)}${value}`;
}

function taxModelLabel(value: CommercialPlanPricingTaxModel | null | undefined) {
  switch (value) {
    case "EXCLUSIVE":
      return "Tax Exclusive";
    case "INCLUSIVE":
      return "Tax Inclusive";
    case "NONE":
    default:
      return "No tax";
  }
}

function taxModelDescription(value: CommercialPlanPricingTaxModel | null | undefined) {
  switch (value) {
    case "EXCLUSIVE":
      return "Tax is added on top of the listed price.";
    case "INCLUSIVE":
      return "Displayed prices already include tax.";
    case "NONE":
    default:
      return "No tax applied.";
  }
}

function addonPurchaseTypeLabel(value: CommercialPlanAddonPurchaseType | null | undefined) {
  switch (value) {
    case "MONTHLY":
      return "Monthly";
    case "ANNUAL":
      return "Annual";
    case "ONE_TIME":
      return "One-time";
    default:
      return "Purchase type";
  }
}

function pricingValidationGroup(finding: CommercialPlanValidationFinding): PricingValidationGroupKey {
  const field = finding.field || "";
  if (field.includes("meteredRates") || finding.targetBuilderTab === "limits") {
    return "metered";
  }
  if (field.includes("addonPricing") || finding.targetBuilderTab === "addons" || finding.category === "ADDON_COMPATIBILITY") {
    return "addons";
  }
  if (field.includes("currency")) {
    return "currency";
  }
  if (field.includes("trial")) {
    return "trial";
  }
  if (field.includes("tax")) {
    return "tax";
  }
  return "subscription";
}

function pricingComparisonHighlights(comparison: CommercialPlanPricingComparison | null | undefined) {
  if (!comparison) {
    return [];
  }
  const labels = new Set<string>();
  for (const item of comparison.subscriptionPricing || []) {
    switch (item.code) {
      case "monthly":
        labels.add("Monthly price changed");
        break;
      case "annual":
        labels.add("Annual price changed");
        break;
      case "currency":
        labels.add("Currency changed");
        break;
      case "trial":
        labels.add("Trial changed");
        break;
      case "setup":
        labels.add("Setup fee changed");
        break;
      case "tax":
        labels.add("Tax model changed");
        break;
      case "taxPercentage":
        labels.add("Tax percentage changed");
        break;
      default:
        break;
    }
  }
  if ((comparison.meteredRates || []).length > 0) {
    labels.add("Metered usage changed");
  }
  if ((comparison.addonPricing || []).length > 0) {
    labels.add("Add-on pricing changed");
  }
  return Array.from(labels);
}

function pricingDraftStateLabel(pricing: CommercialPlanPricingSnapshot, dirty: boolean, validation: CommercialPlanPricingValidationResult | null) {
  if (dirty) {
    return "Unsaved Changes";
  }
  if (validation?.validationState === "VALID" && validation.readyToPublish) {
    return "Configured";
  }
  if (validation?.validationState === "INVALID" || validation?.validationState === "STALE") {
    return "Incomplete";
  }
  return "Incomplete";
}

function pricingValidationStateLabel(validation: CommercialPlanPricingValidationResult | null, dirty: boolean) {
  if (!validation) {
    return "Ready";
  }
  if (dirty && validation.validationState === "VALID") {
    return "Ready";
  }
  if (validation.blockingFindingCount > 0) {
    return "Blocking Issues";
  }
  if (validation.warningFindingCount > 0) {
    return "Warnings";
  }
  return "Ready";
}

function validationSecondaryTab(finding: CommercialPlanValidationResult["findings"][number]) {
  if (finding.targetBuilderTab === "modules" && finding.affectedItemType === "FEATURE") {
    return "features";
  }
  if (finding.targetBuilderTab === "features" && finding.affectedItemType === "MODULE") {
    return "modules";
  }
  return null;
}

function validationStateLabel(value: string | null | undefined) {
  switch (value) {
    case "FEATURE":
      return "Feature";
    case "MODULE":
      return "Module";
    case "LIMIT":
      return "Limit";
    case "ADDON":
      return "Add-on";
    case "CAPABILITY":
      return "Capability";
    case "PLAN_TEMPLATE":
      return "Plan";
    default:
      return value || "Item";
  }
}

export function ValidationFindingCard({
  finding,
  onNavigateTab,
}: {
  finding: CommercialPlanValidationResult["findings"][number];
  onNavigateTab: (tab: WorkspaceTab) => void;
}) {
  const secondaryTab = validationSecondaryTab(finding);
  const severityLabel = finding.blocking ? "Blocking" : finding.severity === "WARNING" ? "Warning" : "Info";
  const severityColor = finding.blocking ? "error" : finding.severity === "WARNING" ? "warning" : "default";
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
          <Chip size="small" label={severityLabel} color={severityColor} />
          <Chip size="small" label={validationCategoryLabel(finding.category)} variant="outlined" />
        </Stack>
        <Stack spacing={0.5}>
          <Typography variant="h6" sx={{ fontWeight: 900 }}>
            {finding.title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {finding.message}
          </Typography>
        </Stack>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
              <Stack spacing={0.5}>
                <Typography variant="overline" sx={{ letterSpacing: 1.1 }}>Affected configuration</Typography>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                  {validationStateLabel(finding.affectedItemType)}
                </Typography>
                <Typography variant="body2">{finding.affectedItemName || "—"}</Typography>
                {finding.affectedItemCode ? <Typography variant="caption" color="text.secondary">Code: {finding.affectedItemCode}</Typography> : null}
                {finding.currentValue ? <Typography variant="caption" color="text.secondary">{finding.currentValue}</Typography> : null}
              </Stack>
            </Paper>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
              <Stack spacing={0.5}>
                <Typography variant="overline" sx={{ letterSpacing: 1.1 }}>
                  {finding.expectedItemType ? "Required dependency" : "Expected state"}
                </Typography>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                  {validationStateLabel(finding.expectedItemType)}
                </Typography>
                <Typography variant="body2">{finding.expectedItemName || finding.expectedValue || "—"}</Typography>
                {finding.expectedItemCode ? <Typography variant="caption" color="text.secondary">Code: {finding.expectedItemCode}</Typography> : null}
                {finding.expectedValue ? <Typography variant="caption" color="text.secondary">{finding.expectedValue}</Typography> : null}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
        <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
          <Stack spacing={0.5}>
            <Typography variant="overline" sx={{ letterSpacing: 1.1 }}>Resolution</Typography>
            <Typography variant="body2">{finding.remediation || "Review the builder configuration and correct the selected items."}</Typography>
          </Stack>
        </Paper>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button variant="contained" onClick={() => onNavigateTab((finding.targetBuilderTab || "validation") as WorkspaceTab)}>
            {finding.actionLabel || validationTabLabel(finding.targetBuilderTab)}
          </Button>
          {secondaryTab ? (
            <Button variant="outlined" onClick={() => onNavigateTab(secondaryTab as WorkspaceTab)}>
              {validationTabLabel(secondaryTab)}
            </Button>
          ) : null}
        </Stack>
      </Stack>
    </Paper>
  );
}

function sectionCard(title: string, count: number, children: React.ReactNode) {
  return (
    <Card variant="outlined" sx={{ height: "100%" }}>
      <CardContent>
        <Stack spacing={1.25}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
            <Chip label={`${count}`} size="small" color="primary" variant="outlined" />
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  );
}

function mapSummaryState(detail: CommercialPlanTemplateDetail | null) {
  const draft = detail?.draft;
  return {
    capabilities: draft?.configuration.capabilities.filter((item) => item.selected).map((item) => item.capabilityId) ?? [],
    modules: draft?.configuration.modules.filter((item) => item.selected).map((item) => ({
      moduleId: item.moduleId,
      selectionSource: item.selectionSource,
      inherited: item.inherited,
      displayOrder: item.displayOrder,
    })) ?? [],
    features: draft?.configuration.features.filter((item) => item.selected).map((item) => item.featureId) ?? [],
    limits: draft?.configuration.limits.filter((item) => item.selected).map((item) => ({
      limitDefinitionId: item.limitDefinitionId,
      configuredValue: item.configuredValue || "",
    })) ?? [],
    addons: draft?.configuration.addons.map((item) => ({
      addonId: item.addonId,
      selectionState: item.selectionState,
    })) ?? [],
    pricing: draft?.configuration.pricing || detail?.pricing || emptyPricingState(),
    draftNotes: draft?.draftNotes || "",
  };
}

export function mapDraftResponseState(draft: CommercialPlanTemplateDetail["draft"]): BuilderState {
  return {
    capabilities: draft.configuration.capabilities.filter((item) => item.selected).map((item) => item.capabilityId),
    modules: draft.configuration.modules
      .filter((item) => item.selected)
      .map((item) => ({
        moduleId: item.moduleId,
        selectionSource: item.selectionSource,
        inherited: item.inherited,
        displayOrder: item.displayOrder,
      })),
    features: draft.configuration.features.filter((item) => item.selected).map((item) => item.featureId),
    limits: draft.configuration.limits
      .filter((item) => item.selected)
      .map((item) => ({
        limitDefinitionId: item.limitDefinitionId,
        configuredValue: item.configuredValue || "",
      })),
    addons: draft.configuration.addons.map((item) => ({
      addonId: item.addonId,
      selectionState: item.selectionState,
    })),
    pricing: draft.configuration.pricing || emptyPricingState(),
    draftNotes: draft.draftNotes || "",
  };
}

export function buildDraftSavePayload(nextState: BuilderState) {
  return {
    draftNotes: nextState.draftNotes,
    capabilities: nextState.capabilities.map((capabilityId) => ({ capabilityId })),
    modules: nextState.modules.map((module, index) => ({
      moduleId: module.moduleId,
      selectionSource: module.selectionSource,
      inherited: module.inherited,
      displayOrder: module.displayOrder || index + 1,
    })),
    features: nextState.features.map((featureId) => ({ featureId })),
    limits: nextState.limits.map((limit) => ({
      limitDefinitionId: limit.limitDefinitionId,
      configuredValue: limit.configuredValue,
    })),
    addons: nextState.addons.map((addon) => ({
      addonId: addon.addonId,
      selectionState: addon.selectionState,
    })),
    pricing: nextState.pricing,
  };
}

function applyDraftResponse(detail: CommercialPlanTemplateDetail | null, saved: CommercialPlanTemplateDetail["draft"]) {
  if (!detail) return detail;
  return {
    ...detail,
    draft: saved,
    draftRevision: saved.revision,
    draftStatus: saved.status,
    publicationReady: saved.validation.readyToPublish,
    validation: saved.validation,
  };
}

function selectedModuleIds(modules: ModuleSelection[]) {
  return modules.map((item) => item.moduleId);
}

function selectedLimitIds(limits: LimitSelection[]) {
  return limits.map((item) => item.limitDefinitionId);
}

function selectedAddonIds(addons: AddonSelection[]) {
  return addons.filter((item) => item.selectionState !== "UNAVAILABLE").map((item) => item.addonId);
}

function addonSelectionStateMap(addons: AddonSelection[]) {
  return Object.fromEntries(addons.map((item) => [item.addonId, item.selectionState])) as Record<string, CommercialPlanSelectionState>;
}

function validateTemplateMeta(values: CommercialPlanTemplateFormValue): CommercialPlanTemplateFormErrors {
  const errors: CommercialPlanTemplateFormErrors = {};
  if (!values.name.trim()) errors.name = "Name is required.";
  else if (values.name.trim().length > 128) errors.name = "Name must be 128 characters or fewer.";
  if (!values.code.trim()) errors.code = "Code is required.";
  else if (!/^[A-Z0-9]+(?:_[A-Z0-9]+)*$/.test(values.code.trim())) errors.code = "Use uppercase letters, numbers, and underscores only.";
  else if (values.code.trim().length > 64) errors.code = "Code must be 64 characters or fewer.";
  if (!values.targetSegment) errors.targetSegment = "Target segment is required.";
  const displayOrder = Number.parseInt(values.displayOrder, 10);
  if (values.displayOrder.trim() === "" || Number.isNaN(displayOrder) || displayOrder < 0) errors.displayOrder = "Display order must be a non-negative integer.";
  if (values.description.trim().length > 512) errors.description = "Description must be 512 characters or fewer.";
  return errors;
}

export default function CommercialPlansPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const params = useParams<{ templateId?: string; versionId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const [templates, setTemplates] = React.useState<CommercialPlanTemplateSummary[]>([]);
  const [templateDetail, setTemplateDetail] = React.useState<CommercialPlanTemplateDetail | null>(null);
  const [versions, setVersions] = React.useState<CommercialPlanVersionSummary[]>([]);
  const [versionDetail, setVersionDetail] = React.useState<CommercialPlanVersionDetail | null>(null);
  const [comparison, setComparison] = React.useState<CommercialPlanVersionComparison | null>(null);
  const [capabilities, setCapabilities] = React.useState<CommercialCapabilitySummary[]>([]);
  const [modules, setModules] = React.useState<CommercialModuleSummary[]>([]);
  const [features, setFeatures] = React.useState<CommercialFeatureSummary[]>([]);
  const [limits, setLimits] = React.useState<CommercialLimitDefinitionSummary[]>([]);
  const [addons, setAddons] = React.useState<CommercialAddonSummary[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [templateSaving, setTemplateSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [editor, setEditor] = React.useState<null | "capabilities" | "modules" | "features" | "limits" | "addons">(null);
  const [publishOpen, setPublishOpen] = React.useState(false);
  const [publishNotes, setPublishNotes] = React.useState("");
  const [state, setState] = React.useState(mapSummaryState(null));
  const [templateMeta, setTemplateMeta] = React.useState(defaultTemplateForm());
  const [templateMetaErrors, setTemplateMetaErrors] = React.useState<CommercialPlanTemplateFormErrors>({});
  const [templateMetaDirty, setTemplateMetaDirty] = React.useState(false);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [createSaving, setCreateSaving] = React.useState(false);
  const [createError, setCreateError] = React.useState<string | null>(null);
  const [discardTemplateChangesOpen, setDiscardTemplateChangesOpen] = React.useState(false);

  const tab = parseTab(searchParams.get("tab"));
  const templateId = params.templateId || null;
  const versionId = params.versionId || null;
  const navigateToTab = React.useCallback((nextTab: WorkspaceTab) => {
    setSearchParams({ tab: nextTab }, { replace: true });
  }, [setSearchParams]);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken) return;
      setLoading(true);
      setError(null);
      try {
        const [templatePage, caps, mods, feats, lims, adds] = await Promise.all([
          listCommercialPlanTemplates(auth.accessToken, { size: 50 }),
          listCommercialCapabilities(auth.accessToken, { size: 200 }),
          listCommercialModules(auth.accessToken, { size: 200 }),
          listCommercialFeatures(auth.accessToken, { size: 200 }),
          listCommercialLimits(auth.accessToken, { size: 200 }),
          listCommercialAddons(auth.accessToken, { size: 200 }),
        ]);
        if (cancelled) return;
        setTemplates(templatePage.items);
        setCapabilities(caps.items);
        setModules(mods.items);
        setFeatures(feats.items);
        setLimits(lims.items);
        setAddons(adds.items);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load commercial plans");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken]);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !templateId) {
        setTemplateDetail(null);
        setVersions([]);
        setVersionDetail(null);
        setComparison(null);
        setState(mapSummaryState(null));
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [detail, versionPage, comparisonRes, versionRes] = await Promise.all([
          getCommercialPlanTemplate(auth.accessToken, templateId),
          listCommercialPlanVersions(auth.accessToken, templateId),
          compareCommercialPlanVersions(auth.accessToken, templateId),
          versionId ? getCommercialPlanVersion(auth.accessToken, templateId, versionId) : Promise.resolve(null),
        ]);
        if (cancelled) return;
        setTemplateDetail(detail);
        setTemplateMeta(templateFormFromDetail(detail));
        setTemplateMetaErrors({});
        setTemplateMetaDirty(false);
        setVersions(versionPage.items);
        setComparison(comparisonRes);
        setVersionDetail(versionRes);
        setState(mapSummaryState(detail));
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load plan template");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, templateId, versionId]);

  React.useEffect(() => {
    if (!templateDetail || !templateMetaDirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [templateDetail, templateMetaDirty]);

  function discardTemplateMetaChanges() {
    if (!templateDetail) return;
    setDiscardTemplateChangesOpen(false);
    setTemplateMeta(templateFormFromDetail(templateDetail));
    setTemplateMetaErrors({});
    setTemplateMetaDirty(false);
  }

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  async function saveDraft(nextState: BuilderState = state) {
    if (!auth.accessToken || !templateDetail) return false;
    setSaving(true);
    try {
      const saved = await saveCommercialPlanDraft(auth.accessToken, templateDetail.id, buildDraftSavePayload(nextState));
      setTemplateDetail((current) => applyDraftResponse(current, saved));
      setState(mapDraftResponseState(saved));
      setToast(`Draft saved for ${templateDetail.name}.`);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save plan draft");
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function validateDraft() {
    if (!auth.accessToken || !templateDetail) return;
    setSaving(true);
    try {
      const result = await validateCommercialPlanDraft(auth.accessToken, templateDetail.id);
      setTemplateDetail((current) => applyDraftResponse(current, result.draft));
      setState(mapDraftResponseState(result.draft));
      setToast(result.validation.readyToPublish ? "Draft is ready to publish." : "Draft validation completed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to validate plan draft");
    } finally {
      setSaving(false);
    }
  }

  async function validatePricing() {
    if (!auth.accessToken || !templateDetail) return;
    setSaving(true);
    try {
      const result = await validateCommercialPlanPricing(auth.accessToken, templateDetail.id);
      setTemplateDetail((current) => (current ? { ...current, pricingValidation: result } : current));
      setToast(result.readyToPublish ? "Pricing is ready to publish." : "Pricing validation completed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to validate pricing");
    } finally {
      setSaving(false);
    }
  }

  async function publishDraft() {
    if (!auth.accessToken || !templateDetail) return;
    setSaving(true);
    try {
      const result = await publishCommercialPlanVersion(auth.accessToken, templateDetail.id, { publicationNotes: publishNotes });
      setToast(`Published ${result.versionLabel} for ${templateDetail.name}.`);
      setPublishOpen(false);
      setPublishNotes("");
      navigate(`/platform/commercial/plans/${templateDetail.id}/versions/${result.id}`, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish plan version");
    } finally {
      setSaving(false);
    }
  }

  async function createTemplate(values: CommercialPlanTemplateFormValue) {
    if (!auth.accessToken) return;
    setCreateSaving(true);
    setCreateError(null);
    try {
      const created = await createCommercialPlanTemplate(auth.accessToken, {
        name: values.name,
        code: values.code,
        description: values.description,
        targetSegment: values.targetSegment,
        status: values.status,
        displayOrder: Number.parseInt(values.displayOrder, 10),
      });
      setCreateOpen(false);
      setToast(`Plan template “${created.name}” created.`);
      navigate(`/platform/commercial/plans/${created.id}`, { replace: true });
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : "Failed to create plan template");
    } finally {
      setCreateSaving(false);
    }
  }

  async function cloneTemplate(sourceTemplateId: string, values: CommercialPlanTemplateFormValue) {
    if (!auth.accessToken) return;
    setCreateSaving(true);
    setCreateError(null);
    try {
      const cloned = await cloneCommercialPlanTemplate(auth.accessToken, sourceTemplateId, {
        sourceTemplateId,
        code: values.code,
        name: values.name,
        description: values.description,
        targetSegment: values.targetSegment,
        status: values.status,
        displayOrder: Number.parseInt(values.displayOrder, 10),
      });
      setCreateOpen(false);
      setToast(`Plan template “${cloned.name}” cloned.`);
      navigate(`/platform/commercial/plans/${cloned.id}`, { replace: true });
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : "Failed to clone plan template");
    } finally {
      setCreateSaving(false);
    }
  }

  async function saveTemplateMeta() {
    if (!auth.accessToken || !templateDetail) return;
    const errors = validateTemplateMeta(templateMeta);
    setTemplateMetaErrors(errors);
    const firstInvalid = Object.keys(errors)[0];
    if (firstInvalid) {
      const element = document.querySelector<HTMLElement>(`[name="commercial-plan-${firstInvalid}"]`);
      element?.focus();
      return;
    }
    setTemplateSaving(true);
    try {
      const saved = await updateCommercialPlanTemplate(auth.accessToken, templateDetail.id, {
        name: templateMeta.name.trim(),
        description: templateMeta.description.trim(),
        targetSegment: templateMeta.targetSegment,
        displayOrder: Number.parseInt(templateMeta.displayOrder, 10),
        status: templateMeta.status,
      });
      setTemplateDetail(saved);
      setTemplateMeta(templateFormFromDetail(saved));
      setTemplateMetaErrors({});
      setTemplateMetaDirty(false);
      setToast(`Plan template “${saved.name}” updated.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update plan template");
    } finally {
      setTemplateSaving(false);
    }
  }

  async function retireTemplate() {
    if (!auth.accessToken || !templateDetail) return;
    await retireCommercialPlanTemplate(auth.accessToken, templateDetail.id);
    setToast(`Retired ${templateDetail.name}.`);
  }

  const listMode = !templateId;
  const workflowValidation = templateDetail?.validation;
  const selectedModuleIdList = React.useMemo(() => selectedModuleIds(state.modules), [state.modules]);
  const selectedLimitIdList = React.useMemo(() => selectedLimitIds(state.limits), [state.limits]);
  const selectedAddonIdList = React.useMemo(() => selectedAddonIds(state.addons), [state.addons]);
  const configuredLimitValues = React.useMemo(() => Object.fromEntries(state.limits.map((item) => [item.limitDefinitionId, item.configuredValue])), [state.limits]);
  const configuredAddonSelectionStates = React.useMemo(() => addonSelectionStateMap(state.addons), [state.addons]);
  const configuredAddonCount = selectedAddonIdList.length;
  const pricing = state.pricing;
  const pricingValidation = templateDetail?.pricingValidation || null;
  const pricingSavedSnapshot = templateDetail?.draft.configuration.pricing || null;
  const pricingDirty = JSON.stringify(pricing) !== JSON.stringify(pricingSavedSnapshot);
  const pricingValidationDirty = Boolean(templateDetail && pricingValidation && pricingValidation.validatedDraftRevision !== templateDetail.draftRevision);
  const pricingStatusLabel = pricingDraftStateLabel(pricing, pricingDirty, pricingValidation);
  const pricingValidationLabel = pricingValidationStateLabel(pricingValidation, pricingValidationDirty);
  const pricingCurrentPublishedLabel = templateDetail?.latestPublishedVersionNumber ? `v${templateDetail.latestPublishedVersionNumber}` : "None";
  const pricingRuntimeImpact = "None - legacy runtime remains authoritative";
  const billingCycle = pricing.billingCycle || "MONTHLY";
  const pricingComparisonSummary = pricingComparisonHighlights(comparison?.pricing);
  const pricingValidationGroups = React.useMemo(() => {
    const findings = pricingValidation?.findings || [];
    return findings.reduce<Record<PricingValidationGroupKey, typeof findings>>((groups, finding) => {
      const group = pricingValidationGroup(finding);
      groups[group] = [...(groups[group] || []), finding];
      return groups;
    }, { subscription: [], currency: [], trial: [], tax: [], metered: [], addons: [] });
  }, [pricingValidation]);
  const publishDisabledReason = templateDetail
    ? templateDetail.validation.validationState === "STALE"
      ? `Validation is outdated for draft revision ${templateDetail.validation.validatedDraftRevision}. Run validation again before publishing.`
      : templateDetail.validation.blockingFindingCount > 0
        ? `Publishing is blocked because ${templateDetail.validation.blockingFindingCount} validation issue${templateDetail.validation.blockingFindingCount === 1 ? "" : "s"} remain. Review Validation to resolve them.`
        : pricingValidation?.validationState === "STALE"
          ? `Pricing validation is outdated for draft revision ${pricingValidation.validatedDraftRevision}. Run Validate Pricing again before publishing.`
          : pricingValidation?.blockingFindingCount
            ? `Publishing is blocked because ${pricingValidation.blockingFindingCount} pricing issue${pricingValidation.blockingFindingCount === 1 ? "" : "s"} remain. Review Pricing Validation to resolve them.`
        : templateDetail.validation.validationState === "NOT_VALIDATED"
          ? "Run validation before publishing."
          : null
    : null;
  const workflowSteps = templateDetail ? [
    { key: "summary", label: "Identity", state: templateMetaDirty ? "warning" : "complete" },
    { key: "capabilities", label: "Capabilities", state: state.capabilities.length > 0 ? "complete" : "incomplete" },
    { key: "modules", label: "Modules", state: state.modules.length > 0 ? "complete" : "incomplete" },
    { key: "features", label: "Features", state: state.features.length > 0 ? "complete" : "optional" },
    { key: "limits", label: "Limits", state: state.limits.length > 0 ? "complete" : "optional" },
    { key: "pricing", label: "Pricing", state: pricing.monthlyPrice && pricing.annualPrice ? "complete" : "incomplete" },
    { key: "addons", label: "Add-ons", state: configuredAddonCount > 0 ? "complete" : "optional" },
    { key: "validation", label: "Validate", state: workflowValidation?.blockingFindingCount ? "blocking" : workflowValidation?.validationState === "VALID" ? "complete" : "incomplete" },
    { key: "versions", label: "Publish", state: workflowValidation?.readyToPublish ? "complete" : "incomplete" },
  ] : [];

  function updatePricingState(updater: (current: PricingState) => PricingState) {
    setState((current) => ({ ...current, pricing: updater(current.pricing) }));
  }

  function updateMeteredRate(index: number, next: Partial<CommercialPlanPricingMeteredRate>) {
    updatePricingState((current) => ({
      ...current,
      meteredRates: current.meteredRates.map((rate, rateIndex) => (rateIndex === index ? { ...rate, ...next } : rate)),
    }));
  }

  function addMeteredRate() {
    updatePricingState((current) => ({
      ...current,
      meteredRates: [
        ...current.meteredRates,
        { id: "", limitDefinitionId: "", limitCode: "", limitName: "", includedQuantity: "0", overageEnabled: false, unitPrice: "", unitName: "", billingRounding: "", status: "DRAFT" },
      ],
    }));
  }

  function removeMeteredRate(index: number) {
    updatePricingState((current) => ({ ...current, meteredRates: current.meteredRates.filter((_, rateIndex) => rateIndex !== index) }));
  }

  function updateAddonPricing(index: number, next: Partial<CommercialPlanPricingAddon>) {
    updatePricingState((current) => ({
      ...current,
      addonPricing: current.addonPricing.map((addon, addonIndex) => (addonIndex === index ? { ...addon, ...next } : addon)),
    }));
  }

  function addAddonPricing() {
    updatePricingState((current) => ({
      ...current,
      addonPricing: [
        ...current.addonPricing,
        { id: "", addonOfferId: "", addonCode: "", addonName: "", purchaseType: "MONTHLY", monthlyPrice: "", annualPrice: "", oneTimePrice: "", maxQuantity: null, status: "DRAFT" },
      ],
    }));
  }

  function removeAddonPricing(index: number) {
    updatePricingState((current) => ({ ...current, addonPricing: current.addonPricing.filter((_, addonIndex) => addonIndex !== index) }));
  }

  return (
    <Stack spacing={2.5}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} flexWrap="wrap">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            {templateDetail ? templateDetail.name : "Commercial Plans"}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 820 }}>
            Commercial plan templates and published versions are design-time records only. Publishing does not alter tenant runtime access.
          </Typography>
        </Box>
      <Stack direction="row" spacing={1} flexWrap="wrap">
          {listMode ? <Button variant="contained" onClick={() => { setCreateError(null); setCreateOpen(true); }}>Create Plan Template</Button> : null}
          {templateDetail ? <Button variant="outlined" onClick={() => void saveDraft()} disabled={saving}>Save Draft</Button> : null}
          {templateDetail ? <Button variant="outlined" onClick={() => void validateDraft()} disabled={saving}>Validate</Button> : null}
          {templateDetail ? (
            <Stack spacing={0.5} alignItems="flex-end">
              <Button
                variant="contained"
                onClick={() => setPublishOpen(true)}
                disabled={saving || !templateDetail.validation.readyToPublish}
                title={publishDisabledReason || undefined}
              >
                Publish Version
              </Button>
              {!templateDetail.validation.readyToPublish && publishDisabledReason ? (
                <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 300, textAlign: "right" }}>
                  {publishDisabledReason}
                </Typography>
              ) : null}
            </Stack>
          ) : null}
        </Stack>
      </Stack>

      {templateDetail ? (
        <Paper variant="outlined" sx={{ p: 1.5 }}>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
            <Typography variant="subtitle2" sx={{ fontWeight: 900, mr: 1 }}>
              Commercial Plan Builder
            </Typography>
            {workflowSteps.map((step) => (
              <Chip
                key={step.key}
                label={`${step.label} · ${step.state}`}
                color={tab === step.key ? "primary" : step.state === "complete" ? "success" : step.state === "warning" ? "warning" : step.state === "blocking" ? "error" : "default"}
                variant={tab === step.key ? "filled" : "outlined"}
                onClick={() => navigateToTab(step.key as WorkspaceTab)}
              />
            ))}
          </Stack>
        </Paper>
      ) : null}

      <Alert severity="info" variant="outlined">
        Existing tenant entitlement behavior remains authoritative. Plan templates and published versions do not directly grant access.
      </Alert>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Typography variant="body2" color="text.secondary">Loading commercial plans…</Typography> : null}

      {listMode ? (
        templates.length === 0 ? (
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5} alignItems="flex-start">
                <Typography variant="h6" sx={{ fontWeight: 900 }}>No plan templates yet</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 760 }}>
                  Create your first commercial plan template to define a reusable package of capabilities, modules, features, limits, and add-ons.
                </Typography>
                <Button variant="contained" onClick={() => { setCreateError(null); setCreateOpen(true); }}>
                  Create Plan Template
                </Button>
              </Stack>
            </CardContent>
          </Card>
        ) : (
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Plan Templates</Typography>
                <TableContainer>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Plan</TableCell>
                        <TableCell>Code</TableCell>
                        <TableCell>Target Segment</TableCell>
                        <TableCell>Draft Revision</TableCell>
                        <TableCell>Latest Published</TableCell>
                        <TableCell>Publication Readiness</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {templates.map((row) => (
                        <TableRow key={row.id} hover onClick={() => navigate(`/platform/commercial/plans/${row.id}`)} sx={{ cursor: "pointer" }}>
                          <TableCell>{row.name}</TableCell>
                          <TableCell>{row.code}</TableCell>
                          <TableCell>{row.targetSegment}</TableCell>
                          <TableCell>{row.draftRevision}</TableCell>
                          <TableCell>{row.latestPublishedVersionNumber ?? "—"}</TableCell>
                          <TableCell>{validationLabel(row.validation)}</TableCell>
                          <TableCell>{row.status}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            </CardContent>
          </Card>
        )
      ) : (
        <>
          <Tabs value={tab} onChange={(_, next) => setSearchParams({ tab: next }, { replace: true })} variant="scrollable" scrollButtons="auto">
            <Tab value="summary" label="Summary" />
          <Tab value="capabilities" label="Capabilities" />
          <Tab value="modules" label="Modules" />
          <Tab value="features" label="Features" />
          <Tab value="limits" label="Limits" />
          <Tab value="pricing" label="Pricing" />
          <Tab value="addons" label="Add-ons" />
          <Tab value="validation" label="Validation" />
          <Tab value="versions" label="Versions" />
          <Tab value="compare" label="Compare" />
          </Tabs>

          {templateDetail ? (
            <>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <Typography variant="h5" sx={{ fontWeight: 900 }}>
                  {templateDetail.name}
                </Typography>
                <Chip label={templateDetail.code} variant="outlined" />
                <Chip label={templateDetail.status} color={templateDetail.status === "ACTIVE" ? "success" : templateDetail.status === "RETIRED" ? "default" : "warning"} />
              </Stack>
              {tab === "summary" ? (
                <Stack spacing={2}>
                  <CommercialPlanTemplateSummarySection
                    values={templateMeta}
                    errors={templateMetaErrors}
                    onChange={(next) => {
                      setTemplateMeta(next);
                      setTemplateMetaDirty(true);
                    }}
                    onSave={() => void saveTemplateMeta()}
                    onDiscard={() => setDiscardTemplateChangesOpen(true)}
                    discardDisabled={!templateMetaDirty}
                    saving={templateSaving}
                    dirty={templateMetaDirty}
                    publicationReady={templateDetail.publicationReady}
                    validation={templateDetail.validation}
                    draftRevision={templateDetail.draftRevision}
                    latestPublishedVersionNumber={templateDetail.latestPublishedVersionNumber}
                    updatedAt={templateDetail.updatedAt}
                    updatedBy={templateDetail.draft.updatedBy ? "platform admin" : null}
                    capabilityCount={state.capabilities.length}
                    moduleCount={state.modules.length}
                    featureCount={state.features.length}
                    limitCount={state.limits.length}
                    addonCount={configuredAddonCount}
                  />
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>{sectionCard("Capabilities", state.capabilities.length, previewChips(capabilities.filter((item) => state.capabilities.includes(item.id))))}</Grid>
                    <Grid size={{ xs: 12, md: 6 }}>{sectionCard("Modules", state.modules.length, previewChips(modules.filter((item) => selectedModuleIdList.includes(item.id))))}</Grid>
                    <Grid size={{ xs: 12, md: 6 }}>{sectionCard("Features", state.features.length, previewChips(features.filter((item) => state.features.includes(item.id))))}</Grid>
                    <Grid size={{ xs: 12, md: 6 }}>{sectionCard("Limits", state.limits.length, previewChips(limits.filter((item) => selectedLimitIdList.includes(item.id))))}</Grid>
                    <Grid size={{ xs: 12, md: 6 }}>{sectionCard("Add-ons", configuredAddonCount, previewChips(addons.filter((item) => selectedAddonIdList.includes(item.id))))}</Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      {sectionCard("Validation", templateDetail.validation.blockingFindingCount, (
                        <Stack spacing={0.75}>
                          {templateDetail.validation.findings.slice(0, 3).map((message) => (
                            <Alert key={`${message.code}-${message.field}`} severity={message.blocking ? "error" : "warning"} variant="outlined">
                              {message.message}
                            </Alert>
                          ))}
                        </Stack>
                      ))}
                    </Grid>
                  </Grid>
                </Stack>
              ) : null}

              {tab === "pricing" ? (
                <CommercialPricingWorkspace
                  templateName={templateDetail.name}
                  templateCode={templateDetail.code}
                  pricing={pricing}
                  pricingMeta={templateDetail.pricing}
                  pricingValidation={pricingValidation}
                  pricingComparison={comparison?.pricing || null}
                  savedPricingSnapshot={pricingSavedSnapshot}
                  pricingDraftRevision={templateDetail.draftRevision}
                  latestPublishedVersionNumber={templateDetail.latestPublishedVersionNumber}
                  templateUpdatedAt={templateDetail.updatedAt}
                  publishDisabledReason={publishDisabledReason}
                  saving={saving}
                  limits={limits}
                  addons={addons}
                  onUpdatePricing={updatePricingState}
                  onSaveDraft={saveDraft}
                  onValidatePricing={validatePricing}
                  onPublishPlan={() => setPublishOpen(true)}
                  onComparePricing={() => navigateToTab("compare")}
                />
              ) : null}

              {(["capabilities", "modules", "features", "limits", "addons"] as const).includes(tab as any) ? (
                <Stack spacing={2}>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.25}>
                          <Typography variant="h6" sx={{ fontWeight: 900 }}>Builder Notes</Typography>
                          <TextField multiline minRows={3} value={state.draftNotes} onChange={(event) => setState((current) => ({ ...current, draftNotes: event.target.value }))} />
                        </Stack>
                      </Paper>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1.25}>
                          <Typography variant="h6" sx={{ fontWeight: 900 }}>Publication Readiness</Typography>
                          <Typography variant="body2" color="text.secondary">
                            Draft revision {templateDetail.draftRevision} · {validationLabel(templateDetail.validation)}
                          </Typography>
                          {!templateDetail.validation.readyToPublish ? (
                            <Alert severity={templateDetail.validation.validationState === "STALE" ? "warning" : "info"} variant="outlined">
                              {templateDetail.validation.validationState === "STALE"
                                ? `Validation is outdated for draft revision ${templateDetail.validation.validatedDraftRevision}. Run validation again for the latest draft.`
                                : `${templateDetail.validation.blockingFindingCount} blocking issue${templateDetail.validation.blockingFindingCount === 1 ? "" : "s"} must be resolved before publishing.`}
                            </Alert>
                          ) : null}
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Button variant="outlined" onClick={() => void validateDraft()} disabled={saving}>
                              Run Validation
                            </Button>
                            <Button variant="text" onClick={() => navigateToTab("validation")}>
                              Review Validation
                            </Button>
                          </Stack>
                        </Stack>
                      </Paper>
                    </Grid>
                  </Grid>

                  {(
                    [
                      { key: "capabilities", title: "Capabilities", items: capabilities.filter((item) => state.capabilities.includes(item.id)) },
                      { key: "modules", title: "Modules", items: modules.filter((item) => selectedModuleIdList.includes(item.id)) },
                      { key: "features", title: "Features", items: features.filter((item) => state.features.includes(item.id)) },
                      { key: "limits", title: "Limits", items: limits.filter((item) => selectedLimitIdList.includes(item.id)) },
                      { key: "addons", title: "Add-ons", items: addons.filter((item) => selectedAddonIdList.includes(item.id)) },
                    ] as const
                  ).map((section) => (
                    <Paper key={section.key} variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={1}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                          <Typography variant="h6" sx={{ fontWeight: 900 }}>{section.title}</Typography>
                          <Button onClick={() => setEditor(section.key as any)}>{`Edit ${section.title}`}</Button>
                        </Stack>
                        {previewChips(section.items as Array<{ name: string; code: string }>)}
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              ) : null}

              {tab === "validation" ? (
                <Stack spacing={2}>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1.25}>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Validation Workspace</Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={validationLabel(templateDetail.validation)} color={validationStatusTone(templateDetail.validation)} variant={templateDetail.validation.validationState === "VALID" ? "filled" : "outlined"} />
                        <Chip label={`Validated draft revision ${templateDetail.validation.validatedDraftRevision}`} variant="outlined" />
                        <Chip label={templateDetail.validation.validatedAt ? formatCommercialDateTime(templateDetail.validation.validatedAt) : "Not validated"} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {templateDetail.validation.validationState === "VALID"
                          ? "Ready to publish"
                          : templateDetail.validation.validationState === "STALE"
                            ? "Validation is outdated"
                            : "Configuration incomplete"}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {templateDetail.validation.blockingFindingCount} blocking issue{templateDetail.validation.blockingFindingCount === 1 ? "" : "s"} · {templateDetail.validation.warningFindingCount} warning{templateDetail.validation.warningFindingCount === 1 ? "" : "s"}
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        {Object.entries(templateDetail.validation.findings.reduce<Record<string, number>>((acc, finding) => {
                          const label = validationCategoryLabel(finding.category);
                          acc[label] = (acc[label] || 0) + 1;
                          return acc;
                        }, {})).map(([label, count]) => (
                          <Chip key={label} size="small" label={`${label}: ${count}`} variant="outlined" />
                        ))}
                      </Stack>
                      {!templateDetail.validation.readyToPublish ? (
                        <Alert severity={templateDetail.validation.validationState === "STALE" ? "warning" : "info"} variant="outlined">
                          {templateDetail.validation.validationState === "STALE"
                            ? `Validation is outdated for draft revision ${templateDetail.validation.validatedDraftRevision}.`
                            : `${templateDetail.validation.blockingFindingCount} blocking issue${templateDetail.validation.blockingFindingCount === 1 ? "" : "s"} remain before publishing.`}
                        </Alert>
                      ) : null}
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Button variant="contained" onClick={() => void validateDraft()} disabled={saving}>
                          Run Validation
                        </Button>
                        <Button variant="outlined" onClick={() => navigateToTab("capabilities")}>
                          Review Builder
                        </Button>
                      </Stack>
                    </Stack>
                  </Paper>
                  {templateDetail.validation.findings.map((message) => (
                    <ValidationFindingCard key={`${message.code}-${message.field}-${message.affectedItemCode || message.affectedItemName || "item"}`} finding={message} onNavigateTab={navigateToTab} />
                  ))}
                </Stack>
              ) : null}

              {tab === "versions" ? (
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>Version History</Typography>
                      <TableContainer>
                        <Table size="small" stickyHeader>
                          <TableHead>
                              <TableRow>
                                <TableCell>Version</TableCell>
                                <TableCell>Status</TableCell>
                                <TableCell>Published</TableCell>
                                <TableCell>Published By</TableCell>
                                <TableCell>Notes</TableCell>
                                <TableCell>Change Summary</TableCell>
                                <TableCell>Hash</TableCell>
                                <TableCell>Actions</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                            {versions.map((version) => (
                              <TableRow key={version.id} hover>
                                <TableCell>{version.versionLabel}</TableCell>
                                <TableCell>{version.status}</TableCell>
                                <TableCell>{formatCommercialDateTime(version.publishedAt)}</TableCell>
                                <TableCell>{version.publishedBy ? "platform admin" : "—"}</TableCell>
                                <TableCell>{version.publicationNotes || "—"}</TableCell>
                                <TableCell>{version.changeSummary || "Initial version"}</TableCell>
                                <TableCell>{version.contentHash.slice(0, 12)}</TableCell>
                                <TableCell>
                                  <Button size="small" onClick={() => navigate(`/platform/commercial/plans/${templateDetail.id}/versions/${version.id}`)}>View</Button>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              {tab === "compare" ? (
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    <Paper variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={1}>
                        <Typography variant="h6" sx={{ fontWeight: 900 }}>Metadata Changes</Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {(comparison?.metadata.changed || []).length === 0 ? <Chip label="No metadata changes" size="small" /> : null}
                          {(comparison?.metadata.changed || []).map((item) => <Chip key={`m-${item.code}`} label={`${item.code}: ${item.detail}`} size="small" />)}
                        </Stack>
                      </Stack>
                    </Paper>
                  </Grid>
                  {comparison ? ([
                    ["Capabilities", comparison.capabilities],
                    ["Modules", comparison.modules],
                    ["Features", comparison.features],
                    ["Limits", comparison.limits],
                    ["Add-ons", comparison.addons],
                    ["Pricing", {
                      added: comparison.pricing.subscriptionPricing.filter((item) => item.detail?.startsWith("Added") || item.detail?.includes("->") ? false : true),
                      removed: [],
                      changed: [
                        ...comparison.pricing.subscriptionPricing,
                        ...comparison.pricing.meteredRates,
                        ...comparison.pricing.addonPricing,
                      ],
                    }],
                  ] as const).map(([title, section]) => (
                    <Grid key={title} size={{ xs: 12, md: 6 }}>
                      <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack spacing={1}>
                          <Typography variant="h6" sx={{ fontWeight: 900 }}>{title}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            Added {section.added.length} · Removed {section.removed.length} · Changed {section.changed.length}
                          </Typography>
                          {section.added.slice(0, 3).map((item) => <Chip key={`a-${item.code}`} label={`+ ${item.name}`} size="small" sx={{ mr: 0.75, mb: 0.75 }} />)}
                          {section.removed.slice(0, 3).map((item) => <Chip key={`r-${item.code}`} label={`- ${item.name}`} size="small" sx={{ mr: 0.75, mb: 0.75 }} />)}
                        </Stack>
                      </Paper>
                    </Grid>
                  )) : null}
                </Grid>
              ) : null}

              {versionDetail ? (
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={1}>
                    <Typography variant="h6" sx={{ fontWeight: 900 }}>Published Version</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {versionDetail.versionLabel} · {versionDetail.contentHash.slice(0, 12)} · {versionDetail.status}
                    </Typography>
                    <Box component="pre" sx={{ m: 0, p: 2, overflow: "auto", bgcolor: "action.hover", borderRadius: 1 }}>
                      {versionDetail.snapshotJson}
                    </Box>
                  </Stack>
                </Paper>
              ) : null}
            </>
          ) : null}
        </>
      )}

      <CommercialPlanSelectionDialog
        open={editor === "capabilities"}
        kind="capabilities"
        title="Edit Capabilities"
        parentName={templateDetail?.name || "Commercial Plan"}
        parentCode={templateDetail?.code || "—"}
        items={capabilities.map((item) => ({ id: item.id, code: item.code, name: item.name, description: item.description, displayOrder: item.displayOrder, status: item.status }))}
        selectedIds={state.capabilities}
        onClose={() => setEditor(null)}
        onSave={async (ids) => saveDraft({ ...state, capabilities: ids })}
      />
      <CommercialPlanSelectionDialog
        open={editor === "modules"}
        kind="modules"
        title="Edit Modules"
        parentName={templateDetail?.name || "Commercial Plan"}
        parentCode={templateDetail?.code || "—"}
        items={modules.map((item) => ({ id: item.id, code: item.code, name: item.name, description: item.description, displayOrder: item.displayOrder, runtimeModuleCode: item.runtimeModuleCode, status: item.status }))}
        selectedIds={selectedModuleIdList}
        onClose={() => setEditor(null)}
        onSave={async (ids) => saveDraft({
          ...state,
          modules: ids.map((moduleId, index) => {
            const existing = state.modules.find((item) => item.moduleId === moduleId);
            return existing || { moduleId, selectionSource: "EXPLICIT", inherited: false, displayOrder: index + 1 };
          }),
        })}
      />
      <CommercialPlanSelectionDialog
        open={editor === "features"}
        kind="features"
        title="Edit Features"
        parentName={templateDetail?.name || "Commercial Plan"}
        parentCode={templateDetail?.code || "—"}
        items={features.map((item) => ({ id: item.id, code: item.code, name: item.name, description: item.description, displayOrder: item.displayOrder, moduleCode: item.moduleCode, moduleName: item.moduleName, status: item.status }))}
        selectedIds={state.features}
        onClose={() => setEditor(null)}
        onSave={async (ids) => saveDraft({ ...state, features: ids })}
      />
      <CommercialPlanSelectionDialog
        open={editor === "limits"}
        kind="limits"
        title="Edit Limits"
        parentName={templateDetail?.name || "Commercial Plan"}
        parentCode={templateDetail?.code || "—"}
        items={limits.map((item) => ({ id: item.id, code: item.code, name: item.name, description: item.description, displayOrder: item.displayOrder, unit: item.unit, valueType: item.valueType, aggregationPeriod: item.aggregationPeriod, enforcementMode: item.enforcementMode, status: item.status }))}
        selectedIds={selectedLimitIdList}
        limitValues={configuredLimitValues}
        onClose={() => setEditor(null)}
        onSave={async (ids, values) => saveDraft({
          ...state,
          limits: ids.map((limitDefinitionId) => ({
            limitDefinitionId,
            configuredValue: values?.[limitDefinitionId] ?? "",
          })),
        })}
      />
      <CommercialPlanSelectionDialog
        open={editor === "addons"}
        kind="addons"
        title="Edit Add-ons"
        parentName={templateDetail?.name || "Commercial Plan"}
        parentCode={templateDetail?.code || "—"}
        items={addons.map((item) => ({ id: item.id, code: item.code, name: item.name, description: item.description, displayOrder: item.displayOrder, addonType: item.addonType, status: item.status }))}
        selectedIds={selectedAddonIdList}
        addonSelectionStates={configuredAddonSelectionStates}
        onClose={() => setEditor(null)}
        onSave={async (ids, _values, addonStates) => {
          const states = addonStates || addonSelectionStateMap(state.addons);
          return saveDraft({
            ...state,
            addons: Array.from(new Set([...ids, ...Object.keys(states)])).map((addonId) => ({
              addonId,
              selectionState: states[addonId] || "UNAVAILABLE",
            })),
          });
        }}
      />

      <Dialog open={publishOpen} onClose={() => setPublishOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: 900 }}>Publish Version</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Publishing creates an immutable commercial plan version. Tenant runtime access remains unchanged.
            </Typography>
            <TextField label="Publication notes" value={publishNotes} onChange={(event) => setPublishNotes(event.target.value)} multiline minRows={3} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void publishDraft()} disabled={saving}>Publish Version</Button>
        </DialogActions>
      </Dialog>

      <CommercialPlanTemplateCreateDialog
        open={createOpen}
        submitting={createSaving}
        error={createError}
        sourceTemplates={templates.map((item) => ({ id: item.id, name: item.name, code: item.code, description: item.description, targetSegment: item.targetSegment, displayOrder: item.displayOrder }))}
        onClose={() => setCreateOpen(false)}
        onCreate={async (values) => {
          await createTemplate(values);
        }}
        onClone={async (sourceTemplateId, values) => {
          await cloneTemplate(sourceTemplateId, values);
        }}
      />

      <Dialog open={discardTemplateChangesOpen} onClose={() => setDiscardTemplateChangesOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Discard template changes?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            The template details you edited have not been saved.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDiscardTemplateChangesOpen(false)}>Keep Editing</Button>
          <Button variant="contained" color="warning" onClick={discardTemplateMetaChanges}>
            Discard Changes
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={Boolean(toast)}
        autoHideDuration={3500}
        onClose={() => setToast(null)}
        message={toast || ""}
      />
    </Stack>
  );
}
