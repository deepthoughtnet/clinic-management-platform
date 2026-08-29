export const DEFAULT_INACTIVITY_TIMEOUT_MINUTES: number;
export const DEFAULT_INACTIVITY_WARNING_LEAD_MINUTES: number;
export const INACTIVITY_WARNING_MESSAGE: string;
export const INACTIVITY_EXPIRED_MESSAGE: string;
export const SESSION_EXPIRED_MESSAGE: string;
export const SESSION_ACTIVITY_STORAGE_KEY: string;
export const SESSION_EVENT_STORAGE_KEY: string;

export function resolveInactivityTimeoutMinutes(rawValue: unknown): number;
export function resolveInactivityTimeoutMs(rawValue: unknown): number;
export function resolveInactivityWarningLeadMs(timeoutMs: number, warningLeadMs?: number): number;
export function computeInactivityWindow(
  lastActivityAtMs: number,
  nowMs: number,
  timeoutMs: number,
  warningLeadMs?: number,
): {
  warningAt: number;
  expiresAt: number;
  warningInMs: number;
  expiresInMs: number;
  shouldWarn: boolean;
  shouldExpire: boolean;
};
export function parseSharedActivityTimestamp(rawValue: string | null | undefined): number | null;
export function createSharedSessionEvent(reason: string): string;
export function parseSharedSessionEvent(rawValue: string | null | undefined): {
  type: "logout";
  reason: string;
  at: number | null;
} | null;
