export type ApiOpts = {
  token?: string;
  tenantId?: string | null;
  apiBase?: string;
  signal?: AbortSignal;
  requireTenant?: boolean;
  platformOperation?: boolean;
  accept?: string;
  clientTimeZone?: string | null;
};

export type ApiErrorResponse = {
  timestamp?: string;
  path?: string;
  status?: number;
  code?: string;
  message?: string;
  correlationId?: string | null;
  requestId?: string | null;
};

export class ApiClientError extends Error {
  status: number;
  code: string | null;
  path: string | null;
  correlationId: string | null;
  requestId: string | null;

  constructor(message: string, details: { status: number; code?: string | null; path?: string | null; correlationId?: string | null; requestId?: string | null }) {
    super(message);
    this.name = "ApiClientError";
    this.status = details.status;
    this.code = details.code ?? null;
    this.path = details.path ?? null;
    this.correlationId = details.correlationId ?? null;
    this.requestId = details.requestId ?? null;
  }
}

const SELECTED_TENANT_STORAGE_KEY = "clinic_selected_tenant";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const UUID_IN_TEXT_RE = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;

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
  const sendTenantHeader = !(platformOp || opts?.requireTenant === false);
  const headerTenantId = sendTenantHeader ? requireTenantId(tenantId) : null;

  console.info("[api] request context", {
    path,
    platformOperation: platformOp,
    requireTenant: opts?.requireTenant !== false,
    selectedTenantId: tenantId,
    xTenantId: headerTenantId,
    hasAuthorization: Boolean(token),
  });

  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(platformOp ? { "X-Platform-Op": "true" } : {}),
    ...(headerTenantId ? { "X-Tenant-Id": headerTenantId } : {}),
    ...(opts?.clientTimeZone ? { "X-Client-Timezone": opts.clientTimeZone } : {}),
    ...(withContentType ? { "Content-Type": "application/json" } : {}),
    Accept: opts?.accept || "application/json",
  };
}

function buildMultipartHeaders(path: string, opts?: ApiOpts): HeadersInit {
  const headers = buildHeaders(path, opts, false) as Record<string, string>;
  delete headers.Accept;
  return headers;
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const bodyText = await res.text();
    let payload: ApiErrorResponse | null = null;
    if (bodyText) {
      try {
        payload = JSON.parse(bodyText) as ApiErrorResponse;
      } catch {
        payload = null;
      }
    }

    const message =
      sanitizeErrorMessage(
        payload?.message?.trim() ||
        (bodyText && bodyText.trim() && !looksLikeMarkup(bodyText) ? bodyText.trim() : res.statusText || "Request failed"),
      );

    throw new ApiClientError(message, {
      status: res.status,
      code: payload?.code ?? null,
      path: payload?.path ?? null,
      correlationId: payload?.correlationId ?? null,
      requestId: payload?.requestId ?? payload?.correlationId ?? null,
    });
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  const payload = text.trim();
  return (payload ? JSON.parse(payload) : undefined) as T;
}

function looksLikeMarkup(text: string): boolean {
  const trimmed = text.trimStart().toLowerCase();
  return trimmed.startsWith("<!doctype") || trimmed.startsWith("<html") || trimmed.startsWith("<body");
}

function sanitizeErrorMessage(message: string): string {
  const normalized = message.trim();
  if (!normalized) {
    return "Request failed";
  }
  if (
    normalized.startsWith("org.") ||
    normalized.startsWith("java.") ||
    normalized.includes("Exception:") ||
    normalized.includes("Stack trace") ||
    normalized.includes("at ") ||
    normalized.includes("SQLSTATE")
  ) {
    return "Request failed";
  }
  const redacted = normalized
    .replace(UUID_IN_TEXT_RE, "[hidden reference]")
    .replace(/\s*\(?\s*ref:\s*\[hidden reference\]\s*\)?/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim();
  return redacted || "Request failed";
}

export async function httpGet<T>(path: string, opts?: ApiOpts): Promise<T> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "GET",
    headers: buildHeaders(path, opts, false),
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}

export async function httpGetText(path: string, opts?: ApiOpts): Promise<string> {
  const res = await fetch(`${baseUrl(opts?.apiBase)}${path}`, {
    method: "GET",
    headers: buildHeaders(path, opts, false),
    signal: opts?.signal,
  });
  if (!res.ok) {
    await parseResponse<unknown>(res);
  }
  return res.text();
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
