import { discoverConfig } from "../config";
import type { LandingPageRenderable, LandingProfile, LandingSection, LandingSnapshot, LandingTheme } from "./providerLandingPage";
import { buildPublicAddressView, normalizeDisplayList, resolveClinicEstablishedYear } from "../utils/publicProfileFormatting";
import { normalizeWeeklyScheduleContent } from "../components/provider-profile-editor/ProviderProfileEditorControls";

export type ProviderPublicProfileReviewFindingResponse = {
  id: string;
  findingReference: string;
  submissionReference: string;
  section: string | null;
  fieldKey: string | null;
  category: string | null;
  severity: string | null;
  required: boolean;
  reviewerNote: string | null;
  providerFacingMessage: string | null;
  internalNote: string | null;
  resolutionStatus: string | null;
  providerResolutionNote: string | null;
  createdAt: string | null;
  resolvedAt: string | null;
};

export type ProviderPublicProfileReviewResponse = {
  id: string;
  submissionReference: string;
  publicProfileReference: string;
  publicProfileType: "CLINIC" | "HOSPITAL" | "INDIVIDUAL_DOCTOR";
  draftReference: string;
  submittedDraftVersion: number;
  moderationStatus: string;
  publicationStatusSnapshot: string;
  tenantConsentStatusSnapshot: string;
  ownershipSnapshot: Record<string, unknown>;
  readinessSnapshot: Record<string, unknown>;
  contentSnapshot: Record<string, unknown>;
  sourceAttributionSnapshot: Record<string, unknown>;
  mediaSnapshot: Record<string, unknown>;
  submittedByProviderAccountId: string | null;
  submittedAt: string | null;
  assignedReviewerId: string | null;
  assignedAt: string | null;
  decisionById: string | null;
  decisionAt: string | null;
  decisionReason: string | null;
  moderationRevision: number;
  current: boolean;
  approvedVersionNumber: number | null;
  publishedAt: string | null;
  unpublishedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  effectiveVisibility: string | null;
  visibilityReason: string | null;
  publicUrl: string | null;
  findings: ProviderPublicProfileReviewFindingResponse[];
  providerAllowedActions: string[];
  allowedActions: string[];
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

async function request<T>(path: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    cache: "no-store",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export function loadProviderPublicProfileReview(publicProfileReference: string) {
  return request<ProviderPublicProfileReviewResponse>(`/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/review`);
}

export function providerPublicProfileReviewMediaContentPath(publicProfileReference: string, submissionReference: string, mediaReference: string) {
  return `/api/provider/public-profiles/${encodeURIComponent(publicProfileReference)}/submissions/${encodeURIComponent(submissionReference)}/media/${encodeURIComponent(mediaReference)}/content`;
}

function textValue(section: Record<string, unknown> | undefined, key: string) {
  const value = section?.[key];
  if (typeof value === "string") {
    return value;
  }
  if (value == null) {
    return null;
  }
  return String(value);
}

function sectionValue(review: ProviderPublicProfileReviewResponse, key: string) {
  const value = review.contentSnapshot?.[key];
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function mediaValue(review: ProviderPublicProfileReviewResponse, key: string) {
  const media = sectionValue(review, "media");
  const value = media[key];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function mediaReferences(review: ProviderPublicProfileReviewResponse) {
  const media = sectionValue(review, "media");
  return Array.isArray(media.gallery) ? media.gallery.map((entry) => typeof entry === "string" ? entry.trim() : "").filter(Boolean) : [];
}

function mediaMetadata(review: ProviderPublicProfileReviewResponse) {
  const media = sectionValue(review, "media");
  const value = media.mediaMetadataByDocumentId;
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, { originalFilename?: string | null }> : {};
}

function mediaAltText(review: ProviderPublicProfileReviewResponse) {
  const media = sectionValue(review, "media");
  const value = media.galleryAltTextByDocumentId;
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, string> : {};
}

function resolveSlug(review: ProviderPublicProfileReviewResponse) {
  return textValue(sectionValue(review, "seo"), "slug")
    || review.publicProfileReference
    || "provider";
}

export function reviewToLandingPreview(review: ProviderPublicProfileReviewResponse): { page: LandingPageRenderable; snapshot: LandingSnapshot } {
  const about = sectionValue(review, "about");
  const contact = sectionValue(review, "contact");
  const servicesSection = sectionValue(review, "services");
  const specialitiesSection = sectionValue(review, "specialities");
  const languagesSection = sectionValue(review, "languages");
  const facilitiesSection = sectionValue(review, "facilities");
  const timings = sectionValue(review, "timings");
  const normalizedTimings = normalizeWeeklyScheduleContent(timings);
  const services = normalizeDisplayList(Array.isArray(servicesSection.items) ? servicesSection.items.map((item) => String(item)) : []);
  const specialities = normalizeDisplayList(Array.isArray(specialitiesSection.items) ? specialitiesSection.items.map((item) => String(item)) : []);
  const languages = normalizeDisplayList(Array.isArray(languagesSection.items) ? languagesSection.items.map((item) => String(item)) : []);
  const facilities = normalizeDisplayList(Array.isArray(facilitiesSection.items) ? facilitiesSection.items.map((item) => String(item)) : []);
  const addressView = buildPublicAddressView({
    addressLine1: textValue(contact, "addressLine1"),
    addressLine2: textValue(contact, "addressLine2"),
    area: textValue(contact, "area"),
    city: textValue(contact, "city"),
    state: textValue(contact, "state"),
    country: textValue(contact, "country"),
    postalCode: textValue(contact, "postalCode"),
  });
  const logoReference = mediaValue(review, "logoDocumentId");
  const coverReference = mediaValue(review, "coverDocumentId");
  const galleryReferences = mediaReferences(review);
  const galleryAlt = mediaAltText(review);
  const providerType = review.publicProfileType;
  const displayName = textValue(about, "displayName") || "Public profile";
  const slug = resolveSlug(review);
  const profile: LandingProfile = {
    providerId: review.publicProfileReference,
    providerType,
    referenceNumber: review.publicProfileReference,
    displayName,
    legalName: null,
    canonicalSlug: slug,
    summary: textValue(about, "shortTagline") || displayName,
    biography: textValue(about, "description") || "",
    qualification: textValue(about, "qualification"),
    medicalCouncil: textValue(about, "medicalCouncil"),
    yearsOfExperience: providerType === "INDIVIDUAL_DOCTOR" ? Number(textValue(about, "yearsOfExperience")) || null : null,
    consultationFee: null,
    appointmentDurationMinutes: null,
    onlineConsultation: false,
    languages,
    specialities,
    subSpecialities: [],
    services,
    departments: [],
    facilities,
    consultationModes: [],
    locations: [{
      label: displayName,
      addressLine1: textValue(contact, "addressLine1"),
      addressLine2: textValue(contact, "addressLine2"),
      address: addressView.singleLine || null,
      area: textValue(contact, "area"),
      city: textValue(contact, "city"),
      state: textValue(contact, "state"),
      country: textValue(contact, "country"),
      pinCode: textValue(contact, "postalCode"),
      postalCode: textValue(contact, "postalCode"),
      workingHours: normalizedTimings.intervals.length ? "Weekly timings configured" : null,
      parkingAvailable: facilities.some((item) => item.toLowerCase() === "parking"),
      accessibilityAvailable: facilities.some((item) => item.toLowerCase().includes("wheelchair")),
      latitude: null,
      longitude: null,
    }],
    gallery: galleryReferences.map((reference, index) => ({
      documentId: reference,
      caption: galleryAlt[reference] || mediaMetadata(review)[reference]?.originalFilename || `Gallery image ${index + 1}`,
    })),
    galleryImageUrls: galleryReferences.map((reference) => providerPublicProfileReviewMediaContentPath(review.publicProfileReference, review.submissionReference, reference)),
    imageUrl: logoReference ? providerPublicProfileReviewMediaContentPath(review.publicProfileReference, review.submissionReference, logoReference) : null,
    coverUrl: coverReference ? providerPublicProfileReviewMediaContentPath(review.publicProfileReference, review.submissionReference, coverReference) : null,
    logoUrl: logoReference ? providerPublicProfileReviewMediaContentPath(review.publicProfileReference, review.submissionReference, logoReference) : null,
    contactPhone: textValue(contact, "publicPhone"),
    contactEmail: textValue(contact, "publicEmail"),
    website: textValue(contact, "website"),
    city: textValue(contact, "city"),
    area: textValue(contact, "area"),
    state: textValue(contact, "state"),
    country: textValue(contact, "country"),
    primarySpeciality: specialities[0] || null,
    ownership: textValue(review.ownershipSnapshot as Record<string, unknown>, "ownershipStatus"),
    hospitalType: null,
    medicalDirector: null,
    beds: null,
    emergencyAvailable: Boolean(textValue(about, "emergencyAvailability")),
    establishedYear: providerType === "CLINIC" ? resolveClinicEstablishedYear(textValue(about, "establishedYear"), textValue(about, "registrationNumber")) : null,
    registrationNumber: textValue(about, "registrationNumber"),
    clinicPhilosophy: textValue(about, "philosophy"),
    reviewsComingSoon: true,
    publishedAt: review.submittedAt || review.updatedAt || new Date().toISOString(),
    publishedVersionNumber: review.submittedDraftVersion,
    slug,
    previousSlug: null,
    canonical: true,
    publicPath: review.publicUrl || `/discover/${providerType.toLowerCase()}s/${slug}`,
    weeklyTimings: normalizedTimings.intervals,
    timezone: normalizedTimings.timezone,
  };
  const snapshot: LandingSnapshot = {
    templateKey: "provider-review",
    templateVersion: 1,
    theme: {
      primaryColor: "#0F8B8D",
      accentColor: "#1E88E5",
      typographyPreset: "clean",
      buttonStyle: "solid",
      borderRadiusPreset: "medium",
    } satisfies LandingTheme,
    sections: [
      { key: "HERO", enabled: true, displayOrder: 0, title: "Overview", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "ABOUT", enabled: true, displayOrder: 1, title: "About", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "SERVICES", enabled: true, displayOrder: 2, title: "Services", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "FACILITIES", enabled: true, displayOrder: 3, title: "Facilities", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "WORKING_HOURS", enabled: true, displayOrder: 4, title: "Timings", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "GALLERY", enabled: true, displayOrder: 5, title: "Media", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "CONTACT", enabled: true, displayOrder: 6, title: "Contact", description: null, visibilityRule: "PUBLIC", content: {} },
      { key: "CTA", enabled: true, displayOrder: 7, title: "Publication", description: null, visibilityRule: "PUBLIC", content: {} },
    ],
  };
  return {
    page: {
      displayName,
      canonicalSlug: slug,
      publicPath: profile.publicPath,
      profile,
      draft: snapshot,
      publishedSnapshot: null,
    },
    snapshot,
  };
}
