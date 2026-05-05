type JwtPayload = Record<string, unknown>;

function decodeBase64Url(input: string): string {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), "=");
  return atob(padded);
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    return JSON.parse(decodeBase64Url(payload)) as JwtPayload;
  } catch {
    return null;
  }
}

export function extractUsername(payload: JwtPayload | null): string {
  if (!payload) return "Guest";
  const preferred = payload.preferred_username || payload.name || payload.email;
  return typeof preferred === "string" && preferred.trim().length > 0 ? preferred : "Guest";
}

export function extractRolesUpper(payload: JwtPayload | null): string[] {
  if (!payload) return [];
  const roles: string[] = [];
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
  if (realmAccess?.roles && Array.isArray(realmAccess.roles)) {
    for (const role of realmAccess.roles) {
      roles.push(String(role).toUpperCase());
    }
  }
  return Array.from(new Set(roles));
}

export function extractTenantIdClaim(payload: JwtPayload | null): string | null {
  if (!payload) return null;
  const tenantId = payload.tenant_id || payload.tenantId || payload["https://deepthoughtnet.com/tenant_id"];
  return typeof tenantId === "string" && tenantId.trim().length > 0 ? tenantId.trim() : null;
}
