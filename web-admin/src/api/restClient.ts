export type ApiOpts = {
  token?: string;
  tenantId?: string | null;
  apiBase?: string;
  signal?: AbortSignal;
};

function baseUrl(apiBase?: string): string {
  const envBase = import.meta.env.VITE_API_BASE_URL || "";
  return (apiBase || envBase || "").replace(/\/+$/, "");
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
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
    headers: {
      ...(opts?.token ? { Authorization: `Bearer ${opts.token}` } : {}),
      ...(opts?.tenantId ? { "X-Tenant-Id": opts.tenantId } : {}),
      Accept: "application/json",
    },
    signal: opts?.signal,
  });
  return parseResponse<T>(res);
}
