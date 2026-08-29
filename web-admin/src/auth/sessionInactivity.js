export const DEFAULT_INACTIVITY_TIMEOUT_MINUTES = 30;
export const DEFAULT_INACTIVITY_WARNING_LEAD_MINUTES = 2;

export const INACTIVITY_WARNING_MESSAGE = "Your session will expire soon due to inactivity.";
export const INACTIVITY_EXPIRED_MESSAGE = "Your session has expired due to inactivity. Please sign in again.";
export const SESSION_EXPIRED_MESSAGE = "Your session has expired. Please sign in again.";

export const SESSION_ACTIVITY_STORAGE_KEY = "clinic_auth_last_activity_at";
export const SESSION_EVENT_STORAGE_KEY = "clinic_auth_session_event";

function toPositiveNumber(raw, fallback) {
  const parsed = typeof raw === "number" ? raw : Number.parseInt(String(raw ?? "").trim(), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function resolveInactivityTimeoutMinutes(rawValue) {
  return toPositiveNumber(rawValue, DEFAULT_INACTIVITY_TIMEOUT_MINUTES);
}

export function resolveInactivityTimeoutMs(rawValue) {
  return resolveInactivityTimeoutMinutes(rawValue) * 60_000;
}

export function resolveInactivityWarningLeadMs(timeoutMs, warningLeadMs = DEFAULT_INACTIVITY_WARNING_LEAD_MINUTES * 60_000) {
  const safeTimeoutMs = Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : DEFAULT_INACTIVITY_TIMEOUT_MINUTES * 60_000;
  return Math.min(Math.max(0, warningLeadMs), Math.max(0, safeTimeoutMs - 60_000));
}

export function computeInactivityWindow(lastActivityAtMs, nowMs, timeoutMs, warningLeadMs = DEFAULT_INACTIVITY_WARNING_LEAD_MINUTES * 60_000) {
  const safeLastActivityAtMs = Number.isFinite(lastActivityAtMs) && lastActivityAtMs > 0 ? lastActivityAtMs : nowMs;
  const safeNowMs = Number.isFinite(nowMs) && nowMs > 0 ? nowMs : Date.now();
  const safeTimeoutMs = Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : DEFAULT_INACTIVITY_TIMEOUT_MINUTES * 60_000;
  const effectiveWarningLeadMs = resolveInactivityWarningLeadMs(safeTimeoutMs, warningLeadMs);
  const expiresAt = safeLastActivityAtMs + safeTimeoutMs;
  const warningAt = expiresAt - effectiveWarningLeadMs;
  return {
    warningAt,
    expiresAt,
    warningInMs: Math.max(0, warningAt - safeNowMs),
    expiresInMs: Math.max(0, expiresAt - safeNowMs),
    shouldWarn: safeNowMs >= warningAt && safeNowMs < expiresAt,
    shouldExpire: safeNowMs >= expiresAt,
  };
}

export function parseSharedActivityTimestamp(rawValue) {
  if (rawValue == null || rawValue === "") {
    return null;
  }
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

export function createSharedSessionEvent(reason) {
  return JSON.stringify({
    type: "logout",
    reason,
    at: Date.now(),
  });
}

export function parseSharedSessionEvent(rawValue) {
  if (!rawValue) {
    return null;
  }
  try {
    const parsed = JSON.parse(rawValue);
    if (!parsed || parsed.type !== "logout") {
      return null;
    }
    return {
      type: "logout",
      reason: typeof parsed.reason === "string" ? parsed.reason : "expired",
      at: Number.isFinite(Number(parsed.at)) ? Number(parsed.at) : null,
    };
  } catch {
    return null;
  }
}
