import * as React from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import { RelationshipDialog } from "./CommercialCatalogRelationshipDialog";
import {
  createCommercialAddon,
  createCommercialCapability,
  createCommercialFeature,
  createCommercialLimit,
  createCommercialModule,
  getCommercialAddon,
  getCommercialCapability,
  getCommercialFeature,
  getCommercialLimit,
  getCommercialModule,
  listCommercialAddons,
  listCommercialCapabilities,
  listCommercialFeatures,
  listCommercialLimits,
  listCommercialModules,
  retireCommercialAddon,
  retireCommercialCapability,
  retireCommercialFeature,
  retireCommercialLimit,
  retireCommercialModule,
  updateCommercialAddon,
  updateCommercialAddonCapabilities,
  updateCommercialAddonFeatures,
  updateCommercialAddonLimitIncrements,
  updateCommercialAddonModules,
  updateCommercialCapability,
  updateCommercialCapabilityModules,
  updateCommercialFeature,
  updateCommercialLimit,
  updateCommercialModule,
  type CommercialAddonDetail,
  type CommercialAddonSummary,
  type CommercialCapabilityDetail,
  type CommercialCapabilitySummary,
  type CommercialFeatureSummary,
  type CommercialLimitDefinitionSummary,
  type CommercialModuleSummary,
  type CommercialCatalogStatus,
} from "../../api/clinicApi";

type TabKey = "capabilities" | "modules" | "features" | "limits" | "addons";
const DEFAULT_TAB: TabKey = "capabilities";
const TAB_KEYS = new Set<TabKey>(["capabilities", "modules", "features", "limits", "addons"]);
type RelationshipDialogKind = "capability-modules" | "addon-capabilities" | "addon-modules" | "addon-features" | "addon-limits";

const MODULE_GROUPS: Array<{ label: string; codes: string[] }> = [
  { label: "Core Clinical", codes: ["PATIENTS", "APPOINTMENTS", "CONSULTATION", "PRESCRIPTION"] },
  { label: "Operations", codes: ["BILLING", "REPORTS", "NOTIFICATIONS", "VACCINATION"] },
  { label: "Laboratory", codes: ["LABORATORY"] },
  { label: "Pharmacy", codes: ["INVENTORY", "PHARMACY_POS", "PHARMACY"] },
  { label: "AI", codes: ["AI_COPILOT"] },
  { label: "Engage", codes: ["CAREPILOT"] },
];

const RELATIONSHIP_DIALOG_COPY: Record<RelationshipDialogKind, {
  title: string;
  noun: string;
  parentLabel: string;
  intro: string;
  empty: string;
  placeholder: string;
  configuredLabel: string;
  saveLabel: string;
}> = {
  "capability-modules": {
    title: "Assign Modules",
    noun: "modules",
    parentLabel: "Capability",
    intro: "Select the application modules included in this capability.",
    empty: "No modules match your search.",
    placeholder: "Search modules",
    configuredLabel: "selected",
    saveLabel: "Save Changes",
  },
  "addon-capabilities": {
    title: "Assign Capabilities",
    noun: "capabilities",
    parentLabel: "Add-on",
    intro: "Select the capabilities granted by this add-on.",
    empty: "No capabilities match your search.",
    placeholder: "Search capabilities",
    configuredLabel: "selected",
    saveLabel: "Save Changes",
  },
  "addon-modules": {
    title: "Assign Modules",
    noun: "modules",
    parentLabel: "Add-on",
    intro: "Select the application modules granted by this add-on.",
    empty: "No modules match your search.",
    placeholder: "Search modules",
    configuredLabel: "selected",
    saveLabel: "Save Changes",
  },
  "addon-features": {
    title: "Assign Features",
    noun: "features",
    parentLabel: "Add-on",
    intro: "Select the features granted by this add-on.",
    empty: "No features match your search.",
    placeholder: "Search features",
    configuredLabel: "selected",
    saveLabel: "Save Changes",
  },
  "addon-limits": {
    title: "Configure Limit Increments",
    noun: "limits",
    parentLabel: "Add-on",
    intro: "Select limit definitions and set the increment granted by this add-on.",
    empty: "No limits match your search.",
    placeholder: "Search limits",
    configuredLabel: "configured",
    saveLabel: "Save Changes",
  },
};

type RelationshipRecord = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: string;
  displayOrder: number;
  runtimeModuleCode?: string | null;
  addonType?: string | null;
  moduleCount?: number;
  capabilityCount?: number;
  featureCount?: number;
  limitIncrementCount?: number;
  modules?: Array<{ moduleId: string; moduleCode: string; moduleName: string; includedByDefault?: boolean; displayOrder?: number }>;
  capabilities?: Array<{ capabilityId: string; capabilityCode: string; capabilityName: string }>;
  features?: Array<{ featureId: string; featureCode: string; featureName: string }>;
  limitIncrements?: Array<{ limitDefinitionId: string; limitDefinitionCode: string; limitDefinitionName: string; incrementValue: string }>;
};

function sortByDisplayOrderThenNameCode<T extends { displayOrder?: number; name: string; code: string }>(left: T, right: T) {
  return (left.displayOrder ?? 0) - (right.displayOrder ?? 0) || left.name.localeCompare(right.name) || left.code.localeCompare(right.code);
}

function textForSearch(value: string | null | undefined) {
  return (value || "").toLowerCase();
}

function matchesSearch(record: { code: string; name: string; description?: string | null; runtimeModuleCode?: string | null; unit?: string | null }, search: string) {
  if (!search) return true;
  const needle = search.toLowerCase();
  return [record.code, record.name, record.description || "", record.runtimeModuleCode || "", record.unit || ""].some((value) => textForSearch(value).includes(needle));
}

function previewChips(items: Array<{ label?: string; name?: string }>, moreLabel?: string) {
  const preview = items.slice(0, 3);
  const extra = items.length - preview.length;
  return (
    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
      {preview.map((item) => <Chip key={item.label || item.name} size="small" label={item.label || item.name || ""} variant="outlined" />)}
      {extra > 0 ? <Chip size="small" label={moreLabel || `+${extra} more`} variant="outlined" /> : null}
    </Stack>
  );
}

function moduleGroupLabel(code: string) {
  return MODULE_GROUPS.find((group) => group.codes.includes(code))?.label || "Other";
}

function relationshipSummaryText(kind: RelationshipDialogKind, count: number, total: number) {
  const noun = RELATIONSHIP_DIALOG_COPY[kind].noun;
  return total > 0 ? `${count} of ${total} ${noun} ${RELATIONSHIP_DIALOG_COPY[kind].configuredLabel}` : `${count} ${RELATIONSHIP_DIALOG_COPY[kind].configuredLabel}`;
}

function relationshipTitle(kind: RelationshipDialogKind, record?: RelationshipRecord | null) {
  const copy = RELATIONSHIP_DIALOG_COPY[kind];
  return {
    title: copy.title,
    parentLabel: copy.parentLabel,
    parentName: record?.name || "Unknown record",
    parentCode: record?.code || "—",
    intro: copy.intro,
  };
}

function previewSort(items: Array<{ code: string; name: string }>) {
  return [...items].sort((left, right) => left.name.localeCompare(right.name) || left.code.localeCompare(right.code));
}

type DialogMode = "create" | "edit" | null;

type FormValues = Record<string, string>;

type BaseDialogProps = {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  submitting: boolean;
  children: React.ReactNode;
};

function BaseDialog({ open, title, onClose, onSubmit, submitting, children }: BaseDialogProps) {
  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 900 }}>{title}</DialogTitle>
      <DialogContent sx={{ pt: 1 }}>{children}</DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>Cancel</Button>
        <Button onClick={onSubmit} variant="contained" disabled={submitting}>
          {submitting ? "Saving..." : "Save Changes"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function statusColor(status: string) {
  switch ((status || "").toUpperCase()) {
    case "ACTIVE":
      return "success";
    case "INACTIVE":
      return "warning";
    case "RETIRED":
      return "default";
    default:
      return "default";
  }
}

function normalizeStatusFilter(value: string) {
  return value === "ALL" ? null : (value as CommercialCatalogStatus | null);
}

function searchText(search?: string | null) {
  return (search || "").trim().toLowerCase();
}

function normalizeTabKey(value: string | null): TabKey {
  return value && TAB_KEYS.has(value as TabKey) ? (value as TabKey) : DEFAULT_TAB;
}

export default function CommercialCatalogPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = normalizeTabKey(searchParams.get("tab"));
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<{ severity: "success" | "error" | "info"; message: string } | null>(null);
  const [capabilities, setCapabilities] = React.useState<CommercialCapabilitySummary[]>([]);
  const [modules, setModules] = React.useState<CommercialModuleSummary[]>([]);
  const [features, setFeatures] = React.useState<CommercialFeatureSummary[]>([]);
  const [limits, setLimits] = React.useState<CommercialLimitDefinitionSummary[]>([]);
  const [addons, setAddons] = React.useState<CommercialAddonSummary[]>([]);
  const [filters, setFilters] = React.useState<Record<TabKey, { search: string; status: string }>>({
    capabilities: { search: "", status: "ALL" },
    modules: { search: "", status: "ALL" },
    features: { search: "", status: "ALL" },
    limits: { search: "", status: "ALL" },
    addons: { search: "", status: "ALL" },
  });
  const [dialog, setDialog] = React.useState<{
    kind: TabKey | RelationshipDialogKind;
    mode: DialogMode;
    id?: string;
  } | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [form, setForm] = React.useState<FormValues>({});
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [retireTarget, setRetireTarget] = React.useState<{ kind: TabKey; id: string } | null>(null);
  const [expandedRows, setExpandedRows] = React.useState<Record<string, boolean>>({});
  const [previewCache, setPreviewCache] = React.useState<Record<string, RelationshipRecord>>({});

  const refresh = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [capRes, modRes, featRes, limitRes, addonRes] = await Promise.all([
        listCommercialCapabilities(auth.accessToken, { page: 0, size: 500 }),
        listCommercialModules(auth.accessToken, { page: 0, size: 500 }),
        listCommercialFeatures(auth.accessToken, { page: 0, size: 500 }),
        listCommercialLimits(auth.accessToken, { page: 0, size: 500 }),
        listCommercialAddons(auth.accessToken, { page: 0, size: 500 }),
      ]);
      setCapabilities(capRes.items);
      setModules(modRes.items);
      setFeatures(featRes.items);
      setLimits(limitRes.items);
      setAddons(addonRes.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load commercial catalog");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken]);

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  const activeFilters = filters[tab];
  const filteredCapabilities = React.useMemo(() => {
    const search = searchText(activeFilters.search);
    const status = normalizeStatusFilter(activeFilters.status);
    return capabilities.filter((row) =>
      (!search || `${row.code} ${row.name}`.toLowerCase().includes(search))
      && (!status || row.status === status)
    );
  }, [activeFilters.search, activeFilters.status, capabilities]);

  const filteredModules = React.useMemo(() => {
    const search = searchText(activeFilters.search);
    const status = normalizeStatusFilter(activeFilters.status);
    return modules.filter((row) =>
      (!search || `${row.code} ${row.name} ${row.runtimeModuleCode || ""}`.toLowerCase().includes(search))
      && (!status || row.status === status)
    );
  }, [activeFilters.search, activeFilters.status, modules]);

  const filteredFeatures = React.useMemo(() => {
    const search = searchText(activeFilters.search);
    const status = normalizeStatusFilter(activeFilters.status);
    return features.filter((row) =>
      (!search || `${row.code} ${row.name} ${row.moduleCode}`.toLowerCase().includes(search))
      && (!status || row.status === status)
    );
  }, [activeFilters.search, activeFilters.status, features]);

  const filteredLimits = React.useMemo(() => {
    const search = searchText(activeFilters.search);
    const status = normalizeStatusFilter(activeFilters.status);
    return limits.filter((row) =>
      (!search || `${row.code} ${row.name} ${row.unit}`.toLowerCase().includes(search))
      && (!status || row.status === status)
    );
  }, [activeFilters.search, activeFilters.status, limits]);

  const filteredAddons = React.useMemo(() => {
    const search = searchText(activeFilters.search);
    const status = normalizeStatusFilter(activeFilters.status);
    return addons.filter((row) =>
      (!search || `${row.code} ${row.name} ${row.addonType}`.toLowerCase().includes(search))
      && (!status || row.status === status)
    );
  }, [activeFilters.search, activeFilters.status, addons]);

  React.useEffect(() => {
    if (tab !== (searchParams.get("tab") as TabKey | null)) {
      const next = new URLSearchParams(searchParams);
      next.set("tab", tab);
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams, tab]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  async function submitForm() {
    if (!auth.accessToken || !dialog) return;
    setSubmitting(true);
    try {
      if (dialog.kind === "capabilities") {
        const body = {
          code: form.code,
          name: form.name,
          description: form.description || null,
          status: (form.status || "ACTIVE") as CommercialCatalogStatus,
          displayOrder: Number(form.displayOrder || 0),
          standaloneAllowed: form.standaloneAllowed === "true",
          addonAllowed: form.addonAllowed === "true",
        };
        if (dialog.mode === "create") {
          await createCommercialCapability(auth.accessToken, body);
        } else {
          await updateCommercialCapability(auth.accessToken, dialog.id!, body);
        }
      } else if (dialog.kind === "modules") {
        const body = {
          code: form.code,
          name: form.name,
          description: form.description || null,
          status: (form.status || "ACTIVE") as CommercialCatalogStatus,
          displayOrder: Number(form.displayOrder || 0),
          runtimeModuleCode: form.runtimeModuleCode || null,
        };
        if (dialog.mode === "create") {
          await createCommercialModule(auth.accessToken, body);
        } else {
          await updateCommercialModule(auth.accessToken, dialog.id!, body);
        }
      } else if (dialog.kind === "features") {
        const body = {
          code: form.code,
          name: form.name,
          description: form.description || null,
          status: (form.status || "ACTIVE") as CommercialCatalogStatus,
          displayOrder: Number(form.displayOrder || 0),
          moduleId: form.moduleId,
          runtimeFeatureKey: form.runtimeFeatureKey || null,
        };
        if (dialog.mode === "create") {
          await createCommercialFeature(auth.accessToken, body);
        } else {
          await updateCommercialFeature(auth.accessToken, dialog.id!, body);
        }
      } else if (dialog.kind === "limits") {
        const body = {
          code: form.code,
          name: form.name,
          description: form.description || null,
          unit: form.unit,
          valueType: form.valueType,
          aggregationPeriod: form.aggregationPeriod,
          enforcementMode: form.enforcementMode,
          status: (form.status || "ACTIVE") as CommercialCatalogStatus,
          displayOrder: Number(form.displayOrder || 0),
        };
        if (dialog.mode === "create") {
          await createCommercialLimit(auth.accessToken, body);
        } else {
          await updateCommercialLimit(auth.accessToken, dialog.id!, body);
        }
      } else if (dialog.kind === "addons") {
        const body = {
          code: form.code,
          name: form.name,
          description: form.description || null,
          status: (form.status || "ACTIVE") as CommercialCatalogStatus,
          addonType: form.addonType,
          displayOrder: Number(form.displayOrder || 0),
          repeatable: form.repeatable === "true",
        };
        if (dialog.mode === "create") {
          await createCommercialAddon(auth.accessToken, body);
        } else {
          await updateCommercialAddon(auth.accessToken, dialog.id!, body);
        }
      }
      setDialog(null);
      setSnackbar({ severity: "success", message: "Catalog updated." });
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to save catalog record");
    } finally {
      setSubmitting(false);
    }
  }

  async function retireRow(kind: TabKey, id: string) {
    if (!auth.accessToken) return;
    setRetireTarget({ kind, id });
  }

  async function confirmRetire() {
    if (!auth.accessToken || !retireTarget) return;
    setSubmitting(true);
    try {
      if (retireTarget.kind === "capabilities") await retireCommercialCapability(auth.accessToken, retireTarget.id);
      if (retireTarget.kind === "modules") await retireCommercialModule(auth.accessToken, retireTarget.id);
      if (retireTarget.kind === "features") await retireCommercialFeature(auth.accessToken, retireTarget.id);
      if (retireTarget.kind === "limits") await retireCommercialLimit(auth.accessToken, retireTarget.id);
      if (retireTarget.kind === "addons") await retireCommercialAddon(auth.accessToken, retireTarget.id);
      setSnackbar({ severity: "success", message: "Catalog item retired." });
      setRetireTarget(null);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to retire catalog item");
    } finally {
      setSubmitting(false);
    }
  }

  function openDialog(kind: any, mode: DialogMode, id?: string) {
    setDialog({ kind, mode, id });
    setSelectedIds([]);
    setForm({});
  }

  async function loadDialogRecord(kind: Exclude<typeof dialog, null>["kind"], id: string) {
    if (!auth.accessToken) return;
    setLoading(true);
    try {
      if (kind === "capabilities") {
        const record = await getCommercialCapability(auth.accessToken, id);
        setForm({
          code: record.code,
          name: record.name,
          description: record.description || "",
          status: record.status,
          displayOrder: String(record.displayOrder),
          standaloneAllowed: String(record.standaloneAllowed),
          addonAllowed: String(record.addonAllowed),
        });
      } else if (kind === "modules") {
        const record = await getCommercialModule(auth.accessToken, id);
        setForm({
          code: record.code,
          name: record.name,
          description: record.description || "",
          status: record.status,
          displayOrder: String(record.displayOrder),
          runtimeModuleCode: record.runtimeModuleCode || "",
        });
      } else if (kind === "features") {
        const record = await getCommercialFeature(auth.accessToken, id);
        setForm({
          code: record.code,
          name: record.name,
          description: record.description || "",
          status: record.status,
          displayOrder: String(record.displayOrder),
          moduleId: record.moduleId,
          runtimeFeatureKey: record.runtimeFeatureKey || "",
        });
      } else if (kind === "limits") {
        const record = await getCommercialLimit(auth.accessToken, id);
        setForm({
          code: record.code,
          name: record.name,
          description: record.description || "",
          unit: record.unit,
          status: record.status,
          displayOrder: String(record.displayOrder),
          valueType: record.valueType,
          aggregationPeriod: record.aggregationPeriod,
          enforcementMode: record.enforcementMode,
        });
      } else if (kind === "addons") {
        const record = await getCommercialAddon(auth.accessToken, id);
        setForm({
          code: record.code,
          name: record.name,
          description: record.description || "",
          status: record.status,
          displayOrder: String(record.displayOrder),
          addonType: record.addonType,
          repeatable: String(record.repeatable),
        });
      }
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    if (dialog?.mode === "edit" && dialog.id && dialog.kind) {
      void loadDialogRecord(dialog.kind as Exclude<typeof dialog, null>["kind"], dialog.id);
    }
  }, [dialog?.id, dialog?.kind, dialog?.mode]);

  async function loadPreview(kind: TabKey, id: string) {
    if (!auth.accessToken) return;
    const cacheKey = `${kind}:${id}`;
    if (previewCache[cacheKey]) return;
    try {
      let record: RelationshipRecord | null = null;
      if (kind === "capabilities") {
        record = await getCommercialCapability(auth.accessToken, id);
      } else if (kind === "modules") {
        record = await getCommercialModule(auth.accessToken, id);
      } else if (kind === "addons") {
        record = await getCommercialAddon(auth.accessToken, id);
      }
      if (record) {
        setPreviewCache((current) => ({ ...current, [cacheKey]: record }));
      }
    } catch (previewError) {
      setError(previewError instanceof Error ? previewError.message : "Unable to load relationship preview");
    }
  }

  function togglePreview(kind: TabKey, id: string) {
    const cacheKey = `${kind}:${id}`;
    setExpandedRows((current) => ({ ...current, [cacheKey]: !current[cacheKey] }));
    void loadPreview(kind, id);
  }

  function getPreviewRecord(kind: TabKey, id: string) {
    return previewCache[`${kind}:${id}`] || null;
  }

  const currentTable = tab === "capabilities"
    ? filteredCapabilities
    : tab === "modules"
      ? filteredModules
      : tab === "features"
        ? filteredFeatures
        : tab === "limits"
          ? filteredLimits
          : filteredAddons;

  const emptyMessage = currentTable.length === 0 ? "No records match the current filters." : null;
  const relationDialogOpen = Boolean(dialog && (dialog.kind === "capability-modules" || dialog.kind === "addon-capabilities" || dialog.kind === "addon-modules" || dialog.kind === "addon-features" || dialog.kind === "addon-limits"));

  return (
    <Stack spacing={2.5}>
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: "background.paper" }}>
        <Stack spacing={1}>
          <Typography variant="overline" sx={{ letterSpacing: 1.4 }}>Platform Admin</Typography>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Commercial Catalog</Typography>
          <Typography variant="body2" color="text.secondary">
            Catalog only - not yet assigned to tenants. Batch S1 seeds the catalog foundation without changing tenant runtime behavior.
          </Typography>
        </Stack>
      </Paper>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}

      <Paper variant="outlined" sx={{ borderRadius: 2 }}>
        <Tabs value={tab} onChange={(_, value) => setSearchParams({ tab: value }, { replace: true })} variant="scrollable" scrollButtons="auto">
          <Tab value="capabilities" label="Capabilities" />
          <Tab value="modules" label="Modules" />
          <Tab value="features" label="Features" />
          <Tab value="limits" label="Limits" />
          <Tab value="addons" label="Add-ons" />
        </Tabs>
        <Box sx={{ p: 2, display: "flex", gap: 1.5, flexWrap: "wrap", alignItems: "center" }}>
          <TextField
            size="small"
            label="Search"
            value={activeFilters.search}
            onChange={(event) => setFilters((prev) => ({ ...prev, [tab]: { ...prev[tab], search: event.target.value } }))}
            sx={{ minWidth: 240 }}
          />
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>Status</InputLabel>
            <Select
              label="Status"
              value={activeFilters.status}
              onChange={(event) => setFilters((prev) => ({ ...prev, [tab]: { ...prev[tab], status: String(event.target.value) } }))}
            >
              <MenuItem value="ALL">All</MenuItem>
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="INACTIVE">Inactive</MenuItem>
              <MenuItem value="RETIRED">Retired</MenuItem>
            </Select>
          </FormControl>
          <Box sx={{ flex: 1 }} />
          <Button variant="outlined" onClick={() => void refresh()}>Refresh</Button>
          <Button variant="contained" onClick={() => openDialog(tab as any, "create")}>{tab === "capabilities" ? "Create Capability" : tab === "modules" ? "Create Module" : tab === "features" ? "Create Feature" : tab === "limits" ? "Create Limit" : "Create Add-on"}</Button>
        </Box>
        {loading ? (
          <Box sx={{ display: "grid", placeItems: "center", py: 8 }}><CircularProgress /></Box>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Code</TableCell>
                  {tab === "capabilities" ? <TableCell>Modules</TableCell> : null}
                  {tab === "modules" ? <TableCell>Runtime Module</TableCell> : null}
                  {tab === "modules" ? <TableCell>Capabilities</TableCell> : null}
                  {tab === "features" ? <TableCell>Parent Module</TableCell> : null}
                  {tab === "features" ? <TableCell>Runtime Feature</TableCell> : null}
                  {tab === "limits" ? <TableCell>Unit</TableCell> : null}
                  {tab === "limits" ? <TableCell>Value Type</TableCell> : null}
                  {tab === "limits" ? <TableCell>Period</TableCell> : null}
                  {tab === "limits" ? <TableCell>Enforcement</TableCell> : null}
                  {tab === "addons" ? <TableCell>Type</TableCell> : null}
                  {tab === "addons" ? <TableCell>Relationships</TableCell> : null}
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {currentTable.map((row: any) => (
                  <React.Fragment key={row.id}>
                    <TableRow hover>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography sx={{ fontWeight: 800 }}>{row.name}</Typography>
                          <Typography variant="caption" color="text.secondary">Code: {row.code}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell sx={{ fontFamily: "monospace" }}>{row.code}</TableCell>
                      {tab === "capabilities" ? (
                        <TableCell>
                          <Stack spacing={0.75}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.moduleCount} modules</Typography>
                            <Button size="small" onClick={() => togglePreview(tab, row.id)}>{expandedRows[`${tab}:${row.id}`] ? "Hide Preview" : "Preview"}</Button>
                          </Stack>
                        </TableCell>
                      ) : null}
                      {tab === "modules" ? <TableCell>{row.runtimeModuleCode || "—"}</TableCell> : null}
                      {tab === "modules" ? (
                        <TableCell>
                          <Stack spacing={0.75}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.capabilityCount} capabilities</Typography>
                            <Button size="small" onClick={() => togglePreview(tab, row.id)}>{expandedRows[`${tab}:${row.id}`] ? "Hide Preview" : "Preview"}</Button>
                          </Stack>
                        </TableCell>
                      ) : null}
                      {tab === "features" ? <TableCell>{row.moduleName}</TableCell> : null}
                      {tab === "features" ? <TableCell>{row.runtimeFeatureKey || "—"}</TableCell> : null}
                      {tab === "limits" ? <TableCell>{row.unit}</TableCell> : null}
                      {tab === "limits" ? <TableCell>{row.valueType}</TableCell> : null}
                      {tab === "limits" ? <TableCell>{row.aggregationPeriod}</TableCell> : null}
                      {tab === "limits" ? <TableCell>{row.enforcementMode}</TableCell> : null}
                      {tab === "addons" ? <TableCell>{row.addonType}</TableCell> : null}
                      {tab === "addons" ? (
                        <TableCell>
                          <Stack spacing={0.75}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {row.capabilityCount} capabilities, {row.moduleCount} modules, {row.featureCount} features, {row.limitIncrementCount} limit increments
                            </Typography>
                            <Button size="small" onClick={() => togglePreview(tab, row.id)}>{expandedRows[`${tab}:${row.id}`] ? "Hide Preview" : "Preview"}</Button>
                          </Stack>
                        </TableCell>
                      ) : null}
                      <TableCell><Chip size="small" label={row.status} color={statusColor(row.status) as any} /></TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                          {tab === "capabilities" && <Button size="small" onClick={() => openDialog("capability-modules", "edit", row.id)}>Manage Modules</Button>}
                          {tab === "addons" && <Button size="small" onClick={() => openDialog("addon-capabilities", "edit", row.id)}>Manage Capabilities</Button>}
                          {tab === "addons" && <Button size="small" onClick={() => openDialog("addon-modules", "edit", row.id)}>Manage Modules</Button>}
                          {tab === "addons" && <Button size="small" onClick={() => openDialog("addon-features", "edit", row.id)}>Manage Features</Button>}
                          {tab === "addons" && <Button size="small" onClick={() => openDialog("addon-limits", "edit", row.id)}>Configure Limits</Button>}
                          <Button size="small" onClick={() => openDialog(tab as any, "edit", row.id)}>Edit</Button>
                          <Button size="small" color="warning" onClick={() => void retireRow(tab, row.id)}>Retire</Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                    {(tab === "capabilities" || tab === "modules" || tab === "addons") && expandedRows[`${tab}:${row.id}`] ? (
                      <TableRow>
                        <TableCell colSpan={tab === "capabilities" ? 5 : tab === "modules" ? 6 : 6} sx={{ bgcolor: "action.hover" }}>
                          <Stack spacing={1.25} sx={{ py: 1 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Assigned relationships</Typography>
                            {tab === "capabilities" ? (
                              previewCache[`${tab}:${row.id}`] ? previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).modules?.map((item) => ({ label: item.moduleName })) || []) : <Typography variant="body2" color="text.secondary">Loading preview...</Typography>
                            ) : tab === "modules" ? (
                              previewCache[`${tab}:${row.id}`] ? previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).capabilities?.map((item) => ({ label: item.capabilityName })) || []) : <Typography variant="body2" color="text.secondary">Loading preview...</Typography>
                            ) : (
                              previewCache[`${tab}:${row.id}`] ? (
                                <Stack spacing={1}>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary">Capabilities</Typography>
                                    {previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).capabilities?.map((item) => ({ label: item.capabilityName })) || [])}
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary">Modules</Typography>
                                    {previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).modules?.map((item) => ({ label: item.moduleName })) || [])}
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary">Features</Typography>
                                    {previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).features?.map((item) => ({ label: item.featureName })) || [])}
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary">Limit increments</Typography>
                                    {previewChips((previewCache[`${tab}:${row.id}`] as RelationshipRecord).limitIncrements?.map((item) => ({ label: `${item.limitDefinitionName} × ${item.incrementValue}` })) || [])}
                                  </Box>
                                </Stack>
                              ) : <Typography variant="body2" color="text.secondary">Loading preview...</Typography>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ) : null}
                  </React.Fragment>
                ))}
                {emptyMessage ? (
                  <TableRow>
                    <TableCell colSpan={tab === "limits" ? 8 : tab === "capabilities" ? 5 : tab === "modules" ? 6 : tab === "features" ? 6 : 6}>
                      <Box sx={{ py: 4, textAlign: "center" }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>No catalog items found</Typography>
                        <Typography variant="body2" color="text.secondary">{emptyMessage}</Typography>
                      </Box>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {relationDialogOpen ? (
        <RelationshipDialog
          open={relationDialogOpen}
          token={auth.accessToken}
          dialog={dialog && (dialog.kind === "capability-modules" || dialog.kind === "addon-capabilities" || dialog.kind === "addon-modules" || dialog.kind === "addon-features" || dialog.kind === "addon-limits")
            ? { kind: dialog.kind, id: dialog.id }
            : null}
          modules={modules}
          capabilities={capabilities}
          features={features}
          limits={limits}
          onClose={() => setDialog(null)}
          onSaved={(message) => setSnackbar({ severity: "success", message })}
        />
      ) : null}

      {!relationDialogOpen ? (
        <CatalogEditDialog
          dialog={dialog}
          onClose={() => setDialog(null)}
          form={form}
          setForm={setForm}
          modules={modules}
          capabilities={capabilities}
          features={features}
          limits={limits}
          selectedIds={selectedIds}
          setSelectedIds={setSelectedIds}
          onSubmit={submitForm}
          submitting={submitting}
        />
      ) : null}

      <Dialog open={retireTarget != null} onClose={() => (submitting ? undefined : setRetireTarget(null))} fullWidth maxWidth="xs">
        <DialogTitle sx={{ fontWeight: 900 }}>Retire catalog item</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Retiring keeps the record for historical use but prevents it from being newly associated.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRetireTarget(null)} disabled={submitting}>Cancel</Button>
          <Button variant="contained" color="warning" onClick={() => void confirmRetire()} disabled={submitting}>
            Retire
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={Boolean(snackbar)} autoHideDuration={3500} onClose={() => setSnackbar(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={snackbar?.severity || "success"} variant="filled" onClose={() => setSnackbar(null)} sx={{ width: "100%" }}>
          {snackbar?.message || ""}
        </Alert>
      </Snackbar>
    </Stack>
  );
}

function CatalogEditDialog({
  dialog,
  onClose,
  form,
  setForm,
  modules,
  capabilities,
  features,
  limits,
  selectedIds,
  setSelectedIds,
  onSubmit,
  submitting,
}: {
  dialog: { kind: TabKey | "capability-modules" | "addon-capabilities" | "addon-modules" | "addon-features" | "addon-limits"; mode: DialogMode; id?: string } | null;
  onClose: () => void;
  form: FormValues;
  setForm: React.Dispatch<React.SetStateAction<FormValues>>;
  modules: CommercialModuleSummary[];
  capabilities: CommercialCapabilitySummary[];
  features: CommercialFeatureSummary[];
  limits: CommercialLimitDefinitionSummary[];
  selectedIds: string[];
  setSelectedIds: React.Dispatch<React.SetStateAction<string[]>>;
  onSubmit: () => void;
  submitting: boolean;
}) {
  if (!dialog) return null;

  const relationList =
    dialog.kind === "capability-modules" ? modules :
    dialog.kind === "addon-capabilities" ? capabilities :
    dialog.kind === "addon-modules" ? modules :
    dialog.kind === "addon-features" ? features :
    dialog.kind === "addon-limits" ? limits : [];

  React.useEffect(() => {
    if (dialog.kind === "capability-modules") {
      setSelectedIds([]);
    }
  }, [dialog.kind, setSelectedIds]);

  if (dialog.kind === "capability-modules" || dialog.kind === "addon-capabilities" || dialog.kind === "addon-modules" || dialog.kind === "addon-features" || dialog.kind === "addon-limits") {
    return (
      <BaseDialog
        open
        title="Manage Relationships"
        onClose={onClose}
        onSubmit={onSubmit}
        submitting={submitting}
      >
        <Stack spacing={2}>
          {dialog.kind === "addon-limits" ? (
            <Typography variant="body2" color="text.secondary">
              Select limits and enter an increment value for each selected row.
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary">
              Select catalog items to associate with this record. Retired items are intentionally excluded from new associations.
            </Typography>
          )}
          <Stack spacing={1} sx={{ maxHeight: 420, overflow: "auto" }}>
            {relationList.map((item: any) => (
              <Paper key={item.id} variant="outlined" sx={{ p: 1.25 }}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={selectedIds.includes(item.id)}
                        onChange={(event) => {
                          setSelectedIds((prev) => event.target.checked ? [...prev, item.id] : prev.filter((value) => value !== item.id));
                        }}
                      />
                    }
                    label={<Box><Typography variant="body2" sx={{ fontWeight: 700 }}>{item.code}</Typography><Typography variant="caption" color="text.secondary">{item.name}</Typography></Box>}
                  />
                  {dialog.kind === "addon-limits" && selectedIds.includes(item.id) ? (
                    <TextField
                      size="small"
                      label="Increment"
                      value={form[`inc-${item.id}`] || "1"}
                      onChange={(event) => setForm((prev) => ({ ...prev, [`inc-${item.id}`]: event.target.value }))}
                      sx={{ ml: "auto", maxWidth: 160 }}
                    />
                  ) : null}
                </Stack>
              </Paper>
            ))}
          </Stack>
        </Stack>
      </BaseDialog>
    );
  }

  const isCapability = dialog.kind === "capabilities";
  const isModule = dialog.kind === "modules";
  const isFeature = dialog.kind === "features";
  const isLimit = dialog.kind === "limits";
  const isAddon = dialog.kind === "addons";

  return (
    <BaseDialog
      open
      title={`${dialog.mode === "create" ? "Create" : "Edit"} ${isCapability ? "Capability" : isModule ? "Module" : isFeature ? "Feature" : isLimit ? "Limit" : "Add-on"}`}
      onClose={onClose}
      onSubmit={onSubmit}
      submitting={submitting}
    >
      <Stack spacing={2} sx={{ pt: 1 }}>
        {dialog.mode === "create" ? (
          <TextField label="Code" value={form.code || ""} onChange={(event) => setForm((prev) => ({ ...prev, code: event.target.value }))} fullWidth />
        ) : null}
        <TextField label="Name" value={form.name || ""} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} fullWidth />
        <TextField label="Description" value={form.description || ""} onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))} multiline minRows={3} fullWidth />
        <FormControl fullWidth>
          <InputLabel>Status</InputLabel>
          <Select label="Status" value={form.status || "ACTIVE"} onChange={(event) => setForm((prev) => ({ ...prev, status: String(event.target.value) }))}>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="INACTIVE">Inactive</MenuItem>
            <MenuItem value="RETIRED">Retired</MenuItem>
          </Select>
        </FormControl>
        <TextField label="Display Order" value={form.displayOrder || "0"} onChange={(event) => setForm((prev) => ({ ...prev, displayOrder: event.target.value }))} fullWidth />
        {isCapability ? (
          <>
            <FormControl fullWidth>
              <InputLabel>Standalone Allowed</InputLabel>
              <Select label="Standalone Allowed" value={form.standaloneAllowed || "true"} onChange={(event) => setForm((prev) => ({ ...prev, standaloneAllowed: String(event.target.value) }))}>
                <MenuItem value="true">Yes</MenuItem>
                <MenuItem value="false">No</MenuItem>
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Add-on Allowed</InputLabel>
              <Select label="Add-on Allowed" value={form.addonAllowed || "true"} onChange={(event) => setForm((prev) => ({ ...prev, addonAllowed: String(event.target.value) }))}>
                <MenuItem value="true">Yes</MenuItem>
                <MenuItem value="false">No</MenuItem>
              </Select>
            </FormControl>
          </>
        ) : null}
        {isModule ? (
          <TextField label="Runtime Module Code" value={form.runtimeModuleCode || ""} onChange={(event) => setForm((prev) => ({ ...prev, runtimeModuleCode: event.target.value }))} fullWidth helperText="Must match an active runtime module code when supplied." />
        ) : null}
        {isFeature ? (
          <>
            <FormControl fullWidth>
              <InputLabel>Parent Module</InputLabel>
              <Select label="Parent Module" value={form.moduleId || ""} onChange={(event) => setForm((prev) => ({ ...prev, moduleId: String(event.target.value) }))}>
                {modules.map((module) => <MenuItem key={module.id} value={module.id}>{module.code} - {module.name}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField label="Runtime Feature Key" value={form.runtimeFeatureKey || ""} onChange={(event) => setForm((prev) => ({ ...prev, runtimeFeatureKey: event.target.value }))} fullWidth />
          </>
        ) : null}
        {isLimit ? (
          <>
            <TextField label="Unit" value={form.unit || ""} onChange={(event) => setForm((prev) => ({ ...prev, unit: event.target.value }))} fullWidth />
            <FormControl fullWidth>
              <InputLabel>Value Type</InputLabel>
              <Select label="Value Type" value={form.valueType || "INTEGER"} onChange={(event) => setForm((prev) => ({ ...prev, valueType: String(event.target.value) }))}>
                <MenuItem value="INTEGER">Integer</MenuItem>
                <MenuItem value="DECIMAL">Decimal</MenuItem>
                <MenuItem value="BOOLEAN">Boolean</MenuItem>
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Aggregation Period</InputLabel>
              <Select label="Aggregation Period" value={form.aggregationPeriod || "NONE"} onChange={(event) => setForm((prev) => ({ ...prev, aggregationPeriod: String(event.target.value) }))}>
                <MenuItem value="NONE">None</MenuItem>
                <MenuItem value="DAILY">Daily</MenuItem>
                <MenuItem value="MONTHLY">Monthly</MenuItem>
                <MenuItem value="ANNUAL">Annual</MenuItem>
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Enforcement Mode</InputLabel>
              <Select label="Enforcement Mode" value={form.enforcementMode || "INFORMATIONAL"} onChange={(event) => setForm((prev) => ({ ...prev, enforcementMode: String(event.target.value) }))}>
                <MenuItem value="INFORMATIONAL">Informational</MenuItem>
                <MenuItem value="SOFT">Soft</MenuItem>
                <MenuItem value="HARD">Hard</MenuItem>
              </Select>
            </FormControl>
          </>
        ) : null}
        {isAddon ? (
          <>
            <FormControl fullWidth>
              <InputLabel>Addon Type</InputLabel>
              <Select label="Addon Type" value={form.addonType || "CAPABILITY"} onChange={(event) => setForm((prev) => ({ ...prev, addonType: String(event.target.value) }))}>
                <MenuItem value="CAPABILITY">Capability</MenuItem>
                <MenuItem value="FEATURE">Feature</MenuItem>
                <MenuItem value="LIMIT_PACK">Limit Pack</MenuItem>
                <MenuItem value="SERVICE">Service</MenuItem>
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Repeatable</InputLabel>
              <Select label="Repeatable" value={form.repeatable || "false"} onChange={(event) => setForm((prev) => ({ ...prev, repeatable: String(event.target.value) }))}>
                <MenuItem value="false">No</MenuItem>
                <MenuItem value="true">Yes</MenuItem>
              </Select>
            </FormControl>
          </>
        ) : null}
      </Stack>
    </BaseDialog>
  );
}
