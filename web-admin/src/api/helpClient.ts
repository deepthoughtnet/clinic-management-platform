import { keycloak } from "../auth/keycloakClient";

export type HelpRequestOptions = {
  token?: string;
  requireTenant: false;
};

export function resolveHelpAccessToken(token?: string | null): string | undefined {
  const explicit = typeof token === "string" ? token.trim() : "";
  if (explicit) {
    return explicit;
  }
  const fallback = keycloak.token?.trim() || "";
  return fallback || undefined;
}

export function buildHelpRequestOptions(token?: string | null): HelpRequestOptions {
  return {
    token: resolveHelpAccessToken(token),
    requireTenant: false,
  };
}
