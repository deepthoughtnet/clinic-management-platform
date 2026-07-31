export type DiscoverConfig = {
  apiBaseUrl: string;
  careAppUrl: string;
  healthcareAppUrl: string;
  aivaAppUrl: string;
  environmentName: string;
  analyticsId: string;
  analyticsEnabled: boolean;
  mapTileUrl: string;
  mapTileAttribution: string;
  mapDefaultLatitude: number | null;
  mapDefaultLongitude: number | null;
  mapDefaultZoom: number;
  mapDirectionsUrlTemplate: string;
  geocodingProvider: string;
  geocodingBaseUrl: string;
  geocodingSearchPath: string;
  showHomeDemoProviders: boolean;
};

function trimEnv(value: string | undefined): string {
  return value?.trim() ?? "";
}

function isLocalHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1";
}

function parseNumber(value: string | undefined): number | null {
  const trimmed = trimEnv(value);
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseBoolean(value: string | undefined): boolean | null {
  const trimmed = trimEnv(value).toLowerCase();
  if (!trimmed) {
    return null;
  }
  if (["true", "1", "yes", "on"].includes(trimmed)) {
    return true;
  }
  if (["false", "0", "no", "off"].includes(trimmed)) {
    return false;
  }
  return null;
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

function mapTileUrlFallback(): string {
  return "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
}

function mapTileAttributionFallback(): string {
  return "&copy; OpenStreetMap contributors";
}

function defaultHomeDemoProviders(): boolean {
  const environmentName = trimEnv(import.meta.env.VITE_ENV_NAME).toLowerCase();
  if (environmentName === "local" || environmentName === "uat") {
    return true;
  }
  if (typeof window !== "undefined") {
    return window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
  }
  return false;
}

export const discoverConfig: DiscoverConfig = {
  apiBaseUrl: trimEnv(import.meta.env.VITE_API_BASE_URL),
  careAppUrl: trimEnv(import.meta.env.VITE_CARE_APP_URL) || appUrlFallback("care"),
  healthcareAppUrl: trimEnv(import.meta.env.VITE_HEALTHCARE_APP_URL) || appUrlFallback("healthcare"),
  aivaAppUrl: trimEnv(import.meta.env.VITE_AIVA_APP_URL),
  environmentName: trimEnv(import.meta.env.VITE_ENV_NAME),
  analyticsId: trimEnv(import.meta.env.VITE_ANALYTICS_ID),
  analyticsEnabled: trimEnv(import.meta.env.VITE_ANALYTICS_ENABLED).toLowerCase() === "true",
  mapTileUrl: trimEnv(import.meta.env.VITE_MAP_TILE_URL) || mapTileUrlFallback(),
  mapTileAttribution: trimEnv(import.meta.env.VITE_MAP_TILE_ATTRIBUTION) || mapTileAttributionFallback(),
  mapDefaultLatitude: parseNumber(import.meta.env.VITE_MAP_DEFAULT_LATITUDE),
  mapDefaultLongitude: parseNumber(import.meta.env.VITE_MAP_DEFAULT_LONGITUDE),
  mapDefaultZoom: parseNumber(import.meta.env.VITE_MAP_DEFAULT_ZOOM) ?? 15,
  mapDirectionsUrlTemplate: trimEnv(import.meta.env.VITE_MAP_DIRECTIONS_URL_TEMPLATE),
  geocodingProvider: trimEnv(import.meta.env.VITE_GEOCODING_PROVIDER),
  geocodingBaseUrl: trimEnv(import.meta.env.VITE_GEOCODING_BASE_URL),
  geocodingSearchPath: trimEnv(import.meta.env.VITE_GEOCODING_SEARCH_PATH),
  showHomeDemoProviders: parseBoolean(import.meta.env.VITE_SHOW_HOME_DEMO_PROVIDERS) ?? defaultHomeDemoProviders(),
};
