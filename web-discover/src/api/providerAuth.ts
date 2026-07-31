import { discoverConfig } from "../config";
import type { ProviderStatus, ProviderType } from "./providerOnboarding";
import type { ProviderDashboard } from "./providerOnboarding";

export type ProviderLoginChallengeResponse = {
  challengeId: string;
  channel: "EMAIL" | "SMS" | string;
  maskedRecipient: string | null;
  message: string;
  developmentCode: string | null;
  verificationMode?: "LOCAL" | "PRODUCTION" | string | null;
  expiresAt: string;
  resendAvailableAt: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
  providerName?: string | null;
  deliveryReference?: string | null;
};

export type ProviderLoginVerifyResponse = {
  verified: boolean;
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
  contactEmail: string | null;
  contactPhone: string | null;
  emailVerifiedAt: string | null;
  phoneVerifiedAt: string | null;
  applications: ProviderWorkspaceApplication[];
};

export type ProviderOnboardingAccessResponse = {
  applicationId: string;
  onboardingToken: string;
};

export type ProviderWorkspaceStartResponse = {
  applicationId: string;
  referenceNumber: string;
  providerType: ProviderType;
  status: ProviderStatus;
  currentStep: string;
  onboardingToken: string | null;
  publicProfilePath: string | null;
};

export class ProviderAuthError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ProviderAuthError";
    this.status = status;
  }
}

function buildUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function parseError(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as { message?: string };
    const message = body.message ?? fallback;
    if (typeof message === "string" && /carepilot|enabled=false|not configured|providerName|deliveryReference|verificationMode/i.test(message)) {
      return "Verification service is temporarily unavailable. Please try again later.";
    }
    return message;
  } catch {
    return fallback;
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ProviderAuthError(await parseError(response), response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function request<T>(path: string, method: "GET" | "POST", body?: unknown): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method,
    cache: "no-store",
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return parseResponse<T>(response);
}

export function requestProviderLoginChallenge(identifier: string) {
  return request<ProviderLoginChallengeResponse>("/api/provider/auth/challenges", "POST", { identifier });
}

export function verifyProviderLoginCode(challengeId: string, code: string) {
  return request<ProviderLoginVerifyResponse>(`/api/provider/auth/challenges/${challengeId}/verify`, "POST", { code });
}

export function loadProviderWorkspace() {
  return request<ProviderWorkspaceResponse>("/api/provider/me", "GET");
}

export function loadProviderApplications() {
  return request<ProviderWorkspaceApplication[]>("/api/provider/applications", "GET");
}

export function loadProviderApplicationDashboard(applicationReference: string) {
  return request<ProviderDashboard>(`/api/provider/applications/${encodeURIComponent(applicationReference)}/dashboard`, "GET");
}

export function createProviderOnboardingAccess(applicationReference: string) {
  return request<ProviderOnboardingAccessResponse>(`/api/provider/applications/${encodeURIComponent(applicationReference)}/onboarding-access`, "POST");
}

export function startProviderApplication(providerType: ProviderType) {
  return request<ProviderWorkspaceStartResponse>("/api/provider/applications/start", "POST", { providerType });
}

export function logoutProviderSession() {
  return request<void>("/api/provider/auth/logout", "POST");
}
