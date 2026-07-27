function stripDiacritics(value: string) {
  return value.normalize("NFKD").replace(/[\u0300-\u036f]/g, "");
}

export function normalizeDiscoveryText(value: unknown) {
  return stripDiacritics(String(value ?? ""))
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function tokenizeDiscoveryQuery(query: string) {
  return normalizeDiscoveryText(query).split(/\s+/).filter(Boolean);
}

export function matchesDiscoveryQuery(fields: unknown[], query: string) {
  const tokens = tokenizeDiscoveryQuery(query);
  if (!tokens.length) {
    return true;
  }
  const haystack = normalizeDiscoveryText(fields.filter(Boolean).join(" "));
  if (!haystack) {
    return false;
  }
  return tokens.every((token) => haystack.includes(token));
}

export function scoreDiscoveryLocation(city: string | null | undefined, area: string | null | undefined, selectedLocation: string) {
  const target = normalizeDiscoveryText(selectedLocation);
  if (!target) {
    return 0;
  }
  const normalizedCity = normalizeDiscoveryText(city);
  const normalizedArea = normalizeDiscoveryText(area);
  if (!normalizedCity && !normalizedArea) {
    return 0;
  }
  if (normalizedCity === target) {
    return 30;
  }
  if (normalizedCity.includes(target) || target.includes(normalizedCity)) {
    return 20;
  }
  if (normalizedArea === target) {
    return 15;
  }
  if (normalizedArea.includes(target) || target.includes(normalizedArea)) {
    return 10;
  }
  if (`${normalizedCity} ${normalizedArea}`.includes(target)) {
    return 8;
  }
  return 0;
}

export function discoveryEmptyMessage({
  query,
  selectedLocation,
  defaultMessage = "No matching results found. Try changing location or search term.",
}: {
  query?: string | null;
  selectedLocation?: string | null;
  defaultMessage?: string;
}) {
  if (query?.trim() || selectedLocation?.trim()) {
    const locationLabel = selectedLocation?.trim() ? ` for ${selectedLocation.trim()}` : "";
    return `No matching results found${locationLabel}. Try changing location or search term.`;
  }
  return defaultMessage;
}

export function slugify(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}
