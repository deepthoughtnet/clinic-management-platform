const TRUSTED_VERIFICATION_STATUSES = new Set(["ACCEPTED", "VERIFIED", "CONFIRMED", "EDITED"]);

function normalizeText(value) {
  return (value || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function parseObservedOn(value) {
  const normalized = (value || "").trim();
  if (!normalized) return Number.NEGATIVE_INFINITY;
  const parsed = Date.parse(normalized);
  return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
}

function stableLabProjectionKey(concept) {
  const conceptKey = normalizeText(concept?.conceptKey);
  if (conceptKey) return conceptKey;
  return normalizeText(concept?.label);
}

function isTrustedLongitudinalLab(concept) {
  if (!concept || normalizeText(concept.conceptFamily) !== "lab_result") {
    return false;
  }
  return TRUSTED_VERIFICATION_STATUSES.has(normalizeText(concept.verificationStatus).toUpperCase());
}

function compareLabObservations(a, b) {
  const observedDiff = parseObservedOn(a?.observedOn) - parseObservedOn(b?.observedOn);
  if (observedDiff !== 0) return observedDiff;

  const sourceIdA = normalizeText(a?.sourceDocumentId);
  const sourceIdB = normalizeText(b?.sourceDocumentId);
  if (sourceIdA !== sourceIdB) return sourceIdA < sourceIdB ? -1 : 1;

  const confidenceA = Number.isFinite(Number(a?.confidence)) ? Number(a.confidence) : Number.NEGATIVE_INFINITY;
  const confidenceB = Number.isFinite(Number(b?.confidence)) ? Number(b.confidence) : Number.NEGATIVE_INFINITY;
  if (confidenceA !== confidenceB) return confidenceA - confidenceB;

  const labelA = normalizeText(a?.label);
  const labelB = normalizeText(b?.label);
  if (labelA !== labelB) return labelA < labelB ? -1 : 1;

  const valueA = normalizeText(a?.valueText);
  const valueB = normalizeText(b?.valueText);
  if (valueA !== valueB) return valueA < valueB ? -1 : 1;

  return 0;
}

export function buildLatestTrustedLabProjection(history) {
  const winners = new Map();
  for (const concept of history || []) {
    if (!isTrustedLongitudinalLab(concept)) {
      continue;
    }
    const key = stableLabProjectionKey(concept);
    if (!key) {
      continue;
    }
    const current = winners.get(key);
    if (!current || compareLabObservations(concept, current) > 0) {
      winners.set(key, concept);
    }
  }
  return Array.from(winners.values()).sort((left, right) => compareLabObservations(right, left));
}

export function isTrustedLongitudinalLabConcept(concept) {
  return isTrustedLongitudinalLab(concept);
}

