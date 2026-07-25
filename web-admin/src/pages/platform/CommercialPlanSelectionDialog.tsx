import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { CommercialPlanSelectionState } from "../../api/clinicApi";

type SelectionItem = {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  displayOrder?: number;
  runtimeModuleCode?: string | null;
  moduleCode?: string | null;
  moduleName?: string | null;
  unit?: string | null;
  valueType?: string | null;
  aggregationPeriod?: string | null;
  enforcementMode?: string | null;
  addonType?: string | null;
  status?: string | null;
};

type DialogKind = "capabilities" | "modules" | "features" | "limits" | "addons";

const EMPTY_LIMIT_VALUES: Record<string, string> = {};
const EMPTY_ADDON_SELECTION_STATES: Record<string, CommercialPlanSelectionState> = {};

type Props = {
  open: boolean;
  kind: DialogKind;
  title: string;
  parentName: string;
  parentCode: string;
  items: SelectionItem[];
  selectedIds: string[];
  limitValues?: Record<string, string>;
  addonSelectionStates?: Record<string, CommercialPlanSelectionState>;
  onClose: () => void;
  onSave: (selectedIds: string[], limitValues?: Record<string, string>, addonSelectionStates?: Record<string, CommercialPlanSelectionState>) => Promise<boolean> | boolean;
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

function includesSearch(values: Array<string | null | undefined>, search: string) {
  if (!search) return true;
  const needle = search.toLowerCase();
  return values.some((value) => (value || "").toLowerCase().includes(needle));
}

function sortByDisplayOrderThenNameCode<T extends { displayOrder?: number; name: string; code: string }>(left: T, right: T) {
  return (left.displayOrder ?? 0) - (right.displayOrder ?? 0) || left.name.localeCompare(right.name) || left.code.localeCompare(right.code);
}

function sameSet(left: string[], right: string[]) {
  if (left.length !== right.length) return false;
  return [...left].sort().every((value, index) => value === [...right].sort()[index]);
}

function sameRecord(left: Record<string, string>, right: Record<string, string>) {
  const leftKeys = Object.keys(left).sort();
  const rightKeys = Object.keys(right).sort();
  if (!sameSet(leftKeys, rightKeys)) return false;
  return leftKeys.every((key) => left[key] === right[key]);
}

function sameAddonStates(left: Record<string, CommercialPlanSelectionState>, right: Record<string, CommercialPlanSelectionState>) {
  const leftKeys = Object.keys(left).sort();
  const rightKeys = Object.keys(right).sort();
  if (!sameSet(leftKeys, rightKeys)) return false;
  return leftKeys.every((key) => left[key] === right[key]);
}

export function filterSelectionItems(items: SelectionItem[], kind: DialogKind, search: string) {
  return [...items]
    .filter((item) => item.status !== "RETIRED")
    .filter((item) => {
      const values = kind === "modules"
        ? [item.name, item.code, item.description || "", item.runtimeModuleCode || ""]
        : kind === "features"
          ? [item.name, item.code, item.description || "", item.moduleCode || ""]
          : kind === "limits"
            ? [item.name, item.code, item.description || "", item.unit || ""]
            : [item.name, item.code, item.description || ""];
      return includesSearch(values, search);
    })
    .sort(sortByDisplayOrderThenNameCode);
}

export function selectedSelectionItems(items: SelectionItem[], selectedIds: string[]) {
  return selectedIds
    .map((id) => items.find((item) => item.id === id))
    .filter((item): item is SelectionItem => Boolean(item));
}

export function toggleSelectedIds(current: string[], id: string) {
  return current.includes(id) ? current.filter((value) => value !== id) : [...current, id];
}

export function computeConfiguredCount(
  kind: DialogKind,
  selected: string[],
  limitValues: Record<string, string>,
  addonSelectionStates: Record<string, CommercialPlanSelectionState>,
) {
  if (kind === "limits") {
    return Object.keys(limitValues).filter((key) => limitValues[key] && selected.includes(key)).length;
  }
  if (kind === "addons") {
    return Object.values(addonSelectionStates).filter((value) => value !== "UNAVAILABLE").length;
  }
  return selected.length;
}

export function isDialogDirty(
  kind: DialogKind,
  selected: string[],
  selectedIds: string[],
  draftValues: Record<string, string>,
  limitValues: Record<string, string>,
  draftAddonStates: Record<string, CommercialPlanSelectionState>,
  addonSelectionStates: Record<string, CommercialPlanSelectionState>,
) {
  if (kind === "limits") {
    return !sameSet(selected, selectedIds) || !sameRecord(draftValues, limitValues);
  }
  if (kind === "addons") {
    return !sameAddonStates(draftAddonStates, addonSelectionStates) || !sameSet(selected, selectedIds);
  }
  return !sameSet(selected, selectedIds);
}

export default function CommercialPlanSelectionDialog({
  open,
  kind,
  title,
  parentName,
  parentCode,
  items,
  selectedIds,
  limitValues = {},
  addonSelectionStates = {},
  onClose,
  onSave,
}: Props) {
  const [search, setSearch] = React.useState("");
  const [selected, setSelected] = React.useState<string[]>([]);
  const [draftValues, setDraftValues] = React.useState<Record<string, string>>({});
  const [draftAddonStates, setDraftAddonStates] = React.useState<Record<string, CommercialPlanSelectionState>>({});
  const [discardOpen, setDiscardOpen] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const resolvedLimitValues = kind === "limits" ? (limitValues ?? EMPTY_LIMIT_VALUES) : EMPTY_LIMIT_VALUES;
  const resolvedAddonSelectionStates = kind === "addons" ? (addonSelectionStates ?? EMPTY_ADDON_SELECTION_STATES) : EMPTY_ADDON_SELECTION_STATES;

  React.useEffect(() => {
    if (!open) return;
    setSearch("");
    setSelected(selectedIds);
    setDraftValues(resolvedLimitValues);
    setDraftAddonStates(resolvedAddonSelectionStates);
    setDiscardOpen(false);
  }, [open, selectedIds, resolvedLimitValues, resolvedAddonSelectionStates]);

  const filteredItems = React.useMemo(() => filterSelectionItems(items, kind, search), [items, kind, search]);

  const groupedItems = React.useMemo(() => {
    if (kind !== "modules") {
      return [{ label: null as string | null, items: filteredItems }];
    }
    const groups = new Map<string, SelectionItem[]>();
    filteredItems.forEach((item) => {
      const label = groupLabel(item.runtimeModuleCode || item.code);
      const current = groups.get(label) || [];
      current.push(item);
      groups.set(label, current);
    });
    return ["Core Clinical", "Operations", "Laboratory", "Pharmacy", "AI", "Engage", "Other"]
      .map((label) => ({ label, items: groups.get(label) || [] }))
      .filter((group) => group.items.length > 0);
  }, [filteredItems, kind]);

  const summaryItems = React.useMemo(() => selectedSelectionItems(items, selected), [items, selected]);
  const selectedCount = summaryItems.length;
  const dirty = isDialogDirty(kind, selected, selectedIds, draftValues, resolvedLimitValues, draftAddonStates, resolvedAddonSelectionStates);

  function toggle(id: string) {
    setSelected((current) => toggleSelectedIds(current, id));
    if (kind === "limits") {
      setDraftValues((current) => current[id] ? current : { ...current, [id]: "1" });
    }
  }

  function addonStateFor(id: string): CommercialPlanSelectionState {
    return draftAddonStates[id] || "UNAVAILABLE";
  }

  function cycleAddonState(id: string) {
    setDraftAddonStates((current): Record<string, CommercialPlanSelectionState> => {
      const state = current[id] || "UNAVAILABLE";
      const next = state === "UNAVAILABLE" ? "AVAILABLE" : state === "AVAILABLE" ? "INCLUDED" : "UNAVAILABLE";
      const nextStates: Record<string, CommercialPlanSelectionState> = { ...current, [id]: next };
      setSelected(Object.keys(nextStates).filter((key) => nextStates[key] !== "UNAVAILABLE"));
      return nextStates;
    });
  }

  function setAddonState(id: string, next: CommercialPlanSelectionState) {
    setDraftAddonStates((current): Record<string, CommercialPlanSelectionState> => {
      const nextStates: Record<string, CommercialPlanSelectionState> = { ...current, [id]: next };
      setSelected(Object.keys(nextStates).filter((key) => nextStates[key] !== "UNAVAILABLE"));
      return nextStates;
    });
  }

  function requestClose() {
    if (dirty) {
      setDiscardOpen(true);
      return;
    }
    onClose();
  }

  async function save() {
    setSaving(true);
    try {
      const saved = await Promise.resolve(onSave(selected, kind === "limits" ? draftValues : undefined, kind === "addons" ? draftAddonStates : undefined));
      if (saved) {
        onClose();
      }
    } catch {
      // The parent save handler surfaces errors through the shared page error state.
    } finally {
      setSaving(false);
    }
  }

  function toggleKindSelection(item: SelectionItem) {
    if (kind === "addons") {
      cycleAddonState(item.id);
      return;
    }
    toggle(item.id);
  }

  function removeSelection(item: SelectionItem) {
    if (kind === "limits") {
      setDraftValues((current) => {
        const next = { ...current };
        delete next[item.id];
        return next;
      });
    }
    if (kind === "addons") {
      setDraftAddonStates((current) => ({ ...current, [item.id]: "UNAVAILABLE" }));
    }
    setSelected((current) => current.filter((value) => value !== item.id));
  }

  if (!open) return null;

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
              {kind === "addons" ? "Plan add-ons" : kind}
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              {title}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {parentName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
              Code: {parentCode}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
              Search and update the plan configuration for this catalog record.
            </Typography>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1.5, flexWrap: "wrap" }}>
              <Chip label={`Selected (${selectedCount})`} size="small" />
              <Box sx={{ flex: 1 }} />
              <TextField
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                size="small"
                placeholder={`Search ${kind}`}
                autoFocus
                sx={{ minWidth: { xs: "100%", sm: 260 } }}
              />
            </Stack>
            {summaryItems.length > 0 ? (
              <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mt: 1.5, flexWrap: "wrap" }} useFlexGap>
                {summaryItems.map((item) => (
                  <Chip
                    key={item.id}
                    size="small"
                    label={item.name}
                    variant="outlined"
                    onDelete={() => removeSelection(item)}
                  />
                ))}
              </Stack>
            ) : null}
          </Box>

          <Box sx={{ flex: 1, overflowY: "auto", px: { xs: 2, sm: 3 }, py: 2 }}>
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
                      const checked = selected.includes(item.id);
                      const disabled = item.status === "RETIRED";
                      return (
                        <Paper
                          key={item.id}
                          variant="outlined"
                          sx={{
                            borderColor: checked ? "primary.main" : "divider",
                            bgcolor: checked ? "action.selected" : "background.paper",
                            opacity: disabled ? 0.64 : 1,
                            overflow: "hidden",
                            boxShadow: checked ? 2 : 0,
                            transition: "border-color 120ms ease, background-color 120ms ease, box-shadow 120ms ease",
                          }}
                        >
                          <Box
                            component="button"
                            type="button"
                            aria-pressed={checked}
                            disabled={disabled}
                            onClick={() => toggleKindSelection(item)}
                            sx={{
                              appearance: "none",
                              width: "100%",
                              textAlign: "left",
                              display: "block",
                              p: 0,
                              border: 0,
                              color: "inherit",
                              background: "transparent",
                              cursor: disabled ? "not-allowed" : "pointer",
                            }}
                          >
                            <Box sx={{ p: 1.5 }}>
                              <Stack direction="row" spacing={1.5} alignItems="flex-start">
                                <Box sx={{ minWidth: 0, flex: 1 }}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                                    {item.name}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    Code: {item.code}
                                  </Typography>
                                  {item.description ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      {item.description}
                                    </Typography>
                                  ) : null}
                                  {item.runtimeModuleCode ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      Runtime module: {item.runtimeModuleCode}
                                    </Typography>
                                  ) : null}
                                  {item.moduleCode ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      Parent module: {item.moduleCode}
                                    </Typography>
                                  ) : null}
                                  {kind === "features" && item.moduleCode ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      Parent module: {item.moduleName || item.moduleCode}
                                    </Typography>
                                  ) : null}
                                  {item.unit ? (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>
                                      {item.unit} · {item.valueType} · {item.aggregationPeriod}
                                    </Typography>
                                  ) : null}
                                </Box>
                                <Stack direction="row" spacing={0.75} alignItems="flex-start" flexWrap="wrap" justifyContent="flex-end">
                                  <Chip size="small" label={item.status || "ACTIVE"} variant="outlined" />
                                  <Chip size="small" label={checked ? "Selected" : "Available"} color={checked ? "primary" : "default"} />
                                  {disabled ? <Chip size="small" label="Retired" /> : null}
                                </Stack>
                              </Stack>
                            </Box>
                          </Box>
                          {kind === "limits" && checked ? (
                            <Box sx={{ px: 1.5, pb: 1.5, pt: 0 }} onClick={(event) => event.stopPropagation()}>
                              <TextField
                                size="small"
                                label="Configured value"
                                value={draftValues[item.id] ?? "1"}
                                onChange={(event) => setDraftValues((current) => ({ ...current, [item.id]: event.target.value }))}
                                sx={{ maxWidth: 220 }}
                              />
                            </Box>
                          ) : null}
                          {kind === "addons" ? (
                            <Box sx={{ px: 1.5, pb: 1.5, pt: 0 }} onClick={(event) => event.stopPropagation()}>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Button size="small" variant={addonStateFor(item.id) === "INCLUDED" ? "contained" : "outlined"} onClick={(event) => { event.stopPropagation(); setAddonState(item.id, "INCLUDED"); }}>
                                  Included
                                </Button>
                                <Button size="small" variant={addonStateFor(item.id) === "AVAILABLE" ? "contained" : "outlined"} onClick={(event) => { event.stopPropagation(); setAddonState(item.id, "AVAILABLE"); }}>
                                  Available for Purchase
                                </Button>
                                <Button size="small" variant={addonStateFor(item.id) === "UNAVAILABLE" ? "contained" : "outlined"} onClick={(event) => { event.stopPropagation(); setAddonState(item.id, "UNAVAILABLE"); }}>
                                  Unavailable
                                </Button>
                              </Stack>
                            </Box>
                          ) : null}
                        </Paper>
                      );
                    })}
                  </Stack>
                </Stack>
              ))}
              {filteredItems.length === 0 ? <Alert severity="info">No {kind} match your search.</Alert> : null}
            </Stack>
          </Box>

          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2, borderTop: 1, borderColor: "divider", flexShrink: 0, bgcolor: "background.paper" }}>
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={requestClose} disabled={saving}>Cancel</Button>
              <Button variant="contained" onClick={() => void save()} disabled={!dirty || saving}>
                Save Changes
              </Button>
            </Stack>
          </Box>
        </Box>
      </Dialog>

      <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Discard changes?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Your unsaved {kind} selections will be lost.
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
