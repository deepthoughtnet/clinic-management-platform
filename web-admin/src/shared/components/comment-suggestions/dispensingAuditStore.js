const STORAGE_KEY = "arogia.pharmacy.dispensing.audit";
const STATE_KEY = "arogia.pharmacy.dispensing.state";

export const DISPENSING_STATUSES = [
  "READY_FOR_DISPENSE",
  "NOT_DISPENSED",
  "PARTIALLY_DISPENSED",
  "FULLY_DISPENSED",
  "BOUGHT_EXTERNALLY",
  "PATIENT_DECLINED",
  "UNAVAILABLE_CLOSED",
  "CANCELLED",
  "EXPIRED",
];

function readJson(storage, key, fallback) {
  try {
    const raw = storage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function writeJson(storage, key, value) {
  storage.setItem(key, JSON.stringify(value));
}

export function readDispensingAuditEntries(storage = window.localStorage) {
  return readJson(storage, STORAGE_KEY, []);
}

export function appendDispensingAuditEntry(storage, entry) {
  const list = readDispensingAuditEntries(storage);
  const next = [...list, entry];
  writeJson(storage, STORAGE_KEY, next.slice(-500));
  return next;
}

export function readDispensingState(storage = window.localStorage) {
  return readJson(storage, STATE_KEY, {});
}

export function setPrescriptionDispensingState(storage, prescriptionId, state) {
  const current = readDispensingState(storage);
  const next = { ...current, [prescriptionId]: state };
  writeJson(storage, STATE_KEY, next);
  return next;
}

export function getPrescriptionDispensingState(storage, prescriptionId) {
  const current = readDispensingState(storage);
  return current[prescriptionId] || null;
}

export function isTerminalDispensingState(status) {
  return ["FULLY_DISPENSED", "BOUGHT_EXTERNALLY", "PATIENT_DECLINED", "UNAVAILABLE_CLOSED", "CANCELLED", "EXPIRED"].includes(status);
}

export function shouldHideFromActiveQueue(storage, prescriptionId, backendStatus) {
  const state = getPrescriptionDispensingState(storage, prescriptionId);
  return isTerminalDispensingState(state?.status || backendStatus);
}
