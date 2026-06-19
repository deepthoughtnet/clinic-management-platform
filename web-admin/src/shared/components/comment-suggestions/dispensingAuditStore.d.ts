export type DispensingAuditEntry = {
  prescriptionId: string;
  medicineLineId: string | null;
  action: string;
  previousStatus: string;
  newStatus: string;
  quantity: number | null;
  batch: string | null;
  reason: string | null;
  remarks: string | null;
  user: string;
  timestamp: string;
};

export type DispensingState = {
  status: string;
  lineStates?: Record<string, string> | null;
};

export declare const DISPENSING_STATUSES: readonly string[];
export declare function readDispensingAuditEntries(storage?: Pick<Storage, "getItem">): DispensingAuditEntry[];
export declare function appendDispensingAuditEntry(storage: Pick<Storage, "getItem" | "setItem">, entry: DispensingAuditEntry): DispensingAuditEntry[];
export declare function readDispensingState(storage?: Pick<Storage, "getItem">): Record<string, DispensingState>;
export declare function setPrescriptionDispensingState(storage: Pick<Storage, "getItem" | "setItem">, prescriptionId: string, state: DispensingState): Record<string, DispensingState>;
export declare function getPrescriptionDispensingState(storage: Pick<Storage, "getItem">, prescriptionId: string): DispensingState | null;
export declare function isTerminalDispensingState(status: string | null | undefined): boolean;
export declare function shouldHideFromActiveQueue(storage: Pick<Storage, "getItem">, prescriptionId: string, backendStatus?: string | null): boolean;
