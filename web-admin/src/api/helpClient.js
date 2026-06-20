import { keycloak } from "../auth/keycloak.js";

export function resolveHelpAccessToken(token) {
  const explicit = typeof token === "string" ? token.trim() : "";
  if (explicit) {
    return explicit;
  }
  const fallback = (keycloak.token || "").trim();
  return fallback || undefined;
}

export function buildHelpRequestOptions(token) {
  return {
    token: resolveHelpAccessToken(token),
    requireTenant: false,
  };
}
