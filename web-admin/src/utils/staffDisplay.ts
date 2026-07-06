const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function staffDisplayName(label?: string | null, raw?: string | null, fallback = "Staff User") {
  const safeLabel = label?.trim();
  if (safeLabel) return safeLabel;
  const safeRaw = raw?.trim();
  if (safeRaw && !UUID_PATTERN.test(safeRaw)) return safeRaw;
  return fallback;
}
