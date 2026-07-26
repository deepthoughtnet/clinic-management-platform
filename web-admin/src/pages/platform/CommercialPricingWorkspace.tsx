import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";

import type {
  CommercialAddonSummary,
  CommercialLimitDefinitionSummary,
  CommercialPlanAddonPurchaseType,
  CommercialPlanPricingBillingCycle,
  CommercialPlanPricingComparison,
  CommercialPlanPricingResponse,
  CommercialPlanPricingSnapshot,
  CommercialPlanPricingTaxModel,
  CommercialPlanPricingValidationResult,
  CommercialPlanValidationFinding,
} from "../../api/clinicApi";

type Props = {
  templateName: string;
  templateCode: string;
  pricing: CommercialPlanPricingSnapshot;
  pricingMeta: CommercialPlanPricingResponse | null;
  pricingValidation: CommercialPlanPricingValidationResult | null;
  pricingComparison: CommercialPlanPricingComparison | null;
  savedPricingSnapshot: CommercialPlanPricingSnapshot | null;
  pricingDraftRevision: number;
  latestPublishedVersionNumber: number | null;
  templateUpdatedAt: string;
  publishDisabledReason: string | null;
  saving: boolean;
  limits: CommercialLimitDefinitionSummary[];
  addons: CommercialAddonSummary[];
  onUpdatePricing: (updater: (current: CommercialPlanPricingSnapshot) => CommercialPlanPricingSnapshot) => void;
  onSaveDraft: () => Promise<unknown>;
  onValidatePricing: () => Promise<void>;
  onPublishPlan: () => void;
  onComparePricing: () => void;
};

const BILLING_MODES: Array<{ value: CommercialPlanPricingBillingCycle; label: string; description: string }> = [
  { value: "MONTHLY", label: "Monthly Subscription", description: "Billed every month." },
  { value: "ANNUAL", label: "Annual Subscription", description: "Billed once per year." },
  { value: "QUARTERLY", label: "Quarterly Subscription", description: "Billed every three months." },
  { value: "ONE_TIME", label: "One-time Purchase", description: "A single non-recurring charge." },
  { value: "TRIAL", label: "Trial-only Plan", description: "Trial period only." },
];

function currencySymbol(currency: string | null | undefined) {
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
  if (!trimmed) return "";
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

function moneyAdornment(currency: string | null | undefined) {
  return <InputAdornment position="start">{currencySymbol(currency)}</InputAdornment>;
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
      return "Tax will be added to the listed price.";
    case "INCLUSIVE":
      return "Displayed prices already include tax.";
    case "NONE":
    default:
      return "No tax applied.";
  }
}

function billingModeLabel(value: CommercialPlanPricingBillingCycle | null | undefined) {
  return BILLING_MODES.find((option) => option.value === value)?.label || "Pricing not set";
}

function billingModeDescription(value: CommercialPlanPricingBillingCycle | null | undefined) {
  return BILLING_MODES.find((option) => option.value === value)?.description || "Choose how this plan is billed.";
}

function comparisonHighlights(comparison: CommercialPlanPricingComparison | null | undefined) {
  if (!comparison) return [];
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
  if ((comparison.meteredRates || []).length > 0) labels.add("Metered usage changed");
  if ((comparison.addonPricing || []).length > 0) labels.add("Add-on pricing changed");
  return Array.from(labels);
}

type PricingGroupKey = "subscription" | "currency" | "trial" | "tax" | "metered" | "addons";

function pricingGroup(finding: CommercialPlanValidationFinding): PricingGroupKey {
  const field = finding.field || "";
  if (field.includes("meteredRates") || finding.targetBuilderTab === "limits") return "metered";
  if (field.includes("addonPricing") || finding.targetBuilderTab === "addons" || finding.category === "ADDON_COMPATIBILITY") return "addons";
  if (field.includes("currency")) return "currency";
  if (field.includes("trial")) return "trial";
  if (field.includes("tax")) return "tax";
  return "subscription";
}

function sectionAnchor(section: PricingGroupKey) {
  return `pricing-${section}`;
}

function formatCommercialDateTime(value: string | null | undefined) {
  if (!value) return "Never";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Never";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function priceFieldLabel(purchaseType: CommercialPlanAddonPurchaseType) {
  switch (purchaseType) {
    case "MONTHLY":
      return "Monthly Price";
    case "ANNUAL":
      return "Annual Price";
    case "ONE_TIME":
      return "One-time Price";
    default:
      return "Price";
  }
}

function addonPurchaseTypeLabel(value: CommercialPlanAddonPurchaseType) {
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

export default function CommercialPricingWorkspace({
  templateName,
  templateCode,
  pricing,
  pricingMeta,
  pricingValidation,
  pricingComparison,
  savedPricingSnapshot,
  pricingDraftRevision,
  latestPublishedVersionNumber,
  templateUpdatedAt,
  publishDisabledReason,
  saving,
  limits,
  addons,
  onUpdatePricing,
  onSaveDraft,
  onValidatePricing,
  onPublishPlan,
  onComparePricing,
}: Props) {
  const pricingDirty = JSON.stringify(pricing) !== JSON.stringify(savedPricingSnapshot);
  const pricingValidationDirty = Boolean(pricingValidation && pricingValidation.validatedDraftRevision !== pricingDraftRevision);
  const billingCycle = pricing.billingCycle || "MONTHLY";
  const validationFindings = pricingValidation?.findings || [];
  const comparisonLabels = comparisonHighlights(pricingComparison);
  const validationGroups = React.useMemo(() => {
    return validationFindings.reduce<Record<PricingGroupKey, CommercialPlanValidationFinding[]>>((groups, finding) => {
      const key = pricingGroup(finding);
      groups[key] = [...(groups[key] || []), finding];
      return groups;
    }, { subscription: [], currency: [], trial: [], tax: [], metered: [], addons: [] });
  }, [validationFindings]);
  const trialEnabled = pricing.trialDays != null;
  const showMonthlyPrice = billingCycle === "MONTHLY" || billingCycle === "QUARTERLY";
  const showAnnualPrice = billingCycle === "ANNUAL" || billingCycle === "QUARTERLY";
  const showTrialDays = billingCycle === "TRIAL" || trialEnabled;
  const showSetupFee = billingCycle === "ONE_TIME" || billingCycle === "TRIAL" || pricing.setupFee != null;
  const retainedPriceSummary = React.useMemo(() => {
    const retained: string[] = [];
    if (!showMonthlyPrice && pricing.monthlyPrice) {
      retained.push(`Monthly ${pricing.monthlyPrice}`);
    }
    if (!showAnnualPrice && pricing.annualPrice) {
      retained.push(`Annual ${pricing.annualPrice}`);
    }
    return retained;
  }, [pricing.monthlyPrice, pricing.annualPrice, showMonthlyPrice, showAnnualPrice]);

  const updatePricing = (updater: (current: CommercialPlanPricingSnapshot) => CommercialPlanPricingSnapshot) => {
    onUpdatePricing(updater);
  };

  const updateMeteredRate = (index: number, next: Partial<CommercialPlanPricingSnapshot["meteredRates"][number]>) => {
    updatePricing((current) => ({
      ...current,
      meteredRates: current.meteredRates.map((rate, rateIndex) => (rateIndex === index ? { ...rate, ...next } : rate)),
    }));
  };

  const addMeteredRate = () => {
    updatePricing((current) => ({
      ...current,
      meteredRates: [
        ...current.meteredRates,
        { id: "", limitDefinitionId: "", limitCode: "", limitName: "", includedQuantity: "", overageEnabled: false, unitPrice: "", unitName: "", billingRounding: "", status: "DRAFT" },
      ],
    }));
  };

  const removeMeteredRate = (index: number) => {
    updatePricing((current) => ({ ...current, meteredRates: current.meteredRates.filter((_, rateIndex) => rateIndex !== index) }));
  };

  const updateAddonPricing = (index: number, next: Partial<CommercialPlanPricingSnapshot["addonPricing"][number]>) => {
    updatePricing((current) => ({
      ...current,
      addonPricing: current.addonPricing.map((addon, addonIndex) => (addonIndex === index ? { ...addon, ...next } : addon)),
    }));
  };

  const addAddonPricing = () => {
    updatePricing((current) => ({
      ...current,
      addonPricing: [
        ...current.addonPricing,
        { id: "", addonOfferId: "", addonCode: "", addonName: "", purchaseType: "MONTHLY", monthlyPrice: "", annualPrice: "", oneTimePrice: "", maxQuantity: null, status: "DRAFT" },
      ],
    }));
  };

  const removeAddonPricing = (index: number) => {
    updatePricing((current) => ({ ...current, addonPricing: current.addonPricing.filter((_, addonIndex) => addonIndex !== index) }));
  };

  return (
    <Stack spacing={2.5}>
      <Paper
        variant="outlined"
        sx={{
          p: 2,
          position: "sticky",
          top: 16,
          zIndex: 2,
          backgroundColor: "background.paper",
        }}
      >
        <Grid container spacing={2} alignItems="flex-start">
          <Grid size={{ xs: 12, md: 8 }}>
            <Stack spacing={1}>
              <Typography variant="overline" sx={{ letterSpacing: 1.1 }}>
                Pricing Status & Actions
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 900 }}>
                {pricingDirty ? "Unsaved draft pricing" : "Draft pricing ready to review"}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                You are editing draft pricing for {templateName} ({templateCode}). Published pricing snapshots remain immutable.
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`Draft Pricing · ${pricingDirty ? "Unsaved Changes" : pricingValidation?.validationState === "VALID" && pricingValidation.readyToPublish ? "Configured" : "Incomplete"}`} color={pricingDirty ? "warning" : "default"} variant="outlined" />
                <Chip label={`Latest Published Version · ${latestPublishedVersionNumber ? `v${latestPublishedVersionNumber}` : "None"}`} variant="outlined" />
                <Chip label={`Pricing Validation · ${pricingValidation?.blockingFindingCount ? "Blocking Issues" : pricingValidation?.warningFindingCount ? "Warnings" : "Ready"}`} color={pricingValidation?.blockingFindingCount ? "error" : pricingValidation?.warningFindingCount ? "warning" : "success"} variant="outlined" />
                <Chip label={`Runtime Impact · None`} variant="outlined" />
                <Chip label={`Last Saved · ${formatCommercialDateTime(pricingMeta?.createdAt || templateUpdatedAt)}`} variant="outlined" />
              </Stack>
            </Stack>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={1} alignItems={{ md: "flex-end" }}>
              <Button variant="outlined" onClick={() => void onSaveDraft()} disabled={saving}>
                {saving ? "Saving..." : "Save Draft"}
              </Button>
              <Button variant="outlined" onClick={() => void onValidatePricing()} disabled={saving}>
                Validate Pricing
              </Button>
              <Button variant="text" onClick={onComparePricing} disabled={comparisonLabels.length === 0}>
                Compare with latest published version
              </Button>
              <Button
                variant="contained"
                onClick={onPublishPlan}
                disabled={saving || Boolean(publishDisabledReason)}
                title={publishDisabledReason || undefined}
              >
                Publish Plan
              </Button>
            </Stack>
          </Grid>
        </Grid>
        {publishDisabledReason ? (
          <Alert severity="info" variant="outlined" sx={{ mt: 2 }}>
            {publishDisabledReason}
          </Alert>
        ) : null}
      </Paper>

      <Alert severity="info" variant="outlined">
        Editable draft pricing is shown here. If you publish the plan, the pricing snapshot becomes immutable and tenant runtime remains unchanged.
      </Alert>

      {comparisonLabels.length > 0 ? (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
              Compare with latest published version
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {comparisonLabels.slice(0, 6).map((label) => (
                <Chip key={label} label={label} size="small" variant="outlined" />
              ))}
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Use Compare for the full change set. This summary highlights the pricing differences that matter for publishing.
            </Typography>
          </Stack>
        </Paper>
      ) : null}

      <Paper variant="outlined" sx={{ p: 2 }} data-pricing-section={sectionAnchor("subscription")}>
        <Stack spacing={2}>
          <Stack spacing={0.5}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>Subscription Pricing</Typography>
            <Typography variant="body2" color="text.secondary">
              Billing model, currency, recurring amounts, trial, setup fee, tax, and discount policy.
            </Typography>
          </Stack>

          <Box>
            <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Billing Model</Typography>
            <ToggleButtonGroup
              exclusive
              value={billingCycle}
              onChange={(_, next: CommercialPlanPricingBillingCycle | null) => {
                if (!next) return;
                updatePricing((current) => ({ ...current, billingCycle: next }));
              }}
              sx={{ flexWrap: "wrap", gap: 1 }}
            >
              {BILLING_MODES.map((option) => (
                <ToggleButton key={option.value} value={option.value} sx={{ alignItems: "flex-start", textTransform: "none", minWidth: 180, px: 2, py: 1.25 }}>
                  <Stack spacing={0.25} alignItems="flex-start">
                    <Typography variant="body2" sx={{ fontWeight: 800 }}>{option.label}</Typography>
                    <Typography variant="caption" color="text.secondary" align="left">{option.description}</Typography>
                  </Stack>
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.75 }}>
              {billingModeDescription(billingCycle)}
            </Typography>
          </Box>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                fullWidth
                label="Currency"
                value={pricing.currency || "INR"}
                onChange={(event) => updatePricing((current) => ({ ...current, currency: event.target.value }))}
                helperText="Choose the billing currency."
              >
                {["INR", "USD", "EUR"].map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Paper variant="outlined" sx={{ p: 1.5, backgroundColor: "background.default" }}>
                <Stack spacing={0.5}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Active pricing profile</Typography>
                  <Typography variant="body2">{billingModeLabel(billingCycle)}</Typography>
                  <Typography variant="caption" color="text.secondary">Monthly and annual amounts remain available for pricing comparison and publishing.</Typography>
                </Stack>
              </Paper>
            </Grid>
            {showMonthlyPrice ? (
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Monthly Price"
                  value={pricing.monthlyPrice || ""}
                  onChange={(event) => updatePricing((current) => ({ ...current, monthlyPrice: normalizeMoneyText(event.target.value) }))}
                  inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                  InputProps={{ startAdornment: moneyAdornment(pricing.currency) }}
                  helperText="Primary recurring price."
                />
              </Grid>
            ) : null}
            {showAnnualPrice ? (
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Annual Price"
                  value={pricing.annualPrice || ""}
                  onChange={(event) => updatePricing((current) => ({ ...current, annualPrice: normalizeMoneyText(event.target.value) }))}
                  inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                  InputProps={{ startAdornment: moneyAdornment(pricing.currency) }}
                  helperText="Primary recurring price."
                />
              </Grid>
            ) : null}
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={1}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={trialEnabled}
                      onChange={(_, checked) => updatePricing((current) => ({ ...current, trialDays: checked ? (current.trialDays ?? 14) : null }))}
                    />
                  }
                  label="Enable Trial"
                />
                {showTrialDays ? (
                  <TextField
                    fullWidth
                    label="Trial Days"
                    type="text"
                    value={pricing.trialDays ?? ""}
                    onChange={(event) => updatePricing((current) => {
                      const value = event.target.value.trim();
                      const next = value === "" ? null : Number.parseInt(value, 10);
                      return { ...current, trialDays: Number.isNaN(next as number) ? current.trialDays : next };
                    })}
                    inputProps={{ inputMode: "numeric", min: 1, max: 365, step: 1 }}
                    helperText="Number of days customers can use this plan before billing starts."
                  />
                ) : (
                  <Typography variant="body2" color="text.secondary">No trial configured.</Typography>
                )}
              </Stack>
            </Grid>
            {showSetupFee ? (
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Setup Fee"
                  value={pricing.setupFee || ""}
                  onChange={(event) => updatePricing((current) => ({ ...current, setupFee: normalizeMoneyText(event.target.value) || null }))}
                  inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                  InputProps={{ startAdornment: moneyAdornment(pricing.currency) }}
                  helperText="Optional one-time setup charge."
                />
              </Grid>
            ) : null}
            {retainedPriceSummary.length > 0 ? (
              <Grid size={{ xs: 12 }}>
                <Paper variant="outlined" sx={{ p: 1.5, backgroundColor: "background.default" }}>
                  <Stack spacing={0.5}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Retained draft values</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {retainedPriceSummary.join(" · ")} remain in the draft for comparison and publishing.
                    </Typography>
                  </Stack>
                </Paper>
              </Grid>
            ) : null}
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                fullWidth
                label="Tax Model"
                value={pricing.taxModel || "NONE"}
                onChange={(event) => updatePricing((current) => ({ ...current, taxModel: event.target.value as CommercialPlanPricingTaxModel }))}
                helperText={taxModelDescription(pricing.taxModel)}
              >
                {["NONE", "INCLUSIVE", "EXCLUSIVE"].map((option) => (
                  <MenuItem key={option} value={option}>
                    {taxModelLabel(option as CommercialPlanPricingTaxModel)}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              {pricing.taxModel === "NONE" ? (
                <Paper variant="outlined" sx={{ p: 1.5, backgroundColor: "background.default" }}>
                  <Typography variant="body2" color="text.secondary">No tax applied.</Typography>
                </Paper>
              ) : (
                <TextField
                  fullWidth
                  label="Tax Percentage"
                  value={pricing.taxPercentage || ""}
                  onChange={(event) => updatePricing((current) => ({ ...current, taxPercentage: normalizeMoneyText(event.target.value) || null }))}
                  inputProps={{ inputMode: "decimal", min: 0, max: 100, step: 0.1 }}
                  InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                  helperText={pricing.taxModel === "INCLUSIVE" ? "Displayed prices already include tax." : "Tax will be added to the listed price."}
                />
              )}
            </Grid>
            <Grid size={{ xs: 12 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={pricing.discountAllowed}
                    onChange={(event) => updatePricing((current) => ({ ...current, discountAllowed: event.target.checked }))}
                  />
                }
                label="Allow Discounts"
              />
              <Typography variant="caption" color="text.secondary" display="block">
                Allows authorized commercial operators to apply approved discounts during subscription or billing workflows.
              </Typography>
            </Grid>
          </Grid>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }} data-pricing-section={sectionAnchor("metered")}>
        <Stack spacing={2}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} flexWrap="wrap">
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>Metered Usage</Typography>
              <Typography variant="body2" color="text.secondary">Charge separately when consumption exceeds an included allowance.</Typography>
            </Box>
            <Button variant="outlined" onClick={addMeteredRate}>Add Metered Rate</Button>
          </Stack>
          {pricing.meteredRates.length === 0 ? (
            <Paper variant="outlined" sx={{ p: 2, backgroundColor: "background.default" }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>No metered rates configured</Typography>
                <Typography variant="body2" color="text.secondary">
                  Use metered pricing only when usage above an included allowance should be charged separately.
                </Typography>
                <Button variant="contained" onClick={addMeteredRate}>Add Metered Rate</Button>
              </Stack>
            </Paper>
          ) : (
            <TableContainer>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Metric</TableCell>
                    <TableCell>Included Quantity</TableCell>
                    <TableCell>Overage</TableCell>
                    <TableCell>Unit Price</TableCell>
                    <TableCell>Unit</TableCell>
                    <TableCell>Rounding</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pricing.meteredRates.map((rate, index) => (
                    <TableRow key={`${rate.limitDefinitionId || "blank"}-${index}`}>
                      <TableCell sx={{ minWidth: 220 }}>
                        <TextField
                          select
                          fullWidth
                          value={rate.limitDefinitionId}
                          onChange={(event) => {
                            const limit = limits.find((item) => item.id === event.target.value);
                            updateMeteredRate(index, { limitDefinitionId: event.target.value, limitCode: limit?.code || "", limitName: limit?.name || "" });
                          }}
                          helperText={rate.limitCode ? `Code: ${rate.limitCode}` : "Select a limit from the catalog."}
                        >
                          <MenuItem value=""><em>Select limit</em></MenuItem>
                          {limits.map((item) => (
                            <MenuItem key={item.id} value={item.id}>
                              {item.name} ({item.code})
                            </MenuItem>
                          ))}
                        </TextField>
                      </TableCell>
                      <TableCell sx={{ minWidth: 150 }}>
                        <TextField
                          fullWidth
                          value={rate.includedQuantity}
                          onChange={(event) => updateMeteredRate(index, { includedQuantity: normalizeMoneyText(event.target.value) })}
                          inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                        />
                      </TableCell>
                      <TableCell>
                        <Switch checked={rate.overageEnabled} onChange={(event) => updateMeteredRate(index, { overageEnabled: event.target.checked })} />
                      </TableCell>
                      <TableCell sx={{ minWidth: 180 }}>
                        <TextField
                          fullWidth
                          disabled={!rate.overageEnabled}
                          value={rate.unitPrice}
                          onChange={(event) => updateMeteredRate(index, { unitPrice: normalizeMoneyText(event.target.value) })}
                          inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                          InputProps={{ startAdornment: moneyAdornment(pricing.currency) }}
                          helperText={rate.overageEnabled ? "Required when overage is enabled." : "Disabled until overage is enabled."}
                        />
                      </TableCell>
                      <TableCell sx={{ minWidth: 160 }}>
                        <TextField fullWidth value={rate.unitName} onChange={(event) => updateMeteredRate(index, { unitName: event.target.value })} helperText="Per request, per page, per minute, etc." />
                      </TableCell>
                      <TableCell sx={{ minWidth: 140 }}>
                        <TextField fullWidth value={rate.billingRounding || ""} onChange={(event) => updateMeteredRate(index, { billingRounding: event.target.value })} helperText="Optional rounding rule." />
                      </TableCell>
                      <TableCell>
                        <Chip label={rate.status} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Button color="error" onClick={() => removeMeteredRate(index)}>Remove</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }} data-pricing-section={sectionAnchor("addons")}>
        <Stack spacing={2}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} flexWrap="wrap">
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>Add-on Pricing</Typography>
              <Typography variant="body2" color="text.secondary">Optional add-ons can be sold separately with their own purchase type and pricing.</Typography>
            </Box>
            <Button variant="outlined" onClick={addAddonPricing}>Add Add-on Pricing</Button>
          </Stack>
          {pricing.addonPricing.length === 0 ? (
            <Paper variant="outlined" sx={{ p: 2, backgroundColor: "background.default" }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>No add-on pricing configured</Typography>
                <Typography variant="body2" color="text.secondary">
                  Add-ons allow optional capabilities or additional capacity to be sold separately.
                </Typography>
                <Button variant="contained" onClick={addAddonPricing}>Add Add-on Pricing</Button>
              </Stack>
            </Paper>
          ) : (
            <TableContainer>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Add-on</TableCell>
                    <TableCell>Purchase Type</TableCell>
                    <TableCell>Price</TableCell>
                    <TableCell>Maximum Quantity</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pricing.addonPricing.map((addon, index) => {
                    const priceKey = addon.purchaseType === "ANNUAL" ? "annualPrice" : addon.purchaseType === "ONE_TIME" ? "oneTimePrice" : "monthlyPrice";
                    const priceLabel = priceFieldLabel(addon.purchaseType);
                    const currentPrice = addon[priceKey];
                    return (
                      <TableRow key={`${addon.addonOfferId || "blank"}-${index}`}>
                        <TableCell sx={{ minWidth: 220 }}>
                          <TextField
                            select
                            fullWidth
                            value={addon.addonOfferId}
                            onChange={(event) => {
                              const offer = addons.find((item) => item.id === event.target.value);
                              updateAddonPricing(index, { addonOfferId: event.target.value, addonCode: offer?.code || "", addonName: offer?.name || "" });
                            }}
                            helperText={addon.addonCode ? `Code: ${addon.addonCode}` : "Select a catalog add-on."}
                          >
                            <MenuItem value=""><em>Select add-on</em></MenuItem>
                            {addons.map((item) => (
                              <MenuItem key={item.id} value={item.id}>
                                {item.name} ({item.code})
                              </MenuItem>
                            ))}
                          </TextField>
                        </TableCell>
                        <TableCell sx={{ minWidth: 160 }}>
                          <TextField
                            select
                            fullWidth
                            value={addon.purchaseType}
                            onChange={(event) => updateAddonPricing(index, { purchaseType: event.target.value as CommercialPlanAddonPurchaseType })}
                          >
                            {["MONTHLY", "ANNUAL", "ONE_TIME"].map((option) => (
                              <MenuItem key={option} value={option}>{addonPurchaseTypeLabel(option as CommercialPlanAddonPurchaseType)}</MenuItem>
                            ))}
                          </TextField>
                        </TableCell>
                        <TableCell sx={{ minWidth: 190 }}>
                          <TextField
                            fullWidth
                            label={priceLabel}
                            value={currentPrice}
                            onChange={(event) => updateAddonPricing(index, { [priceKey]: normalizeMoneyText(event.target.value) } as Partial<typeof addon>)}
                            inputProps={{ inputMode: "decimal", pattern: "\\d*(\\.\\d{0,4})?" }}
                            InputProps={{ startAdornment: moneyAdornment(pricing.currency) }}
                          />
                        </TableCell>
                        <TableCell sx={{ minWidth: 140 }}>
                          <TextField fullWidth type="number" value={addon.maxQuantity ?? ""} onChange={(event) => updateAddonPricing(index, { maxQuantity: event.target.value === "" ? null : Number.parseInt(event.target.value, 10) })} helperText="Optional cap." />
                        </TableCell>
                        <TableCell>
                          <Chip label={addon.status} size="small" variant="outlined" />
                        </TableCell>
                        <TableCell>
                          <Button color="error" onClick={() => removeAddonPricing(index)}>Remove</Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }} data-pricing-section={sectionAnchor("subscription")}>
        <Stack spacing={2}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} flexWrap="wrap">
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>Pricing Validation</Typography>
              <Typography variant="body2" color="text.secondary">
                {pricingValidation ? `${pricingValidation.blockingFindingCount} blocking issue${pricingValidation.blockingFindingCount === 1 ? "" : "s"} · ${pricingValidation.warningFindingCount} warning${pricingValidation.warningFindingCount === 1 ? "" : "s"}` : "Pricing validation has not run yet."}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={pricingValidation?.validationState || "NOT_VALIDATED"} color={pricingValidation?.blockingFindingCount ? "error" : pricingValidation?.warningFindingCount ? "warning" : "success"} variant="outlined" />
              {pricingValidationDirty ? <Chip label="Validation stale" color="warning" variant="outlined" /> : null}
            </Stack>
          </Stack>

          {!pricingValidation ? (
            <Alert severity="info" variant="outlined">Pricing validation has not run yet.</Alert>
          ) : pricingValidation.blockingFindingCount === 0 && pricingValidation.warningFindingCount === 0 ? (
            <Alert severity="success" variant="outlined">Pricing is ready to publish. No blocking issues.</Alert>
          ) : (
            <Grid container spacing={2}>
              {(["subscription", "currency", "trial", "tax", "metered", "addons"] as PricingGroupKey[]).map((groupKey) => {
                const items = validationGroups[groupKey] || [];
                if (!items.length) return null;
                const title = groupKey === "subscription"
                  ? "Subscription Pricing"
                  : groupKey === "currency"
                    ? "Currency"
                    : groupKey === "trial"
                      ? "Trial"
                      : groupKey === "tax"
                        ? "Tax"
                        : groupKey === "metered"
                          ? "Metered Usage"
                          : "Add-on Pricing";
                return (
                  <Grid key={groupKey} size={{ xs: 12, md: 6 }}>
                    <Paper variant="outlined" sx={{ p: 2, height: "100%" }}>
                      <Stack spacing={1.25}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>{title}</Typography>
                          <Button size="small" variant="outlined" onClick={() => document.querySelector(`[data-pricing-section="${groupKey === "currency" || groupKey === "trial" || groupKey === "tax" ? sectionAnchor("subscription") : sectionAnchor(groupKey)}"]`)?.scrollIntoView({ behavior: "smooth", block: "start" })}>
                            Fix
                          </Button>
                        </Stack>
                        <Stack spacing={1}>
                          {items.map((finding) => (
                            <Alert key={`${finding.code}-${finding.field}`} severity={finding.blocking ? "error" : "warning"} variant="outlined">
                              <Stack spacing={0.5}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{finding.message}</Typography>
                                {finding.remediation ? <Typography variant="caption" color="text.secondary">{finding.remediation}</Typography> : null}
                              </Stack>
                            </Alert>
                          ))}
                        </Stack>
                      </Stack>
                    </Paper>
                  </Grid>
                );
              })}
            </Grid>
          )}
        </Stack>
      </Paper>

      <Box sx={{ display: { xs: "block", md: "none" } }}>
        <Divider />
      </Box>
    </Stack>
  );
}
