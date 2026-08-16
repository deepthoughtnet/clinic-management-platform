import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from "react";

const PUBLIC_LOCATION_STORAGE_KEY = "jeevanam-discover-location";
const PUBLIC_LOCATION_COORDS_STORAGE_KEY = "jeevanam-discover-location-coordinates";
const PUBLIC_LOCATION_SOURCE_STORAGE_KEY = "jeevanam-discover-location-source";
const PUBLIC_LOCATION_MODE_STORAGE_KEY = "jeevanam-discover-location-mode";

export const PUBLIC_LOCATION_OPTIONS = [
  "Pune",
  "Mumbai",
  "Bangalore",
  "Delhi",
  "Hyderabad",
  "Chennai",
  "Bhopal",
] as const;

export const PUBLIC_CURRENT_LOCATION_LABEL = "Current location";
export const PUBLIC_DEFAULT_LOCATION = "Pune";
const PUBLIC_LOCATION_INPUT_MAX_LENGTH = 60;
const PUBLIC_LOCATION_INPUT_ALLOWED_PATTERN = /^[\p{L}\p{N}][\p{L}\p{N}\s.'’&()/,-]{0,58}[\p{L}\p{N}]$/u;

export type PublicLocationMode = "city" | "current";

export type PublicLocationCoordinates = {
  latitude: number;
  longitude: number;
};

export type PublicLocationSource = "default" | "manual" | "browser";

export type PublicLocationState = {
  mode: PublicLocationMode;
  location: string;
  coordinates: PublicLocationCoordinates | null;
  source: PublicLocationSource;
};

type PublicLocationContextValue = {
  locationState: PublicLocationState;
  setLocationState: (nextState: PublicLocationState) => void;
  setSelectedLocation: (nextLocation: string, nextCoordinates?: PublicLocationCoordinates | null) => void;
};

const PublicLocationContext = createContext<PublicLocationContextValue | null>(null);

function readStoredPublicCoordinates(value: string): PublicLocationCoordinates | null {
  try {
    const parsed = JSON.parse(value) as { latitude?: unknown; longitude?: unknown };
    const latitude = typeof parsed.latitude === "number" ? parsed.latitude : Number(parsed.latitude);
    const longitude = typeof parsed.longitude === "number" ? parsed.longitude : Number(parsed.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return null;
    }
    return { latitude, longitude };
  } catch {
    return null;
  }
}

export function normalizePublicLocation(value: string) {
  return value.replace(/\s+/g, " ").trim();
}

function isCurrentLocationLabel(value: string) {
  const normalized = normalizePublicLocation(value).toLowerCase();
  return normalized === PUBLIC_CURRENT_LOCATION_LABEL.toLowerCase() || normalized === "current location selected";
}

export function validatePublicLocationInput(value: string) {
  const normalized = normalizePublicLocation(value);
  if (!normalized) {
    return "Enter a valid city or locality.";
  }
  if (normalized.length > PUBLIC_LOCATION_INPUT_MAX_LENGTH) {
    return "Enter a valid city or locality.";
  }
  if (!/\p{L}/u.test(normalized)) {
    return "Enter a valid city or locality.";
  }
  if (!PUBLIC_LOCATION_INPUT_ALLOWED_PATTERN.test(normalized)) {
    return "Enter a valid city or locality.";
  }
  return null;
}

export function isSupportedPublicLocation(value: string) {
  const normalized = normalizePublicLocationSelection(value);
  return Boolean(normalized);
}

export function normalizePublicLocationSelection(value: string) {
  const normalized = normalizePublicLocation(value);
  if (!normalized) {
    return "";
  }
  if (isCurrentLocationLabel(normalized)) {
    return PUBLIC_CURRENT_LOCATION_LABEL;
  }
  const supported = PUBLIC_LOCATION_OPTIONS.find((option) => option.toLowerCase() === normalized.toLowerCase());
  if (supported) {
    return supported;
  }
  return validatePublicLocationInput(normalized) ? "" : normalized;
}

export function isCurrentLocationMode(
  stateOrLocation: Pick<PublicLocationState, "mode" | "coordinates"> | string,
  coordinates?: PublicLocationCoordinates | null,
) {
  if (typeof stateOrLocation === "string") {
    return Boolean(coordinates) && normalizePublicLocationSelection(stateOrLocation) === PUBLIC_CURRENT_LOCATION_LABEL;
  }
  return stateOrLocation.mode === "current" && Boolean(stateOrLocation.coordinates);
}

export function getPublicLocationDisplayLabel(state: Pick<PublicLocationState, "mode" | "location">) {
  return state.mode === "current" ? PUBLIC_CURRENT_LOCATION_LABEL : normalizePublicLocationSelection(state.location) || PUBLIC_DEFAULT_LOCATION;
}

export function getPublicLocationSearchCity(state: Pick<PublicLocationState, "mode" | "location">) {
  return state.mode === "current" ? PUBLIC_DEFAULT_LOCATION : normalizePublicLocationSelection(state.location) || PUBLIC_DEFAULT_LOCATION;
}

export function mapPublicLocationGeolocationError(error: GeolocationPositionError | { code?: number } | null) {
  const code = error?.code;
  if (code === 1) {
    return "Location access was denied. Allow location permission in your browser or select a city manually.";
  }
  if (code === 2) {
    return "We could not determine your current location. Please choose a city manually.";
  }
  if (code === 3) {
    return "Location lookup timed out. Please try again or choose a city manually.";
  }
  return "Location access was denied. Allow location permission in your browser or select a city manually.";
}

export function readStoredPublicLocation(): PublicLocationState {
  if (typeof window === "undefined") {
    return { mode: "city", location: PUBLIC_DEFAULT_LOCATION, coordinates: null, source: "default" };
  }
  const storedMode = window.localStorage.getItem(PUBLIC_LOCATION_MODE_STORAGE_KEY)?.trim();
  const stored = window.localStorage.getItem(PUBLIC_LOCATION_STORAGE_KEY)?.trim();
  const storedCoordinates = window.localStorage.getItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY)?.trim();
  const storedSource = window.localStorage.getItem(PUBLIC_LOCATION_SOURCE_STORAGE_KEY)?.trim() as PublicLocationSource | null;
  const parsedCoordinates = storedCoordinates ? readStoredPublicCoordinates(storedCoordinates) : null;
  if ((storedMode === "current" || parsedCoordinates) && parsedCoordinates) {
    return {
      mode: "current",
      location: "",
      coordinates: parsedCoordinates,
      source: "browser",
    };
  }
  if (storedMode === "current") {
    const fallbackLocation = stored ? normalizePublicLocationSelection(stored) || PUBLIC_DEFAULT_LOCATION : PUBLIC_DEFAULT_LOCATION;
    return {
      mode: "city",
      location: fallbackLocation,
      coordinates: null,
      source: fallbackLocation === PUBLIC_DEFAULT_LOCATION ? "default" : "manual",
    };
  }
  const normalizedStoredLocation = stored ? normalizePublicLocationSelection(stored) : "";
  if (normalizedStoredLocation && normalizedStoredLocation !== PUBLIC_CURRENT_LOCATION_LABEL) {
    return {
      mode: "city",
      location: normalizedStoredLocation,
      coordinates: null,
      source: storedSource === "manual" ? "manual" : "default",
    };
  }
  return { mode: "city", location: PUBLIC_DEFAULT_LOCATION, coordinates: null, source: "default" };
}

export function savePublicLocation(state: PublicLocationState) {
  if (typeof window === "undefined") {
    return;
  }
  const coordinates = state.coordinates && Number.isFinite(state.coordinates.latitude) && Number.isFinite(state.coordinates.longitude)
    ? state.coordinates
    : null;
  if (state.mode === "current" && coordinates) {
    window.localStorage.setItem(PUBLIC_LOCATION_MODE_STORAGE_KEY, "current");
    window.localStorage.removeItem(PUBLIC_LOCATION_STORAGE_KEY);
    window.localStorage.setItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY, JSON.stringify(coordinates));
    window.localStorage.setItem(PUBLIC_LOCATION_SOURCE_STORAGE_KEY, "browser");
    return;
  }
  const normalizedLocation = normalizePublicLocationSelection(state.location);
  const cityLocation = normalizedLocation && normalizedLocation !== PUBLIC_CURRENT_LOCATION_LABEL ? normalizedLocation : PUBLIC_DEFAULT_LOCATION;
  window.localStorage.setItem(PUBLIC_LOCATION_MODE_STORAGE_KEY, "city");
  window.localStorage.setItem(PUBLIC_LOCATION_STORAGE_KEY, cityLocation);
  window.localStorage.setItem(PUBLIC_LOCATION_SOURCE_STORAGE_KEY, cityLocation === PUBLIC_DEFAULT_LOCATION ? "default" : "manual");
  window.localStorage.removeItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY);
}

export function PublicLocationProvider({ children }: { children: ReactNode }) {
  const [locationState, setLocationState] = useState<PublicLocationState>(() => readStoredPublicLocation());

  useEffect(() => {
    savePublicLocation(locationState);
  }, [locationState]);

  const value = useMemo<PublicLocationContextValue>(
    () => ({
      locationState,
      setLocationState,
      setSelectedLocation(nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
        const coordinates = nextCoordinates && Number.isFinite(nextCoordinates.latitude) && Number.isFinite(nextCoordinates.longitude)
          ? nextCoordinates
          : null;
        if (coordinates) {
          setLocationState({
            mode: "current",
            location: "",
            coordinates,
            source: "browser",
          });
          return;
        }
        const normalizedLocation = normalizePublicLocationSelection(nextLocation);
        const cityLocation = normalizedLocation && normalizedLocation !== PUBLIC_CURRENT_LOCATION_LABEL ? normalizedLocation : PUBLIC_DEFAULT_LOCATION;
        setLocationState({
          mode: "city",
          location: cityLocation,
          coordinates: null,
          source: cityLocation === PUBLIC_DEFAULT_LOCATION ? "default" : "manual",
        });
      },
    }),
    [locationState],
  );

  return <PublicLocationContext.Provider value={value}>{children}</PublicLocationContext.Provider>;
}

export function usePublicLocation() {
  const context = useContext(PublicLocationContext);
  if (!context) {
    return {
      locationState: readStoredPublicLocation(),
      setLocationState: (_nextState: PublicLocationState) => undefined,
      setSelectedLocation: (_nextLocation: string, _nextCoordinates?: PublicLocationCoordinates | null) => undefined,
    };
  }
  return context;
}
