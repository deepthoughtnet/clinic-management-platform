import { discoverConfig } from "../config";
import type { ProviderStatus, ProviderType } from "./providerOnboarding";

export type ProviderLoginChallengeResponse = {
  message: string;
  developmentCode: string | null;
  expiresInSeconds: number;
  resendAfterSeconds: number;
  providerName?: string | null;
  deliveryReference?: string | null;
};

export type ProviderLoginVerifyResponse = {
  verified: boolean;
  providerAccountId: string | null;
  sessionExpiresAt: string | null;
  message: string;
};

export type ProviderWorkspaceApplication = {
  id: string;
  referenceNumber: string;
  providerType: ProviderType;
  status: ProviderStatus;
  displayName: string;
  completionPercent: number;
  currentStep: string;
  contactVerified: boolean;
  updatedAt: string;
  submittedAt: string | null;
  publicProfilePath: string | null;
};

export type ProviderWorkspaceResponse = {
  providerAccountId: string;
  applications: ProviderWorkspaceApplication[];
};

function buildUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function parseError(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

async function request<T>(path: string, method: "GET" | "POST", body?: unknown): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function requestProviderLoginChallenge(identifier: string) {
  return request<ProviderLoginChallengeResponse>("/api/provider/auth/request", "POST", { identifier });
}

export function verifyProviderLoginCode(identifier: string, code: string) {
  return request<ProviderLoginVerifyResponse>("/api/provider/auth/verify", "POST", { identifier, code });
}

export function loadProviderWorkspace() {
  return request<ProviderWorkspaceResponse>("/api/provider/me", "GET");
}

export function loadProviderApplications() {
  return request<ProviderWorkspaceApplication[]>("/api/provider/applications", "GET");
}

export function logoutProviderSession() {
  return request<void>("/api/provider/auth/logout", "POST");
}
