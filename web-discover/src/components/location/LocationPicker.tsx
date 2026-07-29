import { useEffect, useMemo, useRef, useState } from "react";
import { discoverConfig } from "../../config";
import type { LocationCoordinates, LocationMapEntry, LocationSearchResult } from "./locationTypes";
import { buildDirectionsUrl, locationAddressLine, locationCoordinates } from "./locationHelpers";
import { geocodeLocation } from "./locationGeocoding";
import { LocationMap } from "./LocationMap";
import { LocationSearchInput } from "./LocationSearchInput";

export type LocationPickerProps = {
  providerName: string;
  location: LocationMapEntry | null;
  onCoordinatesChange: (coordinates: LocationCoordinates | null) => void;
  className?: string;
  editable?: boolean;
};

export function LocationPicker({
  providerName,
  location,
  onCoordinatesChange,
  className,
  editable = true,
}: LocationPickerProps) {
  const [searchValue, setSearchValue] = useState(() => locationAddressLine(location));
  const [results, setResults] = useState<LocationSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const coordinates = locationCoordinates(location);

  useEffect(() => {
    setSearchValue((current) => {
      if (!current.trim() || current === locationAddressLine(location)) {
        return locationAddressLine(location);
      }
      return current;
    });
  }, [location]);

  const center = useMemo<LocationCoordinates | null>(() => coordinates ?? (discoverConfig.mapDefaultLatitude != null && discoverConfig.mapDefaultLongitude != null
    ? { latitude: discoverConfig.mapDefaultLatitude, longitude: discoverConfig.mapDefaultLongitude }
    : null), [coordinates]);

  async function search() {
    if (!editable) return;
    const query = searchValue.trim() || locationAddressLine(location) || providerName;
    if (!query.trim()) {
      setError("Enter an address or locality to search.");
      return;
    }
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError(null);
    setStatusMessage(null);
    try {
      const matches = await geocodeLocation(query, controller.signal);
      if (controller.signal.aborted) {
        return;
      }
      setResults(matches);
      if (!matches.length) {
        setError("No matching location was found. You can still place the marker manually.");
        return;
      }
      const chosen = matches[0];
      setSearchValue(chosen.label);
      onCoordinatesChange(chosen.coordinates);
      setStatusMessage("Location pinned on the map.");
    } catch (ex) {
      if (!controller.signal.aborted) {
        setError(ex instanceof Error ? ex.message : "Location search failed.");
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
      }
    }
  }

  function useCurrentLocation() {
    if (!editable) return;
    setError(null);
    setStatusMessage(null);
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setError("Location services are not available in this browser.");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        onCoordinatesChange({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setStatusMessage("Current location captured.");
      },
      () => {
        setError("Location permission was not allowed. Please search or place the marker manually.");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  function handleSuggestionClick(result: LocationSearchResult) {
    if (!editable) return;
    setSearchValue(result.label);
    setResults([]);
    onCoordinatesChange(result.coordinates);
    setStatusMessage("Location pinned on the map.");
  }

  return (
    <div className={`location-picker ${className ?? ""}`.trim()}>
      <LocationSearchInput
        value={searchValue}
        onChange={setSearchValue}
        onSubmit={search}
        onUseCurrentLocation={useCurrentLocation}
        onSelectSuggestion={handleSuggestionClick}
        suggestions={results}
        loading={loading}
        error={error}
        helperText="Search by the practice address, nearby landmark, or locality."
        disabled={!editable}
      />
      {statusMessage ? <p className="location-picker-status">{statusMessage}</p> : null}
      <LocationMap
        center={center}
        marker={coordinates}
        interactive={editable}
        loading={loading}
        ariaLabel={`${providerName} map picker`}
        className="location-picker-map"
        emptyState={<div className="location-map__fallback location-map-fallback" aria-hidden="true"><strong>Map unavailable</strong><span>Search or use your current location to pin the practice.</span></div>}
        unavailableState={<div className="location-map__fallback location-map-fallback" aria-hidden="true"><strong>Map is temporarily unavailable.</strong><span>Search or use your current location to pin the practice.</span></div>}
        onMapClick={editable ? (nextCoordinates) => {
          onCoordinatesChange(nextCoordinates);
          setStatusMessage("Marker placed on the map.");
        } : undefined}
        onMarkerDragEnd={editable ? (nextCoordinates) => {
          onCoordinatesChange(nextCoordinates);
          setStatusMessage("Marker updated.");
        } : undefined}
      />
      {coordinates ? (
        <small className="location-coordinates" aria-label="Selected coordinates">
          {coordinates.latitude.toFixed(6)}, {coordinates.longitude.toFixed(6)}
        </small>
      ) : null}
      {buildDirectionsUrl(location) ? null : <small className="location-map-note">Directions are shown when map directions are configured.</small>}
    </div>
  );
}
