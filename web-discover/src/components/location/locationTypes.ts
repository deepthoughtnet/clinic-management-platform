export type LocationCoordinates = {
  latitude: number;
  longitude: number;
};

export type LocationMapEntry = {
  label?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  pinCode?: string | null;
  workingHours?: string | null;
  parkingAvailable?: boolean;
  accessibilityAvailable?: boolean;
  latitude?: number | null;
  longitude?: number | null;
};

export type LocationSearchResult = {
  label: string;
  coordinates: LocationCoordinates;
  displayName: string;
};

export function hasCoordinates(value: LocationMapEntry | LocationCoordinates | null | undefined): value is LocationCoordinates {
  return Boolean(value && typeof (value as LocationCoordinates).latitude === "number" && typeof (value as LocationCoordinates).longitude === "number");
}
