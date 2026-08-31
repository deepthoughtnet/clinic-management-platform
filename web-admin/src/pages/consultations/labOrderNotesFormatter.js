export const LAB_ORDER_NOTES_MAX_LENGTH = 250;

function normalizeLine(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function truncateToWordBoundary(text, maxLength) {
  const normalized = normalizeLine(text);
  if (!normalized) {
    return "";
  }
  if (normalized.length <= maxLength) {
    return normalized;
  }
  if (maxLength <= 1) {
    return "…".slice(0, maxLength);
  }
  const limit = Math.max(1, maxLength - 1);
  const candidate = normalized.slice(0, limit).trimEnd();
  const lastSpace = candidate.lastIndexOf(" ");
  if (lastSpace > 0) {
    const shortened = candidate.slice(0, lastSpace).trimEnd();
    return shortened ? `${shortened}…` : "…";
  }
  return `${candidate}…`;
}

function appendBoundedLine(lines, line, maxLength) {
  const normalized = normalizeLine(line);
  if (!normalized) {
    return lines;
  }
  const next = [...lines, normalized];
  if (next.join("\n").length <= maxLength) {
    return next;
  }
  const prefix = lines.length ? `${lines.join("\n")}\n` : "";
  const remaining = maxLength - prefix.length;
  if (remaining <= 0) {
    return lines;
  }
  const shortened = truncateToWordBoundary(normalized, remaining);
  if (!shortened) {
    return lines;
  }
  const shortenedNext = [...lines, shortened];
  return shortenedNext.join("\n").length <= maxLength ? shortenedNext : lines;
}

export function buildAiLabOrderNotes({
  generatedAt,
  reason,
  supportingEvidence,
  suggestedPriority,
  duplicateWarnings,
  maxLength = LAB_ORDER_NOTES_MAX_LENGTH,
}) {
  const lines = [];
  const evidenceText = Array.isArray(supportingEvidence) ? supportingEvidence.filter(Boolean).join(" • ") : normalizeLine(supportingEvidence);
  const warningText = Array.isArray(duplicateWarnings) ? duplicateWarnings.filter(Boolean).join(" • ") : normalizeLine(duplicateWarnings);

  [
    "Prepared from AI Recommendation",
    generatedAt ? `Generated: ${normalizeLine(generatedAt)}` : null,
    reason ? `Reason: ${normalizeLine(reason)}` : null,
    evidenceText ? `Supporting evidence: ${evidenceText}` : null,
    suggestedPriority ? `Suggested priority: ${normalizeLine(suggestedPriority)}` : null,
    warningText ? `Warnings: ${warningText}` : null,
    "Doctor review and confirmation are required before any laboratory request is created.",
  ].forEach((line) => {
    if (line) {
      const next = appendBoundedLine(lines, line, maxLength);
      lines.splice(0, lines.length, ...next);
    }
  });

  return lines.join("\n").trim();
}
