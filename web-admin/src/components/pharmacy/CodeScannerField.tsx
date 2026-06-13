import * as React from "react";
import {
  Button,
  TextField,
  InputAdornment,
} from "@mui/material";
import CodeScannerDialog from "./CodeScannerDialog";

type CodeScannerFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  helperText?: string;
  disabled?: boolean;
  size?: "small" | "medium";
};

export default function CodeScannerField({
  label,
  value,
  onChange,
  placeholder,
  helperText,
  disabled,
  size = "medium",
}: CodeScannerFieldProps) {
  const [open, setOpen] = React.useState(false);

  return (
    <>
      <TextField
        fullWidth
        size={size}
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        helperText={helperText}
        disabled={disabled}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <Button size="small" sx={{ minWidth: 52 }} onClick={() => setOpen(true)} disabled={disabled}>
                Scan
              </Button>
            </InputAdornment>
          ),
        }}
      />
      <CodeScannerDialog
        open={open}
        title={`Scan ${label}`}
        description="Point the camera at a barcode or QR code. You can also type the code manually below."
        value={value}
        onClose={() => setOpen(false)}
        onDetected={(code) => onChange(code.trim())}
        manualLabel="Scan or enter code"
        manualPlaceholder="barcode / QR code"
      />
    </>
  );
}
