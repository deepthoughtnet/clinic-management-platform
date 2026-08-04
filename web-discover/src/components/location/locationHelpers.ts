import { discoverConfig } from "../../config";
import type { LocationCoordinates, LocationMapEntry } from "./locationTypes";
import { buildPublicAddressView, formatPublicAddressCompact } from "../../utils/publicProfileFormatting";

export function locationAddressLine(location: Pick<LocationMapEntry, "addressLine1" | "addressLine2" | "address" | "area" | "city" | "state" | "country" | "pinCode" | "postalCode"> | null | undefined) {
  return buildPublicAddressView(location).lines.join("\n");
}

export function locationAddressCompact(location: Pick<LocationMapEntry, "addressLine1" | "addressLine2" | "address" | "area" | "city" | "state" | "country" | "pinCode" | "postalCode"> | null | undefined) {
  return formatPublicAddressCompact(location);
}

export function locationDisplayLabel(location: Pick<LocationMapEntry, "label" | "city" | "state"> | null | undefined, fallback: string) {
  const label = location?.label?.trim();
  if (label) {
    return label;
  }
  const address = [location?.city, location?.state].filter(Boolean).join(", ");
  return address || fallback;
}

export function locationCoordinates(location: Pick<LocationMapEntry, "latitude" | "longitude"> | null | undefined): LocationCoordinates | null {
  if (location?.latitude == null || location?.longitude == null) {
    return null;
  }
  if (!Number.isFinite(location.latitude) || !Number.isFinite(location.longitude)) {
    return null;
  }
  return {
    latitude: location.latitude,
    longitude: location.longitude,
  };
}

export function buildDirectionsUrl(location: Pick<LocationMapEntry, "address" | "city" | "state" | "country" | "pinCode" | "latitude" | "longitude"> | null | undefined) {
  const template = discoverConfig.mapDirectionsUrlTemplate.trim();
  if (!template || !location) {
    return "";
  }
  const coordinates = locationCoordinates(location);
  const addressQuery = locationAddressLine(location);
  const hasQueryPlaceholder = template.includes("{query}") || template.includes("{address}");
  if (!coordinates && (!hasQueryPlaceholder || !addressQuery.trim())) {
    return "";
  }
  const query = encodeURIComponent(addressQuery);
  return template
    .replaceAll("{latitude}", coordinates ? `${coordinates.latitude}` : "")
    .replaceAll("{longitude}", coordinates ? `${coordinates.longitude}` : "")
    .replaceAll("{query}", query)
    .replaceAll("{address}", query);
}
