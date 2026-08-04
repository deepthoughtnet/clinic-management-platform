export type AdminConfig = {
  providerAppUrl: string;
};

function trim(value: string | undefined): string {
  return value?.trim() ?? "";
}

function providerFallback(): string {
  if (typeof window === "undefined") {
    return "https://discover.deepthoughtnet.com";
  }
  const url = new URL(window.location.origin);
  if (url.hostname === "localhost" || url.hostname === "127.0.0.1") {
    url.port = "5177";
    return url.toString().replace(/\/$/, "");
  }
  url.hostname = "discover.deepthoughtnet.com";
  url.pathname = "/";
  return url.toString().replace(/\/$/, "");
}

export const adminConfig: AdminConfig = {
  providerAppUrl: trim(import.meta.env.VITE_PROVIDER_APP_URL) || providerFallback(),
};
