function normalizeText(value) {
  return (value || "").trim();
}

function parseDate(value) {
  const normalized = normalizeText(value);
  if (!normalized) return Number.NEGATIVE_INFINITY;
  const parsed = Date.parse(normalized);
  return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
}

function firstText(...values) {
  for (const value of values) {
    const normalized = normalizeText(value);
    if (normalized) return normalized;
  }
  return null;
}

export function buildConsultationHistoryEntries(previousVisits, currentConsultation, currentConsultationId) {
  const entries = [];
  if (currentConsultation?.id || currentConsultationId) {
    entries.push({
      consultationId: currentConsultation?.id || currentConsultationId || "current",
      consultationDate: currentConsultation?.createdAt || currentConsultation?.completedAt || null,
      status: currentConsultation?.status || null,
      doctor: currentConsultation?.doctorName || null,
      primaryText: firstText(currentConsultation?.diagnosis, currentConsultation?.chiefComplaints, "Current consultation"),
      followUp: currentConsultation?.followUpDate || null,
      detail: firstText(currentConsultation?.clinicalNotes, currentConsultation?.advice),
      isCurrent: true,
    });
  }

  for (const visit of previousVisits || []) {
    if (currentConsultationId && (visit?.consultationId || visit?.id) === currentConsultationId) {
      continue;
    }
    const consultationDate = normalizeText(visit?.createdAt || visit?.consultationDate);
    entries.push({
      consultationId: visit?.consultationId || visit?.id || "unknown",
      consultationDate: consultationDate || null,
      status: visit?.status || null,
      doctor: visit?.doctorName || null,
      primaryText: firstText(visit?.diagnosis, visit?.chiefComplaints, "Consultation"),
      followUp: normalizeText(visit?.followUpDate) || null,
      detail: firstText(visit?.treatmentSummary, visit?.advice, visit?.clinicalNotes),
      isCurrent: false,
    });
  }

  return entries.sort((left, right) => {
    const leftDate = parseDate(left.consultationDate);
    const rightDate = parseDate(right.consultationDate);
    if (leftDate !== rightDate) {
      return rightDate - leftDate;
    }
    if (left.isCurrent !== right.isCurrent) {
      return left.isCurrent ? -1 : 1;
    }
    return String(left.consultationId).localeCompare(String(right.consultationId));
  });
}

export function getVisibleConsultationHistoryItems(items, expanded, limit) {
  return expanded ? items : items.slice(0, limit);
}

export function shouldShowConsultationHistoryToggle(total, limit) {
  return total > limit;
}

export function getConsultationHistorySectionTitle(total) {
  return total > 0 ? `History at a glance (${total} consultations)` : "History at a glance";
}

export function getConsultationHistoryToggleLabel(expanded) {
  return expanded ? "Show less" : "View all";
}
