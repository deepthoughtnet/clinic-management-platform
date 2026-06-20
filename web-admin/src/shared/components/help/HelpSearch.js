import React from "react";
import { TextField } from "@mui/material";

export default function HelpSearch({ value, onChange, placeholder = "Search help, workflows, common errors, and FAQs...", disabled, autoFocus, inputRef }) {
  return React.createElement(TextField, {
    size: "small",
    fullWidth: true,
    label: "Search",
    inputRef,
    autoFocus,
    value,
    onChange: (event) => onChange(event.target.value),
    placeholder,
    disabled,
  });
}
