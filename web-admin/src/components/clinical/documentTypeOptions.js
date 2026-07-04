export const STANDARD_DOCUMENT_TYPE_ORDER = [
  { value: "EXTERNAL_LAB_REPORT", label: "External Lab Report" },
  { value: "INTERNAL_LAB_REPORT", label: "Internal Lab Report" },
  { value: "RADIOLOGY_REPORT", label: "Radiology Report" },
  { value: "REFERRAL_LETTER", label: "Referral Letter" },
  { value: "DISCHARGE_SUMMARY", label: "Discharge Summary" },
  { value: "OLD_PRESCRIPTION", label: "Old Prescription" },
  { value: "INSURANCE_DOCUMENT", label: "Insurance Document" },
  { value: "IDENTITY_DOCUMENT", label: "Identity Document" },
  { value: "OTHER", label: "Other" },
];

export const LEGACY_DOCUMENT_TYPE_ALIASES = [
  { value: "LAB_REPORT", label: "External Lab Report" },
  { value: "PRESCRIPTION", label: "Old Prescription" },
  { value: "X_RAY", label: "Radiology Report" },
  { value: "MRI_CT", label: "Radiology Report" },
  { value: "REFERRAL", label: "Referral Letter" },
  { value: "INSURANCE", label: "Insurance Document" },
  { value: "VACCINATION", label: "Other" },
  { value: "ATTACHMENT", label: "Other" },
];

function normalizeDocumentType(value) {
  return String(value || "").trim().toUpperCase();
}

function normalizeDocumentLabel(value, fallback) {
  const normalized = String(value || "").trim();
  if (normalized) return normalized;
  return String(fallback || "").trim();
}

function mergeDocumentTypeRecords(records) {
  const merged = [];
  const seen = new Set();
  for (const item of records) {
    const key = normalizeDocumentType(item?.value);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    merged.push({
      value: key,
      label: normalizeDocumentLabel(item?.label, key.replaceAll("_", " ")),
    });
  }
  return merged;
}

export function buildDocumentTypeOptions(backendDocumentTypes = []) {
  return mergeDocumentTypeRecords([
    ...STANDARD_DOCUMENT_TYPE_ORDER,
    ...backendDocumentTypes,
  ]);
}

export function documentTypeLabel(value) {
  const normalized = normalizeDocumentType(value);
  const found = mergeDocumentTypeRecords([
    ...STANDARD_DOCUMENT_TYPE_ORDER,
    ...LEGACY_DOCUMENT_TYPE_ALIASES,
  ]).find((item) => item.value === normalized);
  return found?.label || normalized.replaceAll("_", " ") || "Document";
}

export function documentTypeStorageKey(value) {
  return normalizeDocumentType(value);
}
