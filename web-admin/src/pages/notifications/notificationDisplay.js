function patientDisplayName(patient) {
  const name = `${patient.firstName} ${patient.lastName || ""}`.trim();
  return patient.patientNumber ? `${name} • ${patient.patientNumber}` : name;
}

export function formatNotificationTargetLabel(row, patients) {
  const directPatient = row.patientId ? patients.find((patient) => patient.id === row.patientId) : null;
  if (directPatient) {
    return patientDisplayName(directPatient);
  }

  if (row.recipient.startsWith("patient:")) {
    const legacyPatientId = row.recipient.slice("patient:".length);
    const legacyPatient = patients.find((patient) => patient.id === legacyPatientId);
    if (legacyPatient) {
      return patientDisplayName(legacyPatient);
    }
    return "Patient record unavailable";
  }

  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(row.recipient)) {
    const sourceLabel = formatNotificationSourceLabel(row);
    return sourceLabel || "Notification target";
  }

  if (row.recipient.trim()) {
    return row.recipient;
  }

  return "Notification target";
}

export function formatNotificationSourceLabel(row) {
  const subject = row.subject?.trim();
  const sourceType = row.sourceType?.trim().toUpperCase() || "";
  if (sourceType === "APPOINTMENT") {
    return subject ? `Appointment • ${subject}` : "Appointment";
  }
  if (sourceType === "BILL") {
    return subject ? `Bill • ${subject}` : "Bill";
  }
  if (sourceType === "PRESCRIPTION") {
    return subject ? `Prescription • ${subject}` : "Prescription";
  }
  if (sourceType === "LAB_ORDER") {
    return subject ? `Lab • ${subject}` : "Lab";
  }
  if (sourceType === "RECEIPT") {
    return subject ? `Receipt • ${subject}` : "Receipt";
  }
  return subject || row.eventType.replaceAll("_", " ");
}
