import * as React from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import { type PaymentMode } from "../api/clinicApi";

const PAYMENT_MODES: PaymentMode[] = ["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"];

export type ConsultationFeeDialogValue = {
  paymentMode: PaymentMode;
  referenceNumber: string;
  notes: string;
};

type Props = {
  open: boolean;
  title: string;
  reasonLabel?: string;
  appointmentLabel: string;
  doctorLabel: string;
  patientLabel: string;
  feeLabel: string;
  submitLabel: string;
  onClose: () => void;
  onSubmit: (value: ConsultationFeeDialogValue) => Promise<void> | void;
};

export default function ConsultationFeeDialog(props: Props) {
  const [paymentMode, setPaymentMode] = React.useState<PaymentMode>("CASH");
  const [referenceNumber, setReferenceNumber] = React.useState("");
  const [notes, setNotes] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!props.open) {
      setPaymentMode("CASH");
      setReferenceNumber("");
      setNotes("");
      setSaving(false);
      setError(null);
    }
  }, [props.open]);

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      await props.onSubmit({
        paymentMode,
        referenceNumber: referenceNumber.trim(),
        notes: notes.trim(),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to collect consultation fee");
    } finally {
      setSaving(false);
    }
  };

  const referenceRequired = paymentMode !== "CASH";

  return (
    <Dialog open={props.open} onClose={props.onClose} fullWidth maxWidth="sm">
      <DialogTitle>{props.title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">{props.appointmentLabel}</Typography>
          <Typography variant="body2">{props.doctorLabel}</Typography>
          <Typography variant="body2">{props.patientLabel}</Typography>
          {props.reasonLabel ? <Typography variant="body2">Payment reason: {props.reasonLabel}</Typography> : null}
          <Typography variant="body2" sx={{ fontWeight: 700 }}>{props.feeLabel}</Typography>
          {error ? <Typography variant="body2" color="error">{error}</Typography> : null}
          <FormControl fullWidth size="small">
            <InputLabel id="consultation-fee-payment-mode-label">Payment mode</InputLabel>
            <Select
              labelId="consultation-fee-payment-mode-label"
              label="Payment mode"
              value={paymentMode}
              onChange={(e) => setPaymentMode(e.target.value as PaymentMode)}
            >
              {PAYMENT_MODES.map((mode) => (
                <MenuItem key={mode} value={mode}>{mode}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            size="small"
            label={referenceRequired ? "Reference number" : "Reference number (optional)"}
            value={referenceNumber}
            onChange={(e) => setReferenceNumber(e.target.value)}
          />
          <TextField
            size="small"
            label="Notes"
            multiline
            minRows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose} disabled={saving}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={saving || (referenceRequired && referenceNumber.trim().length === 0)}
        >
          {props.submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
