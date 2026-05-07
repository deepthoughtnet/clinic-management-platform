export type ApiOpts = {
  token?: string;
  tenantId?: string | null;
  apiBase?: string;
  signal?: AbortSignal;
  requireTenant?: boolean;
  platformOperation?: boolean;
};

const SELECTED_TENANT_STORAGE_KEY = "clinic_selected_tenant";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function baseUrl(apiBase?: string): string {
  const envBase = import.meta.env.VITE_API_BASE_URL || "";
  return (apiBase || envBase || "").replace(/\/+$/, "");
}

function getStoredToken(): string {
  try {
    return localStorage.getItem("access_token") || "";
  } catch {
    return "";
  }
}

function getStoredTenantId(): string | null {
  try {
    const raw = localStorage.getItem(SELECTED_TENANT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { id?: unknown; code?: unknown; name?: unknown };
    const id = typeof parsed.id === "string" ? parsed.id.trim() : "";
    const code = typeof parsed.code === "string" ? parsed.code.trim() : "";
    const name = typeof parsed.name === "string" ? parsed.name.trim() : "";
    if (!id || !UUID_RE.test(id) || isSystemTenantValue(id) || isSystemTenantValue(code) || isSystemTenantValue(name)) {
      localStorage.removeItem(SELECTED_TENANT_STORAGE_KEY);
      return null;
    }
    return id;
  } catch {
    return null;
  }
}

function isSystemTenantValue(value: string): boolean {
  const normalized = value.toUpperCase();
  return normalized.startsWith("DEFAULT-ROLES") || normalized.includes("DEFAULT-ROLES-");
}

function resolveTenantId(opts?: ApiOpts): string | null {
  return opts?.tenantId || getStoredTenantId();
}

function requireTenantId(tenantId: string | null): string {
  if (!tenantId) {
    throw new Error("Missing tenant id. Select a clinic tenant first.");
  }
  return tenantId;
}

function isPlatformPath(path: string): boolean {
  return path === "/api/platform" || path.startsWith("/api/platform/");
}

function isPlatformOperation(path: string, opts?: ApiOpts): boolean {
  return opts?.platformOperation === true || isPlatformPath(path);
}

function buildHeaders(path: string, opts?: ApiOpts, withContentType = true): HeadersInit {
  const token = opts?.token ?? getStoredToken();
  const tenantId = resolveTenantId(opts);
  const platformOp = isPlatformOperation(path, opts);

  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(platformOp ? { "X-Platform-Op": "true" } : {}),
    ...(platformOp || opts?.requireTenant === false ? {} : { "X-Tenant-Id": requireTenantId(tenantId) }),
    ...(withContentType ? { "Content-Type": "application/json" } : {}),
    Accept: "application/json",
  };
}

function buildMultipartHeaders(path: string, opts?: ApiOpts): HeadersInit {
  const headers = buildHeaders(path, opts, false) as Record<string, string>;
  delete headers.Accept;
  return headers;
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`HTTP ${res.status}: ${body || res.statusText}`);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function httpGet<T>(path: string, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "GET",
    headers: buildHeaders(path, opts, false),
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}

export async function httpPut<T>(path: string, body: unknown, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "PUT",
    headers: buildHeaders(path, opts, true),
    body: JSON.stringify(body),
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}

export async function httpPatch<T>(path: string, body?: unknown, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "PATCH",
    headers: buildHeaders(path, opts, body !== undefined),
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}

export async function httpPost<T>(path: string, body: unknown, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "POST",
    headers: buildHeaders(path, opts, true),
    body: JSON.stringify(body),
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}

export async function httpPostForm<T>(path: string, formData: FormData, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "POST",
    headers: buildMultipartHeaders(path, opts),
    body: formData,
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}
