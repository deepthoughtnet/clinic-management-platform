import { discoverConfig } from "../config";

export type ProviderPublicProfileType = "CLINIC" | "HOSPITAL" | "INDIVIDUAL_DOCTOR";

export type ProviderPublicProfileDraftFieldSource = {
  sourceSystem: string;
  sourceReference: string;
  sourceRevision: number;
  importedAt: string | null;
  lastEditedBy: string | null;
  lastEditedAt: string | null;
  providerOverride: boolean;
};

export type ProviderPublicProfileDraftSection = {
  key: string;
  title: string;
  content: Record<string, unknown>;
  sources: Record<string, ProviderPublicProfileDraftFieldSource>;
};

export type ProviderPublicProfileDraftMediaType = "LOGO" | "COVER_IMAGE" | "GALLERY_IMAGE";

export type ProviderPublicProfileDraftReadiness = {
  readinessStatus: string;
  ready: boolean;
  completenessPercentage: number;
  missingMandatoryFields: string[];
  recommendedFields: string[];
  invalidFields: string[];
  warnings: string[];
  blockingReasons: string[];
  lastEvaluatedAt: string;
};

export type ProviderPublicProfileDraftVersion = {
  id: string;
  versionNumber: number;
  changeSummary: string;
  createdAt: string;
  createdByProviderAccountId: string | null;
};

export type ProviderPublicProfileDraft = {
  draftId: string;
  draftReference: string;
  publicProfileReference: string;
  publicProfileType: ProviderPublicProfileType;
  providerAccountId: string;
  ownershipStatus: string;
  tenantConsentStatus: string;
  publicProfileStatus: string;
  contentStatus: string;
  readinessStatus: string;
  completenessPercentage: number;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
  lastSavedAt: string | null;
  ownershipUpdatedAt: string | null;
  displayName: string | null;
  canonicalSlug: string | null;
  city: string | null;
  area: string | null;
  state: string | null;
  country: string | null;
  publicPhone: string | null;
  publicEmail: string | null;
  website: string | null;
  whatsappNumber: string | null;
  registrationNumber: string | null;
  establishedYear: number | null;
  sourceSystem: string | null;
  sourceReference: string | null;
  sourceRevision: number;
  sourceUpdatedAt: string | null;
  publicProfilePath: string | null;
  allowedActions: string[];
  sections: ProviderPublicProfileDraftSection[];
  readiness: ProviderPublicProfileDraftReadiness;
  versions: ProviderPublicProfileDraftVersion[];
  fieldSources: Record<string, ProviderPublicProfileDraftFieldSource>;
};

export type ProviderPublicProfileDraftMediaUploadResponse = {
  mediaReference: string;
  draft: ProviderPublicProfileDraft;
};

export type ProviderPublicProfileModeration = {
  submissionEligible: boolean;
  submissionBlockers: string[];
  allowedActions: string[];
  moderationStatus: string;
  publicationStatus: string;
  submissionReference: string | null;
  submittedDraftVersion: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  currentDraftVersion: number;
};

export type ProviderPublicProfileDraftSectionUpdate = {
  sectionKey: string;
  content: Record<string, unknown>;
  expectedVersion?: number | null;
  changeSummary?: string | null;
};

function buildUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function parseError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(buildUrl(path), {
    ...options,
    cache: "no-store",
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function createProviderPublicProfileDraft(publicProfileReference: string) {
  return request<ProviderPublicProfileDraft>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/draft`, { method: "POST" });
}

export function loadProviderPublicProfileDraft(publicProfileReference: string) {
  return request<ProviderPublicProfileDraft>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/draft`);
}

export function saveProviderPublicProfileDraftSection(publicProfileReference: string, payload: ProviderPublicProfileDraftSectionUpdate) {
  return request<ProviderPublicProfileDraft>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/draft/${encodeURIComponent(payload.sectionKey)}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function loadProviderPublicProfileDraftPreview(publicProfileReference: string) {
  return request<ProviderPublicProfileDraft>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/preview`);
}

export function providerPublicProfileDraftMediaContentPath(publicProfileReference: string, mediaReference: string) {
  return `/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/media/${encodeURIComponent(mediaReference)}/content`;
}

export async function uploadProviderPublicProfileDraftMedia(
  publicProfileReference: string,
  mediaType: ProviderPublicProfileDraftMediaType,
  file: File,
  altText?: string | null,
) {
  const body = new FormData();
  body.set("mediaType", mediaType);
  body.set("file", file);
  if (altText?.trim()) {
    body.set("altText", altText.trim());
  }
  const response = await fetch(buildUrl(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/media`), {
    method: "POST",
    cache: "no-store",
    credentials: "include",
    body,
    headers: {
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<ProviderPublicProfileDraftMediaUploadResponse>;
}

export function loadProviderPublicProfileDraftReadiness(publicProfileReference: string) {
  return request<ProviderPublicProfileDraftReadiness>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/readiness`);
}

export function loadProviderPublicProfileModeration(publicProfileReference: string) {
  return request<ProviderPublicProfileModeration>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/moderation`);
}

export function submitProviderPublicProfileForReview(publicProfileReference: string) {
  return request<ProviderPublicProfileModeration>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/submissions`, { method: "POST" });
}
