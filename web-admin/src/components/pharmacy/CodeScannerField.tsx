import * as React from "react";
import {
  Button,
  TextField,
  InputAdornment,
} from "@mui/material";
import CodeScannerDialog from "./CodeScannerDialog";

type CodeScannerFieldProps = {
  id?: string;
  label: React.ReactNode;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  helperText?: string;
  disabled?: boolean;
  size?: "small" | "medium";
  error?: boolean;
  inputRef?: React.Ref<HTMLInputElement>;
};

export default function CodeScannerField({
  id,
  label,
  value,
  onChange,
  placeholder,
  helperText,
  disabled,
  size = "medium",
  error,
  inputRef,
}: CodeScannerFieldProps) {
  const [open, setOpen] = React.useState(false);
  const labelText = typeof label === "string" ? label : "code";

  return (
    <>
      <TextField
        id={id}
        fullWidth
        size={size}
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        helperText={helperText}
        disabled={disabled}
        error={error}
        inputRef={inputRef}
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
        title={`Scan ${labelText}`}
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
