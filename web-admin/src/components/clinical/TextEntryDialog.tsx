import * as React from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
} from "@mui/material";

type SubmitState = "idle" | "submitting";

export function TextEntryDialog({
  open,
  title,
  description,
  label,
  placeholder,
  value,
  maxLength = 1000,
  multiline = true,
  confirmLabel = "Submit",
  cancelLabel = "Cancel",
  submittingLabel = "Submitting...",
  helperText,
  required = true,
  onCancel,
  onSubmit,
}: {
  open: boolean;
  title: string;
  description?: React.ReactNode;
  label: string;
  placeholder?: string;
  value: string;
  maxLength?: number;
  multiline?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
  submittingLabel?: string;
  helperText?: string;
  required?: boolean;
  onCancel: () => void;
  onSubmit: (value: string) => Promise<void> | void;
}) {
  const [draft, setDraft] = React.useState(value);
  const [error, setError] = React.useState<string | null>(null);
  const [submitState, setSubmitState] = React.useState<SubmitState>("idle");
  const textareaId = React.useId();

  React.useEffect(() => {
    if (open) {
      setDraft(value);
      setError(null);
      setSubmitState("idle");
    }
  }, [open, value]);

  const trimmed = draft.trim();
  const isValid = (!required || trimmed.length > 0) && draft.length <= maxLength;
  const remaining = Math.max(0, maxLength - draft.length);

  const handleClose = () => {
    if (submitState === "submitting") return;
    onCancel();
  };

  const handleSubmit = async () => {
    if (!isValid || submitState === "submitting") {
      if (required && trimmed.length === 0) {
        setError("Review comments are required.");
      } else if (draft.length > maxLength) {
        setError(`Review comments must be ${maxLength} characters or fewer.`);
      }
      return;
    }

    setError(null);
    setSubmitState("submitting");
    try {
      await onSubmit(trimmed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed.");
      setSubmitState("idle");
      return;
    }
    setSubmitState("idle");
  };

  return (
    <Dialog
      open={open}
      onClose={(_, reason) => {
        if (submitState === "submitting" || reason === "backdropClick" || reason === "escapeKeyDown") {
          if (submitState !== "submitting") {
            handleClose();
          }
          return;
        }
        handleClose();
      }}
      fullWidth
      maxWidth="sm"
      aria-labelledby={`${textareaId}-title`}
      aria-describedby={`${textareaId}-description`}
      disableEscapeKeyDown={submitState === "submitting"}
    >
      <DialogTitle id={`${textareaId}-title`}>{title}</DialogTitle>
      <DialogContent id={`${textareaId}-description`} sx={{ pt: 1 }}>
        {description ? <DialogContentText component="div" sx={{ mb: 2, whiteSpace: "pre-line" }}>{description}</DialogContentText> : null}
        <TextField
          fullWidth
          autoFocus
          multiline={multiline}
          minRows={multiline ? 4 : 1}
          label={label}
          placeholder={placeholder}
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value);
            if (error) setError(null);
          }}
          disabled={submitState === "submitting"}
          error={Boolean(error)}
          helperText={error || helperText || `${draft.length} / ${maxLength} characters${remaining < 25 ? `, ${remaining} remaining` : ""}`}
          inputProps={{ maxLength }}
        />
      </DialogContent>
      <DialogActions>
        <Button type="button" onClick={handleClose} disabled={submitState === "submitting"}>
          {cancelLabel}
        </Button>
        <Button
          type="button"
          variant="contained"
          disabled={!isValid || submitState === "submitting"}
          onClick={() => void handleSubmit()}
        >
          {submitState === "submitting" ? submittingLabel : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
