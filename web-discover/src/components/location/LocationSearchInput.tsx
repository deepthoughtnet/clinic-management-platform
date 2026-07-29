import { type FormEvent } from "react";
import type { LocationSearchResult } from "./locationTypes";

export type LocationSearchInputProps = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onUseCurrentLocation: () => void;
  onSelectSuggestion: (suggestion: LocationSearchResult) => void;
  suggestions: LocationSearchResult[];
  loading: boolean;
  error: string | null;
  helperText?: string;
  disabled?: boolean;
};

export function LocationSearchInput({
  value,
  onChange,
  onSubmit,
  onUseCurrentLocation,
  onSelectSuggestion,
  suggestions,
  loading,
  error,
  helperText,
  disabled,
}: LocationSearchInputProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <form className="location-search-input" onSubmit={handleSubmit}>
      <label className="provider-field">
        <span>
          Pin location on map
          {helperText ? <small>{helperText}</small> : null}
        </span>
        <input
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="Search by address, landmark, or locality"
          autoComplete="off"
          disabled={disabled}
          aria-describedby={error ? "location-search-error" : undefined}
        />
      </label>
      <div className="location-search-actions">
        <button className="secondary-button" type="submit" disabled={disabled || loading || !value.trim()}>
          {loading ? "Finding…" : "Find on Map"}
        </button>
        <button className="secondary-button" type="button" onClick={onUseCurrentLocation} disabled={disabled}>
          Use Current Location
        </button>
      </div>
      {error ? <small id="location-search-error" className="field-error">{error}</small> : null}
      {suggestions.length ? (
        <div className="location-search-results" role="listbox" aria-label="Search results">
          {suggestions.map((suggestion) => (
            <button
              key={`${suggestion.label}-${suggestion.coordinates.latitude}-${suggestion.coordinates.longitude}`}
              className="location-search-result"
              type="button"
              onClick={() => onSelectSuggestion(suggestion)}
            >
              <strong>{suggestion.label}</strong>
              <span>{suggestion.displayName}</span>
            </button>
          ))}
        </div>
      ) : null}
    </form>
  );
}
