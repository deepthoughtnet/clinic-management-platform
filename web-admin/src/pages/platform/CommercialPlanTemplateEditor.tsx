import * as React from "react";
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { CommercialPlanTargetSegment, CommercialPlanTemplateStatus, CommercialPlanValidationResult } from "../../api/clinicApi";

export type CommercialPlanTemplateFormValue = {
  name: string;
  code: string;
  description: string;
  targetSegment: CommercialPlanTargetSegment | "";
  displayOrder: string;
  status: CommercialPlanTemplateStatus;
};

export type CommercialPlanTemplateFormErrors = Partial<Record<keyof CommercialPlanTemplateFormValue, string>>;

type FieldsProps = {
  values: CommercialPlanTemplateFormValue;
  errors?: CommercialPlanTemplateFormErrors;
  onChange: (next: CommercialPlanTemplateFormValue) => void;
  codeReadOnly?: boolean;
  showStatus?: boolean;
};

export const TEMPLATE_TARGET_SEGMENTS: Array<{ value: CommercialPlanTargetSegment; label: string }> = [
  { value: "SOLO", label: "Solo" },
  { value: "SMALL_CLINIC", label: "Small Clinic" },
  { value: "MULTI_DOCTOR_CLINIC", label: "Multi Doctor Clinic" },
  { value: "SPECIALITY_CLINIC", label: "Speciality Clinic" },
  { value: "DIAGNOSTIC_CENTER", label: "Diagnostic Center" },
  { value: "PHARMACY", label: "Pharmacy" },
  { value: "ENTERPRISE", label: "Enterprise" },
  { value: "CUSTOM", label: "Custom" },
];

export const TEMPLATE_STATUSES: Array<{ value: CommercialPlanTemplateStatus; label: string }> = [
  { value: "DRAFT", label: "Draft" },
  { value: "ACTIVE", label: "Active" },
  { value: "RETIRED", label: "Retired" },
];

export function normalizeTemplateCode(value: string) {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "");
}

export function defaultTemplateForm(): CommercialPlanTemplateFormValue {
  return {
    name: "",
    code: "",
    description: "",
    targetSegment: "",
    displayOrder: "0",
    status: "DRAFT",
  };
}

export function templateFormFromDetail(detail: {
  name: string;
  code: string;
  description: string | null;
  targetSegment: CommercialPlanTargetSegment;
  status: CommercialPlanTemplateStatus;
  displayOrder: number;
}): CommercialPlanTemplateFormValue {
  return {
    name: detail.name,
    code: detail.code,
    description: detail.description || "",
    targetSegment: detail.targetSegment,
    displayOrder: String(detail.displayOrder),
    status: detail.status,
  };
}

function formatCommercialDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function TemplateFields({ values, errors, onChange, codeReadOnly = false, showStatus = true }: FieldsProps) {
  return (
    <Stack spacing={2}>
      <TextField
        name="commercial-plan-name"
        label="Name *"
        value={values.name}
        autoFocus
        onChange={(event) => onChange({ ...values, name: event.target.value })}
        error={Boolean(errors?.name)}
        helperText={errors?.name || "Business name shown to commercial admins."}
        inputProps={{ maxLength: 128 }}
      />
      <TextField
        name="commercial-plan-code"
        label="Code *"
        value={values.code}
        onChange={(event) => onChange({ ...values, code: event.target.value })}
        error={Boolean(errors?.code)}
        helperText={errors?.code || (codeReadOnly ? "Code is fixed after template creation." : "Uppercase business key.")}
        inputProps={{ maxLength: 64 }}
        InputProps={{ readOnly: codeReadOnly }}
      />
      <TextField
        name="commercial-plan-description"
        label="Description"
        value={values.description}
        onChange={(event) => onChange({ ...values, description: event.target.value })}
        error={Boolean(errors?.description)}
        helperText={errors?.description || "Optional planning notes."}
        multiline
        minRows={3}
        inputProps={{ maxLength: 512 }}
      />
      <TextField
        name="commercial-plan-targetSegment"
        select
        label="Target Segment *"
        value={values.targetSegment}
        onChange={(event) => onChange({ ...values, targetSegment: event.target.value as CommercialPlanTargetSegment })}
        error={Boolean(errors?.targetSegment)}
        helperText={errors?.targetSegment || "Select the business segment."}
      >
        {TEMPLATE_TARGET_SEGMENTS.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        name="commercial-plan-displayOrder"
        label="Display Order"
        type="number"
        value={values.displayOrder}
        onChange={(event) => onChange({ ...values, displayOrder: event.target.value })}
        error={Boolean(errors?.displayOrder)}
        helperText={errors?.displayOrder || "Non-negative ordering value."}
        inputProps={{ min: 0, step: 1 }}
      />
      {showStatus ? (
        <TextField
          name="commercial-plan-status"
          select
          label="Status"
          value={values.status}
          onChange={(event) => onChange({ ...values, status: event.target.value as CommercialPlanTemplateStatus })}
          helperText="Draft is the recommended starting status."
        >
          {TEMPLATE_STATUSES.map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      ) : null}
    </Stack>
  );
}

type CreateDialogProps = {
  open: boolean;
  submitting: boolean;
  error: string | null;
  sourceTemplates: Array<{ id: string; name: string; code: string; description: string | null; targetSegment: CommercialPlanTargetSegment; displayOrder: number }>;
  onClose: () => void;
  onCreate: (values: CommercialPlanTemplateFormValue) => Promise<void>;
  onClone: (sourceTemplateId: string, values: CommercialPlanTemplateFormValue) => Promise<void>;
};

export function CommercialPlanTemplateCreateDialog({ open, submitting, error, sourceTemplates, onClose, onCreate, onClone }: CreateDialogProps) {
  const [values, setValues] = React.useState<CommercialPlanTemplateFormValue>(defaultTemplateForm());
  const [errors, setErrors] = React.useState<CommercialPlanTemplateFormErrors>({});
  const [dirty, setDirty] = React.useState(false);
  const [codeTouched, setCodeTouched] = React.useState(false);
  const [discardOpen, setDiscardOpen] = React.useState(false);
  const [mode, setMode] = React.useState<"blank" | "clone">("blank");
  const [sourceTemplateId, setSourceTemplateId] = React.useState<string>("");

  React.useEffect(() => {
    if (!open) return;
    setValues(defaultTemplateForm());
    setErrors({});
    setDirty(false);
    setCodeTouched(false);
    setDiscardOpen(false);
    setMode("blank");
    setSourceTemplateId(sourceTemplates[0]?.id || "");
  }, [open, sourceTemplates]);

  React.useEffect(() => {
    if (!open || mode !== "clone") return;
    const source = sourceTemplates.find((item) => item.id === sourceTemplateId) || sourceTemplates[0];
    if (!source) return;
    setValues((current) => ({
      ...current,
      name: source.name,
      code: source.code,
      description: source.description || "",
      targetSegment: source.targetSegment,
      displayOrder: String(source.displayOrder ?? 0),
      status: "DRAFT",
    }));
    setCodeTouched(false);
    setDirty(false);
  }, [mode, open, sourceTemplateId, sourceTemplates]);

  React.useEffect(() => {
    if (!open || !dirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty, open]);

  function validate(next: CommercialPlanTemplateFormValue) {
    const nextErrors: CommercialPlanTemplateFormErrors = {};
    if (!next.name.trim()) nextErrors.name = "Name is required.";
    if (!next.code.trim()) nextErrors.code = "Code is required.";
    else if (!/^[A-Z0-9]+(?:_[A-Z0-9]+)*$/.test(next.code.trim())) nextErrors.code = "Use uppercase letters, numbers, and underscores only.";
    if (!next.targetSegment) nextErrors.targetSegment = "Target segment is required.";
    const order = Number.parseInt(next.displayOrder, 10);
    if (next.displayOrder.trim() === "" || Number.isNaN(order) || order < 0) nextErrors.displayOrder = "Display order must be a non-negative integer.";
    if (next.description.trim().length > 512) nextErrors.description = "Description must be 512 characters or fewer.";
    if (next.name.trim().length > 128) nextErrors.name = "Name must be 128 characters or fewer.";
    if (next.code.trim().length > 64) nextErrors.code = "Code must be 64 characters or fewer.";
    return nextErrors;
  }

  function requestClose() {
    if (dirty) {
      setDiscardOpen(true);
      return;
    }
    onClose();
  }

  async function submit() {
    const nextErrors = validate(values);
    setErrors(nextErrors);
    const firstInvalid = Object.keys(nextErrors)[0];
    if (firstInvalid) {
      const element = document.querySelector<HTMLElement>(`[name="commercial-plan-${firstInvalid}"]`);
      element?.focus();
      return;
    }
    const normalized = {
      ...values,
      name: values.name.trim(),
      code: normalizeTemplateCode(values.code),
      description: values.description.trim(),
      displayOrder: String(Number.parseInt(values.displayOrder, 10)),
    };
    if (mode === "clone") {
      await onClone(sourceTemplateId, normalized);
      return;
    }
    await onCreate(normalized);
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
        maxWidth="sm"
      >
        <DialogTitle sx={{ fontWeight: 900 }}>Create Plan Template</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Capture the business identity before draft configuration is available.
            </Typography>
            <TextField
              select
              name="commercial-plan-create-mode"
              label="Create Mode"
              value={mode}
              onChange={(event) => {
                const nextMode = event.target.value as "blank" | "clone";
                setMode(nextMode);
                setDirty(true);
                if (nextMode === "blank") {
                  setValues(defaultTemplateForm());
                  setCodeTouched(false);
                  setErrors({});
                } else if (sourceTemplates.length > 0) {
                  const source = sourceTemplates.find((item) => item.id === sourceTemplateId) || sourceTemplates[0];
                  setSourceTemplateId(source.id);
                  setValues({
                    name: source.name,
                    code: source.code,
                    description: source.description || "",
                    targetSegment: source.targetSegment,
                    displayOrder: String(source.displayOrder ?? 0),
                    status: "DRAFT",
                  });
                  setCodeTouched(false);
                }
              }}
            >
              <MenuItem value="blank">Create Blank</MenuItem>
              <MenuItem value="clone" disabled={sourceTemplates.length === 0}>Clone Existing</MenuItem>
            </TextField>
            {mode === "clone" ? (
              <TextField
                select
                name="commercial-plan-source"
                label="Source Template *"
                value={sourceTemplateId}
                onChange={(event) => {
                  const next = event.target.value;
                  setSourceTemplateId(next);
                  const source = sourceTemplates.find((item) => item.id === next);
                  if (source) {
                    setValues({
                      name: source.name,
                      code: source.code,
                      description: source.description || "",
                      targetSegment: source.targetSegment,
                      displayOrder: String(source.displayOrder ?? 0),
                      status: "DRAFT",
                    });
                    setCodeTouched(false);
                    setDirty(true);
                  }
                }}
                helperText="Clones the latest published version when available, otherwise the current draft."
              >
                {sourceTemplates.map((source) => (
                  <MenuItem key={source.id} value={source.id}>
                    {source.name} ({source.code})
                  </MenuItem>
                ))}
              </TextField>
            ) : null}
            {error ? <Alert severity="error">{error}</Alert> : null}
            <Stack spacing={2}>
              <TextField
                name="commercial-plan-name"
                label="Name *"
                value={values.name}
                autoFocus
                onChange={(event) => {
                  const name = event.target.value;
                  setDirty(true);
                  setValues((current) => {
                    const nextCode = codeTouched ? current.code : normalizeTemplateCode(name);
                    return { ...current, name, code: nextCode };
                  });
                }}
                error={Boolean(errors.name)}
                helperText={errors.name || "Business name shown to commercial admins."}
                inputProps={{ maxLength: 128 }}
              />
              <TextField
                name="commercial-plan-code"
                label="Code *"
                value={values.code}
                onChange={(event) => {
                  const code = event.target.value;
                  setDirty(true);
                  setCodeTouched(true);
                  setValues((current) => ({ ...current, code }));
                }}
                error={Boolean(errors.code)}
                helperText={errors.code || "Uppercase business key."}
                inputProps={{ maxLength: 64 }}
              />
              <TextField
                name="commercial-plan-description"
                label="Description"
                value={values.description}
                onChange={(event) => {
                  setDirty(true);
                  setValues((current) => ({ ...current, description: event.target.value }));
                }}
                error={Boolean(errors.description)}
                helperText={errors.description || "Optional planning notes."}
                multiline
                minRows={3}
                inputProps={{ maxLength: 512 }}
              />
              <TextField
                name="commercial-plan-targetSegment"
                select
                label="Target Segment *"
                value={values.targetSegment}
                onChange={(event) => {
                  setDirty(true);
                  setValues((current) => ({ ...current, targetSegment: event.target.value as CommercialPlanTargetSegment }));
                }}
                error={Boolean(errors.targetSegment)}
                helperText={errors.targetSegment || "Select the business segment."}
              >
                {TEMPLATE_TARGET_SEGMENTS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                name="commercial-plan-displayOrder"
                label="Display Order"
                type="number"
                value={values.displayOrder}
                onChange={(event) => {
                  setDirty(true);
                  setValues((current) => ({ ...current, displayOrder: event.target.value }));
                }}
                error={Boolean(errors.displayOrder)}
                helperText={errors.displayOrder || "Non-negative ordering value."}
                inputProps={{ min: 0, step: 1 }}
              />
              <TextField
                name="commercial-plan-status"
                select
                label="Status"
                value={values.status}
                onChange={(event) => {
                  setDirty(true);
                  setValues((current) => ({ ...current, status: event.target.value as CommercialPlanTemplateStatus }));
                }}
                helperText="Draft is the recommended starting status."
              >
                {TEMPLATE_STATUSES.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={requestClose}>Cancel</Button>
          <Button variant="contained" onClick={() => void submit()} disabled={submitting}>
            {mode === "clone" ? "Clone Template" : "Create Template"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={discardOpen} onClose={() => setDiscardOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 900 }}>Discard new plan template?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            The template details you entered have not been saved.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setDiscardOpen(false);
            }}
          >
            Keep Editing
          </Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => {
              setDiscardOpen(false);
              onClose();
            }}
          >
            Discard
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

type TemplateSummaryProps = {
  values: CommercialPlanTemplateFormValue;
  errors?: CommercialPlanTemplateFormErrors;
  onChange: (next: CommercialPlanTemplateFormValue) => void;
  onSave: () => void;
  onDiscard?: () => void;
  discardDisabled?: boolean;
  saving: boolean;
  dirty: boolean;
  publicationReady: boolean;
  validation: CommercialPlanValidationResult;
  draftRevision: number;
  latestPublishedVersionNumber: number | null;
  updatedAt: string;
  updatedBy: string | null;
  capabilityCount: number;
  moduleCount: number;
  featureCount: number;
  limitCount: number;
  addonCount: number;
  codeReadOnly?: boolean;
};

export function CommercialPlanTemplateSummarySection({
  values,
  errors,
  onChange,
  onSave,
  saving,
  dirty,
  publicationReady,
  validation,
  draftRevision,
  latestPublishedVersionNumber,
  updatedAt,
  updatedBy,
  capabilityCount,
  moduleCount,
  featureCount,
  limitCount,
  addonCount,
  onDiscard,
  discardDisabled = false,
  codeReadOnly = true,
}: TemplateSummaryProps) {
  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Stack spacing={0.5}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              Template Details
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Edit the persisted template identity before working with draft configuration.
            </Typography>
          </Stack>
          <TemplateFields values={values} errors={errors} onChange={onChange} codeReadOnly={codeReadOnly} showStatus />
          <Stack direction="row" justifyContent="flex-end" spacing={1}>
            {onDiscard ? (
              <Button variant="outlined" color="inherit" onClick={onDiscard} disabled={discardDisabled || saving}>
                Discard Changes
              </Button>
            ) : null}
            <Button variant="contained" onClick={onSave} disabled={!dirty || saving}>
              Save Template
            </Button>
          </Stack>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="h6" sx={{ fontWeight: 900 }}>
            Publication Snapshot
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`Draft revision ${draftRevision}`} />
            <Chip label={`Validated draft revision ${validation.validatedDraftRevision}`} variant="outlined" />
            <Chip label={validation.validatedAt ? `Validated ${formatCommercialDateTime(validation.validatedAt)}` : "Not validated"} variant="outlined" />
            <Chip label={latestPublishedVersionNumber == null ? "No published version" : `Latest published v${latestPublishedVersionNumber}`} />
            <Chip
              label={
                validation.validationState === "VALID"
                  ? "Ready to publish"
                  : validation.validationState === "STALE"
                    ? "Validation is outdated"
                    : validation.validationState === "INVALID"
                      ? "Configuration incomplete"
                      : "Not validated"
              }
              color={validation.validationState === "VALID" ? "success" : validation.validationState === "INVALID" || validation.validationState === "STALE" ? "warning" : "default"}
              variant={validation.validationState === "VALID" ? "filled" : "outlined"}
            />
            <Chip label={publicationReady ? "Publishable" : "Not publishable"} color={publicationReady ? "success" : "warning"} variant="outlined" />
            <Chip label={`Updated ${formatCommercialDateTime(updatedAt)}`} variant="outlined" />
            {updatedBy ? <Chip label={`Updated by ${updatedBy}`} variant="outlined" /> : null}
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {validation.blockingFindingCount > 0
              ? `${validation.blockingFindingCount} blocking finding${validation.blockingFindingCount === 1 ? "" : "s"}${validation.warningFindingCount > 0 ? ` and ${validation.warningFindingCount} warning${validation.warningFindingCount === 1 ? "" : "s"}` : ""}`
              : validation.warningFindingCount > 0
                ? `${validation.warningFindingCount} warning${validation.warningFindingCount === 1 ? "" : "s"}`
                : "No blocking findings"}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Validation status: {validation.validationState.replaceAll("_", " ").toLowerCase()}
          </Typography>
        </Stack>
      </Paper>

      <Stack spacing={2}>
        {([
          ["Capabilities", capabilityCount],
          ["Modules", moduleCount],
          ["Features", featureCount],
          ["Limits", limitCount],
          ["Add-ons", addonCount],
        ] as const).map(([label, value]) => (
          <Paper key={label} variant="outlined" sx={{ p: 2 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {label}
              </Typography>
              <Chip size="small" label={`${value}`} />
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Stack>
  );
}
