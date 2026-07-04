import * as React from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";

export function ConfirmationDialog({
  open,
  title,
  description,
  children,
  cancelLabel = "Cancel",
  confirmLabel = "Confirm",
  confirmColor = "primary",
  confirmDisabled = false,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  title: string;
  description?: React.ReactNode;
  children?: React.ReactNode;
  cancelLabel?: string;
  confirmLabel?: string;
  confirmColor?: "primary" | "secondary" | "error" | "warning" | "info";
  confirmDisabled?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog
      open={open}
      onClose={onCancel}
      fullWidth
      maxWidth="xs"
      aria-labelledby="confirmation-dialog-title"
      aria-describedby="confirmation-dialog-description"
    >
      <DialogTitle id="confirmation-dialog-title">{title}</DialogTitle>
      <DialogContent id="confirmation-dialog-description">
        {description ? (
          <DialogContentText component="div" sx={{ whiteSpace: "pre-line" }}>
            {description}
          </DialogContentText>
        ) : null}
        {children ? <>{children}</> : null}
      </DialogContent>
      <DialogActions>
        <Button type="button" onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button type="button" variant="contained" color={confirmColor} disabled={confirmDisabled} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
