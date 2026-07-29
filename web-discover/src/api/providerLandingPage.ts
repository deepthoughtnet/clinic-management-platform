import { discoverConfig } from "../config";

export type LandingProviderType = "INDIVIDUAL_DOCTOR" | "CLINIC" | "HOSPITAL";

export type LandingTheme = {
  primaryColor: string;
  accentColor: string;
  typographyPreset: string;
  buttonStyle: string;
  borderRadiusPreset: string;
};

export type LandingSection = {
  key: string;
  enabled: boolean;
  displayOrder: number;
  title: string;
  description: string | null;
  visibilityRule: string;
  content: Record<string, unknown>;
};

export type LandingTemplate = {
  templateKey: string;
  templateName: string;
  providerType: LandingProviderType;
  templateVersion: number;
  description: string | null;
  supportedSections: string[];
  defaultSections: LandingSection[];
  defaultTheme: LandingTheme;
};

export type LandingSnapshot = {
  templateKey: string;
  templateVersion: number;
  theme: LandingTheme;
  sections: LandingSection[];
};

export type LandingVersion = {
  id: string;
  versionNumber: number;
  templateKey: string;
  templateVersion: number;
  changeSummary: string;
  versionKind: string;
  publishedAt: string;
  sectionKeys: string[];
  theme: LandingTheme;
};

export type LandingCompare = {
  leftVersion: number;
  rightVersion: number;
  templateChanged: boolean;
  themeChanged: boolean;
  sectionOrderChanged: boolean;
  addedSections: string[];
  removedSections: string[];
  changedSections: string[];
};

export type LandingProfileLocation = {
  label: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  pinCode: string | null;
  workingHours: string | null;
  parkingAvailable: boolean;
  accessibilityAvailable: boolean;
  latitude: number | null;
  longitude: number | null;
};

export type LandingProfile = {
  providerId: string;
  providerType: LandingProviderType;
  referenceNumber: string;
  displayName: string;
  legalName: string | null;
  canonicalSlug: string;
  summary: string | null;
  biography: string | null;
  qualification: string | null;
  medicalCouncil: string | null;
  yearsOfExperience: number | null;
  consultationFee: number | string | null;
  appointmentDurationMinutes: number | null;
  onlineConsultation: boolean;
  languages: string[];
  specialities: string[];
  subSpecialities: string[];
  services: string[];
  departments: string[];
  facilities: string[];
  consultationModes: string[];
  locations: LandingProfileLocation[];
  gallery: Array<{ documentId: string; caption: string | null }>;
  galleryImageUrls: string[];
  imageUrl: string | null;
  coverUrl: string | null;
  logoUrl: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  website: string | null;
  city: string | null;
  area: string | null;
  state: string | null;
  country: string | null;
  primarySpeciality: string | null;
  ownership: string | null;
  hospitalType: string | null;
  medicalDirector: string | null;
  beds: number | null;
  emergencyAvailable: boolean;
  reviewsComingSoon: boolean;
  publishedAt: string;
  publishedVersionNumber: number;
  slug: string;
  previousSlug: string | null;
  canonical: boolean;
  publicPath: string;
};

export type LandingPageResponse = {
  providerId: string;
  providerType: LandingProviderType;
  displayName: string;
  canonicalSlug: string;
  publicPath: string;
  editable: boolean;
  published: boolean;
  draftVersionNumber: number;
  publishedVersionNumber: number | null;
  publishedAt: string | null;
  draft: LandingSnapshot;
  publishedSnapshot: LandingSnapshot | null;
  profile: LandingProfile;
  templates: LandingTemplate[];
  versions: LandingVersion[];
};

export type LandingPageRenderable = {
  displayName: string;
  canonicalSlug: string;
  publicPath: string;
  profile: LandingProfile;
  draft?: LandingSnapshot | null;
  publishedSnapshot?: LandingSnapshot | null;
};

export type PublicLandingPageResponse = LandingPageRenderable & {
  providerId: string;
  providerType: LandingProviderType;
  published: boolean;
  publishedVersionNumber: number | null;
  publishedAt: string | null;
};

export type LandingPageUpdate = {
  version?: number | null;
  templateKey?: string | null;
  theme?: LandingTheme | null;
  sections?: LandingSection[];
};

export type LandingPageRevert = {
  versionNumber: number;
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

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { "X-Provider-Onboarding-Token": token } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export function loadLandingPage(token: string) {
  return request<LandingPageResponse>("/api/provider/landing-page", {}, token);
}

export function previewLandingPage(token: string) {
  return request<LandingPageResponse>("/api/provider/landing-page/preview", {}, token);
}

export function updateLandingPage(token: string, payload: LandingPageUpdate) {
  return request<LandingPageResponse>("/api/provider/landing-page", {
    method: "PUT",
    body: JSON.stringify(payload),
  }, token);
}

export function publishLandingPage(token: string) {
  return request<LandingPageResponse>("/api/provider/landing-page/publish", { method: "POST" }, token);
}

export function revertLandingPage(token: string, payload: LandingPageRevert) {
  return request<LandingPageResponse>("/api/provider/landing-page/revert", {
    method: "POST",
    body: JSON.stringify(payload),
  }, token);
}

export function listLandingPageVersions(token: string) {
  return request<LandingVersion[]>("/api/provider/landing-page/versions", {}, token);
}

export function compareLandingPageVersions(token: string, leftVersion: number, rightVersion: number) {
  return request<LandingCompare>(`/api/provider/landing-page/compare/${leftVersion}/${rightVersion}`, {}, token);
}

export function loadPublicLandingPage(slug: string) {
  return request<PublicLandingPageResponse>(`/api/public/landing/${slug}`);
}
