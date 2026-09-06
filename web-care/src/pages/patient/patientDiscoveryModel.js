export function normalizeDiscoveryText(value) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ")
    .toLowerCase();
}

export function dedupeByExplicitKey(items, keySelector) {
  const seen = new Set();
  const merged = [];

  items.forEach((item, index) => {
    const key = normalizeDiscoveryText(keySelector(item, index));
    if (key) {
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
    }
    merged.push(item);
  });

  return merged;
}

export function resolveTelHref(phone) {
  const normalized = String(phone ?? "").trim();
  if (!normalized) {
    return null;
  }
  const digits = normalized.replace(/[^\d+]/g, "");
  return digits ? `tel:${digits}` : null;
}

export function normalizePublicClinicDisplayName(value) {
  const trimmed = String(value ?? "").trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.toLowerCase() === "primary") {
    return null;
  }
  return trimmed;
}
