import * as React from "react";
import { TextField } from "@mui/material";

type AutocompleteTextInputProps = {
  value: string;
  onChange: (value: string) => void;
  suggestions: string[];
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  error?: boolean;
  helperText?: React.ReactNode;
};

export default function AutocompleteTextInput({
  value,
  onChange,
  suggestions,
  label,
  placeholder,
  disabled,
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
        fullWidth
        label={label}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
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
