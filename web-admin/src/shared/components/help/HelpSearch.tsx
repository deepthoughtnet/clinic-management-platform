import * as React from "react";
import { TextField } from "@mui/material";

type HelpSearchProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  autoFocus?: boolean;
  inputRef?: React.Ref<HTMLInputElement>;
};

export default function HelpSearch({ value, onChange, placeholder = "Search help, workflows, common errors, and FAQs...", disabled, autoFocus, inputRef }: HelpSearchProps) {
  return (
    <TextField
      size="small"
      fullWidth
      label="Search"
      inputRef={inputRef}
      autoFocus={autoFocus}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      disabled={disabled}
    />
  );
}
