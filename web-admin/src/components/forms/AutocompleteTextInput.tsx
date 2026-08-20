import * as React from "react";
import { TextField } from "@mui/material";

type AutocompleteTextInputProps = {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  suggestions: string[];
  label?: React.ReactNode;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  error?: boolean;
  helperText?: React.ReactNode;
};

export default function AutocompleteTextInput({
  id,
  value,
  onChange,
  suggestions,
  label,
  placeholder,
  disabled,
  required,
  error,
  helperText,
}: AutocompleteTextInputProps) {
  const listId = React.useId();
  const uniqueSuggestions = React.useMemo(() => {
    const seen = new Set<string>();
    const result: string[] = [];
    for (const suggestion of suggestions) {
      const normalized = suggestion.trim().toLowerCase();
      if (!normalized || seen.has(normalized)) continue;
      seen.add(normalized);
      result.push(suggestion);
    }
    return result;
  }, [suggestions]);

  return (
    <>
      <TextField
        id={id}
        fullWidth
        label={label}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        required={required}
        error={error}
        helperText={helperText}
        onChange={(event) => onChange(event.target.value)}
        slotProps={{
          htmlInput: {
            list: listId,
            autoComplete: "off",
          },
        }}
      />
      <datalist id={listId}>
        {uniqueSuggestions.map((suggestion) => (
          <option key={suggestion} value={suggestion} />
        ))}
      </datalist>
    </>
  );
}
