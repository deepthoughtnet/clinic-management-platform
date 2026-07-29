import { discoverConfig } from "../../config";
import type { LocationSearchResult } from "./locationTypes";

function buildGeocodingUrl(query: string) {
  if (!discoverConfig.geocodingBaseUrl.trim() || !discoverConfig.geocodingSearchPath.trim()) {
    return null;
  }
  const url = new URL(discoverConfig.geocodingSearchPath, discoverConfig.geocodingBaseUrl);
  if (discoverConfig.geocodingProvider.toLowerCase() === "nominatim") {
    url.searchParams.set("q", query);
    url.searchParams.set("format", "jsonv2");
    url.searchParams.set("addressdetails", "1");
    url.searchParams.set("limit", "5");
  } else {
    url.searchParams.set("q", query);
    url.searchParams.set("limit", "5");
  }
  return url;
}

function parseResult(entry: Record<string, unknown>): LocationSearchResult | null {
  const latitudeRaw = entry.lat ?? entry.latitude;
  const longitudeRaw = entry.lon ?? entry.lng ?? entry.longitude;
  const latitude = Number(latitudeRaw);
  const longitude = Number(longitudeRaw);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null;
  }
  const label = typeof entry.display_name === "string"
    ? entry.display_name
    : typeof entry.label === "string"
      ? entry.label
      : typeof entry.name === "string"
        ? entry.name
        : "";
  if (!label.trim()) {
    return null;
  }
  return {
    label,
    displayName: label,
    coordinates: {
      latitude,
      longitude,
    },
  };
}

export async function geocodeLocation(query: string, signal?: AbortSignal): Promise<LocationSearchResult[]> {
  const trimmed = query.trim();
  if (!trimmed) {
    return [];
  }
  const url = buildGeocodingUrl(trimmed);
  if (!url) {
    return [];
  }
  const response = await fetch(url.toString(), {
    headers: { Accept: "application/json" },
    signal,
  });
  if (!response.ok) {
    throw new Error(`Location search failed with status ${response.status}.`);
  }
  const body = (await response.json()) as unknown;
  const entries = Array.isArray(body) ? body : Array.isArray((body as { results?: unknown }).results) ? (body as { results: unknown[] }).results : [];
  return entries
    .map((entry) => (entry && typeof entry === "object" ? parseResult(entry as Record<string, unknown>) : null))
    .filter((item): item is LocationSearchResult => Boolean(item));
}
