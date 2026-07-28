import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from "react";

const PUBLIC_LOCATION_STORAGE_KEY = "clinic-web-public-location";
const PUBLIC_LOCATION_COORDS_STORAGE_KEY = "clinic-web-public-location-coordinates";
const PUBLIC_LOCATION_SOURCE_STORAGE_KEY = "clinic-web-public-location-source";

export const PUBLIC_LOCATION_OPTIONS = [
  "Pune",
  "Mumbai",
  "Bangalore",
  "Delhi",
  "Hyderabad",
  "Chennai",
  "Bhopal",
] as const;

export const PUBLIC_CURRENT_LOCATION_LABEL = "Current location selected";
export const PUBLIC_DEFAULT_LOCATION = "Pune";

export type PublicLocationCoordinates = {
  latitude: number;
  longitude: number;
};

export type PublicLocationSource = "default" | "manual" | "browser";

export type PublicLocationState = {
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
  return value.trim().slice(0, 60);
}

export function readStoredPublicLocation(): PublicLocationState {
  if (typeof window === "undefined") {
    return { location: PUBLIC_DEFAULT_LOCATION, coordinates: null, source: "default" };
  }
  const stored = window.localStorage.getItem(PUBLIC_LOCATION_STORAGE_KEY)?.trim();
  const storedCoordinates = window.localStorage.getItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY)?.trim();
  const storedSource = window.localStorage.getItem(PUBLIC_LOCATION_SOURCE_STORAGE_KEY)?.trim() as PublicLocationSource | null;
  if (stored) {
    return {
      location: stored,
      coordinates: storedCoordinates ? readStoredPublicCoordinates(storedCoordinates) : null,
      source: storedCoordinates ? "browser" : storedSource === "browser" ? "browser" : "manual",
    };
  }
  if (storedCoordinates) {
    return {
      location: PUBLIC_CURRENT_LOCATION_LABEL,
      coordinates: readStoredPublicCoordinates(storedCoordinates),
      source: "browser",
    };
  }
  return { location: PUBLIC_DEFAULT_LOCATION, coordinates: null, source: "default" };
}

export function savePublicLocation(state: PublicLocationState) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(PUBLIC_LOCATION_STORAGE_KEY, state.location);
  window.localStorage.setItem(PUBLIC_LOCATION_SOURCE_STORAGE_KEY, state.coordinates ? "browser" : "manual");
  if (state.coordinates) {
    window.localStorage.setItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY, JSON.stringify(state.coordinates));
  } else {
    window.localStorage.removeItem(PUBLIC_LOCATION_COORDS_STORAGE_KEY);
  }
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
        const normalizedLocation = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
        setLocationState({
          location: normalizedLocation,
          coordinates: nextCoordinates,
          source: nextCoordinates ? "browser" : "manual",
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
