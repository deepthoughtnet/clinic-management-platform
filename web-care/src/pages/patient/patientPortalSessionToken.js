function decodeBase64Url(value) {
  if (!value) {
    return "";
  }
  const normalized = `${value}`.replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4;
  const padded = normalized + (padding ? "=".repeat(4 - padding) : "");
  if (typeof atob === "function") {
    return atob(padded);
  }
  if (typeof Buffer !== "undefined") {
    return Buffer.from(padded, "base64").toString("binary");
  }
  return "";
}

export function decodePatientPortalSessionTokenPayload(token) {
  if (!token) {
    return null;
  }
  const parts = `${token}`.trim().split(".");
  if (parts.length !== 2) {
    return null;
  }
  try {
    const payloadText = decodeBase64Url(parts[0]);
    if (!payloadText) {
      return null;
    }
    const parsed = JSON.parse(payloadText);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

export function isPatientPortalSessionTokenActive(token, now = Date.now()) {
  const payload = decodePatientPortalSessionTokenPayload(token);
  if (!payload || typeof payload.exp !== "string") {
    return false;
  }
  const expiryTime = Date.parse(payload.exp);
  return Number.isFinite(expiryTime) && expiryTime > now;
}
