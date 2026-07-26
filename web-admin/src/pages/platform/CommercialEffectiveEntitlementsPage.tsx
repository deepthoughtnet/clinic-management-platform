import * as React from "react";
import {
  Alert,
  Box,
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";

import { useAuth } from "../../auth/useAuth";
import {
  activateCommercialEntitlementOverride,
  cancelCommercialEntitlementOverride,
  compareCommercialEffectiveEntitlements,
  createCommercialEntitlementOverride,
  getCommercialEffectiveEntitlements,
  getCommercialEffectiveEntitlementHistory,
  getCommercialPlatformOverview,
  getCommercialRuntimeDiffSummary,
  getCommercialEffectiveLimit,
  getPlatformTenants,
  listCommercialEntitlementOverrides,
  listCommercialSubscriptions,
  regenerateCommercialEffectiveEntitlements,
  previewCommercialEntitlementOverride,
  submitCommercialEntitlementOverride,
  withdrawCommercialEntitlementOverride,
  approveCommercialEntitlementOverride,
  requestCommercialEntitlementOverrideChanges,
  rollbackCommercialEntitlementOverride,
  getCommercialEntitlementOverrideHistory,
  updateCommercialEntitlementOverride,
  type CommercialEffectiveEntitlementAddOn,
  type CommercialEffectiveEntitlementComparison,
  type CommercialEffectiveEntitlementOverrideHistory,
  type CommercialEffectiveEntitlementOverride,
  type CommercialEffectiveEntitlementOverridePreview,
  type CommercialEffectiveEntitlementSnapshot,
  type CommercialEffectiveEntitlementLimitResponse,
  type CommercialPlatformOverview,
  type CommercialRuntimeDiffSummary,
  type PlatformTenant,
  type CommercialSubscriptionSummary,
} from "../../api/clinicApi";

type OverrideDraft = {
  targetType: "CAPABILITY" | "MODULE" | "FEATURE" | "LIMIT" | "ADD_ON";
  targetCode: string;
  operation: "ENABLE" | "DISABLE" | "SET_VALUE" | "SET_UNLIMITED" | "SET_ADDON_STATE";
  value: string;
  addOnState: string;
  effectiveFrom: string;
  effectiveUntil: string;
  reason: string;
  internalNotes: string;
};

type LookupResult = CommercialEffectiveEntitlementLimitResponse | null;

const DEFAULT_DRAFT: OverrideDraft = {
  targetType: "MODULE",
  targetCode: "",
  operation: "ENABLE",
  value: "",
  addOnState: "INCLUDED",
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveUntil: "",
  reason: "",
  internalNotes: "",
};

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(parsed);
}

function hashPreview(value: string | null | undefined) {
  if (!value) return "—";
  return value.length > 12 ? `${value.slice(0, 12)}…` : value;
}

function runtimeSourceLabel(runtimeEnabled: boolean, shadowCompare: boolean) {
  if (runtimeEnabled) return "Commercial";
  if (shadowCompare) return "Shadow Compare";
  return "Legacy";
}

function statusTone(status: string | null | undefined) {
  if (!status) return "default" as const;
  if (status === "CURRENT" || status === "VALID" || status === "ACTIVE" || status === "INCLUDED") return "success" as const;
  if (status === "INVALID" || status === "CANCELLED" || status === "EXPIRED") return "error" as const;
  if (status === "PENDING_REGENERATION" || status === "SCHEDULED") return "warning" as const;
  return "default" as const;
}

function codeLabel(value: string | null | undefined) {
  return value || "—";
}

function previewAction(snapshot: CommercialEffectiveEntitlementSnapshot | null, draft: OverrideDraft) {
  if (!snapshot || !draft.targetCode) {
    return { before: "—", after: "—", note: "Select a target to preview the impact." };
  }
  const code = draft.targetCode.trim().toUpperCase();
  if (draft.targetType === "MODULE") {
    const before = snapshot.modules.find((item) => item.code === code)?.enabled ? "Enabled" : "Disabled";
    return {
      before,
      after: draft.operation === "ENABLE" ? "Enabled" : "Disabled",
      note: draft.operation === "ENABLE" ? "The module will become available if it exists in the catalog." : "Dependent features will be removed from the effective snapshot.",
    };
  }
  if (draft.targetType === "FEATURE") {
    const before = snapshot.features.find((item) => item.code === code)?.enabled ? "Enabled" : "Disabled";
    return {
      before,
      after: draft.operation === "ENABLE" ? "Enabled" : "Disabled",
      note: draft.operation === "ENABLE" ? "Parent module must remain effective." : "The feature will be removed from the effective snapshot.",
    };
  }
  if (draft.targetType === "LIMIT") {
    const before = snapshot.limits.find((item) => item.code === code)?.configuredValue || "—";
    return {
      before,
      after: draft.operation === "SET_UNLIMITED" ? "Unlimited" : (draft.value || "—"),
      note: "Limit overrides replace the effective plan value.",
    };
  }
  if (draft.targetType === "ADD_ON") {
    const before = snapshot.addOns.find((item) => item.code === code)?.state || "—";
    return {
      before,
      after: draft.addOnState || "—",
      note: "Add-on state changes may add or remove contributions.",
    };
  }
  const before = snapshot.capabilities.find((item) => item.code === code)?.enabled ? "Enabled" : "Disabled";
  return {
    before,
    after: draft.operation === "ENABLE" ? "Enabled" : "Disabled",
    note: "Capability changes cascade into related modules and features.",
  };
}

function tokenSummary(token: string | null | undefined) {
  if (!token) return "—";
  return token.length > 10 ? `${token.slice(0, 10)}…` : token;
}

function snapshotStateLabel(snapshot: CommercialEffectiveEntitlementSnapshot | null) {
  if (!snapshot) return "Missing";
  if (snapshot.snapshotStatus === "CURRENT" && snapshot.validationState === "VALID") return "Valid";
  if (snapshot.snapshotStatus === "INVALID" || snapshot.validationState === "INVALID") return "Invalid";
  if (snapshot.snapshotStatus === "SUPERSEDED") return "Superseded";
  return snapshot.snapshotStatus || "Missing";
}

function runtimeSourceDetail(runtimeEnabled: boolean, shadowCompare: boolean) {
  if (runtimeEnabled) {
    return {
      title: "Commercial Runtime — Authoritative",
      description: "Tenant access is resolved from the effective entitlement snapshot.",
      tone: "success" as const,
    };
  }
  if (shadowCompare) {
    return {
      title: "Shadow Comparison — Enabled",
      description: "Legacy remains authoritative. Differences are being recorded.",
      tone: "warning" as const,
    };
  }
  return {
    title: "Legacy Runtime — Authoritative",
    description: "Commercial snapshots are diagnostic only and do not affect tenant access.",
    tone: "default" as const,
  };
}

function snapshotIssue(snapshot: CommercialEffectiveEntitlementSnapshot | null) {
  if (!snapshot) {
    return {
      title: "Snapshot unavailable",
      reason: "No active commercial subscription exists for this tenant.",
      action: "Assign and activate a published commercial plan, then regenerate the snapshot.",
    };
  }
  if (snapshot.snapshotStatus === "INVALID") {
    const finding = snapshot.validationFindings[0];
    return {
      title: "Snapshot generation failed",
      reason: finding ? finding.message : "The effective entitlement snapshot did not validate.",
      action: finding?.remediation || "Review validation findings and regenerate after fixing the source data.",
    };
  }
  if (snapshot.snapshotStatus === "SUPERSEDED") {
    return {
      title: "Snapshot superseded",
      reason: "A newer effective entitlement snapshot replaced this one.",
      action: "Refresh the page to view the current snapshot.",
    };
  }
  return null;
}

function sectionTitle(title: string, helper?: string) {
  return (
    <Stack spacing={0.25}>
      <Typography variant="h6" sx={{ fontWeight: 900 }}>
        {title}
      </Typography>
      {helper ? (
        <Typography variant="body2" color="text.secondary">
          {helper}
        </Typography>
      ) : null}
    </Stack>
  );
}

function renderList<T>(items: T[], render: (item: T) => React.ReactNode, emptyMessage = "No records.") {
  if (!items.length) {
    return <Typography variant="body2" color="text.secondary">{emptyMessage}</Typography>;
  }
  return (
    <Stack spacing={1}>
      {items.map((item) => render(item))}
    </Stack>
  );
}

export default function CommercialEffectiveEntitlementsPage() {
  const auth = useAuth();
  const [platformOverview, setPlatformOverview] = React.useState<CommercialPlatformOverview | null>(null);
  const [runtimeSummary, setRuntimeSummary] = React.useState<CommercialRuntimeDiffSummary | null>(null);
  const [tenants, setTenants] = React.useState<PlatformTenant[]>([]);
  const [subscriptions, setSubscriptions] = React.useState<CommercialSubscriptionSummary[]>([]);
  const [tenantSearch, setTenantSearch] = React.useState("");
  const [selectedTenantId, setSelectedTenantId] = React.useState<string>("");
  const [snapshot, setSnapshot] = React.useState<CommercialEffectiveEntitlementSnapshot | null>(null);
  const [history, setHistory] = React.useState<CommercialEffectiveEntitlementSnapshot[]>([]);
  const [overrides, setOverrides] = React.useState<CommercialEffectiveEntitlementOverride[]>([]);
  const [comparison, setComparison] = React.useState<CommercialEffectiveEntitlementComparison | null>(null);
  const [limitLookup, setLimitLookup] = React.useState<LookupResult>(null);
  const [overridePreview, setOverridePreview] = React.useState<CommercialEffectiveEntitlementOverridePreview | null>(null);
  const [overrideHistory, setOverrideHistory] = React.useState<CommercialEffectiveEntitlementOverrideHistory[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [comparisonLoading, setComparisonLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [overrideOpen, setOverrideOpen] = React.useState(false);
  const [editingOverride, setEditingOverride] = React.useState<CommercialEffectiveEntitlementOverride | null>(null);
  const [draft, setDraft] = React.useState<OverrideDraft>(DEFAULT_DRAFT);

  const canViewDiagnostics = auth.hasPermission("commercial.runtime.diagnostics.view");
  const canManageOverrides = auth.hasPermission("commercial.overrides.manage");
  const canReviewOverrides = auth.hasPermission("commercial.overrides.review");
  const runtimeEnabled = React.useMemo(() => {
    if (runtimeSummary) {
      return runtimeSummary.commercialRuntimeEnabled;
    }
    const card = platformOverview?.kpis.find((item) => item.label === "Commercial Runtime Enabled");
    if (!card) return false;
    return /^(yes|true|1)$/i.test(String(card.value));
  }, [platformOverview, runtimeSummary]);
  const shadowCompareEnabled = runtimeSummary?.shadowComparisonEnabled ?? false;
  const runtimeSource = runtimeSourceDetail(runtimeEnabled, shadowCompareEnabled);
  const filteredTenants = React.useMemo(() => {
    const needle = tenantSearch.trim().toLowerCase();
    if (!needle) return tenants;
    return tenants.filter((tenant) => [tenant.name, tenant.code].some((value) => value.toLowerCase().includes(needle)));
  }, [tenantSearch, tenants]);
  const selectedTenant = React.useMemo(() => tenants.find((tenant) => tenant.id === selectedTenantId) || null, [selectedTenantId, tenants]);
  const selectedSubscription = React.useMemo(
    () => subscriptions.find((subscription) => subscription.tenantId === selectedTenantId) || null,
    [selectedTenantId, subscriptions],
  );
  const snapshotIssueCard = snapshotIssue(snapshot);

  React.useEffect(() => {
    if (!auth.accessToken) return;
    const accessToken = auth.accessToken;
    let cancelled = false;
    async function loadTenants() {
      try {
        const [platform, tenantRows, subscriptionRows, runtime] = await Promise.all([
          getCommercialPlatformOverview(accessToken),
          getPlatformTenants(accessToken),
          listCommercialSubscriptions(accessToken, { page: 0, size: 500 }),
          getCommercialRuntimeDiffSummary(accessToken),
        ]);
        if (cancelled) return;
        setPlatformOverview(platform);
        setTenants(tenantRows);
        setSubscriptions(subscriptionRows.items);
        setRuntimeSummary(runtime);
        setSelectedTenantId((current) => current || tenantRows[0]?.id || "");
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load commercial entitlements");
        }
      }
    }
    void loadTenants();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken]);

  React.useEffect(() => {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    let cancelled = false;
    async function loadTenantData() {
      setLoading(true);
      setError(null);
      try {
        const [current, historyPage, overrideRows] = await Promise.all([
          getCommercialEffectiveEntitlements(accessToken, selectedTenantId),
          getCommercialEffectiveEntitlementHistory(accessToken, selectedTenantId, { page: 0, size: 20 }),
          listCommercialEntitlementOverrides(accessToken, selectedTenantId),
        ]);
        if (cancelled) return;
        setSnapshot(current);
        setHistory(historyPage.items);
        setOverrides(overrideRows);
        setComparison(null);
        setLimitLookup(null);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load effective entitlements");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void loadTenantData();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, selectedTenantId]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  async function refreshCurrentTenant() {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    setLoading(true);
    setError(null);
    try {
      const [current, historyPage, overrideRows] = await Promise.all([
        getCommercialEffectiveEntitlements(accessToken, selectedTenantId),
        getCommercialEffectiveEntitlementHistory(accessToken, selectedTenantId, { page: 0, size: 20 }),
        listCommercialEntitlementOverrides(accessToken, selectedTenantId),
      ]);
      setSnapshot(current);
      setHistory(historyPage.items);
      setOverrides(overrideRows);
      setToast("Effective entitlements refreshed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh entitlements");
    } finally {
      setLoading(false);
    }
  }

  async function regenerateSnapshot() {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    setLoading(true);
    try {
      const regenerated = await regenerateCommercialEffectiveEntitlements(accessToken, selectedTenantId, "MANUAL_REGENERATE");
      setSnapshot(regenerated);
      await refreshCurrentTenant();
      setToast("Snapshot regenerated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to regenerate entitlements");
    } finally {
      setLoading(false);
    }
  }

  async function loadComparison() {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    setComparisonLoading(true);
    try {
      setComparison(await compareCommercialEffectiveEntitlements(accessToken, selectedTenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to compare entitlements");
    } finally {
      setComparisonLoading(false);
    }
  }

  async function loadLimit(limitCode: string) {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    try {
      setLimitLookup(await getCommercialEffectiveLimit(accessToken, selectedTenantId, limitCode));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load limit");
    }
  }

  React.useEffect(() => {
    if (!auth.accessToken || !selectedTenantId || !overrideOpen || !draft.targetCode.trim() || !draft.reason.trim()) {
      setOverridePreview(null);
      return;
    }
    const accessToken = auth.accessToken;
    const payload = {
      targetType: draft.targetType,
      targetCode: draft.targetCode.trim().toUpperCase(),
      operation: draft.operation,
      value: draft.value.trim() || null,
      addOnState: draft.addOnState.trim() || null,
      effectiveFrom: draft.effectiveFrom,
      effectiveUntil: draft.effectiveUntil || null,
      reason: draft.reason.trim(),
      internalNotes: draft.internalNotes.trim() || null,
      subscriptionId: snapshot?.subscriptionId || null,
    };
    const timer = window.setTimeout(() => {
      void previewCommercialEntitlementOverride(accessToken, selectedTenantId, payload).then(setOverridePreview).catch(() => setOverridePreview(null));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [auth.accessToken, draft, overrideOpen, selectedTenantId, snapshot?.subscriptionId]);

  function openCreateOverride() {
    setEditingOverride(null);
    setDraft(DEFAULT_DRAFT);
    setOverrideOpen(true);
  }

  function openEditOverride(item: CommercialEffectiveEntitlementOverride) {
    setEditingOverride(item);
    setDraft({
      targetType: item.targetType,
      targetCode: item.targetCode,
      operation: item.operation,
      value: item.value || "",
      addOnState: item.addOnState || "INCLUDED",
      effectiveFrom: item.effectiveFrom.slice(0, 10),
      effectiveUntil: item.effectiveUntil ? item.effectiveUntil.slice(0, 10) : "",
      reason: item.reason || "",
      internalNotes: "",
    });
    setOverrideOpen(true);
    void loadOverrideHistory(item.id);
  }

  async function loadOverrideHistory(overrideId: string) {
    if (!auth.accessToken || !selectedTenantId) return;
    try {
      setOverrideHistory(await getCommercialEntitlementOverrideHistory(auth.accessToken, selectedTenantId, overrideId));
    } catch {
      setOverrideHistory([]);
    }
  }

  async function saveOverride() {
    if (!auth.accessToken || !selectedTenantId || !draft.reason.trim()) return;
    const accessToken = auth.accessToken;
    try {
      const payload = {
        targetType: draft.targetType,
        targetCode: draft.targetCode.trim().toUpperCase(),
        operation: draft.operation,
        value: draft.value.trim() || null,
        addOnState: draft.addOnState.trim() || null,
        effectiveFrom: draft.effectiveFrom,
        effectiveUntil: draft.effectiveUntil || null,
        reason: draft.reason.trim(),
        internalNotes: draft.internalNotes.trim() || null,
        subscriptionId: snapshot?.subscriptionId || null,
      };
      if (editingOverride) {
        await updateCommercialEntitlementOverride(accessToken, selectedTenantId, editingOverride.id, payload);
      } else {
        await createCommercialEntitlementOverride(accessToken, selectedTenantId, payload);
      }
      setToast("Override saved.");
      setOverrideOpen(false);
      await refreshCurrentTenant();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save override");
    }
  }

  async function toggleOverride(item: CommercialEffectiveEntitlementOverride, next: "activate" | "cancel") {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    try {
      if (next === "activate") {
        await activateCommercialEntitlementOverride(accessToken, selectedTenantId, item.id);
      } else {
        await cancelCommercialEntitlementOverride(accessToken, selectedTenantId, item.id);
      }
      await refreshCurrentTenant();
      setToast(`Override ${next}d.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update override");
    }
  }

  async function submitOverrideAction(item: CommercialEffectiveEntitlementOverride) {
    if (!auth.accessToken || !selectedTenantId) return;
    try {
      await submitCommercialEntitlementOverride(auth.accessToken, selectedTenantId, item.id);
      await refreshCurrentTenant();
      setToast("Override submitted for approval.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit override");
    }
  }

  async function approveOverrideAction(item: CommercialEffectiveEntitlementOverride) {
    if (!auth.accessToken || !selectedTenantId) return;
    try {
      await approveCommercialEntitlementOverride(auth.accessToken, selectedTenantId, item.id, item.reason || null);
      await refreshCurrentTenant();
      setToast("Override approved.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve override");
    }
  }

  async function requestChangesAction(item: CommercialEffectiveEntitlementOverride) {
    if (!auth.accessToken || !selectedTenantId) return;
    try {
      await requestCommercialEntitlementOverrideChanges(auth.accessToken, selectedTenantId, item.id, "Please clarify the business reason.");
      await refreshCurrentTenant();
      setToast("Changes requested.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to request changes");
    }
  }

  async function rollbackOverrideAction(item: CommercialEffectiveEntitlementOverride) {
    if (!auth.accessToken || !selectedTenantId) return;
    try {
      await rollbackCommercialEntitlementOverride(auth.accessToken, selectedTenantId, item.id, "Rollback requested from diagnostics workspace.");
      await refreshCurrentTenant();
      setToast("Override rolled back.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to rollback override");
    }
  }

  const currentLimitCode = snapshot?.limits[0]?.code || "MAX_DOCTORS";
  const preview = previewAction(snapshot, draft);

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Effective Entitlements
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 900 }}>
          Resolved commercial access from the tenant’s active subscription, published plan version, add-ons, and tenant overrides.
        </Typography>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {toast ? <Alert severity="success" variant="outlined" onClose={() => setToast(null)}>{toast}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} alignItems={{ md: "center" }} justifyContent="space-between">
            <Stack spacing={0.4}>
              <Typography variant="subtitle2" color="text.secondary">Tenant selector</Typography>
              <Typography variant="body2" color="text.secondary">
                Search by tenant name or code. No selected clinic tenant is required.
              </Typography>
            </Stack>
            <Stack spacing={1} alignItems={{ md: "flex-end" }}>
              <Chip size="small" label={runtimeSource.title} color={runtimeSource.tone} variant="outlined" />
              <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 420, textAlign: { md: "right" } }}>
                {runtimeSource.description}
              </Typography>
            </Stack>
          </Stack>

          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} flexWrap="wrap" useFlexGap>
            <Card variant="outlined" sx={{ minWidth: 190, flex: "1 1 220px" }}>
              <CardContent>
                <Typography variant="caption" color="text.secondary">Tenant</Typography>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>{selectedTenant?.name || "—"}</Typography>
                <Typography variant="body2" color="text.secondary">Tenant code: {selectedTenant?.code || "—"}</Typography>
              </CardContent>
            </Card>
            <Card variant="outlined" sx={{ minWidth: 190, flex: "1 1 220px" }}>
              <CardContent>
                <Typography variant="caption" color="text.secondary">Commercial Subscription</Typography>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>{selectedSubscription?.displayName || selectedSubscription?.planTemplateName || "None"}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {selectedSubscription ? `${selectedSubscription.publishedVersionLabel || "Version " + selectedSubscription.publishedVersionNumber} · ${selectedSubscription.subscriptionStatus}` : "No active commercial subscription"}
                </Typography>
              </CardContent>
            </Card>
            <Card variant="outlined" sx={{ minWidth: 190, flex: "1 1 220px" }}>
              <CardContent>
                <Typography variant="caption" color="text.secondary">Snapshot</Typography>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>{snapshotStateLabel(snapshot)}</Typography>
                <Typography variant="body2" color="text.secondary">Generated: {formatDateTime(snapshot?.generatedAt)}</Typography>
              </CardContent>
            </Card>
            <Card variant="outlined" sx={{ minWidth: 190, flex: "1 1 220px" }}>
              <CardContent>
                <Typography variant="caption" color="text.secondary">Runtime Source</Typography>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>{runtimeSource.title}</Typography>
                <Typography variant="body2" color="text.secondary">{runtimeSource.description}</Typography>
              </CardContent>
            </Card>
          </Stack>

          {snapshotIssueCard ? (
            <Alert severity={snapshotIssueCard.title === "Snapshot generation failed" ? "error" : "warning"} variant="outlined">
              <Stack spacing={0.5}>
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{snapshotIssueCard.title}</Typography>
                <Typography variant="body2"><strong>Reason:</strong> {snapshotIssueCard.reason}</Typography>
                <Typography variant="body2"><strong>How to resolve:</strong> {snapshotIssueCard.action}</Typography>
              </Stack>
            </Alert>
          ) : null}

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Button variant="outlined" onClick={refreshCurrentTenant} disabled={!selectedTenantId || loading}>
              Refresh
            </Button>
            <Button variant="contained" onClick={regenerateSnapshot} disabled={!selectedTenantId || loading}>
              Regenerate
            </Button>
            <Button variant="outlined" onClick={() => void loadComparison()} disabled={!selectedTenantId || comparisonLoading || !canViewDiagnostics}>
              View Legacy Comparison
            </Button>
            <Button variant="outlined" onClick={openCreateOverride} disabled={!selectedTenantId || !canManageOverrides}>
              Create Override
            </Button>
            <Button variant="outlined" onClick={() => void loadOverrideHistory(overrides[0]?.id || "")} disabled={!selectedTenantId || !overrides.length}>
              View History
            </Button>
          </Stack>

          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
            <TextField
              fullWidth
              label="Search tenants"
              value={tenantSearch}
              onChange={(event) => setTenantSearch(event.target.value)}
              placeholder="Tenant name or code"
            />
            <TextField
              select
              fullWidth
              label="Tenant"
              value={selectedTenantId}
              onChange={(event) => setSelectedTenantId(event.target.value)}
            >
              {filteredTenants.map((tenant) => (
                <MenuItem key={tenant.id} value={tenant.id}>
                  {tenant.name} ({tenant.code})
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </Stack>
      </Paper>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Chip label={`Tenant ${selectedTenant?.name || "—"}`} variant="outlined" />
        <Chip label={`Active Subscription ${selectedSubscription?.displayName || selectedSubscription?.planTemplateName || tokenSummary(snapshot?.subscriptionId) || "—"}`} color={statusTone(selectedSubscription?.subscriptionStatus || snapshot?.subscriptionStatus)} variant="outlined" />
        <Chip label={`Published Version ${selectedSubscription?.publishedVersionLabel || (snapshot?.publishedVersionNumber ? `Version ${snapshot.publishedVersionNumber}` : "—")}`} variant="outlined" />
        <Chip label={`Snapshot ${snapshot?.snapshotStatus || "—"}`} color={statusTone(snapshot?.snapshotStatus)} variant="outlined" />
        <Chip label={`Generated ${formatDateTime(snapshot?.generatedAt)}`} variant="outlined" />
        <Chip label={`Hash ${hashPreview(snapshot?.contentHash)}`} variant="outlined" />
        <Chip label={`Runtime Source ${runtimeSource.title}`} color={runtimeEnabled ? "success" : "default"} variant="outlined" />
      </Stack>

      <Accordion variant="outlined" disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
          <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Technical Details</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1}>
            <Typography variant="body2" color="text.secondary">
              Snapshot id: {tokenSummary(snapshot?.snapshotId)} · Source hash: {hashPreview(snapshot?.sourceHash)} · Content hash: {hashPreview(snapshot?.contentHash)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Subscription id: {tokenSummary(snapshot?.subscriptionId)} · Plan template id: {tokenSummary(snapshot?.planTemplateId)} · Published version id: {tokenSummary(snapshot?.publishedVersionId)}
            </Typography>
          </Stack>
        </AccordionDetails>
      </Accordion>

      <Grid container spacing={2}>
          {[
            { label: "Tenant", value: selectedTenant ? `${selectedTenant.name} (${selectedTenant.code})` : "—" },
          { label: "Active Subscription", value: selectedSubscription ? `${selectedSubscription.displayName || selectedSubscription.planTemplateName} · ${selectedSubscription.subscriptionStatus}` : snapshot?.subscriptionStatus || "—" },
          { label: "Published Version", value: selectedSubscription?.publishedVersionLabel || (snapshot?.publishedVersionNumber ? `Version ${snapshot.publishedVersionNumber}` : "—") },
          { label: "Snapshot Status", value: snapshot?.snapshotStatus || "—" },
          { label: "Generated At", value: formatDateTime(snapshot?.generatedAt) },
          { label: "Content Hash", value: hashPreview(snapshot?.contentHash) },
        ].map((card) => (
          <Grid key={card.label} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={0.5}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                    {card.label}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {card.value}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {loading ? <Alert severity="info">Loading effective entitlements…</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Capabilities", "Business capabilities resolved from the published plan and overrides.")}
                {renderList(snapshot?.capabilities || [], (item) => (
                  <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>{item.name}</Typography>
                        <Typography variant="body2" color="text.secondary">{item.code}</Typography>
                      </Box>
                      <Chip size="small" label={item.source} variant="outlined" />
                    </Stack>
                  </Paper>
                ), snapshot ? "No effective capabilities are available because this tenant has no valid active commercial subscription." : "Snapshot unavailable")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Modules", "Runtime module enablement and provenance.")}
                {renderList(snapshot?.modules || [], (item) => (
                  <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={0.75}>
                      <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>{item.name}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {item.code} · {codeLabel(item.runtimeModuleCode)}
                          </Typography>
                        </Box>
                        <Chip size="small" label={item.enabled ? "Enabled" : "Disabled"} color={item.enabled ? "success" : "default"} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">{item.reason || "—"}</Typography>
                    </Stack>
                  </Paper>
                ), snapshot ? "No commercial modules were resolved. Legacy runtime remains authoritative." : "Snapshot unavailable")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Features", "Feature access is only effective when the parent module is present.")}
                {renderList(snapshot?.features || [], (item) => (
                  <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={0.75}>
                      <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>{item.name}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {item.code} · {codeLabel(item.parentModuleCode)}
                          </Typography>
                        </Box>
                        <Chip size="small" label={item.enabled ? "Enabled" : "Disabled"} color={item.enabled ? "success" : "default"} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">{item.reason || "—"}</Typography>
                    </Stack>
                  </Paper>
                ), snapshot ? "No commercial features were resolved from the active plan and overrides." : "Snapshot unavailable")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Limits", "Configured effective limits, not usage or remaining allowance.")}
                {renderList(snapshot?.limits || [], (item) => (
                  <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={0.75}>
                      <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>{item.name}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {item.code} · {item.unit || "—"} · {item.period || "—"}
                          </Typography>
                        </Box>
                        <Chip size="small" label={item.unlimited ? "Unlimited" : item.configuredValue || "—"} color={item.unlimited ? "success" : "default"} variant="outlined" />
                      </Stack>
                      <Button size="small" onClick={() => void loadLimit(item.code)}>
                        Preview lookup
                      </Button>
                    </Stack>
                  </Paper>
                ), snapshot ? "No configured commercial limits were resolved. S4 does not display usage or remaining allowance." : "Snapshot unavailable")}
                {limitLookup ? (
                  <Alert severity="info" variant="outlined">
                    Limit lookup for {limitLookup.code}: {limitLookup.unlimited ? "Unlimited" : limitLookup.configuredValue || "—"} · source {limitLookup.source} · snapshot {hashPreview(limitLookup.snapshotId)}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Add-ons", "Included and purchasable add-ons with effective contributions.")}
                {renderList((snapshot?.addOns || []) as CommercialEffectiveEntitlementAddOn[], (item) => (
                  <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>{item.name}</Typography>
                        <Typography variant="body2" color="text.secondary">{item.code}</Typography>
                      </Box>
                      <Chip size="small" label={item.state} color={statusTone(item.state)} variant="outlined" />
                    </Stack>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                      Contributions: {item.appliedContributions.length ? item.appliedContributions.join(" · ") : "None"}
                    </Typography>
                  </Paper>
                ), snapshot ? "No add-ons are included or available for purchase for this tenant." : "Snapshot unavailable")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Overrides", "Tenant-specific commercial policy changes.")}
                {renderList(overrides, (item) => (
                  <Paper key={item.id} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={1}>
                      <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                        <Box>
                          <Typography sx={{ fontWeight: 800 }}>{item.targetCode}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {item.targetType} · {item.operation} · {item.status}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {item.reason || "No reason provided"}
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                          <Chip size="small" label={item.status} color={statusTone(item.status)} variant="outlined" />
                          <Button size="small" onClick={() => openEditOverride(item)} disabled={!canManageOverrides || item.status === "APPROVED" || item.status === "ACTIVE"}>Edit</Button>
                          <Button size="small" onClick={() => void submitOverrideAction(item)} disabled={!canManageOverrides || item.status !== "DRAFT"}>Submit</Button>
                          <Button size="small" onClick={() => void approveOverrideAction(item)} disabled={!canReviewOverrides || item.status !== "PENDING_APPROVAL" && item.status !== "CHANGES_REQUESTED"}>Approve</Button>
                          <Button size="small" onClick={() => void requestChangesAction(item)} disabled={!canReviewOverrides || item.status !== "PENDING_APPROVAL" && item.status !== "APPROVED"}>Request Changes</Button>
                          <Button size="small" onClick={() => void toggleOverride(item, "activate")} disabled={!canManageOverrides || (item.status !== "APPROVED" && item.status !== "SCHEDULED")}>Activate</Button>
                          <Button size="small" onClick={() => void toggleOverride(item, "cancel")} disabled={!canManageOverrides || item.status === "CANCELLED"}>Cancel</Button>
                          <Button size="small" onClick={() => void rollbackOverrideAction(item)} disabled={!canManageOverrides}>Rollback</Button>
                        </Stack>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        Effective {formatDate(item.effectiveFrom)} to {formatDate(item.effectiveUntil)}
                      </Typography>
                    </Stack>
                  </Paper>
                ), "No active tenant-specific overrides exist.")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                {sectionTitle("Provenance", "Where each resolved entitlement came from.")}
                {renderList(snapshot?.provenance || [], (item) => (
                  <Paper key={`${item.itemType}-${item.code}-${item.source}`} variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={0.35}>
                      <Typography sx={{ fontWeight: 800 }}>{item.itemType} · {item.code}</Typography>
                      <Typography variant="body2" color="text.secondary">{item.source} · {item.reason}</Typography>
                      {item.details ? <Typography variant="caption" color="text.secondary">{item.details}</Typography> : null}
                    </Stack>
                  </Paper>
                ), "No provenance was resolved from the current snapshot.")}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            {sectionTitle("Validation", "Effective entitlement validation findings.")}
            {renderList(snapshot?.validationFindings || [], (item) => (
              <Paper key={item.code} variant="outlined" sx={{ p: 1.5 }}>
                <Stack direction="row" justifyContent="space-between" spacing={1}>
                  <Box>
                    <Typography sx={{ fontWeight: 800 }}>{item.title}</Typography>
                    <Typography variant="body2" color="text.secondary">{item.message}</Typography>
                  </Box>
                  <Chip size="small" label={item.blocking ? "Blocking" : "Advisory"} color={item.blocking ? "error" : "warning"} variant="outlined" />
                </Stack>
              </Paper>
            ), snapshot?.validationFindings?.length ? "Snapshot is valid. No blocking or warning findings were reported." : "Snapshot validation has not run.")}
          </Stack>
        </CardContent>
      </Card>

      {canViewDiagnostics ? (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1.5}>
              {sectionTitle("Legacy Comparison", "Legacy remains authoritative while commercial runtime is disabled.")}
              {comparisonLoading ? <Alert severity="info">Loading comparison…</Alert> : null}
              {comparison ? (
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Modules</Typography>
                    <Stack spacing={0.75}>
                      {comparison.modules.map((item) => (
                        <Chip key={item.code} label={`${item.code} · ${item.category}`} variant="outlined" />
                      ))}
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Features</Typography>
                    <Stack spacing={0.75}>
                      {comparison.features.map((item) => (
                        <Chip key={item.code} label={`${item.code} · ${item.category}`} variant="outlined" />
                      ))}
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Limits</Typography>
                    <Stack spacing={0.75}>
                      {comparison.limits.map((item) => (
                        <Chip key={item.code} label={`${item.code} · ${item.category}`} variant="outlined" />
                      ))}
                    </Stack>
                  </Grid>
                </Grid>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Click View Legacy Comparison to load the diagnostic diff.
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            {sectionTitle("Snapshot History", "Immutable history of generated effective entitlement snapshots.")}
            {renderList(history, (item) => (
              <Paper key={item.snapshotId} variant="outlined" sx={{ p: 1.5 }}>
                <Stack direction="row" justifyContent="space-between" spacing={1}>
                  <Box>
                    <Typography sx={{ fontWeight: 800 }}>{item.snapshotStatus}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {item.generationReason} · {formatDateTime(item.generatedAt)}
                    </Typography>
                  </Box>
                  <Chip size="small" label={hashPreview(item.contentHash)} variant="outlined" />
                </Stack>
              </Paper>
            ), "No snapshot history yet.")}
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={overrideOpen} onClose={() => setOverrideOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editingOverride ? "Edit Override" : "Create Override"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <FormControl fullWidth>
                <InputLabel id="override-target-type">Target Type</InputLabel>
                <Select
                  labelId="override-target-type"
                  label="Target Type"
                  value={draft.targetType}
                  onChange={(event) => setDraft((current) => ({ ...current, targetType: event.target.value as OverrideDraft["targetType"] }))}
                >
                  <MenuItem value="CAPABILITY">Capability</MenuItem>
                  <MenuItem value="MODULE">Module</MenuItem>
                  <MenuItem value="FEATURE">Feature</MenuItem>
                  <MenuItem value="LIMIT">Limit</MenuItem>
                  <MenuItem value="ADD_ON">Add-on</MenuItem>
                </Select>
              </FormControl>
              <TextField
                fullWidth
                label="Target Code"
                value={draft.targetCode}
                onChange={(event) => setDraft((current) => ({ ...current, targetCode: event.target.value.toUpperCase() }))}
                helperText="Use the commercial business code."
              />
            </Stack>

            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <FormControl fullWidth>
                <InputLabel id="override-operation">Operation</InputLabel>
                <Select
                  labelId="override-operation"
                  label="Operation"
                  value={draft.operation}
                  onChange={(event) => setDraft((current) => ({ ...current, operation: event.target.value as OverrideDraft["operation"] }))}
                >
                  <MenuItem value="ENABLE">Enable</MenuItem>
                  <MenuItem value="DISABLE">Disable</MenuItem>
                  <MenuItem value="SET_VALUE">Set Value</MenuItem>
                  <MenuItem value="SET_UNLIMITED">Set Unlimited</MenuItem>
                  <MenuItem value="SET_ADDON_STATE">Set Add-on State</MenuItem>
                </Select>
              </FormControl>
              <TextField
                fullWidth
                label="Reason"
                value={draft.reason}
                onChange={(event) => setDraft((current) => ({ ...current, reason: event.target.value }))}
                required
              />
            </Stack>

            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField
                fullWidth
                label="Effective From"
                type="date"
                value={draft.effectiveFrom}
                onChange={(event) => setDraft((current) => ({ ...current, effectiveFrom: event.target.value }))}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                fullWidth
                label="Effective Until"
                type="date"
                value={draft.effectiveUntil}
                onChange={(event) => setDraft((current) => ({ ...current, effectiveUntil: event.target.value }))}
                InputLabelProps={{ shrink: true }}
              />
            </Stack>

            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField
                fullWidth
                label="Value"
                value={draft.value}
                onChange={(event) => setDraft((current) => ({ ...current, value: event.target.value }))}
              />
              <TextField
                fullWidth
                label="Add-on State"
                value={draft.addOnState}
                onChange={(event) => setDraft((current) => ({ ...current, addOnState: event.target.value.toUpperCase() }))}
              />
            </Stack>

            <TextField
              fullWidth
              multiline
              minRows={3}
              label="Internal Notes"
              value={draft.internalNotes}
              onChange={(event) => setDraft((current) => ({ ...current, internalNotes: event.target.value }))}
            />

            <Alert severity={overridePreview ? "info" : "warning"} variant="outlined">
              <Stack spacing={0.5}>
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Impact Preview</Typography>
                {overridePreview ? (
                  <>
                    <Typography variant="body2">Before: {overridePreview.beforeValue || "—"}</Typography>
                    <Typography variant="body2">After: {overridePreview.afterValue || "—"}</Typography>
                    <Typography variant="body2">{overridePreview.runtimeImpact}</Typography>
                    <Typography variant="body2">Dependent effects: {overridePreview.dependentEffects.length ? overridePreview.dependentEffects.join(" · ") : "None"}</Typography>
                  </>
                ) : (
                  <>
                    <Typography variant="body2">Before: {preview.before} · After: {preview.after}</Typography>
                    <Typography variant="body2">{preview.note}</Typography>
                  </>
                )}
              </Stack>
            </Alert>

            {overrideHistory.length ? (
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>Approval History</Typography>
                    {overrideHistory.map((entry) => (
                      <Typography key={`${entry.overrideId}-${entry.revision}`} variant="body2" color="text.secondary">
                        {entry.changedAt} · {entry.action || "Event"} · {entry.previousStatus || "—"} → {entry.newStatus || "—"}
                      </Typography>
                    ))}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOverrideOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveOverride()} disabled={!draft.reason.trim() || !draft.targetCode.trim()}>
            Save Draft
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
