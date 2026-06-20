export type QueueFilter =
  | "ACTIVE"
  | "ALL"
  | "PENDING"
  | "PARTIAL"
  | "FULLY_DISPENSED"
  | "BOUGHT_EXTERNALLY"
  | "PATIENT_DECLINED"
  | "UNAVAILABLE_CLOSED"
  | "CANCELLED"
  | "EXPIRED";

export type DispensingQueueOption = {
  value: QueueFilter;
  label: string;
};

export const QUEUE_FILTER_OPTIONS: readonly DispensingQueueOption[];

export function isTerminalDispenseStatus(status: string | null | undefined): boolean;
export function isActiveDispenseStatus(status: string | null | undefined): boolean;
export function normalizeDispensingSearch(value: unknown): string;
export function matchesDispensingSearch(row: {
  prescriptionNumber?: string | null;
  patientName?: string | null;
  doctorName?: string | null;
  lines?: Array<{ prescribedMedicineName?: string | null } | null> | null;
}, term: string): boolean;
export function resolveQueueFilterStatus(status: string | null | undefined): string;
export function queueRowMatchesFilter(status: string | null | undefined, filter: QueueFilter | string): boolean;
