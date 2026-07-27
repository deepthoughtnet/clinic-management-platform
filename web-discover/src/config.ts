export type DiscoverConfig = {
  apiBaseUrl: string;
  careAppUrl: string;
  healthcareAppUrl: string;
  aivaAppUrl: string;
  environmentName: string;
  analyticsId: string;
  analyticsEnabled: boolean;
};

function trimEnv(value: string | undefined): string {
  return value?.trim() ?? "";
}

function isLocalHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1";
}

function appUrlFallback(app: "care" | "healthcare"): string {
  if (typeof window === "undefined") {
    return app === "care" ? "https://care.deepthoughtnet.com" : "https://healthcare.deepthoughtnet.com";
  }

  const currentUrl = new URL(window.location.origin);

  if (isLocalHost(currentUrl.hostname)) {
    currentUrl.port = app === "care" ? "5175" : "5173";
    currentUrl.pathname = app === "care" ? "/patient/login" : "/";
    return currentUrl.toString();
  }

  currentUrl.hostname =
    app === "care" ? "care.deepthoughtnet.com" : "healthcare.deepthoughtnet.com";
  currentUrl.pathname = "/";
  return currentUrl.toString();
}

export const discoverConfig: DiscoverConfig = {
  apiBaseUrl: trimEnv(import.meta.env.VITE_API_BASE_URL),
  careAppUrl: trimEnv(import.meta.env.VITE_CARE_APP_URL) || appUrlFallback("care"),
  healthcareAppUrl: trimEnv(import.meta.env.VITE_HEALTHCARE_APP_URL) || appUrlFallback("healthcare"),
  aivaAppUrl: trimEnv(import.meta.env.VITE_AIVA_APP_URL),
  environmentName: trimEnv(import.meta.env.VITE_ENV_NAME),
  analyticsId: trimEnv(import.meta.env.VITE_ANALYTICS_ID),
  analyticsEnabled: trimEnv(import.meta.env.VITE_ANALYTICS_ENABLED).toLowerCase() === "true",
};
