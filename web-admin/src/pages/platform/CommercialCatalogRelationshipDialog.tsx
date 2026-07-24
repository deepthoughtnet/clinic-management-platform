import * as React from "react";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import {
  getCommercialAddon,
  getCommercialCapability,
  listCommercialAddons,
  listCommercialCapabilities,
  listCommercialFeatures,
  listCommercialLimits,
  listCommercialModules,
  type CommercialAddonDetail,
  type CommercialAddonSummary,
  type CommercialCapabilityDetail,
  type CommercialCapabilitySummary,
  type CommercialFeatureSummary,
  type CommercialLimitDefinitionSummary,
  type CommercialModuleSummary,
  updateCommercialAddonCapabilities,
  updateCommercialAddonFeatures,
  updateCommercialAddonLimitIncrements,
  updateCommercialAddonModules,
  updateCommercialCapabilityModules,
} from "../../api/clinicApi";

export type RelationshipDialogKind = "capability-modules" | "addon-capabilities" | "addon-modules" | "addon-features" | "addon-limits";

type RelationshipDialogProps = {
  open: boolean;
  token: string | null;
  dialog: { kind: RelationshipDialogKind; id?: string } | null;
  modules: CommercialModuleSummary[];
  capabilities: CommercialCapabilitySummary[];
  features: CommercialFeatureSummary[];
  limits: CommercialLimitDefinitionSummary[];
  onClose: () => void;
  onSaved: (message: string) => void;
};

type ParentRecord = CommercialCapabilityDetail | CommercialAddonDetail | null;
type FormState = Record<string, string>;

const KIND_COPY: Record<RelationshipDialogKind, {
  title: string;
  parentLabel: string;
  noun: string;
  placeholder: string;
  intro: string;
  saveLabel: string;
  selectionLabel: string;
  empty: string;
}> = {
  "capability-modules": {
    title: "Assign Modules",
    parentLabel: "Capability",
    noun: "modules",
    placeholder: "Search modules",
    intro: "Select the application modules included in this capability.",
    saveLabel: "Save Changes",
    selectionLabel: "selected",
    empty: "No modules match your search.",
  },
  "addon-capabilities": {
    title: "Assign Capabilities",
    parentLabel: "Add-on",
    noun: "capabilities",
    placeholder: "Search capabilities",
    intro: "Select the capabilities granted by this add-on.",
    saveLabel: "Save Changes",
    selectionLabel: "selected",
    empty: "No capabilities match your search.",
  },
  "addon-modules": {
    title: "Assign Modules",
    parentLabel: "Add-on",
    noun: "modules",
    placeholder: "Search modules",
    intro: "Select the application modules granted by this add-on.",
    saveLabel: "Save Changes",
    selectionLabel: "selected",
    empty: "No modules match your search.",
  },
  "addon-features": {
    title: "Assign Features",
    parentLabel: "Add-on",
    noun: "features",
    placeholder: "Search features",
    intro: "Select the features granted by this add-on.",
    saveLabel: "Save Changes",
    selectionLabel: "selected",
    empty: "No features match your search.",
  },
  "addon-limits": {
    title: "Configure Limit Increments",
    parentLabel: "Add-on",
    noun: "limits",
    placeholder: "Search limits",
    intro: "Select limit definitions and enter the increment granted by this add-on.",
    saveLabel: "Save Changes",
    selectionLabel: "configured",
    empty: "No limits match your search.",
  },
};

const MODULE_GROUPS: Array<{ label: string; codes: string[] }> = [
  { label: "Core Clinical", codes: ["PATIENTS", "APPOINTMENTS", "CONSULTATION", "PRESCRIPTION"] },
  { label: "Operations", codes: ["BILLING", "REPORTS", "NOTIFICATIONS", "VACCINATION"] },
  { label: "Laboratory", codes: ["LABORATORY"] },
  { label: "Pharmacy", codes: ["INVENTORY", "PHARMACY_POS", "PHARMACY"] },
  { label: "AI", codes: ["AI_COPILOT"] },
  { label: "Engage", codes: ["CAREPILOT"] },
];

function groupLabel(code: string) {
  return MODULE_GROUPS.find((group) => group.codes.includes(code))?.label || "Other";
}

function sortByDisplayOrderThenNameCode<T extends { displayOrder?: number; name: string; code: string }>(left: T, right: T) {
  return (left.displayOrder ?? 0) - (right.displayOrder ?? 0) || left.name.localeCompare(right.name) || left.code.localeCompare(right.code);
}

function selectedIdsEqual(left: string[], right: string[]) {
  if (left.length !== right.length) return false;
  const leftSorted = [...left].sort();
  const rightSorted = [...right].sort();
  return leftSorted.every((value, index) => value === rightSorted[index]);
}

function sameRecord(left: FormState, right: FormState) {
  const leftKeys = Object.keys(left).sort();
  const rightKeys = Object.keys(right).sort();
  if (leftKeys.length !== rightKeys.length) return false;
  return leftKeys.every((key, index) => key === rightKeys[index] && left[key] === right[key]);
}

function includesSearch(values: Array<string | null | undefined>, search: string) {
  if (!search) return true;
  const needle = search.toLowerCase();
  return values.some((value) => (value || "").toLowerCase().includes(needle));
}

function selectedSummary(kind: RelationshipDialogKind, selectedCount: number, totalCount: number) {
  const noun = KIND_COPY[kind].noun;
  if (kind === "addon-limits") {
    return totalCount ? `${selectedCount} of ${totalCount} ${noun} ${KIND_COPY[kind].selectionLabel}` : `${selectedCount} ${KIND_COPY[kind].selectionLabel}`;
  }
  return totalCount ? `${selectedCount} of ${totalCount} ${noun} ${KIND_COPY[kind].selectionLabel}` : `${selectedCount} ${KIND_COPY[kind].selectionLabel}`;
}

function selectedCountLabel(kind: RelationshipDialogKind, selectedCount: number, totalCount: number) {
  return selectedSummary(kind, selectedCount, totalCount);
}

function previewChips(items: Array<{ label: string }>) {
  const visible = items.slice(0, 3);
  const more = items.length - visible.length;
  return (
    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
      {visible.map((item) => <Chip key={item.label} size="small" variant="outlined" label={item.label} />)}
      {more > 0 ? <Chip size="small" variant="outlined" label={`+${more} more`} /> : null}
    </Stack>
  );
}

export function RelationshipDialog({
  open,
  token,
  dialog,
  modules,
  capabilities,
  features,
  limits,
  onClose,
  onSaved,
}: RelationshipDialogProps) {
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [parent, setParent] = React.useState<ParentRecord>(null);
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [initialSelectedIds, setInitialSelectedIds] = React.useState<string[]>([]);
  const [incrementDrafts, setIncrementDrafts] = React.useState<FormState>({});
  const [initialIncrementDrafts, setInitialIncrementDrafts] = React.useState<FormState>({});
  const [discardOpen, setDiscardOpen] = React.useState(false);

  const kind = dialog?.kind;
  const copy = kind ? KIND_COPY[kind] : null;

  const selectableItems = React.useMemo(() => {
    if (!kind) return [];
    const base = (kind === "capability-modules" ? modules
      : kind === "addon-capabilities" ? capabilities
        : kind === "addon-modules" ? modules
          : kind === "addon-features" ? features
            : limits) as any[];
    return base.filter((item) => item.status !== "RETIRED");
  }, [capabilities, features, kind, limits, modules]);

  const filteredItems = React.useMemo(() => {
    const base = (selectableItems.filter((item) => {
      const values = kind === "capability-modules" || kind === "addon-modules"
        ? [item.code, item.name, item.description || "", item.runtimeModuleCode || ""]
        : kind === "addon-features"
          ? [item.code, item.name, item.description || "", item.moduleCode || ""]
          : kind === "addon-limits"
            ? [item.code, item.name, item.description || "", item.unit || ""]
            : [item.code, item.name, item.description || ""];
      return includesSearch(values, search);
    }) as any[]).sort(sortByDisplayOrderThenNameCode);
    return base;
  }, [kind, search, selectableItems]);

  const groupedItems = React.useMemo(() => {
    if (kind !== "capability-modules" && kind !== "addon-modules") {
      return [{ label: null as string | null, items: filteredItems }];
    }
    const groups = new Map<string, Array<(typeof filteredItems)[number]>>();
    filteredItems.forEach((item) => {
      const label = groupLabel(kind === "capability-modules" ? (item.runtimeModuleCode || item.code) : (item.runtimeModuleCode || item.code));
      const current = groups.get(label) || [];
      current.push(item);
      groups.set(label, current);
    });
    const ordered = ["Core Clinical", "Operations", "Laboratory", "Pharmacy", "AI", "Engage", "Other"];
    return ordered
      .map((label) => ({ label, items: groups.get(label) || [] }))
      .filter((group) => group.items.length > 0);
  }, [filteredItems, kind]);

  const dirty = React.useMemo(() => {
    if (kind === "addon-limits") {
      return !selectedIdsEqual(selectedIds, initialSelectedIds) || !sameRecord(incrementDrafts, initialIncrementDrafts);
    }
    return !selectedIdsEqual(selectedIds, initialSelectedIds);
  }, [initialIncrementDrafts, initialSelectedIds, incrementDrafts, kind, selectedIds]);

  React.useEffect(() => {
    if (!open || !dialog || !token) return;
    const activeDialog = dialog!;
    const accessToken = token;
    setLoading(true);
    setError(null);
    setSearch("");
    setDiscardOpen(false);
    setSelectedIds([]);
    setInitialSelectedIds([]);
    setIncrementDrafts({});
    setInitialIncrementDrafts({});
    setParent(null);

    async function load() {
      try {
        if (activeDialog.kind === "capability-modules") {
          const record = await getCommercialCapability(accessToken, activeDialog.id!);
          const ids = record.modules.map((module) => module.moduleId);
          setParent(record);
          setSelectedIds(ids);
          setInitialSelectedIds(ids);
        } else {
          const record = await getCommercialAddon(accessToken, activeDialog.id!);
          setParent(record);
          if (activeDialog.kind === "addon-capabilities") {
            const ids = record.capabilities.map((item) => item.capabilityId);
            setSelectedIds(ids);
            setInitialSelectedIds(ids);
          } else if (activeDialog.kind === "addon-modules") {
            const ids = record.modules.map((item) => item.moduleId);
            setSelectedIds(ids);
            setInitialSelectedIds(ids);
          } else if (activeDialog.kind === "addon-features") {
            const ids = record.features.map((item) => item.featureId);
            setSelectedIds(ids);
            setInitialSelectedIds(ids);
          } else if (activeDialog.kind === "addon-limits") {
            const ids = record.limitIncrements.map((item) => item.limitDefinitionId);
            const draft = Object.fromEntries(record.limitIncrements.map((item) => [item.limitDefinitionId, item.incrementValue]));
            setSelectedIds(ids);
            setInitialSelectedIds(ids);
            setIncrementDrafts(draft);
            setInitialIncrementDrafts(draft);
          }
        }
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : "Unable to load relationship data");
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, [dialog, open, token]);

  if (!open || !dialog || !copy) return null;
  const activeDialog = dialog!;
  const accessToken = token!;

  const header = {
    title: copy.title,
    parentLabel: copy.parentLabel,
    parentName: parent?.name || "Loading...",
    parentCode: parent?.code || "—",
  };

  async function save() {
    if (!accessToken || !activeDialog || !parent) return;
    setSaving(true);
    setError(null);
    try {
      if (activeDialog.kind === "capability-modules") {
        await updateCommercialCapabilityModules(accessToken, activeDialog.id!, {
          modules: selectedIds.map((moduleId, index) => ({ moduleId, includedByDefault: true, displayOrder: index + 1 })),
        });
        onSaved(`Modules updated for ${parent.name}.`);
      } else if (activeDialog.kind === "addon-capabilities") {
        await updateCommercialAddonCapabilities(accessToken, activeDialog.id!, { capabilityIds: selectedIds });
        onSaved(`Capabilities updated for ${parent.name}.`);
      } else if (activeDialog.kind === "addon-modules") {
        await updateCommercialAddonModules(accessToken, activeDialog.id!, { moduleIds: selectedIds });
        onSaved(`Modules updated for ${parent.name}.`);
      } else if (activeDialog.kind === "addon-features") {
        await updateCommercialAddonFeatures(accessToken, activeDialog.id!, { featureIds: selectedIds });
        onSaved(`Features updated for ${parent.name}.`);
      } else if (activeDialog.kind === "addon-limits") {
        await updateCommercialAddonLimitIncrements(accessToken, activeDialog.id!, {
          limitIncrements: selectedIds.map((limitDefinitionId) => ({
            limitDefinitionId,
            incrementValue: incrementDrafts[limitDefinitionId] || "1",
          })),
        });
        onSaved(`Limit increments updated for ${parent.name}.`);
      }
      onClose();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save relationship changes");
    } finally {
      setSaving(false);
    }
  }

  function requestClose() {
    if (dirty) {
      setDiscardOpen(true);
      return;
    }
    onClose();
  }

  function toggleSelection(id: string) {
    setSelectedIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  }

  function toggleLimitSelection(id: string) {
    setSelectedIds((current) => {
      if (current.includes(id)) {
        setIncrementDrafts((drafts) => {
          const next = { ...drafts };
          delete next[id];
          return next;
        });
        return current.filter((value) => value !== id);
      }
      setIncrementDrafts((drafts) => ({ ...drafts, [id]: drafts[id] || "1" }));
      return [...current, id];
    });
  }

  function isSelected(id: string) {
    return selectedIds.includes(id);
  }

  const selectableCount = filteredItems.length;
  const summaryLabel = selectedCountLabel(activeDialog.kind, selectedIds.length, selectableCount);

  return (
    <>
      <Dialog
        open
        onClose={(_, reason) => {
          if (reason === "backdropClick" || reason === "escapeKeyDown") {
            requestClose();
            return;
          }
          requestClose();
        }}
        fullWidth
        maxWidth={false}
        PaperProps={{
          sx: {
            width: { xs: "calc(100vw - 24px)", sm: "min(1120px, calc(100vw - 48px))" },
            maxWidth: "1120px",
            height: "min(90vh, 920px)",
            overflow: "hidden",
          },
        }}
      >
        <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2, borderBottom: 1, borderColor: "divider", flexShrink: 0 }}>
            <Typography variant="overline" sx={{ letterSpacing: 1.2, display: "block" }}>
              {header.parentLabel}
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              {header.title}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {header.parentName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
              Code: {header.parentCode}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
              {copy.intro}
            </Typography>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1.5, flexWrap: "wrap" }}>
              <Chip label={summaryLabel} size="small" />
              <Box sx={{ flex: 1 }} />
              <TextField
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                size="small"
                placeholder={copy.placeholder}
                autoFocus
                sx={{ minWidth: { xs: "100%", sm: 260 } }}
                inputProps={{ "aria-label": copy.placeholder }}
              />
            </Stack>
          </Box>

          <Box sx={{ flex: 1, overflowY: "auto", px: { xs: 2, sm: 3 }, py: 2 }}>
            {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 280 }}>
                <CircularProgress />
              </Box>
            ) : (
              <Stack spacing={2}>
                {groupedItems.map((group) => (
                  <Stack key={group.label || "all"} spacing={1}>
                    {group.label ? (
                      <Typography variant="overline" sx={{ letterSpacing: 1.1, color: "text.secondary" }}>
                        {group.label}
                      </Typography>
                    ) : null}
                    <Stack spacing={1}>
                      {group.items.map((item) => {
                        const selected = isSelected(item.id);
                        const disabled = item.status === "RETIRED";
                        return (
                          <Paper
                            key={item.id}
                            variant="outlined"
                            sx={{
                              borderColor: selected ? "primary.main" : "divider",
                              bgcolor: selected ? "action.selected" : "background.paper",
                              opacity: disabled ? 0.64 : 1,
                              overflow: "hidden",
                            }}
                          >
                            <ButtonBase
                              onClick={() => {
                                if (!disabled) {
                                  if (activeDialog.kind === "addon-limits") {
                                    toggleLimitSelection(item.id);
                                  } else {
                                    toggleSelection(item.id);
                                  }
                                }
                              }}
                              disabled={disabled}
                              sx={{ width: "100%", textAlign: "left", p: 1.5, display: "block" }}
                            >
                              <Stack direction="row" spacing={1.5} alignItems="flex-start">
                                <Checkbox
                                  checked={selected}
                                  disabled={disabled}
                                  tabIndex={-1}
                                  onClick={(event) => event.stopPropagation()}
                                  onChange={() => {
                                    if (activeDialog.kind === "addon-limits") {
                                      toggleLimitSelection(item.id);
                                    } else {
                                      toggleSelection(item.id);
                                    }
                                  }}
                                />
                                <Box sx={{ minWidth: 0, flex: 1 }}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                    {item.name}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    Code: {item.code}
                                  </Typography>
                                  {"description" in item && item.description ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      {item.description}
                                    </Typography>
                                  ) : null}
                                  {activeDialog.kind === "capability-modules" || activeDialog.kind === "addon-modules" ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      {item.runtimeModuleCode ? `Runtime module: ${item.runtimeModuleCode}` : null}
                                    </Typography>
                                  ) : null}
                                  {activeDialog.kind === "addon-features" ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      Parent module: {item.moduleName} ({item.moduleCode})
                                    </Typography>
                                  ) : null}
                                  {activeDialog.kind === "addon-limits" ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      {item.unit} · {item.valueType} · {item.aggregationPeriod}
                                    </Typography>
                                  ) : null}
                                </Box>
                                <Chip size="small" label={selected ? "Selected" : "Available"} color={selected ? "primary" : "default"} />
                                {disabled ? <Chip size="small" label="Retired" color="default" /> : null}
                              </Stack>
                            </ButtonBase>
                            {activeDialog.kind === "addon-limits" && selected ? (
                              <Box sx={{ px: 1.5, pb: 1.5, pt: 0 }}>
                                <TextField
                                  size="small"
                                  label="Increment value"
                                  value={incrementDrafts[item.id] || "1"}
                                  onChange={(event) => setIncrementDrafts((current) => ({ ...current, [item.id]: event.target.value }))}
                                  sx={{ maxWidth: 200 }}
                                />
                              </Box>
                            ) : null}
                          </Paper>
                        );
                      })}
                    </Stack>
                  </Stack>
                ))}
                {filteredItems.length === 0 ? (
                  <Alert severity="info">{copy.empty}</Alert>
                ) : null}
              </Stack>
            )}
          </Box>

          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2, borderTop: 1, borderColor: "divider", flexShrink: 0, bgcolor: "background.paper" }}>
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={requestClose} disabled={saving}>Cancel</Button>
              <Button variant="contained" onClick={() => void save()} disabled={saving || !dirty}>
                {saving ? "Saving..." : copy.saveLabel}
              </Button>
            </Stack>
          </Box>
        </Box>
      </Dialog>

      <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Discard relationship changes?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Your unsaved {copy.noun} selections will be lost.
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
            Discard Changes
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
