import { Autocomplete, Chip, TextField } from "@mui/material";

export type ProviderOption = {
  value: string;
  label: string;
};

export function ProviderDropdownField({
  label,
  helperText,
  error,
  value,
  options,
  onChange,
  placeholder,
  disabled = false,
}: {
  label: string;
  helperText?: string;
  error?: string | null;
  value: string;
  options: ProviderOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}) {
  return (
    <label className="provider-field provider-dropdown-field">
      <span>
        {label}
        {helperText ? <small>{helperText}</small> : null}
      </span>
      <select
        aria-invalid={Boolean(error)}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
      >
        <option value="">{placeholder ?? `Select ${label.toLowerCase()}`}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
}

export function ProviderMultiSelectField({
  label,
  helperText,
  error,
  value,
  options,
  onChange,
  placeholder,
  loading = false,
  noOptionsText,
  disabled = false,
  allowCustomValues = false,
}: {
  label: string;
  helperText?: string;
  error?: string | null;
  value: string[];
  options: ProviderOption[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  loading?: boolean;
  noOptionsText?: string;
  disabled?: boolean;
  allowCustomValues?: boolean;
}) {
  const selectedValues = value
    .map((item) => options.find((option) => option.value === item) ?? { value: item, label: item })
    .filter((item, index, items) => items.findIndex((candidate) => candidate.value === item.value) === index);

  return (
    <Autocomplete
      className="provider-autocomplete"
      multiple
      freeSolo={allowCustomValues}
      disableCloseOnSelect
      loading={loading}
      disabled={disabled}
      options={options}
      value={selectedValues}
      isOptionEqualToValue={(option, current) => option.value === current.value}
      getOptionLabel={(option) => typeof option === "string" ? option : option.label}
      filterSelectedOptions
      noOptionsText={noOptionsText ?? "No options match your search"}
      onChange={(_, next) => onChange(next.map((item) => typeof item === "string" ? item : item.value))}
      renderTags={(tagValue, getTagProps) =>
        (tagValue as ProviderOption[]).map((option, index) => (
          <Chip
            {...getTagProps({ index })}
            key={option.value}
            className="provider-chip"
            label={option.label}
            variant="outlined"
            size="small"
          />
        ))
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          helperText={error ?? helperText}
          error={Boolean(error)}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  );
}
