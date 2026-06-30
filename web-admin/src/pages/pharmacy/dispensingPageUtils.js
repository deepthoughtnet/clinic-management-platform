const ACTIVE_STATUSES = new Set(["READY_FOR_DISPENSE", "NOT_DISPENSED", "PARTIALLY_DISPENSED"]);
const TERMINAL_STATUSES = new Set([
  "FULLY_DISPENSED",
  "BOUGHT_EXTERNALLY",
  "PATIENT_DECLINED",
  "UNAVAILABLE_CLOSED",
  "CANCELLED",
  "EXPIRED",
]);

export const QUEUE_FILTER_OPTIONS = [
  { value: "ACTIVE", label: "Active" },
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "PARTIAL", label: "Partial" },
  { value: "OUT_OF_STOCK", label: "Out of stock" },
  { value: "FULLY_DISPENSED", label: "Fully Dispensed" },
  { value: "BOUGHT_EXTERNALLY", label: "Bought Outside" },
  { value: "PATIENT_DECLINED", label: "Patient Declined" },
  { value: "UNAVAILABLE_CLOSED", label: "Unavailable Closed" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "EXPIRED", label: "Expired" },
];

export function isTerminalDispenseStatus(status) {
  return TERMINAL_STATUSES.has(String(status || "").trim().toUpperCase());
}

export function isActiveDispenseStatus(status) {
  return ACTIVE_STATUSES.has(String(status || "").trim().toUpperCase());
}

export function normalizeDispensingSearch(value) {
  return String(value || "").trim().toLowerCase();
}

export function matchesDispensingSearch(row, term) {
  const query = normalizeDispensingSearch(term);
  if (!query) return true;
  const medicineNames = Array.isArray(row.lines) ? row.lines.map((line) => line?.prescribedMedicineName).filter(Boolean) : [];
  return [row.prescriptionNumber, row.patientName, row.doctorName, ...medicineNames]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(query));
}

export function resolveQueueFilterStatus(status) {
  const normalized = String(status || "").trim().toUpperCase();
  return normalized || "NOT_DISPENSED";
}

export function queueRowMatchesFilter(status, filter) {
  const normalized = resolveQueueFilterStatus(status);
  switch (filter) {
    case "ACTIVE":
      return isActiveDispenseStatus(normalized);
    case "ALL":
      return true;
    case "PENDING":
      return normalized === "NOT_DISPENSED" || normalized === "READY_FOR_DISPENSE";
    case "PARTIAL":
      return normalized === "PARTIALLY_DISPENSED";
    case "OUT_OF_STOCK":
      return false;
    case "FULLY_DISPENSED":
      return normalized === "FULLY_DISPENSED";
    case "BOUGHT_EXTERNALLY":
      return normalized === "BOUGHT_EXTERNALLY";
    case "PATIENT_DECLINED":
      return normalized === "PATIENT_DECLINED";
    case "UNAVAILABLE_CLOSED":
      return normalized === "UNAVAILABLE_CLOSED";
    case "CANCELLED":
      return normalized === "CANCELLED";
    case "EXPIRED":
      return normalized === "EXPIRED";
    default:
      return true;
  }
}
