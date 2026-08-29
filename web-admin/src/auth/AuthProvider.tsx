import * as React from "react";
import { assertValidKeycloakClient, keycloak } from "./keycloakClient";
import { initKeycloakOnce, resetKeycloakInit } from "./keycloakInit";
import { decodeJwtPayload, extractRolesUpper, extractTenantIdClaim, extractUsername } from "./tokenUtils";
import { AuthContext, type AuthContextValue, type SelectedTenant } from "./AuthContext";
import {
  computeInactivityWindow,
  createSharedSessionEvent,
  INACTIVITY_EXPIRED_MESSAGE,
  INACTIVITY_WARNING_MESSAGE,
  parseSharedActivityTimestamp,
  parseSharedSessionEvent,
  resolveInactivityTimeoutMs,
  SESSION_ACTIVITY_STORAGE_KEY,
  SESSION_EVENT_STORAGE_KEY,
  SESSION_EXPIRED_MESSAGE,
} from "./sessionInactivity";

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
  enabledModules?: Record<string, boolean> | null;
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
    enabledModules?: Record<string, boolean> | null;
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
    enabledModules?: Record<string, boolean> | null;
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
  enabledModules?: Record<string, boolean> | null;
};

const SELECTED_TENANT_STORAGE_KEY = "clinic_selected_tenant";
const SESSION_NOTICE_STORAGE_KEY = "clinic_auth_session_notice";
const INACTIVITY_ACTIVITY_THROTTLE_MS = 1000;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function baseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
}

function loginRedirectUri(): string {
  if (typeof window === "undefined") {
    return "/login";
  }
  return new URL("/login", window.location.origin).toString();
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

function readStoredSessionNotice(): string | null {
  try {
    const notice = sessionStorage.getItem(SESSION_NOTICE_STORAGE_KEY);
    return notice && notice.trim() ? notice.trim() : null;
  } catch {
    return null;
  }
}

function storeSessionNotice(notice: string | null): void {
  try {
    if (!notice || !notice.trim()) {
      sessionStorage.removeItem(SESSION_NOTICE_STORAGE_KEY);
      return;
    }
    sessionStorage.setItem(SESSION_NOTICE_STORAGE_KEY, notice.trim());
  } catch {
    // Ignore storage failures; auth state must still update.
  }
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
  enabledModules?: Record<string, boolean> | null;
};

function normalizeEnabledModules(modules: Record<string, boolean> | null | undefined) {
  if (!modules || typeof modules !== "object") return null;
  const next = Object.entries(modules).reduce<Record<string, boolean>>((acc, [key, value]) => {
    acc[key.trim().toUpperCase()] = value === true;
    return acc;
  }, {});
  return Object.keys(next).length ? next : null;
}

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
      enabledModules: normalizeEnabledModules((membership.enabledModules as Record<string, boolean> | null | undefined) ?? null),
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
  const [enabledTenantModules, setEnabledTenantModules] = React.useState<Record<string, boolean> | null>(null);
  const [initError, setInitError] = React.useState<string | null>(null);
  const [sessionWarning, setSessionWarning] = React.useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = React.useState<string | null>(() => readStoredSessionNotice());
  const [initVersion, setInitVersion] = React.useState(0);
  const inactivityTimeoutMs = React.useMemo(() => resolveInactivityTimeoutMs(import.meta.env.VITE_WEB_ADMIN_INACTIVITY_TIMEOUT_MINUTES), []);
  const inactivityWarningLeadMs = React.useMemo(() => Math.min(2 * 60_000, Math.max(0, inactivityTimeoutMs - 60_000)), [inactivityTimeoutMs]);
  const lastActivityAtRef = React.useRef<number>(Date.now());
  const lastPersistedActivityAtRef = React.useRef<number>(0);
  const inactivityWarningTimerRef = React.useRef<number | null>(null);
  const inactivityLogoutTimerRef = React.useRef<number | null>(null);
  const sessionInvalidatedRef = React.useRef(false);

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

  const clearInactivityTimers = React.useCallback(() => {
    if (inactivityWarningTimerRef.current !== null) {
      window.clearTimeout(inactivityWarningTimerRef.current);
      inactivityWarningTimerRef.current = null;
    }
    if (inactivityLogoutTimerRef.current !== null) {
      window.clearTimeout(inactivityLogoutTimerRef.current);
      inactivityLogoutTimerRef.current = null;
    }
  }, []);

  const resetAuthState = React.useCallback((notice: string | null, persistNotice: boolean) => {
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
    setEnabledTenantModules(null);
    setSessionWarning(null);
    if (persistNotice && notice) {
      setSessionNotice(notice);
      storeSessionNotice(notice);
    } else {
      setSessionNotice(null);
      storeSessionNotice(null);
    }
  }, []);

  const persistSharedActivity = React.useCallback((timestamp: number) => {
    if (!Number.isFinite(timestamp) || timestamp <= 0) {
      return;
    }
    if (timestamp - lastPersistedActivityAtRef.current < INACTIVITY_ACTIVITY_THROTTLE_MS) {
      return;
    }
    try {
      localStorage.setItem(SESSION_ACTIVITY_STORAGE_KEY, String(timestamp));
      lastPersistedActivityAtRef.current = timestamp;
    } catch {
      // Ignore storage failures; the local timer still runs.
    }
  }, []);

  const broadcastLogout = React.useCallback((reason: string) => {
    try {
      localStorage.setItem(SESSION_EVENT_STORAGE_KEY, createSharedSessionEvent(reason));
      window.setTimeout(() => {
        try {
          localStorage.removeItem(SESSION_EVENT_STORAGE_KEY);
        } catch {
          // Ignore storage cleanup failures.
        }
      }, 0);
    } catch {
      // Ignore storage failures; the local tab still logs out.
    }
  }, []);

  const terminateKeycloakSession = React.useCallback(async (reason: "explicit" | "inactivity") => {
    try {
      assertValidKeycloakClient(keycloak);
      console.info("[auth] ending keycloak session", {
        reason,
        redirectUri: loginRedirectUri(),
      });
      await keycloak.logout({ redirectUri: loginRedirectUri() });
    } catch (err) {
      console.warn("[auth] keycloak logout failed", {
        reason,
        error: err instanceof Error ? err.message : err ? String(err) : null,
      });
      if (typeof window !== "undefined") {
        window.location.assign(loginRedirectUri());
      }
    }
  }, []);

  const handleInactivityLogout = React.useCallback((source: "timer" | "storage") => {
    if (sessionInvalidatedRef.current) {
      return;
    }
    sessionInvalidatedRef.current = true;
    clearInactivityTimers();
    console.warn("[auth] session expired due to inactivity", { source });
    resetAuthState(INACTIVITY_EXPIRED_MESSAGE, true);
    if (source === "timer") {
      broadcastLogout("inactivity");
      void terminateKeycloakSession("inactivity");
    }
  }, [broadcastLogout, clearInactivityTimers, resetAuthState, terminateKeycloakSession]);

  const handleSharedLogout = React.useCallback((reason: string) => {
    if (sessionInvalidatedRef.current) {
      return;
    }
    sessionInvalidatedRef.current = true;
    clearInactivityTimers();
    console.warn("[auth] shared logout received", { reason });
    const notice = reason === "inactivity" ? INACTIVITY_EXPIRED_MESSAGE : reason === "explicit" ? null : SESSION_EXPIRED_MESSAGE;
    resetAuthState(notice, Boolean(notice));
  }, [clearInactivityTimers, resetAuthState]);

  const syncInactivityFromTimestamp = React.useCallback((timestamp: number, persist = false, force = false) => {
    if (!authenticated || sessionInvalidatedRef.current) {
      return;
    }
    if (!Number.isFinite(timestamp) || timestamp <= 0) {
      return;
    }
    if (!force && timestamp <= lastActivityAtRef.current) {
      return;
    }
    lastActivityAtRef.current = timestamp;
    setSessionWarning(null);
    if (persist) {
      persistSharedActivity(timestamp);
    }
    clearInactivityTimers();
    const inactivityWindow = computeInactivityWindow(timestamp, Date.now(), inactivityTimeoutMs, inactivityWarningLeadMs);
    if (inactivityWindow.shouldExpire) {
      void handleInactivityLogout("timer");
      return;
    }
    if (inactivityWindow.shouldWarn) {
      setSessionWarning(INACTIVITY_WARNING_MESSAGE);
    } else {
      inactivityWarningTimerRef.current = window.setTimeout(() => {
        if (!sessionInvalidatedRef.current && authenticated) {
          setSessionWarning(INACTIVITY_WARNING_MESSAGE);
        }
      }, inactivityWindow.warningInMs);
    }
    inactivityLogoutTimerRef.current = window.setTimeout(() => {
      if (!sessionInvalidatedRef.current && authenticated) {
        void handleInactivityLogout("timer");
      }
    }, inactivityWindow.expiresInMs);
  }, [authenticated, clearInactivityTimers, handleInactivityLogout, inactivityTimeoutMs, inactivityWarningLeadMs, persistSharedActivity]);

  const clearSession = React.useCallback(() => {
    clearInactivityTimers();
    resetAuthState(null, false);
  }, [clearInactivityTimers, resetAuthState]);

  const markSessionExpired = React.useCallback((reason: string, error?: unknown) => {
    console.warn("[auth] session expired", {
      reason,
      error: error instanceof Error ? error.message : error ? String(error) : null,
    });
    if (sessionInvalidatedRef.current) {
      return;
    }
    sessionInvalidatedRef.current = true;
    clearInactivityTimers();
    resetAuthState(SESSION_EXPIRED_MESSAGE, true);
    broadcastLogout("expired");
  }, [broadcastLogout, clearInactivityTimers, resetAuthState]);

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
      setEnabledTenantModules(normalizeEnabledModules(me.enabledModules));
      setPermissions((me.permissions || []).map((permission) => permission.toLowerCase()));
      setInitError(null);
      setSessionNotice(null);
      storeSessionNotice(null);
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
    let handleStorageEvent: ((event: StorageEvent) => void) | null = null;
    let handleActivityEvent: (() => void) | null = null;
    let activityEvents: Array<keyof WindowEventMap> = [];

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
        assertValidKeycloakClient(keycloak);
        const ok = await initKeycloakOnce();
        if (cancelled) return;
        console.info("[auth] keycloak init completed", { authenticated: ok });

        setAuthenticated(ok);
        const token = keycloak.token || null;
        console.info("[auth] token acquired", { hasToken: Boolean(token) });
        hydrateFromToken(token);

        if (ok && token) {
          setSessionNotice(null);
          storeSessionNotice(null);
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
                setEnabledTenantModules(normalizeEnabledModules(effectiveMe.enabledModules));
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

          const startInactivityWatch = () => {
            const startedAt = Date.now();
            sessionInvalidatedRef.current = false;
            lastActivityAtRef.current = startedAt;
            clearInactivityTimers();
            persistSharedActivity(startedAt);
            syncInactivityFromTimestamp(startedAt, false, true);
          };

          handleActivityEvent = () => {
            if (!authenticated || sessionInvalidatedRef.current) {
              return;
            }
            syncInactivityFromTimestamp(Date.now(), true);
          };

          handleStorageEvent = (event: StorageEvent) => {
            if (event.key === SESSION_ACTIVITY_STORAGE_KEY) {
              const timestamp = parseSharedActivityTimestamp(event.newValue);
              if (timestamp != null) {
                syncInactivityFromTimestamp(timestamp, false);
              }
              return;
            }
            if (event.key === SESSION_EVENT_STORAGE_KEY) {
              const sharedEvent = parseSharedSessionEvent(event.newValue);
              if (sharedEvent) {
                handleSharedLogout(sharedEvent.reason);
              }
            }
          };

          activityEvents = ["pointerdown", "mousedown", "keydown", "click", "touchstart", "wheel", "scroll"];

          startInactivityWatch();
          window.addEventListener("storage", handleStorageEvent!);
          activityEvents.forEach((eventName) => window.addEventListener(eventName, handleActivityEvent!, { capture: true, passive: true }));

          refreshInterval = window.setInterval(async () => {
            try {
              if (!keycloak.authenticated || sessionInvalidatedRef.current) return;
              await keycloak.updateToken(30);
              const newToken = keycloak.token || null;
              if (sessionInvalidatedRef.current) return;
              hydrateFromToken(newToken);
              setAuthenticated(!!newToken);
            } catch {
              if (!cancelled) {
                markSessionExpired("refresh-token-update-failed");
              }
            }
          }, 10_000);

          keycloak.onTokenExpired = () => {
            console.info("[auth] access token expired; attempting refresh");
            void keycloak.updateToken(30)
              .then(() => {
                if (sessionInvalidatedRef.current) {
                  return;
                }
                const newToken = keycloak.token || null;
                hydrateFromToken(newToken);
                setAuthenticated(!!newToken);
              })
              .catch((err) => {
                if (!cancelled) {
                  markSessionExpired("access-token-expired-refresh-failed", err);
                }
              });
          };
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
      if (handleStorageEvent) {
        window.removeEventListener("storage", handleStorageEvent);
      }
      if (handleActivityEvent) {
        activityEvents.forEach((eventName) => window.removeEventListener(eventName, handleActivityEvent!, true));
      }
      clearInactivityTimers();
      keycloak.onTokenExpired = undefined;
    };
  }, [clearInactivityTimers, clearSession, handleSharedLogout, hydrateFromToken, initVersion, markSessionExpired, persistSharedActivity, syncInactivityFromTimestamp]);

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
      enabledTenantModules,
      accessToken,
      initError,
      sessionWarning,
      sessionNotice,
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
        sessionInvalidatedRef.current = false;
        clearInactivityTimers();
        setInitVersion((v) => v + 1);
        setSessionWarning(null);
        setSessionNotice(null);
        storeSessionNotice(null);
      },
      clearSession,
      hasPermission: (permission: string) => permissions.includes(permission.trim().toLowerCase()),
      login: async () => {
        assertValidKeycloakClient(keycloak);
        await keycloak.login({ prompt: "login" });
      },
      logout: async () => {
        console.info("[auth] user initiated logout");
        sessionInvalidatedRef.current = true;
        clearInactivityTimers();
        clearSession();
        broadcastLogout("explicit");
        await terminateKeycloakSession("explicit");
      },
    }),
    [initialized, authenticated, username, rolesUpper, permissions, selectedTenant, activeTenantMemberships, tenantModules, enabledTenantModules, accessToken, initError, sessionWarning, sessionNotice, appUserId, tenantRole, clearSession, refreshTenantContext, clearInactivityTimers, broadcastLogout, terminateKeycloakSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
