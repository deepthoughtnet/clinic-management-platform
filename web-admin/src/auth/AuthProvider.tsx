import * as React from "react";
import { keycloak } from "./keycloak";
import { initKeycloakOnce, resetKeycloakInit } from "./keycloakInit";
import { decodeJwtPayload, extractRolesUpper, extractTenantIdClaim, extractUsername } from "./tokenUtils";
import { AuthContext, type AuthContextValue, type SelectedTenant } from "./AuthContext";

type MeResponse = {
  email?: string | null;
  username?: string | null;
  platformAdmin?: boolean | null;
  tenantId?: string | null;
  appUserId?: string | null;
  subject?: string | null;
  tenantRole?: string | null;
  permissions?: string[] | null;
  tokenRoles?: string[] | null;
  modules?: {
    carePilot?: boolean | null;
    aiCopilot?: boolean | null;
  } | null;
  memberships?: Array<{
    tenantId?: string | null;
    id?: string | null;
    tenantCode?: string | null;
    code?: string | null;
    tenantName?: string | null;
    name?: string | null;
    role?: string | null;
    status?: string | null;
    active?: boolean | null;
    modules?: {
      carePilot?: boolean | null;
      aiCopilot?: boolean | null;
    } | null;
  }> | null;
  activeTenantMemberships?: Array<{
    tenantId?: string | null;
    id?: string | null;
    tenantCode?: string | null;
    code?: string | null;
    tenantName?: string | null;
    name?: string | null;
    role?: string | null;
    status?: string | null;
    active?: boolean | null;
    modules?: {
      carePilot?: boolean | null;
      aiCopilot?: boolean | null;
    } | null;
  }> | null;
};
type ActiveMembership = {
  tenantId: string;
  tenantCode?: string | null;
  tenantName?: string | null;
  role?: string | null;
  status?: string | null;
  modules?: {
    carePilot?: boolean | null;
    aiCopilot?: boolean | null;
  } | null;
};

const SELECTED_TENANT_STORAGE_KEY = "clinic_selected_tenant";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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

function parseStoredSelectedTenant(): SelectedTenant | null {
  try {
    const raw = localStorage.getItem(SELECTED_TENANT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<SelectedTenant>;
    if (
      typeof parsed.id !== "string" ||
      typeof parsed.code !== "string" ||
      typeof parsed.name !== "string" ||
      !isValidClinicTenantShape(parsed)
    ) {
      localStorage.removeItem(SELECTED_TENANT_STORAGE_KEY);
      return null;
    }
    return {
      id: parsed.id,
      code: parsed.code,
      name: parsed.name,
    };
  } catch {
    return null;
  }
}

function storeSelectedTenant(tenant: SelectedTenant | null): void {
  if (!tenant || !isValidClinicTenantShape(tenant)) {
    localStorage.removeItem(SELECTED_TENANT_STORAGE_KEY);
    console.info("[auth] selected tenant cleared", { storageKey: SELECTED_TENANT_STORAGE_KEY });
    return;
  }
  localStorage.setItem(SELECTED_TENANT_STORAGE_KEY, JSON.stringify(tenant));
  console.info("[auth] selected tenant stored", {
    storageKey: SELECTED_TENANT_STORAGE_KEY,
    tenant,
    storedValue: localStorage.getItem(SELECTED_TENANT_STORAGE_KEY),
  });
}

function isSystemTenantValue(value?: string | null): boolean {
  const normalized = (value || "").trim().toUpperCase();
  return normalized.startsWith("DEFAULT-ROLES") || normalized.includes("DEFAULT-ROLES-");
}

function isValidClinicTenantShape(tenant: Partial<SelectedTenant> | Membership): boolean {
  const id = "tenantId" in tenant ? tenant.tenantId : tenant.id;
  const code = "tenantCode" in tenant ? tenant.tenantCode : tenant.code;
  const name = "tenantName" in tenant ? tenant.tenantName : tenant.name;
  return Boolean(id && UUID_RE.test(id) && !isSystemTenantValue(code) && !isSystemTenantValue(name) && !isSystemTenantValue(id));
}

type Membership = {
  tenantId: string;
  tenantCode: string | null;
  tenantName: string | null;
  role?: string | null;
  status?: string | null;
  active?: boolean | null;
  modules?: {
    carePilot?: boolean | null;
    aiCopilot?: boolean | null;
  } | null;
};

function normalizeMemberships(me: MeResponse): Membership[] {
  const source = ((me.activeTenantMemberships && me.activeTenantMemberships.length > 0)
    ? me.activeTenantMemberships
    : me.memberships || []) as Array<Record<string, unknown>>;
  const normalized: Membership[] = [];
  for (const membership of source) {
    const tenantId = typeof membership.tenantId === "string"
      ? membership.tenantId
      : typeof membership.id === "string"
        ? membership.id
        : null;
    if (!tenantId) continue;
    const tenantCode = typeof membership.tenantCode === "string"
      ? membership.tenantCode
      : typeof membership.code === "string"
        ? membership.code
        : null;
    const tenantName = typeof membership.tenantName === "string"
      ? membership.tenantName
      : typeof membership.name === "string"
        ? membership.name
        : null;
    const normalizedMembership = {
      tenantId,
      tenantCode,
      tenantName,
      role: typeof membership.role === "string" ? membership.role : null,
      status: typeof membership.status === "string" ? membership.status : null,
      active: membership.active === true,
      modules: typeof membership.modules === "object" && membership.modules !== null
        ? {
          carePilot: (membership.modules as { carePilot?: boolean | null }).carePilot ?? null,
          aiCopilot: (membership.modules as { aiCopilot?: boolean | null }).aiCopilot ?? null,
        }
        : null,
    };
    if (!isValidClinicTenantShape(normalizedMembership)) continue;
    normalized.push(normalizedMembership);
  }
  return normalized;
}

export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [initialized, setInitialized] = React.useState(false);
  const [authenticated, setAuthenticated] = React.useState(false);
  const [accessToken, setAccessToken] = React.useState<string | null>(null);
  const [username, setUsername] = React.useState("Guest");
  const [rolesUpper, setRolesUpper] = React.useState<string[]>([]);
  const [permissions, setPermissions] = React.useState<string[]>([]);
  const [selectedTenant, setSelectedTenant] = React.useState<SelectedTenant | null>(null);
  const [appUserId, setAppUserId] = React.useState<string | null>(null);
  const [tenantRole, setTenantRole] = React.useState<string | null>(null);
  const [activeTenantMemberships, setActiveTenantMemberships] = React.useState<ActiveMembership[]>([]);
  const [tenantModules, setTenantModules] = React.useState<{ carePilot?: boolean | null; aiCopilot?: boolean | null } | null>(null);
  const [initError, setInitError] = React.useState<string | null>(null);
  const [initVersion, setInitVersion] = React.useState(0);

  const hydrateFromToken = React.useCallback((token: string | null) => {
    const payload = token ? decodeJwtPayload(token) : null;
    const tokenRoles = payload ? extractRolesUpper(payload) : [];
    const tokenUser = payload ? extractUsername(payload) : "Guest";
    const tokenTenant = payload ? extractTenantIdClaim(payload) : null;

    setAccessToken(token);
    setRolesUpper(tokenRoles);
    setUsername(tokenUser);
    setSelectedTenant((current) => {
      if (current) return current;
      if (!tokenTenant) return null;
      const tenant = { id: tokenTenant, code: tokenTenant, name: tokenTenant };
      return isValidClinicTenantShape(tenant) ? tenant : null;
    });
  }, []);

  const clearSession = React.useCallback(() => {
    storeSelectedTenant(null);
    setAuthenticated(false);
    setAccessToken(null);
    setUsername("Guest");
    setRolesUpper([]);
    setPermissions([]);
    setSelectedTenant(null);
    setAppUserId(null);
    setTenantRole(null);
    setActiveTenantMemberships([]);
    setTenantModules(null);
  }, []);

  const refreshTenantContext = React.useCallback(async (tenant: SelectedTenant | null, tokenOverride?: string | null) => {
    const token = tokenOverride ?? accessToken;
    if (!token) {
      console.warn("[auth] tenant context refresh skipped because no access token is available", { tenant });
      return;
    }

    console.info("[auth] tenant context refresh started", {
      selectedTenant: tenant,
      activeMode: tenant ? "clinic" : "platform",
    });

    try {
      console.info("[auth] /me request started", { tenantId: tenant?.id || null });
      const me = await fetchMe(token, tenant?.id || undefined);
      console.info("[auth] /me request completed", {
        tenantId: me.tenantId || null,
        tenantRole: me.tenantRole || null,
        memberships: (me.activeTenantMemberships || me.memberships || []).length,
      });

      setActiveTenantMemberships(normalizeMemberships(me));
      setSelectedTenant(tenant);
      setAppUserId(me.appUserId || null);
      setTenantRole(me.tenantRole || null);
      setTenantModules(me.modules || null);
      setPermissions((me.permissions || []).map((permission) => permission.toLowerCase()));
      setInitError(null);
      console.info("[auth] tenant context refresh completed", {
        activeMode: tenant ? "clinic" : "platform",
        tenantId: tenant?.id || null,
        tenantRole: me.tenantRole || null,
      });
    } catch (err) {
      console.warn("[auth] tenant context refresh failed", err);
      setInitError(err instanceof Error ? err.message : "Failed to switch tenant context");
    }
  }, [accessToken]);

  React.useEffect(() => {
    let cancelled = false;
    let refreshInterval: number | null = null;

    async function bootstrap() {
      console.info("[auth] bootstrap started");
      setInitialized(false);
      setInitError(null);

      const bootstrapTimeout = window.setTimeout(() => {
        if (!cancelled) {
          console.warn("[auth] bootstrap exceeded timeout");
          setInitError("Authentication initialization is taking too long. Verify Keycloak URL/realm/client and browser network logs.");
        }
      }, 10000);

      try {
        console.info("[auth] keycloak init started");
        const ok = await initKeycloakOnce();
        if (cancelled) return;
        console.info("[auth] keycloak init completed", { authenticated: ok });

        setAuthenticated(ok);
        const token = keycloak.token || null;
        console.info("[auth] token acquired", { hasToken: Boolean(token) });
        hydrateFromToken(token);

        if (ok && token) {
          console.info("[auth] tenant bootstrap started");
          const payload = decodeJwtPayload(token);
          const tokenRolesUpper = extractRolesUpper(payload);
          const tokenTenantId = extractTenantIdClaim(payload);
          const storedTenant = parseStoredSelectedTenant();
          const tokenTenant = tokenTenantId ? { id: tokenTenantId, code: tokenTenantId, name: tokenTenantId } : null;
          const initialTenantId = storedTenant?.id || (tokenTenant && isValidClinicTenantShape(tokenTenant) ? tokenTenant.id : null);

          try {
            const meAbort = new AbortController();
            const meTimeout = window.setTimeout(() => meAbort.abort(), 6000);
            console.info("[auth] /me request started", { tenantId: initialTenantId || null });
            try {
              const me = await fetchMe(token, initialTenantId || undefined, meAbort.signal);
              if (!cancelled) {
                console.info("[auth] /me request completed", { memberships: (me.activeTenantMemberships || me.memberships || []).length });
                const memberships = normalizeMemberships(me);
                setActiveTenantMemberships(memberships);
                const isPlatformAdmin = Boolean(me.platformAdmin)
                  || tokenRolesUpper.includes("PLATFORM_ADMIN")
                  || (me.tokenRoles || []).some((role) => String(role).toUpperCase() === "PLATFORM_ADMIN");
                const activeMemberships = memberships.filter((membership) => {
                  if (membership.active === true) return true;
                  return (membership.status || "").toUpperCase() === "ACTIVE";
                });

                let resolved: SelectedTenant | null = null;
                if (isPlatformAdmin && storedTenant && me.tenantId === storedTenant.id) {
                  resolved = storedTenant;
                }

                if (!resolved && storedTenant && activeMemberships.some((membership) => membership.tenantId === storedTenant.id)) {
                  const match = activeMemberships.find((membership) => membership.tenantId === storedTenant.id);
                  if (match) {
                    resolved = {
                      id: match.tenantId,
                      code: match.tenantCode || storedTenant.code || match.tenantId,
                      name: match.tenantName || storedTenant.name || match.tenantCode || match.tenantId,
                    };
                  }
                }

                if (!resolved) {
                  const meTenant = (me.tenantId && activeMemberships.find((membership) => membership.tenantId === me.tenantId)) || null;
                  if (meTenant) {
                    resolved = {
                      id: meTenant.tenantId,
                      code: meTenant.tenantCode || meTenant.tenantId,
                      name: meTenant.tenantName || meTenant.tenantCode || meTenant.tenantId,
                    };
                  }
                }

                if (!resolved && !isPlatformAdmin && activeMemberships.length === 1) {
                  const only = activeMemberships[0];
                  resolved = {
                    id: only.tenantId,
                    code: only.tenantCode || only.tenantId,
                    name: only.tenantName || only.tenantCode || only.tenantId,
                  };
                }

                if (!resolved && !isPlatformAdmin && tokenTenantId && activeMemberships.some((membership) => membership.tenantId === tokenTenantId)) {
                  const match = activeMemberships.find((membership) => membership.tenantId === tokenTenantId);
                  if (match) {
                    resolved = {
                      id: match.tenantId,
                      code: match.tenantCode || match.tenantId,
                      name: match.tenantName || match.tenantCode || match.tenantId,
                    };
                  }
                }

                if (!isPlatformAdmin && activeMemberships.length === 0) {
                  setInitError("No active clinic membership found. Contact clinic administrator.");
                  resolved = null;
                } else if (!isPlatformAdmin && activeMemberships.length > 1 && !resolved) {
                  resolved = null;
                }

                let effectiveMe = me;
                if (resolved?.id && (me.tenantId !== resolved.id || !me.appUserId || !me.tenantRole)) {
                  try {
                    effectiveMe = await fetchMe(token, resolved.id);
                  } catch {
                    effectiveMe = me;
                  }
                }

                setSelectedTenant(resolved);
                setAppUserId(effectiveMe.appUserId || null);
                setTenantRole(effectiveMe.tenantRole || null);
                setTenantModules(effectiveMe.modules || null);
                setPermissions((effectiveMe.permissions || []).map((permission) => permission.toLowerCase()));
                storeSelectedTenant(resolved);
                console.info("[auth] tenant bootstrap completed", {
                  activeMode: resolved ? "clinic" : "platform",
                  selectedTenantId: resolved?.id || null,
                  tenantRole: effectiveMe.tenantRole || null,
                  permissions: (effectiveMe.permissions || []).length,
                });
              }
            } finally {
              window.clearTimeout(meTimeout);
            }
          } catch (err) {
            if (!cancelled) {
              console.warn("[auth] /me request failed", err);
              setInitError(err instanceof Error ? err.message : "Failed to load /api/me");
            }
          }

          refreshInterval = window.setInterval(async () => {
            try {
              if (!keycloak.authenticated) return;
              await keycloak.updateToken(30);
              const newToken = keycloak.token || null;
              hydrateFromToken(newToken);
              setAuthenticated(!!newToken);
            } catch {
              if (!cancelled) {
                clearSession();
              }
            }
          }, 10_000);
        }
      } catch (err) {
        if (!cancelled) {
          clearSession();
          console.warn("[auth] bootstrap failed", err);
          setInitError(err instanceof Error ? err.message : "Keycloak init failed");
        }
      } finally {
        window.clearTimeout(bootstrapTimeout);
        if (!cancelled) {
          console.info("[auth] auth loading cleared");
          setInitialized(true);
        }
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
      if (refreshInterval) {
        window.clearInterval(refreshInterval);
      }
    };
  }, [clearSession, hydrateFromToken, initVersion]);

  const value = React.useMemo<AuthContextValue>(
    () => ({
      initialized,
      authenticated,
      username,
      rolesUpper,
      permissions,
      selectedTenant,
      tenantId: selectedTenant?.id || null,
      tenantName: selectedTenant?.name || null,
      appUserId,
      tenantRole,
      activeTenantMemberships,
      tenantModules,
      accessToken,
      initError,
      selectTenant: (tenant) => {
        console.info("[auth] selectTenant invoked", {
          tenant,
          activeMode: tenant ? "clinic" : "platform",
        });
        setSelectedTenant(tenant);
        storeSelectedTenant(tenant);
        void refreshTenantContext(tenant);
      },
      retryInit: () => {
        resetKeycloakInit();
        setInitVersion((v) => v + 1);
      },
      clearSession,
      hasPermission: (permission: string) => permissions.includes(permission.trim().toLowerCase()),
      login: async () => {
        await keycloak.login({ prompt: "login" });
      },
      logout: async () => {
        clearSession();
        await keycloak.logout({ redirectUri: `${window.location.origin}/login` });
      },
    }),
    [initialized, authenticated, username, rolesUpper, permissions, selectedTenant, activeTenantMemberships, tenantModules, accessToken, initError, appUserId, tenantRole, clearSession, refreshTenantContext]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
