function trimEnv(value: string | undefined) {
  return value?.trim() ?? "";
}

function isLocalHost(hostname: string) {
  return hostname === "localhost" || hostname === "127.0.0.1";
}

function localPortUrl(port: string, path = "/") {
  const url = new URL(window.location.origin);
  url.port = port;
  url.pathname = path;
  url.search = "";
  url.hash = "";
  return url.toString().replace(/\/$/, "");
}

function discoverFallbackUrl() {
  if (typeof window === "undefined") {
    return "https://jeevanam.deepthoughtnet.com";
  }
  if (isLocalHost(window.location.hostname)) {
    return localPortUrl("5177");
  }
  return "https://jeevanam.deepthoughtnet.com";
}

function healthcareFallbackUrl() {
  if (typeof window === "undefined") {
    return "https://healthcare.deepthoughtnet.com";
  }
  if (isLocalHost(window.location.hostname)) {
    return localPortUrl("5174");
  }
  return "https://healthcare.deepthoughtnet.com";
}

export const careConfig = {
  apiBaseUrl: trimEnv(import.meta.env.VITE_PUBLIC_API_BASE_URL) || trimEnv(import.meta.env.VITE_API_BASE_URL),
  discoverAppUrl: trimEnv(import.meta.env.VITE_DISCOVER_APP_URL) || discoverFallbackUrl(),
  healthcareAppUrl: trimEnv(import.meta.env.VITE_HEALTHCARE_APP_URL) || trimEnv(import.meta.env.VITE_CLINIC_LOGIN_URL) || healthcareFallbackUrl(),
  supportUrl: trimEnv(import.meta.env.VITE_SUPPORT_URL),
  aivaAppUrl: trimEnv(import.meta.env.VITE_AIVA_APP_URL),
};

export function externalAppUrl(baseUrl: string, path = "/", search = "") {
  const url = new URL(baseUrl, window.location.origin);
  url.pathname = path;
  url.search = search;
  url.hash = "";
  return url.toString();
}
