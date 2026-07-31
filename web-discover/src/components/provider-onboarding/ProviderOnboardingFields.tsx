import { Autocomplete, Chip, TextField } from "@mui/material";
import type { ReactNode } from "react";

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
  required = false,
  loading = false,
  loadError = null,
  onRetry,
  disabled = false,
}: {
  label: string;
  helperText?: ReactNode;
  error?: string | null;
  value: string;
  options: ProviderOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  loading?: boolean;
  loadError?: string | null;
  onRetry?: () => void;
  disabled?: boolean;
}) {
  if (loading) {
    return (
      <div className="provider-field provider-dropdown-field provider-dropdown-field--loading" aria-live="polite">
        <span>
          {label}
          {required ? <strong aria-hidden="true"> *</strong> : null}
          {helperText ? <small>{helperText}</small> : null}
        </span>
        <div className="provider-field-skeleton" aria-hidden="true">
          <span />
          <span />
        </div>
        <small className="provider-field-loading-text">Loading reference data…</small>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="provider-field provider-dropdown-field provider-dropdown-field--error" role="status" aria-live="polite">
        <span>
          {label}
          {required ? <strong aria-hidden="true"> *</strong> : null}
          {helperText ? <small>{helperText}</small> : null}
        </span>
        <div className="provider-field-error-panel">
          <strong>Unable to load reference data</strong>
          <p>{loadError}</p>
          {onRetry ? (
            <button className="secondary-button" type="button" onClick={onRetry}>
              Retry
            </button>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <label className="provider-field provider-dropdown-field">
      <span>
        {label}
        {required ? <strong aria-hidden="true"> *</strong> : null}
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
  loadError = null,
  onRetry,
  noOptionsText,
  disabled = false,
  allowCustomValues = false,
  required = false,
}: {
  label: string;
  helperText?: ReactNode;
  error?: string | null;
  value: string[];
  options: ProviderOption[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  loading?: boolean;
  loadError?: string | null;
  onRetry?: () => void;
  noOptionsText?: string;
  disabled?: boolean;
  allowCustomValues?: boolean;
  required?: boolean;
}) {
  if (loading) {
    return (
      <div className="provider-field provider-dropdown-field provider-dropdown-field--loading" aria-live="polite">
        <span>
          {label}
          {required ? <strong aria-hidden="true"> *</strong> : null}
          {helperText ? <small>{helperText}</small> : null}
        </span>
        <div className="provider-field-skeleton" aria-hidden="true">
          <span />
          <span />
        </div>
        <small className="provider-field-loading-text">Loading reference data…</small>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="provider-field provider-dropdown-field provider-dropdown-field--error" role="status" aria-live="polite">
        <span>
          {label}
          {required ? <strong aria-hidden="true"> *</strong> : null}
          {helperText ? <small>{helperText}</small> : null}
        </span>
        <div className="provider-field-error-panel">
          <strong>Unable to load reference data</strong>
          <p>{loadError}</p>
          {onRetry ? (
            <button className="secondary-button" type="button" onClick={onRetry}>
              Retry
            </button>
          ) : null}
        </div>
      </div>
    );
  }

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
      loadingText="Loading reference data…"
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
          fullWidth
          label={required ? `${label} *` : label}
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
