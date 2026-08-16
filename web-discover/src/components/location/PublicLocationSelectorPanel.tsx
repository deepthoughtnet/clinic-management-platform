import type { ReactNode } from "react";
import type { PublicLocationCoordinates } from "../../context/PublicLocationContext";

type PublicLocationSelectorPanelProps = {
  className?: string;
  locationDraft: string;
  onLocationDraftChange: (value: string) => void;
  selectedCoordinates: PublicLocationCoordinates | null;
  options: readonly string[];
  onRadiusChange?: (value: string) => void;
  radiusKm?: string;
  showRadius?: boolean;
  inputLabel?: string;
  inputPlaceholder?: string;
  helperText?: ReactNode;
  message?: string | null;
  note?: ReactNode;
  saveLabel?: string;
  clearLabel?: string;
  currentLocationButtonLabel?: string;
  onSaveLocation: () => void;
  onUseCurrentLocation: () => void;
  onClearLocation?: () => void;
  saveDisabled?: boolean;
  useCurrentLocationDisabled?: boolean;
  showClear?: boolean;
};

export function PublicLocationSelectorPanel({
  className,
  locationDraft,
  onLocationDraftChange,
  selectedCoordinates,
  options,
  onRadiusChange,
  radiusKm,
  showRadius = true,
  inputLabel = "City or locality",
  inputPlaceholder = "Pune",
  helperText,
  message,
  note,
  saveLabel = "Save location",
  clearLabel = "Clear",
  currentLocationButtonLabel = "Use my current location",
  onSaveLocation,
  onUseCurrentLocation,
  onClearLocation,
  saveDisabled = false,
  useCurrentLocationDisabled = false,
  showClear = false,
}: PublicLocationSelectorPanelProps) {
  return (
    <div className={`public-location-selector location-selector ${className ?? ""}`.trim()}>
      <label className="public-location-selector__field">
        <span className="public-location-selector__label">{inputLabel}</span>
        <input
          value={locationDraft}
          onChange={(event) => onLocationDraftChange(event.target.value)}
          placeholder={inputPlaceholder}
          aria-label={inputLabel}
          autoComplete="off"
          maxLength={60}
        />
      </label>
      {message ? (
        <p className="form-note" role="status">
          {message}
        </p>
      ) : null}
      {helperText ? <p className="public-location-selector__helper">{helperText}</p> : null}
      <div className="chip-row" role="list" aria-label="Popular locations">
        {options.map((option) => (
          <button key={option} className="chip-button" type="button" onClick={() => onLocationDraftChange(option)}>
            {option}
          </button>
        ))}
      </div>
      {showRadius ? (
        <label className="public-location-selector__field">
          <span className="public-location-selector__label">Nearby radius</span>
          <select value={radiusKm ?? "10"} onChange={(event) => onRadiusChange?.(event.target.value)} disabled={!selectedCoordinates}>
            <option value="2">2 km</option>
            <option value="5">5 km</option>
            <option value="10">10 km</option>
            <option value="25">25 km</option>
            <option value="50">50 km</option>
          </select>
        </label>
      ) : null}
      {note ? <p className="form-note">{note}</p> : null}
      <div className="cta-row">
        <button className="secondary-button" type="button" onClick={onSaveLocation} disabled={saveDisabled}>
          {saveLabel}
        </button>
        <button className="text-button" type="button" onClick={onUseCurrentLocation} disabled={useCurrentLocationDisabled}>
          {currentLocationButtonLabel}
        </button>
        {showClear && onClearLocation ? (
          <button className="text-button" type="button" onClick={onClearLocation}>
            {clearLabel}
          </button>
        ) : null}
      </div>
    </div>
  );
}
