import * as React from "react";
import { keycloak } from "./keycloak";
import { initKeycloakOnce } from "./keycloakInit";
import { decodeJwtPayload, extractRolesUpper, extractTenantIdClaim, extractUsername } from "./tokenUtils";
import { AuthContext, type AuthContextValue } from "./AuthContext";

type MeResponse = {
  tenantId?: string | null;
  subject?: string | null;
  tenantRole?: string | null;
  tokenRoles?: string[] | null;
  activeTenantMemberships?: Array<{
    tenantId: string;
    tenantCode?: string | null;
    tenantName?: string | null;
  }> | null;
};

function baseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
}

async function fetchMe(token: string, tenantId?: string | null, signal?: AbortSignal): Promise<MeResponse> {
  const response = await fetch(`${baseUrl()}/api/me`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(tenantId ? { "X-Tenant-Id": tenantId } : {}),
      Accept: "application/json",
    },
    signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  return (await response.json()) as MeResponse;
}

export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [initialized, setInitialized] = React.useState(false);
  const [authenticated, setAuthenticated] = React.useState(false);
  const [accessToken, setAccessToken] = React.useState<string | null>(null);
  const [username, setUsername] = React.useState("Guest");
  const [rolesUpper, setRolesUpper] = React.useState<string[]>([]);
  const [tenantId, setTenantId] = React.useState<string | null>(null);
  const [tenantName, setTenantName] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;

    initKeycloakOnce()
      .then(async (authenticated) => {
        if (cancelled) return;
        setAuthenticated(authenticated);

        if (!authenticated) {
          setInitialized(true);
          return;
        }

        const token = keycloak.token || null;
        setAccessToken(token);
        const payload = token ? decodeJwtPayload(token) : null;
        setUsername(extractUsername(payload));
        setRolesUpper(extractRolesUpper(payload));
        setTenantId(extractTenantIdClaim(payload));

        if (token) {
          try {
            const me = await fetchMe(token, extractTenantIdClaim(payload) || undefined);
            if (!cancelled) {
              setTenantId(me.tenantId || extractTenantIdClaim(payload));
              setTenantName(
                me.activeTenantMemberships?.find((membership) => membership.tenantId === me.tenantId)?.tenantName || null
              );
            }
          } catch {
            // Keep the JWT-derived session usable even if /api/me is temporarily unavailable.
          }
        }

        setInitialized(true);
      })
      .catch(() => {
        if (!cancelled) {
          setInitialized(true);
          setAuthenticated(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const value = React.useMemo<AuthContextValue>(
    () => ({
      initialized,
      authenticated,
      username,
      rolesUpper,
      tenantId,
      tenantName,
      accessToken,
      login: async () => {
        await keycloak.login({ prompt: "login" });
      },
      logout: async () => {
        await keycloak.logout({ redirectUri: window.location.origin });
      },
    }),
    [initialized, authenticated, username, rolesUpper, tenantId, tenantName, accessToken]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
